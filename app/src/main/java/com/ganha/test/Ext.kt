package com.ganha.test

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ganha.test.bean.StatusBarBean
import com.ganha.test.utils.MyCustomTipsDialog
import java.util.Locale

/**
 *@auth: Hank
 *邮箱: cs16xiaoc1@163.com
 *创建时间: 2026/3/19 10:20
 *描述:扩展函数
 */

fun ComponentActivity.setStatusBarTextColor(statusBarBean: StatusBarBean) {
    val isLightBackground = statusBarBean.isLightModel
    val color = statusBarBean.color

    // 1. 切换状态栏图标和文字的颜色（深色/浅色）
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = isLightBackground

    // 2. 动态设置状态栏背景色 (应对 enableEdgeToEdge 和 Android 15 的强制透明)
    val contentView = findViewById<ViewGroup>(android.R.id.content)
    val tag = "custom_status_bar_bg_view"
    var statusBarBgView = contentView.findViewWithTag<View>(tag)

    if (statusBarBgView == null) {
        // 如果还没有注入过，就动态创建一个 View 作为状态栏背景
        
        // 尝试直接获取当前的状态栏高度
        var initialHeight = 0
        ViewCompat.getRootWindowInsets(window.decorView)?.let { insets ->
            initialHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        }
        
        statusBarBgView = View(this).apply {
            this.tag = tag
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                initialHeight // 初始高度，如果有获取到则直接设置
            ).apply {
                gravity = Gravity.TOP // 悬浮在最顶部
            }
            contentView.addView(this)
        }

        // 监听系统窗口 Insets 变化，动态获取真实的状态栏高度
        ViewCompat.setOnApplyWindowInsetsListener(statusBarBgView) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val lp = view.layoutParams
            if (lp.height != statusBarHeight) {
                lp.height = statusBarHeight
                view.layoutParams = lp
            }
            insets // 返回 insets，不拦截传递
        }
        
        // 动态添加View后请求分发Insets，以便初始更新高度
        ViewCompat.requestApplyInsets(statusBarBgView)
    }

    // 3. 为这个占位 View 设置你想要的颜色
    statusBarBgView.setBackgroundColor(Color.parseColor(color))
}

fun Int.toH5Value(context: Context): String {
    val density = context.resources.displayMetrics.density
    val result = this.toFloat() / density

    return String.format(Locale.US, "%.1f", result)
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
