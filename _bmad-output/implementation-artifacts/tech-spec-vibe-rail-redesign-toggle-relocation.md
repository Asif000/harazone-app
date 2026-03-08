---
title: 'Vibe Rail Redesign + Map/List Toggle Relocation'
slug: 'vibe-rail-redesign-toggle-relocation'
created: '2026-03-07'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Compose UI Graphics (Brush/lerp)']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/VibeOrb.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/VibeRail.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/FabMenu.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/MapListToggle.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/components/VibeRailSizingTest.kt'
code_patterns:
  - 'Vibe.toColor() in ui/theme/Color.kt — import com.areadiscovery.ui.theme.toColor'
  - 'Vibe.displayName — enum property, no import needed'
  - 'MapFloatingUiDark.copy(alpha=0.80f) — toggle background matches AISearchBar'
  - 'AnimatedVisibility(fadeIn/fadeOut) — used for MyLocation button hide, reuse pattern for toggle'
  - 'WindowInsets.navigationBars — already used for navBarPadding in MapScreen'
test_patterns:
  - 'No existing tests for VibeOrb/VibeRail — add sizing formula unit test in commonTest'
  - 'commonTest location: composeApp/src/commonTest/kotlin/com/areadiscovery/'
---

# Tech-Spec: Vibe Rail Redesign + Map/List Toggle Relocation

**Created:** 2026-03-07

## Overview

### Problem Statement

The vibe rail shows icon-only circular orbs with no text labels — users cannot tell what each vibe represents without tapping. The Map/List toggle is buried inside the FAB expandable menu, making it undiscoverable. Both issues reduce the core usability of the two most-used navigation elements in the app.

### Solution

Replace the icon-only orbs with round radial-gradient circles + text labels below, dynamically sized (32–48dp) by POI count per vibe. Move the Map/List toggle to an always-visible segmented control inline between the search bar and FAB. The FAB stays with its remaining 2 items (Saved Places, Settings).

### Scope

**In Scope:**
- `VibeOrb.kt`: radial gradient fill, text label below circle, dynamic sizing (32–48dp), 3-state visual (default-glow / active / dimmed), remove count badge
- `VibeRail.kt`: compute min/max POI counts across all vibes, derive dp size per vibe, pass to VibeOrb
- `FabMenu.kt`: remove the List/Map toggle menu item and its `onListMapToggle` callback
- `MapScreen.kt`: add `MapListToggle` composable between search bar and FAB, adjust search bar `end` padding to accommodate toggle

**Out of Scope:**
- Phase A dynamic vibes (Room DB, engagement reorder, ghost vibes)
- POIListView chip/filter row changes
- New animations beyond existing breathing/scale
- iOS-specific layout changes

## Context for Development

### Codebase Patterns

- **Compose Multiplatform** — all UI is in `commonMain`. No platform-specific composables for this feature.
- **`Vibe.toColor()`** — extension in `ui/theme/Color.kt:26`. Import: `com.areadiscovery.ui.theme.toColor`. Vibe colors: CHARACTER=#2BBCB3 (teal), HISTORY=#C4935A (amber), WHATS_ON=#9B6ED8 (purple), SAFETY=#E8A735 (yellow), NEARBY=#5B9BD5 (blue), COST=#5CAD6F (green). These are mid-saturation — `lerp(vibeColor, White, 0.4f)` produces a clean lighter tint for radial gradient center.
- **`Vibe.displayName`** — enum property (e.g. `"Character"`, `"What's On"`). No import needed. Already used in `VibeOrb` for `semantics { contentDescription }`.
- **`Vibe.orbIconName`** — string field (`"palette"`, `"history"`, etc.) currently unused in UI. `toImageVector()` extension in `VibeOrb.kt` handles icon mapping. No change needed.
- **`rememberReduceMotion()`** — already used in `VibeOrb`. Keep respecting it for breathing animation.
- **`activeVibe: Vibe?`** — null means "all vibes shown" (default/all-glow state). `switchVibe(vibe)` in VM already toggles to null if same vibe tapped — no VM changes needed.
- **`vibePoiCounts: Map<Vibe, Int>`** — already passed into `VibeRail`. Use this to compute dynamic sizing.
- **`MapFloatingUiDark = Color(0xFF0A0A0A)`** — near-black. `AISearchBar` uses `MapFloatingUiDark.copy(alpha = 0.80f)`. `MapListToggle` must use the same color + alpha for visual consistency.
- **`AnimatedVisibility` hide pattern** — `MyLocation` button uses `AnimatedVisibility(visible = !state.isSearchOverlayOpen, enter = fadeIn(), exit = fadeOut())`. Reuse exact same pattern for `MapListToggle`.
- **Bottom bar layout in `MapScreen`**: `AISearchBar` uses `Alignment.BottomStart` with `padding(end = 80.dp)`. `MapListToggle` at `Alignment.BottomEnd` with `padding(end = 80.dp)`. Increasing search bar `end` to `168.dp` leaves correct gap on 360dp+ screens.
- **No `project-context.md` found** — not generated yet for this project.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `ui/map/components/VibeOrb.kt` | Current orb — replace visual, add label, dynamic size |
| `ui/map/components/VibeRail.kt` | Passes vibePoiCounts — add sizing computation here |
| `ui/map/components/FabMenu.kt` | Remove toggle item + callback |
| `ui/map/MapScreen.kt` | Wire toggle, adjust search bar padding |
| `ui/theme/Color.kt` | `Vibe.toColor()` extension at line 26, all vibe color constants |
| `domain/model/Vibe.kt` | Enum with `displayName`, `accentColorHex`, `orbIconName` properties |
| `ui/map/components/AISearchBar.kt` | Search bar — uses `MapFloatingUiDark.copy(0.80f)`, match in toggle |

### Technical Decisions

| # | Decision | Detail |
|---|----------|--------|
| 1 | Gradient style | `Brush.radialGradient` with light color at center, vibe color at edge. Light color = `vibe.toColor().copy(alpha=1f)` lightened via `lerp` with White at 0.4f. |
| 2 | Dynamic sizing formula | `sizeDp = if (maxCount == minCount) 40f else (32 + 16 * (count - minCount) / (maxCount - minCount)).coerceIn(32, 48)`. Edge cases: all-same → 40dp; zero POIs → 32dp; out-of-range inputs clamped. |
| 3 | 3-state visual logic | **Default** (`activeVibe == null`): all circles show radial glow + vibe-colored label. **Active** (`vibe == activeVibe`): radial glow + white border ring + white bold label. **Dimmed** (`activeVibe != null && vibe != activeVibe`): opacity 0.45, no glow. |
| 4 | Text label | `10.sp` (validate on device — 9sp is borderline illegible on small circles at arm's length), constant size (does not scale with circle), vibe color in default state, white in active, `rgba(255,255,255,0.35)` in dimmed. |
| 5 | Count badge | Remove entirely — dynamic sizing replaces it as the relevance signal. |
| 6 | Toggle component | New `MapListToggle` composable in `components/`. Two `IconButton`s inside a `Row` with `RoundedCornerShape(50)` background matching `MapFloatingUiDark`. Active button gets `rgba(white, 0.18)` background highlight. |
| 7 | Search bar padding | Currently `end = 80.dp`. New value: `end = 168.dp` (80dp FAB + 8dp gap + 72dp toggle + 8dp breathing room). On 360dp-wide devices this leaves an 8dp gap between search bar end and toggle start — verified safe. Toggle width = 2 × 36dp icons + 2 × 4dp padding = ~72dp. |
| 8 | FAB changes | Remove `onListMapToggle` param and `showListView` param from `FabMenu`. Remove the `FabMenuItem` for map/list toggle. |

## Implementation Plan

### Tasks

- [x] **Task 1 — Update `VibeOrb.kt`** — Replace flat orb with radial-gradient labeled circle
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/VibeOrb.kt`
  - Add params: `sizeDp: Dp` (replaces hardcoded `48.dp`), `isFilterActive: Boolean`
  - Add imports: `import androidx.compose.ui.graphics.Brush`, `import androidx.compose.ui.graphics.lerp`, `import androidx.compose.ui.text.font.FontWeight`, `import androidx.compose.foundation.border`
  - Replace `background(color = vibeColor.copy(alpha = bgAlpha * breathingAlpha), shape = CircleShape)` with `background(brush = Brush.radialGradient(listOf(lerp(vibeColor, Color.White, 0.4f), vibeColor)), shape = CircleShape)`
  - **Breathing animation**: `breathingAlpha` previously modulated color alpha — that no longer works with `Brush`. Instead apply it via `Modifier.graphicsLayer { alpha = breathingAlpha }` on the circle `Box` (active state only). In dimmed/default states, do not apply graphicsLayer alpha — let `Modifier.alpha(0.45f)` handle dimming instead.
  - Active state: add `Modifier.border(2.dp, Color.White, CircleShape)` on the circle `Box`; apply `Modifier.graphicsLayer { alpha = breathingAlpha }` for the breathing pulse
  - Dimmed state (`isFilterActive && !isActive`): apply `Modifier.alpha(0.45f)` on the outer `Column`, remove `breathingAlpha` and `bgAlpha` variables (not used in this state)
  - Default state (`activeVibe == null`, i.e. `!isFilterActive`): no alpha modifier, full opacity radial gradient for all circles
  - Remove count badge `Box` entirely (lines 95–110 in current file)
  - Wrap the circle `Box` in a `Column(horizontalAlignment = Alignment.CenterHorizontally)`
  - Add `Text` below circle: `text = vibe.displayName`, `fontSize = 10.sp` (validate on device), color = `vibeColor` in default, `Color.White` + `fontWeight = FontWeight.Bold` in active, `Color.White.copy(alpha = 0.35f)` in dimmed
  - Update `Modifier.size(48.dp)` → `Modifier.size(sizeDp)`
  - Add `Modifier.minimumInteractiveComponentSize()` to the `Column` to ensure tap target meets the 48dp Material minimum even when circle renders at 32dp
  - Keep `poiCount: Int` param for a11y semantics only (badge removed, count still in contentDescription)

- [x] **Task 2 — Update `VibeRail.kt`** — Compute dynamic sizing, pass new params to VibeOrb
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/VibeRail.kt`
  - Extract top-level function (for testability): `fun computeVibeSizeDp(count: Int, minCount: Int, maxCount: Int): Float = 32f + 16f * (count - minCount) / maxOf(1, maxCount - minCount).toFloat()` — returns a Float dp-value, call site uses `.dp` extension
  - **`activeVibe: Vibe?` already exists in `VibeRail` signature — do NOT add it again.** Just use it.
  - In body: `val counts = vibePoiCounts.values; val minCount = counts.minOrNull() ?: 0; val maxCount = counts.maxOrNull() ?: 0`
  - Per vibe: `val sizeDp = computeVibeSizeDp(vibePoiCounts[vibe] ?: 0, minCount, maxCount).dp`
  - Pass to each `VibeOrb`: `sizeDp = sizeDp`, `isFilterActive = activeVibe != null`
  - Remove `modifier.padding(bottom = 72.dp)` from the `Column` — `MapScreen` already controls outer bottom padding

- [x] **Task 3 — Create `MapListToggle.kt`** — New always-visible map/list toggle composable
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/MapListToggle.kt`
  - Signature: `fun MapListToggle(showListView: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier)`
  - Outer `Box(modifier)`: `clip(RoundedCornerShape(50))` + `background(MapFloatingUiDark.copy(alpha = 0.80f))` + `border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))`
  - Inner `Row(verticalAlignment = CenterVertically, modifier = Modifier.padding(4.dp))`
  - Map `IconButton(onClick = onToggle, enabled = showListView, modifier = Modifier.size(36.dp))`: background `Color.White.copy(0.18f)` + `CircleShape` when `!showListView`, icon `Icons.Default.Map` size `20.dp`, tint white full alpha when active (`!showListView`) / `0.45f` inactive, contentDescription `"Map view"`. Using `enabled = showListView` disables the ripple when already on map view — avoids confusing no-op tap.
  - List `IconButton(onClick = onToggle, enabled = !showListView, modifier = Modifier.size(36.dp))`: same pattern mirrored, icon `Icons.AutoMirrored.Default.List`, contentDescription `"List view"`

- [x] **Task 4 — Update `FabMenu.kt`** — Remove map/list toggle item
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/FabMenu.kt`
  - Remove `showListView: Boolean` parameter from `FabMenu` signature
  - Remove `onListMapToggle: () -> Unit` parameter from `FabMenu` signature
  - Remove the `FabMenuItem` block for map/list (lines 67–71 in current file)
  - Remove now-unused imports: `androidx.compose.material.icons.filled.Map` and `androidx.compose.material.icons.automirrored.filled.List` (exact paths as they appear in `FabMenu.kt`)

- [x] **Task 5 — Update `MapScreen.kt`** — Wire toggle, adjust search bar padding
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt`
  - Add import: `import com.areadiscovery.ui.map.components.MapListToggle`
  - Add after MyLocation button block:
    ```kotlin
    AnimatedVisibility(
        visible = !state.isSearchOverlayOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = navBarPadding + 16.dp, end = 80.dp),
    ) {
        MapListToggle(
            showListView = state.showListView,
            onToggle = { viewModel.toggleListView() },
        )
    }
    ```
  - Update `AISearchBar` modifier: `end = 80.dp` → `end = 168.dp`
  - Update `FabMenu` call: remove `showListView = state.showListView`, remove `onListMapToggle = { viewModel.toggleFab(); viewModel.toggleListView() }`
  - `VibeRail` call site already passes `activeVibe` — no change needed

- [x] **Task 6 — Add sizing unit test**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/components/VibeRailSizingTest.kt`
  - Test `computeVibeSizeDp()` cases: normal range, all-equal → 40dp, zero count → 32dp, only one vibe has POIs (rest zero), single-vibe scenario, maxCount == minCount (no div-by-zero)

### Acceptance Criteria

- [x] **AC1 — Vibe labels visible:** Given the map screen is open, when the vibe rail is visible, then each vibe circle has its display name rendered in text (10sp) below it at all times.

- [x] **AC2 — Radial gradient fill:** Given any vibe circle, when rendered, then it shows a radial gradient from a lighter tint at center to the vibe's brand color at the edge (not a flat fill).

- [x] **AC3a — Dynamic sizing (varied counts):** Given vibePoiCounts = `{CHARACTER:6, HISTORY:4, WHATS_ON:5, SAFETY:2, NEARBY:3, COST:1}`, when the rail renders, then CHARACTER circle is largest (~48dp), COST is smallest (~32dp), others are proportionally between.

- [x] **AC3b — Dynamic sizing (equal counts):** Given all vibes have equal POI count, then all circles render at 40dp (midpoint).

- [x] **AC3c — Dynamic sizing (zero POIs):** Given a vibe has 0 POIs, then it renders at 32dp. (Note: 0.35 opacity deferred — design decision: default state shows full opacity for all, sizing alone signals relevance.)

- [x] **AC3d — Dynamic sizing (one vibe only):** Given only one vibe has POIs and all others are zero, then that vibe renders at 48dp and all others at 32dp dimmed — correct expected behaviour.

- [x] **AC4 — Default all-glow state:** Given no vibe is selected (`activeVibe == null`), when the rail renders, then all circles show radial gradient glow with vibe-colored labels and none are dimmed.

- [x] **AC5 — Filtered state:** Given a vibe is selected (`activeVibe != null`), when the rail renders, then the active vibe shows white border ring + white bold label, and all others show at 0.45 opacity with muted labels.


- [x] **AC6 — Deselect:** Given a vibe is active, when the user taps it again, then `activeVibe` returns to null and the rail returns to the all-glow default state.

- [x] **AC7 — No count badge:** Given any vibe circle, when rendered, then no count number badge is visible on the circle.

- [x] **AC8 — Map/List toggle always visible:** Given the map screen is in Ready state and search overlay is closed, when the user is on either map or list view, then the Map/List toggle is visible in the bottom bar without opening the FAB menu.

- [x] **AC9 — Toggle state reflects active view:** Given the app is in map view, then the map icon button is highlighted. Given the app is in list view, then the list icon button is highlighted.

- [x] **AC10 — FAB has 2 items:** Given the FAB is tapped and expanded, when the menu appears, then it shows exactly 2 items: Saved Places and Settings. No Map/List toggle item present.

- [x] **AC11 — Search bar not obscured:** Given the bottom bar is rendered with toggle visible, then the search bar and toggle do not overlap (`end` padding increased to 168dp).

- [x] **AC12 — Toggle hidden when search overlay open:** Given the search overlay is open (`isSearchOverlayOpen == true`), then the Map/List toggle is not visible (same behaviour as the MyLocation button).

## Additional Context

### Dependencies

- No new libraries required — `Brush.radialGradient` is in `compose.ui.graphics`, already available.
- `lerp(Color, Color, Float)` is in `androidx.compose.ui.graphics.lerp` — confirm it's imported or use manual calculation `Color(r1+(r2-r1)*t, …)`.

### Testing Strategy

- **Unit tests**: Pure sizing formula — extract as top-level function `fun computeVibeSizeDp(count: Int, minCount: Int, maxCount: Int): Float` in `VibeRail.kt` and test in `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/components/VibeRailSizingTest.kt`. Cover: normal range, all-equal counts, zero POIs, only one vibe has POIs, outlier distribution.
- **Compose UI tests**: Not required for this iteration (design still iterating). Rely on device testing + code review.
- **Device test**: After install, verify: (1) labels readable on physical screen, (2) toggle switches views, (3) FAB shows 2 items only, (4) dynamic sizing visually distinguishable.

### Notes

- **Scale animation decision (F10):** Remove `Modifier.scale(scale)` entirely from `VibeOrb`. With dynamic sizing and a text label below the circle, scaling the entire `Column` looks awkward and conflicts with the sizing signal. The white border ring + bold label are sufficient active-state indicators. Delete the `animateFloatAsState` for `scale` and its usage.
- **VibeRail padding removal (F11):** `VibeRail` is only used in `MapScreen.kt` (confirmed — not in `POIListView` or any other screen). Removing `modifier.padding(bottom = 72.dp)` from the `Column` is safe.
- `toggleListView()` in VM is already a public function — no VM changes needed.

## Review Notes
- Adversarial review completed
- Findings: 12 total, 5 fixed, 3 skipped (noise), 4 deferred (low/pre-existing)
- Resolution approach: auto-fix
- Fixed: F1/F3 output clamping `.coerceIn(32f, 48f)`, F2 a11y semantics (`Role.Tab` + `selected`), F8 dual-alpha comment, F9 edge case tests
- Skipped as noise: F6 (spec says no Compose UI tests), F10 (magic numbers documented in spec), F11 (Row is better than Box), F12 (pre-existing FabScrim)
- Deferred: F4 (callback guard — low risk), F5 (icon scaling — validate on device), F7 (dead code — pre-existing)
