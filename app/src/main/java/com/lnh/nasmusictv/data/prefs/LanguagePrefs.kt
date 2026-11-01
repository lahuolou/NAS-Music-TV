package com.lnh.nasmusictv.data.prefs

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** 语言键（顶层 internal：AppPreferences.appSettings 聚合 Flow 也读，同包共享） */
internal val keyLanguage = stringPreferencesKey("settings_language")

/**
 * R-4 语言域子 Prefs（R-7 双写镜像；实现全量下移，键不迁移）。
 *
 * ## 双写镜像（R-7 第一类修复）
 *
 * `attachBaseContext` 无法挂起，冷启动语言读取只能走 SharedPreferences 镜像
 * （"language_mirror"）；DataStore 是事实源，[setLanguage] 双写保证一致，
 * 镜像未命中时回退默认值并打点。
 */
class LanguagePrefs internal constructor(
    private val prefs: AppPreferences,
    context: Context,
) {

    /** 沿用 AppPreferences 的日志 TAG（域日志归属不变，运维 grep 口径不漂移） */
    private companion object {
        const val TAG = "AppPreferences"
    }

    private val mirrorPrefs = context
        .getSharedPreferences("language_mirror", Context.MODE_PRIVATE)

    val language: Flow<String> = prefs.prefsData.map { it[keyLanguage] ?: "system" }

    /**
     * 同步获取当前语言设置。
     *
     * R-7 第一类修复：attachBaseContext 无法挂起，改读 SharedPreferences 镜像
     * （[setLanguage] 双写保证一致；镜像未命中时回退默认值并打点）。
     */
    fun getLanguageSync(): String {
        val mirrored = mirrorPrefs.getString("language", null)
        return mirrored ?: "system"
    }

    /** 语言双写：DataStore 事实源 + SharedPreferences 镜像（只服务冷启动） */
    suspend fun setLanguage(lang: String) {
        prefs.prefsEdit { it[keyLanguage] = lang }
        mirrorPrefs.edit { putString("language", lang) }
    }

    /**
     * R-7（第一类）：老版本升级迁移——DataStore 已有语言值但镜像为空时补写镜像。
     * 由 NasMusicApp.onCreate 在首次读取镜像前调用（一次性，幂等）。
     */
    fun migrateLanguageMirrorIfNeeded() {
        if (mirrorPrefs.contains("language")) return
        try {
            val stored = runBlocking(Dispatchers.IO) {
                prefs.prefsData.first()[keyLanguage] ?: "system"
            }
            mirrorPrefs.edit { putString("language", stored) }
        } catch (e: Exception) {
            AppLog.w(TAG, "migrateLanguageMirrorIfNeeded failed", e)
        }
    }
}
