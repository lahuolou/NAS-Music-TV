# Changelog

> 显著变更记录。
> 格式参考 [Keep a Changelog](https://keepachangelog.com/)；版本号见 `app/build.gradle.kts` 的 `appVersionName`。
> 类型：`Added` | `Changed` | `Fixed` | `Removed`

---

## [v1.0.0] — 当前版本（`versionName=1.0.0`，`versionCode=1`）

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
