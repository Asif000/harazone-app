---
stepsCompleted: [1, 2, 3]
inputDocuments: []
session_topic: 'Post-Tester Roadmap Prioritization — what ships next after detail page polish (#47-50)?'
session_goals: 'Determine highest-impact next batch of work after tester release, balancing engagement vs infrastructure vs trust vs delight. Constraints: no backend, solo dev, first tester in Brazil 2026-03-21.'
selected_approach: 'ai-recommended'
techniques_used: ['Constraint Mapping', 'Six Thinking Hats']
ideas_generated: ['#1-#27']
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-15

## Session Overview

**Topic:** Post-Tester Roadmap Prioritization — what ships next after detail page polish (#47-50)?

**Goals:** Determine the highest-impact next batch of work after the tester release. Core question: engagement, infrastructure, trust, or delight? Constraints: no backend exists yet, first tester in Brazil on 2026-03-21, solo dev.

## Key Decisions

### 1. "Visit" Replaces "Save" as Core Action (#6, #7, #9, #15)

**Decision:** Single action — "Visit." AI determines the flow based on context (distance, time, POI type). States evolve: Visit → Visited → Return. No manual state management.

- **Nearby + Open** → AI responds inline with "Go Now" context (safety, crowd, transport, weather) — conversational, not a checklist
- **Nearby + Closed/Late** → Plan for soon, suggest best time
- **Far / International** → Plan Trip with seasonality, cost, visa context (needs backend for notifications)

**Key principle:** User doesn't choose the mode — the AI does. One verb, two paths.

### 2. Visit Flow is 2 Steps Only (#21)

**Decision:** Tap "Visit" → AI responds inline. No intermediate assessment screen. The AI response IS the assessment, woven into engaging context.

### 3. Auto-Detect Arrival, No Dedicated Screen (#13, #22)

**Decision:** Location proximity (50m + dwell time) auto-detects visits silently. No "You visited!" screen. Profile updates in background. Visit count = implicit rating (no stars needed).

### 4. AI Mirror Profile Page (#19, #23, #24, #25, #26)

**Decision:** Living profile page with:
- **Compact identity strip** — avatar + AI-generated name + tagline + stats (visits, areas, vibes) — above the fold
- **Geographic footprint with flags** — 🇧🇷 São Paulo (7), 🇯🇵 Tokyo (planned). Tappable → map. Planned visits show as aspirational.
- **Vibe capsules with icons** — tappable, expand inline showing AI insight + list of actual places. Each place tappable → detail page. Places show visit count, distance, area.
- **AI chat agent** — anchored input at bottom, scrollable conversation above. Suggestion pills: "What's my blindspot?", "What should I try next?", "Why night explorer?"
- **Dynamic** — page evolves with every visit. New traits appear, old ones change.

### 5. WOW Trio for Public Launch

**Selected:** Visit Action + AI Mirror Profile + Ambient Facts/Vibes
**All three are client-side — no backend blocker.**

### 6. Two-Phase Roadmap (#18, #19)

- **Tester release** = core loop validation (discover → engage → visit). Get solid feedback, not wow.
- **Public release** = full wow with Visit + AI Mirror + Ambient Facts + everything learned from tester.

## Constraint Map

| Candidate | Backend? | Core Loop Role | Tester Impact |
|-----------|----------|---------------|---------------|
| Visit / "Go Now" | NO (client Gemini) | COMPLETES loop | YES |
| AI Mirror Profile | NO (client MVP) | REFLECTS loop | YES |
| #16 Ambient Facts | NO (client Gemini) | DISCOVERY delight | YES |
| Epic 7 Offline | NO | PROTECTS loop | YES |
| Epic 8 Return Visits | Partial | EXTENDS loop | YES |
| #23 Buckets→Vibe | NO | ENGAGEMENT polish | YES |
| #43 Backend | IS the backend | ENABLES future | NO (invisible) |
| #33 Push Notifs | YES (blocked by #43) | RE-ENTRY | blocked |
| Epic 9 Emergency/Share | Partial | TRUST layer | YES |
| #19 User Profile | NO (client MVP) | REFLECTION | YES |

## Itinerary Discovery (#1-#5)

- Core insight: app's loop is discover → engage → GO. "Visit" is the missing bridge to action.
- Local "Go Now" = real-time feasibility (safety, crowd, transport) — client-side
- Travel "Go Later" = nurture loop (cost, season, visa) — needs backend
- "Visit" unifies both: AI routes based on context

## Ideas Generated (27 total)

1. Itinerary / "Get There" Flow
2. Local "Go Now" Action Bridge
3. Travel "Go Later" Nurture Loop
4. "Go Now" Pre-Navigation Decision Layer
5. "Go Now" as Gemini Prompt Extension
6. "Visit" — Single Action, AI Determines the Plan
7. "Visit" Replaces "Save Vibe"
8. Visit + Save as Two Distinct Intents
9. Single Action, State Evolves Over Time (SELECTED)
10. "Want" as Universal Action
11. Visited Places Get "Return" Treatment
12. Heart + Foot Metaphor
13. No Explicit "Visited" — AI Infers It (SELECTED)
14. Collect vs Visit Framing
15. "Visit" Single Action with AI-Evolved State (SELECTED DIRECTION)
16. "Visited" Unlocks Return Intelligence
17. Visit Count as Implicit Rating
18. Tester = Loop Validation, Public = Full Wow
19. Two-Phase Roadmap
20. The WOW Trio — Visit + AI Mirror + Ambient Facts
21. Visit Flow is 2-Step, Not 3 (SELECTED)
22. Visited State = AI Insight Only, No Stats Dashboard (SELECTED)
23. Profile Page = Living AI Agent, Not Static Card (SELECTED)
24. Profile Shows Geographic Footprint with Flags (SELECTED)
25. Geo Pill Taps Open City Visit Map
26. Vibe Capsule Taps Show Places (SELECTED)
27. Scrollable Visit History Below Chat

## Prototypes

- `prototype-visit-journey.html` — v1: full journey timeline + wow picker + 4-screen phone mockup
- `prototype-visit-journey-v2.html` — v2: 2-step visit flow, inline AI response, plan-later variant
- `prototype-visit-journey-v3.html` — v3: removed Visited screen, tappable vibes, anchored chat
- `prototype-visit-journey-v4.html` — v4 (CURRENT): compact profile, geo flags, vibe icons, places inside vibe expansions, chat agent with anchored input

## Next Steps

- Quick spec for Visit feature (client-side "Go Now" flow)
- Quick spec for AI Mirror Profile page
- Decide: should Ambient Facts (#16) get its own spec or fold into existing ticker architecture?
- Tester feedback will inform public release sequencing
