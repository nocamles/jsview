package com.ganha.test.bean

import com.google.gson.JsonElement

data class JsBeanRequest(
    var methods: String?,
    var callback: String?,
    var paramObj: JsonElement?
)
