package com.lnh.nasmusictv.ui.components.branches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.lnh.nasmusictv.data.model.Screen
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.ui.viewmodel.MainViewModel
import com.lnh.nasmusictv.data.model.*
import com.lnh.nasmusictv.ui.screens.*
import com.lnh.nasmusictv.ui.screens.library.*
import com.lnh.nasmusictv.ui.screens.settings.*
import com.lnh.nasmusictv.ui.screens.netdisk.*
import com.lnh.nasmusictv.ui.screens.stats.*
import com.lnh.nasmusictv.ui.viewmodel.*
/**
 * ArtistDetail 分支提取自 AppRoot（Method too large 根治：分支下沉 branches/）。
 * 分支体逐行搬迁，外层共享状态经参数注入。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ArtistDetailBranch(
    viewModel: MainViewModel,
    onPickSongForPlaylist: (Song) -> Unit
) {
                    // M7 修复：下载进度流下沉到分支内收集（原 AppRoot 顶层收集下传）
                    val songDownloadStates by viewModel.songDownloadStates.collectAsState(initial = emptyMap())
                    val selectedArtistName by viewModel.selectedArtistName.collectAsState(initial = null)
                    val artistDetailSongsCache by viewModel.artistDetailSongsCache.collectAsState(initial = emptyMap())
                    val artistSongs = selectedArtistName?.let { artistDetailSongsCache[it] } ?: emptyList()
                    // 用合并后的 _mergedArtists（已应用 resolvedArtistCovers 封面缓存）查找，
                    // 而非原始 _artists：百度/本地艺术家不在 _artists，且 _artists 的 coverUrl 未应用解析缓存，
                    // 导致详情页左侧封面不显示。
                    val artistsState by viewModel.mergedArtists.collectAsState(initial = emptyList())
                    val selectedArtist = selectedArtistName?.let { name ->
                        val key = com.lnh.nasmusictv.util.ArtistSplitter.normalizeKey(name)
                        artistsState.find {
                            com.lnh.nasmusictv.util.ArtistSplitter.normalizeKey(it.name) == key
                        }
                    }
                    val favoriteIds by viewModel.favoriteIds.collectAsState(initial = emptySet())
                    if (selectedArtistName != null) {
                        ArtistDetailScreen(
                            artist = selectedArtist,
                            artistName = selectedArtistName ?: "",
                            songs = artistSongs,
                            onPlaySong = { song ->
                                viewModel.playQueue(artistSongs, artistSongs.indexOf(song).coerceAtLeast(0))
                                viewModel.navVM.navigateTo(Screen.NowPlaying)
                            },
                            onPlayAll = { songList ->
                                viewModel.playQueue(songList)
                                viewModel.navVM.navigateTo(Screen.NowPlaying)
                            },
                            onBack = { viewModel.navVM.navigateTo(Screen.Library) },
                            queueSongIds = viewModel.queueSongIds.collectAsState(initial = emptySet()).value,
                            onToggleQueue = { song -> viewModel.playerVM.toggleQueueSong(song) },
                            favoriteIds = favoriteIds,
                            onToggleFavorite = { song -> viewModel.toggleNetworkFavorite(song) },
                            onAddToPlaylist = { song -> onPickSongForPlaylist(song) },
                            downloadStates = songDownloadStates,
                            onDownloadSong = { song -> viewModel.downloadVM.downloadSong(song) },
                            onDeleteDownloadSong = { song -> viewModel.downloadVM.deleteDownload(song) }
                        )
                    }
}
