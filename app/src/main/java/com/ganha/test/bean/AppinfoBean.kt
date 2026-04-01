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
    val versionCode: Int = 0,
    val versionName: String = "",
    val packageName: String = "",
    val androidId: String = "",
    val gaid: String = "", //google广告ID
    val device_brand: String = "", //手机品牌
    val device_model: String = "", //手机型号
    val os_version: String = "", //系统版本
    val network_type: String = "" //网络类型
)
