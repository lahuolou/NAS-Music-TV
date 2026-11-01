package com.lnh.nasmusictv.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnh.nasmusictv.data.model.ServerConfig
import com.lnh.nasmusictv.util.CryptoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── 键（顶层 internal：AppPreferences 的数据备份导出/恢复区也横切读写，同包共享）──

internal val keyBackendType = stringPreferencesKey("server_backend_type")
internal val keyBaseUrl = stringPreferencesKey("server_base_url")
internal val keyApiToken = stringPreferencesKey("server_api_token")
internal val keyUsername = stringPreferencesKey("server_username")
internal val keyPassword = stringPreferencesKey("server_password")
/**
 * 访问码 / 安全码（飞牛 fnOS 外网访问码）。**加密存储**——它与密码同级，
 * 明文落盘等于把访问凭证写进 prefs 文件。
 */
internal val keyAccessCode = stringPreferencesKey("server_access_code")
internal val keyRootPath = stringPreferencesKey("server_root_path")
internal val keySmbShare = stringPreferencesKey("server_smb_share")
internal val keySmbDomain = stringPreferencesKey("server_smb_domain")
internal val keyServerConnected = booleanPreferencesKey("server_connected")
internal val keyServerDisplayName = stringPreferencesKey("server_display_name")

/**
 * R-4 服务器配置域子 Prefs（实现全量下移，键不迁移、行为不变）。
 *
 * 服务器密码 / API Token / 访问码一律 CryptoUtils AES-GCM 加密落盘；
 * 备份导出/恢复走 AppPreferences（敏感字段剔除，恢复后 isConnected=false）。
 */
class ServerPrefs internal constructor(private val prefs: AppPreferences) {

    /** 服务器配置 Flow */
    val serverConfig: Flow<ServerConfig> = prefs.dataStoreData().map { prefs ->
        ServerConfig(
            backendType = prefs[keyBackendType] ?: ServerConfig.TYPE_JELLYFIN,
            baseUrl = prefs[keyBaseUrl] ?: "",
            apiToken = CryptoUtils.decrypt(prefs[keyApiToken] ?: ""),
            username = prefs[keyUsername] ?: "",
            password = CryptoUtils.decrypt(prefs[keyPassword] ?: ""),
            accessCode = CryptoUtils.decrypt(prefs[keyAccessCode] ?: ""),
            rootPath = prefs[keyRootPath] ?: "/",
            smbShare = prefs[keySmbShare] ?: "",
            smbDomain = prefs[keySmbDomain] ?: "",
            isConnected = prefs[keyServerConnected] ?: false,
            displayName = prefs[keyServerDisplayName] ?: ""
        )
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        prefs.prefsEdit { prefs ->
            prefs[keyBackendType] = config.backendType
            prefs[keyBaseUrl] = config.baseUrl
            prefs[keyApiToken] = CryptoUtils.encrypt(config.apiToken)
            prefs[keyUsername] = config.username
            prefs[keyPassword] = CryptoUtils.encrypt(config.password)
            // 访问码是凭据：绝不明文落盘（与 password / apiToken 同等对待）
            prefs[keyAccessCode] = CryptoUtils.encrypt(config.accessCode)
            prefs[keyRootPath] = config.rootPath
            prefs[keySmbShare] = config.smbShare
            prefs[keySmbDomain] = config.smbDomain
            prefs[keyServerConnected] = config.isConnected
            prefs[keyServerDisplayName] = config.displayName
        }
    }

    suspend fun setServerConnected(connected: Boolean, displayName: String = "") {
        prefs.prefsEdit { prefs ->
            prefs[keyServerConnected] = connected
            if (displayName.isNotBlank()) {
                prefs[keyServerDisplayName] = displayName
            }
        }
    }

    suspend fun clearServerConfig() {
        prefs.prefsEdit { prefs ->
            prefs.remove(keyBackendType)
            prefs.remove(keyBaseUrl)
            prefs.remove(keyApiToken)
            prefs.remove(keyUsername)
            prefs.remove(keyPassword)
            prefs.remove(keyAccessCode)
            prefs.remove(keyRootPath)
            prefs.remove(keySmbShare)
            prefs.remove(keySmbDomain)
            prefs[keyServerConnected] = false
            prefs.remove(keyServerDisplayName)
        }
    }
}
