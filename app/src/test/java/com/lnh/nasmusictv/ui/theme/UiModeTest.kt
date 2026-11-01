package com.lnh.nasmusictv.ui.theme

import com.lnh.nasmusictv.ui.components.TV_TOUCH_TARGET_DP
import com.lnh.nasmusictv.ui.components.adaptiveColumnsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形态因子 / 自适应体系的纯函数单测。
 *
 * （2026-11-01）：TV-only 收敛后重写 ——
 * - [deriveUiMode] 恒返回 [UiMode.TV]（不再区分手机形态，PhoneLandscape 已移除）
 * - [adaptiveColumnsOf] 列数阈值（`>=1000` / `>=600` / else），`<600dp` 保留给电视多窗口 / 低分辨率容器
 *
 * ⚠️ 不依赖 Compose / Android 运行时，可在纯 JVM（无需 Robolectric）下运行。
 */
class UiModeTest {

    // ── deriveUiMode（TV-only 收敛后恒为 TV）──

    @Test
    fun `TV 设备判定为 TV`() {
        assertEquals(UiMode.TV, deriveUiMode(isTV = true))
    }

    @Test
    fun `非 TV 也判定为 TV（TV-only 收敛，手机形态已移除）`() {
        assertEquals(UiMode.TV, deriveUiMode(isTV = false))
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

    // ── TV 触摸目标下限 ──

    /**
     * TV 触摸目标下限护栏：TV_TOUCH_TARGET_DP 必须满足物理 ≥ 44dp（TV / 遥控器可操作区域）。
     */
    @Test
    fun `TV_TOUCH_TARGET_DP 满足物理 44dp`() {
        assertTrue(
            "TV_TOUCH_TARGET_DP=$TV_TOUCH_TARGET_DP 小于 44dp 触摸下限",
            TV_TOUCH_TARGET_DP >= 44f,
        )
    }
}
