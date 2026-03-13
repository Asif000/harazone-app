---
title: 'Saved Vibe Chip + Count Badge + Dynamic Reordering'
slug: 'saved-vibe-chip-count-badge-dynamic-reordering'
created: '2026-03-11'
status: 'in-progress'
stepsCompleted: [1]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'SQLDelight']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/SavedPoi.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt'
code_patterns:
  - 'VibeRail is a vertical Column of VibeOrb elements on the right side of the map'
  - 'vibePoiCounts: Map<Vibe, Int> already in MapUiState.Ready — counts Gemini POIs, not DB saves'
  - 'savedPois: List<SavedPoi> already observed from DB in MapViewModel.init'
  - 'SavedPoi has no vibe field — needs DB migration if save-count-per-vibe is required'
  - 'switchVibe(Vibe) in MapViewModel sets activeVibe — Saved chip needs same tap toggle pattern'
  - 'Progressive area loading spec (in-dev) touches MapUiState, MapViewModel, MapScreen — layer on top'
test_patterns: []
---

# Tech-Spec: Saved Vibe Chip + Count Badge + Dynamic Reordering

**Created:** 2026-03-11

## Overview

### Problem Statement

The VibeRail shows 6 static vibe orbs (Character, History, What's On, Safety, Nearby, Cost) in a fixed order with no memory of what the user has saved. There is no way to filter the map to only show saved (gold) pins via the vibe rail, no per-vibe save counts visible, and no dynamic reordering based on the user's history in an area.

### Solution

Three layered changes to the VibeRail:
1. Add a "Saved" chip — taps toggle a filter showing only gold DB pins. No Gemini call needed.
2. Add count badges — small number on each orb showing saves per vibe in the current area. Zero = no badge.
3. Dynamic reordering — vibe orbs reorder by area-scoped save count (most-saved vibe first). New area = default order. Explored area = chips reflect history.

### Scope

**In Scope:**
- "Saved" VibeOrb pinned at top of VibeRail above a separator line
- Count badge on each vibe orb showing saves per vibe in the current area
- Dynamic reordering of the 6 vibe orbs by area-scoped save count (Saved chip stays pinned, not reordered)
- `savedVibeFilter: Boolean` state in MapUiState.Ready — when true, map shows only gold pins
- `vibeAreaSaveCounts: Map<Vibe, Int>` computed in MapViewModel from `savedPois` filtered to `areaName`

**Out of Scope:**
- SavedPlacesScreen changes (Feature #18 — separate spec)
- External API integration
- Social/sharing features
- Per-vibe save counts across multiple areas (global) — area-scoped only

## Context for Development

### Open Questions (MUST resolve before Step 2)

Three decisions deferred — user to answer next session:

**Q1 — How to count saves per vibe?**
`SavedPoi` currently has no `vibe` field.
- Option 1 (RECOMMENDED): Add `vibe: String` to `SavedPoi` + DB migration — save vibe at save time. Most accurate.
- Option 2: Derive vibe from `SavedPoi.type` at runtime via mapping. No migration.
- Option 3: Count badge on Saved chip only = total area saves. No per-vibe breakdown. Simplest, but weakens the feature.

**Q2 — Saved chip placement in VibeRail?**
Mockup at `/tmp/saved-vibe-chip-mockup.html`
- Option 1 (RECOMMENDED): Pinned at top, above a separator line. Always visible, always findable.
- Option 2: Participates in dynamic reordering — floats up naturally. Invisible on first visit (new area = 0 saves = bottom).
- Option 3: External gold pill button outside VibeRail, above AI bar. Different visual language.

**Q3 — Dynamic reordering scope?**
- Option 1 (RECOMMENDED): Area-scoped — sort by saves in current area only. Chips reflect THIS area's history.
- Option 2: Global — sort by total saves per vibe across all areas.

### Codebase Patterns

- `VibeRail` is a `Column` of `VibeOrb` components in `composeApp/.../ui/map/components/VibeRail.kt`.
- `vibePoiCounts: Map<Vibe, Int>` in `MapUiState.Ready` counts Gemini POIs per vibe — already displayed as orb size in `VibeRail`. Save counts are separate; do NOT overload this field.
- `savedPois: List<SavedPoi>` is observed from DB in `MapViewModel.init` and kept in state. Recompute save counts whenever `savedPois` or `areaName` changes.
- `switchVibe(Vibe)` toggles `activeVibe` — "Saved" chip needs its own toggle `onSavedVibeSelected()` that sets `savedVibeFilter = !savedVibeFilter` and clears `activeVibe`.
- When `savedVibeFilter = true`, map pin rendering must filter to only saved POIs (gold pins from DB), same suppression logic as the existing 50m suppression in the save-as-snapshot architecture.
- Progressive area loading spec (`tech-spec-progressive-area-loading.md`) is currently in-dev and touches `MapUiState`, `MapViewModel`, `MapScreen`. This spec must layer on top — do NOT duplicate or contradict those changes.
- `SavedPoi` DB schema: if Q1 = Option 1, need a new DB migration adding `vibe TEXT NOT NULL DEFAULT ''` column.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `domain/model/Vibe.kt` | 6 vibes: CHARACTER, HISTORY, WHATS_ON, SAFETY, NEARBY, COST — enum with displayName |
| `domain/model/SavedPoi.kt` | Saved POI model — needs `vibe: String` field if Q1=Option 1 |
| `ui/map/MapUiState.kt` | Add `savedVibeFilter: Boolean`, `vibeAreaSaveCounts: Map<Vibe, Int>` |
| `ui/map/MapViewModel.kt` | Add `onSavedVibeSelected()`, compute `vibeAreaSaveCounts`, reorder logic |
| `ui/map/MapScreen.kt` | Pass `savedVibeFilter` + `vibeAreaSaveCounts` to VibeRail; filter pins when active |
| `ui/map/components/VibeRail.kt` | Add Saved orb + separator; reorder vibes by save count; count badges |
| `ui/map/components/VibeOrb.kt` | May need count badge overlay added (check if already has badge slot) |

### Technical Decisions

Deferred pending Q1/Q2/Q3 answers.

## Implementation Plan

### Tasks

TBD — pending Q1/Q2/Q3 decisions.

### Acceptance Criteria

TBD — pending Q1/Q2/Q3 decisions.

## Additional Context

### Dependencies

- Must layer on top of `tech-spec-progressive-area-loading.md` (currently in-dev)
- If Q1=Option 1: DB migration required — increment schema version, add `vibe TEXT NOT NULL DEFAULT ''` to `saved_poi` table
- No new Koin modules needed
- No new libraries needed

### Testing Strategy

TBD — pending decisions.

### Notes

- Brainstorm ref: `_bmad-output/brainstorming/brainstorming-session-2026-03-11-001.md` ideas #18, #19, #20
- Mockup saved at `/tmp/saved-vibe-chip-mockup.html` (ephemeral — regenerate if needed)
- "Saved is a vibe" product principle — Saved chip must feel like a peer of Character/History/etc., not a secondary control
- Existing `savedPoiCount: Int` and `showSavesSheet: Boolean` in state are unrelated — do not repurpose them
