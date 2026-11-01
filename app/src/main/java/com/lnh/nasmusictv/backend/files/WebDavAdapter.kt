package com.lnh.nasmusictv.backend.files

import android.content.Context
import com.lnh.nasmusictv.data.model.ServerConfig

/** WebDAV file-library adapter. The shared playback datasource injects Basic auth by host. */
class WebDavAdapter(
    context: Context?,
    private val config: ServerConfig
) : NetworkFolderAdapter(context, ServerConfig.TYPE_WEBDAV, "webdav_", "WebDAV") {

    @Volatile private var davClient: WebDavClient? = null

    override val streamHeaders: Map<String, String>
        get() = davClient?.authorizationHeader()?.let { mapOf("Authorization" to it) } ?: emptyMap()

    override suspend fun initialize(
        baseUrl: String,
        apiToken: String,
        username: String,
        password: String
    ): Boolean {
        return try {
            val client = WebDavClient(baseUrl, username, password)
            davClient = client
            serverName = "WebDAV · ${client.urlFor("").substringAfter("://").substringBefore('/')}"
            validateRoot(config.rootPath)
        } catch (e: Exception) {
            lastErrorDetail = e.message?.take(96) ?: "WebDAV 地址或配置无效"
            false
        }
    }

    override suspend fun testConnection(): Boolean = validateRoot(config.rootPath)

    override suspend fun listRemote(path: String): List<RemoteMusicEntry> =
        davClient?.list(path) ?: throw IllegalStateException("WebDAV 尚未连接")

    override suspend fun readRemoteRange(path: String, start: Long, endInclusive: Long): ByteArray? =
        davClient?.readRange(path, start, endInclusive)

    override suspend fun readRemoteText(path: String, maxBytes: Int): String? =
        davClient?.readText(path, maxBytes)

    override fun createPlaybackUrl(entry: RemoteMusicEntry, resourceId: String): String =
        davClient?.urlFor(entry.path).orEmpty()

    override fun closeRemoteResources() {
        davClient = null // OkHttp is shared with the rest of the application; never shut it down here.
    }
}
