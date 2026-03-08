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
| Epic 10: Voice & Push (Phase 2) | placeholder | Phase 2 | — | — | ⏳ Backlog |
| Epic 11: Advanced Exploration (Phase 3-4) | placeholder | Phase 3-4 | — | — | ⏳ Backlog |

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
| Search Bar Dual Mode | 🔧 in-flight (WIP) | 2026-03-08 |
| iOS Map (MapLibre) | ⏳ next — parallel with AI Chat | 2026-03-08 |
| AI Chat v1 (Epic 4a) | ⏳ next — parallel with iOS Map | 2026-03-08 |

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
