package com.example.kmp_demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.example.kmp_demo.core.data.local.room.AndroidAppContext
import com.example.kmp_demo.core.player.domain.AndroidFullscreenController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.di.androidAppModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val fullscreenController = AndroidFullscreenController(this)
        // 1. 初始化 Android 上下文，供 Koin 注入及 Room 使用
        AndroidAppContext.init(this)

        setContent {
            CompositionLocalProvider(LocalFullscreenController provides fullscreenController) {
                App()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
