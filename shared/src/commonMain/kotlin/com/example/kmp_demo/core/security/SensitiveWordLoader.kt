package com.example.kmp_demo.core.security

import cinewave.shared.generated.resources.Res
import org.jetbrains.compose.resources.InternalResourceApi

/**
 * 跨平台敏感词表加载器。
 *
 * 从 [composeResources/files/sensitive_words.txt] 读取词表，
 * 解析后注入 [SensitiveWordFilter.initWordTree]。
 *
 * ## 文件格式
 *
 * - 每行一个敏感词
 * - 空行和 `#` 开头的行自动跳过
 * - 词表不区分大小写（[SensitiveWordFilter.preProcessText] 负责归一化）
 *
 * ## 使用方式
 *
 * 在 App 启动时（[App.kt] 的 LaunchedEffect 中）调用 [loadAsync]：
 * ```kotlin
 * LaunchedEffect(Unit) {
 *     val loader = SensitiveWordLoader(get<SensitiveWordFilter>())
 *     loader.loadAsync()
 * }
 * ```
 */
class SensitiveWordLoader(
    private val filter: SensitiveWordFilter,
) {

    /**
     * 异步加载敏感词表并初始化 DFA 树。
     *
     * 使用 Compose Resources 跨平台 API [Res.readBytes] 读取文件，
     * 因此必须在 Compose 协程作用域内调用。
     */
    @OptIn(InternalResourceApi::class)
    suspend fun loadAsync() {
        val bytes = Res.readBytes("files/sensitive_words.txt")
        val text = bytes.decodeToString()

        val words = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        filter.initWordTree(words)
    }
}
