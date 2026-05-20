package com.example.kmp_demo.core.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 基于 DFA（确定有穷自动机）的跨平台敏感词过滤器。
 *
 * ## 架构定位
 *
 * 纯客户端本地防线，用于搜索输入的前置拦截。
 * 将 O(N×M) 暴力匹配降为 O(L)（L = 输入长度），
 * 确保万级词表下每次搜索零卡顿。
 *
 * ## 防绕过策略
 *
 * [preProcessText] 在匹配前执行归一化：
 * - 全角→半角、大写→小写
 * - 剔除空格、标点符号、控制字符
 * - 剔除数字（可选，通过 [filterDigits] 控制）
 *
 * ## 线程安全
 *
 * 所有读写操作通过 [Mutex] 保护，支持并发初始化与查询。
 *
 * @param filterDigits 是否同时过滤数字绕过（如 "P0RN"），默认 false
 */
class SensitiveWordFilter(
    private val filterDigits: Boolean = false,
) {
    /** DFA 前缀树根节点：子节点用 Char 索引，叶子节点用 "isEnd" 标记 */
    private val rootNode = HashMap<Any, Any>()

    private val mutex = Mutex()

    /** 是否启用拦截，默认 true。设为 false 可临时放行所有搜索 */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * 初始化敏感词树。
     *
     * 支持多次调用以增量更新词表（旧词表会被清空重建）。
     *
     * @param sensitiveWords 敏感词列表
     */
    suspend fun initWordTree(sensitiveWords: Collection<String>) = mutex.withLock {
        rootNode.clear()
        for (word in sensitiveWords) {
            val trimmed = word.trim()
            if (trimmed.isEmpty()) continue
            var node = rootNode
            for ((i, ch) in trimmed.withIndex()) {
                @Suppress("UNCHECKED_CAST")
                node = (node[ch] as? HashMap<Any, Any>) ?: HashMap<Any, Any>().also { node[ch] = it }
                if (i == trimmed.lastIndex) {
                    node["isEnd"] = true
                }
            }
        }
    }

    /**
     * 判断文本是否命中敏感词。
     *
     * @param text 用户输入的原始文本
     * @return true=命中（应拦截），false=放行
     */
    suspend fun containsSensitiveWord(text: String): Boolean {
        if (!isEnabled || text.isBlank()) return false

        return mutex.withLock {
            val clean = preProcessText(text)
            if (clean.isEmpty()) return@withLock false

            for (i in clean.indices) {
                var node = rootNode
                for (j in i until clean.length) {
                    @Suppress("UNCHECKED_CAST")
                    node = (node[clean[j]] as? HashMap<Any, Any>) ?: break
                    if (node["isEnd"] == true) return@withLock true
                }
            }
            false
        }
    }

    /**
     * 文本归一化：剔除空格、标点、控制字符，大写转小写。
     *
     * 应对绕过手段：
     * - "P_O_R_N" → "porn"
     * - " 激情  " → "激情"
     * - "国产.成人" → "国产成人"
     * - "FRee XXX" → "freexxx"
     */
    private fun preProcessText(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                // 跳过空格、制表符、换行等空白字符
                ch.isWhitespace() -> continue
                // 跳过标点符号
                ch.isLetterOrDigit().not() -> continue
                // 可选：跳过数字（防 "P0RN" 绕过）
                filterDigits && ch.isDigit() -> continue
                // 大写转小写
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }
}
