package com.harazone.data.remote

import com.harazone.BuildKonfig
import com.harazone.domain.model.GeocodingSuggestion
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.harazone.domain.provider.LocaleProvider
import kotlin.coroutines.cancellation.CancellationException

@Serializable
private data class GeocodingResponse(val features: List<GeocodingFeature>)

@Serializable
private data class GeocodingFeature(
    val text: String,
    @SerialName("place_name") val placeName: String,
    val center: List<Double>,
    val context: List<GeocodingContext>? = null,
    val properties: GeocodingProperties? = null,
)

@Serializable
private data class GeocodingContext(
    val id: String? = null,
    val text: String? = null,
    @SerialName("short_code") val shortCode: String? = null,
)

@Serializable
private data class GeocodingProperties(
    @SerialName("country_code") val countryCode: String? = null,
)

data class ReverseGeocodeInfo(
    val countryCode: String,
    val countryName: String,
    val regionName: String?,
)

open class MapTilerGeocodingProvider(
    private val httpClient: HttpClient,
    private val localeProvider: LocaleProvider,
) {

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun search(query: String, limit: Int = 5): Result<List<GeocodingSuggestion>> {
        return try {
            val sanitized = query.replace(Regex("[/#?&]"), " ").trim()
            val encoded = sanitized.encodeURLPathPart()
            val lang = localeProvider.languageTag.substringBefore("-")
            val url = "https://api.maptiler.com/geocoding/$encoded.json" +
                "?key=${BuildKonfig.MAPTILER_API_KEY}&limit=$limit&language=$lang"
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

    open suspend fun reverseGeocodeInfo(latitude: Double, longitude: Double): Result<ReverseGeocodeInfo> {
        return try {
            val lang = localeProvider.languageTag.substringBefore("-")
            val url = "https://api.maptiler.com/geocoding/$longitude,$latitude.json" +
                "?key=${BuildKonfig.MAPTILER_API_KEY}&limit=1&language=$lang"
            val body = httpClient.get(url).bodyAsText()
            val response = json.decodeFromString<GeocodingResponse>(body)
            val feature = response.features.firstOrNull()
                ?: return Result.failure(Exception("No reverse geocode result"))

            // Extract country code from properties or context
            val countryCode = feature.properties?.countryCode?.uppercase()
                ?: feature.context?.firstOrNull { it.id?.startsWith("country") == true }?.shortCode?.uppercase()
                ?: ""

            val countryName = feature.context?.firstOrNull { it.id?.startsWith("country") == true }?.text ?: ""
            val regionName = feature.context?.firstOrNull { it.id?.startsWith("region") == true }?.text

            Result.success(ReverseGeocodeInfo(countryCode, countryName, regionName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
