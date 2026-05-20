package com.example.kmp_demo.features.radio.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StationDto(
    @SerialName("stationuuid") val stationUuid: String,
    @SerialName("name") val name: String,
    @SerialName("url_resolved") val url: String,
    @SerialName("favicon") val favicon: String?,
    @SerialName("tags") val tags: String?,
    @SerialName("countrycode") val countryCode: String?,
    @SerialName("votes") val votes: Int?,
    @SerialName("clickcount") val clickCount: Int?,
    @SerialName("language") val language: String?
)

@Serializable
data class IpResponse(
    @SerialName("countryCode") val countryCode: String,
    @SerialName("country") val countryName: String,
    @SerialName("query") val ip: String
)
