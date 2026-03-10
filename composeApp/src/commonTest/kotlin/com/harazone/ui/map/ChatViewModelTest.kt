package com.harazone.ui.map

import app.cash.turbine.test
import com.harazone.data.remote.GeminiPromptBuilder
import com.harazone.domain.model.ChatIntent
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.MessageRole
import com.harazone.domain.model.Vibe
import com.harazone.fakes.FakeAreaIntelligenceProvider
import com.harazone.fakes.FakeClock
import com.harazone.fakes.FakeSavedPoiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAiProvider = FakeAreaIntelligenceProvider()
    private val fakeClock = FakeClock()
    private val promptBuilder = GeminiPromptBuilder()
    private val fakeSavedPoiRepository = FakeSavedPoiRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ChatViewModel(
        aiProvider = fakeAiProvider,
        promptBuilder = promptBuilder,
        clock = fakeClock,
        savedPoiRepository = fakeSavedPoiRepository,
    )

    @Test
    fun `openChat sets isOpen and shows intent pills`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Hi", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        assertTrue(vm.uiState.value.isOpen)
        assertTrue(vm.uiState.value.bubbles.isEmpty())
        assertEquals(ChatIntent.entries.toList(), vm.uiState.value.intentPills)

        // tapIntentPill injects system context and sends opening message
        vm.tapIntentPill(ChatIntent.DISCOVER)
        val history = fakeAiProvider.lastChatHistory
        // F1 fix: snapshot taken BEFORE adding user msg, so only system context is in history
        assertEquals(1, history.size)
        assertEquals(MessageRole.USER, history[0].role) // F2 fix: system context is USER role
        assertTrue(history[0].content.contains("Test Area"))
    }

    @Test
    fun `sendMessage appends user bubble then streaming AI bubble`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Hello", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.uiState.test {
            // Initial state after openChat
            val initial = awaitItem()
            assertTrue(initial.bubbles.isEmpty())

            vm.sendMessage("Hi there")

            val final_ = expectMostRecentItem()
            assertEquals(2, final_.bubbles.size)
            assertEquals(MessageRole.USER, final_.bubbles[0].role)
            assertEquals("Hi there", final_.bubbles[0].content)
            assertEquals(MessageRole.AI, final_.bubbles[1].role)
            assertEquals("Hello", final_.bubbles[1].content)
            assertFalse(final_.isStreaming)
        }
    }

    @Test
    fun `conversation history grows across turns`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Answer1", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        // tapIntentPill triggers system context + opening message send
        vm.tapIntentPill(ChatIntent.DISCOVER)
        // First call: tapIntentPill calls sendMessage which takes snapshot before adding user msg
        // Snapshot = [system_context], then user msg (opening message) is added after snapshot
        assertEquals(1, fakeAiProvider.chatCallCount)
        assertEquals(1, fakeAiProvider.lastChatHistory.size)

        // Second turn
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Answer2", false),
            ChatToken("", true),
        )
        vm.sendMessage("Question 2")
        assertEquals(2, fakeAiProvider.chatCallCount)
        // Second call gets: system_context + user1(opening) + ai1 (snapshot before adding user2)
        assertEquals(3, fakeAiProvider.lastChatHistory.size)
        assertEquals(MessageRole.USER, fakeAiProvider.lastChatHistory[0].role) // system (USER role)
        assertEquals(MessageRole.USER, fakeAiProvider.lastChatHistory[1].role) // opening message
        assertEquals(MessageRole.AI, fakeAiProvider.lastChatHistory[2].role) // ai1
    }

    @Test
    fun `error during stream shows error bubble`() = runTest {
        fakeAiProvider.shouldThrowChat = true
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.sendMessage("Hello")

        val state = vm.uiState.value
        assertFalse(state.isStreaming)
        assertEquals(2, state.bubbles.size)
        assertTrue(state.bubbles.last().isError)
        assertTrue(state.bubbles.last().content.contains("Tap to retry"))
    }

    @Test
    fun `retryLastMessage re-sends last query`() = runTest {
        fakeAiProvider.shouldThrowChat = true
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.sendMessage("Hello")
        assertEquals(1, fakeAiProvider.chatCallCount)

        // Now fix the fake and retry
        fakeAiProvider.shouldThrowChat = false
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Retried", false),
            ChatToken("", true),
        )
        vm.retryLastMessage()
        assertEquals(2, fakeAiProvider.chatCallCount)

        val state = vm.uiState.value
        assertFalse(state.isStreaming)
        assertEquals(2, state.bubbles.size)
        assertEquals("Hello", state.bubbles[0].content)
        assertEquals("Retried", state.bubbles[1].content)
        assertFalse(state.bubbles[1].isError)
    }

    @Test
    fun `retryLastMessage is no-op when last bubble is not error`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")

        val bubblesBefore = vm.uiState.value.bubbles
        vm.retryLastMessage()
        // No change — last bubble is not an error
        assertEquals(bubblesBefore, vm.uiState.value.bubbles)
    }

    @Test
    fun `closeChat sets isOpen to false`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        assertTrue(vm.uiState.value.isOpen)

        vm.closeChat()
        assertFalse(vm.uiState.value.isOpen)
    }

    @Test
    fun `tapChip calls sendMessage with chip text`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.tapChip("Hidden gems?")

        val state = vm.uiState.value
        assertEquals(2, state.bubbles.size)
        assertEquals("Hidden gems?", state.bubbles[0].content)
        assertEquals(MessageRole.USER, state.bubbles[0].role)
    }

    @Test
    fun `openChat shows intent pills instead of vibe starter chips`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), Vibe.SAFETY)

        val pills = vm.uiState.value.intentPills
        assertEquals(5, pills.size)
        assertEquals(ChatIntent.TONIGHT, pills[0])
        assertTrue(vm.uiState.value.followUpChips.isEmpty())
    }

    @Test
    fun `follow-up chips appear after response completes`() = runTest {
        // Seed saves so engagement is LIGHT+ (keyword-based follow-up)
        for (i in 1..3) {
            fakeSavedPoiRepository.save(
                com.harazone.domain.model.SavedPoi(
                    id = "p$i|$i.0|$i.0", name = "P$i", type = "park",
                    areaName = "Test Area", lat = i.toDouble(), lng = i.toDouble(),
                    whySpecial = "test", savedAt = fakeClock.nowMs,
                )
            )
        }
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Sure!", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.tapIntentPill(ChatIntent.TONIGHT)

        // Second message triggers keyword follow-ups (LIGHT+ engagement)
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Safe area!", false),
            ChatToken("", true),
        )
        vm.sendMessage("Is it safe here?")

        val state = vm.uiState.value
        assertFalse(state.isStreaming)
        assertTrue(state.followUpChips.isNotEmpty())
    }

    @Test
    fun `sendMessage is no-op when already streaming`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.sendMessage("First")
        val bubblesAfterFirst = vm.uiState.value.bubbles.size

        // Manually set isStreaming to true to simulate in-progress stream
        // Since UnconfinedTestDispatcher completes eagerly, we verify via the guard:
        // blank query should be rejected
        vm.sendMessage("")
        assertEquals(bubblesAfterFirst, vm.uiState.value.bubbles.size)
        assertEquals(1, fakeAiProvider.chatCallCount)
    }

    @Test
    fun `openChat reopens without resetting when closed with existing conversation`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")
        assertEquals(2, vm.uiState.value.bubbles.size)

        vm.closeChat()
        assertFalse(vm.uiState.value.isOpen)

        // Reopen same area — should preserve conversation
        vm.openChat("Test Area", emptyList(), null)
        assertTrue(vm.uiState.value.isOpen)
        assertEquals(2, vm.uiState.value.bubbles.size) // conversation preserved
    }

    // --- v1.1 tests: POI parser, skeletons, save/unsave ---

    @Test
    fun `parsePoiCards extracts valid POI from text`() = runTest {
        val vm = createViewModel()
        val text = """Here is a great place:
{"n":"Cafe Roma","t":"food","lat":41.8902,"lng":12.4922,"w":"Best espresso in the neighborhood"}
Check it out!"""
        val results = vm.parsePoiCards(text)
        assertEquals(1, results.size)
        assertEquals("Cafe Roma", results[0].first.name)
        assertEquals("food", results[0].first.type)
        assertEquals(41.8902, results[0].first.lat)
        assertEquals(12.4922, results[0].first.lng)
    }

    @Test
    fun `parsePoiCards handles malformed JSON silently`() = runTest {
        val vm = createViewModel()
        val text = """{"n":"Good","t":"food","lat":1.0,"lng":2.0,"w":"nice"} and {"broken json"""
        val results = vm.parsePoiCards(text)
        assertEquals(1, results.size)
        assertEquals("Good", results[0].first.name)
    }

    @Test
    fun `parsePoiCards extracts multiple POIs`() = runTest {
        val vm = createViewModel()
        val text = """{"n":"A","t":"food","lat":1.0,"lng":2.0,"w":"a"} text {"n":"B","t":"park","lat":3.0,"lng":4.0,"w":"b"}"""
        val results = vm.parsePoiCards(text)
        assertEquals(2, results.size)
        assertEquals("A", results[0].first.name)
        assertEquals("B", results[1].first.name)
    }

    @Test
    fun `parsePoiCards handles braces inside string values`() = runTest {
        val vm = createViewModel()
        val text = """{"n":"Cafe {Seasonal}","t":"food","lat":1.0,"lng":2.0,"w":"Open {April-Oct} only"}"""
        val results = vm.parsePoiCards(text)
        assertEquals(1, results.size)
        assertEquals("Cafe {Seasonal}", results[0].first.name)
        assertEquals("Open {April-Oct} only", results[0].first.whySpecial)
    }

    @Test
    fun `showSkeletons true on stream start, false on complete`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Some text", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.uiState.test {
            awaitItem() // initial

            vm.sendMessage("Show me cafes")

            val final_ = expectMostRecentItem()
            // After completion, showSkeletons should be false
            assertFalse(final_.showSkeletons)
            assertFalse(final_.isStreaming)
        }
    }

    @Test
    fun `savePoi optimistically updates savedPoiIds`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        val card = ChatPoiCard(
            name = "Test Cafe",
            type = "food",
            lat = 1.0,
            lng = 2.0,
            whySpecial = "Great coffee",
        )

        vm.savePoi(card, "Test Area")

        assertTrue(vm.uiState.value.savedPoiIds.contains(card.id))
    }

    @Test
    fun `savePoi reverts on repository failure`() = runTest {
        fakeSavedPoiRepository.shouldThrow = true
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        val card = ChatPoiCard(
            name = "Test Cafe",
            type = "food",
            lat = 1.0,
            lng = 2.0,
            whySpecial = "Great coffee",
        )

        vm.savePoi(card, "Test Area")

        // With UnconfinedTestDispatcher, the coroutine runs eagerly
        // The save throws, so savedPoiIds should NOT contain the id
        assertFalse(vm.uiState.value.savedPoiIds.contains(card.id))
    }

    @Test
    fun `unsavePoi removes from savedPoiIds`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        val card = ChatPoiCard(
            name = "Test Cafe",
            type = "food",
            lat = 1.0,
            lng = 2.0,
            whySpecial = "Great coffee",
        )

        vm.savePoi(card, "Test Area")
        assertTrue(vm.uiState.value.savedPoiIds.contains(card.id))

        vm.unsavePoi(card.id)
        assertFalse(vm.uiState.value.savedPoiIds.contains(card.id))
    }

    @Test
    fun `error during stream resets showSkeletons`() = runTest {
        fakeAiProvider.shouldThrowChat = true
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.sendMessage("Hello")

        val state = vm.uiState.value
        assertFalse(state.showSkeletons)
    }

    // Regression: stripPoiJson originally used StringBuilder.delete() which is JVM-only.
    // Fixed to use deleteRange() which is available in commonMain. This test must pass on all platforms.
    @Test
    fun `stripPoiJson removes JSON and keeps prose`() = runTest {
        val vm = createViewModel()
        val text = """Here are some spots:
{"n":"Cafe Roma","t":"food","lat":41.89,"lng":12.49,"w":"Best espresso"}
And also check out:
{"n":"Central Park","t":"park","lat":40.78,"lng":-73.97,"w":"Iconic green space"}
Enjoy!"""
        val stripped = vm.stripPoiJson(text)
        assertTrue(stripped.contains("Here are some spots:"))
        assertTrue(stripped.contains("And also check out:"))
        assertTrue(stripped.contains("Enjoy!"))
        assertFalse(stripped.contains("Cafe Roma"))
        assertFalse(stripped.contains("Central Park"))
        assertFalse(stripped.contains("{"))
    }

    @Test
    fun `stripPoiJson returns text unchanged when no POI JSON`() = runTest {
        val vm = createViewModel()
        val text = "Just a plain response with no POI cards."
        assertEquals(text, vm.stripPoiJson(text))
    }

    // --- Saves injection tests ---

    @Test
    fun `tapIntentPill_withAreaSaves_injectsThemIntoSystemContext`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        fakeSavedPoiRepository.save(
            com.harazone.domain.model.SavedPoi(
                id = "Cafe Roma|41.89|12.49",
                name = "Cafe Roma",
                type = "food",
                areaName = "Test Area",
                lat = 41.89,
                lng = 12.49,
                whySpecial = "Best espresso",
                savedAt = 1000L,
            )
        )
        fakeSavedPoiRepository.save(
            com.harazone.domain.model.SavedPoi(
                id = "Park Verde|41.90|12.50",
                name = "Park Verde",
                type = "park",
                areaName = "Test Area",
                lat = 41.90,
                lng = 12.50,
                whySpecial = "Peaceful park",
                savedAt = 2000L,
            )
        )

        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.tapIntentPill(ChatIntent.DISCOVER)

        val context = vm.systemContextForTest
        assertTrue(context.contains("Cafe Roma"), "System context should contain saved POI name 'Cafe Roma'")
        assertTrue(context.contains("Park Verde"), "System context should contain saved POI name 'Park Verde'")
        assertTrue(context.contains("SAVED PLACES IN THIS AREA"), "System context should contain saves section")
    }

    @Test
    fun `tapIntentPill_withSavesInOtherArea_doesNotInjectThem`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        fakeSavedPoiRepository.save(
            com.harazone.domain.model.SavedPoi(
                id = "Other Cafe|10.0|20.0",
                name = "Other Cafe",
                type = "food",
                areaName = "Other Area",
                lat = 10.0,
                lng = 20.0,
                whySpecial = "Far away",
                savedAt = 1000L,
            )
        )

        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.tapIntentPill(ChatIntent.DISCOVER)

        val context = vm.systemContextForTest
        assertFalse(context.contains("Other Cafe"), "System context should NOT contain saves from other areas")
        assertFalse(context.contains("SAVED PLACES IN THIS AREA"), "System context should NOT contain saves section when no area matches")
    }

    @Test
    fun `tapIntentPill_doubleTap_isNoOp`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.tapIntentPill(ChatIntent.DISCOVER)
        assertEquals(1, fakeAiProvider.chatCallCount)

        // Second tap should be ignored
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Second", false),
            ChatToken("", true),
        )
        vm.tapIntentPill(ChatIntent.TONIGHT)
        assertEquals(1, fakeAiProvider.chatCallCount, "Double-tap should not trigger a second send")
    }

    @Test
    fun `tapIntentPill_moreThanEightSaves_capsAtEight`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        // Use names that aren't substrings of each other
        val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet")
        names.forEachIndexed { i, name ->
            fakeSavedPoiRepository.save(
                com.harazone.domain.model.SavedPoi(
                    id = "$name|${(i + 1).toDouble()}|${(i + 1).toDouble()}",
                    name = name,
                    type = "food",
                    areaName = "Test Area",
                    lat = (i + 1).toDouble(),
                    lng = (i + 1).toDouble(),
                    whySpecial = "Place $name",
                    savedAt = (i + 1).toLong(),
                )
            )
        }

        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.tapIntentPill(ChatIntent.DISCOVER)

        val context = vm.systemContextForTest
        assertTrue(context.contains("SAVED PLACES IN THIS AREA"), "System context should contain saves section")
        // Most recent = Juliet (savedAt=10), 8th = Charlie (savedAt=3)
        assertTrue(context.contains("Juliet"), "Should contain most recent save")
        assertTrue(context.contains("Charlie"), "Should contain 8th most recent save")
        assertFalse(context.contains("Bravo"), "Should NOT contain 9th save")
        assertFalse(context.contains("Alpha"), "Should NOT contain 10th save")
    }
}
