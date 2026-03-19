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
        const val js_appPageLoading = "appPageLoading"
        const val js_goBack = "goBack"
        const val js_refresh = "refreshPage"
        const val js_statusBarLight = "statusBarLight"
        const val js_openUrlExternally = "openUrlExternally"


        fun sendJsNative(jsName: String, webView: WebView?, jsonParams: String) {
            var jsCode = "javascript:$jsName('$jsonParams')"
            println("jsCode: $jsCode")

            webView?.evaluateJavascript(jsCode) { value ->
                println("js native jsName: $jsName, result: $value")
            }
        }

        fun sendEmptyJsNative(jsName: String, webView: WebView?) {
            var jsCode = "javascript:$jsName()"
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