package com.areadiscovery.ui.summary

import app.cash.turbine.test
import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Source
import com.areadiscovery.fakes.FakeAnalyticsTracker
import com.areadiscovery.fakes.FakeAreaIntelligenceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeProvider: FakeAreaIntelligenceProvider
    private val stateMapper = SummaryStateMapper()
    private lateinit var fakeTracker: FakeAnalyticsTracker

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProvider = FakeAreaIntelligenceProvider()
        fakeTracker = FakeAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() = runTest {
        fakeProvider.emissions = emptyList()
        val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)

        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun afterContentDeltaStateIsStreaming() = runTest {
        fakeProvider.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe "),
        )
        val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)

        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            val streaming = awaitItem()
            assertTrue(streaming is SummaryUiState.Streaming)
            assertEquals("Alfama, Lisbon", streaming.areaName)
            assertEquals("Safe ", streaming.buckets[BucketType.SAFETY]?.bodyText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun afterPortraitCompleteStateIsComplete() = runTest {
        val testContent = BucketContent(
            type = BucketType.SAFETY,
            highlight = "Very safe",
            content = "Alfama is safe",
            confidence = Confidence.HIGH,
            sources = listOf(Source("Test", null)),
        )
        val testPois = listOf(
            POI("Castle", "landmark", "Historic castle", Confidence.HIGH, 38.7, -9.1),
        )

        fakeProvider.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe "),
            BucketUpdate.BucketComplete(testContent),
            BucketUpdate.PortraitComplete(testPois),
        )
        val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)

        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            // Skip intermediate streaming states
            var state = awaitItem()
            while (state !is SummaryUiState.Complete) {
                state = awaitItem()
            }
            assertEquals("Alfama, Lisbon", state.areaName)
            assertEquals(1, state.pois.size)
            assertEquals("Castle", state.pois.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onProviderErrorStateIsError() = runTest {
        fakeProvider.shouldThrow = true
        fakeProvider.errorMessage = "Network failure"
        val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)

        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            val error = awaitItem()
            assertTrue(error is SummaryUiState.Error)
            assertEquals("Network failure", error.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshResetsToLoadingAndReCollects() = runTest {
        fakeProvider.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe "),
        )
        val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)

        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            // Wait for streaming state
            val streaming = awaitItem()
            assertTrue(streaming is SummaryUiState.Streaming)

            // Refresh
            fakeProvider.emissions = listOf(
                BucketUpdate.ContentDelta(BucketType.CHARACTER, "Charming "),
            )
            viewModel.refresh()

            val loading = awaitItem()
            assertEquals(SummaryUiState.Loading, loading)

            val newStreaming = awaitItem()
            assertTrue(newStreaming is SummaryUiState.Streaming)
            assertTrue(newStreaming.buckets.containsKey(BucketType.CHARACTER))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun summaryViewedEventIsTrackedWhenPortraitCompletes() = runTest {
        fakeProvider.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe area"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)

        viewModel.uiState.test {
            assertEquals(SummaryUiState.Loading, awaitItem())
            // Skip intermediate states until complete
            var state = awaitItem()
            while (state !is SummaryUiState.Complete) {
                state = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, fakeTracker.recordedEvents.size)
        fakeTracker.assertEventTracked("summary_viewed", mapOf("source" to "mock"))
    }
}
