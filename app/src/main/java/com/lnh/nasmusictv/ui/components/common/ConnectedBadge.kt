package com.lnh.nasmusictv.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors

/**
 * 「已连接」状态徽章（绿点 + 文字）。
 *
 * 用于 logo / 标题区域标注当前后端连接状态：
 * - 顶栏品牌区（[com.lnh.nasmusictv.ui.components.AppRoot] 的 TvTopNavBar）
 * - 首页欢迎区（[com.lnh.nasmusictv.ui.screens.HomeScreen] 的 WelcomeSection）
 *
 * 未连接时不渲染 —— 调用方自行决定"未连接"的呈现文案。
 *
 * @param modifier 额外 Modifier
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConnectedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NasMusicColors.Success.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(NasMusicColors.Success, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.server_connected_badge),
            color = NasMusicColors.Success,
            fontSize = FontSize.caption()
        )
    }
}
