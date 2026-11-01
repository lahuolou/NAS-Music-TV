package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.reflect.TypeToken
import com.lnh.nasmusictv.data.model.EqualizerPreset
import com.lnh.nasmusictv.data.model.VisualQuality
import com.lnh.nasmusictv.data.model.VisualizerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── 键（顶层 internal：AppPreferences 的 appSettings Flow / 数据备份恢复区也读写，同包共享）──

internal val keyEqualizerPreset = intPreferencesKey("equalizer_preset")
internal val keyEqualizerBands = stringPreferencesKey("equalizer_bands")
internal val keyVisualizerTheme = stringPreferencesKey("settings_visualizer_theme")
internal val keyVisualizerQuality = stringPreferencesKey("visualizer_quality")
internal val keyFontAdjustment = intPreferencesKey("settings_font_adjustment")
internal val keyCoverFilterEnabled = booleanPreferencesKey("cover_filter_enabled")
internal val keyCoverFilterBlurRadius = doublePreferencesKey("cover_filter_blur_radius")
internal val keyCoverFilterDarkOverlay = doublePreferencesKey("cover_filter_dark_overlay")

/**
 * R-4 可视化/均衡器/封面滤镜域子 Prefs（键不迁移）。
 *
 * 实现已全量下移（不再委托 AppPreferences）；AppPreferences 的 appSettings Flow
 * 与数据备份恢复区通过顶层 internal key 做横切读写。
 */
class VisualizerPrefs internal constructor(private val prefs: AppPreferences) {

    // --- 均衡器 ---
    val equalizerPreset: Flow<EqualizerPreset> = prefs.dataStoreData().map { data ->
        val ordinal = data[keyEqualizerPreset] ?: 0
        EqualizerPreset.entries.getOrElse(ordinal) { EqualizerPreset.NORMAL }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        prefs.prefsEdit { it[keyEqualizerPreset] = preset.ordinal }
    }

    val equalizerBands: Flow<List<Float>> = prefs.dataStoreData().map { data ->
        val json = data[keyEqualizerBands] ?: "[]"
        try {
            prefs.prefsGson().fromJson<List<Float>>(json, object : TypeToken<List<Float>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setEqualizerBands(bands: List<Float>) {
        prefs.prefsEdit { it[keyEqualizerBands] = prefs.prefsGson().toJson(bands) }
    }

    suspend fun setEqualizerBand(index: Int, value: Float) {
        prefs.prefsEdit { data ->
            val json = data[keyEqualizerBands] ?: "[]"
            val bands = PrefsJson.parse("equalizer_bands", json) {
                prefs.prefsGson().fromJson<List<Float>>(json, object : TypeToken<List<Float>>() {}.type)
                    .toMutableList()
            } ?: return@prefsEdit
            while (bands.size <= index) bands.add(0f)
            bands[index] = value
            data[keyEqualizerBands] = prefs.prefsGson().toJson(bands)
        }
    }

    // --- 视觉主题 / 质量 / 字号 ---
    suspend fun setVisualizerTheme(theme: VisualizerTheme) =
        prefs.prefsEdit { it[keyVisualizerTheme] = theme.name }

    suspend fun setVisualizerQuality(quality: VisualQuality) =
        prefs.prefsEdit { it[keyVisualizerQuality] = quality.name }

    suspend fun setFontAdjustment(adjustment: Int) =
        prefs.prefsEdit { it[keyFontAdjustment] = adjustment }

    // --- 封面滤镜设置 ---
    val coverFilterEnabled: Flow<Boolean> = prefs.dataStoreData().map { data ->
        data[keyCoverFilterEnabled] ?: false
    }

    suspend fun setCoverFilterEnabled(enabled: Boolean) {
        prefs.prefsEdit { it[keyCoverFilterEnabled] = enabled }
    }

    val coverFilterBlurRadius: Flow<Float> = prefs.dataStoreData().map { data ->
        (data[keyCoverFilterBlurRadius] ?: 8.0).toFloat()
    }

    suspend fun setCoverFilterBlurRadius(radius: Float) {
        prefs.prefsEdit { it[keyCoverFilterBlurRadius] = radius.toDouble() }
    }

    val coverFilterDarkOverlay: Flow<Float> = prefs.dataStoreData().map { data ->
        (data[keyCoverFilterDarkOverlay] ?: 0.3).toFloat()
    }

    suspend fun setCoverFilterDarkOverlay(overlay: Float) {
        prefs.prefsEdit { it[keyCoverFilterDarkOverlay] = overlay.toDouble() }
    }
}
