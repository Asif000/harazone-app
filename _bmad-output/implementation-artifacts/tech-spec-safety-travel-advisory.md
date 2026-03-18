---
title: 'Safety & Travel Advisory Warnings'
slug: 'safety-travel-advisory'
created: '2026-03-17'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
review_pass: 'v6 — 29 findings over 5 passes, all resolved'
tech_stack: [KMP, Compose Multiplatform, Ktor, Koin, MapLibre, kotlinx.serialization]
files_to_modify:
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaAdvisory.kt
  - composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AdvisoryProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/FcdoAdvisoryProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/FcdoCountrySlugs.kt
  - composeApp/src/commonMain/kotlin/com/harazone/domain/model/CompanionNudge.kt
  - composeApp/src/commonMain/kotlin/com/harazone/domain/companion/CompanionNudgeEngine.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/TopContextBar.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/SafetyBanner.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/SafetyGateModal.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt
  - composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt
  - composeApp/src/commonMain/composeResources/values/strings.xml
code_patterns:
  - 'Provider interface in domain/ + concrete impl in data/remote/'
  - 'Result<T> return type for all provider calls'
  - 'Koin single() registration in DataModule'
  - 'StateFlow<MapUiState> for reactive UI'
  - 'CompanionNudge priority queue with ordinal-based ordering'
  - 'PlatformBackHandler for every dismissible overlay'
  - 'HttpClientFactory.create() shared Ktor client'
test_patterns:
  - 'JUnit + MockK for unit tests'
  - 'Turbine for Flow testing'
  - 'Mock providers for isolation'
---

# Tech-Spec: Safety & Travel Advisory Warnings

**Created:** 2026-03-17

## Overview

### Problem Statement

No real-time danger indicators exist in the app. Users can teleport to conflict zones (e.g., Dubai near Yemen) with zero safety awareness. Locally, users exploring unfamiliar neighborhoods have no safety context. This is a beta blocker — the app cannot ship without safety indicators.

### Solution

Two-tier safety system:

1. **FCDO international advisories** (authoritative) — government travel advisory data from UK FCDO with sub-national zones. Powers the safety dot in area header, dismissible banner for Level 2+, and safety gate modal for Level 4 (Do Not Travel). Data persisted to Settings/preferences for cold-launch resilience.

2. **Gemini local safety context** (AI-generated, labeled) — enhanced SAFETY bucket prompt for neighborhood-level safety context. Clearly labeled as "AI-generated safety context" with disclaimer. Different UI treatment from authoritative FCDO warnings. Works globally (US, Brazil, anywhere).

FCDO = hard warnings ("DO NOT TRAVEL"). Gemini local = soft context ("higher reported crime — stay aware"). Different weight, different trust level, clearly labeled.

### Scope

**In Scope:**
- FCDO data provider (international advisory API) with ISO→slug mapping
- Sub-national advisory zones (country-level default, region name text-matching via geocoder)
- Safety dot in TopContextBar (no dot when safe, yellow/orange/red when advisory active)
- Dismissible banner on area load for Level 2+
- Safety gate modal for Level 4 zones (Do Not Travel)
- Companion orb safety nudge for advisory zones (localized via strings.xml)
- 24h advisory cache persisted to preferences (survives cold launch)
- Dismissible but persistent indicator (banner dismisses, dot stays)
- Enhanced Gemini SAFETY bucket prompt for local neighborhood safety context
- "AI-generated" disclaimer label on Gemini-sourced safety info
- Strings/localization for safety UI
- "Data unavailable" state when FCDO is unreachable (grey dot, not green)

**Out of Scope:**
- US State Dept integration (post-beta, second source)
- Push notifications for advisory changes
- Emergency contacts/hospitals/embassy lookup (FR23-24, separate feature)
- GDACS disaster alerts (post-beta)
- Real-time crime APIs (CrimeMapping, SpotCrime — post-beta local enhancement)
- Background location monitoring for safety alerts (FR36, Phase 3)

## Context for Development

### Codebase Patterns

- **Provider pattern**: Interface in `domain/provider/`, concrete impl in `data/remote/`. Returns `Result<T>`. See `OpenMeteoWeatherProvider` for minimal example — constructor-injected `HttpClient`, single suspend function, try-catch wrapping.
- **State management**: `MapViewModel` uses `MutableStateFlow<MapUiState>` with sealed class. `MapUiState.Ready` has 40+ fields.
- **Companion nudge system**: `NudgeType` enum with priority ordering (lower ordinal = higher priority). `CompanionNudgeEngine` has check methods returning `CompanionNudge?`. `MapViewModel.enqueueNudge()` manages a priority queue.
- **DI**: Koin `single()` registrations in `DataModule.kt`.
- **HTTP client**: `HttpClientFactory.create()` shared instance with SSE, ContentNegotiation (JSON), timeouts.
- **Error handling**: `isRetryableError()` — 5xx retryable, 4xx not, network errors retryable. Exponential backoff.
- **Platform back handler**: `PlatformBackHandler(enabled = condition) { dismissAction }` required for every dismissible overlay.
- **Enum comparison**: Kotlin enums are `Comparable` by ordinal. Use `advisory.level.ordinal >= AdvisoryLevel.CAUTION.ordinal` or define `fun isAtLeast(level: AdvisoryLevel): Boolean` extension.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/provider/AreaIntelligenceProvider.kt` | Provider interface pattern |
| `data/remote/OpenMeteoWeatherProvider.kt` | Minimal provider impl pattern |
| `data/remote/HttpClientFactory.kt` | Shared Ktor HttpClient |
| `data/remote/GeminiAreaIntelligenceProvider.kt` | Error handling, retry, `isRetryableError()` |
| `data/remote/GeminiPromptBuilder.kt` | SAFETY bucket prompt to enhance |
| `domain/model/WeatherState.kt` | Data class pattern for AreaAdvisory |
| `domain/model/CompanionNudge.kt` | NudgeType enum — add SAFETY_ALERT |
| `domain/companion/CompanionNudgeEngine.kt` | Nudge check methods |
| `ui/map/MapViewModel.kt` | State management, nudge queue, area data fetching |
| `ui/map/MapUiState.kt` | UI state sealed class |
| `ui/map/MapScreen.kt` | UI composition |
| `ui/map/components/TopContextBar.kt` | Area header — add safety dot |
| `ui/map/components/CompanionCard.kt` | Nudge card UI — reuse for safety |
| `ui/components/PlatformBackHandler.kt` | Back button for dismissibles |
| `di/DataModule.kt` | Koin DI registrations |

### Technical Decisions

- **FCDO as single authoritative source** — most international coverage, structured data, covers sub-national zones.
- **Gemini for local safety** — existing SAFETY bucket enhanced. Labeled as AI-generated.
- **Keep both layers** — FCDO = hard facts, Gemini = local color/tips.
- **24h cache persisted to preferences** — survives app restart. No safety regression on cold launch. [FIX H6]
- **Safety warnings ON by default** — cannot fully disable indicators.
- **No AI hallucination for authoritative warnings** — FCDO data rendered as-is.
- **SAFETY_ALERT nudge type** — high priority (low ordinal), surfaces before ambient nudges.
- **Fail to UNKNOWN, not SAFE** — when FCDO is unreachable, show grey dot ("data unavailable"), not green ("safe"). Never actively mislead. [FIX H3]
- **"Go Back" = dismiss gate + revert camera** — if no previous area exists (cold launch into danger zone), dismiss gate and show banner instead. [FIX H7]
- **No dot when SAFE** — absence of dot = no advisory. Green dot would falsely imply "actively certified safe." [FIX L3]
- **Display `lastUpdated` (FCDO's timestamp), not `cachedAt`** — label as "FCDO last updated: [date]" for honesty about data age. [FIX M4]

## Implementation Plan

### Tasks

#### Phase 1: Domain Models + Provider Interface

- [x] Task 1: Create `AreaAdvisory` model
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/AreaAdvisory.kt` (NEW)
  - Action: Create data classes:
    ```kotlin
    @Serializable
    enum class AdvisoryLevel {
        @SerialName("safe") SAFE,
        @SerialName("caution") CAUTION,
        @SerialName("reconsider") RECONSIDER,
        @SerialName("do_not_travel") DO_NOT_TRAVEL,
        @SerialName("unknown") UNKNOWN;

        fun isAtLeast(level: AdvisoryLevel): Boolean = this.ordinal >= level.ordinal && this != UNKNOWN
    }
    // NOTE: Color mapping lives in UI layer, NOT here. See Task 10 for dotColor() in TopContextBar. [FIX N-M2]

    @Serializable
    data class AreaAdvisory(
        val level: AdvisoryLevel,
        val countryName: String,
        val countryCode: String,
        val summary: String,
        val details: List<String>,
        val subNationalZones: List<SubNationalAdvisory>,
        val sourceUrl: String,
        val lastUpdated: Long,      // FCDO's published timestamp — display this [FIX M4]
        val cachedAt: Long,         // Our fetch time — for cache expiry only
    )

    @Serializable
    data class SubNationalAdvisory(
        val regionName: String,
        val level: AdvisoryLevel,
        val summary: String,
    )
    ```
  - Notes: `UNKNOWN` is the new state for "data unavailable" — shows grey dot, no banner, no gate. `isAtLeast()` solves the enum comparison issue [FIX H1]. Color mapping lives in UI layer (TopContextBar), not on this enum [FIX P3-L2].

- [x] Task 2: Create `AdvisoryProvider` interface
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AdvisoryProvider.kt` (NEW)
  - Action: Create interface:
    ```kotlin
    interface AdvisoryProvider {
        suspend fun getAdvisory(countryCode: String, regionName: String? = null): Result<AreaAdvisory>
    }
    ```
  - Notes: Single method. `countryCode` = ISO alpha-2 (from MapTiler geocoding already available in the area fetch flow). `regionName` = optional, for sub-national matching. ViewModel passes both — no redundant geocoding call. [FIX L1]

#### Phase 2: FCDO Data Provider

- [x] Task 3: Create ISO→FCDO slug mapping
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/FcdoCountrySlugs.kt` (NEW)
  - Action: Create a `Map<String, String>` mapping ISO 3166-1 alpha-2 codes to FCDO URL slugs. Source the mapping by fetching `gov.uk/api/content/foreign-travel-advice` (index endpoint) which lists all countries with their slugs. Embed the top ~80 most-traveled countries statically, with a runtime fallback that derives the slug from the country name (lowercase, hyphenated). Example:
    ```kotlin
    object FcdoCountrySlugs {
        private val STATIC_MAP = mapOf(
            "AE" to "united-arab-emirates",
            "BR" to "brazil",
            "JP" to "japan",
            "YE" to "yemen",
            "PK" to "pakistan",
            "MX" to "mexico",
            // ... ~80 entries
        )
        fun getSlug(countryCode: String, countryName: String): String {
            return STATIC_MAP[countryCode.uppercase()]
                ?: countryName.lowercase().replace(" ", "-").replace("'", "")
        }
    }
    ```
  - Notes: This is the missing task identified in review [FIX H5]. The fallback derivation handles unmapped countries reasonably (works for most single-word countries). Edge cases (Côte d'Ivoire, etc.) go in the static map.

- [x] Task 4: Implement `FcdoAdvisoryProvider`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/FcdoAdvisoryProvider.kt` (NEW)
  - Action: Implement `AdvisoryProvider` interface:
    - Use `FcdoCountrySlugs.getSlug()` to resolve country code → FCDO slug [FIX H5]
    - Fetch `gov.uk/api/content/foreign-travel-advice/{slug}` via Ktor
    - Parse JSON response. FCDO response structure:
      ```json
      {
        "title": "Yemen travel advice",
        "details": { "summary": "<p>FCDO advises against all travel to Yemen...</p>" },
        "parts": [
          { "slug": "safety-and-security", "body": "<p>...</p>" },
          { "slug": "entry-requirements", "body": "<p>...</p>" }
        ],
        "public_updated_at": "2025-12-15T10:30:00Z"
      }
      ```
    - Map `details.summary` → `AreaAdvisory.summary` (strip HTML tags)
    - Map `parts[slug=safety-and-security].body` → parse for sub-national zone names and risk mentions
    - Map `public_updated_at` → `lastUpdated` timestamp
    - Determine `AdvisoryLevel` from summary text keywords: "advises against all travel" → DO_NOT_TRAVEL, "advises against all but essential travel" → RECONSIDER, "advises caution" / "exercise increased caution" → CAUTION, no advisory keywords → SAFE
    - **Populate `details` list**: Parse `parts[slug=safety-and-security].body` HTML. Split by `<h3>` or `<h2>` sub-sections. Strip HTML tags. Each sub-section becomes one entry in `details: List<String>`. These are the bullet points rendered in the gate modal. If no safety-and-security part exists, set `details = listOf(summary)` as fallback so the gate modal body is never empty. [FIX P3-M2]
    - **Sub-national matching** [FIX H4]: Parse "Safety and security" body for region-specific warnings. If `regionName` parameter is provided, text-search for it in the body. If found with "advises against" language, elevate that region's level. If not found, use country-level. This is best-effort text matching — not geo-polygon matching. Sufficient for beta.
    - **Cache**: Persist to `Settings` (multiplatform-settings) keyed by country code. Structure: `advisory_{countryCode}` → JSON string of `AreaAdvisory`. Check `cachedAt` < 24h before HTTP call. [FIX H6]
    - **Error fallback**: Return `AdvisoryLevel.UNKNOWN` on failure — NOT `SAFE`. [FIX H3]
    - **Never cache UNKNOWN**: Only write to Settings cache on successful fetch. UNKNOWN is transient — next area load retries the network call. A network hiccup should not leave a persistent grey dot for 24h. [FIX P5-L1]
    - Error handling following `isRetryableError()` pattern

- [x] Task 5: Register provider + Settings in Koin
  - File: `composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt`
  - Action:
    - Check if `Settings` (from `com.russhwolf.settings`) is already registered in Koin. If not, add platform-specific factory:
      - **commonMain**: `single { Settings() }` if using `Settings()` no-arg constructor (uses NSUserDefaults on iOS, SharedPreferences on Android)
      - **If platform-specific setup needed**: add `expect fun createSettings(): Settings` in commonMain, `actual` in androidMain (`Settings(context.getSharedPreferences("advisory_cache", MODE_PRIVATE))`) and iosMain (`Settings(NSUserDefaults.standardUserDefaults)`)
      - Verify the project already uses multiplatform-settings — if not, add dependency to `libs.versions.toml` and `build.gradle.kts`
    - Register provider: `single<AdvisoryProvider> { FcdoAdvisoryProvider(get(), get()) }` — inject HttpClient and Settings
  - Notes: This is the N-H1 blocker fix. Settings registration is platform-specific in KMP — do NOT assume `Settings()` works without checking the existing DI setup.

#### Phase 3: Companion Nudge Integration

- [x] Task 6: Add `SAFETY_ALERT` nudge type
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/CompanionNudge.kt`
  - Action: Add `SAFETY_ALERT` to `NudgeType` enum as the FIRST entry (ordinal 0) so it has highest priority in the nudge queue.

- [x] Task 7: Add safety check to `CompanionNudgeEngine`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/companion/CompanionNudgeEngine.kt`
  - Action: Add method that uses string resources (not hardcoded text) [FIX M2]:
    ```kotlin
    fun buildSafetyNudge(advisory: AreaAdvisory?, nudgeText: String): CompanionNudge? {
        if (advisory == null || advisory.level == AdvisoryLevel.SAFE || advisory.level == AdvisoryLevel.UNKNOWN) return null
        return CompanionNudge(
            type = NudgeType.SAFETY_ALERT,
            text = nudgeText,
            chatContext = "Safety advisory for ${advisory.countryName}: ${advisory.summary}",
        )
    }
    ```
  - Notes: Caller passes pre-resolved localized `nudgeText` — no empty placeholder, no getString in engine. [FIX N-L2, N-M1]

#### Phase 4: ViewModel + State Integration

- [x] Task 8: Add advisory fields to `MapUiState`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: Add to `MapUiState.Ready`:
    ```kotlin
    val advisory: AreaAdvisory? = null,
    val isAdvisoryBannerDismissed: Boolean = false,
    val hasAcknowledgedGate: Boolean = false,
    val hasPendingSafetyNudge: Boolean = false,  // Resolved to text in Composable scope [FIX N-M1]
    val previousAreaName: String? = null,  // For "Go Back to Safety" [FIX H7]
    ```
  - Notes: `hasAcknowledgedGate` tracks whether Level 4 gate has been acknowledged this session [FIX H2]. `previousAreaName` enables "Go Back" navigation.

- [x] Task 9: Fetch advisory in `MapViewModel`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action:
    - Add `advisoryProvider: AdvisoryProvider` to constructor (Koin injection)
    - Before area change, save current area name: `previousAreaName = currentState.areaName`
    - In the area data fetch flow, pass already-known country code (from MapTiler geocoding result, already available):
      ```kotlin
      launch {
          val regionName = geocodeResult.region // if available from MapTiler
          advisoryProvider.getAdvisory(geocodeResult.countryCode, regionName)
              .onSuccess { advisory ->
                  updateState { copy(
                      advisory = advisory,
                      isAdvisoryBannerDismissed = false,
                      hasAcknowledgedGate = false,
                      previousAreaName = oldAreaName,
                  ) }
                  // Nudge text is resolved in MapScreen (Composable scope) via pendingSafetyNudge flag
                  if (advisory.level.isAtLeast(AdvisoryLevel.CAUTION)) {
                      updateState { copy(hasPendingSafetyNudge = true) }
                  }
              }
              .onFailure { /* Log error — advisory shows as UNKNOWN (grey dot) */ }
      }
      ```
    - **String resolution pattern** [FIX N-M1]: ViewModel sets `hasPendingSafetyNudge = true`. MapScreen observes this flag and, in Composable scope, resolves the string via `stringResource()` then calls `viewModel.enqueueSafetyNudge(resolvedText)`:
      ```kotlin
      // In MapScreen composable:
      if (state.hasPendingSafetyNudge && state.advisory != null) {
          val nudgeText = when (state.advisory.level) {
              AdvisoryLevel.CAUTION -> stringResource(Res.string.advisory_nudge_caution, state.advisory.countryName)
              AdvisoryLevel.RECONSIDER -> stringResource(Res.string.advisory_nudge_reconsider, state.advisory.countryName)
              AdvisoryLevel.DO_NOT_TRAVEL -> stringResource(Res.string.advisory_nudge_danger, state.advisory.countryName)
              else -> null
          }
          if (nudgeText != null) {
              LaunchedEffect(state.hasPendingSafetyNudge) { viewModel.enqueueSafetyNudge(nudgeText) }
          }
      }
      ```
    - Add `enqueueSafetyNudge(text: String)` to ViewModel:
      ```kotlin
      fun enqueueSafetyNudge(text: String) {
          val advisory = (uiState.value as? MapUiState.Ready)?.advisory ?: return
          companionEngine.buildSafetyNudge(advisory, text)?.let { enqueueNudge(it) }
          updateState { copy(hasPendingSafetyNudge = false) }
      }
      ```
    - Add methods:
      ```kotlin
      fun dismissAdvisoryBanner() {
          updateState { copy(isAdvisoryBannerDismissed = true) }
      }
      fun acknowledgeGate() {
          updateState { copy(hasAcknowledgedGate = true) }
      }
      fun goBackToSafety() {
          val prev = (uiState.value as? MapUiState.Ready)?.previousAreaName
          if (prev != null) {
              // Navigate back to previous area (trigger area search for prev)
              searchArea(prev)
          } else {
              // No previous area (cold launch) — just dismiss the gate, show banner [FIX H7]
              acknowledgeGate()
          }
      }
      ```

#### Phase 5: UI Components

- [x] Task 10: Add safety dot to `TopContextBar`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/TopContextBar.kt`
  - Action: Add `advisoryLevel: AdvisoryLevel?` parameter. After the time text, render a small colored circle (8dp). Define color mapping in the composable (UI layer, not domain) [FIX N-M2]:
    ```kotlin
    private fun AdvisoryLevel.dotColor(): Color? = when (this) {
        AdvisoryLevel.SAFE -> null            // No dot
        AdvisoryLevel.CAUTION -> Color(0xFFD29922)
        AdvisoryLevel.RECONSIDER -> Color(0xFFDB6D28)
        AdvisoryLevel.DO_NOT_TRAVEL -> Color(0xFFF85149)
        AdvisoryLevel.UNKNOWN -> Color(0xFF888888)
    }
    ```
    - `null` or `SAFE` → no dot (absence = no advisory) [FIX L3]
    - `CAUTION` → yellow dot
    - `RECONSIDER` → orange dot
    - `DO_NOT_TRAVEL` → red dot with independent pulse animation [FIX L4]:
      ```kotlin
      val pulseAlpha by infiniteTransition.animateFloat(
          initialValue = 1f, targetValue = 0.4f,
          animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
      )
      ```
    - `UNKNOWN` → grey dot (data unavailable)
  - Notes: Dot is not tappable in beta.

- [x] Task 11: Create `SafetyBanner` composable
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/SafetyBanner.kt` (NEW)
  - Action: Create dismissible banner below `TopContextBar` for `CAUTION` and above:
    - Background color matches level (yellow/orange/red with transparency)
    - ⚠ icon + advisory title from strings.xml (e.g., `advisory_banner_title_3`)
    - 1-line summary text from `AreaAdvisory.summary`
    - "Learn More" button → opens `sourceUrl` in system browser via `UriHandler` [FIX M3]
    - ✕ dismiss button → calls `onDismiss` (banner hides, dot persists)
    - `PlatformBackHandler(enabled = isVisible) { onDismiss() }`
    - Slide-in animation from top (`AnimatedVisibility` with `slideInVertically`)
  - Notes: Banner dismisses per-session. Reappears on area change.

- [x] Task 12: Create `SafetyGateModal` composable
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/SafetyGateModal.kt` (NEW)
  - Action: Full-screen blocking modal for `DO_NOT_TRAVEL`:
    - Scrim background (dark overlay)
    - Centered card with:
      - ⚠ large warning icon
      - "Travel Advisory — Level 4: Do Not Travel" header (from strings.xml)
      - Red badge
      - Advisory details (bullet points from `AreaAdvisory.details`)
      - Source attribution: "Source: UK FCDO" + "FCDO last updated: [date]" using `lastUpdated` timestamp [FIX M4]
      - Two buttons: "← Go Back to Safety" (primary, green) → `onGoBack()` and "I Understand the Risks — Continue" (secondary, red outline) → `onContinue()`
    - `PlatformBackHandler(enabled = true) { onGoBack() }`
    - **"Go Back" behavior** [FIX H7]:
      - If `previousAreaName` exists → navigate to that area
      - If no previous area (cold launch) → dismiss gate, show banner instead. User stays in area but sees persistent warning.
    - "Continue" → calls `onContinue()` (sets `hasAcknowledgedGate = true`)
  - Notes: Gate only shows ONCE per area visit.

- [x] Task 13: Integrate safety UI into `MapScreen`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action:
    - Pass `advisory?.level` to `TopContextBar` for safety dot
    - Show `SafetyBanner` below TopContextBar when `advisory != null && advisory.level.isAtLeast(AdvisoryLevel.CAUTION) && !isAdvisoryBannerDismissed` [FIX H1 — uses isAtLeast()]
    - Show `SafetyGateModal` when `advisory?.level == DO_NOT_TRAVEL && !hasAcknowledgedGate` [FIX H2 — hasAcknowledgedGate now defined]
    - Wire callbacks: `onDismissBanner = viewModel::dismissAdvisoryBanner`, `onGoBack = viewModel::goBackToSafety`, `onContinue = viewModel::acknowledgeGate`

#### Phase 6: Enhanced Gemini Local Safety

- [x] Task 14: Enhance SAFETY bucket prompt
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Update the SAFETY bucket prompt from:
    ```
    1. SAFETY - Current safety conditions, alerts, crime levels
    ```
    to:
    ```
    1. SAFETY - Neighborhood-level safety context: crime levels, areas to avoid, time-of-day considerations, tourist scam warnings, local emergency numbers. Be specific to this exact area, not country-level generalizations. Include practical tips.
    ```

#### Phase 7: Strings + Localization

- [x] Task 15: Add safety strings
  - File: `composeApp/src/commonMain/composeResources/values/strings.xml`
  - Action: Add strings:
    ```xml
    <!-- Safety Advisory -->
    <string name="advisory_safe">Safe</string>
    <string name="advisory_caution">Exercise Caution</string>
    <string name="advisory_reconsider">Reconsider Travel</string>
    <string name="advisory_do_not_travel">Do Not Travel</string>
    <string name="advisory_unknown">Safety data unavailable</string>
    <string name="advisory_banner_title_2">Travel Advisory — Exercise Caution</string>
    <string name="advisory_banner_title_3">Travel Advisory — Reconsider Travel</string>
    <string name="advisory_banner_title_4">Travel Advisory — Do Not Travel</string>
    <string name="advisory_learn_more">Learn More</string>
    <string name="advisory_go_back">Go Back to Safety</string>
    <string name="advisory_understand_risks">I Understand the Risks — Continue</string>
    <string name="advisory_last_updated">FCDO last updated %1$s</string>
    <string name="advisory_source">Source: UK FCDO</string>
    <string name="advisory_ai_disclaimer">AI-generated safety context</string>
    <!-- Safety nudge strings [FIX M2] -->
    <string name="advisory_nudge_caution">⚠ Heads up — exercise caution in %1$s. Tap for details.</string>
    <string name="advisory_nudge_reconsider">⚠ %1$s has an active travel advisory. Please review before exploring.</string>
    <string name="advisory_nudge_danger">🚨 %1$s — official advice is DO NOT TRAVEL. Tap for details.</string>
    ```

### Acceptance Criteria

- [x] AC1: Given the user opens a map in a country with FCDO Level 2 advisory (e.g., Mexico), when the area loads, then a yellow banner appears below the TopContextBar with "Travel Advisory — Exercise Caution" and the safety dot in the header turns yellow.
- [x] AC2: Given a safety banner is visible, when the user taps ✕ dismiss, then the banner slides away but the safety dot remains visible in the header bar.
- [x] AC3: Given the user teleports to a Level 4 zone (e.g., Yemen), when the area loads, then a full-screen SafetyGateModal appears with "Do Not Travel" warning, details, and two buttons: "Go Back to Safety" and "I Understand the Risks."
- [x] AC4: Given the SafetyGateModal is showing, when the user presses Android back button, then the modal dismisses and navigates back to the previous area (same as "Go Back to Safety"). If no previous area exists, the gate dismisses and the banner + dot persist.
- [x] AC5: Given the user taps "I Understand the Risks" on the gate modal, when the modal dismisses, then the red banner + red dot remain visible and the user can explore the area. The gate does not reappear for this area visit.
- [x] AC6: Given a Level 3 advisory is active, when the companion orb is tapped, then a safety nudge appears with localized text from strings.xml: "⚠ [Country] has an active travel advisory."
- [x] AC7: Given an advisory was fetched more than 24 hours ago, when the area loads again, then the provider fetches fresh data from FCDO (cache expired).
- [x] AC8: Given the FCDO API is unreachable (timeout/error), when an advisory fetch fails, then the safety dot shows GREY (not green), indicating data unavailable. No crash, no empty screen, no false "safe" signal.
- [x] AC9: Given the user is in a safe country (e.g., Japan), when the area loads, then NO safety dot appears and no banner shows.
- [x] AC10: Given the user views a POI detail page, when the SAFETY bucket is rendered, then it shows the enhanced Gemini-generated local safety context with an "AI-generated safety context" disclaimer label.
- [x] AC11: Given a country has sub-national advisories (e.g., Pakistan — some regions dangerous), when the user is in a named region, then the provider text-matches the region name against FCDO "Safety and security" content and reflects the regional risk level if found.
- [x] AC12: Given the user kills the app and relaunches into the same area, when the app loads, then the cached advisory from preferences loads instantly (no network required), and the safety dot/banner appear without delay.
- [x] AC13: Given the "Learn More" button is tapped on the safety banner, when tapped, then the FCDO advisory source URL opens in the system browser.

## Additional Context

### Dependencies

- **FCDO Content API**: `gov.uk/api/content/foreign-travel-advice/{country-slug}` — free, no API key required, JSON response. Rate limit: be polite (24h cache handles this).
- **MapTiler Geocoding**: Already in codebase — provides country code + region name from lat/lng. No redundant call needed.
- **multiplatform-settings**: Already in codebase (or add if not) — for persisting advisory cache across cold launches.
- **kotlinx-datetime**: Required for parsing FCDO's `public_updated_at` ISO 8601 string (e.g., `"2025-12-15T10:30:00Z"`) to `Long` epoch millis in commonMain. `java.time` is JVM-only. Add `org.jetbrains.kotlinx:kotlinx-datetime` to `libs.versions.toml` + `build.gradle.kts`. Usage: `Instant.parse(isoString).toEpochMilliseconds()`. [FIX P5-M1]

### Testing Strategy

**Unit Tests:**
- `FcdoAdvisoryProviderTest` — mock HTTP responses for each advisory level, verify parsing, verify cache behavior (hit/miss/expiry, UNKNOWN never cached), verify error returns UNKNOWN (not SAFE), verify slug mapping. Include isolated tests for extracted `internal fun classifyAdvisoryLevel(summary: String): AdvisoryLevel`: "advises against all travel" → DO_NOT_TRAVEL, "advises against all but essential" → RECONSIDER, "exercise increased caution" → CAUTION, neutral → SAFE, empty → SAFE, unexpected phrasing → SAFE + logged warning.
- `FcdoCountrySlugsTest` — verify static map entries, verify fallback derivation for unmapped countries
- `CompanionNudgeEngineTest` — verify `buildSafetyNudge()` returns nudge for CAUTION/RECONSIDER/DO_NOT_TRAVEL, returns null for SAFE and UNKNOWN
- `MapViewModelTest` — verify advisory fetched on area change, verify banner dismiss state, verify gate acknowledgment state, verify `goBackToSafety` with/without previous area, verify nudge enqueued for Level 2+

**Manual Testing (Android):**
- Teleport to Yemen → verify gate modal appears with details
- Teleport to Mexico → verify yellow banner + yellow dot
- Teleport to Japan → verify NO dot, no banner
- Dismiss banner → verify dot persists
- Kill app, reopen same area → verify cached advisory loads instantly
- Turn off network → verify grey dot (UNKNOWN), no crash
- Tap "Learn More" → verify FCDO URL opens in browser
- Tap "Go Back to Safety" with previous area → verify navigates back
- Tap "Go Back to Safety" on cold launch (no previous area) → verify gate dismisses, banner shows

**Manual Testing (iOS Simulator):** [FIX M5]
- All above scenarios repeated on iOS Simulator
- Verify SafetyGateModal dismisses correctly (no Android back button — swipe gesture or "Go Back" button only)
- Verify PlatformBackHandler is no-op on iOS (does not interfere)
- Build + launch check for compile errors

### Notes

- **Risk: FCDO API format changes** — gov.uk content API is stable but not versioned. Monitor for breaking changes.
- **Risk: Keyword-based level detection** — advisory level is derived from summary text keywords ("advises against all travel" → DO_NOT_TRAVEL). If FCDO changes phrasing, detection silently degrades to SAFE. Mitigation: log unmatched summaries for monitoring. Post-beta: consider mapping from FCDO's structured `alert_status` field if available. [FIX N-L1]
- **Risk: Sub-national text matching** — best-effort only. FCDO doesn't provide structured region data. Text matching "Balochistan" in the safety body is pragmatic for beta but not perfect. If region not found in text, falls back to country-level advisory.
- **Future: Multi-source** — architecture supports adding US State Dept behind same `AdvisoryProvider` interface.
- **Future: Persistent cache upgrade** — currently using Settings/preferences (key-value). Post-beta: migrate to Room/SQLDelight for richer queries.
- **Review findings addressed**: H1 (enum comparison), H2 (hasAcknowledgedGate), H3 (fail-to-UNKNOWN), H4 (sub-national text matching), H5 (slug mapping task), H6 (persistent cache), H7 (Go Back navigation), M1 (FCDO schema documented), M2 (nudge strings localized), M3 (Learn More opens browser), M4 (display lastUpdated), M5 (iOS test plan), L1 (no redundant geocoding), L2 (dead param removed), L3 (no dot when safe), L4 (independent pulse animation).
- Brainstorm reference: `_bmad-output/brainstorming/brainstorming-session-2026-03-17-001.md` (Item 9, ideas #111-130)
- Prototype reference: `_bmad-output/brainstorming/prototype-category-a-streaming-discovery.html` (Scene 4: Safety Gate)
- PRD reference: FR23-24, FR36, NFR22-25
