package com.ganha.test.bean

data class AppinfoBean(
    val isEmulator: Boolean = false,
    val isVpnOrProxy: Boolean = false,
    val isRooted: Boolean = false,
    val isUsbDebuggingOrDevMode: Boolean = false,
    val hasSimCard: Boolean = false,
    val sim_country: String = "",
    val simOperator: String = "",
    val deviceId: String = "",
    val versionCode :Int = 0,
    val versionName :String = "",
    val packageName :String = "",
    val statusBarHeight : String = "",
)
