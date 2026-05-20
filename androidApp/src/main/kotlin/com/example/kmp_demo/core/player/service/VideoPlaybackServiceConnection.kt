package com.example.kmp_demo.core.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 通过 MediaController 连接到 [VideoPlaybackService] 的适配器。
 *
 * 实现 [IPlayerController] 接口，使得 UI 层可以通过统一的接口控制
 * Service 中的 ExoPlayer，实现后台播放。
 *
 * 参考 [Media3PlayerController] 的相同模式。
 */
class VideoPlaybackServiceConnection(
    private val context: Context
) : IPlayerController {

    private val tag = "VideoSvcConnection"
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerDeferred = CompletableDeferred<MediaController>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 暴露 MediaController 作为 Player，供 VideoPlayerSurface 绑定 */
    val player: Player?
        get() = try {
            if (controllerDeferred.isCompleted && !controllerDeferred.isCancelled) {
                controllerDeferred.getCompleted()
            } else null
        } catch (_: Exception) {
            null
        }

    private val _playbackState = MutableStateFlow(VideoPlaybackState.IDLE)
    override val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _bufferedPercent = MutableStateFlow(0)
    override val bufferedPercent: StateFlow<Int> = _bufferedPercent.asStateFlow()

    private var positionPollingJob: kotlinx.coroutines.Job? = null

    init {
        connectToService()
    }

    private fun connectToService() {
        synchronized(this) {
            if (controllerDeferred.isCompleted && !controllerDeferred.isCancelled
                && controllerDeferred.getCompletionExceptionOrNull() == null
            ) {
                return
            }
            if (controllerDeferred.isCompleted) {
                controllerDeferred = CompletableDeferred()
            }
        }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val sessionToken = SessionToken(
            context,
            ComponentName(context, VideoPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                if (controller != null) {
                    setupController(controller)
                    if (!controllerDeferred.isCompleted) {
                        controllerDeferred.complete(controller)
                    }
                    Log.d(tag, "MediaController Connected")
                    startPositionPolling(controller)
                }
            } catch (e: Exception) {
                Log.e(tag, "MediaController Connection Failed: ${e.message}")
                if (!controllerDeferred.isCompleted) {
                    controllerDeferred.completeExceptionally(e)
                }
            }
        }, mainExecutor)
    }

    private fun setupController(controller: MediaController) {
        // 同步初始状态
        _playbackState.value = mapPlaybackState(controller.playbackState)
        _duration.value = controller.duration.coerceAtLeast(0)

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playbackState.value = VideoPlaybackState.PLAYING
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = mapPlaybackState(state)
                Log.d(tag, "State Changed: $state")
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                // 音量变化等
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(tag, "Player Error: ${error.message}, Code: ${error.errorCode}")
                _playbackState.value = VideoPlaybackState.ERROR
            }
        })
    }

    private fun startPositionPolling(controller: MediaController) {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                try {
                    _currentPosition.value = controller.currentPosition
                    val dur = controller.duration.coerceAtLeast(0)
                    if (dur > 0) {
                        _duration.value = dur
                        val buffered = controller.bufferedPosition
                        _bufferedPercent.value =
                            ((buffered.toFloat() / dur) * 100).toInt().coerceIn(0, 100)
                    }
                } catch (_: Exception) {
                    // 播放器尚未就绪
                }
                delay(250)
            }
        }
    }

    private suspend fun getController(): MediaController {
        if (controllerDeferred.isCompleted && controllerDeferred.getCompletionExceptionOrNull() != null) {
            connectToService()
        }
        return withTimeout(10000) { controllerDeferred.await() }
    }

    override suspend fun open(url: String, headers: Map<String, String>?) {
        _playbackState.value = VideoPlaybackState.BUFFERING
        try {
            val controller = getController()
            val mediaItem = MediaItem.Builder()
                .setMediaId(url)
                .setUri(Uri.parse(url))
                .build()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to open URL: ${e.message}")
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        try {
            val controller = getController()
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to play: ${e.message}")
        }
    }

    override suspend fun pause() {
        try {
            getController().pause()
        } catch (e: Exception) {
            Log.e(tag, "Failed to pause: ${e.message}")
        }
    }

    override suspend fun togglePlayPause() {
        try {
            val controller = getController()
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle play/pause: ${e.message}")
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            getController().seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(tag, "Failed to seek: ${e.message}")
        }
    }

    override suspend fun seekForward(seconds: Long) {
        try {
            val controller = getController()
            val newPos = (controller.currentPosition + seconds * 1000)
                .coerceAtMost(controller.duration.coerceAtLeast(0))
            controller.seekTo(newPos)
        } catch (e: Exception) {
            Log.e(tag, "Failed to seek forward: ${e.message}")
        }
    }

    override suspend fun seekBackward(seconds: Long) {
        try {
            val controller = getController()
            val newPos = (controller.currentPosition - seconds * 1000).coerceAtLeast(0)
            controller.seekTo(newPos)
        } catch (e: Exception) {
            Log.e(tag, "Failed to seek backward: ${e.message}")
        }
    }

    override suspend fun setVolume(volume: Float) {
        try {
            getController().volume = volume
            _volume.value = volume
        } catch (e: Exception) {
            Log.e(tag, "Failed to set volume: ${e.message}")
        }
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        positionPollingJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        scope.cancel()
    }

    private fun mapPlaybackState(state: Int): VideoPlaybackState {
        return when (state) {
            Player.STATE_IDLE -> VideoPlaybackState.IDLE
            Player.STATE_BUFFERING -> VideoPlaybackState.BUFFERING
            Player.STATE_READY -> VideoPlaybackState.READY
            Player.STATE_ENDED -> VideoPlaybackState.ENDED
            else -> VideoPlaybackState.IDLE
        }
    }
}
