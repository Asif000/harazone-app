---
title: 'Radial Galaxy Chip Layout'
slug: 'radial-galaxy-chip-layout'
created: '2026-03-06'
status: 'superseded'
superseded_by: 'tech-spec-v3-fullscreen-map'
superseded_note: 'V3 retires SummaryScreen entirely. RadialGalaxy and related components targeting SummaryScreen will be deleted as part of V3.'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain / androidMain / iosMain)'
  - 'Compose Multiplatform 1.10.0'
  - 'Material 3 1.10.0-alpha05'
  - 'compose-foundation (Canvas / DrawScope)'
  - 'Kotlin Coroutines 1.10.2'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Color.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketTypeMapper.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/RadialGalaxy.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/TimeOfDay.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/components/TimeOfDay.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/areadiscovery/ui/components/TimeOfDay.ios.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/components/RadialGalaxyTest.kt'
code_patterns:
  - 'expect/actual for platform utils (ReduceMotion.kt, Platform.kt pattern)'
  - 'BucketType extension functions in BucketTypeMapper.kt'
  - 'ModalBottomSheet + rememberModalBottomSheetState for detail sheets'
  - 'Animatable for per-element animation'
  - 'MutableTransitionState + AnimatedVisibility for entrance'
  - 'MaterialTheme.spacing.* for all spacing'
  - 'Canvas {} composable from compose-foundation'
test_patterns:
  - 'kotlin.test in commonTest, pure functions only'
  - 'No Compose UI tests this iteration (design is fluid)'
  - 'Test pure geometric function: computeOrbPositions(n, radius)'
---

# Tech-Spec: Radial Galaxy Chip Layout

**Created:** 2026-03-06

## Overview

### Problem Statement

`ChipCloud` renders the area portrait as a flat `FlowRow` list — it doesn't communicate that HISTORY is the identity anchor of a place, or make AI streaming feel like something forming. The current layout is functional but visually inert.

### Solution

Replace `ChipCloud` with a new `RadialGalaxy` composable: HISTORY orb at center (the "sun"), remaining discovery chips distributed evenly around it on a circular ring using dynamic N-point polygon math. A `Canvas` `DrawScope` draws a dashed orbital ring and spokes as chips stream in. Each chip arrival fires a sonar pulse from the HISTORY center; completion fires a triple sonar burst. Per-bucket accent colors and a time-of-day background gradient make the space feel alive.

### Scope

**In Scope:**
- New `RadialGalaxy` composable (`RadialGalaxy.kt`) — same public signature as `ChipCloud`
- Replaces `ChipCloud` call-site in `ChipDashboard` (SummaryScreen.kt)
- HISTORY center orb (68dp) — tappable → `ModalBottomSheet` with era cards (duplicates sheet content from `MiniTimelineStrip`)
- N outer orbs (44dp) distributed at `angle = -π/2 + (2π / n) * i` (top-first, clockwise) — layout is N-agnostic; 5–6 expected
- Outer orbs: tappable → `ModalBottomSheet` where `chip.detail.isNotBlank()`
- `Canvas` DrawScope: dashed orbital ring + spokes per visible outer chip (progressive alpha)
- Sonar pulse per chip arrival (chip accent color), triple sonar burst on `isComplete = true`
- Per-bucket accent colors added to `Color.kt` + `BucketType.galaxyAccentColor()` in `BucketTypeMapper.kt`
- Time-of-day background gradient: `expect fun currentHour(): Int` in `TimeOfDay.kt` + actuals
- Adaptive ring diameter: `min(constraints.maxWidth * 0.82f, 300.dp.toPx())`
- `AlertBanner` stays above galaxy — unchanged
- `reduceMotion` respected: skip sonar, skip orbital draw animation, skip fade-in
- Count-up animation on number chip orb sublabels
- Orb label: `bucketType.displayTitle()` (name, 9.5sp bold) + `chip.label` truncated to 1 line (sublabel, 8.5sp, white 45% alpha)
- Pure function `computeOrbPositions(n, radiusPx)` extracted and unit-tested

**Out of Scope:**
- `MiniTimelineStrip` internals (reused sheet content only, chip trigger UI is not reused)
- `ChipExtractor`, ViewModel, data layer
- Bottom sheet content redesign
- `AlertBanner` redesign
- Landscape / tablet layout
- Accessibility (TalkBack ordering) — deferred to backlog

## Context for Development

### Codebase Patterns

- **KMP composables**: all in `commonMain` — no Android-only APIs in composable layer
- **Canvas**: use `androidx.compose.foundation.Canvas {}` composable; `DrawScope` available in CMP via `compose-foundation` (already in deps). **This will be the first Canvas usage in the project.**
- **expect/actual**: `rememberReduceMotion()` in `ReduceMotion.kt` + `Platform.kt` with `getPlatform()` show the exact pattern. Add `expect fun currentHour(): Int` in `TimeOfDay.kt` (commonMain).
- **reduceMotion**: `expect @Composable fun rememberReduceMotion(): Boolean` — call at top of `RadialGalaxy`, pass down to sub-composables
- **Spacing**: use `MaterialTheme.spacing.*` tokens throughout; never hardcode padding dp values
- **ModalBottomSheet pattern**: `var showSheet by remember { mutableStateOf(false) }` → render sheet inside same composable (see `DiscoveryChipItem`, `MiniTimelineStrip`)
- **Sonar ring animation**: `Animatable` pair — `scaleAnim = Animatable(0.15f)`, `alphaAnim = Animatable(0.9f)`. Launch both simultaneously: `animate to scale=4f over tween(1400)`, `animate to alpha=0f over tween(1400)`. Draw in `Canvas` as `drawCircle(style = Stroke)`. Maintain `mutableStateListOf<SonarRingState>()` — add on arrival, remove after animation completes.
- **MutableTransitionState + AnimatedVisibility**: for per-orb fade-in entrance (see `ChipCloud.kt:97`). Each orb gets its own `remember { MutableTransitionState(false).apply { targetState = true } }`.
- **Count-up**: `LaunchedEffect(chip.numberValue)` loop, `COUNT_UP_FRAMES = 30`, `COUNT_UP_DURATION_MS = 400L` — copy directly from `ChipCloud.kt:131–141`
- **HISTORY sheet content**: duplicate from `MiniTimelineStrip.kt:62–103` — `val eras = remember(chip.detail) { parseTimelineEras(chip.detail) }` + `ModalBottomSheet { LazyRow { items(eras) { era -> Card { ... } } } }`. Do NOT modify `MiniTimelineStrip.kt`.
- **No kotlinx-datetime**: use `expect fun currentHour(): Int` only

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ChipCloud.kt` | Being replaced — harvest: `rememberReduceMotion` usage, `AnimatedVisibility` entrance (line 97–113), count-up logic (131–141), `ModalBottomSheet` pattern (206–229) |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt:210–236` | `ChipDashboard` — replace `ChipCloud(chips = chips, isComplete = isComplete)` with `RadialGalaxy(chips = chips, isComplete = isComplete)` + update import |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/DiscoveryChip.kt` | Data model: `bucketType`, `label`, `detail`, `isWow`, `isNumber`, `numberValue`, `eraYears` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Bucket.kt` | `BucketType` enum: SAFETY, CHARACTER, WHATS_HAPPENING, COST, HISTORY, NEARBY |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketTypeMapper.kt` | Add `fun BucketType.galaxyAccentColor(): Color` extension here alongside existing `displayTitle()` / `icon()` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/MiniTimelineStrip.kt` | Duplicate sheet body (lines 62–103) into RadialGalaxy HISTORY orb — do NOT modify this file |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ReduceMotion.kt` | Pattern to follow for `expect fun currentHour(): Int` declaration |
| `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/components/ReduceMotion.android.kt` | Pattern to follow for `TimeOfDay.android.kt` actual |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Color.kt` | Add 6 galaxy accent color constants after existing `Confidence*` constants |
| `_bmad-output/brainstorming/visual-treatments-v1.html` | Treatment C reference. Key: `GPOS` pentagon coords, `sonar-out` keyframe (scale 0.15→4, opacity 0.9→0, 1400ms), triple burst timing (0 / 320 / 640ms), orbital ring alpha formula, `drawGalaxyC()` spoke logic |

### Technical Decisions

- **Per-bucket accent colors** — add to `Color.kt` after `ConfidenceLow`:
  ```kotlin
  val GalaxyHistory   = Color(0xFFCE93D8)
  val GalaxySafety    = Color(0xFF90CAF9)
  val GalaxyCharacter = Color(0xFF80DEEA)
  val GalaxyHappening = Color(0xFFA5D6A7)
  val GalaxyCost      = Color(0xFFFFCC80)
  val GalaxyNearby    = Color(0xFFF48FB1)
  ```

- **`BucketType.galaxyAccentColor()`** — add to `BucketTypeMapper.kt`:
  ```kotlin
  fun BucketType.galaxyAccentColor(): Color = when (this) {
      BucketType.HISTORY         -> GalaxyHistory
      BucketType.SAFETY          -> GalaxySafety
      BucketType.CHARACTER       -> GalaxyCharacter
      BucketType.WHATS_HAPPENING -> GalaxyHappening
      BucketType.COST            -> GalaxyCost
      BucketType.NEARBY          -> GalaxyNearby
  }
  ```

- **TimeOfDay.kt structure** (commonMain):
  ```kotlin
  expect fun currentHour(): Int

  enum class TimeOfDaySlot { DAWN, DAY, DUSK, NIGHT }

  fun timeOfDaySlot(hour: Int = currentHour()): TimeOfDaySlot = when (hour) {
      in 5..7   -> TimeOfDaySlot.DAWN
      in 8..16  -> TimeOfDaySlot.DAY
      in 17..19 -> TimeOfDaySlot.DUSK
      else      -> TimeOfDaySlot.NIGHT
  }

  // Returns Pair(centerColor, edgeColor) for radial gradient
  fun TimeOfDaySlot.backgroundColors(): Pair<Color, Color> = when (this) {
      TimeOfDaySlot.DAWN  -> Color(0xFF1A0A2E) to Color(0xFF3D1C00)
      TimeOfDaySlot.DAY   -> Color(0xFF0B0F1E) to Color(0xFF060709)
      TimeOfDaySlot.DUSK  -> Color(0xFF1A0A00) to Color(0xFF2D0A1A)
      TimeOfDaySlot.NIGHT -> Color(0xFF0B0F1E) to Color(0xFF060709)
  }
  ```

- **TimeOfDay.android.kt** (androidMain actual):
  ```kotlin
  actual fun currentHour(): Int =
      java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
  ```

- **TimeOfDay.ios.kt** (iosMain actual):
  ```kotlin
  import platform.Foundation.NSCalendar
  import platform.Foundation.NSCalendarUnitHour
  import platform.Foundation.NSDate
  actual fun currentHour(): Int {
      val cal = NSCalendar.currentCalendar
      return cal.components(NSCalendarUnitHour, NSDate()).hour.toInt()
  }
  ```

- **`computeOrbPositions()`** — top-level pure function in `RadialGalaxy.kt` (internal visibility), also tested:
  ```kotlin
  internal fun computeOrbPositions(n: Int, radiusPx: Float): List<Offset> =
      List(n) { i ->
          val angle = -Math.PI / 2.0 + (2.0 * Math.PI / n) * i
          Offset((radiusPx * cos(angle)).toFloat(), (radiusPx * sin(angle)).toFloat())
      }
  ```

- **Layout structure**: `BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center)` → compute `ringDiameterPx` → `Box(Modifier.size(ringDiameterDp))` containing: (1) `Canvas(Modifier.fillMaxSize())` for all drawing, (2) HISTORY orb `Box(Modifier.align(Alignment.Center).size(68.dp))`, (3) outer orbs `Box(Modifier.align(Alignment.Center).offset { IntOffset(dx, dy) }.size(44.dp))` where dx/dy are orb center offset minus half orb size in px.

- **Orbital ring draw**: `drawCircle(style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f), 0f)), color = White.copy(alpha = min(0.12f, (visibleOuterCount - 1) * 0.03f)), radius = orbitalRadiusPx)`. Only when `visibleOuterCount > 1`.

- **Spoke draw**: `drawLine(color = White.copy(alpha = 0.07f), start = centerPx, end = centerPx + orbPositions[i], strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f), 0f))` for each visible outer chip.

- **SonarRingState** data class (internal): `data class SonarRingState(val color: Color, val scale: Animatable<Float, AnimationVector1D> = Animatable(0.15f), val alpha: Animatable<Float, AnimationVector1D> = Animatable(0.9f))`. Draw radius = `orbSize / 2 * scale.value` where `orbSize` is the chip's orb size in px.

- **Triple sonar on complete**: `LaunchedEffect(isComplete)` — when `isComplete == true && !reduceMotion`, fire 3 sonar rings: `fireSonar(delay = 0)`, `fireSonar(delay = 320)`, `fireSonar(delay = 640)` each using `White.copy(alpha = 0.35f)`.

- **HISTORY orb tapping**: `MiniTimelineStrip` is NOT embedded (it renders its own `SuggestionChip`). Instead: center orb Box has `Modifier.clickable { showHistorySheet = true }`. Sheet content duplicated from `MiniTimelineStrip.kt:62–103`.

- **Outer orb no-detail**: if `chip.detail.isBlank()`, the orb box has no clickable modifier (no tap ripple).

- **ChipCloud.kt is NOT deleted**: leave in place; only the call-site in `ChipDashboard` is swapped.

## Implementation Plan

### Tasks

- [x] **Task 1: Add galaxy accent colors**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Color.kt`
  - Action: Add 6 `val Galaxy*` color constants (see Technical Decisions) after the existing `val ConfidenceLow` line
  - Notes: No imports needed — `Color` is already imported

- [x] **Task 2: Add `BucketType.galaxyAccentColor()` extension**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketTypeMapper.kt`
  - Action: Add `import com.areadiscovery.ui.theme.Galaxy*` imports + new extension function `fun BucketType.galaxyAccentColor(): Color` (see Technical Decisions) after the existing `icon()` function
  - Notes: Must import each Galaxy color constant individually or use `import com.areadiscovery.ui.theme.*`

- [x] **Task 3: Create `TimeOfDay.kt` (commonMain expect + pure functions)**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/TimeOfDay.kt`
  - Action: Create new file with `expect fun currentHour(): Int`, `enum class TimeOfDaySlot`, `fun timeOfDaySlot()`, `fun TimeOfDaySlot.backgroundColors()` (see Technical Decisions)
  - Notes: Package `com.areadiscovery.ui.components`. No Composable annotation on `currentHour` — it's a plain function.

- [x] **Task 4: Create `TimeOfDay.android.kt` (androidMain actual)**
  - File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/components/TimeOfDay.android.kt`
  - Action: Create file with `actual fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)`
  - Notes: Package `com.areadiscovery.ui.components`. Mirror the directory structure of `ReduceMotion.android.kt`.

- [x] **Task 5: Create `TimeOfDay.ios.kt` (iosMain actual)**
  - File: `composeApp/src/iosMain/kotlin/com/areadiscovery/ui/components/TimeOfDay.ios.kt`
  - Action: Create file with `actual fun currentHour()` using `NSCalendar.currentCalendar.components(NSCalendarUnitHour, NSDate()).hour.toInt()` (see Technical Decisions)
  - Notes: Package `com.areadiscovery.ui.components`. Requires `platform.Foundation.*` imports.

- [x] **Task 6: Create `RadialGalaxy.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/RadialGalaxy.kt`
  - Action: Create the full composable. Implement in this order within the file:
    1. `internal fun computeOrbPositions(n: Int, radiusPx: Float): List<Offset>` — pure trig function (see Technical Decisions)
    2. `internal data class SonarRingState(val color: Color, val scale: Animatable<Float,..>, val alpha: Animatable<Float,..>)`
    3. `@Composable fun RadialGalaxy(chips: List<DiscoveryChip>, isComplete: Boolean, modifier: Modifier = Modifier)` — main composable:
       - Split chips: `historyChip` (HISTORY bucket) + `outerChips` (all others)
       - `BoxWithConstraints` → compute `ringDiameterPx`, `ringDiameterDp`, `orbitalRadiusPx = ringDiameterPx * 0.5f * 0.733f` (matches prototype's 110/150 ratio)
       - `remember { timeOfDaySlot() }` for background colors (not reactive — recomputes on recompose only, no ticking)
       - `val sonarRings = remember { mutableStateListOf<SonarRingState>() }`
       - `val coroutineScope = rememberCoroutineScope()`
       - `val visibleOuterCount = remember { mutableIntStateOf(0) }` — updated via callback from outer orbs
       - `Canvas(Modifier.fillMaxSize())`: draw radial gradient background, orbital ring (when visibleOuterCount > 1), spokes, sonar rings
       - `LaunchedEffect(isComplete)`: triple sonar burst when `isComplete == true && !reduceMotion`
       - HISTORY orb: `Box(Modifier.align(Alignment.Center).size(68.dp))` — circle background with `GalaxyHistory` accent, `Icon(BucketType.HISTORY.icon())`, `isWow` → `rememberInfiniteTransition` glow pulse, tappable → `showHistorySheet`; sheet content from `MiniTimelineStrip.kt:62–103`
       - Outer orbs: `outerChips.forEachIndexed` — `Box(Modifier.align(Alignment.Center).offset { IntOffset(...) }.size(44.dp))`, `AnimatedVisibility(visibleState)`, `LaunchedEffect(chip.bucketType)` fires sonar + increments `visibleOuterCount`, inner `Column` with orb circle + `displayTitle()` text + label sublabel, `LaunchedEffect` for count-up if `isNumber`
  - Notes: Import `kotlin.math.cos`, `kotlin.math.sin`, `kotlin.math.PI`. Use `PathEffect.dashPathEffect` from `androidx.compose.ui.graphics`. `Animatable` from `androidx.compose.animation.core`. `@OptIn(ExperimentalMaterial3Api::class)` needed for `ModalBottomSheet`.

- [x] **Task 7: Create `RadialGalaxyTest.kt`**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/components/RadialGalaxyTest.kt`
  - Action: Write `kotlin.test` tests for `computeOrbPositions`:
    - `computeOrbPositions_singleOrb_pointsUp`: n=1 → single offset at `(0f, -radius)` (top)
    - `computeOrbPositions_twoOrbs_opposite`: n=2 → first at top `(0f, -r)`, second at bottom `(0f, r)` within float tolerance
    - `computeOrbPositions_fiveOrbs_correctCount`: n=5 → list size is 5
    - `computeOrbPositions_fiveOrbs_evenlySpaced`: verify each consecutive angle diff is `2π/5` (72°)
    - `computeOrbPositions_allOnCircle`: verify each offset magnitude ≈ `radiusPx` within 0.01f tolerance
  - Notes: Use `kotlin.math.sqrt`, `kotlin.math.abs` for assertions. `internal` visibility of `computeOrbPositions` is accessible from `commonTest` in same module.

- [x] **Task 8: Swap call-site in `SummaryScreen.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryScreen.kt`
  - Action:
    1. Remove import `import com.areadiscovery.ui.components.ChipCloud`
    2. Add import `import com.areadiscovery.ui.components.RadialGalaxy`
    3. In `ChipDashboard` (~line 230): replace `ChipCloud(chips = chips, isComplete = isComplete)` with `RadialGalaxy(chips = chips, isComplete = isComplete)`
  - Notes: Signature is identical — no other changes to `ChipDashboard` needed.

### Acceptance Criteria

- [ ] **AC 1:** Given the screen renders with no chips yet (loading state), when `RadialGalaxy(chips = emptyList(), isComplete = false)` renders, then a dark radial gradient fills the ring area (slot-appropriate for time of day) and no orbital ring, spokes, or sonar are visible.

- [ ] **AC 2:** Given HISTORY chip arrives, when it first appears, then: (a) a 68dp center orb fades in with `GalaxyHistory` accent glow, (b) a sonar pulse expands and fades over ~1400ms using `GalaxyHistory` color, (c) no orbital ring is drawn yet.

- [ ] **AC 3:** Given a first outer chip arrives after HISTORY, when it appears, then: (a) its 44dp orb fades in at the correct top-first clockwise position, (b) a sonar pulse fires using that chip's accent color, (c) the dashed orbital ring appears at low alpha.

- [ ] **AC 4:** Given N outer chips visible (N ≥ 2), when Canvas redraws, then orbital ring alpha = `min(0.12f, (N - 1) * 0.03f)` and N dashed spokes run from center to each visible outer orb position.

- [ ] **AC 5:** Given all chips are loaded and `isComplete` transitions to `true`, when the effect fires, then exactly 3 sonar pulses emit from center at 0ms, 320ms, 640ms intervals using white accent color.

- [ ] **AC 6:** Given an outer orb where `chip.detail.isNotBlank()`, when user taps it, then a `ModalBottomSheet` opens displaying `bucketType.displayTitle()` as title and `chip.detail` as body.

- [ ] **AC 7:** Given the HISTORY center orb where `chip.eraYears` is non-empty, when user taps it, then a `ModalBottomSheet` opens with a horizontal `LazyRow` of era cards (year + sentence per card).

- [ ] **AC 8:** Given `rememberReduceMotion()` returns `true`, when chips arrive and complete, then: orbs appear without fade animation, no sonar pulses fire, orbital ring and spokes render statically at full alpha once visible (no animation).

- [ ] **AC 9:** Given an outer orb with `chip.isNumber = true` and `chip.numberValue = 47`, when the orb first renders, then the sublabel count-up animates from 0 to 47 over ~400ms.

- [ ] **AC 10:** Given 3 outer chips, when rendered, then the 3 orbs are placed at 120° intervals starting at the top (−90° / 12 o'clock), in clockwise order.

- [ ] **AC 11:** Given a device with screen width 360dp, when ring is sized, then `ringDiameterDp = 360 * 0.82 = 295.2dp` (below 300dp cap, so adaptive formula applies).

- [ ] **AC 12:** Given `chip.isWow = true` on the HISTORY chip, when the center orb renders, then a continuous slow glow pulse animation plays on the orb's shadow/border using `GalaxyHistory` color.

- [ ] **AC 13:** Given an outer orb where `chip.detail.isBlank()`, when rendered, then the orb has no click ripple and tapping does nothing.

- [ ] **AC 14:** Given `computeOrbPositions(n = 5, radiusPx = 100f)`, when called, then it returns 5 offsets each with magnitude ≈ 100f (within 0.01f tolerance), evenly spaced at 72° intervals, first offset pointing up (negative Y).

## Additional Context

### Dependencies

No new Gradle dependencies. `compose-foundation` (already declared in `build.gradle.kts`) provides `Canvas {}`, `DrawScope`, and `PathEffect`. Time-of-day uses `expect/actual` — no `kotlinx-datetime` added.

### Testing Strategy

**Unit tests (Task 7 — `RadialGalaxyTest.kt`):**
- `computeOrbPositions` — 5 test cases covering: single orb, two orbs, count, angular spacing, all-on-circle magnitude

**Manual device testing after implementation:**
1. Run `./gradlew :composeApp:installDebug && adb shell am start -n com.areadiscovery.debug/com.areadiscovery.MainActivity`
2. Open app → observe galaxy render with current time-of-day background
3. Pull-to-refresh → watch chips stream in one by one — verify each sonar pulse, orbital ring progressive reveal
4. Wait for complete — verify triple sonar burst
5. Tap HISTORY orb — verify era sheet
6. Tap outer orb with detail — verify detail sheet
7. Tap outer orb without detail — verify no sheet / no ripple
8. Test with a number chip (COST bucket) — verify count-up
9. Enable Android Accessibility > Remove Animations — retest streaming (orbs appear instantly, no sonar)

### Notes

- **Pre-mortem / high-risk items:**
  - `mutableStateListOf<SonarRingState>()` with `Animatable` inside — mutations to `Animatable` won't auto-trigger Canvas redraws. Ensure Canvas reads `sonarRings` state in its content lambda so recomposition triggers redraw. If Canvas doesn't redraw, wrap sonar ring draw reads in explicit state reads.
  - `BoxWithConstraints` in `commonMain` — ensure `contentAlignment = Alignment.Center` propagates correctly; outer orb offsets are relative to center so the math only works if the Box is center-aligned.
  - `PathEffect.dashPathEffect` — verify it's available in CMP commonMain (it's in `androidx.compose.ui.graphics`, which is part of `compose-ui` already in deps).
  - iOS `NSCalendar` actual — verify the `platform.Foundation` import resolves correctly in iosMain; if not, fall back to `0` (night) as safe default.

- **ChipCloud.kt is NOT deleted** — leave it in place. The call-site swap in `ChipDashboard` is the only change to SummaryScreen. ChipCloud can be deleted in a cleanup pass after the new component is validated on device.
- **MiniTimelineStrip.kt is NOT modified.**
- Future: if bucket count ever changes beyond 6, the N-point polygon layout handles it automatically. The only thing that would need updating is `BucketTypeMapper.galaxyAccentColor()` for any new `BucketType` values.
