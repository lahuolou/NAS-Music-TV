package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.data.model.PhotoScaleMode
import com.lnh.nasmusictv.visualizer.photo.PhotoTransitionId

// ── 键（顶层 internal：AppPreferences 的 appSettings Flow / 数据备份恢复区也读写，同包共享）──

internal val keyPhotoWallGalleryEnabled = booleanPreferencesKey("photo_wall_gallery_enabled")
internal val keyPhotoWallExternalEnabled = booleanPreferencesKey("photo_wall_external_enabled")
internal val keyPhotoWallJellyfinEnabled = booleanPreferencesKey("photo_wall_jellyfin_enabled")
internal val keyPhotoWallSourceBalance = booleanPreferencesKey("photo_wall_source_balance")
internal val keyPhotoWallDirUri = stringPreferencesKey("photo_wall_dir_uri")
internal val keyPhotoWallCommonDirsOnly = booleanPreferencesKey("photo_wall_common_dirs_only")
internal val keyPhotoWallFacesOnly = booleanPreferencesKey("photo_wall_faces_only")
internal val keyPhotoWallFaceScanDone = booleanPreferencesKey("photo_wall_face_scan_done")
internal val keyPhotoWallRandomTransition = booleanPreferencesKey("photo_wall_random_transition")
internal val keyPhotoWallFixedTransition = stringPreferencesKey("photo_wall_fixed_transition")
internal val keyPhotoWallTransitionMs = intPreferencesKey("photo_wall_transition_ms")
internal val keyPhotoWallHoldMs = intPreferencesKey("photo_wall_hold_ms")
internal val keyPhotoWallScaleMode = stringPreferencesKey("photo_wall_scale_mode")
internal val keyPhotoWallKenBurns = booleanPreferencesKey("photo_wall_ken_burns")
internal val keyPhotoWallAudioReactive = booleanPreferencesKey("photo_wall_audio_reactive")
internal val keyPhotoWallPulseZoom = booleanPreferencesKey("photo_wall_pulse_zoom")
internal val keyPhotoWallBreathe = booleanPreferencesKey("photo_wall_breathe")

/** 转场时长合法范围（§7.3：300–2000 ms），写入侧钳制 */
internal val PHOTO_WALL_TRANSITION_MS_RANGE = 300..2000

/** 停留时长合法范围（§7.3：3000–30000 ms），写入侧钳制 */
internal val PHOTO_WALL_HOLD_MS_RANGE = 3_000..30_000

/**
 * R-4 照片墙域子 Prefs（§7.3，17 个字段；实现全量下移，键不迁移、行为不变）。
 *
 * ## 为什么只提供 setter，不提供 Flow
 *
 * 设置页**读**值走的是 `AppSettings`（`AppPreferences.appSettings` 已经把它整份发出来，
 * `SettingsScreen` 本来就收这个参数）；只有**写**需要一个入口。
 * ⇒ 多一份 `Flow<Boolean>` 只会多一条要维护的订阅，没有任何收益。
 *
 * ## 与「三个来源开关」的关系
 *
 * `galleryEnabled` / `externalEnabled` / `jellyfinEnabled` 三者是**平级**的，
 * 「照片墙该不该出现在效果列表里」由它们的**或**派生（见 `PhotoWallAvailability`，§7.4）——
 * 本类不承担该判定，只负责存取。
 */
class PhotoWallPrefs internal constructor(private val prefs: AppPreferences) {

    // ── 照片来源 ──────────────────────────────────────────────────────────
    suspend fun setGalleryEnabled(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallGalleryEnabled] = v }

    suspend fun setExternalEnabled(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallExternalEnabled] = v }

    suspend fun setJellyfinEnabled(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallJellyfinEnabled] = v }

    suspend fun setSourceBalance(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallSourceBalance] = v }

    suspend fun setDirUri(v: String) =
        prefs.prefsEdit { it[keyPhotoWallDirUri] = v }

    suspend fun setCommonDirsOnly(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallCommonDirsOnly] = v }

    // ── 显示筛选（人脸） ──────────────────────────────────────────────────
    suspend fun setFacesOnly(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallFacesOnly] = v }

    suspend fun setFaceScanDone(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallFaceScanDone] = v }

    // ── 转场效果 ──────────────────────────────────────────────────────────
    suspend fun setRandomTransition(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallRandomTransition] = v }

    suspend fun setFixedTransition(v: PhotoTransitionId) =
        prefs.prefsEdit { it[keyPhotoWallFixedTransition] = v.name }

    /** 数值字段写入侧钳制：设置页 `+/-` 与备份导入都不会写进越界值 */
    suspend fun setTransitionMs(v: Int) =
        prefs.prefsEdit { it[keyPhotoWallTransitionMs] = v.coerceIn(PHOTO_WALL_TRANSITION_MS_RANGE) }

    suspend fun setHoldMs(v: Int) =
        prefs.prefsEdit { it[keyPhotoWallHoldMs] = v.coerceIn(PHOTO_WALL_HOLD_MS_RANGE) }

    // ── 画面与运动 ────────────────────────────────────────────────────────
    suspend fun setScaleMode(v: PhotoScaleMode) =
        prefs.prefsEdit { it[keyPhotoWallScaleMode] = v.name }

    suspend fun setKenBurns(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallKenBurns] = v }

    suspend fun setAudioReactive(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallAudioReactive] = v }

    suspend fun setPulseZoom(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallPulseZoom] = v }

    suspend fun setBreathe(v: Boolean) =
        prefs.prefsEdit { it[keyPhotoWallBreathe] = v }
}
