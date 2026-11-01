# NAS Music TV

> 安卓 **TV 端**音乐播放器（包名 `com.lnh.nasmusictv`）。用遥控器在大屏电视上听 NAS / 网盘 / 流媒体音乐，支持本地曲库、MV、K 歌、音乐可视化，并可用手机或电脑浏览器**扫码反向遥控**。

- **形态**：仅 Android TV，强制横屏（`sensorLandscape`，已无竖屏/手机客户端代码）；`minSdk=22`（Android 5.1）/ `targetSdk=34` / `compileSdk=34`
- **技术栈**：100% **Jetpack Compose for TV**（非 Leanback Presenter）；播放基于 **AndroidX Media3 / ExoPlayer**
- **手机不是客户端，而是遥控器**：App 在部分模式下启动内嵌 HTTP 服务并展示二维码，手机/电脑浏览器扫码即可控制

---

## 鸣谢与衍生说明

本项目是 [hxzhang2000](https://github.com/hxzhang2000) 原创 [NASMusicTV](https://github.com/hxzhang2000/NASMusicTV) 的衍生分支（提取 TV 版），保留原版 GPL v3 协议与全部既有功能，在此向原创作者致谢。

在原版基础上，本项目进行了以下改动：

- **包名与身份**：`com.nasmusic.tv` → `com.lnh.nasmusictv`，项目名改为 NAS-Music-TV
- **单平台收敛**：Android TV 成为**唯一完整播放客户端**，手机不再打包独立 UI，仅通过浏览器扫码充当遥控器 / 输入终端
- **清理手机端残留**：删除手机单列布局（约 187 行）、手机独占的「图库」照片来源 `MediaStorePhotoSource.kt` 及其 `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` 权限、竖屏与手机紧凑 UI 体系；`UiMode` 枚举塌缩为仅 `TV`
- **反向遥控可达性修复**：`NetworkUtils.getLocalIpAddress()` 新增 `/proc/net/fib_trie` 兜底 + 跨接口全局择优，二维码给出**同网段地址**而非模拟器 NAT 的 `10.0.2.15`（Java `NetworkInterface` 枚举不到 TAP 桥接地址）
- **遥控安全加固**：6 个内嵌 NanoHTTPD 服务由原绑 `0.0.0.0` 无鉴权，改为**一次性 token** 写入二维码 URL（`?t=...`），服务重启即换
- **TV 交互默认化**：拼音（全拼 / 首字母）搜索提升为默认主路径（移除 `isTVDevice` 设备门）；文本输入默认 TV 自定义键盘
- **构建与签名**：修复 release 构建的 R8 / slf4j 缺失类失败；统一发布签名身份并配齐仓库签名 Secrets，自此更新可直接覆盖安装
- **测试与文档**：修复 288 个因旧包名硬编码导致的单测失败（现 2427 例全绿）；以代码为唯一基准重写各项维护文档

---

## 它能做什么

### 媒体源（统一封装在 `BackendAdapter`）
| 类型 | 适配器 | 协议 |
|------|--------|------|
| Jellyfin | `JellyfinAdapter` | Jellyfin REST |
| Navidrome | `NavidromeAdapter` | Subsonic 兼容 API |
| Subsonic | `SubsonicAdapter` | Subsonic `/rest/`（兼容 Navidrome/lx-server/Airsonic） |
| 飞牛 fnOS | `FeiniuAdapter` | fnOS music API v1 |
| 岛鲤鱼 | `DaoliyuAdapter` | 自定义 REST（社区后端，端点为反推） |
| WebDAV | `WebDavAdapter` | WebDAV（Basic Auth） |
| SMB | `SmbAdapter` | **SMB2/SMB3**（jcifs-ng，SMB1 禁用） |
| 百度网盘 | `BaiduNetdiskService` | OAuth2 + 流式播放（非 `BackendAdapter`） |
| 本地存储 | `LocalMusic*` | USB / SD / 外接存储扫描，Room 持久化 |

### 网络音乐（非 NAS，`NetworkMusicManager` 多源路由）
- **Meting 聚合**（网易/QQ/酷狗…）— 默认 `https://meting.mikus.ink/api`，多码率 + 可播探测
- **网易云**原生免鉴权接口（失败回落 Meting）
- **Jamendo** CC 独立音乐（官方 API v3）
- **Bilibili MV**（`BilibiliMvService`，三步取流 + Referer 防盗链）

### 播放能力（`player/`）
- Media3/ExoPlayer；**多码率**（全局默认 + 单曲覆盖）、**交叉淡入**、**睡眠定时**
- **均衡器 + FFT 频谱**（`PlayerEqualizer` / `SpectrumAnalyzer`）
- **离线下载与下载后播放**（`backend/download/`，串行队列 + 重试 + 崩溃恢复 + ID3 标签/内嵌封面/歌词写入）
- **歌词**：LRC（含逐字 `<mm:ss.xx>` 高亮）+ 酷狗/网易网络歌词 + 本地侧车/内嵌 ID3
- **K 歌人声分离**：HT-Demucs FT ONNX（`DemucsSeparator`，vocals / accompaniment 双轨），模型经设置页下载
- **Android Auto / 车机**：`PlaybackService` 实现 `MediaLibraryService` + `MEDIA_PLAY_FROM_SEARCH` 语音起播

### 可视化（`visualizer/`，26 套主题）
- 以 **Compose `Canvas` 过程化绘制**为主；两例用 WebView（`WorldGlobeRenderer`、`MoonlitRenderer`）
- 音频源来自播放器的频谱（`SpectrumRepository`），含节拍检测
- 画质分级 `VisualQuality`（HIGH/MEDIUM/LOW）按设备过滤可用主题
- 照片墙是其中一个主题（`PHOTO_WALL`）：EXTERNAL（USB/SD/SAF）+ Jellyfin 来源，**YuNet 人脸检测 ONNX** 已接入

### 反向遥控（`net/`，NanoHTTPD，端口 18080–18085）
| 端口 | 服务 | 用途 | 启动时机 |
|------|------|------|----------|
| 18080 | `LocalInputServer` | 手机扫码向 TV 输入框推送文字 | 打开输入对话框时 |
| 18081 | `BackupTransferServer` | 备份/恢复传输 | 按需 |
| **18082** | `RemoteControlServer` | 手机/PC 浏览器遥控（队列/搜索加歌） | **进入 K 歌 / MTV 模式时** |
| 18083 | `ModelTransferServer` | 人声分离模型传输 | 按需 |
| 18084 | `PlaylistUploadServer` | 歌单上传 | 按需 |
| 18085 | `ServerConfigTransferServer` | 服务器配置传输 | 按需 |

安全：原绑 `0.0.0.0` 无鉴权，现改为**一次性 token** 写入二维码 URL（`?t=...`），服务重启即换。本地 IP 经 `util/NetworkUtils.getLocalIpAddress()` 选取（避开模拟器 NAT，优先局域网段）。

### 其它
- **电台**：公共电台目录（标签筛选、直播流即点即播）；**天气电台**：按实时天气匹配心情电台
- **歌单导入**：M3U / 网易云 / JSON / TXT（`backend/playlist/`）
- **导出**：经 SAF `OpenDocumentTree` 导出到外接设备（`backend/export/`）
- **数据持久化**：3 个 Room 库（本地音乐 `local_music.db` v3、下载 `downloads.db` v3、人脸 `photo_face.db` v1）＋ DataStore Preferences（统一替代 SharedPreferences）

---

## 工程结构

```
app/src/main/java/com/lnh/nasmusictv/
├── backend/        # 数据源：impl/(NAS 后端) · network/(流媒体) · local/(本地扫描) · files/(SMB/WebDAV) · download/ · export/ · photo/ · playlist/
├── player/         # Media3 播放状态机、PlaybackService、均衡器/频谱、人声分离
├── lyrics/         # LRC/逐字解析、歌词获取优先级
├── visualizer/     # 25+ 渲染主题 + 频谱数据
├── ui/             # Compose TV 界面：screens/ · components/ · viewmodel/ · theme/
├── net/            # 6 个 NanoHTTPD 内嵌服务器 + SMB 回环桥
├── data/           # 模型(Screen 枚举) · prefs/DataStore · model/
├── util/           # NetworkUtils 等
└── NasMusicApp.kt  # Application，手动 DI 容器（无 Hilt/Dagger）
```

导航**不是** Jetpack Navigation，而是 `data/model/Screen.kt` 的 14 个枚举页 + `MainViewModel` 的 `StateFlow` 驱动 `AppRoot` 的 `when` 分支直接组合。

### 主要界面
首页(Home) · 正在播放(NowPlaying) · 音乐库(Library：发现/搜索/电台/Jamendo/浏览) · 我的(Mine) · 播放队列(Queue) · 设置(Settings) · 服务器连接(ServerConnect) · 专辑/艺人详情 · 均衡器 · 歌单管理 · 网盘(Netdisk) · 天气电台(WeatherRadio) · 播放统计(PlayStats)；叠加层：可视化 / K 歌 / MTV。

---

## 构建与运行

### 环境要求
- **JDK 17**（本机用 `C:\Program Files\Java\jdk-17.0.21.0.2`）
- **Android SDK**（含 platform-34、build-tools）
- 通过项目自带 Gradle wrapper（`gradlew`）运行，**无需预先安装 Gradle**

### 常用命令
```bash
# 调试包（APK：app/build/outputs/apk/debug/NAS-Music-TV-debug-v1-0-0.apk）
./gradlew :app:assembleDebug

# 单元测试（纯 JVM，241 个测试文件，含 Robolectric）
./gradlew :app:testDebugUnitTest

# 安装到已连接设备/模拟器
adb install -r app/build/outputs/apk/debug/NAS-Music-TV-debug-v1-0-0.apk
```

> ⚠️ 测试 worker 在含中文路径的 `GRADLE_USER_HOME` 下会崩溃（Gradle 已知坑）。本机用 `subst G: C:\Users\内涵哥\.gradle` 后设 `GRADLE_USER_HOME=G:\` 再跑测试可绕开。该坑**不影响 `assembleDebug`**。

### 签名与密钥
- 百度网盘 `BAIDU_APP_ID/SECRET`、AES 口令 `CRYPTO_PASSPHRASE` 从 **`keystore.properties`**（已 gitignore，不入库）读取，写入 `BuildConfig`
- `release` 打包前校验 `CRYPTO_PASSPHRASE` 已配置，缺失则 fail-fast（避免换密钥静默发布导致既有凭据无法解密）
- 无 `keystore.properties` 时 CI 仍可做 debug 构建与测试，仅 release 签名跳过

### ABI
`arm64-v8a · armeabi-v7a · x86_64 · x86`（含 x86 以便 TV 模拟器调试）

---

## 测试
- 位置：`app/src/test/`（241 个 Kotlin 测试文件）
- 框架：**JUnit4 + Robolectric 4.11.1 + Mockito**，OkHttp `mockwebserver`，Room `room-testing`
- **无 `androidTest`**（无真机/Instrumentation 集成测试，全部为 JVM 单测）
- 注意：`NasMusicApp` 在 Robolectric 下会真跑 `onCreate` 并读写 Room——这是既定测试模式

---

## 文档
- `docs/technical-overview.md` — 架构总览（模块边界、数据流、外部集成、关键约定）
- `docs/conventions-adaptive-ui.md` — UI / TV 适配约定（焦点视觉、尺寸/对话框 helper）
- `docs/changes/` — 历次变更报告
- `docs/archive/` — 历史方案与计划（快照，保留以供追溯，**不代表当前代码状态**）

---

## 许可与第三方组件
- 音频元数据读写使用 **jaudiotagger 3.0.1**（LGPL）；项目整体授权以仓库 LICENSE 为准（衍生自 [hxzhang2000/NASMusicTV](https://github.com/hxzhang2000/NASMusicTV)，保留 GPL v3，见上方「鸣谢与衍生说明」）
- 人声分离模型 **HT-Demucs**（MIT）；人脸检测 **YuNet**（OpenCV Zoo，Apache-2.0），均经 ONNX Runtime 1.17.1 推理
- 详细依赖见 `app/build.gradle.kts`
