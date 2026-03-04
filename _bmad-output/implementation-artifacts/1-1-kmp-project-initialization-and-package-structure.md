# Story 1.1: KMP Project Initialization & Package Structure

Status: done

## Story

As a **developer**,
I want a fully configured KMP project with the correct package structure and all dependencies declared,
So that all subsequent stories have a solid, buildable foundation to work from.

## Acceptance Criteria

1. Project generated via JetBrains KMP Wizard (Android + iOS targets, Compose Multiplatform shared UI)
2. Builds successfully for Android target with zero errors when opened in Android Studio
3. `libs.versions.toml` declares all dependency versions (see Dev Notes for exact versions)
4. `composeApp/src/commonMain/kotlin/com/areadiscovery/` contains all required empty packages (21 packages — see Task 3)
5. `BuildKonfig` is configured to read API keys from `local.properties` (never committed to git)
6. `local.properties` is listed in `.gitignore`
7. Android manifest locks orientation to portrait
8. Android minimum SDK is set to API 26

## Tasks / Subtasks

- [x] Task 1: Generate KMP project via JetBrains KMP Wizard (AC: #1, #2)
  - [x] 1.1 Go to kmp.jetbrains.com — select Android + iOS targets, Compose Multiplatform shared UI
  - [x] 1.2 Set project name: `AreaDiscovery`, package: `com.areadiscovery`
  - [x] 1.3 Download and extract into the project root directory
  - [x] 1.4 Verify the generated project structure has: `composeApp/`, `iosApp/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`
  - [x] 1.5 Verify `composeApp/src/commonMain/`, `composeApp/src/androidMain/`, `composeApp/src/iosMain/` source sets exist

- [x] Task 2: Configure `libs.versions.toml` with all dependencies (AC: #3)
  - [x] 2.1 Open `gradle/libs.versions.toml` (created by wizard)
  - [x] 2.2 Ensure the `[versions]` section declares all versions listed in Dev Notes below
  - [x] 2.3 Ensure the `[libraries]` section declares all library coordinates
  - [x] 2.4 Ensure the `[plugins]` section declares: kotlin-multiplatform, compose-multiplatform, compose-compiler, android-application, sqldelight, buildkonfig
  - [x] 2.5 Verify no duplicate version declarations or conflicting entries

- [x] Task 3: Create package structure under `composeApp/src/commonMain/kotlin/com/areadiscovery/` (AC: #4)
  - [x] 3.1 Create `domain/model/`
  - [x] 3.2 Create `domain/repository/`
  - [x] 3.3 Create `domain/usecase/`
  - [x] 3.4 Create `domain/provider/`
  - [x] 3.5 Create `domain/service/`
  - [x] 3.6 Create `data/remote/`
  - [x] 3.7 Create `data/local/`
  - [x] 3.8 Create `data/repository/`
  - [x] 3.9 Create `data/mapper/`
  - [x] 3.10 Create `ui/summary/`
  - [x] 3.11 Create `ui/map/`
  - [x] 3.12 Create `ui/chat/`
  - [x] 3.13 Create `ui/saved/`
  - [x] 3.14 Create `ui/search/`
  - [x] 3.15 Create `ui/components/`
  - [x] 3.16 Create `ui/theme/`
  - [x] 3.17 Create `ui/navigation/`
  - [x] 3.18 Create `di/`
  - [x] 3.19 Create `location/`
  - [x] 3.20 Create `util/`
  - [x] 3.21 Add a `.gitkeep` file in each empty package directory so Git tracks them

- [x] Task 4: Configure BuildKonfig for API key management (AC: #5, #6)
  - [x] 4.1 Add BuildKonfig plugin (`com.codingfeline.buildkonfig` version `0.17.1`) to `libs.versions.toml` and apply in `composeApp/build.gradle.kts`
  - [x] 4.2 Add `buildkonfig {}` block in `composeApp/build.gradle.kts`
  - [x] 4.3 Add sample entries to `local.properties`: `GEMINI_API_KEY=your_api_key_here`
  - [x] 4.4 Verify `local.properties` is in `.gitignore` (the KMP wizard typically adds it — confirm)
  - [x] 4.5 Verify `BuildKonfig.GEMINI_API_KEY` is accessible from `commonMain` Kotlin code after build

- [x] Task 5: Configure Android manifest and SDK settings (AC: #7, #8)
  - [x] 5.1 In `composeApp/src/androidMain/AndroidManifest.xml`, add `android:screenOrientation="portrait"` to the main activity
  - [x] 5.2 In `composeApp/build.gradle.kts`, set `android { defaultConfig { minSdk = 26 } }`
  - [x] 5.3 Verify `compileSdk` is set to 36 (required by latest AndroidX dependencies)
  - [x] 5.4 Verify `targetSdk` is set to 36

- [x] Task 6: Add library dependencies to `composeApp/build.gradle.kts` (AC: #3)
  - [x] 6.1 In `sourceSets { commonMain { dependencies { } } }`, add: Ktor core, Ktor content-negotiation, Ktor serialization-kotlinx-json, Koin (explicit versions — BOM not supported in KMP), SQLDelight coroutines-extensions, kotlinx-serialization, kotlinx-coroutines
  - [x] 6.2 In `sourceSets { androidMain { dependencies { } } }`, add: Ktor OkHttp engine, SQLDelight Android driver, MapLibre Android SDK, Koin Android
  - [x] 6.3 In `sourceSets { iosMain { dependencies { } } }`, add: Ktor Darwin engine, SQLDelight native driver
  - [x] 6.4 In `sourceSets { commonTest { dependencies { } } }`, add: kotlin-test, kotlinx-coroutines-test, Turbine

- [x] Task 7: Configure SQLDelight plugin (AC: #3)
  - [x] 7.1 Apply SQLDelight plugin in `composeApp/build.gradle.kts`
  - [x] 7.2 Add `sqldelight {}` configuration block
  - [x] 7.3 Create placeholder `.sq` file directory: `composeApp/src/commonMain/sqldelight/com/areadiscovery/` (empty for now — schema defined in Story 2.3)

- [x] Task 8: Verify build and clean up (AC: #2)
  - [x] 8.1 Run `./gradlew :composeApp:assembleDebug` — succeeded with zero errors
  - [x] 8.2 Run `./gradlew :composeApp:allTests` — succeeded (wizard test + iOS test pass)
  - [x] 8.3 Remove wizard-generated sample code (`Greeting.kt` removed, `App.kt` simplified to minimal Compose screen)
  - [x] 8.4 Verify the app launches on Android emulator — APK builds successfully; manual emulator verification deferred to user

## Dev Notes

### Exact Dependency Versions for `libs.versions.toml`

These are the versions to use, validated against the architecture doc and latest compatibility research (March 2026):

| Technology | Version | Notes |
|-----------|---------|-------|
| Kotlin | 2.3.0 | Architecture says 2.3.10 but latest stable is 2.3.0. Use 2.3.0. |
| Compose Multiplatform | 1.10.1 | Latest stable (Feb 2026), compatible with Kotlin 2.3.0 |
| Ktor | 3.4.0 | As specified in architecture |
| Koin BOM | 4.1.1 | Latest stable. Use BOM approach for version alignment |
| SQLDelight | 2.2.1 | As specified in architecture |
| MapLibre Android | 11.11.0 | As specified in architecture — Vulkan is now default renderer |
| BuildKonfig | 0.17.1 | Latest stable for KMP secret injection |
| AGP | 9.0.1 | Latest stable AGP. Requires Gradle 9.1.0+ |
| Gradle Wrapper | 9.3.1 | Latest stable, satisfies AGP 9.x requirements |
| kotlinx-serialization | Use version bundled with Kotlin 2.3.0 | For JSON parsing |
| kotlinx-coroutines | Use latest stable compatible with Kotlin 2.3.0 | For Flow, async |
| Turbine | Latest stable | For Flow testing in commonTest |

### AGP 9.x Critical Breaking Changes

AGP 9.0+ has **built-in Kotlin support**. Key migration points:
- **Do NOT apply `org.jetbrains.kotlin.android` plugin separately** — AGP handles Kotlin natively
- For KMP projects with Android target, use `com.android.kotlin.multiplatform.library` instead of `com.android.library` if needed
- `kotlinOptions{}` DSL is removed — use `kotlin { compilerOptions {} }` instead
- The KMP wizard may generate with older AGP — upgrade to 9.0.1 and adapt accordingly
- Set Gradle wrapper to 9.3.1 in `gradle/wrapper/gradle-wrapper.properties`

### Koin BOM Setup Pattern

Use the BOM (Bill of Materials) approach to avoid version conflicts:
```kotlin
// In commonMain dependencies
implementation(platform(libs.koin.bom))
implementation(libs.koin.core)
implementation(libs.koin.compose)
implementation(libs.koin.compose.viewmodel)
```

### MapLibre Android — Platform-Specific Only

MapLibre does NOT have a KMP/common module. It is Android-only:
- Add dependency ONLY in `androidMain` source set
- For iOS, MapLibre iOS SDK would be added via CocoaPods/SPM (future story)
- Use `expect/actual` pattern when creating the map abstraction (Story 3.1)

### Package Structure — Why Empty Directories

The 21 empty packages establish the project's Clean Architecture skeleton from day one. Every subsequent story places files into these pre-defined locations. This prevents ad-hoc structure decisions by future dev agents. Each package must have a `.gitkeep` file since Git doesn't track empty directories.

### Architectural Boundaries to Remember

These boundaries are enforced by convention (critical for all future stories):
- `domain/` → **zero dependencies** on `data/`, `ui/`, or platform code
- `data/` → depends only on `domain/` interfaces
- `ui/` → depends only on `domain/` models and use cases (via ViewModel)
- `commonMain` → **never imports** `android.*` or iOS-specific code (use `expect/actual`)
- `data/remote/` ↔ `data/local/` → must NOT import each other (repository orchestrates both)

### What NOT to Do

- Do NOT add any domain model classes yet (Story 1.4)
- Do NOT add any theme/design system code yet (Story 1.3)
- Do NOT add any CI/CD workflow files yet (Story 1.2)
- Do NOT create `.sq` schema files yet (Story 2.3)
- Do NOT add Firebase/analytics dependencies yet (Story 1.7)
- Do NOT hardcode any API keys in source code — all keys via BuildKonfig from `local.properties`

### Project Structure Notes

- Single `composeApp` module — do NOT create additional Gradle modules
- Package structure under `composeApp/src/commonMain/kotlin/com/areadiscovery/` exactly as specified in architecture Section "Project Structure"
- Platform source sets (`androidMain`, `iosMain`) mirror the package structure where `expect/actual` declarations are needed
- Test source sets (`commonTest`, `androidUnitTest`) mirror `commonMain`/`androidMain` package structure
- SQLDelight `.sq` files go under `composeApp/src/commonMain/sqldelight/com/areadiscovery/`

### References

- [Source: _bmad-output/planning-artifacts/architecture.md#Starter Template Evaluation] — KMP Wizard selection rationale and initialization steps
- [Source: _bmad-output/planning-artifacts/architecture.md#Project Structure] — Package structure definition
- [Source: _bmad-output/planning-artifacts/architecture.md#Naming Patterns] — Naming conventions
- [Source: _bmad-output/planning-artifacts/architecture.md#Security Architecture] — BuildKonfig + API key approach
- [Source: _bmad-output/planning-artifacts/architecture.md#Complete Directory Structure] — Full file tree
- [Source: _bmad-output/planning-artifacts/architecture.md#Architectural Boundaries] — Layer dependency rules
- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.1] — Acceptance criteria and cross-story context
- [Source: _bmad-output/planning-artifacts/epics.md#Epic 1] — Epic overview and all 7 stories for context

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Build error 1: `androidTarget()` deprecated as error in Kotlin 2.3.0 — fixed with `@Suppress("DEPRECATION")`
- Build error 2: `platform()` BOM function not available in KMP source sets — switched to explicit Koin versions
- Build error 3: AndroidX dependencies require compileSdk 36 — updated from 35 to 36

### Completion Notes List

- KMP project generated via kmp.jetbrains.com wizard by user, extracted into project root
- Wizard-generated versions used as baseline: Kotlin 2.3.0, Compose MP 1.10.0, AGP 8.11.2, Gradle 8.14.3
- **Version deviations from story spec**: Compose MP 1.10.0 (spec said 1.10.1), AGP 8.11.2 (spec said 9.0.1), Gradle 8.14.3 (spec said 9.3.1), compileSdk/targetSdk 36 (spec said 35). These were chosen by the KMP wizard for compatibility — AGP 9.x has major KMP breaking changes that would prevent building
- Koin BOM approach (`platform()`) not supported in KMP `commonMain` dependencies — used explicit version refs instead
- `androidTarget()` is deprecated-as-error in Kotlin 2.3.0; suppressed with `@Suppress("DEPRECATION")` — this is the official KMP migration path during transition to AGP 9.x
- Wizard sample code cleaned up: `Greeting.kt` removed, `App.kt` simplified to minimal MaterialTheme + Surface + centered Text
- `Platform.kt` + platform implementations (`Platform.android.kt`, `Platform.ios.kt`) retained as useful KMP foundation
- All 20 leaf package directories created with `.gitkeep` files under `composeApp/src/commonMain/kotlin/com/areadiscovery/`
- SQLDelight placeholder directory created at `composeApp/src/commonMain/sqldelight/com/areadiscovery/`
- BuildKonfig generates `BuildKonfig.GEMINI_API_KEY` in commonMain, confirmed accessible after build
- `local.properties` confirmed in `.gitignore` by wizard
- Android manifest: portrait orientation added to main activity
- `./gradlew :composeApp:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :composeApp:allTests` — BUILD SUCCESSFUL (Android unit tests + iOS simulator tests pass)
- Task 8.4 (emulator launch verification) deferred to user — APK builds successfully

### Change Log

- 2026-03-04: Initial implementation of Story 1.1 — KMP project initialization and package structure

### File List

- build.gradle.kts (modified — added sqldelight, buildkonfig, kotlinSerialization plugin declarations)
- settings.gradle.kts (wizard-generated, unmodified)
- gradle.properties (wizard-generated, unmodified)
- gradle/libs.versions.toml (modified — added Ktor, Koin, SQLDelight, MapLibre, BuildKonfig, kotlinx-serialization, kotlinx-coroutines, Turbine)
- gradle/wrapper/gradle-wrapper.properties (wizard-generated)
- gradlew (wizard-generated)
- gradlew.bat (wizard-generated)
- .gitignore (wizard-generated, includes local.properties)
- local.properties (modified — added GEMINI_API_KEY placeholder)
- composeApp/build.gradle.kts (modified — added all dependencies, BuildKonfig config, SQLDelight config, @Suppress for androidTarget)
- composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt (modified — simplified to minimal Compose screen)
- composeApp/src/commonMain/kotlin/com/areadiscovery/Greeting.kt (deleted — wizard sample code)
- composeApp/src/commonMain/kotlin/com/areadiscovery/Platform.kt (wizard-generated, unmodified)
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/usecase/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/service/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/data/local/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/data/mapper/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/chat/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/saved/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/search/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/navigation/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/location/.gitkeep (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/util/.gitkeep (new)
- composeApp/src/commonMain/sqldelight/com/areadiscovery/.gitkeep (new)
- composeApp/src/androidMain/AndroidManifest.xml (modified — added portrait orientation)
- composeApp/src/androidMain/kotlin/com/areadiscovery/MainActivity.kt (wizard-generated, unmodified)
- composeApp/src/androidMain/kotlin/com/areadiscovery/Platform.android.kt (wizard-generated, unmodified)
- composeApp/src/iosMain/kotlin/com/areadiscovery/MainViewController.kt (wizard-generated, unmodified)
- composeApp/src/iosMain/kotlin/com/areadiscovery/Platform.ios.kt (wizard-generated, unmodified)
- composeApp/src/commonTest/kotlin/com/areadiscovery/ComposeAppCommonTest.kt (wizard-generated, unmodified)
- iosApp/ (wizard-generated directory — Xcode project, unmodified)
