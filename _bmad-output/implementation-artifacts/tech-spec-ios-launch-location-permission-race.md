---
title: 'Fix iOS Initial Launch Location Permission Race Condition'
slug: 'ios-launch-location-permission-race'
created: '2026-03-10'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Coroutines', 'CoreLocation (iOS)', 'Koin']
files_to_modify:
  - 'composeApp/src/iosMain/kotlin/com/harazone/location/IosLocationProvider.kt'
code_patterns: ['suspendCancellableCoroutine', 'CLLocationManagerDelegateProtocol', 'Result<T>']
test_patterns: ['Given/When/Then', 'ViewModel unit test with fake LocationProvider']
---

# Tech-Spec: Fix iOS Initial Launch Location Permission Race Condition

**Created:** 2026-03-10

## Overview

### Problem Statement

On iOS, the app always shows an error state + retry button on first launch. Tapping Retry works fine and the map loads correctly. The bug is a race condition in `IosLocationProvider.getCurrentLocation()`: it calls `requestWhenInUseAuthorization()` and `startUpdatingLocation()` back-to-back. On first launch with authorization status `.notDetermined`, iOS immediately fires `locationManager:didFailWithError:` because location updates were started before the user responded to the permission dialog. This causes the coroutine to resume with `Result.failure`, setting `MapUiState.LocationFailed`. Android is unaffected because `MainActivity` gates `App()` rendering behind a `permissionResolved` flag — the ViewModel is never created until permission is already decided.

### Solution

Fix `IosLocationProvider.getCurrentLocation()` to properly sequence authorization → location updates using a two-phase approach: (1) add an instance-level `activeManager` strong ref alongside `activeDelegate` to prevent CLLocationManager GC during auth dialog wait, (2) use `manager.authorizationStatus` (iOS 14+ instance property) instead of the deprecated static method, (3) add a `locationUpdateStarted` boolean flag to prevent double `startUpdatingLocation()` calls (on iOS 14+, `locationManagerDidChangeAuthorization` fires synchronously on `manager.delegate = delegate` for already-authorized users, so both the callback and the subsequent `when` block would otherwise both call `startUpdatingLocation()`), (4) add `cont.isCompleted` guard in `didFailWithError` to prevent double-resume when permission is denied (iOS fires both the auth callback AND `didFailWithError` on denial).

### Scope

**In Scope:**
- Fix `IosLocationProvider.kt` to properly sequence authorization → location updates
- Add regression test in `MapViewModel` tests using a fake `LocationProvider` that simulates the "delay then succeed" pattern

**Out of Scope:**
- Android location flow (unaffected)
- iOS permission UI customization (usage description strings already set)
- Timeout tuning (10s is adequate for the prompt-respond flow once race is fixed)
- Matching Android's permission pre-check gate pattern in Swift (not needed once IosLocationProvider is fixed)

## Context for Development

### Codebase Patterns

- `IosLocationProvider` uses `suspendCancellableCoroutine` inside `withContext(Dispatchers.Main)` guarded by a `locationMutex`
- The delegate is stored as `activeDelegate: NSObject?` to prevent GC (CLLocationManager holds weak ref to delegate — this pattern must be preserved)
- `MapViewModel.loadLocation()` wraps the entire location+geocode call in `withTimeoutOrNull(10_000L)` — returning null from the timeout triggers `LocationFailed`
- `MapViewModel.retry()` cancels the current job, resets to `Loading`, and calls `loadLocation()` again
- Android gates `App()` rendering via `permissionResolved` state in `MainActivity` — iOS has no equivalent gate, `App()` renders immediately from `MainViewController`
- All platform-specific location code lives in `iosMain` — no Swift changes needed for this fix

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/iosMain/kotlin/com/harazone/location/IosLocationProvider.kt` | PRIMARY: fix goes here |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | `loadLocation()` at line 645, `retry()` at line 639, timeout const at line 817 |
| `composeApp/src/androidMain/kotlin/com/harazone/MainActivity.kt` | Reference for Android's permission-gate pattern (lines 41–49) |
| `composeApp/src/iosMain/kotlin/com/harazone/MainViewController.kt` | Entry point — calls `App()` immediately, no permission gate |

### Technical Decisions

- Fix is entirely in `IosLocationProvider.kt` (iosMain) — no Swift, no Android, no ViewModel changes
- Use `locationManagerDidChangeAuthorization` (iOS 14+) for the auth callback. The project targets iOS 14+ based on MapLibre usage
- Keep `locationMutex` pattern — concurrent calls still need serialization
- Keep `activeDelegate` strong-ref pattern — critical to prevent delegate GC mid-callback (CLLocationManager holds only a weak ref to its delegate)
- Add `activeManager: CLLocationManager?` instance property alongside `activeDelegate` — the manager itself is a local `val` inside the coroutine and Kotlin/Native ARC will GC it while suspended waiting for the auth dialog unless we hold a strong ref (F2)
- Use `manager.authorizationStatus` (iOS 14+ instance property) not `CLLocationManager.authorizationStatus()` (deprecated static) — the static was deprecated in iOS 14 in favour of the instance property (F3)
- Add `var locationUpdateStarted = false` local flag captured in the delegate — on iOS 14+, assigning `manager.delegate = delegate` triggers `locationManagerDidChangeAuthorization` synchronously for already-authorized users; without this flag the `when` block that follows would call `startUpdatingLocation()` a second time (F1/F4)
- Add `if (cont.isCompleted) return` guard in `didFailWithError` — when user denies permission, iOS fires both `locationManagerDidChangeAuthorization` AND `didFailWithError`; without the guard the second callback double-resumes and crashes (F6)

## Implementation Plan

### Tasks

**Task 1 — Rewrite `IosLocationProvider.getCurrentLocation()` to gate on authorization**

File: `composeApp/src/iosMain/kotlin/com/harazone/location/IosLocationProvider.kt`

1. Add `private var activeManager: CLLocationManager? = null` alongside the existing `activeDelegate` property.

2. Replace the body of `getCurrentLocation()` with the following implementation. Read every comment — they explain the non-obvious iOS behaviors being guarded against:

```kotlin
@OptIn(ExperimentalForeignApi::class)
override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
    locationMutex.withLock {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val manager = CLLocationManager()
                manager.desiredAccuracy = kCLLocationAccuracyBest
                activeManager = manager  // F2: prevent ARC from GC-ing manager during auth dialog wait

                // F1/F4: On iOS 14+, assigning manager.delegate triggers locationManagerDidChangeAuthorization
                // synchronously if status is already determined. This flag prevents double startUpdatingLocation().
                var locationUpdateStarted = false

                val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {

                    // iOS 14+ callback — fires immediately on delegate assignment if status already determined
                    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                        if (cont.isCompleted) return
                        when (manager.authorizationStatus) {  // F3: instance property, not deprecated static
                            CLAuthorizationStatus.kCLAuthorizationStatusAuthorizedWhenInUse,
                            CLAuthorizationStatus.kCLAuthorizationStatusAuthorizedAlways -> {
                                if (!locationUpdateStarted) {  // F1/F4: guard double start
                                    locationUpdateStarted = true
                                    manager.startUpdatingLocation()
                                }
                            }
                            CLAuthorizationStatus.kCLAuthorizationStatusDenied,
                            CLAuthorizationStatus.kCLAuthorizationStatusRestricted -> {
                                activeDelegate = null
                                activeManager = null
                                cont.resume(Result.failure(Exception("Location permission denied")))
                            }
                            else -> { /* notDetermined — wait for user to respond to dialog */ }
                        }
                    }

                    override fun locationManager(
                        manager: CLLocationManager,
                        didUpdateLocations: List<*>
                    ) {
                        val location = didUpdateLocations.firstOrNull() as? CLLocation
                        if (location != null) {
                            manager.stopUpdatingLocation()
                            activeDelegate = null
                            activeManager = null
                            cont.resume(Result.success(
                                GpsCoordinates(
                                    latitude = location.coordinate.useContents { latitude },
                                    longitude = location.coordinate.useContents { longitude }
                                )
                            ))
                        }
                    }

                    override fun locationManager(
                        manager: CLLocationManager,
                        didFailWithError: platform.Foundation.NSError
                    ) {
                        if (cont.isCompleted) return  // F6: iOS fires both auth callback AND didFailWithError on denial
                        manager.stopUpdatingLocation()
                        activeDelegate = null
                        activeManager = null
                        AppLogger.e { "CLLocationManager failed: ${didFailWithError.localizedDescription}" }
                        cont.resume(Result.failure(Exception(didFailWithError.localizedDescription)))
                    }
                }

                activeDelegate = delegate
                // F1/F4: This assignment may trigger locationManagerDidChangeAuthorization synchronously
                // (iOS 14+ fires it immediately when status is already determined).
                // locationUpdateStarted guards against the when-block below also calling startUpdatingLocation.
                manager.delegate = delegate

                // Secondary gate — runs after delegate assignment.
                // If locationManagerDidChangeAuthorization already ran synchronously (authorized case),
                // locationUpdateStarted will be true and the authorized branch is skipped.
                if (!locationUpdateStarted) {
                    when (manager.authorizationStatus) {  // F3: instance property
                        CLAuthorizationStatus.kCLAuthorizationStatusAuthorizedWhenInUse,
                        CLAuthorizationStatus.kCLAuthorizationStatusAuthorizedAlways -> {
                            // Callback did NOT fire synchronously — start directly
                            locationUpdateStarted = true
                            manager.startUpdatingLocation()
                        }
                        CLAuthorizationStatus.kCLAuthorizationStatusNotDetermined -> {
                            // Show permission dialog. locationManagerDidChangeAuthorization will
                            // call startUpdatingLocation() once user responds.
                            manager.requestWhenInUseAuthorization()
                        }
                        else -> {
                            // Denied or restricted — fast fail without waiting for timeout
                            if (!cont.isCompleted) {
                                activeDelegate = null
                                activeManager = null
                                cont.resume(Result.failure(Exception("Location permission denied")))
                            }
                        }
                    }
                }

                cont.invokeOnCancellation {
                    manager.stopUpdatingLocation()
                    activeDelegate = null
                    activeManager = null
                }
            }
        }
    }
```

Key changes from current code:
- Add `activeManager` instance property to hold strong ref to the CLLocationManager during auth dialog wait (F2)
- Add `locationUpdateStarted` flag to prevent double `startUpdatingLocation()` (F1/F4)
- Add `locationManagerDidChangeAuthorization` override to handle async authorization response
- Replace unconditional `requestWhenInUseAuthorization() + startUpdatingLocation()` with gated `when` block on `manager.authorizationStatus` (instance property, not deprecated static) (F3)
- Add `if (cont.isCompleted) return` guard in `didFailWithError` (F6)
- Clear `activeManager = null` in all exit paths (update callbacks, cancellation, failure)

**Task 2 — Add regression test**

File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt`

Add test `iosFirstLaunch_delayedLocation_doesNotShowErrorState` to the existing `MapViewModelTest` class. Use a `StandardTestDispatcher` (not `UnconfinedTestDispatcher`) for this test so that `delay()` inside the fake can be advanced with `advanceTimeBy()` (F5 — `UnconfinedTestDispatcher` does not advance virtual time for `delay()`).

```kotlin
@Test
fun iosFirstLaunch_delayedLocation_doesNotShowErrorState() = runTest {
    // Simulates: permission dialog shown, user takes 3 seconds to grant, then location arrives
    val delayedProvider = object : LocationProvider {
        override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
            delay(3_000)
            return Result.success(GpsCoordinates(51.5074, -0.1278))
        }
        override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
            Result.success("Test Area")
    }
    val vm = createViewModel(locationProvider = delayedProvider)

    // Before delay resolves: should be Loading, never LocationFailed
    assertIs<MapUiState.Loading>(vm.uiState.value)

    // Advance past the 3s delay + geocode
    advanceTimeBy(5_000)

    // Should reach Ready, not LocationFailed
    assertIs<MapUiState.Ready>(vm.uiState.value)
}
```

Note: `runTest` uses a `TestCoroutineScheduler` by default and controls virtual time. The `advanceTimeBy(5_000)` moves the virtual clock past the 3s delay. This guards against regressions where a delayed location is incorrectly treated as a timeout failure.

### Acceptance Criteria

**AC1 — Happy path (first launch, permission granted)**
- Given: iOS app is freshly installed (permission status = `.notDetermined`)
- When: App launches and the system permission dialog appears
- Then: The app shows a loading state (not error state) while waiting for permission
- And: After the user grants permission, the map loads successfully without any retry

**AC2 — Happy path (returning user, already authorized)**
- Given: iOS app has location permission already granted (`.authorizedWhenInUse`)
- When: App launches
- Then: Map loads immediately — no error state, no retry button

**AC3 — Permission denied**
- Given: iOS app has location permission denied (`.denied`)
- When: App launches
- Then: App shows `LocationFailed` error state promptly — without waiting for the 10s timeout to expire
- And: Error message reads "Can't find your location. Please try again."

**AC4 — Retry still works**
- Given: App shows `LocationFailed` for any reason
- When: User taps Retry
- Then: App attempts location fetch again and succeeds if permission is now granted

**AC5 — Android unaffected**
- Given: Android build on any device
- When: App launches (first or returning)
- Then: Behavior is identical to pre-fix (no regression)
- Automated guard: existing `AppLaunchSmokeTest` (`./gradlew :composeApp:connectedDebugAndroidTest`) must pass

**AC6 — Regression test passes**
- Given: `MapViewModelTest` with `DelayedSuccessLocationProvider`
- When: `loadLocation()` is called
- Then: `uiState` eventually becomes `MapUiState.Ready`, never `MapUiState.LocationFailed`

## Additional Context

### Dependencies

- No new library dependencies required
- `CLAuthorizationStatus` constants are already available via `platform.CoreLocation.*`
- `locationManagerDidChangeAuthorization` is iOS 14+ API — matches existing project minimum target

### Testing Strategy

- **Unit test** (Task 2): Add to existing `MapViewModelTest.kt` — infra already in place, `createViewModel()` helper accepts custom `locationProvider`
- `FakeLocationProvider` in `fakes/` takes a pre-configured `Result` (no delay support) — regression test should create an inline fake inside the test function using `delay()` + `Result.success`, OR add a `delayMs` constructor param to `FakeLocationProvider`
- A `ResettableFakeLocationProvider` already exists in `MapViewModelTest.kt` (line 80) — check if it can be reused or extended for the delay case
- **Manual test** (iOS Simulator): Delete app → reinstall → launch → confirm no error state on first load
- **Manual test** (iOS Simulator): Revoke permission in Settings → launch → confirm fast failure
- **Manual test** (Android device): Full regression pass — confirm no behavior change

### Notes

- The `cont.isCompleted` guard in `locationManagerDidChangeAuthorization` handles an edge case where cancellation fires at the same time as the auth callback — prevents `IllegalStateException: Already resumed`
- The `locationMutex` prevents concurrent `getCurrentLocation()` calls (e.g. if `returnToCurrentLocation` triggers while `loadLocation` is mid-flight). This is unchanged.
- Do NOT increase `LOCATION_TIMEOUT_MS` — 10s is plenty for the user to respond to the permission dialog. The fix makes the app wait correctly instead of failing early.
- If Apple ever runs the `locationManagerDidChangeAuthorization` callback on a non-Main thread, the `withContext(Dispatchers.Main)` wrapper still ensures the `cont.resume` call is safe.
- BEFORE implementing: verify iOS deployment target in `iosApp/iosApp.xcodeproj` is iOS 14+. `locationManagerDidChangeAuthorization` is iOS 14+ only — on iOS 13 it will never fire and the fix will silently hang (F13). If target is iOS 13, use the legacy `locationManager:didChangeAuthorizationStatus:` signature instead.
- The unit test (Task 2) does NOT test iOS CoreLocation behavior — it only guards the ViewModel's response to a delayed location result. Manual iOS Simulator testing is the primary verification for the CoreLocation sequencing fix (F10).
