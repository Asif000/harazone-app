package com.harazone.data.repository

import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.data.local.Area_bucket_cache
import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketContent
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.DynamicVibe
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

        // skipCache: bypass ALL cache paths (Surprise here!) — go straight to AI with enrichment
        if (!context.skipCache) {
            val cached = database.area_bucket_cacheQueries
                .getBucketsByAreaAndLanguage(areaName, language)
                .executeAsList()

            database.area_bucket_cacheQueries.deleteExpiredBuckets(now)
            database.area_poi_cacheQueries.deleteExpiredPois(now)

            val validParsed = cached.filter { it.expires_at > now }.mapNotNull { it.toBucketContent() }
            val staleParsed = cached.filter { it.expires_at <= now }.mapNotNull { it.toBucketContent() }

            if (validParsed.size == BucketType.entries.size) {
                AppLogger.d { "Cache HIT for '$areaName' — ${validParsed.size} buckets" }
                validParsed.forEach { emit(BucketUpdate.BucketComplete(it)) }
                val cachedPois = resolveTileRefs(loadPoisFromCache(areaName, language, now))
                val cachedVibes = loadVibesFromCache(areaName, language, now)
                if (cachedVibes.isNotEmpty()) {
                    emit(BucketUpdate.VibesReady(cachedVibes, cachedPois, fromCache = true))
                }
                emit(BucketUpdate.PortraitComplete(pois = cachedPois))
                return@flow
            }

            // POI-only cache hit
            if (validParsed.isEmpty() && staleParsed.isEmpty()) {
                val cachedPois = loadPoisFromCache(areaName, language, now)
                if (cachedPois.isNotEmpty()) {
                    AppLogger.d { "POI cache HIT for '$areaName' — ${cachedPois.size} POIs (no bucket cache)" }
                    val batches = cachedPois.chunked(3)
                    val batch0 = batches.first()
                    val cachedVibes = loadVibesFromCache(areaName, language, now)
                    if (cachedVibes.isNotEmpty()) {
                        emit(BucketUpdate.VibesReady(cachedVibes, batch0, fromCache = true))
                    }
                    emit(BucketUpdate.PortraitComplete(pois = resolveTileRefs(batch0)))
                    for (i in 1 until batches.size) {
                        emit(BucketUpdate.BackgroundBatchReady(batches[i], batchIndex = i))
                    }
                    if (batches.size > 1) {
                        emit(BucketUpdate.BackgroundFetchComplete)
                    }
                    val needsEnrichment = cachedPois.any { it.imageUrl == null }
                    if (needsEnrichment) {
                        scope.launch {
                            try {
                                val enriched = enrichPoisWithImages(cachedPois)
                                withContext(ioDispatcher) { writePoisToCache(enriched, areaName, language) }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                AppLogger.e(e) { "Background POI enrichment failed for '$areaName'" }
                            }
                        }
                    }
                    return@flow
                }
            }

            // Offline check
            val connectivity = connectivityObserver().first()
            if (connectivity is ConnectivityState.Offline) {
                val cachedVibes = loadVibesFromCache(areaName, language, now)
                val cachedPoisForVibes = loadPoisFromCache(areaName, language, now)
                if (cachedVibes.isNotEmpty() && cachedPoisForVibes.isNotEmpty()) {
                    emit(BucketUpdate.VibesReady(vibes = cachedVibes, pois = cachedPoisForVibes, fromCache = true))
                }
                val allCached = validParsed + staleParsed
                if (allCached.isNotEmpty()) {
                    allCached.forEach { emit(BucketUpdate.BucketComplete(it)) }
                    emit(BucketUpdate.ContentAvailabilityNote("You're offline — showing last known content"))
                } else if (cachedVibes.isEmpty()) {
                    emit(BucketUpdate.ContentAvailabilityNote("No content available offline for this area"))
                }
                emit(BucketUpdate.PortraitComplete(pois = resolveTileRefs(cachedPoisForVibes)))
                return@flow
            }

            // Stale-while-revalidate
            if (staleParsed.isNotEmpty()) {
                staleParsed.forEach { emit(BucketUpdate.BucketComplete(it)) }
                validParsed.forEach { emit(BucketUpdate.BucketComplete(it)) }
                val staleVibes = loadVibesFromCache(areaName, language, now)
                val stalePois = resolveTileRefs(loadPoisFromCache(areaName, language, now))
                if (staleVibes.isNotEmpty()) {
                    emit(BucketUpdate.VibesReady(staleVibes, stalePois, fromCache = true))
                }
                scope.launch {
                    aiProvider.streamAreaPortrait(areaName, context)
                        .catch { e -> AppLogger.e(e) { "Background refresh failed for area: $areaName" } }
                        .collect { update ->
                            if (update is BucketUpdate.PinsReady) return@collect
                            if (update is BucketUpdate.BucketComplete) {
                                withContext(ioDispatcher) { writeToCache(update.content, areaName, language) }
                            }
                            if (update is BucketUpdate.PortraitComplete && update.pois.isNotEmpty()) {
                                val hasCoords = update.pois.any { it.latitude != null && it.longitude != null }
                                if (hasCoords) {
                                    withContext(ioDispatcher) {
                                        val enriched = enrichPoisWithImages(update.pois)
                                        writePoisToCache(enriched, areaName, language)
                                    }
                                }
                            }
                        }
                }
                emit(BucketUpdate.PortraitComplete(pois = stalePois))
                return@flow
            }
        } else {
            AppLogger.d { "Cache SKIP for '$areaName' (skipCache=true)" }
        }

        // Cache miss (or skipCache) — stream from AI with image enrichment + caching
        AppLogger.d { "Fetching fresh portrait for '$areaName'" }
        try {
            aiProvider.streamAreaPortrait(areaName, context).collect { update ->
                when (update) {
                    is BucketUpdate.VibesReady -> {
                        // Apply client-side quality gate: drop vibes with no matching POIs
                        val minCount = if (update.pois.size <= 3) 1 else 2
                        val qualityVibes = update.vibes.filter { dv ->
                            update.pois.count { it.vibe == dv.label } >= minCount
                        }
                        emit(BucketUpdate.VibesReady(vibes = qualityVibes, pois = update.pois, fromCache = false))
                        if (update.pois.isNotEmpty()) writePoisToCache(update.pois, areaName, language)
                        if (qualityVibes.isNotEmpty()) writeVibesToCache(qualityVibes, areaName, language)
                    }
                    is BucketUpdate.PinsReady -> {
                        emit(update)
                        // Cache Stage 1 POIs (with coords) so restarts don't re-query
                        if (update.pois.isNotEmpty()) writePoisToCache(update.pois, areaName, language)
                    }
                    is BucketUpdate.PortraitComplete -> {
                        val hasCoords = update.pois.any { it.latitude != null && it.longitude != null }
                        if (hasCoords) {
                            // Full portrait POIs (Stage 1 fallback) — enrich images + cache as normal
                            val enriched = if (update.pois.isNotEmpty()) enrichPoisWithImages(update.pois) else update.pois
                            if (enriched.isNotEmpty()) writePoisToCache(enriched, areaName, language)
                            emit(BucketUpdate.PortraitComplete(resolveTileRefs(enriched)))
                        } else {
                            // Stage 2 enrichment-only POIs (no coords) — merge onto cached Stage 1 POIs
                            val enriched = if (update.pois.isNotEmpty()) enrichPoisWithImages(update.pois) else update.pois
                            val cachedStage1 = loadPoisFromCache(areaName, language, clock.nowMs())
                            if (cachedStage1.isNotEmpty() && enriched.isNotEmpty()) {
                                val merged = mergeStage2OntoCached(cachedStage1, enriched)
                                writePoisToCache(merged, areaName, language)
                                emit(BucketUpdate.PortraitComplete(resolveTileRefs(merged)))
                            } else {
                                emit(BucketUpdate.PortraitComplete(enriched))
                            }
                        }
                    }
                    is BucketUpdate.DynamicVibeComplete -> {
                        emit(update)
                    }
                    is BucketUpdate.BackgroundEnrichmentComplete -> {
                        val enriched = if (update.pois.isNotEmpty()) enrichPoisWithImages(update.pois) else update.pois
                        if (enriched.isNotEmpty()) mergePoisIntoCache(enriched, areaName, language)
                        emit(BucketUpdate.BackgroundEnrichmentComplete(enriched, update.batchIndex))
                    }
                    else -> {
                        emit(update)
                        if (update is BucketUpdate.BucketComplete) writeToCache(update.content, areaName, language)
                        // Cache background batch POIs so pagination survives relaunch
                        if (update is BucketUpdate.BackgroundBatchReady && update.pois.isNotEmpty()) {
                            mergePoisIntoCache(update.pois, areaName, language)
                        }
                    }
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
            val fallbackPois = resolveTileRefs(loadPoisFromCache(areaName, language, now))
            val fallbackVibes = loadVibesFromCache(areaName, language, now)
            if (fallbackVibes.isNotEmpty()) {
                emit(BucketUpdate.VibesReady(fallbackVibes, fallbackPois, fromCache = true))
            }
            emit(BucketUpdate.PortraitComplete(pois = fallbackPois))
        }
    }.flowOn(ioDispatcher)

    private suspend fun enrichPoisWithImages(pois: List<POI>): List<POI> = coroutineScope {
        AppLogger.d { "enrichPoisWithImages: enriching ${pois.size} POIs" }
        val semaphore = Semaphore(WikipediaImageRepository.MAX_CONCURRENT_REQUESTS)
        val result = pois.map { poi ->
            async {
                semaphore.withPermit {
                    try {
                        val wikiUrl = wikipediaImageRepository.getImageUrl(poi.wikiSlug, poi.name)
                        val imageUrl = wikiUrl ?: buildSatelliteTileRef(poi.latitude, poi.longitude)
                        AppLogger.d { "enrichPoisWithImages: '${poi.name}' -> wiki=${wikiUrl != null}, mapTiler=${imageUrl != null && wikiUrl == null}, final=${imageUrl?.take(60)}" }
                        poi.copy(imageUrl = imageUrl)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        AppLogger.w(e) { "enrichPoisWithImages: skipping '${poi.name}' — ${e.message}" }
                        poi
                    }
                }
            }
        }.awaitAll()
        AppLogger.d { "enrichPoisWithImages: done — ${result.count { it.imageUrl != null }}/${result.size} have images" }
        result
    }

    private fun mergeStage2OntoCached(stage1: List<POI>, stage2: List<POI>): List<POI> {
        val enrichmentByName = stage2.groupBy { it.name.lowercase() }
        val stage1Names = stage1.map { it.name.lowercase() }.toSet()
        val merged = stage1.map { cached ->
            val candidates = enrichmentByName[cached.name.lowercase()]
            if (candidates.isNullOrEmpty()) return@map cached
            if (candidates.size > 1) {
                AppLogger.w { "mergeStage2OntoCached: ${candidates.size} Stage 2 POIs with name '${cached.name}' — using first" }
            }
            val enrichment = candidates.first()
            cached.copy(
                vibe = enrichment.vibe.ifEmpty { cached.vibe },
                vibes = enrichment.vibes.ifEmpty { cached.vibes },
                insight = enrichment.insight.ifEmpty { cached.insight },
                description = enrichment.description.ifEmpty { cached.description },
                imageUrl = enrichment.imageUrl ?: cached.imageUrl,
                wikiSlug = enrichment.wikiSlug ?: cached.wikiSlug,
                hours = enrichment.hours ?: cached.hours,
                rating = enrichment.rating ?: cached.rating,
            )
        }
        // Append Stage 2-only POIs that had no match in Stage 1
        val unmatched = stage2.filter { it.name.lowercase() !in stage1Names }
        return merged + unmatched
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

    private fun writeVibesToCache(vibes: List<DynamicVibe>, areaName: String, language: String) {
        val now = clock.nowMs()
        val vibesJson = json.encodeToString(vibes)
        database.area_vibe_cacheQueries.insertOrReplaceVibes(
            area_name = areaName,
            language = language,
            vibes_json = vibesJson,
            expires_at = now + 3 * MS_PER_HOUR,
            created_at = now,
        )
    }

    private fun loadVibesFromCache(areaName: String, language: String, now: Long): List<DynamicVibe> {
        val cached = database.area_vibe_cacheQueries.getVibes(areaName, language).executeAsOneOrNull()
            ?: return emptyList()
        return try {
            json.decodeFromString(cached.vibes_json)
        } catch (e: Exception) {
            AppLogger.e(e) { "Failed to parse cached vibes for $areaName" }
            emptyList()
        }
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

    /**
     * Merge new POIs into the existing POI cache — deduplicates by name,
     * updates existing entries with enriched fields, appends truly new ones.
     */
    private fun mergePoisIntoCache(newPois: List<POI>, areaName: String, language: String) {
        val now = clock.nowMs()
        val existing = loadPoisFromCache(areaName, language, now)
        if (existing.isEmpty()) {
            writePoisToCache(newPois, areaName, language)
            return
        }
        val newByName = newPois.associateBy { it.name.trim().lowercase() }
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<POI>()
        for (poi in existing) {
            val key = poi.name.trim().lowercase()
            seen.add(key)
            val update = newByName[key]
            if (update != null) {
                merged.add(poi.copy(
                    vibe = update.vibe.ifEmpty { poi.vibe },
                    vibes = update.vibes.ifEmpty { poi.vibes },
                    insight = update.insight.ifEmpty { poi.insight },
                    description = update.description.ifEmpty { poi.description },
                    imageUrl = update.imageUrl ?: poi.imageUrl,
                    wikiSlug = update.wikiSlug ?: poi.wikiSlug,
                    hours = update.hours ?: poi.hours,
                    rating = update.rating ?: poi.rating,
                    latitude = update.latitude ?: poi.latitude,
                    longitude = update.longitude ?: poi.longitude,
                ))
            } else {
                merged.add(poi)
            }
        }
        for (poi in newPois) {
            if (poi.name.trim().lowercase() !in seen) {
                merged.add(poi)
            }
        }
        writePoisToCache(merged, areaName, language)
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
