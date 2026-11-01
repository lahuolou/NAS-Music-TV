package com.lnh.nasmusictv.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 网络工具
 *
 * 获取设备在局域网中的 IP 地址，用于生成扫码输入的 URL。
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    /**
     * 获取设备在局域网中的 IPv4 地址（点分十进制，如 "192.168.1.100"）
     *
     * F-6 修复：TV 常见双网卡（以太网 + Wi-Fi）场景下，原 firstOrNull() 不分接口
     * 优先级，可能返回错误网段的 IP（手机扫码后连不上）。改进：
     * 1. 有序候选——「有默认路由（实际在用）的接口」优先，其次 eth（有线），
     *    再 wlan（无线），最后其余接口；
     * 2. 同一接口内取第一个非回环 IPv4；
     * 3. 全部失败时回退原行为（任意非回环 IPv4）。
     * 不需要 ACCESS_WIFI_STATE 权限，兼容所有 API 级别（接口名前缀匹配，不用 ICU）。
     *
     * @return IP 地址字符串；无可用网络时返回 null
     */
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.asSequence()?.toList().orEmpty()

            /** 多 IP 择优评分：典型局域网地址得分最高，避开 NAT/链路本地 */
            fun ipScore(ip: String): Int = when {
                ip.startsWith("169.254.") -> 0   // 链路本地，最低
                ip.startsWith("10.0.2.") -> 1    // 模拟器 NAT，避开
                ip.startsWith("10.") -> 4        // 其他 10.x 私有段
                ip.startsWith("192.168.") -> 5  // 典型局域网，优先
                ip.startsWith("172.") -> 4       // 172.16-31 私有段
                else -> 3                        // 公网/其他
            }

            // 跨所有「在用接口」收集全部非回环 IPv4，连同接口优先级一起记录，
            // 再做全局择优——这样无论 192.168.x 桥接地址被归到哪个接口名下都能被选中，
            // 规避 Android 对 TAP 桥接地址在 NetworkInterface 上的反射差异。
            val candidates: MutableList<Pair<String, Int>> = interfaces.flatMap { ni ->
                val name = ni.name.lowercase()
                val prio = (if (ni.isUp) 8 else 0) + when {
                    name.startsWith("eth") -> 2    // 有线（TV 首选）
                    name.startsWith("wlan") || name.startsWith("wifi") -> 1  // 无线
                    else -> 0
                }
                ni.inetAddresses?.asSequence()
                    ?.filter { !it.isLoopbackAddress && it is Inet4Address }
                    ?.mapNotNull { it.hostAddress }
                    ?.toList().orEmpty()
                    .map { it to prio }
            }.toMutableList()
            // 兜底：部分环境下（如模拟器 TAP 桥接）真实 LAN 地址不会出现在
            // NetworkInterface 中，但内核路由表 /proc/net/fib_trie 里有。补进来再择优。
            val fibIps = readLocalIpsFromFibTrie().filter {
                !it.startsWith("127.") && !it.startsWith("169.254.")
            }
            for (ip in fibIps) if (candidates.none { it.first == ip }) candidates.add(ip to 0)
            return candidates.maxWithOrNull(compareBy({ ipScore(it.first) }, { it.second }))?.first
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to get local IP", e)
            null
        }
    }

    /**
     * 从 /proc/net/fib_trie 读取内核已知的所有本地 IPv4（含 NetworkInterface 看不到的
     * 桥接/外部地址）。仅取「host LOCAL」条目（即本机地址）。文件 world-readable，无需权限。
     * 失败时返回空列表，由调用方退化为仅 NetworkInterface 的结果。
     */
    private fun readLocalIpsFromFibTrie(): List<String> {
        val file = java.io.File("/proc/net/fib_trie")
        if (!file.canRead()) return emptyList()
        val result = mutableListOf<String>()
        try {
            val lines = file.readLines()
            val ipRe = Regex("""^\s*\|--\s+(\d{1,3}(?:\.\d{1,3}){3})\s*$""")
            for (i in lines.indices) {
                val m = ipRe.find(lines[i]) ?: continue
                val ip = m.groupValues[1]
                val next = lines.getOrNull(i + 1) ?: ""
                if (next.contains("host LOCAL")) result.add(ip)
            }
        } catch (_: Exception) {
            // 忽略：解析失败则退化为仅 NetworkInterface 的结果
        }
        return result
    }
}
