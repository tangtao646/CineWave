package com.example.kmp_demo.core.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                useAlternativeNames = false
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = Logger.DEFAULT
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
        expectSuccess = true
    }
}

fun HttpRequestBuilder.userAgent() {
    userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
}

fun commonHeaders() {
    headersOf("Cache-Control", "no-cache")
}
