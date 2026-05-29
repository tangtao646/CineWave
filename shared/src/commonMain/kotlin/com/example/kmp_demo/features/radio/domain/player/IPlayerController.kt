package com.example.kmp_demo.features.radio.domain.player

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppPlaybackState {
    IDLE, BUFFERING, READY, ENDED, ERROR
}

data class PlayableMedia(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String = "",
    val artworkUri: String = ""
)


/**
 * 实时元数据，如电台当前播放的歌名
 */
data class MediaMetadataInfo(
    val title: String? = null,
    val artist: String? = null
)

interface IRadioPlayerController {
    val isPlaying: StateFlow<Boolean>
    val currentMediaId: StateFlow<String?>
    val playbackState: StateFlow<AppPlaybackState>
    val metadataInfo: StateFlow<MediaMetadataInfo>
    val errorEvents: SharedFlow<String> // 新增：错误事件流

    suspend fun setPlaylist(items: List<PlayableMedia>, startIndex: Int)
    suspend fun play()
    suspend fun pause()
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun stop()
    fun release()
}
