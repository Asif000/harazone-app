---
stepsCompleted: [1, 2, 3]
inputDocuments: []
session_topic: 'Saves + Map Experience — 6 topics covering AI Discovery Story, User Notes UI, Saved Places Map View, Ambient Map Facts, Bucket Data UI, Saved Vibe Chip'
session_goals: 'Generate concrete solutions for 6 interrelated features spanning saved places experience, map ambient content, and bucket data utilization'
selected_approach: 'ai-recommended'
techniques_used: ['cross-pollination', 'concept-blending']
ideas_generated: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
context_file: ''
---

# Brainstorming Session Results

Facilitator: Asifchauhan
Date: 2026-03-11

## Session Overview

Topic: Saves + Map Experience — 6 interconnected topics

Goals: Generate concrete, implementable solutions for the saves/map experience layer

### Topics

1. AI Discovery Story — personality insights, vibe fingerprint, gamification (#24)
2. User Notes UI — inline subtitle, tap-to-edit, no-note icon (#25)
3. Saved Places Map View — show all saved POIs on dedicated map (#26)
4. Ambient Map Facts — "Surprise Me" ephemeral bubbles (#16)
5. Buckets — what to do with CHARACTER/HISTORY/SAFETY/COST/NEARBY data (#23)
6. Saved Vibe Chip + Dynamic Reordering (#10)

### Session Setup

Six related topics all touching what the user sees on the map and in their saves experience. Interconnections: Discovery Story uses save data (#1) which includes notes (#2). Saved Places Map (#3) could show ambient facts (#4). Bucket data (#5) could feed ambient facts. Vibe chips (#6) connect to how saves are categorized.

## Phase 1: Cross-Pollination

### Topic #5: Bucket Data UI

Bucket data (CHARACTER, HISTORY, SAFETY, WHATS_HAPPENING, COST, NEARBY) currently has no UI home. Cross-pollinated with vibe chips and ambient facts.

1. Area Context Card — summary card at top of area showing key bucket insights (safety score, cost level, character summary)
2. Bucket Drawer — swipe-up panel organizing bucket data by category with icons
3. Bucket Map Overlays — toggle layers on map (safety heatmap, cost zones, nearby transit)
4. History Timeline — chronological view of area's history from HISTORY bucket
5. Vibe Detail Panel (SELECTED) — tapping a vibe chip opens a detail panel that shows relevant bucket data mapped to that vibe. Bucket-to-vibe mapping: Hungry = COST + CHARACTER, Character = CHARACTER + COST + SAFETY + NEARBY, History = HISTORY + CHARACTER, What's On = WHATS_HAPPENING + COST, Safety = SAFETY + NEARBY + CHARACTER
6. Ambient Fact Bubbles (SELECTED) — ephemeral bubbles that pop in from screen edges with bite-sized facts from bucket data. Contextual to visible area. Fade in/out every 30-60s. Not intrusive.
7. Bubble-to-Vibe Bridge — tapping an ambient bubble opens the relevant vibe detail panel, creating a discovery flow: see bubble → get curious → dive into vibe panel → explore deeper

DECISION: Concept B (Vibe Detail Panel) + Concept C (Ambient Fact Bubbles) selected as the combo. Buckets feed vibes, bubbles create passive discovery, tapping a bubble bridges to the vibe panel.

### Topic #1: AI Discovery Story

8. Discovery Story Hero Card (SELECTED) — rich card at the top of the Saved Places list. Shows vibe fingerprint (donut/radar chart of save categories), personality insights ("You're a night owl who gravitates toward hidden jazz spots"), pattern analysis ("You save 3x more food spots on weekends"), milestone badges. Gemini-generated from day 1 save data. Updates as saves grow. Lives in saves/profile — the natural place for self-reflection.

Cross-pollination links:
- Connects to Bucket data (#5): Discovery Story can reference bucket insights
- Connects to Saved Vibe Chip (#6): The vibe distribution in the hero card IS the chip data visualized differently
- Connects to User Notes (#2): Notes feed personality insights

### Topic #2: User Notes UI

9. Inline Note Subtitle — note text shows as a second line under the place name on save cards. If no note, show a subtle pencil icon as a "tap to add" affordance. Tap the note text or icon → inline edit field (no modal).
10. Note Echoes in Discovery Story — user notes feed into the Hero Card's personality insights. Notes with recurring words get surfaced as patterns ("You mention 'brunch' in 5 notes").
11. Note-Powered Ambient Bubbles — when near a saved place that has a note, the user's own note appears as a personal bubble on the map ("You wrote: 'best at sunset' — it's 5:30pm").

### Topic #3: Saved Places Map View

12. Dedicated Saves Map — toggle in Saved Places list switches to a map view showing ALL saved POIs as gold pins. No Gemini call needed — purely from local DB. Cluster pins when zoomed out, expand on zoom. Tap pin → mini card with name, vibe chip, note preview.
13. Saves Heatmap Layer — heatmap overlay revealing "exploration zones" — areas dense with saves glow warm. Feeds into Discovery Story.
14. Saves Map + Ambient Facts Combo — when viewing the saves map, ambient fact bubbles appear contextually near saved places. Saves become anchors for new discovery.

### Topic #4: Ambient Map Facts

15. Vibe-Filtered Bubbles — bubbles pull from the bucket-to-vibe mapping. When a vibe chip is active, bubbles match that vibe. No chip active → random mix. Ephemeral — fade in/out every 30-60s.
16. Proximity Triggers — bubbles appear more frequently near saved places or when GPS detects the user is walking (not driving). Rewards exploration on foot.
17. Bubble Depth Tap — tap a bubble → it expands into a mini detail panel (Vibe Detail Panel from concept B). Second tap → opens full AI chat with that topic pre-loaded as context.

### Topic #6: Saved Vibe Chip + Dynamic Reordering

18. Save Count Badge on Vibe Chips — each vibe chip shows a small count of saves in that category for the current area. "Hungry (3)" — instant signal of engagement depth.
19. Dynamic Chip Reordering by Saves — vibe chips reorder based on save count — most-saved vibe first. New area with no saves → default order. Returning to an explored area → chips reflect history there.
20. "Saved" as a Vibe Chip — a special chip that filters the map to ONLY show gold pins (saved places). Consistent with the "saved is a vibe" principle. Tapping it = the saves map view (#12) but inline, no screen change.

## Phase 2: Organization

### Themes

| Theme | Ideas | Core Principle |
|-------|-------|----------------|
| Ambient Discovery | #6, #7, #11, #14, #15, #16, #17 | Map comes alive with contextual, ephemeral content |
| Save Intelligence | #8, #10, #13, #18, #19, #20 | Saves are data — surface patterns and meaning |
| Vibe Integration | #5, #7, #15, #17, #18, #19, #20 | Vibes unify everything — buckets, saves, bubbles |
| User Notes | #9, #10, #11 | Notes are micro-content that feed the whole system |
| Map Views | #12, #13, #14, #20 | Multiple ways to see your saved world |

### Key Connections

- #20 ("Saved" chip) + #12 (Saves Map) = same feature, inline vs dedicated
- #15 (Vibe-filtered bubbles) + #5 (Vibe Detail Panel) + #17 (Bubble depth tap) = the full B+C interaction chain
- #8 (Discovery Story) + #10 (Note echoes) + #13 (Heatmap) = three data sources feeding one identity view
- #19 (Dynamic reorder) + #18 (Count badge) = complementary — do both together

### Priority Matrix

| Priority | Ideas | Why |
|----------|-------|-----|
| NOW (v1) | #9, #12, #18, #19, #20 | Low effort, high UX value, no AI dependency |
| NEXT (v1.1) | #5, #6, #7, #15, #17 | The B+C ambient system — needs Gemini prompt work |
| LATER (v1.2) | #8, #10, #13, #14 | Discovery Story + intelligence layer — needs save volume |
| ROADMAP | #11, #16 | GPS triggers, proximity notes — need real usage data |

### 5 Key Decisions

1. "Saved" chip (#20) replaces dedicated saves map screen (#12) — inline, no navigation change
2. Ambient bubbles (#6, #15) launch with vibe filtering from day 1 — never random
3. Discovery Story (#8) deferred until users have enough saves to make it meaningful (5+ saves minimum)
4. User notes (#9) ship as inline subtitle immediately — zero AI dependency
5. Dynamic chip reorder (#19) + count badge (#18) ship together as one unit
