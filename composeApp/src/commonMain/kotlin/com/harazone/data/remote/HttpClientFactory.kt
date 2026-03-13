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
            socketTimeoutMillis = 60_000
            // Do NOT set requestTimeoutMillis — SSE streams can run 15-20s with
            // gemini-2.5-flash thinking; requestTimeout kills the entire request
        }
    }
}
