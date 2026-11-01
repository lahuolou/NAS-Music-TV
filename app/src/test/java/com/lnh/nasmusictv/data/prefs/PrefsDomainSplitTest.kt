package com.lnh.nasmusictv.data.prefs

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * R-4 批次 4a 门禁：AppPreferences 巨型文件拆分的实现下移试点。
 *
 * 断言（源码扫描 + 存在性）：
 * - AppPreferences.kt 不再持有 Download 域与搜索历史域的**实现**（已下移到 DownloadPrefs / HistoryPrefs）；
 * - DownloadPrefs / HistoryPrefs 确实自持这些实现；PrefsJson.kt 承载安全解析。
 *
 * 防止「实现又搬回大文件」的回退。行为兼容由既有 AppPreferencesTest 类群覆盖。
 */
class PrefsDomainSplitTest {

    private fun findRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/lnh/nasmusictv/data/prefs"),
            File("app/src/main/java/com/lnh/nasmusictv/data/prefs"),
            File("../app/src/main/java/com/lnh/nasmusictv/data/prefs"),
        )
        val root = candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError("找不到 prefs 源目录（user.dir=${File("").absolutePath}）")
        return root
    }

    private fun sourceText(root: File, name: String): String {
        val f = File(root, name)
        assertTrue("缺少源文件 $name", f.isFile)
        return f.readText()
    }

    @Test
    fun appPreferencesNoLongerHoldsDownloadDomainImpl() {
        val t = sourceText(findRoot(), "AppPreferences.kt")
        for (impl in listOf(
            "fun setDownloadEnabled", "fun setAutoDownloadOnPlay", "fun setAutoDownloadLimit",
            "fun setDownloadLocation", "fun setLocalMusicEnabled", "fun setExportTreeUri",
            "fun setExportVolumeId", "val exportTreeUri", "val exportVolumeId",
        )) {
            assertTrue("AppPreferences 仍残留 Download 域实现片段: $impl", impl !in t)
        }
    }

    @Test
    fun appPreferencesNoLongerHoldsSearchHistoryImpl() {
        val t = sourceText(findRoot(), "AppPreferences.kt")
        for (impl in listOf(
            "fun getSearchHistory", "fun recordSearch", "fun purgeExpiredSearchHistory",
            "val searchHistory: Flow", "searchHistoryMaxSize", "searchHistoryTtlMs",
            "private inline fun <T> safeParseJson",
        )) {
            assertTrue("AppPreferences 仍残留搜索历史域实现片段: $impl", impl !in t)
        }
    }

    @Test
    fun downloadPrefsHoldsImpl() {
        val t = sourceText(findRoot(), "DownloadPrefs.kt")
        for (impl in listOf(
            "fun setDownloadEnabled", "fun setAutoDownloadOnPlay", "fun setAutoDownloadLimit",
            "fun setDownloadLocation", "fun setLocalMusicEnabled", "fun setExportTreeUri",
            "fun setExportVolumeId", "val exportTreeUri", "val exportVolumeId",
        )) {
            assertTrue("DownloadPrefs 缺失实现片段: $impl", impl in t)
        }
    }

    @Test
    fun historyPrefsHoldsSearchHistoryImpl() {
        val t = sourceText(findRoot(), "HistoryPrefs.kt")
        for (impl in listOf(
            "fun getSearchHistory", "fun recordSearch", "fun purgeExpiredSearchHistory",
            "val searchHistory: Flow", "searchHistoryMaxSize", "searchHistoryTtlMs",
        )) {
            assertTrue("HistoryPrefs 缺失搜索历史实现片段: $impl", impl in t)
        }
    }

    @Test
    fun prefsJsonCarriesSafeParse() {
        val t = sourceText(findRoot(), "PrefsJson.kt")
        assertTrue("PrefsJson.kt 缺失 parse 入口", "object PrefsJson" in t)
        assertTrue("PrefsJson.kt 缺失 inline parse", "inline fun <T> parse" in t)
    }

    @Test
    fun appPreferencesStillExposesInternalPrefsHelpers() {
        val t = sourceText(findRoot(), "AppPreferences.kt")
        for (helper in listOf(
            "internal suspend fun prefsEdit", "internal val prefsData", "internal fun prefsGson",
        )) {
            assertTrue("AppPreferences 缺失 R-4 内部共享 API: $helper", helper in t)
        }
    }
}
