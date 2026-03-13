---
title: 'Dynamic Vibes v1 + Chat Output Quality'
slug: 'dynamic-vibes-v1-chat-output-quality'
created: '2026-03-13'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'SQLDelight', 'Gemini AI', 'Ktor SSE', 'kotlinx.serialization']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt'
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/10.sqm'
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/area_vibe_cache.sq'
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/user_preferences.sq'
code_patterns:
  - 'SQLDelight for all persistence — no DataStore in project'
  - 'BucketUpdate sealed class for pipeline events'
  - 'Two-stage Gemini pipeline: Stage 1 = PinsReady, Stage 2 = BucketComplete + PortraitComplete'
  - 'kotlinx.serialization for JSON parsing (@Serializable, @SerialName)'
  - 'MapUiState.Ready is the single source of truth for map screen state'
  - 'computeVibePoiCounts + computeVibeAreaSaveCounts in MapViewModel drive VibeRail'
test_patterns:
  - 'MapViewModelTest for state transitions'
  - 'Unit tests for parsers and prompt builders'
  - 'Given/When/Then style in existing tests'
---

# Tech-Spec: Dynamic Vibes v1 + Chat Output Quality

**Created:** 2026-03-13

## Overview

### Problem Statement

The vibe chip row is hardcoded to 6 static `BucketType` enum values — the same everywhere regardless of area. A beach town and a city centre show identical chips. Chat follow-up responses return plain prose with no POI cards, and raw markdown asterisks (`**bold**`, `*italic*`) are visible in chat bubbles because the renderer treats all Gemini output as plain text.

### Solution

Gemini generates free-form vibes with emoji + structured JSON schema per area. The chip row becomes fully dynamic — driven by Gemini Stage 1 output rather than a hardcoded enum. `BucketType` retires from Stage 2 (detail panels also switch to dynamic vibe content). Chat gets a dual-channel response format (`{"prose": "...", "pois": [...]}`) for all turns, follow-up context injection preventing duplicate POI recommendations, and a `MarkdownText` composable in `commonMain` that converts Gemini markdown to `AnnotatedString`.

### Scope

**In Scope:**

Part 1 — Dynamic Vibes:
- `DynamicVibe` data class (label, emoji, poiIds, why) — replaces `Vibe` enum in chip row
- New `area_vibe_cache` SQLDelight table (DB migration 10) — stores dynamic vibes per area
- Stage 1 slim prompt updated: returns `vibes` array + single dominant vibe per POI (`"v"`)
- Stage 2 full prompt updated: uses Stage 1 vibe labels as contract, returns multi-vibe binding per POI + vibe prose; BucketType retires from Stage 2
- `AreaRepositoryImpl` updated: emits `DynamicVibe` events instead of `BucketContent` for Stage 2
- Debounced viewport refresh (500m pan or 2 zoom levels — debounce 800ms)
- Sticky-until-stale rule — chip row only updates when new vibes arrive, not mid-load
- Skeleton shimmer transition on chip row during refresh
- "Exploring..." fallback chip when vibes fail or are empty
- Dual quality gate: prompt instructs "only return vibes with 3+ POIs"; client drops any vibe with fewer than 2 tagged POIs
- Cold start picker overlay (8-10 emoji cards, tap 2-3, skip button) — shown on first launch, stored in SQLDelight `user_preferences` table (`cold_start_seen = "true"`)
- Pinned vibes in SQLDelight `user_preferences` table (max 3) — long-press chip to pin, pin indicator on chip
- Max 6 chip slots with priority fill: Pinned → Saved-POI vibes in viewport → Gemini vibes by taste profile rank
- Count badges on each vibe chip (count of tagged POIs)
- Vibe-aware chat: active `DynamicVibe` label injected into `buildChatSystemContext` as context line
- Graceful personality reset: deselect vibe → null context passed to chat
- Saved-POI vibe persistence: before rendering Gemini vibes, append any saved-POI vibes in viewport not in Gemini list
- Offline vibe cache: `area_vibe_cache` serves chips offline; subtle offline indicator on chip row
- Stage 1/2 vibe label contract: Stage 2 prompt includes "Use these exact vibe labels: [list from Stage 1]"

Part 2 — Chat Output Quality:
- Dual-channel chat response format `{"prose": "...", "pois": [...]}` for all chat turns (initial + follow-up)
- `outputFormatBlock()` in `GeminiPromptBuilder` updated to request dual-channel JSON
- `ChatViewModel` parser updated to parse `{"prose", "pois"}` instead of embedded POI extraction from prose
- Follow-up context injection: each follow-up prompt includes previously recommended POI names; AI returns only new recommendations
- `MarkdownText` composable in `commonMain/kotlin/com/harazone/ui/components/` — pure Kotlin, no expect/actual, converts `**bold**`, `*italic*`, `` `code` ``, `- list` to `AnnotatedString`
- Strip-then-render fallback: if parsing fails, strip all markdown symbols and render plain text

**Out of Scope:**
- Emoji consistency cache (#42), vibe label dedup (#34), long-press menu (#52)
- Intent pills adapting to active vibe (#13), vibe personality tone shifts (#14)
- Time-layered / weather-reactive / seasonal vibes (#16, #18, #38)
- POI accumulator across turns (#65), "Ask About This" card action (#67)
- Stage 2 delimiter format migration to JSON (stays as `---BUCKET---` / `---POIS---`)
- BucketType removal from `area_bucket_cache` (old table stays as-is)
- Vibe-colored map zones, ambient pin pulses (#46, #48)
- All Phase 2/3 brainstorm items

## Context for Development

### Codebase Patterns

- KMP app: `commonMain` for all business logic and shared UI; `androidMain`/`iosMain` only for platform-specific expect/actual
- **SQLDelight for ALL persistence** — no DataStore in project; `.sq` files in `commonMain/sqldelight/`, migrations numbered `2.sqm`–`9.sqm` (next = `10.sqm`)
- `kotlinx.serialization` (`@Serializable`, `@SerialName`) for all JSON — used on `POI`, `ChatPoiCard`, `PoiJson`
- **Two-stage Gemini pipeline**: Stage 1 (`buildPinOnlyPrompt`) → emits `BucketUpdate.PinsReady(pois)`; Stage 2 → emits `BucketUpdate.BucketComplete` per bucket, then `BucketUpdate.PortraitComplete(pois)`
- `BucketUpdate` is a sealed class in `Bucket.kt` — add new subclasses here for `VibesReady` and `DynamicVibeComplete`
- Stage 1 currently returns flat JSON array `[{n, t, lat, lng}]` — must change to object `{vibes:[...], pois:[...]}` to carry vibe list
- Stage 2 parsed via delimiter: split on `---POIS---`, then split bucket section on `---BUCKET---`; each bucket parsed as `BucketJson`
- `GeminiResponseParser.PoiJson`: fields `n, t, v, w, h, s, r, lat, lng, wiki` — add `vs: List<String>? = null` for multi-vibe Stage 2
- `computeVibePoiCounts` in `MapViewModel`: `Vibe.entries.associateWith { v -> pois.count { it.vibe.contains(v.name) } }` — must switch to `DynamicVibe`-keyed map
- `computeVibeAreaSaveCounts` in `MapViewModel`: groups `SavedPoi.vibe` (String) against `Vibe.entries` — must switch to match against `DynamicVibe.label`
- `MapUiState.Ready.activeVibe: Vibe?` — must become `activeDynamicVibe: DynamicVibe?`; `vibePoiCounts: Map<Vibe, Int>` → `Map<DynamicVibe, Int>`; `vibeAreaSaveCounts: Map<Vibe, Int>` → `Map<String, Int>` (label-keyed)
- `ChatUiState.vibeName: String?` already exists — used to inject active vibe name into chat header
- `outputFormatBlock()` in `GeminiPromptBuilder` specifies current embedded-JSON format (prose + inline `{n,t,lat,lng,w}` objects) — must be replaced with `{"prose":"...","pois":[...]}` wrapper
- `parsePoiCards` in `ChatViewModel` (bracket-depth extractor) — must be replaced with `json.decodeFromString<ChatResponse>` where `ChatResponse(prose, pois)`
- `conversationHistory: MutableList<ChatMessage>` — add previous POI names before each follow-up to inject context
- `VibeRail` composable currently takes `vibes: Array<Vibe>` — must accept `List<DynamicVibe>`; `VibeOrb` driven by `Vibe` enum properties (`accentColorHex`, `orbIconName`) — `DynamicVibe` carries emoji instead
- **No DataStore** — use SQLDelight `user_preferences` table (key/value TEXT) for `cold_start_seen` and `pinned_vibes` JSON array
- `SavedPoi.vibe: String` already in DB (migration 9) — no schema change needed for saved-POI vibe persistence
- `AnnotatedString` + `SpanStyle` available in Compose Multiplatform `commonMain` — markdown renderer needs no expect/actual

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/Bucket.kt` | BucketType enum, BucketContent, BucketUpdate sealed class — add DynamicVibe models + new BucketUpdate events here |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/Vibe.kt` | Vibe enum (displayName, accentColorHex, orbIconName) — kept for backward compat; DynamicVibe is separate |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | `vibe: String` field — add `vibes: List<String>` for multi-vibe Stage 2 binding |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | `buildPinOnlyPrompt`, `buildChatSystemContext`, `outputFormatBlock` — all three need changes |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt` | `PoiJson`, `parsePinOnlyResponse` — Stage 1 response shape changes; add `vs` field to PoiJson |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt` | Two-stage pipeline — add `VibesReady` emission from Stage 1; Stage 2 emits `DynamicVibeComplete` instead of `BucketComplete` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | `computeVibePoiCounts`, `computeVibeAreaSaveCounts`, `switchVibe`, `activeVibe` — all switch to DynamicVibe |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | `activeVibe: Vibe?` → `activeDynamicVibe: DynamicVibe?`; `vibePoiCounts`/`vibeAreaSaveCounts` map types change |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | `tapIntentPill`, `parsePoiCards`, `conversationHistory`, `buildChatSystemContext` call — dual-channel + injection |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt` | Add `ChatResponse` data class; `ChatPoiCard` unchanged |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt` | Accepts `List<DynamicVibe>` instead of `Array<Vibe>`; VibeOrb uses emoji instead of orbIconName |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/area_bucket_cache.sq` | Stays as-is — old cache untouched |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq` | `vibe TEXT` already present (migration 9) — no change |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/10.sqm` | CREATE `area_vibe_cache` + `user_preferences` tables |

### Technical Decisions

- **Persistence for pinned vibes + cold start flag**: SQLDelight `user_preferences` key/value table (not DataStore — no DataStore in project)
- **New `area_vibe_cache` table**: `(area_name TEXT, language TEXT, vibes_json TEXT, expires_at INTEGER, created_at INTEGER, PRIMARY KEY(area_name, language))`; TTL = 3 hours (dynamic, area-dependent)
- **`user_preferences` table**: `(key TEXT PRIMARY KEY, value TEXT NOT NULL)`; keys: `cold_start_seen` = `"true"`, `pinned_vibes` = `'["Street Art","Craft Beer"]'`
- **Stage 1 response shape change**: from flat `[{n,t,lat,lng}]` → `{"vibes":[{"label":"...","icon":"..."}],"pois":[{n,t,lat,lng,v}]}`; `parsePinOnlyResponse` must handle both old and new shape (backward compat with cached chunks)
- **New domain models**: `DynamicVibe(label: String, icon: String, poiIds: List<String> = emptyList())` in new file `DynamicVibe.kt`; `DynamicVibeContent(label: String, icon: String, highlight: String, content: String, poiIds: List<String>)` for Stage 2 enriched content
- **New `BucketUpdate` events**: `VibesReady(vibes: List<DynamicVibe>, pois: List<POI>)` replaces `PinsReady` for Stage 1; `DynamicVibeComplete(content: DynamicVibeContent)` for Stage 2 — `BucketComplete` and `PinsReady` kept for backward compat during migration
- **Stage 2 prompt contract**: Stage 2 receives vibe labels from Stage 1 and must use them verbatim in the delimiter sections; add `poiIds` field per vibe section
- **POI multi-vibe**: add `vibes: List<String> = emptyList()` to `POI` data class; `mergeStage2OntoCached` populates it from Stage 2 `vs` field; `vibe: String` (single dominant) remains for backward compat with SavedPoi
- **Dual-channel chat**: add `@Serializable data class ChatResponse(val prose: String, val pois: List<ChatPoiCard>)` to `ChatUiState.kt`; `outputFormatBlock()` replaced entirely; `parsePoiCards` replaced with `json.decodeFromString<ChatResponse>(text)`
- **Follow-up injection**: before each follow-up `sendMessage()`, prepend to system context: `"Previously recommended: [name1, name2, ...]. Return NEW places only."`
- **Markdown renderer**: `MarkdownText(text: String, modifier: Modifier)` in `commonMain/kotlin/com/harazone/ui/components/MarkdownText.kt`; parses `**bold**` → `SpanStyle(fontWeight = Bold)`, `*italic*` → `SpanStyle(fontStyle = Italic)`, `` `code` `` → `SpanStyle(fontFamily = Monospace, background = ...)`, `- item` → bullet prefix; strip fallback on any parse error
- **Vibe quality gate (client-side)**: after receiving `VibesReady`, filter out any `DynamicVibe` where `poiIds.size < 2`; prompt-side gate already asks for "3+ POIs per vibe"
- **Saved-POI vibe persistence**: in `MapViewModel`, before publishing `dynamicVibes` to UI, check `latestSavedPois` for saves in current area whose `vibe` is not in Gemini's list → append as `DynamicVibe(label=save.vibe, icon="🔖", poiIds=[save.id])`
- **Offline**: `area_vibe_cache` read on cache hit, even if expired, when network unavailable; add `isOfflineVibes: Boolean` flag to `MapUiState.Ready` to show offline indicator on chip row. `isOfflineVibes` is set to `true` when `VibesReady(fromCache = true)` is received AND `connectivityMonitor.isOnline == false`. `isOfflineVibes` is cleared when: (a) a live `VibesReady(fromCache = false)` arrives, or (b) a 30-second timeout fires after network returns without a successful live `VibesReady` — in this case reset to `false` and show the fallback "Exploring..." chip to avoid a permanent stale-offline indicator. Observe `ConnectivityMonitor.isOnline` in `MapViewModel` — when it transitions false→true, trigger a background vibe re-fetch. If the re-fetch fails within 30s, clear `isOfflineVibes` anyway (timeout). This prevents the offline badge persisting indefinitely when network returns but Gemini is slow.
- **Debounce**: viewport pan/zoom triggers vibe refresh only if center moves 500m+ or zoom changes 2+ levels; debounce 800ms using `kotlinx.coroutines` `debounce` operator on viewport change flow
- **Skeleton shimmer**: when area change triggers vibe reload, set `isLoadingVibes: Boolean = true` in `MapUiState.Ready`; `VibeRail` renders skeleton chips in this state
- **Pinned vibes max 3**: enforced in `MapViewModel.togglePin(vibe: DynamicVibe)` — if 3 already pinned and new pin requested, no-op + show toast
- **Cold start picker**: shown when `user_preferences["cold_start_seen"]` is null/missing; overlay composable `ColdStartPickerOverlay` in `commonMain/kotlin/com/harazone/ui/map/components/`; on confirm, save selections as `pinned_vibes` JSON + set `cold_start_seen = "true"`
- **Priority fill order**: `VibeRail` receives pre-sorted `List<DynamicVibe>` from `MapViewModel.buildChipRowVibes()` — sort: pinned (in pin order) → saved-POI vibes → Gemini vibes; cap at 6

## Implementation Plan

### Tasks

Tasks are ordered by dependency — lowest-level foundation first.

---

#### GROUP A — Foundation (blocks everything else)

- [ ] **Task 1: DB Migration 10**
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/10.sqm`
  - Action: Create migration file with two new tables:
    ```sql
    CREATE TABLE IF NOT EXISTS area_vibe_cache (
        area_name  TEXT NOT NULL,
        language   TEXT NOT NULL,
        vibes_json TEXT NOT NULL,
        expires_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY (area_name, language)
    );
    CREATE INDEX IF NOT EXISTS idx_area_vibe_cache_expires_at ON area_vibe_cache(expires_at);
    CREATE INDEX IF NOT EXISTS idx_area_vibe_cache_lookup ON area_vibe_cache(area_name, language);

    CREATE TABLE IF NOT EXISTS user_preferences (
        key   TEXT NOT NULL PRIMARY KEY,
        value TEXT NOT NULL
    );
    ```
  - Notes: Do NOT touch `area_bucket_cache` or any existing table. Migration number must be 10 (follows 9.sqm). SQLDelight runs migrations automatically on first DB open via the migration runner passed to `AreaDiscoveryDatabase` factory — no manual trigger needed. Tasks 2 (query files) and 1 (migration) must both be done before any `area_vibe_cache` or `user_preferences` queries are compiled — SQLDelight compile-time verification will catch any mismatch. Concurrent inserts for the same `(area_name, language)` are handled by `INSERT OR REPLACE` — last write wins, which is acceptable (eventual consistency).

- [ ] **Task 2: New SQLDelight query files**
  - File (new): `composeApp/src/commonMain/sqldelight/com/harazone/data/local/area_vibe_cache.sq`
  - Action: Add queries:
    ```sql
    getVibes:
    SELECT * FROM area_vibe_cache WHERE area_name = :area_name AND language = :language;

    insertOrReplaceVibes:
    INSERT OR REPLACE INTO area_vibe_cache(area_name, language, vibes_json, expires_at, created_at)
    VALUES (:area_name, :language, :vibes_json, :expires_at, :created_at);

    deleteExpiredVibes:
    DELETE FROM area_vibe_cache WHERE expires_at <= :current_time;
    ```
  - File (new): `composeApp/src/commonMain/sqldelight/com/harazone/data/local/user_preferences.sq`
  - Action: Add queries:
    ```sql
    get:
    SELECT value FROM user_preferences WHERE key = :key;

    set:
    INSERT OR REPLACE INTO user_preferences(key, value) VALUES (:key, :value);

    delete:
    DELETE FROM user_preferences WHERE key = :key;
    ```

- [ ] **Task 3: DynamicVibe domain models**
  - File (new): `composeApp/src/commonMain/kotlin/com/harazone/domain/model/DynamicVibe.kt`
  - Action: Create file with:
    ```kotlin
    import kotlinx.serialization.Serializable

    @Serializable
    data class DynamicVibe(
        val label: String,
        val icon: String,
        val poiIds: List<String> = emptyList(),
    )

    @Serializable
    data class DynamicVibeContent(
        val label: String,
        val icon: String,
        val highlight: String,
        val content: String,
        val poiIds: List<String> = emptyList(),
    )
    ```

- [ ] **Task 4: New BucketUpdate events**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/BucketUpdate.kt` (NOT Bucket.kt — `BucketUpdate` sealed class lives here)
  - Action: Add two new subclasses to the `BucketUpdate` sealed class. Keep ALL existing subclasses (`ContentDelta`, `BucketComplete`, `PinsReady`, `PortraitComplete`, `ContentAvailabilityNote`) unchanged:
    ```kotlin
    data class VibesReady(val vibes: List<DynamicVibe>, val pois: List<POI>, val fromCache: Boolean = false) : BucketUpdate()
    data class DynamicVibeComplete(val content: DynamicVibeContent) : BucketUpdate()
    ```
  - Notes: `PinsReady` and `BucketComplete` stay for old cache-hit paths. `VibesReady` is the new Stage 1 event for the dynamic path. Both coexist — `MapViewModel` pipeline collector must handle both during migration.

- [ ] **Task 5: POI multi-vibe field**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt`
  - Action: Add `val vibes: List<String> = emptyList()` field after the existing `vibe: String` field. Keep `vibe: String` as-is (used for SavedPoi compat and single-dominant at Stage 1). `vibes` is populated at Stage 2 merge.

---

#### GROUP B — Stage 1 Data Layer

- [ ] **Task 6: Stage 1 prompt — request vibes + POIs**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Replace `buildPinOnlyPrompt` body with a new prompt that requests a JSON object (not array). New prompt:
    ```
    Area: "{areaName}". Return JSON only, no other text.
    Schema: {"vibes":[{"label":"Street Art","icon":"🎨"},...],"pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"Street Art"},...]}
    Rules:
    - vibes: 4-6 most distinctive dimensions of THIS area. Each vibe must have 3+ real POIs in this list.
    - pois: 8 best POIs. Each POI "v" field MUST exactly match one of the vibe labels you returned — character-for-character, same case.
    - ONLY return vibes where at least 3 of the 8 POIs will be tagged with that vibe label.
    - t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
    - GPS to 4 decimal places. Skip any POI you cannot place accurately.
    Example:
    {"vibes":[{"label":"Street Art","icon":"🎨"},{"label":"Craft Beer","icon":"🍺"}],"pois":[{"n":"Brick Lane Murals","t":"arts","lat":51.5215,"lng":-0.0714,"v":"Street Art"},{"n":"Howling Hops","t":"food","lat":51.5469,"lng":-0.0507,"v":"Craft Beer"}]}
    ```
  - Notes: The explicit "MUST exactly match" instruction and the inline example are critical to prevent Gemini returning POI `v` fields that don't match any vibe label — which would cause all quality-gate filtering to fail. Pass `areaName` only (no other context — keep Stage 1 fast).

- [ ] **Task 7: Stage 1 parser — parse new shape**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt`
  - Action 1: Add new internal data classes for Stage 1 shape:
    ```kotlin
    @Serializable
    internal data class VibeJson(val label: String = "", val icon: String = "")

    @Serializable
    internal data class Stage1Response(
        val vibes: List<VibeJson> = emptyList(),
        val pois: List<PoiJson> = emptyList(),
    )
    ```
  - Action 2: Add new public method `parseStage1Response(text: String): Pair<List<DynamicVibe>, List<POI>>` that:
    1. Strips markdown code fences if present
    2. Tries `json.decodeFromString<Stage1Response>(cleaned)` — if success, maps to `DynamicVibe` and `POI` lists
    3. Falls back to `json.decodeFromString<List<PoiJson>>(cleaned)` (old flat array) for backward compat with any cached stage-1 chunks still in flight
    4. On any exception, returns `Pair(emptyList(), emptyList())` and logs error
  - Action 3: Add `vs: List<String>? = null` field to existing `PoiJson` data class. This field is ONLY populated in Stage 2 responses — Stage 1 responses will never include it (parser must treat `null` as `emptyList()` silently). Stage 1 uses only `v: String` (single dominant). Stage 2 uses both `v` (dominant, for SavedPoi compat) and `vs` (full multi-vibe list).
  - Notes: Keep existing `parsePinOnlyResponse` — it is still used by the old `PinsReady` cache-hit path. New `parseStage1Response` is only called on fresh Gemini requests.

- [ ] **Task 8: Stage 1 repository — emit VibesReady, cache vibes**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt`
  - Action 1: In the Stage 1 Gemini call path, replace the call to `parser.parsePinOnlyResponse(text)` with `parser.parseStage1Response(text)`. Destructure result into `(dynamicVibes, pois)`.
  - Action 2: Apply client-side quality gate: `val qualityVibes = dynamicVibes.filter { dv -> pois.count { it.vibe == dv.label } >= 2 }`. Do NOT use `it.poiIds.size` — `poiIds` is always empty at Stage 1 (it is only populated at Stage 2). The count-based approach is the correct gate here.
  - Action 3: Emit `BucketUpdate.VibesReady(vibes = qualityVibes, pois = pois)` instead of `BucketUpdate.PinsReady(pois)`.
  - Action 4: Cache vibes to `area_vibe_cache` — serialize `qualityVibes` to JSON string, write with `expires_at = now + 3 hours`. Also cache POIs to `area_poi_cache` as before.
  - Action 5: Add offline path: on cache hit in `area_vibe_cache`, emit `VibesReady` from cache even if expired when network is unavailable. Set `fromCache = true` on `VibesReady` for the offline indicator.
  - Action 6: Coexistence with existing cache-hit paths — the existing `area_bucket_cache` hit path still emits `BucketComplete` events for old bucket content. The new `area_vibe_cache` hit path emits `VibesReady`. Both can coexist in the same pipeline: `MapViewModel` collector already has a `when (update)` branch — add `is BucketUpdate.VibesReady -> ...` alongside existing `is BucketUpdate.BucketComplete -> ...` and `is BucketUpdate.PinsReady -> ...` branches. No removal of existing branches required.

---

#### GROUP C — Stage 2 Data Layer

- [ ] **Task 9: Stage 2 prompt — vibe label contract + DynamicVibeContent sections**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Add new method `buildDynamicVibeEnrichmentPrompt(areaName: String, vibeLabels: List<String>, poiNames: List<String>): String` that:
    1. Takes the Stage 1 vibe labels as a contract
    2. Requests one `---VIBE---` delimited section per vibe label with: highlight (1 sentence), content (2-3 sentences), and `poi_ids` (list of POI names that belong to this vibe)
    3. Requests `---POIS---` section with enriched POI JSON including `vs` (list of vibe labels for each POI)
    - Prompt skeleton:
      ```
      You are a passionate local guide for {areaName}.
      Use EXACTLY these vibe labels (verbatim): {vibeLabels.joinToString(", ")}
      POIs to enrich: {poiNames.joinToString(", ")}

      For each vibe, output a section:
      ---VIBE---
      {"label":"{exact label}","icon":"{emoji}","highlight":"one sentence","content":"2-3 sentences","poi_ids":["Name1","Name2"]}

      Then output:
      ---POIS---
      [{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"dominant vibe","vs":["vibe1","vibe2"],"w":"why special","r":4.2}]
      ```
  - Notes: Keep existing `buildEnrichmentPrompt` — it is still called for old BucketType cache path. This is a NEW method for the dynamic path.

- [ ] **Task 10: Stage 2 parser — DynamicVibeContent + POI multi-vibe**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt`
  - Action 1: Add `@Serializable internal data class DynamicVibeJson(val label: String = "", val icon: String = "", val highlight: String = "", val content: String = "", val poi_ids: List<String> = emptyList())`
  - Action 2: Add public method `parseDynamicVibeResponse(text: String): Pair<List<DynamicVibeContent>, List<POI>>` that:
    1. Splits on `---POIS---` → vibesSection + poisSection
    2. Splits vibesSection on `---VIBE---` → one JSON block per vibe
    3. Decodes each block as `DynamicVibeJson` → maps to `DynamicVibeContent`
    4. Decodes poisSection as `List<PoiJson>` → maps to `POI` with `vibes = poiJson.vs ?: emptyList()`
    5. Returns `Pair(vibeContents, pois)` — on any exception logs error and returns empty pair

- [ ] **Task 11: Stage 2 repository — DynamicVibeComplete emission**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt`
  - Action 1: When `VibesReady` has been emitted (Stage 1 complete), proceed to Stage 2 using `buildDynamicVibeEnrichmentPrompt(areaName, vibeLabels, poiNames)`.
  - Action 2: Call `parser.parseDynamicVibeResponse(accumulatedText)` on Stage 2 completion.
  - Action 3: For each `DynamicVibeContent` in result, emit `BucketUpdate.DynamicVibeComplete(content)`.
  - Action 4: `mergeStage2OntoCached` current signature (already in `AreaRepositoryImpl`): `private fun mergeStage2OntoCached(stage1: List<POI>, stage2: List<POI>): List<POI>`. Update it to also copy the `vibes` field: in the `cached.copy(...)` block, add `vibes = enrichment.vibes.ifEmpty { cached.vibes }`. This OVERWRITES (not appends) — Stage 2 `vibes` replaces the cached value. If Stage 2 returns empty `vibes`, keep existing cached value. Call this method with `mergeStage2OntoCached(stage1Pois, stage2Pois)` as before.
  - Action 5: Emit `BucketUpdate.PortraitComplete(mergedPois)` as final event.
  - Notes: Stage 2 is still non-streaming (accumulate full text before parsing) — same as existing pattern.

---

#### GROUP D — User Preferences Repository

- [ ] **Task 12: UserPreferencesRepository**
  - File (new): `composeApp/src/commonMain/kotlin/com/harazone/data/repository/UserPreferencesRepository.kt`
  - Action: Create class with SQLDelight database access:
    ```kotlin
    class UserPreferencesRepository(private val db: AreaDiscoveryDatabase) {
        private val json = Json { ignoreUnknownKeys = true }

        fun getColdStartSeen(): Boolean =
            db.user_preferencesQueries.get("cold_start_seen").executeAsOneOrNull() == "true"

        fun setColdStartSeen() =
            db.user_preferencesQueries.set("cold_start_seen", "true")

        fun getPinnedVibes(): List<String> {
            val raw = db.user_preferencesQueries.get("pinned_vibes").executeAsOneOrNull() ?: return emptyList()
            return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
        }

        fun setPinnedVibes(labels: List<String>) =
            db.user_preferencesQueries.set("pinned_vibes", json.encodeToString(labels))
    }
    ```
  - Action 2: Wire `UserPreferencesRepository` into Koin. Find the DI module file by searching for `single { AreaRepositoryImpl` — the file containing that registration is the correct module (likely `AppModule.kt` or `DataModule.kt` in `di/`). Add: `single { UserPreferencesRepository(get()) }`. Then inject into `MapViewModel` by adding `private val userPreferencesRepository: UserPreferencesRepository` to its constructor — Koin will resolve it automatically.

---

#### GROUP E — MapViewModel + UI State + Chip Row

- [ ] **Task 13: MapUiState — DynamicVibe types**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`
  - Action: In `MapUiState.Ready`, make the following field changes:
    - `activeVibe: Vibe?` → `activeDynamicVibe: DynamicVibe? = null`
    - `vibePoiCounts: Map<Vibe, Int>` → `dynamicVibePoiCounts: Map<String, Int> = emptyMap()` (keyed by label)
    - `vibeAreaSaveCounts: Map<Vibe, Int>` → `dynamicVibeAreaSaveCounts: Map<String, Int> = emptyMap()` (keyed by label)
    - Add: `dynamicVibes: List<DynamicVibe> = emptyList()`
    - Add: `isLoadingVibes: Boolean = false`
    - Add: `isOfflineVibes: Boolean = false`
    - Add: `showColdStartPicker: Boolean = false`
  - Notes: Remove old `activeVibe`, `vibePoiCounts`, `vibeAreaSaveCounts` fields. Search for all usages with `Ctrl+F` (or grep for `activeVibe`, `vibePoiCounts`, `vibeAreaSaveCounts`) — update every `.copy(activeVibe = ...)` call site in `MapViewModel.kt` and every property read in `MapScreen.kt`. The `SavedVibeOrb` in `VibeRail` currently reads `vibeAreaSaveCounts` — it stays as-is in UI shape but its data source switches to `dynamicVibeAreaSaveCounts: Map<String, Int>`. `VibeRail` call site in `MapScreen` must pass `dynamicVibeAreaSaveCounts` instead of `vibeAreaSaveCounts`.

- [ ] **Task 14: MapViewModel — dynamic vibe wiring**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action 1: Inject `UserPreferencesRepository` via constructor.
  - Action 2: Add `private var currentDynamicVibes: List<DynamicVibe> = emptyList()` and `private var pinnedVibeLabels: List<String> = emptyList()` fields. Load pinned vibes from `UserPreferencesRepository.getPinnedVibes()` on init.
  - Action 3: `isLoadingVibes` lifecycle: set to `true` when area search starts (same place `isSearchingArea = true` is currently set). Set to `false` in three cases: (a) `VibesReady` received successfully, (b) pipeline emits an error/exception for the Stage 1 call — catch in the collector and set `isLoadingVibes = false` then show fallback, (c) `PortraitComplete` received (Stage 2 done — belt-and-suspenders). If `isLoadingVibes == true` and a new viewport refresh fires, cancel the previous fetch job first (same pattern as existing `areaFetchJob` cancellation) then start fresh. Handle `BucketUpdate.VibesReady` in the pipeline collector: set `isLoadingVibes = false`, set `isOfflineVibes = update.fromCache && !connectivityMonitor.isOnline`, call `buildChipRowVibes(update.vibes, areaName)`, update state with `dynamicVibes = chipRowVibes`, `dynamicVibePoiCounts = computeDynamicVibePoiCounts(update.pois, chipRowVibes)`.
  - Action 4: Handle `BucketUpdate.DynamicVibeComplete` in pipeline collector: update the matching vibe in `currentDynamicVibes` with enriched content (highlight, content, poiIds).
  - Action 5: Add NEW method `computeDynamicVibePoiCounts(pois: List<POI>, vibes: List<DynamicVibe>): Map<String, Int>` — for each `DynamicVibe`, count = number of POIs where `poi.vibe == dv.label || dv.label in poi.vibes` (case-sensitive, exact match — Gemini label contract enforces consistent casing). Each POI counts AT MOST ONCE per vibe (OR logic, not additive). If `vibes` is empty, return `emptyMap()`. Keep the existing `computeVibePoiCounts(pois: List<POI>): Map<Vibe, Int>` method unchanged alongside the new one — existing tests continue to pass. Call sites in MapViewModel that produce `vibePoiCounts` should call the new method; existing `MapUiState.Ready.vibePoiCounts` field is renamed to `dynamicVibePoiCounts` in Task 13.
  - Action 6: Replace `computeVibeAreaSaveCounts(saves, areaName): Map<Vibe, Int>` with version returning `Map<String, Int>` — group saves by `save.vibe` string directly (no Vibe enum lookup needed).
  - Action 7: Add `fun buildChipRowVibes(geminiVibes: List<DynamicVibe>, areaName: String): List<DynamicVibe>`:
    1. `areaName` comes from `(uiState as? MapUiState.Ready)?.areaName` at the call site in MapViewModel. Append saved-POI vibes: filter `latestSavedPois` by `save.areaName == areaName && save.vibe.isNotBlank()`. For each unique `save.vibe` label NOT already in `geminiVibes.map { it.label }`, create `DynamicVibe(label=save.vibe, icon="🔖")` and add to a `savedPoiVibes` list.
    2. Sort into chip row: `pinnedVibes` (those from `pinnedVibeLabels` — look them up in `geminiVibes` to get their Gemini emoji, fall back to "📌" if not in Gemini list) + `savedPoiVibes` (not pinned) + remaining Gemini vibes (not pinned, not saved-POI).
    3. Pinned vibes with no POIs in current viewport still appear — their count badge will be 0. This is correct behavior: pin is a persistent preference, not a filter.
    4. Cap at 6 total; return sorted list.
  - Action 8: Replace `fun switchVibe(vibe: Vibe)` with `fun switchDynamicVibe(vibe: DynamicVibe)` — toggles `activeDynamicVibe` in state.
  - Action 9: Add `fun togglePin(vibe: DynamicVibe)`:
    - If already pinned: remove from `pinnedVibeLabels`
    - If not pinned and `pinnedVibeLabels.size < 3`: add to `pinnedVibeLabels`
    - If not pinned and already 3 pinned: no-op (trigger snackbar in UI — add `pinLimitReached: Boolean` event to state or use a `SharedFlow<Unit>`)
    - Persist via `UserPreferencesRepository.setPinnedVibes(pinnedVibeLabels)`
    - Rebuild chip row: call `buildChipRowVibes(currentDynamicVibes)` and update state
  - Action 10: Debounced viewport refresh — add `private val viewportChangeEvents = MutableSharedFlow<MapBounds>(extraBufferCapacity = 1)` (where `MapBounds` is a simple data class `(centerLat, centerLng, zoom)`). In the map camera move callback (wherever `cameraMoveId` is currently incremented in MapViewModel), emit to `viewportChangeEvents`. In `init`, collect: `viewportChangeEvents.asFlow().debounce(800).collect { bounds -> onViewportSettled(bounds) }`. In `onViewportSettled`, compute distance from last bounds using haversine formula; if distance >= 500m or zoom delta >= 2, set `isLoadingVibes = true` and trigger area re-fetch. Note: `cameraMoveId: Int` is for composable recomposition only — do NOT use it as the debounce source.
  - Action 11: Cold start picker trigger — read preferences SYNCHRONOUSLY in `MapViewModel.init` via a `viewModelScope.launch` coroutine (not `LaunchedEffect` in MapScreen — that creates a race). In `init { viewModelScope.launch { pinnedVibeLabels = userPreferencesRepository.getPinnedVibes(); val coldStartSeen = userPreferencesRepository.getColdStartSeen(); if (!coldStartSeen) { /* set flag when Ready state is first entered */ pendingColdStart = true } } }`. Add `private var pendingColdStart = false`. When the pipeline emits `VibesReady` for the first time (i.e., when MapUiState transitions to Ready), if `pendingColdStart == true`: set `showColdStartPicker = true` in state and `pendingColdStart = false`. This ensures the picker only appears AFTER the map is ready (not during loading), without relying on `LaunchedEffect` timing. The scrim in `ColdStartPickerOverlay` must block all touches: `Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while(true) { awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } } }`. Boolean semantics: `cold_start_seen = "true"` means picker has been shown → do NOT show again. Null/absent or any other value → show picker.
  - Action 12: Skip-with-zero-pins — when user skips the picker, `showColdStartPicker = false` and `pinnedVibeLabels = emptyList()`. On first area load, `buildChipRowVibes()` will have empty `pinnedVibeLabels` and will fill all 6 slots with Gemini vibes — this is the correct fallback behavior.
  - Action 13: Update all `switchVibe` call sites in MapScreen composable to use `switchDynamicVibe`.

- [ ] **Task 15: VibeRail — dynamic chip row**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt`
  - Action 1: Change signature from `vibes: Array<Vibe>` to `vibes: List<DynamicVibe>`. Remove `activeVibe: Vibe?` parameter, add `activeDynamicVibe: DynamicVibe?`. Remove `vibePoiCounts: Map<Vibe, Int>`, add `dynamicVibePoiCounts: Map<String, Int>`. Remove `vibeAreaSaveCounts: Map<Vibe, Int>`, add `dynamicVibeAreaSaveCounts: Map<String, Int>`. Add `isLoadingVibes: Boolean`. Add `isOfflineVibes: Boolean`. Add `pinnedVibeLabels: List<String>`. Add `onLongPressVibe: (DynamicVibe) -> Unit`.
  - Action 2: Skeleton state: when `isLoadingVibes == true`, render 5 `SkeletonChip` composables instead of real chips. Define `SkeletonChip` as a private composable at the bottom of `VibeRail.kt`: a `Box` with `Modifier.width(80.dp).height(36.dp).clip(RoundedCornerShape(20.dp)).background(shimmerBrush)` where `shimmerBrush` is an `animatedBrush` cycling `Color(0x1FFFFFFF)` to `Color(0x33FFFFFF)` using `rememberInfiniteTransition`.
  - Action 3: Fallback state: when `vibes.isEmpty() && !isLoadingVibes`, render a single "Exploring..." chip with pulsing dot — a `Row` with a 6dp circle `Box` whose alpha animates 0.4f→1f infinitely, followed by `Text("Exploring...")`. Tapping triggers `onExploreRetry: () -> Unit` parameter (add to function signature; MapViewModel exposes `fun retryAreaFetch()` to connect this).
  - Action 4: Offline indicator: when `isOfflineVibes == true`, render a `Text("Offline · cached", style = caption, color = onSurface.copy(alpha=0.4f))` in a `Row` with a 5dp filled orange `Box` (dot), placed as a separate `Row` below the chip `LazyRow` inside the parent `Column`. No layout changes needed — it simply adds a row below.
  - Action 5: `SavedVibeOrb` stays in position and visual design. Its `saveCount` parameter switches from `vibeAreaSaveCounts[Vibe.X]` to reading from `dynamicVibeAreaSaveCounts` (the area's save count regardless of vibe). No SavedVibeOrb composable changes needed.
  - Action 6: Long-press on chip — wrap each `VibeOrb` in `Modifier.combinedClickable(onClick = { onVibeClick(vibe) }, onLongClick = { onLongPressVibe(vibe) })`. Import `androidx.compose.foundation.combinedClickable`. This is available in Compose Multiplatform commonMain.
  - Action 5: Normal state chip rendering: for each `DynamicVibe` in `vibes` (already sorted by `buildChipRowVibes`):
    - Render emoji from `vibe.icon` instead of `orbIconName` drawable
    - Chip label = `vibe.label`
    - Count badge = `dynamicVibePoiCounts[vibe.label] ?: 0`
    - Pin indicator: if `vibe.label in pinnedVibeLabels`, show 📌 superscript
    - Active state: `activeDynamicVibe?.label == vibe.label`
    - `onClick`: call existing vibe selection callback with `DynamicVibe`
    - `onLongPress`: call `onLongPressVibe(vibe)` → triggers `togglePin` in MapViewModel
  - Action 6: Update all call sites of `VibeRail` in `MapScreen` composable to pass new parameters.

- [ ] **Task 16: ColdStartPickerOverlay**
  - File (new): `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ColdStartPickerOverlay.kt`
  - Action: Create composable `ColdStartPickerOverlay(onConfirm: (List<String>) -> Unit, onSkip: () -> Unit)`:
    - Full-screen scrim with bottom-anchored sheet (not a system bottom sheet — just a Box with Column)
    - Title: "What excites you? ✨", subtitle: "Pick 2–3 to personalize your discovery"
    - Grid of 10 `PickerCard` composables (each: large emoji + one-word label). Use the vibe archetypes from the brainstorm: Art & Culture 🎨, Food Scene 🍜, History 🏛️, Nature 🌿, Music & Nightlife 🎶, Adventure 🏄, Shopping 🛍️, Cafes & Slow Days ☕, Architecture 🏗️, After Dark 🌙
    - Tapping a card toggles selected state (border highlight). Max 3 selected — if 3 already selected, further taps are no-ops.
    - Counter text: "X of 3 selected"
    - "Skip" button: always enabled → calls `onSkip()`
    - "Let's Go →" button: enabled when ≥ 1 selected → calls `onConfirm(selectedLabels)`
    - `PlatformBackHandler(enabled = true) { onSkip() }` — back button dismisses picker
  - Action 2: Wire into `MapScreen`: when `uiState.showColdStartPicker == true`, show `ColdStartPickerOverlay`. On confirm, call `MapViewModel.onColdStartConfirmed(selectedLabels)`. On skip, call `MapViewModel.onColdStartSkipped()`.
  - Action 3: In `MapViewModel`, add:
    - `fun onColdStartConfirmed(labels: List<String>)`: set `pinnedVibeLabels = labels`, persist via `UserPreferencesRepository.setPinnedVibes(labels)`, call `UserPreferencesRepository.setColdStartSeen()`, set `showColdStartPicker = false` in state
    - `fun onColdStartSkipped()`: call `UserPreferencesRepository.setColdStartSeen()`, set `showColdStartPicker = false` in state
  - Notes on label matching (F29): The cold start archetype labels ("Food Scene", "History", etc.) are ASPIRATIONAL hints, not exact Gemini labels. In `buildChipRowVibes`, pinned labels are matched against Gemini vibes using case-insensitive `contains`: `geminiVibes.firstOrNull { gv -> gv.label.contains(pinnedLabel, ignoreCase = true) || pinnedLabel.contains(gv.label, ignoreCase = true) }`. If a match is found, use the Gemini vibe (with its area-specific emoji). If no match, show the pinned label with "📌" emoji (pin persists even without a matching Gemini vibe). Example: pinned "Food Scene" matches Gemini "Street Food" (contains check fails) — in this case the chip shows "📌 Food Scene" with 0 count badge, signalling the user's preference even when Gemini returned more specific labels. This is acceptable v1 behavior; label fuzzy-matching (#34) is a Phase 2 improvement.

---

#### GROUP F — Chat Output Quality

- [ ] **Task 17: ChatResponse data class**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt`
  - Action: Add `@Serializable data class ChatResponse(val prose: String = "", val pois: List<ChatPoiCard> = emptyList())` to the file. No other changes.

- [ ] **Task 18: outputFormatBlock — dual-channel JSON**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
  - Action: Replace the entire `outputFormatBlock()` method body with:
    ```
    RESPONSE FORMAT: Always respond with a single JSON object — no other text, no markdown outside the JSON.
    Schema: {"prose":"your conversational reply here","pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"w":"why special"}]}
    Rules:
    - prose: 2-3 sentences max. Conversational, not a travel blog. End with a follow-up question.
    - pois: every place mentioned in prose MUST appear in the pois array. If no places mentioned, return empty array.
    - prose may use **bold**, *italic*, and `code` for emphasis — these will be rendered.
    - t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
    - DEPTH CONTROL: First response = 1-2 places. If user asks for more = 2-3 more. Never exceed 5 places total per message.
    Example: {"prose":"Check out **Brick Lane** for incredible street art — it changes weekly.\nWhat mood are you in — edgy underground spots or the well-known walls?","pois":[{"n":"Brick Lane","t":"arts","lat":51.5215,"lng":-0.0714,"w":"London's densest open-air gallery, curated by the street itself"}]}
    ```

- [ ] **Task 19: ChatViewModel — dual-channel parsing + follow-up injection + vibe context**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - Action 1: Replace `parsePoiCards(text: String)` method with `parseChatResponse(text: String): ChatResponse`. Define `private fun stripMarkdown(text: String): String` in `ChatViewModel` (not in `MarkdownText.kt`) — removes `**`, `*`, backtick pairs, and `^- ` line prefixes. New implementation:
    1. Strip markdown code fences if present: `text.trim().let { if (it.startsWith("```")) it.lines().drop(1).dropLast(1).joinToString("\n") else it }`
    2. Try `json.decodeFromString<ChatResponse>(cleaned)` — return on success
    3. On any exception: return `ChatResponse(prose = stripMarkdown(text), pois = emptyList())` — never crash
  - Action 2: In the response streaming/collection path (where `parsePoiCards` was called), replace with `parseChatResponse(accumulatedText)`. Mapping: `response.prose` → create a new `ChatBubble` appended to `_uiState.value.bubbles` (check existing `ChatBubble` data class fields — it already has a `content: String` field; map `response.prose` to `content`). `response.pois` → REPLACE `_uiState.value.poiCards` entirely (not accumulate). Each chat turn shows only that turn's POI cards; old cards are gone when the next turn responds. This is intentional — the card rail always reflects the most recent AI recommendation.
  - Action 3: Follow-up context injection — add `private val recommendedPoiNames: MutableList<String> = mutableListOf()` and `private var injectedContextIndex: Int = -1` to `ChatViewModel`. After each `parseChatResponse`, extend the list: `recommendedPoiNames += response.pois.map { it.name }`. Before each follow-up `sendMessage()` (when `conversationHistory.size > 1` and `recommendedPoiNames.isNotEmpty()`), insert at position `conversationHistory.size`: `ChatMessage(role = MessageRole.USER, content = "Context: Do not repeat these previously recommended places: ${recommendedPoiNames.joinToString(", ")}. Return only NEW places.")`. Save `injectedContextIndex = conversationHistory.size - 1`. After the response arrives, remove the injected message: `if (injectedContextIndex >= 0) { conversationHistory.removeAt(injectedContextIndex); injectedContextIndex = -1 }`. In `resetConversation()`, atomically clear BOTH: `recommendedPoiNames.clear()` AND `if (injectedContextIndex >= 0) { conversationHistory.removeAt(injectedContextIndex); injectedContextIndex = -1 }` — this prevents stale dedup rules bleeding into the next conversation.
  - Action 4: Also update `outputFormatBlock()` (Task 18) with a matching instruction: append to the format rules `"DEDUPLICATION: If the user context mentions previously recommended places, do NOT include them in pois or mention them in prose."` — this makes the prompt-side and injection-side both enforce deduplication. The `vibeContextBlock` is inserted into `buildChatSystemContext`'s `listOf(...)` at position after `intentBlock(intent)` and before `engagementBlock(engagementLevel)` — the full updated order is: `personaBlock, areaContextBlock, intentBlock, vibeContextBlock, engagementBlock, saveContextBlock, tasteProfileBlock, confidenceBlock, contextShiftBlock, outputFormatBlock, framingBlock`.
  - Action 5: Vibe-aware context — add `activeVibeName: String? = null` (optional, default null) to `buildChatSystemContext` signature. Updated signature: `fun buildChatSystemContext(areaName: String, pois: List<POI>, intent: ChatIntent, engagementLevel: EngagementLevel, saves: List<SavedPoi>, tasteProfile: TasteProfile, poiCount: Int, framingHint: String? = null, activeVibeName: String? = null): String`. Add a new private method `vibeContextBlock(vibeName: String?): String` — returns `"EXPLORATION CONTEXT: The user is currently viewing the '$vibeName' vibe. Open your response with content relevant to this vibe, but pivot freely if they change topic."` when non-null, `""` when null. Insert `vibeContextBlock(activeVibeName)` into the `listOf(...)` in `buildChatSystemContext` AFTER `intentBlock(intent)` and BEFORE `engagementBlock(engagementLevel)`. All existing call sites pass no `activeVibeName` → default null → no change in behavior. Only `ChatViewModel.tapIntentPill` passes the active vibe: add `activeVibeName = activeDynamicVibe?.label` to that one call site.
  - Action 6: Update `ChatUiState.vibeName: String?` — already exists. Set it from `MapViewModel` when opening chat: pass `activeDynamicVibe?.label` to `ChatViewModel.openChat(vibeName: String?)`.

- [ ] **Task 20: MarkdownText composable**
  - File (new): `composeApp/src/commonMain/kotlin/com/harazone/ui/components/MarkdownText.kt`
  - Action: Create:
    ```kotlin
    @Composable
    fun MarkdownText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
        Text(text = parseMarkdown(text), modifier = modifier, style = style)
    }

    fun parseMarkdown(text: String): AnnotatedString = buildAnnotatedString {
        // Process line by line
        // For each line: detect "- " prefix → prepend "• "
        // Within each line: regex-scan for **bold**, *italic*, `code` spans
        // Apply SpanStyle(fontWeight = FontWeight.Bold) for **...**
        // Apply SpanStyle(fontStyle = FontStyle.Italic) for *...*
        // Apply SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22FFFFFF)) for `...`
        // On any exception in parsing: return AnnotatedString(stripMarkdown(text))
    }
    ```
  - Notes: Process in this strict order to avoid conflicts: (1) split text into lines, (2) for each line, replace leading `^- ` with `• ` (bullet), (3) scan line left-to-right for spans using regex — process `**bold**` FIRST (greedy), then `*italic*`, then `` `code` ``. Use non-overlapping match with `findAll` and track consumed character ranges. Nested markup (e.g., `***bold italic***`) is not supported — treat as plain text if neither pattern matches cleanly. HTML tags (`<b>`, `<i>`) are NOT processed — treat `<` and `>` as plain text characters. On any `Exception` during `buildAnnotatedString`, catch and return `AnnotatedString(text.replace(Regex("[*`]"), ""))` as strip fallback.
  - Action 2: Task 21 below handles wiring `MarkdownText` into `ChatOverlay.kt`.

- [ ] **Task 21: Wire MarkdownText into ChatOverlay**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt`
  - Action: Find every `Text(text = bubble.text, ...)` (or equivalent) call that renders a chat bubble's text content. Replace with `MarkdownText(text = bubble.text, modifier = Modifier, style = <existing text style>)`. Import `com.harazone.ui.components.MarkdownText`. No other changes to ChatOverlay.
  - Notes: This is an explicit task because ChatOverlay was NOT listed in the original files_to_modify. Do NOT replace Text calls in non-bubble contexts (e.g., headers, chip labels, input fields) — only the AI response bubble text.
  - iOS: After completing Task 21, build and run on iOS Simulator (`lli` command or Xcode) to verify `MarkdownText` renders bold/italic/code identically to Android. `AnnotatedString` with `SpanStyle` is fully supported in Compose Multiplatform iOS — no platform-specific workaround expected, but confirm visually before committing.

---

### Acceptance Criteria

#### Part 1 — Dynamic Vibes

- [ ] **AC 1 (Stage 1 — Vibe Generation):** Given the user navigates to an area, when Stage 1 Gemini response arrives (~3-5s), then the chip row shows 4-6 dynamic vibe chips with emoji and labels specific to that area (e.g., a beach town shows "🏄 Surf" not "⚙️ Character").

- [ ] **AC 2 (Quality Gate):** Given Gemini returns a vibe with fewer than 2 matching POIs, when the client-side gate runs, then that vibe is silently dropped and never shown in the chip row.

- [ ] **AC 3 (Skeleton Shimmer):** Given the user searches a new area, when Stage 1 is in-flight, then the chip row displays animated skeleton placeholder chips instead of the old static chips or an empty row.

- [ ] **AC 4 (Fallback):** Given Stage 1 Gemini call fails or returns empty vibes, when the error is handled, then a single "Exploring... " chip (with pulsing dot) is shown. Tapping it retries the area fetch.

- [ ] **AC 5 (Sticky Until Stale):** Given a user navigates between two areas rapidly, when new vibes arrive for the second area, then the chip row atomically replaces to the new vibes — it never shows a mix of old and new vibe chips mid-transition.

- [ ] **AC 6 (Offline Cache):** Given the device has no network connection, when the user opens a previously-visited area, then the chip row shows the cached vibes with a subtle "Offline · cached" indicator. Filter/selection still works on cached POIs.

- [ ] **AC 7 (Stage 1/2 Consistency):** Given Stage 1 returns vibes `["Street Art", "Craft Beer"]`, when Stage 2 enrichment prompt is sent, then the prompt includes `"Use EXACTLY these vibe labels: Street Art, Craft Beer"` and Stage 2 response uses those exact labels — chip labels never change between Stage 1 and Stage 2.

- [ ] **AC 8 (Count Badges):** Given a vibe chip "🎨 Street Art" and 6 POIs tagged with "Street Art", when the chip row renders, then the chip shows a count badge with "6". Badge updates when POI list changes (Stage 2 merge).

- [ ] **AC 9 (Pinned Vibes — Persist):** Given the user long-presses a vibe chip to pin it, when they navigate to a different area, then the pinned vibe chip appears first in the chip row (even if Gemini doesn't return that exact label for the new area). Pin indicator (📌) visible on chip.

- [ ] **AC 10 (Pinned Vibes — Max 3):** Given the user has 3 vibes pinned, when they attempt to pin a 4th, then the pin is rejected with a visible feedback message (snackbar/toast). Existing 3 pins are unchanged.

- [ ] **AC 11 (Priority Fill — 6 Slots):** Given pinned vibes = ["Street Art", "Craft Beer"], saved-POI vibes in viewport = ["Jazz Scene"] (user previously saved a jazz bar), and Gemini vibes = ["Street Art", "Craft Beer", "Live Music", "Markets", "History"], when the chip row renders, then the order is: 🎨 Street Art (pinned) → 🍺 Craft Beer (pinned) → 🔖 Jazz Scene (saved-POI, not in Gemini list) → 🎶 Live Music → 🛒 Markets → 🏛️ History (max 6; no 7th shown).

- [ ] **AC 12 (Saved-POI Vibe Persistence):** Given a user previously saved a POI tagged "Jazz Scene" in a viewport, when they return to that area and Gemini does not return "Jazz Scene" in its vibe list, then "🔖 Jazz Scene" still appears as a chip with the saved count badge.

- [ ] **AC 13 (Cold Start Picker — First Launch):** Given the app is launched for the first time (no `cold_start_seen` preference set), when the map loads, then a full-screen picker overlay is shown with 10 vibe archetype cards. User can select 1-3 and tap "Let's Go →" to set them as initial pinned vibes.

- [ ] **AC 14 (Cold Start Picker — Skip):** Given the cold start picker is shown, when the user taps "Skip" or Android back button, then the picker is dismissed, `cold_start_seen` is stored, and no pinned vibes are set. Picker never shows again.

- [ ] **AC 15 (Vibe-Aware Chat):** Given the user has "🎨 Street Art" vibe active and opens AI chat, when the chat system context is built, then it includes the vibe context line "User is exploring the 'Street Art' vibe 🎨 — open in that spirit, pivot freely if they change topic." The AI first response is Street Art-themed.

- [ ] **AC 16 (Vibe Context Reset):** Given a user deselects the active vibe chip (toggles it off), when they open chat, then no vibe context is injected into the system prompt and the AI response is topic-neutral.

- [ ] **AC 17 (Debounced Refresh):** Given the user pans the map slowly (<500m total movement), when the pan ends, then no vibe refresh is triggered (chips remain unchanged). Given the user pans 600m, then after 800ms debounce a vibe refresh triggers (skeleton shimmer appears).

- [ ] **AC 18 (Regression — VibesReady without crash):** Given Stage 1 Gemini response arrives with the new `{vibes, pois}` schema, when `parseStage1Response` runs, then `VibesReady` is emitted without crash and both vibes and POI pins appear on the map.

#### Part 2 — Chat Output Quality

- [ ] **AC 19 (Dual-Channel — Initial Turn):** Given the user taps an intent pill, when Gemini responds, then: (a) prose text renders in a chat bubble with NO raw asterisks visible, (b) POI cards appear as tappable cards below the bubble, (c) places mentioned in prose match exactly the POI cards shown.

- [ ] **AC 20 (Dual-Channel — Follow-Up Turn):** Given the user types a follow-up question after the initial intent, when Gemini responds, then the same dual-channel format applies: prose bubble + POI cards. Follow-up POI cards are NEW places not previously shown.

- [ ] **AC 21 (Follow-Up Context Injection):** Given Brick Lane and Shoreditch Box Park were recommended in turn 1, when the user asks "what else?" in turn 2, then Gemini's response does NOT mention Brick Lane or Shoreditch Box Park again. New places appear in turn 2 POI cards.

- [ ] **AC 22 (Markdown Rendering — Bold):** Given Gemini response prose contains `**Brick Lane**`, when rendered in `MarkdownText`, then "Brick Lane" is displayed in bold weight with no asterisks visible.

- [ ] **AC 23 (Markdown Rendering — Italic):** Given prose contains `*hidden gem*`, when rendered, then "hidden gem" is italic. No asterisks visible.

- [ ] **AC 24 (Markdown Rendering — Code):** Given prose contains `` `@streetartlondon` ``, when rendered, then it displays in monospace with a faint background highlight. No backticks visible.

- [ ] **AC 25 (Markdown Rendering — Bullet):** Given prose contains `- First place\n- Second place`, when rendered, then each line is prefixed with "• " and indented. No "- " prefix visible.

- [ ] **AC 26 (Strip Fallback):** Given Gemini returns a response that is NOT valid JSON (e.g., a plain text error message with asterisks), when `parseChatResponse` fails JSON decode, then the prose is stripped of markdown symbols and rendered as clean plain text. No crash. No raw asterisks.

- [ ] **AC 27 (MarkdownText — Cross-Platform):** Given the app runs on both Android and iOS, when `MarkdownText` renders in chat, then bold/italic/code rendering works identically on both platforms (no expect/actual required).

---

## Additional Context

### Dependencies

- No new external libraries required
- SQLDelight migration 10 must run before first `area_vibe_cache` or `user_preferences` read/write (handled automatically by SQLDelight migration runner on first DB open)
- `AnnotatedString`, `SpanStyle`, `FontWeight`, `FontStyle`, `FontFamily` — all available in Compose Multiplatform `commonMain`
- Koin DI: `UserPreferencesRepository` must be added to the Koin module before `MapViewModel` can inject it

### Testing Strategy

**Unit Tests (run without device):**
- `GeminiResponseParserTest` — add: `parseStage1Response_parsesVibesAndPois`, `parseStage1Response_fallsBackToFlatArray`, `parseStage1Response_returnsEmptyOnMalformed`, `parseDynamicVibeResponse_parsesVibeContentAndPoiMultiVibe`, `parseChatResponse_parsesDualChannelJson`, `parseChatResponse_stripsMarkdownOnJsonFailure`
- `MarkdownTextTest` — test `parseMarkdown` function. Note: `buildAnnotatedString` and `AnnotatedString` are part of `compose.ui.text` which is a pure Kotlin/multiplatform data structure in Compose Multiplatform — it does NOT require a Compose runtime or UI context to instantiate. Tests can call `parseMarkdown("**bold**")` directly in a standard JUnit test class without Robolectric or `@Composable` context. Test cases: bold, italic, code, bullet, nested markup (expect graceful handling), strip fallback on exception.
- `MapViewModelTest` — add: `buildChipRowVibes_ordersPinnedFirst`, `buildChipRowVibes_appendsSavedPoiVibesNotInGeminiList`, `buildChipRowVibes_capsAtSix`, `qualityGate_dropsVibesWithFewerThanTwoPois`, `togglePin_enforcesMaxThree`

**Regression Tests (add to existing test files):**
- `vibesReadyEmitted_whenStage1ResponseArrives` — fails without Task 8, passes with it
- `chipRowShowsSkeleton_whenAreaChangeInFlight` — fails without `isLoadingVibes` in MapUiState, passes with it
- `coldStartPickerShown_onFirstLaunch` — fails without `showColdStartPicker` logic, passes with it
- `followUpResponseExcludesPreviouslyRecommendedPois` — fails without follow-up injection (Task 19), passes with it
- `allVibesFilteredByQualityGate_showsFallbackChip` — given Gemini returns only vibes with <2 POIs, when quality gate runs, then `dynamicVibes` is empty and `VibeRail` shows "Exploring..." chip (AC 4)
- `debounce_panUnder500m_noRefresh` — given viewport change of 400m, when 800ms passes, then no vibe re-fetch triggered; use `TestCoroutineScheduler` with `advanceTimeBy(900)`
- `debounce_panOver500m_refreshTriggered` — given viewport change of 600m, when 800ms passes, then `isLoadingVibes = true` and fetch triggered
- `offlineFlag_clearedWhenNetworkReturns` — given `isOfflineVibes = true`, when live `VibesReady(fromCache = false)` arrives, then `isOfflineVibes = false`
- `stage2PoiVibeMismatch_ignoredSilently` — given Stage 2 returns a POI tagged with vibe label "Unknown" (not in Stage 1 list), when merge runs, then POI is still included but `vibes` field excludes "Unknown"; no crash

**Manual Testing Checklist:**
1. Fresh install → cold start picker appears → select 3 vibes → confirm → chip row shows pinned vibes on first area load
2. Navigate to a beach area → chips are beach-specific (e.g., "🏄 Surf") not generic static vibes
3. Navigate to a historic city centre → chips are history-specific (e.g., "🏛️ Royal Heritage")
4. Pan map 600m+ → skeleton shimmer appears → new vibes load → chips update
5. Pan map <500m → no refresh (chips stable)
6. Turn off WiFi → open previously-visited area → cached vibes show with "Offline · cached" label
7. Long-press chip → pin it → navigate to new area → pinned chip appears first
8. Attempt to pin 4th chip → snackbar shown, no 4th pin added
9. Chat: tap intent pill → AI response shows prose in bubble (no asterisks) + POI cards below
10. Chat: type follow-up → new POI cards only (no repeats from turn 1)
11. Chat: test on iOS Simulator — bold/italic renders same as Android

### Notes

- Brainstorm source: `_bmad-output/brainstorming/brainstorming-session-2026-03-13-002.md`
- Ideas covered: Part 1: #1, #3, #4, #5, #6, #7, #8, #9, #10, #11, #12, #15, #19, #21, #27, #28, #33, #40, #41, #43, #44, #45, #62. Part 2: #64, #66, #68, #69, #70
- `BucketType` enum and `area_bucket_cache` table are NOT deleted — parallel migration only. Remove in a future sprint after all references migrated.
- `Vibe` enum also stays — still referenced in existing saved-places code and tests. Migration is additive.
- Old flat-array `area_poi_cache` cache-hit path: the existing `parsePinOnlyResponse` (flat JSON array) stays active for the old `PinsReady` cache-hit path. `parseStage1Response` (new object format) is ONLY called for fresh Gemini requests. Existing cached POI data in `area_poi_cache` is still valid — no migration or conversion needed. Both parsers coexist until the old cache naturally expires (3-day TTL).
- HIGH RISK: Stage 1 prompt change breaks any in-flight cached responses — the `parsePinOnlyResponse` fallback in Task 7 is the safety net. Verify it handles partial old-format responses correctly.
- HIGH RISK: `outputFormatBlock` change breaks ALL chat parsing simultaneously — the strip fallback in Task 19 is the safety net. Test with real Gemini before committing.
- The `MarkdownText` parser must NOT attempt to render raw HTML — Gemini occasionally outputs `<b>` tags; treat these as plain text (strip `<` `>` or pass through as-is).
- BACKLOG for Phase 2: emoji consistency cache (#42), vibe label dedup (#34), long-press menu (#52), intent pills adapt to vibe (#13)
