package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.DomainError
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Source
import com.areadiscovery.util.AppLogger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class GeminiSseChunk(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
internal data class GeminiContent(
    val parts: List<GeminiPart> = emptyList(),
    val role: String? = null
)

@Serializable
internal data class GeminiPart(
    val text: String? = null
)

@Serializable
internal data class BucketJson(
    val type: String,
    val highlight: String,
    val content: String,
    val confidence: String,
    val sources: List<SourceJson> = emptyList()
)

@Serializable
internal data class SourceJson(
    val title: String,
    val url: String? = null
)

@Serializable
internal data class PoiJson(
    @SerialName("poi") val poi: String = "",
    val name: String = "",
    val type: String,
    val description: String = "",
    val confidence: String = "MEDIUM",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val vibe: String = "",
    val insight: String = "",
    val hours: String? = null,
    val liveStatus: String? = null,
    val rating: Float? = null,
    val vibeInsights: Map<String, String> = emptyMap(),
)

internal class GeminiResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val BUCKET_DELIMITER = "---BUCKET---"
        private const val POIS_DELIMITER = "---POIS---"
    }

    /**
     * Parses accumulated SSE text into a list of BucketUpdate emissions.
     * Call this after the full SSE stream has been buffered, or incrementally
     * as chunks arrive.
     */
    fun parseFullResponse(accumulatedText: String): Result<List<BucketUpdate>> {
        return try {
            val updates = mutableListOf<BucketUpdate>()

            val poisSplit = accumulatedText.split(POIS_DELIMITER)
            val bucketsSection = poisSplit[0]
            val poisSection = poisSplit.getOrNull(1)?.trim()

            val bucketStrings = bucketsSection.split(BUCKET_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (bucketString in bucketStrings) {
                val bucketContent = parseBucketJson(bucketString)
                if (bucketContent != null) {
                    emitWordByWordDeltas(bucketContent, updates)
                    updates.add(BucketUpdate.BucketComplete(bucketContent))
                }
            }

            val pois = if (!poisSection.isNullOrBlank()) {
                parsePoisJson(poisSection)
            } else {
                emptyList()
            }

            updates.add(BucketUpdate.PortraitComplete(pois))

            Result.success(updates)
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse response" }
            Result.failure(e)
        }
    }

    /**
     * Extracts text content from a single SSE data event JSON.
     */
    fun extractTextFromSseEvent(eventData: String): String? {
        return try {
            val chunk = json.decodeFromString<GeminiSseChunk>(eventData)
            chunk.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse SSE event" }
            null
        }
    }

    private fun parseBucketJson(jsonString: String): BucketContent? {
        return try {
            val bucketJson = json.decodeFromString<BucketJson>(jsonString)
            val bucketType = parseBucketType(bucketJson.type) ?: return null
            BucketContent(
                type = bucketType,
                highlight = bucketJson.highlight,
                content = bucketJson.content,
                confidence = parseConfidence(bucketJson.confidence),
                sources = bucketJson.sources.map { Source(title = it.title, url = it.url) }
            )
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse bucket JSON: ${jsonString.take(100)}" }
            null
        }
    }

    private fun parsePoisJson(jsonString: String): List<POI> {
        return try {
            val poisJson = json.decodeFromString<List<PoiJson>>(jsonString)
            poisJson.map { poiJson ->
                POI(
                    name = poiJson.poi.ifBlank { poiJson.name },
                    type = poiJson.type,
                    description = poiJson.description,
                    confidence = parseConfidence(poiJson.confidence),
                    latitude = poiJson.latitude,
                    longitude = poiJson.longitude,
                    vibe = poiJson.vibe,
                    insight = poiJson.insight,
                    hours = poiJson.hours,
                    liveStatus = poiJson.liveStatus,
                    rating = poiJson.rating,
                    vibeInsights = poiJson.vibeInsights,
                )
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse POIs JSON" }
            emptyList()
        }
    }

    private fun parseBucketType(type: String): BucketType? {
        return when (type.uppercase()) {
            "SAFETY" -> BucketType.SAFETY
            "CHARACTER" -> BucketType.CHARACTER
            "WHATS_HAPPENING" -> BucketType.WHATS_HAPPENING
            "COST" -> BucketType.COST
            "HISTORY" -> BucketType.HISTORY
            "NEARBY" -> BucketType.NEARBY
            else -> {
                AppLogger.d { "GeminiResponseParser: unknown bucket type '$type', skipping bucket" }
                null
            }
        }
    }

    private fun parseConfidence(confidence: String): Confidence {
        return when (confidence.uppercase()) {
            "HIGH" -> Confidence.HIGH
            "MEDIUM" -> Confidence.MEDIUM
            "LOW" -> Confidence.LOW
            else -> {
                AppLogger.d { "GeminiResponseParser: unknown confidence '$confidence', defaulting to MEDIUM" }
                Confidence.MEDIUM
            }
        }
    }

    private fun emitWordByWordDeltas(
        bucketContent: BucketContent,
        results: MutableList<BucketUpdate>
    ) {
        val words = bucketContent.content.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (word in words) {
            results.add(BucketUpdate.ContentDelta(bucketContent.type, "$word "))
        }
    }

    fun createStreamingParser(): StreamingParser = StreamingParser()

    inner class StreamingParser {
        private val buffer = StringBuilder()
        private val currentBucketText = StringBuilder()
        private val poisText = StringBuilder()
        private var inPoisSection = false
        private var finished = false

        fun processChunk(text: String): List<BucketUpdate> {
            check(!finished) { "StreamingParser cannot be reused after finish()" }
            buffer.append(text)
            val results = mutableListOf<BucketUpdate>()
            drainBuffer(results)
            return results
        }

        fun finish(): List<BucketUpdate> {
            check(!finished) { "StreamingParser.finish() already called" }
            finished = true
            val results = mutableListOf<BucketUpdate>()

            // Flush remaining buffer
            val remaining = buffer.toString()
            if (remaining.isNotEmpty()) {
                if (inPoisSection) {
                    poisText.append(remaining)
                } else {
                    currentBucketText.append(remaining)
                }
                buffer.clear()
            }

            // Complete any remaining bucket
            if (!inPoisSection) {
                completeBucket(results)
            }

            // Parse POIs
            val poisStr = poisText.toString().trim()
            val pois = if (poisStr.isNotEmpty()) parsePoisJson(poisStr) else emptyList()
            results.add(BucketUpdate.PortraitComplete(pois))

            return results
        }

        private fun completeBucket(results: MutableList<BucketUpdate>) {
            val bucketJson = currentBucketText.toString().trim()
            if (bucketJson.isNotEmpty()) {
                val bucketContent = parseBucketJson(bucketJson)
                if (bucketContent != null) {
                    emitWordByWordDeltas(bucketContent, results)
                    results.add(BucketUpdate.BucketComplete(bucketContent))
                }
            }
            currentBucketText.clear()
        }

        private fun drainBuffer(results: MutableList<BucketUpdate>) {
            if (buffer.isEmpty()) return

            if (inPoisSection) {
                poisText.append(buffer)
                buffer.clear()
                return
            }

            // Single-pass: convert to string once, scan with position tracking
            val str = buffer.toString()
            var pos = 0

            while (pos < str.length) {
                // Find earliest delimiter from current position
                val poisIdx = str.indexOf(POIS_DELIMITER, pos)
                val bucketIdx = str.indexOf(BUCKET_DELIMITER, pos)

                val delimIdx: Int
                val delimLen: Int
                val isPois: Boolean

                when {
                    poisIdx >= 0 && (bucketIdx < 0 || poisIdx < bucketIdx) -> {
                        delimIdx = poisIdx; delimLen = POIS_DELIMITER.length; isPois = true
                    }
                    bucketIdx >= 0 -> {
                        delimIdx = bucketIdx; delimLen = BUCKET_DELIMITER.length; isPois = false
                    }
                    else -> break // No more delimiters
                }

                // Text before delimiter belongs to current bucket
                currentBucketText.append(str, pos, delimIdx)
                completeBucket(results)

                pos = delimIdx + delimLen

                if (isPois) {
                    inPoisSection = true
                    poisText.append(str, pos, str.length)
                    pos = str.length
                }
            }

            // Keep unprocessed remainder in buffer
            buffer.clear()
            if (pos < str.length) {
                buffer.append(str, pos, str.length)
            }
        }
    }
}
