package com.example.kmp_demo.features.radio.service

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RadioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer


    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. 构建一个强力的 OkHttpClient
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    // 核心：伪装成 PC 端 Chrome 浏览器，避开喜马拉雅等平台的 UA 拦截
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    // 核心：设置 Referer，有些服务器会检查来源
                    .header("Referer", "https://www.radio-browser.info/")
                    .header("Icy-MetaData", "1") // 允许获取电台元数据
                    .header("Connection", "keep-alive")
                    .build()
                chain.proceed(request)
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // 2. 创建 DataSourceFactory 并再次显式设置 UA
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        // 3. 初始化播放器
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onPlaybackStateChanged(
                eventTime: AnalyticsListener.EventTime,
                state: Int
            ) {
                Log.d("RadioService", "Playback State: $state")
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException
            ) {
                // 打印更详细的错误原因
                Log.e("RadioService", "Player Error: ${error.cause?.message ?: error.message}")
            }
        })

        // Build MediaSession with a custom Callback that implements onPlaybackResumption
        // 使用唯一 ID 避免与 VideoPlaybackService 的 MediaSession 冲突
        mediaSession = MediaSession.Builder(this, player)
            .setId("radio_playback_session")
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    Log.d("RadioService", "onPlaybackResumption called")
                    // Return the player's current media items so Media3 can resume properly.
                    val count = player.mediaItemCount
                    val mediaItems = (0 until count).map { player.getMediaItemAt(it) }
                    val currentIndex = player.currentMediaItemIndex
                    val currentPosition = player.currentPosition
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(mediaItems, currentIndex, currentPosition)
                    )
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }


    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
