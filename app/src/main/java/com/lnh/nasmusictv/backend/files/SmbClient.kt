package com.lnh.nasmusictv.backend.files

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.SmbRandomAccess
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.util.Properties

/** SMB2/3 transport. A separate CIFSContext keeps each server's credentials isolated. */
internal class SmbClient(
    rawHost: String,
    private val share: String,
    domain: String,
    username: String,
    password: String
) : AutoCloseable {
    private val host: String
    val displayHost: String get() = host
    private val port: Int
    private val context: CIFSContext

    init {
        require(share.isNotBlank() && !share.contains('/') && !share.contains('\\')) { "SMB 共享名不能为空，且不能包含斜杠" }
        val normalized = rawHost.trim().let { if (it.startsWith("smb://", true)) it else "smb://$it" }
        val uri = URI(normalized)
        host = uri.host?.removeSurrounding("[", "]")?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("SMB 主机地址不正确")
        port = if (uri.port > 0) uri.port else 445
        require(uri.userInfo == null && (uri.path.isNullOrBlank() || uri.path == "/")) {
            "SMB 地址只填写主机和端口，共享名请填写在共享名称栏"
        }
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB300")
            setProperty("jcifs.smb.client.connTimeout", "15000")
            setProperty("jcifs.smb.client.soTimeout", "30000")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.disableSMB1", "true")
            setProperty("jcifs.smb.client.allowGuestFallback", "false")
        }
        val base = BaseContext(PropertyConfiguration(properties))
        context = if (username.isNotBlank()) {
            base.withCredentials(NtlmPasswordAuthenticator(domain, username, password))
        } else {
            base.withAnonymousCredentials()
        }
    }

    fun list(path: String): List<RemoteMusicEntry> {
        val url = smbUrl(path, directory = true)
        val directory = context.get(url)
        try {
            if (!directory.exists() || !directory.isDirectory) throw java.io.IOException("SMB 目录不存在或无法访问")
            val iterator = directory.children()
            try {
                val entries = mutableListOf<RemoteMusicEntry>()
                while (iterator.hasNext()) {
                    val child = iterator.next()
                    try {
                        val isDir = child.isDirectory
                        val name = child.name.trimEnd('/')
                        if (name.isBlank() || name == "." || name == "..") continue
                        val childPath = normalize(path).let { parent -> if (parent == "/") "/$name" else "$parent/$name" }
                        entries += RemoteMusicEntry(name, childPath, isDir, if (isDir) 0L else child.length(), child.lastModified())
                    } finally {
                        child.close()
                    }
                }
                return entries
            } finally {
                iterator.close()
            }
        } finally {
            directory.close()
        }
    }

    fun readRange(path: String, start: Long, endInclusive: Long): ByteArray? {
        if (start < 0 || endInclusive < start) return null
        val resource = context.get(smbUrl(path, directory = false))
        var random: SmbRandomAccess? = null
        try {
            random = resource.openRandomAccess("r")
            random.seek(start)
            val target = (endInclusive - start + 1L).coerceAtMost(16L * 1024 * 1024).toInt()
            val output = ByteArrayOutputStream(target)
            val buffer = ByteArray(16 * 1024)
            var remaining = target
            while (remaining > 0) {
                val count = random.read(buffer, 0, minOf(buffer.size, remaining))
                if (count <= 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
            return output.toByteArray()
        } finally {
            try { random?.close() } catch (_: Exception) { }
            resource.close()
        }
    }

    fun readText(path: String, maxBytes: Int): String? {
        val resource = context.get(smbUrl(path, directory = false))
        try {
            resource.openInputStream().use { input ->
                val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
                val buffer = ByteArray(8 * 1024)
                var remaining = maxBytes
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                return String(output.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            resource.close()
        }
    }

    fun openRangeStream(path: String, start: Long): InputStream {
        val resource = context.get(smbUrl(path, directory = false))
        val random = try {
            resource.openRandomAccess("r").also { it.seek(start) }
        } catch (error: Exception) {
            resource.close()
            throw error
        }
        return object : InputStream() {
            private var closed = false
            override fun read(): Int {
                val one = ByteArray(1)
                val count = read(one, 0, 1)
                return if (count < 0) -1 else one[0].toInt() and 0xff
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (closed) return -1
                if (length == 0) return 0
                return random.read(buffer, offset, length)
            }
            override fun close() {
                if (closed) return
                closed = true
                try { random.close() } finally { resource.close() }
            }
        }
    }

    fun fileSize(path: String): Long {
        val resource = context.get(smbUrl(path, directory = false))
        return try { resource.length() } finally { resource.close() }
    }

    private fun smbUrl(path: String, directory: Boolean): String {
        val components = normalize(path).split('/').filter { it.isNotBlank() }
        val hostPart = if (host.contains(':')) "[$host]" else host
        val suffix = (listOf(share) + components).joinToString("/") { encodeSegment(it) }
        return "smb://$hostPart:$port/$suffix" + if (directory) "/" else ""
    }

    private fun encodeSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun normalize(raw: String): String {
        val parts = mutableListOf<String>()
        raw.replace('\\', '/').split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += it
            }
        }
        return "/" + parts.joinToString("/")
    }

    override fun close() {
        try { context.close() } catch (_: Exception) { }
    }
}
