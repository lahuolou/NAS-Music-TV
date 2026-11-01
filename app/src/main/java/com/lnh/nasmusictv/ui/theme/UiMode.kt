package com.lnh.nasmusictv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 形态因子（Form Factor）。
 *
 * - [TV]：电视端（含只上报 `android.hardware.type.television` 的非认证盒子）
 *
 * （2026-11-01）：**TV-only 收敛** —— 手机作为独立播放客户端已整体下线，
 * 形态因子仅保留 TV。原 `PhoneLandscape` 枚举值已移除（手机恒横屏、复用 TV 布局的现状代码路径
 * 即 TV 路径本身）。`LocalUiMode` 恒提供 `UiMode.TV`，`deriveUiMode` 恒返回 `UiMode.TV`。
 */
enum class UiMode { TV }

/** 当前形态因子（TV-only 收敛后恒为 TV，保留以兼容既有调用点/测试） */
val LocalUiMode = staticCompositionLocalOf { UiMode.TV }

/**
 * 纯函数：设备类型 → 形态因子。
 *
 * TV-only 收敛后不再区分手机形态，恒返回 [UiMode.TV]。
 * 不依赖 Compose / Android 运行时，可在纯 JVM 单测中直接断言。
 */
fun deriveUiMode(isTV: Boolean): UiMode = UiMode.TV
