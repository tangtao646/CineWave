package com.example.kmp_demo.features.film.data.remote

import app.cash.paging.ExperimentalPagingApi
import com.example.kmp_demo.core.data.remote.BasePagingRemoteMediator
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult
import com.example.kmp_demo.features.film.data.local.FilmLocalDataSource
import com.example.kmp_demo.features.film.data.local.MovieEntity


@OptIn(ExperimentalPagingApi::class)
class FilmRemoteMediator(
    private val type: String,
    private val sortOrder: String,
    private val localDataSource: FilmLocalDataSource,
    private val fetchRemote: suspend (page: Int) -> FilmRemoteFetchResult
) : BasePagingRemoteMediator<MovieEntity, String>(baseLocalDataSource = localDataSource) {

    override val key: String get() = "${type}_$sortOrder"
    override val initialPage: Int get() = 1

    override suspend fun fetchRemoteData(
        key: String,
        page: Int,
        pageSize: Int
    ): IRemoteFetchResult<MovieEntity> {
        return fetchRemote(page)
    }
}

data class FilmRemoteFetchResult(
    override val entities: List<MovieEntity>,
    override val isEndOfPagination: Boolean
) : IRemoteFetchResult<MovieEntity> {
    override fun computeNextKey(page: Int, pageSize: Int): Int =
        page + (entities.size.coerceAtLeast(pageSize))
}
