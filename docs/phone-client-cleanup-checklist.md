# NAS Music 手机版放弃 · 代码清理改造清单

> 编写日期：2026-11-01
> 背景：已移除竖屏、应用强制横屏（`sensorLandscape`）。现决定**放弃手机版 App 客户端**，本文列出需要清理的残留代码/组件，并说明原因与风险。

---

## ✅ 执行状态（源码审计版方案，2026-11-01）

> 本清单是较早的「手机版放弃」规划；实际执行以用户提供的《NASMusicTV-TV单平台与手机遥控保留-源码审计版改造方案.md》为准，且已**一次性完成全部阶段（1 / 2 / B / D / E / F / 5）**。
>
> **验证状态（2026-11-01 更新）**：下列状态已由**源码 grep + 编译 + 单测**三方核对 —— `:app:assembleDebug` **BUILD SUCCESSFUL（0 警告）**、`:app:testDebugUnitTest` **2427 测试 / 0 失败 / 0 错误**，`tv5`（Android TV / android-22）模拟器装包并运行成功。

| 阶段 | 状态 | 说明 |
|------|------|------|
| ① 移除 `portraitTouchTarget` 包裹 | ⏸ 有意保留 | 恒等函数，约 20 处调用 + `<600dp` 窄窗 TV 仍需（方案 §5.1） |
| ② 移除 `responsiveDialogSize` 包裹 | ⏸ 有意保留 | 同上，约 14 个 Dialog 调用 |
| ③ 删 `adaptiveColumnsOf` 的 `phonePortrait` 形参 | ⏸ 有意保留 | 形参名保留，`<600dp` 分支保留 |
| ④ 去 `CompactSizes.PHONE_UI_SCALE`(0.82) | ✅ 已完成 | UiMode 塌缩为仅 TV 时一并移除（验收 grep 清零） |
| ⑤ 简化 `MainActivity` 手机分支 | ⏸ 保留 | `isTVDevice()` 仍作布局判据保留，未删 |
| ⑥⑦ `UiMode` / `LocalPhoneCompact` 收敛 | ✅ 已完成 | 枚举塌缩为仅 TV，验收 grep `LocalPhoneCompact` 清零 |
| ⑩ 照片墙「仅手机」来源 | ✅ 已完成 | 见下方 ⑩ 更新 |
| 遥控服务（RemoteControlServer 等） | ✅ 保留未删 | 阶段 E/F 明确禁止误删 |

> 详细说明见 `docs/changes/2026-11-01-tv-only-phone-remote-cleanup.md`。

---

## 0. 前置判断：两种「手机」必须分清

代码库里「手机」一词有两种**完全不同的含义**，清理时只能动第一种，第二种必须保留：

| 类别 | 性质 | 清理？ |
|------|------|--------|
| **手机版 App 客户端** | 同一 App 在手机上运行时，走 `UiMode.PhoneLandscape` / `LocalPhoneCompact=true` 的运行时分支 | ✅ 可清理 |
| **手机当遥控器** | TV 端起 HTTP 服务、手机扫码用浏览器操控/传文件的功能，手机只是浏览器 | ❌ 必须保留 |

### ❌ 必须保留（切勿误删）
以下均为 **TV 端功能**，手机只是被操控的外设：
- `net/RemoteControlServer.kt`、`net/LocalInputServer.kt`、`net/BackupTransferServer.kt`
- `net/PlaylistUploadServer.kt`、`net/ModelTransferServer.kt`、`net/ServerConfigTransferServer.kt`
- `net/RemoteControlHtml.kt`、`net/LocalServerAuth.kt`、`net/HttpRequestBody.kt`
- 网盘/百度扫码授权相关（`netdisk/`、`baidu/` 的手机扫码 auth）
- `player/MediaSessionAccessPolicy.kt` 里 Google 助理（手机端）包名

### 关键架构事实
- 项目**没有** phone 独立 source set / product flavor（`app/src` 只有 `main`/`test`/`globe-upstream`）。
- 手机支持 = 运行时 `UiMode.PhoneLandscape` + `LocalPhoneCompact` 分支；`MainActivity` 据 `isTVDevice`（leanback/television 特性）推导。
- 在 TV 目标上 `isTVDevice=true` ⇒ 所有手机分支**永远走不到 else（TV）分支**，是死代码。

---

## 1. 改造清单

### P0 — 安全、机械、收益高（建议直接做）

#### ① 移除 `portraitTouchTarget(...)` 包裹
- **位置**：`ui/components/CommonComponents.kt:180`（已是恒等函数 `return landscape`）
- **调用点**：约 20 处（各 Dialog、`QueueScreen`、`SettingsScreen`、`ExitConfirmDialog` 等）。
- **原因**：函数名暗示「处理竖屏热区」，实际是空壳，误导维护者。直接内联为裸 `Dp` 即可。

#### ② 移除 `responsiveDialogSize(...)` 包裹
- **位置**：`ui/components/CommonComponents.kt:189`（恒等函数 `Modifier.width(landscapeWidth)`）
- **调用点**：约 14 个 Dialog（`ConfirmDialog`、`SettingsScreen`、`TextInputDialog`、`BackupTransferDialog`、`ModelTransferDialog`、`PlaylistPickerDialog`、`BaiduAuthDialog`、`LogsSettingsSection`、`PlaylistImportUploadDialog`、`ExportDeviceDialog`、`ConnectPromptDialog`、`QualityProbingDialog` 等）。
- **原因**：同上，内联为 `.width(X.dp)`。

#### ③ 删除 `adaptiveColumnsOf` 的 `phonePortrait` 形参
- **位置**：`ui/components/CommonComponents.kt:134`
- **原因**：第三参仅在 `widthDp < 600` 命中；横屏手机(~800dp)走 `medium`，竖屏手机(<600)才走 `phonePortrait`。竖屏已删 + 手机客户端放弃 ⇒ 该分支已死。
- **影响**：`RadioTab`、`Shimmer`、`PhotoWallSettingsSection`、`SettingsComponents`、`GenreYearLists`、`ArtistList`、`AlbumGrid` 等调用点去掉该实参；同步删 `UiModeTest` / `SettingsRowWidthScanTest` 里相关断言。

#### ④ 去掉 `CompactSizes.PHONE_UI_SCALE`(0.82) 密度缩放
- **位置**：`MainActivity.kt:249`（`density * 0.82`）；`Theme.kt:504` 定义；`LYRICS_RECOVER_SCALE = 1/0.82`。
- **原因**：手机不再支持 ⇒ TV 恒用基准密度，删除 `uiDensity` 分支与 `LYRICS_RECOVER_SCALE`（仅手机才需「恢复」）。

#### ⑤ 简化 `MainActivity` 的手机分支
- `isTVDevice=false` 时的 `systemBars hide` 分支（`MainActivity.kt:232`）、`LocalPhoneCompact provides !isTVDevice`（`:254`）在 TV 上永久走不进 → 整段删除，`isTVDevice` 检测可一并去掉。

---

### P1 — 结构性、需谨慎（去手机化核心）

#### ⑥ 收掉 `UiMode` 枚举 + `LocalUiMode` + `deriveUiMode` 的 `PhoneLandscape` 分支
- **位置**：`ui/theme/UiMode.kt`（`enum class UiMode { TV, PhoneLandscape }`）
- **调用点**：9 个页面用 `LocalUiMode.current == UiMode.PhoneLandscape`（AlbumDetailScreen / ArtistDetailScreen / EqualizerScreen / MineScreen / LibraryScreen / QueueScreen / NowPlayingScreen / SettingsScreen 等），均为 `if (phone) 小padding else 大padding`。
- **原因**：TV 上恒为 else 分支，这些判断是死代码，且给维护者「还有手机模式」的错觉。
- **取舍**：
  - **A（保守）**：`deriveUiMode` 恒返回 `TV`，分支保留（仍占空间）。
  - **B（彻底，推荐）**：删枚举 / CompositionLocal，所有判断点换成 TV 布局。改动 9 文件 + 门禁测试，但最干净。

#### ⑦ 收掉 `LocalPhoneCompact`
- **位置**：`Theme.kt:511` 定义，在 `MainActivity.kt:254` 注入。
- **调用点**：Theme 调色板（`phone` vs `tv` 基色，`:285`）、Air 风格（`:407`）、`KaraokeLyricsView`、`LyricsView`、`MineScreen`(`isPhone`)、`BrowseComponents`(`isPhone`)。
- **原因**：TV 上恒 `false`，`if (isPhone) ... else ...` 的真分支已死。

---

### P2 — Manifest / 分发层（需拍板）

#### ⑧ 真正「放弃手机安装」
- **位置**：`AndroidManifest.xml:63` 注释「both TV and phone supported」；`leanback required=false`、`screen.portrait required=false`、`android:roundIcon`。
- **可选动作**：设 `leanback required=true` + 删 `screen.portrait` feature + 注释改「TV only」。Play 商店/启动器不再向手机推送（侧载仍可行）。
- **风险**：仅改清单，无代码风险；属分发策略决策，非纯清理，需确认。

---

### P3 — 测试门禁

#### ⑨ 调整竖屏护栏测试
- `ScreenUiModeCoverageTest`（扫描 `UiMode.PhonePortrait` 防复活）、`UiModeTest`（`PhoneLandscape` 派生 + `phonePortrait` 列数断言）、`SmallTouchTargetScanTest`——手机客户端删掉后护栏前提消失。
- **建议**：删除 `ScreenUiModeCoverageTest`；另两个收窄为只验 TV 行为。

---

### ⚠️ 暂缓、需先确认（不要自动删）

#### ⑩ 照片墙「仅手机」来源 —— ✅ 已处理（源码审计版方案阶段 2，2026-11-01）
- 涉及：`MediaStorePhotoSource`、`ExternalFilePhotoSource` 的 SAF/手机分支、`SafDirectoryPolicy`、`PhotoDedup` 注释里的「手机」。
- **处理动作**：删除 `MediaStorePhotoSource.kt`；移除 `VisualizerViewModel` / `PhotoWallController` 接线、`PhotoWallSettingsSection` 的「图库」开关 UI、`AndroidManifest.xml` 两条手机图片权限（`READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED`）。
- **保守保留**（兼容旧备份 / `gallery:<id>` 缓存解码）：`PhotoSourceKind.GALLERY` 枚举值、`photoWallGalleryEnabled` 字段、`PhotoWallAccessPolicy` gallery 函数、`PermissionHelper` 照片 API、`VisualizerViewModel` 的 `GALLERY_*` 通知枚举与回滚函数。深删需 SDK 环境重编译并同步 gallery 测试。
- TV 照片墙现仅「外接存储 / Jellyfin」两来源。

---

## 2. 执行决策点（待确认）

1. **⑥⑦ 选 A（保守恒 TV）还是 B（彻底删除枚举）？** 建议 B。
2. **⑧ 是否把 Manifest 改为 TV only（禁手机安装）？** 涉及分发策略。
3. **执行范围**：建议先做 P0（①②③④⑤），再做 P1（⑥⑦），P2/P3 按上面决策推进；⑩ 先查证。

## 3. 验收方式
- `./gradlew assembleDebug --no-daemon -Pkotlin.compiler.execution.strategy=in-process` 通过。
- `./gradlew testDebugUnitTest --no-daemon` 全绿（注意 ⑨ 改动后门禁测试需同步更新）。
- 雷电模拟器装机确认 TV 布局无回归（顶部导航、播放页、各 Dialog 尺寸不变）。
