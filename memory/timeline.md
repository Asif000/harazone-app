# AreaDiscovery Timeline

## Epic Progress

| Epic | Stories | Target | Planned Days | Actual | Status |
|------|---------|--------|-------------|--------|--------|
| Epic 1: Skeleton & Streaming UX | 7/7 done | Phase 1a | ~7 | 0.5 | ✅ Done |
| Epic 2: Live Area Portrait & AI | 5/5 done | Phase 1a | ~5 | 0.5 | ✅ Done |
| Epic 3: Interactive Map & POI | 4/4 done | Phase 1a | ~4 | 1 (day 2) | ✅ Done |
| Epic 4: In-Map AI Conversation | 0/3 done | Phase 1a+b | ~4 | — | ⏳ Backlog |
| Epic 5: Onboarding & Permission Flow | 1/2 done | Phase 1b | ~3 | day 3 | 🔧 In Progress |
| Epic 6: Bookmarks & Saved Areas | 0/2 done | Phase 1b | ~3 | — | ⏳ Backlog |
| Epic 7: Offline Experience | 0/4 done | Phase 1b | ~5 | — | ⏳ Backlog |
| Epic 8: Adaptive Intelligence | 0/3 done | Phase 1b | ~4 | — | ⏳ Backlog |
| Epic 9: Safety, Trust & Engagement | 0/4 done | Phase 1b | ~4 | — | ⏳ Backlog |
| Epic 10: AI Depth (Phase 2) | placeholder | Phase 2 | — | — | ⏳ Backlog |
| Epic 11: Itinerary Builder (Phase 3) | placeholder | Phase 3 | — | — | ⏳ Backlog |
| Epic 12: Vibe Evolution (Phase 4) | placeholder | Phase 4 | — | — | ⏳ Backlog |

## Phase 1a Progress

**Epic 1:** ████████████ 100% (7/7)
**Epic 2:** ████████████ 100% (5/5)
**Epic 3:** ████████████ 100% (4/4)

**Phase 1a Total:** 100% ✅ Complete

## Active Quick Specs

| Spec | Status | Started |
|------|--------|---------|
| V3 Full-Screen Map | ✅ implementation-complete | 2026-03-06 |
| Localisation Phase A — AI Locale Injection | 🔧 in-progress | 2026-03-06 |
| Rebrand: HaraZone + Custom Icon | 🔧 in-progress | 2026-03-06 |
| Search Bar Dual Mode | ✅ implementation-complete | 2026-03-08 |
| iOS Map (MapLibre) | ✅ implementation-complete | 2026-03-08 |
| AI Chat v1 (Epic 4a) | 🔧 in-progress | 2026-03-08 |
| AI Chat v1.1 + Save Foundation | ✅ implementation-complete | 2026-03-08 |
| Saves Awareness (gold pins, pill, AI injection) | 🔧 ready-for-dev | 2026-03-08 |

## Schedule Status

**Ahead of schedule** — 16 stories done in 2 days vs ~16 planned days. Phase 1a complete!

## Actuals Log

| Date | Story | Notes |
|------|-------|-------|
| 2026-03-08 | Recent Places code review | 2H, 3M fixed (search bar active bug, upsert error handling, DB transaction, spec gap, missing test). 4L deferred to backlog. |
| 2026-03-04 | Epic 1 complete | All 7 stories done (day 1) |
| 2026-03-04 | Epic 2 complete | All 5 stories done (day 1) |
| 2026-03-04 | Story 3.1 done | MapLibre integration (day 1) |
| 2026-03-05 | Story 3.2 done | AI POI markers (11 code review rounds) |
| 2026-03-05 | Device bugs fixed | Camera zoom, POI cache-hit, tile provider (commit 437ed06) |
| 2026-03-05 | Story 3.3 ready-for-dev | POI Detail Card & Bottom Sheet |
| 2026-03-05 | Story 3.3 done | 3 code review rounds (1H, 3M, 3L fixed across rounds 1–2; 1M, 1L fixed in round 3) |
| 2026-03-05 | Story 3.4 ready-for-dev | POI Accessible List View — last story in Epic 3 |
| 2026-03-05 | Story 3.4 done | POI Accessible List View — code review (3M, 2L fixed: modifier bug, @Preview, safe key, capitalizedType, clickable/semantics order) |
| 2026-03-05 | Epic 3 complete | All 4 stories done — Phase 1a 100% complete |
| 2026-03-05 | Device bug fix | Slow app launch: lastLocation-first + cache-hit state mapping (commit e4c8c28) |
| 2026-03-05 | Sprint reorder | Story 5.1 (Manual Area Search) pulled ahead of Epic 4 (AI Chat) |
| 2026-03-05 | Story 5.1 ready-for-dev | Manual Area Search — SearchScreen, SearchViewModel, search_history DB, nav wiring |
| 2026-03-05 | Story 5.1 done | Implemented + code review 2 rounds (3M, 4L fixed: race condition, duplicate analytics, DB on IO, unused import, internal visibility, Turbine test, Loading state doc) |
| 2026-03-08 | iOS Map (MapLibre) done | Full MapLibre iOS via UIKitView: CocoaPods setup, MLNMapView, blue dot, POI pins, glow zones, camera fly-to, suppressCameraIdle guard, UITapGestureRecognizer deselect, simulator confirmed |
| 2026-03-08 | AI Chat v1 | ChatViewModel + ChatOverlay in progress (user implementing) |
| 2026-03-08 | AI Chat v1.1 + Save Foundation | Progressive POI cards, save foundation, satellite card backgrounds — complete |
| 2026-03-08 | Saves Awareness spec | Quick spec complete (18 tasks, 16 ACs, adversarial review 11 findings applied). Ready for Opus implement. Galaxy saves view backlogged (MEDIUM). |
| 2026-03-10 | Prompt v3 domain models | EngagementLevel, TasteProfile, TasteProfileBuilder, ChatIntent committed. user_note schema (migration 6). GeminiPromptBuilder + ChatViewModel wiring in progress. |
| 2026-03-10 | DevSeeder committed | Persona wiring + EngagementLevel integration. 5 personas, transaction-wrapped. |
| 2026-03-10 | Package rebrand | com.areadiscovery → com.harazone. 166 files. Android verified on device, iOS verified on simulator. |
| 2026-03-10 | CI fixed | google-services.ci.json updated (com.harazone + com.harazone.debug). Pre-push hook updated. iOS bundle ID set to com.harazone. |
| 2026-03-10 | Bugs + features logged | Features 16–21 added. Bugs: iOS cold start retry (HIGH), static vibe buckets (MED), real-time POI hours (CRITICAL). Arch note: saved_pois must store full pin data. |
