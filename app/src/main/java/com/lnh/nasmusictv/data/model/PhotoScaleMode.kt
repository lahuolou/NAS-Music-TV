package com.lnh.nasmusictv.data.model

/**
 * 照片「画面适配」方式 —— 照片宽高比与屏幕不一致时怎么处理
 *
 * ⛔ **与横竖屏无关**：这是「图片比例 vs 屏幕比例」的取舍，
 * 电视横屏放竖幅照片、手机横屏放横幅照片，都是同一个问题
 * ⇒ **两端（电视 / 手机横）都必须暴露该设置**。
 */
enum class PhotoScaleMode {
    /** 满屏：按短边铺满，超出部分裁掉（默认，无黑边） */
    CROP,

    /** 完整：整图缩放到能放下，不足处留黑边（不裁切） */
    FIT,
    ;

    companion object {
        val Default: PhotoScaleMode = CROP

        fun fromKey(key: String?): PhotoScaleMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Default
    }
}
