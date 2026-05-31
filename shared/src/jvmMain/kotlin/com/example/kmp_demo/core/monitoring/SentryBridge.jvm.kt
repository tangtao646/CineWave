package com.example.kmp_demo.core.monitoring

import io.sentry.Sentry

/**
 * JVM/Desktop 平台 Sentry 桥接实现。
 *
 * DSN 通过 [SentryConfig] 传入，绝不硬编码。
 * 初始化由 [initSentry] 统一处理，在 [main.kt] 入口处调用。
 */
actual fun initSentry(config: SentryConfig) {

    Sentry.init { options ->
        options.dsn = config.dsn
        options.environment = config.environment
        options.tracesSampleRate = config.tracesSampleRate
        options.profilesSampleRate = config.profilesSampleRate
        options.isDebug = config.isDebug
        options.release = if (config.isDebug) "debug" else "release"
        config.dist?.let { options.dist = it }
    }


}

