---
title: 'Chip Cloud Dashboard — v1'
slug: 'chip-cloud-dashboard-v1'
created: '2026-03-06'
status: 'superseded'
superseded_by: 'tech-spec-v3-fullscreen-map'
superseded_note: 'V3 retires SummaryScreen entirely. ChipCloud, DiscoveryChip, and related components targeting SummaryScreen will be deleted as part of V3.'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Material3', 'Koin', 'Coroutines/Flow']
files_to_modify: [
  'composeApp/src/commonMain/.../ui/summary/SummaryScreen.kt',
  'composeApp/src/commonMain/.../ui/components/TimelineCard.kt',
  'composeApp/src/commonTest/.../ui/components/TimelineCardTest.kt'
]
files_to_create: [
  'composeApp/src/commonMain/.../ui/summary/TimelineEraParser.kt',
  'composeApp/src/commonMain/.../ui/summary/DiscoveryChip.kt',
  'composeApp/src/commonMain/.../ui/summary/ChipExtractor.kt',
  'composeApp/src/commonMain/.../ui/components/ChipCloud.kt',
  'composeApp/src/commonMain/.../ui/components/MiniTimelineStrip.kt',
  'composeApp/src/commonMain/.../ui/components/AlertBanner.kt',
  'composeApp/src/commonTest/.../ui/summary/ChipExtractorTest.kt',
  'composeApp/src/commonTest/.../ui/summary/TimelineEraParserTest.kt'
]
files_to_delete: [
  'composeApp/src/commonMain/.../ui/components/BucketCard.kt',
  'composeApp/src/commonMain/.../ui/components/BucketSectionHeader.kt',
  'composeApp/src/commonMain/.../ui/components/HighlightFactCallout.kt'
]
code_patterns: [
  'FlowRow from androidx.compose.foundation.layout — already in commonMain deps (libs.compose.foundation)',
  'rememberReduceMotion() — expect/actual in ReduceMotion.kt; gate all animations with it',
  'HeroLoadingPlaceholder — internal to TimelineCard.kt; NOT used by new components',
  'BucketType.icon() + BucketType.displayTitle() — from BucketTypeMapper.kt',
  'parseTimelineEras() — currently internal in TimelineCard.kt; extract to TimelineEraParser.kt',
  'MaterialTheme.spacing.* — all spacing via extension, no raw dp except FlowRow arrangement gaps',
  '@OptIn(ExperimentalLayoutApi::class) required for FlowRow',
  '@OptIn(ExperimentalMaterial3Api::class) required for ModalBottomSheet and TooltipBox'
]
test_patterns: [
  'kotlin.test assertions (assertEquals, assertIs, assertTrue, assertNull)',
  'No MockK needed for ChipExtractor/TimelineEraParser — pure functions',
  'Turbine for Flow tests (used in SummaryViewModelTest)',
  'No Compose UI tests in commonTest — only logic/pure-function tests'
]
---

# Tech-Spec: Chip Cloud Dashboard — v1

**Created:** 2026-03-06

## Overview

### Problem Statement

The current `SummaryScreen` renders a vertical `LazyColumn` of `BucketCard` items. Content is buried behind scroll — the user must scroll to discover 6 buckets of area knowledge. There is no streaming reveal effect (content just appears), no visual hierarchy between ordinary facts and wow moments, and no "constellation forming" feeling as AI buckets arrive. `RightNowCard` and `TimelineCard` compete for hero space with the card list below them.

### Solution

Replace `BucketList` (private `LazyColumn` composable inside `SummaryScreen`) with a `ChipCloud` (`FlowRow`) that streams chips in progressively as each bucket completes. HISTORY bucket renders as a special `MiniTimelineStrip` chip. Wow chips (heuristic) are visually larger with a pulse glow on entrance. Number chips animate count-up. A shimmer wave sweeps all chips when the full portrait loads. `RightNowCard` is replaced by a conditional `AlertBanner` (only shown when urgency is present). `TimelineCard` is removed — its data lives in the `MiniTimelineStrip` chip.

### Scope

**In Scope:**
- `DiscoveryChip` data class
- `ChipExtractor` — pure functions: one chip per bucket from `highlightText` (v1 design decision — see Notes)
- Extract `parseTimelineEras()` + `TimelineEra` from `TimelineCard.kt` into shared `TimelineEraParser.kt`
- `MiniTimelineStrip` composable — wider chip variant showing compressed era year markers, tapping opens `ModalBottomSheet` with full `LazyRow` timeline
- `ChipCloud` composable using `FlowRow` with per-chip animated entry (`AnimatedVisibility` / `animateContentSize`)
- Count-up animation for number chips (~400ms, detected via regex on `highlightText`)
- Constellation shimmer wave across all chips when `isComplete = true` on `SummaryUiState`
- Wow chip visual treatment — heuristic: HISTORY always wow; any bucket where `highlightText.length > 80` is wow. Wow = larger chip + single-cycle border pulse glow on entrance
- Chip tap interactions: tooltip popup for simple chips, `ModalBottomSheet` (showing `bodyText`) for rich content (CHARACTER, HISTORY, NEARBY)
- `AlertBanner` composable — replaces `RightNowCard`; only rendered when WHATS_HAPPENING bucket `highlightText` is non-blank and `isComplete`
- Wire `ChipCloud` + `AlertBanner` into `SummaryScreen`, replacing `BucketList`
- Delete: private `BucketList` composable in `SummaryScreen.kt`, `BucketCard.kt`, `BucketSectionHeader.kt`, "More about this place" label item
- `TimelineCard` removed from `SummaryScreen` (its data absorbed into `MiniTimelineStrip` chip)
- `InlineChatPrompt` preserved — shown below `ChipCloud` when `isComplete`

**Out of Scope:**
- Tab bar / `BottomNavBar` removal (stays as-is)
- Bottom search bar / `SearchBottomSheet`
- Preference tuner sliders / `DataStore` persistence
- Embedded hyper-local map in dashboard
- Share card generation (`drawToBitmap`)
- "New since yesterday" badges
- Fact counter / knowledge portfolio UI
- `DashboardScreen` root restructure
- AI prompt change for machine-emitted surprise score (deferred — see Notes)
- Chip↔map bidirectional linking

## Context for Development

### Codebase Patterns

- All UI is Compose Multiplatform (`commonMain`) — no platform-specific UI code
- `SummaryViewModel` drives `SummaryUiState` (sealed class) via `StateFlow`; UI observes with `collectAsStateWithLifecycle()`
- `BucketDisplayState` is the per-bucket data carrier: `highlightText`, `bodyText`, `isStreaming`, `isComplete`
- Buckets arrive sequentially — each `BucketUpdate.BucketComplete` sets `isComplete = true` on one bucket; `BucketUpdate.PortraitComplete` finalises all
- `BucketType` enum: SAFETY, CHARACTER, WHATS_HAPPENING, COST, HISTORY, NEARBY — icons + display titles in `BucketTypeMapper.kt`
- `parseTimelineEras()` already exists as `internal fun` in `TimelineCard.kt` — needs extraction before reuse
- `rememberReduceMotion()` helper exists in `TimelineCard.kt` — use it to gate all animations
- `MaterialTheme.spacing.*` extension used for all spacing (not raw dp values)
- `@OptIn(ExperimentalLayoutApi::class)` required for `FlowRow`
- DI via Koin — new composables don't need Koin; `ChipExtractor` is pure functions (no DI needed)
- Tests: `commonTest`, MockK for mocks, Turbine for Flow testing, Truth for assertions

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/.../ui/summary/SummaryScreen.kt` | Replace `BucketList` with `ChipCloud` + `AlertBanner` |
| `composeApp/src/commonMain/.../ui/summary/SummaryUiState.kt` | Sealed state — `Streaming.buckets`, `Complete.buckets` drive chip rendering |
| `composeApp/src/commonMain/.../ui/summary/BucketDisplayState.kt` | Per-bucket data carrier used by `ChipExtractor` |
| `composeApp/src/commonMain/.../ui/summary/SummaryViewModel.kt` | No changes needed — exposes same `uiState` Flow |
| `composeApp/src/commonMain/.../ui/components/TimelineCard.kt` | Extract `parseTimelineEras()` + `TimelineEra` from here |
| `composeApp/src/commonMain/.../ui/components/BucketTypeMapper.kt` | `BucketType.icon()` + `BucketType.displayTitle()` used by `ChipExtractor` |
| `composeApp/src/commonMain/.../ui/components/RightNowCard.kt` | DO NOT DELETE — used by `SearchScreen.kt`; only removed from `SummaryScreen` |
| `composeApp/src/commonMain/.../ui/components/BucketCard.kt` | DELETE — only used by `SummaryScreen` (confirmed via grep) |
| `composeApp/src/commonMain/.../ui/components/BucketSectionHeader.kt` | DELETE — only used by `BucketCard.kt` (confirmed via grep) |
| `composeApp/src/commonMain/.../ui/components/HighlightFactCallout.kt` | DELETE — only used by `BucketCard.kt` (confirmed via grep) |
| `composeApp/src/commonMain/.../domain/model/Bucket.kt` | `BucketType` enum source of truth |

### Technical Decisions

1. **One chip per bucket (v1):** `ChipExtractor` emits exactly one `DiscoveryChip` per `BucketDisplayState` using `highlightText`. Multi-chip extraction from `bodyText` is explicitly deferred — the data model supports it but v1 keeps it simple.
2. **Wow detection — heuristic:** No AI prompt change in this story. HISTORY bucket is always wow. Any other bucket where `highlightText.length > 80` chars is wow. This heuristic is replaced by AI-emitted surprise score (1–5) in a future story.
3. **`parseTimelineEras()` extraction:** Currently `internal` in `TimelineCard.kt`. Must be moved to `TimelineEraParser.kt` (same `summary` package) so `ChipExtractor` can call it. `TimelineCard.kt` updates its own reference to import from new location.
4. **`FlowRow` for chip layout:** Requires `@OptIn(ExperimentalLayoutApi::class)`. Use `androidx.compose.foundation.layout.FlowRow`. `libs.compose.foundation` is already a `commonMain` dependency — no build change needed.
5. **Chip weight ordering:** For v1, chips are ordered by `BucketType.ordinal` (SAFETY first, NEARBY last) — the natural enum order. Preference-adjusted reweighting is deferred to the preference tuner story.
6. **`AlertBanner` replaces `RightNowCard` in `SummaryScreen`:** New composable, simpler — shows `highlightText` from WHATS_HAPPENING bucket when non-blank + complete. `RightNowCard.kt` file is NOT deleted — `SearchScreen.kt` uses it (lines 51, 239).
7. **`TimelineCard` NOT deleted:** `SearchScreen.kt` uses `TimelineCard` (lines 52, 246). File stays. Only removed from `SummaryScreen.kt`. `parseTimelineEras()` is extracted but `TimelineCard.kt` remains fully functional.
8. **`HeroLoadingPlaceholder` stays in `TimelineCard.kt`:** Only referenced by `RightNowCard.kt` and `TimelineCard.kt`. Both files are kept, so no orphan risk.
9. **`updateScrollDepth` call removed from `SummaryScreen`:** The existing tracking relied on `LazyListState` inside `BucketList`. `ChipCloud` uses `FlowRow` (not `LazyColumn`) so there is no scroll state to track. The `SummaryViewModel.updateScrollDepth()` method is unchanged — call site in `SummaryScreen` is simply removed. Analytics `summary_viewed` event (fired on `PortraitComplete`) is unaffected.
10. **Files safe to delete:** `BucketCard.kt`, `BucketSectionHeader.kt`, `HighlightFactCallout.kt` — grep confirms these are only referenced by each other and `SummaryScreen.kt`. No other consumers.

## Implementation Plan

### Tasks

Tasks ordered lowest-level dependency first. Full package path: `composeApp/src/commonMain/kotlin/com/areadiscovery/` (abbreviated as `src/main/` below for readability — use full path when creating files).

- [x] **T1 — Extract `TimelineEraParser.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/TimelineEraParser.kt` (CREATE)
  - Action: Move `internal data class TimelineEra(val year: Int, val sentence: String)` and `internal fun parseTimelineEras(content: String): List<TimelineEra>` out of `TimelineCard.kt` into this new file. Keep `internal` visibility. Keep `YEAR_REGEX` and `SENTENCE_SPLIT_REGEX` private vals in this file.
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/TimelineCard.kt` (MODIFY)
  - Action: Remove the now-duplicate `TimelineEra`, `parseTimelineEras`, `YEAR_REGEX`, `SENTENCE_SPLIT_REGEX` declarations. Add import for `TimelineEra` and `parseTimelineEras` from `com.areadiscovery.ui.summary`. Verify file still compiles.
  - Notes: `TimelineCardTest.kt` is in the same module — it will still be able to access `internal` declarations from `TimelineEraParser.kt` via the same source set.

- [x] **T2 — `DiscoveryChip` data class**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/DiscoveryChip.kt` (CREATE)
  - Action: Create with exact definition:
    ```kotlin
    package com.areadiscovery.ui.summary

    import com.areadiscovery.domain.model.BucketType

    data class DiscoveryChip(
        val bucketType: BucketType,
        val label: String,        // from BucketDisplayState.highlightText
        val detail: String,       // from BucketDisplayState.bodyText (bottom sheet content)
        val isWow: Boolean,       // HISTORY always true; others true if label.length > 80
        val isNumber: Boolean,    // true if label starts with a digit sequence
        val numberValue: Int?,    // parsed leading number for count-up animation; null otherwise
        val eraYears: List<Int>,  // non-empty only for HISTORY chip; up to 4 years from parseTimelineEras
    )
    ```

- [x] **T3 — `ChipExtractor` pure functions**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/ChipExtractor.kt` (CREATE)
  - Action: Implement two functions:
    1. `fun extractChips(buckets: Map<BucketType, BucketDisplayState>): List<DiscoveryChip>` — iterates `BucketType.entries` order; calls `toDiscoveryChip()` for each bucket where `isComplete = true` AND `highlightText.isNotBlank()`; skips incomplete or blank buckets.
    2. `internal fun BucketDisplayState.toDiscoveryChip(): DiscoveryChip`:
       - `isWow = bucketType == BucketType.HISTORY || highlightText.length > 80`
       - Number detection: `val numMatch = Regex("^(\\d[\\d,]*)").find(highlightText.trim())` — if non-null, `isNumber = true`, `numberValue = numMatch.groupValues[1].replace(",", "").toIntOrNull()`
       - For HISTORY: `eraYears = parseTimelineEras(bodyText).map { it.year }.take(4)`
       - For others: `eraYears = emptyList()`
       - `label = highlightText`, `detail = bodyText`
  - Notes: No imports of Compose needed — pure Kotlin.

- [x] **T4 — `MiniTimelineStrip` composable**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/MiniTimelineStrip.kt` (CREATE)
  - Action: Implement `@Composable fun MiniTimelineStrip(chip: DiscoveryChip, modifier: Modifier = Modifier)`:
    - Renders a `SuggestionChip` that is wider than normal chips (use `Modifier.widthIn(min = 200.dp)`)
    - Chip label: join `chip.eraYears` into `"1847 · 1923 · 1987 · Now"` — always append `"· Now"` at the end. If `eraYears` is empty, show `"History"` as fallback.
    - Leading icon: `Icons.Filled.History`
    - State: `var showSheet by remember { mutableStateOf(false) }`
    - On click: `showSheet = true`
    - When `showSheet`: show `ModalBottomSheet(onDismissRequest = { showSheet = false })` containing a `LazyRow` of era cards — each card shows `era.year` as `titleLarge` + `era.sentence` as `bodySmall` (max 3 lines). Reuse the card style from existing `TimelineCard.kt` era cards (outlined card, 200.dp width, `spacing.md` padding).
    - Requires `@OptIn(ExperimentalMaterial3Api::class)`
    - Pass `chip.detail` (bodyText) to `parseTimelineEras()` inside the bottom sheet to regenerate full era list.

- [x] **T5 — `AlertBanner` composable**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/AlertBanner.kt` (CREATE)
  - Action: Implement `@Composable fun AlertBanner(state: BucketDisplayState?, modifier: Modifier = Modifier)`:
    - Only renders when `state?.isComplete == true && state.highlightText.isNotBlank()` — otherwise renders nothing (`return`)
    - Layout: `Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium)` wrapping a `Row` with `Alignment.CenterVertically`, `spacing.md` padding
    - Row content: `Icon(Icons.Filled.Event, tint = colorScheme.onTertiaryContainer)` + `spacing.sm` spacer + `Text(state.highlightText, style = bodyMedium, color = colorScheme.onTertiaryContainer)`
    - Add `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the text (matches existing `RightNowCard` accessibility pattern)

- [x] **T6 — `DiscoveryChipItem` composable (single chip renderer)**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ChipCloud.kt` (CREATE — add as private composable inside this file)
  - Action: Implement `@Composable private fun DiscoveryChipItem(chip: DiscoveryChip, modifier: Modifier = Modifier)`:
    - If `chip.bucketType == BucketType.HISTORY`: delegate entirely to `MiniTimelineStrip(chip, modifier)`; return.
    - State: `var showSheet by remember { mutableStateOf(false) }`, `var displayNumber by remember { mutableIntStateOf(0) }`
    - Count-up: `if (chip.isNumber && chip.numberValue != null) LaunchedEffect(chip.numberValue) { /* loop from 0 to numberValue in 400ms */ val steps = chip.numberValue; val delayMs = 400L / steps.coerceAtLeast(1); for (i in 0..steps) { displayNumber = i; delay(delayMs) } }`
    - Displayed label: if `chip.isNumber && chip.numberValue != null`, replace the leading number in `chip.label` with `displayNumber.toString()`; otherwise use `chip.label` as-is.
    - Wow glow: `val glowAlpha by animateFloatAsState(...)` — on first composition, animate from 0f→1f→0f once (use `var triggered by remember { mutableStateOf(false) }` + `LaunchedEffect(Unit) { triggered = true }`). Gate with `rememberReduceMotion()`.
    - Chip: `SuggestionChip(onClick = { if (chip.detail.isNotBlank()) showSheet = true }, label = { Text(displayedLabel, maxLines = 2, overflow = TextOverflow.Ellipsis) }, icon = { Icon(chip.bucketType.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) }, border = if (chip.isWow) SuggestionChipDefaults.suggestionChipBorder(true).copy(borderColor = colorScheme.primary.copy(alpha = glowAlpha)) else SuggestionChipDefaults.suggestionChipBorder(true), modifier = modifier.then(if (chip.isWow) Modifier.scale(1.15f) else Modifier))`
    - If `chip.detail.isBlank()`: wrap chip in `TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(chip.label) } }, state = rememberTooltipState())` instead of showing a bottom sheet.
    - If `showSheet`: `ModalBottomSheet(onDismissRequest = { showSheet = false }) { Text(chip.detail, modifier = Modifier.padding(spacing.md)) }`
    - Requires `@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)`

- [x] **T7 — `ChipCloud` composable**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ChipCloud.kt` (CREATE — main public composable in this file)
  - Action: Implement `@Composable fun ChipCloud(chips: List<DiscoveryChip>, isComplete: Boolean, modifier: Modifier = Modifier)`:
    - Sort order: HISTORY chip first (find by `bucketType == BucketType.HISTORY`), then remaining chips in their natural `BucketType.entries` order.
    - Layout: `FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm))` wrapping `AnimatedVisibility` per chip.
    - Per-chip entrance: `AnimatedVisibility(visible = true, enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)))`. Gate with `rememberReduceMotion()` — if reduce motion, use `fadeIn(tween(100))` only.
    - Shimmer on completion: `LaunchedEffect(isComplete) { if (isComplete && !reduceMotion) { /* staggered alpha sweep: for each chip index i, delay(i * 30L), then brief alpha pulse via Animatable */ } }` — implement as a `remember { Animatable(1f) }` per chip that pulses 1f→1.4f→1f over 200ms with staggered start.
    - Empty state: if `chips.isEmpty()`, render nothing (caller handles loading state).
  - Notes: `@OptIn(ExperimentalLayoutApi::class)` on the file or function.

- [x] **T8 — Wire into `SummaryScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt` (MODIFY)
  - Action — Remove imports: `BucketCard`, `RightNowCard`, `TimelineCard`, `HorizontalDivider`, `LazyColumn`, `items` (lazy), `rememberLazyListState`, `snapshotFlow`
  - Action — Remove entirely: the private `BucketList` composable function (lines ~228–325), the `ContentNoteBanner` if only used inside `BucketList` (check — if used in `LocationFailed` state, keep it), `onScrollDepthChanged` parameter from all call sites and its `snapshotFlow` + `LaunchedEffect` tracking block inside `BucketList`.
  - Action — Remove from `SummaryViewModel.onScreenExit()` call: leave `onScreenExit()` intact (it still tracks `maxScrollDepthPercent` — just not updated anymore; that's fine for v1).
  - Action — Add imports: `AlertBanner`, `ChipCloud`, `extractChips`, `Column`, `verticalScroll`, `rememberScrollState`
  - Action — Replace all `BucketList(buckets = ..., areaName = ..., isComplete = ..., contentNote = ..., onNavigateToChat = ..., onScrollDepthChanged = ...)` call sites (there are 4: Loading, LocationResolving, LocationFailed, Streaming/Complete states) with:
    ```kotlin
    // Helper lambda — define once above the when block:
    val chipContent = @Composable { buckets: Map<BucketType, BucketDisplayState>, isComplete: Boolean ->
        val chips = remember(buckets) { extractChips(buckets) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            if (state.contentNote != null) ContentNoteBanner(message = state.contentNote)
            AlertBanner(state = buckets[BucketType.WHATS_HAPPENING])
            ChipCloud(chips = chips, isComplete = isComplete)
            if (isComplete) {
                InlineChatPrompt(areaName = areaName, onNavigateToChat = onNavigateToChat)
            }
            Spacer(Modifier.height(MaterialTheme.spacing.touchTarget))
        }
    }
    ```
    Then in each `when` branch, call `chipContent(buckets, isComplete)`.
  - Notes: The `PullToRefreshBox` wrapping stays. The `MediumTopAppBar` with area name + Search icon stays unchanged.

- [x] **T9 — Delete dead files**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketCard.kt` — DELETE
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketSectionHeader.kt` — DELETE
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/HighlightFactCallout.kt` — DELETE
  - DO NOT delete `RightNowCard.kt` or `TimelineCard.kt` — both actively used by `SearchScreen.kt` (verified via grep)
  - Action: After deletion, run a project-wide search for any remaining import of the deleted class names; fix any stragglers.

- [x] **T10 — Tests**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/summary/TimelineEraParserTest.kt` (CREATE)
  - Action: Migrate era-parsing test cases from `TimelineCardTest.kt` that test `parseTimelineEras()` and `TimelineEra` directly. Remove the migrated assertions from `TimelineCardTest.kt` (keep any composable-level tests there).
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/summary/ChipExtractorTest.kt` (CREATE)
  - Action: Write the following test cases using `kotlin.test`:
    - `HISTORY bucket with body text containing years → eraYears populated, isWow = true`
    - `any bucket with highlightText length > 80 → isWow = true`
    - `any bucket with highlightText length <= 80 and not HISTORY → isWow = false`
    - `highlightText starting with "47 cafes nearby" → isNumber = true, numberValue = 47`
    - `highlightText starting with "1,200 residents" → isNumber = true, numberValue = 1200`
    - `highlightText not starting with number → isNumber = false, numberValue = null`
    - `incomplete bucket (isComplete = false) → not included in extractChips output`
    - `bucket with blank highlightText (isComplete = true) → not included in extractChips output`
    - `all 6 buckets complete → 6 chips, ordered SAFETY first NEARBY last`

### Acceptance Criteria

- [x] **AC1 — Chip cloud renders instead of bucket list**
  - Given: `SummaryUiState.Streaming` with 3 complete buckets
  - When: `SummaryScreen` renders
  - Then: a `FlowRow` of 3 chips is visible with bucket icons; no `BucketCard`, no vertical `HorizontalDivider`, no "More about this place" label present

- [x] **AC2 — Chips stream in as buckets complete**
  - Given: `SummaryUiState.Streaming` with buckets arriving one at a time
  - When: each new `BucketUpdate.BucketComplete` is processed and state updates
  - Then: one new chip appears with fade+scale entrance animation; previously rendered chips stay in place without re-animating

- [x] **AC3 — HISTORY chip renders as MiniTimelineStrip**
  - Given: HISTORY bucket is complete with `bodyText` containing year-bearing sentences (e.g., "In 1847 the city was founded...")
  - When: chip cloud renders
  - Then: HISTORY chip is visually wider than other chips and shows compressed era years joined by `·` (e.g., "1987 · 1923 · 1847 · Now")
  - When: HISTORY chip is tapped
  - Then: `ModalBottomSheet` opens showing a scrollable list of era cards with year + sentence

- [x] **AC4 — Wow chip visual treatment**
  - Given: HISTORY chip (always wow), or any chip where the source `highlightText.length > 80`
  - When: the chip first appears in the cloud
  - Then: chip renders at `1.15×` scale relative to normal chips; a single-cycle border glow animation plays on entrance
  - Given: reduce motion is enabled
  - Then: chip renders at `1.15×` scale but no border animation plays

- [x] **AC5 — Number chip count-up**
  - Given: a chip whose `highlightText` starts with a digit (e.g., "47 cafes nearby")
  - When: the chip enters the cloud
  - Then: the displayed number starts at 0 and increments to 47 over approximately 400ms; after completion the full label is shown as-is

- [x] **AC6 — Constellation shimmer on completion**
  - Given: portrait streaming completes (`SummaryUiState.Complete`)
  - When: the state first transitions to Complete
  - Then: a single shimmer alpha-pulse sweeps across all chips with staggered timing; all chips remain fully visible after the sweep finishes
  - Given: reduce motion is enabled
  - Then: no shimmer plays; chips are statically visible

- [x] **AC7 — AlertBanner conditional rendering**
  - Given: WHATS_HAPPENING bucket is complete with non-blank `highlightText`
  - When: `SummaryScreen` renders
  - Then: `AlertBanner` is visible above the chip cloud showing the urgency text in `tertiaryContainer` colour
  - Given: WHATS_HAPPENING bucket has blank `highlightText`, or is not yet complete
  - When: `SummaryScreen` renders
  - Then: `AlertBanner` is absent — no empty space or placeholder

- [x] **AC8 — Chip tap: tooltip for simple, bottom sheet for rich**
  - Given: a chip where `detail` (bodyText) is blank
  - When: chip is tapped
  - Then: a `PlainTooltip` appears showing the chip label; no bottom sheet
  - Given: a chip where `detail` is non-blank
  - When: chip is tapped
  - Then: `ModalBottomSheet` slides up showing the full body text

- [x] **AC9 — Loading/error states show empty chip cloud**
  - Given: `SummaryUiState.Loading` or `SummaryUiState.LocationResolving`
  - When: `SummaryScreen` renders
  - Then: chip cloud area shows no chips (empty `FlowRow`); pull-to-refresh indicator and/or `LinearProgressIndicator` still visible as before; no crash

- [x] **AC10 — Dead code removed, build clean**
  - Given: all tasks are complete and the project is built
  - Then: `SummaryScreen.kt` contains no imports or usages of `BucketCard`, `BucketList`, `RightNowCard`, `TimelineCard`, `HorizontalDivider` (lazy), or `snapshotFlow`; deleted files `BucketCard.kt`, `BucketSectionHeader.kt`, `HighlightFactCallout.kt` no longer exist; `./gradlew :composeApp:build` passes with zero errors

## Additional Context

### Dependencies

- `FlowRow` → `androidx.compose.foundation` (already in project as Compose dep)
- `ModalBottomSheet` → `androidx.compose.material3` (already present)
- `PlainTooltip` / `TooltipBox` → `androidx.compose.material3` with `@OptIn(ExperimentalMaterial3Api::class)`
- No new Gradle dependencies required

### Testing Strategy

- `ChipExtractor` is pure functions — test exhaustively in `commonTest` with no mocking needed
- `TimelineEraParser` extraction: migrate existing era-parsing tests from `TimelineCardTest.kt`
- UI composables: not unit-tested (consistent with project pattern — no Compose UI tests in `commonTest`)
- Manual smoke test on device: stream a real area portrait and verify chips appear progressively, HISTORY chip opens bottom sheet, shimmer plays on completion

### Notes

- **v1 chip-per-bucket decision:** Deliberately one chip per bucket in v1. `bodyText` often contains rich multi-sentence content that could yield 3–5 chips per bucket in a future iteration. `ChipExtractor` is designed as pure functions so the extraction logic can be expanded without touching composables.
- **Wow score deferral:** AI-emitted surprise score (1–5, one extra token in the AI prompt) is the eventual replacement for the length heuristic. Deferred because it requires: (1) prompt engineering + testing, (2) new field on `BucketDisplayState`, (3) parser update in `SummaryStateMapper`. Tracked for a future story.
- **`HighlightFactCallout` deletion confirmed safe:** Grep verified — only referenced by `BucketCard.kt` (also being deleted) and `BucketSectionHeader.kt` (also being deleted). No other consumers. Safe to delete.
- **`InlineChatPrompt` preserved** below the chip cloud in complete state — chat entry point unchanged in this story.
- **`SearchScreen.kt` preserved as-is** — it uses `RightNowCard` and `TimelineCard` which are not touched in this story. SearchScreen's summary display will be updated in a future story.
- **Risk — `SuggestionChipDefaults.suggestionChipBorder` API:** The border copy trick for wow glow may not be available depending on Material3 version. Fallback: use `Modifier.border(...)` on the chip wrapper instead of the `SuggestionChip` border parameter.
- **Risk — `TooltipBox` / `PlainTooltip` API:** These are `@ExperimentalMaterial3Api` and may have changed. Check the current M3 version in `libs.versions.toml`. If API has changed, use a simple `Popup` or `DropdownMenu` as fallback for the tooltip.
- **Risk — shimmer implementation:** The staggered `Animatable` shimmer across variable chip count needs careful index tracking. If complex, simplify to a single `InfiniteTransition` alpha pulse on all chips simultaneously (still creates a "live" feeling without stagger complexity).
- **`updateScrollDepth` analytics gap:** Scroll depth tracking is lost for `SummaryScreen` in this story. The `summary_viewed` analytics event still fires. Scroll depth can be reinstated in a future story by tracking `verticalScroll` state offset.
