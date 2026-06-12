package com.example.kmp_demo.features.film.data.repository

import androidx.paging.*
import com.example.kmp_demo.core.data.remote.BasePagingRemoteMediator
import com.example.kmp_demo.core.data.remote.IRemoteFetchResult
import com.example.kmp_demo.features.film.data.local.FilmLocalDataSource
import com.example.kmp_demo.features.film.data.local.MovieEntity
import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.data.remote.FilmPagingSource
import com.example.kmp_demo.features.film.data.remote.FilmRemoteFetchResult
import com.example.kmp_demo.features.film.data.remote.FilmRemoteMediator
import com.example.kmp_demo.features.film.data.remote.SnifferDataSource
import com.example.kmp_demo.features.film.data.remote.dto.GenreDto
import com.example.kmp_demo.features.film.data.remote.mapper.toEntity
import com.example.kmp_demo.features.film.data.remote.mapper.toMovie
import com.example.kmp_demo.features.film.data.remote.mapper.toMovieDetail
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.model.MovieSortOrder
import com.example.kmp_demo.features.film.domain.model.VideoSource
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FilmRepositoryImpl(
    private val api: FilmApi,
    private val localDataSource: FilmLocalDataSource,
    private val snifferDataSource: SnifferDataSource,
) : FilmRepository {

    override fun searchMovies(query: String): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { FilmPagingSource(api, "search", query) }
        ).flow
    }

    override suspend fun getMovieDetail(movieId: Int): Result<MovieDetail> {
        return try {
            val response = api.getMovieDetail(movieId)
            Result.success(response.toMovieDetail())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMovieGenres(): Result<List<GenreDto>> {
        return try {
            val response = api.getMovieGenres()
            Result.success(response.genres)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchVideoSources(title: String): List<VideoSource> {
        return snifferDataSource.searchSources(title)
    }

    override fun getPopularMovies(): Flow<PagingData<Movie>> {
        return createPager(
            type = "popular",
            sortOrder = "",
            fetchRemote = { page ->
                val response = api.getPopularMovies(
                    page = page,
                )
                val entities = response.results.map { it.toEntity("popular_") }
                FilmRemoteFetchResult(
                    entities,
                    entities.isEmpty() || page >= response.totalPages
                )
            }
        ).flow.map { pagingData -> pagingData.map { it.toMovie() } }
    }

    override fun getMoviesByGenre(
        genreId: String,
        sortOrder: MovieSortOrder
    ): Flow<PagingData<Movie>> {
        val type = "genre_$genreId"
        return createPager(
            type = type,
            sortOrder = sortOrder.value,
            fetchRemote = { page ->
                val response =
                    api.getMoviesByGenre(genreId = genreId, page = page, sortBy = sortOrder.value)
                val entities = response.results.map { it.toEntity("${type}_${sortOrder.value}") }
                FilmRemoteFetchResult(
                    entities,
                    entities.isEmpty() || page >= response.totalPages
                )
            }
        ).flow.map { pagingData -> pagingData.map { it.toMovie() } }
    }

    @OptIn(ExperimentalPagingApi::class)
    private fun createPager(
        type: String,
        sortOrder: String,
        fetchRemote: suspend (Int) -> FilmRemoteFetchResult
    ): Pager<Int, MovieEntity> {
        val compositeKey = "${type}_$sortOrder"
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = FilmRemoteMediator(type, sortOrder, localDataSource, fetchRemote),
            pagingSourceFactory = { localDataSource.getPagingSource(compositeKey) }
        )
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
