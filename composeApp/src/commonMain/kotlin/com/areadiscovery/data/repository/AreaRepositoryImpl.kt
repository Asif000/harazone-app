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
import com.areadiscovery.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    }

    private val json = Json { ignoreUnknownKeys = true }

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

        val validBuckets = cached.filter { it.expires_at > now }
        val staleBuckets = cached.filter { it.expires_at <= now }

        if (validBuckets.size == BucketType.entries.size) {
            // Full cache hit — emit all from cache
            validBuckets.forEach { row ->
                emit(BucketUpdate.BucketComplete(row.toBucketContent()))
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        if (cached.isNotEmpty() && staleBuckets.isNotEmpty()) {
            // Stale-while-revalidate — emit stale immediately
            cached.forEach { row ->
                emit(BucketUpdate.BucketComplete(row.toBucketContent()))
            }
            // Trigger background refresh — do NOT await
            scope.launch {
                aiProvider.streamAreaPortrait(areaName, context).collect { update ->
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

    private fun Area_bucket_cache.toBucketContent(): BucketContent = BucketContent(
        type = BucketType.valueOf(bucket_type),
        highlight = highlight,
        content = content,
        confidence = Confidence.valueOf(confidence),
        sources = json.decodeFromString(sources_json)
    )
}
