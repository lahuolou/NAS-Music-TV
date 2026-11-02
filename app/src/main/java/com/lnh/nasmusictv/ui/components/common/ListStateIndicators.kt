package com.lnh.nasmusictv.ui.components.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.ui.components.FocusableSurface
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors
/**
 * 居中加载动画 + 可选文字
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingIndicator(
    text: String? = null,
    modifier: Modifier = Modifier
) {
    val resolvedText = text ?: stringResource(R.string.list_loading)
    // 2026-10-30：Canvas 画缺口圆弧（标准 spinner），匀速旋转 —— 不再转字符「⟳」（字形偏心看起来乱转）
    val infinite = rememberInfiniteTransition(label = "loadingSpin")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier.size(36.dp)
            ) {
                val strokeWidth = 3.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = androidx.compose.ui.geometry.Offset(
                    x = (size.width - diameter) / 2f,
                    y = (size.height - diameter) / 2f
                )
                drawArc(
                    color = NasMusicColors.Primary,
                    startAngle = rotation - 90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = resolvedText,
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.button()
            )
        }
    }
}

/**
 * 错误信息 + 重试按钮
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorDisplay(
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val resolvedMessage = message ?: stringResource(R.string.list_load_failed)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(NasMusicColors.SurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = NasMusicColors.Warning,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = resolvedMessage,
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.button(),
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                FocusableSurface(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    focusedScale = 1.08f,
                    animationDurationMs = 150,
                    containerColor = NasMusicColors.PrimaryFill,
                    focusedContainerColor = NasMusicColors.Primary,
                    contentColor = NasMusicColors.TextPrimary,
                    focusedContentColor = NasMusicColors.TextPrimary
                ) {
                    Text(
                        text = stringResource(R.string.list_retry),
                        color = NasMusicColors.TextPrimary,
                        fontSize = FontSize.button(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 空状态图标 + 提示文字 + 可选操作按钮
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EmptyState(
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val resolvedMessage = message ?: stringResource(R.string.list_empty)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(NasMusicColors.SurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = NasMusicColors.TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = resolvedMessage,
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.button(),
                textAlign = TextAlign.Center
            )
            if (actionText != null && onAction != null) {
                FocusableSurface(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    focusedScale = 1.08f,
                    animationDurationMs = 150,
                    containerColor = NasMusicColors.Surface.copy(alpha = 0.7f),
                    focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.3f),
                    contentColor = NasMusicColors.TextPrimary,
                    focusedContentColor = NasMusicColors.Primary
                ) {
                    Text(
                        text = actionText,
                        color = NasMusicColors.TextPrimary,
                        fontSize = FontSize.button(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
