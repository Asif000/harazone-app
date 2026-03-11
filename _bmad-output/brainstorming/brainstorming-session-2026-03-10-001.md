---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: []
session_topic: 'Saved Places v2 + AI Behavior — 7 topics covering map pin matching, AI interaction patterns, card detail views, AI discovery stories, capsule UX, user notes display, and AI prompt latency/behavior'
session_goals: 'Generate concrete solutions for 7 interrelated issues spanning saved POI architecture, AI UX patterns, and prompt engineering'
selected_approach: 'ai-recommended'
techniques_used: ['cross-pollination', 'morphological-analysis', 'reverse-brainstorming']
ideas_generated: 39
context_file: ''
session_active: false
workflow_completed: true
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-10

## Session Overview

**Topic:** Saved Places v2 + AI Behavior — 7 interconnected topics + 1 bonus (map pan exploration)

**Goals:** Generate concrete, implementable solutions across saved POI architecture, AI interaction UX, and prompt engineering/latency

### Topics

1. CRITICAL: Saved POIs invisible on map — fuzzy matching or DB-independent rendering
2. HIGH: "Ask AI" should open ChatOverlay with intent pills, not auto-send hardcoded message
3. HIGH: Card tap detail view — SavedPoi lacks full fields (rating, hours, description)
4. HIGH: AI Discovery Story card — surface patterns, trends, vibe fingerprint
5. HIGH: Capsule label truncation + scroll affordance for area filters
6. MEDIUM: User notes display on saved cards
7. CRITICAL: AI prompt behavior (ask first, don't assume) + 15s latency reduction
8. BONUS: Map pan-to-explore without search box

### Session Setup

_Multi-topic brainstorm covering the full Saved Places v2 iteration plus AI behavior overhaul. Topics are interconnected — solutions in one area affect others (e.g., what we store at save time affects both map pins and detail views; AI behavior affects both chat and ask-AI flows)._

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** 7 tightly coupled topics across architecture, AI UX, and visual design

**Recommended Techniques:**

- **Cross-Pollination:** Borrow proven patterns from best-in-class apps (Google Maps, Spotify, ChatGPT, Pinterest, Airbnb, etc.)
- **Morphological Analysis:** Systematically map all solution dimensions per topic and lock optimal combinations
- **Reverse Brainstorming:** Stress-test decisions by asking "how could this go wrong?" to surface failure modes and defenses

## Technique Execution Results

### Phase 1: Cross-Pollination

Borrowed patterns from 15+ apps across all 8 topics. Key inspirations:

- **Google Maps / Spotify / Pinterest**: "Save is a snapshot" — store everything at save time, render from DB, never re-query
- **ChatGPT / Notion AI / Perplexity**: AI never auto-fires, shows intent pills, waits for user
- **ChatGPT / Perplexity**: Streaming tokens for perceived latency reduction
- **Stripe Docs AI**: Concise first, depth on demand
- **Spotify Wrapped**: Personality insights from user data, gamified discovery
- **Instagram / YouTube**: Fade edge scroll hint for horizontal chip rows
- **Google Maps / Airbnb**: "Search this area" floating button after map pan
- **GitHub Copilot Chat**: Context-aware entry with transparent context banner

### Phase 2: Morphological Analysis

Mapped solution dimensions for each topic and locked optimal combinations:

**Topic #1+#3 Save Architecture:** A3 (full snapshot) + B2 (separate gold pin layer) + C1 (detail from DB)
**Topic #7 AI Behavior:** A2 (intent pills) + B2 (streaming) + C2 (concise-first) + D2 (streaming-only latency fix)
**Topic #2 Ask AI Flow:** A1 (one ChatOverlay) + B2 (context banner) + C1 (static pills per surface)
**Topic #4 Discovery Story:** A3 (local fallback + Gemini primary) + B3 (hero insight + fingerprint) + C2 (next button) + D2 (pre-cached queue)
**Topic #5 Capsules:** A2 (smart truncation) + B2 (fade edge) + C1 (flat scroll for v1), D2 (long-press tooltip) nice-to-have
**Topic #6 User Notes:** A1 (inline subtitle) + B2 (icon only for no-note) + C1 (tap to edit inline)
**Topic #8 Map Explore:** A1 (floating button after 2s idle) + B2 (reverse geocode header update)

### Phase 3: Reverse Brainstorming

Surfaced 12 failure modes with defenses across all topics. Key findings:

- AI concise-first must still contain REAL value (names, specifics), not vague filler
- Chat messages must feel conversational (2-3 sentences like texting), not travel blog essays
- Streaming must NOT attempt partial JSON parsing — stream prose, parse POI JSON from completed response
- Suppress Gemini pins within ~50m of matching saved pin to avoid duplicates
- Vibe fingerprint requires minimum 5 saves to be meaningful
- Insight queue capped at 5 — "save more to unlock" after exhaustion
- Smart truncation must keep distinguishing segments when city names collide
- Cap AI depth at 2-3 levels, then offer actions (save, directions) not open-ended "more"

## Complete Idea Inventory

### THEME 1: Save Data Architecture

| # | Idea | Status |
|---|------|--------|
| 1 | Save-as-Snapshot — store full POI (name, lat, lng, type, area, description, rating, imageUrl, whySpecial) at save time | LOCKED |
| 2 | Dual Pin Layer — gold pins from DB (always visible), blue pins from Gemini (current query) | LOCKED |
| 3 | Detail View from Snapshot — card tap opens detail using stored data, no re-fetch | LOCKED |
| 35 | Defense: Suppress Gemini pin within 50m of matching saved pin name | LOCKED |
| 34 | Defense: Stale data acceptable for v1, flag saves older than N months later | ACCEPTED |
| 36 | Defense: DB size not a concern (~1MB at 500 saves) | ACCEPTED |

### THEME 2: AI Behavior + Prompt Engineering

| # | Idea | Status |
|---|------|--------|
| 5 | AI Never Auto-Fires — every AI surface opens with intent pills + text field, user initiates | LOCKED |
| 6 | Concise First, Depth on Demand — short 2-3 sentence answer, "go deeper" option | LOCKED |
| 32 | Conversational tone — "respond like a knowledgeable friend texting, not a travel writer" | LOCKED |
| 33 | Context-specific length: chat=2-3 sentences, card=1 sentence, story=1 punchy line, deeper=1 short paragraph | LOCKED |
| 29 | Defense: Concise must contain real value (place names, specifics), not filler | LOCKED |
| 31 | Defense: Cap depth at 2-3 levels, then offer actions (save, directions, share) | LOCKED |
| 28 | Defense: Pills must be specific/contextual ("What's open now near Bondi?"), not generic ("Explore") | LOCKED |

### THEME 3: Streaming + Latency

| # | Idea | Status |
|---|------|--------|
| 4 | Streaming Phase A — switch to generateContentStream() for ChatOverlay prose, display tokens as they arrive, <1s first visible token | LOCKED, FIRST TARGET |
| 30 | Defense: Stream prose only, extract POI JSON from completed response, no partial JSON parsing | LOCKED |

### THEME 4: Ask AI Interaction

| # | Idea | Status |
|---|------|--------|
| 17 | Context-Aware Intent Pills — different pills per entry surface (saves overview, specific card, map) | LOCKED |
| 18 | Pre-Filled Context Banner — shows "Asking about: [POI name], [area]" at top of ChatOverlay, dismissible | LOCKED |

Pill sets per surface:
- From saves overview: "Plan a day trip", "Find patterns in my saves", "What am I missing?"
- From a specific card: "Tell me more about [name]", "What's nearby?", "Is this worth visiting?"
- From map: existing pills (Hungry, Explore, etc.)

### THEME 5: Discovery Story Card

| # | Idea | Status |
|---|------|--------|
| 7 | Gemini-Generated Personality Insight — rich, personal, Spotify Wrapped energy | LOCKED |
| 8 | Local Heuristic Fallback — instant stats (vibe breakdown, area count) while Gemini loads | LOCKED |
| 19 | Single Hero Insight Card — one punchy line, bold, vibe-colored background | LOCKED |
| 20 | Vibe Fingerprint Visualization — radar/bar chart of vibe breakdown | LOCKED |
| 22 | "Next Insight" Button — tap to see another Gemini insight, slot-machine dopamine | LOCKED |
| 23 | Pre-Cached Insight Queue — generate 5 insights when saves change, instant "next" | LOCKED |
| 21 | Rotating Insights on each visit | NICE-TO-HAVE |
| 24 | Insight Counter / Collection gamification ("47 insights unlocked") | NICE-TO-HAVE |
| 37 | Defense: Cap queue at 5, show "Check back after your next save" when exhausted | LOCKED |
| 38 | Defense: Fingerprint requires minimum 5 saves, show encouragement below that | LOCKED |

### THEME 6: Capsule UX

| # | Idea | Status |
|---|------|--------|
| 9 | Smart Label Truncation — extract most distinctive segment (neighborhood/city), drop country/state unless ambiguous | LOCKED |
| 10 | Fade Edge Scroll Hint — gradient on trailing edge signals scrollability | LOCKED |
| 12 | Long-Press Tooltip — shows full area name | NICE-TO-HAVE |
| 11 | Hierarchical Grouping for 20+ areas (country -> city drill-down) | DEFERRED v2 |
| 39 | Defense: Keep distinguishing segment when city names collide (Springfield IL vs MA) | LOCKED |

Marquee/ticker text inside capsules: REJECTED — UX anti-pattern, touch target conflict, cross-platform complexity.

### THEME 7: User Notes

| # | Idea | Status |
|---|------|--------|
| 13 | Inline Note Subtitle — 1 line, truncated, muted style (smaller, lighter, italic) | LOCKED |
| 15 | Tap-to-Edit Inline — tap note text to open edit field, dismiss with done/tap-outside | LOCKED |
| 16 | No-Note State = icon only — small "add note" icon, no ghost text placeholder | LOCKED |

### THEME 8: Map Exploration

| # | Idea | Status |
|---|------|--------|
| 25 | "Explore This Area" Floating Button — appears after 2s map idle, triggers Gemini for current viewport center | LOCKED |
| 27 | Reverse Geocode Header Update — area name updates on map settle, before any Gemini call | LOCKED |
| 26 | Auto-trigger on idle | DEFERRED, API cost risk |

## Prioritized Implementation Order

| Priority | Theme | Key Ideas | Rationale |
|----------|-------|-----------|-----------|
| 1 | Save Architecture | #1, #2, #3, #35 | Fixes CRITICAL invisible-pins bug, unblocks detail view, unblocks discovery story |
| 2 | AI Behavior + Prompt | #5, #6, #32, #33, #28, #29, #31 | Fixes CRITICAL UX problem, prompt-engineering-only change, no code architecture change |
| 3 | Streaming Phase A | #4, #30 | Biggest perceived-latency win, API call change only |
| 4 | Ask AI Interaction | #17, #18 | Completes the AI behavior overhaul |
| 5 | Capsule UX | #9, #10, #39 | Quick visual win, small code change |
| 6 | User Notes Display | #13, #15, #16 | Quick visual win, small code change |
| 7 | Discovery Story Card | #7, #8, #19, #20, #22, #23, #37, #38 | Biggest feature, most work, highest delight |
| 8 | Explore This Area | #25, #27 | Enhancement, independent of other work |

## Key Decisions Log

1. "Save is a snapshot" — store full POI data at save time, never depend on Gemini re-query
2. Dual pin layers — gold (DB) and blue (Gemini) coexist independently
3. AI never auto-fires — always intent pills first, user initiates
4. Streaming Phase A first — prose streaming only, POI JSON from completed response
5. Chat = conversational tone, 2-3 sentences max
6. One ChatOverlay for all entry points, with context banner and surface-specific pills
7. Discovery Story = Gemini hero insight + local fallback + next button + pre-cached queue of 5
8. Capsules = smart truncation + fade edge, hierarchical grouping deferred
9. User notes = inline subtitle when present, icon when absent, tap to edit
10. Explore This Area = floating button after 2s idle + reverse geocode header
11. Marquee text in capsules REJECTED
12. Auto-trigger explore on pan DEFERRED (API cost)

## Session Summary

**39 ideas generated** across 3 techniques (Cross-Pollination, Morphological Analysis, Reverse Brainstorming) covering 8 themes. All 7 original topics plus 1 bonus topic resolved with locked architectural decisions and prioritized implementation order. 12 failure modes identified and defended against.
