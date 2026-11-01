package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.util.AppLog
import com.lnh.nasmusictv.util.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ── 键（顶层 internal：旧明文键仅作一次性迁移读取）──

internal val keyWeatherApiKey = stringPreferencesKey("weather_openweathermap_api_key")
/**
 * F-7：加密存储键（OpenWeatherMap key 属付费资源凭证，泄露可被刷量；
 * 与服务器密码/百度 token 一致走 CryptoUtils AES-GCM。旧明文键保留做一次性迁移读取）。
 */
internal val keyWeatherApiKeyEnc = stringPreferencesKey("weather_openweathermap_api_key_enc")

/**
 * R-4 天气电台域子 Prefs（实现全量下移，键不迁移、行为不变）。
 *
 * F-7：Key 加密落盘（AES-GCM），旧明文键一次性迁移后清除；
 * R-7：同步读改 @Volatile 内存镜像（WeatherRadioViewModel.fetchWeather 在 Main 协程内，
 * 读镜像不阻塞——原先 runBlocking 会冻住主线程触发 ANR）。
 */
class WeatherPrefs internal constructor(private val prefs: AppPreferences) {

    /** F-7：天气 API Key 镜像（加密键解密后的值；WeatherRadioViewModel.fetchWeather 同步读） */
    @Volatile private var cachedWeatherApiKey: String = ""

    /** 镜像已启动收集标志（startMirrors 只执行一次） */
    @Volatile private var mirrorStarted = false
    private val mirrorLock = Any()

    /**
     * 启动天气域镜像收集与明文→加密一次性迁移（由 AppPreferences.startProviderMirrors 调用，仅一次）。
     * 设置页改动 → DataStore 写入 → Flow 发射 → @Volatile 镜像更新 → getWeatherApiKeySync 立即生效。
     */
    fun startMirrors(scope: CoroutineScope) {
        synchronized(mirrorLock) {
            if (mirrorStarted) return
            mirrorStarted = true
        }
        scope.launch { migrateWeatherApiKeyIfNeeded() }
        scope.launch {
            prefs.dataStoreData().map { prefs ->
                val enc = prefs[keyWeatherApiKeyEnc]
                if (!enc.isNullOrBlank()) {
                    try { CryptoUtils.decrypt(enc) } catch (e: Exception) { "" }
                } else ""
            }.distinctUntilChanged().collect { cachedWeatherApiKey = it }
        }
    }

    /** OpenWeatherMap API Key（F-7：读加密键 AES-GCM 解密；解密失败回退空串） */
    val weatherApiKey: Flow<String> = prefs.dataStoreData().map { prefs ->
        val enc = prefs[keyWeatherApiKeyEnc]
        if (!enc.isNullOrBlank()) {
            try { CryptoUtils.decrypt(enc) } catch (e: Exception) { "" }
        } else ""
    }

    /** R-7 第二类修复：同步读 @Volatile 内存镜像（不阻塞主线程） */
    fun getWeatherApiKeySync(): String = cachedWeatherApiKey

    /** F-7：写入加密键（同时清除旧明文键） */
    suspend fun setWeatherApiKey(key: String) {
        val trimmed = key.trim()
        prefs.prefsEdit { prefs ->
            prefs[keyWeatherApiKeyEnc] = CryptoUtils.encrypt(trimmed)
            prefs.remove(keyWeatherApiKey)  // 迁移后清除明文
        }
    }

    /** F-7：一次性迁移——旧明文键有值且加密键为空时，加密搬移。幂等。 */
    private suspend fun migrateWeatherApiKeyIfNeeded() {
        val prefs = prefs.dataStoreData().first()
        val legacy = prefs[keyWeatherApiKey]
        if (!legacy.isNullOrBlank() && prefs[keyWeatherApiKeyEnc].isNullOrBlank()) {
            setWeatherApiKey(legacy)
            AppLog.i("AppPreferences", "weather API key migrated to encrypted storage")
        }
    }
}
