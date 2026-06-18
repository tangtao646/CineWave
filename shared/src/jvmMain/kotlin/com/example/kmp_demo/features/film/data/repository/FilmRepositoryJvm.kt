package com.example.kmp_demo.features.film.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.core.data.paging.SimpleFetchResult
import com.example.kmp_demo.features.film.data.remote.FilmApi
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
 * 统一使用通用的 [InMemoryPagingSource] 驱动分页，移除了逻辑冗余的 FilmPagingSource。
 * 与 Android 版的区别在于其直接使用 API 数据而不经过 Room 本地缓存。
 */
class FilmRepositoryJvm(
    private val api: FilmApi,
    private val snifferDataSource: SnifferDataSource,
) : FilmRepository {

    override fun getPopularMovies(): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, _ ->
                    val response = api.getPopularMovies(page = page)
                    val entities = response.results
                        .map { it.toMovie() }
                        .distinctBy { it.id }
                        .filter { !it.isAdult }
                    SimpleFetchResult(
                        entities = entities,
                        isEndOfPagination = page >= response.totalPages || response.results.isEmpty()
                    )
                }
            }
        ).flow
    }

    override fun searchMovies(query: String): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, _ ->
                    val response = api.searchMovies(query = query, page = page)
                    val entities = response.results
                        .map { it.toMovie() }
                        .distinctBy { it.id }
                        .filter { !it.isAdult }
                    SimpleFetchResult(
                        entities = entities,
                        isEndOfPagination = page >= response.totalPages || response.results.isEmpty()
                    )
                }
            }
        ).flow
    }

    override fun getMoviesByGenre(
        genreId: String,
        sortOrder: MovieSortOrder
    ): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, _ ->
                    val response = api.getMoviesByGenre(
                        genreId = genreId,
                        page = page,
                        sortBy = sortOrder.value
                    )
                    val entities = response.results
                        .map { it.toMovie() }
                        .distinctBy { it.id }
                        .filter { !it.isAdult }
                    SimpleFetchResult(
                        entities = entities,
                        isEndOfPagination = page >= response.totalPages || response.results.isEmpty()
                    )
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

    companion object {
        private const val PAGE_SIZE = 20
    }
}
