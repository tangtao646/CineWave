package com.example.kmp_demo.core.data.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingState

/**
 * JVM 平台的内存分页数据源。
 *
 * 替代 Room PagingSource，数据直接通过 [fetchPage] 回调从网络获取，
 * 并在内存中缓存已加载的页面。
 *
 * 使用场景：Desktop 端不需要持久化缓存，每次启动都是全新会话。
 *
 * @param T 数据类型
 * @param fetchPage 分页加载回调，(page, pageSize) -> List<T>
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
            val data = cache.getOrPut(page) { fetchPage(page, params.loadSize) }
            LoadResult.Page(
                data = data,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /** 清除内存缓存并触发刷新 */
    fun invalidateCache() {
        cache.clear()
        invalidate()
    }
}
