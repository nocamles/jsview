package com.ganha.test.utils

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 针对高频加载的JS、CSS、背景图、金币图标及视频进行本地拦截和缓存。
 * 实现LRU缓存策略，最大空间限制为100MB。
 */
class WebViewLocalCache(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "webview_local_cache")
    private val maxCacheSize = 100 * 1024 * 1024L // 100MB

    private val cacheableExtensions = listOf(
        ".js", ".css", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico",
        ".mp4", ".webm", ".ogg", ".mp3", ".wav"
    )

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        checkLruCacheSize()
    }

    private fun getExtension(url: String): String {
        val path = Uri.parse(url).path ?: return ""
        val lastDot = path.lastIndexOf('.')
        if (lastDot != -1) {
            return path.substring(lastDot).lowercase()
        }
        return ""
    }

    fun shouldIntercept(url: String): Boolean {
        val ext = getExtension(url)
        val lowerUrl = url.lowercase()
        return cacheableExtensions.contains(ext) || lowerUrl.contains("video") || lowerUrl.contains("image")
    }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun interceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!shouldIntercept(url)) return null

        val method = request.method
        if (method != "GET") return null

        // 去除可能的哈希后缀，或者如果H5使用了带有版本号的URL(比如?v=2)作为更新机制，URL自带更新能力
        val cacheKey = md5(url)
        val file = File(cacheDir, cacheKey)

        try {
            // 命中本地缓存
            if (file.exists() && file.length() > 0) {
                file.setLastModified(System.currentTimeMillis()) // LRU 更新时间
                val mimeType = getMimeType(url)

                // 缓存失效和实时更新：通常H5前端更新会带有新的哈希版本号(如app.xxx.js)或者时间戳参数，
                // 由于我们的cacheKey基于完整的URL md5，当H5更新且URL改变时，会自然不命中缓存，
                // 从而重新走网络请求并替换缓存。100M LRU机制会保证旧版本的无用缓存被自动清理。

                val rangeHeader = request.requestHeaders?.entries?.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
                
                // 处理视频等多媒体Range请求
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val rangeParts = rangeHeader.substring(6).split("-")
                    val startStr = rangeParts.getOrNull(0)
                    val endStr = rangeParts.getOrNull(1)

                    var start = if (startStr.isNullOrEmpty()) 0L else startStr.toLong()
                    var end = if (endStr.isNullOrEmpty()) file.length() - 1 else endStr.toLong()

                    if (start > file.length() - 1) start = file.length() - 1
                    if (end > file.length() - 1) end = file.length() - 1

                    val contentLength = end - start + 1
                    val inputStream = FileInputStream(file)
                    inputStream.skip(start)

                    val response = WebResourceResponse(mimeType, "UTF-8", inputStream)
                    response.setStatusCodeAndReasonPhrase(206, "Partial Content")
                    val responseHeaders = mutableMapOf<String, String>()
                    responseHeaders["Content-Type"] = mimeType
                    responseHeaders["Content-Length"] = contentLength.toString()
                    responseHeaders["Content-Range"] = "bytes $start-$end/${file.length()}"
                    responseHeaders["Accept-Ranges"] = "bytes"
                    responseHeaders["Access-Control-Allow-Origin"] = "*"
                    response.responseHeaders = responseHeaders
                    return response
                } else {
                    val inputStream = FileInputStream(file)
                    val response = WebResourceResponse(mimeType, "UTF-8", inputStream)
                    val responseHeaders = mutableMapOf<String, String>()
                    responseHeaders["Content-Type"] = mimeType
                    responseHeaders["Content-Length"] = file.length().toString()
                    responseHeaders["Accept-Ranges"] = "bytes"
                    responseHeaders["Access-Control-Allow-Origin"] = "*"
                    responseHeaders["Cache-Control"] = "public, max-age=31536000" // 强制WebView缓存
                    response.responseHeaders = responseHeaders
                    return response
                }
            }

            // 本地无缓存，走网络并写入缓存 (实现边下边播/边缓存)
            return fetchAndCache(request, file)

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun fetchAndCache(request: WebResourceRequest, cacheFile: File): WebResourceResponse? {
        val urlString = request.url.toString()
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            request.requestHeaders?.forEach { (k, v) ->
                connection.setRequestProperty(k, v)
            }

            val responseCode = connection.responseCode
            val mimeType = connection.contentType?.substringBefore(";") ?: getMimeType(urlString)
            val encoding = connection.contentEncoding ?: "UTF-8"

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val tempFile = File(cacheDir, "${cacheFile.name}.tmp")
                val outputStream = FileOutputStream(tempFile)

                val cachingInputStream = CachingInputStream(connection.inputStream, outputStream) {
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.renameTo(cacheFile)
                        checkLruCacheSize()
                    }
                }

                val response = WebResourceResponse(mimeType, encoding, cachingInputStream)
                val responseHeaders = mutableMapOf<String, String>()
                connection.headerFields.forEach { (key, values) ->
                    if (key != null && values.isNotEmpty()) {
                        responseHeaders[key] = values.joinToString(",")
                    }
                }
                responseHeaders["Access-Control-Allow-Origin"] = "*"
                response.responseHeaders = responseHeaders
                return response
            } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // 分片请求直接放行，通常等200完整请求时再缓存
                val response = WebResourceResponse(mimeType, encoding, connection.inputStream)
                response.setStatusCodeAndReasonPhrase(206, "Partial Content")
                val responseHeaders = mutableMapOf<String, String>()
                connection.headerFields.forEach { (key, values) ->
                    if (key != null && values.isNotEmpty()) {
                        responseHeaders[key] = values.joinToString(",")
                    }
                }
                responseHeaders["Access-Control-Allow-Origin"] = "*"
                response.responseHeaders = responseHeaders
                return response
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getMimeType(url: String): String {
        val ext = getExtension(url)
        return when (ext) {
            ".js" -> "application/javascript"
            ".css" -> "text/css"
            ".png" -> "image/png"
            ".jpg", ".jpeg" -> "image/jpeg"
            ".webp" -> "image/webp"
            ".gif" -> "image/gif"
            ".svg" -> "image/svg+xml"
            ".ico" -> "image/x-icon"
            ".mp4" -> "video/mp4"
            ".webm" -> "video/webm"
            ".ogg", ".oga" -> "audio/ogg"
            ".mp3" -> "audio/mpeg"
            ".wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    private fun checkLruCacheSize() {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize <= maxCacheSize) return

            val sortedFiles = files.sortedBy { it.lastModified() }

            for (file in sortedFiles) {
                if (file.name.endsWith("tmp")) continue
                totalSize -= file.length()
                file.delete()
                if (totalSize <= maxCacheSize) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun md5(string: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(string.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            string.hashCode().toString()
        }
    }

    private class CachingInputStream(
        private val source: InputStream,
        private val cacheOutputStream: OutputStream,
        private val onCacheComplete: () -> Unit
    ) : InputStream() {
        private var isClosed = false

        override fun read(): Int {
            val b = try { source.read() } catch (e: Exception) { -1 }
            if (b != -1) {
                try { cacheOutputStream.write(b) } catch (e: Exception) {}
            } else {
                finishCache()
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = try { source.read(b, off, len) } catch (e: Exception) { -1 }
            if (count != -1) {
                try { cacheOutputStream.write(b, off, count) } catch (e: Exception) {}
            } else {
                finishCache()
            }
            return count
        }

        private fun finishCache() {
            if (!isClosed) {
                isClosed = true
                try { cacheOutputStream.flush() } catch (e: Exception) {}
                try { cacheOutputStream.close() } catch (e: Exception) {}
                onCacheComplete()
            }
        }

        override fun close() {
            try { source.close() } catch (e: Exception) {}
            finishCache()
        }
    }
}
