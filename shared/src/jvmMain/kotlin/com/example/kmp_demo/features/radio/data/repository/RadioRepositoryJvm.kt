package com.example.kmp_demo.features.radio.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult
import com.example.kmp_demo.features.radio.data.remote.IpApiService
import com.example.kmp_demo.features.radio.data.remote.RadioApiService
import com.example.kmp_demo.features.radio.data.remote.dto.CountryDto
import com.example.kmp_demo.features.radio.data.remote.mapper.toDomain
import com.example.kmp_demo.features.radio.data.remote.mapper.toEntity
import com.example.kmp_demo.features.radio.domain.model.RadioStation
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import kotlinx.coroutines.flow.Flow

/**
 * Desktop 版 RadioRepository — 无 Room 缓存。
 *
 * 电台数据直接从网络获取，不持久化。
 * 收藏功能使用内存存储。
 *
 * 与 Android 版 [RadioRepositoryImpl] 的区别：
 * - Android: Room + RemoteMediator 驱动分页
 * - Desktop: InMemoryPagingSource 驱动分页，无缓存
 */
class RadioRepositoryJvm(
    private val radioApiService: RadioApiService,
    private val ipApiService: IpApiService,
) : RadioRepository {

    private val favoriteIds = mutableSetOf<String>()
    // 全局去重：记录所有已加载的 stationUuid，防止跨页重复
    private val seenUuids = mutableSetOf<String>()

    override fun getStations(
        category: String,
        countryCode: String
    ): Flow<PagingData<RadioStation>> {
        // 每次切换分类/国家时重置去重集合
        seenUuids.clear()
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 2
            ),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    // 电台 API 使用 offset 偏移量分页，而非页码
                    // page 从 1 开始，转换为 offset = (page - 1) * pageSize
                    val offset = (page - 1) * pageSize
                    val remoteStations = radioApiService.searchStations(
                        name = category,
                        countryCode = countryCode,
                        offset = offset,
                        limit = pageSize
                    )
                    // 全局去重：过滤掉已见过的 uuid，防止 LazyColumn key 冲突
                    val entities = remoteStations
                        .filter { seenUuids.add(it.stationUuid) }
                        .map { it.toEntity(category).toDomain() }
                    RadioJvmFetchResult(
                        entities = entities,
                        isEndOfPagination = entities.isEmpty()
                    )
                }
            }
        ).flow
    }

    override suspend fun searchStations(query: String, countryCode: String?): List<RadioStation> {
        return try {
            radioApiService.searchStations(name = query, countryCode = countryCode).map { dto ->
                RadioStation(
                    uuid = dto.stationUuid,
                    name = dto.name,
                    streamUrl = dto.url,
                    favicon = dto.favicon ?: "",
                    tags = dto.tags?.split(",") ?: emptyList(),
                    countryCode = dto.countryCode ?: "",
                    language = dto.language ?: "",
                    category = ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun toggleFavorite(uuid: String, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteIds.add(uuid)
        } else {
            favoriteIds.remove(uuid)
        }
    }

    override fun getFavoriteStations(): Flow<PagingData<RadioStation>> {
        // Desktop 端收藏使用内存存储，返回空分页数据
        return Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource<RadioStation> { _, _ ->
                    RadioJvmFetchResult(
                        entities = emptyList(),
                        isEndOfPagination = true
                    )
                }
            }
        ).flow
    }

    override suspend fun getCurrentCountryCode(): String {
        return try {
            ipApiService.getCurrentLocation().countryCode
        } catch (e: Exception) {
            "CN"
        }
    }

    override suspend fun getCountries(): List<CountryDto> {
        return try {
            radioApiService.getCountries()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Radio 模块 JVM 平台的分页结果。
 *
 * 与 commonMain 中的 [com.example.kmp_demo.features.radio.data.remote.RadioRemoteFetchResult] 保持相同的分页计算逻辑：
 * - nextKey = page + entities.size（按实际返回数量推进）
 * - isEndOfPagination = 数据为空
 */
private data class RadioJvmFetchResult(
    override val entities: List<RadioStation>,
    override val isEndOfPagination: Boolean
) : IRemoteFetchResult<RadioStation> {
    override fun computeNextKey(page: Int, pageSize: Int): Int =
        page + (entities.size.coerceAtLeast(pageSize))
}
