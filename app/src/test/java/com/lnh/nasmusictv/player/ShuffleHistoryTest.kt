package com.lnh.nasmusictv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShuffleHistoryTest {
    @Test
    fun `empty queue has no valid start index`() {
        assertNull(normalizeQueueStartIndex(0, 0))
        assertNull(normalizeQueueStartIndex(-1, 4))
    }

    @Test
    fun `requested start index is clamped`() {
        assertEquals(0, normalizeQueueStartIndex(5, -1))
        assertEquals(4, normalizeQueueStartIndex(5, 12))
        assertEquals(2, normalizeQueueStartIndex(5, 2))
    }

    @Test
    fun `history excludes played items`() {
        val history = ShuffleHistory()
        history.reset(1)
        history.markPlayed(3)
        assertEquals(listOf(0, 2), history.availableIndices(4, 1))
    }

    @Test
    fun `history discards indices removed after queue shrink`() {
        val history = ShuffleHistory()
        history.reset(0)
        history.markPlayed(3)
        assertEquals(listOf(1, 2), history.availableIndices(3, 0))
    }

    @Test
    fun `completed round preserves current track as first item of next round`() {
        val history = ShuffleHistory()
        history.reset(0)
        history.markPlayed(1)
        history.markPlayed(2)
        assertEquals(listOf(1, 2), history.availableIndices(3, 0))
        history.markPlayed(1)
        history.markPlayed(2)
        assertEquals(listOf(1, 2), history.availableIndices(3, 0))
    }

    @Test
    fun `queue mutation resets history to current index only`() {
        val history = ShuffleHistory()
        history.reset(0)
        history.markPlayed(1)
        history.markPlayed(2)
        history.reset(2)
        assertEquals(listOf(0, 1), history.availableIndices(3, 2))
    }
}
