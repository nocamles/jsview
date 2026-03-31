package com.ganha.test.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

object DeviceInfoHelper {

    /**
     * 1. 模拟器检测
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * 2. 代理、VPN检测
     */
    fun isVpnOrProxy(context: Context): Boolean {
        return isVpnUsed(context) || isProxyUsed()
    }

    private fun isVpnUsed(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (networkInterface in networkInterfaces) {
                    if (networkInterface.isUp && networkInterface.interfaceAddresses.isNotEmpty()) {
                        if (networkInterface.name == "tun0" || networkInterface.name == "ppp0") {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isProxyUsed(): Boolean {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        return !proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()
    }

    /**
     * 3. Root权限检测
     */
    fun isRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * 4. USB调试/开发者模式检测
     */
    fun isUsbDebuggingOrDevMode(context: Context): Boolean {
        val isDevModeEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0

        val isAdbEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) != 0

        return isDevModeEnabled || isAdbEnabled
    }

    /**
     * 5. SIM卡识别（是否有SIM卡）
     */
    fun hasSimCard(context: Context): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.simState == TelephonyManager.SIM_STATE_READY
    }

    /**
     * 5. SIM卡识别（运营商）
     */
    fun getSimOperator(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (telephonyManager.simState == TelephonyManager.SIM_STATE_READY) {
            telephonyManager.simOperatorName ?: ""
        } else {
            ""
        }
    }

    /**
     * 6. SIM卡识别（国家/地区代码）
     */
    fun getSimCountry(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (telephonyManager.simState == TelephonyManager.SIM_STATE_READY) {
            telephonyManager.simCountryIso ?: ""
        } else {
            ""
        }
    }

    /**
     * 7. 获取手机品牌
     */
    fun getDeviceBrand(): String = Build.BRAND ?: ""

    /**
     * 8. 获取手机型号
     */
    fun getDeviceModel(): String = Build.MODEL ?: ""

    /**
     * 9. 获取系统版本
     */
    fun getOsVersion(): String = Build.VERSION.RELEASE ?: ""

    /**
     * 10. 获取网络类型
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "NONE"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "NONE"
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "OTHER"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return "NONE"
            @Suppress("DEPRECATION")
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> "WIFI"
                ConnectivityManager.TYPE_MOBILE -> "CELLULAR"
                ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                ConnectivityManager.TYPE_VPN -> "VPN"
                else -> "OTHER"
            }
        }
    }
}
