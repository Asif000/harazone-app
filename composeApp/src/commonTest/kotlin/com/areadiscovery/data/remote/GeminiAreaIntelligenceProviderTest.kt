package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.DomainError
import com.areadiscovery.domain.model.DomainErrorException
import com.areadiscovery.domain.provider.ApiKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeminiAreaIntelligenceProviderTest {

    // Note: Ktor MockEngine does not support SSE sessions in Ktor 3.x —
    // SSE integration tests require a real HTTP engine. Tests here cover
    // validation and error handling paths that execute before the SSE call.

    private val testAreaContext = AreaContext(
        timeOfDay = "afternoon",
        dayOfWeek = "Wednesday",
        visitCount = 0,
        preferredLanguage = "en"
    )

    private class FakeApiKeyProvider(
        override val geminiApiKey: String = "test-key"
    ) : ApiKeyProvider

    private fun createProvider(apiKey: String = "test-key"): GeminiAreaIntelligenceProvider {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(SSE)
        }
        return GeminiAreaIntelligenceProvider(
            httpClient = client,
            apiKeyProvider = FakeApiKeyProvider(apiKey),
            promptBuilder = GeminiPromptBuilder(),
            responseParser = GeminiResponseParser()
        )
    }

    @Test
    fun streamAreaPortrait_failsFastWithBlankApiKey() = runTest {
        val provider = createProvider(apiKey = "")

        val error = assertFailsWith<DomainErrorException> {
            provider.streamAreaPortrait("TestArea", testAreaContext).collect {}
        }

        val apiError = error.domainError as DomainError.ApiError
        assertEquals(0, apiError.code)
        assertEquals("Gemini API key not configured", apiError.message)
    }

    @Test
    fun streamAreaPortrait_failsFastWithWhitespaceApiKey() = runTest {
        val provider = createProvider(apiKey = "   ")

        val error = assertFailsWith<DomainErrorException> {
            provider.streamAreaPortrait("TestArea", testAreaContext).collect {}
        }

        assertTrue(error.domainError is DomainError.ApiError)
        assertEquals("Gemini API key not configured", (error.domainError as DomainError.ApiError).message)
    }

    @Test
    fun streamChatResponse_returnsEmptyFlow() = runTest {
        val provider = createProvider()

        val tokens = mutableListOf<com.areadiscovery.domain.model.ChatToken>()
        provider.streamChatResponse("query", "area", emptyList()).collect {
            tokens.add(it)
        }

        assertTrue(tokens.isEmpty())
    }
}
