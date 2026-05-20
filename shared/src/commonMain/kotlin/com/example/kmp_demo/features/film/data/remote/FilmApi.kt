package com.example.kmp_demo.features.film.data.remote

import com.example.kmp_demo.features.film.data.remote.dto.GenreResponseDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieDetailDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.util.date.getTimeMillis
import kotlinx.datetime.Clock

class FilmApi(private val client: HttpClient) {

    suspend fun getPopularMovies(page: Int, apiKey: String = API_KEY, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}movie/popular") {
            parameter("api_key", apiKey)
            parameter("page", page)
            parameter("language", lang)
            parameter("_nocache", getTimeMillis())
        }.body()
    }

    suspend fun searchMovies(query: String, page: Int, apiKey: String = API_KEY, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}search/movie") {
            parameter("api_key", apiKey)
            parameter("query", query)
            parameter("page", page)
            parameter("language", lang)
            parameter("_nocache", getTimeMillis())
        }.body()
    }

    suspend fun getMovieDetail(movieId: Int, apiKey: String = API_KEY, append: String = "credits", lang: String = "zh-CN"): MovieDetailDto {
        return client.get("${BASE_URL}movie/$movieId") {
            parameter("api_key", apiKey)
            parameter("append_to_response", append)
            parameter("language", lang)
        }.body()
    }

    suspend fun getMovieGenres(apiKey: String = API_KEY, lang: String = "zh-CN"): GenreResponseDto {
        return client.get("${BASE_URL}genre/movie/list") {
            parameter("api_key", apiKey)
            parameter("language", lang)
        }.body()
    }

    suspend fun getMoviesByGenre(genreId: String, page: Int, sortBy: String = "popularity.desc", apiKey: String = API_KEY, lang: String = "zh-CN"): MovieResponseDto {
        return client.get("${BASE_URL}discover/movie") {
            parameter("api_key", apiKey)
            parameter("with_genres", genreId)
            parameter("page", page)
            parameter("sort_by", sortBy)
            parameter("language", lang)
            parameter("_nocache", getTimeMillis())
        }.body()
    }

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val API_KEY = "fb731f9431f7b0dfc7f66667b834d8dc"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/original"
    }
}
