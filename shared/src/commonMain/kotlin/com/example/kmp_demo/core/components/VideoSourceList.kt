package com.example.kmp_demo.core.components

import androidx.compose.runtime.Composable
import com.example.kmp_demo.core.videosource.domain.VideoSource

/**
 * 平台相关的播放源列表布局。
 *
 * Android 端使用纵向列表（Column + forEach），
 * Desktop 端使用网格布局（FlowRow）。
 */
@Composable
expect fun VideoSourceListContent(
    videoSources: List<VideoSource>,
    isSniffing: Boolean,
    onPlayClick: (VideoSource) -> Unit,
)
