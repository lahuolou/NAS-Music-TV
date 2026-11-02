package com.lnh.nasmusictv.util

import com.google.gson.JsonParseException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * 连接失败原因诊断：把底层网络 / SSL / HTTP 异常翻译成面向用户的中文短句，
 * 写入 [com.lnh.nasmusictv.backend.BackendAdapter.lastErrorDetail]，
 * 由连接界面 / 弹窗直接展示，避免只显示泛化的「连接失败」。
 *
 * ⚠️ 只描述错误性质与排查方向，**绝不写入**密码 / token / 地址查询串等凭据。
 */
object ConnectionErrorDiagnostics {

    fun diagnose(e: Throwable): String {
        val msg = e.message?.take(120).orEmpty()
        return when (e) {
            is SSLHandshakeException ->
                "SSL 握手失败：证书不受信任（多为自签 / 局域网证书；建议改用 http://，或在服务端配置受信任证书）"
            is SSLException ->
                if (msg.isNotBlank()) "SSL 错误：$msg" else "SSL 加密连接失败"
            is java.net.UnknownHostException ->
                "无法解析主机：地址 / 域名错误，或电视与服务器不在同一网络"
            is java.net.ConnectException ->
                "无法连接：地址或端口不可达（确认端口、防火墙、设备与服务器同一网段）"
            is java.net.SocketTimeoutException ->
                "连接超时：服务器无响应（网络不通或地址 / 端口错误）"
            is IllegalArgumentException ->
                "地址格式错误：请带 http:// 或 https:// 前缀（例：http://192.168.1.100:8096）"
            // 服务端返回的不是预期 JSON：最常见是地址指向了另一个后端
            // （例如用 Jellyfin 去连 Subsonic/Navidrome 的端口），或地址末尾多/少了路径
            is JsonParseException ->
                "响应格式不匹配：服务器返回的不是该后端的 JSON（地址可能指向了别的服务，或后端类型选错了）"
            is java.io.IOException ->
                if (msg.isNotBlank()) "网络错误：$msg" else "网络 I/O 错误"
            else ->
                "连接错误：${e.javaClass.simpleName}${if (msg.isNotBlank()) "（$msg）" else ""}"
        }
    }
}
