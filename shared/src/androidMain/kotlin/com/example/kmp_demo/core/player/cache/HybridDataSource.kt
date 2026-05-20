package com.example.kmp_demo.core.player.cache

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 核心复合调度器 —— 混合数据源。
 *
 * 在 ExoPlayer 的每次数据请求时，先询问 KMP 的 [DiskLruCache] 是否包含该 URL，
 * 如果命中则走本地 [OkioDiskCacheDataSource] 流式读取，否则无缝回退到原生 HTTP 网络栈。
 *
 * 设计要点：
 * - 实现 [DataSource] 接口，可直接作为 ExoPlayer 的 DataSource.Factory 产物使用
 * - 每次 [open] 调用时动态决策：缓存命中 → 本地流；缓存未命中 → 网络回退
 * - 网络数据源由外部注入（如 [DefaultHttpDataSource]），保持配置灵活性
 * - 线程安全：DiskLruCache 的 contains 查询通过 [runBlocking] 桥接协程
 *
 * @param diskCache KMP 跨平台磁盘缓存实例
 * @param networkDataSource 原生 HTTP 网络数据源（缓存未命中时回退）
 */
class HybridDataSource(
    private val diskCache: DiskLruCache,
    private val networkDataSource: DataSource
) : DataSource {

    private var activeDataSource: DataSource? = null

    /**
     * 打开数据源。
     *
     * 决策逻辑：
     * 1. 查询 DiskLruCache 是否包含该 URL
     * 2. 命中 → 创建 [OkioDiskCacheDataSource] 作为活跃数据源
     * 3. 未命中 → 使用 [networkDataSource] 作为活跃数据源
     *
     * @param dataSpec ExoPlayer 的数据请求规格
     * @return 数据长度
     * @throws IOException 打开失败时抛出
     */
    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val key = dataSpec.uri.toString()

        // 关键点：询问 KMP 磁盘缓存是否包含这个 URL (TS分片或M3U8)
        val hasCache = runBlocking { diskCache.contains(key) }

        activeDataSource = if (hasCache) {
            // 命中：走本地 Okio 流
            OkioDiskCacheDataSource(diskCache)
        } else {
            // 没命中：无缝退回到原生 HTTP 网络栈下载
            networkDataSource
        }

        return activeDataSource!!.open(dataSpec)
    }

    /**
     * 从当前活跃数据源读取数据。
     *
     * @param buffer 目标字节数组
     * @param offset 写入偏移
     * @param length 期望读取长度
     * @return 实际读取的字节数，-1 表示到达末尾
     * @throws IOException 读取失败时抛出
     */
    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource?.read(buffer, offset, length) ?: -1
    }

    /**
     * 返回当前活跃数据源的 URI。
     */
    override fun getUri(): android.net.Uri? = activeDataSource?.uri

    /**
     * 关闭当前活跃数据源。
     */
    @Throws(IOException::class)
    override fun close() {
        activeDataSource?.close()
        activeDataSource = null
    }

    /**
     * 添加传输监听器到网络数据源。
     * 本地缓存读取不触发网络监听器。
     */
    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        networkDataSource.addTransferListener(transferListener)
    }
}
