package com.example.kmp_demo.features.radio.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val favicon: String,
    val tags: String,
    val countryCode: String,
    val language: String,
    val category: String, // 音乐、故事、新闻
    val isFavorite: Boolean = false,
    val lastPlayedTime: Long = 0L,
)
