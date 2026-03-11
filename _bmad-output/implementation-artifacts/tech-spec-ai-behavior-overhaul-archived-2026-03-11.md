---
title: 'AI Behavior Overhaul — Ask First, Concise First'
slug: 'ai-behavior-overhaul'
created: '2026-03-10'
status: 'review'
stepsCompleted: [1, 2, 3]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Gemini AI (streaming)']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/ContextualPill.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
code_patterns:
  - 'ChatUiState is a plain data class — all UI state changes via copy()'
  - 'ViewModel fields: pendingFramingHint, isIntentSelected, sessionPois, currentEngagementLevel — mutable private vars, not StateFlow'
  - 'tapIntentPill() builds system context, adds to conversationHistory[0], then calls sendMessage()'
  - 'computeFollowUpChips() is a pure function called at stream completion'
  - 'Streaming: prose tokens arrive incrementally, POI JSON parsed from completed response'
  - 'SavedPoi != POI — different domain types, both in domain/model/'
test_patterns:
  - 'ChatViewModelTest uses fake/mock AreaIntelligenceProvider + SavedPoiRepository'
  - 'Tests assert on uiState snapshots collected via Turbine'
  - 'System context verified via internal systemContextForTest accessor'
---

# Tech-Spec: AI Behavior Overhaul — Ask First, Concise First

**Created:** 2026-03-10

## Overview

### Problem Statement

Every AI entry point (search bar, POI card "Ask AI", saves per-card "Ask AI", saves overview "Ask AI") currently auto-fires a hardcoded message the moment the user taps. The user has no control. Intent pills exist on the empty state but are bypassed entirely. Responses are up to 150 words by default — essay-like, not conversational. Pills are generic ("Tonight", "Discover") not contextual. There is no visible indicator of what the AI "sees", and no depth management — conversations can run endlessly with no path to action.

### Solution

Make every AI interaction user-initiated. Every entry point opens `ChatOverlay` with surface-specific `ContextualPill` pills and an optional pre-filled input — no message is sent until the user taps a pill or hits send. Introduce a new `ContextualPill` domain type (replaces the generic `ChatIntent` list in state). Add a dismissible context banner showing what the AI is focused on. Make Gemini responses concise by default (2-3 sentences) with a go-deeper path. Track conversation depth and offer "New topic" at level 3.

### Scope

**In Scope:**
- Remove all auto-fire `sendMessage()` calls from all three entry points (POI card, saves per-card, saves overview)
- New `ContextualPill` domain type replacing `List<ChatIntent>` in `ChatUiState`
- Surface-specific pill sets: Default (5 area-aware), PoiCard (3 POI-specific), SavesSheet (3 saves-specific), SavedCard (same 3 as PoiCard)
- Pre-fill text input with "Tell me more about [name]" for PoiCard and SavedCard entries
- Dismissible context banner in ChatOverlay (null for Default entry)
- New `ChatEntryPoint.SavedCard(poiName: String)` variant
- `GeminiPromptBuilder.outputFormatBlock()` prompt update: 2-3 sentences, go-deeper instruction
- Depth tracker (`depthLevel` in state): increments per user message, resets on new pill tap
- "🔄 New topic" chip appears at `depthLevel >= 3`; tapping it calls `resetToIntentPills()`

**Out of Scope:**
- Streaming Phase A (separate spec)
- Discovery Story card
- Capsule UX / user notes display
- Depth indicator dots in UI (design deferred — "New topic" chip is enough for v1)
- "Go deeper" as a separate Gemini call with different prompt (handled naturally by conversation history + prompt instruction)

---

## Context for Development

### Codebase Patterns

- `ChatUiState` is a plain `data class` — all mutations via `_uiState.value = _uiState.value.copy(...)`. No sealed states.
- `ChatEntryPoint` is a `sealed class` in `ui/map/`. The ViewModel maps it to a plain `String` framing hint to avoid cross-layer imports. New `ContextualPill` generation also happens inside the ViewModel for the same reason.
- `tapIntentPill()` currently takes `ChatIntent`. Will take `ContextualPill` — uses `pill.intent` for `intentBlock()`, `pill.message` as the opening message sent to AI.
- `computeFollowUpChips()` is a pure function called at stream completion. Depth logic added here.
- `sendMessage()` is called by `tapIntentPill()` (after setting system context) and by the user directly. Both paths increment `depthLevel`. This is intentional — the opening message counts as depth level 1.
- `SavedPlacesScreen.onAskAi: (String) -> Unit` today → changing to `(SavedPoi?) -> Unit`. `null` = overview button tap, non-null = per-card tap.
- `SavedPoiCard.onAskAi: () -> Unit` stays unchanged — the POI is captured in the lambda at the `SavedPlacesScreen` call site.
- `ChatIntent` enum stays — it is the `intent` field on `ContextualPill` and still passed to `intentBlock()` in `GeminiPromptBuilder`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `ui/map/ChatViewModel.kt` | Core logic — openChat, tapIntentPill, sendMessage, computeFollowUpChips |
| `ui/map/ChatUiState.kt` | Data model — intentPills type change, contextBanner + depthLevel additions |
| `ui/map/ChatOverlay.kt` | UI — EmptyState pill rendering, ContextBanner composable, follow-up chips |
| `ui/map/ChatEntryPoint.kt` | Sealed class — add SavedCard variant |
| `ui/map/MapScreen.kt` | Wire-up — remove auto-fires, update callback types |
| `ui/map/components/ExpandablePoiCard.kt` | onAskAiClick signature: (String)->Unit → ()->Unit |
| `ui/saved/SavedPlacesScreen.kt` | onAskAi signature + call site updates |
| `ui/saved/components/SavedPoiCard.kt` | Reference only — onAskAi: ()->Unit, no change needed |
| `data/remote/GeminiPromptBuilder.kt` | outputFormatBlock() concise-first update |
| `domain/model/ChatIntent.kt` | Reference only — used as intent field on ContextualPill |

### Technical Decisions

1. `ContextualPill` lives in `domain/model/` — it is a domain concept, not UI-only.
2. Store `currentEntryPoint: ChatEntryPoint` as a private ViewModel field so `resetToIntentPills()` can regenerate the correct pill set without requiring `openChat()` to be called again.
3. Depth counter resets per topic: `tapIntentPill()` resets `depthLevel = 0`. `sendMessage()` increments it.
4. Context banner for `SavesSheet`: show `"Using your saved places"` — no count for v1 to avoid race with the saves observer at `openChat()` time.
5. `onAskAiClick` on `ExpandablePoiCard` changes from `(String)->Unit` to `()->Unit`. Pre-fill is now set inside the ViewModel, not constructed in the UI.
6. Only `outputFormatBlock()` changes in `GeminiPromptBuilder` — `buildAiSearchPrompt()` (area portrait) is untouched.
7. "🔄 New topic" chip is identified by its exact string constant in `ChatOverlay` — routes to `resetToIntentPills()` instead of `tapChip()`.

---

## Implementation Plan

### Tasks

- [ ] **Task 1: Create `ContextualPill` domain model (NEW file)**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/ContextualPill.kt`
  - Action: Create new file with:
    ```kotlin
    package com.harazone.domain.model

    data class ContextualPill(
        val label: String,
        val message: String,
        val intent: ChatIntent = ChatIntent.DISCOVER,
        val emoji: String = "✨",
    )
    ```
  - Notes: Pure Kotlin data class, no Android dependencies.

- [ ] **Task 2: Add `SavedCard` to `ChatEntryPoint`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt`
  - Action: Add `data class SavedCard(val poiName: String) : ChatEntryPoint()` as the 4th variant. Result:
    ```kotlin
    sealed class ChatEntryPoint {
        data object Default : ChatEntryPoint()
        data object SavesSheet : ChatEntryPoint()
        data class PoiCard(val poi: POI) : ChatEntryPoint()
        data class SavedCard(val poiName: String) : ChatEntryPoint()
    }
    ```
  - Notes: No other changes to this file.

- [ ] **Task 3: Update `ChatUiState`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt`
  - Action 1: Replace import `import com.harazone.domain.model.ChatIntent` with `import com.harazone.domain.model.ContextualPill`
  - Action 2: Change `val intentPills: List<ChatIntent> = emptyList()` → `val intentPills: List<ContextualPill> = emptyList()`
  - Action 3: Add `val contextBanner: String? = null` field to `ChatUiState`
  - Action 4: Add `val depthLevel: Int = 0` field to `ChatUiState`
  - Notes: Field order does not matter. All existing callers using `intentPills` will need updating (ChatViewModel + ChatOverlay — covered in Tasks 4 and 5).

- [ ] **Task 4: Update `ChatViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`

  - Action 4a — Add private field after `private var isIntentSelected: Boolean = false`:
    ```kotlin
    private var currentEntryPoint: ChatEntryPoint = ChatEntryPoint.Default
    ```

  - Action 4b — Add private pill-generation helpers before `companion object` (or at bottom of class):
    ```kotlin
    private fun defaultPills(areaName: String): List<ContextualPill> = listOf(
        ContextualPill("What's on tonight in $areaName?", "What's on tonight in $areaName?", ChatIntent.TONIGHT, "🌙"),
        ContextualPill("Best food right now", "Where should I eat right now in $areaName?", ChatIntent.HUNGRY, "🍜"),
        ContextualPill("Show me hidden gems", "Show me hidden gems in $areaName", ChatIntent.DISCOVER, "🔍"),
        ContextualPill("Get me outside", "Get me outside in $areaName", ChatIntent.OUTSIDE, "🌳"),
        ContextualPill("Surprise me in $areaName", "Surprise me in $areaName", ChatIntent.SURPRISE, "🎲"),
    )

    private fun poiCardPills(poiName: String): List<ContextualPill> = listOf(
        ContextualPill("Tell me more about $poiName", "Tell me more about $poiName", ChatIntent.DISCOVER, "✨"),
        ContextualPill("What's nearby?", "What's nearby $poiName?", ChatIntent.DISCOVER, "📍"),
        ContextualPill("Is this worth visiting?", "Is $poiName worth visiting?", ChatIntent.DISCOVER, "⭐"),
    )

    private fun savesOverviewPills(): List<ContextualPill> = listOf(
        ContextualPill("Plan a day trip from my saves", "Plan a day trip using my saved places", ChatIntent.DISCOVER, "🗺️"),
        ContextualPill("Find patterns in my saves", "What patterns do you see in my saved places?", ChatIntent.DISCOVER, "🔍"),
        ContextualPill("What am I missing?", "What places am I missing that I should save?", ChatIntent.DISCOVER, "🤔"),
    )

    private fun pillsFor(entryPoint: ChatEntryPoint, areaName: String): List<ContextualPill> = when (entryPoint) {
        is ChatEntryPoint.Default -> defaultPills(areaName)
        is ChatEntryPoint.SavesSheet -> savesOverviewPills()
        is ChatEntryPoint.PoiCard -> poiCardPills(entryPoint.poi.name)
        is ChatEntryPoint.SavedCard -> poiCardPills(entryPoint.poiName)
    }

    private fun preFillFor(entryPoint: ChatEntryPoint): String = when (entryPoint) {
        is ChatEntryPoint.PoiCard -> "Tell me more about ${entryPoint.poi.name}"
        is ChatEntryPoint.SavedCard -> "Tell me more about ${entryPoint.poiName}"
        else -> ""
    }

    private fun bannerFor(entryPoint: ChatEntryPoint): String? = when (entryPoint) {
        is ChatEntryPoint.PoiCard -> "Asking about: ${entryPoint.poi.name}"
        is ChatEntryPoint.SavedCard -> "Asking about: ${entryPoint.poiName}"
        is ChatEntryPoint.SavesSheet -> "Using your saved places"
        is ChatEntryPoint.Default -> null
    }
    ```

  - Action 4c — In `openChat()`, after `chatJob?.cancel()` in the "different area" branch:
    - Add `currentEntryPoint = entryPoint`
    - Extend the `pendingFramingHint` when block to handle `SavedCard`:
      ```kotlin
      is ChatEntryPoint.SavedCard ->
          "The user is currently looking at ${entryPoint.poiName} — lead with context about that place."
      ```
    - Replace `intentPills = ChatIntent.entries.toList()` with `intentPills = pillsFor(entryPoint, areaName)`
    - Add `inputText = preFillFor(entryPoint)` to the `ChatUiState(...)` constructor
    - Add `contextBanner = bannerFor(entryPoint)` to the `ChatUiState(...)` constructor
    - Add `depthLevel = 0` to the `ChatUiState(...)` constructor

  - Action 4d — Change `tapIntentPill` signature and body:
    - Change `fun tapIntentPill(intent: ChatIntent)` → `fun tapIntentPill(pill: ContextualPill)`
    - Change `if (isIntentSelected) return` guard — keep as-is
    - Change `selectedIntent = intent` → `selectedIntent = pill.intent`
    - Change `promptBuilder.buildChatSystemContext(areaName, sessionPois, intent, ...)` → `promptBuilder.buildChatSystemContext(areaName, sessionPois, pill.intent, ...)`
    - Change `inputText = intent.openingMessage` → `inputText = pill.message`
    - Add `depthLevel = 0` to the `_uiState.value.copy(...)` call (reset depth on new intent)

  - Action 4e — In `sendMessage()`, after the early-return guards (`if (query.isBlank() || ...)`), add depth increment:
    ```kotlin
    _uiState.value = _uiState.value.copy(depthLevel = _uiState.value.depthLevel + 1)
    ```
    Add this BEFORE the `chatJob?.cancel()` line so the depth reflects the message being sent.

  - Action 4f — Update `computeFollowUpChips` signature and add depth cap:
    - Add `depthLevel: Int` parameter
    - At the very top of the function body, before the `if (level == EngagementLevel.FRESH)` check:
      ```kotlin
      if (depthLevel >= 3) {
          return listOf("🔄 New topic") + computeFollowUpChipsByKeyword(query).take(1)
      }
      ```
    - Update the call site inside `sendMessage()` (in the `.collect { token ->` `isComplete` branch):
      ```kotlin
      followUpChips = computeFollowUpChips(query, selectedIntent, currentEngagementLevel, _uiState.value.depthLevel),
      ```

  - Action 4g — Add `resetToIntentPills()` public function:
    ```kotlin
    fun resetToIntentPills() {
        chatJob?.cancel()
        conversationHistory = mutableListOf()
        nextId = 0L
        isIntentSelected = false
        selectedIntent = null
        val areaName = _uiState.value.areaName
        _uiState.value = _uiState.value.copy(
            bubbles = emptyList(),
            followUpChips = emptyList(),
            poiCards = emptyList(),
            showSkeletons = false,
            isStreaming = false,
            intentPills = pillsFor(currentEntryPoint, areaName),
            inputText = preFillFor(currentEntryPoint),
            contextBanner = bannerFor(currentEntryPoint),
            depthLevel = 0,
        )
    }
    ```

  - Action 4h — Add `dismissContextBanner()` public function:
    ```kotlin
    fun dismissContextBanner() {
        _uiState.value = _uiState.value.copy(contextBanner = null)
    }
    ```

  - Notes: Add `import com.harazone.domain.model.ContextualPill` to imports. Remove `import com.harazone.domain.model.ChatIntent` only if it's no longer referenced directly — it IS still used for `selectedIntent: ChatIntent?` and `computeFollowUpChips`, so keep it.

- [ ] **Task 5: Update `ChatOverlay.kt`**

  - Action 5a — Remove `intentPillEmoji()` function (lines 306–312). Emoji is now on `pill.emoji`.

  - Action 5b — Update `EmptyState` composable:
    - Change signature: `intentPills: List<ChatIntent>` → `intentPills: List<ContextualPill>`, `onPillTap: (ChatIntent) -> Unit` → `onPillTap: (ContextualPill) -> Unit`
    - Replace the FlowRow pill rendering:
      ```kotlin
      intentPills.forEach { pill ->
          SuggestionChip(
              onClick = { onPillTap(pill) },
              label = { Text("${pill.emoji} ${pill.label}", fontSize = 13.sp) },
              colors = SuggestionChipDefaults.suggestionChipColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant,
              ),
          )
      }
      ```

  - Action 5c — Add `ContextBanner` private composable (add before or after `EmptyState`):
    ```kotlin
    @Composable
    private fun ContextBanner(
        text: String,
        onDismiss: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    ```

  - Action 5d — In `ChatOverlay` body, after the header `Row` block and before `Spacer(Modifier.height(4.dp))`, add:
    ```kotlin
    chatState.contextBanner?.let { banner ->
        ContextBanner(text = banner, onDismiss = { viewModel.dismissContextBanner() })
    }
    ```

  - Action 5e — Update `EmptyState` call site in `LazyColumn`:
    ```kotlin
    EmptyState(
        areaName = chatState.areaName,
        intentPills = chatState.intentPills,
        onPillTap = { viewModel.tapIntentPill(it) },
    )
    ```

  - Action 5f — Update follow-up chips `forEach` in the `FlowRow` below messages. Replace the single `SuggestionChip` with a when-branch:
    ```kotlin
    chatState.followUpChips.forEach { chip ->
        if (chip == "🔄 New topic") {
            SuggestionChip(
                onClick = { viewModel.resetToIntentPills() },
                label = { Text(chip, fontSize = 13.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                ),
            )
        } else {
            SuggestionChip(
                onClick = { viewModel.tapChip(chip) },
                label = { Text(chip, fontSize = 13.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
    ```

  - Notes: Add `import com.harazone.domain.model.ContextualPill`. Remove `import com.harazone.domain.model.ChatIntent` only if no other reference exists in this file.

- [ ] **Task 6: Update `ExpandablePoiCard.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt`
  - Action 1: Change parameter line 70: `onAskAiClick: (String) -> Unit` → `onAskAiClick: () -> Unit`
  - Action 2: Change call site line 228: `onClick = { onAskAiClick("Tell me more about ${poi.name}") }` → `onClick = { onAskAiClick() }`
  - Notes: No other changes needed in this file.

- [ ] **Task 7: Update `MapScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

  - Action 7a — POI card `onAskAiClick` handler (lines ~250–261):
    - Change lambda from `{ query ->` to `{` (no parameter)
    - Remove `chatViewModel.sendMessage(query)` call entirely
    - Keep `viewModel.clearPoiSelection()` and the streaming guard and `chatViewModel.openChat(...)` call unchanged
    - Result:
      ```kotlin
      onAskAiClick = {
          viewModel.clearPoiSelection()
          if (chatState.isStreaming) {
              coroutineScope.launch {
                  snackbarHostState.showSnackbar("AI is still responding...")
              }
          } else {
              chatViewModel.openChat(
                  state.areaName, state.pois, state.activeVibe,
                  entryPoint = ChatEntryPoint.PoiCard(state.selectedPoi!!),
              )
          }
      },
      ```

  - Action 7b — Saves sheet `onAskAi` handler (lines ~394–398):
    - Change lambda from `{ msg ->` to `{ poi ->`
    - Replace `chatViewModel.openChat(..., ChatEntryPoint.SavesSheet)` and `chatViewModel.sendMessage(msg)` with:
      ```kotlin
      onAskAi = { poi ->
          viewModel.closeSavesSheet()
          if (poi != null) {
              chatViewModel.openChat(
                  state.areaName, state.pois, state.activeVibe,
                  entryPoint = ChatEntryPoint.SavedCard(poi.name),
              )
          } else {
              chatViewModel.openChat(
                  state.areaName, state.pois, state.activeVibe,
                  entryPoint = ChatEntryPoint.SavesSheet,
              )
          }
      },
      ```
    - Add import if missing: `import com.harazone.domain.model.SavedPoi`

- [ ] **Task 8: Update `SavedPlacesScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt`
  - Action 1: Change parameter signature line 76: `onAskAi: (String) -> Unit` → `onAskAi: (poi: SavedPoi?) -> Unit`
  - Action 2: Update per-card call site line 323: `onAskAi = { onAskAi("Tell me more about ${poi.name}") }` → `onAskAi = { onAskAi(poi) }`
  - Action 3: Update overview "Ask AI" button call site line 345: `onClick = { onAskAi("What should I do with my saved places?") }` → `onClick = { onAskAi(null) }`
  - Action 4: Delete the `TODO(BACKLOG-HIGH)` comment on line 343 — this spec resolves it.
  - Notes: `SavedPoi` is likely already imported in this file. Verify and add if missing.

- [ ] **Task 9: Update `GeminiPromptBuilder.kt` — concise-first prompt**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: In `outputFormatBlock()`, replace the opening length instruction only. Change `"Answer conversationally, under 150 words of prose per reply."` to:
    ```
    "Answer like a knowledgeable friend texting — 2-3 sentences max. Be specific: use real place names and details, never vague filler. If the user says 'go deeper', 'tell me more', or similar, expand to one short paragraph (100–150 words) with richer context."
    ```
  - Keep the rest of `outputFormatBlock()` (the 1:1 JSON card rule, format instructions, example) exactly as-is.
  - Notes: Do NOT touch `buildAiSearchPrompt()`. Only `outputFormatBlock()` changes.

---

### Acceptance Criteria

- [ ] **AC-1 — No auto-fire from POI card**
  Given: User is viewing a POI card overlay on the map
  When: User taps "Ask AI"
  Then: ChatOverlay opens with context banner "Asking about: [POI name]", three pills (Tell me more, What's nearby?, Is this worth visiting?), text input pre-filled with "Tell me more about [POI name]", and NO message has been sent to Gemini (no bubbles visible)

- [ ] **AC-2 — No auto-fire from saves per-card**
  Given: User is in SavedPlacesScreen and taps the Ask AI icon on a specific saved card
  When: The icon is tapped
  Then: ChatOverlay opens with context banner "Asking about: [POI name]", the same three POI-card pills, text pre-filled — NO Gemini call, NO bubbles

- [ ] **AC-3 — No auto-fire from saves overview**
  Given: User is in SavedPlacesScreen and taps the overview "Ask AI" button
  When: The button is tapped
  Then: ChatOverlay opens with context banner "Using your saved places", three saves-specific pills (Plan a day trip from my saves, Find patterns in my saves, What am I missing?), empty text input — NO Gemini call

- [ ] **AC-4 — Area-specific default pills**
  Given: User taps the search bar / AI bar in area "Bondi Beach"
  When: ChatOverlay opens (Default entry)
  Then: Pills read "What's on tonight in Bondi Beach?", "Best food right now", "Show me hidden gems", "Get me outside", "Surprise me in Bondi Beach"
  And: No context banner is shown

- [ ] **AC-5 — Concise-first response**
  Given: User taps any intent pill and Gemini responds
  When: The response completes
  Then: The AI bubble prose is 2-3 sentences (not a multi-paragraph essay). Specific place names appear if places are being recommended.

- [ ] **AC-6 — "Go deeper" expands the response**
  Given: An AI response has appeared (2-3 sentences)
  When: User types "go deeper" or "tell me more" and sends
  Then: The next AI response is a fuller paragraph (~100-150 words) with more specific detail about the same topic

- [ ] **AC-7 — Depth tracking triggers "New topic" chip**
  Given: User has sent 3 messages in a session (after tapping a pill)
  When: AI responds to the 3rd user message
  Then: "🔄 New topic" appears as the first follow-up chip, styled distinctly (tertiaryContainer background)

- [ ] **AC-8 — "New topic" resets cleanly**
  Given: "🔄 New topic" chip is visible
  When: User taps it
  Then: Conversation clears (no bubbles), pills reappear with the same surface-specific set, depth resets to 0, context banner reappears (for PoiCard/SavedCard entries), text input pre-fill restores

- [ ] **AC-9 — Context banner is dismissible**
  Given: ChatOverlay is open with a context banner (POI card or saves entry)
  When: User taps the × on the banner
  Then: Banner disappears; the chat continues normally with pills and input still present

- [ ] **AC-10 — Depth resets on new pill tap (unit test)**
  Given: ViewModel has processed 3 user messages (depthLevel == 3)
  When: `tapIntentPill(pill)` is called
  Then: `uiState.depthLevel == 0` after the pill tap resets state (before the opening `sendMessage` increments it to 1)

---

## Additional Context

### Dependencies

- No new libraries required
- No DB schema changes
- No new Koin modules or DI wiring
- `ContextualPill` is a pure Kotlin data class — compiles to all KMP targets without platform code

### Testing Strategy

Write the following unit tests in `ChatViewModelTest.kt`:

1. `openChat_withPoiCard_setsPreFillAndNoBubbles` — call `openChat(PoiCard(poi))` → assert `uiState.inputText == "Tell me more about ${poi.name}"`, `uiState.bubbles.isEmpty()`, no Gemini call made
2. `openChat_withSavesSheet_showsSavesSpecificPills` — call `openChat(SavesSheet)` → assert `uiState.intentPills.map { it.label }` contains "Plan a day trip from my saves"
3. `openChat_withDefault_hasNoBanner` — call `openChat(Default)` → assert `uiState.contextBanner == null`
4. `openChat_withPoiCard_hasBanner` — call `openChat(PoiCard(poi))` → assert `uiState.contextBanner == "Asking about: ${poi.name}"`
5. `sendMessage_incrementsDepthLevel` — after 3 `sendMessage()` calls → assert `uiState.depthLevel == 3`
6. `tapIntentPill_resetsDepthLevel` — prime depthLevel to 2 manually, call `tapIntentPill(pill)` → assert `uiState.depthLevel == 0` at the moment pills are cleared (before the internal `sendMessage`)
7. `resetToIntentPills_clearsBubblesAndRestoresPills` — after a conversation, call `resetToIntentPills()` → assert `uiState.bubbles.isEmpty()`, `uiState.intentPills.isNotEmpty()`, `uiState.depthLevel == 0`

Manual testing checklist:
- Tap "Ask AI" on a map POI card → verify no immediate Gemini spinner, pills and pre-fill visible
- Tap "Ask AI" on a saved card → same verification
- Tap overview "Ask AI" in saves → verify saves-specific pills, no pre-fill, banner shows
- Open search bar chat → verify area-specific pill labels, no banner
- Have a 3-turn conversation → verify "New topic" chip appears
- Tap "New topic" → verify clean reset

### Notes

- The BACKLOG-HIGH TODO comment in `SavedPlacesScreen.kt` line 343 is explicitly resolved by this spec — delete it in Task 8.
- `tapChip(chip: String)` stays as-is — regular follow-up chips still route through it. Only the literal string `"🔄 New topic"` bypasses it in `ChatOverlay`.
- Pill labels for Default entry intentionally include the area name (e.g., "What's on tonight in Bondi Beach?") per brainstorm decision #28 — pills must be specific/contextual, not generic.
- If `selectedIntent` is `null` when `computeFollowUpChips` runs (edge case: user sends a raw message before tapping a pill), the depth cap still applies correctly — "New topic" will still appear at depth 3.
- Future: if the saves count banner becomes important, expose `savedCount: StateFlow<Int>` from the ViewModel and update `bannerFor()` to use it. Not needed for v1.
