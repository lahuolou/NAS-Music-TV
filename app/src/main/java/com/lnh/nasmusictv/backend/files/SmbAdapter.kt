package com.lnh.nasmusictv.backend.files

import android.content.Context
import com.lnh.nasmusictv.data.model.ServerConfig

/** SMB share adapter with a loopback HTTP bridge for Media3 playback. */
class SmbAdapter(
    context: Context?,
    private val config: ServerConfig
) : NetworkFolderAdapter(context, ServerConfig.TYPE_SMB, "smb_", "SMB") {

    @Volatile private var smbClient: SmbClient? = null
    @Volatile private var streamServer: SmbHttpStreamServer? = null

    override suspend fun initialize(
        baseUrl: String,
        apiToken: String,
        username: String,
        password: String
    ): Boolean = try {
        val client = SmbClient(baseUrl, config.smbShare, config.smbDomain, username, password)
        smbClient = client
        serverName = "SMB · ${client.displayHost} / ${config.smbShare}"
        validateRoot(config.rootPath)
    } catch (e: Exception) {
        lastErrorDetail = e.message?.take(96) ?: "SMB 地址或配置无效"
        false
    }

    override suspend fun testConnection(): Boolean = validateRoot(config.rootPath)

    override suspend fun listRemote(path: String): List<RemoteMusicEntry> =
        smbClient?.list(path) ?: throw IllegalStateException("SMB 尚未连接")

    override suspend fun readRemoteRange(path: String, start: Long, endInclusive: Long): ByteArray? =
        smbClient?.readRange(path, start, endInclusive)

    override suspend fun readRemoteText(path: String, maxBytes: Int): String? =
        smbClient?.readText(path, maxBytes)

    override fun createPlaybackUrl(entry: RemoteMusicEntry, resourceId: String): String {
        val client = smbClient ?: return ""
        val bridge = streamServer ?: synchronized(this) {
            streamServer ?: SmbHttpStreamServer(
                openStream = { path, start -> client.openRangeStream(path, start) },
                getSize = { path -> client.fileSize(path) }
            ).also { streamServer = it }
        }
        return bridge.register(resourceId, entry.path)
    }

    override fun closeRemoteResources() {
        streamServer?.close()
        streamServer = null
        smbClient?.close()
        smbClient = null
    }
}
