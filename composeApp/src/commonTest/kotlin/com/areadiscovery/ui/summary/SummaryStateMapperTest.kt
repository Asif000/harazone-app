package com.areadiscovery.ui.summary

import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryStateMapperTest {

    private val mapper = SummaryStateMapper()
    private val testAreaName = "Test Area"

    // --- Test 9.6: Initial state is Loading, first ContentDelta transitions to Streaming ---

    @Test
    fun `first ContentDelta transitions Loading to Streaming with areaName`() {
        val state = SummaryUiState.Loading
        val delta = BucketUpdate.ContentDelta(BucketType.SAFETY, "Crime is ")

        val result = mapper.processUpdate(state, delta, areaName = testAreaName)

        val streaming = assertIs<SummaryUiState.Streaming>(result)
        assertEquals(testAreaName, streaming.areaName)
        assertEquals(1, streaming.buckets.size)
        val bucket = streaming.buckets[BucketType.SAFETY]!!
        assertEquals("Crime is ", bucket.bodyText)
        assertTrue(bucket.isStreaming)
        assertEquals(false, bucket.isComplete)
        assertEquals("", bucket.highlightText)
    }

    // --- Test 9.2: ContentDelta appends text to correct bucket in Streaming state ---

    @Test
    fun `ContentDelta appends text to correct bucket`() {
        val initial = SummaryUiState.Streaming(
            buckets = mapOf(
                BucketType.SAFETY to BucketDisplayState(
                    bucketType = BucketType.SAFETY,
                    bodyText = "Crime is ",
                    isStreaming = true,
                ),
            ),
            areaName = testAreaName,
        )
        val delta = BucketUpdate.ContentDelta(BucketType.SAFETY, "low in this area.")

        val result = mapper.processUpdate(initial, delta, areaName = testAreaName)

        val streaming = assertIs<SummaryUiState.Streaming>(result)
        assertEquals("Crime is low in this area.", streaming.buckets[BucketType.SAFETY]!!.bodyText)
        assertTrue(streaming.buckets[BucketType.SAFETY]!!.isStreaming)
    }

    // --- Test 9.3: BucketComplete finalizes bucket with highlight, confidence, sources ---

    @Test
    fun `BucketComplete finalizes bucket with highlight confidence and sources`() {
        val initial = SummaryUiState.Streaming(
            buckets = mapOf(
                BucketType.SAFETY to BucketDisplayState(
                    bucketType = BucketType.SAFETY,
                    bodyText = "Crime is low in this area.",
                    isStreaming = true,
                ),
            ),
            areaName = testAreaName,
        )
        val sources = listOf(Source("CrimeStats", "https://example.com"))
        val complete = BucketUpdate.BucketComplete(
            content = BucketContent(
                type = BucketType.SAFETY,
                highlight = "Safest neighborhood in the city!",
                content = "Crime is low in this area.",
                confidence = Confidence.HIGH,
                sources = sources,
            ),
        )

        val result = mapper.processUpdate(initial, complete, areaName = testAreaName)

        val streaming = assertIs<SummaryUiState.Streaming>(result)
        val bucket = streaming.buckets[BucketType.SAFETY]!!
        assertEquals("Safest neighborhood in the city!", bucket.highlightText)
        assertEquals(Confidence.HIGH, bucket.confidence)
        assertEquals(sources, bucket.sources)
        assertEquals(false, bucket.isStreaming)
        assertTrue(bucket.isComplete)
    }

    // --- Test 9.4: PortraitComplete transitions from Streaming to Complete with POIs ---

    @Test
    fun `PortraitComplete transitions Streaming to Complete with POIs`() {
        val initial = SummaryUiState.Streaming(
            buckets = mapOf(
                BucketType.SAFETY to BucketDisplayState(
                    bucketType = BucketType.SAFETY,
                    bodyText = "Crime is low.",
                    isStreaming = false,
                    isComplete = true,
                ),
            ),
            areaName = testAreaName,
        )
        val pois = listOf(
            POI("Coffee Shop", "cafe", "Great coffee", Confidence.HIGH, 40.0, -74.0),
        )
        val portraitComplete = BucketUpdate.PortraitComplete(pois = pois)

        val result = mapper.processUpdate(initial, portraitComplete, areaName = testAreaName)

        val complete = assertIs<SummaryUiState.Complete>(result)
        assertEquals(pois, complete.pois)
        assertEquals(testAreaName, complete.areaName)
        assertTrue(complete.buckets[BucketType.SAFETY]!!.isComplete)
    }

    // --- Test 9.5: Multiple sequential buckets stream correctly ---

    @Test
    fun `multiple sequential buckets stream correctly`() {
        var state: SummaryUiState = SummaryUiState.Loading

        // SAFETY starts streaming
        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe area."), areaName = testAreaName)
        val streaming1 = assertIs<SummaryUiState.Streaming>(state)
        assertEquals(1, streaming1.buckets.size)
        assertEquals(testAreaName, streaming1.areaName)

        // SAFETY completes
        state = mapper.processUpdate(
            state,
            BucketUpdate.BucketComplete(
                BucketContent(BucketType.SAFETY, "Very safe!", "Safe area.", Confidence.HIGH, emptyList()),
            ),
            areaName = testAreaName,
        )

        // CHARACTER starts streaming
        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.CHARACTER, "Vibrant "), areaName = testAreaName)
        val streaming2 = assertIs<SummaryUiState.Streaming>(state)
        assertEquals(2, streaming2.buckets.size)
        assertTrue(streaming2.buckets[BucketType.SAFETY]!!.isComplete)
        assertTrue(streaming2.buckets[BucketType.CHARACTER]!!.isStreaming)
        assertEquals("Vibrant ", streaming2.buckets[BucketType.CHARACTER]!!.bodyText)

        // CHARACTER gets more text
        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.CHARACTER, "neighborhood."), areaName = testAreaName)
        val streaming3 = assertIs<SummaryUiState.Streaming>(state)
        assertEquals("Vibrant neighborhood.", streaming3.buckets[BucketType.CHARACTER]!!.bodyText)
    }

    // --- Test 9.7: ContentDelta for unseen bucket type creates new entry defensively ---

    @Test
    fun `ContentDelta for unseen bucket creates new entry defensively`() {
        val initial = SummaryUiState.Streaming(
            buckets = mapOf(
                BucketType.SAFETY to BucketDisplayState(
                    bucketType = BucketType.SAFETY,
                    bodyText = "Safe area.",
                    isStreaming = false,
                    isComplete = true,
                ),
            ),
            areaName = testAreaName,
        )

        // Receive delta for a bucket not yet in the map
        val result = mapper.processUpdate(
            initial,
            BucketUpdate.ContentDelta(BucketType.HISTORY, "Founded in 1800."),
            areaName = testAreaName,
        )

        val streaming = assertIs<SummaryUiState.Streaming>(result)
        assertEquals(2, streaming.buckets.size)
        val historyBucket = streaming.buckets[BucketType.HISTORY]!!
        assertEquals("Founded in 1800.", historyBucket.bodyText)
        assertTrue(historyBucket.isStreaming)
        assertEquals("", historyBucket.highlightText)
        assertNull(historyBucket.confidence)
    }

    // --- Additional: ContentDelta never writes to highlightText ---

    @Test
    fun `ContentDelta never populates highlightText`() {
        var state: SummaryUiState = SummaryUiState.Loading

        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.COST, "Affordable "), areaName = testAreaName)
        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.COST, "area with "), areaName = testAreaName)
        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.COST, "low rent."), areaName = testAreaName)

        val streaming = assertIs<SummaryUiState.Streaming>(state)
        val bucket = streaming.buckets[BucketType.COST]!!
        assertEquals("Affordable area with low rent.", bucket.bodyText)
        assertEquals("", bucket.highlightText)
    }

    // --- PortraitComplete on non-Streaming state returns current state ---

    @Test
    fun `PortraitComplete on Loading state returns Loading`() {
        val result = mapper.processUpdate(
            SummaryUiState.Loading,
            BucketUpdate.PortraitComplete(pois = emptyList()),
            areaName = testAreaName,
        )
        assertIs<SummaryUiState.Loading>(result)
    }

    // --- ContentAvailabilityNote handling ---

    @Test
    fun `ContentAvailabilityNote on Streaming sets contentNote`() {
        val initial = SummaryUiState.Streaming(
            buckets = mapOf(
                BucketType.SAFETY to BucketDisplayState(
                    bucketType = BucketType.SAFETY,
                    bodyText = "Safe area.",
                    isStreaming = true,
                ),
            ),
            areaName = testAreaName,
        )
        val note = BucketUpdate.ContentAvailabilityNote("You're offline - showing last known content")

        val result = mapper.processUpdate(initial, note, areaName = testAreaName)

        val streaming = assertIs<SummaryUiState.Streaming>(result)
        assertEquals("You're offline - showing last known content", streaming.contentNote)
    }

    @Test
    fun `ContentAvailabilityNote on Complete sets contentNote`() {
        val initial = SummaryUiState.Complete(
            buckets = mapOf(
                BucketType.SAFETY to BucketDisplayState(
                    bucketType = BucketType.SAFETY,
                    bodyText = "Safe area.",
                    isComplete = true,
                ),
            ),
            pois = emptyList(),
            areaName = testAreaName,
        )
        val note = BucketUpdate.ContentAvailabilityNote("Cached content from 2 hours ago")

        val result = mapper.processUpdate(initial, note, areaName = testAreaName)

        val complete = assertIs<SummaryUiState.Complete>(result)
        assertEquals("Cached content from 2 hours ago", complete.contentNote)
    }

    @Test
    fun `ContentAvailabilityNote on Loading transitions to Streaming with note`() {
        val result = mapper.processUpdate(
            SummaryUiState.Loading,
            BucketUpdate.ContentAvailabilityNote("Some note"),
            areaName = testAreaName,
        )
        val streaming = assertIs<SummaryUiState.Streaming>(result)
        assertEquals("Some note", streaming.contentNote)
        assertEquals(testAreaName, streaming.areaName)
        assertTrue(streaming.buckets.isEmpty())
    }

    @Test
    fun `ContentAvailabilityNote on LocationResolving transitions to Streaming with note`() {
        val result = mapper.processUpdate(
            SummaryUiState.LocationResolving,
            BucketUpdate.ContentAvailabilityNote("Offline content"),
            areaName = testAreaName,
        )
        val streaming = assertIs<SummaryUiState.Streaming>(result)
        assertEquals("Offline content", streaming.contentNote)
        assertEquals(testAreaName, streaming.areaName)
    }

    @Test
    fun `ContentAvailabilityNote on LocationFailed returns unchanged`() {
        val initial = SummaryUiState.LocationFailed("Can't find your location")
        val result = mapper.processUpdate(
            initial,
            BucketUpdate.ContentAvailabilityNote("Some note"),
            areaName = testAreaName,
        )
        val locationFailed = assertIs<SummaryUiState.LocationFailed>(result)
        assertEquals("Can't find your location", locationFailed.message)
    }

    @Test
    fun `contentNote carries from Streaming to Complete via PortraitComplete`() {
        var state: SummaryUiState = SummaryUiState.Loading

        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe."), areaName = testAreaName)
        state = mapper.processUpdate(state, BucketUpdate.ContentAvailabilityNote("Stale content"), areaName = testAreaName)
        val streaming = assertIs<SummaryUiState.Streaming>(state)
        assertEquals("Stale content", streaming.contentNote)

        state = mapper.processUpdate(
            state,
            BucketUpdate.BucketComplete(
                BucketContent(BucketType.SAFETY, "Very safe!", "Safe.", Confidence.HIGH, emptyList()),
            ),
            areaName = testAreaName,
        )
        state = mapper.processUpdate(state, BucketUpdate.PortraitComplete(pois = emptyList()), areaName = testAreaName)

        val complete = assertIs<SummaryUiState.Complete>(state)
        assertEquals("Stale content", complete.contentNote)
    }

    @Test
    fun `ContentDelta after ContentAvailabilityNote preserves contentNote`() {
        var state: SummaryUiState = SummaryUiState.Loading

        state = mapper.processUpdate(state, BucketUpdate.ContentAvailabilityNote("Offline — showing cached data"), areaName = testAreaName)
        val streamingWithNote = assertIs<SummaryUiState.Streaming>(state)
        assertEquals("Offline — showing cached data", streamingWithNote.contentNote)

        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe area."), areaName = testAreaName)
        val streamingAfterDelta = assertIs<SummaryUiState.Streaming>(state)
        assertEquals("Offline — showing cached data", streamingAfterDelta.contentNote)
        assertEquals("Safe area.", streamingAfterDelta.buckets[BucketType.SAFETY]!!.bodyText)

        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.CHARACTER, "Vibrant."), areaName = testAreaName)
        val streamingAfterSecondDelta = assertIs<SummaryUiState.Streaming>(state)
        assertEquals("Offline — showing cached data", streamingAfterSecondDelta.contentNote)
    }

    // --- areaName from Loading is preserved through full lifecycle ---

    @Test
    fun `areaName provided at Loading carries through to Complete`() {
        var state: SummaryUiState = SummaryUiState.Loading

        state = mapper.processUpdate(state, BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe."), areaName = "Downtown SF")
        val streaming = assertIs<SummaryUiState.Streaming>(state)
        assertEquals("Downtown SF", streaming.areaName)

        state = mapper.processUpdate(
            state,
            BucketUpdate.BucketComplete(
                BucketContent(BucketType.SAFETY, "Very safe!", "Safe.", Confidence.HIGH, emptyList()),
            ),
            areaName = "Downtown SF",
        )

        state = mapper.processUpdate(state, BucketUpdate.PortraitComplete(pois = emptyList()), areaName = "Downtown SF")
        val complete = assertIs<SummaryUiState.Complete>(state)
        assertEquals("Downtown SF", complete.areaName)
    }
}
