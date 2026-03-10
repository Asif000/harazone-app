package com.harazone.data.remote

import app.cash.turbine.test
import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.Confidence
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAreaIntelligenceProviderTest {

    private val provider = MockAreaIntelligenceProvider()
    private val context = AreaContext(
        timeOfDay = "afternoon",
        dayOfWeek = "Tuesday",
        visitCount = 0,
        preferredLanguage = "en"
    )

    @Test
    fun mockProviderEmitsAllSixBucketTypes() = runTest {
        provider.streamAreaPortrait("Alfama", context).test {
            val emittedBucketTypes = mutableSetOf<BucketType>()
            while (true) {
                val item = awaitItem()
                when (item) {
                    is BucketUpdate.ContentDelta -> emittedBucketTypes.add(item.bucketType)
                    is BucketUpdate.BucketComplete -> emittedBucketTypes.add(item.content.type)
                    is BucketUpdate.PortraitComplete -> break
                    is BucketUpdate.ContentAvailabilityNote -> {}
                }
            }
            assertEquals(BucketType.entries.toSet(), emittedBucketTypes)
            awaitComplete()
        }
    }

    @Test
    fun mockProviderFlowCompletes() = runTest {
        provider.streamAreaPortrait("Alfama", context).test {
            var foundPortraitComplete = false
            while (true) {
                val item = awaitItem()
                if (item is BucketUpdate.PortraitComplete) {
                    foundPortraitComplete = true
                    break
                }
            }
            assertTrue(foundPortraitComplete)
            awaitComplete()
        }
    }

    @Test
    fun mockDataContainsAtLeastThreePOIs() {
        assertTrue(MockAreaIntelligenceProvider.mockPOIs.size >= 3)
    }

    @Test
    fun mockDataContainsMixedConfidenceLevels() {
        val confidenceLevels = MockAreaIntelligenceProvider.mockBuckets
            .map { it.confidence }
            .toSet()
        assertTrue(confidenceLevels.contains(Confidence.HIGH))
        assertTrue(confidenceLevels.contains(Confidence.MEDIUM))
        assertTrue(confidenceLevels.contains(Confidence.LOW))
    }

    @Test
    fun mockProviderEmitsBucketsSequentially() = runTest {
        provider.streamAreaPortrait("Alfama", context).test {
            val bucketOrder = mutableListOf<BucketType>()
            while (true) {
                val item = awaitItem()
                when (item) {
                    is BucketUpdate.BucketComplete -> bucketOrder.add(item.content.type)
                    is BucketUpdate.PortraitComplete -> break
                    is BucketUpdate.ContentDelta -> { /* collect but don't track order here */ }
                    is BucketUpdate.ContentAvailabilityNote -> {}
                }
            }
            assertEquals(
                listOf(
                    BucketType.SAFETY,
                    BucketType.CHARACTER,
                    BucketType.WHATS_HAPPENING,
                    BucketType.COST,
                    BucketType.HISTORY,
                    BucketType.NEARBY
                ),
                bucketOrder
            )
            awaitComplete()
        }
    }

    @Test
    fun mockChatResponseFlowCompletes() = runTest {
        provider.streamChatResponse("Tell me about Alfama", "Alfama", emptyList()).test {
            var lastToken = awaitItem()
            while (!lastToken.isComplete) {
                lastToken = awaitItem()
            }
            assertTrue(lastToken.isComplete)
            awaitComplete()
        }
    }

    @Test
    fun portraitCompleteEmitsPOIsViaInterface() = runTest {
        provider.streamAreaPortrait("Alfama", context).test {
            var portraitComplete: BucketUpdate.PortraitComplete? = null
            while (true) {
                val item = awaitItem()
                if (item is BucketUpdate.PortraitComplete) {
                    portraitComplete = item
                    break
                }
            }
            assertTrue(portraitComplete!!.pois.size >= 3, "PortraitComplete must carry at least 3 POIs")
            awaitComplete()
        }
    }

    @Test
    fun eachBucketHasHighlightWhoaFact() {
        for (bucket in MockAreaIntelligenceProvider.mockBuckets) {
            assertTrue(
                bucket.highlight.isNotBlank(),
                "Bucket ${bucket.type} should have a non-blank highlight"
            )
        }
    }
}
