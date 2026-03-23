package com.ganha.test.bean

data class PermissionBean(
    var permissions: List<String>? = null,
    var permission: String? = null,
    var explainReason: String? = null,
    var forwardtoSettingReason: String? = null
)
