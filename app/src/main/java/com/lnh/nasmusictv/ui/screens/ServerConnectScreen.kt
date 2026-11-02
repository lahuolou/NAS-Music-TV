package com.lnh.nasmusictv.ui.screens

import com.lnh.nasmusictv.ui.theme.FontSize

import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.lnh.nasmusictv.NasMusicApp
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.net.ServerConfigTransferServer
import com.lnh.nasmusictv.util.NetworkUtils
import com.lnh.nasmusictv.util.QrCodeGenerator
import com.lnh.nasmusictv.data.model.ServerConfig
import com.lnh.nasmusictv.data.model.BackendDirectory
import com.lnh.nasmusictv.ui.components.FocusableSurface
import com.lnh.nasmusictv.ui.components.LocalFocusableContentColor
import com.lnh.nasmusictv.ui.theme.NasMusicColors
import com.lnh.nasmusictv.ui.theme.LocalUiMode
import com.lnh.nasmusictv.ui.theme.UiMode
import kotlinx.coroutines.launch
private enum class InputField {
    BASE_URL, USERNAME, PASSWORD, API_TOKEN, ACCESS_CODE, ROOT_PATH, SMB_SHARE, SMB_DOMAIN
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServerConnectScreen(
    initialConfig: ServerConfig,
    isConnected: Boolean,
    serverDisplayName: String,
    isConnecting: Boolean,
    onConnect: (ServerConfig) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用 initialConfig 作为 key，当 initialConfig 变化时（如 DataStore 加载完成后）
    // 字段会重新初始化，确保显示已保存的配置
    // 若 DataStore 中无保存值，使用内置默认值方便首次配置
    var backendType by remember(initialConfig) { mutableStateOf(initialConfig.backendType) }

    // 各后端默认端口
    fun defaultPort(type: String): String = when (type) {
        ServerConfig.TYPE_JELLYFIN -> "8096"
        ServerConfig.TYPE_NAVIDROME -> "4533"
        ServerConfig.TYPE_SUBSONIC -> "4533"
        ServerConfig.TYPE_DAOLIYU -> "4000"
        // 飞牛音乐（fnOS）音乐服务默认端口 5666（HTTP）/ 5667（HTTPS），不是 Web 的 80。
        // 依据：参考项目 fn-music-tv `ServerUrlNormalizer`（DEFAULT_HTTP_PORT = 5666）。
        ServerConfig.TYPE_FEINIU -> "5666"
        else -> "8096"
    }
    fun defaultUrl(type: String): String = ""

    var baseUrl by remember(initialConfig) {
        mutableStateOf(
            if (initialConfig.baseUrl.isNotBlank()) TextFieldValue(initialConfig.baseUrl)
            else TextFieldValue(defaultUrl(initialConfig.backendType))
        )
    }
    // 切换后端类型时，若 baseUrl 为空或是某个后端的默认值（说明用户未自定义），自动变更为新后端的默认地址
    LaunchedEffect(backendType) {
        val currentUrl = baseUrl.text.trim().removeSuffix("/")
        // 检查当前地址是否是任一后端的默认地址（用户没自定义过）
        val isDefaultUrl = listOf(
            ServerConfig.TYPE_JELLYFIN,
            ServerConfig.TYPE_NAVIDROME,
            ServerConfig.TYPE_SUBSONIC,
            ServerConfig.TYPE_DAOLIYU,
            ServerConfig.TYPE_FEINIU,
            ServerConfig.TYPE_WEBDAV,
            ServerConfig.TYPE_SMB
        ).any { defaultUrl(it) == currentUrl }
        if (isDefaultUrl) {
            baseUrl = TextFieldValue(defaultUrl(backendType))
        }
    }
    var username by remember(initialConfig) {
        mutableStateOf(
            if (initialConfig.username.isNotBlank()) TextFieldValue(initialConfig.username)
            else TextFieldValue()
        )
    }
    var password by remember(initialConfig) {
        mutableStateOf(
            if (initialConfig.password.isNotBlank()) TextFieldValue(initialConfig.password)
            else TextFieldValue()
        )
    }
    var apiToken by remember(initialConfig) {
        mutableStateOf(
            if (initialConfig.apiToken.isNotBlank()) TextFieldValue(initialConfig.apiToken)
            else TextFieldValue()
        )
    }
    // 飞牛「访问码 / 安全码」：仅飞牛音乐需要；其余后端不显示。
    // 属于凭据，加密落盘（AppPreferences.saveServerConfig），且从备份中剥离。
    // NAS 未开启访问码保护时留空即可——适配器会先探测 {origin}/access_code_verify，
    // 未开启时该端点 404/放行，留空不影响正常登录。
    var accessCode by remember(initialConfig) {
        mutableStateOf(
            if (initialConfig.accessCode.isNotBlank()) TextFieldValue(initialConfig.accessCode)
            else TextFieldValue()
        )
    }
    var rootPath by remember(initialConfig) { mutableStateOf(TextFieldValue(initialConfig.rootPath.ifBlank { "/" })) }
    var smbShare by remember(initialConfig) { mutableStateOf(TextFieldValue(initialConfig.smbShare)) }
    var smbDomain by remember(initialConfig) { mutableStateOf(TextFieldValue(initialConfig.smbDomain)) }
    // —— 手机扫码填入服务器配置（2026-09-22 用户需求）：手机表单 → 推送到电视 →
    // 自动填充下方表单，用户核对后用遥控器确认键连接（本流程不代替用户连接）——
    // 二维码常驻显示（2026-09-22 用户修正：TV 无触摸，不能依赖点击展开）；
    // HTTP 服务随页面开关：进入本页即启动，离开即关闭。
val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("") }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val configTransferServer = remember {
        ServerConfigTransferServer(
            context,
            onConfigReceived = { backend: String, url: String, user: String, pass: String, name: String,
                                 root: String, share: String, domain: String ->
                // NanoHTTPD 工作线程回调 → 主线程更新表单状态
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    baseUrl = TextFieldValue(url)
                    username = TextFieldValue(user)
                    password = TextFieldValue(pass)
                    backendType = backend
                    // WebDAV / SMB 附带目录与共享名字段（其余后端提交为空串，不覆盖已填内容）
                    if (backend == ServerConfig.TYPE_WEBDAV || backend == ServerConfig.TYPE_SMB) {
                        rootPath = TextFieldValue(root.ifBlank { "/" })
                    }
                    if (backend == ServerConfig.TYPE_SMB) {
                        smbShare = TextFieldValue(share)
                        smbDomain = TextFieldValue(domain)
                    }
                    statusMessage = context.getString(R.string.server_config_received_hint)
                }
            }
        )
    }
    DisposableEffect(Unit) {
        val ip = NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            val url0 = configTransferServer.buildUrl(ip)
            qrUrl = url0
            qrBitmap = QrCodeGenerator.generateQrBitmap(url0, 150)
            configTransferServer.startServer()
        } else {
            statusMessage = context.getString(R.string.server_config_no_ip)
        }
        onDispose { configTransferServer.stopServer() }
    }
    var activeInputField by remember { mutableStateOf<InputField?>(null) }
    var showDirectoryBrowser by remember { mutableStateOf(false) }
    var browsingPath by remember { mutableStateOf("/") }
    var browsingDirectories by remember { mutableStateOf<List<BackendDirectory>>(emptyList()) }
    var browsingLoading by remember { mutableStateOf(false) }
    var browsingError by remember { mutableStateOf("") }

    // 连接测试状态
    var isTesting by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("") }  // "" | "success:xxx" | "error:xxx"
    val testScope = rememberCoroutineScope()
    val appContext = LocalContext.current
    val backendRegistry = remember {
        (appContext.applicationContext as? NasMusicApp)?.backendRegistry
            ?: error("Application is not NasMusicApp")
    }
    fun loadNasDirectory(path: String) {
        browsingPath = path
        browsingLoading = true
        browsingError = ""
        showDirectoryBrowser = true
        testScope.launch {
            try {
                val config = ServerConfig(
                    backendType = backendType,
                    baseUrl = baseUrl.text.trim(),
                    username = username.text.trim(),
                    password = password.text,
                    rootPath = "/",
                    smbShare = smbShare.text.trim(),
                    smbDomain = smbDomain.text.trim()
                )
                browsingDirectories = backendRegistry.browseDirectories(config, path)
            } catch (e: Exception) {
                browsingDirectories = emptyList()
                browsingError = e.message?.take(120) ?: "目录读取失败"
            } finally {
                browsingLoading = false
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .heightIn(max = 900.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NasMusicColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：标题区（左）+ 手机扫码填入二维码（右，常驻显示）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    // 顶部服务器图标
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                NasMusicColors.Primary,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = NasMusicColors.TextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.server_config_title),
                        color = Color.White,
                        fontSize = FontSize.displayLarge()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.server_connect_desc),
                        color = NasMusicColors.TextSecondary,
                        fontSize = FontSize.button(),
                        textAlign = TextAlign.Center
                    )
                }
                // 二维码（常驻显示；token 随 URL 下发，HTTP 服务随页面开关）
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.server_config_scan_desc),
                            modifier = Modifier.size(150.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.server_config_scan_hint),
                            fontSize = 12.sp,
                            color = NasMusicColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } ?: run {
                        Text(
                            text = stringResource(R.string.server_config_qr_generating),
                            fontSize = 12.sp,
                            color = NasMusicColors.TextSecondary
                        )
                    }
                }
            }

            // 已连接状态
            if (isConnected) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            NasMusicColors.Primary.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态指示点
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                NasMusicColors.Primary,
                                RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.server_connected),
                            color = NasMusicColors.Primary,
                            fontSize = FontSize.subtitle()
                        )
                        if (serverDisplayName.isNotBlank()) {
                            Text(
                                text = serverDisplayName,
                                color = NasMusicColors.TextSecondary,
                                fontSize = FontSize.button()
                            )
                        }
                    }
                    // 断开按钮
                    FocusableSurface(
                        onClick = {
                            onDisconnect()
                            statusMessage = context.getString(R.string.server_disconnected)
                        },
                        shape = RoundedCornerShape(12.dp),
                        focusedScale = 1.08f,
                        animationDurationMs = 150,
                        containerColor = NasMusicColors.Danger,
                        contentColor = Color.White,
                        focusedContainerColor = NasMusicColors.Danger.copy(alpha = 0.85f),
                        focusedContentColor = Color.White,
                        pressedScale = 0.95f
                    ) {
                        Text(
                            text = stringResource(R.string.server_disconnect),
                            color = LocalFocusableContentColor.current,
                            fontSize = FontSize.button(),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 服务器类型选择
            Text(
                text = stringResource(R.string.server_type_label),
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.subtitle(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeCard(
                    text = "Jellyfin",
                    selected = backendType == ServerConfig.TYPE_JELLYFIN,
                    onClick = { backendType = ServerConfig.TYPE_JELLYFIN },
                    modifier = Modifier.weight(1f)
                )
                TypeCard(
                    text = "Navidrome",
                    selected = backendType == ServerConfig.TYPE_NAVIDROME,
                    onClick = { backendType = ServerConfig.TYPE_NAVIDROME },
                    modifier = Modifier.weight(1f)
                )
                TypeCard(
                    text = "Subsonic",
                    selected = backendType == ServerConfig.TYPE_SUBSONIC,
                    onClick = { backendType = ServerConfig.TYPE_SUBSONIC },
                    modifier = Modifier.weight(1f)
                )
                TypeCard(
                    text = stringResource(R.string.server_connect_type_daoliyu),
                    selected = backendType == ServerConfig.TYPE_DAOLIYU,
                    onClick = { backendType = ServerConfig.TYPE_DAOLIYU },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeCard(
                    text = stringResource(R.string.server_connect_type_feiniu),
                    selected = backendType == ServerConfig.TYPE_FEINIU,
                    onClick = { backendType = ServerConfig.TYPE_FEINIU },
                    modifier = Modifier.weight(1f)
                )
                TypeCard(
                    text = "WebDAV",
                    selected = backendType == ServerConfig.TYPE_WEBDAV,
                    onClick = { backendType = ServerConfig.TYPE_WEBDAV; testStatus = "" },
                    modifier = Modifier.weight(1f)
                )
                TypeCard(
                    text = "SMB",
                    selected = backendType == ServerConfig.TYPE_SMB,
                    onClick = { backendType = ServerConfig.TYPE_SMB; testStatus = "" },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 表单字段 - 服务器地址（带测试按钮）
            ServerAddressField(
                baseUrl = baseUrl,
                onBaseUrlChange = { baseUrl = it; testStatus = "" },
                onOpenKeyboard = { activeInputField = InputField.BASE_URL },
                isTesting = isTesting,
                testStatus = testStatus,
                backendType = backendType,
                onTestClick = {
                    if (baseUrl.text.isBlank()) {
                        testStatus = context.getString(R.string.server_connect_error_empty)
                    } else if (backendType == ServerConfig.TYPE_SMB && smbShare.text.isBlank()) {
                        testStatus = "请填写 SMB 共享名称"
                    } else {
                        isTesting = true
                        testStatus = ""
                        testScope.launch {
                            val config = ServerConfig(
                                backendType = backendType,
                                baseUrl = baseUrl.text.trim().removeSuffix("/"),
                                apiToken = apiToken.text.trim(),
                                username = username.text.trim(),
                                password = if (backendType == ServerConfig.TYPE_WEBDAV || backendType == ServerConfig.TYPE_SMB) password.text else password.text.trim(),
                                accessCode = accessCode.text.trim(),
                                rootPath = rootPath.text.trim().ifBlank { "/" },
                                smbShare = smbShare.text.trim(),
                                smbDomain = smbDomain.text.trim()
                            )
                            val (success, message) = backendRegistry.testConnection(config)
                            testStatus = if (success) "success:$message" else "error:$message"
                            isTesting = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (backendType == ServerConfig.TYPE_JELLYFIN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormField(
                        label = stringResource(R.string.server_username),
                        hint = stringResource(R.string.server_username_hint_generic),
                        value = username,
                        onValueChange = { username = it },
                        onOpen = { activeInputField = InputField.USERNAME },
                        modifier = Modifier.weight(1f)
                    )
                    FormField(
                        label = stringResource(R.string.server_password),
                        hint = stringResource(R.string.server_password_hint_generic),
                        value = password,
                        onValueChange = { password = it },
                        masked = true,
                        onOpen = { activeInputField = InputField.PASSWORD },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (backendType == ServerConfig.TYPE_DAOLIYU) {
                // 道理鱼：邮箱 + 密码
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormField(
                        label = stringResource(R.string.server_connect_email),
                        hint = "user@example.com",
                        value = username,
                        onValueChange = { username = it },
                        onOpen = { activeInputField = InputField.USERNAME },
                        modifier = Modifier.weight(1f)
                    )
                    FormField(
                        label = stringResource(R.string.server_password),
                        hint = stringResource(R.string.server_password_hint_generic),
                        value = password,
                        onValueChange = { password = it },
                        masked = true,
                        onOpen = { activeInputField = InputField.PASSWORD },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Navidrome / Subsonic / 飞牛 / WebDAV / SMB：用户名 + 密码
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormField(
                        label = stringResource(R.string.server_username),
                        hint = stringResource(R.string.server_username_hint_generic),
                        value = username,
                        onValueChange = { username = it },
                        onOpen = { activeInputField = InputField.USERNAME },
                        modifier = Modifier.weight(1f)
                    )
                    FormField(
                        label = stringResource(R.string.server_password),
                        hint = stringResource(R.string.server_password_hint_generic),
                        value = password,
                        onValueChange = { password = it },
                        masked = true,
                        onOpen = { activeInputField = InputField.PASSWORD },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 访问码仅飞牛音乐需要：其余后端没有这个概念，显示出来只会误导
                if (backendType == ServerConfig.TYPE_FEINIU) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FormField(
                        label = stringResource(R.string.server_access_code),
                        hint = stringResource(R.string.server_access_code_hint),
                        value = accessCode,
                        onValueChange = { accessCode = it },
                        masked = true,
                        onOpen = { activeInputField = InputField.ACCESS_CODE }
                    )
                }
            }

            if (backendType == ServerConfig.TYPE_SMB) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormField(
                        label = "SMB 共享名称",
                        hint = "例如：music、share",
                        value = smbShare,
                        onValueChange = { smbShare = it },
                        onOpen = { activeInputField = InputField.SMB_SHARE },
                        modifier = Modifier.weight(1f)
                    )
                    FormField(
                        label = "工作组 / 域（可选）",
                        hint = "例如：WORKGROUP",
                        value = smbDomain,
                        onValueChange = { smbDomain = it },
                        onOpen = { activeInputField = InputField.SMB_DOMAIN },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (backendType == ServerConfig.TYPE_WEBDAV || backendType == ServerConfig.TYPE_SMB) {
                Spacer(modifier = Modifier.height(16.dp))
                FormField(
                    label = "音乐根目录",
                    hint = "/",
                    value = rootPath,
                    onValueChange = { rootPath = it },
                    onOpen = { activeInputField = InputField.ROOT_PATH }
                )
                Spacer(modifier = Modifier.height(10.dp))
                FocusableSurface(
                    onClick = { loadNasDirectory("/") },
                    shape = RoundedCornerShape(10.dp),
                    focusedScale = 1.04f,
                    animationDurationMs = 150,
                    containerColor = NasMusicColors.SurfaceVariant,
                    contentColor = NasMusicColors.TextPrimary,
                    focusedContainerColor = NasMusicColors.FocusOverlay,
                    focusedContentColor = NasMusicColors.TextPrimary
                ) {
                    Text(
                        text = "浏览 NAS 目录并选择音乐文件夹",
                        color = LocalFocusableContentColor.current,
                        fontSize = FontSize.button(),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }

            // 表单统一操作提示（原每字段一行，排版冗余）
            Text(
                text = stringResource(R.string.server_press_ok_edit),
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.body(),
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = NasMusicColors.TextSecondary,
                    fontSize = FontSize.button(),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // 连接按钮
            FocusableSurface(
                onClick = {
                    if (baseUrl.text.isBlank()) {
                        statusMessage = context.getString(R.string.server_address_required)
                    } else if (backendType == ServerConfig.TYPE_SMB && smbShare.text.isBlank()) {
                        statusMessage = "请填写 SMB 共享名称"
                    } else {
                        statusMessage = ""
                        val config = ServerConfig(
                            backendType = backendType,
                            baseUrl = baseUrl.text.trim().removeSuffix("/"),
                            apiToken = apiToken.text.trim(),
                            username = username.text.trim(),
                            password = if (backendType == ServerConfig.TYPE_WEBDAV || backendType == ServerConfig.TYPE_SMB) password.text else password.text.trim(),
                            accessCode = accessCode.text.trim(),
                            rootPath = rootPath.text.trim().ifBlank { "/" },
                            smbShare = smbShare.text.trim(),
                            smbDomain = smbDomain.text.trim()
                        )
                        onConnect(config)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.08f,
                animationDurationMs = 150,
                containerColor = NasMusicColors.Primary,
                contentColor = NasMusicColors.TextPrimary,
                focusedContainerColor = NasMusicColors.PrimaryFill,
                focusedContentColor = NasMusicColors.TextPrimary,
                pressedScale = 0.95f
            ) {
                Text(
                    text = if (isConnecting) stringResource(R.string.server_connecting) else stringResource(R.string.server_connect_action),
                    color = LocalFocusableContentColor.current,
                    fontSize = FontSize.subtitle(),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.server_privacy_notice),
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.body()
            )
        }

        // 文本输入对话框
        if (activeInputField != null) {
            val dialogTitle: String
            val dialogHint: String
            val dialogValue: String
            val dialogMasked: Boolean
            when (activeInputField) {
                InputField.BASE_URL -> {
                    dialogTitle = stringResource(R.string.server_address_dialog_title)
                    dialogHint = when (backendType) {
                        ServerConfig.TYPE_SMB -> "smb://192.168.100.1"
                        else -> "http://192.168.100.1"
                    }
                    dialogValue = baseUrl.text
                    dialogMasked = false
                }
                InputField.USERNAME -> {
                    dialogTitle = stringResource(R.string.server_username_dialog_title)
                    dialogHint = stringResource(R.string.server_username_dialog_hint)
                    dialogValue = username.text
                    dialogMasked = false
                }
                InputField.PASSWORD -> {
                    dialogTitle = stringResource(R.string.server_password_dialog_title)
                    dialogHint = stringResource(R.string.server_password_dialog_hint)
                    dialogValue = password.text
                    dialogMasked = true
                }
                InputField.API_TOKEN -> {
                    dialogTitle = stringResource(R.string.server_token_dialog_title)
                    dialogHint = stringResource(R.string.server_token_dialog_hint)
                    dialogValue = apiToken.text
                    dialogMasked = true
                }
                InputField.ACCESS_CODE -> {
                    dialogTitle = stringResource(R.string.server_access_code_dialog_title)
                    dialogHint = stringResource(R.string.server_access_code_dialog_hint)
                    dialogValue = accessCode.text
                    dialogMasked = true
                }
                InputField.ROOT_PATH -> {
                    dialogTitle = "音乐根目录"
                    dialogHint = "例如：/Music；也可以使用上方目录浏览器选择"
                    dialogValue = rootPath.text
                    dialogMasked = false
                }
                InputField.SMB_SHARE -> {
                    dialogTitle = "SMB 共享名称"
                    dialogHint = "例如：music"
                    dialogValue = smbShare.text
                    dialogMasked = false
                }
                InputField.SMB_DOMAIN -> {
                    dialogTitle = "工作组 / 域名（可选）"
                    dialogHint = "例如：WORKGROUP"
                    dialogValue = smbDomain.text
                    dialogMasked = false
                }
                else -> {
                    dialogTitle = ""
                    dialogHint = ""
                    dialogValue = ""
                    dialogMasked = false
                }
            }
            TextInputDialog(
                title = dialogTitle,
                hint = dialogHint,
                initialValue = dialogValue,
                masked = dialogMasked,
                onConfirm = { newText ->
                    when (activeInputField) {
                        InputField.BASE_URL -> baseUrl = TextFieldValue(newText)
                        InputField.USERNAME -> username = TextFieldValue(newText)
                        InputField.PASSWORD -> password = TextFieldValue(newText)
                        InputField.API_TOKEN -> apiToken = TextFieldValue(newText)
                        InputField.ACCESS_CODE -> accessCode = TextFieldValue(newText)
                        InputField.ROOT_PATH -> rootPath = TextFieldValue(newText)
                        InputField.SMB_SHARE -> smbShare = TextFieldValue(newText)
                        InputField.SMB_DOMAIN -> smbDomain = TextFieldValue(newText)
                        else -> {}
                    }
                    activeInputField = null
                },
                onDismiss = { activeInputField = null }
            )
        }
        if (showDirectoryBrowser) {
            NasDirectoryBrowserDialog(
                path = browsingPath,
                directories = browsingDirectories,
                loading = browsingLoading,
                error = browsingError,
                onOpenDirectory = { loadNasDirectory(it) },
                onChooseCurrent = {
                    rootPath = TextFieldValue(browsingPath)
                    showDirectoryBrowser = false
                },
                onDismiss = { showDirectoryBrowser = false }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NasDirectoryBrowserDialog(
    path: String,
    directories: List<BackendDirectory>,
    loading: Boolean,
    error: String,
    onOpenDirectory: (String) -> Unit,
    onChooseCurrent: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .width(620.dp)
                .height(620.dp)
                .background(NasMusicColors.Surface, RoundedCornerShape(18.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("选择 NAS 音乐目录", color = NasMusicColors.TextPrimary, fontSize = FontSize.subtitle())
            Spacer(Modifier.height(8.dp))
            Text(path, color = NasMusicColors.TextSecondary, fontSize = FontSize.body())
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("正在读取目录…", color = NasMusicColors.TextSecondary, fontSize = FontSize.button())
                }
            } else if (error.isNotBlank()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(error, color = NasMusicColors.Warning, fontSize = FontSize.button())
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (path != "/") {
                        item {
                            FocusableSurface(
                                onClick = { onOpenDirectory(path.substringBeforeLast('/', "").ifBlank { "/" }) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                containerColor = NasMusicColors.SurfaceVariant,
                                contentColor = NasMusicColors.TextPrimary,
                                focusedContainerColor = NasMusicColors.FocusOverlay,
                                focusedContentColor = NasMusicColors.TextPrimary
                            ) {
                                Text("↑ 返回上一级", color = LocalFocusableContentColor.current, fontSize = FontSize.button(), modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                    if (directories.isEmpty()) {
                        item {
                            Text(
                                "此目录下没有子文件夹，可以直接选择当前目录",
                                color = NasMusicColors.TextSecondary,
                                fontSize = FontSize.button(),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    items(directories, key = { it.path }) { directory ->
                        FocusableSurface(
                            onClick = { onOpenDirectory(directory.path) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            containerColor = NasMusicColors.SurfaceVariant.copy(alpha = 0.65f),
                            contentColor = NasMusicColors.TextPrimary,
                            focusedContainerColor = NasMusicColors.FocusOverlay,
                            focusedContentColor = NasMusicColors.TextPrimary
                        ) {
                            Text("📁  ${directory.name}", color = LocalFocusableContentColor.current, fontSize = FontSize.button(), modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                        }
                    }
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                FocusableSurface(
                    onClick = { onOpenDirectory(path) }, shape = RoundedCornerShape(8.dp),
                    containerColor = NasMusicColors.SurfaceVariant, contentColor = NasMusicColors.TextPrimary,
                    focusedContainerColor = NasMusicColors.FocusOverlay, focusedContentColor = NasMusicColors.TextPrimary
                ) { Text("重试", color = LocalFocusableContentColor.current, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FocusableSurface(
                    onClick = onDismiss, shape = RoundedCornerShape(8.dp),
                    containerColor = NasMusicColors.SurfaceVariant, contentColor = NasMusicColors.TextPrimary,
                    focusedContainerColor = NasMusicColors.FocusOverlay, focusedContentColor = NasMusicColors.TextPrimary
                ) { Text("取消", color = LocalFocusableContentColor.current, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) }
                FocusableSurface(
                    onClick = onChooseCurrent, shape = RoundedCornerShape(8.dp),
                    containerColor = NasMusicColors.Primary, contentColor = NasMusicColors.TextPrimary,
                    focusedContainerColor = NasMusicColors.PrimaryFill, focusedContentColor = NasMusicColors.TextPrimary,
                    requestFocusOnLaunch = true
                ) { Text("选择当前目录", color = LocalFocusableContentColor.current, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TypeCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val animScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else if (selected) 2.dp else 0.dp,
                color = if (isFocused) NasMusicColors.FocusRing
                        else if (selected) NasMusicColors.Primary
                        else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.08f,
        animationDurationMs = 150,
        containerColor = if (selected) NasMusicColors.Primary else NasMusicColors.Surface,
                    contentColor = if (selected) NasMusicColors.TextPrimary else NasMusicColors.TextPrimary,
                    focusedContainerColor = if (selected) NasMusicColors.Primary else NasMusicColors.Primary.copy(alpha = 0.3f),
                    focusedContentColor = if (selected) NasMusicColors.Primary else NasMusicColors.TextPrimary,
                    pressedContainerColor = NasMusicColors.SurfaceVariant,
                    pressedContentColor = NasMusicColors.TextPrimary,
                    pressedScale = 0.96f,
                    focusBorderColor = NasMusicColors.FocusRing
                ) {
        Text(
            text = text,
            color = LocalFocusableContentColor.current,
            fontSize = FontSize.subtitle(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 16.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FormField(
    label: String,
    hint: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOpen: () -> Unit = {},
    masked: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = NasMusicColors.TextPrimary,
            fontSize = FontSize.button(),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FocusableSurface(
            onClick = { onOpen() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.05f,
            animationDurationMs = 150,
            containerColor = NasMusicColors.SurfaceVariant,
            contentColor = NasMusicColors.TextPrimary,
            focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.2f),
            focusedContentColor = NasMusicColors.TextPrimary,
            pressedScale = 0.96f
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (value.text.isEmpty()) hint
                           else if (masked) "*".repeat(value.text.length)
                           else value.text,
                    color = LocalFocusableContentColor.current,
                    fontSize = FontSize.button(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TestConnectionButton(
    isTesting: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val animScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

FocusableSurface(
        onClick = {
            if (!isTesting) onClick()
        },
        modifier = Modifier
            .width(100.dp)
            .height(56.dp)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) NasMusicColors.FocusRing else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.05f,
        animationDurationMs = 150,
        containerColor = NasMusicColors.Primary,
        focusedContainerColor = NasMusicColors.Primary,
        contentColor = if (isTesting) NasMusicColors.TextSecondary else NasMusicColors.TextPrimary,
        focusedContentColor = NasMusicColors.TextPrimary,
        pressedContainerColor = NasMusicColors.Primary.copy(alpha = 0.8f),
        pressedContentColor = NasMusicColors.TextPrimary,
        pressedScale = 0.96f,
        focusBorderColor = NasMusicColors.FocusRing
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isTesting) stringResource(R.string.server_testing) else stringResource(R.string.server_connection_test),
                color = LocalFocusableContentColor.current,
                fontSize = FontSize.button()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerAddressField(
    baseUrl: TextFieldValue,
    onBaseUrlChange: (TextFieldValue) -> Unit,
    onOpenKeyboard: () -> Unit,
    isTesting: Boolean,
    testStatus: String,
    onTestClick: () -> Unit,
    backendType: String = ServerConfig.TYPE_JELLYFIN
) {
    Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (backendType == ServerConfig.TYPE_SMB) "SMB 主机地址" else if (backendType == ServerConfig.TYPE_WEBDAV) "WebDAV 地址" else stringResource(R.string.server_address),
                color = NasMusicColors.TextPrimary,
            fontSize = FontSize.button(),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 输入框
            FocusableSurface(
                onClick = onOpenKeyboard,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.02f,
                animationDurationMs = 150,
                containerColor = NasMusicColors.SurfaceVariant,
                contentColor = NasMusicColors.TextPrimary,
                focusedContainerColor = NasMusicColors.Primary.copy(alpha = 0.2f),
                focusedContentColor = NasMusicColors.TextPrimary,
                pressedScale = 0.96f
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (baseUrl.text.isEmpty()) {
                            if (backendType == ServerConfig.TYPE_SMB) "smb://192.168.100.1" else "http://192.168.100.1"
                        } else baseUrl.text,
                        color = LocalFocusableContentColor.current,
                        fontSize = FontSize.button(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 测试按钮
            TestConnectionButton(
                isTesting = isTesting,
                onClick = onTestClick
            )
        }

        // 提示文字
        Text(
            text = stringResource(R.string.server_press_ok_edit),
            color = NasMusicColors.TextSecondary,
            fontSize = FontSize.body(),
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )

        // 测试状态显示
        if (testStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            val isSuccess = testStatus.startsWith("success:")
            val message = if (isSuccess) testStatus.removePrefix("success:") else testStatus.removePrefix("error:")
            Text(
                text = if (isSuccess) stringResource(R.string.server_connect_success, message) else stringResource(R.string.server_connect_failed, message),
                color = if (isSuccess) NasMusicColors.Primary else NasMusicColors.Warning,
                fontSize = FontSize.body()
            )
        }
    }
}
