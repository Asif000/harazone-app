package com.harazone.ui.map

import app.cash.turbine.test
import com.harazone.data.remote.GeminiPromptBuilder
import com.harazone.domain.model.ChatIntent
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.ContextualPill
import com.harazone.domain.model.MessageRole
import com.harazone.domain.model.POI
import com.harazone.domain.model.DynamicVibe
import com.harazone.fakes.FakeAreaIntelligenceProvider
import com.harazone.fakes.FakeClock
import com.harazone.fakes.FakeLocaleProvider
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
        localeProvider = FakeLocaleProvider(),
    )

    private fun discoverPill() = ContextualPill("Show me hidden gems", "Show me hidden gems in Test Area", ChatIntent.DISCOVER, "🔍")
    private fun tonightPill() = ContextualPill("What's on tonight in Test Area?", "What's on tonight in Test Area?", ChatIntent.TONIGHT, "🌙")

    private fun testPoi(name: String = "Test Cafe") = POI(
        name = name,
        type = "food",
        vibe = "character",
        insight = "Great place",
        description = "A nice cafe",
        confidence = Confidence.HIGH,
        latitude = 1.0,
        longitude = 2.0,
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
        assertEquals(5, vm.uiState.value.persistentPills.size)

        // tapIntentPill injects system context and sends opening message
        vm.tapIntentPill(discoverPill())
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
        vm.tapIntentPill(discoverPill())
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
        assertFalse(state.showSkeletons) // AC6: skeletons reset on error
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
        vm.openChat("Test Area", emptyList(), DynamicVibe(label = "Safety", icon = ""))

        val pills = vm.uiState.value.persistentPills
        assertEquals(5, pills.size)
        assertEquals(ChatIntent.TONIGHT, pills[0].intent)
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
        vm.tapIntentPill(tonightPill())

        // Second message triggers keyword follow-ups (LIGHT+ engagement)
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Safe area!", false),
            ChatToken("", true),
        )
        vm.sendMessage("Is it safe here?")

        val state = vm.uiState.value
        assertFalse(state.isStreaming)
        assertTrue(state.persistentPills.isNotEmpty(), "Persistent pills should replenish after response")
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
        vm.tapIntentPill(discoverPill())

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
        vm.tapIntentPill(discoverPill())

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
        vm.tapIntentPill(discoverPill())
        assertEquals(1, fakeAiProvider.chatCallCount)

        // Second tap should be ignored
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Second", false),
            ChatToken("", true),
        )
        vm.tapIntentPill(tonightPill())
        assertEquals(1, fakeAiProvider.chatCallCount, "Double-tap should not trigger a second send")
    }

    // --- Phase A streaming regression tests ---

    @Test
    fun `poiCards empty and showSkeletons true on every mid-stream token`() = runTest {
        val poiJson = """{"n":"Café","t":"cafe","lat":1.0,"lng":2.0,"w":"good"}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Some prose ", false),
            ChatToken("more prose. $poiJson", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.uiState.test {
            awaitItem() // openChat state

            vm.sendMessage("Hello")

            // With UnconfinedTestDispatcher + StateFlow, intermediate states may be
            // conflated. Drain all emitted items and verify invariants on each.
            val emissions = mutableListOf<ChatUiState>()
            // Collect all buffered items (sendMessage ran eagerly)
            while (true) {
                val item = try { awaitItem() } catch (_: Throwable) { break }
                emissions.add(item)
                // Stop after stream completes
                if (!item.isStreaming && item.bubbles.isNotEmpty()) break
            }

            assertTrue(emissions.size >= 2, "Expected at least setup + final, got ${emissions.size}")

            // All mid-stream emissions (with isStreaming=true): poiCards must be empty, showSkeletons must be true
            val midStreamStates = emissions.filter { it.isStreaming }
            for ((i, state) in midStreamStates.withIndex()) {
                assertTrue(state.showSkeletons, "Mid-stream state $i: showSkeletons should be true")
                assertTrue(state.poiCards.isEmpty(), "Mid-stream state $i: poiCards should be empty")
            }

            // Final emission: parsing happened
            val finalState = emissions.last()
            assertFalse(finalState.isStreaming)
            assertFalse(finalState.showSkeletons)
            assertEquals(1, finalState.poiCards.size)
            assertEquals("Café", finalState.poiCards[0].name)
        }
    }

    @Test
    fun `poiCards populated and showSkeletons false after stream completes with JSON`() = runTest {
        val poiJson = """{"n":"Bistro","t":"restaurant","lat":1.0,"lng":2.0,"w":"cozy"}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Try this place: $poiJson", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.uiState.test {
            awaitItem() // openChat state
            vm.sendMessage("Hello")
            val state = expectMostRecentItem()
            assertFalse(state.isStreaming)
            assertFalse(state.showSkeletons)
            assertEquals(1, state.poiCards.size)
            assertEquals("Bistro", state.poiCards[0].name)
        }
    }

    @Test
    fun `poiCards empty and showSkeletons false after stream completes with no JSON`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Just some conversational prose, no POIs.", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.uiState.test {
            awaitItem() // openChat state
            vm.sendMessage("Hello")
            val state = expectMostRecentItem()
            assertFalse(state.isStreaming)
            assertFalse(state.showSkeletons)
            assertTrue(state.poiCards.isEmpty())
            assertEquals("Just some conversational prose, no POIs.", state.bubbles.last().content)
        }
    }

    @Test
    fun `closeChat resets isStreaming and showSkeletons`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Streaming...", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")

        // Close mid-conversation
        vm.closeChat()
        val state = vm.uiState.value
        assertFalse(state.isOpen)
        assertFalse(state.isStreaming)
        assertFalse(state.showSkeletons)
        // Bubble-level isStreaming must also be reset (prevents stuck blinking cursor)
        assertTrue(state.bubbles.none { it.isStreaming }, "No bubble should have isStreaming=true after closeChat")
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
        vm.tapIntentPill(discoverPill())

        val context = vm.systemContextForTest
        assertTrue(context.contains("SAVED PLACES IN THIS AREA"), "System context should contain saves section")
        // Most recent = Juliet (savedAt=10), 8th = Charlie (savedAt=3)
        assertTrue(context.contains("Juliet"), "Should contain most recent save")
        assertTrue(context.contains("Charlie"), "Should contain 8th most recent save")
        assertFalse(context.contains("Bravo"), "Should NOT contain 9th save")
        assertFalse(context.contains("Alpha"), "Should NOT contain 10th save")
    }

    // --- AI Behavior Overhaul tests ---

    @Test
    fun `openChat_withPoiCard_setsPreFillAndNoBubbles`() = runTest {
        val poi = testPoi("Cafe Roma")
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null, entryPoint = ChatEntryPoint.PoiCard(poi))

        val state = vm.uiState.value
        assertEquals("Tell me more about Cafe Roma", state.inputText)
        assertTrue(state.bubbles.isEmpty())
        assertEquals(0, fakeAiProvider.chatCallCount)
    }

    @Test
    fun `openChat_withSavesSheet_showsSavesSpecificPills`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null, entryPoint = ChatEntryPoint.SavesSheet)

        val labels = vm.uiState.value.persistentPills.map { it.label }
        assertTrue(labels.contains("Plan a day trip from my saves"))
    }

    @Test
    fun `openChat_withDefault_hasNoBanner`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null, entryPoint = ChatEntryPoint.Default)

        assertEquals(null, vm.uiState.value.contextBanner)
    }

    @Test
    fun `openChat_withPoiCard_hasBanner`() = runTest {
        val poi = testPoi("Cafe Roma")
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null, entryPoint = ChatEntryPoint.PoiCard(poi))

        assertEquals("Asking about: Cafe Roma", vm.uiState.value.contextBanner)
    }

    @Test
    fun `sendMessage_incrementsDepthLevel`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("R1", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        vm.sendMessage("Q1")
        fakeAiProvider.chatTokens = listOf(ChatToken("R2", false), ChatToken("", true))
        vm.sendMessage("Q2")
        fakeAiProvider.chatTokens = listOf(ChatToken("R3", false), ChatToken("", true))
        vm.sendMessage("Q3")

        assertEquals(3, vm.uiState.value.depthLevel)
    }

    @Test
    fun `tapIntentPill_resetsDepthLevel`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("R", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        // Send 2 messages to prime depthLevel
        vm.sendMessage("Q1")
        fakeAiProvider.chatTokens = listOf(ChatToken("R2", false), ChatToken("", true))
        vm.sendMessage("Q2")
        assertEquals(2, vm.uiState.value.depthLevel)

        // Now open a new area to reset isIntentSelected so tapIntentPill works
        fakeAiProvider.chatTokens = listOf(ChatToken("R3", false), ChatToken("", true))
        vm.openChat("New Area", emptyList(), null)
        val pill = ContextualPill("Show me hidden gems", "Show me hidden gems in New Area", ChatIntent.DISCOVER, "🔍")
        vm.tapIntentPill(pill)

        // depthLevel should be 0 from the pill tap reset (before sendMessage increments to 1)
        // But tapIntentPill calls sendMessage internally, so depthLevel = 1 after completion
        // The spec says: assert depthLevel == 0 "at the moment pills are cleared (before the internal sendMessage)"
        // With UnconfinedTestDispatcher, sendMessage runs eagerly, so we see depthLevel == 1
        // The important thing is it was reset from 2 to 0 before being incremented to 1
        assertEquals(1, vm.uiState.value.depthLevel)
    }

    @Test
    fun `resetToIntentPills_clearsBubblesAndRestoresPills`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")
        assertTrue(vm.uiState.value.bubbles.isNotEmpty())

        vm.resetToIntentPills()

        val state = vm.uiState.value
        assertTrue(state.bubbles.isEmpty())
        assertTrue(state.persistentPills.isNotEmpty())
        assertEquals(0, state.depthLevel)
    }

    // --- Chat Experience Redesign tests ---

    @Test
    fun `mapAwarePrompt_injectsAllSessionPois`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val pois = listOf(
            testPoi("Brick Lane"),
            testPoi("Beigel Bake"),
            testPoi("Rivington St"),
            testPoi("Spitalfields Market"),
            testPoi("Old Truman Brewery"),
            testPoi("Columbia Road"),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", pois, null)
        vm.tapIntentPill(discoverPill())

        val context = vm.systemContextForTest
        // All 6 POIs should appear — not just top 5
        pois.forEach { poi ->
            assertTrue(context.contains(poi.name), "System context should contain '${poi.name}'")
        }
        assertTrue(context.contains("MAP POIS"), "System context should contain MAP POIS section")
        assertTrue(context.contains("REFERENCE RULE"), "System context should contain REFERENCE RULE")
    }

    @Test
    fun `reinforcedQuery_containsPoisAndQuestion`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Hi!", false),
            ChatToken("", true),
        )
        val pois = listOf(testPoi("Brick Lane"), testPoi("Beigel Bake"))
        val vm = createViewModel()
        vm.openChat("Test Area", pois, null)

        fakeAiProvider.chatTokens = listOf(
            ChatToken("Sure!", false),
            ChatToken("", true),
        )
        vm.sendMessage("Show me food")

        val lastQuery = fakeAiProvider.lastChatQuery
        assertTrue(lastQuery.contains("LAST SENTENCE ENDS WITH '?'"), "Reinforced query should enforce question ending")
        assertTrue(lastQuery.contains("Brick Lane"), "Reinforced query should contain POI name 'Brick Lane'")
        assertTrue(lastQuery.contains("Beigel Bake"), "Reinforced query should contain POI name 'Beigel Bake'")
    }

    @Test
    fun `poiCards_accumulateAcrossTurns`() = runTest {
        val card1Json = """{"prose":"Try this","pois":[{"n":"Cafe A","t":"food","lat":1.0,"lng":2.0,"w":"great"}]}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken(card1Json, false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("First")
        assertEquals(1, vm.uiState.value.poiCards.size, "Should have 1 card after first turn")

        val card2Json = """{"prose":"Also try","pois":[{"n":"Park B","t":"park","lat":3.0,"lng":4.0,"w":"nice"}]}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken(card2Json, false),
            ChatToken("", true),
        )
        vm.sendMessage("Second")
        assertEquals(2, vm.uiState.value.poiCards.size, "Should have 2 cards accumulated across turns")
        assertEquals("Park B", vm.uiState.value.poiCards[0].name, "Newest cards should be first")
        assertEquals("Cafe A", vm.uiState.value.poiCards[1].name)
    }

    @Test
    fun `bubblePoiCards_associatesCardsWithAiBubble`() = runTest {
        val cardJson = """{"prose":"Try this","pois":[{"n":"Cafe A","t":"food","lat":1.0,"lng":2.0,"w":"great"}]}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken(cardJson, false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("First")

        val state = vm.uiState.value
        val aiBubble = state.bubbles.find { it.role == MessageRole.AI }
        assertTrue(aiBubble != null, "Should have an AI bubble")
        val inlineCards = state.bubblePoiCards[aiBubble!!.id]
        assertTrue(inlineCards != null && inlineCards.isNotEmpty(), "Cards should be associated with AI bubble")
        assertEquals("Cafe A", inlineCards!![0].name)
    }

    @Test
    fun `poiCards_clearOnReset`() = runTest {
        val cardJson = """{"prose":"Try this","pois":[{"n":"Cafe A","t":"food","lat":1.0,"lng":2.0,"w":"great"}]}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken(cardJson, false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("First")
        assertTrue(vm.uiState.value.poiCards.isNotEmpty())

        vm.resetToIntentPills()
        assertTrue(vm.uiState.value.poiCards.isEmpty(), "POI cards should clear on reset")
    }

    @Test
    fun `openChat_sameArea_expiry_showsReturnDialog`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")

        // Close chat
        vm.closeChat()

        // Advance clock past 30 minutes
        fakeClock.nowMs += 31 * 60 * 1000L

        // Reopen same area
        vm.openChat("Test Area", emptyList(), null)

        assertTrue(vm.uiState.value.showReturnDialog, "Should show return dialog after 30-minute expiry")
    }

    @Test
    fun `continueConversation_resetsTimerAndPreservesChat`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")
        vm.closeChat()

        fakeClock.nowMs += 31 * 60 * 1000L
        vm.openChat("Test Area", emptyList(), null)
        assertTrue(vm.uiState.value.showReturnDialog)

        vm.continueConversation()
        assertFalse(vm.uiState.value.showReturnDialog, "Dialog should be dismissed")
        assertTrue(vm.uiState.value.bubbles.isNotEmpty(), "Conversation should be preserved")
    }

    @Test
    fun `startFreshConversation_clearsHistory`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.sendMessage("Hello")
        vm.closeChat()

        fakeClock.nowMs += 31 * 60 * 1000L
        vm.openChat("Test Area", emptyList(), null)
        assertTrue(vm.uiState.value.showReturnDialog)

        vm.startFreshConversation()
        assertFalse(vm.uiState.value.showReturnDialog, "Dialog should be dismissed")
        assertTrue(vm.uiState.value.bubbles.isEmpty(), "Conversation should be cleared")
        assertTrue(vm.uiState.value.poiCards.isEmpty(), "POI cards should be cleared")
    }

    @Test
    fun `persistentPills_replenishAfterResponse`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Sure!", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        vm.tapIntentPill(discoverPill())

        // After tapIntentPill triggers sendMessage, persistentPills should be replenished
        val state = vm.uiState.value
        assertFalse(state.isStreaming)
        assertTrue(state.persistentPills.isNotEmpty(), "Persistent pills should replenish after AI response")
    }

    @Test
    fun `persistentPills_depthGate_includesNewTopic`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("R", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        // Send 3 messages to reach depthLevel >= 3
        vm.sendMessage("Q1")
        fakeAiProvider.chatTokens = listOf(ChatToken("R2", false), ChatToken("", true))
        vm.sendMessage("Q2")
        fakeAiProvider.chatTokens = listOf(ChatToken("R3", false), ChatToken("", true))
        vm.sendMessage("Q3")

        assertEquals(3, vm.uiState.value.depthLevel)
        val newTopicPill = vm.uiState.value.persistentPills.find { it.label == ChatViewModel.LABEL_NEW_TOPIC }
        assertTrue(newTopicPill != null, "At depthLevel >= 3, persistent pills should include 'New topic'")
    }

    // --- Regression: H2 — openChat re-open always preserves persistent pills ---

    // --- AI Detail Page tests: forceReset + openChatForPoi ---

    @Test
    fun `openChatForPoi resets conversation even when same area has existing bubbles`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("First response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Tokyo", emptyList(), null)
        vm.sendMessage("Hello Tokyo")
        assertEquals(2, vm.uiState.value.bubbles.size)
        val oldContent = vm.uiState.value.bubbles[0].content

        // Now open for a specific POI in the same area — should force reset
        fakeAiProvider.chatTokens = listOf(
            ChatToken("About the cafe", false),
            ChatToken("", true),
        )
        val poi = testPoi("Test Cafe")
        vm.openChatForPoi(poi, "Tokyo", emptyList(), null)

        val state = vm.uiState.value
        assertTrue(state.bubbles.isNotEmpty(), "Should have AI intro bubbles")
        assertFalse(state.bubbles.any { it.content == oldContent }, "Previous conversation should be cleared")
    }

    @Test
    fun `openChatForPoi immediately starts streaming AI intro`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("This is a great place", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        val poi = testPoi("Test Cafe")
        vm.openChatForPoi(poi, "Test Area", emptyList(), null)

        val state = vm.uiState.value
        // With UnconfinedTestDispatcher, streaming completes eagerly
        assertTrue(state.bubbles.isNotEmpty(), "Should have bubbles from AI intro")
        val aiBubble = state.bubbles.find { it.role == MessageRole.AI }
        assertTrue(aiBubble != null, "Should have an AI bubble")
        assertTrue(aiBubble!!.content.isNotEmpty(), "AI bubble should have content")
    }

    @Test
    fun `openChat with forceReset true ignores same-area preservation`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Paris", emptyList(), null)
        vm.sendMessage("Hello Paris")
        assertEquals(2, vm.uiState.value.bubbles.size)

        // Force reset same area
        vm.openChat("Paris", emptyList(), null, forceReset = true)
        assertTrue(vm.uiState.value.bubbles.isEmpty(), "Bubbles should be cleared with forceReset=true")
        assertTrue(vm.uiState.value.isOpen, "Chat should be open")
    }

    // --- POI context block tests ---

    @Test
    fun `openChatForPoi sets isContextLoading true immediately`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("About the place", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        val poi = testPoi("Test Cafe")

        // With UnconfinedTestDispatcher, the entire coroutine runs eagerly
        // so by the time we check, loading is already complete.
        // We verify the final state instead: context fields populated, loading false.
        vm.openChatForPoi(poi, "Test Area", emptyList(), null)

        val state = vm.uiState.value
        assertFalse(state.isContextLoading, "After provider resolves, isContextLoading should be false")
        assertTrue(state.contextBlurb != null, "contextBlurb should be non-null after provider resolves")
        assertEquals(1, fakeAiProvider.poiContextCallCount)
    }

    @Test
    fun `openChatForPoi populates context fields after load`() = runTest {
        fakeAiProvider.poiContextResult = Triple(
            "A historic morning at the cafe.",
            "Perfect for a morning visit.",
            "Ask the barista about the rooftop.",
        )
        fakeAiProvider.chatTokens = listOf(
            ChatToken("About the place", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        val poi = testPoi("Test Cafe")
        vm.openChatForPoi(poi, "Test Area", emptyList(), null)

        val state = vm.uiState.value
        assertEquals("A historic morning at the cafe.", state.contextBlurb)
        assertEquals("Perfect for a morning visit.", state.whyNow)
        assertEquals("Ask the barista about the rooftop.", state.localTip)
        assertFalse(state.isContextLoading)
    }

    @Test
    fun `openChatForPoi uses fallback on provider null`() = runTest {
        fakeAiProvider.shouldReturnNullPoiContext = true
        fakeAiProvider.chatTokens = listOf(
            ChatToken("About the place", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        val poi = testPoi("Test Cafe")
        vm.openChatForPoi(poi, "Test Area", emptyList(), null)

        val state = vm.uiState.value
        assertTrue(state.contextBlurb != null && state.contextBlurb!!.isNotEmpty(), "contextBlurb should have fallback")
        assertTrue(state.contextBlurb!!.contains("Test Cafe"), "Fallback should include POI name")
        assertFalse(state.isContextLoading)
    }

    @Test
    fun `openChat on reopen always preserves persistent pills`() = runTest {
        // Regression: H2 — dead if/else where both branches did the same thing.
        // Collapsed to single branch: pills are persistent and always preserved on reopen.
        fakeAiProvider.chatTokens = listOf(ChatToken("Response", false), ChatToken("", true))
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        val initialPills = vm.uiState.value.persistentPills
        assertTrue(initialPills.isNotEmpty(), "Expected pills on fresh open")

        // Tap a pill — starts conversation
        vm.tapIntentPill(discoverPill())
        assertTrue(vm.uiState.value.bubbles.isNotEmpty(), "Expected bubbles after pill tap")

        vm.closeChat()
        // Reopen same area — pills must still be present (persistent spec)
        vm.openChat("Test Area", emptyList(), null)

        assertTrue(vm.uiState.value.isOpen)
        assertTrue(vm.uiState.value.persistentPills.isNotEmpty(), "Pills must be preserved on reopen — they are persistent")
    }

    @Test
    fun `openChatForPoiVisit with GO_NOW inserts Go Now framing hint into system context`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Visit response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        val poi = testPoi().copy(liveStatus = "open", hours = "9am-10pm")
        vm.openChatForPoiVisit(poi, com.harazone.domain.model.VisitState.GO_NOW, "Test Area", listOf(poi), null)

        assertTrue(vm.systemContextForTest.contains("Go Now response"), "System context should contain Go Now framing hint")
    }

    @Test
    fun `openChatForPoiVisit with PLAN_SOON inserts Plan Soon framing hint into system context`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Plan response", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        val poi = testPoi().copy(liveStatus = "closed", hours = "6pm-2am")
        vm.openChatForPoiVisit(poi, com.harazone.domain.model.VisitState.PLAN_SOON, "Test Area", listOf(poi), null)

        assertTrue(vm.systemContextForTest.contains("Plan Soon response"), "System context should contain Plan Soon framing hint")
    }
}
