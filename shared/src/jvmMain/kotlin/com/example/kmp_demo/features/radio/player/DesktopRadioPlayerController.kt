package com.example.kmp_demo.features.radio.player

import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.domain.player.IPlayerController
import com.example.kmp_demo.features.radio.domain.player.MediaMetadataInfo
import com.example.kmp_demo.features.radio.domain.player.PlayableMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URI
import javax.sound.sampled.*

/**
 * Desktop 电台播放器 — 基于 javax.sound 实现
 *
 * 使用 Java 内置的音频系统播放 MP3/AAC 流媒体。
 * 注意：javax.sound 原生不支持 MP3 解码，需要额外安装 MP3SPI 或使用其他方案。
 * 当前实现使用 AudioSystem 播放 PCM/WAV/AU 格式，对于 MP3 流需要添加
 * 第三方 SPI（如 mp3spi、jlayer）或使用 JavaFX MediaPlayer。
 *
 * 替代方案：
 * - 使用 JavaFX MediaPlayer（需添加 javafx-media 依赖）
 * - 使用 VLCJ（需安装 VLC）
 * - 使用 https://github.com/dheid/jlayer 纯 Java MP3 解码
 */
class DesktopRadioPlayerController : IPlayerController {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentPlaylist = listOf<PlayableMedia>()
    private var currentIndex = -1
    private var clip: Clip? = null
    private var audioStream: AudioInputStream? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    override val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _playbackState = MutableStateFlow(AppPlaybackState.IDLE)
    override val playbackState: StateFlow<AppPlaybackState> = _playbackState.asStateFlow()

    private val _metadataInfo = MutableStateFlow(MediaMetadataInfo())
    override val metadataInfo: StateFlow<MediaMetadataInfo> = _metadataInfo.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    override suspend fun setPlaylist(items: List<PlayableMedia>, startIndex: Int) {
        currentPlaylist = items
        currentIndex = startIndex
        if (startIndex in items.indices) {
            loadAndPlay(items[startIndex])
        }
    }

    private fun loadAndPlay(item: PlayableMedia) {
        releaseCurrentPlayer()
        _currentMediaId.value = item.id
        _playbackState.value = AppPlaybackState.BUFFERING

        try {
            val url = URI(item.uri).toURL()
            audioStream = AudioSystem.getAudioInputStream(url)
            val format = audioStream!!.format

            // 如果音频格式需要转换（如 MP3），使用 AudioSystem.getAudioInputStream 转换
            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.sampleRate,
                16,
                format.channels,
                format.channels * 2,
                format.sampleRate,
                false
            )
            val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream)

            clip = AudioSystem.getClip().apply {
                open(decodedStream)
                addLineListener { event ->
                    when (event.type) {
                        LineEvent.Type.START -> {
                            _isPlaying.value = true
                            _playbackState.value = AppPlaybackState.READY
                        }
                        LineEvent.Type.STOP -> {
                            _isPlaying.value = false
                        }
                        else -> {}
                    }
                }
            }

            _playbackState.value = AppPlaybackState.READY
            clip?.start()
        } catch (e: UnsupportedAudioFileException) {
            _errorEvents.tryEmit("不支持的音频格式: ${item.uri}")
            _playbackState.value = AppPlaybackState.ERROR
        } catch (e: Exception) {
            _errorEvents.tryEmit("加载媒体失败: ${e.message}")
            _playbackState.value = AppPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        clip?.start()
    }

    override suspend fun pause() {
        clip?.stop()
    }

    override suspend fun skipToNext() {
        if (currentPlaylist.isNotEmpty() && currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            loadAndPlay(currentPlaylist[currentIndex])
        }
    }

    override suspend fun skipToPrevious() {
        if (currentPlaylist.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            loadAndPlay(currentPlaylist[currentIndex])
        }
    }

    override suspend fun stop() {
        clip?.stop()
        clip?.framePosition = 0
        _isPlaying.value = false
        _playbackState.value = AppPlaybackState.IDLE
    }

    override fun release() {
        releaseCurrentPlayer()
        scope.cancel()
    }

    private fun releaseCurrentPlayer() {
        clip?.close()
        clip = null
        audioStream?.close()
        audioStream = null
    }
}
