package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiResponseParserTest {

    private val parser = GeminiResponseParser()

    companion object {
        const val COMPLETE_RESPONSE = """{"type":"SAFETY","highlight":"Alfama is generally safe.","content":"Low crime rates with good street lighting. Stay aware of pickpockets in tourist areas.","confidence":"HIGH","sources":[{"title":"Lisbon Safety Report","url":"https://example.com/safety"}]}
---BUCKET---
{"type":"CHARACTER","highlight":"Historic and charming neighborhood.","content":"Narrow cobblestone streets, colorful tiles, and fado music. A mix of locals and tourists.","confidence":"HIGH","sources":[{"title":"Visit Lisbon","url":"https://example.com/visit"}]}
---BUCKET---
{"type":"WHATS_HAPPENING","highlight":"Fado festival this weekend.","content":"Live music in local restaurants. Street markets on Tuesdays and Saturdays.","confidence":"MEDIUM","sources":[{"title":"Lisbon Events","url":"https://example.com/events"}]}
---BUCKET---
{"type":"COST","highlight":"Moderate for Lisbon standards.","content":"Meals range 8-15 EUR. Coffee around 1.50 EUR. Accommodation is pricier than other areas.","confidence":"HIGH","sources":[{"title":"Cost Guide","url":"https://example.com/cost"}]}
---BUCKET---
{"type":"HISTORY","highlight":"Oldest district in Lisbon.","content":"Survived the 1755 earthquake. Moorish origins visible in the street layout. Castle of São Jorge overlooks the area.","confidence":"HIGH","sources":[{"title":"History of Alfama","url":"https://example.com/history"}]}
---BUCKET---
{"type":"NEARBY","highlight":"Castle and waterfront nearby.","content":"São Jorge Castle is a 5-minute walk. Flea market at Feira da Ladra. Riverfront promenade accessible on foot.","confidence":"HIGH","sources":[{"title":"Alfama Guide","url":"https://example.com/guide"}]}
---POIS---
[{"name":"Castle of São Jorge","type":"landmark","description":"Medieval castle with panoramic views","confidence":"HIGH","latitude":38.7139,"longitude":-9.1335},{"name":"Feira da Ladra","type":"market","description":"Lisbon's famous flea market","confidence":"HIGH","latitude":38.7155,"longitude":-9.1285}]"""

        const val PARTIAL_RESPONSE = """{"type":"SAFETY","highlight":"Area is safe.","content":"Low crime area with good infrastructure.","confidence":"HIGH","sources":[]}
---BUCKET---
{"type":"CHARACTER","highlight":"Vibrant culture.","content":"Known for arts and food scene.","confidence":"MEDIUM","sources":[]}
---POIS---
[]"""

        const val MALFORMED_SSE = """{"type":"SAFETY","highlight":"Safe area","content":"Details here","confidence":"HIGH","sources":[]}
---BUCKET---
{this is not valid json at all!!!
---BUCKET---
{"type":"HISTORY","highlight":"Rich history","content":"Founded centuries ago.","confidence":"MEDIUM","sources":[]}
---POIS---
not valid json either"""

        const val EMPTY_BUCKETS = """---POIS---
[]"""

        const val VALID_SSE_EVENT = """{"candidates":[{"content":{"parts":[{"text":"Hello world"}],"role":"model"}}]}"""
        const val MALFORMED_SSE_EVENT = """{"not_valid": true}"""

        const val V3_POIS_RESPONSE = """{"type":"SAFETY","highlight":"Safe area","content":"Low crime.","confidence":"HIGH","sources":[]}
---POIS---
[{"poi":"Time Out Market","type":"food","vibe":"character","insight":"Curated food hall with 24 restaurants","hours":"Sun-Wed 10am-12am","liveStatus":"open","confidence":"HIGH","rating":4.5,"latitude":38.71,"longitude":-9.13,"vibeInsights":{"character":"A gathering hub for locals","history":"Converted 1892 iron market hall"}},{"name":"Old Church","type":"historic","description":"A very old church","vibe":"history","insight":"Built in 1200","confidence":"MEDIUM","latitude":38.72,"longitude":-9.14}]"""
    }

    @Test
    fun parseFullResponse_completeResponse_returns6BucketsAndPois() {
        val result = parser.parseFullResponse(COMPLETE_RESPONSE)

        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()

        // Word-by-word ContentDelta emissions + 6 BucketComplete + 1 PortraitComplete
        // First emission is a word-level ContentDelta for SAFETY
        val firstDelta = updates[0] as BucketUpdate.ContentDelta
        assertEquals(BucketType.SAFETY, firstDelta.bucketType)

        // Count BucketComplete emissions — should be exactly 6
        val bucketCompletes = updates.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(6, bucketCompletes.size)
        assertEquals(BucketType.SAFETY, bucketCompletes[0].content.type)
        assertEquals("Alfama is generally safe.", bucketCompletes[0].content.highlight)
        assertEquals(Confidence.HIGH, bucketCompletes[0].content.confidence)
        assertEquals(1, bucketCompletes[0].content.sources.size)

        // Multiple ContentDelta per bucket (word-by-word streaming)
        val safetyDeltas = updates.filterIsInstance<BucketUpdate.ContentDelta>()
            .filter { it.bucketType == BucketType.SAFETY }
        assertTrue(safetyDeltas.size > 1, "Should emit multiple ContentDelta per bucket")

        // Check last emission is PortraitComplete with POIs
        val portrait = updates.last() as BucketUpdate.PortraitComplete
        assertEquals(2, portrait.pois.size)
        assertEquals("Castle of São Jorge", portrait.pois[0].name)
        assertEquals(38.7139, portrait.pois[0].latitude)
    }

    @Test
    fun parseFullResponse_partialResponse_returns2BucketsAndEmptyPois() {
        val result = parser.parseFullResponse(PARTIAL_RESPONSE)

        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()

        val bucketCompletes = updates.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(2, bucketCompletes.size)

        val portrait = updates.last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois.isEmpty())
    }

    @Test
    fun parseFullResponse_malformedSse_skipsBadBucketsAndContinues() {
        val result = parser.parseFullResponse(MALFORMED_SSE)

        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()

        // 2 valid buckets (malformed bucket skipped)
        val bucketCompletes = updates.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(2, bucketCompletes.size)
        assertEquals(BucketType.SAFETY, bucketCompletes[0].content.type)
        assertEquals(BucketType.HISTORY, bucketCompletes[1].content.type)

        // POIs should be empty due to malformed JSON
        val portrait = updates.last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois.isEmpty())
    }

    @Test
    fun parseFullResponse_emptyBuckets_returnsOnlyPortraitComplete() {
        val result = parser.parseFullResponse(EMPTY_BUCKETS)

        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()

        // Only PortraitComplete with empty POIs
        assertEquals(1, updates.size)
        val portrait = updates[0] as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois.isEmpty())
    }

    @Test
    fun extractTextFromSseEvent_validEvent_returnsText() {
        val text = parser.extractTextFromSseEvent(VALID_SSE_EVENT)
        assertEquals("Hello world", text)
    }

    @Test
    fun extractTextFromSseEvent_malformedEvent_returnsNull() {
        val text = parser.extractTextFromSseEvent(MALFORMED_SSE_EVENT)
        assertNull(text)
    }

    // --- StreamingParser tests ---

    @Test
    fun streamingParser_emitsBucketIncrementallyOnDelimiter() {
        val streaming = parser.createStreamingParser()

        // Feed first bucket JSON (no delimiter yet)
        val chunk1 = """{"type":"SAFETY","highlight":"Safe area","content":"Low crime area.","confidence":"HIGH","sources":[]}"""
        val updates1 = streaming.processChunk(chunk1)
        assertTrue(updates1.isEmpty(), "No emissions before delimiter")

        // Feed delimiter + start of next bucket
        val chunk2 = "\n---BUCKET---\n{\"type\":\"CHARACTER\""
        val updates2 = streaming.processChunk(chunk2)
        // Word-by-word deltas + BucketComplete for first bucket
        val bucket1Completes = updates2.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(1, bucket1Completes.size)
        assertEquals(BucketType.SAFETY, bucket1Completes[0].content.type)
        assertEquals("Safe area", bucket1Completes[0].content.highlight)
        // Multiple word-level ContentDelta emissions
        val bucket1Deltas = updates2.filterIsInstance<BucketUpdate.ContentDelta>()
        assertTrue(bucket1Deltas.isNotEmpty())
        assertTrue(bucket1Deltas.all { it.bucketType == BucketType.SAFETY })

        // Feed rest of second bucket + POIS delimiter + POIs
        val chunk3 = ""","highlight":"Vibrant","content":"Great culture.","confidence":"MEDIUM","sources":[]}
---POIS---
[{"name":"Park","type":"park","description":"Nice park","confidence":"HIGH"}]"""
        val updates3 = streaming.processChunk(chunk3)
        val bucket2Completes = updates3.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(1, bucket2Completes.size)
        assertEquals(BucketType.CHARACTER, bucket2Completes[0].content.type)

        // Finish
        val finalUpdates = streaming.finish()
        assertEquals(1, finalUpdates.size)
        val portrait = finalUpdates[0] as BucketUpdate.PortraitComplete
        assertEquals(1, portrait.pois.size)
        assertEquals("Park", portrait.pois[0].name)
    }

    @Test
    fun streamingParser_handlesCompleteResponseInOneChunk() {
        val streaming = parser.createStreamingParser()

        val allUpdates = mutableListOf<BucketUpdate>()
        allUpdates.addAll(streaming.processChunk(COMPLETE_RESPONSE))
        allUpdates.addAll(streaming.finish())

        // 6 BucketComplete + 1 PortraitComplete + many word-level ContentDeltas
        val bucketCompletes = allUpdates.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(6, bucketCompletes.size)
        assertEquals(BucketType.SAFETY, (allUpdates[0] as BucketUpdate.ContentDelta).bucketType)
        val portrait = allUpdates.last() as BucketUpdate.PortraitComplete
        assertEquals(2, portrait.pois.size)
    }

    @Test
    fun streamingParser_skipsMalformedBuckets() {
        val streaming = parser.createStreamingParser()

        val allUpdates = mutableListOf<BucketUpdate>()
        allUpdates.addAll(streaming.processChunk(MALFORMED_SSE))
        allUpdates.addAll(streaming.finish())

        // 2 valid buckets (malformed bucket skipped)
        val bucketCompletes = allUpdates.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(2, bucketCompletes.size)
        assertEquals(BucketType.SAFETY, bucketCompletes[0].content.type)
        assertEquals(BucketType.HISTORY, bucketCompletes[1].content.type)
        assertTrue((allUpdates.last() as BucketUpdate.PortraitComplete).pois.isEmpty())
    }

    @Test
    fun streamingParser_emptyBuckets_returnsOnlyPortraitComplete() {
        val streaming = parser.createStreamingParser()

        val allUpdates = mutableListOf<BucketUpdate>()
        allUpdates.addAll(streaming.processChunk(EMPTY_BUCKETS))
        allUpdates.addAll(streaming.finish())

        assertEquals(1, allUpdates.size)
        assertTrue((allUpdates[0] as BucketUpdate.PortraitComplete).pois.isEmpty())
    }

    @Test
    fun streamingParser_handlesDelimiterSplitAcrossChunks() {
        val streaming = parser.createStreamingParser()

        // Chunk 1: first bucket JSON + partial delimiter "---BUCK"
        val chunk1 = """{"type":"SAFETY","highlight":"Safe","content":"Low crime.","confidence":"HIGH","sources":[]}
---BUCK"""
        val updates1 = streaming.processChunk(chunk1)
        // Delimiter not yet complete, so no bucket emitted
        assertTrue(updates1.isEmpty(), "No emissions until delimiter is complete")

        // Chunk 2: rest of delimiter "ET---" + second bucket + POIS
        val chunk2 = """ET---
{"type":"CHARACTER","highlight":"Vibrant","content":"Great culture.","confidence":"MEDIUM","sources":[]}
---POIS---
[]"""
        val updates2 = streaming.processChunk(chunk2)
        // Both buckets emitted once the delimiter completes and POIS delimiter is found
        val bucketCompletes = updates2.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(2, bucketCompletes.size)
        assertEquals(BucketType.SAFETY, bucketCompletes[0].content.type)
        assertEquals(BucketType.CHARACTER, bucketCompletes[1].content.type)

        // Finish should emit PortraitComplete with empty POIs
        val finalUpdates = streaming.finish()
        assertEquals(1, finalUpdates.size)
        val portrait = finalUpdates[0] as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois.isEmpty())
    }

    @Test
    fun streamingParser_skipsUnknownBucketType() {
        val streaming = parser.createStreamingParser()

        val input = """{"type":"UNKNOWN_TYPE","highlight":"Mystery","content":"Something.","confidence":"HIGH","sources":[]}
---BUCKET---
{"type":"SAFETY","highlight":"Safe","content":"Low crime.","confidence":"HIGH","sources":[]}
---POIS---
[]"""
        val allUpdates = mutableListOf<BucketUpdate>()
        allUpdates.addAll(streaming.processChunk(input))
        allUpdates.addAll(streaming.finish())

        // Unknown type bucket should be skipped, only SAFETY emitted
        val bucketCompletes = allUpdates.filterIsInstance<BucketUpdate.BucketComplete>()
        assertEquals(1, bucketCompletes.size)
        assertEquals(BucketType.SAFETY, bucketCompletes[0].content.type)
    }

    // --- v3 POI field tests ---

    @Test
    fun parseFullResponse_v3Pois_parsesVibe() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()
        val portrait = updates.last() as BucketUpdate.PortraitComplete
        assertEquals(2, portrait.pois.size)
        assertEquals("character", portrait.pois[0].vibe)
        assertEquals("history", portrait.pois[1].vibe)
    }

    @Test
    fun parseFullResponse_v3Pois_parsesInsight() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois[0].insight.isNotEmpty())
        assertEquals("Curated food hall with 24 restaurants", portrait.pois[0].insight)
    }

    @Test
    fun parseFullResponse_v3Pois_parsesVibeInsights() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        val vibeInsights = portrait.pois[0].vibeInsights
        assertTrue(vibeInsights.isNotEmpty())
        assertEquals("A gathering hub for locals", vibeInsights["character"])
        assertEquals("Converted 1892 iron market hall", vibeInsights["history"])
    }

    @Test
    fun parseFullResponse_v3Pois_parsesLiveStatus() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertEquals("open", portrait.pois[0].liveStatus)
    }

    @Test
    fun parseFullResponse_v3Pois_fallsBackToNameFieldWhenPoiMissing() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        // First POI uses "poi" key -> "Time Out Market"
        assertEquals("Time Out Market", portrait.pois[0].name)
        // Second POI uses "name" key (no "poi") -> "Old Church"
        assertEquals("Old Church", portrait.pois[1].name)
    }

    @Test
    fun streamingParser_cannotBeReusedAfterFinish() {
        val streaming = parser.createStreamingParser()
        streaming.finish()

        assertFailsWith<IllegalStateException> {
            streaming.processChunk("text")
        }
        assertFailsWith<IllegalStateException> {
            streaming.finish()
        }
    }
}
