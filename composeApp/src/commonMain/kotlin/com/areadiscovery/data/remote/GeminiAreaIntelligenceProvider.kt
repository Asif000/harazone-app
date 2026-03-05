package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.ChatMessage
import com.areadiscovery.domain.model.ChatToken
import com.areadiscovery.domain.model.DomainError
import com.areadiscovery.domain.model.DomainErrorException
import com.areadiscovery.domain.provider.ApiKeyProvider
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.util.AppLogger
import com.areadiscovery.util.withRetry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiRequestContent>
)

@Serializable
private data class GeminiRequestContent(
    val parts: List<GeminiRequestPart>
)

@Serializable
private data class GeminiRequestPart(
    val text: String
)

internal class GeminiAreaIntelligenceProvider(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val promptBuilder: GeminiPromptBuilder,
    private val responseParser: GeminiResponseParser
) : AreaIntelligenceProvider {

    companion object {
        const val GEMINI_MODEL = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
        val apiKey = apiKeyProvider.geminiApiKey
        if (apiKey.isBlank()) {
            throw DomainErrorException(DomainError.ApiError(0, "Gemini API key not configured"))
        }

        AppLogger.d { "GeminiAreaIntelligenceProvider: streaming portrait for '$areaName'" }

        val prompt = promptBuilder.buildAreaPortraitPrompt(areaName, context)
        val requestBody = json.encodeToString(
            GeminiRequest(
                contents = listOf(
                    GeminiRequestContent(
                        parts = listOf(GeminiRequestPart(text = prompt))
                    )
                )
            )
        )

        var hasEmitted = false

        val result = withRetry(
            maxAttempts = MAX_RETRY_ATTEMPTS,
            isRetryable = { e -> !hasEmitted && e is Exception && isRetryableError(e) }
        ) {
            val streamingParser = responseParser.createStreamingParser()

            httpClient.sse(
                urlString = "$BASE_URL/$GEMINI_MODEL:streamGenerateContent",
                request = {
                    method = HttpMethod.Post
                    parameter("alt", "sse")
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            ) {
                incoming.collect { event ->
                    val data = event.data ?: return@collect
                    val text = responseParser.extractTextFromSseEvent(data) ?: return@collect

                    for (update in streamingParser.processChunk(text)) {
                        hasEmitted = true
                        emit(update)
                    }
                }
            }

            for (update in streamingParser.finish()) {
                hasEmitted = true
                emit(update)
            }

            AppLogger.d { "GeminiAreaIntelligenceProvider: portrait streaming complete" }
        }

        if (result.isFailure) {
            val error = result.exceptionOrNull()
            throw when (error) {
                is DomainErrorException -> error
                is Exception -> mapToDomainErrorException(error)
                else -> mapToDomainErrorException(RuntimeException("Unexpected error", error))
            }
        }
    }

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>
    ): Flow<ChatToken> = emptyFlow()

    private fun isRetryableError(e: Exception): Boolean = when (e) {
        is ServerResponseException -> true  // 5xx — transient server errors
        is ClientRequestException -> false  // 4xx — client errors, never retry
        is kotlinx.serialization.SerializationException -> false  // Structural failure, never retry
        is IllegalArgumentException, is IllegalStateException -> false  // Programming errors, never retry
        else -> true  // Timeouts, network errors — retryable
    }

    private fun mapToDomainErrorException(e: Exception): DomainErrorException {
        val domainError = when (e) {
            is ClientRequestException -> {
                val status = e.response.status.value
                when (status) {
                    401, 403 -> DomainError.ApiError(status, "Invalid API key")
                    429 -> DomainError.ApiError(429, "Rate limited")
                    else -> DomainError.ApiError(status, "Client error")
                }
            }
            is ServerResponseException -> {
                DomainError.ApiError(e.response.status.value, "Server error")
            }
            else -> {
                val message = e.message?.lowercase() ?: ""
                when {
                    message.contains("timeout") -> DomainError.NetworkError("Request timed out")
                    message.contains("dns") || message.contains("unknownhost") || message.contains("unresolved") ->
                        DomainError.NetworkError("DNS resolution failed")
                    message.contains("ssl") || message.contains("tls") || message.contains("certificate") ->
                        DomainError.NetworkError("SSL/TLS error")
                    message.contains("refused") ->
                        DomainError.NetworkError("Connection refused")
                    else -> DomainError.NetworkError("Network error")
                }
            }
        }
        return DomainErrorException(domainError)
    }
}
