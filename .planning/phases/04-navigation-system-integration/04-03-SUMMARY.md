---
phase: 04-navigation-system-integration
plan: 03
subsystem: android-navigation
tags: [edge-to-edge, insets, predictive-back, window-insets]
dependency_graph:
  requires: [04-01, 04-02]
  provides: [NAV-03, NAV-04]
  affects: [MainActivity, ChatScreen, ConversationsScreen]
tech_stack:
  added: []
  patterns:
    - PredictiveBackHandler for gesture-driven back on API 34+
    - WindowInsets.statusBars explicit param on TopAppBar inside zero-inset Scaffold
    - enableEdgeToEdge() before super.onCreate() for correct window flag setup
key_files:
  created: []
  modified:
    - android/app/src/main/java/run/koto/MainActivity.kt
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - enableEdgeToEdge() must precede super.onCreate() — repositioned from after to before for correct window setup
  - ChatTopBar receives explicit windowInsets=WindowInsets.statusBars because Scaffold has contentWindowInsets=WindowInsets(0)
  - ConversationsScreen Scaffold uses M3 Scaffold defaults for status bar insets — no change needed (TopAppBar gets statusBars automatically)
  - PredictiveBackHandler degrades gracefully on API < 34 — no API version guard needed
metrics:
  duration_minutes: 3
  completed_date: "2026-04-05"
  tasks_completed: 1
  tasks_total: 2
  files_changed: 2
---

# Phase 4 Plan 3: Edge-to-Edge Insets + PredictiveBackHandler Summary

**One-liner:** Edge-to-edge system bar transparency with explicit status bar insets on ChatScreen and predictive back gesture handler via PredictiveBackHandler (Activity 1.10+).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Verify edge-to-edge insets + add PredictiveBackHandler to ChatScreen | 704d1fb | MainActivity.kt, ChatScreen.kt |
| 2 | Visual verification checkpoint | — | Awaiting human verify |

## What Was Built

### Task 1: Edge-to-Edge Insets + PredictiveBackHandler

**MainActivity.kt:**
- Moved `enableEdgeToEdge()` before `super.onCreate()`. Previously it was called after, which could cause a brief flash of system bar backgrounds before window flags took effect. This is a correctness fix for proper window transparency from the first frame.

**ChatScreen.kt:**
- Added `import androidx.activity.compose.PredictiveBackHandler`
- Added `PredictiveBackHandler(enabled = true)` before the Scaffold. On Android 14+ (API 34+), this enables the system's predictive back gesture showing a live preview of the ConversationsScreen behind the chat screen during a slow left-edge swipe. On API < 34 it degrades gracefully to standard back.
- Added `windowInsets = WindowInsets.statusBars` to the `TopAppBar` inside `ChatTopBar`. This is required because the Scaffold has `contentWindowInsets = WindowInsets(0)` (intentional — lets `imePadding()` on the LazyColumn handle keyboard resize), which suppresses automatic inset propagation to the TopAppBar. Without the explicit param, the TopAppBar would render under the status bar with no padding.

**ConversationsScreen.kt:**
- Audited: No change needed. The Scaffold uses Material3 defaults where `TopAppBar` automatically applies `statusBars` padding. The Scaffold passes `contentPadding` (including TopAppBar height + status bar offset) to children via the `padding` lambda. The `LazyColumn` receives this via `Modifier.padding(padding)` — correct M3 behavior with no double-padding.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] enableEdgeToEdge() called after super.onCreate()**
- **Found during:** Task 1 verification
- **Issue:** The plan context stated "enableEdgeToEdge() IS already called in onCreate() before super.onCreate()" but the actual code had it after `super.onCreate()`. This means system bar flags weren't set before the Activity window was attached, potentially causing a brief flash of opaque system bars on first launch.
- **Fix:** Moved `enableEdgeToEdge()` to immediately after `installSplashScreen()` and before `super.onCreate()`.
- **Files modified:** MainActivity.kt
- **Commit:** 704d1fb

### Pre-Existing Out-of-Scope Compile Errors

The following pre-existing compile errors in unrelated files were observed but NOT fixed (out of scope per deviation Rule scope boundary):
- `CryptoManager.kt` — uniffi bindings not generated (requires `cargoBuild` task)
- `AuthRepository.kt`, `ChatRepository.kt` — depend on unresolved uniffi types
- `TypingWaveIndicator.kt`, `SwipeableConversationItem.kt` — unresolved references (`offset`, `matchParentSize`)
- `Typography.kt` — experimental API usage warnings treated as errors

These are deferred to a separate build/infra fix. The three files modified by this plan (MainActivity.kt, ChatScreen.kt, ConversationsScreen.kt) have zero new errors.

## Known Stubs

None — this plan is infrastructure/inset wiring with no UI data stubs.

## Self-Check: PASSED

- `android/app/src/main/java/run/koto/MainActivity.kt` — FOUND
- `android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt` — FOUND
- Commit 704d1fb — FOUND (git log confirms)
- `PredictiveBackHandler` in ChatScreen.kt — FOUND (line 121)
- `enableEdgeToEdge()` in MainActivity.kt — FOUND (line 32)
- `windowInsets = WindowInsets.statusBars` in ChatScreen.kt — FOUND (line 908)
