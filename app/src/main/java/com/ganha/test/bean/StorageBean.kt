package com.ganha.test.bean

data class StorageBean(
    val type: Int = 0, // 0 存, 1 取
    val key: String? = null,
    val value: String? = null
)
