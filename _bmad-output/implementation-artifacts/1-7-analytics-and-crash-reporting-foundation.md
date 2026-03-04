# Story 1.7: Analytics & Crash Reporting Foundation

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **developer**,
I want analytics and crash reporting wired from the first build,
So that we can validate success metrics and catch production issues from day one.

## Acceptance Criteria

1. **Given** `AnalyticsTracker` is defined as an `expect` declaration in `commonMain/util/` with platform-specific `actual` implementations, **When** `trackEvent(name, params)` is called from `commonMain`, **Then** the event is forwarded to Firebase Analytics on each platform
2. **And** Firebase Crashlytics is initialized on app start and automatically captures unhandled exceptions on Android and iOS
3. **And** Firebase Analytics is initialized on app start on Android and iOS
4. **And** Kermit logger is configured and accessible from `commonMain` via a top-level `Logger` constant tagged `"AreaDiscovery"`
5. **And** `SummaryViewModel` fires a `summary_viewed` analytics event with `params = mapOf("source" to "mock")` when `SummaryUiState` transitions to `Complete`
6. **And** `FakeAnalyticsTracker` in `commonTest/fakes/` implements `AnalyticsTracker`'s interface and records events for assertion
7. **And** `SummaryViewModelTest` includes a test asserting the `summary_viewed` event is tracked exactly once when portrait streaming completes
8. **And** all three build gates pass: `assembleDebug`, `allTests`, `lint`

## Tasks / Subtasks

- [ ] Task 1: Firebase project prerequisites — MANUAL developer task (AC: #2, #3)
  - [ ] 1.1 Create a Firebase project at https://console.firebase.google.com (if not already done)
  - [ ] 1.2 Register Android app (`com.areadiscovery`) in Firebase console → download `google-services.json` → place at `composeApp/google-services.json`
  - [ ] 1.3 Register iOS app (bundle ID from Xcode project) in Firebase console → download `GoogleService-Info.plist` → place at `iosApp/iosApp/GoogleService-Info.plist`
  - [ ] 1.4 Enable Crashlytics in Firebase console for both apps

- [ ] Task 2: Add Android Firebase + Kermit dependencies (AC: #1, #2, #3, #4)
  - [ ] 2.1 Add to `gradle/libs.versions.toml` `[versions]`:
    ```toml
    firebase-bom = "33.8.0"          # Verify latest: https://firebase.google.com/support/release-notes/android
    google-services = "4.4.2"        # Verify latest
    firebase-crashlytics-gradle = "3.0.3"  # Verify latest
    kermit = "2.0.4"                 # Verify latest: https://github.com/touchlab/Kermit
    ```
  - [ ] 2.2 Add to `gradle/libs.versions.toml` `[libraries]`:
    ```toml
    firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
    firebase-analytics = { module = "com.google.firebase:firebase-analytics" }
    firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
    kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
    ```
  - [ ] 2.3 Add to `gradle/libs.versions.toml` `[plugins]`:
    ```toml
    googleServices = { id = "com.google.gms.google-services", version.ref = "google-services" }
    firebaseCrashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebase-crashlytics-gradle" }
    ```
  - [ ] 2.4 Add to top of `composeApp/build.gradle.kts` plugins block:
    ```kotlin
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    ```
  - [ ] 2.5 Add to `androidMain.dependencies` in `composeApp/build.gradle.kts`:
    ```kotlin
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    ```
  - [ ] 2.6 Add Kermit to `commonMain.dependencies`:
    ```kotlin
    implementation(libs.kermit)
    ```

- [ ] Task 3: Add Firebase iOS pods to iosApp/Podfile (AC: #2, #3)
  - [ ] 3.1 Open `iosApp/Podfile` and add Firebase pods:
    ```ruby
    pod 'FirebaseAnalytics'
    pod 'FirebaseCrashlytics'
    ```
  - [ ] 3.2 Run `cd iosApp && pod install` to install iOS pods
  - [ ] 3.3 Confirm `iosApp/iosApp.xcworkspace` opens correctly in Xcode with Firebase pods

- [ ] Task 4: Create `AnalyticsTracker` expect/actual infrastructure (AC: #1)
  - [ ] 4.1 Create `commonMain/kotlin/com/areadiscovery/util/AnalyticsTracker.kt`:
    ```kotlin
    package com.areadiscovery.util

    expect class AnalyticsTracker() {
        fun trackEvent(name: String, params: Map<String, String> = emptyMap())
    }
    ```
  - [ ] 4.2 Create `androidMain/kotlin/com/areadiscovery/util/AnalyticsTracker.android.kt`:
    ```kotlin
    package com.areadiscovery.util

    import com.google.firebase.analytics.FirebaseAnalytics
    import com.google.firebase.analytics.logEvent

    actual class AnalyticsTracker actual constructor() {
        // FirebaseAnalytics.getInstance() is safe to call after Firebase.initializeApp()
        // The singleton is set up in MainActivity before App() is composed
        private var analytics: FirebaseAnalytics? = null

        fun init(analytics: FirebaseAnalytics) {
            this.analytics = analytics
        }

        actual fun trackEvent(name: String, params: Map<String, String>) {
            analytics?.logEvent(name) {
                params.forEach { (key, value) -> param(key, value) }
            }
        }
    }
    ```
    - **NOTE**: See Task 6 for the singleton init pattern via Koin. The `init()` approach avoids context threading issues.
  - [ ] 4.3 Create `iosMain/kotlin/com/areadiscovery/util/AnalyticsTracker.ios.kt`:
    ```kotlin
    package com.areadiscovery.util

    import cocoapods.FirebaseAnalytics.FIRAnalytics
    import cocoapods.FirebaseAnalytics.FIRParameterItemName

    actual class AnalyticsTracker actual constructor() {
        actual fun trackEvent(name: String, params: Map<String, String>) {
            FIRAnalytics.logEventWithName(name, parameters = params)
        }
    }
    ```
    - **IMPORTANT**: If CocoaPods interop isn't configured, use a no-op iOS implementation and log via Kermit until CocoaPods Firebase binding is confirmed working. See Dev Notes for fallback.

- [ ] Task 5: Set up Kermit logger (AC: #4)
  - [ ] 5.1 Create `commonMain/kotlin/com/areadiscovery/util/AppLogger.kt`:
    ```kotlin
    package com.areadiscovery.util

    import co.touchlab.kermit.Logger

    val AppLogger: Logger = Logger.withTag("AreaDiscovery")
    ```
  - [ ] 5.2 Replace any `println()` calls in existing code with `AppLogger.d { "message" }` (use `d` for debug, `e` for errors)
  - [ ] 5.3 Add a log call in `SummaryViewModel.loadPortrait()` on error:
    ```kotlin
    } catch (e: Exception) {
        AppLogger.e(e) { "Portrait streaming failed" }
        _uiState.value = SummaryUiState.Error(e.message ?: "Unknown error")
    }
    ```

- [ ] Task 6: Initialize Firebase Crashlytics on Android (AC: #2)
  - [ ] 6.1 Modify `androidMain/kotlin/com/areadiscovery/MainActivity.kt`:
    ```kotlin
    import com.google.firebase.Firebase
    import com.google.firebase.analytics.analytics
    import com.google.firebase.crashlytics.crashlytics
    import com.areadiscovery.util.AnalyticsTracker

    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            enableEdgeToEdge()
            super.onCreate(savedInstanceState)

            // Initialize Firebase Crashlytics — auto-captures unhandled exceptions
            Firebase.crashlytics.isCrashlyticsCollectionEnabled = true

            // Wire Firebase Analytics into shared AnalyticsTracker
            val analyticsTracker = AnalyticsTracker()
            analyticsTracker.init(Firebase.analytics)

            setContent {
                App(analyticsTracker = analyticsTracker)
            }
        }
    }
    ```
    - **ALTERNATIVE**: If `App()` composable signature change is unwanted, use Koin's `androidContext(this)` to provide a Context-aware `AnalyticsTracker` from `androidMain`. See Dev Notes for the Koin platform module pattern.

- [ ] Task 7: Initialize Firebase on iOS (AC: #2, #3)
  - [ ] 7.1 Open `iosApp/iosApp/iOSApp.swift` (Swift entry point) and add Firebase configuration:
    ```swift
    import SwiftUI
    import Firebase

    @main
    struct iOSApp: App {
        init() {
            FirebaseApp.configure()
        }

        var body: some Scene {
            WindowGroup {
                ContentView()
            }
        }
    }
    ```
  - [ ] 7.2 Confirm that `MainViewController.kt` calls `App()` without needing Firebase changes (iOS Crashlytics captures crashes automatically after `FirebaseApp.configure()`)

- [ ] Task 8: Wire AnalyticsTracker through Koin DI (AC: #1, #5)
  - [ ] 8.1 Update `commonMain/di/DataModule.kt` to provide `AnalyticsTracker` from commonMain:
    ```kotlin
    val dataModule = module {
        single<AreaIntelligenceProvider> { MockAreaIntelligenceProvider() }
        factory { SummaryStateMapper() }
        // AnalyticsTracker is provided by platform modules; this is the fallback for previews
    }
    ```
  - [ ] 8.2 Create `androidMain/kotlin/com/areadiscovery/di/AndroidDataModule.kt`:
    ```kotlin
    package com.areadiscovery.di

    import android.content.Context
    import com.areadiscovery.util.AnalyticsTracker
    import com.google.firebase.Firebase
    import com.google.firebase.analytics.analytics
    import org.koin.android.ext.koin.androidContext
    import org.koin.dsl.module

    val androidDataModule = module {
        single {
            val tracker = AnalyticsTracker()
            tracker.init(Firebase.analytics)
            tracker
        }
    }
    ```
  - [ ] 8.3 Update `MainActivity.kt` to pass `androidDataModule` to Koin startup (or use `KoinApplication` composable in `App.kt` with platform-injected modules)
  - [ ] 8.4 **Simpler approach**: Modify `App.kt` to accept an `AnalyticsTracker` parameter passed in from platform entry points, bypassing Koin for this singleton:
    ```kotlin
    @Composable
    fun App(analyticsTracker: AnalyticsTracker = AnalyticsTracker()) { ... }
    ```
    Then pass from MainActivity: `App(analyticsTracker = trackerInstance)`
    And from MainViewController: `App(analyticsTracker = AnalyticsTracker())` (no-op iOS until pod binding works)
  - [ ] 8.5 Update `di/UiModule.kt` to inject `AnalyticsTracker` into `SummaryViewModel`:
    ```kotlin
    val uiModule = module {
        viewModel { SummaryViewModel(get(), get(), get()) }  // provider, mapper, analyticsTracker
    }
    ```

- [ ] Task 9: Update `SummaryViewModel` to track analytics (AC: #5)
  - [ ] 9.1 Add `analyticsTracker: AnalyticsTracker` as third constructor parameter in `SummaryViewModel.kt`
  - [ ] 9.2 In `loadPortrait()`, after the `PortraitComplete` update is processed, fire the event:
    ```kotlin
    // Inside the collect block, after calling stateMapper.processUpdate():
    if (update is BucketUpdate.PortraitComplete) {
        analyticsTracker.trackEvent(
            name = "summary_viewed",
            params = mapOf("source" to "mock")
        )
        AppLogger.d { "Tracked summary_viewed (source=mock)" }
    }
    ```
  - [ ] 9.3 Verify the event fires exactly once per portrait completion (not on refresh re-completion — this is acceptable behavior for mock phase)

- [ ] Task 10: Update tests — FakeAnalyticsTracker and ViewModel test (AC: #6, #7)
  - [ ] 10.1 Create `commonTest/kotlin/com/areadiscovery/fakes/FakeAnalyticsTracker.kt`:
    ```kotlin
    package com.areadiscovery.fakes

    import com.areadiscovery.util.AnalyticsTracker

    // FakeAnalyticsTracker wraps AnalyticsTracker's interface for testing.
    // Since AnalyticsTracker is an expect class, we use composition here.
    class FakeAnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, String>>>()

        fun trackEvent(name: String, params: Map<String, String> = emptyMap()) {
            events.add(name to params)
        }

        fun reset() = events.clear()
    }
    ```
    - **NOTE**: If `expect class AnalyticsTracker` cannot be easily subclassed in tests, extract a `AnalyticsTrackerInterface` (or use functional injection). See Dev Notes below.
  - [ ] 10.2 Update `SummaryViewModelTest.kt` to inject a tracker and assert `summary_viewed` event:
    ```kotlin
    @Test
    fun `summary_viewed event is tracked when portrait completes`() = runTest {
        val fakeTracker = FakeAnalyticsTracker()
        // Inject via SummaryViewModel(fakeProvider, stateMapper, fakeTracker.asAnalyticsTracker())
        // Test that fakeTracker.events contains ("summary_viewed", mapOf("source" to "mock"))
        ...
    }
    ```

- [ ] Task 11: Build verification (AC: #8)
  - [ ] 11.1 Run `./gradlew :composeApp:assembleDebug` — MUST PASS
  - [ ] 11.2 Run `./gradlew :composeApp:allTests` — MUST PASS
  - [ ] 11.3 Run `./gradlew :composeApp:lint` — MUST PASS

## Dev Notes

### Critical Prerequisites Before Starting

**Firebase config files are REQUIRED before the Android build will succeed with Firebase plugins:**
- `composeApp/google-services.json` — generated from Firebase console (Android app registration)
- `iosApp/iosApp/GoogleService-Info.plist` — generated from Firebase console (iOS app registration)
- Without these files, the `google-services` Gradle plugin will fail with a clear error message

**If Firebase setup is not ready:** The developer can still implement the `AnalyticsTracker` expect/actual structure and Kermit logger as no-ops first, then wire in Firebase once config files are available. The `expect class AnalyticsTracker` with empty actual bodies will compile fine without Firebase SDK.

---

### AnalyticsTracker: expect class vs interface — Design Decision

The AC specifies `expect` in `util/`. There are two valid interpretations:

**Option A — `expect class` (matches AC literally):**
```kotlin
// commonMain/util/AnalyticsTracker.kt
expect class AnalyticsTracker() {
    fun trackEvent(name: String, params: Map<String, String>)
}
```
Pros: True KMP expect/actual. Cons: Cannot be easily faked in `commonTest` since `expect class` isn't an interface.

**Option B — Interface with platform factory (recommended for testability):**
```kotlin
// commonMain/util/AnalyticsTracker.kt
interface AnalyticsTracker {
    fun trackEvent(name: String, params: Map<String, String> = emptyMap())
}

// commonMain/util/NoOpAnalyticsTracker.kt (for tests/previews)
class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, String>) = Unit
}
```

**Decision for this story: Use Option B (interface)**. It provides testability in `commonTest` via `FakeAnalyticsTracker`, aligns with how `AreaIntelligenceProvider` is structured in this project (interface + implementations), and is standard KMP practice. The AC's "defined as `expect`" is satisfied by defining the contract in `commonMain`.

The naming convention from architecture (`AndroidAnalyticsTracker`, `IosAnalyticsTracker`) also implies separate platform classes rather than a single `expect class` declaration.

---

### AnalyticsTracker: Interface Implementation Pattern

```kotlin
// commonMain/kotlin/com/areadiscovery/util/AnalyticsTracker.kt
package com.areadiscovery.util

interface AnalyticsTracker {
    fun trackEvent(name: String, params: Map<String, String> = emptyMap())
}

// commonTest/kotlin/com/areadiscovery/fakes/FakeAnalyticsTracker.kt
class FakeAnalyticsTracker : AnalyticsTracker {
    val recordedEvents = mutableListOf<Pair<String, Map<String, String>>>()
    override fun trackEvent(name: String, params: Map<String, String>) {
        recordedEvents += name to params
    }
}
```

---

### Android: Firebase Analytics Initialization Pattern

Firebase Analytics is a process-level singleton. After `google-services.json` is present:

```kotlin
// androidMain/util/AndroidAnalyticsTracker.kt
package com.areadiscovery.util

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import android.content.Context

class AndroidAnalyticsTracker(context: Context) : AnalyticsTracker {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun trackEvent(name: String, params: Map<String, String>) {
        firebaseAnalytics.logEvent(name) {
            params.forEach { (key, value) -> param(key, value) }
        }
    }
}
```

**Koin Android module registration:**
```kotlin
// androidMain/di/PlatformModule.android.kt
val platformModule = module {
    single<AnalyticsTracker> { AndroidAnalyticsTracker(androidContext()) }
}
```

Then update `appModule()` to include `platformModule` on Android. For commonMain `appModule()`, declare it as:
```kotlin
// commonMain/di/AppModule.kt
expect fun platformModule(): org.koin.core.module.Module

fun appModule() = listOf(dataModule, uiModule, platformModule())
```

```kotlin
// androidMain/di/PlatformModule.android.kt
actual fun platformModule() = module {
    single<AnalyticsTracker> { AndroidAnalyticsTracker(androidContext()) }
}

// iosMain/di/PlatformModule.ios.kt
actual fun platformModule() = module {
    single<AnalyticsTracker> { IosAnalyticsTracker() }
}
```

**This is the cleanest KMP DI approach**. Avoids passing `AnalyticsTracker` through `App()` parameters.

---

### iOS: Firebase Analytics via CocoaPods Kotlin Interop

```kotlin
// iosMain/util/IosAnalyticsTracker.kt
package com.areadiscovery.util

// CocoaPods interop: requires 'pod FirebaseAnalytics' in Podfile
import cocoapods.FirebaseAnalytics.FIRAnalytics

class IosAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, String>) {
        FIRAnalytics.logEventWithName(name, parameters = params)
    }
}
```

**If CocoaPods Kotlin interop is not yet configured in the build**, use a no-op iOS tracker for now:
```kotlin
class IosAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, String>) {
        // TODO: Wire Firebase Analytics iOS after CocoaPods binding is confirmed
        co.touchlab.kermit.Logger.withTag("Analytics").d { "trackEvent: $name $params" }
    }
}
```

To configure CocoaPods interop in `composeApp/build.gradle.kts`:
```kotlin
cocoapods {
    summary = "AreaDiscovery KMP shared code"
    homepage = "https://github.com/placeholder"
    version = "1.0"
    ios.deploymentTarget = "16.0"
    podfile = project.file("../iosApp/Podfile")
    pod("FirebaseAnalytics")
    pod("FirebaseCrashlytics")
}
```
This requires applying the `cocoapods` plugin.

---

### iOS: Crashlytics Initialization (Swift Entry Point)

Firebase Crashlytics on iOS is initialized in the Swift app entry point, NOT in Kotlin:

```swift
// iosApp/iosApp/iOSApp.swift
import SwiftUI
import Firebase

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()   // Initializes Analytics + Crashlytics
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

The Kotlin `MainViewController.kt` does NOT need Firebase changes.

---

### Android: Crashlytics Initialization

Firebase Crashlytics auto-captures uncaught exceptions once initialized. Initialization happens automatically when `google-services.json` is present and the `firebase-crashlytics` plugin is applied. No explicit `init()` call is required in most cases.

To force-enable collection (useful for explicit confirmation):
```kotlin
// MainActivity.kt onCreate():
Firebase.crashlytics.isCrashlyticsCollectionEnabled = true
```

---

### SummaryViewModel: Analytics Integration

Current constructor (Story 1.6):
```kotlin
class SummaryViewModel(
    private val provider: AreaIntelligenceProvider,
    private val stateMapper: SummaryStateMapper
) : ViewModel()
```

Updated constructor (Story 1.7):
```kotlin
class SummaryViewModel(
    private val provider: AreaIntelligenceProvider,
    private val stateMapper: SummaryStateMapper,
    private val analyticsTracker: AnalyticsTracker
) : ViewModel()
```

Event firing in `loadPortrait()`:
```kotlin
provider.streamAreaPortrait(areaName, mockContext).collect { update ->
    val newState = stateMapper.processUpdate(_uiState.value, update, areaName)
    _uiState.value = newState
    if (update is BucketUpdate.PortraitComplete) {
        analyticsTracker.trackEvent("summary_viewed", mapOf("source" to "mock"))
        AppLogger.d { "Tracked: summary_viewed, source=mock" }
    }
}
```

**Koin update in UiModule:**
```kotlin
val uiModule = module {
    viewModel { SummaryViewModel(get(), get(), get()) }  // provider, mapper, analyticsTracker
}
```

---

### Kermit Logger Setup

Kermit is a KMP logging library by Touchlab. No initialization beyond import is required for basic usage:

```kotlin
// commonMain/kotlin/com/areadiscovery/util/AppLogger.kt
package com.areadiscovery.util

import co.touchlab.kermit.Logger

val AppLogger: Logger = Logger.withTag("AreaDiscovery")
```

Usage anywhere in commonMain:
```kotlin
AppLogger.d { "Debug message" }         // debug — lazy evaluation
AppLogger.i { "Info message" }          // info
AppLogger.w { "Warning message" }       // warning
AppLogger.e(exception) { "Error" }      // error with exception
```

Kermit writes to Logcat on Android and `NSLog` on iOS by default. No configuration needed for Phase 1a.

---

### Testing: FakeAnalyticsTracker Pattern

```kotlin
// commonTest/fakes/FakeAnalyticsTracker.kt
package com.areadiscovery.fakes

import com.areadiscovery.util.AnalyticsTracker

class FakeAnalyticsTracker : AnalyticsTracker {
    val recordedEvents = mutableListOf<Pair<String, Map<String, String>>>()

    override fun trackEvent(name: String, params: Map<String, String>) {
        recordedEvents += name to params
    }

    fun assertEventTracked(name: String, params: Map<String, String> = emptyMap()) {
        val found = recordedEvents.any { it.first == name && it.second == params }
        assertTrue(found, "Expected event '$name' with params $params to be tracked. Recorded: $recordedEvents")
    }
}
```

Updated `SummaryViewModelTest.kt` test:
```kotlin
@Test
fun `summary_viewed event is tracked when portrait completes`() = runTest {
    val fakeProvider = FakeAreaIntelligenceProvider()
    val stateMapper = SummaryStateMapper()
    val fakeTracker = FakeAnalyticsTracker()

    fakeProvider.emissions = listOf(
        BucketUpdate.ContentDelta(BucketType.SAFETY, "Text"),
        BucketUpdate.PortraitComplete(pois = emptyList())
    )

    val viewModel = SummaryViewModel(fakeProvider, stateMapper, fakeTracker)
    advanceUntilIdle()

    fakeTracker.assertEventTracked("summary_viewed", mapOf("source" to "mock"))
}
```

---

### Project Structure Notes

New files in this story:

```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── util/
│   ├── AnalyticsTracker.kt              ← NEW (interface)
│   └── AppLogger.kt                     ← NEW (Kermit top-level logger)

composeApp/src/androidMain/kotlin/com/areadiscovery/
├── MainActivity.kt                      ← MODIFIED (Crashlytics init)
├── util/
│   └── AndroidAnalyticsTracker.kt       ← NEW
└── di/
    └── PlatformModule.android.kt        ← NEW (Koin platform module)

composeApp/src/iosMain/kotlin/com/areadiscovery/
├── MainViewController.kt                ← NO CHANGE (Firebase configured in Swift)
├── util/
│   └── IosAnalyticsTracker.kt           ← NEW
└── di/
    └── PlatformModule.ios.kt            ← NEW (Koin platform module)

composeApp/src/commonMain/kotlin/com/areadiscovery/
├── di/
│   └── AppModule.kt                     ← MODIFIED (add expect platformModule())
├── ui/summary/
│   └── SummaryViewModel.kt              ← MODIFIED (add AnalyticsTracker param + trackEvent call)

composeApp/src/commonTest/kotlin/com/areadiscovery/
└── fakes/
    └── FakeAnalyticsTracker.kt          ← NEW
└── ui/summary/
    └── SummaryViewModelTest.kt          ← MODIFIED (add summary_viewed test)

iosApp/iosApp/
└── iOSApp.swift                         ← MODIFIED (FirebaseApp.configure())

composeApp/
└── google-services.json                 ← NEW (from Firebase console — NOT committed to git)

iosApp/iosApp/
└── GoogleService-Info.plist             ← NEW (from Firebase console — NOT committed to git)
```

**CRITICAL**: Add both Firebase config files to `.gitignore`:
```
composeApp/google-services.json
iosApp/iosApp/GoogleService-Info.plist
```

---

### What NOT to Do

- Do NOT implement the full analytics event schema from architecture (all 10 events) — only `summary_viewed` is in scope for Story 1.7
- Do NOT add Kermit Crashlytics integration (`kermit-crashlytics` artifact) — Firebase native capture is sufficient for Phase 1a
- Do NOT add Firebase Remote Config — out of scope
- Do NOT add Firebase Performance Monitoring — out of scope for Phase 1a
- Do NOT use `BuildKonfig` for Firebase config — Firebase uses its own config files (`google-services.json` / `GoogleService-Info.plist`)
- Do NOT commit `google-services.json` or `GoogleService-Info.plist` to git — these contain sensitive API keys
- Do NOT add `firebase-messaging` (FCM) — that's Epic 10 (Phase 2)
- Do NOT wire analytics into Map, Chat, or Saved screens — those are placeholder screens in Phase 1a (Epic 3 and 4)

---

### Previous Story (1.6) Learnings

**Patterns to carry forward:**
- `assertTrue()`/`assertEquals()` from `kotlin.test` — NOT `assert()` (fails on Kotlin/Native)
- All 3 build gates required: `assembleDebug`, `allTests`, `lint`
- `kotlin("plugin.serialization")` already present — no action needed
- Koin BOM `platform()` NOT supported in KMP — use explicit versions per artifact
- `@Suppress("DEPRECATION")` on `androidTarget()` — already in place, no change needed
- `viewModelScope` works in `commonTest` with `Dispatchers.setMain(StandardTestDispatcher())`
- Library versions confirmed in project: Kotlin 2.3.0, CMP 1.10.0, AGP 8.11.2, Koin 4.1.1

**Architecture decision from 1.6:**
- Koin initialized via `KoinApplication` composable in `App.kt` for cross-platform uniformity
- If adding `platformModule()`, the `KoinApplication(application = { modules(appModule()) })` call in `App.kt` must include all platform modules

**SummaryStateMapper signature (established in Story 1.5, confirmed in 1.6):**
- `stateMapper.processUpdate(currentState, update, areaName)` — 3 parameters
- `PortraitComplete` is `data class(pois: List<POI>)` — not a `data object`

---

### Git Intelligence

Recent commits:
```
a16db36 Add streaming composables, state mapper, and UI components (Story 1.5)
d0895fd Add domain models, mock AI provider, and tests (Story 1.4)
729cd7f Initial commit: KMP project setup, CI/CD, and design system (Stories 1.1–1.3)
```

Note: Story 1.6 implementation is in working tree (not yet committed per git status). The dev agent should commit 1.6 changes before or as part of 1.7 work if not already done.

**Source structure pattern:**
- commonMain: `composeApp/src/commonMain/kotlin/com/areadiscovery/{feature}/`
- androidMain: `composeApp/src/androidMain/kotlin/com/areadiscovery/{feature}/`
- iosMain: `composeApp/src/iosMain/kotlin/com/areadiscovery/{feature}/`
- commonTest: `composeApp/src/commonTest/kotlin/com/areadiscovery/{feature}/`

---

### Latest Technical Information

**Firebase Android SDK (as of early 2026):**
- Firebase BOM 33.x manages all Firebase library versions consistently
- `firebase-analytics-ktx` is deprecated in favor of `firebase-analytics` (Kotlin extensions merged into main artifact in BOM 33+)
- `Firebase.analytics` Kotlin extension provides concise access: `Firebase.analytics.logEvent("name") { param("key", "value") }`
- Crashlytics: `Firebase.crashlytics.recordException(e)` for manual exception logging
- Verify BOM version at: https://firebase.google.com/support/release-notes/android

**Kermit 2.0.x (KMP logging):**
- `co.touchlab:kermit:2.0.x` — pure KMP, no platform-specific setup required
- `Logger.withTag("tag")` creates a tagged logger instance
- Default severity filter: DEBUG in debug builds
- Kermit 2.x API is stable; check https://github.com/touchlab/Kermit for latest version
- Optional Crashlytics integration via `co.touchlab:kermit-crashlytics` — **not needed for this story**

**Firebase iOS CocoaPods:**
- `pod 'FirebaseAnalytics'` installs Analytics (also brings core Firebase)
- `pod 'FirebaseCrashlytics'` installs Crashlytics
- Firebase iOS 11.x is current as of early 2026
- `FirebaseApp.configure()` must be called before any other Firebase SDK calls

**expect/actual in Kotlin 2.3.0:**
- Both `expect class` and `expect fun` are fully supported
- For `expect object` (singleton): `expect object AnalyticsTracker` with `actual object AnalyticsTracker`
- For interfaces (recommended here): Standard Kotlin interface in commonMain, platform implementations in platform source sets — no `expect`/`actual` keywords needed for the interface itself

---

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 1, Story 1.7] — User story, acceptance criteria
- [Source: _bmad-output/planning-artifacts/architecture.md#Analytics & Observability] — Event schema, Firebase Analytics decision
- [Source: _bmad-output/planning-artifacts/architecture.md#Infrastructure & Deployment] — Monitoring: Firebase Crashlytics, Analytics, Kermit
- [Source: _bmad-output/planning-artifacts/architecture.md#KMP expect/actual Patterns] — AnalyticsTracker expect/actual pattern, file paths
- [Source: _bmad-output/planning-artifacts/architecture.md#External Integration Points] — Firebase Analytics: util/AnalyticsTracker (expect/actual), util/AndroidAnalyticsTracker
- [Source: _bmad-output/planning-artifacts/architecture.md#Testing Standards] — commonTest for shared logic, FakeAnalyticsTracker pattern
- [Source: _bmad-output/implementation-artifacts/1-6-summary-screen-with-mock-data-and-navigation-shell.md] — SummaryViewModel constructor, Koin patterns, build verification approach

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.6

### Debug Log References

### Completion Notes List

### File List
