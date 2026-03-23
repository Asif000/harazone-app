package com.harazone.data.remote

import com.harazone.domain.model.BucketContent
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.DomainError
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.DynamicVibeContent
import com.harazone.domain.model.POI
import com.harazone.domain.model.Source
import com.harazone.util.AppLogger
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
    val confidence: String = "MEDIUM",
    val sources: List<SourceJson> = emptyList()
)

@Serializable
internal data class SourceJson(
    val title: String,
    val url: String? = null
)

@Serializable
internal data class PoiJson(
    val n: String = "",
    val t: String = "",
    val v: String = "",
    val w: String = "",
    val h: String? = null,
    val s: String? = null,
    val r: Float? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val wiki: String? = null,
    val vs: List<String>? = null,
    val p: String? = null,
)

@Serializable
internal data class EnrichJson(
    val n: String = "",
    val v: String = "",
    val w: String = "",
    val h: String? = null,
    val s: String? = null,
    val r: Float? = null,
    val p: String? = null,
)

@Serializable
internal data class VibeJson(val label: String = "", val icon: String = "")

@Serializable
internal data class Stage1Response(
    val vibes: List<VibeJson> = emptyList(),
    val pois: List<PoiJson> = emptyList(),
    val ah: List<String> = emptyList(),
    val cc: String? = null,
    val lg: String? = null,
)

@Serializable
internal data class EnrichmentResponseJson(
    val pois: List<EnrichJson> = emptyList(),
    val ah: List<String> = emptyList(),
)

@Serializable
internal data class PortraitPoisJson(
    val pois: List<PoiJson> = emptyList(),
    val ah: List<String> = emptyList(),
    val cc: String? = null,
    val lg: String? = null,
)

@Serializable
internal data class DynamicVibeJson(
    val label: String = "",
    val icon: String = "",
    val highlight: String = "",
    val content: String = "",
    val poi_ids: List<String> = emptyList(),
)

internal fun stripMarkdownFences(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("```")) trimmed.lines().drop(1).dropLast(1).joinToString("\n") else trimmed
}

internal data class Stage1ParseResult(
    val vibes: List<DynamicVibe>,
    val pois: List<POI>,
    val areaHighlights: List<String> = emptyList(),
    val currencyText: String? = null,
    val languageText: String? = null,
)

internal data class EnrichmentParseResult(
    val enrichments: List<EnrichJson>,
    val areaHighlights: List<String> = emptyList(),
)

@Serializable
internal data class PoiContextJson(
    val contextBlurb: String = "",
    val whyNow: String = "",
    val localTip: String = "",
)

@Serializable
internal data class ProfileIdentityResponseJson(
    val explorerName: String = "",
    val tagline: String = "",
    val avatarEmoji: String = "",
    val totalVisits: Int = 0,
    val totalAreas: Int = 0,
    val totalVibes: Int = 0,
    val geoFootprint: List<GeoFootprintJson> = emptyList(),
    val vibeInsights: List<VibeInsightResponseJson> = emptyList(),
)

@Serializable
internal data class GeoFootprintJson(
    val areaName: String = "",
    val countryCode: String = "",
)

@Serializable
internal data class VibeInsightResponseJson(
    val vibeName: String = "",
    val insight: String = "",
)

internal class GeminiResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val BUCKET_DELIMITER = "---BUCKET---"
        private const val POIS_DELIMITER = "---POIS---"
        private const val VIBE_DELIMITER = "---VIBE---"
    }

    fun parseStage1Response(text: String): Pair<List<DynamicVibe>, List<POI>> {
        val result = parseStage1WithHighlights(text)
        return Pair(result.vibes, result.pois)
    }

    /** Fallback parser for background batch responses — extracts JSON from text and parses pois. */
    fun parseBackgroundBatchPois(text: String): List<POI> {
        return try {
            // Extract first JSON object from text (handles Gemini prefixing prose before JSON)
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()
            val jsonText = text.substring(jsonStart, jsonEnd + 1)
            val wrapper = json.decodeFromString<Stage1Response>(jsonText)
            wrapper.pois
                .filter { it.n.isNotBlank() && it.lat != null && it.lng != null }
                .map { poiJson ->
                    POI(
                        name = poiJson.n,
                        type = poiJson.t,
                        description = "",
                        confidence = Confidence.MEDIUM,
                        latitude = poiJson.lat,
                        longitude = poiJson.lng,
                        vibe = poiJson.v,
                        insight = "",
                        priceRange = poiJson.p,
                        hours = poiJson.h,
                        liveStatus = poiJson.s,
                    )
                }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: background batch fallback parse failed" }
            emptyList()
        }
    }

    fun parseStage1WithHighlights(text: String): Stage1ParseResult {
        return try {
            val cleaned = stripMarkdownFences(text)
            try {
                val stage1 = json.decodeFromString<Stage1Response>(cleaned)
                val vibes = stage1.vibes
                    .filter { it.label.isNotBlank() }
                    .map { DynamicVibe(label = it.label, icon = it.icon) }
                val pois = stage1.pois
                    .filter { it.n.isNotBlank() && it.lat != null && it.lng != null }
                    .map { poiJson ->
                        POI(
                            name = poiJson.n,
                            type = poiJson.t,
                            description = "",
                            confidence = Confidence.MEDIUM,
                            latitude = poiJson.lat,
                            longitude = poiJson.lng,
                            vibe = poiJson.v,
                            insight = "",
                            priceRange = poiJson.p,
                            hours = poiJson.h,
                            liveStatus = poiJson.s,
                        )
                    }
                Stage1ParseResult(vibes, pois, stage1.ah, currencyText = stage1.cc, languageText = stage1.lg)
            } catch (_: Exception) {
                // Fallback: old flat array format
                val poisJson = json.decodeFromString<List<PoiJson>>(cleaned)
                val pois = poisJson
                    .filter { it.n.isNotBlank() && it.lat != null && it.lng != null }
                    .map { poiJson ->
                        POI(
                            name = poiJson.n,
                            type = poiJson.t,
                            description = "",
                            confidence = Confidence.MEDIUM,
                            latitude = poiJson.lat,
                            longitude = poiJson.lng,
                            vibe = poiJson.v,
                            insight = "",
                            priceRange = poiJson.p,
                            hours = poiJson.h,
                            liveStatus = poiJson.s,
                        )
                    }
                Stage1ParseResult(emptyList(), pois)
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse Stage 1 response" }
            Stage1ParseResult(emptyList(), emptyList())
        }
    }

    fun parseDynamicVibeResponse(text: String): Pair<List<DynamicVibeContent>, List<POI>> {
        return try {
            val poisSplit = text.split(POIS_DELIMITER)
            val vibesSection = poisSplit[0]
            val poisSection = poisSplit.getOrNull(1)?.trim()

            val vibeStrings = vibesSection.split(VIBE_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val vibeContents = vibeStrings.mapNotNull { vibeStr ->
                try {
                    val vibeJson = json.decodeFromString<DynamicVibeJson>(vibeStr)
                    DynamicVibeContent(
                        label = vibeJson.label,
                        icon = vibeJson.icon,
                        highlight = vibeJson.highlight,
                        content = vibeJson.content,
                        poiIds = vibeJson.poi_ids,
                    )
                } catch (e: Exception) {
                    AppLogger.e(e) { "GeminiResponseParser: failed to parse dynamic vibe JSON: ${vibeStr.take(100)}" }
                    null
                }
            }

            val pois = if (!poisSection.isNullOrBlank()) {
                try {
                    val poisJson = json.decodeFromString<List<PoiJson>>(poisSection)
                    poisJson.filter { it.n.isNotBlank() }.map { poiJson ->
                        POI(
                            name = poiJson.n,
                            type = poiJson.t,
                            description = "",
                            confidence = Confidence.MEDIUM,
                            latitude = poiJson.lat,
                            longitude = poiJson.lng,
                            vibe = poiJson.v,
                            insight = poiJson.w,
                            hours = poiJson.h,
                            liveStatus = poiJson.s,
                            rating = poiJson.r,
                            vibeInsights = emptyMap(),
                            wikiSlug = poiJson.wiki,
                            vibes = poiJson.vs ?: emptyList(),
                            priceRange = poiJson.p,
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(e) { "GeminiResponseParser: failed to parse dynamic vibe POIs" }
                    emptyList()
                }
            } else {
                emptyList()
            }

            Pair(vibeContents, pois)
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse dynamic vibe response" }
            Pair(emptyList(), emptyList())
        }
    }

    internal fun parseEnrichmentResponse(text: String): List<EnrichJson> {
        return parseEnrichmentWithHighlights(text).enrichments
    }

    internal fun parseEnrichmentWithHighlights(text: String): EnrichmentParseResult {
        return try {
            val cleaned = stripMarkdownFences(text)
            try {
                // New format: {"pois":[...],"ah":[...]}
                val wrapper = json.decodeFromString<EnrichmentResponseJson>(cleaned)
                EnrichmentParseResult(
                    enrichments = wrapper.pois.filter { it.n.isNotBlank() },
                    areaHighlights = wrapper.ah,
                )
            } catch (_: Exception) {
                // Backward-compatible fallback: old flat array format
                val enrichments = json.decodeFromString<List<EnrichJson>>(cleaned)
                    .filter { it.n.isNotBlank() }
                EnrichmentParseResult(enrichments = enrichments)
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse enrichment response" }
            EnrichmentParseResult(emptyList())
        }
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

            val poisResult = if (!poisSection.isNullOrBlank()) {
                parsePoisJsonWithHighlights(poisSection)
            } else {
                PoisWithHighlights(emptyList())
            }

            updates.add(BucketUpdate.PortraitComplete(poisResult.pois, areaHighlights = poisResult.areaHighlights, currencyText = poisResult.currencyText, languageText = poisResult.languageText))

            Result.success(updates)
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse response" }
            Result.failure(e)
        }
    }

    /**
     * Extracts text from a non-streaming generateContent response JSON.
     */
    fun extractTextFromGenerateContent(responseJson: String): String {
        return try {
            val chunk = json.decodeFromString<GeminiSseChunk>(responseJson)
            chunk.candidates.firstOrNull()?.content?.parts
                ?.joinToString("") { it.text ?: "" } ?: ""
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse generateContent response" }
            ""
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

    fun parsePoiContextResponse(text: String): Triple<String, String, String>? {
        return try {
            val cleaned = stripMarkdownFences(text)
            val ctx = json.decodeFromString<PoiContextJson>(cleaned)
            Triple(ctx.contextBlurb, ctx.whyNow, ctx.localTip)
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse poi context response" }
            null
        }
    }

    fun parseProfileIdentityResponse(text: String, languageTag: String = "en"): com.harazone.domain.model.ProfileIdentity? {
        return try {
            val cleaned = stripMarkdownFences(text)
            val parsed = json.decodeFromString<ProfileIdentityResponseJson>(cleaned)
            if (parsed.explorerName.isBlank()) return null
            if (!languageTag.startsWith("en")) {
                AppLogger.w { "GeminiResponseParser: profile identity for non-English locale '$languageTag' — verify language quality" }
            }
            com.harazone.domain.model.ProfileIdentity(
                explorerName = parsed.explorerName,
                tagline = parsed.tagline,
                avatarEmoji = parsed.avatarEmoji,
                totalVisits = parsed.totalVisits,
                totalAreas = parsed.totalAreas,
                totalVibes = parsed.totalVibes,
                geoFootprint = parsed.geoFootprint
                    .filter { it.areaName.isNotBlank() }
                    .map { com.harazone.domain.model.GeoArea(areaName = it.areaName, countryCode = it.countryCode) },
                vibeInsights = parsed.vibeInsights
                    .filter { it.vibeName.isNotBlank() }
                    .map { com.harazone.domain.model.VibeInsight(vibeName = it.vibeName, insight = it.insight) },
            )
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse profile identity response: ${text.take(100)}" }
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

    private data class PoisWithHighlights(val pois: List<POI>, val areaHighlights: List<String> = emptyList(), val currencyText: String? = null, val languageText: String? = null)

    private fun parsePoisJson(jsonString: String): List<POI> = parsePoisJsonWithHighlights(jsonString).pois

    private fun parsePoisJsonWithHighlights(jsonString: String): PoisWithHighlights {
        return try {
            // Try new wrapper format: {"pois":[...],"ah":[...]}
            try {
                val wrapper = json.decodeFromString<PortraitPoisJson>(jsonString)
                PoisWithHighlights(
                    pois = wrapper.pois.filter { it.n.isNotBlank() }.map { it.toPoi() },
                    areaHighlights = wrapper.ah,
                    currencyText = wrapper.cc,
                    languageText = wrapper.lg,
                )
            } catch (_: Exception) {
                // Backward-compatible fallback: old flat array format
                val poisJson = json.decodeFromString<List<PoiJson>>(jsonString)
                PoisWithHighlights(pois = poisJson.filter { it.n.isNotBlank() }.map { it.toPoi() })
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "GeminiResponseParser: failed to parse POIs JSON" }
            PoisWithHighlights(emptyList())
        }
    }

    private fun PoiJson.toPoi(): POI = POI(
        name = n,
        type = t,
        description = "",
        confidence = Confidence.MEDIUM,
        latitude = lat,
        longitude = lng,
        vibe = v,
        insight = w,
        hours = h,
        liveStatus = s,
        rating = r,
        vibeInsights = emptyMap(),
        wikiSlug = wiki,
        priceRange = p,
    )

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
            val poisResult = if (poisStr.isNotEmpty()) parsePoisJsonWithHighlights(poisStr) else PoisWithHighlights(emptyList())
            results.add(BucketUpdate.PortraitComplete(poisResult.pois, areaHighlights = poisResult.areaHighlights, currencyText = poisResult.currencyText, languageText = poisResult.languageText))

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
