package com.example.kmp_demo.features.radio.data.repository

import app.cash.paging.ExperimentalPagingApi
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.map
import com.example.kmp_demo.core.data.remote.BasePagingRemoteMediator
import com.example.kmp_demo.features.radio.data.local.RadioLocalDataSource
import com.example.kmp_demo.features.radio.data.local.RadioQueryParameter
import com.example.kmp_demo.features.radio.data.local.model.RadioStationEntity
import com.example.kmp_demo.features.radio.data.remote.IpApiService
import com.example.kmp_demo.features.radio.data.remote.RadioApiService
import com.example.kmp_demo.features.radio.data.remote.dto.CountryDto
import com.example.kmp_demo.features.radio.data.remote.mapper.toDomain
import com.example.kmp_demo.features.radio.data.remote.mapper.toEntity
import com.example.kmp_demo.features.radio.domain.model.RadioStation
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class RadioRemoteMediator(
    private val radioApiService: RadioApiService,
    radioLocalDataSource: RadioLocalDataSource,
    private val category: String,
    private val countryCode: String
) : BasePagingRemoteMediator<RadioStationEntity, RadioQueryParameter>(baseLocalDataSource = radioLocalDataSource) {

    override val key: RadioQueryParameter
        get() = RadioQueryParameter(category, countryCode)
    override val initialPage: Int
        get() = 0

    override suspend fun fetchRemoteData(
        key: RadioQueryParameter,
        page: Int,
        pageSize: Int
    ): RemoteFetchResult<RadioStationEntity> {
        val remoteStations = radioApiService.searchStations(
            name = key.tag,
            countryCode = key.countryCode,
            offset = page,
            limit = pageSize
        )

        val entities = remoteStations.map { dto ->
            dto.toEntity(category = key.tag)
        }

        return RemoteFetchResult(
            entities = entities,
            isEndOfPagination = entities.size < pageSize
        )
    }
}

class RadioRepositoryImpl(
    private val radioApiService: RadioApiService,
    private val ipApiService: IpApiService,
    private val localDataSource: RadioLocalDataSource
) : RadioRepository {

    @OptIn(ExperimentalPagingApi::class)
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
            remoteMediator = RadioRemoteMediator(radioApiService, localDataSource, category, countryCode),
            pagingSourceFactory = {
                localDataSource.getPagingSource(
                    RadioQueryParameter(category, countryCode)
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
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
        localDataSource.toggleFavorite(uuid, isFavorite)
    }

    override fun getFavoriteStations(): Flow<PagingData<RadioStation>> {
        return Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { localDataSource.getFavoritePagingSource() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
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
