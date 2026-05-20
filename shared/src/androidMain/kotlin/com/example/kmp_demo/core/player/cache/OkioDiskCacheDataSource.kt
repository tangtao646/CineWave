package com.example.kmp_demo.core.player.cache

import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import okio.BufferedSource
import okio.IOException
import okio.buffer

/**
 * 基于 Okio [Source] 的 ExoPlayer (Media3) DataSource 实现。
 *
 * 将 ExoPlayer 的底层数据请求无缝桥接到 KMP 的 [DiskLruCache] 上，
 * 通过流式读取（[DiskLruCache.getSource]）避免大视频分片导致 OOM。
 *
 * 设计要点：
 * - 继承 [BaseDataSource]（isNetwork=false），标记为非网络数据源
 * - 支持 ExoPlayer 的 Seek 操作（通过 [BufferedSource.skip] 实现位置偏移）
 * - 支持 [C.LENGTH_UNSET] 表示读取到 EOF
 * - 线程安全：所有 DiskLruCache 调用通过 [runBlocking] 桥接协程
 *
 * @param diskCache KMP 跨平台磁盘缓存实例
 */
class OkioDiskCacheDataSource(
    private val diskCache: DiskLruCache
) : BaseDataSource(/* isNetwork= */ false) {

    private var currentSource: BufferedSource? = null
    private var openedUri: android.net.Uri? = null
    private var bytesRemaining: Long = 0

    /**
     * 打开数据源，准备读取。
     *
     * @param dataSpec ExoPlayer 的数据请求规格，包含 URI、位置偏移、长度等
     * @return 数据长度（[C.LENGTH_UNSET] 表示未知长度，读到 EOF 为止）
     * @throws IOException 缓存未命中或读取失败时抛出
     */
    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val key = dataSpec.uri.toString()
        openedUri = dataSpec.uri

        // 从跨平台磁盘缓存获取流
        val source = runBlocking { diskCache.getSource(key) }
            ?: throw IOException("Cache miss for key: $key")

        val bufferedSource = source.buffer()

        // 处理 ExoPlayer 的 Seek 导致的位置偏移
        if (dataSpec.position > 0) {
            try {
                bufferedSource.skip(dataSpec.position)
            } catch (e: Exception) {
                bufferedSource.close()
                throw IOException("Failed to skip to position ${dataSpec.position}", e)
            }
        }

        currentSource = bufferedSource
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            C.LENGTH_UNSET.toLong() // 读到 EOF 为止
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    /**
     * 从当前数据源读取数据到缓冲区。
     *
     * @param buffer 目标字节数组
     * @param offset 写入偏移
     * @param length 期望读取长度
     * @return 实际读取的字节数，-1 表示到达末尾
     * @throws IOException 读取失败时抛出
     */
    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val source = currentSource ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length.toLong()
        } else {
            minOf(length.toLong(), bytesRemaining)
        }.toInt()

        val bytesRead = try {
            source.read(buffer, offset, bytesToRead)
        } catch (e: java.io.IOException) {
            throw IOException(e)
        }

        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    /**
     * 返回当前打开的 URI。
     */
    override fun getUri(): android.net.Uri? = openedUri

    /**
     * 关闭数据源，释放资源。
     */
    override fun close() {
        openedUri = null
        try {
            currentSource?.close()
        } catch (_: Exception) {
            // 关闭异常不影响主流程
        } finally {
            currentSource = null
            transferEnded()
        }
    }
}
