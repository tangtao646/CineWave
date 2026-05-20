package com.example.kmp_demo.core.player.service

import android.app.PendingIntent
import android.content.Intent
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
import com.example.kmp_demo.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Android Foreground Service 管理视频播放器的生命周期。
 *
 * 当 App 退到后台时，此 Service 保持存活，确保 ExoPlayer 继续播放。
 * 同时提供 MediaSession，在锁屏通知栏显示播放控制。
 *
 * 参考 [RadioPlaybackService] 的相同模式。
 */
class VideoPlaybackService : MediaSessionService() {

    companion object {
        const val TAG = "VideoPlaybackService"

        /** Intent action to start playback with a URL */
        const val ACTION_PLAY = "com.example.kmp_demo.action.VIDEO_PLAY"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_HEADERS = "extra_headers"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // 1. 构建 OkHttpClient（复用 RadioPlaybackService 的配置模式）
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // 2. 创建 DataSourceFactory
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        // 3. 初始化 ExoPlayer
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
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
                Log.d(TAG, "Playback State: $state")
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException
            ) {
                Log.e(TAG, "Player Error: ${error.cause?.message ?: error.message}")
            }
        })

        // 4. 创建 MediaSession（提供锁屏通知控制）
        // 使用唯一 ID 避免与 RadioPlaybackService 的 MediaSession 冲突
        mediaSession = MediaSession.Builder(this, player)
            .setId("video_playback_session")
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    Log.d(TAG, "onPlaybackResumption called")
                    val count = player.mediaItemCount
                    val mediaItems = (0 until count).map { player.getMediaItemAt(it) }
                    val currentIndex = player.currentMediaItemIndex
                    val currentPosition = player.currentPosition
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(mediaItems, currentIndex, currentPosition)
                    )
                }
            })
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务中划掉 App 时，如果还在播放则保持 Service 运行
        val shouldStop = !player.playWhenReady
        if (shouldStop) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** 获取当前 ExoPlayer 实例（供 VideoPlaybackServiceConnection 使用） */
    fun getPlayer(): ExoPlayer = player
}
