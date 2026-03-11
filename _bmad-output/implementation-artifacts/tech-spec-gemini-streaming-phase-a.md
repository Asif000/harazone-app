---
title: 'Gemini Streaming Phase A — Chat Prose Display'
slug: 'gemini-streaming-phase-a'
created: '2026-03-10'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Ktor SSE', 'Coroutines/Flow', 'Turbine (tests)']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt'
code_patterns: ['StateFlow + MutableStateFlow', 'Flow.collect token loop', 'Turbine test pattern']
test_patterns: ['runTest + UnconfinedTestDispatcher', 'FakeAreaIntelligenceProvider.chatTokens list', 'awaitItem / expectMostRecentItem']
---

# Tech-Spec: Gemini Streaming Phase A — Chat Prose Display

**Created:** 2026-03-10

## Overview

### Problem Statement

`ChatViewModel.sendMessage()` currently calls `parsePoiCardsIncremental()` on **every** streaming token and writes `poiCards` to UI state mid-stream. This violates two locked decisions from the 2026-03-10 brainstorm (Theme 3):

- Decision #3: POI JSON extraction must happen only after the complete response.
- Decision #4: Pin/card rendering must wait for the completed response.

Additionally `showSkeletons` is tied to `parsedCards.size < 3`, which could prematurely hide skeleton cards if partial JSON is parsed before the stream ends. A secondary bug: `closeChat()` cancels `chatJob` but does not reset `showSkeletons` or `isStreaming`, leaving them permanently `true` if the user dismisses the overlay while a stream is in flight.

Note: The SSE streaming infrastructure is **already fully implemented** in `GeminiAreaIntelligenceProvider.streamChatResponse()` and prose tokens already flow to the UI on every `ChatToken(isComplete=false)`. `stripPoiJson(accumulated)` continues to run on every mid-stream token to scrub complete JSON blocks from visible prose — this is display-only text processing and remains in the mid-stream handler. This spec targets only the mid-stream **state population** violations (poiCards and showSkeletons), plus the cancellation cleanup.

### Solution

In `sendMessage()`: remove `parsePoiCardsIncremental()` and the `poiCards` update from the non-complete token handler; fix `showSkeletons` to stay `true` throughout streaming. In `closeChat()`: reset `isStreaming = false` and `showSkeletons = false` alongside the existing `isOpen = false`.

### Scope

**In Scope:**
- Remove `parsePoiCardsIncremental(accumulated)` from the non-complete token handler in `sendMessage()`
- Remove `poiCards = parsedCards.toList()` from the non-complete token handler
- Change `showSkeletons = parsedCards.size < 3` → `showSkeletons = true` in the non-complete token handler
- Add `isStreaming = false, showSkeletons = false` to `closeChat()` state update
- Add 4 regression tests to `ChatViewModelTest` (covering mid-stream, completion with JSON, completion without JSON, cancellation)

**Out of Scope:**
- Any change to `GeminiAreaIntelligenceProvider` (SSE call already correct)
- Any change to `ChatOverlay` (skeleton/bubble rendering already correct)
- Prompt structure changes, call splitting, or new API calls
- Concurrent `sendMessage()` race on `parsedCards`/`lastParseOffset` — pre-existing issue, not introduced by this change (noted as known limitation)

---

## Context for Development

### Codebase Patterns

- `ChatViewModel` exposes `_uiState: MutableStateFlow<ChatUiState>` — single source of truth
- `sendMessage()` synchronously updates state once (setup: user + AI bubbles, `showSkeletons = true`, `poiCards = emptyList()`), then launches a coroutine collecting `ChatToken` from `aiProvider.streamChatResponse()`
- `ChatToken.isComplete = false` → mid-stream token (prose chunk); `ChatToken.isComplete = true` → stream finished
- `parsedCards` and `lastParseOffset` are instance-level vars, reset at the top of `sendMessage()` — they remain in use in the `isComplete` branch after this change
- Tests use `FakeAreaIntelligenceProvider` where `chatTokens = listOf(...)` sets up the token sequence, `UnconfinedTestDispatcher` runs coroutines eagerly (all tokens process synchronously within `sendMessage()`), and Turbine buffers every `uiState` emission for `awaitItem()` consumption
- `openChat()` initial state: `showSkeletons = false`, `poiCards = emptyList()`, `isStreaming = false`, `isOpen = true`

### Files to Reference

| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | Primary change target — `sendMessage()` non-complete handler and `closeChat()` |
| `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt` | Add 4 regression tests |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt` | Reference only — SSE streaming already correct, no changes |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt` | Reference only — skeleton/bubble rendering already correct, no changes |

### Technical Decisions

1. **Skeleton count stays 3 throughout streaming** — `remainingSkeletons = (3 - chatState.poiCards.size)`. With `poiCards` empty during streaming, renders 3 skeleton cards. After `isComplete`, real cards replace them. No new state flag needed.
2. **`stripPoiJson(accumulated)` stays in mid-stream handler** — display-only. Strips complete `{...}` JSON blocks from visible text. Partial unclosed fragments remain visible briefly on slow networks — acceptable for Phase A (Phase B would split the call to eliminate this).
3. **`parsePoiCardsIncremental` and `parsedCards`/`lastParseOffset` remain in `isComplete` branch** — they are not dead code. Only removed from the non-complete handler.

---

## Implementation Plan

### Tasks

- [x] Task 1a: Remove partial JSON parsing and mid-stream card update from `sendMessage()` non-complete handler
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action: Locate the `else` branch inside `.collect { token -> }` (the non-complete token handler — identifiable as the block containing `accumulated += token.text` that does NOT start with `if (token.isComplete)`). Make exactly 3 changes:
    1. Delete the line calling `parsePoiCardsIncremental(accumulated)`
    2. Delete the line `poiCards = parsedCards.toList(),` from the `s.copy(...)` call
    3. Replace `showSkeletons = parsedCards.size < 3,` with `showSkeletons = true,`
  - Notes: After the change the non-complete handler should look like:
    ```kotlin
    } else {
        accumulated += token.text
        val displayText = stripPoiJson(accumulated)
        val s = _uiState.value
        _uiState.value = s.copy(
            bubbles = s.bubbles.map {
                if (it.id == aiBubbleId) ChatBubble(
                    id = aiBubbleId, role = MessageRole.AI,
                    content = displayText, isStreaming = true,
                ) else it
            },
            showSkeletons = true,
        )
    }
    ```

- [x] Task 1b: Fix `closeChat()` to reset streaming state on cancel
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action: Locate `fun closeChat()`. Change `_uiState.value = _uiState.value.copy(isOpen = false)` to include `isStreaming = false` and `showSkeletons = false`:
    ```kotlin
    fun closeChat() {
        chatJob?.cancel()
        _uiState.value = _uiState.value.copy(isOpen = false, isStreaming = false, showSkeletons = false)
    }
    ```
  - Notes: Without this, `chatJob?.cancel()` cancels the coroutine before `isComplete` fires, leaving `showSkeletons = true` permanently. When the user reopens the same-area chat, the same-area guard in `openChat()` preserves state with `isOpen = true`, surfacing the stale `showSkeletons = true`.

- [x] Task 2: Add regression test — mid-stream state has empty `poiCards`, `showSkeletons = true`, and `isStreaming = true` throughout
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt`
  - Action: Add the following test. Use `awaitItem()` to consume each individual state emission in sequence (not `expectMostRecentItem`) — this is the only way to assert mid-stream invariants. With `UnconfinedTestDispatcher`, all state updates are synchronous and buffered by Turbine before any `awaitItem()` is reached.
    ```kotlin
    @Test
    fun `poiCards empty and showSkeletons true on every mid-stream token`() = runTest {
        val poiJson = """{"id":"p1","name":"Café","type":"cafe","lat":1.0,"lng":2.0,"whySpecial":"good"}"""
        fakeAiProvider.chatTokens = listOf(
            ChatToken("Some prose ", false),
            ChatToken("more prose. $poiJson", false), // second token contains complete JSON
            ChatToken("", true),
        )
        val vm = createViewModel()
        vm.openChat("Test Area", emptyList(), null)
        // openChat: showSkeletons=false, poiCards=empty, isStreaming=false

        vm.uiState.test {
            awaitItem() // openChat state — consume before sendMessage

            vm.sendMessage("Hello")

            // State 1: sendMessage initial setup (user bubble + empty AI bubble added)
            // showSkeletons=true, poiCards=empty, isStreaming=true set here synchronously
            val setupState = awaitItem()
            assertTrue(setupState.isStreaming)
            assertTrue(setupState.showSkeletons)
            assertTrue(setupState.poiCards.isEmpty())

            // State 2: first mid-stream token processed ("Some prose ")
            val midState1 = awaitItem()
            assertTrue(midState1.isStreaming)
            assertTrue(midState1.showSkeletons)
            assertTrue(midState1.poiCards.isEmpty()) // KEY: no cards mid-stream

            // State 3: second mid-stream token processed (contains complete POI JSON)
            val midState2 = awaitItem()
            assertTrue(midState2.isStreaming)
            assertTrue(midState2.showSkeletons)
            assertTrue(midState2.poiCards.isEmpty()) // KEY: JSON not parsed until isComplete

            // State 4: isComplete token — parsing happens now
            val finalState = awaitItem()
            assertFalse(finalState.isStreaming)
            assertFalse(finalState.showSkeletons)
            assertEquals(1, finalState.poiCards.size)
            assertEquals("Café", finalState.poiCards[0].name)
        }
    }
    ```

- [x] Task 3: Add regression test — stream completes with POI JSON (guards isComplete branch)
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt`
  - Action: Add the following test. Must use Turbine `expectMostRecentItem()` rather than reading `uiState.value` directly — the latter races the coroutine even on UnconfinedTestDispatcher.
    ```kotlin
    @Test
    fun `poiCards populated and showSkeletons false after stream completes with JSON`() = runTest {
        val poiJson = """{"id":"p1","name":"Bistro","type":"restaurant","lat":1.0,"lng":2.0,"whySpecial":"cozy"}"""
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
    ```

- [x] Task 4: Add regression test — stream completes with no JSON (AC5)
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt`
  - Action: Add the following test. Verifies `showSkeletons = false` and `poiCards` empty on a prose-only response.
    ```kotlin
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
    ```

### Acceptance Criteria

- [ ] AC1: (Manual) Given a user taps an intent pill, when the Gemini stream starts, then the first prose word appears in the AI bubble within ~1s and a blinking cursor is visible. Verify on real device — not automatable as a ViewModel unit test. Automated proxy: the setup state from Task 2's test asserts `isStreaming = true` on the first emitted state.
- [ ] AC2: Given the AI response is streaming with mid-stream tokens (including those containing complete JSON blocks), then `poiCards` in UI state is empty and no POI cards are rendered in the list.
- [ ] AC3: Given the AI response is streaming, then `showSkeletons` is `true` throughout and 3 skeleton cards remain visible below the prose bubble.
- [ ] AC4: Given the stream completes (`isComplete = true`) with valid POI JSON in the response, then `poiCards` is populated, skeleton cards are replaced by real POI cards simultaneously, and `showSkeletons` is `false`.
- [ ] AC5: Given the stream completes with no valid POI JSON, then `poiCards` is empty, `showSkeletons` is `false`, and the prose bubble shows the complete AI text unmodified.
- [ ] AC6: Given a stream error occurs mid-flight, then the error bubble appears, `isStreaming` is `false`, `showSkeletons` is `false`, and `poiCards` is empty. Verified by the existing test `error during stream shows error bubble` — add `assertFalse(state.showSkeletons)` assertion to that test.
- [ ] AC7: Given the user dismisses the chat overlay while streaming (`closeChat()` called), then after reopening the same area, `showSkeletons` is `false` and `isStreaming` is `false`. Verified by Task 1b; add a manual device test: tap intent pill → immediately tap dismiss → reopen chat → confirm no stuck skeletons.

---

## Additional Context

### Dependencies

- No new libraries or dependencies required
- No changes to `GeminiAreaIntelligenceProvider`, `ChatOverlay`, or any domain models
- `FakeAreaIntelligenceProvider` already supports multi-token sequences — no changes needed

### Testing Strategy

- **Unit tests (Tasks 2–4)**: Run with `./gradlew :composeApp:test` — fast, no device needed
- **Existing error test**: Add `assertFalse(state.showSkeletons)` to the existing test `error during stream shows error bubble` in `ChatViewModelTest`
- **Device verification gate (before commit)**: (1) Tap any intent pill → confirm first token visible within ~1s. (2) Watch entire stream → confirm skeletons visible throughout, all POI cards appear simultaneously at end. (3) Tap intent pill → immediately dismiss overlay → reopen same area → confirm no stuck skeletons. (4) If POI cards never appear after streaming completes — stop, the `isComplete` branch is broken, do not commit.
- **Slow network test (optional)**: Throttle to 3G in device developer options and observe `{` fragment visibility during a slow stream. Acceptable for Phase A; if jarring, flag for Phase B.
- **No `PromptComparisonTest` needed** — this is a ViewModel behaviour fix, not a prompt change

### Notes

- The SSE streaming infrastructure was confirmed already in place. The perceived-latency win (first token <1s) is achievable with the existing Ktor SSE setup.
- **Known pre-existing limitation (out of scope):** Concurrent `sendMessage()` calls (rapid taps or retry logic firing before previous stream ends) share the instance-level `parsedCards` and `lastParseOffset` vars. The existing `chatJob?.cancel()` at the top of `sendMessage()` mitigates but does not fully eliminate the race. Not introduced by this change.
- **Phase B option:** Split the Gemini call into prose-only streaming + JSON-only non-streaming call. Eliminates `stripPoiJson` mid-stream processing and the partial `{` flicker entirely. Not needed for Phase A.
- `stripPoiJson()` continues to run on every mid-stream token for display purposes. On a slow mobile connection, an unclosed `{` block could be visible in the prose area for several seconds before the block closes. This is a display-only artefact — it does not affect `poiCards` state.
