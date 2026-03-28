---
title: 'Detail Page v2 Story B — Save/Go/Been Lifecycle'
slug: 'save-go-been'
created: '2026-03-28'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'SQLDelight', 'Koin']
files_to_modify:
  - composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq
  - composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/14.sqm
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt
  - composeApp/src/commonMain/kotlin/com/harazone/domain/repository/SavedPoiRepository.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt
  - composeApp/src/commonMain/composeResources/values/strings.xml
  - composeApp/src/androidUnitTest/kotlin/com/harazone/data/repository/SavedPoiRepositoryTest.kt
  - composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt
  - composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeSavedPoiRepository.kt
code_patterns:
  - SQLDelight migration with ALTER TABLE
  - Repository suspend fun + optimistic UI update in ViewModel
  - Compose ModalBottomSheet for far-away Go sheet
  - haversineDistanceMeters for distance calculation (already imported in AiDetailPage)
  - LocalUriHandler.openUri for navigation
test_patterns:
  - runTest with fake AppClock for timestamp assertions
  - MockK for repository mocking in ViewModel tests
---

# Tech-Spec: Detail Page v2 Story B — Save/Go/Been Lifecycle

**Created:** 2026-03-28

## Overview

### Problem Statement

The detail page currently has a single "Visit" chip that conflates two distinct user actions — saving a place for later and marking it as somewhere you've been. There is no context-aware navigation button ("Go") and no way to record a POI visit independently of saving. The old `VisitState` enum (GO_NOW / PLAN_SOON / WANT_TO_GO) is internal bookkeeping that was never meaningful to users. The detail page chip UX is also small and hard to tap.

### Solution

Replace the action chips in `PoiDetailHeader` with three prominent equal-width CTA buttons: **Save** (bookmark the place), **Go** (context-aware navigation), and **Been** (mark as visited). Go adapts its behavior to GPS distance: nearby → opens navigation; far away (>500 km) → shows a bottom sheet with distance context. Each Go tap records `goIntentAt` for future companion nudges.

### Scope

**In Scope:**
- New `SaveGoBeenStrip` composable replacing the current chip row in `PoiDetailHeader`
- **Save button**: toggles save/unsave. Saved = row exists in `saved_pois` with `visited_at = null`. Unsave = delete row.
- **Go button**: context-aware by haversine distance to POI from GPS.
  - No GPS / POI has no coords → show far-away bottom sheet (no distance label)
  - ≤ 500 km → call `onNavigateToMaps(lat, lng, name)` (existing platform callback); if returns false → show "no maps app" snackbar
  - > 500 km → show `GoFarAwaySheet` bottom sheet with distance + companion voice blurb
  - Every Go tap records `goIntentAt` on the SavedPoi (auto-save first if not already saved)
- **Been button**: marks `visited_at = now()`. If the POI is not yet saved, auto-saves first. Tapping again (already Been) un-marks by setting `visited_at = null` (keeps the save).
- DB migration 14: add `go_intent_at INTEGER` column to `saved_pois`
- New repository methods: `markBeen`, `unmarkBeen`, `recordGoIntent`
- New ViewModel methods: `savePoi`, `unsavePoi` (thin wrappers), `markBeen`, `unmarkBeen`, `recordGoIntent`
- AiDetailPage signature update: replace `isVisited/visitState/onVisit/onUnvisit` with `isSaved/isBeen/onSave/onUnsave/onBeen/onUnbeen/onGoTapped`
- `GoFarAwaySheet` composable: warm companion voice + [Save] + [Plan a trip — stub]
- New strings for CTA labels and Go sheet copy

**Out of Scope:**
- Renaming `visitedPoiIds` → `savedPoiIds` in MapUiState (deferred refactor)
- Updating carousel/list view "Visit" buttons (separate story — saved list redesign)
- "Plan a trip" full implementation (stub only — shows snackbar "Coming soon!")
- Companion nudge integration for `goIntentAt` (post-beta)
- Chat message on Been tap (existing `openChatForPoi` stays unchanged)
- Any changes to `VisitState` enum or `visitPoi()` / `unvisitPoi()` functions (kept for carousel/list view, which are not changing in this story)

---

## Context for Development

### Codebase Patterns

- **Repository layer**: All methods are `suspend fun`. ViewModel calls them from `viewModelScope.launch`. Optimistic UI update is applied first, then DB write; rollback on failure.
- **SQLDelight**: Schema in `*.sq` files, migrations in numbered `*.sqm` files. Current DB version = 13 (last migration is `13.sqm`). New migration goes in `14.sqm`.
- **KMP navigation**: `onNavigateToMaps: (Double, Double, String) -> Boolean` is the platform-specific navigation callback (Android: geo: Intent; iOS: MKMapItem). Returns `false` if no maps app. Defined in `MapScreen.kt` signature, injected into `MapComposable.android.kt` / `MapComposable.ios.kt`. This callback is already threaded all the way down into `AiDetailPage`.
- **Distance utility**: `haversineDistanceMeters(lat1, lon1, lat2, lon2): Double` is in `com.harazone.util`. Already imported in `AiDetailPage.kt` at line 88. GPS coords come from `MapUiState.Ready.gpsLatitude` / `gpsLongitude`. When both are `0.0`, GPS is unavailable.
- **Saved state tracking**: `MapUiState.Ready.visitedPoiIds: Set<String>` = all saved POI IDs. `visitedPois: List<SavedPoi>` = all saved POI objects. `isSaved = poi.savedId in state.visitedPoiIds`. `isBeen = state.visitedPois.find { it.id == poi.savedId }?.visitedAt != null`.
- **ModalBottomSheet**: Use `androidx.compose.material3.ModalBottomSheet` with `rememberModalBottomSheetState()`. Available (Material3 already in project).
- **Strings**: `stringResource(Res.string.key)` for KMP compose resources. English strings in `composeApp/src/commonMain/composeResources/values/strings.xml`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt` | Domain model — add `goIntentAt` field |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/repository/SavedPoiRepository.kt` | Repository interface — add 3 new methods |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt` | Repository impl — wire new methods + map go_intent_at |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq` | SQLDelight queries — add 3 new queries |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | ViewModel — add 5 new functions |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | UiState — read `gpsLatitude`, `gpsLongitude`, `visitedPoiIds`, `visitedPois` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Compose screen — update AiDetailPage call site (lines ~619–645) |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt` | Detail page — update signature + replace chip section |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Strings — add CTA + sheet strings |

### Technical Decisions

1. **`isBeen` derivation** — Derived in `MapScreen.kt` from `state.visitedPois.find { it.id == poi.savedId }?.visitedAt != null`. No new set in MapUiState. Keeps diff minimal.

2. **Go distance threshold: 500 km** — Matches the brainstorm. `distanceKm >= 500.0` OR `(gpsLatitude == 0.0 && gpsLongitude == 0.0)` OR `poi.latitude == null` → show far-away sheet.

3. **Go intent recording** — Every Go tap auto-saves (if not already saved) and records `goIntentAt`. This is a fire-and-forget optimistic write; no UI feedback needed beyond the navigation action itself.

4. **Been auto-save** — If `!isSaved`, Been tap auto-saves (calls `savePoi()` first), then `markBeen()`. ViewModel handles this atomically: update state optimistically, save + markBeen in same coroutine.

5. **Unmark Been** — Tapping Been when already Been calls `unmarkBeen()` which sets `visited_at = null` but keeps the save record. The Save button stays filled/active.

6. **Keep `visitPoi()` / `unvisitPoi()` unchanged** — Carousel and POIListView still call these. Not touched in this story.

7. **"Plan a trip" stub** — GoFarAwaySheet has a "Plan a trip" button that silently dismisses the sheet (no Snackbar — per AC3).

8. **Distance formatting** — `distanceKm < 1.0` → `"${(distanceKm * 1000).toInt()} m"`. `distanceKm < 10.0` → `"%.1f km".format(distanceKm)`. `else` → `"${distanceKm.toInt()} km"`. Use `String.format` equivalent via `buildString` / Kotlin string templates (not Java's `String.format` — KMP incompatible; use `"${"%.1f".format(d)} km"` which uses Kotlin's `format` extension on `Double`).

9. **Go button icon + label** — `distanceKm < 2.0` → `🚶`, `2..50` → `🧭`, `50..500` → `🚗`, `>500 or unknown` → `✈`. Label: `"Go · $formattedDistance"` when distance known; `"Go"` when unknown.

---

## Implementation Plan

### Tasks

**Task 1 — DB Migration (lowest level)**

File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/14.sqm`

Create new file:
```sql
ALTER TABLE saved_pois ADD COLUMN go_intent_at INTEGER;
```

**Task 2 — SQLDelight queries**

File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq`

Append three queries after the existing `updateVisitState` query:

```sql
updateGoIntentAt:
UPDATE saved_pois SET go_intent_at = :go_intent_at WHERE poi_id = :poi_id;

updateVisitedAt:
UPDATE saved_pois SET visited_at = :visited_at WHERE poi_id = :poi_id;

clearVisitedAt:
UPDATE saved_pois SET visited_at = NULL WHERE poi_id = :poi_id;
```

**Task 3 — SavedPoi domain model**

File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt`

Add field to `SavedPoi` data class after `visitedAt`:
```kotlin
val goIntentAt: Long? = null,
```

**Task 4 — SavedPoiRepository interface**

File: `composeApp/src/commonMain/kotlin/com/harazone/domain/repository/SavedPoiRepository.kt`

Add three methods:
```kotlin
suspend fun markBeen(poi: SavedPoi)       // sets visited_at = now, inserts if not present
suspend fun unmarkBeen(poiId: String)     // sets visited_at = null, keeps row
suspend fun recordGoIntent(poiId: String, timestamp: Long)  // sets go_intent_at
```

**Task 5 — SavedPoiRepositoryImpl**

File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt`

a) In `observeAll()` mapping, add `goIntentAt = it.go_intent_at` to the `SavedPoi(...)` constructor.

b) Implement `markBeen`:
```kotlin
override suspend fun markBeen(poi: SavedPoi) {
    withContext(ioDispatcher) {
        database.saved_poisQueries.insertOrReplaceWithState(
            poi_id = poi.id,
            name = poi.name,
            type = poi.type,
            area_name = poi.areaName,
            lat = poi.lat,
            lng = poi.lng,
            why_special = poi.whySpecial,
            saved_at = poi.savedAt,
            user_note = poi.userNote,
            image_url = poi.imageUrl,
            description = poi.description,
            rating = poi.rating?.toDouble(),
            vibe = poi.vibe,
            visit_state = null,
            visited_at = clock.nowMs(),
        )
    }
}
```

c) Implement `unmarkBeen`:
```kotlin
override suspend fun unmarkBeen(poiId: String) {
    withContext(ioDispatcher) {
        database.saved_poisQueries.clearVisitedAt(poiId)
    }
}
```

d) Implement `recordGoIntent`:
```kotlin
override suspend fun recordGoIntent(poiId: String, timestamp: Long) {
    withContext(ioDispatcher) {
        database.saved_poisQueries.updateGoIntentAt(
            go_intent_at = timestamp,
            poi_id = poiId,
        )
    }
}
```

**Task 6 — MapViewModel new functions**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`

Add these functions after `unvisitPoi()` (around line 350). Each follows the same optimistic-update + background-launch pattern as `visitPoi()`.

```kotlin
fun savePoi(poi: POI, areaName: String) {
    val poiId = poi.savedId
    val current = _uiState.value as? MapUiState.Ready ?: return
    val savedPoiObj = SavedPoi(
        id = poiId,
        name = poi.name,
        type = poi.type,
        areaName = areaName,
        lat = poi.latitude ?: return,
        lng = poi.longitude ?: return,
        whySpecial = poi.insight.ifEmpty { poi.description },
        savedAt = clock(),
        imageUrl = poi.imageUrl,
        description = poi.description,
        rating = poi.rating,
        vibe = poi.primaryVibe ?: poi.vibe.split(",").firstOrNull()?.trim() ?: "",
    )
    _uiState.value = current.copy(
        visitedPoiIds = current.visitedPoiIds + poiId,
        visitedPois = current.visitedPois + savedPoiObj,
    )
    viewModelScope.launch {
        try {
            savedPoiRepository.save(savedPoiObj)
        } catch (e: Exception) {
            AppLogger.e(e) { "MapViewModel: savePoi failed" }
            val s = _uiState.value as? MapUiState.Ready ?: return@launch
            _uiState.value = s.copy(
                visitedPoiIds = s.visitedPoiIds - poiId,
                visitedPois = s.visitedPois.filter { it.id != poiId },
            )
        }
    }
}

fun unsavePoi(poi: POI) = unvisitPoi(poi)  // same as delete — reuse existing

fun markBeen(poi: POI, areaName: String) {
    val poiId = poi.savedId
    val current = _uiState.value as? MapUiState.Ready ?: return
    // Optimistic: ensure saved + set visitedAt in local list
    val existing = current.visitedPois.find { it.id == poiId }
    val timestamp = clock()
    val beenPoi = existing?.copy(visitedAt = timestamp) ?: SavedPoi(
        id = poiId,
        name = poi.name,
        type = poi.type,
        areaName = areaName,
        lat = poi.latitude ?: return,
        lng = poi.longitude ?: return,
        whySpecial = poi.insight.ifEmpty { poi.description },
        savedAt = timestamp,
        imageUrl = poi.imageUrl,
        description = poi.description,
        rating = poi.rating,
        vibe = poi.primaryVibe ?: poi.vibe.split(",").firstOrNull()?.trim() ?: "",
        visitedAt = timestamp,
    )
    val updatedPois = if (existing != null) {
        current.visitedPois.map { if (it.id == poiId) beenPoi else it }
    } else {
        current.visitedPois + beenPoi
    }
    _uiState.value = current.copy(
        visitedPoiIds = current.visitedPoiIds + poiId,
        visitedPois = updatedPois,
    )
    viewModelScope.launch {
        try {
            savedPoiRepository.markBeen(beenPoi)
        } catch (e: Exception) {
            AppLogger.e(e) { "MapViewModel: markBeen failed" }
            // Rollback
            val s = _uiState.value as? MapUiState.Ready ?: return@launch
            if (existing == null) {
                _uiState.value = s.copy(
                    visitedPoiIds = s.visitedPoiIds - poiId,
                    visitedPois = s.visitedPois.filter { it.id != poiId },
                )
            } else {
                _uiState.value = s.copy(
                    visitedPois = s.visitedPois.map { if (it.id == poiId) existing else it },
                )
            }
        }
    }
}

fun unmarkBeen(poi: POI) {
    val poiId = poi.savedId
    val current = _uiState.value as? MapUiState.Ready ?: return
    val existing = current.visitedPois.find { it.id == poiId } ?: return
    val updatedPoi = existing.copy(visitedAt = null)
    _uiState.value = current.copy(
        visitedPois = current.visitedPois.map { if (it.id == poiId) updatedPoi else it },
    )
    viewModelScope.launch {
        try {
            savedPoiRepository.unmarkBeen(poiId)
        } catch (e: Exception) {
            AppLogger.e(e) { "MapViewModel: unmarkBeen failed" }
            val s = _uiState.value as? MapUiState.Ready ?: return@launch
            _uiState.value = s.copy(
                visitedPois = s.visitedPois.map { if (it.id == poiId) existing else it },
            )
        }
    }
}

fun recordGoIntent(poi: POI, areaName: String) {
    val poiId = poi.savedId
    val current = _uiState.value as? MapUiState.Ready ?: return
    val timestamp = clock()
    // Auto-save if not already saved (Go intent is a strong signal)
    if (poiId !in current.visitedPoiIds) savePoi(poi, areaName)
    viewModelScope.launch {
        try {
            savedPoiRepository.recordGoIntent(poiId, timestamp)
        } catch (e: Exception) {
            AppLogger.e(e) { "MapViewModel: recordGoIntent failed" }
        }
    }
}
```

**Task 7 — AiDetailPage.kt signature + new composables**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`

a) **Update `AiDetailPage` composable signature** — replace `isVisited/visitState/onVisit/onUnvisit` with:
```kotlin
isSaved: Boolean,
isBeen: Boolean,
userLat: Double,
userLng: Double,
onSave: () -> Unit,
onUnsave: () -> Unit,
onBeen: () -> Unit,
onUnbeen: () -> Unit,
onGoTapped: () -> Unit,
```
Keep `onNavigateToMaps: (Double, Double, String) -> Boolean` and `onDirectionsFailed: () -> Unit`.
Remove `onDirectionsClick: (Double, Double, String) -> Unit` (Go button handles navigation directly now).

b) **Update `PoiDetailHeader` composable signature** with the same parameter changes as `AiDetailPage`.

c) **Replace chip section** (lines ~687–734 in `PoiDetailHeader`) with:
```kotlin
SaveGoBeenStrip(
    poi = poi,
    isSaved = isSaved,
    isBeen = isBeen,
    userLat = userLat,
    userLng = userLng,
    onSave = onSave,
    onUnsave = onUnsave,
    onBeen = onBeen,
    onUnbeen = onUnbeen,
    onGoTapped = onGoTapped,
    onNavigateToMaps = onNavigateToMaps,
    onDirectionsFailed = onDirectionsFailed,
)
```

d) **Add `SaveGoBeenStrip` composable** (private, after `PoiDetailHeader`):
```kotlin
@Composable
private fun SaveGoBeenStrip(
    poi: POI,
    isSaved: Boolean,
    isBeen: Boolean,
    userLat: Double,
    userLng: Double,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
    onBeen: () -> Unit,
    onUnbeen: () -> Unit,
    onGoTapped: () -> Unit,
    onNavigateToMaps: (Double, Double, String) -> Boolean,
    onDirectionsFailed: () -> Unit,
) {
    var showGoSheet by remember { mutableStateOf(false) }
    val distanceKm: Double? = remember(userLat, userLng, poi.latitude, poi.longitude) {
        if (userLat == 0.0 && userLng == 0.0) null
        else if (poi.latitude == null || poi.longitude == null) null
        else haversineDistanceMeters(userLat, userLng, poi.latitude, poi.longitude) / 1000.0
    }
    val isFarAway = distanceKm == null || distanceKm >= 500.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Save button
        OutlinedButton(
            onClick = { if (isSaved && !isBeen) onUnsave() else onSave() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isSaved) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isSaved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.cta_save))
        }

        // Go button
        Button(
            onClick = {
                onGoTapped()
                if (isFarAway) {
                    showGoSheet = true
                } else {
                    val handled = onNavigateToMaps(poi.latitude!!, poi.longitude!!, poi.name)
                    if (!handled) onDirectionsFailed()
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!isFarAway) Color(0xFF3FB950) else MaterialTheme.colorScheme.primary,
            ),
        ) {
            val (goIcon, goLabel) = buildGoLabel(distanceKm)
            Text("$goIcon ${stringResource(Res.string.cta_go)}${if (goLabel.isNotEmpty()) " · $goLabel" else ""}")
        }

        // Been button
        OutlinedButton(
            onClick = { if (isBeen) onUnbeen() else onBeen() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isBeen) Color(0xFF3FB950).copy(alpha = 0.15f) else Color.Transparent,
                contentColor = if (isBeen) Color(0xFF3FB950) else MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, if (isBeen) Color(0xFF3FB950) else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.cta_been))
        }
    }

    if (showGoSheet) {
        GoFarAwaySheet(
            poi = poi,
            distanceKm = distanceKm,
            isSaved = isSaved,
            onSave = { onSave(); showGoSheet = false },
            onDismiss = { showGoSheet = false },
        )
    }
}
```

e) **Add `buildGoLabel` private function** (pure, not composable):
```kotlin
private fun buildGoLabel(distanceKm: Double?): Pair<String, String> {
    if (distanceKm == null) return "✈️" to ""
    return when {
        distanceKm < 2.0 -> "🚶" to if (distanceKm < 1.0) "${(distanceKm * 1000).toInt()} m" else "${"%.1f".format(distanceKm)} km"
        distanceKm < 50.0 -> "🧭" to "${"%.0f".format(distanceKm)} km"
        distanceKm < 500.0 -> "🚗" to "${"%.0f".format(distanceKm)} km"
        else -> "✈️" to "${"%.0f".format(distanceKm)} km"
    }
}
```

f) **Add `GoFarAwaySheet` composable** (private):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoFarAwaySheet(
    poi: POI,
    distanceKm: Double?,
    isSaved: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val distanceText = if (distanceKm != null) "${"%.0f".format(distanceKm)} km from you" else "a journey away"
            Text(
                text = "${poi.name} is $distanceText! Save it for the right moment, or start planning your trip?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isSaved) {
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(Res.string.cta_save)) }
                }
                Button(
                    onClick = {
                        // TODO(BACKLOG-MEDIUM): Plan a trip — post-beta
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.cta_plan_trip)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

g) **Update `AiDetailPage` call to `PoiDetailHeader`** — pass through all new params, remove old ones.

h) **Remove `onDirectionsClick` param** from `AiDetailPage` entirely (no longer needed — Go handles it).

i) **Remove import** `androidx.compose.material.icons.filled.Directions` if unused after this change.

**Task 8 — MapScreen.kt**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

Update the `AiDetailPage(...)` call site (around lines 619–645):

```kotlin
AiDetailPage(
    poi = state.selectedPoi,
    chatViewModel = chatViewModel,
    chatState = chatState,
    areaName = state.areaName,
    allPois = state.allDiscoveredPois,
    activeDynamicVibe = state.activeDynamicVibe,
    isSaved = state.selectedPoi.savedId in state.visitedPoiIds,
    isBeen = state.visitedPois.find { it.id == state.selectedPoi.savedId }?.visitedAt != null,
    userLat = state.gpsLatitude,
    userLng = state.gpsLongitude,
    onSave = { viewModel.savePoi(state.selectedPoi, state.areaName) },
    onUnsave = { viewModel.unsavePoi(state.selectedPoi) },
    onBeen = { viewModel.markBeen(state.selectedPoi, state.areaName) },
    onUnbeen = { viewModel.unmarkBeen(state.selectedPoi) },
    onGoTapped = { viewModel.recordGoIntent(state.selectedPoi, state.areaName) },
    onNavigateToMaps = onNavigateToMaps,
    onDirectionsFailed = {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(noMapsAppMessage)
        }
    },
    onShowOnMap = { lat, lng ->
        viewModel.flyToCoords(lat, lng)
    },
    onDismiss = dismissDetail,
    // ... other params unchanged
)
```

Remove the old `onVisit`, `onUnvisit`, `onDirectionsClick` lambdas from this call site.

**Task 9 — Strings**

File: `composeApp/src/commonMain/composeResources/values/strings.xml`

Add to the string resources (near existing action strings):
```xml
<string name="cta_save">Save</string>
<string name="cta_go">Go</string>
<string name="cta_been">Been</string>
<string name="cta_plan_trip">Plan a trip</string>
```

**Task 10 — Tests: SavedPoiRepositoryTest**

File: `composeApp/src/androidUnitTest/kotlin/com/harazone/data/repository/SavedPoiRepositoryTest.kt`

Add three tests after existing `visit` tests:

```kotlin
@Test
fun `markBeen inserts row with visitedAt set`() = testScope.runTest {
    val poi = poi()
    repository.markBeen(poi)
    repository.observeAll().first().let { list ->
        assertEquals(1, list.size)
        assertNotNull(list.first().visitedAt)
        assertNull(list.first().visitState)
    }
}

@Test
fun `unmarkBeen clears visitedAt but keeps row`() = testScope.runTest {
    val poi = poi().copy(visitedAt = 12345L)
    repository.markBeen(poi)
    repository.unmarkBeen(poi.id)
    repository.observeAll().first().let { list ->
        assertEquals(1, list.size)
        assertNull(list.first().visitedAt)
    }
}

@Test
fun `recordGoIntent updates go_intent_at on saved row`() = testScope.runTest {
    val poi = poi()
    repository.save(poi)
    repository.recordGoIntent(poi.id, 99999L)
    repository.observeAll().first().let { list ->
        assertEquals(99999L, list.first().goIntentAt)
    }
}
```

**Task 11 — Tests: MapViewModelTest**

File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt`

Add tests for new ViewModel functions. Follow the existing pattern in the file (use `coEvery`/`coVerify` from MockK):

```kotlin
@Test
fun `savePoi adds to visitedPoiIds optimistically`() = runTest {
    // arrange: ready state with no saved pois
    // act: viewModel.savePoi(testPoi, "Test Area")
    // assert: state.visitedPoiIds contains testPoi.savedId
    // verify: savedPoiRepository.save() called once
}

@Test
fun `unsavePoi removes from visitedPoiIds optimistically`() = runTest {
    // arrange: poi already in visitedPoiIds
    // act: viewModel.unsavePoi(testPoi)
    // assert: state.visitedPoiIds does not contain testPoi.savedId
}

@Test
fun `markBeen sets visitedAt on poi in visitedPois`() = runTest {
    // arrange: poi in visitedPois with visitedAt = null
    // act: viewModel.markBeen(testPoi, "Test Area")
    // assert: state.visitedPois.find { it.id == testPoi.savedId }?.visitedAt != null
    // verify: savedPoiRepository.markBeen() called once
}

@Test
fun `markBeen auto-saves poi if not already saved`() = runTest {
    // arrange: poi NOT in visitedPoiIds
    // act: viewModel.markBeen(testPoi, "Test Area")
    // assert: state.visitedPoiIds contains testPoi.savedId AND visitedAt != null
    // verify: savedPoiRepository.markBeen() called (uses insertOrReplaceWithState)
}

@Test
fun `unmarkBeen clears visitedAt but keeps poi in visitedPoiIds`() = runTest {
    // arrange: poi in visitedPois with visitedAt set
    // act: viewModel.unmarkBeen(testPoi)
    // assert: state.visitedPois.find { it.id == testPoi.savedId }?.visitedAt == null
    // assert: testPoi.savedId still in state.visitedPoiIds
}

@Test
fun `recordGoIntent auto-saves poi if not already saved`() = runTest {
    // arrange: poi NOT in visitedPoiIds
    // act: viewModel.recordGoIntent(testPoi, "Test Area")
    // assert: state.visitedPoiIds contains testPoi.savedId
    // verify: savedPoiRepository.recordGoIntent() called
}
```

### Acceptance Criteria

**AC1 — Save button**
- **Given** the detail page is open for an unsaved POI → Save button shows unfilled bookmark icon, no container fill
- **When** Save is tapped → Save button becomes filled bookmark, highlighted with primaryContainer background; optimistic update visible before DB write
- **When** Save is tapped again (while not Been) → POI removed from saved list; button returns to unfilled state
- **Given** POI is Been (visitedAt set) → Save button remains active/filled; tapping Save does not unsave (unsave only available when not Been)

**AC2 — Go button (nearby)**
- **Given** GPS is available and POI is ≤500 km away → Go button is green, shows distance-appropriate icon + "Go · X km"
- **When** Go is tapped → `onNavigateToMaps` is called; platform maps app opens
- **When** no maps app is installed → `onDirectionsFailed` called → Snackbar shown
- **When** Go is tapped → `goIntentAt` is recorded on the POI (auto-saves if not saved)

**AC3 — Go button (far away)**
- **Given** GPS unavailable OR POI is >500 km away → Go button shows ✈ icon, "Go" or "Go · X km"
- **When** Go is tapped → `GoFarAwaySheet` bottom sheet appears
- **Sheet content** shows "[POI name] is [X km] from you!" or "a journey away" if no GPS, with companion voice tone
- **Sheet: Save button** only shown if POI is not yet saved; taps it → saves + dismisses sheet
- **Sheet: Plan a trip button** → dismisses sheet (stub for now; no TODO needed, just works silently)
- **When** sheet dismissed via scrim or back → sheet closes, no action taken

**AC4 — Been button**
- **Given** POI has not been visited (visitedAt = null) → Been button shows unfilled checkmark, no fill
- **When** Been is tapped on an unsaved POI → POI is auto-saved AND visitedAt is set; Save button also fills
- **When** Been is tapped on a saved POI → visitedAt is set; Been button shows green checkmark + green fill
- **When** Been button is tapped again (already Been) → visitedAt cleared; Been button returns to unfilled state; POI remains saved
- **Given** POI is Been → Been button shows green checkmark

**AC5 — Button layout**
- Three buttons are equal width, side-by-side in a Row
- Each button fits within screen width on smallest supported device (320dp) — use `weight(1f)` layout

**AC6 — GoFarAwaySheet back/scrim dismiss**
- PlatformBackHandler for the sheet is provided by ModalBottomSheet automatically (Material3)

**AC7 — No regression on existing save/visit flows**
- Carousel "Visit" button still calls `visitPoi()` — unaffected
- POIListView visit toggle still calls `visitPoi()` / `unvisitPoi()` — unaffected
- `SavedPoiCard` VisitState badge still renders — unaffected (VisitState enum not removed)

---

## Additional Context

### Dependencies

- No new dependencies needed. `ModalBottomSheet` is already available via `androidx.compose.material3` (already in project).
- SQLDelight migration file must match the DB schema version increment. Current highest migration = 13, so new file = `14.sqm`. Verify `schemaVersion` in the SQLDelight Gradle config matches after adding the new migration.

### Testing Strategy

- **Repository tests** (`androidUnitTest`): Use existing in-memory SQLDelight setup from `SavedPoiRepositoryTest`. Add 3 new tests (AC covers markBeen, unmarkBeen, recordGoIntent).
- **ViewModel tests** (`commonTest`): MockK-based. Add 6 new tests. Mock `savedPoiRepository` with `coEvery { markBeen(any()) } just Runs`, etc.
- **UI tests**: Not required for this story. The Save/Go/Been strip is tested via ViewModel and repo unit tests.
- **Manual smoke test checklist**:
  1. Open detail page for a nearby POI (GPS active) → verify Go button shows distance + green color
  2. Tap Save → button fills; check saved list shows it
  3. Tap Go (nearby) → maps app opens
  4. Tap Been on unsaved POI → both Save and Been fill simultaneously
  5. Tap Been again → Been unfills, Save stays filled
  6. Open detail page for a remote POI (e.g., teleport to another country) → Go shows ✈ + distance
  7. Tap Go (far) → sheet appears with companion copy
  8. On sheet: tap Save → saves + dismisses sheet

### Notes

- **`unsavePoi(poi: POI)` signature**: the function delegates to `unvisitPoi(poi)` but note that `unvisitPoi` takes `POI` not `String`. This is consistent.
- **Emoji in button labels**: Kotlin string templates with emoji chars work on both Android and iOS KMP. Use Unicode escapes if build tool complains: 🚶 = `"\uD83D\uDEB6"`, 🧭 = `"\uD83E\uDDED"`, 🚗 = `"\uD83D\uDE97"`, ✈ = `"\u2708"`.
- **"Plan a trip" stub**: The button silently dismisses the sheet. No snackbar needed — it's less confusing than promising a feature that's not there. The TODO comment in the implementation is enough.
- **Go button disabled state**: If `poi.latitude == null || poi.longitude == null`, show far-away sheet (not navigate). No need to disable the button — just route to sheet path.
- **`clock()` in MapViewModel**: The `clock` property is already used for timestamps throughout MapViewModel (e.g., in `visitPoi()`). Use the same `clock()` call for all timestamp captures in new functions.
