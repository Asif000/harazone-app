package com.harazone.data.repository

import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.data.local.Area_bucket_cache
import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketContent
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.Confidence
import com.harazone.BuildKonfig
import com.harazone.data.remote.WikipediaImageRepository
import com.harazone.domain.model.ConnectivityState
import com.harazone.domain.model.POI
import com.harazone.domain.model.Source
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.domain.repository.AreaRepository
import com.harazone.util.AppClock
import com.harazone.util.AppLogger
import com.harazone.util.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class AreaRepositoryImpl(
    private val aiProvider: AreaIntelligenceProvider,
    private val database: AreaDiscoveryDatabase,
    private val scope: CoroutineScope,
    private val clock: AppClock = SystemClock(),
    private val connectivityObserver: () -> Flow<ConnectivityState>,
    private val wikipediaImageRepository: WikipediaImageRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AreaRepository {

    companion object {
        private const val MS_PER_HOUR = 3_600_000L
        private const val MS_PER_DAY = 86_400_000L
        const val CACHE_TTL_STATIC_MS = 14 * MS_PER_DAY
        const val CACHE_TTL_SEMI_STATIC_MS = 3 * MS_PER_DAY
        const val CACHE_TTL_DYNAMIC_MS = 12 * MS_PER_HOUR
        private val json = Json { ignoreUnknownKeys = true }

    }

    private fun getTtlMs(bucketType: BucketType): Long = when (bucketType) {
        BucketType.HISTORY, BucketType.CHARACTER -> CACHE_TTL_STATIC_MS
        BucketType.COST, BucketType.NEARBY -> CACHE_TTL_SEMI_STATIC_MS
        BucketType.SAFETY, BucketType.WHATS_HAPPENING -> CACHE_TTL_DYNAMIC_MS
    }

    override fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
        val language = context.preferredLanguage
        val now = clock.nowMs()

        val cached = database.area_bucket_cacheQueries
            .getBucketsByAreaAndLanguage(areaName, language)
            .executeAsList()

        // Clean up expired cache entries after reading current area's data
        database.area_bucket_cacheQueries.deleteExpiredBuckets(now)
        database.area_poi_cacheQueries.deleteExpiredPois(now)

        // L2: Parse once, reuse — avoids duplicate type lookups
        val validParsed = cached.filter { it.expires_at > now }.mapNotNull { it.toBucketContent() }
        val staleParsed = cached.filter { it.expires_at <= now }.mapNotNull { it.toBucketContent() }

        if (validParsed.size == BucketType.entries.size) {
            // Full cache hit — emit all from cache
            AppLogger.d { "Cache HIT for '$areaName' — ${validParsed.size} buckets" }
            validParsed.forEach { emit(BucketUpdate.BucketComplete(it)) }
            emit(BucketUpdate.PortraitComplete(pois = resolveTileRefs(loadPoisFromCache(areaName, language, now))))
            return@flow
        }

        // Check connectivity before attempting AI call
        val connectivity = connectivityObserver().first()

        if (connectivity is ConnectivityState.Offline) {
            val allCached = validParsed + staleParsed
            if (allCached.isNotEmpty()) {
                allCached.forEach { emit(BucketUpdate.BucketComplete(it)) }
                emit(BucketUpdate.ContentAvailabilityNote("You're offline — showing last known content"))
            } else {
                emit(BucketUpdate.ContentAvailabilityNote("No content available offline for this area"))
            }
            emit(BucketUpdate.PortraitComplete(pois = resolveTileRefs(loadPoisFromCache(areaName, language, now))))
            return@flow
        }

        if (staleParsed.isNotEmpty()) {
            // Stale-while-revalidate: emit cached content immediately, refresh in background.
            // Errors in the background refresh are silently logged — intentional:
            // the caller already has stale content to display; propagating a refresh
            // failure would disrupt the UI for no user benefit.
            staleParsed.forEach { emit(BucketUpdate.BucketComplete(it)) }
            validParsed.forEach { emit(BucketUpdate.BucketComplete(it)) }
            scope.launch {
                aiProvider.streamAreaPortrait(areaName, context)
                    .catch { e -> AppLogger.e(e) { "Background refresh failed for area: $areaName" } }
                    .collect { update ->
                        if (update is BucketUpdate.BucketComplete) {
                            withContext(ioDispatcher) {
                                writeToCache(update.content, areaName, language)
                            }
                        }
                        if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
                            withContext(ioDispatcher) {
                                val enriched = enrichPoisWithImages(update.pois)
                                writePoisToCache(enriched, areaName, language)
                            }
                        }
                    }
            }
            emit(BucketUpdate.PortraitComplete(pois = resolveTileRefs(loadPoisFromCache(areaName, language, now))))
            return@flow
        }

        // Cache miss — stream from AI with error fallback
        AppLogger.d { "Cache MISS for '$areaName' — valid=${validParsed.size}, stale=${staleParsed.size}, needed=${BucketType.entries.size}" }
        try {
            aiProvider.streamAreaPortrait(areaName, context).collect { update ->
                if (update is BucketUpdate.PortraitComplete) {
                    val enriched = if (update.pois.isNotEmpty()) enrichPoisWithImages(update.pois) else update.pois
                    if (enriched.isNotEmpty()) writePoisToCache(enriched, areaName, language)
                    emit(BucketUpdate.PortraitComplete(resolveTileRefs(enriched)))
                } else {
                    emit(update)
                    if (update is BucketUpdate.BucketComplete) writeToCache(update.content, areaName, language)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(e) { "AI provider failed — falling back to cache" }
            val allCached = database.area_bucket_cacheQueries
                .getBucketsByAreaAndLanguage(areaName, language)
                .executeAsList()
                .mapNotNull { it.toBucketContent() }
            if (allCached.isNotEmpty()) {
                allCached.forEach { emit(BucketUpdate.BucketComplete(it)) }
                emit(BucketUpdate.ContentAvailabilityNote("Content from cache — may not be current"))
            } else {
                emit(BucketUpdate.ContentAvailabilityNote("Could not load area content — please try again"))
            }
            emit(BucketUpdate.PortraitComplete(pois = resolveTileRefs(loadPoisFromCache(areaName, language, now))))
        }
    }.flowOn(ioDispatcher)

    private suspend fun enrichPoisWithImages(pois: List<POI>): List<POI> = coroutineScope {
        AppLogger.d { "enrichPoisWithImages: enriching ${pois.size} POIs" }
        val semaphore = Semaphore(WikipediaImageRepository.MAX_CONCURRENT_REQUESTS)
        val result = pois.map { poi ->
            async {
                semaphore.withPermit {
                    val wikiUrl = wikipediaImageRepository.getImageUrl(poi.wikiSlug, poi.name)
                    val imageUrl = wikiUrl ?: buildSatelliteTileRef(poi.latitude, poi.longitude)
                    AppLogger.d { "enrichPoisWithImages: '${poi.name}' -> wiki=${wikiUrl != null}, mapTiler=${imageUrl != null && wikiUrl == null}, final=${imageUrl?.take(60)}" }
                    poi.copy(imageUrl = imageUrl)
                }
            }
        }.awaitAll()
        AppLogger.d { "enrichPoisWithImages: done — ${result.count { it.imageUrl != null }}/${result.size} have images" }
        result
    }

    private fun buildSatelliteTileRef(lat: Double?, lng: Double?): String? {
        if (lat == null || lng == null) return null
        val z = 17
        val n = 1 shl z // 2^z
        val x = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = lat * kotlin.math.PI / 180.0
        val y = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / kotlin.math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return "maptiler-satellite://$z/$x/$y"
    }

    private fun resolveTileRefs(pois: List<POI>): List<POI> = pois.map { poi ->
        val url = poi.imageUrl ?: return@map poi
        if (!url.startsWith("maptiler-satellite://")) return@map poi
        val parts = url.removePrefix("maptiler-satellite://").split("/")
        if (parts.size != 3) return@map poi
        poi.copy(imageUrl = "https://api.maptiler.com/tiles/satellite-v2/${parts[0]}/${parts[1]}/${parts[2]}.jpg?key=${BuildKonfig.MAPTILER_API_KEY}")
    }

    private fun writePoisToCache(pois: List<POI>, areaName: String, language: String) {
        val now = clock.nowMs()
        database.area_poi_cacheQueries.insertOrReplacePois(
            area_name = areaName,
            language = language,
            pois_json = json.encodeToString(pois),
            expires_at = now + CACHE_TTL_SEMI_STATIC_MS,
            created_at = now,
        )
    }

    private fun loadPoisFromCache(areaName: String, language: String, now: Long): List<POI> {
        val cached = database.area_poi_cacheQueries.getPois(areaName, language).executeAsOneOrNull()
        if (cached == null || cached.expires_at <= now) return emptyList()
        return try {
            json.decodeFromString(cached.pois_json)
        } catch (e: Exception) {
            AppLogger.e(e) { "Failed to parse cached POIs for $areaName, deleting corrupted entry" }
            database.area_poi_cacheQueries.deletePoisByArea(areaName)
            emptyList()
        }
    }

    private fun writeToCache(bucket: BucketContent, areaName: String, language: String) {
        val now = clock.nowMs()
        val expiresAt = now + getTtlMs(bucket.type)
        val sourcesJson = json.encodeToString(bucket.sources)
        database.area_bucket_cacheQueries.insertOrReplace(
            area_name = areaName,
            bucket_type = bucket.type.name,
            language = language,
            highlight = bucket.highlight,
            content = bucket.content,
            confidence = bucket.confidence.name,
            sources_json = sourcesJson,
            expires_at = expiresAt,
            created_at = now
        )
    }

    // Safe parsing — returns null for unrecognized values or corrupted JSON
    private fun Area_bucket_cache.toBucketContent(): BucketContent? {
        val type = BucketType.entries.firstOrNull { it.name == bucket_type } ?: run {
            AppLogger.d { "Skipping unknown bucket type in cache: $bucket_type" }
            return null
        }
        val conf = Confidence.entries.firstOrNull { it.name == confidence } ?: run {
            AppLogger.d { "Skipping unknown confidence in cache: $confidence" }
            return null
        }
        val parsedSources = try {
            json.decodeFromString<List<Source>>(sources_json)
        } catch (e: Exception) {
            AppLogger.e(e) { "Failed to parse sources_json for $bucket_type in $area_name" }
            emptyList()
        }
        return BucketContent(
            type = type,
            highlight = highlight,
            content = content,
            confidence = conf,
            sources = parsedSources
        )
    }
}
