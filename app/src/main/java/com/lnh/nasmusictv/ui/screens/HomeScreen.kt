package com.lnh.nasmusictv.ui.screens

import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.UiMode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.data.model.Album
import com.lnh.nasmusictv.data.model.HomeDashboardData
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.data.model.WeatherData
import com.lnh.nasmusictv.data.model.WeatherMood
import com.lnh.nasmusictv.ui.LocalListBackHandler
import com.lnh.nasmusictv.ui.components.FocusableSurface
import com.lnh.nasmusictv.ui.components.MarqueeText
import com.lnh.nasmusictv.ui.components.common.SectionHeader
import com.lnh.nasmusictv.ui.components.LocalFocusableContentColor
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import kotlinx.coroutines.launch
/**
 * 首页仪表盘
 *
 * 连接 NAS 后展示音乐库概览，提供快捷入口和推荐内容。
 * 结构（从上到下）：
 * 1. 欢迎区域（服务器名称 + 统计卡片）
 * 2. 最新添加专辑（横向滚动）
 * 3. 推荐/热门歌曲
 * 4. 快捷操作（搜索、网络音乐等）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
    isLibraryLoading: Boolean,
    serverDisplayName: String,
    dashboardData: HomeDashboardData,
    weatherData: WeatherData?,
    weatherLoading: Boolean = false,
    weatherError: String? = null,
    recentSongs: List<Song>,
    currentSong: Song? = null,
    coverCandidates: List<String> = emptyList(),
    onPlaySong: (Song) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onOpenAlbumDetail: (Album) -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToQueue: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {},
    onPlayAllRecent: () -> Unit = {},
    onNavigateToWeatherRadio: () -> Unit = {},
    randomSongs: List<Song> = emptyList(),
    onPlayRandomSongs: (List<Song>, Int) -> Unit = { _, _ -> },
    /** F2-3 首页列表化：智能电台浏览批次（随心听式），onLoadSmartRadio = null 隐藏区块 */
    smartRadioBatch: List<Song> = emptyList(),
    /** 播放批次中第 index 首 */
    onPlaySmartRadioAt: (Int) -> Unit = {},
    /** 生成/换一批（只生成不播放；首次进入自动触发） */
    onLoadSmartRadio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val listBackHandler = LocalListBackHandler.current
    // LazyColumn 内容 lambda 不是 @Composable 上下文

    // F2-3 首页列表化：首次进入自动生成智能电台批次（只生成不播放；防重入在 MainViewModel）
    LaunchedEffect(Unit) { onLoadSmartRadio?.invoke() }

    // Level 1.5: 列表回顶
    DisposableEffect(Unit) {
        val handler: () -> Boolean = {
            val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            if (!atTop) {
                scope.launch { listState.scrollToItem(0) }
                true
            } else {
                false
            }
        }
        listBackHandler.value = handler
        onDispose { listBackHandler.value = null }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. 欢迎 + 统计卡片
        item(key = "welcome") {
            WelcomeSection(
                isConnected = isConnected,
                isLibraryLoading = isLibraryLoading,
                serverDisplayName = serverDisplayName,
                dashboardData = dashboardData,
                weatherData = weatherData
            )
        }

        // 1.5 当前播放卡片（有歌曲正在播放时显示）
        // 手机横屏可用高度有限且 TV 顶栏已有「正在播放」入口 → 手机横屏隐藏
        // （B1：TV 端保持原显示条件，行为不变）
        if (currentSong != null) {
            item(key = "now_playing") {
                NowPlayingCard(
                    song = currentSong,
                    coverCandidates = coverCandidates,
                    onClick = onNavigateToNowPlaying
                )
            }
        }

        // 2. 快捷操作按钮
        item(key = "quick_actions") {
            QuickActionRow(
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToQueue = onNavigateToQueue
            )
        }

        // 3. 最新添加专辑（仅当有数据时显示）
        val recentAlbums = dashboardData.recentlyAddedAlbums
        if (recentAlbums.isNotEmpty()) {
            item(key = "recent_albums_header") {
                SectionHeader(
                    title = stringResource(R.string.library_recently_added),
                    count = recentAlbums.size
                )
            }
            item(key = "recent_albums") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(recentAlbums, key = { it.id }) { album ->
                        HomeAlbumCard(
                            album = album,
                            onClick = { onOpenAlbumDetail(album) },
                            onPlay = { onPlayAlbum(album) }
                        )
                    }
                }
            }
        }

        // 4. 最近播放歌曲
        if (recentSongs.isNotEmpty()) {
            item(key = "recent_played_header") {
                SectionHeader(
                    title = stringResource(R.string.library_recent),
                    count = recentSongs.size,
                    onViewAll = { onPlayAllRecent() }
                )
            }
            item(key = "recent_played") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(recentSongs.take(20), key = { it.id }) { song ->
                        HomeSongCard(
                            song = song,
                            onClick = { onPlaySong(song) }
                        )
                    }
                }
            }
        }

        // 4.5 随心听（随机歌曲推荐）
        if (randomSongs.isNotEmpty()) {
            item(key = "shuffle_play_header") {
                SectionHeader(
                    title = stringResource(R.string.home_shuffle_play),
                    count = randomSongs.size
                )
            }
            item(key = "shuffle_play") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(randomSongs, key = { it.id }) { song ->
                        HomeSongCard(
                            song = song,
                            onClick = { onPlayRandomSongs(randomSongs, randomSongs.indexOf(song)) }
                        )
                    }
                }
            }
        }

        // 5. 收藏歌曲展示
        val favoriteSongs = dashboardData.favoriteSongs
        if (favoriteSongs.isNotEmpty()) {
            item(key = "favorites_header") {
                SectionHeader(
                    title = stringResource(R.string.library_favorites),
                    count = favoriteSongs.size
                )
            }
            item(key = "favorites") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(favoriteSongs.take(20), key = { it.id }) { song ->
                        HomeSongCard(
                            song = song,
                            onClick = { onPlaySong(song) }
                        )
                    }
                }
            }
        }

        // 5.5 智能电台（F2-3 首页列表化：随心听式——展示推荐批次，点卡片播歌，"换一批"重新生成）
        if (onLoadSmartRadio != null && smartRadioBatch.isNotEmpty()) {
            item(key = "smart_radio_header") {
                SectionHeader(
                    title = stringResource(R.string.home_smart_radio),
                    count = smartRadioBatch.size,
                    actionLabel = stringResource(R.string.home_smart_radio_next),
                    onAction = onLoadSmartRadio
                )
            }
            item(key = "smart_radio") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    itemsIndexed(smartRadioBatch, key = { _, s -> s.id }) { index, song ->
                        HomeSongCard(
                            song = song,
                            onClick = { onPlaySmartRadioAt(index) }
                        )
                    }
                }
            }
        }

        // 6. 天气信息（加载/错误/正常三种状态，与网络音乐页面统一）
        item(key = "weather_card") {
            HomeWeatherCard(
                weatherData = weatherData,
                isLoading = weatherLoading,
                errorMessage = weatherError,
                onClick = onNavigateToWeatherRadio
            )
        }

        // 底部间距
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 欢迎区域 + 统计卡片
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WelcomeSection(
    isConnected: Boolean,
    isLibraryLoading: Boolean,
    serverDisplayName: String,
    dashboardData: HomeDashboardData,
    weatherData: WeatherData?
) {
    Column {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NasMusicColors.Primary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\u266A", color = NasMusicColors.TextPrimary, fontSize = FontSize.title())
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = if (isConnected) serverDisplayName.ifBlank { stringResource(R.string.app_name) }
                           else stringResource(R.string.app_name),
                    color = NasMusicColors.TextPrimary,
                    fontSize = FontSize.display(),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isLibraryLoading) stringResource(R.string.common_loading)
                           else if (!isConnected) stringResource(R.string.common_not_connected)
                           else stringResource(R.string.home_ready),
                    color = if (isConnected) NasMusicColors.Success else NasMusicColors.TextSecondary,
                    fontSize = FontSize.body()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 统计卡片：竖屏改 2×2 网格（4 张横排竖屏必溢出，方案 §4.1）
        if (isConnected) {
            val stats = listOf(
                stringResource(R.string.home_album_count) to "${dashboardData.totalAlbums}",
                stringResource(R.string.home_song_count) to "${dashboardData.totalSongs}",
                stringResource(R.string.home_artist_count) to "${dashboardData.totalArtists}",
                stringResource(R.string.home_playlist_count) to "${dashboardData.totalPlaylists}",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stats.forEach { (label, value) ->
                    Box(modifier = Modifier.weight(1f)) {
                        StatCard(label = label, value = value)
                    }
                }
            }
        }
    }
}

/**
 * 统计小卡片
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatCard(
    label: String,
    value: String
) {
    // L11 修复（2026-10-06，代码审查报告 §5）：StatCard 是纯展示卡片——原实现借
    // FocusableSurface 的 clickable 获得焦点（D-Pad 会停在一个无行为的可聚焦节点上）；
    // 改为普通 Box 容器，视觉不变、不再参与焦点搜索。
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NasMusicColors.Surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = FontSize.display(),
                fontWeight = FontWeight.Bold,
                color = NasMusicColors.Primary
            )
            Text(
                text = label,
                fontSize = FontSize.body(),
                color = NasMusicColors.TextSecondary
            )
        }
    }
}

/**
 * 快捷操作行
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickActionRow(
    onNavigateToLibrary: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToQueue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            QuickActionButton(
                label = stringResource(R.string.nav_library),
                icon = Icons.Outlined.LibraryMusic,
                onClick = onNavigateToLibrary
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            QuickActionButton(
                label = stringResource(R.string.common_search),
                icon = Icons.Outlined.Search,
                onClick = onNavigateToSearch
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            QuickActionButton(
                label = stringResource(R.string.nav_queue),
                icon = Icons.Outlined.QueueMusic,
                onClick = onNavigateToQueue
            )
        }
    }
}

/**
 * 快捷操作按钮
 *
 * 2026-10-28 用户定稿：图标从 **emoji（📂 🔍 🔀）改为极简线框图标**
 * （[Icons.Outlined.LibraryMusic] / [Search] / [QueueMusic]）。
 * emoji 是彩色字形，与播放页那套单色 Material 图标完全不在一个语汇里，
 * 且不同设备字体渲染差异大（部分电视会画成彩色立体图）。线框图标单色、
 * 粗细统一，跟随 [LocalFocusableContentColor] 变化（聚焦提亮）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.06f,
        animationDurationMs = 200,
        containerColor = NasMusicColors.Surface.copy(alpha = 0.5f),
        focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.2f),
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.TextPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = LocalFocusableContentColor.current,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = LocalFocusableContentColor.current,
                fontSize = FontSize.button(),
                fontWeight = FontWeight.Medium
            )
        }
    }
}


/**
 * 首页专辑卡片（方形封面）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null
) {
    FocusableSurface(
        onClick = onClick,
        // 首页海报放大：240dp（TV 与手机横屏一致）
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        animationDurationMs = 200,
        containerColor = NasMusicColors.Surface.copy(alpha = 0.6f),
        focusedContainerColor = NasMusicColors.FocusOverlay,
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            // 正方形封面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NasMusicColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!album.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "\u266A", color = LocalFocusableContentColor.current, fontSize = FontSize.display())
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = album.name,
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.body(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist.ifBlank { "\u2014" },
                color = LocalFocusableContentColor.current,
                fontSize = FontSize.small(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 首页歌曲卡片（竖版）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSongCard(
    song: Song,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        // 首页歌曲卡片宽度：240dp（与专辑卡片一致）
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        animationDurationMs = 200,
        containerColor = NasMusicColors.Surface.copy(alpha = 0.6f),
        focusedContainerColor = NasMusicColors.FocusOverlay,
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NasMusicColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!song.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "\u266A", color = LocalFocusableContentColor.current, fontSize = FontSize.display())
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 2026-10-30 用户定稿：标题完整展示 —— 短名静态、超长右→左无缝循环滚动
            MarqueeText(
                text = song.title,
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.body(),
                maxLines = 1,
                textAlign = TextAlign.Start
            )
            Text(
                text = song.artist.ifBlank { "\u2014" },
                color = LocalFocusableContentColor.current,
                fontSize = FontSize.small(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 当前播放小卡片（首页嵌入）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NowPlayingCard(
    song: Song,
    coverCandidates: List<String> = emptyList(),
    onClick: () -> Unit
) {
    // 优先使用 coverCandidates（含后端封面候选+回退），与原 coverUrl 兼容
    val effectiveCoverUrl = coverCandidates.firstOrNull() ?: song.coverUrl

    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.02f,
        animationDurationMs = 200,
        containerColor = NasMusicColors.FocusOverlay,
        focusedContainerColor = NasMusicColors.FocusOverlay,
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面缩略图
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NasMusicColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!effectiveCoverUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = effectiveCoverUrl,
                            contentScale = ContentScale.Crop
                        ),
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "\u266A", color = LocalFocusableContentColor.current, fontSize = FontSize.title())
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = song.title,
                    color = NasMusicColors.TextPrimary,
                    fontSize = FontSize.button(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Start
                )
                if (!song.artist.isNullOrBlank()) {
                    Text(
                        text = song.artist,
                        color = LocalFocusableContentColor.current,
                        fontSize = FontSize.body(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.nav_now_playing) + " \u25B6",
                color = NasMusicColors.Primary,
                fontSize = FontSize.body()
            )
        }
    }
}



/**
 * 天气小卡片（首页嵌入，与网络音乐页面 WeatherInfoCard 统一的状态处理）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeWeatherCard(
    weatherData: WeatherData?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onClick: () -> Unit = {}
) {
    val bgColor = if (weatherData != null) {
        Color(0x332DD4BF)
    } else {
        NasMusicColors.Surface.copy(alpha = 0.3f)
    }

    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.01f,
        animationDurationMs = 200,
        containerColor = NasMusicColors.Surface.copy(alpha = 0.3f),
        focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.1f),
        contentColor = NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.Primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading && weatherData == null) {
                Text(
                    text = stringResource(R.string.common_loading),
                    color = LocalFocusableContentColor.current,
                    fontSize = FontSize.button()
                )
            } else if (weatherData != null) {
                val mood = WeatherMood.fromWeather(weatherData)
                Text(text = mood.icon, fontSize = FontSize.displayLarge(), color = LocalFocusableContentColor.current)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "${weatherData.temperature.toInt()}\u00B0C",
                        fontSize = FontSize.title(),
                        fontWeight = FontWeight.Bold,
                        color = NasMusicColors.TextPrimary
                    )
                    Text(
                        text = if (weatherData.cityName.isBlank()) stringResource(R.string.weather_unknown_location) else "${weatherData.cityName}  \u00B7  ${weatherData.description}",
                        fontSize = FontSize.body(),
                        color = LocalFocusableContentColor.current
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "\uD83C\uDFB2 " + stringResource(R.string.home_weather_radio),
                    fontSize = FontSize.body(),
                    color = NasMusicColors.Primary
                )
            } else if (errorMessage != null) {
                Text(
                    text = "\u26A0 $errorMessage",
                    color = NasMusicColors.Warning,
                    fontSize = FontSize.body()
                )
            } else {
                Text(
                    text = stringResource(R.string.common_loading),
                    color = LocalFocusableContentColor.current,
                    fontSize = FontSize.button()
                )
            }
        }
    }
}
