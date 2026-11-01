package com.lnh.nasmusictv.player

import com.lnh.nasmusictv.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseCacheTest {
    private fun song(id: String) = Song(id = id, title = "title-$id")

    @Test
    fun `batch lookup keeps input order and accepts full or bare ids`() {
        val cache = BrowseCache()
        cache.putAll(listOf(song("a"), song("b"), song("c")))

        val result = cache.songsOf(listOf("song/c", "missing", "song/a", "b"))

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
    }

    @Test
    fun `batch lookup drops blank and missing ids`() {
        val cache = BrowseCache()
        cache.put(song("a"))

        assertEquals(emptyList<Song>(), cache.songsOf(listOf("", " ", "missing")))
        assertNull(cache.songOf("song/missing"))
    }

    @Test
    fun `batch lookup refreshes LRU access order`() {
        val cache = BrowseCache(maxSize = 2)
        cache.putAll(listOf(song("a"), song("b")))
        cache.songsOf(listOf("song/a"))
        cache.put(song("c"))

        assertNull(cache.songOf("b"))
        assertEquals("a", cache.songOf("a")?.id)
        assertEquals("c", cache.songOf("c")?.id)
    }
}
