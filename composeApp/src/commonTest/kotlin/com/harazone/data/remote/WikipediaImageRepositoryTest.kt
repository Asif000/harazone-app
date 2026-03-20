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

    // --- getImageUrls() tests ---

    @Test
    fun getImageUrls_happyPath_commonsReturnsMultipleImages() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            // Calls 1-2: Wikipedia slug + name (404)
            if (callCount <= 2) respond("Not found", status = HttpStatusCode.NotFound)
            // Call 3: Commons returns 3 results
            else respond(
                content = COMMONS_MULTI_RESPONSE_3,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, JSON_CT),
            )
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val urls = repo.getImageUrls("Bad_Slug", "Some Place")
        assertEquals(3, urls.size)
        assertTrue(urls.all { it.startsWith("https://") })
    }

    @Test
    fun getImageUrls_wikipedia_plus_commons_combined() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            when (callCount) {
                // Call 1: Wikipedia slug thumbnail (success → name fetch skipped)
                1 -> respond(
                    content = """{"title":"Test","thumbnail":{"source":"https://wiki.example.com/slug.jpg","width":320,"height":240}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CT),
                )
                // Call 2: Commons search
                else -> respond(
                    content = COMMONS_MULTI_RESPONSE_2,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CT),
                )
            }
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val urls = repo.getImageUrls("Valid_Slug", "Some Place")
        assertEquals(3, urls.size, "Wikipedia slug + 2 Commons = 3 distinct URLs")
        assertEquals("https://wiki.example.com/slug.jpg", urls.first(), "Slug thumbnail should be first")
        assertTrue(urls.distinct().size == urls.size, "All URLs must be distinct")
    }

    @Test
    fun getImageUrls_allFail_returnsEmptyList() = runTest {
        val engine = MockEngine { _ -> throw RuntimeException("Network error") }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val urls = repo.getImageUrls("Slug", "Place")
        assertTrue(urls.isEmpty(), "Must return empty list, not throw")
    }

    @Test
    fun getImageUrls_dedup_sameUrlFromWikiAndCommons() = runTest {
        val sharedUrl = "https://upload.wikimedia.org/commons/thumb/shared.jpg"
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            when (callCount) {
                1 -> respond(
                    content = """{"title":"Test","thumbnail":{"source":"$sharedUrl","width":320,"height":240}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CT),
                )
                2 -> respond("Not found", status = HttpStatusCode.NotFound)
                else -> respond(
                    content = """{"batchcomplete":"","query":{"pages":{"-1":{"pageid":1,"ns":6,"title":"File:Shared.jpg","imageinfo":[{"thumburl":"$sharedUrl","thumbwidth":800,"thumbheight":600,"url":"$sharedUrl"}]}}}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CT),
                )
            }
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val urls = repo.getImageUrls("Slug", "Place")
        assertEquals(1, urls.size, "Duplicate URL should appear only once")
        assertEquals(sharedUrl, urls.first())
    }

    @Test
    fun getImageUrls_wikiSlugNull_skipsSlugCall_stillReturnsResults() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            when (callCount) {
                // Call 1: Wikipedia name lookup
                1 -> respond(
                    content = """{"title":"Test","thumbnail":{"source":"https://wiki.example.com/name.jpg","width":320,"height":240}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CT),
                )
                // Call 2: Commons search
                else -> respond(
                    content = COMMONS_MULTI_RESPONSE_2,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, JSON_CT),
                )
            }
        }
        val repo = WikipediaImageRepository(HttpClient(engine))
        val urls = repo.getImageUrls(wikiSlug = null, poiName = "Some Place")
        assertTrue(urls.isNotEmpty(), "Must return results even with null wikiSlug")
        assertEquals(2, callCount, "Slug-based Wikipedia call NOT made when wikiSlug is null")
    }

    companion object {
        private const val COMMONS_MULTI_RESPONSE_3 = """{"batchcomplete":"","query":{"pages":{"-1":{"pageid":1,"ns":6,"title":"File:A.jpg","imageinfo":[{"thumburl":"https://commons.example.com/a.jpg","thumbwidth":800,"thumbheight":600,"url":"https://commons.example.com/a_full.jpg"}]},"-2":{"pageid":2,"ns":6,"title":"File:B.jpg","imageinfo":[{"thumburl":"https://commons.example.com/b.jpg","thumbwidth":800,"thumbheight":600,"url":"https://commons.example.com/b_full.jpg"}]},"-3":{"pageid":3,"ns":6,"title":"File:C.jpg","imageinfo":[{"thumburl":"https://commons.example.com/c.jpg","thumbwidth":800,"thumbheight":600,"url":"https://commons.example.com/c_full.jpg"}]}}}}"""
        private const val COMMONS_MULTI_RESPONSE_2 = """{"batchcomplete":"","query":{"pages":{"-1":{"pageid":1,"ns":6,"title":"File:X.jpg","imageinfo":[{"thumburl":"https://commons.example.com/x.jpg","thumbwidth":800,"thumbheight":600,"url":"https://commons.example.com/x_full.jpg"}]},"-2":{"pageid":2,"ns":6,"title":"File:Y.jpg","imageinfo":[{"thumburl":"https://commons.example.com/y.jpg","thumbwidth":800,"thumbheight":600,"url":"https://commons.example.com/y_full.jpg"}]}}}}"""
    }
}
