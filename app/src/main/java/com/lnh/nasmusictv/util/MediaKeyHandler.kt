package com.lnh.nasmusictv.util

import android.view.KeyEvent
import com.lnh.nasmusictv.ui.viewmodel.MainViewModel
import com.lnh.nasmusictv.data.model.Screen

/**
 * 媒体键处理器
 * 处理 HDMI-CEC / 蓝牙遥控器媒体键事件
 */
object MediaKeyHandler {

    /**
     * 处理按键事件
     * @return true 表示事件已消费，false 表示需要交给默认处理
     */
    fun handleKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        viewModel: MainViewModel,
        isImmersiveMode: Boolean,
        currentScreen: Screen
    ): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                viewModel.playerVM.playPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                viewModel.playerVM.next()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                viewModel.playerVM.previous()
                true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                viewModel.playerVM.playPause()
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                // 极简遥控器适配（只有菜单/方向/确认/返回）：
                // 菜单键播放页=切换播放模式（循环↔单曲↔随机），其他页=进入设置
                if (currentScreen == Screen.NowPlaying) {
                    viewModel.playerVM.togglePlayMode()
                } else {
                    viewModel.navVM.navigateTo(Screen.Settings)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (isImmersiveMode) {
                    // 沉浸模式下 OK → 退出全屏，不触发播放暂停
                    false // Let the caller handle immersive mode exit
                } else if (currentScreen == Screen.NowPlaying) {
                    // 仅在播放页面时处理
                    viewModel.playerVM.playPause()
                    true
                } else {
                    false // Let default handling proceed
                }
            }
            else -> false
        }
    }
}
