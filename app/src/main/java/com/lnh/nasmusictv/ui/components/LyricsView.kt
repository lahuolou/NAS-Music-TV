package com.lnh.nasmusictv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.data.model.Lyrics
import com.lnh.nasmusictv.data.model.LyricsHighlightMode
import com.lnh.nasmusictv.ui.theme.LocalLyricsTheme
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec
import kotlinx.coroutines.delay

/**
 * 歌词视图
 * 支持按当前播放时间滚动显示歌词行
 * 支持逐行/逐字高亮模式切换
 * 使用 TV 标准 Surface 焦点管理，避免与焦点系统冲突
 *
 * 上下渐隐不再用纯色遮罩盖在歌词上（纯色与整页渐变背景存在色差，
 * 会显出一块独立底色的矩形），改为对歌词内容自身做 alpha 渐隐：
 * 边缘直接透出页面背景，歌词区与黑胶区共用同一背景、天然无缝。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    currentTimeMs: Long,
    modifier: Modifier = Modifier,
    highlightMode: LyricsHighlightMode = LyricsHighlightMode.LINE_BY_LINE,
    isPlaying: Boolean = true,
    fontSizeMultiplier: Float = 1.0f,
    onSeekToLine: ((Long) -> Unit)? = null,
    longPressSeekEnabled: Boolean = false,
    /** 减弱动效总闸；为 true 时当前行切换改为瞬时到位（§11-8） */
    reduceMotion: Boolean = false,
    /**
     * **沉浸隐藏进度**（0 = 菜单可见，1 = 5s 菜单全隐）：
     * - 高亮行落点在 [PlayerVisualSpec.LYRIC_HIGHLIGHT_TARGET_FRACTION]（可见态 0.40）
     *   与 [PlayerVisualSpec.LYRIC_HIGHLIGHT_TARGET_IDLE_FRACTION]（隐藏态 0.50）间插值；
     * - **隐藏态行槽收窄**（[PlayerVisualSpec.LYRIC_LINE_SLOT_FRACTION]=0.12，可见态 0.145），
     *   行距压缩、同屏多排几行；字号占比由 [PlayerVisualSpec.LYRIC_SLOT_FILL_*_IDLE]
     *   兜住，字号与可见态基本一致（不再随隐藏态放大）；
     * - 顶部渐隐切换到**更深**的隐藏态笔刷，保证歌词不与标题/歌手堆叠。
     */
    idleProgress: Float = 0f,
    /**
     * 歌词行**居左对齐**（2026-10-28 用户定稿：播放页歌词改为左对齐，
     * 与左侧黑胶列形成统一的左侧视觉基线；K 歌页等其它场景保持居中）。
     *
     * ⚠️ 只改文字排布方向，**不改任何布局尺寸** —— 行高、内边距、字号全部不变，
     * 因此不会影响已有的滚动位置/自适应缩字逻辑。
     */
    alignStart: Boolean = false,
    /**
     * **顶部保护内边距**（占视口高比例；2026-10-30 十九轮）：
     * 给歌词容器顶部加这段内边距，内容区整体下压，把滚进标题/歌手带的行裁掉
     * （相对量自适应任何分辨率）。播放页调用方按 idleProgress 插值：
     * 菜单可见态 > 0，5s 隐藏态 = 0（图一定稿不受影响）。
     */
    topGuardFraction: Float = 0f
) {
    // TV-only：移除手机紧凑模式的密度恢复分支，统一走 TV 歌词排版（两分支原本等价）。
    LyricsViewInner(lyrics, currentTimeMs, modifier, highlightMode, isPlaying, fontSizeMultiplier, onSeekToLine, longPressSeekEnabled, reduceMotion, idleProgress, alignStart, topGuardFraction)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LyricsViewInner(
    lyrics: Lyrics?,
    currentTimeMs: Long,
    modifier: Modifier = Modifier,
    highlightMode: LyricsHighlightMode = LyricsHighlightMode.LINE_BY_LINE,
    isPlaying: Boolean = true,
    fontSizeMultiplier: Float = 1.0f,
    onSeekToLine: ((Long) -> Unit)? = null,
    longPressSeekEnabled: Boolean = false,
    reduceMotion: Boolean = false,
    /** 沉浸隐藏进度 0..1（见 [LyricsView] 文档），驱动锚点/行槽/渐隐的隐藏态版式 */
    idleProgress: Float = 0f,
    alignStart: Boolean = false,
    /** 顶部保护内边距（占视口高比例，见 [LyricsView] 文档） */
    topGuardFraction: Float = 0f
) {
    if (lyrics == null || lyrics.isEmpty) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = NasMusicColors.TextSecondary,
                    modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                )
                Text(
                    text = stringResource(R.string.player_no_lyrics),
                    style = LocalLyricsTheme.current.normalLine,
                    color = NasMusicColors.TextSecondary
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()

    // 首尾留白（edgeSpacer）不再用固定 dp：改为「当前行滚到高亮落点
    // （视口 × [PlayerVisualSpec.LYRIC_HIGHLIGHT_TARGET_FRACTION]，2026-10-30 定稿 0.40）」后，
    // 留白必须等于**视口高 × 同系数**，首行/末行才能滚到高亮位（见 BoxWithConstraints 内
    // 的 `edgeSpacer`）。

    // —— 手势拖拽跳转（2026-09-22 新增，手机端横竖屏）：拖动浏览歌词时顶部出现
    //    虚线指示条，松手后跳到虚线处歌词的起始时间播放。isDragSeeking 期间
    //    暂停自动跟行；tap（未滚动）不触发，普通浏览不受影响。
    var isDragSeeking by remember { mutableStateOf(false) }
    var seekTargetLine by remember { mutableStateOf(-1) }
    // 修复（17:51 真机反馈）：LazyColumn 的 scrollable 发出的是 DragInteraction
    // 而非 PressInteraction——collectIsPressedAsState 永远收不到事件，
    // isDragSeeking 不会激活，拖拽跳转整体无效。改 collectIsDraggedAsState。
    val listDragged by listState.interactionSource.collectIsDraggedAsState()

    // 逐字模式下使用本地高频时钟插值，平滑过渡（避免 1000ms progress 导致逐字跳动）
    // 基于上次已知 currentTimeMs（1秒锚点）+ 实际流逝时间估算当前进度
    var lyricTickMs by remember(lyrics) { mutableLongStateOf(currentTimeMs) }
    LaunchedEffect(currentTimeMs, isPlaying, highlightMode, lyrics) {
        if (highlightMode == LyricsHighlightMode.WORD_BY_WORD && isPlaying) {
            // 记录锚点：当前已知 progress + 系统时间
            var anchorProgress = currentTimeMs
            var anchorSystemMs = System.currentTimeMillis()
            lyricTickMs = anchorProgress
            while (true) {
                delay(50)  // 50ms 刷新（20fps）
                val elapsed = System.currentTimeMillis() - anchorSystemMs
                lyricTickMs = anchorProgress + elapsed
                // currentTimeMs 更新时（每秒一次），重新校准锚点
                if (currentTimeMs != anchorProgress) {
                    anchorProgress = currentTimeMs
                    anchorSystemMs = System.currentTimeMillis()
                }
            }
        } else {
            // 非逐字模式或暂停，直接使用 currentTimeMs
            lyricTickMs = currentTimeMs
        }
    }

    // 找到当前歌词行索引（逐字模式用高频时钟，逐行模式用原始 progress）
    val effectiveTimeMs = if (highlightMode == LyricsHighlightMode.WORD_BY_WORD && isPlaying) {
        lyricTickMs
    } else {
        currentTimeMs
    }

    // ── 2026-10-30：过滤 LRC 空行（间奏 / 段落间隔）──────────────────────────
    // 空行如果参与 5 行排版，会占掉一整个行槽却什么都不显示 ——
    // 5 行里凭空出现一个"大洞"，肉眼读起来就是「行距突然变大、比例不协调」。
    // 过滤后 5 个行槽始终有字，行距恒定；空行的时间戳**不丢**：删掉的是它自己，
    // 上一行会一直高亮到下一个非空行的时间点（语义与原来完全一致）。
    // 极端情况（整首都是空行）回退到原始列表，避免出现空列表导致的索引越界。
    val displayLines = remember(lyrics.lines) {
        val filtered = lyrics.lines.filter { it.text.isNotBlank() }
        if (filtered.isEmpty()) lyrics.lines else filtered
    }

    val currentIndex = displayLines
        .indexOfFirst { it.time > effectiveTimeMs }
        .let { if (it == -1) displayLines.size - 1 else it - 1 }
        .coerceAtLeast(0)

    // 视口高度（px）：沉浸隐藏时标题收合 / 展开会让歌词框高度变化，
    // 把它作为自动跟行 effect 的 key —— 视口一变就重新居中，
    // 否则隐藏态下当前行会停在旧位置、偏离新的视口中线（2026-10-29 实测 55.6%→44.7%）。
    // derivedStateOf 只在高度值真正变化时触发重组/重启，滚动过程不产生额外开销。
    val viewportH by remember {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }

    // ── 2026-10-30：锚点/行槽随沉浸隐藏态切换 ──────────────────────
    // 可见态：锚点 0.40（第二行高亮）+ 行槽 0.145；
    // 隐藏态：锚点 0.50（高亮居中）+ 行槽收窄到 0.12 —— 行距压缩、同屏多排约 3 行；
    // 字号不随隐藏态放大（字占行槽比例同步提高）。
    // ⚠️ 不用 lerp 连续插值：idleProgress tween 期间行槽每帧变化会让 LazyColumn
    // 每行尺寸连续重排 + 跟行动画被反复打断 → 切换时歌词列表抖动/下滑。
    // 改为过半瞬时切换：版式量在 idleProgress>0.5 时一步到位，跟行定位走
    // scrollToItem 瞬时收敛（见下方 effect 的 anchorChanged 分支）。
    val isIdle = idleProgress > 0.5f
    val anchorFraction = if (isIdle) {
        PlayerVisualSpec.LYRIC_HIGHLIGHT_TARGET_IDLE_FRACTION
    } else {
        PlayerVisualSpec.LYRIC_HIGHLIGHT_TARGET_FRACTION
    }

    // 记录上一次锚点：区分「唱行前进」（平滑滚动）与「版式切换」（瞬时定位）两种重启
    var lastAnchorFraction by remember { mutableStateOf(anchorFraction) }

    LaunchedEffect(currentIndex, isDragSeeking, viewportH, anchorFraction) {
        // 版式切换（锚点变了）→ 瞬时定位、无滚动动画（否则用户看到歌词列表下滑一段）；
        // 唱行前进（currentIndex 变）→ 平滑 animateScrollBy。
        val layoutChanged = anchorFraction != lastAnchorFraction
        lastAnchorFraction = anchorFraction
        // 拖拽跳转中暂停自动跟行；松手跳转后 currentIndex 变化会自然滚回当前行
        if (currentIndex >= 0 && !isDragSeeking) {
            // 2026-10-30 三定稿：**高亮行落点 = 视口 × anchorFraction**。
            // 行中心落点仍是相对单位（视口高 × 系数），任意分辨率 / 宽高比自适应，
            // 无硬编码像素。视口整体与屏幕的对齐由调用方（NowPlayingScreen 补偿 offset）负责。
            // 定位用 scrollToItem 的**像素级精确偏移**：⚠️ scrollToItem 的
            // itemTop = -scrollOffset，偏移符号勿反（实测教训）。
            val targetItem = currentIndex + 1   // item 0 是首部留白 spacer，行 k 对应 item k+1
            val info = listState.layoutInfo
            val vh = info.viewportSize.height
            if (vh <= 0) return@LaunchedEffect
            // 高亮行中心的目标 y（px）
            val highlightCenter = (vh * anchorFraction).toInt()
            // 锚点偏移：行中心 → 高亮落点。
            // itemTop = -scrollOffset ⇒ itemTop = highlightCenter - itemSize/2
            // ⇒ scrollOffset = itemSize/2 - highlightCenter（可为负：首部 spacer 足够容纳）。
            val anchorOffset: (Int) -> Int = { itemSize ->
                itemSize / 2 - highlightCenter
            }
            val item = info.visibleItemsInfo.firstOrNull { it.index == targetItem }
            if (item == null) {
                // 目标行不在可视区（切歌 / 跳转大跨度）→ **瞬时定位**（含校正），
                // 大跨度做动画只会一闪而过的乱滚
                listState.scrollToItem(targetItem)
                val it2 = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetItem }
                if (it2 != null) {
                    listState.scrollToItem(
                        targetItem,
                        anchorOffset(it2.size)
                    )
                }
            } else {
                // 版式切换（行槽/锚点一步到位）→ 瞬时对齐，无滚动动画：
                // 否则 animateScrollBy 会让用户看到整个歌词列表下滑一段。
                // 唱行前进 → FastOutSlowInEasing 缓动插值过渡，平滑优雅。
                val delta = (item.offset + item.size / 2f) - highlightCenter
                if (kotlin.math.abs(delta) > 1f) {
                    if (reduceMotion || layoutChanged) {
                        listState.scrollBy(delta)
                    } else {
                        listState.animateScrollBy(
                            delta,
                            tween(PlayerVisualSpec.LYRIC_SCROLL_MS, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
        }
    }

    // 拖动即进入（TV/横屏默认）。手机竖屏（longPressSeekEnabled）改长按进入：
    // 普通拖动留给「纵向浏览 + 横向 pager 切页」，避免轴向手势冲突。
    LaunchedEffect(listDragged, listState.isScrollInProgress, longPressSeekEnabled) {
        if (!longPressSeekEnabled && listDragged && listState.isScrollInProgress && !isDragSeeking) {
            isDragSeeking = true
            seekTargetLine = -1
        }
    }

    // 拖拽中：计算虚线指示处的目标行（LazyColumn item index = 行 index + 1，头部有 spacer）
    if (isDragSeeking) {
        // 2026-10-30：指示线贴「视口 × anchorFraction」高亮落点（与当前行同一基线）
        val info = listState.layoutInfo
        val indicatorPx = (info.viewportSize.height * anchorFraction).toInt()
        seekTargetLine = info.visibleItemsInfo.firstOrNull { item ->
            item.index in 1..displayLines.size &&
                item.offset <= indicatorPx && item.offset + item.size > indicatorPx
        }?.let { it.index - 1 } ?: -1
    }

    // 松手（拖动即进入模式）：跳到目标行起始时间播放（DragInteraction.Stop = 手指抬起）。
    // 长按模式的松手由下方 detectDragGesturesAfterLongPress 的 onDragEnd 处理。
    LaunchedEffect(listDragged) {
        if (!longPressSeekEnabled && !listDragged && isDragSeeking) {
            isDragSeeking = false
            val target = seekTargetLine
            seekTargetLine = -1
            if (target in displayLines.indices) {
                onSeekToLine?.invoke(displayLines[target].time)
            }
        }
    }

    // Box 叠加：下方是滚动歌词，整体做内容级上下渐隐。
    // 实现：offscreen 合成 + DstIn——把歌词（含拖拽指示）渲染到独立图层后，
    // 用「透明→不透明→透明」的纵向渐变作为目标 alpha，使上下边缘渐隐为全透明，
    // 直接透出底层页面背景（黑胶区同源），不再出现遮罩纯色与渐变背景的色差矩形。
    //
    // 2026-10-30 用户定稿「高亮行绝对居中」后视口占满整列，标题/歌手改为 overlay
    // 悬浮在视口顶部 —— 歌词行会滚进标题带，**顶部渐隐必须扩大到盖住标题区**。
    //
    // 2026-10-30 十九轮统一笔刷（可见/隐藏态几何已对齐，共用一条曲线）：
    // 实测两态的「首行歌词落点」都在视口 ≈0.31、歌手线都在 ≈0.18 ——
    // 因此歌手线（0.17）以上几乎全隐、首行歌词位（0.315）全显：
    // 高亮行上面的**第二行**（正好落在歌手线上）被完全隐去，彻底消除叠字
    //（可见态实测 v19 教训：旧可见态笔刷 0.20 即全显，第二行以 83% 透明度压在歌手上）。
    val edgeFadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.20f to Color.Transparent, // 歌手线（视口 0.18）及以下完全透明（v20：残影归零）
            0.315f to Color.Black,  // 首行歌词落点（屏高 0.374 ≈ 视口 0.31）处全显
            0.94f to Color.Black,   // 底部 ~6% 柔和渐出
            1f to Color.Transparent
        )
    }    // 切歌歌词淡入（§10.3「歌词淡出 150–250ms 后新歌词淡入」）。
    //
    // ⚠️ 取舍说明：严格实现"旧歌词先淡出、再换数据、新歌词淡入"需要**延迟应用新歌词**
    // （即先保留上一份歌词渲染 200ms），这会让歌词与播放进度在切换窗口内不同步 ——
    // 对有逐字高亮/卡拉 OK 的场景是实质性的体验倒退，风险大于收益。
    // 因此这里只做**新歌词淡入**：切歌瞬间列表内容已换新，整块从透明渐显，
    // 观感上依然是"旧内容淡出、新内容浮现"的柔和过渡，且绝不引入歌词错位。
    val swapAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(lyrics) {
        // 2026-10-29（修"换歌时歌词跳来跳去"的原始意图）与**自动跟行的冲突修复**：
        // 此处原有的 listState.scrollToItem(0)（换词先归零再淡入）会在同一帧内
        // **晚于**上方自动跟行的定位执行（本 effect 声明在后），把刚定位好的
        // 当前行直接覆盖回列表顶部 —— 106dp 顶部留白占满矮视口，当前行只露出
        // 底部一条缝，正是"宽屏歌词看不见"的最后一块拼图。
        // 归零已无必要：换歌时 currentIndex 必然变化，上方 effect 会重新定位到
        // 新当前行，淡入动画照常覆盖过渡。
        if (reduceMotion) {
            swapAlpha.snapTo(1f)
        } else {
            swapAlpha.snapTo(0f)
            swapAlpha.animateTo(
                1f,
                androidx.compose.animation.core.tween(PlayerVisualSpec.LYRIC_SWAP_MS)
            )
        }
    }

    // 2026-10-29 用户反馈"歌词太大…自适应各种尺寸屏幕"：BoxWithConstraints 拿到
    // 歌词视口实际高度，**按视口自适应缩字** —— ≥300dp 满字号；更矮的视口
    // （超宽横屏 / 小屏）线性缩小、下限 0.6，保证任何屏幕上行数都合理。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = swapAlpha.value
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = edgeFadeBrush, blendMode = BlendMode.DstIn)
            }
            .then(
            if (longPressSeekEnabled) {
                Modifier.pointerInput(longPressSeekEnabled) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            // 长按 → 进入跳转模式
                            isDragSeeking = true
                            seekTargetLine = -1
                        },
                        onDrag = { change, dragAmount ->
                            // 跳转模式中拖动 = 滚动列表选行（手动驱动，禁用子列表滚动）
                            change.consume()
                            listState.dispatchRawDelta(-dragAmount.y)
                        },
                        onDragEnd = {
                            // 松手 → 跳到目标行起始时间播放并退出跳转模式
                            val target = seekTargetLine
                            isDragSeeking = false
                            seekTargetLine = -1
                            if (target in displayLines.indices) {
                                onSeekToLine?.invoke(displayLines[target].time)
                            }
                        },
                        onDragCancel = {
                            isDragSeeking = false
                            seekTargetLine = -1
                        }
                    )
                }
            } else {
                Modifier
            }
        )
    ) {
        // 2026-10-30 十九轮：顶部保护内边距（占视口高比例，菜单可见态 > 0）。
        // 内容区 = 视口高 − 内边距；行槽/留白全部改用内容区高，锚点仍是相对量
        //（自适应任意分辨率）。整体下压把滚进标题/歌手带的行裁出容器。
        val topGuard = maxHeight * topGuardFraction
        val contentH = (maxHeight - topGuard).coerceAtLeast(1.dp)

        // 2026-10-30「隐藏态多排几行」：行槽 = 内容区高 × 行槽系数 —— 可见态 0.145，
        // 隐藏态收窄到 0.12（行距压缩、多排约 3 行）；字号不再随隐藏态放大，
        // 改由下方 cappedSp 的字占比（fill）兜住，字号肉眼不变。
        // ⚠️ 不用 lerp 连续插值（idleProgress tween 期间行槽每帧变 → LazyColumn
        // 连续重排 → 切换时歌词抖动），改过半瞬时切换（isIdle）。
        val slotFraction = if (isIdle) {
            PlayerVisualSpec.LYRIC_LINE_SLOT_FRACTION
        } else {
            PlayerVisualSpec.LYRIC_LINE_SLOT_FRACTION_VISIBLE
        }
        val lineSlot = contentH * slotFraction
        // 行槽真实像素高度与 1sp→px 系数：行槽预算封顶用（相对单位，任何
        // density / fontScale / 字体档位下"字高 : 行距"比例恒定）。
        val slotPx = with(LocalDensity.current) { lineSlot.toPx() }
        // 1sp → px 的换算系数（含系统字体缩放），用于把像素预算反算回 sp
        val spToPx = LocalDensity.current.density * LocalDensity.current.fontScale
        // ── 2026-10-30 用户定稿「字号层级以标题为基准、相对档位统一管理」──────────
        //   普通歌词行 = 标题 -1 档 = [FontSize.subtitle]（与歌手名**同字号**）
        //   当前歌词行 = 标题 +1 档 = [FontSize.display]
        // 再乘 [fontSizeMultiplier]（用户 A/A+/… 档位 + 沉浸隐藏同比例放大）。
        // ⚠️ 同时保留**行槽像素预算上限**：极端矮视口（超宽宽高比横屏）下行槽可能
        //    装不下 display 档的渲染高度，此时按行槽预算封顶（仍是相对单位），避免裁字。
        // 滚动期间**所有行字号恒定** —— 行高由固定行槽保证，不存在"字号忽大忽小、
        // 行高跳动"；当前行只通过颜色（品牌红）与 alpha 过渡强调（见下方 lineAlpha）。
        fun cappedSp(base: TextUnit, slotFill: Float): TextUnit {
            val v = base.value * fontSizeMultiplier
            val cap = if (spToPx > 0f && slotPx > 0f) {
                slotPx * slotFill / spToPx
            } else {
                Float.MAX_VALUE
            }
            return v.coerceIn(PlayerVisualSpec.LYRIC_FONT_MIN_SP, cap).sp
        }
        // 隐藏态行槽收窄（0.145→0.12）后字占比同步提高（0.62→0.85 / 0.80→0.95），
        // 行槽预算 cap 与可见态持平 ⇒ 字号肉眼不变、行距压缩、多排几行。
        // 同样过半瞬时切换（isIdle），不做连续插值。
        val rowFill = if (isIdle) {
            PlayerVisualSpec.LYRIC_SLOT_FILL_ROW_IDLE
        } else {
            PlayerVisualSpec.LYRIC_SLOT_FILL_ROW
        }
        val currentFill = if (isIdle) {
            PlayerVisualSpec.LYRIC_SLOT_FILL_CURRENT_IDLE
        } else {
            PlayerVisualSpec.LYRIC_SLOT_FILL_CURRENT
        }
        val rowFontSp = cappedSp(FontSize.subtitle(), rowFill)
        val currentFontSp = cappedSp(FontSize.display(), currentFill)
        // 首尾留白 = 内容区高 × 高亮落点系数 —— 只有留白 ≥ 高亮落点，首行/末行中心
        // 才可能滚到高亮位（否则被列表边界截断），任意屏高自动适配，无硬编码。
        val edgeSpacer = contentH * anchorFraction

        // --- 滚动歌词列表（使用 TV 焦点管理，移除了与焦点冲突的 pointerInput）---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 顶部保护内边距：内容区下压，滚进标题/歌手带的行被裁出容器
                .padding(top = topGuard),
            state = listState,
            // 长按跳转模式下禁用列表自身滚动——拖动由外层 pointerInput 接管并
            // dispatchRawDelta 手动驱动，避免双驱动
            userScrollEnabled = !(longPressSeekEnabled && isDragSeeking),
            horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally
        ) {
            item {
                // 留白高度 = 半个视口（不是 padding(vertical=) 的两倍叠加），
                // 这样首行 / 末行中心也能精确落到视口中线上。
                Box(modifier = Modifier.fillMaxWidth().height(edgeSpacer))
            }
            itemsIndexed(displayLines) { index, line ->
                // 5 行布局：每行放入固定高度 lineSlot 的槽中、文本在槽内垂直居中。
                // 行槽高度恒定 ⇒ LazyColumn 的 item.size 恒定 ⇒ 上方「当前行绝对
                // 居中」定位精确，且上下各 2 行稳定可见。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lineSlot),
                    contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.Center
                ) {
                val isCurrent = index == currentIndex
                val played = index < currentIndex
                val near = kotlin.math.abs(index - currentIndex) <= 1
                val isSeekTarget = isDragSeeking && index == seekTargetLine

                // ── P1-5 当前行过渡（§10.3「当前行高亮过渡 200–300ms」）────────────
                // 2026-10-30 用户定稿「滚动中不要字号忽大忽小、行高跳动；仅对当前行
                // 做颜色/透明度/轻微缩放过渡」：字号已按档位区分（当前行 display、
                // 其余 subtitle），**不再叠加 graphicsLayer 缩放**（叠加会让当前行
                // 超出行槽裁字）；强调只走 alpha + 颜色，合成阶段零布局抖动。
                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        isCurrent -> 1f
                        played -> PlayerVisualSpec.LYRIC_PLAYED_ALPHA
                        near -> 0.85f
                        else -> PlayerVisualSpec.LYRIC_DIM_ALPHA
                    },
                    animationSpec = if (reduceMotion) {
                        snap()
                    } else {
                        tween(PlayerVisualSpec.LYRIC_LINE_MS, easing = FastOutSlowInEasing)
                    },
                    label = "lyricLineAlpha"
                )
                // 作用在 padding 之后 ⇒ 只在合成阶段变换，不影响 MeasureResult。
                fun Modifier.lineVisual(): Modifier = this.graphicsLayer {
                    alpha = lineAlpha
                }

                // 颜色只表达**语义**（当前行 / 拖拽目标 / 普通），淡出量统一由上面的 alpha 接管。
                // （原实现在 color 上再叠 `copy(alpha=0.45f)`，与 graphicsLayer alpha 相乘后
                //   已播行几乎不可见 —— 现拆开各司其职。）
                //
                // 2026-10-29：普通行由 TextSecondary 改为 **TextPrimary** ——
                // TextSecondary 取自封面动态取色调色板，深色封面上取到的次要色本身就暗，
                // 再叠 0.45  alpha 在黑底上几乎不可见（超宽横屏实测整片歌词"消失"）。
                // 改用 TextPrimary 后由 alpha 单独控制层级：当前行 1.0 → 上下行 0.85 →
                // 远处行 0.45，任何封面上都保证可读。
                val textColor = when {
                    isSeekTarget -> NasMusicColors.Primary
                    isCurrent -> NasMusicColors.Primary
                    else -> NasMusicColors.TextPrimary
                }
                // 2026-10-30：滚动期间**字号恒定** —— 当前行 display 档、其余 subtitle 档
                //（= 歌手字号，见上方注释），行高由固定行槽保证不跳。
                val fontSize = if (isCurrent) currentFontSp else rowFontSp

                // 逐字模式：与 KARAOKE 页一致，使用平滑双层裁剪推进（半个字粒度），
                // 而非逐字跳变。
                // 2026-10-30 修复「逐字不同步」：有 wordTimestamps（LRC 内嵌逐字标记）
                // 时按真实字时间算 progress；否则回退到行级线性插值（兼容无逐字的 LRC）。
                if (isCurrent && highlightMode == LyricsHighlightMode.WORD_BY_WORD) {
                    val progress = if (line.wordTimestamps.isNotEmpty()) {
                        val words = line.wordTimestamps
                        // 当前唱到第几个字（effectiveTimeMs >= words[i].startMs 的最大 i）
                        var idx = -1
                        for (i in words.indices) {
                            if (effectiveTimeMs >= words[i].startMs) idx = i else break
                        }
                        when {
                            idx < 0 -> 0f
                            idx >= words.size - 1 -> 1f
                            else -> {
                                val wordStart = words[idx].startMs
                                val wordEnd = words[idx + 1].startMs
                                val frac = if (wordEnd > wordStart) {
                                    ((effectiveTimeMs - wordStart).toFloat() / (wordEnd - wordStart)).coerceIn(0f, 1f)
                                } else 1f
                                (idx + frac) / words.size
                            }
                        }
                    } else {
                        val nextLineTime = if (index + 1 < displayLines.size) {
                            displayLines[index + 1].time
                        } else {
                            line.time + 3000L
                        }
                        lineProgress(line.time, nextLineTime, effectiveTimeMs)
                    }
                    KaraokeLineText(
                        text = line.text,
                        progress = progress,
                        fontSize = currentFontSp,
                        textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
                        // 2026-10-28 用户定稿：逐字配色改为**红底 → 白字点亮** ——
                        // 未唱部分保持当前行色（品牌红），已唱字符点亮为白色。
                        // 原先高亮是纯黄（Color.Yellow），红底黄字既刺眼又和整页配色不搭。
                        baseColor = textColor,
                        highlightColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 行内上下留白由外层固定行槽 lineSlot 承担（5 行等距），
                            // 此处只保留 4dp 横向内缩，与工具行 / 歌名的左侧基线对齐。
                            .padding(horizontal = 4.dp)
                            .lineVisual()
                    )
                } else {
                    // ── 2026-10-28：行内**自适应缩字**（用户定稿：过长一行不得换行）──
                    // 先按当前字号单行排版，若溢出则按 [PlayerVisualSpec.LYRIC_AUTOFIT_STEP]
                    // 逐帧收字号，直到单行放下或到 [PlayerVisualSpec.LYRIC_AUTOFIT_MIN_SCALE]
                    // 下限（极端超长行才允许截断，绝不着换行撑高列表）。
                    // 缩放结果按 (文本, 字号) 记忆 —— 同一行反复重组不会重新收敛抖动。
                    //
                    // 2026-10-29 修复"当前行长行左右溢出被裁"：graphicsLayer 的 scaleX
                    // （当前行 1.15 / 相邻 1.05）不参与测量 —— 长行"测量放得下、放大后溢出"。
                    // autoFit 改按**放大后宽度**预算：文本测量宽 × 本行缩放目标 > 容器实测宽
                    // ⇒ 继续收字号（lineBoxW 由 padding **之后**的 onSizeChanged 实测，
                    //    即文字真正可用的宽度；2026-10-29 修正）。
                    var autoFitScale by remember(line.text, fontSize) { mutableStateOf(1f) }
                    var lineBoxW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    Text(
                        text = line.text,
                        color = textColor,
                        fontSize = fontSize * autoFitScale,
                        maxLines = 1,
                        textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
                        onTextLayout = { result ->
                            // 2026-10-30：graphicsLayer 缩放已移除（字号档位直接区分层级），
                            // 溢出判断简化为「涂绘宽 > 可用宽」。
                            // ⚠️ 仍必须用**文本涂绘宽**（getLineRight-LineLeft）而不是
                            // result.size.width —— Text 挂 fillMaxWidth 后后者恒等于
                            // 容器宽，会把所有行误判溢出、无差别缩到 0.55（2026-10-30
                            // 修掉的字号层级反转 bug 的根因），此处保留防回归注释。
                            val paintedW = try {
                                result.getLineRight(0) - result.getLineLeft(0)
                            } catch (_: Exception) {
                                result.size.width.toFloat()
                            }
                            val visualOverflow = lineBoxW > 0 && paintedW > lineBoxW
                            if ((result.hasVisualOverflow || visualOverflow) &&
                                autoFitScale > PlayerVisualSpec.LYRIC_AUTOFIT_MIN_SCALE
                            ) {
                                autoFitScale = (autoFitScale * PlayerVisualSpec.LYRIC_AUTOFIT_STEP)
                                    .coerceAtLeast(PlayerVisualSpec.LYRIC_AUTOFIT_MIN_SCALE)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            // 行内上下留白由外层固定行槽 lineSlot 承担（5 行等距），
                            // 此处只保留 4dp 横向内缩，与工具行 / 歌名的左侧基线对齐。
                            .padding(horizontal = 4.dp)
                            // ⚠️ onSizeChanged **必须在 padding 之后**：lineBoxW 要和 autoFit
                            // 的上限比较对象一致 —— 测「padding 前」的整列宽会让预算多出
                            // 左右各 4dp，当前行再乘 1.15 放大就正好越界，实测在 1080p
                            // 上长行被右边裁掉一小截。
                            .onSizeChanged { lineBoxW = it.width }
                            .lineVisual()
                    )
                }
                }   // 关闭行槽 Box
            }
            item {
                // 留白高度 = 半个视口（不是 padding(vertical=) 的两倍叠加），
                // 这样首行 / 末行中心也能精确落到视口中线上。
                Box(modifier = Modifier.fillMaxWidth().height(edgeSpacer))
            }
        }

        // —— 拖拽跳转指示：顶部虚线 + 目标行时间（松手跳到该行起始时间）——
        if (isDragSeeking) {
            val targetTime = displayLines.getOrNull(seekTargetLine)?.time
            val timeLabel = if (targetTime != null && targetTime >= 0) {
                val m = (targetTime / 60000).toString().padStart(2, '0')
                val s = ((targetTime % 60000) / 1000).toString().padStart(2, '0')
                "$m:$s"
            } else {
                "—"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 2026-10-30：指示条贴「视口 × anchorFraction」高亮落点（与当前行同一基线）
                    .offset(y = maxHeight * anchorFraction)
            ) {
                Text(
                    text = timeLabel,
                    color = NasMusicColors.Primary,
                    style = LocalLyricsTheme.current.normalLine,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .background(NasMusicColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Canvas(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(start = 88.dp, end = 24.dp)
                        .height(2.dp)
                        .fillMaxWidth()
                ) {
                    drawLine(
                        color = NasMusicColors.Primary,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                }
            }
        }

    }
}


