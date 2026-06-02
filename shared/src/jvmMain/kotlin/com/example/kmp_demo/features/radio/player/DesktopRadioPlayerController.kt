package com.example.kmp_demo.features.radio.player

import com.example.kmp_demo.core.PlatformLogger
import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.domain.player.IRadioPlayerController
import com.example.kmp_demo.features.radio.domain.player.MediaMetadataInfo
import com.example.kmp_demo.features.radio.domain.player.PlayableMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.factory.MediaPlayerFactory

/**
 * Desktop 电台播放器 — 基于 VLCJ
 *
 * VLCJ 是 libVLC 的 Java 绑定，原生支持各种音频格式（MP3/AAC/OGG/FLAC）和流媒体协议（HTTP/HTTPS/HLS）。
 * 需要用户安装 VLC: brew install vlc
 *
 * 优势：
 * - 原生支持所有音频格式，无需额外解码器
 * - 稳定的流媒体播放能力
 * - 内置缓冲管理
 * - 支持播放/暂停/停止
 */
class DesktopRadioPlayerController(
    private val mediaPlayerFactory: MediaPlayerFactory
) : IRadioPlayerController {

    companion object {
        private const val TAG = "DesktopRadioPlayerController"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentPlaylist = listOf<PlayableMedia>()
    private var currentIndex = -1
    private var mediaPlayer: MediaPlayer? = null
    private var isPaused = false

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

    init {
        PlatformLogger.d(TAG, "=== DesktopRadioPlayerController init ===")
        // 尝试发现本地 VLC 安装
        val discovery = NativeDiscovery()
        val found = discovery.discover()
        PlatformLogger.d(TAG, "NativeDiscovery: found=$found")
        if (found) {
            PlatformLogger.d(TAG, "VLC path: ${discovery.discoveredPath()}")
        } else {
            PlatformLogger.d(TAG, "VLC not found! Please install VLC: brew install vlc")
        }
    }

    override suspend fun setPlaylist(items: List<PlayableMedia>, startIndex: Int) {
        PlatformLogger.d(TAG, "=== setPlaylist: items.size=${items.size}, startIndex=$startIndex")
        currentPlaylist = items
        currentIndex = startIndex
        if (startIndex in items.indices) {
            loadAndPlay(items[startIndex])
        }
    }

    private suspend fun loadAndPlay(item: PlayableMedia) {
        PlatformLogger.d(TAG, "loadAndPlay: ${item.title}, uri=${item.uri.take(80)}")
        _currentMediaId.value = item.id
        _playbackState.value = AppPlaybackState.BUFFERING
        _isPlaying.value = false

        // 释放旧的播放器
        releaseMediaPlayer()
        isPaused = false

        val readyDeferred = CompletableDeferred<Result<Unit>>()

        withContext(Dispatchers.Default) {
            try {
                PlatformLogger.d(TAG, "Creating MediaPlayer...")
                val player = mediaPlayerFactory.mediaPlayers().newMediaPlayer()
                mediaPlayer = player

                // 设置事件监听
                player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                    override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                        PlatformLogger.d(TAG, "VLCJ: mediaPlayerReady")
                        _playbackState.value = AppPlaybackState.READY
                        _isPlaying.value = true
                        isPaused = false
                        if (!readyDeferred.isCompleted) {
                            readyDeferred.complete(Result.success(Unit))
                        }
                    }

                    override fun playing(mediaPlayer: MediaPlayer) {
                        PlatformLogger.d(TAG, "VLCJ: playing")
                        _playbackState.value = AppPlaybackState.READY
                        _isPlaying.value = true
                    }

                    override fun paused(mediaPlayer: MediaPlayer) {
                        PlatformLogger.d(TAG, "VLCJ: paused")
                        _isPlaying.value = false
                    }

                    override fun stopped(mediaPlayer: MediaPlayer) {
                        PlatformLogger.d(TAG, "VLCJ: stopped")
                        _isPlaying.value = false
                        _playbackState.value = AppPlaybackState.IDLE
                    }

                    override fun finished(mediaPlayer: MediaPlayer) {
                        PlatformLogger.d(TAG, "VLCJ: finished")
                        _playbackState.value = AppPlaybackState.ENDED
                        _isPlaying.value = false
                    }

                    override fun error(mediaPlayer: MediaPlayer) {
                        val msg = "VLCJ播放错误"
                        PlatformLogger.d(TAG, "VLCJ: error")
                        if (!readyDeferred.isCompleted) {
                            readyDeferred.complete(Result.failure(RuntimeException(msg)))
                        } else {
                            _errorEvents.tryEmit(msg)
                            _playbackState.value = AppPlaybackState.ERROR
                        }
                    }
                })

                PlatformLogger.d(TAG, "Starting playback: ${item.uri}")
                player.media().play(item.uri)
                PlatformLogger.d(TAG, "VLCJ play() called, waiting for ready...")
            } catch (e: Exception) {
                PlatformLogger.d(TAG, "Failed to create/start VLCJ: ${e.message}")
                if (!readyDeferred.isCompleted) {
                    readyDeferred.complete(Result.failure(e))
                }
            }
        }

        // 等待播放就绪（超时 30 秒）
        try {
            val result = withTimeout(30_000L) { readyDeferred.await() }
            result.fold(
            onSuccess = { PlatformLogger.d(TAG, "Playback started successfully") },
            onFailure = { error ->
                PlatformLogger.d(TAG, "Playback failed: ${error.message}")
                    _errorEvents.tryEmit("播放失败: ${error.message}")
                    _playbackState.value = AppPlaybackState.ERROR
                }
            )
        } catch (e: Exception) {
            PlatformLogger.d(TAG, "Timeout waiting for playback: ${e.message}")
            _errorEvents.tryEmit("播放启动超时")
            _playbackState.value = AppPlaybackState.ERROR
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
    }

    override suspend fun play() {
        PlatformLogger.d(TAG, "play()")
        isPaused = false
        try {
            mediaPlayer?.controls()?.play()
            _isPlaying.value = true
        } catch (e: Exception) {
            PlatformLogger.d(TAG, "play() error: ${e.message}")
        }
    }

    override suspend fun pause() {
        PlatformLogger.d(TAG, "pause()")
        isPaused = true
        try {
            mediaPlayer?.controls()?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            PlatformLogger.d(TAG, "pause() error: ${e.message}")
        }
    }

    override suspend fun stop() {
        PlatformLogger.d(TAG, "stop()")
        try {
            mediaPlayer?.controls()?.stop()
        } catch (_: Exception) { }
        _isPlaying.value = false
        _playbackState.value = AppPlaybackState.IDLE
    }

    override suspend fun skipToNext() {
        PlatformLogger.d(TAG, "skipToNext: currentIndex=$currentIndex, playlist.size=${currentPlaylist.size}")
        if (currentPlaylist.isNotEmpty() && currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            loadAndPlay(currentPlaylist[currentIndex])
        }
    }

    override suspend fun skipToPrevious() {
        PlatformLogger.d(TAG, "skipToPrevious: currentIndex=$currentIndex, playlist.size=${currentPlaylist.size}")
        if (currentPlaylist.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            loadAndPlay(currentPlaylist[currentIndex])
        }
    }

    override fun release() {
        PlatformLogger.d(TAG, "release()")
        releaseMediaPlayer()
        scope.cancel()
    }
}
