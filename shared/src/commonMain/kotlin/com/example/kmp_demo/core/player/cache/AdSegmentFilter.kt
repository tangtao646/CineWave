package com.example.kmp_demo.core.player.cache

/**
 * 广告切片检测策略接口。
 *
 * 定义 M3U8 播放列表中广告 .ts 切片的检测契约。
 * 支持多种策略实现（关键词、域名、正则、AI 模型等），
 * 通过策略模式实现可扩展性。
 *
 * ## 设计原则
 *
 * - **单一职责**：只负责"判断一行 URL 是否为广告切片"
 * - **开闭原则**：新增检测策略无需修改现有代码，只需实现此接口
 * - **无状态**：实现类应为无状态单例，可被并发调用
 *
 * ## 内置实现
 *
 * - [DefaultAdSegmentFilter]：基于关键词 + 域名 + 正则的默认实现
 *
 * ## 使用示例
 *
 * ```kotlin
 * val filter: AdSegmentFilter = DefaultAdSegmentFilter()
 * val isAd = filter.isAdSegment("https://asdf.top/gucheng.ts", "https://vod.example.com/live.m3u8")
 * ```
 */
interface AdSegmentFilter {

    /**
     * 判断给定的 URL 行是否为广告切片。
     *
     * @param line M3U8 文件中的一行文本（通常是 .ts 切片 URL）
     * @param m3u8BaseUrl 原始 M3U8 播放列表的 URL，用于提取"合法"域名做对比
     * @return true 表示该行是广告切片，应被过滤
     */
    fun isAdSegment(line: String, m3u8BaseUrl: String): Boolean
}
