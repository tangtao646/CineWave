package com.example.kmp_demo.features.film.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieResponseDto(
    @SerialName("page") val page: Int,
    @SerialName("results") val results: List<MovieDto>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int
)

@Serializable
data class MovieDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("overview") val overview: String,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("release_date") val releaseDate: String?,
    @SerialName("genre_ids") val genreIds: List<Int>,
    @SerialName("adult") val adult: Boolean = false,
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    @SerialName("popularity") val popularity: Double = 0.0,
    @SerialName("video") val video: Boolean = false,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("softcore") val softcore: Boolean = false
)

@Serializable
data class MovieDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("overview") val overview: String,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("release_date") val releaseDate: String?,
    @SerialName("genres") val genres: List<GenreDto>,
    @SerialName("runtime") val runtime: Int?,
    @SerialName("tagline") val tagline: String?,
    @SerialName("credits") val credits: CreditsDto?,
    @SerialName("adult") val adult: Boolean = false,
)

@Serializable
data class GenreResponseDto(
    @SerialName("genres") val genres: List<GenreDto>
)

@Serializable
data class GenreDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String
)

@Serializable
data class CreditsDto(
    @SerialName("cast") val cast: List<CastDto>
)

@Serializable
data class CastDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("profile_path") val profilePath: String?,
    @SerialName("character") val character: String
)
