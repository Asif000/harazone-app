---
title: 'Move Here — Phase 1a (Gemini-Powered Resident Lens)'
slug: 'move-here-phase-1a'
created: '2026-03-26'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'Gemini API', 'Google Places API', 'Open-Meteo', 'SQLDelight', 'Koin', 'MapLibre']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/DiscoveryMode.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/ResidentData.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/FeatureFlags.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/DiscoveryHeader.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ResidentDashboardCard.kt (NEW)'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/RotatingMetaTicker.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
code_patterns:
  - 'Sealed class state (MapUiState, MetaLine, ChatEntryPoint)'
  - 'MutableStateFlow + update{} in ViewModels'
  - 'Modular prompt blocks in GeminiPromptBuilder'
  - 'Priority-based MetaLine rotation (lower number = higher priority)'
  - 'expect/actual for platform-specific (MapComposable, PlatformBackHandler)'
  - 'GooglePlacesProvider with 24h TTL cache'
  - 'Koin DI with factory/single in UiModule'
test_patterns:
  - 'kotlin.test (Test, assertEquals, assertTrue, assertIs)'
  - 'runTest + UnconfinedTestDispatcher + TestCoroutineScheduler'
  - 'Fakes in commonTest/fakes/ (FakeAreaRepository, FakeLocationProvider, etc.)'
  - 'MapViewModelTest, ChatViewModelTest, GeminiPromptBuilderTest'
---

# Tech-Spec: Move Here — Phase 1a (Gemini-Powered Resident Lens)

**Created:** 2026-03-26
**Brainstorm source:** `_bmad-output/brainstorming/brainstorming-session-2026-03-25-001.md` (decisions LOCKED)

## Overview

### Problem Statement

AreaDiscovery helps users discover and visit places, but has no support for relocation intent. Users considering moving to an area must leave the app and cobble together data from 10+ sources (Numbeo, Zillow, government sites, Reddit). There's no way to shift the AI from "tourist guide" to "relocation advisor."

### Solution

Add a "Move Here" mode that transforms the entire app experience from traveler to resident lens — area-scoped, explicit opt-in, with 9 data categories powered by Gemini + existing APIs. A new `DiscoveryMode` enum gates all behavior changes behind a feature flag.

### Scope

**In Scope:**
- `DiscoveryMode.TRAVELER` / `.RESIDENT` state architecture (area-scoped)
- "Move Here" button on area header + mode indicator badge
- 9 data categories (Gemini + Open-Meteo + Google Places)
- 6 UI surfaces: ticker transform, dashboard card, pin shift, detail page section, chat persona shift, mode indicator
- Trust signals: attribution, confidence meter, AI badge, verify links
- Feature flag wrapping everything
- Prompt layering (resident overlay on traveler base)
- Origin-aware framing via GPS/locale

**Out of Scope:**
- Numbeo API (Phase 1b)
- Budget simulator, neighborhood matchmaker, cost shock alerts (Phase 1b)
- AI-inferred intent detection (Phase 2)
- Comparison view (Phase 2)
- Monetization
- "Move" as 4th lifecycle state on SavedPoi (separate spec — Save/Go/Been story)
- Disclaimer modal on first activation (deferred — inline disclaimers sufficient for Phase 1a)

## Context for Development

### Codebase Patterns

- **State management:** Sealed classes + `MutableStateFlow` + `update{}` in ViewModels (MVVM)
- **Platform abstraction:** expect/actual for `MapComposable`, `PlatformBackHandler`, etc.
- **Prompt system:** Modular prompt blocks in `GeminiPromptBuilder` — persona + context + rules, delimiter-based output segmentation (`---BUCKET---`, `---POIS---`)
- **Ticker:** `MetaLine` sealed class with priority-based rotation (1=highest) in `RotatingMetaTicker`, 4s crossfade cycle
- **Chat context:** `ChatEntryPoint` sealed class gates context framing per surface, `buildChatSystemContext()` assembles 11 blocks
- **Places enrichment:** `GooglePlacesProvider` with 24h TTL cache in SQLDelight, text search with location bias
- **Pin system:** Vibe-colored pins via expect/actual `MapComposable`, callback-based interaction
- **Area intelligence:** `GetAreaPortraitUseCase` → `AreaRepository` → `GeminiAreaIntelligenceProvider` — SSE streaming with bucket parsing
- **DI:** Koin `factory`/`single` in `UiModule.kt`

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/model/MetaLine.kt` | Ticker line types + `buildMetaLines()` — add resident variants |
| `domain/model/AreaContext.kt` | Context passed to prompts — add `discoveryMode` field |
| `ui/map/MapUiState.kt` | UI state sealed class — add `discoveryMode` + `residentData` |
| `ui/map/MapViewModel.kt` | Main ViewModel — mode toggle, resident data fetch, pin shift |
| `ui/map/ChatViewModel.kt` | Chat ViewModel — mode-aware prompt context |
| `data/remote/GeminiPromptBuilder.kt` | All Gemini prompts — add resident overlay block + resident data prompt |
| `data/remote/GeminiAreaIntelligenceProvider.kt` | Gemini SSE streaming — add resident data fetch method |
| `domain/provider/AreaIntelligenceProvider.kt` | Provider interface — add resident data method |
| `ui/map/components/DiscoveryHeader.kt` | Header bar — add "Move Here" button + mode indicator |
| `ui/map/components/AiDetailPage.kt` | POI detail — add resident proximity section |
| `ui/map/components/RotatingMetaTicker.kt` | Ticker display — already handles MetaLine variants |
| `ui/map/MapScreen.kt` | Screen composition — wire mode state + new components |

### Technical Decisions

1. **`DiscoveryMode` as enum, not sealed class** — only two states (TRAVELER/RESIDENT), no payload needed. Clean `when` exhaustiveness.
2. **Area-scoped mode via `Map<String, DiscoveryMode>`** — keyed by area name in `MapViewModel`. On area change: if new area has no explicit mode, defaults to TRAVELER. Existing RESIDENT areas are preserved.
3. **Feature flag as compile-time constant** — `FeatureFlags.MOVE_HERE_ENABLED`. Simple `if` guard at UI entry points. No RemoteConfig dependency (infra item #55 is separate).
4. **Resident data as separate Gemini call** — not merged into `buildAreaPortraitPrompt()`. Separate `buildResidentDataPrompt()` returns structured JSON for 9 categories. Keeps traveler flow untouched — zero regression risk.
5. **Trust signals inline, not overlay** — attribution text ("Gemini AI estimate · Mar 2026") rendered inline under each data point. `DataConfidence` enum (LOW/MEDIUM/HIGH) per category. `DataClassification` enum (STATIC/DYNAMIC/VOLATILE) determines citation format.
6. **Pin shift via Google Places Nearby Search** — daily-life POI types (grocery_store, school, hospital, transit_station, gym, pharmacy) queried via existing `GooglePlacesProvider`. New method `searchDailyLifePois()`, separate from existing `enrichPoi()`.
7. **Prompt layering, not replacement** — resident overlay appended to existing chat system context via new `residentContextBlock()` in `buildChatSystemContext()`. Traveler base prompt stays untouched.
8. **Origin detection via existing locale/GPS** — `LocaleProvider.countryCode` + GPS home coordinates give origin context. No user input needed. Progressive detail gathering through chat.
9. **No caching for Phase 1a** — resident data is fetched fresh per area activation. Caching deferred to Phase 1b when Numbeo adds structured data worth caching.

---

## Data Model Reference

### 9 Data Categories

| ID | Category | Source | Classification | Confidence default |
|----|----------|--------|----------------|-------------------|
| D1 | Rental prices | Gemini | Dynamic | MEDIUM |
| D2 | Buy prices | Gemini | Dynamic | MEDIUM |
| D3 | Cost of living index | Gemini | Dynamic | MEDIUM |
| D4 | Safety / crime stats | Gemini + existing safety spec | Dynamic | MEDIUM |
| D6 | Job market / top industries | Gemini | Dynamic | MEDIUM |
| D9 | Community vibe | Gemini | Static-ish | HIGH |
| D10 | Weather / climate | Open-Meteo (existing) | Static | HIGH |
| D12 | Visa / immigration | Gemini | Volatile | LOW |
| D21 | Cultural & community fit | Gemini + Google Places POIs | Static-ish | MEDIUM |

### Data Classification Citation Rules

| Classification | Citation format | Example |
|---------------|----------------|---------|
| STATIC | Source + year | "Gemini AI estimate · 2026" |
| DYNAMIC | Source + month | "Gemini AI estimate · Mar 2026" |
| VOLATILE | Source + date + "verify" + gov link | "Gemini AI estimate · Mar 2026 · Verify at [country].gov/visa" |

### Daily-Life POI Types (Pin Shift)

When in RESIDENT mode, Google Places API is queried for these types near the area center:
- `grocery_store` — Supermarkets, grocery
- `school` — Schools, education
- `hospital` — Hospitals, clinics
- `transit_station` — Public transit
- `gym` — Fitness, sports
- `pharmacy` — Pharmacies, drugstores

These are displayed as separate pin layer alongside (not replacing) existing traveler pins. User can toggle between "Explore" and "Daily Life" pin views.

---

## Implementation Plan

### Tasks

#### Layer 1: Domain Models (no dependencies)

- [x] **Task 1: Create `DiscoveryMode` enum**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/DiscoveryMode.kt` (NEW)
  - Action: Create enum with two values:
    ```kotlin
    enum class DiscoveryMode { TRAVELER, RESIDENT }
    ```

- [x] **Task 2: Create `ResidentData` domain model**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/ResidentData.kt` (NEW)
  - Action: Create data classes for resident intelligence:
    ```kotlin
    enum class DataConfidence { LOW, MEDIUM, HIGH }
    enum class DataClassification { STATIC, DYNAMIC, VOLATILE }

    data class ResidentDataPoint(
        val value: String,           // "~$1,400/mo" or "Above national average"
        val detail: String,          // "1-bedroom in city center" or "Violent crime rate: 3.2 per 1,000"
        val confidence: DataConfidence,
        val classification: DataClassification,
        val sourceLabel: String,     // "Gemini AI estimate · Mar 2026"
        val verifyUrl: String? = null, // gov link for VOLATILE data
    )

    data class ResidentCategory(
        val id: String,              // "D1", "D2", etc.
        val label: String,           // "Rental Prices"
        val icon: String,            // emoji: "🏠"
        val points: List<ResidentDataPoint>,
    )

    data class ResidentData(
        val areaName: String,
        val categories: List<ResidentCategory>,
        val originContext: String?,   // "Compared to Miami, FL" or null if local
        val fetchedAt: Long,         // epoch ms for staleness check
    )
    ```

- [x] **Task 3: Create `FeatureFlags` object**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/FeatureFlags.kt` (NEW)
  - Action: Create simple flags object:
    ```kotlin
    object FeatureFlags {
        const val MOVE_HERE_ENABLED = false  // flip to true when ready to ship
    }
    ```

#### Layer 2: State Integration (depends on Layer 1)

- [x] **Task 4: Add `discoveryMode` to `AreaContext`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaContext.kt`
  - Action: Add field `val discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER` to `AreaContext` data class. This flows into prompt builders so they can detect mode.

- [x] **Task 5: Add resident state to `MapUiState.Ready`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add five fields to `MapUiState.Ready` (consolidates all Move Here state — Task 14 does NOT add more fields):
    ```kotlin
    val discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER,
    val residentData: ResidentData? = null,
    val isLoadingResidentData: Boolean = false,
    val dailyLifePois: List<POI> = emptyList(),
    val showDailyLifePins: Boolean = false,  // toggle between explore/daily-life
    ```

- [x] **Task 6: Add resident `MetaLine` variants**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/MetaLine.kt`
  - Action:
    1. Add new sealed class variant at priority 2:
       ```kotlin
       /** Priority 2 — resident mode headline data. Teal text. */
       data class ResidentHeadline(val text: String) : MetaLine(2)
       ```
    2. Add `is MetaLine.ResidentHeadline -> text` to `MetaLine.text` extension property
    3. Update `buildMetaLines()` (already has 18 params — add 2 more with defaults to avoid breaking existing callers):
       - Add parameter `discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER`
       - Add parameter `residentData: ResidentData? = null`
       - When `discoveryMode == RESIDENT && residentData != null`:
         - Add up to 3 `ResidentHeadline` lines cycling through top category headlines:
           - "🏠 Avg rent {D1.value}" (if D1 exists)
           - "📊 CoL index {D3.value}" (if D3 exists)
           - "🛡️ Safety: {D4.value}" (if D4 exists)
         - These replace currency/language context lines (those are less relevant in resident mode)
       - When `discoveryMode == TRAVELER`: existing behavior unchanged

#### Layer 3: Gemini Prompt + Data Fetch (depends on Layer 1)

- [x] **Task 7: Add `buildResidentDataPrompt()` to `GeminiPromptBuilder`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Add new method that returns a prompt requesting structured JSON for all 9 categories:
    ```kotlin
    fun buildResidentDataPrompt(
        areaName: String,
        originCountryCode: String,
        originCity: String?,
        languageTag: String = "en",
    ): String
    ```
    - Prompt instructs Gemini to return JSON matching `ResidentData` schema
    - 9 categories with IDs (D1, D2, D3, D4, D6, D9, D10, D12, D21)
    - Each category has: label, icon, 2-4 data points with value + detail
    - Origin-aware comparisons: "Compared to {originCity}: rent is X% lower/higher"
    - Confidence self-assessment per category (Gemini rates its own confidence)
    - For D12 (Visa): must include `verifyUrl` pointing to official gov immigration page
    - For D10 (Weather): instruct to use "See weather data" since we have Open-Meteo — just provide climate summary
    - Language rule: respond in locale language if non-English
    - Output: single JSON object, no other text

- [x] **Task 8: Add `residentContextBlock()` to `GeminiPromptBuilder`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Add private method for chat prompt layering:
    ```kotlin
    private fun residentContextBlock(residentData: ResidentData?): String
    ```
    - When `residentData != null`: returns block instructing AI to act as "honest relocation advisor" instead of "enthusiastic travel guide"
    - Injects top-line data points as grounding facts the AI must not contradict
    - Adds rules: "Prioritize daily-life relevance over tourist appeal", "Compare to user's origin when relevant", "Be honest about downsides — groceries cost X% above average"
    - When `residentData == null`: returns empty string (no-op)

- [x] **Task 9: Wire `residentContextBlock` into `buildChatSystemContext()`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action:
    1. Add parameter `residentData: ResidentData? = null` to `buildChatSystemContext()` (default null preserves all existing callers — no signature breakage)
    2. Add `residentContextBlock(residentData)` to the `listOf(...)` block assembly, after `contextShiftBlock()` and before `outputFormatBlock()`
    3. When in resident mode, modify `personaBlock()` call or add override: persona shifts from "passionate local who has lived here 20 years" to "honest relocation advisor who knows {areaName} inside out"
    4. Update `ChatViewModel` call site (line ~270) to pass `residentData` when available. Update `systemContextForTest` accessor similarly. Update any test call sites in `GeminiPromptBuilderTest.kt`.

- [x] **Task 10: Add `fetchResidentData()` to `AreaIntelligenceProvider` interface**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt`
  - Action: Add method to interface:
    ```kotlin
    suspend fun fetchResidentData(
        areaName: String,
        originCountryCode: String,
        originCity: String?,
        languageTag: String,
    ): ResidentData
    ```

- [x] **Task 11: Implement `fetchResidentData()` in `GeminiAreaIntelligenceProvider`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action:
    1. Implement the interface method
    2. Call `promptBuilder.buildResidentDataPrompt(...)` to get the prompt
    3. Send to Gemini API (non-streaming — single JSON response, not SSE)
    4. Parse JSON response into `ResidentData` model
    5. Apply default confidence values from the data model reference table if Gemini doesn't self-assess
    6. Set `classification` per category based on ID:
       - D9, D10, D21 → STATIC
       - D1, D2, D3, D4, D6 → DYNAMIC
       - D12 → VOLATILE
    7. Generate `sourceLabel` based on classification rules (see Data Classification Citation Rules table)
    8. Set `fetchedAt` to current epoch ms

- [x] **Task 12: Add stub to `MockAreaIntelligenceProvider`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/MockAreaIntelligenceProvider.kt`
  - Action: Add `fetchResidentData()` implementation returning hardcoded mock `ResidentData` for "Lisbon" with realistic values across all 9 categories. This supports offline development and testing.

#### Layer 4: ViewModel Logic (depends on Layers 2 + 3)

- [x] **Task 13: Add mode management to `MapViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action:
    1. Add private state: `private val residentAreas = mutableMapOf<String, DiscoveryMode>()`
    2. Add public method:
       ```kotlin
       fun toggleMoveHere() {
           if (!FeatureFlags.MOVE_HERE_ENABLED) return
           val current = _uiState.value as? MapUiState.Ready ?: return
           val newMode = if (current.discoveryMode == DiscoveryMode.RESIDENT)
               DiscoveryMode.TRAVELER else DiscoveryMode.RESIDENT
           residentAreas[current.areaName] = newMode
           _uiState.update { state ->
               (state as? MapUiState.Ready)?.copy(
                   discoveryMode = newMode,
                   residentData = if (newMode == DiscoveryMode.TRAVELER) null else state.residentData,
                   isLoadingResidentData = newMode == DiscoveryMode.RESIDENT && state.residentData == null,
               ) ?: state
           }
           if (newMode == DiscoveryMode.RESIDENT) {
               fetchResidentData(current.areaName)
           }
       }
       ```
    3. Add private method `fetchResidentData(areaName: String)`:
       - Launch coroutine in `viewModelScope`
       - Get origin country from `localeProvider.languageTag` (parse region subtag, e.g. "pt-BR" → "BR"; fall back to "US" if no region). NOTE: `LocaleProvider` has no `countryCode` property — derive from `languageTag`.
       - Call `getAreaPortrait.repository` or inject `AreaIntelligenceProvider` directly for `fetchResidentData()`
       - On success: update `_uiState` with `residentData`, `isLoadingResidentData = false`
       - On error: emit error event, reset mode to TRAVELER, clear loading
    4. In existing `cancelAreaFetch()` / area change logic:
       - On area change: check `residentAreas[newAreaName]` — if RESIDENT, restore mode + refetch data
       - If no entry, default to TRAVELER (clear `residentData`, set mode)
    5. Wire `discoveryMode` into `buildMetaLines()` calls (pass mode + residentData)

- [x] **Task 14: Add daily-life pin fetch to `MapViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action: (NOTE: `dailyLifePois` and `showDailyLifePins` fields already added to `MapUiState.Ready` in Task 5)
    1. When mode switches to RESIDENT, trigger Google Places text search for daily-life POI type keywords near area center. Use existing `GooglePlacesProvider` — call existing search infrastructure with type keywords ("grocery store", "school", "hospital", "transit station", "gym", "pharmacy") as text queries. Do NOT add new methods to `PlacesProvider` interface — reuse existing text search capability.
    2. Store results in `dailyLifePois`
    3. Add `fun togglePinLayer()` to switch between explore pins and daily-life pins
    4. Default: show daily-life pins when in RESIDENT mode, explore pins when in TRAVELER

- [x] **Task 15: Add mode-aware context to `ChatViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action:
    1. Add field: `private var currentResidentData: ResidentData? = null`
    2. Add method to receive mode updates from MapViewModel (called from MapScreen):
       ```kotlin
       fun updateDiscoveryMode(mode: DiscoveryMode, residentData: ResidentData?) {
           currentResidentData = if (mode == DiscoveryMode.RESIDENT) residentData else null
       }
       ```
    3. In existing chat message sending logic, pass `currentResidentData` to `promptBuilder.buildChatSystemContext()`
    4. When in RESIDENT mode, update orb bar ghost text to cycle through resident-themed prompts: "Ask about living here", "What's the job market like?", "Is it safe?", "Cost of living?"

#### Layer 5: UI Components (depends on Layer 4)

- [x] **Task 16: Add "Move Here" button + mode indicator to `DiscoveryHeader`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/DiscoveryHeader.kt`
  - Action:
    1. Add parameters to `DiscoveryHeader` composable:
       ```kotlin
       // Move Here mode
       discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER,
       moveHereEnabled: Boolean = false,
       onMoveHereTap: () -> Unit = {},
       ```
    2. Add parameters to `CollapsedBar`:
       ```kotlin
       discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER,
       moveHereEnabled: Boolean = false,
       onMoveHereTap: () -> Unit = {},
       ```
    3. In `CollapsedBar`, when `moveHereEnabled && FeatureFlags.MOVE_HERE_ENABLED`:
       - Show 🏠 icon button to the LEFT of the area name
       - When `discoveryMode == RESIDENT`: icon is filled/highlighted (teal tint), acts as mode indicator
       - When `discoveryMode == TRAVELER`: icon is outline/muted, tapping activates resident mode
       - `onClick = onMoveHereTap`
    4. When `discoveryMode == RESIDENT`:
       - Area name text gets subtle teal tint to signal mode
       - Mode indicator: small "Living" label below area name (or inline after name, space permitting)

- [x] **Task 17: Create `ResidentDashboardCard` composable**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ResidentDashboardCard.kt` (NEW)
  - Action: Create expandable dashboard card showing all 9 resident categories:
    1. **Collapsed state:** Shows top 3 headline numbers in a horizontal row:
       - "🏠 $1,400/mo" · "📊 CoL 72" · "🛡️ Above avg"
       - Tap to expand
    2. **Expanded state:** Vertical list of all 9 categories, each as a section:
       - Category icon + label header
       - 2-4 data points per category, each showing:
         - `value` in prominent text
         - `detail` in secondary text
         - Confidence badge: colored dot (green=HIGH, yellow=MEDIUM, red=LOW) + label
         - Source attribution in 10sp muted text: "Gemini AI estimate · Mar 2026"
       - For VOLATILE categories (D12 Visa): "Verify locally" link button with `verifyUrl`
    3. **Trust signals:**
       - Section header: "🤖 AI Insight" badge on each Gemini-sourced section
       - For D10 Weather: "📡 Open-Meteo" badge (reuse existing weather data, just show climate summary)
       - Bottom of card: "Data for informational purposes only. Verify before making decisions."
    4. **Loading state:** Shimmer placeholder matching card layout when `isLoadingResidentData == true`
    5. **Origin comparison:** If `residentData.originContext != null`, show comparison header: "Compared to {originCity}"
    6. Style: Dark card matching `MapFloatingUiDark` theme. Rounded corners. Uses `PlatformBackHandler` to dismiss when expanded.

- [x] **Task 18: Add resident section to `AiDetailPage`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`
  - Action:
    1. Add parameters:
       ```kotlin
       discoveryMode: DiscoveryMode = DiscoveryMode.TRAVELER,
       residentData: ResidentData? = null,
       ```
    2. Add parameter `dailyLifePois: List<POI> = emptyList()` to `AiDetailPage`
    3. When `discoveryMode == RESIDENT && residentData != null`:
       - Add "Living Here" section after the existing POI metadata section
       - Show 3-4 proximity data points for the current POI based on `dailyLifePois` param:
         - "5 min walk to Metro" (nearest transit_station)
         - "3 grocery stores within 500m" (grocery_store count)
         - "Nearest hospital: 1.2 km" (nearest hospital)
         - "2 schools within 1 km" (school count)
       - Calculate distances from current POI's lat/lng using `haversineDistanceMeters()` (in `com.harazone.util.GeoUtils`)
       - Section has "🤖 AI Insight" badge + "Based on Google Places data" attribution
       - `dailyLifePois` passed from `MapScreen` → `AiDetailPage` (wired in Task 20)
    3. When `discoveryMode == TRAVELER`: no change, section is hidden

- [x] **Task 19: Update ticker rendering for resident mode**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/RotatingMetaTicker.kt`
  - Action:
    1. Add handling for `MetaLine.ResidentHeadline` in the `when` block that determines text color
    2. Use teal color (same as `RemoteContext`) for resident headline lines
    3. No structural changes needed — `RotatingMetaTicker` already handles any `MetaLine` variant via the `text` extension property

#### Layer 6: Wiring (depends on Layer 5)

- [x] **Task 20: Wire everything in `MapScreen`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action:
    1. Extract `discoveryMode`, `residentData`, `isLoadingResidentData`, `dailyLifePois`, `showDailyLifePins` from `MapUiState.Ready`
    2. Pass to `DiscoveryHeader`:
       - `discoveryMode = discoveryMode`
       - `moveHereEnabled = FeatureFlags.MOVE_HERE_ENABLED`
       - `onMoveHereTap = { mapViewModel.toggleMoveHere() }`
    3. When `discoveryMode == RESIDENT`:
       - Show `ResidentDashboardCard` composable (positioned below header, above map)
       - Pass `residentData`, `isLoadingResidentData`, origin context
    4. Pass to `AiDetailPage`:
       - `discoveryMode = discoveryMode`
       - `residentData = residentData`
    5. Sync mode to ChatViewModel:
       - `LaunchedEffect(discoveryMode, residentData) { chatViewModel.updateDiscoveryMode(discoveryMode, residentData) }`
    6. Pass pin layer toggle:
       - When `showDailyLifePins`: pass `dailyLifePois` to MapComposable as the active pin set
       - When `!showDailyLifePins`: pass existing `pois` (traveler pins)
       - Add pin layer toggle button (small chip above map: "Explore" / "Daily Life")

- [x] **Task 21: Update `AreaContextFactory` to include `discoveryMode`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/service/AreaContextFactory.kt`
  - Action: Pass `discoveryMode` through to `AreaContext` construction. This ensures prompts that use `AreaContext` are mode-aware.

#### Layer 7: Tests (depends on all above)

- [x] **Task 22: Unit tests for domain models**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/domain/model/ResidentDataTest.kt` (NEW)
  - Action: Test `ResidentData`, `ResidentCategory`, `ResidentDataPoint` construction + defaults. Test `DataClassification` citation rules.

- [x] **Task 23: Test `buildMetaLines()` with resident mode**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/domain/model/DomainModelTest.kt` (existing)
  - Action: Add tests:
    - `buildMetaLines` with `discoveryMode = RESIDENT` and `residentData` produces `ResidentHeadline` lines
    - `buildMetaLines` with `discoveryMode = TRAVELER` produces no `ResidentHeadline` lines (regression)
    - `ResidentHeadline` lines have priority 2

- [x] **Task 24: Test `buildResidentDataPrompt()` and `residentContextBlock()`**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiPromptBuilderTest.kt` (existing)
  - Action: Add tests:
    - `buildResidentDataPrompt` includes all 9 category IDs
    - `buildResidentDataPrompt` includes origin city comparison when provided
    - `buildResidentDataPrompt` applies language rule for non-English
    - `buildChatSystemContext` with `residentData != null` includes "relocation advisor" persona
    - `buildChatSystemContext` with `residentData == null` omits resident block (regression)

- [x] **Task 25: Test `MapViewModel` mode toggle**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt` (existing)
  - Action: Add tests:
    - `toggleMoveHere` flips mode from TRAVELER to RESIDENT and back
    - Mode is area-scoped: switching areas preserves resident areas map
    - Feature flag disabled: `toggleMoveHere` is no-op
    - Resident data fetch error resets mode to TRAVELER
    - Area change with no resident entry defaults to TRAVELER

- [x] **Task 26: Add `FakeAreaIntelligenceProvider.fetchResidentData()`**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaIntelligenceProvider.kt` (existing)
  - Action: Add `fetchResidentData()` implementation returning configurable mock data. Support error simulation via flag.

---

### Acceptance Criteria

#### Core State

- [ ] **AC1:** Given the feature flag is enabled, when a user taps the 🏠 button on the collapsed header bar, then `discoveryMode` changes from TRAVELER to RESIDENT and the button becomes highlighted/filled.
- [ ] **AC2:** Given `discoveryMode == RESIDENT`, when the user taps the 🏠 button again, then mode reverts to TRAVELER, `residentData` is cleared, and all UI surfaces revert to traveler behavior.
- [ ] **AC3:** Given `discoveryMode == RESIDENT` for "Lisbon", when the user pans to "Porto", then Porto defaults to TRAVELER mode. When user returns to Lisbon, RESIDENT mode is restored.
- [ ] **AC4:** Given the feature flag is disabled (`FeatureFlags.MOVE_HERE_ENABLED = false`), when the app renders, then no 🏠 button is visible and `toggleMoveHere()` is a no-op.

#### Data Fetch

- [ ] **AC5:** Given `discoveryMode` changes to RESIDENT, when resident data fetch starts, then `isLoadingResidentData == true` and a shimmer placeholder is shown in the dashboard card area.
- [ ] **AC6:** Given resident data fetch completes, then `residentData` contains all 9 categories with at least 2 data points each, confidence levels, and source attribution labels.
- [ ] **AC7:** Given resident data fetch fails (network error, Gemini error), then mode resets to TRAVELER, an error toast is shown, and no partial data is displayed.
- [ ] **AC8:** Given the user's locale is `pt-BR`, when resident data is fetched, then data labels and values are returned in Portuguese.

#### Ticker

- [ ] **AC9:** Given `discoveryMode == RESIDENT` and `residentData` is loaded, then the ticker rotates through resident headlines ("🏠 Avg rent $1,400", "📊 CoL index 72", "🛡️ Safety: Above avg") instead of currency/language context lines.
- [ ] **AC10:** Given `discoveryMode == TRAVELER`, then the ticker displays existing lines (weather, currency, language, etc.) with no resident headlines (regression check).

#### Dashboard Card

- [ ] **AC11:** Given `discoveryMode == RESIDENT` and `residentData` is loaded, then the `ResidentDashboardCard` is visible with collapsed state showing 3 headline numbers.
- [ ] **AC12:** Given the dashboard card is shown, when the user taps it, then it expands to show all 9 categories with data points, confidence badges, and source attributions.
- [ ] **AC13:** Given a VOLATILE category (D12 Visa/Immigration), then each data point shows a "Verify locally" link that opens the government URL.
- [ ] **AC14:** Given `discoveryMode == TRAVELER`, then no `ResidentDashboardCard` is visible.

#### Pin Shift

- [ ] **AC15:** Given `discoveryMode == RESIDENT`, when daily-life POIs are loaded, then map shows grocery, school, hospital, transit, gym, pharmacy pins.
- [ ] **AC16:** Given `discoveryMode == RESIDENT`, when the user taps the "Explore" / "Daily Life" toggle, then map switches between traveler POI pins and daily-life pins.

#### Detail Page

- [ ] **AC17:** Given `discoveryMode == RESIDENT` and a POI is selected, then the detail page shows a "Living Here" section with proximity data (nearest transit, grocery count, hospital distance, school count).
- [ ] **AC18:** Given `discoveryMode == TRAVELER` and a POI is selected, then no "Living Here" section is shown (regression check).

#### Chat

- [ ] **AC19:** Given `discoveryMode == RESIDENT`, when the user opens the companion chat, then the AI persona is "honest relocation advisor" and responses reference resident data points as grounding facts.
- [ ] **AC20:** Given `discoveryMode == RESIDENT`, then the orb bar ghost text cycles through: "Ask about living here", "What's the job market like?", "Is it safe?", "Cost of living?"

#### Trust Signals

- [ ] **AC21:** Given any resident data point is displayed, then it shows source attribution ("Gemini AI estimate · Mar 2026"), a confidence dot (green/yellow/red), and "🤖 AI Insight" badge on the section header.
- [ ] **AC22:** Given a VOLATILE data point (visa/immigration), then it shows "Verify before acting" warning + government URL link.
- [ ] **AC23:** Given `residentData.originContext` is set (e.g., "Compared to Miami, FL"), then origin comparison context is shown in the dashboard card header.

#### Android Back Button

- [ ] **AC24:** Given the `ResidentDashboardCard` is expanded, when the Android back button is pressed, then the card collapses (not exit app). Uses `PlatformBackHandler`.

---

## Additional Context

### Dependencies

- **No new external dependencies.** All data sources (Gemini API, Google Places API, Open-Meteo) are already integrated.
- **Internal dependency:** `AreaIntelligenceProvider` interface must be extended (Task 10) before implementing in `GeminiAreaIntelligenceProvider` (Task 11).
- **Existing infrastructure reused:**
  - `GooglePlacesProvider` for daily-life POI search
  - `LocaleProvider.countryCode` for origin detection
  - `haversineDistanceMeters()` for proximity calculations
  - `MapFloatingUiDark` theme for card styling
  - `PlatformBackHandler` for back button handling
  - Existing `Confidence` model pattern (if compatible, reuse; otherwise new `DataConfidence` enum)

### Testing Strategy

**Unit Tests (Layer 7 tasks):**
- Domain model construction + defaults
- `buildMetaLines()` with RESIDENT vs TRAVELER mode
- `buildResidentDataPrompt()` output validation
- `buildChatSystemContext()` with/without resident data
- `MapViewModel.toggleMoveHere()` state transitions
- Feature flag guard behavior

**Manual Testing:**
- Activate "Move Here" on 3+ international cities (Lisbon, Tokyo, Buenos Aires) — verify all 9 categories populate
- Activate on US domestic city — verify D12 Visa section shows "N/A" or domestic context, not international
- Toggle mode on/off rapidly — verify no state corruption
- Switch areas while RESIDENT mode is active — verify area-scoped isolation
- Open chat in RESIDENT mode — verify persona shift is perceptible
- Verify all trust signals render: attribution, confidence dots, AI badges, verify links
- Test with `pt-BR` locale — verify Portuguese responses
- Android back button: expanded dashboard card dismisses correctly
- iOS: verify no crashes, no back button issues (swipe-back only)

**Regression:**
- Run full existing test suite after implementation — zero failures expected due to feature flag isolation
- Specifically verify: traveler flow unchanged when flag is on but mode is TRAVELER
- Specifically verify: traveler flow unchanged when flag is off

### Notes

**High-Risk Items:**
1. **Gemini accuracy for quantitative data** (rent prices, CoL index) — confidence meter mitigates this, but shadow benchmark against Numbeo website for 20 cities should be done manually before/after launch. See brainstorm spreadsheet at `_bmad-output/brainstorming/gemini-vs-numbeo-benchmark.csv`.
2. **Visa info staleness** — VOLATILE classification + "verify locally" links + LOW default confidence are the mitigation. The app explicitly does NOT position itself as authoritative for visa data.
3. **Google Places coverage for daily-life POIs internationally** — schools, hospitals may have poor coverage outside US/EU. Graceful degradation: show available pins, no error if count is low.

**Phase 1b Upgrade Path:**
- `ResidentDataPoint.sourceLabel` will change from "Gemini AI estimate" to "Numbeo verified data" when Numbeo API is integrated
- `DataConfidence` defaults will shift upward for Numbeo-backed categories
- `ResidentData` model is designed to be source-agnostic — same structure regardless of data provider
- Budget simulator will add a new `ResidentDashboardCard` section, not a separate component

**Story Sizing Recommendation:**
This spec is large. Recommend splitting into 3 stories:
- **Story A:** Domain models + state + feature flag + ViewModel mode toggle (Tasks 1-6, 13, 21-23, 25-26) — foundation
- **Story B:** Gemini prompts + data fetch + trust signals (Tasks 7-12, 24) — data layer
- **Story C:** All 6 UI surfaces + wiring (Tasks 14-20) — presentation layer

Each story is independently testable. Story A can ship behind feature flag with mode toggle only (no visible data yet). Story B adds data. Story C lights up the UI.
