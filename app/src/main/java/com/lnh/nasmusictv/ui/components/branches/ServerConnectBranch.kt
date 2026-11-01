package com.lnh.nasmusictv.ui.components.branches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.lnh.nasmusictv.data.model.ServerConfig
import com.lnh.nasmusictv.ui.viewmodel.MainViewModel
import com.lnh.nasmusictv.data.model.*
import com.lnh.nasmusictv.ui.screens.*
import com.lnh.nasmusictv.ui.screens.library.*
import com.lnh.nasmusictv.ui.screens.settings.*
import com.lnh.nasmusictv.ui.screens.netdisk.*
import com.lnh.nasmusictv.ui.screens.stats.*
import com.lnh.nasmusictv.ui.viewmodel.*
/**
 * ServerConnect 分支提取自 AppRoot（Method too large 根治：分支下沉 branches/）。
 * 分支体逐行搬迁，外层共享状态经参数注入。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ServerConnectBranch(
    viewModel: MainViewModel,
    serverConfig: ServerConfig,
    isConnected: Boolean,
    serverDisplayName: String,
    isLoading: Boolean,
    onConnect: (ServerConfig) -> Unit
) {
                    ServerConnectScreen(
                        initialConfig = serverConfig,
                        isConnected = isConnected,
                        serverDisplayName = serverDisplayName,
                        isConnecting = isLoading,
                        onConnect = onConnect,
                        onDisconnect = { viewModel.serverVM.disconnect() }
                    )
}
