package com.example.kmp_demo.core.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.kmp_demo.core.data.local.BaseLocalDataSource
import kotlin.time.Clock

@OptIn(ExperimentalPagingApi::class)
abstract class BasePagingRemoteMediator<Value : Any, K : Any>(
    private val cacheTimeoutHours: Long = 1,
    private val baseLocalDataSource: BaseLocalDataSource<Value, K>
) : RemoteMediator<Int, Value>() {

    protected abstract val key: K
    protected abstract val initialPage: Int

    protected suspend fun getLastUpdated(key: K): Long {
        return baseLocalDataSource.getLastUpdated(key)
    }

    protected suspend fun getNextPage(key: K): Int? {
        return baseLocalDataSource.getNextPage(key)
    }

    protected suspend fun saveNextPage(key: K, nextPage: Int?) {
        baseLocalDataSource.saveNextPage(key, nextPage)
    }

    protected suspend fun insertData(key: K, entities: List<Value>) {
        baseLocalDataSource.insert(entities, key)
    }

    protected suspend fun replaceData(key: K, entities: List<Value>) {
        baseLocalDataSource.replaceData(entities, key)
    }


    protected abstract suspend fun fetchRemoteData(
        key: K,
        page: Int,
        pageSize: Int
    ): IRemoteFetchResult<Value>



    override suspend fun initialize(): InitializeAction {
        val cacheTimeoutMillis = cacheTimeoutHours * 60 * 60 * 1000L
        val lastUpdated = getLastUpdated(key)
        return if (Clock.System.now().toEpochMilliseconds() - lastUpdated <= cacheTimeoutMillis)
            InitializeAction.SKIP_INITIAL_REFRESH
        else
            InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Value>): MediatorResult {
        return try {
            val page = when (loadType) {
                LoadType.REFRESH -> initialPage
                LoadType.PREPEND -> return MediatorResult.Success(true)
                LoadType.APPEND -> getNextPage(key) ?: return MediatorResult.Success(true)
            }

            val pageSize = state.config.pageSize
            val result = fetchRemoteData(key, page, pageSize)

            if (loadType == LoadType.REFRESH) {
                replaceData(key, result.entities)
            } else if (result.entities.isNotEmpty()) {
                insertData(key, result.entities)
            }

            val nextKey = if (result.isEndOfPagination) null else result.computeNextKey(
                page,
                pageSize
            )
            saveNextPage(key, nextKey)

            MediatorResult.Success(endOfPaginationReached = result.isEndOfPagination)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}

interface IRemoteFetchResult<Value : Any> {
    val entities: List<Value>
    val isEndOfPagination: Boolean
    fun computeNextKey(page: Int, pageSize: Int): Int
}

