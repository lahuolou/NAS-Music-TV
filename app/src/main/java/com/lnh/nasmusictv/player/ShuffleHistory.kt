package com.lnh.nasmusictv.player

/**
 * 按队列索引记录当前随机播放轮次中已播放的歌曲。
 * 队列重排、删除或整体替换后，旧索引不再代表同一首歌，调用方必须 reset。
 */
internal class ShuffleHistory {
    private val playedIndices = linkedSetOf<Int>()

    fun clear() = playedIndices.clear()

    fun reset(currentIndex: Int? = null) {
        playedIndices.clear()
        if (currentIndex != null && currentIndex >= 0) playedIndices.add(currentIndex)
    }

    fun markPlayed(index: Int) {
        if (index >= 0) playedIndices.add(index)
    }

    /** 返回当前轮未播放的合法索引；完成一轮后保留当前歌曲并开始下一轮。 */
    fun availableIndices(queueSize: Int, currentIndex: Int): List<Int> {
        if (queueSize <= 0) return emptyList()
        playedIndices.removeAll { it !in 0 until queueSize }
        if (playedIndices.size >= queueSize) {
            reset(currentIndex.takeIf { it in 0 until queueSize })
        }
        return (0 until queueSize).filterNot(playedIndices::contains)
    }
}

/** 空队列返回 null；非空队列把外部索引安全收敛到有效范围。 */
internal fun normalizeQueueStartIndex(queueSize: Int, requestedIndex: Int): Int? =
    if (queueSize <= 0) null else requestedIndex.coerceIn(0, queueSize - 1)
