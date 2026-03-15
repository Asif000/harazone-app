---
title: 'Ambient Layer + Bottom Carousel'
slug: 'ambient-layer-bottom-carousel'
created: '2026-03-14'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - Kotlin Multiplatform
  - Compose Multiplatform
  - MapLibre (Android SymbolManager / iOS MLNShapeSource+MLNSymbolStyleLayer)
  - Gemini API (SSE streaming, GeminiPromptBuilder + GeminiResponseParser)
  - kotlinx-datetime (sunset calculation)
  - Ktor (HTTP client, SSE)
  - kotlinx.serialization (JSON parsing)
  - Compose Foundation snapping (rememberSnapFlingBehavior for LazyRow)
files_to_modify:
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapComposable.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt
  - composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt
  - composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt
files_to_create:
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AmbientTicker.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PoiCarousel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/PinStatusUtils.kt
files_to_delete:
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PinCardLayer.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FloatingPoiCard.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ScreenOffset.kt
code_patterns:
  - ViewModel pattern: val state = _uiState.value as? MapUiState.Ready ?: return; _uiState.value = state.copy(...)
  - BucketUpdate sealed class — new events carry data from Gemini to VM to state
  - PoiJson short-key schema (n/t/v/w/h/s/r/lat/lng/wiki/vs) — add p for priceRange
  - Stage1Response carries vibes+pois — extend with ah for areaHighlights
  - ensureIcon() in MapComposable.android.kt draws pin bitmaps — extend signature for new visual states
  - iconKey must encode all variant params to avoid stale cached bitmaps
test_patterns:
  - MapViewModelTest uses UnconfinedTestDispatcher + createViewModel() factory with fake injection
  - Test files in composeApp/src/commonTest/kotlin/
  - Pattern: @Test fun name() = runTest { val vm = createViewModel(...); vm.action(); assertEquals(expected, (vm.uiState.value as MapUiState.Ready).field) }
  - 14 fakes in com.harazone.fakes package — use FakeAreaRepository to inject BucketUpdate sequences
---

# Tech-Spec: Ambient Layer + Bottom Carousel

**Created:** 2026-03-14

## Overview

### Problem Statement

Pin-anchored floating cards (PinCardLayer) are broken on iOS due to coordinate bridge complexity, collision issues, and weak visual hierarchy. The map has no ambient context — users cannot tell what is open or closed at a glance. Browsing and acting on the 3 POIs is clunky. iOS pins show the default red MapLibre teardrop, with no emoji or vibe color.

### Solution

Two coupled changes sharing the same state: (1) Replace PinCardLayer + FloatingPoiCard entirely with a snap-scroll bottom carousel — no coordinate bridge, works identically on both platforms. (2) Add an ambient layer: rotating ticker below the search bar, status dots + ghost treatment + micro-badges baked into pin bitmaps, permanent pin labels. Migrate iOS pins from default MLNPointAnnotation to bitmap-based custom pins matching Android. Both features share `selectedPinIndex` in `MapUiState.Ready` which is why they are a single spec.

### Scope

**In Scope:**

- DELETE: `PinCardLayer.kt` (161 LOC), `FloatingPoiCard.kt` (80 LOC), `ScreenOffset.kt`
- DELETE from `MapUiState.Ready`: `pinScreenPositions`, `cardsVisible`, `selectedPinId`
- DELETE from `MapViewModel`: `onPinsProjected()`, `onMapGestureStart()`, `onPinChipTapped()`
- ADD to `POI`: `priceRange: String?`
- ADD to Gemini Stage 1 response: `areaHighlights: List<String>` (via `Stage1Response.ah`)
- ADD to `MapUiState.Ready`: `selectedPinIndex: Int?`, `areaHighlights: List<String>`
- NEW `AmbientTicker` composable — rotating area intel below search bar
- NEW `PoiCarousel` composable — snap LazyRow above bottom bar, two-way pin binding
- Pin bitmap upgrades (both platforms): status dot (green/orange/red/grey), ghost treatment (closed POIs), micro-badge (emoji icon)
- iOS pin icons: `MLNPointAnnotation` + `MLNAnnotationImage` via delegate `imageForAnnotation` + `UIGraphicsBeginImageContextWithOptions` (legacy API, reliable in K/N). UILabel `layer.renderInContext()` for emoji. Tap via `UITapGestureRecognizer` + coordinate hit-test (avoids Kotlin/Native `didSelectAnnotation`/`imageForAnnotation` conflicting overloads).
- Ticker data sources: open count (device, liveStatus parse) + sunset time (device, lat/lng solar formula) + area highlights (Gemini)
- Regression tests before deletion + new tests for carousel binding + ticker slots

**Out of Scope:**

- Activity rings (deferred v1.1 — continuous animation requires per-pin coroutine loop or bitmap cycling, not clean on either platform)
- AI Whispers + Relaunch Greeting (deferred — requires backend session tracking + notification queue, feature #43)
- Real-time live events for ticker (Tier 3 — requires external API)
- Push-apart collision resolution (eliminated — carousel removes the problem entirely)
- Distance calculation for carousel meta row (deferred — requires user GPS permission flow integration)

## Context for Development

### Codebase Patterns

- Android pin bitmaps: `ensureIcon()` in `MapComposable.android.kt` (~line 512) draws emoji circle bitmaps on Android Canvas, registers via `style.addImage(iconKey, bitmap)`. Current iconKey: `"poi_${vibe.name}_${typeKey}${if (isSaved) "_saved" else ""}"`. Must extend key with all new variant params to avoid stale bitmap cache hits.
- iOS pins: `MLNPointAnnotation` + custom images via `MLNAnnotationImage` returned from delegate's `imageForAnnotation`. Pin images drawn with `UIGraphicsBeginImageContextWithOptions` (legacy context API — reliable in K/N, unlike `UIGraphicsImageRenderer` which has interop issues). Emoji rendered via `UILabel.layer.renderInContext()`. `annotationPoiMap` and `currentAnnotations` track annotations. Tap via `AnnotationTapHandler` (`UITapGestureRecognizer` + coordinate hit-test) — avoids Kotlin/Native conflicting overloads between `didSelectAnnotation` and `imageForAnnotation` (both map to same JVM signature `mapView(MLNMapView, MLNAnnotationProtocol)`).
- Gemini data flow: Stage 1 (`buildPinOnlyPrompt`) → `parseStage1Response()` → `Stage1Response` (vibes + pois) → `BucketUpdate.VibesReady`. `areaHighlights` added to `Stage1Response.ah`, carried to `VibesReady.areaHighlights`, stored in `MapUiState.Ready.areaHighlights`.
- `priceRange` threads through: `PoiJson.p` → 4 parse functions in `GeminiResponseParser` (`parsePinOnlyResponse`, `parseStage1Response` try+catch, `parseDynamicVibeResponse`, `parsePoisJson`) + 1 path in `GeminiAreaIntelligenceProvider` via `EnrichJson.p` at ~line 199. Total 5 paths across 2 files — 4 covered in T7, 1 covered in T9(b).
- `BucketUpdate` sealed class — add `areaHighlights: List<String> = emptyList()` to `VibesReady` and `PinsReady`. Default empty preserves backward compat with all existing tests.
- `MapUiState.Ready` all-state data class — remove 3 fields, add 2 fields (`selectedPinIndex`, `areaHighlights`). All new fields have safe defaults.
- VM handler pattern: `fun onX() { val state = _uiState.value as? MapUiState.Ready ?: return; _uiState.value = state.copy(...) }`. Both new carousel handlers follow this exactly.
- Snap LazyRow: `rememberSnapFlingBehavior(lazyListState)` from `androidx.compose.foundation.gestures.snapping`. No new dependency — project already uses Compose Foundation pager (`HorizontalPager` in `ExpandablePoiCard.kt`).
- `liveStatus` parse convention (used in bitmap drawing, ticker, carousel stripe):
  - `contains("open", ignoreCase=true)` AND NOT contains "closed" → `PinStatusColor.GREEN`
  - `contains("clos", ignoreCase=true) && contains("soon", ignoreCase=true)` → `PinStatusColor.ORANGE`
  - `contains("closed", ignoreCase=true)` → `PinStatusColor.RED`
  - null or other → `PinStatusColor.GREY`
- `PinStatusColor` is a `enum class PinStatusColor { GREEN, ORANGE, RED, GREY }` in `commonMain/PinStatusUtils.kt`. Android: `fun PinStatusColor.toAndroidColor(): Int` extension (returns `android.graphics.Color` int). iOS: `fun PinStatusColor.toUIColor(): UIColor` extension. `liveStatusToColor(liveStatus: String?): PinStatusColor` lives in `commonMain`. No Int or UIColor in commonMain.
- `PinBadge` is `enum class PinBadge { CLOSING_SOON, TRENDING, EVENT }` in `commonMain/PinStatusUtils.kt`. Both platforms reference it directly. No androidMain placement.
- `deriveBadge(liveStatus: String?): PinBadge?` lives in `commonMain/PinStatusUtils.kt`. Logic: `contains("clos", ignoreCase=true) && contains("soon", ignoreCase=true)` → `CLOSING_SOON`; `contains("trend", ignoreCase=true)` → `TRENDING`; `contains("event", ignoreCase=true)` → `EVENT`; else → `null`. Priority is enforced by evaluation order (CLOSING_SOON checked first). NEW badge deferred — no added timestamp on POI in v1.
- `isClosed(liveStatus: String?): Boolean` lives in `commonMain/PinStatusUtils.kt`. Implementation: `return liveStatusToColor(liveStatus) == PinStatusColor.RED`. Must be included when creating `PinStatusUtils.kt` — it is tested in commonTest (`isClosed_closed_returnsTrue`, `isClosed_open_returnsFalse`).
- `fun PinStatusColor.toComposeColor(): Color` extension lives in `commonMain/PinStatusUtils.kt`. Required for Compose `background()` calls in `PoiCarousel` and `AmbientTicker` (both commonMain). Implementation: `return when (this) { GREEN -> Color(0xFF4CAF50); ORANGE -> Color(0xFFFF9800); RED -> Color(0xFFf44336); GREY -> Color(0xFF9E9E9E) }`. The existing `toAndroidColor()` and `toUIColor()` extensions are for platform-specific bitmap drawing only — Compose UI must use `toComposeColor()` from commonMain.
- Sunset calc: `internal fun calculateSunsetMinutes(lat: Double, lng: Double): Int` defined in `commonMain/PinStatusUtils.kt`. Uses NOAA Solar Calculator algorithm (Jean Meeus "Astronomical Algorithms" Ch. 25). Inputs: lat, lng, `LocalDate.now()`, `TimeZone.currentSystemDefault()` from `kotlinx-datetime`. Output: minutes until sunset as Int. Edge cases: polar summer (sun never sets) → return `Int.MAX_VALUE` → skip slot. Polar winter (sun never rises) → return `-1` → skip slot. No DST handling required — `kotlinx-datetime` `TimeZone.currentSystemDefault()` handles offset correctly. Referenced in T19 as `::calculateSunsetMinutes`.
- Micro-badge priority (one badge per pin): CLOSING_SOON (⏰) > TRENDING (🔥) > EVENT (🎤). NEW badge deferred — no "added" timestamp on POI in v1.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PinCardLayer.kt` | DELETE — 161 LOC coordinate bridge being replaced |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FloatingPoiCard.kt` | DELETE — 80 LOC fallback card being replaced |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ScreenOffset.kt` | DELETE — 1-line data class, no longer needed |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Add `priceRange: String? = null` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt` | Add `areaHighlights` to VibesReady + PinsReady |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | Remove 3 fields, add 2 fields (`selectedPinIndex`, `areaHighlights`) |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Remove 3 methods, add 3; handle areaHighlights in BucketUpdate handlers |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Replace PinCardLayer + FloatingPoiCard with AmbientTicker + PoiCarousel |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt` | Add `p` to PoiJson+EnrichJson; add `ah` to Stage1Response; update 4 mapping paths (parsePinOnlyResponse, parseStage1Response ×2, parseDynamicVibeResponse, parsePoisJson); extract `internal fun stripMarkdownFences(raw: String): String` (called by T9). 5th priceRange path (EnrichJson) handled in T9b. |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt` | Pass areaHighlights through VibesReady/PinsReady; fix EnrichJson→POI priceRange mapping |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | Add `"p":"$$"` to all POI schemas; add `"ah":[...]` to Stage 1 schema |
| `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt` | Extend ensureIcon() with status dot, ghost, badge, selected ring; remove onPinsProjected call |
| `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt` | Full iOS pin migration — MLNAnnotation → MLNShapeSource + style.addImage; add selectedPinIndex param |
| `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt` | Regression tests + carousel/ticker tests |
| `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiResponseParserTest.kt` | priceRange + areaHighlights parse tests |
| `_bmad-output/brainstorming/prototype-tester-release-3features.html` | Visual reference — tabs 1 (Ambient) + 2 (Carousel) |

### Technical Decisions

- `priceRange` in `PoiJson` as field `p: String? = null` — consistent with existing short-key convention (n/t/v/w/h/s/r). Add to exactly 5 parse paths: 4 in `GeminiResponseParser` (T7) + 1 `EnrichJson` path in `GeminiAreaIntelligenceProvider` (T9b). Add `"p":"$$"` to POI schemas in `buildPinOnlyPrompt`, `buildBackgroundBatchPrompt`, `buildDynamicVibeEnrichmentPrompt`, `buildEnrichmentPrompt`.
- `areaHighlights` in `Stage1Response` as `ah: List<String> = emptyList()`. Stage 1 is the right home — it runs first and is area-level. Prompt rule: "ah: up to 3 short area highlights — recurring events, seasonal notes, trending now. Max 40 chars each."
- Carousel uses `selectedPinIndex: Int?` (not `selectedPinId`) — index sufficient for LazyRow `animateScrollToItem` and pin highlight lookup by `pois[index]`.
- `buildTickerSlots()` extracted as an internal pure function in `AmbientTicker.kt` — makes slot logic unit-testable without Compose. Returns `List<String>`, empty strings filtered before display.
- `ensureIcon()` extended with `liveStatus: String?`, `isSelected: Boolean`, `badgeType: PinBadge?` (`PinBadge` is an `enum class` — not sealed class). Icon key: `"poi_${vibe.name}_${typeKey}_${liveStatusToColor(liveStatus).name}_${badgeType?.name ?: "none"}_${isSelected}_${isSaved}"`. Use `badgeType?.name` (enum `.name` returns `"CLOSING_SOON"`) — do NOT use `.javaClass.simpleName`. Status dot: 10px filled circle at bottom-right of bitmap. Badge: 14sp emoji at top-left (8dp inset). Selected: 3dp white stroke ring drawn last (over everything).
- iOS ghost treatment: in `drawPinImage()`, apply `CIColorMonochrome` filter (`inputColor = CIColor.gray`, `inputIntensity = 1.0`) to the rendered `UIImage`, then draw a `UIColor(white: 0, alpha: 0.65)` overlay to achieve ~35% effective opacity. This matches Android's `Paint.setAlpha(90)` + greyscale output visually.
- Two-way binding: tap pin → `onPinTapped(index)` → `viewModel.onPinTapped(index)` → `selectedPinIndex = index` → `LaunchedEffect(selectedPinIndex)` sets `isProgrammaticScroll = true`, calls `listState.animateScrollToItem(index)`, then sets `isProgrammaticScroll = false` in a `finally` block. Swipe card: `snapshotFlow { listState.firstVisibleItemIndex }.drop(1).collect { if (!isProgrammaticScroll) onCardSwiped(it) }`. Using `isProgrammaticScroll` (set before animation, cleared after) is correct — `isScrollInProgress` is NOT used as the guard because a programmatic `animateScrollToItem` also sets `isScrollInProgress = true`, which would re-arm any `isScrollInProgress`-based flag during the animation itself. `drop(1)` requires `import kotlinx.coroutines.flow.drop` — already used in `ExpandablePoiCard.kt`.
- `PlatformBackHandler(enabled = selectedPinIndex != null)` on carousel calls `onSelectionCleared()` — a dedicated callback wiring to `viewModel.onCarouselSelectionCleared()` which sets `selectedPinIndex = null`. Do NOT use `onCardSwiped(0)` for back press — that sets index to 0 instead of null, silently selecting pin 0.
- Carousel re-entry after detail dismissed: when `state.selectedPoi` transitions from non-null to null, `selectedPinIndex` is preserved. The carousel resumes with the last pin still highlighted. No reset on detail dismiss.
- `MapComposable` expect/actual signatures need `onPinTapped: (Int) -> Unit` and `selectedPinIndex: Int?` added. Both Android and iOS implementations must handle these. `onSelectionCleared` is NOT on `MapComposable` — it is wired entirely within `PoiCarousel` → `MapScreen` → `ViewModel` and the map composable has no involvement.

## Implementation Plan

### Tasks

**Phase 1 — Regression tests + safe deletion (do this first)**

- [ ] T1: Write regression tests for the 3 ViewModel methods being deleted
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt`
  - Action: Add `onPinsProjected_setsScreenPositionsAndShowsCards`, `onMapGestureStart_hidesCards`, `onPinChipTapped_togglesSelectedPinId`. Use `FakeAreaRepository` and `createViewModel()`. Tests must pass GREEN before any deletion.
  - Notes: These tests document existing behavior. Once deletion is done, they become dead code and should be removed — or repurposed to verify the fields no longer exist.

- [ ] T2: Delete the 3 files being removed
  - Files: `PinCardLayer.kt`, `FloatingPoiCard.kt`, `ScreenOffset.kt`
  - Action: Delete all 3 files from `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/` and `composeApp/src/commonMain/kotlin/com/harazone/ui/map/`.

- [ ] T3: Remove deleted fields from `MapUiState.Ready`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Remove `pinScreenPositions: Map<String, ScreenOffset>`, `cardsVisible: Boolean`, `selectedPinId: String?` from the `Ready` data class. Remove the `ScreenOffset` import.

- [ ] T4: Remove deleted methods from `MapViewModel` and fix all call sites
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action: Delete `onPinsProjected()`, `onMapGestureStart()`, `onPinChipTapped()`. Remove all `state.copy(pinScreenPositions = ...)`, `state.copy(cardsVisible = ...)`, `state.copy(selectedPinId = ...)` assignments from other methods (there are ~4 sites where these are reset to defaults on area fetch).
  - File: `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt`
  - Action: Remove `onPinsProjected` and `onMapGestureStart` parameters and call sites.
  - File: `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt`
  - Action: Same — remove `onPinsProjected` and `onMapGestureStart` params and calls.
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action: Remove `PinCardLayer` and `FloatingPoiCard` import lines and their usage blocks (lines ~286-318).

- [ ] T5: Run all tests — confirm green before proceeding to Phase 2
  - Command: `./gradlew :composeApp:allTests`
  - Notes: All existing tests must pass. If any fail due to missing state fields, fix the test fakes/assertions to match the new state shape.

**Phase 2 — Data model extensions**

- [ ] T6: Add `priceRange` to `POI`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt`
  - Action: Add `val priceRange: String? = null` after `userNote`.

- [ ] T7: Add `priceRange` and `areaHighlights` to Gemini DTOs and the 4 parse paths in `GeminiResponseParser` (the 5th path — `EnrichJson` in `GeminiAreaIntelligenceProvider` — is handled in T9b)
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt`
  - Action: (a) Add `val p: String? = null` to `PoiJson`. (b) Add `val p: String? = null` to `EnrichJson`. (c) Add `val ah: List<String> = emptyList()` to `Stage1Response`. (d) In `parsePinOnlyResponse`: add `priceRange = poiJson.p` to `POI(...)`. (e) In `parseStage1Response` — both the try block and the catch fallback — add `priceRange = poiJson.p`. (f) In `parseDynamicVibeResponse`: add `priceRange = poiJson.p`. (g) In `parsePoisJson`: add `priceRange = poiJson.p`.

- [ ] T8: Thread `areaHighlights` through `BucketUpdate`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt`
  - Action: Add `val areaHighlights: List<String> = emptyList()` to `VibesReady` and `PinsReady` data classes.

- [ ] T9: Pass `areaHighlights` and `priceRange` through `GeminiAreaIntelligenceProvider`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action: (a) `parseStage1Response` returns `Pair<List<DynamicVibe>, List<POI>>` — `cleaned` is a local variable inside it, inaccessible from the provider. Do NOT try to call `json.decodeFromString<Stage1Response>(cleaned)` from the provider. Instead: extract the markdown-fence stripping logic into an `internal` top-level function `stripMarkdownFences(raw: String): String` in `GeminiResponseParser.kt` (NOT `private` — `private` is file-scoped in Kotlin; `internal` makes it accessible across files within the same module), then call it from both `parseStage1Response` (replacing the inline regex) and from `GeminiAreaIntelligenceProvider` T9(a). The provider calls `stripMarkdownFences(rawStage1Json)`, then `json.decodeFromString<Stage1Response>(stripped)`, extracts `.ah`, and passes `areaHighlights = stage1Response.ah` to `VibesReady(...)` and `PinsReady(...)`. Single source of truth for the fence-stripping logic — no duplication risk. (b) In the EnrichJson→POI mapping at ~line 199: add `priceRange = e.p` to the `POI(...)` constructor.

- [ ] T10: Update Gemini prompts with new schema fields
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: (a) In `buildPinOnlyPrompt`: update `Stage1Response` schema to `{"vibes":[...],"pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"Vibe","p":"$$"}],"ah":["highlight"]}`. Add rule: `- ah: up to 3 short area highlights (recurring events, seasonal notes, trending now). Max 40 chars each. Omit if nothing notable.` (b) In `buildBackgroundBatchPrompt`: add `"p":"$$"` to the pois schema. (c) In `buildDynamicVibeEnrichmentPrompt`: add `"p":"$$"` to the enriched POI schema. (d) In `buildEnrichmentPrompt`: add `"p":"$$"` to the EnrichJson schema.

- [ ] T11: Add new fields to `MapUiState.Ready`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add `val selectedPinIndex: Int? = null`, `val areaHighlights: List<String> = emptyList()` to `Ready`. Confirm `latitude: Double` and `longitude: Double` already exist on `MapUiState.Ready` — these are existing fields used by the map camera. T20 and T19 reference them. No new additions needed for these two fields.

- [ ] T12: Add carousel handler methods to `MapViewModel`; handle `areaHighlights` in BucketUpdate handlers
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action: (a) Add `fun onPinTapped(index: Int) { val state = _uiState.value as? MapUiState.Ready ?: return; if (state.pois.isEmpty()) return; _uiState.value = state.copy(selectedPinIndex = index.coerceIn(0, state.pois.size - 1)) }`. (b) Add `fun onCarouselSwiped(index: Int)` — identical body with same `coerceIn` guard. (c) Add `fun onCarouselSelectionCleared() { val state = _uiState.value as? MapUiState.Ready ?: return; _uiState.value = state.copy(selectedPinIndex = null) }`. (d) In the `when (update)` BucketUpdate handler: for `is BucketUpdate.VibesReady` and `is BucketUpdate.PinsReady`, include `areaHighlights = update.areaHighlights` in the `state.copy(...)`. (e) In all 4 area-reset sites, also reset `selectedPinIndex = null`, `areaHighlights = emptyList()`.

- [ ] T13: Write tests for new ViewModel methods and parser changes
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt`
  - Action: Add `onPinTapped_setsSelectedPinIndex`, `onCarouselSwiped_updatesSelectedPinIndex`, `onCarouselSwiped_withOutOfBoundsIndex_clamps` (index 99 with 3 POIs — guard returns clamped value, no crash), `onCarouselSelectionCleared_setsSelectedPinIndexToNull`, `onPinTapped_whenPoisEmpty_doesNotCrash`, `areaFetch_storesAreaHighlightsFromVibesReady`, `areaReset_clearsSelectedPinIndexAndAreaHighlights`.
  - File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiResponseParserTest.kt`
  - Action: Add `parsePinOnlyResponse_includesPriceRange`, `parseStage1Response_includesPriceRange`, `parseStage1Response_includesAreaHighlights`, `parseStage1Response_missingAhField_defaultsToEmpty`, `parseDynamicVibeResponse_includesPriceRange`, `parsePoisJson_includesPriceRange`.

**Phase 3 — Android pin bitmap upgrades**

- [ ] T14: Extend `ensureIcon()` in `MapComposable.android.kt`
  - File: `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt`
  - Action: (a) `PinBadge` is defined in `commonMain/PinStatusUtils.kt` — do NOT redeclare it here. (b) Update `ensureIcon` signature: add `liveStatus: String?`, `isSelected: Boolean`, `badgeType: PinBadge?`. (c) Update icon key to `"poi_${vibe.name}_${typeKey}_${liveStatusToColor(liveStatus).name}_${badgeType?.name ?: "none"}_${isSelected}_${isSaved}"`. Use `badgeType?.name` (returns `"CLOSING_SOON"` / `"TRENDING"` / `"EVENT"`) — do NOT use `.javaClass.simpleName` which returns `"PinBadge"` for every variant, collapsing all badges to the same cache key. (d) After drawing emoji, draw status dot: 10px filled circle at `(size - 10f, size - 10f)` in the color from `liveStatusToColor(liveStatus)`. (e) If `badgeType != null`, draw badge emoji (12sp) at `(8f, 8f + descent)`. (f) If `isClosed(liveStatus)`, apply `Paint.setAlpha(90)` and `ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })` to the background circle paint BEFORE drawing (or draw a greyscale overlay). (g) If `isSelected`, draw white stroke ring: `Paint(ANTI_ALIAS_FLAG).apply { style = STROKE; strokeWidth = 6f; color = Color.WHITE }`, `canvas.drawCircle(size/2f, size/2f, size/2f - 3f, ringPaint)` as the last draw operation.
  - Notes: `liveStatusToColor` and `isClosed` are both defined in `commonMain/PinStatusUtils.kt` (not androidMain) so they are reachable from commonTest. Call `liveStatusToColor(liveStatus).toAndroidColor()` inside `ensureIcon` to get the Android `Int` color. Call `isClosed(liveStatus)` directly — it returns `liveStatusToColor(liveStatus) == PinStatusColor.RED`. Update all `ensureIcon` call sites in the file to pass the new params.

- [ ] T15: Update all `ensureIcon` call sites in `MapComposable.android.kt` to pass new params
  - File: `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt`
  - Action: Find every `ensureIcon(style, vibe, poi.type, isSaved)` call and add `liveStatus = poi.liveStatus`, `isSelected = (pois.indexOf(poi) == selectedPinIndex)`, `badgeType = deriveBadge(poi.liveStatus)`. Add `selectedPinIndex: Int?` parameter to `MapComposable.android.kt` function.

**Phase 4 — iOS pin migration**

- [ ] T16: Implement iOS pin bitmap drawing function
  - File: `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt`
  - Action: Add private function `drawPinImage(emoji: String, vibeColorHex: String, liveStatus: String?, isSelected: Boolean, isSaved: Boolean, badgeType: PinBadge?): UIImage`. Use `badgeType?.name` (e.g. `"CLOSING_SOON"`) to look up the badge emoji — define a local mapping: `CLOSING_SOON` → `"⏰"`, `TRENDING` → `"🔥"`, `EVENT` → `"🎤"`. This keeps the function signature type-safe and consistent with Android. Use `UIGraphicsImageRenderer(size: CGSizeMake(64.0, 64.0))`. Draw: (1) filled circle in vibe color; (2) emoji text centered; (3) status dot at bottom-right using `UIColor` from `liveStatus`; (4) badge emoji at top-left if `badgeType != null`; (5) white stroke ring if `isSelected`; (6) gold ring + checkmark if `isSaved`. For ghost (closed): (1) render the pin normally to a `UIImage`, (2) apply `CIColorMonochrome` filter (`inputColor = CIColor.gray`, `inputIntensity = 1.0`) to desaturate, (3) draw a `UIColor(white: 0, alpha: 0.65)` filled rect over the full bitmap to achieve ~35% effective opacity. This matches Android's greyscale + alpha approach.
  - Notes: Use `NSString.draw(at:withAttributes:)` for emoji rendering. Vibe color from existing `toColor()` extension — convert to `UIColor` via ARGB components.

- [ ] T17: Migrate iOS from `MLNPointAnnotation` to `MLNShapeSource` + `MLNSymbolStyleLayer`
  - File: `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt`
  - Action: In the `LaunchedEffect(pois, activeVibe, savedPoiIds, ...)` block: (a) Remove the `MLNPointAnnotation` creation loop and `mapView.addAnnotations(...)` call. (b) Remove `annotationPoiMap` and `currentAnnotations` refs. (c) For each filtered POI at index `i`, call `style.addImage(iconKey, drawPinImage(...))` where `iconKey` uses the same variant-encoding convention as Android. Set `MLNPointFeature` attributes `iconKey` and `poiIndex = i` (Int). Do NOT key by coordinate string — floating-point precision of `CLLocationCoordinate2D` when round-tripped through feature attributes is unreliable; use `poiIndex` for tap lookup instead. (d) Create `MLNShapeSource(identifier: "poi_icon_source", features: features, options: null)`. Before calling `style.addSource(...)`, check: `if (style.source(withIdentifier: "poi_icon_source") != null) { (style.source(withIdentifier: "poi_icon_source") as? MLNShapeSource)?.shape = MLNShapeCollectionFeature(shapes: features) } else { style.addSource(source) }`. Same pattern for the layer: check `style.layer(withIdentifier: "poi_icon_layer") != null` before calling `style.addLayer(...)`. This prevents "source already exists" crashes on the second area load. (e) Create `MLNSymbolStyleLayer(identifier: "poi_icon_layer", source: source)`. Set `iconImageName = NSExpression.expressionForKeyPath("iconKey")`. Set `iconAllowsOverlap = true`. (f) Update text label layer: the existing `POI_TEXT_SOURCE_ID` source is annotation-based and will be removed. Update the text label `MLNSymbolStyleLayer` to use `poi_icon_source` instead (features already carry `name` attribute). Remove `POI_TEXT_SOURCE_ID` source entirely. IMPORTANT — both the text label layer update and the `poi_icon_source` creation must live in the same `LaunchedEffect(pois, activeVibe, savedPoiIds, ...)` block (not in a separate `LaunchedEffect(style)` block). `poi_icon_source` does not exist at style-load time — if the text label layer is initialised in an earlier style-load effect and references `poi_icon_source`, MapLibre will silently fail to render labels because the source doesn't exist yet. Sequence within the block: add images → add/update source → add icon layer (idempotency-guarded) → add/update text label layer pointing at `poi_icon_source` (idempotency-guarded). (g) Update tap handling: add a `UITapGestureRecognizer` to `mapView`. In the `@ObjCAction` handler, call `mapView.visibleFeatures(at: point, inStyleLayersWithIdentifiers: ["poi_icon_layer"])`. Cast the first result to `MLNPointFeature`, read `feature.attribute("poiIndex") as? Int`, look up `pois[poiIndex]`, and call `onPinTapped(poiIndex)`. Remove the old `mapView(_:didSelectAnnotation:)` delegate method entirely.
  - Notes: **Tap detection:** Use `UITapGestureRecognizer` added to `mapView`. In the handler call `mapView.visibleFeatures(at: point, inStyleLayersWithIdentifiers: ["poi_icon_layer"])`, cast to `MLNPointFeature`, read `feature.attribute("poiIndex") as? Int`, and call `onPinTapped(poiIndex)`. Do NOT use coordinate string lookup — coordinate precision is unreliable round-tripping through feature attributes. **Annotation call site audit (F13):** Before removing `annotationPoiMap` and `currentAnnotations`, search the file for every usage: `removeAnnotations`, `addAnnotations`, `currentAnnotations.isNotEmpty()`, `annotationPoiMap[`, and the `MapDelegate.mapView(_:didSelectAnnotation:)` method. All must be removed or replaced — a leftover `removeAnnotations(currentAnnotations)` call after migration will silently no-op but the `didSelectAnnotation` delegate will never fire, causing a silent tap bug. Remove `POI_TEXT_SOURCE_ID` / `POI_TEXT_LAYER_ID` source cleanup from the annotation clearing block — they should only be removed when POIs change, not when annotations clear (since annotations no longer exist).

- [ ] T18: Add `selectedPinIndex: Int?` param to iOS `MapComposable` and re-render on change
  - File: `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt`
  - Action: Add `selectedPinIndex: Int?` to the `actual fun MapComposable(...)` signature. Add `LaunchedEffect(selectedPinIndex)` that re-renders only the previously-selected and newly-selected pin images: call `style.addImage(prevIconKey, drawPinImage(isSelected = false, ...))` for the deselected pin and `style.addImage(newIconKey, drawPinImage(isSelected = true, ...))` for the newly selected pin. The `MLNSymbolStyleLayer` resolves image keys from the style image cache automatically — no `symbolLayer.update()` call exists or is needed in MapLibre iOS.
  - Notes: Also update `commonMain` expect declaration and Android actual to include `selectedPinIndex: Int?`. Track `prevSelectedPinIndex` via `var prevIndex by remember { mutableStateOf<Int?>(null) }` updated inside the `LaunchedEffect`. Retrieve all params for the old pin via `pois.getOrNull(prevIndex)` — this is safe because `pois` is a snapshot captured by the LaunchedEffect's closure. If `pois` has changed (new area load), `prevIndex` is stale and `getOrNull` returns null — skip the deselect re-render in that case.

**Phase 5 — AmbientTicker composable**

- [ ] T19: Create `AmbientTicker.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AmbientTicker.kt`
  - Action: (a) Add internal pure function `buildTickerSlots(pois: List<POI>, lat: Double, lng: Double, areaHighlights: List<String>, sunsetMinutesProvider: (Double, Double) -> Int = ::calculateSunsetMinutes): List<String>`. The `sunsetMinutesProvider` parameter defaults to the real NOAA implementation but can be overridden in tests with a stub lambda (e.g. `{ _, _ -> 45 }`). In the composable, call it via `val slots = remember(pois, lat, lng, areaHighlights) { buildTickerSlots(pois, lat, lng, areaHighlights) }` — the NOAA solar calculation is non-trivial and must not run on every recomposition. Slots: (1) `"${openCount} open nearby"` if openCount > 0; (2) `"Sunset in ${minutesUntilSunset} min"` if minutesUntilSunset in 1..120; (3) each string in `areaHighlights`. Return only non-empty slots. (b) `@Composable fun AmbientTicker(pois, latitude, longitude, areaHighlights, modifier)`. Derive dot counts explicitly in the composable body: `val statusCounts = remember(pois) { pois.groupBy { liveStatusToColor(it.liveStatus) }.mapValues { it.value.size } }; val greenCount = statusCounts[PinStatusColor.GREEN] ?: 0; val orangeCount = statusCounts[PinStatusColor.ORANGE] ?: 0; val redCount = statusCounts[PinStatusColor.RED] ?: 0`. Declare rotation index: `var currentIndex by remember { mutableStateOf(0) }`. Build slots. If `slots.isEmpty()`, return without rendering. If `slots.size == 1`, show static (`currentIndex = 0`). If `slots.size > 1`, `LaunchedEffect(slots) { currentIndex = 0; while(true) { delay(5000); currentIndex = (currentIndex + 1) % slots.size } }` — resetting `currentIndex = 0` at LaunchedEffect start prevents out-of-bounds when slots shrinks on recomposition. Access via `slots[currentIndex]` (safe after reset). (c) Layout: `Row(modifier.fillMaxWidth().background(MapFloatingUiDark.copy(alpha=0.85f)).padding(horizontal=12.dp, vertical=6.dp))`. Left: `AnimatedContent(targetState=slots[currentIndex]) { Text(it, style=labelMedium) }`. Right: `Text("● $greenCount", color=PinStatusColor.GREEN.toComposeColor()); Text("  ● $orangeCount", color=PinStatusColor.ORANGE.toComposeColor()); Text("  ● $redCount", color=PinStatusColor.RED.toComposeColor())`.
  - Notes: `liveStatusToColor()` is defined in `PinStatusUtils.kt` in `commonMain` — accessible from both `AmbientTicker` and `PoiCarousel`. `MapFloatingUiDark` is an existing color token — confirm it exists in `composeApp/src/commonMain/kotlin/com/harazone/ui/theme/` (or equivalent theme file) before using it; if absent, add it there (`Color(0xFF1A1A2E)` or match existing dark overlay convention).

- [ ] T20: Add `AmbientTicker` to `MapScreen.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action: Below the `GeocodingSearchBar` composable call (after its `padding` modifier), add `if (state.pois.isNotEmpty()) { AmbientTicker(pois = state.pois, latitude = state.latitude, longitude = state.longitude, areaHighlights = state.areaHighlights, modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarPadding + 56.dp + 48.dp + 4.dp)) }`. `statusBarPadding` must be a `Dp` value — obtain via `WindowInsets.systemBars.asPaddingValues().calculateTopPadding()` at the composable call site. Adding `Dp + Dp` is valid; mixing `Int`/`Float` raw pixels with `Dp` is a compile error.

**Phase 6 — PoiCarousel composable**

- [ ] T21: Create `PoiCarousel.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/PoiCarousel.kt`
  - Action: `@Composable fun PoiCarousel(pois, selectedIndex, savedPoiIds, onCardSwiped, onSelectionCleared, onSaveTapped, onDetailTapped, modifier)`. (a) `val listState = rememberLazyListState()`. `val snapBehavior = rememberSnapFlingBehavior(listState)`. (b) `var isProgrammaticScroll by remember { mutableStateOf(false) }`. (c) `LaunchedEffect(selectedIndex) { try { isProgrammaticScroll = true; if (selectedIndex != null) listState.animateScrollToItem(selectedIndex) } finally { isProgrammaticScroll = false } }`. (d) `LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.drop(1).collect { if (!isProgrammaticScroll) onCardSwiped(it) } }`. (e) `PlatformBackHandler(enabled = selectedIndex != null) { onSelectionCleared() }` — clears to null, not index 0. (f) Wrap the LazyRow and page dots in `BoxWithConstraints { val screenWidth = maxWidth` — `maxWidth` is a `Dp` provided by `BoxWithConstraints`, the correct cross-platform width source in commonMain. (g) `LazyRow(state=listState, flingBehavior=snapBehavior, contentPadding=PaddingValues(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp))` with `items(pois)` block. (h) Each card: `Box(Modifier.width(screenWidth - 32.dp).clip(RoundedCornerShape(16.dp)).background(MapFloatingUiDark.copy(0.94f)))`. Left status stripe: `val liveStatusColor = liveStatusToColor(poi.liveStatus).toComposeColor(); Box(Modifier.width(4.dp).fillMaxHeight().background(liveStatusColor))`. Content column: emoji + name (`titleMedium`) + vibe (`labelSmall`, 60% alpha) + `poi.vs` (vibe summary — existing field, 2 lines max, `bodySmall`) + meta row (⭐ rating · priceRange · hours) + CTA row (Save icon button + "Details →" TextButton). (i) Page dots: `val visibleIndex = listState.firstVisibleItemIndex; Row` centered below `LazyRow` with `pois.indices.map { filled if it == visibleIndex else outline dot }`.
  - Notes: Import `PlatformBackHandler` from `com.harazone.ui.components`.

- [ ] T22: Add `PoiCarousel` to `MapScreen.kt` and wire up
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action: Add `if (state.pois.isNotEmpty() && !state.showListView && state.selectedPoi == null) { PoiCarousel(pois = state.pois, selectedIndex = state.selectedPinIndex, savedPoiIds = state.savedPoiIds, onCardSwiped = { viewModel.onCarouselSwiped(it) }, onSelectionCleared = { viewModel.onCarouselSelectionCleared() }, onSaveTapped = { viewModel.toggleSavePoi(it) }, onDetailTapped = { viewModel.selectPoi(it) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBarPadding + 72.dp)) }`. `navBarPadding` must be a `Dp` value — obtain via `WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()`. Same pattern as `statusBarPadding` in T20.

**Phase 7 — MapComposable signature updates and final wiring**

- [ ] T23: Add `onPinTapped: (Int) -> Unit` and `selectedPinIndex: Int?` to `MapComposable` expect/actual
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapComposable.kt` (expect)
  - Action: Add `onPinTapped: (Int) -> Unit` and `selectedPinIndex: Int?` to the `expect fun MapComposable(...)` signature.
  - File: `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt`
  - Action: Add to `actual fun MapComposable(...)`. In the symbol click listener (`sm.addClickListener`), after finding `poi`, compute `val index = pois.indexOf(poi)` and call `onPinTapped(index)`.
  - File: `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt`
  - Action: Add to `actual fun MapComposable(...)`. In tap delegate, compute index from `pois.indexOf(tappedPoi)` and call `onPinTapped(index)`.
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action: Pass `onPinTapped = { viewModel.onPinTapped(it) }` and `selectedPinIndex = state.selectedPinIndex` to `MapComposable`.

- [ ] T24: Run full test suite and fix any remaining issues
  - Command: `./gradlew :composeApp:allTests`
  - Notes: Expect clean green. Any state-shape test failures should be straightforward to fix by updating assertions to use `selectedPinIndex` instead of `selectedPinId`.

### Acceptance Criteria

**Deletion regression**

- [ ] AC1: Given the app is running with POIs loaded, When `MapUiState.Ready` is inspected at runtime, Then the fields `pinScreenPositions`, `cardsVisible`, and `selectedPinId` do not exist on the data class.
- [ ] AC2: Given POIs are loaded, When the map renders, Then `onPinsProjected` is never called — no coordinate bridge computation occurs.

**Bottom Carousel — presence and snap**

- [ ] AC3: Given 3 POIs are loaded and no detail view is open, When the map screen renders, Then a horizontal snap-scrollable carousel appears above the bottom nav bar with 3 cards and page dots.
- [ ] AC4: Given the carousel is showing, When user flings the carousel to card 2, Then card 2 snaps to center and page dot 2 becomes filled.
- [ ] AC5: Given the carousel is showing, When user taps pin 2 on the map, Then the carousel scrolls to card 2 and pin 2 renders with a white ring.
- [ ] AC6: Given card 2 is focused (selectedPinIndex = 1), When user presses the Android back button, Then selectedPinIndex clears to null and no pin shows a white ring.

**Bottom Carousel — card content**

- [ ] AC7: Given a POI with `priceRange = "$$"`, `rating = 4.2`, `hours = "9am–10pm"`, `liveStatus = "open"`, When the carousel card renders, Then the left stripe is green, rating shows "⭐ 4.2", price shows "$$", hours shows "9am–10pm".
- [ ] AC8: Given a POI with `liveStatus = null`, When its carousel card renders, Then the left stripe is grey and no status label is shown.
- [ ] AC9: Given a POI with `liveStatus = "closing soon"`, When its carousel card renders, Then the left stripe is orange.

**Ambient ticker**

- [ ] AC10: Given 3 POIs where 2 have `liveStatus = "open"` and 1 has `liveStatus = "closed"`, When the ticker renders, Then one of its rotation slots shows "2 open nearby".
- [ ] AC11: Given lat/lng is available and the current time is daytime (sunset >0 min away), When the ticker renders, Then the sunset slot shows "Sunset in X min" where X is within 5 minutes of the actual computed value.
- [ ] AC12: Given `areaHighlights = ["Jazz at 9pm", "Market till 8"]`, When the ticker cycles through slots, Then both strings appear as distinct rotation slots.
- [ ] AC13: Given only 1 ticker slot has data, When the ticker renders, Then the slot is shown statically with no rotation animation.
- [ ] AC14: Given `pois` is empty, When the map screen renders, Then no ticker component is shown.

**Pin ambient visuals — Android**

- [ ] AC15: Given a POI with `liveStatus = "closed"`, When rendered on Android map, Then the pin bitmap is greyscale at approximately 35% opacity.
- [ ] AC16: Given a POI with `liveStatus = "closing soon"`, When rendered on Android map, Then the status dot at the bottom-right of the pin is orange.
- [ ] AC17: Given `selectedPinIndex = 1` and 3 POIs, When pin at index 1 renders on Android, Then a white ring is visible around the pin circle.

**iOS pin quality**

- [ ] AC18: Given any POI on iOS, When the map renders, Then the pin shows an emoji in a vibe-colored circle — not the default red MapLibre teardrop.
- [ ] AC19: Given a POI with `liveStatus = "closed"` on iOS, When rendered, Then the pin appears desaturated and semi-transparent.
- [ ] AC20: Given `selectedPinIndex = 1` on iOS, When pin at index 1 renders, Then a white ring is visible around the pin circle matching Android visual output.

**Data pipeline — priceRange and areaHighlights**

- [ ] AC21: Given Gemini returns `"p":"$$"` for a POI in any response format, When the response is parsed, Then `poi.priceRange == "$$"`.
- [ ] AC22: Given Gemini Stage 1 returns `"ah":["Jazz Fridays","Outdoor Market Sat"]`, When the response is parsed and state updates, Then `MapUiState.Ready.areaHighlights == ["Jazz Fridays", "Outdoor Market Sat"]`.
- [ ] AC23: Given an area fetch that returns no `ah` field (old prompt cache), When parsed, Then `areaHighlights` defaults to empty list and ticker skips those slots without crashing.

## Additional Context

### Dependencies

- `kotlinx-datetime` — already in the project (saved card date formatting). No version bump needed.
- `androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior` — already available via existing Compose Foundation pager dependency.
- No new Gradle dependencies required for any part of this spec.
- Gemini prompt changes are additive — existing cached area responses return `areaHighlights = []` and `priceRange = null` gracefully. No cache invalidation needed.

### Testing Strategy

All tests in `composeApp/src/commonTest/kotlin/`.

Regression tests (T1, write BEFORE any deletion):
- `onPinsProjected_setsScreenPositionsAndShowsCards`
- `onMapGestureStart_hidesCards`
- `onPinChipTapped_togglesSelectedPinId`

ViewModel unit tests (T13):
- `onPinTapped_setsSelectedPinIndex`
- `onCarouselSwiped_updatesSelectedPinIndex`
- `onCarouselSwiped_withOutOfBoundsIndex_clamps`
- `onCarouselSelectionCleared_setsSelectedPinIndexToNull`
- `onPinTapped_whenPoisEmpty_doesNotCrash`
- `areaFetch_storesAreaHighlightsFromVibesReady`
- `areaReset_clearsSelectedPinIndexAndAreaHighlights`

Parser unit tests (T13):
- `parsePinOnlyResponse_includesPriceRange`
- `parseStage1Response_includesPriceRange`
- `parseStage1Response_includesAreaHighlights`
- `parseStage1Response_missingAhField_defaultsToEmpty`
- `parseDynamicVibeResponse_includesPriceRange`
- `parsePoisJson_includesPriceRange`

Ticker slot builder unit tests (pure function, no Compose needed):
- `buildTickerSlots_openCountSlot_countsCorrectly`
- `buildTickerSlots_sunsetSlot_skippedWhenNegative`
- `buildTickerSlots_sunsetSlot_skippedWhenOver120min`
- `buildTickerSlots_sunsetSlot_includesSlotWhenInRange` (positive case — pass `sunsetMinutesProvider = { _, _ -> 45 }`, assert slot "Sunset in 45 min" is present)
- `buildTickerSlots_areaHighlightsAppendedAsSlots`
- `buildTickerSlots_emptyWhenNoData`

Status util unit tests:
- `liveStatusToColor_open_returnsGreen`
- `liveStatusToColor_closingSoon_returnsOrange`
- `liveStatusToColor_closed_returnsRed`
- `liveStatusToColor_null_returnsGrey`
- `isClosed_closed_returnsTrue`
- `isClosed_open_returnsFalse`

Manual testing steps:
1. Android: launch app, verify carousel appears with 3 snap cards. Tap each pin — verify carousel scrolls. Swipe carousel — verify pin highlights. Press back — verify selection clears.
2. Android: verify pin status dots are colored correctly for open/closing/closed POIs. Verify closed pins are greyscale.
3. iOS: verify pins show emoji circles (not red teardrop). Verify tap → carousel scroll works. Verify closed pin is desaturated.
4. Both: verify ticker appears below search bar and rotates slots. Verify sunset slot shows a reasonable time.
5. Both: verify `priceRange` shows on carousel cards when Gemini returns it.

### Notes

- Spec 1 of 3. Spec 2 = List View Refresh. Spec 3 = AI Detail Page (chat as detail). All 3 are tester release gates (target 2026-03-21).
- High risk items:
  - iOS pin migration: tap detection uses `UITapGestureRecognizer` + `mapView.visibleFeatures(at:inStyleLayersWithIdentifiers:["poi_icon_layer"])` (see T17). The `@ObjCAction` TODO in `MapComposable.ios.kt` (~line 100) is resolved by this approach — no `MLNPointAnnotation` wrapper needed.
  - `snapshotFlow` carousel sync: `isProgrammaticScroll` flag (set before `animateScrollToItem`, cleared in `finally`) gates the `firstVisibleItemIndex` collector so programmatic scrolls never fire `onCardSwiped`. `isScrollInProgress` is NOT used as the guard — it fires for both programmatic and user scrolls and would re-arm itself during animation. This is implemented in T21 — not an open decision.
  - `ensureIcon` bitmap cache: the new icon key must be carefully constructed. If any variant param is missed, stale bitmaps will show (e.g. a closed pin shows as open after re-render). Test pin re-render on `selectedPinIndex` change explicitly.
- **Bitmap visual testing limitation (F12):** Pin bitmap rendering (`ensureIcon` on Android, `drawPinImage` on iOS) cannot be unit-tested in `commonTest` — no Canvas/UIGraphicsImageRenderer in JVM test environment. Cover with: (a) unit tests on `liveStatusToColor`, `isClosed`, `deriveBadge` pure functions (testable without bitmaps); (b) AC15–AC17 and AC18–AC20 verified manually on device/simulator; (c) iconKey string format tested by asserting the key string contains the expected variant tokens for a given input combination.
- Activity rings deferred to v1.1 — requires continuous per-pin animation loop, not suitable for MapLibre bitmap approach.
- AI Whispers + Relaunch Greeting deferred — backend infrastructure required (feature #43 in project memory).
- iOS pin migration resolves the existing BACKLOG-HIGH TODO about gold saved pins via `MLNAnnotationView` ObjC bridging — the new `MLNSymbolStyleLayer` approach handles saved state in the bitmap directly.
- `distance` field in carousel meta row is deferred (noted in scope). Show a dash `—` placeholder or omit the field entirely in v1.
