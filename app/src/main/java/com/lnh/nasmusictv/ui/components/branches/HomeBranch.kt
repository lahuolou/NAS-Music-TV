package com.lnh.nasmusictv.ui.components.branches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Home 分支提取自 AppRoot（Method too large 根治：分支下沉 branches/）。
 * 分支体逐行搬迁，外层共享状态经参数注入。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HomeBranch(
    viewModel: MainViewModel,
    isConnected: Boolean,
    isLibraryLoading: Boolean,
    serverDisplayName: String,
    currentSong: Song?,
    coverCandidates: List<String>
) {
                    val homeDashboardData by viewModel.homeDashboardData.collectAsState(initial = HomeDashboardData())
                    val weatherData by viewModel.weatherRadioVM.weatherData.collectAsState(initial = null)
                    val weatherLoading by viewModel.weatherRadioVM.weatherLoading.collectAsState(initial = false)
                    val weatherError by viewModel.weatherRadioVM.weatherError.collectAsState(initial = null)
                    val recentSongsState by viewModel.recentSongs.collectAsState(initial = UiState.Success(emptyList()))
                    val recentSongsList = recentSongsState.dataOrNull() ?: emptyList()
                    val randomSongs by viewModel.randomSongs.collectAsState(initial = emptyList())

                    // 进入首页时刷新数据
                    LaunchedEffect(Unit) {
                        viewModel.loadHomeDashboard()
                        viewModel.loadRecentSongs()
                        viewModel.weatherRadioVM.fetchWeather()
                        viewModel.loadRandomSongs()
                    }

                    HomeScreen(
                        isConnected = isConnected,
                        isLibraryLoading = isLibraryLoading,
                        serverDisplayName = serverDisplayName,
                        dashboardData = homeDashboardData,
                        weatherData = weatherData,
                        weatherLoading = weatherLoading,
                        weatherError = weatherError,
                        recentSongs = recentSongsList,
                        currentSong = currentSong,
                        coverCandidates = coverCandidates,
                        onPlaySong = { song ->
                            if (song.isNetworkSong) viewModel.playNetworkSong(song)
                            else viewModel.playQueue(listOf(song))
                            viewModel.navVM.navigateTo(Screen.NowPlaying)
                        },
                        onPlayAlbum = { album ->
                            viewModel.playAlbumMultiSource(album)
                        },
                        onOpenAlbumDetail = { album -> viewModel.openAlbumDetail(album) },
                        onNavigateToLibrary = { viewModel.navVM.navigateTo(Screen.Library) },
                        onNavigateToSearch = {
                            viewModel.selectLibraryTab(LibraryTab.SEARCH)
                            viewModel.navVM.navigateTo(Screen.Library)
                        },
                        onNavigateToQueue = { viewModel.navVM.navigateTo(Screen.Queue) },
                        onNavigateToNowPlaying = { viewModel.navVM.navigateTo(Screen.NowPlaying) },
                        onNavigateToWeatherRadio = { viewModel.navVM.navigateTo(Screen.WeatherRadio) },
                        onPlayAllRecent = {
                            if (recentSongsList.isNotEmpty()) {
                                viewModel.playQueue(recentSongsList)
                                viewModel.navVM.navigateTo(Screen.NowPlaying)
                            }
                        },
                        randomSongs = randomSongs,
                        onPlayRandomSongs = { songs, index ->
                            viewModel.playRandomSongs(songs, index)
                        },
                        // F2-3 首页列表化：智能电台浏览批次（随心听式，点卡片播歌）
                        // 修复（2026-10-28）：此前只 playQueue 不跳转 —— 点击卡片后歌曲
                        // 已经在播，但页面纹丝不动，用户以为入口"坏了"（与最近播放 /
                        // 随机歌曲入口行为不一致：它们都 navigateTo(NowPlaying)）。
                        // 现在与其它入口对齐：播放 + 跳转播放页。
                        smartRadioBatch = viewModel.smartRadioBatch.collectAsState().value,
                        onPlaySmartRadioAt = { index ->
                            viewModel.playSmartRadioBatchAt(index)
                            viewModel.navVM.navigateTo(Screen.NowPlaying)
                        },
                        onLoadSmartRadio = { viewModel.loadSmartRadioBatch() }
                    )
}
