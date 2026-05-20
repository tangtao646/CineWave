package com.example.kmp_demo.features.film.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val voteAverage: Double,
    val releaseDate: String,
    val type: String, // "popular" or other categories
    val orderIndex: Long = System.currentTimeMillis(),
    val genreIds: String = "", // JSON array as string
    val popularity: Double = 0.0,
    val isFavorite: Boolean = false,
)
