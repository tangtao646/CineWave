package com.example.kmp_demo.features.film.domain.model

import kotlin.math.roundToInt

enum class MovieSortOrder(val value: String, val label: String) {
    VOTE_AVERAGE_DESC("vote_average.desc", "按评分排序"),
    RELEASE_DATE_DESC("primary_release_date.desc", "按上映日期")
}

data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val voteAverage: Double,
    val releaseDate: String,
    val genreIds: List<Int> = emptyList(),
    val isAdult: Boolean = false
){
    private val roundedValue = (voteAverage * 10).roundToInt() / 10.0
    val voteAverageTxt = roundedValue.toString()
}

data class MovieDetail(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val voteAverage: Double,
    val releaseDate: String,
    val genres: List<String>,
    val runtime: Int,
    val tagline: String?,
    val cast: List<CastMember> = emptyList(),
    val isAdult: Boolean = false
)

data class CastMember(
    val id: Int,
    val name: String,
    val profilePath: String?,
    val character: String
)
