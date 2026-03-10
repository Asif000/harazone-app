---
stepsCompleted: [1, 2, 3-partial, party-mode-session-2, party-mode-session-3]
inputDocuments: [galaxy-saves-view.html]
session_topic: 'Discovery-to-Achievement unified model + AI companion vision + prompt engineering framework + prompt engineering deep-dive'
session_goals: 'Unify discovery/plan, define AI companion product framework, establish prompt engineering priorities, map engagement levels, define save-as-service-request, draft composable prompt architecture, define intent buckets, design taste profile system'
selected_approach: 'ai-recommended + party-mode'
techniques_used: ['First Principles Thinking', 'Constraint Mapping (partial)', 'Party Mode — Product Vision Workshop', 'Party Mode — Prompt Engineering Deep-Dive']
ideas_generated: [12 first principles, 4 constraint decisions, unified product framework, 5 engagement levels, 6-purpose save model, ask-first pattern, user notes as service requests, 8-layer prompt architecture, 5 universal intent buckets, taste profile system, context-shift resilience, intent-aware follow-ups, inverse-save for Surprise Me]
context_file: '_bmad-output/brainstorming/galaxy-saves-view.html'
session_status: 'complete — ready for quick spec'
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-09

## Session Overview

**Topic:** Galaxy Saves View — a dedicated full-screen saves experience replacing the basic bottom sheet

**Goals:**
- Determine what's realistic for v1 vs aspirational/later phases
- Ensure alignment with product vision: "Vibes are the starting point, AI is the depth, Itinerary is the outcome"
- Design the mobile interaction model (mockup is desktop-scale)
- Decide: replace FAB saves sheet or separate navigation destination?

### Context Guidance

Reference mockup (`galaxy-saves-view.html`) demonstrates 4 scenes:
1. **Galaxy Overview** — POI orbs clustered by area, starfield bg, vibe-colored glow zones, dashed connection lines, distance labels between clusters, galaxy/list toggle, "Plan my day" CTA, vibe legend
2. **Cluster Zoom** — Single area expanded, walking route with time labels between POIs, larger orbs with detail info
3. **List View** — Traditional scrollable list grouped by area, each POI shows name/why/meta/actions, area-level "Plan route" buttons
4. **Itinerary Galaxy** — Ordered numbered stops with route lines, time badges (arrival times), share button, back to saves

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Galaxy Saves View with focus on v1 scoping, mobile interaction, product vision alignment

**Recommended Techniques:**
- **First Principles Thinking:** Strip to irreducible truths before designing
- **Constraint Mapping:** Map real vs imagined constraints for v1 feasibility
- **Reverse Brainstorming:** Stress-test by finding failure modes

## Technique Execution Results

### Phase 1: First Principles Thinking (COMPLETE)

**12 First Principles Established:**

**[FP #1]: Active Wishlist, Not Dead Archive**
Saves feel alive, not archived. Not "things I saw" — "things I want to do."

**[FP #2]: Itinerary Foundation**
Saves are the raw ingredients; itinerary is the recipe. Bridge between Phase 2 (AI depth) and Phase 3 (itinerary builder).

**[FP #3]: Re-engagement Engine**
Real-time notifs, AI recs, proximity alerts — saves becomes the reason the app stays installed between trips.

**[FP #4]: Personal World Dashboard**
Info radiator across all scales — next door to globe. Always warm, always current.

**[FP #5]: AI Taste Profile**
Saves are INPUT to the AI, creating a learning flywheel: Explore > Save > AI learns taste > AI suggests more > Save more > Build itinerary > Visit > repeat.

**[FP #6]: Dynamic Ordering — The List is a Living Feed**
List reshuffles based on what's happening RIGHT NOW. Distance, open/closed, time-of-day relevance, events. Not static alphabetical/chronological.

**[FP #7]: Saves Never Die — Visited Places Stay Alive**
No "archive" or "mark as visited and forget." Every saved place remains in the constellation permanently, continuing to send signal. That jazz club you visited 6 months ago has live salsa tonight.

**[FP #8]: Engine First, Skin Later**
v1 = intelligent, living saves with dynamic reordering. Galaxy visualization is Phase 2+ polish. Intelligence before visual spectacle.

**[FP #9]: Saves is a Filter, Not a Destination Map**
No separate map for saves. Main map + "Saved only" filter = spatial view. Saves list = intelligent view. Two lenses on same data.

**[FP #10]: Saves = A Vibe, Not a Separate Screen** (BREAKTHROUGH)
"Saved" is a vibe chip alongside Character, History, What's On, etc. Same interaction model users already know. Tap the chip, map filters to gold pins, card rail shows saved POIs. No new screen, no new navigation, no FAB change needed. The entire "where does saves live" question evaporated.

**[FP #11]: Saved Vibe Follows Map Viewport**
Saved works exactly like every other vibe — shows what's in the current map view. Zoom in = nearby saves. Zoom out = city-wide saves. Zoom way out = global saves. Map viewport is the filter. Zero new patterns to learn.

**[FP #12]: One Card, One List — Saved is a Visual State, Not a Separate Component**
POI card and list row are the same component whether saved or not. Saved POIs get visual cues (gold pin, filled bookmark). No duplicate components. Save/unsave works identically in every context.

### Phase 2: Constraint Mapping (PARTIAL — paused)

**Constraints Resolved:**

**Real-time data (RESOLVED):** v1 uses local-only signals (GPS distance, cached hours, system clock for time-of-day). No new APIs. Google Places API = v2 unlock for verified hours, busyness, permanent closures. Gemini re-query = v3 for "what's new" updates. Push notifs = v4 aspirational.

**v1 sort signals:** Distance from current location, open/closed status, time-of-day relevance (coffee AM, bars PM), recency of save (tiebreaker).

**Navigation (RESOLVED via FP #10):** Saves is a vibe chip. No new screen, no FAB change, no bottom nav bar. Constraint eliminated.

**Map view (RESOLVED via FP #9 + #11):** No separate saves map. Saved vibe filters existing map. Zoom controls scope.

**Card/list design (RESOLVED via FP #12):** Reuse existing card and list components. Saved is a visual state (gold cues), not a separate component. Saves list and vibe list work the same way.

**Constraints Still To Map (resume tomorrow):**
- "Plan my day" CTA — where it lives, what it does (leaning: button on card rail header when Saved vibe active, opens AI chat pre-seeded with saved POIs)
- Empty state (zero saves)
- Saved vibe chip visibility (always shown, or only after first save?)
- Badge on chip showing save count
- Performance with large save counts (50+ saves)

### Phase 3: Reverse Brainstorming (NOT STARTED)

Deferred to next session. Will stress-test the "saves as vibe" design by asking "how could this fail?"

## Key Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| v1 visual approach | Intelligent list, no galaxy | Engine first, skin later |
| Navigation model | Saved = vibe chip | Reuses existing patterns, zero new screens |
| Map for saves | Filter on existing map | No duplicate map component |
| Real-time data v1 | Local-only (distance, hours, time) | No new API costs, validates concept |
| Card/list design | Reuse existing, add saved visual state | One component everywhere |
| Galaxy visualization | Phase 2+ | Deferred until saves proves value |
| Itinerary builder | Phase 3 (v1 = AI chat plan) | "Plan my day" = prompt to Gemini with saves context |

## v1 Scope Summary (emerging)

**Build:**
- "Saved" vibe chip (with badge count)
- Dynamic reordering of saved POI cards (distance, open/closed, time-of-day)
- "Plan my day" CTA when Saved vibe active (opens AI chat with saves context)
- Saved visual cues on existing card/list components

**Reuse (already built):**
- Gold pins on map
- POI card components
- Save/unsave infrastructure
- Map viewport filtering
- Vibe chip rail

**Defer:**
- Galaxy visualization (Phase 2+)
- Google Places real-time data (v2)
- AI "what's new" updates (v3)
- Push notifications (v4)
- Itinerary builder UI (Phase 3)

## Product Vision Alignment

"Vibes are the starting point. AI is the depth. Itinerary is the outcome."

- Saved AS a vibe = saves IS the starting point, literally a vibe chip
- AI taste profile from saves = AI learns from saves, deepens recommendations
- "Plan my day" CTA = itinerary emerges naturally from saves
- The flywheel: Explore vibes > Save > AI learns > Richer recs > More saves > Plan itinerary > Visit > Discover new area > repeat

## Core Insight (user-stated)

"Save and Plan are two sides of the same coin." Every save is a proto-plan. Every plan is built from saves. They are not separate features — they are one continuum. This must inform all design decisions going forward.

## Vision Reframe (Session 2 — 2026-03-09)

**Core Loop Reframe:** Discovery and Plan are NOT sequential steps. They are the SAME interaction viewed through different lenses. You discover BY planning. You plan BY discovering. Save is the bridge — the moment discovery becomes intent.

**Spectrum:** DISCOVERY ("what's here?") ---- ACHIEVEMENT ("I went and did it")

**Two modes of the same activity:**
1. VISUAL (map) — spatial discovery, spatial planning
2. TEXTUAL (list) — curated discovery, curated planning

**AI is the connective tissue** across the entire spectrum. The intelligence (taste profile, contextual recs) is what people pay for — not the map, not the list.

**Revenue insight:** "AI companion for discovery/travel" = monetization surface. AI gets smarter as you save/plan, creating a flywheel that's worth paying for.

**Priority #1:** Prompt engineering — discovery is only as good as the results. Generic tourist traps kill the flywheel before it starts.

**Key question raised:** Does the AI companion include implicit intent detection (user browses an area for 3 min, reads 4 cards, never taps save — AI should still know this area matters)? Or is explicit save the only bridge? (Answer: v2+ feature, explicit save is the bridge for v1.)

## Party Mode Session 2: AI Companion Product Framework (2026-03-09)

### The Unified Discovery-Plan Model

Discovery and Plan are ONE continuous activity, not sequential steps:

```
DISCOVER ──── SAVE ──── PLAN ──── GO ──── ACHIEVE
  "what's       intent    "when/     act     "did
   here?"       signal     how?"      on it    it"
```

Save is the pivot — the moment discovery becomes intent. Every save is a proto-plan. Every plan is built from saves.

### Two Time Horizons (AI-Detected, Not User-Declared)

The AI detects NOW vs LATER from signals — no user toggle needed:
- NOW: GPS in area, evening/weekend, browsing nearby
- LATER: remote/couch, morning research, distant city search

### Two Lenses on Same Data

| | VISUAL (Map) | TEXTUAL (List/Chat) |
|---|---|---|
| Discovery | Pins on map, vibe-filtered | AI chat results, card rail |
| Plan | Gold pins = saved, spatial view | Saved vibe chip, dynamic sorted list |
| Surface | SAME map component | SAME card/list components |

### Five Engagement Levels

| Level | Threshold | AI Behavior |
|---|---|---|
| FRESH | 0 saves | Ask everything, concierge, full pills |
| LIGHT | 1-5 saves | Hint at past taste, still ask intent |
| REGULAR | 6-29 saves | Suggest confidently, one-tap confirm |
| POWER | 30+ saves | Anticipate, proactive nudges |
| DORMANT | 14+ days away, saves > 0 | Re-engage warmly, "what's changed" |

Dormant takes priority over other levels. The AI EARNS the right to assume over time.

### Ask-First Pattern

AI asks before answering. How it asks depends on engagement level:
- FRESH: Full pill selection — "What are you in the mood for?" + [Date Night] [Family] [Solo] [Adventure] [Surprise Me]
- LIGHT/REGULAR: Shortcut pills referencing past taste — "Date night again, or something different?"
- POWER: No question needed — "3 of your saves are nearby. Rooftop vibes tonight?"
- DORMANT: Re-engagement — "Welcome back! Things have changed around here."

Pills are HYBRID: 3 instant presets (time-of-day rules) appear immediately + 2 AI-personalized pills slide in from lightweight Gemini call. If Gemini call fails, presets still work.

### Save Serves Six Purposes

One tap (+ optional note), six functions — user only sees #1:
1. BOOKMARK — "remember this"
2. INTENT SIGNAL — feeds taste profile
3. PLAN INGREDIENT — raw material for itineraries
4. AI CONTEXT — injected into future prompts
5. NOTEBOOK — personal curation, portable, exportable
6. SERVICE REQUEST — "do this for me" via user notes (NEW)

### User Notes on Saves

Save + optional note turns passive bookmark into active AI instruction:
- Long-press bookmark or "Add a note?" on save toast
- Unstructured natural language: "visit in June, keep me updated"
- AI parses intent from note, enriches future responses
- v1: add/delete only. Edit planned but deferred.
- Schema: single nullable `userNote: String?` column on SavedPoiEntity
- Revenue trigger: "keep me up to date" = subscription service request

### Fresh Install First-Open Flow

1. Map loads at current location
2. AI chat bubble pulses gently (inviting, not pushy)
3. User taps chat (or can use vibe chips — existing flow still works)
4. AI: "Hey! What are you in the mood for?"
5. Pills appear: contextual + preset fallbacks
6. User taps one
7. AI returns 3-5 curated results with WHY framing + meta-summary
8. "Save any that catch your eye — I'll learn your taste"
9. Optional: "Everything stays on your phone. Your data, always yours."

The AI IS the onboarding. No tutorial screens needed.

### AI Response Structure (Target)

```
meta_summary: "4 spots for a first date tonight — intimate,
  walkable, open now. I skipped the sports bars and tourist traps."

per POI:
  - why_this_fits_intent: specific to user's ask
  - insider_tip: atmosphere-based, not fabricated specifics
  - best_time, typical_duration, vibe_tags

footer: "Save any that catch your eye — I learn your taste"
```

Confidence calibration: well-known areas get specific tips, obscure areas get atmosphere descriptions. Never fabricate staff names, off-menu items, or secret passwords.

### Revenue Paths

- SHORT-TERM (NOW mode): transactional — reservations, tickets, real-time deals
- LONG-TERM (LATER mode): subscription — AI companion, trip planning, deal alerts, growing taste profile
- User notes as explicit service requests drive natural upgrade moments

### Privacy as Differentiator

"An AI that knows you but never tells anyone else." On-device saves, on-device taste profile, Gemini calls stateless. Data portable — export anytime. This is product positioning, not just a technical choice.

### Key Decisions Made (Session 2)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| NOW/LATER detection | AI-automatic, no user toggle | Reduce decisions, AI adapts |
| Ask-first pattern | Always ask on fresh, earn right to skip | Progressive familiarity |
| Pill generation | Hybrid: 3 instant presets + 2 AI pills | Zero latency + personalization |
| User notes on saves | Optional, unstructured text | Service request + AI context |
| Notes v1 scope | Add/delete only, edit later | Ship fast, schema supports edit |
| Privacy model | On-device first, export anytime | Differentiator + trust builder |
| Implicit intent detection | v2+ | Explicit save is v1 bridge |

### Roadmap Items Added

- Gamification / relationship milestones ("I'm learning your taste..." after 5th save)
- Google Places verification layer (v2)
- Implicit intent detection (v2+)
- Data export / portability
- Travel deals, flight alerts (long-term revenue)
- Dev test data seeder (DevSeederModule with persona presets)
- Note editing (v2)

### Next Session: Prompt Engineering Deep-Dive

The product framework is established. Next session should focus on:
1. Actual Gemini prompt text that delivers the "wow" first response
2. Intent injection into prompt (pill selection → prompt modifier)
3. Engagement level injection (tone/verbosity adaptation)
4. Save + note context injection (relevant subset, not all saves)
5. Confidence calibration rules (when to be specific vs vague)
6. Testing: side-by-side comparison of current prompt vs evolved prompt

## Party Mode Session 3: Prompt Engineering Deep-Dive (2026-03-09)

### 8-Layer Composable Prompt Architecture

Each layer is a separate pure function in `GeminiPromptBuilder`. Composed at call time. Total budget: 800-1000 tokens. Typical composed prompt: 430-550 tokens depending on engagement level and save count.

```
LAYER 1: personaBlock(areaName)           ~45 tokens  — stable persona
LAYER 2: areaContextBlock(pois)           ~40 tokens  — area orientation + cultural calibration
LAYER 3: intentBlock(intent)              ~70-90 tokens — per-pill, swappable
LAYER 4: engagementBlock(level)           ~55 tokens  — tone/verbosity/follow-up behavior
LAYER 5a: saveContextBlock(saves)         ~15-60 tokens — filtered explicit saves, max 8
LAYER 5b: tasteProfileBlock(profile)      ~0-80 tokens — derived patterns from all saves
LAYER 6: confidenceBlock(confidence)      ~30 tokens  — specific tips vs atmospheric
LAYER 7: contextShiftBlock()              ~35 tokens  — always present, handles pivots
LAYER 8: outputFormatBlock()              ~120 tokens — meta-summary + JSON POI rules
```

### 5 Universal Intent Buckets

Validated across age groups and cultures. These are INTENTS, not IDENTITIES — they answer "what do you want RIGHT NOW" not "who are you."

| Pill Label | Internal Key | Mission | Voice | Unique Mechanic |
|------------|-------------|---------|-------|-----------------|
| "Tonight" | TONIGHT | Memorable evening, multi-stop arc | Texting a friend | Evening flow/sequence |
| "Discover" | DISCOVER | Story of this area, hidden depth | Storyteller | Depth over breadth |
| "Hungry" | HUNGRY | Eat right now, open now | Decisive, punchy | Time-sensitive, cultural food norms |
| "Outside" | OUTSIDE | Feel the environment | Energizing | Sensory, weather-implicit |
| "Surprise me" | SURPRISE | Things they'd never find alone | Mischievous | Anti-pattern of saves (inverse filter) |

Why these 5 hold across demographics:
- Age-proof: intents are about MOOD, not life stage. A 22-year-old and 70-year-old both have "tonights"
- Culture-proof: local persona + cultural calibration line handles regional norms (late dinner in Spain, street food in Bangkok, etc.)
- No demographic profiling needed: taste emerges from saves, not from asking age/gender/budget

### Intent Block Drafts

TONIGHT (~90 tokens):
```
YOUR FRIEND ASKED: "What should I do tonight?"
YOUR MISSION: Recommend 3-5 places that create a MEMORABLE EVENING. Think in arcs — not isolated stops. Where to start, where to end, what order makes the night flow. Atmosphere matters more than ratings.
QUALITY MEANS: Worth telling a story about tomorrow. A plastic-stool street cart can beat a Michelin restaurant. No chains. No tourist traps. No places you'd personally skip.
VOICE: Warm, confident, like texting a close friend. Say "trust me on this" not "I recommend."
```

DISCOVER (~85 tokens):
```
YOUR FRIEND ASKED: "What makes this place special?"
YOUR MISSION: Reveal 3-5 places that tell the STORY of this area. Not the top-10 list — things a curious person would regret missing. Hidden history, local legends, architectural details, cultural undercurrents.
QUALITY MEANS: "I had no idea that existed." Skip anything on the first page of Google. Depth over breadth.
VOICE: Storyteller energy. The friend who makes a 15-minute walk take an hour because you keep stopping to point things out.
```

HUNGRY (~80 tokens):
```
YOUR FRIEND ASKED: "Where should I eat right now?"
YOUR MISSION: Recommend 3-5 places OPEN NOW or opening soon. What locals actually eat here, not tourist traps. A legendary street cart beats a mediocre sit-down.
QUALITY MEANS: You'd take your own family here. Adapt to local dining culture — street food, markets, cafes, whatever THIS place does best. Never default to Western restaurant assumptions.
VOICE: Decisive. "Go here, order this, thank me later." Include what to order if you know.
```

OUTSIDE (~80 tokens):
```
YOUR FRIEND ASKED: "Get me outside."
YOUR MISSION: Recommend 3-5 outdoor experiences — parks, walks, viewpoints, waterfronts, gardens, trails, beaches, plazas. Places where you FEEL the environment.
QUALITY MEANS: You'd go here on your day off. Not a parking lot with a "scenic overlook" sign. Real atmosphere.
VOICE: Energizing. "Grab your shoes, I know exactly where to go." Paint the sensory picture — what they'll see, hear, smell.
```

SURPRISE ME (~70 tokens):
```
YOUR FRIEND ASKED: "Surprise me."
YOUR MISSION: Pick 3-4 places they'd NEVER find on their own. Break the expected pattern. If the area is known for beaches, recommend underground jazz. If known for nightlife, recommend a dawn fish market.
QUALITY MEANS: "Wait, WHAT? That exists here?" Go weird. Go niche. Go hyperlocal.
VOICE: Mischievous. "Okay, you're not going to believe this, but..."
```

### Intent-Aware Follow-Up Questions (FRESH engagement only)

```
TONIGHT:  "Solo mission or bringing company?"
DISCOVER: "On foot or do you have wheels?"
HUNGRY:   "Any food preferences I should know?"
OUTSIDE:  "Leisurely stroll or proper adventure?"
SURPRISE: (no follow-up — just surprise them)
```

### Engagement Level Blocks

| Level | Behavior | Token cost |
|-------|----------|-----------|
| FRESH (0 saves) | Ask ONE follow-up. Warm, inviting. Explain reasoning. End with "Save any that catch your eye — I learn your taste." | ~55 |
| LIGHT (1-5) | Reference taste lightly: "Since you liked [type]..." Still conversational | ~45 |
| REGULAR (6-29) | Confident. Skip unnecessary explanations. Connect to preferences | ~40 |
| POWER (30+) | Reference saves by name. Anticipate. Proactive nudges. Brief | ~40 |
| DORMANT (14+ days away) | Lead with what's CHANGED. Warm re-engagement. "Welcome back!" | ~45 |

### Context-Shift Resilience (Layer 7)

Users don't think in modes — they evolve intent in real time. Three pivot types the AI must handle:
1. SOFT PIVOT — same area, different intent: "Actually, something more adventurous?" → AI re-steers with enthusiasm
2. HARD PIVOT — different area: "What about parks in Tokyo?" → AI bridges gracefully
3. INTENT BLEND — combined: "Date night but near a park" → AI combines lenses

Prompt instruction (~35 tokens):
```
CONTEXT SHIFTS: If the user changes mind, switches areas, or blends intents mid-conversation, roll with it enthusiastically. Acknowledge the shift, adapt immediately. Your initial framing is a starting point, not a cage.
```

Key rule: saves from the original area should NOT be forced into new-area context.

### Confidence Calibration (Layer 6)

Computed from area portrait POI count — zero extra API calls:
- HIGH (12+ POIs): Specific insider tips — best time, which section, what to try
- MEDIUM (6-11): Mix of specific and atmospheric
- LOW (<6): Atmospheric descriptions, honest about less documentation

Hard rule: NEVER fabricate staff names, off-menu items, or secret passwords regardless of confidence level.

### Save + Taste Profile System

Two distinct signals from saves:

SIGNAL 1 — Explicit Saves (Layer 5a):
- Filtered subset: geographic proximity + intent match + recency
- Max 8 saves injected, compact format
- Notes included for saves with user notes (max 5 notes)

SIGNAL 2 — Taste Profile (Layer 5b, NEW):
- Derived pattern across ALL saves, computed locally
- `TasteProfileBuilder` — pure function, runs after each save/unsave

```kotlin
data class TasteProfile(
    val strongAffinities: List<String>,   // types/vibes with 3+ saves
    val emergingInterests: List<String>,   // 1-2 recent saves
    val notableAbsences: List<String>,     // common types with 0 saves
    val diningStyle: String?,              // derived from food patterns
    val totalSaves: Int
)
```

Intent-specific taste usage:
- TONIGHT/DISCOVER/HUNGRY/OUTSIDE: positive filter (recommend what matches taste)
- SURPRISE ME: INVERSE filter (deliberately break their pattern)

The "by the way" moment: AI surfaces a user's own forgotten note at exactly the right time ("You mentioned wanting to catch that rooftop at sunset — tonight's actually perfect"). This is the retention/word-of-mouth moment.

Token budget by user type:
| User type | Layer 5 total | Full prompt total |
|-----------|--------------|-------------------|
| FRESH (0) | ~10 | ~430 |
| LIGHT (3) | ~40 | ~460 |
| REGULAR (15) | ~120 | ~530 |
| POWER (50+) | ~160 | ~550 |

All within the 800-1000 budget with headroom.

### User Note Injection

Notes are the most powerful signal — explicit intent, not inferred:
- Injected only when contextually relevant (same area, time-relevant, intent-match)
- Format: natural language, compact: `Taqueria Luna: "best al pastor, come back for Sunday brunch"`
- v1: inject all notes from current-area saves, cap at 5
- v2: contextual relevance filtering (time-of-day, intent matching)

### Cultural Calibration

Added to Layer 2 (area context), ~15 tokens:
```
Local dining culture: adapt to what "good food" means HERE — street carts, markets, fine dining, whatever fits this place.
```

Plus quality definition in all intent blocks:
```
QUALITY MEANS: memorable, worth the trip, has a story. NOT: has a website, has 4+ Google stars, looks good on Instagram. A plastic stool street cart can be better than a Michelin restaurant.
```

No demographic parameters. No age/gender/budget inputs. Cultural adaptation comes from the local persona + area context implicitly.

### Full Golden-Path Reference Prompt

Complete composed output for FRESH user + TONIGHT + HIGH confidence: see session notes above. ~430 tokens total. Radically different from current generic prompt (~180 tokens, "be a guide").

### Key Decisions Made (Session 3)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Prompt architecture | 8-layer composition | Testable, swappable, token-budgeted |
| Intent buckets | 5 universal (Tonight, Discover, Hungry, Outside, Surprise) | Age/culture-proof, mood-based not identity-based |
| Taste profile | Local computation from saves | Private, zero API cost, flywheel enabler |
| Context shifts | Resilience layer always present | Users don't think in modes |
| Confidence | Derived from POI count | Zero extra API calls |
| Cultural calibration | Implicit via persona + area context | No demographic profiling |
| Save injection | Filtered subset max 8 + notes max 5 | Token-efficient, relevance-filtered |
| Surprise Me mechanic | Inverse taste filter | More saves = better surprises |
| Photo scanning | Rejected | Contradicts privacy positioning |

### Implementation Path

1. Quick spec for prompt v3 refactor of `GeminiPromptBuilder.kt`
2. Implement 8-layer composition with Tonight as reference
3. Add TasteProfileBuilder (pure function, unit-testable)
4. Add remaining 4 intent blocks
5. PromptComparisonTest: side-by-side current vs v3
6. Iterate based on real results
