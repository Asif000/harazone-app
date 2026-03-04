# Story 2.1: Location Service & Privacy Pipeline

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user**,
I want the app to know what area I'm in without sending my exact GPS coordinates to any external service,
So that I get area-relevant content while my precise location stays private.

## Acceptance Criteria

1. **Given** the user grants fine location permission, **When** the app detects the device location, **Then** `LocationProvider` (interface with platform `actual` implementations) uses Android `FusedLocationProviderClient` to get current coordinates
2. **And** coordinates are passed to `PrivacyPipeline` (in `domain/service/`) which reverse geocodes via Android `Geocoder` to extract locality/sublocality area name
3. **And** only the area name string (e.g., "Alfama, Lisbon") is ever passed beyond the privacy pipeline — raw GPS coordinates never leave `PrivacyPipeline` [NFR11]
4. **And** `PrivacyPipeline` is the SINGLE enforcement point for the privacy rule — it owns GPS coordinates and disposes them after geocoding
5. **And** unit tests in `commonTest` verify `PrivacyPipeline` strips coordinates and outputs only area name strings
6. **And** if reverse geocoding fails, a fallback extracts the best available area name from the geocode result (e.g., `adminArea` if `locality` is null)
7. **And** all three build gates pass: `assembleDebug`, `allTests`, `lint`

**Note on AC4 (geofencing / significant location change):** The epics file AC mentions "significant location change detection uses geofencing APIs." For Story 2.1, implement a one-shot location request (current location) — continuous monitoring and significant location change detection are integration concerns deferred to Story 2.5 (Live Summary Screen Integration) when the ViewModel wires live location. Don't over-engineer now.

## Tasks / Subtasks

- [x] Task 1: Add Android location permissions to AndroidManifest.xml (AC: #1)
  - [x] 1.1 Open `composeApp/src/androidMain/AndroidManifest.xml` (or top-level manifest)
  - [x] 1.2 Add `<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />` and `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />` inside `<manifest>`
  - [x] 1.3 Verify manifest is at `composeApp/src/androidMain/AndroidManifest.xml` — if not, check `composeApp/src/main/AndroidManifest.xml` which is standard for KMP projects using KMP Wizard

- [x] Task 2: Add iOS location permission to Info.plist (AC: #1)
  - [x] 2.1 Open `iosApp/iosApp/Info.plist`
  - [x] 2.2 Add key `NSLocationWhenInUseUsageDescription` with value: `"AreaDiscovery uses your location to show you a portrait of the area you're in."`
  - [x] 2.3 Do NOT add `NSLocationAlwaysUsageDescription` — only when-in-use permission is needed for Phase 1a

- [x] Task 3: Create `GpsCoordinates` model and `LocationProvider` interface in `commonMain` (AC: #1, #3)
  - [x] 3.1 Create `commonMain/kotlin/com/areadiscovery/location/GpsCoordinates.kt`:
    ```kotlin
    package com.areadiscovery.location

    data class GpsCoordinates(
        val latitude: Double,
        val longitude: Double
    )
    ```
  - [x] 3.2 Create `commonMain/kotlin/com/areadiscovery/location/LocationProvider.kt`:
    ```kotlin
    package com.areadiscovery.location

    interface LocationProvider {
        /**
         * Returns the current device GPS coordinates.
         * Suspending — waits for a location fix.
         */
        suspend fun getCurrentLocation(): Result<GpsCoordinates>

        /**
         * Reverse geocodes the given coordinates to a human-readable area name
         * (e.g., "Alfama, Lisbon"). Returns a fallback area name if full geocoding fails.
         *
         * IMPORTANT: This is an internal function — callers should NEVER store
         * the coordinates passed here. Use [PrivacyPipeline] as the sole entry point.
         */
        suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String>
    }
    ```
  - [x] 3.3 Note: Use interface pattern (not `expect class`) — consistent with `AnalyticsTracker` established in Story 1.7

- [x] Task 4: Create `AndroidLocationProvider` using FusedLocationProviderClient + Geocoder (AC: #1, #6)
  - [x] 4.1 Create `androidMain/kotlin/com/areadiscovery/location/AndroidLocationProvider.kt`:
    ```kotlin
    package com.areadiscovery.location

    import android.content.Context
    import android.location.Geocoder
    import android.os.Build
    import com.google.android.gms.location.LocationServices
    import com.google.android.gms.location.Priority
    import com.areadiscovery.util.AppLogger
    import kotlinx.coroutines.suspendCancellableCoroutine
    import kotlin.coroutines.resume

    class AndroidLocationProvider(private val context: Context) : LocationProvider {

        private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        @Throws(SecurityException::class)
        override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
            suspendCancellableCoroutine { cont ->
                try {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                cont.resume(Result.success(GpsCoordinates(location.latitude, location.longitude)))
                            } else {
                                cont.resume(Result.failure(Exception("Location unavailable — null result from FusedLocationProvider")))
                            }
                        }
                        .addOnFailureListener { e ->
                            AppLogger.e(e) { "FusedLocationProvider failed" }
                            cont.resume(Result.failure(e))
                        }
                } catch (e: SecurityException) {
                    AppLogger.e(e) { "Location permission not granted" }
                    cont.resume(Result.failure(e))
                }
            }

        override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
            runCatching {
                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+ async geocoder
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            cont.resume(extractAreaName(addresses))
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    extractAreaName(addresses ?: emptyList())
                }
            }

        private fun extractAreaName(addresses: List<android.location.Address>): String {
            val address = addresses.firstOrNull()
                ?: return "Unknown area"
            // Prefer subLocality (neighborhood) → locality (city) → adminArea (state/region)
            return listOfNotNull(
                address.subLocality,
                address.locality,
                address.adminArea
            ).joinToString(", ").ifBlank { "Unknown area" }
        }
    }
    ```
  - [x] 4.2 **CRITICAL**: `play-services-location` dependency MUST be added to `androidMain.dependencies` in `composeApp/build.gradle.kts`:
    ```kotlin
    implementation("com.google.android.gms:play-services-location:21.3.0")  // verify latest
    ```
    Also add to `gradle/libs.versions.toml`:
    ```toml
    # [versions]
    play-services-location = "21.3.0"
    # [libraries]
    play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "play-services-location" }
    ```
    Then reference via `libs.play.services.location` in build.gradle.kts
  - [x] 4.3 Note: `FusedLocationProviderClient.getCurrentLocation()` requires `ACCESS_FINE_LOCATION` permission to already be granted at runtime — permission request UI is NOT in scope for Story 2.1 (see Story 5.2). If permission is denied, `SecurityException` is caught and returned as `Result.failure`.

- [x] Task 5: Create `IosLocationProvider` using CLLocationManager + CLGeocoder (AC: #1, #6)
  - [x] 5.1 Create `iosMain/kotlin/com/areadiscovery/location/IosLocationProvider.kt`:
    ```kotlin
    package com.areadiscovery.location

    import com.areadiscovery.util.AppLogger
    import kotlinx.coroutines.suspendCancellableCoroutine
    import platform.CoreLocation.CLGeocoder
    import platform.CoreLocation.CLLocation
    import platform.CoreLocation.CLLocationManager
    import platform.CoreLocation.CLLocationManagerDelegateProtocol
    import platform.CoreLocation.kCLLocationAccuracyBest
    import platform.darwin.NSObject
    import kotlin.coroutines.resume

    class IosLocationProvider : LocationProvider {

        override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
            suspendCancellableCoroutine { cont ->
                val manager = CLLocationManager()
                manager.desiredAccuracy = kCLLocationAccuracyBest

                val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                    override fun locationManager(
                        manager: CLLocationManager,
                        didUpdateLocations: List<*>
                    ) {
                        val location = didUpdateLocations.firstOrNull() as? CLLocation
                        if (location != null) {
                            manager.stopUpdatingLocation()
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
                        manager.stopUpdatingLocation()
                        AppLogger.e { "CLLocationManager failed: ${didFailWithError.localizedDescription}" }
                        cont.resume(Result.failure(Exception(didFailWithError.localizedDescription)))
                    }
                }

                manager.delegate = delegate
                manager.requestWhenInUseAuthorization()
                manager.startUpdatingLocation()

                cont.invokeOnCancellation { manager.stopUpdatingLocation() }
            }

        override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
            suspendCancellableCoroutine { cont ->
                val geocoder = CLGeocoder()
                val location = CLLocation(latitude, longitude)
                geocoder.reverseGeocodeLocation(location) { placemarks, error ->
                    if (error != null) {
                        AppLogger.e { "CLGeocoder failed: ${error.localizedDescription}" }
                        cont.resume(Result.failure(Exception(error.localizedDescription)))
                    } else {
                        val placemark = (placemarks?.firstOrNull() as? platform.CoreLocation.CLPlacemark)
                        val areaName = listOfNotNull(
                            placemark?.subLocality,
                            placemark?.locality,
                            placemark?.administrativeArea
                        ).joinToString(", ").ifBlank { "Unknown area" }
                        cont.resume(Result.success(areaName))
                    }
                }
            }
    }
    ```
  - [x] 5.2 iOS CocoaPods / Kotlin/Native note: `platform.CoreLocation.*` is available in KMP's iosMain via Kotlin/Native's automatic iOS framework bridging — **no additional dependencies needed** beyond standard KMP iOS target setup. This is different from Firebase (which needs CocoaPods bindings).

- [x] Task 6: Create `PrivacyPipeline` in `domain/service/` (AC: #2, #3, #4, #6)
  - [x] 6.1 Create directory `commonMain/kotlin/com/areadiscovery/domain/service/`
  - [x] 6.2 Create `commonMain/kotlin/com/areadiscovery/domain/service/PrivacyPipeline.kt`:
    ```kotlin
    package com.areadiscovery.domain.service

    import com.areadiscovery.location.LocationProvider
    import com.areadiscovery.util.AppLogger

    /**
     * Enforces the location privacy policy (NFR11):
     * GPS coordinates NEVER leave this class.
     * Only the resolved area name string is returned to callers.
     *
     * This is the single architectural enforcement point — not a convention.
     */
    class PrivacyPipeline(private val locationProvider: LocationProvider) {

        /**
         * Resolves the current area name from GPS without exposing raw coordinates to callers.
         * Returns a [Result] containing either the area name string or a failure.
         */
        suspend fun resolveAreaName(): Result<String> {
            val coordsResult = locationProvider.getCurrentLocation()
            if (coordsResult.isFailure) {
                AppLogger.e { "PrivacyPipeline: location unavailable — ${coordsResult.exceptionOrNull()?.message}" }
                return Result.failure(coordsResult.exceptionOrNull()!!)
            }

            val coords = coordsResult.getOrThrow()
            // Coordinates are used here and NEVER passed to any other component
            AppLogger.d { "PrivacyPipeline: resolving area name (coordinates withheld from log)" }

            val areaNameResult = locationProvider.reverseGeocode(coords.latitude, coords.longitude)
            return areaNameResult.also { result ->
                if (result.isSuccess) {
                    AppLogger.d { "PrivacyPipeline resolved area: ${result.getOrThrow()}" }
                } else {
                    AppLogger.e { "PrivacyPipeline: reverse geocoding failed — ${result.exceptionOrNull()?.message}" }
                }
            }
        }
    }
    ```
  - [x] 6.3 **CRITICAL**: `PrivacyPipeline` is a pure Kotlin class in `domain/service/` — NO android.* or iOS imports. Platform details are entirely inside LocationProvider implementations.
  - [x] 6.4 Verify layer boundaries: `PrivacyPipeline` is in `domain/service/` and may only import from `domain/` and `location/` (which is a platform-abstracted package — allowed since LocationProvider is an interface). Do NOT import `data/` or `ui/` from domain service.

- [x] Task 7: Wire `LocationProvider` and `PrivacyPipeline` via Koin (AC: #1, #2)
  - [x] 7.1 Update `androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt` to add `LocationProvider`:
    ```kotlin
    package com.areadiscovery.di

    import com.areadiscovery.location.AndroidLocationProvider
    import com.areadiscovery.location.LocationProvider
    import com.areadiscovery.util.AnalyticsTracker
    import com.areadiscovery.util.AndroidAnalyticsTracker
    import org.koin.android.ext.koin.androidContext
    import org.koin.dsl.module

    actual fun platformModule() = module {
        single<AnalyticsTracker> { AndroidAnalyticsTracker() }
        single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    }
    ```
  - [x] 7.2 Update `iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt` to add `LocationProvider`:
    ```kotlin
    package com.areadiscovery.di

    import com.areadiscovery.location.IosLocationProvider
    import com.areadiscovery.location.LocationProvider
    import com.areadiscovery.util.AnalyticsTracker
    import com.areadiscovery.util.IosAnalyticsTracker
    import org.koin.dsl.module

    actual fun platformModule() = module {
        single<AnalyticsTracker> { IosAnalyticsTracker() }
        single<LocationProvider> { IosLocationProvider() }
    }
    ```
  - [x] 7.3 Add `PrivacyPipeline` to `commonMain/kotlin/com/areadiscovery/di/DataModule.kt`:
    ```kotlin
    // Add to existing dataModule:
    single { PrivacyPipeline(get()) }
    ```
    Import: `import com.areadiscovery.domain.service.PrivacyPipeline`
  - [x] 7.4 Verify `appModule()` in `AppModule.kt` already includes `dataModule` and `platformModule()` — no changes needed there.

- [x] Task 8: Create `FakeLocationProvider` for testing (AC: #5)
  - [x] 8.1 Create `commonTest/kotlin/com/areadiscovery/fakes/FakeLocationProvider.kt`:
    ```kotlin
    package com.areadiscovery.fakes

    import com.areadiscovery.location.GpsCoordinates
    import com.areadiscovery.location.LocationProvider

    class FakeLocationProvider(
        private val locationResult: Result<GpsCoordinates> = Result.success(GpsCoordinates(38.7139, -9.1394)),  // Alfama, Lisbon coords
        private val geocodeResult: Result<String> = Result.success("Alfama, Lisbon")
    ) : LocationProvider {

        var locationCallCount = 0
        var geocodeCallCount = 0
        // Last coordinates passed to reverseGeocode (for assertion purposes only)
        var lastGeocodedCoords: Pair<Double, Double>? = null

        override suspend fun getCurrentLocation(): Result<GpsCoordinates> {
            locationCallCount++
            return locationResult
        }

        override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> {
            geocodeCallCount++
            lastGeocodedCoords = latitude to longitude
            return geocodeResult
        }
    }
    ```

- [x] Task 9: Write `PrivacyPipelineTest` in `commonTest` (AC: #5, #6)
  - [x] 9.1 Create `commonTest/kotlin/com/areadiscovery/domain/service/PrivacyPipelineTest.kt`:
    ```kotlin
    package com.areadiscovery.domain.service

    import com.areadiscovery.fakes.FakeLocationProvider
    import com.areadiscovery.location.GpsCoordinates
    import kotlinx.coroutines.test.runTest
    import kotlin.test.Test
    import kotlin.test.assertEquals
    import kotlin.test.assertTrue

    class PrivacyPipelineTest {

        @Test
        fun `resolveAreaName returns area name string - not raw coordinates`() = runTest {
            val fakeProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(38.7139, -9.1394)),
                geocodeResult = Result.success("Alfama, Lisbon")
            )
            val pipeline = PrivacyPipeline(fakeProvider)

            val result = pipeline.resolveAreaName()

            assertTrue(result.isSuccess)
            assertEquals("Alfama, Lisbon", result.getOrThrow())
            // Verify the result is a string (area name), not coordinates
            assertTrue(!result.getOrThrow().contains("38.7"))
            assertTrue(!result.getOrThrow().contains("-9.1"))
        }

        @Test
        fun `resolveAreaName returns failure when location unavailable`() = runTest {
            val fakeProvider = FakeLocationProvider(
                locationResult = Result.failure(Exception("Permission denied"))
            )
            val pipeline = PrivacyPipeline(fakeProvider)

            val result = pipeline.resolveAreaName()

            assertTrue(result.isFailure)
            assertEquals("Permission denied", result.exceptionOrNull()?.message)
        }

        @Test
        fun `resolveAreaName returns failure when reverse geocoding fails`() = runTest {
            val fakeProvider = FakeLocationProvider(
                locationResult = Result.success(GpsCoordinates(0.0, 0.0)),
                geocodeResult = Result.failure(Exception("Geocoder unavailable"))
            )
            val pipeline = PrivacyPipeline(fakeProvider)

            val result = pipeline.resolveAreaName()

            assertTrue(result.isFailure)
        }

        @Test
        fun `resolveAreaName calls location then geocoder exactly once`() = runTest {
            val fakeProvider = FakeLocationProvider()
            val pipeline = PrivacyPipeline(fakeProvider)

            pipeline.resolveAreaName()

            assertEquals(1, fakeProvider.locationCallCount)
            assertEquals(1, fakeProvider.geocodeCallCount)
        }
    }
    ```
  - [x] 9.2 Note: Use `kotlin.test` assertions only (`assertTrue`, `assertEquals`) — NOT `assert()` which fails on Kotlin/Native
  - [x] 9.3 Note: `runTest` from `kotlinx-coroutines-test` is already in the project (confirmed from Story 1.7 SummaryViewModelTest)

- [x] Task 10: Build verification (AC: #7)
  - [x] 10.1 Run `./gradlew :composeApp:assembleDebug` — PASS
  - [x] 10.2 Run `./gradlew :composeApp:allTests` — PASS (all PrivacyPipelineTest cases pass)
  - [x] 10.3 Run `./gradlew :composeApp:lint` — PASS

## Dev Notes

### Critical Architecture Rule: Privacy Pipeline as Sole GPS Enforcement Point

`PrivacyPipeline` is NOT just a utility class — it is an **architectural enforcement mechanism** for NFR11. The data flow is:

```
GPS Coordinates
    └─→ LocationProvider.getCurrentLocation() [androidMain/iosMain ONLY]
    └─→ LocationProvider.reverseGeocode() [called ONLY inside PrivacyPipeline]
    └─→ PrivacyPipeline.resolveAreaName(): Result<String>   ← GPS dies here
               │
               ▼
         area name: String  (e.g., "Alfama, Lisbon")
               │
               ▼
         [passes to Story 2.2 AI adapter, Story 2.3 AreaRepository, etc.]
```

**NEVER** pass `GpsCoordinates` out of `PrivacyPipeline`. Future stories (2.2, 2.3, 2.5) will receive only `String` area names.

---

### LocationProvider: Interface (Not expect/actual) Pattern

Following the same decision made in Story 1.7 for `AnalyticsTracker`:

- `LocationProvider` is a **Kotlin interface** in `commonMain/location/`
- `AndroidLocationProvider` and `IosLocationProvider` are platform implementations
- No `expect`/`actual` keywords needed — the interface serves the same purpose
- Benefits: fully testable via `FakeLocationProvider` in `commonTest`, consistent with project convention

Architecture file lists `LocationProvider.kt` as `expect: GPS + reverse geocode interface` — "expect" here means "defined in commonMain as the interface contract," not the Kotlin `expect` keyword.

---

### Android: FusedLocationProviderClient vs Legacy LocationManager

**Always use `FusedLocationProviderClient`** (Google Play Services). Never use `android.location.LocationManager` directly. Reasons:
- Battery-efficient (NFR6): respects device power state, uses existing location fixes
- `getCurrentLocation()` with `PRIORITY_HIGH_ACCURACY` gives a one-shot location result
- Available via `play-services-location` dependency

**Permission handling:** `AndroidLocationProvider.getCurrentLocation()` will throw `SecurityException` if location permission is not granted. This is caught and returned as `Result.failure`. The permission request UI flow is out of scope for Story 2.1 (covered in Story 5.2). For Phase 1a testing, grant permission manually.

**Coroutine bridging:** Use `suspendCancellableCoroutine` to bridge `Task<Location>` callbacks to Kotlin coroutines.

---

### Android: Geocoder API 33+ Async vs Legacy

`Geocoder.getFromLocation()` has two signatures:
- API < 33 (deprecated): synchronous, blocks thread → wrap in `withContext(Dispatchers.IO)` OR use as shown
- API ≥ 33 (Android 13+): async callback via `GeocodeListener`

The implementation in Task 4.1 handles both with a Build version check.

**Fallback strategy for geocoding failures:**
```
Priority: subLocality → locality → adminArea → "Unknown area"
```
Example results:
- Dense urban: "Alfama, Lisbon" (subLocality + locality)
- Suburb: "Belém, Lisbon"
- Rural: "Alentejo" (adminArea only)

---

### iOS: CoreLocation in KMP (No CocoaPods Needed)

Unlike Firebase (which requires CocoaPods bindings), CoreLocation and CLGeocoder are part of Apple's standard iOS frameworks, which Kotlin/Native automatically bridges in `iosMain`. You can use `platform.CoreLocation.*` without any Podfile changes.

**Note on CLLocationManager delegate:** In Kotlin/Native, implementing ObjC delegate protocols requires `NSObject()` superclass. The pattern in Task 5.1 is idiomatic KMP iOS delegate bridging.

**Info.plist is required:** If `NSLocationWhenInUseUsageDescription` is missing, iOS will silently deny location. The `iosApp/iosApp/Info.plist` must have this key.

---

### Koin Wiring: `androidContext()` in PlatformModule

`AndroidLocationProvider` needs a `Context`. Since `platformModule()` is registered in the Koin `KoinApplication` composable in `App.kt`, and the Android platform module uses `androidContext()`, verify that the Android Koin initialization includes `androidContext(this)`.

**Check `MainApplication.kt` or `MainActivity.kt` for:**
```kotlin
startKoin {
    androidContext(this@MainApplication)
    // OR
    androidContext(this@MainActivity)
    modules(appModule())
}
```

If `App.kt` uses `KoinApplication(application = { modules(appModule()) })` composable (as established in Story 1.6), this may NOT automatically provide `androidContext`. If `androidContext()` is missing, `AndroidLocationProvider(androidContext())` will fail at runtime.

**Solution if androidContext() is unavailable:** Pass `Context` through `MainActivity` and add it to Koin via `androidContext()` before the composable initializer. Check `MainActivity.kt` and `App.kt` to determine the current initialization pattern.

---

### DomainError: LocationError Already Defined

`DomainError.LocationError` already exists in `domain/model/DomainError.kt`:
```kotlin
data class LocationError(val message: String) : DomainError()
```

`PrivacyPipeline` uses `Result<String>` (kotlin.Result) not `DomainError` in its current design. If future stories need `DomainError` typed results, `PrivacyPipeline` can be updated then. For Story 2.1, `Result<String>` is sufficient and simpler.

---

### Story Scope: What NOT to Do

- **Do NOT** add a permission request dialog — that's Story 5.2
- **Do NOT** add continuous location updates or geofencing — that's Story 2.5 integration
- **Do NOT** connect `PrivacyPipeline` to `SummaryViewModel` yet — SummaryViewModel still uses `MockAreaIntelligenceProvider` (that connection happens in Story 2.5)
- **Do NOT** add `AreaRepository` — that's Story 2.3
- **Do NOT** add `ConnectivityMonitor` — that's Story 2.4
- **Do NOT** add `AreaContext` time/day enrichment — that's Story 2.5
- **Do NOT** implement `RetryHelper` — not needed for location service in Phase 1a scope
- **Do NOT** log raw GPS coordinates anywhere — not even in debug logs (privacy by design)

---

### Project Structure Notes

New files for this story:

```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── location/
│   ├── GpsCoordinates.kt                ← NEW (data class)
│   └── LocationProvider.kt              ← NEW (interface)
└── domain/
    └── service/
        └── PrivacyPipeline.kt           ← NEW (domain business rule)

composeApp/src/androidMain/kotlin/com/areadiscovery/
└── location/
    └── AndroidLocationProvider.kt       ← NEW (FusedLocationProvider + Geocoder)

composeApp/src/iosMain/kotlin/com/areadiscovery/
└── location/
    └── IosLocationProvider.kt           ← NEW (CLLocationManager + CLGeocoder)

composeApp/src/commonTest/kotlin/com/areadiscovery/
├── fakes/
│   └── FakeLocationProvider.kt          ← NEW
└── domain/
    └── service/
        └── PrivacyPipelineTest.kt       ← NEW

Modified files:
- composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt  ← add LocationProvider
- composeApp/src/iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt          ← add LocationProvider
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt                ← add PrivacyPipeline
- composeApp/build.gradle.kts                                                         ← add play-services-location
- gradle/libs.versions.toml                                                           ← add version entry
- iosApp/iosApp/Info.plist                                                            ← add NSLocationWhenInUseUsageDescription
- composeApp/src/androidMain/AndroidManifest.xml                                      ← add location permissions
```

---

### Previous Story (1.7) Key Learnings to Carry Forward

- `kotlin.test` assertions: `assertTrue()`, `assertEquals()` — NOT `assert()` (fails Kotlin/Native)
- All 3 build gates required: `assembleDebug`, `allTests`, `lint`
- Koin BOM `platform()` NOT supported in KMP — use explicit versions
- Firebase BOM explicit versions: `firebase-analytics:22.4.0`, `firebase-crashlytics:19.4.0`
- `AppLogger` is available at `com.areadiscovery.util.AppLogger` — use for all logging
- Interface pattern preferred over `expect class` — testable in `commonTest`
- `viewModelScope` + `Dispatchers.setMain(StandardTestDispatcher())` for ViewModel tests
- Library versions: **Kotlin 2.3.0**, CMP 1.10.0, AGP 8.11.2, **Koin 4.1.1**
- Koin initialized via `KoinApplication` composable in `App.kt`
- `expect fun platformModule()` pattern established in `di/AppModule.kt`
- `androidContext()` available in androidMain Koin platform module

---

### Git Intelligence

Recent commits:
```
0afb29b Add Firebase Analytics, Crashlytics, and platform DI wiring (Story 1.7)
75fad9b Add summary screen, navigation shell, Koin DI, and analytics foundation (Stories 1.6–1.7)
a16db36 Add streaming composables, state mapper, and UI components (Story 1.5)
d0895fd Add domain models, mock AI provider, and tests (Story 1.4)
729cd7f Initial commit: KMP project setup, CI/CD, and design system (Stories 1.1–1.3)
```

Files established in previous stories relevant to this story:
- `di/AppModule.kt` — `expect fun platformModule()` + `appModule()` composition
- `di/DataModule.kt` — add `PrivacyPipeline` binding here
- `di/PlatformModule.android.kt` — add `LocationProvider` binding
- `di/PlatformModule.ios.kt` — add `LocationProvider` binding
- `util/AppLogger.kt` — use for all logging
- `domain/model/DomainError.kt` — `LocationError` already defined
- `domain/model/AreaContext.kt` — `timeOfDay`, `dayOfWeek` will be used in Story 2.5
- `fakes/FakeAnalyticsTracker.kt` — follow same pattern for `FakeLocationProvider`

---

### Latest Technical Information

**Google Play Services Location (2026):**
- Latest: `play-services-location:21.3.0` (verify at [Google Maven](https://maven.google.com/web/index.html#com.google.android.gms:play-services-location))
- `FusedLocationProviderClient.getCurrentLocation(Priority, CancellationToken)` — available API 16+, returns `Task<Location?>`
- Priority constants: `Priority.PRIORITY_HIGH_ACCURACY`, `PRIORITY_BALANCED_POWER_ACCURACY`, `PRIORITY_LOW_POWER`
- Use `PRIORITY_HIGH_ACCURACY` for initial location; switch to `BALANCED_POWER_ACCURACY` for continuous updates (Story 2.5)

**Android Geocoder:**
- `Geocoder.isPresent()` — check if geocoder service is available (returns false on devices without Google services)
- `getFromLocation(lat, lng, maxResults)` — max 1 result is sufficient
- Geocoder is synchronous on API < 33 — it WILL block if called on main thread; always call from a coroutine dispatcher
- On emulators: Geocoder works as long as the emulator has Google Play (use "Pixel" device images, not "AOSP")

**iOS CoreLocation / CLGeocoder (2026):**
- `CLGeocoder.reverseGeocodeLocation()` callback is on the main queue by default — OK for `suspendCancellableCoroutine`
- `CLPlacemark.subLocality` → neighborhood (e.g., "Alfama"), `locality` → city (e.g., "Lisbon")
- `requestWhenInUseAuthorization()` must be called before `startUpdatingLocation()`
- iOS 14+: Location accuracy authorization (precise vs approximate) — use precise for Phase 1a

---

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.1] — User story, acceptance criteria
- [Source: _bmad-output/planning-artifacts/architecture.md#Project Structure] — `location/LocationProvider.kt` (expect), `domain/service/PrivacyPipeline.kt`, platform implementations
- [Source: _bmad-output/planning-artifacts/architecture.md#Data Flow] — GPS → LocationProvider → PrivacyPipeline → area name → AreaRepository → AI provider
- [Source: _bmad-output/planning-artifacts/architecture.md#Cross-Cutting Concerns] — NFR11 privacy pipeline enforcement point, NFR6 battery constraint
- [Source: _bmad-output/planning-artifacts/architecture.md#Authentication & Security] — NFR11: no raw GPS to APIs
- [Source: _bmad-output/implementation-artifacts/1-7-analytics-and-crash-reporting-foundation.md#Dev Notes] — Interface vs expect class decision, Koin platformModule pattern, kotlin.test assertion rules
- [Source: _bmad-output/planning-artifacts/architecture.md#Layer Boundary Rules] — domain/service/ pure business rules, enforced architecturally

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- iOS build fix: Added `@OptIn(ExperimentalForeignApi::class)` and `import kotlinx.cinterop.useContents` to `IosLocationProvider.kt` — required for accessing `CLLocationCoordinate2D` struct fields via Kotlin/Native cinterop.

### Completion Notes List

- All 10 tasks completed, all acceptance criteria satisfied
- `PrivacyPipeline` enforces NFR11: GPS coordinates never leave the class, only area name strings are returned
- `LocationProvider` interface pattern (not expect/actual) consistent with `AnalyticsTracker` from Story 1.7
- `AndroidLocationProvider`: FusedLocationProviderClient + Geocoder with API 33+ async/legacy fallback
- `IosLocationProvider`: CLLocationManager + CLGeocoder with Kotlin/Native cinterop
- 4 unit tests in `PrivacyPipelineTest` pass on both Android and iOS targets
- All 3 build gates pass: assembleDebug, allTests, lint
- Known note: `androidContext()` in PlatformModule is lazy — LocationProvider not instantiated until first request (Story 2.5). KoinApplication composable in App.kt may need androidContext registration when LocationProvider is first used at runtime.

### Change Log

- 2026-03-04: Story 2.1 implementation complete — location service and privacy pipeline

### File List

New files:
- composeApp/src/commonMain/kotlin/com/areadiscovery/location/GpsCoordinates.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/location/LocationProvider.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/service/PrivacyPipeline.kt
- composeApp/src/androidMain/kotlin/com/areadiscovery/location/AndroidLocationProvider.kt
- composeApp/src/iosMain/kotlin/com/areadiscovery/location/IosLocationProvider.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeLocationProvider.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/domain/service/PrivacyPipelineTest.kt

Modified files:
- composeApp/src/androidMain/AndroidManifest.xml (added location permissions)
- iosApp/iosApp/Info.plist (added NSLocationWhenInUseUsageDescription)
- composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt (added LocationProvider binding)
- composeApp/src/iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt (added LocationProvider binding)
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt (added PrivacyPipeline binding)
- composeApp/build.gradle.kts (added play-services-location dependency)
- gradle/libs.versions.toml (added play-services-location version and library entry)
