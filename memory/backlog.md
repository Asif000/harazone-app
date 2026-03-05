# Cross-Epic Deferred Backlog

Items deferred during code review — to be picked up in relevant future stories.

---

## Story 3.2 — AI-Generated POI Markers

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | Custom POI icons per type (landmark, food, culture, nature) using `org.maplibre.gl:android-plugin-annotation-v9` SymbolManager | `MapComposable.android.kt` | Story 3.3 or dedicated icon pass |
| LOW | TalkBack per-marker `contentDescription` — full accessibility via custom `AccessibilityDelegate` | `MapComposable.android.kt` | Story 3.4 (POI Accessible List View) |
| LOW | POI caching across sessions — cache-hit paths currently return `emptyList()` for POIs | `AreaRepositoryImpl.kt` | Epic 7 |
| LOW | `AppLaunchSmokeTest` assertion is weak (`assertNotNull(activity)`) — doesn't verify UI actually renders | `AppLaunchSmokeTest.kt` | Story 5.2 (onboarding/permission flow) |
| LOW | Generic "Can't find your location" message shown on permission denial — should detect and show specific guidance | `MapViewModel.kt` | Story 5.2 (permission flow) |
| INFO | Deprecated MapLibre marker API (`addMarker`, `removeMarker`, `MarkerOptions`) — V1 approach; migrate when adding custom icons | `MapComposable.android.kt` | Story 3.3 |

---

## Device Testing — Bugs Found (Fix Before Story 3.3)

| Severity | Bug | Root Cause | File |
|----------|-----|------------|------|
| HIGH | **Camera never centres on user location at zoom 14** — map stays zoomed out on Florida peninsula. `moveCamera()` fires before map style loads; MapLibre resets viewport on style load, discarding the camera update. Fix: call `moveCamera` inside `Style.OnStyleLoaded` callback instead of bare `getMapAsync`. | `LaunchedEffect(latitude, longitude)` fires `getMapAsync { map -> map.moveCamera(...) }` before style is ready | `MapComposable.android.kt` |
| HIGH | **No POI markers ever appear on Map screen** — cache-hit path in `AreaRepositoryImpl` returns `PortraitComplete(emptyList())`, so POIs are always empty after first AI call. `MapViewModel` receives empty list and never updates markers. Fix: persist POIs in the local cache alongside buckets so cache-hit paths return real POIs. | `AreaRepositoryImpl` cache hit emits `PortraitComplete(pois = emptyList())` | `AreaRepositoryImpl.kt` |
