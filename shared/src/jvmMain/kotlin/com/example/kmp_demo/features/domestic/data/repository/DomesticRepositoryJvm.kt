package com.example.kmp_demo.features.domestic.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.map
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.data.remote.DomesticApi
import com.example.kmp_demo.features.domestic.data.remote.DomesticSearchEngine
import com.example.kmp_demo.features.domestic.data.remote.mapper.toDomesticMedia
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.model.DomesticMediaType
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.flow.Flow

/**
 * Desktop 版 DomesticRepository — 无 Room 缓存。
 *
 * 直接使用 [Pager] + [InMemoryPagingSource] 从网络加载分页数据。
 * 每次启动都是全新会话，不持久化任何数据。
 *
 * 与 Android 版 [DomesticRepositoryImpl] 的区别：
 * - Android: Room + RemoteMediator 驱动分页
 * - Desktop: InMemoryPagingSource 驱动分页，无缓存
 */
class DomesticRepositoryJvm(
    private val domesticApi: DomesticApi,
    private val searchEngine: DomesticSearchEngine,
) : DomesticRepository {

    override suspend fun getAvailableTypes(): List<String> {
        return domesticApi.discoverTypes()
    }

    override fun getRecentMediaPaging(typeName: String): Flow<PagingData<DomesticMedia>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val typeParam = if (typeName == "全部") null else typeName
                    val items = domesticApi.getRecentMedia(page = page, typeName = typeParam)
                    items.map { it.toDomesticMedia() }
                }
            }
        ).flow
    }

    override suspend fun search(keyword: String, page: Int): List<DomesticMedia> {
        val apiResults = domesticApi.search(keyword, page)
        if (apiResults.isNotEmpty()) {
            return apiResults.map { it.toDomesticMedia() }
        }
        return searchEngine.search(keyword)
    }

    override suspend fun getDetailMeta(title: String): Result<DomesticMedia> {
        return try {
            val apiItem = domesticApi.searchFirstMatch(title)
            if (apiItem != null) {
                Result.success(apiItem.toDomesticMedia())
            } else {
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
            searchEngine.search(title).flatMap { it.videoSources }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
