package com.example.kmp_demo.core.player.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * 视频播放状态枚举
 */
enum class VideoPlaybackState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

/**
 * 播放器核心抽象接口
 *
 * 定义与具体播放引擎无关的播放控制协议。
 * 任何平台特定的播放器实现（如 compose-media-player、ExoPlayer、AVPlayer 等）
 * 只需实现此接口即可被 UI 层使用，实现"易替换"的设计目标。
 */
interface IVideoPlayerController {

    /** 播放状态 */
    val playbackState: StateFlow<VideoPlaybackState>

    /** 当前进度（毫秒） */
    val currentPosition: StateFlow<Long>

    /** 总时长（毫秒） */
    val duration: StateFlow<Long>

    /** 音量 0.0 ~ 1.0 */
    val volume: StateFlow<Float>

    /** 是否处于全屏模式 */
    val isFullScreen: StateFlow<Boolean>

    /** 缓冲百分比 0~100 */
    val bufferedPercent: StateFlow<Int>

    /** 打开视频源 */
    suspend fun open(url: String, headers: Map<String, String>? = null)

    /** 播放/继续 */
    suspend fun play()

    /** 暂停 */
    suspend fun pause()

    /** 切换播放/暂停 */
    suspend fun togglePlayPause()

    /** 跳转到指定位置（毫秒） */
    suspend fun seekTo(positionMs: Long)

    /** 快进指定秒数 */
    suspend fun seekForward(seconds: Long = 10)

    /** 快退指定秒数 */
    suspend fun seekBackward(seconds: Long = 10)

    /** 设置音量 */
    suspend fun setVolume(volume: Float)

    /** 切换全屏 */
    suspend fun toggleFullScreen()

    /** 释放播放器资源 */
    fun release()
}
