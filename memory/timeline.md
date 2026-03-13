# AreaDiscovery Timeline

Started: 2026-03-04 | Day 10 today (2026-03-13)

## Prototype Release Tracker

Target: 2026-03-17 (Mon) | Scope: FROZEN (new items go to v1.1)

### Shipped (18/25 = 72%)

| # | Feature | Date |
|---|---------|------|
| 1 | KMP Project + Design System | 03-04 |
| 2 | Domain Models + Mock AI | 03-04 |
| 3 | Streaming Composables | 03-04 |
| 4 | Analytics + Crashlytics | 03-04 |
| 5 | Location + Privacy Pipeline | 03-04 |
| 6 | Gemini API + SSE Streaming | 03-04 |
| 7 | Cache + Offline Fallback | 03-04 |
| 8 | Map + POI Markers (MapLibre) | 03-05 |
| 9 | Manual Area Search | 03-05 |
| 10 | POI Detail Card | 03-05 |
| 11 | Gemini Prompt v2 + v3 | 03-07 |
| 12 | POI Images (Wikipedia + MapTiler) | 03-07 |
| 13 | Search Bar Dual Mode | 03-08 |
| 14 | iOS Map (MapLibre) | 03-08 |
| 15 | AI Chat v1 + v1.1 (multi-turn, save, cards) | 03-08 |
| 16 | Package Rebrand (harazone) | 03-10 |
| 17 | Progressive Area Loading (two-stage) | 03-11 |
| 18 | Save Experience v2 (orb, badges, reorder) | 03-12 |

### Remaining (7 items)

| # | Item | Platform | Effort | Status |
|---|------|----------|--------|--------|
| 19 | Dynamic Vibes v1 + Chat Output Quality | Both | 2 days | IN DEV (Opus window) → CODE REVIEW WIP |
| 20 | Chat Overlay Full Screen | Both | 0.5 day | TODO |
| 21 | iOS map pin text labels | iOS | 0.5 day | TODO |
| 22 | Smoke test both platforms | Both | 0.5 day | TODO |
| 23 | Known issues doc for testers | — | 0.5 day | TODO |
| 24 | Debug build distribution (Firebase/TestFlight) | Both | 1 day | TODO |
| 25 | Final device test pass | Both | 0.5 day | TODO |

### Quality — Open Bugs

| Priority | Count | Items |
|----------|-------|-------|
| CRITICAL | 1 | Real-time POI hours (known issue for prototype) |
| HIGH | 1 | POI pins in water (known issue for prototype) |
| MEDIUM | 7 | Stale chat on reopen, static vibes, iOS pin labels, chat POI tap, AI question ending, intent pills disappear, follow-up POI cards |
| Backlog | 39 | `./scripts/generate-backlog.sh` |

### Known Issues (ship with prototype, note for testers)

- POI hours may be wrong (Gemini limitation — no real-time data)
- Coastal POIs may appear in water (needs POI lookup service)
- No onboarding — testers need to grant location permission manually
- English only
- Intent pills disappear after first tap
- Chat doesn't always end with a question

## Velocity

| Period | Features | Bugs | Reviews |
|--------|----------|------|---------|
| Day 1-2 (03-04 to 03-05) | 10 | 2 | 15 |
| Day 3-5 (03-07 to 03-08) | 6 | 2 | 4 |
| Day 7-8 (03-10 to 03-11) | 4 | 2 | 2 |
| Day 9-10 (03-12 to 03-13) | 2 | 3 | 2 |
| TOTAL | 18 features | 11 fixes | 23 reviews |

Avg: ~2 features/day

## Active Specs

| Spec | Status |
|------|--------|
| Dynamic Vibes v1 + Chat Output Quality | IN DEV + CODE REVIEW WIP — 2026-03-13. Spec: `tech-spec-dynamic-vibes-v1-chat-output-quality.md` |
| Golden Path Regression Test | SPEC DONE — implement after dynamic vibes lands, in same review window. Spec: `tech-spec-golden-path-regression-test.md` |
| Localisation Phase A | Spec exists (post-prototype) |
| Saved Places List Redesign | Spec done (post-prototype) |
| Rebrand: HaraZone | WIP (tasks 1-5 + 8-9 pending) |
