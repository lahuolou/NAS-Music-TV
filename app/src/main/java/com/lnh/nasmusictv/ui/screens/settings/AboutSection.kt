package com.lnh.nasmusictv.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.core.net.toUri
import com.lnh.nasmusictv.NasMusicVersion
import com.lnh.nasmusictv.R
import com.lnh.nasmusictv.data.model.VersionInfo
import com.lnh.nasmusictv.data.update.UpdateManager
import com.lnh.nasmusictv.data.update.UpdateUiState
import com.lnh.nasmusictv.ui.components.LocalFocusableContentColor
import com.lnh.nasmusictv.ui.theme.FontSize
import com.lnh.nasmusictv.ui.theme.NasMusicColors

/** 关于分区状态 */
data class AboutSettingsState(
    val isConnected: Boolean,
    val serverDisplayName: String,
    val backendApiVersion: String,
    val apiVersions: List<VersionInfo>,
)

/**
 * 关于分区（原 SettingsScreen ABOUT 分支，逻辑逐行搬迁；四卡构成见方案 §3.2 / Task 8）。
 *
 * ⚠️ Task 8 只做「裸 `Text` / `SubSectionTitle` 标题 → `SettingsGroupCard` 卡片头」，
 * 并把 GitHub 仓库链接从 `Text.clickable` 换成可聚焦瓦片
 * （方案 §2-7 缺陷本体：`Modifier.clickable` 的文本在 TV 上聚焦不到，遥控器没路径）：
 * 容器 / 焦点色与 [SettingActionButton] 同一套口径（T11 起两者都走 `SettingsTile`），
 * 跳转仍走 `Intent.ACTION_VIEW`。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AboutTitle() {
    SectionTitle(stringResource(R.string.settings_about))
}

// ⚠️ 本页五张卡必须各自独立成 LazyColumn item（见 SettingsScreen ABOUT 分支）：
// 整页塞在单个 item 里时，TV 遥控器上下键只在卡内跳焦点、LazyColumn 不滚动 ——
// 关于页超长（版本/连接/能力/开源/更新五卡）下半屏就永远按不到。

/** 卡① 版本信息（§4.5：本页全是只读信息行，说明由卡片 description 承担） */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AboutVersionCard() {
    SettingsGroupCard(
        title = stringResource(R.string.settings_group_version),
        description = stringResource(R.string.settings_group_about_desc),
    ) {
        AboutRow(label = stringResource(R.string.settings_app_name), value = stringResource(R.string.app_name))
        AboutRow(label = stringResource(R.string.about_version), value = NasMusicVersion.DISPLAY)
        AboutRow(label = stringResource(R.string.settings_build_type), value = NasMusicVersion.BUILD_TYPE)
        AboutRow(label = stringResource(R.string.about_license), value = stringResource(R.string.about_license_value))
        AboutRow(label = stringResource(R.string.settings_supported_backends), value = stringResource(R.string.settings_supported_backends_value))
    }
}

/** 卡② 连接信息（当前连接的后端 + 全量 API 版本号） */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AboutConnectionCard(state: AboutSettingsState) {
    SettingsGroupCard(title = stringResource(R.string.settings_group_connection_info)) {
        // 当前连接的后端信息
        if (state.isConnected) {
            AboutRow(label = stringResource(R.string.settings_backend_type), value = state.serverDisplayName)
            AboutRow(label = stringResource(R.string.settings_api_version), value = state.backendApiVersion)
        } else {
            AboutRow(label = stringResource(R.string.settings_backend_type), value = stringResource(R.string.settings_not_connected))
        }
        // 全量 API 版本号（后端 + 外部服务）
        Text(
            text = stringResource(R.string.settings_api_versions),
            color = NasMusicColors.Primary,
            fontSize = FontSize.button(),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        if (state.apiVersions.isEmpty()) {
            AboutRow(label = stringResource(R.string.settings_api_versions_empty), value = "")
        } else {
            state.apiVersions.forEach { v ->
                val (labelText, valueText) = formatVersionInfo(v)
                AboutRow(label = labelText, value = valueText)
            }
        }
    }
}

/** 卡③ 能力说明 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AboutCapabilitiesCard() {
    SettingsGroupCard(title = stringResource(R.string.settings_group_capabilities)) {
        AboutRow(label = stringResource(R.string.settings_network_music_info), value = stringResource(R.string.settings_network_music_value))
        AboutRow(label = stringResource(R.string.settings_independent_music), value = stringResource(R.string.settings_independent_music_value))
        AboutRow(label = stringResource(R.string.settings_radio_info), value = stringResource(R.string.settings_radio_value))
        AboutRow(label = stringResource(R.string.settings_baidu_netdisk_info), value = stringResource(R.string.settings_baidu_netdisk_value))
        AboutRow(label = stringResource(R.string.settings_lyrics_info), value = stringResource(R.string.settings_lyrics_value))
    }
}

/** 卡④ 开源与许可（版权说明 + GitHub 项目链接） */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AboutOpenSourceCard() {
    SettingsGroupCard(title = stringResource(R.string.settings_group_opensource)) {
            // 版权说明
            Text(
                text = stringResource(R.string.settings_copyright),
                color = NasMusicColors.TextPrimary,
                fontSize = FontSize.button(),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.settings_copyright_text),
                color = NasMusicColors.TextSecondary,
                fontSize = FontSize.small(),
                lineHeight = FontSize.small() * 1.5
            )
        }
}

/**
 * 应用更新卡（自动更新入口）。
 *
 * 订阅 [UpdateManager.uiState] / [UpdateManager.autoCheckUpdates] 响应式渲染：
 * - 始终展示「当前版本」+「自动检查更新」开关 +「检查更新」按钮；
 * - 根据 [UpdateUiState] 展示检查中 / 已是最新 / 发现新版本（下载安装 / 跳过 / 查看说明）/
 *   下载进度 / 错误重试。
 * 非 root 的 Android TV 无法静默安装，下载后由系统在安装器里弹确认（平台限制）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AppUpdateCard(updateManager: UpdateManager) {
    val context = LocalContext.current
    val uiState by updateManager.uiState.collectAsState()
    val autoCheck by updateManager.autoCheckUpdates.collectAsState()
    val currentVersion = updateManager.currentVersion
    val busy = uiState is UpdateUiState.Checking || uiState is UpdateUiState.Downloading

    SettingsGroupCard(
        title = stringResource(R.string.settings_group_update),
        description = stringResource(R.string.settings_update_desc),
    ) {
        AboutRow(label = stringResource(R.string.about_update_current), value = currentVersion)

        SettingSwitch(
            label = stringResource(R.string.about_update_auto_check),
            description = stringResource(R.string.about_update_auto_check_desc),
            checked = autoCheck,
            onClick = { updateManager.setAutoCheck(!autoCheck) },
        )

        SettingActionButton(
            label = stringResource(R.string.about_update_check_now),
            description = stringResource(R.string.about_update_check_now_desc),
            onClick = { updateManager.checkForUpdates(manual = true) },
            enabled = !busy,
        )

        when (val s = uiState) {
            UpdateUiState.Idle -> { /* 静默，不展示额外状态 */ }

            UpdateUiState.Checking -> {
                SettingsHint(stringResource(R.string.about_update_checking))
            }

            UpdateUiState.UpToDate -> {
                AboutRow(
                    label = stringResource(R.string.about_update_status),
                    value = stringResource(R.string.about_update_up_to_date),
                )
            }

            is UpdateUiState.Available -> {
                val r = s.release
                AboutRow(label = stringResource(R.string.about_update_latest), value = r.version)
                if (r.sizeBytes > 0) {
                    AboutRow(label = stringResource(R.string.about_update_size), value = formatBytes(r.sizeBytes))
                }
                SettingsHint(stringResource(R.string.about_update_available_desc, r.version))

                Row(horizontalArrangement = Arrangement.spacedBy(SettingsTokens.chipGap)) {
                    SettingActionButton(
                        label = stringResource(R.string.about_update_download_install),
                        description = stringResource(R.string.about_update_download_install_desc),
                        onClick = { updateManager.downloadAndInstall(context) },
                        modifier = Modifier.weight(1f),
                        enabled = !r.apkUrl.isNullOrBlank(),
                    )
                    SettingActionButton(
                        label = stringResource(R.string.about_update_skip),
                        description = stringResource(R.string.about_update_skip_desc),
                        onClick = { updateManager.skipVersion(r.version) },
                        modifier = Modifier.weight(1f),
                    )
                }

                SettingActionButton(
                    label = stringResource(R.string.about_update_view_notes),
                    description = stringResource(R.string.about_update_view_notes_desc),
                    onClick = { updateManager.openReleasePage(context) },
                    trailing = Trail.Arrow,
                )
            }

            is UpdateUiState.Downloading -> {
                SettingsHint(
                    stringResource(R.string.about_update_downloading, s.downloadedMb, s.totalMb),
                )
                LinearProgress(progress = s.progress)
            }

            is UpdateUiState.Error -> {
                SettingsHint(stringResource(R.string.about_update_error, s.message))
                SettingActionButton(
                    label = stringResource(R.string.about_update_retry),
                    description = stringResource(R.string.about_update_retry_desc),
                    onClick = { updateManager.checkForUpdates(manual = true) },
                )
            }
        }
    }
}

/**
 * 自绘线性进度条（TV Material3 无 LinearProgressIndicator，沿用项目惯例）。
 * [progress] ∈ [0, 1]。
 */
@Composable
private fun LinearProgress(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(horizontal = SettingsTokens.rowHPadding, vertical = 4.dp)
            .background(NasMusicColors.Border, RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(NasMusicColors.Primary, RoundedCornerShape(3.dp)),
        )
    }
}
