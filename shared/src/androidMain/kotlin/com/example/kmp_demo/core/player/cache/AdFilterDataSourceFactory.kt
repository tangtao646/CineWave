package com.example.kmp_demo.core.player.cache

import android.net.Uri
import android.util.Log
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
 * 2. **广谱嗅探**：无 `.ts/.m4s/.mp4` 后缀的 URL 都进入嗅探通道，不限于 `.m3u8` 后缀
 * 3. **广告过滤**：确认是 M3U8 后，用 [M3u8Sanitizer] 过滤广告切片行
 * 4. **带重试的容错机制**：嗅探/过滤失败时，连续失败 N 次后才回退放行，避免脏数据进缓存
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

    companion object {
        private const val TAG = "AdFilterDataSource"
        /** 最大连续失败次数：超过此值才回退放行，防止脏数据进缓存 */
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    private var currentUri: Uri? = null
    private var filteredStream: InputStream? = null
    private var isM3u8Confirmed = false
    private var consecutiveFailures = 0

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val url = dataSpec.uri.toString()
        isM3u8Confirmed = false

        // 广谱嗅探：无 .ts/.m4s/.mp4 后缀的 URL 都进入嗅探通道
        if (shouldSniff(url)) {
            return openWithSniff(url, dataSpec)
        }

        // 明显的二进制媒体数据块，直接透传
        return upstream.open(dataSpec)
    }

    /**
     * 判断是否需要对该 URL 进行内容嗅探。
     *
     * 广谱策略：不限于 `.m3u8` 后缀，只要不是已知的媒体切片后缀就进行嗅探。
     * 这能捕获那些 URL 伪装成非标准路径的 M3U8 请求。
     */
    private fun shouldSniff(url: String): Boolean {
        val lower = url.lowercase()
        return !lower.contains(".ts") &&
               !lower.contains(".m4s") &&
               !lower.contains(".mp4")
    }

    /**
     * 打开 URL 并进行内容嗅探和广告过滤。
     *
     * 带重试机制的容错处理：
     * - 连续失败 [MAX_CONSECUTIVE_FAILURES] 次后才回退放行
     * - 失败次数不足时抛异常触发 ExoPlayer 重试
     * - 宁可抛异常触发重试，也不放行未经验证的内容
     */
    private fun openWithSniff(url: String, dataSpec: DataSpec): Long {
        return try {
            // 用 Ktor 下载网络文本
            val rawContent = kotlinx.coroutines.runBlocking {
                httpClient.get(url).bodyAsText()
            }

            // 核心嗅探：首行是 #EXTM3U 才确认是 M3U8
            if (!rawContent.trimStart().startsWith("#EXTM3U")) {
                // 嗅探完发现不是 M3U8 文本，透传
                isM3u8Confirmed = false
                consecutiveFailures = 0
                return upstream.open(dataSpec)
            }

            // 确认是 M3U8，重置失败计数
            isM3u8Confirmed = true
            consecutiveFailures = 0

            // 用 M3u8Sanitizer 过滤广告
            val adCount = m3u8Sanitizer.countAdSegments(rawContent, url)
            val cleanContent = if (adCount > 0) {
                Log.d(TAG, "广告过滤: 移除了 $adCount 个广告区间, url=$url")
                m3u8Sanitizer.sanitize(rawContent, url)
            } else {
                rawContent
            }

            val bytes = cleanContent.toByteArray(Charsets.UTF_8)
            filteredStream = ByteArrayInputStream(bytes)
            bytes.size.toLong()
        } catch (e: Exception) {
            consecutiveFailures++
            Log.e(TAG, "嗅探/过滤失败 ($consecutiveFailures/$MAX_CONSECUTIVE_FAILURES): $url", e)

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                // 连续失败 N 次后回退放行，避免播放卡死
                Log.w(TAG, "连续失败 $consecutiveFailures 次，回退原生放行: $url")
                isM3u8Confirmed = false
                upstream.open(dataSpec)
            } else {
                // 失败次数不足，抛异常触发 ExoPlayer 重试
                throw java.io.IOException(
                    "M3U8 Filter temporary failure ($consecutiveFailures/$MAX_CONSECUTIVE_FAILURES), will retry",
                    e,
                )
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (isM3u8Confirmed) {
            val stream = filteredStream
            if (stream != null) {
                val bytesRead = stream.read(buffer, offset, length)
                return if (bytesRead == -1) C.RESULT_END_OF_INPUT else bytesRead
            }
            return C.RESULT_END_OF_INPUT
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        filteredStream?.close()
        filteredStream = null
        isM3u8Confirmed = false
        consecutiveFailures = 0
        upstream.close()
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }
}