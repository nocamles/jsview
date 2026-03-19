package com.ganha.test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ganha.test.bean.JsBean
import com.ganha.test.bean.JsBean.Companion.js_openUrlExternally
import com.ganha.test.bean.JsBean.Companion.js_refresh
import com.ganha.test.bean.JsBean.Companion.js_removeSplashScreen
import com.ganha.test.bean.JsBean.Companion.js_vibrate
import com.ganha.test.bean.JsBean.Companion.sendJsNative
import com.ganha.test.bean.JsBeanRequest
import com.ganha.test.bean.JsExterUrlBean
import com.ganha.test.bean.VibrateBean
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context.VIBRATOR_SERVICE
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
import com.ganha.test.utils.PermissionHelper
import com.ganha.test.utils.RequestCallback
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.dangerous.WriteExternalStoragePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Base64
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.media.MediaScannerConnection
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import android.webkit.ValueCallback
import android.app.Activity
import com.ganha.test.bean.JsBean.Companion.js_deviceInfo
import com.ganha.test.bean.JsBean.Companion.js_saveImageToGallery
import com.ganha.test.utils.DeviceIdUtil
import com.ganha.test.utils.DeviceInfoHelper

class MainActivity : AppCompatActivity() {

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
                if (fileSize <= 800 * 1024) {
                    return@withContext uri
                }

                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) return@withContext uri

                var quality = 90
                var outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

                while (outputStream.toByteArray().size > 800 * 1024 && quality > 10) {
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

    private var backPressedTime = 0L
    private var failedUrl: String? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private val viewModel: MainViewModel by viewModels()

    private var progressBar: ProgressBar? = null
    private var tvProgress: TextView? = null
    private var tvFileName: TextView? = null

    private var isDownloading = false
    private var customDialog: Dialog? = null

    private var statusBarHeight: Int = 0
    private var navBarHeight: Int = 0

    private val MIN_DISPLAY_TIME = 1500L


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

                if (splashView.isVisible) {
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

                    js_openUrlExternally -> {
                        var jsExterUrlBean =
                            Gson().fromJson(jsMessage.paramObj, JsExterUrlBean::class.java)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(jsExterUrlBean.url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    js_vibrate -> {
                        try {
                            val vibrateBean = Gson().fromJson(jsMessage.paramObj, VibrateBean::class.java) ?: VibrateBean()
                            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                            if (vibrator.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val amplitude = if (vibrateBean.amplitude in 1..255) vibrateBean.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                                    vibrator.vibrate(VibrationEffect.createOneShot(vibrateBean.duration, amplitude))
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
                        var statusBarBean =
                            Gson().fromJson(jsMessage.paramObj, StatusBarBean::class.java)
                        setStatusBarTextColor(statusBarBean.isLight)
                    }

                    js_saveImageToGallery -> {
                        try {
                            var jsExterUrlBean =
                                Gson().fromJson(jsMessage.paramObj, JsExterUrlBean::class.java)
                            runOnUiThread {
                                checkPermissionAndSaveImage(jsExterUrlBean.url)
                            }
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

                            val appInfo = com.ganha.test.bean.AppinfoBean(
                                isEmulator = isEmulator,
                                isVpnOrProxy = isVpnOrProxy,
                                isRooted = isRooted,
                                isUsbDebuggingOrDevMode = isUsbDebuggingOrDevMode,
                                hasSimCard = hasSimCard,
                                simOperator = simOperator,
                                deviceId = deviceId
                            )
                            val jsonStr = Gson().toJson(appInfo)
                            withContext(Dispatchers.Main) {
                                sendJsNative(jsMessage.callback, webView, jsonStr)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startDownloadTask(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        showCustomDialog(fileName) {}

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

    private fun showCustomDialog(fileName: String, onCancel: () -> Unit) {
        // 使用 Dialog 而不是 Builder
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_download_custom, null)

        dialog.setContentView(view)
        dialog.setCancelable(false) // 禁止点击外部关闭

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        tvFileName = view.findViewById(R.id.tvFileName)
        progressBar = view.findViewById(R.id.progressBar)
        tvProgress = view.findViewById(R.id.tvProgress)

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

    private fun showSaveImageDialog(imageUrl: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.tips)
            .setMessage(R.string.save_image_to_gallery_prompt)
            .setPositiveButton(R.string.save) { _, _ ->
                checkPermissionAndSaveImage(imageUrl)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
}
