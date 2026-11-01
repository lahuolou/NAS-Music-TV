# NAS-Music-TV：TV 单平台 · 手机仅作浏览器遥控 改造记录（源码审计版）

- **change_id**：`NAS-TV-2026-11-01-TVONLY-PHONEREMOTE-01`
- **日期**：2026-11-01
- **依据方案**：`NASMusicTV-TV单平台与手机遥控保留-源码审计版改造方案.md`（用户提供的源码审计版改造方案）
- **基线**：当前工作树（本项目此环境未纳入 git 版本控制，`baseCommit` 不可校验；方案原文引用的基线 `06f1c761` 仅作文档记录，未在本环境核实）
- **目标平台**：Android TV（唯一完整播放客户端）；手机仅作浏览器遥控 / 输入终端
- **变更类型**：UI 形态收敛、移除手机独占「图库」照片来源、搜索拼音匹配提升为主路径、文本输入默认 TV 自定义键盘、保留并校验手机浏览器遥控服务、交付变更文档
- **新增依赖**：无
- **删除文件**：1（生产源码 `MediaStorePhotoSource.kt`）

## 1. 给维护者的摘要

本轮按「源码审计版改造方案」一次性完成全部阶段（1 / 2 / B / D / E / F / 5），把 Android TV 收敛为**唯一完整播放客户端**，同时**完整保留**手机浏览器遥控链路（扫码 → 浏览器 → 推文字 / 传文件）。要点：

1. **UI 形态收敛（阶段 1）**：`UiMode` 枚举已塌缩为仅 `TV`（前置会话完成）；`VolumeControl` 移除手机触屏按钮分支、收敛为 TV `FocusableSurface` 单面；`VisualizerStage` 移除手机独占的左上角「关闭」退出按钮（改走 TV 返回键 / `onPreviewKeyEvent`）。
2. **移除手机独占「图库」照片来源（阶段 2）**：删除 `MediaStorePhotoSource.kt` 实现，移除其在 `VisualizerViewModel` / `PhotoWallController` 的接线、设置页 UI、以及两条仅手机需要的 `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` 清单权限。**保守保留** `GALLERY` 枚举值、`photoWallGalleryEnabled` 字段、`PhotoWallAccessPolicy` 相关函数、`PermissionHelper` 照片 API，以避免旧备份 / `gallery:<id>` 缓存解码崩溃（见 §5 残留风险）。
3. **搜索拼音匹配提升为主路径（阶段 B）**：`SearchAggregator` 移除 `isTVDevice` 设备开关，拼音（全拼 / 首字母）匹配对所有客户端默认开启——TV 是唯一完整客户端，手机仅作遥控输入，拼音搜索本就是 TV 主行为，本次只是把「已有 TV 功能」变为默认路径，未删除拼音能力（`PinyinUtils` / `SongWithPinyin` 保留）。
4. **文本输入默认 TV 自定义键盘（阶段 D）**：`TextInputDialog` 移除 `isTVDevice` 嗅探，默认 `showSystemIme = false`（TV 自定义键盘）；二维码面板改为跟随调用方 `showQrCode` 开关开启（它是手机浏览器遥控输入入口，必须保留）；`LocalInputServer` 生命周期 / `qrText` / 历史建议均保留；「中文输入」按钮（系统 IME 切换）保留供已装中文 IME 的电视盒使用。
5. **保留手机浏览器遥控服务（阶段 E/F）**：`RemoteControlServer`（手机遥控 UI 服务）、`LocalInputServer`、`BackupTransferServer` / `PlaylistUploadServer` / `ServerConfigTransferServer` / `ModelTransferServer`、`LocalServerAuth`（二维码 token）、`QrCodeGenerator`、`NetworkUtils`、`SmbHttpStreamServer`、`RemoteControlHtml` 资产均**未删除**，且 `RemoteControlServer` 仍在 `MainViewModel.kt:200` 实例化、`LocalInputServer` 仍在 `TextInputDialog.kt:148` 实例化——遥控链路依然在线。
6. **清单与分发（阶段 4F / 决策项）**：已移除两条手机图片权限；`leanback required=false`、`screen.portrait/landscape required=false`、Android Auto `<meta-data>` + `MEDIA_PLAY_FROM_SEARCH` 按方案「单独决策」项**保留未改**（属分发策略，需你最终拍板）。
7. **文档与清单（阶段 5）**：本文件 + 同目录 `.json` 机器可读清单；更新 `CHANGELOG.md`、`README.md`、`docs/phone-client-cleanup-checklist.md`。
8. **追加批次（同日）**：
   - **反向遥控可达性修复**：`NetworkUtils.getLocalIpAddress()` 补 `/proc/net/fib_trie` 兜底 + 跨接口全局择优，使二维码给出同网段地址而非模拟器 NAT 地址（详见 §2.1）。
   - **单测全绿**：288 失败（旧包名源码根硬编码）→ 0，剩余 2 个脆性/门禁漂移失败亦收掉，最终 **2427 / 0 失败**（详见 §4.2）。
   - **文档以代码为基准重写**：`README.md`、`CHANGELOG.md`、`AGENTS.md`、`docs/technical-overview.md`、`docs/conventions-adaptive-ui.md` 全部按当前源码重写；`docs/archive/` 改为**代码对账索引**（保留历史原文，见 §2.5）。

## 2. 变更明细（按文件）

### 2.1 修改的生产 / 模型文件（12）

#### `app/src/main/java/com/lnh/nasmusictv/ui/components/CommonComponents.kt`
- **动作**：修改。
- **改动**：`PHONE_TOUCH_TARGET_DP` → `TV_TOUCH_TARGET_DP`（重命名由前置会话完成），本会话补 2 处注释修正（KDoc 去掉 `CompactSizes.PHONE_UI_SCALE(0.82)` 引用、交叉引用改指 `TV_TOUCH_TARGET_DP`）。
- **兼容性**：`portraitTouchTarget(...)` / `responsiveDialogSize(...)` 恒等函数保留（约 20+ 处调用点仍依赖，且 `<600dp` 窄窗 TV 仍需），未内联。

#### `app/src/main/java/com/lnh/nasmusictv/ui/theme/Theme.kt`
- **动作**：修改。
- **改动**：注释去掉 `LocalPhoneCompact` 引用（手机紧凑 UI 尺度已随手机 UI 下线移除）。

#### `app/src/main/java/com/lnh/nasmusictv/ui/components/VolumeControl.kt`
- **动作**：修改（阶段 1 §3.1）。
- **为什么**：原 `if (tv) {...} else {...}` 双分支里，手机触屏按钮分支在 TV 上永久走不进，是死代码。
- **改动**：移除 `val tv = isTVDevice()` 与双分支包裹，仅保留 TV `FocusableSurface` 单面；删除死代码 `VolumeCenterSurface(...)` / `VolumeStepButton(...)` 及未用 import（`fillMaxSize` / `size` / `LaunchedEffect` / `kotlinx.coroutines.delay`）。`isTVDevice()` 函数本身保留（`SettingsScreen.kt:580` 仍用）。

#### `app/src/main/java/com/lnh/nasmusictv/ui/components/VisualizerStage.kt`
- **动作**：修改（阶段 1 §3.2）。
- **改动**：移除手机独占的左上角「关闭」(`Icons.Filled.Close`) 退出按钮整块及其 import（`RoundedCornerShape` / `Icons` / `Close` / `contentDescription` / `semantics` / `Icon`）。
- **残留（警告级，非错误）**：`onExit: () -> Unit` 与 `isTV: Boolean` 形参现函数体内未使用；因调用方仍传参，保留签名（不删以免破坏调用点）。

#### `app/src/main/java/com/lnh/nasmusictv/ui/viewmodel/VisualizerViewModel.kt`
- **动作**：修改（阶段 2）。
- **改动**：移除 `import MediaStorePhotoSource`；`photoWallSources` 不再向来源表加入 `GALLERY`（`if (!isTVDevice) out[...GALLERY] = MediaStorePhotoSource(app)` 删除，改为说明注释）。
- **保守保留**：`isTVDevice` 懒值、`photoPermissionState`、`GALLERY_DENIED/GALLERY_REVOKED/GALLERY_PARTIAL` 通知枚举、`applyGalleryRollback` / `shouldRollbackGallerySwitch` 等——见 §5 残留风险。

#### `app/src/main/java/com/lnh/nasmusictv/backend/photo/PhotoWallController.kt`
- **动作**：修改（阶段 2）。
- **改动**：`enabledKinds(s)` 不再 `if (s.photoWallGalleryEnabled) out.add(PhotoSourceKind.GALLERY)`，并加注释说明旧 `gallery:<id>` 引用忽略。

#### `app/src/main/AndroidManifest.xml`
- **动作**：修改（阶段 2 / §4F）。
- **改动**：移除 `READ_MEDIA_IMAGES` 与 `READ_MEDIA_VISUAL_USER_SELECTED` 两条权限及注释；注释「both TV and phone supported」→「Android TV」、「Main Activity (supports both TV and phone)」→「Main Activity: Android TV」。
- **保留**：`READ_MEDIA_AUDIO` + 旧 `READ_EXTERNAL_STORAGE maxSdkVersion=32`（本地音乐）、`leanback required=false`、`LEANBACK_LAUNCHER` 双 intent-filter、USB host、Android Auto `<meta-data>` + `MEDIA_PLAY_FROM_SEARCH`（方案单独决策项）。

#### `app/src/main/java/com/lnh/nasmusictv/ui/screens/settings/PhotoWallSettingsSection.kt`
- **动作**：修改（阶段 2）。
- **改动**：移除 `if (!state.isTV) { ... }` 内的「图库」`SettingSwitch` + Android 14 部分权限「重新选择照片」`SettingActionButton`；移除 `showGalleryIntro` 对话框块与 `var showGalleryIntro`；`enabledSourcesText` 去掉「图库」标签。
- **Import 修复**：误删的 `remember` 已补回（`PhotoTransitionChips` 仍用）；`getValue/setValue/mutableStateOf/Dialog/DialogProperties/PhotoWallAccessPolicy/ConfirmDialog` 等确属无用的 import 已删。

#### `app/src/main/java/com/lnh/nasmusictv/backend/SearchAggregator.kt`
- **动作**：修改（阶段 B，§3.4 / §4B）。
- **改动**：
  - 构造函数移除 `private val isTVDevice: Boolean = false`；
  - NAS / 百度 / 本地三条 `isPinyinQuery && ... && isTVDevice` 去掉设备门；
  - `pinyinCache` 不再 `if (isTVDevice)` 包裹，恒生成；
  - `matchesBySearchTypePrecise` 移除 `isTVDevice` 形参与 `if (!isTVDevice) return false`；
  - 更新注释（「仅 TV 端启用拼音匹配」→「TV 是唯一完整客户端，拼音匹配为主路径」）。
- **兼容性**：`PinyinUtils` / `SongWithPinyin` 保留，拼音索引实现完整保留。

#### `app/src/main/java/com/lnh/nasmusictv/NasMusicApp.kt`
- **动作**：修改（阶段 B）。
- **改动**：删除 `val isTVDevice = packageManager.hasSystemFeature(...)` 局部及传给 `SearchAggregator` 的 `isTVDevice = isTVDevice` 实参（该局部仅用于此构造，删除后无悬挂引用）。

#### `app/src/test/java/com/lnh/nasmusictv/ui/viewmodel/ArtistExpansionGenerationGateTest.kt`
- **动作**：修改（阶段 B）。
- **改动**：`realAggregator()` 构造去掉 `isTVDevice = false` 实参（与新的无参签名对齐）。该测试测艺人展开门禁，不依赖拼音开关，移除实参不影响断言。

#### `app/src/main/java/com/lnh/nasmusictv/ui/screens/TextInputDialog.kt`
- **动作**：修改（阶段 D，§3.4）。
- **改动**：
  - 移除 `isTVDevice` 嗅探；
  - `var showSystemIme by remember { mutableStateOf(false) }`（默认 TV 自定义键盘，去掉 `!isTVDevice`）；
  - `val effectiveShowQrCode = showQrCode`（去掉 `if (isTVDevice) ... else false`，二维码即手机浏览器遥控入口，按调用方开关启用）；
  - 保留「中文输入」系统 IME 切换按钮、`LocalInputServer` 生命周期、`qrText` 回填、搜索历史建议、返回键盘逻辑；
  - 注释「仅 TV 启用」改为「TV 唯一完整客户端；手机经浏览器扫码遥控」。
- **Import 影响**：`Context` / `InputMethodManager` / `LocalSoftwareKeyboardController` / `BasicTextField` 等仍被保留的 IME 路径使用，无需删除。

#### `app/src/main/java/com/lnh/nasmusictv/util/NetworkUtils.kt`
- **动作**：修改（**反向遥控可达性修复**，与本轮 TV-only 收敛同批交付）。
- **为什么**：模拟器经 TAP 桥接与电脑同网段时，App 生成的二维码仍给出 `10.0.2.15`（模拟器 NAT 地址），手机扫码后连不上 —— 属反向遥控链路的可用性缺陷。
- **改动**：
  - `getLocalIpAddress()` 改为**跨全部在用接口**收集非回环 IPv4 并记录接口优先级（`eth` +2 / `wlan` +1 / `isUp` +8），再做**全局择优**（`ipScore`：`192.168.`=5 > `10.`/`172.`=4 > 公网=3 > `10.0.2.`=1 > `169.254.`=0），不再用 `firstOrNull()`；
  - 新增 `readLocalIpsFromFibTrie()`：读 `/proc/net/fib_trie`（world-readable）的 `host LOCAL` 条目，补回 **Java `NetworkInterface` 枚举不到的桥接地址**。
- **关键事实**：Java `NetworkInterface.getNetworkInterfaces()`（getifaddrs）**枚举不到 TAP 桥接的 `192.168.x`**（只看到 eth0 的 `10.0.2.15`），但内核路由表里有 —— 这是必须读 `fib_trie` 的根因。
- **验证**：logcat 实测 `getLocalIpAddress -> 192.168.100.90`（候选 `[10.0.2.15(10), 192.168.100.90(0)]`，按 ipScore 胜出）。
- **收尾**：临时 `VERIFY` 调试日志已全部移除；顺带修编译器告警（`.map { it.hostAddress }` → `.mapNotNull { it.hostAddress }`），`assembleDebug` 0 警告。

#### `app/src/main/java/com/lnh/nasmusictv/ui/screens/NowPlayingScreen.kt`
- **动作**：修改（死代码清理，与本轮 TV-only 收敛同批交付）。
- **为什么**：`val isPhoneLandscape = false` 是**恒假死变量**，其 8 处 `if (isPhoneLandscape)` 判定永远走 else 分支 —— 手机横屏布局早已下线，留着只会误导后续维护者以为存在双形态布局。
- **改动**：折叠全部 8 处恒假判定为其实际生效的分支（左右留白 `32.dp`、顶部 `40.dp`、底部 `maxOf(12.dp, insetBottom)`、`Arrangement.spacedBy(if (isImmersiveMode) 24.dp else 48.dp)`、`Alignment.CenterVertically`、`contentPaddingStart = 32.dp`、`LYRICS_TITLE_TOP_PAD`）。
- **等价性**：**纯等价重构**，运行行为零变化（死分支的取值即原 else 分支取值）。
- **校验**：全仓 grep `isPhoneLandscape` → 仅剩 1 处说明性注释。

#### `app/src/main/java/com/lnh/nasmusictv/ui/screens/MineScreen.kt`
- **动作**：修改（死代码清理，与本轮 TV-only 收敛同批交付）。
- **为什么**：`val isPhone = false` 恒假，其 `if (isPhone) { ... }` 包着**整块手机单列布局**（约 187 行）—— 代码注释自标「保留结构，Phase 5 清理」，本轮即 Phase 5。
- **改动**：删除恒假变量与整个手机单列分支，将 TV 双列布局（`Row { FavoritesPane / Spacer / PlaylistsPane }`）提升为 `Column` 直接子级并回退缩进；同时删除因删除该分支而失效的 `import com.lnh.nasmusictv.data.prefs.AppPreferences`。
- **等价性**：**纯等价重构**，运行行为零变化（死分支不可达）。
- **校验**：全仓 grep `isPhone` → 0 命中；文件括号配对校验 → 平衡；`assembleDebug` + `testDebugUnitTest` 全绿。

### 2.2 删除的生产文件（1）

#### `app/src/main/java/com/lnh/nasmusictv/backend/photo/MediaStorePhotoSource.kt`
- **动作**：删除（阶段 2）。
- **原因**：`class MediaStorePhotoSource : PhotoSource`（`kind = PhotoSourceKind.GALLERY`）是手机 MediaStore 照片来源实现，手机客户端下线后该来源在 TV 路径不再接线。
- **校验**：全仓 grep `MediaStorePhotoSource` → No matches；其 `GALLERY` 枚举值仍保留（旧备份兼容）。

#### `app/proguard-rules.pro`
- **动作**：修改（**预存 release 构建缺陷修复**，与本轮改造无因果关系）。
- **症状**：`:app:assembleRelease` 在 `:app:minifyReleaseWithR8` 失败 ——
  `ERROR: R8: Missing class org.slf4j.impl.StaticLoggerBinder`
  （referenced from: `void org.slf4j.LoggerFactory.bind()` and 3 other contexts）。
- **根因**：`eu.agno3.jcifs:jcifs-ng:2.1.10`（SMB 库）传递引入 `org.slf4j:slf4j-api:1.7.36`；
  slf4j-api 1.7.36 通过反射查找 `org.slf4j.impl.StaticLoggerBinder` 绑定实现类，
  而 Android 端本就没有 slf4j binding（业务代码零直接使用 slf4j，仅 jcifs-ng 内部用于日志），
  R8 视其为缺失类而中止构建。
- **为何此前未暴露**：CI 的 `build` job 在 push 路径会先因**签名 Secrets 缺失** fail-fast 退出，
  根本走不到 R8；本地又一直只跑 `assembleDebug`（不走 R8）。
- **预存性证明**：在**原始提交 `06f1c76`**（本轮改造之前）上用 worktree 复现 `assembleRelease`，
  报**完全相同**的 R8 错误 ⇒ 与本轮改动无因果关系。
- **改动**：新增一行 `-dontwarn org.slf4j.**`（按 R8 自己生成的 `missing_rules.txt` 建议），
  并在注释中写明根因、预存性、复现方式与后续注意事项。
- **验证**：本地 `assembleRelease` **BUILD SUCCESSFUL**，产出可安装的 release APK。

### 2.3 修改的文档文件（**以代码为基准重写**，见 §2.5）

#### `CHANGELOG.md`
- **动作**：修改 → **按代码重写**。移除与代码不符的条目（`LocalPhoneCompact` 笔误、`PHONE_UI_SCALE` / `LYRICS_RECOVER_SCALE` / `PHONE_TOUCH_TARGET` 等已不存在符号的引用）；改为按代码真实能力与真实版本（`versionName=1.0.0`）组织。

#### `README.md`
- **动作**：修改 → **按代码重写**。
- 顶部描述校正为「仅 Android TV，强制横屏；手机不是客户端而是遥控器」；
- 功能清单按代码核对：媒体源 **7 种**（原文档写「五种」，漏 WebDAV/SMB 与百度网盘的定位差异）、可视化 **25+ 主题**（原写「30+ 套」）、补入电台/天气电台、歌单导入、反向遥控端口表、Room/DataStore 持久化；
- 构建段落改为仓库真实命令与真实环境（JDK 17 路径、`ANDROID_HOME=D:\tools\Android\Sdk`、中文路径 `GRADLE_USER_HOME` 坑与 `subst G:` 绕法）；
- 移除与代码不符的「5 档音质（自动/128/192/320/无损 FLAC）」等未在代码中核实的表述。

#### `AGENTS.md`
- **动作**：修改 → **按代码重写**（原 33KB，多处与当前代码不符）。
- 校正：后端「Jellyfin 或 Navidrome」→「`BackendRegistry` 注册 7 种类型」；`UiMode`「`TV / PhoneLandscape` 两值」→「**仅 `TV`**」；ABI 补 `x86`；可视化主题数 → 25+；「虽有 `navigation-compose` 依赖但不用」→ **工程根本没有该依赖**；
- 路径校正：adb `C:\Users\hxzha\...` → `D:\tools\Android\Sdk\platform-tools\adb.exe`；wrapper `file:///C:/Users/hxzha/Downloads/...` → `file:///D:/tools/gradle-9.5.0-bin.zip`；JDK → `C:\Program Files\Java\jdk-17.0.21.0.2`；
- 单测数「184 类 / 2035 例」→ **241 文件 / 2427 例**；补入本机两个必踩坑（中文路径 `GRADLE_USER_HOME`、`--no-daemon`）与 `fileHashes.lock` 的正确解法（`--stop` 而非 `rm`）；
- 新增死依赖告警（`androidx.leanback` 声明但零 import）、`fib_trie` 取 IP 事实、`ScreenUiModeCoverageTest` 已反转为「竖屏残留扫描」等当前事实。

#### `docs/technical-overview.md`
- **动作**：修改 → **按代码重写**（原 1.4MB，含大量历史 `LocalPhoneCompat`/`isTVDevice` 叙述）。
- 重写为 14 节精炼架构文档：身份与平台、UI 架构（含焦点视觉约定）、模块与目录、后端与数据源、文件/网络访问、播放、歌词、可视化、照片墙、反向遥控（含 `getLocalIpAddress` 实测要点）、数据层、构建与依赖、测试、已知约束。

#### `docs/conventions-adaptive-ui.md`
- **动作**：修改 → **按代码重写**（原整篇围绕已下线的手机竖屏 `UiMode`/`PHONE_UI_SCALE` 体系）。
- 改为「TV UI 维护约定」：现状（TV-only）+ 残留 helper 的**真实语义表** + 焦点视觉（判据/缓存/误判面/焦点环顺序/`onFocusChanged`）+ 内容色契约 + 尺寸与对话框 + 新增组件检查清单 + 门禁清单（逐个核实测试真实存在）+ 验证手段。
- ⚠️ 修正一处**方向性错误**：原文档称 `ScreenUiModeCoverageTest` 是「新增 Screen 必须有竖屏分支」的覆盖门禁；实际该测试已**反转为「竖屏残留扫描」**（main 源集再出现竖屏标识符即判红）。

#### `docs/phone-client-cleanup-checklist.md`
- **动作**：修改。顶部新增「执行状态（源码审计版方案，2026-11-01）」块；将原「⑩ 照片墙仅手机来源 · 暂不处理」改为「已处理（源码审计版方案阶段 2）」。

### 2.4 新增文档（2）

#### `docs/changes/2026-11-01-tv-only-phone-remote-cleanup.md`
- 本文件。

#### `docs/changes/2026-11-01-tv-only-phone-remote-cleanup.json`
- 机器可读结构化清单（同源本文件），含 `change_id`、基线、平台范围、文件增删改动作、原因、改动项与验证状态。

### 2.5 文档重写专项（以代码为唯一基准）

用户要求「**以代码为基准，不看文档，重新编写各项文档**」。执行方式：

1. **事实来源**：全部取自源码与构建文件（`app/build.gradle.kts`、`AndroidManifest.xml`、`settings.gradle.kts`、全部 `.kt`/`.xml`），并由代码侧独立复核关键硬事实（AGP/Kotlin 版本、SDK 级别、依赖清单、`UiMode` 实际枚举值、helper 实际实现体、门禁测试实际存在性）。
2. **重写范围**：`README.md`、`CHANGELOG.md`、`AGENTS.md`、`docs/technical-overview.md`、`docs/conventions-adaptive-ui.md`，以及本变更报告与 `.json`。
3. **`docs/archive/` 处理**：归档是**历史方案快照**，逐份按当前代码改写会抹掉历史脉络、且与事实不符（它们记录的是「当时设计意图」）。因此**保留归档原文不改写**，改为生成**代码对账索引**（见 `docs/archive/README.md`），逐份标注该方案描述的功能**当前是否已实现 / 部分实现 / 未实现 / 已废弃**，供追溯查询。
4. **`docs/articles/`**：对外推广文章（知乎稿），非工程描述，不在本次重写范围。

## 3. 文件增删改统计

| 动作 | 数量 | 说明 |
|---|---:|---|
| 新增 | 2 | 2 个变更文档（`docs/changes/2026-11-01-*`） |
| 修改 | 20 | 13 个生产/模型 Kotlin（含 `NetworkUtils.kt`）+ `AndroidManifest.xml` + `CHANGELOG.md` + `README.md` + `AGENTS.md` + `docs/technical-overview.md` + `docs/conventions-adaptive-ui.md` + `docs/phone-client-cleanup-checklist.md` |
| 删除 | 1 | 生产源码 `MediaStorePhotoSource.kt` |
| 新增依赖 | 0 | 无 Gradle 改动 |
| 遥控服务改动 | 0 | 手机浏览器遥控相关文件均未删除（`NetworkUtils.kt` 为其依赖的 IP 取值逻辑，行为增强不删服务） |

## 4. 验证记录

### 4.1 静态核对（grep）
- **文档 §7.1 验收 grep**：`UiMode\.PhoneLandscape|LocalPhoneCompact|PHONE_UI_SCALE|LYRICS_RECOVER_SCALE|PHONE_TOUCH_TARGET` 在 `app/src/main/java` + `app/src/test/java` → **No matches found**（阶段 1 验收通过）。
- **删除干净度**：`MediaStorePhotoSource` 全仓 grep → **No matches found**。
- **阶段 B 验收**：`isTVDevice` 在 `SearchAggregator.kt` / `NasMusicApp.kt` / `ArtistExpansionGenerationGateTest.kt` 均 **No matches found**。
- **阶段 D 验收**：`isTVDevice` 在 `TextInputDialog.kt` → **No matches found**。
- **遥控服务在线**：`RemoteControlServer` 在 `MainViewModel.kt:200` 实例化、`LocalInputServer` 在 `TextInputDialog.kt:148` 实例化；`BackupTransferServer` / `PlaylistUploadServer` / `ServerConfigTransferServer` / `ModelTransferServer` / `LocalServerAuth` / `QrCodeGenerator` / `NetworkUtils` / `SmbHttpStreamServer` 文件均存在。

### 4.2 编译 / 单测验证（最终状态）
- **`assembleDebug`**：**BUILD SUCCESSFUL**，**零警告**（最后一次为清理 `NetworkUtils` 调试日志后的干净构建，57s）。
- **`testDebugUnitTest`**：**2427 测试 / 0 失败 / 0 错误 / 0 跳过**（BUILD SUCCESSFUL）。
  - 最初的 **288 失败**已全部收掉：根因是一批 `Gate/Contract/Coverage` 与 `visualizer.renderers.*` 契约测试从磁盘读自身 Kotlin 源码时按**旧包名 `com/nasmusic/tv/`** 拼源码根，而真实包名是 `com/lnh/nasmusictv/`（59 个测试文件 / 143 处硬编码）。**本改造引入的回归始终为 0**。
  - 剩余 2 个与旧包名无关的失败亦已收掉（均为测试侧脆性/门禁漂移，生产逻辑无需改）：
    1. `NetworkMusicManagerTest` 两个取消测试 —— 原 `assertTrue(actual === cancellation)` 引用相等断言过脆（生产走 `withContext(Dispatchers.IO)`，该边界会重建 `CancellationException` 实例；生产 `catch(CancellationException){ throw e }` 原样抛，取消确实在传播）。改为断言「捕获到 CancellationException 且已传播」+ 维持 fallback 计数断言。
    2. `VisualizerQualityWiringTest`「画质文案必须中英双份」—— 门禁写死裸 key `settings_visualizer_quality`（从未创建）。改为取 `values/strings.xml` 下全部 `settings_visualizer_quality*` key，逐一校验 `values-en/strings.xml` 也有英文 counterpart。
- **运行前提（本机环境坑，记录备查）**：`C:\Users\内涵哥\.gradle` 含中文路径，导致 Gradle 9.5 测试 worker 的 `@argfile` classpath 漏掉 `gradle-worker-main` 引导 jar（`ClassNotFoundException: GradleWorkerMain`）。修复：用 `subst G: C:\Users\内涵哥\.gradle` 映射为 ASCII 盘符，并以 `GRADLE_USER_HOME=G:\` 跑测试。该问题不影响 `assembleDebug`（Kotlin 编译用 `-Pin-process` 不 fork worker）。后台跑测试须加 `--no-daemon --continue --max-workers=2 -Dorg.gradle.jvmargs=-Xmx4g`，否则会被「stop command received」中断。

### 4.3 反向遥控端到端（模拟器）
- `tv5`（Android TV / android-22）模拟器以 `-net-tap tap0` 启动，与电脑桥接为**同网段**（模拟器 `192.168.100.90/24`、电脑 `192.168.100.66/24`，同属 `192.168.100.0/24`）；模拟器→电脑 `ping` 4/4 成功（~1.3ms）。
- 扫码二维码中的 IP 修复前后对比：
  - 修复前：`10.0.2.15`（NAT 地址，局域网不可达）❌
  - 修复后：`192.168.100.90`（同网段可达）✅ —— logcat 实测 `VERIFY fib_trie_ips=[10.0.2.15, 192.168.100.90]` → `VERIFY getLocalIpAddress -> 192.168.100.90`（验证后已移除该临时日志）。

### 4.4 尚未执行的验证
- 真机 / 物理电视盒子装机联调、扫码遥控端到端交互回归（本次为 `tv5` 模拟器）。
- `docs/archive/` 方案中未落地项的功能验证（以对账索引为准）。

## 5. 建议在 Android Studio / 电视上验证

在原开发 Windows 环境中打开工程，执行：
```powershell
.\gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
.\gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
.\gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:lintDebug
```
电视设备重点验证：
- 搜索框输入拼音首字母（如 `zjl` → 周杰伦）能命中（拼音主路径）。
- 搜索页二维码扫码 → 手机浏览器输入文字能推回 TV 输入框。
- 设置 → 照片墙来源只剩「外接存储 / Jellyfin」，无「图库」开关；旧含 `gallery:<id>` 的备份恢复不崩溃。
- 播放页音量条（TV 单面）、可视化退出走返回键正常。
- 遥控 / 扫码传文件（备份、歌单、模型）链路正常。

## 6. 残留风险与后续检查点（重要）

1. **`VisualizerStage` 未用形参**：`onExit` / `isTV` 现函数体内未使用，仅为警告（Kotlin 不对未用形参报错），编译安全；需在 SDK 环境确认无调用点依赖其副作用后再考虑删除。
2. **图库相关「保守保留」项**（阶段 2 故意未深删，需 SDK 环境再审）：`VisualizerViewModel` 的 `GALLERY_DENIED/GALLERY_REVOKED/GALLERY_PARTIAL` 通知枚举 + `applyGalleryRollback` 等、`PhotoWallAccessPolicy` 的 gallery 函数、`PermissionHelper` 照片 API、`AppPreferences/AppSettings` 的 `photoWallGalleryEnabled` 字段（保留为读但忽略，兼容旧备份）。这些是为了不让旧备份 / `gallery:<id>` 缓存解码崩溃而保留；若要彻底删除，必须在 SDK 环境重编译并同步更新 `PhotoWallAccessPolicyTest` / `PhotoPermissionStateTest` / `PhotoWallPrefsTest` / `BackupGsonTest` 等测试。
3. **`<600dp` 窄窗 TV 支持**：`portraitTouchTarget` / `responsiveDialogSize` / `adaptiveColumnsOf(phonePortrait)` 形参按方案 §5.1 保留（窄窗 TV 仍需），未内联；属有意保留，非遗漏。
4. **`PinyinMatcher.kt` 死代码**：全仓无任何调用（仅自身定义），未纳入本方案改造范围（非 `SearchAggregator` 路径），保持现状。
5. **清单分发决策**：`leanback required=false` 与 Android Auto 相关声明按方案「单独决策」项保留；是否改为 `leanback required=true`、是否移除 Android Auto，需你最终拍板（纯分发策略，无代码风险）。
6. **生成类文档**：✅ **已解决** —— `docs/conventions-adaptive-ui.md` 与 `docs/technical-overview.md` 已于本次**按代码重写**（见 §2.3），不再含历史 `LocalPhoneCompact` / `isTVDevice` 竖屏叙述；`README.md` / `CHANGELOG.md` / `AGENTS.md` 同步按代码重写。

## 7. AI 检索与后续维护约定

1. 先读本文件 `change_id` / `baseline` / `files` / `verification`，再读列出的源码。
2. 不要把「未执行」的 Gradle / Robolectric / 硬件联调描述成通过（见 §4）。
3. 后续若深删图库残留（见 §6.2），务必先在 SDK 环境重编译并同步更新 gallery 相关测试。
4. 遥控服务（§2.1 / §5 未改动项）是手机浏览器遥控的承载，**禁止误删** `RemoteControlServer` / `LocalInputServer` / `*TransferServer` / `LocalServerAuth` / `QrCodeGenerator` / `NetworkUtils` / `SmbHttpStreamServer` / `RemoteControlHtml`。
