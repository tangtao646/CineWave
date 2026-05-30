package com.example.kmp_demo.core.player.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 测试 URL 编码/解码逻辑。
 *
 * 验证 encodeURL() 函数能否正确处理包含中文的 URL。
 */
class UrlEncodingTest {

    @Test
    fun testEncodeUrlWithChineseCharacters() {
        val originalUrl = "https://s1.bfllvip.com/video/majiangzhiye/中字/index.m3u8"
        val encoded = originalUrl.encodeURL()

        println("原始 URL: $originalUrl")
        println("编码后:   $encoded")

        // 验证编码结果包含正确的 UTF-8 百分比编码
        // "中" 的 UTF-8 编码是 E4 B8 AD → %E4%B8%AD
        // "字" 的 UTF-8 编码是 E5 AD 97 → %E5%AD%97
        assertEquals(
            "https%3A%2F%2Fs1.bfllvip.com%2Fvideo%2Fmajiangzhiye%2F%E4%B8%AD%E5%AD%97%2Findex.m3u8",
            encoded,
            "中文应被编码为 UTF-8 百分比编码"
        )
    }

    @Test
    fun testEncodeUrlWithAlreadyEncodedUrl() {
        val originalUrl = "https://s1.bfllvip.com/video/majiangzhiye/%E4%B8%AD%E5%AD%97/index.m3u8"
        val encoded = originalUrl.encodeURL()

        println("原始 URL (已编码): $originalUrl")
        println("编码后:           $encoded")

        // 已编码的 URL 再次编码，% 会被编码为 %25
        // 这是正确的行为，因为 encodeURL 用于将 URL 放入 query parameter
        assertEquals(
            "https%3A%2F%2Fs1.bfllvip.com%2Fvideo%2Fmajiangzhiye%2F%25E4%25B8%25AD%25E5%25AD%2597%2Findex.m3u8",
            encoded,
            "已编码的 URL 再次编码时，% 应被编码为 %25"
        )
    }

    @Test
    fun testEncodeUrlWithAsciiOnlyUrl() {
        val originalUrl = "https://vod2.maowushi.com/20240806/9Y5xvjpZ/index.m3u8"
        val encoded = originalUrl.encodeURL()

        println("原始 URL (纯 ASCII): $originalUrl")
        println("编码后:              $encoded")

        // 纯 ASCII URL 编码后应该只是替换特殊字符
        assertEquals(
            "https%3A%2F%2Fvod2.maowushi.com%2F20240806%2F9Y5xvjpZ%2Findex.m3u8",
            encoded,
            "纯 ASCII URL 应正确编码特殊字符"
        )
    }

    @Test
    fun testEncodeUrlWithSpecialCharacters() {
        val originalUrl = "https://example.com/path with spaces/file(1).m3u8"
        val encoded = originalUrl.encodeURL()

        println("原始 URL (含特殊字符): $originalUrl")
        println("编码后:               $encoded")

        // 空格应编码为 %20（而非 +）
        assertTrue(encoded.contains("%20"), "空格应编码为 %20")
        assertTrue(!encoded.contains("+"), "不应使用 + 编码空格")
    }

    @Test
    fun testFullProxyUrlGeneration() {
        // 模拟 getProxiedM3u8Url 的完整流程
        val originalUrl = "https://s1.bfllvip.com/video/majiangzhiye/中字/index.m3u8"
        val proxyPort = 19876

        val proxiedUrl = "http://localhost:$proxyPort/m3u8?url=${originalUrl.encodeURL()}"

        println("原始 URL:     $originalUrl")
        println("代理 URL:     $proxiedUrl")

        // 验证代理 URL 格式正确
        assertTrue(proxiedUrl.startsWith("http://localhost:19876/m3u8?url="), "应以正确的代理前缀开头")

        // 验证中文被正确编码
        assertTrue(proxiedUrl.contains("%E4%B8%AD%E5%AD%97"), "中文应被编码为 UTF-8 百分比编码")

        // 验证没有乱码
        assertTrue(!proxiedUrl.contains("%4E2D"), "不应使用 Unicode 码点编码")
        assertTrue(!proxiedUrl.contains("%5B57"), "不应使用 Unicode 码点编码")
    }
}
