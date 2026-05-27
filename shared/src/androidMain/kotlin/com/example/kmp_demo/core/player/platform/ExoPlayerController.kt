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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.example.kmp_demo.core.player.cache.ExoPlayerCache
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 基于 ExoPlayer (Media3) + SimpleCache 的 Android 原生播放器控制器。
 *
 * ## 缓存架构（原生 CacheDataSource 模式）
 *
 * 使用 ExoPlayer 官方推荐的 [CacheDataSource] 方案，完全替代旧的本地 HTTP 代理。
 *
 * ```
 * ExoPlayer
 *   └─ HlsMediaSource
 *        └─ CacheDataSource.Factory
 *             ├─ 命中：SimpleCache(磁盘) → 直接读取，零网络请求
 *             └─ 未命中：DefaultHttpDataSource → CDN，同时写入 SimpleCache
 * ```
 *
 * ## 优势（对比旧代理方案）
 * - **无端口竞态**：无本地 HTTP 服务器，无异步绑定问题
 * - **真正流式**：CacheDataSource 逐块写入缓存，同时逐块喂给解码器，首帧延迟最低
 * - **Range 请求原生支持**：Seek 操作直接命中缓存对应字节范围，无需重下整个切片
 * - **ExoPlayer 原生集成**：缓存写入由 ExoPlayer 内部调度，不占用额外线程
 * - **官方维护**：Google 团队维护，稳定性和兼容性有保障
 *
 * @param context Android 上下文
 * @param exoCache ExoPlayer SimpleCache 实例（应用级单例，由 DI 注入）
 */
@OptIn(UnstableApi::class)
class ExoPlayerController(
    private val context: Context,
    private val exoCache: ExoPlayerCache,
) : IPlayerController {

    companion object {
        private const val TAG = "ExoPlayerController"
        private const val POLL_INTERVAL_MS = 250L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionPollingJob: Job? = null

    // ==================== CacheDataSource 工厂（核心缓存入口） ====================

    /**
     * HTTP 数据源工厂（上游，仅在缓存未命中时使用）。
     * 8 秒超时，允许跨协议重定向（http→https）。
     */
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(8_000)
        .setAllowCrossProtocolRedirects(true)

    /**
     * 带缓存的数据源工厂（ExoPlayer 实际使用的工厂）。
     *
     * [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR]：
     * 缓存读取失败时（如磁盘损坏），自动回退到网络，不崩溃。
     */
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(exoCache.cache)
        .setUpstreamDataSourceFactory(httpDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // ==================== ExoPlayer 实例 ====================

    /** 暴露 ExoPlayer 实例，供 [ExoPlayerSurface] 的 AndroidView 绑定 */
    val player: ExoPlayer

    // ==================== 状态 ====================

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
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // 注入带缓存的 HLS 媒体源工厂
            .setMediaSourceFactory(HlsMediaSource.Factory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playbackState.value = VideoPlaybackState.PLAYING
                } else {
                    if (_playbackState.value == VideoPlaybackState.PLAYING) {
                        _playbackState.value = VideoPlaybackState.PAUSED
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val mapped = mapExoState(state)
                // READY 状态不覆盖 PLAYING，避免状态抖动
                if (mapped != VideoPlaybackState.READY || _playbackState.value != VideoPlaybackState.PLAYING) {
                    _playbackState.value = mapped
                }
                Log.d(TAG, "ExoPlayer state=$state → $mapped")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message} (code=${error.errorCode})")
                _playbackState.value = VideoPlaybackState.ERROR
            }
        })

        _volume.value = player.volume
        startPositionPolling()
    }

    // ==================== 位置轮询 ====================

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                try {
                    _currentPosition.value = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        _duration.value = dur
                        _bufferedPercent.value =
                            ((player.bufferedPosition.toFloat() / dur) * 100)
                                .toInt().coerceIn(0, 100)
                    }
                } catch (_: Exception) { /* 播放器尚未就绪 */ }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // ==================== IPlayerController ====================

    @OptIn(UnstableApi::class)
    override suspend fun open(url: String, headers: Map<String, String>?) {
        _playbackState.value = VideoPlaybackState.BUFFERING
        try {
            // 如果有自定义请求头，重建上游工厂（SimpleCache 不感知 headers，只透传给上游）
            val factory = if (!headers.isNullOrEmpty()) {
                val headersFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(8_000)
                    .setReadTimeoutMs(8_000)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                CacheDataSource.Factory()
                    .setCache(exoCache.cache)
                    .setUpstreamDataSourceFactory(headersFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } else {
                cacheDataSourceFactory
            }

            val mediaSource = HlsMediaSource.Factory(factory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        .setMediaId(url)
                        .build()
                )

            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()
            Log.d(TAG, "Opening: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open: ${e.message}")
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        try {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "play() error: ${e.message}")
        }
    }

    override suspend fun pause() {
        try { player.pause() } catch (e: Exception) {
            Log.e(TAG, "pause() error: ${e.message}")
        }
    }

    override suspend fun togglePlayPause() {
        try {
            if (player.isPlaying) player.pause()
            else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePlayPause() error: ${e.message}")
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try { player.seekTo(positionMs) } catch (e: Exception) {
            Log.e(TAG, "seekTo() error: ${e.message}")
        }
    }

    override suspend fun seekForward(seconds: Long) {
        try {
            val newPos = (player.currentPosition + seconds * 1000)
                .coerceAtMost(player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
        } catch (e: Exception) {
            Log.e(TAG, "seekForward() error: ${e.message}")
        }
    }

    override suspend fun seekBackward(seconds: Long) {
        try {
            val newPos = (player.currentPosition - seconds * 1000).coerceAtLeast(0L)
            player.seekTo(newPos)
        } catch (e: Exception) {
            Log.e(TAG, "seekBackward() error: ${e.message}")
        }
    }

    override suspend fun setVolume(volume: Float) {
        try {
            player.volume = volume.coerceIn(0f, 1f)
            _volume.value = volume
        } catch (e: Exception) {
            Log.e(TAG, "setVolume() error: ${e.message}")
        }
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        positionPollingJob?.cancel()
        scope.cancel()
        player.release()
        // 注意：exoCache.release() 由 Koin single{} 在 Application 销毁时调用，此处不调用
    }

    private fun mapExoState(state: Int): VideoPlaybackState = when (state) {
        Player.STATE_IDLE -> VideoPlaybackState.IDLE
        Player.STATE_BUFFERING -> VideoPlaybackState.BUFFERING
        Player.STATE_READY -> VideoPlaybackState.READY
        Player.STATE_ENDED -> VideoPlaybackState.ENDED
        else -> VideoPlaybackState.IDLE
    }
}
