---
title: 'List View Refresh'
slug: 'list-view-refresh'
created: '2026-03-15'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Coil3', 'Material3']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
code_patterns:
  - 'chat-list scrollable list of cards: full-width background image + text overlay + trailing icon buttons'
  - 'dynamicVibes chip strip (VibeOrb pattern)'
  - 'viewModel.switchDynamicVibe / savePoi / unsavePoi'
  - 'ChatEntryPoint.PoiCard for chat'
  - 'PlatformBackHandler for list view dismiss'
test_patterns:
  - 'composeTestRule + mock callbacks'
---

# Tech-Spec: List View Refresh

**Created:** 2026-03-15

## Overview

### Problem Statement

The POI list view (toggled via FAB menu) has three issues:
1. Cards show a small 56dp thumbnail — not visually polished enough.
2. Cards have no save, navigate, or chat CTAs — users must exit list view to act on a POI.
3. The vibe chip strip uses the old static `Vibe` enum with `activeVibe = null` — it never mirrors the map's active dynamic vibe filter, creating a stale/confusing UI.

### Solution

Implement the list as a clean **chat-list style** — scrollable list of polished POI cards. Each card: full-width **background image** + dark gradient scrim + text overlay top-left (name, type, insight, rating, status) + 3 **icon-only CTAs** (bookmark, navigate, chat bubble) bottom-right. Replace static-enum chip strip with `dynamicVibes` from state. Add `PlatformBackHandler` to dismiss the list view on Android back press.

### Scope

**In Scope:**
- `POIListView.kt` — card redesign + chip strip replacement + new callback params
- `MapScreen.kt` — updated call site + `PlatformBackHandler` for list view
- Unit/compose tests for all new interactions

**Out of Scope:**
- No new APIs or data model changes
- No status stripe or TextButton labels (icon-only CTAs)
- Any changes to `SavedPlacesScreen` (separate saved list)
- `POIListViewPreviews.kt` preview updates (nice-to-have, not required)
- Note: `POIListView.kt` is in `commonMain` — changes compile and run on both Android and iOS. This spec targets Android UX but the implementation is cross-platform by nature. iOS-specific polish is a follow-up.

---

## Context for Development

### Codebase Patterns

**Background image card pattern** — adapt from `PoiCarousel.kt`:
```kotlin
Box(
    Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MapFloatingUiDark)  // fallback when imageUrl is null
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "${poi.name}, ${poi.type}" }
) {
    // 1. Background image (only when imageUrl non-null)
    if (poi.imageUrl != null) {
        AsyncImage(model = poi.imageUrl, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    // 2. Dark scrim (always — ensures readability with or without image)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color.Black.copy(0.1f), Color.Black.copy(0.75f))
    )))
    // 3. Content overlay
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Column(Modifier.align(Alignment.TopStart)) { /* name, subtitle, insight, rating/status */ }
        Row(Modifier.align(Alignment.BottomEnd)) { /* icon CTAs */ }
    }
}
```

**PlatformBackHandler** — use existing `PlatformBackHandler` in `commonMain`:
```kotlin
// In MapScreen ReadyContent, alongside existing back handlers:
PlatformBackHandler(enabled = state.showListView) { viewModel.toggleListView() }
```
Check `MapViewModel` for the correct method name to toggle list view off (`showListView = false`).

**Dynamic vibe chip** — `DynamicVibe.label` + `DynamicVibe.icon` for display. Selected = `activeDynamicVibe?.label == vibe.label`. On tap: `onDynamicVibeSelected(vibe)`. Use `MaterialTheme.colorScheme.primary` for selected chip color (not hardcoded hex).

**Rating display** — render `poi.rating` as `"⭐ ${poi.rating}"` (same as `PoiCarousel`). Only show if non-null.

**Live status display** — render `poi.liveStatus` as color-coded dot or label. Use existing `liveStatusToColor(resolveStatus(poi.liveStatus, poi.hours)).toComposeColor()`. Show as small colored dot + text, or omit if null.

**Save/unsave** — `viewModel.savePoi(poi, state.areaName)` / `viewModel.unsavePoi(poi)`. Toggle based on `poi.savedId in savedPoiIds`.

**Navigate** — `onNavigateToMaps(poi.latitude!!, poi.longitude!!, poi.name)` — guard with `poi.latitude != null && poi.longitude != null`. Already wired: `MainActivity → App → MapScreen`.

**Chat guard** — before calling `chatViewModel.openChat()`, do NOT guard on `chatState.isStreaming`. The existing `ExpandablePoiCard` path does guard, but list-view chat taps should open chat immediately (same as FAB chat). The overlay handles streaming state internally.

**POI filtering** — `state.pois` is already filtered by `activeDynamicVibe` in `MapViewModel.switchDynamicVibe()`. **Remove the existing local `filteredPois` logic** from `POIListView`. Chip strip is for display + switching only.

**Empty state when filter active** — when `pois` is empty AND `activeDynamicVibe != null`, show: `"No ${activeDynamicVibe.label} found in this area"`. When `pois` is empty and no filter, show the existing `stringResource(Res.string.poi_list_empty)`.

**Vibe subtitle guard** — only append `" · ${poi.vibe}"` if `poi.vibe.isNotBlank()`. Render as: `if (poi.vibe.isNotBlank()) "${poi.type.cap()} · ${poi.vibe}" else poi.type.cap()`.

**LazyColumn key** — use `poi.savedId` (already stable: `"$name|${lat}|${lng}"`). Do not use `"${it.name}_${it.type}"` (non-unique).

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt` | File to modify — current card + chip strip |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PoiCarousel.kt` | Background image + icon CTA pattern |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Call site to update + PlatformBackHandler location (lines ~229–240) |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | `state.dynamicVibes`, `state.activeDynamicVibe`, `state.areaName`, `state.allDiscoveredPois`, `state.showListView` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/DynamicVibe.kt` | `DynamicVibe(label, icon, poiIds)` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | `imageUrl`, `savedId`, `latitude`, `longitude`, `liveStatus`, `hours`, `rating`, `vibe` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt` | `ChatEntryPoint.PoiCard(poi)` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | `switchDynamicVibe()`, `savePoi()`, `unsavePoi()`, list view toggle method |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/PlatformBackHandler.kt` | `PlatformBackHandler(enabled, onBack)` |

### Technical Decisions

- **Card height: 120dp** — single resolved value. Tall enough for image + 3 lines of text + icon row. Shorter than carousel (148dp) for list density.
- **Background image, simple scrim**: `AsyncImage` + `verticalGradient(Black@0.1f → Black@0.75f)`. No blur, no status stripe, no TextButton labels.
- **Icon-only CTAs with contentDescriptions**: `IconButton` with explicit `contentDescription` for each — "Save"/"Saved", "Navigate to [name]", "Ask AI about [name]".
- **Rating + liveStatus retained**: Both fields are shown in the card — rating as `"⭐ X.X"`, live status as color-coded dot. Not dropped.
- **Chip color**: `MaterialTheme.colorScheme.primary` (not hardcoded hex) for selected chip `selectedContainerColor`.
- **No local filtering**: VM owns filter state. Remove `filteredPois` from `POIListView`.
- **Stable LazyColumn key**: `poi.savedId` replaces `"${it.name}_${it.type}"`.
- **Merged save callback**: `onSaveToggled: () -> Unit` — caller pre-wires save vs unsave; card stays stateless.
- **`poi.latitude != null` guard on navigate**: Hide navigate icon if coords null.
- **Vibe blank guard**: `if (poi.vibe.isNotBlank()) append " · ${poi.vibe}"` else show type only.
- **commonMain scope**: File compiles for both platforms. "Android-only" refers to the UX priority, not build target.

---

## Implementation Plan

### Tasks

- [x] **Task 1: Update `POIListView` function signature**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Remove `activeVibe: Vibe?` and `onVibeSelected: (Vibe) -> Unit`. Add: `dynamicVibes: List<DynamicVibe>`, `activeDynamicVibe: DynamicVibe?`, `onDynamicVibeSelected: (DynamicVibe) -> Unit`, `onSaveTapped: (POI) -> Unit`, `onUnsaveTapped: (POI) -> Unit`, `onNavigateTapped: (POI) -> Unit`, `onChatTapped: (POI) -> Unit`. Remove `filteredPois` local computation — use `pois` directly.
  - Notes: Add imports: `DynamicVibe`, `coil3.compose.AsyncImage`, `androidx.compose.ui.graphics.Brush`, `Icons.Filled.Bookmark`, `Icons.Outlined.BookmarkBorder`, `Icons.Default.Navigation`, `Icons.Default.ChatBubbleOutline`, `androidx.compose.material3.IconButton`. Remove imports: `SubcomposeAsyncImage`, `ImageRequest`, `Size`, `LocalDensity`, `LocalPlatformContext`, `kotlin.math.roundToInt`, `Vibe`, `BorderStroke`.

- [x] **Task 2: Replace static-enum chip strip with dynamic vibes**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Replace `items(Vibe.entries.toList())` with `items(dynamicVibes, key = { it.label })`. Selected state: `vibe.label == activeDynamicVibe?.label`. Label: `"${vibe.icon} ${vibe.label}"`. On click: `onDynamicVibeSelected(vibe)`. Wrap entire `LazyRow` in `if (dynamicVibes.isNotEmpty())`. Use `selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)` and `selectedLabelColor = Color.White`.

- [x] **Task 3: Redesign `PoiListCard` — background image + icon CTAs**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Replace current `Card { Row { thumbnail + Column } }` with:
    ```
    Box(fillMaxWidth, height=120dp, clip=RoundedCornerShape(12dp),
        background=MapFloatingUiDark, clickable=onClick,
        semantics(mergeDescendants=true) { contentDescription = "${poi.name}, ${poi.type}" }
    ) {
        if (poi.imageUrl != null) AsyncImage(contentScale=Crop, fillMaxSize)
        Box(fillMaxSize, background=verticalGradient(Black@0.1f → Black@0.75f))

        Box(fillMaxSize, padding=12dp) {
            Column(Alignment.TopStart) {
                Text(poi.name, titleSmall, white, maxLines=1, ellipsis)
                val subtitle = if (poi.vibe.isNotBlank()) "${poi.type.cap()} · ${poi.vibe}"
                               else poi.type.cap()
                Text(subtitle, labelSmall, white@0.7f)
                if (poi.insight.isNotBlank())
                    Text(poi.insight, bodySmall, white@0.8f, maxLines=1, ellipsis)
                // Rating + live status row
                Row(spacedBy=6.dp) {
                    if (poi.rating != null) Text("⭐ ${poi.rating}", labelSmall, white)
                    if (poi.liveStatus != null) {
                        val statusColor = liveStatusToColor(resolveStatus(poi.liveStatus, poi.hours)).toComposeColor()
                        Box(8dp circle, background=statusColor)
                        Text(poi.liveStatus.cap(), labelSmall, statusColor)
                    }
                }
            }
            Row(Alignment.BottomEnd, spacedBy=4.dp) {
                IconButton(onClick=onSaveToggled,
                    contentDescription = if (isSaved) "Saved" else "Save") {
                    Icon(if (isSaved) Filled.Bookmark else Outlined.BookmarkBorder,
                         tint = if (isSaved) Color(0xFFFFD700) else Color.White)
                }
                if (poi.latitude != null && poi.longitude != null) {
                    IconButton(onClick=onNavigateTapped,
                        contentDescription = "Navigate to ${poi.name}") {
                        Icon(Icons.Default.Navigation, tint=Color.White)
                    }
                }
                IconButton(onClick=onChatTapped,
                    contentDescription = "Ask AI about ${poi.name}") {
                    Icon(Icons.Default.ChatBubbleOutline, tint=Color.White)
                }
            }
        }
    }
    ```
  - Notes: Signature: `(poi, isSaved, onClick, onSaveToggled, onNavigateTapped, onChatTapped)`. `contentDescription` on `IconButton` — pass via `Modifier.semantics { }` or use the `IconButton` overload with `modifier = Modifier.semantics { contentDescription = "..." }`.

- [x] **Task 4: Update `LazyColumn` — stable key + new callbacks**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Change key from `"${it.name}_${it.type}"` to `it.savedId`. Pass new callbacks:
    ```kotlin
    items(pois, key = { it.savedId }) { poi ->
        val isSaved = poi.savedId in savedPoiIds
        PoiListCard(
            poi = poi, isSaved = isSaved,
            onClick = { onPoiClick(poi) },
            onSaveToggled = { if (isSaved) onUnsaveTapped(poi) else onSaveTapped(poi) },
            onNavigateTapped = { onNavigateTapped(poi) },
            onChatTapped = { onChatTapped(poi) },
        )
    }
    ```

- [x] **Task 5: Update empty state for vibe-filtered case**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Replace `if (filteredPois.isEmpty())` empty state block. Since `pois` is now pre-filtered by VM, check `pois.isEmpty()` and show context-aware message:
    ```kotlin
    if (pois.isEmpty()) {
        Box(fillMaxSize, contentAlignment=Center) {
            Text(
                text = if (activeDynamicVibe != null)
                    "No ${activeDynamicVibe.label} found in this area"
                else
                    stringResource(Res.string.poi_list_empty),
                style = bodyMedium, color = onSurfaceVariant
            )
        }
    }
    ```

- [x] **Task 6: Update `MapScreen.kt` call site + add PlatformBackHandler**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action A — Add `PlatformBackHandler` for list view (inside `ReadyContent`, alongside existing back handlers):
    ```kotlin
    PlatformBackHandler(enabled = state.showListView) { viewModel.toggleListView() }
    ```
    Replace `toggleListView()` with the actual VM method that sets `showListView = false`.
  - Action B — Update `POIListView(...)` call (lines ~229–240):
    ```kotlin
    POIListView(
        pois = state.pois,
        dynamicVibes = state.dynamicVibes,
        activeDynamicVibe = state.activeDynamicVibe,
        onDynamicVibeSelected = viewModel::switchDynamicVibe,
        onPoiClick = { viewModel.selectPoi(it) },
        onSaveTapped = { poi -> viewModel.savePoi(poi, state.areaName) },
        onUnsaveTapped = { poi -> viewModel.unsavePoi(poi) },
        onNavigateTapped = { poi ->
            if (poi.latitude != null && poi.longitude != null)
                onNavigateToMaps(poi.latitude, poi.longitude, poi.name)
        },
        onChatTapped = { poi ->
            chatViewModel.openChat(
                state.areaName, state.allDiscoveredPois, state.activeDynamicVibe,
                entryPoint = ChatEntryPoint.PoiCard(poi),
            )
        },
        modifier = Modifier.fillMaxSize().padding(top = statusBarPadding + 112.dp),
        savedPoiIds = state.savedPoiIds,
    )
    ```
  - Notes: `chatViewModel` is declared at the top of `ReadyContent` — it is in scope at this call site. Remove the `// TODO(BACKLOG-MEDIUM)` comment at line ~226.

- [x] **Task 7: Delete dead code**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Delete `ThumbnailPlaceholder()` composable. Remove `filteredPois` val. Delete `// TODO(BACKLOG-LOW)` comment about ThumbnailPlaceholder.

---

### Acceptance Criteria

- [ ] **AC1:** Given a POI with non-null `imageUrl`, when list view opens, then each card shows that image as a full-width background with dark gradient scrim and text overlaid on top.
- [ ] **AC2:** Given a POI with `imageUrl = null`, when list view opens, then card shows `MapFloatingUiDark` fallback with scrim — no crash, no blank card.
- [ ] **AC3:** Given unsaved POI, when user taps bookmark icon, then `viewModel.savePoi(poi, areaName)` is called and icon renders as `Icons.Filled.Bookmark` in gold.
- [ ] **AC4:** Given saved POI, when user taps filled bookmark icon, then `viewModel.unsavePoi(poi)` is called and icon returns to `Icons.Outlined.BookmarkBorder` in white.
- [ ] **AC5:** Given POI with non-null `latitude` and `longitude`, when user taps navigate icon, then `onNavigateToMaps(lat, lng, name)` is called.
- [ ] **AC6:** Given POI with null `latitude` or `longitude`, when card renders, then navigate icon is not shown.
- [ ] **AC7:** Given any POI, when user taps chat bubble icon, then `chatViewModel.openChat(entryPoint=ChatEntryPoint.PoiCard(poi))` is called and chat overlay opens.
- [ ] **AC8:** Given 3 `dynamicVibes`, when list view opens, then chip strip shows exactly 3 chips each displaying `"${vibe.icon} ${vibe.label}"`.
- [ ] **AC9:** Given `activeDynamicVibe.label == "Cafes"`, when list renders, then "Cafes" chip is selected and all others are not.
- [ ] **AC10:** Given user taps a vibe chip, then `viewModel.switchDynamicVibe(vibe)` is called with the tapped `DynamicVibe`.
- [ ] **AC11:** Given `dynamicVibes` is empty, when list renders, then no chip strip is shown.
- [ ] **AC12:** Given user taps card body (not an icon button), then `viewModel.selectPoi(poi)` is called and `ExpandablePoiCard` detail sheet opens.
- [ ] **AC13:** Given user is in list view and presses Android back button, then list view closes and map is shown.
- [ ] **AC14:** Given POI has non-null `rating`, when card renders, then rating is shown as `"⭐ X.X"`.
- [ ] **AC15:** Given POI has non-null `liveStatus`, when card renders, then a color-coded status dot + label is shown.
- [ ] **AC16:** Given POI with blank `poi.vibe`, when card renders, then subtitle shows type only (no trailing `" · "`).
- [ ] **AC17:** Given `pois` is empty and `activeDynamicVibe` is set, when list renders, then empty state shows `"No [vibe.label] found in this area"`.
- [ ] **AC18:** Given TalkBack is active, when user focuses bookmark icon, then TalkBack announces "Save" or "Saved". Navigate icon announces "Navigate to [poi.name]". Chat icon announces "Ask AI about [poi.name]".

---

## Additional Context

### Dependencies

- `coil3.compose.AsyncImage` — already a dependency (used in PoiCarousel)
- `liveStatusToColor`, `resolveStatus`, `toComposeColor` — already in `com.harazone.ui.map` package
- `DynamicVibe` — `com.harazone.domain.model.DynamicVibe`
- `PlatformBackHandler` — `com.harazone.ui.components.PlatformBackHandler` (already in commonMain)
- No new Gradle dependencies required

### Testing Strategy

Write tests in `composeApp/src/commonTest/kotlin/com/harazone/ui/map/POIListViewTest.kt` using Compose test rules.

| # | Test name | Covers AC |
|---|-----------|-----------|
| T1 | `dynamicVibeChips_showCorrectCount` | AC8 |
| T2 | `dynamicVibeChip_selectedState_matchesActiveDynamicVibe` | AC9 |
| T3 | `dynamicVibeChip_tap_callsOnDynamicVibeSelected` | AC10 |
| T4 | `emptyDynamicVibes_hidesChipStrip` | AC11 |
| T5 | `bookmarkTap_unsavedPoi_callsSaveTapped` | AC3 |
| T6 | `bookmarkTap_savedPoi_callsUnsaveTapped` | AC4 |
| T7 | `navigateIcon_tap_callsOnNavigateTapped` | AC5 |
| T8 | `navigateIcon_hiddenWhenNullCoords` | AC6 |
| T9 | `chatIcon_tap_callsOnChatTapped` | AC7 |
| T10 | `cardBodyTap_callsOnPoiClick` | AC12 |
| T11 | `nullImageUrl_showsFallbackBackground` | AC2 |
| T12 | `emptyPois_withActiveVibe_showsVibeSpecificMessage` | AC17 |
| T13 | `emptyPois_noFilter_showsGenericMessage` | (empty state) |
| T14 | `blankVibe_noTrailingSeparatorInSubtitle` | AC16 |

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*POIListViewTest*"`

### Notes

- `POIListView.kt` lives in `commonMain` — compiles and runs on both Android and iOS. "Android UX priority" is the right framing, not "Android-only scope."
- Feature #42 in the open features table — tester release gate.
- Remove `// TODO(BACKLOG-MEDIUM)` at `MapScreen.kt` line ~226 after Task 6 — this spec resolves it.
- The `// TODO(BACKLOG-LOW)` about `ThumbnailPlaceholder` is resolved by Task 7.
- **Card height is 120dp** — single authoritative value. Do not use 140dp (that was a stale note, now removed).
