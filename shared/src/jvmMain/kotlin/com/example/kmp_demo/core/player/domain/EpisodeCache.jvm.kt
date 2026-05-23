package com.example.kmp_demo.core.player.domain

/**
 * JVM 端剧集列表缓存。
 *
 * 由于 Desktop 端未使用 Jetpack Navigation，无法通过 [previousBackStackEntry]
 * 共享 ViewModel 来传递 [EpisodeInfo] 列表。本组件作为轻量级单例缓存，
 * 在详情页导航到播放器页时暂存剧集列表，播放器页读取后消费。
 *
 * ## 使用方式
 * 1. 详情页调用 [EpisodeCache.put] 缓存剧集列表
 * 2. 播放器页调用 [EpisodeCache.get] 读取并消费（消费后自动清空）
 *
 * ## 线程安全
 * 使用 [kotlinx.coroutines.sync.Mutex] 保证并发安全。
 *
 * ## 生命周期
 * 缓存为一次性消费设计，读取后自动清空，避免内存泄漏。
 */
object EpisodeCache {
    @Volatile
    private var cachedEpisodes: List<EpisodeInfo> = emptyList()

    /**
     * 缓存剧集列表。
     *
     * @param episodes 剧集列表
     */
    fun put(episodes: List<EpisodeInfo>) {
        cachedEpisodes = episodes
    }

    /**
     * 获取并消费缓存的剧集列表。
     *
     * 读取后自动清空缓存，确保一次性消费语义。
     *
     * @return 缓存的剧集列表（可能为空）
     */
    fun get(): List<EpisodeInfo> {
        val episodes = cachedEpisodes
        cachedEpisodes = emptyList()
        return episodes
    }

    /**
     * 清空缓存。
     */
    fun clear() {
        cachedEpisodes = emptyList()
    }
}
