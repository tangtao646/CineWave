package com.example.kmp_demo.features.radio.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
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

    override fun getStations(
        category: String,
        countryCode: String
    ): Flow<PagingData<RadioStation>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 2
            ),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val remoteStations = radioApiService.searchStations(
                        name = category,
                        countryCode = countryCode,
                        offset = page,
                        limit = pageSize
                    )
                    remoteStations.map { it.toEntity(category).toDomain() }
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
                InMemoryPagingSource<RadioStation> { _, _ -> emptyList() }
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
