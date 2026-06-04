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
 *
 * ## 协议状态机测试（新增）
 *
 * - 片头广告区间（DISCONTINUITY 前短区间）→ 整段切除
 * - 片中插播广告区间（DISCONTINUITY 包裹的短区间）→ 切除
 * - 正常长 DISCONTINUITY 区间（>31s）→ 保留，零误杀
 * - 多重广告区间 → 全部切除
 * - 无 DISCONTINUITY 的 M3U8 → 回退特征规则层
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

    // ==================== 包含广告切片（特征规则层） ====================

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

    // ==================== 多个广告切片（特征规则层） ====================

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

    // ==================== 全部是广告（特征规则层） ====================

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

    // ==================== 协议状态机：片头广告区间 ====================

    @Test
    fun `should remove ad zone at beginning`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:5.000,
            ad_001.ts
            #EXTINF:5.000,
            ad_002.ts
            #EXTINF:5.000,
            ad_003.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            real_001.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(m3u8, "https://vod.example.com/play.m3u8")

        assertFalse(result.contains("ad_001.ts"))
        assertFalse(result.contains("ad_002.ts"))
        assertFalse(result.contains("ad_003.ts"))
        assertFalse(result.contains("#EXT-X-DISCONTINUITY"))
        assertTrue(result.contains("real_001.ts"))
    }

    // ==================== 协议状态机：片中插播广告区间 ====================

    @Test
    fun `should remove ad zone in middle`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:10.000,
            real_001.ts
            #EXTINF:10.000,
            real_002.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:5.000,
            ad_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            real_003.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(m3u8, "https://vod.example.com/play.m3u8")

        assertTrue(result.contains("real_001.ts"))
        assertTrue(result.contains("real_002.ts"))
        assertTrue(result.contains("real_003.ts"))
        assertFalse(result.contains("ad_001.ts"))
    }

    // ==================== 协议状态机：正常长区间（零误杀） ====================

    @Test
    fun `should not remove normal discontinuity`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:10.000,
            seg_001.ts
            #EXTINF:10.000,
            seg_002.ts
            #EXTINF:10.000,
            seg_003.ts
            #EXTINF:10.000,
            seg_004.ts
            #EXTINF:10.000,
            seg_005.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            seg_006.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(m3u8, "https://vod.example.com/play.m3u8")

        // 所有正片切片都应保留
        assertTrue(result.contains("seg_001.ts"))
        assertTrue(result.contains("seg_002.ts"))
        assertTrue(result.contains("seg_003.ts"))
        assertTrue(result.contains("seg_004.ts"))
        assertTrue(result.contains("seg_005.ts"))
        assertTrue(result.contains("seg_006.ts"))
        // DISCONTINUITY 标签也应保留（因为前面的区间不是广告）
        assertTrue(result.contains("#EXT-X-DISCONTINUITY"))
    }

    // ==================== 协议状态机：多重广告区间 ====================

    @Test
    fun `should handle multiple ad zones`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:5.000,
            ad_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            real_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:5.000,
            ad_002.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            real_002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = sanitizer.sanitize(m3u8, "https://vod.example.com/play.m3u8")

        assertFalse(result.contains("ad_001.ts"))
        assertFalse(result.contains("ad_002.ts"))
        assertTrue(result.contains("real_001.ts"))
        assertTrue(result.contains("real_002.ts"))
    }

    // ==================== 协议状态机：无 DISCONTINUITY 回退特征规则层 ====================

    @Test
    fun `should fallback to ad filter when no discontinuity`() {
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

    // ==================== 协议状态机：countAdSegments 统计 ====================

    @Test
    fun `countAdSegments should return ad segment count for discontinuity zones`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:5.000,
            ad_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            real_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:5.000,
            ad_002.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            real_002.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        // 两个广告区间，每个区间1个切片，共2个广告切片
        assertEquals(2, sanitizer.countAdSegments(m3u8, "https://vod.example.com/play.m3u8"))
    }

    @Test
    fun `countAdSegments should return zero for normal discontinuity`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:10.000,
            seg_001.ts
            #EXTINF:10.000,
            seg_002.ts
            #EXTINF:10.000,
            seg_003.ts
            #EXTINF:10.000,
            seg_004.ts
            #EXTINF:10.000,
            seg_005.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            seg_006.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals(0, sanitizer.countAdSegments(m3u8, "https://vod.example.com/play.m3u8"))
    }

    // ==================== DiscontinuityZone 单元测试 ====================

    @Test
    fun `discontinuity zone with short duration should be likely ad`() {
        val zone = DiscontinuityZone(
            startLine = 0,
            endLine = 4,
            totalDuration = 15.0,
            segmentCount = 3,
        )
        assertTrue(zone.isLikelyAd)
    }

    @Test
    fun `discontinuity zone with long duration should not be likely ad`() {
        val zone = DiscontinuityZone(
            startLine = 0,
            endLine = 10,
            totalDuration = 60.0,
            segmentCount = 6,
        )
        assertFalse(zone.isLikelyAd)
    }

    @Test
    fun `discontinuity zone with too many segments should not be likely ad`() {
        val zone = DiscontinuityZone(
            startLine = 0,
            endLine = 10,
            totalDuration = 30.0,
            segmentCount = 10,
        )
        assertFalse(zone.isLikelyAd)
    }

    @Test
    fun `discontinuity zone with very short duration should not be likely ad`() {
        val zone = DiscontinuityZone(
            startLine = 0,
            endLine = 2,
            totalDuration = 1.0,
            segmentCount = 1,
        )
        assertFalse(zone.isLikelyAd)
    }
}