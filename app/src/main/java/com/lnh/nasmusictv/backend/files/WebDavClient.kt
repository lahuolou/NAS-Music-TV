package com.lnh.nasmusictv.backend.files

import android.net.Uri
import android.util.Xml
import com.lnh.nasmusictv.backend.BackendRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser

/** WebDAV transport using the app's shared OkHttp pool. Credentials are sent only in headers. */
internal class WebDavClient(
    rawBaseUrl: String,
    username: String,
    password: String
) {
    private val baseUrl: HttpUrl
    private val authHeader: String?
    private val client: OkHttpClient

    init {
        val normalized = rawBaseUrl.trim().let { if (it.contains("://")) it else "http://$it" }
        baseUrl = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("WebDAV 地址格式不正确")
        require(baseUrl.scheme == "http" || baseUrl.scheme == "https") { "WebDAV 仅支持 HTTP 或 HTTPS 地址" }
        require(baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) { "请勿把账号密码写在 URL 中，请使用账号和密码输入框" }
        require(baseUrl.encodedQuery == null && baseUrl.encodedFragment == null) { "WebDAV 地址不能包含查询参数或片段" }
        authHeader = if (username.isNotBlank()) {
            val raw = "$username:$password".toByteArray(Charsets.UTF_8)
            "Basic " + android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
        } else null
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(BackendRegistry.sharedConnectionPool)
            .dispatcher(BackendRegistry.sharedDispatcher)
            .build()
    }

    fun authorizationHeader(): String? = authHeader

    fun urlFor(path: String): String {
        val builder = baseUrl.newBuilder()
        val clean = path.trim('/')
        if (clean.isNotEmpty()) builder.addPathSegments(clean)
        return builder.build().toString()
    }

    fun list(path: String): List<RemoteMusicEntry> {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>""".trimIndent()
        val request = Request.Builder()
            .url(directoryUrl(path))
            .method("PROPFIND", xml.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 207 && !response.isSuccessful) {
                throw java.io.IOException("WebDAV 返回 HTTP ${response.code}")
            }
            val body = response.body ?: return emptyList()
            val maxResponseBytes = 4 * 1024 * 1024
            if (body.contentLength() > maxResponseBytes) throw java.io.IOException("WebDAV 目录响应过大")
            // Some DAV servers use chunked encoding and report contentLength=-1.
            // Enforce the same cap while reading, not only from the optional header.
            val xmlBytes = body.byteStream().use { readAtMost(it, maxResponseBytes + 1) }
            if (xmlBytes.size > maxResponseBytes) throw java.io.IOException("WebDAV 目录响应过大")
            return parsePropfind(xmlBytes.inputStream(), path)
        }
    }

    fun readRange(path: String, start: Long, endInclusive: Long): ByteArray? {
        if (start < 0L || endInclusive < start) return null
        val request = Request.Builder()
            .url(urlFor(path))
            .header("Range", "bytes=$start-$endInclusive")
            .apply { authHeader?.let { header("Authorization", it) } }
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) return null
            val body = response.body ?: return null
            val expected = (endInclusive - start + 1L).coerceAtMost(16L * 1024 * 1024).toInt()
            body.byteStream().use { input ->
                if (response.code == 200 && start > 0L && !skipFully(input, start)) return null
                return readAtMost(input, expected)
            }
        }
    }

    fun readText(path: String, maxBytes: Int): String? {
        val request = Request.Builder().url(urlFor(path))
            .apply { authHeader?.let { header("Authorization", it) } }
            .get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val stream = response.body?.byteStream() ?: return null
            stream.use { String(readAtMost(it, maxBytes), Charsets.UTF_8) }
        }
    }

    private fun directoryUrl(path: String): String = urlFor(path).let { if (it.endsWith("/")) it else "$it/" }

    private fun parsePropfind(stream: InputStream, requestedPath: String): List<RemoteMusicEntry> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
            setInput(stream, Charsets.UTF_8.name())
        }
        val results = mutableListOf<RemoteMusicEntry>()
        var event = parser.eventType
        var inResponse = false
        var href: String? = null
        var isCollection = false
        var size = 0L
        var modified = 0L
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    when (tag) {
                        "response" -> { inResponse = true; href = null; isCollection = false; size = 0L; modified = 0L }
                        "href" -> if (inResponse) href = parser.nextText()
                        "collection" -> if (inResponse) isCollection = true
                        "getcontentlength" -> if (inResponse) size = parser.nextText().toLongOrNull() ?: 0L
                        "getlastmodified" -> if (inResponse) modified = parseHttpDate(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':').equals("response", true) && inResponse) {
                    href?.let { resolveHref(it, requestedPath, isCollection, size, modified) }?.let(results::add)
                    inResponse = false
                }
            }
            event = parser.next()
        }
        return results.distinctBy { it.path }.sortedWith(compareBy<RemoteMusicEntry> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    private fun resolveHref(
        href: String,
        requestedPath: String,
        isCollection: Boolean,
        size: Long,
        modified: Long
    ): RemoteMusicEntry? {
        val parsedHref = runCatching { URI(href) }.getOrNull() ?: return null
        val hrefUri = if (!parsedHref.isAbsolute && !parsedHref.rawPath.orEmpty().startsWith("/")) {
            runCatching { URI(baseUrl.toString().let { if (it.endsWith("/")) it else "$it/" }).resolve(parsedHref) }.getOrNull() ?: return null
        } else parsedHref
        if (hrefUri.isAbsolute) {
            if (!hrefUri.host.equals(baseUrl.host, true)) return null
            val hrefPort = if (hrefUri.port >= 0) hrefUri.port else if (hrefUri.scheme.equals("https", true)) 443 else 80
            if (hrefPort != baseUrl.port) return null
        }
        val decodedFullPath = Uri.decode(hrefUri.rawPath ?: href)
        val endpointPath = Uri.decode(baseUrl.encodedPath).trimEnd('/')
        val relative = when {
            endpointPath.isBlank() -> decodedFullPath
            decodedFullPath == endpointPath -> "/"
            decodedFullPath.startsWith("$endpointPath/") -> decodedFullPath.removePrefix(endpointPath)
            else -> return null
        }
        if (relative.replace('\\', '/').split('/').any { it == ".." }) return null
        val cleanPath = normalizePath(relative)
        val requested = normalizePath(requestedPath)
        if (cleanPath == requested) return null // PROPFIND returns the requested collection itself
        val childSuffix = if (requested == "/") cleanPath.removePrefix("/") else cleanPath.removePrefix("$requested/")
        if (childSuffix.isBlank() || childSuffix.contains('/')) return null // Depth:1 only
        val name = childSuffix.trimEnd('/').substringAfterLast('/')
        if (name.isBlank() || name == "." || name == "..") return null
        return RemoteMusicEntry(name, cleanPath, isCollection, size, modified)
    }

    private fun parseHttpDate(value: String): Long = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(value)?.time ?: 0L
    }.getOrDefault(0L)

    private fun normalizePath(raw: String): String {
        val stack = mutableListOf<String>()
        raw.replace('\\', '/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += part
            }
        }
        return "/" + stack.joinToString("/")
    }

    private fun readAtMost(input: InputStream, max: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(max, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var remaining = max
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    private fun skipFully(input: InputStream, bytes: Long): Boolean {
        var remaining = bytes
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) { remaining -= skipped; continue }
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) return false
            remaining -= count
        }
        return true
    }

    private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { toHttpUrl() }.getOrNull()
}
