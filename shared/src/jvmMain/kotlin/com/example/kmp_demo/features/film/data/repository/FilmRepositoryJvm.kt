package com.example.kmp_demo.features.film.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.data.remote.FilmPagingSource
import com.example.kmp_demo.features.film.data.remote.SnifferDataSource
import com.example.kmp_demo.features.film.data.remote.dto.GenreDto
import com.example.kmp_demo.features.film.data.remote.mapper.toMovie
import com.example.kmp_demo.features.film.data.remote.mapper.toMovieDetail
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.model.MovieSortOrder
import com.example.kmp_demo.features.film.domain.model.VideoSource
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.flow.Flow

/**
 * Desktop 版 FilmRepository — 无 Room 缓存。
 *
 * 热门/分类浏览使用 [Pager] + [InMemoryPagingSource]，
 * 搜索使用 [FilmPagingSource]（它本身不依赖 Room）。
 *
 * 与 Android 版 [FilmRepositoryImpl] 的区别：
 * - Android: Room + RemoteMediator 驱动分页
 * - Desktop: InMemoryPagingSource 驱动分页，无缓存
 */
class FilmRepositoryJvm(
    private val api: FilmApi,
    private val snifferDataSource: SnifferDataSource,
) : FilmRepository {

    override fun getPopularMovies(): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val response = api.getPopularMovies(page = page)
                    response.results.map { it.toMovie() }
                }
            }
        ).flow
    }

    override fun searchMovies(query: String): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { FilmPagingSource(api, "search", query) }
        ).flow
    }

    override fun getMoviesByGenre(
        genreId: String,
        sortOrder: MovieSortOrder
    ): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val response = api.getMoviesByGenre(
                        genreId = genreId,
                        page = page,
                        sortBy = sortOrder.value
                    )
                    response.results.map { it.toMovie() }
                }
            }
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
}
