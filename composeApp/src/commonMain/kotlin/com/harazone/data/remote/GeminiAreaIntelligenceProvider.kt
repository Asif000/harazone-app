package com.harazone.data.remote

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.MessageRole
import com.harazone.domain.model.DomainError
import com.harazone.domain.model.DomainErrorException
import com.harazone.domain.model.POI
import com.harazone.domain.provider.ApiKeyProvider
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.util.AppLogger
import com.harazone.util.withRetry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiRequestContent>,
    @kotlinx.serialization.SerialName("system_instruction")
    val systemInstruction: GeminiSystemInstruction? = null,
)

@Serializable
private data class GeminiSystemInstruction(
    val parts: List<GeminiRequestPart>
)

@Serializable
private data class GeminiRequestContent(
    val role: String = "user",
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

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = kotlinx.coroutines.flow.channelFlow {
        val apiKey = apiKeyProvider.geminiApiKey
        if (apiKey.isBlank()) {
            throw DomainErrorException(DomainError.ApiError(0, "Gemini API key not configured"))
        }

        AppLogger.d { "GeminiAreaIntelligenceProvider: streaming portrait for '$areaName'" }

        data class Stage1Result(val names: List<String>, val vibes: List<DynamicVibe>, val vibeLabels: List<String>)
        val stage1Deferred = CompletableDeferred<Stage1Result>()

        // Stage 1 — fast pin call (returns vibes + POIs)
        launch {
            try {
                val prompt = promptBuilder.buildPinOnlyPrompt(areaName, isNewUser = context.isNewUser, languageTag = context.preferredLanguage)
                val requestBody = buildRequestBody(prompt)
                val fullText = StringBuilder()
                var hasEmitted = false
                val result = withRetry(
                    maxAttempts = MAX_RETRY_ATTEMPTS, initialDelayMs = 200, maxDelayMs = 2000,
                    isRetryable = { e -> !hasEmitted && e is Exception && isRetryableError(e) }
                ) {
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
                            fullText.append(text)
                            hasEmitted = true
                        }
                    }
                }
                if (result.isFailure) {
                    stage1Deferred.completeExceptionally(result.exceptionOrNull()!!)
                    throw result.exceptionOrNull()!!
                }
                val stage1Parsed = responseParser.parseStage1WithHighlights(fullText.toString())
                val dynamicVibes = stage1Parsed.vibes
                val pois = stage1Parsed.pois
                val areaHighlights = stage1Parsed.areaHighlights
                stage1Deferred.complete(Stage1Result(pois.map { it.name }, dynamicVibes, dynamicVibes.map { it.label }))
                if (pois.isNotEmpty()) {
                    if (dynamicVibes.isNotEmpty()) {
                        send(BucketUpdate.VibesReady(vibes = dynamicVibes, pois = pois, areaHighlights = areaHighlights))
                    } else {
                        send(BucketUpdate.PinsReady(pois, areaHighlights = areaHighlights))
                    }
                    AppLogger.d { "Stage 1 complete: ${pois.size} pins, ${dynamicVibes.size} vibes for '$areaName'" }
                } else {
                    AppLogger.d { "Stage 1 returned no pins for '$areaName' — Stage 2 will fallback" }
                }
            } catch (e: CancellationException) {
                stage1Deferred.cancel()
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Stage 1 failed for '$areaName' — Stage 2 will fallback" }
                if (!stage1Deferred.isCompleted) stage1Deferred.completeExceptionally(e)
            }
        }

        // Stage 2 — enrich call (waits for Stage 1 names)
        launch {
            try {
                val stage1Result = try { stage1Deferred.await() } catch (e: Exception) { Stage1Result(emptyList(), emptyList(), emptyList()) }
                val stage1Names = stage1Result.names
                val stage1Vibes = stage1Result.vibes
                val prompt = if (stage1Names.isNotEmpty() && stage1Vibes.isNotEmpty()) {
                    promptBuilder.buildDynamicVibeEnrichmentPrompt(areaName, stage1Vibes.map { it.label }, stage1Names, languageTag = context.preferredLanguage)
                } else if (stage1Names.isNotEmpty()) {
                    promptBuilder.buildEnrichmentPrompt(areaName, stage1Names, context)
                } else {
                    promptBuilder.buildAreaPortraitPrompt(areaName, context)
                }
                val requestBody = buildRequestBody(prompt)
                val fullText = StringBuilder()
                var hasEmitted = false
                val streamingParser = if (stage1Names.isEmpty()) responseParser.createStreamingParser() else null
                val result = withRetry(
                    maxAttempts = MAX_RETRY_ATTEMPTS, initialDelayMs = 200, maxDelayMs = 2000,
                    isRetryable = { e -> !hasEmitted && e is Exception && isRetryableError(e) }
                ) {
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
                            if (streamingParser != null) {
                                for (update in streamingParser.processChunk(text)) {
                                    if (update is BucketUpdate.PortraitComplete) send(update)
                                }
                            } else {
                                fullText.append(text)
                            }
                            hasEmitted = true
                        }
                    }
                    streamingParser?.finish()?.filterIsInstance<BucketUpdate.PortraitComplete>()?.forEach { send(it) }
                }
                if (result.isFailure) throw result.exceptionOrNull()!!
                if (stage1Names.isNotEmpty() && stage1Vibes.isNotEmpty()) {
                    // Dynamic vibe enrichment path
                    val (vibeContents, enrichedPois) = responseParser.parseDynamicVibeResponse(fullText.toString())
                    AppLogger.d { "Stage 2 (dynamic vibes) complete: ${vibeContents.size} vibes, ${enrichedPois.size} POIs for '$areaName'" }
                    for (vc in vibeContents) {
                        send(BucketUpdate.DynamicVibeComplete(vc))
                    }
                    send(BucketUpdate.PortraitComplete(enrichedPois))
                } else if (stage1Names.isNotEmpty()) {
                    val enrichResult = responseParser.parseEnrichmentWithHighlights(fullText.toString())
                    val enriched = enrichResult.enrichments
                    AppLogger.d { "Stage 2 complete: ${enriched.size} enriched, ${enrichResult.areaHighlights.size} highlights for '$areaName'" }
                    send(BucketUpdate.PortraitComplete(
                        pois = enriched.map { e ->
                            POI(name = e.n, type = "", description = "", confidence = Confidence.MEDIUM,
                                latitude = null, longitude = null, vibe = e.v, insight = e.w,
                                hours = e.h, liveStatus = e.s, rating = e.r, priceRange = e.p)
                        },
                        areaHighlights = enrichResult.areaHighlights,
                    ))
                }
                AppLogger.d { "GeminiAreaIntelligenceProvider: portrait streaming complete for '$areaName'" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(e) { "Stage 2 failed for '$areaName'" }
                throw mapToDomainErrorException(e)
            }
        }

        // Stage 3 — background batch pipeline (silent, 2 batches of 3 POIs each)
        launch {
            try {
                val stage1Result = stage1Deferred.await()
                val stage1Names = stage1Result.names
                val stage1VibeLabels = stage1Result.vibeLabels
                if (stage1Names.isEmpty() || stage1VibeLabels.isEmpty()) return@launch

                // Batch 1
                ensureActive()
                val batch1Pois = streamAndParsePois(
                    promptBuilder.buildBackgroundBatchPrompt(areaName, stage1Names, stage1VibeLabels, languageTag = context.preferredLanguage)
                )
                send(BucketUpdate.BackgroundBatchReady(batch1Pois, batchIndex = 1))
                if (batch1Pois.isNotEmpty()) {
                    ensureActive()
                    val enriched1Prompt = promptBuilder.buildDynamicVibeEnrichmentPrompt(
                        areaName, stage1VibeLabels, batch1Pois.map { it.name }, languageTag = context.preferredLanguage
                    )
                    val (_, enriched1Pois) = streamAndParseEnrichment(enriched1Prompt)
                    send(BucketUpdate.BackgroundEnrichmentComplete(enriched1Pois, batchIndex = 1))
                }

                // Batch 2
                ensureActive()
                val allExcluded = stage1Names + batch1Pois.map { it.name }
                val batch2Pois = streamAndParsePois(
                    promptBuilder.buildBackgroundBatchPrompt(areaName, allExcluded, stage1VibeLabels, languageTag = context.preferredLanguage)
                )
                send(BucketUpdate.BackgroundBatchReady(batch2Pois, batchIndex = 2))
                if (batch2Pois.isNotEmpty()) {
                    ensureActive()
                    val enriched2Prompt = promptBuilder.buildDynamicVibeEnrichmentPrompt(
                        areaName, stage1VibeLabels, batch2Pois.map { it.name }, languageTag = context.preferredLanguage
                    )
                    val (_, enriched2Pois) = streamAndParseEnrichment(enriched2Prompt)
                    send(BucketUpdate.BackgroundEnrichmentComplete(enriched2Pois, batchIndex = 2))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(e) { "Background batch failed silently" }
            } finally {
                trySend(BucketUpdate.BackgroundFetchComplete)
            }
        }
    }

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>
    ): Flow<ChatToken> = flow {
        val apiKey = apiKeyProvider.geminiApiKey
        if (apiKey.isBlank()) {
            throw DomainErrorException(DomainError.ApiError(0, "Gemini API key not configured"))
        }

        AppLogger.d { "GeminiAreaIntelligenceProvider: streaming chat for '$query' in '$areaName'" }

        // Extract system context (first USER message before any real conversation) as systemInstruction
        val systemInstruction = conversationHistory.firstOrNull()?.let { first ->
            GeminiSystemInstruction(parts = listOf(GeminiRequestPart(text = first.content)))
        }
        val actualHistory = if (systemInstruction != null) conversationHistory.drop(1) else conversationHistory

        val contents = actualHistory.map { msg ->
            GeminiRequestContent(
                role = if (msg.role == MessageRole.USER) "user" else "model",
                parts = listOf(GeminiRequestPart(text = msg.content))
            )
        } + GeminiRequestContent(
            role = "user",
            parts = listOf(GeminiRequestPart(text = query))
        )
        val requestBody = json.encodeToString(
            GeminiRequest(contents = contents, systemInstruction = systemInstruction)
        )

        var hasEmitted = false

        val result = withRetry(
            maxAttempts = MAX_RETRY_ATTEMPTS,
            initialDelayMs = 200,
            maxDelayMs = 2000,
            isRetryable = { e -> !hasEmitted && e is Exception && isRetryableError(e) }
        ) {
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
                    hasEmitted = true
                    emit(ChatToken(text = text, isComplete = false))
                }
            }

            emit(ChatToken(text = "", isComplete = true))
            AppLogger.d { "GeminiAreaIntelligenceProvider: chat streaming complete" }
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

    override suspend fun generatePoiContext(
        poiName: String,
        poiType: String,
        areaName: String,
        timeHint: String,
        languageTag: String,
    ): Triple<String, String, String>? {
        return try {
            val apiKey = apiKeyProvider.geminiApiKey
            if (apiKey.isBlank()) return null
            val prompt = promptBuilder.buildPoiContextPrompt(poiName, poiType, areaName, timeHint, languageTag)
            val requestBody = buildRequestBody(prompt)
            val responseJson = httpClient.post("$BASE_URL/$GEMINI_MODEL:generateContent") {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()
            val text = responseParser.extractTextFromGenerateContent(responseJson)
            responseParser.parsePoiContextResponse(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiAreaIntelligenceProvider: generatePoiContext failed" }
            null
        }
    }

    private suspend fun streamAndParsePois(prompt: String): List<POI> {
        val apiKey = apiKeyProvider.geminiApiKey
        val requestBody = buildRequestBody(prompt)
        val fullText = StringBuilder()
        httpClient.sse(
            urlString = "$BASE_URL/$GEMINI_MODEL:streamGenerateContent",
            request = {
                method = io.ktor.http.HttpMethod.Post
                parameter("alt", "sse")
                parameter("key", apiKey)
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
            }
        ) {
            incoming.collect { event ->
                val data = event.data ?: return@collect
                val text = responseParser.extractTextFromSseEvent(data) ?: return@collect
                fullText.append(text)
            }
        }
        val (_, pois) = responseParser.parseStage1Response(fullText.toString())
        return pois
    }

    private suspend fun streamAndParseEnrichment(prompt: String): Pair<List<com.harazone.domain.model.DynamicVibeContent>, List<POI>> {
        val apiKey = apiKeyProvider.geminiApiKey
        val requestBody = buildRequestBody(prompt)
        val fullText = StringBuilder()
        httpClient.sse(
            urlString = "$BASE_URL/$GEMINI_MODEL:streamGenerateContent",
            request = {
                method = io.ktor.http.HttpMethod.Post
                parameter("alt", "sse")
                parameter("key", apiKey)
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
            }
        ) {
            incoming.collect { event ->
                val data = event.data ?: return@collect
                val text = responseParser.extractTextFromSseEvent(data) ?: return@collect
                fullText.append(text)
            }
        }
        return responseParser.parseDynamicVibeResponse(fullText.toString())
    }

    private fun buildRequestBody(prompt: String): String = json.encodeToString(
        GeminiRequest(contents = listOf(GeminiRequestContent(parts = listOf(GeminiRequestPart(text = prompt)))))
    )

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
