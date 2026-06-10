package com.example.kmp_demo.features.film.data.remote

import com.example.kmp_demo.core.network.commonHeaders
import com.example.kmp_demo.core.network.userAgent
import com.example.kmp_demo.features.film.data.remote.dto.GenreResponseDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieDetailDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class FilmApi(
    private val client: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
) {

    suspend fun getPopularMovies(page: Int, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}movie/popular") {
            parameter("api_key", apiKeyProvider.getApiKey())
            parameter("page", page)
            parameter("language", lang)
            commonHeaders()
            userAgent()
        }.body()
    }

    suspend fun searchMovies(query: String, page: Int, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}search/movie") {
            parameter("api_key", apiKeyProvider.getApiKey())
            parameter("query", query)
            parameter("page", page)
            parameter("language", lang)
            commonHeaders()
            userAgent()
        }.body()
    }

    suspend fun getMovieDetail(
        movieId: Int,
        append: String = "credits",
        lang: String = "zh-CN"
    ): MovieDetailDto {
        return client.get("${BASE_URL}movie/$movieId") {
            parameter("api_key", apiKeyProvider.getApiKey())
            parameter("append_to_response", append)
            parameter("language", lang)
            commonHeaders()
            userAgent()
        }.body()
    }

    suspend fun getMovieGenres(lang: String = "zh-CN"): GenreResponseDto {
        return client.get("${BASE_URL}genre/movie/list") {
            parameter("api_key", apiKeyProvider.getApiKey())
            parameter("language", lang)
            commonHeaders()
            userAgent()
        }.body()
    }

    suspend fun getMoviesByGenre(
        genreId: String,
        page: Int,
        sortBy: String = "popularity.desc",
        lang: String = "zh-CN"
    ): MovieResponseDto {
        return client.get("${BASE_URL}discover/movie") {
            parameter("api_key", apiKeyProvider.getApiKey())
            parameter("with_genres", genreId)
            parameter("page", page)
            parameter("sort_by", sortBy)
            parameter("language", lang)
            commonHeaders()
            userAgent()
        }.body()
    }

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/original"
    }
}
