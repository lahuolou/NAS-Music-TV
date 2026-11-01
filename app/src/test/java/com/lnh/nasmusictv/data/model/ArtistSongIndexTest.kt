package com.lnh.nasmusictv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSongIndexTest {
    private fun song(id: String, title: String = id, artist: String = "Artist A") =
        Song(id = id, title = title, artist = artist)

    @Test
    fun append_addsSongToEverySplitArtist_withoutMutatingPreviousMaps() {
        val emptySongArtists = emptyMap<String, List<String>>()
        val emptyArtistSongs = emptyMap<String, List<Song>>()
        val result = ArtistSongIndex.append(emptySongArtists, emptyArtistSongs, listOf(song("1", artist = "Artist A / Artist B")))

        assertEquals(0, emptySongArtists.size)
        assertEquals(0, emptyArtistSongs.size)
        assertEquals(listOf("Artist A", "Artist B"), result.songArtists["1"])
        assertEquals(listOf("1"), result.artistSongs["artist a"]?.map { it.id })
        assertEquals(listOf("1"), result.artistSongs["artist b"]?.map { it.id })
        assertTrue(result.songArtists.containsKey("1"))
    }

    @Test
    fun append_replacesSameIdWithUpdatedMetadata() {
        val original = song("1", title = "Old title")
        val previous = mapOf("1" to listOf("Artist A"))
        val previousArtists = mapOf("artist a" to listOf(original))
        val updated = song("1", title = "New title")

        val result = ArtistSongIndex.append(previous, previousArtists, listOf(updated))

        assertEquals("New title", result.artistSongs.getValue("artist a").single().title)
        assertSame(original, previousArtists.getValue("artist a").single())
    }

    @Test
    fun append_removesSongFromOldArtistWhenArtistChanges() {
        val original = song("1", artist = "Artist A")
        val retained = song("2", artist = "Artist A")
        val previous = mapOf("1" to listOf("Artist A"), "2" to listOf("Artist A"))
        val previousArtists = mapOf("artist a" to listOf(original, retained))

        val result = ArtistSongIndex.append(previous, previousArtists, listOf(song("1", artist = "Artist B")))

        assertEquals(listOf("2"), result.artistSongs.getValue("artist a").map { it.id })
        assertEquals(listOf("1"), result.artistSongs.getValue("artist b").map { it.id })
    }

    @Test
    fun append_canRemoveLastSongAndRemoveEmptyArtistBucket() {
        val previous = mapOf("1" to listOf("Artist A"))
        val previousArtists = mapOf("artist a" to listOf(song("1")))

        val result = ArtistSongIndex.append(previous, previousArtists, listOf(song("1", artist = "")))

        assertFalse(result.artistSongs.containsKey("artist a"))
        assertTrue(result.songArtists.getValue("1").isEmpty())
    }

    @Test
    fun mergeSearchResults_deduplicatesAgainstExistingAndWithinSearchBatch() {
        val original = song("1")
        val existing = mapOf("artist a" to listOf(original))
        val result = ArtistSongIndex.mergeSearchResults(
            existing,
            listOf(song("1", title = "Duplicate"), song("2"), song("2", title = "Duplicate 2"))
        )

        assertEquals(listOf("1", "2"), result.getValue("artist a").map { it.id })
        assertSame(original, result.getValue("artist a").first())
    }

    @Test
    fun mergeSearchResults_handlesCollaborationsAndLeavesUnrelatedBucketsShared() {
        val untouched = listOf(song("x", artist = "Unrelated"))
        val result = ArtistSongIndex.mergeSearchResults(
            mapOf("unrelated" to untouched),
            listOf(song("1", artist = "Artist A / Artist B"))
        )

        assertSame(untouched, result["unrelated"])
        assertEquals(listOf("1"), result.getValue("artist a").map { it.id })
        assertEquals(listOf("1"), result.getValue("artist b").map { it.id })
    }
}
