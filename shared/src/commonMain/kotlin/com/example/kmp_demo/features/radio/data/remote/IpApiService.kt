package com.example.kmp_demo.features.radio.data.remote

import com.example.kmp_demo.features.radio.data.remote.dto.IpResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class IpApiService(private val client: HttpClient) {
    companion object {
        const val BASE_URL = "http://ip-api.com/"
    }

    suspend fun getCurrentLocation(): IpResponse {
        return client.get("${BASE_URL}json").body()
    }
}
