package com.harazone.data.remote

import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.AreaAdvisory
import com.harazone.fakes.FakeClock
import com.harazone.fakes.FakeUserPreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FcdoAdvisoryProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createProvider(
        responseText: String = "{}",
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        clock: FakeClock = FakeClock(),
    ): Triple<FcdoAdvisoryProvider, FakeUserPreferencesRepository, FakeClock> {
        val engine = MockEngine { _ ->
            respond(
                content = responseText,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = FcdoAdvisoryProvider(HttpClient(engine), prefs, clock)
        return Triple(provider, prefs, clock)
    }

    // --- classifyAdvisoryLevel ---

    @Test
    fun classifyAdvisoryLevel_advises_against_all_travel_returns_DO_NOT_TRAVEL() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.DO_NOT_TRAVEL,
            provider.classifyAdvisoryLevel("The FCDO advises against all travel to this country."),
        )
    }

    @Test
    fun classifyAdvisoryLevel_advises_against_all_but_essential_returns_RECONSIDER() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.RECONSIDER,
            provider.classifyAdvisoryLevel("The FCDO advises against all but essential travel to parts of this country."),
        )
    }

    @Test
    fun classifyAdvisoryLevel_exercise_increased_caution_returns_CAUTION() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.CAUTION,
            provider.classifyAdvisoryLevel("You should exercise increased caution when visiting."),
        )
    }

    @Test
    fun classifyAdvisoryLevel_neutral_text_returns_SAFE() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.SAFE,
            provider.classifyAdvisoryLevel("Most visits are trouble-free."),
        )
    }

    @Test
    fun classifyAdvisoryLevel_empty_returns_SAFE() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.SAFE,
            provider.classifyAdvisoryLevel(""),
        )
    }

    @Test
    fun classifyAdvisoryLevel_unexpected_phrasing_returns_SAFE() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.SAFE,
            provider.classifyAdvisoryLevel("Check local conditions before traveling."),
        )
    }

    // --- Cache behavior ---

    @Test
    fun getAdvisory_returns_cached_result_within_24h() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val clock = FakeClock(nowMs = 100_000_000L)
        val cachedAdvisory = AreaAdvisory(
            level = AdvisoryLevel.CAUTION,
            countryName = "Japan",
            countryCode = "JP",
            summary = "Exercise increased caution.",
            details = emptyList(),
            subNationalZones = emptyList(),
            sourceUrl = "https://www.gov.uk/foreign-travel-advice/japan",
            lastUpdated = 0L,
            cachedAt = clock.nowMs - 1_000_000L, // cached 1000s ago (well within 24h)
        )
        prefs.setAdvisoryCache("JP", json.encodeToString(AreaAdvisory.serializer(), cachedAdvisory))

        val engine = MockEngine { _ ->
            error("Should not fetch — cache should be used")
        }
        val provider = FcdoAdvisoryProvider(HttpClient(engine), prefs, clock)
        val result = provider.getAdvisory("JP")
        assertTrue(result.isSuccess)
        assertEquals(AdvisoryLevel.CAUTION, result.getOrThrow().level)
    }

    @Test
    fun getAdvisory_fetches_fresh_when_cache_expired() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val clock = FakeClock(nowMs = 200_000_000L)
        val staleAdvisory = AreaAdvisory(
            level = AdvisoryLevel.CAUTION,
            countryName = "Japan",
            countryCode = "JP",
            summary = "Exercise increased caution.",
            details = emptyList(),
            subNationalZones = emptyList(),
            sourceUrl = "",
            lastUpdated = 0L,
            cachedAt = clock.nowMs - 25 * 3_600_000L, // 25 hours ago (expired)
        )
        prefs.setAdvisoryCache("JP", json.encodeToString(AreaAdvisory.serializer(), staleAdvisory))
        prefs.setAdvisoryCachedCountryName("JP", "Japan")

        val fcdoResponse = """
        {
            "title": "Japan travel advice",
            "public_updated_at": "2026-01-01T00:00:00Z",
            "details": { "summary": "Most visits are trouble-free." },
            "parts": []
        }
        """.trimIndent()

        val (provider, _, _) = createProvider(
            responseText = fcdoResponse,
            prefs = prefs,
            clock = clock,
        )
        val result = provider.getAdvisory("JP")
        assertTrue(result.isSuccess)
        assertEquals(AdvisoryLevel.SAFE, result.getOrThrow().level)
    }

    @Test
    fun getAdvisory_does_not_cache_UNKNOWN() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val clock = FakeClock()
        val (provider, _, _) = createProvider(
            responseText = "not json at all",
            prefs = prefs,
            clock = clock,
        )
        val result = provider.getAdvisory("XX")
        assertTrue(result.isSuccess)
        assertEquals(AdvisoryLevel.UNKNOWN, result.getOrThrow().level)
        assertEquals(null, prefs.getAdvisoryCache("XX"))
    }

    // --- Error handling ---

    @Test
    fun getAdvisory_error_returns_UNKNOWN_not_SAFE() = runTest {
        val (provider, _, _) = createProvider(
            statusCode = HttpStatusCode.InternalServerError,
            responseText = "server error",
        )
        val result = provider.getAdvisory("GB")
        assertTrue(result.isSuccess)
        assertEquals(AdvisoryLevel.UNKNOWN, result.getOrThrow().level)
    }

    // --- Slug mapping ---

    @Test
    fun getAdvisory_uses_correct_slug_for_mapped_country() = runTest {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """{"title":"UAE travel advice","details":{"summary":"Most visits are trouble-free."},"parts":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val prefs = FakeUserPreferencesRepository()
        prefs.setAdvisoryCachedCountryName("AE", "United Arab Emirates")
        val provider = FcdoAdvisoryProvider(HttpClient(engine), prefs, FakeClock())
        provider.getAdvisory("AE")
        assertTrue(requestedUrl.contains("united-arab-emirates"))
    }
}
