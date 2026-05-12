---
phase: 04-navigation-system-integration
plan: "01"
subsystem: android/navigation
tags: [navigation, glassmorphism, compose, animation, spring]
dependency_graph:
  requires:
    - "01-01 KotoColors (surface, primary, onSurfaceMuted, divider tokens)"
    - "01-02 KotoTypography (labelSmall)"
    - "01-03 KotoMotion (springBouncy)"
    - "KotoNavGraph.kt (Screen sealed class routes)"
  provides:
    - "KotoBottomNavBar composable — drop-in glassmorphism bottom nav"
    - "BottomNavDestination sealed class — 4 tab destinations"
  affects:
    - "04-02 NavHost scaffold integration (will consume KotoBottomNavBar)"
tech_stack:
  added: []
  patterns:
    - "Modifier.blur() for API 31+ glassmorphism; solid alpha fallback for API <31"
    - "animateFloatAsState with motion.springBouncy for indicator scale"
    - "graphicsLayer { scaleX; scaleY } for draw-phase reads (P3 pitfall avoidance)"
    - "drawBehind for top divider line without recomposition"
    - "windowInsetsPadding(WindowInsets.navigationBars) for edge-to-edge"
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/navigation/KotoBottomNavBar.kt
  modified: []
decisions:
  - "weight(1f) applied at Row call site via modifier param, not inside BottomNavItem — avoids RowScope receiver requirement on private composable"
  - "glassModifier built as separate Modifier val before the Box — keeps API-level branch readable"
  - "dividerColor captured as val before drawBehind lambda — avoids reading KotoTheme inside lambda context"
metrics:
  duration: "2 minutes"
  completed: "2026-04-05T20:04:36Z"
  tasks_completed: 1
  files_created: 1
  files_modified: 0
---

# Phase 04 Plan 01: KotoBottomNavBar — Glassmorphism Bottom Navigation Summary

**One-liner:** Glassmorphism bottom nav bar with spring-animated indicator pill — API 31+ blur via `Modifier.blur()`, solid-surface fallback for older API, all colors from KotoTheme tokens.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | KotoBottomNavBar composable with glassmorphism + spring indicator | d686687 | KotoBottomNavBar.kt (created) |

## What Was Built

`KotoBottomNavBar.kt` delivers a fully standalone bottom navigation bar composable for the Koto Android app. The component:

- **4 tab destinations:** Chats (conversations), Contacts, Calls, Settings — defined in `BottomNavDestination` sealed class. Contacts and Calls use stub routes (`"contacts"`, `"calls"`) for future screen wiring.

- **Glassmorphism background (NAV-01):**
  - API 31+ (Android 12+): `surface.copy(alpha = 0.85f)` with `Modifier.blur(radius = 20.dp)` for a frosted-glass effect.
  - API < 31 fallback: solid `surface.copy(alpha = 0.97f)` — no blur API available pre-S.
  - Top border: 1dp `divider` color drawn via `drawBehind` in the draw phase (no recomposition cost).

- **Spring-animated active indicator:** A 64×32dp stadium-shape pill (primary color at 15% alpha) scales from 0 → 1 using `animateFloatAsState` with `KotoTheme.motion.springBouncy` (StiffnessMedium + DampingRatioLowBouncy). The `graphicsLayer { scaleX; scaleY }` pattern defers reads to the draw phase, avoiding recomposition per P3 pitfall.

- **Edge-to-edge insets:** `windowInsetsPadding(WindowInsets.navigationBars)` on the outer container adds bottom safe-area padding on top of the fixed 64dp bar height.

- **Color discipline:** Zero raw hex literals. All color references exclusively via `KotoTheme.colors.*` tokens.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] BottomNavItem Modifier.weight() RowScope resolution**
- **Found during:** Task 1 — first compilation attempt
- **Issue:** The plan specification placed `Modifier.weight(1f)` inside `BottomNavItem`'s own modifier, but `weight` is an extension only available in `RowScope`. The private composable is not defined inside a Row.
- **Fix:** Added optional `modifier: Modifier = Modifier` parameter to `BottomNavItem`. The `weight(1f)` modifier is passed from the `Row` forEach call site where `RowScope` is in scope.
- **Files modified:** KotoBottomNavBar.kt
- **Commit:** d686687 (included in task commit — one-compile fix)

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `BottomNavDestination.Contacts` route `"contacts"` | KotoBottomNavBar.kt:64 | No Contacts screen yet — future phase will deliver it |
| `BottomNavDestination.Calls` route `"calls"` | KotoBottomNavBar.kt:70 | No Calls screen yet — future phase will deliver it |

Both stubs are intentional scaffolding (noted in plan: "Contacts and Calls destinations are stubs"). They do not prevent the plan's goal (standalone nav bar component delivery). Plan 04-02 wires this bar into NavHost scaffold; actual screen destinations are out of scope for Phase 04.

## Self-Check: PASSED

- [x] `KotoBottomNavBar.kt` exists at `android/app/src/main/java/run/koto/ui/navigation/KotoBottomNavBar.kt`
- [x] Commit d686687 exists in git log
- [x] No raw hex in file (`grep "Color(0x"` returns empty)
- [x] `springBouncy` + `animateFloatAsState` present
- [x] `graphicsLayer` pattern present
- [x] `windowInsetsPadding(WindowInsets.navigationBars)` present
- [x] `BottomNavDestination.all` contains 4 entries
