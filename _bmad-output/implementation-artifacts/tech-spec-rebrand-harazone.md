---
title: 'Rebrand: Rename to HaraZone + Custom App Icon'
slug: 'rebrand-harazone'
created: '2026-03-06'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain + androidMain + iosMain)'
  - 'Android Adaptive Icons (mipmap-anydpi-v26 + drawable layers)'
  - 'Koin 4.x (no DI changes — package rename only)'
files_to_modify:
  - 'composeApp/build.gradle.kts'
  - 'composeApp/src/androidMain/res/values/strings.xml'
  - 'composeApp/src/androidMain/res/drawable/ic_launcher_background.xml'
  - 'composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml'
  - 'composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml'
  - 'composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher_round.xml'
  - 'iosApp/iosApp/Info.plist'
  - 'All Kotlin source files — package declaration rename (IDE refactor)'
files_to_replace:
  - 'composeApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png'
  - 'composeApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png'
  - 'composeApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png'
  - 'composeApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png'
  - 'composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png'
  - 'composeApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png'
  - 'composeApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png'
  - 'composeApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png'
  - 'composeApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png'
  - 'composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png'
  - 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/ (all sizes)'
---

# Tech-Spec: Rebrand — Rename to HaraZone + Custom App Icon

**Created:** 2026-03-06

## Overview

### What

1. Rename the app to **HaraZone** — app label, package ID, and iOS bundle display name.
2. Replace the default Android placeholder icon with a custom HaraZone adaptive icon.

### Why now

The app is pre-launch. Changing the application ID (`com.areadiscovery` → `com.harazone`) after Play Store publication is impossible without a new listing. This is the lowest-cost moment to rename.

"Hara" (حارة) = neighbourhood/quarter in Arabic — culturally resonant, especially given the multilingual work in progress.

---

## Tasks

### Task 1: Package rename — `build.gradle.kts`

Three locations to update in `composeApp/build.gradle.kts`:

```kotlin
// SQLDelight
packageName = "com.harazone"                          // was com.areadiscovery
packageName.set("com.harazone.data.local")            // was com.areadiscovery.data.local

// Android
namespace = "com.harazone"                            // was com.areadiscovery
applicationId = "com.harazone"                        // was com.areadiscovery
// applicationIdSuffix = ".debug" — no change needed
```

---

### Task 2: Rename all Kotlin package declarations

Use Android Studio **Refactor → Rename** on the `com.areadiscovery` package in `commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidInstrumentedTest`:

- Right-click `com/areadiscovery` folder → Refactor → Rename → `harazone`
- Choose "Rename package" (not just the directory)
- This updates all `package com.areadiscovery.*` and `import com.areadiscovery.*` declarations automatically

**Verify after refactor:**
```bash
grep -r "areadiscovery" composeApp/src --include="*.kt" | grep -v ".gradle"
# Should return zero results
```

---

### Task 3: SQLDelight package rename

SQLDelight generates code into the package set in `build.gradle.kts`. After Task 1 + 2, the generated DB classes will be at `com.harazone.data.local.*`. No `.sq` file changes needed — SQLDelight uses the package name from the Gradle config only.

Clean and rebuild after rename:
```bash
./gradlew clean :composeApp:generateCommonMainDatabaseInterface
```

---

### Task 4: App label — `strings.xml`

`composeApp/src/androidMain/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">HaraZone</string>
</resources>
```

The debug variant label is controlled by `applicationIdSuffix` + `resValue` in `build.gradle.kts`. Confirm the debug label reads "HaraZone" (not "HaraZone DEBUG") — add a `resValue` in the debug buildType if needed:
```kotlin
debug {
    applicationIdSuffix = ".debug"
    resValue("string", "app_name", "HaraZone DEBUG")
}
```

---

### Task 5: iOS bundle display name — `Info.plist`

Add `CFBundleDisplayName` to `iosApp/iosApp/Info.plist`:
```xml
<key>CFBundleDisplayName</key>
<string>HaraZone</string>
```

---

### Task 6: Custom icon — Android

**Prerequisites (designer step — do before this task):**
Produce the HaraZone icon as:
- A foreground vector SVG (safe zone: 66dp centered in 108dp canvas — content must stay within inner 72dp)
- A background color or simple vector

**Implementation:**

1. **`drawable/ic_launcher_background.xml`** — update to HaraZone brand background color:
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#YOUR_BRAND_COLOR"/>
</shape>
```

2. **`drawable-v24/ic_launcher_foreground.xml`** — replace with HaraZone vector foreground (exported from design tool)

3. **`mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`** — no changes needed (already reference foreground + background layers)

4. **PNG fallbacks** (for API < 26) — use Android Studio **Image Asset Studio**:
   - Right-click `res` → New → Image Asset
   - Icon type: Launcher Icons (Adaptive and Legacy)
   - Source: your foreground SVG
   - Generates all `mipmap-*/ic_launcher.png` + `ic_launcher_round.png` sizes automatically

**Required PNG sizes:**

| Density | Size | Folder |
|---------|------|--------|
| mdpi | 48×48px | mipmap-mdpi |
| hdpi | 72×72px | mipmap-hdpi |
| xhdpi | 96×96px | mipmap-xhdpi |
| xxhdpi | 144×144px | mipmap-xxhdpi |
| xxxhdpi | 192×192px | mipmap-xxxhdpi |

---

### Task 7: Custom icon — iOS

In `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`:

Replace all icon PNGs with HaraZone versions. Required sizes:

| Size | Scale | File |
|------|-------|------|
| 20×20 | @2x, @3x | iPhone notification |
| 29×29 | @2x, @3x | iPhone settings |
| 40×40 | @2x, @3x | iPhone spotlight |
| 60×60 | @2x, @3x | iPhone app icon |
| 1024×1024 | @1x | App Store |

Use **[appicon.co](https://appicon.co)** or Xcode's image asset tool: drag the 1024×1024 master → it generates all sizes.

---

### Task 8: Update dev tooling references

Update `memory/MEMORY.md` device commands (post-rename the package changes):
```
# Old
adb shell am start -n com.areadiscovery.debug/com.areadiscovery.MainActivity
adb uninstall com.areadiscovery.debug

# New
adb shell am start -n com.harazone.debug/com.harazone.MainActivity
adb uninstall com.harazone.debug
```

Also update `AppLaunchSmokeTest.kt` if it references the package name explicitly.

---

### Task 9: Uninstall old package before testing

The old `com.areadiscovery.debug` and `com.harazone.debug` are different packages — both can coexist. Before testing the rename, uninstall the old build:
```bash
adb uninstall com.areadiscovery.debug
```
Then install fresh:
```bash
./gradlew :composeApp:installDebug
```

---

## Acceptance Criteria

1. App installs as `com.harazone.debug` (debug) and `com.harazone` (release)
2. App label shows "HaraZone DEBUG" on debug build, "HaraZone" on release
3. iOS app displays "HaraZone" as home screen label
4. Custom icon appears on Android launcher (adaptive, round, and legacy PNG)
5. Custom icon appears on iOS home screen
6. `./gradlew :composeApp:assembleDebug` builds cleanly with zero `areadiscovery` references in source
7. All existing tests pass — `./gradlew :composeApp:test`
8. `grep -r "areadiscovery" composeApp/src --include="*.kt"` returns zero results

---

## Notes

- **Firebase**: If `google-services.json` / `GoogleService-Info.plist` are wired, the bundle ID change requires updating the Firebase project to add `com.harazone` as a registered app. Low priority until Firebase is actively used.
- **Play Store**: Pre-launch — no listing to update. Clean slate with `com.harazone`.
- **Icon design**: Task 6 + 7 are blocked on the icon asset being ready. Tasks 1–5 and 8–9 can be done independently without the icon.
