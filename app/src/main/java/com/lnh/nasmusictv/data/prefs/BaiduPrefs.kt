package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.util.CryptoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * R-4 百度网盘域子 Prefs（CloudDriveConfig 按类型存取；键不迁移）。
 * T2 第二批（2026-09-14）：全部便捷方法改为 suspend + baiduConfigFlow.first()，
 * 移除 runBlocking 同步透传。getBaiduConfigSync 已删除；getCloudDriveConfigSync /
 * saveCloudDriveConfigSync 生产代码已无调用方，仅保留供单测 CloudDriveConfigTest 同步读写。
 *
 * 百度网盘凭据（AppKey / AppSecret）：设置页可填写、运行时持久化。
 * AppId 明文存储（公开的 client_id，非机密）；AppSecret 走 AES-GCM 加密落盘（F-7 同天气 Key
 * 的安全姿态，避免 AppSecret 以明文留在 DataStore）。读取优先于 BuildConfig 嵌入值，
 * 二者皆空时 BaiduOAuthClient 给出「未配置」可见失败（见 resolveAppKey/SecretKey）。
 */
class BaiduPrefs internal constructor(private val prefs: AppPreferences) {

    // ── 凭据键（顶层 internal）──
    internal val keyBaiduAppId = stringPreferencesKey("baidu_app_id")
    /** AppSecret 加密键（与天气 Key 一致，避免明文落盘） */
    internal val keyBaiduAppSecretEnc = stringPreferencesKey("baidu_app_secret_enc")

    val baiduConfigFlow: Flow<com.lnh.nasmusictv.data.model.CloudDriveConfig> = prefs.baiduConfigFlow

    // ── 百度网盘凭据（设置页填写 / 运行时持久化）──
    /** AppKey（明文） */
    val baiduAppId: Flow<String> = prefs.dataStoreData().map { it[keyBaiduAppId] ?: "" }
    suspend fun getBaiduAppId(): String = baiduAppId.first()
    suspend fun setBaiduAppId(id: String) = prefs.prefsEdit { it[keyBaiduAppId] = id.trim() }

    /** AppSecret（读取时 AES-GCM 解密；解密失败回退空串） */
    val baiduAppSecret: Flow<String> = prefs.dataStoreData().map { prefsData ->
        prefsData[keyBaiduAppSecretEnc]?.let { enc ->
            try { CryptoUtils.decrypt(enc) } catch (e: Exception) { "" }
        } ?: ""
    }
    suspend fun getBaiduAppSecret(): String = baiduAppSecret.first()
    suspend fun setBaiduAppSecret(secret: String) = prefs.prefsEdit {
        val trimmed = secret.trim()
        if (trimmed.isBlank()) it.remove(keyBaiduAppSecretEnc)
        else it[keyBaiduAppSecretEnc] = CryptoUtils.encrypt(trimmed)
    }

    suspend fun getBaiduTokens(): com.lnh.nasmusictv.data.model.BaiduTokens? = prefs.getBaiduTokens()
    suspend fun saveBaiduTokens(tokens: com.lnh.nasmusictv.data.model.BaiduTokens) = prefs.saveBaiduTokens(tokens)
    suspend fun clearBaiduTokens() = prefs.clearBaiduTokens()

    suspend fun getBaiduEnabled(): Boolean = prefs.getBaiduEnabled()
    suspend fun setBaiduEnabled(enabled: Boolean) = prefs.setBaiduEnabled(enabled)
    suspend fun getBaiduMusicRootDir(): String = prefs.getBaiduMusicRootDir()
    suspend fun setBaiduMusicRootDir(dir: String) = prefs.setBaiduMusicRootDir(dir)
    suspend fun getBaiduMvDir(): String? = prefs.getBaiduMvDir()
    suspend fun setBaiduMvDir(dir: String?) = prefs.setBaiduMvDir(dir)
}

