package com.example.kmp_demo

import android.app.Application
import com.example.kmp_demo.core.monitoring.SentryConfig
import com.example.kmp_demo.core.monitoring.initSentry
import com.example.kmp_demo.core.videosource.di.coreVideosourceModule
import com.example.kmp_demo.di.androidAppModule
import com.example.kmp_demo.di.commonModule
import com.example.kmp_demo.di.platformModule
import com.example.kmp_demo.features.domestic.di.domesticModule
import com.example.kmp_demo.features.film.di.filmModule
import com.example.kmp_demo.features.radio.di.radioModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initSentry(SentryConfig( context = this))

        startKoin {
            // 1. 开启 Koin 日志（可选，推荐开发阶段开启）
            androidLogger(Level.INFO)
            androidContext(this@MainApplication)
            modules(
                listOf(
                    commonModule,
                    platformModule,
                    coreVideosourceModule,
                    radioModule,
                    filmModule,
                    domesticModule,
                    androidAppModule,
                )
            )

        }
    }
}
