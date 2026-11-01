package com.lnh.nasmusictv.ui.components.branches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.lnh.nasmusictv.data.model.Screen
import com.lnh.nasmusictv.ui.viewmodel.MainViewModel
import com.lnh.nasmusictv.data.model.*
import com.lnh.nasmusictv.ui.screens.*
import com.lnh.nasmusictv.ui.screens.library.*
import com.lnh.nasmusictv.ui.screens.settings.*
import com.lnh.nasmusictv.ui.screens.netdisk.*
import com.lnh.nasmusictv.ui.screens.stats.*
import com.lnh.nasmusictv.ui.viewmodel.*
/**
 * Mine 分支提取自 AppRoot（Method too large 根治：分支下沉 branches/）。
 * 分支体逐行搬迁，外层共享状态经参数注入。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun MineBranch(
    viewModel: MainViewModel
) {
                    // M7 修复：下载进度流下沉到分支内收集（原 AppRoot 顶层收集下传）
                    val songDownloadStates by viewModel.songDownloadStates.collectAsState(initial = emptyMap())
                    val favoriteSongsState by viewModel.favoriteSongs.collectAsState(initial = UiState.Success(emptyList()))
                    val networkFavoriteSongs by viewModel.networkFavoriteSongs.collectAsState(initial = emptyList())
                    val recentSongsState by viewModel.recentSongs.collectAsState(initial = UiState.Success(emptyList()))
                    val recentSongsList = recentSongsState.dataOrNull() ?: emptyList()
                    val localPlaylists by viewModel.playlistVM.localPlaylists.collectAsState(initial = emptyList())
                    // 进入"我的"页时刷新最近播放（首次进入/从播放页返回时更新）
                    LaunchedEffect(Unit) {
                        viewModel.loadRecentSongs()
                        // 已连接服务器时同步服务器歌单（未连接则区块自动隐藏）
                        viewModel.serverVM.loadServerPlaylists()
                    }
                    val queueSongIds by viewModel.queueSongIds.collectAsState(initial = emptySet())
                    // 服务器歌单（「我的」页「服务器歌单」区块数据源；未连接时为空且不显示）
                    val serverConnected by viewModel.serverVM.isConnected.collectAsState(initial = false)
                    val serverPlaylists by viewModel.serverVM.serverPlaylists.collectAsState(initial = emptyList())
                    val serverPlaylistsLoading by viewModel.serverVM.serverPlaylistsLoading.collectAsState(initial = false)
                    // 歌单导入（阶段5）：URL 失效标记 / 补全进度（「我的」页徽标与按钮数据源）
                    val songReachability by viewModel.playlistImportVM.songReachability.collectAsState(initial = emptyMap())
                    val enrichProgress by viewModel.playlistImportVM.enrichProgress.collectAsState(initial = null)
                    MineScreen(
                        favoriteSongsState = favoriteSongsState,
                        networkFavoriteSongs = networkFavoriteSongs,
                        recentSongs = recentSongsList,
                        localPlaylists = localPlaylists,
                        queueSongIds = queueSongIds,
                        onPlaySong = { song ->
                            // 网络歌曲先解析播放链接，本地歌曲直接播放
                            if (song.isNetworkSong) {
                                viewModel.playNetworkSong(song)
                            } else {
                                viewModel.playQueue(listOf(song))
                            }
                            viewModel.navVM.navigateTo(Screen.NowPlaying)
                        },
                        onPlayAll = { songs ->
                            if (songs.isNotEmpty()) {
                                viewModel.playQueue(songs)
                                viewModel.navVM.navigateTo(Screen.NowPlaying)
                            }
                        },
                        onToggleFavorite = { song -> viewModel.toggleNetworkFavorite(song) },
                        onToggleQueue = { song -> viewModel.playerVM.toggleQueueSong(song) },
                        onCreatePlaylist = { name -> viewModel.playlistVM.createLocalPlaylist(name) },
                        onRenamePlaylist = { id, newName -> viewModel.playlistVM.renameLocalPlaylist(id, newName) },
                        onDeletePlaylist = { id ->
                            viewModel.playlistVM.deleteLocalPlaylist(id)
                            // 歌单删除联动清理导入历史（2026-09-18 决策：历史行无删除入口）
                            viewModel.playlistImportVM.consumeHistoryIfDeleted(id)
                        },
                        onPlayPlaylist = { playlist -> viewModel.playLocalPlaylist(playlist) },
                        onRemoveSongFromPlaylist = { playlistId, songId -> viewModel.playlistVM.removeSongFromPlaylist(playlistId, songId) },
                        onAddSongToPlaylist = { playlistId, song -> viewModel.playlistVM.addSongToPlaylist(playlistId, song) },
                        // 功能入口（手机端底部导航未覆盖：队列 / 网盘 / 设置）
                        onOpenQueue = { viewModel.navVM.navigateTo(Screen.Queue) },
                        onOpenNetdisk = { viewModel.navVM.navigateTo(Screen.Netdisk) },
                        onOpenSettings = { viewModel.navVM.navigateTo(Screen.Settings) },
                        // 歌曲下载状态
                        downloadStates = songDownloadStates,
                        onDownloadSong = { song -> viewModel.downloadVM.downloadSong(song) },
                        onDeleteDownloadSong = { song -> viewModel.downloadVM.deleteDownload(song) },
                        // 歌单导入（阶段5）
                        songReachability = songReachability,
                        enrichProgress = enrichProgress,
                        onEnrichPlaylist = { playlistId -> viewModel.playlistImportVM.enrichPlaylist(playlistId) },
                        // 服务器歌单（2026-10-28：已连接服务器时同步展示）
                        serverConnected = serverConnected,
                        serverPlaylists = serverPlaylists,
                        serverPlaylistsLoading = serverPlaylistsLoading,
                        onPlayServerPlaylist = { playlist -> viewModel.playServerPlaylist(playlist) }
                    )
}
