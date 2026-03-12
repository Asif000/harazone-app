package com.harazone.ui.saved

import com.harazone.domain.model.SavedPoi
import com.harazone.fakes.FakeSavedPoiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPlacesViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makePoi(
        id: String,
        name: String = "Place $id",
        type: String = "restaurant",
        areaName: String = "Area A",
        lat: Double = 25.0,
        lng: Double = -80.0,
        userNote: String? = null,
    ) = SavedPoi(
        id = id,
        name = name,
        type = type,
        areaName = areaName,
        lat = lat,
        lng = lng,
        whySpecial = "Special",
        savedAt = 1_000_000L,
        userNote = userNote,
    )

    private fun createViewModel(
        repo: FakeSavedPoiRepository = FakeSavedPoiRepository(),
    ): Pair<SavedPlacesViewModel, FakeSavedPoiRepository> {
        val vm = SavedPlacesViewModel(repo)
        return vm to repo
    }

    @Test
    fun capsule_nearestGroup_isHighlighted() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", areaName = "Area A", lat = 25.76, lng = -80.19))
        repo.save(makePoi("2", areaName = "Area B", lat = 40.71, lng = -74.01))
        repo.save(makePoi("3", areaName = "Area C", lat = 48.85, lng = 2.35))
        val (vm, _) = createViewModel(repo)

        vm.onLocationUpdated(25.77, -80.20)

        val state = vm.uiState.value
        assertEquals("All", state.capsules[0].label)
        val nearestCapsule = state.capsules.first { it.isNearest }
        assertEquals("Area A", nearestCapsule.label)
        assertEquals("Area A", state.capsules[1].label)
    }

    @Test
    fun capsule_noGps_sortedByCount() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", areaName = "Small"))
        repo.save(makePoi("2", areaName = "Big"))
        repo.save(makePoi("3", areaName = "Big"))
        repo.save(makePoi("4", areaName = "Big"))
        val (vm, _) = createViewModel(repo)

        vm.onLocationUpdated(null, null)

        val state = vm.uiState.value
        assertTrue(state.capsules.none { it.isNearest })
        assertEquals("Big", state.capsules[1].label)
        assertEquals("Small", state.capsules[2].label)
    }

    @Test
    fun search_matchesName_caseInsensitive() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", name = "Blue Note Jazz Club"))
        repo.save(makePoi("2", name = "Pizza Palace"))
        val (vm, _) = createViewModel(repo)

        vm.onSearchQueryChanged("jazz")

        val filtered = vm.uiState.value.filteredSaves
        assertEquals(1, filtered.size)
        assertEquals("Blue Note Jazz Club", filtered[0].name)
    }

    @Test
    fun search_matchesAreaName() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", name = "Art Gallery", areaName = "Wynwood, Miami"))
        repo.save(makePoi("2", name = "Park", areaName = "Central Park, NYC"))
        val (vm, _) = createViewModel(repo)

        vm.onSearchQueryChanged("wynwood")

        val filtered = vm.uiState.value.filteredSaves
        assertEquals(1, filtered.size)
        assertEquals("Art Gallery", filtered[0].name)
    }

    @Test
    fun capsule_and_search_intersection() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", name = "Jazz Bar", areaName = "Wynwood"))
        repo.save(makePoi("2", name = "Jazz Club", areaName = "Downtown"))
        repo.save(makePoi("3", name = "Pizza Place", areaName = "Wynwood"))
        val (vm, _) = createViewModel(repo)

        vm.selectCapsule("Wynwood")
        vm.onSearchQueryChanged("jazz")

        val filtered = vm.uiState.value.filteredSaves
        assertEquals(1, filtered.size)
        assertEquals("Jazz Bar", filtered[0].name)
    }

    @Test
    fun unsave_removesFromFilteredImmediately() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        repo.save(makePoi("2"))
        val (vm, _) = createViewModel(repo)

        assertEquals(2, vm.uiState.value.filteredSaves.size)

        vm.unsavePoi("1")

        val filtered = vm.uiState.value.filteredSaves
        assertEquals(1, filtered.size)
        assertFalse(filtered.any { it.id == "1" })
    }

    @Test
    fun undo_restoresCard() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        val (vm, _) = createViewModel(repo)

        vm.unsavePoi("1")
        assertEquals(0, vm.uiState.value.filteredSaves.size)

        vm.commitUnsave("1", undo = true)
        assertEquals(1, vm.uiState.value.filteredSaves.size)
        assertEquals("1", vm.uiState.value.filteredSaves[0].id)
    }

    @Test
    fun undo_preventsDbDelete() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        val (vm, _) = createViewModel(repo)

        vm.unsavePoi("1")
        vm.commitUnsave("1", undo = true)

        val allPois = vm.uiState.value.saves
        assertTrue(allPois.any { it.id == "1" })
    }

    @Test
    fun discoveryStory_nullWhenFewerThanTwoSaves() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        val (vm, _) = createViewModel(repo)

        assertNull(vm.uiState.value.discoveryStory)
    }

    @Test
    fun discoveryStory_generatedWithCorrectCounts() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", areaName = "Area A"))
        repo.save(makePoi("2", areaName = "Area B"))
        repo.save(makePoi("3", areaName = "Area C"))
        repo.save(makePoi("4", areaName = "Area A"))
        repo.save(makePoi("5", areaName = "Area B"))
        val (vm, _) = createViewModel(repo)

        val story = vm.uiState.value.discoveryStory
        assertNotNull(story)
        assertTrue(story.summary.contains("5"))
        assertTrue(story.summary.contains("Area A"))
        assertTrue(story.summary.contains("Area B"))
        assertTrue(story.summary.contains("Area C"))
    }

    @Test
    fun unsave_updatesCapsuleCounts() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1", areaName = "Area A"))
        repo.save(makePoi("2", areaName = "Area A"))
        repo.save(makePoi("3", areaName = "Area B"))
        val (vm, _) = createViewModel(repo)

        assertEquals(3, vm.uiState.value.capsules.first { it.label == "All" }.count)

        vm.unsavePoi("1")

        // Capsule counts should reflect visible saves (excluding pending unsave)
        assertEquals(2, vm.uiState.value.capsules.first { it.label == "All" }.count)
        assertEquals(1, vm.uiState.value.capsules.first { it.label == "Area A" }.count)
    }

    @Test
    fun commitUnsave_dbErrorDoesNotCrash() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        val (vm, _) = createViewModel(repo)

        vm.unsavePoi("1")
        repo.shouldThrow = true
        // Should not throw — error is caught internally
        vm.commitUnsave("1", undo = false)

        // Pending ID cleared even on error (DB flow will correct)
        assertFalse(vm.uiState.value.pendingUnsaveIds.contains("1"))
    }

    @Test
    fun commitAllPendingUnsaves_clearsAll() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        repo.save(makePoi("2"))
        val (vm, _) = createViewModel(repo)

        vm.unsavePoi("1")
        vm.unsavePoi("2")
        assertEquals(2, vm.uiState.value.pendingUnsaveIds.size)

        vm.commitAllPendingUnsaves()

        assertTrue(vm.uiState.value.pendingUnsaveIds.isEmpty())
    }
}
