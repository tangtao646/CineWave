package com.example.kmp_demo.features.radio.data.remote

import com.example.kmp_demo.features.radio.data.remote.dto.CountryDto
import com.example.kmp_demo.features.radio.data.remote.dto.StationDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class RadioApiService(private val client: HttpClient) {
    companion object {
        const val BASE_URL = "https://de1.api.radio-browser.info/json/"
    }

    suspend fun searchStations(
        name: String? = null,
        countryCode: String? = null,
        language: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        order: String = "votes",
        reverse: Boolean = true,
        hideBroken: Boolean = true
    ): List<StationDto> {
        return client.get("${BASE_URL}stations/search") {
            parameter("name", name)
            parameter("countrycode", countryCode)
            parameter("language", language)
            parameter("limit", limit)
            parameter("offset", offset)
            parameter("order", order)
            parameter("reverse", reverse)
            parameter("hidebroken", hideBroken)
        }.body()
    }

    suspend fun getCountries(
        order: String = "stationcount",
        reverse: Boolean = true
    ): List<CountryDto> {
        return client.get("${BASE_URL}countries") {
            parameter("order", order)
            parameter("reverse", reverse)
        }.body()
    }
}
