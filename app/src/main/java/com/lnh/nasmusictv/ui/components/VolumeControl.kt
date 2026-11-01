package com.lnh.nasmusictv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import kotlin.math.roundToInt

/** 音量单次步进（0–100 的 5%） */
private const val VOLUME_STEP_PCT = 5


/**
 * 应用内音量 OSD 控制条（应用级增益 0.0–1.0，独立于系统音量）。
 *
 * **TV（D-Pad）形态**：整组件是**单一焦点面**，未聚焦时是紧凑 chip（"音量 85%"），
 * 聚焦后**原地展开**成 `− [百分比 + 细进度条] +`，左右键按 5% 步进调节
 * （`onPreviewKeyEvent` 拦截 D-Pad 左右键），失焦自动收起。
 * − / + 是装饰性视觉锚点（提示"这里能减 / 加"），**不是独立焦点**——
 * 这是刻意设计：若它们可聚焦，展开/收起时焦点节点会凭空消失，D-Pad 会卡死。
 * ≥0 / ≤100 边界时按键**不消费**（返回 false），左右导航可自然移出本控件。
 *
 * 不遮挡关键控件：组件是**行内替换**而非悬浮层——只占一行 chip 高度，
 * 不会压住播放控制或歌词。
 *
 * @param volume 当前应用级音量（0.0–1.0，播放层负责实际增益）
 * @param onChangeVolume 新音量回调（始终落在 5% 的整数网格上）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VolumeControl(
    volume: Float,
    onChangeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val percent = (volume * 100).roundToInt()

    // ── TV：单一焦点面。聚焦展开、左右键调节、失焦收起 ──
    FocusableSurface(
        // 电视上点击（OK 键）无操作——左右键才是主交互（见 onPreviewKeyEvent）
        onClick = { },
        onFocusChanged = { expanded = it },
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.DirectionLeft -> {
                        val canDecrease = percent > 0
                        if (canDecrease) onChangeVolume(((percent - VOLUME_STEP_PCT).coerceAtLeast(0) / 100f))
                        canDecrease
                    }
                    Key.DirectionRight -> {
                        val canIncrease = percent < 100
                        if (canIncrease) onChangeVolume(((percent + VOLUME_STEP_PCT).coerceAtMost(100) / 100f))
                        canIncrease
                    }
                    else -> false
                }
            } else false
        },
        shape = RoundedCornerShape(6.dp),
        focusedScale = 1.05f,
        animationDurationMs = 150,
        containerColor = NasMusicColors.Surface.copy(alpha = 0.8f),
        focusedContainerColor = NasMusicColors.FocusOverlay,
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary,
        pressedScale = 0.97f,
        focusBorderColor = NasMusicColors.FocusRing.copy(alpha = 0.7f)
    ) {
        VolumeChipContent(expanded = expanded, percent = percent, volume = volume, hintVisible = true)
    }
}

/** chip 内容：收起 = 单行"音量 N%"；展开 = − [百分比 + 细条] +（+ 可选操作提示） */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VolumeChipContent(
    expanded: Boolean,
    percent: Int,
    volume: Float,
    hintVisible: Boolean,
) {
    if (expanded) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "-",
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.button(),
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                    Text(
                        text = "$percent%",
                        color = NasMusicColors.Primary,
                        fontSize = FontSize.button(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    VolumeFillBar(volume = volume, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                }
                Text(
                    text = "+",
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.button(),
                    fontWeight = FontWeight.Bold
                )
            }
            if (hintVisible) {
                Text(
                    text = stringResource(R.string.nowplaying_volume_hint),
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.small(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    } else {
        Text(
            text = stringResource(R.string.nowplaying_volume) + " $percent%",
            color = NasMusicColors.TextPrimary,
            fontSize = FontSize.small(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}



/** 细进度条：底槽 SurfaceVariant，已填充段 Primary（与设置页模型下载进度条同视觉语言） */
@Composable
private fun VolumeFillBar(volume: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(NasMusicColors.SurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(volume.coerceIn(0f, 1f))
                .height(4.dp)
                .background(NasMusicColors.Primary, RoundedCornerShape(2.dp))
        )
    }
}
