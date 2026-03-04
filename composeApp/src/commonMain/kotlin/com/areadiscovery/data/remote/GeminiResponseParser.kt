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
    val name: String,
    val type: String,
    val description: String,
    val confidence: String = "MEDIUM",
    val latitude: Double? = null,
    val longitude: Double? = null
)

class GeminiResponseParser {

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
                    updates.add(BucketUpdate.ContentDelta(bucketContent.type, bucketContent.highlight + " " + bucketContent.content))
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
            BucketContent(
                type = parseBucketType(bucketJson.type),
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
                    name = poiJson.name,
                    type = poiJson.type,
                    description = poiJson.description,
                    confidence = parseConfidence(poiJson.confidence),
                    latitude = poiJson.latitude,
                    longitude = poiJson.longitude
                )
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse POIs JSON" }
            emptyList()
        }
    }

    private fun parseBucketType(type: String): BucketType {
        return when (type.uppercase()) {
            "SAFETY" -> BucketType.SAFETY
            "CHARACTER" -> BucketType.CHARACTER
            "WHATS_HAPPENING" -> BucketType.WHATS_HAPPENING
            "COST" -> BucketType.COST
            "HISTORY" -> BucketType.HISTORY
            "NEARBY" -> BucketType.NEARBY
            else -> BucketType.NEARBY
        }
    }

    private fun parseConfidence(confidence: String): Confidence {
        return when (confidence.uppercase()) {
            "HIGH" -> Confidence.HIGH
            "MEDIUM" -> Confidence.MEDIUM
            "LOW" -> Confidence.LOW
            else -> Confidence.MEDIUM
        }
    }
}
