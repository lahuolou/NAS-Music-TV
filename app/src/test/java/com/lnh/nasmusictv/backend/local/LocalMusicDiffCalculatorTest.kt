package com.lnh.nasmusictv.backend.local

import android.net.Uri
import com.lnh.nasmusictv.backend.local.db.LocalSongEntity
import com.lnh.nasmusictv.data.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalMusicDiffCalculatorTest {

    private fun scanned(
        id: Long = 1L,
        uri: String = "content://media/external/audio/media/1",
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        size: Long = 1024L,
        lastModified: Long = 100L,
        dateAdded: Long = 50L
    ) = ScannedSong(
        mediaStoreId = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 7L,
        duration = 180_000L,
        size = size,
        dateAdded = dateAdded,
        mimeType = "audio/mpeg",
        contentUri = Uri.parse(uri),
        volumeName = "external_primary",
        storageType = StorageType.INTERNAL,
        year = 2024,
        genre = "Pop",
        lastModified = lastModified
    )

    private fun cached(song: ScannedSong, lastModified: Long = song.lastModified) = LocalSongEntity(
        mediaStoreId = song.mediaStoreId,
        title = song.title,
        artist = song.artist,
        album = song.album,
        albumId = song.albumId,
        duration = song.duration,
        size = song.size,
        dateAdded = song.dateAdded,
        mimeType = song.mimeType,
        contentUri = song.contentUri.toString(),
        volumeName = song.volumeName,
        storageType = song.storageType.name,
        path = song.contentUri.toString(),
        lastModified = lastModified,
        coverPath = "/cache/covers/song.jpg",
        year = song.year,
        genre = song.genre
    )

    @Test
    fun `unchanged row is not reinserted`() {
        val song = scanned()

        val diff = LocalMusicDiffCalculator.calculate(listOf(song), listOf(cached(song)))

        assertTrue(diff.newSongs.isEmpty())
        assertTrue(diff.updatedSongs.isEmpty())
    }

    @Test
    fun `different last modified timestamp marks row updated`() {
        val current = scanned(lastModified = 200L)
        val old = cached(current, lastModified = 100L)

        val diff = LocalMusicDiffCalculator.calculate(listOf(current), listOf(old))

        assertTrue(diff.newSongs.isEmpty())
        assertEquals(1, diff.updatedSongs.size)
        assertEquals(current, diff.updatedSongs.single())
    }

    @Test
    fun `metadata change is detected even when provider has no timestamp`() {
        val current = scanned(title = "Renamed", lastModified = 0L)
        val old = cached(scanned(title = "Old title", lastModified = 0L))

        val diff = LocalMusicDiffCalculator.calculate(listOf(current), listOf(old))

        assertEquals(1, diff.updatedSongs.size)
        assertEquals("Renamed", diff.updatedSongs.single().title)
    }

    @Test
    fun `file size change marks row updated`() {
        val current = scanned(size = 2048L, lastModified = 0L)
        val old = cached(scanned(size = 1024L, lastModified = 0L))

        val diff = LocalMusicDiffCalculator.calculate(listOf(current), listOf(old))

        assertEquals(1, diff.updatedSongs.size)
    }

    @Test
    fun `date added unit correction marks an existing row updated`() {
        val current = scanned(dateAdded = 50L)
        val old = cached(current).copy(dateAdded = 1_700_000_000_000L)

        val diff = LocalMusicDiffCalculator.calculate(listOf(current), listOf(old))

        assertEquals(1, diff.updatedSongs.size)
    }

    @Test
    fun `is unchanged when all indexed metadata and file timestamp match`() {
        val song = scanned()
        assertTrue(LocalMusicDiffCalculator.isUnchanged(song, cached(song)))
    }

    @Test
    fun `is changed when timestamp differs even if tags are identical`() {
        val current = scanned(lastModified = 200L)
        assertTrue(!LocalMusicDiffCalculator.isUnchanged(current, cached(current, lastModified = 100L)))
    }

    @Test
    fun `new path is classified as new`() {
        val current = scanned(id = 2L, uri = "content://media/external/audio/media/2")
        val old = cached(scanned())

        val diff = LocalMusicDiffCalculator.calculate(listOf(current), listOf(old))

        assertEquals(listOf(current), diff.newSongs)
        assertTrue(diff.updatedSongs.isEmpty())
    }
}
