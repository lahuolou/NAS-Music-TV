package com.lnh.nasmusictv.net

import com.lnh.nasmusictv.util.AppLog
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

/**
 * 内嵌 HTTP server 一次性鉴权（P1-1 修复，2026-09-22 审查）。
 *
 * 背景：5 个内嵌 server（18080~18084）此前绑定 0.0.0.0 且无任何鉴权，
 * 同网段任意设备可遥控播放、上传备份并 /api/restore 一键覆盖用户全部数据、
 * 向输入框注入文本、覆盖模型文件 / 磁盘填充。
 *
 * 机制（对既有「扫码 → 手机浏览器打开」流程零改动）：
 * 1. server 启动时生成一次性随机 token（[newToken]）；
 * 2. 二维码 URL 携带 `?t=<token>`（各 server 的 buildUrl / RemoteControlServer.start）；
 * 3. serve() 头部经 [isAuthorized] 校验：query `t=` 或 Cookie `auth=` 命中即放行；
 * 4. 页面响应带 `Set-Cookie: auth=<token>`，手机浏览器后续 /api 接口同源请求自动携带；
 * 5. token 每次启动重新生成——旧页面 / 旧二维码在 server 重启后自然失效。
 *
 * 边界说明：token 随二维码出现在电视屏幕上，本机制把访问者限定为「能看到二维码的人」，
 * 防的是同网段其他设备的未授权访问；HTTP 明文传输属项目既定取舍（cleartext 支持）。
 */
object LocalServerAuth {

    private const val TAG = "LocalServerAuth"

    /** 生成 16 位十六进制 token（UUID 去连字符截断；单次启动内唯一即可） */
    fun newToken(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** 进程级稳定 token 缓存：同一 [key] 在进程生命周期内恒定（按服务类型区分，互不串用）。 */
    private val stableTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * 进程级稳定 token：同一 [key] 在整个进程生命周期内始终返回同一个 token。
     *
     * 背景（扫码 403 回归修复）：各内嵌 server 的 token 曾是「每实例 newToken」。TV 端
     * 屏幕重组 / 导航会新建 server 实例（新 token、新二维码），而旧实例仍占用端口
     * （`EADDRINUSE`——旧实例未 stop），导致**屏幕上二维码的 token ≠ 真正在监听的旧实例
     * token** → 手机扫码 403 Forbidden。改为按 [key] 稳定后，同类服务无论哪个实例在监听，
     * 二维码 token 永远一致，扫码不再 403。
     *
     * 安全取舍：token 由「每次服务启动轮换」放宽为「进程生命周期内恒定」——仍把访问者限定为
     * 「能看到电视当前二维码的人」，仅不再随服务重启自动失效（重启电视 / 杀掉进程后才更换）。
     */
    fun stableToken(key: String): String = stableTokens.getOrPut(key) { newToken() }

    /**
     * 校验请求是否携带有效凭证：query 参数 `t` 或 Cookie `auth=` 命中 [expected]。
     * [expected] 为空时一律拒绝（未配置鉴权的 server 安全默认 = 全拒）。
     */
    fun isAuthorized(session: NanoHTTPD.IHTTPSession, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return false
        if (session.parameters["t"]?.firstOrNull() == expected) return true
        val cookie = session.headers["cookie"] ?: return false
        for (part in cookie.split(';')) {
            val trimmed = part.trim()
            if (trimmed.startsWith("auth=") && trimmed.removePrefix("auth=") == expected) return true
        }
        return false
    }

    /** 页面响应附带的 Cookie 头（手机浏览器存 24h，后续 /api 接口同源请求自动携带） */
    fun cookieHeader(token: String): String = "auth=$token; Path=/; Max-Age=86400; SameSite=Lax"

    /**
     * 统一的 403 响应（不回显任何内部信息）。
     *
     * 必须断连：带请求体的请求（如上传）走到这里时 body 还挂在 socket 上，
     * 若继续 keep-alive，残留字节会被解析成下一个请求 → 浏览器报 `Failed to fetch`
     * 而非"未授权"。见 [HttpRequestBody] 头注。
     *
     * [session] 仅用于日志（[AppLog.w] 落盘，release 现场唯一可观测通道）。
     */
    fun forbidden(session: NanoHTTPD.IHTTPSession? = null): NanoHTTPD.Response {
        if (session != null) {
            AppLog.w(TAG, "403 拒绝: ${session.method} ${session.uri} from ${session.remoteIpAddress}")
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.FORBIDDEN,
            "text/plain; charset=UTF-8",
            "Forbidden"
        ).noKeepAlive()
    }
}