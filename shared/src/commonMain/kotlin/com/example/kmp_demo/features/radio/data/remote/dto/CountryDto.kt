package com.example.kmp_demo.features.radio.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CountryDto(
    @SerialName("name") val name: String,
    @SerialName("iso_3166_1") val code: String,
    @SerialName("stationcount") val stationCount: Int
)
