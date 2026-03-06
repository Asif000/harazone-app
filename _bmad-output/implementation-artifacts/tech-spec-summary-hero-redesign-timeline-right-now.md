---
title: 'Summary Screen Hero Redesign: Timeline & Right Now Cards'
slug: 'summary-hero-redesign-timeline-right-now'
created: '2026-03-06'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Material3', 'Coroutines/Flow']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/TimelineCard.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/RightNowCard.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/components/TimelineCardTest.kt'
code_patterns:
  - 'rememberReduceMotion() for all animation guards'
  - 'AnimatedVisibility + fadeIn(tween(200)) for reduce-motion path'
  - 'BucketDisplayState.isComplete as gate for hero card visibility'
  - 'BucketDisplayState.highlightText for hero sentence'
  - 'BucketDisplayState.bodyText for full content to parse'
test_patterns:
  - 'commonTest, kotlin.test, @Test, runTest, UnconfinedTestDispatcher'
  - 'Pure function parseTimelineEras() tested in isolation'
---

# Tech-Spec: Summary Screen Hero Redesign: Timeline & Right Now Cards

**Created:** 2026-03-06

## Overview

### Problem Statement

`SummaryScreen` renders six `BucketCard`s with identical visual weight in a flat `LazyColumn`. All text, no visual differentiation, no animation beyond streaming cursor. The "whoa" moment isn't strong enough to earn engagement or lead naturally to AI chat — confirmed in party mode design retrospective on 2026-03-06.

### Solution

Add two new hero-level composables rendered at the top of `BucketList` — `TimelineCard` (horizontal `LazyRow` of the place's story arc, sourced from `HISTORY` bucket) and `RightNowCard` (urgency card sourced from `WHATS_HAPPENING` bucket highlight). Both cards appear via `AnimatedVisibility` when their source bucket completes. The six `BucketCard`s shift to a "More about this place" depth section below, preceded by a section label. No domain model changes — all data is already in `BucketDisplayState`.

### Scope

**In Scope:**
- New `TimelineCard` composable with horizontal `LazyRow` of `TimelineEra` items — extracted from `HISTORY` bucket `bodyText` via `parseTimelineEras()` pure function (regex for 4-digit years)
- New `RightNowCard` composable — bold urgency card using `WHATS_HAPPENING` `highlightText`, `Icons.Filled.Event` icon, `tertiaryContainer` color accent
- `parseTimelineEras(content: String): List<TimelineEra>` internal pure function — unit tested in `commonTest`
- `TimelineEra(year: Int, sentence: String)` data class — in `TimelineCard.kt`
- Modify `SummaryScreen.BucketList` — render hero cards before bucket items; add "More about this place" label; gate hero cards on `isComplete` of source bucket
- `AnimatedVisibility(visible = isComplete, enter = fadeIn(tween(300)))` for hero card entrance — respects `rememberReduceMotion()`
- Unit tests for `parseTimelineEras()`: happy path, edge cases (no years, single year, >5 years capped)

**Out of Scope:**
- Safety color field, Character illustration, Cost data viz redesign
- POI density / two-pass map loading
- Image/photo fetching for timeline eras (icon-only for v1)
- Discovery Engine, AR camera
- AI prompt changes — uses existing `BucketContent` data only
- `BucketCard` visual changes

## Context for Development

### Codebase Patterns

- **Animation guard**: Every animated composable calls `rememberReduceMotion()` (expect/actual in `ui/components/ReduceMotion.kt`). Reduce-motion path uses `AnimatedVisibility + fadeIn(tween(200))` only. Normal path may use `rememberInfiniteTransition`. Follow this pattern exactly in both new composables.
- **Streaming gate**: Hero cards only appear when `BucketDisplayState.isComplete == true` for their source bucket. During streaming, the card is hidden (not a skeleton placeholder). Use `AnimatedVisibility(visible = state?.isComplete == true)`.
- **`BucketDisplayState` access in `BucketList`**: `BucketList` receives `buckets: Map<BucketType, BucketDisplayState>`. Access source buckets via `buckets[BucketType.HISTORY]` and `buckets[BucketType.WHATS_HAPPENING]`.
- **`LazyColumn` item keys**: All existing items use string `key` parameters. New hero card items must use keys `"timeline_card"`, `"right_now_card"`, `"more_about_label"`.
- **Spacing**: Use `MaterialTheme.spacing.*` exclusively — never hardcode dp values.
- **Icon pattern**: Reuse `BucketType.icon()` extension from `BucketTypeMapper.kt`.
- **`RightNowCard` color**: Use `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)` to signal urgency — visually distinct from `HighlightFactCallout`'s left-border primary accent.
- **Private composable location**: `TimelineCard` and `RightNowCard` go in `ui/components/` (same package as `BucketCard`, `HighlightFactCallout`), not inside `SummaryScreen.kt`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `ui/summary/SummaryScreen.kt` | `BucketList` composable to modify — add hero card items at top of `LazyColumn` |
| `ui/summary/BucketDisplayState.kt` | `BucketDisplayState` data class — `highlightText`, `bodyText`, `isComplete` fields |
| `ui/summary/SummaryStateMapper.kt` | How `BucketComplete` sets `highlightText` and marks `isComplete = true` |
| `ui/components/BucketCard.kt` | Pattern for composable structure — `Column`, `BucketSectionHeader`, spacing |
| `ui/components/BucketSectionHeader.kt` | Animation pattern — `rememberReduceMotion()`, `StreamingDot`, state-driven rendering |
| `ui/components/StreamingTextContent.kt` | `AnimatedVisibility + fadeIn` reduce-motion pattern to replicate |
| `ui/components/ReduceMotion.kt` | `rememberReduceMotion(): Boolean` — import and use in both new composables |
| `ui/components/BucketTypeMapper.kt` | `BucketType.icon()` and `BucketType.displayTitle()` extensions |
| `ui/components/HighlightFactCallout.kt` | Left-border accent card pattern — `RightNowCard` must look different from this |
| `ui/theme/Spacing.kt` | Spacing tokens — use `MaterialTheme.spacing.*` exclusively |
| `commonTest/.../SummaryViewModelTest.kt` | Test pattern — `kotlin.test`, `@Test`, fake collaborators |

### Technical Decisions

1. **Era parsing via regex, not prompt engineering**: Extract `TimelineEra` items from `HISTORY` `bodyText` at render time using `Regex("\\b(\\d{4})\\b")`. No AI prompt changes, no domain model additions. Sentences without a 4-digit year are excluded. Cap at 5 eras. Sort by year ascending.
2. **`TimelineEra` as local data class in `TimelineCard.kt`**: Not a domain model — it's a UI-only concern. Internal to the component file.
3. **`parseTimelineEras` as internal top-level function in `TimelineCard.kt`**: Marked `internal` for testability from `commonTest`. Signature: `internal fun parseTimelineEras(content: String): List<TimelineEra>`.
4. **Hero cards hidden during streaming**: Partial text yields unreliable eras. Cards are invisible until `isComplete = true`.
5. **`RightNowCard` uses `tertiaryContainer`**: Signals urgency distinct from `primary` used in bucket headers.
6. **"More about this place" label always shown**: Anchors the bucket section visually even during loading.

## Implementation Plan

### Tasks

- [ ] Task 1: Create `TimelineCard.kt` with `TimelineEra` data class, `parseTimelineEras()` function, and `TimelineCard` composable
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/TimelineCard.kt`
  - Action: Create file with the following:
    1. `internal data class TimelineEra(val year: Int, val sentence: String)`
    2. `internal fun parseTimelineEras(content: String): List<TimelineEra>` — split `content` into sentences on `[.!?]`, for each sentence find first match of `Regex("\\b(\\d{4})\\b")`, create `TimelineEra(year = match.value.toInt(), sentence = sentence.trim())`, filter duplicates by year, sort ascending by year, take first 5
    3. `@Composable fun TimelineCard(historyState: BucketDisplayState?, modifier: Modifier = Modifier)`:
       - `val eras = remember(historyState?.bodyText) { parseTimelineEras(historyState?.bodyText ?: "") }`
       - `val reduceMotion = rememberReduceMotion()`
       - `val visible = historyState?.isComplete == true && eras.isNotEmpty()`
       - `AnimatedVisibility(visible = visible, enter = if (reduceMotion) fadeIn(tween(200)) else fadeIn(tween(300)))`
       - Inside: `Column` with header `Row` (Icon `Icons.Filled.History`, Text "The Story" in `titleMedium`, `onSurface` color) + `LazyRow` of era items
       - Each era item: `Card` (outlined style, fixed width `200.dp`) containing `Column` with `Text(era.year.toString(), titleLarge, primary color)` + `Text(era.sentence, bodySmall, onSurfaceVariant)`
       - Add `contentDescription` on the `LazyRow` parent: `semantics { contentDescription = "Place history timeline" }`
    4. Add `@Preview` for non-empty and empty states

- [ ] Task 2: Create `RightNowCard.kt` with `RightNowCard` composable
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/RightNowCard.kt`
  - Action: Create file with the following:
    1. `@Composable fun RightNowCard(whatsHappeningState: BucketDisplayState?, modifier: Modifier = Modifier)`:
       - `val reduceMotion = rememberReduceMotion()`
       - `val visible = whatsHappeningState?.isComplete == true && whatsHappeningState.highlightText.isNotEmpty()`
       - `AnimatedVisibility(visible = visible, enter = if (reduceMotion) fadeIn(tween(200)) else fadeIn(tween(300)))`
       - Inside: `Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer))` containing `Column(modifier = Modifier.padding(MaterialTheme.spacing.md))`:
         - `Row` with `Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)` + `Spacer(spacing.sm)` + `Text("Right Now", titleMedium, onTertiaryContainer)`
         - `Spacer(spacing.sm)`
         - `Text(whatsHappeningState.highlightText, bodyLarge, onTertiaryContainer)` with `semantics { liveRegion = LiveRegionMode.Polite }`
    2. Add `@Preview` for non-empty and empty/hidden state

- [ ] Task 3: Add unit tests for `parseTimelineEras()`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/components/TimelineCardTest.kt`
  - Action: Create test class `TimelineCardTest` with:
    1. `fun parseTimelineEras_withMultipleYears_returnsErasSortedAscendingCappedAtFive()` — input with 6+ year-bearing sentences, assert 5 returned, sorted by year
    2. `fun parseTimelineEras_withNoYears_returnsEmptyList()` — input with no 4-digit years, assert empty list
    3. `fun parseTimelineEras_withSingleYear_returnsSingleEra()` — input with one year-bearing sentence, assert one era with correct year and sentence
    4. `fun parseTimelineEras_withDuplicateYears_deduplicatesByYear()` — two sentences with same year, assert only one era for that year
    5. `fun parseTimelineEras_withEmptyString_returnsEmptyList()` — empty input, assert empty list

- [ ] Task 4: Modify `SummaryScreen.BucketList` to render hero cards
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt`
  - Action:
    1. Add imports for `TimelineCard`, `RightNowCard`, `BucketType`
    2. Inside `BucketList` composable, extract source bucket states before the `LazyColumn`:
       ```kotlin
       val historyState = buckets[BucketType.HISTORY]
       val whatsHappeningState = buckets[BucketType.WHATS_HAPPENING]
       ```
    3. In the `LazyColumn`, add as first three items (before the existing `contentNote` item):
       ```kotlin
       item(key = "right_now_card") {
           RightNowCard(whatsHappeningState = whatsHappeningState)
           Spacer(Modifier.height(MaterialTheme.spacing.md))
       }
       item(key = "timeline_card") {
           TimelineCard(historyState = historyState)
           Spacer(Modifier.height(MaterialTheme.spacing.md))
       }
       item(key = "more_about_label") {
           Text(
               text = "More about this place",
               style = MaterialTheme.typography.labelLarge,
               color = MaterialTheme.colorScheme.onSurfaceVariant,
               modifier = Modifier.padding(
                   top = MaterialTheme.spacing.sm,
                   bottom = MaterialTheme.spacing.xs,
               ),
           )
       }
       ```
    4. Note: `RightNowCard` appears before `TimelineCard` — urgency first, then story depth. `contentNote` item stays in its existing position (after `"more_about_label"`).

### Acceptance Criteria

- [ ] AC 1: Given the `HISTORY` bucket has completed streaming with text containing year-bearing sentences, when `SummaryScreen` renders, then a `TimelineCard` appears above the bucket list showing a horizontal row of era cards, each with a 4-digit year label and a sentence, sorted oldest-to-newest, maximum 5 eras.

- [ ] AC 2: Given the `HISTORY` bucket has completed streaming with text containing no 4-digit years (e.g. new area with no historical data), when `SummaryScreen` renders, then no `TimelineCard` is shown (invisible, not an empty shell).

- [ ] AC 3: Given the `WHATS_HAPPENING` bucket has completed streaming with a non-empty `highlightText`, when `SummaryScreen` renders, then a `RightNowCard` appears above the timeline card, showing the highlight text on a `tertiaryContainer` colored card with a `Event` icon and "Right Now" heading.

- [ ] AC 4: Given the `WHATS_HAPPENING` bucket `highlightText` is empty after completion, when `SummaryScreen` renders, then no `RightNowCard` is shown.

- [ ] AC 5: Given either bucket is still streaming (not yet complete), when `SummaryScreen` renders, then the corresponding hero card is not visible — no placeholder or skeleton shown for that card.

- [ ] AC 6: Given `RightNowCard` and `TimelineCard` both have content, when they become visible, then each fades in smoothly (`AnimatedVisibility + fadeIn`); given the device has Reduce Motion enabled, then the cards fade in with a 200ms tween (no spring/overshoot animations).

- [ ] AC 7: Given the summary screen renders in any state, when the user scrolls down, then a "More about this place" label in `labelLarge` / `onSurfaceVariant` appears above the six `BucketCard`s, clearly separating the hero section from the depth section.

- [ ] AC 8: Given TalkBack is active and `TimelineCard` is visible, when the user navigates to it, then the `LazyRow` has `contentDescription = "Place history timeline"` and each era card is focusable with year and sentence readable in sequence.

- [ ] AC 9: Given `parseTimelineEras()` is called with content containing 6 year-bearing sentences, then it returns exactly 5 eras sorted by year ascending.

- [ ] AC 10: Given `parseTimelineEras()` is called with an empty string, then it returns an empty list without throwing.

## Additional Context

### Dependencies

No new library dependencies. All required APIs already in project:
- `compose.animation` (`AnimatedVisibility`, `fadeIn`, `tween`) — already used in `StreamingTextContent.kt`
- `material3` (`Card`, `CardDefaults`, `tertiaryContainer`) — already in use
- `material.icons.extended` (`Icons.Filled.Event`, `Icons.Filled.History`) — already used in `BucketTypeMapper.kt`

### Testing Strategy

- **Unit tests** (automated): `parseTimelineEras()` pure function — 5 test cases in `TimelineCardTest.kt`, run via `./gradlew :composeApp:test`
- **Manual device testing**: Launch app in Doral (or any area with history data), verify `TimelineCard` and `RightNowCard` appear after streaming completes, verify animations, verify "More about this place" label, verify TalkBack navigation
- **Reduce Motion**: Enable "Remove animations" in Android Accessibility settings, verify fade-only entrance

### Notes

- **Risk**: `parseTimelineEras()` depends on sentence quality from Gemini. If the AI returns history as a single paragraph with no sentence-ending punctuation, splitting will fail. Mitigation: also split on newlines (`\n`) as a fallback separator.
- **Future**: When image API is added (Phase 2+), `TimelineEra` can be extended with an optional `imageUrl: String?` and `TimelineCard` updated to show a thumbnail above the year label — no structural changes needed.
- **Future**: `RightNowCard` can be extended to show a countdown timer (e.g. "starts in 2h") when event time data is available from the AI — currently not in `BucketContent` model.
- Design direction reference: `_bmad-output/planning-artifacts/design-direction-v2.md`
