package com.example.kmp_demo.core.player.cache

/**
 * 广告被清洗后的时间轴处理策略
 */
enum class AdCleanStrategy {
    DROP_COMPLETELY, // 适用于桌面端 VLCJ（彻底移除广告行，无缝顺延）
    REPLACE_WITH_GAP // 适用于 Android 端 ExoPlayer（原地保留，转为 GAP 占位，闪跃通过）
}

/**
 * M3U8 播放列表清洗器 (至简回归·指纹微观过滤版)
 * * 核心设计哲学：
 * 1. 放弃所有基于时间、分块、分辨率的宏观盲猜算法，根除误杀和首播卡顿。
 * 2. 100% 信任并依赖 [AdSegmentFilter] 的域名黑名单与文本特征扫描。
 * 3. 完美保留原生 [#EXT-X-DISCONTINUITY] 标签，仅在广告真正被剔除时处理语法的平滑缝合。
 */
class M3u8Sanitizer(
    private val adSegmentFilter: AdSegmentFilter,
    private val cleanStrategy: AdCleanStrategy = AdCleanStrategy.DROP_COMPLETELY
) {

    /**
     * 单个媒体切片的平铺结构
     */
    private data class MediaSegment(
        val infLine: String?,
        val urlLine: String,
        val extraLines: List<String> = emptyList()
    )

    /**
     * 核心无损清洗入口（零网络I/O，微秒级纯文本处理）
     */
    fun sanitize(rawContent: String, m3u8BaseUrl: String): String {
        val lines = rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()

        var hasDroppedAnyAd = false
        var isFirstValidSegment = true

        var nextSegmentHasDiscontinuity = false
        var activeInfLine: String? = null
        val activeExtraLines = mutableListOf<String>()

        for (line in lines) {
            when {
                // 1. 全局头部标记直接写入
                (line.startsWith("#EXTM3U") ||
                        line.startsWith("#EXT-X-VERSION") ||
                        line.startsWith("#EXT-X-TARGETDURATION") ||
                        line.startsWith("#EXT-X-MEDIA-SEQUENCE") ||
                        line.startsWith("#EXT-X-PLAYLIST-TYPE")) -> {
                    result.add(line)
                }

                // 2. 捕获不连续性断层标签
                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    nextSegmentHasDiscontinuity = true
                }

                // 3. 捕获时长标签
                line.startsWith("#EXTINF:") -> {
                    activeInfLine = line
                }

                // 4. 捕获其他随带标签
                line.startsWith("#") -> {
                    if (!line.startsWith("#EXT-X-ENDLIST") && !line.startsWith("#EXT-X-GAP")) {
                        activeExtraLines.add(line)
                    }
                }

                // 5. 遭遇真实的 TS URL 行（触发核心审计与状态机流式缝合）
                else -> {
                    // 🌟 唯一裁决基准：交由 DefaultAdSegmentFilter 的黑名单，不盲猜时间，不探测分辨率
                    val isAd = adSegmentFilter.isAdSegment(line, m3u8BaseUrl)

                    if (isAd) {
                        // ========= 命中真正的域名黑名单广告 =========
                        hasDroppedAnyAd = true

                        if (cleanStrategy == AdCleanStrategy.REPLACE_WITH_GAP) {
                            // 【Android ExoPlayer 策略】：原地转为 GAP 占位，确保播放器 Timeline 总时长不塌陷
                            activeInfLine?.let { result.add(it) }
                            result.add("#EXT-X-GAP")
                        }
                        // 【Desktop VLCJ 策略】：直接丢弃，不写入 result，无缝顺延
                    } else {
                        // ========= 命中健康的纯正片切片 =========

                        // 原生断层标签自愈恢复逻辑
                        if (nextSegmentHasDiscontinuity && !isFirstValidSegment) {
                            if (cleanStrategy == AdCleanStrategy.DROP_COMPLETELY) {
                                // 如果紧挨着前面刚刚清洗掉了广告，为了防止 VLC 回放画面闪烁或回滚，
                                // 我们把这个由广告引发的断层顺带“抹平”；只有当纯原生的正片断层（前面没洗过广告）才予以放行。
                                if (!hasDroppedAnyAd) {
                                    ensureDiscontinuityTag(result)
                                }
                            } else {
                                // Android 端无条件信任并保留正片原生断层
                                ensureDiscontinuityTag(result)
                            }
                        }

                        // 写入安全的正片序列
                        isFirstValidSegment = false
                        result.addAll(activeExtraLines)
                        activeInfLine?.let { result.add(it) }
                        result.add(line)

                        // 重置状态
                        hasDroppedAnyAd = false
                    }

                    // 消费完毕，消费局部状态重置
                    nextSegmentHasDiscontinuity = false
                    activeInfLine = null
                    activeExtraLines.clear()
                }
            }
        }

        // 强力防跳尾保护锁：强制清除末尾由于裁剪可能残存的悬空断层标记
        while (result.isNotEmpty() && (result.last().startsWith("#EXT-X-DISCONTINUITY") || result.last().isEmpty())) {
            result.removeAt(result.lastIndex)
        }

        if (rawContent.contains("#EXT-X-ENDLIST") && !result.contains("#EXT-X-ENDLIST")) {
            result.add("#EXT-X-ENDLIST")
        }

        return result.joinToString("\n")
    }

    private fun ensureDiscontinuityTag(result: MutableList<String>) {
        if (result.isNotEmpty() && !result.last().startsWith("#EXT-X-DISCONTINUITY")) {
            result.add("#EXT-X-DISCONTINUITY")
        }
    }

    /**
     * 辅助统计：获取被域名黑名单捕获的广告数
     */
    fun countAdSegments(rawContent: String, m3u8BaseUrl: String): Int {
        val lines = rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var count = 0
        for (line in lines) {
            if (!line.startsWith("#") && adSegmentFilter.isAdSegment(line, m3u8BaseUrl)) {
                count++
            }
        }
        return count
    }
}