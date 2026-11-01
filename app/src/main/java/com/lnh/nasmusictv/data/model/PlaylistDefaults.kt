package com.lnh.nasmusictv.data.model

/** 歌单领域默认值（data 层单一真相源） */
object PlaylistDefaults {

    /**
     * 导入裸条目 stub 的 Song.id 统一前缀（§4.1.8 (2) / §4.3）。
     * PlaylistImporter 生成、PlaylistEnricher 用该前缀识别「待补全」条目。
     */
    const val IMPORTED_ID_PREFIX = "imported_"
}
