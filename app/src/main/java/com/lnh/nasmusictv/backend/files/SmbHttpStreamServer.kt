package com.lnh.nasmusictv.backend.files

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Loopback-only HTTP bridge required because ExoPlayer has no SMB protocol datasource. */
internal class SmbHttpStreamServer(
    private val openStream: (path: String, start: Long) -> InputStream,
    private val getSize: (path: String) -> Long
) : AutoCloseable {
    private val randomToken = ByteArray(24).also(SecureRandom()::nextBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private val resources = ConcurrentHashMap<String, String>()
    @Volatile private var server: BridgeServer? = null

    @Synchronized
    fun register(resourceId: String, path: String): String {
        resources[resourceId] = path
        ensureStarted()
        val port = server?.listeningPort ?: throw IllegalStateException("SMB 本地播放服务未启动")
        return "http://127.0.0.1:$port/$randomToken/$resourceId"
    }

    private fun ensureStarted() {
        if (server != null) return
        val created = BridgeServer(
            openStream = openStream,
            getSize = getSize,
            token = randomToken,
            resources = resources
        )
        created.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = created
    }

    override fun close() {
        synchronized(this) {
            server?.stop()
            server = null
            resources.clear()
        }
    }

    private class BridgeServer(
        private val openStream: (String, Long) -> InputStream,
        private val getSize: (String) -> Long,
        private val token: String,
        private val resources: ConcurrentHashMap<String, String>
    ) : NanoHTTPD("127.0.0.1", 0) {

        override fun serve(session: IHTTPSession): Response {
            if (session.method != Method.GET && session.method != Method.HEAD) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
            }
            val pieces = session.uri.trim('/').split('/')
            if (pieces.size != 2 || pieces[0] != token) return notFound()
            val path = resources[pieces[1]] ?: return notFound()
            val size = try { getSize(path) } catch (_: Exception) { return notFound() }
            if (size < 0L) return notFound()

            val rangeHeader = session.headers["range"]
            val requested = parseRange(rangeHeader, size) ?: if (rangeHeader == null) ByteRange(0L, (size - 1L).coerceAtLeast(0L)) else return rangeNotSatisfiable(size)
            if (size == 0L && rangeHeader != null) return rangeNotSatisfiable(0L)
            val isPartial = rangeHeader != null
            val start = if (size == 0L) 0L else requested.start
            val length = if (size == 0L) 0L else requested.end - requested.start + 1L

            val response = if (session.method == Method.HEAD) {
                newFixedLengthResponse(
                    if (isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                    "audio/*", ByteArrayInputStream(ByteArray(0)), 0L
                )
            } else {
                try {
                    val stream = openStream(path, start)
                    newFixedLengthResponse(
                        if (isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                        mimeFor(path),
                        LimitedInputStream(stream, length),
                        length
                    )
                } catch (_: Exception) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SMB stream unavailable")
                }
            }
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Cache-Control", "no-store")
            response.addHeader("Content-Length", length.toString())
            if (isPartial) response.addHeader("Content-Range", "bytes ${requested.start}-${requested.end}/$size")
            if (session.method == Method.HEAD) response.addHeader("Content-Type", mimeFor(path))
            return response
        }

        private fun parseRange(header: String?, size: Long): ByteRange? {
            if (header == null || size < 0L || !header.startsWith("bytes=")) return null
            val value = header.removePrefix("bytes=").substringBefore(',').trim()
            val pair = value.split('-', limit = 2)
            if (pair.size != 2) return null
            val start: Long
            val end: Long
            if (pair[0].isBlank()) {
                val suffix = pair[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
                start = (size - suffix).coerceAtLeast(0L)
                end = size - 1L
            } else {
                start = pair[0].toLongOrNull() ?: return null
                end = pair[1].takeIf { it.isNotBlank() }?.toLongOrNull() ?: (size - 1L)
            }
            if (start < 0L || start >= size || end < start) return null
            return ByteRange(start, end.coerceAtMost(size - 1L))
        }

        private fun rangeNotSatisfiable(size: Long): Response = newFixedLengthResponse(
            Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "Range Not Satisfiable"
        ).apply { addHeader("Content-Range", "bytes */$size") }

        private fun notFound(): Response = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")

        private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "m4b", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "ape" -> "audio/ape"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "lrc" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }

        private data class ByteRange(val start: Long, val end: Long)

        private class LimitedInputStream(input: InputStream, private var remaining: Long) : java.io.FilterInputStream(input) {
            override fun read(): Int {
                if (remaining <= 0L) return -1
                val value = super.read()
                if (value >= 0) remaining--
                return value
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0L) return -1
                val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
                if (count > 0) remaining -= count
                return count
            }
        }
    }
}
