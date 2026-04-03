package com.ganha.test.bean

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.ganha.test.viewmodel.MainViewModel
import java.util.logging.Logger

class JsBean(val viewModel: MainViewModel) {
    companion object {
        const val js_deviceInfo = "getDeviceRiskInfo"
        const val js_channelInfo = "channelInfo"
        const val js_installedSocialApps = "installedSocialApps"
        const val js_requestPermission = "requestPermission"
        const val js_getPermissionStatus = "getPermissionStatus"
        const val js_shareTo = "shareTo"
        const val js_copyToClipboard = "copyToClipboard"
        const val js_vibrate = "vibrate"
        const val js_saveImageToGallery = "saveImageToGallery"
        const val js_removeSplashScreen = "removeSplashScreen"
        const val js_onAppLifecycle = "onAppLifecycle"
        const val js_goBack = "goBack"
        const val js_refresh = "refreshPage"
        const val js_statusBarLight = "setStatusBarColor"
        const val js_openUrlExternally = "openUrlExternally"
        const val js_appUpdate = "appUpdate"
        const val js_getBaseUrlInfo = "getBaseUrlInfo"
        const val js_payload = "payload"
        const val js_gotoH5BaseUrl = "h5Baseurl"
        const val js_storage = "storage"

        const val js_getPushToken = "getPushToken"

        const val js_clickNotificationBar = "clickNotificationBar"
        const val js_loadErrorUrl = "loadErrorUrl"

        const val js_getClipboard = "js_getClipboard"

        fun sendJsNative(jsName: String?, webView: WebView?, jsonParams: String?) {
            if (jsName.isNullOrEmpty()) return
            // 使用 org.json.JSONObject.quote() 自动转义双引号和特殊字符，生成安全的带双引号的 JSON 字符串
            val safeJson = org.json.JSONObject.quote(jsonParams ?: "")
            val jsCode = "javascript:$jsName($safeJson)"
            println("jsCode: $jsCode")

            webView?.evaluateJavascript(jsCode) { value ->
                println("js native jsName: $jsName, result: $value")
            }
        }

        fun sendEmptyJsNative(jsName: String?, webView: WebView?) {
            if (jsName.isNullOrEmpty()) return
            val jsCode = "javascript:$jsName()"
            println("jsCode: $jsCode")

            webView?.evaluateJavascript(jsCode) { value ->
                println("js native jsName: $jsName, result: $value")
            }
        }
    }

    @JavascriptInterface
    fun JSToNative(msg: String) {
        viewModel.sendJsMessage(msg)
    }
}