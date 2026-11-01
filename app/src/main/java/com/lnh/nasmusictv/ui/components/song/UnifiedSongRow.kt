package com.lnh.nasmusictv.ui.components.song

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.backend.download.model.DownloadState
import com.lnh.nasmusictv.backend.download.model.isDownloadableSong
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.ui.components.ConfirmDialog
import com.lnh.nasmusictv.ui.components.MarqueeText
import androidx.compose.ui.text.style.TextAlign
import com.lnh.nasmusictv.ui.components.common.CoverImage
import com.lnh.nasmusictv.ui.components.common.SourceBadge
import com.lnh.nasmusictv.ui.components.focusRing
import com.lnh.nasmusictv.ui.components.portraitTouchTarget
import com.lnh.nasmusictv.ui.components.shouldShowFocusVisuals
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.LocalUiMode
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.UiMode
import com.lnh.nasmusictv.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
/**
 * 当前正在播放（已选中）的歌曲 id —— 全应用唯一，由 AppRoot 提供。
 *
 * [UnifiedSongRow] 据此把「正在播放 / 已选中」的歌曲渲染成**持久高亮**
 * （♪ 标记 + Primary 文字 + 独立底色/描边），与焦点态视觉区分：
 * - 焦点 = "光标停在哪"（瞬态，切走即消失）；
 * - 选中 = "用户实际播放的是哪首"（跨列表常驻，切走不丢）。
 *
 * 这正对真机反馈「选中后切走就丢失、和焦点态混淆」：此前歌曲列表只有焦点高亮，
 * 焦点一移开就什么都不剩，且队列页里"正在播放行"与"焦点行"用同一底色无从区分。
 *
 * ⚠️ 用 `staticCompositionLocalOf`：切歌频率低（几分钟一次），只让真正读它的行重组，
 * 不会像进度流那样驱动整棵列表树高频重组（与 H-3 / M7 的"高频流下沉"同理）。
 */
val LocalCurrentSongId = staticCompositionLocalOf<String?> { null }

/**
 * Resolve a local/downloaded track's embedded cover without touching storage during composition.
 * `remember {}` only memoizes a result; it does not move synchronous file I/O off the main thread.
 * The producer is cancelled when a lazy-list row is reused for another song or leaves composition.
 */
@Composable
private fun rememberEffectiveCoverUrl(song: Song, downloadState: DownloadState): String? {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val coverUrl by produceState(
        song.coverUrl,
        song.id,
        song.path,
        song.coverUrl,
        downloadState,
        appContext
    ) {
        value = withContext(Dispatchers.IO) {
            val completed = downloadState as? DownloadState.Completed
            val audioPath = completed?.path?.takeIf { it.isNotBlank() }
                ?: song.path?.takeIf { song.isLocalSong && it.isNotBlank() }
            if (audioPath.isNullOrBlank()) {
                song.coverUrl
            } else {
                val embedded = com.lnh.nasmusictv.backend.local.EmbeddedCoverExtractor.extractCoverUri(
                    audioPath,
                    appContext.cacheDir,
                    appContext
                )
                embedded
                    ?: completed?.coverPath?.takeIf { path ->
                        path.isNotBlank() && java.io.File(path).isFile
                    }?.let { path -> "file://$path" }
                    ?: song.coverUrl
            }
        }
    }
    return coverUrl
}

/**
 * 歌曲行布局模式
 */
enum class SongRowMode {
    /** 水平行：封面 + 文字 + 操作，用于列表 */
    MODE_ROW,
    /** 卡片：封面在上，文字在下，用于网格 */
    MODE_CARD,
    /** 紧凑行：无封面，仅文字，用于队列 */
    MODE_COMPACT
}

/**
 * 导入歌单歌曲的 URL 直链状态（docs/archive/playlist-import-feature-plan.md §4.5.2）。
 * 仅 MODE_ROW 在来源标签位渲染；非导入场景保持 NONE（不渲染）。
 */
enum class UrlStatus {
    /** 非导入歌曲 / 无 URL 直链 */
    NONE,
    /** 已捕获 URL 直链，待后台可达性测试（导入完成瞬间的短暂状态） */
    PENDING,
    /** 不可达（24h 判定窗口内，见 AppPreferences.songReachability） */
    UNREACHABLE,
}

/**
 * 统一歌曲行组件
 *
 * 替代 HomeScreen.HomeSongCard 等重复实现。
 * 支持三种布局模式，通过参数控制显示内容。
 *
 * @param song 歌曲数据
 * @param onClick 点击回调（播放歌曲）
 * @param mode 布局模式
 * @param index 列表序号（从 0 开始，显示为 index+1）。null 时显示播放图标
 * @param isFavorited 是否已收藏
 * @param onToggleFavorite 收藏切换回调（null 时不显示收藏按钮）
 * @param isInQueue 是否在播放队列中
 * @param onToggleQueue 队列切换回调（null 时不显示队列按钮）
 * @param onAddToPlaylist 添加到歌单回调（null 时不显示按钮）
 * @param onDelete 删除回调（null 时不显示按钮）。仅用于"可删除"上下文
 *                   （如歌单内移除歌曲），搜索/发现/曲库页不应传入
 * @param downloadState 下载状态（null 默认 None，不显示下载按钮）
 * @param onDownload 下载/删除下载回调（null 时不显示下载按钮）
 * @param onDeleteDownload 删除已下载文件回调（null 时 Completed 状态点击不响应）
 * @param focusRequester 焦点请求器
 * @param isStub 是否导入歌单的裸条目（id 以 imported_ 开头；true 时来源标签显示「待补全」）
 * @param urlStatus URL 直链可达性状态（§4.5.2；UNREACHABLE 时优先显示「URL 失效」）
 * @param possiblyUnplayable 该曲在艺人全量列表里带 `privilege.st != 0`（**仅打标、不隐藏**，Q1 的 b）。
 *                   默认 false；普通搜索分支与端点兜底分支**没有这个字段**，必须保持 false（§2 C.1）——
 *                   "没角标"不等于"能播"，UI 侧统一以缺省即不显示处理。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UnifiedSongRow(
    song: Song,
    onClick: () -> Unit,
    mode: SongRowMode = SongRowMode.MODE_ROW,
    index: Int? = null,
    isFavorited: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isInQueue: Boolean = false,
    onToggleQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    downloadState: DownloadState = DownloadState.None,
    onDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    isStub: Boolean = false,
    urlStatus: UrlStatus = UrlStatus.NONE,
    possiblyUnplayable: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 当前播放/已选中：全应用由 AppRoot 提供 LocalCurrentSongId（见其上 KDoc）。
    // 按 song.id 匹配（跨列表常驻），切歌时只有正在渲染该行/该行的列表重组。
    val isCurrent = song.id == LocalCurrentSongId.current
    when (mode) {
        SongRowMode.MODE_ROW -> SongRowModeRow(
            song = song,
            onClick = onClick,
            index = index,
            isFavorited = isFavorited,
            onToggleFavorite = onToggleFavorite,
            isInQueue = isInQueue,
            onToggleQueue = onToggleQueue,
            onAddToPlaylist = onAddToPlaylist,
            onDelete = onDelete,
            downloadState = downloadState,
            onDownload = onDownload,
            onDeleteDownload = onDeleteDownload,
            focusRequester = focusRequester,
            isStub = isStub,
            urlStatus = urlStatus,
            possiblyUnplayable = possiblyUnplayable,
            isCurrent = isCurrent,
            modifier = modifier
        )
        SongRowMode.MODE_CARD -> SongRowModeCard(
            song = song,
            onClick = onClick,
            downloadState = downloadState,
            focusRequester = focusRequester,
            isCurrent = isCurrent
        )
        SongRowMode.MODE_COMPACT -> SongRowModeCompact(
            song = song,
            onClick = onClick,
            index = index,
            focusRequester = focusRequester,
            isCurrent = isCurrent
        )
    }
}

/**
 * MODE_ROW：水平行布局（封面 + 文字 + 操作按钮）
 * 与现有 LibraryScreen.SongRow 样式一致
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongRowModeRow(
    song: Song,
    onClick: () -> Unit,
    index: Int?,
    isFavorited: Boolean,
    onToggleFavorite: (() -> Unit)?,
    isInQueue: Boolean,
    onToggleQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    downloadState: DownloadState,
    onDownload: (() -> Unit)?,
    onDeleteDownload: (() -> Unit)?,
    focusRequester: FocusRequester?,
    isStub: Boolean = false,
    urlStatus: UrlStatus = UrlStatus.NONE,
    possiblyUnplayable: Boolean = false,
    isCurrent: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isRowFocused by remember { mutableStateOf(false) }
    val animScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // P0-12: 未下载过的可下载歌曲 fallback 到 Idle 而非 None，使下载按钮可见
    val effectiveDownloadState = if (downloadState is DownloadState.None && isDownloadableSong(song)) {
        DownloadState.Idle
    } else {
        downloadState
    }

    // P1-16: Completed 状态点击弹出删除确认而非死按钮
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 竖屏体验修复：焦点视觉只在**用按键导航**的设备上生效。
    // 手机触摸会让 `clickable`/`focusable` 节点获得焦点且**焦点粘住** → 行背景永久高亮 +
    // 永久放大 2%。与 FocusableSurface 的同款处理保持一致。
    // ⚠️ 判据走 `shouldShowFocusVisuals()`（无触摸屏 / 电视 feature / 已用过方向键）。
    val tvDevice = shouldShowFocusVisuals()
    val activeFocus = isRowFocused && tvDevice

    // 时长与操作按钮作为行内右侧第二列（竖屏独立操作行已随竖屏下线移除）。
    val actionButtons: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 下载按钮（None 不渲染；Idle ⬇ 下载 / Queued ⋯ / Downloading ⇣ / Completed ✓ 删除 / Failed ✕ 重试）
            if (onDownload != null && effectiveDownloadState !is DownloadState.None) {
                val button: Triple<String, Color, Boolean>? = when (effectiveDownloadState) {
                    DownloadState.Idle -> Triple("⬇", NasMusicColors.TextPrimary, true)
                    DownloadState.Queued -> Triple("⋯", NasMusicColors.TextSecondary, false)
                    is DownloadState.Downloading -> Triple("⇣", NasMusicColors.Primary, false)
                    // Completed 可点击 → 弹出「删除已下载文件」二次确认（§8.7.1）
                    is DownloadState.Completed -> Triple("✓", NasMusicColors.Success, true)
                    is DownloadState.Failed -> Triple("✕", NasMusicColors.Warning, true)
                    DownloadState.None -> null
                }
                if (button != null) {
                    RowActionButton(
                        text = button.first,
                        color = button.second,
                        onClick = {
                            if (effectiveDownloadState is DownloadState.Completed && onDeleteDownload != null) {
                                showDeleteConfirm = true
                            } else {
                                onDownload.invoke()
                            }
                        },
                        enabled = button.third
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            if (onToggleFavorite != null) {
                RowActionButton(
                    text = if (isFavorited) "♥" else "♡",
                    color = if (isFavorited) NasMusicColors.Warning else NasMusicColors.TextPrimary,
                    onClick = onToggleFavorite
                )
            }
            if (onToggleQueue != null) {
                Spacer(modifier = Modifier.width(10.dp))
                RowActionButton(
                    text = if (isInQueue) "✓" else "☰",
                    color = if (isInQueue) NasMusicColors.Primary else NasMusicColors.TextPrimary,
                    onClick = onToggleQueue
                )
            }
            if (onAddToPlaylist != null) {
                Spacer(modifier = Modifier.width(10.dp))
                RowActionButton(
                    text = "+",
                    color = NasMusicColors.TextPrimary,
                    onClick = onAddToPlaylist
                )
            }
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(10.dp))
                RowActionButton(
                    text = "✕",
                    color = NasMusicColors.Warning,
                    onClick = onDelete
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(animScale.value)
            .clip(RoundedCornerShape(6.dp))
            .background(
                // 选中（当前播放）与焦点拆成两套底色：焦点 0.20f（瞬态，切走即无）、
                // 选中 0.10f（常驻，切走仍在）。焦点行压过选中行 ⇒ 聚焦"正在播放"时
                // 底色加深，配合 ♪/Primary 文字仍能区分"播放中"与"光标在这"。
                color = when {
                    activeFocus -> NasMusicColors.Primary.copy(alpha = 0.2f)
                    isCurrent -> NasMusicColors.FocusOverlay
                    else -> NasMusicColors.Surface.copy(alpha = 0.5f)
                }
            )
            .onFocusChanged { state ->
                isRowFocused = state.hasFocus
                scope.launch {
                    animScale.animateTo(
                        // ⚠️ 用 `state.hasFocus` 而不是外层的 activeFocus：
                        // onFocusChanged 的 lambda 捕获的是**本次组合**的 activeFocus，
                        // 焦点刚变化时它还是旧值（与 tvDevice 分档叠加会读错）。
                        if (state.hasFocus && tvDevice) 1.02f else 1f,
                        tween(200)
                    )
                }
            }
            // 焦点环挂在链尾 ⇒ 画在背景与文字之上（见 FocusIndicator.kt 的绘制顺序约定）
            .focusRing(
                shape = RoundedCornerShape(6.dp),
                color = NasMusicColors.FocusRing,
                visible = activeFocus,
            )
    ) {
        // 竖屏体验修复：外层用 Column 承载「第二行（时长 + 操作按钮）」。
        // 非竖屏下 Column 只有原 Row 一个子项 → 渲染结果与改动前逐字等价（B1）。
        Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：封面 + 序号/图标 + 文字（可点击播放）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
                    .focusable()
                    .clickable { onClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 已下载/本地音频的 APIC 提取通过 produceState + Dispatchers.IO 异步执行；
                // 首帧先显示服务端/MediaStore 封面，提取完成后再替换，不阻塞 TV 遥控滚动。
                val effectiveCoverUrl = rememberEffectiveCoverUrl(song, downloadState)
                CoverImage(
                    coverUrl = effectiveCoverUrl,
                    contentDescription = song.title,
                    size = 92.dp,
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.width(14.dp))

                // 序号 / 当前播放标记 / 播放图标
                if (isCurrent) {
                    // 正在播放的歌曲：序号位换成 ♪（跨列表常驻，切走不丢）
                    Text(
                        text = "♪",
                        color = NasMusicColors.Primary,
                        fontSize = FontSize.button(),
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else if (index != null) {
                        Text(
                        text = String.format(Locale.ROOT, "%02d", index + 1),
                        color = NasMusicColors.TextSecondary,
                        fontSize = FontSize.button(),
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                        Text(
                        text = "▶",
                        color = NasMusicColors.Primary,
                        fontSize = FontSize.button(),
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                // 歌曲信息 + 来源标签
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MarqueeText(
                            text = song.title,
                            color = if (isCurrent) NasMusicColors.Primary else NasMusicColors.TextPrimary,
                            fontSize = FontSize.button(),
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 来源标签：URL 失效（红）> 待补全（橙）> 其余走「来源徽标 + 版权存疑图标（灰）」。
                        // ⚠️ 角标只打标不过滤（Q1 b）；行仍可点播，失败走 Task 5/6/7 的替换链。
                        when {
                            urlStatus == UrlStatus.UNREACHABLE -> Text(
                                text = stringResource(com.lnh.nasmusictv.R.string.playlist_url_invalid),
                                color = NasMusicColors.Warning,
                                fontSize = FontSize.small()
                            )
                            isStub -> Text(
                                text = stringResource(com.lnh.nasmusictv.R.string.playlist_stub_badge),
                                color = NasMusicColors.Primary,
                                fontSize = FontSize.small()
                            )
                            // 「版权存疑」角标是**追加**在来源徽标之后的，不再顶替它
                            // （原先互斥的 when 让 artist 全量展开的行丢了「网络」标签）。
                            // 顺序有意为之：来源徽标在前 —— Row 里非 weight 的子项按序测量，
                            // 窄屏挤爆时先被压扁的是后面的角标，来源标签始终完整可见。
                            // ⚠️ owner 决策：角标改为**纯图标**（℗ 上画一道斜杠，不带任何文字）。
                            // 图标不会说话、也就没法像文案那样带不确定语气，所以角标仍挂
                            // `song_row_unplayable_badge`（「版权存疑」/「Rights unclear」）作读屏名 ——
                            // 图标在视觉上比文案更确定，那句带保留的文案是唯一的兜底。
                            // ⛔ `privilege.st` 只是**发行参考**，实测这类行可播率仅 15.0%(3/20)
                            // （docs/archive/network-artist-full-discography-plan.md T2），
                            // ⛔ 不得改写成「不可播 / 播不了 / 无版权」这类已证实的断言。
                            else -> {
                                SourceBadge(song = song)
                                if (possiblyUnplayable) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CopyrightSuspectMark()
                                }
                            }
                        }
                        // 多码率（§5.2.3）：已下载歌曲显示**实际落盘档位**徽标。
                        // 同曲多档并存时（无损 FLAC / 极高 320 / 标准 128 各一行），
                        // 靠它区分，否则用户会误以为重复下载了同一首歌。
                        // 优先取 downloadState 的档位；网络歌曲未下载但已解析过则回退 resolvedQuality。
                        val badgeQuality = when (effectiveDownloadState) {
                            is DownloadState.Completed -> effectiveDownloadState.quality
                            else -> song.resolvedQuality
                        }
                        if (badgeQuality != com.lnh.nasmusictv.data.model.QualityTiers.AUTO) {
                            Spacer(modifier = Modifier.width(8.dp))
                            com.lnh.nasmusictv.ui.components.QualityBadge(quality = badgeQuality)
                        }
                    }
                        Text(
                        text = song.artist.ifBlank { "-" },
                        color = NasMusicColors.TextSecondary,
                        fontSize = FontSize.button(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = TimeUtils.formatDuration(song.durationMs),
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.button()
                )
            }

            // 操作按钮在行内右侧
            actionButtons()
        }
    }
    }

    // P1-16: Completed 状态点击弹出删除确认
    if (showDeleteConfirm) {
        Dialog(
            onDismissRequest = { showDeleteConfirm = false },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            ConfirmDialog(
title = stringResource(R.string.download_delete_title),
                message = stringResource(R.string.download_delete_message, song.title),
                destructive = true,
                onConfirm = {
                    onDeleteDownload?.invoke()
                    showDeleteConfirm = false
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

/**
 * 「版权存疑」角标：℗（录音制品版权符号）上加一道斜杠，**纯图标、不带任何文字**。
 *
 * ⛔ 为什么不直接 `Text("℗")`：U+2117 不在所有系统字体里。minSdk 22 的老电视
 * （如创维 5.1）缺字会渲染成豆腐块 □ —— 比原来的文案更糟，而且**只在别人设备上**暴露。
 * 所以圆环、P 的竖干与碗、斜杠一律用 `drawBehind` 画矢量，不依赖任何字形覆盖。
 * ⛔ 斜杠是真画的 45° 斜线：`TextDecoration.LineThrough` 画的是横线，不是这里要的。
 *
 * ⚠️ 图标不会说话，也没法像「版权存疑」那样带不确定语气，故仍挂
 * `R.string.song_row_unplayable_badge` 作读屏名（`clearAndSetSemantics`）。
 */
@Composable
private fun CopyrightSuspectMark(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.song_row_unplayable_badge)
    val density = LocalDensity.current
    // 图标随 caption 档走，与同一行的来源徽标 / QualityBadge 同一字号体系；
    // sp→dp 走 LocalDensity（含 fontScale），用户在设置里调大字体时图标跟着缩放。
    val markSize = with(density) { FontSize.caption().toDp() }
    // Stroke 提到组合期缓存：滚动时每帧新建 Stroke 纯属浪费（这一行会随列表反复重绘）。
    val markStroke = remember(markSize) {
        Stroke(width = with(density) { (markSize * 0.08f).toPx() })
    }
    val slashStroke = remember(markSize) {
        Stroke(width = with(density) { (markSize * 0.11f).toPx() })
    }
    Box(
        modifier = modifier
            .size(markSize + 6.dp)
            .drawBehind {
                val g = markSize.toPx()          // 图标本体边长（药丸四周各留 3.dp）
                val cx = size.width / 2f
                val cy = size.height / 2f
                val ink = NasMusicColors.TextSecondary
                val stemX = cx - g * 0.13f
                val ringR = g / 2f - markStroke.width / 2f
                // ① 药丸底：照 VisualizerStage 的 drawBehind 圆角底写法，
                //    避开 RoundedCornerShape.clip 在 Android 5.1 的 hwui Region 段错误。
                drawRoundRect(
                    color = NasMusicColors.SurfaceVariant,
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                // ② ℗ 的圆环
                drawCircle(
                    color = ink,
                    radius = ringR,
                    center = Offset(cx, cy),
                    style = markStroke
                )
                // ③ P 的碗：右半椭圆。椭圆圆心 x 对齐竖干 ⇒ 弧的两端正好落在竖干上下端。
                val bowlTop = cy - g * 0.20f
                val bowlHalfW = g * 0.26f
                drawArc(
                    color = ink,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(stemX - bowlHalfW, bowlTop),
                    size = Size(bowlHalfW * 2f, g * 0.26f),
                    style = markStroke
                )
                // ④ P 的竖干（下端比碗长出一截，形成 P 的下伸段）
                drawLine(
                    color = ink,
                    start = Offset(stemX, bowlTop),
                    end = Offset(stemX, cy + g * 0.30f),
                    strokeWidth = markStroke.width,
                    cap = StrokeCap.Round
                )
                // ⑤ 斜杠：过圆心的 45° 斜线
                drawLine(
                    color = ink,
                    start = Offset(cx - ringR * 0.68f, cy + ringR * 0.68f),
                    end = Offset(cx + ringR * 0.68f, cy - ringR * 0.68f),
                    strokeWidth = slashStroke.width,
                    cap = StrokeCap.Round
                )
            }
            .clearAndSetSemantics { contentDescription = label }
    )
}

/**
 * 行内操作小按钮（收藏 / 队列 / 歌单）— Box + focusable + clickable，
 * 触屏与遥控器（D-Pad）均可操作，带焦点放大反馈与触控目标尺寸。
 */
@Composable
private fun RowActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    // 焦点视觉（高亮底 / 焦点环 / 1.15 缩放）只在**用按键导航**的设备上生效 ——
    // 手机触摸会让 `focusable()`/`clickable` 节点获得焦点且焦点粘住，
    // 否则点过一次的按钮会**永久放大 15% 并永久高亮**（与 FocusableSurface 同款处理）。
    // ⚠️ 判据走 `shouldShowFocusVisuals()`（无触摸屏 / 电视 feature / 已用过方向键），
    // 不再单靠 `hasSystemFeature` 嗅探 —— 嗅探在非认证电视盒子上为 false，
    // 会把焦点环整项关掉（真机反馈「遥控器导航看不到焦点」）。
    val showFocusVisuals = shouldShowFocusVisuals()
    var isFocused by remember { mutableStateOf(false) }
    val animScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            // 48 / 42 dp 在竖屏分别只有 39.4 / 34.4 物理 dp ❌ → §2.7 换算抬到 56dp
            .widthIn(min = portraitTouchTarget(48.dp))
            .heightIn(min = portraitTouchTarget(42.dp))
            .scale(animScale.value)
            // ⛔ 不再 `.clip(shape)`：它会把 focusRing 的外侧辉光裁掉、还只剩内侧半圈。
            // 背景本身已由 `.background(color, shape)` 按圆角裁剪，clip 原本就是冗余的。
            .background(
                color = if (isFocused) color.copy(alpha = 0.25f) else Color.Transparent,
                shape = shape
            )
            .onFocusChanged { state ->
                isFocused = state.hasFocus && showFocusVisuals
                scope.launch {
                    animScale.animateTo(
                        if (state.hasFocus && showFocusVisuals) 1.15f else 1f,
                        tween(150)
                    )
                }
            }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled) { onClick() }
            // 焦点环挂在链尾 ⇒ 画在背景与文字之上（与 FocusableSurface 同款契约）
            .focusRing(
                shape = shape,
                color = NasMusicColors.FocusRing,
                visible = isFocused,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = FontSize.subtitle(),
            color = if (enabled) color else color.copy(alpha = 0.4f)
        )
    }
}

/**
 * MODE_CARD：卡片布局（封面在上，文字在下）
 * 与现有 HomeScreen.HomeSongCard 样式一致
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongRowModeCard(
    song: Song,
    onClick: () -> Unit,
    downloadState: DownloadState = DownloadState.None,
    focusRequester: FocusRequester?,
    isCurrent: Boolean = false
) {
    // 竖屏体验修复：焦点视觉只在**用按键导航**的设备上生效，
    // 与 FocusableSurface / SongRowModeRow / RowActionButton 同款判据。
    // ⚠️ 此前 Card/Compact 直接 `state.hasFocus` 不判设备 ⇒ 在焦点视觉被关掉的
    // 设备（或手机触摸）上，卡片会"永久放大 + 永久高亮"而列表行不会 ——
    // 同一屏两套焦点行为，正是真机反馈「焦点态混淆」的来源之一。
    val tvDevice = shouldShowFocusVisuals()
    var isFocused by remember { mutableStateOf(false) }
    val animScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .width(160.dp)
            .scale(animScale.value)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = when {
                    isFocused -> NasMusicColors.FocusOverlay
                    isCurrent -> NasMusicColors.Primary.copy(alpha = 0.08f)
                    else -> NasMusicColors.Surface.copy(alpha = 0.6f)
                }
            )
            // 选中（当前播放）的持久描边：常驻、与焦点环并存时可区分
            .then(
                if (isCurrent) Modifier.border(2.dp, NasMusicColors.Primary.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .onFocusChanged { state ->
                isFocused = state.hasFocus && tvDevice
                scope.launch {
                    animScale.animateTo(
                        if (isFocused) 1.06f else 1f,
                        tween(200)
                    )
                }
            }
            .focusRing(
                shape = RoundedCornerShape(12.dp),
                color = NasMusicColors.FocusRing,
                visible = isFocused,
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp)
        ) {
            // 内嵌封面提取在 IO 协程中完成；卡片首帧先用 song.coverUrl 兜底。
            val cardCoverUrl = rememberEffectiveCoverUrl(song, downloadState)
            CoverImage(
                coverUrl = cardCoverUrl,
                contentDescription = song.title,
                size = 148.dp,
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
                MarqueeText(
                    text = song.title,
                    color = if (isCurrent) NasMusicColors.Primary else NasMusicColors.TextPrimary,
                    fontSize = FontSize.body(),
                    maxLines = 1,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = song.artist.ifBlank { "-" },
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.small(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * MODE_COMPACT：紧凑行（无封面，仅文字 + 序号）
 * 用于播放队列等空间紧张的场景
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongRowModeCompact(
    song: Song,
    onClick: () -> Unit,
    index: Int?,
    focusRequester: FocusRequester?,
    isCurrent: Boolean = false
) {
    // 与 SongRowModeCard 同款：焦点视觉统一走 shouldShowFocusVisuals()，
    // 避免同屏两套焦点行为（此前 Compact 直接 hasFocus，被关掉焦点视觉的设备上会永久高亮）。
    val tvDevice = shouldShowFocusVisuals()
    var isFocused by remember { mutableStateOf(false) }
    val animScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animScale.value)
            .clip(RoundedCornerShape(6.dp))
            .background(
                // 选中（当前播放）0.10f 常驻；焦点 0.20f 瞬态（压过选中行）
                color = when {
                    isFocused -> NasMusicColors.Primary.copy(alpha = 0.2f)
                    isCurrent -> NasMusicColors.FocusOverlay
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { state ->
                isFocused = state.hasFocus && tvDevice
                scope.launch {
                    animScale.animateTo(
                        if (isFocused) 1.02f else 1f,
                        tween(200)
                    )
                }
            }
            .focusRing(
                shape = RoundedCornerShape(6.dp),
                color = NasMusicColors.FocusRing,
                visible = isFocused,
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号 / 当前播放标记
            if (isCurrent) {
                Text(
                    text = "♪",
                    color = NasMusicColors.Primary,
                    fontSize = FontSize.body(),
                    modifier = Modifier.width(30.dp)
                )
            } else if (index != null) {
                    Text(
                    text = "${index + 1}.",
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.body(),
                    modifier = Modifier.width(30.dp)
                )
            }
            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                    text = song.title,
                    color = if (isCurrent) NasMusicColors.Primary else NasMusicColors.TextPrimary,
                    fontSize = FontSize.button(),
                    maxLines = 1,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = song.artist.ifBlank { "-" },
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.small(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 时长
            Text(
                text = TimeUtils.formatDuration(song.durationMs),
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.small()
            )
        }
    }
}
