# AGENTS.md — NAS-Music-TV

> 给协作者与 AI 代理的速查手册。**每条结论都来自源码/构建文件核实**，不含历史方案的推测。
> 与本文冲突的 `docs/archive/*` 是「当时的设计意图」，不代表当前实现。

---

## 1. Project Overview

Android **TV** 音乐播放器（Kotlin + Jetpack Compose for TV），聚合 NAS / 网络共享 / 网盘 / 网络音乐多源，支持本地曲库、MV、K 歌、音乐可视化，并可用手机或电脑浏览器**扫码反向遥控**。

- 包名 `com.lnh.nasmusictv` · 单模块 Gradle 工程
- `minSdk 22` / `targetSdk 34` / `compileSdk 34` / `versionName 1.0.0` · Java 17
- Kotlin 2.3.10 + Compose 插件 2.3.10 + KSP 2.3.10 + AGP 9.2.1
- TV Compose `androidx.tv:tv-material` 仍是 **alpha** → 需 `-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api`（已在 `app/build.gradle.kts` 全局设置）
- **TV-only**：`UiMode` 枚举只剩 `TV`；`MainActivity` 强制 `sensorLandscape`；手机不是客户端，只当遥控器
- GPL v3

### 源码规模（`app/src/main`，约 480 个 kt 文件）
| 包 | 文件数 | 职责 |
|----|--------|------|
| `ui/` | 126 | Compose 界面、组件、ViewModel、主题 |
| `backend/` | 110 | 数据源：NAS 后端 / 网络音乐 / 本地扫描 / 文件访问 / 下载 / 导出 / 照片墙 / 歌单 / 电台 / 天气 |
| `visualizer/` | 106 | 25+ 渲染主题（`fx` / `renderers` / `photo`） |
| `data/` | 73 | 数据模型、DataStore 偏好、播放统计、缓存清单 |
| `util/` | 23 | 编码、拼音、网络等工具 |
| `player/` | 23 | Media3 播放状态机、服务、均衡器频谱、人声分离 |
| `net/` | 10 | 6 个 NanoHTTPD 服务器 + 传输工具 |
| `lyrics/` | 5 | LRC/逐字解析、歌词获取优先级 |

---

## 2. Build & Test (Windows，本机实测)

### 2.1 环境
| 项 | 路径 |
|----|------|
| JDK | `C:\Program Files\Java\jdk-17.0.21.0.2`（Java 17） |
| Android SDK | `D:\tools\Android\Sdk` |
| Gradle | 项目 wrapper，`distributionUrl=file:///D:/tools/gradle-9.5.0-bin.zip`（**本地 file URL 是刻意的离线构建设计，不要"改成"网络 URL**；CI 会覆盖为网络分发） |
| adb | `D:\tools\Android\Sdk\platform-tools\adb.exe`（不在 PATH） |

### 2.2 常用命令
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17.0.21.0.2"
export ANDROID_HOME="/d/tools/Android/Sdk"

./gradlew :app:assembleDebug --no-daemon --continue --max-workers=2 -Dorg.gradle.jvmargs=-Xmx4g
./gradlew :app:testDebugUnitTest --no-daemon --continue --max-workers=2 -Dorg.gradle.jvmargs=-Xmx4g
./gradlew :app:lintDebug --no-daemon
```

### 2.3 ⛔ 本机必须绕的两个坑

**① 中文路径的 `GRADLE_USER_HOME` 会让测试 worker 崩溃**
`C:\Users\内涵哥\.gradle` 含中文 → Gradle 测试 worker 的 `@argfile` classpath 漏掉 `gradle-worker-main` 引导 jar（`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`）。
**绕法**：`subst G: C:\Users\内涵哥\.gradle` → `export GRADLE_USER_HOME=G:`。该坑**不影响 `assembleDebug`**（Kotlin 编译走 `-in-process`，不 fork worker）。

**② 必须加 `--no-daemon`**
本机 Gradle fork 出的子进程普遍起不来：AAPT2 报 `Daemon startup failed / Please check if you installed the Windows Universal C Runtime`（即使只改一个字符串）、Kotlin 编译守护进程报 `AccessDeniedException`。
用 `--no-daemon`（Kotlin 侧可叠 `-Pkotlin.compiler.execution.strategy=in-process`）绕过。注意 `aapt2.exe` 本身能独立运行（`aapt2 version` 正常），问题只出在守护进程模式，**别被 "Windows Universal C Runtime" 的提示误导**。

**③ `fileHashes.lock (拒绝访问)`**
`--no-daemon` 仍可能被 `org.gradle.jvmargs` 强制 fork 一次单次守护进程；若它卡在退出序列并持有 `.gradle/<ver>/fileHashes/fileHashes.lock`，后续构建全部在 `Could not create service of type FileHasher` 秒失败。
**解法**：`./gradlew --stop`（打印 `1 Daemon stopped`）后重跑。⛔ **不要 `rm` 锁文件** —— 带 safe-delete 包装的环境里 `rm` 失败会把文件留在 Windows「删除挂起」状态，之后任何 open 都 `Permission denied`，**反而让后续构建全挂**；`rm` 报 `Device or resource busy` 就说明有进程持有，去 `--stop` 而不是硬删。

### 2.4 单测现状
- `app/src/test/`：**241 个 `*Test.kt` / 2427 例**（JVM 单测，含 Robolectric）。`app/src/androidTest` **0 文件** —— 无 Instrumentation 测试。
- 增量跑显示 "up-to-date" 时**不会真正执行测试**，验数必须带 `--rerun-tasks`。
- 目标类隔离：`--tests "*PinyinUtilsTest"` 正常。
- **测试从磁盘读自身源码做契约校验时，源码根必须按真实包名 `com/lnh/nasmusictv/` 拼**（旧路径 `com/nasmusic/tv/` 会导致整批 `FileNotFoundException`，曾造成 288 例失败）。
- `NasMusicApp` 在 Robolectric 下**会真跑 `onCreate` 并读写 Room** —— 这是既定模式，不是 bug。

### 2.5 CI（`.github/workflows/build.yml`，3 个 job）
| job | 触发 | 内容 |
|-----|------|------|
| `build` | push `main`/`develop`、tag `v*`、PR→`main` | `assembleRelease` + 上传 APK + Release |
| `test` | — | `testDebugUnitTest`，失败时 dump 完整断言文案并上传 XML |
| `lint` | — | `lintDebug`（**阻塞**，0 error 为基线，warning 不阻塞） |

**签名是双模 + fail-fast**：仓库有 `SIGNING_KEYSTORE_BASE64` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD` 时用真 key；一次性 `ci-keystore.jks` 回退**只用于 `pull_request`**（fork PR 拿不到 secrets）。`push`/`tag` 下 secrets 或 `CRYPTO_PASSPHRASE` 缺失会**立即 `exit 1`**。写出的 `keystore.properties` 必须含 `cryptoPassphrase` 行，否则 `packageRelease` 的 guard 会让构建失败。
本地 release 从**项目根**读 `keystore.properties`，`storeFile` 是**项目根相对路径**（如 `release-key.jks`），`app/build.gradle.kts` 用 `rootProject.file(...)` 解析（不是 `project.file(...)`）。

---

## 3. Architecture (verified against source)

### 3.1 DI
**无 DI 框架、无 `getInstance()` 单例**：`NasMusicApp` 在 `onCreate()` 里直接构造并持有全部全局实例（`backendRegistry` / `appPreferences` / `playerManager` / `networkMusicManager` / `songDownloadManager` …）。取用方式是把 `Application` 转型为 `NasMusicApp`（`MainViewModel` 即 `app as NasMusicApp`）。

### 3.2 后端适配器
`BackendAdapter` 接口；实现位于 `backend/impl/`：
`JellyfinAdapter`（REST）、`NavidromeAdapter`（Subsonic 兼容）、`SubsonicAdapter`（Subsonic REST，Token+Salt）、`DaoliyuAdapter`（社区后端，端点反推）、`FeiniuAdapter`（fnOS）、`WebDavAdapter`、`SmbAdapter`。
`BackendRegistry` 注册 **7 种类型**，只把当前适配器**放在内存**里（重启后凭 `AppPreferences` 重连）；`testConnection()` 构造临时适配器，**不污染当前状态**。

### 3.3 播放
`PlaybackService`（Media3 `MediaLibraryService`）创建 ExoPlayer 并交给 `PlayerManager`。
**进度不是 ExoPlayer 回调驱动的**，而是 `PlayerManager` 里 1000ms `Handler` 轮询。
网络歌曲在播放前没有 resolved `streamUrl` —— `PlayerManager.onNeedResolveStreamUrl` 通知 `MainViewModel` 去解析再重放。
`PlaybackService` 同时注册 `androidx.media3.session.MediaLibraryService` 与 `android.media.browse.MediaBrowserService` 两个 action（缺前者 Media3 客户端找不到，缺后者平台 MediaBrowser / Android Auto 找不到）。

### 3.4 网络音乐（与 NAS 后端独立）
`backend/network/`：`MetingApiService`（多端点回退、302 直链解析、播放地址 LRU 500 `PLAY_URL_CACHE_MAX`）、`NeteaseOpenApi`（网易云免鉴权原生接口，失败回落 Meting）、`JamendoService`（CC 音乐）、`baidu/`（百度网盘 OAuth + 流式）、`mv/`（Bilibili MV）。
聚合入口 `SearchAggregator`（拼音匹配已是**默认主路径**）、`NetworkArtistExpansion`（艺人全量作品）、`PlayabilityProbe`（可播探测）。
收藏能力**只在 NAS 适配器**，`NetworkMusicManager` 无收藏。

### 3.5 电台与天气
- `backend/radio/`：`RadioBrowserClient`（公共电台目录）、`RadioSongScorer`（评分）、`SmartRadioManager`
- `backend/weather/`：`WeatherApi`（OpenWeatherMap）、`WeatherRadioManager`（按天气心情匹配曲库）。为让天气电台在**无 NAS 连接**时也能工作，`BackendAdapter` 被做成可空。

### 3.6 歌词
`LyricsManager`（在 `MainViewModel` 构造，依赖 `context` / `backendRegistry` / `networkMusicManager`）**确实调用** `BackendAdapter.getLyrics()`（Jellyfin 歌词端点）。
获取优先级：本地 `.lrc` 侧车 → 内嵌 ID3 歌词 → NAS 后端 API → 持久缓存 → 网络模糊匹配（标题+艺人）。
`LrcParser` 解析标准 LRC + 增强 LRC + **内嵌逐字时间戳**（karaoke `<mm:ss.xx>`），供 `KaraokeLyricsView` 渲染逐字高亮。
⛔ **未实现 QRC / kvly 解析**（源码零命中）；酷狗 krc 走 base64 解码。

### 3.7 可视化
`visualizer/`（`fx` / `renderers` / `photo`）。以 **Compose Canvas 过程化绘制**为主，两例用 WebView（`WorldGlobeRenderer` three-globe、`MoonlitRenderer` 解码 `assets/globe/moon.jpg`）。**无 OpenGL/Shader，无 ONNX/karaose 模型**。
音频源统一来自 `SpectrumRepository`（`AudioFrame` 的唯一写入方，由 `PlayerEqualizer` 注入）。
`VisualizerRendererFactory` 注册 **26 套主题**（`VisualizerTheme` 枚举在 `data/model/AppSettings.kt:125`；新增 = 加 Renderer + factory 分支）。旧的 2D `WorldRenderer` 已删除，E41「World」只由 3D `WorldGlobeRenderer` 提供 —— **不要复活 2D 世界渲染器**。
画质门控 `VisualQuality.supports(theme)`：`BASIC` 全档可选；`ADV` 需「不消耗粒子预算 **或** `maxParticles > 0`」（⛔ 不要再用 `maxParticles > 0` 当 ADV 的代理条件）；`ULTRA` 仅 `allowFramebuffer` 为真的档提供。
`VisualizerRendererFactory.create(theme, context)` 需要 `context` 的原因：`MOONLIT` 分支要解 `assets/globe/moon.jpg`，必须传 `context.applicationContext`（**绝不能传 Activity**）。
中英双语主题名由 `VisualizerThemeStringsGateTest` 把关 —— 缺 `values-en` key 编译期不报错、渲染期才崩。

### 3.8 E41 资产分两处（不要合回去）
- `app/src/main/assets/globe/`：**运行时**装载的 9 个文件（index.html + polyfill/three/three-globe 的 `.es5.js` + globe.js + 4 张贴图）
- `app/src/globe-upstream/`：上游原件与构建输入（`three.min.js`、`three-globe.min.js`、`earth.jpg`、`cities.json`、`downlevel_libs.mjs`），在模块内但 `main` 源集之外，**Gradle 不打包**
- ⛔ 两个 ES6 原版是 ES5 产物的**唯一再生源**，也是 diff 基线 —— **永不删除、也永不移回 assets**（移回即白占 0.62 MiB）
- ⛔ `downlevel_libs.mjs` 装依赖必须装在 `globe-upstream/` —— 装在 assets 下会被 `mergeDebugAssets` 打进 APK（实测一次多 23 MB）。护栏 `GlobeAssetsHygieneTest`；`.gitignore` 挡不住打包
- ⛔ `cities.json` **从不加载** —— 城市坐标由 Kotlin 经 `evalJs("WorldGlobe.initCities(...)")` 注入

### 3.9 导航
单 Activity `.ui.MainActivity`（**不是** `com.lnh.nasmusictv.MainActivity`；`am start -n com.lnh.nasmusictv/.ui.MainActivity`）+ 手工 `when(currentScreen)` 切换（`Screen` 枚举在 `data/model/Screen.kt`，14 个页面）。
⛔ 工程**没有任何 `androidx.navigation` 依赖，也没有 Navigation 组件** —— 需要新页面就加 `Screen` 枚举值 + `AppRoot` 分支，不要引框架。
BACK 五级：`MainActivity` 内实现 —— 退出全屏(0) → 关对话框(1) → 列表滚回顶(1.5) → 退回 NowPlaying(2) → 退出确认(3)。

---

## 4. Non-obvious constraints

### 4.1 平台形态
- **TV-only**：`AndroidManifest` 三个 feature（`leanback` / `touchscreen` / `screen.portrait`）全 `required="false"`（为兼容非认证盒子与上架），但代码是**纯 TV 横屏**。
- `MainActivity` = `screenOrientation="sensorLandscape"`，运行时不动态改 `requestedOrientation`。
- `configChanges` 刻意**不含** `uiMode` / `density` / `layoutDirection`（会影响深色模式、字体缩放、RTL 的重建时机）。
- ⛔ **Leanback 是死依赖**：`androidx.leanback:leanback:1.0.0` 声明在 `app/build.gradle.kts:231`，但**全源码零 `androidx.leanback` import**。UI 是 Compose TV。保留原因疑似间接提供 `fragment`，但 `fragment-ktx:1.6.2` 已显式声明。

### 4.2 焦点视觉（TV 最易复发的 UI 缺陷）
- 焦点相关视觉**一律**用 `shouldShowFocusVisuals()`（`ui/components/FocusIndicator.kt`），⛔ **不要**用 `isTVDevice()`。
  `isTVDevice()` 纯靠 `hasSystemFeature(leanback | type.television)` 嗅探，在非认证盒子上双双 false ⇒ 焦点环/缩放/容器色全关，用户只能按确认键反推焦点位置。
  正确判据是「用户是否在用按键导航」：① 无触摸屏 ② 电视 feature ③ **存在 `SOURCE_DPAD` 输入设备**（⭐ 非认证盒子进场即命中，无需先按键）④ 进程内收到过方向键。四条任一成立即显示。
- `shouldShowFocusVisuals()` 被 **135+ 组件在组合期调用**，而 `InputDevice.getDeviceIds()` 是 binder 调用 ⇒ **必须缓存**。⛔ 不能写成 `remember { }`（每个调用点各一份缓存，首屏仍 135 次 binder）；放 `object by lazy`。
- 误判面：部分 ROM/模拟器给内建触摸数字化器顺带多报 source 位 ⇒ `isRemoteNavigationDevice(hasDpad, isTouchDigitizer) = hasDpad && !isTouchDigitizer`（排除「既支持 DPAD 又是触摸数字化器」的设备）。真正的遥控器是**独立** input device，不会同时声明 `SOURCE_TOUCHSCREEN`。
- `activeFocus = isFocused && shouldShowFocusVisuals()`；缩放、焦点环、`focusedContainerColor`、`focusedContentColor` 全走它。
- ⛔ **焦点环必须画在 `.background(...)` 之后**（链尾）。Compose 绘制顺序 = 修饰符链顺序，越靠前越靠下；边框写在背景之前会被不透明容器色盖住 ⇒ 全应用看不见焦点框（与对比度无关）。用 `Modifier.focusRing(shape, color, visible)`。
- `onFocusChanged { }` 的 lambda **捕获本次组合的值**，焦点刚变时读到的还是旧值。需要立即生效的动画（如 1.15 倍缩放）必须读 `state.hasFocus && tvDevice`，不能读外层派生的 `activeFocus`。
- ⛔ **内容色契约**：`androidx.tv.material3.LocalContentColor` 默认值是 **`Color.Black`**，而 tv-material3 的 `Icon`/`Text` 都会回退到它。`FocusableSurface` 早期只提供自有的 `LocalFocusableContentColor` ⇒ 内部没显式写 `tint=`/`color=` 的 `Icon`/`Text` 全是**黑字压深底**。
  **任何自建容器组件**（Surface/Card/Button 封装）只要内部可能放裸 `Icon`/`Text`，就**必须同时**下发 `LocalFocusableContentColor` 与 `LocalContentColor`。门禁 `FocusableSurfaceColorContractTest`。

### 4.3 已塌缩的自适应 helper（TV-only 收敛的残留 API）
竖屏下线后下列 helper 变成**恒等实现**，旧文档里的缩放数学全部失效：
| helper | 现状 | 说明 |
|--------|------|------|
| `portraitTouchTarget(landscape)` | **恒返回入参** | 旧「物理 44dp ⇒ Compose 56dp」算术已不适用；TV 热区下限用 `TV_TOUCH_TARGET = 56dp` |
| `responsiveDialogSize(w)` | **= `Modifier.width(w)`** | `scrollable` 参数已随竖屏滚动对话框移除 |
| `UiMode` | **只有 `TV`** | `LocalUiMode` 恒 TV，`deriveUiMode()` 恒返回 TV |
| `adaptiveColumns(tv, phonePortrait, medium)` | **仍按 `screenWidthDp` 分档** | `≥1000→tv` / `≥600→medium` / 其余→`phonePortrait`；TV 实机 ≥1000 恒走 `tv` 档，`phonePortrait` 形参在 TV 上是死参 |
| `TV_TOUCH_TARGET_DP` | `56f` | `UiModeTest` 仍在纯 JVM 下断言该算术 |

新增控件仍用 `portraitTouchTarget(...)` 包裹（保持调用点稳定），但**不要**再基于它做缩放换算。

### 4.4 拼音
用 TinyPinyin（`com.github.promeg:tinypinyin:2.0.3`），**刻意选择**它是因为 `minSdk 22` 而 `android.icu.Transliterator` 需要 API 26+。⛔ 不要换回 ICU 方案。

### 4.5 编码
**GBK 乱码陷阱**（`util/EncodingUtils.kt`）：Jellyfin 可能把 GBK ID3 字节按 UTF-8 存。`utf8Body()`/`fixEncoding()` 在出现 U+FFFD / 希腊字母 / 西里尔字母时尝试 GBK 回退。部分情况在客户端**不可恢复**（已被编码成 Unicode 码点）—— 不要以为能彻底修好艺人名乱码。

### 4.6 清晰文本
`usesCleartextTraffic="true"`（局域网 NAS HTTP 需要）。

### 4.7 ProGuard / R8
release 开启 minify + shrinkResources。`proguard-rules.pro` 保留 `data.model` / `data.prefs` / `backend` / Gson / ExoPlayer。v2.5.1 曾因 **Gson 类型擦除在 R8 下崩溃** —— 新增序列化模型时保留对应 `-keep` 规则。
`Log.d/v` 在 release 被 `-assumenosideeffects` 剥掉；⛔ **注意 `AppLog.d/i/w` 另有 `if (BuildConfig.DEBUG)` 守卫**，release 下 R8 会连字符串常量一起折掉（`AppLog.e` 无守卫必然保留）—— 用 dex 字符串验 R8 存活时**别拿 d/i/w 文案当证据**。

### 4.8 MediaSession
⛔ **绝不在 `MediaSession.Callback.onConnect` 里 `reject()`**（车机蓝牙媒体键失效的根因）。系统侧控制器（蓝牙 AVRCP / SystemUI / 车机）经框架 `MediaController` 到达，Media3 走 `MediaSessionLegacyStub.tryGetController() → onConnect`，reject 会让命令被**静默丢弃**。
安全边界放在 `setAvailableSessionCommands`；它是**严格集合成员判定**（非增量），因此**只能往 `DEFAULT_SESSION_AND_LIBRARY_COMMANDS` 里加**。门禁 `MediaSessionAccessPolicyTest`。⚠️ 该问题**只在 release 构建可复现**。

### 4.9 人声分离（Demucs）
- **输出契约**（`player/DemucsSeparator.kt`）：`decodeAudioToTempFile()` 保证输出**恒为 44100Hz 立体声**（单声道复制成 L/R，非 44100 由 `LinearResampler` 线性插值归一化）。因此 `writeWavHeader()`/`patchWavDataSize()` 无条件用 `SAMPLE_RATE`/`CHANNEL_COUNT` 是安全的 —— ⛔ 若日后放开该保证，这两处必须改为接收实际参数。`totalSamples` 取 `writeFrame()` 的调用次数，⛔ 不要用「float 数 / 声道数」反推。
- **模型完整性**（`player/ModelDownloadManager.kt`）：`EXPECTED_SHA256` 取自 HuggingFace LFS 的 `oid`（对 LFS 对象而言 `oid` 就是 SHA-256）。下载完成必须过 SHA-256 才落盘，加载前再 `verifyModelIntegrity()` 校验一次。**该校验对自定义 URL 同样生效** —— 自定义源要提供字节一致的文件；换权重必须同步改 `EXPECTED_SHA256`。代码下载的是 **fp16** 权重（165,612,636 字节），上游另有 316MB 非 fp16 版本，两者 SHA 不同。`isModelDownloaded()` 保持「快速判定」（可能被主线程调用，166MB 哈希会 ANR），⛔ **不要在里加哈希**；大小阈值已收紧到 **±1%**（`163,956,509 ~ 167,268,762` 字节）。
- 模型 `htdemucs_ft_vocals.onnx`（FP16 ~166MB，4 stems：drums/bass/other/vocals），默认 AWS CDN 镜像，设置页可下载。

### 4.10 ONNX Runtime
- **Java API 坑**：`OnnxValue.getInfo()` 返回的 `ValueInfo` 是**空接口**，shape 只在 `TensorInfo` 上 —— `value.info.shape` 编译不过，必须 `(value.info as? TensorInfo)?.shape`。`OrtEnvironment.getEnvironment()` 是**进程级单例，绝不能 close**。`OnnxTensor` / `OrtSession.Result` 持有 native 内存（GC 收不到），必须显式 close。
- ⛔ **版本被「minSdk 22」与「16KB 页对齐」双重锁定，不要随手升级**：lint `Aligned16KB` 会报（1.17.1 的 native 库 `p_align = 4096`）。
  实测矩阵（`p_align`，AAR 内 4 ABI × 2 库）：**1.17.1** 全 4096 / minSdk 21；**1.20.0** 主库 16384 但 `libonnxruntime4j_jni.so` 仍 4096 / minSdk 21；**1.21.1** 仅 arm64 对齐 / minSdk 24；**只有 1.29.0 全对齐** / minSdk 24。
  机制：① lint 的 `PageAlignmentDetector` 遍历 AAR 解包目录下**全部 ABI**（不受 `abiFilters` 影响），命中第一个未对齐即 `return` ⇒ 每个依赖只报 1 条；② 1.29.0 要求 `minSdkVersion 24`，直接改会让 manifest merger 失败，改 minSdk 则砍掉 Android 5.0/5.1/6.0（含创维 5.1.1 真机回归基准设备）。
  **当前有意保持 1.17.1**（Warning 非 Error，`targetSdk 34` 下 Play 的 16KB 强制要求未触发）。**触发条件**：升 `targetSdk 35` 或上架前必须处理。
- ONNX 仅用于**人声分离（Demucs）**与**人脸检测（YuNet）**，可视化与歌词不用 ONNX。

### 4.11 认证头注入链路
`BackendAdapter.streamHeaders` 曾**定义了但全仓库无消费方**（Jellyfin/Navidrome 把凭据拼在 URL query 上）。飞牛是第一个必须走请求头的后端。
现由 `BackendRegistry` 在连接成功时向 `BackendAuthHeaders` 绑定 **provider**（每次请求**实时读取** `adapter.streamHeaders` —— 快照形态会漏掉静默重登换新令牌），再由 `BaiduHttpDataSourceFactory` 拦截器按 **host 精确匹配**注入（同一客户端服务 ExoPlayer 播放与 Coil 封面两条链路）。
⛔ **新增后端若要走请求头，只需覆写 `streamHeaders`，不要另起炉灶**；也不要放宽 host 匹配，否则令牌会随 302 泄漏到 CDN。

### 4.12 飞牛（fnOS）后端协议
`backend/impl/FeiniuAdapter.kt` + `FeiniuUrl.kt`。⛔ 不要回到第三方逆向文章的猜测端点。
易错点：① 认证头是 **`Authorization: <userToken>`（原始值，无 Bearer）**，不是 `Cookie: music-token=`；② 登录响应字段是 **`data.userToken`**；③ 分页是 **`page` + `size`**，不是 `limit`；④ ID 全是 **GUID**，参数名 `albumGUID`/`artistGUID`/`trackGUID`/`playlistGUID`；⑤ 流地址 `track/stream?guid=`（查询参数）、封面 `static/cover?coverId=`（按 **coverId** 而非曲目 ID）；⑥ `duration` **已是毫秒**；⑦ 信封 `{code,msg,data}`，`data` 可为 null；⑧ 默认端口 **5666**（HTTPS 5667），不是 80；⑨ **服务端没有搜索端点**，`searchSongs` 是客户端本地过滤；⑩ 适配器必须在**解析期填充 `Song.streamUrl`**（`FeiniuUrl.streamUrl`）—— 全 app 的 NAS 播放解析只认 `getSongsByIds()` 返回的 streamUrl，置空会让点播「解析失败」。

### 4.13 反向遥控与本地 IP
- 6 个 NanoHTTPD 服务器：`LocalInputServer`(18080)、`BackupTransferServer`(18081)、`RemoteControlServer`(18082)、`ModelTransferServer`(18083)、`PlaylistUploadServer`(18084)、`ServerConfigTransferServer`(18085)。`RemoteControlServer` **仅在进入 K 歌/MTV 模式时**由 `MainViewModel` 启动，不是常驻。
- 安全：原绑 `0.0.0.0` 且无鉴权，现由 `LocalServerAuth` 发放**一次性 token** 写入二维码 URL（`?t=...`），服务重启即换。
- ⛔ **`NetworkUtils.getLocalIpAddress()` 必须读 `/proc/net/fib_trie`**：Java `NetworkInterface.getNetworkInterfaces()`（getifaddrs）**枚举不到 TAP 桥接地址**（模拟器上只看到 NAT `10.0.2.15`），但内核 `fib_trie`（world-readable）里有 `host LOCAL` 条目。实现上跨接口收集 NetworkInterface 地址 + 读 `fib_trie` 补齐 + 按 `ipScore`（`192.168.`=5 > `10.`=4 > `10.0.2.`=1 > `169.254.`=0）全局择优。缺了这一步，二维码会给出不可达的 NAT 地址，手机扫码连不上。

### 4.14 其它
- **Multi-ABI**：`arm64-v8a` / `armeabi-v7a` / `x86_64` / **`x86`**（含 x86 以便 TV 模拟器调试）。
- lint 输出里出现 `LintCliClient.analyzeOnly` → UAST visitor 的**内部异常栈是既有的**（`docs/archive/verification/verify4.log` 中同样存在），不影响报告与构建结果，不要当新问题排查。

---

## 5. Conventions

- Git/分支/commit 规则见 `.opencode/rules.md`（conventional-commit 前缀、`main`/`dev`/`feat/*`，以及"经验证的改动要同步 `CHANGELOG.md` + `docs/technical-overview.md`"）。**以该文件为准，不要在这里重复。**
- 变更历史记录在 `docs/technical-overview.md` §15；`docs/changes/` 存每次变更报告；`docs/archive/` 是历史快照（含原 §10 的 1.6 万行历史流水 `technical-overview-changelog.md`）。
- 测试：`app/src/test/`，JUnit4 + Robolectric。目标类隔离：`./gradlew testDebugUnitTest --tests "*XxxTest"`。

### 5.1 ⛔ 绝不用 PowerShell 对 UTF-8 源文件做文本往返
`(Get-Content -Raw) -replace … | Set-Content` 会按控制台代码页（CP936）读、UTF-8 写回 ⇒ 中文注释变乱码，并**吞掉紧邻的 ASCII 标点与换行** ⇒ 语法直接崩。
批量替换一律用 `edit`/`write` 工具，或 `python -c` 配 `encoding='utf-8'`。
（真实教训：曾用该手法毁掉一个 2400 行、未提交、无备份的原型文件。）

### 5.2 文档写作风格（用户明令）
- `README.md` **只列功能** —— 面向用户、简洁，剔除实现级细节。
- `CHANGELOG.md` 条目**只写做了什么** —— 每条一行，**不写根因、不写实现细节、不写源码行号、不写验证过程**；那些属于 `docs/technical-overview.md`。
- **只改当前版本的段落**，永不重写历史段落。

### 5.3 文档生命周期（归档规则）
`docs/` 根目录只放**活文档**：`technical-overview.md`、`conventions-adaptive-ui.md`。完成的方案移入 `docs/archive/`；对外文章（知乎等）在 `docs/articles/`。
一份文档**同时满足**下列两条才可归档：
1. **它不是活文档**（只有持续维护的索引/约定文档永久留在根目录）；
2. **它的命运已定** —— ✅ 已落地（以 `CHANGELOG.md` 与源码是否真存在为准，如 `SubsonicAdapter.kt`、`KaraokePlaybackScreen.kt`），或 ❌ 永久废弃。

### 5.4 可视化原型的验证脚本
浏览器原型（`docs/*.html`）的验证脚本在 `docs/archive/verification/scripts/`。
⭐ **Kotlin 端单测可直接照抄 harness 的断言口径**（测的都是纯函数：位置映射、噪声连续性、cull 阈值、每帧位移上限），不需要图形设备。
⚠️ harness 的探针**必须挂在目标变量赋值之后** —— 挂错作用域会读到上一帧残留值，量出「间距恒等于 0」这类假结论（真实发生过）。
⚠️ 基线数字**只能由脚本再生，不许手抄**（例：Moonlit 云场基线由 `moonlit_cloud_golden.js` → `MoonlitCloudBaseline.kt`）。把数抄进 Kotlin 注释或测试就是偏差重演。

---

## 6. 新增功能检查清单

- [ ] 新页面：加 `data/model/Screen.kt` 的 `Screen` 枚举值 + `AppRoot` 分支（⛔ 不引 Navigation 框架）
- [ ] 新文案：**同步** `res/values/strings.xml` 与 `res/values-en/strings.xml`
- [ ] 新可聚焦组件：焦点视觉走 `shouldShowFocusVisuals()`；焦点环挂链尾（在 `.background()` 之后）
- [ ] 新容器组件：同时下发 `LocalFocusableContentColor` 与 `LocalContentColor`
- [ ] 多列网格走 `adaptiveColumns(...)`（⛔ 不写死 `GridCells.Fixed(常量)`）
- [ ] 对话框走 `responsiveDialogSize(...)`；弹层必须 `RegisterDialogBackHandler(onDismiss)`
- [ ] ⛔ **禁止在 `AppRoot` 顶层订阅 `progress`/`duration`**（1000ms Handler 轮询，会导致整棵树每秒重组）—— 需要进度的组件在自己内部订阅
- [ ] 触摸目标：`TV_TOUCH_TARGET = 56dp` 为下限；沿用 `portraitTouchTarget(...)` 包裹
- [ ] 新后端：实现 `BackendAdapter`，需要请求头则覆写 `streamHeaders`（⛔ 不另起认证链路）
- [ ] 新可序列化模型：补 `proguard-rules.pro` 的 `-keep`（Gson 擦除坑）
- [ ] 纯逻辑（列数、方向、评分）抽顶层 `internal fun` 并补 JVM 单测
