package com.harazone.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun create(): HttpClient = HttpClient {
        install(SSE)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
            // Do NOT set requestTimeoutMillis — SSE streams run 6-8s total
            // and requestTimeout kills the entire request, including active streaming
        }
    }
}
