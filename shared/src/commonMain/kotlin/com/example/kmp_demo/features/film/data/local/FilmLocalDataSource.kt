package com.example.kmp_demo.features.film.data.local

import androidx.paging.PagingSource
import com.example.kmp_demo.core.data.local.room.BaseLocalDataSourceImpl
import com.example.kmp_demo.core.data.local.room.CoreRemoteKeyDao

class FilmLocalDataSource(
    private val filmDao: FilmDao,
    remoteKeyDao: CoreRemoteKeyDao
) : BaseLocalDataSourceImpl<MovieEntity, String>(remoteKeyDao, "film") {

    override suspend fun insert(data: List<MovieEntity>, key: String) {
        filmDao.insertMovies(data)
    }

    override fun getPagingSource(key: String): PagingSource<Int, MovieEntity> {
        return filmDao.getMoviesPagingSource(key)
    }

    override suspend fun clearData(key: String) {
        filmDao.clearMovies(key)
    }

    override suspend fun replaceData(data: List<MovieEntity>, key: String) {
        filmDao.replaceData(key, data)
    }
}
