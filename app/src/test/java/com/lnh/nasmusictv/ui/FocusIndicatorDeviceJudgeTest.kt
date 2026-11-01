package com.lnh.nasmusictv.ui

import android.content.res.Configuration
import com.lnh.nasmusictv.ui.components.isStaticRemoteDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门禁：静态设备判据 [isStaticRemoteDevice] 必须覆盖「老电视 ROM」。
 *
 * 真机 TCL D65A620U（Android 4.2+ 时代的 AOSP 底座）上，仅靠 `hasSystemFeature`
 * 嗅探会**全部落空**：
 * - `android.software.leanback` 是 Android TV（API 21+）特性，老 ROM 恒 false；
 * - `android.hardware.type.television` 在非 Android TV 固件里也不声明；
 * - 这类电视用**红外遥控器**，不注册成 input device ⇒ `dpadInputDevicePresent` 也为 false；
 * - 「按过一次方向键」的兜底又必须先按一下才翻转 —— 用户拒绝的行为。
 *
 * 所以必须补上两条 **firmware 配置**判据：`uiMode=TELEVISION`（API 13+）与
 * `navigation=DPAD`（API 1 就有）—— 它们是系统开机写死的，不随 ROM 是否隐瞒特性而变。
 *
 * 约定见 `docs/conventions-adaptive-ui.md` §11 与 `FocusIndicatorContractTest.kt`。
 */
class FocusIndicatorDeviceJudgeTest {

    // ─────────────────────── 老电视（本次修复的目标设备） ───────────────────────

    @Test
    fun `老电视 uiMode 标为电视类型时必须命中（即使所有特性嗅探都落空）`() {
        // TCL D65A620U 类场景：leanback/television 双 false、ROM 甚至误报触摸屏、
        // uiMode 由固件标成 TELEVISION —— 此前这类设备焦点视觉被整个关掉。
        assertTrue(
            "uiMode=TELEVISION 应命中",
            isStaticRemoteDevice(
                hasLeanback = false,
                hasTelevisionType = false,
                hasTouchscreen = true,
                uiModeType = Configuration.UI_MODE_TYPE_TELEVISION,
                navigation = Configuration.NAVIGATION_NONAV,
            ),
        )
    }

    @Test
    fun `老电视固件声明 DPAD 导航时必须命中（即使所有特性嗅探都落空）`() {
        // 非 Android TV 老固件 uiMode 可能仍是 NORMAL，但导航方式固件恒标成 DPAD
        assertTrue(
            "navigation=DPAD 应命中",
            isStaticRemoteDevice(
                hasLeanback = false,
                hasTelevisionType = false,
                hasTouchscreen = true,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                navigation = Configuration.NAVIGATION_DPAD,
            ),
        )
    }

    @Test
    fun `没有触摸屏的盒子即使无任何电视特性也必须命中`() {
        assertTrue(
            "无触摸屏应命中",
            isStaticRemoteDevice(
                hasLeanback = false,
                hasTelevisionType = false,
                hasTouchscreen = false,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                navigation = Configuration.NAVIGATION_NONAV,
            ),
        )
    }

    // ─────────────────────── 既有判据保持有效 ───────────────────────

    @Test
    fun `Android TV 的 leanback 特性仍然命中`() {
        assertTrue(
            "leanback 应命中",
            isStaticRemoteDevice(
                hasLeanback = true,
                hasTelevisionType = false,
                hasTouchscreen = true,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                navigation = Configuration.NAVIGATION_NONAV,
            ),
        )
    }

    @Test
    fun `type-television 特性仍然命中`() {
        assertTrue(
            "type.television 应命中",
            isStaticRemoteDevice(
                hasLeanback = false,
                hasTelevisionType = true,
                hasTouchscreen = true,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                navigation = Configuration.NAVIGATION_NONAV,
            ),
        )
    }

    // ─────────────────────── 手机不误判（防「点一下永久高亮」回归） ───────────────────────

    @Test
    fun `裸机手机典型组合不得命中`() {
        // 手机：有触摸屏、uiMode=NORMAL、navigation=NONAV、无任何电视特性
        val phoneCases = listOf(
            Triple("标准手机", Configuration.UI_MODE_TYPE_NORMAL, Configuration.NAVIGATION_NONAV),
            Triple("带轨迹球的老手机", Configuration.UI_MODE_TYPE_NORMAL, Configuration.NAVIGATION_TRACKBALL),
            Triple("车载模式", Configuration.UI_MODE_TYPE_CAR, Configuration.NAVIGATION_NONAV),
        )
        val wrong = phoneCases.filter { (_, uiMode, nav) ->
            isStaticRemoteDevice(
                hasLeanback = false,
                hasTelevisionType = false,
                hasTouchscreen = true,
                uiModeType = uiMode,
                navigation = nav,
            )
        }
        assertTrue("手机不应命中：${wrong.map { it.first }}", wrong.isEmpty())
    }

    @Test
    fun `手机声明无触摸屏才会命中（多源设备的罕见情况，保留兜底）`() {
        assertTrue(
            "无触摸屏判据独立生效",
            isStaticRemoteDevice(
                hasLeanback = false,
                hasTelevisionType = false,
                hasTouchscreen = false,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                navigation = Configuration.NAVIGATION_NONAV,
            ),
        )
    }
}
