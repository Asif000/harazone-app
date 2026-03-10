---
title: 'Prompt v3 — 8-Layer Composable Chat Architecture'
slug: 'prompt-v3-composable-chat'
created: '2026-03-09'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'SQLDelight', 'Gemini API', 'Compose Multiplatform']
files_to_modify: [
  'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt',
  'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt',
  'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatOverlay.kt',
  'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatUiState.kt',
  'composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/SavedPoi.kt',
  'composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/saved_pois.sq',
  'composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/SavedPoiRepositoryImpl.kt',
  'composeApp/src/commonMain/kotlin/com/areadiscovery/debug/DevSeeder.kt',
  'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt',
  'composeApp/src/commonTest/kotlin/com/areadiscovery/ui/map/ChatViewModelTest.kt',
  'NEW: composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/ChatIntent.kt',
  'NEW: composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/EngagementLevel.kt',
  'NEW: composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/TasteProfile.kt',
  'NEW: composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/TasteProfileBuilder.kt',
  'NEW: composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/6.sqm',
  'NEW: composeApp/src/commonTest/kotlin/com/areadiscovery/domain/model/TasteProfileBuilderTest.kt',
  'NEW: composeApp/src/commonTest/kotlin/com/areadiscovery/domain/model/EngagementLevelTest.kt',
]
code_patterns: [
  'Pure functions for each layer — no side effects, all inputs as params',
  'EmptyState composable in ChatOverlay.kt is the intent pill injection point',
  'System context deferred — NOT added in openChat(), added in tapIntentPill() before first send',
  'SavedPoiRepositoryImpl.save() keeps clock.nowMs() for production; saveWithTimestamp() added for DevSeeder only',
  'ChatIntent + EngagementLevel + TasteProfile in domain.model — visible to both ui.map and data.remote',
  'tasteProfileBlock suppressed entirely when EngagementLevel == DORMANT',
  'areaName normalized (lowercase + trim) before filtering saves in saveContextBlock',
]
test_patterns: [
  'commonTest (KMP): assertTrue(prompt.contains(...)) for all prompt layer tests',
  'TasteProfileBuilderTest in commonTest — pure function, no mocks needed',
  'EngagementLevelTest in commonTest — boundary cases for all 5 levels',
  'GeminiPromptBuilderTest: one test per new layer + update old signature tests',
  'ChatViewModelTest: move systemContext assertions to after tapIntentPill()',
  'Existing GeminiPromptBuilderTest buildAreaPortraitPrompt tests must not break',
]
---

# Tech-Spec: Prompt v3 — 8-Layer Composable Chat Architecture

**Created:** 2026-03-09

## Overview

### Problem Statement

`buildChatSystemContext` is a single flat string (~180 tokens) with no intent awareness, engagement adaptation, or taste profile. Every user gets the same generic "be a local guide" prompt regardless of how many places they have saved, how long they have been away, or what they actually want right now (food vs outdoor vs surprise). The result is generic, underwhelming AI responses that do not reflect user taste or current intent.

### Solution

Refactor `buildChatSystemContext` into 8 composable pure-function layers, add 5 intent buckets (Tonight / Discover / Hungry / Outside / Surprise Me) selected via UI pills that replace the current vibe-based starter chips, compute engagement level (Fresh / Light / Regular / Power / Dormant) from save history, compute `TasteProfile` from all saves as a pure function, and inject notes from saved POIs. Each layer is independently testable and token-budgeted. Total prompt: 430-550 tokens depending on user type.

### Scope

**In Scope:**
- `GeminiPromptBuilder.kt`: full refactor of `buildChatSystemContext` into 8 composable layers (pure functions)
- `TasteProfileBuilder.kt`: new pure function in `domain.model`
- `ChatIntent` enum: TONIGHT, DISCOVER, HUNGRY, OUTSIDE, SURPRISE
- `EngagementLevel` enum: FRESH, LIGHT, REGULAR, POWER, DORMANT (with companion `from()` factory)
- `SavedPoi` domain model: add nullable `userNote: String?`
- `saved_pois.sq` + DB migration `6.sqm`: add nullable `user_note TEXT` column
- `SavedPoiRepositoryImpl.kt`: update mapper + add `saveWithTimestamp()` for DORMANT seeding (production `save()` keeps `clock.nowMs()`)
- `ChatViewModel.kt`: defer system context build to `tapIntentPill()`, compute EngagementLevel, intent-aware follow-up chips, guard against double-tap
- `ChatUiState.kt`: add `intentPills: List<ChatIntent>`
- `ChatOverlay.kt`: replace `EmptyState` chips with intent pills
- `DevSeeder.kt`: add DORMANT persona (uses `saveWithTimestamp()`) + stub `userNote` on Power persona saves
- `GeminiPromptBuilderTest.kt`: update old signature tests + add per-layer tests
- `ChatViewModelTest.kt`: move system-context assertions to after `tapIntentPill()`
- `TasteProfileBuilderTest.kt`: new unit test file
- `EngagementLevelTest.kt`: new unit test file for boundary cases

**Out of Scope:**
- `buildAreaPortraitPrompt` — untouched (separate structured data call; HIGH backlog for latency)
- Note contextual relevance filtering by time-of-day / intent (v2)

---

## Context for Development

### Codebase Patterns

- KMP: all shared logic in `commonMain`; `androidMain` / `iosMain` for platform specifics
- `GeminiPromptBuilder` is `internal class` in `data.remote` — keep internal
- `ChatViewModel.openChat` currently adds system context as first `MessageRole.USER` entry in `conversationHistory` (Gemini requires first turn to be user role). New flow: defer this until `tapIntentPill()` so intent is known at build time
- `ChatViewModel.tapChip(chip)` sets `inputText = chip` then calls `sendMessage()` — `tapIntentPill` follows same pattern but also prepends system context to history first
- `EmptyState` composable (line 248, `ChatOverlay.kt`) is the exact injection point — currently takes `chips: List<String>` rendered as `SuggestionChip` in a `FlowRow`
- New domain types in `domain.model` to avoid circular imports between `data.remote` and `ui.map`
- SQLDelight migration: new nullable column in `6.sqm`; MUST also bump `schemaVersion` in `composeApp/build.gradle.kts` from 5 to 6 or the migration never runs on existing installs
- `SavedPoiRepositoryImpl.save()` keeps using `clock.nowMs()` for production saves — a separate `saveWithTimestamp(poi, timestampMs)` method is added exclusively for DevSeeder backdating
- `DORMANT` EngagementLevel: tasteProfileBlock is suppressed entirely (DORMANT = re-engagement framing only, no deep preference context)
- `saveContextBlock` normalizes `areaName` to `lowercase().trim()` before string-equality filtering to handle geocoder casing variance
- `ChatViewModelTest` currently tests system context via `systemContextForTest` after `openChat()` — these 3 tests must move assertions to after `tapIntentPill()` is called

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `data/remote/GeminiPromptBuilder.kt` | File being refactored — current flat `buildChatSystemContext` |
| `ui/map/ChatViewModel.kt` | `openChat` + `tapChip` patterns to follow for `tapIntentPill` |
| `ui/map/ChatOverlay.kt` | `EmptyState` composable at line 248 — intent pill injection point |
| `ui/map/ChatUiState.kt` | Add `intentPills` field |
| `domain/model/SavedPoi.kt` | Add `userNote: String?` |
| `data/local/saved_pois.sq` | Add `user_note` to `insertOrReplace` query |
| `data/repository/SavedPoiRepositoryImpl.kt` | Update mapper + add `saveWithTimestamp()` |
| `debug/DevSeeder.kt` | DORMANT persona + userNote stubs |
| `data/remote/GeminiPromptBuilderTest.kt` | Existing tests — must not break; update old signature |
| `ui/map/ChatViewModelTest.kt` | 3 system-context tests need moving to after `tapIntentPill()` |
| `composeApp/build.gradle.kts` | Bump `schemaVersion` 5 → 6 alongside Task 6 |
| `_bmad-output/brainstorming/brainstorming-session-2026-03-09-001.md` | Party Mode Session 3 — authoritative prompt copy (git-untracked; ensure available in implementation context) |

### Technical Decisions

- `ChatIntent`, `EngagementLevel`, `TasteProfile`, `TasteProfileBuilder` all in `domain.model` package
- `EngagementLevel.from(saves, nowMs)` companion factory: DORMANT if `saves >= 1 && max(savedAt) < nowMs - 14.days`; FRESH if `saves == 0`; LIGHT 1-5; REGULAR 6-29; POWER 30+. DORMANT takes priority over POWER for high-save users who have been away.
- `TasteProfileBuilder.build(saves, nowMs)`: `nowMs` is a REQUIRED parameter (no default) to prevent production callers forgetting it and getting all-saves-as-recent. Call sites pass `clock.nowMs()`. Groups by `type` — strongAffinities = types with 3+ saves; emergingInterests = types with 1-2 saves in last 30 days AND not already in strongAffinities; notableAbsences = COMMON_TYPES with 0 saves; diningStyle = "food lover" if food-type saves >= 2 else null
- `COMMON_TYPES` must match actual `SavedPoi.type` values in the codebase: `listOf("restaurant", "bakery", "food_alley", "park", "museum", "gallery", "bar", "nightlife", "historic", "street_art")`
- Layer 5b (`tasteProfileBlock`) is suppressed (returns "") when `EngagementLevel == DORMANT` OR when `profile.totalSaves < 3`
- Layer 5a save injection: normalize areaName to `lowercase().trim()` for equality check; sort by `savedAt` desc; cap 8; include `userNote` if non-null as `Name: "note"`
- SURPRISE intent uses `notableAbsences` from TasteProfile as positive recommendation signal (inverse filter)
- Intent pills are tapped to send a pre-formed opening message — reuses existing `sendMessage()` flow. `tapIntentPill` prepends system context then sets `inputText` and calls `sendMessage()`
- Double-tap guard: `isIntentSelected: Boolean` private var in `ChatViewModel`; `tapIntentPill` returns immediately if `isIntentSelected == true`; reset to `false` in `openChat()`
- Same-area preserve path: when `openChat()` returns early (same area), clear `intentPills` if `conversationHistory` is non-empty (a pill was already tapped); preserve `intentPills` if history is empty
- `pendingFramingHint: String?` — canonical name for the private var in `ChatViewModel` that stores framingHint between `openChat()` and `tapIntentPill()`
- Intent-aware follow-up chips (FRESH only): TONIGHT → "Solo or with company?"; DISCOVER → "On foot or do you have wheels?"; HUNGRY → "Any food preferences?"; OUTSIDE → "Leisurely stroll or proper adventure?"; SURPRISE → no follow-up. LIGHT+ → existing keyword-matching logic unchanged

---

## Implementation Plan

### Tasks

Tasks are ordered by dependency (lowest level first).

- [ ] Task 1: Create `ChatIntent` enum
  - File: `NEW composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/ChatIntent.kt`
  - Action: Create enum with 5 values. Each value carries `displayLabel: String` (shown on pill) and `openingMessage: String` (sent as first chat message when pill tapped):
    - `TONIGHT("Tonight", "What should I do tonight?")`
    - `DISCOVER("Discover", "What makes this place special?")`
    - `HUNGRY("Hungry", "Where should I eat right now?")`
    - `OUTSIDE("Outside", "Get me outside.")`
    - `SURPRISE("Surprise me", "Surprise me.")`

- [ ] Task 2: Create `EngagementLevel` enum
  - File: `NEW composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/EngagementLevel.kt`
  - Action: Create enum FRESH, LIGHT, REGULAR, POWER, DORMANT. Add companion object with:
    ```kotlin
    fun from(saves: List<SavedPoi>, nowMs: Long): EngagementLevel {
        if (saves.isEmpty()) return FRESH
        val dayMs = 24 * 60 * 60 * 1000L
        val mostRecentSave = saves.maxOf { it.savedAt }
        if (nowMs - mostRecentSave > 14 * dayMs) return DORMANT
        return when (saves.size) {
            in 1..5 -> LIGHT
            in 6..29 -> REGULAR
            else -> POWER
        }
    }
    ```
  - Notes: DORMANT is checked before count thresholds — a user with 50 saves who has been away 15 days is DORMANT, not POWER.

- [ ] Task 3: Create `TasteProfile` data class
  - File: `NEW composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/TasteProfile.kt`
  - Action:
    ```kotlin
    data class TasteProfile(
        val strongAffinities: List<String>,   // POI types with 3+ saves
        val emergingInterests: List<String>,  // POI types with 1-2 saves in last 30 days (not in strongAffinities)
        val notableAbsences: List<String>,    // COMMON_TYPES with 0 saves
        val diningStyle: String?,             // "food lover" if food-type saves >= 2, else null
        val totalSaves: Int,
    )
    ```
  - Notes: `TasteProfile()` with all-empty lists + `totalSaves=0` is the FRESH user state

- [ ] Task 4: Create `TasteProfileBuilder` object
  - File: `NEW composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/TasteProfileBuilder.kt`
  - Action: Create `object TasteProfileBuilder` with a single pure function. `nowMs` is REQUIRED — no default value:
    ```kotlin
    fun build(saves: List<SavedPoi>, nowMs: Long): TasteProfile
    ```
  - Logic:
    - `COMMON_TYPES = listOf("restaurant", "bakery", "food_alley", "park", "museum", "gallery", "bar", "nightlife", "historic", "street_art")`
    - `FOOD_TYPES = listOf("restaurant", "bakery", "food_alley")`
    - `countByType = saves.groupBy { it.type }.mapValues { it.value.size }`
    - `strongAffinities` = types where count >= 3, sorted by count desc
    - `thirtyDayMs = 30L * 24 * 60 * 60 * 1000`
    - `recentSaves` = saves where `savedAt > nowMs - thirtyDayMs`
    - `recentCountByType = recentSaves.groupBy { it.type }.mapValues { it.value.size }`
    - `emergingInterests` = types where recentCount in 1..2 AND type NOT in strongAffinities
    - `notableAbsences` = COMMON_TYPES where `countByType[type] == null`
    - `diningStyle` = "food lover" if `saves.count { it.type in FOOD_TYPES } >= 2` else null
    - `totalSaves` = saves.size

- [ ] Task 5: Add `userNote` to `SavedPoi` domain model
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/SavedPoi.kt`
  - Action: Add `val userNote: String? = null` as the last field. Default null preserves all existing construction sites without change.

- [ ] Task 6: Add DB migration `6.sqm` and bump schemaVersion
  - File 1: `NEW composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/migrations/6.sqm`
    ```sql
    ALTER TABLE saved_pois ADD COLUMN user_note TEXT;
    ```
  - File 2: `composeApp/build.gradle.kts` — find the SQLDelight `schemaVersion` setting and change it from `5` to `6`. Without this bump, the migration never runs on existing installs and column writes crash at runtime.
  - Notes: Nullable column — existing rows get `NULL`. No data migration needed.

- [ ] Task 7: Update `saved_pois.sq` insert query
  - File: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/saved_pois.sq`
  - Action: Update only `insertOrReplace` — add `user_note` to both column list and VALUES. `observeAll` and `observeSavedIds` use `SELECT *` so they auto-include the new column with no changes:
    ```sql
    insertOrReplace:
    INSERT OR REPLACE INTO saved_pois(poi_id, name, type, area_name, lat, lng, why_special, saved_at, user_note)
    VALUES (:poi_id, :name, :type, :area_name, :lat, :lng, :why_special, :saved_at, :user_note);
    ```

- [ ] Task 8: Update `SavedPoiRepositoryImpl`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/SavedPoiRepositoryImpl.kt`
  - Action 1 — Update `observeAll` mapper: add `userNote = it.user_note` to the `SavedPoi(...)` constructor call
  - Action 2 — Keep `save()` using `clock.nowMs()` for `saved_at` — DO NOT change this. Production saves must use the current timestamp.
  - Action 3 — Add `user_note = poi.userNote` to the `insertOrReplace()` call in `save()`
  - Action 4 — Add a new `saveWithTimestamp(poi: SavedPoi, timestampMs: Long)` method for DevSeeder use only. It calls `insertOrReplace` using `timestampMs` for `saved_at` instead of `clock.nowMs()`. Mark with `@VisibleForTesting` or an `// DevSeeder only` comment.
  - Action 5 — Add `saveWithTimestamp` to the `SavedPoiRepository` interface in `domain/repository/SavedPoiRepository.kt`

- [ ] Task 9: Refactor `GeminiPromptBuilder` — 8-layer architecture
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action: Replace the existing `buildChatSystemContext(areaName, poiNames, vibeName, savedPoiNames, framingHint)` with a new public function and private layer functions.

  New public signature:
  ```kotlin
  fun buildChatSystemContext(
      areaName: String,
      pois: List<POI>,
      intent: ChatIntent,
      engagementLevel: EngagementLevel,
      saves: List<SavedPoi>,
      tasteProfile: TasteProfile,
      poiCount: Int,
      framingHint: String? = null,
  ): String
  ```

  Public function assembles layers in order — only `saveContextBlock`, `tasteProfileBlock`, and `framingBlock` can return blank strings:
  ```kotlin
  return listOf(
      personaBlock(areaName),
      areaContextBlock(pois),
      intentBlock(intent),
      engagementBlock(engagementLevel),
      saveContextBlock(saves, areaName),
      tasteProfileBlock(tasteProfile, intent, engagementLevel),
      confidenceBlock(poiCount),
      contextShiftBlock(),
      outputFormatBlock(),
      framingBlock(framingHint),
  ).filter { it.isNotBlank() }.joinToString("\n\n")
  ```

  Private layer functions (use prompt text verbatim from `_bmad-output/brainstorming/brainstorming-session-2026-03-09-001.md` Party Mode Session 3 — the full drafts are there):

  **Layer 1 — `private fun personaBlock(areaName: String): String`** (~45 tokens)
  ```
  You are a passionate local who has lived in "[areaName]" for 20 years. You love showing visitors things they would NEVER find on Google Maps. Your mission: surface the genuinely unique, memorable, and local.
  UNIQUENESS RULE: Only include places with a genuine story or character. A place is not interesting because it exists — it is interesting because of what it means to this area.
  FOOD GATE: Food and drink places are welcome if unique, with a story, or offering something you cannot find anywhere else. No more than 30% food POIs unless the area is genuinely food-destination-famous.
  WHY SPECIAL REQUIRED: Every place you mention must have a compelling reason. "Popular" or "nice" are not acceptable.
  NO CHAINS: Never recommend chain brands, international franchises, or tourist traps.
  ```

  **Layer 2 — `private fun areaContextBlock(pois: List<POI>): String`** (~40 tokens)
  ```
  AREA CONTEXT: You are guiding someone around [areaName]. Key places in this area include: [pois.take(5).joinToString(", ") { it.name }].
  Local dining culture: adapt to what "good food" means HERE — street carts, markets, fine dining, whatever fits this place. QUALITY MEANS: memorable, worth the trip, has a story. NOT: has a website, has 4+ Google stars, looks good on Instagram.
  ```

  **Layer 3 — `private fun intentBlock(intent: ChatIntent): String`** (~70-90 tokens). Copy verbatim from brainstorm. Full drafts:

  TONIGHT:
  ```
  YOUR FRIEND ASKED: "What should I do tonight?"
  YOUR MISSION: Recommend 3-5 places that create a MEMORABLE EVENING. Think in arcs — not isolated stops. Where to start, where to end, what order makes the night flow. Atmosphere matters more than ratings.
  QUALITY MEANS: Worth telling a story about tomorrow. A plastic-stool street cart can beat a Michelin restaurant. No chains. No tourist traps. No places you'd personally skip.
  VOICE: Warm, confident, like texting a close friend. Say "trust me on this" not "I recommend."
  ```

  DISCOVER:
  ```
  YOUR FRIEND ASKED: "What makes this place special?"
  YOUR MISSION: Reveal 3-5 places that tell the STORY of this area. Not the top-10 list — things a curious person would regret missing. Hidden history, local legends, architectural details, cultural undercurrents.
  QUALITY MEANS: "I had no idea that existed." Skip anything on the first page of Google. Depth over breadth.
  VOICE: Storyteller energy. The friend who makes a 15-minute walk take an hour because you keep stopping to point things out.
  ```

  HUNGRY:
  ```
  YOUR FRIEND ASKED: "Where should I eat right now?"
  YOUR MISSION: Recommend 3-5 places OPEN NOW or opening soon. What locals actually eat here, not tourist traps. A legendary street cart beats a mediocre sit-down.
  QUALITY MEANS: You'd take your own family here. Adapt to local dining culture — street food, markets, cafes, whatever THIS place does best. Never default to Western restaurant assumptions.
  VOICE: Decisive. "Go here, order this, thank me later." Include what to order if you know.
  ```

  OUTSIDE:
  ```
  YOUR FRIEND ASKED: "Get me outside."
  YOUR MISSION: Recommend 3-5 outdoor experiences — parks, walks, viewpoints, waterfronts, gardens, trails, beaches, plazas. Places where you FEEL the environment.
  QUALITY MEANS: You'd go here on your day off. Not a parking lot with a "scenic overlook" sign. Real atmosphere.
  VOICE: Energizing. "Grab your shoes, I know exactly where to go." Paint the sensory picture — what they'll see, hear, smell.
  ```

  SURPRISE:
  ```
  YOUR FRIEND ASKED: "Surprise me."
  YOUR MISSION: Pick 3-4 places they'd NEVER find on their own. Break the expected pattern. If the area is known for beaches, recommend underground jazz. If known for nightlife, recommend a dawn fish market.
  QUALITY MEANS: "Wait, WHAT? That exists here?" Go weird. Go niche. Go hyperlocal.
  VOICE: Mischievous. "Okay, you're not going to believe this, but..."
  ```

  **Layer 4 — `private fun engagementBlock(level: EngagementLevel): String`** (~55 tokens):

  FRESH: `"ENGAGEMENT: This user is new — they have no saves yet. Ask ONE follow-up question after your response. Be warm and inviting. Briefly explain your reasoning so they understand what to expect. End every first response with: Save any places that catch your eye — I learn your taste from what you keep."`

  LIGHT: `"ENGAGEMENT: This user has started saving places. Reference their taste lightly where relevant — mention patterns you notice. Keep it conversational, not clinical."`

  REGULAR: `"ENGAGEMENT: This user knows what they like. Be confident. Skip unnecessary explanation. Connect recommendations directly to their preferences without over-explaining."`

  POWER: `"ENGAGEMENT: This user is deeply engaged — they have saved many places. Reference their saved places by name when relevant. Anticipate their needs. Make proactive nudges. Be brief — they don't need hand-holding."`

  DORMANT: `"ENGAGEMENT: This user has been away for a while. Lead with what has CHANGED or what is NEW since they were last here. Open with warm welcome-back energy. Do not assume they remember their previous context."`

  **Layer 5a — `private fun saveContextBlock(saves: List<SavedPoi>, areaName: String): String`**
  - Filter: `saves.filter { it.areaName.lowercase().trim() == areaName.lowercase().trim() }.sortedByDescending { it.savedAt }.take(8)`
  - If filtered list is empty, return `""`
  - Format each save as a compact line: `"- [name] ([type])"` + if `userNote != null`: append `": \"[userNote]\""`
  - Wrap: `"SAVED PLACES IN THIS AREA (use these for personalisation):\n[lines]"`

  **Layer 5b — `private fun tasteProfileBlock(profile: TasteProfile, intent: ChatIntent, level: EngagementLevel): String`**
  - Return `""` if: `profile.totalSaves < 3` OR `level == EngagementLevel.DORMANT`
  - For `SURPRISE` intent: `"SURPRISE FILTER: This user has NEVER saved anything in: [notableAbsences]. Deliberately recommend from these categories — break their usual pattern. Do NOT recommend from: [strongAffinities]."`
  - For all other intents: `"TASTE PROFILE: Strong affinities: [strongAffinities]. [if diningStyle != null: diningStyle]. Prioritise recommendations that match these patterns."`

  **Layer 6 — `private fun confidenceBlock(poiCount: Int): String`** (~30 tokens):
  - `poiCount >= 12`: `"HIGH CONFIDENCE: You have strong knowledge of this area. Give specific insider tips — best time to visit, which section to head to, what to order or try. Be precise."`
  - `poiCount in 6..11`: `"MEDIUM CONFIDENCE: Mix specific tips with atmospheric descriptions where you are less certain."`
  - else: `"LOW CONFIDENCE: Lean on atmosphere and general character. Be honest if documentation is limited for this area. HARD RULE: Never fabricate staff names, off-menu items, or insider passwords regardless of confidence level."`

  **Layer 7 — `private fun contextShiftBlock(): String`** (~35 tokens, always non-blank):
  ```
  CONTEXT SHIFTS: If the user changes their mind, switches to a different area, or blends intents mid-conversation, roll with it enthusiastically. Acknowledge the shift and adapt immediately. Your initial framing is a starting point, not a cage.
  ```

  **Layer 8 — `private fun outputFormatBlock(): String`** (~120 tokens, always non-blank):
  Keep existing POI JSON inline-emit rules verbatim from the current `buildChatSystemContext` implementation. Add at the start: `"Answer conversationally, under 150 words of prose per reply. Be specific and practical."`

  **Framing — `private fun framingBlock(framingHint: String?): String`**:
  Return `framingHint ?: ""`

  Notes: `buildAreaPortraitPrompt` and `buildAiSearchPrompt` are untouched.

- [ ] Task 10: Update `ChatUiState`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatUiState.kt`
  - Action: Add `val intentPills: List<ChatIntent> = emptyList()` field to `ChatUiState` data class

- [ ] Task 11: Update `ChatViewModel`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatViewModel.kt`
  - Action 1 — Add private session-state vars:
    ```kotlin
    private var sessionPois: List<POI> = emptyList()
    private var selectedIntent: ChatIntent? = null
    private var currentEngagementLevel: EngagementLevel = EngagementLevel.FRESH
    private var pendingFramingHint: String? = null
    private var isIntentSelected: Boolean = false
    ```
  - Action 2 — Change `openChat()`:
    - Remove the block that builds `systemContext` string and adds it to `conversationHistory`
    - Store `sessionPois = pois`
    - Store `pendingFramingHint = <existing framingHint computation — keep logic unchanged>`
    - Reset `isIntentSelected = false` and `selectedIntent = null`
    - Explicitly preserve `vibeName` in the new `ChatUiState` assignment — do NOT omit it
    - Same-area guard modification: when early-returning for same area, check `if (conversationHistory.isNotEmpty()) { _uiState.value = current.copy(isOpen = true, intentPills = emptyList()) } else { _uiState.value = current.copy(isOpen = true) }` — clears pills only if a pill was already tapped (history non-empty)
    - Replace `followUpChips = computeStarterChips(activeVibe)` with `intentPills = ChatIntent.entries.toList()`
  - Action 3 — Add `fun tapIntentPill(intent: ChatIntent)`:
    ```kotlin
    fun tapIntentPill(intent: ChatIntent) {
        if (isIntentSelected) return   // double-tap guard
        isIntentSelected = true
        selectedIntent = intent
        val saves = latestSavedPois
        currentEngagementLevel = EngagementLevel.from(saves, clock.nowMs())
        val tasteProfile = TasteProfileBuilder.build(saves, clock.nowMs())
        val areaName = _uiState.value.areaName
        val poiCount = sessionPois.size
        val systemContext = promptBuilder.buildChatSystemContext(
            areaName, sessionPois, intent, currentEngagementLevel,
            saves, tasteProfile, poiCount, pendingFramingHint
        )
        conversationHistory.add(
            ChatMessage(id = nextId(), role = MessageRole.USER,
                content = systemContext, timestamp = clock.nowMs(), sources = emptyList())
        )
        _uiState.value = _uiState.value.copy(inputText = intent.openingMessage, intentPills = emptyList())
        sendMessage()
    }
    ```
  - Action 4 — Update `computeFollowUpChips` signature:
    ```kotlin
    private fun computeFollowUpChips(query: String, intent: ChatIntent?, level: EngagementLevel): List<String>
    ```
    - For `level == FRESH`: return intent-specific single chip: TONIGHT → `listOf("Solo or with company?")`, DISCOVER → `listOf("On foot or do you have wheels?")`, HUNGRY → `listOf("Any food preferences?")`, OUTSIDE → `listOf("Leisurely stroll or proper adventure?")`, SURPRISE / null → `emptyList()`
    - For LIGHT+: existing keyword-matching logic unchanged
  - Action 5 — Update `computeFollowUpChips` call site in the `sendMessage` response handler to pass `selectedIntent` and `currentEngagementLevel`
  - Action 6 — Delete `computeStarterChips` function entirely

- [ ] Task 12: Update `ChatOverlay` — intent pills in `EmptyState`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/ChatOverlay.kt`
  - Action 1 — Change `EmptyState` composable signature:
    ```kotlin
    private fun EmptyState(
        areaName: String,
        intentPills: List<ChatIntent>,
        onPillTap: (ChatIntent) -> Unit,
    )
    ```
  - Action 2 — Replace the `FlowRow` chip loop with intent pill chips. Use same `SuggestionChip` style as current chips. Display `pill.displayLabel` with emoji prefix: TONIGHT → "🌙 Tonight", DISCOVER → "🔍 Discover", HUNGRY → "🍜 Hungry", OUTSIDE → "🌳 Outside", SURPRISE → "🎲 Surprise me"
  - Action 3 — Update subtitle text to `"What are you in the mood for?"`
  - Action 4 — Update `EmptyState` call site in `ChatOverlay` to pass `chatState.intentPills` and `{ viewModel.tapIntentPill(it) }`

- [ ] Task 13: Update `DevSeeder`
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/debug/DevSeeder.kt`
  - Action 1 — Add `DORMANT` to the `Persona` enum
  - Action 2 — Add `dormantPersona()` private function: 3 saves with `savedAt` = 20 days ago. Use `repo.saveWithTimestamp(poi, twentyDaysAgoMs)` (from Task 8) instead of `repo.save(poi)`:
    ```kotlin
    val twentyDaysAgoMs = Clock.System.now().toEpochMilliseconds() - 20L * 24 * 60 * 60 * 1000
    ```
    The area name for DORMANT saves can be anything (e.g. "Ginza, Tokyo") — engagement level is computed from timestamp globally, area doesn't matter.
  - Action 3 — Add `DORMANT -> dormantPersona()` to the `seedPersona` when-block. Note: `dormantPersona()` calls `saveWithTimestamp` directly (not `repo.save()`), so it bypasses the `poi()` helper.
  - Action 4 — Update `poi()` helper to accept optional `userNote: String? = null` as last param, pass through to `SavedPoi(..., userNote = userNote)`
  - Action 5 — Add `userNote` stubs to 5 Power persona saves: a tip ("best spot is the rooftop at sunset"), a reminder ("book ahead — fills up fast"), a time note ("only open evenings"), a come-back note ("need to try the weekend market"), a food order note ("order the lamb — ignore the menu")
  - Action 6 — If `DevSeeder` has a `seedDirect()` function that calls `database.saved_poisQueries.insertOrReplace(...)` directly, update that call to include `user_note = null` as the new required parameter (Task 7 adds it to the SQL signature)

- [ ] Task 14: Create `TasteProfileBuilderTest`
  - File: `NEW composeApp/src/commonTest/kotlin/com/areadiscovery/domain/model/TasteProfileBuilderTest.kt`
  - Action: Write unit tests. Use a fixed `nowMs` (e.g. `1_000_000_000_000L`) for all calls:
    - `build_emptyList`: `build(emptyList(), nowMs)` → `totalSaves == 0`, all lists empty, `diningStyle == null`
    - `build_strongAffinity_threeOrMoreSaves`: 3 saves of type "park" → "park" in `strongAffinities`
    - `build_emergingInterest_oneRecentSave`: 1 save of type "museum" within 30 days → "museum" in `emergingInterests`
    - `build_emergingInterest_notDuplicated_inStrongAffinity`: 3 saves of "park" + 1 recent "park" → "park" in `strongAffinities` only, NOT in `emergingInterests`
    - `build_notableAbsence_zeroSaves`: 0 saves of type "restaurant" → "restaurant" in `notableAbsences`
    - `build_diningStyle_twoFoodTypeSaves`: 2 saves with type in FOOD_TYPES → `diningStyle == "food lover"`
    - `build_surprise_notableAbsencesAvailable`: user with only "park" saves → "restaurant" in `notableAbsences` (available for SURPRISE inverse filter)
    - `build_nowMs_required_recentFilter`: save with `savedAt` 40 days before `nowMs` → NOT in `emergingInterests`

- [ ] Task 15: Update `GeminiPromptBuilderTest`
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt`
  - Action 1 — Update the 4 existing `buildChatSystemContext_*` tests to use the new signature. The saves/framingHint behaviour is preserved — only the call signature changes (pass `ChatIntent.DISCOVER`, `EngagementLevel.LIGHT`, a `TasteProfile`, and `poiCount = 5` as sensible defaults for these tests)
  - Action 2 — Update the existing `openChat_moreThanTenSaves_capsAtTen` test: the cap is now 8, not 10. Update test name to `layer5a_saves_capped_at_8` and update assertion accordingly.
  - Action 3 — Add one test per layer:
    - `buildChatSystemContext_layer1_persona`: contains "20 years"
    - `buildChatSystemContext_layer3_tonight_intent`: contains "memorable evening"
    - `buildChatSystemContext_layer3_surprise_intent`: contains "NEVER find on their own" or "Mischievous"
    - `buildChatSystemContext_layer4_fresh_engagement`: contains "Save any places that catch your eye"
    - `buildChatSystemContext_layer4_dormant_engagement`: contains "CHANGED" or "welcome-back" (case-insensitive)
    - `buildChatSystemContext_layer5a_saves_capped_at_8`: 10 saves injected → output contains save #8 name, does NOT contain save #9 or #10 name
    - `buildChatSystemContext_layer5a_userNote_injected`: save with `userNote = "best at sunset"` → output contains `"best at sunset"` (AC 9)
    - `buildChatSystemContext_layer5b_omitted_for_fresh`: FRESH engagement → output does NOT contain "TASTE PROFILE" or "SURPRISE FILTER"
    - `buildChatSystemContext_layer5b_omitted_for_dormant`: DORMANT engagement + 10 saves → output does NOT contain "TASTE PROFILE" or "SURPRISE FILTER"
    - `buildChatSystemContext_layer5b_surprise_uses_absences`: SURPRISE intent + profile with "park" in notableAbsences → output contains "park" and "NEVER saved"
    - `buildChatSystemContext_layer6_high_confidence`: poiCount=12 → contains "insider"
    - `buildChatSystemContext_layer6_low_confidence`: poiCount=3 → contains "LOW CONFIDENCE"
    - `buildChatSystemContext_layer7_context_shift`: always contains "CONTEXT SHIFTS"

- [ ] Task 16: Create `EngagementLevelTest`
  - File: `NEW composeApp/src/commonTest/kotlin/com/areadiscovery/domain/model/EngagementLevelTest.kt`
  - Action: Write unit tests for all boundary cases. Use a fixed `nowMs = 1_000_000_000_000L`:
    - `from_emptySaves`: `from(emptyList(), nowMs)` → `FRESH`
    - `from_oneSave`: 1 save, recent → `LIGHT`
    - `from_fiveSaves`: 5 saves, recent → `LIGHT`
    - `from_sixSaves`: 6 saves, recent → `REGULAR`
    - `from_twentyNineSaves`: 29 saves, recent → `REGULAR`
    - `from_thirtySaves`: 30 saves, recent → `POWER`
    - `from_dormant_14daysExact`: most recent save exactly 14 days ago → NOT DORMANT (boundary: > 14 days is DORMANT, == 14 is still POWER/REGULAR/LIGHT)
    - `from_dormant_15days`: most recent save 15 days ago → `DORMANT`
    - `from_dormant_overridesPower`: 50 saves but most recent 20 days ago → `DORMANT` (not POWER)
    - `from_singleSave_dormant`: 1 save, 20 days ago → `DORMANT` (not FRESH)

### Acceptance Criteria

- [ ] AC 1: Given a FRESH user (0 saves) opens chat, when the chat sheet appears, then 5 intent pills are shown (Tonight / Discover / Hungry / Outside / Surprise me) and no vibe-based starter chips are shown
- [ ] AC 2: Given a user taps the "Tonight" pill, when the first AI response arrives, then the chat history shows the user sent "What should I do tonight?" and the AI response reflects the TONIGHT intent block (evening arc framing)
- [ ] AC 3: Given a FRESH user taps any intent pill (except Surprise me), when the AI responds, then one intent-specific follow-up chip is shown ("Solo or with company?" for Tonight, etc.) and no other follow-up chips
- [ ] AC 4: Given a FRESH user taps the "Surprise me" pill, when the AI responds, then no follow-up chip is shown
- [ ] AC 5: Given a LIGHT user (1-5 saves), when they tap an intent pill, then the AI prompt includes the LIGHT engagement block text ("Reference their taste lightly") and their area saves appear in the system context
- [ ] AC 6: Given a POWER user (30+ saves), when they tap an intent pill, then the AI prompt includes the POWER engagement block and taste profile with strong affinities listed
- [ ] AC 7: Given a DORMANT user (14+ days since last save), when they open chat and tap an intent pill, then the AI prompt includes the DORMANT engagement block (welcome-back framing) and does NOT include a taste profile block
- [ ] AC 8: Given a user has 10 saves in the current area, when the system context is built, then exactly 8 saves are injected (most recent first, 9th and 10th excluded)
- [ ] AC 9: Given a saved POI has a userNote, when the system context is built, then the note appears in the save context block in format `Name: "note"`
- [ ] AC 10: Given a user with 3+ "park" saves and "restaurant" in their notableAbsences taps "Surprise me", when the AI prompt is built, then the prompt contains the inverse filter referencing "restaurant" (not "park")
- [ ] AC 11: Given a new install (no saves), when a `SavedPoi` is saved, then the `user_note` column is null and no crash occurs
- [ ] AC 12: Given the DevSeeder DORMANT persona is seeded, when `EngagementLevel.from()` is called with those saves and current time, then the result is `DORMANT`
- [ ] AC 13: Given `TasteProfileBuilder.build(emptyList(), nowMs)` is called, then all list fields are empty, `totalSaves == 0`, and no exception is thrown
- [ ] AC 14: Given an area portrait returns 12+ POIs, when an intent pill is tapped, then the system context contains the HIGH CONFIDENCE block with "insider" language
- [ ] AC 15: Given all existing `buildAreaPortraitPrompt` tests in `GeminiPromptBuilderTest`, when the full test suite runs after this refactor, then all pre-existing tests still pass
- [ ] AC 16: Given a user taps an intent pill and then taps a second pill before the first response arrives, when `tapIntentPill` is called a second time, then it returns immediately without adding a second system context to history

---

## Additional Context

### Dependencies

- No new external libraries required
- Depends on existing: SQLDelight, Koin (DI), kotlinx-coroutines, `AppClock`
- `schemaVersion` bump (Task 6) is tightly coupled with `6.sqm` — both must land in the same commit or existing-install devices will crash
- `saveWithTimestamp` (Task 8) must be added to the `SavedPoiRepository` interface before `DevSeeder` (Task 13) can compile

### Testing Strategy

- **Unit tests (commonTest — run with `./gradlew :composeApp:test`):**
  - `TasteProfileBuilderTest` — pure function, no mocks
  - `EngagementLevelTest` — pure function, no mocks
  - `GeminiPromptBuilderTest` — pure string assertions, no mocks
  - All new tests runnable without device
- **ChatViewModelTest updates:**
  - 3 existing tests that call `systemContextForTest` after `openChat()` will return empty string after this refactor (system context is now deferred). Update these tests to call `viewModel.tapIntentPill(ChatIntent.DISCOVER)` first, then assert on `systemContextForTest`.
- **Manual device testing checklist:**
  - FRESH persona (DevSeeder): 5 intent pills visible, correct labels and emojis
  - LIGHT persona: tap Tonight → AI references saves lightly, no taste profile block
  - POWER persona: tap Surprise me → AI recommends against taste pattern (notableAbsences used)
  - DORMANT persona: tap any pill → welcome-back tone, no taste profile in prompt
  - Power persona with notes: tap any pill → note content visible in natural AI response
  - Existing chat conversation: same-area preserve still works (open, close, reopen → no pills if already tapped)
  - Context shift: mid-conversation ask about a different area → AI bridges gracefully

### Notes

- COMMIT ORDER: Task 6 (migration + schemaVersion bump) must be in the same commit. If split, existing-install users on the old schemaVersion will not run the migration.
- `SavedPoiRepositoryTest` asserts `savedAt == fakeClock.nowMs` — this test remains valid after Task 8 because `save()` still uses `clock.nowMs()`. No change to this test needed.
- PromptComparisonTest: run after implementation to compare v2 vs v3 side-by-side. Command: `./gradlew :composeApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.areadiscovery.PromptComparisonTest`
- Future (v2): contextual note injection — filter by area + time-of-day + intent. The "by the way" moment (AI surfaces forgotten note at exactly the right time).
- Future (v2): intent-aware follow-up chips for LIGHT+ users.
- Future: DORMANT+POWER hybrid — consider separate `DORMANT_POWER` level that combines welcome-back framing with taste profile for high-save returning users.
