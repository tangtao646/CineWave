package com.example.kmp_demo.core.data.local.room

import com.example.kmp_demo.core.data.local.BaseLocalDataSource
import kotlin.time.Clock

abstract class BaseLocalDataSourceImpl<T: Any, K: Any>(
    private val remoteKeyDao: CoreRemoteKeyDao,
    private val modulePrefix: String
) : BaseLocalDataSource<T, K> {

    protected fun getFullKey(key: K): String = "${modulePrefix}_${key.toString()}"

    override suspend fun getNextPage(key: K): Int? {
        return remoteKeyDao.getKey(getFullKey(key))?.nextPage
    }

    override suspend fun saveNextPage(key: K, nextPage: Int?) {
        remoteKeyDao.saveKey(
            RemoteKeyEntity(
                label = getFullKey(key),
                nextPage = nextPage,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun clear(key: K) {
        remoteKeyDao.clearKey(getFullKey(key))
        clearData(key)
    }

    override suspend fun getLastUpdated(key: K): Long {
        return remoteKeyDao.getKey(getFullKey(key))?.lastUpdated ?: 0L
    }

    abstract suspend fun clearData(key: K)
}
