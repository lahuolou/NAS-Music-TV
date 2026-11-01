package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.data.model.Song
import kotlinx.coroutines.flow.first

// ── 键（顶层 internal：AppPreferences 数据备份/恢复区也读写，同包共享）──

internal val keyLastQueue = stringPreferencesKey("last_queue")

/** 上次播放队列的持久化数据结构（streamUrl 置空，播放时重新解析） */
data class LastQueueData(
    val songs: List<Song>,
    val currentIndex: Int
)

/**
 * R-4 上次播放队列域子 Prefs（键不迁移）。
 *
 * 实现已全量下移（不再委托 AppPreferences）；AppPreferences 数据备份/恢复区
 * 通过本类的 public API 与顶层 internal key 做横切读写。
 */
class QueuePrefs internal constructor(private val prefs: AppPreferences) {

    /**
     * 保存上次播放队列
     * 仅网络歌曲 streamUrl 置空后序列化（直链有时效）；本地歌曲保留 file:// URI
     */
    suspend fun saveLastQueue(songs: List<Song>, currentIndex: Int) {
        prefs.prefsEdit { data ->
            val songsToSave = songs.map { it.stripVolatileStreamUrl() }
            val queueData = LastQueueData(songsToSave, currentIndex)
            data[keyLastQueue] = prefs.prefsGson().toJson(queueData)
        }
    }

    /**
     * 读取上次播放队列（用于应用启动时恢复，调用方需在协程中）
     */
    suspend fun getLastQueue(): LastQueueData? {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keyLastQueue] ?: return@let null
                prefs.prefsGson().fromJson<LastQueueData>(json, LastQueueData::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 清除上次播放队列（用户主动清空队列时调用）
     */
    suspend fun clearLastQueue() {
        prefs.prefsEdit { data ->
            data.remove(keyLastQueue)
        }
    }
}
