package com.example.kmp_demo.core.player.platform

import android.content.Context
import android.net.Uri
import com.example.kmp_demo.core.PlatformLogger
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
import com.example.kmp_demo.core.player.cache.AdFilterDataSourceFactory
import com.example.kmp_demo.core.player.cache.ExoPlayerCache
import com.example.kmp_demo.core.player.cache.M3u8Sanitizer
import com.example.kmp_demo.core.player.domain.FullscreenController
import com.example.kmp_demo.core.player.domain.IVideoPlayerController
import com.example.kmp_demo.core.player.domain.PlayerError
import com.example.kmp_demo.core.player.domain.PlayerErrorType
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.net.toUri
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy

/**
 * 基于 ExoPlayer (Media3) + SimpleCache 的 Android 原生播放器控制器。
 *
 * ## 缓存 + 广告过滤架构
 *
 * 使用 ExoPlayer 官方推荐的 [CacheDataSource] 方案，完全替代旧的本地 HTTP 代理。
 * 在缓存层之上叠加 [AdFilterDataSourceFactory] 进行广告过滤。
 *
 * ```
 * ExoPlayer
 *   └─ HlsMediaSource
 *        └─ AdFilterDataSourceFactory  ← 广告过滤层（内容嗅探 + M3U8 清洗）
 *             └─ CacheDataSource.Factory
 *                  ├─ 命中：SimpleCache(磁盘) → 直接读取，零网络请求
 *                  └─ 未命中：DefaultHttpDataSource → CDN，同时写入 SimpleCache
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
 * @param m3u8Sanitizer M3U8 清洗器，用于过滤广告切片
 * @param httpClient Ktor HTTP 客户端，用于下载原始 M3U8 内容
 * @param fullscreenController 全屏控制器（可选）
 */
@OptIn(UnstableApi::class)
class ExoPlayerController(
    private val context: Context,
    private val exoCache: ExoPlayerCache,
    private val m3u8Sanitizer: M3u8Sanitizer,
    private val httpClient: HttpClient,
    private val fullscreenController: FullscreenController? = null,
) : IVideoPlayerController {


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
     * 带缓存的数据源工厂。
     *
     * [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR]：
     * 缓存读取失败时（如磁盘损坏），自动回退到网络，不崩溃。
     *
     * 数据流：
     * ```
     * ExoPlayer → CacheDataSource → DefaultHttpDataSource
     *               (缓存层)           (网络层)
     * ```
     */
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(exoCache.cache)
        .setUpstreamDataSourceFactory(httpDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * 广告过滤 + 缓存的数据源工厂（ExoPlayer 实际使用的工厂）。
     *
     * 包装 [cacheDataSourceFactory]，所有请求先经过 [AdFilterDataSource] 内容嗅探，
     * 对 M3U8 播放列表进行广告过滤后，干净内容才进入 [CacheDataSource] 缓存。
     *
     * 数据流：
     * ```
     * ExoPlayer → AdFilterDataSource → CacheDataSource → DefaultHttpDataSource
     *               (广告过滤层)          (缓存层)           (网络层)
     * ```
     *
     * 为什么 AdFilterDataSource 在 CacheDataSource 外面？
     * - AdFilterDataSource 通过内容嗅探（首行 #EXTM3U）识别 M3U8 请求，
     *   用 Ktor 下载原始内容 → M3u8Sanitizer 过滤 → 返回干净 ByteArray。
     * - 过滤后的干净 M3U8 才进入 CacheDataSource，确保 .exo 缓存中只存干净内容。
     * - TS/MP4 切片直接透传，不受影响。
     * - 嗅探/过滤失败时回退到 upstream（CacheDataSource），不影响播放。
     */
    private val adFilterCacheDataSourceFactory = AdFilterDataSourceFactory(
        upstreamFactory = cacheDataSourceFactory,
        m3u8Sanitizer = m3u8Sanitizer,
        httpClient = httpClient,
    )

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

    private val _playerError = MutableStateFlow<PlayerError?>(null)
    override val playerError: StateFlow<PlayerError?> = _playerError.asStateFlow()

    init {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // minBufferMs: 提高最小缓冲到30秒，保证后续播放平滑
                60_000, // maxBufferMs: 最大缓冲60秒
                2_000,  // bufferForPlaybackMs: 降低到2秒，实现秒开、极速起播
                5_000   // bufferForPlaybackAfterRebufferMs: 跌入二次缓冲后，攒满5秒就继续播，避免长等待
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 1. 创建自适应重试并降低网络错误敏感度的 LoadErrorHandlingPolicy
        val customErrorHandlingPolicy =
            object : DefaultLoadErrorHandlingPolicy() {
                override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                    // 针对 HLS 的媒体切片(DATA_TYPE_MEDIA)，提高容错重试次数，防止直接抛异常卡死
                    return if (dataType == C.DATA_TYPE_MEDIA) 6 else 3
                }
            }

        player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // 注入带缓存的 HLS 媒体源工厂
            .setMediaSourceFactory(
                HlsMediaSource.Factory(cacheDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(customErrorHandlingPolicy)
            )
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playbackState.value = VideoPlaybackState.PLAYING
                    startPositionPolling() // 开始轮询
                } else {
                    stopPositionPolling()  // 停止轮询，释放主线程
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
                PlatformLogger.d(TAG, "ExoPlayer state=$state → $mapped")
            }

            override fun onPlayerError(error: PlaybackException) {
                PlatformLogger.e(TAG, "Player error: ${error.message} (code=${error.errorCode})")
                _playbackState.value = VideoPlaybackState.ERROR
                _playerError.value = mapExoError(error)
            }
        })

        _volume.value = player.volume
    }

    // ==================== 位置轮询 ====================

    private fun startPositionPolling() {
        if (positionPollingJob?.isActive == true) return // 避免重复创建
        positionPollingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying) { // 仅在真正播放时读取
                    _currentPosition.value = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        _duration.value = dur
                        _bufferedPercent.value = ((player.bufferedPosition.toFloat() / dur) * 100)
                            .toInt().coerceIn(0, 100)
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }



    // ==================== IVideoPlayerController ====================

    @OptIn(UnstableApi::class)
    override suspend fun open(url: String, headers: Map<String, String>?) {
        //暂停上一个视频播放
        if (_playbackState.value == VideoPlaybackState.PLAYING) {
            player.stop()
        }
        //每次开新视频时，顺便在后台瞅一眼缓存满了没，满了就自动咔嚓掉旧的
        exoCache.checkAndTrim()
        _playbackState.value = VideoPlaybackState.BUFFERING
        try {
            // 如果有自定义请求头，重建带广告过滤的工厂
            val factory = if (!headers.isNullOrEmpty()) {
                val headersFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(8_000)
                    .setReadTimeoutMs(8_000)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                val cacheFactory = CacheDataSource.Factory()
                    .setCache(exoCache.cache)
                    .setUpstreamDataSourceFactory(headersFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                // 广告过滤包装在缓存外面，确保过滤后的干净内容才被缓存
                AdFilterDataSourceFactory(
                    upstreamFactory = cacheFactory,
                    m3u8Sanitizer = m3u8Sanitizer,
                    httpClient = httpClient,
                )
            } else {
                adFilterCacheDataSourceFactory
            }

            val mediaSource = HlsMediaSource.Factory(factory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(url.toUri())
                        .setMediaId(url)
                        .build()
                )

            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()
            PlatformLogger.d(TAG, "Opening: $url")
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "Failed to open: ${e.message}")
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        try {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "play() error: ${e.message}")
        }
    }

    override suspend fun pause() {
        try {
            player.pause()
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "pause() error: ${e.message}")
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
            PlatformLogger.e(TAG, "togglePlayPause() error: ${e.message}")
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            player.seekTo(positionMs)
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "seekTo() error: ${e.message}")
        }
    }

    override suspend fun seekForward(seconds: Long) {
        try {
            val newPos = (player.currentPosition + seconds * 1000)
                .coerceAtMost(player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "seekForward() error: ${e.message}")
        }
    }

    override suspend fun seekBackward(seconds: Long) {
        try {
            val newPos = (player.currentPosition - seconds * 1000).coerceAtLeast(0L)
            player.seekTo(newPos)
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "seekBackward() error: ${e.message}")
        }
    }

    override suspend fun setVolume(volume: Float) {
        try {
            player.volume = volume.coerceIn(0f, 1f)
            _volume.value = volume
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "setVolume() error: ${e.message}")
        }
    }


    override suspend fun setFullscreen(isFullScreen: Boolean) {
        _isFullScreen.value = isFullScreen
        if (isFullScreen) {
            fullscreenController?.enterFullscreen()
        } else {
            fullscreenController?.exitFullscreen()
        }
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

    /**
     * 将 ExoPlayer 的 [PlaybackException] 映射为统一的 [PlayerError]。
     *
     * ExoPlayer 的错误码体系：
     * - [PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED]：网络连接失败
     * - [PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT]：连接超时
     * - [PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND]：文件未找到（404）
     * - [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS]：HTTP 状态码错误
     * - [PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED]：明文 HTTP 被禁止
     * - [PlaybackException.ERROR_CODE_DECODER_INIT_FAILED]：解码器初始化失败
     * - [PlaybackException.ERROR_CODE_DRM_*]：DRM 相关错误
     */
    private fun mapExoError(error: PlaybackException): PlayerError {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlayerError(
                type = PlayerErrorType.SOURCE_NOT_FOUND,
                message = "视频资源不存在",
                detail = error.message,
                retryable = false,
            )

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlayerError(
                type = PlayerErrorType.NETWORK_ERROR,
                message = "网络连接失败，请检查网络",
                detail = error.message,
                retryable = true,
            )

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlayerError(
                type = PlayerErrorType.TIMEOUT,
                message = "连接超时，请稍后重试",
                detail = error.message,
                retryable = true,
            )

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                // 尝试从错误消息中提取 HTTP 状态码
                val statusCode = error.message?.let { msg ->
                    Regex("HTTP (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
                }
                if (statusCode != null) {
                    PlayerError.fromHttpStatusCode(statusCode, error.message)
                } else {
                    PlayerError(
                        type = PlayerErrorType.NETWORK_ERROR,
                        message = "服务器响应异常",
                        detail = error.message,
                        retryable = true,
                    )
                }
            }

            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlayerError(
                type = PlayerErrorType.FORBIDDEN,
                message = "不安全的 HTTP 链接被禁止",
                detail = error.message,
                retryable = false,
            )

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> PlayerError(
                type = PlayerErrorType.FORMAT_ERROR,
                message = "视频格式不支持或已损坏",
                detail = error.message,
                retryable = false,
            )

            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> PlayerError(
                type = PlayerErrorType.DRM_ERROR,
                message = "DRM 解密失败，无法播放此视频",
                detail = error.message,
                retryable = false,
            )

            else -> PlayerError(
                type = PlayerErrorType.UNKNOWN,
                message = "播放出错",
                detail = error.message,
                retryable = true,
            )
        }
    }
}
