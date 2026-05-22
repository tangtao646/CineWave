package com.example.kmp_demo.features.domestic.data.remote

import app.cash.paging.ExperimentalPagingApi
import com.example.kmp_demo.core.data.remote.BasePagingRemoteMediator
import com.example.kmp_demo.features.domestic.data.local.DomesticLocalDataSource
import com.example.kmp_demo.features.domestic.data.local.DomesticMediaEntity

/**
 * 国内影视首页 RemoteMediator。
 *
 * 数据加载逻辑通过 [fetchRemote] 回调反转到 Repository 层，
 * RemoteMediator 只负责"何时加载、如何缓存"。
 *
 * @param typeName 分类名称（如"国产剧"、"综艺"），"全部" 表示不过滤
 * @param localDataSource 本地数据源
 * @param fetchRemote Repository 层注入的数据加载回调
 */
@OptIn(ExperimentalPagingApi::class)
class DomesticRemoteMediator(
    private val typeName: String,
    localDataSource: DomesticLocalDataSource,
    private val fetchRemote: suspend (page: Int, pageSize: Int) -> RemoteFetchResult<DomesticMediaEntity>,
) : BasePagingRemoteMediator<DomesticMediaEntity, String>(
    baseLocalDataSource = localDataSource
) {
    override val key: String get() = "domestic_$typeName"
    override val initialPage: Int get() = 1

    override fun computeNextKey(
        page: Int,
        pageSize: Int,
        result: RemoteFetchResult<DomesticMediaEntity>
    ): Int {
        return page + 1
    }

    override suspend fun fetchRemoteData(
        key: String,
        page: Int,
        pageSize: Int,
    ): RemoteFetchResult<DomesticMediaEntity> {
        return fetchRemote(page, pageSize)
    }
}
