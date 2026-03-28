package com.ganha.test.bean

import com.google.gson.annotations.SerializedName

data class VibrateBean(
    @SerializedName("duration")
    val duration: Long? = 50L,
    @SerializedName("amplitude")
    val amplitude: Int? = -1
)
