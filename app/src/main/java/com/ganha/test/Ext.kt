package com.ganha.test

import android.content.Context
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.ganha.test.utils.MyCustomTipsDialog

/**
 *@auth: Hank
 *邮箱: cs16xiaoc1@163.com
 *创建时间: 2026/3/19 10:20
 *描述:扩展函数
 */

fun ComponentActivity.setStatusBarTextColor(isLightBackground: Boolean) {
    println("statusbar_color = $isLightBackground")
    if (isLightBackground) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
    }else{
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            )
        )
    }

    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = isLightBackground
}

/**
 * 弹出提示对话框
 */
fun Context.showTipsDialog(
    title: String,
    content: String,
    cancelText: String = getString(R.string.cancel),
    confirmText: String = getString(R.string.confirm),
    onCancelListener: (() -> Unit)? = null,
    onConfirmListener: (() -> Unit)? = null
): MyCustomTipsDialog {
    val dialog = MyCustomTipsDialog(
        context = this,
        title = title,
        content = content,
        cancelText = cancelText,
        confirmText = confirmText,
        onCancelListener = onCancelListener,
        onConfirmListener = onConfirmListener
    )
    dialog.show()
    return dialog
}
