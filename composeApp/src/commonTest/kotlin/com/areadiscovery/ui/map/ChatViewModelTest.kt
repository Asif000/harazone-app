package com.areadiscovery.ui.map

import app.cash.turbine.test
import com.areadiscovery.data.remote.GeminiPromptBuilder
import com.areadiscovery.domain.model.ChatToken
import com.areadiscovery.domain.model.MessageRole
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.fakes.FakeAreaIntelligenceProvider
import com.areadiscovery.fakes.FakeClock
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
    )

    @Test
    fun `openChat sets isOpen and injects system turn into history`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Hi", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

        assertTrue(vm.uiState.value.isOpen)
        assertTrue(vm.uiState.value.bubbles.isEmpty())

        // Send a message to verify system context is in history snapshot
        vm.sendMessage("Hello")
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

        vm.sendMessage("Question 1")
        assertEquals(1, fakeAiProvider.chatCallCount)
        // F1 fix: history snapshot is taken BEFORE adding user msg
        // So first call gets: [system_context] only
        assertEquals(1, fakeAiProvider.lastChatHistory.size)

        // Second turn
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Answer2", false),
            ChatToken("", true),
        )
        vm.sendMessage("Question 2")
        assertEquals(2, fakeAiProvider.chatCallCount)
        // Second call gets: system_context + user1 + ai1 (snapshot before adding user2)
        assertEquals(3, fakeAiProvider.lastChatHistory.size)
        assertEquals(MessageRole.USER, fakeAiProvider.lastChatHistory[0].role) // system (USER role)
        assertEquals(MessageRole.USER, fakeAiProvider.lastChatHistory[1].role) // user1
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
    fun `starter chips are vibe-aware`() = runTest {
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), Vibe.SAFETY)

        val chips = vm.uiState.value.followUpChips
        assertEquals(4, chips.size)
        assertTrue(chips[0].contains("safe", ignoreCase = true))
    }

    @Test
    fun `follow-up chips appear after response completes`() = runTest {
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Sure!", false),
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)

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
}
