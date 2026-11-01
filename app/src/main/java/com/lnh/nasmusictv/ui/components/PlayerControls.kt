package com.lnh.nasmusictv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.lnh.nasmusictv.R

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.lnh.nasmusictv.data.model.PlayMode
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec
import com.lnh.nasmusictv.util.TimeUtils
// ─── Progress Section (standalone) ────────────────────────────────────────────

/**
 * 独立进度条组件，包含进度轨道、当前进度的「呼吸灯」柔光指示和时间标签。
 * 用于 NowPlayingScreen 底部全宽显示，也可复用至其他页面。
 *
 * 2026-10-29 用户定稿：移除原圆形滑块 thumb，改为在当前进度位置的柔光点 ——
 * 播放时轻微呼吸，暂停/减弱动效时减弱为静态微光，直播态不显示。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
/**
 * 进度条缓冲脉冲（P1-7「缓冲时进度条脉冲或 loading 呼吸」）。
 *
 * ⚠️ [animate] 为 false 时**不创建** InfiniteTransition —— 非缓冲期本机没有任何
 * 动画 tick 在跑（§11-2「页面不可见/非必要时停止动画」、禁止事项 14）。
 * 减弱动效时退化为静态常量：缓冲仍**可见**（静态灰条），只是不呼吸。
 */
@Composable
private fun progressBufferPulse(animate: Boolean): Float {
    if (!animate) return 0.45f
    val transition = rememberInfiniteTransition(label = "bufferPulse")
    return transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bufferPulseAlpha"
    ).value
}

/**
 * 进度「呼吸灯」指示的呼吸相位（2026-10-29 用户定稿：移除圆形滑块 thumb，
 * 改为在当前进度位置的柔光点，播放时轻微呼吸、暂停时减弱为静态微光）。
 *
 * ⚠️ [animate] 为 false（暂停 / 减弱动效）时**不创建** InfiniteTransition ——
 * 非播放态本机没有任何动画 tick 在跑（§11-2「页面不可见/非必要时停止动画」、
 * 禁止事项 14）。返回 0..1 的 alternate 相位；调用方据此把暂停态退化为常量微光。
 */
@Composable
private fun progressBreathingLight(animate: Boolean): Float {
    if (!animate) return 0f
    val transition = rememberInfiniteTransition(label = "progressBreath")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PlayerVisualSpec.PROGRESS_BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progressBreathPhase"
    ).value
}

@Composable
fun ProgressSection(
    progressMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onProgressFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    timesBelow: Boolean = false,
    isLive: Boolean = false,
    /**
     * 2026-10-29 用户定稿「呼吸灯」：播放中当前进度柔光点轻微呼吸，
     * 暂停时减弱为静态微光。默认 true（其它复用页面未传参时保持呼吸可视）。
     */
    isPlaying: Boolean = true,
    reduceMotion: Boolean = false,
    /** P0-6 / P1-7：是否正在缓冲（显示缓冲态 + 脉冲呼吸） */
    buffering: Boolean = false,
    /**
     * 2026-10-29 用户定稿（播放页）：**取消两端时间显示**，进度条直接占满整行、
     * 落回原「时间标签行」的垂直位置 —— 底部控制栏因此整体变矮（去掉一整行文字）。
     * 其它复用页面不传此参数 → 保持原有「时间 + 轨道」版式不受影响。
     */
    showTimes: Boolean = true
) {
    var isProgressFocused by remember { mutableStateOf(false) }
    val progressSpacer = if (compact) 8.dp else 16.dp
    val timeFont = if (compact) FontSize.caption() else FontSize.small()
    val timeFontFocused = if (compact) FontSize.small() else FontSize.body()
    val progressFocusRequester = remember { FocusRequester() }

    // 手势处理器读取最新值（progressMs 每秒更新，不能作为 pointerInput key 否则手势每秒重启）
    val latestProgressMs by rememberUpdatedState(progressMs)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestIsLive by rememberUpdatedState(isLive)
    val latestOnSeek by rememberUpdatedState(onSeek)

    // 时间标签（局部复用：常规版式在轨道两侧；timesBelow 版式在轨道下方两端）
    val currentTimeLabel: @Composable () -> Unit = {
        Text(
            text = if (isLive) "\u25CF " + stringResource(R.string.network_radio_live)
                   else TimeUtils.formatDuration(progressMs),
            color = if (isLive) NasMusicColors.Primary
                    else if (isProgressFocused) NasMusicColors.TextPrimary else NasMusicColors.TextSecondary,
            fontSize = if (isProgressFocused) timeFontFocused else timeFont
        )
    }
    val totalTimeLabel: @Composable () -> Unit = {
        Text(
            text = if (isLive) "\u25CF LIVE" else TimeUtils.formatDuration(durationMs),
            color = if (isProgressFocused) NasMusicColors.TextPrimary else NasMusicColors.TextSecondary,
            fontSize = if (isProgressFocused) timeFontFocused else timeFont
        )
    }
    // 进度条聚焦放大（§10.4「进度条焦点放大 1.05–1.10，150–200ms」）。
    // 用 graphicsLayer 的 scale —— 不改 height/宽高，不触发重新测量，
    // 因此不会把下方控制栏顶开（禁止事项 13「动画不得导致布局抖动」）。
    val trackScale by animateFloatAsState(
        targetValue = if (isProgressFocused) PlayerVisualSpec.PROGRESS_FOCUS_SCALE else 1f,
        animationSpec = tween(if (reduceMotion) 0 else PlayerVisualSpec.FOCUS_ANIM_MS),
        label = "progressFocusScale"
    )

    // 进度轨道（局部复用：外层 Modifier 由版式决定 weight 缩放或全宽）
    val progressTrack: @Composable (Modifier) -> Unit = { trackModifier ->
        Row(
            modifier = trackModifier
                // 2026-10-29 用户反馈"底部工具栏太高"：轨道命中区 36 → 24dp。
                // 视觉轨道粗细不变（内部自绘），只收窄焦点/触摸命中区，
                // 播放页底部整体高度随之下降（自适应任何屏高，非硬编码总高）。
                .height(24.dp)
                .graphicsLayer {
                    scaleX = trackScale
                    scaleY = trackScale
                }
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(progressFocusRequester)
                .focusable()
                .onFocusChanged {
                    isProgressFocused = it.isFocused
                    onProgressFocusChanged(it.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp, Key.DirectionDown -> {
                                isProgressFocused = false
                                false
                            }
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (durationMs > 0 && !isLive) {
                                    isProgressFocused = true
                                    val delta = if (event.key == Key.DirectionLeft) -15000L else 15000L
                                    val newPosition = (progressMs + delta).coerceIn(0, durationMs)
                                    onSeek(newPosition)
                                }
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                isProgressFocused = true
                                false
                            }
                            else -> {
                                isProgressFocused = true
                                false
                            }
                        }
                    } else false
                }
                .pointerInput(Unit) {
                    // key 用 Unit：progressMs 每秒更新，作为 key 会导致手势每秒重启（手机触摸拖动失效）
                    // 2026-09-25 审查修复（#14）：不得把 latest* 委托的当前值冻结进局部 val ——
                    // pointerInput(Unit) 终身只执行一次，首组合时 durationMs=0 会让手势 seek 永久失效。
                    // 必须在手势回调内直接读委托（动态取最新值）。
                    detectTapGestures { offset ->
                        val duration = latestDurationMs
                        val live = latestIsLive
                        if (duration > 0 && !live) {
                            val seekTo = (offset.x / size.width * duration).toLong().coerceIn(0, duration)
                            latestOnSeek(seekTo)
                        }
                    }
                }
                // 手机触摸：拖动 seek（直播态禁用）
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val duration = latestDurationMs
                            if (duration > 0 && !latestIsLive) {
                                latestOnSeek((offset.x / size.width * duration).toLong().coerceIn(0, duration))
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val duration = latestDurationMs
                            if (duration > 0 && !latestIsLive) {
                                latestOnSeek((change.position.x / size.width * duration).toLong().coerceIn(0, duration))
                            }
                        }
                    )
                }
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
            // 2026-10-28 用户定稿：进度条**不再有胶囊底板**（原先一层 Surface 15% 的
            // 横向底槽在播放页背景上显出一条灰带）。整条只保留细轨道 + 红色进度。
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 直播态：进度填充置 0（下方呼吸灯在直播态也不显示）
                val progress = if (isLive) 0f
                               else if (durationMs > 0) progressMs.toFloat() / durationMs else 0f

                // 背景轨道（网易云同款细灰轨；2026-10-28 收细到 3dp，
                // 底色改为**半透明白**——底板透明后靠它自己撑出可见的"未播放段"）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlayerVisualSpec.PROGRESS_TRACK_HEIGHT)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NasMusicColors.TextSecondary.copy(alpha = 0.22f))
                )
                // P1-7 缓冲脉冲：缓冲中让**整条轨道**做呼吸，与"播放进度"在视觉上明确区分
                // （§7.2「缓冲态不与播放进度混淆」）—— 轨道底色呼吸，红色填充照常表达已播放量。
                // ⚠️ 只在 buffering 时创建 InfiniteTransition：非缓冲期本机零动画 tick（§11-2）。
                val bufferPulse = progressBufferPulse(
                    animate = buffering && !reduceMotion
                )
                if (buffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PlayerVisualSpec.PROGRESS_TRACK_HEIGHT)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(2.dp))
                            .graphicsLayer { alpha = bufferPulse }
                            .background(NasMusicColors.TextSecondary)
                    )
                }
                // 进度填充（网易云：纯品牌红，不再用渐变）
                //
                // 2026-10-30 用户定稿「光晕中心必须与当前进度位置水平对齐」：
                // 呼吸灯光晕直接画在**填充条右缘**（drawBehind）—— 填充条右缘 x
                // 就等于当前进度位置，光晕圆心 = (size.width, size.height/2) 与进度
                // **天然重合**，从根上消除此前 offset 计算 / 端点 clamp 造成的错位。
                // ⚠️ drawBehind 必须放在 clip **之前**：clip 只作用于链中位于它之后
                //    的绘制层，放后面会把光晕裁成 3dp 高的细条。
                // 2026-10-30 用户二次定稿「所有进度条可见状态下都应用呼吸灯」：
                // 呼吸不再区分播放/暂停 —— 只要进度条可见（非直播）就脉动；
                // 仅减弱动效退静态。本机不再有"暂停时静止"的分支。
                val breath = if (!isLive && !reduceMotion) {
                    progressBreathingLight(animate = true)
                } else 0f
                // 2026-10-30：呼吸灯圆保留，但 progress≈0（width≈0）时**不画**——
                // 在 width=0 盒子里 radialGradient 渲染成纯黑圆块（切歌瞬间的大黑块）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(PlayerVisualSpec.PROGRESS_TRACK_HEIGHT)
                        .align(Alignment.CenterStart)
                        .then(
                            if (!isLive) Modifier.drawBehind {
                                if (size.width <= 0f) return@drawBehind
                                val lightAlpha = 0.35f + 0.65f * breath
                                val lightScale = 0.78f + 0.42f * breath
                                val r = PlayerVisualSpec.PROGRESS_LIGHT_SIZE.toPx() / 2f * lightScale
                                val glowCenter = Offset(size.width, size.height / 2f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        0f to Color.White.copy(alpha = 0.92f * lightAlpha),
                                        0.12f to NasMusicColors.PrimaryBright.copy(alpha = 0.72f * lightAlpha),
                                        0.32f to NasMusicColors.Primary.copy(alpha = 0.42f * lightAlpha),
                                        0.62f to NasMusicColors.Primary.copy(alpha = 0.15f * lightAlpha),
                                        1f to Color.Transparent,
                                        center = glowCenter,
                                        radius = r
                                    ),
                                    radius = r,
                                    center = glowCenter
                                )
                            }
                            else Modifier
                        )
                        .clip(RoundedCornerShape(2.dp))
                        .background(NasMusicColors.Primary)
                )
            }
        }
        }
        }
    }

    if (!showTimes) {
        // 2026-10-29 用户定稿：播放页**不显示两端时间**，轨道直接占满整行。
        // 位置说明：原版式是「轨道在上 + 时间行在下」，时间行被去掉后，底部控制栏
        // 整体高度减少一行，而控制栏按钮锚定屏幕底部 —— 轨道自然**下移**到
        // 原时间行所在的位置（用户要求「把进度条放到现在时间的位置」）。
        //
        // 电台（isLive）例外：进度条本身无意义，但「● LIVE」是状态角标而非时间，
        // 保留它，避免电台态丢失"直播中"的唯一提示。
        if (isLive) {
            Column(modifier = modifier.fillMaxWidth()) {
                progressTrack(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(2.dp))
                currentTimeLabel()
            }
        } else {
            progressTrack(modifier.fillMaxWidth())
        }
    } else if (timesBelow) {
        // 图片同款（2026-10-27）：进度条全宽在上，时间标签移到进度条下方两端
        Column(modifier = modifier.fillMaxWidth()) {
            progressTrack(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentTimeLabel()
                Spacer(modifier = Modifier.weight(1f))
                totalTimeLabel()
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            currentTimeLabel()
            Spacer(modifier = Modifier.width(progressSpacer))
            progressTrack(Modifier.weight(1f))
            Spacer(modifier = Modifier.width(progressSpacer))
            totalTimeLabel()
        }
    }
}

// ─── Control Buttons Row (standalone) ────────────────────────────────────────

/**
 * 独立播放控制按钮行：上一首、播放/暂停、下一首、播放模式。
 * 供 NowPlayingScreen 在封面下方使用，也可在其他页面复用。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ControlButtonsRow(
    isPlaying: Boolean,
    playMode: PlayMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayMode: () -> Unit,
    showVocalButton: Boolean = false,
    onEnterKaraoke: () -> Unit = {},
    showVisualizerButton: Boolean = false,
    onEnterVisualizer: () -> Unit = {},
    showMvButton: Boolean = false,
    mvAvailable: Boolean = false,
    onEnterMv: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    playPauseFocusRequester: FocusRequester? = null
) {
    // 手机端修复：按钮行改为**可横向滚动**。
    //
    // 背景：本行在 NowPlayingScreen 里被放在固定 `width(380.dp)` 的 Column 内。
    // compact 模式下已有 上一首/播放/下一首/播放模式/频谱/K歌/MTV 共 7 个控件，
    // 新增「音质」后总宽超出 380dp —— 且原实现无横向滚动，超出的按钮直接被裁掉，
    // 用户看不到音质入口（"NowPlaying 没有音质按钮"）。
    // 滚动后所有按钮均可横向滑动到达；TV 端按钮数少，通常无需滚动，行为不变。
    // 布局重组：控制按钮分两行 ——
    // 行1 主控制：上一曲 / 播放暂停 / 下一曲 / 循环；
    // 行2 副入口：频谱 / K歌 / MTV（任一按钮显示时才渲染第二行）。
    // 两行均按宽度居中；compact 下行1 4×40+6×3=178dp、行2 ≤180dp，永不溢出，去掉原横向滚动。
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 按钮间距：紧凑模式 6dp / 常规 14dp
        val btnGap = if (compact) 6.dp else 14.dp
        // 行1：主控制
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, compact = compact, icon = {
                Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "Previous",
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(if (compact) 22.dp else 32.dp))
            })
            Spacer(modifier = Modifier.width(btnGap))
            IconButton(onClick = onPlayPause, primary = true, compact = compact,
                focusRequester = playPauseFocusRequester, icon = {
                Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(if (compact) 28.dp else 40.dp))
            })
            Spacer(modifier = Modifier.width(btnGap))
            IconButton(onClick = onNext, compact = compact, icon = {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next",
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(if (compact) 22.dp else 32.dp))
            })
            Spacer(modifier = Modifier.width(btnGap))
            IconButton(onClick = onTogglePlayMode, compact = compact, icon = {
                val icon = when (playMode) {
                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                    PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                    else -> Icons.Filled.Repeat
                }
                Icon(imageVector = icon, contentDescription = stringResource(playMode.displayNameRes),
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(if (compact) 20.dp else 28.dp))
            })
        }
        // 行2：频谱 / K歌 / MTV
        if (showVisualizerButton || showVocalButton || showMvButton) {
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 频谱（全屏可视化舞台）入口按钮
                if (showVisualizerButton) {
                    VocalToggleButton(
                        label = stringResource(R.string.player_visualizer_short),
                        onClick = onEnterVisualizer,
                        compact = compact,
                        // 两字标签需与「K歌」同宽（52dp），否则 compact 下被截断
                        width = if (compact) 52.dp else null
                    )
                }
                // K 歌入口按钮
                if (showVocalButton) {
                    Spacer(modifier = Modifier.width(btnGap))
                    VocalToggleButton(
                        label = stringResource(R.string.player_karaoke),
                        onClick = onEnterKaraoke,
                        compact = compact,
                        width = if (compact) 52.dp else null
                    )
                }
                // MTV 入口按钮（有 MV 时可点击，无 MV 时半透明禁用）
                if (showMvButton) {
                    Spacer(modifier = Modifier.width(btnGap))
                    VocalToggleButton(
                        label = "MTV",
                        onClick = { if (mvAvailable) onEnterMv() },
                        compact = compact,
                        dimmed = !mvAvailable,
                        // MTV 三个字母需更宽才能单行显示
                        width = if (compact) 64.dp else 100.dp
                    )
                }
            }
        }
    }
}

/**
 * 正在播放页底部控制条（网易云样式，2026-10-27；重排）：
 * - 左侧：爱心 / 歌词(显隐) / K歌 / MTV
 * - 中部：上一曲 / 播放暂停 / 下一曲（居中）
 * - 右侧：播放模式 / 频谱 / [trailingInfo 插槽：信息 + 来源]
 * 面向纯方向键遥控器（上/下/左/右/确认/返回，无媒体专用键）：所有项均为可聚焦按钮，
 * 横向为线性序列，上下方向键在「进度条 ↔ 控制行」间切换，OK 确认，返回退出。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingBottomControls(
    isPlaying: Boolean,
    playMode: PlayMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayMode: () -> Unit,
    showVisualizerButton: Boolean = false,
    onEnterVisualizer: () -> Unit = {},
    showVocalButton: Boolean = false,
    onEnterKaraoke: () -> Unit = {},
    showMvButton: Boolean = false,
    mvAvailable: Boolean = false,
    onEnterMv: () -> Unit = {},
    // 底部左侧按钮组（爱心 + 歌词显隐）
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    lyricsVisible: Boolean = true,
    onToggleLyrics: () -> Unit = {},
    // 「信息 + 来源」簇由调用方注入，紧跟「频谱」右侧
    // （用插槽而非直接传参：来源标识依赖 Song / SourceBadge 体系，放在本组件会造成耦合）
    trailingInfo: (@Composable () -> Unit)? = null,
    /**
     * （2026-10-28 用户定稿）：**音质 / 码率角标插槽**，插在左簇「MTV」旁边。
     * 之前挂在黑胶下方（CoverColumn），用户要求移到控制栏；作为插槽传入
     * （角标逻辑依赖 Song / qualityBadgeLabel，留在调用方，避免本组件耦合）。
     */
    qualitySlot: (@Composable () -> Unit)? = null,
    /** 减弱动效总闸；为 true 时播放/暂停图标瞬时切换（§11-8） */
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
    playPauseFocusRequester: FocusRequester? = null
) {
    val btnGap = 14.dp
    // 2026-10-28 用户定稿：上一曲/播放暂停/下一曲要**相对整行左右绝对居中** ——
    // 旧结构「左簇 + weight(1f) 居中 + 右簇」在左右簇宽度不等时会把传输键挤偏；
    // 改为三层叠加：传输键锚在整行正中（与两侧簇宽度无关），左右功能簇贴边对齐
    // （电视宽屏下两侧簇远短于半行，不会与传输键重叠）。
    Box(modifier = modifier.fillMaxWidth()) {
        // 左侧按钮组：爱心 + 歌词(显隐)（与中部传输键同高一行对齐）
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleFavorite, compact = true,
                // 2026-10-28 用户定稿：点亮态 = **纯红色实心爱心**——
                // 不再套红底圆（primary）与描边圈，图标也不再是黄色（Warning）
                icon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) NasMusicColors.Primary else LocalContentColor.current,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
            Spacer(modifier = Modifier.width(btnGap))
            // 2026-10-28 用户定稿：歌词开关改**图标样式**（「词」字方框图标，参照截图）——
            // 与两侧 40dp 圆形图标按钮同尺寸同节奏；状态由颜色表达（见组件内注释）。
            LyricsToggleIconButton(
                lyricsVisible = lyricsVisible,
                onClick = onToggleLyrics
            )
            // K歌 / MTV 从右侧簇移到「歌词」后面（左侧功能簇）
            if (showVocalButton) {
                Spacer(modifier = Modifier.width(btnGap))
                VocalToggleButton(
                    label = stringResource(R.string.player_karaoke),
                    onClick = onEnterKaraoke,
                    compact = true,
                    width = 52.dp
                )
            }
            if (showMvButton) {
                Spacer(modifier = Modifier.width(btnGap))
                VocalToggleButton(
                    label = "MTV",
                    onClick = { if (mvAvailable) onEnterMv() },
                    compact = true,
                    dimmed = !mvAvailable,
                    width = 64.dp
                )
            }
            // 音质 / 码率角标紧跟 MTV（网络歌曲可点弹档位面板；本地歌曲只读码率）
            if (qualitySlot != null) {
                Spacer(modifier = Modifier.width(btnGap))
                qualitySlot()
            }
        }
        // 中部传输键：**锚定整行正中**（与左右簇宽度无关，绝对居中）
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
                IconButton(
                    onClick = onPrevious,
                    compact = true,
                    icon = {
                        Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "Previous",
                            tint = LocalContentColor.current, modifier = Modifier.size(22.dp))
                    }
                )
                Spacer(modifier = Modifier.width(btnGap))
                IconButton(
                    onClick = onPlayPause, primary = true, compact = true,
                    focusRequester = playPauseFocusRequester,
                    icon = {
                        // P1-6 播放/暂停图标状态过渡：淡入淡出 + 轻微缩放（§10.3「不做复杂 morph」）。
                        // reduceMotion 时退化为瞬时切换（§11-8）。
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                if (reduceMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (fadeIn(tween(140)) +
                                        scaleIn(initialScale = 0.84f, animationSpec = tween(140))) togetherWith
                                        (fadeOut(tween(110)) +
                                            scaleOut(targetScale = 1.10f, animationSpec = tween(110)))
                                }
                            },
                            label = "playPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = LocalContentColor.current,
                                // 与上下一曲同尺寸（22dp）：主键缩小后图标必须同步收，
                                // 否则图标会顶到圆边、比副键显得更"挤"
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.width(btnGap))
                IconButton(
                    onClick = onNext,
                    compact = true,
                    icon = {
                        Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next",
                            tint = LocalContentColor.current, modifier = Modifier.size(22.dp))
                    }
                )
            }
        // 右侧功能簇：播放模式 / 频谱 / 信息（与传输键同高，贴行尾对齐）
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePlayMode, compact = true,
                icon = {
                    val icon = when (playMode) {
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    }
                    Icon(imageVector = icon, contentDescription = stringResource(playMode.displayNameRes),
                        tint = LocalContentColor.current, modifier = Modifier.size(20.dp))
                }
            )
            if (showVisualizerButton) {
                Spacer(modifier = Modifier.width(btnGap))
                VocalToggleButton(label = stringResource(R.string.player_visualizer_short), onClick = onEnterVisualizer, compact = true, width = 52.dp)
            }
            // 「信息 + 来源」紧跟「频谱」右侧（K歌 / MTV 已移到左侧「歌词」后）
            if (trailingInfo != null) {
                Spacer(modifier = Modifier.width(btnGap))
                trailingInfo()
            }
        }
    }
}

/**
 * 「歌词」显隐开关 —— **图标样式**（2026-10-28 用户定稿，参照网易云播放页截图）。
 *
 * 形态：40dp 透明圆形容器（与爱心/模式/上一曲等图标按钮完全同尺寸同焦点节奏），
 * 内部是 24dp 圆角方框描边 + 框内「词」字。
 *
 * 状态表达**只靠颜色**：
 * - 显示中 → 品牌红（描边 + 字），与爱心点亮态同一套语义（"已开启"= 红）
 * - 隐藏中 → 次要文字色（灰），一眼可辨"歌词关着"
 * 不再用 dimmed 半透明：文字按钮变图标后，半透明会让 24dp 的细描边糊成一团。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LyricsToggleIconButton(
    lyricsVisible: Boolean,
    onClick: () -> Unit,
) {
    // 2026-10-30 用户定稿：与 K歌/MTV 完全同款（40dp 高圆角矩形 + 透明底 + 灰描边），
    // 不再用圆形 40dp 内套 24dp 小框 —— 视觉权重与相邻文字按钮一致，遥控器焦点环对齐。
    val contentColor = if (lyricsVisible) NasMusicColors.Primary else NasMusicColors.TextSecondary
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(52.dp)
            .height(PlayerVisualSpec.BOTTOM_SECONDARY_BUTTON_SIZE),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.12f,
        animationDurationMs = 250,
        containerColor = Color.Transparent,
        focusedContainerColor = NasMusicColors.FocusOverlay,
        contentColor = contentColor,
        focusedContentColor = contentColor,
        pressedContainerColor = NasMusicColors.FocusOverlay,
        pressedContentColor = contentColor,
        pressedScale = 0.90f,
        focusBorderColor = NasMusicColors.FocusRing
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = NasMusicColors.TextSecondary,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "歌词",
                fontSize = FontSize.small(),
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    icon: @Composable () -> Unit
) {
    // （2026-10-28 用户定稿）：底部控制栏的播放/暂停（primary + compact）
    // **与上一曲/下一曲同尺寸**（40dp）—— 此前 56dp 的"大红圆"把这一行的视觉重心
    // 全压在中间，且整行高度被它顶起来，看起来更"贴底"。
    // 主键的身份由**红色实心圆 + 白色图标**承担，不再靠尺寸取胜（§7.3 视觉突出 ≠ 尺寸最大）。
    val buttonSize = when {
        primary && !compact -> 88.dp
        primary && compact -> PlayerVisualSpec.BOTTOM_PRIMARY_BUTTON_SIZE
        !primary && !compact -> 72.dp
        else -> PlayerVisualSpec.BOTTOM_SECONDARY_BUTTON_SIZE
    }
    // 主按钮（播放/暂停）添加绛红描边光（border 替代 shadow 以减少渲染开销）
    val glowModifier: Modifier = if (primary) {
        modifier
            .size(buttonSize)
            .border(
                width = 1.5.dp,
                color = NasMusicColors.Primary.copy(alpha = 0.4f),
                shape = CircleShape
            )
    } else {
        modifier
            .size(buttonSize)
    }
    // 网易云同款：
    // - 主键（播放/暂停）：大红实心圆 + 白图标（光靠红底即可辨识，去掉描边）
    // - 副键（上一曲/下一曲/循环）：无底纯图标（透明容器，焦点才提亮），
    //   图标颜色随 LocalContentColor（主键白 / 副键主题文字色）
    FocusableSurface(
        onClick = onClick,
        modifier = glowModifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = CircleShape,
        // 柔和化：主键聚焦 1.06（原 1.12/250ms 太弹簧），时长 180ms
        focusedScale = 1.06f,
        animationDurationMs = 180,
        containerColor = if (primary) NasMusicColors.Primary else Color.Transparent,
        // 主按钮聚焦时整体变亮（Primary → PrimaryBright，鲜红留给高优先级主键）；
        // 普通按钮聚焦绛红 18% 提亮（原 30% 红底太重，克制原则）
        focusedContainerColor = if (primary) NasMusicColors.PrimaryBright else NasMusicColors.FocusOverlay,
        contentColor = if (primary) Color.White else NasMusicColors.TextPrimary,
        focusedContentColor = if (primary) Color.White else NasMusicColors.TextPrimary,
        pressedContainerColor = if (primary) NasMusicColors.PrimaryBright else NasMusicColors.FocusOverlay,
        pressedContentColor = if (primary) Color.White else NasMusicColors.TextPrimary,
        pressedScale = 0.90f,
        focusBorderColor = NasMusicColors.FocusRing,
        // P1-8 焦点光晕呼吸（仅聚焦时运行；减弱动效/低端设备自动退化为静态光晕）
        glowBreath = true
    ) {
        CompositionLocalProvider(
            LocalContentColor provides (if (primary) Color.White else NasMusicColors.TextPrimary)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}
