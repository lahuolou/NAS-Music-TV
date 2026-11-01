# 技术总览（Technical Overview）

> 以源码为唯一基准。任何与本文不符的 `docs/archive/*` 历史方案均为「当时设计意图」，不代表当前实现。
> 包根：`com.lnh.nasmusictv`。关键事实以 `app/build.gradle.kts`、`AndroidManifest.xml` 为准。

---

## 1. 身份与平台

| 项 | 值 | 来源 |
|----|----|------|
| 包名 / namespace | `com.lnh.nasmusictv` | `build.gradle.kts:42` |
| `minSdk` / `targetSdk` / `compileSdk` | `22` / `34` / `34` | `build.gradle.kts:43-48` |
| `versionName` / `versionCode` | `1.0.0` / `1` | `build.gradle.kts:8,49-50` |
| ABI | `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` | `build.gradle.kts:52-54` |
| 主 Activity | `.ui.MainActivity`（`LEANBACK_LAUNCHER` + `LAUNCHER`） | `AndroidManifest.xml:127` |
| 屏幕方向 | `sensorLandscape`（**强制横屏，无竖屏**） | `AndroidManifest.xml:131` |
| Application | `.NasMusicApp`（手动 DI 容器，**无 Hilt/Dagger**） | `AndroidManifest.xml:88` |

**形态因子**：`ui/theme/UiMode.kt` 中 `enum class UiMode { TV }` —— **仅 TV**。
`LocalUiMode` 恒提供 `UiMode.TV`，`deriveUiMode()` 恒返回 `TV`。
`AndroidManifest` 把 `leanback` / `screen.portrait` 标为 `required=false`（为兼容非认证盒子/上架），
但**实际是纯 TV 横屏**：竖屏代码已删除、`sensorLandscape` 强制、手机 system-bar 逻辑已移除。

> ⚠️ **死依赖**：`androidx.leanback:leanback:1.0.0` 在 `build.gradle.kts:231` 声明，但**全源码零 `androidx.leanback` import**（UI 是 Compose TV）。保留原因疑似间接提供 `fragment`，但 `fragment-ktx:1.6.2` 已显式声明，leanback 应可移除（需评估移除后的传递依赖）。

---

## 2. UI 架构

- **100% Jetpack Compose for TV**：`androidx.tv:tv-foundation/tv-material:1.0.0-alpha10` + `compose-bom:2024.02.00`
- **无 Leanback Presenter/Fragment**、**无 Jetpack Navigation**（`androidx.navigation` 零 import）
- **导航机制**：`data/model/Screen.kt` 定义 14 个枚举页，`MainViewModel.navVM.currentScreen`（`StateFlow`）驱动 `AppRoot.kt` 的 `when(currentScreen)` 直接组合分支。叠加层（可视化/K歌/MTV）由各自 ViewModel 的 `StateFlow` 控制全屏覆盖。
- **DI**：`NasMusicApp` 持有全部单例（播放器、各 ViewModel、`BackendRegistry`、`NasMusicApp` 暴露的仓库），无框架。

### 2.1 主要界面（`ui/screens/`）
首页 `Home` · 正在播放 `NowPlaying` · 音乐库 `Library`（发现/搜索/电台/Jamendo/浏览）· 我的 `Mine` · 队列 `Queue` · 设置 `Settings` · 连接 `ServerConnect` · 专辑/艺人详情 · 均衡器 `Equalizer` · 歌单管理 `PlaylistManagement` · 网盘 `Netdisk` · 天气电台 `WeatherRadio` · 统计 `PlayStats`。
每个 Screen 对应一个 `ui/components/branches/*Branch` 组合函数。

### 2.2 焦点视觉（TV 关键约定）
- 焦点相关视觉**一律**用 `shouldShowFocusVisuals()`（`ui/components/FocusIndicator.kt:223`）判断，**不是** `isTVDevice()`。
  `isTVDevice()`（`FocusableSurface.kt:69`）只靠 `hasSystemFeature(leanback|type.television)`，在非认证盒子上常误判；焦点判据改为「用户是否用按键导航」（无触摸屏 / 电视 feature / 存在 `SOURCE_DPAD` 设备 / 收到过方向键，任一成立），并由 `object by lazy` 缓存（避免 135+ 调用点各查一次 binder）。
- `activeFocus = isFocused && shouldShowFocusVisuals()`，缩放/焦点环/容器色全走它；手机仅保留 `isPressed` 瞬时反馈。
- `FocusableSurface` 必须**同时**下发 `LocalFocusableContentColor` 与 tv-material3 的 `LocalContentColor`（后者默认 `Color.Black`，漏发会黑字压深底看不见）。
- 自实现焦点环必须画在 `.background(...)` **之后**（链尾），用 `focusRing(shape, color, visible)`。
- `onFocusChanged { }` 的 lambda 捕获旧值；需立即生效的动画必须读 `state.hasFocus && tvDevice`，不能读外层派生的 `activeFocus`。

---

## 3. 模块与目录

```
backend/        数据源
  impl/         Jellyfin / Navidrome / Subsonic / Feiniu / Daoliyu 适配器
  network/      Meting / Netease / Jamendo / Bilibili-MV / Baidu 网盘 / 聚合路由
  local/        本地音乐扫描 + Room + 封面提取
  files/        SMB / WebDAV / NetworkFolder 抽象 + SMB loopback 桥
  download/     离线下载（串行队列 / 迁移 / 标签写入）
  export/       SAF 导出
  photo/        照片墙 + YuNet 人脸检测 + 来源聚合
  playlist/     M3U / 网易云 / JSON 歌单解析
  BackendAdapter / BackendRegistry / BackendAuthHeaders
player/         Media3 播放状态机 / PlaybackService / 均衡器·频谱 / 人声分离
lyrics/         LRC/逐字解析、歌词获取优先级
visualizer/     25+ 渲染主题 + 频谱数据（SpectrumRepository）
ui/             screens / components / viewmodel / theme
net/            6 个 NanoHTTPD 服务器 + SMB 回环桥
data/           model(Screen) / prefs(DataStore) / model
util/           NetworkUtils 等
NasMusicApp.kt  应用 + DI
```

---

## 4. 后端与数据源

`BackendAdapter`（`backend/BackendAdapter.kt:16`）统一封装 albums/artists/songs/playlists/favorites/genres/lyrics/stream/cover，以及 `buildAuthenticatedUrl`（照片墙）、`streamHeaders`（认证头注入）、`lastErrorDetail`（用户可读诊断）。

`BackendRegistry`（`backend/BackendRegistry.kt:32`）注册 7 种类型：`Jellyfin / Navidrome / Subsonic / Daoliyu / Feiniu / WebDAV / SMB`。创建时按类型实例化对应适配器。

认证头经 `BackendAuthHeaders` 实时读取，由 `BaiduHttpDataSourceFactory` 拦截器注入 ExoPlayer 播放与 Coil 封面两条链路。

### 4.1 各后端
| 类型 | 适配器 | 协议 |
|------|--------|------|
| Jellyfin | `JellyfinAdapter` | Jellyfin REST `/Items`、`/Playlists` |
| Navidrome | `NavidromeAdapter` | Subsonic 兼容 API（无状态认证） |
| Subsonic | `SubsonicAdapter` | Subsonic `/rest/`，Token+Salt 认证 |
| 岛鲤鱼 | `DaoliyuAdapter` | 自定义 REST（`/api/auth/login` 等，端点为反推，多处标 INFERRED） |
| 飞牛 | `FeiniuAdapter` | fnOS music API v1（默认 5666/HTTPS 5667，访问码校验挂站点根） |
| WebDAV | `WebDavAdapter` | WebDAV（`WebDavClient`，Basic Auth） |
| SMB | `SmbAdapter` | SMB2/SMB3（`SmbClient` via jcifs-ng）+ `SmbHttpStreamServer` 回环桥 |

### 4.2 网络音乐（非 NAS，`NetworkMusicManager` 多源路由）
- `MetingApiService`：Meting 聚合（网易/QQ/酷狗…），302 直链 + 多码率 + `HttpPlayabilityProbe`
- `NeteaseOpenApi`：网易云原生免鉴权接口（失败回落 Meting）
- `JamendoService`：Jamendo CC 独立音乐（client_id）
- `baidu/`：`BaiduNetdiskService`（实现 `NetworkMusicService`，非 `BackendAdapter`）：OAuth2 + `BaiduStreamFactory` 流式播放（dlink 8h 过期，播放时解析）+ `BaiduLyricsProvider` / `BaiduCoverProvider` / `BaiduMvFileService` / `BaiduFileIndexCache`
- `mv/`：`BilibiliMvService`（search→view→playurl + Referer 防盗链）+ `MvSearchManager` + `MvPersistentCache`
- 聚合入口：`SearchAggregator`（拼音匹配提升为主路径）、`NetworkArtistExpansion`、`PlayabilityProbe`

### 4.3 本地音乐（`backend/local/`）
`MusicScanner` → `LocalMusicRepository` → `LocalMusicDatabase`（Room，`local_music.db` v3，`fallbackToDestructiveMigration`）。封面 `EmbeddedCoverExtractor` / `LocalCoverExtractor` / `AlbumCoverResolver` / `ArtistCoverResolver` / `ItunesCoverSearcher` / `CoverUrlPersistentCache`。`StorageMonitor` 监听 USB/SD 插拔。

---

## 5. 文件 / 网络访问（`backend/files/`）

- `NetworkFolderAdapter`：文件型 NAS 共享扫描器（深度≤32、条目≤80,000、并行标签读≤4；音频扩展名 17 种）
- `SmbAdapter` → `SmbClient`（**jcifs-ng 2.1.10，SMB2/SMB3，SMB1 禁用**）+ `SmbHttpStreamServer`（NanoHTTPD 回环 `127.0.0.1:动态端口`，把 SMB 文件暴露给 Media3 播放）
- `WebDavAdapter` → `WebDavClient`（Basic Auth）
- 百度网盘流式：`BaiduStreamFactory`

---

## 6. 播放（`player/`）

- **`PlayerManager`**：核心状态机（ExoPlayer 生命周期、进度轮询、队列、播放模式），持有 `PlayerEqualizer`（均衡器+频谱）、`CrossfadeController`（双实例交叉淡入）、`SleepTimerController`
- **`PlaybackService`**：Media3 `MediaLibraryService` + `MediaSession`，前台 `MediaStyle` 通知，Android Auto 媒体树（`MediaLibraryTree`），`MEDIA_PLAY_FROM_SEARCH` 语音分支（`onSetMediaItems` 的 searchQuery 分支经 `resolveVoiceSearch()` 起播）
- **人声分离 / K 歌**：`DemucsSeparator`（**HT-Demucs FT ONNX**，模型 `htdemucs_ft_vocals.onnx` FP16 ~166MB，4 stems，经 `ai.onnxruntime`）、`HqSeparationOrchestrator`、`ModelDownloadManager`（设置页下载，默认 AWS CDN 可改）、`AccompanimentCache` 等
- **离线下载**（`backend/download/`）：`SongDownloadManager`（串行队列、512KB 进度、重试≤3、崩溃恢复清 .tmp/.part）、`DownloadRepository` + `DownloadDatabase`（`downloads.db` v3，**真实迁移 M1_2/M2_3**）、`MediaTagWriter`（jaudiotagger 写 ID3/封面/歌词）、`QualityProbe`（逐档探测多码率）、`AutoDownloadController`、`StorageGuard`
- **导出**（`backend/export/`）：`ExportCoordinator` + `SongExporter` 经 SAF `OpenDocumentTree` 导出到外接设备，维护 `ExportRecordEntity` 增量判定

---

## 7. 歌词（`lyrics/`）

`LyricsManager` 获取优先级：① 本地同目录 `.lrc` 侧车 → ② 内嵌 ID3 歌词 → ③ NAS 后端 API 歌词 → ④ 持久缓存 → ⑤ 网络模糊匹配。
`LrcParser` 支持**标准 LRC + 增强 LRC**，并解析**内嵌逐字时间戳**（karaoke `<mm:ss.xx>` 正则），生成 `WordTimestamp` 供 `KaraokeLyricsView` / `KaraokePlaybackScreen` 渲染逐字高亮。
网络歌词：酷狗 krc base64（`LyricsNetworkProvider`）、网易云端点（可配置基址）。
**未实现** QRC / kvly 专用解析（grep 零命中）。

---

## 8. 可视化（`visualizer/`）

- 渲染：**以 Compose `Canvas` 2D / 过程化绘制为主**；两例 WebView（`WorldGlobeRenderer` three-globe、`MoonlitRenderer` 解码 `assets/globe/moon.jpg`）。**无 OpenGL/Shader，无 ONNX/karaoke 模型用于可视化**。
- 音频源：`SpectrumRepository`（唯一 `AudioFrame` 写入方，由 `PlayerEqualizer` 注入）→ `SpectrumAnalyzer` / `BeatDetector` / `SectionEnergyTracker` / `PeakHoldTracker`。
- 主题注册（`data/model/AppSettings.kt:125` 的 `VisualizerTheme` 枚举，共 **26** 套；新增 = 加 Renderer + factory 分支）：CIRCULAR_RING、LIQUID_GRID、BEAT_FIREWORK、LIQUID_RIPPLE、MATRIX_RAIN、CONSTELLATION、MILKDROP_FEEDBACK、LYRICS_DOT_MATRIX、ECG_WAVE、HYPNOTIC_FUNCTION、ORBITAL_RINGS、RADAR_GRID、CONCENTRIC_GEARS、LIGHT_BEAMS、MOLECULE、VINTAGE_TV、PHOTO_WALL、DNA、WORLD、STAR_TRAILS、SEASIDE、MOONLIT、TRIPOD_FOG、SNOW_CEDAR、BIRDS_FLOCK、DESERT_SMOKE
- 画质分级 `VisualQuality`（HIGH/MEDIUM/LOW）按 `supports(theme)` 过滤：`BASIC` 全档可选；`ADV` 需「不消耗粒子预算 **或** `maxParticles > 0`」（2026-10-01 裁决：不再用 `maxParticles > 0` 当 ADV 的代理条件）；`ULTRA` 仅 `allowFramebuffer` 为真的档（= HIGH）提供。`PHOTO_WALL` 在三来源全关时被 `selectable(photoWallAvailable)` 过滤掉。

---

## 9. 照片墙（`backend/photo/`）

已接入 UI（作为可视化 `PHOTO_WALL` 主题 + 设置分区），**非孤儿**。
- 人脸检测：`YuNetFaceDetector`（OpenCV Zoo `yunet_face.onnx` 经 ONNX Runtime，阈值 0.6）+ `FaceScanManager`；`PhotoFaceDatabase`（`photo_face.db` v1，**懒加载**）。
- 来源（`PhotoSourceAggregator`）：**EXTERNAL**（USB/SD/SAF，TV 默认开）、**JELLYFIN**（`JellyfinPhotoSource` 经 `buildAuthenticatedUrl`）、**GALLERY**（手机 MediaStore，随手机客户端下线已失效，默认关，TV 上形同虚设）。
- 转场：`PhotoTransitionId` 多种（CROSSFADE/IRIS/TILES 等）+ Ken Burns / 音频反应。

---

## 10. 反向遥控（`net/`）

6 个 NanoHTTPD 内嵌 HTTP 服务器（端口 18080–18085）+ SMB 回环桥：

| 端口 | 类 | 用途 | 启动时机 |
|------|------|------|----------|
| 18080 | `LocalInputServer` | 手机扫码向 TV 输入框推送文字 | `TextInputDialog` 打开时 |
| 18081 | `BackupTransferServer` | 备份/恢复传输 | 按需 |
| **18082** | `RemoteControlServer` | 手机/PC 浏览器遥控 | **进入 K 歌/MTV 模式时**（MainViewModel 启动） |
| 18083 | `ModelTransferServer` | 人声分离模型传输 | 按需 |
| 18084 | `PlaylistUploadServer` | 歌单上传 | 按需 |
| 18085 | `ServerConfigTransferServer` | 服务器配置传输 | 按需 |

- 二维码：`com.google.zxing:core:3.5.3`。本地 IP：`util/NetworkUtils.getLocalIpAddress()`。
- 安全：`LocalServerAuth` 一次性 token 写入二维码 URL（`?t=...`），服务重启即换（原绑 `0.0.0.0` 无鉴权已改）。
- `RemoteControlHtml.kt`：遥控页 HTML；`HttpRequestBody` / `MultipartBoundaryStreamer` 绕过 NanoHTTPD 2.3.1 不解析 body 的限制。

### 10.1 `NetworkUtils.getLocalIpAddress()` 多 IP 择优（实测要点）
模拟器 TAP 桥接场景下，Java `NetworkInterface.getNetworkInterfaces()`（getifaddrs）**枚举不到**桥接的 `192.168.x`（只看到 NAT `10.0.2.15`），但内核 `/proc/net/fib_trie`（world-readable）里有。因此该函数在跨接口收集 NetworkInterface 地址后，再读 `fib_trie` 的 `host LOCAL` 条目补回桥接 IP，最后按 `ipScore`（192.168=5 优先于 10.0.2.=1）全局择优。确保二维码展示同网段地址，手机/电脑才能直连。

---

## 11. 数据层

**3 个 Room 库**（`@Database`）：
1. `LocalMusicDatabase` — `local_music.db` v3，单表 `LocalSongEntity`，`fallbackToDestructiveMigration(true)`，`exportSchema=true`
2. `DownloadDatabase` — `downloads.db` v3，`DownloadSongEntity` + `ExportRecordEntity`，**真实迁移 M1_2/M2_3**（`exportSchema=true`，含 `{1,2,3}.json` 供 `MigrationTestHelper`）
3. `PhotoFaceDatabase` — `photo_face.db` v1，`PhotoFaceEntity`，**懒加载**

**Preferences**：`androidx.datastore:datastore-preferences:1.1.1`（`data/prefs/AppPreferences.kt`）。门面子域：server/player/lyrics/baidu/network/photoWall/history/playlist。独立 `QualityOverrides`（单曲音质覆盖，500 LRU）、`CryptoUtils` AES-256 派生口令（加密百度 refresh_token，口令来自 `BuildConfig.CRYPTO_PASSPHRASE`）。**无 SharedPreferences 新写入**。

---

## 12. 构建与依赖

- **AGP `9.2.1`**、**Kotlin Compose `2.3.10`**、**KSP `2.3.10`**（root `build.gradle.kts`）
- `jvmTarget=17`；`freeCompilerArgs += -opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api`
- `buildFeatures { compose=true; buildConfig=true }`；APK 命名 `NAS-Music-TV-${variant}-v${ver}.apk`
- `release` 打包前 guard 校验 `CRYPTO_PASSPHRASE`，缺失 fail-fast

**关键依赖**（app `build.gradle.kts`）：
- Media3 `media3-exoplayer/session/ui/datasource-okhttp:1.2.1`
- Compose TV `tv-foundation/tv-material:1.0.0-alpha10`、`ui`、`activity-compose:1.8.2`、`fragment-ktx:1.6.2`、`lifecycle:2.7.0`
- 网络 `okhttp:4.12.0` + `logging-interceptor`（**无 Retrofit**）
- 图片 `coil-compose:2.5.0`（**无 Glide**）、`palette-ktx:1.0.0`
- 数据 `datastore-preferences:1.1.1`、`room:2.7.1`、`gson:2.10.1`
- 本地服务 `nanohttpd:2.3.1`、`jcifs-ng:2.1.10`、`tinypinyin:2.0.3`、`zxing:core:3.5.3`
- AI `onnxruntime-android:1.17.1`（人声分离 + 人脸检测；版本被 minSdk22 / 16KB 页对齐锁定，详见 `build.gradle.kts:233-263`）、`jaudiotagger:3.0.1`（LGPL）
- **Leanback `1.0.0` 声明但零 import（死依赖）**
- **无 WorkManager、无 Lottie**

> ⚠️ **ONNX 版本陷阱**：`onnxruntime-android:1.17.1` 的 native 库 `p_align=4096` 未满足 16KB 页要求（lint `Aligned16KB` Warning，非 Error，targetSdk 34 未触发）。升 `targetSdk 35` 或上架前必须处理：升 minSdk 24 + 换 1.29.0，或自编 ONNX 加 `-Wl,-z,max-page-size=16384`。

---

## 13. 测试

- 位置 `app/src/test/`，**241 个 Kotlin 测试文件**；`app/src/androidTest` **0 文件**（无 Instrumentation 测试）
- 框架 JUnit4 + Robolectric 4.11.1 + Mockito + mockwebserver + room-testing
- `NasMusicApp` 在 Robolectric 下真跑 `onCreate` 并读写 Room——既定测试模式
- 大量 Robolectric 测试依赖 `@Config(application = NasMusicApp::class)`
- 注意：测试包名 `com.lnh.nasmusictv` 与源码一致；某些测试从磁盘读自身源码做契约校验（按 `com/lnh/nasmusictv/` 拼路径，旧 `com/nasmusic/tv/` 路径会导致 FileNotFoundException——已修复）
---

## 14. 已知约束 / 易错点（给维护者）

1. **测试 worker 在含中文路径的 `GRADLE_USER_HOME` 下崩溃**：用 `subst G: C:\Users\内涵哥\.gradle` + `GRADLE_USER_HOME=G:\` 绕开；不影响 `assembleDebug`。
2. **Leanback 是死依赖**（见 §1）。
3. **`portraitTouchTarget` / `responsiveDialogSize` 已塌缩为恒等**（竖屏下线）；`PHONE_UI_SCALE` 数学失效；TV 热区下限用 `TV_TOUCH_TARGET=56dp`。
4. **`isTVDevice()` 仅用于布局/默认值判定，焦点视觉必须用 `shouldShowFocusVisuals()`**。
5. **反向遥控端口 18080–18085**：`RemoteControlServer`(18082) 仅 K 歌/MTV 模式启动；非 App 启动常驻。
6. **百度网盘沙盒目录**：第三方应用只能访问 `/apps/NAS-Music-TV/`（百度开放平台策略），音乐须放该目录下。
7. **照片墙 GALLERY 源已失效**（手机下线）；TV 照片墙靠 EXTERNAL + JELLYFIN。

---

## 15. 修改记录

> 记录**经测试验证**的功能变更与问题修复。
> 每次代码修改后同步更新 `CHANGELOG.md` 与本节。
> 历史变更流水（约 1.6 万行，§10.1–§10.282）已归档至 `docs/archive/technical-overview-changelog.md`。

### 15.1 2026-11-01 · TV 单平台收敛 + 手机仅作浏览器遥控

**变更范围**：平台形态收敛为纯 TV；删除手机独立 UI 与图库照片源；保留手机浏览器作为反向遥控 / 输入终端。

| 项 | 内容 |
|----|------|
| 包名 / namespace | `com.lnh.nasmusictv`（稳定版） |
| 平台形态 | `UiMode` 枚举仅保留 `TV`；`LocalUiMode` / `deriveUiMode()` 恒为 TV |
| 删除文件 | `app/src/main/java/com/lnh/nasmusictv/backend/photo/MediaStorePhotoSource.kt`（图库照片源） |
| 遥控 | `RemoteControlServer`(18082) / `LocalInputServer`(18080) 保留；手机通过扫码在浏览器中遥控 |
| 视觉适配 | `portraitTouchTarget` / `responsiveDialogSize` 塌缩为恒等；`adaptiveColumns` 保留按宽度分档 |

**问题修复**：

1. **反向遥控二维码显示 NAT 地址**：Android 的 `NetworkInterface`（getifaddrs）**无法枚举 TAP / 桥接网卡地址**，
   导致 `getLocalIpAddress()` 只返回模拟器 NAT 地址 `10.0.2.15`，手机扫码后无法直连。
   **修复**：新增 `/proc/net/fib_trie` 兜底读取本机 IPv4，与 `NetworkInterface` 结果合并后按
   「私网段优先 + 接口状态」评分择优选择。
2. **288 个单元测试预存失败**：一批契约 / 门禁测试从磁盘读取自身源码做校验，但源码路径硬编码旧包名
   `com/nasmusic/tv/`（实际为 `com/lnh/nasmusictv/`），集体 `FileNotFoundException`。
   **修复**：59 个测试文件、143 处路径全部更正为新包名。
3. **`NetworkMusicManagerTest` 取消断言失效**：`withContext(Dispatchers.IO)` 会重建 `CancellationException` 实例，
   跨边界的 `===` 引用相等断言必然失败。**修复**：改为类型 / 传播断言（生产代码 `catch(CancellationException){ throw e }` 正确，不改）。
4. **`VisualizerQualityWiringTest` 门禁过期**：断言了从未存在的 `settings_visualizer_quality` 键。
   **修复**：改为读取 `values/strings.xml` 中全部 `settings_visualizer_quality*` 键并逐一校验 `values-en/` 有英文对应项。

**验证**：

- `assembleDebug`：**BUILD SUCCESSFUL**（0 error）
- `testDebugUnitTest`：**2427 / 2427 通过**（0 失败 / 0 错误 / 0 跳过）
- tv5 模拟器（Android 5.1 / API 22）：APK 安装成功，实机运行正常

**详见**：`docs/changes/2026-11-01-tv-only-phone-remote-cleanup.md`（含 `.json` 机器可读清单）
