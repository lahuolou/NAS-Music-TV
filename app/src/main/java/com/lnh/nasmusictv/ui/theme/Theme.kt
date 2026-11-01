package com.lnh.nasmusictv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ShapeDefaults
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
/**
 * 项目级别的颜色系统（与 HTML index.html 中的 CSS 变量一一对应）
 *
 * ⚠️ 主题修复（+）：属性由 `val`（编译期常量）改为 **Compose 可变 State**，
 * 由 [NASMusicTVTheme] 按 darkTheme 同步对应色板（见 [TVDarkPalette]/[LightPalette]）。
 * 这样全仓数百处 `NasMusicColors.X` 引用**零改动**即可随开关整体切换 —— 此前它们是
 * 固定深色常量，MaterialTheme 切换覆盖不到，导致「暗色主题开关无反应」。
 *
 * ⚠️ 组合期写入同值 State 不会触发重组（默认 structuralEqualityPolicy），同步幂等。
 */
object NasMusicColors {
    // 背景（初始值 = TV 档；运行时由 NASMusicTVTheme 按设备/主题覆盖）
    var Background by mutableStateOf(Color(0xFF0E0F12))        // var(--bg)
    var Surface by mutableStateOf(Color(0xFF17191F))            // var(--card-bg)
    var SurfaceVariant by mutableStateOf(Color(0xFF1E222A))     // var(--card-hover)
    // 主色（品牌暗红——网易云风格固定强调色；#C22323 比绛红 #B01B1B 更正，不土不刺眼）
    var Primary by mutableStateOf(Color(0xFFC22323))            // var(--primary)
    var PrimaryVariant by mutableStateOf(Color(0xFF9E1B1B))
    var Secondary by mutableStateOf(Color(0xFF6190E8))          // var(--secondary) 网易辅助蓝
    // 文字
    var TextPrimary by mutableStateOf(Color(0xFFF2F4F8))        // var(--text-primary)
    var TextSecondary by mutableStateOf(Color(0xFF9AA3B2))      // var(--text-secondary)
    // 边框
    var Border by mutableStateOf(Color(0xFF20242C))             // var(--border)
    // 状态
    var Danger by mutableStateOf(Color(0xFFF87171))
    var Warning by mutableStateOf(Color(0xFFFBBF24))
    var Success by mutableStateOf(Color(0xFF34D399))
    // 焦点（暗红描边——TV 焦点态；拒绝大面积红色填充）
    var FocusRing by mutableStateOf(Color(0xFFC22323))          // var(--focus-ring)
    // glow / accent（极淡暗红：氛围光晕不用高饱和红）
    var AccentGlow by mutableStateOf(Color(0x14C22323))          // rgba(194, 35, 35, 0.08)
    var AccentGlowStrong by mutableStateOf(Color(0x26C22323))    // rgba(194, 35, 35, 0.15)
    // 主按钮（播放/暂停）聚焦高亮色 — 鲜红留给高优先级状态（直播/会员/当前歌词行）
    var PrimaryBright by mutableStateOf(Color(0xFFE25858))
    // Karaoke word highlight (B-3)：歌词当前行品牌红
    var TextBrightHighlight by mutableStateOf(Color(0xFFE25858))

    // ── 配色纪律（工整化）：全站聚焦/选中/实心按钮只用这两个统一档位 ──
    // FocusOverlay：普通项聚焦/选中叠加（16% 弱提亮——克制，避免红底过重）
    val FocusOverlay: Color get() = Primary.copy(alpha = 0.16f)
    // PrimaryFill：实心主操作按钮（对话框 isPrimary 等），全站统一 85%
    val PrimaryFill: Color get() = Primary.copy(alpha = 0.85f)
}

/**
 * 网易云音乐风格（+）：
 * - **绛红/暗红为固定品牌强调色**（继承网易传统红的品牌资产），不随封面完全漂移；
 *   动态取色（若有）只作播放页背景氛围层。
 * - **TV 档**（[TVDarkPalette]）：深净底 + 不透明表面 + 明确描边；焦点态=暗红描边 + 表面轻微提亮，
 *   拒绝大面积高饱和红填充（3 米距离可读性优先）。
 * - 仅保留 **TV 档**（[TVDarkPalette]）：不透明表面 + 明确描边 + 高对比，适配大屏。
 * 列表页保持深色底，避免红白强对比在 TV 上刺眼。
 */
private data class NasColorPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val focusRing: Color,
    val accentGlow: Color,
    val accentGlowStrong: Color,
    val primaryBright: Color,
    val textBrightHighlight: Color,
)

/** 深色 TV 档色板（默认；高对比 + 明确描边；品牌暗红，glow 极淡） */
private val TVDarkPalette = NasColorPalette(
    background = Color(0xFF0E0F12),
    surface = Color(0xFF17191F),
    surfaceVariant = Color(0xFF1E222A),
    primary = Color(0xFFC22323),
    primaryVariant = Color(0xFF9E1B1B),
    secondary = Color(0xFF6190E8),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFF9AA3B2),
    border = Color(0xFF20242C),
    danger = Color(0xFFF87171),
    warning = Color(0xFFFBBF24),
    success = Color(0xFF34D399),
    focusRing = Color(0xFFC22323),
    accentGlow = Color(0x14C22323),
    accentGlowStrong = Color(0x26C22323),
    primaryBright = Color(0xFFE25858),
    textBrightHighlight = Color(0xFFE25858),
)

// （Air 档色板已随手机 UI 下线移除；TV 统一使用 TVDarkPalette / LightPalette）

/** 浅色色板（亮色主题；品牌暗红保证浅底对比度，与 LightColorScheme 一致） */
private val LightPalette = NasColorPalette(
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF4),
    primary = Color(0xFFC22323),
    primaryVariant = Color(0xFF9E1B1B),
    secondary = Color(0xFF2563EB),
    textPrimary = Color(0xFF1B2333),
    textSecondary = Color(0xFF5B6B83),
    border = Color(0xFFD5DEE8),
    danger = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    success = Color(0xFF059669),
    focusRing = Color(0xFFC22323),
    accentGlow = Color(0x26C22323),
    accentGlowStrong = Color(0x4DC22323),
    primaryBright = Color(0xFFE25858),
    textBrightHighlight = Color(0xFFC22323),
)

/** 把指定色板同步到全局 [NasMusicColors]（组合期调用；同值幂等，不触发多余重组） */
private fun applyPalette(palette: NasColorPalette) {
    NasMusicColors.Background = palette.background
    NasMusicColors.Surface = palette.surface
    NasMusicColors.SurfaceVariant = palette.surfaceVariant
    NasMusicColors.Primary = palette.primary
    NasMusicColors.PrimaryVariant = palette.primaryVariant
    NasMusicColors.Secondary = palette.secondary
    NasMusicColors.TextPrimary = palette.textPrimary
    NasMusicColors.TextSecondary = palette.textSecondary
    NasMusicColors.Border = palette.border
    NasMusicColors.Danger = palette.danger
    NasMusicColors.Warning = palette.warning
    NasMusicColors.Success = palette.success
    NasMusicColors.FocusRing = palette.focusRing
    NasMusicColors.AccentGlow = palette.accentGlow
    NasMusicColors.AccentGlowStrong = palette.accentGlowStrong
    NasMusicColors.PrimaryBright = palette.primaryBright
    NasMusicColors.TextBrightHighlight = palette.textBrightHighlight
}

/**
 * 常用渐变 — 例如进度条、背景遮罩、渐隐 mask
 * ⚠️ 全部改为 `get()`：读取时现算（读取当前 [NasMusicColors] 色值），
 * 否则顶层惰性 val 会缓存**首次访问时**的旧色，主题切换后渐变不跟着变。
 */
object NasMusicBrushes {
    val progressBar get() = Brush.horizontalGradient(
        colors = listOf(NasMusicColors.Primary, NasMusicColors.Secondary)
    )
    val topFadeMask get() = Brush.verticalGradient(
        colors = listOf(NasMusicColors.Background.copy(alpha = 0.8f), Color.Transparent)
    )
    val bottomFadeMask get() = Brush.verticalGradient(
        colors = listOf(Color.Transparent, NasMusicColors.Background.copy(alpha = 0.8f))
    )
    val overlayGradient get() = Brush.verticalGradient(
        colors = listOf(
            NasMusicColors.Background.copy(alpha = 0.85f),
            NasMusicColors.Background
        )
    )
    val coverGlow get() = Brush.radialGradient(
        colors = listOf(NasMusicColors.AccentGlowStrong, Color.Transparent)
    )
}

object NasMusicDimens {
    val PaddingSmall = 8.dp
    val PaddingMedium = 16.dp
    val PaddingLarge = 24.dp
    val PaddingXLarge = 40.dp
    val CornerRadius = 12.dp
    val CoverSize = 200.dp
    val CoverSizeLarge = 360.dp
}

/**
 * 字号分级系统
 *
 * 所有字号统一归入 8 个档位，每个档位对应手机/TV 两套数值：
 * - 手机端保持设计稿原始值
 * - TV 端统一 +12sp（比手机端大 12 号，适配大屏）
 *
 * 使用方式：直接引用 FontSize.XX，例如 Text(fontSize = FontSize.Body)
 * 或通过 Composable 函数获取设备适配值：FontSize.body()
 */
/** 全局字体字号调整量（sp）。用户在设置中增减，正值放大、负值缩小。 */
val LocalFontAdjustment = androidx.compose.runtime.staticCompositionLocalOf { 0 }

object FontSize {
    // 档位 1: Caption（注释、徽章、最小文字）
    val Caption = 12.sp        // 手机
    val CaptionTv = 18.sp      // TV = 12 + 6

    // 档位 2: Small（次要文字、设置值、按钮小字）
    val Small = 14.sp
    val SmallTv = 20.sp        // TV = 14 + 6

    // 档位 3: Body（正文、列表项、主要文字）
    val Body = 17.sp
    val BodyTv = 23.sp         // TV = 17 + 6

    // 档位 4: Button（按钮文字、标签）
    val Button = 19.sp
    val ButtonTv = 25.sp       // TV = 19 + 6

    // 档位 5: Subtitle（副标题、卡片标题、对话框标题）
    val Subtitle = 23.sp
    val SubtitleTv = 29.sp     // TV = 23 + 6

    // 档位 6: Title（大标题、歌词普通行）
    val Title = 27.sp
    val TitleTv = 33.sp        // TV = 27 + 6

    // 档位 7: Display（展示文字、歌词当前行、大数字）
    val Display = 33.sp
    val DisplayTv = 39.sp      // TV = 33 + 6

    // 档位 8: DisplayLarge（超大展示）
    val DisplayLarge = 41.sp
    val DisplayLargeTv = 47.sp // TV = 41 + 6

    /** 根据当前设备返回对应档位的字号（含用户全局字体调整） */
    @Composable
    fun caption(): TextUnit = adjustedSize(Caption, CaptionTv)
    @Composable
    fun small(): TextUnit = adjustedSize(Small, SmallTv)
    @Composable
    fun body(): TextUnit = adjustedSize(Body, BodyTv)
    @Composable
    fun button(): TextUnit = adjustedSize(Button, ButtonTv)
    @Composable
    fun subtitle(): TextUnit = adjustedSize(Subtitle, SubtitleTv)
    @Composable
    fun title(): TextUnit = adjustedSize(Title, TitleTv)
    @Composable
    fun display(): TextUnit = adjustedSize(Display, DisplayTv)
    @Composable
    fun displayLarge(): TextUnit = adjustedSize(DisplayLarge, DisplayLargeTv)
}

/** 根据设备与用户全局字体调整量，计算最终字号 */
@Composable
private fun adjustedSize(phone: TextUnit, tv: TextUnit): TextUnit {
    val adjustment = LocalFontAdjustment.current
    // TV-only：直接采用 TV 字号。原 phone 分支随手机 UI 下线已无意义；保留参数不变以不破坏调用点。
    val base = tv
    return if (adjustment == 0) base else (base.value + adjustment).sp
}

/**
 * 按钮文字颜色系统
 *
 * 统一管理按钮在不同状态下的文字颜色：
 * - 默认（未选中）：TextPrimary（亮白色），确保可读性
 * - 聚焦：Primary（青色），视觉反馈
 * - 按下：Primary，与聚焦一致
 * - 禁用：TextSecondary（灰色），降低视觉权重
 */
object ButtonColors {
    /** 默认（未选中）文字颜色 —— 亮色，确保可读性（get()：随主题取当前色） */
    val DefaultContent get() = NasMusicColors.TextPrimary

    /** 聚焦时文字颜色 —— Primary 青色 */
    val FocusedContent get() = NasMusicColors.Primary

    /** 按下时文字颜色 —— Primary 青色 */
    val PressedContent get() = NasMusicColors.Primary

    /** 禁用时文字颜色 —— TextSecondary 灰色 */
    val DisabledContent get() = NasMusicColors.TextSecondary
}

/**
 * 类型系统
 * ⚠️ 色值改用常量色板（TVDarkPalette/LightPalette）构建：顶层 val 只初始化一次，
 * 不能再读可变 [NasMusicColors]（否则首访缓存旧色）；且语义上 scheme 应跟随当前主题。
 */
private val TVDarkColorScheme = darkColorScheme(
    background = TVDarkPalette.background,
    surface = TVDarkPalette.surface,
    primary = TVDarkPalette.primary,
    onPrimary = Color(0xFF000000),
    onBackground = TVDarkPalette.textPrimary,
    onSurface = TVDarkPalette.textPrimary,
    onSurfaceVariant = TVDarkPalette.textSecondary
)

// （AirDarkColorScheme 已随手机 UI 下线移除；TV 统一使用 TVDarkColorScheme / LightColorScheme）

private val LightColorScheme = lightColorScheme(
    background = LightPalette.background,
    surface = LightPalette.surface,
    primary = LightPalette.primary,
    onPrimary = Color(0xFFFFFFFF),
    onBackground = LightPalette.textPrimary,
    onSurface = LightPalette.textPrimary,
    onSurfaceVariant = LightPalette.textSecondary
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = FontSize.DisplayLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontSize = FontSize.DisplayLarge,
        fontWeight = FontWeight.Bold
    ),
    headlineLarge = TextStyle(
        fontSize = FontSize.Display,
        fontWeight = FontWeight.SemiBold
    ),
    headlineMedium = TextStyle(
        fontSize = FontSize.Title,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = FontSize.Subtitle,
        fontWeight = FontWeight.Medium
    ),
    titleMedium = TextStyle(
        fontSize = FontSize.Subtitle,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = FontSize.Button,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = FontSize.Body,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = FontSize.Button,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = FontSize.Body,
        fontWeight = FontWeight.Normal
    )
)

private val AppShapes = Shapes(
    extraSmall = ShapeDefaults.ExtraSmall,
    small = ShapeDefaults.Small,
    medium = ShapeDefaults.Medium,
    large = ShapeDefaults.Large,
    extraLarge = ShapeDefaults.ExtraLarge
)

@Composable
fun NASMusicTVTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // TV-only：固定使用 TV 档（不透明表面+明确描边+高对比），背景统一深净底 #0E0F12（留白感）。
    applyPalette(if (darkTheme) TVDarkPalette else LightPalette)
    val colorScheme = if (!darkTheme) LightColorScheme else TVDarkColorScheme
    // TV 端字号由 FontSize.xx() @Composable 函数返回 +6sp 的 TV 值，无需 fontScale 缩放
    val lyricsTheme = LyricsThemeData(
        currentLine = LyricsTheme.currentLine,
        normalLine = LyricsTheme.normalLine,
        dimLine = LyricsTheme.dimLine
    )
    // Task 12: 检测系统高对比度模式
    // L15 修复（2026-10-06，代码审查报告 §5）：旧实现用 `uiMode and 0x20000` 掩码判断，
    // 但 0x20000 不是任何 Android 高对比度标志位（UI_MODE_NIGHT_MASK=0x30）⇒ 恒为 false，
    // 4 个消费分支（AlbumGrid/ArtistList）整条从未生效；且 remember 包裹不随配置变化重组。
    // 修复：读 Settings.Secure 的 high_text_contrast_enabled（API 21+，minSdk 22 兼容；
    // AccessibilityManager.isHighTextContrastTextEnabled 是 API 36+ 不可用），
    // 并注册 ContentObserver 监听系统开关变化 ⇒ 状态变化时自动重组。
    val context = androidx.compose.ui.platform.LocalContext.current
    var isHighContrast by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(context) {
        fun readFlag(): Boolean = android.provider.Settings.Secure.getInt(
            context.contentResolver,
            "high_text_contrast_enabled", 0
        ) == 1
        isHighContrast = readFlag()
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                isHighContrast = readFlag()
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.Secure.getUriFor("high_text_contrast_enabled"),
            false, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLyricsTheme provides lyricsTheme,
        LocalHighContrast provides isHighContrast
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * 歌词相关主题
 */
object LyricsTheme {
    // ⚠️ get()：顶层 val 惰性缓存首访色值，主题切换后不更新（与 NasMusicBrushes 同理）
    val currentLine get() = TextStyle(
        fontSize = FontSize.DisplayLarge,
        fontWeight = FontWeight.SemiBold,
        color = NasMusicColors.Primary
    )
    val normalLine get() = TextStyle(
        fontSize = FontSize.Display,
        fontWeight = FontWeight.Normal,
        color = NasMusicColors.TextPrimary
    )
    val dimLine get() = TextStyle(
        fontSize = FontSize.Title,
        fontWeight = FontWeight.Normal,
        color = NasMusicColors.TextSecondary
    )
}

/** 歌词主题容器（TV/手机自动切换） */
data class LyricsThemeData(
    val currentLine: TextStyle,
    val normalLine: TextStyle,
    val dimLine: TextStyle
)

/** CompositionLocal：提供当前设备对应的歌词主题 */
val LocalLyricsTheme = androidx.compose.runtime.staticCompositionLocalOf {
    LyricsThemeData(
        currentLine = LyricsTheme.currentLine,
        normalLine = LyricsTheme.normalLine,
        dimLine = LyricsTheme.dimLine
    )
}

// （手机紧凑 UI 尺度 CompactSizes 已随手机 UI 下线移除；TV 统一使用 TV 字号体系）

/**
 * 当前是否为**暗色主题**（由 `MainActivity` 依 `AppSettings.darkTheme` 提供）。
 *
 * ## 为什么要有这个 CompositionLocal
 * 整个应用的 `` NasMusicColors.X `` 是**可变的 Compose State**（[NASMusicTVTheme] 按 darkTheme
 * 同步色板），组件读它必然订阅色值重组，但**读不出「当前是暗色还是亮色」这个意图本身**。
 * 此前播放页被迫用 `NasMusicColors.Background.luminance() > 0.5f` **反推**主题：
 * 一旦某档色板的底色亮度接近 0.5 阈值，或用户自定义主题改变了底色，判断就会翻转 ——
 * 这是把「策略判断」建立在「观感副产物」上的脆弱写法。
 *
 * ## 播放页沉浸式方案的**唯一**入口条件（最高优先级条款 A1/A2）
 * - `true`（暗色）→ 启用封面模糊背景、暗色遮罩、黑胶光晕、呼吸动效等深色特效
 * - `false`（亮色）→ **一律不套用**上述深色视觉，保留原有播放页布局
 *
 * 判断始终暴露给 UI 的是**用户的主题意图**（应用内设置，见 A1.3 优先级），
 * 而非系统 uiMode 或取色结果；动态取色不得影响本值。
 *
 * 默认值 `true` 与 `AppSettings.darkTheme` 的默认值保持一致（历史上应用默认暗色），
 * 保证预览 / 单测等未提供该 CompositionLocal 的场景仍走「暗色」这一主路径。
 */
val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * 当前是否启用**减弱动效**（由 `MainActivity` 依 `AppSettings.animationsEnabled` 取反提供）。
 *
 * 这是播放页所有**非必要**动效的总开关（ §10.1-7、§11-8 ）：
 * 打开后保留静态构图与必要的状态反馈（焦点边框、播放/暂停图标、进度），
 * 但关闭背景呼吸缩放、黑胶光晕呼吸、封面进入回弹、页面进场转场等无限/连续动画。
 *
 * ⚠️ 它不是"摆设"（禁止事项 15）：所有无限循环动画（`rememberInfiniteTransition`
 * / `while(true) animateTo`）都必须以它为条件提前返回或降级为静态值，详见各特效的实现。
 *
 * 默认 `false`（= 动效开启），与 `AppSettings.animationsEnabled = true` 一致，保持零回归。
 */
val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Task 12: 高对比度模式
 *
 * 读取 Settings.Secure.high_text_contrast_enabled（L15 修复，2026-10-06：
 * 旧注释引用的 `UI_MODE_MASK_HIGH_CONTRAST` 常量并不存在，原掩码实现恒为 false），
 * 提供 CompositionLocal 供组件使用。
 * 高对比度模式下：分割线加粗、焦点边框加宽、文字对比增强。
 */
val LocalHighContrast = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 高对比度模式下的颜色增强
 */
object HighContrastColors {
    /** 加强的主色 — 比 Primary 更亮（get()：随主题取当前色板） */
    val PrimaryBright get() = NasMusicColors.PrimaryBright
    /** 加强的文字色 — 纯白 */
    val TextPrimary = Color(0xFFFFFFFF)
    /** 加强的分割线色 */
    val BorderStrong = Color(0xFF4A5A72)
}


