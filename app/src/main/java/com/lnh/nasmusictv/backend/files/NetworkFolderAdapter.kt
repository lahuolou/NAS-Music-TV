package com.lnh.nasmusictv.backend.files

import android.content.Context
import com.lnh.nasmusictv.backend.BackendAdapter
import com.lnh.nasmusictv.backend.network.baidu.Id3v2Parser
import com.lnh.nasmusictv.data.model.Album
import com.lnh.nasmusictv.data.model.Artist
import com.lnh.nasmusictv.data.model.BackendDirectory
import com.lnh.nasmusictv.data.model.Genre
import com.lnh.nasmusictv.data.model.MusicSourceType
import com.lnh.nasmusictv.data.model.ServerConfig
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.data.model.VersionInfo
import com.lnh.nasmusictv.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Protocol-neutral listing result. [path] is relative to a WebDAV endpoint or SMB share. */
data class RemoteMusicEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L
)

/**
 * Shared scanner and library projection for file-oriented NAS backends.
 *
 * Protocol adapters only implement directory listing, bounded reads and playback URL creation.
 * Scanning is serialized and cached for the lifetime of the active backend connection so the
 * parallel album/artist/song requests from MainViewModel do not each walk the NAS independently.
 */
abstract class NetworkFolderAdapter(
    protected val appContext: Context?,
    final override val backendType: String,
    private val idPrefix: String,
    initialDisplayName: String
) : BackendAdapter {

    companion object {
        private const val TAG = "NetworkFolderAdapter"
        private const val MAX_SCAN_DEPTH = 32
        private const val MAX_SCAN_ENTRIES = 80_000
        private const val MAX_PARALLEL_TAG_READS = 4
        private const val MAX_LYRICS_BYTES = 1024 * 1024
        private const val MAX_ID3_TAG_BYTES = 16 * 1024 * 1024

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "flac", "wav", "ogg", "oga", "opus",
            "wma", "ape", "alac", "aiff", "aif", "dsf", "dff"
        )
        private val COVER_NAMES = listOf("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png")
    }

    @Volatile
    override var serverName: String = initialDisplayName
        protected set

    @Volatile
    override var apiVersion: String = "File share"
        protected set

    @Volatile
    override var lastErrorDetail: String = ""
        protected set

    @Volatile
    protected var rootPath: String = "/"
        private set

    private val scanMutex = Mutex()
    @Volatile
    private var cachedSongs: List<Song>? = null
    private val songById = ConcurrentHashMap<String, Song>()
    private val audioEntryById = ConcurrentHashMap<String, RemoteMusicEntry>()
    private val lyricsByAudioPath = ConcurrentHashMap<String, RemoteMusicEntry>()
    private val metadataBySongId = ConcurrentHashMap<String, Id3v2Parser.Metadata>()
    private val recentOrder = ConcurrentHashMap<String, Long>()

    protected abstract suspend fun listRemote(path: String): List<RemoteMusicEntry>
    protected abstract suspend fun readRemoteRange(path: String, start: Long, endInclusive: Long): ByteArray?
    protected abstract suspend fun readRemoteText(path: String, maxBytes: Int): String?
    protected abstract fun createPlaybackUrl(entry: RemoteMusicEntry, resourceId: String): String
    protected abstract fun closeRemoteResources()

    /** Called by implementations after the protocol client has been configured. */
    protected suspend fun validateRoot(configuredRoot: String): Boolean = withContext(Dispatchers.IO) {
        rootPath = normalizeRemotePath(configuredRoot)
        return@withContext try {
            listRemote(rootPath)
            lastErrorDetail = ""
            true
        } catch (e: Exception) {
            lastErrorDetail = safeFailureName(e)
            AppLog.w(TAG, "$backendType root validation failed: ${safeFailureName(e)}")
            false
        }
    }

    override suspend fun listDirectories(path: String): List<BackendDirectory> = withContext(Dispatchers.IO) {
        listRemote(normalizeRemotePath(path))
            .asSequence()
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .map { BackendDirectory(it.name.trimEnd('/'), normalizeRemotePath(it.path)) }
            .toList()
    }

    override suspend fun getApiVersion(): VersionInfo = VersionInfo.NoVersion(serverName)

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            listRemote(rootPath)
            lastErrorDetail = ""
            true
        } catch (e: Exception) {
            lastErrorDetail = safeFailureName(e)
            false
        }
    }

    override suspend fun getSongs(limit: Int, offset: Int): List<Song> {
        val all = ensureScanned()
        if (limit <= 0 || offset < 0 || offset >= all.size) return emptyList()
        val endExclusive = offset + minOf(limit, all.size - offset)
        return all.subList(offset, endExclusive).toList()
    }

    override suspend fun getSongsTotalCount(): Int = ensureScanned().size

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        ensureScanned()
        return ids.mapNotNull { songById[it] }
    }

    override suspend fun getAlbums(): List<Album> = ensureScanned()
        .groupBy { albumKey(it.artist, it.album) }
        .map { (key, songs) ->
            val first = songs.first()
            Album(
                id = stableId("album:$key", "${idPrefix}album_"),
                name = first.album.ifBlank { "未知专辑" },
                artist = first.artist,
                artistId = first.artistId,
                coverUrl = songs.firstNotNullOfOrNull { it.coverUrl },
                year = songs.mapNotNull { it.year }.firstOrNull(),
                songCount = songs.size,
                durationMs = songs.sumOf { it.durationMs },
                genre = songs.mapNotNull { it.genre }.firstOrNull(),
                sourceType = MusicSourceType.NAS,
                sourceIds = songs.map { it.id }
            )
        }
        .sortedWith(compareBy<Album> { it.artist.lowercase(Locale.ROOT) }.thenBy { it.name.lowercase(Locale.ROOT) })

    override suspend fun getAlbumSongs(albumId: String): List<Song> = ensureScanned()
        .filter { stableId("album:${albumKey(it.artist, it.album)}", "${idPrefix}album_") == albumId }
        .sortedWith(compareBy<Song> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.title.lowercase(Locale.ROOT) })

    override suspend fun getArtists(): List<Artist> = ensureScanned()
        .groupBy { it.artistId ?: stableId("artist:${it.artist}", "${idPrefix}artist_") }
        .map { (artistId, songs) ->
            Artist(
                id = artistId,
                name = songs.first().artist,
                coverUrl = songs.firstNotNullOfOrNull { it.coverUrl },
                albumCount = songs.map { albumKey(it.artist, it.album) }.distinct().size,
                songCount = songs.size
            )
        }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

    override suspend fun getArtistSongs(artistId: String, artistName: String?): List<Song> {
        val all = ensureScanned()
        return if (artistName.isNullOrBlank()) {
            all.filter { it.artistId == artistId }
        } else {
            all.filter { it.artistId == artistId || it.artist.equals(artistName, ignoreCase = true) }
        }
    }

    override suspend fun getYears(): List<Int> = ensureScanned()
        .mapNotNull { it.year }
        .distinct()
        .sortedDescending()

    override suspend fun searchSongs(query: String): List<Song> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return ensureScanned().filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    override suspend fun getRecentSongs(): List<Song> = ensureScanned()
        .sortedByDescending { recentOrder[it.id] ?: 0L }
        .take(100)

    override suspend fun getRandomSongs(limit: Int): List<Song> =
        if (limit <= 0) emptyList() else ensureScanned().shuffled().take(limit)

    override suspend fun getGenres(): List<Genre> {
        val names = ensureScanned().mapNotNull { it.genre?.trim()?.takeIf(String::isNotBlank) }
        val displayNames = names.distinctBy { it.lowercase(Locale.ROOT) }
            .associateBy { it.lowercase(Locale.ROOT) }
        return names.groupingBy { it.lowercase(Locale.ROOT) }.eachCount()
            .toSortedMap()
            .map { (key, count) -> Genre(id = key, name = displayNames[key] ?: key, songCount = count) }
    }

    override suspend fun getSongsByGenre(genre: String): List<Song> = ensureScanned()
        .filter { it.genre.equals(genre, ignoreCase = true) }

    override suspend fun getSongsByYearRange(fromYear: Int, toYear: Int): List<Song> = ensureScanned()
        .filter { it.year != null && it.year in fromYear..toYear }

    override fun getStreamUrl(songId: String): String =
        audioEntryById[songId]?.let { createPlaybackUrl(it, songId) }.orEmpty()

    override fun getCoverUrl(songId: String): String = songById[songId]?.coverUrl.orEmpty()

    override fun getCoverUrlCandidates(song: Song): List<String> =
        listOfNotNull(songById[song.id]?.coverUrl ?: song.coverUrl).distinct()

    override suspend fun getLyrics(songId: String): String? = withContext(Dispatchers.IO) {
        if (songById[songId] == null) ensureScanned()
        if (songById[songId] == null) return@withContext null
        metadataBySongId[songId]?.lyrics?.takeIf { it.isNotBlank() }?.let { return@withContext it }

        val audio = audioEntryById[songId] ?: return@withContext null
        val base = audio.path.substringBeforeLast('.', audio.path)
        val sidecar = lyricsByAudioPath[base] ?: return@withContext null
        readRemoteText(sidecar.path, MAX_LYRICS_BYTES)?.takeIf { it.isNotBlank() }
    }

    override fun close() {
        try {
            closeRemoteResources()
        } catch (e: Exception) {
            AppLog.w(TAG, "$backendType close failed: ${safeFailureName(e)}")
        }
        cachedSongs = null
        songById.clear()
        audioEntryById.clear()
        lyricsByAudioPath.clear()
        metadataBySongId.clear()
        recentOrder.clear()
        synchronized(albumCoverCache) { albumCoverCache.clear() }
    }

    private suspend fun ensureScanned(): List<Song> = withContext(Dispatchers.IO) { ensureScannedOnIo() }

    private suspend fun ensureScannedOnIo(): List<Song> {
        cachedSongs?.let { return it }
        scanMutex.lock()
        try {
            cachedSongs?.let { return it }
            val allEntries = linkedMapOf<String, RemoteMusicEntry>()
            val visitedDirectories = hashSetOf<String>()

            suspend fun walk(directory: String, depth: Int) {
                if (depth > MAX_SCAN_DEPTH || allEntries.size >= MAX_SCAN_ENTRIES) return
                val canonical = normalizeRemotePath(directory)
                if (!visitedDirectories.add(canonical)) return
                val children = try {
                    listRemote(canonical)
                } catch (e: Exception) {
                    AppLog.w(TAG, "$backendType scan skipped directory (${safeFailureName(e)})")
                    return
                }
                for (entry in children) {
                    if (allEntries.size >= MAX_SCAN_ENTRIES) break
                    allEntries[normalizeRemotePath(entry.path)] = entry.copy(path = normalizeRemotePath(entry.path))
                    if (entry.isDirectory) walk(entry.path, depth + 1)
                }
            }

            walk(rootPath, 0)
            lyricsByAudioPath.clear()
            allEntries.values.asSequence()
                .filter { !it.isDirectory && extensionOf(it.name) == "lrc" }
                .forEach { lyrics ->
                    val audioBase = lyrics.path.substringBeforeLast('.', lyrics.path)
                    lyricsByAudioPath.putIfAbsent(audioBase, lyrics)
                }

            val audioEntries = allEntries.values.filter { !it.isDirectory && extensionOf(it.name) in AUDIO_EXTENSIONS }
            // Build sidecar-cover lookup once; avoid O(audioFiles × directoryEntries) scans.
            val coversByDirectory = allEntries.values
                .filter { !it.isDirectory && it.name.lowercase(Locale.ROOT) in COVER_NAMES }
                .groupBy { it.path.substringBeforeLast('/', "") }
                .mapValues { (_, entries) ->
                    val byName = entries.associateBy { it.name.lowercase(Locale.ROOT) }
                    COVER_NAMES.firstNotNullOfOrNull { byName[it] }
                }
            val semaphore = Semaphore(MAX_PARALLEL_TAG_READS)
            val parsedSongs = mutableListOf<Song>()
            // Batch jobs to avoid creating tens of thousands of deferred objects for large NAS libraries.
            for (batch in audioEntries.chunked(32)) {
                parsedSongs += coroutineScope {
                    batch.map { entry ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                buildSong(entry, coversByDirectory[entry.path.substringBeforeLast('/', "")])
                            }
                        }
                    }.awaitAll()
                }
            }
            val result = parsedSongs.sortedWith(
                compareBy<Song> { it.artist.lowercase(Locale.ROOT) }
                    .thenBy { it.album.lowercase(Locale.ROOT) }
                    .thenBy { it.discNumber }
                    .thenBy { it.trackNumber }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
            songById.clear()
            result.forEach { song -> songById[song.id] = song }
            cachedSongs = result
            AppLog.i(TAG, "$backendType scan complete: ${result.size} audio files (${allEntries.size} entries)")
            return result
        } finally {
            scanMutex.unlock()
        }
    }

    private suspend fun buildSong(
        entry: RemoteMusicEntry,
        coverSidecar: RemoteMusicEntry?
    ): Song {
        val id = stableId(entry.path, "${idPrefix}song_")
        val tagBytes = readId3Tag(entry.path)
        val tags = tagBytes?.let(Id3v2Parser::parseMetadata)
        val artist = tags?.artist?.takeIf { it.isNotBlank() } ?: "未知艺术家"
        val folderName = entry.path.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { "未知专辑" }
        val title = tags?.title?.takeIf { it.isNotBlank() } ?: entry.name.substringBeforeLast('.', entry.name)
        val album = tags?.album?.takeIf { it.isNotBlank() } ?: folderName
        val artistId = stableId("artist:$artist", "${idPrefix}artist_")
        val albumId = stableId("album:${albumKey(artist, album)}", "${idPrefix}album_")
        val albumKey = albumKey(artist, album)
        var coverUrl = coverSidecar?.let { entry ->
            val resourceId = stableId(entry.path, "${idPrefix}resource_")
            createPlaybackUrl(entry, resourceId)
        }
        if (coverUrl == null && tags?.artwork != null) {
            // 只为当前专辑缓存第一张内嵌封面，避免同一张封面为每首歌重复落盘。
            synchronized(albumCoverCache) {
                coverUrl = albumCoverCache[albumKey]
                if (coverUrl == null) {
                    coverUrl = cacheArtwork(id, tags.artworkMime, tags.artwork)
                    if (coverUrl != null) albumCoverCache[albumKey] = coverUrl!!
                }
            }
        }
        val streamUrl = createPlaybackUrl(entry, id)
        audioEntryById[id] = entry
        recentOrder[id] = entry.lastModified
        if (tags != null) {
            // Do not retain full APIC byte arrays for every track in the in-memory index.
            // Artwork is already written to the app cache; lyrics are capped to bound memory.
            metadataBySongId[id] = tags.copy(
                lyrics = tags.lyrics?.take(MAX_LYRICS_BYTES),
                artworkMime = null,
                artwork = null
            )
        }
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            coverUrl = coverUrl,
            streamUrl = streamUrl,
            durationMs = tags?.durationMs ?: 0L,
            trackNumber = tags?.trackNumber ?: 0,
            discNumber = tags?.discNumber ?: 1,
            year = tags?.year,
            genre = tags?.genre,
            bitrate = 0,
            path = entry.path
        )
    }

    private val albumCoverCache = HashMap<String, String>()

    private suspend fun readId3Tag(path: String): ByteArray? {
        // Read only the 10-byte ID3 header first. A 256 KB probe for every track would
        // waste gigabytes of NAS traffic on large libraries that contain no ID3 tag.
        val header = readRemoteRange(path, 0L, 9L) ?: return null
        val totalSize = Id3v2Parser.tagTotalSize(header) ?: return null
        val safeSize = minOf(totalSize, MAX_ID3_TAG_BYTES).coerceAtLeast(10)
        return if (safeSize == header.size) header
        else readRemoteRange(path, 0L, safeSize.toLong() - 1L) ?: header
    }

    private fun cacheArtwork(songId: String, mime: String?, bytes: ByteArray): String? {
        val cacheRoot = appContext?.cacheDir ?: return null
        return try {
            val directory = File(cacheRoot, "nas_music_covers")
            if (!directory.exists()) directory.mkdirs()
            if (!directory.isDirectory) return null
            val extension = when (mime?.lowercase(Locale.ROOT)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val file = File(directory, "$songId.$extension")
            if (!file.exists()) file.writeBytes(bytes)
            file.toURI().toString()
        } catch (e: Exception) {
            AppLog.w(TAG, "embedded artwork cache failed: ${safeFailureName(e)}")
            null
        }
    }

    private fun stableId(value: String, prefix: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return prefix + hex
    }

    private fun albumKey(artist: String, album: String): String =
        "${artist.trim().lowercase(Locale.ROOT)}\u0000${album.trim().lowercase(Locale.ROOT)}"

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    protected fun normalizeRemotePath(raw: String): String {
        val segments = mutableListOf<String>()
        raw.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return "/" + segments.joinToString("/")
    }

    private fun safeFailureName(error: Throwable): String =
        error.javaClass.simpleName.ifBlank { "NetworkError" }.take(48)
}
