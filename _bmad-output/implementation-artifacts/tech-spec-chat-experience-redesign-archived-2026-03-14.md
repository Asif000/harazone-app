---
title: 'Chat Experience Redesign'
slug: 'chat-experience-redesign'
created: '2026-03-14'
status: 'implemented'
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Gemini AI', 'Koin']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
code_patterns:
  - 'ChatUiState data class holds all UI state; mutate via _uiState.value = _uiState.value.copy(...)'
  - 'GeminiPromptBuilder builds prompt blocks as private String functions, assembled in buildChatSystemContext()'
  - 'reinforcedQuery appends a per-turn reminder to every Gemini call'
  - 'ChatOverlay.kt is pure UI; all logic stays in ChatViewModel'
  - 'ContextualPill(label, message, intent, emoji) — message is what gets sent to Gemini'
test_patterns:
  - 'ChatViewModelTest uses TestAppClock + FakeAreaIntelligenceProvider + FakeSavedPoiRepository'
  - 'systemContextForTest internal accessor verifies system context injection'
  - 'Flow-based streaming tests use turbine collect pattern'
---

# Tech-Spec: Chat Experience Redesign

**Created:** 2026-03-14

## Overview

### Problem Statement

Chat and map are two parallel universes. Chat generates its own POIs independent of what the user sees on the map, causing 5 distinct bugs: (1) follow-up responses return plain text with no POI cards; (2) places mentioned in chat don't match the map pins; (3) intent pills vanish after the first tap and never return; (4) returning to the same area resumes a stale conversation with no user control; (5) AI responses don't reliably end with a follow-up question.

### Solution

Seven targeted changes across prompt, ViewModel, and UI that make chat a narrator for the map rather than a parallel universe: inject the full map POI set into every prompt, strengthen the prompt contract so every place mention produces a card, make the intent pill rail permanent and self-replenishing, accumulate POI cards across turns, add a "Show on Map" card action, enforce question-ending in the prompt, and introduce a 30-minute conversation expiry with a user choice on return.

### Scope

**In Scope:**
- Map-aware prompt injection — full map POI set in every chat message (#104)
- Persistent intent pill rail above input — always visible, replenishes after each AI response (#107)
- POI card accumulation across conversation turns — cards persist and grow (#109)
- "Show on Map" card action — dismiss chat, fly camera to pin (#110)
- Prompt contract strengthening — every prose mention must be a POI card (#111)
- Always-end-with-question prompt enforcement (#112)
- Conversation expiry after 30 minutes — choice dialog on return (#105/#106)

**Out of Scope:**
- Chat session persistence across app kills (sessions remain in-memory only)
- Chat history browser / ARCHIVED state (#113)
- Chat POI highlight/ghost pins on map (#114) — deferred v1.1
- Markdown renderer changes — already shipped
- Any changes to Stage 1/2 POI fetch pipeline

---

## Context for Development

### Codebase Patterns

- **`ChatUiState`** (`ChatUiState.kt:34`) — pure data class, all fields immutable; mutate via `_uiState.value = _uiState.value.copy(...)`.
- **`ChatViewModel`** (`ChatViewModel.kt:28`) — single ViewModel; `tapIntentPill()` builds the system context + injects it as first conversation message; `sendMessage()` appends `reinforcedQuery` suffix for per-turn reinforcement.
- **`GeminiPromptBuilder`** (`GeminiPromptBuilder.kt`) — each block is a `private fun` returning a `String`; `buildChatSystemContext()` filters blanks and joins with `\n\n`. Add new blocks as private functions; add parameters to `buildChatSystemContext()` as needed.
- **`areaContextBlock`** currently injects only `pois.take(5).joinToString(", ") { it.name }`. This is the root cause of the map-chat mismatch.
- **`outputFormatBlock`** already contains the prose→pois contract rule, but it lives only in the system message (conversation index 0). Follow-up turns can drift because Gemini's attention on the system message weakens. `reinforcedQuery` re-injects a shorter reminder per-turn.
- **`intentPills`** in `ChatUiState.kt:46` — currently shown inside `EmptyState` only when `bubbles.isEmpty()`. Cleared to `emptyList()` in `tapIntentPill()` (line 163) and never replenished.
- **`followUpChips`** (`ChatUiState.kt:40`) — list of plain `String`s shown in `FlowRow` above input. These are NOT `ContextualPill` — they don't carry an intent enum.
- **`poiCards`** (`ChatUiState.kt:43`) — set to `emptyList()` at the start of every `sendMessage()` call (line 210). This is why follow-up cards disappear.
- **`sessionPois`** (`ChatViewModel.kt:49`) — snapshot of all POIs when chat opens, passed into `buildChatSystemContext()`. Correct data is already here; it just isn't fully used.
- **`onPoiCardClick`** (`ChatOverlay.kt:87`) — currently maps to POI detail expand, not "show on map".

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt` | All state types: `ChatUiState`, `ChatPoiCard`, `ChatBubble`, `ChatResponse` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | All chat logic: `openChat`, `tapIntentPill`, `sendMessage`, `computeFollowUpChips` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt` | Chat UI: `ModalBottomSheet`, `EmptyState`, `ChatPoiMiniCard`, `ChatInputBar` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | Prompt blocks: `buildChatSystemContext`, `areaContextBlock`, `outputFormatBlock` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | POI data model (fields: name, type, latitude, longitude, whySpecial/insight, vibe) |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/ContextualPill.kt` | `ContextualPill(label, message, intent, emoji)` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Map root screen — where `ChatOverlay` is called; owns `onPoiCardClick` and camera control |

### Technical Decisions

1. **Map POI injection format:** Inject all `sessionPois` as a compact numbered list: `"1. {name} ({type}) — {insight/whySpecial}"`. Cap at 15 POIs to stay within Gemini context budget. Include lat/lng only for the POIs (not in the prose block) so Gemini can echo them back in the pois array accurately. This replaces the current top-5-names-only injection.

2. **Persistent pill rail vs follow-up chips:** Replace the two separate systems (`intentPills` in `EmptyState` + `followUpChips` FlowRow) with a single `persistentPills: List<ContextualPill>` field in `ChatUiState`. The rail lives in a fixed-position section above `ChatInputBar`. Initial pills = `defaultPills()`; after each AI response → replenish with `computePersistentPills(intent, lastQuery, depthLevel)`.

3. **POI card accumulation:** Remove `poiCards = emptyList()` from the start of `sendMessage()`. Instead, append new cards from each response to the existing list, deduplicating by `card.id`. Add `accumulatedPoiCards: List<ChatPoiCard>` to `ChatUiState` (or just change `poiCards` to be append-only). Reset to empty only on `resetToIntentPills()` and `openChat()` new-area path.

4. **POI card rail layout:** Change from `LazyColumn` vertical items to a single `LazyRow` horizontal card strip. This rail should live below the `LazyColumn` message area and above the pill rail, always visible when `poiCards.isNotEmpty()`. This matches the prototype design.

5. **"Show on Map" callback:** Add `onShowOnMap: (ChatPoiCard) -> Unit` parameter to `ChatOverlay`. Add a `📍 Show on Map` chip to `ChatPoiMiniCard` alongside the existing Directions chip. The callback in `MapScreen` will: call `chatViewModel.closeChat()`, then call `mapViewModel.selectAndFlyTo(card)`.

6. **Conversation expiry:** Add `chatOpenedAt: Long = 0L` to `ChatViewModel`. Set it on first `openChat()` for a new area, reset on `resetToIntentPills()`. On `openChat()` same-area check: if `clock.nowMs() - chatOpenedAt > 30 * 60 * 1000L` AND `bubbles.isNotEmpty()` → set `showReturnDialog = true` in `ChatUiState` instead of restoring conversation. `ChatUiState` gets `showReturnDialog: Boolean = false`. Dialog shows "Pick up where you left off?" with [Continue] and [Start Fresh] buttons.

7. **Prompt contract reinforcement:** The `outputFormatBlock` rule already exists. Add a stronger restatement in `areaContextBlock` immediately after listing the map POIs: "REFERENCE RULE: Any place you mention in your prose response MUST appear in the pois array with coordinates. Do NOT invent places not in the list above unless the user asks for something you cannot find there."

8. **Always-end-with-question enforcement:** The `reinforcedQuery` suffix already has "End with a question." Strengthen it: change suffix from `"[Remember: 2-3 sentences max. End with a question. Respond with JSON only: ...]"` to `"[REQUIRED: Respond with JSON only. 2-3 prose sentences. FINAL SENTENCE MUST BE A QUESTION ending with '?'.]"`.

---

## Implementation Plan

### Tasks

Tasks are ordered dependency-first. Complete each before starting the next.

---

**Task 1 — `GeminiPromptBuilder.kt`: Upgrade `areaContextBlock` to inject full map POI set**

File: `GeminiPromptBuilder.kt`

Current `areaContextBlock` (line 178–182):
```kotlin
private fun areaContextBlock(areaName: String, pois: List<POI>): String {
    val poiLine = if (pois.isNotEmpty()) " Key places in this area include: ${pois.take(5).joinToString(", ") { it.name }}." else ""
    return """AREA CONTEXT: You are guiding someone around $areaName.$poiLine
Local dining culture: ..."""
}
```

Replace the `poiLine` construction with a full structured block. Change signature to add explicit `mapPois` parameter or reuse existing `pois`:

```kotlin
private fun areaContextBlock(areaName: String, pois: List<POI>): String {
    val poiSection = if (pois.isNotEmpty()) {
        val poiList = pois.take(15).mapIndexed { i, poi ->
            val desc = poi.insight?.takeIf { it.isNotBlank() } ?: poi.description?.take(80) ?: ""
            "${i + 1}. ${poi.name} (${poi.type})${if (desc.isNotBlank()) " — $desc" else ""}"
        }.joinToString("\n")
        """
MAP POIS — these are the exact places visible on the user's map right now:
$poiList

REFERENCE RULE: Any place you mention in your prose MUST appear in the pois array with matching name and coordinates. Do NOT mention places outside this list unless the user specifically asks for something not here."""
    } else ""
    return """AREA CONTEXT: You are guiding someone around $areaName.$poiSection
Local dining culture: adapt to what "good food" means HERE — street carts, markets, fine dining, whatever fits this place. QUALITY MEANS: memorable, worth the trip, has a story. NOT: has a website, has 4+ Google stars, looks good on Instagram."""
}
```

Note: `pois` is already passed into `buildChatSystemContext()` as the `pois: List<POI>` parameter (line 142). No signature changes needed; just change the body of `areaContextBlock`.

---

**Task 2 — `GeminiPromptBuilder.kt`: Strengthen `reinforcedQuery` contract**

File: `ChatViewModel.kt` (line 230)

Current:
```kotlin
val reinforcedQuery = query + "\n[Remember: 2-3 sentences max. End with a question. Respond with JSON only: {\"prose\":\"...\",\"pois\":[...]}]"
```

Replace with:
```kotlin
val mapPoiReminder = if (sessionPois.isNotEmpty()) {
    " Map POIs: ${sessionPois.take(15).joinToString("; ") { it.name }}."
} else ""
val reinforcedQuery = query + "\n[REQUIRED: JSON only. prose = 2-3 sentences, LAST SENTENCE ENDS WITH '?'.$mapPoiReminder Every named place in prose → entry in pois array.]"
```

This re-injects the POI name list as a compact reminder on every turn (not the full structured block — just names for dedup reference).

---

**Task 3 — `ChatUiState.kt`: Add new fields for persistent pills, return dialog, and accumulated cards**

File: `ChatUiState.kt`

Add to `ChatUiState` data class:
```kotlin
data class ChatUiState(
    val isOpen: Boolean = false,
    val areaName: String = "",
    val vibeName: String? = null,
    val bubbles: List<ChatBubble> = emptyList(),
    val isStreaming: Boolean = false,
    val followUpChips: List<String> = emptyList(),   // REMOVE in Task 5
    val inputText: String = "",
    val lastUserQuery: String = "",
    val poiCards: List<ChatPoiCard> = emptyList(),   // now accumulated, not cleared per-message
    val showSkeletons: Boolean = false,
    val savedPoiIds: Set<String> = emptySet(),
    val intentPills: List<ContextualPill> = emptyList(),  // REMOVE in Task 5 (replaced by persistentPills)
    val contextBanner: String? = null,
    val depthLevel: Int = 0,
    // New fields:
    val persistentPills: List<ContextualPill> = emptyList(),  // always-visible pill rail
    val showReturnDialog: Boolean = false,                    // 30-min expiry dialog
)
```

Do NOT remove `followUpChips` or `intentPills` yet — they'll be removed/repurposed in Task 5 after UI is updated.

---

**Task 4 — `ChatViewModel.kt`: Add expiry logic to `openChat()`**

File: `ChatViewModel.kt`

Add private field:
```kotlin
private var chatOpenedAt: Long = 0L
private val EXPIRY_MS = 30 * 60 * 1000L  // 30 minutes
```

In `openChat()`, modify the same-area guard (currently lines 83–93):

```kotlin
fun openChat(areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?, entryPoint: ChatEntryPoint = ChatEntryPoint.Default) {
    val current = _uiState.value
    if (current.areaName == areaName && (current.isOpen || current.bubbles.isNotEmpty())) {
        val isExpired = current.bubbles.isNotEmpty() && (clock.nowMs() - chatOpenedAt) > EXPIRY_MS
        if (isExpired) {
            // Show dialog — don't restore yet, let user choose
            _uiState.value = current.copy(isOpen = true, showReturnDialog = true)
            return
        }
        if (conversationHistory.isNotEmpty() && current.bubbles.isNotEmpty()) {
            _uiState.value = current.copy(isOpen = true, intentPills = emptyList())
        } else {
            _uiState.value = current.copy(isOpen = true)
        }
        return
    }
    // ... existing reset path ...
    chatOpenedAt = clock.nowMs()
    // ... rest of existing openChat logic, unchanged ...
}
```

Add two new public functions for dialog responses:
```kotlin
fun continueConversation() {
    chatOpenedAt = clock.nowMs()  // reset expiry timer
    _uiState.value = _uiState.value.copy(showReturnDialog = false)
}

fun startFreshConversation() {
    _uiState.value = _uiState.value.copy(showReturnDialog = false)
    chatOpenedAt = clock.nowMs()
    resetToIntentPills()
}
```

---

**Task 5 — `ChatViewModel.kt`: Replace intentPills + followUpChips with persistentPills**

File: `ChatViewModel.kt`

**5a.** Replace `intentPills` with `persistentPills` throughout `ChatViewModel`. In `openChat()` new-area path (line 119 block), change:
```kotlin
// OLD:
intentPills = pillsFor(entryPoint, areaName),
// NEW:
persistentPills = pillsFor(entryPoint, areaName),
intentPills = emptyList(),  // keep field temporarily while UI migrates
```

**5b.** In `tapIntentPill()` (line 161), change:
```kotlin
// OLD:
intentPills = emptyList(),
// NEW:
// Don't clear persistentPills — they replenish after response
```

**5c.** In `sendMessage()` completion block (line 266 area), change `followUpChips` to `persistentPills`:
```kotlin
// OLD:
followUpChips = computeFollowUpChips(query, selectedIntent, currentEngagementLevel, _uiState.value.depthLevel),
// NEW:
persistentPills = computePersistentPills(selectedIntent, query, _uiState.value.depthLevel),
followUpChips = emptyList(),
```

**5d.** Add `computePersistentPills()` function. This replaces `computeFollowUpChips()`:
```kotlin
private fun computePersistentPills(intent: ChatIntent?, lastQuery: String, depthLevel: Int): List<ContextualPill> {
    val areaName = _uiState.value.areaName
    if (depthLevel >= 3) {
        return listOf(
            ContextualPill("New topic", "Let's change topic", ChatIntent.DISCOVER, "🔄"),
        ) + computeContextualPills(lastQuery, areaName).take(2)
    }
    return computeContextualPills(lastQuery, areaName).take(3)
}

private fun computeContextualPills(query: String, areaName: String): List<ContextualPill> {
    val q = query.lowercase()
    return when {
        q.containsAnyWord("safe", "crime", "danger", "night") -> listOf(
            ContextualPill("Safe at night?", "Is it safe at night around here?", ChatIntent.DISCOVER, "🌙"),
            ContextualPill("Areas to avoid", "What areas should I avoid?", ChatIntent.DISCOVER, "⚠️"),
        )
        q.containsAnyWord("food", "eat", "restaurant", "drink") -> listOf(
            ContextualPill("Best time to go", "What's the best time to visit?", ChatIntent.HUNGRY, "⏰"),
            ContextualPill("Veggie options?", "Are there vegetarian options nearby?", ChatIntent.HUNGRY, "🥗"),
        )
        q.containsAnyWord("history", "historic", "built", "founded") -> listOf(
            ContextualPill("Tell me more", "Tell me more about the history here", ChatIntent.DISCOVER, "📖"),
            ContextualPill("Famous events?", "Any famous events happened here?", ChatIntent.DISCOVER, "🏛️"),
        )
        else -> listOf(
            ContextualPill("What's nearby?", "What else is nearby worth seeing?", ChatIntent.DISCOVER, "📍"),
            ContextualPill("Surprise me", "Surprise me with something unexpected in $areaName", ChatIntent.SURPRISE, "🎲"),
        )
    }
}
```

**5e.** In `resetToIntentPills()`, change:
```kotlin
// OLD:
intentPills = pillsFor(currentEntryPoint, areaName),
// NEW:
persistentPills = pillsFor(currentEntryPoint, areaName),
intentPills = emptyList(),
```

---

**Task 6 — `ChatViewModel.kt`: Accumulate POI cards (don't clear per message)**

File: `ChatViewModel.kt`

In `sendMessage()` (line 210), remove the per-message clear:
```kotlin
// REMOVE this line:
poiCards = emptyList(),
```

In the `isComplete` handler (line 275 area), change the `poiCards` update to accumulate:
```kotlin
// OLD:
poiCards = response.pois.ifEmpty { parsedCards.toList() },
// NEW:
val newCards = response.pois.ifEmpty { parsedCards.toList() }
val existingIds = _uiState.value.poiCards.map { it.id }.toSet()
val deduped = newCards.filter { it.id !in existingIds }
poiCards = _uiState.value.poiCards + deduped,
```

In `resetToIntentPills()`, keep the existing `poiCards = emptyList()` — cards should clear when conversation resets.

In `openChat()` new-area path (line 119 block), ensure `poiCards = emptyList()` is included in the reset.

---

**Task 7 — `ChatOverlay.kt`: Add "Show on Map" callback to `ChatPoiMiniCard`**

File: `ChatOverlay.kt`

**7a.** Add `onShowOnMap: (ChatPoiCard) -> Unit = {}` parameter to `ChatOverlay` composable (after `onPoiCardClick`).

**7b.** Pass it down to `ChatPoiMiniCard` via the existing `items(chatState.poiCards)` block:
```kotlin
items(chatState.poiCards, key = { it.id }) { card ->
    ChatPoiMiniCard(
        card = card,
        isSaved = card.id in chatState.savedPoiIds,
        onSave = { viewModel.savePoi(card, chatState.areaName) },
        onUnsave = { viewModel.unsavePoi(card.id) },
        onDirections = { ... },
        onClick = { onPoiCardClick(card) },
        onShowOnMap = { onShowOnMap(card) },  // NEW
    )
}
```

**7c.** In `ChatPoiMiniCard` composable, add a "📍 Show on Map" chip in the action row alongside the existing Directions chip:
```kotlin
// Add after the Directions chip:
SuggestionChip(
    onClick = onShowOnMap,
    label = { Text("📍 Show on Map", fontSize = 11.sp) },
    colors = SuggestionChipDefaults.suggestionChipColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ),
)
```

---

**Task 8 — `ChatOverlay.kt`: Replace EmptyState pills + followUpChips FlowRow with persistent pill rail**

File: `ChatOverlay.kt`

**8a.** Remove the `EmptyState` pill-rendering section (currently inside `LazyColumn` when `bubbles.isEmpty() && intentPills.isNotEmpty()`). Replace with a simpler `EmptyState` that shows only the greeting text:
```kotlin
// EmptyState now shows text only — pills are in persistent rail below
if (chatState.bubbles.isEmpty() && chatState.persistentPills.isEmpty()) {
    item(key = "empty_state") {
        EmptyStateText(areaName = chatState.areaName)
    }
} else if (chatState.bubbles.isEmpty()) {
    item(key = "empty_prompt") {
        Text(
            "Tap a suggestion below to start exploring",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        )
    }
}
```

**8b.** Remove the existing `FlowRow` follow-up chips section (lines 228–255).

**8c.** Add the persistent pill rail between the `LazyColumn` and `ChatInputBar`:
```kotlin
// Persistent pill rail — always visible when pills exist
if (chatState.persistentPills.isNotEmpty() && !chatState.isStreaming) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chatState.persistentPills) { pill ->
            SuggestionChip(
                onClick = { viewModel.tapIntentPill(pill) },
                label = { Text("${pill.emoji} ${pill.label}", fontSize = 13.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}
```

Note: `tapIntentPill()` currently guards with `if (isIntentSelected) return`. This guard must be removed or relaxed — persistent pills should be tappable at any depth, not just on first tap. Update `tapIntentPill()` in `ChatViewModel` to remove the `isIntentSelected` guard, OR introduce a `tapPersistentPill(pill)` that always sends (no guard):
```kotlin
fun tapPersistentPill(pill: ContextualPill) {
    // No isIntentSelected guard — persistent pills always work
    if (_uiState.value.isStreaming) return
    if (selectedIntent == null) selectedIntent = pill.intent
    viewModelScope.launch {
        // same body as tapIntentPill, minus the isIntentSelected guard
        ...
    }
}
```
Use `tapPersistentPill` in the persistent rail and keep `tapIntentPill` for legacy entry (or consolidate).

**8d.** Move POI card section out of `LazyColumn` to a separate horizontal `LazyRow` section between messages and the pill rail:

Current layout in `ChatOverlay`:
```
LazyColumn (bubbles + skeletons + cards)
FlowRow (follow-up chips)  <-- REMOVE
ChatInputBar
```

New layout:
```
LazyColumn (bubbles + skeletons only — no cards inside)
LazyRow (accumulated POI cards — visible when poiCards.isNotEmpty())  <-- NEW
LazyRow (persistent pill rail)  <-- NEW
ChatInputBar
```

Remove POI card rendering from inside `LazyColumn`. Add after the `LazyColumn`:
```kotlin
// POI card rail — accumulates across turns
if (chatState.poiCards.isNotEmpty()) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chatState.poiCards, key = { it.id }) { card ->
            ChatPoiMiniCard(
                card = card,
                isSaved = card.id in chatState.savedPoiIds,
                onSave = { viewModel.savePoi(card, chatState.areaName) },
                onUnsave = { viewModel.unsavePoi(card.id) },
                onDirections = { ... },
                onClick = { onPoiCardClick(card) },
                onShowOnMap = { onShowOnMap(card) },
            )
        }
    }
}
```

Also remove skeleton shimmer items from `LazyColumn` if they were associated with cards. Keep skeleton shimmer as a standalone item in `LazyColumn` after the last bubble, indicating AI is responding.

---

**Task 9 — `ChatOverlay.kt`: Add return-dialog for expired conversations**

File: `ChatOverlay.kt`

Inside `ModalBottomSheet` content (after header, before `LazyColumn`), add:
```kotlin
if (chatState.showReturnDialog) {
    AlertDialog(
        onDismissRequest = { viewModel.continueConversation() },
        title = { Text("Welcome back!") },
        text = { Text("Your earlier conversation has been paused. Pick up where you left off, or start fresh?") },
        confirmButton = {
            TextButton(onClick = { viewModel.continueConversation() }) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.startFreshConversation() }) { Text("Start Fresh") }
        },
    )
}
```

---

**Task 10 — `MapScreen.kt`: Wire `onShowOnMap` callback**

File: `MapScreen.kt` (the composable that renders `ChatOverlay`)

Find the `ChatOverlay(...)` call and add the `onShowOnMap` lambda:
```kotlin
ChatOverlay(
    viewModel = chatViewModel,
    chatState = chatUiState,
    onDismiss = { chatViewModel.closeChat() },
    onNavigateToMaps = { lat, lng, name -> ... },
    onDirectionsFailed = { ... },
    onPoiCardClick = { card -> /* expand detail */ },
    onShowOnMap = { card ->
        chatViewModel.closeChat()
        mapViewModel.selectAndFlyToCoords(card.lat, card.lng, card.name)
    },
)
```

In `MapViewModel`, add or verify the `selectAndFlyToCoords` function exists. If not, add:
```kotlin
fun selectAndFlyToCoords(lat: Double, lng: Double, name: String) {
    // Set fly-to target that the map camera observes
    _uiState.value = _uiState.value.copy(
        flyToTarget = FlyToTarget(lat, lng, name)
    )
}
```
If `flyToTarget` doesn't exist in `MapUiState`, add it (nullable `data class FlyToTarget(val lat: Double, val lng: Double, val name: String)`). The map composable already observes `mapUiState` for camera changes — find the existing camera control pattern and follow it.

---

### Acceptance Criteria

**AC1 — Map-aware chat (Bug: map-chat mismatch)**

Given: User is on map showing 8 POIs (e.g. "Brick Lane", "Beigel Bake", "Rivington St")
When: User opens chat and asks "What should I eat?"
Then: AI response mentions only places from the visible map POI set (e.g. "Beigel Bake") — NOT random places not on the map
And: Those places appear as POI cards in the card rail with correct coordinates matching the map pins

**AC2 — Persistent pill rail (Bug: pills vanish after first tap)**

Given: User taps any intent pill (e.g. "Best food right now")
When: AI response finishes streaming
Then: A new set of contextual pills appears above the input bar (e.g. "Veggie options?", "What's nearby?")
And: Tapping a pill sends it as a message and new pills appear after that response too
And: Pills are visible at all conversation depths (depthLevel 1, 2, 3+)
And: At depthLevel >= 3, one pill is always "🔄 New topic"

**AC3 — POI card accumulation (Bug: follow-up POI cards missing)**

Given: User has a 3-turn conversation that produces cards in turns 1 and 3
When: User is in turn 3
Then: Card rail shows all POI cards from turns 1 AND 3 (accumulated, deduped by id)
And: Scrolling the card rail left shows earlier cards

**AC4 — Show on Map (new feature)**

Given: A POI card is visible in the chat card rail
When: User taps "📍 Show on Map"
Then: Chat overlay closes
And: Map camera flies to that POI's coordinates
And: That POI's floating card is shown/highlighted on the map

**AC5 — Always ends with question (Bug: AI doesn't always end with question)**

Given: User sends any message in chat
When: AI response is fully received
Then: The prose response always ends with a "?" character
And: This holds for first response AND all follow-up turns

**AC6 — Conversation expiry (Bug: stale conversation on reopen)**

Given: User had a chat conversation in "Shoreditch" and left the screen
When: User returns to Shoreditch area chat more than 30 minutes later
Then: A dialog appears: "Welcome back! Pick up where you left off, or start fresh?"
And: Tapping "Continue" restores the previous conversation and resets the 30-min timer
And: Tapping "Start Fresh" resets to the default intent pills with a clean slate

**AC7 — Prompt contract (all follow-up turns produce cards)**

Given: Conversation has had 2 prior turns
When: User asks "What's good for history here?"
Then: AI response includes a structured `pois` array in its JSON
And: Each place name mentioned in prose corresponds to a card in the card rail
And: This behavior holds even at depth level 3+

---

## Additional Context

### Dependencies

- None on external libraries — all changes are in-scope Kotlin/Compose code
- `AlertDialog` is from `androidx.compose.material3` — already imported in `ChatOverlay.kt`
- `LazyRow` needs `import androidx.compose.foundation.lazy.LazyRow` and `import androidx.compose.foundation.lazy.items` — verify both are imported (likely already present from other lazy usages)
- `FlyToTarget` may not exist yet in `MapUiState` — check before adding; follow existing camera-control pattern in `MapViewModel`

### Testing Strategy

All tests in `composeApp/src/commonTest/.../ChatViewModelTest.kt` (or equivalent test file).

| Test | What to Verify |
|------|----------------|
| `mapAwarePrompt_injectsAllSessionPois` | Call `tapIntentPill()`, check `systemContextForTest` contains all POI names from `sessionPois`, not just top 5 |
| `reinforcedQuery_containsPoisAndQuestion` | Mock `aiProvider.streamChatResponse`, capture the `query` arg, assert it contains POI names and "LAST SENTENCE ENDS WITH '?'" |
| `poiCards_accumulateAcrossTurns` | Send 2 messages both returning POIs; assert `uiState.poiCards.size == turn1Cards + turn2Cards` (deduped) |
| `poiCards_clearOnReset` | After accumulation, call `resetToIntentPills()`; assert `uiState.poiCards.isEmpty()` |
| `openChat_sameArea_expiry_showsReturnDialog` | Set `chatOpenedAt` to `now - 31 minutes`, call `openChat()` same area with bubbles; assert `uiState.showReturnDialog == true` |
| `continueConversation_resetsTimer` | After expiry dialog, call `continueConversation()`; assert `uiState.showReturnDialog == false` and conversation preserved |
| `startFreshConversation_clearsHistory` | After expiry dialog, call `startFreshConversation()`; assert `uiState.bubbles.isEmpty()` and `uiState.poiCards.isEmpty()` |
| `persistentPills_replenishAfterResponse` | After AI response completes, assert `uiState.persistentPills.isNotEmpty()` |
| `persistentPills_depthGate_includesNewTopic` | At `depthLevel >= 3` after response, assert "New topic" pill present in `persistentPills` |

### Notes

- **`isIntentSelected` guard**: Currently prevents re-tapping intent pills after conversation starts. When introducing `tapPersistentPill`, bypass this guard entirely — persistent pills MUST work at any depth. Keep `tapIntentPill` with the guard only for the legacy `EmptyState` path (or remove `isIntentSelected` entirely if `EmptyState` is simplified).
- **Skeleton shimmer**: Currently shown inside `LazyColumn` when `showSkeletons == true`. After moving card rail out of `LazyColumn`, the skeleton section (3 placeholders while loading) should remain inside the `LazyColumn` as a loading indicator below the last AI bubble.
- **Card width**: `ChatPoiMiniCard` is currently rendered as full-width vertical item. For `LazyRow`, give it a fixed width (`width = 220.dp` or similar) to make it a compact horizontal card. Check `ChatPoiMiniCard` composable for any `fillMaxWidth()` modifiers and replace with `width(220.dp)`.
- **Prototype reference**: `_bmad-output/brainstorming/prototype-chat-redesign.html` — side-by-side design showing persistent pill rail, card rail, and redesigned layout. Open in browser before implementing UI.
- **`buildChatSystemContext` is only called from `tapIntentPill()`** — it runs once at conversation start. The per-turn `reinforcedQuery` suffix is the mechanism for ongoing enforcement. Both must be correct for full coverage.
- **Do not change the JSON response schema** (`ChatResponse`, `ChatPoiCard`) — the field names (`n`, `t`, `lat`, `lng`, `w`, `img`) are already in the prompt and changing them would break parsing.
