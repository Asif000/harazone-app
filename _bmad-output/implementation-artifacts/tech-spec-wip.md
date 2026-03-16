---
title: 'AI Mirror Profile Page'
slug: 'ai-mirror-profile-page'
created: '2026-03-15'
status: 'in-progress'
stepsCompleted: [1]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'SQLDelight', 'Koin', 'Gemini API']
files_to_modify: []
code_patterns: []
test_patterns: []
---

# Tech-Spec: AI Mirror Profile Page

**Created:** 2026-03-15

## Overview

### Problem Statement

Users discover and save places but the app provides no reflection of their patterns, preferences, or evolving identity. The app knows the user — their favorite vibes, geographic footprint, time-of-day habits — but doesn't show it. There is no "mirror" that reflects who you are as an explorer.

### Solution

A living profile page that reads SavedPoi + visit tracking data, uses Gemini (non-streaming single call) to generate an AI identity (name, tagline, stats), and presents:
- Compact identity strip above the fold (avatar + AI-generated name + tagline + stats)
- Geographic footprint with country/city flags and visit counts
- Vibe capsules that expand inline showing AI insight + list of visited places
- AI chat agent anchored at bottom with suggestion pills for self-discovery
- Entry via top bar avatar/icon on the map screen

Place taps within vibe expansions open AiDetailPage. Profile evolves dynamically with every visit.

### Scope

**In Scope:**
- Profile screen composable (full-screen overlay from map)
- Identity strip: Gemini-generated name, tagline, avatar emoji, stats (visits, areas, vibes)
- Geographic footprint: country flags, city names, visit counts, tappable
- Vibe capsules: top vibes highlighted, expand inline with AI insight + place list
- Profile AI chat agent: new ProfileViewModel, dedicated Gemini prompt, suggestion pills
- Entry point: avatar/profile icon in map top bar
- PlatformBackHandler for dismissal
- Data reads from SavedPoi + visit tracking fields (provided by #52 Visit Feature)

**Out of Scope:**
- Backend infrastructure
- Push notifications
- Social layer / sharing
- Trip planning / itinerary
- Visit tracking data creation (handled by #52)

## Context for Development

### Codebase Patterns

_To be filled in Step 2_

### Files to Reference

| File | Purpose |
| ---- | ------- |

_To be filled in Step 2_

### Technical Decisions

- Non-streaming Gemini call for identity generation (like `generatePoiContext`)
- New `ProfileViewModel` (not reusing ChatViewModel — different concerns, different prompt context)
- Entry via top bar icon (no bottom nav exists)
- Place taps reuse existing `AiDetailPage`
- Visit data schema: expects visitCount, visitedAt, visitState fields (contract with #52)
- SavedPoi data as foundation — profile reads all saved + visited POIs to build identity

## Implementation Plan

### Tasks

_To be filled in Step 3_

### Acceptance Criteria

_To be filled in Step 3_

## Additional Context

### Dependencies

- Feature #52 (Visit Feature) — provides visit tracking data that the profile reads
- Parallel development: this spec defines the data shape it expects; #52 defines how data is written

### Testing Strategy

_To be filled in Step 3_

### Notes

- Brainstorm decisions: `_bmad-output/brainstorming/brainstorming-session-2026-03-15-002.md`
- Prototype v4: `_bmad-output/brainstorming/prototype-visit-journey-v4.html`
- Prototype shows: compact identity strip, geo pills with flags, vibe capsules with inline expansion, anchored chat with suggestion pills
