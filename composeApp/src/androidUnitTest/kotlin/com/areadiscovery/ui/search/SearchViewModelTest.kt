package com.areadiscovery.ui.search

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.usecase.SearchAreaUseCase
import com.areadiscovery.fakes.FakeAnalyticsTracker
import com.areadiscovery.fakes.FakeAreaContextFactory
import com.areadiscovery.fakes.FakeAreaRepository
import com.areadiscovery.fakes.FakeClock
import com.areadiscovery.ui.summary.SummaryStateMapper
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AreaDiscoveryDatabase
    private lateinit var fakeUseCase: TestSearchAreaUseCase
    private lateinit var fakeContextFactory: FakeAreaContextFactory
    private lateinit var fakeTracker: FakeAnalyticsTracker
    private lateinit var fakeClock: FakeClock
    private val stateMapper = SummaryStateMapper()

    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher

    @BeforeTest
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AreaDiscoveryDatabase.Schema.create(driver)
        database = AreaDiscoveryDatabase(driver)
        fakeUseCase = TestSearchAreaUseCase()
        fakeContextFactory = FakeAreaContextFactory()
        fakeTracker = FakeAnalyticsTracker()
        fakeClock = FakeClock()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun createViewModel() = SearchViewModel(
        searchAreaUseCase = fakeUseCase,
        areaContextFactory = fakeContextFactory,
        stateMapper = stateMapper,
        analyticsTracker = fakeTracker,
        database = database,
        clock = fakeClock,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun searchEmitsLoadingThenComplete() = runTest {
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe area"),
            BucketUpdate.PortraitComplete(pois = listOf(
                POI("Shrine", "landmark", "A temple", Confidence.HIGH, 35.6, 139.7),
            )),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertIs<SearchUiState.Idle>(awaitItem())

            viewModel.search("Shibuya, Tokyo")

            // StateFlow conflation may merge Loading+Streaming; verify at least Loading or final Complete
            val emissions = mutableListOf<SearchUiState>()
            while (true) {
                val item = awaitItem()
                emissions.add(item)
                if (item is SearchUiState.Complete) break
            }

            // Loading should be the first emission (set synchronously before coroutine)
            assertIs<SearchUiState.Loading>(emissions.first())
            val complete = assertIs<SearchUiState.Complete>(emissions.last())
            assertEquals("Shibuya, Tokyo", complete.query)
            assertEquals(1, complete.pois.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchPersistsQueryToHistory() = runTest {
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.CHARACTER, "Charming"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.search("Paris")

        val recent = database.search_historyQueries.getRecentSearches().executeAsList()
        assertTrue(recent.contains("Paris"))
    }

    @Test
    fun searchFiresAnalyticsOnComplete() = runTest {
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.search("Paris")

        fakeTracker.assertEventTracked(
            "summary_viewed",
            mapOf("source" to "search", "area_name" to "Paris"),
        )
    }

    @Test
    fun searchErrorTransitionsToErrorState() = runTest {
        fakeUseCase.shouldThrow = true
        fakeUseCase.errorMessage = "Network error"
        val viewModel = createViewModel()

        viewModel.search("Tokyo")

        val state = assertIs<SearchUiState.Error>(viewModel.uiState.value)
        assertEquals("Tokyo", state.query)
        assertEquals("Network error", state.message)
    }

    @Test
    fun clearSearchRestoresIdleWithRecentSearches() = runTest {
        fakeUseCase.emissions = listOf(
            BucketUpdate.ContentDelta(BucketType.SAFETY, "Safe"),
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.search("Paris")
        assertIs<SearchUiState.Complete>(viewModel.uiState.value)

        viewModel.clearSearch()

        val state = assertIs<SearchUiState.Idle>(viewModel.uiState.value)
        assertTrue(state.recentSearches.contains("Paris"))
    }

    @Test
    fun searchShowsLoadingImmediately() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeUseCase.emissions = emptyList()
        fakeUseCase.suspendUntil = gate
        val viewModel = createViewModel()

        viewModel.search("London")

        val state = assertIs<SearchUiState.Loading>(viewModel.uiState.value)
        assertEquals("London", state.query)
        gate.complete(Unit)
    }

    @Test
    fun blankQueryIsIgnored() = runTest {
        val viewModel = createViewModel()

        viewModel.search("   ")

        assertIs<SearchUiState.Idle>(viewModel.uiState.value)
        assertEquals(0, fakeUseCase.callCount)
    }

    @Test
    fun recentSearchesOrderedByMostRecent() = runTest {
        fakeUseCase.emissions = listOf(
            BucketUpdate.PortraitComplete(pois = emptyList()),
        )
        val viewModel = createViewModel()

        fakeClock.nowMs = 1000L
        viewModel.search("First")
        fakeClock.nowMs = 2000L
        viewModel.search("Second")
        fakeClock.nowMs = 3000L
        viewModel.search("Third")

        viewModel.clearSearch()

        val state = assertIs<SearchUiState.Idle>(viewModel.uiState.value)
        assertEquals(listOf("Third", "Second", "First"), state.recentSearches)
    }
}

class TestSearchAreaUseCase : SearchAreaUseCase(FakeAreaRepository()) {
    var emissions: List<BucketUpdate> = emptyList()
    var shouldThrow: Boolean = false
    var errorMessage: String = "Test error"
    var callCount = 0
    var suspendUntil: CompletableDeferred<Unit>? = null

    override fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> {
        callCount++
        return flow {
            suspendUntil?.await()
            if (shouldThrow) throw RuntimeException(errorMessage)
            emissions.forEach { emit(it) }
        }
    }
}
