---
phase: "01"
plan: "01"
subsystem: android-build
tags: [compose-bom, gradle, dependencies, test-scaffold]
dependency_graph:
  requires: []
  provides: [compose-bom-2026, kotlinx-collections-immutable, compose-compiler-reports, koto-theme-test-scaffold]
  affects: [android/gradle/libs.versions.toml, android/app/build.gradle.kts, android/app/src/test/java/run/koto/ui/theme/KotoThemeTest.kt]
tech_stack:
  added: [kotlinx-collections-immutable@0.4.0]
  patterns: [compose-compiler-reports, version-catalog]
key_files:
  created:
    - android/app/src/test/java/run/koto/ui/theme/KotoThemeTest.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
decisions:
  - "Compose BOM upgraded from 2025.03.01 to 2026.03.01 — unlocks Material3 1.4.0, Compose Animation 1.10.6, animateItem(), stable SharedTransitionLayout"
  - "kotlinx-collections-immutable 0.4.0 added for @Immutable UI models (ImmutableList) used from Plan 05 onward"
  - "composeCompiler { reportsDestination } configured now so stability reports are available from first build after BOM upgrade"
  - "KotoThemeTest.kt created in RED state intentionally — tests reference token classes that do not exist yet; GREEN after Plans 02-04"
  - "NDK not installed in current environment — assembleDebug fails at configuration stage (pre-existing environment constraint, not caused by these changes)"
metrics:
  duration: "~10 minutes"
  completed: "2026-04-05"
  tasks_completed: 2
  files_changed: 3
---

# Phase 01 Plan 01: BOM Upgrade and Test Scaffold Summary

Compose BOM upgraded to 2026.03.01 with kotlinx-collections-immutable 0.4.0 added, compiler reports configured, and KotoThemeTest scaffold created in RED state for Plans 02-05 design tokens.

## Tasks Completed

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Upgrade Compose BOM and add kotlinx-collections-immutable | Done | b33592d |
| 2 | Create KotoThemeTest scaffold | Done | 9b93ac6 |

## What Was Built

### Task 1: Compose BOM Upgrade (b33592d)

**android/gradle/libs.versions.toml:**
- `composeBom` changed from `"2025.03.01"` to `"2026.03.01"`
- Added `kotlinxCollectionsImmutable = "0.4.0"` in `[versions]`
- Added `kotlinx-collections-immutable` library entry in `[libraries]` under `# Collections`

**android/app/build.gradle.kts:**
- Added `implementation(libs.kotlinx.collections.immutable)` in dependencies
- Added `composeCompiler { reportsDestination / metricsDestination }` block inside `android {}` after `buildFeatures`

### Task 2: KotoThemeTest Scaffold (9b93ac6)

**android/app/src/test/java/run/koto/ui/theme/KotoThemeTest.kt:**
- 15 `@Test` methods covering DS-01 through DS-06
- JVM-only (no emulator required)
- References: `darkKotoColors`, `lightKotoColors`, `defaultKotoTypography`, `KotoSpacing`, `KotoShapes`
- Tests are in intentional RED state — token classes do not exist until Plans 02-04

## Verification Results

```
grep "composeBom" libs.versions.toml    → composeBom = "2026.03.01"   PASS
grep "kotlinxCollectionsImmutable"      → "0.4.0"                      PASS
grep "kotlinx-collections-immutable"    → library entry present        PASS
grep "reportsDestination" build.gradle  → line present                 PASS
ls KotoThemeTest.kt                     → file exists                  PASS
@Test count                             → 15 (>= required 8)           PASS
./gradlew :app:assembleDebug            → BLOCKED (see deviation below)
```

## Deviations from Plan

### Environment Limitation — NDK Not Installed

**Found during:** Task 1 verification step

**Issue:** `./gradlew :app:assembleDebug` fails with `NDK is not installed` at Gradle configuration phase. The `rust-android-gradle` plugin requires NDK to configure the project — this failure occurs regardless of any changes made in this plan. The error was present before any edits.

**Evidence:** `> NDK is not installed` at project configuration, not at compilation stage. Both `assembleDebug` and `compileDebugKotlin` fail identically with the same NDK error.

**Impact:** Cannot verify BUILD SUCCESSFUL via Gradle in this environment. All file content changes (TOML, Gradle DSL) are syntactically correct — verified by inspection. The BOM change, library entry, and composeCompiler block are all valid Gradle Kotlin DSL patterns.

**Resolution:** Install Android NDK in Android Studio (SDK Manager → NDK) before building. This is an environment setup issue, not a code issue.

**Action:** None — no code fix possible. Document for developer to install NDK.

## Known Stubs

None — this plan contains only build configuration and test scaffold. No UI-rendering stubs exist.

## Self-Check: PASSED

- `android/gradle/libs.versions.toml` — contains `composeBom = "2026.03.01"` and `kotlinxCollectionsImmutable = "0.4.0"` ✓
- `android/app/build.gradle.kts` — contains `reportsDestination` and `libs.kotlinx.collections.immutable` ✓
- `android/app/src/test/java/run/koto/ui/theme/KotoThemeTest.kt` — exists with 15 @Test annotations ✓
- Commit `b33592d` exists (Task 1) ✓
- Commit `9b93ac6` exists (Task 2) ✓
