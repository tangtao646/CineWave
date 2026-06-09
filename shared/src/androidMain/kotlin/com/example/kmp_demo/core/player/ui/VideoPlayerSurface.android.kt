package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW

/**
 * Android 平台视频渲染 Surface。
 */
@Composable
fun VideoPlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,

    ) {

    // 1. 默认给 16:9（1.77f）占位，如果你的业务坚信全是横屏，直接写死它确实清爽
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // 2. 干净的生命周期监听：完美防御 9:16 竖屏和 21:9 电影
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener) // 顺手把解绑和清理也做了，防内存泄漏
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.aspectRatio(videoAspectRatio) // 动态卡死比例
        )
    }
}
