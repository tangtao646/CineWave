package com.example.kmp_demo.core.navigation

/**
 * Kotlin stdlib URL 编码，替代 java.net.URLEncoder
 */
fun String.encodeNavParam(): String =
    this.encodeToByteArray().joinToString("") { byte ->
        "%${byte.toUByte().toString(16).uppercase()}"
    }

/**
 * Kotlin stdlib URL 解码，替代 java.net.URLDecoder
 */
fun String.decodeNavParam(): String {
    val hexPattern = "%([0-9A-Fa-f]{2})".toRegex()
    val bytes = hexPattern.findAll(this).map {
        it.groupValues[1].toInt(16).toByte()
    }.toList().toByteArray()
    return if (bytes.isNotEmpty()) bytes.decodeToString() else this
}
