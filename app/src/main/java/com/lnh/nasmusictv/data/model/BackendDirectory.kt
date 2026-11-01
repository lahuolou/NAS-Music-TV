package com.lnh.nasmusictv.data.model

/** TV 端 NAS 目录浏览项（路径相对于 WebDAV endpoint 或 SMB share）。 */
data class BackendDirectory(
    val name: String,
    val path: String
)
