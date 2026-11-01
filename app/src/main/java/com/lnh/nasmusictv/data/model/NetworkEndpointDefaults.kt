package com.lnh.nasmusictv.data.model

/**
 * 网络端点默认值（data 层单一真相源）。
 *
 * 服务类与持久化层共用，避免 data 层反向 import backend 服务类。
 */
object NetworkEndpointDefaults {

    /** Meting-API 搜索服务默认端点 */
    const val DEFAULT_METING_BASE_URL = "https://meting.api.redcha.cn/api"

    /** MV 搜索服务（Bilibili）默认端点 */
    const val DEFAULT_MV_API_BASE_URL = "https://api.bilibili.com"
}
