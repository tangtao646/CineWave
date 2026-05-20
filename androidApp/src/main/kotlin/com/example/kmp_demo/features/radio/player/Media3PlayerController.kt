package com.example.kmp_demo.features.radio.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.domain.player.IPlayerController
import com.example.kmp_demo.features.radio.domain.player.MediaMetadataInfo
import com.example.kmp_demo.features.radio.domain.player.PlayableMedia
import com.example.kmp_demo.features.radio.service.RadioPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class Media3PlayerController(
    private val context: Context
) : IPlayerController {

    private val tag = "Media3PlayerController"
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerDeferred = CompletableDeferred<MediaController>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    // 重连尝试计数
    private var reconnectCount = 0
    private val MAX_RECONNECT_ATTEMPTS = 3

    init {
        initializeController()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initializeController() {
        synchronized(this) {
            if (controllerDeferred.isCompleted && !controllerDeferred.isCancelled && controllerDeferred.getCompletionExceptionOrNull() == null) {
                return
            }
            if (controllerDeferred.isCompleted) {
                controllerDeferred = CompletableDeferred()
            }
        }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))

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
        _isPlaying.value = controller.isPlaying
        _playbackState.value = mapPlaybackState(controller.playbackState)
        _currentMediaId.value = controller.currentMediaItem?.mediaId
        
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) reconnectCount = 0 // 播放成功，重置重连计数
            }

            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = mapPlaybackState(state)
                Log.d(tag, "State Changed: $state")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaId.value = mediaItem?.mediaId
                _metadataInfo.value = mediaItem?.mediaMetadata?.toDomain() ?: MediaMetadataInfo()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                _metadataInfo.value = mediaMetadata.toDomain()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e(tag, "Player Error: ${error.message}, Code: ${error.errorCode}")
                
                // 自动重连机制
                if (shouldAttemptReconnect(error)) {
                    attemptReconnect(controller)
                } else {
                    _playbackState.value = AppPlaybackState.ERROR
                    _errorEvents.tryEmit(getErrorMessage(error))
                }
            }
        })
    }

    private fun shouldAttemptReconnect(error: PlaybackException): Boolean {
        return reconnectCount < MAX_RECONNECT_ATTEMPTS && (
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW // 电台流常见错误
        )
    }

    private fun attemptReconnect(controller: MediaController) {
        reconnectCount++
        Log.w(tag, "Attempting reconnect $reconnectCount/$MAX_RECONNECT_ATTEMPTS...")
        
        scope.launch {
            _playbackState.value = AppPlaybackState.BUFFERING // 重连时显示缓冲状态
            delay(2000L * reconnectCount) // 指数退避
            controller.prepare()
            controller.play()
        }
    }

    private fun getErrorMessage(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "电台链接无效 (403/404)"
            else -> "播放失败: ${error.errorCodeName}"
        }
    }

    private fun MediaMetadata.toDomain(): MediaMetadataInfo {
        return MediaMetadataInfo(
            title = title?.toString(),
            artist = artist?.toString() ?: subtitle?.toString()
        )
    }

    private fun mapPlaybackState(state: Int): AppPlaybackState {
        return when (state) {
            Player.STATE_IDLE -> AppPlaybackState.IDLE
            Player.STATE_BUFFERING -> AppPlaybackState.BUFFERING
            Player.STATE_READY -> AppPlaybackState.READY
            Player.STATE_ENDED -> AppPlaybackState.ENDED
            else -> AppPlaybackState.IDLE
        }
    }

    private suspend fun getController(): MediaController {
        if (controllerDeferred.isCompleted && controllerDeferred.getCompletionExceptionOrNull() != null) {
            initializeController()
        }
        return withTimeout(10000) { controllerDeferred.await() }
    }

    override suspend fun setPlaylist(items: List<PlayableMedia>, startIndex: Int) {
        val controller = getController()
        reconnectCount = 0 // 切歌重置重连计数
        controller.setMediaItems(items.map { it.toMediaItem() }, startIndex, 0L)
        controller.prepare() 
    }

    override suspend fun play() {
        val controller = getController()
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override suspend fun pause() { getController().pause() }
    override suspend fun skipToNext() { getController().seekToNextMediaItem() }
    override suspend fun skipToPrevious() { getController().seekToPreviousMediaItem() }
    override suspend fun stop() { getController().stop() }
    
    override fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        scope.cancel()
    }

    private fun PlayableMedia.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(if (artworkUri.isBlank()) null else Uri.parse(artworkUri))
                    .build()
            ).build()
    }
}
