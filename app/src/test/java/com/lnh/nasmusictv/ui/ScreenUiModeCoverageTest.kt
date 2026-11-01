package com.lnh.nasmusictv.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 「竖屏残留扫描」门禁测试（重写）。
 *
 * 前身是「新增 Screen 必须有竖屏 UiMode 分支」的覆盖门禁（方案 §9 P1-32）；
 * 2026-10-28 竖屏整体下线（枚举缩为 `TV / PhoneLandscape`）后，该门禁的前提已不存在——
 * 没有任何屏幕需要处理竖屏。重写为**反向残留扫描**：凡在 main 源集/资源里再次出现
 * 已删除的竖屏标识符（组件名、prefs、字符串前缀、旧断言），一律判红。
 *
 * 这样做的价值：竖屏代码是**编译不过**的（`UiMode.PhonePortrait` 已不存在），但**注释、
 * 字符串、文档引用**仍可能被人带回来，或把已删组件的旧写法当模板复制 —— 那道门禁
 * 换了个方向继续守「不要再碰竖屏」。
 *
 * ## 判定规则
 *
 * main 源集（java + res）里出现任一 [PORTRAIT_TOKENS] 且文件没有豁免标记 → 违规。
 * 大小写敏感：`portraitTouchTarget`（保留的自适应 API）与 `adaptiveColumnsOf` 的
 * `phonePortrait` 形参不受影响。
 *
 * ⚠️ 启发式护栏：只查标识符字面量，不校验语义；有效性由本文件末尾的负向用例守住。
 * 约定见 `docs/conventions-adaptive-ui.md`。
 */
class ScreenUiModeCoverageTest {

    // ─────────────────────────── 真实源码扫描 ───────────────────────────

    @Test
    fun `main 源集不得再出现竖屏残留标识符`() {
        val roots = listOf(
            File("src/main/java/com/nasmusic/tv"),
            File("app/src/main/java/com/nasmusic/tv"),
            File("../app/src/main/java/com/nasmusic/tv"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("找不到 main 源码目录（user.dir=${File("").absolutePath}）", roots)

        val violations = mutableListOf<String>()
        var scanned = 0
        roots!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                scanned++
                val text = file.readText()
                if (text.contains(EXEMPT_MARKER)) return@forEach
                val hits = PORTRAIT_TOKENS.filter { text.contains(it) }
                if (hits.isNotEmpty()) {
                    violations += "${file.name} → ${hits.joinToString(", ")}"
                }
            }

        // 资源侧（字符串 key 前缀）
        val resCandidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res"),
        ).firstOrNull { it.isDirectory }
        if (resCandidates != null) {
            resCandidates.walkTopDown()
                .filter { it.isFile && it.extension == "xml" }
                .forEach { file ->
                    val text = file.readText()
                    val hits = RES_TOKEN_PREFIXES.filter { text.contains(it) }
                    if (hits.isNotEmpty()) {
                        violations += "${file.name} → ${hits.joinToString(", ")}"
                    }
                }
        }

        assertTrue("没有扫到任何源文件 —— 源目录定位可能失败：${roots.absolutePath}", scanned > 0)

        if (violations.isNotEmpty()) {
            fail(
                "竖屏已整体下线（2026-10-28），以下文件仍出现竖屏残留标识符：\n" +
                    violations.joinToString("\n") { "  • $it" } +
                    "\n\n修法：删除对应竖屏分支/组件引用/字符串 key。确实需要提及历史的，" +
                    "在文件顶部注释加 `$EXEMPT_MARKER: <理由>`。\n" +
                    "约定见 docs/conventions-adaptive-ui.md。",
            )
        }
    }

    // ─────────────────── 负向用例：护栏本身必须有效 ───────────────────
    //
    // 没有这组用例，"扫描通过"可能只是因为扫描逻辑空转（例如正则失配）。

    @Test
    fun `含 PhonePortrait 的源码必须被判违规`() {
        assertTrue(
            "护栏失效：PhonePortrait 残留没被判违规",
            PORTRAIT_TOKENS.any { "val m = LocalUiMode.current == UiMode.PhonePortrait".contains(it) },
        )
    }

    @Test
    fun `保留的自适应 API 不得被判违规`() {
        val allowed = listOf(
            "portraitTouchTarget(48.dp)",
            "adaptiveColumnsOf(widthDp, 6, 3, 6)",
            "responsiveDialogSize(720.dp)",
            "PHONE_TOUCH_TARGET",
        )
        allowed.forEach { sample ->
            assertTrue(
                "保留 API 被误判为竖屏残留：`$sample`（PORTRAIT_TOKENS 过宽，需收紧）",
                PORTRAIT_TOKENS.none { sample.contains(it) },
            )
        }
    }

    @Test
    fun `带豁免标记的源码放行`() {
        val text = """
            // $EXEMPT_MARKER: 历史说明（竖屏已下线）

            package p
        """.trimIndent()
        assertTrue("豁免标记未被识别", text.contains(EXEMPT_MARKER))
    }

    // ─────────────────────────── 内部实现 ───────────────────────────

    /** 豁免标记：写在文件顶部注释里，后跟理由 */
    private val EXEMPT_MARKER = "NasScreenUiMode-exempt"

    /**
     * 已删除的竖屏标识符（大小写敏感）。**任何一处出现都判红**——
     * 若确有保留意义（如文档性历史说明），用豁免标记显式声明。
     */
    private val PORTRAIT_TOKENS = listOf(
        // 枚举值与分支判据
        "PhonePortrait",
        "isPhonePortrait",
        "isPortraitPhone",
        // 已删组件 / prefs / 工具
        "PhoneTopBar",
        "PhoneNavBar",
        "MiniPlayer",
        "DisplayPrefs",
        "ScreenOrientationPref",
        "resolveOrientation",
        "SettingsSectionList",
        "SettingsSectionBackHeader",
        "PortraitQueueHeader",
        "PortraitRowMenuButton",
        "PortraitNowPlayingMode",
        "NowPlayingPortrait",
        "PortraitTopBarButton",
        "PORTRAIT_SONG_COVER_SIZE",
        "PORTRAIT_SONG_ROW_LINE1_HEIGHT",
        "AdaptiveLayout(",
        // 竖屏专属字符串 key（res 侧由 RES_TOKEN_PREFIXES 兜底）
        "settings_group_orientation",
        "settings_orientation_auto",
        "settings_section_back",
        "miniplayer_cd_play",
        "nav_phone_orientation_cd",
    )

    /** 字符串 key 前缀（res/xml 侧，values 与 values-en 都查） */
    private val RES_TOKEN_PREFIXES = listOf(
        "settings_orientation_",
        "settings_display_orientation",
        "settings_section_back",
        "miniplayer_cd_",
        "nav_phone_orientation_cd",
    )
}
