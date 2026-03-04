# Story 1.2: CI/CD Pipeline

Status: done

## Story

As a **developer**,
I want automated build, test, and lint checks on every pull request,
So that code quality is enforced from the first commit.

## Acceptance Criteria

1. GitHub Actions workflow file exists at `.github/workflows/ci.yml`
2. Workflow triggers on pull requests opened or updated against `main` branch
3. Workflow runs `./gradlew :composeApp:assembleDebug` and passes
4. Workflow runs `./gradlew :composeApp:allTests` and passes
5. Workflow runs lint checks and passes
6. Workflow fails the PR check if any step fails

## Tasks / Subtasks

- [x] Task 1: Create GitHub Actions workflow file (AC: #1, #2)
  - [x] 1.1 Create directory `.github/workflows/`
  - [x] 1.2 Create `ci.yml` with workflow trigger on `pull_request` targeting `main` branch
  - [x] 1.3 Also trigger on `push` to `main` branch (ensures main stays green)

- [x] Task 2: Configure build job (AC: #3, #6)
  - [x] 2.1 Set `runs-on: ubuntu-latest` for the Android build job
  - [x] 2.2 Add `actions/checkout@v4` step
  - [x] 2.3 Add `actions/setup-java@v4` with Temurin JDK 21
  - [x] 2.4 Add `gradle/actions/setup-gradle@v4` for Gradle caching
  - [x] 2.5 Add Kotlin Native cache step for `.konan` directory using `actions/cache@v4`
  - [x] 2.6 Add build step: `./gradlew :composeApp:assembleDebug`

- [x] Task 3: Configure test job (AC: #4, #6)
  - [x] 3.1 Add test step: `./gradlew :composeApp:allTests`
  - [x] 3.2 Ensure test step fails the workflow if any test fails

- [x] Task 4: Configure lint checks (AC: #5, #6)
  - [x] 4.1 Add lint step: `./gradlew :composeApp:lint`
  - [x] 4.2 Ensure lint step fails the workflow if lint errors are found

- [x] Task 5: Verify workflow locally (AC: #3, #4, #5)
  - [x] 5.1 Run `./gradlew :composeApp:assembleDebug` locally — confirm it passes
  - [x] 5.2 Run `./gradlew :composeApp:allTests` locally — confirm it passes
  - [x] 5.3 Run `./gradlew :composeApp:lint` locally — confirm it passes (fix any lint errors if needed)
  - [x] 5.4 Validate the YAML syntax is correct (no indentation errors)

## Dev Notes

### GitHub Actions Workflow Structure

The workflow should be a single job with sequential steps (not parallel jobs) to keep it simple for Phase 1a. All steps run on `ubuntu-latest`.

### Exact Workflow Configuration

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    name: Build, Test & Lint
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Cache Kotlin Native
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: konan-

      - name: Build Android Debug APK
        run: ./gradlew :composeApp:assembleDebug

      - name: Run All Tests
        run: ./gradlew :composeApp:allTests

      - name: Run Lint
        run: ./gradlew :composeApp:lint
```

### Key Technical Details

- **JDK 21**: Required by AGP 8.11.2 + Gradle 8.14.3. Temurin (Eclipse Adoptium) is the recommended distribution.
- **Gradle caching**: `gradle/actions/setup-gradle@v4` handles Gradle wrapper download, dependency cache, and build cache automatically.
- **Kotlin Native cache**: The `.konan` directory stores Kotlin Native compiler and libraries needed for iOS target compilation during `allTests`. Without caching, this adds ~2-3 minutes per run.
- **No iOS-specific build**: iOS compilation happens as part of `allTests` (which includes `iosSimulatorArm64Test`). A dedicated iOS build job on `macos-latest` is deferred (10x cost).
- **No `continue-on-error`**: All steps must pass — a failure in any step should fail the workflow (AC #6).

### Android Lint Considerations

The project currently has no custom lint rules. The default Android lint will run against `composeApp`. If there are pre-existing lint warnings from the wizard-generated code, they should be fixed, not suppressed.

Common lint issues to fix:
- Missing `android:exported` on activities (already set by wizard)
- Hardcoded strings (the wizard's `strings.xml` handles `app_name`)
- Unused resources (the compose-multiplatform drawable may trigger this — can be suppressed via `lint.xml` if needed)

### Previous Story (1.1) Learnings

Key insights from Story 1.1 implementation:
- `androidTarget()` is deprecated-as-error in Kotlin 2.3.0 — uses `@Suppress("DEPRECATION")`. This is a known issue and won't affect CI.
- Koin BOM `platform()` not supported in KMP — explicit versions used instead.
- `compileSdk` and `targetSdk` are 36 (not 35) due to AndroidX dependency requirements.
- Build command `./gradlew :composeApp:assembleDebug` confirmed working.
- Test command `./gradlew :composeApp:allTests` confirmed working (runs Android unit tests + iOS simulator tests).
- The `Unable to strip libraries: libandroidx.graphics.path.so, libmaplibre.so` warning is benign — does not fail the build.

### What NOT to Do

- Do NOT add Detekt or other static analysis tools (not in story scope)
- Do NOT add deployment steps (Google Play upload is a future concern)
- Do NOT add iOS-specific jobs on macOS runners (cost optimization — deferred)
- Do NOT use `continue-on-error: true` on any step — all must be required
- Do NOT cache `local.properties` or any secrets in CI

### Project Structure Notes

- The workflow file goes at `.github/workflows/ci.yml` — this is the standard GitHub Actions path
- No `local.properties` file in CI — BuildKonfig will use the empty default (`""`) for `GEMINI_API_KEY` which is fine for builds/tests
- The `sdk.dir` is not needed in CI — GitHub Actions' `setup-java` handles the Android SDK

### References

- [Source: _bmad-output/planning-artifacts/architecture.md#Infrastructure & Deployment] — GitHub Actions CI/CD decision
- [Source: _bmad-output/planning-artifacts/architecture.md#Testing Strategy] — Test framework setup
- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.2] — Acceptance criteria
- [Source: _bmad-output/implementation-artifacts/1-1-kmp-project-initialization-and-package-structure.md#Completion Notes] — Build/test commands and known issues

## Dev Agent Record

### Agent Model Used
Claude Opus 4.6

### Debug Log References
N/A — no errors encountered during implementation.

### Completion Notes List

- Created `.github/workflows/ci.yml` matching the exact spec from Dev Notes
- Workflow triggers on `push` to `main` and `pull_request` targeting `main`
- Single job `build-and-test` with sequential steps: checkout → JDK 21 setup → Gradle setup → Kotlin Native cache → assembleDebug → allTests → lint
- No `continue-on-error` on any step — all steps are required (AC #6)
- All three Gradle commands verified locally:
  - `./gradlew :composeApp:assembleDebug` — BUILD SUCCESSFUL
  - `./gradlew :composeApp:allTests` — BUILD SUCCESSFUL
  - `./gradlew :composeApp:lint` — BUILD SUCCESSFUL (28 informational warnings: AGP/dependency version suggestions, locked orientation)
- YAML syntax validated via Ruby YAML parser
- Lint warnings are informational only (version upgrade suggestions, intentional locked orientation) — no lint errors

### Change Log
- 2026-03-04: Created CI/CD workflow file `.github/workflows/ci.yml` — all ACs satisfied
- 2026-03-04: Code review fixes — added concurrency group, permissions block, Konan cache conditional, inline comments, iOS test limitation documentation

### File List
- `.github/workflows/ci.yml` (new, then updated) — GitHub Actions CI workflow

## Senior Developer Review (AI)

**Review Date:** 2026-03-04
**Reviewer Model:** Claude Opus 4.6
**Review Outcome:** Approve (after fixes applied)

### Findings Summary
- 0 Critical, 3 Medium, 3 Low (1 Low dropped as intentional)

### Action Items
- [x] [M1] Add comment documenting iOS tests not running on ubuntu-latest
- [x] [M2] Add `concurrency` group with `cancel-in-progress: true`
- [x] [M3] Add explicit `permissions: contents: read` block
- [x] [L1] Add `if: runner.os == 'macOS'` condition on Konan cache step
- [x] [L2] Add inline YAML comments explaining design decisions
- [x] L3 dropped — keeping `_bmad/` in git is intentional for team onboarding
