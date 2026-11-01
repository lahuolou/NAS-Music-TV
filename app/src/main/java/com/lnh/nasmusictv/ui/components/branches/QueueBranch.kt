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
 * Queue 分支提取自 AppRoot（Method too large 根治：分支下沉 branches/）。
 * 分支体逐行搬迁，外层共享状态经参数注入。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun QueueBranch(
    viewModel: MainViewModel,
    currentSong: Song?,
    coverCandidates: List<String>,
    isPlaying: Boolean,
    playMode: com.lnh.nasmusictv.data.model.PlayMode
) {
                    val playerState by viewModel.playerVM.playerState.collectAsState()
                    val queue = playerState.queue
                    val currentIndex = playerState.currentIndex
                    QueueScreen(
                        queue = queue,
                        currentIndex = currentIndex,
                        currentSong = currentSong,
                        coverCandidates = coverCandidates,
                        isPlaying = isPlaying,
                        playMode = playMode,
                        onPlaySong = { index ->
                            if (index in queue.indices) {
                                viewModel.playQueue(queue, index)
                                viewModel.navVM.navigateTo(Screen.NowPlaying)
                            }
                        },
                        onRemoveSong = { index -> viewModel.removeFromQueue(index) },
                        onClearQueue = { viewModel.clearQueue() },
                        onPlayPause = { viewModel.playerVM.playPause() },
                        onNext = { viewModel.playerVM.next() },
                        onPrevious = { viewModel.playerVM.previous() },
                        onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) }
                    )
}
