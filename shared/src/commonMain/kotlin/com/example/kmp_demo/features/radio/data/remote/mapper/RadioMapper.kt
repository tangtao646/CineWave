package com.example.kmp_demo.features.radio.data.remote.mapper

import com.example.kmp_demo.features.radio.data.local.model.RadioStationEntity
import com.example.kmp_demo.features.radio.data.remote.dto.StationDto
import com.example.kmp_demo.features.radio.domain.model.RadioStation

fun StationDto.toEntity(category: String) = RadioStationEntity(
    stationUuid = stationUuid,
    name = name,
    url = url,
    favicon = favicon ?: "",
    tags = tags ?: "",
    countryCode = countryCode ?: "CN",
    language = language ?: "",
    category = category,
)


fun RadioStationEntity.toDomain(): RadioStation {
    return RadioStation(
        uuid = stationUuid,
        name = name,
        streamUrl = url,
        favicon = favicon,
        tags = tags.split(","),
        countryCode = countryCode,
        language = language,
        category = category,
        isFavorite = isFavorite,
        lastPlayedTime = lastPlayedTime
    )
}