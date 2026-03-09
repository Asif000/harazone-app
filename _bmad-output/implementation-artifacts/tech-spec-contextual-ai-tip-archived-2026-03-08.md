---
title: 'Contextual AI Tip'
slug: 'contextual-ai-tip'
created: '2026-03-08'
status: 'in-progress'
stepsCompleted: [1, 2]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin 4.x', 'Ktor (ContentNegotiation + SSE)', 'Gemini API (generateContent non-streaming)', 'kotlinx.coroutines.test']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/AreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MockAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt'
files_to_create:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ContextualTipCard.kt'
code_patterns:
  - 'StateFlow in ViewModel, collectAsStateWithLifecycle in Composable'
  - 'AnimatedVisibility with fadeIn/fadeOut for map UI overlays'
  - 'Box z-ordering in ReadyContent for overlay stacking'
  - 'Koin viewModel{} with positional get() params'
  - 'suspend fun on AreaIntelligenceProvider for non-streaming calls'
  - 'withRetry wrapper for Gemini calls'
  - 'LaunchedEffect for side-effects tied to state changes'
  - 'PlatformBackHandler for dismissible overlays (not needed here — tip is tap-dismiss only)'
test_patterns:
  - 'kotlin.test annotations (@Test, @BeforeTest, @AfterTest)'
  - 'Dispatchers.setMain/resetMain with UnconfinedTestDispatcher'
  - 'advanceTimeBy() for delay-based logic'
  - 'FakeAreaIntelligenceProvider with var tipResult: Result<String>'
  - 'GeminiPromptBuilderTest pattern: instantiate builder, call method, assertTrue/assertFalse on string content'
---

# Tech-Spec: Contextual AI Tip

**Created:** 2026-03-08

## Overview

### Problem Statement

The map screen is passive — users must actively tap vibes, pins, or the AI bar to discover anything. There is no proactive signal from the app that volunteers useful, contextual information about the current area.

### Solution

After an area portrait loads, trigger a single lightweight Gemini call that returns one short tip (1–2 sentences) contextual to the area name, time of day, and active vibe. Display it as a dismissible floating card above the AI bar (bottom of screen). Auto-hide after 10 seconds. English only for v1. Separate from AI Chat and onboarding.

### Scope

In Scope:
- Single contextual tip per area load (not per vibe switch)
- Gemini-generated, 1–2 sentences, English only
- Appears 2s after POIs finish loading
- Placement: bottom of screen, above AI bar
- Dismiss on tap anywhere on the tip card
- Auto-hide after 10 seconds
- Silent failure — if Gemini call fails, no tip is shown
- New area search clears previous tip immediately

Out of Scope:
- Multi-tip lists
- Tip regeneration on vibe switch
- Saving/combining tips across areas (deferred to itinerary phase)
- Localisation (English only for v1)
- Onboarding integration
- Analytics beyond basic event tracking

## Context for Development

### Codebase Patterns

- `MapUiState.Ready` is a data class with `copy()` for all state transitions — add `contextualTip: String? = null` and `tipVisible: Boolean = false` as default fields.
- `MapViewModel` uses `_uiState: MutableStateFlow<MapUiState>` — all state mutation follows the `val current = _uiState.value as? MapUiState.Ready ?: return` guard pattern.
- `MapViewModel` already has `AreaContextFactory` injected — reuse `areaContextFactory.create()` for the tip call context.
- `AreaIntelligenceProvider` interface has two existing methods — add a third: `suspend fun fetchContextualTip(areaName: String, context: AreaContext): String`.
- `GeminiAreaIntelligenceProvider` uses `httpClient.sse()` for streaming — for the tip, use `httpClient.post(...).body<GeminiGenerateResponse>()` (non-streaming `generateContent` endpoint). `ContentNegotiation` JSON is already installed on the shared `HttpClient`.
- The non-streaming Gemini endpoint is: `$BASE_URL/$GEMINI_MODEL:generateContent` (POST, `?key=apiKey`). Response shape: `{"candidates":[{"content":{"parts":[{"text":"..."}]}}]}`.
- `MockAreaIntelligenceProvider` stubs each interface method — add `override suspend fun fetchContextualTip(...): String = "Explore on foot — most alleys are too narrow for cars."`.
- `FakeAreaIntelligenceProvider` in test fakes — add `var tipResult: Result<String> = Result.success("Test tip")` and implement method to return it or throw.
- `MapViewModel` currently takes 7 Koin-injected params — add `AreaIntelligenceProvider` as the 8th. `UiModule` `viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }`.
- `AnimatedVisibility(visible = tipVisible, enter = fadeIn(), exit = fadeOut())` wraps the tip card — same pattern as `MyLocation` button.
- `LaunchedEffect(state.tipVisible)` in `ReadyContent` handles the 10s auto-hide: `if (state.tipVisible) { delay(10_000); viewModel.dismissTip() }`.
- No `PlatformBackHandler` needed — tip is not a modal/overlay with scrim, it is a tap-to-dismiss inline card.
- `ContextualTipCard` is a new standalone composable in `ui/map/components/` — follows naming convention of `VibeRail`, `FabMenu`, `TopContextBar`.
- Tests use `advanceTimeBy(2_001)` to simulate the 2s trigger delay and `advanceTimeBy(10_001)` to simulate auto-hide.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/AreaIntelligenceProvider.kt` | Interface — add `fetchContextualTip()` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt` | Implementation — add non-streaming Gemini call + response model |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/MockAreaIntelligenceProvider.kt` | Stub — add hardcoded tip string |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt` | Add `buildContextualTipPrompt()` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapUiState.kt` | Add `contextualTip: String?` and `tipVisible: Boolean` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt` | Add tip fetch, dismiss, clear logic + new AreaIntelligenceProvider param |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/di/UiModule.kt` | Add `get()` for AreaIntelligenceProvider in MapViewModel registration |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt` | Insert ContextualTipCard + LaunchedEffect for auto-hide |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ContextualTipCard.kt` | New composable (create) |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt` | Add tip fake |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt` | Add tip behaviour tests |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt` | Add tip prompt tests |

### Technical Decisions

- Non-streaming Gemini call: tip is one sentence — no need for SSE. Use `generateContent` POST endpoint, parse `candidates[0].content.parts[0].text`. `ContentNegotiation` JSON already installed.
- `AreaIntelligenceProvider` gets a 3rd `suspend fun` method (not a `Flow`) — consistent with the principle that non-streaming results don't need flow.
- `MapViewModel` gets `AreaIntelligenceProvider` as an 8th constructor param — same pattern `ChatViewModel` uses. No new use-case layer needed.
- Tip state is ephemeral: not persisted to DB, not cached. Each area load fetches fresh.
- 2s trigger: `delay(2_000)` inside a `viewModelScope.launch` block kicked off from `collectPortraitWithRetry`'s `onComplete` callback.
- Auto-hide: `LaunchedEffect(state.tipVisible)` in `ReadyContent` — `if (state.tipVisible) { delay(10_000); viewModel.dismissTip() }`. Cancel-safe because LaunchedEffect cancels on recomposition when `tipVisible` flips to false.
- Tip clears immediately when new area search starts: set `contextualTip = null, tipVisible = false` in all `_uiState.value = current.copy(isSearchingArea = true, ...)` blocks (4 locations in MapViewModel: `onGeocodingSuggestionSelected`, `onRecentSelected`, `onGeocodingSubmitEmpty`, `returnToCurrentLocation`).

## Implementation Plan

### Tasks

TBD — Step 3 will produce the ordered task list.

### Acceptance Criteria

TBD — Step 3 will produce Given/When/Then ACs.

## Additional Context

### Dependencies

- Gemini API key already configured via `BuildKonfigApiKeyProvider` — no changes needed
- `AreaContext` already provides `timeOfDay` and `dayOfWeek` — no new platform code needed
- `Vibe` model already available in `MapUiState.Ready.activeVibe` — pass `activeVibe?.name` to prompt builder
- Shared `HttpClient` already has `ContentNegotiation` JSON — no factory changes needed

### Testing Strategy

- `GeminiPromptBuilderTest`: `buildContextualTipPrompt` includes area name, timeOfDay, dayOfWeek, vibe name
- `MapViewModelTest`: tip is null before portrait loads
- `MapViewModelTest`: tip appears after 2s delay when portrait completes with POIs
- `MapViewModelTest`: tip clears immediately when new area search starts
- `MapViewModelTest`: tip not shown if `fetchContextualTip` throws (silent failure)
- `MapViewModelTest`: `dismissTip()` sets `tipVisible = false`

### Notes

- Backlog item (LOW): multi-destination tip combining — deferred to itinerary phase
- English only for v1 — localisation hook via `AreaContext.preferredLanguage` already exists for Phase 2
- The `GeminiGenerateResponse` response model (for parsing non-streaming response) can be a private `@Serializable` data class inside `GeminiAreaIntelligenceProvider.kt` — no need to expose it
- DEFERRED CONNECTION: explore integrating contextual tip with onboarding flow — tip could serve as the first "wow moment" introducing AI capability during first-run. Revisit when onboarding spec (Epic 5 Story 5.2) is designed.
