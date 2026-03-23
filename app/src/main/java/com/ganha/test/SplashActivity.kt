package com.ganha.test

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 设置延迟 1.5 秒跳转到 MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MessageActivity::class.java))
            finish()
        }, 1500)
    }
}