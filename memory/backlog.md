# Cross-Epic Deferred Backlog

Auto-generated from `TODO(BACKLOG-*)` comments in source code.
Run `./scripts/generate-backlog.sh` to regenerate.

---

**24 open items**

## HIGH

| Severity | Item | Location |
|----------|------|----------|
| HIGH | Coil disk cache not configured — iOS has no default disk cache, thumbnails re-fetched every session | `commonMain/kotlin/com/areadiscovery/App.kt:21` |

## MEDIUM

| Severity | Item | Location |
|----------|------|----------|
| MEDIUM | clusterPois uses Manhattan distance in degrees — same limitation as Android. Replace with Haversine. | `iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt:254` |
| MEDIUM | SubcomposeAsyncImage overhead in LazyColumn — switch to AsyncImage with custom Painter for placeholder/error | `commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt:127` |
| MEDIUM | Add +/- zoom control buttons as Compose overlays — MapLibre 11.x removed built-in zoom controls | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:134` |
| MEDIUM | clusterPois uses Manhattan distance in degrees — inconsistent radius at different latitudes. Replace with Haversine. | `androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt:428` |

## LOW

| Severity | Item | Location |
|----------|------|----------|
| LOW | Tap empty map space to deselect POI card on iOS. | `iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt:80` |
| LOW | Add vibe-coloured circle marker images via mapView:imageForAnnotation: | `iosMain/kotlin/com/areadiscovery/ui/map/MapComposable.ios.kt:216` |
| LOW | ThumbnailPlaceholder uses fillMaxSize with no intrinsic size — fragile implicit contract. Should accept modifier param. | `commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt:202` |
| LOW | render source attribution links under AI chat bubbles | `commonMain/kotlin/com/areadiscovery/ui/map/ChatOverlay.kt:332` |
| LOW | Icon size hardcoded at 20dp regardless of dynamic circle size — scale proportionally with sizeDp | `commonMain/kotlin/com/areadiscovery/ui/map/components/VibeOrb.kt:130` |
| LOW | submitSearch — no current UI caller; preserve for programmatic search | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:101` |
| LOW | ~50 lines duplicated between onRecentSelected and onGeocodingSuggestionSelected — extract shared helper | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:257` |
| LOW | Generic location error message — detect permission denial vs GPS off and show specific guidance | `commonMain/kotlin/com/areadiscovery/ui/map/MapViewModel.kt:741` |
| LOW | snackbarHostState created inside Ready branch — resets on Ready→Failed→Ready retry | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:107` |
| LOW | Magic number 112.dp for POI list top padding — should be named or derived | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:130` |
| LOW | Three-stop bottom sheet deferred — V1 uses two stops. Requires anchoredDraggable with custom snap points. | `commonMain/kotlin/com/areadiscovery/ui/map/MapScreen.kt:224` |
| LOW | orbIconName is a dead field — unmapped in UI, overridden by toImageVector() | `commonMain/kotlin/com/areadiscovery/domain/model/Vibe.kt:6` |
| LOW | COLLATE NOCASE only covers ASCII — Unicode place names won't deduplicate | `commonMain/sqldelight/com/areadiscovery/data/local/recent_places.sq:2` |
| LOW | This test doesn't isolate cold-start seed vs observer path | `commonTest/kotlin/com/areadiscovery/ui/map/MapViewModelTest.kt:1091` |
| LOW | Dedup is case-sensitive here but real DB uses COLLATE NOCASE — inconsistent test behavior | `commonTest/kotlin/com/areadiscovery/fakes/FakeRecentPlacesRepository.kt:26` |
| LOW | Weak assertion (assertNotNull only) — doesn't verify UI actually renders | `androidInstrumentedTest/kotlin/com/areadiscovery/AppLaunchSmokeTest.kt:25` |
| LOW | Custom POI icons per type (landmark, food, culture, nature) using annotation plugin SymbolManager | `androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt:242` |
| LOW | TalkBack per-marker contentDescription for map-mode accessibility | `androidMain/kotlin/com/areadiscovery/ui/map/MapComposable.android.kt:243` |
| LOW | resolveActivity deprecated in API 33+ — replace with ResolveInfoFlags variant | `androidMain/kotlin/com/areadiscovery/MainActivity.kt:55` |

---

_Last generated: 2026-03-08 18:30_
