package com.example.kmp_demo.core.monitoring

/**
 * Sentry 统一配置。
 *
 * 由各平台入口处构建并传入 [SentryBridge.initSentry]。
 * DSN 通过 BuildConfig / 环境变量 / 配置文件注入，绝不硬编码在代码中。
 *
 * @param dsn Sentry DSN，从外部安全注入
 * @param environment 环境标识（"release" / "debug" / "staging"）
 * @param tracesSampleRate 性能追踪采样率（0.0 ~ 1.0）
 * @param profilesSampleRate 性能分析采样率（0.0 ~ 1.0）
 * @param isAnrEnabled 是否启用 ANR 检测（仅 Android 有效）
 * @param isAttachScreenshot 是否附加截图（仅 Android 有效）
 * @param release 发布版本号（可选，自动从 BuildConfig 获取）
 * @param dist 构建编号（可选）
 * @param context Android Context（仅 Android 平台需要，JVM 平台忽略）
 */
data class SentryConfig(
    val dsn: String = "https://07785b08f50e30ba0c3bd032ad0fb43d@o4511482158120960.ingest.us.sentry.io/4511482472955904",
    val environment: String = "release",
    val tracesSampleRate: Double = 0.3,
    val profilesSampleRate: Double = 0.1,
    val isAnrEnabled: Boolean = true,
    val isAttachScreenshot: Boolean = true,
    val isDebug: Boolean = false,
    val dist: String? = null,
    val context: Any? = null,
)
