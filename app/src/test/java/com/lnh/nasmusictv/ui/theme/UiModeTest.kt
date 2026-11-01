package com.lnh.nasmusictv.ui.theme

import com.lnh.nasmusictv.ui.components.PHONE_TOUCH_TARGET_DP
import com.lnh.nasmusictv.ui.components.adaptiveColumnsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形态因子 / 自适应体系的纯函数单测。
 *
 * （2026-10-28）：竖屏整体下线后重写 ——
 * - [deriveUiMode] 只按设备类型判定（TV 优先，非 TV 一律手机横屏；方向不再参与）
 * - [adaptiveColumnsOf] 列数阈值（`>=1000` / `>=600` / else）
 * - §2.7 dp 口径护栏（`PHONE_UI_SCALE` 下的物理/Compose dp 换算，手机端缩放仍生效）
 *
 * ⚠️ 不依赖 Compose / Android 运行时（`ActivityInfo`/`Configuration` 常量已不再需要），
 * 可在**纯 JVM**（无需 Robolectric）下运行。
 */
class UiModeTest {

    // ── deriveUiMode（竖屏下线后：只按设备类型，方向不参与） ──

    @Test
    fun `TV 设备判定为 TV`() {
        assertEquals(UiMode.TV, deriveUiMode(isTV = true))
    }

    @Test
    fun `手机判定为 PhoneLandscape`() {
        assertEquals(UiMode.PhoneLandscape, deriveUiMode(isTV = false))
    }

    /**
     * B1 回归护栏（方案 §3.1 硬规则）：
     * 手机**不得**落入 TV 分支 —— 否则 AppRoot 会把手机切到 TV 专属导航，
     * 违反「手机横屏与改前一致」。
     */
    @Test
    fun `手机不得判定为 TV（B1 回归）`() {
        assertNotEquals(UiMode.TV, deriveUiMode(isTV = false))
    }

    // ── adaptiveColumnsOf ──

    @Test
    fun `列数阈值 窄屏落 phonePortrait`() {
        assertEquals(3, adaptiveColumnsOf(360, tv = 6, phonePortrait = 3, medium = 6))
        assertEquals(3, adaptiveColumnsOf(599, tv = 6, phonePortrait = 3, medium = 6))
    }

    @Test
    fun `列数阈值 600 起落 medium`() {
        assertEquals(6, adaptiveColumnsOf(600, tv = 6, phonePortrait = 3, medium = 6))
        assertEquals(6, adaptiveColumnsOf(999, tv = 6, phonePortrait = 3, medium = 6))
    }

    @Test
    fun `列数阈值 1000 起落 tv`() {
        assertEquals(6, adaptiveColumnsOf(1000, tv = 6, phonePortrait = 3, medium = 6))
        assertEquals(6, adaptiveColumnsOf(1920, tv = 6, phonePortrait = 3, medium = 6))
    }

    @Test
    fun `列数阈值 电台网格三档`() {
        assertEquals(3, adaptiveColumnsOf(1280, tv = 3, phonePortrait = 1, medium = 2))
        assertEquals(2, adaptiveColumnsOf(800, tv = 3, phonePortrait = 1, medium = 2))
        assertEquals(1, adaptiveColumnsOf(360, tv = 3, phonePortrait = 1, medium = 2))
    }

    // ── §2.7 dp 口径护栏 ──

    /**
     * 手机端 `LocalDensity` 被 `PHONE_UI_SCALE = 0.82` 缩放（横屏手机同样生效），
     * Compose 里写的 `X.dp` 实际只占 `X × 0.82` 个**物理 dp**。
     *
     * 护栏 1：56dp → 物理 ≈ 45.9dp ≥ 44dp（D3 达标）。
     * 护栏 2：物理 44dp 需要 Compose ≥ 53.7dp —— 代码里**不能**再拿 44dp 当热区下限。
     *
     * ⚠️ 函数名里不能出现 `.`（JVM 方法名非法字符），故写作「2-7」而非「2.7」。
     */
    @Test
    fun `触摸目标 dp 口径（D3 与方案 2-7 回归护栏）`() {
        val scale = CompactSizes.PHONE_UI_SCALE
        val physicalOf56 = 56 * scale
        assertEquals(45.92f, physicalOf56, 0.01f)
        assertTrue("56dp 容器物理高度不足 44dp", physicalOf56 >= 44f)

        val composeFor44Physical = 44f / scale
        assertEquals(53.66f, composeFor44Physical, 0.01f)
        assertTrue("44 物理 dp 需要更大的 Compose dp", composeFor44Physical > 44f)
    }

    /**
     * P0-26 回归护栏：`PHONE_TOUCH_TARGET` 必须真的满足「物理 ≥ 44dp」，
     * 且 44 / 48 / 52 这三个**曾被写进代码**的值必须被证明不达标 —— 防止有人再把它们改回去。
     *
     * ⚠️ 这里用 `assertTrue` 而**不是** Kotlin 的 `assert(...)`：
     * 后者在未开 `-ea` 的测试 JVM 里是**空操作**，护栏会静默失效。
     */
    @Test
    fun `PHONE_TOUCH_TARGET 满足物理 44dp 而 44 与 48 不满足`() {
        val scale = CompactSizes.PHONE_UI_SCALE

        // 常量本身达标
        val physicalOfTarget = PHONE_TOUCH_TARGET_DP * scale
        assertTrue(
            "PHONE_TOUCH_TARGET_DP=$PHONE_TOUCH_TARGET_DP 只有 $physicalOfTarget 物理 dp",
            physicalOfTarget >= 44f,
        )
        assertTrue(
            "常量小于 44 物理 dp 所需的 Compose dp",
            PHONE_TOUCH_TARGET_DP >= 44f / scale,
        )
        // 且是「够用的最小整数档」：再小一档（52）就不够了，说明不能下调
        assertTrue("若 52dp 已达标，PHONE_TOUCH_TARGET 应下调", 52f * scale < 44f)

        // 历史上被写进代码的三个值都不达标（§2.7 第 1 条的原始依据）
        assertTrue("44 Compose dp 竟然达标了？§2.7 口径需重新推导", 44f * scale < 44f)
        assertTrue("48 Compose dp 竟然达标了？§2.7 口径需重新推导", 48f * scale < 44f)
        assertEquals(36.08f, 44f * scale, 0.01f)
        assertEquals(39.36f, 48f * scale, 0.01f)
    }

    @Test
    fun `LYRICS_RECOVER_SCALE 是 PHONE_UI_SCALE 的倒数`() {
        assertEquals(1f / CompactSizes.PHONE_UI_SCALE, CompactSizes.LYRICS_RECOVER_SCALE, 0.0001f)
    }

    /**
     * **P2-37 决策护栏**（方案 §9 P2-37「是否把 0.82 调到 0.88」）。
     *
     * 这不是"禁止改"，而是**强迫改的人先读这一段**：`PHONE_UI_SCALE` 是 §2.7 全部尺寸口径的
     * 唯一输入 —— 一改就要连带复核：
     *
     * 1. `CompactSizes.LYRICS_RECOVER_SCALE`（= 1 / PHONE_UI_SCALE，有独立用例守着）
     * 2. `PHONE_TOUCH_TARGET_DP` 的取值依据（0.82 下 52dp 不达标才取 56dp；
     *    若改到 0.88，52dp 恰好达标，取值应重新推导）
     * 3. 所有手机端固定尺寸的物理换算（`docs/conventions-adaptive-ui.md` §6）
     * 4. 上表 §2.7 的"两条硬结论"与方案 §2.5 的决策记录
     *
     * 本版**有意保持 0.82**：方案标为"可选"，且无法上机验证改后的观感。
     */
    @Test
    fun `PHONE_UI_SCALE 变更需同步复核 2-7 全部口径（P2-37 护栏）`() {
        assertTrue(
            "PHONE_UI_SCALE 变了 —— 请同步复核 docs/conventions-adaptive-ui.md §6：" +
                "PHONE_TOUCH_TARGET 取值依据、LYRICS_RECOVER_SCALE、各手机端固定尺寸的物理 dp，" +
                "以及 docs/archive/phone-portrait-ui-plan.md §2.7 / §2.5 的结论。" +
                "当前实测值 = ${CompactSizes.PHONE_UI_SCALE}",
            kotlin.math.abs(CompactSizes.PHONE_UI_SCALE - 0.82f) < 0.0001f,
        )
    }
}
