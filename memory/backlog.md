# Cross-Epic Deferred Backlog

Auto-generated from `TODO(BACKLOG-*)` comments in source code.
Run `./scripts/generate-backlog.sh` to regenerate.

---

**21 open items**

## HIGH

| Severity | Item | Location |
|----------|------|----------|
| HIGH | POI pins in water — validate AI coordinates against area bounding box, re-geocode outliers | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:196` |
| HIGH | Coil disk cache not configured — iOS has no default disk cache, thumbnails re-fetched every session | `commonMain/kotlin/com/areadiscovery/App.kt:21` |

## MEDIUM

| Severity | Item | Location |
|----------|------|----------|
| MEDIUM | SubcomposeAsyncImage overhead in LazyColumn — switch to AsyncImage with custom Painter for placeholder/error | `commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt:127` |
| MEDIUM | conversationHistory always empty — follow-up chips produce standalone answers with no context | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:147` |
| MEDIUM | Add +/- zoom control buttons as Compose overlays — MapLibre 11.x removed built-in zoom controls | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:131` |
| MEDIUM | clusterPois uses Manhattan distance in degrees — inconsistent radius at different latitudes. Replace with Haversine. | `androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt:428` |

## LOW

| Severity | Item | Location |
|----------|------|----------|
| LOW | ThumbnailPlaceholder uses fillMaxSize with no intrinsic size — fragile implicit contract. Should accept modifier param. | `commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt:202` |
| LOW | Icon size hardcoded at 20dp regardless of dynamic circle size — scale proportionally with sizeDp | `commonMain/kotlin/com/areadiscovery/ui/map/components/VibeOrb.kt:130` |
| LOW | ~50 lines duplicated between onRecentSelected and onGeocodingSuggestionSelected — extract shared helper | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:344` |
| LOW | Generic location error message — detect permission denial vs GPS off and show specific guidance | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:862` |
| LOW | snackbarHostState created inside Ready branch — resets on Ready→Failed→Ready retry | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:104` |
| LOW | Magic number 112.dp for POI list top padding — should be named or derived | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:127` |
| LOW | Three-stop bottom sheet deferred — V1 uses two stops. Requires anchoredDraggable with custom snap points. | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:224` |
| LOW | orbIconName is a dead field — unmapped in UI, overridden by toImageVector() | `commonMain/kotlin/com/areadiscovery/domain/model/Vibe.kt:6` |
| LOW | COLLATE NOCASE only covers ASCII — Unicode place names won't deduplicate | `commonMain/sqldelight/com/areadiscovery/data/local/recent_places.sq:2` |
| LOW | This test doesn't isolate cold-start seed vs observer path | `commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt:1150` |
| LOW | Dedup is case-sensitive here but real DB uses COLLATE NOCASE — inconsistent test behavior | `commonTest/kotlin/com/areadiscovery/fakes/FakeRecentPlacesRepository.kt:26` |
| LOW | Weak assertion (assertNotNull only) — doesn't verify UI actually renders | `androidInstrumentedTest/kotlin/com/areadiscovery/AppLaunchSmokeTest.kt:25` |
| LOW | Custom POI icons per type (landmark, food, culture, nature) using annotation plugin SymbolManager | `androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt:242` |
| LOW | TalkBack per-marker contentDescription for map-mode accessibility | `androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt:243` |
| LOW | resolveActivity deprecated in API 33+ — replace with ResolveInfoFlags variant | `androidMain/kotlin/com/areadiscovery/MainActivity.kt:55` |

---

_Last generated: 2026-03-08 05:40_
