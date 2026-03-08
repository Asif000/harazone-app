# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AreaDiscovery** is a project currently in the planning/pre-development phase. No application code exists yet. The project uses the BMAD (Build Measure Assess Decide) methodology (v6.0.4) for structured project planning and development workflows.

## Project Status

Pre-development — use BMAD workflows to progress through planning before writing application code.

## Directory Structure

- `docs/` — Project knowledge and documentation
- `_bmad-output/planning-artifacts/` — BMAD planning outputs (product briefs, PRDs, architecture docs, UX designs)
- `_bmad-output/implementation-artifacts/` — BMAD implementation outputs (stories, sprint plans)
- `_bmad/` — BMAD framework internals (do not modify directly)
- `.claude/commands/` — BMAD slash commands for Claude Code

## BMAD Workflow

BMAD provides a structured progression from idea to implementation. The typical flow:

1. **Discovery**: `/bmad-bmm-create-product-brief` — Define the product vision
2. **Research**: `/bmad-bmm-domain-research`, `/bmad-bmm-market-research`, `/bmad-bmm-technical-research`
3. **Requirements**: `/bmad-bmm-create-prd` — Create product requirements document
4. **Design**: `/bmad-bmm-create-ux-design` — Plan UX patterns and specifications
5. **Architecture**: `/bmad-bmm-create-architecture` — Define technical architecture
6. **Planning**: `/bmad-bmm-create-epics-and-stories` → `/bmad-bmm-sprint-planning`
7. **Readiness check**: `/bmad-bmm-check-implementation-readiness` — Validate all specs are complete
8. **Implementation**: `/bmad-bmm-create-story` → `/bmad-bmm-dev-story`
9. **Quick changes**: `/bmad-bmm-quick-spec` → `/bmad-bmm-quick-dev` for small features

Use `/bmad-help` to get guidance on what to do next based on current project state.

## Platform Rules

### Android Back Button
Every dismissible overlay, modal, bottom sheet, or card **must** handle the Android back button via `PlatformBackHandler`. This is an OS-level expectation — pressing back must dismiss the topmost overlay, not exit the app.

- Use `PlatformBackHandler(enabled = <condition>) { <dismiss action> }` in `commonMain`
- Implementation: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/PlatformBackHandler.kt` (expect/actual)
  - Android: delegates to `androidx.activity.compose.BackHandler`
  - iOS: no-op (iOS uses swipe-back gesture from navigation controller)
- When multiple overlays can be open, use priority ordering — only the topmost enabled handler fires
- Checklist for any new overlay/modal/card: (1) add scrim with tap-to-dismiss, (2) add X/close button, (3) add `PlatformBackHandler`

### iOS Navigation
iOS back navigation is handled by the system navigation controller (swipe-back gesture). No additional back button handling is needed in Compose code for iOS.

## Configuration

- **User**: Asifchauhan
- **Skill level**: Intermediate
- **Languages**: English (communication and documents)
