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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FcdoAdvisoryProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createProvider(
        responseText: String = SAFE_COUNTRY_RESPONSE,
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

    // --- classifyAlertStatus ---

    @Test
    fun classifyAlertStatus_avoid_all_travel_whole_country_returns_DO_NOT_TRAVEL() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.DO_NOT_TRAVEL,
            provider.classifyAlertStatus(listOf("avoid_all_travel_to_whole_country")),
        )
    }

    @Test
    fun classifyAlertStatus_avoid_all_travel_parts_returns_DO_NOT_TRAVEL() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.DO_NOT_TRAVEL,
            provider.classifyAlertStatus(listOf("avoid_all_travel_to_parts")),
        )
    }

    @Test
    fun classifyAlertStatus_avoid_but_essential_whole_returns_RECONSIDER() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.RECONSIDER,
            provider.classifyAlertStatus(listOf("avoid_all_but_essential_travel_to_whole_country")),
        )
    }

    @Test
    fun classifyAlertStatus_avoid_but_essential_parts_returns_RECONSIDER() {
        val (provider, _, _) = createProvider()
        assertEquals(
            AdvisoryLevel.RECONSIDER,
            provider.classifyAlertStatus(listOf("avoid_all_but_essential_travel_to_parts")),
        )
    }

    @Test
    fun classifyAlertStatus_empty_returns_null() {
        val (provider, _, _) = createProvider()
        assertNull(provider.classifyAlertStatus(emptyList()))
    }

    @Test
    fun classifyAlertStatus_unknown_value_returns_null() {
        val (provider, _, _) = createProvider()
        assertNull(provider.classifyAlertStatus(listOf("some_new_unknown_status")))
    }

    // --- classifyAdvisoryLevel (keyword fallback) ---

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
        assertEquals(AdvisoryLevel.SAFE, provider.classifyAdvisoryLevel(""))
    }

    // --- parseFcdoResponse with real structure ---

    @Test
    fun parseFcdoResponse_yemen_returns_DO_NOT_TRAVEL() {
        val (provider, _, _) = createProvider()
        val advisory = provider.parseFcdoResponse(YEMEN_RESPONSE, "YE", null)
        assertEquals(AdvisoryLevel.DO_NOT_TRAVEL, advisory.level)
        assertEquals("Yemen", advisory.countryName)
        assertTrue(advisory.summary.contains("advises against all travel"))
    }

    @Test
    fun parseFcdoResponse_safe_country_returns_SAFE() {
        val (provider, _, _) = createProvider()
        val advisory = provider.parseFcdoResponse(SAFE_COUNTRY_RESPONSE, "JP", null)
        assertEquals(AdvisoryLevel.SAFE, advisory.level)
        assertEquals("Japan", advisory.countryName)
    }

    @Test
    fun parseFcdoResponse_reconsider_country_returns_RECONSIDER() {
        val (provider, _, _) = createProvider()
        val advisory = provider.parseFcdoResponse(RECONSIDER_RESPONSE, "PK", null)
        assertEquals(AdvisoryLevel.RECONSIDER, advisory.level)
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
            cachedAt = clock.nowMs - 1_000_000L,
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
            cachedAt = clock.nowMs - 25 * 3_600_000L,
        )
        prefs.setAdvisoryCache("JP", json.encodeToString(AreaAdvisory.serializer(), staleAdvisory))
        prefs.setAdvisoryCachedCountryName("JP", "Japan")

        val (provider, _, _) = createProvider(
            responseText = SAFE_COUNTRY_RESPONSE,
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
        val (provider, _, _) = createProvider(
            responseText = "not json at all",
            prefs = prefs,
        )
        val result = provider.getAdvisory("XX")
        assertTrue(result.isSuccess)
        assertEquals(AdvisoryLevel.UNKNOWN, result.getOrThrow().level)
        assertNull(prefs.getAdvisoryCache("XX"))
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
                content = SAFE_COUNTRY_RESPONSE,
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

    companion object {
        // Real FCDO response structure for Yemen (DO_NOT_TRAVEL)
        val YEMEN_RESPONSE = """
        {
            "title": "Yemen travel advice",
            "public_updated_at": "2026-03-09T17:16:38+00:00",
            "details": {
                "alert_status": ["avoid_all_travel_to_whole_country"],
                "country": {"name": "Yemen", "slug": "yemen"},
                "parts": [
                    {
                        "slug": "warnings-and-insurance",
                        "body": "<h2>FCDO advises against all travel to Yemen</h2><p>FCDO advises against all travel to Yemen due to the ongoing conflict.</p>"
                    },
                    {
                        "slug": "safety-and-security",
                        "body": "<h2>Terrorism</h2><p>There is a high threat of terrorism.</p><h2>Civil unrest</h2><p>Armed conflict is ongoing.</p>"
                    }
                ]
            }
        }
        """.trimIndent()

        // Safe country (no alert_status)
        val SAFE_COUNTRY_RESPONSE = """
        {
            "title": "Japan travel advice",
            "public_updated_at": "2026-01-15T10:00:00+00:00",
            "details": {
                "alert_status": [],
                "country": {"name": "Japan", "slug": "japan"},
                "parts": [
                    {
                        "slug": "warnings-and-insurance",
                        "body": "<p>Most visits to Japan are trouble-free.</p>"
                    },
                    {
                        "slug": "safety-and-security",
                        "body": "<p>Japan is generally safe.</p>"
                    }
                ]
            }
        }
        """.trimIndent()

        // Reconsider travel (partial)
        val RECONSIDER_RESPONSE = """
        {
            "title": "Pakistan travel advice",
            "public_updated_at": "2026-02-20T08:00:00+00:00",
            "details": {
                "alert_status": ["avoid_all_but_essential_travel_to_parts"],
                "country": {"name": "Pakistan", "slug": "pakistan"},
                "parts": [
                    {
                        "slug": "warnings-and-insurance",
                        "body": "<h2>FCDO advises against all but essential travel to parts of Pakistan</h2><p>The FCDO advises against all but essential travel to Balochistan and KPK.</p>"
                    },
                    {
                        "slug": "safety-and-security",
                        "body": "<p>FCDO advises against all travel to Balochistan province.</p>"
                    }
                ]
            }
        }
        """.trimIndent()
    }
}
