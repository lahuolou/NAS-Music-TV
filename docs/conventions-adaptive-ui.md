# TV UI 维护约定

> 文件名沿用 `conventions-adaptive-ui.md` 以保持既有链接有效；**内容已按 TV-only 收敛后的代码重写**。
> 适用范围：新增/修改任何 Compose UI、焦点交互、尺寸与对话框代码。
> 与本文冲突的 `docs/archive/phone-portrait-ui-plan.md` 等历史方案是「当时设计意图」，**不代表当前实现**。

---

## 1. 现状：TV-only（先读这一节）

本项目已**整体下线手机客户端**，代码是纯 TV 横屏：

| 事实 | 证据 |
|------|------|
| `UiMode` 枚举**只有 `TV`** | `ui/theme/UiMode.kt:14` — `enum class UiMode { TV }` |
| `LocalUiMode` 恒提供 `UiMode.TV` | `ui/theme/UiMode.kt:17` |
| `deriveUiMode()` 恒返回 `UiMode.TV` | `ui/theme/UiMode.kt:25` |
| 强制横屏 | `AndroidManifest.xml:131` — `screenOrientation="sensorLandscape"` |
| 手机 system-bar / 竖屏组件已删除 | `MainActivity` / `AppRoot` 注释 |

⚠️ **历史文档里的「手机竖屏 / 手机横屏双形态」「`PHONE_UI_SCALE = 0.82` 缩放数学」「竖屏热区 56dp」等叙述全部失效**，不要照抄。

### 1.1 残留自适应 API 的真实语义（塌缩后的现状）

| API | 现状 | 怎么用 |
|-----|------|--------|
| `portraitTouchTarget(landscape: Dp): Dp` | **恒返回入参**（`CommonComponents.kt:180`） | 继续用它包裹尺寸，保持调用点稳定；**不要**再做缩放换算 |
| `responsiveDialogSize(landscapeWidth: Dp): Modifier` | **= `Modifier.width(landscapeWidth)`**（`CommonComponents.kt:189`） | 对话框宽度仍走它；`scrollable` 参数已移除 |
| `adaptiveColumns(tv, phonePortrait, medium)` | **仍按 `LocalConfiguration.screenWidthDp` 分档**：`≥1000→tv` / `≥600→medium` / 其余→`phonePortrait`（`CommonComponents.kt:134-152`） | TV 实机（≥1000dp）恒走 `tv` 档；`phonePortrait` 形参在 TV 上是死参，保留只为不破坏调用点 |
| `TV_TOUCH_TARGET_DP = 56f` | **有效** | TV 触摸/焦点热区下限；`UiModeTest` 仍在纯 JVM 断言该算术 |

### 1.2 ⛔ 反向门禁：竖屏残留扫描
`ScreenUiModeCoverageTest` 已**反转**为「main 源集不得再出现竖屏残留标识符」：
凡 `src/main/java/com/lnh/nasmusictv` 或 `res` 里重新出现已删除的竖屏标识符（组件名、prefs、字符串前缀、旧断言）**一律判红**。
大小写敏感；`portraitTouchTarget`（保留 API）与 `adaptiveColumnsOf` 的 `phonePortrait` 形参**不受影响**。

> 竖屏代码本身编译不过（`UiMode.PhonePortrait` 已不存在），但**注释、字符串、文档引用**仍可能被人带回来，或把已删组件的旧写法当模板复制 —— 这道门禁就是防这个。

---

## 2. 焦点视觉（TV 最易复发的 UI 缺陷，最高优先级）

### 2.1 判据必须是 `shouldShowFocusVisuals()`，不能是 `isTVDevice()`

```kotlin
// ✅ 正确
val tvDevice = shouldShowFocusVisuals()        // ui/components/FocusIndicator.kt
val activeFocus = isFocused && tvDevice

// ⛔ 错误
val activeFocus = isFocused && isTVDevice()     // 非认证盒子上恒 false ⇒ 焦点完全看不见
```

`isTVDevice()`（`FocusableSurface.kt:69`）纯靠 `hasSystemFeature(leanback | type.television)` 嗅探。本项目历史上已被迫为这个判据补过两次（先从 leanback 扩到 `type.television`，再修「单查 leanback」）—— 在**非 Google 认证的电视盒子**上仍常双双返回 false。一旦为 false，缩放 / 焦点环 / 容器色 / 内容色**全部关闭**，用户只能按确认键反推焦点位置。

正确判据是「**用户正在用按键导航**」，可直接观测，不依赖嗅探。四条任一成立即显示：

| # | 判据 | 覆盖场景 |
|---|------|----------|
| 1 | 没有触摸屏（`!FEATURE_TOUCHSCREEN`） | 电视盒子 / 机顶盒 / 投影仪常态 |
| 2 | `FEATURE_LEANBACK` **或** `android.hardware.type.television` | 认证电视 |
| 3 | **存在支持 `SOURCE_DPAD` 的输入设备** | ⭐ 非认证盒子；**进场即命中，无需先按键** |
| 4 | 进程内收到过方向/确认键（`DpadInputTracker`） | 运行中才接入的蓝牙键盘 / 手柄 |

判据 3 是「切到页面立刻看到焦点环」这条需求的**唯一保证**（1、2 是静态嗅探会落空；4 必须先按一次键，正好是用户拒绝的那个行为）。

### 2.2 缓存策略
`InputDevice.getDeviceIds()` 是 binder 调用，而 `shouldShowFocusVisuals()` 被 **135+ 组件在组合期调用** ⇒ 必须缓存。
- ⛔ **不能**写成 `remember { … }`（每个调用点各一份缓存，首屏仍 135 次 binder 调用）
- ✅ 放 `object` 的 `by lazy`，全进程只查一次

**「设备是运行中才接入的」不做过期机制**：蓝牙键盘后连时该值确实过期，但用户要用键盘导航就得先按方向键 —— 那一刻判据 4 立刻生效并触发全体重组。3 与 4 互补。主动过期反而会在用户形成肌肉记忆后把焦点环抽走，那正是要修的故障本身。

### 2.3 误判面
部分 ROM / 模拟器会给内建触摸数字化器**顺带多报 source 位**，那样会被误判成远控设备。处理方式：

```kotlin
internal fun isRemoteNavigationDevice(hasDpad: Boolean, isTouchDigitizer: Boolean): Boolean =
    hasDpad && !isTouchDigitizer
```
即排除「既支持 DPAD 又是触摸数字化器」的设备。真正的遥控器/键盘/手柄是**独立** input device，不会同时声明 `SOURCE_TOUCHSCREEN`，所以这条排除**不会**牺牲「电视盒子谎报 touchscreen 也要点亮焦点环」这条核心需求。

### 2.4 焦点环必须画在 `.background(...)` 之后

Compose 的绘制顺序 = 修饰符链顺序，**越靠前越靠下**。若写成：

```kotlin
.border(width = if (activeFocus) 2.dp else 0.dp, color = …, shape = shape)  // ⛔ 画在下面
.background(targetContainerColor, shape)                                    // ⛔ 盖住它
```

焦点环只在容器色为 `Color.Transparent` 的少数组件上侥幸可见；所有卡片/列表行用的都是不透明 `NasMusicColors.Surface` ⇒ **全应用看不见焦点框**。这与对比度无关（`FocusRing #2DD4BF` 对 `Background #0C1222` 对比度足够）。

自实现焦点视觉一律走：

```kotlin
Modifier.focusRing(shape, color, visible)   // 挂在链尾（.background 之后）
```
它画「外侧柔光带（7dp, alpha 0.26）+ 内侧实心环（3dp, 全不透明）」两层，靠 `Modifier.border` 的内缩语义落在边界**之内** ⇒ 被 `.clip(shape)` 裁剪后仍完整可见，且**绝不覆盖组件内文字**。

### 2.5 `onFocusChanged` 里别读外层派生的 `activeFocus`
`onFocusChanged { }` 的 lambda **捕获本次组合的值**，焦点刚变化时读到的还是旧值。需要立即生效的动画（如 1.15 倍缩放）必须读状态本身：

```kotlin
.onFocusChanged { state ->
    isFocused = state.hasFocus && tvDevice          // ✅
    scope.launch { animScale.animateTo(if (state.hasFocus && tvDevice) 1.15f else 1f, tween(150)) }
}
```

### 2.6 判据的使用纪律
- ⛔ 不要再写一份 `packageManager.hasSystemFeature(...)`
- ⚠️ `isTVDevice()` **仍保留给布局/默认值判断**（外接存储默认值等），它与焦点视觉判据**故意不同** —— 布局判错顶多版式不对，焦点判错则是「完全看不见焦点」
- ⚠️ `MainActivity` 里的 `isTVDevice` 也只是布局判据，不要当焦点视觉判据来源

---

## 3. 内容色契约（`FocusableSurface` 必须同时下发两个 CompositionLocal）

`androidx.tv.material3.LocalContentColor` 的**默认值是 `Color.Black`**：

```kotlin
// androidx/tv/material3/ContentColor.kt
val LocalContentColor = compositionLocalOf { Color.Black }
```

而 tv-material3 的 `Icon` / `Text` **都会回退到它**（`Icon.kt:67` 的 `tint`、`Text.kt:110-113` 的 `color.takeOrElse { … LocalContentColor.current }`）。

`FocusableSurface` 早期**只**提供自定义的 `LocalFocusableContentColor` ⇒ 内部凡是**没显式写 `tint =` / `color =`** 的 `Icon` / `Text` 全部画成**黑色**，压在深色底（`Surface #162032` / `SurfaceVariant #1E2D42`）上就是"看不见"。这是真机反馈「下方主按钮 / 标题行按钮看不清」的根因。

### 约定
```kotlin
CompositionLocalProvider(
    LocalFocusableContentColor provides targetContentColor,   // 项目自有：给显式读它的组件
    LocalContentColor provides targetContentColor,            // tv-material3 官方：给 Icon/Text 兜底
) { content() }
```

**任何自建容器组件**（`FocusableSurface` 之外新写的 Surface/Card/Button 封装）只要内部可能放裸 `Icon` / `Text`，就**必须**同时下发这两个 —— 少一个就黑字压深底。

---

## 4. 尺寸与对话框

### 4.1 触摸目标
TV 端物理 dp = Compose dp（无缩放），故热区下限直接用 **56dp**（`TV_TOUCH_TARGET`）即可满足 Android 无障碍 44 物理 dp 下限，并兼顾 TV 远焦 + 大屏可读性。

```kotlin
Modifier.size(portraitTouchTarget(48.dp))   // 现在 = 48.dp
Modifier.height(portraitTouchTarget(44.dp)) // 现在 = 44.dp
```
`portraitTouchTarget` 恒等，但**沿用它**可保持调用点语义清晰、且未来若恢复多形态不必全量改。

⚠️ **两个坑**：
1. **`padding` 会削热区**。`Modifier.height(52.dp).padding(vertical = 4.dp)` 传给 `FocusableSurface` 时，`clickable` 加在 padding **之后** ⇒ 实际热区只有 44dp。
2. **容器即热区**。整行瓦片类组件的做法是「子项 `fillMaxSize()` / 56dp + 容器不加垂直 padding」，热区等于整块容器，不要再套内边距。

### 4.2 小视觉元素不要直接挂 `clickable`
`Modifier.size(8.dp).clickable { }` 只有 8dp 热区。正确写法是**视觉与热区分离**：

```kotlin
Box(
    modifier = Modifier
        .size(portraitTouchTarget(44.dp))   // 外层承担热区
        .clickable { onSwitch(m) },
    contentAlignment = Alignment.Center,
) {
    Box(Modifier.size(if (active) 8.dp else 6.dp).clip(CircleShape).background(color))  // 内层只做视觉
}
```

⚠️ **扫描脚本的盲区**（真实踩过）：
- 尺寸是**表达式**时正则匹配不到（`size(if (a) 8.dp else 6.dp)` 没有字面形态）⇒ 扫描脚本必须取括号配对内容再抽 `.dp` 字面量，否则**空转**（报"0 处"，看起来干净，实际什么都没查）
- **手势可能写在同一行**（`Modifier.size(8.dp).clickable { }`）⇒ 用 `^\s*\.` 锚行首的手势正则匹配不到 ⇒ 必须另设一条**不锚行首**的同行正则做「同行 + 后续行」双判定，并排除整行注释（否则 KDoc 里的举例会被误报）

> 📌 脚本已入库：`scripts/audit_small_touch_target.py`
> **必须先跑 `--selftest` 再实跑** —— 源码扫描型护栏不自证，就无法区分"真干净"和"空转"。

### 4.3 对话框宽度
```kotlin
Modifier.then(responsiveDialogSize(480.dp))   // 现在 = Modifier.width(480.dp)
```
⛔ 直接写死 `fillMaxWidth(0.92f)` 会把 TV 上的 480~720dp 对话框压窄，属**回归**。
⛔ 对话框内**不要再叠 `fillMaxWidth` / `fillMaxHeight`** —— `responsiveDialogSize` 只给 width，再叠 fill 会被忽略（`LogsSettingsSection` 曾因此出现「还未显示完全」）。

---

## 5. 新增 Screen / 组件检查清单

- [ ] 顶层容器宽度不写死（`width(760.dp)` 之类）→ `fillMaxWidth()` / `widthIn(max = …)`
- [ ] 多列网格走 `adaptiveColumns(...)`，⛔ 不写 `GridCells.Fixed(常量)`
- [ ] 对话框走 `responsiveDialogSize(...)`
- [ ] 触摸目标下限 56dp，沿用 `portraitTouchTarget(...)` 包裹
- [ ] ⛔ **小视觉元素（圆点 / 图标 / 进度条）不直接挂 `clickable`** —— 外层承担热区 + 内层只做视觉（§4.2）
- [ ] 焦点视觉一律 `shouldShowFocusVisuals()`，⛔ 不用 `isTVDevice()`
- [ ] 焦点环挂链尾（`.background()` 之后）
- [ ] 自建容器组件同时下发 `LocalFocusableContentColor` + `LocalContentColor`
- [ ] 新弹层（`Box` 覆盖层）→ 必须 `RegisterDialogBackHandler(onDismiss)`
- [ ] 页面级 BACK 状态提升到 `MainViewModel`，⛔ 不藏在页面内部
- [ ] ⛔ **禁止在 `AppRoot` 顶层订阅 `progress` / `duration`**（1000ms `Handler` 轮询 ⇒ 整棵树每秒重组）。需要进度的组件（如 `MiniPlayer`）在**自己内部**订阅
- [ ] 新增 UI 文案**同步** `res/values/strings.xml` 与 `res/values-en/strings.xml`
- [ ] 纯逻辑（列数、方向决策）抽顶层 `internal fun` 并补 JVM 单测

---

## 6. 门禁清单（跑 `testDebugUnitTest` 即生效，CI 已阻塞）

| 门禁 | 位置 | 守什么 |
|------|------|--------|
| `FocusIndicatorContractTest` | `app/src/test/…/ui/` | 焦点环修饰符链顺序、四条判据完整性、D-PAD 探测与缓存、`isRemoteNavigationDevice` 误判面（含负向用例） |
| `FocusIndicatorDeviceJudgeTest` | `app/src/test/…/ui/` | 焦点判据纯函数的正负用例 |
| `FocusableSurfaceColorContractTest` | `app/src/test/…/ui/` | 内容色契约（含 4 组负向自证，防 import 假通过） |
| `SmallTouchTargetScanTest` | `app/src/test/…/ui/` | 小触摸目标扫描（**三层自证**：负向用例 ×2 + 误报防线 ×1 + 空转断言） |
| `ScreenUiModeCoverageTest` | `app/src/test/…/ui/` | **竖屏残留反向扫描**（§1.2） |
| `UiModeTest` | `app/src/test/…/ui/theme/` | `deriveUiMode` 恒 TV、`adaptiveColumnsOf` 列数阈值、`TV_TOUCH_TARGET_DP` 算术（纯 JVM，无需 Robolectric） |
| `ChipContentWidthScanTest` / `SettingsRowWidthScanTest` / `SettingsGroupCardContractTest` / `SettingsGroupCoverageTest` / `SettingsSectionWiringGateTest` / `LogsTabWiringGateTest` / `BackupResultVisibilityGateTest` / `SettingsEndpointDialogWiringTest` | `app/src/test/…/ui/` | 设置页各项结构与接线护栏 |

⚠️ **修改判定逻辑时，若把负向用例一起改"绿"了，护栏就废了** —— 请谨慎。

---

## 7. 验证手段（可复现）

### 7.1 构建 / 测试
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17.0.21.0.2"
export ANDROID_HOME="/d/tools/Android/Sdk"

# ⛔ 本机必须 --no-daemon（fork 子进程起不来：AAPT2 / Kotlin 守护进程 AccessDenied）
./gradlew :app:compileDebugKotlin --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx4g

# ⛔ 中文路径的 GRADLE_USER_HOME 会让测试 worker 崩溃 → 用 subst 映射到纯 ASCII 盘
subst G: "C:\Users\内涵哥\.gradle"
export GRADLE_USER_HOME=G:
./gradlew :app:testDebugUnitTest --no-daemon --continue --max-workers=2 -Dorg.gradle.jvmargs=-Xmx4g

# lint（基线 0 Error）
./gradlew :app:lintDebug --no-daemon
```
⚠️ 增量跑显示 "up-to-date" 时**不会真正执行测试**，验数必须带 `--rerun-tasks`。

### 7.2 静态自查
```bash
# ① 是否误引 material3（不在编译类路径）
grep -rn "import androidx.compose.material3" app/src/main/java | wc -l   # 期望 0

# ② 是否还有裸 44/48/52dp 触摸目标（需人工确认「是不是触摸目标」）
grep -rnE "\.(size|height|width)\((\s*)([0-3][0-9]|4[0-9]|5[0-3])\.dp\)" app/src/main/java/com/lnh/nasmusictv/ui \
  | grep -vE "Karaoke|MvPlayback|VisualEqualizer|Shimmer|Cover|cover|favicon|Spacer"

# ③ 小触摸目标扫描脚本（自带负向自证，先自证再实跑）
python scripts/audit_small_touch_target.py --selftest
python scripts/audit_small_touch_target.py

# ④ 中文/编码自查
python scripts/check_chinese.py
```

### 7.3 人工复核
```bash
# FocusableSurface 是否同时下发两个 ContentColor
grep -n "LocalContentColor provides\|LocalFocusableContentColor provides" \
  app/src/main/java/com/lnh/nasmusictv/ui/components/FocusableSurface.kt
```

---

## 8. 一句话总结
**本项目是 TV-only 的 Compose 应用**：没有竖屏、没有手机端；`UiMode` 恒为 TV，`portraitTouchTarget`/`responsiveDialogSize` 是恒等包装。
真正要盯的只有三件事 —— **焦点视觉用 `shouldShowFocusVisuals()` 且焦点环画在背景之后**、**容器组件同时下发两个 ContentColor**、**热区 ≥56dp 且小视觉元素不单独挂 `clickable`**。
