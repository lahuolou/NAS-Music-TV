package com.lnh.nasmusictv.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.LocalUiMode
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.UiMode
/**
 * 公共返回按钮组件
 * 带焦点动画的返回按钮，供各详情屏幕复用
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.08f,
        animationDurationMs = 200,
        containerColor = NasMusicColors.Surface,
        focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.2f),
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.common_back_arrow), color = NasMusicColors.TextPrimary, fontSize = FontSize.button(), modifier = Modifier.padding(end = 6.dp))
            Text(text = stringResource(R.string.common_back), color = NasMusicColors.TextPrimary, fontSize = FontSize.button())
        }
    }
}

/**
 * 公共搜索框组件（统一样式：胶囊形、无独立搜索按钮）
 *
 * 点击整个搜索框触发 [onOpenSearch]（调用方负责弹出输入对话框）；
 * 已有搜索词时框内显示 ✕ 清除按钮，点击触发 [onClear]。
 * 宽度由调用方通过 modifier 指定，高度统一 48dp（**TV 抬到 [TV_TOUCH_TARGET]**，见 §2.7）。
 */
@Composable
fun SearchField(
    query: String,
    placeholder: String,
    onOpenSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableSurface(
        onClick = onOpenSearch,
        modifier = modifier.height(portraitTouchTarget(48.dp)),
        shape = RoundedCornerShape(24.dp),
        containerColor = NasMusicColors.Surface,
        focusedContainerColor = NasMusicColors.FocusOverlay
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = NasMusicColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (query.isBlank()) placeholder else query,
                color = if (query.isBlank()) NasMusicColors.TextSecondary else NasMusicColors.TextPrimary,
                fontSize = FontSize.body(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (query.isNotBlank()) {
                FocusableSurface(
                    onClick = onClear,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("✕", color = NasMusicColors.TextPrimary, fontSize = FontSize.body(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/**
 * 歌曲列表列数：TV 两列、手机单列（一行一个歌曲条目）
 *
 * 始终返回 1 列：歌曲条目信息较多（标题+艺术家+专辑+时长+操作按钮），
 * 两列布局下歌名被压缩不可读。统一用一列让 SongRow 占满整行。
 *
 * 注意："我的"页面不使用此函数，其 TV/手机版式差异保持不变。
 */
@Composable
fun songGridColumns(): GridCells = GridCells.Fixed(1)

/**
 * 响应式网格列数 —— **纯函数**（可 JVM 单测，不依赖 Compose 运行时）。
 *
 * 阈值口径沿用项目既有约定：
 * - `>= 1000dp`：TV / 大屏 → [tv]
 * - `600..999dp`：手机横屏 / 小平板（`medium`）→ [medium]
 * - `< 600dp`：手机竖屏 → [phonePortrait]
 *
 * ⚠️ 参数语义：第三参原名为 `phoneLandscape`，但它由 `widthDp >= 600` 触发，
 * **平板竖屏（sw >= 600）也会落进这一支**，故改名为 `medium`（方案 §3.4）。
 */
fun adaptiveColumnsOf(widthDp: Int, tv: Int, phonePortrait: Int, medium: Int): Int = when {
    widthDp >= 1000 -> tv
    widthDp >= 600 -> medium
    else -> phonePortrait
}

/**
 * 响应式网格列数（由 `ui/screens/library/browse/BrowseComponents.kt` 上移，
 * 供全项目复用；与 [songGridColumns] 并列）。
 *
 * 按 `LocalConfiguration.screenWidthDp`（未缩放 Android dp）分档：
 * ≥1000 → [tv]，≥600 → [medium]，其余 → [phonePortrait]。
 * （竖屏优先分支已随竖屏下线移除。）
 */
@Composable
fun adaptiveColumns(tv: Int, phonePortrait: Int, medium: Int = phonePortrait): Int {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return adaptiveColumnsOf(widthDp, tv, phonePortrait, medium)
}

/**
 * 触摸目标下限（**Compose dp 口径**，方案 §2.7 第 1 条 / P0-26）。
 *
 * TV / 横屏下物理 dp = Compose dp（无 0.82 缩放），故热区下限直接用 **56dp**
 * 即可满足 Android 无障碍 44 物理 dp 下限：`56 Compose dp ≥ 44 物理 dp` ✅。
 *
 * ⚠️ 所有控件热区不得低于 56dp（TV 遥控器远焦 + 大屏可读性要求）。
 * 新增控件请用 [portraitTouchTarget]（竖屏已下线，恒返回入参）。
 *
 * 保留为 `Float` 常量是为了让 [com.lnh.nasmusictv.ui.theme.UiModeTest] 能在纯 JVM 下断言该算术。
 */
const val TV_TOUCH_TARGET_DP: Float = 56f

/** [TV_TOUCH_TARGET_DP] 的 `Dp` 形式 */
val TV_TOUCH_TARGET: Dp = TV_TOUCH_TARGET_DP.dp

/**
 * 触摸目标尺寸（竖屏，方案 §2.7 / P0-26；竖屏已下线）。
 *
 * 竖屏下线后恒返回 [landscape] —— 与竖屏移除前 TV / 手机横屏的取值**逐字等价**（B1）。
 * 函数与 [TV_TOUCH_TARGET] 保留：`TV_TOUCH_TARGET_DP` 的 56dp 算术仍由
 * `UiModeTest` 护栏断言，全部调用点无需改动。
 *
 * 用法：`Modifier.size(portraitTouchTarget(48.dp))` / `Modifier.height(portraitTouchTarget(44.dp))`
 */
@Composable
fun portraitTouchTarget(landscape: Dp): Dp = landscape

/**
 * 对话框 / 弹层的响应式尺寸（竖屏，方案 §2.4 对话框族 / P1-27；竖屏已下线）。
 *
 * 竖屏下线后恒返回 `width(landscapeWidth)` —— 与竖屏移除前 TV / 手机横屏的取值
 * **逐字等价**（B1）。`scrollable` 参数随竖屏滚动对话框一并移除。
 */
@Composable
fun responsiveDialogSize(landscapeWidth: Dp): Modifier = Modifier.width(landscapeWidth)
