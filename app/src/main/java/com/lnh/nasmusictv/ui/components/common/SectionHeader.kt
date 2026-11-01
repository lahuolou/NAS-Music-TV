package com.lnh.nasmusictv.ui.components.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.ui.components.FocusableSurface
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors

/**
 * 统一节标题组件（全应用唯一实现 —— 之前 HomeScreen 里还有一份私有副本，已合并至此）。
 *
 * 显示标题 + 计数 + 可选右侧操作按钮（自定义文案优先，否则显示"查看全部"）。
 *
 * @param title 标题文字
 * @param count 数量（显示在标题后括号中）
 * @param onViewAll 点击"查看全部"回调（[actionLabel] 为空时生效；null 不显示按钮）
 * @param actionLabel 自定义右侧操作按钮文案（如"换一批"）；设置后优先于 onViewAll 渲染
 * @param onAction 自定义操作按钮回调（须与 [actionLabel] 同时提供）
 * @param modifier 额外 Modifier
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SectionHeader(
    title: String,
    count: Int,
    onViewAll: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = NasMusicColors.TextPrimary,
            fontSize = FontSize.subtitle(),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = " ($count)",
            color = NasMusicColors.TextSecondary,
            fontSize = FontSize.button()
        )
        Spacer(modifier = Modifier.weight(1f))
        // 自定义文案优先（如"换一批"）；否则退化为通用的"查看全部 >"
        val actionText = actionLabel ?: (stringResource(R.string.section_view_all) + " >")
        val actionClick = if (actionLabel != null) onAction else onViewAll
        if (actionClick != null) {
            FocusableSurface(
                onClick = actionClick,
                shape = RoundedCornerShape(6.dp),
                focusedScale = 1.08f,
                animationDurationMs = 150,
                containerColor = Color.Transparent,
                focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.2f),
                contentColor = NasMusicColors.Primary,
                focusedContentColor = NasMusicColors.Primary
            ) {
                Text(
                    text = actionText,
                    color = NasMusicColors.Primary,
                    fontSize = FontSize.body(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
