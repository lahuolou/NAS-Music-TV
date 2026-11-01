package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.reflect.TypeToken
import com.lnh.nasmusictv.data.model.ImportedBackupRecord
import com.lnh.nasmusictv.data.model.LocalPlaylist
import com.lnh.nasmusictv.data.model.PlaylistImportHistoryItem
import com.lnh.nasmusictv.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// ── 键（顶层 internal：AppPreferences 数据备份/恢复区也读写这些 key，同包共享）──

internal val keyLocalPlaylists = stringPreferencesKey("local_playlists")
internal val keyPlaylistImportHistory = stringPreferencesKey("playlist_import_history")
internal val keyImportedBackups = stringPreferencesKey("imported_backup_files")
internal val keySongReachability = stringPreferencesKey("song_reachability")

/** URL 可达性持久化判定窗口（24h）：窗口内的「不可达」标记重启后不重测 */
internal const val songReachabilityWindowMs = 24 * 60 * 60 * 1000L

/** URL 可达性持久化条目（songId → 判定；只记录非 REACHABLE 判定） */
data class ReachabilityEntry(val result: String, val checkedAt: Long)

/**
 * R-4 本地歌单域子 Prefs（localPlaylists / 歌单导入历史 / 备份文件登记 / URL 可达性；
 * JSON 序列化；键不迁移）。
 *
 * 实现已全量下移（不再委托 AppPreferences）；AppPreferences 数据备份/恢复区
 * 通过本类的 public API 与顶层 internal key 做横切读写。
 */
class PlaylistPrefs internal constructor(private val prefs: AppPreferences) {

    /** 歌单导入历史最大保留条数（超出按 importedAt 淘汰最旧） */
    private val playlistImportHistoryMaxSize = 20

    /** 「从本地文件导入」挑过的备份文件最大登记条数（超出淘汰最旧） */
    private val importedBackupsMaxSize = 20

    // ========== 本地歌单（DataStore JSON，独立于 NAS 后端歌单） ==========

    /**
     * 本地歌单列表 Flow（响应式，歌单变化时自动更新）
     */
    val localPlaylists: Flow<List<LocalPlaylist>> = prefs.dataStoreData().map { data ->
        val json = data[keyLocalPlaylists] ?: "[]"
        try {
            prefs.prefsGson().fromJson<List<LocalPlaylist>>(json, object : TypeToken<List<LocalPlaylist>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    /**
     * 一次性读取本地歌单列表
     */
    suspend fun getLocalPlaylists(): List<LocalPlaylist> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keyLocalPlaylists] ?: "[]"
                prefs.prefsGson().fromJson<List<LocalPlaylist>>(json, object : TypeToken<List<LocalPlaylist>>() {}.type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 创建本地歌单（最新创建的放最前）
     */
    suspend fun createLocalPlaylist(name: String): LocalPlaylist {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name must not be empty" }
        lateinit var created: LocalPlaylist
        prefs.prefsEdit { data ->
            val json = data[keyLocalPlaylists] ?: "[]"
            val list = PrefsJson.parse("local_playlists", json) {
                prefs.prefsGson().fromJson<MutableList<LocalPlaylist>>(json, object : TypeToken<MutableList<LocalPlaylist>>() {}.type)
            } ?: mutableListOf()  // 歌单数据损坏时仍可创建新歌单（原数据保留，本函数不覆盖）

            val playlist = LocalPlaylist(
                id = java.util.UUID.randomUUID().toString(),
                name = trimmed,
                createdAt = System.currentTimeMillis()
            )
            val mutable = list.toMutableList()
            mutable.add(0, playlist)
            data[keyLocalPlaylists] = prefs.prefsGson().toJson(mutable)
            created = playlist
        }
        return created
    }

    /**
     * 重命名本地歌单
     */
    suspend fun renameLocalPlaylist(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        prefs.prefsEdit { data ->
            val json = data[keyLocalPlaylists] ?: "[]"
            val list = PrefsJson.parse("local_playlists", json) {
                prefs.prefsGson().fromJson<MutableList<LocalPlaylist>>(json, object : TypeToken<MutableList<LocalPlaylist>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            val idx = mutable.indexOfFirst { it.id == id }
            if (idx >= 0) {
                mutable[idx] = mutable[idx].copy(name = trimmed)
                data[keyLocalPlaylists] = prefs.prefsGson().toJson(mutable)
            }
        }
    }

    /**
     * 删除本地歌单
     */
    suspend fun deleteLocalPlaylist(id: String) {
        prefs.prefsEdit { data ->
            val json = data[keyLocalPlaylists] ?: "[]"
            val list = PrefsJson.parse("local_playlists", json) {
                prefs.prefsGson().fromJson<MutableList<LocalPlaylist>>(json, object : TypeToken<MutableList<LocalPlaylist>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            if (mutable.removeAll { it.id == id }) {
                data[keyLocalPlaylists] = prefs.prefsGson().toJson(mutable)
            }
        }
    }

    /**
     * 添加歌曲到本地歌单（按 song.id 去重；仅网络歌曲 streamUrl 置空不持久化）
     * @return true 添加成功；false 歌单不存在或歌曲已在歌单中
     */
    suspend fun addSongToPlaylist(playlistId: String, song: Song): Boolean {
        var added = false
        prefs.prefsEdit { data ->
            val json = data[keyLocalPlaylists] ?: "[]"
            val list = PrefsJson.parse("local_playlists", json) {
                prefs.prefsGson().fromJson<MutableList<LocalPlaylist>>(json, object : TypeToken<MutableList<LocalPlaylist>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            val idx = mutable.indexOfFirst { it.id == playlistId }
            if (idx < 0) {
                added = false
            } else {
                val current = mutable[idx]
                if (current.songs.any { it.id == song.id }) {
                    added = false
                } else {
                    mutable[idx] = current.copy(songs = current.songs + song.stripVolatileStreamUrl())
                    data[keyLocalPlaylists] = prefs.prefsGson().toJson(mutable)
                    added = true
                }
            }
        }
        return added
    }

    /**
     * 从本地歌单移除歌曲
     */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        prefs.prefsEdit { data ->
            val json = data[keyLocalPlaylists] ?: "[]"
            val list = PrefsJson.parse("local_playlists", json) {
                prefs.prefsGson().fromJson<MutableList<LocalPlaylist>>(json, object : TypeToken<MutableList<LocalPlaylist>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            val idx = mutable.indexOfFirst { it.id == playlistId }
            if (idx >= 0) {
                val current = mutable[idx]
                mutable[idx] = current.copy(songs = current.songs.filterNot { it.id == songId })
                data[keyLocalPlaylists] = prefs.prefsGson().toJson(mutable)
            }
        }
    }

    /**
     * 用 newSong 替换歌单中的旧歌曲（补全命中后写回）。
     * oldSongId 不存在则忽略（并发补全时目标可能已被替换）。
     */
    suspend fun replaceSongInPlaylist(playlistId: String, oldSongId: String, newSong: Song) {
        prefs.prefsEdit { data ->
            val json = data[keyLocalPlaylists] ?: "[]"
            val list = PrefsJson.parse("local_playlists", json) {
                prefs.prefsGson().fromJson<MutableList<LocalPlaylist>>(json, object : TypeToken<MutableList<LocalPlaylist>>() {}.type)
            } ?: return@prefsEdit

            val mutable = list.toMutableList()
            val idx = mutable.indexOfFirst { it.id == playlistId }
            if (idx < 0) return@prefsEdit
            val current = mutable[idx]
            val songIdx = current.songs.indexOfFirst { it.id == oldSongId }
            if (songIdx < 0) return@prefsEdit
            val updatedSongs = current.songs.toMutableList().apply {
                this[songIdx] = newSong.stripVolatileStreamUrl()
            }
            mutable[idx] = current.copy(songs = updatedSongs)
            data[keyLocalPlaylists] = prefs.prefsGson().toJson(mutable)
        }
    }

    // ========== 歌单导入历史（最近 20 条；删除歌单时联动清理） ==========

    val playlistImportHistory: Flow<List<PlaylistImportHistoryItem>> =
        prefs.dataStoreData().map { data ->
            val json = data[keyPlaylistImportHistory] ?: "[]"
            try {
                prefs.prefsGson().fromJson<List<PlaylistImportHistoryItem>>(
                    json,
                    object : TypeToken<List<PlaylistImportHistoryItem>>() {}.type
                ) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        }

    suspend fun recordPlaylistImport(item: PlaylistImportHistoryItem) {
        prefs.prefsEdit { data ->
            val json = data[keyPlaylistImportHistory] ?: "[]"
            val list = PrefsJson.parse("playlist_import_history", json) {
                prefs.prefsGson().fromJson<MutableList<PlaylistImportHistoryItem>>(
                    json,
                    object : TypeToken<MutableList<PlaylistImportHistoryItem>>() {}.type
                )
            } ?: mutableListOf()
            val mutable = list.toMutableList()
            mutable.removeAll { it.playlistId == item.playlistId }
            mutable.add(0, item)
            while (mutable.size > playlistImportHistoryMaxSize) {
                mutable.removeAt(mutable.size - 1)
            }
            data[keyPlaylistImportHistory] = prefs.prefsGson().toJson(mutable)
        }
    }

    /** 删除歌单时联动清理导入历史（无单独删除入口，UI 侧由 consumeHistoryIfDeleted 调用） */
    suspend fun deletePlaylistImportHistory(playlistId: String) {
        prefs.prefsEdit { data ->
            val json = data[keyPlaylistImportHistory] ?: "[]"
            val list = PrefsJson.parse("playlist_import_history", json) {
                prefs.prefsGson().fromJson<MutableList<PlaylistImportHistoryItem>>(
                    json,
                    object : TypeToken<MutableList<PlaylistImportHistoryItem>>() {}.type
                )
            } ?: return@prefsEdit
            val mutable = list.toMutableList()
            if (mutable.removeAll { it.playlistId == playlistId }) {
                data[keyPlaylistImportHistory] = prefs.prefsGson().toJson(mutable)
            }
        }
    }

    // ========== 「从本地文件导入」挑过的备份文件（最近 20 条，按显示名去重） ==========

    val importedBackups: Flow<List<ImportedBackupRecord>> =
        prefs.dataStoreData().map { data ->
            val json = data[keyImportedBackups] ?: "[]"
            try {
                prefs.prefsGson().fromJson<List<ImportedBackupRecord>>(
                    json,
                    object : TypeToken<List<ImportedBackupRecord>>() {}.type
                ) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        }

    /** 一次性读取（备份列表刷新是同步调用链，不为它开订阅） */
    suspend fun getImportedBackups(): List<ImportedBackupRecord> =
        importedBackups.first()

    /**
     * 登记一个「用户在选择器里挑过并恢复成功」的备份文件。
     *
     * 为什么必须由调用方显式登记：这类文件不归本 app 所有（`owner_package_name` 是 shell/下载器），
     * `MediaStore.Downloads` 查询对本 app 不可见 ⇒ 不登记就永远不会出现在备份列表里。
     */
    suspend fun recordImportedBackup(item: ImportedBackupRecord) {
        prefs.prefsEdit { data ->
            val json = data[keyImportedBackups] ?: "[]"
            val list = PrefsJson.parse("imported_backup_files", json) {
                prefs.prefsGson().fromJson<MutableList<ImportedBackupRecord>>(
                    json,
                    object : TypeToken<MutableList<ImportedBackupRecord>>() {}.type
                )
            } ?: mutableListOf()
            val mutable = list.toMutableList()
            mutable.removeAll { it.displayName == item.displayName }
            mutable.add(0, item)
            while (mutable.size > importedBackupsMaxSize) {
                mutable.removeAt(mutable.size - 1)
            }
            data[keyImportedBackups] = prefs.prefsGson().toJson(mutable)
        }
    }

    /** 删除备份文件时联动清理登记（否则列表里残留一条读不到的死行） */
    suspend fun removeImportedBackup(uri: String) {
        prefs.prefsEdit { data ->
            val json = data[keyImportedBackups] ?: "[]"
            val list = PrefsJson.parse("imported_backup_files", json) {
                prefs.prefsGson().fromJson<MutableList<ImportedBackupRecord>>(
                    json,
                    object : TypeToken<MutableList<ImportedBackupRecord>>() {}.type
                )
            } ?: return@prefsEdit
            val mutable = list.toMutableList()
            if (mutable.removeAll { it.uri == uri }) {
                data[keyImportedBackups] = prefs.prefsGson().toJson(mutable)
            }
        }
    }

    // ========== URL 可达性持久化（旁路状态，与 Song 解耦；只记录非 REACHABLE 判定） ==========
    // 内存 5 分钟缓存为运行时权威（UrlReachabilityChecker 内部）；本层仅在启动时
    // 初始化（24h 判定窗口内不重测）与 MARK/CLEAR 两处写时机落库。

    /** 当前已知不可达的 URL 直链判定（songId → 判定），供 UI 显示「🔗 URL 已失效」 */
    val songReachability: Flow<Map<String, ReachabilityEntry>> = prefs.dataStoreData().map { data ->
        val json = data[keySongReachability] ?: "{}"
        try {
            prefs.prefsGson().fromJson<Map<String, ReachabilityEntry>>(json, object : TypeToken<Map<String, ReachabilityEntry>>() {}.type)
                ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun getSongReachability(): Map<String, ReachabilityEntry> {
        return try {
            prefs.dataStoreData().first().let { data ->
                val json = data[keySongReachability] ?: "{}"
                prefs.prefsGson().fromJson<Map<String, ReachabilityEntry>>(json, object : TypeToken<Map<String, ReachabilityEntry>>() {}.type)
                    ?: emptyMap()
            }
        } catch (e: Exception) { emptyMap() }
    }

    /** 标记歌曲 URL 不可达（UI 显示「🔗 URL 已失效」；不修改 songs 列表） */
    suspend fun markSongUnreachable(songId: String, result: String = "NOT_REACHABLE") {
        prefs.prefsEdit { data ->
            val json = data[keySongReachability] ?: "{}"
            val map = PrefsJson.parse("song_reachability", json) {
                prefs.prefsGson().fromJson<MutableMap<String, ReachabilityEntry>>(json, object : TypeToken<MutableMap<String, ReachabilityEntry>>() {}.type)
            } ?: mutableMapOf()
            map[songId] = ReachabilityEntry(result = result, checkedAt = System.currentTimeMillis())
            data[keySongReachability] = prefs.prefsGson().toJson(map)
        }
    }

    /** 清除歌曲 URL 不可达标记（补全成功替换后调用，避免旧标记残留） */
    suspend fun clearSongUnreachable(songId: String) {
        prefs.prefsEdit { data ->
            val json = data[keySongReachability] ?: "{}"
            val map = PrefsJson.parse("song_reachability", json) {
                prefs.prefsGson().fromJson<MutableMap<String, ReachabilityEntry>>(json, object : TypeToken<MutableMap<String, ReachabilityEntry>>() {}.type)
            } ?: return@prefsEdit
            if (map.remove(songId) != null) {
                data[keySongReachability] = prefs.prefsGson().toJson(map)
            }
        }
    }
}
