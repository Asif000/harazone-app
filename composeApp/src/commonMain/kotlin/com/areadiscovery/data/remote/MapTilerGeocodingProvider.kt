package com.areadiscovery.data.remote

import com.areadiscovery.BuildKonfig
import com.areadiscovery.domain.model.GeocodingSuggestion
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

@Serializable
private data class GeocodingResponse(val features: List<GeocodingFeature>)

@Serializable
private data class GeocodingFeature(
    val text: String,
    @SerialName("place_name") val placeName: String,
    val center: List<Double>,
)

open class MapTilerGeocodingProvider(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun search(query: String, limit: Int = 5): Result<List<GeocodingSuggestion>> {
        return try {
            val sanitized = query.replace(Regex("[/#?&]"), " ").trim()
            val encoded = sanitized.encodeURLPathPart()
            val url = "https://api.maptiler.com/geocoding/$encoded.json" +
                "?key=${BuildKonfig.MAPTILER_API_KEY}&limit=$limit"
            val body = httpClient.get(url).bodyAsText()
            val response = json.decodeFromString<GeocodingResponse>(body)
            Result.success(response.features.mapNotNull { f ->
                if (f.center.size < 2) return@mapNotNull null
                GeocodingSuggestion(
                    name = f.text,
                    fullAddress = f.placeName,
                    latitude = f.center[1],
                    longitude = f.center[0],
                    distanceKm = null,
                )
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
