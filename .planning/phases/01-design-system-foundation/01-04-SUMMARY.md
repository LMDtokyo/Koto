---
phase: "01"
plan: "04"
subsystem: android-design-tokens
tags: [spacing, shapes, elevation, motion, design-tokens, compose]
dependency_graph:
  requires: [01-01]
  provides: [KotoSpacing, KotoShapes, KotoElevation, KotoMotion, DefaultKotoMotion]
  affects:
    - android/app/src/main/java/run/koto/ui/theme/Spacing.kt
    - android/app/src/main/java/run/koto/ui/theme/Shapes.kt
    - android/app/src/main/java/run/koto/ui/theme/Elevation.kt
    - android/app/src/main/java/run/koto/ui/theme/Motion.kt
tech_stack:
  added: []
  patterns: [immutable-data-class-tokens, composition-local-ready, spring-physics-motion]
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/theme/Spacing.kt
    - android/app/src/main/java/run/koto/ui/theme/Shapes.kt
    - android/app/src/main/java/run/koto/ui/theme/Elevation.kt
    - android/app/src/main/java/run/koto/ui/theme/Motion.kt
  modified: []
decisions:
  - "xxxl=48dp per REQUIREMENTS.md DS-04 (overrides ARCHITECTURE.md draft which had 32dp)"
  - "KotoShapes uses RoundedCornerShape constructor with 4 dp arguments for asymmetric bubble shapes — bubbleOut tail at bottomEnd=4dp, bubbleIn tail at bottomStart=4dp"
  - "DefaultKotoMotion is a top-level val (not object) — consistent with how DefaultKotoColors is structured, composable via staticCompositionLocalOf"
  - "KotoElevation.dialog and KotoElevation.pressed are both 8dp — intentionally same value to reflect Material3 elevation semantics"
metrics:
  duration: "~2 minutes"
  completed: "2026-04-05"
  tasks_completed: 2
  files_changed: 4
---

# Phase 01 Plan 04: Spacing, Shapes, Elevation, and Motion Token Files Summary

Four @Immutable data class token files created: KotoSpacing (xxs=2dp through xxxl=48dp with messenger semantic tokens), KotoShapes (standard sizes + asymmetric chat bubble shapes), KotoElevation (7 levels none=0dp to dialog=8dp), and KotoMotion (3 spring specs + 3 tween specs + DefaultKotoMotion instance).

## Tasks Completed

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Create Spacing.kt and Shapes.kt | Done | 88a638e |
| 2 | Create Elevation.kt and Motion.kt | Done | 09ce5ee |

## What Was Built

### Task 1: Spacing.kt and Shapes.kt (88a638e)

**android/app/src/main/java/run/koto/ui/theme/Spacing.kt:**
- `@Immutable data class KotoSpacing` with 8 base scale tokens (xxs=2dp through xxxl=48dp)
- 4 messenger-semantic tokens: `bubblePaddingH=14dp`, `bubblePaddingV=10dp`, `listItemPadding=16dp`, `inputBarPadding=12dp`
- xxxl=48dp per DS-04 requirements (not 32dp as in ARCHITECTURE.md draft)

**android/app/src/main/java/run/koto/ui/theme/Shapes.kt:**
- `@Immutable data class KotoShapes` with 11 shape fields
- `bubbleOut`: asymmetric `RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)` — tail at bottomEnd
- `bubbleIn`: asymmetric `RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)` — tail at bottomStart
- Standard sizes: `sm=8dp`, `md=12dp`, `lg=16dp`, `xl=20dp`, `bubble=18dp`
- Messenger-specific: `bottomSheet` (24dp topStart/topEnd), `inputField=24dp`, `pill=CircleShape`, `card=16dp`

### Task 2: Elevation.kt and Motion.kt (09ce5ee)

**android/app/src/main/java/run/koto/ui/theme/Elevation.kt:**
- `@Immutable data class KotoElevation` with 7 levels
- `none=0dp`, `low=1dp`, `card=2dp`, `appBar=4dp`, `fab=6dp`, `dialog=8dp`, `pressed=8dp`

**android/app/src/main/java/run/koto/ui/theme/Motion.kt:**
- `@Immutable data class KotoMotion` with 6 animation spec fields
- Spring specs: `springSnappy` (StiffnessMediumLow/NoBouncy), `springBouncy` (StiffnessMedium/LowBouncy), `springGentle` (StiffnessLow/NoBouncy)
- Tween specs: `tweenFast=150ms/FastOutSlowIn`, `tweenMedium=300ms/FastOutSlowIn`, `tweenSlow=450ms/LinearOutSlowIn`
- `val DefaultKotoMotion = KotoMotion()` — top-level instance for CompositionLocal provision
- Phase 1 success criterion 5 structurally satisfied: named spring constants exist

## Verification Results

```
grep "val xxxl : Dp = 48.dp" Spacing.kt        → match    PASS
grep "val springSnappy" Motion.kt               → match    PASS
grep "val DefaultKotoMotion" Motion.kt          → match    PASS
grep "val bubbleOut" Shapes.kt                  → match    PASS
Spacing.kt @Immutable + data class KotoSpacing  → present  PASS
Shapes.kt @Immutable + data class KotoShapes    → present  PASS
Elevation.kt @Immutable + data class KotoElev.  → present  PASS
Motion.kt @Immutable + data class KotoMotion    → present  PASS
./gradlew :app:assembleDebug                    → BLOCKED  (NDK not installed — pre-existing env constraint, same as Plan 01)
```

## Deviations from Plan

None — plan executed exactly as written. The NDK build verification limitation is pre-existing (documented in 01-01-SUMMARY.md, not caused by this plan).

## Known Stubs

None — all token files contain complete, concrete values with no placeholders or hardcoded empty collections.

## Self-Check: PASSED

- `android/app/src/main/java/run/koto/ui/theme/Spacing.kt` — exists, contains `xxxl=48dp`, `xxs=2dp`, `lg=16dp`, `@Immutable`, `data class KotoSpacing` ✓
- `android/app/src/main/java/run/koto/ui/theme/Shapes.kt` — exists, contains `bubbleOut`, `bubbleIn`, `bubble`, `sm`, `@Immutable`, `data class KotoShapes` ✓
- `android/app/src/main/java/run/koto/ui/theme/Elevation.kt` — exists, contains `dialog=8dp`, `@Immutable`, `data class KotoElevation` ✓
- `android/app/src/main/java/run/koto/ui/theme/Motion.kt` — exists, contains `springSnappy`, `springBouncy`, `springGentle`, `tweenFast`, `tweenMedium`, `tweenSlow`, `DefaultKotoMotion`, `@Immutable`, `data class KotoMotion` ✓
- Commit `88a638e` exists (Task 1) ✓
- Commit `09ce5ee` exists (Task 2) ✓
