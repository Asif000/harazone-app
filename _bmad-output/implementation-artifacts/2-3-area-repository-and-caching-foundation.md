# Story 2.3: Area Repository & Caching Foundation

Status: done

## Story

As a **user**,
I want area summaries to load instantly when I revisit an area, and refresh in the background if they're stale,
so that I always see content immediately and it stays fresh.

## Acceptance Criteria

1. `AreaRepository` interface defined in `domain/repository/` with `fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>`
2. `AreaRepositoryImpl` in `data/repository/` orchestrates cache and `AreaIntelligenceProvider`
3. Repository checks `area_bucket_cache` SQLDelight table with composite key `(area_name, bucket_type, language)`:
   - **Cache hit (valid TTL):** returns cached `Flow<BucketUpdate>` immediately — target <500ms [NFR3]
   - **Cache stale (past TTL):** emits stale cached content immediately **and** launches background coroutine to refresh; UI gets instant content, background updates cache silently
   - **Cache miss:** streams from `AreaIntelligenceProvider`, writes each completed bucket to cache as it arrives
4. TTL tier constants enforced (never magic numbers):
   - `HISTORY`, `CHARACTER` → **14 days**
   - `COST`, `NEARBY` → **3 days**
   - `SAFETY`, `WHATS_HAPPENING` → **12 hours**
5. Cache key includes `language` — changing `AreaContext.preferredLanguage` forces fresh fetch (not stale content)
6. SQLDelight schema file `area_bucket_cache.sq` defines table with columns: `area_name TEXT NOT NULL`, `bucket_type TEXT NOT NULL`, `language TEXT NOT NULL`, `highlight TEXT NOT NULL`, `content TEXT NOT NULL`, `confidence TEXT NOT NULL`, `sources_json TEXT NOT NULL`, `expires_at INTEGER NOT NULL`, `created_at INTEGER NOT NULL`, `PRIMARY KEY (area_name, bucket_type, language)`
7. `DatabaseDriverFactory` (expect/actual) provides `SqlDriver` per platform — Android uses `AndroidSqliteDriver`, iOS uses `NativeSqliteDriver`; wired via Koin in platform-specific DI modules
8. `GetAreaPortraitUseCase` in `domain/usecase/` wraps `AreaRepository.getAreaPortrait()` with `operator fun invoke()`
9. `FakeAreaIntelligenceProvider` in `commonTest/fakes/` for test doubles (no MockK — see Testing section)
10. Unit tests in `commonTest` cover all three cache paths: hit, stale-revalidate, and miss — verifying correct emissions and cache write-through
11. `DataModule.kt` updated to wire `AreaRepository`, `AreaDiscoveryDatabase`, and `DatabaseDriverFactory`
12. All 3 build gates pass: `./gradlew assembleDebug`, `./gradlew allTests`, `./gradlew lint`

## Tasks / Subtasks

- [x] Task 1: Create SQLDelight schema (AC: #6)
  - [x] 1.1 Create directory `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/`
  - [x] 1.2 Create `area_bucket_cache.sq` with `CREATE TABLE`, insert, select-by-key, select-all-by-area, and delete-expired queries
  - [x] 1.3 Confirm `./gradlew generateCommonMainAreaDiscoveryDatabaseInterface` generates `AreaDiscoveryDatabase` cleanly
  - [x] 1.4 Add an index: `CREATE INDEX IF NOT EXISTS idx_area_bucket_cache_expires_at ON area_bucket_cache(expires_at)` for TTL cleanup queries

- [x] Task 2: Create platform `DatabaseDriverFactory` (AC: #7)
  - [x] 2.1 Create `commonMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.kt` — `expect class DatabaseDriverFactory { fun createDriver(): SqlDriver }`
  - [x] 2.2 Create `androidMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.android.kt` — `actual class DatabaseDriverFactory(private val context: Context)` using `AndroidSqliteDriver(AreaDiscoveryDatabase.Schema, context, "area_discovery.db")`
  - [x] 2.3 Create `iosMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.ios.kt` — `actual class DatabaseDriverFactory()` using `NativeSqliteDriver(AreaDiscoveryDatabase.Schema, "area_discovery.db")`
  - [x] 2.4 Wire in `PlatformModule.android.kt`: `single { DatabaseDriverFactory(androidContext()) }` and in `PlatformModule.ios.kt`: `single { DatabaseDriverFactory() }`

- [x] Task 3: Create `AreaRepository` interface (AC: #1)
  - [x] 3.1 Create `domain/repository/AreaRepository.kt` — interface with `fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>`

- [x] Task 4: Create `AreaRepositoryImpl` with caching logic (AC: #2, #3, #4, #5)
  - [x] 4.1 Create `data/repository/AreaRepositoryImpl.kt` implementing `AreaRepository` — constructor: `(aiProvider: AreaIntelligenceProvider, database: AreaDiscoveryDatabase, scope: CoroutineScope, clock: AppClock = SystemClock())`
  - [x] 4.2 Define TTL constants in companion object: `CACHE_TTL_STATIC_MS`, `CACHE_TTL_SEMI_STATIC_MS`, `CACHE_TTL_DYNAMIC_MS` (computed from days/hours in milliseconds)
  - [x] 4.3 Define `fun getTtlMs(bucketType: BucketType): Long` mapping each `BucketType` to its tier TTL
  - [x] 4.4 Implement `getAreaPortrait()`:
    - Query all cached buckets for `(areaName, language)` from SQLDelight
    - For each bucket: check `expires_at` vs `currentTimeMillis()`
    - If all 6 buckets are valid → return flow of `BucketComplete` events from cache + `PortraitComplete`
    - If any bucket stale → emit stale buckets immediately via `flow { }`, then `launch` background coroutine to refresh (see stale-revalidate detail below)
    - If cache miss → stream from `AreaIntelligenceProvider`, write each `BucketComplete` to cache as it arrives
  - [x] 4.5 Implement stale-while-revalidate: `launch(coroutineScope) { freshStream.collect { if (it is BucketComplete) writeToCache(it, areaName, language) } }` — UI is NOT awaiting this coroutine
  - [x] 4.6 Implement `writeToCache(bucket: BucketContent, areaName: String, language: String)` — serializes `sources` to JSON string, computes `expires_at = currentTimeMillis() + getTtlMs(bucket.type)`, inserts or replaces row
  - [x] 4.7 Ensure `sources_json` serialized with `kotlinx.serialization` (JSON encode `List<Source>` to string, decode on read)

- [x] Task 5: Create `GetAreaPortraitUseCase` (AC: #8)
  - [x] 5.1 Create `domain/usecase/GetAreaPortraitUseCase.kt` — `class GetAreaPortraitUseCase(private val repository: AreaRepository)` with `operator fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> = repository.getAreaPortrait(areaName, context)`

- [x] Task 6: Create `FakeAreaIntelligenceProvider` test double (AC: #9)
  - [x] 6.1 Create `commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt` — implements `AreaIntelligenceProvider`, exposes configurable `Flow<BucketUpdate>` via `var responseFlow`
  - [x] 6.2 Default behavior: emits `ContentDelta` + `BucketComplete` for all 6 buckets + `PortraitComplete`

- [x] Task 7: Write unit tests for all cache paths (AC: #10)
  - [x] 7.1 Create `androidUnitTest/kotlin/com/areadiscovery/data/repository/AreaRepositoryTest.kt` (moved from commonTest — JdbcSqliteDriver is JVM-only)
  - [x] 7.2 **Cache hit test:** Pre-populate DB with all 6 valid buckets → call `getAreaPortrait()` → verify `FakeAreaIntelligenceProvider` was NOT called, all 6 `BucketComplete` events emitted from cache
  - [x] 7.3 **Stale-revalidate test:** Pre-populate DB with expired buckets → call `getAreaPortrait()` → verify stale `BucketComplete` events emitted immediately, AND background refresh triggered (`FakeAreaIntelligenceProvider` called)
  - [x] 7.4 **Cache miss test:** Empty DB → call `getAreaPortrait()` → verify `FakeAreaIntelligenceProvider` called, all bucket updates pass through, buckets written to DB after stream ends
  - [x] 7.5 **Language-switch test:** Cache populated for `language=en` → request with `language=fr` → verify treated as cache miss
  - [x] 7.6 Use in-memory SQLDelight driver for tests: `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` — call `AreaDiscoveryDatabase.Schema.create(driver)` in test setup
  - [x] 7.7 Use Turbine for Flow assertions: `getAreaPortrait(...).test { ... }`

- [x] Task 8: Update DI wiring (AC: #11)
  - [x] 8.1 In `DataModule.kt`: add `single { AreaDiscoveryDatabase(get<DatabaseDriverFactory>().createDriver()) }`
  - [x] 8.2 In `DataModule.kt`: add `single<AreaRepository> { AreaRepositoryImpl(get(), get(), get()) }` (passes `AreaIntelligenceProvider`, `AreaDiscoveryDatabase`, and app `CoroutineScope`)
  - [x] 8.3 In `DataModule.kt`: added `CoroutineScope`, `AppClock`, and `GetAreaPortraitUseCase` bindings
  - [x] 8.4 Verify Koin graph resolves: `./gradlew assembleDebug` must pass

- [x] Task 9: Build gates (AC: #12)
  - [x] 9.1 `./gradlew assembleDebug` — PASS
  - [x] 9.2 `./gradlew allTests` — PASS
  - [x] 9.3 `./gradlew lint` — PASS

## Dev Notes

### SQLDelight Configuration (Already Wired — No Changes Needed to Build Files)

The project already has SQLDelight fully configured:

```toml
# gradle/libs.versions.toml
sqldelight = "2.2.1"
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
```

```kotlin
// composeApp/build.gradle.kts — already present
sqldelight {
    databases {
        create("AreaDiscoveryDatabase") {
            packageName.set("com.areadiscovery.data.local")
        }
    }
}
// Deps already declared: sqldelight.coroutines (common), sqldelight.android.driver, sqldelight.native.driver
```

**Generated code location:** `build/generated/sqldelight/code/AreaDiscoveryDatabase/commonMain/`
**Generated package:** `com.areadiscovery.data.local`
**Class name:** `AreaDiscoveryDatabase`

**For unit tests, add this to `commonTest` dependencies** if not already present:
```toml
# gradle/libs.versions.toml
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
```
```kotlin
// composeApp/build.gradle.kts — in commonTest dependencies block:
implementation(libs.sqldelight.sqlite.driver)
```
This provides `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` for in-memory test databases. This is a JVM-only artifact used only in `commonTest` (which runs on JVM via Gradle test tasks).

### SQLDelight Schema — area_bucket_cache.sq

Create at: `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_bucket_cache.sq`

```sql
CREATE TABLE IF NOT EXISTS area_bucket_cache (
    area_name TEXT NOT NULL,
    bucket_type TEXT NOT NULL,
    language TEXT NOT NULL,
    highlight TEXT NOT NULL,
    content TEXT NOT NULL,
    confidence TEXT NOT NULL,
    sources_json TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (area_name, bucket_type, language)
);

CREATE INDEX IF NOT EXISTS idx_area_bucket_cache_expires_at
    ON area_bucket_cache(expires_at);

-- Named queries:

getBucketsByAreaAndLanguage:
SELECT *
FROM area_bucket_cache
WHERE area_name = :area_name AND language = :language;

getBucket:
SELECT *
FROM area_bucket_cache
WHERE area_name = :area_name AND bucket_type = :bucket_type AND language = :language;

insertOrReplace:
INSERT OR REPLACE INTO area_bucket_cache(
    area_name, bucket_type, language, highlight, content, confidence, sources_json, expires_at, created_at
) VALUES (
    :area_name, :bucket_type, :language, :highlight, :content, :confidence, :sources_json, :expires_at, :created_at
);

deleteExpiredBuckets:
DELETE FROM area_bucket_cache
WHERE expires_at < :current_time;

deleteByArea:
DELETE FROM area_bucket_cache
WHERE area_name = :area_name;
```

**Important:** SQLDelight table names in `.sq` are `snake_case`. Column names are `snake_case`. SQLDelight generates Kotlin extension functions matching the SQL query names.

### DatabaseDriverFactory — expect/actual Pattern

**commonMain:**
```kotlin
// composeApp/src/commonMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.kt
package com.areadiscovery.data.local

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

**androidMain:**
```kotlin
// composeApp/src/androidMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.android.kt
package com.areadiscovery.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(AreaDiscoveryDatabase.Schema, context, "area_discovery.db")
}
```

**iosMain:**
```kotlin
// composeApp/src/iosMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.ios.kt
package com.areadiscovery.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(AreaDiscoveryDatabase.Schema, "area_discovery.db")
}
```

**Platform DI wiring:**
Check for existing `PlatformModule.android.kt` and `PlatformModule.ios.kt`. If they exist (from Story 2.1 for `LocationProvider`), add `DatabaseDriverFactory` there:
```kotlin
// PlatformModule.android.kt
actual val platformModule = module {
    // existing LocationProvider binding...
    single { DatabaseDriverFactory(androidContext()) }
}
// PlatformModule.ios.kt
actual val platformModule = module {
    // existing LocationProvider binding...
    single { DatabaseDriverFactory() }
}
```

### AreaRepositoryImpl — Caching Logic Detail

```kotlin
// composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt
class AreaRepositoryImpl(
    private val aiProvider: AreaIntelligenceProvider,
    private val database: AreaDiscoveryDatabase,
    private val scope: CoroutineScope  // app-scoped for background refreshes
) : AreaRepository {

    companion object {
        private const val MS_PER_HOUR = 3_600_000L
        private const val MS_PER_DAY = 86_400_000L
        // TTL tiers
        const val CACHE_TTL_STATIC_MS = 14 * MS_PER_DAY          // History, Character
        const val CACHE_TTL_SEMI_STATIC_MS = 3 * MS_PER_DAY       // Cost, Nearby
        const val CACHE_TTL_DYNAMIC_MS = 12 * MS_PER_HOUR         // Safety, What's Happening
    }

    private fun getTtlMs(bucketType: BucketType): Long = when (bucketType) {
        BucketType.HISTORY, BucketType.CHARACTER -> CACHE_TTL_STATIC_MS
        BucketType.COST, BucketType.NEARBY -> CACHE_TTL_SEMI_STATIC_MS
        BucketType.SAFETY, BucketType.WHATS_HAPPENING -> CACHE_TTL_DYNAMIC_MS
    }

    override fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
        val language = context.preferredLanguage
        val now = currentTimeMillis()
        val cached = database.areaBucketCacheQueries
            .getBucketsByAreaAndLanguage(areaName, language)
            .executeAsList()

        val validBuckets = cached.filter { it.expires_at > now }
        val staleBuckets = cached.filter { it.expires_at <= now }

        if (validBuckets.size == 6) {
            // Full cache hit — emit all 6 from cache
            validBuckets.forEach { row ->
                emit(BucketUpdate.BucketComplete(row.toBucketContent()))
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        if (staleBuckets.isNotEmpty()) {
            // Stale-while-revalidate — emit stale immediately
            staleBuckets.forEach { row ->
                emit(BucketUpdate.BucketComplete(row.toBucketContent()))
            }
            // Trigger background refresh — do NOT await
            scope.launch {
                aiProvider.streamAreaPortrait(areaName, context).collect { update ->
                    if (update is BucketUpdate.BucketComplete) {
                        writeToCache(update.content, areaName, language)
                    }
                }
            }
            emit(BucketUpdate.PortraitComplete(pois = emptyList()))
            return@flow
        }

        // Cache miss — stream from AI, write each bucket as it completes
        aiProvider.streamAreaPortrait(areaName, context).collect { update ->
            emit(update)
            if (update is BucketUpdate.BucketComplete) {
                writeToCache(update.content, areaName, language)
            }
        }
    }

    private fun writeToCache(bucket: BucketContent, areaName: String, language: String) {
        val now = currentTimeMillis()
        val expiresAt = now + getTtlMs(bucket.type)
        val sourcesJson = Json.encodeToString(bucket.sources)
        database.areaBucketCacheQueries.insertOrReplace(
            area_name = areaName,
            bucket_type = bucket.type.name,
            language = language,
            highlight = bucket.highlight,
            content = bucket.content,
            confidence = bucket.confidence.name,
            sources_json = sourcesJson,
            expires_at = expiresAt,
            created_at = now
        )
    }
}

// Extension to map SQLDelight row → domain model
private fun AreaBucketCache.toBucketContent(): BucketContent = BucketContent(
    type = BucketType.valueOf(bucket_type),
    highlight = highlight,
    content = content,
    confidence = Confidence.valueOf(confidence),
    sources = Json.decodeFromString(sources_json)
)
```

**`CoroutineScope` for background refresh:** The app-level scope is injected. This ensures background refreshes outlive the calling coroutine. Register in `AppModule.kt`:
```kotlin
single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
```
Check `AppModule.kt` — it may already exist with other app-wide bindings. Add to it, don't create a duplicate module.

**`currentTimeMillis()` in KMP:** `System.currentTimeMillis()` is JVM-only — cannot be used in `commonMain`. Use Kotlin stdlib (Kotlin 2.3.0+ is sufficient):
```kotlin
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
```
Or inject a `Clock` interface for testability (allows `TestScope.advanceTimeBy()` to control time in tests):
```kotlin
// Prefer injectable clock for clean testing:
interface AppClock { fun nowMs(): Long }
class SystemClock : AppClock { override fun nowMs() = Clock.System.now().toEpochMilliseconds() }
class FakeClock(var nowMs: Long = 0L) : AppClock { override fun nowMs() = nowMs }
```
Inject `AppClock` into `AreaRepositoryImpl` and use `clock.nowMs()` instead of direct calls. Wire in DI: `single<AppClock> { SystemClock() }`.
**Note:** If `kotlinx-datetime` is in `libs.versions.toml`, you can use `Clock.System.now().toEpochMilliseconds()` from it — same API as the stdlib approach above.

### sources_json Serialization

`List<Source>` must be JSON-serializable. Check `Source.kt` — if it doesn't already have `@Serializable`, add it:
```kotlin
// domain/model/Source.kt — add if missing:
import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val title: String,
    val url: String?
)
```

Then serialize with: `Json { ignoreUnknownKeys = true }.encodeToString(bucket.sources)` and deserialize with `Json.decodeFromString<List<Source>>(sourcesJson)`.

### Testing Pattern — In-Memory SQLDelight

```kotlin
// AreaRepositoryTest.kt — setup
private lateinit var driver: SqlDriver
private lateinit var database: AreaDiscoveryDatabase
private lateinit var fakeProvider: FakeAreaIntelligenceProvider
private lateinit var repository: AreaRepositoryImpl
private val testScope = TestScope()

@BeforeTest
fun setup() {
    driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    AreaDiscoveryDatabase.Schema.create(driver)
    database = AreaDiscoveryDatabase(driver)
    fakeProvider = FakeAreaIntelligenceProvider()
    repository = AreaRepositoryImpl(fakeProvider, database, testScope)
}

@AfterTest
fun teardown() {
    driver.close()
}
```

**Fake provider:**
```kotlin
// commonTest/fakes/FakeAreaIntelligenceProvider.kt
class FakeAreaIntelligenceProvider : AreaIntelligenceProvider {
    var callCount = 0
    var responseFlow: Flow<BucketUpdate> = defaultBucketFlow()

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> {
        callCount++
        return responseFlow
    }

    override fun streamChatResponse(...) = emptyFlow<ChatToken>()
}

private fun defaultBucketFlow() = flow {
    BucketType.entries.forEach { type ->
        emit(BucketUpdate.BucketComplete(BucketContent(
            type = type,
            highlight = "Test highlight $type",
            content = "Test content $type",
            confidence = Confidence.HIGH,
            sources = emptyList()
        )))
    }
    emit(BucketUpdate.PortraitComplete(pois = emptyList()))
}
```

**Cache hit test (skeleton):**
```kotlin
@Test
fun `cache hit returns buckets without calling AI provider`() = testScope.runTest {
    // Pre-populate all 6 buckets with future expires_at
    BucketType.entries.forEach { type ->
        database.areaBucketCacheQueries.insertOrReplace(
            area_name = "Test Area", bucket_type = type.name, language = "en",
            highlight = "h", content = "c", confidence = "HIGH",
            sources_json = "[]",
            expires_at = currentTimeMillis() + 10_000L,
            created_at = currentTimeMillis()
        )
    }

    val results = repository.getAreaPortrait("Test Area", defaultContext()).toList()

    assertEquals(0, fakeProvider.callCount)
    assertEquals(7, results.size) // 6 BucketComplete + 1 PortraitComplete
}
```

### Project Structure Notes

**New files to create:**
```
composeApp/src/commonMain/
├── sqldelight/com/areadiscovery/data/local/
│   └── area_bucket_cache.sq
└── kotlin/com/areadiscovery/
    ├── data/
    │   ├── local/
    │   │   └── DatabaseDriverFactory.kt          (expect)
    │   └── repository/
    │       └── AreaRepositoryImpl.kt
    ├── domain/
    │   ├── repository/
    │   │   └── AreaRepository.kt
    │   └── usecase/
    │       └── GetAreaPortraitUseCase.kt
    └── util/
        └── AppClock.kt                           (AppClock interface + SystemClock impl)

composeApp/src/androidMain/kotlin/com/areadiscovery/data/local/
    └── DatabaseDriverFactory.android.kt           (actual)

composeApp/src/iosMain/kotlin/com/areadiscovery/data/local/
    └── DatabaseDriverFactory.ios.kt               (actual)

composeApp/src/commonTest/kotlin/com/areadiscovery/
├── data/repository/
│   └── AreaRepositoryTest.kt
└── fakes/
    └── FakeAreaIntelligenceProvider.kt
```

**Files to modify:**
```
composeApp/src/androidMain/.../di/PlatformModule.android.kt  — add DatabaseDriverFactory(androidContext())
composeApp/src/iosMain/.../di/PlatformModule.ios.kt          — add DatabaseDriverFactory()
composeApp/src/commonMain/.../di/DataModule.kt               — add Database + Repository + UseCase bindings
composeApp/src/commonMain/.../domain/model/Source.kt         — add @Serializable if missing
```

**Do NOT modify:**
- `GeminiAreaIntelligenceProvider.kt` — already implements `AreaIntelligenceProvider`; this story wraps it, not changes it
- `MockAreaIntelligenceProvider.kt` — keep for testing; `FakeAreaIntelligenceProvider` is a new test double
- Any UI code (`SummaryViewModel`, `SummaryScreen`) — Story 2.5 connects them to `AreaRepository`
- `BucketUpdate.kt`, `BucketContent.kt`, `AreaContext.kt` domain models — stable

### Existing Patterns to Follow

- **Logging:** `AppLogger.d { }` / `AppLogger.e(throwable) { }` — from `com.areadiscovery.util.AppLogger`
- **Serialization:** `@Serializable` + `kotlinx.serialization` JSON — NOT Gson or Moshi
- **Koin DI:** `single<Interface> { Implementation(get(), get()) }` — `single` (not `factory`) for repositories
- **Testing flows:** `import app.cash.turbine.test` + `.test { awaitItem() }` pattern (Turbine 1.2.0)
- **Assertions:** `kotlin.test` — `assertEquals()`, `assertTrue()`, `assertNotNull()` — NOT JUnit's `assertEquals`
- **Test coroutines:** `kotlinx.coroutines.test.runTest` + `TestScope`
- **Expect/actual:** Same pattern as `LocationProvider` (from Story 2.1) — interface as `expect class` in commonMain, `actual class` per platform
- **Package:** `com.areadiscovery.*` — never use `com.example.*`

### Anti-Patterns to Avoid

- **Do NOT** use `GlobalScope` for background refreshes — inject `CoroutineScope` so it can be controlled in tests
- **Do NOT** expose `MutableStateFlow` publicly — only exposes `Flow<BucketUpdate>` per contract
- **Do NOT** hardcode TTL values inline — use named constants in companion object
- **Do NOT** use `Thread.sleep()` for timing in tests — use `advanceTimeBy()` in `TestScope`
- **Do NOT** store raw GPS coordinates in `area_bucket_cache` — `area_name` (string) is the key per privacy pipeline (Story 2.1)
- **Do NOT** import `android.*` in `commonMain` — `DatabaseDriverFactory` in commonMain must be `expect class` only
- **Do NOT** use `runBlocking` in tests — use `runTest` from `kotlinx.coroutines.test`
- **Do NOT** call `AreaIntelligenceProvider` for stale-revalidate on the calling coroutine — always `launch` in injected scope
- **Do NOT** use `@Serializable` annotation on SQLDelight-generated classes — only on domain models

### Previous Story Intelligence (Story 2.2)

**Key learnings from Story 2.2:**
- `ktor-client-sse` does NOT exist as a separate artifact in Ktor 3.4.0 — SSE is in `ktor-client-core`. Verify before adding dependencies that the capability isn't already present.
- `DomainErrorException` wrapper class was added to `DomainError.kt` to bridge sealed class to exception system — reuse this for flow error propagation if needed
- `companion object { const val X = ... }` pattern for constants — follow this in `AreaRepositoryImpl`
- All tests must run on both Android and iOS — use `commonTest` only; no `androidUnitTest` for repository logic
- `FakeXxx` test doubles in `commonTest/fakes/` — Story 2.2 convention is `FakeAreaIntelligenceProvider`
- Code review happens after dev-story; plan for at least 1-2 review rounds before marking done

**Files from Story 2.2 that this story depends on:**
- `data/remote/GeminiAreaIntelligenceProvider.kt` — bound as `AreaIntelligenceProvider` in Koin (Story 2.3 wraps it)
- `di/DataModule.kt` — must be updated (not replaced) to add new bindings
- `domain/model/DomainError.kt` — includes `DomainErrorException` for error bridging

**Files from Story 2.1 that this story depends on:**
- `domain/service/PrivacyPipeline.kt` — outputs `area_name` string used as cache key
- `di/PlatformModule.android.kt` / `PlatformModule.ios.kt` — add `DatabaseDriverFactory` here

### Git Intelligence

Recent commit pattern:
- `8e72d20 Add Gemini adapter with SSE streaming (Story 2.2)` — initial implementation
- `26957ad Address code review findings for Story 2.2 (8 items)` — first review round
- `ee0205b Address round 2 review findings for Story 2.2 (8 items)` — second review round
- Expect at minimum 1-2 code review iterations after initial implementation
- Commit message format: `Add [description] (Story X.Y)` for initial, `Address [round N] review findings for Story X.Y` for fixes

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Epic 2, Story 2.3]
- [Source: _bmad-output/planning-artifacts/architecture.md — Data Architecture, Cache Patterns, SQLDelight Schema, Repository Pattern]
- [Source: _bmad-output/planning-artifacts/prd.md — NFR3 (cache <500ms), FR1, FR2, FR5]
- [Source: _bmad-output/implementation-artifacts/2-2-gemini-adapter-and-sse-streaming.md — Previous Story Dev Notes, File List]
- [Source: _bmad-output/implementation-artifacts/2-1-location-service-and-privacy-pipeline.md — expect/actual pattern, PlatformModule wiring]
- [SQLDelight 2.x KMP Docs](https://cashapp.github.io/sqldelight/2.x/multiplatform_sqlite/)
- [SQLDelight Coroutines Extensions](https://cashapp.github.io/sqldelight/2.x/multiplatform_sqlite/coroutines/)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- SQLDelight generated accessor: `area_bucket_cacheQueries` (snake_case, not camelCase) — required fix from initial build failure
- `sqldelight-sqlite-driver` is JVM-only — moved repository tests from `commonTest` to `androidUnitTest` source set
- `TestScope.testScheduler.advanceUntilIdle()` needed for stale-revalidate test to allow background `scope.launch` to execute

### Completion Notes List

- All 9 tasks and subtasks completed successfully
- 5 unit tests pass: cache hit, stale-revalidate, cache miss, language-switch, TTL verification
- All 3 build gates pass (assembleDebug, allTests, lint)
- Added `@Serializable` to `Source` data class for JSON serialization of `sources_json` column
- Created injectable `AppClock` interface for testable time management (avoids JVM-only `System.currentTimeMillis()`)
- Updated existing `FakeAreaIntelligenceProvider` with `callCount` tracking and configurable `responseFlow`
- DI wiring consolidated in `DataModule.kt` (CoroutineScope, AppClock, Database, Repository, UseCase)

### Change Log

- 2026-03-04: Initial implementation of Story 2.3 — Area Repository & Caching Foundation

### File List

**New files:**
- `composeApp/src/commonMain/sqldelight/com/areadiscovery/data/local/area_bucket_cache.sq`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.kt`
- `composeApp/src/androidMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.android.kt`
- `composeApp/src/iosMain/kotlin/com/areadiscovery/data/local/DatabaseDriverFactory.ios.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/repository/AreaRepository.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/usecase/GetAreaPortraitUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/util/AppClock.kt`
- `composeApp/src/androidUnitTest/kotlin/com/areadiscovery/data/repository/AreaRepositoryTest.kt`
- `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeClock.kt`

**Modified files:**
- `gradle/libs.versions.toml` — added `sqldelight-sqlite-driver` library entry
- `composeApp/build.gradle.kts` — added `sqldelight-sqlite-driver` to `androidUnitTest` dependencies
- `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Source.kt` — added `@Serializable` annotation
- `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt` — added Database, Repository, UseCase, AppClock, CoroutineScope bindings
- `composeApp/src/androidMain/kotlin/com/areadiscovery/di/PlatformModule.android.kt` — added DatabaseDriverFactory binding
- `composeApp/src/iosMain/kotlin/com/areadiscovery/di/PlatformModule.ios.kt` — added DatabaseDriverFactory binding
- `composeApp/src/commonTest/kotlin/com/areadiscovery/fakes/FakeAreaIntelligenceProvider.kt` — added callCount, responseFlow, defaultBucketEmissions()
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — story status: ready-for-dev → in-progress → review
