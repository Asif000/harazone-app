# Story 2.4: Connectivity Monitor & Error Resilience

Status: done

## Story

As a **user**,
I want the app to handle network problems gracefully without ever showing an error screen,
so that I always see something useful regardless of connectivity.

## Acceptance Criteria

1. `ConnectivityMonitor` (expect/actual) in `commonMain/util/` emits `Flow<ConnectivityState>` with states: `Online`, `Offline`
2. Android implementation (`AndroidConnectivityMonitor`) uses `ConnectivityManager` callback API — registered with `NetworkCallback`, no polling
3. iOS implementation (`IosConnectivityMonitor`) uses `NWPathMonitor` on a dedicated `DispatchQueue`
4. `RetryHelper` in `commonMain/util/` provides `suspend fun <T> withRetry(maxAttempts: Int = 3, initialDelayMs: Long = 1000, maxDelayMs: Long = 10000, block: suspend () -> T): Result<T>` — exponential backoff (1s → 2s → 4s), respects `CancellationException` [NFR25]
5. `GeminiAreaIntelligenceProvider` refactored to use `RetryHelper.withRetry()` instead of its inline retry loop — behavior unchanged, code centralized
6. `BucketUpdate` sealed class gains a new variant: `ContentAvailabilityNote(message: String)` — emitted by repository to signal that content is from cache due to API failure or offline state
7. `AreaRepositoryImpl` updated: when the AI provider throws and there is any cached content (even stale), emit cached buckets + `ContentAvailabilityNote("Content from cache — may not be current")` + `PortraitComplete` instead of propagating the exception
8. `AreaRepositoryImpl` updated: when `ConnectivityMonitor` reports `Offline` before the AI call is attempted, immediately serve cached content (even stale) + `ContentAvailabilityNote("You're offline — showing last known content")` without calling the AI provider
9. If both connectivity is offline AND no cache exists for the area, emit `ContentAvailabilityNote("No content available offline for this area")` + `PortraitComplete(pois = emptyList())` — never crash or show empty
10. `FakeConnectivityMonitor` in `commonTest/fakes/` with configurable `MutableStateFlow<ConnectivityState>` for testing offline/online transitions
11. Unit tests in `commonTest` cover: `RetryHelper` success on first attempt, success after retries, all attempts exhausted returns `Result.failure`, respects `maxDelayMs` cap
12. Unit tests in `commonTest` (or `androidUnitTest` if JVM-only APIs required) cover: repository offline path (no AI call made, cached content served), repository AI-failure path (cached content served with note), repository no-cache-offline path (note + PortraitComplete emitted)
13. All 3 build gates pass: `./gradlew assembleDebug`, `./gradlew allTests`, `./gradlew lint`

## Tasks / Subtasks

- [x] Task 1: Create `ConnectivityState` and `ConnectivityMonitor` expect class (AC: #1)
  - [x] 1.1 Create `commonMain/kotlin/com/areadiscovery/domain/model/ConnectivityState.kt` — `sealed class ConnectivityState { data object Online : ConnectivityState(); data object Offline : ConnectivityState() }`
  - [x] 1.2 Create `commonMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.kt` — `expect class ConnectivityMonitor { fun observe(): Flow<ConnectivityState> }` (match `DatabaseDriverFactory` pattern: `expect class`, no `interface`)
  - [x] 1.3 Confirm `ConnectivityState` is in `domain/model/` — same package as `DomainError`, `BucketUpdate`, etc.

- [x] Task 2: Create Android `ConnectivityMonitor` actual (AC: #2)
  - [x] 2.1 Create `androidMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.android.kt`
  - [x] 2.2 `actual class ConnectivityMonitor(private val context: Context)` — constructor takes Android `Context`
  - [x] 2.3 Implement `observe()` using `ConnectivityManager.registerNetworkCallback()` with `callbackFlow { }` — emit `Online` when `onAvailable`, emit `Offline` when `onLost`/`onUnavailable`
  - [x] 2.4 Emit initial state immediately: query `connectivityManager.activeNetwork` / `getNetworkCapabilities()` on subscribe, emit `Online`/`Offline` before registering callback
  - [x] 2.5 Cancel callback on `awaitClose { connectivityManager.unregisterNetworkCallback(callback) }`
  - [ ] 2.6 Use `shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)` so state survives brief unsubscription — DEFERRED: not needed until Story 2.5; current usage is single `.first()` reads only

- [x] Task 3: Create iOS `ConnectivityMonitor` actual (AC: #3)
  - [x] 3.1 Create `iosMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.ios.kt`
  - [x] 3.2 `actual class ConnectivityMonitor()` — no constructor args needed on iOS
  - [x] 3.3 Implement `observe()` using `callbackFlow { }` with `NWPathMonitor()` — emit `Online` when `path.status == .satisfied`, else `Offline`
  - [x] 3.4 Start monitor on dispatch queue, cancel on `awaitClose { nw_path_monitor_cancel(monitor) }` — NOTE: uses global queue due to KMP cinterop type mismatch (`dispatch_queue_create` returns `CPointer` but `nw_path_monitor_set_queue` expects `NSObject?`); NWPathMonitor manages its own thread safety regardless of queue type
  - [ ] 3.5 Use `shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)` same as Android — DEFERRED: same as 2.6

- [x] Task 4: Create `RetryHelper` (AC: #4)
  - [x] 4.1 Create `commonMain/kotlin/com/areadiscovery/util/RetryHelper.kt`
  - [x] 4.2 Implement as top-level suspend function (not object/class — no state needed):
    ```kotlin
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        block: suspend () -> T
    ): Result<T>
    ```
  - [x] 4.3 Retry logic: attempt 1 (no delay), attempt 2 (delay = `initialDelayMs * 2^0` = 1000ms), attempt 3 (delay = `initialDelayMs * 2^1` = 2000ms) — cap each delay at `maxDelayMs`
  - [x] 4.4 **CRITICAL**: rethrow `CancellationException` immediately — never catch it in retry loop
  - [x] 4.5 On all attempts exhausted: return `Result.failure(lastException)`
  - [x] 4.6 On success: return `Result.success(value)` immediately without retrying
  - [x] 4.7 Use `kotlinx.coroutines.delay()` — NOT `Thread.sleep()`

- [x] Task 5: Refactor `GeminiAreaIntelligenceProvider` to use `RetryHelper` (AC: #5)
  - [x] 5.1 Open `data/remote/GeminiAreaIntelligenceProvider.kt`
  - [x] 5.2 Replace the existing inline `for (attempt in 0 until MAX_RETRY_ATTEMPTS)` retry loop in `streamAreaPortrait()` with `RetryHelper.withRetry()` wrapping the single SSE attempt
  - [x] 5.3 Remove `RETRY_DELAYS_MS` array constant — delays now owned by `RetryHelper`
  - [x] 5.4 Keep `MAX_RETRY_ATTEMPTS = 3` constant for clarity (pass to `withRetry(maxAttempts = MAX_RETRY_ATTEMPTS)`)
  - [x] 5.5 Keep `isRetryableError()` function — pass a custom predicate to `withRetry` or re-check after `Result.failure`
  - [x] 5.6 Keep `mapToDomainErrorException()` unchanged — still needed to map raw exceptions to `DomainError`
  - [x] 5.7 Verify `hasEmitted` guard still works: if partial data has been emitted, do NOT retry (same behavior as before)
  - [x] 5.8 Run `./gradlew allTests` — confirm all existing `GeminiAreaIntelligenceProviderTest` tests pass

- [x] Task 6: Add `ContentAvailabilityNote` to `BucketUpdate` sealed class (AC: #6)
  - [x] 6.1 Open `commonMain/kotlin/com/areadiscovery/domain/model/BucketUpdate.kt`
  - [x] 6.2 Add: `data class ContentAvailabilityNote(val message: String) : BucketUpdate()`
  - [x] 6.3 Check all existing `when (update)` exhaustive expressions in codebase — add `is BucketUpdate.ContentAvailabilityNote -> { /* ignore for now */ }` branches where needed to avoid compile errors
  - [x] 6.4 Specifically check: `SummaryViewModel.kt`, `GeminiResponseParser.kt`, any test files with `when (BucketUpdate)` — add no-op branches

- [x] Task 7: Update `AreaRepositoryImpl` with connectivity-aware fallback logic (AC: #7, #8, #9)
  - [x] 7.1 Add `ConnectivityMonitor` as a constructor parameter: `class AreaRepositoryImpl(aiProvider, database, scope, clock, connectivityMonitor: ConnectivityMonitor, ioDispatcher)`
  - [x] 7.2 In `getAreaPortrait()`, before attempting AI call (i.e., in the "cache miss" and "stale-revalidate" paths):
    - [x] 7.2a Call `connectivityMonitor.observe().first()` to get current connectivity state
    - [x] 7.2b If `Offline`: skip AI call entirely, emit whatever cached buckets exist + `ContentAvailabilityNote("You're offline — showing last known content")`; if no cache: emit `ContentAvailabilityNote("No content available offline for this area")` + `PortraitComplete(pois = emptyList())`
  - [x] 7.3 Wrap the AI provider call in a try/catch — currently exceptions propagate; catch `DomainErrorException` and other non-cancellation exceptions:
    - [x] 7.3a In the "cache miss" path: if AI call throws, check if `staleBuckets.isNotEmpty()` (from any TTL); if yes, emit stale buckets + `ContentAvailabilityNote` + `PortraitComplete`; if no cache at all, emit `ContentAvailabilityNote("Could not load area content — please try again")` + `PortraitComplete(pois = emptyList())`
    - [x] 7.3b Stale-revalidate background refresh failure is silent (background job) — do not surface errors from background refresh
  - [x] 7.4 **CRITICAL**: Always rethrow `CancellationException` — never swallow it in catch blocks
  - [x] 7.5 Update `DataModule.kt` Koin binding for `AreaRepositoryImpl` to include `ConnectivityMonitor`: `single<AreaRepository> { AreaRepositoryImpl(get(), get(), get(named("appScope")), get(), get(), Dispatchers.IO) }`

- [x] Task 8: Wire `ConnectivityMonitor` in platform DI (AC: #1, #2, #3)
  - [x] 8.1 In `PlatformModule.android.kt`: add `single { ConnectivityMonitor(androidContext()) }`
  - [x] 8.2 In `PlatformModule.ios.kt`: add `single { ConnectivityMonitor() }`
  - [x] 8.3 Add required imports in both platform modules
  - [x] 8.4 Run `./gradlew assembleDebug` to confirm Koin graph resolves — fix any missing binding errors

- [x] Task 9: Create `FakeConnectivityMonitor` (AC: #10)
  - [x] 9.1 Create `commonTest/kotlin/com/areadiscovery/fakes/FakeConnectivityMonitor.kt`
  - [x] 9.2 Implement as `class FakeConnectivityMonitor(initialState: ConnectivityState = ConnectivityState.Online)`
  - [x] 9.3 Use `MutableStateFlow<ConnectivityState>` internally — expose `fun setState(state: ConnectivityState)` for test control
  - [x] 9.4 Implement `observe(): Flow<ConnectivityState>` as `stateFlow.asStateFlow()`
  - [x] 9.5 Note: `FakeConnectivityMonitor` is NOT an `expect class` — it's a regular test-only class that has the same `observe()` API; pass it directly to `AreaRepositoryImpl` constructor in tests

- [x] Task 10: Write unit tests for `RetryHelper` (AC: #11)
  - [x] 10.1 Create `commonTest/kotlin/com/areadiscovery/util/RetryHelperTest.kt`
  - [x] 10.2 **Success on first attempt**: `withRetry { "result" }` returns `Result.success("result")`, no delay, block called once
  - [x] 10.3 **Success after retry**: block fails once then succeeds, result is `Result.success`, block called twice
  - [x] 10.4 **All attempts exhausted**: block always throws, result is `Result.failure` with last exception, block called exactly `maxAttempts` times
  - [x] 10.5 **Delay capping**: with `initialDelayMs = 5000, maxDelayMs = 6000`, second retry delay is `min(10000, 6000) = 6000` — use `TestScope` and `advanceUntilIdle()` to control time
  - [x] 10.6 **CancellationException not caught**: verify that `CancellationException` escapes immediately (use `assertFailsWith<CancellationException>`)
  - [x] 10.7 Use `kotlinx.coroutines.test.runTest` + `TestScope` for all coroutine tests

- [x] Task 11: Write repository resilience tests (AC: #12)
  - [x] 11.1 Create or extend test file — prefer `androidUnitTest` (same location as `AreaRepositoryTest.kt`) since `JdbcSqliteDriver` is JVM-only
  - [x] 11.2 **Offline path with cache**: Set `FakeConnectivityMonitor` to `Offline`, pre-populate cache; call `getAreaPortrait()`; verify: AI provider NOT called, stale buckets emitted, `ContentAvailabilityNote` emitted, `PortraitComplete` emitted
  - [x] 11.3 **Offline path no cache**: Set `FakeConnectivityMonitor` to `Offline`, empty DB; verify: AI NOT called, `ContentAvailabilityNote` emitted, `PortraitComplete` emitted
  - [x] 11.4 **AI failure with cache**: Set monitor to `Online`, pre-populate stale cache, `FakeAreaIntelligenceProvider.responseFlow` throws `DomainErrorException`; verify: stale buckets emitted, `ContentAvailabilityNote` emitted, `PortraitComplete` emitted
  - [x] 11.5 **AI failure no cache**: Set monitor to `Online`, empty DB, provider throws; verify: `ContentAvailabilityNote` emitted, `PortraitComplete` emitted, no exception propagated to collector
  - [x] 11.6 **Online path normal**: `FakeConnectivityMonitor.Online`, cache miss, provider succeeds; verify normal stream path unchanged (regression guard)
  - [x] 11.7 Use `Turbine` for all `Flow` assertions: `.test { awaitItem() ... awaitComplete() }`

- [x] Task 12: Build gates (AC: #13)
  - [x] 12.1 `./gradlew assembleDebug` — PASS
  - [x] 12.2 `./gradlew allTests` — PASS
  - [x] 12.3 `./gradlew lint` — PASS

## Dev Notes

### ConnectivityMonitor: expect/actual Pattern

Follow the `DatabaseDriverFactory` pattern exactly (`expect class`, not interface). The reason: Android requires `Context` in the constructor; iOS doesn't. An interface can't express different constructor signatures in KMP.

**commonMain expect:**
```kotlin
// commonMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.kt
package com.areadiscovery.util

import com.areadiscovery.domain.model.ConnectivityState
import kotlinx.coroutines.flow.Flow

expect class ConnectivityMonitor {
    fun observe(): Flow<ConnectivityState>
}
```

**Android actual:**
```kotlin
// androidMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.android.kt
package com.areadiscovery.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.areadiscovery.domain.model.ConnectivityState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

actual class ConnectivityMonitor(private val context: Context) {
    actual fun observe(): Flow<ConnectivityState> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Emit initial state synchronously
        trySend(currentState(cm))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(ConnectivityState.Online) }
            override fun onLost(network: Network) { trySend(ConnectivityState.Offline) }
            override fun onUnavailable() { trySend(ConnectivityState.Offline) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun currentState(cm: ConnectivityManager): ConnectivityState {
        val network = cm.activeNetwork ?: return ConnectivityState.Offline
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectivityState.Offline
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectivityState.Online
        } else {
            ConnectivityState.Offline
        }
    }
}
```

**iOS actual:**
```kotlin
// iosMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.ios.kt
package com.areadiscovery.util

import com.areadiscovery.domain.model.ConnectivityState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.NWPathMonitor
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.DISPATCH_QUEUE_SERIAL

actual class ConnectivityMonitor {
    actual fun observe(): Flow<ConnectivityState> = callbackFlow {
        val monitor = NWPathMonitor()
        val queue = dispatch_queue_create("ConnectivityMonitor", DISPATCH_QUEUE_SERIAL)

        monitor.setUpdateHandler { path ->
            val state = if (path.status == nw_path_status_satisfied) {
                ConnectivityState.Online
            } else {
                ConnectivityState.Offline
            }
            trySend(state)
        }
        monitor.startWithQueue(queue)

        awaitClose { monitor.cancel() }
    }.distinctUntilChanged()
}
```

**IMPORTANT:** The `shareIn` wrapping for long-lived sharing is optional for Story 2.4. `AreaRepositoryImpl` calls `observe().first()` (single read) — no sharing needed yet. Story 2.5 may add sharing when ViewModels observe connectivity.

### RetryHelper Implementation

```kotlin
// commonMain/kotlin/com/areadiscovery/util/RetryHelper.kt
package com.areadiscovery.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 10000,
    block: suspend () -> T
): Result<T> {
    var lastError: Throwable? = null
    var delayMs = initialDelayMs

    for (attempt in 0 until maxAttempts) {
        if (attempt > 0) {
            delay(delayMs.coerceAtMost(maxDelayMs))
            delayMs *= 2
        }
        try {
            return Result.success(block())
        } catch (e: CancellationException) {
            throw e  // NEVER swallow CancellationException
        } catch (e: Throwable) {
            lastError = e
            AppLogger.d { "RetryHelper: attempt ${attempt + 1} failed — ${e.message}" }
        }
    }

    return Result.failure(lastError ?: Exception("withRetry: unknown failure after $maxAttempts attempts"))
}
```

**Backoff schedule** (with defaults):
| Attempt | Delay before attempt |
|---------|---------------------|
| 1st     | 0ms (no delay)      |
| 2nd     | 1000ms              |
| 3rd     | 2000ms              |
| 4th+    | 4000ms, 8000ms, capped at 10000ms |

### GeminiAreaIntelligenceProvider Refactor

The existing inline retry (`for (attempt in 0 until MAX_RETRY_ATTEMPTS)`) must be replaced. Key constraint: **if data has already been partially emitted (`hasEmitted = true`), do NOT retry** — the collector's state is already corrupted by partial data.

```kotlin
override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
    val apiKey = apiKeyProvider.geminiApiKey
    if (apiKey.isBlank()) {
        throw DomainErrorException(DomainError.ApiError(0, "Gemini API key not configured"))
    }

    AppLogger.d { "GeminiAreaIntelligenceProvider: streaming portrait for '$areaName'" }
    val prompt = promptBuilder.buildAreaPortraitPrompt(areaName, context)
    val requestBody = buildRequestBody(prompt)
    var hasEmitted = false

    val result = withRetry(maxAttempts = MAX_RETRY_ATTEMPTS) {
        if (hasEmitted) throw DomainErrorException(DomainError.NetworkError("Partial data already emitted"))
        performSseStream(requestBody) { update ->
            hasEmitted = true
            emit(update)
        }
    }

    if (result.isFailure) {
        val error = result.exceptionOrNull() ?: Exception("Unknown error")
        throw if (error is DomainErrorException) error else mapToDomainErrorException(error as Exception)
    }
}
```

**IMPORTANT:** The refactor must preserve the `isRetryableError()` check. Pass it as a predicate or check after failure: if `result.isFailure && !isRetryableError(result.exceptionOrNull())` → throw immediately without retrying. You may need to extend `withRetry` with an `isRetryable: (Throwable) -> Boolean = { true }` parameter, OR keep the retry in the provider with `withRetry` used only for the delay logic. Either approach is valid — choose the cleaner one.

The simplest working approach: Keep the `withRetry` signature as-is, and inside the block, check `isRetryableError` before throwing:
```kotlin
val result = withRetry(maxAttempts = MAX_RETRY_ATTEMPTS) {
    try {
        performSseStream(...)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!isRetryableError(e) || hasEmitted) throw mapToDomainErrorException(e)
        throw e  // retryable — let withRetry catch and retry
    }
}
```

### AreaRepositoryImpl Resilience Changes

Add `ConnectivityMonitor` to the constructor. The updated signature:
```kotlin
class AreaRepositoryImpl(
    private val aiProvider: AreaIntelligenceProvider,
    private val database: AreaDiscoveryDatabase,
    private val scope: CoroutineScope,
    private val clock: AppClock = SystemClock(),
    private val connectivityMonitor: ConnectivityMonitor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AreaRepository
```

**Logic flow in `getAreaPortrait()`:**

```
1. Load cached buckets for (areaName, language)
2. Separate validBuckets (not expired) vs staleBuckets (expired)

IF validBuckets.size == 6:
   → Full cache hit → emit from cache → return (unchanged from Story 2.3)

CHECK connectivity:
   connectivity = connectivityMonitor.observe().first()

IF connectivity == Offline:
   → emit all cached buckets (valid + stale, best we have)
   → emit ContentAvailabilityNote("You're offline — showing last known content")
   → emit PortraitComplete(pois = emptyList())
   → return

IF staleBuckets.isNotEmpty() (and online):
   → emit stale buckets immediately (unchanged from Story 2.3)
   → launch background refresh (unchanged) — failure is silent
   → emit PortraitComplete(pois = emptyList())
   → return

// Cache miss path — attempt AI call with error catching:
try {
    aiProvider.streamAreaPortrait(areaName, context).collect { update ->
        emit(update)
        if (update is BucketUpdate.BucketComplete) writeToCache(update.content, areaName, language)
    }
} catch (e: CancellationException) {
    throw e  // Always propagate
} catch (e: Exception) {
    AppLogger.e(e) { "AI provider failed — falling back to cache" }
    val allCached = database.area_bucket_cacheQueries.getBucketsByAreaAndLanguage(areaName, language).executeAsList()
    if (allCached.isNotEmpty()) {
        allCached.forEach { emit(BucketUpdate.BucketComplete(it.toBucketContent())) }
        emit(BucketUpdate.ContentAvailabilityNote("Content from cache — may not be current"))
    } else {
        emit(BucketUpdate.ContentAvailabilityNote("Could not load area content — please try again"))
    }
    emit(BucketUpdate.PortraitComplete(pois = emptyList()))
}
```

**Koin DI update:**
```kotlin
// DataModule.kt — update AreaRepositoryImpl binding:
single<AreaRepository> {
    AreaRepositoryImpl(
        aiProvider = get(),
        database = get(),
        scope = get(named("appScope")),
        clock = get(),
        connectivityMonitor = get()
    )
}
```
Note: `ConnectivityMonitor` is bound in the platform module (`platformModule()`) — Koin resolves across all modules.

### BucketUpdate Change — Exhaustive `when` Audit

After adding `ContentAvailabilityNote`, search for all exhaustive `when` on `BucketUpdate`:

```bash
grep -rn "when (update\|when (it\|when (bucketUpdate" composeApp/src/ | grep -v "//\|test"
```

Expected locations requiring `is BucketUpdate.ContentAvailabilityNote -> {}` no-op:
- `SummaryViewModel.kt` — ViewModel's `collect` handler
- Any test that uses `when` on `BucketUpdate`

Do NOT add UI rendering logic for `ContentAvailabilityNote` in this story — Story 2.5 handles the UI layer.

### FakeConnectivityMonitor Pattern

```kotlin
// commonTest/fakes/FakeConnectivityMonitor.kt
package com.areadiscovery.fakes

import com.areadiscovery.domain.model.ConnectivityState
import com.areadiscovery.util.ConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Note: Not an expect/actual — just a test double with same observe() API
class FakeConnectivityMonitor(
    initialState: ConnectivityState = ConnectivityState.Online
) {
    private val _state = MutableStateFlow(initialState)

    fun observe(): Flow<ConnectivityState> = _state.asStateFlow()

    fun setState(state: ConnectivityState) {
        _state.value = state
    }
}
```

**CRITICAL:** `FakeConnectivityMonitor` is NOT a `ConnectivityMonitor` (the expect class) — it's a separate class used only in test code. Pass it to `AreaRepositoryImpl` via a secondary constructor or refactor to accept a `() -> Flow<ConnectivityState>` lambda instead.

**Recommended approach**: Extract the connectivity check to a lambda in the constructor to avoid the `expect class` vs test fake issue:
```kotlin
class AreaRepositoryImpl(
    // ...
    private val connectivityObserver: () -> Flow<ConnectivityState> = { ConnectivityMonitor().observe() }
) : AreaRepository
```

In production (Koin): `single<AreaRepository> { AreaRepositoryImpl(..., connectivityObserver = { get<ConnectivityMonitor>().observe() }) }`
In tests: `AreaRepositoryImpl(..., connectivityObserver = { fakeMonitor.observe() })`

**Alternative**: Make `ConnectivityMonitor` an interface for testability, with `expect class` being the production `ConnectivityMonitor` implementing it, and `FakeConnectivityMonitor` implementing the interface. Either approach is valid — choose the cleanest for the codebase.

### Testing Notes

**RetryHelper tests use `UnconfinedTestDispatcher`:**
```kotlin
@Test
fun `retries with exponential backoff`() = runTest {
    var attempts = 0
    val result = withRetry(maxAttempts = 3, initialDelayMs = 1000) {
        attempts++
        if (attempts < 3) throw RuntimeException("fail")
        "success"
    }
    assertEquals(Result.success("success"), result)
    assertEquals(3, attempts)
    // Time advanced automatically by runTest with virtual clock
}
```

**Repository tests:**
- Extend existing `AreaRepositoryTest` in `androidUnitTest/` or create a new test file in the same source set
- Use `FakeConnectivityMonitor` passed as lambda: `AreaRepositoryImpl(..., connectivityObserver = { fakeMonitor.observe() })`
- Use existing `FakeAreaIntelligenceProvider` with `responseFlow = flow { throw DomainErrorException(...) }` for AI failure tests
- Use `Turbine` for Flow assertions

### Project Structure Notes

**New files:**
```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── domain/model/
│   └── ConnectivityState.kt                     (Online / Offline sealed variants)
└── util/
    ├── ConnectivityMonitor.kt                   (expect class)
    └── RetryHelper.kt                           (withRetry top-level suspend fun)

composeApp/src/androidMain/kotlin/com/areadiscovery/util/
└── ConnectivityMonitor.android.kt               (actual: ConnectivityManager)

composeApp/src/iosMain/kotlin/com/areadiscovery/util/
└── ConnectivityMonitor.ios.kt                   (actual: NWPathMonitor)

composeApp/src/commonTest/kotlin/com/areadiscovery/
├── fakes/
│   └── FakeConnectivityMonitor.kt
└── util/
    └── RetryHelperTest.kt
```

**Modified files:**
```
composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/BucketUpdate.kt
    — add ContentAvailabilityNote(message: String) variant

composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt
    — add ConnectivityMonitor param, offline/fallback logic

composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt
    — refactor inline retry to use withRetry()

composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt
    — update AreaRepositoryImpl binding to pass ConnectivityMonitor

composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt
    — add single { ConnectivityMonitor(androidContext()) }

composeApp/src/iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt
    — add single { ConnectivityMonitor() }

composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryViewModel.kt
    — add no-op branch for ContentAvailabilityNote in when() expression (if exhaustive)

composeApp/src/androidUnitTest/kotlin/com/areadiscovery/data/repository/AreaRepositoryTest.kt
    — extend with resilience tests (or create new file in same source set)
```

**Do NOT modify:**
- `AreaRepository.kt` interface — signature unchanged
- `GeminiPromptBuilder.kt`, `GeminiResponseParser.kt` — not touched
- Any SQLDelight schema files — no new tables needed
- `AppClock.kt`, `AppLogger.kt` — stable utilities

### Previous Story Intelligence (Story 2.3)

From Story 2.3 completion notes:
- `AreaRepositoryImpl` uses injectable `AppClock` (not `System.currentTimeMillis()`) — follow same injectable pattern for `ConnectivityMonitor`
- SQLDelight accessor is `area_bucket_cacheQueries` (snake_case, not camelCase) — already correct in existing code
- `TestScope.testScheduler.advanceUntilIdle()` needed for background `scope.launch` — use same in stale-revalidate tests
- `JdbcSqliteDriver` is JVM-only → repository tests live in `androidUnitTest/`, not `commonTest/`
- `FakeAreaIntelligenceProvider` already has `callCount` and configurable `responseFlow` — leverage these for AI failure tests

Story 2.3 debug log insight:
- `sqldelight-sqlite-driver` is JVM-only — if any new tests need SQLDelight in-memory DB, they must go in `androidUnitTest/`

### Git Intelligence

Recent commits follow this pattern:
- `94d7689 Add area repository with SQLDelight caching (Story 2.3)` — initial
- `1df5444 Address code review findings for Story 2.3 (3H, 3M, 1L)` — first review
- `4cbe587 Address round 4 review findings for Story 2.3 (4L)` — fourth review

Plan for 2-3 review rounds after initial implementation. Commit format:
- Initial: `Add connectivity monitor and error resilience (Story 2.4)`
- Reviews: `Address code review findings for Story 2.4 (XH, YM, ZL)`

### Android Manifest Requirement

**IMPORTANT:** `AndroidConnectivityMonitor` requires `ACCESS_NETWORK_STATE` permission. Check `composeApp/src/androidMain/AndroidManifest.xml`:
- It may already be present from earlier network usage (Ktor requires it)
- If missing, add: `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`
- This is a normal (non-dangerous) permission — no runtime request needed

### NFR Compliance Summary

| NFR | Requirement | Implementation |
|-----|-------------|----------------|
| NFR8 | Offline detection within 1s | `ConnectivityManager.NetworkCallback` is instantaneous — no polling |
| NFR22 | AI downtime → cached content, not crash | `AreaRepositoryImpl` try/catch with cache fallback |
| NFR23 | News API failure → WHATS_HAPPENING uses AI knowledge | Handled by `GeminiAreaIntelligenceProvider` prompt design; no separate News API in Phase 1a |
| NFR25 | 10s timeout, max 3 retries, no cascade | `RetryHelper` + `HttpClientFactory` timeout (already 10s from Story 2.2) |

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Epic 2, Story 2.4, NFR8, NFR22, NFR23, NFR25]
- [Source: _bmad-output/planning-artifacts/architecture.md — Error Handling Strategy, Retry Pattern, ConnectivityMonitor pattern, Project Directory Structure]
- [Source: _bmad-output/implementation-artifacts/2-3-area-repository-and-caching-foundation.md — AreaRepositoryImpl patterns, AppClock injection, testing approach]
- [Source: composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt — Existing retry logic, error mapping]
- [Source: composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt — Platform DI pattern]
- [Android ConnectivityManager docs](https://developer.android.com/training/monitoring-device-state/connectivity-status-type)
- [Kotlin callbackFlow docs](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- iOS `NWPathMonitor` class-based API (`platform.Network.NWPathMonitor`) not available in KMP; used C-function equivalents (`nw_path_monitor_create`, `nw_path_monitor_set_update_handler`, etc.)
- `dispatch_queue_create` returns C pointer incompatible with `nw_path_monitor_set_queue` NSObject param; used `dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT)` instead
- `AreaRepositoryImpl` refactored to accept `connectivityObserver: () -> Flow<ConnectivityState>` lambda instead of `ConnectivityMonitor` directly — enables testability without fighting `expect class` constraints

### Completion Notes List

- Created `ConnectivityState` sealed class (Online/Offline) in domain model
- Created `ConnectivityMonitor` expect/actual: Android uses `ConnectivityManager.NetworkCallback`, iOS uses `nw_path_monitor` C API
- Added `ACCESS_NETWORK_STATE` permission to AndroidManifest
- Created `RetryHelper` (`withRetry`) with exponential backoff, CancellationException propagation, delay capping
- Refactored `GeminiAreaIntelligenceProvider` to use `withRetry()` — removed inline retry loop and `RETRY_DELAYS_MS` constant, preserved `isRetryableError()` and `hasEmitted` guard
- Added `ContentAvailabilityNote(message)` variant to `BucketUpdate` sealed class
- Added no-op branches in `SummaryStateMapper`, `DomainModelTest`, `MockAreaIntelligenceProviderTest` for exhaustive `when`
- Updated `AreaRepositoryImpl` with connectivity-aware fallback: offline check before AI call, try/catch on cache-miss path with fallback to cached content
- Used `connectivityObserver: () -> Flow<ConnectivityState>` lambda pattern for testability
- Wired `ConnectivityMonitor` in platform DI modules (Android + iOS) and `DataModule`
- Created `FakeConnectivityMonitor` for tests with `MutableStateFlow`-based state control
- 7 `RetryHelper` tests: success first attempt, success after retry, all exhausted, delay capping, CancellationException propagation, single attempt success/failure
- 5 repository resilience tests: offline+cache, offline+no-cache, AI-failure+partial-cache, AI-failure+no-cache, online-normal regression guard
- All 3 build gates pass: `assembleDebug`, `allTests`, `lint`
- NOTE for Story 2.5: `SummaryStateMapper` ignores `ContentAvailabilityNote` (returns currentState). `SummaryUiState.Streaming`/`Complete` have no `availabilityNote: String?` field — Story 2.5 will need a state model change, not just a mapper change

### Change Log

- 2026-03-04: Add connectivity monitor and error resilience (Story 2.4)
- 2026-03-04: Address code review findings for Story 2.4 (3H, 3M, 3L)

### File List

**New files:**
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/ConnectivityState.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/util/RetryHelper.kt
- composeApp/src/androidMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.android.kt
- composeApp/src/iosMain/kotlin/com/areadiscovery/util/ConnectivityMonitor.ios.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeConnectivityMonitor.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/util/RetryHelperTest.kt

**Modified files:**
- _bmad-output/implementation-artifacts/sprint-status.yaml
- composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/BucketUpdate.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryStateMapper.kt
- composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt
- composeApp/src/androidMain/AndroidManifest.xml
- composeApp/src/iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt
- composeApp/src/androidUnitTest/kotlin/com/areadiscovery/data/repository/AreaRepositoryTest.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/MockAreaIntelligenceProviderTest.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/domain/model/DomainModelTest.kt
