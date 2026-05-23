package com.example.kmp_demo.features.radio.data.remote

import com.example.kmp_demo.core.data.remote.BasePagingRemoteMediator
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult
import com.example.kmp_demo.features.radio.data.local.RadioLocalDataSource
import com.example.kmp_demo.features.radio.data.local.RadioQueryParameter
import com.example.kmp_demo.features.radio.data.local.model.RadioStationEntity

class RadioRemoteMediator(
    radioLocalDataSource: RadioLocalDataSource,
    private val category: String,
    private val countryCode: String,
    private val fetchRemote: suspend (page: Int, pageSize: Int, param: RadioQueryParameter) -> RadioRemoteFetchResult
) : BasePagingRemoteMediator<RadioStationEntity, RadioQueryParameter>(baseLocalDataSource = radioLocalDataSource) {

    override val key: RadioQueryParameter
        get() = RadioQueryParameter(category, countryCode)
    override val initialPage: Int
        get() = 0

    override suspend fun fetchRemoteData(
        key: RadioQueryParameter,
        page: Int,
        pageSize: Int
    ): IRemoteFetchResult<RadioStationEntity> {
        return fetchRemote(page, pageSize, key)

    }
}

data class RadioRemoteFetchResult(
    override val entities: List<RadioStationEntity>,
    override val isEndOfPagination: Boolean
) : IRemoteFetchResult<RadioStationEntity> {
    override fun computeNextKey(page: Int, pageSize: Int): Int =
        page + (entities.size.coerceAtLeast(pageSize))
}
