---
title: 'AI Detail Page'
slug: 'ai-detail-page'
created: '2026-03-15'
status: 'done'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Coroutines/Flow', 'Turbine', 'kotlin.test']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt'
files_to_delete:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt'
code_patterns:
  - 'Compose LazyColumn with sticky footer (Column { LazyColumn(weight(1f)) + ChatInputBar })'
  - 'PlatformBackHandler for every dismissible overlay'
  - 'MapUiState.Ready cast guard in MapViewModel (as? MapUiState.Ready ?: return)'
  - 'ChatViewModel openChat() resets all state and sets isOpen=true'
  - 'tapIntentPill() injects system context then calls sendMessage() — required for contextual AI responses'
test_patterns:
  - 'commonTest, FakeAreaIntelligenceProvider + FakeClock + FakeSavedPoiRepository + FakeLocaleProvider'
  - 'app.cash.turbine for Flow testing, kotlin.test assertions'
  - 'runTest with UnconfinedTestDispatcher'
---

# Tech-Spec: AI Detail Page

**Created:** 2026-03-15

## Overview

### Problem Statement

`ExpandablePoiCard` is a static detail card. Tapping "Ask AI" opens `ChatOverlay` as a second modal on top — two overlapping layers with conflicting UX (bug #39). The POI card context is lost when entering chat, and the persistent POI card carousel conflicts with the chat input.

### Solution

Replace `ExpandablePoiCard` with a new `AiDetailPage` composable — a full-screen `LazyColumn` where the POI header card is item 0, followed by a pre-seeded AI intro (fired via `LaunchedEffect(poi) { chatViewModel.openChat(..., ChatEntryPoint.PoiCard(poi)) }`), then inline chat (bubbles + pills + input bar). One screen = card + AI + chat. Solves #39 by eliminating the two-modal pattern.

### Scope

**In Scope:**
- New `AiDetailPage.kt` in `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/`
- `PoiDetailHeader` sub-composable: 160dp hero image, name, type, vibe chip, status badge + buzz meter, rating, price range, insight, user note (display only)
- Header CTAs: Save/Unsave, Directions, Show on Map (no "Ask AI" — already in AI)
- Pre-seed AI intro on open: `LaunchedEffect(poi)` → `chatViewModel.openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.PoiCard(poi))`
- Chat always starts fresh per POI tap (reset history + framing hint for specific POI)
- Full inline chat: bubbles, skeleton, POI card rail, persistent pills, `ChatInputBar` sticky footer
- Wire into `MapScreen.kt` replacing `ExpandablePoiCard` block (lines 444–499)
- `PlatformBackHandler` + scrim tap-to-dismiss
- Show on Map CTA: `viewModel.clearPoiSelection()` + `viewModel.flyToCoords(lat, lng)`
- Delete `ExpandablePoiCard.kt` once replaced

**Out of Scope:**
- Topic dividers, AI itinerary builder, map preview in chat
- Per-POI persisted chat history (fresh chat on each open)
- Sibling pager (carousel handles multi-POI selection upstream)
- Notes editing on detail page (display only)
- `AnimatedVisibility` expandable sections (all content visible upfront)
- Share button (deferred — snackbar "coming soon" if needed)

## Context for Development

### Codebase Patterns

- **Architecture:** Compose Multiplatform, commonMain only. No platform-specific code needed.
- **State ownership:** `MapViewModel` owns `selectedPoi: POI?` (seals as `MapUiState.Ready`). `ChatViewModel` owns all chat state. Both injected via Koin in `MapScreen.kt`.
- **`AiDetailPage` visibility:** Controlled by `state.selectedPoi != null` in `MapScreen.kt` — same gate as current `ExpandablePoiCard`. No new state field needed.
- **`ChatOverlay` conflict guard:** `ChatOverlay` currently shows when `chatState.isOpen`. Must add guard: `chatState.isOpen && state.selectedPoi == null` — prevents `ChatOverlay` modal from appearing while `AiDetailPage` is open.
- **`PlatformBackHandler`:** Every dismissible overlay must use `PlatformBackHandler(enabled = <condition>) { <dismiss> }`. Location: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/PlatformBackHandler.kt`.
- **Pre-seeded AI intro — CRITICAL FLOW:** `openChat()` alone does NOT auto-send. `tapIntentPill()` is what injects the big system context prompt + calls `sendMessage()`. Without it, AI has no area/POI context and gives generic answers. `AiDetailPage` must call `chatViewModel.openChatForPoi()` — a new ViewModel method (see Technical Decision #3) that does the full setup + auto-send in one call.
- **`openChat()` same-area guard (lines 89–97 in ChatViewModel):** If `areaName` matches and bubbles exist, it returns early (preserves conversation). `AiDetailPage` must bypass this with `forceReset = true`.
- **`tapIntentPill()` is public** — safe to call from composable via `LaunchedEffect`. Has an `isIntentSelected` guard (idempotent, safe for recomposition).
- **Chat private helpers to make internal:** `ChatBubbleItem`, `ChatInputBar`, `SkeletonSection`, `BlinkingCursor`, `poiThumbnailUrl()`, `poiTypeEmoji()` in `ChatOverlay.kt` — change `private` → `internal`.
- **`LiveStatusBadge` and `BuzzMeter`:** Private in `ExpandablePoiCard.kt` (being deleted). Copy inline into `AiDetailPage.kt` — do NOT create a shared file for just two components.
- **Layout pattern:** `Column { LazyColumn(Modifier.weight(1f)) + ChatInputBar }` — same pattern as `ChatOverlay` but without `ModalBottomSheet` wrapper. Input bar stays pinned at bottom.
- **`selectPoiWithImageResolve()`:** Called in `MapScreen.kt` when user taps a carousel card (resolves Wikipedia image). `AiDetailPage` will show `poi.imageUrl` once resolved — no additional work needed.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Wire-in. Replace ExpandablePoiCard block (lines 444–499), scrim (424–440). Add selectedPoi == null guard to ChatOverlay. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt` | Donor for `PoiDetailHeader` UI (image hero, `LiveStatusBadge`, `BuzzMeter`, action chips). DELETE after. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt` | Make `ChatBubbleItem`, `ChatInputBar`, `SkeletonSection`, `BlinkingCursor`, `poiThumbnailUrl`, `poiTypeEmoji` internal. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | Add `forceReset: Boolean = false` to `openChat()`. Add `openChatForPoi()`. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt` | `ChatUiState`, `ChatBubble`, `ChatPoiCard` — read-only reference. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt` | `ChatEntryPoint.PoiCard(poi)` — already exists, use as-is. |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Fields: name, type, vibe, liveStatus, rating, priceRange, imageUrl, userNote, insight, hours, lat, lng. |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/PlatformBackHandler.kt` | Back handler — must use for AiDetailPage dismiss. |
| `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt` | Test patterns: FakeAreaIntelligenceProvider, FakeClock, FakeSavedPoiRepository, FakeLocaleProvider, UnconfinedTestDispatcher. |

### Technical Decisions

1. **`openChat()` force-reset param:** Add `forceReset: Boolean = false`. When `true`, skip the same-area preservation guard (lines 89–97) and fall through to full reset. Default `false` preserves all existing callers unchanged.

2. **`ChatOverlay` conflict guard:** In `MapScreen.kt`, change condition from `if (chatState.isOpen)` to `if (chatState.isOpen && state.selectedPoi == null)`. One-line fix. Prevents both overlays being open simultaneously.

3. **`openChatForPoi()` — new ViewModel method:** Encapsulates the full "open for a specific POI + auto-send AI intro" flow:
   ```
   fun openChatForPoi(poi: POI, areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?) {
       openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.PoiCard(poi), forceReset = true)
       // Auto-tap a synthetic "Tell me about this place" pill so tapIntentPill()
       // injects system context and fires the first AI message immediately.
       tapIntentPill(ContextualPill(
           label = poi.name,
           message = "Tell me about ${poi.name}.",
           intent = ChatIntent.DISCOVER,
           emoji = "📍",
       ))
   }
   ```
   Called from `AiDetailPage` via `LaunchedEffect(poi.savedId)`.

4. **Header CTAs (3 only):** Save/Unsave, Directions, Show on Map. No "Ask AI" chip — already in AI. No Share chip (deferred).

5. **Show on Map:** `onShowOnMap: () -> Unit` lambda in `AiDetailPage`. In `MapScreen.kt`: `viewModel.clearPoiSelection(); if (poi.latitude != null && poi.longitude != null) viewModel.flyToCoords(poi.latitude, poi.longitude)`.

6. **No sibling pager in `AiDetailPage`:** Single POI only. Carousel upstream handles switching (user swipes carousel → different `selectedPoi` → `AiDetailPage` remounts with new POI via `LaunchedEffect(poi.savedId)`).

7. **`isOpen` in `AiDetailPage` context:** `openChatForPoi()` calls `openChat()` which sets `isOpen = true`. The `ChatOverlay` conflict guard (Decision #2) prevents `ChatOverlay` from appearing. When `AiDetailPage` is dismissed (`clearPoiSelection()`), `ChatOverlay` stays suppressed because `chatState.isOpen && state.selectedPoi == null` only becomes true after the chat was explicitly opened from the search bar. The `closeChat()` call is NOT needed on dismiss — chat state is preserved so re-opening the same POI shows fresh chat (forceReset handles it).

## Implementation Plan

### Tasks

- [x] **T1: Add `forceReset` to `ChatViewModel.openChat()` + add `openChatForPoi()`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action 1: Add `forceReset: Boolean = false` parameter to `fun openChat(...)`. Wrap the same-area preservation block (lines 89–97) in `if (!forceReset)` so it is skipped when `forceReset = true`. All existing callers keep default `false` — no other changes needed.
  - Action 2: Add new public method `fun openChatForPoi(poi: POI, areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?)` that calls `openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.PoiCard(poi), forceReset = true)` then immediately calls `tapIntentPill(ContextualPill(label = poi.name, message = "Tell me about ${poi.name}.", intent = ChatIntent.DISCOVER, emoji = "📍"))`.
  - Notes: `tapIntentPill()` has an `isIntentSelected` guard — it only fires once per `openChat()` cycle. After `openChat(forceReset=true)` resets `isIntentSelected = false`, the immediate `tapIntentPill()` call in `openChatForPoi()` will always fire correctly.

- [x] **T2: Make chat helpers `internal` in `ChatOverlay.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt`
  - Action: Change visibility from `private` to `internal` on: `ChatBubbleItem`, `ChatInputBar`, `SkeletonSection`, `BlinkingCursor`, `poiThumbnailUrl()`, `poiTypeEmoji()`.
  - Notes: No logic changes — visibility only. `AiDetailPage.kt` (same package `com.harazone.ui.map`) can access `internal` declarations directly.

- [x] **T3: Create `AiDetailPage.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt` (new file)
  - Action: Create the composable with this exact structure:

  ```
  @Composable
  fun AiDetailPage(
      poi: POI,
      activeVibe: Vibe?,
      chatViewModel: ChatViewModel,
      chatState: ChatUiState,
      areaName: String,
      allPois: List<POI>,
      activeDynamicVibe: DynamicVibe?,
      isSaved: Boolean,
      onSave: () -> Unit,
      onUnsave: () -> Unit,
      onDirectionsClick: (Double, Double, String) -> Unit,
      onShowOnMap: () -> Unit,
      onDismiss: () -> Unit,
      onNavigateToMaps: (Double, Double, String) -> Boolean,
      onDirectionsFailed: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

  **Layout (outer):** `Box(modifier)` containing:
  1. `Column(Modifier.fillMaxSize()) { LazyColumn(Modifier.weight(1f)) + ChatInputBar(...) }`
  2. `PlatformBackHandler(enabled = true) { onDismiss() }`

  **`LazyColumn` items (in order):**
  - `item("header")` → `PoiDetailHeader(poi, isSaved, onSave, onUnsave, onDirectionsClick, onShowOnMap, onDismiss)`
  - `items(chatState.bubbles, key = { it.id })` → `ChatBubbleItem(bubble, onRetry = { chatViewModel.retryLastMessage() })`
  - `if (chatState.showSkeletons)` → `item("skeletons") { SkeletonSection(3) }`
  - `if (chatState.poiCards.isNotEmpty())` → `item("poi_cards") { LazyRow with ChatPoiMiniCard items }` — same pattern as `ChatOverlay`
  - `if (chatState.persistentPills.isNotEmpty() && !chatState.isStreaming)` → `item("pills") { LazyRow with SuggestionChip items }` — same pattern as `ChatOverlay`, same pill label lookup table

  **Auto-scroll:** `LaunchedEffect(chatState.bubbles.size, chatState.isStreaming)` → `listState.animateScrollToItem(lastIndex)` — same as `ChatOverlay`.

  **Pre-seed:** `LaunchedEffect(poi.savedId) { chatViewModel.openChatForPoi(poi, areaName, allPois, activeDynamicVibe) }`

  **`PoiDetailHeader` sub-composable** (private inside `AiDetailPage.kt`):
  - Same hero image block as `ExpandablePoiCard` (160dp, gradient fallback, `AsyncImage` if `poi.imageUrl != null`)
  - Close (X) `IconButton` top-end corner on the image (calls `onDismiss`)
  - Below image in `Column(Modifier.padding(16.dp))`:
    - `Text(poi.name)` — `titleMedium`, white
    - `Text(poi.type.replaceFirstChar { it.uppercaseChar() })` — `labelMedium`, white 60%
    - Vibe chip: `Text(poi.vibe)` in a small pill with vibe color background
    - `Row` with `Icon(Star)` + rating text (if `poi.rating != null`) + `LiveStatusBadge` + `BuzzMeter` (if `poi.liveStatus != null`)
    - `Text(poi.priceRange)` in meta row (if not null), `labelSmall`, white 60%
    - `Text(poi.insight)` if not empty (same shimmer placeholders if empty as in `ExpandablePoiCard`)
    - User note block (if `poi.userNote != null`) — same style as `ExpandablePoiCard` (pencil emoji + note text, subtle background)
    - `Spacer(12.dp)`
    - `FlowRow` of 3 `AssistChip`s: Save/Unsave, Directions (hidden if no lat/lng), Show on Map
  - `LiveStatusBadge` and `BuzzMeter`: copy private implementations from `ExpandablePoiCard.kt` verbatim into this file.

  **`ChatPoiMiniCard` in LazyRow:** reuse `ChatPoiMiniCard` — but that composable is private in `ChatOverlay.kt`. Make it `internal` as part of T2, OR inline a simplified version here. Preferred: make it `internal` in T2 and reuse.

  - Notes: Add `ChatPoiMiniCard` to the list of helpers to make `internal` in T2.

- [x] **T4: Wire `AiDetailPage` into `MapScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action 1 — Replace scrim (lines 424–440): Keep the `Box` scrim but update `onDismiss` callback — remove the `returnToChat` / `returnToSaves` restoration logic (no longer needed; `AiDetailPage` subsumes that flow). Simplified: `viewModel.clearPoiSelection()`.
  - Action 2 — Replace ExpandablePoiCard block (lines 444–499): Replace with:
    ```kotlin
    if (state.selectedPoi != null) {
        AiDetailPage(
            poi = state.selectedPoi,
            activeVibe = null,
            chatViewModel = chatViewModel,
            chatState = chatState,
            areaName = state.areaName,
            allPois = state.allDiscoveredPois,
            activeDynamicVibe = state.activeDynamicVibe,
            isSaved = state.selectedPoi.savedId in state.savedPoiIds,
            onSave = { viewModel.savePoi(state.selectedPoi, state.areaName) },
            onUnsave = { viewModel.unsavePoi(state.selectedPoi) },
            onDirectionsClick = { lat, lon, name ->
                val handled = onNavigateToMaps(lat, lon, name)
                if (!handled) coroutineScope.launch { snackbarHostState.showSnackbar(noMapsAppMessage) }
            },
            onShowOnMap = {
                viewModel.clearPoiSelection()
                state.selectedPoi.latitude?.let { lat ->
                    state.selectedPoi.longitude?.let { lng -> viewModel.flyToCoords(lat, lng) }
                }
            },
            onDismiss = { viewModel.clearPoiSelection() },
            onNavigateToMaps = onNavigateToMaps,
            onDirectionsFailed = { coroutineScope.launch { snackbarHostState.showSnackbar(noMapsAppMessage) } },
            modifier = Modifier.fillMaxSize().padding(top = statusBarPadding + 56.dp, bottom = navBarPadding + 56.dp),
        )
    }
    ```
  - Action 3 — ChatOverlay conflict guard: Find the condition that gates `ChatOverlay` (currently `if (chatState.isOpen)` or inside a block that checks `chatState.isOpen`). Add `&& state.selectedPoi == null` to that condition.
  - Action 4 — Remove now-unused `returnToChat` and `returnToSaves` ref arrays if they are no longer referenced anywhere after the above changes. Also remove the `PlatformBackHandler` block that previously called `chatViewModel.reopenChat()` after POI dismiss (line ~406–413) — that logic is gone.
  - Action 5 — Remove `import com.harazone.ui.map.components.ExpandablePoiCard`.
  - Notes: `state.selectedPoi` is smart-cast inside the `if` block — use `state.selectedPoi` directly without `!!`.

- [x] **T5: Delete `ExpandablePoiCard.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt`
  - Action: Delete the file. Verify no other file imports it (should be zero after T4).

- [x] **T6: Tests — `ChatViewModel.forceReset` + `openChatForPoi`**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt`
  - Test 1: `openChatForPoi resets conversation even when same area has existing bubbles` — open chat in area "Tokyo", send a message, then call `openChatForPoi(testPoi, "Tokyo", emptyList(), null)`. Assert `uiState.value.bubbles` is non-empty (AI intro seeded) and does NOT contain the previous bubble content. Verifies `forceReset = true` bypasses the same-area guard.
  - Test 2: `openChatForPoi immediately starts streaming AI intro` — call `openChatForPoi(testPoi, "Test Area", emptyList(), null)`. Assert `uiState.value.isStreaming == true` or bubbles contain an AI bubble. Verifies auto-seed fires without user interaction.
  - Test 3: `openChat with forceReset=true ignores same-area preservation` — open chat in "Paris", send a message, call `openChat("Paris", emptyList(), null, forceReset = true)`. Assert `uiState.value.bubbles.isEmpty()` — state was reset despite same area.
  - Use same `FakeAreaIntelligenceProvider` pattern with seeded `chatTokens` for streaming tests.

### Acceptance Criteria

- [ ] **AC1:** Given a POI is visible in the bottom carousel, when the user taps its card, then `AiDetailPage` opens full-screen showing the POI header card (image/gradient, name, type, vibe, status, rating, price) with Save/Directions/Show on Map CTAs.

- [ ] **AC2:** Given `AiDetailPage` opens, when the composable is first composed, then the AI begins streaming a contextual intro about the POI within ~3s (no user action required — auto-seeded).

- [ ] **AC3:** Given the AI intro has loaded, when the user taps a persistent pill or types a message and sends, then the AI responds inline in the same `LazyColumn` below the header card and the list auto-scrolls to the latest bubble.

- [ ] **AC4:** Given a POI is unsaved, when the user taps the Save CTA in the header, then the bookmark icon fills and the POI is saved (optimistic update, same as existing save flow).

- [ ] **AC5:** Given a POI has lat/lng, when the user taps Directions in the header, then the maps app opens (or the "no maps app" snackbar shows if unavailable).

- [ ] **AC6:** Given `AiDetailPage` is open, when the user taps "Show on Map", then the detail page closes and the map camera flies to the POI's coordinates.

- [ ] **AC7:** Given `AiDetailPage` is open on Android, when the user presses the hardware back button, then the detail page dismisses and the map is visible again (not app exit).

- [ ] **AC8:** Given `AiDetailPage` is open, when the user taps the background scrim outside the card area, then the detail page dismisses.

- [ ] **AC9:** Given `AiDetailPage` is open, when the user had previously opened `ChatOverlay` from the search bar, then `ChatOverlay` does NOT appear on top of `AiDetailPage` (conflict guard active).

- [ ] **AC10:** Given the user chatted about POI A, when they dismiss and tap POI B (different POI, same area), then `AiDetailPage` opens for POI B with a fresh chat about B — no messages from POI A conversation are visible.

- [ ] **AC11:** Given a POI has no `imageUrl`, when `AiDetailPage` opens, then the header shows the vibe-colored gradient fallback without crash or blank space.

- [ ] **AC12:** Given a POI has no `latitude`/`longitude`, when `AiDetailPage` opens, then the Directions CTA is hidden (not shown in the header).

- [ ] **AC13:** Given a POI has a `userNote`, when `AiDetailPage` opens, then the note is displayed in the header card with the pencil emoji prefix and subtle background.

- [ ] **AC14:** Given `AiDetailPage` is open, when the user taps the X close button on the hero image, then the detail page dismisses (same as back button / scrim).

## Additional Context

### Dependencies

- No new libraries needed.
- `ChatViewModel` already handles AI streaming, pills, POI card rail, save/unsave, retry.
- `MapViewModel.flyToCoords()` and `clearPoiSelection()` already exist.
- `ChatEntryPoint.PoiCard(poi)` already exists with correct framing hint wiring.
- `PlatformBackHandler` already implemented for Android and iOS (no-op).

### Testing Strategy

**Unit tests (T6 — `ChatViewModelTest.kt`):**
- `forceReset` bypasses same-area guard
- `openChatForPoi` auto-seeds streaming immediately
- `openChat` with `forceReset=true` clears existing bubbles regardless of area

**Manual smoke test (after implementation):**
1. Launch app, let 3 pins load, tap a carousel card → verify `AiDetailPage` opens with correct header
2. Wait for AI intro to stream in (~3s) → verify contextual content about the tapped POI
3. Tap a pill → verify follow-up response appears inline
4. Tap Save → verify bookmark fills; tap again → verify unsave
5. Tap Directions → verify maps app opens (or snackbar)
6. Tap Show on Map → verify detail page closes, map flies to pin
7. Press Android back → verify detail page dismisses
8. Tap scrim → verify detail page dismisses
9. Open search bar chat after dismissing → verify `ChatOverlay` opens normally (not blocked)
10. Tap POI A, chat, dismiss, tap POI B → verify fresh chat for B
11. Tap POI with no image → verify gradient fallback, no crash
12. iOS: verify detail page layout is correct, AI intro fires, back swipe works

### Notes

- **High-risk: `returnToChat` / `returnToSaves` removal (T4 Action 4).** The current `MapScreen.kt` has `returnToChat` and `returnToSaves` boolean refs that manage returning to `ChatOverlay` or `SavedPlacesScreen` after POI card dismiss. `AiDetailPage` subsumes the "from chat" flow. Carefully audit all references before removing — `returnToSaves` may still be needed for the `SavedPlacesScreen` → detail page flow.
- **Brainstorm source:** `_bmad-output/brainstorming/brainstorming-session-2026-03-14-001.md` ideas #28–#32
- **Prototype reference:** `_bmad-output/brainstorming/prototype-tester-release-3features.html` tab 4
- **Solves:** Open bug #39 (Chat POI Cards + Map Cards UX conflict), Feature #44
- **Future (out of scope):** Topic dividers (#33), itinerary builder (#34), map preview in chat (#35), per-POI chat history persistence
