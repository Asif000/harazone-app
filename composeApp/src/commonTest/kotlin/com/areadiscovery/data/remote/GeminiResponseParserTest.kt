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
        const val COMPLETE_RESPONSE = """{"type":"SAFETY","highlight":"Alfama is generally safe.","content":"Low crime rates with good street lighting. Stay aware of pickpockets in tourist areas."}
---BUCKET---
{"type":"CHARACTER","highlight":"Historic and charming neighborhood.","content":"Narrow cobblestone streets, colorful tiles, and fado music. A mix of locals and tourists."}
---BUCKET---
{"type":"WHATS_HAPPENING","highlight":"Fado festival this weekend.","content":"Live music in local restaurants. Street markets on Tuesdays and Saturdays."}
---BUCKET---
{"type":"COST","highlight":"Moderate for Lisbon standards.","content":"Meals range 8-15 EUR. Coffee around 1.50 EUR. Accommodation is pricier than other areas."}
---BUCKET---
{"type":"HISTORY","highlight":"Oldest district in Lisbon.","content":"Survived the 1755 earthquake. Moorish origins visible in the street layout. Castle of São Jorge overlooks the area."}
---BUCKET---
{"type":"NEARBY","highlight":"Castle and waterfront nearby.","content":"São Jorge Castle is a 5-minute walk. Flea market at Feira da Ladra. Riverfront promenade accessible on foot."}
---POIS---
[{"n":"Castle of São Jorge","t":"historic","v":"history","w":"Medieval castle with panoramic views of Lisbon","h":"9a-9p","s":"open","r":4.7,"lat":38.7139,"lng":-9.1335},{"n":"Feira da Ladra","t":"shopping","v":"character","w":"Lisbon's famous flea market running since the 13th century","h":"Tue+Sat 9a-6p","s":"open","r":4.2,"lat":38.7155,"lng":-9.1285}]"""

        const val PARTIAL_RESPONSE = """{"type":"SAFETY","highlight":"Area is safe.","content":"Low crime area with good infrastructure."}
---BUCKET---
{"type":"CHARACTER","highlight":"Vibrant culture.","content":"Known for arts and food scene."}
---POIS---
[]"""

        const val MALFORMED_SSE = """{"type":"SAFETY","highlight":"Safe area","content":"Details here"}
---BUCKET---
{this is not valid json at all!!!
---BUCKET---
{"type":"HISTORY","highlight":"Rich history","content":"Founded centuries ago."}
---POIS---
not valid json either"""

        const val EMPTY_BUCKETS = """---POIS---
[]"""

        const val VALID_SSE_EVENT = """{"candidates":[{"content":{"parts":[{"text":"Hello world"}],"role":"model"}}]}"""
        const val MALFORMED_SSE_EVENT = """{"not_valid": true}"""

        const val V3_POIS_RESPONSE = """{"type":"SAFETY","highlight":"Safe area","content":"Low crime."}
---POIS---
[{"n":"Time Out Market","t":"food","v":"character","w":"Curated food hall with 24 local restaurants in a converted 1892 iron market hall","h":"Sun-Wed 10am-12am, Thu-Sat 10am-2am","s":"open","r":4.5,"lat":38.71,"lng":-9.13},{"n":"Old Church","t":"historic","v":"history","w":"Built in 1200, oldest surviving structure in the district","h":"10a-5p","s":"open","r":4.2,"lat":38.72,"lng":-9.14}]"""
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
        assertEquals(Confidence.MEDIUM, bucketCompletes[0].content.confidence)

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
        val chunk1 = """{"type":"SAFETY","highlight":"Safe area","content":"Low crime area."}"""
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
        val chunk3 = ""","highlight":"Vibrant","content":"Great culture."}
---POIS---
[{"n":"Park","t":"park","v":"nearby","w":"Peaceful community green space with century-old oak trees","lat":null,"lng":null}]"""
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
        val chunk1 = """{"type":"SAFETY","highlight":"Safe","content":"Low crime."}
---BUCK"""
        val updates1 = streaming.processChunk(chunk1)
        // Delimiter not yet complete, so no bucket emitted
        assertTrue(updates1.isEmpty(), "No emissions until delimiter is complete")

        // Chunk 2: rest of delimiter "ET---" + second bucket + POIS
        val chunk2 = """ET---
{"type":"CHARACTER","highlight":"Vibrant","content":"Great culture."}
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

        val input = """{"type":"UNKNOWN_TYPE","highlight":"Mystery","content":"Something."}
---BUCKET---
{"type":"SAFETY","highlight":"Safe","content":"Low crime."}
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
        assertEquals("Curated food hall with 24 local restaurants in a converted 1892 iron market hall", portrait.pois[0].insight)
    }

    @Test
    fun parseFullResponse_v3Pois_parsesVibeInsights() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois[0].vibeInsights.isEmpty())
    }

    @Test
    fun parseFullResponse_v3Pois_parsesLiveStatus() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertEquals("open", portrait.pois[0].liveStatus)
    }

    @Test
    fun parseFullResponse_slimPois_parsesNameFromNKey() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertEquals("Time Out Market", portrait.pois[0].name)
        assertEquals("Old Church", portrait.pois[1].name)
    }

    @Test
    fun parseFullResponse_slimPois_mapsWhySpecialToInsight() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois[0].insight.isNotEmpty())
        assertTrue(portrait.pois[0].insight.contains("food hall") || portrait.pois[0].insight.contains("market"))
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
