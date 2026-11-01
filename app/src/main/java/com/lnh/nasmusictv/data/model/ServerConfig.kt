package com.lnh.nasmusictv.data.model

import java.util.UUID

/**
 * 服务器配置
 */
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val backendType: String,
    val baseUrl: String,
    val apiToken: String = "",
    val username: String = "",
    val password: String = "",
    /**
     * 访问码 / 安全码（飞牛 fnOS 的「外网访问码」；其余后端忽略）。
     *
     * ⚠️ **属凭据**：与 `password` / `apiToken` 同等对待，落盘走 `CryptoUtils` AES-GCM
     * 加密，且**不进备份 JSON**（见 `AppPreferences.exportBackupData`）。
     */
    val accessCode: String = "",
    /** WebDAV / SMB 音乐扫描根目录，相对远端服务根路径。 */
    val rootPath: String = "/",
    /** SMB 共享名（baseUrl 仅保存 smb://host:port）。 */
    val smbShare: String = "",
    /** SMB NTLM 工作组 / 域名，非凭据。 */
    val smbDomain: String = "",
    val isConnected: Boolean = false,
    val displayName: String = ""
) {
    companion object {
        const val TYPE_JELLYFIN = "jellyfin"
        const val TYPE_NAVIDROME = "navidrome"
        const val TYPE_SUBSONIC = "subsonic"
        const val TYPE_DAOLIYU = "daoliyu"   // 道理鱼音乐
        const val TYPE_FEINIU = "feiniu"     // 飞牛音乐
        const val TYPE_WEBDAV = "webdav"     // WebDAV 文件音乐源
        const val TYPE_SMB = "smb"           // SMB 文件音乐源

        val Empty = ServerConfig(
            backendType = TYPE_JELLYFIN,
            baseUrl = "",
            apiToken = "",
            username = "",
            password = ""
        )
    }

    val isValid: Boolean
        get() = baseUrl.isNotBlank() && backendType.isNotBlank()
}
