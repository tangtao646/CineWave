package com.example.kmp_demo.features.radio.data.local

import app.cash.paging.PagingSource
import com.example.kmp_demo.core.data.local.room.BaseLocalDataSourceImpl
import com.example.kmp_demo.core.data.local.room.CoreRemoteKeyDao
import com.example.kmp_demo.features.radio.data.local.model.RadioStationEntity

data class RadioQueryParameter(
    val tag: String,
    val countryCode: String
)

/**
 * 电台本地数据源 - 迁移至 Koin
 */
class RadioLocalDataSource(
    private val radioDao: RadioDao,
    remoteKeyDao: CoreRemoteKeyDao,
) : BaseLocalDataSourceImpl<RadioStationEntity, RadioQueryParameter>(remoteKeyDao, "radio") {
    override suspend fun clearData(key: RadioQueryParameter) {
        radioDao.clearRadioByCategory(key.tag)
    }

    override suspend fun insert(
        data: List<RadioStationEntity>,
        key: RadioQueryParameter
    ) {
        radioDao.insertStations(data)
    }

    override fun getPagingSource(key: RadioQueryParameter): PagingSource<Int, RadioStationEntity> {
        return radioDao.getStationsByCategory(key.tag, key.countryCode)
    }

    override suspend fun replaceData(
        data: List<RadioStationEntity>,
        key: RadioQueryParameter
    ) {
        radioDao.replaceData(data, key.tag)
    }

    fun getFavoritePagingSource(): PagingSource<Int, RadioStationEntity> {
        return radioDao.getFavoriteStationsPagingSource()
    }

    suspend fun toggleFavorite(uuid: String, isFavorite: Boolean) {
        radioDao.updateFavoriteStatus(uuid, isFavorite)
    }
}
