package com.harazone.data.remote

import co.touchlab.kermit.Logger
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.data.local.Places_enrichment_cache
import com.harazone.domain.model.POI
import com.harazone.domain.provider.ApiKeyProvider
import com.harazone.domain.provider.PlacesProvider
import com.harazone.util.AppClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

internal class GooglePlacesProvider(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
) : PlacesProvider {

    @Serializable
    private data class PlacesRequest(
        val textQuery: String,
        val locationBias: LocationBias,
        val maxResultCount: Int = 1,
    )

    @Serializable
    private data class LocationBias(val circle: Circle)

    @Serializable
    private data class Circle(val center: LatLng, val radius: Double = 100.0)

    @Serializable
    private data class LatLng(val latitude: Double, val longitude: Double)

    private val json = Json { ignoreUnknownKeys = true }
    private val log = Logger.withTag("GooglePlacesProvider")

    override suspend fun enrichPoi(poi: POI): Result<POI> {
        if (poi.latitude == null || poi.longitude == null) return Result.success(poi)
        if (poi.name.length < 6) return Result.success(poi)
        if (poi.reviewCount != null) return Result.success(poi) // already enriched
        return try {
            val cached = database.places_enrichment_cacheQueries
                .getPlacesData(poi.savedId).executeAsOneOrNull()
            if (cached != null && cached.expires_at > clock.nowMs()) {
                return Result.success(applyCache(poi, cached))
            }

            val responseText = fetchPlacesData(poi)
            val parseResult = parsePlacesResponse(responseText, poi)
            val enriched = if (parseResult.photoRefs.isNotEmpty()) {
                applyPhotos(parseResult.poi, parseResult.photoRefs)
            } else {
                parseResult.poi
            }

            if (enriched.reviewCount != null) {
                val now = clock.nowMs()
                database.places_enrichment_cacheQueries.insertOrReplace(
                    saved_id = poi.savedId,
                    hours = enriched.hours,
                    live_status = enriched.liveStatus,
                    rating = enriched.rating?.toDouble(),
                    review_count = enriched.reviewCount.toLong(),
                    price_range = enriched.priceRange,
                    image_url = enriched.imageUrl,
                    image_urls = enriched.imageUrls.joinToString("|"),
                    expires_at = now + CACHE_TTL_MS,
                    cached_at = now,
                )
            }
            Result.success(enriched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Places enrichment failed for '${poi.name}'" }
            Result.success(poi)
        }
    }

    private suspend fun fetchPlacesData(poi: POI): String {
        val request = PlacesRequest(
            textQuery = poi.name,
            locationBias = LocationBias(Circle(LatLng(poi.latitude!!, poi.longitude!!)))
        )
        return httpClient.post(PLACES_SEARCH_URL) {
            header("X-Goog-Api-Key", apiKeyProvider.placesApiKey)
            header("X-Goog-FieldMask", FIELD_MASK)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }.bodyAsText()
    }

    internal data class PlacesParseResult(
        val poi: POI,
        val photoRefs: List<String> = emptyList(),
    )

    internal fun parsePlacesResponse(responseText: String, poi: POI): PlacesParseResult {
        val root = json.parseToJsonElement(responseText).jsonObject
        val places = root["places"]?.jsonArray ?: return PlacesParseResult(poi)
        if (places.isEmpty()) return PlacesParseResult(poi)

        val place = places[0].jsonObject
        val displayName = place["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: return PlacesParseResult(poi)

        if (!isConfidentMatch(poi.name, displayName)) return PlacesParseResult(poi)

        val currentHours = place["currentOpeningHours"]?.jsonObject
        val regularHours = place["regularOpeningHours"]?.jsonObject
        val openNow = currentHours?.get("openNow")?.jsonPrimitive?.booleanOrNull
        val weekdays = (currentHours ?: regularHours)?.get("weekdayDescriptions")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }

        val rating = (place["rating"] as? JsonPrimitive)?.doubleOrNull
        val reviewCount = (place["userRatingCount"] as? JsonPrimitive)?.intOrNull
        val priceLevel = (place["priceLevel"] as? JsonPrimitive)?.contentOrNull

        val photoRefs = place["photos"]?.jsonArray
            ?.take(MAX_PHOTOS)
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?: emptyList()

        val enriched = poi.copy(
            liveStatus = when (openNow) { true -> "open"; false -> "closed"; null -> poi.liveStatus },
            hours = if (weekdays != null) weekdays.joinToString("\n") else poi.hours,
            rating = rating?.toFloat() ?: poi.rating,
            reviewCount = reviewCount ?: 0,
            priceRange = mapPriceLevel(priceLevel) ?: poi.priceRange,
        )
        return PlacesParseResult(enriched, photoRefs)
    }

    private suspend fun applyPhotos(poi: POI, photoRefs: List<String>): POI {
        val urls = photoRefs.mapNotNull { ref -> fetchPhotoUrl(ref) }
        if (urls.isEmpty()) return poi
        return poi.copy(
            imageUrl = urls.first(),
            imageUrls = urls,
        )
    }

    private suspend fun fetchPhotoUrl(photoRef: String): String? = try {
        val responseText = httpClient.get("$PHOTOS_MEDIA_URL$photoRef/media") {
            header("X-Goog-Api-Key", apiKeyProvider.placesApiKey)
            url { parameters.append("maxHeightPx", "800") }
            url { parameters.append("skipHttpRedirect", "true") }
        }.bodyAsText()
        val root = json.parseToJsonElement(responseText).jsonObject
        root["photoUri"]?.jsonPrimitive?.contentOrNull
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w(e) { "Failed to fetch photo URL for $photoRef" }
        null
    }

    internal fun isConfidentMatch(poiName: String, displayName: String): Boolean {
        val normalize = { s: String -> s.lowercase().replace(NON_ALNUM_REGEX, "").trim() }
        val poiTokens = normalize(poiName).split(" ").filter { it.length >= 3 }.toSet()
        val dispTokens = normalize(displayName).split(" ").filter { it.length >= 3 }.toSet()
        if (poiTokens.isEmpty() || dispTokens.isEmpty()) return false
        val (shorter, longer) = if (poiTokens.size <= dispTokens.size) poiTokens to dispTokens else dispTokens to poiTokens
        return shorter.all { it in longer }
    }

    private fun applyCache(poi: POI, cached: Places_enrichment_cache): POI {
        val cachedImageUrls = cached.image_urls
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return poi.copy(
            hours = cached.hours ?: poi.hours,
            liveStatus = cached.live_status ?: poi.liveStatus,
            rating = cached.rating?.toFloat() ?: poi.rating,
            reviewCount = cached.review_count?.toInt() ?: 0,
            priceRange = cached.price_range ?: poi.priceRange,
            imageUrl = cached.image_url ?: poi.imageUrl,
            imageUrls = cachedImageUrls.ifEmpty { poi.imageUrls },
        )
    }

    internal fun mapPriceLevel(priceLevel: String?): String? = when (priceLevel) {
        "PRICE_LEVEL_FREE" -> "Free"
        "PRICE_LEVEL_INEXPENSIVE" -> "$"
        "PRICE_LEVEL_MODERATE" -> "$$"
        "PRICE_LEVEL_EXPENSIVE" -> "$$$"
        "PRICE_LEVEL_VERY_EXPENSIVE" -> "$$$$"
        else -> null
    }

    companion object {
        private const val PLACES_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
        private const val FIELD_MASK = "places.id,places.displayName,places.currentOpeningHours,places.regularOpeningHours,places.rating,places.userRatingCount,places.priceLevel,places.photos"
        private const val PHOTOS_MEDIA_URL = "https://places.googleapis.com/v1/"
        private const val MAX_PHOTOS = 5
        internal const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9 ]")
    }
}
