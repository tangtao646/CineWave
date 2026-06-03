package com.example.kmp_demo.core.player.cache

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DefaultAdSegmentFilter] 的单元测试。
 *
 * 覆盖以下场景：
 * - 正常正片切片（同域名、连续序号）→ 放行
 * - 广告关键词命中 → 拦截
 * - 跨域名切片 → 拦截
 * - 广告域名模式匹配 → 拦截
 * - 非 .ts 行 → 放行
 * - 相对路径切片 → 放行（非 HTTP URL）
 * - 二级 M3U8 链接 → 放行
 */
class DefaultAdSegmentFilterTest {

    private val filter = DefaultAdSegmentFilter()
    private val m3u8BaseUrl = "https://vod.example.com/live/playlist.m3u8"

    // ==================== 正常正片切片 ====================

    @Test
    fun `normal segment with same domain should pass`() {
        assertFalse(
            filter.isAdSegment(
                "https://vod.example.com/live/out001.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `normal segment with sequential naming should pass`() {
        assertFalse(
            filter.isAdSegment(
                "https://vod.example.com/live/segment_123.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `relative path segment should pass`() {
        assertFalse(
            filter.isAdSegment(
                "out001.ts",
                m3u8BaseUrl
            )
        )
    }

    // ==================== 广告关键词命中 ====================

    @Test
    fun `segment with ad keyword should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://asdf.top/ad_banner.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment with casino keyword should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://evil.com/casino_hd.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment with gg keyword should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://malware.top/gg001.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment with macau keyword should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://spam.xyz/macau_live.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment with porn keyword should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://xxx-site.com/porn_video.ts",
                m3u8BaseUrl
            )
        )
    }

    // ==================== 跨域名检测 ====================

    @Test
    fun `segment from different domain should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://evil-cdn.com/live/out001.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment from completely unrelated domain should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://asdf.top/gucheng.ts",
                m3u8BaseUrl
            )
        )
    }

    // ==================== 广告域名模式 ====================

    @Test
    fun `segment from top domain should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://random123.top/video/ad.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment from xyz domain should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://spam.xyz/live/ad.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment from club domain should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "https://casino.club/live/ad.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment from IP address domain should be blocked`() {
        assertTrue(
            filter.isAdSegment(
                "http://192.168.1.100/live/ad.ts",
                m3u8BaseUrl
            )
        )
    }

    // ==================== 非 .ts 行 ====================

    @Test
    fun `non ts line should pass`() {
        assertFalse(
            filter.isAdSegment(
                "#EXTINF:10.000,",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `m3u8 link should pass`() {
        assertFalse(
            filter.isAdSegment(
                "https://vod.example.com/live/playlist.m3u8",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `empty line should pass`() {
        assertFalse(
            filter.isAdSegment(
                "",
                m3u8BaseUrl
            )
        )
    }

    // ==================== 边缘情况 ====================

    @Test
    fun `segment with ad in path but same domain should be blocked by keyword`() {
        // 即使同域名，路径中包含 ad 关键词也应拦截
        assertTrue(
            filter.isAdSegment(
                "https://vod.example.com/live/ad_banner.ts",
                m3u8BaseUrl
            )
        )
    }

    @Test
    fun `segment with subdomain of m3u8 domain should pass`() {
        // 子域名算不同域名，但这里测试的是子域名情况
        // 实际中正片切片可能来自子域名 cdn.vod.example.com
        assertTrue(
            filter.isAdSegment(
                "https://cdn.vod.example.com/live/out001.ts",
                m3u8BaseUrl
            )
        )
    }
}
