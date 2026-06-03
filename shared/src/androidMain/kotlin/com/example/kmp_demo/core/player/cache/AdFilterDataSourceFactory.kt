package com.example.kmp_demo.core.player.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * 广告过滤数据源工厂。
 *
 * 包装 ExoPlayer 的 [DataSource.Factory]，拦截所有请求进行内容嗅探，
 * 对 M3U8 播放列表在内存中用 [M3u8Sanitizer] 过滤广告切片，返回干净内容给 ExoPlayer。
 *
 * ## 工作原理
 *
 * ```
 * ExoPlayer
 *   └─ HlsMediaSource
 *        └─ AdFilterDataSourceFactory  ← 拦截层（包装 CacheDataSource.Factory）
 *             └─ CacheDataSource.Factory
 *                  └─ DefaultHttpDataSource.Factory
 * ```
 *
 * - **M3U8 请求**：内容嗅探（首行为 `#EXTM3U`）→ 用 Ktor 下载原始内容 → [M3u8Sanitizer] 过滤 → 返回干净内容
 * - **TS/MP4/M4S 切片请求**：URL 后缀检测 → 直接透传 upstream，零开销
 * - **二级 M3U8**（`#EXT-X-STREAM-INF` 引用的子 M3U8）：递归拦截，因为子 M3U8 的 URL 也会经过此工厂
 *
 * ## 复用 commonMain 代码
 *
 * - [AdSegmentFilter] 接口 — 直接复用
 * - [DefaultAdSegmentFilter] — 直接复用
 * - [M3u8Sanitizer] 清洗器 — 直接复用
 * - Ktor [HttpClient] — 直接复用
 *
 * @param upstreamFactory 上游数据源工厂（通常是 [CacheDataSource.Factory]）
 * @param m3u8Sanitizer M3U8 清洗器，复用 commonMain 的过滤逻辑
 * @param httpClient Ktor HTTP 客户端，用于下载原始 M3U8 内容
 */
@OptIn(UnstableApi::class)
class AdFilterDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val m3u8Sanitizer: M3u8Sanitizer,
    private val httpClient: HttpClient,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return AdFilterDataSource(
            upstream = upstreamFactory.createDataSource(),
            m3u8Sanitizer = m3u8Sanitizer,
            httpClient = httpClient,
        )
    }
}

/**
 * 广告过滤数据源。
 *
 * 对 [DataSource] 的包装，通过内容嗅探识别 M3U8 请求并进行广告过滤。
 *
 * ## 过滤策略
 *
 * 1. **URL 后缀快速过滤**：`.ts`、`.m4s`、`.mp4` 直接透传 upstream，零开销
 * 2. **内容嗅探**：其他 URL 用 Ktor 下载文本，检测首行是否为 `#EXTM3U`
 * 3. **广告过滤**：确认是 M3U8 后，用 [M3u8Sanitizer] 过滤广告切片行
 * 4. **回退机制**：嗅探/过滤失败时，回退到 upstream 放行，不影响播放
 *
 * @param upstream 上游数据源（TS/MP4 切片透传；M3U8 请求在嗅探/过滤失败时回退使用）
 * @param m3u8Sanitizer M3U8 清洗器
 * @param httpClient Ktor HTTP 客户端
 */
@OptIn(UnstableApi::class)
class AdFilterDataSource(
    private val upstream: DataSource,
    private val m3u8Sanitizer: M3u8Sanitizer,
    private val httpClient: HttpClient,
) : DataSource {

    private var currentUri: Uri? = null
    private var filteredStream: InputStream? = null
    private var isRealM3u8 = false // 动态标记

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val url = dataSpec.uri.toString()

        // 1. 如果是明显的二进制媒体数据块（ts / m4s / mp4），直接透传给 upstream，绝对不拦截
        if (url.contains(".ts") || url.contains(".m4s") || url.contains(".mp4")) {
            isRealM3u8 = false
            return upstream.open(dataSpec)
        }

        // 2. 任何不带明显媒体后缀的 API 链接，或者是 .m3u8，一律视为潜存的 M3U8 文本进行嗅探
        return try {
            // 用 Ktor 下载网络文本
            val rawContent = kotlinx.coroutines.runBlocking {
                httpClient.get(url).bodyAsText()
            }

            // 核心嗅探：不管你 URL 怎么伪装，只要文本第一行是 #EXTM3U，你就是 M3U8！
            if (rawContent.startsWith("#EXTM3U")) {
                isRealM3u8 = true

                // 喂给你的 commonMain 清洗器
                val adCount = m3u8Sanitizer.countAdSegments(rawContent, url)
                val cleanContent = if (adCount > 0) {
                    m3u8Sanitizer.sanitize(rawContent, url)
                } else {
                    rawContent
                }

                val bytes = cleanContent.toByteArray(Charsets.UTF_8)
                filteredStream = ByteArrayInputStream(bytes)
                bytes.size.toLong()
            } else {
                // 嗅探完发现不是 M3U8 文本（可能是黑产加了密的非标二级数据，或伪装的ts），交回给原生
                isRealM3u8 = false
                upstream.open(dataSpec)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdFilterDataSource", "嗅探/过滤失败，回退原生放行: $url", e)
            isRealM3u8 = false
            upstream.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (isRealM3u8) { // 严格跟着动态标记走
            val stream = filteredStream
            if (stream != null) {
                val bytesRead = stream.read(buffer, offset, length)
                return if (bytesRead == -1) C.RESULT_END_OF_INPUT else bytesRead
            }
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        filteredStream?.close()
        filteredStream = null
        isRealM3u8 = false
        upstream.close()
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }
}
