package com.areadiscovery.data.repository

import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.data.local.Area_bucket_cache
import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.Source
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.domain.repository.AreaRepository
import com.areadiscovery.util.AppClock
import com.areadiscovery.util.AppLogger
import com.areadiscovery.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AreaRepositoryImpl(
    private val aiProvider: AreaIntelligenceProvider,
    private val database: AreaDiscoveryDatabase,
    private val scope: CoroutineScope,
    private val clock: AppClock = SystemClock()
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

        val validBuckets = cached.filter { it.expires_at > now }
        val staleBuckets = cached.filter { it.expires_at <= now }

        // M2: Count only rows that map to known types for cache hit check
        val knownValidCount = validBuckets.count { row ->
            BucketType.entries.any { it.name == row.bucket_type }
        }

        if (knownValidCount == BucketType.entries.size) {
            // Full cache hit — emit all from cache
            validBuckets.forEach { row ->
                row.toBucketContent()?.let { emit(BucketUpdate.BucketComplete(it)) }
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        if (staleBuckets.isNotEmpty()) {
            // Stale-while-revalidate: emit cached content immediately, refresh in background.
            // Errors in the background refresh are silently logged (L3: intentional —
            // the caller already has stale content to display; propagating a refresh
            // failure would disrupt the UI for no user benefit).
            staleBuckets.forEach { row ->
                row.toBucketContent()?.let { emit(BucketUpdate.BucketComplete(it)) }
            }
            validBuckets.forEach { row ->
                row.toBucketContent()?.let { emit(BucketUpdate.BucketComplete(it)) }
            }
            // M3: Use withContext(IO) for DB writes in background refresh
            scope.launch {
                aiProvider.streamAreaPortrait(areaName, context)
                    .catch { e -> AppLogger.e(e) { "Background refresh failed for area: $areaName" } }
                    .collect { update ->
                        if (update is BucketUpdate.BucketComplete) {
                            withContext(Dispatchers.IO) {
                                writeToCache(update.content, areaName, language)
                            }
                        }
                    }
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        // Cache miss — stream from AI, write each bucket as it completes.
        // Unlike stale-revalidate, errors here propagate to the caller (L3: intentional —
        // there is no cached content to fall back on).
        aiProvider.streamAreaPortrait(areaName, context).collect { update ->
            emit(update)
            if (update is BucketUpdate.BucketComplete) {
                writeToCache(update.content, areaName, language)
            }
        }
    }.flowOn(Dispatchers.IO) // DB reads/writes off Main thread

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
        // M1: Protect against corrupted sources_json
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
