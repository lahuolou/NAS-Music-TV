package com.lnh.nasmusictv.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.lnh.nasmusictv.NasMusicVersion
import com.lnh.nasmusictv.data.prefs.AppPreferences
import com.lnh.nasmusictv.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 应用内自动更新管理器（手动 DI，挂在 [com.lnh.nasmusictv.NasMusicApp]）。
 *
 * 行为（与用户确认的形态一致）：
 * - **更新来源**：GitHub Releases。本应用由 `lahuolou/NAS-Music-TV` 仓库发版（CI 自动建
 *   Release 并签名 APK）。注意这**不是**关于页链接的上游 `hxzhang2000/NasMusicTV`。
 * - **自动检查**：开机（节流）或后台触发 `checkForUpdates(manual=false)`，仅更新内部状态，
 *   不在前台弹窗；用户在「设置 → 关于 → 应用更新」里能看到是否有新版本。
 * - **手动安装**：用户在关于页点「下载并安装」→ 下载 APK 到应用私有缓存 → 调起系统安装器
 *   （`ACTION_INSTALL_PACKAGE` + FileProvider content URI）。**非 root 的 Android TV 无法
 *   完全静默安装**，必须由用户在系统安装器里点确认——这是平台限制，不是缺陷。
 *
 * 状态机见 [UpdateUiState]。UI 在关于页订阅 [uiState]。
 */
class UpdateManager(
    private val app: android.app.Application,
    private val prefs: AppPreferences,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Android 5.1 老电视不信任 GitHub 新 SSL 根证书，配置宽松 SSL 验证
        .sslSocketFactory(createTrustAllSslContext().socketFactory, TRUST_ALL_MANAGER)
        .hostnameVerifier { _, _ -> true }
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 自动检查开关（来自偏好，响应式）。 */
    val autoCheckUpdates: StateFlow<Boolean> =
        prefs.update.autoCheckUpdatesFlow.stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    /** 当前安装版本名（来自 BuildConfig，经 [NasMusicVersion]）。 */
    val currentVersion: String get() = NasMusicVersion.VERSION_NAME

    /**
     * 开机自动检查入口（[android.app.Application] / [MainActivity] 调用）。
     * 仅在开启自动检查、且距上次检查超过 24h 时触发一次静默检查。
     */
    fun maybeAutoCheck() {
        scope.launch(Dispatchers.IO) {
            val enabled = runCatching { prefs.update.autoCheckUpdatesFlow.first() }.getOrDefault(true)
            if (!enabled) return@launch
            val last = runCatching { prefs.update.lastCheckFlow.first() }.getOrDefault(0L)
            val now = System.currentTimeMillis()
            if (now - last < AUTO_CHECK_INTERVAL_MS) return@launch
            prefs.update.setLastCheck(now)
            checkForUpdates(manual = false)
        }
    }

    /** 检查最新版本（manual=true 时由用户手动点按触发）。 */
    fun checkForUpdates(manual: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (_uiState.value is UpdateUiState.Checking) return@launch
            _uiState.value = UpdateUiState.Checking
            runCatching { fetchLatest() }
                .onSuccess { release ->
                    if (release == null) {
                        _uiState.value = UpdateUiState.UpToDate
                        return@launch
                    }
                    val skipped = runCatching { prefs.update.skippedVersionFlow.first() }.getOrDefault("")
                    val newer = isNewer(currentVersion, release.version)
                    _uiState.value = if (newer && release.version != skipped) {
                        UpdateUiState.Available(release)
                    } else {
                        UpdateUiState.UpToDate
                    }
                }
                .onFailure { e ->
                    AppLog.w(TAG, "checkForUpdates failed", e)
                    _uiState.value = UpdateUiState.Error(e.message ?: "unknown error")
                }
        }
    }

    /** 下载当前可用版本并拉起系统安装器。需在 [UpdateUiState.Available] 状态下调用。 */
    fun downloadAndInstall(context: Context) {
        val release = (_uiState.value as? UpdateUiState.Available)?.release ?: run {
            AppLog.w(TAG, "downloadAndInstall called without Available state")
            return
        }
        val apkUrl = release.apkUrl
        if (apkUrl.isNullOrBlank()) {
            _uiState.value = UpdateUiState.Error("release has no apk asset")
            return
        }
        scope.launch(Dispatchers.IO) {
            val dest = File(app.cacheDir, "updates/${UPDATE_APK_NAME}").also { it.parentFile?.mkdirs() }
            try {
                downloadFile(apkUrl, dest) { downloaded, total ->
                    val prog = if (total > 0) downloaded.toFloat() / total else 0f
                    _uiState.value = UpdateUiState.Downloading(
                        progress = prog,
                        downloadedMb = downloaded / MB,
                        totalMb = if (total > 0) total / MB else 0,
                    )
                }
                installApk(context.applicationContext, dest)
                // 安装器已拉起（独立 activity）；本应用可能被更新/重启，状态归位即可。
                _uiState.value = UpdateUiState.Idle
            } catch (e: Exception) {
                AppLog.w(TAG, "downloadAndInstall failed", e)
                _uiState.value = UpdateUiState.Error(e.message ?: "download failed")
            }
        }
    }

    /** 跳过某个版本（写入偏好，下次/自动检查不再提示该版本）。 */
    fun skipVersion(version: String) {
        scope.launch(Dispatchers.IO) { prefs.update.setSkippedVersion(version) }
        if (_uiState.value is UpdateUiState.Available) _uiState.value = UpdateUiState.UpToDate
    }

    /** 切换自动检查开关。 */
    fun setAutoCheck(enabled: Boolean) {
        scope.launch(Dispatchers.IO) { prefs.update.setAutoCheck(enabled) }
    }

    /** 打开发布页（查看发布说明）。无可用版本时回退到仓库 Releases 列表页。 */
    fun openReleasePage(context: Context) {
        val url = (_uiState.value as? UpdateUiState.Available)?.release?.htmlUrl ?: RELEASE_PAGE
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure { AppLog.w(TAG, "openReleasePage failed", it) }
    }

    // ───────────────────────── 内部实现 ─────────────────────────

    private fun fetchLatest(): ReleaseInfo? {
        val req = Request.Builder().url(GITHUB_API_LATEST)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                // 404 = 仓库暂无 Release；其它错误上抛由调用方转 Error 状态。
                if (resp.code == 404) return null
                throw RuntimeException("HTTP ${resp.code}")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            val tag = json.optString("tag_name", "")
            val version = tag.removePrefix("v").trim()
            val name = json.optString("name", tag)
            val htmlUrl = json.optString("html_url", RELEASE_PAGE)
            val notes = json.optString("body", "")
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val an = a.optString("name", "")
                if (an.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "").ifBlank { null }
                    size = a.optLong("size", 0L)
                    break
                }
            }
            return ReleaseInfo(
                tag = tag,
                version = version,
                name = name,
                htmlUrl = htmlUrl,
                apkUrl = apkUrl,
                sizeBytes = size,
                notes = notes,
            )
        }
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val total = resp.body?.contentLength() ?: -1L
            resp.body?.byteStream()?.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        onProgress(done, if (total > 0) total else done)
                    }
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTH, file)
        } else {
            // API 21–23：系统安装器仍接受 file:// URI（StrictMode 的 file:// 限制从 N 起才生效）
            Uri.fromFile(file)
        }
        // 全程使用 ACTION_INSTALL_PACKAGE：API 21（Android 5.0）起即可用，且行为一致
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "UpdateManager"

        /** 信任所有证书的 TrustManager（Android 5.1 老电视兼容 GitHub 新证书） */
        private val TRUST_ALL_MANAGER = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }

        /** 创建信任所有证书的 SSLContext */
        private fun createTrustAllSslContext(): SSLContext {
            return SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(TRUST_ALL_MANAGER), SecureRandom())
            }
        }

        /** 发布仓库（自动更新来源；CI 在此自动建 Release 并签名 APK） */
        private const val GITHUB_REPO = "lahuolou/NAS-Music-TV"
        private const val GITHUB_API_LATEST = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val RELEASE_PAGE = "https://github.com/$GITHUB_REPO/releases/latest"
        private const val FILE_PROVIDER_AUTH = "com.lnh.nasmusictv.fileprovider"
        private const val UPDATE_APK_NAME = "nasmusic-update.apk"
        private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val MB = 1_048_576L

        /**
         * 语义化版本比较：latest 是否比 current 新。
         * 逐段比较整数（"1.0.0.3" vs "1.0.0.4"），缺段补 0。非数字段忽略。
         */
        internal fun isNewer(current: String, latest: String): Boolean {
            val c = current.split('.').map { it.toIntOrNull() ?: 0 }
            val l = latest.split('.').map { it.toIntOrNull() ?: 0 }
            val n = maxOf(c.size, l.size)
            for (i in 0 until n) {
                val a = c.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (b > a) return true
                if (b < a) return false
            }
            return false
        }
    }
}

/** GitHub Release 解析结果（仅保留更新所需字段）。 */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val name: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val sizeBytes: Long,
    val notes: String,
)

/** 更新 UI 状态机。 */
sealed interface UpdateUiState {
    /** 空闲（未进行任何检查） */
    data object Idle : UpdateUiState

    /** 正在检查最新版本 */
    data object Checking : UpdateUiState

    /** 已是最新（或无可用更新） */
    data object UpToDate : UpdateUiState

    /** 发现新版本 */
    data class Available(val release: ReleaseInfo) : UpdateUiState

    /** 正在下载（progress 0..1） */
    data class Downloading(val progress: Float, val downloadedMb: Long, val totalMb: Long) : UpdateUiState

    /** 出错（message 为可读原因） */
    data class Error(val message: String) : UpdateUiState
}
