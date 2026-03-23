package com.ganha.test.bean

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.ganha.test.viewmodel.MainViewModel
import java.util.logging.Logger

class JsBean(val viewModel: MainViewModel) {
    companion object {
        const val js_deviceInfo = "deviceInfo"
        const val js_channelInfo = "channelInfo"
        const val js_installedSocialApps = "installedSocialApps"
        const val js_requestPushPermission = "requestPushPermission"
        const val js_shareTo = "shareTo"
        const val js_copyToClipboard = "copyToClipboard"
        const val js_vibrate = "vibrate"
        const val js_saveImageToGallery = "saveImageToGallery"
        const val js_removeSplashScreen = "removeSplashScreen"
        const val js_onAppLifecycle = "onAppLifecycle"
        const val js_goBack = "goBack"
        const val js_refresh = "refreshPage"
        const val js_statusBarLight = "statusBarLight"
        const val js_openUrlExternally = "openUrlExternally"
        const val js_appUpdate = "appUpdate"

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