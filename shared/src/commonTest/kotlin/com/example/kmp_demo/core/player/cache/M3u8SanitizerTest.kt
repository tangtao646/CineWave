package com.example.kmp_demo.core.player.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class M3u8SanitizerTest {

    private val filter = DefaultAdSegmentFilter()
    private val sanitizerDrop = M3u8Sanitizer(filter, AdCleanStrategy.DROP_COMPLETELY)
    private val sanitizerGap = M3u8Sanitizer(filter, AdCleanStrategy.REPLACE_WITH_GAP)
    private val m3u8BaseUrl = "https://vod.example.com/live/playlist.m3u8"

    private fun syncSanitize(s: M3u8Sanitizer, content: String, base: String = m3u8BaseUrl): String =
        runBlocking { s.sanitize(content, base) }

    @Test fun `clean m3u8 unchanged`() {
        val input = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(input, syncSanitize(sanitizerDrop, input))
    }

    @Test fun `clean m3u8 zero ad count`() {
        val input = "#EXTM3U\n#EXTINF:10.000,\nhttps://vod.example.com/live/out001.ts\n#EXT-X-ENDLIST"
        assertEquals(0, sanitizerDrop.countAdSegments(input, m3u8BaseUrl))
    }

    @Test fun `ad keyword removes segment and extinf`() {
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
        assertEquals(expected, syncSanitize(sanitizerDrop, input))
    }

    @Test fun `multiple ads removed`() {
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
        assertEquals(expected, syncSanitize(sanitizerDrop, input))
        assertEquals(2, sanitizerDrop.countAdSegments(input, m3u8BaseUrl))
    }

    @Test fun `only ads returns headers`() {
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
        assertEquals(expected, syncSanitize(sanitizerDrop, input))
    }

    @Test fun `variant m3u8 unchanged`() {
        val input = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=720x480
            https://vod.example.com/live/low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720
            https://vod.example.com/live/medium.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5120000,RESOLUTION=1920x1080
            https://vod.example.com/live/high.m3u8
        """.trimIndent()
        assertEquals(input, syncSanitize(sanitizerDrop, input))
    }

    @Test fun `ad without extinf removed`() {
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
        assertEquals(expected, syncSanitize(sanitizerDrop, input))
    }

    @Test fun `empty returns empty`() {
        assertEquals("", syncSanitize(sanitizerDrop, ""))
        assertEquals(0, sanitizerDrop.countAdSegments("", m3u8BaseUrl))
    }

    @Test fun `ad zone at beginning removed`() {
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
        val r = syncSanitize(sanitizerDrop, m3u8)
        assertFalse(r.contains("ad_001.ts"))
        assertTrue(r.contains("#EXT-X-DISCONTINUITY"))
        assertTrue(r.contains("real_001.ts"))
    }

    @Test fun `ad zone in middle removed`() {
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
        val r = syncSanitize(sanitizerDrop, m3u8)
        assertTrue(r.contains("real_001.ts"))
        assertTrue(r.contains("real_003.ts"))
        assertFalse(r.contains("ad_001.ts"))
        assertTrue(r.contains("#EXT-X-DISCONTINUITY"))
    }

    @Test fun `normal large block preserved`() {
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
        val r = syncSanitize(sanitizerDrop, m3u8)
        for (i in 1..6) assertTrue(r.contains("seg_00$i.ts"))
        assertTrue(r.contains("#EXT-X-DISCONTINUITY"))
    }

    @Test fun `multiple ad zones removed`() {
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
        val r = syncSanitize(sanitizerDrop, m3u8)
        assertFalse(r.contains("ad_001.ts"))
        assertFalse(r.contains("ad_002.ts"))
        assertTrue(r.contains("real_001.ts"))
        assertTrue(r.contains("real_002.ts"))
    }

    @Test fun `GAP strategy writes GAP`() {
        val input = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/live/out001.ts
            #EXTINF:10.000,
            https://asdf.top/ad.ts
            #EXTINF:10.000,
            https://vod.example.com/live/out002.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val r = syncSanitize(sanitizerGap, input)
        assertTrue(r.contains("out001.ts"))
        assertTrue(r.contains("out002.ts"))
        assertFalse(r.contains("asdf.top"))
        assertTrue(r.contains("#EXT-X-GAP"))
    }

    @Test fun `no trailing DISCONTINUITY`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:10.000,
            real_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:5.000,
            ad_001.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val r = syncSanitize(sanitizerDrop, m3u8)
        val last = r.lines().lastOrNull { it.isNotBlank() } ?: ""
        assertFalse(last.contains("#EXT-X-DISCONTINUITY"))
    }

    @Test fun `countAdSegments mixed m3u8`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:5.000,
            https://asdf.top/ad1.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            https://vod.example.com/real_001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:5.000,
            https://malware.xyz/ad2.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            https://vod.example.com/real_002.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(2, sanitizerDrop.countAdSegments(m3u8, m3u8BaseUrl))
    }

    @Test fun `countAdSegments zero for clean`() {
        val m3u8 = """
            #EXTM3U
            #EXTINF:10.000,
            https://vod.example.com/seg_001.ts
            #EXTINF:10.000,
            https://vod.example.com/seg_002.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(0, sanitizerDrop.countAdSegments(m3u8, m3u8BaseUrl))
    }
}
