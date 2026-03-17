---
title: 'Companion Orb + Companion Card — Tier 1 Proactive AI Companion'
slug: 'companion-orb-card-tier1'
created: '2026-03-16'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Gemini API', 'Koin', 'SQLDelight']
files_to_modify:
  - composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/repository/UserPreferencesRepository.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FabMenu.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/profile/ProfileScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt
  - composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt
code_patterns:
  - UserPreferencesRepository key/value pattern (SQLDelight user_preferencesQueries.set/get)
  - GeminiAreaIntelligenceProvider non-streaming suspend pattern (see generatePoiContext)
  - ChatEntryPoint sealed class + pendingFramingHint dispatch in ChatViewModel.openChat()
  - Koin single {} registration in DataModule/UiModule
  - PlatformBackHandler for every dismissible overlay
  - AppClock interface (inject via Koin, use .nowMs())
  - haversineDistanceMeters() in com.harazone.util.GeoUtils.kt
test_patterns:
  - Given/When/Then in commonTest using MockK fakes
  - UserPreferencesRepository tested via fake in-memory impl
---

# Tech-Spec: Companion Orb + Companion Card — Tier 1 Proactive AI Companion

**Created:** 2026-03-16

---

## Overview

### Problem Statement

The FAB is a generic utility widget with no personality. The AI only speaks when asked. Users miss places near them, behavioural patterns forming in their saves, and moments worth a nudge — all because the app is silent unless prompted.

### Solution

Replace the FAB with a **Companion Orb** (warm gold/orange, always visible bottom-right). The orb dims when idle and glows+pulses when the AI has something to say. Tapping the orb slides up a **Companion Card** with the nudge. The user can dismiss or tap "Tell me more" to enter ChatOverlay pre-seeded with the nudge as context. Six Tier 1 nudges are implemented — all client-side, no backend, no push notifications.

### Scope

**In Scope:**
- `CompanionOrb` composable replacing `FabMenu` (always visible, quiet/active states)
- `CompanionCard` composable (slides up on orb tap, dismiss + "Tell me more")
- Priority nudge queue in `MapViewModel` (one nudge shown at a time)
- Six Tier 1 nudges:
  - **#8 Welcome Back Delta** — on session start, Gemini surfaces interesting change across saved POIs
  - **#13 Ambient Whisper** — after 18s idle, Gemini comments on current map view
  - **#17 Gentle Proximity Ping** — within 200m of a saved place, surface practical nudge
  - **#34 Instant Neighbor** — on save, suggest 1 nearby same-vibe unvisited POI
  - **#37 Anticipation Seed** — when WANT_TO_GO save occurs, companion promises to watch
  - **#39 Vibe Profile Reveal** — at milestones (5/10/20 saves in same vibe), reveal the pattern
- Persistent rate limiting via `UserPreferencesRepository` (SQLDelight key/value)
- `generateCompanionNudge()` added to `AreaIntelligenceProvider` + Gemini impl (for #8 and #13 only)
- "Tell me more" opens `ChatOverlay` via `ChatEntryPoint.CompanionNudge` (new entry point)
- `PlatformBackHandler` on `CompanionCard` (dismiss on back press)
- Settings migration: gear icon in `ProfileScreen` header, remove Settings from FAB path
- Remove `isFabExpanded` state entirely (FAB replaced, expand menu gone)

**Out of Scope:**
- Local notifications (T4 time-based triggers) — Tier 2
- Tier 2 and Tier 3 nudges (require visit history or weather API)
- Companion personality setting toggles (humor, wellness, history) — post-MVP
- Background geofencing — foreground GPS updates only for beta
- Spring animation from orb position to card origin

---

## Context for Development

### Codebase Patterns

**UserPreferencesRepository** (`data/repository/UserPreferencesRepository.kt`):
Uses SQLDelight `user_preferencesQueries.set(key, value)` / `.get(key).executeAsOneOrNull()`. All values are strings. Existing keys: `"cold_start_seen"`, `"pinned_vibes"`. Pattern: parse JSON for complex types (see `setPinnedVibes` / `getPinnedVibes` using `Json + ListSerializer`).

**AppClock** (`util/AppClock.kt`):
```kotlin
interface AppClock { fun nowMs(): Long }
class SystemClock : AppClock { override fun nowMs() = Clock.System.now().toEpochMilliseconds() }
```
Inject via Koin `get<AppClock>()`. Use `.nowMs()` for all timestamps. No kotlinx-datetime in deps — use epoch math for date comparison (see proximity key design in T8).

**haversineDistanceMeters** (`util/GeoUtils.kt`):
`fun haversineDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double`
Already used in `MapViewModel`. Returns metres as a Double.

**GeminiAreaIntelligenceProvider non-streaming pattern** (`data/remote/GeminiAreaIntelligenceProvider.kt`):
See `generatePoiContext()` at line ~346 — makes a single non-streaming POST to Gemini, returns a result or null on failure. Use this exact pattern for `generateCompanionNudge()`.

**ChatEntryPoint + pendingFramingHint dispatch** (`ui/map/ChatEntryPoint.kt`, `ChatViewModel.kt`):
`ChatEntryPoint` is a sealed class. `ChatViewModel.openChat()` maps each entry point to a `pendingFramingHint: String?` via `when (entryPoint)` block. This hint is injected into the first Gemini chat request. Adding a new variant = add one `data class` to the sealed class and one `is` branch in `openChat()`.

**Koin DI** (`di/DataModule.kt`, `di/UiModule.kt`):
`DataModule` registers data/domain services. `UiModule` registers ViewModels. `CompanionNudgeEngine` belongs in `DataModule` as `single { ... }`. `MapViewModel` gets it via constructor injection.

**FabMenu replacement pattern**: `FabMenu.kt` currently lives at `ui/map/components/FabMenu.kt`. The new `CompanionOrb.kt` and `CompanionCard.kt` replace it. Delete `FabMenu.kt` entirely after wiring.

**PlatformBackHandler ownership**: `PlatformBackHandler` for CompanionCard lives in MapScreen (T14d), NOT inside CompanionCard. This keeps it in the correct priority chain. CompanionCard is a dumb composable — it has no back handler inside it.

**R09 — `visitedPoiIds` already exists**: `MapUiState.Ready` already has `val visitedPoiIds: Set<String> = emptySet()` (line 39). T5 does not need to add it. T10k references it without modification — this is correct.

**Idle detection in MapScreen**: Use `Modifier.pointerInput(Unit) { awaitPointerEventScope { while(true) { awaitPointerEvent(); reset timer } } }` on the outer Box. Combine with `LaunchedEffect(timerKey)` that delays 18s then calls `viewModel.onIdleDetected()`. Reset by bumping `timerKey`.

### Files to Reference

| File | Purpose |
|------|---------|
| `ui/map/components/FabMenu.kt` | FAB to remove — replace with CompanionOrb |
| `ui/map/MapUiState.kt` | Add companion state fields, remove isFabExpanded |
| `ui/map/MapViewModel.kt` | Integrate engine + queue + idle hook |
| `ui/map/ChatEntryPoint.kt` | Add CompanionNudge variant |
| `ui/map/ChatViewModel.kt` | Add framing hint for CompanionNudge entry point |
| `ui/map/MapScreen.kt` | Wire orb, card, idle detection, back handler |
| `domain/provider/AreaIntelligenceProvider.kt` | Add generateCompanionNudge() to interface |
| `data/remote/GeminiAreaIntelligenceProvider.kt` | Implement generateCompanionNudge() |
| `data/repository/UserPreferencesRepository.kt` | Add rate-limit key helpers |
| `domain/model/SavedPoi.kt` | Reference for fields available to nudge logic |
| `util/GeoUtils.kt` | haversineDistanceMeters() for proximity ping |
| `util/AppClock.kt` | AppClock interface + SystemClock impl |
| `ui/profile/ProfileScreen.kt` | Add gear icon for settings access |
| `di/DataModule.kt` | Register CompanionNudgeEngine |
| `di/UiModule.kt` | Inject CompanionNudgeEngine into MapViewModel |

### Technical Decisions

1. **Nudge queue lives in MapViewModel** (not in state). Only the current head nudge and `isCompanionPulsing` flag live in `MapUiState.Ready`. Queue is an `ArrayDeque<CompanionNudge>` in the ViewModel.

2. **Priority order (highest first)**: `PROXIMITY > RELAUNCH_DELTA > VIBE_REVEAL > AMBIENT_WHISPER > ANTICIPATION_SEED > INSTANT_NEIGHBOR`. On enqueue, insert at the correct priority slot.

3. **Gemini only for #8 and #13**. #17, #34, #37, #39 are pure client-side with static/template copy. This keeps Gemini call count low and avoids latency on saves.

4. **Proximity ping daily key**: No kotlinx-datetime in deps. Use epoch day: `dayKey = clock.nowMs() / 86_400_000L`. Key = `"${poi.id}:$dayKey"`. One ping per saved POI per UTC day. Store as single string (last fired key only — not a set, since only one POI fires at a time).

5. **Welcome Back Delta rate limit**: 6-hour minimum interval (`6 * 3_600_000L` ms). Fired on first non-empty `visitedPois` observation in MapViewModel `init` coroutine. Gate: `clock.nowMs() - lastDeltaShownAt >= 6h`.

6. **Ambient Whisper once per area**: Key = area name string. Store in `"companion_whisper_area"`. Whisper fires only if current areaName ≠ stored value. On fire, store new areaName.

7. **Vibe Reveal milestones**: Milestones = [5, 10, 20]. Key format = `"vibe:milestone"` e.g. `"coffee:5"`. Stored as JSON-serialised `Set<String>` in `"companion_vibe_milestones"`. Uses existing `Json + SetSerializer(String.serializer())` pattern.

8. **Instant Neighbor matching**: Match by `POI.type == SavedPoi.type` (not `vibe`, which may differ between POI and SavedPoi). Filter out already-visited POIs. Take the nearest by haversine from the just-saved POI's lat/lng.

9. **Idle detection resets on ANY pointer event** (tap, scroll, drag) anywhere on the map screen Box. The `LaunchedEffect` key is a `Long` timestamp bumped on each pointer event. After 18s delay completes without a new key, calls `viewModel.onIdleDetected()`.

10. **Settings migration**: `FabMenu`'s Settings item is removed. `ProfileScreen` gets a new `onShowSettings: () -> Unit` parameter. A gear `IconButton` is added to the profile header (top-right, beside the close X). MapScreen passes `onShowSettings = { showSettings = true }` to ProfileScreen. MapScreen keeps its own `showSettings` state + `SettingsSheet` call unchanged — just removes the FAB-triggered path.

11. **`isFabExpanded` removal**: Removed from `MapUiState.Ready`. `toggleFab()` removed from MapViewModel. `PlatformBackHandler` for FAB expand removed from MapScreen. `!state.isFabExpanded` guard removed from `SavesNearbyPill` visibility condition. No other consumer.

---

## Implementation Plan

### Tasks

Tasks are ordered lowest-dependency first. Each task is complete before starting the next.

---

#### T1 — UserPreferencesRepository: add companion rate-limit keys

**File**: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/UserPreferencesRepository.kt`

Add the following methods using the existing `getPreference(key)` / `db!!.user_preferencesQueries.set(key, value)` pattern:

```kotlin
// Welcome Back Delta — timestamp (Long as string)
open fun getLastDeltaShownAt(): Long =
    getPreference("companion_delta_at")?.toLongOrNull() ?: 0L

open fun setLastDeltaShownAt(ts: Long) {
    db!!.user_preferencesQueries.set("companion_delta_at", ts.toString())
}

// Proximity Ping — "poiId:dayKey" composite string
open fun getLastProximityPingKey(): String =
    getPreference("companion_proximity_key") ?: ""

open fun setLastProximityPingKey(key: String) {
    db!!.user_preferencesQueries.set("companion_proximity_key", key)
}

// Vibe Milestones — JSON Set<String>, e.g. {"coffee:5","food:5","coffee:10"}
open fun getVibeMilestonesSeenSet(): Set<String> {
    val raw = getPreference("companion_vibe_milestones") ?: return emptySet()
    return try {
        json.decodeFromString(SetSerializer(String.serializer()), raw)
    } catch (_: Exception) { emptySet() }
}

open fun setVibeMilestonesSeenSet(milestones: Set<String>) {
    db!!.user_preferencesQueries.set(
        "companion_vibe_milestones",
        json.encodeToString(SetSerializer(String.serializer()), milestones)
    )
}

// Ambient Whisper — last area name where whisper was shown
open fun getWhisperShownForArea(): String =
    getPreference("companion_whisper_area") ?: ""

open fun setWhisperShownForArea(area: String) {
    db!!.user_preferencesQueries.set("companion_whisper_area", area)
}
```

Note: `SetSerializer` is `kotlinx.serialization.builtins.SetSerializer` — already available in project.

---

#### T2 — Domain model: CompanionNudge + NudgeType

**New file**: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/CompanionNudge.kt`

```kotlin
package com.harazone.domain.model

enum class NudgeType {
    PROXIMITY,          // #17 — near a saved place
    RELAUNCH_DELTA,     // #8  — interesting fact on session start
    VIBE_REVEAL,        // #39 — pattern milestone
    AMBIENT_WHISPER,    // #13 — idle commentary on current view
    ANTICIPATION_SEED,  // #37 — WANT_TO_GO save acknowledgment
    INSTANT_NEIGHBOR,   // #34 — nearby same-vibe suggestion on save
}

data class CompanionNudge(
    val type: NudgeType,
    val text: String,
    val chatContext: String = text,
)
```

---

#### T3 — AreaIntelligenceProvider: add generateCompanionNudge()

**File**: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt`

Add to the interface using plain strings — **do not import NudgeType** (R07: keeps the domain provider free of companion-feature coupling):

```kotlin
// promptType: "relaunch_delta" | "ambient_whisper"
suspend fun generateCompanionNudge(
    promptType: String,
    context: String,
    languageTag: String = "en",
): String?
```

---

#### T4 — GeminiAreaIntelligenceProvider: implement generateCompanionNudge()

**File**: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`

Add method following the exact pattern of `generatePoiContext()` (non-streaming POST, return null on failure):

```kotlin
override suspend fun generateCompanionNudge(
    promptType: String,
    context: String,
    languageTag: String,
): String? {
    // R01: guard — only "relaunch_delta" and "ambient_whisper" are valid callers
    val prompt = buildCompanionNudgePrompt(promptType, context, languageTag) ?: return null
    return try {
        val apiKey = apiKeyProvider.geminiApiKey
        // ... POST to Gemini (same httpClient pattern as generatePoiContext)
        // Return trimmed text response or null
    } catch (e: Exception) {
        AppLogger.e(e) { "GeminiAreaIntelligenceProvider: generateCompanionNudge failed" }
        null
    }
}

private fun buildCompanionNudgePrompt(promptType: String, context: String, languageTag: String): String? {
    val langInstruction = if (languageTag != "en") "Respond in the language with tag: $languageTag." else ""
    val toneInstruction = "You are a thoughtful travel companion. Write 1-2 sentences. Evocative but honest. Never hype. Never use exclamation marks."
    return when (promptType) {
        "relaunch_delta" ->
            "$toneInstruction $langInstruction\nThe user has these saved places: $context.\nSurface one genuinely interesting observation or fact about any of them. Make it feel like you've been keeping watch."
        "ambient_whisper" ->
            "$toneInstruction $langInstruction\n$context\nThe user is looking at the map. Say something brief and evocative about this place or what's visible — like a knowledgeable friend leaning over their shoulder."
        else -> null  // unknown type — caller guard prevents this in practice
    }
}
```

---

#### T5 — MapUiState: add companion fields, remove isFabExpanded

**File**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`

In `data class Ready`:

1. **Remove**: `val isFabExpanded: Boolean = false`
2. **Add** (after existing fields):
   ```kotlin
   val companionNudge: CompanionNudge? = null,
   val isCompanionPulsing: Boolean = false,
   val autoSlideshowIndex: Int? = null,   // null = inactive, Int = current slide index
   ```

Import: `com.harazone.domain.model.CompanionNudge`

---

#### T6 — ChatEntryPoint: add CompanionNudge variant

**File**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatEntryPoint.kt`

Add to sealed class:
```kotlin
data class CompanionNudge(val nudgeText: String) : ChatEntryPoint()
```

---

#### T7 — ChatViewModel: handle CompanionNudge framing

**File**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`

In `openChat()`, in the `pendingFramingHint = when (entryPoint)` block, add:
```kotlin
is ChatEntryPoint.CompanionNudge ->
    "Continue this thought as the user's travel companion: \"${entryPoint.nudgeText}\". Pick up naturally — expand on it, offer something connected, invite the user deeper."
```

---

#### T8 — CompanionNudgeEngine: new domain service

**New file**: `composeApp/src/commonMain/kotlin/com/harazone/domain/companion/CompanionNudgeEngine.kt`

```kotlin
package com.harazone.domain.companion

import com.harazone.data.repository.UserPreferencesRepository
import com.harazone.domain.model.CompanionNudge
import com.harazone.domain.model.NudgeType
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.util.AppClock
import com.harazone.util.haversineDistanceMeters

class CompanionNudgeEngine(
    private val prefs: UserPreferencesRepository,
    private val aiProvider: AreaIntelligenceProvider,
    private val clock: AppClock,
) {
    companion object {
        private const val DELTA_MIN_INTERVAL_MS = 6 * 3_600_000L // 6 hours
        private const val PROXIMITY_THRESHOLD_M = 200.0
        private val VIBE_MILESTONES = listOf(5, 10, 20)
    }

    // #8 Welcome Back Delta — call on session start when savedPois first loads
    suspend fun checkRelaunched(savedPois: List<SavedPoi>, languageTag: String): CompanionNudge? {
        if (savedPois.size < 3) return null  // R11: too thin for meaningful Gemini observation
        if (clock.nowMs() - prefs.getLastDeltaShownAt() < DELTA_MIN_INTERVAL_MS) return null
        val summary = savedPois.take(8).joinToString { "${it.name} (${it.vibe}, ${it.areaName})" }
        val text = aiProvider.generateCompanionNudge("relaunch_delta", summary, languageTag)  // R07
            ?: return null
        prefs.setLastDeltaShownAt(clock.nowMs())
        return CompanionNudge(NudgeType.RELAUNCH_DELTA, text)
    }

    // #17 Gentle Proximity Ping — call on each GPS update
    fun checkProximity(gpsLat: Double, gpsLng: Double, savedPois: List<SavedPoi>): CompanionNudge? {
        val nearest = savedPois.minByOrNull {
            haversineDistanceMeters(gpsLat, gpsLng, it.lat, it.lng)
        } ?: return null
        val dist = haversineDistanceMeters(gpsLat, gpsLng, nearest.lat, nearest.lng)
        if (dist > PROXIMITY_THRESHOLD_M) return null
        val dayKey = clock.nowMs() / 86_400_000L // UTC day number
        val key = "${nearest.id}:$dayKey"
        if (prefs.getLastProximityPingKey() == key) return null
        prefs.setLastProximityPingKey(key)
        val distText = if (dist < 50) "${dist.toInt()}m" else "${((dist / 50.0).roundToInt() * 50)}m"  // R08: round, not floor
        val text = "${nearest.name} — you're $distText away."
        return CompanionNudge(NudgeType.PROXIMITY, text, "Tell me about ${nearest.name}.")
    }

    // #34 Instant Neighbor — call after visitPoi() saves a new place
    fun checkInstantNeighbor(
        savedPoi: SavedPoi,
        allPois: List<POI>,
        visitedIds: Set<String>,
    ): CompanionNudge? {
        // R05: POI.savedId exists (verified: MapScreen line 514 uses allDiscoveredPois.count { it.savedId in visitedPoiIds })
        // but may be empty string for newly discovered unsaved POIs — guard with isNotEmpty()
        // Use name != savedPoi.name to exclude the POI just saved (avoids ID cross-type comparison)
        val neighbor = allPois
            .filter { it.name != savedPoi.name && (it.savedId.isEmpty() || it.savedId !in visitedIds) && it.type == savedPoi.type }
            .minByOrNull { haversineDistanceMeters(savedPoi.lat, savedPoi.lng, it.latitude, it.longitude) }
            ?: return null
        val text = "There's also ${neighbor.name} nearby — same vibe as ${savedPoi.name}."
        return CompanionNudge(NudgeType.INSTANT_NEIGHBOR, text, "Tell me about ${neighbor.name}.")
    }

    // #37 Anticipation Seed — call when new save resolves to WANT_TO_GO
    fun makeAnticipationSeed(poi: POI): CompanionNudge =
        CompanionNudge(
            NudgeType.ANTICIPATION_SEED,
            "I'll keep an eye on ${poi.name} for you — looks worth the trip.",
            "What should I know about ${poi.name} before I go?",
        )

    // #13 Ambient Whisper — call after idle threshold exceeded
    suspend fun checkAmbientWhisper(
        areaName: String,
        visiblePois: List<POI>,
        languageTag: String,
    ): CompanionNudge? {
        if (visiblePois.isEmpty()) return null
        if (prefs.getWhisperShownForArea() == areaName) return null
        val poisSummary = visiblePois.take(5).joinToString { it.name }
        val context = "Area: $areaName. Places visible: $poisSummary."
        val text = aiProvider.generateCompanionNudge("ambient_whisper", context, languageTag)  // R07
            ?: return null
        prefs.setWhisperShownForArea(areaName)
        return CompanionNudge(NudgeType.AMBIENT_WHISPER, text)
    }

    // #39 Vibe Profile Reveal — call after each save to check pattern milestones
    fun checkVibeReveal(savedPois: List<SavedPoi>): CompanionNudge? {
        val seen = prefs.getVibeMilestonesSeenSet()
        val vibeCounts = savedPois
            .filter { it.vibe.isNotBlank() }
            .groupingBy { it.vibe }
            .eachCount()
        // R12: iterate milestones descending so a user with 10 saves gets "10 spots" not "5 spots"
        // Mark all lower milestones as seen too, to avoid follow-up "5 spots" message later
        for ((vibe, count) in vibeCounts) {
            for (milestone in VIBE_MILESTONES.sortedDescending()) {
                val key = "$vibe:$milestone"
                if (count >= milestone && key !in seen) {
                    // Mark this milestone and all lower ones as seen
                    val allKeysToMark = VIBE_MILESTONES.filter { it <= milestone }.map { "$vibe:$it" }.toSet()
                    prefs.setVibeMilestonesSeenSet(seen + allKeysToMark)
                    val text = "You've saved $milestone ${vibe.lowercase()} spots. I'm starting to know your vibe."
                    return CompanionNudge(NudgeType.VIBE_REVEAL, text)
                }
            }
        }
        return null
    }
}
```

---

#### T9 — Koin DI: register CompanionNudgeEngine

**File**: `composeApp/src/commonMain/kotlin/com/harazone/di/DataModule.kt`

Add to `dataModule`:
```kotlin
single { CompanionNudgeEngine(get(), get(), get()) }
```

Imports: `com.harazone.domain.companion.CompanionNudgeEngine`

**File**: `composeApp/src/commonMain/kotlin/com/harazone/di/UiModule.kt`

R06: Explicitly update the `MapViewModel` Koin registration to add both new constructor params:
```kotlin
// Before (example):
viewModel { MapViewModel(get(), get(), get(), get()) }
// After — append get() for CompanionNudgeEngine and get() for LocaleProvider:
viewModel { MapViewModel(get(), get(), get(), get(), get<CompanionNudgeEngine>(), get<LocaleProvider>()) }
```
The exact position of the new `get()` calls must match the constructor parameter order defined in T10a and T10g. Verify by counting existing `get()` calls against the current constructor signature before editing.

---

#### T10 — MapViewModel: integrate engine, queue, idle detection

**File**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`

**10a — Constructor**: Add `private val companionEngine: CompanionNudgeEngine` as a constructor parameter.

**10b — Queue and priority order**:
```kotlin
private val nudgeQueue = ArrayDeque<CompanionNudge>()
private val NUDGE_PRIORITY = listOf(
    NudgeType.PROXIMITY,
    NudgeType.RELAUNCH_DELTA,
    NudgeType.VIBE_REVEAL,
    NudgeType.AMBIENT_WHISPER,
    NudgeType.ANTICIPATION_SEED,
    NudgeType.INSTANT_NEIGHBOR,
)
```

**10c — enqueueNudge()**:
```kotlin
private fun enqueueNudge(nudge: CompanionNudge) {
    val insertIndex = nudgeQueue.indexOfFirst {
        NUDGE_PRIORITY.indexOf(it.type) > NUDGE_PRIORITY.indexOf(nudge.type)
    }.takeIf { it >= 0 } ?: nudgeQueue.size
    nudgeQueue.add(insertIndex, nudge)
    val current = _uiState.value as? MapUiState.Ready ?: return
    if (current.companionNudge == null) {
        _uiState.value = current.copy(isCompanionPulsing = true)
    }
}
```

**10d — showCompanionCard()**:

N3: Check queue first — only stop slideshow if a nudge actually exists to show. An orb tap on an empty queue (quiet orb) must not silently kill the slideshow.

```kotlin
fun showCompanionCard() {
    val nudge = nudgeQueue.removeFirstOrNull() ?: return  // N3: check queue before any side effects
    stopAutoSlideshow()  // R17: pause slideshow while user reads nudge — resumes only after next idle
    val current = _uiState.value as? MapUiState.Ready ?: return
    _uiState.value = current.copy(companionNudge = nudge, isCompanionPulsing = false)
}
```

**10e — dismissCompanionCard()**:
```kotlin
fun dismissCompanionCard() {
    val current = _uiState.value as? MapUiState.Ready ?: return
    _uiState.value = current.copy(
        companionNudge = null,
        isCompanionPulsing = nudgeQueue.isNotEmpty(),
    )
}
```

**10f — onIdleDetected()**:

R14: Slideshow and ambient whisper are mutually exclusive — both are idle discovery experiences. When the carousel is visible, slideshow IS the experience. When it's not, whisper fires instead. This prevents the orb pulsing mid-scroll (R20 resolved by same fix).

```kotlin
fun onIdleDetected() {
    val current = _uiState.value as? MapUiState.Ready ?: return
    if (current.pois.isEmpty() || current.isSearchingArea) return
    val carouselVisible = !current.showListView && current.selectedPoi == null
    if (carouselVisible) {
        // Slideshow is the idle discovery experience — whisper would clash (R14)
        startAutoSlideshow()
    } else {
        // No carousel — ambient whisper is the idle discovery experience
        viewModelScope.launch {
            companionEngine.checkAmbientWhisper(
                areaName = current.areaName,
                visiblePois = current.pois,
                languageTag = localeProvider.languageTag,
            )?.let { enqueueNudge(it) }
        }
    }
}
```

**10m — startAutoSlideshow()**:

Cycle order: card 0 → card 1 → ... → card N-1 → card 0 (continuous loop, no cards skipped).
Start from the card AFTER the current position (or card 0 if no current position).
Each step: update `autoSlideshowIndex` → pan camera → wait 3.5s → advance.
Loop is infinite — only `stopAutoSlideshow()` / job cancellation exits it.

R15 + R23: Use `try/finally` to guarantee `autoSlideshowIndex` is cleared and `slideshowJob` is nulled whenever the coroutine body exits (break, cancel, or any exception).
R16: Guard against starting when user has made a deliberate pin selection (`selectedPinIndex != null`).
N2: `selectedPinIndex: Int?` confirmed at `MapUiState.Ready` line 52. `flyToCoords(lat: Double, lng: Double)` confirmed at `MapViewModel` line 153 (already used by the "show on map" feature). No new code needed.

```kotlin
private var slideshowJob: Job? = null

private fun startAutoSlideshow() {
    val current = _uiState.value as? MapUiState.Ready ?: return
    if (current.selectedPinIndex != null) return  // R16: don't override deliberate pin selection
    slideshowJob?.cancel()
    // O3: capture job reference locally so finally block can compare — prevents a rapid
    // cancel+relaunch race where the old job's finally nulls the new job's reference.
    var thisJob: Job? = null
    thisJob = viewModelScope.launch {
        var index = (current.autoSlideshowIndex?.plus(1)) ?: 0
        try {
            while (true) {
                val state = _uiState.value as? MapUiState.Ready ?: break
                if (state.pois.isEmpty() || state.showListView || state.selectedPoi != null) break
                index = index % state.pois.size   // wrap: last card loops back to card 0
                val poi = state.pois[index]
                _uiState.value = state.copy(autoSlideshowIndex = index)
                flyToCoords(poi.latitude, poi.longitude)
                delay(3_500L)
                index++
            }
        } finally {
            // R15: always clear state on exit (break, cancel, or exception)
            // R23 + O3: only null slideshowJob if this is still the current job
            (_uiState.value as? MapUiState.Ready)?.let { s ->
                if (s.autoSlideshowIndex != null) _uiState.value = s.copy(autoSlideshowIndex = null)
            }
            if (slideshowJob === thisJob) slideshowJob = null
        }
    }
    slideshowJob = thisJob
}
```

**10n — stopAutoSlideshow()**:
```kotlin
fun stopAutoSlideshow() {
    slideshowJob?.cancel()
    slideshowJob = null
    val current = _uiState.value as? MapUiState.Ready ?: return
    if (current.autoSlideshowIndex != null) {
        _uiState.value = current.copy(autoSlideshowIndex = null)
    }
}
```
```

**10g — LocaleProvider**: MapViewModel does not currently inject `LocaleProvider`. Add it as a constructor param: `private val localeProvider: LocaleProvider`. Register `get()` in UiModule. Import: `com.harazone.domain.provider.LocaleProvider`.

**10h — Remove toggleFab()**: Delete the `toggleFab()` function entirely (it referenced `isFabExpanded` which is now removed).

**10i — Welcome Back Delta (on savedPois first load)**:
In the coroutine that observes `savedPoiRepository` (where `visitedPois` is set), after first non-empty emit, add:
```kotlin
if (latestSavedPois.isNotEmpty()) {
    viewModelScope.launch {
        companionEngine.checkRelaunched(latestSavedPois, localeProvider.languageTag)
            ?.let { enqueueNudge(it) }
    }
}
```
Gate: use a `var deltaCheckFired = false` flag in the ViewModel to ensure it only fires once per session (not on every repository update).

**10j — Proximity check (on meaningful location refresh only)**:
Do NOT call `checkProximity` on every raw GPS event. The app already gates location processing with `STALE_REFRESH_THRESHOLD_MS = 1 hour` and `DISTANCE_REFRESH_THRESHOLD_M = 100m` (MapViewModel constants). The proximity check must fire only when `isStale || hasMovedSignificantly` is true — i.e., inside the branch that proceeds past the `if (isSameArea && !isStale && !hasMovedSignificantly) { return }` guard (around line 740).

Place the call at the point where a meaningful location update is confirmed — after `hasMovedSignificantly` or `isStale` evaluates to true, before the area fetch kicks off:
```kotlin
if (isStale || hasMovedSignificantly) {
    val savedPois = (_uiState.value as? MapUiState.Ready)?.visitedPois ?: emptyList()
    companionEngine.checkProximity(coords.latitude, coords.longitude, savedPois)
        ?.let { enqueueNudge(it) }
}
```
This keeps proximity evaluation consistent with the app's own location refresh cadence and avoids firing on every GPS jitter.

**10k — Post-save nudges (in visitPoi())**:
After the state update that adds the new saved POI (after line ~239), add:
```kotlin
viewModelScope.launch {
    // Instant Neighbor
    companionEngine.checkInstantNeighbor(savedPoiObj, current.allDiscoveredPois, current.visitedPoiIds)
        ?.let { enqueueNudge(it) }
    // Vibe Reveal
    val updatedPois = current.visitedPois + savedPoiObj
    companionEngine.checkVibeReveal(updatedPois)?.let { enqueueNudge(it) }
    // Anticipation Seed (only for WANT_TO_GO)
    if (visitState == VisitState.WANT_TO_GO) {
        enqueueNudge(companionEngine.makeAnticipationSeed(poi))
    }
}
```

**10l — Remove all isFabExpanded references**: Search for `isFabExpanded` in MapViewModel, remove any remaining `.copy(isFabExpanded = ...)` calls.

---

#### T11 — CompanionOrb composable

**New file**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/CompanionOrb.kt`

```kotlin
package com.harazone.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// R13: Icons.Default.Star confirmed available in standard material-icons. AutoAwesome requires
// material-icons-extended which is not in project deps — use Star instead.

private val OrbGold = Color(0xFFFFD54F)
private val OrbOrange = Color(0xFFFF9800)
private val OrbQuietAlpha = 0.50f

@Composable
fun CompanionOrb(
    isPulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_scale",
    )
    val scale = if (isPulsing) pulseScale else 1f
    val gradientColors = if (isPulsing) {
        listOf(OrbGold, OrbOrange)
    } else {
        listOf(OrbGold.copy(alpha = OrbQuietAlpha), OrbOrange.copy(alpha = OrbQuietAlpha))
    }

    // R10: scale applied on outer Box so hit target expands with the pulse animation.
    // clip + background applied on inner Box so visual shape stays circular.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .scale(scale),  // outer — expands hit target
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(gradientColors))
                .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Companion",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
```

---

#### T12 — CompanionCard composable

**New file**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/CompanionCard.kt`

```kotlin
package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.CompanionNudge
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.ui.theme.TextPrimary
import com.harazone.ui.theme.TextSecondary

// R02: No full-screen scrim — it would block map pan/zoom/pin taps while the card is open.
// CompanionCard is a lightweight bottom card; dismiss via X button or Android back only.
// R04: PlatformBackHandler is NOT inside CompanionCard — it lives in MapScreen (T14d) to
// stay in the correct priority chain (list view > show all > POI card > companion card).
@Composable
fun CompanionCard(
    nudge: CompanionNudge,
    onTellMeMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Card — anchored to bottom, no scrim
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFFF5F2EF))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "✦",
                        fontSize = 16.sp,
                        color = Color(0xFFFF9800),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = nudge.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onTellMeMore,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Tell me more →", color = Color(0xFFFF9800))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
```

---

#### T13 — ProfileScreen: add gear icon for settings

**File**: `composeApp/src/commonMain/kotlin/com/harazone/ui/profile/ProfileScreen.kt`

1. Add `onShowSettings: () -> Unit` to `ProfileScreen()` parameters (after `onDismiss`).
2. Locate the profile header row that contains the close (X) button. Add a gear `IconButton` to its trailing end:
   ```kotlin
   IconButton(onClick = onShowSettings) {
       Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
   }
   ```
3. In `MapScreen.kt`, update the `ProfileScreen(...)` call to pass `onShowSettings = { showSettings = true }`.

---

#### T14 — MapScreen: wire everything up

**File**: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

**14a — Remove FAB-related code**:
- Delete the `FabScrim(...)` block (lines ~528-532).
- Delete the `FabMenu(...)` block (lines ~535-553).
- Delete the `savedFabOffset` variable declaration and its `onGloballyPositioned` usage.
- Remove `PlatformBackHandler(enabled = state.selectedPoi == null && !state.visitedFilter && state.isFabExpanded)` block.
- In `SavesNearbyPill` `AnimatedVisibility` visibility condition, remove `&& !state.isFabExpanded`.
- Remove the `showSettings` state and `SettingsSheet` call from MapScreen (settings is now accessed via ProfileScreen's gear icon only). Pass `onShowSettings = { showSettings = true }` to `ProfileScreen` (see T13) and keep `showSettings` state + `SettingsSheet` call here.

  Actually: **keep** `showSettings` state in MapScreen (it's the owner). **Remove** the FAB-triggered path only. The flow: ProfileScreen gear icon → `onShowSettings()` callback → `showSettings = true` in MapScreen → `SettingsSheet`.

**14b — Add CompanionOrb**:

Replace the removed `FabMenu(...)` block with:
```kotlin
if (!showProfile) {
    CompanionOrb(
        isPulsing = state.isCompanionPulsing || state.companionNudge != null,
        onClick = { viewModel.showCompanionCard() },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = navBarPadding + 16.dp, end = 16.dp),
    )
}
```

**14c — Add CompanionCard**:

In the composable (after the orb, before or after the AI detail page section), add:
```kotlin
if (state.companionNudge != null && !showProfile) {
    CompanionCard(
        nudge = state.companionNudge,
        onTellMeMore = {
            val nudge = state.companionNudge
            viewModel.dismissCompanionCard()
            chatViewModel.openChat(
                areaName = state.areaName,
                pois = state.pois,
                activeDynamicVibe = state.activeDynamicVibe,
                entryPoint = ChatEntryPoint.CompanionNudge(nudge.chatContext),
                forceReset = true,
            )
        },
        onDismiss = { viewModel.dismissCompanionCard() },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navBarPadding),
    )
}
```

**14d — Back handler for CompanionCard**:

In the back handler priority list (currently: list view > show all > POI card > FAB), replace FAB back handler with CompanionCard:
```kotlin
PlatformBackHandler(enabled = state.companionNudge != null && !showProfile) {
    viewModel.dismissCompanionCard()
}
```

**14e — Idle detection**:

After the state and coroutine scope setup, add:
```kotlin
var idleTimerKey by remember { mutableLongStateOf(0L) }

LaunchedEffect(idleTimerKey) {
    delay(18_000L)
    viewModel.onIdleDetected()
}
```

Wrap the root `Box` content with `Modifier.pointerInput(Unit)` to reset the timer on any pointer event:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent()
                    idleTimerKey++                   // R03: KMP-safe, resets idle countdown
                    viewModel.stopAutoSlideshow()    // stop slideshow on any touch
                }
            }
        },
) { /* existing content */ }
```

**14f — Remove FabMenu import**, add `CompanionOrb`, `CompanionCard` imports.

**14g — Update ProfileScreen call** to pass `onShowSettings = { showSettings = true }` (see T13).

**14h — Wire auto-slideshow into PoiCarousel**:

`PoiCarousel` already accepts `selectedIndex: Int?` and scrolls to it via `LaunchedEffect(selectedIndex)`. Drive it from `autoSlideshowIndex` in addition to the existing pin-tap selection:

```kotlin
PoiCarousel(
    pois = state.pois,
    // autoSlideshowIndex takes priority during slideshow; falls back to pin-tap selection
    selectedIndex = state.autoSlideshowIndex ?: state.selectedPinIndex,
    onCardSwiped = { index ->
        viewModel.stopAutoSlideshow()   // manual swipe cancels slideshow immediately
        viewModel.onCarouselSwiped(index)   // O2: confirmed at MapViewModel line 644
    },
    // ... other existing params unchanged
)
```

Note: `PoiCarousel.isProgrammaticScroll` flag already prevents `onCardSwiped` from firing during programmatic `animateScrollToItem` calls — so setting `autoSlideshowIndex` will NOT trigger `stopAutoSlideshow()` in a feedback loop.

---

#### T15 — Tests

Write unit tests in `composeApp/src/commonTest/kotlin/com/harazone/domain/companion/CompanionNudgeEngineTest.kt`.

**Test: checkRelaunched — skips when rate-limited**
```
Given lastDeltaShownAt = now - 2 hours
When checkRelaunched(nonEmptyPois)
Then returns null (rate limit not elapsed)
```

**Test: checkRelaunched — fires and persists timestamp**
```
Given lastDeltaShownAt = now - 7 hours, aiProvider returns "Interesting fact"
When checkRelaunched(nonEmptyPois)
Then returns CompanionNudge(RELAUNCH_DELTA, "Interesting fact")
And setLastDeltaShownAt was called with current time
```

**Test: checkProximity — fires within 200m**
```
Given savedPoi at (0.001, 0.001), gps at (0.0, 0.0) (~157m apart), no prior key
When checkProximity(gps, savedPois)
Then returns CompanionNudge(PROXIMITY, text containing poi.name)
```

**Test: checkProximity — skips when already pinged today**
```
Given savedPoi, gps within 200m, lastProximityPingKey == "${poi.id}:$dayKey"
When checkProximity
Then returns null
```

**Test: checkVibeReveal — fires at milestone 5**
```
Given savedPois: 5 x vibe="coffee", vibeMilestonesSeenSet = emptySet
When checkVibeReveal(savedPois)
Then returns CompanionNudge(VIBE_REVEAL, text containing "5" and "coffee")
And "coffee:5" added to seen set
```

**Test: checkVibeReveal — skips already-seen milestone**
```
Given savedPois: 5 x vibe="coffee", vibeMilestonesSeenSet = {"coffee:5"}
When checkVibeReveal(savedPois)
Then returns null
```

**Test: checkInstantNeighbor — returns nearest same-type unvisited POI**
```
Given savedPoi type="cafe", allPois has 2 unvisited cafes at different distances
When checkInstantNeighbor
Then returns nudge for the closer one
```

**Test: checkAmbientWhisper — skips same area**
```
Given whisperShownForArea = "Lisbon", areaName = "Lisbon"
When checkAmbientWhisper
Then returns null (no Gemini call made)
```

**Slideshow ViewModel tests** (R22) — in `composeApp/src/commonTest/kotlin/com/harazone/ui/map/MapViewModelSlideshowTest.kt`:

**Test: startAutoSlideshow — advances index each step**
```
Given pois = [A, B, C], autoSlideshowIndex = null, TestCoroutineDispatcher
When startAutoSlideshow() called
Then after 1 step: autoSlideshowIndex = 0, flyToCoords called with A.lat/lng
After 2 steps: autoSlideshowIndex = 1
After 3 steps: autoSlideshowIndex = 2
After 4 steps: autoSlideshowIndex = 0 (wraps)
```

**Test: startAutoSlideshow — clears index on cancel (R15)**
```
Given slideshow running
When stopAutoSlideshow() called
Then autoSlideshowIndex = null in state
And slideshowJob = null
```

**Test: startAutoSlideshow — clears index on internal break (R15)**
```
Given slideshow running, then selectedPoi becomes non-null
When while-loop break condition triggers
Then finally block runs: autoSlideshowIndex = null, slideshowJob = null
```

**Test: onIdleDetected — slideshow + whisper mutually exclusive (R14)**
```
Given carousel is visible (pois non-empty, showListView=false, selectedPoi=null)
When onIdleDetected() called
Then startAutoSlideshow() is called
And generateCompanionNudge() (whisper) is NOT called
```

---

### Acceptance Criteria

**AC1 — Orb always visible, not FAB**
Given the map is loaded in map mode (not profile, not detail page)
When no nudge is queued
Then CompanionOrb is visible at bottom-right, dimmed gold/orange at ~50% alpha
And FabMenu is gone — no expand menu, no + icon

**AC2 — Orb pulses when nudge is ready**
Given a new nudge is enqueued (any trigger)
When the CompanionCard is not currently showing
Then CompanionOrb visually pulses (scale breathing animation)

**AC3 — Tap orb opens CompanionCard**
Given isPulsing = true and a nudge in the queue
When user taps CompanionOrb
Then CompanionCard slides up from bottom with nudge text
And orb stops pulsing

**AC4 — Dismiss CompanionCard**
Given CompanionCard is showing
When user taps the X button or presses Android back (no scrim — R02 removed it)
Then CompanionCard disappears
And if more nudges are in queue, orb starts pulsing again

**AC5 — "Tell me more" opens ChatOverlay pre-seeded**
Given CompanionCard is showing with nudge text "X"
When user taps "Tell me more →"
Then CompanionCard dismisses
And ChatOverlay opens with framing hint derived from nudge.chatContext
And the AI's first response continues naturally from the nudge

**AC6 — Welcome Back Delta fires on session start**
Given savedPois.isNotEmpty() and > 6 hours since last delta
When MapViewModel init loads saved POIs for the first time
Then Gemini generates a relaunch observation
And it is enqueued as a RELAUNCH_DELTA nudge (rate-limited to one per 6h)

**AC7 — Ambient Whisper fires after 18s idle (carousel NOT visible)**
Given user has not tapped for 18s, pois loaded, area not whispered yet
And carousel is NOT visible (list view open, or detail page open)
When idle timer fires
Then Gemini generates an ambient observation and it is enqueued as AMBIENT_WHISPER nudge
And same area will not trigger another whisper this session
Note: in normal map mode with POIs loaded, carousel IS visible — slideshow fires instead of whisper (R14 mutual exclusion). Whisper only activates when the carousel is hidden. "No POIs loaded" is not a valid whisper trigger — `onIdleDetected()` early-returns on `pois.isEmpty()` before reaching this branch (O1).

**AC8 — Proximity Ping fires within 200m on meaningful location refresh**
Given user moves 100m+ (or 1hr has elapsed) AND is within 200m of a saved POI
When the app's location refresh threshold is crossed (`isStale || hasMovedSignificantly`)
Then PROXIMITY nudge enqueued with POI name + distance text
And will not fire again for the same POI on the same UTC day
And does NOT fire on every raw GPS event between refresh thresholds

**AC9 — Instant Neighbor fires on save**
Given user saves a POI (visitPoi called), allDiscoveredPois has a nearer unvisited same-type POI
When visitPoi() completes
Then INSTANT_NEIGHBOR nudge enqueued suggesting the neighbor

**AC10 — Anticipation Seed fires for WANT_TO_GO saves**
Given visitPoi() resolves to VisitState.WANT_TO_GO
When post-save nudge checks run
Then ANTICIPATION_SEED nudge enqueued ("I'll keep an eye on X")

**AC11 — Vibe Reveal fires at milestone**
Given user has 5 saved POIs with vibe="coffee" and "coffee:5" not yet seen
When post-save nudge checks run
Then VIBE_REVEAL nudge enqueued ("You've saved 5 coffee spots...")
And "coffee:5" is persisted in vibeMilestonesSeenSet

**AC12 — Priority queue: PROXIMITY beats RELAUNCH_DELTA**
Given both a PROXIMITY and a RELAUNCH_DELTA nudge are queued simultaneously
When user taps orb
Then PROXIMITY nudge is shown first

**AC13 — Settings accessible via ProfileScreen gear icon**
Given ProfileScreen is open
When user taps the gear icon (top-right, beside X)
Then SettingsSheet opens
And Settings is NOT accessible from the orb (no expand menu)

**AC14 — Rate limits survive app kill**
Given RELAUNCH_DELTA fired, user force-kills app, relaunches within 6 hours
When MapViewModel init runs
Then RELAUNCH_DELTA does NOT fire again (rate limit persisted in DB)

**AC15 — Slideshow starts after idle threshold**
Given POIs are loaded, carousel is visible (map mode, no detail page, no list view)
And user has not interacted for 18 seconds
When `onIdleDetected()` fires
Then the carousel begins cycling: card 0 → card 1 → ... → last card → card 0 (3.5s per card)
And the map camera pans to each POI's coordinates as its card becomes active

**AC16 — Slideshow cycles ALL cards in order without skipping**
Given slideshow is active with N POIs
When the cycle completes N steps
Then every card index 0..N-1 was shown exactly once, in ascending order
And index wraps back to 0 after the last card (continuous loop)

**AC17 — Any user interaction stops the slideshow immediately**
Given slideshow is active (carousel auto-cycling, camera panning)
When user performs any touch, tap, scroll, or pan anywhere on screen
Then `stopAutoSlideshow()` is called
And `autoSlideshowIndex` is set to null
And carousel stops scrolling, camera stops panning
And slideshow resumes only after the next idle period of 18s

---

## Additional Context

### Dependencies

- `CompanionNudgeEngine` depends on: `UserPreferencesRepository`, `AreaIntelligenceProvider`, `AppClock`
- `MapViewModel` gains: `CompanionNudgeEngine`, `LocaleProvider` (new constructor param)
- `ProfileScreen` gains: `onShowSettings: () -> Unit` (new param)
- No new Gradle dependencies required

### Testing Strategy

- `CompanionNudgeEngineTest` — 8 unit tests covering all 6 nudge types + rate limit gates (see T15 for full list)
- Use `FakeUserPreferencesRepository` (open class, override methods to use in-memory map — follow pattern from existing fake in `MapViewModelTest`)
- Use `FakeAreaIntelligenceProvider` — return hardcoded strings for `generateCompanionNudge`
- `FakeAppClock` — inject controllable `nowMs` for rate limit testing
- No UI tests required — CompanionOrb/Card are thin composables; logic is in engine

### Notes

- **`FabMenu.kt` after T14**: The file can be deleted. `FabScrim` was the only other export — it's now inlined in `CompanionCard`. Verify no other file imports from `FabMenu.kt` before deleting.
- **`isFabExpanded` in MapUiState**: Remove the field. Any `copy(isFabExpanded = ...)` calls in MapViewModel will be compile errors — use them as a search guide to find all removal points.
- **Icon**: Use `Icons.Default.Star` (confirmed available in standard material-icons). `AutoAwesome` requires `material-icons-extended` which is not in project deps.
- **TextPrimary / TextSecondary** in CompanionCard: verify these tokens exist in the theme — they are used in `DetailPageLight` context elsewhere. If not, use `Color(0xFF1A1A1A)` and `Color(0xFF888888)` respectively.
- **`roundToInt` import**: In `CompanionNudgeEngine.checkProximity`, import `kotlin.math.roundToInt` for the distance rounding.
- **Post-MVP toggles**: The brainstorm specifies Humor, Wellness, History Drop toggles in Settings. These are out of scope for this spec — the engine always behaves as if all are enabled.
