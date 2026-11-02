# Changelog

> 显著变更记录。
> 格式参考 [Keep a Changelog](https://keepachangelog.com/)；版本号见 `app/build.gradle.kts` 的 `appVersionName`。
> 类型：`Added` | `Changed` | `Fixed` | `Removed`

---

## [v1.0.2] — 当前版本（`versionName=1.0.2`，`versionCode=3`）

服务器连接排障与手机遥控能力增强。

### Added
- 手机扫码填配置支持 WebDAV 与 SMB 两类后端
- NAS 后端连接失败时显示具体失败原因

### Changed
- 移除首页精选推荐轮播
- 服务器配置表单输入框改用通用型占位符提示，账号与密码改为同行排列
- 后端类型选择改为两行排列
- 顶栏品牌区与首页标题区随连接状态切换：已连接显示当前服务器名称 + 「已连接」徽章，未连接显示应用名「NAS Music TV」

### Added
- 新增通用「已连接」徽章组件 `ConnectedBadge`（绿点 + 文字）

### Fixed
- 修复手机扫码填配置提示 Forbidden 的问题
- 修复服务器配置页「连接测试」按钮文字被裁切的问题
- 修复后端类型选择项过长导致文字截断的问题

### Docs
- 同步反向遥控 token 机制在 README、AGENTS.md 与技术文档中的描述

---

## [v1.0.1] — `versionName=1.0.1`，`versionCode=2`

基于 v1.0.0 的 TV 端观感打磨与体验修复（代码等价，无功能新增）。

### Added
- 首页「精选推荐」hero 大图轮播：复用 `recentlyAddedAlbums` 与 `CoverImage`，600ms 交叉淡入自动切换，聚焦 / 减弱动效时暂停，含渐变遮罩与指示点

### Changed
- 亮色主题播放页沉浸感：背景借封面主色做极淡晕染；黑胶组件亮色分支加静态极淡辉光与中性灰底座阴影（不呼吸、不压暗）
- 歌曲网格间距 8dp → 12dp（专辑 / 歌单网格本已 12–20dp，安全）

### Fixed
- 歌曲卡片聚焦放大溢出：卡片聚焦缩放 1.06 → 1.03，修复放大时溢出相邻格
- 空 / 错状态图标升级：`ListStateIndicators` 的 ⚠ / 📭 emoji 换为 Material 矢量图标 + 柔和圆形底
- 反馈出口收敛：新增统一 `AppToast`，收口散落 `Toast` 调用（SettingsScreen ×2、LinkUtils ×1），并同步更新 `BackupResultVisibilityGate` 门禁正则

### Docs
- README 新增「鸣谢与衍生说明」：声明本项目为 hxzhang2000 原创 NASMusicTV 的 TV 衍生分支，保留原版 GPL-3.0 协议与全部既有功能

---

## [v1.0.0]（`versionName=1.0.0`，`versionCode=1`）

首个以最新开发线功能为基础的稳定版。

### 媒体源
- **NAS 后端**：Jellyfin / Navidrome / Subsonic / 飞牛 fnOS / 岛鲤鱼，统一 `BackendAdapter` 封装
- **网络共享**：WebDAV、SMB2/SMB3（jcifs-ng，SMB1 禁用）+ 应用内 loopback HTTP 桥接播放
- **网络音乐**：Meting 聚合（网易/QQ/酷狗）、网易云原生接口、Jamendo CC、Bilibili MV
- **百度网盘**：OAuth2 授权 + 流式播放
- **本地存储**：USB / SD / 外接存储扫描，Room 持久化，增量扫描

### 播放
- Media3/ExoPlayer；多码率、交叉淡入、睡眠定时、均衡器 + FFT 频谱
- 离线下载与下载后播放（ID3 标签/内嵌封面/歌词写入）
- 歌词：LRC（含逐字高亮）+ 网络歌词 + 本地侧车/内嵌 ID3
- K 歌人声分离（HT-Demucs ONNX）、MTV（B 站 MV）
- Android Auto / 车机语音起播（`MediaLibraryService` + `MEDIA_PLAY_FROM_SEARCH`）

### 体验
- 音乐可视化 26 套主题（Compose Canvas 为主，两例 WebView）；照片墙可视化（EXTERNAL + Jellyfin + YuNet 人脸检测）
- 电台 / 天气电台；歌单导入（M3U / 网易云 / JSON / TXT）
- 反向遥控：NanoHTTPD 端口 18080–18085，进入 K 歌/MTV 时启动 `RemoteControlServer`(18082)，二维码一次性 token 鉴权
- 完整 D-Pad 焦点体系、HDMI-CEC 媒体键、前台媒体通知

---

## [TV-only 收敛]（源码审计版方案，已落地）

Android TV 成为**唯一完整播放客户端**；手机不再打包独立 UI，仅通过扫码浏览器充当遥控器 / 输入终端。

### Changed
- `VolumeControl` / `VisualizerStage` 移除手机触屏分支，收敛为 TV `FocusableSurface` 单面
- `SearchAggregator` 移除「仅 TV 启用拼音」设备开关，拼音（全拼/首字母）匹配提升为默认主路径
- `TextInputDialog` 移除 `isTVDevice` 嗅探，默认 TV 自定义键盘；二维码面板改为按调用方开关启用
- `UiMode` 枚举收敛为仅 `TV`（`LocalUiMode` / `deriveUiMode` 恒返回 TV）；`portraitTouchTarget` / `responsiveDialogSize` 塌缩为恒等实现
- `MainActivity` 强制 `sensorLandscape`，移除动态 `requestedOrientation` 与手机 system-bar 隐藏逻辑

### Removed
- 手机独占「图库」照片来源（`MediaStorePhotoSource` 及相关权限 `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED`）
- `UiMode.PhoneLandscape` / `LocalPhoneCompat` 等手机分支标记；`PHONE_UI_SCALE` 缩放数学随之失效
- 权限瘦身：移除通知权限、电池优化白名单（`Manifest` 注释详见 `AndroidManifest.xml`）

### Preserved
- 手机浏览器遥控链路完整保留（`RemoteControlServer` / `LocalInputServer` / `*TransferServer` / `LocalServerAuth` / `QrCodeGenerator` / `NetworkUtils` / `SmbHttpStreamServer` / `RemoteControlHtml`）
- `leanback required=false` 与 Android Auto 声明保留
- `GALLERY` 枚举 / `photoWallGalleryEnabled` 字段 / `PhotoWallAccessPolicy` / `PermissionHelper` 照片 API 保守保留（兼容旧备份与 `gallery:<id>` 缓存）

### Fixed
- 反向遥控二维码在桥接 / TAP 网络下显示 NAT 地址：Android `NetworkInterface` 无法枚举桥接网卡地址，改为并读 `/proc/net/fib_trie` 兜底择优（`NetworkUtils.getLocalIpAddress`）
- 288 个单元测试预存失败：契约 / 门禁测试硬编码旧包名路径 `com/nasmusic/tv/`（59 个文件、143 处），更正为 `com/lnh/nasmusictv/`
- `NetworkMusicManagerTest` 取消断言与 `VisualizerQualityWiringTest` 过期门禁（断言了从未存在的字符串键）
- release 构建失败（R8 缺 `org.slf4j.impl.StaticLoggerBinder`）：`jcifs-ng` 传递引入的 `slf4j-api` 在 Android 上无 binding 实现，补 `-dontwarn org.slf4j.**`

---

## 测试与质量
- 单元测试 241 个文件（JUnit4 + Robolectric + Mockito），全量零 androidTest
- 关键护栏：聚焦视觉 `shouldShowFocusVisuals()` 约定、`FocusableSurface` 内容色契约、频谱/可视化 renderer 预算与 gate 测试、下载迁移测试
- 本轮全量结果：**2427 / 2427 通过**（0 失败 / 0 错误 / 0 跳过），`assembleDebug` BUILD SUCCESSFUL
