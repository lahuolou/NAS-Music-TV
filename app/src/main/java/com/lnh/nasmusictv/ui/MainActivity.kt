package com.lnh.nasmusictv.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.lnh.nasmusictv.NasMusicApp
import com.lnh.nasmusictv.player.PlaybackService
import com.lnh.nasmusictv.ui.components.AppRoot
import com.lnh.nasmusictv.ui.components.ConnectPromptDialog
import com.lnh.nasmusictv.ui.components.DpadInputTracker
import com.lnh.nasmusictv.ui.screens.ExitConfirmDialog
import com.lnh.nasmusictv.ui.theme.NASMusicTVTheme
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.viewmodel.MainViewModel
import com.lnh.nasmusictv.data.model.Screen
import com.lnh.nasmusictv.util.AppLog
import com.lnh.nasmusictv.util.MediaKeyHandler
import com.lnh.nasmusictv.util.NetworkMonitor
import kotlinx.coroutines.launch
/**
 * 主 TV Activity —— NAS Music TV
 */
// UnstableApi 属 androidx @RequiresOptIn 机制，须用 androidx.annotation.OptIn。
@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    // Level 1: 对话框 BACK 键回调 —— 当对话框（输入对话框、退出确认等）打开时设置
    private val dialogBackHandler: MutableState<(() -> Unit)?> = mutableStateOf(null)
    // Level 1.5: 列表回到顶部回调 —— 列表已滚动时按 BACK 先回顶
    private val listBackHandler: MutableState<(() -> Boolean)?> = mutableStateOf(null)
    // Level 2: 页面导航 BACK 键回调 —— 当不在 NowPlaying 页面时设置为导航函数
    private val navigateBackHandler: MutableState<(() -> Unit)?> = mutableStateOf(null)
    // Level 3: 退出确认对话框显示标志 —— 在 NowPlaying 页面时按下 BACK 设为 true
    private val showExitConfirm: MutableState<Boolean> = mutableStateOf(false)
    // 全屏沉浸模式状态 — 由 AppRoot 持有一份引用，同时 Activity.onKeyDown 也需要读取
    private val isImmersiveMode: MutableState<Boolean> = mutableStateOf(false)
    // 2026-10-30 极简遥控器：BACK 唤醒播放页隐藏态的动作（组合期注入 = bump userActivityTick）
    private var backWakeAction: (() -> Unit)? = null
    // 播放页沉浸隐藏态（AppRoot 上报）—— BACK 分层返回 Level 1.7 读取
    private val chromeIdleHidden: MutableState<Boolean> = mutableStateOf(false)
    private lateinit var networkMonitor: NetworkMonitor

    // SAF 树选择器（§8.8.4）：导出到外接设备时启动系统文件夹选择器
    private val exportTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val app = application as? NasMusicApp
        if (uri != null) {
            app?.exportCoordinator?.onTreeGranted(uri)
        } else {
            app?.exportCoordinator?.onTreeUnavailable()
        }
    }

    // ── 照片墙（阶段 9，§9）─────────────────────────────────────────────────
    // ⛔ 权限对话框**不在这里触发** —— 它由图库开关驱动（§6.2 / §9.4 官方要求：
    //    "Request these permissions when the app needs storage access, instead of at startup."）。
    //    本字段只负责「注册 + 把结果转交」，绝不放进 onCreate 里无条件 launch。

    /** 照片权限对话框结果 */
    private val photoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        // ⛔ 刻意**忽略**回调里的 Map：Android 14+「仅选择照片」下 `READ_MEDIA_IMAGES`
        // 可能是 granted 却只是**会话级**授权（§9.6）⇒ 一律以重新读取的三态为准。
        viewModel.visualizerVM.onPhotoPermissionResult()
    }

    // ── 本地音乐（§5.5 权限瘦身）───────────────────────────────────────────
    // ⛔ 权限对话框**不在这里触发** —— 它由「设置 → 本地音乐」的总开关驱动
    //    （§5.5 官方要求：仅在需要访问时申请，绝不在启动时申请）。
    //    本字段只负责「注册 + 把结果转交」，绝不放进 onCreate 里无条件 launch。

    /** 本地音乐权限对话框结果 */
    private val localMusicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        // ⛔ 刻意**忽略**回调里的 Map（照抄上方照片墙写法）：一律以重新读取的状态为准。
        viewModel.onLocalMusicPermissionResult()
    }

    /** 照片目录选择器（§6.3 路线 B）：与导出同款契约，结果走照片墙自己的通道 */
    private val photoDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        viewModel.visualizerVM.onPhotoDirectoryPicked(uri)
    }

    /**
     * 在每次 Activity 创建（含 recreate）时应用存储的语言设置。
     * resources.updateConfiguration() 仅影响 Application 级别资源，
     * 通过 attachBaseContext 创建带正确 locale 的 Context，确保 Compose 使用正确的资源配置。
     *
     * ⛔ **覆盖配置里只能放 locale**：
     * `createConfigurationContext(x)` 的入参 x 会成为该 Context 的 Resources **override 配置**，而
     * `ResourcesManager.applyConfigurationToResourcesLocked()` 在**每一次**全局配置变更时都会执行
     * `tmpConfig.setTo(全局配置); tmpConfig.updateFrom(override 配置)` ——
     * 而 `Configuration.updateFrom()` 是**逐字段**判定「非 undefined 才写入」，
     * 于是 override 里凡是非 undefined 的字段都会被**重新写回旧值**。
     *
     * 早期实现传的是整份 `Configuration(newBase.resources.configuration)` 拷贝 →
     * 方向 / 屏幕尺寸 / 密度 / `screenLayout` / `uiMode` / `windowConfiguration`
     * 全部被**钉死在 Activity 启动那一刻**（曾因此出现「旋转后 UI 形态不跟随」的回归）。
     *
     * 因此这里**只构造「仅含 locale」的覆盖配置**：其余字段一律保持 undefined
     * （`Configuration()` 的默认值已是 `ORIENTATION_UNDEFINED` / `SCREEN_WIDTH_DP_UNDEFINED` /
     * `DENSITY_DPI_UNDEFINED` / `SCREENLAYOUT_UNDEFINED`，且其 `WindowConfiguration` 的
     * 空边界在 `updateFrom()` 中是 no-op），方向与屏幕尺寸一律跟随系统全局配置。
     * ⚠️ `fontScale` 是唯一的例外：`Configuration()` 的默认值是 **1**（不是 undefined），
     * 必须显式置 **0**（0 即 `Configuration.unset()` 采用的「未设置」语义），
     * 否则会把系统字体缩放钉死在启动值。
     */
    override fun attachBaseContext(newBase: Context) {
        val lang = com.lnh.nasmusictv.data.prefs.AppPreferences.getInstance(newBase)
            .languagePrefs.getLanguageSync()
        val locale = when (lang) {
            "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
            "en" -> java.util.Locale.US
            else -> com.lnh.nasmusictv.NasMusicApp.getSystemLocale() // 跟随系统：读取真正的系统 locale
        }
        // ⛔ 只下发 locale：其余字段必须保持 undefined，否则会钉死方向/屏幕尺寸/密度（见上方 KDoc）
        val localeOverride = Configuration().apply {
            setLocale(locale)
            fontScale = 0f
        }
        super.attachBaseContext(newBase.createConfigurationContext(localeOverride))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TV-only：电视无系统栏，且手机设备不再作为播放客户端，移除手机端 system-bar 隐藏逻辑。

        // SAF 树选择器（§8.8.4）：注入到 ExportCoordinator，导出时启动系统文件夹选择器
        (application as? NasMusicApp)?.exportCoordinator?.let { it.treePickLauncher = {
            exportTreeLauncher.launch(null)
        } }

        // 照片墙权限链路（阶段 9）：ViewModel 不能自己 registerForActivityResult，
        // 只能由 Activity 注入（与上一行的导出做法完全一致）。
        // ⚠️ 这里只是**接线**，不发起任何请求 —— 请求由「图库」开关触发（§6.2）。
        viewModel.visualizerVM.photoPermissionLauncher = {
            photoPermissionLauncher.launch(
                com.lnh.nasmusictv.util.PermissionHelper.getPhotoPermissions()
            )
        }
        viewModel.visualizerVM.photoDirectoryLauncher = {
            photoDirectoryLauncher.launch(null)
        }
        // 本地音乐权限链路（§5.5）：同款接线，不发起任何请求 —— 请求由总开关触发。
        viewModel.localMusicPermissionLauncher = {
            localMusicPermissionLauncher.launch(
                com.lnh.nasmusictv.util.PermissionHelper.getLocalMusicPermissions()
            )
        }

        setContent {
            val settings by viewModel.appSettings.collectAsState(initial = com.lnh.nasmusictv.data.model.AppSettings())
            // TV-only：形态因子 UiMode 已随手机 UI 下线移除，统一走 TV 路径。

            // 全屏页状态（沉浸模式 / 覆盖层显隐）
            val showMv by viewModel.mvVM.showMv.collectAsState(initial = false)
            val showKaraoke by viewModel.vocalVM.showKaraoke.collectAsState(initial = false)
            val showVisualizer by viewModel.visualizerVM.showVisualizer.collectAsState(initial = false)

            // TV-only：电视无系统栏，手机端 system-bar 隐藏逻辑已移除。

            androidx.compose.runtime.CompositionLocalProvider(
                com.lnh.nasmusictv.ui.theme.LocalFontAdjustment provides settings.fontAdjustment,
                // 沉浸式播放页的启用条件（最高优先级 A1/A2）：
                // 直接读用户的**主题意图** AppSettings.darkTheme，而非由色值亮度反推。
                com.lnh.nasmusictv.ui.theme.LocalDarkTheme provides settings.darkTheme,
                // 减弱动效总闸（§10.1-7/§11-8）：取 AppSettings.animationsEnabled 的反值。
                // 另外**低端设备自动降级**（§11-7）：低内存设备 / ≤2 核的老电视盒子上
                // 连续动画会明显掉帧，此时无论用户设置如何都强制走静态呈现 ——
                // 放在注入点统一处理，避免每个组件各判一次（§12-10「判断集中管理」）。
                com.lnh.nasmusictv.ui.theme.LocalReduceMotion provides (
                    !settings.animationsEnabled ||
                        com.lnh.nasmusictv.ui.theme.PlayerVisualSpec.isLowPerfDevice(this@MainActivity)
                    )
            ) {
            NASMusicTVTheme(darkTheme = settings.darkTheme) {
                // 暴露当前 Activity 给子组件，用于注册对话框的 BACK 键处理
                CompositionLocalProvider(
                    LocalDialogBackHandler provides dialogBackHandler,
                    LocalListBackHandler provides listBackHandler,
                    LocalNavigateBackHandler provides navigateBackHandler,
                    LocalShowExitConfirm provides showExitConfirm
                ) {
                    val showConnectPrompt by viewModel.serverVM.showConnectPrompt.collectAsState(initial = false)
                    val connectMessage by viewModel.connectMessage.collectAsState(initial = null)
                    val errorMessage by viewModel.errorMessage.collectAsState(initial = null)

                    // 用户活动信号——播放页播放中无操作 5s 自动隐藏顶栏
                    //（AppRoot 消费）；任意按键/触摸 +1，立即恢复显示并重置计时器
                    val userActivityTick = remember { mutableLongStateOf(0L) }
                    // 2026-10-30：播放页沉浸隐藏态 + 唤醒动作（BACK 分层返回用）。
                    // AppRoot 上报隐藏态（chromeIdleHidden 为 Activity 字段）；
                    // 唤醒 = bump userActivityTick（任意键同路径）。
                    DisposableEffect(Unit) {
                        backWakeAction = { userActivityTick.longValue++ }
                        onDispose { backWakeAction = null }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NasMusicColors.Background)
                            // 触摸（手机横屏/触屏）也视为用户活动
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.pressed }) userActivityTick.longValue++
                                    }
                                }
                            }
                            // ⛔ 不用 `Activity.dispatchKeyEvent` 记方向键：那是 androidx.core 的
                            // 受限 API，lint 报 RestrictedApi 会卡住构建（CI 的 lint 是阻塞的）。
                            // `onPreviewKeyEvent` 挂在根节点上，Compose 会沿焦点目标的祖先链
                            // 从根往下派发按键 ⇒ 覆盖全部页面，且在按键被消费之前先经过。
                            //
                            // 覆盖不到 Compose `Dialog`（自带独立 Window / 独立 ComposeView），
                            // 但那不构成问题：用户必须先用方向键走到那个弹窗入口，
                            // 标志在打开弹窗之前就已经置上了。
                            .onPreviewKeyEvent { event ->
                                // ⛔ 直接取 `nativeKeyEvent` 的 keyCode，而不是逐个 Compose `Key` 映射 ——
                                // 后者要维护一张几十项的对照表，且新增遥控器键就漏一个。
                                val native = event.nativeKeyEvent
                                DpadInputTracker.noteKeyEvent(native.keyCode, native.action)
                                userActivityTick.longValue++
                                false // 只观察，不消费
                            }
                    ) {
                        AppRoot(
                            viewModel = viewModel,
                            isImmersiveMode = isImmersiveMode,
                            onConnect = { config ->
                                lifecycleScope.launch {
                                    viewModel.connectToServer(config)
                                }
                            },
                            userActivityTick = userActivityTick.longValue,
                            onIdleHiddenChange = { chromeIdleHidden.value = it }
                        )

                        // Level 3: 退出确认对话框（在 NowPlaying 页面按 BACK 键时显示）
                        if (showExitConfirm.value) {
                            ExitConfirmDialog(
                                onConfirm = {
                                    showExitConfirm.value = false
                                    val app = application as? NasMusicApp
                                    // 释放播放器资源
                                    app?.playerManager?.release()
                                    stopService(Intent(this@MainActivity, PlaybackService::class.java))
                                    // 注销 Jellyfin session，确保 HTTP 请求完成后再杀进程。
                                    // 修复（M-1）：logout 网络请求限时 1.5s——OkHttp 超时最长 15s，
                                    // 无限等待会在网络异常时造成主线程 ANR；超时仍照常退出。
                                    // P3：整段移出主线程（原 runBlocking 会让 UI 冻结至多 1.5s），
                                    // 改为 IO 协程等待完成，再回主线程做收尾。
                                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            kotlinx.coroutines.withTimeout(1500) {
                                                app?.backendRegistry?.disconnect()
                                            }
                                            AppLog.d("MainActivity", "exit: backend disconnected")
                                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                            AppLog.w("MainActivity", "exit: disconnect timeout (1.5s), exiting anyway")
                                        } catch (e: Exception) {
                                            AppLog.w("MainActivity", "exit: disconnect failed", e)
                                        } finally {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                finishAffinity()
                                                android.os.Process.killProcess(android.os.Process.myPid())
                                            }
                                        }
                                    }
                                },
                                onDismiss = { showExitConfirm.value = false }
                            )
                        }

                        // 启动连接提示对话框
                        if (showConnectPrompt) {
                            ConnectPromptDialog(
                                serverDisplayName = viewModel.serverVM.serverDisplayName.value,
                                onConfirm = { viewModel.connectToSavedServer() },
                                onDismiss = { viewModel.serverVM.dismissConnectPrompt() }
                            )
                        }

                        // D-3: 错误提示消息（数据加载/操作失败时显示，5秒后自动清除）
                        errorMessage?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(top = 80.dp)
                                    .background(
                                        color = NasMusicColors.Danger.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 32.dp, vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = msg,
                                    color = NasMusicColors.TextPrimary,
                                    fontSize = FontSize.button(),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 连接结果提示消息（短时悬浮显示）
                        connectMessage?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (errorMessage != null) 140.dp else 80.dp)
                                    .background(
                                        color = NasMusicColors.Surface.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 32.dp, vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = msg,
                                    color = NasMusicColors.TextPrimary,
                                    fontSize = FontSize.button(),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
}
            }
        }
    }

    // 分层 BACK 键处理：Level 0 → Level 1 → Level 1.5 → Level 2 → Level 3
        // Level 0: 沉浸模式 → 退出全屏
        // Level 1: 关闭对话框（由 dialogBackHandler 控制）
        // Level 1.5: 列表回到顶部（由 listBackHandler 控制，返回 true 表示已消费）
        // Level 2: 从其他页面导航回播放页（由 AppRoot 动态设置 navigateBackHandler）
        // Level 3: 在播放页显示退出确认（设置 showExitConfirm = true）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Level 0: 沉浸模式下 BACK → 退出全屏，不往下传递
                if (isImmersiveMode.value) {
                    isImmersiveMode.value = false
                    return
                }

                // Level 1: 如果有对话框打开 → 先关闭对话框
                val dialogHandler = dialogBackHandler.value
                if (dialogHandler != null) {
                    dialogHandler()
                    return
                }

                // Level 1.5: 列表已滚动 → 先滚动到顶部
                val listHandler = listBackHandler.value
                if (listHandler != null && listHandler()) {
                    return
                }

                // Level 1.7（2026-10-30 极简遥控器分层返回）：播放页沉浸隐藏态
                // → 先唤醒控件（焦点保持在「暂停/播放」），不弹退出确认
                if (chromeIdleHidden.value) {
                    backWakeAction?.invoke()
                    return
                }

                // Level 2: 如果不在 NowPlaying 页面 → 导航回播放页
                val navHandler = navigateBackHandler.value
                if (navHandler != null) {
                    navHandler()
                    return
                }

                // Level 3: 已经在 NowPlaying 页面 → 显示退出确认
                showExitConfirm.value = true
            }
        })

        // Android TV: 确保窗口可聚焦
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 启动播放服务
        startService(Intent(this, PlaybackService::class.java))

        // F2-2 / L3：通知栏"播放模式"按钮接线——service 发出的事件转交 PlayerViewModel。
        // 切换动作必须由 UI 侧执行：playMode 的真相在 PlayerViewModel（B-13：不归
        // PlayerManager），服务侧独立完成会与界面显示脱节。
        // 用 lifecycleScope 而**不是** repeatOnLifecycle(STARTED)：本事件在 Activity 退到
        // 后台（按 Home）后仍需可用——那种场景下用户正是通过通知栏控制播放，若按 STARTED
        // 退订会让按钮静默失效（相比旧实现反而是退化）。lifecycleScope 随 Activity 销毁
        // 自动取消 → 自动退订，无需在 onDestroy 手动解绑，也不会像旧闭包那样被 Application
        // 长期持有上一个 Activity 的 ViewModel。
        lifecycleScope.launch {
            (application as? NasMusicApp)?.playModeToggleEvents?.collect {
                viewModel.playerVM.togglePlayMode()
            }
        }

        // D-2: 网络状态监听
        networkMonitor = NetworkMonitor(
            context = this,
            onNetworkAvailable = { viewModel.onNetworkAvailable() },
            onNetworkLost = { viewModel.onNetworkLost() }
        )
        networkMonitor.register()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.unregister()
        // 兜底清理：确保播放服务和后端连接被释放
        // 正常退出流程（退出对话框）已在 onConfirm 中处理，这里处理异常退出场景
        // 修复（M-2）：仅在本页真正结束（isFinishing，如退出确认/finishAffinity）时才清理；
        // 配置重建（旋转/主题切换/分屏）isFinishing=false，原无条件 release+stopService 会误杀后台播放；
        // 从最近任务划掉应用同样保留播放（通知栏可控），符合媒体类应用预期
        if (!isFinishing) return
        val app = application as? NasMusicApp ?: return
        // 2026-09-25 审查修复（#10）：launcher 闭包捕获 Activity，真退出时置空断开引用
        //（配置重建 isFinishing=false 走不到这里，onCreate 会重新注入，不影响功能）
        runCatching {
            viewModel.visualizerVM.photoPermissionLauncher = null
            viewModel.visualizerVM.photoDirectoryLauncher = null
            viewModel.localMusicPermissionLauncher = null
            // 2026-09-26 审查补修（#5 遗留）：ExportCoordinator 由 Application 持有、跨 Activity
            // 存活，其 treePickLauncher 闭包同样捕获本 Activity ⇒ 真退出时对称置空断开引用
            //（配置重建 isFinishing=false 走不到这里，onCreate 会重新注入）
            app.exportCoordinator.treePickLauncher = null
        }
        // L3：此处**不再**需要手动解绑播放模式回调——订阅跑在 lifecycleScope 中，
        // onDestroy 时作用域自动取消、SharedFlow 自动退订，时序窗口与残留引用一并消失
        // 停止播放服务（如果仍在运行）
        try {
            stopService(Intent(this, PlaybackService::class.java))
        } catch (_: Exception) {}
        // 释放播放器资源（Handler、listener）
        try {
            app.playerManager.release()
        } catch (_: Exception) {}
        // 应用退出时断开后端连接，释放 OkHttp 连接池，防止连接泄漏
        // 使用 applicationScope 确保断开操作在 Activity 销毁后仍能执行
        app.applicationScope.launch {
            try {
                app.backendRegistry.disconnect()
                AppLog.d("MainActivity", "onDestroy: backend disconnected")
            } catch (e: Exception) {
                AppLog.w("MainActivity", "onDestroy: disconnect failed", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Android TV: 主动请求窗口焦点
        window.decorView.requestFocus()
        // 阶段 9（§6.2 / §9.6）：权限可能在 onStart / onResume 之间被用户改掉（App 不重启）
        // ⇒ 每次回到前台都重判三态：撤销了就把「图库」开关回弹并提示，
        //   SAF 目录授权失效就清掉目录设置（否则设置页显示「已选目录」却读不到）。
        // ⚠️ 不能只在启动判一次 —— 那正是官方点名要避免的写法。
        try {
            viewModel.visualizerVM.refreshPhotoAccess()
        } catch (e: Exception) {
            AppLog.w("MainActivity", "refreshPhotoAccess failed", e)
        }
        // 本地音乐（§5.5）：权限可能在系统设置里被撤销 ⇒ 转移式回弹（见 MainViewModel）
        try {
            viewModel.refreshLocalMusicAccess()
        } catch (e: Exception) {
            AppLog.w("MainActivity", "refreshLocalMusicAccess failed", e)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // G-1: HDMI-CEC / 蓝牙遥控器媒体键映射
        val handled = MediaKeyHandler.handleKeyEvent(
            keyCode = keyCode,
            event = event,
            viewModel = viewModel,
            isImmersiveMode = isImmersiveMode.value,
            currentScreen = viewModel.navVM.currentScreen.value
        )
        if (handled) return true

        // MediaKeyHandler 未处理的键（如沉浸模式下的 OK 键退出全屏）
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (isImmersiveMode.value) {
                isImmersiveMode.value = false
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

}
