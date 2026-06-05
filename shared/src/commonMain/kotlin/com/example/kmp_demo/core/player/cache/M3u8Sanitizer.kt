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

    fun sanitize(rawContent: String, m3u8BaseUrl: String): String {
        val lines = rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()

        // 仅用于追踪当前切片之前的那个 #EXTINF 时长标签
        var activeInfLine: String? = null

        for (line in lines) {
            when {
                // 1. 仅仅拦截时长标签，不立刻写入，暂存起来等待与紧随其后的 TS 链接捆绑审计
                line.startsWith("#EXTINF:") -> {
                    activeInfLine = line
                }

                // 2. 核心裁决区：遭遇真实的媒体数据切片（非 # 开头的行，或显式包含 .ts）
                (!line.startsWith("#") || line.contains(".ts", ignoreCase = true)) -> {
                    // 100% 听从且仅听从 DefaultAdSegmentFilter 的黑名单域名裁决
                    val isAd = adSegmentFilter.isAdSegment(line, m3u8BaseUrl)

                    if (isAd) {
                        // 【命中广告】：根据平台差异化抹除或占位
                        if (cleanStrategy == AdCleanStrategy.REPLACE_WITH_GAP) {
                            activeInfLine?.let { result.add(it) }
                            result.add("#EXT-X-GAP")
                        }
                        // DROP_COMPLETELY 策略下，广告时长行和 URL 行直接物理蒸发，代码不写入任何内容
                    } else {
                        // 【命中正片】：将暂存的时长行（如有）和当前切片完整、安全地写入
                        activeInfLine?.let { result.add(it) }
                        result.add(line)
                    }

                    // 消费完毕，清空当前切片的依附状态
                    activeInfLine = null
                }

                // 3. 安全放行阀：所有非切片、非时长的公共标签（VERSION, KEY解密, MAP, DISCONTINUITY等）
                // 我们不理解、不关心的东西，一律原样直接放行，杜绝任何株连误杀
                line.startsWith("#") -> {
                    // 过滤掉原本存在或动态生成的标签，防止双重冲突
                    if (!line.startsWith("#EXT-X-ENDLIST") && !line.startsWith("#EXT-X-GAP")) {
                        result.add(line)
                    }
                }

                // 4. 兜底文本放行
                else -> {
                    result.add(line)
                }
            }
        }

        // 🌟 生产级刚需：全量还原 VOD 视频的点播闭环结束标记，防止 ExoPlayer/VLC 播放到末尾卡死或识别为直播
        if (rawContent.contains("#EXT-X-ENDLIST") && !result.contains("#EXT-X-ENDLIST")) {
            result.add("#EXT-X-ENDLIST")
        }

        return result.joinToString("\n")
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