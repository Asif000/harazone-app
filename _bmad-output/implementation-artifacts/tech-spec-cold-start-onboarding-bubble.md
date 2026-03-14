---
title: 'Cold Start Onboarding Bubble'
slug: 'cold-start-onboarding-bubble'
created: '2026-03-14'
status: 'review'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin', 'Compose Multiplatform', 'Koin', 'SQLDelight', 'Compose Resources']
files_to_modify:
  - composeApp/src/commonMain/composeResources/files/onboarding-tips.json
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ColdStartPickerOverlay.kt
code_patterns:
  - 'MapUiState.Ready sealed class — add fields, update all copy() callsites'
  - 'PlatformBackHandler for dismissible overlay'
  - 'Compose Resources Res.readBytes("files/...") for bundled JSON'
  - 'viewModelScope.launch + delay() for timed state transitions'
test_patterns:
  - 'MapViewModel unit tests — assert showOnboardingBubble state transitions'
  - 'UserPreferencesRepository — getColdStartSeen/setColdStartSeen'
---

# Tech-Spec: Cold Start Onboarding Bubble

**Created:** 2026-03-14

## Overview

### Problem Statement

The existing `ColdStartPickerOverlay` ("What excites you?") collides with the 3-pin progressive discovery flow on first launch — both try to appear simultaneously, causing a broken UX. Additionally, the picker blocks discovery until the user makes a selection, creating an unnecessary gate on the core app experience.

### Solution

Remove the picker entirely. On first launch, Gemini auto-drops 3 diverse showcase pins (food + culture + activity) via an augmented prompt. After the pins land (~2 seconds), display an `OnboardingBubble` overlay — a bottom-anchored card with scrim, 4 tips read from `onboarding-tips.json`, pulsing callout dots on the vibes rail and AI bar, and a "Let's go!" dismiss button. The bubble shows only once, gated by the existing `cold_start_seen` preference key.

### Scope

**In Scope:**
- Delete `ColdStartPickerOverlay.kt` and all callsites
- New `OnboardingBubble.kt` composable (scrim + card + callout dots + PlatformBackHandler)
- `GeminiPromptBuilder.buildPinOnlyPrompt()` gains `isNewUser: Boolean` → injects diversity instruction when true
- `MapUiState.Ready`: `showColdStartPicker` replaced by `showOnboardingBubble`
- `MapViewModel`: picker logic replaced by 2-second delayed bubble logic
- `VibeRail.kt`: gains `showCalloutDot: Boolean` param for pulsing dot on vibes + saved orb
- Bundle `docs/onboarding-tips.json` as a Compose Resource at `composeResources/files/onboarding-tips.json`
- `MapScreen.kt`: wire up bubble and callout dot

**Out of Scope:**
- No picker state or pinned-vibes-from-picker logic (removed entirely; pinned vibes still work via long-press)
- No changes to the chat overlay
- No Gemini diversity logic changes beyond the prompt hint
- No tip rotation or A/B testing — show all 4 tips every time

---

## Context for Development

### Codebase Patterns

- **State**: `MapUiState` is a sealed class. All state mutations go through `_uiState.value = current.copy(...)`. Every new field needs a default value and must be reset properly.
- **Persistence**: `UserPreferencesRepository` uses SQLDelight's `user_preferencesQueries.set(key, value)` / `get(key)`. The `cold_start_seen` key already exists — do not create a new key.
- **Back handling**: Every dismissible overlay uses `PlatformBackHandler(enabled = <visible>) { <dismiss action> }` from `com.harazone.ui.components.PlatformBackHandler`.
- **Compose Resources**: The project uses `composeResources/` with `drawable/` and `font/` subdirs already in place. Add `files/` subdir for the JSON. Read at runtime with `Res.readBytes("files/onboarding-tips.json")` — must be done in a coroutine (`LaunchedEffect` or `produceState`).
- **ViewModel timing**: `pendingColdStart: Boolean` var in `MapViewModel` tracks whether the bubble should fire. It's set to `true` in `init` if `getColdStartSeen()` returns false. It is consumed (set to `false`) when the bubble is scheduled.
- **Onboarding tips**: `docs/onboarding-tips.json` is the canonical source. Content: avatar `✨`, title "I'm your AI travel guide!", 4 tips (tap pins / save places / tap vibes / chat), footer "The more you explore, the smarter I get", dismiss label "Let's go!".

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ColdStartPickerOverlay.kt` | DELETE this file — being replaced |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt` | Add `showOnboardingBubble`, remove `showColdStartPicker` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Replace picker flow with bubble flow; pass `isNewUser` to prompt builder |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Wire `OnboardingBubble`, remove `ColdStartPickerOverlay` usage |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt` | Add `showCalloutDot: Boolean = false` param |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt` | Add `isNewUser` param to `buildPinOnlyPrompt()` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/UserPreferencesRepository.kt` | Already has `getColdStartSeen()` / `setColdStartSeen()` — no changes needed |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/PlatformBackHandler.kt` | Use this in `OnboardingBubble` |
| `docs/onboarding-tips.json` | Source of truth for bubble content |

### Technical Decisions

1. **No new preference key**: The existing `cold_start_seen` key gates the bubble (same as it gated the picker). Once dismissed, `setColdStartSeen()` is called — exactly as today.
2. **2-second delay implemented in ViewModel**: After the first batch of pins lands (the `pendingColdStart` consumption point), `viewModelScope.launch { delay(2000); _uiState.value = ... showOnboardingBubble = true }`. This keeps timing logic off the UI layer.
3. **JSON loaded as Compose Resource**: Bundle `onboarding-tips.json` under `composeResources/files/`. Read via `Res.readBytes()` in a `LaunchedEffect` inside `OnboardingBubble`. Parse with `kotlinx.serialization.json.Json`. Fall back to hardcoded tips if parsing fails (resilience, not a crash path).
4. **Callout dots in OnboardingBubble, not in child components**: The dots for the AI bar and vibes rail are absolute-positioned inside the `OnboardingBubble`'s fullscreen `Box` (same layer as scrim). VibeRail gets a `showCalloutDot` param only for the dots that are positionally coupled to items inside it (saved orb + first vibe orb), since those sizes are dynamic.
5. **`onColdStartConfirmed` / `onColdStartSkipped` replaced by single `onOnboardingBubbleDismissed`**: No pinned vibes are set from the bubble (the picker is gone). Dismissed = just `setColdStartSeen()` + clear `showOnboardingBubble`.

---

## Implementation Plan

### Tasks

**Task 1 — Bundle onboarding-tips.json as Compose Resource**

- Create directory: `composeApp/src/commonMain/composeResources/files/`
- Copy `docs/onboarding-tips.json` → `composeApp/src/commonMain/composeResources/files/onboarding-tips.json`
- File content is already correct — copy as-is, no edits needed

---

**Task 2 — Delete ColdStartPickerOverlay.kt**

- Delete file: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/ColdStartPickerOverlay.kt`
- Do not delete `PickerCard` composable from anywhere else — it only lives in this file

---

**Task 3 — Update GeminiPromptBuilder: add isNewUser to buildPinOnlyPrompt**

File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`

Change signature:
```kotlin
fun buildPinOnlyPrompt(areaName: String, isNewUser: Boolean = false): String {
```

Add diversity instruction when `isNewUser = true`. Insert after the `Rules:` line:
```
- NEW USER MODE: Return 3 POIs that showcase the DIVERSITY of "$areaName" — one food/drink, one culture/arts, one outdoor/activity. No taste profile yet.
```
Full updated method:
```kotlin
fun buildPinOnlyPrompt(areaName: String, isNewUser: Boolean = false): String {
    val newUserHint = if (isNewUser) {
        "\n- NEW USER MODE: Return 3 POIs that showcase the DIVERSITY of this area — one food/drink, one culture/arts, one outdoor/activity. No taste profile yet."
    } else ""
    return """
Area: "$areaName". Return JSON only, no other text.
Schema: {"vibes":[{"label":"Street Art","icon":"🎨"}],"pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"Street Art"}]}
Rules:
- vibes: 4-6 most distinctive dimensions of THIS area.
- pois: 3 best POIs. Each "v" MUST exactly match a vibe label.
- t: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
- GPS to 4 decimal places.$newUserHint
    """.trimIndent()
}
```

---

**Task 4 — Update MapUiState.Ready**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapUiState.kt`

- Remove: `val showColdStartPicker: Boolean = false`
- Add: `val showOnboardingBubble: Boolean = false`

---

**Task 5 — Update MapViewModel: replace picker flow with bubble flow**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`

**5a. Update `buildPinOnlyPrompt` call** — find the call to `buildPinOnlyPrompt(areaName)` and pass `isNewUser`:
```kotlin
// Before (search for the existing call):
promptBuilder.buildPinOnlyPrompt(areaName)

// After:
promptBuilder.buildPinOnlyPrompt(areaName, isNewUser = pendingColdStart)
```

**5b. Replace picker state trigger with delayed bubble trigger** — find the line:
```kotlin
showColdStartPicker = if (pendingColdStart) { pendingColdStart = false; true } else s.showColdStartPicker,
```
Replace with (no inline expression — use a flag before the copy):
```kotlin
// Before the .copy() block, add:
val shouldShowBubble = pendingColdStart
if (pendingColdStart) {
    pendingColdStart = false
    viewModelScope.launch {
        delay(2000)
        val s2 = _uiState.value as? MapUiState.Ready ?: return@launch
        _uiState.value = s2.copy(showOnboardingBubble = true)
    }
}
// In the .copy() block, remove the showColdStartPicker line entirely
// (showOnboardingBubble defaults to false, set by the launched coroutine above)
```

**5c. Replace `onColdStartConfirmed` and `onColdStartSkipped`** — delete both methods and add:
```kotlin
fun onOnboardingBubbleDismissed() {
    viewModelScope.launch { userPreferencesRepository.setColdStartSeen() }
    val current = _uiState.value as? MapUiState.Ready ?: return
    _uiState.value = current.copy(showOnboardingBubble = false)
}
```

---

**Task 6 — Create OnboardingBubble.kt**

Create file: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/OnboardingBubble.kt`

```kotlin
package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.ui.components.PlatformBackHandler
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import areadiscovery.composeapp.generated.resources.Res

private val Accent = Color(0xFF4ECDC4)
private val DarkCard = Color(0xFF1A1224)

@Serializable
private data class OnboardingTip(val icon: String, val action: String, val description: String)

@Serializable
private data class OnboardingTipsData(
    val avatar_emoji: String,
    val title: String,
    val footer_text: String,
    val dismiss_label: String,
    val tips: List<OnboardingTip>,
)

private val fallbackTips = OnboardingTipsData(
    avatar_emoji = "✨",
    title = "I'm your AI travel guide!",
    footer_text = "The more you explore, the smarter I get",
    dismiss_label = "Let's go!",
    tips = listOf(
        OnboardingTip("👆", "Tap pins", "to discover what makes each place special"),
        OnboardingTip("🔖", "Save places", "you like — I'll learn your taste and recommend better spots"),
        OnboardingTip("🎨", "Tap vibes", "on the right to filter by what excites you"),
        OnboardingTip("💬", "Chat with me", "anytime — ask about hidden gems, safety, food, anything"),
    ),
)

@OptIn(ExperimentalResourceApi::class)
@Composable
fun OnboardingBubble(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    var tipsData by remember { mutableStateOf(fallbackTips) }

    LaunchedEffect(Unit) {
        try {
            val bytes = Res.readBytes("files/onboarding-tips.json")
            val parsed = Json { ignoreUnknownKeys = true }
                .decodeFromString(OnboardingTipsData.serializer(), bytes.decodeToString())
            tipsData = parsed
        } catch (_: Exception) {
            // fallback already set
        }
    }

    PlatformBackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        ) {
            // Callout dot — vibes rail (right side, mid-screen)
            CalloutDot(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp, bottom = 80.dp))
            // Callout dot — saved orb (right side, above mid)
            CalloutDot(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, bottom = 200.dp))
            // Callout dot — AI bar (bottom left)
            CalloutDot(modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, bottom = 32.dp))

            // Bubble card — anchored above bottom bar
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 80.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .clickable(enabled = false) {} // absorb clicks so scrim doesn't fire
                        .padding(20.dp),
                ) {
                    // Avatar
                    Text(tipsData.avatar_emoji, fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))

                    // Title
                    Text(
                        tipsData.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Tips
                    tipsData.tips.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            Text(tip.icon, fontSize = 18.sp, modifier = Modifier.padding(top = 1.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                buildString {
                                    append(tip.action)
                                    append(" ")
                                    append(tip.description)
                                },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Footer row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            tipsData.footer_text,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                tipsData.dismiss_label,
                                color = Color(0xFF111111),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalloutDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "callout")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "calloutScale",
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size((10 * scale).dp)
                .clip(CircleShape)
                .background(Accent),
        )
    }
}
```

**Implementation notes for Task 6:**
- The `clickable(enabled = false) {}` on the card absorbs clicks so tapping inside the bubble doesn't dismiss (only scrim taps and "Let's go!" dismiss)
- The `CalloutDot` positions are approximate — use padding values matching the actual screen layout. Adjust `bottom` offsets if the VibeRail or AI bar positions differ at runtime (the positions in the prototype were: vibes mid-right ~200dp from bottom, saved orb ~350dp from bottom, AI bar ~28dp from bottom at left)
- Bold tip action text: Since rich text (SpannableString) complicates commonMain, render tip text as plain string. The `action` word is still clearly the leading word. If bold is required, use `buildAnnotatedString` with `SpanStyle(fontWeight = FontWeight.Bold)` applied to the `action` substring length

---

**Task 7 — Update VibeRail: add showCalloutDot param**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/VibeRail.kt`

Add parameter to `VibeRail()`:
```kotlin
fun VibeRail(
    // existing params...
    showCalloutDot: Boolean = false,  // ADD THIS
```

In the saved orb composable section (the `saved-orb-wrap` equivalent), wrap it with a `Box` and overlay a `CalloutDot` when `showCalloutDot = true`:
```kotlin
// Around the saved orb rendering:
Box {
    // existing saved orb content
    if (showCalloutDot) {
        CalloutDot(modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
    }
}
```
Apply the same pattern to the first vibe orb (index 0 in the vibes list).

**Note**: Import `CalloutDot` from `OnboardingBubble.kt` — make it `internal` (not `private`) so VibeRail can use it. OR duplicate the ~10-line composable inline. Prefer extracting to a shared `ui/components/CalloutDot.kt` to avoid cross-module access issues.

> **Decision**: Extract `CalloutDot` to `composeApp/src/commonMain/kotlin/com/harazone/ui/components/CalloutDot.kt` as a standalone `internal` composable. Both `OnboardingBubble` and `VibeRail` import from there.

---

**Task 7b — Create CalloutDot.kt (shared component)**

Create: `composeApp/src/commonMain/kotlin/com/harazone/ui/components/CalloutDot.kt`

```kotlin
package com.harazone.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Accent = Color(0xFF4ECDC4)

@Composable
fun CalloutDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "callout")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "calloutScale",
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size((10 * scale).dp)
                .clip(CircleShape)
                .background(Accent),
        )
    }
}
```

Update `OnboardingBubble.kt` to remove its local `CalloutDot` and import from `com.harazone.ui.components.CalloutDot`.

---

**Task 8 — Update MapScreen.kt**

File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

**8a. Remove import:**
```kotlin
import com.harazone.ui.map.components.ColdStartPickerOverlay
```

**8b. Add import:**
```kotlin
import com.harazone.ui.map.components.OnboardingBubble
```

**8c. Replace the cold start picker block:**

Find:
```kotlin
// Cold start picker overlay
if (state.showColdStartPicker) {
    ColdStartPickerOverlay(
        onConfirm = { viewModel.onColdStartConfirmed(it) },
        onSkip = { viewModel.onColdStartSkipped() },
    )
}
```

Replace with:
```kotlin
// Onboarding bubble — first launch only
OnboardingBubble(
    visible = state.showOnboardingBubble,
    onDismiss = { viewModel.onOnboardingBubbleDismissed() },
)
```

**8d. Pass `showCalloutDot` to VibeRail:**

Find the `VibeRail(...)` call in `ReadyContent` and add the new param:
```kotlin
VibeRail(
    // existing params...
    showCalloutDot = state.showOnboardingBubble,
)
```

---

### Acceptance Criteria

**AC1 — No picker on first launch**
- **Given** a fresh install (no `cold_start_seen` preference set)
- **When** the map screen loads and the first 3 pins land
- **Then** no `ColdStartPickerOverlay` appears; `OnboardingBubble` appears ~2 seconds after pins land

**AC2 — Diversity showcase pins on first launch**
- **Given** `isNewUser = true` (first launch, `pendingColdStart` was true)
- **When** `buildPinOnlyPrompt` is called
- **Then** the prompt includes the NEW USER MODE diversity instruction (food/drink + culture/arts + outdoor/activity)

**AC3 — Bubble dismiss via "Let's go!" button**
- **Given** the onboarding bubble is visible
- **When** the user taps "Let's go!"
- **Then** the bubble and scrim animate out, `cold_start_seen = "true"` is persisted, `showOnboardingBubble = false` in state

**AC4 — Bubble dismiss via scrim tap**
- **Given** the onboarding bubble is visible
- **When** the user taps anywhere on the scrim (outside the bubble card)
- **Then** same result as AC3

**AC5 — Bubble dismiss via Android back button**
- **Given** the onboarding bubble is visible on Android
- **When** the user presses the hardware back button
- **Then** same result as AC3 (bubble dismissed, `cold_start_seen` persisted, app does NOT exit)

**AC6 — Bubble never shown again after dismiss**
- **Given** the bubble was shown and dismissed (`cold_start_seen = "true"`)
- **When** the app is restarted and the map loads
- **Then** `pendingColdStart` is false, `showOnboardingBubble` remains false, no bubble appears

**AC7 — Callout dots appear with bubble**
- **Given** the onboarding bubble is visible
- **When** the bubble card and scrim render
- **Then** three pulsing callout dots appear: one near the vibes rail, one near the saved orb, one near the AI chat bar

**AC8 — Tips content from onboarding-tips.json**
- **Given** `composeResources/files/onboarding-tips.json` is bundled correctly
- **When** `OnboardingBubble` loads
- **Then** all 4 tips render with their icons and text as defined in the JSON

**AC9 — Fallback tips if JSON fails to parse**
- **Given** `Res.readBytes("files/onboarding-tips.json")` throws or returns malformed data
- **When** `OnboardingBubble` renders
- **Then** the hardcoded `fallbackTips` are displayed — no crash, no blank bubble

**AC10 — Returning user: no bubble, no picker**
- **Given** `cold_start_seen = "true"` in preferences (returning user)
- **When** the map loads
- **Then** neither `ColdStartPickerOverlay` nor `OnboardingBubble` appears; `isNewUser = false` is passed to the prompt builder

**AC11 — iOS: no back handler regression**
- **Given** the bubble is visible on iOS
- **When** the app is used normally (no hardware back button)
- **Then** `PlatformBackHandler` no-op behaves correctly; bubble dismisses only via scrim tap or "Let's go!" button

---

## Additional Context

### Dependencies

- **`kotlinx-serialization-json`**: Already in project (used in `UserPreferencesRepository`). The `OnboardingTipsData` and `OnboardingTip` classes use `@Serializable` — ensure the serialization plugin processes them (they are in `commonMain`, same module as existing serializable classes).
- **Compose Resources API**: Already used for fonts/drawables. The `files/` subdir is a standard Compose Resources location for arbitrary bundled files.
- **No new Koin bindings needed**: `OnboardingBubble` is a pure composable; `VibeRail` is a pure composable. No DI required.

### Testing Strategy

**Unit test: `MapViewModelTest` (or equivalent in `composeApp/src/commonMain/test/`)**

```
Test: onFirstLaunch_showsOnboardingBubbleAfterDelay
- Given: UserPreferencesRepository.getColdStartSeen() returns false
- When: ViewModel init completes and first pin batch loads
- Then: After 2s+ (advance coroutine time), MapUiState.Ready.showOnboardingBubble == true

Test: onOnboardingBubbleDismissed_clearsStateAndPersists
- Given: showOnboardingBubble == true
- When: viewModel.onOnboardingBubbleDismissed() called
- Then: showOnboardingBubble == false, setColdStartSeen() was called

Test: onReturnVisit_noBubble
- Given: getColdStartSeen() returns true
- When: ViewModel init and first batch load
- Then: showOnboardingBubble never becomes true

Test: buildPinOnlyPrompt_isNewUser_includesDiversityHint
- Given: isNewUser = true
- When: buildPinOnlyPrompt("Shoreditch", isNewUser = true)
- Then: returned string contains "NEW USER MODE"

Test: buildPinOnlyPrompt_returningUser_noDiversityHint
- Given: isNewUser = false
- When: buildPinOnlyPrompt("Shoreditch", isNewUser = false)
- Then: returned string does NOT contain "NEW USER MODE"
```

**Manual verification checklist:**
- [ ] Fresh install: pins land → 2s pause → bubble appears with scrim + 3 callout dots
- [ ] All 4 tips display with correct icon + text from JSON
- [ ] "Let's go!" dismisses cleanly (animate out)
- [ ] Scrim tap dismisses
- [ ] Android back button dismisses (not exits app)
- [ ] Restart after dismiss: no bubble
- [ ] Returning user: no bubble on any subsequent launch

### Notes

- **`showColdStartPicker` field removal**: Search the full codebase for `showColdStartPicker` to catch any remaining references (e.g., tests that assert on it). Update all references to `showOnboardingBubble`.
- **`onColdStartConfirmed` / `onColdStartSkipped` removal**: These methods are removed from `MapViewModel`. The `setPinnedVibes` with user-selected labels is no longer called at cold start (pinned vibes still work via long-press on vibe orbs — that pathway is unaffected).
- **`pendingColdStart` and `isNewUser` timing**: `buildPinOnlyPrompt` is called BEFORE `pendingColdStart` is consumed. The prompt call happens during area search, and the `pendingColdStart = false` flip happens when the response arrives (Task 5b). This order is already correct — the diversity hint applies to exactly the first call, and subsequent calls get `isNewUser = false`.
- **Bottom padding for bubble**: The bubble is positioned `bottom = 80.dp` above the bottom of the screen. Verify this clears the bottom bar (FAB + toggle) — adjust if needed based on actual bottom bar height.
- **Prototype reference**: `_bmad-output/brainstorming/prototype-cold-start-v2.html` — open in browser for full animated reference implementation.
