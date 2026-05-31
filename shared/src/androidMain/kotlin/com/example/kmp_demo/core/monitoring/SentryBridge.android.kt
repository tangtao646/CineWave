package com.example.kmp_demo.core.monitoring

import io.sentry.android.core.SentryAndroid

/**
 * Android 平台 Sentry 桥接实现。
 *
 */
actual fun initSentry(config: SentryConfig) {

    val context = requireNotNull(config.context as? android.content.Context) {
        "SentryConfig.context must be a non-null android.content.Context for Android platform"
    }

    SentryAndroid.init(context) { options ->
        options.dsn = config.dsn
        options.environment = config.environment
        options.tracesSampleRate = config.tracesSampleRate
        options.profilesSampleRate = config.profilesSampleRate
        options.isAnrEnabled = config.isAnrEnabled
        options.isAttachScreenshot = config.isAttachScreenshot
        options.isDebug = config.isDebug
        options.release= if (config.isDebug) "debug" else "release"
        config.dist?.let { options.dist = it }

    }

}