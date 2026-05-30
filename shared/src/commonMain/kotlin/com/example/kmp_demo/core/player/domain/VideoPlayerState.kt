package com.example.kmp_demo.core.player.domain

import com.example.kmp_demo.core.player.cache.SegmentInfo

/**
 * 视频播放器 UI 状态聚合模型
 *
 * 单一事实来源 (Single Source of Truth)，UI 层只需订阅此 StateFlow。
 * 所有播放器相关的 UI 状态都聚合在此，避免 UI 层直接订阅多个 StateFlow。
 */
data class VideoPlayerUiState(
    val playbackState: VideoPlaybackState = VideoPlaybackState.IDLE,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercent: Int = 0,
    val volume: Float = 1.0f,
    val isFullScreen: Boolean = false,
    val isControlsVisible: Boolean = true,
    /** 详细的错误信息（当 [playbackState] 为 ERROR 时非空） */
    val playerError: PlayerError? = null,
    /** 切片缓存状态列表，用于在 SeekBar 上标记已缓存/未缓存区域 */
    val cachedSegments: List<SegmentInfo> = emptyList(),
) {
    /** 播放进度百分比 0f ~ 1f */
    val progressFraction: Float
        get() = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    /** 格式化后的当前进度 mm:ss */
    val currentPositionText: String
        get() = formatDuration(currentPosition)

    /** 格式化后的总时长 mm:ss */
    val durationText: String
        get() = formatDuration(duration)

    /** 是否正在播放 */
    val isPlaying: Boolean
        get() = playbackState == VideoPlaybackState.PLAYING

    /** 是否处于缓冲中 */
    val isBuffering: Boolean
        get() = playbackState == VideoPlaybackState.BUFFERING

    /** 是否处于错误状态 */
    val isError: Boolean
        get() = playbackState == VideoPlaybackState.ERROR

    /** 错误消息（兼容旧版，从 [playerError] 派生） */
    @Deprecated("Use playerError instead", ReplaceWith("playerError?.message"))
    val error: String?
        get() = playerError?.message

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = (totalSeconds / 60) % 60
            val seconds = totalSeconds % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                "${hours}:${pad2(minutes)}:${pad2(seconds)}"
            } else {
                "${pad2(minutes)}:${pad2(seconds)}"
            }
        }

        private fun pad2(value: Long): String {
            return if (value < 10) "0${value}" else "$value"
        }
    }
}
