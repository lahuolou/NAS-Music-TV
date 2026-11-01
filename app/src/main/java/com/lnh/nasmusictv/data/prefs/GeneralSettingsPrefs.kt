package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.data.model.PlayMode

// ── 键（顶层 internal：AppPreferences 的 appSettings Flow / 数据备份恢复区也读写，同包共享）──

internal val keyDarkTheme = booleanPreferencesKey("settings_dark_theme")
internal val keyAnimations = booleanPreferencesKey("settings_animations")
internal val keyAutoPlayNext = booleanPreferencesKey("settings_auto_play_next")
internal val keyPlayMode = intPreferencesKey("settings_play_mode")
internal val keyCacheLyrics = booleanPreferencesKey("settings_cache_lyrics")
internal val keyCacheCover = booleanPreferencesKey("settings_cache_cover")
internal val keyDefaultNetworkSource = stringPreferencesKey("settings_default_network_source")

/**
 * R-4 通用设置域子 Prefs（键不迁移）。
 *
 * 实现已全量下移（不再委托 AppPreferences）；AppPreferences 的 appSettings 聚合 Flow
 * 与数据备份恢复区通过顶层 internal key 做横切读写。
 */
class GeneralSettingsPrefs internal constructor(private val prefs: AppPreferences) {

    suspend fun setDarkTheme(enabled: Boolean) =
        prefs.prefsEdit { it[keyDarkTheme] = enabled }

    suspend fun setAnimationsEnabled(enabled: Boolean) =
        prefs.prefsEdit { it[keyAnimations] = enabled }

    suspend fun setAutoPlayNext(enabled: Boolean) =
        prefs.prefsEdit { it[keyAutoPlayNext] = enabled }

    suspend fun setDefaultPlayMode(mode: PlayMode) =
        prefs.prefsEdit { it[keyPlayMode] = mode.ordinal }

    suspend fun setCacheLyrics(enabled: Boolean) =
        prefs.prefsEdit { it[keyCacheLyrics] = enabled }

    suspend fun setCacheCover(enabled: Boolean) =
        prefs.prefsEdit { it[keyCacheCover] = enabled }

    suspend fun setDefaultNetworkSource(source: com.lnh.nasmusictv.data.model.NetworkSource) =
        prefs.prefsEdit { it[keyDefaultNetworkSource] = source.key }
}
