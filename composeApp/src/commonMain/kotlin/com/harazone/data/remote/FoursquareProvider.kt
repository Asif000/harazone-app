package com.harazone.data.remote

import co.touchlab.kermit.Logger
import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.domain.model.POI
import com.harazone.domain.provider.ApiKeyProvider
import com.harazone.util.AppClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

internal class FoursquareProvider(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val database: AreaDiscoveryDatabase,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = Logger.withTag("FoursquareProvider")

    suspend fun enrichPoi(poi: POI): Result<POI> {
        if (apiKeyProvider.foursquareApiKey.isBlank()) return Result.success(poi)
        if (poi.latitude == null || poi.longitude == null) return Result.success(poi)
        if (poi.name.length < 6) return Result.success(poi)
        // Idempotency: skip if any social field already populated (cache hit applied upstream)
        if (poi.instagram != null || poi.facebook != null || poi.twitter != null) return Result.success(poi)

        return try {
            // Check cache first
            val cached = database.foursquare_social_cacheQueries
                .getSocialData(poi.savedId).executeAsOneOrNull()
            if (cached != null && cached.expires_at > clock.nowMs()) {
                val anySocial = cached.instagram != null || cached.facebook != null || cached.twitter != null
                return if (anySocial) {
                    Result.success(poi.copy(
                        instagram = cached.instagram,
                        facebook = cached.facebook,
                        twitter = cached.twitter,
                    ))
                } else {
                    Result.success(poi) // cached miss — no social data for this POI
                }
            }

            // Fetch from API
            val responseText = httpClient.get("https://places-api.foursquare.com/places/search") {
                header("Authorization", "Bearer ${apiKeyProvider.foursquareApiKey}")
                header("X-Places-Api-Version", "2025-06-17")
                url {
                    parameters.append("ll", "${poi.latitude},${poi.longitude}")
                    parameters.append("query", poi.name)
                    parameters.append("radius", "300")  // 300m — prevents cross-city false matches
                    parameters.append("limit", "1")
                    parameters.append("fields", "name,social_media")
                }
            }.bodyAsText()

            val root = json.parseToJsonElement(responseText).jsonObject
            val results = root["results"]?.jsonArray ?: return Result.success(poi)
            if (results.isEmpty()) return cacheAndReturn(poi, null, null, null)

            val place = results[0].jsonObject
            val displayName = place["name"]?.jsonPrimitive?.contentOrNull
                ?: return cacheAndReturn(poi, null, null, null)

            if (!PoiMatchUtils.isConfidentMatch(poi.name, displayName)) {
                return cacheAndReturn(poi, null, null, null)
            }

            val social = place["social_media"]?.jsonObject
            val instagram = social?.get("instagram")?.jsonPrimitive?.contentOrNull
            val facebook = social?.get("facebook_id")?.jsonPrimitive?.contentOrNull
            val twitter = social?.get("twitter")?.jsonPrimitive?.contentOrNull

            cacheAndReturn(poi, instagram, facebook, twitter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Foursquare enrichment failed for '${poi.name}'" }
            Result.success(poi)
        }
    }

    private fun cacheAndReturn(
        poi: POI,
        instagram: String?,
        facebook: String?,
        twitter: String?,
    ): Result<POI> {
        val now = clock.nowMs()
        database.foursquare_social_cacheQueries.insertOrReplace(
            saved_id = poi.savedId,
            instagram = instagram,
            facebook = facebook,
            twitter = twitter,
            expires_at = now + CACHE_TTL_MS,
            cached_at = now,
        )
        return if (instagram != null || facebook != null || twitter != null) {
            Result.success(poi.copy(instagram = instagram, facebook = facebook, twitter = twitter))
        } else {
            Result.success(poi)
        }
    }

    companion object {
        internal const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days — social handles change rarely
    }
}
