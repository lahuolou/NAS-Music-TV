# NasmusicTV — 大曲库与封面加载性能优化

- 变更 ID：`2026-10-31-tv-large-library-cover-optimization`
- 基线：`NAS-Music-TV-播放器稳定性性能优化版.zip`（上一轮 WebDAV/SMB + 播放稳定性优化版）
- 本轮目标：Android TV 大曲库性能、封面加载响应、重复 IO 与临时内存分配
- 适用范围：Android TV / 遥控器使用场景；没有增加手机专用 UI
- 依赖变化：无新增 Gradle 依赖
- 文件统计：新增 4、修改 8、删除 0（共 12 个文件记录）

## 1. 为什么要做这一轮

上一轮主要修复播放协程取消、Media3 Future 生命周期、网络缓存和随机队列一致性。本轮继续检查大曲库与图片路径时，发现两个容易在普通小曲库里隐藏、但会随曲库规模放大的问题：

1. 歌曲列表行/卡片组合时，使用 `remember { EmbeddedCoverExtractor.extractCoverUri(...) }` 同步读取音频文件头并解析 ID3/APIC。`remember` 只负责记忆值，不会切换线程。电视滚动列表时这类磁盘 IO 可能占用 Compose 主线程。
2. 歌手歌曲索引和搜索合并反复复制列表、重新建立 ID 集合。单次成本看起来不大，但热门艺术家或大量搜索结果会放大临时对象数量与遍历次数。

另外，播放页、列表行和预取器可能同时请求同一文件的嵌入封面；快速切歌时旧预取也可能继续占用网络与图片解码资源。

## 2. 本轮做了什么

### 2.1 封面提取从 Compose 主线程移至 IO

文件：`app/src/main/java/com/nasmusic/tv/ui/components/song/UnifiedSongRow.kt`

- 新增私有 Composable `rememberEffectiveCoverUrl()`。
- 使用 `produceState` 管理异步提取任务，在 `Dispatchers.IO` 中执行 `EmbeddedCoverExtractor.extractCoverUri()`。
- 行模式和卡片模式共用该 helper，不再在组合期直接读取音频文件。
- 首帧仍显示已有的 `song.coverUrl`；内嵌 APIC 提取成功后替换为缓存文件 URI。
- 已下载歌曲的旁路封面检查也在 IO 内执行。
- Song ID、路径、封面 URL、下载状态变化会触发重新解析；Lazy item 离开组合或被复用时，producer 跟随 Compose 协程取消。

行为影响：现有封面优先级和显示方式不变；只改变文件读取执行线程和结果到达时机。首帧可能短暂显示已有封面/占位图，再切换到内嵌封面。

### 2.2 封面候选构建成为 IO suspend API

文件：`app/src/main/java/com/nasmusic/tv/ui/viewmodel/MainViewModel.kt`

- `getCoverCandidates(song)` 从普通同步函数改为 `suspend` 函数。
- 内部使用 `withContext(Dispatchers.IO)`，覆盖文件存在性检查、APIC 提取以及候选 URL 组装。
- 保持现有候选来源、顺序与 `distinct()` 去重行为。
- 歌手索引更新委托给新增的 `ArtistSongIndex.append()`，并继续在 `artistMapsMutex` 内提交 StateFlow。

调用点：
- 切歌时并行的 `coverLoadJob`（本来就在 `viewModelScope.launch` 中）。
- `AppRoot` 当前/前后曲封面预取（改为 IO effect）。
- `VisualizerOverlay` 的切歌封面加载（本来就在 `LaunchedEffect` 中）。

行为影响：函数签名从同步变为挂起式；本项目内调用方均已处于协程上下文。若未来有新调用点，必须从协程调用，不可在 Compose 组合体内同步调用。

### 2.3 当前/前后曲封面预取有界且可取消

文件：`app/src/main/java/com/nasmusic/tv/ui/components/AppRoot.kt`

- 删除组合期 `remember { viewModel.getCoverCandidates(...) }`，避免每次组合时做磁盘或文件元数据检查。
- 当前歌曲、下一曲和上一曲候选都在 `LaunchedEffect` 中解析，并在 `Dispatchers.IO` 执行。
- Effect key 增加 `networkCoverUrl`、当前队列索引、下一曲 ID、上一曲 ID。队列重排或候选 URL 变化时重新计算预取。
- 增加 `DisposableEffect(Unit)`；AppRoot 的 `LaunchedEffect` 一开始就取消上一代请求（不等待新曲 URL 搜索完成），退出组合时再次取消尚未完成的预取请求。

文件：`app/src/main/java/com/nasmusic/tv/ui/components/CoverPrefetcher.kt`

- URL 先 trim、去空、去重，最多预取 12 个。
- 记录 Coil `Disposable`，新一代预取开始时取消上一代仍在执行的任务。
- 使用 generation token 处理“旧请求正在 enqueue，新一代已经开始”的并发窗口；过期请求句柄拿到后立即 dispose。
- `LaunchedEffect` 一进入新一代就先调用 `cancelPending()`，不会等新歌曲候选 URL/网络封面搜索完成后才取消旧曲请求。
- 提供 `cancelPending()` 给 AppRoot 生命周期回调。
- 仍使用现有 Coil ImageLoader、磁盘/内存缓存策略及 256–768 px 尺寸边界，没有新增图片库。

行为影响：快速连按切歌时过期封面不再无限留在下载/解码队列；已进入 Coil 缓存的结果保留。单次预取 URL 总量限制为 12，极端后端返回大量候选时只取前 12 个非空唯一项。

### 2.4 相同文件的内嵌封面只解析一次

文件：`app/src/main/java/com/nasmusic/tv/backend/local/EmbeddedCoverExtractor.kt`

- 新增 32 个分片锁，按文件键选择锁。
- 同一文件键的列表行、播放页和预取调用会串行进入缓存检查与 APIC 解析，第二个调用可以命中第一次的正缓存或负缓存。
- 不同文件分布到不同锁槽，仍可并发提取，不使用一把全局锁串行化所有音频文件 IO。
- 本地文件缓存键增加文件路径、`lastModified` 和 `length`。文件内容更新后，即使路径相同，也会使用新键，不继续复用旧 APIC 正缓存或“无封面”负缓存。
- `content://` 仍以原 URI 作为键；本轮没有加入 MediaStore 版本查询。

行为影响：文件路径不变但修改时间或大小变化时，会重新提取封面。缓存文件名随新缓存键变化；旧散文件由系统 cache 清理或现有缓存清理功能回收。

### 2.5 大曲库歌手索引改为纯 Kotlin 更新器

新增文件：`app/src/main/java/com/nasmusic/tv/data/model/ArtistSongIndex.kt`

新增 `ArtistSongIndex`，不依赖 Android、Compose 或 ViewModel，可单独编译和测试。

`append(previousSongArtists, previousArtistSongs, newSongs)`：

- 先浅复制顶层 Map；
- 只为本批次涉及的歌手复制歌曲列表；
- 每个已触及歌手建立一次 `songId -> list index` 索引；
- 同一 ID 的歌曲再次出现时，用最新 `Song` 替换旧对象，避免标题、专辑、封面等元数据变化后 Artist 详情继续显示旧对象；
- 歌手字段变化时，从旧歌手分组移出歌曲，再加入新分组；
- 删除标记延迟到批次结束时按歌手列表一次性过滤，避免每移除一首都执行 `removeAt` 并重建整份索引。

`mergeSearchResults(existingArtistSongs, searchResults)`：

- 每个被搜索结果触及的歌手只复制一次列表；
- 每个歌手只建立一次 `songId` Set；
- 后续结果平均 O(1) 检查是否已存在，避免每首歌曲都重新遍历已有歌曲并构造新 Set/List；
- 保留原合并优先级：已有索引歌曲优先，搜索命中只追加不存在的 songId。

文件：`app/src/main/java/com/nasmusic/tv/ui/viewmodel/MainViewModel.kt`

- 在原 `artistMapsMutex` 锁内调用 `ArtistSongIndex.append()`。
- Mutex 仍负责索引提交互斥；没有改变 StateFlow 的发布方式。

文件：`app/src/main/java/com/nasmusic/tv/ui/screens/LibraryScreen.kt`

- `displayArtistSongsMap` 搜索分支改为调用 `ArtistSongIndex.mergeSearchResults()`。
- 普通非搜索分支仍直接使用 `artistSongsMap`。

新增测试：`app/src/test/java/com/nasmusic/tv/data/model/ArtistSongIndexTest.kt`

六个用例覆盖：
1. 多艺术家拆分与索引添加，同时确保输入 Map 不被修改。
2. 相同 songId 的元数据更新会替换旧 Song。
3. 艺术家字段改变时会从旧艺术家移除并进入新艺术家。
4. 艺术家字段变空后移除空分组。
5. 搜索结果对既有歌曲及同批重复歌曲去重。
6. 合作艺人搜索合并正确，未触及分组继续共享原列表。

## 3. 文件变更明细

| 文件 | 操作 | 原因 | 修改摘要 |
|---|---|---|---|
| `ui/components/song/UnifiedSongRow.kt` | 修改 | `remember` 中有同步文件 IO | `produceState + Dispatchers.IO` 异步提取行/卡片封面 |
| `ui/viewmodel/MainViewModel.kt` | 修改 | 候选封面读文件可能发生在调用者线程；歌手索引复制开销大 | `getCoverCandidates` 改为 suspend IO；artist index 调用独立 helper |
| `ui/components/AppRoot.kt` | 修改 | 组合阶段同步读封面；预取没有清理生命周期 | 移除组合期解析；Effect 中解析当前/前后曲；Dispose 取消预取 |
| `ui/components/CoverPrefetcher.kt` | 修改 | 快速切歌会留下过期预取；候选总量无上限 | 12 URL 上限、Disposable 管理、代际取消、cancelPending |
| `backend/local/EmbeddedCoverExtractor.kt` | 修改 | 同文件重复读取/解析；路径缓存不能感知文件变化 | 32 分片锁；路径+mtime+size 缓存键 |
| `ui/screens/LibraryScreen.kt` | 修改 | 搜索结果合并逐首扫描整个歌手列表 | 调用线性 ID 集合合并器 |
| `data/model/ArtistSongIndex.kt` | 新增 | 索引逻辑耦合 ViewModel/界面，缺少纯逻辑测试入口 | 纯 Kotlin append 与 mergeSearchResults |
| `test/.../ArtistSongIndexTest.kt` | 新增 | 保护增量更新和搜索合并语义 | 新增 6 个测试方法 |
| `CHANGELOG.md` | 修改 | 项目变更缺少版本记录 | 增加本轮封面与大曲库优化摘要 |
| `docs/technical-overview.md` | 修改 | 项目技术总览需保留实施事实 | 增加 §10.282 |
| `docs/changes/2026-10-31-tv-large-library-cover-optimization.md` | 新增 | 方便维护者理解逐文件改动 | 本文件 |
| `docs/changes/2026-10-31-tv-large-library-cover-optimization.json` | 新增 | 方便 AI、脚本按字段读取改动 | 文件动作、原因、修改、测试和限制的机器可读清单 |

**文件统计**：代码文件新增 2、修改 6；项目文档新增 2、修改 2；删除 0。全部新增/修改文件 12 个。

## 4. 验证与限制

### 已执行

- 本机 Kotlin/JVM 编译：`Song.kt`、`ArtistSplitter.kt`、`ArtistSongIndex.kt` 编译通过。
- 新增 `ArtistSongIndexTest.kt`：使用轻量 JUnit API stub 编译测试源码并逐一执行 6 个测试方法，6/6 通过。该步骤验证实际测试方法与算法，但不是 Gradle/JUnit/Robolectric 测试任务。
- 独立行为 harness：实际调用 `ArtistSongIndex` 执行 7 项断言，7/7 通过。
- 随机批次对照：将实际 `ArtistSongIndex` 与朴素参考模型对比 250 批随机新增、同 ID 更新、合作艺人变更与移除操作（40 个唯一 songId），每批两种索引完全一致。
- Android/Coil 桩编译：实际 `EmbeddedCoverExtractor.kt` 与 `CoverPrefetcher.kt` 分别在最小 Android/ID3/Coil API stub 下通过 Kotlin 编译；仅用于语法/API 使用形态检查，不等同于依赖真实 AAR 的 Android Gradle 编译。
- 静态调用点核对：所有 `EmbeddedCoverExtractor.extractCoverUri()` UI/VM 调用位于 IO 路径；所有 `getCoverCandidates()` 调用位于协程上下文。
- 基线 ZIP 与本轮工作目录差异核对：无意外删除；本轮无 Gradle 依赖变化。

### 未执行

- Android Gradle `compileDebugKotlin`、完整单元测试/Robolectric、Lint：当前执行环境没有 Android SDK；项目 Wrapper 的 Gradle 发行包 URL 指向开发机本地 `D:/tools/gradle-9.5.0-bin.zip`，此环境无法拉起项目 Gradle 构建。
- Android TV 真机滚动、快速切歌、网络弱网及 NAS 大曲库压测：未连接电视和 NAS 测试环境。

### 后续需要在 Android Studio / TV 实机验证

1. 打开曲库歌曲列表快速上下滚动，观察首帧占位后内嵌封面异步替换，不应出现明显掉帧。
2. 连续快速按下一曲/上一曲，确认旧预取取消，当前歌曲封面不会显示成上一首。
3. 打开/关闭播放页、Visualizer，检查页面退出后没有残余封面预取。
4. 对同一路径本地歌曲修改 ID3/APIC 后再次显示，确认 mtime 或文件大小改变能让封面缓存重新提取。
5. 用多艺术家歌曲和重复 songId 的曲目验证歌手详情与搜索合并。
6. 使用 2 万、5 万首 NAS 曲库分别观察首次扫描、艺术家页、搜索和播放全部的耗时/内存峰值。

## 5. 安全边界与兼容性

- 无新增网络端点、权限、存储格式或凭据处理。
- 无新增 Gradle 依赖。
- 不修改 Media3 播放队列或音频解码路径。
- 不更改 WebDAV/SMB 协议实现。
- 不增加手机专用 UI；本轮仅针对现有电视曲库和播放页面的执行路径。
- 本轮没有删除文件。
