package com.harazone.data.remote

import com.harazone.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class WikipediaImageRepository(private val httpClient: HttpClient) {

    companion object {
        private const val USER_AGENT = "HaraZone/1.0 (https://github.com/harazone; contact@harazone.com)"
        internal const val MAX_CONCURRENT_REQUESTS = 5
        private val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class WikiSummary(val thumbnail: WikiThumbnail? = null)

    @Serializable
    private data class WikiThumbnail(val source: String = "")

    /**
     * Image lookup chain:
     * 1. Wikipedia summary thumbnail via wikiSlug (most accurate)
     * 2. Wikipedia summary thumbnail via poiName
     * 3. Wikimedia Commons photo search by poiName
     * Returns null if all attempts fail.
     */
    suspend fun getImageUrl(wikiSlug: String?, poiName: String): String? {
        if (wikiSlug != null) {
            val url = fetchThumbnail(wikiSlug)
            if (url != null) return url
        }
        fetchThumbnail(poiName)?.let { return it }
        return searchCommonsImage(poiName)
    }

    private suspend fun fetchThumbnail(title: String): String? {
        return try {
            val encoded = title.encodeURLPathPart()
            val response = httpClient.get(
                "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            ) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                timeout {
                    requestTimeoutMillis = 5_000
                    socketTimeoutMillis = 5_000
                }
            }
            if (!response.status.isSuccess()) return null
            val summary = json.decodeFromString<WikiSummary>(response.bodyAsText())
            summary.thumbnail?.source?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d { "WikipediaImageRepository: failed to fetch image for '$title': ${e.message}" }
            null
        }
    }

    private suspend fun searchCommonsImage(query: String): String? {
        return try {
            val encoded = query.encodeURLQueryComponent()
            val response = httpClient.get(
                "https://commons.wikimedia.org/w/api.php?action=query" +
                    "&generator=search&gsrnamespace=6&gsrsearch=$encoded" +
                    "&gsrlimit=1&prop=imageinfo&iiprop=url&iiurlwidth=800" +
                    "&format=json"
            ) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                timeout {
                    requestTimeoutMillis = 5_000
                    socketTimeoutMillis = 5_000
                }
            }
            if (!response.status.isSuccess()) return null
            val body = json.decodeFromString<JsonObject>(response.bodyAsText())
            val pages = body["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
            val firstPage = pages.values.firstOrNull()?.jsonObject ?: return null
            val imageInfo = firstPage["imageinfo"]
                ?.let { json.decodeFromString<List<CommonsImageInfo>>(it.toString()) }
                ?.firstOrNull()
            imageInfo?.thumburl?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d { "WikipediaImageRepository: Commons search failed for '$query': ${e.message}" }
            null
        }
    }

    @Serializable
    private data class CommonsImageInfo(val thumburl: String = "")
}
