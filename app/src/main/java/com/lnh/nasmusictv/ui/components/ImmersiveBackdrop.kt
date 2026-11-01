package com.lnh.nasmusictv.ui.components

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lnh.nasmusictv.ui.theme.CoverPalette
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec

/**
 * 沉浸式播放页**背景层**（P0-1：封面模糊背景 + 暗色遮罩）。
 *
 * ## 仅在暗色主题启用（最高优先级 A2）
 * [enabled] 为 `false`（亮色主题）时本组件**不渲染封面层**，只留透明 Box：
 * 亮色主题下不得出现深色模糊背景与暗色遮罩（验收 A4-2）。布局不受影响，
 * 由调用方在亮色分支沿用原有背景，实现「换背景不动布局」。
 *
 * ## 分层结构（自底向上）
 * 1. **取色背景层**（新增）：[palette] 可用时，用封面取出的**背景色 / 中段色**
 *    铺一层竖向渐变 —— 整页色调跟着封面走（网易云的"智能取色"观感）
 * 2. **封面图层**：当前封面超低分辨率解码后 `Crop` 铺满，播放中缓慢呼吸缩放；
 *    取色可用时压到低透明度（[PlayerVisualSpec.PALETTE_COVER_ALPHA]）只作纹理，
 *    不可用（取色失败）时仍是主体
 * 3. **暗色遮罩层**：竖向渐变（§6.1 —— 全屏 40%~60% 黑，并额外压暗顶部/底部，
 *    保证顶栏歌曲名与底部进度时间的可读性）；这是「遮罩」而非「背景」，永远在最上层
 *
 * 无封面 / 封面全部加载失败时 → **照片墙壁纸虚化 + 渐变兜底**（2026-10-30 用户定稿恢复）：
 * 系统壁纸按屏宽自适应降采样并做两级缩放近似虚化，半透明铺在调用方的深色渐变之上；
 * 壁纸取不到（TV 无壁纸 / 权限受限）时只剩渐变兜底。
 *
 * ## 遥控器无关性
 * 本组件**不可聚焦**（没有任何 focusable / Surface），不会成为焦点陷阱（§9.3 / 禁止事项 9），
 * 也不拦截按键 —— 特效不得阻塞返回键与焦点移动（§10.1-8）。
 *
 * @param coverCandidates 封面候选 URL（首个可用者作为背景；失败自动顺延下一个）
 * @param isPlaying 播放中才推进呼吸动画；暂停时**保持**当前缩放不再变化（§10.3）
 * @param enabled 是否启用深色沉浸式背景（暗色主题 = true）
 * @param reduceMotion 减弱动效总闸；为 true 时呼吸缩放退化为静态常量（§11-8）
 * @param paused 页面被遮挡（弹窗/队列打开）时暂停呼吸；§10.3「弹窗/队列打开时暂停背景呼吸」
 * @param palette 封面智能取色结果（[CoverPalette]）；非空时用其颜色渲染背景层，
 *                null = 取色不可用，退回"封面模糊图 + 固定遮罩"的旧方案
 * @param slideDirection 切歌方向（2026-10-30 用户定稿：**背景全屏方向性滑动转场**）：
 *                `+1` = 下一曲 → 新背景从右往左滑入（旧背景向左滑出）；
 *                `-1` = 上一曲 → 从左往右。与黑胶封面换碟同一节奏
 *                （[PlayerVisualSpec.DISC_COVER_SLIDE_MS]/2 × 两段）。
 */
@Composable
fun ImmersiveBackdrop(
    coverCandidates: List<String>,
    isPlaying: Boolean,
    enabled: Boolean,
    reduceMotion: Boolean,
    paused: Boolean = false,
    palette: CoverPalette? = null,
    slideDirection: Int = 1,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // clipToBounds：呼吸放大到 1.05 后图层会超出 Box 边界，
    // 不裁的话会盖住父容器之外的内容（并导致不必要的过度绘制）。
    // ⚠️ 只看字面会觉得多余——graphicsLayer 的 scale 不受 Box 尺寸约束。
    // 2026-10-30：换歌方向性滑动（背景整体平移 ±1.1 屏宽）也依赖这层裁剪。
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        if (!enabled) return@Box

        if (palette != null) {
            // ① 取色背景：中段色 → 背景色 → 更暗的底色，自上而下收拢。
            // 三档都是"压暗后保留色相"的封面色，因此整页氛围随切歌平滑切换（Coil 之外零开销）。
            //
            // 2026-10-30 用户定稿「切歌转场（含方向性）」：背景**全屏滑动 + 淡入淡出**
            // —— 旧 Crossfade（原地淡换）升级为方向性位移转场：点下一曲新背景从右往左
            // 滑入（旧背景向左滑出淡去），上一曲反向。实现与黑胶封面换碟同构：
            // Animatable 位移 + 中途换内容 key，避免 AnimatedContent 双树常驻。
            // produceState 在取色完成前一直持有**上一曲**的 palette（key 变化不重置 value），
            // 因此滑动期间盘上/屏上始终是旧色，新色就绪瞬间开始转场，看不到黑底。
            val dirSign = if (slideDirection >= 0) 1f else -1f
            // 旧 palette（切歌前那一曲）的记忆：作为转场期间滑出的覆盖层。
            // 与旧实现的关键区别：底层永远铺「新 palette」且恒不透明，旧层只在它之上滑出淡去，
            // 因此转场全程看不到任何透明帧 → 根除「当前背景 → 黑 → 下一曲背景」的黑闪。
            var prevPalette by remember { mutableStateOf<CoverPalette?>(null) }
            val bgSlide = remember { Animatable(0f) }
            LaunchedEffect(palette) {
                val firstFill = prevPalette == null
                if (reduceMotion || firstFill || prevPalette == palette) {
                    // 首帧 / 同色 / 减弱动效：直接落定，不做滑动转场
                    prevPalette = palette
                    bgSlide.snapTo(0f)
                    return@LaunchedEffect
                }
                // 旧背景沿切歌方向滑出全屏并淡出，底层新色随之显现（无黑帧）
                bgSlide.snapTo(0f)
                bgSlide.animateTo(
                    1f,
                    tween(
                        PlayerVisualSpec.DISC_COVER_SLIDE_MS / 2,
                        easing = FastOutLinearInEasing
                    )
                )
                prevPalette = palette
                bgSlide.snapTo(0f)
            }
            // ① 常驻底色层 = 新 palette：整段转场恒在，旧层透明时也只露出新色，不再闪黑
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.surface,
                                palette.background,
                                palette.background.copy(alpha = 1f)
                            )
                        )
                    )
            )
            // ② 旧 palette 覆盖层：随切歌方向滑出 + 淡出，底层新色随之显现
            val prevPal = prevPalette
            if (prevPal != null && prevPal != palette) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -dirSign * bgSlide.value * size.width * 1.1f
                            alpha = (1f - bgSlide.value).coerceIn(0f, 1f)
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    prevPal.surface,
                                    prevPal.background,
                                    prevPal.background.copy(alpha = 1f)
                                )
                            )
                        )
                )
            }
        }

        // 2026-10-29 用户定稿：背景**只取色渲染** —— 取色可用时不再叠加封面模糊图
        // （参考网易云：整页就是封面取出的柔和单色渐变，无照片纹理）。
        // 取色不可用（null）时按「封面 → 壁纸 → 兜底渐变」逐级兜底。
        //
        // ⚠️ 2026-10-29 修「背景有模糊白块」：原写法 `if (palette == null && effectiveUrl != null)`
        // 画封面、**else 一律画壁纸** —— 取色成功但封面候选耗尽（全部加载失败/无封面字段）时
        // 也会掉进 else，把 96px 壁纸放大铺满整屏，壁纸亮部在取色渐变上就是一团"模糊白块"。
        // 现在改为：**palette != null 时只渲染取色渐变**，封面/壁纸兜底仅在取色失败时参与。
        var failedCount by remember(coverCandidates) { mutableIntStateOf(0) }
        val effectiveUrl = coverCandidates.getOrNull(failedCount)

        // 呼吸缩放：只在「播放中 且 未减弱动效 且 页面未被弹窗遮挡」时运行；
        // 其余情况返回常量（暂停"保持当前缩放"的静态近似），彻底不起动画循环。
        val breathScale = backdropBreathScale(animate = isPlaying && !reduceMotion && !paused)

        if (palette == null) {
            if (effectiveUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(effectiveUrl)
                        // 关键：只解码到 SAMPLE_PX —— 既是"静态模糊"，也让内存占用恒定极小
                        .size(PlayerVisualSpec.BACKDROP_SAMPLE_PX)
                        // 切歌交叉淡入（Coil 在同一 AsyncImage 上换 model 时自带淡变）
                        .crossfade(PlayerVisualSpec.BACKDROP_CROSSFADE_MS)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = {
                        // 顺延到下一张候选；越界后自然落到"无封面"分支
                        failedCount++
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = breathScale
                            scaleY = breathScale
                        }
                )
            } else {
                // ② 无封面 → **照片墙（系统壁纸）虚化背景**（2026-10-30 用户定稿恢复）。
                // 此前因「96px 放大出现磨糊白块」被换成纯黑；本次改为**按屏宽自适应采样**
                // （屏宽 ÷ WALLPAPER_SAMPLE_DIVISOR，夹 160–480px）+ 两级降采样近似模糊，
                // 斑块尺度降到肉眼不可辨；壁纸层半透明压在兜底深色渐变之上，
                // 渐变仍从下层透出 = 「壁纸虚化 + 渐变兜底」两层结构。
                // 系统壁纸取不到（TV 无壁纸 / 权限受限）时什么都不画，直接露出渐变。
                val wallpaper = remember(context) { wallpaperBackdropBitmap(context) }
                if (wallpaper != null) {
                    Image(
                        bitmap = wallpaper,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = PlayerVisualSpec.WALLPAPER_BACKDROP_ALPHA
                                scaleX = breathScale
                                scaleY = breathScale
                            }
                    )
                }
                // 壁纸也拿不到 → 不绘制，露出调用方（NowPlayingScreen）的兜底深色渐变
            }
        }

        // 暗色遮罩：竖向渐变。顶部/底部压得更暗以保证顶栏与底栏文字对比度，
        // 中带留到约 50% 仍可透出封面主色氛围（§6.1「叠加从上到下的轻微渐变」）。
        // ⚠️ 取色背景本身已经很暗，此时遮罩整体减半，避免把刚取出来的色相又压死了。
        val scrimFactor = if (palette != null) PlayerVisualSpec.PALETTE_SCRIM_FACTOR else 1f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        *PlayerVisualSpec.BACKDROP_SCRIM_STOPS.map { (stop, alpha) ->
                            stop to PlayerVisualSpec.BACKDROP_SCRIM.copy(alpha = alpha * scrimFactor)
                        }.toTypedArray()
                    )
                )
        )
    }
}

/**
 * 无封面时的「照片墙」底图：取**系统壁纸**，按屏宽自适应降采样后做两级缩放，
 * 近似高斯虚化，再交由 GPU 放大铺满（2026-10-30 用户定稿恢复）。
 *
 * 自适应口径（用户要求"以上修改按屏幕尺寸自适应"）：
 * - 解码边长 = 屏宽 ÷ [PlayerVisualSpec.WALLPAPER_SAMPLE_DIVISOR]，
 *   夹在 [PlayerVisualSpec.WALLPAPER_SAMPLE_MIN_PX]..[WALLPAPER_SAMPLE_MAX_PX]；
 * - 保持壁纸原始宽高比（不裁成方块再被 Crop 二次裁切，避免取到壁纸边角）。
 *
 * 虚化方式：sample → 1/4 → 回 sample 两次双线性插值（等价于低通），
 * 不做逐帧 RenderEffect（禁止事项 10），minSdk 22 全兼容、零合成开销。
 *
 * 降级：TV 无壁纸 / 权限受限时 `runCatching` 兜住 → null → 调用方露出兜底深色渐变。
 * `@SuppressLint("MissingPermission")`：`WallpaperManager.getDrawable` 需要
 * `READ_WALLPAPER_INTERNAL`（@SystemApi，三方应用不可申请）；为一个装饰性底图申请
 * `MANAGE_EXTERNAL_STORAGE` 过重，故权限不足时静默降级（不崩、不提权）。
 */
@SuppressLint("MissingPermission")
private fun wallpaperBackdropBitmap(context: Context): ImageBitmap? = runCatching {
    val screenW = context.resources.displayMetrics.widthPixels
    val sampleW = (screenW / PlayerVisualSpec.WALLPAPER_SAMPLE_DIVISOR)
        .coerceIn(PlayerVisualSpec.WALLPAPER_SAMPLE_MIN_PX, PlayerVisualSpec.WALLPAPER_SAMPLE_MAX_PX)
    val manager = WallpaperManager.getInstance(context)
    val drawable = manager.drawable ?: manager.builtInDrawable ?: return null
    val src = (drawable as? BitmapDrawable)?.bitmap
        ?: drawable.toBitmap(sampleW, sampleW)
    if (src.width <= 0 || src.height <= 0) return null
    val sampleH = (sampleW.toFloat() * src.height / src.width).toInt().coerceAtLeast(8)
    val tiny = Bitmap.createScaledBitmap(
        src,
        (sampleW / 4).coerceAtLeast(8),
        (sampleH / 4).coerceAtLeast(4),
        true
    )
    Bitmap.createScaledBitmap(tiny, sampleW, sampleH, true).asImageBitmap()
}.getOrNull()

/**
 * 背景呼吸缩放：播放中在 1.0 ↔ [PlayerVisualSpec.BACKDROP_BREATH_MAX] 间反向往复。
 *
 * ⚠️ 条件早返回是关键：[animate] 为 false 时**根本不创建** InfiniteTransition
 * （ Compose 允许在 @Composable 中分支返回），返回静态常量 ——
 * 减弱动效 / 暂停 / 弹窗打开时本机不再有任何 tick 在跑，
 * 而不是"跑着一个肉眼不动的动画"（§11-8 / 禁止事项 14）。
 */
@Composable
private fun backdropBreathScale(animate: Boolean): Float {
    if (!animate) return PlayerVisualSpec.BACKDROP_BREATH_REST
    val transition = rememberInfiniteTransition(label = "backdropBreath")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = PlayerVisualSpec.BACKDROP_BREATH_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PlayerVisualSpec.BACKDROP_BREATH_PERIOD_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdropBreathScale"
    )
    return scale
}

/**
 * 播放页背景**兜底渐变**（无封面 / 封面加载失败时使用）。
 *
 * 沿用品牌暗红氛围 → 深净底（与既有背景同源），保证在封面缺失时
 * 页面依旧是"沉浸式深色"而不是一块死黑。
 */
@Composable
fun immersiveBackdropFallbackBrush(): Brush = Brush.verticalGradient(
    listOf(
        NasMusicColors.Background.copy(alpha = 0.9f),
        Color(0xFF1A1012),
        NasMusicColors.Background
    )
)
