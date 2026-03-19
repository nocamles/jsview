package com.ganha.test.utils

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.ganha.test.R

class MyCustomTipsDialog(
    context: Context,
    private val title: String,
    private val content: String,
    private val cancelText: String,
    private val confirmText: String,
    private val onCancelListener: (() -> Unit)? = null,
    private val onConfirmListener: (() -> Unit)? = null
) : Dialog(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置背景透明，以便显示圆角
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_custom_tips)

        // 设置居中显示和宽度
        window?.let { win ->
            val layoutParams = win.attributes
            layoutParams.gravity = Gravity.CENTER
            layoutParams.width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            win.attributes = layoutParams
        }

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvContent = findViewById<TextView>(R.id.tvContent)
        val btnCancel = findViewById<TextView>(R.id.btnCancel)
        val btnConfirm = findViewById<TextView>(R.id.btnConfirm)

        tvTitle.text = title
        tvContent.text = content
        btnCancel.text = cancelText
        btnConfirm.text = confirmText

        btnCancel.setOnClickListener {
            dismiss()
            onCancelListener?.invoke()
        }

        btnConfirm.setOnClickListener {
            dismiss()
            onConfirmListener?.invoke()
        }
    }
}