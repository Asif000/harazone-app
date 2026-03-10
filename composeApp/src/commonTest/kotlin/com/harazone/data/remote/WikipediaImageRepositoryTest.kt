package com.harazone.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val JSON_CT = "application/json"
private const val COMMONS_RESPONSE = """{"batchcomplete":"","query":{"pages":{"12345":{"pageid":12345,"ns":6,"title":"File:Photo.jpg","imageinfo":[{"thumburl":"https://upload.wikimedia.org/commons/thumb/photo.jpg","thumbwidth":800,"thumbheight":600,"url":"https://upload.wikimedia.org/commons/photo.jpg"}]}}}}"""

class WikipediaImageRepositoryTest {

    @Test
    fun getImageUrl_happyPath_returnsThumbnailSource() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"title":"Test","thumbnail":{"source":"https://upload.wikimedia.org/img.jpg","width":320,"height":240}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT)
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        assertEquals("https://upload.wikimedia.org/img.jpg", repo.getImageUrl(null, "Test_Place"))
    }

    @Test
    fun getImageUrl_404_returnsNull() = runTest {
        val engine = MockEngine { _ ->
            respond("Not found", status = HttpStatusCode.NotFound)
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        assertNull(repo.getImageUrl(null, "Unknown_Place"))
    }

    @Test
    fun getImageUrl_noThumbnailField_returnsNull() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"title":"Test","extract":"Some text with no thumbnail"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT)
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        assertNull(repo.getImageUrl(null, "Test_Place"))
    }

    @Test
    fun getImageUrl_networkException_returnsNullWithoutThrowing() = runTest {
        val engine = MockEngine { _ -> throw RuntimeException("Network error") }
        val repo = WikipediaImageRepository(HttpClient(engine))
        assertNull(repo.getImageUrl(null, "Test_Place"))
    }

    @Test
    fun getImageUrl_nonAsciiName_encodedInUrl() = runTest {
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"title":"Test"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, JSON_CT))
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        repo.getImageUrl(null, "São Jorge")
        assertTrue(capturedUrl.contains("S%C3%A3o"), "Non-ASCII 'ã' must be percent-encoded in the URL")
    }

    @Test
    fun getImageUrl_wikiSlugSucceeds_poiNameNotCalled() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            respond(
                content = """{"title":"Test","thumbnail":{"source":"https://img.example.com/photo.jpg","width":100,"height":100}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT)
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val result = repo.getImageUrl("Valid_Wiki_Slug", "POI Name")
        assertEquals("https://img.example.com/photo.jpg", result)
        assertEquals(1, callCount, "Only one Wikipedia call when wikiSlug succeeds")
    }

    @Test
    fun getImageUrl_commonsHappyPath_returnsThumburl() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            // First two calls: Wikipedia slug + name (both 404)
            if (callCount <= 2) respond("Not found", status = HttpStatusCode.NotFound)
            // Third call: Commons search succeeds
            else respond(
                content = COMMONS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT)
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val result = repo.getImageUrl("Bad_Slug", "Some Place")
        assertEquals("https://upload.wikimedia.org/commons/thumb/photo.jpg", result)
        assertEquals(3, callCount, "Should try wiki slug, poi name, then Commons")
    }

    @Test
    fun getImageUrl_allTiersFail_returnsNull() = runTest {
        val engine = MockEngine { _ ->
            respond("Not found", status = HttpStatusCode.NotFound)
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        assertNull(repo.getImageUrl("Bad_Slug", "Unknown_Place"))
    }

    @Test
    fun getImageUrl_commonsNoPages_returnsNull() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            if (callCount <= 2) respond("Not found", status = HttpStatusCode.NotFound)
            else respond(
                content = """{"batchcomplete":"","query":{"searchinfo":{"totalhits":0}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT)
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        assertNull(repo.getImageUrl("Bad_Slug", "No_Results"))
    }

    @Test
    fun getImageUrl_wikiSlugFails_fallsBackToPoiName() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            if (callCount == 1) respond("", HttpStatusCode.NotFound)
            else respond(
                content = """{"title":"Test","thumbnail":{"source":"https://img.example.com/fallback.jpg","width":100,"height":100}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT)
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val result = repo.getImageUrl("Bad_Wiki_Slug", "POI Name")
        assertEquals("https://img.example.com/fallback.jpg", result)
        assertEquals(2, callCount, "Two Wikipedia calls when wikiSlug fails and poiName fallback is used")
    }
}
