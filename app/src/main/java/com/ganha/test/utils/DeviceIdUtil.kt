package com.ganha.test.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaDrm
import android.media.UnsupportedSchemeException
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID


object DeviceIdUtil {

    private const val TAG = "DeviceIdUtil"
    
    // WIDEVINE UUID
    private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    /**
     * 获取唯一设备标识 (GAID + Android ID + Widevine ID 进行 MD5 计算)
     * 该方法涉及网络/跨进程调用，必须在协程或后台线程中调用。
     */
    suspend fun getUniqueDeviceId(context: Context): String = withContext(Dispatchers.IO) {
        val gaid = getGaid(context) ?: "null_gaid"
        val androidId = getAndroidId(context) ?: "null_android_id"
        val widevineId = getWidevineId() ?: "null_widevine_id"
        
        val rawId = "${gaid}_${androidId}_${widevineId}"
        Log.d(TAG, "Raw Device IDs: GAID=$gaid, AndroidID=$androidId, WidevineID=$widevineId")
        
        return@withContext md5(rawId)
    }

    /**
     * 获取 Google Advertising ID (GAID)
     * 需在子线程运行
     */
    fun getGaid(context: Context): String? {
        return try {
            val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext)
            adInfo.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get GAID", e)
            null
        }
    }

    /**
     * 获取 Android ID
     */
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Android ID", e)
            null
        }
    }

    /**
     * 获取 Widevine ID
     * (硬件级别ID，极难被篡改)
     */
    private fun getWidevineId(): String? {
        var mediaDrm: MediaDrm? = null
        return try {
            mediaDrm = MediaDrm(WIDEVINE_UUID)
            val widevineId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            val sb = java.lang.StringBuilder()
            for (b in widevineId) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: UnsupportedSchemeException) {
            Log.e(TAG, "Widevine scheme not supported", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Widevine ID", e)
            null
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                mediaDrm?.close()
            } else {
                @Suppress("DEPRECATION")
                mediaDrm?.release()
            }
        }
    }

    /**
     * MD5 哈希
     */
    private fun md5(string: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(string.toByteArray())
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "MD5 calculation failed", e)
            string.hashCode().toString()
        }
    }

    /**
     * 获取应用版本代码(递增的整数值)
     * @param context 上下文对象
     * @return 版本代码，获取失败返回-1
     */
    fun getVersionCode(context: Context): Int {
        try {
            val packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0)
            return packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取版本代码失败", e)
            return -1
        }
    }
}
