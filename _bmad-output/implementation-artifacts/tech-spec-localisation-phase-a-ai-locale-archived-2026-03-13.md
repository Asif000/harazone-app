---
title: 'Localisation Phase A — AI Content Locale Injection'
slug: 'localisation-phase-a-ai-locale'
created: '2026-03-06'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain + androidMain + iosMain)'
  - 'Compose Multiplatform (UI in commonMain)'
  - 'Koin 4.x (DI, single{} registrations in platformModule)'
  - 'Ktor SSE (Gemini streaming)'
  - 'kotlin.test + UnconfinedTestDispatcher (unit tests)'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/LocaleProvider.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/domain/provider/LocaleProvider.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/areadiscovery/domain/provider/LocaleProvider.ios.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderLocaleTest.kt'
code_patterns:
  - 'expect/actual for platform-specific implementations (same as LocationProvider, DatabaseDriverFactory)'
  - 'Koin single{} in platformModule for LocaleProvider'
  - 'GeminiPromptBuilder receives LocaleProvider via constructor injection'
  - 'BCP 47 language tag (e.g. "ar", "es", "pt", "en") passed to prompt'
  - 'RTL flag derived from locale for prompt framing hint'
test_patterns:
  - 'kotlin.test package: @Test, @BeforeTest'
  - 'FakeLocaleProvider returning configurable locale strings'
  - 'Assert prompt contains locale instruction for Arabic, Spanish, Portuguese, English'
---

# Tech-Spec: Localisation Phase A — AI Content Locale Injection

**Created:** 2026-03-06

## Overview

### Problem Statement

All Gemini-generated area portrait content is currently returned in English regardless of the user's device locale. Arabic, Spanish, and Portuguese speakers receive English content. Gemini natively supports all three languages at high quality — the only missing piece is telling it which language to use.

### Solution

1. Add a `LocaleProvider` interface in `commonMain` with `expect/actual` implementations that read the device's primary locale (BCP 47 language tag).
2. Inject `LocaleProvider` into `GeminiPromptBuilder` and prepend a locale instruction to every prompt.
3. Wire `LocaleProvider` via Koin in `platformModule` on Android and iOS.

Gemini handles translation of all six vibes, POI insights, and chat responses automatically. No changes to response parsing, caching, or UI are needed.

### Scope

**In Scope:**
- `LocaleProvider` interface + Android/iOS actual implementations
- `GeminiPromptBuilder` — inject locale, prepend locale instruction to `buildAreaPortraitPrompt` and `buildChatPrompt` (when implemented)
- Koin wiring in `PlatformModule`
- Unit tests: `GeminiPromptBuilderLocaleTest`

**Out of Scope:**
- UI string localisation (Phase B)
- RTL layout direction changes (Phase B)
- MapLibre RTL text plugin (Phase B)
- Arabic numeral formatting
- Locale-aware date/time display
- Per-language prompt tuning / quality evaluation

---

## Tasks

### Task 1: `LocaleProvider` interface + implementations

**`commonMain/domain/provider/LocaleProvider.kt`**
```kotlin
interface LocaleProvider {
    /** BCP 47 language tag of the device primary locale, e.g. "ar", "es", "pt", "en". */
    val languageTag: String

    /** True if the locale is RTL (Arabic, Hebrew, Persian, Urdu, etc.). */
    val isRtl: Boolean
}
```

**`androidMain/domain/provider/LocaleProvider.android.kt`**
```kotlin
class AndroidLocaleProvider(private val context: Context) : LocaleProvider {
    override val languageTag: String
        get() = ConfigurationCompat
            .getLocales(context.resources.configuration)
            .get(0)
            ?.toLanguageTag()
            ?: "en"

    override val isRtl: Boolean
        get() = TextUtils.getLayoutDirectionFromLocale(
            ConfigurationCompat.getLocales(context.resources.configuration).get(0)
        ) == View.LAYOUT_DIRECTION_RTL
}
```

**`iosMain/domain/provider/LocaleProvider.ios.kt`**
```kotlin
class IosLocaleProvider : LocaleProvider {
    override val languageTag: String
        get() = NSLocale.preferredLanguages.firstOrNull()
            ?.let { it as? String }
            ?: "en"

    override val isRtl: Boolean
        get() = NSLocale.characterDirectionForLanguage(
            NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
        ) == NSLocaleLanguageDirectionRightToLeft
}
```

---

### Task 2: Inject locale instruction in `GeminiPromptBuilder`

Add `LocaleProvider` as a constructor parameter. Prepend to every prompt:

```kotlin
private fun localeInstruction(): String {
    val tag = localeProvider.languageTag
    val rtlHint = if (localeProvider.isRtl) " Use right-to-left text conventions." else ""
    return "Respond entirely in the language identified by BCP 47 tag \"$tag\".$rtlHint\n\n"
}
```

Prepend `localeInstruction()` at the top of `buildAreaPortraitPrompt()` and any future `buildChatPrompt()`.

**Fallback behaviour:** If `languageTag` is blank or unrecognised, Gemini defaults to English naturally — no explicit fallback needed.

---

### Task 3: Koin wiring

**`PlatformModule.android.kt`** — add inside `platformModule { }`:
```kotlin
single<LocaleProvider> { AndroidLocaleProvider(androidContext()) }
```

**`PlatformModule.ios.kt`** — add inside `platformModule { }`:
```kotlin
single<LocaleProvider> { IosLocaleProvider() }
```

**`DataModule.kt`** — update `GeminiPromptBuilder` registration:
```kotlin
single { GeminiPromptBuilder(get()) }  // get() resolves LocaleProvider
```

---

### Task 4: Unit tests — `GeminiPromptBuilderLocaleTest`

```kotlin
class FakeLocaleProvider(
    override val languageTag: String,
    override val isRtl: Boolean = false
) : LocaleProvider

class GeminiPromptBuilderLocaleTest {

    @Test
    fun `prompt contains Arabic locale instruction`() {
        val builder = GeminiPromptBuilder(FakeLocaleProvider("ar", isRtl = true))
        val prompt = builder.buildAreaPortraitPrompt("Dubai", AreaContext(...))
        assertContains(prompt, "\"ar\"")
        assertContains(prompt, "right-to-left")
    }

    @Test
    fun `prompt contains Spanish locale instruction`() {
        val builder = GeminiPromptBuilder(FakeLocaleProvider("es"))
        val prompt = builder.buildAreaPortraitPrompt("Madrid", AreaContext(...))
        assertContains(prompt, "\"es\"")
    }

    @Test
    fun `prompt contains Portuguese locale instruction`() {
        val builder = GeminiPromptBuilder(FakeLocaleProvider("pt"))
        val prompt = builder.buildAreaPortraitPrompt("Lisbon", AreaContext(...))
        assertContains(prompt, "\"pt\"")
    }

    @Test
    fun `English locale produces no RTL hint`() {
        val builder = GeminiPromptBuilder(FakeLocaleProvider("en"))
        val prompt = builder.buildAreaPortraitPrompt("London", AreaContext(...))
        assertContains(prompt, "\"en\"")
        assertFalse(prompt.contains("right-to-left"))
    }
}
```

---

## Acceptance Criteria

1. Device set to Arabic → Gemini area portrait returns Arabic text across all six vibes
2. Device set to Spanish → portrait returns Spanish
3. Device set to Portuguese → portrait returns Portuguese
4. Device set to English (or any unsupported locale) → portrait returns English (Gemini default)
5. `GeminiPromptBuilderLocaleTest` — all 4 tests pass
6. No change to response parsing, caching, or UI behaviour
