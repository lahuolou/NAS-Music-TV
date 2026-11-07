package com.lnh.nasmusictv.ui.screens

import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.components.common.SourceBadge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
// luminance 导入已移除：主题判断改由 LocalDarkTheme 直读用户意图，不再由底色亮度反推
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.data.model.Lyrics
import com.lnh.nasmusictv.data.model.LyricsHighlightMode
import com.lnh.nasmusictv.data.model.PlayMode
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.player.SleepTimerController
import com.lnh.nasmusictv.player.remainingMinutesDisplay
import com.lnh.nasmusictv.data.model.isRadioSong
import com.lnh.nasmusictv.ui.RegisterDialogBackHandler
import com.lnh.nasmusictv.ui.components.LyricsView
import com.lnh.nasmusictv.ui.components.MarqueeText
import com.lnh.nasmusictv.ui.components.NowPlayingBottomControls
import com.lnh.nasmusictv.ui.components.CoverCarousel
import com.lnh.nasmusictv.ui.components.ControlButtonsRow
import com.lnh.nasmusictv.ui.components.FocusableSurface
import com.lnh.nasmusictv.ui.components.LocalFocusableContentColor
import com.lnh.nasmusictv.ui.components.KaraokePlaybackScreen
import com.lnh.nasmusictv.ui.components.VinylDisc
import com.lnh.nasmusictv.ui.components.VocalToggleButton
import com.lnh.nasmusictv.ui.components.ProgressSection
import com.lnh.nasmusictv.ui.components.SongInfoPanel
import com.lnh.nasmusictv.ui.components.VolumeControl
import com.lnh.nasmusictv.ui.components.TV_TOUCH_TARGET
import com.lnh.nasmusictv.ui.components.portraitTouchTarget
import com.lnh.nasmusictv.ui.components.QualityPickerDialog
import com.lnh.nasmusictv.ui.components.ImmersiveBackdrop
import com.lnh.nasmusictv.ui.components.immersiveBackdropFallbackBrush
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.LocalDarkTheme
import com.lnh.nasmusictv.ui.theme.LocalReduceMotion
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec
import com.lnh.nasmusictv.ui.theme.CoverPalette
import com.lnh.nasmusictv.ui.theme.CoverPaletteExtractor
/** 进入时黑胶盘的回弹缓动（BackOut），模拟「共享元素放大」的轻微过冲。 */
private val BackOutEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * 顶栏在**布局中占掉的高度**（px，AppRoot 实测后下发）。
 *
 * 播放页背景（兜底渐变 + 封面模糊/取色层）用它做**向上延伸**：
 * 顶栏本身是透明的、浮在播放页渐变背景上 —— 5s 隐藏态顶栏抽屉式滑出后，
 * 背景已经铺满整屏，不再露出顶栏占位的那条黑块（2026-10-28 用户反馈）。
 */
val LocalTopBarReservedPx = staticCompositionLocalOf { 0 }

/**
 * 让被修饰元素**向上溢出 [extraPx]** 绘制，但不改变父布局中的占位：
 * - 测量约束的 maxHeight 增加 [extraPx]（子元素可以更高）；
 * - 布局报告尺寸**不含** [extraPx]（兄弟元素的排布完全不受影响）；
 * - 放置时整体上移 [extraPx]（Compose Box 默认不裁剪越界绘制）。
 *
 * 用途仅限「背景层上溢顶栏区域」——绝不能用在内容层（会改变内容测量约束）。
 */
private fun Modifier.extendUp(extraPx: Int): Modifier =
    layout { measurable, constraints ->
        if (extraPx <= 0 || !constraints.hasBoundedHeight) {
            val p = measurable.measure(constraints)
            layout(p.width, p.height) { p.placeRelative(0, 0) }
        } else {
            val p = measurable.measure(
                constraints.copy(maxHeight = constraints.maxHeight + extraPx)
            )
            layout(p.width, p.height - extraPx) { p.placeRelative(0, -extraPx) }
        }
    }

/**
 * 正在播放屏幕（主界面）
 * 左侧：专辑封面 + 歌曲信息（可聚焦）
 * 右侧：滚动歌词（可聚焦）
 * 底部：播放控制（可聚焦）
 * 使用 TV 标准 Surface 焦点管理模式
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    playMode: PlayMode,
    progressMs: Long,
    durationMs: Long,
    lyrics: Lyrics?,
    lyricsAvailability: com.lnh.nasmusictv.data.model.LyricsAvailability,
    coverCandidates: List<String> = emptyList(),
    highlightMode: LyricsHighlightMode = LyricsHighlightMode.LINE_BY_LINE,
    isFavorite: Boolean = false,
    isImmersiveMode: Boolean = false,
    onToggleImmersive: () -> Unit = {},
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onSeek: (Long) -> Unit,
    onSwitchLyricsSource: (com.lnh.nasmusictv.data.model.LyricsSource) -> Unit,
    onChangeHighlightMode: (LyricsHighlightMode) -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    lyricsFontScale: Float = 0.7f,
    onLyricsFontScaleChange: (Float) -> Unit = {},
    coverFilterEnabled: Boolean = false,
    coverFilterBlurRadius: Float = 8f,
    coverFilterDarkOverlay: Float = 0.3f,
    // 歌曲详情信息
    technicalInfo: com.lnh.nasmusictv.data.model.SongTechnicalInfo? = null,
    onLoadTechnicalInfo: () -> Unit = {},
    // === K 歌页面状态（由 ViewModel 管理，切 Tab 时保持） ===
    showKaraoke: Boolean = false,
    onEnterKaraoke: () -> Unit = {},
    onEnterVisualizer: () -> Unit = {},
    onExitKaraoke: () -> Unit = {},
    // === KARAOKE 人声消除 ===
    vocalRemovalEnabled: Boolean = false,
    onToggleVocalRemoval: () -> Unit = {},
    // === MTV 音乐视频 ===
    mvAvailable: Boolean = false,
    onEnterMv: () -> Unit = {},
    remoteControlUrl: String? = null,
    /** 点击歌手名跳转到网络搜索 */
    onSearchArtist: (String) -> Unit = {},
    /** 点击歌曲名跳转到网络搜索 */
    onSearchSong: (String) -> Unit = {},
    // === 多码率：音质档位（方案 §5.1） ===
    /** 当前生效档位（单曲覆盖 ?: 全局默认） */
    qualityTier: Int = 0,
    /** 用户确认档位 + 生效范围 */
    onChangeQuality: (tier: Int, scope: com.lnh.nasmusictv.backend.network.QualityScope) -> Unit = { _, _ -> },
    // === K 歌页面：升降调 / 变速（全局记忆） ===
    pitchSemitones: Int = 0,
    playbackSpeed: Double = 1.0,
    onPreviewPitch: (Int) -> Unit = {},
    onCommitPitch: () -> Unit = {},
    onPreviewSpeed: (Double) -> Unit = {},
    onCommitSpeed: () -> Unit = {},
    onResetPitch: () -> Unit = {},
    onResetSpeed: () -> Unit = {},
    // === 分离模式（快速/高质量） ===
    isHighQualityMode: Boolean = false,
    isSeparating: Boolean = false,
    separationProgress: Pair<Float, String> = 0f to "",
    /** 高质量分离错误信息（非空时 UI 应显示错误提示） */
    hqError: String? = null,
    onToggleSeparationMode: () -> Unit = {},
    /** 清除高质量分离错误 */
    onClearHqError: () -> Unit = {},
    /** 高质量分离成功信息（非空时 UI 应显示成功提示） */
    hqSuccess: String? = null,
    /** 清除高质量分离成功信息 */
    onClearHqSuccess: () -> Unit = {},
    /** 高质量分离模型是否已下载（未下载时禁用高质量切换） */
    modelDownloaded: Boolean = false,
    // === F2-2 睡眠定时器 ===
    /** 睡眠定时器状态（null = 未启用） */
    sleepTimerState: com.lnh.nasmusictv.player.SleepTimerController.State? = null,
    /** 选择档位后启动定时 */
    onSleepTimerStart: (Int) -> Unit = {},
    /** 取消定时 */
    onSleepTimerCancel: () -> Unit = {},
    /** 「收起」回首页（播放继续） */
    onCollapse: () -> Unit = {},
    /** 「队列」入口 */
    onOpenQueue: () -> Unit = {},
    /** 极简遥控器「主页」键：直接回主页（播放继续） */
    onNavigateHome: () -> Unit = {},
    // === 应用内音量（应用级增益 0.0–1.0，独立于系统音量；播放页 OSD / 控制条）===
    appVolume: Float = 1f,
    onChangeAppVolume: (Float) -> Unit = {},
    // === §7.5 状态兜底：播放错误（null = 正常）===
    playerError: String? = null,
    onRetryPlayback: () -> Unit = {},
    onDismissPlaybackError: () -> Unit = {},
    // === P0-6 进度条缓冲态（P1-7 缓冲脉冲）===
    buffering: Boolean = false,
    /**
     * 5s 无操作的「沉浸隐藏」态。
     *
     * 顶栏由 AppRoot 隐去；本页负责隐去**进度条 + 控制按钮 + 歌词来源行**，
     * 同时把黑胶盘放大（[PlayerVisualSpec.IDLE_DISC_SCALE]）作为隐藏转场的主体特效 ——
     * 隐藏后画面只剩封面与歌词，等于一个纯欣赏态。任意按键 / 触摸即刻恢复。
     */
    idleHidden: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showInfoPanel by remember { mutableStateOf(false) }
    // 多码率：音质选择面板显隐
    var showQualityDialog by remember { mutableStateOf(false) }
    val qualityLabel = if (currentSong?.isNetworkSong == true) {
        stringResource(com.lnh.nasmusictv.data.model.QualityTiers.labelResOf(qualityTier))
    } else ""
    // F2-2b：睡眠定时弹窗显隐（按钮在歌词来源行 A+ 右侧）
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }
    // 歌词显隐（底部「歌词」按钮控制）；无歌词或隐藏时黑胶居中放大
    var lyricsVisible by remember { mutableStateOf(true) }

    // ── 切歌防闪回 ──
    // 切歌时 ViewModel 会先把 _currentLyrics 置 null 再去异步解析（loadLyricsForCurrentSong），
    // 于是 [lyrics] 在中间那段时间为 null。若布局直接跟 [lyrics] 走，会先退成
    // "无歌词"（黑胶居中 + 标题落到盘下）再跳回"有歌词" —— 用户看到的就是"闪一下"。
    // 这里让"有歌词布局"具备粘性：切歌后维持上一首的布局，直到
    //   ① 新歌词解析成功（立即切回有歌词），或
    //   ② [PlayerVisualSpec.LYRICS_SETTLE_MS] 超时仍为 null（判定新歌确实无歌词，才退布局）。
    var lastHadLyrics by remember { mutableStateOf(false) }
    var awaitingLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(currentSong?.id) {
        awaitingLyrics = true
        delay(PlayerVisualSpec.LYRICS_SETTLE_MS)
        awaitingLyrics = false
    }
    LaunchedEffect(lyrics) {
        if (lyrics != null) {
            lastHadLyrics = true
            awaitingLyrics = false
        } else if (!awaitingLyrics) {
            lastHadLyrics = false
        }
    }
    // ── 2026-10-28 用户定稿：**布局只看歌词开关** ──
    // 此前 showLyrics 还掺入「新歌词是否已解析完 / 上一首有没有歌词」：
    // 切歌瞬间 lyrics 先被置 null → 判定退成"无歌词布局"（黑胶跳屏幕中央），
    // 新词到货后再跳回"有歌词布局" —— 用户看到的就是"先切歌、再判断"的闪跳。
    // 现在改为**切歌前就以开关状态定布局**：
    //   开关开 = 恒走有歌词布局（新词未到显示上一首歌词，仍无词则显示"暂无歌词"占位）
    //   开关关 = 恒走无歌词布局（黑胶居中 + 单行「标题 - 歌手」）
    // 于是切歌时布局不动，只有右列文字内容变化。
    val showLyrics = lyricsVisible

    // ── 2026-10-28 用户定稿：切歌方向 → 黑胶"换碟"滑动方向 ──
    //   +1 = 下一曲：整盘从右往左滑（旧碟向左抽出、新碟自右推入）
    //   -1 = 上一曲：整盘从左往右滑
    // ⚠️ 必须在**按下的那一刻**记下方向：等切歌完成、封面列表换掉之后，
    //    就再也无从判断这首歌是从哪一侧换过来的了。
    var discSlideDir by remember { mutableStateOf(1) }
    val goNext: () -> Unit = { discSlideDir = 1; onNext() }
    val goPrev: () -> Unit = { discSlideDir = -1; onPrevious() }

    // ── 2026-10-28 切歌间隙保旧词（修"换歌时歌词跳来跳去"）──
    // 上面只保住了"有歌词布局"，但 [lyrics] 在间隙期本身是 null —— LyricsView 会先
    // 渲染成"暂无歌词"空态、新词到位后再跳回，观感就是来回跳。这里把**上一首的
    // 歌词 + 它最后已知的进度**留住，间隙期继续渲染旧词（画面静止），新词到位后
    // 由 LyricsView 自身的交叉淡入完成切换，全程无空态、无位置跳动。
    var lastLyrics by remember { mutableStateOf<Lyrics?>(null) }
    var lastLyricsProgressMs by remember { mutableStateOf(0L) }
    LaunchedEffect(lyrics, progressMs) {
        if (lyrics != null) {
            lastLyrics = lyrics
            lastLyricsProgressMs = progressMs
        }
    }
    val displayLyrics = lyrics ?: if (awaitingLyrics && lastHadLyrics) lastLyrics else null
    val displayLyricsTimeMs = if (lyrics != null) progressMs else lastLyricsProgressMs

    // ── 全屏 KARAOKE 页面 ──
    if (showKaraoke) {
        KaraokePlaybackScreen(
            currentSong = currentSong,
            isPlaying = isPlaying,
            lyrics = lyrics,
            coverCandidates = coverCandidates,
            coverFilterEnabled = coverFilterEnabled,
            coverFilterBlurRadius = coverFilterBlurRadius,
            progressMs = progressMs,
            durationMs = durationMs,
            vocalRemovalEnabled = vocalRemovalEnabled,
            onToggleVocalRemoval = onToggleVocalRemoval,
            onExitKaraoke = { onExitKaraoke() },
            onPlayPause = onPlayPause,
            onNext = goNext,
            onPrevious = goPrev,
            pitchSemitones = pitchSemitones,
            playbackSpeed = playbackSpeed,
            onPreviewPitch = onPreviewPitch,
            onCommitPitch = onCommitPitch,
            onPreviewSpeed = onPreviewSpeed,
            onCommitSpeed = onCommitSpeed,
            onResetPitch = onResetPitch,
            onResetSpeed = onResetSpeed,
            isHighQualityMode = isHighQualityMode,
            isSeparating = isSeparating,
            separationProgress = separationProgress,
            hqError = hqError,
            onToggleSeparationMode = onToggleSeparationMode,
            onClearHqError = onClearHqError,
            hqSuccess = hqSuccess,
            onClearHqSuccess = onClearHqSuccess,
            modelDownloaded = modelDownloaded,
            playPauseFocusRequester = playPauseFocusRequester,
            remoteControlUrl = remoteControlUrl
        )
        return
    }

    // 进入 NowPlaying 页面时自动聚焦播放/暂停按钮
    LaunchedEffect(Unit) {
        try {
              withFrameNanos { }
              playPauseFocusRequester.requestFocus()
          } catch (_: Exception) {
              // 焦点请求失败时忽略
          }
    }

    // === 极简遥控器（2026-10-30 用户定稿）========================================
    // ① 5s 隐藏态触发 → 默认焦点落到「暂停/播放」：隐藏后画面只有黑胶与歌词，
    //    焦点若停在已淡出的工具行/进度条上，用户按 OK 会激活"看不见的元素"。
    //    播放/暂停是最安全且最高频的落点。
    // ② （2026-11-01 用户定稿）：隐藏态**任意按键第一次只唤醒**——
    //    MainActivity 的根级 onPreviewKeyEvent 先 bump userActivityTick 唤醒，
    //    本页 rootKeyHandler 再把键消费掉（含 OK/菜单，BACK 走 Activity Level 1.7），
    //    不触发播放暂停/面板/导航；唤醒后焦点强制落回「暂停/播放」，下次按键才正常。
    LaunchedEffect(idleHidden) {
        // 进入隐藏态：焦点收拢到「暂停/播放」；唤醒（隐藏→显示）：焦点强制回到
        // 「暂停/播放」——每次重新隐藏后再唤醒，焦点都落在播放/暂停键
        // （2026-11-01 用户定稿）。
        try {
            withFrameNanos { }
            playPauseFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    // 隐藏态焦点保活（2026-11-01 修正）：隐藏期间若焦点因竞态/系统原因离开
    // 暂停/播放键（例如 requestFocus 恰逢焦点树刷新而失败），周期性拉回；
    // UI 恢复（idleHidden=false）后立即停止干预，不干扰正常导航。
    val currentIdleHidden by rememberUpdatedState(idleHidden)
    LaunchedEffect(idleHidden) {
        if (!currentIdleHidden) return@LaunchedEffect
        while (currentIdleHidden) {
            delay(200L)
            if (currentIdleHidden) {
                try {
                    playPauseFocusRequester.requestFocus()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ③ 方向键长按节流 + 菜单/主页键收口（只挂在播放页根级）：
    //    - 方向键 repeat 事件每 140ms 只放行一次，长按不"飞速跳焦"；
    //    - MENU 仅用于呼出/关闭「功能面板」（信息面板），不再承担播放模式切换
    //      （那是 MediaKeyHandler 的旧职责，Compose 消费后 Activity 收不到）；
    //    - HOME 直接回主页（系统一般不向应用投递 HOME，能收到就处理）。
    val dpadThrottleNs = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val keyMenuToggle = {
        if (!showInfoPanel) onLoadTechnicalInfo()
        showInfoPanel = !showInfoPanel
    }
    val rootKeyHandler: (android.view.KeyEvent) -> Boolean = { native ->
        // （2026-11-01 用户定稿）：沉浸隐藏态（5s 无操作）下**任意遥控器键
        // 第一次按下只唤醒并显示控制栏**——不触发播放/暂停点击、不呼出面板、
        // 不移动焦点。UI 唤醒由 MainActivity 根级 onPreviewKeyEvent bump
        // userActivityTick 完成（先于本处理器派发，BACK 另有 Level 1.7 只唤醒）；
        // 此处把按键消费掉（含 OK/ENTER/菜单/HOME），焦点保持/落回「暂停/播放」键
        // （LaunchedEffect(idleHidden) 同步 requestFocus）。控制栏重新显示后：
        // 方向键正常移动焦点、OK 激活当前按钮；每次重新隐藏→唤醒重复该流程。
        if (idleHidden) {
            when (native.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                android.view.KeyEvent.KEYCODE_MENU,
                android.view.KeyEvent.KEYCODE_HOME -> true
                else -> false
            }
        } else when (native.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (native.action == android.view.KeyEvent.ACTION_DOWN &&
                    native.repeatCount > 0
                ) {
                    val now = android.os.SystemClock.uptimeMillis()
                    val last = dpadThrottleNs.longValue
                    if (now - last < 140L) {
                        true   // 节流：吞掉过快的连发，焦点不跳
                    } else {
                        dpadThrottleNs.longValue = now
                        false  // 放行 → 正常空间焦点导航
                    }
                } else false
            }
            android.view.KeyEvent.KEYCODE_MENU -> {
                if (native.action == android.view.KeyEvent.ACTION_DOWN) keyMenuToggle()
                true
            }
            android.view.KeyEvent.KEYCODE_HOME -> {
                if (native.action == android.view.KeyEvent.ACTION_DOWN) onNavigateHome()
                true
            }
            else -> false
        }
    }

    // 主题判断（最高优先级 A1/A2）：直接读 CompositionLocal 中的**用户主题意图**
    // （由 MainActivity 依 AppSettings.darkTheme 提供），不再由底色亮度反推 ——
    // 旧写法 `NasMusicColors.Background.luminance() > 0.5f` 把"策略判断"建立在
    // "观感副产物"上：色板底色一旦接近阈值或用户换主题，沉浸式开关就会莫名翻转。
    val darkTheme = LocalDarkTheme.current
    /**
     * 减弱动效总闸（§10.1-7 / §11-8）：关闭背景呼吸、黑胶光晕、进入转场等非必要连续动画。
     *
     * 除了用户显式设置（[LocalReduceMotion]），**低端设备也自动降级**（§11-7）：
     * 低内存设备 / ≤2 核的老电视盒子上，连续动画会明显掉帧，此时强制走静态呈现，
     * 保留必要的状态反馈（焦点边框、播放/暂停图标、进度）—— 用户无需手动开关。
     */
    val context = androidx.compose.ui.platform.LocalContext.current
    val lowPerfDevice = remember(context) { PlayerVisualSpec.isLowPerfDevice(context) }
    val reduceMotion = LocalReduceMotion.current || lowPerfDevice

    // === 沉浸隐藏（5s 无操作）的统一动画状态 ===
    // 全页共用这一组值与同一条时长曲线（底部控制栏 / 歌词来源行 / 音质角标 / 黑胶放大），
    // 保证"藏"与"显"是同一件事在发生，而不是各处各自为政的动画。
    val hideMs = PlayerVisualSpec.IDLE_HIDE_MS
    /** UI 整体淡出进度：1f = 全显，0f = 全隐 */
    val chromeAlpha by animateFloatAsState(
        targetValue = if (idleHidden) 0f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(hideMs, easing = FastOutSlowInEasing),
        label = "chromeAlpha"
    )
    /** 沉浸隐藏进度 0..1：驱动歌词锚点/行槽/渐隐的隐藏态版式 */
    val idleProgress by animateFloatAsState(
        targetValue = if (idleHidden) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(hideMs, easing = FastOutSlowInEasing),
        label = "idleProgress"
    )
    /** 隐藏时：底部控制栏向下滑 24dp、顶部歌词来源行向上滑 24dp → 上下合拢收势 */
    val chromeOffsetY by animateDpAsState(
        targetValue = if (idleHidden) 24.dp else 0.dp,
        animationSpec = if (reduceMotion) snap() else tween(hideMs, easing = FastOutSlowInEasing),
        label = "chromeOffsetY"
    )

    // === 封面智能取色（网易云式主题色渲染） ===
    // 只对「首个可用封面 URL」取色：解码 + Palette 量化走 Dispatchers.Default，
    // 结果按 URL 缓存（CoverPaletteExtractor），切歌回来零成本。
    // 取不到（无封面 / 解码失败 / 取不到主色）时 palette 为 null，
    // 背景与光晕自动回退到既有的固定品牌色方案，不会半套色。
    val coverPalette by produceState<CoverPalette?>(
        initialValue = null,
        key1 = coverCandidates.firstOrNull()
    ) {
        value = CoverPaletteExtractor.extract(context, coverCandidates.firstOrNull())
    }
    // 黑胶盘外光晕：取到色就跟封面走，否则沿用品牌绛红（VinylDisc 内部默认同色）
    val discGlowColor = coverPalette?.accent ?: NasMusicColors.PrimaryVariant

    // === 2026-10-28 用户定稿：黑胶盘相对**整屏**上下绝对居中 ===
    // 内容 Row 的中线高于屏幕中线：底部进度条 + 控制栏占掉一段高度（B，运行时实测），
    // 顶栏占位（topBarReservedPx，运行时实测——2026-10-29 由硬编码 34dp 改为实测值，
    // 顶栏高度随字体缩放/设备变化时补偿自动跟随，任何屏幕尺寸都精确居中）。
    // 盘整体下移补偿 = B/2 - (顶40-底24)/2 - 顶栏实测高度的一半。
    var bottomReservedPx by remember { mutableIntStateOf(0) }
    val topBarReservedPxForCentering = LocalTopBarReservedPx.current
    val densityForCentering = LocalDensity.current
    val discCenterCompensation =
        with(densityForCentering) { (bottomReservedPx / 2).toDp() } -
            PlayerVisualSpec.DISC_CENTER_PAD_COMPENSATION -
            with(densityForCentering) { (topBarReservedPxForCentering / 2).toDp() }

    // 弹窗/队列打开时暂停背景呼吸与封面光晕（§10.3）；
    // 关闭后自动恢复——由状态驱动，不需要手动重启动画。
    val overlayVisible = showInfoPanel || showQualityDialog || showSleepTimerDialog
    // 进入播放页的「沉浸式聚焦」过渡——整页从 1.06 缩放收束到 1.0 + 淡入，
    // 模拟网易云式「共享元素放大 / 全屏纯享」的切入感；结束精确停在 1.0，不残留缩放缝隙。
    // （硬切架构下仅做进入动画，避免 AnimatedContent 双树副作用；退出沿用瞬间硬切）
    // 减弱动效时：不跑动画，直接取终点值 1f（hero horse 静态呈现）
    val pageEnter = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            pageEnter.snapTo(1f)
        } else {
            pageEnter.animateTo(
                1f,
                tween(PlayerVisualSpec.PAGE_ENTER_MS, easing = FastOutSlowInEasing)
            )
        }
    }
    // 2026-10-28 用户反馈：顶栏 5s 抽屉式滑出后，顶栏占位区不能留"大黑块" ——
    // 背景层（兜底渐变 + 封面模糊/取色）整体**向上延伸顶栏高度**，铺满整屏；
    // 顶栏本身透明、浮在背景上，滑出前后背景连续无接缝。
    // ⚠️ 只延伸背景层：内容 Column 的测量约束必须保持不变（否则黑胶几何会变）。
    val topBarReservedPx = LocalTopBarReservedPx.current
    val pageBgBrush = if (isImmersiveMode) {
        // 沉浸模式：整屏纯黑（左半封面右缘渐黑 + 右半歌词区均落在黑底上，无缝衔接）
        Brush.verticalGradient(listOf(Color.Black, Color.Black))
    } else if (!darkTheme) {
        // 亮色主题（A2 静态）：不套用深色模糊/遮罩，但借封面主色做一层极淡氛围晕染，
        // 让亮色播放页也有"沉浸感"而不只是一块平涂；取不到封面色时回退到 SurfaceVariant。
        val lightTint = coverPalette?.dominant?.copy(alpha = 0.14f)
            ?: NasMusicColors.SurfaceVariant
        Brush.verticalGradient(listOf(lightTint, NasMusicColors.Background))
    } else {
        // 暗色主题：底层兜底渐变（品牌暗红氛围 → 深净底）；
        // 真实封面模糊背景由下方 ImmersiveBackdrop 层叠加绘制，
        // 封面缺失/加载失败时露出这层兜底，页面不会变成一块死黑。
        immersiveBackdropFallbackBrush()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> rootKeyHandler(event.nativeKeyEvent) }
            .graphicsLayer {
                val p = pageEnter.value
                alpha = p
                val s = PlayerVisualSpec.PAGE_ENTER_SCALE_FROM -
                    (PlayerVisualSpec.PAGE_ENTER_SCALE_FROM - 1f) * p
                scaleX = s
                scaleY = s
            }
    ) {
        // 整页底色（上溢顶栏区域）
        Box(
            modifier = Modifier
                .extendUp(topBarReservedPx)
                .fillMaxSize()
                .background(pageBgBrush)
        )
        // P0-1 封面模糊背景层：仅在暗色主题 + 非沉浸模式下启用。
        // 置于所有内容之前绘制（Box 按声明顺序绘制），作为整页底图；
        // 沉浸模式本就是"纯黑 + 大封面"，不叠模糊背景。
        if (!isImmersiveMode) {
            ImmersiveBackdrop(
                coverCandidates = coverCandidates,
                isPlaying = isPlaying,
                enabled = darkTheme,
                reduceMotion = reduceMotion,
                paused = overlayVisible,
                palette = coverPalette,
                // 2026-10-30 用户定稿：切歌背景全屏方向性滑动（下一曲 右→左 / 上一曲 左→右）
                slideDirection = discSlideDir,
                modifier = Modifier.extendUp(topBarReservedPx).fillMaxSize()
            )
        }

        // 空白带修复二轮（2026-09-22 用户反馈 12dp 仍无感，要求干脆去掉）：
        // 手机横屏 padding 全部归零（TV 保持 40/24 不变）。
        // TV-only：手机横屏分支已下线，统一走 TV 布局（原 isPhoneLandscape 恒为 false，已折叠）。

        // 2026-10-29 适配要求（仅横屏）：刘海/挖孔 + 底部手势安全区。
        // 以 WindowInsets 实测值与「基准留白」取**较大者** —— TV 无安全区（inset=0）
        // 时仍保持原基准留白；手机横屏有挖孔/手势条时自动让出。全程相对单位（dp /
        // weight），不写死像素，避免不同屏尺寸 / 宽高比 / 挖孔位置导致内容被遮挡。
        // 顶部 inset 不叠加：顶栏（AppRoot）在内容区之上单独占位，横屏挖孔多在左右。
        val densityForInsets = LocalDensity.current
        val layoutDir = LocalLayoutDirection.current
        val safeArea = WindowInsets.safeDrawing
        val insetStart = with(densityForInsets) {
            safeArea.getLeft(densityForInsets, layoutDir).toDp()
        }
        val insetEnd = with(densityForInsets) {
            safeArea.getRight(densityForInsets, layoutDir).toDp()
        }
        // safeDrawing ∪ systemGestures：既覆盖三键导航栏，也覆盖手势导航的底部手势条
        val insetBottom = with(densityForInsets) {
            maxOf(
                safeArea.getBottom(densityForInsets),
                WindowInsets.systemGestures.getBottom(densityForInsets)
            ).toDp()
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(
                // 左右：基准留白 ∨ 挖孔/侧栏安全区
                start = maxOf(32.dp, insetStart),
                end = maxOf(32.dp, insetEnd),
                // 顶部保持原值（顶栏在上方单独处理，横屏挖孔多在左右两侧）
                top = 40.dp,
                // 2026-10-29「减少底部留白」：TV 24 → 12dp
                bottom = maxOf(12.dp, insetBottom)
            )
        ) {
            // 中部：专辑封面(≈45%) + 歌词(≈55%)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(
                    if (isImmersiveMode) 24.dp else 48.dp
                ),
                // 顶部对齐（CenterVertically 会在短视口里把内容挤成中间一团、
                // 上下各留一条缝——正是用户指出的空带来源之一）
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：
                // - 沉浸模式 = 占满左半屏的大封面，图片右缘虚化并渐变到黑
                // - 普通模式 = 封面 + 歌曲信息（原布局）
                if (isImmersiveMode) {
                    ImmersiveCoverHalf(
                        currentSong = currentSong,
                        coverCandidates = coverCandidates,
                        isPlaying = isPlaying,
                        coverFilterEnabled = coverFilterEnabled,
                        coverFilterBlurRadius = coverFilterBlurRadius,
                        onExitImmersive = onToggleImmersive,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    // 布局：有歌词时左列（黑胶+信息）与右列（歌词）≈ 45% : 55%；
                    // 无歌词或已隐藏时左列占满中部、黑胶居中放大。
                    // 2026-10-27：移除 verticalScroll——左列高度恒等于屏幕高度，
                    // 内容超出时不允许上下滚动（配合移除专辑名行，正常屏幕均可放下）
                    // （2026-10-28 参考图定稿）：有歌词时左列略宽于右列（≈52:48），
                    // 黑胶在列内只占约六成宽 —— 盘小、留白多，歌词区拿走更大的视觉权重
                    val leftWeight = if (showLyrics) PlayerVisualSpec.LEFT_COLUMN_WEIGHT_LYRICS else 1f
                    // 2026-10-29 用户定稿：歌词工具行移到**黑胶盘上方** ——
                    // 左列改为 Box 叠加结构：黑胶容器照旧占满整列（内部垂直绝对居中，
                    // 几何与原 Column+Center 完全一致），工具行作为 overlay 贴列顶左上角，
                    // **不参与居中测量** —— 加在居中流里会把盘心顶高半个工具行高度。
                    Box(
                        modifier = Modifier
                            .weight(leftWeight)
                            .fillMaxHeight()
                    ) {
                        // 黑胶容器（无歌词时限制宽度并保持居中放大）
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxHeight()
                                .then(
                                    if (showLyrics) Modifier.fillMaxWidth()
                                    else Modifier.fillMaxWidth(0.62f)
                                )
                        ) {
                            CoverColumn(
                            currentSong = currentSong,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            coverCandidates = coverCandidates,
                            isPlaying = isPlaying,
                            coverFilterEnabled = coverFilterEnabled,
                            coverFilterBlurRadius = coverFilterBlurRadius,
                            coverFilterDarkOverlay = coverFilterDarkOverlay,
                            technicalInfo = technicalInfo,
                            onLoadTechnicalInfo = onLoadTechnicalInfo,
                            showInfoPanel = showInfoPanel,
                            onToggleInfoPanel = { showInfoPanel = !showInfoPanel },
                            onSearchSong = onSearchSong,
                            onSearchArtist = onSearchArtist,
                            // 网易云黑胶：唱针微交互 —— 2026-10-30 用户定稿升级：
                            // 暂停弹开悬停 / 播放落下 / 切歌「弹开→悬停→落下」三段式，
                            // TV 端同样启用（原先仅手机横屏启用微交互）
                            tonearmAnimated = true,
                            // 无歌词时标题/歌手收进黑胶容器、放在盘正下方
                            infoUnderDisc = !showLyrics,
                            // 无歌词态单行标题要横向居中于屏幕，需知道左列左缘位置
                            contentPaddingStart = 32.dp,
                            reduceMotion = reduceMotion,
                            paused = overlayVisible,
                            // 5s 无操作 → 黑胶放大（隐藏转场的主体特效）
                            idleHidden = idleHidden,
                            chromeAlpha = chromeAlpha,
                            // 2026-10-28：黑胶盘整屏垂直居中补偿（底部预留区 + 顶栏）
                            centerOffsetExtra = discCenterCompensation,
                            // 盘外光晕跟着封面取色走
                            glowColor = discGlowColor,
                            // 2026-10-30 用户定稿：唱针径向位置与播放进度绑定（外缘→内圈）
                            playbackProgress = if (durationMs > 0) {
                                (progressMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else 0f,
                            // 2026-10-28：换碟滑动的运动方向（上一曲 左→右 / 下一曲 右→左）
                            discSlideDirection = discSlideDir,
                            // 2026-10-30 用户定稿：切歌 → 唱臂「弹开→悬停→落下」三段式的触发源
                            songKey = currentSong?.id,
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                        // 2026-10-30 用户定稿：歌词工具行**移回右侧、右对齐**（见右列
                        // 顶部的 LyricsSourceToolbar overlay），左列只留黑胶盘。
                    }
                }

                // 右侧：歌词（仅在有歌词且未隐藏时显示；沉浸模式占右半屏纯黑底）
                // 歌词区与左侧黑胶 ≈ 55% : 45%（参考用户截图比例）
                if (showLyrics) {
                // ── 2026-10-30 十九轮：歌词开启 + 菜单可见态的右列版式（全部自适应）──
                // 字号 -12%、歌词容器顶部保护内边距 —— 按 idleProgress 插值：
                // 可见态生效，5s 隐藏态严格归位图一定稿。
                // ── 2026-11-04：歌词正文字号跟随导航可见态 ──
                // 导航（chrome）没隐藏时歌词大一号（用户要求），5s 隐藏态归位 1；
                // 标题/歌手仍走原 -12% 让位逻辑（lyricsOnFontScale）。
                val lyricsChromeFontScale = androidx.compose.ui.util.lerp(
                    PlayerVisualSpec.LYRICS_CHROME_VISIBLE_FONT_SCALE, 1f, idleProgress
                )
                val lyricsOnFontScale = androidx.compose.ui.util.lerp(
                    PlayerVisualSpec.LYRICS_ON_FONT_SCALE, 1f, idleProgress
                )
                val lyricsOnGuard = androidx.compose.ui.util.lerp(
                    PlayerVisualSpec.LYRIC_TOP_GUARD_FRACTION, 0f, idleProgress
                )
                // 菜单可见态标题/歌手下移量（占屏幕高比例，自适应；隐藏态归零 = 图一定稿）
                val densityTitleDrop = LocalDensity.current
                val contextTitleDrop = androidx.compose.ui.platform.LocalContext.current
                val screenHpxTitleDrop = remember {
                    contextTitleDrop.resources.displayMetrics.heightPixels
                }
                val lyricsOnTitleDrop = with(densityTitleDrop) {
                    (screenHpxTitleDrop * androidx.compose.ui.util.lerp(
                        PlayerVisualSpec.LYRICS_TITLE_DROP_FRACTION, 0f, idleProgress
                    )).toDp()
                }
                // 2026-10-30 用户定稿：右列改 **Box 布局** —— 歌词内容 Column 之外，
                // 「内嵌/本地/在线/缓存」工具行改为**overlay 悬浮右上角**：
                // 不占歌词主区高度（不再挤占 5 行行槽），且**右对齐**贴列右缘。
                Box(
                    modifier = Modifier
                        .weight(PlayerVisualSpec.RIGHT_COLUMN_WEIGHT_LYRICS)
                        // 与左列（黑胶）对齐：必须 fillMaxHeight，否则 Row 不会在竖直方向
                        // 拉伸本列，右列高度按"内容"计算，导致歌词塌成一条缝。
                        .fillMaxHeight()
                        .then(
                            if (isImmersiveMode) Modifier.background(Color.Black)
                            else Modifier
                        )
                ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── 2026-10-30 三定稿「高亮行 = 视口 × 0.40（明显下移）+ 第二行高亮」──
                    // 标题/歌手块**移出布局流**（改为下方 overlay，绘制在歌词之上）：
                    // 歌词视口因此占满整列，视口中心 ≈ 屏幕中线，高亮行落点
                    //（视口 × [PlayerVisualSpec.LYRIC_HIGHLIGHT_TARGET_FRACTION]）稳定；
                    // 若标题仍占布局流，视口中心会低于屏幕中线几十 dp，高亮落点
                    // 会被一起压低 —— 顶部行槽会叠到标题上。
                    // overlay 化后不需要全局上移顶栏。2026-10-29 用户反馈
                    // 「为什么没有标题和歌手了」→ 标题保持**常显**（overlay 也常显）。

                    // F2-2b：睡眠定时弹窗（输入框 -/+ 步进 + 15/30 快捷档 + 取消）
                    if (showSleepTimerDialog) {
                        SleepTimerPickerDialog(
                            onStart = { min ->
                                showSleepTimerDialog = false
                                onSleepTimerStart(min)
                            },
                            onCancel = {
                                showSleepTimerDialog = false
                                onSleepTimerCancel()
                            },
                            onDismiss = { showSleepTimerDialog = false }
                        )
                    }

                    // 多码率：音质选择面板（方案 §5.1）
                    if (showQualityDialog) {
                        com.lnh.nasmusictv.ui.components.QualityPickerDialog(
                            currentTier = qualityTier,
                            onConfirm = { tier, scope ->
                                showQualityDialog = false
                                onChangeQuality(tier, scope)
                            },
                            onDismiss = { showQualityDialog = false }
                        )
                    }

                    // 歌词内容区域（歌词浮动化：移除半透明"框"，歌词直接浮在整页
                    // 渐变背景上，与页面背景统一；沉浸模式本就透明，行为不变）
                    //
                    // **高度内缩** [PlayerVisualSpec.LYRICS_PANEL_VERTICAL_PAD] ——
                    // 歌词此前上下顶满整列（贴着标题下沿与页底），整片文字压在画面里显得满。
                    // 上下各留一段后，歌词只占中间一段，**左右两列的宽度比例（0.9 : 1.1）不变**，
                    // 缩小的是行高而不是列宽，所以黑胶 / 歌词的构图比例保持原样。
                    //
                    // ── 2026-10-30 三定稿「高亮行 = 视口 × 0.40」─────────────────
                    // 歌词视口（本 Box）的中心 ≠ 屏幕中线：上方有标题/歌手块、下方有
                    // 进度条+控制栏，两者高度不对称。LyricsView 只保证「行中心 = 视口 × 0.40」，
                    // 这里再补一层**测量式对齐**：实测本 Box 中心与真实屏幕中线（displayMetrics，
                    // TV 上 LocalConfiguration 不可信）的差 → offset 修正。测量点不含 offset
                    //（offset 打在内部 LyricsView 上）→ 无反馈回路，一帧收敛。
                    // 顶栏不动、标题不动，任何分辨率/宽高比自适应。
                    val contextForLyrics = androidx.compose.ui.platform.LocalContext.current
                    val densityForLyrics = LocalDensity.current
                    val screenHpxLyrics = remember {
                        contextForLyrics.resources.displayMetrics.heightPixels
                    }
                    var lyricsCenterY by remember { mutableStateOf(Float.NaN) }
                    val lyricsCenterCompensation =
                        if (!lyricsCenterY.isNaN() && screenHpxLyrics > 0) {
                            with(densityForLyrics) {
                                (screenHpxLyrics / 2f - lyricsCenterY).toDp()
                            }
                        } else {
                            0.dp
                        }
                    // ── 2026-10-30 十九轮：lyricsOnTitleDrop 已上移到 showLyrics 作用域 ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = PlayerVisualSpec.LYRICS_PANEL_VERTICAL_PAD)
                        .onGloballyPositioned { coords ->
                            lyricsCenterY = coords.positionInWindow().y + coords.size.height / 2f
                        }
                ) {
                        if (currentSong?.networkSource?.isRadioSong() == true) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.player_radio_live),
                                    fontSize = FontSize.title(),
                                    fontWeight = FontWeight.Bold,
                                    color = NasMusicColors.Primary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LyricsView(
                                lyrics = displayLyrics,
                                currentTimeMs = displayLyricsTimeMs,
                                highlightMode = highlightMode,
                                isPlaying = isPlaying,
                                // 字号乘数：用户档位（lyricsFontScale）× 导航可见态放大 1.15
                                // （5s 隐藏态经 idleProgress 插值归位 1；标题/歌手仍用 lyricsOnFontScale -12%）。
                                // 2026-10-30 定稿：隐藏态歌词**不再随黑胶放大**（原 lyricIdleScale）——
                                // 改为行槽收窄多排几行，字号与可见态一致（见 LyricsView idleProgress 文档）。
                                fontSizeMultiplier = lyricsFontScale * lyricsChromeFontScale,
                                // 隐藏态歌词版式（锚点 0.50 + 行槽收窄多排几行），
                                // 由 idleProgress 在可见/隐藏两套比例间插值
                                idleProgress = idleProgress,
                                // 2026-10-30 十九轮：歌词容器顶部保护内边距（可见态防与标题/歌手叠字）
                                topGuardFraction = lyricsOnGuard,
                                onSeekToLine = onSeek,
                                reduceMotion = reduceMotion,
                                // 2026-10-28 用户定稿：播放页歌词**居左对齐**
                                // （与标题一起形成左侧基线，不再逐行居中跳左右）
                                alignStart = true,
                                // 沉浸模式纯黑底用黑色渐隐；普通模式统一为页底深净底，
                                // 让歌词滚动渐隐与整页背景融为一体（不再出现深蓝渐变边）
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 高亮行落点对齐：视口中心 → 屏幕中线（见上方说明），
                                    // 高亮行落点 = 视口 × LYRIC_HIGHLIGHT_TARGET_FRACTION
                                    .offset(y = lyricsCenterCompensation)
                                    // 纵向内缩统一交给外层 LYRICS_PANEL_VERTICAL_PAD，
                                    // 此处不再重复留白（重复会让矮视口再少 16dp）。
                                    .padding(horizontal = 12.dp)
                            )
                        }
                    }
                }

                // ── 标题 + 歌手 overlay（不占布局流，绘制在歌词之上）──────────────
                // 歌词视口占满整列后，其中心 ≈ 屏幕中线（高亮行落点系数稳定的前提）；
                // 标题以 overlay 悬浮在视口顶部，歌词行从其后方滚过（标题常显在最上层）。
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // 2026-10-30 用户定稿「标题和歌手太靠下」：整体绘图上移
                        // [PlayerVisualSpec.LYRICS_TITLE_RISE]，只动绘制不动布局。
                        // 2026-10-30 十九轮：菜单可见态按屏高比例下移
                        // [PlayerVisualSpec.LYRICS_TITLE_DROP_FRACTION]（增加 margin-top 观感），
                        // 隐藏态归零保持图一定稿。
                        .offset(y = -PlayerVisualSpec.LYRICS_TITLE_RISE + lyricsOnTitleDrop)
                ) {
                    // 工具行 overlay 已贴顶栏（内容区顶带），TV 横屏标题只留 4dp
                    // 呼吸；标题需让出工具行带。
                    Spacer(
                        modifier = Modifier.height(PlayerVisualSpec.LYRICS_TITLE_TOP_PAD)
                    )
                    SongTitleAndArtist(
                        currentSong = currentSong,
                        onSearchSong = onSearchSong,
                        onSearchArtist = onSearchArtist,
                        // 有歌词：标题/歌手居左（与下方左对齐歌词同一条基线）
                        alignStart = true,
                        // 2026-10-30 十九轮：菜单可见态字号 -12%（与歌词同步），
                        // 隐藏态插值归位 1（图一字号）
                        fontScale = lyricsOnFontScale
                    )
                    Spacer(modifier = Modifier.height(PlayerVisualSpec.LYRICS_TITLE_BOTTOM_PAD))
                }
                }
                }
            }

            // 2026-10-28：底部固定区（间隔 + 进度条 + 控制栏）作为一个整体测量高度，
            // 供黑胶盘整屏垂直居中补偿使用（见 discCenterCompensation）
            Column(modifier = Modifier.onSizeChanged { bottomReservedPx = it.height }) {
            Spacer(modifier = Modifier.height(2.dp))

            // 注：原 48dp 小频谱条已移除——入口改为播放控件上的「频谱」按钮，
            // 进入独立全屏舞台（VisualizerStage），20 套效果更震撼。

            // 2026-10-27 底部控制区重排（按参考图 / 网易云样式）：
            // ① 进度条全宽在上，时间标签移到进度条下方两端；
            // ② 控制行居中放 上一曲/播放暂停/下一曲，右侧放 播放模式/频谱/K歌/MTV。
            //    （原左列 ControlButtonsRow 已移除，入口全部并入本行，纯 D-pad 遥控器可全程聚焦操作）
            // §7.5 状态兜底：播放错误提示条。
            // 位置在**进度条上方、控制栏之外** —— 错误提示不得遮挡控制栏（§7.5），
            // 且提供「重试 / 关闭」两个**可聚焦按钮**：极简遥控器下 3 次按键内可达，
            // 返回键仍能正常退出，不会把用户困在错误态里（§9.1-6）。
            if (playerError != null) {
                PlaybackErrorBanner(
                    message = playerError,
                    onRetry = {
                        onDismissPlaybackError()
                        onRetryPlayback()
                    },
                    onDismiss = onDismissPlaybackError
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 5s 无操作 → 进度条 + 整条控制栏**淡出并向下滑走**。
            //
            // ⚠️ 这里刻意**保留占位高度**（alpha + offset），而不是用
            // AnimatedVisibility + shrinkVertically 把控件移出组合树。三个理由：
            //   ① 移除控件 = 焦点目标凭空消失，遥控器会失去焦点落点，按键后无从恢复；
            //   ② 底部一旦塌陷，BoxWithConstraints 的 maxHeight 变大 → 黑胶会被"撑大"，
            //      与 IDLE_DISC_SCALE 叠加后放大幅度失控；
            //   ③ 保留占位后，黑胶的放大幅度完全由 IDLE_DISC_SCALE 精确控制，可预期。
            // ⚠️ 播放错误提示条**不参与隐藏** —— 错误态必须始终可见（§7.5）。
            Column(
                modifier = Modifier
                    .alpha(chromeAlpha)
                    .offset(y = chromeOffsetY)
            ) {
            // 进度条（全宽，底部对齐）— Task 2；直播态（电台）禁用 seek
            ProgressSection(
                progressMs = progressMs,
                durationMs = durationMs,
                onSeek = onSeek,
                compact = true,
                timesBelow = true,
                // 2026-10-29 用户定稿：底部**不再显示 0:12 / 4:31 时间**，
                // 进度条占满整行并落回原时间行的位置（控制栏随之变矮）。
                showTimes = false,
                isLive = currentSong?.networkSource?.isRadioSong() == true,
                isPlaying = isPlaying,
                reduceMotion = reduceMotion,
                // P0-6：缓冲态（轨道脉冲，与播放进度区分）
                buffering = buffering
            )
            Spacer(modifier = Modifier.height(2.dp))
            NowPlayingBottomControls(
                isPlaying = isPlaying,
                playMode = playMode,
                onPlayPause = onPlayPause,
                onNext = goNext,
                onPrevious = goPrev,
                onTogglePlayMode = onTogglePlayMode,
                showVisualizerButton = currentSong != null,
                onEnterVisualizer = onEnterVisualizer,
                showVocalButton = currentSong != null,
                onEnterKaraoke = onEnterKaraoke,
                showMvButton = true,
                mvAvailable = mvAvailable,
                onEnterMv = onEnterMv,
                // 底部左侧新增 爱心 + 歌词(显隐) 按钮
                isFavorite = isFavorite,
                onToggleFavorite = { onToggleFavorite?.invoke() },
                lyricsVisible = lyricsVisible,
                onToggleLyrics = { lyricsVisible = !lyricsVisible },
                // 「信息」按钮 + 歌曲来源，紧跟「频谱」右侧
                // （K歌 / MTV 已移到左侧「歌词」按钮后面）
                trailingInfo = {                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VocalToggleButton(
                            label = if (showInfoPanel) stringResource(R.string.player_cover)
                                    else stringResource(R.string.player_info),
                            onClick = {
                                if (!showInfoPanel) onLoadTechnicalInfo()
                                showInfoPanel = !showInfoPanel
                            },
                            compact = true,
                            width = 52.dp
                        )
                        if (currentSong != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            SourceBadge(song = currentSong)
                        }
                    }
                },
                reduceMotion = reduceMotion,
                playPauseFocusRequester = playPauseFocusRequester,
                // 音质 / 码率角标移到左簇「MTV」旁（原在黑胶下方）
                qualitySlot = {
                    QualityBadgeButton(
                        currentSong = currentSong,
                        qualityLabel = qualityLabel,
                        onOpenQuality = { showQualityDialog = true }
                    )
                },
                // 整行上移 —— 此前底栏几乎贴着屏幕下沿，
                // 留出 [PlayerVisualSpec.BOTTOM_BAR_BOTTOM_PAD] 后不再"顶到底边"。
                modifier = Modifier.padding(bottom = PlayerVisualSpec.BOTTOM_BAR_BOTTOM_PAD)
            )
            }
            }
        }

        // 音源工具行 overlay（2026-10-30 用户定稿：从右列顶部**上移到内容区顶带**）：
        // 顶栏正下方、标题上方的一条右对齐空带 —— 即主 Column 自身的 top padding 区。
        // 标题维持原位（LYRICS_TITLE_TOP_PAD=4dp）不再下移避让；悬浮不占布局高度，
        // 歌词 5 个行槽按整列高度分配，不被工具行挤占。随 [chromeAlpha] 淡出。
        // 2026-10-30 再定稿「尽量贴着顶部导航和最右边」：浮层整体上提
        // [PlayerVisualSpec.LYRICS_TOOLBAR_TOP_RAISE] 抵消按钮内边距，让文字视觉贴住
        // 顶栏底边（顶栏 zIndex 更高、可点区不重叠）；end 收到屏幕右缘 8dp，
        // 越过内容列基准留白 —— 浮层不受列边距约束。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .alpha(chromeAlpha)
                .offset(y = -chromeOffsetY - PlayerVisualSpec.LYRICS_TOOLBAR_TOP_RAISE)
                .padding(
                    top = PlayerVisualSpec.LYRICS_TOOLBAR_TOP_PAD,
                    end = maxOf(PlayerVisualSpec.LYRICS_TOOLBAR_EDGE_PAD, insetEnd)
                )
        ) {
            LyricsSourceToolbar(
                lyrics = lyrics,
                lyricsAvailability = lyricsAvailability,
                highlightMode = highlightMode,
                onChangeHighlightMode = onChangeHighlightMode,
                lyricsFontScale = lyricsFontScale,
                onLyricsFontScaleChange = onLyricsFontScaleChange,
                sleepTimerState = sleepTimerState,
                onShowSleepTimer = { showSleepTimerDialog = true },
                onSwitchLyricsSource = onSwitchLyricsSource
            )
        }
    }
}

/** 沉浸模式左半屏封面：右缘虚化的默认模糊半径（dp）——未开启「封面滤镜」时使用。 */
private const val IMMERSIVE_EDGE_BLUR_DP = 24f

/**
 * 沉浸模式左半屏封面（占左侧一半空间）。
 *
 * 视觉构成（自下而上三层）：
 * 1. 封面原图，[ContentScale.Crop] 铺满左半区（保持清晰，作为主体）
 * 2. 同一封面的模糊副本，用水平渐变 alpha 遮罩只在右侧显现 —— 形成「右缘虚化」。
 *    `Modifier.blur` 在 API < 31 上是 no-op（电视 SDK 22 即如此），此时该层自动
 *    退化为纯渐变、不会报错，效果等同单层渐变遮罩。
 * 3. 水平渐变黑幕：左侧全透明 → 右缘纯黑，与右半屏歌词区的纯黑背景无缝衔接。
 * 4. 右侧虚化区**竖排**歌曲信息：最右一列 = 歌曲名（白色加粗），其左侧一列 = 艺术家
 *    （主题青色），两列均**上对齐**。放这里是刻意的——第 3 层已把该区域压到 ≥83% 黑，
 *    底色确定为暗色，文字就能固定用亮色，不必担心与任意封面撞色。
 *
 * 点击（TV OK 键 / 手机触摸）退出沉浸模式，回到普通播放页。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ImmersiveCoverHalf(
    currentSong: Song?,
    coverCandidates: List<String>,
    isPlaying: Boolean,
    coverFilterEnabled: Boolean,
    coverFilterBlurRadius: Float,
    onExitImmersive: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 虚化半径沿用用户的「封面滤镜」设置；未开启时给一个温和的默认值
    val edgeBlurDp = if (coverFilterEnabled && coverFilterBlurRadius > 0f) {
        coverFilterBlurRadius
    } else {
        IMMERSIVE_EDGE_BLUR_DP
    }
    // ③ 右缘渐黑：0.55 处仍全透明，1.0 处纯黑 —— 保证与右侧黑底歌词区无缝
    val edgeFadeBrush = remember {
        Brush.horizontalGradient(
            0.55f to Color.Transparent,
            0.80f to Color.Black.copy(alpha = 0.72f),
            1f to Color.Black
        )
    }
    // ② 模糊副本的显现遮罩：0.45 处不可见 → 1.0 处完全显现
    // （DstIn 只保留渐变不透明的区域，从而让虚化"渐进"出现）
    val blurMaskBrush = remember {
        Brush.horizontalGradient(
            0.45f to Color.Transparent,
            0.72f to Color.Black.copy(alpha = 0.85f),
            1f to Color.Black
        )
    }

    FocusableSurface(
        onClick = onExitImmersive,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        // 已占满左半屏，焦点缩放会溢出裁切，故不做缩放反馈
        focusedScale = 1f,
        pressedScale = 1f,
        animationDurationMs = 150,
        containerColor = Color.Black,
        focusedContainerColor = Color.Black,
        contentColor = Color.Transparent,
        focusedContentColor = Color.Transparent,
        showFocusBorder = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            // ① 封面原图（清晰主体）
            key(currentSong?.id) {
                CoverCarousel(
                    coverCandidates = coverCandidates,
                    isPlaying = isPlaying,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ② 右缘虚化层（模糊副本 + 水平渐变遮罩）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 遮罩需要离屏层，否则 DstIn 会作用到已绘制的整屏内容
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = blurMaskBrush, blendMode = BlendMode.DstIn)
                    }
            ) {
                key(currentSong?.id) {
                    CoverCarousel(
                        coverCandidates = coverCandidates,
                        isPlaying = isPlaying,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(edgeBlurDp.dp)
                    )
                }
            }

            // ③ 右缘渐变到黑
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(edgeFadeBrush)
            )

            // ④ 右侧虚化区竖排歌曲信息：最右一列 = 歌曲名，其左侧一列 = 艺术家，均上对齐。
            //    选这里的理由：③ 已把该区域压到 ≥83% 黑（0.80 处 0.72 → 1.0 处纯黑），
            //    底色**确定是暗的** → 文字可以固定用亮色（白 / Primary），
            //    不必像压在封面上那样担心任意底色撞色。
            val songTitle = currentSong?.title?.takeIf { it.isNotBlank() }
            val songArtist = currentSong?.artist?.takeIf { it.isNotBlank() }
            if (songTitle != null || songArtist != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .padding(top = 36.dp, end = 26.dp, bottom = 36.dp)
                ) {
                    val titleSize = FontSize.title()
                    val artistSize = FontSize.body()
                    val density = LocalDensity.current
                    // 单字行高 = 字号 × 1.15（Compose 1.6 起 includeFontPadding 默认 false，
                    // 无需 PlatformTextStyle）；按可用高度反推最多能放几个字，超出补「…」
                    val titleCharH = with(density) { titleSize.toDp() } * VERTICAL_LINE_HEIGHT_RATIO
                    val artistCharH = with(density) { artistSize.toDp() } * VERTICAL_LINE_HEIGHT_RATIO
                    val maxTitleChars = (maxHeight / titleCharH).toInt().coerceIn(2, 18)
                    val maxArtistChars = (maxHeight / artistCharH).toInt().coerceIn(2, 18)
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 左列：艺术家
                        if (songArtist != null) {
                            VerticalText(
                                text = songArtist,
                                color = NasMusicColors.Primary,
                                fontSize = artistSize,
                                maxChars = maxArtistChars
                            )
                        }
                        // 右列（最右）：歌曲名
                        if (songTitle != null) {
                            VerticalText(
                                text = songTitle,
                                color = Color.White,
                                fontSize = titleSize,
                                fontWeight = FontWeight.Bold,
                                maxChars = maxTitleChars
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 竖排文字的单字行高倍数（见 [VerticalText]：把行高固定下来，竖列才不松散）。 */
private const val VERTICAL_LINE_HEIGHT_RATIO = 1.15f

/**
 * 竖排文字（CJK 习惯：单字自上而下成列，列自右向左排）。
 *
 * Compose **没有原生竖排能力**（`TextStyle` 没有 writing-mode），这里按 **码点** 拆字后逐字
 * 堆一列 `Text`。两个实现要点：
 * - 按码点而不是按 `Char` 拆：`String.toList()` 会把 emoji / 生僻字的**代理对拆成两个乱码**。
 *   用 `Character.codePointAt` + `charCount` 步进（`java.lang.Character` 是 API 1 就有，
 *   minSdk 22 无压力；**不能用 `String.codePoints()`**——它返回 `IntStream`，
 *   `java.util.stream` 是 API 24+）。
 * - 显式设 `lineHeight = 字号 × [VERTICAL_LINE_HEIGHT_RATIO]`：Compose 1.6 起
 *   `includeFontPadding` 默认已是 false（见 ui-text 1.6.1 的 `DefaultIncludeFontPadding`），
 *   所以**不需要** `PlatformTextStyle` 那套实验性 API 就能得到紧凑的竖列。
 *
 * [maxChars] 由调用方按可用高度算出，超出时末字替换为「…」，保证永不溢出封面。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VerticalText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    maxChars: Int,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier
) {
    val chars = remember(text, maxChars) { splitToCodePoints(text, maxChars) }
    val lineHeight = fontSize * VERTICAL_LINE_HEIGHT_RATIO
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        chars.forEach { ch ->
            Text(
                text = ch,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                maxLines = 1
            )
        }
    }
}

/** 按 Unicode 码点切分 [text]，最多 [maxChars] 个；被截断时最后一个替换为「…」。 */
internal fun splitToCodePoints(text: String, maxChars: Int): List<String> {
    val out = ArrayList<String>(maxChars)
    var i = 0
    while (i < text.length && out.size < maxChars) {
        val cp = Character.codePointAt(text, i)
        out.add(StringBuilder().append(Character.toChars(cp)).toString())
        i += Character.charCount(cp)
    }
    if (i < text.length && out.isNotEmpty()) {
        out[out.size - 1] = "…"
    }
    return out
}

/**
 * 标题 + 歌手（从 [CoverColumn] 抽出，供「有歌词」与「无歌词」两种布局复用）。
 *
 * 竖排两行：
 * - **标题在上**：大字（[FontSize.title]），限宽到 [PlayerVisualSpec.SONG_TITLE_MAX_WIDTH]，
 *   超出后在限定宽度内**从右往左跑马灯滚动**（[MarqueeText]）—— 既不换行撑高版面，
 *   也不会被省略号截掉歌名后半截。
 * - **歌手在下**：小字（[FontSize.button]）、次要色，单行省略号兜底。
 *
 * 水平对齐由 [alignStart] 决定：有歌词时**居左**（与左对齐的歌词同一条基线），
 * 无歌词（黑胶正下方那块）时**居中**。
 *
 * ⚠️ 两者都**强制单行**（maxLines = 1）：多行的高度会随文本长度跳变，
 * 在 Disc/歌词两种布局下都会把黑胶或歌词区挤位（竞态式抖动）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongTitleAndArtist(
    currentSong: Song?,
    onSearchSong: (String) -> Unit,
    onSearchArtist: (String) -> Unit,
    /**
     * 2026-10-28 用户定稿：**有歌词时标题/歌手居左对齐**（与下方左对齐的歌词同一条
     * 左侧基线）；无歌词（黑胶正下方）时仍整体居中。
     *
     * 居左后超长标题依旧走 [MarqueeText] 的「从右往左」跑马灯 —— 方向本来就是文字向左
     * 移动，与居中态同一套实现，只是容器从"居中裁切"变成"贴左裁切"。
     */
    alignStart: Boolean = false,
    /**
     * 字号缩放系数（2026-10-30 十九轮：歌词开启 + 菜单可见态右列字号统一 -12%，
     * 标题/歌手/歌词同乘一个系数保持层级；隐藏态由调用方插值归位 1 = 图一定稿）。
     * 只乘字号不动布局盒，MarqueeText 测量含 fontSize，滚动判定同步正确。
     */
    fontScale: Float = 1f,
) {
    val title = currentSong?.title
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 居左态起始 12dp：加上 MarqueeText 自身的 4dp → 与工具行 / 歌词的
            // 16dp 左侧基线完全对齐（居中态保持左右对称的 8dp）。
            .padding(start = if (alignStart) 12.dp else 8.dp, end = 8.dp),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (!title.isNullOrBlank()) {
            // 2026-10-28 用户定稿：标题/歌手**移除可聚焦（可选中）**——纯展示元素，
            // 不再占用遥控器焦点落点（此前焦点会落到歌名上、误触跳网络搜索）。
            Box(modifier = Modifier.widthIn(max = PlayerVisualSpec.SONG_TITLE_MAX_WIDTH)) {
                MarqueeText(
                    text = title,
                    color = NasMusicColors.TextPrimary,
                    // 2026-10-30 用户定稿「以标题字号为基准」的层级体系：
                    // 标题回到 title(27/33) —— 歌词当前行 = title+1 = display(33/39)、
                    // 其他歌词行 = title-1 = subtitle(23/29)，见 LyricsView。
                    // 2026-10-30 十九轮：再乘 [fontScale]（菜单可见态 -12%）。
                    fontSize = FontSize.title() * fontScale,
                    textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                    // 纵向内边距 2 → 1dp，进一步压低标题块高度
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else {
            Text(
                text = stringResource(R.string.player_no_song_selected),
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.subtitle(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2026-10-29「降低标题区高度」：标题与歌手间距 6 → 3dp
        Spacer(modifier = Modifier.height(3.dp))

        val artist = currentSong?.artist?.takeIf { it.isNotBlank() }
        if (artist != null) {
            Box(modifier = Modifier.widthIn(max = PlayerVisualSpec.SONG_TITLE_MAX_WIDTH)) {
                MarqueeText(
                    text = artist,
                    color = NasMusicColors.TextSecondary,
                    // 2026-10-30 用户定稿：歌手 = **标题 -1 档** = subtitle(23/29)，
                    // 与普通歌词行同字号（相对档位统一管理，无硬编码）。
                    // 2026-10-30 十九轮：再乘 [fontScale]（菜单可见态 -12%）。
                    fontSize = FontSize.subtitle() * fontScale,
                    textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                    // 2026-10-29「降低标题区高度」：纵向内边距 2 → 1dp
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else {
            Text(
                text = "—",
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.button(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 无歌词态的**单行**「标题 - 歌手」（2026-10-28 用户定稿）。
 *
 * 与 [SongTitleAndArtist] 的区别：
 * - 一行显示，标题与歌手用不同颜色（标题亮色加粗、` - 歌手` 次要色）区分层级；
 * - 2026-10-30 用户定稿：超长**不省略号截断、不换行**，改走 [MarqueeText]
 *   行内跑马灯滚动（停 → 滚到末尾 → 停 → 快速滚回，循环）；短文本静态居中。
 * - 宽度固定为 [width]（屏宽 90%），整体水平居中于**屏幕**（由 [offsetX] 补偿，
 *   因为它渲染在左列内，左列中心并不等于屏幕中心）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongCaptionLine(
    currentSong: Song?,
    width: androidx.compose.ui.unit.Dp,
    offsetX: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = FontSize.button(),
) {
    val title = currentSong?.title?.takeIf { it.isNotBlank() }
    val artist = currentSong?.artist?.takeIf { it.isNotBlank() }
    val noSongLabel = stringResource(R.string.player_no_song_selected)
    val text = remember(title, artist, noSongLabel) {
        buildAnnotatedString {
            if (title == null) {
                append(noSongLabel)
            } else {
                withStyle(SpanStyle(color = NasMusicColors.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                    append(title)
                }
                if (artist != null) {
                    withStyle(SpanStyle(color = NasMusicColors.TextSecondary, fontWeight = FontWeight.Normal)) {
                        append(" - ")
                        append(artist)
                    }
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .requiredWidth(width)
            .offset(x = offsetX),
        contentAlignment = Alignment.Center
    ) {
        // 2026-10-30 用户定稿：超长不省略号截断、不换行 → 跑马灯滚动展示。
        // AnnotatedString 重载保留「标题（亮色加粗）- 歌手（次要色）」的双色层级；
        // 未溢出时静态居中，溢出时循环滚动（行为见 [MarqueeText]）。
        MarqueeText(
            text = text,
            fontSize = fontSize,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 *
 * 设计约束：
 * - **不遮挡控制栏**：作为底部区的一行独立插入，位于进度条之上
 * - **可聚焦的重试 / 关闭按钮**：不依赖任何额外按键，方向键移动 + OK 激活即可
 * - 不阻塞返回键：本组件不注册 BackHandler，返回键行为保持原有层级返回
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                NasMusicColors.Primary.copy(alpha = 0.14f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = NasMusicColors.TextPrimary,
            fontSize = FontSize.small(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        FocusableSurface(
            onClick = onRetry,
            shape = RoundedCornerShape(6.dp),
            focusedScale = PlayerVisualSpec.FOCUS_SCALE,
            animationDurationMs = PlayerVisualSpec.FOCUS_ANIM_MS,
            containerColor = NasMusicColors.Surface.copy(alpha = 0.35f),
            focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.35f),
            contentColor = NasMusicColors.TextPrimary,
            focusedContentColor = NasMusicColors.TextPrimary
        ) {
            Text(
                text = stringResource(R.string.network_radio_retry),
                fontSize = FontSize.small(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        FocusableSurface(
            onClick = onDismiss,
            shape = RoundedCornerShape(6.dp),
            focusedScale = PlayerVisualSpec.FOCUS_SCALE,
            animationDurationMs = PlayerVisualSpec.FOCUS_ANIM_MS,
            containerColor = Color.Transparent,
            focusedContainerColor = NasMusicColors.FocusOverlay,
            contentColor = NasMusicColors.TextSecondary,
            focusedContentColor = NasMusicColors.TextPrimary
        ) {
            Text(
                text = stringResource(R.string.song_info_close),
                fontSize = FontSize.small(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CoverColumn(
    currentSong: Song?,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    coverCandidates: List<String> = emptyList(),
    isPlaying: Boolean = false,
    coverFilterEnabled: Boolean = false,
    coverFilterBlurRadius: Float = 8f,
    coverFilterDarkOverlay: Float = 0.3f,
    technicalInfo: com.lnh.nasmusictv.data.model.SongTechnicalInfo? = null,
    onLoadTechnicalInfo: () -> Unit = {},
    showInfoPanel: Boolean = false,
    onToggleInfoPanel: () -> Unit = {},
    onSearchSong: (String) -> Unit = {},
    onSearchArtist: (String) -> Unit = {},
    // 音质/码率角标已移到底部控制栏（QualityBadgeButton），
    // CoverColumn 不再需要 qualityLabel / onOpenQuality
    // 网易云黑胶：手机端唱针微交互，TV 静态示意
    tonearmAnimated: Boolean = false,
    // 无歌词（黑胶居中放大）时，把标题/歌手收进黑胶容器、置于盘正下方
    infoUnderDisc: Boolean = false,
    /**
     * 左列在屏幕中的**起始横向留白**（dp）——2026-10-28 新增：
     * 无歌词态单行「标题 - 歌手」要横向居中于**屏幕**，而它渲染在左列里，
     * 需要知道左列左缘位置才能算出补偿位移（`屏宽/2 - (paddingStart + 列宽/2)`）。
     */
    contentPaddingStart: androidx.compose.ui.unit.Dp = 32.dp,
    /** 减弱动效总闸；为 true 时取消黑胶进入回弹、光晕呼吸（§11-8） */
    reduceMotion: Boolean = false,
    /** 页面被弹窗遮挡时暂停光晕呼吸（§10.3） */
    paused: Boolean = false,
    /** 5s 无操作沉浸隐藏态 —— 黑胶放大，音质角标隐去 */
    idleHidden: Boolean = false,
    /** 沉浸隐藏的整体淡出进度（1f = 全显，0f = 全隐），由外层统一驱动，保证节奏一致 */
    chromeAlpha: Float = 1f,
    /**
     * 2026-10-28 用户定稿：黑胶盘**整屏垂直居中**补偿量（由调用方算好传入）。
     * 底部进度条/控制栏占位 + 可视态顶栏，都会让内容 Row 的中线高于屏幕中线。
     */
    centerOffsetExtra: androidx.compose.ui.unit.Dp = 0.dp,
    /** 盘外光晕色（封面取色的强调色；取色不可用时传品牌默认色） */
    glowColor: Color = NasMusicColors.PrimaryVariant,
    /**
     * 2026-10-30 用户定稿：播放进度 0..1 —— 唱针径向位置与其绑定
     * （隐藏态唱臂针尖从唱片外缘向内圈匀速内移，见 [com.lnh.nasmusictv.ui.components.VinylDisc]）。
     */
    playbackProgress: Float = 0f,
    /**
     * 2026-10-28 用户定稿：切歌方向 →「换碟」滑动方向。
     * `+1` = 下一曲（整盘从右往左滑），`-1` = 上一曲（从左往右滑）。
     */
    discSlideDirection: Int = 1,
    /**
     * 2026-10-30 用户定稿：当前歌曲稳定标识（song.id）—— 透传给 [VinylDisc]，
     * 变化时触发唱臂「弹开 → 悬停 → 落下」三段式联动。
     */
    songKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 封面 / 信息面板切换（按下"信息"后占用封面空间显示歌曲详情）
        if (showInfoPanel) {
            SongInfoPanel(
                song = currentSong,
                technicalInfo = technicalInfo,
                onDismiss = onToggleInfoPanel,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 网易云风格：黑胶唱盘——封面嵌入盘心，整盘缓转；唱针右上伸出
            // （手机端落下/抬起微交互，TV 静态示意）。
            // **黑胶盘取消可点击/可聚焦**（纯展示元素，OK 键不再切换沉浸模式）。
            // 黑胶尺寸改为「受高度约束」——取 min(列宽×0.9, 列高×比例) 的正方形，
            // 不再随列宽无限放大（无歌词时占满左列曾导致黑胶过大 / 溢出屏幕高度）。
            // 进入时整盘从 0.82 缩放到 1.0（轻微回弹），呼应「共享元素放大 / 聚焦」切入感。
            val discEnter = remember { Animatable(if (reduceMotion) 1f else 0.82f) }
            LaunchedEffect(reduceMotion) {
                // 减弱动效：不跑回弹，直接停在终态（§11-8「关闭页面转场」）
                if (reduceMotion) {
                    discEnter.snapTo(1f)
                } else {
                    discEnter.animateTo(1f, tween(durationMillis = 460, easing = BackOutEasing))
                }
            }
            // 沉浸隐藏时黑胶放大（隐藏转场的主体特效）。
            // 走 graphicsLayer 缩放 —— 不改布局占位，因此不会把盘下的标题/歌词挤位。
            // 2026-10-28 用户定稿：**两种模式都放大** —— 歌词关闭态盘缘与标题的间距
            // 按盘径比例（CAPTION_GAP_TO_DISC × 0.48×屏高）留足，放大溢出的下半圈
            // 不会压到标题；放大本身正是隐藏/显示的"特效动画"主体。
            val idleScale by animateFloatAsState(
                targetValue = if (idleHidden) PlayerVisualSpec.IDLE_DISC_SCALE else 1f,
                animationSpec = if (reduceMotion) {
                    snap()
                } else {
                    tween(PlayerVisualSpec.IDLE_HIDE_MS, easing = FastOutSlowInEasing)
                },
                label = "discIdleScale"
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // 2026-10-30 用户定稿「黑胶盘还是太小」：盘径改为**按屏幕高度定径** ——
                // 可视态 = 屏高 × 0.60，隐藏态（IDLE_DISC_SCALE）= 屏高 × 0.70。
                // 此前按列高 0.62 计算，列高小于屏高，实际只占屏高 ~50%。
                // W 系数仍按列宽收口，防止窄屏横向溢出。
                val columnWidth = maxWidth
                val contextForCenter = androidx.compose.ui.platform.LocalContext.current
                val screenHdp = with(LocalDensity.current) {
                    contextForCenter.resources.displayMetrics.heightPixels.toDp()
                }
                val discSize = minOf(
                    maxWidth * if (infoUnderDisc) {
                        PlayerVisualSpec.DISC_FRACTION_W_NO_LYRICS
                    } else {
                        PlayerVisualSpec.DISC_FRACTION_W_LYRICS
                    },
                    // 屏高定径：歌词可见 0.60 / 歌词关闭 0.48（网易云 TV 参考图实测定标）
                    screenHdp * if (infoUnderDisc) {
                        PlayerVisualSpec.DISC_HEIGHT_FRACTION_NO_LYRICS
                    } else {
                        PlayerVisualSpec.DISC_HEIGHT_FRACTION
                    }
                )
                Column(
                    // 无歌词：整块（黑胶 + 标题 + 歌手）宽度收敛到黑胶直径，标题/歌手自然落在盘下
                    modifier = Modifier.width(if (infoUnderDisc) discSize else maxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── 2026-10-29 黑胶盘**自校准绝对居中**（修"黑胶盘没有上下绝对居中"）──
                    // 旧方案靠公式推算补偿（底部预留区/2 - 顶栏/2 - 上下padding差/2），
                    // 任何一处布局假设与实际不符（顶栏实测时机、错误横幅、padding 改动）都会漂移。
                    // 新方案**直接测量**：用一层不带 offset 的测量 Box 拿到盘槽位在窗口坐标里的
                    // 真实中心，与**真实屏幕高度**（displayMetrics.heightPixels，TV 上
                    // LocalConfiguration 不可信）的中线求差 → 一次性修正 offset。
                    // 测量 Box 不含 offset，因此修正不改变槽位位置 → 无反馈回路，一帧收敛。
                    // 2026-10-30 用户定稿：**无歌词态同样绝对居中**（原先只对有歌词态生效）。
                    // ⚠️ 实测（v14 弦长/封面质心法）盘心仍偏高 ~11dp —— 根因：
                    // 校准测的是**槽位 Box** 中心，而公式补偿 centerOffsetExtra 打在
                    // Box 内部的盘 offset 上，校准吸收不了它 → 盘心 = 屏心 + centerOffsetExtra。
                    // 修正：adjust = 屏心中线 − 槽位中心 − centerOffsetExtra，
                    // 使「槽位中心 + centerOffsetExtra + adjust」恒等于屏心中线。
                    val densityForCenter = LocalDensity.current
                    val screenHeightPx = remember {
                        contextForCenter.resources.displayMetrics.heightPixels
                    }
                    var slotCenterY by remember { mutableStateOf(Float.NaN) }
                    val centerOffsetExtraPx = with(densityForCenter) {
                        centerOffsetExtra.toPx()
                    }
                    val discAutoAdjust =
                        if (!slotCenterY.isNaN() && screenHeightPx > 0) {
                            with(densityForCenter) {
                                (screenHeightPx / 2f - slotCenterY - centerOffsetExtraPx).toDp()
                            }
                        } else {
                            0.dp
                        }
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            slotCenterY = coords.positionInWindow().y + coords.size.height / 2f
                        }
                    ) {
                        VinylDisc(
                            coverCandidates = coverCandidates,
                            isPlaying = isPlaying,
                            tonearmAnimated = tonearmAnimated,
                            // 2026-10-30 二次定稿：唱臂**常驻可见** —— 本轮新增的
                            // 「暂停弹开悬停 / 播放落下 / 切歌三段式」都发生在可视态
                            // （点按即唤醒屏幕），唱臂不常驻就看不到这些联动
                            //（取代此前「仅 5s 隐藏态加载」的口径）。
                            tonearmVisible = true,
                            reduceMotion = reduceMotion,
                            paused = paused,
                            glowColor = glowColor,
                            // 2026-10-28：换碟滑动的运动方向（上一曲 左→右 / 下一曲 右→左）
                            slideDirection = discSlideDirection,
                            // 2026-10-30 用户定稿：唱针径向位置与播放进度绑定（外缘→内圈）
                            playbackProgress = playbackProgress,
                            modifier = Modifier
                                .size(discSize)
                                // 公式补偿（保留，被自校准吸收）+ 自校准修正 → 盘心恒等于屏幕中线；
                                // 歌词关闭态再上移 [PlayerVisualSpec.CAPTION_COMBO_CENTER]×盘径 ——
                                // 参考「盘 + 标题」**组合整体垂直居中**（网易云 TV 参考图：盘心 0.43 屏高）。
                                .offset(
                                    y = centerOffsetExtra + discAutoAdjust -
                                        (if (infoUnderDisc) {
                                            discSize * PlayerVisualSpec.CAPTION_COMBO_CENTER
                                        } else {
                                            0.dp
                                        })
                                )
                                .graphicsLayer {
                                    val s = discEnter.value * idleScale
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                    }
                    // 无歌词状态下标题 + 歌手放在黑胶盘正下方
                    // 2026-10-28 用户定稿（第三轮）：
                    //   ① 无歌词态**不滚动**（长标题一律省略号，不做跑马灯）；
                    //   ② 两行「标题（大字）/ 歌手（小字）」合并成**单行「标题 - 歌手」**；
                    //   ③ 文字容器宽度 = **屏宽 90%**，水平居中于屏幕（而非居中于左列）。
                    // 2026-10-30 用户定稿「标题/歌手太下了，往黑胶盘上移保持视觉比例」：
                    //   盘改**屏高中线绝对居中**后标题跟随盘缘，删除旧「标题大字行」
                    //   行高占位（titleLineReserve ≈30dp）+ 间距收紧到 16dp ——
                    //   标题上提约 60dp，紧贴盘缘下方；隐藏态盘放大的下半圈溢出
                    //   （≈22dp）由「16dp 间距 + 标题联动下移 18dp」覆盖，不压字。
                    if (infoUnderDisc) {
                        val titleIdle by animateFloatAsState(
                            targetValue = if (idleHidden) 1f else 0f,
                            animationSpec = if (reduceMotion) {
                                snap()
                            } else {
                                tween(PlayerVisualSpec.IDLE_HIDE_MS, easing = FastOutSlowInEasing)
                            },
                            label = "titleIdleShift"
                        )
                        val titleShiftPx = with(LocalDensity.current) {
                            PlayerVisualSpec.IDLE_TITLE_SHIFT.toPx()
                        }
                        // ⚠️ 屏宽**必须用同一 density 坐标系**推算：
                        // `LocalConfiguration.screenWidthDp` 在本机（TV/模拟器 compact 密度）
                        // 报的是另一套 dp（505dp），与实际布局 dp（1097dp）不一致，
                        // 直接用它会让「90% 屏宽」缩成 41% 且整体右偏。
                        // 无歌词态左列 = 整行宽（weight 1f），故：屏宽 ≈ 列宽 + 左右留白。
                        val effectiveScreenWidth = columnWidth + contentPaddingStart * 2
                        // 列中心此时恒等于屏幕中心 → 补偿为 0（保留算式，防止以后布局变化）
                        val captionDx =
                            effectiveScreenWidth / 2 - (contentPaddingStart + columnWidth / 2)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // 标题跟随黑胶盘的完整偏移（自校准 + 组合居中上移）：
                            // 保证间距从**盘的实际视觉下缘**起算，而不是布局槽位下缘
                            // —— 否则盘被下移居中后会压到标题。
                            // offset 是布局级、graphicsLayer 是绘制级，两者叠加不冲突。
                            modifier = Modifier
                                .offset(
                                    y = centerOffsetExtra + discAutoAdjust -
                                        discSize * PlayerVisualSpec.CAPTION_COMBO_CENTER
                                )
                                .graphicsLayer {
                                    translationY = titleIdle * titleShiftPx
                                    val ts = 1f + (PlayerVisualSpec.IDLE_TITLE_SCALE - 1f) * titleIdle
                                    scaleX = ts
                                    scaleY = ts
                                }
                        ) {
                            // 盘底→标题顶间距 = 盘径 × 0.232（网易云 TV 参考图实测定标）
                            Spacer(
                                modifier = Modifier.height(
                                    discSize * PlayerVisualSpec.CAPTION_GAP_TO_DISC
                                )
                            )
                            SongCaptionLine(
                                currentSong = currentSong,
                                width = effectiveScreenWidth * PlayerVisualSpec.CAPTION_WIDTH_FRACTION,
                                offsetX = captionDx,
                                // 标题字高 = 盘径 × 0.094（参考图：46px 字高 / 488px 盘径）
                                fontSize = with(LocalDensity.current) {
                                    (discSize * PlayerVisualSpec.CAPTION_FONT_TO_DISC).toSp()
                                }
                            )
                        }
                    }
                }
            }
        }

        // **有歌词时标题 + 歌手移到右侧歌词区顶部**（见右列）——
        // 让它与歌词同属一个视觉区块，读词时视线不必在左右两列之间来回跳；
        // 黑胶一侧因此只剩封面本身，构图更干净。
        // 无歌词（infoUnderDisc）时标题仍在黑胶正下方，由上面 [infoUnderDisc] 分支渲染。

        // **信息按钮 + 歌曲来源已迁到下方控制条「频谱」右侧**
        // （见 NowPlayingBottomControls 的 trailingInfo 插槽）。
        // 这里只保留音质标识——它是被动展示项，跟着歌曲走、不适合挤进按钮行。
        //
        // （2026-10-28 用户定稿）：音质/码率角标**移出黑胶下方**，
        // 改到底部控制栏左簇「MTV」边上（见 [QualityBadgeButton] 与
        // NowPlayingBottomControls 的 qualitySlot）。黑胶一侧因此只剩封面本身，
        // 与"隐藏时黑胶是唯一焦点"的沉浸语言一致；角标作为"播放控制"的一员，
        // 也比挂在展示区更符合它的交互属性（网络歌曲可点、弹档位面板）。
    }
}

/**
 * 音质 / 码率角标（从 [CoverColumn] 抽出，改挂底部控制栏「MTV」旁）。
 *
 * 两种形态：
 * - **网络歌曲**：可点击 → 弹出音质选择面板（显示当前档位标签，如「无损」/「320k」）
 * - **本地 / NAS / 下载歌曲**：只读展示**真实码率**（如「♪ 320k」/「♪ FLAC」），
 *   不可点击 —— 这类歌曲的码率由文件本身决定，无法切换，但用户理应看得到。
 *   数据来自 `Song.bitrate`（NAS 适配器解析时填充；飞牛为 0=未知，不显示）
 *   与 `Song.resolvedQuality`（已下载歌曲的实际落盘档位）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityBadgeButton(
    currentSong: Song?,
    qualityLabel: String,
    onOpenQuality: () -> Unit,
) {
    val autoLabel = stringResource(R.string.quality_badge_auto)
    val losslessLabel = stringResource(R.string.quality_badge_lossless)
    val qualityBadgeText = com.lnh.nasmusictv.ui.components.qualityBadgeLabel(
        currentSong, qualityLabel, autoLabel, losslessLabel
    ) ?: return
    if (currentSong?.isNetworkSong == true) {
        // 可点击：网络歌曲支持切换档位
        FocusableSurface(
            onClick = onOpenQuality,
            shape = RoundedCornerShape(6.dp),
            focusedScale = 1.08f,
            animationDurationMs = 150,
            containerColor = NasMusicColors.Surface.copy(alpha = 0.3f),
            focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.3f),
            contentColor = NasMusicColors.Primary,
            focusedContentColor = NasMusicColors.Primary
        ) {
            Text(
                text = qualityBadgeText,
                color = NasMusicColors.Primary,
                fontSize = FontSize.small(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    } else {
        // 只读：本地/NAS 歌曲码率固定，展示但不可点击
        Text(
            text = qualityBadgeText,
            color = NasMusicColors.TextSecondary,
            fontSize = FontSize.small(),
            modifier = Modifier
                .background(
                    NasMusicColors.Surface.copy(alpha = 0.3f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.12f,
        animationDurationMs = 150,
        containerColor = if (isFavorite) NasMusicColors.Warning.copy(alpha = 0.2f) else Color.Transparent,
        focusedContainerColor = NasMusicColors.Warning.copy(alpha = 0.3f),
        contentColor = if (isFavorite) NasMusicColors.Warning else NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Warning,
        pressedScale = 0.95f
    ) {
        Text(
            text = if (isFavorite) "♥" else "♡",
            color = LocalFocusableContentColor.current,
            fontSize = FontSize.subtitle(),
            modifier = Modifier.padding(10.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceTag(
    label: String,
    available: Boolean,
    selected: Boolean,
    // L11 修复：onClick 允许 null —— 纯展示标签（如 LOCAL_FILE 来源标记）不应可聚焦
    onClick: (() -> Unit)?
) {
    FocusableSurface(
        onClick = onClick?.let { cb -> { if (available) cb() } } ?: {},
        modifier = Modifier,
        shape = RoundedCornerShape(6.dp),
        focusedScale = 1.1f,
        animationDurationMs = 150,
        // 浮动标签化：去掉胶囊背景，文字直接浮在页面背景上（沉浸式）；
        // 选中=品牌红文字、焦点=文字变红 + 极淡红底（TV 上仍清晰可辨），不再画"框"
        containerColor = Color.Transparent,
        focusedContainerColor = if (selected) Color.Transparent
                                else NasMusicColors.Primary.copy(alpha = 0.16f),
        contentColor = if (!available) NasMusicColors.TextSecondary.copy(alpha = 0.4f)
                       else if (selected) NasMusicColors.Primary
                       else NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary,
        pressedScale = 0.95f,
        showFocusBorder = available && onClick != null  // L11：无行为标签不画焦点框
    ) {
        // 2026-10-30 用户定稿「按钮文字绝对居中」：包一层 Box(contentAlignment=Center)
        // 并用对称 padding —— 不依赖 Text 基线/行高，任意字体度量下都水平垂直居中。
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (!available) NasMusicColors.TextSecondary.copy(alpha = 0.4f)
                        else if (selected) NasMusicColors.Primary
                        else NasMusicColors.TextPrimary,
                fontSize = FontSize.small(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * F2-2b：睡眠定时设置弹窗（紧凑布局，TV D-Pad / 手机点选通用）。
 * 标题"定时关闭"；中间 - [N 分钟] + 步进；下方 15 / 30 / OK / 取消 一行四个紧凑按钮。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SleepTimerPickerDialog(
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by remember { mutableStateOf(30) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(NasMusicColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.notif_sleep_timer_start),
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.subtitle(),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            // - / N 分钟 / +
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                com.lnh.nasmusictv.ui.components.FocusableSurface(
                    onClick = { if (minutes > 5) minutes -= 5 },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "-",
                        color = NasMusicColors.TextPrimary,
                        fontSize = FontSize.title(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.np_sleep_timer_min, minutes),
                    color = NasMusicColors.Primary,
                    fontSize = FontSize.subtitle(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                com.lnh.nasmusictv.ui.components.FocusableSurface(
                    onClick = { if (minutes < 300) minutes += 5 },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "+",
                        color = NasMusicColors.TextPrimary,
                        fontSize = FontSize.title(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 快捷档：15 / 30
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30).forEach { min ->
                    com.lnh.nasmusictv.ui.components.FocusableSurface(
                        onClick = {
                            minutes = min
                            onStart(min)
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.np_sleep_timer_min, min),
                            color = NasMusicColors.TextPrimary,
                            fontSize = FontSize.body(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 确认 / 取消定时
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.lnh.nasmusictv.ui.components.FocusableSurface(
                    onClick = { onStart(minutes) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "OK",
                        color = NasMusicColors.Primary,
                        fontSize = FontSize.body(),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                // 关闭定时（常驻，未运行时点击为 no-op 安全）
                com.lnh.nasmusictv.ui.components.FocusableSurface(
                    onClick = onCancel,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.np_sleep_timer_cancel),
                        color = NasMusicColors.Warning,
                        fontSize = FontSize.body(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}


/**
 * 2026-10-29 新增：歌词工具行（来源 内嵌/本地/在线/缓存 + 高亮模式 + 字号 + 睡眠定时）。
 *
 * 从右列内联代码抽成独立组件 —— 用户定稿把它移到**左列黑胶盘上方**（非沉浸模式，
 * 由调用方以 overlay 方式贴左列顶部），沉浸模式左列无黑胶、仍放右列顶部。
 * 左对齐 + 溢出可横向滚动（手机横屏窄列兜底），语义与拆分前完全一致。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LyricsSourceToolbar(
    lyrics: Lyrics?,
    lyricsAvailability: com.lnh.nasmusictv.data.model.LyricsAvailability,
    highlightMode: LyricsHighlightMode,
    onChangeHighlightMode: (LyricsHighlightMode) -> Unit,
    lyricsFontScale: Float,
    onLyricsFontScaleChange: (Float) -> Unit,
    sleepTimerState: SleepTimerController.State?,
    onShowSleepTimer: () -> Unit,
    onSwitchLyricsSource: (com.lnh.nasmusictv.data.model.LyricsSource) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentSource = lyrics?.source
            SourceTag(
                label = stringResource(com.lnh.nasmusictv.data.model.LyricsSource.EMBEDDED.displayNameRes),
                available = lyricsAvailability.hasBackend,
                selected = currentSource == com.lnh.nasmusictv.data.model.LyricsSource.EMBEDDED,
                onClick = { onSwitchLyricsSource(com.lnh.nasmusictv.data.model.LyricsSource.EMBEDDED) }
            )
            // 2026-10-30 用户定稿：**隐藏「本地」按钮** —— 本地歌词无切换入口
            //（来源由本地文件自动挂载），按钮恒为不可点（锁定态），保留只会
            // 占一档焦点、让极简遥控器导航多一站，直接不再显示。
            Spacer(modifier = Modifier.width(6.dp))
            SourceTag(
                label = stringResource(com.lnh.nasmusictv.data.model.LyricsSource.NETWORK.displayNameRes),
                available = true,
                selected = currentSource == com.lnh.nasmusictv.data.model.LyricsSource.NETWORK,
                onClick = { onSwitchLyricsSource(com.lnh.nasmusictv.data.model.LyricsSource.NETWORK) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            SourceTag(
                label = stringResource(com.lnh.nasmusictv.data.model.LyricsSource.CACHED.displayNameRes),
                available = lyricsAvailability.hasCached,
                selected = currentSource == com.lnh.nasmusictv.data.model.LyricsSource.CACHED,
                onClick = { onSwitchLyricsSource(com.lnh.nasmusictv.data.model.LyricsSource.CACHED) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            // 高亮模式切换按钮
            SourceTag(
                label = if (highlightMode == LyricsHighlightMode.WORD_BY_WORD) stringResource(R.string.player_highlight_word) else stringResource(R.string.player_highlight_line),
                available = true,
                selected = highlightMode == LyricsHighlightMode.WORD_BY_WORD,
                onClick = {
                    val newMode = if (highlightMode == LyricsHighlightMode.WORD_BY_WORD) {
                        LyricsHighlightMode.LINE_BY_LINE
                    } else {
                        LyricsHighlightMode.WORD_BY_WORD
                    }
                    onChangeHighlightMode(newMode)
                }
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 歌词字体大小切换
            val fontLabels = listOf("A", "A+", "A++", "A+++")
            val fontScaleIdx = when (lyricsFontScale) {
                0.7f -> 0
                1.0f -> 1
                1.3f -> 2
                1.6f -> 3
                else -> 0   // 2026-10-30 用户定稿：默认档 = A（最小档 0.7f）
            }
            SourceTag(
                label = fontLabels[fontScaleIdx],
                available = true,
                selected = false,
                onClick = {
                    val scales = listOf(0.7f, 1.0f, 1.3f, 1.6f)
                    val next = (fontScaleIdx + 1) % scales.size
                    onLyricsFontScaleChange(scales[next])
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            // F2-2b：睡眠定时按钮（未启动"定时 -"、运行中橙色剩余分钟）
            SourceTag(
                label = when (val st = sleepTimerState) {
                    is SleepTimerController.State.Running ->
                        stringResource(
                            R.string.np_sleep_timer_on,
                            st.remainingMinutesDisplay()
                        )
                    else -> stringResource(R.string.np_sleep_timer_off)
                },
                available = true,
                selected = sleepTimerState is SleepTimerController.State.Running,
                onClick = onShowSleepTimer
            )
        }
    }
}
