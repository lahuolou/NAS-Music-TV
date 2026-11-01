package com.lnh.nasmusictv.data.model

import com.lnh.nasmusictv.backend.network.baidu.BaiduNetdiskConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 门禁：`CloudDriveConfig.DEFAULT_MUSIC_ROOT` 与 `BaiduNetdiskConfig.APP_DIR` 保持一致。
 *
 * ⚠️ 两侧是**独立常量**（data 层不反向依赖 backend 配置表），
 * 改任一侧必须同步另一侧，否则此测试红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudDriveDefaultsTest {

    @Test
    fun `默认音乐根目录与百度 APP_DIR 保持一致`() {
        assertEquals(BaiduNetdiskConfig.APP_DIR, CloudDriveConfig.DEFAULT_MUSIC_ROOT)
    }
}
