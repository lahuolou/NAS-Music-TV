package com.lnh.nasmusictv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lnh.nasmusictv.data.model.PhotoScaleMode
import com.lnh.nasmusictv.player.SeparationMode
import com.lnh.nasmusictv.data.model.AppSettings
import com.lnh.nasmusictv.data.model.BaiduTokens
import com.lnh.nasmusictv.data.model.CloudDriveConfig
import com.lnh.nasmusictv.data.model.CloudDriveType
import com.lnh.nasmusictv.data.model.EqualizerPreset
import com.lnh.nasmusictv.data.model.LocalPlaylist
import com.lnh.nasmusictv.data.model.NetworkFavoriteItem
import com.lnh.nasmusictv.data.model.NetworkSource
import com.lnh.nasmusictv.data.model.PlayMode
import com.lnh.nasmusictv.data.model.SearchHistoryItem
import com.lnh.nasmusictv.data.model.ServerConfig
import com.lnh.nasmusictv.data.model.VisualizerTheme
import com.lnh.nasmusictv.data.model.VisualQuality
import com.lnh.nasmusictv.data.model.Song
import com.lnh.nasmusictv.util.AppLog
import com.lnh.nasmusictv.util.CryptoUtils
import com.lnh.nasmusictv.util.LogStore
import com.lnh.nasmusictv.visualizer.photo.PhotoTransitionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.lnh.nasmusictv.data.model.NetworkEndpointDefaults
import com.lnh.nasmusictv.data.model.PlaylistDefaults
/**
 * 应用偏好存储
 * 使用 DataStore 持久化服务器配置与通用设置
 *
 * 构造期为 `internal`：生产代码一律走 [getInstance] 单例（DataStore 要求同一文件
 * 只能有一个实例）；单元测试通过 internal 构造器为每个用例构造独立实例，避免
 * 单例跨用例串扰（Robolectric 每个用例有独立 dataDir，共享单例会导致 DataStore
 * 文件锁冲突与标记位污染）。
 */
class AppPreferences internal constructor(private val context: Context) {

    // =====================================================================
    // R-4 门面子域（键不迁移只搬访问器，DataStore 单例不变；调用方渐进迁移，
    // 旧 API 保留至全部调用点迁移后再 @Deprecated）
    // =====================================================================
    val server: ServerPrefs by lazy { ServerPrefs(this) }
    val player: PlayerPrefs by lazy { PlayerPrefs(this) }
    val lyrics: LyricsPrefs by lazy { LyricsPrefs(this) }
    val network: NetworkMusicPrefs by lazy { NetworkMusicPrefs(this) }
    val baidu: BaiduPrefs by lazy { BaiduPrefs(this) }
    val download: DownloadPrefs by lazy { DownloadPrefs(this) }
    val weather: WeatherPrefs by lazy { WeatherPrefs(this) }
    val visualizer: VisualizerPrefs by lazy { VisualizerPrefs(this) }
    val history: HistoryPrefs by lazy { HistoryPrefs(this) }
    val playlist: PlaylistPrefs by lazy { PlaylistPrefs(this) }
    val queue: QueuePrefs by lazy { QueuePrefs(this) }
    val languagePrefs: LanguagePrefs by lazy { LanguagePrefs(this, context) }
    val backup: BackupPrefs by lazy { BackupPrefs(this) }
    /** 照片墙域（§7.3，17 个字段；进 AppSettings 备份 JSON） */
    val photoWall: PhotoWallPrefs by lazy { PhotoWallPrefs(this) }
    val settings: GeneralSettingsPrefs by lazy { GeneralSettingsPrefs(this) }
    /** 应用内自动更新偏好域（开关 / 已跳过版本 / 上次检查时间） */
    val update: UpdatePrefs by lazy { UpdatePrefs(this) }

    companion object {
        private const val TAG = "AppPreferences"

        /** F2-6：音质档位常量（Meting br 值；AUTO 由 BandwidthEstimator 决策） */
        const val QUALITY_TIER_AUTO = 0
        const val QUALITY_TIER_LOSSLESS = 999
        const val QUALITY_TIER_HIGH = 320
        /** 多码率：补齐 192 档 */
        const val QUALITY_TIER_GOOD = 192
        const val QUALITY_TIER_STANDARD = 128



        /** 照片墙转场时长范围（ms，§7.3：300–2000） */

        /** 照片墙停留时长范围（ms，§7.3：3000–30000；默认 8000） */


        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by lazy {
        PreferenceDataStoreFactory.create {
            File(context.filesDir, "datastore/nas_music_tv.preferences_pb")
        }
    }

    // F2-1：月度播放统计仓库（依赖本类 dataStore/gson，recordPlayWithSong 原子联动写入）
    internal val playStatsRepo by lazy { com.lnh.nasmusictv.data.stats.PlayStatsRepository(this) }

    /** F2-1：供 PlayStatsRepository 读取的 DataStore 数据流（包内可见） */
    internal fun dataStoreData() = dataStore.data

    /** F2-1：供 PlayStatsRepository 复用的 Gson 实例（包内可见） */
    internal fun gson(): Gson = gson

    /**
     * R-4 实现下移：供子 Prefs 直接写共享 DataStore（DataStore 单例约束不变）。
     */
    internal suspend fun prefsEdit(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) =
        dataStore.edit(transform)

    /** R-4 实现下移：供子 Prefs 读取共享 DataStore 数据流（等价 dataStoreData()） */
    internal val prefsData: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences>
        get() = dataStore.data

    /** R-4 实现下移：供子 Prefs 复用 Gson（等价 gson()） */
    internal fun prefsGson(): Gson = gson

    /**
     * 本机是否为电视 —— 决定「外接存储」开关的**平台相关默认值**（§6.8：电视 `true` / 手机 `false`）。
     *
     * ⚠️ 与 `NasMusicApp` 的 `isTVDevice` / `ui/components/FocusableSurface.isTVDevice()`
     * 用**同一套判据**（`android.software.leanback` / `android.hardware.type.television`），
     * 这里只是不需要 Composable 与 Activity Context 而已。三处判据必须一致，
     * 否则会出现「按电视给的默认值、按手机渲染的设置页」这类错位。
     *
     * ⚠️ `by lazy`：`hasSystemFeature` 在 API 22 上可能是一次 binder 调用，
     * 而本判定在 `appSettings` 每次发射时都会被读到（设备类型进程内不变，缓存安全）。
     */
    private val isTelevisionDevice: Boolean by lazy {
        context.packageManager.run {
            hasSystemFeature("android.software.leanback") ||
                hasSystemFeature("android.hardware.type.television")
        }
    }

    // =====================================================================
    // R-7（方案 2026-09）：
    // 1. 语言键双写（SharedPreferences 镜像 + DataStore 事实源）——
    //    attachBaseContext 只读镜像，零 IO、无 runBlocking（第一类修复）。
    // 2. provider 类键的 @Volatile 内存镜像（DataStore Flow 常驻收集更新）——
    //    get*Sync 读镜像，调用点零改动（第三类修复，路 A）。
    // =====================================================================


    // ---- provider 键内存镜像（Application scope 常驻收集更新）----
    @Volatile private var cachedMusicSource: String = com.lnh.nasmusictv.data.model.MusicSource.DEFAULT_API_KEY
    @Volatile private var cachedDefaultNetworkSource: String = NetworkSource.DEFAULT.key
    /** F2-6：音质档位镜像（默认 AUTO） */
    @Volatile private var cachedQualityTier: Int = QUALITY_TIER_AUTO
    @Volatile private var cachedJamendoClientId: String = ""
    @Volatile private var cachedMetingApiBaseUrl: String = NetworkEndpointDefaults.DEFAULT_METING_BASE_URL
    @Volatile private var cachedMvApiBaseUrl: String = NetworkEndpointDefaults.DEFAULT_MV_API_BASE_URL
    /** 高质量分离模型自定义下载 URL 镜像（空=用内置默认候选列表） */
    @Volatile private var cachedModelDownloadUrl: String = ""
    /** F-7：天气 API Key 镜像（加密键解密后的值；WeatherRadioViewModel.fetchWeather 同步读） */
    @Volatile private var cachedLyricsKugouBaseUrl: String = com.lnh.nasmusictv.lyrics.LyricsNetworkProvider.DEFAULT_KUGOU_BASE_URL
    @Volatile private var cachedLyricsNeteaseBaseUrl: String = com.lnh.nasmusictv.lyrics.LyricsNetworkProvider.DEFAULT_NETEASE_BASE_URL
    /** 日志页级别过滤（设备本地偏好，默认「全部」） */
    @Volatile private var cachedLogsMinLevel: String = LogStore.LEVEL_DEBUG

    /** 镜像已启动收集标志（ensureMirrorScopeLoaded 只执行一次） */
    @Volatile private var mirrorStarted = false
    private val mirrorLock = Any()

    /**
     * 启动 provider 键镜像收集（由 NasMusicApp.onCreate 注入 applicationScope 调用，仅一次）。
     * 设置页改动 → DataStore 写入 → Flow 发射 → @Volatile 镜像更新 → get*Sync 立即生效。
     */
    fun startProviderMirrors(scope: kotlinx.coroutines.CoroutineScope) {
        synchronized(mirrorLock) {
            if (mirrorStarted) return
            mirrorStarted = true
        }
        // F-7：先做天气 Key 明文→加密一次性迁移（幂等），镜像再从加密键读
        scope.launch { weather.startMirrors(scope) }
        scope.launch {
            dataStore.data.map { it[keyMusicSource] ?: com.lnh.nasmusictv.data.model.MusicSource.DEFAULT_API_KEY }
                .distinctUntilChanged().collect { cachedMusicSource = it }
        }
        scope.launch {
            dataStore.data.map { it[keyDefaultNetworkSource] ?: NetworkSource.DEFAULT.key }
                .distinctUntilChanged().collect { cachedDefaultNetworkSource = NetworkSource.fromKey(it)?.key ?: it }
        }
        // F2-6：音质档位镜像（qualityTierProvider 同步读，注册进 MetingApiService）
        scope.launch {
            dataStore.data.map { it[keyQualityTier] ?: QUALITY_TIER_AUTO }
                .distinctUntilChanged().collect { cachedQualityTier = it }
        }
        scope.launch {
            dataStore.data.map { it[keyJamendoClientId] ?: "" }
                .distinctUntilChanged().collect { cachedJamendoClientId = it }
        }
        scope.launch {
            dataStore.data.map { endpointOrDefault(it[keyMetingApiBaseUrl], NetworkEndpointDefaults.DEFAULT_METING_BASE_URL) }
                .distinctUntilChanged().collect { cachedMetingApiBaseUrl = it }
        }
        scope.launch {
            dataStore.data.map { endpointOrDefault(it[keyMvApiBaseUrl], NetworkEndpointDefaults.DEFAULT_MV_API_BASE_URL) }
                .distinctUntilChanged().collect { cachedMvApiBaseUrl = it }
        }
        scope.launch {
            dataStore.data.map { it[keyLyricsKugouBaseUrl] ?: com.lnh.nasmusictv.lyrics.LyricsNetworkProvider.DEFAULT_KUGOU_BASE_URL }
                .distinctUntilChanged().collect { cachedLyricsKugouBaseUrl = it }
        }
        scope.launch {
            dataStore.data.map { it[keyLyricsNeteaseBaseUrl] ?: com.lnh.nasmusictv.lyrics.LyricsNetworkProvider.DEFAULT_NETEASE_BASE_URL }
                .distinctUntilChanged().collect { cachedLyricsNeteaseBaseUrl = it }
        }
        scope.launch {
            dataStore.data.map { LogStore.normalizeLevel(it[keyLogsMinLevel] ?: LogStore.LEVEL_DEBUG) }
                .distinctUntilChanged().collect { cachedLogsMinLevel = it }
        }
        scope.launch {
            dataStore.data.map { it[keyModelDownloadUrl] ?: "" }
                .distinctUntilChanged().collect { cachedModelDownloadUrl = it }
        }
    }


    // --- 服务器配置 ---
    /**
     * 访问码 / 安全码（飞牛 fnOS 外网访问码）。**加密存储**——它与密码同级，
     * 明文落盘等于把访问凭证写进 prefs 文件。
     */

    // --- 通用设置 ---
    private val keyMetingApiBaseUrl = stringPreferencesKey("settings_meting_api_base_url")
    private val keyMvApiBaseUrl = stringPreferencesKey("settings_mv_api_base_url")
    private val keyModelDownloadUrl = stringPreferencesKey("settings_model_download_url")
    private val keyLyricsKugouBaseUrl = stringPreferencesKey("settings_lyrics_kugou_base_url")
    private val keyLyricsNeteaseBaseUrl = stringPreferencesKey("settings_lyrics_netease_base_url")
    private val keyJamendoClientId = stringPreferencesKey("settings_jamendo_client_id")




    // --- 上次播放队列（序列化为 JSON，streamUrl 置空不持久化）---

    // --- 天气电台设置 ---
    /**
     * F-7：加密存储键（OpenWeatherMap key 属付费资源凭证，泄露可被刷量；
     * 与服务器密码/百度 token 一致走 CryptoUtils AES-GCM。旧明文键保留做一次性迁移读取）。
     */

    // --- 封面滤镜设置（Phase 5） ---

    // --- 频谱显示设置 ---

    // --- 照片墙（§7.3，17 个字段）---

    // --- 全局字体字号调整 ---

    // --- 首次曲库快捷键提示 ---
    private val keyShowLibraryShortcutHint = booleanPreferencesKey("show_library_shortcut_hint")

    // --- 网络音乐平台来源 ---
    private val keyMusicSource = stringPreferencesKey("music_source")

    // --- B-4 均衡器 ---


    // =====================================================================
    // 运行日志：级别过滤（设备本地偏好，**不进 AppSettings 备份 JSON**）
    // 与已移除的屏幕方向同理：这是「这台设备上想看到多少日志」的查看偏好，
    // 恢复到别的设备上没有意义。取值见 LogStore.LEVEL_*，默认 D（全部）。
    // =====================================================================
    private val keyLogsMinLevel = stringPreferencesKey("settings_logs_min_level")

    /** 日志页级别过滤（响应式；供 MainViewModel 在冷启动时补齐镜像尚未发射的窗口）。 */
    val logsMinLevel: Flow<String> = dataStore.data.map {
        LogStore.normalizeLevel(it[keyLogsMinLevel] ?: LogStore.LEVEL_DEBUG)
    }

    /** 同步读日志页级别过滤（LOGS 分区渲染时用，读 @Volatile 镜像）。 */
    fun getLogsMinLevelSync(): String = cachedLogsMinLevel

    suspend fun setLogsMinLevel(level: String) {
        dataStore.edit { it[keyLogsMinLevel] = LogStore.normalizeLevel(level) }
    }

    // --- 首次曲库快捷键提示 Flow ---
    val showLibraryShortcutHint: Flow<Boolean> = dataStore.data.map { it[keyShowLibraryShortcutHint] ?: true }

    suspend fun setShowLibraryShortcutHint(show: Boolean) {
        dataStore.edit { it[keyShowLibraryShortcutHint] = show }
    }


    // --- K 歌模式：升降调 & 变速（全局记忆）---
    private val keyPitchSemitones = intPreferencesKey("k_pitch_semitones")
    private val keyPlaybackSpeed = doublePreferencesKey("k_playback_speed")

    val pitchSemitones: Flow<Int> = dataStore.data.map { it[keyPitchSemitones] ?: 0 }
    val playbackSpeed: Flow<Double> = dataStore.data.map { it[keyPlaybackSpeed] ?: 1.0 }

    suspend fun setPitchSemitones(semitones: Int) {
        dataStore.edit { it[keyPitchSemitones] = semitones }
    }

    suspend fun setPlaybackSpeed(speed: Double) {
        dataStore.edit { it[keyPlaybackSpeed] = speed }
    }

    // --- 伴奏分离模式（快速/高质量）---
    private val keySeparationMode = stringPreferencesKey("k_separation_mode")

    // 分离模式枚举在 player 层顶层（R-5 上提）。2026-10-06 删除此处兼容 typealias：
    // lint 的 K2 分析器在 CI 上对 member typealias 稳定崩溃（Inconsistency in the cache），
    // 消费方已全部直连 com.lnh.nasmusictv.player.SeparationMode。

    val separationMode: Flow<SeparationMode> = dataStore.data.map { prefs ->
        val value = prefs[keySeparationMode] ?: SeparationMode.FAST.value
        SeparationMode.entries.find { it.value == value } ?: SeparationMode.FAST
    }

    suspend fun setSeparationMode(mode: SeparationMode) {
        dataStore.edit { it[keySeparationMode] = mode.value }
    }


    // --- 网盘配置（按 CloudDriveType 存取，JSON 序列化 CloudDriveConfig；token 字段加密）---
    private val keyCloudDriveConfig = stringPreferencesKey("cloud_drive_configs")

    private val gson = Gson()


    // --- AppSettings Flow ---
    val appSettings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            darkTheme = prefs[keyDarkTheme] ?: true,
            animationsEnabled = prefs[keyAnimations] ?: true,
            autoPlayNext = prefs[keyAutoPlayNext] ?: true,
            defaultPlayMode = PlayMode.fromOrdinal(prefs[keyPlayMode] ?: 0),
            cacheLyrics = prefs[keyCacheLyrics] ?: true,
            cacheCover = prefs[keyCacheCover] ?: true,
            defaultNetworkSource = prefs[keyDefaultNetworkSource]?.let { NetworkSource.fromKey(it) ?: NetworkSource.fromName(it) } ?: NetworkSource.DEFAULT,
            metingApiBaseUrl = endpointOrDefault(prefs[keyMetingApiBaseUrl], NetworkEndpointDefaults.DEFAULT_METING_BASE_URL),
            mvApiBaseUrl = endpointOrDefault(prefs[keyMvApiBaseUrl], NetworkEndpointDefaults.DEFAULT_MV_API_BASE_URL),
            lyricsKugouBaseUrl = endpointOrDefault(prefs[keyLyricsKugouBaseUrl], com.lnh.nasmusictv.lyrics.LyricsNetworkProvider.DEFAULT_KUGOU_BASE_URL),
            lyricsNeteaseBaseUrl = endpointOrDefault(prefs[keyLyricsNeteaseBaseUrl], com.lnh.nasmusictv.lyrics.LyricsNetworkProvider.DEFAULT_NETEASE_BASE_URL),
            visualizerTheme = VisualizerTheme.fromKey(prefs[keyVisualizerTheme]),
            visualizerQuality = VisualQuality.fromKey(prefs[keyVisualizerQuality]),
            fontAdjustment = prefs[keyFontAdjustment] ?: 0,
            modelDownloadUrl = prefs[keyModelDownloadUrl] ?: "",
            language = prefs[keyLanguage] ?: "system",
            downloadEnabled = prefs[keyDownloadEnabled] ?: true,
            autoDownloadOnPlay = prefs[keyAutoDownloadOnPlay] ?: false,
            autoDownloadLimit = prefs[keyAutoDownloadLimit] ?: 50,
            downloadLocation = prefs[keyDownloadLocation] ?: "INTERNAL",
            localMusicEnabled = prefs[keyLocalMusicEnabled] ?: true,
            // ── 照片墙（§7.3）──
            // ⚠️ 「外接存储」默认值**按平台**：电视 true / 手机 false（§6.8）。
            //   其余字段的平台默认值相同，直接写字面量。
            photoWallGalleryEnabled = prefs[keyPhotoWallGalleryEnabled] ?: false,
            photoWallExternalEnabled = prefs[keyPhotoWallExternalEnabled] ?: isTelevisionDevice,
            photoWallJellyfinEnabled = prefs[keyPhotoWallJellyfinEnabled] ?: false,
            photoWallSourceBalance = prefs[keyPhotoWallSourceBalance] ?: false,
            photoWallDirUri = prefs[keyPhotoWallDirUri] ?: "",
            photoWallCommonDirsOnly = prefs[keyPhotoWallCommonDirsOnly] ?: true,
            photoWallFacesOnly = prefs[keyPhotoWallFacesOnly] ?: false,
            photoWallFaceScanDone = prefs[keyPhotoWallFaceScanDone] ?: false,
            photoWallRandomTransition = prefs[keyPhotoWallRandomTransition] ?: true,
            // 两个枚举都走各自 fromKey()（**永不返回 null**，见 §10.172 的 Gson 枚举坑）
            photoWallFixedTransition = PhotoTransitionId.fromKey(prefs[keyPhotoWallFixedTransition]),
            photoWallTransitionMs = prefs[keyPhotoWallTransitionMs] ?: 700,
            photoWallHoldMs = prefs[keyPhotoWallHoldMs] ?: 8000,
            photoWallScaleMode = PhotoScaleMode.fromKey(prefs[keyPhotoWallScaleMode]),
            photoWallKenBurns = prefs[keyPhotoWallKenBurns] ?: true,
            photoWallAudioReactive = prefs[keyPhotoWallAudioReactive] ?: false,
            photoWallPulseZoom = prefs[keyPhotoWallPulseZoom] ?: true,
            photoWallBreathe = prefs[keyPhotoWallBreathe] ?: true
        )
    }

    // --- 歌词显示设置 ---
    private val keyLyricsFontScale = doublePreferencesKey("lyrics_font_scale")

    val lyricsFontScale: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[keyLyricsFontScale] ?: 0.7).toFloat()
    }

    suspend fun setLyricsFontScale(scale: Float) {
        dataStore.edit { it[keyLyricsFontScale] = scale.coerceIn(0.7f, 1.6f).toDouble() }
    }


    /**
     * P2 修复（2026-09-22 审查）：网络端点校验。返回 null = 非空白但非法
     * （scheme 非 http/https、无 host、超长）；空串 = 输入本身为空白。
     * 非法值由调用方决定回退（设置页保持旧值 / 备份恢复回落默认），杜绝
     * 任意字符串直接成为请求端点（网络功能静默瘫痪 / 备份重定向流量）。
     */
    private fun normalizeEndpointUrl(raw: String): String? {
        val u = raw.trim().trim('`', '\'', '"').trim()
        if (u.isEmpty()) return ""
        val hostPart = u.removePrefix("https://").removePrefix("http://").substringBefore('/')
        val valid = (u.startsWith("http://") || u.startsWith("https://")) &&
            u.length <= 500 &&
            hostPart.isNotBlank() &&
            (hostPart.contains('.') || hostPart.contains(':') || hostPart == "localhost")
        return if (valid) u else null
    }

    /**
     * 端点配置的**有效值**：空串/空白/非法存值一律回落 [default]。
     *
     * ⚠️ 空串在 DataStore 里表示「未定制」，不是端点本身。直接当端点用会拼出相对
     * 路径（`Expected URL scheme 'http' or 'https'`）——备份导入的空字段踩过这个坑
     * （真机日志：BilibiliMvService 的 `/x/web-interface/nav` 没有 host）。
     * 读取端做兜底而不是只堵写入端：历史上已写进去的空值也要能自愈。
     */
    private fun endpointOrDefault(stored: String?, default: String): String {
        val normalized = stored?.let { normalizeEndpointUrl(it) }
        return if (normalized.isNullOrEmpty()) default else normalized
    }

    suspend fun setMetingApiBaseUrl(url: String) {
        val normalized = normalizeEndpointUrl(url)
        if (url.isNotBlank() && normalized == null) {
            AppLog.w(TAG, "setMetingApiBaseUrl rejected invalid url: " + url.take(80))
            return
        }
        dataStore.edit {
            if (normalized.isNullOrEmpty()) it.remove(keyMetingApiBaseUrl)
            else it[keyMetingApiBaseUrl] = normalized
        }
        // 换端点＝换可播性判据的来源：被拒绝的写入走上面的 return，绝不进这里（终审 B3）
        runCatching { onPlayUrlCacheInvalidated?.invoke() }
            .onFailure { AppLog.w(TAG, "onPlayUrlCacheInvalidated failed: ${it.message}") }
    }

    suspend fun setMvApiBaseUrl(url: String) {
        val normalized = normalizeEndpointUrl(url)
        if (url.isNotBlank() && normalized == null) {
            AppLog.w(TAG, "setMvApiBaseUrl rejected invalid url: " + url.take(80))
            return
        }
        dataStore.edit {
            if (normalized.isNullOrEmpty()) it.remove(keyMvApiBaseUrl)
            else it[keyMvApiBaseUrl] = normalized
        }
    }

    // --- 网络音乐平台来源 ---

    /**
     * 同步获取当前音乐平台来源（用于 MetingApiService 的 serverProvider）
     * 在每次请求时同步读取，支持运行时切换平台。
     *
     * R-7 第三类修复（路 A）：读 @Volatile 内存镜像，无 IO 无阻塞。
     */
    fun getMusicSourceSync(): String = cachedMusicSource

    suspend fun setMusicSource(sourceKey: String) {
        dataStore.edit { it[keyMusicSource] = sourceKey }
        // 平台换了同一个 networkId 指的是另一首歌，旧判定必须随之消失（终审 B3）
        runCatching { onPlayUrlCacheInvalidated?.invoke() }
            .onFailure { AppLog.w(TAG, "onPlayUrlCacheInvalidated failed: ${it.message}") }
    }

    // --- Jamendo（CC 独立音乐）设置 ---
    suspend fun setJamendoClientId(id: String) {
        dataStore.edit { it[keyJamendoClientId] = id.trim() }
    }

    /**
     * 同步读取 Jamendo Client ID（用于 JamendoService 每次请求时读取）。
     * R-7 第三类修复（路 A）：读 @Volatile 内存镜像。
     */
    fun getJamendoClientIdSync(): String = cachedJamendoClientId

    // --- F2-5 跨曲交叉淡入淡出 ---
    private val keyCrossfadeEnabled = booleanPreferencesKey("settings_crossfade_enabled")
    private val keyCrossfadeDurationSec = intPreferencesKey("settings_crossfade_duration_sec")

    // --- F2-6 音质分级 ---
    /** 音质档位：AUTO(0，按带宽) / LOSSLESS(999) / HIGH(320) / STANDARD(128) */
    private val keyQualityTier = intPreferencesKey("settings_quality_tier")

    val crossfadeEnabled: Flow<Boolean> = dataStore.data.map { it[keyCrossfadeEnabled] ?: false }
    val crossfadeDurationSec: Flow<Int> = dataStore.data.map { it[keyCrossfadeDurationSec] ?: 4 }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[keyCrossfadeEnabled] = enabled }
    }

    suspend fun setCrossfadeDurationSec(sec: Int) {
        dataStore.edit { it[keyCrossfadeDurationSec] = sec.coerceIn(1, 12) }
    }

    // --- 应用内音量（0.0–1.0，独立于系统音量，DataStore 持久化） ---
    private val keyAppVolume = floatPreferencesKey("settings_app_volume")

    val appVolume: Flow<Float> = dataStore.data.map { it[keyAppVolume] ?: 1f }

    suspend fun setAppVolume(volume: Float) {
        dataStore.edit { it[keyAppVolume] = volume.coerceIn(0f, 1f) }
    }

    /** F2-6：音质档位（Meting br 参数；AUTO 时不传由 BandwidthEstimator 决策） */
    val qualityTier: Flow<Int> = dataStore.data.map { it[keyQualityTier] ?: QUALITY_TIER_AUTO }

    /**
     * 多码率：档位变化后的缓存失效回调（修 G5）。
     *
     * 由 NasMusicApp 注册为 `{ networkMusicManager.clearPlayUrlCache() }`。
     * 用回调而非直接引用 NetworkMusicManager，避免 data/prefs 反向依赖 backend/network
     * （与项目既有 provider 注入风格一致）。
     */
    @Volatile
    private var onQualityTierChanged: (() -> Unit)? = null

    /** 注册档位变化回调（仅 NasMusicApp 调用一次） */
    fun setOnQualityTierChanged(cb: () -> Unit) {
        onQualityTierChanged = cb
    }

    suspend fun setQualityTier(tier: Int) {
        dataStore.edit { it[keyQualityTier] = tier }
        // 直链是"音源 × 歌曲 × 码率"三元组绑定的时效性资源，档位一变旧链接语义全失效。
        // 缓存上限仅 500 条，全量清除代价可忽略（方案 §2.4）。
        runCatching { onQualityTierChanged?.invoke() }
            .onFailure { AppLog.w(TAG, "onQualityTierChanged failed: ${it.message}") }
    }

    /**
     * 终审 B3：直链缓存失效回调（正面直链 + 负面探测判定一起清）。
     *
     * 缓存 key 是 `networkSource:networkId:quality`，**不认识端点与平台**：
     * Meting 端点换掉、或平台从 netease 切到 qq 后，旧 key 上的 DEAD 判定会在 TTL 内
     * 套到新端点/新平台的另一首歌上（§4 的「换自建端点」补救最长 5 分钟无效）。
     * 与 [onQualityTierChanged] 同一去处，由 NasMusicApp 注册为
     * `{ networkMusicManager.clearPlayUrlCache() }`；同样用回调避免 data/prefs 反向依赖 backend/network。
     */
    @Volatile
    private var onPlayUrlCacheInvalidated: (() -> Unit)? = null

    /** 注册直链缓存失效回调（仅 NasMusicApp 调用一次） */
    fun setOnPlayUrlCacheInvalidated(cb: () -> Unit) {
        onPlayUrlCacheInvalidated = cb
    }

    /**
     * 同步获取当前默认网络源（用于 NetworkMusicManager 的 defaultSourceProvider）。
     * R-7 第三类修复（路 A）：读 @Volatile 内存镜像。
     */
    fun getDefaultNetworkSourceSync(): String = cachedDefaultNetworkSource

    /** F2-6：同步读取音质档位（MetingApiService qualityTierProvider 用） */
    fun getQualityTierSync(): Int = cachedQualityTier

    /**
     * 同步获取 Meting-API 端点 URL（用于 MetingApiService 的 baseUrlProvider）。
     * R-7 第三类修复（路 A）：读 @Volatile 内存镜像。
     */
    fun getMetingApiBaseUrlSync(): String = cachedMetingApiBaseUrl

    /**
     * 同步获取 MTV 视频搜索端点 URL（用于 BilibiliMvService 的 baseUrlProvider）。
     * R-7 第三类修复（路 A）：读 @Volatile 内存镜像。
     */
    fun getMvApiBaseUrlSync(): String = cachedMvApiBaseUrl

    /**
     * 同步获取高质量分离模型自定义下载 URL（用于 ModelDownloadManager 的 customUrlProvider）。
     * 空串表示使用内置默认候选列表（hf-mirror → huggingface）。
     */
    fun getModelDownloadUrlSync(): String = cachedModelDownloadUrl

    /** 设置模型自定义下载 URL（空串=恢复默认候选列表） */
    suspend fun setModelDownloadUrl(url: String) {
        val normalized = normalizeEndpointUrl(url)
        if (url.isNotBlank() && normalized == null) {
            AppLog.w(TAG, "setModelDownloadUrl rejected invalid url: " + url.take(80))
            return
        }
        dataStore.edit { it[keyModelDownloadUrl] = normalized.orEmpty() }
    }

    // --- 网络歌词端点（Kugou / Netease）---
    /** R-7 第三类修复（路 A）：读 @Volatile 内存镜像（原主线程急切求值 runBlocking） */
    fun getLyricsKugouBaseUrlSync(): String = cachedLyricsKugouBaseUrl

    /** R-7 第三类修复（路 A）：读 @Volatile 内存镜像（原主线程急切求值 runBlocking） */
    fun getLyricsNeteaseBaseUrlSync(): String = cachedLyricsNeteaseBaseUrl

    suspend fun setLyricsKugouBaseUrl(url: String) {
        val normalized = normalizeEndpointUrl(url)
        if (url.isNotBlank() && normalized == null) {
            AppLog.w(TAG, "setLyricsKugouBaseUrl rejected invalid url: " + url.take(80))
            return
        }
        dataStore.edit { it[keyLyricsKugouBaseUrl] = normalized.orEmpty() }
    }

    suspend fun setLyricsNeteaseBaseUrl(url: String) {
        val normalized = normalizeEndpointUrl(url)
        if (url.isNotBlank() && normalized == null) {
            AppLog.w(TAG, "setLyricsNeteaseBaseUrl rejected invalid url: " + url.take(80))
            return
        }
        dataStore.edit { it[keyLyricsNeteaseBaseUrl] = normalized.orEmpty() }
    }

    // --- 网络歌曲收藏 ---
    // --- 本地歌单（DataStore JSON，独立于 NAS 后端歌单）---

    // ========== 数据备份 ==========

    /**
     * 备份文件数据结构
     *
     * 注意：不包含敏感字段 —— 密码、API Token、天气 API Key 一律不导出。
     * 恢复后服务器需重新输入密码连接（isConnected=false）。
     */
    // ===================== 网盘配置（CloudDriveConfig，按 CloudDriveType 存取）=====================
    // 存储：keyCloudDriveConfig 存 JSON Map<type.key, CloudDriveConfigJson>，其中 tokens 的 accessToken/refreshToken 用 CryptoUtils 加密。

    /**
     * 同步读取某网盘配置。
     *
     * R-7 第四类：保留 runBlocking（通用同步入口，供非协程上下文兜底；禁止主线程协程内调用）。
     * T2（2026-09-14）第一批：外部 UI/App 调用点迁移到 baiduConfigFlow.first()；
     * 第二批：百度 token/配置便捷方法全部改为 suspend + baiduConfigFlow.first()，
     * BaiduOAuthClient/BaiduNetdiskService/BaiduMvFileService 调用方已同步迁移，本入口仅作通用兜底。
     */
    @androidx.annotation.WorkerThread
    fun getCloudDriveConfigSync(type: CloudDriveType): CloudDriveConfig? {
        return try {
            runBlocking(Dispatchers.IO) {
                val json = dataStore.data.first()[keyCloudDriveConfig] ?: return@runBlocking null
                loadCloudDriveConfigs(json)[type.key]
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "getCloudDriveConfigSync error", e)
            null
        }
    }

    /** 百度配置 Flow（修复 H-3：UI 订阅用，替代组合内 runBlocking 同步读） */
    val baiduConfigFlow: Flow<CloudDriveConfig> = dataStore.data.map { prefs ->
        loadCloudDriveConfigs(prefs[keyCloudDriveConfig] ?: "{}")[CloudDriveType.BAIDU.key]
            ?: CloudDriveConfig(CloudDriveType.BAIDU)
    }

    /** Jamendo Client ID Flow（修复 H-3：同上） */
    val jamendoClientIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[keyJamendoClientId] ?: ""
    }

    /** 异步保存某网盘配置（增量合并到现有 Map） */
    suspend fun saveCloudDriveConfig(config: CloudDriveConfig) {
        dataStore.edit { prefs ->
            val json = prefs[keyCloudDriveConfig] ?: "{}"
            val map = loadCloudDriveConfigs(json).toMutableMap()
            map[config.type.key] = config
            prefs[keyCloudDriveConfig] = saveCloudDriveConfigs(map)
        }
    }

    /** 同步保存（OAuth 客户端在非协程上下文调用；R-7 第四类：保留 runBlocking，禁止主线程协程内调用） */
    @androidx.annotation.WorkerThread
    fun saveCloudDriveConfigSync(config: CloudDriveConfig) {
        runBlocking(Dispatchers.IO) { saveCloudDriveConfig(config) }
    }

    // ---- 百度 token 便捷读写（加解密 accessToken/refreshToken；T2 第二批：suspend + baiduConfigFlow.first()）----

    /** 异步读取百度 token（解密） */
    suspend fun getBaiduTokens(): BaiduTokens? {
        val cfg = baiduConfigFlow.first()
        val t = cfg.tokens ?: return null
        return try {
            val decAt = CryptoUtils.decrypt(t.accessToken).ifBlank { return null }
            val decRt = CryptoUtils.decrypt(t.refreshToken).ifBlank { return null }
            AppLog.d(TAG, "getBaiduTokens: token decrypted (len=${decAt.length}), expiresAt=${t.expiresAt}")
            t.copy(accessToken = decAt, refreshToken = decRt)
        } catch (e: Exception) {
            AppLog.w(TAG, "getBaiduTokens decrypt error", e)
            null
        }
    }

    /** 异步保存百度 token（加密） */
    suspend fun saveBaiduTokens(tokens: BaiduTokens) {
        val cfg = baiduConfigFlow.first().copy(
            tokens = tokens.copy(
                accessToken = CryptoUtils.encrypt(tokens.accessToken),
                refreshToken = CryptoUtils.encrypt(tokens.refreshToken)
            )
        )
        saveCloudDriveConfig(cfg)
    }

    /** 清除百度 token（登出/刷新失败降级用） */
    suspend fun clearBaiduTokens() {
        val cfg = baiduConfigFlow.first().copy(tokens = null)
        saveCloudDriveConfig(cfg)
    }

    // ---- 百度配置项便捷存取 ----

    suspend fun getBaiduEnabled(): Boolean = baiduConfigFlow.first().enabled
    suspend fun setBaiduEnabled(enabled: Boolean) =
        saveCloudDriveConfig(baiduConfigFlow.first().copy(enabled = enabled))
    suspend fun getBaiduMusicRootDir(): String {
        val saved = baiduConfigFlow.first().musicRootDir
        val appDir = com.lnh.nasmusictv.backend.network.baidu.BaiduNetdiskConfig.APP_DIR
        // 空白或不在沙盒 /apps/NAS-Music-TV 下的旧路径，自动纠正为沙盒目录
        val corrected = saved.ifBlank { appDir }
        if (corrected != appDir && !corrected.startsWith(appDir)) {
            com.lnh.nasmusictv.util.AppLog.w("AppPreferences", "musicRootDir='$corrected' outside sandbox, resetting to $appDir")
            setBaiduMusicRootDir(appDir)
            return appDir
        }
        return corrected
    }
    suspend fun setBaiduMusicRootDir(dir: String) =
        saveCloudDriveConfig(baiduConfigFlow.first().copy(musicRootDir = dir))
    suspend fun getBaiduMvDir(): String? {
        val saved = baiduConfigFlow.first().mvDir
        val appDir = com.lnh.nasmusictv.backend.network.baidu.BaiduNetdiskConfig.APP_DIR
        // MV 目录不在沙盒下则视为无效，返回 null（调用方会 fallback 到 musicRootDir）
        if (saved != null && saved.isNotBlank() && !saved.startsWith(appDir)) {
            com.lnh.nasmusictv.util.AppLog.w("AppPreferences", "mvDir='$saved' outside sandbox, clearing")
            setBaiduMvDir(null)
            return null
        }
        return saved
    }
    suspend fun setBaiduMvDir(dir: String?) =
        saveCloudDriveConfig(baiduConfigFlow.first().copy(mvDir = dir))

    // ---- 内部：CloudDriveConfig Map <-> JSON（tokens 加密）----

    private fun loadCloudDriveConfigs(json: String): Map<String, CloudDriveConfig> {
        return try {
            val type = object : TypeToken<Map<String, CloudDriveConfig>>() {}.type
            val raw: Map<String, CloudDriveConfig> = gson.fromJson(json, type) ?: emptyMap()
            raw
        } catch (e: Exception) {
            AppLog.w(TAG, "loadCloudDriveConfigs parse error", e)
            emptyMap()
        }
    }

    private fun saveCloudDriveConfigs(map: Map<String, CloudDriveConfig>): String =
        try { gson.toJson(map) } catch (e: Exception) { "{}" }

    data class BackupData(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val serverConfig: ServerConfig? = null,
        val appSettings: AppSettings? = null,
        val networkFavorites: List<NetworkFavoriteItem> = emptyList(),
        val localPlaylists: List<LocalPlaylist> = emptyList(),
        // v3: 歌单导入历史与 URL 可达性状态（旧备份文件恢复时用默认值）
        val playlistImportHistory: List<com.lnh.nasmusictv.data.model.PlaylistImportHistoryItem> = emptyList(),
        val songReachability: Map<String, ReachabilityEntry> = emptyMap(),
        val lastQueue: LastQueueData? = null,
        val recentSongIds: List<String> = emptyList(),
        val recentSongObjects: List<Song> = emptyList(),
        val playCounts: Map<String, Int> = emptyMap(),
        val playRecords: List<com.lnh.nasmusictv.data.model.PlayRecord> = emptyList(),
        val searchHistory: List<SearchHistoryItem> = emptyList(),
        val equalizerPreset: EqualizerPreset? = null,
        val equalizerBands: List<Float> = emptyList(),
        // v2: 新增备份项（旧备份文件恢复时用默认值）
        val mvCacheEntries: List<com.lnh.nasmusictv.data.model.MvCacheEntry> = emptyList(),
        val coverFilterEnabled: Boolean = false,
        val coverFilterBlurRadius: Double = 8.0,
        val coverFilterDarkOverlay: Double = 0.3,
        val musicSource: String = "",
        val lyricsFontScale: Double = 0.7
    )

    /**
     * 备份快照专用：读取搜索历史（实现已下移到 HistoryPrefs，本处是横切备份机制的内部读取）。
     */
    private suspend fun internalReadSearchHistory(): List<SearchHistoryItem> = try {
        dataStore.data.first().let { data ->
            val json = data[keySearchHistory] ?: "[]"
            gson.fromJson<List<SearchHistoryItem>>(json, object : com.google.gson.reflect.TypeToken<List<SearchHistoryItem>>() {}.type)
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 导出备份数据（敏感字段已排除：密码、API Token、访问码/安全码、天气 API Key）
     */
    suspend fun exportBackupData(): BackupData {
        val config = server.serverConfig.first()
        val ds = dataStore.data.first()
        return BackupData(
            // accessCode 是凭据（飞牛访问码），与密码/token 一并剔除，不进备份文件
            serverConfig = if (config.baseUrl.isNotBlank()) {
                config.copy(apiToken = "", password = "", accessCode = "", isConnected = false)
            } else null,
            appSettings = appSettings.first(),
            networkFavorites = history.getNetworkFavorites(),
            localPlaylists = playlist.getLocalPlaylists(),
            playlistImportHistory = runCatching {
                dataStore.data.first().let { prefs ->
                    gson.fromJson<List<com.lnh.nasmusictv.data.model.PlaylistImportHistoryItem>>(
                        prefs[keyPlaylistImportHistory] ?: "[]",
                        object : TypeToken<List<com.lnh.nasmusictv.data.model.PlaylistImportHistoryItem>>() {}.type
                    ) ?: emptyList()
                }
            }.getOrDefault(emptyList()),
            songReachability = playlist.getSongReachability().filterValues { entry ->
                System.currentTimeMillis() - entry.checkedAt < songReachabilityWindowMs
            },
            lastQueue = queue.getLastQueue(),
            recentSongIds = history.getRecentSongIds(),
            recentSongObjects = history.getRecentSongObjects(),
            playCounts = history.playCounts.first(),
            playRecords = history.getPlayRecords(),
            searchHistory = internalReadSearchHistory(),
            equalizerPreset = visualizer.equalizerPreset.first(),
            equalizerBands = visualizer.equalizerBands.first(),
            // v2: 补全之前遗漏的设置项
            coverFilterEnabled = ds[keyCoverFilterEnabled] ?: false,
            coverFilterBlurRadius = ds[keyCoverFilterBlurRadius] ?: 8.0,
            coverFilterDarkOverlay = ds[keyCoverFilterDarkOverlay] ?: 0.3,
            musicSource = ds[keyMusicSource] ?: "",
            lyricsFontScale = ds[keyLyricsFontScale] ?: 0.7
        )
    }

    /**
     * 导入备份数据并恢复至 DataStore
     * 服务器密码/API Token 不在备份中，恢复后 isConnected=false，需重新输入密码连接
     */
    suspend fun importBackupData(data: BackupData) {
        data.serverConfig?.let { config ->
            // 访问码与密码/token 同理：备份里不带凭据（防止外部备份文件注入访问码）
            server.saveServerConfig(
                config.copy(
                    apiToken = "",
                    password = "",
                    accessCode = "",
                    rootPath = config.rootPath.orEmpty().ifBlank { "/" },
                    smbShare = config.smbShare.orEmpty(),
                    smbDomain = config.smbDomain.orEmpty(),
                    isConnected = false
                )
            )
        }
        data.appSettings?.let { settings ->
            dataStore.edit { prefs ->
                prefs[keyDarkTheme] = settings.darkTheme
                prefs[keyAnimations] = settings.animationsEnabled
                prefs[keyAutoPlayNext] = settings.autoPlayNext
                prefs[keyPlayMode] = settings.defaultPlayMode.ordinalOrDefault()
                prefs[keyCacheLyrics] = settings.cacheLyrics
                prefs[keyCacheCover] = settings.cacheCover
                prefs[keyDefaultNetworkSource] = settings.defaultNetworkSource.keyOrDefault()
                // P2 修复（2026-09-22 审查）：恢复路径同样过端点校验——恶意/异常
                // 备份不能把请求流量重定向到任意字符串端点；非法值回落默认。
                // ⚠️ 空串同样回落默认（[endpointOrDefault]）：备份里未定制字段就是空串，
                //    原样写入会让服务端拼出相对 URL（真机日志的 MV 空端点）。
                prefs[keyMetingApiBaseUrl] =
                    endpointOrDefault(settings.metingApiBaseUrl, NetworkEndpointDefaults.DEFAULT_METING_BASE_URL)
                prefs[keyMvApiBaseUrl] =
                    endpointOrDefault(settings.mvApiBaseUrl, NetworkEndpointDefaults.DEFAULT_MV_API_BASE_URL)
                prefs[keyModelDownloadUrl] =
                    normalizeEndpointUrl(settings.modelDownloadUrl) ?: ""
                prefs[keyVisualizerTheme] = settings.visualizerTheme.nameOrDefault()
                prefs[keyVisualizerQuality] = settings.visualizerQuality.nameOrDefault()
                // ── 本地音乐总开关（§五）──
                prefs[keyLocalMusicEnabled] = settings.localMusicEnabled
                // ── 照片墙（§7.3）──
                // ⚠️ 枚举一律走 `nameOrDefault()`：`backupGson` 的容错适配器对
                //   `JsonToken.NULL` 仍会返回 null（只对「名字不认识」做回落），
                //   而 null 经反射写进非空字段后 `.name` 就是 NPE ⇒ 整份备份导入失败。
                //   数值字段一律 `coerceIn`：手改过的备份可能写出越界值。
                prefs[keyPhotoWallGalleryEnabled] = settings.photoWallGalleryEnabled
                prefs[keyPhotoWallExternalEnabled] = settings.photoWallExternalEnabled
                prefs[keyPhotoWallJellyfinEnabled] = settings.photoWallJellyfinEnabled
                prefs[keyPhotoWallSourceBalance] = settings.photoWallSourceBalance
                prefs[keyPhotoWallDirUri] = settings.photoWallDirUri
                prefs[keyPhotoWallCommonDirsOnly] = settings.photoWallCommonDirsOnly
                prefs[keyPhotoWallFacesOnly] = settings.photoWallFacesOnly
                prefs[keyPhotoWallFaceScanDone] = settings.photoWallFaceScanDone
                prefs[keyPhotoWallRandomTransition] = settings.photoWallRandomTransition
                prefs[keyPhotoWallFixedTransition] = settings.photoWallFixedTransition.nameOrDefault()
                prefs[keyPhotoWallTransitionMs] =
                    settings.photoWallTransitionMs.coerceIn(PHOTO_WALL_TRANSITION_MS_RANGE)
                prefs[keyPhotoWallHoldMs] =
                    settings.photoWallHoldMs.coerceIn(PHOTO_WALL_HOLD_MS_RANGE)
                prefs[keyPhotoWallScaleMode] = settings.photoWallScaleMode.nameOrDefault()
                prefs[keyPhotoWallKenBurns] = settings.photoWallKenBurns
                prefs[keyPhotoWallAudioReactive] = settings.photoWallAudioReactive
                prefs[keyPhotoWallPulseZoom] = settings.photoWallPulseZoom
                prefs[keyPhotoWallBreathe] = settings.photoWallBreathe
            }
        }
        dataStore.edit { prefs ->
            prefs[keyNetworkFavorites] = gson.toJson(data.networkFavorites)
            prefs[keyLocalPlaylists] = gson.toJson(data.localPlaylists)
            if (data.playlistImportHistory.isNotEmpty()) {
                prefs[keyPlaylistImportHistory] = gson.toJson(data.playlistImportHistory)
            }
            if (data.songReachability.isNotEmpty()) {
                // 恢复时一并恢复可达性标记：24h 窗口内不重测（避免恢复后全量 HEAD 风暴）
                val clean = data.songReachability.filterValues { entry ->
                    System.currentTimeMillis() - entry.checkedAt < songReachabilityWindowMs
                }
                if (clean.isNotEmpty()) {
                    prefs[keySongReachability] = gson.toJson(clean)
                }
            }
            prefs[keyRecentSongs] = gson.toJson(data.recentSongIds)
            prefs[keyRecentSongObjects] = gson.toJson(
                RecentSongObjectsData(data.recentSongObjects.map { it.stripVolatileStreamUrl() })
            )
            prefs[keyPlayCounts] = gson.toJson(data.playCounts)
            if (data.lastQueue != null) {
                prefs[keyLastQueue] = gson.toJson(
                    data.lastQueue.copy(songs = data.lastQueue.songs.map { it.stripVolatileStreamUrl() })
                )
            }
            data.equalizerPreset?.let { prefs[keyEqualizerPreset] = it.ordinal }
            if (data.equalizerBands.isNotEmpty()) {
                prefs[keyEqualizerBands] = gson.toJson(data.equalizerBands)
            }
            if (data.playRecords.isNotEmpty()) {
                prefs[keyPlayRecords] = gson.toJson(PlayRecordsData(data.playRecords))
            }
            if (data.searchHistory.isNotEmpty()) {
                prefs[keySearchHistory] = gson.toJson(data.searchHistory)
            }
            // v2: 恢复之前遗漏的设置项
            prefs[keyCoverFilterEnabled] = data.coverFilterEnabled
            prefs[keyCoverFilterBlurRadius] = data.coverFilterBlurRadius
            prefs[keyCoverFilterDarkOverlay] = data.coverFilterDarkOverlay
            if (data.musicSource.isNotBlank()) prefs[keyMusicSource] = data.musicSource
            prefs[keyLyricsFontScale] = data.lyricsFontScale
        }
    }
}

// =====================================================================
// 备份导入的**最后一道兜底**
// =====================================================================
//
// ⚠️ Gson 用反射写字段，**绕过 Kotlin 的非空检查**：反序列化时若某个枚举名在当前枚举里
// 不存在，默认适配器会返回 `null` 并被直接写进声明为非空的字段（详见 BackupGson.kt）。
// 随后 `settings.visualizerTheme.name` 抛 NPE → `dataStore.edit {}` 事务回滚
// → **整份备份导入失败**（真机故障：老备份里存着已删除的 `CLASSICAL_WAVE`）。
//
// 主修复是让备份走 `backupGson`（容错枚举）。这里再兜一层：**单个字段为 null 也只回落默认值**，
// 绝不因为一个字段让整份备份导入失败。扩展函数接收可空接收者，故对非空类型同样适用、无编译告警。

/** 可视化主题：null → [VisualizerTheme.Default]（`CIRCULAR_RING`） */
private fun VisualizerTheme?.nameOrDefault(): String =
    this?.name ?: VisualizerTheme.Default.name

/** 可视化画质：null → [VisualQuality.Default]（`MEDIUM`） */
private fun VisualQuality?.nameOrDefault(): String =
    this?.name ?: VisualQuality.Default.name

/** 播放模式：null → [PlayMode.SEQUENTIAL] */
private fun PlayMode?.ordinalOrDefault(): Int =
    this?.ordinal ?: PlayMode.SEQUENTIAL.ordinal

/** 网络音乐源：null → [NetworkSource.DEFAULT] */
private fun NetworkSource?.keyOrDefault(): String =
    this?.key ?: NetworkSource.DEFAULT.key

/** 照片墙转场：null → [PhotoTransitionId.Default]（`CROSSFADE`） */
private fun PhotoTransitionId?.nameOrDefault(): String =
    this?.name ?: PhotoTransitionId.Default.name

/** 画面适配：null → [PhotoScaleMode.Default]（`CROP`） */
private fun PhotoScaleMode?.nameOrDefault(): String =
    this?.name ?: PhotoScaleMode.Default.name

// =====================================================================
// 应用内自动更新偏好域（§更新）
// 独立于 AppSettings：这类「这台设备要不要自动检查」的查看偏好不进备份 JSON，
// 与日志级别过滤（keyLogsMinLevel）同口径。
// =====================================================================
// 应用内自动更新（2026-11-03）偏好键：按本文件既有约定，键为文件级 `internal val`
// （与 GeneralSettingsPrefs.kt 的 keyDarkTheme 等同口径），由同文件的 UpdatePrefs 读取。
internal val keyAutoCheckUpdates = booleanPreferencesKey("settings_auto_check_updates")
internal val keySkippedUpdateVersion = stringPreferencesKey("settings_skipped_update_version")
internal val keyLastUpdateCheck = longPreferencesKey("settings_last_update_check")

class UpdatePrefs internal constructor(private val prefs: AppPreferences) {

    /** 自动检查开关（默认开） */
    val autoCheckUpdatesFlow: kotlinx.coroutines.flow.Flow<Boolean> =
        prefs.prefsData.map { it[keyAutoCheckUpdates] ?: true }

    /** 已跳过的版本（空串=未跳过） */
    val skippedVersionFlow: kotlinx.coroutines.flow.Flow<String> =
        prefs.prefsData.map { it[keySkippedUpdateVersion] ?: "" }

    /** 上次自动检查时间（ms 时间戳，0=从未） */
    val lastCheckFlow: kotlinx.coroutines.flow.Flow<Long> =
        prefs.prefsData.map { it[keyLastUpdateCheck] ?: 0L }

    suspend fun setAutoCheck(enabled: Boolean) =
        prefs.prefsEdit { it[keyAutoCheckUpdates] = enabled }

    suspend fun setSkippedVersion(version: String) =
        prefs.prefsEdit { it[keySkippedUpdateVersion] = version }

    suspend fun setLastCheck(timeMs: Long) =
        prefs.prefsEdit { it[keyLastUpdateCheck] = timeMs }
}
