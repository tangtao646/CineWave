package com.example.kmp_demo.core.player.domain

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AndroidFullscreenController(private val context: Context) : FullscreenController {

    private val activity: Activity?
        get() = context as? Activity // 或者通过 ContextWrapper 递归查找

    override fun enterFullscreen() {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)

            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 隐藏状态栏和导航栏
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun exitFullscreen() {
        activity?.let { act ->
            // 1. 恢复竖屏
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            // 2. 恢复显示系统栏
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}