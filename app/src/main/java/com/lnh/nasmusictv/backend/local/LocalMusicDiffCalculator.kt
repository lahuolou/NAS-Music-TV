package com.lnh.nasmusictv.backend.local

import com.lnh.nasmusictv.backend.local.db.LocalSongEntity

/** Result of comparing the latest device scan with the persisted local index. */
internal data class LocalMusicDiff(
    val newSongs: List<ScannedSong>,
    val updatedSongs: List<ScannedSong>
)

/**
 * Classifies new and changed local music rows in O(scanned + cached) time.
 *
 * The path is the stable lookup key used by LocalMusicRepository. Timestamp comparison is only
 * authoritative when the provider supplied a positive value; size and metadata comparisons still
 * catch changes on providers that do not expose DATE_MODIFIED.
 */
internal object LocalMusicDiffCalculator {

    fun calculate(
        scanned: List<ScannedSong>,
        cached: List<LocalSongEntity>
    ): LocalMusicDiff {
        val cachedByPath = cached.associateBy { it.path }
        val newSongs = ArrayList<ScannedSong>()
        val updatedSongs = ArrayList<ScannedSong>()

        for (song in scanned) {
            val path = song.contentUri.toString()
            val previous = cachedByPath[path]
            if (previous == null) {
                newSongs += song
            } else if (hasChanged(song, previous)) {
                updatedSongs += song
            }
        }

        return LocalMusicDiff(newSongs, updatedSongs)
    }

    /**
     * Used by full scans to keep derived cover files only when the indexed song is still identical.
     * A changed audio file or changed tags must not keep a potentially stale embedded cover.
     */
    fun isUnchanged(song: ScannedSong, cached: LocalSongEntity): Boolean =
        !hasChanged(song, cached)

    private fun hasChanged(song: ScannedSong, cached: LocalSongEntity): Boolean =
        song.mediaStoreId != cached.mediaStoreId ||
            song.title != cached.title ||
            song.artist != cached.artist ||
            song.album != cached.album ||
            song.albumId != cached.albumId ||
            song.duration != cached.duration ||
            song.size != cached.size ||
            song.dateAdded != cached.dateAdded ||
            song.mimeType != cached.mimeType ||
            song.volumeName != cached.volumeName ||
            song.storageType.name != cached.storageType ||
            song.year != cached.year ||
            song.genre != cached.genre ||
            (song.lastModified > 0L && song.lastModified != cached.lastModified)
}
