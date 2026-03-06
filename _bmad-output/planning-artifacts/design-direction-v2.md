# AreaDiscovery Design Direction v2

**Session Date:** 2026-03-06
**Method:** Multi-agent party mode design retrospective (Sally, John, Winston, Mary)
**Trigger:** Pre-Epic 4 design review — "aha moment not strong enough yet to earn AI chat"

---

## Core Insight

The original "whoa" moment was never about any single piece of information.
It was the **collision of information across time and context in one view** — the combination itself IS the product.

The six-bucket structure is logically correct but emotionally flat. Text for everything is the wrong container. Different information deserves different visual treatments.

---

## Revised Product Frame

AreaDiscovery is a **place story engine** — not a directory, not a guide.

Every place has two things any human always cares about, regardless of whether they are a local or tourist:

| Layer | What it answers | Emotional register |
|-------|-----------------|--------------------|
| **The Story** | Why does this place matter? What happened here? | Nostalgia, meaning, context |
| **Right Now** | What's on? What not to miss? What to do today/tonight? | Excitement, urgency, FOMO |

Everything else (Safety, Character, Cost, Nearby) is supporting depth — present but not competing for top billing.

---

## Revised UX Structure

### Primary Experience (Hero)

```
① Timeline card     — place story arc from past to now     [visual, horizontal scroll]
② Right Now card    — events, things to do, what's on      [urgency, bold, time-aware]
③ Map               — POIs, dense, explorable              [needs more pins]
```

### Secondary Experience (Depth)

```
④ Character         — vibe, culture, who lives here
⑤ Safety            — color field, not a paragraph
⑥ Cost              — simple data viz, trend arrow
⑦ Nearby            — walking distance, categories
```

---

## Timeline Card — Key Design Direction

The timeline is the signature new feature. Core concept: **a place's story arc as a horizontal scroll**.

- 3-5 moments per place: e.g. *1923 → 1967 → 2012 → now*
- Each moment: one photo/illustration + one sentence
- Covers ancient history AND recent history (e.g. "Venice Beach: Snapchat's HQ opened here in 2012")
- Temporal framing makes history feel alive, not academic
- In AR Phase: point camera at a building and see its past version ghosted over it

---

## Right Now Card — Key Design Direction

- Events happening today or soon (sport games, markets, festivals, concerts)
- Bold time/countdown display
- "Don't miss" framing — urgency is the design language
- Actionable: one tap to navigate or get more info
- This IS the "what to do" answer — no separate category needed

---

## Visual Language Overhaul

Current state: all text, no graphics, no animation, very basic.
Direction:

| Content type | Visual treatment |
|-------------|-----------------|
| Timeline | Horizontal scroll, photo + sentence per era |
| Right Now | Bold event card, countdown, time-of-day color |
| Safety | Color field (green/amber/red), not a paragraph |
| Character | Illustration or mood imagery, not description |
| Cost | Trend arrow + median figure, not a paragraph |
| Map | Progressive pin loading — starts with 5-6 AI pins, fills in as background enrichment pass |

**Animation direction:**
- Map pins appear progressively (map "waking up" as more pins load)
- Bucket headers have subtle entrance animations
- Timeline card horizontal scroll with momentum
- Streaming text is already the signature — preserve it

---

## POI Density Fix

Current: 5-6 pins (AI prompt constraint)
Direction: Two-pass loading
1. First load: 5-6 AI-curated "best of" pins (fast, current behavior)
2. Background enrichment: second async AI call adds 20-30 more pins as the user reads the summary
3. Result: map feels alive and filling in — the density problem AND an animation moment solved together

---

## Staleness / Retention Solution

Problem stated by user: same location every day → same data → app gets stale quickly.

**Short-term fix (low effort):**
- Rotate which facts surface as the hero sentence each session
- AI generates 30+ facts — show different ones each load
- Same location feels fresh through varied entry points

**Medium-term fix — Discovery Engine (Phase 2):**
- Surfaces nearby places the user has NOT explored yet
- "Today's discovery: X — 8 min away, you've never been"
- One card per session, proactive recommendation
- Uses visit_history table + DiscoveryUseCase querying unvisited places within configurable radius
- Expands the user's world outward gradually from their home base

---

## Phase Roadmap (Revised)

| Phase | What it delivers | Key new capability |
|-------|-----------------|-------------------|
| **Phase 1** | Cards + Map + Timeline + Right Now | Story Engine — redesigned UX |
| **Phase 2** | Discovery Engine | Retention — find new places near you |
| **Phase 3** | AR Camera layer | Experience — see stories through the camera |

**Priority rationale:**
- Discovery Engine (Phase 2) over AR (Phase 3) — solves retention first, AR needs somewhere worth pointing the camera at
- AR becomes the payoff when Discovery Engine sends you somewhere new

---

## Impact on Epic 4 (AI Chat)

The party mode session confirmed: **the aha moment isn't strong enough yet to naturally lead to AI chat.**

AI Chat earns its place when the passive experience (Timeline + Right Now) is compelling enough that users are bursting with a specific question. The chat entry point should feel like a natural extension of curiosity triggered by the timeline or a Right Now event — not a generic "ask about your area" prompt.

**Recommendation:** Implement Timeline card and Right Now card visual redesign before or alongside Epic 4. The chat prompt should be contextual — seeded from the specific timeline moment or event the user was just reading.

---

## What Does NOT Change

- The six-bucket structure as the data layer — stays, just repositioned as depth
- Streaming text as the delivery mechanism — preserve as signature interaction
- The "combination of info in one place" as the core value — this IS the product
- KMP architecture — no platform changes implied
- Summary-first, map-second layout — still correct

---

## Open Questions (Deferred)

- Real estate / cost data: tabled for future iteration (Phase 1b or later)
- AR implementation: ARCore, Android-native initially, KMP AR story not mature yet
- Discovery radius: walking (10-15 min) vs driving (10-15 min) — flex by context TBD
- Timeline data source: AI-generated vs structured historical data API
