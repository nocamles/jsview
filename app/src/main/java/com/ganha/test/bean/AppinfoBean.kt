package com.ganha.test.bean

data class AppinfoBean(
    val isEmulator: Boolean = false,
    val isVpnOrProxy: Boolean = false,
    val isRooted: Boolean = false,
    val isUsbDebuggingOrDevMode: Boolean = false,
    val hasSimCard: Boolean = false,
    val simOperator: String = "",
    val deviceId: String = ""
)
