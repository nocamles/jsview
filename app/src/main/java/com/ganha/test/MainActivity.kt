package com.ganha.test

import android.Manifest
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
import com.ganha.test.bean.JsBeanRequest
import com.ganha.test.bean.JsExterUrlBean
import com.ganha.test.viewmodel.MainViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.getValue
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
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
        splashView = findViewById(R.id.splashView)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        initJsNative()
        initWebView()
        setupBackPressed()

        val mockHtml = """
            <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <script>
                        // 模拟加载完成后调用移除启动页
                        window.onload = function() {
                            setTimeout(function() {
                                const request = {
                                    methods: 'removeSplashScreen',
                                    callback: `removeSplashScreen_callback`,
                                    paramObj: null
                                };
                    
                                // 调用 Android 原生方法
                                try {
                                    if (window.android && window.android.JSToNative) {
                                        window.android.JSToNative(JSON.stringify(request));
                                    } else {
                                        // 如果在浏览器环境测试时，给出提示，方便调试
                                        console.warn('当前非Android原生环境，桥接调用信息:', JSON.stringify(request));
                                    }
                                } catch (error) {
                                    console.error('调用原生方法失败:', error);
                                }
                            }, 500);
                        };
                    </script>
                </head>
                <body style="background-color: #282c34; color: white; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0;">
                    <h1>Splash Demo Page</h1>
                </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("http://192.168.1.1", mockHtml, "text/html", "utf-8", null)
    }

    private fun initWebView() {
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
                    Gson().fromJson<JsBeanRequest>(message, JsBeanRequest::class.java)
                when (jsMessage.methods) {
                    js_refresh -> {
                        failedUrl?.let { url ->
                            webView.loadUrl(url)
                        } ?: webView.reload()
                    }

                    js_removeSplashScreen -> {
                        runOnUiThread {
                            webView.loadUrl("http://192.168.1.1")
                            if (splashView.visibility == View.VISIBLE) {
                                splashView.animate()
                                    .alpha(0f)
                                    .setDuration(400)
                                    .withEndAction {
                                        splashView.visibility = View.GONE
                                    }
                                    .start()
                            }
                        }
                    }

                    js_openUrlExternally ->{
                        var jsExterUrlBean =
                            Gson().fromJson<JsExterUrlBean>(jsMessage.paramObj, JsExterUrlBean::class.java)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(jsExterUrlBean.url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun showErrorView() {
        webView.loadUrl("file:///android_asset/net_error.html")
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        // 绑定生命周期：后台锁屏时必须调用 onPause() 解决幽灵声音 Bug
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onDestroy() {
        // 严格在 onDestroy 中执行移除视图、clearHistory()、destroy() 防止 OOM
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.clearHistory()
        webView.destroy()
        super.onDestroy()
    }
}
