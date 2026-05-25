package com.example.kmp_demo.features.domestic.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult
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
                    val entities = items.map { it.toDomesticMedia() }
                    DomesticJvmFetchResult(
                        entities = entities,
                        isEndOfPagination = entities.isEmpty()
                    )
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
            val matchResult = domesticApi.searchFirstMatch(title)
            if (matchResult != null) {
                val media = matchResult.item.toDomesticMedia()
                // 将相对路径的封面图拼接为完整 URL
                val resolvedCoverUrl = resolveCoverUrl(media.coverUrl, matchResult.siteBaseUrl)
                Result.success(media.copy(coverUrl = resolvedCoverUrl))
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

    /**
     * 解析封面图 URL。
     *
     * 资源站返回的 vod_pic 可能是相对路径（如 "/upload/vod/xxx.jpg"），
     * 需要拼接站点的 base URL 才能形成完整 URL。
     * 如果已经是完整 URL（以 http:// 或 https:// 开头），则直接返回。
     *
     * @param coverUrl 原始封面图 URL（可能为相对路径）
     * @param siteBaseUrl 来源站点的 base URL（如 "https://example.com"）
     * @return 完整的封面图 URL
     */
    private fun resolveCoverUrl(coverUrl: String?, siteBaseUrl: String): String? {
        if (coverUrl == null || coverUrl.isBlank()) return null
        // 已经是完整 URL
        if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://")) {
            return coverUrl
        }
        // 相对路径，拼接站点的 base URL
        val baseUrl = siteBaseUrl.trimEnd('/')
        return if (coverUrl.startsWith("/")) {
            "$baseUrl$coverUrl"
        } else {
            "$baseUrl/$coverUrl"
        }
    }
}

/**
 * Domestic 模块 JVM 平台的分页结果。
 *
 * 与 commonMain 中的 [com.example.kmp_demo.features.domestic.data.remote.DomesticRemoteFetchResult] 保持相同的分页计算逻辑：
 * - nextKey = page + 1（每次递增 1 页）
 * - isEndOfPagination = 数据为空
 */
private data class DomesticJvmFetchResult(
    override val entities: List<DomesticMedia>,
    override val isEndOfPagination: Boolean
) : IRemoteFetchResult<DomesticMedia> {
    override fun computeNextKey(page: Int, pageSize: Int): Int = page + 1
}
