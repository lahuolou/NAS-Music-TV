package com.lnh.nasmusictv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.platform.LocalContext
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.ui.viewmodel.DownloadViewModel
import com.lnh.nasmusictv.data.model.ServerConfig
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.data.model.UiState
import com.lnh.nasmusictv.ui.LocalNavigateBackHandler
import com.lnh.nasmusictv.ui.screens.NowPlayingScreen
import com.lnh.nasmusictv.ui.screens.LocalTopBarReservedPx
import com.lnh.nasmusictv.ui.screens.PlaylistPickerDialog
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.components.branches.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lnh.nasmusictv.ui.components.song.LocalCurrentSongId
import com.lnh.nasmusictv.ui.components.common.ConnectedBadge
import com.lnh.nasmusictv.ui.viewmodel.MainViewModel
import com.lnh.nasmusictv.data.model.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppRoot(
    viewModel: MainViewModel,
    isImmersiveMode: androidx.compose.runtime.MutableState<Boolean>,
    onConnect: (ServerConfig) -> Unit,
    // 用户活动信号（MainActivity 在按键/触摸时 +1）。
    // 播放页播放中无操作 5s 后自动隐藏顶部导航；任意操作立即恢复。
    userActivityTick: Long = 0L,
    // 2026-10-30：沉浸隐藏态上报给 Activity —— BACK 键在隐藏态要先"唤醒控件"
    // 而不是直接弹退出确认（极简遥控器分层返回：弹层 → 隐藏态 → 上一级）。
    onIdleHiddenChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isTV = remember {
        // 与 MainActivity/NasMusicApp/TextInputDialog 一致：很多非认证 TV 盒子
        // 只上报 android.hardware.type.television 而无 leanback 特性，
        // 单查 leanback 会把 TV 误判为手机（手机端支持引入的回归，
        // 曾导致 K 歌/MTV 页手机遥控二维码因 remoteControlUrl 被置 null 而消失）
        context.packageManager.hasSystemFeature("android.software.leanback") ||
            context.packageManager.hasSystemFeature("android.hardware.type.television")
    }
    val currentScreen by viewModel.navVM.currentScreen.collectAsState(initial = Screen.Home)
    val playerState by viewModel.playerVM.playerState.collectAsState()
    val currentSong = playerState.currentSong
    // 多码率：当前生效档位 = 单曲覆盖 ?: 全局默认（方案 §2.3 两级模型）
    val globalQualityTier by viewModel.prefs.player.qualityTier.collectAsState(initial = 0)
    val qualityTier = remember(currentSong, globalQualityTier) {
        currentSong?.let { viewModel.effectiveQualityFor(it) } ?: globalQualityTier
    }
    val isPlaying by viewModel.playerVM.isPlaying.collectAsState(initial = false)
    val playMode by viewModel.playerVM.playMode.collectAsState(initial = com.lnh.nasmusictv.data.model.PlayMode.SEQUENTIAL)
    // §7.5 状态兜底：播放错误（null = 正常）。低频流（仅在真正的播放失败时发一次），
    // 顶层收集安全 —— 不像进度流那样每秒驱动全树重组。
    val playerError by viewModel.playerVM.playerError.collectAsState(initial = null)
    // F-2（修复）：progress/duration 不再顶层收集——PlayerManager 的进度由 1000ms
    // Handler 轮询驱动，顶层收集会每秒驱动 AppRoot 全树重组（含 LazyColumn 状态与
    // D-Pad 焦点搜索）；下沉到 NowPlayingScreen 分支内收集（与 H-3 频谱流下沉同向）。
    val networkCoverUrl by viewModel.networkCoverUrl.collectAsState(initial = null)
    // M7 修复（2026-10-06，代码审查报告 §4）：songDownloadStates 不再顶层收集——
    // 512KB 步长的高频下载进度会驱动 AppRoot 顶层读取位置重组。已下沉到
    // Library/Mine/AlbumDetail/ArtistDetail 四个分支内自行收集。
    val songs by viewModel.songs.collectAsState(initial = UiState.Loading as UiState<List<Song>>)
    val isLoading by viewModel.isLoading.collectAsState(initial = false)
    val isLibraryLoading by viewModel.isLibraryLoading.collectAsState(initial = false)
    val isConnected by viewModel.serverVM.isConnected.collectAsState(initial = false)
    val serverDisplayName by viewModel.serverVM.serverDisplayName.collectAsState(initial = "")
    val serverConfig by viewModel.serverConfig.collectAsState(initial = ServerConfig.Empty)
    val settings by viewModel.appSettings.collectAsState(initial = com.lnh.nasmusictv.data.model.AppSettings())
    // 封面滤镜状态（跨屏幕共享，用于 NowPlaying + Settings）
    val coverFilterEnabled by viewModel.prefs.visualizer.coverFilterEnabled.collectAsState(initial = false)
    val coverFilterBlurRadius by viewModel.prefs.visualizer.coverFilterBlurRadius.collectAsState(initial = 8f)
    val coverFilterDarkOverlay by viewModel.prefs.visualizer.coverFilterDarkOverlay.collectAsState(initial = 0.3f)

    // 加入歌单弹窗目标歌曲（提升到 AppRoot 顶层，供 Library / 专辑详情 / 艺术家详情 共用）
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    // 天气 API Key
    // 百度网盘状态（设置页网盘分区）
    // K 歌页面显隐（切 Tab 时保持，退出 K 歌页时清除）
    // MTV 页面显隐（进入 MTV 全屏页时为 true）
    val showMv by viewModel.mvVM.showMv.collectAsState(initial = false)
    // K 歌全屏页显隐（修复：BACK 键需在 K 歌页优先退出 K 歌，而非触发应用退出确认）
    val showKaraoke by viewModel.vocalVM.showKaraoke.collectAsState(initial = false)
    // 全屏可视化舞台显隐（BACK 需在舞台页优先退出，而非触发应用退出确认）
    val showVisualizer by viewModel.visualizerVM.showVisualizer.collectAsState(initial = false)
    // MTV 搜索状态（顶层收集，供 NotFound 自动退出保护与 NowPlaying 分支共用）
    val mvState by viewModel.mvVM.mvState.collectAsState()
    // 安全兜底：切歌后新歌无 MV（NotFound）时自动退出 MTV 全屏，避免卡在无导航栏的播放页
    LaunchedEffect(mvState, showMv) {
        if (showMv && mvState is com.lnh.nasmusictv.ui.viewmodel.MvAvailability.NotFound) {
            viewModel.mvVM.exitMvMode()
        }
    }
    // Level 2: 根据当前屏幕和沉浸模式动态设置导航 BACK 键处理函数
    val navBackHandler = LocalNavigateBackHandler.current
    LaunchedEffect(currentScreen, isImmersiveMode.value, showMv, showKaraoke, showVisualizer) {
        // C-2 修正说明：初版审查把 when/if-else 分支里的 {{ ... }} 误判为 no-op（lambda 内 lambda）。
        // 实测编译行为：when/if 分支的 { } 按“块”解析，{{ X }} = 块 + 尾部 lambda 表达式，
        // 分支值就是可用的 lambda——原实现功能正常，并非 bug。此处改用具名 lambda 仅作可读性清理。
        val navigateHome: () -> Unit = { viewModel.navVM.navigateTo(Screen.Home) }
        val exitImmersive: () -> Unit = { isImmersiveMode.value = false }
        val exitMv: () -> Unit = { viewModel.mvVM.exitMvMode() }
        val exitKaraoke: () -> Unit = { viewModel.vocalVM.exitKaraoke() }
        val exitVisualizer: () -> Unit = { viewModel.visualizerVM.exitVisualizer() }
        val navSettings: () -> Unit = { viewModel.navVM.navigateTo(Screen.Settings) }
        val navigateMine: () -> Unit = { viewModel.navVM.navigateTo(Screen.Mine) }
        val handler: (() -> Unit)? = when {
            isImmersiveMode.value -> exitImmersive
            // 全屏可视化舞台：BACK 退出舞台，而非应用退出确认
            showVisualizer -> exitVisualizer
            // K 歌页：BACK 退出 K 歌（切回普通 NOW PLAYING），而非应用退出确认
            showKaraoke -> exitKaraoke
            showMv -> exitMv
            currentScreen == Screen.NowPlaying -> if (isTV) null else navigateHome
            currentScreen == Screen.Home -> null
            // ── 设置域子页面：BACK 返回设置主菜单（入口唯一：SettingsBranch）──
            // 修复：均衡器/播放统计此前落入 else 回首页，遥控器返回键无法回到设置
            currentScreen == Screen.ServerConnect -> navSettings
            currentScreen == Screen.Equalizer -> navSettings
            currentScreen == Screen.PlayStats -> navSettings
            // ── "我的"域子页面：BACK 返回我的页（网盘入口唯一：MineBranch）──
            currentScreen == Screen.Netdisk -> navigateMine
            // WeatherRadio 入口在首页，由 else 回首页覆盖，无需专门分支
            else -> navigateHome
        }
        navBackHandler.value = handler
    }

    // D10：**不加任何过渡**（裸 Column，硬切）—— 引入 AnimatedContent/Crossfade
    // 会在过渡期同时组合新旧子树，带回 B3 的三个副作用（handler 置空 / 重复拉数据 / 滚动位丢失）。
        // ── 顶栏自动隐藏状态（置于 Column 之外：topBarReservedPx 要经
        // CompositionLocalProvider 下发整个子树，含内容区里的播放页）──
        val autoHideIdle = currentScreen == Screen.NowPlaying && isPlaying
        var idleHidden by remember { mutableStateOf(false) }
        // 记录上一轮的触发源：effect 重启可能来自「用户活动」（tick 变化，必须
        // 立即唤醒）或「播放状态翻转」（切歌间隙，需要宽限期过滤）。
        val prevTick = remember { mutableLongStateOf(0L) }
        val prevAutoHide = remember { mutableStateOf(false) }
        LaunchedEffect(userActivityTick, autoHideIdle) {
            val tickChanged = userActivityTick != prevTick.longValue
            prevTick.longValue = userActivityTick
            prevAutoHide.value = autoHideIdle
            when {
                // ① 用户活动（任意键/触摸/BACK）→ 立即唤醒并重新计时
                tickChanged -> {
                    idleHidden = false
                    if (autoHideIdle) {
                        delay(5000)
                        idleHidden = true
                    }
                }
                // ② 播放停止 / 离开播放页 → 宽限后再唤醒：
                // 2026-10-30 用户定稿「隐藏状态下切歌（一曲播完）继续保持隐藏」——
                // 自动连播间隙 isPlaying 短暂变 false，旧逻辑会立即唤醒；现在先等
                // [PlayerVisualSpec.IDLE_WAKE_GRACE_MS]，期间播放恢复（切歌成功）
                // effect 被打断重启 → 走 ③ 保持隐藏；真正暂停/退出才唤醒。
                !autoHideIdle -> {
                    if (idleHidden) delay(PlayerVisualSpec.IDLE_WAKE_GRACE_MS)
                    idleHidden = false
                }
                // ③ 播放中（首次进入 / 切歌恢复播放）：已隐藏 → 继续保持隐藏；
                // 未隐藏 → 重新计时 5s
                else -> {
                    if (!idleHidden) {
                        delay(5000)
                        idleHidden = true
                    }
                }
            }
        }
        LaunchedEffect(idleHidden) { onIdleHiddenChange(idleHidden) }
        val topBarVisible = !idleHidden
        // 2026-10-28 用户定稿：顶栏隐藏改**抽屉式** —— 向上滑出（带渐隐），
        // 而非瞬间消失。布局占位不变（graphicsLayer 只做合成变换），因此
        // 播放页黑胶几何在 隐藏/显示 两态间依旧恒定（此前等高占位的修复保持有效）。
        val topBarSlide by animateFloatAsState(
            targetValue = if (topBarVisible) 0f else 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "topBarSlide"
        )
        // 滑出未完成时顶栏保留在组合树（动画继续跑）；完全隐藏后切占位 Box
        //（无可聚焦内容，不会残留焦点落点）。
        val showTopBar = topBarVisible || topBarSlide < 1f
        // 顶栏占位实测高度（onSizeChanged 缓存）：隐藏态等高占位与背景延伸都要用
        var topBarHeightPx by remember { mutableIntStateOf(0) }
        // 顶栏占位高度下发给播放页：背景层向上延伸铺满顶栏区域（滑出后无黑块）
        val topBarReservedPx =
            if (!isImmersiveMode.value && !showMv && !showVisualizer) topBarHeightPx else 0

        CompositionLocalProvider(LocalTopBarReservedPx provides topBarReservedPx) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部导航栏（沉浸模式 / MTV 全屏页时隐藏）
        // 播放页播放中无操作 5s 进入「沉浸隐藏」——顶栏 + 进度条 + 控制按钮
        // + 歌词来源行**一并隐去**，只留黑胶与歌词，且黑胶同步放大（见 NowPlayingScreen）。
        // 任意按键 / 触摸都会刷新 userActivityTick → 立即恢复全部 UI。
        if (!isImmersiveMode.value && !showMv && !showVisualizer) {
            // 2026-10-28：播放页隐藏顶栏时**保留占位高度**（实测高度缓存）——
            // 此前 AnimatedVisibility 淡出后把顶栏移出组合树，内容区瞬间变高：
            // 播放页黑胶盘被 maxHeight 撑大 ~14%、整块上移压向标题
            //（用户反馈"隐藏后黑胶不居中且与标题重叠"）。占位保留后布局几何恒定，
            // 黑胶尺寸与位置在 隐藏/可视 两态间不再跳动。
            if (showTopBar) {
                Box(
                    modifier = Modifier
                        // 2026-10-28：播放页背景层会上溢顶栏区域绘制（背景铺满整屏，
                        // 见 LocalTopBarReservedPx），而 Column 中内容区在顶栏**之后**
                        // 组合 → 背景会盖住顶栏。zIndex 提升顶栏绘制层级，保证顶栏
                        // 始终浮在背景之上。
                        .zIndex(1f)
                        .onSizeChanged { topBarHeightPx = it.height }
                        .graphicsLayer {
                            translationY = -size.height * topBarSlide
                            alpha = 1f - topBarSlide * 0.5f
                        }
                ) {
                    TvTopNavBar(
                        currentScreen = currentScreen,
                        isConnected = isConnected,
                        serverDisplayName = serverDisplayName,
                        onNavigate = { viewModel.navVM.navigateTo(it) },
                    )
                }
            } else if (topBarHeightPx > 0) {
                // ── 2026-10-30 修复「标题/歌手在 5s 隐藏时上跳」────────────────
                // 隐藏态：**等高占位**（无任何可聚焦内容）—— 内容区几何恒定，
                // 播放页黑胶不会被 maxHeight 撑大、也不会整体上移，
                // 歌词上方的标题/歌手在 隐藏/可视 两态完全同位置、同高度。
                //
                // ⚠️ 此前这段 `else if` 因为少一个右花括号被挂到了外层
                //    `if (!isImmersiveMode && !showMv && !showVisualizer)` 上：
                //    播放页 5s 沉浸隐藏（此时并非沉浸模式）走进外层 if 的**真分支**，
                //    内层 `if (showTopBar)` 又为假 ⇒ **顶栏和占位都没组合**，
                //    内容区整体上移一个顶栏高度（实测 3200×1440 上标题 y 301 → 75）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { topBarHeightPx.toDp() })
                )
            }
        }

        // 内容区域
        // 当前歌曲封面候选：getCoverCandidates 已改为 suspend（ID3 文件头/旁路图片读取在 IO），
        // 这里异步解析后写入状态，供 Home/NowPlaying/Queue 各分支同步复用（语义同基线 remember 版本）。
        var coverCandidates by remember(currentSong?.id, networkCoverUrl) {
            mutableStateOf(emptyList<String>())
        }
        LaunchedEffect(currentSong?.id, networkCoverUrl) {
            coverCandidates = currentSong?.let { viewModel.getCoverCandidates(it) } ?: emptyList()
        }
        // 封面候选解析可能读 ID3 文件头/检查旁路图片，必须放在 IO；`remember` 本身不切线程。
        val prefetchContext = LocalContext.current.applicationContext
        val prefetchNextId = playerState.queue.getOrNull(playerState.currentIndex + 1)?.id
        val prefetchPreviousId = playerState.queue.getOrNull(playerState.currentIndex - 1)?.id
        DisposableEffect(Unit) {
            onDispose { CoverPrefetcher.cancelPending() }
        }
        LaunchedEffect(
            currentSong?.id,
            networkCoverUrl,
            playerState.currentIndex,
            prefetchNextId,
            prefetchPreviousId
        ) {
            // 先取消上一首的在途预取，再解析新一代 URL；即便新曲封面搜索较慢，
            // 旧曲请求也不会继续占用网络连接与 Coil 解码队列。
            CoverPrefetcher.cancelPending()
            val queue = playerState.queue
            val index = playerState.currentIndex
            withContext(Dispatchers.IO) {
                val urls = linkedSetOf<String>()
                currentSong?.let { urls.addAll(viewModel.getCoverCandidates(it)) }
                // 顺序：下一曲优先（用户更常点"下一曲"）
                listOf(index + 1, index - 1).forEach { i ->
                    val neighbor = queue.getOrNull(i) ?: return@forEach
                    val candidates = viewModel.getCoverCandidates(neighbor)
                    if (candidates.isEmpty()) {
                        // 网络歌曲无自带封面：提前完成网络封面搜索并写入 ViewModel 缓存。
                        viewModel.resolveNetworkCoverUrl(neighbor)?.let { urls.add(it) }
                    } else {
                        urls.addAll(candidates)
                    }
                }
                CoverPrefetcher.prefetch(prefetchContext, urls.toList())
            }
        }

        // 当前播放歌曲 id 下发给全部歌曲列表（UnifiedSongRow 据此渲染持久"选中"高亮，
        // 见 components/song/UnifiedSongRow.kt 的 LocalCurrentSongId KDoc）。
        // 只有切歌时变化（低频），staticCompositionLocalOf 只让真正读它的行重组。
        CompositionLocalProvider(LocalCurrentSongId provides currentSong?.id) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (currentScreen) {
                Screen.Home -> HomeBranch(
                    viewModel = viewModel,
                    isConnected = isConnected,
                    isLibraryLoading = isLibraryLoading,
                    serverDisplayName = serverDisplayName,
                    currentSong = currentSong,
                    coverCandidates = coverCandidates
                )
                Screen.NowPlaying -> NowPlayingBranch(
                    viewModel = viewModel,
                    isTV = isTV,
                    isImmersiveMode = isImmersiveMode,
                    currentSong = currentSong,
                    // 多码率：当前生效档位（单曲覆盖 ?: 全局默认，方案 §5.1）
                    qualityTier = qualityTier,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    coverCandidates = coverCandidates,
                    coverFilterEnabled = coverFilterEnabled,
                    coverFilterBlurRadius = coverFilterBlurRadius,
                    coverFilterDarkOverlay = coverFilterDarkOverlay,
                    settings = settings,
                    showMv = showMv,
                    mvState = mvState,
                    isConnected = isConnected,
                    playerError = playerError,
                    // 5s 无操作 → 播放页全域隐藏（顶栏已由 AppRoot 隐去，其余见内）
                    idleHidden = idleHidden
                )
                Screen.Library -> LibraryBranch(
                    viewModel = viewModel,
                    songsState = songs,
                    isLoading = isLoading,
                    isLibraryLoading = isLibraryLoading,
                    isConnected = isConnected,
                    onPickSongForPlaylist = { song -> pickerSong = song }
                )
                Screen.Mine -> MineBranch(
                    viewModel = viewModel,
                )
                Screen.Queue -> QueueBranch(
                    viewModel = viewModel,
                    currentSong = currentSong,
                    coverCandidates = coverCandidates,
                    isPlaying = isPlaying,
                    playMode = playMode
                )
                Screen.Settings -> SettingsBranch(
                    viewModel = viewModel,
                    settings = settings,
                    isLoading = isLoading,
                    isConnected = isConnected,
                    serverDisplayName = serverDisplayName,
                    serverConfig = serverConfig,
                    coverFilterEnabled = coverFilterEnabled,
                    coverFilterBlurRadius = coverFilterBlurRadius,
                    coverFilterDarkOverlay = coverFilterDarkOverlay,
                    context = context,
                    coroutineScope = coroutineScope,
                    onConnect = onConnect,
                )
                Screen.ServerConnect -> ServerConnectBranch(
                    viewModel = viewModel,
                    serverConfig = serverConfig,
                    isConnected = isConnected,
                    serverDisplayName = serverDisplayName,
                    isLoading = isLoading,
                    onConnect = onConnect
                )
                Screen.AlbumDetail -> AlbumDetailBranch(
                    viewModel = viewModel,
                    onPickSongForPlaylist = { song -> pickerSong = song }
                )
                Screen.ArtistDetail -> ArtistDetailBranch(
                    viewModel = viewModel,
                    onPickSongForPlaylist = { song -> pickerSong = song }
                )
                Screen.Equalizer -> EqualizerBranch(
                    viewModel = viewModel,
                    settings = settings
                )
                Screen.PlaylistManagement -> PlaylistManagementBranch(
                    viewModel = viewModel
                )
                Screen.Netdisk -> NetdiskBranch(
                    viewModel = viewModel
                )
                Screen.WeatherRadio -> WeatherRadioBranch(
                    viewModel = viewModel,
                    onPickSongForPlaylist = { song -> pickerSong = song }
                )
                Screen.PlayStats -> PlayStatsBranch(
                    viewModel = viewModel
                )
            }
        }
        }

        // 全屏可视化舞台（20 套效果）—— 覆盖层，不新增 Screen 枚举
        if (showVisualizer) {
            VisualizerOverlay(
                viewModel = viewModel,
                isTV = isTV,
                currentSong = currentSong,
                isPlaying = isPlaying
            )
        }

    // 加入歌单弹窗（Library / 专辑详情 / 艺术家详情 共用，提升到 AppRoot 顶层跨页面生效）
    pickerSong?.let { song ->
        val dlgPlaylists by viewModel.playlistVM.localPlaylists.collectAsState(initial = emptyList())
        PlaylistPickerDialog(
            playlists = dlgPlaylists,
            onPick = { playlist ->
                viewModel.playlistVM.addSongToPlaylist(playlist.id, song)
                pickerSong = null
            },
            onCreate = { name ->
                if (name.isNotBlank()) {
                    viewModel.playlistVM.createLocalPlaylist(name)
                }
            },
            onDismiss = { pickerSong = null }
        )
    }

    // 多码率：单曲下载的码率选择面板（探测到多档可用时由 DownloadViewModel 弹出）
    val qpState by viewModel.downloadVM.qualityPicker.collectAsState()
    when (val qp = qpState) {
        is DownloadViewModel.QualityPickerState.Probing -> {
            QualityProbingDialog(songTitle = qp.song.title)
        }
        is DownloadViewModel.QualityPickerState.Pick -> {
            DownloadQualityPickerDialog(
                songTitle = qp.song.title,
                available = qp.available,
                downloaded = qp.downloaded,
                onConfirm = { tier -> viewModel.downloadVM.downloadWithQuality(qp.song, tier) },
                onDismiss = { viewModel.downloadVM.dismissQualityPicker() }
            )
        }
        is DownloadViewModel.QualityPickerState.None -> {
            // 零档可用 → 错误提示，不入队（自动关闭）
            LaunchedEffect(qp.song.id) {
                viewModel.showMessage(context.getString(R.string.quality_probe_none))
                viewModel.downloadVM.dismissQualityPicker()
            }
        }
        null -> Unit
    }

    }
    } // CompositionLocalProvider(LocalTopBarReservedPx)

}
/**
 * 现状顶部导航栏（TV + 手机横屏共用）。
 *
 * 由 `AppRoot` 内联代码原样抽为具名 composable（方案 §8.6 骨架要求），
 * **零行为变化** —— 6 项导航（首页/播放/曲库/我的/队列/设置）+ 窄屏横向滚动。
 *
 * 品牌区（logo + 文字）随连接状态切换：
 * - 已连接 → 服务器名 + 「已连接」徽章（[ConnectedBadge]）
 * - 未连接 → 应用名 "NAS Music TV"
 *
 * 右上角方向切换按钮已随竖屏下线移除（恒横屏，无切换目标）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTopNavBar(
    currentScreen: Screen,
    isConnected: Boolean,
    serverDisplayName: String,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(36.dp)
                    .background(
                        NasMusicColors.Primary,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\u266A", color = NasMusicColors.TextPrimary, fontSize = FontSize.subtitle())
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isConnected) {
                // 已连接：显示当前服务器名称（过长时省略，避免顶栏挤压导航项）
                Text(
                    text = serverDisplayName.ifBlank { stringResource(R.string.app_name) },
                    color = NasMusicColors.TextPrimary,
                    fontSize = FontSize.subtitle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ConnectedBadge()
            } else {
                // 未连接：显示应用名
                Text(
                    text = stringResource(R.string.app_name),
                    color = NasMusicColors.TextPrimary,
                    fontSize = FontSize.subtitle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 导航项（外层固定宽度右对齐；内层可横向滑动——手机窄屏滚动浏览全部 tab）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    label = stringResource(R.string.nav_home),
                    selected = currentScreen == Screen.Home,
                    onClick = { onNavigate(Screen.Home) }
                )
                NavItem(
                    label = stringResource(R.string.nav_now_playing),
                    selected = currentScreen == Screen.NowPlaying,
                    onClick = { onNavigate(Screen.NowPlaying) }
                )
                NavItem(
                    label = stringResource(R.string.nav_library),
                    selected = currentScreen == Screen.Library,
                    onClick = { onNavigate(Screen.Library) }
                )
                NavItem(
                    label = stringResource(R.string.nav_mine),
                    selected = currentScreen == Screen.Mine,
                    onClick = { onNavigate(Screen.Mine) }
                )
                NavItem(
                    label = stringResource(R.string.nav_queue),
                    selected = currentScreen == Screen.Queue,
                    onClick = { onNavigate(Screen.Queue) }
                )
                NavItem(
                    label = stringResource(R.string.nav_settings),
                    selected = currentScreen == Screen.Settings,
                    onClick = { onNavigate(Screen.Settings) }
                )
            }
        }

        // 右上角方向切换按钮已随竖屏下线移除（恒横屏，无切换目标）
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.12f,
        animationDurationMs = 250,
        containerColor = Color.Transparent,
        focusedContainerColor = if (selected) NasMusicColors.Primary.copy(alpha = 0.3f)
                                else NasMusicColors.Primary.copy(alpha = 0.2f),
        contentColor = if (selected) NasMusicColors.Primary else NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary,
        pressedScale = 0.96f
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = if (selected) FontSize.subtitle() else FontSize.button(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = if (selected) NasMusicColors.Primary else NasMusicColors.TextPrimary
            )
        }
    }
}

/**
 * 全屏可视化舞台覆盖层（20 套效果）。
 *
 * 歌词 / 进度在**此处**收集而非 AppRoot 顶层——播放期间进度每秒变化，
 * 若在顶层订阅会驱动整棵 UI 树重组（同 H-3 修复的教训）。
 */
@Composable
private fun VisualizerOverlay(
    viewModel: com.lnh.nasmusictv.ui.viewmodel.MainViewModel,
    isTV: Boolean,
    currentSong: com.lnh.nasmusictv.data.model.Song?,
    isPlaying: Boolean
) {
    val vm = viewModel.visualizerVM
    val theme by vm.theme.collectAsState()
    val quality by vm.quality.collectAsState()
    val photoWallAvailable by vm.photoWallAvailable.collectAsState()
    val cover by vm.cover.collectAsState()
    val palette by vm.palette.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState(initial = null)
    val progress by viewModel.playerVM.progress.collectAsState(initial = 0L)

    // 无自动导演档：用户选中的主题恒定显示，无需低频重估
    val effectiveTheme = theme

    // 封面加载 + 取色（切歌时一次，异步不阻塞）
    LaunchedEffect(currentSong?.id) {
        val url = currentSong?.let { viewModel.getCoverCandidates(it).firstOrNull() }
        vm.loadCover(url, currentSong?.id)
    }

    VisualizerStage(
        song = currentSong,
        // T6 双缓冲：传 provider 而非快照引用——绘制循环每帧求值，拿到当前 front
        frame = { vm.frame },
        cover = cover,
        palette = palette,
        lyrics = lyrics?.lines,
        progressMs = progress,
        theme = effectiveTheme,
        quality = quality,
        // 照片墙编排器（阶段 10）：控制器在 ViewModel 里（跨重组存活），本组件只接线。
        // ⚠️ 访问 `vm.photoWall` 会触发 `by lazy` 构造 —— 成本只等于几个纯 Kotlin 对象。
        photoWall = vm.photoWall,
        // 三来源开关之「或」—— 与 `VisualizerViewModel.step()` 用的是**同一个** StateFlow
        // ⇒ 指示器与左右键看到的是同一份列表（不会出现「指示器上没有、却切得到」）。
        photoWallAvailable = photoWallAvailable,
        isTV = isTV,
        onExit = { vm.exitVisualizer() },
        onNextTheme = { vm.nextTheme() },
        onPrevTheme = { vm.prevTheme() }
    )
}
