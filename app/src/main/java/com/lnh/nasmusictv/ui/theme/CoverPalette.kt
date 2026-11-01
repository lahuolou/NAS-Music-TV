package com.lnh.nasmusictv.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 播放页**封面智能取色**（，网易云式「主题色 / 背景色」渲染方案）。
 *
 * ## 做什么
 * 把封面解码成一张小位图交给 [Palette] 量化，得到一组语义色：
 * 主色 / 背景色 / 中段色 / 强调色，再由 [CoverPalette] 统一提供给
 * 页面背景（渐变）、黑胶光晕、歌词高亮等消费点 —— 让整页的色调跟着封面走，
 * 而不是永远一套固定的品牌深色。
 *
 * ## 为什么必须先压暗
 * 播放页是**暗色沉浸态**（最高优先级 A1/A2）：直接把封面主色铺满屏幕会让
 * 白底大色块变成刺眼的亮屏，顶栏文字对比度也会崩。因此 [CoverPalette.background]
 * 与 [CoverPalette.surface] 一律向近黑方向混合（[mixTo]），只保留"色相氛围"不保留"亮度"。
 *
 * ## 为什么有缓存
 * 取色要解码一次位图（≈ 几十 ms），而播放页重组非常频繁（进度每帧刷新）。
 * 不做缓存会让每次重组都重跑一次解码 —— 直接把 UI 线程相邻的 I/O 变成持续开销。
 * 缓存按封面 URL 记，容量小（24 首），命中后零成本。
 *
 * ## 降级
 * 任一步失败（无 URL / 解码失败 / Palette 取不到 swatch）都返回 `null`，
 * 消费点回退到既有的固定深色方案，不会出现"半套色"导致的怪异画面。
 */
object CoverPaletteExtractor {

    /** 缓存上限（约 24 首歌的量，够覆盖一次听歌 session，又不占内存） */
    private const val MAX_CACHE = 24

    /** 取色用的解码边长：越小越快，96px 对量化主色完全够用 */
    private const val SAMPLE_PX = 96

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CoverPalette>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverPalette>) =
                size > MAX_CACHE
        }
    )

    /** 调试/设置页清缓存时一并清掉 */
    fun clearCache() = cache.clear()

    /**
     * 按封面 URL 取（或命中缓存返回）一套配色。`null` = 无法取色，调用方请回退默认方案。
     *
     * 必须在后台线程语义下调用 —— 内部已切 [Dispatchers.Default]（解码 + Palette 量化）。
     */
    suspend fun extract(context: Context, url: String?): CoverPalette? = withContext(Dispatchers.Default) {
        val key = url?.takeIf { it.isNotBlank() } ?: return@withContext null
        cache[key]?.let { return@withContext it }

        val request = ImageRequest.Builder(context)
            .data(key)
            .size(SAMPLE_PX)
            // Palette 需要可读像素：硬件位图（Bitmap.Config.HARDWARE）不可读，必须关掉
            .allowHardware(false)
            .build()
        val bitmap = runCatching {
            val drawable = Coil.imageLoader(context).execute(request).drawable
            (drawable as? BitmapDrawable)?.bitmap
        }.getOrNull() ?: return@withContext null

        val palette = buildFrom(bitmap) ?: return@withContext null
        cache[key] = palette
        palette
    }

    private fun buildFrom(bitmap: Bitmap): CoverPalette? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val p = runCatching {
            Palette.from(bitmap).maximumColorCount(24).generate()
        }.getOrNull() ?: return null

        // dominantSwatch 是"整图占比最高"的色，作为主色最稳
        val dominant = p.dominantSwatch ?: return null
        // 强调色优先活泼色（用于光晕 / 高亮），取不到就退回主色；
        // 2026-10-30：vibrant 常常偏暗（深色封面），加 lightVibrant 亮度兜底
        val accentSwatch = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: dominant
        // 背景：优先暗而柔和的色（暗色沉浸态要的就是"沉下去"的那支）；
        // muted 比 darkMuted 略亮，深色封面时能少压一档（2026-10-30 顺序对调）
        val backdropSwatch = p.mutedSwatch ?: p.darkMutedSwatch ?: dominant

        return CoverPalette(
            dominant = Color(dominant.rgb),
            // 压暗程度递进：背景最暗、中段略亮，形成自上而下的层次。
            // 2026-10-30 用户反馈「背景取色太暗」：混黑量 0.66/0.42 → 0.55/0.34。
            // 2026-10-30 再定稿「背景太亮，适当再暗一个维度」：0.55/0.34 → 0.62/0.40
            //（比最初的全暗略亮、比上一轮明显沉一档）；亮度下限同步收窄，
            // 最暗端不再被 liftLuma 提回那么亮。
            background = liftLuma(
                Color(backdropSwatch.rgb).mixTo(NEAR_BLACK, 0.62f),
                minLuma = 0.035f
            ),
            surface = liftLuma(
                Color(backdropSwatch.rgb).mixTo(NEAR_BLACK, 0.40f),
                minLuma = 0.07f
            ),
            accent = liftLuma(
                Color(accentSwatch.rgb),
                minLuma = 0.22f,
                maxLuma = 0.90f
            ),
            // 强调色上的文字色：直接取 Palette 给的 bodyTextColor（它按对比度算过）
            onAccent = Color(accentSwatch.bodyTextColor)
        )
    }

    /**
     * 亮度下限/上限（相对亮度，0–1）：低于 [minLuma] 时按通道等比提亮（保留色相），
     * 高于 [maxLuma] 时等比压暗。取色「最暗端不熄灭、最亮端不过曝」的统一收口。
     */
    private fun liftLuma(c: Color, minLuma: Float, maxLuma: Float = 1f): Color {
        val l = c.luminance()
        val k = when {
            l in minLuma..maxLuma || l <= 0f -> 1f
            l < minLuma -> (minLuma / l).coerceAtMost(2.5f)
            else -> (maxLuma / l).coerceAtMost(1f)
        }
        return if (k == 1f) c else Color(
            red = (c.red * k).coerceIn(0f, 1f),
            green = (c.green * k).coerceIn(0f, 1f),
            blue = (c.blue * k).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /** 统一的"压暗目标"：不是纯黑，留一点蓝紫让暗部不死板（与 [PlayerVisualSpec.BACKDROP_SCRIM] 同源） */
    private val NEAR_BLACK = Color(0xFF07070A)
}

/**
 * 一套从封面取出来的播放页配色。
 *
 * @param dominant 封面主色（原始亮度，供需要"保留本色"的消费点使用）
 * @param background 页面背景色（已压暗，供大面积铺底）
 * @param surface 背景渐变的中段色（略亮于 [background]，制造纵深）
 * @param accent 强调色（黑胶光晕 / 高亮等需要"跳出来"的地方）
 * @param onAccent 压在 [accent] 之上的前景色（由 Palette 按对比度给出）
 */
data class CoverPalette(
    val dominant: Color,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val onAccent: Color,
)

/**
 * 把颜色向 [target] 方向混合 [amount]（0 = 原色，1 = 完全变成 target）。
 *
 * 用 sRGB 通道直接线性插值：这里的目标只是"压暗 + 保留色相"，
 * 不需要色彩空间精确转换，线性插值足够且零依赖。
 */
private fun Color.mixTo(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha + (target.alpha - alpha) * t
    )
}
