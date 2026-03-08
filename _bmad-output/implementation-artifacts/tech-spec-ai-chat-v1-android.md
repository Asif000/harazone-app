---
title: 'AI Chat v1 — In-Map Conversational Overlay (Android)'
slug: 'ai-chat-v1-android'
created: '2026-03-08'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'Koin 4.x', 'Ktor SSE', 'kotlinx.coroutines', 'kotlinx.serialization', 'Gemini API (streamGenerateContent SSE)', 'kotlin.test + Turbine + UnconfinedTestDispatcher']
files_to_modify: ['GeminiAreaIntelligenceProvider.kt', 'GeminiPromptBuilder.kt', 'ChatViewModel.kt (NEW)', 'ChatUiState.kt (NEW)', 'ChatOverlay.kt (NEW)', 'UiModule.kt', 'MapScreen.kt', 'MapViewModel.kt', 'FakeAreaIntelligenceProvider.kt', 'ChatViewModelTest.kt (NEW)']
code_patterns: ['sealed UiState + MutableStateFlow', 'viewModelScope.launch + Job cancellation', 'Flow<ChatToken> streaming collection', 'Koin viewModel{} registration', 'PlatformBackHandler expect/actual', 'Fake-based unit tests in commonTest']
test_patterns: ['kotlin.test', 'UnconfinedTestDispatcher + Dispatchers.setMain/resetMain', 'app.cash.turbine', 'Fake* classes in commonTest/fakes/', 'Tests in commonTest (not androidUnitTest)']
---

# Tech-Spec: AI Chat v1 — In-Map Conversational Overlay (Android)

**Created:** 2026-03-08

## Overview

### Problem Statement

The app has no dedicated conversational AI interface. The existing AI question flow lives inside `MapViewModel` as a side-mode of the search overlay, with `conversationHistory` hardcoded to `emptyList()` (BACKLOG-MEDIUM), meaning follow-up questions produce standalone answers with no context. There is no persistent chat thread, no dedicated ViewModel, and no UI designed for multi-turn conversation.

### Solution

Add a dedicated `ChatViewModel` and `ChatOverlay` (ModalBottomSheet) accessible via a new chat FAB (💬, bottom-left of the map). The overlay streams responses from the existing `AreaIntelligenceProvider.streamChatResponse()`, maintains `conversationHistory` across turns so follow-ups have full context, shows chat bubbles with live streaming animation, and surfaces vibe-aware follow-up chips. Area context (areaName + top-5 POI names + active vibe) is injected as a system turn into conversation history on open. Also fixes `GeminiAreaIntelligenceProvider.streamChatResponse()` which was silently ignoring the `conversationHistory` parameter entirely.

### Scope

**In Scope:**
- New `ChatViewModel` (commonMain, Koin `viewModel {}`)
- New `ChatUiState` sealed class (commonMain)
- New `ChatOverlay` composable (commonMain, ModalBottomSheet, 84% height)
- New dedicated chat FAB (💬, `Alignment.BottomStart`, `bottom = navBarPadding + 72.dp`)
- Streaming bubble with blinking cursor; input + send disabled during stream
- `conversationHistory: List<ChatMessage>` maintained across turns in ChatViewModel
- Area context injected as first `MessageRole.AI` system turn on `openChat()`
- Vibe-aware follow-up chips (keyword logic) — chips disappear on user submit, reappear on response complete
- Empty state: AI orb + prompt + 4 vibe-aware starter chips
- Error state: inline red bubble with ⚠ icon + "Tap to retry" (re-sends last query)
- Auto-scroll to latest message after each token append
- `PlatformBackHandler` + scrim tap + X button for dismiss
- Fix `GeminiAreaIntelligenceProvider.streamChatResponse` to use `conversationHistory` with proper Gemini multi-turn format
- Add `role` field to `GeminiRequestContent` data class
- Add `buildChatPrompt()` to `GeminiPromptBuilder`
- Remove BACKLOG-MEDIUM TODO comment from `MapViewModel.kt` line 147
- Extend `FakeAreaIntelligenceProvider.streamChatResponse` for test support

**Out of Scope:**
- Voice input / speech-to-text
- Push notifications
- Persistence across app restarts (no DB)
- Source link rendering (stored in history, not shown)
- iOS FAB trigger (ChatOverlay in commonMain but no iOS entry point yet)
- Changes to `AreaIntelligenceProvider` interface signature

---

## Context for Development

### Critical Investigation Findings

1. **`streamChatResponse` ignores `conversationHistory` — CONFIRMED BUG** (`GeminiAreaIntelligenceProvider.kt` line 140): calls `promptBuilder.buildAiSearchPrompt(query, areaName)` only. `conversationHistory` param is accepted but never used. Request body is always single-content, single-part with no multi-turn structure. **Must be fixed in Task 1.**

2. **`GeminiRequestContent` has no `role` field** — Gemini multi-turn API requires `role: "user"` or `role: "model"` on each content item. Current: `GeminiRequestContent(parts: List<GeminiRequestPart>)` — no role. **Must add `val role: String` in Task 1.**

3. **`GeminiPromptBuilder.buildAiSearchPrompt` is single-turn only** — `"You are a local guide for $areaName. Answer: $query. Under 120 words."` — no history, no context injection. Needs a new `buildChatPrompt()` method. **Task 2.**

4. **`Source` model confirmed** — `Source(title: String, url: String?)` in `domain/model/Source.kt`. Pass `sources = emptyList()` for all v1 `ChatMessage` construction.

5. **Layout conflict: chat FAB vs AISearchBar** — `AISearchBar` is at `Alignment.BottomStart`, `padding(start=16.dp, bottom=navBarPadding+16.dp, end=168.dp)`. Chat FAB must be placed at `Alignment.BottomStart`, `padding(start=16.dp, bottom=navBarPadding+72.dp)` — one row above.

6. **`FakeAreaIntelligenceProvider.streamChatResponse` returns `emptyFlow()`** — must be extended with configurable `chatFlow` / `shouldThrowChat` properties.

7. **Test pattern** — `MapViewModelTest` uses `UnconfinedTestDispatcher` + `Dispatchers.setMain/resetMain` + `kotlin.test.*`. Mirror exactly in `ChatViewModelTest`.

### Codebase Patterns

- **ViewModels**: Plain `ViewModel()` in `commonMain`, registered via `viewModel { }` in `UiModule.kt`. Injected in composables via `koinViewModel()`.
- **UI state**: Single `MutableStateFlow<UiState>` sealed class. State is immutable data class, updated via `.copy()`.
- **Streaming**: `Flow<ChatToken>` collected in `viewModelScope.launch {}`. Append `token.text` each emission; on `token.isComplete = true` finalise turn.
- **Jobs**: Each streaming call assigned to `var chatJob: Job?`, cancelled on new submit or close.
- **Back handling**: `PlatformBackHandler(enabled = <condition>) { <dismiss> }`.
- **DI**: `AreaIntelligenceProvider` registered as `single<>` in `DataModule.kt` — inject into `ChatViewModel`.
- **Message IDs**: Use a private monotonic counter in `ChatViewModel` (`private var nextId = 0L; private fun nextId() = (nextId++).toString()`). Do NOT use epoch millis for IDs — they are not unique under rapid input, causing `LazyColumn` key collisions.
- **Timestamps**: Inject `AppClock` (registered as `single<AppClock>` in `DataModule.kt`) into `ChatViewModel`. Use `clock.now()` for `ChatMessage.timestamp`. This also makes tests deterministic via `FakeClock`.
- **Auto-scroll**: `LazyListState.animateScrollToItem(messages.lastIndex)` in a `LaunchedEffect(messages.size)`.

### Files to Reference

| File | Action |
| ---- | ------ |
| `composeApp/src/commonMain/.../data/remote/GeminiAreaIntelligenceProvider.kt` | **MODIFY** — Task 1 |
| `composeApp/src/commonMain/.../data/remote/GeminiPromptBuilder.kt` | **MODIFY** — Task 2 |
| `composeApp/src/commonMain/.../ui/map/ChatUiState.kt` | **CREATE** — Task 3 |
| `composeApp/src/commonMain/.../ui/map/ChatViewModel.kt` | **CREATE** — Task 4 |
| `composeApp/src/commonMain/.../di/UiModule.kt` | **MODIFY** — Task 5 |
| `composeApp/src/commonMain/.../ui/map/ChatOverlay.kt` | **CREATE** — Task 6 |
| `composeApp/src/commonMain/.../ui/map/MapScreen.kt` | **MODIFY** — Task 7 |
| `composeApp/src/commonMain/.../ui/map/MapViewModel.kt` | **MODIFY** — Task 8 |
| `composeApp/src/commonTest/.../fakes/FakeAreaIntelligenceProvider.kt` | **MODIFY** — Task 9 |
| `composeApp/src/commonTest/.../ui/map/ChatViewModelTest.kt` | **CREATE** — Task 9 |
| `composeApp/src/commonMain/.../domain/model/ChatMessage.kt` | Read-only reference |
| `composeApp/src/commonMain/.../domain/model/ChatToken.kt` | Read-only reference |
| `composeApp/src/commonMain/.../domain/model/Source.kt` | Read-only reference |
| `composeApp/src/commonMain/.../ui/map/MapUiState.kt` | Read-only reference — do NOT add `isChatOpen` |
| `composeApp/src/commonMain/.../ui/components/PlatformBackHandler.kt` | Use as-is |

### Technical Decisions

1. **Separate ChatViewModel** — independent of MapViewModel. MapScreen observes both via `koinViewModel()`.
2. **Sheet height 84%** — map peeks at top for spatial context.
3. **Context injection** — `openChat(areaName, pois, vibe)` prepends a `MessageRole.AI` system turn: `"Context: You are a local guide for {areaName}. Key places: {poi1, poi2…}. Current vibe focus: {vibeName}."`. This is included in `conversationHistory` on every subsequent `streamChatResponse` call.
4. **`isChatOpen` lives in `ChatUiState`** — `MapUiState` changes cannot close the chat sheet.
5. **Chip lifecycle** — chips list cleared on user message submit; new chips computed and set when `isComplete = true`.
6. **Context strip** — `📍 {areaName} · {activeVibeName}` only in sheet header.
7. **Empty state** — shown when `messages` is empty (only the injected system turn exists, which is not rendered). 4 starter chips derived from `activeVibe` using keyword mapping.
8. **Error state** — appended as a special `ChatUiState.Message` with `isError = true`. Retry calls `sendMessage(lastUserQuery)`. No chips on error.
9. **`GeminiRequestContent` role values** — `"user"` for `MessageRole.USER`, `"model"` for `MessageRole.AI`.

---

## Implementation Plan

### Tasks

- [x] **Task 1: Fix `GeminiAreaIntelligenceProvider.streamChatResponse` for multi-turn**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action 1a: Add `val role: String = "user"` to `GeminiRequestContent` data class (before `parts`), with `"user"` as the default. This keeps the existing `streamAreaPortrait` call site (`GeminiRequestContent(parts = listOf(...))`) compiling without changes — it will use the default `"user"` role, which is valid for Gemini single-turn requests.
  - Action 1b: Replace the `streamChatResponse` body's `requestBody` construction. Instead of calling `promptBuilder.buildAiSearchPrompt()` and wrapping in a single content, build a multi-turn `contents` list from `conversationHistory`:
    ```
    val contents = conversationHistory.map { msg ->
        GeminiRequestContent(
            role = if (msg.role == MessageRole.USER) "user" else "model",
            parts = listOf(GeminiRequestPart(text = msg.content))
        )
    } + GeminiRequestContent(
        role = "user",
        parts = listOf(GeminiRequestPart(text = query))
    )
    val requestBody = json.encodeToString(GeminiRequest(contents = contents))
    ```
  - Action 1c: Add import for `com.areadiscovery.domain.model.MessageRole`.
  - Notes: `streamAreaPortrait` call site at line 71–76 does NOT need changing — the default `role = "user"` covers it. The `buildAiSearchPrompt` method in `GeminiPromptBuilder` is also kept (still used by `MapViewModel.submitSearch`). Do NOT delete it. After adding the default, verify `streamAreaPortrait` still works on device (Gemini accepts `role` on single-turn requests).

- [x] **Task 2: Add `buildChatPrompt` to `GeminiPromptBuilder`** *(no longer needed for request body construction — system context is now a history turn. Keep for reference. This task becomes: add a helper that returns the system context string.)*
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action: Add a new method:
    ```kotlin
    fun buildChatSystemContext(areaName: String, poiNames: List<String>, vibeName: String?): String {
        val poisLine = if (poiNames.isNotEmpty()) " Key places: ${poiNames.joinToString(", ")}." else ""
        val vibeLine = if (vibeName != null) " Current vibe focus: $vibeName." else ""
        return "You are a knowledgeable local guide for $areaName.$poisLine$vibeLine Answer conversationally, under 150 words per reply. Be specific and practical."
    }
    ```
  - Notes: This string is used by `ChatViewModel.openChat()` to construct the first history turn. It is NOT sent as a separate API call.

- [x] **Task 3: Create `ChatUiState`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatUiState.kt` *(new file)*
  - Action: Create sealed class:
    ```kotlin
    package com.areadiscovery.ui.map

    import com.areadiscovery.domain.model.ChatMessage

    data class ChatBubble(
        val id: String,
        val role: MessageRole,
        val content: String,
        val isStreaming: Boolean = false,
        val isError: Boolean = false,
    )

    data class ChatUiState(
        val isOpen: Boolean = false,
        val areaName: String = "",
        val vibeName: String? = null,
        val bubbles: List<ChatBubble> = emptyList(),
        val isStreaming: Boolean = false,
        val followUpChips: List<String> = emptyList(),
        val inputText: String = "",
        val lastUserQuery: String = "",
    )
    ```
  - Notes: `bubbles` is the display list (excludes the system context turn). `conversationHistory` (the full `List<ChatMessage>` including the system turn) is stored as private state in `ChatViewModel`, not in `ChatUiState`.

- [x] **Task 4: Create `ChatViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt` *(new file)*
  - Action: Implement as follows:
    ```kotlin
    package com.areadiscovery.ui.map

    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
    import com.areadiscovery.data.remote.GeminiPromptBuilder
    import com.areadiscovery.domain.model.ChatMessage
    import com.areadiscovery.domain.model.MessageRole
    import com.areadiscovery.domain.model.POI
    import com.areadiscovery.domain.model.Vibe
    import com.areadiscovery.domain.provider.AreaIntelligenceProvider
    import com.areadiscovery.util.AppClock
    import com.areadiscovery.util.AppLogger
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.flow.catch
    import kotlinx.coroutines.launch
    import kotlin.coroutines.cancellation.CancellationException

    class ChatViewModel(
        private val aiProvider: AreaIntelligenceProvider,
        private val promptBuilder: GeminiPromptBuilder,
        private val clock: AppClock,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(ChatUiState())
        val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

        // Full history including system context turn — not exposed to UI
        private var conversationHistory: MutableList<ChatMessage> = mutableListOf()
        private var chatJob: Job? = null
        // Monotonic counter — safe, no collision under rapid input
        private var nextId = 0L
        private fun nextId() = (nextId++).toString()

        fun openChat(areaName: String, pois: List<POI>, activeVibe: Vibe?) {
            chatJob?.cancel()
            conversationHistory = mutableListOf()
            nextId = 0L
            val vibeName = activeVibe?.name
            val poiNames = pois.take(5).map { it.name }
            val systemContext = promptBuilder.buildChatSystemContext(areaName, poiNames, vibeName)
            // System context injected as the first AI turn in history (not rendered in UI)
            conversationHistory.add(
                ChatMessage(
                    id = nextId(),
                    role = MessageRole.AI,
                    content = systemContext,
                    timestamp = clock.now(),
                    sources = emptyList(),
                )
            )
            _uiState.value = ChatUiState(
                isOpen = true,
                areaName = areaName,
                vibeName = vibeName,
                bubbles = emptyList(),
                followUpChips = computeStarterChips(activeVibe),
            )
        }

        fun closeChat() {
            chatJob?.cancel()
            _uiState.value = _uiState.value.copy(isOpen = false)
        }

        fun updateInput(text: String) {
            _uiState.value = _uiState.value.copy(inputText = text)
        }

        fun sendMessage(query: String = _uiState.value.inputText) {
            if (query.isBlank() || _uiState.value.isStreaming) return
            chatJob?.cancel()
            val userBubbleId = nextId()
            val aiBubbleId = nextId()
            val userBubble = ChatBubble(id = userBubbleId, role = MessageRole.USER, content = query)
            _uiState.value = _uiState.value.copy(
                bubbles = _uiState.value.bubbles + userBubble + ChatBubble(
                    id = aiBubbleId, role = MessageRole.AI, content = "", isStreaming = true,
                ),
                isStreaming = true,
                followUpChips = emptyList(),
                inputText = "",
                lastUserQuery = query,
            )
            conversationHistory.add(
                ChatMessage(id = userBubbleId, role = MessageRole.USER, content = query,
                    timestamp = clock.now(), sources = emptyList())
            )
            chatJob = viewModelScope.launch {
                var accumulated = ""
                // Single canonical error handler: .catch {} on the flow.
                // CancellationException is re-thrown automatically by .catch {}.
                aiProvider.streamChatResponse(query, _uiState.value.areaName, conversationHistory.toList())
                    .catch { e ->
                        AppLogger.e(e) { "ChatViewModel: stream failed" }
                        val s = _uiState.value
                        _uiState.value = s.copy(
                            bubbles = s.bubbles.dropLast(1) + ChatBubble(
                                id = aiBubbleId, role = MessageRole.AI,
                                content = "Something went wrong. Tap to retry.",
                                isError = true,
                            ),
                            isStreaming = false,
                            followUpChips = emptyList(),
                        )
                    }
                    .collect { token ->
                        if (token.isComplete) {
                            val s = _uiState.value
                            _uiState.value = s.copy(
                                bubbles = s.bubbles.dropLast(1) + ChatBubble(
                                    id = aiBubbleId, role = MessageRole.AI, content = accumulated
                                ),
                                isStreaming = false,
                                followUpChips = computeFollowUpChips(query),
                            )
                            conversationHistory.add(
                                ChatMessage(id = aiBubbleId, role = MessageRole.AI,
                                    content = accumulated, timestamp = clock.now(), sources = emptyList())
                            )
                        } else {
                            accumulated += token.text
                            val s = _uiState.value
                            _uiState.value = s.copy(
                                bubbles = s.bubbles.dropLast(1) + ChatBubble(
                                    id = aiBubbleId, role = MessageRole.AI,
                                    content = accumulated, isStreaming = true,
                                ),
                            )
                        }
                    }
            }
        }

        fun retryLastMessage() {
            val lastQuery = _uiState.value.lastUserQuery
            if (lastQuery.isBlank()) return
            val s = _uiState.value
            _uiState.value = s.copy(bubbles = s.bubbles.dropLast(2), isStreaming = false)
            // Only remove from history if the last entry is the failed user message
            if (conversationHistory.lastOrNull()?.role == MessageRole.USER) {
                conversationHistory.removeLastOrNull()
            }
            sendMessage(lastQuery)
        }

        fun tapChip(chip: String) {
            // Directly sends — does NOT set inputText first (no flash of text in field)
            sendMessage(chip)
        }

        private fun computeStarterChips(vibe: Vibe?): List<String> = when (vibe) {
            Vibe.SAFETY   -> listOf("Is it safe right now?", "Areas to avoid?", "Safe at night?", "Emergency services nearby?")
            Vibe.WHATS_ON -> listOf("What's on tonight?", "Best events this week?", "Free things to do?", "Where's the crowd tonight?")
            Vibe.CHARACTER -> listOf("What's the vibe here?", "Best local spots?", "Hidden gems?", "Who lives here?")
            Vibe.HISTORY  -> listOf("What's the history here?", "Oldest buildings nearby?", "Famous events here?", "Hidden historical gems?")
            Vibe.COST     -> listOf("Is this area expensive?", "Budget tips?", "Free attractions?", "Cheapest eats nearby?")
            Vibe.NEARBY   -> listOf("What's close by?", "Best way to get around?", "Transport options?", "What's within walking distance?")
            null          -> listOf("What's special about here?", "Hidden gems?", "Best time to visit?", "What's nearby?")
        }

        private fun computeFollowUpChips(query: String): List<String> {
            val q = query.lowercase()
            return when {
                q.containsAny("safe", "crime", "danger", "night") -> listOf("Is it safe at night?", "What areas to avoid?")
                q.containsAny("food", "eat", "restaurant", "drink", "brunch") -> listOf("Best time to visit?", "Vegetarian options?")
                q.containsAny("history", "historic", "old", "founded", "built") -> listOf("When was it built?", "Any famous events here?")
                q.containsAny("cost", "price", "expensive", "cheap", "budget") -> listOf("Budget tips?", "Free things to do?")
                q.containsAny("event", "tonight", "on", "happening") -> listOf("How busy will it be?", "Best way to get there?")
                else -> listOf("Tell me more", "What's nearby?")
            }
        }

        private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
    }
    ```
  - Notes: `GeminiPromptBuilder` and `AppClock` must be added as constructor params and injected via Koin (3 `get()` calls in UiModule — see Task 5). Do NOT use `kotlinx.datetime.Clock` directly; use `clock.now()` via the injected `AppClock`. The `Vibe` enum has exactly 6 values: `CHARACTER, HISTORY, WHATS_ON, SAFETY, NEARBY, COST` — the `when` expression in `computeStarterChips` is exhaustive over all 6 + `null`. There is NO `Vibe.FOOD`. Verify against `domain/model/Vibe.kt` before submitting.

- [x] **Task 5: Register `ChatViewModel` in Koin**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt`
  - Action: Add after existing `viewModel { MapViewModel(...) }` line:
    ```kotlin
    viewModel { ChatViewModel(get(), get(), get()) }
    ```
  - Notes: The three `get()` calls resolve `AreaIntelligenceProvider`, `GeminiPromptBuilder`, and `AppClock` — all registered as singletons in `DataModule.kt`. No new `single {}` needed.

- [x] **Task 6: Create `ChatOverlay` composable**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatOverlay.kt` *(new file)*
  - Action: Implement a `@Composable fun ChatOverlay(viewModel: ChatViewModel, onDismiss: () -> Unit)` using `ModalBottomSheet`. Structure:
    - `@OptIn(ExperimentalMaterial3Api::class)` on the composable function
    - `rememberModalBottomSheetState()` with `skipPartiallyExpanded = true` (opens directly to 84% — no half-sheet intermediate state)
    - Sheet `fillMaxHeight(0.84f)`
    - **Handle**: 38dp wide, 4dp tall pill, centered, `rgba(255,255,255,0.14)` tint
    - **Header** (not scrollable): AI orb icon (indigo gradient, 30×30dp, rounded 9dp) + title "Ask about this area" + subtitle "📍 {areaName} · {vibeName}" + X close button
    - **Messages** (`LazyColumn` with `rememberLazyListState()`):
      - If `bubbles.isEmpty()`: empty state — orb graphic (52×52dp) + "Ask me anything about {areaName}" text + 4 starter chips (each tap calls `viewModel.tapChip(chip)`)
      - Else: render each `ChatBubble`:
        - `MessageRole.USER`: right-aligned, indigo gradient background, white text, `border-radius: 16dp 16dp 4dp 16dp`
        - `MessageRole.AI`, `isStreaming = false`, `isError = false`: left-aligned, `rgba(255,255,255,0.07)` surface, `border-radius: 16dp 16dp 16dp 4dp`
        - `MessageRole.AI`, `isStreaming = true`: same as AI bubble + animated blinking cursor appended (`Canvas` or `Text("▍")` with alpha animation)
        - `MessageRole.AI`, `isError = true`: left-aligned, red-tint surface (`ErrorContainer`), ⚠ prefix, "Tap to retry" clickable — calls `viewModel.retryLastMessage()`
      - `LaunchedEffect(bubbles.size) { listState.animateScrollToItem(max(0, bubbles.size - 1)) }`
    - **Follow-up chips row** (below LazyColumn, not scrollable): shown only when `followUpChips.isNotEmpty() && !isStreaming`. Each chip tap calls `viewModel.tapChip(chip)`.
    - **Input bar** (bottom, not scrollable):
      - `TextField` or `BasicTextField` in a rounded container
      - Send button (indigo circle, ➤ icon): calls `viewModel.sendMessage()` on tap. Disabled when `isStreaming || inputText.isBlank()`.
      - Input field disabled when `isStreaming = true`.
    - **Dismiss**: sheet `onDismissRequest = { onDismiss() }`, X button calls `onDismiss()`
    - **No `PlatformBackHandler` inside `ChatOverlay`** — the canonical back handler lives in `MapScreen` (Task 7d). Do not add a second handler here.
  - Notes: Use `MaterialTheme.colorScheme.surface` / `surfaceVariant` for dark theming consistency. Do NOT hardcode hex colors — use theme tokens. Padding bottom = `WindowInsets.navigationBars` inset.

- [x] **Task 7: Wire `ChatOverlay` and chat FAB into `MapScreen`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Action 7a: In `MapScreen` (the outer composable), inject `ChatViewModel` and collect state:
    ```kotlin
    val chatViewModel: ChatViewModel = koinViewModel()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    ```
    Then pass both down to `ReadyContent` as explicit parameters. Update `ReadyContent`'s signature:
    ```kotlin
    private fun ReadyContent(
        state: MapUiState.Ready,
        viewModel: MapViewModel,
        chatViewModel: ChatViewModel,
        chatState: ChatUiState,
        onNavigateToMaps: ...
    )
    ```
    Update the call site in `MapScreen` to pass `chatViewModel = chatViewModel, chatState = chatState`.
  - Action 7b: In `ReadyContent`, add the chat FAB inside the `Box`:
    ```kotlin
    // Chat FAB — positioned above AISearchBar
    FloatingActionButton(
        onClick = {
            chatViewModel.openChat(
                areaName = state.areaName,
                pois = state.pois,
                activeVibe = state.activeVibe,
            )
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = navBarPadding + 72.dp)
            .size(48.dp),
    ) {
        Icon(/* chat/speech bubble icon */ imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "Open AI Chat")
    }
    ```
  - Action 7c: Directly below the FAB, add the overlay:
    ```kotlin
    if (chatState.isOpen) {
        ChatOverlay(
            viewModel = chatViewModel,
            onDismiss = { chatViewModel.closeChat() },
        )
    }
    ```
  - Action 7d: Add a `PlatformBackHandler` for the chat sheet **after** (below) all existing `PlatformBackHandler` calls in `ReadyContent`. In Compose, the last-composed handler wins — placing it last gives it the highest priority when `chatState.isOpen = true`:
    ```kotlin
    // Must be LAST PlatformBackHandler in ReadyContent — last-composed = highest priority
    PlatformBackHandler(enabled = chatState.isOpen) {
        chatViewModel.closeChat()
    }
    ```
  - Action 7e: Add `&& !chatState.isOpen` to the `AnimatedVisibility` condition wrapping the MyLocation button, so it hides when the chat sheet is open (avoids overlap with sheet):
    ```kotlin
    AnimatedVisibility(visible = state.showMyLocationButton && !chatState.isOpen) { ... }
    ```
  - Notes:
    - `Icons.AutoMirrored.Filled.Chat` requires `material-icons-extended` — verify it's in the dependency tree or use `Icons.Default.Forum` / a custom SVG.
    - The FAB does NOT show when the map is in `MapUiState.Loading` or `LocationFailed` — it only exists inside `ReadyContent`.
    - Chat FAB bottom padding `+72.dp` is a marginal clearance over AISearchBar at `+16.dp`. Consider `+80.dp` if the FABs feel visually crowded on small screens.

- [x] **Task 8: Remove BACKLOG-MEDIUM TODO from `MapViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt`
  - Action: Delete line 147: `// TODO(BACKLOG-MEDIUM): conversationHistory always empty — follow-up chips produce standalone answers with no context`
  - Notes: The `emptyList()` passed to `streamChatResponse` in `MapViewModel.submitSearch()` can stay — the search overlay remains single-turn. The dedicated chat sheet is the multi-turn entry point.

- [x] **Task 9: Extend `FakeAreaIntelligenceProvider` and create `ChatViewModelTest`**
  - File A: `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt`
  - Action A: Replace the `streamChatResponse` stub with:
    ```kotlin
    var chatTokens: List<ChatToken> = emptyList()
    var shouldThrowChat: Boolean = false
    var chatCallCount = 0
    var lastChatHistory: List<ChatMessage> = emptyList()

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>,
    ): Flow<ChatToken> {
        chatCallCount++
        lastChatHistory = conversationHistory
        return flow {
            if (shouldThrowChat) throw RuntimeException("Chat test error")
            chatTokens.forEach { emit(it) }
        }
    }
    ```
  - File B: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/ChatViewModelTest.kt` *(new file)*
  - Action B: Write tests covering:
    - `openChat() sets isOpen=true and injects system turn into history` — after `openChat()`, assert `uiState.isOpen == true`, `uiState.bubbles.isEmpty()`, and `fake.lastChatHistory` on first `sendMessage()` contains a system context turn at index 0.
    - `sendMessage() appends user bubble, then streaming AI bubble` — emit `[ChatToken("Hello", false), ChatToken("", true)]`, assert bubbles contain user bubble + final AI bubble with content "Hello".
    - `conversation history grows across turns` — send two messages, assert `fake.lastChatHistory` on the second call contains 3 entries (system context + user1 + ai1).
    - `error during stream shows error bubble` — set `shouldThrowChat = true`, `sendMessage()`, assert last bubble has `isError = true`.
    - `retryLastMessage() re-sends last query` — after error bubble, call `retryLastMessage()`, assert `chatCallCount == 2`.
    - `closeChat() sets isOpen=false and cancels job` — send message (slow fake), then `closeChat()`, assert `isOpen == false`.
    - `tapChip() calls sendMessage with chip text` — assert user bubble content equals chip text.

---

## Acceptance Criteria

- [x] **AC1**: Given the map is in `Ready` state, when the user taps the 💬 chat FAB (bottom-left, above AISearchBar), then the `ChatOverlay` bottom sheet appears at 84% screen height with the map peeking above.
- [x] **AC2**: Given the chat sheet is open with no messages sent, when the user views the sheet, then the empty state is shown: AI orb + "Ask me anything about {areaName}" + 4 vibe-aware starter chips.
- [x] **AC3**: Given a starter chip is visible, when the user taps it, then the chip text is sent as a message directly (input field is NOT populated first — no visible flash of text in the field).
- [x] **AC4**: Given a message is submitted, when the AI response streams, then a streaming bubble appears with an animated blinking cursor, the input field is disabled, and the send button is disabled.
- [x] **AC5**: Given a streaming response, when new tokens arrive, then the message list auto-scrolls to show the latest text without user interaction.
- [x] **AC6**: Given an AI response completes, when `isComplete = true` is received, then the streaming cursor disappears, the final bubble content is shown, and follow-up chips appear below.
- [x] **AC7**: Given follow-up chips are visible, when the user taps a chip, then that chip disappears from the row and a new user bubble with the chip text is sent.
- [x] **AC8**: Given a multi-turn conversation, when the user sends a second message, then `streamChatResponse` is called with a `conversationHistory` list containing: system context turn (index 0) + first user turn + first AI turn + second user turn — confirming context is maintained.
- [x] **AC9**: Given a message is sent, when `streamChatResponse` throws an exception, then an inline red error bubble appears with "Something went wrong. Tap to retry." and the input is re-enabled.
- [x] **AC10**: Given an error bubble is visible, when the user taps "Tap to retry", then the last query is re-sent, the error bubble is replaced by a new streaming bubble.
- [x] **AC11**: Given the chat sheet is open, when the user presses the Android back button, taps the scrim, or taps the X button, then the sheet dismisses and `chatState.isOpen` becomes false.
- [x] **AC12**: Given `MapUiState` transitions to `Loading` (e.g., location reload), when this happens while the chat sheet is open, then the chat sheet remains open and the conversation is unaffected.
- [x] **AC13**: Given the chat sheet is open with an active conversation, when the user closes and reopens the sheet, then the conversation history is cleared and the empty state is shown (no persistence across open/close).
- [x] **AC14**: Given any conversation state, when the user navigates to `MapUiState.LocationFailed`, then the chat FAB is not visible (it only exists inside `ReadyContent`).

---

## Additional Context

### Dependencies

- No new libraries required.
- **`AppClock`** — inject into `ChatViewModel` (registered as `single<AppClock>` in `DataModule.kt`). Do NOT import `kotlinx.datetime.Clock` directly in `ChatViewModel` — use `AppClock` for testability.
- `Icons.AutoMirrored.Filled.Chat` or `Icons.Default.Forum` — verify availability in `material-icons-extended`. If neither is available without adding the extended icons dependency, use `Icons.Default.MailOutline` as a fallback or add a custom `ImageVector`.
- `ModalBottomSheet` — Material3, already in the dependency tree.
- `GeminiPromptBuilder` — already registered as `single { GeminiPromptBuilder() }` in `DataModule.kt`.

### Testing Strategy

- **Unit tests** (`commonTest/kotlin/.../ui/map/ChatViewModelTest.kt`):
  - All 7 test cases in Task 9 above.
  - Pattern: `UnconfinedTestDispatcher` + `Dispatchers.setMain/resetMain` + `kotlin.test` assertions.
  - Use `FakeAreaIntelligenceProvider` (extended in Task 9).
  - For streaming assertions (tests 2, 3, 5), use Turbine's `viewModel.uiState.test { ... }` block to collect state emissions and `awaitItem()` to assert intermediate states (user bubble appears, then streaming bubble, then final bubble). Do not just assert on `_uiState.value` after `sendMessage()` returns — streaming is async even with `UnconfinedTestDispatcher` and intermediate states may be skipped without Turbine.

- **Manual device test**:
  1. Launch app → tap 💬 FAB → verify empty state + starter chips
  2. Tap a starter chip → verify message sends, stream animates, chips reappear
  3. Tap a follow-up chip → send a third message → verify third call to Gemini includes full 5-turn history in request (observable via `AppLogger.d` logs)
  4. Trigger error (disable network) → verify red error bubble → tap retry → verify retry fires
  5. Press Android back button → verify sheet dismisses
  6. Open chat → trigger a map pan (area reload) → verify chat sheet stays open

- **Regression test** (fixes BACKLOG-MEDIUM):
  - `ChatViewModelTest` `conversation history grows across turns` directly tests the previously-broken behavior.

### Notes

- **High-risk item**: `GeminiRequestContent` now has a `role` field. If `streamAreaPortrait` builds its `GeminiRequest` differently (it uses `promptBuilder.buildAreaPortraitPrompt` into a single content), confirm that single-content requests with `role = "user"` still work — they do per Gemini API docs, but test on device.
- **Vibe enum names**: `computeStarterChips` covers exactly `CHARACTER, HISTORY, WHATS_ON, SAFETY, NEARBY, COST, null`. There is NO `Vibe.FOOD`. The `when` expression is exhaustive — Kotlin will produce a compile error if any enum entry is missing.
- **Layering note**: `ChatViewModel` injects `GeminiPromptBuilder` (a data-layer class) directly. This is a deliberate shortcut for v1 scope — extracting a domain-layer `ChatContextBuilder` is deferred to backlog. Flag this in code review if it becomes a concern.
- **Threading note**: `retryLastMessage()` must only be called from the main thread (called from UI on tap). The `conversationHistory` mutation in `retryLastMessage()` is not thread-safe — do not call it from a background coroutine. This is fine for v1 since all ViewModel public functions are called from UI events.
- **Logging pattern**: Follow `AppLogger.e(throwable) { "message" }` exactly — as used in `MapViewModel`. Do NOT use `AppLogger.e("message", throwable)` (wrong param order).
- **`streamAreaPortrait` unaffected**: That path builds `GeminiRequest(contents = listOf(GeminiRequestContent(...)))`. After adding `role`, the serialised field will appear in the JSON. Gemini accepts a `role` field on single-turn requests without error — but verify on device.
- **Future**: Source link rendering (`ChatMessage.sources`) deferred to Phase 2. Add `// TODO(BACKLOG-LOW): render source attribution links under AI chat bubbles` in `ChatOverlay` where bubbles are rendered.
- **iOS**: `ChatOverlay` in `commonMain` is ready. iOS FAB wiring deferred until iOS Map (MapLibre) lands.

## Review Notes
- Adversarial review completed
- Findings: 14 total, 11 fixed, 3 skipped (2 undecided, 1 noise)
- Resolution approach: auto-fix
- Key fixes: duplicate user message in API (F1), system instruction via Gemini API field (F2), history cap at 20 turns (F3), ID-based bubble targeting (F4), FAB visibility gating (F6), conversation preservation on reopen (F7), streaming auto-scroll (F8), IME inset handling (F9), word-boundary chip matching (F11), rapid-send guard test (F13)
- Skipped: F5 retryLastMessage guard (undecided, added isError check instead), F10 CancellationException (noise — Flow.catch is transparent to cancellation), F12 Koin scoping (undecided, works correctly in practice)
