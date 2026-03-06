# Cross-Epic Deferred Backlog

Items deferred during code review — to be picked up in relevant future stories.

---

## Story 3.2 — AI-Generated POI Markers

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | Custom POI icons per type (landmark, food, culture, nature) using `org.maplibre.gl:android-plugin-annotation-v9` SymbolManager | `MapComposable.android.kt` | Dedicated icon pass |
| LOW | TalkBack per-marker `contentDescription` — map-mode accessibility enhancement (list view satisfies NFR21; this is for sighted TalkBack users exploring map) | `MapComposable.android.kt` | Future UX polish |
| LOW | `AppLaunchSmokeTest` assertion is weak (`assertNotNull(activity)`) — doesn't verify UI actually renders | `AppLaunchSmokeTest.kt` | Story 5.2 (onboarding/permission flow) |
| LOW | Generic "Can't find your location" message shown on permission denial — should detect and show specific guidance | `MapViewModel.kt` | Story 5.2 (permission flow) |
| INFO | Deprecated MapLibre marker API (`addMarker`, `removeMarker`, `MarkerOptions`) — V1 approach; migrate when adding custom icons | `MapComposable.android.kt` | Dedicated icon pass |

---

## Story 3.3 — POI Detail Card & Bottom Sheet

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | `resolveActivity(packageManager)` deprecated in API 33+ — replace with `resolveActivity(packageManager, PackageManager.ResolveInfoFlags.of(0))` to silence lint | `MainActivity.kt` | Any story touching MainActivity |
| LOW | Scaffold + snackbar state (`rememberBottomSheetScaffoldState`, `SnackbarHostState`) created inside `is MapUiState.Ready` branch — resets to peek on retry (Ready→Failed→Ready) | `MapScreen.kt` | Future UX polish |
| INFO | Three-stop bottom sheet (collapsed/half/full) deferred — V1 uses two stops (peek 88dp / expanded). Requires `anchoredDraggable` with custom snap points | `MapScreen.kt` | Future UX polish |

---

## Architecture — Shared Area Session (from device testing, post-Epic 3)

| Severity | Item | Description | Deferred To |
|----------|------|-------------|-------------|
| MEDIUM | Shared AreaSessionManager | Summary + Map ViewModels both fetch location + portrait independently. Quick fix done (LocationProvider caching), but proper solution is a shared AreaSessionManager holding current area/name/portrait consumed by both screens. | Future architecture story |

---

## Hero Redesign (Timeline & Right Now Cards)

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | Hero cards have no click-to-scroll interaction to jump to full bucket content below | `TimelineCard.kt`, `RightNowCard.kt` | Future UX polish |
| LOW | No tablet/landscape adaptive layout for TimelineCard LazyRow | `TimelineCard.kt` | Phase 2+ responsive layout |
| LOW | liveRegion on RightNowCard may re-announce on unrelated recomposition — monitor | `RightNowCard.kt` | Manual testing / UX polish |

---

## Resolved Items (for reference)

- ~~POI caching across sessions~~ — Fixed in commit 437ed06 (area_poi_cache table, migration 2)
- ~~Camera never centres on user location~~ — Fixed in commit 437ed06
- ~~No POI markers on Map screen~~ — Fixed in commit 437ed06
- ~~Slow app launch (GPS timeout + cache-hit state dropped)~~ — Fixed in commit e4c8c28 (lastLocation-first, SummaryStateMapper fix)
