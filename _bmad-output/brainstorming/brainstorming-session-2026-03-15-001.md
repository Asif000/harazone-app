---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: []
session_topic: 'AI Detail Page Experience — Nav/UI, Prompt Engineering, Dynamic Context'
session_goals: 'Define navigation model, strengthen prompt contract, design proactive context section — scoped for 2026-03-21 tester release'
selected_approach: 'ai-recommended'
techniques_used: ['Role Playing', 'Morphological Analysis', 'Resource Constraints']
ideas_generated: 26
context_file: ''
---

# Brainstorming Session: AI Detail Page Experience

**Facilitator:** Asifchauhan
**Date:** 2026-03-15
**Duration:** ~45 min
**Techniques:** Role Playing → Morphological Analysis → Resource Constraints

## Session Overview

**Topic:** AI Detail Page Experience — unified brainstorm across 3 related features
**Sub-Topics:** #47 Nav/UI overlap, #48 Prompt engineering, #49 Dynamic context section
**Constraint:** Tester release 2026-03-21

## Key Decisions

### #47 Detail Page Nav/UI Overlap
- **Hide main CTAs** (bottom nav, ambient ticker, carousel) when detail page is open. FAB persists.
- **Map as ambient backdrop strip** — top ~10-15%, non-interactive, visible for spatial context.
- **Two-tap progressive disclosure confirmed** — pin tap → carousel card, card tap → detail page.
- **Simple scroll** — no sticky headers, everything scrolls naturally. v1.1 if testers request.

### #48 Prompt Engineering + AI Detail Screen
- **Hybrid response format** — JSON for structured fields (pois[], status, priceRange), free-form prose for contextBlurb/whyNow/localTip.
- **Belt-and-suspenders POI card guarantee** — prompt mandates tapped POI in pois array + client-side injection fallback.
- **Light-touch history** — single sentence in prompt: "Include one sentence about the place's history, origin story, or neighborhood's cultural significance."
- **Field-level fallback** — contextBlurb/whyNow get metadata-generated fallbacks, localTip hides if empty, pois gets client injection.
- **Anti-clustering prompt contract** — AI text MUST appear between POI cards. No back-to-back cards. Client-side divider fallback.
- **Follow-up chat aware of context block** — context block content included in chat conversation history.
- **Prompt regression test** — manual checklist with 10 diverse POIs, verify structure before every prompt change.
- **Tiered prompt contract** — must-ship (time-awareness, POI guarantee, history) vs stretch (weather, season, user history).

### #49 Dynamic Detail Page Context Section
- **AI Context Block** as own section between hero and chat — not inside hero card, not a chat bubble.
- **Three-layer stack:** Hero (identity) → AI Context Block (dynamic value) → Chat (follow-up).
- **Context-aware generation** — time of day (must-ship), weather/season/user history (defer to v1.1).
- **Shimmer-to-content transition** — hero loads instantly from cache, context block shimmers, chat is non-blocking.
- **AI context as persistent asset** — cache locally, phase 2 feeds shared knowledge layer (post-backend).

### Theme / Visual
- **Light theme by default** for detail page below hero. Warm off-white (#F5F2EF), not cold white.
- **Hero stays dark** (it's a photo). Transition from dark hero to light content at POI info section.
- **System theme support** — light default, dark mode swaps to current palette. Ship light-only for tester, add dark toggle as fast follow.
- **Chat visual hierarchy** — lighter background, white AI bubbles with hairline border, indigo user bubbles pop on light bg.
- **Placeholder text:** "Ask about this place..." (contextual, not generic).
- **POI cards inline vertical** — full-width in chat flow, not horizontal carousel. Status stripe, thumbnail, AI insight, meta row, action buttons.

## Scoping — Tester Release (2026-03-21)

### MUST SHIP (15 items)
| # | Item | Sub-Topic |
|---|------|-----------|
| 1 | Hide main CTAs on detail page (keep FAB) | #47 |
| 2 | Map as ambient strip | #47 |
| 3 | AI Context Block as structured section | #49 |
| 5 | Two-tap flow (already built) | #47 |
| 6 | Context Block between hero and chat | #49 |
| 7 | Lighter chat theme (light bg default) | #48 |
| 8 | Fallback template (field-level) | #48 |
| 10 | History/culture sentence in prompt | #48 |
| 11 | POI card guarantee (prompt + client) | #48 |
| 12 | Chat aware of context block | #48 |
| 14 | Tiered prompt contract | #48 |
| 15 | Prompt regression test (10 POIs) | #48 |
| 17 | Three-layer stack layout | #49 |
| 18 | Hybrid response format | #48 |
| 21 | Simple scroll, no sticky | #47 |
| 22 | Shimmer-to-content transition | #49 |
| 23 | Non-blocking chat | #49 |

### SHOULD SHIP (3 items)
| # | Item | Sub-Topic |
|---|------|-----------|
| 4 | Time-awareness must, weather/season/user defer | #49 |
| 19 | Field-level fallback strategy | #48 |
| 24 | Parallel context/chat coherence | #49 |

### DEFER v1.1 (2 items)
| # | Item | Sub-Topic |
|---|------|-----------|
| 9 | AI context as persistent asset / cache | #49 |
| — | Dark mode toggle (ship light-only first) | Theme |

## Prototypes

- `prototype-detail-page-redesign.html` — Side-by-side: current dark vs proposed light theme with 3-layer stack, color swatches
- `prototype-detail-page-inline-pois.html` — Full conversation flow with inline vertical POI cards + card anatomy
- `prototype-detail-page-states.html` — State progression: launched (shimmer) → context loaded → conversation with anti-clustering POI cards

## Prompt Contract Spec (for implementation)

### Required JSON fields
```json
{
  "contextBlurb": "2-3 sentences, time-aware, with history touch",
  "whyNow": "Why visit at this moment",
  "localTip": "Insider knowledge (optional — hide if empty)",
  "pois": [
    {
      "name": "...",
      "emoji": "...",
      "type": "...",
      "lat": 0.0,
      "lng": 0.0,
      "insight": "1-2 line AI-generated hook",
      "rating": 4.5,
      "priceRange": "$$",
      "status": "open|closing|closed|unknown",
      "hours": "..."
    }
  ]
}
```

### Anti-clustering rules
1. Every POI mentioned by name MUST have a pois array entry
2. Between POI cards, include brief text (context/comparison/transition)
3. Exception: explicit "list" request → max 3 consecutive cards with text intro + summary
4. Tapped POI always guaranteed (prompt + client-side)
5. Client fallback: if consecutive cards with no text, insert divider

### Prompt additions
- "Include one sentence about the place's history, origin story, or the neighborhood's cultural significance."
- "ALWAYS return the focused POI in the pois array."
- "When mentioning multiple places, include a brief text transition between each."
