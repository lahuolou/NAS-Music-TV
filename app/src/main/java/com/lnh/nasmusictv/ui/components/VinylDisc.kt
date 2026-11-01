package com.lnh.nasmusictv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
// ⚠️ matchParentSize 是 BoxScope 的**成员**扩展（不是顶层函数），在 Box{} 作用域内
//    自动可用，既不需要也无法顶层 import —— 强行 import 会报 Unresolved reference。
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec
/**
 * 黑胶动效参数（§10.4 动效参数建议 / §11 性能降级）。集中管理，禁止散落硬编码。
 */
internal object VinylSpec {
    /** 缓起时长（ms）——§10.4「恢复缓起」，取自统一常量源 */
    const val SPIN_RAMP_MS = PlayerVisualSpec.DISC_SPIN_UP_MS
    /** 缓停时长（ms）——§10.4「暂停缓停 300–500ms」 */
    const val SPIN_HALT_MS = PlayerVisualSpec.DISC_SPIN_DOWN_MS
    /** 封面光晕呼吸周期（ms），2026-10-30 起全可见态恒呼吸（不再仅播放时运行） */
    const val GLOW_BREATH_MS = PlayerVisualSpec.DISC_GLOW_PERIOD_MS
    /** 减弱动效 / 暂停 / 弹窗遮挡下的静态光晕强度（取呼吸区间中值，构图不跳变） */
    const val GLOW_STATIC_ALPHA = 0.65f
}

/** 缓起期间额外转过的角度（约 16s/圈 匀速值的一半 Ernest16 ⇒ 观感是"起步偏慢"） */
private const val SPIN_RAMP_DEG = 8f
/** 缓停期间滑过的角度（约 22.5°/s × 450ms × ½） */
private const val SPIN_HALT_DEG = 6f

/**
 * 网易云风格黑胶唱盘组件
 *
 * 核心意象：黑色圆形盘面 + 同心沟纹 + 中心圆标签，封面圆嵌盘心；
 * 播放时整盘（含封面）缓慢旋转（16s/圈），暂停冻结。
 * 唱针自右上角伸出：播放落下贴盘缘、暂停抬起（[tonearmAnimated]=true 时微交互动画，
 * TV/低端设备传 false 简化为"唱片旋转 + 唱针静态示意"）。
 *
 * 盘面不做真实 blur/发光：用径向渐变 + 极淡绛红光晕模拟空气感（性能降级原则）。
 *
 * @param coverCandidates 封面候选（复用 [CoverCarousel] 的 fallback 与占位逻辑，静态单张展示）
 * @param isPlaying 播放中则旋转；暂停**缓停**滑一小段后冻结，唱针抬起（仅 [tonearmAnimated] 时）
 * @param tonearmAnimated 唱针落下/抬起微交互；false 时唱针直接落到终态（TV 静态示意）
 * @param tonearmVisible 2026-10-30 用户定稿：**只有「5s 沉浸隐藏」态才显示唱臂**。
 *                       false 时整棵唱臂子树**不进入组合**（不绘制、不跑动画、零 tick），
 *                       由隐藏态退出再卸载。默认 true 保持其它复用点的原行为。
 * @param reduceMotion 减弱动效总闸；为 true 时光晕退化静态常量、不再呼吸（§11-8）
 * @param paused 页面被遮挡（弹窗/队列打开）时暂停光晕呼吸；§10.3「弹窗/队列打开时暂停封面光晕」
 * @param glowColor 盘外光晕色（优先传封面取色的 [CoverPalette.accent]，
 *                 让光晕跟着封面走；取色不可用时调用方传回品牌默认色）
 * @param slideDirection 切歌方向，决定封面在盘内圆窗「换碟滑动」的**运动方向**
 *                       （2026-10-30 二次定稿：底盘绝对静止，仅盘心封面滑动）：
 *                       `+1` = 下一曲 → 旧封面**从右往左**滑出、新封面自右滑入；
 *                       `-1` = 上一曲 → 从左往右。必须由调用方在**按下按钮那一刻**
 *                       记录并下传 —— 切歌后封面变了，就再也无从判断是从哪一侧换过来的。
 * @param playbackProgress 播放进度 0..1（2026-10-30 用户定稿：**唱针径向位置与进度绑定**
 *                         —— 针尖从唱片外缘 [PlayerVisualSpec.TONEARM_NEEDLE_OUTER_R]
 *                         向内圈 [PlayerVisualSpec.TONEARM_NEEDLE_INNER_R] 匀速径向内移，
 *                         模拟真实黑胶的物理播放状态）。
 * @param songKey 当前歌曲的稳定标识（如 song.id；2026-10-30 用户定稿新增）：
 *                变化即触发唱臂「快速弹开 → 悬停 → 落下」三段式 —— 悬停保持
 *                [PlayerVisualSpec.TONEARM_SONG_HOLD_MS]，等封面滑动与背景转场
 *                完成后才落下合并。null 时该联动关闭。
 */
@Composable
fun VinylDisc(
    coverCandidates: List<String>,
    isPlaying: Boolean,
    tonearmAnimated: Boolean,
    // 2026-10-30 用户定稿：唱臂**仅在 5s 沉浸隐藏态加载**（见上方 KDoc）
    tonearmVisible: Boolean = true,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    paused: Boolean = false,
    glowColor: Color = NasMusicColors.PrimaryVariant,
    slideDirection: Int = 1,
    playbackProgress: Float = 0f,
    songKey: Any? = null,
) {
    // P1-2 光晕是**暗色专属**特效（最高优先级 A2：亮色主题不得出现黑胶暗色光晕）。
    // 直接读 LocalDarkTheme —— 主题判断集中在这一处，不向上层要求新参数（§12-10）。
    val darkTheme = com.lnh.nasmusictv.ui.theme.LocalDarkTheme.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // P1-2 封面边缘光晕：**全可见态恒呼吸**（2026-10-30 用户定稿，与进度条光晕
        // 「所有可见状态恒呼吸、不区分播放/暂停」同一口径）—— 此前被 isPlaying 门控，
        // 一暂停就静止，与进度条的呼吸对比之下像"呼吸丢了"。
        // ⚠️ 条件早返回（不创建 InfiniteTransition）——这些情况下本机不再有任何 tick 在跑。
        val glowAlpha = vinylGlowAlpha(
            animate = !reduceMotion && !paused && darkTheme
        )
        // 亮色主题不绘制光晕层：白色/浅色底上一圈绛红光晕是"暗色特效"越界
        if (darkTheme) {
            // ⚠️ 修复：**盘外光晕此前等于没有** —— 旧写法是 matchParentSize()，
            // 光晕层与盘面尺寸完全重合，被上面不透明的盘面整块盖住，一点都看不见。
            // 这里用 graphicsLayer 把光晕放大到 1.22 倍（不改布局占位），让外圈那一段
            // 露在盘外形成真正的"盘外光晕"；色标也改成"盘缘处最亮、向外淡出"。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = glowAlpha
                        scaleX = PlayerVisualSpec.DISC_GLOW_SCALE
                        scaleY = PlayerVisualSpec.DISC_GLOW_SCALE
                    }
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                // 盘面内（会被盘盖住，任意值）
                                0.0f to glowColor.copy(alpha = 0.50f),
                                0.78f to glowColor.copy(alpha = 0.30f),
                                // ⚠️ 2026-10-30 修复「光晕呼吸看不见 + 盘不够圆的观感」：
                                // scale=1.22 时盘缘位于光晕层的 1/1.22 ≈ 0.82 处，旧色标把
                                // 亮环放在 0.90 —— 亮环悬浮在离盘缘很远的细环上，且右半边
                                // 被较亮的背景洗掉，轮廓残缺（盘看起来"不圆"）、环又太淡
                                //（呼吸变化察觉不到）。现在亮环收回**盘缘外侧贴边**：
                                // 0.82 盘缘 → 0.85 最亮 → 向外自然淡出。
                                0.85f to glowColor.copy(alpha = 0.60f),
                                0.94f to glowColor.copy(alpha = 0.22f),
                                // 向外自然淡出，不出现硬边
                                1.0f to Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        // 盘外底座 / 阴影：比盘面略大的一圈柔和暗环，营造"悬浮于底座之上"的体量感
        // （暗色主题专属：亮色底上一圈暗影会显脏，与光晕同理门控）
        if (darkTheme) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringR = r * PlayerVisualSpec.DISC_BASE_SHADOW_SCALE
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.84f to Color.Transparent,
                            // 暗环正好落在盘缘外侧（disc 边缘 ≈ 0.93 * ringR）
                            0.93f to Color.Black.copy(alpha = PlayerVisualSpec.DISC_BASE_SHADOW_ALPHA),
                            1.0f to Color.Transparent
                        ),
                        center = center,
                        radius = ringR
                    ),
                    radius = ringR,
                    center = center
                )
            }
        }
        // 旋转盘面 + 封面圆（整盘一起转）
        //
        // ⚠️ 旧的写法是「LaunchedEffect(isPlaying) { while(true) animateTo(+360, 16s) }」：
        // isPlaying 翻转时协程被取消 ⇒ 旋转**瞬间冻结**，视觉上是"卡死"而非"停下"。
        // 改为「缓起 / 缓停」（§10.3：暂停缓停 300–500ms，恢复缓起）：
        //   - 起播：先用 FastOutLinearIn 由慢加速转过一小段，再衔接匀速（隐喻唱片起步惯性）
        //   - 暂停：用 LinearOutSlowIn 减速滑停一小段后冻结（隐喻摩擦减速）
        // 两个分支都由同一个 LaunchedEffect(isPlaying) 驱动，切换时旧协程取消、
        // 新协程从**当前角度**接续，不会出现角度跳变（§10.3「旋转角度重置不突兀」）。
        val angle = remember { Animatable(0f) }
        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                // 缓起
                angle.animateTo(
                    angle.value + SPIN_RAMP_DEG,
                    tween(VinylSpec.SPIN_RAMP_MS, easing = FastOutLinearInEasing)
                )
                while (true) {
                    // 匀速旋转：§10.4「20–30s/圈，linear」
                    angle.animateTo(
                        angle.value + 360f,
                        tween(PlayerVisualSpec.DISC_SPIN_MS, easing = LinearEasing)
                    )
                }
            } else {
                // 缓停（首次进入即为暂停态时也存在：从 0 滑停，无害）
                angle.animateTo(
                    angle.value + SPIN_HALT_DEG,
                    tween(VinylSpec.SPIN_HALT_MS, easing = LinearOutSlowInEasing)
                )
            }
        }
        // ── 2026-10-30 二次定稿：**黑胶底盘绝对静止**，只有盘心封面在圆窗内
        // 「滑动 + 淡入淡出」换碟（旧方案整盘横移被此版取代）。
        // 实现仍用 Animatable 位移 + 中途换封面 key，而不是 AnimatedContent：
        // 后者会同时组合两份盘面绘制（每帧多画一遍 200+ 圆环），成本翻倍。
        val slideHalfMs = PlayerVisualSpec.DISC_COVER_SLIDE_MS / 2
        // 运动方向：+1 = 下一曲（新封面从右往左滑入），-1 = 上一曲（从左往右滑入）。
        // ⚠️ 只在**位移公式的符号**里用它：滑出与滑入是同一方向的连续两段，
        //    所以"滑出到 ±1"接着"从 ∓1 滑回 0"必须共用同一个符号，视觉上才是
        //    一条单向的换碟轨迹（而不是先左后右地抖一下）。
        val dirSign = if (slideDirection >= 0) 1f else -1f
        var displayedCovers by remember { mutableStateOf(coverCandidates) }
        var displayedKey by remember { mutableStateOf(coverCandidates.firstOrNull()) }
        val coverSlide = remember { Animatable(0f) }
        LaunchedEffect(coverCandidates.firstOrNull()) {
            val target = coverCandidates.firstOrNull()
            val firstFill = displayedKey == null && target != null
            if (reduceMotion || firstFill || displayedKey == target) {
                // 首次拿到封面 / 开关关闭：不做"换碟"，直接落位
                displayedCovers = coverCandidates
                displayedKey = target
                coverSlide.snapTo(0f)
                return@LaunchedEffect
            }
            // 1) 旧封面沿换碟方向滑出圆窗并淡出（此刻盘上仍是**旧封面**）
            coverSlide.animateTo(1f, tween(slideHalfMs, easing = FastOutLinearInEasing))
            // 2) 封面在窗外/透明时换成新的
            displayedCovers = coverCandidates
            displayedKey = target
            // 3) 新封面从**另一侧**滑入圆窗并淡入，与上一段同向
            coverSlide.snapTo(-1f)
            coverSlide.animateTo(0f, tween(slideHalfMs, easing = FastOutSlowInEasing))
        }
        // ── 2026-10-30 用户定稿：切歌 → 唱臂「快速弹开 → 悬停 → 落下」三段式 ──
        // songKey 变化（与封面是否变化无关）即置 [armHold]：臂先弹开抬起并保持悬停，
        // 等 TONEARM_SONG_HOLD_MS（封面滑动 + 背景转场都完成）后再落回播放位。
        var armHold by remember { mutableStateOf(false) }
        var armInitialized by remember { mutableStateOf(false) }
        LaunchedEffect(songKey) {
            if (songKey == null) return@LaunchedEffect
            if (!armInitialized) {
                armInitialized = true
                return@LaunchedEffect
            }
            if (reduceMotion) return@LaunchedEffect
            armHold = true
            kotlinx.coroutines.delay(PlayerVisualSpec.TONEARM_SONG_HOLD_MS.toLong())
            armHold = false
        }
        // 盘面纹理层：**只做自转**（沟纹/高光/使用痕迹随盘转），不再承担换碟位移
        // —— 底盘绝对静止是本轮定稿，位移全部收进下方封面圆窗内部。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value % 360f }
        ) {
            // 盘面：近黑实底 + 非对称环境光 + 密纹沟纹（间距微扰，去"示意图"感）+ 使用痕迹 + 中心标签/孔
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // 盘底：真实黑胶近纯黑，外缘略提一点"深灰黑"让圆缘有体积感（去"纸片圆"感）
                drawCircle(color = Color(0xFF0B0C0F), radius = r, center = center)
                // ⚠️ 2026-10-30 修复「盘不够圆」的观感：盘面近黑、背景也是暗渐变，
                // 盘缘（尤其右下背光侧）会融进背景，圆的轮廓看起来缺一块/发扁。
                // 补一圈**极淡的亮描边**把完整圆周勾勒出来（真实黑胶盘缘也有高光反光）。
                drawCircle(
                    color = Color.White.copy(alpha = 0.055f),
                    radius = r * 0.995f,
                    center = center,
                    style = Stroke(width = r * 0.012f)
                )
                drawCircle(
                    color = Color(0xFF1B1D22).copy(alpha = 0.55f),
                    radius = r * 0.985f,
                    center = center,
                    style = Stroke(width = r * 0.012f)
                )

                // 环境光：光源偏左上，非对称径向渐变（自然反光，不是中心对称的"设计稿渐变"）
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2C2E33).copy(alpha = 0.50f),
                            Color(0xFF141519).copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(center.x - r * 0.38f, center.y - r * 0.42f),
                        radius = r * 1.55f
                    ),
                    radius = r,
                    center = center
                )

                // 左上高光（黑胶镜面反射）：小范围、上沿更亮的"釉光"，强化"黑胶"材质观感
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = PlayerVisualSpec.DISC_SPECULAR_ALPHA),
                            0.5f to Color.White.copy(alpha = PlayerVisualSpec.DISC_SPECULAR_ALPHA * 0.35f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(center.x - r * 0.50f, center.y - r * 0.55f),
                        radius = r * 0.95f
                    ),
                    radius = r,
                    center = center
                )

                // 密纹沟纹：数量适中、间距带确定性微扰、极低 alpha（真实唱片纹感，非等距圆环）
                // 2026-10-28 用户定稿"圈数少一点"：起始 0.60r（封面 0.56 直径 → 0.28r 之外），
                // 间距 0.034r ⇒ 约 11 圈（原 0.021r ≈ 22 圈，太密像线稿）。
                var rr = r * PlayerVisualSpec.DISC_GROOVE_START
                val step = r * PlayerVisualSpec.DISC_GROOVE_STEP
                var k = 0
                while (rr < r * 0.97f) {
                    // 确定性伪随机（k 派生）：间距与半径都带 ±20% 微扰，避免机械等距
                    val jitter = (((k * 7919) % 100) - 50) / 250f   // -0.2 .. 0.2
                    drawCircle(
                        color = Color.White.copy(alpha = PlayerVisualSpec.DISC_GROOVE_ALPHA),
                        radius = (rr + jitter * step).coerceAtLeast(r * 0.05f),
                        center = center,
                        style = Stroke(width = 0.8f)
                    )
                    rr += step * (1f + jitter)
                    k++
                }

                // 使用痕迹：几道极淡短弧（确定性位置），黑胶的"实体物"质感
                val scratchColor = Color.White.copy(alpha = 0.020f)
                for (s in 0 until 4) {
                    val sr = r * (0.68f + 0.08f * s)
                    val startDeg = ((s * 47 + 13) % 360).toFloat()
                    val sweepDeg = 24f + 10f * s
                    drawArc(
                        color = scratchColor,
                        startAngle = startDeg,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = Offset(center.x - sr, center.y - sr),
                        size = androidx.compose.ui.geometry.Size(sr * 2f, sr * 2f),
                        style = Stroke(width = 0.7f)
                    )
                }
                // 2026-10-28 用户定稿：**移除盘面与封面之间的金色描边圈**（原先那圈金线
                // 夹在黑胶与封面之间显得多余、且与封面配色常打架）。封面直接压在盘心，
                // 交接处只保留一圈极淡的暗边（下面 Box 的阴影）来分层。
            }
        }
        // 封面圆窗（2026-10-30 二次定稿）：**圆窗本身绝对静止**（不随盘转、不参与换碟
        // 位移），旋转与滑动都在窗内的内容层上发生 —— 即 CSS 语义里的
        // overflow:hidden 圆形切割：封面滑出/滑入都被 clip(CircleShape) 裁在盘心圆内。
        // 2026-10-28：直径 0.46 → 0.56（[PlayerVisualSpec.DISC_COVER_FRACTION]）。
        // ⚠️ 内容层 graphicsLayer 的合成顺序 scale → rotation → translation：
        //    rotation 让封面**绕自身中心随盘自转**（旋转正方形恒包含其内切圆，
        //    静止圆窗始终被铺满）；translation 在窗坐标系里生效 → 换碟轨迹恒为水平横移，
        //    不会被自转带歪。alpha = 1-|slide| 实现「滑出淡出 / 滑入淡入」。
        Box(
            modifier = Modifier
                .fillMaxSize(PlayerVisualSpec.DISC_COVER_FRACTION)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(NasMusicColors.Surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle.value % 360f
                        // dirSign=+1（下一曲）：0 → -1.1w（旧封面向左滑出）→ 由 +1.1w 滑入
                        // dirSign=-1（上一曲）：0 → +1.1w → 由 -1.1w 滑入
                        translationX = -dirSign * coverSlide.value * size.width * 1.1f
                        alpha = (1f - kotlin.math.abs(coverSlide.value)).coerceIn(0f, 1f)
                    }
            ) {
                CoverCarousel(
                    coverCandidates = displayedCovers,
                    isPlaying = false,
                    autoCycle = false,
                    contentScale = ContentScale.Crop,
                    // 封面切换本身不再做淡变：盘内滑动 + 淡入淡出已经是转场本体
                    crossfadeMs = 0,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── 唱针（2026-10-30 用户定稿：仅「5s 沉浸隐藏」态加载）──────────────
        // ⚠️ `if (tonearmVisible)` 包的是**整个 ToneArm 组合子树** —— 未隐藏时不组合：
        // Canvas 不绘制、Animatable 与 Inf 过渡不创建、也没有每帧 tick（§11-2）。
        // 进入隐藏态时才加载并播放「从抬起落到盘面」的弹簧动画；退出隐藏立即卸载。
        if (tonearmVisible) {
            ToneArm(
                isPlaying = isPlaying,
                // 2026-10-30 用户定稿：切歌瞬间臂先快速弹开、悬停到转场结束才落下
                holdLifted = armHold,
                animated = tonearmAnimated,
                reduceMotion = reduceMotion,
                playbackProgress = playbackProgress,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 唱针（自盘外右上角伸出的白臂 + 枢轴柔光）。
 *
 * 参考网易云实拍：枢轴在盘外上方，臂身轻微弯杆（二次贝塞尔）+ 右下投射阴影，
 * 唱头壳顺臂向倾斜，落点带一颗品牌红小点。
 *
 * @param isPlaying 播放中落在盘面；暂停抬起 [PlayerVisualSpec.TONEARM_LIFT_DEG]
 *        （2026-10-30 用户定稿：暂停 = 弹开并**保持悬停**不落下；恢复播放 = 从悬停落下合并）
 * @param holdLifted 切歌悬停开关（2026-10-30 用户定稿）：true 时无论播放态一律抬到
 *        悬停位 —— 由 [VinylDisc] 在切歌瞬间置 true、转场结束后置 false，
 *        形成「弹开 → 悬停 → 落下」三段式。
 * @param animated  是否做弹簧式落下/抬起微交互；false 时直接落到终态（TV 静态示意）
 * @param reduceMotion 减弱动效总闸：true 时不做任何过渡动画（§11-8）
 * @param playbackProgress 播放进度 0..1：驱动针尖**径向内移**（见 [VinylDisc] 文档）。
 *        实现方式：针尖距盘心的目标距离 d(p) 在外圈/内圈间线性插值，再反解
 *        「枢轴 → 长度 [PlayerVisualSpec.TONEARM_LEN] 的臂」落在该距离上的旋转角
 *        （余弦定理，见 [needleRadialOffsetDeg]），叠加到 [PlayerVisualSpec.TONEARM_BASE_DEG]
 *        之上 —— 抬起/入场动画不受影响。
 *
 * ⚠️ 初始角度刻意从**抬起**起步 —— 本组件只在进入隐藏态那一刻被组合，
 *    因此这一起步）构造天然形成「进入隐藏 → 唱臂落下」的入场动画。
 */
@Composable
private fun ToneArm(
    isPlaying: Boolean,
    holdLifted: Boolean,
    animated: Boolean,
    reduceMotion: Boolean,
    playbackProgress: Float,
    modifier: Modifier = Modifier
) {
    // 2026-10-30 用户定稿：抬起条件 = 暂停 ∨ 切歌悬停；两者都消失（恢复播放且转场完）
    // 才落回盘面。
    val restTarget = if (!isPlaying || holdLifted) PlayerVisualSpec.TONEARM_LIFT_DEG else 0f
    val armAngle = remember { Animatable(PlayerVisualSpec.TONEARM_ENTER_DEG) }
    LaunchedEffect(restTarget, animated, reduceMotion) {
        if (!animated || reduceMotion) {
            armAngle.snapTo(restTarget)
        } else {
            // 抬起段用「快速弹开」高刚度弹簧（啪地抬起），落下段保持慢弹簧（放针的仪式感）
            val lifting = restTarget < armAngle.value
            armAngle.animateTo(
                restTarget,
                spring(
                    dampingRatio = if (lifting) {
                        PlayerVisualSpec.TONEARM_LIFT_DAMPING
                    } else {
                        PlayerVisualSpec.TONEARM_SPRING_DAMPING
                    },
                    stiffness = if (lifting) {
                        PlayerVisualSpec.TONEARM_LIFT_STIFFNESS
                    } else {
                        PlayerVisualSpec.TONEARM_SPRING_STIFFNESS
                    }
                )
            )
        }
    }
    // 2026-10-30 用户定稿：唱针红点光晕改**呼吸灯** —— 播放中光晕强度缓慢脉动，
    // 暂停 / 减弱动效退化为静态中值（零 tick，§11-8）。呼吸只改光晕 alpha，
    // 半径与位置不动，红点本体始终最后绘制压在最上层（不遮挡唱头/臂身）。
    val glowBreath = if (isPlaying && !reduceMotion) {
        rememberInfiniteTransition(label = "needleGlowBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    PlayerVisualSpec.TONEARM_GLOW_BREATH_MS,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "needleGlowPhase"
        ).value
    } else 0.5f
    Canvas(modifier = modifier) {
            val w = size.width
            // 枢轴位于**盘外上方**（画布外）：唱臂整体悬在盘外，只有唱头搭在盘缘右上
            // （2026-10-28 用户定稿：唱臂不能压在盘面上，要和盘"错开"）
            val pivot = Offset(
                w * PlayerVisualSpec.TONEARM_PIVOT_X,
                w * PlayerVisualSpec.TONEARM_PIVOT_Y
            )
            val armLen = w * PlayerVisualSpec.TONEARM_LEN
            val baseDeg = PlayerVisualSpec.TONEARM_BASE_DEG

            // 枢轴底座：**外发光**（白色柔光球，2026-10-28 用户定稿参考示意图）
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = PlayerVisualSpec.TONEARM_PIVOT_GLOW_ALPHA),
                        0.42f to Color.White.copy(alpha = PlayerVisualSpec.TONEARM_PIVOT_GLOW_ALPHA * 0.45f),
                        1.0f to Color.Transparent
                    ),
                    center = pivot,
                    radius = w * PlayerVisualSpec.TONEARM_PIVOT_GLOW_RADIUS
                ),
                radius = w * PlayerVisualSpec.TONEARM_PIVOT_GLOW_RADIUS,
                center = pivot
            )
            // 枢轴本体：白色金属转轴 + 内圈暗轴心（去"线条示意图"感）
            drawCircle(color = Color(0xFFD9DBE0), radius = w * 0.040f, center = pivot)
            drawCircle(color = Color(0xFF8E9198), radius = w * 0.015f, center = pivot)

            // 2026-10-30 用户定稿：唱针径向位置随播放进度内移（余弦定理反解转角）
            val radialDeg = needleRadialOffsetDeg(size.width, playbackProgress)

            rotate(degrees = baseDeg + armAngle.value + radialDeg, pivot = pivot) {
                val head = Offset(pivot.x + armLen, pivot.y)
                // 弯杆：从枢轴到唱头用二次贝塞尔，控制点上抬形成肉眼明显的弓形弧
                val ctrlX = (pivot.x + head.x) / 2f
                val ctrlY = (pivot.y + head.y) / 2f - w * PlayerVisualSpec.TONEARM_BEND_FRACTION
                val armPath = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    quadraticBezierTo(ctrlX, ctrlY, head.x, head.y)
                }
                // 投射阴影：向右下偏移的半透明粗描边，制造悬浮感
                drawContext.canvas.save()
                drawContext.canvas.translate(w * 0.006f, w * 0.010f)
                drawPath(
                    path = armPath,
                    color = Color.Black.copy(alpha = 0.30f),
                    style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
                )
                drawContext.canvas.restore()
                // 臂身（弯杆、圆头、浅银白）
                drawPath(
                    path = armPath,
                    color = Color(0xFFEAECEF),
                    style = Stroke(width = w * 0.013f, cap = StrokeCap.Round)
                )
                // 唱头壳（细长圆角块，顺臂向）
                rotate(degrees = 10f, pivot = head) {
                    drawRoundRect(
                        color = Color(0xFFB9BCC3),
                        topLeft = Offset(head.x - w * 0.010f, head.y - w * 0.016f),
                        size = androidx.compose.ui.geometry.Size(w * 0.040f, w * 0.032f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.008f)
                    )
                }
                // 唱针触点（品牌红小点——唯一强调色，保留可辨识的"落点"）
                // 2026-10-30 用户定稿：红点**同心光晕** —— 先画径向渐变光晕
                //（PrimaryBright→透明，圆心与红点完全一致），再画红点本体；
                // 光晕只向外扩散、不遮挡唱头与臂身（光晕半径 2.6× 红点半径，
                // 中心 25% 内仍是透明度极高的亮区，红点本体最后绘制压在最上层）。
                // 2026-10-30 再定稿：光晕改**呼吸灯** —— 强度随 [glowBreath] 脉动。
                val dotCenter = Offset(head.x + w * 0.016f, head.y + w * 0.006f)
                val dotR = w * 0.008f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to NasMusicColors.PrimaryBright.copy(alpha = 0.30f + 0.45f * glowBreath),
                            0.30f to NasMusicColors.Primary.copy(alpha = 0.16f + 0.24f * glowBreath),
                            1.0f to Color.Transparent
                        ),
                        center = dotCenter,
                        radius = dotR * 2.6f
                    ),
                    radius = dotR * 2.6f,
                    center = dotCenter
                )
                drawCircle(
                    color = NasMusicColors.Primary.copy(alpha = 0.85f),
                    radius = dotR,
                    center = dotCenter
                )
            }
        }
    }

/**
 * 播放进度 → 唱臂**径向附加转角**（度）。
 *
 * 几何：盘心 C = 画布中心，枢轴 P，臂长 L。针尖距盘心目标距离
 * d(p) = 盘半径 × lerp([PlayerVisualSpec.TONEARM_NEEDLE_OUTER_R],
 * [PlayerVisualSpec.TONEARM_NEEDLE_INNER_R], p)。由余弦定理
 * `cos(θ−φ) = (d² − |PC|² − L²) / (2·|PC|·L)`（φ = PC 方位角）反解臂角 θ，
 * 取与现行 [PlayerVisualSpec.TONEARM_BASE_DEG] 同侧（+acos）的解，
 * 返回 `θ(d(p)) − θ(d(0))` —— 进度 0 时附加角为 0，针尖落在唱片外缘。
 */
private fun needleRadialOffsetDeg(w: Float, progress: Float): Float {
    val cx = w / 2f
    val cy = w / 2f
    val px = w * PlayerVisualSpec.TONEARM_PIVOT_X
    val py = w * PlayerVisualSpec.TONEARM_PIVOT_Y
    val armLen = w * PlayerVisualSpec.TONEARM_LEN
    val vx = px - cx
    val vy = py - cy
    val pv = kotlin.math.sqrt(vx * vx + vy * vy)
    if (pv <= 0f || armLen <= 0f) return 0f
    val phi = kotlin.math.atan2(vy, vx)
    val discR = w / 2f
    val p = progress.coerceIn(0f, 1f)
    val dOuter = discR * PlayerVisualSpec.TONEARM_NEEDLE_OUTER_R
    val dInner = discR * PlayerVisualSpec.TONEARM_NEEDLE_INNER_R
    val d = dOuter + (dInner - dOuter) * p
    fun thetaFor(dist: Float): Float {
        val k = ((dist * dist - pv * pv - armLen * armLen) / (2f * pv * armLen))
            .coerceIn(-1f, 1f)
        return phi + kotlin.math.acos(k)
    }
    return Math.toDegrees((thetaFor(d) - thetaFor(dOuter)).toDouble()).toFloat()
}

/**
 * 黑胶光晕强度：**全可见态恒呼吸**，在 0.35 ↔ 1.0 间反向往复（周期 [VinylSpec.GLOW_BREATH_MS]）。
 * 与进度条光晕同一口径（不区分播放/暂停）。
 *
 * ⚠️ 与背景呼吸同理，[animate] 为 false 时**不创建** InfiniteTransition 而直接返回静态值
 * —— 暂停 / 减弱动效下本机零 tick（§10.1-4「暂停时相关动画要暂停或降级」、§11-8）。
 */
@Composable
private fun vinylGlowAlpha(animate: Boolean): Float {
    if (!animate) return VinylSpec.GLOW_STATIC_ALPHA
    val transition = rememberInfiniteTransition(label = "vinylGlow")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(VinylSpec.GLOW_BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vinylGlowAlpha"
    ).value
}
