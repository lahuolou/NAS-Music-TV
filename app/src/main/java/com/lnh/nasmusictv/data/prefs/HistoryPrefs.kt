package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.reflect.TypeToken
import com.lnh.nasmusictv.data.model.NetworkFavoriteItem
import com.lnh.nasmusictv.data.model.PlaylistDefaults
import com.lnh.nasmusictv.data.model.SearchHistoryItem
import com.lnh.nasmusictv.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// ── 键（顶层 internal：AppPreferences 数据备份/恢复区也读写这些 key，同包共享）──

internal val keyRecentSongs = stringPreferencesKey("recent_songs")
internal val keyRecentSongObjects = stringPreferencesKey("recent_song_objects")
internal val keyPlayCounts = stringPreferencesKey("play_counts")
internal val keyNetworkFavorites = stringPreferencesKey("network_favorites")
internal val keyPlayRecords = stringPreferencesKey("play_records")
internal val keySearchHistory = stringPreferencesKey("search_history")

// ── 共享模型/扩展（包级 internal：AppPreferences 的队列/歌单/备份恢复区也使用）──

/** 最近播放歌曲对象 JSON 载体（Gson 结构：{"songs":[...]}） */
internal data class RecentSongObjectsData(val songs: List<Song> = emptyList())

/** 播放记录 JSON 载体（Gson 结构：{"records":[...]}） */
internal data class PlayRecordsData(
    val records: List<com.lnh.nasmusictv.data.model.PlayRecord> = emptyList()
)

/**
 * 持久化前的 streamUrl 清理（修复：已下载/本地歌曲恢复后无法播放）：
 * 仅网络歌曲置空（直链有时效，播放时需重新解析）；
 * 本地歌曲（含已下载入库，isLocalSong=true）的 file:// URI 永久有效，必须保留，
 * 否则重启恢复队列 / 最近播放 / 本地歌单场景下无法回放。
 */
internal fun Song.stripVolatileStreamUrl(): Song =
    // 2026-09-25 审查修复（凭据明文落盘）：NAS 歌曲 streamUrl 含长期凭据
    // （Jellyfin api_key / Navidrome·Subsonic t=md5(password+salt) / 道理鱼 JWT），
    // 此前仅 isNetworkSong 置空，NAS 凭据 URL 随队列恢复/最近播放/本地歌单明文写入 DataStore。
    // 现改为仅本地歌曲保留 file:// URI（恢复播放经 adapter.getSongsByIds 重建）。
    // 例外：imported_ 前缀（m3u/json 歌单导入的 URL 直链 stub）——其 streamUrl 是
    // 永久直链、不含凭据，是唯一播放来源（PlaylistImporter.toBareSong 直接落地），
    // 置空会导致导入歌单无法播放（PlaylistImporterTest 已覆盖）。
    if (!isLocalSong && !id.startsWith(PlaylistDefaults.IMPORTED_ID_PREFIX)) copy(streamUrl = null) else this

/** 搜索历史最大保留条数（超出裁剪尾部） */
private val searchHistoryMaxSize = 200

/** 搜索历史 TTL（30 天，超出清除） */
private val searchHistoryTtlMs = 30L * 24 * 60 * 60 * 1000

/** 最近播放 ID 列表最大长度（LRU，超出移除最旧） */
private val recentSongsMaxSize = 50

/** 最近播放完整歌曲对象最大保留数 */
private val recentSongsObjectsMaxSize = 50

/** 网络收藏最大条数（LRU，超出移除最旧） */
private val networkFavoritesMaxSize = 500

/** 播放记录最多保留条数 */
private const val playRecordsMaxSize = 500

/**
 * R-4 历史记录域子 Prefs（最近播放/播放次数/播放记录/网络收藏/搜索历史；键不迁移）。
 *
 * 实现已全量下移（不再委托 AppPreferences）；AppPreferences 数据备份/恢复区
 * 通过本类的 public API 与顶层 internal key 做横切读写。
 */
class HistoryPrefs internal constructor(private val prefs: AppPreferences) {

    // ========== 最近播放 ID 列表 + 播放次数（B-2） ==========

    val recentSongIds: Flow<List<String>> = prefs.dataStoreData().map { data ->
        val json = data[keyRecentSongs] ?: "[]"
        try {
            prefs.prefsGson().fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    // --- B-2 播放次数 Flow ---
    val playCounts: Flow<Map<String, Int>> = prefs.dataStoreData().map { data ->
        val json = data[keyPlayCounts] ?: "{}"
        try {
            prefs.prefsGson().fromJson<Map<String, Int>>(json, object : TypeToken<Map<String, Int>>() {}.type)
        } catch (e: Exception) { emptyMap() }
    }

    /**
     * 获取最近播放 ID 列表（一次性读取，调用方需在协程中）
     */
    suspend fun getRecentSongIds(): List<String> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val recentJson = data[keyRecentSongs] ?: "[]"
                prefs.prefsGson().fromJson<List<String>>(recentJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 记录一次播放（B-2）
     * 1. 添加到最近播放列表（去重 + LRU，最多 50 条）
     * 2. 播放次数 +1
     */
    suspend fun recordPlay(songId: String) {
        prefs.prefsEdit { data ->
            // 更新最近播放
            val recentJson = data[keyRecentSongs] ?: "[]"
            val recentList = PrefsJson.parse("recent_songs", recentJson) {
                prefs.prefsGson().fromJson<MutableList<String>>(recentJson, object : TypeToken<MutableList<String>>() {}.type)
            }
            if (recentList != null) {
                val mutableRecent = recentList.toMutableList()
                mutableRecent.remove(songId) // 去重
                mutableRecent.add(0, songId)  // 最新放最前面
                if (mutableRecent.size > recentSongsMaxSize) {
                    mutableRecent.removeAt(mutableRecent.lastIndex)
                }
                data[keyRecentSongs] = prefs.prefsGson().toJson(mutableRecent)
            }

            // 更新播放次数
            val countsJson = data[keyPlayCounts] ?: "{}"
            val counts = PrefsJson.parse("play_counts", countsJson) {
                prefs.prefsGson().fromJson<MutableMap<String, Int>>(countsJson, object : TypeToken<MutableMap<String, Int>>() {}.type)
            }
            if (counts != null) {
                counts[songId] = (counts[songId] ?: 0) + 1
                data[keyPlayCounts] = prefs.prefsGson().toJson(counts)
            }
        }
    }

    // ========== 最近播放完整歌曲对象（B-2b，含网络歌曲，供"最近播放"区展示/播放） ==========

    /**
     * 记录一次最近播放的完整歌曲对象（网络歌曲 streamUrl 置空）。
     * 与 recordPlay 的 id 列表互补：这里存完整元数据，支持网络歌曲/未连 NAS 时展示。
     */
    suspend fun recordRecentSongObject(song: Song) {
        prefs.prefsEdit { data ->
            val json = data[keyRecentSongObjects] ?: "{\"songs\":[]}"
            val parsed = PrefsJson.parse("recent_song_objects", json) {
                prefs.prefsGson().fromJson<RecentSongObjectsData>(json, RecentSongObjectsData::class.java)
            } ?: return@prefsEdit
            val list = parsed.songs.toMutableList()
            list.removeAll { it.id == song.id } // 去重（保留最新一条）
            // 网络歌曲 streamUrl 置空（直链有时效）；本地歌曲保留 file:// URI
            list.add(0, song.stripVolatileStreamUrl())
            if (list.size > recentSongsObjectsMaxSize) {
                list.removeAt(list.lastIndex)
            }
            data[keyRecentSongObjects] = prefs.prefsGson().toJson(RecentSongObjectsData(list))
        }
    }

    /**
     * 合并记录播放：单次 DataStore edit 同时更新 id 列表 + 播放次数 + 完整歌曲对象。
     * 替代分别调用 [recordPlay] + [recordRecentSongObject]（两次 DataStore 写）。
     */
    suspend fun recordPlayWithSong(song: Song) {
        prefs.prefsEdit { data ->
            val songId = song.id

            // 1. 更新最近播放 id 列表（去重 + LRU，最多 50 条）
            val recentJson = data[keyRecentSongs] ?: "[]"
            val recentList = PrefsJson.parse("recent_songs", recentJson) {
                prefs.prefsGson().fromJson<MutableList<String>>(recentJson, object : TypeToken<MutableList<String>>() {}.type)
            }
            if (recentList != null) {
                recentList.remove(songId)
                recentList.add(0, songId)
                if (recentList.size > recentSongsMaxSize) {
                    recentList.removeAt(recentList.lastIndex)
                }
                data[keyRecentSongs] = prefs.prefsGson().toJson(recentList)
            }

            // 2. 更新播放次数
            val countsJson = data[keyPlayCounts] ?: "{}"
            val counts = PrefsJson.parse("play_counts", countsJson) {
                prefs.prefsGson().fromJson<MutableMap<String, Int>>(countsJson, object : TypeToken<MutableMap<String, Int>>() {}.type)
            }
            if (counts != null) {
                counts[songId] = (counts[songId] ?: 0) + 1
                data[keyPlayCounts] = prefs.prefsGson().toJson(counts)
            }

            // 3. 更新完整歌曲对象（含网络歌曲，streamUrl 置空）
            val objJson = data[keyRecentSongObjects] ?: "{\"songs\":[]}"
            val objData = PrefsJson.parse("recent_song_objects", objJson) {
                prefs.prefsGson().fromJson<RecentSongObjectsData>(objJson, RecentSongObjectsData::class.java)
            }
            if (objData != null) {
                val objList = objData.songs.toMutableList()
                objList.removeAll { it.id == songId }
                objList.add(0, song.stripVolatileStreamUrl())
                if (objList.size > recentSongsObjectsMaxSize) {
                    objList.removeAt(objList.lastIndex)
                }
                data[keyRecentSongObjects] = prefs.prefsGson().toJson(RecentSongObjectsData(objList))
            }

            // 4. 月度统计（F2-1）：与上述键同一次 edit 原子写入
            val now = System.currentTimeMillis()
            prefs.playStatsRepo.appendMonthlyPlayInEdit(data, songId, now)
            // 5. 按天统计（F2-5 热力图）：同一次 edit，保证与月度口径一致
            prefs.playStatsRepo.appendDailyPlayInEdit(data, now)
        }
    }

    /**
     * 获取最近播放歌曲对象（最新在前，streamUrl 为空需播放时重新解析）
     */
    suspend fun getRecentSongObjects(): List<Song> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keyRecentSongObjects] ?: "{\"songs\":[]}"
                val parsed = prefs.prefsGson().fromJson<RecentSongObjectsData>(json, RecentSongObjectsData::class.java)
                parsed.songs
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 一次性把 play_records 回填进按天统计（F2-5 热力图）。
     *
     * 幂等且零日常开销：标记已置位时**直接返回，不进入 edit**。
     * 由统计页首次加载时触发（见 PlayStatsViewModel.loadStats）。
     */
    suspend fun backfillDailyStatsOnce() {
        if (prefs.dataStoreData().first()[prefs.playStatsRepo.keyDailyBackfilled] == true) return
        prefs.prefsEdit { data ->
            val json = data[keyPlayRecords] ?: return@prefsEdit
            val parsed = PrefsJson.parse("play_records", json) {
                prefs.prefsGson().fromJson<PlayRecordsData>(json, PlayRecordsData::class.java)
            } ?: return@prefsEdit
            prefs.playStatsRepo.backfillDailyInEdit(data, parsed.records)
        }
    }

    // ========== 播放记录（PlayStats 明细，F2-1/F2-5） ==========

    /**
     * 记录一次播放
     */
    suspend fun addPlayRecord(record: com.lnh.nasmusictv.data.model.PlayRecord) {
        prefs.prefsEdit { data ->
            val json = data[keyPlayRecords] ?: "{\"records\":[]}"
            val parsed = PrefsJson.parse("play_records", json) {
                prefs.prefsGson().fromJson<PlayRecordsData>(json, PlayRecordsData::class.java)
            } ?: return@prefsEdit  // 解析失败：跳过回写，保留原数据
            // 最多保留 500 条记录
            val updated = PlayRecordsData(
                records = (listOf(record) + parsed.records).take(playRecordsMaxSize)
            )
            data[keyPlayRecords] = prefs.prefsGson().toJson(updated)
        }
    }

    /**
     * 获取所有播放记录
     */
    suspend fun getPlayRecords(): List<com.lnh.nasmusictv.data.model.PlayRecord> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keyPlayRecords] ?: return emptyList()
                val parsed = prefs.prefsGson().fromJson<PlayRecordsData>(json, PlayRecordsData::class.java)
                parsed.records
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清除所有播放记录
     */
    suspend fun clearPlayRecords() {
        prefs.prefsEdit { data ->
            data.remove(keyPlayRecords)
        }
    }

    // ========== 网络歌曲收藏（LRU 500） ==========

    val networkFavorites: Flow<List<NetworkFavoriteItem>> = prefs.dataStoreData().map { data ->
        val json = data[keyNetworkFavorites] ?: "[]"
        try {
            prefs.prefsGson().fromJson<List<NetworkFavoriteItem>>(json, object : TypeToken<List<NetworkFavoriteItem>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    /**
     * 同步获取网络收藏列表（用于 isFavorite 判断，非 Flow）
     */
    suspend fun getNetworkFavorites(): List<NetworkFavoriteItem> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keyNetworkFavorites] ?: "[]"
                prefs.prefsGson().fromJson<List<NetworkFavoriteItem>>(json, object : TypeToken<List<NetworkFavoriteItem>>() {}.type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 切换网络收藏状态（已收藏则取消，未收藏则添加）
     * 新收藏的歌曲添加到列表头部（最新在前）
     * 超过 networkFavoritesMaxSize（500 条）时自动清理最旧的收藏
     */
    suspend fun toggleNetworkFavorite(item: NetworkFavoriteItem) {
        prefs.prefsEdit { data ->
            val json = data[keyNetworkFavorites] ?: "[]"
            val list = PrefsJson.parse("network_favorites", json) {
                prefs.prefsGson().fromJson<MutableList<NetworkFavoriteItem>>(json, object : TypeToken<MutableList<NetworkFavoriteItem>>() {}.type)
            } ?: return@prefsEdit  // 解析失败：跳过回写，保留原数据

            val mutable = list.toMutableList()
            val existing = mutable.indexOfFirst { it.songId == item.songId }
            if (existing >= 0) {
                mutable.removeAt(existing)  // 取消收藏
            } else {
                mutable.add(0, item)  // 添加收藏（最新在前）
                // LRU 上限：超过 500 条时移除最旧的（列表末尾）
                while (mutable.size > networkFavoritesMaxSize) {
                    mutable.removeAt(mutable.size - 1)
                }
            }
            data[keyNetworkFavorites] = prefs.prefsGson().toJson(mutable)
        }
    }

    // ========== 搜索历史（实现已下移，本类自持） ==========

    /**
     * 搜索历史列表 Flow（响应式）
     * 列表按 lastSearchedAt 降序（最新在前）
     */
    val searchHistory: Flow<List<SearchHistoryItem>> = prefs.dataStoreData().map { data ->
        val json = data[keySearchHistory] ?: "[]"
        try {
            prefs.prefsGson().fromJson<List<SearchHistoryItem>>(json, object : TypeToken<List<SearchHistoryItem>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    /**
     * 一次性读取搜索历史
     */
    suspend fun getSearchHistory(): List<SearchHistoryItem> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keySearchHistory] ?: "[]"
                prefs.prefsGson().fromJson<List<SearchHistoryItem>>(json, object : TypeToken<List<SearchHistoryItem>>() {}.type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 记录一次搜索
     * 1. 同名 query 合并：count+1、lastSearchedAt 更新为当前时间、移到列表头部
     * 2. 清理超过 30 天的条目
     * 3. 超过 200 条上限时裁剪尾部
     */
    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        prefs.prefsEdit { data ->
            val json = data[keySearchHistory] ?: "[]"
            val list = PrefsJson.parse("search_history", json) {
                prefs.prefsGson().fromJson<MutableList<SearchHistoryItem>>(json, object : TypeToken<MutableList<SearchHistoryItem>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            // 合并同名条目
            val existingIdx = mutable.indexOfFirst { it.query == trimmed }
            if (existingIdx >= 0) {
                val existing = mutable.removeAt(existingIdx)
                mutable.add(0, existing.copy(lastSearchedAt = now, count = existing.count + 1))
            } else {
                mutable.add(0, SearchHistoryItem(query = trimmed, lastSearchedAt = now, count = 1))
            }
            // 30 天 TTL 清理
            val cutoff = now - searchHistoryTtlMs
            mutable.removeAll { it.lastSearchedAt < cutoff }
            // 数量上限裁剪
            if (mutable.size > searchHistoryMaxSize) {
                mutable.subList(searchHistoryMaxSize, mutable.size).clear()
            }
            data[keySearchHistory] = prefs.prefsGson().toJson(mutable)
        }
    }

    /**
     * 清理超过 30 天的搜索历史条目
     * 在应用启动时调用一次
     */
    suspend fun purgeExpiredSearchHistory() {
        val now = System.currentTimeMillis()
        val cutoff = now - searchHistoryTtlMs
        prefs.prefsEdit { data ->
            val json = data[keySearchHistory] ?: return@prefsEdit
            val list = PrefsJson.parse("search_history", json) {
                prefs.prefsGson().fromJson<MutableList<SearchHistoryItem>>(json, object : TypeToken<MutableList<SearchHistoryItem>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            val beforeSize = mutable.size
            mutable.removeAll { it.lastSearchedAt < cutoff }
            if (mutable.size != beforeSize) {
                data[keySearchHistory] = prefs.prefsGson().toJson(mutable)
            }
        }
    }
}
