# WebDAV / SMB 音乐源接入 — A-G 核对报告

> 生成时间：2026-10-31
> 基线版本：（versionCode=196, commit=ec7641e）
> 目的：在动手写代码前，盘点现有项目对 WebDAV/SMB 新音乐源的可复用资产与缺口。
> 约束：不改代码，只输出核对结果与差异。

---

## A. 技术栈、模块结构、依赖版本

| 类别 | 现状 | WebDAV/SMB 影响 |
|---|---|---|
| 语言 / 平台 | Kotlin 2.3.10, minSdk 22, targetSdk 34, Java 17 | 无 |
| UI | Jetpack Compose for TV（tv-material alpha10），手动 `when(currentScreen)` 切屏，无 Navigation-Compose | 需加新连接表单 + 目录浏览组件 |
| 播放器 | Media3 1.2.1（exoplayer / session / ui / datasource-okhttp） | 无 |
| 网络 | OkHttp 4.12.0 + logging-interceptor，**裸 OkHttp，无 Retrofit** | WebDAV PROPFIND 直接用 OkHttp |
| JSON | Gson 2.10.1 | 无 |
| 本地存储 | DataStore Preferences 1.1.1 + Room 2.7.1（KSP） | 扫描结果可落 Room |
| 图片 | Coil 2.5.0 | 封面 URL 直接喂 Coil |
| 本地 HTTP | nanohttpd 2.3.1（手机遥控用） | SMB 可复用它做本地转发 |
| **ID3 读** | `jaudiotagger 3.0.1`（MediaTagWriter 写）+ 自研 `Id3v2Parser.kt`（Range 读 USLT/APIC） | **直接复用** |
| **XML 解析** | **无**（全部 JSON） | **需新增**——PROPFIND 响应是 XML |
| **SMB 库** | **无**（无 jcifs-ng / smbj） | **需新增依赖** |
| **WebDAV 库** | **无**（无 sardine / webdav4） | **需新增依赖，或裸 OkHttp + 手写 XML 解析** |

### 模块结构（关键包）

```
app/src/main/java/com/nasmusic/tv/
├── backend/
│   ├── BackendAdapter.kt          # 统一后端接口（25+ 方法）
│   ├── BackendRegistry.kt         # 手动 DI，按 backendType 创建 adapter
│   ├── BackendAuthHeaders.kt      # streamHeaders host 匹配注入
│   ├── impl/                      # 5 个 HTTP API 后端
│   │   ├── JellyfinAdapter.kt
│   │   ├── NavidromeAdapter.kt
│   │   ├── SubsonicAdapter.kt
│   │   ├── FeiniuAdapter.kt
│   │   └── DaoliyuAdapter.kt
│   ├── network/baidu/            # 百度网盘（HTTP Range + ID3 解析参考实现）
│   ├── local/                     # MediaStore 本地扫描 + Room 索引
│   └── download/                  # MediaTagWriter（jaudiotagger）
├── player/
│   ├── PlaybackService.kt          # MediaLibraryService，构建 ExoPlayer
│   └── BaiduHttpDataSourceFactory.kt  # DataSource.Factory 注入点
├── data/
│   ├── model/ServerConfig.kt      # backendType/baseUrl/username/password/accessCode
│   └── prefs/AppPreferences.kt    # DataStore 子域拆分
└── ui/screens/
    └── ServerConnectScreen.kt     # 连接表单（后端类型选择 + 账号密码）
```

---

## B. 现有音乐来源抽象

### B.1 接口（BackendAdapter）

| 类别 | 方法 | WebDAV/SMB 需求 |
|---|---|---|
| 连接 | `initialize(baseUrl, apiToken, username, password)` / `testConnection()` / `logout()` / `close()` | 可复用签名 |
| 曲库 | `getAlbums()` / `getAlbumSongs(albumId)` / `getArtists()` / `getArtistSongs()` / `getSongs(limit, offset)` / `getSongsByIds(ids)` / `searchSongs(query)` / `getRecentSongs()` | 需实现：扫描目录后内存聚合 |
| 播放 | `getStreamUrl(songId): String` | WebDAV 直接返回 HTTP URL；SMB 需本地转发 |
| 封面 | `getCoverUrl(songId)` / `getCoverUrlCandidates(song)` | WebDAV 返回 cover.jpg URL 或 ID3 APIC Range URL |
| 歌词 | `getLyrics(songId): String?` | 可复用 LRC 侧车 / ID3 USLT |
| 认证头 | `streamHeaders: Map<String,String>`（默认空，飞牛覆写） | **WebDAV Basic Auth 走这里** |
| 扩展 | playlists / favorites / ratings / genres / scrobble（默认空） | 默认空实现即可 |

### B.2 已有 5 个实现

全部是 **HTTP API 模式**（服务端有数据库，客户端调 REST API）：
- Jellyfin / Navidrome / Subsonic / 飞牛 fnOS / 道理鱼

**没有文件系统模式**的实现——WebDAV/SMB 是第一个「自己扫描目录、自己解析 ID3」的后端。

### B.3 播放器如何消费

- `Song.streamUrl` → ExoPlayer `MediaItem.fromUri(streamUrl)`
- 网络歌曲 streamUrl 不在解析时填，`PlayerManager.onNeedResolveStreamUrl` 信号 MainViewModel 解析后重播
- 封面走 Coil，URL 直接喂

### B.4 目录浏览

**现有代码没有目录浏览 UI**。所有后端都直接填 URL，不需要选文件夹。WebDAV/SMB 需要新增「列目录 → 选音乐根目录」交互。

---

## C. 播放器 DataSource / Header 注入

### C.1 结论

**是 Media3/ExoPlayer**（`PlaybackService` 继承 `MediaLibraryService`）。

### C.2 DataSource.Factory 链路

```
PlaybackService
  └── BaiduHttpDataSourceFactory.create(context)
        ├── OkHttpDataSource.Factory(sharedOkClient)
        │     └── Interceptor 链：
        │           ├── *.baidu.com   → UA + Referer
        │           ├── *.bilibili.* → UA + Referer
        │           └── BackendAuthHeaders.forHost(host) → 实时读 adapter.streamHeaders，host 精确匹配
        └── DefaultDataSource.Factory(context, httpFactory)
```

### C.3 WebDAV 复用方式

- **播放**：`WebDavAdapter.getStreamUrl()` 返回完整 `http://user:pass@host/path.mp3` 或 Basic Auth 走 `streamHeaders`（推荐后者，密码不进 URL 日志）
- **封面**：Coil 复用同一 `sharedOkClient`（`createOkHttpClientForCoil()`），Basic Auth 自动注入
- **SMB**：不能直接给 ExoPlayer URL——要么写自定义 DataSource，要么起本地 HTTP 转发（nanohttpd 已有）

---

## D. 配置和凭据存储

### D.1 AppPreferences

- DataStore Preferences 单例，子域拆分：`server / player / lyrics / network / baidu / download / weather / visualizer / history / playlist / queue / backup / photoWall / settings`
- `server.serverConfig: Flow<ServerConfig?>` 存当前连接

### D.2 ServerConfig

```kotlin
data class ServerConfig(
    val backendType: String,   // "jellyfin" / "navidrome" / "subsonic" / "daoliyu" / "feiniu"
    val baseUrl: String,
    val apiToken: String = "",
    val username: String = "",
    val password: String = "",
    val accessCode: String = "",  // 飞牛访问码
    val isConnected: Boolean = false,
    val displayName: String = ""
)
```

- **backendType 是字符串，不是枚举**——加新类型只需加常量
- 凭据（password/apiToken/accessCode）落盘前 `CryptoUtils` AES-GCM 加密
- 备份导出剔除凭据

### D.3 WebDAV/SMB 新增字段建议

| 字段 | ServerConfig 现有是否够 | 说明 |
|---|---|---|
| WebDAV baseUrl | ✅ baseUrl | |
| WebDAV username/password | ✅ username/password | |
| WebDAV 根目录路径 | ❌ 需加 `rootPath: String` | 默认 `/` |
| SMB host/port/share | ❌ baseUrl 可拼 smb://host/share，但端口/域要加字段 | 建议加 `smbDomain: String`（工作组） |
| SMB 根目录 | ❌ 同上 `rootPath` | |

---

## E. 添加来源 / 设置 / 目录浏览 UI

### E.1 ServerConnectScreen 现状

- 后端类型选择：**硬编码 5 个按钮**（Jellyfin/Navidrome/Subsonic/道理鱼/飞牛）
- 表单：baseUrl + 端口自动填（8096/4533/4533/4000/5666）、username、password、apiToken（按类型显隐）、accessCode（仅飞牛）
- 「测试连接」按钮 → `testConnection()`
- **无目录浏览组件**

### E.2 需要新增的 UI

1. **后端类型按钮**：加「WebDAV」「SMB」两个
2. **表单差异**：
   - WebDAV：URL + 端口（5005 / 443）+ username + password + 根目录路径
   - SMB：主机 + 端口（445）+ 共享名 + 工作组 + username + password + 根目录路径
3. **目录浏览组件**（关键缺口）：
   - WebDAV：PROPFIND 列目录 → 可点进子目录 → 选「设为音乐根目录」
   - SMB：jcifs-ng 列共享 → 列目录 → 选根目录
4. **扫描进度**：首次连接后递归扫描，需要进度条 UI（已扫 N 首 / 正在扫 xxx）

---

## F. 网络客户端、XML 解析、ID3 解析

### F.1 可复用资产

| 能力 | 现有实现 | WebDAV 复用方式 |
|---|---|---|
| OkHttp 单例 | `BaiduHttpDataSourceFactory.sharedOkClient`（连接池/线程池/Interceptor） | WebDAV client 直接复用，加 Basic Auth 拦截器分支 |
| Range 请求读文件头 | `BaiduCoverProvider.downloadRange()` / `BaiduLyricsProvider` | **完全一致**——WebDAV 文件也是 HTTP Range，直接复用 |
| ID3v2 轻量解析 | `Id3v2Parser.kt`（USLT/APIC） | 直接复用 |
| ID3 全功能 | `jaudiotagger 3.0.1`（MediaTagWriter） | 扫描时读 tag 用 AudioFileIO.read()，但需本地文件——WebDAV 要先 Range 读头 |
| 封面提取 | `EmbeddedCoverExtractor`（本地文件）/ `BaiduCoverProvider`（Range） | WebDAV 走 Range 分支 |
| 本地索引 | Room `LocalMusicDatabase`（local_songs 表，version 3） | 表结构可参考，但当前是 MediaStore 专用（contentUri），WebDAV 要存 HTTP URL |
| 凭据加密 | `CryptoUtils` AES-GCM | 直接复用 |
| 本地 HTTP 服务 | nanohttpd 2.3.1 | SMB 转发用 |

### F.2 缺口

| 能力 | 缺口 | 补法 |
|---|---|---|
| XML 解析 | 无 | `XmlPullParser`（Android 内置，零依赖）解析 PROPFIND 响应 |
| WebDAV PROPFIND | 无 | 手写 OkHttp 请求 + XML 解析（不引第三方库） |
| SMB 协议 | 无 | 加 `eu.agno3.jcifs:jcifs-ng:2.1.10` |
| 文件扫描器 | 无（MediaStore 是系统扫的） | 递归遍历目录 + 过滤音频扩展名 + ID3 解析构建 Song 对象 |
| 增量扫描缓存 | 无（Room 表存在但没 WebDAV 用） | 用 Room 存扫描结果，按 path + lastModified 增量 |

---

## G. 对照 docs/webdav-media-source-design.md

### G.1 文档状态

**`docs/webdav-media-source-design.md` 不存在**——全项目 Glob 零命中。

需要你提供：
1. 文档路径（我再读），或
2. 直接把文档内容贴过来，我逐条核对。

### G.2 基于现有代码预判的差异点

| 设计假设 | 是否成立 | 差异说明 |
|---|---|---|
| WebDAV 走 BackendAdapter 接口 | ✅ 成立 | 加 `TYPE_WEBDAV` 常量 + `WebDavAdapter` 实现，无接口改动 |
| WebDAV 播放 URL 直接给 ExoPlayer | ✅ 成立 | `getStreamUrl()` 返回 HTTP URL，Basic Auth 走 `streamHeaders` |
| WebDAV 封面走 Coil | ✅ 成立 | `getCoverUrlCandidates()` 返回 cover.jpg URL + APIC Range URL |
| WebDAV 需要目录浏览 UI | ❌ 现有不成立 | ServerConnectScreen 无目录组件，需新写 |
| WebDAV 扫描结果持久化 | ⚠️ 部分成立 | LocalMusicDatabase 是 MediaStore 专用，需加 source 字段或新建表 |
| WebDAV 凭据加密存储 | ✅ 成立 | CryptoUtils 直接用 |
| SMB 可复用同一套扫描/ID3 | ❌ 不成立 | SMB 不是 HTTP，不能 Range 请求；要 jcifs-ng + 本地 HTTP 转发或自定义 DataSource |
| SMB 走 BackendAdapter 接口 | ⚠️ 勉强成立 | 接口方法签名兼容，但 `getStreamUrl()` 要返回本地转发 URL（nanohttpd） |
| 已有 XML 解析库 | ❌ 不成立 | 全部 JSON，需新增 XmlPullParser 解析 PROPFIND |
| 已有 WebDAV/SMB 依赖 | ❌ 不成立 | build.gradle.kts 无 jcifs-ng / sardine |

---

## 下一步建议

1. **先补设计文档**：把 `docs/webdav-media-source-design.md` 写出来（或贴过来），我逐条核对 G 项
2. **MVP 范围**：建议先做 WebDAV（OkHttp 现成、ExoPlayer 直接播 URL、Basic Auth 走 streamHeaders），SMB 下一轮
3. **新增依赖最小集**：
   - WebDAV：**零新依赖**（OkHttp + XmlPullParser 都有）
   - SMB：`eu.agno3.jcifs:jcifs-ng:2.1.10`
4. **必须新写的代码**：
   - `WebDavAdapter.kt`（~500 行：PROPFIND 列目录 + 扫描 + ID3 解析 + Song 聚合）
   - `WebDavClient.kt`（OkHttp 封装 PROPFIND + GET）
   - ServerConnectScreen 加 WebDAV 表单 + 目录浏览组件
   - Room 表加 WebDAV 索引（或新建 WebDavSongEntity）
