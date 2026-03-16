---
title: 'AI Mirror Profile Page'
slug: 'ai-mirror-profile-page'
created: '2026-03-15'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4, 5]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'SQLDelight', 'Koin', 'Gemini API (Ktor SSE + generateContent)', 'Turbine (test)']
files_to_modify: ['composeApp/src/commonMain/kotlin/com/harazone/ui/profile/ProfileScreen.kt (NEW)', 'composeApp/src/commonMain/kotlin/com/harazone/ui/profile/ProfileViewModel.kt (NEW)', 'composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt (ADD generateProfileIdentity + streamProfileChat)', 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt (IMPL generateProfileIdentity + streamProfileChat)', 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt (ADD buildProfileIdentityPrompt + buildProfileChatSystemPrompt)', 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt (ADD parseProfileIdentityResponse)', 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/MockAreaIntelligenceProvider.kt (ADD generateProfileIdentity + streamProfileChat)', 'composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt (ADD ProfileViewModel)', 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt (ADD profile entry point + showProfile state)', 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/ProfileIdentity.kt (NEW)', 'composeApp/src/commonMain/sqldelight/com/harazone/db/ProfileIdentityCache.sq (NEW)', 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/ProfileIdentityCacheRepository.kt (NEW)', 'composeApp/src/commonTest/kotlin/com/harazone/ui/profile/ProfileViewModelTest.kt (NEW)', 'composeApp/src/commonTest/kotlin/com/harazone/data/repository/ProfileIdentityCacheRepositoryTest.kt (NEW)', 'composeApp/src/commonTest/kotlin/com/harazone/data/remote/ProfilePromptTest.kt (NEW)']
code_patterns: ['MVVM with Koin viewModel injection', 'StateFlow + collectAsStateWithLifecycle', 'Full-screen overlay pattern (like AiDetailPage — selectedPoi != null hides map UI)', 'Non-streaming Gemini call pattern (generatePoiContext: prompt → POST → parse)', 'Streaming chat pattern (streamChatResponse: SSE → Flow<ChatToken>)', 'PlatformBackHandler for all dismissible overlays', 'FakeXxx pattern for test doubles (FakeAreaIntelligenceProvider, FakeSavedPoiRepository, FakeClock)', 'TasteProfileBuilder for saved POI analysis (affinities, emerging interests, absences)']
test_patterns: ['kotlin.test + Turbine + UnconfinedTestDispatcher', 'FakeXxx classes in commonTest/fakes/', 'Dispatchers.setMain/resetMain in BeforeTest/AfterTest', 'StateFlow assertions via turbine .test { }', 'Pure function tests for domain model logic']
---

# Tech-Spec: AI Mirror Profile Page

**Created:** 2026-03-15

## Overview

### Problem Statement

Users discover and save places but the app provides no reflection of their patterns, preferences, or evolving identity. The app knows the user — their favorite vibes, geographic footprint, time-of-day habits — but doesn't show it. There is no "mirror" that reflects who you are as an explorer.

### Solution

A living profile page that reads SavedPoi + visit tracking data, uses Gemini (non-streaming single call) to generate an AI identity (name, tagline, stats), and presents:
- Compact identity strip above the fold (avatar + AI-generated name + tagline + stats)
- Geographic footprint with country/city flags and visit counts
- Vibe capsules that expand inline showing AI insight + list of visited places
- AI chat agent anchored at bottom with suggestion pills for self-discovery
- Entry via top bar avatar/icon on the map screen

Place taps within vibe expansions open AiDetailPage. Profile evolves dynamically with every visit.

### Scope

**In Scope:**
- Profile screen composable (full-screen overlay from map)
- Identity strip: Gemini-generated name, tagline, avatar emoji, stats (visits, areas, vibes)
- Geographic footprint: country flags, city names, visit counts, tappable
- Vibe capsules: top vibes highlighted, expand inline with AI insight + place list
- Profile AI chat agent: new ProfileViewModel, dedicated Gemini prompt, suggestion pills
- Entry point: avatar/profile icon in map top bar
- PlatformBackHandler for dismissal
- Data reads from SavedPoi + visit tracking fields (provided by #52 Visit Feature)

**Out of Scope:**
- Backend infrastructure
- Push notifications
- Social layer / sharing
- Trip planning / itinerary
- Visit tracking data creation (handled by #52)

## Context for Development

### Codebase Patterns

- Architecture: MVVM with Koin DI. ViewModels registered in `UiModule.kt` via `viewModel { }`. Injected in composables via `koinViewModel()`.
- State: `MutableStateFlow` → `StateFlow` exposed via `.asStateFlow()`. Composables collect with `collectAsStateWithLifecycle()`.
- Overlays: Full-screen overlay pattern used by AiDetailPage — when `selectedPoi != null`, map UI elements (ticker, vibe rail, search bar, carousel, my-location, saves pill) are hidden. Profile should follow same pattern with `showProfile` state.
- Gemini non-streaming: `generatePoiContext()` pattern — build prompt via `GeminiPromptBuilder`, POST to `generateContent` endpoint, parse via `GeminiResponseParser`. Returns nullable result with try/catch fallback.
- Gemini streaming chat: `streamChatResponse()` — SSE via Ktor, emits `Flow<ChatToken>`. Profile chat can reuse this for conversational follow-ups.
- Existing analysis: `TasteProfileBuilder.build(saves, nowMs)` already computes strongAffinities, emergingInterests, notableAbsences, diningStyle, totalSaves from SavedPoi list. Profile identity prompt should include this.
- Existing engagement: `EngagementLevel.from(saves, nowMs)` — FRESH/LIGHT/REGULAR/POWER/DORMANT.
- Test doubles: `FakeAreaIntelligenceProvider`, `FakeSavedPoiRepository`, `FakeClock`, `FakeLocaleProvider` in `commonTest/fakes/`.
- Back handler: `PlatformBackHandler(enabled = condition) { action }` — expect/actual, Android delegates to BackHandler, iOS no-op.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/model/SavedPoi.kt` | Data model — id, name, type, areaName, lat, lng, whySpecial, savedAt, userNote, imageUrl, description, rating, vibe |
| `domain/repository/SavedPoiRepository.kt` | Interface — observeAll(), observeSavedIds(), save(), unsave(), updateUserNote() |
| `domain/model/TasteProfileBuilder.kt` | Builds TasteProfile from SavedPoi list (affinities, emerging, absences) |
| `domain/model/TasteProfile.kt` | Data class — strongAffinities, emergingInterests, notableAbsences, diningStyle, totalSaves |
| `domain/model/EngagementLevel.kt` | FRESH/LIGHT/REGULAR/POWER/DORMANT from saves + recency |
| `domain/provider/AreaIntelligenceProvider.kt` | Interface — streamAreaPortrait, streamChatResponse, generatePoiContext |
| `data/remote/GeminiAreaIntelligenceProvider.kt` | Gemini impl — SSE streaming + non-streaming POST |
| `data/remote/GeminiPromptBuilder.kt` | All Gemini prompts centralized here |
| `data/remote/GeminiResponseParser.kt` | All Gemini response parsing centralized here |
| `data/remote/MockAreaIntelligenceProvider.kt` | Mock impl for development/testing |
| `ui/map/MapScreen.kt` | Main screen hub — add profile entry point here |
| `ui/map/ChatViewModel.kt` | Reference for chat pattern (openChat, sendMessage, bubbles, pills, streaming) |
| `ui/map/components/AiDetailPage.kt` | Full-screen overlay pattern to follow; also reuse for place taps |
| `ui/settings/SettingsSheet.kt` | ModalBottomSheet + PlatformBackHandler pattern |
| `di/UiModule.kt` | Koin ViewModel registration |
| `fakes/FakeAreaIntelligenceProvider.kt` | Test double for AI provider |
| `fakes/FakeSavedPoiRepository.kt` | Test double for saved POI repo |

### Technical Decisions

- Non-streaming Gemini call for identity generation (like `generatePoiContext`) — identity doesn't need streaming, single call is simpler
- New `ProfileViewModel` (not reusing ChatViewModel — different concerns: profile reads ALL saved data, builds identity, has its own prompt context for self-discovery chat)
- Entry via profile icon in top bar area of MapScreen (near TopContextBar or as standalone icon)
- Place taps within vibe expansions reuse existing `AiDetailPage` — pass POI + area name
- Visit data schema: expects visitCount, visitedAt, visitState fields on SavedPoi (contract with #52 Visit Feature)
- SavedPoi data as foundation — profile reads all saved + visited POIs to build identity
- Reuse `TasteProfileBuilder` output as input to the identity Gemini prompt
- Profile chat uses `streamProfileChat` (new method) for conversational follow-ups with profile-specific system prompt
- Dedicated profile system prompt with user's full save/visit history as context
- Geo footprint: `countryCode` sourced from Gemini identity response `geoFootprint` array (areaName + countryCode). `poiCount` always computed client-side from SavedPoi grouping (avoids Gemini/client count mismatches). Client maps countryCode → flag emoji. Fallback on Gemini failure: client-side grouping by areaName with globe emoji.

## Implementation Plan

### Tasks

- [ ] Task 1: Create `ProfileIdentity` data model
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/ProfileIdentity.kt` (NEW)
  - Action: Create data class with fields from Gemini identity response:
    ```kotlin
    data class ProfileIdentity(
        val explorerName: String,        // e.g. "Night Foodie Explorer"
        val tagline: String,             // e.g. "Flavor after dark, hidden corners"
        val avatarEmoji: String,         // e.g. "🌙"
        val totalVisits: Int,
        val totalAreas: Int,
        val totalVibes: Int,
        val geoFootprint: List<GeoArea>,          // from Gemini response, authoritative geo data
        val vibeInsights: List<VibeInsight>,       // per-vibe AI insight text
        // NOTE: No generatedAtHash here. The input hash is a caching concern and lives
        // only in the profile_identity_cache table (input_hash column) and in
        // ProfileIdentityCacheRepository. The domain model stays clean of infrastructure.
    )

    data class GeoArea(
        val areaName: String,            // e.g. "Vila Madalena, São Paulo"
        val countryCode: String,         // ISO 3166-1 alpha-2, e.g. "BR"
        // NOTE: poiCount is NOT sourced from GeoArea. Gemini provides areaName + countryCode only.
        // poiCount is always computed client-side by grouping SavedPoi by areaName. This avoids
        // count mismatches when the POI list is truncated to 50 or Gemini hallucinates a count.
    )

    data class VibeInsight(
        val vibeName: String,            // matches vibe label
        val insight: String,             // e.g. "Your #1 vibe. Solo weeknights, friends on weekends."
    )
    ```
  - Notes: Pure Kotlin data class, no Android deps. Used by both VM and Gemini parser. `GeoArea` contains server-authoritative `countryCode` (ISO 3166-1 alpha-2) — client maps to flag emoji via `String(Character.toChars(0x1F1E6 + c - 'A'))` for each char. Cache invalidation hash lives in `ProfileIdentityCacheRepository` / cache table only, not on this domain model.

- [ ] Task 1b: Add SQLDelight `profile_identity_cache` table and repository
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/db/ProfileIdentityCache.sq` (NEW) + `composeApp/src/commonMain/kotlin/com/harazone/data/repository/ProfileIdentityCacheRepository.kt` (NEW)
  - Action:
    - SQLDelight table:
      ```sql
      CREATE TABLE profile_identity_cache (
          id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,  -- singleton row
          identity_json TEXT NOT NULL,                 -- serialized ProfileIdentity
          input_hash TEXT NOT NULL,                    -- hash of saved POI IDs + count
          created_at INTEGER NOT NULL                  -- epoch ms
      );

      upsertIdentity:
      INSERT OR REPLACE INTO profile_identity_cache(id, identity_json, input_hash, created_at)
      VALUES (1, ?, ?, ?);

      getIdentity:
      SELECT * FROM profile_identity_cache WHERE id = 1;

      clearCache:
      DELETE FROM profile_identity_cache;
      ```
    - Repository: `ProfileIdentityCacheRepository` with `suspend fun getCached(): Pair<ProfileIdentity, String>?` (returns identity + stored inputHash), `suspend fun cache(identity: ProfileIdentity, inputHash: String)`, `fun computeInputHash(savedPois: List<SavedPoi>): String`. The VM compares `computeInputHash(currentPois)` against the stored hash to decide if a refresh is needed.
    - **Hash specification:** `computeInputHash` builds the input string as `sortedIds.joinToString(separator = ",") + "|" + count.toString()` then computes SHA-256 hex. Example: POI IDs `["abc", "def", "ab"]` with count 3 → input `"ab,abc,def|3"` → SHA-256 hex string. The comma separator prevents ambiguity between IDs (e.g., `["a","bc"]` → `"a,bc|2"` vs `["abc"]` → `"abc|1"`). The pipe separator separates the ID list from the count.
  - Notes: Cache key is a hash of sorted saved POI IDs + total count. On profile open: (1) show cached identity instantly if hash matches, (2) if hash differs (new save since last generation), show cached identity but refresh in background, (3) if no cache, show shimmer and fetch. This eliminates the Gemini call on every profile open.

- [ ] Task 2: Add `generateProfileIdentity` to `AreaIntelligenceProvider` interface
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt`
  - Action: Add method signature:
    ```kotlin
    suspend fun generateProfileIdentity(
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        engagementLevel: EngagementLevel,
        languageTag: String = "en",
    ): ProfileIdentity?
    ```
  - Notes: Nullable return — mirrors `generatePoiContext` pattern for graceful failure.

- [ ] Task 3: Add `streamProfileChat` to `AreaIntelligenceProvider` interface
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt`
  - Action: Add method signature:
    ```kotlin
    fun streamProfileChat(
        query: String,
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        conversationHistory: List<ChatMessage>,
        languageTag: String = "en",
    ): Flow<ChatToken>
    ```
  - Notes: Same `Flow<ChatToken>` return as `streamChatResponse` — streaming SSE pattern. System prompt differs (profile context instead of area context).
  - **Conversation history cap:** The `conversationHistory` parameter is limited to the most recent 20 messages (10 user + 10 AI turns). The system prompt with 50 POIs consumes ~8,300 tokens; each conversation turn averages ~100 tokens (user message ~30, AI response ~70). 20 messages ≈ 2,000 tokens. Total budget: ~10,300 tokens — safely under context limits. The VM applies the sliding window before calling `streamProfileChat`: `conversationHistory.takeLast(20)`. Older messages remain in `chatBubbles` for scrollback display but are not sent to Gemini.

- [ ] Task 4: Build Gemini prompts for profile
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Add two new methods:
    - `buildProfileIdentityPrompt(savedPois, tasteProfile, engagementLevel, languageTag)` — non-streaming identity generation. Include:
      - Full list of saved places grouped by area and vibe
      - TasteProfile summary (affinities, emerging, absences)
      - Engagement level
      - JSON output schema matching `ProfileIdentity` fields, including `geoFootprint` array with `areaName` (String) and `countryCode` (ISO 3166-1 alpha-2 String) for each unique area. No `poiCount` in the Gemini response — counts are computed client-side from SavedPoi to avoid mismatches when the POI list is truncated to 50
      - Instructions: creative explorer name, poetic tagline, representative emoji, per-vibe insight sentences, per-area country code
      - Language rule: if `languageTag != "en"`, include explicit instruction: `"You MUST respond entirely in {languageTag}. All fields — explorerName, tagline, vibeInsights — must be in {languageTag}. Do NOT mix languages."` This is stronger than the existing `languageBlock()` pattern. **Placement: this language instruction must be the absolute last line of the prompt, after the JSON output schema.** Recency bias in LLMs means the final instruction carries the most weight — the schema defines structure, the language rule constrains all content within that structure.
      - **POI cap:** Include at most 50 POIs. Estimation: ~150 tokens per POI (name + area + vibe + note ≈ 30 words × 1.3 tokens/word + field overhead). 50 POIs ≈ 7,500 input tokens. Combined with system prompt (~500 tokens) and output schema (~300 tokens), total ≈ 8,300 tokens — well within Gemini 1.5 Flash 1M context. **Truncation strategy:** if more than 50 saved POIs, include the 50 with highest `visitCount` (breaking ties by most recent `savedAt`). This prioritizes places the user has actually engaged with.
    - `buildProfileChatSystemPrompt(savedPois, tasteProfile, languageTag)` — system prompt for profile chat. Include:
      - User's save/visit history as context, **using the same 50-POI truncation** as the identity prompt (same selection: top 50 by visitCount, tiebreak by savedAt). This ensures the chat AI only references places that were also used to generate the identity — preventing inconsistencies where the chat mentions patterns from POIs the identity prompt never saw.
      - Persona: "You are the user's AI mirror — you know their exploration patterns intimately"
      - Tone: insightful, warm, occasionally surprising
      - Language rule if non-English
  - Notes: Follow existing prompt patterns (string templates, language rules via `languageBlock()`).

- [ ] Task 5: Add `parseProfileIdentityResponse` to `GeminiResponseParser`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt`
  - Action: Add:
    - `@Serializable` internal data class `ProfileIdentityJson` matching Gemini output schema
    - `fun parseProfileIdentityResponse(text: String): ProfileIdentity?` — strip markdown fences, deserialize, map to domain model
  - Notes: Follow `parsePoiContextResponse` pattern — try/catch with AppLogger.e on failure, return null. **Language validation:** after parsing, if `languageTag != "en"`, log a warning via `AppLogger.w` if the response contains common English-only words (heuristic: check if explorerName or tagline contain "the", "and", "your" when target is non-English). This is diagnostic only — do not reject the response, as Gemini may mix in some English (especially for proper nouns). The warning helps track language compliance rates for prompt tuning.

- [ ] Task 6: Implement `generateProfileIdentity` + `streamProfileChat` in `GeminiAreaIntelligenceProvider`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`
  - Action:
    - `generateProfileIdentity`: Build prompt via `promptBuilder.buildProfileIdentityPrompt()`, POST to `generateContent`, parse via `responseParser.parseProfileIdentityResponse()`. Try/catch with null fallback.
    - `streamProfileChat`: Build system prompt via `promptBuilder.buildProfileChatSystemPrompt()`, use existing SSE streaming infrastructure (same as `streamChatResponse` but with profile system prompt instead of area persona).
  - Notes: Follow `generatePoiContext` pattern exactly for the non-streaming call. For streaming, reuse SSE connection logic.

- [ ] Task 7: Add stubs to `MockAreaIntelligenceProvider`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/MockAreaIntelligenceProvider.kt`
  - Action: Add `generateProfileIdentity` returning a hardcoded `ProfileIdentity` and `streamProfileChat` returning a simple `flow { emit(ChatToken("Mock profile response")) }`.
  - Notes: Keeps mock provider compiling and usable for offline dev.

- [ ] Task 8: Add `generateProfileIdentity` + `streamProfileChat` to `FakeAreaIntelligenceProvider`
  - File: `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeAreaIntelligenceProvider.kt`
  - Action: Add configurable fake implementations (e.g., `var profileIdentityResult: ProfileIdentity? = ...` and `var profileChatTokens: List<ChatToken> = ...`).
  - Notes: Follows existing fake patterns — test can configure return values.

- [ ] Task 9: Create `ProfileViewModel`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/profile/ProfileViewModel.kt` (NEW)
  - Action: Create ViewModel with:
    - Dependencies: `SavedPoiRepository`, `AreaIntelligenceProvider`, `ProfileIdentityCacheRepository`, `AppClock`, `LocaleProvider`
    - Note: `GeminiPromptBuilder` is NOT a VM dependency — prompt construction belongs inside `GeminiAreaIntelligenceProvider` (the implementation). The VM calls `AreaIntelligenceProvider.generateProfileIdentity()` which internally delegates to the prompt builder. This preserves the architecture boundary: VM depends on domain interfaces only.
    - State: `ProfileUiState` data class:
      ```kotlin
      data class ProfileUiState(
          val isLoading: Boolean = true,
          val identity: ProfileIdentity? = null,
          val geoFootprint: List<GeoEntry> = emptyList(),  // computed client-side
          val vibeGroups: List<VibeGroup> = emptyList(),    // grouped SavedPoi by vibe
          val expandedVibe: String? = null,                  // which vibe is expanded
          val chatBubbles: List<ProfileChatBubble> = emptyList(),
          val isStreaming: Boolean = false,
          val suggestionPills: List<String> = emptyList(),  // loaded from string resources (profile_pill_blindspot, profile_pill_try_next, profile_pill_why_name) in composable init or VM init{} via LocaleProvider
          val isOffline: Boolean = false,        // disables send + shows "Offline" indicator
          val identityRefreshAvailable: Boolean = false,  // true when background refresh has a newer identity ready
          val error: String? = null,
      )

      data class GeoEntry(
          val areaName: String,
          val countryCode: String,  // ISO 3166-1 alpha-2 from Gemini GeoArea response
          val countryFlag: String,  // emoji flag derived from countryCode (see below)
          val poiCount: Int,
          val isHome: Boolean,      // area with most POIs
      )
      // countryFlag derivation: map countryCode to flag emoji via:
      //   countryCode.uppercase().map { c -> String(Character.toChars(0x1F1E6 + (c - 'A'))) }.joinToString("")
      // e.g., "BR" → "🇧🇷", "JP" → "🇯🇵"
      // Fallback chain: (1) use countryCode from Gemini GeoArea response, (2) if countryCode is empty/invalid, show "🌍"

      data class VibeGroup(
          val vibeName: String,
          val poiCount: Int,
          val isTop: Boolean,       // top 2 vibes by count
          val places: List<SavedPoi>,
          val aiInsight: String,    // from ProfileIdentity.vibeInsights
      )

      data class ProfileChatBubble(
          val id: String,           // generated via java.util.UUID.randomUUID().toString() (expect/actual: Android=java.util.UUID, iOS=platform.Foundation.NSUUID)
          val text: String,
          val isUser: Boolean,
          val isError: Boolean = false,  // true for error bubbles (shown with retry action)
      )
      ```
    - On init: collect `savedPoiRepository.observeAll()`, compute geo footprint + vibe groups, call `generateProfileIdentity()`, populate UI state.
    - **Initial greeting bubble (AC 6):** After identity is loaded (or from cache), the VM constructs the greeting bubble locally using a template filled with identity data — NOT a separate Gemini call. The template uses numbered positional args in a fixed order documented in the string resource comment:
      - `profile_chat_greeting`: `"Hey %1$s! With %2$d visits across %3$d areas and %4$d vibes, I can see some interesting patterns — ask me anything."`
      - Args order: `(1) explorerName: String, (2) totalVisits: Int, (3) totalAreas: Int, (4) totalVibes: Int`
      - The arg order and count is documented in a `<!-- args: explorerName, totalVisits, totalAreas, totalVibes -->` XML comment above the string resource to prevent localizer errors.
      The VM creates a `ProfileChatBubble(id = generateUuid(), text = filledTemplate, isUser = false)` and sets it as the first entry in `chatBubbles`. Suggestion pills are loaded at the same time. If identity fails (fallback mode), the greeting uses `profile_chat_greeting_fallback`: `"I've been looking at your saved places — want to explore what they say about you?"` (no format args — static string, crash-safe).
    - `fun toggleVibe(vibeName: String)` — expand/collapse vibe capsule. **Single expansion only** (setting `expandedVibe` to the new vibe collapses the previous one). **UX rationale:** single expansion prevents long scroll jumps and keeps the chat area visible below the fold — users can always see the chat input without scrolling past multiple expanded vibe lists. Each vibe expansion can show 5-15 places with AI insight text, which is 300-600px of content; allowing multiple expansions would push the chat area off-screen and break the "mirror + conversation" layout metaphor.
    - `fun sendMessage(text: String)` — **no-op while `isStreaming == true`** (rate limiting). Send to `streamProfileChat`, collect tokens, append bubbles. **Streaming flow error handling order:** the `catch`/`finally` block on the streaming Flow must (1) set `isStreaming = false` FIRST, then (2) on network exception (IOException, UnresolvedAddressException): set `isOffline = true` and append error bubble, (3) on other exceptions: append error bubble only. This ordering is critical — if `isStreaming` remains `true` after a failure, both `retryLastMessage()` and `retryConnection()` would be gated by the no-op guard. The send button and suggestion pills are disabled (`alpha = 0.5f`) while `isStreaming == true`. Input field remains editable but send is gated.
    - `fun retryLastMessage()` — **error bubble retry (AC 14 only).** Removes the error bubble from `chatBubbles`, re-sends the last user message text via `sendMessage()`. Only callable when an error bubble exists in `chatBubbles`. Does NOT interact with `isOffline` state.
    - `fun retryConnection()` — **offline banner retry (AC 15 only).** Clears `isOffline` optimistically, then re-attempts the last failed send if one exists, or does nothing if the user hadn't sent a message yet (the banner just clears, re-enabling the send button). If the re-attempt fails with a network exception, `isOffline` is re-set. This is distinct from `retryLastMessage()`: offline retry is about restoring connectivity, error retry is about re-sending after a mid-stream failure.
    - `fun applyRefreshedIdentity()` — applies the pending refreshed identity (stored in a private `pendingIdentity` field) to the UI state: updates `identity`, recomputes `geoFootprint` and `vibeGroups`, sets `identityRefreshAvailable = false`. Also updates the first chat bubble (the greeting) to reflect the new explorer name — replaces the greeting bubble's text with a freshly formatted `profile_chat_greeting` using the new identity's fields. Subsequent chat bubbles and conversation history are NOT reset — the chat context sent to Gemini continues using the identity that was active at chat start (acceptable minor inconsistency; a full context reset would lose the user's conversation).
    - `fun tapSuggestionPill(pill: String)` — calls `sendMessage(pill)`, hides pills
    - `fun getPoiForDetail(savedPoi: SavedPoi): POI` — convert SavedPoi to POI for AiDetailPage
    - Geo footprint logic: **poiCount is always computed client-side** by grouping SavedPoi by `areaName` (this avoids count mismatches when the Gemini prompt truncates to 50 POIs). **countryCode** comes from `ProfileIdentity.geoFootprint` GeoArea entries. **Matching strategy:** The identity prompt includes the exact `areaName` strings from SavedPoi, and instructs Gemini to return them verbatim in `geoFootprint[].areaName` (prompt instruction: "Return the areaName exactly as provided in the input, do not normalize or abbreviate"). Match is case-insensitive `trim()` equality. Build a `Map<String, String>` of `areaName.lowercase().trim() → countryCode` from Gemini's response, then look up each client-side area group's key. **Unmatched areas** (Gemini reformatted or omitted an area): fall back to "🌍" globe emoji for that specific area — not the entire footprint. Sort by `poiCount` descending, mark highest as `isHome = true`.
    - **Cache-aware init flow:** (1) check `ProfileIdentityCacheRepository.getCached()`, (2) if cached and hash matches current POIs, show instantly — no Gemini call, (3) if hash differs or no cache, show cached (if any) + fetch fresh in background, (4) on fresh result, update cache and UI.
    - **Session identity freeze policy:** Once the identity is displayed for this profile session (whether from cache or fresh fetch), it is frozen for the duration the profile overlay is open. If a background refresh completes with a different identity, the VM stores the new identity in the cache but does NOT update the UI state. Instead, it sets `identityRefreshAvailable = true` in state, and the UI shows a subtle "Profile updated — tap to refresh" pill at the top of the identity strip. Tapping it applies the new identity (updates explorerName, tagline, geo footprint, vibe insights in one state emission). This prevents the disorienting experience of the explorer name or vibe insights changing mid-read or mid-chat. The chat system prompt always uses the identity that was active when the chat started.
  - Notes: Register in UiModule. Pattern follows ChatViewModel structure.

- [ ] Task 10: Register `ProfileIdentityCacheRepository` and `ProfileViewModel` in Koin
  - File: `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt` (or `DataModule.kt` if repositories are registered there)
  - Action:
    - Add `single { ProfileIdentityCacheRepository(get()) }` — singleton, depends on `AreaDiscoveryDatabase`. Must be registered before the ViewModel.
    - Add `viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }`
  - Notes: Dependencies for ProfileIdentityCacheRepository: `AreaDiscoveryDatabase` (already in DI). Dependencies for ProfileViewModel: SavedPoiRepository, AreaIntelligenceProvider, ProfileIdentityCacheRepository, AppClock, LocaleProvider. No GeminiPromptBuilder — prompt building happens inside GeminiAreaIntelligenceProvider.

- [ ] Task 11: Create `ProfileScreen` composable
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/profile/ProfileScreen.kt` (NEW)
  - Action: Full-screen dark overlay composable with:
    - Layout: Column with `profile-top` (fixed, above fold) and `profile-chat-area` (flex, fills remaining)
    - Identity strip: avatar emoji (gradient circle), explorer name, tagline (italic, accent color), stats row (visits / areas / vibes — **format numbers via locale-aware formatting:** use `NumberFormat.getInstance(locale)` or Compose `stringResource` with `%d` which handles locale formatting. For MVP counts (<1000) this is cosmetic, but establishes the correct pattern for when counts grow). **Refresh pill:** when `identityRefreshAvailable == true`, show a small pill/chip above the identity strip: `profile_refresh_available` text, teal accent border, tap calls `viewModel.applyRefreshedIdentity()`. Wrapped in `AnimatedVisibility(enter = fadeIn + slideInVertically)`. On tap, the identity strip transitions via `AnimatedContent` crossfade (300ms).
    - Shimmer placeholder while `isLoading` — same pattern as AiDetailPage context block
    - Geographic footprint: horizontal scrollable Row of geo pills with flag emoji + area name + count. "Home" pill has accent border. Tappable (future: navigate to area map).
    - Vibe capsules: FlowRow of chips. Top vibes get accent border. Tappable → toggles `expandedVibe`. When expanded, shows:
      - AI insight text (bordered left with accent color)
      - Vertical list of places: emoji icon + name + area/meta + badge (visited/planned/x2) + chevron. Each tappable → opens AiDetailPage.
    - Chat area: LazyColumn of bubbles (AI purple-tinted, user teal-tinted). Suggestion pills below intro bubble. Anchored input bar at bottom with text field + send button. **Error bubbles** (`isError == true`): rendered with red-tinted background (`#3d1f1f`), error icon (Material `Icons.Outlined.ErrorOutline`), localized error text from `profile_chat_error`, and a "Retry" text button (`profile_chat_retry` string, teal accent color). Tap handler calls `viewModel.retryLastMessage()`. **Offline indicator:** when `isOffline == true`, a banner appears above the input bar with `profile_offline` text + `profile_offline_retry` tap action → calls `viewModel.retryConnection()` (NOT `retryLastMessage()`). Send button shows `alpha = 0.3f` when offline.
    - PlatformBackHandler → dismiss profile
    - Close icon (X) top-right
    - Dark theme matching map UI (`#111` bg, white text `#FFFFFF`, secondary text `#B0B0B0`, accent colors: `#4ecdc4` teal, `#a78bfa` purple). **Color contrast (WCAG AA verified):** `#FFFFFF` on `#111111` = 18.3:1 (pass). `#B0B0B0` on `#111111` = 7.3:1 (pass, normal text). `#4ecdc4` on `#111111` = 6.3:1 (pass). `#a78bfa` on `#111111` = 5.1:1 (pass). Error red `#3d1f1f` bg with `#FFFFFF` text = 14.2:1 (pass). Do NOT use any gray darker than `#B0B0B0` for text on `#111` bg.
    - **Accessibility (a11y):**
      - **Content descriptions (all interactive + meaningful elements):**
        - Close X button: `contentDescription = stringResource(profile_close)` → "Close profile"
        - Avatar emoji: `semantics { contentDescription = null }` (decorative, not announced)
        - Each geo pill: `contentDescription = "$areaName, $poiCount places"` (+ ", home area" if `isHome`)
        - Each vibe capsule: `semantics { contentDescription = "$vibeName, $poiCount places"; stateDescription = if (expanded) "expanded" else "collapsed"; role = Role.Button }`. On expand, announce via `liveRegion = LiveRegion.Polite` on the expanded content container so TalkBack reads the AI insight.
        - Each suggestion pill: `contentDescription = pillText` (the pill text itself is sufficient)
        - Send button: `contentDescription = stringResource(profile_send)` → "Send message"
        - Error retry button: `contentDescription = stringResource(profile_chat_retry)` → "Retry sending message"
        - Offline banner: `semantics { contentDescription = stringResource(profile_offline) + ", " + stringResource(profile_offline_retry); role = Role.Button; liveRegion = LiveRegion.Assertive }`
        - Refresh pill: `contentDescription = stringResource(profile_refresh_available)` → "Profile updated, tap to refresh"
        - Each place row in vibe expansion: `contentDescription = "$name, $areaName"` + badge text if present
      - **Focus management & live regions:**
        - Shimmer placeholder container: `semantics { contentDescription = stringResource(profile_loading); liveRegion = LiveRegion.Polite }`. When `isLoading` transitions from true → false, the identity strip gets `liveRegion = LiveRegion.Polite` so TalkBack announces the loaded content.
        - New chat bubbles: only the **most recently added AI bubble** gets `liveRegion = LiveRegion.Polite`, and only after `isStreaming` transitions from `true` to `false` (use a `LaunchedEffect(isStreaming)` that sets `announceLatestBubble = true` on the transition). All other bubbles have `liveRegion = LiveRegion.None`. This prevents re-firing on older bubbles and avoids announcing partial streaming tokens. User bubbles do NOT need live region (the user knows what they typed).
        - Error bubble appearance: `liveRegion = LiveRegion.Assertive` so TalkBack interrupts to announce the error.
        - Offline banner appearance: `liveRegion = LiveRegion.Assertive`.
      - **Chat bubble semantic grouping:**
        - Each bubble is wrapped in `Modifier.semantics(mergeDescendants = true) {}` (no `role` — omit entirely to avoid Role.Image/Role.Button misinterpretation) with a custom `roleDescription`: `"Your message"` for user bubbles, `"AI response"` for AI bubbles, `"Error"` for error bubbles. TalkBack reads: "AI response: Hey Night Foodie Explorer..." instead of raw text.
      - **Chat input field:** `TextField` with `label` parameter set to `stringResource(profile_chat_placeholder)` (Compose uses label for a11y automatically). If using `placeholder` instead, add explicit `Modifier.semantics { contentDescription = stringResource(profile_chat_placeholder) }`.
      - **Empty state (AC 10):** When 0 saved POIs, the empty state container has `semantics { contentDescription = stringResource(profile_empty_state); liveRegion = LiveRegion.Polite }` so TalkBack announces why the profile is empty when it opens. The close button remains accessible.
      - **Touch targets:** All interactive elements (geo pills, vibe capsules, suggestion pills, close X, send button, retry button, place list rows, refresh pill) must use `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)` or `minimumInteractiveComponentSize()` to meet Android 48dp minimum touch target guidelines.
  - Notes: Reference prototype-visit-journey-v4.html for exact layout. Follow AiDetailPage composable structure for overlay pattern.

- [ ] Task 12: Add profile entry point in MapScreen
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Action:
    - Add `var showProfile by remember { mutableStateOf(false) }` state
    - Add profile icon button in top bar area (near settings gear / TopContextBar). Use a person/avatar icon from Material Icons. `contentDescription = stringResource(profile_open)` → "Open profile". Minimum touch target 48dp.
    - When `showProfile == true`, render `ProfileScreen` as full-screen overlay (same z-order as AiDetailPage). Hide map UI elements (ticker, vibe rail, carousel, search bar, my-location) when profile is open.
    - Add `PlatformBackHandler(enabled = showProfile) { showProfile = false }` with correct priority ordering relative to other handlers.
    - Pass `onDismiss = { showProfile = false }` and `onOpenDetail = { poi, areaName -> showProfile = false; viewModel.selectPoi(poi) }` to ProfileScreen.
    - **Transition coordination (finding #9):** When a place is tapped in the profile, both `showProfile = false` and `selectedPoi = poi` must update atomically in a single recomposition frame to avoid a map flash. Implementation: both are `remember` states in `MapScreen`. The `onOpenDetail` callback sets `showProfile = false` first, then `selectedPoi = poi` in the same lambda body — Compose batches state changes within a single callback into one recomposition. Alternatively, use a sealed class navigation event (`ProfileNavEvent.OpenDetail(poi)`) processed in a `LaunchedEffect` that performs both updates. Either approach ensures no intermediate frame where neither overlay is visible.
  - Notes: Profile and AiDetailPage are mutually exclusive overlays — profile closes when a place is tapped and detail opens. Both are remember-backed states in MapScreen, so Compose handles both updates in one frame.

- [ ] Task 13: Add localized strings
  - File: `composeApp/src/commonMain/composeResources/values/strings.xml` (and `values-pt-rBR/strings.xml`)
  - Action: Add string resources for:
    - `profile_title` / `profile_visits` / `profile_areas` / `profile_vibes` — stats labels
    - `profile_loading` — shimmer placeholder text
    - `profile_chat_placeholder` — "Ask about your style..."
    - `profile_pill_blindspot` / `profile_pill_try_next` / `profile_pill_why_name` — suggestion pills
    - `profile_empty_state` — shown when user has no saved places
    - `profile_error` — shown when Gemini identity call fails
    - `profile_chat_greeting` — initial greeting template: `"Hey %1$s! With %2$d visits across %3$d areas and %4$d vibes, I can see some interesting patterns — ask me anything."` Args: `(1) explorerName, (2) totalVisits, (3) totalAreas, (4) totalVibes`. Add `<!-- args: explorerName, totalVisits, totalAreas, totalVibes -->` XML comment above.
    - `profile_chat_greeting_fallback` — fallback when identity unavailable: `"I've been looking at your saved places — want to explore what they say about you?"`
    - `profile_offline` — "You're offline"
    - `profile_offline_retry` — "Tap to retry"
    - `profile_refresh_available` — "Profile updated — tap to refresh"
    - `profile_chat_error` — "Something went wrong"
    - `profile_chat_retry` — "Retry"
    - `profile_open` — "Open profile" (a11y: profile icon button contentDescription)
    - `profile_close` — "Close profile" (a11y: close X button contentDescription)
    - `profile_send` — "Send message" (a11y: send button contentDescription)
    - `profile_geo_pill_cd` — "%1$s, %2$d places" (a11y: geo pill contentDescription, args: areaName, poiCount)
    - `profile_geo_pill_home_cd` — "%1$s, %2$d places, home area" (a11y: home geo pill contentDescription)
    - `profile_bubble_user_role` — "Your message" (a11y: chat bubble roleDescription)
    - `profile_bubble_ai_role` — "AI response" (a11y: chat bubble roleDescription)
    - `profile_bubble_error_role` — "Error" (a11y: error bubble roleDescription)
  - Notes: Follow localisation checklist. pt-BR translations **must be provided by a native speaker or verified human translator** — do not auto-translate. The greeting template (`profile_chat_greeting`) must preserve the exact same arg count and order (`%1$s, %2$d, %3$d, %4$d`). Document arg semantics in XML comments for both languages. Add pt-BR translations for all strings listed above in `values-pt-rBR/strings.xml`.

### Acceptance Criteria

- [ ] AC 1: Given a user with 3+ saved POIs across 2+ areas, when they tap the profile icon on the map, then a full-screen profile page opens showing an AI-generated explorer name, tagline, avatar emoji, and stats (total visits, areas, vibes) above the fold.

- [ ] AC 2: Given the profile page is open, when Gemini identity is loading, then a shimmer placeholder is shown for the identity strip. When loaded, shimmer is replaced with actual content.

- [ ] AC 3: Given a user with saved POIs in multiple areas, when the profile page opens, then a geographic footprint row shows area pills with country flag emojis, area names, and POI counts. The area with the most POIs is marked as "home" with an accent border.

- [ ] AC 4: Given the profile page is open, when the user taps a vibe capsule, then it expands inline showing an AI-generated insight about that vibe and a list of places belonging to that vibe. Tapping the same capsule collapses it. Only one capsule can be expanded at a time.

- [ ] AC 5: Given an expanded vibe capsule showing places, when the user taps a place, then the profile closes and AiDetailPage opens for that POI.

- [ ] AC 6: Given the profile chat area, when the profile loads, then an initial AI bubble appears with a personalized greeting referencing the user's visit patterns. Three suggestion pills appear below it.

- [ ] AC 7: Given the profile chat, when the user taps a suggestion pill or types a message, then the AI responds via streaming (tokens appear progressively). Suggestion pills hide after first interaction.

- [ ] AC 8: Given the profile page is open, when the user presses the Android back button, then the profile page dismisses and the map is shown.

- [ ] AC 9: Given the profile page is open, when the user taps the X close button, then the profile page dismisses.

- [ ] AC 10: Given a user with 0 saved POIs, when they tap the profile icon, then an empty state is shown with a message encouraging exploration (no Gemini call made).

- [ ] AC 11: Given a Gemini identity call failure, when the profile opens, then fallback content is shown — stats are computed client-side (from SavedPoi data), explorer name shows a generic fallback, and an error indicator appears with retry option.

- [ ] AC 12: Given the profile page is open, when map UI elements are checked, then ticker, vibe rail, search bar, carousel, my-location button, and saves pill are all hidden.

- [ ] AC 13: Given a chat message is streaming (`isStreaming == true`), when the user taps the send button or a suggestion pill, then nothing happens (no-op). The send button and suggestion pills are visually disabled (`alpha = 0.5f`). The input field remains editable.

- [ ] AC 14: Given a chat message stream fails mid-response, when the error occurs, then an error bubble appears in the chat with a localized error message and a "Retry" action button. Tapping retry resends the last user message.

- [ ] AC 15: Given a chat streaming call fails with a network exception (IOException, UnresolvedAddressException), when the error occurs, then `isOffline` is set to `true`, the input area shows an "Offline" banner with a "Tap to retry" action, and the send button is disabled (`alpha = 0.3f`). When the user taps "Tap to retry", the VM calls `retryConnection()` which: (a) clears `isOffline` optimistically, (b) re-attempts the last failed send if one exists, otherwise just re-enables the send button. If the re-attempt fails with another network exception, `isOffline` is re-set. This is reactive detection — no background connectivity observer. Distinct from error bubble retry (`retryLastMessage()`), which handles mid-stream content failures, not network loss.

- [ ] AC 16: Given a user with a previously generated profile identity, when they open the profile again, then the cached identity is shown instantly (no shimmer). If saves have changed since the last generation, a background refresh runs silently. When the refresh completes, a "Profile updated — tap to refresh" pill appears at the top of the identity strip (no automatic re-render). Tapping it applies the new identity with a crossfade animation (300ms `AnimatedContent` transition on the identity strip; geo footprint and vibe groups update in place without animation). No shimmer reappears during background refresh.

## Additional Context

### Dependencies

- Feature #52 (Visit Feature) — provides visit tracking data that the profile reads
- Parallel development: this spec defines the data shape it expects; #52 defines how data is written
- Graceful degradation: if #52 is not yet merged, profile works with SavedPoi only (visitCount defaults to 0, all states are "saved"). The profile is useful even without visit tracking — it reflects saves, vibes, and geography.

#### Integration Contract with #52 (Visit Feature)

Both features agree on these exact fields added to `SavedPoi`:

| Field | Type | Default | Owner |
|-------|------|---------|-------|
| `visitCount` | `Int` | `0` | #52 writes, profile reads |
| `visitedAt` | `Long?` | `null` | #52 writes, profile reads |
| `visitState` | `String` | `"saved"` | #52 writes, profile reads. Enum values: `"saved"`, `"visited"`, `"return"` |

**Compile-time strategy:** Define these fields on `SavedPoi` with their defaults NOW as part of this feature's first task. This allows the profile to compile and run independently of #52. When #52 merges, it populates these fields with real data — no profile code changes needed. The defaults ensure the profile degrades gracefully: `visitCount = 0` means stats show save-only data, `visitState = "saved"` means all badges show "saved", `visitedAt = null` means no recency-based sorting for visits.

If #52 changes the field names or types, compilation will fail in both features immediately, surfacing the contract break at build time.

### Testing Strategy

Unit tests (commonTest):
- `ProfileViewModelTest.kt` — test init flow (observeAll → compute geo + vibes → call Gemini → state updates), toggleVibe, sendMessage streaming, suggestion pill tap, empty state, error fallback, retry, initial greeting bubble generation, conversation history sliding window (verify only last 20 messages sent), sendMessage no-op while streaming, retryLastMessage, identityRefreshAvailable flow
- `ProfileIdentityCacheRepositoryTest.kt` — test cache hit (hash matches → returns identity), cache miss (no row → returns null), hash mismatch (row exists but hash differs → returns identity but signals stale), upsert overwrites existing row, computeInputHash determinism (same POIs in different order → same hash), computeInputHash changes when POI added/removed
- `ProfilePromptTest.kt` — test `buildProfileIdentityPrompt` output contains expected sections (save list, taste profile, JSON schema), test `buildProfileChatSystemPrompt` includes user history, test POI truncation to 50 (verify highest visitCount POIs are included)
- `GeminiResponseParserTest.kt` — add tests for `parseProfileIdentityResponse` (valid JSON, malformed JSON, missing fields, missing countryCode → empty string)
- Update `FakeAreaIntelligenceProvider` with configurable profile methods

Manual testing:
- Open profile with 0, 1, 5, 10+ saved POIs — verify empty state, minimal profile, full profile
- Verify geo footprint accuracy (correct area grouping, flag emoji)
- Verify vibe expand/collapse animation
- Tap place in vibe expansion → verify AiDetailPage opens
- Chat: tap suggestion pill → verify streaming response
- Chat: type custom question → verify streaming response
- Android back button → verify dismissal
- iOS: verify profile opens/closes (back handler is no-op, X button works)
- Kill network → open profile → verify error state + retry
- TalkBack (Android): navigate profile end-to-end — verify all interactive elements have meaningful announcements, vibe capsules announce expanded/collapsed state, new chat bubbles are announced, error/offline states are announced assertively
- VoiceOver (iOS): verify same navigation flow works, all contentDescriptions are read
- Touch target audit: verify all tappable elements meet 48dp minimum

### Notes

- Country flag resolution: `countryCode` (ISO 3166-1 alpha-2) is included in the Gemini identity response `geoFootprint` array (areaName + countryCode only — no poiCount from Gemini). Client maps `countryCode` → flag emoji via `countryCode.uppercase().map { c -> String(Character.toChars(0x1F1E6 + (c - 'A'))) }.joinToString("")`. `poiCount` is always computed client-side from SavedPoi grouping. Fallback if countryCode is missing/invalid: show "🌍" globe emoji. No client-side parsing of area name strings for geo data.
- POI cap: 50 POIs maximum in identity prompt. Math: ~150 tokens/POI × 50 = 7,500 tokens + ~800 tokens overhead = ~8,300 total input tokens (Gemini 1.5 Flash supports 1M context). Truncation: top 50 by `visitCount` descending, ties broken by most recent `savedAt`.
- Profile identity caching: SQLDelight `profile_identity_cache` table (Task 1b). Cache key = SHA-256 hash of sorted POI IDs + count. Show cached instantly, background refresh if stale.
- Known limitation: GeoArea areaName matching between Gemini response and client-side SavedPoi grouping relies on case-insensitive trim() equality after prompting Gemini to return names verbatim. Gemini may still normalize diacritics ("São Paulo" → "Sao Paulo"), reorder components, or translate in some cases. Unmatched areas gracefully fall back to "🌍" globe emoji per-area. If match rates are low in practice, a future improvement could use Levenshtein distance or extract countryCode from lat/lng via reverse geocoding instead.
- Known limitation: Geo pill tap currently a no-op (future: navigate to that area on the map).
- Known limitation: Profile chat does not include POI cards inline (unlike area chat). Text-only MVP.
- Known limitation: RTL layout (Arabic, Hebrew) is not supported. The horizontal scroll Row for geo pills, FlowRow for vibe chips, and chat layout (user bubbles right, AI left) would need RTL mirroring. Not a blocker for MVP (en + pt-BR are both LTR). If RTL languages are added, audit all Row/FlowRow composables for `LayoutDirection` handling.
- Package path: Verified `com.harazone` matches `build.gradle.kts` namespace/applicationId and `composeApp/src/commonMain/kotlin/com/harazone/` directory structure. All `files_to_modify` paths are correct.
- Brainstorm decisions: `_bmad-output/brainstorming/brainstorming-session-2026-03-15-002.md`
- Prototype v4: `_bmad-output/brainstorming/prototype-visit-journey-v4.html`
