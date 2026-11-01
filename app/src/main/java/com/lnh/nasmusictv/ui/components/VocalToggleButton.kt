package com.lnh.nasmusictv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.lnh.nasmusictv.ui.theme.FontSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.lnh.nasmusictv.ui.theme.NasMusicColors
/**
 * 原唱/伴奏/K歌/MTV 切换按钮
 *
 * 触摸与遥控器双兼容（FocusableSurface）：手机直接点击，TV D-Pad 聚焦 + OK 键。
 *
 * @param label 按钮文字（入口按钮传 "K歌"/"MTV"/"歌词"，K 歌页内根据当前模式传 "原唱"/"伴唱"）
 * @param onClick 点击回调
 * @param compact 紧凑模式（NowPlayingScreen 控制栏用 true，KARAOKE/MTV 全屏页用 false）
 * @param dimmed 半透明禁用态（如 MTV 无可用视频时）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VocalToggleButton(
    label: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    dimmed: Boolean = false,
    width: Dp? = null
) {
    // 紧凑模式高度必须与 IconButton 一致（IconButton compact = 40dp），
    // 否则同一行图标按钮 40dp 、文字按钮 48dp 会明显不齐
    val buttonSize = if (compact) 40.dp else 72.dp
    val effectiveWidth = width ?: buttonSize
    // 改用与「词」按钮同源的「透明底 + 描边」语言。
    // 旧写法 containerColor = Surface(#17191F) 在暗色背景上是一块无边界的深色实心块，
    // 视觉上融成"黑斑"（用户反馈的"进度条大黑点"实为 K歌/MTV 按钮）。
    // 现在透明底 + 常驻灰描边，按钮轮廓清晰可辨，聚焦时叠暗红焦点环 + 16% 红底提亮。
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(effectiveWidth)
            .height(buttonSize)
            .alpha(if (dimmed) 0.35f else 1f),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.12f,
        animationDurationMs = 250,
        containerColor = Color.Transparent,
        focusedContainerColor = NasMusicColors.FocusOverlay,
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.TextPrimary,
        pressedContainerColor = NasMusicColors.FocusOverlay,
        pressedContentColor = NasMusicColors.TextPrimary,
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
                text = label,
                fontSize = FontSize.small(),
                fontWeight = FontWeight.Bold,
                color = NasMusicColors.TextPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}