package com.areadiscovery.ui.summary

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.Source
import com.areadiscovery.domain.usecase.GetAreaPortraitUseCase
import com.areadiscovery.fakes.FakeAnalyticsTracker
import com.areadiscovery.fakes.FakeAreaContextFactory
import com.areadiscovery.fakes.FakeAreaRepository
import com.areadiscovery.fakes.FakePrivacyPipeline
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    private lateinit var fakePipeline: FakePrivacyPipeline
    private lateinit var fakeContextFactory: FakeAreaContextFactory
    private lateinit var fakeUseCase: TestGetAreaPortraitUseCase
    private val stateMapper = SummaryStateMapper()
    private lateinit var fakeTracker: FakeAnalyticsTracker

    private fun initFakes() {
        fakePipeline = FakePrivacyPipeline()
        fakeContextFactory = FakeAreaContextFactory()
        fakeUseCase = TestGetAreaPortraitUseCase()
        fakeTracker = FakeAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SummaryViewModel(
        privacyPipeline = fakePipeline,
        areaContextFactory = fakeContextFactory,
        getAreaPortrait = fakeUseCase,
        stateMapper = stateMapper,
        analyticsTracker = fakeTracker,
    )

    @Test
    fun locationResolvingStateAppearsWhenPipelineSuspends() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        val suspendingPipeline = SuspendingFakePrivacyPipeline()
        val viewModel = SummaryViewModel(
            privacyPipeline = suspendingPipeline,
            areaContextFactory = fakeContextFactory,
            getAreaPortrait = fakeUseCase,
            stateMapper = stateMapper,
            analyticsTracker = fakeTracker,
        )

        assertIs<SummaryUiState.LocationResolving>(viewModel.uiState.value)
    }

    @Test
    fun afterContentDeltaStateIsStreaming() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Downtown SF"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe "),
        )
        val viewModel = createViewModel()

        val state = assertIs<SummaryUiState.Streaming>(viewModel.uiState.value)
        assertEquals("Downtown SF", state.areaName)
        assertEquals("Safe ", state.buckets[BucketType.SAFETY]?.bodyText)
    }

    @Test
    fun afterPortraitCompleteStateIsComplete() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Downtown SF"))
        val testContent = BucketContent(
            type = BucketType.SAFETY,
            highlight = "Very safe",
            content = "Safe area",
            confidence = Confidence.HIGH,
            sources = listOf(Source("Test", null)),
        )
        val testPois = listOf(
            POI("Castle", "landmark", "Historic castle", Confidence.HIGH, 38.7, -9.1),
        )
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe "),
            BucketUpdate.BucketComplete(testContent),
            BucketUpdate.PortraitComplete(testPois),
        )
        val viewModel = createViewModel()

        val state = assertIs<SummaryUiState.Complete>(viewModel.uiState.value)
        assertEquals("Downtown SF", state.areaName)
        assertEquals(1, state.pois.size)
        assertEquals("Castle", state.pois.first().name)
    }

    @Test
    fun gpsFailureShowsLocationFailedNotError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.failure(RuntimeException("GPS unavailable")))
        val viewModel = createViewModel()

        val state = assertIs<SummaryUiState.LocationFailed>(viewModel.uiState.value)
        assertEquals(SummaryViewModel.LOCATION_FAILURE_MESSAGE, state.message)
    }

    @Test
    fun refreshResetsAndReCollects() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Area A"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe "),
        )
        val viewModel = createViewModel()
        assertIs<SummaryUiState.Streaming>(viewModel.uiState.value)

        fakePipeline.setResult(Result.success("Area B"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.CHARACTER, "Charming "),
        )
        viewModel.refresh()

        val state = assertIs<SummaryUiState.Streaming>(viewModel.uiState.value)
        assertTrue(state.buckets.containsKey(BucketType.CHARACTER))
        assertEquals("Area B", state.areaName)
    }

    @Test
    fun analyticsTrackedWithGpsSourceAndAreaName() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Alfama, Lisbon"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe area"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        createViewModel()

        assertEquals(1, fakeTracker.recordedEvents.size)
        fakeTracker.assertEventTracked(
            "summary_viewed",
            mapOf("source" to "gps", "area_name" to "Alfama, Lisbon"),
        )
    }

    @Test
    fun useCaseReceivesResolvedAreaNameAndContext() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Bairro Alto"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
        )
        createViewModel()

        assertEquals("Bairro Alto", fakeUseCase.lastAreaName)
        assertEquals(fakeContextFactory.create(), fakeUseCase.lastContext)
    }

    @Test
    fun zeroScrollDepthSuppressesEvent() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Test Area"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.onScreenExit()

        val scrollEvents = fakeTracker.recordedEvents.filter { it.first == "summary_scroll_depth" }
        assertTrue(scrollEvents.isEmpty())
    }

    @Test
    fun refreshResetsScrollDepth() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Area A"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.updateScrollDepth(80)

        fakePipeline.setResult(Result.success("Area B"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        viewModel.refresh()
        viewModel.onScreenExit()

        val scrollEvents = fakeTracker.recordedEvents.filter { it.first == "summary_scroll_depth" }
        assertTrue(scrollEvents.isEmpty())
    }

    @Test
    fun scrollDepthTrackedOnScreenExit() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        initFakes()
        fakePipeline.setResult(Result.success("Test Area"))
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.updateScrollDepth(75)
        viewModel.onScreenExit()

        fakeTracker.assertEventTracked(
            "summary_scroll_depth",
            mapOf("area_name" to "Test Area", "depth_percent" to "75"),
        )
    }
}

class SuspendingFakePrivacyPipeline : FakePrivacyPipeline() {
    private val deferred = CompletableDeferred<Result<String>>()
    override suspend fun resolveAreaName(): Result<String> = deferred.await()
}

class TestGetAreaPortraitUseCase : GetAreaPortraitUseCase(FakeAreaRepository()) {
    var emissions: List<BucketUpdate> = emptyList()
    var shouldThrow: Boolean = false
    var errorMessage: String = "Test error"
    var callCount = 0
    var lastAreaName: String? = null
    var lastContext: AreaContext? = null

    override fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> {
        callCount++
        lastAreaName = areaName
        lastContext = context
        return flow {
            if (shouldThrow) throw RuntimeException(errorMessage)
            emissions.forEach { emit(it) }
        }
    }
}
