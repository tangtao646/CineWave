package com.example.kmp_demo.features.radio.player

import com.example.kmp_demo.features.radio.domain.model.RadioStation
import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.domain.player.IRadioPlayerController
import com.example.kmp_demo.features.radio.domain.player.MediaMetadataInfo
import com.example.kmp_demo.features.radio.domain.player.PlayableMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 播放器 UI 状态聚合模型
 */
data class PlayerUiState(
    val currentStation: RadioStation? = null,
    val isPlaying: Boolean = false,
    val playbackState: AppPlaybackState = AppPlaybackState.IDLE,
    val trackTitle: String? = null,
    val sleepTimerRemaining: Int? = null
)

/**
 * 业务编排层 (Orchestrator)
 */
class RadioPlayerManager(
    private val playerController: IRadioPlayerController
) {
    // 使用应用级别的作用域，避免在单例中 cancel 后导致无法使用
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val playlistInternal = MutableStateFlow<List<RadioStation>>(emptyList())
    private val sleepTimerRemaining = MutableStateFlow<Int?>(null)
    private var sleepTimerJob: Job? = null

    /**
     * 单一事实来源 (SSOT)
     */
    val uiState: StateFlow<PlayerUiState> = combine(
        playerController.currentMediaId,
        playerController.isPlaying,
        playerController.playbackState,
        playerController.metadataInfo,
        playlistInternal,
        sleepTimerRemaining
    ) { args: Array<*> ->
        val mediaId = args[0] as String?
        val isPlaying = args[1] as Boolean
        val playbackState = args[2] as AppPlaybackState
        val metadata = args[3] as MediaMetadataInfo
        val stations = args[4] as List<RadioStation>
        val timer = args[5] as Int?

        val station = stations.find { it.uuid == mediaId }
        PlayerUiState(
            currentStation = station,
            isPlaying = isPlaying,
            playbackState = playbackState,
            trackTitle = metadata.title ?: station?.name,
            sleepTimerRemaining = timer
        )
    }.stateIn(
        scope = managerScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    // 暴露底层的错误事件
    val errorEvents: SharedFlow<String> = playerController.errorEvents

    fun playFromList(stations: List<RadioStation>, startIndex: Int) {
        managerScope.launch {
            playlistInternal.value = stations
            val playableItems = stations.map { it.toPlayable() }
            try {
                playerController.setPlaylist(playableItems, startIndex)
                playerController.play()
            } catch (e: Exception) {
                // 处理可能的初始化超时或其它异常
            }
        }
    }

    fun togglePlayPause() {
        managerScope.launch {
            try {
                if (uiState.value.isPlaying) playerController.pause() else playerController.play()
            } catch (e: Exception) {}
        }
    }

    fun playNext() = managerScope.launch { 
        try { playerController.skipToNext() } catch (e: Exception) {}
    }
    
    fun playPrevious() = managerScope.launch { 
        try { playerController.skipToPrevious() } catch (e: Exception) {}
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepTimerRemaining.value = null
            return
        }

        sleepTimerRemaining.value = minutes
        sleepTimerJob = managerScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60000)
                remaining--
                sleepTimerRemaining.value = remaining
            }
            playerController.pause()
            sleepTimerRemaining.value = null
        }
    }

    private fun RadioStation.toPlayable() = PlayableMedia(
        id = uuid,
        uri = streamUrl,
        title = name,
        subtitle = tags.take(3).joinToString(" · "),
        artworkUri = favicon
    )

    fun release() {
        // 单例不建议 cancel scope，除非是 App 销毁流程
        playerController.release()
    }
}
