package com.example.kmp_demo.features.film.data.remote.mapper

import com.example.kmp_demo.features.film.data.local.MovieEntity
import com.example.kmp_demo.features.film.data.remote.dto.CastDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieDetailDto
import com.example.kmp_demo.features.film.data.remote.dto.MovieDto
import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.domain.model.CastMember
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.model.MovieDetail

fun MovieDto.toMovie(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterUrl = posterPath?.let { "${FilmApi.IMAGE_BASE_URL}$it" },
    backdropUrl = backdropPath?.let { "${FilmApi.IMAGE_BASE_URL}$it" },
    voteAverage = voteAverage,
    releaseDate = releaseDate ?: "",
    genreIds = genreIds
)

fun MovieDto.toEntity(type: String): MovieEntity = MovieEntity(
    id = id,
    title = title,
    overview = overview,
    posterUrl = posterPath?.let { "${FilmApi.IMAGE_BASE_URL}$it" },
    backdropUrl = backdropPath?.let { "${FilmApi.IMAGE_BASE_URL}$it" },
    voteAverage = voteAverage,
    releaseDate = releaseDate ?: "",
    type = type
)

fun MovieEntity.toMovie(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    voteAverage = voteAverage,
    releaseDate = releaseDate
)

fun MovieDetailDto.toMovieDetail(): MovieDetail = MovieDetail(
    id = id,
    title = title,
    overview = overview,
    posterUrl = posterPath?.let { "${FilmApi.IMAGE_BASE_URL}$it" },
    backdropUrl = backdropPath?.let { "${FilmApi.BACKDROP_BASE_URL}$it" },
    voteAverage = voteAverage,
    releaseDate = releaseDate ?: "",
    genres = genres.map { it.name },
    runtime = runtime ?: 0,
    tagline = tagline,
    cast = credits?.cast?.map { it.toCastMember() } ?: emptyList()
)

fun CastDto.toCastMember(): CastMember = CastMember(
    id = id,
    name = name,
    profilePath = profilePath?.let { "${FilmApi.IMAGE_BASE_URL}$it" },
    character = character
)
