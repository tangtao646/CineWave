package com.example.kmp_demo.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult

/**
 * JVM 平台的内存分页数据源。
 *
 * 使用 [IRemoteFetchResult] 抽象分页计算逻辑，与 commonMain 的 RemoteMediator 保持一致。
 * 每个 feature 通过实现 [IRemoteFetchResult] 来定义自己的分页策略（nextKey 计算、结束判断等）。
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
