package com.example.kmp_demo.core.data.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingState

/**
 * JVM 平台的内存分页数据源。


 */
class InMemoryPagingSource<T : Any>(
    private val fetchPage: suspend (page: Int, pageSize: Int) -> List<T>
) : PagingSource<Int, T>() {

    private val cache = mutableMapOf<Int, List<T>>()

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val newData = fetchPage(page, params.loadSize)
            cache[page] = newData
            // 全局去重：对所有已加载页面的数据按 hashCode/equals 去重
            val allData = cache.entries.sortedBy { it.key }.flatMap { it.value }
            val deduplicated = mutableListOf<T>()
            val seen = mutableSetOf<T>()
            for (item in allData) {
                if (seen.add(item)) {
                    deduplicated.add(item)
                }
            }
            LoadResult.Page(
                data = deduplicated,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (newData.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }


}
