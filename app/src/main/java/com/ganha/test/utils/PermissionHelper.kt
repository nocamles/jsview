package com.ganha.test.utils

import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import androidx.appcompat.app.AlertDialog
import com.hjq.permissions.permission.base.IPermission
import com.ganha.test.showTipsDialog
import com.ganha.test.R

/**
 * 权限请求结果回调
 */
interface RequestCallback {
    fun onGranted()
    fun onDenied()
}

/**
 * 权限请求助手
 */
object PermissionHelper {

    /**
     * 检查并请求权限
     *
     * @param activity               当前的 AppCompatActivity
     * @param permissions            需要请求的权限列表 (如 listOf(Permission.CAMERA))
     * @param explainReason          权限被常规拒绝时的解释说明（提示权限作用）
     * @param forwardtoSettingReason 权限被永久拒绝时，引导去设置页的说明
     * @param callBack               权限请求结果回调
     */
    fun checkPermission(
        activity: AppCompatActivity,
        permissions: List<IPermission>,
        explainReason: String,
        forwardtoSettingReason: String,
        callBack: RequestCallback
    ) {
        // 防止 Activity 销毁时操作引发异常
        if (activity.isFinishing || activity.isDestroyed) return

        // 1. 如果已经拥有所有的权限，直接回调成功并中断
        if (XXPermissions.isGrantedPermissions(activity, permissions)) {
            callBack.onGranted()
            return
        }

        // 2. 弹窗提示用户权限说明，确认后发起权限请求
        activity.showTipsDialog(
            title = activity.getString(R.string.permission_request),
            content = explainReason,
            cancelText = activity.getString(R.string.cancel),
            confirmText = activity.getString(R.string.go_to_authorize),
            onCancelListener = {
                // 用户拒绝弹窗，回调失败
                callBack.onDenied()
            },
            onConfirmListener = {
                // 发起权限请求
                XXPermissions.with(activity)
                    .permissions(permissions)
                    .request(object : OnPermissionCallback {
                        override fun onResult(
                            grantedList: List<IPermission?>,
                            deniedList: List<IPermission?>
                        ) {
                            if (deniedList.isEmpty()) {
                                callBack.onGranted()
                            } else {
                                val doNotAskAgain =
                                    XXPermissions.isDoNotAskAgainPermissions(activity, deniedList)
                                if (doNotAskAgain) {
                                    showSettingDialog(
                                        activity,
                                        deniedList,
                                        forwardtoSettingReason,
                                        callBack
                                    )
                                } else {
                                    showExplainDialog(
                                        activity,
                                        permissions,
                                        explainReason,
                                        forwardtoSettingReason,
                                        callBack
                                    )
                                }
                            }
                        }
                    })
            }
        )
    }

    /**
     * 解释权限弹窗 (用户普通拒绝时触发)
     */
    private fun showExplainDialog(
        activity: AppCompatActivity,
        permissions: List<IPermission>,
        explainReason: String,
        forwardtoSettingReason: String,
        callBack: RequestCallback
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_tips)
            .setMessage(explainReason)
            .setCancelable(false)
            .setPositiveButton(R.string.reauthorize) { dialog, _ ->
                dialog.dismiss()
                checkPermission(
                    activity,
                    permissions,
                    explainReason,
                    forwardtoSettingReason,
                    callBack
                )
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                callBack.onDenied()
            }
            .show()
    }

    /**
     * 引导去设置页弹窗 (用户永久拒绝时触发)
     */
    private fun showSettingDialog(
        activity: AppCompatActivity,
        permissions: List<IPermission?>,
        forwardtoSettingReason: String,
        callBack: RequestCallback
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_tips)
            .setMessage(forwardtoSettingReason)
            .setCancelable(false)
            .setPositiveButton(R.string.go_to_settings) { dialog, _ ->
                dialog.dismiss()
                XXPermissions.startPermissionActivity(activity, permissions)
                callBack.onDenied()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                callBack.onDenied()
            }
            .show()
    }
}