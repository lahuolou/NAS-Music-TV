package com.lnh.nasmusictv.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.backend.download.model.DownloadState
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.ui.LocalListBackHandler
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.LocalUiMode
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.UiMode
import com.lnh.nasmusictv.ui.components.BackButton
import com.lnh.nasmusictv.ui.components.FocusableSurface
import com.lnh.nasmusictv.ui.components.MarqueeText
import androidx.compose.ui.text.style.TextAlign
import com.lnh.nasmusictv.ui.components.song.UnifiedSongRow
import com.lnh.nasmusictv.ui.components.song.SongRowMode
import com.lnh.nasmusictv.ui.components.common.CoverImage
import kotlinx.coroutines.launch
import com.lnh.nasmusictv.backend.download.model.stateOfSong
/**
 * 歌手详情屏幕
 * 顶部：歌手头像 + 名称
 * 下方：歌手所有歌曲列表
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artist: com.lnh.nasmusictv.data.model.Artist? = null,
    artistName: String,
    songs: List<Song>,
    onPlaySong: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onBack: () -> Unit,
    queueSongIds: Set<String> = emptySet(),
    onToggleQueue: (Song) -> Unit = {},
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    downloadStates: Map<String, DownloadState> = emptyMap(),
    onDownloadSong: (Song) -> Unit = {},
    onDeleteDownloadSong: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val listBackHandler = LocalListBackHandler.current

    // Level 1.5: 曲目列表已滚动时按 BACK 先回顶并聚焦第一个
    DisposableEffect(Unit) {
        val handler: () -> Boolean = {
            val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            if (!atTop) {
                scope.launch {
                    listState.scrollToItem(0)
                    runCatching { firstItemFocusRequester.requestFocus() }
                }
                true
            } else {
                false
            }
        }
        listBackHandler.value = handler
        onDispose { listBackHandler.value = null }
    }

    val isPhoneLandscape = LocalUiMode.current == UiMode.PhoneLandscape

    Column(
        modifier = modifier.fillMaxSize().padding(
            horizontal = if (isPhoneLandscape) 16.dp else 32.dp,
            vertical = if (isPhoneLandscape) 0.dp else 20.dp
        )
    ) {
        // 返回 + 标题行 + 操作按钮 + 歌曲数
        val playAllButton: @Composable () -> Unit = {
            FocusableSurface(
                onClick = { if (songs.isNotEmpty()) onPlayAll(songs) },
                shape = RoundedCornerShape(8.dp),
                focusedScale = 1.08f,
                animationDurationMs = 150,
                containerColor = NasMusicColors.PrimaryFill,
                focusedContainerColor = NasMusicColors.Primary,
                contentColor = NasMusicColors.TextPrimary,
                focusedContentColor = NasMusicColors.TextPrimary
            ) {
                Text(
                    text = stringResource(R.string.action_play_all),
                    color = NasMusicColors.TextPrimary,
                    fontSize = FontSize.button(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        val addQueueButton: @Composable () -> Unit = {
            FocusableSurface(
                onClick = { songs.forEach { song -> onToggleQueue(song) } },
                shape = RoundedCornerShape(8.dp),
                focusedScale = 1.08f,
                animationDurationMs = 150,
                containerColor = NasMusicColors.Surface.copy(alpha = 0.7f),
                focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.3f),
                contentColor = NasMusicColors.TextPrimary,
                focusedContentColor = NasMusicColors.Primary
            ) {
                Text(
                    text = stringResource(R.string.action_add_queue),
                    color = NasMusicColors.TextPrimary,
                    fontSize = FontSize.button(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        val songCountText: @Composable () -> Unit = {
            Text(
                text = stringResource(R.string.action_song_count, songs.size),
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.body()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(16.dp))
            MarqueeText(
                text = artistName,
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.title(),
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            playAllButton()
            Spacer(modifier = Modifier.width(12.dp))
            addQueueButton()
            Spacer(modifier = Modifier.width(12.dp))
            songCountText()
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 歌曲列表（两种形态共用同一份实现）
        val songList: @Composable (Modifier) -> Unit = { m ->
            LazyColumn(
                modifier = m,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    UnifiedSongRow(
                        song = song,
                        onClick = { onPlaySong(song) },
                        mode = SongRowMode.MODE_ROW,
                        index = index,
                        isFavorited = song.id in favoriteIds,
                        onToggleFavorite = { onToggleFavorite(song) },
                        isInQueue = song.id in queueSongIds,
                        onToggleQueue = { onToggleQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        downloadState = downloadStates.stateOfSong(song),
                        onDownload = { onDownloadSong(song) },
                        onDeleteDownload = onDeleteDownloadSong?.let { cb -> { cb(song) } },
                        focusRequester = if (index == 0) firstItemFocusRequester else null
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 左侧：歌手头像
            CoverImage(
                coverUrl = artist?.coverUrl,
                contentDescription = artistName,
                size = 160.dp,
                cornerRadius = 80.dp
            )

            Spacer(modifier = Modifier.width(24.dp))

            // 右侧：歌曲列表
            Column(modifier = Modifier.weight(1f)) {
                Spacer(modifier = Modifier.height(16.dp))
                songList(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
