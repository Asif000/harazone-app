# Cross-Epic Deferred Backlog

Auto-generated from `TODO(BACKLOG-*)` comments in source code.
Run `./scripts/generate-backlog.sh` to regenerate.

---

**39 open items**

## HIGH

| Severity | Item | Location |
|----------|------|----------|
| HIGH | iOS dual-pin layer deferred — see tech-spec-save-as-snapshot-poi-architecture.md | `iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt:54` |
| HIGH | Capsule row has no scroll affordance — user can't tell more capsules exist off-screen. Also long area names hog space. Brainstorm: scroll hint, label truncation strategy, multi-level grouping, wrap layout. Critical for international saves. | `commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt:198` |
| HIGH | Replace with Foursquare photo API before public release | `commonMain/kotlin/com/harazone/ui/map/components/ExpandablePoiCard.kt:115` |
| HIGH | Coil disk cache not configured — iOS has no default disk cache, thumbnails re-fetched every session | `commonMain/kotlin/com/harazone/App.kt:26` |

## MEDIUM

| Severity | Item | Location |
|----------|------|----------|
| MEDIUM | clusterPois uses Manhattan distance in degrees — same limitation as Android. Replace with Haversine. | `iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt:261` |
| MEDIUM | Move haversineKm to shared util — duplicated in MapViewModel. Deduplicate when a third caller appears. | `commonMain/kotlin/com/harazone/ui/saved/SavedPlacesUiState.kt:32` |
| MEDIUM | Centralize hardcoded colors (storyPurple, storyGold, screenBg, card colors) into theme or CompositionLocal — makes future theming possible | `commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt:67` |
| MEDIUM | Add Modifier.animateItem() to card items for smooth removal animation (requires Compose 1.7+) | `commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt:303` |
| MEDIUM | Wire discovery story tag chips to filter/search action instead of no-op onClick | `commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt:433` |
| MEDIUM | Show userNote on SavedPoiCard — needs design feedback on placement, truncation, and styling | `commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt:92` |
| MEDIUM | SubcomposeAsyncImage overhead in LazyColumn — switch to AsyncImage with custom Painter for placeholder/error | `commonMain/kotlin/com/harazone/ui/map/POIListView.kt:135` |
| MEDIUM | POIListView needs polish pass — list rows lack save CTAs, tap opens | `commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:142` |
| MEDIUM | Add +/- zoom control buttons as Compose overlays — MapLibre 11.x removed built-in zoom controls | `commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:158` |
| MEDIUM | Guard skips when stale bucket entries exist — if bucket cache is expired but POI cache is fresh, | `commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt:93` |
| MEDIUM | clusterPois uses Manhattan distance in degrees — inconsistent radius at different latitudes. Replace with Haversine. | `androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt:556` |

## LOW

| Severity | Item | Location |
|----------|------|----------|
| LOW | iOS gold saved pins — implement MLNAnnotationView subclass with gold border when MLNAnnotationView ObjC bridging is resolved | `iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt:36` |
| LOW | Tap empty map space to deselect POI card on iOS. | `iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt:87` |
| LOW | Add vibe-coloured circle marker images via mapView:imageForAnnotation: | `iosMain/kotlin/com/harazone/ui/map/MapComposable.ios.kt:223` |
| LOW | Bottom bar uses translucent bg without blur — list content ghosts through on scroll. Make opaque or add gradient fade. | `commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt:332` |
| LOW | ThumbnailPlaceholder uses fillMaxSize with no intrinsic size — fragile implicit contract. Should accept modifier param. | `commonMain/kotlin/com/harazone/ui/map/POIListView.kt:219` |
| LOW | render source attribution links under AI chat bubbles | `commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt:585` |
| LOW | Icon size hardcoded at 20dp regardless of dynamic circle size — scale proportionally with sizeDp | `commonMain/kotlin/com/harazone/ui/map/components/VibeOrb.kt:134` |
| LOW | submitSearch — no current UI caller; preserve for programmatic search. If wired to UI, also set preSearchSnapshot for cancel-restore. | `commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt:169` |
| LOW | ~50 lines duplicated between onRecentSelected and onGeocodingSuggestionSelected — extract shared helper | `commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt:410` |
| LOW | Generic location error message — detect permission denial vs GPS off and show specific guidance | `commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt:1030` |
| LOW | \b word boundaries may break with non-Latin scripts — revisit for Localisation Phase A | `commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt:445` |
| LOW | snackbarHostState created inside Ready branch — resets on Ready→Failed→Ready retry | `commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:125` |
| LOW | Magic number 112.dp for POI list top padding — should be named or derived | `commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:153` |
| LOW | Three-stop bottom sheet deferred — V1 uses two stops. Requires anchoredDraggable with custom snap points. | `commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:286` |
| LOW | upgrade saves nearby to haversine radius — areaName match misses saves when geocoding drifts | `commonMain/kotlin/com/harazone/ui/map/MapScreen.kt:349` |
| LOW | Stage 2-only POIs (no coords, wiki fails) keep imageUrl=null after enrichment, | `commonMain/kotlin/com/harazone/data/repository/AreaRepositoryImpl.kt:102` |
| LOW | orbIconName is a dead field — unmapped in UI, overridden by toImageVector() | `commonMain/kotlin/com/harazone/domain/model/Vibe.kt:6` |
| LOW | COLLATE NOCASE only covers ASCII — Unicode place names won't deduplicate | `commonMain/sqldelight/com/harazone/data/local/recent_places.sq:2` |
| LOW | This test doesn't isolate cold-start seed vs observer path | `commonTest/kotlin/com/harazone/ui/map/MapViewModelTest.kt:1222` |
| LOW | Dedup is case-sensitive here but real DB uses COLLATE NOCASE — inconsistent test behavior | `commonTest/kotlin/com/harazone/fakes/FakeRecentPlacesRepository.kt:26` |
| LOW | Weak assertion (assertNotNull only) — doesn't verify UI actually renders | `androidInstrumentedTest/kotlin/com/harazone/AppLaunchSmokeTest.kt:25` |
| LOW | Custom POI icons per type (landmark, food, culture, nature) using annotation plugin SymbolManager | `androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt:248` |
| LOW | TalkBack per-marker contentDescription for map-mode accessibility | `androidMain/kotlin/com/harazone/ui/map/MapComposable.android.kt:249` |
| LOW | resolveActivity deprecated in API 33+ — replace with ResolveInfoFlags variant | `androidMain/kotlin/com/harazone/MainActivity.kt:67` |

---

_Last generated: 2026-03-13 03:02_
