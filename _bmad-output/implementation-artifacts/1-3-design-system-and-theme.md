# Story 1.3: Design System & Theme

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user**,
I want the app to have a warm, inviting visual identity with excellent readability,
So that reading area content feels like pleasure, not work.

## Acceptance Criteria

1. **Given** a custom `MaterialTheme` defined in `ui/theme/`, **When** the app launches, **Then** the light theme uses: primary orange `#E8722A`, surface beige `#F5EDE3`, text dark charcoal `#2D2926`, background white
2. A dark theme color scheme is defined and toggles with system setting (dark mode colors: surface `#2D2520`, primary `#E89A5E`, on-surface `#EDE0D4`, background `#1A1412`)
3. Typography uses Inter font family with the Material 3 type scale (Display -> Label)
4. All body text is minimum 16sp, labels minimum 12sp
5. All text meets WCAG 2.1 AA contrast ratios: 4.5:1 for body text, 3:1 for large text/UI components
6. Shape system uses rounded corners consistent with card-based content (16dp cards, 8dp chips)
7. Spacing tokens use 8dp base unit defined in a `Spacing` object
8. All interactive elements use minimum 48dp touch targets
9. Colors are accessed exclusively via `MaterialTheme.colorScheme` — zero hardcoded values
10. App.kt updated to use the new custom `AreaDiscoveryTheme` instead of bare `MaterialTheme`

## Tasks / Subtasks

- [x] Task 1: Create Color.kt — Light and dark color schemes (AC: #1, #2)
  - [x] 1.1 Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Color.kt`
  - [x] 1.2 Define light color scheme using `lightColorScheme()` with exact hex values from AC #1
  - [x] 1.3 Define dark color scheme using `darkColorScheme()` with exact hex values from AC #2
  - [x] 1.4 Define confidence tier accent colors (green `#4A8C5C`, amber `#C49A3C`, red `#B85C4A`) as standalone color constants
  - [x] 1.5 Define on-surface variant warm gray `#6B5E54` for secondary text

- [x] Task 2: Bundle Inter font and create Typography.kt (AC: #3, #4)
  - [x] 2.1 Download Inter font files (Regular, Medium, SemiBold, Bold) in `.ttf` format from Google Fonts
  - [x] 2.2 Place font files in `composeApp/src/commonMain/composeResources/font/` directory (NOT `fonts/`)
  - [x] 2.3 Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Type.kt`
  - [x] 2.4 Create `InterFontFamily` using `FontFamily()` with `Font(Res.font.inter_regular, FontWeight.Normal)`, etc. via Compose Resources API
  - [x] 2.5 Define M3 type scale: displayMedium (28sp Bold), headlineSmall (20sp SemiBold), titleMedium (16sp SemiBold), bodyLarge (16sp Regular, 1.5x line height), bodyMedium (14sp Regular), labelMedium (12sp Medium)
  - [x] 2.6 Verify minimum sizes: body >= 16sp, labels >= 12sp

- [x] Task 3: Create Shape.kt (AC: #6)
  - [x] 3.1 Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Shape.kt`
  - [x] 3.2 Define shapes: small = 8dp (chips, buttons), medium = 16dp (cards, bottom sheet top corners), large = 24dp (if needed)

- [x] Task 4: Create Spacing.kt — Custom spacing token object (AC: #7, #8)
  - [x] 4.1 Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Spacing.kt`
  - [x] 4.2 Define `object Spacing` with tokens: xs=4dp, sm=8dp, bucketInternal=12dp, md=16dp, lg=24dp, xl=32dp, touchTarget=48dp
  - [x] 4.3 Create `LocalSpacing` CompositionLocal to make spacing available via `MaterialTheme` extension

- [x] Task 5: Create Theme.kt — Main theme composable (AC: #1, #2, #9, #10)
  - [x] 5.1 Create `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Theme.kt`
  - [x] 5.2 Create `@Composable fun AreaDiscoveryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` that composes `MaterialTheme` with custom colors, typography, shapes
  - [x] 5.3 Provide `LocalSpacing` via `CompositionLocalProvider`
  - [x] 5.4 Create extension property `val MaterialTheme.spacing: Spacing` for clean access syntax

- [x] Task 6: Update App.kt to use AreaDiscoveryTheme (AC: #10)
  - [x] 6.1 Replace `MaterialTheme { ... }` with `AreaDiscoveryTheme { ... }` in App.kt
  - [x] 6.2 Verify the app still builds and renders with the new theme

- [x] Task 7: Add Compose @Preview functions for theme verification
  - [x] 7.1 Add preview composable showing light theme color swatches
  - [x] 7.2 Add preview composable showing dark theme color swatches
  - [x] 7.3 Add preview composable showing typography scale samples
  - [x] 7.4 Add preview composable showing shape samples

- [x] Task 8: Write unit tests for theme constants (AC: #1, #2, #4, #5, #7)
  - [x] 8.1 Create `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/ColorTest.kt` — verify hex values match spec
  - [x] 8.2 Create `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/TypographyTest.kt` — verify minimum font sizes (body >= 16sp, label >= 12sp)
  - [x] 8.3 Create `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/SpacingTest.kt` — verify spacing token values and 8dp base unit
  - [x] 8.4 Verify WCAG contrast ratios programmatically: charcoal on beige >= 4.5:1, orange on white >= 3:1

- [x] Task 9: Build verification
  - [x] 9.1 Run `./gradlew :composeApp:assembleDebug` — must pass
  - [x] 9.2 Run `./gradlew :composeApp:allTests` — must pass
  - [x] 9.3 Run `./gradlew :composeApp:lint` — must pass

## Dev Notes

### Critical: Font Loading in Compose Multiplatform (KMP)

The architecture doc mentions `androidx.compose.ui.text.googlefonts` for Inter font loading. **This is Android-only and will NOT work in commonMain.** In Compose Multiplatform, fonts must be:

1. **Bundled as resources** in `composeApp/src/commonMain/composeResources/font/` (note: `font` singular, NOT `fonts`)
2. **Loaded via Compose Resources API** using `Font(Res.font.inter_regular, FontWeight.Normal)` etc.
3. The Compose Multiplatform plugin auto-generates the `Res` object for type-safe resource access
4. Font files should be `.ttf` format, named with lowercase and underscores: `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`

**Download Inter font**: Get the TTF files from [Google Fonts - Inter](https://fonts.google.com/specimen/Inter). Select Regular (400), Medium (500), SemiBold (600), Bold (700) weights.

### Exact Color Values

**Light Mode:**

| Role | Hex | M3 Token |
|------|-----|----------|
| Primary | `#E8722A` | `primary` |
| Primary Variant (pressed) | `#C45A1C` | `primaryContainer` or custom |
| Surface (cards/content) | `#F5EDE3` | `surface` |
| Background (screen base) | `#FFFFFF` | `background` |
| On-Primary | `#FFFFFF` | `onPrimary` |
| On-Surface (text) | `#2D2926` | `onSurface` |
| On-Surface Variant | `#6B5E54` | `onSurfaceVariant` |
| Error | `#BA1A1A` | `error` |

**Dark Mode:**

| Role | Hex | M3 Token |
|------|-----|----------|
| Background | `#1A1412` | `background` |
| Surface | `#2D2520` | `surface` |
| Primary | `#E89A5E` | `primary` |
| On-Surface | `#EDE0D4` | `onSurface` |
| On-Surface Variant | `#A89888` | `onSurfaceVariant` |

### Type Scale Definition

```kotlin
val AreaDiscoveryTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 33.6.sp,  // 1.2x
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp  // 1.3x
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.8.sp  // 1.3x
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp  // 1.5x
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp  // 1.5x
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.8.sp,  // 1.4x
        letterSpacing = 0.5.sp
    )
)
```

### Spacing Object Pattern

```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val bucketInternal = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val touchTarget = 48.dp
}

val LocalSpacing = staticCompositionLocalOf { Spacing }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
```

Usage: `MaterialTheme.spacing.md` for 16dp, `MaterialTheme.spacing.lg` for 24dp, etc.

### Shape System

```kotlin
val AreaDiscoveryShapes = Shapes(
    small = RoundedCornerShape(8.dp),   // Chips, buttons
    medium = RoundedCornerShape(16.dp), // Cards, content containers
    large = RoundedCornerShape(24.dp)   // Large surfaces
)
```

### Theme Composable Pattern

```kotlin
@Composable
fun AreaDiscoveryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalSpacing provides Spacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AreaDiscoveryTypography,
            shapes = AreaDiscoveryShapes,
            content = content
        )
    }
}
```

### WCAG Contrast Verification

The following contrast ratios must be verified:
- Dark charcoal `#2D2926` on beige `#F5EDE3`: **~8.2:1** (exceeds AA 4.5:1)
- Orange `#E8722A` on white `#FFFFFF`: **~3.5:1** (passes AA for large text/UI 3:1)
- Orange `#E8722A` on beige `#F5EDE3`: Should be verified (use for interactive elements on cards)
- Dark mode: warm off-white `#EDE0D4` on dark brown `#2D2520`: Must be >= 4.5:1

### Compose Resources Dependency

The `compose.components.resources` library should already be in `build.gradle.kts` from Story 1.1. Verify it exists in `commonMain` dependencies:
```kotlin
implementation(compose.components.resources)
```

If not present, add it. This is required for the `Res.font.*` API.

### Confidence Tier Colors (Not part of M3 color scheme)

These are standalone color constants used by `ConfidenceTierBadge` (Story 1.5+):
```kotlin
// Define in Color.kt as top-level constants
val ConfidenceHigh = Color(0xFF4A8C5C)   // Muted Green — Verified
val ConfidenceMedium = Color(0xFFC49A3C) // Muted Amber — Approximate
val ConfidenceLow = Color(0xFFB85C4A)    // Muted Red — Limited Data
```

### Bucket Icon Colors

All six bucket headers use orange (`MaterialTheme.colorScheme.primary`) for their Material Symbol icons. No per-bucket color variations — unified orange accent.

| Bucket | Material Symbol |
|--------|----------------|
| Safety | `Shield` |
| Character | `Palette` |
| What's Happening | `CalendarMonth` |
| Cost | `Payments` |
| History | `History` |
| Nearby | `Explore` |

### Previous Story (1.1 & 1.2) Learnings

**From Story 1.1:**
- `androidTarget()` deprecated-as-error in Kotlin 2.3.0 — uses `@Suppress("DEPRECATION")`. No impact on theme code.
- Koin BOM `platform()` not supported in KMP — explicit versions. No impact on theme code.
- `compileSdk` and `targetSdk` are 36 (not 35). Build commands: `./gradlew :composeApp:assembleDebug`, `./gradlew :composeApp:allTests`.
- Actual versions in use: Kotlin 2.3.0, Compose MP 1.10.0, AGP 8.11.2, Gradle 8.14.3
- Material 3 version: `1.10.0-alpha05` (from libs.versions.toml)
- The `Unable to strip libraries` warning is benign.
- App.kt is currently: `MaterialTheme { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("AreaDiscovery") } } }`

**From Story 1.2:**
- CI runs: assembleDebug, allTests, lint — all three must pass after changes
- No `continue-on-error` — all steps required

### Enforcement Rules (Critical for All Future Stories)

1. **Zero hardcoded colors** — Always `MaterialTheme.colorScheme.*`
2. **Zero hardcoded font sizes** — Always `MaterialTheme.typography.*`
3. **Zero hardcoded spacing** — Always `MaterialTheme.spacing.*`
4. **48dp minimum touch targets** on all interactive elements
5. **Color + icon + text** for confidence tiers (never color alone)
6. **`contentDescription`** on all interactive composables
7. **Respect `prefers-reduced-motion`** system preference
8. **Dark charcoal on beige** for primary text — never stark black-on-white

### What NOT to Do

- Do NOT use `androidx.compose.ui.text.googlefonts` — it's Android-only, won't compile in commonMain
- Do NOT create custom components beyond the theme system (that's Story 1.5)
- Do NOT add streaming animation code (that's Story 1.5)
- Do NOT add navigation (that's Story 1.6)
- Do NOT hardcode any colors in composables — always use theme tokens
- Do NOT create dynamic color / Material You dynamic theming — use the fixed brand palette
- Do NOT add Inter via Gradle dependency — bundle .ttf files as compose resources
- Do NOT place fonts in `composeResources/fonts/` (plural) — must be `composeResources/font/` (singular)
- Do NOT add custom M3 components beyond Theme, Color, Typography, Shape, Spacing files

### Project Structure Notes

- All theme files go in: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/`
- Font resources go in: `composeApp/src/commonMain/composeResources/font/`
- Tests go in: `composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/`
- Remove `.gitkeep` from `ui/theme/` once actual files are added
- Single `composeApp` module — no additional Gradle modules

### References

- [Source: _bmad-output/planning-artifacts/architecture.md#Design System] — M3 theme architecture, color palette, typography, shapes
- [Source: _bmad-output/planning-artifacts/architecture.md#Project Structure] — File locations and package conventions
- [Source: _bmad-output/planning-artifacts/architecture.md#Naming Patterns] — Composable naming conventions
- [Source: _bmad-output/planning-artifacts/architecture.md#Accessibility] — WCAG contrast, touch targets, reduced motion
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Design System Foundation] — M3 approach, component strategy
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Color System] — Complete color palette with hex values
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Typography System] — Inter font, type scale, principles
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Spacing & Layout System] — 8dp base unit, spacing tokens
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Accessibility Requirements] — WCAG 2.1 AA compliance checklist
- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.3] — Acceptance criteria
- [Source: _bmad-output/planning-artifacts/epics.md#Epic 1] — Cross-story context
- [Source: _bmad-output/implementation-artifacts/1-1-kmp-project-initialization-and-package-structure.md] — Project setup, version deviations, build commands
- [Source: _bmad-output/implementation-artifacts/1-2-ci-cd-pipeline.md] — CI requirements (assembleDebug + allTests + lint must pass)
- [Web: Compose Multiplatform Resources](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources.html) — Font loading via composeResources/font/

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- Initial `assert()` calls in ColorTest.kt required `@ExperimentalNativeApi` opt-in on Kotlin/Native — replaced with `assertTrue()` from kotlin.test
- Inter font downloaded from GitHub rsms/inter v4.1 releases (static TTF from extras/ttf/)
- Typography and InterFontFamily made `@Composable` functions (not top-level vals) since Compose Resources `Font()` requires composable context in KMP

### Completion Notes List

- Created complete M3 design system: Color.kt, Type.kt, Shape.kt, Spacing.kt, Theme.kt
- Bundled Inter font (4 weights: Regular, Medium, SemiBold, Bold) as compose resources
- Light theme: orange primary #E8722A, beige surface #F5EDE3, charcoal text #2D2926, white background
- Dark theme: surface #2D2520, primary #E89A5E, text #EDE0D4, background #1A1412
- Confidence tier colors defined as standalone constants (not part of M3 scheme)
- Spacing object with 8dp base unit and CompositionLocal for MaterialTheme.spacing access
- Shape system: 8dp (small), 16dp (medium), 24dp (large) rounded corners
- App.kt updated from bare MaterialTheme to AreaDiscoveryTheme
- 4 Android @Preview composables for visual verification of colors, typography, and shapes
- Unit tests verify: all hex color values, WCAG contrast ratios (AA compliance), font size minimums, spacing token values
- All 3 CI gates pass: assembleDebug, allTests, lint

### Change Log

- 2026-03-04: Implemented design system and theme (Story 1.3) — Color, Typography, Shape, Spacing, Theme files created; Inter font bundled; App.kt updated; unit tests and previews added
- 2026-03-04: Code review fixes applied — (H1) Rewrote TypographyTest.kt to reference Type.kt constants instead of hardcoded literals; (H2) Raised bodyMedium from 14sp to 16sp to comply with AC #4; (M1) Added missing WCAG orange-on-beige contrast test to ColorTest.kt; (M2) Added onPrimary to DarkColorScheme; (M4) Fixed AC #1 text (background/surface terminology corrected). Note: code has not been committed to git (M3 — requires developer action).

### File List

- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Color.kt (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Type.kt (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Shape.kt (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Spacing.kt (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Theme.kt (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt (modified)
- composeApp/src/commonMain/composeResources/font/inter_regular.ttf (new)
- composeApp/src/commonMain/composeResources/font/inter_medium.ttf (new)
- composeApp/src/commonMain/composeResources/font/inter_semibold.ttf (new)
- composeApp/src/commonMain/composeResources/font/inter_bold.ttf (new)
- composeApp/src/androidMain/kotlin/com/areadiscovery/ui/theme/ThemePreview.kt (new)
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/ColorTest.kt (new)
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/TypographyTest.kt (new)
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/SpacingTest.kt (new)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/.gitkeep (deleted)
