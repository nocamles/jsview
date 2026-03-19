package com.ganha.test

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ganha.test.bean.JsBean
import com.ganha.test.bean.JsBean.Companion.js_openUrlExternally
import com.ganha.test.bean.JsBean.Companion.js_refresh
import com.ganha.test.bean.JsBean.Companion.js_removeSplashScreen
import com.ganha.test.bean.JsBean.Companion.sendJsNative
import com.ganha.test.bean.JsBeanRequest
import com.ganha.test.bean.JsExterUrlBean
import com.ganha.test.viewmodel.MainViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.getValue
import kotlin.jvm.java
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import com.ganha.test.bean.JsBean.Companion.js_statusBarLight
import com.ganha.test.bean.JsBean.Companion.sendEmptyJsNative
import com.ganha.test.bean.StatusBarBean

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var splash_webview: WebView? = null
    private lateinit var splashView: View

    private var backPressedTime = 0L
    private var failedUrl: String? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        if (allGranted && pendingPermissionRequest != null) {
            pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
        } else {
            pendingPermissionRequest?.deny()
        }
        pendingPermissionRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        webView = findViewById(R.id.webView)
        splash_webview = findViewById(R.id.webview_Splash)
        splashView = findViewById(R.id.splashView)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        initJsNative()
        initWebView()
        setupBackPressed()
        splash_webview?.loadUrl("file:///android_asset/splash_screen.html")
        webView.loadUrl("file:///android_asset/myTest.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        splash_webview?.settings?.javaScriptEnabled = true
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        // 强制 setTextZoom(100) 忽略系统字体大小
        settings.textZoom = 100

        // 关闭手势播放限制 允许视频连播
        settings.mediaPlaybackRequiresUserGesture = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                // 支持 Deep Linking (URL Scheme 及 App Links)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    failedUrl = request.url.toString()
                    showErrorView()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.e("WebViewTest", "主页面已加载完毕进入 onPageFinished: $url")

                if(splashView.isVisible) {
                    sendEmptyJsNative("finishAnimationFast", splash_webview)

                    splashView.postDelayed({
                        if (splashView.isVisible) {
                            android.util.Log.e("WebViewTest", "JS未按时响应，触发兜底强制移除开屏页")
                            sendEmptyJsNative("finishAnimationFast", splash_webview)
                        }
                    }, 1000)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // 拦截 H5 相机/麦克风权限，自动映射系统原生权限
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                pendingPermissionRequest = request
                val androidPermissions = mutableListOf<String>()
                for (res in request.resources) {
                    if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        androidPermissions.add(Manifest.permission.CAMERA)
                    } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        androidPermissions.add(Manifest.permission.RECORD_AUDIO)
                    }
                }
                if (androidPermissions.isNotEmpty()) {
                    permissionLauncher.launch(androidPermissions.toTypedArray())
                } else {
                    request.grant(request.resources)
                }
            }
        }

        webView.addJavascriptInterface(JsBean(viewModel), "android")
        splash_webview?.addJavascriptInterface(JsBean(viewModel), "android")
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 拦截物理/侧滑返回手势，优先执行 WebView.goBack()
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // 若在 H5 首页，提示“再按一次退出”，禁止单次点击直接闪退
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - backPressedTime > 2000) {
                        Toast.makeText(
                            this@MainActivity,
                            resources.getString(R.string.quit_tips),
                            Toast.LENGTH_SHORT
                        ).show()
                        backPressedTime = currentTime
                    } else {
                        finish()
                    }
                }
            }
        })
    }

    fun initJsNative() {
        lifecycleScope.launch {
            viewModel.messageFlow.collect { message ->
                var jsMessage =
                    Gson().fromJson(message, JsBeanRequest::class.java)
                when (jsMessage.methods) {
                    js_refresh -> {
                        failedUrl?.let { url ->
                            webView.loadUrl(url)
                        } ?: webView.reload()
                    }

                    js_removeSplashScreen -> {
                        runOnUiThread {
                            if (splashView.visibility == View.VISIBLE) {
                                splashView.animate()
                                    .alpha(0f)
                                    .setDuration(400)
                                    .withEndAction {
                                        destroySplashWebView()
                                    }
                                    .start()
                            }
                        }
                    }

                    js_openUrlExternally ->{
                        var jsExterUrlBean =
                            Gson().fromJson(jsMessage.paramObj, JsExterUrlBean::class.java)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(jsExterUrlBean.url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    js_statusBarLight ->{
                        var statusBarBean =
                            Gson().fromJson(jsMessage.paramObj, StatusBarBean::class.java)
                        setStatusBarTextColor(statusBarBean.isLight)
                    }
                }
            }
        }
    }

    fun showErrorView() {
        destroySplashWebView()
        webView.loadUrl("file:///android_asset/net_error.html")
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        splash_webview?.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        // 绑定生命周期：后台锁屏时必须调用 onPause() 解决幽灵声音 Bug
        webView.onPause()
        splash_webview?.onPause()
        webView.pauseTimers()
    }

    override fun onDestroy() {
        // 严格在 onDestroy 中执行移除视图、clearHistory()、destroy() 防止 OOM
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.clearHistory()
        webView.destroy()
        splash_webview?.destroy()
        super.onDestroy()
    }

    /***
     * 销毁开屏页
     */
    private fun destroySplashWebView() {
        if (splash_webview != null) {
            splashView.visibility = View.GONE

            val parent = splash_webview?.parent as? ViewGroup
            parent?.removeView(splash_webview)

            splash_webview?.stopLoading()

            // 4. 清除设置和数据
            splash_webview?.settings?.javaScriptEnabled = false
            splash_webview?.clearHistory()
            splash_webview?.clearView() // 针对老版本API
            splash_webview?.removeAllViews()
            splash_webview?.loadUrl("about:blank")
        }
    }
}
