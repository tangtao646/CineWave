package com.example.kmp_demo.features.film.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kmp_demo.features.film.data.remote.mapper.toMovie
import com.example.kmp_demo.features.film.domain.model.Movie

class FilmPagingSource(
    private val api: FilmApi,
    private val type: String, // "popular", "search", or "genre"
    private val query: String? = null
) : PagingSource<Int, Movie>() {

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val page = params.key ?: 1
        return try {
            val response = when (type) {
                "popular" -> api.getPopularMovies(page = page)
                "search" -> api.searchMovies(query = query ?: "", page = page)
                "genre" -> api.getMoviesByGenre(genreId = query ?: "", page = page)
                else -> api.getPopularMovies(page = page)
            }
            
            val movies = response.results.map { it.toMovie() }
            
            LoadResult.Page(
                data = movies,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (movies.isEmpty() || page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
