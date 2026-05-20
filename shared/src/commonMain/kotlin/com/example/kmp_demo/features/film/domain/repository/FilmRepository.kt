package com.example.kmp_demo.features.film.domain.repository

import androidx.paging.PagingData
import com.example.kmp_demo.features.film.data.remote.dto.GenreDto
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.model.MovieSortOrder
import com.example.kmp_demo.features.film.domain.model.VideoSource
import kotlinx.coroutines.flow.Flow

interface FilmRepository {
    fun getPopularMovies(): Flow<PagingData<Movie>>
    fun searchMovies(query: String): Flow<PagingData<Movie>>
    fun getMoviesByGenre(genreId: String, sortOrder: MovieSortOrder = MovieSortOrder.VOTE_AVERAGE_DESC): Flow<PagingData<Movie>>
    suspend fun getMovieDetail(movieId: Int): Result<MovieDetail>
    suspend fun getMovieGenres(): Result<List<GenreDto>>
    
    /**
     * 根据影片信息搜索播放源
     */
    suspend fun searchVideoSources(title: String): List<VideoSource>
}
