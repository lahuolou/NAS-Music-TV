package com.lnh.nasmusictv.data.prefs

import com.lnh.nasmusictv.util.AppLog

/**
 * 偏好持久化 JSON 辅助（R-4 实现下移共享）。
 *
 * 从 `AppPreferences.safeParseJson` 下沉：各子 Prefs 解析 DataStore JSON 时共用，
 * 避免每个域各自 try/catch + 日志。
 */
internal object PrefsJson {

    private const val TAG = "PrefsJson"

    /**
     * 从持久化 JSON 安全解析（跳过损坏数据）。
     *
     * @param keyName 偏好键名（仅用于日志定位）
     * @param json 原始 JSON 字符串
     * @param parse 解析 lambda
     * @return 解析结果；解析失败返回 null
     */
    inline fun <T> parse(keyName: String, json: String, parse: () -> T): T? {
        return try {
            parse()
        } catch (e: Exception) {
            AppLog.w(TAG, "JSON 解析失败，跳过回写以保留原数据 [$keyName]: ${e.message?.take(80)}")
            null
        }
    }
}
