package com.harazone.ui.saved

import com.harazone.domain.model.SavedPoi
import com.harazone.fakes.FakeSavedPoiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    fun discoveryStory_nullWhenFewerThanFiveSaves() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("1"))
        repo.save(makePoi("2"))
        repo.save(makePoi("3"))
        repo.save(makePoi("4"))
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

    // --- User Notes tests ---

    @Test
    fun onStartEditingNote_setsEditingNotePoiId() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        vm.onStartEditingNote("poi-1")

        assertEquals("poi-1", vm.uiState.value.editingNotePoiId)
    }

    @Test
    fun onStopEditingNote_clearsEditingNotePoiId() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        vm.onStartEditingNote("poi-1")
        vm.onStopEditingNote("note text")

        assertNull(vm.uiState.value.editingNotePoiId)
    }

    @Test
    fun onNoteChanged_savesAfterDebounce() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        vm.onNoteChanged("poi-1", "Great spot")
        advanceTimeBy(600)

        assertEquals("Great spot", repo.lastUpdatedNote)
        assertEquals("poi-1", repo.lastUpdatedPoiId)
    }

    @Test
    fun onNoteChanged_blankNotesPersistAsNull() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        vm.onNoteChanged("poi-1", "   ")
        advanceTimeBy(600)

        assertNull(repo.lastUpdatedNote)
    }

    @Test
    fun onStopEditingNote_flushesImmediately() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        vm.onStartEditingNote("poi-1")
        vm.onNoteChanged("poi-1", "Fast note")
        // Do NOT advance time — stop immediately within 500ms
        vm.onStopEditingNote("Fast note")

        assertEquals("Fast note", repo.lastUpdatedNote)
    }

    // Regression: H1 — onStopEditingNote must use the text passed by the caller (composable's noteText),
    // not the DB value. This test verifies the ViewModel correctly saves whatever finalNote is passed.
    @Test
    fun onStopEditingNote_usesPassedTextNotDbValue() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1", userNote = null))
        val (vm, _) = createViewModel(repo)

        vm.onStartEditingNote("poi-1")
        // User types "Great spot" but debounce hasn't fired — DB still has null
        vm.onNoteChanged("poi-1", "Great spot")
        // Stop with the CURRENT typed text (as card's PlatformBackHandler would pass)
        vm.onStopEditingNote("Great spot")

        assertEquals("Great spot", repo.lastUpdatedNote)
        assertEquals("poi-1", repo.lastUpdatedPoiId)
    }

    // Regression: M1 — switching cards must flush the previous card's pending note
    @Test
    fun onStartEditingNote_flushesPreviousCardNote() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        repo.save(makePoi("poi-2"))
        val (vm, _) = createViewModel(repo)

        vm.onStartEditingNote("poi-1")
        vm.onNoteChanged("poi-1", "Note for card 1")
        // Switch to card 2 without stopping card 1 — should auto-flush
        vm.onStartEditingNote("poi-2")

        assertEquals("poi-1", repo.lastUpdatedPoiId)
        assertEquals("Note for card 1", repo.lastUpdatedNote)
        assertEquals("poi-2", vm.uiState.value.editingNotePoiId)
    }

    // Regression: M2 — updateUserNote DB error should not crash
    @Test
    fun onNoteChanged_dbErrorDoesNotCrash() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        repo.shouldThrow = true
        vm.onNoteChanged("poi-1", "Will fail")
        advanceTimeBy(600)

        // Should not throw — error is caught silently
        // lastUpdatedNote is NOT set because shouldThrow prevented it
        assertNull(repo.lastUpdatedNote)
    }

    // Regression: M3 — flushPendingNoteEdit saves tracked text on dispose
    @Test
    fun flushPendingNoteEdit_savesTrackedText() = runTest {
        val repo = FakeSavedPoiRepository()
        repo.save(makePoi("poi-1"))
        val (vm, _) = createViewModel(repo)

        vm.onStartEditingNote("poi-1")
        vm.onNoteChanged("poi-1", "Unsaved on dismiss")
        // Simulate sheet dismiss — flushPendingNoteEdit uses tracked currentEditingNoteText
        vm.flushPendingNoteEdit()

        assertEquals("Unsaved on dismiss", repo.lastUpdatedNote)
        assertNull(vm.uiState.value.editingNotePoiId)
    }
}
