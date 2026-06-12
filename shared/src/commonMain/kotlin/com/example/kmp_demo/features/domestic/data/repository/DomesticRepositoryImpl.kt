package com.example.kmp_demo.features.domestic.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.kmp_demo.core.data.remote.BasePagingRemoteMediator
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.data.local.DomesticLocalDataSource
import com.example.kmp_demo.features.domestic.data.local.DomesticMediaEntity
import com.example.kmp_demo.features.domestic.data.remote.DomesticApi
import com.example.kmp_demo.features.domestic.data.remote.DomesticRemoteFetchResult
import com.example.kmp_demo.features.domestic.data.remote.DomesticRemoteMediator
import com.example.kmp_demo.features.domestic.data.remote.DomesticSearchEngine
import com.example.kmp_demo.features.domestic.data.remote.mapper.toDomesticMedia
import com.example.kmp_demo.features.domestic.data.remote.mapper.toEntity
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.model.DomesticMediaType
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DomesticRepository 实现。
 *
 * 首页瀑布流使用 Paging 3 + Room 缓存：
 * - [Pager] + [DomesticRemoteMediator] 驱动分页加载
 * - [DomesticLocalDataSource] 提供 Room 缓存
 * - 缓存 1 小时内有效，过期自动刷新
 *
 * 搜索和详情通过 [DomesticSearchEngine]（基于 VideoSourceSearchEngine 的多站点并行搜索）。
 * 支持按 type_name 分类筛选。
 *
 * 详情页支持分阶段加载：
 * 1. [getDetailMeta] — 快速获取封面、简介等元数据（找到第一个匹配即返回）
 * 2. [getDetailSources] — 耗时获取播放源列表（多站点并行嗅探）
 *
 * 数据映射逻辑统一集中在 [DomesticMapper](composeApp/src/commonMain/kotlin/com/example/kmp_demo/features/domestic/data/remote/mapper/DomesticMapper.kt)。
 */
class DomesticRepositoryImpl(
    private val domesticApi: DomesticApi,
    private val searchEngine: DomesticSearchEngine,
    private val localDataSource: DomesticLocalDataSource,
) : DomesticRepository {

    override suspend fun getAvailableTypes(): List<String> {
        return domesticApi.discoverTypes()
    }

    override fun getRecentMediaPaging(typeName: String): Flow<PagingData<DomesticMedia>> {
        return createPager(typeName).flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomesticMedia() }
        }
    }

    override fun searchPaging(keyword: String): Flow<PagingData<DomesticMedia>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                object : androidx.paging.PagingSource<Int, DomesticMedia>() {
                    override fun getRefreshKey(state: androidx.paging.PagingState<Int, DomesticMedia>): Int? = null
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DomesticMedia> {
                        val page = params.key ?: 1
                        return try {
                            val results = search(keyword, page)
                            LoadResult.Page(
                                data = results,
                                prevKey = if (page == 1) null else page - 1,
                                nextKey = if (results.isEmpty()) null else page + 1
                            )
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                    }
                }
            }
        ).flow
    }

    @OptIn(ExperimentalPagingApi::class)
    private fun createPager(typeName: String): Pager<Int, DomesticMediaEntity> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
            ),
            remoteMediator = DomesticRemoteMediator(
                typeName = typeName,
                localDataSource = localDataSource,
                fetchRemote = { page, pageSize -> fetchDomesticMedia(typeName, page, pageSize) },
            ),
            pagingSourceFactory = { localDataSource.getPagingSource("domestic_$typeName") },
        )
    }

    private suspend fun fetchDomesticMedia(
        typeName: String,
        page: Int,
        pageSize: Int,
    ): DomesticRemoteFetchResult {
        val typeParam = if (typeName == "全部") null else typeName
        val items =
            domesticApi.getRecentMedia(page = page, typeName = typeParam).distinctBy { it.id }
        val entities = items.mapIndexed { index, item ->
            item.toEntity(
                typeName = typeName,
                orderIndex = ((page - 1) * pageSize + index).toLong(),
            )
        }
        return DomesticRemoteFetchResult(
            entities = entities,
            isEndOfPagination = entities.isEmpty(),
        )
    }

    override suspend fun search(keyword: String, page: Int): List<DomesticMedia> {
        // 优先使用 DomesticApi 搜索（带封面）
        val apiResults = domesticApi.search(keyword, page).distinctBy { it.id }
        if (apiResults.isNotEmpty()) {
            return apiResults.map { it.toDomesticMedia() }
        }

        // 降级：使用 VideoSourceSearchEngine 多站点搜索（无封面）
        return searchEngine.search(keyword)
    }

    override suspend fun getDetailMeta(title: String): Result<DomesticMedia> {
        return try {
            // 仅通过 DomesticApi 搜索获取封面图、简介等元数据（不含播放源）
            // 使用 searchFirstMatch 找到第一个匹配即返回，避免遍历所有站点
            val matchResult = domesticApi.searchFirstMatch(title)

            if (matchResult != null) {
                Result.success(matchResult.item.toDomesticMedia())
            } else {
                // 降级：返回仅有标题的占位媒体，让 UI 至少能展示标题
                Result.success(
                    DomesticMedia(
                        id = title.hashCode().toUInt().toString(16),
                        title = title,
                        coverUrl = null,
                        year = null,
                        area = null,
                        type = DomesticMediaType.DRAMA,
                        description = null,
                        remarks = null,
                        videoSources = emptyList(),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDetailSources(title: String): List<VideoSource> {
        return try {
            // 通过 VideoSourceSearchEngine 多站点并行搜索获取播放源
            searchEngine.search(title).flatMap { it.videoSources }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PAGE_SIZE = 10
    }
}
