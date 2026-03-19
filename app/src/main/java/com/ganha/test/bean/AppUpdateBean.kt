package com.ganha.test.bean

data class AppUpdateBean(
    val needUpdate: Boolean = false,
    val updateUrl: String = "",
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkName:String = "",
    val isBackGround: Boolean = false,
    val isForceUpdate: Boolean = false
)
