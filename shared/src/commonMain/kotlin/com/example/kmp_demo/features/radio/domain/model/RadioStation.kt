package com.example.kmp_demo.features.radio.domain.model

data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val tags: List<String>,
    val countryCode: String,
    val language: String,
    val category: String,
    val isFavorite: Boolean = false,
    val lastPlayedTime: Long = 0L
)