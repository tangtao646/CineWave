package com.example.kmp_demo.features.film.data.remote

import com.example.kmp_demo.core.network.commonHeaders
import com.example.kmp_demo.core.network.userAgent
import com.example.kmp_demo.features.film.data.remote.dto.GenreResponseDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieDetailDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * TMDB API 客户端。
 *
 * 封装了电影列表、详情、搜索及分类获取。
 */
class FilmApi(
    private val client: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
) {

    /**
     * 提取通用的 HTTP 配置（API Key、语言、Headers）。
     */
    private suspend fun HttpRequestBuilder.applyCommonConfig(lang: String) {
        parameter("api_key", apiKeyProvider.getApiKey())
        parameter("language", lang)
        commonHeaders()
        userAgent()
    }

    suspend fun getPopularMovies(page: Int, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}movie/popular") {
            applyCommonConfig(lang)
            parameter("page", page)
        }.body()
    }

    suspend fun searchMovies(query: String, page: Int, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}search/movie") {
            applyCommonConfig(lang)
            parameter("query", query)
            parameter("page", page)
        }.body()
    }

    suspend fun getMovieDetail(
        movieId: Int,
        append: String = "credits",
        lang: String = "zh-CN"
    ): MovieDetailDto {
        return client.get("${BASE_URL}movie/$movieId") {
            applyCommonConfig(lang)
            parameter("append_to_response", append)
        }.body()
    }

    suspend fun getMovieGenres(lang: String = "zh-CN"): GenreResponseDto {
        return client.get("${BASE_URL}genre/movie/list") {
            applyCommonConfig(lang)
        }.body()
    }

    suspend fun getMoviesByGenre(
        genreId: String,
        page: Int,
        sortBy: String = "popularity.desc",
        lang: String = "zh-CN"
    ): MovieResponseDto {
        return client.get("${BASE_URL}discover/movie") {
            applyCommonConfig(lang)
            parameter("with_genres", genreId)
            parameter("page", page)
            parameter("sort_by", sortBy)
        }.body()
    }

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/original"
    }
}
