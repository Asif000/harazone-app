# iOS Bugs: 3-Pin Batching + Stale Vibes

Created: 2026-03-14
Priority: HIGH
Status: INVESTIGATION COMPLETE, ready for fix

## Bug 1: iOS shows more than 3 pins
## Bug 2: Batch navigation (1/3) broken on iOS
## Bug 3: Dynamic vibes show old stale vibes on iOS

## Root Cause Analysis

All three bugs likely share one root cause: ViewModel StateFlow updates not propagating to iOS Compose UI.

### Batching (Bugs 1 & 2)

The 3-pin batching logic is in shared ViewModel code (commonMain) and works on Android:
- `onNextBatch()` / `onPrevBatch()` in MapViewModel correctly update `state.pois` with the batch subset
- `MapScreen.kt` passes `state.pois` to MapComposable
- `MapComposable.ios.kt` renders ALL pois it receives (no iOS-specific filtering)
- Therefore: if iOS shows >3 pins, `state.pois` is not being updated on iOS

### Stale Vibes (Bug 3)

- `currentDynamicVibes` is a mutable variable cached after first `BucketUpdate.VibesReady`
- Returning to same area doesn't force fresh vibe fetch
- `buildChipRowVibes()` relies on this cached variable
- Shared code issue but more visible on iOS (same test area)

## Investigation Paths

1. Check `collectAsStateWithLifecycle()` vs `collectAsState()` on iOS — lifecycle-aware collection may behave differently
2. Check if iOS recomposition triggers when `pois` list reference changes but content is similar
3. Check if `poiBatchesCache` is populated on iOS (may be empty if area fetch flow differs)
4. Vibe cache: check if `AreaRepositoryImpl` cache-hit path emits `VibesReady` or skips it on iOS
5. Check `LaunchedEffect(pois, ...)` in MapComposable.ios.kt — does it fire when pois changes?

## Key Files

- `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` — batching logic, `onNextBatch()`, `onPrevBatch()`, `currentDynamicVibes`
- `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` — passes `state.pois` to MapComposable
- `composeApp/src/iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt` — renders pois, LaunchedEffect deps
- `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` — batch state fields
- `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt` — cache-hit path

## Fix Requirements

- Fix must include iOS-specific regression tests
- Test on both fresh install and relaunch scenarios
- Verify vibes refresh on area change
