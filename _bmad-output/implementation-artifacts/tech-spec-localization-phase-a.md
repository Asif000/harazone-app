---
title: 'Localization Phase A — Locale-Aware AI, Dual-Name POIs, Badge Strings, Currency Context'
slug: 'localization-phase-a'
created: '2026-03-13'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain + androidMain + iosMain)'
  - 'Compose Multiplatform (UI in commonMain)'
  - 'Koin 4.x (DI, factory{} in UiModule, single{} in platformModule)'
  - 'Ktor SSE (Gemini streaming)'
  - 'kotlin.test + UnconfinedTestDispatcher (unit tests)'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/provider/LocaleProvider.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/domain/provider/LocaleProvider.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/harazone/domain/provider/LocaleProvider.ios.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/util/LocalizedStrings.kt'
  - 'composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiPromptBuilderLocaleTest.kt'
code_patterns:
  - 'Interface in commonMain/domain/provider/, platform impl in androidMain/iosMain — mirrors LocationProvider pattern'
  - 'LocaleProvider registered as single{} in platformModule() on both platforms'
  - 'AreaContextFactory in UiModule: factory { AreaContextFactory(get(), get()) } — clock + LocaleProvider'
  - 'AreaContext is a data class — add homeCurrencyCode as a new field with default "USD"'
  - 'POI is @Serializable — add localName: String? = null (Kotlinx serialization ignores unknown fields)'
  - 'EnrichJson in GeminiResponseParser — add ln: String? = null for local script name'
  - 'mergeStage2OntoCached in AreaRepositoryImpl — add localName = enrichment.localName ?: cached.localName'
  - 'LocalizedStrings is a pure Kotlin object with a get(languageTag, key) function + English fallback'
test_patterns:
  - 'kotlin.test package: @Test, @BeforeTest'
  - 'FakeLocaleProvider(languageTag, isRtl, homeCurrencyCode) — companion to FakeAreaContextFactory'
  - 'GeminiPromptBuilderLocaleTest: assert prompt contains locale instruction and currency instruction'
  - 'LocalizedStringsTest: assert correct translation returned per language, English fallback for unknown locale'
---

# Tech-Spec: Localization Phase A — Locale-Aware AI, Dual-Name POIs, Badge Strings, Currency Context

**Created:** 2026-03-13

## Overview

### Problem Statement

All Gemini-generated content (area portraits, chat) is returned in English regardless of the user's device locale. UI badge strings (Open, Closed, Safe, Hours unverified) are hardcoded English strings. POIs with non-Latin-script names (Tokyo Tower, Sagrada Família) show only the romanized name — users cannot show the local name to a taxi driver or shop owner. Price references in the cost bucket have no relation to the user's home currency.

International testers on iOS TestFlight encounter an entirely English experience regardless of their device language. This is a TestFlight launch blocker.

### Solution

Four targeted changes, in dependency order:

1. **`LocaleProvider`** — new interface (mirrors `LocationProvider` pattern) that reads device locale, RTL flag, and home currency code from the platform. Registered via Koin in `platformModule()` on both Android and iOS.

2. **Locale injection into `AreaContextFactory`** — `AreaContextFactory` takes `LocaleProvider` as a second constructor parameter. `AreaContext` gains `homeCurrencyCode: String`. `preferredLanguage` is now populated from the device locale instead of being hardcoded `"en"`.

3. **Gemini prompt updates in `GeminiPromptBuilder`** — (a) prepend a strong locale instruction to all prompts using `context.preferredLanguage`; (b) add a currency hint to the cost bucket instruction using `context.homeCurrencyCode`; (c) request a `"ln"` (local name) field in the enrichment prompt for non-Latin-script place names.

4. **Dual-name POI display** — `POI` model gains `localName: String? = null`. `EnrichJson` gains `ln: String? = null`. `GeminiAreaIntelligenceProvider` maps `e.ln` to `POI.localName`. `mergeStage2OntoCached` propagates `localName`. `ExpandablePoiCard` and `POIListView` show `"localName / name"` when `localName` is non-null and differs from `name`.

5. **Hardcoded badge string map** — `LocalizedStrings.kt` — a pure Kotlin object with ~20 strings × 4 non-English languages (AR, ES, PT, FR) + English fallback. Used by UI components rendering live status badges and hours labels.

### Scope

**In Scope:**
- `LocaleProvider` interface + Android/iOS implementations
- `AreaContext.homeCurrencyCode` field
- `AreaContextFactory` wiring to `LocaleProvider`
- `GeminiPromptBuilder`: locale instruction in all prompts, currency hint in cost bucket, `ln` field in enrichment prompt
- `POI.localName` field + `EnrichJson.ln` + merge in `mergeStage2OntoCached`
- `ExpandablePoiCard` + `POIListView` dual-name display
- `LocalizedStrings.kt` for badge/status strings (AR, ES, PT, FR)
- Koin wiring updates in `UiModule`, `PlatformModule.android.kt`, `PlatformModule.ios.kt`
- Unit tests: `GeminiPromptBuilderLocaleTest`, `LocalizedStringsTest`
- Archive old `tech-spec-localisation-phase-a-ai-locale.md` (superseded)

**Out of Scope:**
- Android `strings.xml` / iOS `Localizable.strings` (Phase B)
- RTL layout direction changes (Phase B)
- MapLibre RTL text plugin (Phase B)
- Real-time exchange rate API (currency conversion via Gemini prose only)
- Per-language Gemini prompt quality evaluation
- Safety content translation quality gate (deferred to Safety vibe spec)

---

## Context for Development

### Codebase Patterns

- **`LocaleProvider` mirrors `LocationProvider`**: `LocationProvider` is an interface in `commonMain/location/`, with `AndroidLocationProvider` in `androidMain` and `IosLocationProvider` in `iosMain`, registered as `single<LocationProvider> { ... }` in `platformModule()`. Follow this exact pattern.
- **`AreaContextFactory` is in `UiModule`**: Currently `factory { AreaContextFactory(get()) }` — takes only `AppClock`. Add `LocaleProvider` as second param: `factory { AreaContextFactory(get(), get()) }`.
- **`AreaContext` is a plain Kotlin `data class`** — not `@Serializable`, not stored to DB directly. Adding `homeCurrencyCode` and `isRtl` with defaults is safe, no DB migration needed. All existing named-argument construction sites compile without change.
- **`POI` is `@Serializable`** — stored as JSON in `area_poi_cache`. Adding `localName: String? = null` with a default value is fully backwards-compatible — old cached rows without the field will deserialize with `null` (Kotlinx serialization ignores missing optional fields with defaults).
- **`EnrichJson`** is an internal `@Serializable` data class in `GeminiResponseParser.kt` — add `val ln: String? = null`.
- **`GeminiAreaIntelligenceProvider` maps `EnrichJson` → `POI`** at line ~181 in `streamAreaPortrait`. Add `localName = e.ln` there.
- **`mergeStage2OntoCached`** in `AreaRepositoryImpl` copies specific fields from Stage 2 onto Stage 1 POIs. Add `localName = enrichment.localName ?: cached.localName`.
- **`GeminiPromptBuilder` has no constructor params** currently — `single { GeminiPromptBuilder() }` in `DataModule`. Do NOT add `LocaleProvider` here — the locale arrives via `AreaContext` which is already passed to every prompt-building function. Just use `context.preferredLanguage` and `context.homeCurrencyCode`.
- **`FakeAreaContextFactory`** in `commonTest/fakes/` subclasses `AreaContextFactory(FakeClock())`. After this change, `AreaContextFactory` constructor takes `(clock, localeProvider)`. Update `FakeAreaContextFactory` to pass a `FakeLocaleProvider` as the second arg.
- **`preferredLanguage` is already in the prompt Context block** in both `buildEnrichmentPrompt` and `buildAreaPortraitPrompt`. The existing line `- Preferred language: ${context.preferredLanguage}` is a soft context hint — it is NOT a strong Gemini instruction. Replace it with a hard instruction (see Task 3).

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/location/LocationProvider.kt` | Pattern to copy for LocaleProvider interface |
| `composeApp/src/androidMain/kotlin/com/harazone/location/AndroidLocationProvider.kt` | Pattern for Android platform impl |
| `composeApp/src/iosMain/kotlin/com/harazone/location/IosLocationProvider.kt` | Pattern for iOS platform impl |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt` | Add homeCurrencyCode field |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Add localName field |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt` | Inject LocaleProvider, use languageTag + homeCurrencyCode |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | Add locale instruction + currency hint + ln field |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt` | Add ln to EnrichJson |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt` | Map e.ln → POI.localName at line ~181 |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt` | Add localName to mergeStage2OntoCached |
| `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt` | factory { AreaContextFactory(get(), get()) } |
| `composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt` | Register AndroidLocaleProvider |
| `composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt` | Register IosLocaleProvider |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt` | Show localName / name when localName present |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/POIListView.kt` | Show localName / name in list |
| `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt` | Pass FakeLocaleProvider as second arg |

### Technical Decisions

- **Currency via Gemini prose, not conversion API**: The cost bucket instruction tells Gemini to express prices in both local currency and the user's home currency (e.g., "150 CZK (~$6.50)"). Gemini's training data includes approximate exchange rates adequate for illustrative purposes. No exchange rate API, no bundled rates file, no UI toggle needed for Phase A.
- **Dual-name only from Stage 2 enrichment**: Stage 1 pin-only prompt is intentionally kept slim (4 lines) for speed. `localName` is only requested in Stage 2 enrichment. During Stage 1, all POI cards show the romanized name only. The dual name pops in when Stage 2 enrichment arrives — consistent with the existing two-stage pop-in pattern.
- **LocalName only shown when it differs from name**: Guard: `if (localName != null && localName != name)`. This prevents redundant display for Latin-script POI names (e.g., "Alfama" where local name is identical).
- **LocalizedStrings fallback chain**: `get(languageTag, key)` → check language root (e.g., "ar" for "ar-SA") → if not found, return English. Unknown languages always return English. No crashes, no blank badges.
- **`preferredLanguage` field renamed intent**: Currently `AreaContext.preferredLanguage` is set to `"en"`. After this change, it contains the BCP 47 language tag from the device (e.g., "ar", "es-MX", "pt-BR"). The Gemini instruction uses this tag directly.
- **`isRtl` flows through `AreaContext`, not hardcoded in prompt builder**: `LocaleProvider.isRtl` is platform-computed (Android: `TextUtils.getLayoutDirectionFromLocale`, iOS: `NSLocale.characterDirectionForLanguage`). It is set on `AreaContext.isRtl` by `AreaContextFactory` and consumed by `localeInstruction(context)`. This avoids maintaining a hardcoded RTL language list in the prompt builder that can diverge from the platform's own RTL detection.

---

## Implementation Plan

### Tasks

#### Task 1: `LocaleProvider` interface + platform implementations

**Create: `commonMain/kotlin/com/harazone/domain/provider/LocaleProvider.kt`**
```kotlin
package com.harazone.domain.provider

interface LocaleProvider {
    /** BCP 47 language tag of the device primary locale, e.g. "ar", "es", "pt-BR", "en". */
    val languageTag: String

    /** True if the locale is RTL (Arabic, Hebrew, Persian, Urdu, etc.). */
    val isRtl: Boolean

    /** ISO 4217 currency code of the user's home locale, e.g. "USD", "GBP", "EUR". Falls back to "USD". */
    val homeCurrencyCode: String
}
```

**Create: `androidMain/kotlin/com/harazone/domain/provider/LocaleProvider.android.kt`**
```kotlin
package com.harazone.domain.provider

import android.content.Context
import android.text.TextUtils
import android.view.View
import androidx.core.os.ConfigurationCompat
import java.util.Currency

class AndroidLocaleProvider(private val context: Context) : LocaleProvider {

    private val primaryLocale get() = ConfigurationCompat
        .getLocales(context.resources.configuration)
        .get(0)

    override val languageTag: String
        get() = primaryLocale?.toLanguageTag() ?: "en"

    override val isRtl: Boolean
        get() = TextUtils.getLayoutDirectionFromLocale(primaryLocale) == View.LAYOUT_DIRECTION_RTL

    override val homeCurrencyCode: String
        get() = try {
            primaryLocale?.let { Currency.getInstance(it).currencyCode } ?: "USD"
        } catch (e: IllegalArgumentException) {
            "USD"
        }
}
```

**Create: `iosMain/kotlin/com/harazone/domain/provider/LocaleProvider.ios.kt`**
```kotlin
package com.harazone.domain.provider

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.currentLocale
import platform.Foundation.currencyCode
import platform.Foundation.preferredLanguages

class IosLocaleProvider : LocaleProvider {

    override val languageTag: String
        get() = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

    override val isRtl: Boolean
        get() {
            val lang = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: return false
            return NSLocale.characterDirectionForLanguage(lang) == NSLocaleLanguageDirectionRightToLeft
        }

    override val homeCurrencyCode: String
        get() = NSLocale.currentLocale.currencyCode ?: "USD"
}
```

---

#### Task 2: Update `AreaContext` + `AreaContextFactory`

ORDERING CONSTRAINT: Update `AreaContextFactory` FIRST (step 2b), then `FakeAreaContextFactory` (step 2c). If you update the fake before the real class, the project will not compile.

**2a — Audit existing `AreaContext` construction call sites BEFORE changing the class.**

Search for `AreaContext(` across the project. Known call sites that need `homeCurrencyCode = "USD"` added (or will compile fine due to the default if using named arguments):
- `composeApp/src/commonTest/kotlin/com/harazone/domain/model/DomainModelTest.kt`
- `composeApp/src/androidUnitTest/kotlin/com/harazone/data/repository/AreaRepositoryTest.kt`
- `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProviderTest.kt`
- `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiPromptBuilderTest.kt`
- `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt`
- `composeApp/src/androidInstrumentedTest/kotlin/com/harazone/PromptComparisonTest.kt`

All of these construct `AreaContext` with named parameters — the new `homeCurrencyCode` field has a default of `"USD"` so they will compile without changes. However, audit each to confirm no positional construction is used.

**2b — Modify: `commonMain/kotlin/com/harazone/domain/model/AreaContext.kt`**

Add `homeCurrencyCode` and `isRtl` (used by `localeInstruction()` to avoid hardcoded language list — see Task 4):
```kotlin
data class AreaContext(
    val timeOfDay: String,
    val dayOfWeek: String,
    val visitCount: Int,
    val preferredLanguage: String,
    val homeCurrencyCode: String = "USD",
    val isRtl: Boolean = false,
)
```

**2c — Modify: `commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt`**

Inject `LocaleProvider`, use it for `preferredLanguage`, `homeCurrencyCode`, and `isRtl`:
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
            homeCurrencyCode = localeProvider.homeCurrencyCode,
            isRtl = localeProvider.isRtl,
        )
    }
    // resolveTimeOfDay and resolveDayOfWeek unchanged
}
```

**2d — Modify: `commonTest/kotlin/com/harazone/fakes/FakeAreaContextFactory.kt`**

Define `FakeLocaleProvider` at the top of the file, then update `FakeAreaContextFactory` to pass it as the second constructor argument:
```kotlin
import com.harazone.domain.provider.LocaleProvider

class FakeLocaleProvider(
    override val languageTag: String = "en",
    override val isRtl: Boolean = false,
    override val homeCurrencyCode: String = "USD",
) : LocaleProvider

class FakeAreaContextFactory(
    private val context: AreaContext = AreaContext(
        timeOfDay = "morning",
        dayOfWeek = "Wednesday",
        visitCount = 0,
        preferredLanguage = "en",
        homeCurrencyCode = "USD",
        isRtl = false,
    )
) : AreaContextFactory(FakeClock(), FakeLocaleProvider()) {

    var callCount = 0
        private set

    override fun create(): AreaContext {
        callCount++
        return context
    }
}
```

Note: `FakeAreaContextFactory` overrides `create()` entirely, so the base constructor args are unused — passing `FakeLocaleProvider()` only satisfies the new constructor signature. Keep the existing `callCount` tracking.

---

#### Task 3: Koin wiring

**Modify: `androidMain/kotlin/com/harazone/di/PlatformModule.android.kt`**

Add `AndroidLocaleProvider` registration:
```kotlin
import com.harazone.domain.provider.AndroidLocaleProvider
import com.harazone.domain.provider.LocaleProvider

actual fun platformModule() = module {
    single<AnalyticsTracker> { AndroidAnalyticsTracker() }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single { DatabaseDriverFactory(androidContext()) }
    single { ConnectivityMonitor(androidContext()) }
    single<LocaleProvider> { AndroidLocaleProvider(androidContext()) }  // ADD
}
```

**Modify: `iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt`**

Add `IosLocaleProvider` registration:
```kotlin
import com.harazone.domain.provider.IosLocaleProvider
import com.harazone.domain.provider.LocaleProvider

actual fun platformModule() = module {
    single<AnalyticsTracker> { IosAnalyticsTracker() }
    single<LocationProvider> { IosLocationProvider() }
    single { DatabaseDriverFactory() }
    single { ConnectivityMonitor() }
    single<LocaleProvider> { IosLocaleProvider() }  // ADD
}
```

**Modify: `commonMain/kotlin/com/harazone/di/UiModule.kt`**

Two changes in one edit — consolidate both in a single pass to avoid applying one and missing the other:
```kotlin
import com.harazone.domain.provider.LocaleProvider
import com.harazone.domain.service.AreaContextFactory
import com.harazone.ui.map.ChatViewModel
import com.harazone.ui.map.MapViewModel
import com.harazone.ui.saved.SavedPlacesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    factory { AreaContextFactory(get(), get()) }                              // +LocaleProvider (was: get() only)
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChatViewModel(get(), get(), get(), get(), get()) }            // +LocaleProvider (was: 4 get()s)
    viewModel { SavedPlacesViewModel(get()) }
}
```

Both `get()` additions resolve `LocaleProvider` — Koin matches by type, `LocaleProvider` is registered as `single<LocaleProvider>` in `platformModule()`.

---

#### Task 4: `GeminiPromptBuilder` updates

Three changes to `GeminiPromptBuilder.kt`:

**4a — Add a `localeInstruction()` private function:**

Use `context.isRtl` (platform-computed via `LocaleProvider.isRtl` → `AreaContextFactory` → `AreaContext.isRtl`) rather than a hardcoded language-prefix list. This avoids maintaining two separate RTL detection paths.

```kotlin
private fun localeInstruction(context: AreaContext): String {
    val rtlHint = if (context.isRtl) " Write text right-to-left where appropriate." else ""
    return "LANGUAGE: Respond entirely in the language identified by BCP 47 tag \"${context.preferredLanguage}\".${rtlHint}\n\n"
}
```

**4b — Prepend `localeInstruction()` in `buildAreaPortraitPrompt` and `buildEnrichmentPrompt` only.**

EXPLICIT EXCEPTION: Do NOT add `localeInstruction()` to `buildPinOnlyPrompt`. Stage 1 (pin-only) is intentionally kept to 4 lines for speed — adding locale overhead defeats the optimization. Pins will show romanized English names for the 3-7 second Stage 1 window. This is acceptable: Stage 2 enrichment arrives quickly and replaces content including `localName`. Document this as a known product decision, not a bug.

In `buildAreaPortraitPrompt`:
```kotlin
fun buildAreaPortraitPrompt(areaName: String, context: AreaContext): String {
    return localeInstruction(context) + """
You are a passionate local ...
...
Context:
- Time of day: ${context.timeOfDay}
- Day of week: ${context.dayOfWeek}
...
    """.trimIndent()
}
```

Remove the now-redundant `- Preferred language: ${context.preferredLanguage}` line from the Context block in both prompts — replaced by the stronger `localeInstruction()` prepend.

**4c — Add currency hint to cost bucket instruction in `buildAreaPortraitPrompt`:**

In the area portrait prompt, after the bucket type definitions, add a currency instruction:
```
CURRENCY: When mentioning prices in the COST bucket, express amounts in both local currency and ${context.homeCurrencyCode} where you know the approximate rate (e.g., "150 CZK (~$6.50)"). If unknown, local currency only is fine.
```

Place this instruction after the UNIQUENESS RULE block and before the FOOD GATE block in the existing prompt.

**4d — Request `"ln"` field in `buildEnrichmentPrompt`:**

Update the JSON schema line:
```kotlin
// BEFORE:
[{"n":"Name","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5}]

// AFTER:
[{"n":"Name","ln":"Local script name if different from romanized name (e.g. 東京タワー). Omit for Latin-script names.","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5}]
```

Also prepend `localeInstruction(context)` to `buildEnrichmentPrompt`. Remove `- Preferred language: ${context.preferredLanguage}` from its Context block.

**4e — Add locale instruction to `buildChatSystemContext`:**

`buildChatSystemContext` assembles blocks via `listOf(...).joinToString("\n\n")`. Add a locale block:
```kotlin
fun buildChatSystemContext(..., context: AreaContext, ...): String {
```
Wait — `buildChatSystemContext` does NOT currently receive `AreaContext`. It only receives `areaName`, `pois`, `intent`, etc. Two options:
- Option A: Add `context: AreaContext` parameter to `buildChatSystemContext`
- Option B: Add a `languageTag: String` parameter

**Use Option A** — pass `context: AreaContext` as a new parameter. This is more consistent and leaves room for future context fields. Caller is `ChatViewModel` — update the call site to pass the `AreaContext` stored in `MapViewModel` state (or re-create via `areaContextFactory.create()`).

Add locale to the blocks list in `buildChatSystemContext`:
```kotlin
private fun localeBlock(context: AreaContext): String =
    "LANGUAGE: Respond entirely in the language identified by BCP 47 tag \"${context.preferredLanguage}\"."
```

Add `localeBlock(context)` as the first item in the `listOf(...)`.

**Caller change in `ChatViewModel`**: `ChatViewModel` calls `promptBuilder.buildChatSystemContext(...)`. It needs to pass `context: AreaContext`. `ChatViewModel` does not have `AreaContextFactory` currently. Options:
- Pass the `AreaContext` from `MapViewModel` when initializing chat (already passed indirectly through `pois`)
- Or inject `AreaContextFactory` into `ChatViewModel`

**Simplest approach**: inject `LocaleProvider` directly into `ChatViewModel` as a 5th constructor parameter. `ChatViewModel` currently takes `(aiProvider, promptBuilder, clock, savedPoiRepository)` — add `localeProvider: LocaleProvider` as the 5th param. Use `localeProvider.languageTag` when calling `buildChatSystemContext`.

`UiModule.kt` update: `viewModel { ChatViewModel(get(), get(), get(), get(), get()) }` — the 5th `get()` resolves `LocaleProvider`. `LocaleProvider` is already registered in `platformModule()` (Task 3), so it will be available to Koin.

---

#### Task 5: `GeminiResponseParser` — add `ln` to both `EnrichJson` and `PoiJson`

`localName` must be added to BOTH JSON DTOs — `EnrichJson` (Stage 2 enrichment path) AND `PoiJson` (Stage 2 full-portrait fallback path, used in `parsePoisJson`). Missing it from `PoiJson` means POIs returned via the portrait path (not the two-stage path) will always have `localName = null`.

**Modify `EnrichJson`:**
```kotlin
@Serializable
internal data class EnrichJson(
    val n: String = "",
    val v: String = "",
    val w: String = "",
    val h: String? = null,
    val s: String? = null,
    val r: Float? = null,
    val ln: String? = null,  // ADD: local script name
)
```

**Modify `PoiJson`:**
```kotlin
@Serializable
internal data class PoiJson(
    val n: String = "",
    val t: String = "",
    val v: String = "",
    val w: String = "",
    val h: String? = null,
    val s: String? = null,
    val r: Float? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val wiki: String? = null,
    val ln: String? = null,  // ADD: local script name
)
```

Also update `parsePoisJson` (which maps `PoiJson` → `POI`) to include `localName = poiJson.ln` in the `POI(...)` constructor call.

No changes to parser logic — `ignoreUnknownKeys = true` is already set on the `Json` instance.

---

#### Task 6: `GeminiAreaIntelligenceProvider` — map `e.ln` to `POI.localName`

**Modify `POI.kt`** — add `localName` field:
```kotlin
@Serializable
data class POI(
    val name: String,
    val type: String,
    val description: String,
    val confidence: Confidence,
    val latitude: Double?,
    val longitude: Double?,
    val vibe: String = "",
    val insight: String = "",
    val hours: String? = null,
    val liveStatus: String? = null,
    val rating: Float? = null,
    val vibeInsights: Map<String, String> = emptyMap(),
    val wikiSlug: String? = null,
    val imageUrl: String? = null,
    val localName: String? = null,  // ADD: local script name (e.g. "東京タワー")
) {
    val savedId: String get() = "$name|$latitude|$longitude"
}
```

**Modify `GeminiAreaIntelligenceProvider.kt`** — at line ~181 where `EnrichJson` is mapped to `POI`:
```kotlin
send(BucketUpdate.PortraitComplete(enriched.map { e ->
    POI(
        name = e.n,
        type = "",
        description = "",
        confidence = Confidence.MEDIUM,
        latitude = null,
        longitude = null,
        vibe = e.v,
        insight = e.w,
        hours = e.h,
        liveStatus = e.s,
        rating = e.r,
        localName = e.ln,  // ADD
    )
}))
```

---

#### Task 7: `AreaRepositoryImpl` — merge `localName` in `mergeStage2OntoCached`

Read the existing `mergeStage2OntoCached` method in `AreaRepositoryImpl.kt` first. Add ONE line to the existing `cached.copy(...)` block — do not replace the whole block, which risks dropping fields that exist today:

```kotlin
// Inside the existing cached.copy(...) block, add:
localName = enrichment.localName ?: cached.localName,
```

The complete post-change method should look like the existing method with only this one new line added inside `cached.copy(...)`. Do not remove or reorder any existing copy fields.

---

#### Task 8: UI — dual-name display in `ExpandablePoiCard` and `POIListView`

**Modify `ExpandablePoiCard.kt`** — POI name Text composable (line ~136):

Replace:
```kotlin
text = poi.name,
```
With:
```kotlin
text = if (poi.localName != null && poi.localName != poi.name) {
    "${poi.localName} / ${poi.name}"
} else {
    poi.name
},
```

Apply the same pattern to the `contentDescription` at line ~109:
```kotlin
contentDescription = if (poi.localName != null && poi.localName != poi.name) {
    "${poi.localName} / ${poi.name}"
} else {
    poi.name
},
```

**Modify `POIListView.kt`** — POI name Text at line ~155 and contentDescription at line ~123:

Same pattern as above. Both files — find the `text = poi.name` and `contentDescription = poi.name` for the POI name display and apply the localName guard.

---

#### Task 9: `LocalizedStrings.kt` — hardcoded badge string map

**Create: `commonMain/kotlin/com/harazone/util/LocalizedStrings.kt`**

```kotlin
package com.harazone.util

/**
 * Hardcoded translations for ~20 common badge/status UI strings.
 * Covers Arabic (ar), Spanish (es), Portuguese (pt), French (fr).
 * All other locales fall back to English.
 *
 * Phase B: migrate to Android strings.xml / iOS Localizable.strings.
 * TODO(BACKLOG-LOW): migrate badge strings to platform string resources in Phase B
 */
object LocalizedStrings {

    // Key constants
    const val OPEN = "open"
    const val CLOSED = "closed"
    const val BUSY = "busy"
    const val HOURS_UNVERIFIED = "hours_unverified"
    const val NO_DATA = "no_data"
    const val SAFE = "safe"
    const val USE_CAUTION = "use_caution"
    const val LIMITED_DATA = "limited_data"
    const val LOADING = "loading"
    const val SAVED = "saved"
    const val SAVE = "save"
    const val DIRECTIONS = "directions"
    const val ASK_AI = "ask_ai"
    const val SHARE = "share"
    const val LOCAL_NAME_ATTRIBUTION = "local_name_attribution"
    const val OFFLINE_CACHED = "offline_cached"
    const val CALL_AHEAD = "call_ahead"
    const val LOCAL_WHISPER = "local_whisper"

    private val strings: Map<String, Map<String, String>> = mapOf(
        "ar" to mapOf(
            OPEN to "مفتوح",
            CLOSED to "مغلق",
            BUSY to "مزدحم",
            HOURS_UNVERIFIED to "ساعات غير مؤكدة",
            NO_DATA to "لا توجد بيانات",
            SAFE to "آمن",
            USE_CAUTION to "توخ الحذر",
            LIMITED_DATA to "بيانات محدودة",
            LOADING to "جارٍ التحميل...",
            SAVED to "محفوظ",
            SAVE to "حفظ",
            DIRECTIONS to "الاتجاهات",
            ASK_AI to "اسأل الذكاء الاصطناعي",
            SHARE to "مشاركة",
            LOCAL_NAME_ATTRIBUTION to "الاسم المحلي",
            OFFLINE_CACHED to "محتوى محفوظ مؤقتاً",
            CALL_AHEAD to "اتصل مسبقاً للتأكيد",
            LOCAL_WHISPER to "همسة محلية",
        ),
        "es" to mapOf(
            OPEN to "Abierto",
            CLOSED to "Cerrado",
            BUSY to "Concurrido",
            HOURS_UNVERIFIED to "Horario no verificado",
            NO_DATA to "Sin datos",
            SAFE to "Seguro",
            USE_CAUTION to "Precaución",
            LIMITED_DATA to "Datos limitados",
            LOADING to "Cargando...",
            SAVED to "Guardado",
            SAVE to "Guardar",
            DIRECTIONS to "Cómo llegar",
            ASK_AI to "Preguntar a la IA",
            SHARE to "Compartir",
            LOCAL_NAME_ATTRIBUTION to "Nombre local",
            OFFLINE_CACHED to "Contenido en caché",
            CALL_AHEAD to "Llama antes de ir",
            LOCAL_WHISPER to "Susurro local",
        ),
        "pt" to mapOf(
            OPEN to "Aberto",
            CLOSED to "Fechado",
            BUSY to "Movimentado",
            HOURS_UNVERIFIED to "Horário não verificado",
            NO_DATA to "Sem dados",
            SAFE to "Seguro",
            USE_CAUTION to "Atenção",
            LIMITED_DATA to "Dados limitados",
            LOADING to "Carregando...",
            SAVED to "Salvo",
            SAVE to "Salvar",
            DIRECTIONS to "Como chegar",
            ASK_AI to "Perguntar à IA",
            SHARE to "Compartilhar",
            LOCAL_NAME_ATTRIBUTION to "Nome local",
            OFFLINE_CACHED to "Conteúdo em cache",
            CALL_AHEAD to "Ligue antes de ir",
            LOCAL_WHISPER to "Segredo local",
        ),
        "fr" to mapOf(
            OPEN to "Ouvert",
            CLOSED to "Fermé",
            BUSY to "Très fréquenté",
            HOURS_UNVERIFIED to "Horaires non vérifiés",
            NO_DATA to "Aucune donnée",
            SAFE to "Sûr",
            USE_CAUTION to "Prudence",
            LIMITED_DATA to "Données limitées",
            LOADING to "Chargement...",
            SAVED to "Enregistré",
            SAVE to "Enregistrer",
            DIRECTIONS to "Itinéraire",
            ASK_AI to "Demander à l'IA",
            SHARE to "Partager",
            LOCAL_NAME_ATTRIBUTION to "Nom local",
            OFFLINE_CACHED to "Contenu en cache",
            CALL_AHEAD to "Appelez avant de visiter",
            LOCAL_WHISPER to "Murmure local",
        ),
    )

    /**
     * Returns the localized string for [key] in [languageTag].
     * Falls back to English built-in strings if language not found.
     * Strips region subtag for lookup (e.g. "ar-SA" → "ar").
     */
    fun get(languageTag: String, key: String): String {
        val lang = languageTag.substringBefore('-').lowercase()
        return strings[lang]?.get(key) ?: englishFallback(key)
    }

    private fun englishFallback(key: String): String = when (key) {
        OPEN -> "Open"
        CLOSED -> "Closed"
        BUSY -> "Busy"
        HOURS_UNVERIFIED -> "Hours unverified"
        NO_DATA -> "No data"
        SAFE -> "Safe"
        USE_CAUTION -> "Use caution"
        LIMITED_DATA -> "Limited data"
        LOADING -> "Loading..."
        SAVED -> "Saved"
        SAVE -> "Save"
        DIRECTIONS -> "Directions"
        ASK_AI -> "Ask AI"
        SHARE -> "Share"
        LOCAL_NAME_ATTRIBUTION -> "Local name"
        OFFLINE_CACHED -> "Cached content"
        CALL_AHEAD -> "Call ahead to confirm"
        LOCAL_WHISPER -> "Local Whisper"
        else -> key
    }
}
```

**Usage in UI — REQUIRED, not optional**: Search for hardcoded `"Open"`, `"Closed"`, `"Busy"`, `"Hours unverified"` string literals in `ExpandablePoiCard.kt`, `POIListView.kt`, and `SavedPoiCard.kt`. Replace each with `LocalizedStrings.get(languageTag, LocalizedStrings.OPEN)` etc.

**How to get `languageTag` in UI**: Expose `languageTag: String` on `MapUiState` (set from `areaContextFactory.create().preferredLanguage` when the ViewModel creates a context). Thread it into composables via the existing UiState parameter — do NOT inject `LocaleProvider` directly into composables.

This wiring is a required deliverable of this task. AC 13 below validates it with an automated test.

---

#### Task 10: Unit tests

**Create: `commonTest/kotlin/com/harazone/data/remote/GeminiPromptBuilderLocaleTest.kt`**
```kotlin
package com.harazone.data.remote

import com.harazone.domain.model.AreaContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class GeminiPromptBuilderLocaleTest {

    private val builder = GeminiPromptBuilder()

    private fun ctx(lang: String, currency: String = "USD") = AreaContext(
        timeOfDay = "morning",
        dayOfWeek = "Monday",
        visitCount = 0,
        preferredLanguage = lang,
        homeCurrencyCode = currency,
    )

    @Test
    fun `area portrait prompt contains Arabic locale instruction`() {
        val prompt = builder.buildAreaPortraitPrompt("Dubai", ctx("ar"))
        assertContains(prompt, "\"ar\"")
        assertContains(prompt, "right-to-left")
    }

    @Test
    fun `area portrait prompt contains Spanish locale instruction`() {
        val prompt = builder.buildAreaPortraitPrompt("Madrid", ctx("es"))
        assertContains(prompt, "\"es\"")
        assertFalse(prompt.contains("right-to-left"))
    }

    @Test
    fun `area portrait prompt contains currency instruction for GBP`() {
        val prompt = builder.buildAreaPortraitPrompt("London", ctx("en-GB", "GBP"))
        assertContains(prompt, "GBP")
    }

    @Test
    fun `enrichment prompt contains locale instruction`() {
        val prompt = builder.buildEnrichmentPrompt("Tokyo", listOf("Tokyo Tower"), ctx("ja"))
        assertContains(prompt, "\"ja\"")
    }

    @Test
    fun `enrichment prompt requests ln field`() {
        val prompt = builder.buildEnrichmentPrompt("Tokyo", listOf("Tokyo Tower"), ctx("en"))
        assertContains(prompt, "\"ln\"")
    }

    @Test
    fun `English locale produces no RTL hint`() {
        val prompt = builder.buildAreaPortraitPrompt("London", ctx("en"))
        assertContains(prompt, "\"en\"")
        assertFalse(prompt.contains("right-to-left"))
    }
}
```

**Create: `commonTest/kotlin/com/harazone/util/LocalizedStringsTest.kt`**
```kotlin
package com.harazone.util

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedStringsTest {

    @Test
    fun `Arabic returns correct translation`() {
        assertEquals("مفتوح", LocalizedStrings.get("ar", LocalizedStrings.OPEN))
    }

    @Test
    fun `Spanish returns correct translation`() {
        assertEquals("Cerrado", LocalizedStrings.get("es", LocalizedStrings.CLOSED))
    }

    @Test
    fun `Portuguese with region subtag falls back to root language`() {
        assertEquals("Aberto", LocalizedStrings.get("pt-BR", LocalizedStrings.OPEN))
    }

    @Test
    fun `French returns correct translation`() {
        assertEquals("Ouvert", LocalizedStrings.get("fr", LocalizedStrings.OPEN))
    }

    @Test
    fun `Unknown language falls back to English`() {
        assertEquals("Open", LocalizedStrings.get("zh", LocalizedStrings.OPEN))
    }

    @Test
    fun `Unknown key returns key itself`() {
        assertEquals("unknown_key", LocalizedStrings.get("en", "unknown_key"))
    }
}
```

---

### Acceptance Criteria

**Given** device locale is Arabic (ar):
- **When** area portrait loads, **Then** all Gemini-generated bucket content (highlight, content text) is in Arabic
- **When** a POI has a local Arabic name, **Then** the POI card shows the Arabic name alongside the romanized name
- **When** live status badge renders, **Then** it shows the Arabic word (e.g., "مفتوح" not "Open")

**Given** device locale is Spanish (es):
- **When** area portrait loads, **Then** Gemini content is in Spanish

**Given** device locale is Portuguese (pt-BR):
- **When** area portrait loads, **Then** Gemini content is in Portuguese
- **When** `LocalizedStrings.get("pt-BR", LocalizedStrings.OPEN)` is called, **Then** it returns "Aberto"

**Given** device locale is French (fr):
- **When** area portrait loads, **Then** Gemini content is in French

**Given** device locale is English or any unsupported locale:
- **When** area portrait loads, **Then** Gemini content is in English (Gemini default)
- **When** `LocalizedStrings.get("zh", key)` is called, **Then** English fallback string is returned

**Given** a POI in a non-Latin-script area (e.g., Tokyo):
- **When** Stage 2 enrichment arrives, **Then** `localName` is populated on the `POI` model (e.g., "東京タワー")
- **When** `ExpandablePoiCard` renders the POI, **Then** it shows "東京タワー / Tokyo Tower"
- **When** `localName == null` or `localName == name`, **Then** only `name` is shown (no duplicate display)

**Given** device locale is US English:
- **When** cost bucket content is generated for Prague, **Then** Gemini prose includes both CZK and USD prices (e.g., "150 CZK (~$6.50)")

**Given** device locale is British English (GBP):
- **When** cost bucket content is generated, **Then** Gemini prose includes GBP alongside local currency

**Given** Arabic locale is active:
- **When** `ExpandablePoiCard` renders a POI with `liveStatus = "open"`, **Then** `LocalizedStrings.get("ar", OPEN)` is called and "مفتوح" is displayed — not the hardcoded string "Open" (AC 13)

**All tests:**
- `GeminiPromptBuilderLocaleTest` — 6 tests pass (note: `GeminiPromptBuilder` is `internal class` — tests in `commonTest` have access via same compilation unit; do not move these tests to a separate module)
- `LocalizedStringsTest` — 6 tests pass
- `LocalizedStringsWiringTest` — add 1 test verifying `LocalizedStrings.get("ar", OPEN) != "Open"` to confirm the wiring contract is not accidentally broken
- All existing tests continue to pass (no regressions from `AreaContext` + `POI` model changes)

---

## Additional Context

### Dependencies

- No new Gradle dependencies required
- `androidx.core:core-ktx` already in androidMain (provides `ConfigurationCompat`)
- iOS: `platform.Foundation.NSLocale` — available in KMP iOS stdlib, no new imports

### Testing Strategy

- Unit tests cover prompt content + LocalizedStrings behavior
- No instrumented tests needed for this feature
- Manual device test: set device language to Arabic → launch app → verify Gemini content is in Arabic
- Manual device test: set device language to Japanese → search for Tokyo → verify dual name on POI cards
- `FakeLocaleProvider` enables testing any locale scenario without a real device

### Notes

- Old spec `tech-spec-localisation-phase-a-ai-locale.md` has been archived as `tech-spec-localisation-phase-a-ai-locale-archived-2026-03-13.md`. This spec supersedes it with correct `com.harazone` package names and expanded scope.
- Cache keyed by language: `getBucketsByAreaAndLanguage(areaName, language)` already exists in `AreaRepositoryImpl` — locale-separated caching is already implemented. No cache schema changes needed.
- Cache invalidation for `localName`: Existing cached POI JSON rows (pre-release installs) will not have `localName` populated until their cache TTL expires (semi-static: 3 days). This is acceptable for a TestFlight launch. On test devices, uninstall and reinstall (`adb uninstall com.harazone.debug`) to force a clean state and verify dual-name display immediately.
- `buildPinOnlyPrompt` intentionally stays English-only for Stage 1 speed. Pin names during the 3-7s Stage 1 window will always be romanized English. This is a deliberate product decision — document in release notes if needed.
- `GeminiPromptBuilder` is declared `internal class` — the proposed tests in `commonTest` rely on KMP same-compilation-unit visibility. Do not move these tests to a separate Gradle module or they will fail to compile.
