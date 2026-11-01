package com.lnh.nasmusictv.data.model

import com.lnh.nasmusictv.util.ArtistSplitter

/**
 * 纯 Kotlin 歌手歌曲索引更新器。
 *
 * 用于大曲库分页扫描和歌手搜索结果合并。该类不持有可变全局状态：所有输入 Map/List
 * 均视为只读，返回新 Map；只有本批次触及的艺术家歌曲列表会被复制。
 */
object ArtistSongIndex {

    data class Maps(
        val songArtists: Map<String, List<String>>,
        val artistSongs: Map<String, List<Song>>
    )

    /**
     * 将一批歌曲增量合并到两个方向的歌手索引中。
     *
     * - map 仅做浅复制；只复制本批歌曲触及的艺术家列表；
     * - 相同 songId 用最新 Song 替换，避免元数据更新后详情页仍显示旧对象；
     * - artist 字段变化时，从旧歌手列表移除该曲，再加入新歌手列表；
     * - 删除采用延迟批量过滤，避免每删一首都重排并重建整份索引。
     */
    fun append(
        previousSongArtists: Map<String, List<String>>,
        previousArtistSongs: Map<String, List<Song>>,
        newSongs: List<Song>
    ): Maps {
        if (newSongs.isEmpty()) return Maps(previousSongArtists, previousArtistSongs)

        val songArtists = previousSongArtists.toMutableMap()
        val artistSongs = previousArtistSongs.toMutableMap()
        val changedLists = HashMap<String, MutableList<Song>>()
        val songIndexesByArtist = HashMap<String, MutableMap<String, Int>>()
        val removedSongIdsByArtist = HashMap<String, MutableSet<String>>()

        fun mutableSongs(key: String): MutableList<Song> = changedLists.getOrPut(key) {
            previousArtistSongs[key].orEmpty().toMutableList()
        }

        fun indexesFor(key: String, songs: MutableList<Song>): MutableMap<String, Int> =
            songIndexesByArtist.getOrPut(key) {
                HashMap<String, Int>(songs.size + 1).apply {
                    songs.forEachIndexed { index, song -> put(song.id, index) }
                }
            }

        for (song in newSongs) {
            val displayArtists = ArtistSplitter.split(song.artist)
            val newKeys = displayArtists.asSequence()
                .map(ArtistSplitter::normalizeKey)
                .filter(String::isNotBlank)
                .toCollection(LinkedHashSet())
            val oldKeys = songArtists[song.id].orEmpty().asSequence()
                .map(ArtistSplitter::normalizeKey)
                .filter(String::isNotBlank)
                .toSet()

            // 只登记删除；在批次结束时对每个受影响歌手执行一次过滤。
            for (oldKey in oldKeys - newKeys) {
                val songs = mutableSongs(oldKey)
                if (indexesFor(oldKey, songs).containsKey(song.id)) {
                    removedSongIdsByArtist.getOrPut(oldKey) { HashSet() }.add(song.id)
                }
            }

            songArtists[song.id] = displayArtists
            for (key in newKeys) {
                val songs = mutableSongs(key)
                // 同一批中歌曲可能先移出再移回该艺术家，重新关联时撤销删除标记。
                removedSongIdsByArtist[key]?.remove(song.id)
                val indexes = indexesFor(key, songs)
                val existingIndex = indexes[song.id]
                if (existingIndex == null) {
                    indexes[song.id] = songs.size
                    songs.add(song)
                } else {
                    songs[existingIndex] = song
                }
            }
        }

        for ((key, songs) in changedLists) {
            val removedIds = removedSongIdsByArtist[key]
            if (!removedIds.isNullOrEmpty()) songs.removeAll { it.id in removedIds }
            if (songs.isEmpty()) artistSongs.remove(key) else artistSongs[key] = songs
        }
        return Maps(songArtists, artistSongs)
    }

    /**
     * 把搜索命中的歌曲按拆分后的歌手名合并进当前索引。
     * 对每个被触及的歌手只建立一次 ID 集合、只复制一次列表，整体接近 O(N)。
     */
    fun mergeSearchResults(
        existingArtistSongs: Map<String, List<Song>>,
        searchResults: List<Song>
    ): Map<String, List<Song>> {
        if (searchResults.isEmpty()) return existingArtistSongs

        val merged = existingArtistSongs.toMutableMap()
        val changedLists = HashMap<String, MutableList<Song>>()
        val seenSongIds = HashMap<String, MutableSet<String>>()
        for (song in searchResults) {
            for (artistName in ArtistSplitter.split(song.artist)) {
                val key = ArtistSplitter.normalizeKey(artistName)
                if (key.isBlank()) continue
                val songs = changedLists.getOrPut(key) {
                    existingArtistSongs[key].orEmpty().toMutableList()
                }
                val ids = seenSongIds.getOrPut(key) {
                    songs.mapTo(HashSet(songs.size + 1)) { it.id }
                }
                if (ids.add(song.id)) songs.add(song)
            }
        }
        merged.putAll(changedLists)
        return merged
    }
}
