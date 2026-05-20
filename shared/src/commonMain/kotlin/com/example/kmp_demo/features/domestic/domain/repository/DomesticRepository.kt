package com.example.kmp_demo.features.domestic.domain.repository

import androidx.paging.PagingData
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import kotlinx.coroutines.flow.Flow

/**
 * 国内影视 Repository 接口。
 *
 * 国内板块不依赖 TMDB 分页 API，基于资源站 API (ffzyapi) 获取数据。
 * 支持多站点并行查询、去重合并，以及按分类筛选。
 *
 * 首页瀑布流使用 Paging 3 + Room 缓存：
 * - [getRecentMediaPaging] 返回 [Flow]<[PagingData]<[DomesticMedia]>>，支持分页加载
 * - 缓存由 [DomesticRemoteMediator] 自动管理
 */
interface DomesticRepository {
    /** 发现所有活跃站点中可用的分类名称列表 */
    suspend fun getAvailableTypes(): List<String>

    /**
     * 获取最近更新的影视列表（首页瀑布流），支持 Paging 分页 + Room 缓存。
     *
     * @param typeName 可选分类名称（如"国产剧"、"综艺"），"全部" 表示不过滤
     * @return PagingData Flow，由 Pager + RemoteMediator 驱动
     */
    fun getRecentMediaPaging(typeName: String = "全部"): Flow<PagingData<DomesticMedia>>

    /** 根据关键词搜索国内影视内容 */
    suspend fun search(keyword: String, page: Int = 1): List<DomesticMedia>

    /**
     * 根据标题获取元数据（封面、简介、年份等，不含播放源）。
     * 使用 Result 包装，便于 ViewModel 链式调用。
     */
    suspend fun getDetailMeta(title: String): Result<DomesticMedia>

    /**
     * 根据标题获取播放源列表（耗时操作，多站点并行嗅探）。
     */
    suspend fun getDetailSources(title: String): List<VideoSource>
}
