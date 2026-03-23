---
title: 'Ambient Header Polish — Flag, Ambient Ticker & Humor Tone'
slug: 'ambient-header-polish'
created: '2026-03-22'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (KMP)'
  - 'Jetpack Compose Multiplatform'
  - 'Koin DI'
  - 'Ktor HTTP client'
  - 'Open-Meteo (weather API, already integrated)'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/util/GeoUtils.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/DiscoveryHeader.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/RotatingMetaTicker.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
code_patterns:
  - 'sealed class MetaLine(priority: Int) — extend with new variant'
  - 'buildMetaLines() — add new param, insert at priority 2'
  - 'MapUiState.Ready — add nullable field with default'
  - 'fetchAdvisory() stores countryCode independently from advisory result'
  - 'countryCodeToFlag — pure function, Unicode regional indicator math, in GeoUtils.kt'
  - 'MapScreen computes timeText/weatherText as remember() blocks before buildMetaLines call'
  - 'isRemote extracted to single val, referenced by all downstream remember() blocks'
  - 'welcomeBeatKey pattern in RotatingMetaTicker prevents spurious beat re-fires'
test_patterns:
  - 'commonTest unit tests under composeApp/src/commonTest/kotlin/com/harazone/'
  - 'Pure functions tested directly (no mocks needed)'
  - 'buildMetaLines tested for correct variant insertion'
---

# Tech-Spec: Ambient Header Polish — Flag, Ambient Ticker & Humor Tone

**Created:** 2026-03-22

## Overview

### Problem Statement

Remote exploration lacks environmental grounding. Three separate gaps:

1. **No country signal in the header.** When a user teleports to Marrakech, the CollapsedBar shows "Marrakech" with no flag — the user has no visual cue that they are outside their home country.
2. **No ambient context on remote landing.** The MetaTicker shows weather/time in the `MetaLine.Default` line (priority 6) mixed with visit tag. On remote landing there is no dedicated "welcome beat" that grounds the user in the area's local time and conditions before rotation starts.
3. **Undifferentiated AI tone.** The Gemini companion is warm and informative by default but has no explicit rule to stay non-playful unless the context calls for it. Entertainment/nightlife queries deserve playful energy; everything else deserves calm warmth.

### Solution

Three small, focused changes that ship together as one implementation pass:

1. **Flag emoji** — derive from `areaCountryCode` (stored when `reverseGeocodeInfo` is called for advisory). Render left of area name in `NormalRow`. Show only when `isRemote`.
2. **`MetaLine.AmbientContext`** — new priority-2 MetaLine variant. Built from already-fetched `WeatherState.utcOffsetSeconds` + temp + emoji — **no new API call required**. Welcome beat: first rotation delay is 7 000 ms (not 4 000 ms) when `AmbientContext` is the leading line.
3. **Humor tone directive** — one sentence appended to `personaBlock()` in `GeminiPromptBuilder`. Zero code change beyond the string.

### Scope

**In Scope:**
- Country flag emoji in `CollapsedBar → NormalRow`, remote areas only
- `MetaLine.AmbientContext` sealed variant + `buildMetaLines` extension + welcome beat in `RotatingMetaTicker`
- Ambient context computed from existing `WeatherState.utcOffsetSeconds` (Open-Meteo already returns this)
- One-line humor/tone directive in `personaBlock()`
- Unit tests: `countryCodeToFlag`, `buildMetaLines` with ambient variant

**Out of Scope:**
- Google Time Zone API (not needed — Open-Meteo `utc_offset_seconds` is sufficient)
- Teleportation UX / Save·Go·Been lifecycle (separate spec)
- Universal companion chat redesign (separate spec)
- Proactive humor, rate limiting, fun-fact caching (explicitly dropped in BRAINSTORM #10)
- iOS/Android platform-specific timezone APIs

---

## Context for Development

### Codebase Patterns

**MetaLine sealed class** (`MetaLine.kt`):
- All variants extend `sealed class MetaLine(val priority: Int)`
- Priority 1 = safety (fixed), 2 = remote context, 3 = vibe filter, 4 = nudge, 5 = poi highlight, 6 = default
- `buildMetaLines()` is a free function in the same file that constructs the sorted list
- Kotlin's `sortedBy` is stable — insertion order preserved for equal priorities
- Simple text variants follow pattern: `data class Foo(val text: String) : MetaLine(N)`
- `MetaLine.text` and `MetaLine.isFixed()` are extension properties in the same file
- `RotatingMetaTicker.displayColor()` is an extension function in `RotatingMetaTicker.kt`

**MapScreen meta-line construction** (`MapScreen.kt`, ~lines 379–424):
- `weatherText` and `timeText` are `remember()` blocks computed before `buildMetaLines()` call
- `timeText` already uses `utcOffsetSeconds` to show local area time (not user's device time)
- `currentTimeMillis()` is an `expect fun` in `TimeOfDay.kt` — already available in commonMain
- `isRemote` is derived inline: `state.showMyLocation && state.gpsLatitude != state.latitude`

**Advisory / country code** (`MapViewModel.kt`, `fetchAdvisory()` ~line 1856):
- `geocodingProvider.reverseGeocodeInfo(lat, lng)` returns `ReverseGeocodeInfo(countryCode, countryName, regionName)`
- `geoInfo.countryCode` is already obtained; currently only used for advisory fetch
- **Fix (F2):** `areaCountryCode` must be stored directly from `geoInfo` before the advisory call — NOT inside `onSuccess`. Advisory fetch can fail (network, quota, unsupported country) independently of geocoding. Store countryCode in a separate `_uiState.update` call immediately after `geoInfo` is obtained.

**Flag emoji math** (standard Unicode, KMP-safe):
- Regional Indicator letters: U+1F1E6 (🇦) through U+1F1FF (🇿)
- Formula: `base = 0x1F1E6 - 'A'.code`; each letter becomes `base + char.uppercaseChar().code`
- Two code points concatenated = flag emoji (e.g., "MA" → 🇲🇦)
- Use `String(intArrayOf(cp1, cp2), 0, 2)` — **NOT** `appendCodePoint`. `appendCodePoint` is JVM-only and will fail to compile on Kotlin Native (iOS). `String(IntArray, offset, count)` is the correct KMP-safe constructor.

**GeminiPromptBuilder** (`GeminiPromptBuilder.kt`):
- `personaBlock(areaName)` returns a multi-line string — append tone directive as final line
- The block is assembled in `buildChatSystemContext()` via `listOf(personaBlock(...), ...)`

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt` | Sealed class + `buildMetaLines` — add `AmbientContext` variant and param |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/RotatingMetaTicker.kt` | Render + `displayColor()` — add `AmbientContext` color + welcome beat delay |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | Add `areaCountryCode: String? = null` to `Ready` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | `fetchAdvisory()` — add `areaCountryCode` to state copy |
| `composeApp/src/commonMain/kotlin/com/harazone/util/GeoUtils.kt` | Add `countryCodeToFlag()` pure function (already exists, append to it) |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Compute `ambientContextText`, `countryFlag`; pass to `buildMetaLines` and `DiscoveryHeader` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/DiscoveryHeader.kt` | Thread `countryFlag: String?` param through to `NormalRow` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | Append tone directive to `personaBlock()` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/TimeOfDay.kt` | Reference — `currentTimeMillis()` expect fun already available |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/WeatherState.kt` | Reference — `utcOffsetSeconds: Int?` already on model |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/MapTilerGeocodingProvider.kt` | Reference — `ReverseGeocodeInfo.countryCode: String` |

### Technical Decisions

- **No Google Time Zone API** — `WeatherState.utcOffsetSeconds` (from Open-Meteo `timezone=auto`) is already fetched and stored. Reuse it for ambient local time computation. This eliminates an API dependency entirely.
- **AmbientContext priority = 2, inserted first among priority-2 lines** — Kotlin's `sortedBy` is stable; inserting `AmbientContext` before `RemoteContext` in `buildMetaLines` guarantees it is `metaLines[0]` when remote, which the welcome beat check in `RotatingMetaTicker` relies on.
- **Welcome beat implementation** — introduces `hasShownWelcomeBeat` state (reset by `remember(welcomeBeatKey)`), a `welcomeBeatKey: Any?` parameter on `RotatingMetaTicker`, and prop drilling of that key through `DiscoveryHeader → CollapsedBar → NormalRow`. The "one delay() change" from the original brainstorm was the end-state intent, but a naive `LaunchedEffect(metaLines)` key causes the beat to re-fire on every mid-session metaLines mutation. The `hasShownWelcomeBeat` + `welcomeBeatKey` pattern is the minimal correct solution.
- **countryFlag shown only when `isRemote`** — local users don't need their own country flagged. `isRemote = state.showMyLocation && state.gpsLatitude != state.latitude` is the existing condition.
- **`countryCodeToFlag` returns `String?`** — returns `null` for empty, malformed, or 1-char codes; caller guards with `?.let { ... }`.
- **`areaCountryCode` stored in `MapUiState.Ready`** — state flows to `MapScreen`; computing flag in the composable from state follows existing pattern (same as `areaCurrencyText`, `areaLanguageText`).

---

## Implementation Plan

### Tasks

> Tasks are ordered by dependency — foundational data model changes first, then ViewModel, then UI.

---

**Feature 1: Country Flag Emoji**

- [ ] **Task 1.1 — Add `areaCountryCode` to state**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add `val areaCountryCode: String? = null` to `MapUiState.Ready` data class (after `areaLanguageText`, before `savedLensActive`)
  - Notes: Nullable with default null — backward-compatible, no other callers need updating

- [ ] **Task 1.2 — Store country code in ViewModel**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action: In `fetchAdvisory()`, immediately after the `geoInfo` null-check (before calling `advisoryProvider.getAdvisory`), add a separate state update:
    ```kotlin
    // Store country code immediately — independent of advisory success/failure
    // Use .update for atomic read-modify-write (avoids race with concurrent weather/advisory updates)
    _uiState.update { state ->
        (state as? MapUiState.Ready)?.copy(areaCountryCode = geoInfo.countryCode) ?: state
    }
    ```
    Do NOT add `areaCountryCode` to the `onSuccess` copy — that call already happens above and the state will already have the code by then.
  - Notes: Advisory fetch is a separate network call that can fail while geocoding succeeded. Decoupling these ensures the flag appears even when advisory is unavailable (offline, unsupported region, quota exceeded).

- [ ] **Task 1.3 — Add `countryCodeToFlag()` pure function**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/util/GeoUtils.kt`
  - Action: Append the following function at the bottom of the existing `GeoUtils.kt` file:
    ```kotlin
    /**
     * Converts an ISO 3166-1 alpha-2 country code to its flag emoji.
     * Returns null for null, empty, or non-ASCII-letter inputs.
     * Example: "MA" → "🇲🇦", "US" → "🇺🇸"
     *
     * Uses surrogate pair math with String(CharArray) — the only genuinely KMP-safe
     * approach for SMP code points. Do NOT use appendCodePoint (JVM-only) or
     * String(IntArray, offset, count) (also JVM-only @InlineOnly).
     */
    fun countryCodeToFlag(code: String?): String? {
        if (code == null || code.length != 2 || !code.all { it in 'A'..'Z' || it in 'a'..'z' }) return null
        val base = 0x1F1E6 - 'A'.code
        val chars = CharArray(4)
        for (i in 0..1) {
            val cp = base + code[i].uppercaseChar().code
            val offset = cp - 0x10000
            chars[i * 2]     = (0xD800 or (offset shr 10)).toChar()   // high surrogate
            chars[i * 2 + 1] = (0xDC00 or (offset and 0x3FF)).toChar() // low surrogate
        }
        return String(chars)
    }
    ```
  - Notes: `String(CharArray)` is in Kotlin's common stdlib and compiles on JVM and Kotlin Native alike. Regional Indicator code points (U+1F1E6–U+1F1FF) are above U+FFFF, so each requires a surrogate pair (2 `Char` values). The ASCII guard (`it in 'A'..'Z' || it in 'a'..'z'`) rejects non-ASCII Unicode letters (e.g., "Ä", "π") that would produce garbage code points. `GeoUtils.kt` already exists and contains `haversineDistanceMeters` — country flag conversion is geo-related and belongs here.

- [ ] **Task 1.4 — Thread `countryFlag` through DiscoveryHeader → CollapsedBar → NormalRow**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/DiscoveryHeader.kt`
  - Action (3 changes in this file):
    1. Add `countryFlag: String? = null` param to `DiscoveryHeader()` composable signature (after `areaName`)
    2. Add `countryFlag: String? = null` param to `CollapsedBar()` private composable signature (after `areaName`)
    3. Add `countryFlag: String? = null` param to `NormalRow()` private composable signature (after `areaName`)
    4. Thread the param: `DiscoveryHeader` → passes `countryFlag` to `CollapsedBar` (in `NormalRow(...)` call within `CollapsedBar`); `CollapsedBar` → passes to `NormalRow`
    5. In `NormalRow`, in the `else` branch where `areaName` text is rendered (~line 379), prepend the flag **before** the `Text(areaName)`. Render as:
       ```kotlin
       if (countryFlag != null) {
           Text(text = countryFlag, ...)  // no trailing space in the string
           Spacer(Modifier.width(4.dp))   // single gap via Spacer only
       }
       ```
       Use the same text style as the area name but with no `width` constraint. Do NOT add a trailing space inside the `Text` string — the `Spacer` provides the gap. Using both would produce a double gap.

  - Notes: `SpinningRow` does not need the flag (it's in searching state). Only `NormalRow` needs it. `CollapsedBar` already handles `isSearchingArea` → `SpinningRow` vs `NormalRow` split — no changes needed there beyond threading the param.

- [ ] **Task 1.5 — Extract `isRemote`, compute `countryFlag`, pass from MapScreen**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action:
    1. Extract `isRemote` to a single top-level `val` (before all `remember` blocks that use it). Currently it is inlined in three separate places — consolidate to one:
       ```kotlin
       val isRemote = state.showMyLocation && state.gpsLatitude != state.latitude
       ```
    2. Replace every inline `state.showMyLocation && state.gpsLatitude != state.latitude` occurrence in the `remember` blocks for `metaLines`, `ambientContextText`, and `countryFlag` with this single `isRemote` val.
    3. Add the `countryFlag` derivation (no `remember` needed — pure function, cheap):
       ```kotlin
       val countryFlag = if (isRemote) countryCodeToFlag(state.areaCountryCode) else null
       ```
    4. Add `countryFlag = countryFlag` to the `DiscoveryHeader(...)` call.
    5. Pass `welcomeBeatKey = Pair(state.areaName, isRemote)` to `DiscoveryHeader(...)`, which threads it through to `RotatingMetaTicker`. Add `welcomeBeatKey: Any? = null` param to `DiscoveryHeader` → `CollapsedBar` → `NormalRow` → `RotatingMetaTicker(welcomeBeatKey = welcomeBeatKey)`.
    6. Add import `com.harazone.util.countryCodeToFlag` at the top of the file.
  - Notes: `isRemote` is a derived boolean that should be computed once. Duplication here is a maintenance risk — if the definition ever changes (e.g., tolerance threshold added), all callsites must be updated in sync.

---

**Feature 2: Ambient Context in Ticker**

- [ ] **Task 2.1 — Add `MetaLine.AmbientContext` variant**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt`
  - Action (4 changes in this file):
    1. Add new variant after `LanguageContext` (before `VibeFilter`):
       ```kotlin
       /** Priority 2 — remote area ambient context: local time, temp, day/night. Teal text. */
       data class AmbientContext(val text: String) : MetaLine(2)
       ```
    2. In the `MetaLine.text` extension property `when` block, add case:
       `is MetaLine.AmbientContext -> text`
       (place after `LanguageContext -> text`)
    3. In `buildMetaLines()` signature, add param: `ambientContextText: String? = null`
    4. In `buildMetaLines()` body, in the "Priority 2: Remote/teleported area" section, add **before** the `RemoteContext` insertion:
       ```kotlin
       if (isRemote && ambientContextText != null) {
           lines.add(MetaLine.AmbientContext(ambientContextText))
       }
       ```
  - Notes: `sortedBy { it.priority }` is stable — `AmbientContext` inserted first among priority-2 lines will appear at index 0 in the sorted output when remote. This is what the welcome beat relies on. `isFixed()` does not need a new case — the `when` exhaustive check returns `false` for all non-`SafetyWarning` variants by default.

- [ ] **Task 2.2 — Add `AmbientContext` display color + welcome beat in RotatingMetaTicker**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/RotatingMetaTicker.kt`
  - Action (3 changes in this file):
    1. Add `welcomeBeatKey: Any? = null` parameter to `RotatingMetaTicker()` composable signature.
    2. In `MetaLine.displayColor()` extension, add case before `MetaLine.Default`:
       ```kotlin
       is MetaLine.AmbientContext -> Color(0xFF26A69A)
       ```
    3. Replace the existing `LaunchedEffect(metaLines)` block with the welcome-beat version that prevents spurious re-fires when metaLines changes mid-session (e.g., safety warning resolves, currency line loads):
       ```kotlin
       // hasShownWelcomeBeat resets to false when welcomeBeatKey changes (new area arrival).
       // This prevents the beat from re-firing when mid-session metaLines mutations occur.
       var hasShownWelcomeBeat by remember(welcomeBeatKey) { mutableStateOf(false) }

       LaunchedEffect(metaLines) {
           currentIndex = 0
           if (metaLines.size > 1) {
               val isAmbientFirst = metaLines.first() is MetaLine.AmbientContext
               val firstDelay = if (isAmbientFirst && !hasShownWelcomeBeat) {
                   hasShownWelcomeBeat = true
                   7_000L
               } else {
                   4_000L
               }
               delay(firstDelay)
               currentIndex = 1
               while (true) {
                   delay(4_000L)
                   currentIndex = (currentIndex + 1) % metaLines.size
               }
           }
       }
       ```
  - Notes: The `remember(welcomeBeatKey) { mutableStateOf(false) }` pattern is key. `remember` with a key resets the state value to `false` whenever `welcomeBeatKey` changes. Pass `welcomeBeatKey = Pair(state.areaName, isRemote)` from `MapScreen` — this key changes on every new area arrival, resetting the beat. It does NOT change when metaLines content mutates mid-session (advisory appears, currency resolves), so the beat fires exactly once per remote landing.

- [ ] **Task 2.3a — Extract `buildAmbientContextText` pure function to `GeoUtils.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/util/GeoUtils.kt`
  - Action: Append this function after `countryCodeToFlag`:
    ```kotlin
    /**
     * Builds the ambient context string for the MetaTicker on remote area landing.
     * Returns a formatted string: e.g. "🌆 6:30 PM · 72°F ☀️"
     *
     * @param utcOffsetSeconds UTC offset for the remote area (from WeatherState)
     * @param nowMs            Current UTC time in milliseconds
     * @param temperatureF     Temperature in Fahrenheit
     * @param weatherEmoji     Weather condition emoji (from WeatherState)
     */
    fun buildAmbientContextText(
        utcOffsetSeconds: Int,
        nowMs: Long,
        temperatureF: Int,
        weatherEmoji: String,
    ): String {
        val localMs = nowMs + utcOffsetSeconds * 1000L
        val totalMinutes = (localMs / 60_000) % (24 * 60)
        val localHour = (totalMinutes / 60).toInt()
        val localMinute = (totalMinutes % 60).toInt()
        val dayNightEmoji = when (localHour) {
            in 5..7   -> "\uD83C\uDF05" // 🌅
            in 8..16  -> "\u2600\uFE0F" // ☀️
            in 17..19 -> "\uD83C\uDF06" // 🌆
            else      -> "\uD83C\uDF19" // 🌙
        }
        val amPm = if (localHour < 12) "AM" else "PM"
        val displayHour = when {
            localHour == 0  -> 12
            localHour > 12  -> localHour - 12
            else            -> localHour
        }
        return "$dayNightEmoji $displayHour:${localMinute.toString().padStart(2, '0')} $amPm \u00b7 ${temperatureF}\u00B0F $weatherEmoji"
    }
    ```
  - Notes: Pure function — no Compose, no Android APIs, testable in commonTest. All parameters are primitives/Strings. `GeoUtils.kt` is the right home: it already houses `countryCodeToFlag` and `haversineDistanceMeters`.

- [ ] **Task 2.3b — Call `buildAmbientContextText` from MapScreen `remember` block**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action:
    1. Add a `remember` block after the existing `timeText` block:
       ```kotlin
       val ambientContextText = remember(isRemote, state.weather) {
           if (!isRemote) return@remember null
           val w = state.weather ?: return@remember null
           val utcOffset = w.utcOffsetSeconds ?: return@remember null
           buildAmbientContextText(
               utcOffsetSeconds = utcOffset,
               nowMs = com.harazone.ui.components.currentTimeMillis(),
               temperatureF = w.temperatureF,
               weatherEmoji = w.emoji,
           )
       }
       ```
    2. Add `ambientContextText` to the `remember(...)` key list for `metaLines` (after `state.isSurpriseSearching`).
    3. Pass to `buildMetaLines(...)` call: add `ambientContextText = ambientContextText`.
    4. Add import `com.harazone.util.buildAmbientContextText`.
  - Notes: The `remember` lambda is now a thin coordinator — guards for `isRemote`, `weather`, and `utcOffsetSeconds`, then delegates to the pure function. All business logic lives in `GeoUtils.kt` and is independently testable.

---

**Feature 3: Humor / Tone Directive**

- [ ] **Task 3.1 — Add tone directive to `personaBlock()`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: In `personaBlock()`, append the following line to the returned string (after the `NO CHAINS` line):
    ```
    TONE: Match your tone to the user's query context. Be warm and informative by default. Only be playful when the user is exploring entertainment, nightlife, or humor-related topics.
    ```
    The full updated return value of `personaBlock()`:
    ```kotlin
    private fun personaBlock(areaName: String): String =
        """You are a passionate local who has lived in "$areaName" for 20 years. You love showing visitors things they would NEVER find on Google Maps. Your mission: surface the genuinely unique, memorable, and local.
    UNIQUENESS RULE: Only include places with a genuine story or character. A place is not interesting because it exists — it is interesting because of what it means to this area.
    FOOD GATE: Food and drink places are welcome if unique, with a story, or offering something you cannot find anywhere else. No more than 30% food POIs unless the area is genuinely food-destination-famous.
    WHY SPECIAL REQUIRED: Every place you mention must have a compelling reason. "Popular" or "nice" are not acceptable.
    NO CHAINS: Never recommend chain brands, international franchises, or tourist traps.
    TONE: Match your tone to the user's query context. Be warm and informative by default. Only be playful when the user is exploring entertainment, nightlife, or humor-related topics."""
    ```
  - Notes: This is an additive change to an existing multi-line string — no structural changes. The TONE line is placed last in the persona block, after NO CHAINS, so it reads as a behavioral modifier on top of all the persona rules above it.

---

**Tests**

- [ ] **Task 4.1 — Unit test: `countryCodeToFlag`**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/util/CountryFlagTest.kt` (new file)
  - Action: Create test class `CountryFlagTest` with the following cases:
    - `"MA"` → `"🇲🇦"` (Morocco)
    - `"US"` → `"🇺🇸"` (United States)
    - `"GB"` → `"🇬🇧"` (UK)
    - `""` → `null`
    - `"U"` → `null` (1 char)
    - `"123"` → `null` (all digits)
    - `"A1"` → `null` (mixed letter + digit)
    - `null` → `null`
    - `"ma"` → `"🇲🇦"` (lowercase — function uppercases)
  - Notes: Pure function test — no mocks, no coroutines, no Android APIs. Follow existing test file structure in `commonTest`.

- [ ] **Task 4.2 — Unit test: `buildMetaLines` with `AmbientContext`**
  - File: Extend or create in `composeApp/src/commonTest/kotlin/com/harazone/domain/model/` (follow existing `BucketTypeTest` naming convention)
  - Action: Add test `buildMetaLines_remote_with_ambient_returns_ambient_at_index_0` that:
    - Calls `buildMetaLines(isRemote = true, ambientContextText = "🌙 11:30 PM · 68°F ⛅", homeCity = "Miami", remoteDistance = "8100 km")`
    - Asserts result is not empty
    - Asserts `result[0]` is `MetaLine.AmbientContext`
    - Asserts `result[0].text == "🌙 11:30 PM · 68°F ⛅"`
    - Asserts `result[1]` is `MetaLine.RemoteContext`
  - Also add `buildMetaLines_local_no_ambient` that passes `isRemote = false, ambientContextText = "..."` and asserts no `AmbientContext` in result.

- [ ] **Task 4.4 — Unit test: `buildAmbientContextText`**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/util/AmbientContextTextTest.kt` (new file)
  - Action: Create test class `AmbientContextTextTest` with the following cases:
    - UTC+9 (`utcOffsetSeconds = 32400`), `nowMs` for 9:00 AM local → asserts result contains "☀️", "9:00 AM", `"°F"`
    - UTC+0 (`utcOffsetSeconds = 0`), `nowMs` for midnight local → asserts result contains "🌙", "12:00 AM"
    - UTC+5:30 (`utcOffsetSeconds = 19800`), `nowMs` for 17:30 local → asserts result contains "🌆", "5:30 PM"
    - UTC-5 (`utcOffsetSeconds = -18000`), `nowMs` for 6:00 AM local → asserts result contains "🌅", "6:00 AM"
    - Temperature and emoji pass-through: `temperatureF = 72, weatherEmoji = "⛅"` → result contains "72°F" and "⛅"
  - Compute `nowMs` as `(targetLocalHour * 3600L + targetLocalMinute * 60L - utcOffsetSeconds) * 1000L`. For cases with no minutes `targetLocalMinute = 0`; for the UTC+5:30 case use `targetLocalHour = 17, targetLocalMinute = 30` (i.e. `nowMs = (17 * 3600L + 30 * 60L - 19800) * 1000L`).
  - Notes: `WeatherState.temperatureF` is declared `Int` — the `buildAmbientContextText` signature `temperatureF: Int` matches exactly (verified in `WeatherState.kt`).
  - Notes: Pure function — no mocks, no coroutines, no Compose. Directly exercises the logic that was untestable when it lived inside a `remember` lambda in `MapScreen`.

- [ ] **Task 4.3 — Unit test: `GeminiPromptBuilder` tone directive**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/` (new or existing test file for `GeminiPromptBuilder`)
  - Action: Add test `buildChatSystemContext_containsToneDirective` that:
    - Creates a `GeminiPromptBuilder` instance
    - Calls:
      ```kotlin
      val result = GeminiPromptBuilder().buildChatSystemContext(
          areaName = "Marrakech",
          pois = emptyList(),
          intent = ChatIntent.DISCOVER,
          engagementLevel = EngagementLevel.FRESH,
          saves = emptyList(),
          tasteProfile = TasteProfile(
              strongAffinities = emptyList(),
              emergingInterests = emptyList(),
              notableAbsences = emptyList(),
              diningStyle = null,
              totalSaves = 0,
          ),
          poiCount = 0,
          languageTag = "en",
      )
      ```
    - Asserts `result.contains("TONE: Match your tone to the user's query context.")`
  - Notes: `TasteProfile` is a plain data class with no companion/factory — construct directly. Pure function test, no mocks.

---

### Acceptance Criteria

**Feature 1 — Country Flag**

- [ ] **AC1:** Given user teleports to Marrakech (country code "MA" stored in `areaCountryCode`), when `CollapsedBar` is in `NormalRow` state (not searching, not GPS acquiring), then flag emoji 🇲🇦 appears to the left of "Marrakech" text.
- [ ] **AC2:** Given user is viewing their home city (GPS position matches camera position, i.e. `!isRemote`), when `CollapsedBar` renders, then no flag emoji is shown.
- [ ] **AC3:** Given `countryCodeToFlag("US")` is called, then it returns the string "🇺🇸".
- [ ] **AC4:** Given `countryCodeToFlag(null)`, `countryCodeToFlag("")`, or `countryCodeToFlag("X")` is called, then it returns `null`.
- [ ] **AC5:** Given `areaCountryCode` is null (geocoding not yet completed for the current area), when `CollapsedBar` renders, then no flag emoji appears (no crash, no empty string artifact). Advisory success or failure has no effect on this.
- [ ] **AC6:** Given user is in GPS-acquiring state (`isGpsAcquiring = true`) or geocode pending, when `CollapsedBar` renders, then neither the area name nor the flag is shown (existing shimmer/text behavior unchanged).

**Feature 2 — Ambient Context Ticker**

- [ ] **AC7:** Given user is in their home area (`isRemote = false`), when `buildMetaLines` is called, then no `MetaLine.AmbientContext` is present in the result regardless of `ambientContextText`.
- [ ] **AC8:** Given user teleports to Tokyo (UTC+9, `utcOffsetSeconds = 32400`), weather is 72°F ⛅, and local time resolves to 11:30 PM, when `buildMetaLines` is called with `isRemote = true`, then `result[0]` is `MetaLine.AmbientContext` with text containing "🌙", "11:30 PM", "72°F", and "⛅".
- [ ] **AC9:** Given `MetaLine.AmbientContext` is `result[0]` (first line on remote landing), when `RotatingMetaTicker`'s `LaunchedEffect` fires, then the first line is displayed for 7 seconds before advancing to index 1.
- [ ] **AC10:** Given `MetaLine.AmbientContext` is NOT the first line (e.g., safety warning is first), when `RotatingMetaTicker`'s `LaunchedEffect` fires, then the first rotation delay is 4 seconds (unchanged behavior).
- [ ] **AC11:** Given `state.weather` is null (weather not yet loaded), when `ambientContextText` is computed in `MapScreen`, then it returns null and no `AmbientContext` line is inserted.
- [ ] **AC12:** Given `WeatherState.utcOffsetSeconds` is null, when `ambientContextText` is computed, then it returns null (guard clause fires).

**Feature 3 — Humor Tone**

- [ ] **AC13:** Given `buildChatSystemContext(...)` is called, when the result is inspected, then it contains the substring `"TONE: Match your tone to the user's query context."`.
- [ ] **AC14:** Given user sends a message asking about comedy clubs or nightlife, when Gemini responds (manual test), then the tone is noticeably more playful than a response about a historic monument.

---

## Additional Context

### Dependencies

- No new external libraries required.
- No new API integrations required (Open-Meteo UTC offset already fetched).
- Feature 2 depends on `state.weather` being populated (Open-Meteo call). If weather is not yet loaded, ambient line simply doesn't appear — graceful degradation.
- Feature 1 depends on `reverseGeocodeInfo` having resolved for the current area (called inside `fetchAdvisory()`). The flag will not show until the geocoding call completes. Advisory success/failure has no bearing on whether the flag appears — countryCode is stored before the advisory call.

### Testing Strategy

**Automated:**
- `CountryFlagTest` — unit test, commonTest, covers all edge cases of `countryCodeToFlag()` (Task 4.1)
- `buildMetaLines` test — unit test, commonTest, covers ambient insertion at position 0 and non-remote suppression (Task 4.2)
- `AmbientContextTextTest` — unit test, commonTest, covers `buildAmbientContextText()` with known UTC offsets, hour boundaries, and pass-through fields (Task 4.4)
- `GeminiPromptBuilder` tone directive test — unit test, commonTest, asserts TONE line present in `buildChatSystemContext` output (Task 4.3)

**Manual (run on device after implementation):**
1. Teleport to Marrakech → verify 🇲🇦 appears left of "Marrakech" in collapsed bar
2. Teleport to Tokyo → verify ticker shows ambient line first (🌙 or 🌅 depending on time), pins for ~7s, then rotates
3. Return to home area → verify flag disappears, ambient line disappears
4. Open chat, ask about a comedy club → verify warmer/playful tone vs. asking about a historic site
5. Open chat, ask about a war memorial → verify calm, informative tone (no jokes)

### Notes

- **Welcome beat fires once per area arrival:** The `hasShownWelcomeBeat` state (reset by `remember(welcomeBeatKey)`) ensures the 7-second beat fires at most once per remote area arrival, not on every mid-session `metaLines` change (e.g., safety advisory resolving, currency line loading). `welcomeBeatKey = Pair(areaName, isRemote)` changes when the user arrives at a new area.
- **Flag disappears during searching state:** `CollapsedBar` switches to `SpinningRow` when `isSearchingArea = true`. The flag is only in `NormalRow`, so it naturally disappears during discovery — no extra handling needed.
- **AmbientContext color = same teal as CurrencyContext/LanguageContext:** `Color(0xFF26A69A)`. This maintains visual coherence — all remote context lines appear in the same teal.
- **Surrogate pair math for flag emoji:** Regional Indicator code points (U+1F1E6–U+1F1FF) are in the Supplementary Multilingual Plane (above U+FFFF) and require surrogate pairs in Kotlin strings. Use `String(CharArray)` with explicit high/low surrogate calculation — this is in Kotlin's common stdlib and safe on both JVM and Native. Neither `appendCodePoint` nor `String(IntArray, offset, count)` are KMP-safe (both are JVM-only).
- **BRAINSTORM #6 "Disappears for local exploration"** — implemented by tying flag to `isRemote`. If user explicitly searches their own city (which would make `isRemote = false`), no flag shown. Correct behavior.
- **Day/night emoji selection for ambient line:** Uses Unicode escape sequences to stay consistent with existing codebase style (`MetaLine.kt` uses `\uD83C\uDFAD` for 🎭). Mapping: `🌅` dawn (5–7), `☀️` day (8–16), `🌆` dusk (17–19), `🌙` night (else).
- **ACCEPTED RISK — F5, float equality for isRemote:** `gpsLatitude != latitude` is an existing pattern inherited from the codebase. Float equality for coordinates can be unreliable at the sub-meter level, but in practice the GPS vs camera distinction is large (different cities) so false-positives are extremely unlikely. Out of scope for this spec to refactor.
- **ACCEPTED RISK — F9, AmbientContext as sole line:** `buildMetaLines` always adds a `MetaLine.Default` line unconditionally (the `val defaultText = buildString { ... }` block at the bottom). So `metaLines.size` will always be ≥ 1 when AmbientContext is present, and `size > 1` when both AmbientContext and Default are present. AmbientContext can never be the only line.
- **ACCEPTED RISK — F10, stale weather on teleport:** If `state.weather` still holds the previous area's data when the user first arrives remote, the ambient line may briefly show the old area's time/temp. Weather is fetched as part of area load, so the stale window is short (typically <1s). When the new weather resolves, `state.weather` changes → `ambientContextText` recomputes → metaLines updates → ticker shows fresh data. The stale period is too brief to be a real UX issue.
- **ACCEPTED — F7, no timing unit test for welcome beat:** Coroutine timing in `LaunchedEffect` requires a `TestCoroutineScheduler` and a Compose test environment. This is out of scope for the current test pass. Welcome beat correctness is verified via manual test (teleport to remote area, observe 7s pin).
- **DESIGN DECISION — L3, `welcomeBeatKey` prop-drilled through 3 layers:** `DiscoveryHeader → CollapsedBar → NormalRow → RotatingMetaTicker` — only the ticker uses it. A `CompositionLocal` would eliminate the param chain but adds indirection and non-obvious scoping for a single nullable value. Prop drilling is the standard Compose pattern for non-global, non-theme values. The param is typed `Any?` with a default of `null` so all intermediate signatures are a one-word change. Accepted as-is.
