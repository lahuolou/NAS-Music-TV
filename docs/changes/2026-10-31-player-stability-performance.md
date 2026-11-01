# NasmusicTV 播放链路稳定性与性能优化记录

- **change_id**：`NAS-TV-2026-10-31-PLAYER-STABILITY-01`
- **日期**：2026-10-31
- **基线**：`NAS-Music-TV-TV-原有代码优化版.zip`（包含此前 WebDAV/SMB 接入与 §10.280 本地音乐索引修复）
- **目标平台**：Android TV；遥控器操作与电视播放场景优先，不新增手机 UI。
- **变更类型**：播放器/网络/缓存逻辑修复、队列状态一致性优化、测试与文档补充。
- **新增依赖**：无。
- **删除文件**：无。

## 1. 给维护者的摘要

本轮重点不是改变播放器功能，而是降低切歌、搜索取消、Media3 媒体浏览回调与队列变更时的状态竞态。

1. **取消语义修复**：网络搜索、播放链接解析、同源候选重搜与网易原生优先搜索，不再把 `CancellationException` 当成普通网络失败吞掉；取消后不应继续跑 fallback。
2. **Media3 Future 生命周期绑定**：播放器服务中的异步浏览/搜索/媒体解析任务与 `SettableFuture` 双向关联。控制器取消结果时取消协程；服务作用域取消时也结束 Future；Completion hook 还覆盖协程体尚未开始执行的情形。
3. **网络 URL 缓存治理**：缓存 TTL 使用单调时钟；全表维护从每次解析改为最多每分钟一次；当前 key 仍即时检查 TTL；缓存数量保持上限；重新注册/注销音乐源时清理该源缓存；旧服务实例的迟到响应不能写回新实例缓存。
4. **队列和随机播放一致性**：播放队列入口先复制稳定快照并校验起始索引；随机历史封装为纯 Kotlin 组件；队列重排/删除/替换后重置索引历史；静默推进和下一首预览使用同一套未播放历史。
5. **浏览与搜索缓存**：`BrowseCache.songsOf()` 整批只加锁一次并刷新 LRU；MediaLibrary 搜索缓存为有界 LRU，结果列表取快照，TTL 从查询完成时开始计时。

本轮没有修改 WebDAV/SMB 协议实现、电视页面布局、Manifest 权限策略、MediaSession `onConnect` 安全边界、Gradle/Release 签名配置。

## 2. 变更明细（按文件）

### 2.1 修改文件

#### `app/src/main/java/com/nasmusic/tv/backend/network/NetworkMusicManager.kt`

- **动作**：修改。
- **为什么**：播放 URL/负面探测缓存的旧维护逻辑每次解析都遍历并排序缓存；服务运行时替换后，旧解析请求还可能晚到并写入旧 URL；多个 suspend 入口的取消也必须传递给调用方。
- **改动**：
  - 用 `System.nanoTime()` 作为内存 TTL 时钟，避免系统时间校准影响缓存时长。
  - 新增 `maintainCachesIfDue()`，全表过期清理限频为每 60 秒一次；每次读取仍针对当前 key 精确检查 TTL。
  - 缓存仅在超过容量上限时排序淘汰，正常命中路径不排序。
  - 通过 `clearCachesForSource()` 在注册/注销源时清除该源的 URL 与 DEAD 探测缓存。
  - 注册/注销与解析结果提交共用 `serviceCacheMutationLock`，把“服务身份检查 + 缓存提交”串行化，消除 check-then-act 竞态；源已被替换时丢弃旧解析结果，不返回旧 URL；TTL 从响应完成时开始。
  - `isProbedDead()` 对指定 key 即时检查 TTL，不依赖下一轮全表维护。
  - `search`、URL/歌词/封面/歌单/艺人查询及跨源降级的 `CancellationException` 单独捕获并重新抛出，不再进入 fallback。
- **兼容性**：公开 API、缓存 key 结构、5 分钟业务 TTL、500 条容量上限与音质档位语义保持不变；仅在音乐源被同时替换时拒绝旧实例的迟到解析结果。

#### `app/src/main/java/com/nasmusic/tv/backend/network/CandidatePicker.kt`

- **动作**：修改。
- **为什么**：同源重搜将搜索/候选解析异常视为“没有候选”是合理的，但不能把用户取消、切歌或父协程取消也当成普通失败。
- **改动**：搜索和候选 URL 解析的 catch 链增加 `CancellationException` 分支并重新抛出；普通网络异常仍按原有方式降级为空候选/下一个候选。
- **兼容性**：标题匹配规则、最多 3 个候选、成功候选返回结构均不变。

#### `app/src/main/java/com/nasmusic/tv/backend/network/MetingApiService.kt`

- **动作**：修改。
- **为什么**：网易优先搜索原生接口失败时会降级到 Meting 端点；取消操作不应被误判为网易接口故障并继续请求其他端点。
- **改动**：`searchNativeFirst()` 对原生搜索的 `CancellationException` 显式重新抛出；只有普通异常才走原有 Meting fallback。
- **兼容性**：网易原生正常命中与普通错误 fallback 行为保持不变。

#### `app/src/main/java/com/nasmusic/tv/player/PlaybackService.kt`

- **动作**：修改。
- **为什么**：Media3 回调通过 Future 交付异步结果；只在协程体中捕获取消，无法覆盖 `serviceScope` 已取消、协程还未启动的竞态。另有无 UI 播放 URL 解析路径可能吞掉取消异常。
- **改动**：
  - `bindFutureCancellation()` 增加 Job completion hook。Future 被取消时取消 Job；Job 因取消结束且 Future 尚未完成时取消 Future。
  - `resolveStreamUrlWithoutUi()` 不再使用 `runCatching` 吞掉取消；普通异常仍可走原有 UI fallback，主动取消则直接结束旧解析任务。
  - MediaLibrary 搜索缓存以同步保护的 access-order `LinkedHashMap` 实现 LRU，最多保留 64 个 query。
  - 搜索结果复制成列表快照，缓存 TTL 从搜索完成时间开始，而不是请求开始时间。
  - 服务销毁时清空搜索缓存与浏览缓存。
- **兼容性**：Media3 回调的返回结构、超时值、搜索结果语义和 Android TV/车机浏览接口不变。

#### `app/src/main/java/com/nasmusic/tv/player/BrowseCache.kt`

- **动作**：修改。
- **为什么**：批量恢复 MediaItem 时，逐首调用 `songOf()` 会对同一把锁进行多次竞争。
- **改动**：`songsOf()` 改为整批只获取一次锁，保持输入顺序、过滤空/未知 ID，并通过 map 读取继续刷新 access-order LRU。
- **兼容性**：返回值类型、ID 前缀规则和 LRU 容量策略不变。

#### `app/src/main/java/com/nasmusic/tv/player/PlayerManager.kt`

- **动作**：修改。
- **为什么**：随机历史直接存索引，重排/删除后旧索引会指向不同歌曲；播放队列还可能接收外部可变 List 或越界起始索引。
- **改动**：
  - `playQueue()` 对输入队列创建快照，起始索引统一钳制到合法范围，并在一次 StateFlow 更新中发布 queue/currentIndex/currentSong。
  - `syncQueueFromExternal()` 与 `restoreQueue()` 同样使用快照并重置随机历史。
  - 随机历史迁移到 `ShuffleHistory`，随机下一首、IDLE 恢复、静默推进与随机预览共享未播放历史。
  - 队列重排、删除、替换和清空时按新的索引语义重置/清除历史，避免旧索引错配。
  - 随机轮次结束时保留当前歌曲作为已播放项，下一轮不立即重复当前曲。
- **兼容性**：播放模式常量、PlayerManager 对外 API、ExoPlayer 队列操作方式保持不变；随机顺序本身仍然是随机的，但更少出现同一轮重复/误跳。

### 2.2 新增文件

#### `app/src/main/java/com/nasmusic/tv/player/ShuffleHistory.kt`

- **动作**：新增。
- **职责**：纯 Kotlin 随机播放索引历史与队列起始索引归一化；与 Android/Media3 解耦，便于快速单测。

#### `app/src/test/java/com/nasmusic/tv/player/ShuffleHistoryTest.kt`

- **动作**：新增。
- **覆盖**：空队列、起始索引钳制、已播放排除、队列缩小时丢弃失效索引、随机轮次衔接、队列变更后重置。

#### `app/src/test/java/com/nasmusic/tv/player/BrowseCacheTest.kt`

- **动作**：新增。
- **覆盖**：批量查询保持顺序、完整/裸 mediaId 兼容、空/未知 ID 丢弃、批量访问刷新 LRU 次序。

### 2.3 修改测试文件

#### `app/src/test/java/com/nasmusic/tv/backend/network/CandidatePickerTest.kt`

增加搜索被取消、候选解析被取消时必须向上传播的回归用例；普通 IOException 仍然返回无替代候选。

#### `app/src/test/java/com/nasmusic/tv/backend/network/NetworkMusicManagerTest.kt`

增加搜索取消不再 fallback、URL 解析取消不再伪装失败、相同 sourceId 重新注册后旧 URL 缓存失效，以及旧服务在途解析被替换后必须丢弃且不能污染新缓存的回归用例。

#### `app/src/test/java/com/nasmusic/tv/backend/network/NeteasePrioritySearchTest.kt`

增加 MockWebServer 延迟原生响应的在途取消用例，验证网易原生搜索取消后不再请求 Meting fallback；测试不访问真实公网。

### 2.4 文档文件

#### `CHANGELOG.md`

- **动作**：修改。
- **原因**：让版本维护者在查看未发布变更时能快速看到本轮主要稳定性修复。
- **改动**：在 `[Unreleased] / Fixed` 增加播放取消与 Future 生命周期、队列随机历史、网络/媒体浏览缓存三条摘要。

#### `docs/technical-overview.md`

- **动作**：修改。
- **原因**：项目技术总览需要保留本轮实现决策与兼容性约束，避免后续 AI/开发者只看到代码而丢失设计背景。
- **改动**：追加 §10.281，记录取消传播、Future 双向绑定、缓存串行化、随机队列历史、验证边界和范围约束。

### 2.5 新增文档

#### `docs/changes/2026-10-31-player-stability-performance.md`

- **动作**：新增。
- **用途**：本轮面向开发者/维护者的详细记录，包含背景、每个文件的原因与改动、验证方法、限制及后续检查点。

#### `docs/changes/2026-10-31-player-stability-performance.json`

- **动作**：新增。
- **用途**：机器可读的结构化清单，包含 `change_id`、基线、平台范围、文件路径、增删改动作、原因、改动项和验证状态，便于 AI 检索和后续自动化分析。

## 3. 文件增删改统计

| 动作 | 数量 | 说明 |
|---|---:|---|
| 新增 | 5 | 1 个纯逻辑生产文件、2 个测试文件、2 个变更文档 |
| 修改 | 11 | 6 个生产 Kotlin 文件、3 个测试文件、`CHANGELOG.md`、`docs/technical-overview.md` |
| 删除 | 0 | 没有删除项目文件 |
| 新增依赖 | 0 | 没有改 Gradle 依赖 |
| UI/Manifest 改动 | 0 | 不增加手机 UI，不改 TV 页面和系统权限 |

## 4. 验证记录

### 已执行的独立验证

- `ShuffleHistory.kt` 用本环境 Kotlin 编译器独立编译，并运行纯 Kotlin harness：**7 项断言通过**。
- `Song.kt` + `BrowseCache.kt` 独立编译，并运行批量查询/LRU harness：**4 项断言通过**。
- `NetworkMusicManager.kt` + `CandidatePicker.kt` 连同实际模型/接口文件，在仅为 Android `R` 与日志门面提供最小编译桩的条件下独立 Kotlin 编译：**通过**。
- 使用真实 `NetworkMusicManager`、真实协程和可挂起 Fake 服务运行“解析在途时重新注册 sourceId”场景：**3 项断言通过**（旧 URL 被丢弃，新服务 URL 成功返回）。
- JSON 清单解析成功，清单引用的 16 个项目文件全部存在；文件差异清点基于上一轮交付 ZIP 与当前工作树逐文件 SHA-256 对比；本轮没有删除文件。

### 尚未执行的验证

- Android `compileDebugKotlin` / `assembleDebug`：未执行。
- 完整 Robolectric `testDebugUnitTest`：未执行。
- 真机/电视盒子播放、切歌、暂停恢复、NAS 异常断线联调：未执行。

原因：当前执行环境没有 Android SDK；工程 Gradle Wrapper 配置为开发机 Windows 本地分发包 `D:/tools/gradle-9.5.0-bin.zip`，本环境无法取得该分发包。新增/修改的 Android/Robolectric 测试已写入项目，但不能标记为已通过。

## 5. 建议在 Android Studio 上验证

在项目原开发 Windows 环境中打开工程，先确认原 Wrapper 路径可用，然后执行：

```powershell
.\gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
.\gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
.\gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:lintDebug
```

之后重点在电视设备验证：

- 搜索过程中切换页面/取消，确认不再继续请求 fallback。
- 连续快速切歌、播放失败重试、源重新配置后播放同一歌曲。
- 随机播放连续一轮、队列重排/删除后继续播放。
- Android TV 媒体浏览/搜索过程中关闭服务或取消控制器请求。
- NAS/WebDAV/SMB 播放时快进、拖动、切歌与网络恢复。

## 6. AI 检索与后续维护约定

本文件用明确的键名和路径描述变更；同目录 `2026-10-31-player-stability-performance.json` 提供机器可读的同源清单。后续 AI/维护者应：

1. 先读本文件的 `change_id`、`baseline`、`files` 与 `verification`，再读取列出的源码。
2. 不要把“未执行”的 Gradle/Robolectric/硬件联调描述成通过。
3. 不要将既有 §10.280 本地音乐扫描修复误记为本轮新增；它属于本轮基线。
4. 修改随机队列索引语义时同步检查 `ShuffleHistory` 和 `PlayerManager` 中所有队列变更入口。
5. 修改 Future/协程生命周期时保留 Future→Job 与 Job→Future 双向取消绑定。
