---
title: 'Localisation Phase A — pt-BR (Brazil Tester Gate)'
slug: 'localisation-pt-br'
created: '2026-03-14'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain + androidMain + iosMain)'
  - 'Compose Multiplatform 1.10.0 (UI in commonMain)'
  - 'Koin 4.x (DI — single{}/factory{}/viewModel{} in module blocks)'
  - 'compose-resources (org.jetbrains.compose.components:components-resources, already in build.gradle.kts line 94)'
  - 'kotlin.test + Turbine + UnconfinedTestDispatcher (unit tests in commonTest)'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/domain/service/AreaContextFactoryTest.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/GeocodingSearchBar.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/settings/SettingsSheet.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/settings/FeedbackPreviewSheet.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/OnboardingBubble.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/provider/LocaleProvider.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/domain/provider/LocaleProvider.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/harazone/domain/provider/LocaleProvider.ios.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeLocaleProvider.kt'
  - 'composeApp/src/commonMain/composeResources/values/strings.xml'
  - 'composeApp/src/commonMain/composeResources/values-pt-BR/strings.xml'
code_patterns:
  - 'LocaleProvider follows LocationProvider pattern: interface in commonMain, platform impls in androidMain/iosMain, registered single<LocaleProvider>{} in platformModule()'
  - 'AreaContextFactory is open class with open fun create() — subclassed by FakeAreaContextFactory which overrides create() entirely'
  - 'Koin DI: factory{} for AreaContextFactory, viewModel{} for ViewModels, single{} for platform services'
  - 'compose-resources: stringResource(Res.string.key) in composables; pluralStringResource(Res.plurals.key, count, count) for plurals'
  - 'FakeAreaContextFactory overrides create() entirely — base constructor args satisfy the signature but are not used at runtime'
  - 'Pill label translation at render layer: ChatOverlay.kt maps known pill.label values via when{} to stringResource()'
  - '"All" capsule sentinel must remain "All" in DistanceCapsule.label — translate display only at composable layer'
test_patterns:
  - 'kotlin.test + UnconfinedTestDispatcher (commonTest runs on all platforms)'
  - 'Fake objects in composeApp/src/commonTest/kotlin/com/harazone/fakes/'
  - 'AreaContextFactoryTest instantiates AreaContextFactory(fakeClock, FakeLocaleProvider()) directly'
  - 'ChatViewModelTest adds FakeLocaleProvider() as 5th arg to createViewModel()'
---

# Tech-Spec: Localisation Phase A — pt-BR (Brazil Tester Gate)

**Created:** 2026-03-14

## Overview

### Problem Statement

The app is hardcoded to English throughout. AI responses (area portraits, POI descriptions, chat) always come back in English regardless of the user's device language. All UI strings are hardcoded English literals with no translation infrastructure. A Brazilian tester with a Portuguese-language device sees English everywhere.

### Solution

(1) Introduce `LocaleProvider` (interface + Android/iOS impls, mirrors `LocationProvider` pattern) to read device OS locale; wire it into `AreaContextFactory` and `ChatViewModel` so Gemini prompts carry the user's language tag and AI responds in pt-BR on Portuguese devices. (2) Extract all user-facing UI strings to `composeResources/values/strings.xml` using Compose Multiplatform resources; add `composeResources/values-pt-BR/strings.xml` with Brazilian Portuguese translations.

### Scope

**In Scope:**
- `LocaleProvider` interface + Android/iOS implementations (`languageTag`, `isRtl`, `homeCurrencyCode`)
- `AreaContext` model — add `isRtl: Boolean = false` and `homeCurrencyCode: String = "USD"` fields
- `AreaContextFactory` wiring — populate `preferredLanguage`, `isRtl`, `homeCurrencyCode` from `LocaleProvider`
- `ChatViewModel` wiring — inject `LocaleProvider` as 5th constructor param; pass `languageTag` to `buildChatSystemContext`
- `GeminiPromptBuilder.buildChatSystemContext` — add `languageTag` param + language instruction block
- Compose-resources string extraction: all user-facing labels, placeholders, snackbars, error messages, status badges (Open/Busy/Closed), month abbreviations, chat pill labels, onboarding tips
- `values/strings.xml` (English default) + `values-pt-BR/strings.xml` (Brazilian Portuguese)
- Language rule: device OS locale only — no GPS-based override, no in-app picker
- Test updates: `AreaContextFactoryTest`, `ChatViewModelTest`, `FakeAreaContextFactory` constructor

**Out of Scope:**
- Dual-name POIs (`nameLocalized` field on POI model)
- In-app language picker
- Backend/server-side localisation
- Internal logs, debug strings, analytics event names
- RTL layout flipping (isRtl propagated through AreaContext but no layout direction changes in this spec)

## Context for Development

### Codebase Patterns

**LocaleProvider pattern — mirror `LocationProvider` exactly:**
- Interface in `commonMain/kotlin/com/harazone/domain/provider/LocaleProvider.kt`
- Android impl: `class AndroidLocaleProvider(context: Context) : LocaleProvider` in `androidMain/kotlin/com/harazone/domain/provider/LocaleProvider.android.kt`
- iOS impl: `class IosLocaleProvider : LocaleProvider` in `iosMain/kotlin/com/harazone/domain/provider/LocaleProvider.ios.kt`
- Registered as `single<LocaleProvider> { ... }` in `platformModule()` on both platforms (same file that registers `LocationProvider`)

**Koin wiring changes:**
- `UiModule.kt` line 11: `factory { AreaContextFactory(get()) }` → `factory { AreaContextFactory(get(), get()) }`
- `UiModule.kt` line 13: `viewModel { ChatViewModel(get(), get(), get(), get()) }` → `viewModel { ChatViewModel(get(), get(), get(), get(), get()) }`

**AreaContextFactory current state:** `preferredLanguage = "en"` hardcoded at line 14. Constructor is `open class AreaContextFactory(private val clock: AppClock)`.

**GeminiPromptBuilder.buildChatSystemContext:** Currently has NO language instruction. `buildEnrichmentPrompt` and `buildAreaPortraitPrompt` already use `context.preferredLanguage` so those prompts work once `AreaContextFactory` is fixed. Chat is the only prompt that needs the explicit language block.

**Pill label translation at render layer:** `ContextualPill.label` is a plain string set in `ChatViewModel`. To avoid making ViewModel code suspend-aware or changing the `ContextualPill` data class, translate pill labels in `ChatOverlay.kt` using a `when` expression that maps the known English label strings to `stringResource()` equivalents. Unrecognised labels fall through as-is.

**"All" capsule sentinel:** `DistanceCapsule.label == "All"` is used as a filter sentinel in `SavedPlacesViewModel` (lines 177 and 218). Do NOT change the raw label value. In `SavedPlacesScreen.kt` composable, when rendering the chip display text, check `if (capsule.label == "All") stringResource(Res.string.filter_all) else capsule.label`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/location/LocationProvider.kt` | Pattern to copy for LocaleProvider interface |
| `composeApp/src/androidMain/kotlin/com/harazone/location/AndroidLocationProvider.kt` | Pattern for Android platform impl |
| `composeApp/src/iosMain/kotlin/com/harazone/location/IosLocationProvider.kt` | Pattern for iOS platform impl |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt` | Add `isRtl` + `homeCurrencyCode` fields |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt` | Inject LocaleProvider, populate 3 locale fields |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | Add `languageTag` param + `languageBlock` to `buildChatSystemContext` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | Add LocaleProvider 5th param; pass `localeProvider.languageTag` at line 151 |
| `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt` | Update AreaContextFactory + ChatViewModel `get()` counts |
| `composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt` | Register `AndroidLocaleProvider` |
| `composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt` | Register `IosLocaleProvider` |
| `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt` | Update base constructor call |
| `composeApp/src/commonTest/kotlin/com/harazone/domain/service/AreaContextFactoryTest.kt` | Update constructor instantiation at line 10 |
| `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt` | Add `FakeLocaleProvider()` as 5th arg to `createViewModel()` |

### Technical Decisions

1. **Language follows device OS locale, not GPS.** `LocaleProvider` reads `Locale.getDefault()` (Android) / `NSLocale.currentLocale` (iOS). No GPS check, no override.
2. **Pill LABELS translated at render layer; pill MESSAGES stay English.** Messages are AI queries — Gemini responds in Portuguese based on the system context language instruction regardless of query language.
3. **"All" capsule: translate display only, not sentinel.** Filter logic uses raw string sentinel `"All"` in ViewModel.
4. **`buildChatSystemContext` language block is a no-op for "en"** — empty string returned for English, preserving exact existing behaviour.
5. **`FakeLocaleProvider` in its own file** in `commonTest/fakes/` for reuse across all tests.
6. **No expect/actual for LocaleProvider** — plain interface + concrete platform classes registered via Koin.
7. **`poi_card_hours` uses string formatting:** `"Hours: %s"` → `stringResource(Res.string.poi_card_hours, poi.hours)` using compose-resources format arg support.
8. **Month abbreviations:** Replace `val months = arrayOf(...)` with a `@Composable` helper `localizedMonthAbbrev(index: Int): String` in `SavedPoiCard.kt` that calls 12 individual string resources.
9. **`saved_places_nearby` uses plurals:** `pluralStringResource(Res.plurals.saved_places_nearby, count, count)` replaces the inline if/else plural.

## Implementation Plan

### Tasks

**PART 1 — AI Locale Injection (implement first; establishes the provider all Part 2 composables can use)**

- [ ] Task 1: Create `LocaleProvider` interface
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/LocaleProvider.kt`
  - Action: Create new file. Package `com.harazone.domain.provider`. Define:
    ```kotlin
    interface LocaleProvider {
        val languageTag: String       // e.g. "pt-BR", "en", "ar"
        val isRtl: Boolean
        val homeCurrencyCode: String  // e.g. "BRL", "USD"
    }
    ```

- [ ] Task 2: Create `AndroidLocaleProvider`
  - File: `composeApp/src/androidMain/kotlin/com/harazone/domain/provider/LocaleProvider.android.kt`
  - Action: Create new file. Package `com.harazone.domain.provider`. Imports: `android.content.Context`, `android.os.LocaleList`, `android.text.TextUtils`, `android.view.View`, `java.util.Currency`, `java.util.Locale`.
    ```kotlin
    class AndroidLocaleProvider(private val context: Context) : LocaleProvider {
        override val languageTag: String
            get() = Locale.getDefault().toLanguageTag()
        override val isRtl: Boolean
            get() = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
        override val homeCurrencyCode: String
            get() = try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (e: Exception) { "USD" }
    }
    ```

- [ ] Task 3: Create `IosLocaleProvider`
  - File: `composeApp/src/iosMain/kotlin/com/harazone/domain/provider/LocaleProvider.ios.kt`
  - Action: Create new file. Package `com.harazone.domain.provider`. Imports: `platform.Foundation.NSLocale`, `platform.Foundation.NSLocaleLanguageDirectionRightToLeft`, `platform.Foundation.currentLocale`, `platform.Foundation.characterDirectionForLanguage`, `platform.Foundation.languageCode`, `platform.Foundation.localeIdentifier`, `platform.Foundation.currencyCode`.
    ```kotlin
    class IosLocaleProvider : LocaleProvider {
        override val languageTag: String
            get() = NSLocale.currentLocale.localeIdentifier.replace("_", "-")
        override val isRtl: Boolean
            get() {
                val lang = NSLocale.currentLocale.languageCode ?: return false
                return NSLocale.characterDirectionForLanguage(lang) == NSLocaleLanguageDirectionRightToLeft
            }
        override val homeCurrencyCode: String
            get() = NSLocale.currentLocale.currencyCode ?: "USD"
    }
    ```

- [ ] Task 4: Create `FakeLocaleProvider`
  - File: `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeLocaleProvider.kt`
  - Action: Create new file. Package `com.harazone.fakes`. Import `com.harazone.domain.provider.LocaleProvider`.
    ```kotlin
    class FakeLocaleProvider(
        override val languageTag: String = "en",
        override val isRtl: Boolean = false,
        override val homeCurrencyCode: String = "USD",
    ) : LocaleProvider
    ```

- [ ] Task 5: Update `AreaContext` — add locale fields
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt`
  - Action: Add two fields with defaults to the `data class`:
    ```kotlin
    data class AreaContext(
        val timeOfDay: String,
        val dayOfWeek: String,
        val visitCount: Int,
        val preferredLanguage: String,
        val isNewUser: Boolean = false,
        val isRtl: Boolean = false,           // ADD
        val homeCurrencyCode: String = "USD", // ADD
    )
    ```

- [ ] Task 6: Update `AreaContextFactory` — inject `LocaleProvider`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt`
  - Action: Add `LocaleProvider` as 2nd constructor param; use it in `create()`. Replace hardcoded `"en"` with `localeProvider.languageTag`. Add `isRtl` and `homeCurrencyCode` from provider.
    ```kotlin
    import com.harazone.domain.provider.LocaleProvider

    open class AreaContextFactory(
        private val clock: AppClock,
        private val localeProvider: LocaleProvider,
    ) {
        open fun create(): AreaContext {
            val nowMs = clock.nowMs()
            return AreaContext(
                timeOfDay = resolveTimeOfDay(nowMs),
                dayOfWeek = resolveDayOfWeek(nowMs),
                visitCount = 0,
                preferredLanguage = localeProvider.languageTag,
                isRtl = localeProvider.isRtl,
                homeCurrencyCode = localeProvider.homeCurrencyCode,
            )
        }
        // resolveTimeOfDay and resolveDayOfWeek unchanged
    }
    ```

- [ ] Task 7: Update `FakeAreaContextFactory` — update base constructor call
  - File: `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt`
  - Action: Add import for `FakeLocaleProvider`. Change base constructor call from `AreaContextFactory(FakeClock())` to `AreaContextFactory(FakeClock(), FakeLocaleProvider())`. No other changes — `create()` override is unchanged.
    ```kotlin
    import com.harazone.fakes.FakeLocaleProvider
    // ...
    ) : AreaContextFactory(FakeClock(), FakeLocaleProvider()) {
    ```

- [ ] Task 8: Update `AreaContextFactoryTest` — fix instantiation
  - File: `composeApp/src/commonTest/kotlin/com/harazone/domain/service/AreaContextFactoryTest.kt`
  - Action: Add `import com.harazone.fakes.FakeLocaleProvider`. Change line 10 from `AreaContextFactory(fakeClock)` to `AreaContextFactory(fakeClock, FakeLocaleProvider())`. Update the `preferredLanguage defaults to en` test (line 128–129): assertion `assertEquals("en", ...)` remains correct since `FakeLocaleProvider().languageTag == "en"` by default.

- [ ] Task 9: Register `AndroidLocaleProvider` in Koin
  - File: `composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt`
  - Action: Add import `com.harazone.domain.provider.AndroidLocaleProvider` and `com.harazone.domain.provider.LocaleProvider`. Add inside `platformModule()`:
    ```kotlin
    single<LocaleProvider> { AndroidLocaleProvider(androidContext()) }
    ```

- [ ] Task 10: Register `IosLocaleProvider` in Koin
  - File: `composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt`
  - Action: Add import `com.harazone.domain.provider.IosLocaleProvider` and `com.harazone.domain.provider.LocaleProvider`. Add inside `platformModule()`:
    ```kotlin
    single<LocaleProvider> { IosLocaleProvider() }
    ```

- [ ] Task 11: Update `UiModule` — add extra `get()` to two registrations
  - File: `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt`
  - Action: Two line changes:
    - Line 11: `factory { AreaContextFactory(get()) }` → `factory { AreaContextFactory(get(), get()) }`
    - Line 13: `viewModel { ChatViewModel(get(), get(), get(), get()) }` → `viewModel { ChatViewModel(get(), get(), get(), get(), get()) }`

- [ ] Task 12: Update `GeminiPromptBuilder` — add language instruction to chat system context
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Add `languageTag: String = "en"` as last param of `buildChatSystemContext` (after `activeVibeName`). Add `languageBlock(languageTag)` as the last entry in the `listOf(...)`. Add private function:
    ```kotlin
    private fun languageBlock(languageTag: String): String =
        if (languageTag == "en") ""
        else "LANGUAGE RULE: You MUST respond ONLY in the language identified by locale '$languageTag'. Every word of your response must be in that language."
    ```

- [ ] Task 13: Update `ChatViewModel` — inject `LocaleProvider`, pass `languageTag`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action (2 changes):
    1. Add `private val localeProvider: LocaleProvider` as 5th constructor parameter. Add import `com.harazone.domain.provider.LocaleProvider`.
    2. At line 151, in the `buildChatSystemContext(...)` call, add `languageTag = localeProvider.languageTag` as a named argument after `activeVibeName`.

- [ ] Task 14: Update `ChatViewModelTest` — add `FakeLocaleProvider` 5th arg
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt`
  - Action: Add `import com.harazone.fakes.FakeLocaleProvider`. In `createViewModel()`, add `localeProvider = FakeLocaleProvider()` as 5th named argument to the `ChatViewModel(...)` constructor call.

---

**PART 2 — UI String Resources**

- [ ] Task 15: Create `values/strings.xml` — English defaults
  - File: `composeApp/src/commonMain/composeResources/values/strings.xml`
  - Action: Create directory `composeResources/values/` and file with all string resources below.
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <!-- Search / Map -->
        <string name="search_placeholder">Search a place or refresh area</string>
        <string name="search_field_placeholder">Search a place…</string>
        <string name="search_clear_history">Clear history</string>
        <string name="search_refreshing">Refreshing area…</string>
        <string name="poi_list_empty">No places found for this area</string>

        <!-- Chat Overlay -->
        <string name="chat_input_hint">Ask about this area</string>
        <string name="chat_input_placeholder">Ask a question…</string>
        <string name="chat_return_title">Welcome back!</string>
        <string name="chat_return_message">Your earlier conversation has been paused. Pick up where you left off, or start fresh?</string>
        <string name="chat_return_continue">Continue</string>
        <string name="chat_return_start_fresh">Start Fresh</string>
        <string name="chat_suggestion_hint">Tap a suggestion below to start exploring</string>
        <string name="chat_poi_save">Save</string>
        <string name="chat_poi_saved">Saved</string>
        <string name="chat_poi_directions">Directions</string>
        <string name="chat_poi_show_on_map">📍 Show on Map</string>

        <!-- POI Card -->
        <string name="poi_card_loading">Looking…</string>
        <string name="poi_card_save">Save</string>
        <string name="poi_card_saved">Saved</string>
        <string name="poi_card_share">Share</string>
        <string name="poi_card_directions">Directions</string>
        <string name="poi_card_ask_ai">Ask AI</string>
        <string name="poi_card_hours">Hours: %s</string>
        <string name="poi_card_vibe_insights">Vibe Insights</string>
        <string name="poi_card_more_details">More details</string>
        <string name="poi_card_less_details">Less</string>
        <string name="poi_status_open">Open</string>
        <string name="poi_status_busy">Busy</string>
        <string name="poi_status_closed">Closed</string>
        <string name="poi_status_unknown">Unknown</string>

        <!-- Vibe Rail -->
        <string name="vibe_rail_saved">Saved</string>
        <string name="vibe_rail_offline">Offline · cached</string>
        <string name="vibe_rail_exploring">Exploring…</string>

        <!-- Map Screen -->
        <string name="map_retry">Retry</string>
        <string name="map_no_maps_app">No maps app available</string>
        <string name="map_sharing_coming_soon">Sharing coming soon</string>
        <plurals name="saved_places_nearby">
            <item quantity="one">%d saved place nearby</item>
            <item quantity="other">%d saved places nearby</item>
        </plurals>

        <!-- Saved Places -->
        <string name="saved_places_title">Saved Places</string>
        <string name="saved_no_matches">No matching saves</string>
        <string name="saved_removed">Removed</string>
        <string name="saved_undo">Undo</string>
        <string name="saved_map_button">Map</string>
        <string name="filter_all">All</string>
        <string name="note_placeholder">Add a note…</string>

        <!-- Month abbreviations -->
        <string name="month_jan">Jan</string>
        <string name="month_feb">Feb</string>
        <string name="month_mar">Mar</string>
        <string name="month_apr">Apr</string>
        <string name="month_may">May</string>
        <string name="month_jun">Jun</string>
        <string name="month_jul">Jul</string>
        <string name="month_aug">Aug</string>
        <string name="month_sep">Sep</string>
        <string name="month_oct">Oct</string>
        <string name="month_nov">Nov</string>
        <string name="month_dec">Dec</string>

        <!-- Settings / Feedback -->
        <string name="settings_version">Version</string>
        <string name="settings_send_feedback">Send Feedback</string>
        <string name="feedback_screenshot_captured">Screenshot captured ✓</string>
        <string name="feedback_description_placeholder">Describe the issue…</string>
        <string name="feedback_cancel">Cancel</string>
        <string name="feedback_send_report">Send Report</string>

        <!-- Onboarding -->
        <string name="onboarding_tip1_title">Tap pins</string>
        <string name="onboarding_tip1_body">to discover what makes each place special</string>
        <string name="onboarding_tip2_title">Save places</string>
        <string name="onboarding_tip2_body">you like — I\'ll learn your taste and recommend better spots</string>
        <string name="onboarding_tip3_title">Tap vibes</string>
        <string name="onboarding_tip3_body">on the right to filter by what excites you</string>
        <string name="onboarding_tip4_title">Chat with me</string>
        <string name="onboarding_tip4_body">anytime — ask about hidden gems, safety, food, anything</string>
        <string name="onboarding_footer">The more you explore, the smarter I get</string>

        <!-- Chat Pill Labels -->
        <string name="pill_new_topic">New topic</string>
        <string name="pill_areas_to_avoid">Areas to avoid</string>
        <string name="pill_best_time">Best time to go</string>
        <string name="pill_tell_me_more">Tell me more</string>
        <string name="pill_surprise_me">Surprise me</string>
        <string name="pill_best_food">Best food right now</string>
        <string name="pill_hidden_gems">Show me hidden gems</string>
        <string name="pill_get_outside">Get me outside</string>
        <string name="pill_day_trip">Plan a day trip from my saves</string>
        <string name="pill_find_patterns">Find patterns in my saves</string>
    </resources>
    ```

- [ ] Task 16: Create `values-pt-BR/strings.xml` — Brazilian Portuguese translations
  - File: `composeApp/src/commonMain/composeResources/values-pt-BR/strings.xml`
  - Action: Create directory `composeResources/values-pt-BR/` and file with all pt-BR translations.
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <!-- Search / Map -->
        <string name="search_placeholder">Procurar um lugar ou atualizar área</string>
        <string name="search_field_placeholder">Procurar um lugar…</string>
        <string name="search_clear_history">Limpar histórico</string>
        <string name="search_refreshing">Atualizando área…</string>
        <string name="poi_list_empty">Nenhum lugar encontrado nesta área</string>

        <!-- Chat Overlay -->
        <string name="chat_input_hint">Pergunte sobre esta área</string>
        <string name="chat_input_placeholder">Faça uma pergunta…</string>
        <string name="chat_return_title">Bem-vindo(a) de volta!</string>
        <string name="chat_return_message">Sua conversa anterior foi pausada. Continue de onde parou, ou comece do zero?</string>
        <string name="chat_return_continue">Continuar</string>
        <string name="chat_return_start_fresh">Começar do Zero</string>
        <string name="chat_suggestion_hint">Toque em uma sugestão abaixo para começar a explorar</string>
        <string name="chat_poi_save">Salvar</string>
        <string name="chat_poi_saved">Salvo</string>
        <string name="chat_poi_directions">Rotas</string>
        <string name="chat_poi_show_on_map">📍 Ver no Mapa</string>

        <!-- POI Card -->
        <string name="poi_card_loading">Buscando…</string>
        <string name="poi_card_save">Salvar</string>
        <string name="poi_card_saved">Salvo</string>
        <string name="poi_card_share">Compartilhar</string>
        <string name="poi_card_directions">Rotas</string>
        <string name="poi_card_ask_ai">Perguntar à IA</string>
        <string name="poi_card_hours">Horário: %s</string>
        <string name="poi_card_vibe_insights">Características do Vibe</string>
        <string name="poi_card_more_details">Mais detalhes</string>
        <string name="poi_card_less_details">Menos</string>
        <string name="poi_status_open">Aberto</string>
        <string name="poi_status_busy">Movimentado</string>
        <string name="poi_status_closed">Fechado</string>
        <string name="poi_status_unknown">Desconhecido</string>

        <!-- Vibe Rail -->
        <string name="vibe_rail_saved">Salvos</string>
        <string name="vibe_rail_offline">Offline · salvo</string>
        <string name="vibe_rail_exploring">Explorando…</string>

        <!-- Map Screen -->
        <string name="map_retry">Tentar novamente</string>
        <string name="map_no_maps_app">Nenhum aplicativo de mapas disponível</string>
        <string name="map_sharing_coming_soon">Compartilhamento em breve</string>
        <plurals name="saved_places_nearby">
            <item quantity="one">%d lugar salvo próximo</item>
            <item quantity="other">%d lugares salvos próximos</item>
        </plurals>

        <!-- Saved Places -->
        <string name="saved_places_title">Lugares Salvos</string>
        <string name="saved_no_matches">Nenhuma correspondência</string>
        <string name="saved_removed">Removido</string>
        <string name="saved_undo">Desfazer</string>
        <string name="saved_map_button">Mapa</string>
        <string name="filter_all">Todos</string>
        <string name="note_placeholder">Adicionar uma nota…</string>

        <!-- Month abbreviations -->
        <string name="month_jan">jan</string>
        <string name="month_feb">fev</string>
        <string name="month_mar">mar</string>
        <string name="month_apr">abr</string>
        <string name="month_may">mai</string>
        <string name="month_jun">jun</string>
        <string name="month_jul">jul</string>
        <string name="month_aug">ago</string>
        <string name="month_sep">set</string>
        <string name="month_oct">out</string>
        <string name="month_nov">nov</string>
        <string name="month_dec">dez</string>

        <!-- Settings / Feedback -->
        <string name="settings_version">Versão</string>
        <string name="settings_send_feedback">Enviar Feedback</string>
        <string name="feedback_screenshot_captured">Captura de tela feita ✓</string>
        <string name="feedback_description_placeholder">Descreva o problema…</string>
        <string name="feedback_cancel">Cancelar</string>
        <string name="feedback_send_report">Enviar Relatório</string>

        <!-- Onboarding -->
        <string name="onboarding_tip1_title">Toque nos pinos</string>
        <string name="onboarding_tip1_body">para descobrir o que torna cada lugar especial</string>
        <string name="onboarding_tip2_title">Salve lugares</string>
        <string name="onboarding_tip2_body">que você gostou — vou aprender seu gosto e recomendar lugares melhores</string>
        <string name="onboarding_tip3_title">Toque nos vibes</string>
        <string name="onboarding_tip3_body">à direita para filtrar pelo que te anima</string>
        <string name="onboarding_tip4_title">Converse comigo</string>
        <string name="onboarding_tip4_body">a qualquer hora — pergunte sobre joias escondidas, segurança, comida, qualquer coisa</string>
        <string name="onboarding_footer">Quanto mais você explora, mais inteligente fico</string>

        <!-- Chat Pill Labels -->
        <string name="pill_new_topic">Novo assunto</string>
        <string name="pill_areas_to_avoid">Áreas a evitar</string>
        <string name="pill_best_time">Melhor hora para ir</string>
        <string name="pill_tell_me_more">Conta mais</string>
        <string name="pill_surprise_me">Me surpreenda</string>
        <string name="pill_best_food">Melhor comida agora</string>
        <string name="pill_hidden_gems">Joias escondidas</string>
        <string name="pill_get_outside">Sair para explorar</string>
        <string name="pill_day_trip">Planejar um passeio com meus salvos</string>
        <string name="pill_find_patterns">Padrões nos meus salvos</string>
    </resources>
    ```

- [ ] Task 17: Extract strings from `GeocodingSearchBar.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/GeocodingSearchBar.kt`
  - Action: Add `import org.jetbrains.compose.resources.stringResource` and `import com.harazone.composeapp.generated.resources.*`. Replace:
    - `"Search a place or refresh area"` (line 185) → `stringResource(Res.string.search_placeholder)`
    - `"Search a place…"` (line 270) → `stringResource(Res.string.search_field_placeholder)`
    - `"Clear history"` (line 325) → `stringResource(Res.string.search_clear_history)`
    - `"Refreshing area…"` (line 555) → `stringResource(Res.string.search_refreshing)`

- [ ] Task 18: Extract strings from `POIListView.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt`
  - Action: Add `stringResource` import. Replace:
    - `"No places found for this area"` (line 95) → `stringResource(Res.string.poi_list_empty)`

- [ ] Task 19: Extract strings from `ChatOverlay.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt`
  - Action: Add `stringResource` import and `pluralStringResource` if needed. Replace:
    - `"Ask about this area"` (line 150) → `stringResource(Res.string.chat_input_hint)`
    - `"Welcome back!"` (line 177) → `stringResource(Res.string.chat_return_title)`
    - `"Your earlier conversation has been paused..."` (line 178) → `stringResource(Res.string.chat_return_message)`
    - `"Continue"` (line 180) → `stringResource(Res.string.chat_return_continue)`
    - `"Start Fresh"` (line 183) → `stringResource(Res.string.chat_return_start_fresh)`
    - Both occurrences of `"Tap a suggestion below to start exploring"` (lines 206, 326) → `stringResource(Res.string.chat_suggestion_hint)`
    - `if (isSaved) "Saved" else "Save"` (line 476) → `if (isSaved) stringResource(Res.string.chat_poi_saved) else stringResource(Res.string.chat_poi_save)`
    - `"Directions"` (line 498) → `stringResource(Res.string.chat_poi_directions)`
    - `"📍 Show on Map"` (line 508) → `stringResource(Res.string.chat_poi_show_on_map)`
    - `"Ask a question..."` (line 645) → `stringResource(Res.string.chat_input_placeholder)`
    - **Pill label mapping** — in the composable where `pill.label` is displayed, introduce a helper val:
      ```kotlin
      val pillDisplayLabel = when (pill.label) {
          "New topic"                    -> stringResource(Res.string.pill_new_topic)
          "Areas to avoid"               -> stringResource(Res.string.pill_areas_to_avoid)
          "Best time to go"              -> stringResource(Res.string.pill_best_time)
          "Tell me more"                 -> stringResource(Res.string.pill_tell_me_more)
          "Surprise me"                  -> stringResource(Res.string.pill_surprise_me)
          "Best food right now"          -> stringResource(Res.string.pill_best_food)
          "Show me hidden gems"          -> stringResource(Res.string.pill_hidden_gems)
          "Get me outside"               -> stringResource(Res.string.pill_get_outside)
          "Plan a day trip from my saves"-> stringResource(Res.string.pill_day_trip)
          "Find patterns in my saves"    -> stringResource(Res.string.pill_find_patterns)
          else                           -> pill.label
      }
      ```
      Replace `pill.label` with `pillDisplayLabel` in the chip label lambdas (lines 268 and 279).

- [ ] Task 20: Extract strings from `ExpandablePoiCard.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt`
  - Action: Add `stringResource` import. Replace:
    - `"Looking..."` (line 224) → `stringResource(Res.string.poi_card_loading)`
    - `if (isSaved) "Saved" else "Save"` (line 343) → `if (isSaved) stringResource(Res.string.poi_card_saved) else stringResource(Res.string.poi_card_save)`
    - `"Share"` (line 356) → `stringResource(Res.string.poi_card_share)`
    - `"Directions"` (line 366) → `stringResource(Res.string.poi_card_directions)`
    - `"Ask AI"` (line 376) → `stringResource(Res.string.poi_card_ask_ai)`
    - `"Hours: ${poi.hours}"` (line 401) → `stringResource(Res.string.poi_card_hours, poi.hours)`
    - `"Vibe Insights"` (line 409) → `stringResource(Res.string.poi_card_vibe_insights)`
    - `if (expanded) "Less" else "More details"` (line 440) → `if (expanded) stringResource(Res.string.poi_card_less_details) else stringResource(Res.string.poi_card_more_details)`
    - Status badge string pairs (lines 451–453, 475–478): replace `"Open"`, `"Busy"`, `"Closed"`, `"Unknown"` with calls to `stringResource(Res.string.poi_status_open)` etc. Since these are in `when` expressions inside a `@Composable`, use local vals:
      ```kotlin
      val statusOpen   = stringResource(Res.string.poi_status_open)
      val statusBusy   = stringResource(Res.string.poi_status_busy)
      val statusClosed = stringResource(Res.string.poi_status_closed)
      val statusUnknown = stringResource(Res.string.poi_status_unknown)
      ```
      Then reference those vals in the `when` expressions.

- [ ] Task 21: Extract strings from `VibeRail.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt`
  - Action: Add `stringResource` import. Replace:
    - `"Offline · cached"` (line 152) → `stringResource(Res.string.vibe_rail_offline)`
    - `"Exploring..."` (line 306) → `stringResource(Res.string.vibe_rail_exploring)`
    - `"Saved"` (line 366) → `stringResource(Res.string.vibe_rail_saved)`

- [ ] Task 22: Extract strings from `MapScreen.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action: Add `stringResource` and `pluralStringResource` imports. Replace:
    - `Text("Retry")` (line 122) → `Text(stringResource(Res.string.map_retry))`
    - `"No maps app available"` (lines 441, 581) → `stringResource(Res.string.map_no_maps_app)` (both occurrences)
    - `"Sharing coming soon"` (line 464) → `stringResource(Res.string.map_sharing_coming_soon)`
    - `"$count saved place${if (count == 1) "" else "s"} nearby"` (line 742) → `pluralStringResource(Res.plurals.saved_places_nearby, count, count)`

- [ ] Task 23: Extract strings from `SavedPlacesScreen.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt`
  - Action: Add `stringResource` import. Replace:
    - `"Saved Places"` (line 126) → `stringResource(Res.string.saved_places_title)`
    - **"All" capsule display** (lines 211, 216): change to `if (capsule.label == "All") stringResource(Res.string.filter_all) else capsule.label` — do NOT change the sentinel comparison logic
    - `"No matching saves"` (line 299) → `stringResource(Res.string.saved_no_matches)`
    - `"Removed"` (line 322) → `stringResource(Res.string.saved_removed)`
    - `"Undo"` (line 323) → `stringResource(Res.string.saved_undo)`
    - `"Map"` (line 380) → `stringResource(Res.string.saved_map_button)`

- [ ] Task 24: Extract strings from `SavedPoiCard.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt`
  - Action: Add `stringResource` import. Replace:
    - `"Add a note…"` (line 207) → `stringResource(Res.string.note_placeholder)`
    - Replace `val months = arrayOf("Jan", "Feb", ...)` (line 313) with a `@Composable` helper or inline the resources. Use a local val inside the composable:
      ```kotlin
      val monthAbbrevs = listOf(
          stringResource(Res.string.month_jan), stringResource(Res.string.month_feb),
          stringResource(Res.string.month_mar), stringResource(Res.string.month_apr),
          stringResource(Res.string.month_may), stringResource(Res.string.month_jun),
          stringResource(Res.string.month_jul), stringResource(Res.string.month_aug),
          stringResource(Res.string.month_sep), stringResource(Res.string.month_oct),
          stringResource(Res.string.month_nov), stringResource(Res.string.month_dec),
      )
      ```
      Replace all references to `months[...]` with `monthAbbrevs[...]`.

- [ ] Task 25: Extract strings from `SettingsSheet.kt` and `FeedbackPreviewSheet.kt`
  - Files: both settings files
  - Action: Add `stringResource` import to each. Replace:
    - `SettingsSheet.kt`: `"Version"` → `stringResource(Res.string.settings_version)`, `"Send Feedback"` → `stringResource(Res.string.settings_send_feedback)`
    - `FeedbackPreviewSheet.kt`: `"Screenshot captured ✓"` → `stringResource(Res.string.feedback_screenshot_captured)`, `"Describe the issue..."` → `stringResource(Res.string.feedback_description_placeholder)`, `"Cancel"` → `stringResource(Res.string.feedback_cancel)`, `"Send Report"` → `stringResource(Res.string.feedback_send_report)`

- [ ] Task 26: Extract strings from `OnboardingBubble.kt`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/OnboardingBubble.kt`
  - Action: Add `stringResource` import. The four `OnboardingTip(emoji, title, body)` objects are currently constructed outside a composable scope (as top-level vals or inside init). Move tip construction inside the composable (or use `@Composable` function), then replace:
    - `"Tap pins"` → `stringResource(Res.string.onboarding_tip1_title)`, `"to discover..."` → `stringResource(Res.string.onboarding_tip1_body)`
    - `"Save places"` → `stringResource(Res.string.onboarding_tip2_title)`, `"you like..."` → `stringResource(Res.string.onboarding_tip2_body)`
    - `"Tap vibes"` → `stringResource(Res.string.onboarding_tip3_title)`, `"on the right..."` → `stringResource(Res.string.onboarding_tip3_body)`
    - `"Chat with me"` → `stringResource(Res.string.onboarding_tip4_title)`, `"anytime..."` → `stringResource(Res.string.onboarding_tip4_body)`
    - `footer_text = "The more you explore..."` → `stringResource(Res.string.onboarding_footer)`

---

**PART 3 — Tests**

- [ ] Task 27: Write new locale wiring tests
  - File: `composeApp/src/commonTest/kotlin/com/harazone/domain/service/AreaContextFactoryTest.kt`
  - Action: Add new tests at the bottom of the existing test class:
    1. `fun 'LocaleProvider languageTag flows through to preferredLanguage'` — create `AreaContextFactory(fakeClock, FakeLocaleProvider("pt-BR"))`, assert `create().preferredLanguage == "pt-BR"`
    2. `fun 'LocaleProvider isRtl flows through'` — `FakeLocaleProvider(isRtl = true)`, assert `create().isRtl == true`
    3. `fun 'LocaleProvider homeCurrencyCode flows through'` — `FakeLocaleProvider(homeCurrencyCode = "BRL")`, assert `create().homeCurrencyCode == "BRL"`

- [ ] Task 28: Write new `GeminiPromptBuilder` language block tests
  - File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiPromptBuilderTest.kt`
  - Action: Add new tests:
    1. `fun 'buildChatSystemContext with pt-BR languageTag includes LANGUAGE RULE'` — call `buildChatSystemContext(..., languageTag = "pt-BR")`, assert result contains `"LANGUAGE RULE"` and `"pt-BR"`
    2. `fun 'buildChatSystemContext with en languageTag omits LANGUAGE RULE'` — call with `languageTag = "en"`, assert result does NOT contain `"LANGUAGE RULE"`

### Acceptance Criteria

- [ ] AC 1: Given device OS locale is "pt-BR", when app loads and AI generates an area portrait, then the portrait text is in Brazilian Portuguese.
- [ ] AC 2: Given device OS locale is "pt-BR", when user taps a chat pill and AI responds, then the chat response is in Brazilian Portuguese.
- [ ] AC 3: Given device OS locale is "en", when AI generates any content (portrait, enrichment, chat), then content is in English — no regression.
- [ ] AC 4: Given `FakeLocaleProvider("pt-BR", homeCurrencyCode = "BRL")`, when `AreaContextFactory.create()` is called, then `preferredLanguage == "pt-BR"` and `homeCurrencyCode == "BRL"`.
- [ ] AC 5: Given `buildChatSystemContext(languageTag = "pt-BR")`, when the output string is inspected, then it contains `"LANGUAGE RULE"` and `"pt-BR"`.
- [ ] AC 6: Given `buildChatSystemContext(languageTag = "en")`, when the output string is inspected, then it does NOT contain `"LANGUAGE RULE"`.
- [ ] AC 7: Given device OS locale is "pt-BR", when user opens the Saved Places screen, then the title reads "Lugares Salvos".
- [ ] AC 8: Given device OS locale is "pt-BR", when a POI card shows status "open", then the badge displays "Aberto".
- [ ] AC 9: Given device OS locale is "pt-BR", when chat overlay shows the "Best food right now" pill, then the pill label displays "Melhor comida agora".
- [ ] AC 10: Given device OS locale is "en", when any UI screen is viewed, then all strings remain in English — no regression.
- [ ] AC 11: Given `DistanceCapsule.label == "All"`, when rendered in the filter chip in pt-BR, then chip displays "Todos" — and the underlying filter logic (`capsule.label == "All"`) still works correctly.
- [ ] AC 12: Given 1 saved place nearby in pt-BR, when the count badge is shown, then it reads "1 lugar salvo próximo" (singular). Given 3 saved places, then "3 lugares salvos próximos" (plural).
- [ ] AC 13: Given device OS locale is "pt-BR", when user views a saved card date with month index 1 (February), then "fev" is displayed.

## Additional Context

### Dependencies

- `compose-resources` library already declared in `build.gradle.kts` line 94 — no new Gradle dependency needed.
- `LocaleProvider` must be registered in `platformModule()` before `UiModule` bindings resolve — Koin lazy resolution handles this automatically.
- Phase A spec (`tech-spec-localization-phase-a.md`) — archive after this spec ships. This spec supersedes it.

### Testing Strategy

**Unit tests (commonTest — run on all platforms via `./gradlew :composeApp:allTests`):**
- `AreaContextFactoryTest` — 3 new tests (Tasks 27a/b/c)
- `GeminiPromptBuilderTest` — 2 new tests (Task 28a/b)

**Manual smoke tests (after deploy):**
1. Set device/simulator locale to "pt-BR" in OS settings
2. Launch app → verify area portrait + POI descriptions come back in Portuguese
3. Open chat → tap any pill → verify AI response is in Portuguese
4. Open Saved Places → verify "Lugares Salvos" title, "Todos" filter chip
5. Check POI card status badge → "Aberto" / "Fechado"
6. Set device locale back to "en" → verify all English strings unchanged

**Regression guard:** All existing `AreaContextFactoryTest` time-of-day and day-of-week tests must still pass after Task 8.

### Notes

- **Supersedes `tech-spec-localization-phase-a.md`** — archive it by renaming to `tech-spec-localization-phase-a-archived-2026-03-14.md` before starting implementation.
- **Spanish / Arabic:** Adding a new language after this ships = 1 new `values-{locale}/strings.xml` file + Gemini handles AI automatically. Arabic additionally needs an RTL layout pass (deferred — `isRtl` already flows through `AreaContext` once Task 6 ships).
- **v1.1 — In-app language picker:** `LocaleProvider` is the single injection point, so adding a user override is low effort. Steps: (1) persist chosen locale in `UserPreferencesRepository` (DataStore), (2) `LocaleProvider` checks persisted value first, falls back to device locale if null, (3) wrap app root in `CompositionLocalProvider` with custom `ResourceEnvironment` to override compose-resources locale independently of OS. Hardest part is step 3. Estimated ~1 day.
- **`OnboardingBubble.kt` tips location:** Confirm whether tip objects are constructed at top-level or inside a composable before Task 26. If at top-level (outside `@Composable`), move construction inside the composable function so `stringResource()` is valid.
- **iOS locale tag format:** `NSLocale.currentLocale.localeIdentifier` returns underscore-separated (e.g. `"pt_BR"`). The `replace("_", "-")` in `IosLocaleProvider` normalises to BCP-47 tag format (`"pt-BR"`) expected by Gemini prompts.
