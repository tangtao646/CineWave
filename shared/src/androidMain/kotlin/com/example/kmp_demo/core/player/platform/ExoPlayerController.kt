package com.example.kmp_demo.core.player.platform

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.HybridDataSource
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 基于 ExoPlayer (Media3) 的原生播放器控制器。
 *
 * 完全替代 [ComposeMediaPlayerController]，直接操作 ExoPlayer API，
 * 不再依赖任何第三方 Compose 播放库。
 *
 * 设计要点：
 * - 播放器实例由本类创建和管理，生命周期绑定到 Controller
 * - 通过 [player] 属性暴露 ExoPlayer 实例，供 [ExoPlayerSurface] 绑定
 * - 位置轮询使用协程，250ms 间隔
 * - 使用 [HybridDataSource] 桥接 KMP 的 [DiskLruCache] 与 ExoPlayer 数据加载
 *
 * 缓存架构（解法A）：
 * - [HybridDataSource] 在每次数据请求时先查询 DiskLruCache
 * - 命中 → 走本地 [OkioDiskCacheDataSource] 流式读取
 * - 未命中 → 无缝回退到 [DefaultHttpDataSource] 原生 HTTP 网络栈
 * - 后台 [M3u8CacheInterceptor] 持续预加载切片到 DiskLruCache
 */
@OptIn(UnstableApi::class)
class ExoPlayerController(
    private val context: Context,
    private val diskCache: DiskLruCache,
) : IPlayerController {

    companion object {
        private const val TAG = "ExoPlayerController"
        private const val POLL_INTERVAL_MS = 250L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionPollingJob: Job? = null

    /** 暴露 ExoPlayer 实例，供 [ExoPlayerSurface] 的 AndroidView 绑定 */
    val player: ExoPlayer

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

    init {
        // ==================== 构建混合数据源工厂 ====================
        // 1. 创建标准的网络数据源工厂（配置 Connect/Read Timeout）
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        // 2. 创建组装好的混合复合数据源工厂
        val hybridDataSourceFactory = DataSource.Factory {
            HybridDataSource(
                diskCache = diskCache,
                networkDataSource = httpDataSourceFactory.createDataSource()
            )
        }

        // 3. 构建 HlsMediaSource 工厂
        val hlsMediaSourceFactory = HlsMediaSource.Factory(hybridDataSourceFactory)

        val myLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50_000,
                64_000,
                2500,
                5000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 初始化 ExoPlayer
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setMediaSourceFactory(hlsMediaSourceFactory) // 🌟 核心注入点
            .setLoadControl(myLoadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 注册播放器监听器
        // 注意：ExoPlayer 的回调顺序不保证，onPlaybackStateChanged 可能在
        // onIsPlayingChanged 之后触发，导致 STATE_READY 覆盖 PLAYING 状态。
        // 因此 onPlaybackStateChanged 中需要结合 isPlaying 来判断最终状态。
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playbackState.value = VideoPlaybackState.PLAYING
                } else {
                    // 当播放停止时，根据当前 ExoPlayer 状态决定是 PAUSED 还是其他
                    val currentState = _playbackState.value
                    if (currentState == VideoPlaybackState.PLAYING) {
                        _playbackState.value = VideoPlaybackState.PAUSED
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val mapped = mapPlaybackState(state)
                // 如果当前已经是 PLAYING 状态，不要被 STATE_READY 覆盖
                // 只有当 mapped 不是 READY，或者当前不是 PLAYING 时才更新
                if (mapped != VideoPlaybackState.READY || _playbackState.value != VideoPlaybackState.PLAYING) {
                    _playbackState.value = mapped
                }
                Log.d(TAG, "Playback state: $state, mapped: $mapped")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}, code: ${error.errorCode}")
                _playbackState.value = VideoPlaybackState.ERROR
            }
        })

        // 同步初始音量
        _volume.value = player.volume

        // 启动位置轮询
        startPositionPolling()
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                try {
                    _currentPosition.value = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0)
                    if (dur > 0) {
                        _duration.value = dur
                        val buffered = player.bufferedPosition
                        _bufferedPercent.value =
                            ((buffered.toFloat() / dur) * 100).toInt().coerceIn(0, 100)
                    }
                } catch (_: Exception) {
                    // 播放器尚未就绪
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun open(url: String, headers: Map<String, String>?) {
        _playbackState.value = VideoPlaybackState.BUFFERING
        try {
            val mediaItem = MediaItem.Builder()
                .setMediaId(url)
                .setUri(Uri.parse(url))
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: ${e.message}")
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        try {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: ${e.message}")
        }
    }

    override suspend fun pause() {
        try {
            player.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
        }
    }

    override suspend fun togglePlayPause() {
        try {
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle play/pause: ${e.message}")
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            player.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek: ${e.message}")
        }
    }

    override suspend fun seekForward(seconds: Long) {
        try {
            val newPos = (player.currentPosition + seconds * 1000)
                .coerceAtMost(player.duration.coerceAtLeast(0))
            player.seekTo(newPos)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek forward: ${e.message}")
        }
    }

    override suspend fun seekBackward(seconds: Long) {
        try {
            val newPos = (player.currentPosition - seconds * 1000).coerceAtLeast(0)
            player.seekTo(newPos)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek backward: ${e.message}")
        }
    }

    override suspend fun setVolume(volume: Float) {
        try {
            player.volume = volume
            _volume.value = volume
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume: ${e.message}")
        }
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        positionPollingJob?.cancel()
        scope.cancel()
        player.release()
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
