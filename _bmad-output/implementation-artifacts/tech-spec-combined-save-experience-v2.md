---
title: 'Combined Save Experience v2'
slug: 'combined-save-experience-v2'
created: '2026-03-12'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'SQLDelight']
files_to_modify:
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq'
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/9.sqm (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeOrb.kt'
code_patterns:
  - 'VibeRail call in MapScreen:229 — currently: activeVibe, vibePoiCounts, onVibeSelected only; needs 4 new params'
  - 'VibeOrb two-level alpha: Modifier.alpha(0.45f) on Column for dim, graphicsLayer{alpha=breathingAlpha} on circle Box for pulse — NEVER merge these'
  - 'switchVibe(Vibe) at MapViewModel:117 — add savedVibeFilter = false here; mutual exclusion with saved filter'
  - 'savedPois observed in MapViewModel.init:77-82 via observeAll() — reuse this flow to compute vibeAreaSaveCounts'
  - 'vibePoiCounts counts Gemini POIs — do NOT overload; vibeAreaSaveCounts is a separate field for DB saves'
  - 'savedVibeFilter pin hiding: pass pois=emptyList() to MapComposable when savedVibeFilter=true — avoids touching platform actuals'
  - 'MapComposable.android.kt:220 filters pois by activeVibe via poi.vibe.contains(name) — saved filter handled upstream'
  - 'POI.vibe is a CSV String e.g. "Character,History" — extract first vibe at save time'
  - 'ChatPoiCard has no vibe field — use chatViewModel active context vibe instead'
  - 'DB migration latest is 8.sqm (2026-03-11) — next is 9.sqm'
test_patterns:
  - 'commonTest — ViewModel tests use kotlin.test + UnconfinedTestDispatcher'
  - 'FakeSavedPoiRepository at commonTest/fakes/ — supports save(), unsave(), observeAll(), observeSavedIds()'
  - 'runTest + UnconfinedTestDispatcher — see MapViewModelTest.kt'
---

# Tech-Spec: Combined Save Experience v2

**Created:** 2026-03-12

## Overview

### Problem Statement

The VibeRail shows 6 static vibe orbs in a fixed order with no memory of the user's saved places in an area. There is no inline way to filter the map to only show saved (gold) pins, no per-vibe save counts visible on the rail, and the ordering never changes regardless of how many places the user has saved in each vibe category. The VibeRail does not reflect the user's history in an area.

Part A (SavedPlacesScreen full-screen overlay) is already implemented. This spec adds Part B: the VibeRail integration.

### Solution

Three layered changes to the VibeRail:

1. **"Saved" orb** — pinned at top of VibeRail above a separator line. Tapping it: (a) activates `savedVibeFilter = true` on the map (only gold DB pins shown), AND (b) opens `SavedPlacesScreen` overlay via `viewModel.openSavesSheet()`. Tapping again deactivates filter without closing the list. The orb is styled with the gold star visual language.
2. **Count badges** — small number on each vibe orb showing area-scoped saves per vibe. Badge hidden when count = 0. Requires DB migration to add `vibe: String` to `SavedPoi` (stored at save time). `vibeAreaSaveCounts: Map<Vibe, Int>` computed in MapViewModel from `savedPois` filtered to current `areaName`.
3. **Dynamic reordering** — the 6 vibe orbs reorder by `vibeAreaSaveCounts` (most-saved vibe first) for the current area. Saved orb stays pinned at top, is NOT included in the sort. New area with zero saves = default `Vibe.entries` order.

### Scope

**In Scope:**
- "Saved" VibeOrb pinned at top of VibeRail, above a horizontal separator line
- Saved orb: gold/star visual, active state pulses (same breathing animation), dims other orbs when active
- `savedVibeFilter: Boolean` in `MapUiState.Ready` — when true, map shows only gold DB pins
- `vibeAreaSaveCounts: Map<Vibe, Int>` in `MapUiState.Ready` — area-scoped save counts per vibe
- Count badge overlay on each vibe orb (zero = no badge); count badge on Saved orb = total area saves
- Dynamic reorder of the 6 vibe orbs by area-scoped save count
- DB migration: add `vibe TEXT NOT NULL DEFAULT ''` column to `saved_pois`
- `SavedPoi.vibe: String` domain model field
- Store `vibe` at save time in `MapViewModel.savePoi` and `ChatViewModel.savePoi`
- `onSavedVibeSelected()` in MapViewModel — toggles `savedVibeFilter`, sets `activeVibe = null`, calls `openSavesSheet()`
- Map pin rendering: filter to savedPoiIds-only when `savedVibeFilter = true`
- Clear `savedVibeFilter` when a regular vibe orb is tapped (`switchVibe`)

**Out of Scope:**
- SavedPlacesScreen changes (Part A — already implemented)
- Global save counts across all areas
- External API integration (Foursquare, Google Places)
- Social/sharing features
- Per-vibe icon changes or VibeOrb visual redesign beyond badge + Saved orb

---

## Context for Development

### Codebase Patterns

- `VibeRail` call in `MapScreen:229`: currently `activeVibe`, `vibePoiCounts`, `onVibeSelected`. Add: `savedVibeActive: Boolean`, `vibeAreaSaveCounts: Map<Vibe, Int>`, `totalAreaSaveCount: Int`, `onSavedVibeSelected: () -> Unit`.
- `VibeOrb` two-level alpha (CRITICAL — do not merge): `Modifier.alpha(0.45f)` on outer `Column` for dim state; `graphicsLayer { alpha = breathingAlpha }` on inner circle `Box` for pulse. Both must remain separate.
- `switchVibe(vibe)` at `MapViewModel:117`: add `savedVibeFilter = false` here — mutual exclusion with saved filter mode.
- `MapViewModel.savePoi()` constructor at lines 181-193: add `vibe = activeVibe?.name ?: poi.vibe.split(",").firstOrNull()?.trim() ?: ""`. `POI.vibe` is CSV e.g. "Character,History" — extract first if no activeVibe.
- `ChatPoiCard` has NO vibe field — in `ChatViewModel.savePoi()`, use `vibe = _uiState.value.activeVibe?.name ?: ""` (ChatUiState already carries `activeVibe`).
- `savedVibeFilter` pin hiding: in `MapScreen`, pass `pois = if (state.savedVibeFilter) emptyList() else state.pois` to `MapComposable` — avoids touching platform actuals (android + iOS). `savedPois` still renders gold pins normally.
- `MapComposable.android.kt:220` filters `pois` by `activeVibe` via `poi.vibe.contains(name)` — this is downstream and unaffected; our upstream empty-list trick handles saved filter cleanly.
- `openSavesSheet()` at `MapViewModel:223` exists — Saved orb tap calls this after setting `savedVibeFilter = true`.
- `observeAll()` Flow already collected in `MapViewModel.init:77` into `latestSavedPois` — hook into this to also recompute `vibeAreaSaveCounts`.
- Migration 8.sqm is latest (2026-03-11) — next is **9.sqm**.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `ui/map/components/VibeRail.kt` | Add Saved orb + separator above vibes; add count badges; dynamic reorder by save count |
| `ui/map/components/VibeOrb.kt` | Add optional `saveCount: Int?` param for badge overlay |
| `ui/map/MapUiState.kt` | Add `savedVibeFilter: Boolean = false`, `vibeAreaSaveCounts: Map<Vibe, Int> = emptyMap()` |
| `ui/map/MapViewModel.kt:117` | `switchVibe` — clear `savedVibeFilter`; add `onSavedVibeSelected()`; compute `vibeAreaSaveCounts` in init observer |
| `ui/map/MapViewModel.kt:181` | `savePoi` — add `vibe = activeVibe?.name ?: poi.vibe.split(",").firstOrNull()?.trim() ?: ""` |
| `ui/map/ChatViewModel.kt:282` | `savePoi` — add `vibe = _uiState.value.activeVibe?.name ?: ""` (check field name in ChatUiState) |
| `ui/map/MapScreen.kt:229` | Pass 4 new params to VibeRail; pass `pois = if (state.savedVibeFilter) emptyList() else state.pois` to MapComposable |
| `domain/model/SavedPoi.kt` | Add `val vibe: String = ""` |
| `data/repository/SavedPoiRepositoryImpl.kt:52` | `save()` — add `vibe = poi.vibe`; `observeAll()` mapping — add `vibe = it.vibe ?: ""` |
| `data/local/saved_pois.sq` | Add `vibe TEXT NOT NULL DEFAULT ''` to CREATE TABLE + insertOrReplace |
| `data/local/migrations/9.sqm (NEW)` | `ALTER TABLE saved_pois ADD COLUMN vibe TEXT NOT NULL DEFAULT '';` |
| `commonTest/fakes/FakeSavedPoiRepository.kt` | Use in ViewModel tests — already has save/unsave/observeAll/observeSavedIds |

### Technical Decisions

1. **Saved orb placement**: Separate composable block in `VibeRail.kt` above a `HorizontalDivider(1.dp, White.copy(alpha=0.12f))`. The `vibes` param of `VibeRail` remains `Array<Vibe>` and only contains the 6 regular vibes — Saved is NOT in that array.

2. **Saved chip tap — dual action**: `onSavedVibeSelected()` in MapViewModel:
   - If `savedVibeFilter` is currently `false`: set `savedVibeFilter = true`, `activeVibe = null`, call `openSavesSheet()`
   - If `savedVibeFilter` is currently `true`: set `savedVibeFilter = false` only (screen has its own dismiss via BackHandler)

3. **`switchVibe` mutual exclusion**: `switchVibe(vibe)` at line 117 must set `savedVibeFilter = false` before toggling `activeVibe`.

4. **`vibeAreaSaveCounts` computation** (add to MapViewModel init observer, triggered on each `latestSavedPois` update):
   ```kotlin
   private fun computeVibeAreaSaveCounts(saves: List<SavedPoi>, areaName: String): Map<Vibe, Int> =
       saves.filter { it.areaName == areaName && it.vibe.isNotEmpty() }
            .groupBy { save -> Vibe.entries.firstOrNull { it.name == save.vibe } }
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .mapValues { it.value.size }
   ```
   Update state: `vibeAreaSaveCounts = computeVibeAreaSaveCounts(saves, current.areaName)`.

5. **Dynamic reorder** (pure UI in `VibeRail`, no ViewModel mutation):
   ```kotlin
   val sortedVibes = vibes.sortedByDescending { vibeAreaSaveCounts[it] ?: 0 }
   ```

6. **Count badge** (overlay on top-right of orb circle): Show only when `count > 0`. Blue badge (`Color(0xFF4a7cf7)`, white text, 8sp) for regular vibes. Gold badge (`Color(0xFFFFD700)`, black text) for Saved orb (total area save count). Use `Box(contentAlignment = TopEnd)` wrapping the orb to position badge.

7. **Saved orb composable**: Implement as a private `SavedVibeOrb` composable inside `VibeRail.kt` (not inside `VibeOrb.kt`). Fixed size 40.dp. Icon: `Icons.Default.Bookmark`, tint `Color(0xFFFFD700)`. Active breathing: same `rememberInfiniteTransition` pattern as `VibeOrb`. Dim behavior: `Modifier.alpha(if (isFilterActive && !savedVibeActive) 0.45f else 1f)`.

8. **`vibe` at save time**: `MapViewModel.savePoi(poi, areaName)`: `vibe = activeVibe?.name ?: poi.vibe.split(",").firstOrNull()?.trim() ?: ""`. `ChatViewModel.savePoi(card, areaName)`: check `ChatUiState` for active vibe field; use `vibe = state.activeVibe?.name ?: ""`.

9. **DB migration**: File `9.sqm` — single line: `ALTER TABLE saved_pois ADD COLUMN vibe TEXT NOT NULL DEFAULT '';`. Nullable-in-practice (default `''` covers all existing rows).

---

## Implementation Plan

### Tasks

- [ ] **T1 — DB migration: add `vibe` column**
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/9.sqm` (NEW)
  - Action: Create file with single line: `ALTER TABLE saved_pois ADD COLUMN vibe TEXT NOT NULL DEFAULT '';`
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq`
  - Action: Add `vibe TEXT NOT NULL DEFAULT ''` as the last column in the `CREATE TABLE` statement. Add `:vibe` parameter to `insertOrReplace` query.

- [ ] **T2 — Domain model: add `vibe` field to `SavedPoi`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt`
  - Action: Add `val vibe: String = ""` field. Default value prevents breaking existing call sites.

- [ ] **T3 — Repository: thread `vibe` through insert + row mapping**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt`
  - Action (save, line ~58): Add `vibe = poi.vibe` to the `insertOrReplace(...)` call.
  - Action (observeAll row mapping, line ~36): Add `vibe = it.vibe ?: ""` to the `SavedPoi(...)` constructor.

- [ ] **T4 — Store `vibe` at save time in `MapViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action (savePoi, line ~181): In the `SavedPoi(...)` constructor, add:
    ```kotlin
    vibe = activeVibe?.name
        ?: poi.vibe.split(",").firstOrNull()?.trim() ?: "",
    ```
    where `activeVibe` is the current `(state as? MapUiState.Ready)?.activeVibe`. Note: `POI.vibe` is a CSV string like `"Character,History"` — this extracts the first entry as fallback.

- [ ] **T5 — Store `vibe` at save time in `ChatViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action (savePoi, line ~282): In the `SavedPoi(...)` constructor, add:
    ```kotlin
    vibe = _uiState.value.vibeName ?: "",
    ```
    `ChatUiState.vibeName` stores `Vibe.name` (uppercase, e.g., `"CHARACTER"`) — confirmed from `openChat` at line 99.

- [ ] **T6 — Add `savedVibeFilter` and `vibeAreaSaveCounts` to `MapUiState`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: In `MapUiState.Ready`, add two new fields at the end:
    ```kotlin
    val savedVibeFilter: Boolean = false,
    val vibeAreaSaveCounts: Map<Vibe, Int> = emptyMap(),
    ```

- [ ] **T7 — `MapViewModel`: `onSavedVibeSelected`, `vibeAreaSaveCounts`, `switchVibe` fix**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action A — Add private helper after `computeVibePoiCounts` (line ~783):
    ```kotlin
    private fun computeVibeAreaSaveCounts(saves: List<SavedPoi>, areaName: String): Map<Vibe, Int> =
        saves.filter { it.areaName == areaName && it.vibe.isNotEmpty() }
             .groupBy { save -> Vibe.entries.firstOrNull { it.name == save.vibe } }
             .filterKeys { it != null }
             .mapKeys { it.key!! }
             .mapValues { it.value.size }
    ```
  - Action B — In the `observeAll()` Flow collector in `init` (line ~77), after updating `savedPois` and `savedPoiCount`, also update `vibeAreaSaveCounts`:
    ```kotlin
    val current = _uiState.value as? MapUiState.Ready ?: return@collect
    _uiState.value = current.copy(
        savedPois = saves,
        savedPoiCount = saves.size,
        savedPoiIds = saves.map { it.id }.toSet(),
        vibeAreaSaveCounts = computeVibeAreaSaveCounts(saves, current.areaName),
    )
    ```
    Note: The existing init currently updates these in separate collectors — consolidate or add alongside existing pattern.
  - Action C — In `switchVibe(vibe)` at line 117, add `savedVibeFilter = false` to the state copy:
    ```kotlin
    fun switchVibe(vibe: Vibe) {
        val current = _uiState.value as? MapUiState.Ready ?: return
        _uiState.value = current.copy(
            activeVibe = if (current.activeVibe == vibe) null else vibe,
            savedVibeFilter = false,   // ADD THIS
        )
    }
    ```
  - Action D — Add new public function:
    ```kotlin
    fun onSavedVibeSelected() {
        val current = _uiState.value as? MapUiState.Ready ?: return
        val newFilter = !current.savedVibeFilter
        _uiState.value = current.copy(
            savedVibeFilter = newFilter,
            activeVibe = if (newFilter) null else current.activeVibe,
        )
        if (newFilter) openSavesSheet()
    }
    ```

- [ ] **T8 — `MapScreen`: pass new params to `VibeRail` + savedVibeFilter to `MapComposable`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action A — Update `MapComposable` call (line ~154): change `pois = state.pois` to:
    ```kotlin
    pois = if (state.savedVibeFilter) emptyList() else state.pois,
    ```
  - Action B — Update `VibeRail` call (line ~229):
    ```kotlin
    VibeRail(
        activeVibe = state.activeVibe,
        vibePoiCounts = state.vibePoiCounts,
        vibeAreaSaveCounts = state.vibeAreaSaveCounts,
        savedVibeActive = state.savedVibeFilter,
        totalAreaSaveCount = state.savedPoiIds.count { id ->
            state.savedPois.firstOrNull { it.id == id }?.areaName == state.areaName
        },
        onVibeSelected = { viewModel.switchVibe(it) },
        onSavedVibeSelected = { viewModel.onSavedVibeSelected() },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = navBarPadding + 88.dp),
    )
    ```
    Note: `totalAreaSaveCount` is the total saves in the current area (for the Saved orb badge). Compute inline from existing `savedPois` + `areaName` in state — no new state field needed.

- [ ] **T9 — `VibeOrb`: add optional `saveCount` badge overlay**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeOrb.kt`
  - Action: Add `saveCount: Int = 0` parameter to `VibeOrb`. After the `Box` circle block (line ~122), add count badge when `saveCount > 0`:
    ```kotlin
    // Wrap the existing Column content in a Box to allow badge overlay
    ```
    Specifically: wrap the entire `Column` in a `Box(contentAlignment = Alignment.TopEnd)` and add the badge as a sibling after the `Column`:
    ```kotlin
    Box {
        Column(...) { /* existing orb + label */ }
        if (saveCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp, end = 0.dp)
                    .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                    .background(Color(0xFF4a7cf7), CircleShape)
                    .border(1.5.dp, Color(0xFF0a0c10), CircleShape),
            ) {
                Text(
                    text = saveCount.toString(),
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    ```
    Pass `saveCount` through from `VibeRail` call site.

- [ ] **T10 — `VibeRail`: add Saved orb, separator, count badges, dynamic reorder**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt`
  - Action A — Update `VibeRail` signature:
    ```kotlin
    @Composable
    fun VibeRail(
        vibes: Array<Vibe> = Vibe.entries.toTypedArray(),
        activeVibe: Vibe?,
        vibePoiCounts: Map<Vibe, Int>,
        vibeAreaSaveCounts: Map<Vibe, Int> = emptyMap(),
        savedVibeActive: Boolean = false,
        totalAreaSaveCount: Int = 0,
        onVibeSelected: (Vibe) -> Unit,
        onSavedVibeSelected: () -> Unit = {},
        modifier: Modifier = Modifier,
    )
    ```
  - Action B — Replace the `Column` body with:
    ```kotlin
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Saved orb — pinned, not reordered
        SavedVibeOrb(
            isActive = savedVibeActive,
            isFilterActive = activeVibe != null,
            totalAreaSaveCount = totalAreaSaveCount,
            onClick = onSavedVibeSelected,
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 6 vibe orbs — dynamically reordered by area save count
        val sortedVibes = remember(vibeAreaSaveCounts) {
            vibes.sortedByDescending { vibeAreaSaveCounts[it] ?: 0 }
        }
        val minCount = vibePoiCounts.values.minOrNull() ?: 0
        val maxCount = vibePoiCounts.values.maxOrNull() ?: 0
        val isFilterActive = activeVibe != null || savedVibeActive

        for (vibe in sortedVibes) {
            val count = vibePoiCounts[vibe] ?: 0
            val sizeDp = computeVibeSizeDp(count, minCount, maxCount).dp
            VibeOrb(
                vibe = vibe,
                isActive = vibe == activeVibe,
                isFilterActive = isFilterActive,
                poiCount = count,
                sizeDp = sizeDp,
                saveCount = vibeAreaSaveCounts[vibe] ?: 0,
                onClick = { onVibeSelected(vibe) },
            )
        }
    }
    ```
  - Action C — Add private `SavedVibeOrb` composable in the same file:
    ```kotlin
    @Composable
    private fun SavedVibeOrb(
        isActive: Boolean,
        isFilterActive: Boolean,
        totalAreaSaveCount: Int,
        onClick: () -> Unit,
    ) {
        val reduceMotion = rememberReduceMotion()
        val breathingAlpha = if (isActive && !reduceMotion) {
            val transition = rememberInfiniteTransition(label = "saved_breathing")
            val alpha by transition.animateFloat(
                initialValue = 0.85f, targetValue = 1.0f,
                animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                label = "saved_breathing_alpha",
            )
            alpha
        } else 1.0f

        val isDimmed = isFilterActive && !isActive
        val goldColor = Color(0xFFFFD700)

        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(if (isDimmed) 0.45f else 1f)
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = "Saved places, $totalAreaSaveCount in this area" },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(Color(0xFFFFE26A), goldColor)),
                            shape = CircleShape,
                        )
                        .let {
                            if (isActive) it.border(2.dp, Color.White, CircleShape)
                                              .graphicsLayer { alpha = breathingAlpha }
                            else it
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "Saved",
                    fontSize = 10.sp,
                    color = if (isDimmed) Color.White.copy(alpha = 0.35f) else goldColor,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            // Gold count badge
            if (totalAreaSaveCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                        .background(goldColor, CircleShape)
                        .border(1.5.dp, Color(0xFF0a0c10), CircleShape),
                ) {
                    Text(
                        text = totalAreaSaveCount.toString(),
                        fontSize = 8.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    ```
  - Notes: Import `HorizontalDivider`, `Icons.Default.Bookmark`, `defaultMinSize`, `remember`. `isFilterActive` in `VibeOrb` call should be `activeVibe != null || savedVibeActive` — when Saved is active, regular vibes dim.

- [ ] **T11 — Unit tests**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt` (ADD tests to existing file)
  - Action: Add 4 tests using existing `runTest` + `UnconfinedTestDispatcher` + `FakeSavedPoiRepository` pattern:
    1. `vibeAreaSaveCounts_countsOnlyCurrentArea` — save 2 CHARACTER saves in "Area A", 1 HISTORY save in "Area B", load Area A → `vibeAreaSaveCounts[Vibe.CHARACTER] == 2`, `vibeAreaSaveCounts[Vibe.HISTORY] == null`
    2. `vibeAreaSaveCounts_emptyWhenNoVibeStored` — saves with `vibe = ""` → `vibeAreaSaveCounts.isEmpty()`
    3. `onSavedVibeSelected_activatesFilterAndOpensSavesSheet` — call `onSavedVibeSelected()` → `state.savedVibeFilter == true` AND `state.showSavesSheet == true` AND `state.activeVibe == null`
    4. `switchVibe_clearsSavedVibeFilter` — set `savedVibeFilter = true` via `onSavedVibeSelected()`, then call `switchVibe(Vibe.CHARACTER)` → `state.savedVibeFilter == false`

### Acceptance Criteria

- [ ] **AC1 — Saved orb always visible**: Given the map is in map mode (not list view), when the VibeRail renders, then a "Saved" orb with a gold bookmark icon appears pinned at the top of the rail, above a faint separator line, regardless of which vibe is active or how many saves exist.

- [ ] **AC2 — Saved orb count badge**: Given the user has saved at least 1 place in the current area, when the VibeRail renders, then a gold count badge appears on the top-right of the Saved orb showing the total number of saves in the current area. Given zero saves in the current area, no badge is shown.

- [ ] **AC3 — Saved chip tap: filter + open list**: Given the user taps the Saved orb, when it was previously inactive, then: (a) the map shows only gold DB pins (Gemini POIs disappear), (b) the SavedPlacesScreen overlay opens, (c) all regular vibe orbs dim to 45% alpha, (d) no Gemini API call is made.

- [ ] **AC4 — Saved chip tap: deactivate**: Given the Saved orb is currently active, when the user taps it again, then: (a) the gold-pin-only filter is cleared, (b) Gemini POIs reappear on the map, (c) the SavedPlacesScreen remains open (not force-closed), (d) regular vibe orbs return to full alpha.

- [ ] **AC5 — Mutual exclusion with vibe filter**: Given the Saved orb is active and the user taps any regular vibe orb, when the tap registers, then `savedVibeFilter` is cleared, the selected vibe becomes active, and the map shows Gemini POIs filtered to that vibe (not gold pins only).

- [ ] **AC6 — Per-vibe save count badges**: Given the user has saved places with stored `vibe` values, when the VibeRail renders, then each regular vibe orb with at least 1 area-scoped save shows a blue count badge (e.g., CHARACTER orb shows "2"). Orbs with zero saves show no badge.

- [ ] **AC7 — Dynamic reorder by save count**: Given the user has 2 CHARACTER saves and 1 HISTORY save in the current area, when the VibeRail renders, then CHARACTER orb appears first among the 6 regular vibes, HISTORY second, remaining vibes follow in default order. The Saved orb is always pinned above the separator regardless.

- [ ] **AC8 — New area resets reorder**: Given the user navigates to a new area with no saves, when the VibeRail renders, then the 6 vibe orbs appear in default `Vibe.entries` order with no badges.

- [ ] **AC9 — Vibe stored at save time (map side)**: Given a user saves a POI while the HISTORY vibe is active on the map, when the save is committed to the DB, then `SavedPoi.vibe == "HISTORY"`. Given no vibe is active, the first vibe from `POI.vibe` CSV is stored, or `""` if none.

- [ ] **AC10 — Vibe stored at save time (chat side)**: Given a user saves a POI from chat while the CHARACTER vibe context is active in `ChatUiState.vibeName`, when the save is committed to the DB, then `SavedPoi.vibe == "CHARACTER"`. Given `vibeName` is null, `SavedPoi.vibe == ""`.

- [ ] **AC11 — Existing saves unaffected**: Given saves that existed before this migration (no `vibe` value), when the DB migrates to schema version 9, then existing rows have `vibe = ""` (empty string default). The app does not crash. These saves show no per-vibe badge and do not affect reorder.

---

## Additional Context

### Dependencies

- `SavedPlacesScreen` (Part A) — already implemented and wired in MapScreen via `AnimatedVisibility(visible = state.showSavesSheet)`
- `MapViewModel.openSavesSheet()` at line 223 — exists, no changes needed to its body
- `savedPois: List<SavedPoi>` — already observed from `observeAll()` in MapViewModel init
- `POI.vibe: String` — exists as CSV string (e.g., `"Character,History"`)
- `ChatUiState.vibeName: String?` — stores `Vibe.name` (uppercase) set in `openChat()`
- `Vibe.entries` — all 6 vibes enumerable
- `FakeSavedPoiRepository` — already in `commonTest/fakes/`, has `save()`, `unsave()`, `observeAll()`, `observeSavedIds()`
- No new Koin modules, no new libraries, no platform actual changes needed

### Testing Strategy

Unit tests (`MapViewModelTest.kt` additions — 4 tests):
1. `vibeAreaSaveCounts_countsOnlyCurrentArea` — verifies area-scoped count computation
2. `vibeAreaSaveCounts_emptyWhenNoVibeStored` — verifies empty-vibe saves don't appear in counts
3. `onSavedVibeSelected_activatesFilterAndOpensSavesSheet` — verifies dual action (filter + open)
4. `switchVibe_clearsSavedVibeFilter` — verifies mutual exclusion

Manual test checklist:
- Save 2 places while CHARACTER vibe is active → reopen app → CHARACTER badge shows "2", CHARACTER orb floats to top of rail
- Save 1 place while no vibe active (POI.vibe has value) → verify badge appears on correct vibe
- Save from chat while HISTORY vibe context active → badge increments on HISTORY orb
- Tap Saved orb → gold filter active + SavedPlacesScreen opens; tap a vibe orb → filter clears, list stays open
- Tap Saved orb twice → filter toggles off, list stays open
- Navigate to new area → vibe orbs reset to default order, no badges
- Old saves (pre-migration): app launches without crash, no badges shown for those saves

### Notes

- "Saved is a vibe" product principle: Saved orb must feel peer-level, not secondary. Same visual grammar (circle, icon, label, breathing animation) as other orbs — just gold.
- `vibePoiCounts` (Gemini Stage 1 POI counts, used for orb SIZING) and `vibeAreaSaveCounts` (DB save counts, used for BADGES + REORDER) are parallel fields — never conflate.
- `savedVibeFilter` pin hiding works by passing `pois = emptyList()` to MapComposable when active. This is the simplest approach and avoids touching `MapComposable.android.kt` or `MapComposable.ios.kt`. Gold pins (from `savedPois` param) continue to render normally in both platform actuals.
- `totalAreaSaveCount` for the Saved orb badge is computed inline at the MapScreen call site from `state.savedPois.count { it.areaName == state.areaName }` — no new state field needed.
- `ChatPoiCard` intentionally has no vibe field (compact serialization for API) — this is by design. `vibeName` from `ChatUiState` is the correct proxy.
- `remember(vibeAreaSaveCounts)` in VibeRail ensures sort only recomputes when save counts change, not on every recomposition.
- Mockup at `/tmp/saved-chip-mockup.html`
- Part A spec: `tech-spec-saved-places-list-redesign.md` (implementation-complete)
- Brainstorm ref: `_bmad-output/brainstorming/brainstorming-session-2026-03-11-001.md` ideas #18, #19, #20
