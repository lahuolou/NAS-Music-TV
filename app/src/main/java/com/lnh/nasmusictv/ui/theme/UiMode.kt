package com.lnh.nasmusictv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 形态因子（Form Factor）。
 *
 * - [TV]：电视端（含只上报 `android.hardware.type.television` 的非认证盒子）
 * - [PhoneLandscape]：手机（竖屏已整体下线，恒横屏），**复用 TV 横屏布局的现状代码路径**
 *
 * （2026-10-28）：**全项目移除竖屏** —— 应用强制横屏（Manifest
 * `screenOrientation="sensorLandscape"`），原竖屏形态与专属组件
 * （顶栏 / 迷你播放条 / 底部导航 / 两级设置页）已整体删除。
 */
enum class UiMode { TV, PhoneLandscape }

/** 当前形态因子（由 `MainActivity` 提供，默认 TV 以保持既有测试/预览行为） */
val LocalUiMode = staticCompositionLocalOf { UiMode.TV }

/**
 * 纯函数：设备类型 → 形态因子。
 *
 * TV 判定优先；非 TV 一律为手机横屏形态（方向不再参与判定 —— 竖屏下线，手机恒横屏）。
 *
 * 不依赖 Compose / Android 运行时，因此可在纯 JVM 单测中直接断言。
 */
fun deriveUiMode(isTV: Boolean): UiMode = if (isTV) UiMode.TV else UiMode.PhoneLandscape
