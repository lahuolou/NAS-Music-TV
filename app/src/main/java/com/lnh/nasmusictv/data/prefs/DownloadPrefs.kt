package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── 键（顶层 internal：AppPreferences.appSettings 也读这些 key，同包共享）──

internal val keyDownloadEnabled = booleanPreferencesKey("settings_download_enabled")
internal val keyAutoDownloadOnPlay = booleanPreferencesKey("settings_auto_download_on_play")
internal val keyLocalMusicEnabled = booleanPreferencesKey("local_music_enabled")
internal val keyAutoDownloadLimit = intPreferencesKey("settings_auto_download_limit")
internal val keyDownloadLocation = stringPreferencesKey("settings_download_location")
internal val keyExportTreeUri = stringPreferencesKey("export_tree_uri")
internal val keyExportVolumeId = stringPreferencesKey("export_volume_id")

/**
 * R-4 本地音乐/下载/导出域子 Prefs（键不迁移；实现已下移，不再委托 AppPreferences）。
 */
class DownloadPrefs internal constructor(private val prefs: AppPreferences) {

    suspend fun setDownloadEnabled(v: Boolean) = prefs.prefsEdit { it[keyDownloadEnabled] = v }

    suspend fun setAutoDownloadOnPlay(v: Boolean) = prefs.prefsEdit { it[keyAutoDownloadOnPlay] = v }

    suspend fun setAutoDownloadLimit(v: Int) = prefs.prefsEdit { it[keyAutoDownloadLimit] = v.coerceIn(1, 5000) }

    suspend fun setDownloadLocation(v: String) = prefs.prefsEdit { it[keyDownloadLocation] = v }

    /**
     * 本地音乐总开关（§五）。
     *
     * ⚠️ **写 `true` 不等于授权**：设置页的开关回调走 `MainViewModel.setLocalMusicEnabled`
     * （先判权限、未授权先拉起系统对话框），**不走这个裸 setter**——直接写 true 会留下
     * 「开关开着但扫不到数据」的静默坏状态。本 setter 供 MainViewModel 在权限确认后落盘。
     */
    suspend fun setLocalMusicEnabled(v: Boolean) = prefs.prefsEdit { it[keyLocalMusicEnabled] = v }

    // --- 导出 SAF 授权持久化 ---
    val exportTreeUri: Flow<String?> = prefs.dataStoreData().map { it[keyExportTreeUri] }
    val exportVolumeId: Flow<String?> = prefs.dataStoreData().map { it[keyExportVolumeId] }
    suspend fun setExportTreeUri(uri: String?) = prefs.prefsEdit { if (uri == null) it.remove(keyExportTreeUri) else it[keyExportTreeUri] = uri }
    suspend fun setExportVolumeId(id: String?) = prefs.prefsEdit { if (id == null) it.remove(keyExportVolumeId) else it[keyExportVolumeId] = id }
}
