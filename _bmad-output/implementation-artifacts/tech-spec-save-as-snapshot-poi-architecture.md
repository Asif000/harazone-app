---
title: 'Save-as-Snapshot POI Architecture'
slug: 'save-as-snapshot-poi-architecture'
created: '2026-03-10'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'SQLDelight', 'Coroutines/Flow', 'MapLibre (Android)']
files_to_modify:
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq'
  - 'composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/8.sqm'
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/util/GeoUtils.kt'
code_patterns: ['StateFlow + MutableStateFlow', 'optimistic update + rollback', 'SQLDelight ALTER TABLE migrations', 'LaunchedEffect(key) for map rendering', 'SymbolManager pin creation']
test_patterns: ['runTest + UnconfinedTestDispatcher', 'FakeSavedPoiRepository', 'Turbine awaitItem / expectMostRecentItem']
---

# Tech-Spec: Save-as-Snapshot POI Architecture

**Created:** 2026-03-10

## Overview

### Problem Statement

Saved POIs are invisible on the map. Gold pins are rendered by checking if a Gemini result's `savedId` (`"$name|$latitude|$longitude"`) exists in `savedPoiIds` — but Gemini returns slightly different coordinates each call, so the ID never matches and gold pins never appear. Additionally, the DB snapshot is missing `description` and `rating` fields. Saved POIs from the AI Chat screen also lose their image because `ChatPoiCard` has no `imageUrl` field, causing `imageUrl = null` to be stored in the DB.

### Solution

Add an independent DB-sourced gold pin layer to the map that is always visible, regardless of Gemini results. Use 50m proximity (haversine) to suppress Gemini blue pins when a saved POI is nearby. Extend the `SavedPoi` DB schema to store full snapshot data (`description`, `rating`). Fix `ChatPoiCard` to carry `imageUrl` so chat saves have images. Gold pin tap converts the `SavedPoi` snapshot to a `POI` inline and calls the existing `onPoiSelected` callback — no new sheet component, no Gemini re-fetch.

### Scope

**In Scope:**
- DB migration 8: add `description` (TEXT nullable) and `rating` (REAL nullable) to `saved_pois`; update `insertOrReplace` named query to include new params
- `SavedPoi` domain model: add `description: String?` and `rating: Float?`
- `SavedPoiRepositoryImpl`: map new columns in `observeAll()` and pass them in `save()`
- `MapViewModel.savePoi`: pass `poi.description` and `poi.rating`; migrate existing `haversineKm` to use shared `GeoUtils`
- `ChatPoiCard`: add `@SerialName("img") val imageUrl: String? = null`; `ChatViewModel.savePoi` passes `card.imageUrl`
- `MapComposable.android.kt`: extract `ensureIcon` to module-level; add `savedPois` param; extract `filterSuppressedPois` as a pure function; add `savedPois` to Gemini `LaunchedEffect` key; add 50m suppression; add DB gold pin `LaunchedEffect`; update tap handler
- `MapScreen.kt`: pass `state.savedPois` to `MapComposable`
- New `GeoUtils.kt` (commonMain): `haversineDistanceMeters` — consolidates all haversine logic

**Out of Scope:**
- iOS dual-pin layer (deferred — see Notes)
- `savedId` formula change (`name|lat|lng` stays as-is)
- User notes UI (add/edit)
- Gemini re-fetch on gold pin tap
- Chat prompt update to return `imageUrl` (structural field added; prompt change is a separate task)

## Context for Development

### Codebase Patterns

- **DB migrations**: `ALTER TABLE saved_pois ADD COLUMN ...` pattern. Current version = 7. New = `8.sqm`. Must update BOTH the `CREATE TABLE` in `saved_pois.sq` AND the `insertOrReplace` named query (which enumerates columns explicitly — new params must be added or they will be silently omitted from every write).
- **SavedPoi save flow**: Caller passes `savedAt = 0L`; `SavedPoiRepositoryImpl.save()` sets the actual timestamp via `clock.nowMs()`. Do not change this.
- **Optimistic update pattern**: update `_uiState` immediately, then write to DB. On exception: roll back the state change.
- **`MapUiState.Ready.savedPois` already observed**: confirmed that `MapViewModel.init` collects `savedPoiRepository.observeAll()` into `latestSavedPois` and copies it into `MapUiState.Ready.savedPois` (lines 72–78). No changes needed to this collection.
- **`ensureIcon` is a LOCAL function inside the Gemini `LaunchedEffect`**: it must be extracted to a module-level private function (taking `style: Style` as a parameter) BEFORE the DB pin `LaunchedEffect` can call it. A sibling `LaunchedEffect` cannot reference a local function defined inside another `LaunchedEffect` lambda.
- **MapComposable pin rendering**: driven by `LaunchedEffect(pois, activeVibe, savedPoiIds, styleLoaded.value)`. Must add `savedPois` to this key so suppression re-fires when saves change.
- **Existing haversine duplication**: `MapViewModel` has a private `haversineKm` function. `GeoUtils.haversineDistanceMeters` replaces it. Migrate the call site in `MapViewModel`.
- **Pin tap**: `sm.addClickListener` is registered once inside the `setStyle` callback (fires only on first style load). `symbolSavedPoiMap` is populated later by the DB pin `LaunchedEffect` — but because both are `remember`-ed mutable maps, the closure captures the reference correctly and sees later mutations. The ordering is safe.
- **`savedPoiIds` set stays**: still needed for the save/unsave button state on Gemini result cards. `isSaved` on Gemini pins is removed from pin colour logic but not from card button state.

### Files to Reference

| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq` | Add `description` + `rating` to CREATE TABLE and `insertOrReplace` query |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/7.sqm` | Reference pattern: `ALTER TABLE saved_pois ADD COLUMN image_url TEXT;` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt` | Add `description: String?` and `rating: Float?` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Source fields: `description: String`, `rating: Float?`, `insight: String` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt` | Update `observeAll()` map + `save()` call |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Update `savePoi()`; migrate `haversineKm` to `GeoUtils` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt` | Add `imageUrl` to `ChatPoiCard` with `@SerialName("img")` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt` | Pass `card.imageUrl` in `savePoi()` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Pass `state.savedPois` to `MapComposable` |
| `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt` | Extract `ensureIcon`; add `savedPois` param; suppression; DB pin layer; tap handler |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/` | Migration versioning reference |

### Technical Decisions

1. **Gold pins come from DB only** — `isSaved = poi.savedId in savedPoiIds` removed from Gemini pin loop. Gold pins are a separate render pass from `savedPois`. Gemini pins are always blue. `savedPoiIds` stays for save/unsave button state only.
2. **50m haversine suppression** — extracted as `filterSuppressedPois(pois, savedPois, thresholdMeters = 50.0): List<POI>` pure function in `MapComposable.android.kt` for testability. Uses `haversineDistanceMeters` from `GeoUtils`.
3. **Gold pin tap → inline SavedPoi-to-POI conversion** — convert `SavedPoi` to `POI` at tap time and call existing `currentOnPoiSelected`. `Confidence.HIGH` is justified: this is user-confirmed saved data, not AI inference. If `Confidence` drives a data-source disclaimer in the detail sheet, verify this is appropriate. `description = savedPoi.description ?: savedPoi.whySpecial` — `description` is the full narrative; fallback to `whySpecial` if null. `insight = savedPoi.whySpecial` — always the AI insight sentence.
4. **Separate `symbolSavedPoiMap`** — `MutableMap<Long, SavedPoi>()` alongside existing `symbolPoiMap`. Tap handler checks saved map first, then Gemini map.
5. **Separate `savedSymbolsRef`** — `MutableList<Symbol>()` for DB-sourced pins. Distinct from `symbolsRef`.
6. **`savedPois` LaunchedEffect key** — `LaunchedEffect(savedPois, styleLoaded.value)` for the DB pin layer. Also add `savedPois` to the EXISTING Gemini `LaunchedEffect` key so suppression re-evaluates on every save/unsave.
7. **`GeoUtils` consolidates haversine** — `haversineDistanceMeters` is the single implementation. Existing `haversineKm` in `MapViewModel` is replaced with `haversineDistanceMeters(...) / 1000.0`.
8. **`Vibe.DEFAULT` for DB gold pins** — before using `Vibe.DEFAULT`, verify this enum entry exists in `Vibe.kt`. Its base circle color will show under the gold ring. If `Vibe.DEFAULT` maps to a near-black color, consider using a neutral grey (`#555555`) or white (`#FFFFFF`) as the base circle for saved pins to ensure visibility on the dark map. Confirm visually on device.
9. **`ensureIcon` extraction** — must be a module-level `private fun ensureIcon(style: Style, vibe: Vibe, poiType: String, isSaved: Boolean): String`. The `Style` reference (currently captured from the outer scope) must become an explicit parameter.

---

## Implementation Plan

### Tasks

- [ ] **Task 1: DB migration 8 — add description and rating**
  - Create: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/migrations/8.sqm`
  - Content:
    ```sql
    ALTER TABLE saved_pois ADD COLUMN description TEXT;
    ALTER TABLE saved_pois ADD COLUMN rating REAL;
    ```
  - Update `saved_pois.sq` in TWO places:
    - **CREATE TABLE**: add both columns after `image_url`:
      ```sql
      description TEXT,
      rating      REAL
      ```
    - **`insertOrReplace` named query**: add `:description` and `:rating` to both the column list and the VALUES list. The query enumerates columns explicitly — if this is not updated, SQLDelight will generate a function without these parameters and the fields will silently never be written to the DB.

- [ ] **Task 2: Update `SavedPoi` domain model**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt`
  - Add two nullable fields with defaults after `imageUrl`:
    ```kotlin
    val description: String? = null,
    val rating: Float? = null,
    ```

- [ ] **Task 3: Update `SavedPoiRepositoryImpl` for new columns**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt`
  - In `observeAll()` mapping block, add:
    ```kotlin
    description = it.description,
    rating = it.rating?.toFloat(),
    ```
  - In `save()` `insertOrReplace()` call, add:
    ```kotlin
    description = poi.description,
    rating = poi.rating?.toDouble(),
    ```
    (SQLDelight REAL maps to `Double` in generated query params; `Float?` needs `.toDouble()` cast)

- [ ] **Task 4: Update `MapViewModel.savePoi` to pass description and rating**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - In the `SavedPoi(...)` constructor call inside `savePoi()`, add after `imageUrl = poi.imageUrl`:
    ```kotlin
    description = poi.description,
    rating = poi.rating,
    ```

- [ ] **Task 5: Add `imageUrl` to `ChatPoiCard` and pass it through in `ChatViewModel`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt`
  - Add to `ChatPoiCard` data class after `whySpecial`. Must include `@SerialName("img")` so the JSON key is defined now — even though the chat prompt does not yet return it, the annotation must be set before the prompt ships:
    ```kotlin
    @SerialName("img") val imageUrl: String? = null,
    ```
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`
  - In `savePoi(card: ChatPoiCard, ...)` change `imageUrl = null` to `imageUrl = card.imageUrl`

- [ ] **Task 6: `MapViewModel` savedPois collection — NO CHANGE NEEDED**
  - Verified: `MapViewModel.init` (lines 72–78) already collects `savedPoiRepository.observeAll()` into `latestSavedPois` and updates `MapUiState.Ready.savedPois`. This task is a confirmation check only — read those lines and confirm they are present. Do not modify.

- [ ] **Task 7: Create `GeoUtils.kt` and consolidate haversine**
  - Create: `composeApp/src/commonMain/kotlin/com/harazone/util/GeoUtils.kt`
    ```kotlin
    package com.harazone.util

    import kotlin.math.*

    fun haversineDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lng2 - lng1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
    ```
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Find the private `haversineKm` function and replace its body with:
    ```kotlin
    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        haversineDistanceMeters(lat1, lng1, lat2, lng2) / 1000.0
    ```
  - Add import: `import com.harazone.util.haversineDistanceMeters`
  - This eliminates the duplicate implementation while keeping the `haversineKm` call site intact.

- [ ] **Task 8: Update `MapComposable.android.kt` — extract, dual pin layer, suppression**
  - File: `composeApp/src/androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt`

  **8a — Extract `ensureIcon` to module-level private function** (REQUIRED before 8e — this is a compile prerequisite):
  - Find the local `fun ensureIcon(...)` defined inside the Gemini `LaunchedEffect` lambda.
  - Move it to module level (top of file, outside any composable or lambda).
  - Add `style: Style` as the first explicit parameter (was captured from outer scope):
    ```kotlin
    private fun ensureIcon(style: Style, vibe: Vibe, poiType: String, isSaved: Boolean): String {
        // ... existing body unchanged, but reference `style` param instead of captured var ...
    }
    ```
  - Update the call site inside the Gemini `LaunchedEffect` to pass `style`: `ensureIcon(style, vibe, poi.type, isSaved = false)`.
  - Verify: confirm `Vibe.DEFAULT` exists in the `Vibe` enum. If it does not, use any neutral entry. If its base circle color is near-black, change the background color inside `ensureIcon` for `isSaved = true` to a neutral grey (`#555555`) for visibility on the dark map. Check on device.

  **8b — Add `savedPois` parameter** to the composable function signature:
  ```kotlin
  @Composable
  fun MapComposable(
      // ... existing params ...
      savedPois: List<SavedPoi>,
      // ...
  )
  ```

  **8c — Add two new refs** alongside existing `symbolsRef` and `symbolPoiMap`:
  ```kotlin
  val savedSymbolsRef = remember { mutableListOf<Symbol>() }
  val symbolSavedPoiMap = remember { mutableMapOf<Long, SavedPoi>() }
  ```

  **8d — Update tap handler** (inside `sm.addClickListener { symbol -> ... }`, registered once at style-load):
  - The click listener is registered once in the `setStyle` callback. `symbolSavedPoiMap` is populated later by the DB pin `LaunchedEffect` (8g). The ordering is safe because both maps are `remember`-ed refs — mutations are visible through the already-registered closure.
  ```kotlin
  sm.addClickListener { symbol ->
      val savedPoi = symbolSavedPoiMap[symbol.id]
      if (savedPoi != null) {
          val poi = POI(
              name = savedPoi.name,
              type = savedPoi.type,
              description = savedPoi.description ?: savedPoi.whySpecial,
              insight = savedPoi.whySpecial,
              confidence = Confidence.HIGH,  // user-confirmed save; not AI inference
              latitude = savedPoi.lat,
              longitude = savedPoi.lng,
              imageUrl = savedPoi.imageUrl,
              rating = savedPoi.rating,
          )
          currentOnPoiSelected.value(poi)
      } else {
          symbolPoiMap[symbol.id]?.let { poi ->
              currentOnPoiSelected.value(poi)
          }
      }
      true
  }
  ```

  **8e — Extract `filterSuppressedPois` as a pure function** (for testability — add at module level):
  ```kotlin
  internal fun filterSuppressedPois(
      pois: List<POI>,
      savedPois: List<SavedPoi>,
      thresholdMeters: Double = 50.0,
  ): Set<String> = pois.filter { poi ->
      poi.latitude != null && poi.longitude != null &&
      savedPois.any { saved ->
          saved.lat != 0.0 && saved.lng != 0.0 &&
          haversineDistanceMeters(poi.latitude, poi.longitude, saved.lat, saved.lng) <= thresholdMeters
      }
  }.map { it.savedId }.toSet()
  ```
  Note: `<= 50.0` (inclusive) — exactly 50m is suppressed.

  **8f — Update existing Gemini pins `LaunchedEffect` key** — add `savedPois` so suppression re-fires on every save/unsave:
  ```kotlin
  // Before:
  LaunchedEffect(pois, activeVibe, savedPoiIds, styleLoaded.value) {
  // After:
  LaunchedEffect(pois, activeVibe, savedPoiIds, savedPois, styleLoaded.value) {
  ```
  Then inside the effect, before the `for` loop:
  ```kotlin
  val suppressedIds = filterSuppressedPois(filteredPois, savedPois)

  // Replace: for ((i, poi) in filteredPois.withIndex()) {
  // With:
  for ((i, poi) in filteredPois.filter { it.savedId !in suppressedIds }.withIndex()) {
  ```
  Also remove `isSaved` / gold-ring logic from the Gemini pin loop — Gemini pins are always blue:
  ```kotlin
  // Remove: val isSaved = poi.savedId in savedPoiIds
  // Replace: val iconKey = ensureIcon(vibe, poi.type, isSaved)
  // With:
  val iconKey = ensureIcon(style, vibe, poi.type, isSaved = false)
  ```

  **8g — Add `LaunchedEffect` for DB gold pins** (after the Gemini pins `LaunchedEffect`):
  - This rebuilds the entire gold pin layer on every DB change. With many saved POIs this causes a brief visual flash (pins removed then re-added). This is a known v1 limitation — acceptable for now. A diff-based incremental update is deferred.
  - The `isDestroyed[0]` guard inside `forEachIndexed` has no suspension points between pin creates, so it fires synchronously and the guard is effectively dead code. It is kept for defensive symmetry with the Gemini loop.
  ```kotlin
  LaunchedEffect(savedPois, styleLoaded.value) {
      if (!styleLoaded.value) return@LaunchedEffect
      savedSymbolsRef.forEach { sm.delete(it) }
      savedSymbolsRef.clear()
      symbolSavedPoiMap.clear()

      savedPois
          .filter { it.lat != 0.0 && it.lng != 0.0 }
          .forEachIndexed { i, savedPoi ->
              if (isDestroyed[0]) return@LaunchedEffect
              val iconKey = ensureIcon(style, Vibe.DEFAULT, savedPoi.type, isSaved = true)
              val symbol = sm.create(
                  SymbolOptions()
                      .withLatLng(LatLng(savedPoi.lat, savedPoi.lng))
                      .withIconImage(iconKey)
                      .withIconSize(1.0f)
                      .withTextField(savedPoi.name)
                      .withTextSize(10f)
                      .withTextColor("#FFD700")
                      .withTextOffset(arrayOf(0f, 1.8f))
                      .withTextHaloColor("rgba(0,0,0,0.7)")
                      .withTextHaloWidth(1.5f)
              )
              savedSymbolsRef.add(symbol)
              symbolSavedPoiMap[symbol.id] = savedPoi
          }
  }
  ```

- [ ] **Task 9: Update `MapScreen.kt` to pass `savedPois` to `MapComposable`**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`
  - Read `MapUiState.Ready.savedPois` and pass to `MapComposable`:
    ```kotlin
    MapComposable(
        // ... existing params ...
        savedPois = state.savedPois,
    )
    ```

### Acceptance Criteria

- [ ] **AC1**: Given the app launches with previously saved POIs, when the map loads any area, then gold pins for ALL saved POIs within the viewport are visible — regardless of whether a Gemini query has run.
- [ ] **AC2**: Given a Gemini query returns a POI that is within 50m of a saved POI, when the map renders, then the Gemini blue pin is NOT shown — only the gold pin is visible.
- [ ] **AC3**: Given a Gemini query returns a POI that is more than 50m from any saved POI, when the map renders, then the Gemini blue pin IS shown as normal (blue, no gold ring).
- [ ] **AC4**: Given the user taps a gold DB pin on the map, when the tap fires, then the existing POI detail bottom sheet opens showing: name, type, `whySpecial` as insight, image (if present), and rating (if present) — sourced from the DB snapshot only.
- [ ] **AC5**: Given the user saves a POI from the map screen, when the save completes, then the stored `SavedPoi` includes `description` and `rating` from the `POI` object.
- [ ] **AC6**: Given the user saves a POI from the AI Chat screen, when the save completes, then `imageUrl` is stored (populated if Gemini chat returns `"img"` key; null if not — no regression vs current behaviour, but field is now structurally wired).
- [ ] **AC7**: Given a DevSeeder persona has saved POIs, when the map opens for that area, then gold pins are visible for all seeded saves.
- [ ] **AC8**: Given the user unsaves a POI, when the unsave completes, then the gold pin disappears from the map within one DB observation cycle.
- [ ] **AC9**: Given the user saves a new POI while the map is showing Gemini results, when the save completes, then if the new saved POI is within 50m of a visible Gemini blue pin, that blue pin is suppressed (Gemini `LaunchedEffect` re-fires because `savedPois` is now in its key).

---

## Additional Context

### Dependencies

- No new libraries required
- SQLDelight version already supports `ALTER TABLE` in migration files (confirmed by migrations 6 + 7)
- `kotlin.math` (haversine) is available in commonMain
- `MapUiState.Ready.savedPois` field already exists — no MapUiState change needed
- `MapViewModel.observeAll()` collection already implemented — no ViewModel init change needed

### Testing Strategy

- **Unit: `GeoUtilsTest`** (commonTest)
  - `haversineDistanceMeters` with known pairs: 0m (same point), 50m apart (boundary, suppressed), 50.1m apart (not suppressed), 1000m apart
  - Verify: `<= 50.0` inclusive — exactly 50.0m returns suppressed
- **Unit: `FilterSuppressedPoisTest`** (androidTest or commonTest if extracted to commonMain)
  - `filterSuppressedPois` with: empty savedPois → nothing suppressed; poi exactly 50m from saved → in result; poi 50.1m away → not in result; multiple pois, only one suppressed
  - This is the primary regression guard for off-by-one errors in the suppression boundary
- **Unit: `MapViewModelTest`** — `savePoi passes description and rating to repository`
  - Use `FakeSavedPoiRepository`, call `savePoi(poi, areaName)` with a POI that has description + rating, assert the saved `SavedPoi` has correct values
- **Unit: `ChatViewModelTest`** — `savePoi passes imageUrl to repository`
  - Use `FakeSavedPoiRepository`, call `savePoi(card, areaName)` with `ChatPoiCard(imageUrl = "https://example.com/img.jpg")`, assert stored `imageUrl` matches
- **Manual on device**: (1) cold launch → gold pins visible without any search; (2) search area → blue pins appear, gold stay; (3) blue pin suppressed when within 50m of gold; (4) tap gold pin → detail sheet opens; (5) save a new POI → gold pin appears, nearby blue suppressed; (6) unsave → gold pin disappears
- No `PromptComparisonTest` needed — no prompt changes

### Notes

- **iOS deferred**: `MapComposable.ios.kt` is NOT changed. Add a TODO comment:
  ```kotlin
  // TODO(BACKLOG-HIGH): iOS dual-pin layer deferred — see tech-spec-save-as-snapshot-poi-architecture.md
  // Required: load savedPois from DB, render as gold pins, apply 50m suppression for Gemini results
  ```
- **`savedId` formula unchanged**: `"$name|$latitude|$longitude"` stays as the deduplication key. The 50m suppression is purely a display concern, independent of ID matching.
- **Gemini `savedPoiIds` set stays**: still drives the save/unsave star button on POI cards. Decoupled from pin colour.
- **DevSeeder**: no schema change needed — new fields default to null. Optionally add values for richer test data (not required for AC7).
- **Gold pin layer full rebuild on every DB write**: when `savedPois` changes (any save/unsave), ALL gold pins are torn down and re-created synchronously. With 20+ saves this produces a brief visible flash. Acceptable for v1. A diff-based incremental update (add new, remove missing) is the mitigation if the flash is unacceptable in practice — defer to a follow-up spec.
- **Tap handler ordering**: `sm.addClickListener` is registered once at style-load. `symbolSavedPoiMap` is populated asynchronously by the DB pin `LaunchedEffect`. The closure captures the map reference (not a snapshot), so it sees all future mutations. The ordering is safe — taps before the first `LaunchedEffect` completes will simply find no entry and fall through to `symbolPoiMap`.
- **`imageUrl` in `ChatPoiCard`**: structural field added with `@SerialName("img")`. Gemini chat prompt does not currently return this key — chat saves will remain `imageUrl = null` until the chat prompt is updated to include `"img"`. Defer prompt update to a separate task.
- **`@SerialName("img")` key choice**: `"img"` chosen to match the compact short-key convention of the existing chat card schema (`"n"`, `"t"`, `"w"`). If a different key is decided when the prompt is updated, change the annotation at that time.
