package com.example.kmp_demo.features.film.data.remote

import com.example.kmp_demo.core.videosource.VideoSourceSearchEngine
import com.example.kmp_demo.core.videosource.domain.VideoSource

/**
 * 视频源嗅探数据源。
 *
 * 根据影片标题搜索可用的播放源。
 * 内部委托给 [VideoSourceSearchEngine] 执行多站点并行搜索。
 *
 * 职责单一：作为数据源层入口，协调搜索流程。
 */
class SnifferDataSource(
    private val searchEngine: VideoSourceSearchEngine,
) {

    /**
     * 根据影片标题搜索播放源。
     *
     * @param title 影片标题
     * @return 去重后的播放源列表
     */
    suspend fun searchSources(title: String): List<VideoSource> {
        return searchEngine.search(title)
    }
}
