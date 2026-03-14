---
title: 'In-App Feedback / Bug Reporting (Shake + Settings Entry)'
slug: 'in-app-feedback-settings'
created: '2026-03-14'
revised: '2026-03-14'
status: 'review'
stepsCompleted: [1, 2, 3, 4]
review_findings_addressed: [F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Kermit 2.0.4', 'core-ktx 1.17.0', 'atomicfu']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/App.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt'
  - 'composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt'
  - 'composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt'
  - 'composeApp/src/androidMain/AndroidManifest.xml'
code_patterns: ['expect/actual', 'Koin platformModule', 'PlatformBackHandler', 'AnimatedVisibility overlay']
test_patterns: ['commonTest unit tests', 'fake collaborators in commonTest/fakes/']
---

# Tech-Spec: In-App Feedback / Bug Reporting (Shake + Settings Entry)

**Created:** 2026-03-14 · **Revised:** 2026-03-14 (15 review findings applied)

---

## Revision Summary

This is a revised spec. The following findings from adversarial review were applied:

| Finding | Change |
|---------|--------|
| **F15** | Shake-to-report promoted from Out of Scope to **primary trigger** — FAB dismisses all overlays, making FAB→Settings useless for capturing overlay-state bugs |
| **F1** | Screenshot timing extended — shake path must wait 350ms (animation duration) after dismissing ALL overlays, not just one frame |
| **F14** | `@Synchronized` replaced with `kotlinx.atomicfu.locks.SynchronizedObject` + `synchronized(this)` — `@Synchronized` is silently ignored by Kotlin/Native (iOS), risking corrupt ring buffer reads |
| **F5** | Log body capped at 8000 UTF-8 **bytes** (not 8000 chars) — multi-byte chars could overflow Binder transaction limit |
| **F2** | Activity context staleness guard confirmed and reinforced in T4 |
| **F3/F13** | MailDelegate strong-ref pattern confirmed and reinforced in T6 |
| **F4** | ClipData + `message/rfc822` pattern confirmed correct in T4 |
| **F6** | iOS 15+ `connectedScenes` window API confirmed and reinforced in T6 |
| **F7** | Topmost VC presentation confirmed and reinforced in T6 |
| **F8** | `Logger.addLogWriter` once-guard confirmed in T2 |
| **F9** | `ActivityNotFoundException` guard confirmed in T4 |
| **F10** | `feedbackScreenshot` memory note confirmed in Notes |
| **F11** | `objectForInfoDictionaryKey` iOS version API confirmed in T3 |
| **F12** | iOS `toImageBitmap` returns null (no skia internals) confirmed in T8 |

---

## Overview

### Problem Statement

There is no way for testers to report bugs. Feature #37 is a tester gate item — access cannot be granted until this ships. The `onSettings` callback in `FabMenu` currently shows a "Coming soon" snackbar (`MapScreen.kt` line ~451).

Additionally, the FAB menu itself **dismisses all open overlays** when tapped. This means a FAB→Settings entry point cannot capture screenshots of overlay-state bugs (open chat, open saved sheet, open pin cards, etc.). Shake-to-report is required as the primary trigger because it fires in any app state without disturbing the UI.

### Solution

**Primary trigger (shake):** `AndroidShakeDetector` / `IosShakeDetector` (registered in Koin, injected into `MapScreen`) fires `onShake` in any app state. `MapScreen` dismisses all active overlays, waits 350ms for exit animations to complete, captures a clean map screenshot, then opens `FeedbackPreviewSheet` directly.

**Secondary trigger (Settings):** The existing `onSettings` hook opens `SettingsSheet`. Tapping "Send Feedback" closes the sheet, waits one Compose frame, captures a screenshot, then opens `FeedbackPreviewSheet`.

Both paths converge at `FeedbackPreviewSheet` (thumbnail + optional description). On confirm, launches a platform email intent to `saturnplasma@gmail.com` with screenshot attached and device info + last 50 Kermit log lines (capped at 8000 UTF-8 bytes) in the body.

### Scope

**In Scope:**
- `ShakeDetector` interface (commonMain) + `AndroidShakeDetector` (accelerometer, SensorManager) + `IosShakeDetector` (accelerometer, CMMotionManager)
- `SettingsSheet` — new `ModalBottomSheet` composable with "Send Feedback" + version rows (secondary entry point)
- `FeedbackPreviewSheet` — screenshot thumbnail + optional description + Send/Cancel
- `RingBufferLogWriter` — Kermit `LogWriter` keeping last 50 lines × 200 chars in memory (`commonMain`); thread-safe via `atomicfu` `SynchronizedObject`
- `FeedbackReporter` interface (commonMain) + `AndroidFeedbackReporter` + `IosFeedbackReporter`
- Android screenshot: `decorView.drawToBitmap()`, `FileProvider` URI, `ACTION_SEND` with `ClipData` attachment, `ActivityNotFoundException` guard
- iOS screenshot: `UIWindowScene`-based window lookup (iOS 13+), `UIGraphicsImageRenderer`, `MFMailComposeViewController` presented from topmost VC, MailDelegate retained on reporter instance, `mailto:` fallback
- Shake: Android `SensorManager` accelerometer (TYPE_ACCELEROMETER, 2.5g threshold, 1s debounce); iOS `CMMotionManager` accelerometer (10Hz polling, same threshold)
- `dismissAllOverlays()` helper in `MapScreen` that closes FAB, chat, saves sheet, and local overlay states before shake-triggered capture
- Device info in email body via `getPlatform().name`
- Version number displayed in Settings sheet via `expect val appVersionName`
- `PlatformBackHandler` on both new sheets
- Screenshot timing: Settings path = `awaitFrame()` (one frame); Shake path = `delay(350)` (animation exit budget)

**Out of Scope:**
- Backend / crash reporting pipeline
- Screenshot redaction / annotation tools
- Analytics on feedback submissions
- Settings items beyond "Send Feedback" and version

---

## Context for Development

### Codebase Patterns

- **expect/actual**: Follow the `PlatformBackHandler` pattern exactly — `expect` in `commonMain`, `actual` in `androidMain` and `iosMain`. `FeedbackReporter` and `appVersionName` use this pattern.
- **Koin DI**: `platformModule()` is `expect fun` in `commonMain/di/AppModule.kt`. Register `FeedbackReporter` and `ShakeDetector` in each platform's `PlatformModule.{platform}.kt`.
- **Overlays in MapScreen**: Existing overlays use `AnimatedVisibility` + ViewModel state booleans. New sheets use `ModalBottomSheet`. Screenshot capture uses a `pendingCapture` boolean state + `LaunchedEffect` + `awaitFrame()` (Settings path) or `delay(350)` (shake path) to defer after sheet exit.
- **Kermit 2.0.4**: `Logger.addLogWriter(writer)` is NOT idempotent. Initialize exactly once at app startup using a top-level `object` initializer before `KoinApplication`.
- **Android context in Koin**: `androidContext()` in platform module resolves to `MainActivity`. Activity can be destroyed in background — always null-check the cast to `Activity` before calling `decorView.drawToBitmap()`. **(F2)**
- **iOS window lookup**: `UIApplication.windows` is deprecated iOS 15+. Use `connectedScenes → UIWindowScene → windows` chain everywhere. **(F6)**
- **iOS MFMailComposeVC presentation**: Must present from the topmost presented VC (traverse `presentedViewController` chain), not `rootViewController` directly — `FeedbackPreviewSheet` (a Compose `ModalBottomSheet`) is already presented when `launchEmail` is called. **(F7)**
- **Thread safety**: `@Synchronized` is silently ignored by Kotlin/Native. Use `kotlinx.atomicfu.locks.SynchronizedObject` + `synchronized(this)` for K/N-safe synchronization. **(F14)**
- **atomicfu availability**: atomicfu is available transitively via `kotlinx-coroutines-core`. If the build fails, add `implementation("org.jetbrains.kotlinx:atomicfu:0.23.2")` to `composeApp/build.gradle.kts`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/PlatformBackHandler.kt` | expect/actual pattern to replicate for FeedbackReporter and ShakeDetector |
| `composeApp/src/androidMain/kotlin/com/harazone/ui/components/PlatformBackHandler.android.kt` | Android actual example |
| `composeApp/src/iosMain/kotlin/com/harazone/ui/components/PlatformBackHandler.ios.kt` | iOS actual example |
| `composeApp/src/commonMain/kotlin/com/harazone/di/AppModule.kt` | `platformModule()` expect declaration |
| `composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt` | Android Koin registrations — add FeedbackReporter + ShakeDetector here |
| `composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt` | iOS Koin registrations — add FeedbackReporter + ShakeDetector here |
| `composeApp/src/commonMain/kotlin/com/harazone/App.kt` | KoinApplication setup — register LogWriter here (once, at top level) |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` | Wire onSettings → SettingsSheet (line ~451); inject FeedbackReporter + ShakeDetector via koinInject() |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FabMenu.kt` | onSettings callback already declared |
| `composeApp/src/commonMain/kotlin/com/harazone/util/AppLogger.kt` | Kermit Logger instance — RingBufferLogWriter plugs in here |
| `composeApp/src/iosMain/kotlin/com/harazone/Platform.ios.kt` | UIDevice import pattern for iOS platform info |
| `composeApp/src/androidMain/AndroidManifest.xml` | Needs FileProvider + email query additions |
| `composeApp/build.gradle.kts` | `versionName = "1.0"` at line 158 |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Confirm dismiss method names before T11 (closeFab, closeChat, hideSavesSheet equivalents) |

### Technical Decisions

- **Shake as primary trigger (F15):** The FAB menu collapses all overlays before expanding — FAB→Settings cannot capture overlay-state bugs. Shake fires without touching the UI. `ShakeDetector` is a Koin `single<ShakeDetector>` interface registered per-platform, injected into `MapScreen`, started via `DisposableEffect(Unit)`.

- **Screenshot timing — Settings path (F1):** Set `showSettings = false` and `pendingCapture = true` when "Send Feedback" is tapped. A `LaunchedEffect(pendingCapture)` calls `awaitFrame()` (one Compose frame — enough for `ModalBottomSheet` to leave the composition tree) before calling `feedbackReporter.captureScreenshot()`.

- **Screenshot timing — Shake path (F1 extended):** On shake, call `dismissAllOverlays()` (see T11) then set `pendingShakeCapture = true`. A separate `LaunchedEffect(pendingShakeCapture)` calls `delay(350)` — the Material3 bottom sheet exit animation budget (~300ms) plus margin — before calling `feedbackReporter.captureScreenshot()`. A single `awaitFrame()` is insufficient here because `AnimatedVisibility` exit animations are in progress.

- **Thread safety for RingBufferLogWriter (F14):** Extend `SynchronizedObject` from `kotlinx.atomicfu.locks` and wrap `log()` and `getLines()` bodies with `synchronized(this) { }`. Unlike `@Synchronized`, `atomicfu`'s `synchronized` compiles to K/N-safe spinlock on iOS and `synchronized` on JVM. No separate NSLock expect/actual needed.

- **Log body size cap in bytes (F5):** `getLines()` must cap at 8000 UTF-8 **bytes** — not 8000 chars — to respect the Android Binder transaction limit (1MB, email body is a fraction) and the iOS `mailto:` URL practical limit (~8KB). Implementation: encode `joinToString("\n")` to UTF-8 bytes; if `≤ 8000` bytes, return as-is; else truncate at 7500 chars (conservative ASCII-safe proxy that avoids splitting multi-byte sequences). See T1.

- **Screenshot on Android (F2):** Cast `context as? Activity`; if null, log warning via `AppLogger.w` and return null (Activity may have been destroyed). Call `activity.window.decorView.drawToBitmap()` (`core-ktx` 1.17.0, must be on main thread — Compose `LaunchedEffect` runs on main dispatcher by default). Compress to PNG bytes.

- **Screenshot on iOS (F6):** Use `UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().flatMap { it.windows }.firstOrNull { it.isKeyWindow }`. `UIApplication.windows` is deprecated iOS 15+ and must not be used. Fall back to `null` gracefully if no key window found.

- **Email on Android (F4):** Keep `intent.type = "message/rfc822"` always — overriding to `"image/png"` breaks email client matching. For attachment: `intent.putExtra(Intent.EXTRA_STREAM, uri)` AND `intent.clipData = ClipData.newUri(context.contentResolver, "Screenshot", uri)` AND `intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)`. Wrap `startActivity` in `try/catch ActivityNotFoundException` — show toast "No email app found" on failure. **(F4, F9)**

- **Email on iOS — topmost VC (F7):** Traverse `rootViewController.presentedViewController` chain until `presentedViewController == null`. Present `MFMailComposeViewController` from that topmost VC, not directly from `rootViewController`. A Compose `ModalBottomSheet` is already presented when `launchEmail` is called.

- **MailDelegate retain on IosFeedbackReporter (F3/F13):** `IosFeedbackReporter` keeps `private var activeMailDelegate: MailDelegate? = null`. Assign before wiring to VC: `activeMailDelegate = MailDelegate(onDone = { activeMailDelegate = null })`. `MFMailComposeViewController` holds only a **weak** ref to its delegate — without this strong ref on the reporter, the delegate is immediately deallocated and `didFinishWithResult` never fires.

- **Logger.addLogWriter once (F8):** Register `ringBufferLogWriter` via a top-level `private object LogWriterInit { init { Logger.addLogWriter(ringBufferLogWriter) } }` before the `App()` composable, triggered by `remember { LogWriterInit; true }` inside `App()`. The `object` `init` runs once per process regardless of Activity recreation.

- **iOS version string (F11):** Use `NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"`. Do not use `infoDictionary?.get("CFBundleShortVersionString")` — that returns `Any?` and K/N bridging is unreliable. `objectForInfoDictionaryKey` returns a properly bridged K/N `String`.

- **iOS `toImageBitmap` (F12):** iOS actual returns `null` always — avoids depending on `org.jetbrains.skia` internal API. `FeedbackPreviewSheet` shows "Screenshot captured ✓" text when `bitmap == null`.

- **`feedbackScreenshot` memory (F10):** Holds 300KB–2MB in Compose snapshot state. Cleared on every dismiss/send path. Screen rotation while `FeedbackPreviewSheet` is open retains the bytes until next dismiss. Acceptable for v1 tester tool.

- **FeedbackReporter interface:**
  ```kotlin
  // commonMain/feedback/FeedbackReporter.kt
  interface FeedbackReporter {
      fun captureScreenshot(): ByteArray?
      fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String)
  }
  ```

- **ShakeDetector interface:**
  ```kotlin
  // commonMain/feedback/ShakeDetector.kt
  interface ShakeDetector {
      fun start(onShake: () -> Unit)
      fun stop()
  }
  ```

- **Version:** `expect val appVersionName: String` in `commonMain`. Android actual reads `BuildConfig.VERSION_NAME`. iOS actual uses `objectForInfoDictionaryKey`.

---

## Implementation Plan

### Tasks

Tasks are ordered dependency-first.

- [x] **T1 — Create RingBufferLogWriter (thread-safe, bytes-capped)**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/util/RingBufferLogWriter.kt` (new)
  - Action: Create `class RingBufferLogWriter(val maxLines: Int = 50, val maxLineLength: Int = 200) : SynchronizedObject(), LogWriter()`.
    - Internal state: `private val deque = ArrayDeque<String>()`.
    - Override `log` wrapped in `synchronized(this)`:
      ```kotlin
      override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) =
          synchronized(this) {
              val line = "[${severity.name}] $tag: $message".take(maxLineLength)
              if (deque.size >= maxLines) deque.removeFirst()
              deque.addLast(line)
          }
      ```
    - Add `fun getLines(): String`:
      ```kotlin
      fun getLines(): String {
          val joined = synchronized(this) { deque.joinToString("\n") }
          // Cap at 8000 UTF-8 bytes (F5). 7500-char proxy avoids multi-byte boundary splits.
          val bytes = joined.encodeToByteArray()
          return if (bytes.size <= 8000) joined else joined.take(7500)
      }
      ```
    - Below the class add file-level singleton: `val ringBufferLogWriter = RingBufferLogWriter()`.
  - Imports: `co.touchlab.kermit.LogWriter`, `co.touchlab.kermit.Severity`, `kotlinx.atomicfu.locks.SynchronizedObject`, `kotlinx.atomicfu.locks.synchronized`
  - **F14 note:** `@Synchronized` is silently ignored on Kotlin/Native. `atomicfu`'s `synchronized(this)` on a `SynchronizedObject` compiles to a K/N-safe spinlock on iOS and `synchronized` on JVM. No accept-and-document workaround — this is a real fix.
  - **F5 note:** Cap is in bytes (7500-char conservative proxy for 8000-byte limit).

- [x] **T2 — Register RingBufferLogWriter exactly once in App.kt**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/App.kt` (modify)
  - Action: Add a top-level `private object LogWriterInit { init { Logger.addLogWriter(ringBufferLogWriter) } }` before the `App()` composable. Inside `App()`, add `remember { LogWriterInit; true }` to trigger the object initialization on first composition only (subsequent recompositions hit the `remember` cache; Activity recreation produces a new composition but the `object` `init` only runs once per process). **(F8)**
  - Imports: `co.touchlab.kermit.Logger`, `com.harazone.util.ringBufferLogWriter`

- [x] **T3 — Create expect/actual appVersionName**
  - File (expect): `composeApp/src/commonMain/kotlin/com/harazone/util/AppVersion.kt` (new)
    ```kotlin
    package com.harazone.util
    expect val appVersionName: String
    ```
  - File (Android actual): `composeApp/src/androidMain/kotlin/com/harazone/util/AppVersion.android.kt` (new)
    ```kotlin
    package com.harazone.util
    import com.harazone.BuildConfig
    actual val appVersionName: String = BuildConfig.VERSION_NAME
    ```
  - File (iOS actual): `composeApp/src/iosMain/kotlin/com/harazone/util/AppVersion.ios.kt` (new)
    ```kotlin
    package com.harazone.util
    import platform.Foundation.NSBundle
    // F11: objectForInfoDictionaryKey — correct localized-aware API, reliably K/N-bridged.
    // Do NOT use infoDictionary?.get("CFBundleShortVersionString") — K/N bridging is unreliable.
    actual val appVersionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"
    ```

- [x] **T4 — Create FeedbackReporter interface + Android actual**
  - File (interface): `composeApp/src/commonMain/kotlin/com/harazone/feedback/FeedbackReporter.kt` (new)
    ```kotlin
    package com.harazone.feedback
    interface FeedbackReporter {
        fun captureScreenshot(): ByteArray?
        fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String)
    }
    ```
  - File (Android): `composeApp/src/androidMain/kotlin/com/harazone/feedback/AndroidFeedbackReporter.kt` (new)
    - `class AndroidFeedbackReporter(private val context: Context) : FeedbackReporter`
    - `captureScreenshot()`:
      ```kotlin
      override fun captureScreenshot(): ByteArray? {
          // F2: Activity context staleness guard — context may not be an Activity if destroyed.
          val activity = context as? Activity
          if (activity == null) {
              AppLogger.w("AndroidFeedbackReporter", "captureScreenshot: context is not an Activity — skipping")
              return null
          }
          val bmp = activity.window.decorView.drawToBitmap()
          val baos = ByteArrayOutputStream()
          bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
          return baos.toByteArray()
      }
      ```
    - `launchEmail(screenshot, description, deviceInfo, logs)`:
      ```kotlin
      override fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String) {
          val body = "--- Device Info ---\n$deviceInfo\n\n--- Description ---\n${description.ifBlank { "(none)" }}\n\n--- Logs ---\n$logs"
          val intent = Intent(Intent.ACTION_SEND).apply {
              // F4: type must stay "message/rfc822" — do NOT override to "image/png".
              // ClipData grants URI permission while preserving email client matching.
              type = "message/rfc822"
              putExtra(Intent.EXTRA_EMAIL, arrayOf("saturnplasma@gmail.com"))
              putExtra(Intent.EXTRA_SUBJECT, "AreaDiscovery Feedback")
              putExtra(Intent.EXTRA_TEXT, body)
          }
          if (screenshot != null) {
              val file = File(context.cacheDir, "feedback_screenshot.png")
              file.writeBytes(screenshot)
              val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
              intent.putExtra(Intent.EXTRA_STREAM, uri)
              intent.clipData = ClipData.newUri(context.contentResolver, "Screenshot", uri)
              intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          // F9: Guard against no email client installed.
          try {
              context.startActivity(Intent.createChooser(intent, "Send Feedback"))
          } catch (e: ActivityNotFoundException) {
              AppLogger.w("AndroidFeedbackReporter", "No email client available: ${e.message}")
              Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
          }
      }
      ```
    - Imports: `android.app.Activity`, `android.content.ActivityNotFoundException`, `android.content.ClipData`, `android.content.Context`, `android.content.Intent`, `android.graphics.Bitmap`, `android.widget.Toast`, `androidx.core.content.FileProvider`, `androidx.core.view.drawToBitmap`, `com.harazone.util.AppLogger`, `java.io.*`

- [x] **T5 — AndroidManifest: add email query + FileProvider**
  - File: `composeApp/src/androidMain/AndroidManifest.xml` (modify)
  - Add inside the existing `<queries>` block (alongside the geo intent):
    ```xml
    <intent>
        <action android:name="android.intent.action.SENDTO" />
        <data android:scheme="mailto" />
    </intent>
    ```
  - Add inside the `<application>` block (before `</application>`):
    ```xml
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.provider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_provider_paths" />
    </provider>
    ```
  - Create new file `composeApp/src/androidMain/res/xml/file_provider_paths.xml`:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <paths>
        <cache-path name="feedback" path="." />
    </paths>
    ```

- [x] **T6 — Create iOS actual FeedbackReporter**
  - File: `composeApp/src/iosMain/kotlin/com/harazone/feedback/IosFeedbackReporter.kt` (new)
  - ```kotlin
    class IosFeedbackReporter : FeedbackReporter {
        // F3/F13: Strong reference — MFMailComposeVC holds only a WEAK ref to its delegate.
        // Without this, delegate is immediately deallocated and didFinishWithResult never fires.
        private var activeMailDelegate: MailDelegate? = null

        override fun captureScreenshot(): ByteArray? {
            // F6: UIApplication.windows deprecated iOS 15+. Use connectedScenes chain.
            val keyWindow = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .flatMap { it.windows }
                .firstOrNull { it.isKeyWindow }
                ?: return null
            val renderer = UIGraphicsImageRenderer(bounds = keyWindow.bounds)
            val nsData = renderer.JPEGDataWithCompressionQuality(0.8) { ctx ->
                keyWindow.layer.renderInContext(ctx!!.CGContext)
            }
            return ByteArray(nsData.length.toInt()).also { nsData.getBytes(it.refTo(0), nsData.length) }
        }

        override fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String) {
            val body = "--- Device Info ---\n$deviceInfo\n\n--- Description ---\n${description.ifBlank { "(none)" }}\n\n--- Logs ---\n$logs"

            if (!MFMailComposeViewController.canSendMail()) {
                // Fallback: mailto: URL. Body capped at 2000 chars to respect iOS URL limits (~8KB).
                val encoded = body.take(2000).encodeURLPath()
                val url = NSURL(string = "mailto:saturnplasma@gmail.com?subject=AreaDiscovery%20Feedback&body=$encoded")
                UIApplication.sharedApplication.openURL(url!!)
                return
            }

            val vc = MFMailComposeViewController()
            val delegate = MailDelegate { activeMailDelegate = null }
            activeMailDelegate = delegate  // must retain strongly before assigning
            vc.mailComposeDelegate = delegate
            vc.setToRecipients(listOf("saturnplasma@gmail.com"))
            vc.setSubject("AreaDiscovery Feedback")
            vc.setMessageBody(body, isHTML = false)

            if (screenshot != null) {
                val nsData = NSData.dataWithBytes(screenshot.refTo(0), screenshot.size.toULong())
                vc.addAttachmentData(nsData, mimeType = "image/jpeg", fileName = "screenshot.jpg")
            }

            // F7: Present from TOPMOST VC — rootViewController may already have a modal
            // (FeedbackPreviewSheet Compose ModalBottomSheet). Presenting from rootViewController
            // directly will crash or silently fail on iOS.
            val rootVc = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .flatMap { it.windows }
                .firstOrNull { it.isKeyWindow }
                ?.rootViewController ?: return
            var topVc: UIViewController = rootVc
            while (topVc.presentedViewController != null) {
                topVc = topVc.presentedViewController!!
            }
            topVc.presentViewController(vc, animated = true, completion = null)
        }
    }

    private class MailDelegate(private val onDone: () -> Unit) : NSObject(), MFMailComposeViewControllerDelegateProtocol {
        override fun mailComposeController(
            controller: MFMailComposeViewController,
            didFinishWithResult: MFMailComposeResult,
            error: NSError?
        ) {
            controller.dismissViewControllerAnimated(true, completion = null)
            onDone()  // clears activeMailDelegate on IosFeedbackReporter
        }
    }
    ```
  - Imports: `platform.MessageUI.*`, `platform.UIKit.*`, `platform.Foundation.*`

- [x] **T7 — Create ShakeDetector interface + platform implementations**
  - File (interface): `composeApp/src/commonMain/kotlin/com/harazone/feedback/ShakeDetector.kt` (new)
    ```kotlin
    package com.harazone.feedback

    /** Platform-specific shake detector. Inject via Koin (single<ShakeDetector>). */
    interface ShakeDetector {
        /** Start listening. [onShake] fires on the main thread with a 1s debounce. */
        fun start(onShake: () -> Unit)
        /** Stop listening and release platform resources. Call from DisposableEffect.onDispose. */
        fun stop()
    }
    ```

  - File (Android): `composeApp/src/androidMain/kotlin/com/harazone/feedback/AndroidShakeDetector.kt` (new)
    ```kotlin
    package com.harazone.feedback

    import android.content.Context
    import android.hardware.Sensor
    import android.hardware.SensorEvent
    import android.hardware.SensorEventListener
    import android.hardware.SensorManager
    import kotlin.math.sqrt

    class AndroidShakeDetector(context: Context) : ShakeDetector {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private var listener: SensorEventListener? = null
        private var lastShakeMs = 0L
        private val thresholdG = 2.5f
        private val debouncMs = 1000L

        override fun start(onShake: () -> Unit) {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0] / SensorManager.GRAVITY_EARTH
                    val y = event.values[1] / SensorManager.GRAVITY_EARTH
                    val z = event.values[2] / SensorManager.GRAVITY_EARTH
                    val g = sqrt(x * x + y * y + z * z)
                    val now = System.currentTimeMillis()
                    if (g > thresholdG && now - lastShakeMs > debouncMs) {
                        lastShakeMs = now
                        onShake()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }

        override fun stop() {
            listener?.let { sensorManager.unregisterListener(it) }
            listener = null
        }
    }
    ```

  - File (iOS): `composeApp/src/iosMain/kotlin/com/harazone/feedback/IosShakeDetector.kt` (new)
    ```kotlin
    package com.harazone.feedback

    import platform.CoreMotion.CMMotionManager
    import platform.Foundation.NSOperationQueue
    import kotlin.math.sqrt

    class IosShakeDetector : ShakeDetector {
        private val motionManager = CMMotionManager()
        private val thresholdG = 2.5
        private var lastShakeMs = 0L
        private val debounceMs = 1000L

        override fun start(onShake: () -> Unit) {
            if (!motionManager.accelerometerAvailable) return
            motionManager.accelerometerUpdateInterval = 0.1 // 10 Hz
            motionManager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue()) { data, _ ->
                data?.let {
                    val x = it.acceleration.x
                    val y = it.acceleration.y
                    val z = it.acceleration.z
                    val g = sqrt(x * x + y * y + z * z)
                    val nowMs = (platform.Foundation.NSDate.date().timeIntervalSince1970 * 1000).toLong()
                    if (g > thresholdG && nowMs - lastShakeMs > debounceMs) {
                        lastShakeMs = nowMs
                        onShake()
                    }
                }
            }
        }

        override fun stop() {
            motionManager.stopAccelerometerUpdates()
        }
    }
    ```
  - Note: Raw accelerometer via CMMotionManager does NOT require Motion & Fitness permission — only step count / motion activity APIs require that entitlement.

- [x] **T8 — Register FeedbackReporter + ShakeDetector in both platformModules**
  - File: `composeApp/src/androidMain/kotlin/com/harazone/di/PlatformModule.android.kt` (modify)
    ```kotlin
    // Add imports:
    import com.harazone.feedback.AndroidFeedbackReporter
    import com.harazone.feedback.AndroidShakeDetector
    import com.harazone.feedback.FeedbackReporter
    import com.harazone.feedback.ShakeDetector
    // Add inside module { }:
    single<FeedbackReporter> { AndroidFeedbackReporter(androidContext()) }
    single<ShakeDetector> { AndroidShakeDetector(androidContext()) }
    ```
  - File: `composeApp/src/iosMain/kotlin/com/harazone/di/PlatformModule.ios.kt` (modify)
    ```kotlin
    // Add imports:
    import com.harazone.feedback.IosFeedbackReporter
    import com.harazone.feedback.IosShakeDetector
    import com.harazone.feedback.FeedbackReporter
    import com.harazone.feedback.ShakeDetector
    // Add inside module { }:
    single<FeedbackReporter> { IosFeedbackReporter() }
    single<ShakeDetector> { IosShakeDetector() }
    ```

- [x] **T9 — Create expect/actual ByteArray.toImageBitmap()**
  - File (expect): `composeApp/src/commonMain/kotlin/com/harazone/util/ImageBitmapExt.kt` (new)
    ```kotlin
    package com.harazone.util
    import androidx.compose.ui.graphics.ImageBitmap
    expect fun ByteArray.toImageBitmap(): ImageBitmap?
    ```
  - File (Android actual): `composeApp/src/androidMain/kotlin/com/harazone/util/ImageBitmapExt.android.kt` (new)
    ```kotlin
    package com.harazone.util
    import android.graphics.BitmapFactory
    import androidx.compose.ui.graphics.ImageBitmap
    import androidx.compose.ui.graphics.asImageBitmap
    actual fun ByteArray.toImageBitmap(): ImageBitmap? =
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    ```
  - File (iOS actual): `composeApp/src/iosMain/kotlin/com/harazone/util/ImageBitmapExt.ios.kt` (new)
    ```kotlin
    package com.harazone.util
    import androidx.compose.ui.graphics.ImageBitmap
    // F12: Return null — avoids dependency on internal org.jetbrains.skia API.
    // FeedbackPreviewSheet falls back to "Screenshot captured ✓" text on iOS.
    actual fun ByteArray.toImageBitmap(): ImageBitmap? = null
    ```

- [x] **T10 — Create SettingsSheet composable**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/settings/SettingsSheet.kt` (new)
  - Signature: `@Composable fun SettingsSheet(onDismiss: () -> Unit, onSendFeedback: () -> Unit)`
  - Body:
    ```kotlin
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true) { onDismiss() }
        ListItem(
            headlineContent = { Text("Version") },
            trailingContent = { Text(appVersionName) }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Send Feedback") },
            leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
            modifier = Modifier.clickable { onSendFeedback() }
        )
        Spacer(Modifier.height(16.dp))
    }
    ```
  - Imports: `com.harazone.ui.components.PlatformBackHandler`, `com.harazone.util.appVersionName`, `androidx.compose.material3.*`, `androidx.compose.material.icons.filled.BugReport`

- [x] **T11 — Create FeedbackPreviewSheet composable**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/settings/FeedbackPreviewSheet.kt` (new)
  - Signature: `@Composable fun FeedbackPreviewSheet(screenshotBytes: ByteArray?, onDismiss: () -> Unit, onSend: (description: String) -> Unit)`
  - State: `var description by remember { mutableStateOf("") }`
  - Body:
    ```kotlin
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true) { onDismiss() }
        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
            if (screenshotBytes != null) {
                val bitmap = remember(screenshotBytes) { screenshotBytes.toImageBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Screenshot preview",
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // F12: iOS always hits this branch (toImageBitmap returns null on iOS)
                    Text("Screenshot captured ✓", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Describe the issue...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSend(description) }) { Text("Send Report") }
            }
        }
    }
    ```

- [x] **T12 — Wire everything into MapScreen**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt` (modify)
  - **Step 1 — Read MapViewModel.kt first** to confirm the exact method names for: closing the FAB (`isFabExpanded`), closing chat (`chatState.isOpen`), and hiding the saves sheet (`showSavesSheet`). Use those exact names in `dismissAllOverlays()`.
  - Add imports:
    ```kotlin
    import com.harazone.feedback.FeedbackReporter
    import com.harazone.feedback.ShakeDetector
    import com.harazone.ui.settings.SettingsSheet
    import com.harazone.ui.settings.FeedbackPreviewSheet
    import com.harazone.util.ringBufferLogWriter
    import kotlinx.coroutines.awaitFrame
    import kotlinx.coroutines.delay
    ```
  - Add inside `MapScreen` composable body alongside existing state:
    ```kotlin
    val feedbackReporter: FeedbackReporter = koinInject()
    val shakeDetector: ShakeDetector = koinInject()
    var showSettings by remember { mutableStateOf(false) }
    var showFeedbackPreview by remember { mutableStateOf(false) }
    var feedbackScreenshot by remember { mutableStateOf<ByteArray?>(null) }
    // Settings path: set true after showSettings = false
    var pendingCapture by remember { mutableStateOf(false) }
    // Shake path: set true after dismissAllOverlays()
    var pendingShakeCapture by remember { mutableStateOf(false) }
    ```
  - Add `dismissAllOverlays` helper (use confirmed ViewModel method names from Step 1 above):
    ```kotlin
    fun dismissAllOverlays() {
        // Close ViewModel-owned overlays. Confirm exact method names from MapViewModel.kt.
        if (state.isFabExpanded) viewModel.closeFab()        // or toggleFab() / collapseFab()
        if (chatState.isOpen) viewModel.closeChat()          // or toggleChat() / collapseChat()
        if (state.showSavesSheet) viewModel.hideSavesSheet() // or toggleSavesSheet()
        // Close local state overlays
        showSettings = false
        showFeedbackPreview = false
    }
    ```
  - Add `LaunchedEffect` blocks for both capture paths:
    ```kotlin
    // Settings path (F1): one frame is enough — SettingsSheet has already left composition
    LaunchedEffect(pendingCapture) {
        if (pendingCapture) {
            awaitFrame()
            feedbackScreenshot = feedbackReporter.captureScreenshot()
            pendingCapture = false
            showFeedbackPreview = true
        }
    }

    // Shake path (F1 extended): 350ms budget for Material3 bottom sheet exit animation (~300ms)
    LaunchedEffect(pendingShakeCapture) {
        if (pendingShakeCapture) {
            delay(350)
            feedbackScreenshot = feedbackReporter.captureScreenshot()
            pendingShakeCapture = false
            showFeedbackPreview = true
        }
    }
    ```
  - Add shake wiring via `DisposableEffect` (F15):
    ```kotlin
    DisposableEffect(Unit) {
        shakeDetector.start {
            // Only start a new shake capture if FeedbackPreviewSheet is not already showing
            if (!showFeedbackPreview && !pendingShakeCapture) {
                dismissAllOverlays()
                pendingShakeCapture = true
            }
        }
        onDispose { shakeDetector.stop() }
    }
    ```
  - Replace `onSettings` lambda in `FabMenu` (line ~451, currently shows snackbar):
    ```kotlin
    onSettings = {
        viewModel.toggleFab()
        showSettings = true
    }
    ```
  - Add conditional blocks for new sheets alongside the existing overlay blocks:
    ```kotlin
    // Settings sheet (secondary entry point)
    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            onSendFeedback = {
                showSettings = false
                pendingCapture = true  // triggers Settings path LaunchedEffect
            }
        )
    }

    // Feedback preview (shared by both entry points)
    if (showFeedbackPreview) {
        FeedbackPreviewSheet(
            screenshotBytes = feedbackScreenshot,
            onDismiss = {
                showFeedbackPreview = false
                feedbackScreenshot = null  // F10: clear memory
            },
            onSend = { desc ->
                feedbackReporter.launchEmail(
                    screenshot = feedbackScreenshot,
                    description = desc,
                    deviceInfo = getPlatform().name,
                    logs = ringBufferLogWriter.getLines()
                )
                showFeedbackPreview = false
                feedbackScreenshot = null  // F10: clear memory
            }
        )
    }
    ```

---

### Acceptance Criteria

- [x] **AC1** — Given FAB menu is open, when tester taps the Settings icon, then FAB closes and `SettingsSheet` opens showing a "Send Feedback" row and a "Version 1.0" row.

- [x] **AC2** — Given `SettingsSheet` is open, when tester taps outside the sheet or presses Android back, then sheet dismisses and map is visible with no crash.

- [x] **AC3 (Settings path)** — Given `SettingsSheet` is open, when tester taps "Send Feedback", then `SettingsSheet` closes and `FeedbackPreviewSheet` opens showing either a screenshot thumbnail (Android) or "Screenshot captured ✓" text (iOS). The screenshot must show the map, NOT the settings sheet.

- [x] **AC3b (Shake path)** — Given any overlay is open (chat, saves sheet, FAB menu, pin card), when tester shakes the device, then all overlays close, the app waits ~350ms, captures a clean map screenshot, and `FeedbackPreviewSheet` opens. No crash. No double-trigger if `FeedbackPreviewSheet` is already visible.

- [x] **AC4** — Given `FeedbackPreviewSheet` is open on Android, when tester optionally fills description and taps "Send Report", then Android email chooser opens with `to = saturnplasma@gmail.com`, subject "AreaDiscovery Feedback", body containing device info + log lines, and screenshot attached as PNG.

- [x] **AC4b** — Given `FeedbackPreviewSheet` is open on Android with no email client installed, when tester taps "Send Report", then a toast "No email app found" is shown and the app does not crash. **(F9)**

- [x] **AC5** — Given `FeedbackPreviewSheet` is open on iOS with `canSendMail() = true`, when tester taps "Send Report", then `MFMailComposeViewController` opens pre-filled with same fields and screenshot attached as JPEG.

- [x] **AC5b** — Given `FeedbackPreviewSheet` is open on iOS with `canSendMail() = false`, when tester taps "Send Report", then app opens `mailto:` URL with email body text (no attachment, no crash).

- [x] **AC6** — Given `FeedbackPreviewSheet` is open, when tester taps "Cancel" or presses Android back, then sheet dismisses, map is visible, no crash, no email sent, `feedbackScreenshot` state is cleared.

- [x] **AC7** — Given the app has generated log output via `AppLogger`, when feedback email is composed, then the email body contains lines in the format `[SEVERITY] tag: message`, and the log portion of the body is ≤ 8000 UTF-8 bytes.

- [x] **AC8** — Given any platform, when feedback email is composed, then the email body contains the platform name and OS version string from `getPlatform().name`.

- [x] **AC9** — Given `MFMailComposeViewController` is presented on iOS, when tester taps Send or Cancel in the mail VC, then the VC dismisses correctly and `activeMailDelegate` is cleared (no retain cycle). **(F3/F13)**

---

## Additional Context

### Dependencies

- `androidx.core:core-ktx 1.17.0` — already present; `drawToBitmap()`, `ClipData`
- `androidx.activity:activity-compose` — already present
- `co.touchlab.kermit 2.0.4` — already present; `Logger.addLogWriter()` available
- `kotlinx.atomicfu` — available transitively via `kotlinx-coroutines-core`. If build fails, add `implementation("org.jetbrains.kotlinx:atomicfu:0.23.2")` to `composeApp/build.gradle.kts`. **(F14)**
- `platform.MessageUI` (iOS `MessageUI.framework`) — no Gradle dep; available via K/N iOS target
- `platform.CoreMotion` (iOS `CoreMotion.framework`) — no Gradle dep; available via K/N iOS target. No permission required for raw accelerometer.
- `FileProvider` — part of `androidx.core`, already present
- `kotlinx.coroutines.awaitFrame` + `delay` — already available via Compose coroutines runtime
- **No new Gradle dependencies required (atomicfu likely already transitive).**

### Testing Strategy

**Unit tests (commonTest):**
- [x] **Test T1a** — Ring eviction: given 60 log entries written, `getLines()` returns exactly 50 lines.
- [x] **Test T1b** — Empty: given no entries, `getLines()` returns `""`.
- [x] **Test T1c** — Line cap: given a 500-char message, stored line is truncated to 200 chars.
- [x] **Test T1d** — Bytes cap: given 50 lines × 200 chars (ASCII), `getLines().encodeToByteArray().size` ≤ 8000. **(F5)**
- [ ] **Test T12a** — Screenshot null safety: fake `FeedbackReporter` where `captureScreenshot()` returns `null`; trigger `onSendFeedback`; verify `feedbackScreenshot = null` and `showFeedbackPreview = true`.
- [ ] **Test T12b** — Settings path cancel: call `onDismiss` on `FeedbackPreviewSheet`; verify `showFeedbackPreview = false` and `feedbackScreenshot = null`.
- [ ] **Test T12c** — Shake guard: trigger shake while `showFeedbackPreview = true`; verify `pendingShakeCapture` is NOT set a second time.
- [ ] **Test T12d** — `dismissAllOverlays` coverage: given all overlays open, calling `dismissAllOverlays()` sets all local state booleans to false and calls relevant ViewModel methods.

**Manual Android:**
- [ ] Build debug APK. FAB → Settings → Send Feedback → confirm map screenshot in preview (NOT sheet UI) → Send Report → confirm Gmail opens with correct to/subject/body/attachment.
- [ ] Shake while chat is open → confirm chat dismisses + clean map screenshot in preview.
- [ ] Shake while no overlay open → confirm clean map screenshot.
- [ ] On emulator with no email app: Send Report → confirm toast shown, no crash. **(F9)**
- [ ] Shake twice rapidly → confirm only one `FeedbackPreviewSheet` opens (debounce + guard).

**Manual iOS Simulator:**
- [ ] Same Settings path; `canSendMail()` = false on Simulator → confirm `mailto:` URL opened; no crash.
- [ ] Shake: Simulator → Hardware → Shake Gesture → confirm overlay dismisses + preview opens.

**Manual iOS Device:**
- [ ] Full Settings path with `MFMailComposeViewController`; tap Send/Cancel in mail VC → confirm it dismisses correctly and delegate is cleaned up. **(F3/F13, F7)**
- [ ] Full Shake path with overlays open.

### Notes

- **`awaitFrame()` import:** `kotlinx.coroutines.awaitFrame` is in the common coroutines runtime — available wherever `LaunchedEffect` is used.
- **Shake on iOS Simulator:** Hardware → Shake Gesture triggers `CMMotionManager` callbacks on real devices; Simulator may behave differently. Prefer device testing for shake path.
- **`decorView.drawToBitmap()` thread:** Must be called on the main thread. The `LaunchedEffect` coroutine runs on the main dispatcher in Compose — correct.
- **FileProvider authority:** `"${context.packageName}.provider"` — no other FileProvider currently registered in this app, no conflict risk.
- **iOS `mailto:` body truncation:** Body capped at 2000 chars in the fallback path to avoid exceeding iOS URL limits (~8KB). Applied before URL encoding.
- **F10 — feedbackScreenshot memory:** Holds 300KB–2MB in Compose snapshot state. Cleared on every dismiss/send path. Screen rotation while `FeedbackPreviewSheet` is open retains bytes until next dismiss. Acceptable for v1 tester tool.
- **ShakeDetector `disableEffect` vs. remember:** `shakeDetector` is a Koin `single<>` so it survives recomposition. `DisposableEffect(Unit)` ensures `stop()` is called when `MapScreen` leaves composition.
