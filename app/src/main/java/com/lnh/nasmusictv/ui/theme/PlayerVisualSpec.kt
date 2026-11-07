package com.lnh.nasmusictv.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 播放页沉浸式改造的**唯一视觉 / 动效常量源**（改造任务书 §12-2）。
 *
 * ## 为什么要有这个文件
 * 任务书禁止事项 8「禁止硬编码大量颜色和尺寸」、§12-2「新增样式常量集中管理」。
 * 此前这些数值散落在 `NowPlayingScreen` / `VinylDisc` / `LyricsView` / `ImmersiveBackdrop`
 * 四处，改一个节奏要翻四个文件，且极易出现"同一个效果两处参数不一致"。
 *
 * 所有数值均直接对应任务书 §10.4「动效参数建议」，注释里标注了出处，
 * 便于验收时逐条核对。
 *
 * ## 仅暗色主题生效（最高优先级 A1/A2）
 * 这些常量描述的是**暗色沉浸式**视觉语言。亮色主题下调用方根本不套用本套组件
 * （由 [LocalDarkTheme] 分支决定），因此此处不再重复做主题判断 ——
 * 主题判断集中在一处，不散落（§12-10）。
 */
object PlayerVisualSpec {

    // ==================== 焦点与按压（§9.5 / §10.4） ====================

    /** 普通按钮焦点放大倍数（§10.4：1.05–1.10） */
    const val FOCUS_SCALE = 1.08f

    /** 播放/暂停按钮焦点放大倍数：视觉最突出（§7.3「播放/暂停按钮视觉最突出」） */
    const val FOCUS_SCALE_PRIMARY = 1.10f

    /** 按压缩放（§10.4：0.96–0.98） */
    const val PRESS_SCALE = 0.97f

    /** 焦点缩放动画时长（§10.4：150–200ms） */
    const val FOCUS_ANIM_MS = 180

    // ==================== 底部控制栏（§7.3） ====================

    /** 底部控制栏与屏幕下沿的间距（2026-10-29 用户反馈"工具栏太高"：32 → 12dp；
     *  同日「减少底部留白」再收：12 → 8dp） */
    val BOTTOM_BAR_BOTTOM_PAD = 8.dp

    /**
     * 底部传输键尺寸：**播放/暂停与上一曲/下一曲同尺寸** ——
     * 主键的视觉突出靠红底 + 白图标，而不是更大的直径（2026-10-28 用户定稿）。
     */
    val BOTTOM_PRIMARY_BUTTON_SIZE = 40.dp
    val BOTTOM_SECONDARY_BUTTON_SIZE = 40.dp

    /** 按压动画时长（§10.4：100–150ms） */
    const val PRESS_ANIM_MS = 120

    /**
     * 进度条聚焦放大（§10.4：1.05–1.10）。
     * 2026-10-28 用户定稿"进度条焦点也小一点"：1.06 → 1.03。
     */
    const val PROGRESS_FOCUS_SCALE = 1.03f

    /** 进度轨道粗细。2026-10-28 用户定稿"进度条再细一点"：4dp → 3dp。 */
    val PROGRESS_TRACK_HEIGHT = 3.dp

    /**
     * 进度「呼吸灯」柔光点直径（2026-10-29 用户定稿：移除圆形滑块 thumb，
     * 改为在当前进度位置的柔光指示，播放时轻微呼吸、暂停时减弱静止）。
     *
     * 2026-10-29 实测回调：24dp 时光晕直径不够、中心仍被读作"白点滑块"，
     * 放大到 32dp —— 实心核只占半径 12%（见 drawBehind 的渐变 stops），
     * 其余是连续衰减外晕，整体读作"柔光"而非"可拖拽的圆点"。
     */
    val PROGRESS_LIGHT_SIZE = 32.dp

    /** 呼吸灯呼吸周期（§10.4 轻动效建议 1.5s alternate；暂停态不跑 tick） */
    const val PROGRESS_BREATH_MS = 1500

    /**
     * 唱针红点光晕的呼吸周期（2026-10-30 用户定稿「光晕改成呼吸灯效果」）。
     * 与进度条呼吸灯同节奏量级；暂停 / 减弱动效时退化为静态中值强度。
     */
    const val TONEARM_GLOW_BREATH_MS = 1500

    /**
     * 沉浸隐藏态的**唤醒宽限期**（2026-10-30 用户定稿「隐藏状态下切歌保持隐藏」）。
     *
     * 一曲播完自动连播的间隙 isPlaying 会短暂变 false（加载下一曲），旧逻辑会把
     * 这当作"播放停止"立即唤醒控件。加宽限：唤醒前先等这一段，期间播放恢复
     * （autoHideIdle 变回 true）则**维持隐藏**；真正暂停/离开播放页仍会唤醒。
     */
    const val IDLE_WAKE_GRACE_MS = 2000L

    /**
     * 「歌词」显隐开关（2026-10-28 用户定稿：**纯文字按钮 → 图标按钮**，参照网易云播放页）。
     *
     * 图标 = 圆角方框描边 + 框内一个「词」字；尺寸与左右 40dp 圆形图标按钮同节奏
     * （容器仍是 40dp，图标本体 24dp）。显示态用品牌红，隐藏态转次要色 ——
     * 不再靠 dimmed 半透明表达状态（半透明 + 文字会显得"缺一块"）。
     */
    val LYRICS_TOGGLE_ICON_SIZE = 24.dp
    val LYRICS_TOGGLE_ICON_STROKE = 1.4.dp
    const val LYRICS_TOGGLE_GLYPH_SP = 12f

    /**
     * 无歌词态隐藏/显示转场中，标题块的**联动下移量**（特效：黑胶放大让位、标题轻移呼应）。
     *
     * ⚠️ 歌词关闭态盘径改为屏高 0.48（见 [DISC_HEIGHT_FRACTION_NO_LYRICS]）后，
     * 隐藏态放大 [IDLE_DISC_SCALE]=1.1667 → 0.56 屏高，下半圈溢出 ≈ 0.03×屏高，
     * 18dp 联动下移仍足够覆盖、不压字。
     */
    val IDLE_TITLE_SHIFT = 18.dp

    /** 无歌词态标题块联动动效的轻微放大（1.04，与黑胶放大方向呼应但不抢戏） */
    const val IDLE_TITLE_SCALE = 1.04f

    /**
     * 无歌词态单行「标题 - 歌手」的容器宽度占屏宽比例（2026-10-28 用户定稿"90%"）。
     * 该行**水平居中于屏幕**（渲染在左列里，靠一个补偿位移对齐屏心）。
     */
    const val CAPTION_WIDTH_FRACTION = 0.9f

    /**
     * 焦点光晕呼吸周期（P1-8，§10.4：2–4s）。
     * 仅在控件**获得焦点**时运行，且低端设备 / 减弱动效时关闭（§11-7）。
     */
    const val FOCUS_GLOW_PERIOD_MS = 3_000

    // ==================== 背景（§6.1 / §10.3 / §10.4） ====================

    /**
     * 模糊背景图的**解码采样边长**（px）。
     *
     * ## 为什么用「超低分辨率位图 + 放大」代替 `Modifier.blur`
     * `Modifier.blur` 走**逐帧** RenderEffect（API 31+）/ RenderScript（低版本已废弃），
     * 对一张铺满全屏的图是持续合成开销 —— 既踩了「禁止每帧实时高斯模糊」（禁止事项 10），
     * 又在 minSdk 22 的老电视上没有可靠降级路径。
     *
     * 让 Coil 把封面解码到 96px，再由 GPU 双线性插值放大铺满：
     * 10 倍以上放大率本身就是一次高质量箱式模糊，观感与 20–30dp 高斯模糊等价，
     * 而解码后它只是一张约 36KB 的静态小位图，每帧仅一次纹理采样 ——
     * 零合成开销、零新依赖、全 API 兼容。
     */
    const val BACKDROP_SAMPLE_PX = 96

    /**
     * 无封面时「照片墙（系统壁纸）虚化背景」的壁纸层不透明度（2026-10-30 用户定稿恢复）。
     * 底色仍是调用方的兜底深色渐变，壁纸只作氛围纹理，故不能压到 1f。
     */
    const val WALLPAPER_BACKDROP_ALPHA = 0.55f

    /**
     * 壁纸解码采样边长 = **屏宽 ÷ 该系数**（自适应任意屏幕尺寸，2026-10-30 用户要求）。
     * ⚠️ 旧实现固定 96px：在 3200px 宽的屏幕上放大 33 倍，壁纸亮部就是一团团
     * "模糊白块"（用户反馈的直接原因）。改为按屏宽 1/8 采样（3200 → 400px），
     * 再叠一次两级降采样（天然高斯近似）后放大，斑块尺度降到肉眼不可辨。
     */
    const val WALLPAPER_SAMPLE_DIVISOR = 8

    /** 壁纸采样边长夹取范围（px）：小屏不至于糊成色块，大屏不至于吃内存 */
    const val WALLPAPER_SAMPLE_MIN_PX = 160
    const val WALLPAPER_SAMPLE_MAX_PX = 480

    /** 背景切歌交叉淡入（§10.4：400–600ms） */
    const val BACKDROP_CROSSFADE_MS = 520

    /** 背景呼吸缩放峰值（§10.4：1.0 → 1.05） */
    const val BACKDROP_BREATH_MAX = 1.05f

    /** 呼吸静止态（暂停 / 减弱动效 / 弹窗打开时保持的**常量**，不推进动画） */
    const val BACKDROP_BREATH_REST = 1.02f

    /** 背景呼吸周期（§10.4：20–30s，alternate） */
    const val BACKDROP_BREATH_PERIOD_MS = 25_000

    /**
     * 智能取色（[com.lnh.nasmusictv.ui.theme.CoverPalette]）可用时，封面图层的**不透明度**。
     *
     * 「一曲一色」（网易云"背景颜色随歌曲封面变化"）的核心是**纯色/渐变铺底**：
     * 取色渐变是绝对主体，封面图只留极淡的纹理细节（8%），
     * 不能再看出"一张模糊的照片" —— 2026-10-28 用户定稿，从 0.22 收到 0.08。
     */
    const val PALETTE_COVER_ALPHA = 0.08f

    /** 取色背景上的遮罩强度系数（取色背景本身已很暗，遮罩再打满会把色相压死） */
    /** 取色背景上的遮罩强度系数（2026-10-30「背景太亮，再暗一个维度」：0.40 → 0.50） */
    const val PALETTE_SCRIM_FACTOR = 0.50f

    /** 遮罩基色（近黑，与黑胶盘底同源，视觉收敛） */
    val BACKDROP_SCRIM = Color(0xFF0A0A0C)

    /** 遮罩渐变色标：顶/底压得更暗以保证顶栏与底栏文字对比度（§6.1：全屏 40%–60% 黑） */
    val BACKDROP_SCRIM_STOPS = arrayOf(
        0.00f to 0.78f,
        0.26f to 0.52f,
        0.60f to 0.56f,
        1.00f to 0.82f
    )

    // ==================== 封面 / 黑胶（§6.2 / §10.3 / §10.4） ====================

    /**
     * 黑胶盘直径的约束系数：盘径 = min(列宽 × W 系数, **屏高** × [DISC_HEIGHT_FRACTION])。
     *
     * 2026-10-30 用户定稿「黑胶盘还是太小」：改为**按屏幕高度定径** ——
     * 可视态盘径 = 屏高 × 0.60，隐藏态（叠加 [IDLE_DISC_SCALE] = 0.70/0.60）= 屏高 × 0.70。
     * 此前按「列高的 0.62」计算，但列高小于屏高（顶栏+底栏占位），实际盘径只占屏高
     * 约 50%，这就是"还是太小"的根因。W 系数仍按列宽收口，防止窄屏横向溢出。
     */
    const val DISC_FRACTION_W_LYRICS = 0.64f
    const val DISC_FRACTION_W_NO_LYRICS = 0.86f

    /** 黑胶盘直径占**屏幕高度**的比例（可视态；隐藏态由 [IDLE_DISC_SCALE] 放大到 0.70） */
    const val DISC_HEIGHT_FRACTION = 0.60f

    /**
     * **歌词关闭态**（[infoUnderDisc] 布局：标题/歌手单行在黑胶正下方）的视觉规格。
     *
     * 2026-10-30 用户定稿：按**网易云音乐 TV 版播放页参考图**逐像素实测定标 ——
     * 参考图（2275×1024，16:9）：盘径 488px = 屏高 47.7%、标题白字全高 46px、
     * 盘底→标题顶间距 113px、盘心 y=441（0.43 屏高，即「盘 + 标题」**组合整体
     * 垂直居中于屏幕**）。由此导出三个比例（均相对**盘径**，随分辨率自适应）：
     *
     * - [DISC_HEIGHT_FRACTION_NO_LYRICS] = 0.48：盘径 = 屏高 × 0.48；
     * - [CAPTION_FONT_TO_DISC] = 0.101：标题字号 = 盘径 × 0.101 —— 参考值 0.094 是
     *   **字形实高**口径，中文字形实高 ≈ 0.93×fontSize，换算成 fontSize 口径 ÷0.93；
     * - [CAPTION_GAP_TO_DISC] = 0.211：spacer 高 = 盘径 × 0.211 —— 参考值 0.232 是
     *   「盘底→**字形顶**」口径，Text 行盒顶到字形顶有 ≈0.21×fontSize 空隙，先扣除；
     * - [CAPTION_COMBO_CENTER] = 0.17：组合居中所需的**盘心上移量**占盘径比例
     *   （= (0.211 + 1.35×0.101)/2 —— 上移半个「间距 + 标题行盒高」，
     *   使「盘顶…标题底」整体的中线落在屏幕中线上，与参考图一致）。
     */
    const val DISC_HEIGHT_FRACTION_NO_LYRICS = 0.48f
    const val CAPTION_FONT_TO_DISC = 0.101f
    const val CAPTION_GAP_TO_DISC = 0.211f
    const val CAPTION_COMBO_CENTER = 0.17f

    /** 有歌词时左右两列的 weight 分配（参考图左列 ≈ 52%，列内黑胶居中） */
    const val LEFT_COLUMN_WEIGHT_LYRICS = 1.1f
    const val RIGHT_COLUMN_WEIGHT_LYRICS = 1.0f

    /**
     * 盘心封面圆直径占盘径比例。
     * 2026-10-28 用户定稿"封面大一点"：0.46 → 0.56（封面成为盘面主体，
     * 外圈只留一圈黑胶边，"贴纸"感更强）。
     */
    const val DISC_COVER_FRACTION = 0.56f

    /** 黑胶旋转一圈时长（§10.4：20–30s/圈，linear） */
    const val DISC_SPIN_MS = 24_000

    /** 暂停缓停（§10.4：300–500ms） */
    const val DISC_SPIN_DOWN_MS = 450

    /** 恢复缓起（§10.3「暂停缓停 300-500ms，恢复缓起」） */
    const val DISC_SPIN_UP_MS = 500

    /** 切歌封面淡出/淡入（§10.4：200–400ms） */
    const val DISC_COVER_CROSSFADE_MS = 300

    /**
     * 切歌时**盘心封面**的「换碟滑动」时长（2026-10-28 用户定稿：上下一曲要能看出换碟，
     * 但"滑动时间不要太快，影响视觉" —— 420 → 700ms，滑出/滑回各一半，从容不抢节奏）。
     * 2026-10-30 二次定稿：底盘绝对静止，滑动收进封面圆窗内部（滑出+淡出 → 滑入+淡入）。
     */
    const val DISC_COVER_SLIDE_MS = 700

    /** 封面光晕呼吸周期（P1-2） */
    const val DISC_GLOW_PERIOD_MS = 3_000

    /**
     * 盘外光晕相对盘面的放大倍数（1.06 = 外圈约 3% 盘径宽的贴边微光）。
     *
     * ⚠️ 必须 > 1：光晕层与盘面同尺寸时会被不透明盘面完全盖住，等于没画。
     * 2026-10-28 用户定稿：旧 1.22 的外圈光带太大太抢眼，收到贴边一圈。
     */
    const val DISC_GLOW_SCALE = 1.06f

    /** 唱针落下/抬起（§10.4：250–400ms，easeOut） */
    const val TONEARM_MS = 350

    /**
     * 盘外底座 / 阴影相对盘径的放大倍数（1.03 = 盘缘外约 1.5% 盘径宽的贴边暗环，
     * 只做"唱片有厚度"的贴地阴影，不做大光晕 —— 2026-10-28 用户定稿收窄）。
     */
    const val DISC_BASE_SHADOW_SCALE = 1.03f

    /** 盘外底座阴影强度（暗色主题下绘制） */
    const val DISC_BASE_SHADOW_ALPHA = 0.30f

    /** 同心沟纹亮度（暗色盘面上的细密反光，极低 alpha 才像真实密纹而非"线稿圆环"） */
    const val DISC_GROOVE_ALPHA = 0.045f

    /**
     * 沟纹起始半径（占盘径比）—— 紧贴封面圆外缘（封面 0.56/2 = 0.28r，留一点边距）。
     * 2026-10-28：封面放大后纹路从封面外沿起，盘面不再"空"。
     */
    const val DISC_GROOVE_START = 0.60f

    /**
     * 沟纹间距（占盘径比）。2026-10-28 用户定稿"圈数少一点"：0.021 → 0.034
     * （约 22 圈 → 11 圈，疏朗、更像"唱片纹"而不是密集圆环线稿）。
     */
    const val DISC_GROOVE_STEP = 0.034f

    /** 左上高光辉度（黑胶镜面反射的"釉光"，强化材质观感） */
    const val DISC_SPECULAR_ALPHA = 0.12f

    /** 唱臂下落弹簧阻尼比（<1 自然回弹，隐喻"落下"而非匀速平移） */
    const val TONEARM_SPRING_DAMPING = 0.62f

    /** 唱臂弹簧刚度 */
    const val TONEARM_SPRING_STIFFNESS = 170f

    /**
     * 唱臂**快速弹开**弹簧（2026-10-30 用户定稿：切歌/暂停抬起段）。
     * 高刚度短行程 —— 观感是"啪地抬起"，与落下段的慢弹簧（170f，隐喻放针）形成对比。
     */
    const val TONEARM_LIFT_STIFFNESS = 1200f
    const val TONEARM_LIFT_DAMPING = 0.65f

    /**
     * 切歌时唱臂**悬停**时长：先快速弹开抬起，等「封面盘内滑动 + 背景转场」
     * 都完成后才落下合并（2026-10-30 用户定稿）。
     * 取值 = [DISC_COVER_SLIDE_MS]（封面滑出+滑入全程）+ 120ms 背景收尾余量。
     */
    const val TONEARM_SONG_HOLD_MS = DISC_COVER_SLIDE_MS + 120

    /**
     * 唱臂几何（相对盘径画布的归一化坐标）。
     *
     * 枢轴在盘外上方（y 为负 = 画布顶之上，Compose Canvas 默认不裁剪越界绘制），
     * 臂身悬在盘外、仅唱头搭在盘缘右上（落点距盘心 ≈ 0.89 半径）——
     * 唱臂与盘面"错开"，不压在盘上（2026-10-28 用户定稿）。
     *
     * 2026-10-28 二次调整：枢轴上探量 -0.22 → **-0.13**（用户要求唱臂底座
     * 不得高于顶部导航栏；配合盘径整体收窄，越界量同步减小）。
     */
    const val TONEARM_PIVOT_X = 0.55f
    const val TONEARM_PIVOT_Y = -0.13f

    /** 唱臂长度（相对盘径）：枢轴到唱头的直线距离（唱头落点距盘心 ≈ 0.88 半径，贴盘缘） */
    const val TONEARM_LEN = 0.41f

    /** 唱臂基础朝向（度，从枢轴指向盘面右下；暂停抬起时在此基础上回摆） */
    const val TONEARM_BASE_DEG = 50.7f

    /**
     * 唱臂**暂停抬起**角度（度，叠加在 [TONEARM_BASE_DEG] 之上）。
     * 负值 = 把唱头摆向盘外右上 = 抬起；0 = 落下贴盘面。
     */
    const val TONEARM_LIFT_DEG = -24f

    /**
     * 唱臂**入场起步**角度（度，叠加在 [TONEARM_BASE_DEG] 之上）。
     *
     * 2026-10-30 用户定稿：唱臂只在「5s 沉浸隐藏」态加载，进入那一刻从这
     * 个更"上扬"的角度用弹簧落到 [TONEARM_LIFT_DEG] / 0 —— 这就是用户要的
     * 「进入隐藏时才加载唱臂**及动画**」。
     */
    const val TONEARM_ENTER_DEG = -34f

    /**
     * 唱臂**弯杆**弯曲度（相对盘径）：贝塞尔控制点相对臂中点的上抬量。
     * 2026-10-28 用户定稿"把指针改成弯曲的"——0.045 是肉眼明显可辨的弓形弧度。
     */
    const val TONEARM_BEND_FRACTION = 0.045f

    /**
     * 唱针**径向行程**（2026-10-30 用户定稿：唱针位置与播放进度绑定）——
     * 针尖距盘心的距离从「外圈」到「内圈」随播放进度线性内移：
     * 进度 0 → [TONEARM_NEEDLE_OUTER_R]×盘半径（唱片外缘），
     * 进度 1 → [TONEARM_NEEDLE_INNER_R]×盘半径（内圈，与 [DISC_GROOVE_START] 沟纹带呼应）。
     *
     * ⚠️ 2026-10-30 二次修正（用户实测反馈）：
     *  · 起点「太里面」→ 0.93 → **0.96**（落针点贴近唱片外缘，只进去一点点）；
     *  · 终点「快刮到封面」→ 0.62 → **0.74**。封面圆半径 = 0.56×盘半径
     *    （[DISC_COVER_FRACTION] 0.56 是直径，半径 = 0.28w = 0.56R），
     *    旧 0.62 只比封面边缘外 0.06R，视觉上几乎贴住封面；
     *    0.74 保留 0.18R 的安全沟纹带（真实唱片也有 inner groove 禁区）。
     */
    const val TONEARM_NEEDLE_OUTER_R = 0.96f
    const val TONEARM_NEEDLE_INNER_R = 0.74f

    /**
     * 枢轴底座**外发光**半径（相对盘径，2026-10-28 用户定稿，参考白色光球示意图）：
     * 白色柔和光晕包裹枢轴圆，像打了一点柔光。
     */
    const val TONEARM_PIVOT_GLOW_RADIUS = 0.075f

    /** 枢轴外发光中心强度（0..1） */
    const val TONEARM_PIVOT_GLOW_ALPHA = 0.55f

    // ==================== 歌词（§6.3 / §10.3 / §10.4） ====================

    /** 当前行高亮过渡（§10.4：200–300ms） */
    const val LYRIC_LINE_MS = 260

    /**
     * 歌词自动跟行的**平滑滚动时长**（2026-10-30 用户定稿「滚动更平滑优雅」）。
     * 锚行在可视区内时用 [androidx.compose.animation.core.FastOutSlowInEasing]
     * 缓动插值过渡到新的中线位置；不在可视区（切歌/大跨度跳转）仍瞬时定位。
     */
    const val LYRIC_SCROLL_MS = 650

    /** 非当前行透明度（§6.3：0.4–0.6） */
    const val LYRIC_DIM_ALPHA = 0.45f

    /** 已播放行透明度（比未播放更暗，形成"已唱过"的层次） */
    const val LYRIC_PLAYED_ALPHA = 0.55f

    /** 切歌时歌词淡出（§10.3：150–250ms） */
    const val LYRIC_SWAP_MS = 200

    // ---- 歌词行内自适应缩字（2026-10-28 用户定稿：过长一行不得换行）----

    /** 每轮收敛的缩放系数（×0.92，2–4 帧内收敛到可读的最小可容纳字号） */
    const val LYRIC_AUTOFIT_STEP = 0.92f

    /** 自适应缩字下限（相对原字号 55%）；到限仍溢出的极端长行才截断 */
    const val LYRIC_AUTOFIT_MIN_SCALE = 0.55f

    // ---- 歌词行槽版式（2026-10-30 三定稿：更紧凑行距 + 全局下移 + 第二行高亮）----

    /**
     * 歌词行槽高度 = 歌词视口高 × 此系数（**沉浸隐藏态**值）。
     * 2026-10-30 用户定稿「隐藏态多排几行」→ 0.12：隐藏态行距明显收窄、
     * 同屏可见约 8 行；字号**不再随 [IDLE_DISC_SCALE] 放大**（由隐藏态更高的
     * [LYRIC_SLOT_FILL_*_IDLE] 字占比兜住，字号与菜单可见态基本一致）。
     * 菜单可见态用 [LYRIC_LINE_SLOT_FRACTION_VISIBLE]（0.145）。
     */
    const val LYRIC_LINE_SLOT_FRACTION = 0.12f

    /**
     * 高亮行中心在歌词视口中的落点（占视口高比例，0..1）。
     * **0.40 = 2026-10-30「明显下移」定稿（菜单可见态）** —— 同时构成「第二行高亮」
     * 版式：高亮行上方恰好保留一行普通行。
     * 隐藏态用 [LYRIC_HIGHLIGHT_TARGET_IDLE_FRACTION]（图一定稿 0.50）。
     */
    const val LYRIC_HIGHLIGHT_TARGET_FRACTION = 0.40f

    /**
     * **沉浸隐藏态（歌词开 + 5s 菜单隐藏）**的高亮行落点：
     * 高亮行 = 视口（≈屏幕）高 0.50（居中）；行槽收窄到 [LYRIC_LINE_SLOT_FRACTION]=0.12
     * 多排几行，字号占比由 [LYRIC_SLOT_FILL_CURRENT_IDLE]/[LYRIC_SLOT_FILL_ROW_IDLE] 兜住
     * （字号与可见态基本一致、不再随 [IDLE_DISC_SCALE] 放大），顶部渐隐保证不与标题/歌手堆叠。
     */
    const val LYRIC_HIGHLIGHT_TARGET_IDLE_FRACTION = 0.50f

    // ---- 2026-10-30 十九轮：歌词开启 + 菜单可见态（非 5s 隐藏）右侧列版式 ----
    // ⚠️ 全部为**比例常量**（占视口高 / 屏幕高），任意分辨率与宽高比自适应；
    //     且运行时按 idleProgress 在「可见态值 ↔ 隐藏态原值」间插值 ——
    //     5s 沉浸隐藏态严格保持图一定稿，不受本组参数影响。

    /**
     * 歌词开启且菜单可见时，右列（标题 / 歌手 / 歌词）**字号统一缩小系数**。
     * 用户要求「缩小 10%–15%」→ 取中 0.88（-12%）。只乘字号、不动布局盒；
     * 隐藏态经 idleProgress 归位为 1（图一字号）。
     */
    const val LYRICS_ON_FONT_SCALE = 0.88f

    /**
     * 歌词开启且导航（chrome）仍可见时，**歌词正文**的放大系数。
     * 用户要求：导航没隐藏时歌词「大一号」，导航 5s 隐藏（沉浸态）经 idleProgress 归位 1。
     * 只作用于歌词正文，标题/歌手仍走 [LYRICS_ON_FONT_SCALE]（-12% 让位逻辑不变）。
     */
    const val LYRICS_CHROME_VISIBLE_FONT_SCALE = 1.15f

    /**
     * 歌词容器的**顶部保护内边距**（占歌词视口高的比例）。
     * 稳态下「高亮行的上一行」恰好落在歌手名同一高度（实测 0.277 vs 歌手 0.281），
     * 仅靠顶部渐隐兜底 —— 给容器加这段内边距后歌词整体下压，与标题/歌手带
     * 拉开真实间隙；行槽/锚点都是相对量，自动跟随。隐藏态归零（图一无叠字）。
     */
    const val LYRIC_TOP_GUARD_FRACTION = 0.08f

    /**
     * 菜单可见态的**行槽系数**（[LYRIC_LINE_SLOT_FRACTION]=0.12 的隐藏态更宽松），
     * 行距与段落间距同步；隐藏态收窄到 0.12 多排几行（字占比同步提高、字号不变）。
     */
    const val LYRIC_LINE_SLOT_FRACTION_VISIBLE = 0.145f

    /**
     * 标题/歌手块的**下移量**（占屏幕高比例，用户「增加 margin-top」；约 26px@1440p）。
     * 与固定上提 [LYRICS_TITLE_RISE] 叠加：可见态 = −RISE + 本量，隐藏态 = −RISE。
     */
    const val LYRICS_TITLE_DROP_FRACTION = 0.018f

    // ---- 5 行布局的行疏密（2026-10-30：字号档位以标题为基准 + 行槽预算封顶）----

    /**
     * 歌词行**渲染后**应占「行槽高度」的比例上限（行槽 = 视口高 × [LYRIC_LINE_SLOT_FRACTION]）。
     *
     * 2026-10-30 字号层级改为以标题为基准（当前行 = title+1 = display 档、
     * 普通行 = title-1 = subtitle 档，见 LyricsView.cappedSp）——常规屏幕直接
     * 用 FontSize 档位；仅当极端矮视口下"档位字号"装不进行槽时才按这里的
     * 预算封顶。中日韩字形墨高约 em 的 0.85，0.72 的当前行预算 ⇒ 墨高 ≈ 行槽
     * 0.61、行间空隙 ≈ 字高 0.75 倍，仍在舒适版式区。
     */
    const val LYRIC_SLOT_FILL_CURRENT = 0.80f
    const val LYRIC_SLOT_FILL_ROW = 0.62f

    /**
     * **沉浸隐藏态**的字占行槽比例上限（2026-10-30「隐藏态多排几行、字号不变」定稿）：
     * 行槽从 0.1867×屏高（0.16×[IDLE_DISC_SCALE]）收窄到 0.12×屏高后，
     * 字占比必须同步提高，否则档位字号会被行槽预算（cap）压小 ——
     * 0.12×0.95 ≈ 0.114（当前行）/ 0.12×0.85 ≈ 0.102（普通行），
     * 与可见态 0.145×0.80=0.116 / 0.145×0.62=0.0899 基本持平 ⇒ 字号肉眼不变。
     */
    const val LYRIC_SLOT_FILL_CURRENT_IDLE = 0.95f
    const val LYRIC_SLOT_FILL_ROW_IDLE = 0.85f

    /**
     * 字号绝对下限（sp）：防止极端视口尺寸 / 系统字体缩放下算出离谱的值。
     */
    const val LYRIC_FONT_MIN_SP = 12f

    /**
     * 切歌后**维持上一首布局**的等待窗口（ms）。
     *
     * 切歌时歌词会先被清空再异步解析，中间有一段 `lyrics == null`。
     * 布局若立刻跟随，会先闪回"无歌词"再跳回"有歌词"（用户可见的抖动）。
     * 这里等这么久：解析成功立即恢复；超时仍为 null 才判定新歌确实无歌词。
     * 取 1200ms —— 覆盖本地内嵌/本地 LRC 的解析与一次缓存命中，网络取词另走自己的超时。
     */
    const val LYRICS_SETTLE_MS = 1_200L

    // ==================== 沉浸隐藏（5s 无操作） ====================

    /**
     * 播放中 5s 无操作 → 隐去装饰性 UI（顶栏 / 进度条 / 底部控制栏 / 音质角标）的转场时长。
     * 与页面进入同级（§10.4：300–600ms），保证"藏"与"显"是同一套节奏。
     *
     * ⚠️ 歌词工具行（内嵌/本地/在线/缓存 + 高亮 + 字号 + 睡眠 + 音量）**不参与隐藏** ——
     * 它是歌词阅读态的一部分，且隐去会让歌词区整体位移。
     */
    const val IDLE_HIDE_MS = 420

    /**
     * 沉浸隐藏时**黑胶盘放大倍数** —— 隐藏转场的主体特效。
     *
     * 2026-10-30 用户定稿：盘径改为屏高定径（可视态 0.60，见 [DISC_HEIGHT_FRACTION]），
     * 隐藏态要求占屏高 0.70 → 本倍数 = 0.70 / 0.60 ≈ 1.1667。
     * 用 `graphicsLayer` 缩放（不动布局占位），因此不会把标题/歌词挤位。
     */
    const val IDLE_DISC_SCALE = 1.1667f

    // ==================== 曲名 / 歌手（§6.2） ====================

    /**
     * 黑胶盘**整屏垂直居中**补偿：与运行时实测的底部控制区 / 顶栏高度配合，
     * 令 `centerOffsetExtra = 底部控制区/2 − 本值 − 顶栏/2` 尽量接近 0，
     * 使 CoverColumn 的自校准把盘心精确落在屏幕中线。
     *
     * 2026-10-29「减少底部留白」把 [BOTTOM_BAR_BOTTOM_PAD] 12→8dp（底部控制区矮 4dp），
     * 本值同步 8→6dp 抵消由此产生的 2dp 居中漂移；主 Column 底部 padding 的变化由
     * 自校准吸收、不参与此处。这样收紧底部留白后黑胶仍保持原居中位置不变。
     */
    val DISC_CENTER_PAD_COMPENSATION = 6.dp

    /**
     * 黑胶盘**整屏垂直居中**补偿之二：顶栏（TvTopNavBar）高度的一半。
     * 顶栏 = 36dp 内容 + 上下各 16dp padding = 68dp；可视态时它把内容区往下推，
     * 隐藏态由 AppRoot 淡出移除（内容区上扩）—— 因此**只在可视态**参与补偿。
     */
    val TOP_BAR_HALF_HEIGHT = 34.dp

    /**
     * 音源工具行 overlay 与**内容区顶带**的间距。
     *
     * 2026-10-30 用户定稿：工具行从右列顶部**上移到内容区顶带**（顶栏正下方、
     * 标题上方的一条空带 —— 主 Column 自身的 top padding），标题**不再下移避让**。
     * 已收到 0dp：贴顶由 [LYRICS_TOOLBAR_TOP_RAISE] 统一承担。
     */
    val LYRICS_TOOLBAR_TOP_PAD = 0.dp

    /** 音源工具行 overlay 的额外上提量（浮层，抵消 SourceTag 按钮内边距让文字贴住顶栏底边） */
    val LYRICS_TOOLBAR_TOP_RAISE = 20.dp

    /** 音源工具行 overlay 距屏幕右缘的内缩（浮层，可越过内容列的基准留白） */
    val LYRICS_TOOLBAR_EDGE_PAD = 8.dp

    /**
     * 歌词区标题块的**顶部间距**（右列内）。
     *
     * 2026-10-29 用户反馈「视觉重心偏上、头重脚轻、减少顶部留白」：12 → 4dp。
     * 2026-10-30 用户定稿「工具行往上移、标题不要动」：工具行 overlay 上移到
     * 内容区顶带（见 [LYRICS_TOOLBAR_TOP_PAD]），标题维持 4dp 原位 ——
     * 撤销此前为避让工具行而做的 48dp 下移。
     */
    val LYRICS_TITLE_TOP_PAD = 4.dp

    /**
     * 标题/歌手块的**绘图上移量**（offset，不改变布局）。
     *
     * 2026-10-30 用户定稿「标题和歌手太靠下」：工具行贴顶后，标题块再整体上提
     * 16dp 缩短与顶栏的空档 —— 用 offset 只动绘制、不动测量，歌词行槽与黑胶
     * 居中补偿完全不受影响（主 Column 顶距是黑胶居中公式的一部分，不能轻动）。
     * 2026-10-30 图一定稿再上提至 27dp：隐藏态实测标题 0.223 / 歌手 0.299 屏高，
     * 图一目标 0.197 / 0.270 —— 差值 ≈11dp，与行槽/锚点一起精确复刻图一比例。
     */
    val LYRICS_TITLE_RISE = 27.dp

    /** 标题 + 歌手与歌词内容之间的间距（2026-10-29 收紧：6 → 4dp） */
    val LYRICS_TITLE_BOTTOM_PAD = 4.dp

    /**
     * 歌词内容区的**上下内缩量**。
     *
     * 只缩行高、不动列宽（列宽仍由 0.9 : 1.1 的 weight 决定），
     * 因此黑胶 / 歌词两列的构图比例保持不变，只是歌词块不再上下顶满。
     *
     * 2026-10-29 用户要求「区域大一点」：32 → 8dp。
     * 原因：超宽横屏（3200×1440@526dpi）右列总高仅约 257dp，32dp×2 的内缩加上
     * 工具行 + 标题块后歌词视口只剩约 45dp —— 连一行都放不下，观感仍是"一条缝"。
     * 收到 8dp 后把空间全部让给歌词正文本身。
     *
     * 2026-10-29 再收紧「减少底部留白 / 上下均衡」：8 → 4dp，配合 5 行固定行槽，
     * 让歌词内容再向上下各多铺 4dp，右列不再"上重下空"。
     */
    val LYRICS_PANEL_VERTICAL_PAD = 4.dp

    /**
     * 曲名（大字）的**最大宽度**。
     *
     * 目的是给 [com.lnh.nasmusictv.ui.components.MarqueeText] 一个**确定的限宽边界**：
     * 宽容器（有歌词时占半屏的右列、无歌词时的整屏左列）下，超长曲名不会顶到屏幕边缘，
     * 而是在这段宽度内**单行横向滚动（从右往左跑马灯）** —— 既不换行撑高版面，也不被省略号截断。
     *
     * 取 560dp：1920dp 电视上约合右列宽度的 2/3，居中排布时两侧留白均匀。
     */
    val SONG_TITLE_MAX_WIDTH = 560.dp

    // ==================== 页面转场（§10.3 / §10.4） ====================

    /** 页面进入（§10.4：300–400ms） */
    const val PAGE_ENTER_MS = 420

    /** 页面进入起始缩放：从略大收束到 1.0，形成"聚焦 / 全屏纯享"的切入感 */
    const val PAGE_ENTER_SCALE_FROM = 1.06f

    // ==================== 特效开关（§10.2 / §10.1-7） ====================

    /**
     * P2 特效总开关（§10.2：「P2 可选，默认关闭」「P2 特效默认关闭且可开关」）。
     *
     * 本项目**未实现**任何 P2 特效（封面取色流动、氛围粒子、唱片纹理动态高光、
     * 背景视差、页面内音频可视化）—— 因此默认 false 且当前无消费点，
     * 保留常量是为了让"将来若加 P2，必须从这里开关"成为显式约束，
     * 而不是散落成各处 if (某 flag)。
     *
     * 独立的「频谱」按钮是**独立页面**（VisualizerScreen），不属于播放页背景特效，不受此开关约束。
     */
    const val P2_EFFECTS_ENABLED = false

    // ==================== 性能降级（§11-7） ====================

    /**
     * 是否为**低端设备**。低端设备自动降级（§11-7）：
     * 关闭背景呼吸、焦点光晕呼吸、黑胶光晕呼吸；保留静态模糊背景与必要的状态反馈。
     *
     * 判据（两条任一命中即为低端）：
     * - `ActivityManager.isLowRamDevice()`：系统声明的低内存设备（TV 盒子常见）
     * - 可用 CPU 核数 ≤ 2：老电视/廉价盒子典型配置
     *
     * 为什么不用 `isTVDevice()`：那是设备形态嗅探，与性能无关。
     */
    fun isLowPerfDevice(context: Context): Boolean {
        val lowRam = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        }.getOrDefault(false)
        return lowRam || Runtime.getRuntime().availableProcessors() <= 2
    }
}
