package com.harazone.data.remote

import com.harazone.fakes.FakeLocaleProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MapTilerGeocodingProviderTest {

    @Test
    fun `search includes language parameter from locale provider`() = runTest {
        var capturedUrl = ""
        val mockEngine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"features":[]}""", HttpStatusCode.OK)
        }
        val provider = MapTilerGeocodingProvider(
            httpClient = HttpClient(mockEngine),
            localeProvider = FakeLocaleProvider(languageTag = "pt-BR"),
        )

        provider.search("Doral")

        assertTrue(capturedUrl.contains("language=pt"), "URL should contain language=pt but was: $capturedUrl")
    }

    @Test
    fun `search uses base language without region subtag`() = runTest {
        var capturedUrl = ""
        val mockEngine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"features":[]}""", HttpStatusCode.OK)
        }
        val provider = MapTilerGeocodingProvider(
            httpClient = HttpClient(mockEngine),
            localeProvider = FakeLocaleProvider(languageTag = "ar-SA"),
        )

        provider.search("Dubai")

        assertTrue(capturedUrl.contains("language=ar"), "URL should contain language=ar but was: $capturedUrl")
    }

    @Test
    fun `search handles simple language tag without region`() = runTest {
        var capturedUrl = ""
        val mockEngine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"features":[]}""", HttpStatusCode.OK)
        }
        val provider = MapTilerGeocodingProvider(
            httpClient = HttpClient(mockEngine),
            localeProvider = FakeLocaleProvider(languageTag = "en"),
        )

        provider.search("Miami")

        assertTrue(capturedUrl.contains("language=en"), "URL should contain language=en but was: $capturedUrl")
    }
}
