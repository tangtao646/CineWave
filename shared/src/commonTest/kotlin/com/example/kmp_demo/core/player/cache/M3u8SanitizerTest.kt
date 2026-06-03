package com.example.kmp_demo.core.player.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [M3u8Sanitizer] 的单元测试。
 *
 * 覆盖以下场景：
 * - 无广告的干净 M3U8 → 内容不变
 * - 包含广告切片的 M3U8 → 广告行及其 #EXTINF 被移除
 * - 多个广告切片 → 全部移除
 * - 只有广告切片 → 返回空播放列表
 * - 二级 M3U8（#EXT-X-STREAM-INF）→ 不受影响
 * - countAdSegments 统计准确性
 */
class M3u8SanitizerTest {

    private val filter = DefaultAdSegmentFilter()
    private val sanitizer = M3u8Sanitizer(filter)
    private val m3u8BaseUrl = "https://vod.example.com/live/playlist.m3u8"

    // ==================== 干净 M3U8 ====================

    @Test
    fun `clean m3u8 should remain unchanged`() {
        val input = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out003.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(input, m3u8BaseUrl)
        assertEquals(input, result)
    }

    @Test
    fun `clean m3u8 should have zero ad count`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals(0, sanitizer.countAdSegments(input, m3u8BaseUrl))
    }

    // ==================== 包含广告切片 ====================

    @Test
    fun `m3u8 with ad segment should remove ad and its extinf`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://asdf.top/gucheng.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val expected = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(input, m3u8BaseUrl)
        assertEquals(expected, result)
    }

    @Test
    fun `m3u8 with ad segment should report correct ad count`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://asdf.top/gucheng.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals(1, sanitizer.countAdSegments(input, m3u8BaseUrl))
    }

    // ==================== 多个广告切片 ====================

    @Test
    fun `m3u8 with multiple ad segments should remove all ads`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://asdf.top/ad1.ts
            #EXTINF:10.000,
            https://malware.xyz/casino.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val expected = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(input, m3u8BaseUrl)
        assertEquals(expected, result)
    }

    @Test
    fun `m3u8 with multiple ad segments should report correct ad count`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://asdf.top/ad1.ts
            #EXTINF:10.000,
            https://malware.xyz/casino.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals(2, sanitizer.countAdSegments(input, m3u8BaseUrl))
    }

    // ==================== 全部是广告 ====================

    @Test
    fun `m3u8 with only ad segments should return only headers`() {
        val input = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:10.000,
            https://asdf.top/ad1.ts
            #EXTINF:10.000,
            https://malware.xyz/ad2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val expected = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(input, m3u8BaseUrl)
        assertEquals(expected, result)
    }

    // ==================== 二级 M3U8 ====================

    @Test
    fun `variant m3u8 with stream inf should remain unchanged`() {
        val input = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=720x480
            https://vod.example.com/live/low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720
            https://vod.example.com/live/medium.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5120000,RESOLUTION=1920x1080
            https://vod.example.com/live/high.m3u8
        """.trimIndent()

        val result = sanitizer.sanitize(input, m3u8BaseUrl)
        assertEquals(input, result)
    }

    // ==================== 广告切片没有 #EXTINF ====================

    @Test
    fun `ad segment without extinf should still be removed`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            https://asdf.top/gucheng.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val expected = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(input, m3u8BaseUrl)
        assertEquals(expected, result)
    }

    // ==================== 空内容 ====================

    @Test
    fun `empty m3u8 should return empty string`() {
        val result = sanitizer.sanitize("", m3u8BaseUrl)
        assertEquals("", result)
    }

    @Test
    fun `empty m3u8 should have zero ad count`() {
        assertEquals(0, sanitizer.countAdSegments("", m3u8BaseUrl))
    }
}
