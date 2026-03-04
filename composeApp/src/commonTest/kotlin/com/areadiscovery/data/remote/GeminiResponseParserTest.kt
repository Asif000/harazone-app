package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }

    @Test
    fun parseFullResponse_completeResponse_returns6BucketsAndPois() {
        val result = parser.parseFullResponse(COMPLETE_RESPONSE)

        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()

        // 6 buckets × 2 (ContentDelta + BucketComplete) + 1 PortraitComplete = 13
        assertEquals(13, updates.size)

        // Check first bucket
        val firstDelta = updates[0] as BucketUpdate.ContentDelta
        assertEquals(BucketType.SAFETY, firstDelta.bucketType)

        val firstComplete = updates[1] as BucketUpdate.BucketComplete
        assertEquals(BucketType.SAFETY, firstComplete.content.type)
        assertEquals("Alfama is generally safe.", firstComplete.content.highlight)
        assertEquals(Confidence.HIGH, firstComplete.content.confidence)
        assertEquals(1, firstComplete.content.sources.size)

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

        // 2 buckets × 2 + 1 PortraitComplete = 5
        assertEquals(5, updates.size)

        val portrait = updates.last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois.isEmpty())
    }

    @Test
    fun parseFullResponse_malformedSse_skipsBadBucketsAndContinues() {
        val result = parser.parseFullResponse(MALFORMED_SSE)

        assertTrue(result.isSuccess)
        val updates = result.getOrThrow()

        // 2 valid buckets × 2 + 1 PortraitComplete = 5 (malformed bucket skipped)
        assertEquals(5, updates.size)

        val firstComplete = updates[1] as BucketUpdate.BucketComplete
        assertEquals(BucketType.SAFETY, firstComplete.content.type)

        val secondComplete = updates[3] as BucketUpdate.BucketComplete
        assertEquals(BucketType.HISTORY, secondComplete.content.type)

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
        assertEquals(2, updates2.size, "Should emit ContentDelta + BucketComplete for first bucket")
        val delta = updates2[0] as BucketUpdate.ContentDelta
        assertEquals(BucketType.SAFETY, delta.bucketType)
        assertEquals("Low crime area.", delta.textDelta)
        val complete = updates2[1] as BucketUpdate.BucketComplete
        assertEquals(BucketType.SAFETY, complete.content.type)
        assertEquals("Safe area", complete.content.highlight)

        // Feed rest of second bucket + POIS delimiter + POIs
        val chunk3 = ""","highlight":"Vibrant","content":"Great culture.","confidence":"MEDIUM","sources":[]}
---POIS---
[{"name":"Park","type":"park","description":"Nice park","confidence":"HIGH"}]"""
        val updates3 = streaming.processChunk(chunk3)
        assertEquals(2, updates3.size, "Should emit ContentDelta + BucketComplete for second bucket")
        assertEquals(BucketType.CHARACTER, (updates3[0] as BucketUpdate.ContentDelta).bucketType)

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

        // 6 buckets × 2 (ContentDelta + BucketComplete) + 1 PortraitComplete = 13
        assertEquals(13, allUpdates.size)

        // Verify first and last
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

        // 2 valid buckets × 2 + 1 PortraitComplete = 5
        assertEquals(5, allUpdates.size)
        val firstComplete = allUpdates[1] as BucketUpdate.BucketComplete
        assertEquals(BucketType.SAFETY, firstComplete.content.type)
        val secondComplete = allUpdates[3] as BucketUpdate.BucketComplete
        assertEquals(BucketType.HISTORY, secondComplete.content.type)
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
}
