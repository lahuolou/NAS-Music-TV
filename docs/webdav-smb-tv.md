# WebDAV / SMB 音乐源（Android TV）

本文说明 NasmusicTV 的文件型 NAS 音乐源实现。新增连接与目录选择界面只针对 Android TV 遥控器操作；不新增手机布局或手机专用页面。仓库原有的手机扫码配置入口保持原样。

## 功能入口

在“服务器连接”页面选择 **WebDAV** 或 **SMB / Windows 共享**。

### WebDAV

- 地址示例：`http://192.168.1.10:5005/dav` 或 `https://nas.example.com/dav`
- 用户名、密码：按 NAS 的 WebDAV 账号填写；匿名服务可留空。
- 音乐根目录：可手动填写，也可用 TV 遥控器打开目录浏览器选择。
- WebDAV 请求使用 OkHttp `PROPFIND` 列目录；播放 URL 保持 HTTP(S)，Basic Authorization 通过现有 `BackendAuthHeaders` 注入，不把密码拼进 URL。
- 播放、封面和歌词侧车文件通过 HTTP 请求读取；ID3v2 标签读取使用 HTTP Range。

### SMB

- 主机示例：`192.168.1.20`、`192.168.1.20:445` 或 `smb://192.168.1.20:445`
- 共享名称：填写 NAS/Windows 上已创建的共享名，例如 `music`。当前版本需要手动填写共享名，不枚举服务器全部共享。
- 工作组 / 域：可选，例如 `WORKGROUP`。
- 用户名、密码：按共享权限填写；允许匿名共享时可留空。
- 音乐根目录：相对共享根目录的路径，例如 `/Music`；可通过 TV 目录浏览器选择。
- SMB 文件播放通过应用内 HTTP bridge 转换为 `http://127.0.0.1:<随机端口>/...`，服务只绑定 loopback，不监听局域网网卡。桥接支持 GET、HEAD、单段 `Range`、206 和 416 响应，供 Media3/ExoPlayer 快进与分段读取。

## 扫描与元数据

- WebDAV 与 SMB 共用 `NetworkFolderAdapter` 扫描、歌曲模型映射、专辑/歌手/流派聚合逻辑。
- 当前识别扩展名：MP3、M4A、M4B、AAC、FLAC、WAV、OGG、OGA、OPUS、WMA、APE、ALAC、AIFF、AIF、DSF、DFF。
- 目前直接解析 ID3v2.3 / ID3v2.4 常见文本帧、USLT 歌词和 APIC 封面；同目录的 `cover.jpg` / `folder.jpg` 等图片和 `.lrc` 侧车歌词也会识别。
- **元数据兼容边界：** 当前并未实现 FLAC Vorbis Comment、MP4 `ilst`、Ogg Vorbis Comment 等非 ID3 标签解析。此类文件仍会被扫描和播放，但专辑/歌手/年份/流派等字段可能回退到文件名、目录名或“未知艺术家”。
- 每次连接后的首次曲库读取会递归扫描；同一适配器会缓存扫描结果，避免专辑、歌手、歌曲并行加载时重复遍历。断开或重连后重新扫描。
- 为限制电视设备内存和网络并发，扫描最多深入 32 层、最多收集 80,000 个文件/目录条目，ID3 读取并发数为 4，单个 ID3 标签最多读取 16 MiB。
- 当前扫描缓存是连接会话内存缓存，尚未将 WebDAV/SMB 索引持久化到 Room，也尚未实现基于 `lastModified` 的增量扫描；详细逐首扫描进度界面也尚未接入。

## SMB 协议兼容与安全

- 客户端协商范围固定为 SMB 2.02 到 SMB 3.0，不接受 SMB1。仅支持 SMB1 的旧设备无法连接。
- `jcifs-ng` 对 SMB3 支持并非所有高级能力都完整；需要 SMB3 加密的共享当前不保证可用。若 NAS 可配置 SMB 最低/最高版本，建议开启 SMB2 或 SMB3，并关闭 SMB1。
- SMB 用户名/密码由独立 `CIFSContext` 持有；WebDAV Basic Auth 不写入 URL。应用不关闭全局 OkHttp 连接池。
- 本地 SMB HTTP bridge 使用随机 192-bit 访问令牌和随机端口，并绑定 `127.0.0.1`；断开适配器时停止服务并清理资源表。

## 关键实现位置

- `backend/files/WebDavClient.kt`：WebDAV `PROPFIND`、XML 响应、Range 与 Basic Auth。
- `backend/files/SmbClient.kt`：SMB 目录、文件元数据、Range 随机访问。
- `backend/files/SmbHttpStreamServer.kt`：loopback HTTP / Range 播放桥接。
- `backend/files/NetworkFolderAdapter.kt`：共享扫描、ID3、歌曲/专辑/歌手/流派、歌词与封面映射。
- `backend/files/WebDavAdapter.kt`、`SmbAdapter.kt`：两个 `BackendAdapter` 实现。
- `ui/screens/ServerConnectScreen.kt`：TV 遥控器连接表单与目录浏览器。
- `data/prefs/ServerPrefs.kt`：根目录、SMB share/domain 配置保存；密码仍沿用原 AES-GCM 加密流程。

## 本地验证

此源代码包的 `gradle/wrapper/gradle-wrapper.properties` 指向项目维护者 Windows 开发机上的离线 Gradle 包（`file:///D:/tools/gradle-9.5.0-bin.zip`）。这是仓库 `AGENTS.md` 明确要求保留的本地配置；在没有该 Windows 路径的 Linux 容器中无法启动 Gradle Wrapper。当前执行环境也未配置 Android SDK，因此本次只能进行源码静态检查，不能宣称 Android 编译或 TV 真机测试已通过。请在项目原开发环境使用 Android Studio Sync 后执行 `:app:compileDebugKotlin`、单元测试与 TV 真机验证。
