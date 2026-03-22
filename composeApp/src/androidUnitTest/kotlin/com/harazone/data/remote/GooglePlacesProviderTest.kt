package com.harazone.data.remote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.POI
import com.harazone.domain.provider.ApiKeyProvider
import com.harazone.fakes.FakeClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GooglePlacesProviderTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AreaDiscoveryDatabase

    private val basePoi = POI(
        name = "Kyoto Tower",
        type = "entertainment",
        description = "Observation tower",
        confidence = Confidence.MEDIUM,
        latitude = 34.9876,
        longitude = 135.7590,
        liveStatus = "busy",
        hours = "9am-9pm",
        rating = 3.5f,
        priceRange = "$",
    )

    private val fakeApiKeyProvider = object : ApiKeyProvider {
        override val geminiApiKey: String = ""
        override val placesApiKey: String = "test-key"
    }

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AreaDiscoveryDatabase.Schema.create(driver)
        database = AreaDiscoveryDatabase(driver)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    private fun createProvider(
        responseText: String = "{}",
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        clock: FakeClock = FakeClock(),
    ): GooglePlacesProvider {
        val engine = MockEngine { _ ->
            respond(
                content = responseText,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return GooglePlacesProvider(
            httpClient = HttpClient(engine),
            apiKeyProvider = fakeApiKeyProvider,
            database = database,
            clock = clock,
        )
    }

    // --- parsePlacesResponse ---

    @Test
    fun parsePlacesResponse_returns_enriched_poi_on_confident_match() {
        val provider = createProvider()
        val result = provider.parsePlacesResponse(CONFIDENT_MATCH_RESPONSE, basePoi)
        assertEquals("open", result.liveStatus)
        assertEquals(4.2f, result.rating)
        assertEquals(1840, result.reviewCount)
        assertEquals("$$", result.priceRange)
        assertTrue(result.hours!!.contains("Monday"))
    }

    @Test
    fun parsePlacesResponse_returns_original_poi_on_name_mismatch() {
        val provider = createProvider()
        val result = provider.parsePlacesResponse(NAME_MISMATCH_RESPONSE, basePoi)
        assertEquals(basePoi, result)
    }

    @Test
    fun parsePlacesResponse_returns_original_poi_on_empty_places_array() {
        val provider = createProvider()
        val result = provider.parsePlacesResponse("""{"places":[]}""", basePoi)
        assertEquals(basePoi, result)
    }

    @Test
    fun parsePlacesResponse_openNow_true_sets_liveStatus_open() {
        val provider = createProvider()
        val poi = basePoi.copy(liveStatus = "closed")
        val result = provider.parsePlacesResponse(CONFIDENT_MATCH_RESPONSE, poi)
        assertEquals("open", result.liveStatus)
    }

    @Test
    fun parsePlacesResponse_openNow_false_sets_liveStatus_closed() {
        val provider = createProvider()
        val result = provider.parsePlacesResponse(CLOSED_RESPONSE, basePoi)
        assertEquals("closed", result.liveStatus)
    }

    @Test
    fun parsePlacesResponse_openNow_null_preserves_gemini_liveStatus() {
        val provider = createProvider()
        val poi = basePoi.copy(liveStatus = "busy")
        val result = provider.parsePlacesResponse(NO_OPEN_NOW_RESPONSE, poi)
        assertEquals("busy", result.liveStatus)
    }

    @Test
    fun parsePlacesResponse_uses_regularOpeningHours_when_currentOpeningHours_absent() {
        val provider = createProvider()
        val poi = basePoi.copy(liveStatus = null)
        val result = provider.parsePlacesResponse(REGULAR_HOURS_ONLY_RESPONSE, poi)
        assertTrue(result.hours!!.contains("Monday"))
        assertNull(result.liveStatus)
    }

    // --- mapPriceLevel ---

    @Test
    fun mapPriceLevel_maps_all_levels_correctly() {
        val provider = createProvider()
        assertEquals("Free", provider.mapPriceLevel("PRICE_LEVEL_FREE"))
        assertEquals("$", provider.mapPriceLevel("PRICE_LEVEL_INEXPENSIVE"))
        assertEquals("$$", provider.mapPriceLevel("PRICE_LEVEL_MODERATE"))
        assertEquals("$$$", provider.mapPriceLevel("PRICE_LEVEL_EXPENSIVE"))
        assertEquals("$$$$", provider.mapPriceLevel("PRICE_LEVEL_VERY_EXPENSIVE"))
        assertNull(provider.mapPriceLevel(null))
        assertNull(provider.mapPriceLevel("UNKNOWN"))
    }

    // --- isConfidentMatch ---

    @Test
    fun isConfidentMatch_returns_true_for_token_subset_match() {
        val provider = createProvider()
        assertTrue(provider.isConfidentMatch("Kyoto Tower", "Kyoto Tower Observatory"))
    }

    @Test
    fun isConfidentMatch_returns_false_for_no_token_overlap() {
        val provider = createProvider()
        assertFalse(provider.isConfidentMatch("Sakura Cafe", "Tokyo Ramen House"))
    }

    @Test
    fun isConfidentMatch_returns_false_for_empty_tokens() {
        val provider = createProvider()
        assertFalse(provider.isConfidentMatch("ab", "cd"))
    }

    @Test
    fun isConfidentMatch_returns_true_for_exact_match() {
        val provider = createProvider()
        assertTrue(provider.isConfidentMatch("Golden Gate Bridge", "Golden Gate Bridge"))
    }

    @Test
    fun isConfidentMatch_ignores_punctuation() {
        val provider = createProvider()
        assertTrue(provider.isConfidentMatch("McDonald's", "McDonalds Restaurant"))
    }

    // --- enrichPoi edge cases ---

    @Test
    fun enrichPoi_skips_poi_with_name_shorter_than_6_chars() = runTest {
        val errorEngine = MockEngine { _ -> error("Should not be called") }
        val provider = GooglePlacesProvider(
            httpClient = HttpClient(errorEngine),
            apiKeyProvider = fakeApiKeyProvider,
            database = database,
            clock = FakeClock(),
        )
        val shortNamePoi = basePoi.copy(name = "Dojo")
        val result = provider.enrichPoi(shortNamePoi)
        assertTrue(result.isSuccess)
        assertEquals(shortNamePoi, result.getOrNull())
    }

    @Test
    fun enrichPoi_poi_without_coords_returns_original_immediately() = runTest {
        val errorEngine = MockEngine { _ -> error("Should not be called") }
        val provider = GooglePlacesProvider(
            httpClient = HttpClient(errorEngine),
            apiKeyProvider = fakeApiKeyProvider,
            database = database,
            clock = FakeClock(),
        )
        val noCoordsPoi = basePoi.copy(latitude = null, longitude = null)
        val result = provider.enrichPoi(noCoordsPoi)
        assertTrue(result.isSuccess)
        assertEquals(noCoordsPoi, result.getOrNull())
    }

    @Test
    fun enrichPoi_network_failure_returns_original_poi() = runTest {
        val provider = createProvider(statusCode = HttpStatusCode.InternalServerError)
        val result = provider.enrichPoi(basePoi)
        assertTrue(result.isSuccess)
        assertEquals(basePoi, result.getOrNull())
    }

    @Test
    fun enrichPoi_returns_cached_result_within_24h() = runTest {
        val clock = FakeClock(nowMs = 1_000_000_000L)
        // Pre-populate cache
        database.places_enrichment_cacheQueries.insertOrReplace(
            saved_id = basePoi.savedId,
            hours = "Cached hours",
            live_status = "open",
            rating = 4.5,
            review_count = 999,
            price_range = "$$$",
            expires_at = clock.nowMs + GooglePlacesProvider.CACHE_TTL_MS,
            cached_at = clock.nowMs,
        )
        // MockEngine that would fail if called
        val errorEngine = MockEngine { _ -> error("Should not make HTTP call") }
        val provider = GooglePlacesProvider(
            httpClient = HttpClient(errorEngine),
            apiKeyProvider = fakeApiKeyProvider,
            database = database,
            clock = clock,
        )
        val result = provider.enrichPoi(basePoi)
        assertTrue(result.isSuccess)
        val enriched = result.getOrNull()!!
        assertEquals("Cached hours", enriched.hours)
        assertEquals("open", enriched.liveStatus)
        assertEquals(4.5f, enriched.rating)
        assertEquals(999, enriched.reviewCount)
        assertEquals("$$$", enriched.priceRange)
    }

    companion object {
        private val CONFIDENT_MATCH_RESPONSE = """
            {
                "places": [{
                    "id": "abc123",
                    "displayName": {"text": "Kyoto Tower"},
                    "currentOpeningHours": {
                        "openNow": true,
                        "weekdayDescriptions": ["Monday: 9:00 AM – 9:00 PM", "Tuesday: 9:00 AM – 9:00 PM"]
                    },
                    "rating": 4.2,
                    "userRatingCount": 1840,
                    "priceLevel": "PRICE_LEVEL_MODERATE"
                }]
            }
        """.trimIndent()

        private val CLOSED_RESPONSE = """
            {
                "places": [{
                    "id": "abc123",
                    "displayName": {"text": "Kyoto Tower"},
                    "currentOpeningHours": {
                        "openNow": false
                    },
                    "rating": 4.2,
                    "userRatingCount": 1840
                }]
            }
        """.trimIndent()

        private val NAME_MISMATCH_RESPONSE = """
            {
                "places": [{
                    "id": "abc123",
                    "displayName": {"text": "Completely Different Place"},
                    "rating": 4.2,
                    "userRatingCount": 1840
                }]
            }
        """.trimIndent()

        private val NO_OPEN_NOW_RESPONSE = """
            {
                "places": [{
                    "id": "abc123",
                    "displayName": {"text": "Kyoto Tower"},
                    "rating": 4.0,
                    "userRatingCount": 500
                }]
            }
        """.trimIndent()

        private val REGULAR_HOURS_ONLY_RESPONSE = """
            {
                "places": [{
                    "id": "abc123",
                    "displayName": {"text": "Kyoto Tower"},
                    "regularOpeningHours": {
                        "weekdayDescriptions": ["Monday: 9:00 AM – 9:00 PM", "Tuesday: 9:00 AM – 9:00 PM"]
                    },
                    "rating": 4.0,
                    "userRatingCount": 500
                }]
            }
        """.trimIndent()
    }
}
