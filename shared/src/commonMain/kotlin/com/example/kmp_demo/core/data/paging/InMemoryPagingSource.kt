package com.example.kmp_demo.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult

/**
 * 通用的内存分页数据源。
 *
 * 使用 [IRemoteFetchResult] 抽象分页计算逻辑，支持各平台无数据库缓存的分页场景。
 *
 * @param T 数据类型
 * @param fetchPage 分页加载函数，返回 [IRemoteFetchResult] 以统一分页计算逻辑
 */
class InMemoryPagingSource<T : Any>(
    private val fetchPage: suspend (page: Int, pageSize: Int) -> IRemoteFetchResult<T>
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val result = fetchPage(page, params.loadSize)

            LoadResult.Page(
                data = result.entities,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (result.isEndOfPagination) null else result.computeNextKey(page, params.loadSize)
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

/**
 * 简单的分页结果实现，适用于大多数基于页码（Page-based）的 API。
 */
data class SimpleFetchResult<T : Any>(
    override val entities: List<T>,
    override val isEndOfPagination: Boolean
) : IRemoteFetchResult<T> {
    override fun computeNextKey(page: Int, pageSize: Int): Int = page + 1
}
