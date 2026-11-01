package com.lnh.nasmusictv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** 全程连续循环跑马灯：两份文本之间的间隔（决定循环周期长度与视觉衔接距离）。 */
private val MARQUEE_LOOP_GAP = 64.dp

/**
 * 横向滚动字幕（；2026-10-30 泛化支持 [AnnotatedString]）。
 *
 * 文本宽度不超过容器时静态居中显示；超出时**全程连续循环**滚动：
 * 渲染两份文本 + 间隔，整体右→左匀速平移，滚完一个「文本宽 + 间隔」后瞬回起点 ——
 * 因第二份文本此刻恰好落在起点位置，回跳帧与上一帧完全一致，视觉上无缝衔接，
 * **中间没有任何停顿**（2026-10-30 用户定稿：不要"停 3 秒再滚"的有序循环）。
 * 用于播放页黑胶下方的专辑名/标题等长文本，解决"截断难看"问题。
 *
 * 依赖 Compose 1.6+ 的 [rememberTextMeasurer]；本组件只渲染单行。
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 16.sp,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Center,
    fontWeight: FontWeight? = null,
    // 滚动速度（dp/秒）
    speedDpPerSec: Float = 42f,
) = MarqueeText(
    text = AnnotatedString(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    maxLines = maxLines,
    textAlign = textAlign,
    fontWeight = fontWeight,
    speedDpPerSec = speedDpPerSec,
)

/** [AnnotatedString] 版本：支持行内多段样式（如「标题 - 歌手」双色行）。 */
@Composable
fun MarqueeText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 16.sp,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Center,
    fontWeight: FontWeight? = null,
    speedDpPerSec: Float = 42f,
) {
    val scroll = remember { Animatable(0f) }
    var isOverflow by remember { mutableStateOf(false) }
    var overflowPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val speedPxPerSec = with(density) { speedDpPerSec.dp.toPx() }

    // 用 TextMeasurer 测出单行文本实际像素宽（不依赖布局约束循环）。
    // ⚠️ 测量样式必须与渲染样式一致（fontSize + fontWeight）—— 否则实测宽度偏窄，
    // 会误判"放得下"从而把字裁掉（"标题被截断"的经典成因）。
    val textMeasurer = rememberTextMeasurer()
    val measured = remember(text, fontSize, maxLines, fontWeight) {
        textMeasurer.measure(
            text = text,
            style = TextStyle(fontSize = fontSize, fontWeight = fontWeight ?: FontWeight.Normal),
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            softWrap = false
        )
    }
    val textWidthPx = measured.size.width

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                isOverflow = textWidthPx > size.width
                overflowPx = (textWidthPx - size.width).coerceAtLeast(0)
            }
    ) {
        if (isOverflow && overflowPx > 0) {
            // ⚠️ 2026-10-28 用户定稿：**只有文本过长（放不下）时才滚动** ——
            // 短文本一律静态显示（此前"无条件滚动"的字幕模式已移除，
            // 短标题来回空转反而干扰视线）。
            //
            // ⚠️ 2026-10-30 最终定稿：**全程连续循环（无缝跑马灯）** ——
            // 渲染两份文本 + 间隔，整体向左匀速平移一个「文本宽 + 间隔」后瞬回起点；
            // 第二份文本此刻恰好落在起点 → 回跳帧与上一帧完全一致 → 视觉无缝，
            // 全程无任何停顿（"有序循环 + 停 3 秒"版本被用户否决）。
            val gapPx = with(density) { MARQUEE_LOOP_GAP.toPx() }
            val loopPx = textWidthPx + gapPx
            LaunchedEffect(loopPx, text) {
                val loopDurMs = (loopPx / speedPxPerSec * 1000).toInt().coerceAtLeast(300)
                while (true) {
                    scroll.snapTo(0f)
                    scroll.animateTo(
                        -loopPx,
                        animationSpec = tween(durationMillis = loopDurMs, easing = LinearEasing)
                    )
                }
            }
            Row(
                // ⚠️ 必须 **unbounded** 测量：默认 Row 会按父约束（容器宽）逐个收缩子项 ——
                // 第一份文本被压成容器宽、第二份直接拿到 0 宽，滚到后面就是一片空白
                //（表现为"被截断 + 空档"）。unbounded 让子项按真实文本宽度排版，
                // 溢出部分由外层 Box 的 clipToBounds 裁掉，动画才是完整的无缝循环。
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = scroll.value }
            ) {
                Text(
                    text = text,
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Start,
                    maxLines = maxLines,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.width(MARQUEE_LOOP_GAP))
                Text(
                    text = text,
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Start,
                    maxLines = maxLines,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        } else {
            // 未溢出：**静态显示**（放得下 → 完整显示，绝不加省略号）
            //
            // ⚠️ 必须用 BoxScope.align 而不是给 Text 套 fillMaxWidth：
            // Text 自身宽度 = 文本宽度，textAlign 才会有"可分配的剩余空间"可生效；
            // 套 fillMaxWidth 会改变本组件在其他调用点（Row 内标题等）的占位行为。
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
                modifier = Modifier.align(
                    when (textAlign) {
                        TextAlign.Center -> Alignment.Center
                        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }
                )
            )
        }
    }
}
