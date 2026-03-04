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

        // M1: Clean up expired cache entries after reading current area's data
        database.area_bucket_cacheQueries.deleteExpiredBuckets(now)

        val validBuckets = cached.filter { it.expires_at > now }
        val staleBuckets = cached.filter { it.expires_at <= now }

        if (validBuckets.size == BucketType.entries.size) {
            // Full cache hit — emit all from cache
            validBuckets.forEach { row ->
                row.toBucketContent()?.let { emit(BucketUpdate.BucketComplete(it)) }
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        if (staleBuckets.isNotEmpty()) {
            // Stale-while-revalidate — emit only stale buckets immediately (M2)
            staleBuckets.forEach { row ->
                row.toBucketContent()?.let { emit(BucketUpdate.BucketComplete(it)) }
            }
            // Also emit any still-valid buckets
            validBuckets.forEach { row ->
                row.toBucketContent()?.let { emit(BucketUpdate.BucketComplete(it)) }
            }
            // H1: Background refresh with exception handling
            scope.launch {
                aiProvider.streamAreaPortrait(areaName, context)
                    .catch { e -> AppLogger.e(e) { "Background refresh failed for area: $areaName" } }
                    .collect { update ->
                        if (update is BucketUpdate.BucketComplete) {
                            writeToCache(update.content, areaName, language)
                        }
                    }
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        // Cache miss — stream from AI, write each bucket as it completes
        aiProvider.streamAreaPortrait(areaName, context).collect { update ->
            emit(update)
            if (update is BucketUpdate.BucketComplete) {
                writeToCache(update.content, areaName, language)
            }
        }
    }.flowOn(Dispatchers.IO) // H2: DB reads/writes off Main thread

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

    // H3: Safe enum parsing — returns null for unrecognized values
    private fun Area_bucket_cache.toBucketContent(): BucketContent? {
        val type = BucketType.entries.firstOrNull { it.name == bucket_type } ?: run {
            AppLogger.d { "Skipping unknown bucket type in cache: $bucket_type" }
            return null
        }
        val conf = Confidence.entries.firstOrNull { it.name == confidence } ?: run {
            AppLogger.d { "Skipping unknown confidence in cache: $confidence" }
            return null
        }
        return BucketContent(
            type = type,
            highlight = highlight,
            content = content,
            confidence = conf,
            sources = json.decodeFromString(sources_json)
        )
    }
}
