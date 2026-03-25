package com.ganha.test

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ComponentCaller
import android.app.Dialog
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.ganha.test.bean.AppUpdateBean
import com.ganha.test.bean.AppinfoBean
import com.ganha.test.bean.JsBean
import com.ganha.test.bean.JsBean.Companion.js_appUpdate
import com.ganha.test.bean.JsBean.Companion.js_channelInfo
import com.ganha.test.bean.JsBean.Companion.js_clickNotificationBar
import com.ganha.test.bean.JsBean.Companion.js_copyToClipboard
import com.ganha.test.bean.JsBean.Companion.js_deviceInfo
import com.ganha.test.bean.JsBean.Companion.js_getBaseUrlInfo
import com.ganha.test.bean.JsBean.Companion.js_getPermissionStatus
import com.ganha.test.bean.JsBean.Companion.js_getPushToken
import com.ganha.test.bean.JsBean.Companion.js_goBack
import com.ganha.test.bean.JsBean.Companion.js_installedSocialApps
import com.ganha.test.bean.JsBean.Companion.js_onAppLifecycle
import com.ganha.test.bean.JsBean.Companion.js_openUrlExternally
import com.ganha.test.bean.JsBean.Companion.js_refresh
import com.ganha.test.bean.JsBean.Companion.js_removeSplashScreen
import com.ganha.test.bean.JsBean.Companion.js_requestPermission
import com.ganha.test.bean.JsBean.Companion.js_saveImageToGallery
import com.ganha.test.bean.JsBean.Companion.js_shareTo
import com.ganha.test.bean.JsBean.Companion.js_statusBarLight
import com.ganha.test.bean.JsBean.Companion.js_vibrate
import com.ganha.test.bean.JsBean.Companion.sendEmptyJsNative
import com.ganha.test.bean.JsBean.Companion.sendJsNative
import com.ganha.test.bean.JsBeanRequest
import com.ganha.test.bean.JsExterUrlBean
import com.ganha.test.bean.StatusBarBean
import com.ganha.test.bean.VibrateBean
import com.ganha.test.utils.DeviceIdUtil
import com.ganha.test.utils.DeviceInfoHelper
import com.ganha.test.utils.MyCustomTipsDialog
import com.ganha.test.utils.PermissionHelper
import com.ganha.test.utils.RequestCallback
import com.ganha.test.viewmodel.MainViewModel
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.messaging.messaging
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.get
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.hjq.permissions.permission.PermissionLists
import com.tencent.vasdolly.helper.ChannelReaderUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URISyntaxException
import java.net.URL
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var mainUrlText: String = ""
    private var mainUrlGanha: String = ""

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uri = data?.data
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val compressedUri = compressImageIfNeeded(uri)
                    withContext(Dispatchers.Main) {
                        filePathCallback?.onReceiveValue(arrayOf(compressedUri))
                        filePathCallback = null
                    }
                }
                return@registerForActivityResult
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private suspend fun compressImageIfNeeded(uri: Uri): Uri {
        return withContext(Dispatchers.IO) {
            try {
                var fileSize = 0L
                contentResolver.openFileDescriptor(uri, "r")?.use {
                    fileSize = it.statSize
                }
                if (fileSize <= 500 * 1024) {
                    return@withContext uri
                }

                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) return@withContext uri

                var quality = 90
                var outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

                while (outputStream.toByteArray().size > 500 * 1024 && quality > 10) {
                    quality -= 10
                    outputStream.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                }

                val tempFile = File(cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
                val fileOutputStream = FileOutputStream(tempFile)
                fileOutputStream.write(outputStream.toByteArray())
                fileOutputStream.flush()
                fileOutputStream.close()
                outputStream.close()

                return@withContext Uri.fromFile(tempFile)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext uri
            }
        }
    }

    private var splash_webview: WebView? = null
    private lateinit var splashView: View

    private var pendingDeepLink: String? = null
    private var backPressedTime = 0L
    private var failedUrl: String? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private val viewModel: MainViewModel by viewModels()

    private var progressBar: ProgressBar? = null
    private var tvProgress: TextView? = null
    private var tvFileName: TextView? = null

    private var isDownloading = false
    private var customDialog: Dialog? = null
    private var updateDownloadJob: kotlinx.coroutines.Job? = null
    
    private var isAppInForeground = false
    private var pendingInstallApkUri: Uri? = null
    private val installPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                pendingInstallApkUri?.let { 
                    installApk(it) 
                    pendingInstallApkUri = null
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_install_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var statusBarHeight: Int = 0
    private var navBarHeight: Int = 0

    private val MIN_DISPLAY_TIME = 1500L
    private lateinit var webViewLocalCache: com.ganha.test.utils.WebViewLocalCache

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webViewLocalCache = com.ganha.test.utils.WebViewLocalCache(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        webView = findViewById(R.id.webView)
        splash_webview = findViewById(R.id.webview_Splash)
        splashView = findViewById(R.id.splashView)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = systemBars.top
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        initJsNative()
        initWebView()
        setupBackPressed()
        checkAndClearDownloadCache()
        splash_webview?.loadUrl("file:///android_asset/splash_screen.html")
        webView.loadUrl("file:///android_asset/myTest.html")

        firebaseAnalytics = Firebase.analytics
        updateDivEvent()
        initFbConfig()

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            val urlStr = uri.toString()
            Toast.makeText(this, getString(R.string.received_external_web_call, urlStr), Toast.LENGTH_SHORT).show()
            
            if (webView.progress == 100) {
                val jsCode = "javascript:if(window.JSBridge && window.JSBridge.onDeepLink){window.JSBridge.onDeepLink('$urlStr');}"
                webView.evaluateJavascript(jsCode, null)
            } else {
                pendingDeepLink = urlStr
            }
        }
    }

    private fun checkAndClearDownloadCache() {
        try {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var currentVersion = 0
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val lastVersion = prefs.getInt("last_installed_version", -1)
            if (currentVersion != lastVersion) {
                // 获取渠道号
                var channelInfo = ChannelReaderUtil.getChannel(this)

                // 如果渠道号为空，则尝试获取剪贴板内容
                if (channelInfo.isNullOrEmpty()) {
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboardManager.hasPrimaryClip()) {
                        val clipData = clipboardManager.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            channelInfo = clipData.getItemAt(0).text?.toString() ?: ""
                        }
                    }
                }

                val jsonObj = JSONObject()
                jsonObj.put("channel", channelInfo ?: "---")

                // 需要在主线程中调用 jsBridge
                runOnUiThread {
                    sendJsNative("channelInfo_callback", webView, jsonObj.toString())
                }

                val downloadDir = File(cacheDir, "downloadapk")
                if (downloadDir.exists()) {
                    downloadDir.deleteRecursively()
                }
                prefs.edit().putInt("last_installed_version", currentVersion).apply()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        splash_webview?.settings?.javaScriptEnabled = true
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true

        // 强制 setTextZoom(100) 忽略系统字体大小
        settings.textZoom = 100

        // 关闭手势播放限制 允许视频连播
        settings.mediaPlaybackRequiresUserGesture = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (isDownloading) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.downloading_please_wait),
                    Toast.LENGTH_SHORT
                ).show()
                return@setDownloadListener
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                PermissionHelper.checkPermission(
                    this@MainActivity,
                    arrayListOf(PermissionLists.getWriteExternalStoragePermission()),
                    getString(R.string.permission_storage_download_reason),
                    getString(R.string.permission_storage_setting_reason),
                    object : RequestCallback {
                        override fun onGranted() {
                            startDownloadTask(url, userAgent, contentDisposition, mimetype)
                        }

                        override fun onDenied() {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.permission_denied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } else {
                startDownloadTask(url, userAgent, contentDisposition, mimetype)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request != null && request.isForMainFrame) {
                    return super.shouldInterceptRequest(view, request)
                }

                request?.let {
                    val localResponse = webViewLocalCache.interceptRequest(it)
                    if (localResponse != null) {
                        return localResponse
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url =
                    request?.url?.toString() ?: return super.shouldOverrideUrlLoading(view, request)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return super.shouldOverrideUrlLoading(view, request)
                }

                if (url.startsWith("intent://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        intent.component = null
                        intent.selector = null

                        // 检查设备上是否安装了该包名对应的应用
                        if (intent.resolveActivity(view!!.context.packageManager) != null) {
                            view.context?.startActivity(intent)
                        } else {
                            // 如果没安装应用，可以看是否有 fallback 链接，跳转去下载
                            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                            if (!fallbackUrl.isNullOrEmpty()) {
                                view.loadUrl(fallbackUrl)
                            } else {
                                Log.e("WebView", "未安装目标应用: ${intent.`package`}")
                            }
                        }
                        return true // 拦截处理完毕
                    } catch (e: URISyntaxException) {
                        Log.e("WebView", "Intent URI 解析失败", e)
                        return false
                    } catch (e: ActivityNotFoundException) {
                        Log.e("WebView", "未找到对应的 Activity", e)
                        return true
                    }
                }

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    this@MainActivity.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                    return true
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) {
                    return super.shouldOverrideUrlLoading(view, url as String?)
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return super.shouldOverrideUrlLoading(view, url)
                }

                if (url.startsWith("intent://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        intent.component = null
                        intent.selector = null

                        // 检查设备上是否安装了该包名对应的应用
                        if (intent.resolveActivity(view!!.context.packageManager) != null) {
                            view.context?.startActivity(intent)
                        } else {
                            // 如果没安装应用，可以看是否有 fallback 链接，跳转去下载
                            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                            if (!fallbackUrl.isNullOrEmpty()) {
                                view.loadUrl(fallbackUrl)
                            } else {
                                Log.e("WebView", "未安装目标应用: ${intent.`package`}")
                            }
                        }
                        return true // 拦截处理完毕
                    } catch (e: URISyntaxException) {
                        Log.e("WebView", "Intent URI 解析失败", e)
                        return false
                    } catch (e: ActivityNotFoundException) {
                        Log.e("WebView", "未找到对应的 Activity", e)
                        return true
                    }
                }

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    this@MainActivity.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                    return true
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    if (error?.errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
                        return
                    }
                    failedUrl = request.url.toString()
                    showErrorView()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.e("WebViewTest", "主页面已加载完毕进入 onPageFinished: $url")

                if (splashView.isVisible) {
                    sendEmptyJsNative("finishAnimationFast", splash_webview)

                    splashView.postDelayed({
                        if (splashView.isVisible) {
                            android.util.Log.e("WebViewTest", "JS未按时响应，触发兜底强制移除开屏页")
                            sendEmptyJsNative("finishAnimationFast", splash_webview)
                        }
                    }, 1000)
                }

                pendingDeepLink?.let { deepLinkUrl ->
                    val jsCode = "javascript:if(window.JSBridge && window.JSBridge.onDeepLink){window.JSBridge.onDeepLink('$deepLinkUrl');}"
                    webView.evaluateJavascript(jsCode, null)
                    pendingDeepLink = null
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                fileChooserLauncher.launch(intent)
                return true
            }

            // 拦截 H5 相机/麦克风权限，自动映射系统原生权限
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                pendingPermissionRequest = request
                val ipPermissions = mutableListOf<com.hjq.permissions.permission.base.IPermission>()
                for (res in request.resources) {
                    if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        ipPermissions.add(PermissionLists.getCameraPermission())
                    } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        ipPermissions.add(PermissionLists.getRecordAudioPermission())
                    }
                }
                if (ipPermissions.isNotEmpty()) {
                    PermissionHelper.checkPermission(
                        this@MainActivity,
                        ipPermissions,
                        getString(R.string.permission_camera_mic_reason),
                        getString(R.string.permission_camera_mic_setting_reason),
                        object : RequestCallback {
                            override fun onGranted() {
                                pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
                                pendingPermissionRequest = null
                            }

                            override fun onDenied() {
                                pendingPermissionRequest?.deny()
                                pendingPermissionRequest = null
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.permission_denied_function_disabled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } else {
                    request.grant(request.resources)
                    pendingPermissionRequest = null
                }
            }
        }

        // 添加长按监听，拦截图片保存
        webView.setOnLongClickListener {
            val hitTestResult = webView.hitTestResult
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                val imageUrl = hitTestResult.extra
                if (!imageUrl.isNullOrEmpty()) {
                    showSaveImageDialog(imageUrl)
                    return@setOnLongClickListener true
                }
            }
            false
        }

        webView.addJavascriptInterface(JsBean(viewModel), "App")
        splash_webview?.addJavascriptInterface(JsBean(viewModel), "App")
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
                try {
                    val jsMessage = Gson().fromJson(message, JsBeanRequest::class.java)
                    if (jsMessage == null || jsMessage.methods.isNullOrEmpty()) return@collect
                    
                    when (jsMessage.methods) {
                        js_requestPermission -> {
                            try {
                                val permissionBean = Gson().fromJson(jsMessage.paramObj, com.ganha.test.bean.PermissionBean::class.java)
                                val permList = mutableListOf<com.hjq.permissions.permission.base.IPermission>()
                                val requestedPerms = permissionBean?.permissions ?: if (!permissionBean?.permission.isNullOrEmpty()) listOf(permissionBean!!.permission!!) else emptyList()
                                
                                requestedPerms.forEach { perm ->
                                    when (perm.lowercase()) {
                                        "camera" -> permList.add(PermissionLists.getCameraPermission())
                                        "storage" -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permList.add(PermissionLists.getReadMediaImagesPermission())
                                                permList.add(PermissionLists.getReadMediaVideoPermission())
                                                permList.add(PermissionLists.getReadMediaAudioPermission())
                                            } else {
                                                permList.add(PermissionLists.getWriteExternalStoragePermission())
                                                permList.add(PermissionLists.getReadExternalStoragePermission())
                                            }
                                        }
                                        "manage_storage" -> permList.add(PermissionLists.getManageExternalStoragePermission())
                                        "audio", "microphone" -> permList.add(PermissionLists.getRecordAudioPermission())
                                        "location" -> {
                                            permList.add(PermissionLists.getAccessFineLocationPermission())
                                            permList.add(PermissionLists.getAccessCoarseLocationPermission())
                                        }
                                        "notification" -> permList.add(PermissionLists.getPostNotificationsPermission())
                                        "contacts" -> permList.add(PermissionLists.getReadContactsPermission())
                                    }
                                }
                                
                                if (permList.isNotEmpty()) {
                                    PermissionHelper.checkPermission(
                                        this@MainActivity,
                                        permList,
                                        permissionBean?.explainReason ?: getString(R.string.need_get_permission),
                                        permissionBean?.forwardtoSettingReason ?: getString(R.string.go_to_settings),
                                        object : RequestCallback {
                                            override fun onGranted() {
                                                val jsonStr = "{\"status\":\"granted\"}"
                                                sendJsNative(jsMessage.callback, webView, jsonStr)
                                            }
                                            override fun onDenied() {
                                                val jsonStr = "{\"status\":\"denied\"}"
                                                sendJsNative(jsMessage.callback, webView, jsonStr)
                                            }
                                        }
                                    )
                                } else {
                                     val jsonStr = "{\"status\":\"error\", \"message\":\"Unknown permissions\"}"
                                     sendJsNative(jsMessage.callback, webView, jsonStr)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_getPermissionStatus -> {
                            try {
                                val permissionBean = Gson().fromJson(jsMessage.paramObj, com.ganha.test.bean.PermissionBean::class.java)
                                val permList = mutableListOf<com.hjq.permissions.permission.base.IPermission>()
                                val requestedPerms = permissionBean?.permissions ?: if (!permissionBean?.permission.isNullOrEmpty()) listOf(permissionBean!!.permission!!) else emptyList()
                                
                                requestedPerms.forEach { perm ->
                                    when (perm.lowercase()) {
                                        "camera" -> permList.add(PermissionLists.getCameraPermission())
                                        "storage" -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permList.add(PermissionLists.getReadMediaImagesPermission())
                                                permList.add(PermissionLists.getReadMediaVideoPermission())
                                                permList.add(PermissionLists.getReadMediaAudioPermission())
                                            } else {
                                                permList.add(PermissionLists.getWriteExternalStoragePermission())
                                                permList.add(PermissionLists.getReadExternalStoragePermission())
                                            }
                                        }
                                        "manage_storage" -> permList.add(PermissionLists.getManageExternalStoragePermission())
                                        "audio", "microphone" -> permList.add(PermissionLists.getRecordAudioPermission())
                                        "location" -> {
                                            permList.add(PermissionLists.getAccessFineLocationPermission())
                                            permList.add(PermissionLists.getAccessCoarseLocationPermission())
                                        }
                                        "notification" -> permList.add(PermissionLists.getPostNotificationsPermission())
                                        "contacts" -> permList.add(PermissionLists.getReadContactsPermission())
                                    }
                                }
                                
                                if (permList.isNotEmpty()) {
                                    val isGranted = com.hjq.permissions.XXPermissions.isGrantedPermissions(this@MainActivity, permList)
                                    val status = if (isGranted) "granted" else "denied"
                                    val jsonStr = "{\"status\":\"$status\"}"
                                    sendJsNative(jsMessage.callback, webView, jsonStr)
                                } else {
                                    val jsonStr = "{\"status\":\"error\", \"message\":\"Unknown permissions\"}"
                                    sendJsNative(jsMessage.callback, webView, jsonStr)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_appUpdate -> {
                            try {
                                val appUpdateBean = Gson().fromJson(
                                    jsMessage.paramObj,
                                    AppUpdateBean::class.java
                                )
                                if (appUpdateBean?.needUpdate == true && !appUpdateBean.updateUrl.isNullOrEmpty()) {
                                    runOnUiThread {
                                        if (appUpdateBean.isBackGround) {
                                            downloadAppUpdate(appUpdateBean)
                                        } else {
                                            MyCustomTipsDialog(
                                                this@MainActivity,
                                                getString(R.string.version_update),
                                                getString(R.string.found_new_version, appUpdateBean.versionName),
                                                getString(R.string.remind_later),
                                                getString(R.string.update_now),
                                                onCancelListener = null,
                                                onConfirmListener = {
                                                    downloadAppUpdate(appUpdateBean)
                                                }
                                            ).apply {
                                                setCancelable(!appUpdateBean.isForceUpdate)
                                            }.show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_goBack -> {
                            runOnUiThread {
                                if (webView.canGoBack()) {
                                    webView.goBack()
                                } else {
                                    finish()
                                }
                            }
                        }

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

                        js_copyToClipboard -> {
                            try {
                                val copyBean = Gson().fromJson(
                                    jsMessage.paramObj,
                                    com.ganha.test.bean.CopyBean::class.java
                                )
                                copyBean?.text?.let { textToCopy ->
                                    val clipboard =
                                        getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip =
                                        android.content.ClipData.newPlainText("label", textToCopy)
                                    clipboard.setPrimaryClip(clip)
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.copy_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_shareTo -> {
                            try {
                                val shareBean = Gson().fromJson(
                                    jsMessage.paramObj,
                                    com.ganha.test.bean.ShareBean::class.java
                                )
                                handleShare(shareBean)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_openUrlExternally -> {
                            try {
                                val jsExterUrlBean =
                                    Gson().fromJson(jsMessage.paramObj, JsExterUrlBean::class.java)
                                if (jsExterUrlBean != null && !jsExterUrlBean.url.isNullOrEmpty()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(jsExterUrlBean.url))
                                    startActivity(intent)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_vibrate -> {
                            try {
                                val vibrateBean =
                                    Gson().fromJson(jsMessage.paramObj, VibrateBean::class.java)
                                        ?: VibrateBean()
                                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                                if (vibrator.hasVibrator()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val amplitude =
                                            if (vibrateBean.amplitude in 1..255) vibrateBean.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                                        vibrator.vibrate(
                                            VibrationEffect.createOneShot(
                                                vibrateBean.duration,
                                                amplitude
                                            )
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(vibrateBean.duration)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_statusBarLight -> {
                            try {
                                val statusBarBean =
                                    Gson().fromJson(jsMessage.paramObj, StatusBarBean::class.java)
                                if (statusBarBean != null) {
                                    setStatusBarTextColor(statusBarBean)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_saveImageToGallery -> {
                            try {
                                val jsExterUrlBean =
                                    Gson().fromJson(jsMessage.paramObj, JsExterUrlBean::class.java)
                                if (jsExterUrlBean != null && !jsExterUrlBean.url.isNullOrEmpty()) {
                                    runOnUiThread {
                                        checkPermissionAndSaveImage(jsExterUrlBean.url)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_channelInfo -> {
                            // 获取渠道号
                            var channelInfo = ChannelReaderUtil.getChannel(this@MainActivity)

                            // 如果渠道号为空，则尝试获取剪贴板内容
                            if (channelInfo.isNullOrEmpty()) {
                                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                if (clipboardManager.hasPrimaryClip()) {
                                    val clipData = clipboardManager.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        channelInfo = clipData.getItemAt(0).text?.toString() ?: ""
                                    }
                                }
                            }

                            val jsonObj = JSONObject()
                            jsonObj.put("channel", channelInfo ?: "---")

                            sendJsNative(jsMessage.callback, webView, jsonObj.toString())
                        }

                        js_installedSocialApps -> {
                            try {
                                val pm = packageManager
                                fun checkInstalled(vararg packages: String): Boolean {
                                    for (pkg in packages) {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                pm.getPackageInfo(pkg, 0)
                                            }
                                            return true
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                    return false
                                }

                                val resultObj = org.json.JSONObject().apply {
                                    put("whatsapp", checkInstalled("com.whatsapp", "com.whatsapp.w4b"))
                                    put("facebook", checkInstalled("com.facebook.katana", "com.facebook.lite"))
                                    put("messenger", checkInstalled("com.facebook.orca", "com.facebook.mlite"))
                                    put("instagram", checkInstalled("com.instagram.android"))
                                    put("tiktok", checkInstalled("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"))
                                    put("kwai", checkInstalled("com.kwai.video", "com.smile.gifmaker"))
                                }
                                sendJsNative(jsMessage.callback, webView, resultObj.toString())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_getBaseUrlInfo -> {
                            try {
                                val jsonObj = JSONObject()
                                jsonObj.put("mainUrlText", mainUrlText)
                                jsonObj.put("mainUrlGanha", mainUrlGanha)
                                sendJsNative(jsMessage.callback, webView, jsonObj.toString())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        js_deviceInfo -> {
                            lifecycleScope.launch {
                                val isEmulator = DeviceInfoHelper.isEmulator()
                                val isVpnOrProxy = DeviceInfoHelper.isVpnOrProxy(this@MainActivity)
                                val isRooted = DeviceInfoHelper.isRooted()
                                val isUsbDebuggingOrDevMode =
                                    DeviceInfoHelper.isUsbDebuggingOrDevMode(this@MainActivity)
                                val hasSimCard = DeviceInfoHelper.hasSimCard(this@MainActivity)
                                val simOperator = DeviceInfoHelper.getSimOperator(this@MainActivity)
                                val deviceId = DeviceIdUtil.getUniqueDeviceId(this@MainActivity)

                                val simCountry = DeviceInfoHelper.getSimCountry(this@MainActivity)

                                var versionCode = 0
                                var versionName = ""
                                var packName = ""
                                try {
                                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        packageInfo.longVersionCode.toInt()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        packageInfo.versionCode
                                    }
                                    versionName = packageInfo.versionName ?: ""
                                    packName = packageName
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                val appInfo = AppinfoBean(
                                    isEmulator = isEmulator,
                                    isVpnOrProxy = isVpnOrProxy,
                                    isRooted = isRooted,
                                    isUsbDebuggingOrDevMode = isUsbDebuggingOrDevMode,
                                    hasSimCard = hasSimCard,
                                    sim_country = simCountry,
                                    simOperator = simOperator,
                                    deviceId = deviceId,
                                    versionCode = versionCode,
                                    versionName = versionName,
                                    packageName = packName,
                                    statusBarHeight = statusBarHeight.toH5Value(this@MainActivity)
                                )
                                val jsonStr = Gson().toJson(appInfo)
                                withContext(Dispatchers.Main) {
                                    sendJsNative(jsMessage.callback, webView, jsonStr)
                                }
                            }
                        }
                        js_getPushToken -> {
                            Firebase.messaging.token.addOnCompleteListener(
                                OnCompleteListener { task ->
                                    if (!task.isSuccessful) {
                                        Log.w("WebViewTest", "Fetching FCM registration token failed", task.exception)
                                        return@OnCompleteListener
                                    }
                                    // Get new FCM registration token
                                    val token = task.result
                                    // Log and toast
                                    val msg = "FCM registration Token: ${token}"
                                    Log.d("WebViewTest", msg)
                                    try {
                                        val jsonObj = JSONObject()
                                        jsonObj.put("FCMRegistrationToken", token)
                                        sendJsNative(jsMessage.callback, webView, jsonObj.toString())
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                            )
                        }
                        js_clickNotificationBar -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // Create channel to show notifications.
                                val channelId = getString(R.string.default_notification_channel_id)
                                val channelName = "TestCCC"
                                val notificationManager = getSystemService(NotificationManager::class.java)
                                notificationManager?.createNotificationChannel(
                                    NotificationChannel(
                                        channelId,
                                        channelName,
                                        NotificationManager.IMPORTANCE_LOW,
                                    ),
                                )
                            }
                            sendNotification(getString(R.string.test_notification_content), getString(R.string.test_notification_title))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun downloadAppUpdate(appUpdateBean: AppUpdateBean) {
        println(Gson().toJson(appUpdateBean))
        val urlStr = appUpdateBean.updateUrl
        val fileName = "${appUpdateBean.apkName}.apk"

        val dir = File(cacheDir, "downloadapk")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)

        if (file.exists() && file.length() > 0) {
            installApk(Uri.fromFile(file))
            return
        }

        if (!appUpdateBean.isBackGround) {
            showCustomDialog(fileName, appUpdateBean.isForceUpdate)
        }

        isDownloading = true

        customDialog?.findViewById<TextView>(R.id.btnCancel)?.setOnClickListener {
            if (!appUpdateBean.isForceUpdate) {
                updateDownloadJob?.cancel()
                isDownloading = false
                customDialog?.dismiss()
                Toast.makeText(this, getString(R.string.download_canceled), Toast.LENGTH_SHORT).show()
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        updateDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var downloadedFile: File? = null

            try {
                var url = URL(urlStr)
                var connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(this@MainActivity))
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                connection.setRequestProperty("Connection", "keep-alive")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true

                var redirectCount = 0
                while (connection.responseCode / 100 == 3 && redirectCount < 5) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    url = URL(newUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(this@MainActivity))
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                    connection.setRequestProperty("Connection", "keep-alive")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    redirectCount++
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val fileLength = connection.contentLength

                    if (file.exists()) {
                        file.delete()
                    }

                    val input: InputStream = connection.inputStream
                    val output = FileOutputStream(file)

                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    var lastProgress = -1

                    while (input.read(data).also { count = it } != -1 && isActive && isDownloading) {
                        total += count
                        output.write(data, 0, count)

                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                if (!appUpdateBean.isBackGround) {
                                    withContext(Dispatchers.Main) {
                                        progressBar?.progress = progress
                                        tvProgress?.text = "$progress%"
                                    }
                                }
                            }
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()

                    if (isActive && isDownloading) {
                        isSuccess = true
                        downloadedFile = file
                    } else {
                        file.delete()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                if (isDownloading) {
                    if (!appUpdateBean.isBackGround) {
                        customDialog?.dismiss()
                    }
                    isDownloading = false

                    if (isSuccess && downloadedFile != null) {
                        installApk(Uri.fromFile(downloadedFile!!))
                    } else {
                        if (isActive) {
                            Toast.makeText(this@MainActivity, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun installApk(apkUri: Uri) {
        if (!isAppInForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pendingInstallApkUri = apkUri
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasInstallPermission = packageManager.canRequestPackageInstalls()
            if (!hasInstallPermission) {
                pendingInstallApkUri = apkUri
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                installPermissionLauncher.launch(intent)
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        var finalUri = apkUri
        if (apkUri.scheme == "file") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val file = File(apkUri.path ?: "")
                if (file.exists()) {
                    finalUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "$packageName.fileprovider",
                        file
                    )
                }
            }
        }
        
        intent.setDataAndType(finalUri, "application/vnd.android.package-archive")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.install_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownloadTask(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        showCustomDialog(fileName, false)

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                val cookie = CookieManager.getInstance().getCookie(url)
                addRequestHeader("Cookie", cookie)
                addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setTitle(fileName)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            customDialog?.findViewById<TextView>(R.id.btnCancel)?.setOnClickListener {
                downloadManager.remove(downloadId) // 移除下载任务
                isDownloading = false
                customDialog?.dismiss()
                Toast.makeText(this, getString(R.string.download_canceled), Toast.LENGTH_SHORT)
                    .show()
            }

            observeDownloadProgress(downloadManager, downloadId, fileName)

        } catch (e: Exception) {
            isDownloading = false
            customDialog?.dismiss()
            Toast.makeText(
                this,
                getString(R.string.download_task_create_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeDownloadProgress(
        manager: DownloadManager,
        downloadId: Long,
        fileName: String
    ) {
        val startTime = System.currentTimeMillis()

        isDownloading = true

        lifecycleScope.launch(Dispatchers.IO) {
            var finishDownload = false
            var isSuccess = false

            while (!finishDownload && isActive && isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)

                if (cursor != null && cursor.moveToFirst()) {
                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val loadedIndex =
                        cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (totalIndex != -1 && loadedIndex != -1 && statusIndex != -1) {
                        val total = cursor.getLong(totalIndex)
                        val loaded = cursor.getLong(loadedIndex)
                        val status = cursor.getInt(statusIndex)

                        val progress = if (total > 0) ((loaded * 100) / total).toInt() else 0

                        // 更新 UI
                        withContext(Dispatchers.Main) {
                            progressBar?.progress = progress
                            tvProgress?.text = "$progress%"
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            finishDownload = true
                            isSuccess = true
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            finishDownload = true
                            isSuccess = false
                        }
                    }
                } else {
                    finishDownload = true
                }
                cursor?.close()

                if (!finishDownload) delay(200)
            }

            val endTime = System.currentTimeMillis()
            val timeElapsed = endTime - startTime
            if (timeElapsed < MIN_DISPLAY_TIME) {
                delay(MIN_DISPLAY_TIME - timeElapsed)
            }

            withContext(Dispatchers.Main) {
                if (isDownloading) {
                    customDialog?.dismiss()
                    isDownloading = false

                    if (isSuccess) {
                        val path = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.download_success_path, path),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        if (finishDownload) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.download_failed_or_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun showCustomDialog(fileName: String, isForceUpdate: Boolean = false) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_download_custom, null)

        dialog.setContentView(view)
        dialog.setCancelable(!isForceUpdate)
        dialog.setCanceledOnTouchOutside(false)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        tvFileName = view.findViewById(R.id.tvFileName)
        progressBar = view.findViewById(R.id.progressBar)
        tvProgress = view.findViewById(R.id.tvProgress)
        
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)
        btnCancel.isVisible = !isForceUpdate

        tvFileName?.text = fileName

        customDialog = dialog
        dialog.show()
    }

    fun showErrorView() {
        destroySplashWebView()
        webView.loadUrl("file:///android_asset/net_error.html")
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        webView.onResume()
        splash_webview?.onResume()
        webView.resumeTimers()
        sendJsNative(js_onAppLifecycle, webView, "{\"status\":\"foreground\"}")
        pendingInstallApkUri?.let { 
            installApk(it)
            pendingInstallApkUri = null 
        }
        handleNotificationClick(intent)
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        // 绑定生命周期：后台锁屏时必须调用 onPause() 解决幽灵声音 Bug
        webView.onPause()
        splash_webview?.onPause()
        webView.pauseTimers()
        sendJsNative(js_onAppLifecycle, webView, "{\"status\":\"background\"}")
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

    private fun showSaveImageDialog(imageUrl: String) {
        MyCustomTipsDialog(
            this,
            getString(R.string.tips),
            getString(R.string.save_image_to_gallery_prompt),
            getString(R.string.cancel),
            getString(R.string.save),
            onCancelListener = null,
            onConfirmListener = {
                checkPermissionAndSaveImage(imageUrl)
            }
        ).show()
    }

    private fun checkPermissionAndSaveImage(imageUrl: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            PermissionHelper.checkPermission(
                this,
                arrayListOf(PermissionLists.getWriteExternalStoragePermission()),
                getString(R.string.permission_storage_save_image_reason),
                getString(R.string.permission_storage_save_image_setting_reason),
                object : RequestCallback {
                    override fun onGranted() {
                        performSaveImage(imageUrl)
                    }

                    override fun onDenied() {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.permission_denied_cannot_save_image),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        } else {
            // Android 10+ 不需要存储权限即可向公共图库插入图片
            performSaveImage(imageUrl)
        }
    }

    private fun performSaveImage(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (imageUrl.startsWith("data:image")) {
                    // 处理 Base64 格式的图片
                    val base64Data = imageUrl.substringAfter(",")
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else {
                    // 处理网络 URL 格式的图片
                    val url = URL(imageUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    BitmapFactory.decodeStream(connection.inputStream)
                }

                if (bitmap != null) {
                    saveBitmapToGallery(bitmap)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.image_decode_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.save_failed_reason, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap) {
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        var isSuccess = false
        var savedPath = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { os ->
                    isSuccess = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                }
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { os ->
                isSuccess = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
            }
            if (isSuccess) {
                savedPath = file.absolutePath
                MediaScannerConnection.scanFile(
                    this@MainActivity,
                    arrayOf(savedPath),
                    arrayOf("image/jpeg"),
                    null
                )
            }
        }

        withContext(Dispatchers.Main) {
            if (isSuccess) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.image_saved_successfully),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.save_image_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val TAG = "FbConfig"

    private fun initFbConfig(){
        // [START get_remote_config_instance]
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
        // [END get_remote_config_instance]

        // [START enable_dev_mode]
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        // [END enable_dev_mode]

        // [START set_default_values]
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        // [END set_default_values]

        // [START fetch_config_with_callback]
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")
                } else {
                    Log.d(TAG, "Fetch failed")
                }
                displayWelcomeMessage()
            }
        // [END fetch_config_with_callback]

        // [START add_config_update_listener]
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Log.d(TAG, "Updated keys: " + configUpdate.updatedKeys)

                if (configUpdate.updatedKeys.contains("welcome_message")) {
                    remoteConfig.activate().addOnCompleteListener {
                        displayWelcomeMessage()
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w(TAG, "Config update error with code: " + error.code, error)
            }
        })
        // [END add_config_update_listener]
    }

    private fun displayWelcomeMessage() {
        val remoteConfig = Firebase.remoteConfig
        // [START get_config_values]
        mainUrlText = remoteConfig["main_url_text"].asString()
        mainUrlGanha = remoteConfig["main_url_ganha"].asString()
        Log.d(TAG,"mainUrlText:${mainUrlText}\nmainUrlGanha:${mainUrlGanha}")
        // [END get_config_values]
    }

    private fun handleShare(shareBean: com.ganha.test.bean.ShareBean?) {
        if (shareBean == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            val shareText = buildString {
                if (!shareBean.title.isNullOrEmpty()) append(shareBean.title).append("\n")
                if (!shareBean.content.isNullOrEmpty()) append(shareBean.content).append("\n")
            }.trim()

            var imageUri: Uri? = null
            if (!shareBean.imageUrl.isNullOrEmpty()) {
                imageUri = downloadAndSaveImageToCache(shareBean.imageUrl)
            }

            val isSpecificApp = !shareBean.targetApp.isNullOrEmpty()
            val isSms = shareBean.targetApp?.lowercase() == "sms"

            withContext(Dispatchers.Main) {
                // 如果是分享到指定App（包含SMS），先把文字复制到剪贴板，并提示用户
                if (isSpecificApp) {
                    if (shareText.isNotEmpty()) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("label", shareText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@MainActivity, getString(R.string.copied_exclusive_link_and_text), Toast.LENGTH_LONG).show()
                    }
                }

                // SMS短信按照主流软件单独处理
                if (isSms) {
                    val intent = if (imageUri != null) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            if (shareText.isNotEmpty()) {
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:")
                            if (shareText.isNotEmpty()) {
                                putExtra("sms_body", shareText)
                            }
                        }
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, getString(R.string.sms_app_not_found), Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    if (imageUri != null) {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        if (shareText.isNotEmpty()) {
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        type = "text/plain"
                        if (shareText.isNotEmpty()) {
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                    }
                }

                if (isSpecificApp) {
                    val targetPackages = when (shareBean.targetApp?.lowercase()) {
                        "whatsapp" -> listOf("com.whatsapp", "com.whatsapp.w4b")
                        "facebook" -> listOf("com.facebook.katana", "com.facebook.lite")
                        "messenger" -> listOf("com.facebook.orca", "com.facebook.mlite")
                        "instagram", "ins" -> listOf("com.instagram.android")
                        "tiktok" -> listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
                        "kwai" -> listOf("com.kwai.video", "com.smile.gifmaker")
                        else -> emptyList()
                    }

                    var packageToUse: String? = null
                    for (pkg in targetPackages) {
                        if (isPackageInstalled(pkg)) {
                            packageToUse = pkg
                            break
                        }
                    }

                    if (packageToUse != null) {
                        intent.setPackage(packageToUse)
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // 当不支持 text/plain 的 ACTION_SEND 时（例如 Instagram 纯文本），降级为直接打开 App 方便用户自行粘贴
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageToUse)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            } else {
                                startActivity(Intent.createChooser(intent, getString(R.string.share_to)))
                            }
                        }
                    } else {
                        startActivity(Intent.createChooser(intent, getString(R.string.share_to)))
                    }
                } else {
                    startActivity(Intent.createChooser(intent, getString(R.string.share_to)))
                }
            }
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadAndSaveImageToCache(imageUrl: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = if (imageUrl.startsWith("data:image")) {
                    val base64Data = imageUrl.substringAfter(",")
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else {
                    val url = URL(imageUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    BitmapFactory.decodeStream(connection.inputStream)
                } ?: return@withContext null

                val cachePath = File(cacheDir, "images")
                if (!cachePath.exists()) {
                    cachePath.mkdirs()
                } else {
                    cachePath.listFiles()?.forEach { it.delete() }
                }

                val file = File(cachePath, "share_image_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()

                androidx.core.content.FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun updateDivEvent(){
        firebaseAnalytics.logEvent("push_permission_status") {
            param("status", "1")
        }
    }

    private fun sendNotification(messageBody: String,title: String = "FCM Message") {
        val requestCode = 0
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("from_notification", true)
        intent.putExtra("NotificationTitle", title)
        intent.putExtra("NotificationBody", messageBody)
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val timestamp = System.currentTimeMillis()
        val randomNumber: Int = Random.nextInt(99999) // 生成一个0到99999之间的随机数
        val notificationId = (timestamp xor randomNumber.toLong()).toInt() // 使用XOR操作符混合时间戳和随机数

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        handleNotificationClick(intent)
    }

    private fun handleNotificationClick(intent: Intent){
        if (intent.getBooleanExtra("from_notification", false)) {
            // 清除标志防止重复处理
            getIntent().removeExtra("from_notification")
            val title = intent.getStringExtra("NotificationTitle")
            val content = intent.getStringExtra("NotificationBody")
            try {
                val jsonObj = JSONObject()
                jsonObj.put("NotificationTitle", title)
                jsonObj.put("NotificationContent", content)
                Log.w("WebViewTest",jsonObj.toString())
                sendJsNative("clickNotificationBar_callback", webView, jsonObj.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
