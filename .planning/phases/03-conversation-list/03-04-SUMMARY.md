---
phase: 03-conversation-list
plan: 04
subsystem: android/conversations
tags: [pull-to-refresh, animation, canvas, haptics, spring, compose, conversation-list, blob]
dependency_graph:
  requires: [03-02, 03-03]
  provides: [CL-05, final-ConversationsScreen]
  affects: [ConversationsScreen, ConversationsViewModel]
tech_stack:
  added: []
  patterns:
    - PullToRefreshBox with custom Canvas blob indicator (Material3 1.4+)
    - BoxScope content lambda for PullToRefreshBox compatibility
    - collectAsStateWithLifecycle() (lifecycle-aware StateFlow collection)
    - isRefreshing as separate MutableStateFlow<Boolean> in ViewModel
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/screens/conversations/PullToRefresh.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsViewModel.kt
decisions:
  - "BoxScope content lambda: KotoPullToRefresh accepts @Composable BoxScope.() -> Unit (not () -> Unit) to match PullToRefreshBox's content parameter type"
  - "isRefreshing as separate StateFlow: avoids modifying UiModels.kt ConversationListState to preserve domain model boundaries"
  - "refresh() stubs with delay(1000): chatRepository.refreshConversations() does not exist yet; minimum 1800ms visible refresh time (1000ms sim + 800ms UX floor)"
metrics:
  duration_minutes: 6
  completed_date: "2026-04-05"
  tasks_completed: 2
  files_changed: 3
requirements: [CL-05]
---

# Phase 03 Plan 04: Custom Pull-to-Refresh (CL-05) Summary

**One-liner:** Custom canvas blob indicator pull-to-refresh with primary-color oval→circle morphing, haptic threshold feedback, and spring completion animation.

## What Was Built

### PullToRefresh.kt (new)

`KotoPullToRefresh` wraps Material3's `PullToRefreshBox` with a fully custom `KotoBlobIndicator`:

- **Blob morph**: Canvas draws an oval that stretches from the top edge. As `distanceFraction` increases from 0→1, the blob transitions from a tall narrow oval to a proportional circle using `drawOval()` with dynamic `blobH`/`blobW` calculations.
- **Fade-in**: Alpha ramps from 0→1 during the first 50% of pull to smoothly introduce the blob.
- **Haptic feedback**: `HapticFeedbackConstants.CONFIRM` (API 30+) or `LONG_PRESS` fallback fires exactly once per pull cycle at `distanceFraction >= 1f`.
- **Pulse animation**: `rememberInfiniteTransition` drives a 1.0→1.05 scale pulse at 800ms/cycle during refresh.
- **Completion spring**: `animateFloatAsState` with `Spring.DampingRatioNoBouncy` / `StiffnessMediumLow` shrinks the circle to 0 when `isRefreshing` becomes false.
- All colors via `KotoTheme.colors.primary` — no hardcoded hex values.

### ConversationsViewModel.kt (modified)

- Added `_isRefreshing: MutableStateFlow<Boolean>` + `isRefreshing: StateFlow<Boolean>`.
- Added `fun refresh()`: guards double-invocation, simulates network call with `delay(1000)`, enforces 800ms minimum visible refresh time, always resets `_isRefreshing` in `finally`.

### ConversationsScreen.kt (modified)

- Added `isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()`.
- Wrapped Scaffold content `Box` with `KotoPullToRefresh(isRefreshing, viewModel::refresh)`.
- Replaced all `collectAsState()` with lifecycle-aware `collectAsStateWithLifecycle()` (4 occurrences).

## Integration Summary

All 6 CL requirements are now present in ConversationsScreen:
- **CL-01**: ConversationItem rows (Plan 01)
- **CL-02**: SwipeableConversationItem (Plan 02)
- **CL-03**: PinnedSection header (Plan 02)
- **CL-04**: AnimatedSearchBar (Plan 03)
- **CL-05**: KotoPullToRefresh blob indicator (this plan)
- **CL-06**: OnlineDot pulse (Plan 01)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed PullToRefreshBox content parameter type mismatch**
- **Found during:** Task 1 compilation
- **Issue:** `KotoPullToRefresh` declared `content: @Composable () -> Unit` but `PullToRefreshBox.content` requires `@Composable BoxScope.() -> Unit`
- **Fix:** Changed `KotoPullToRefresh` content parameter to `@Composable BoxScope.() -> Unit`; updated import from `Box` to `BoxScope`
- **Files modified:** `PullToRefresh.kt`
- **Commit:** a0b74dc (included in Task 1 commit)

## Known Stubs

- `refresh()` in ViewModel uses `delay(1000)` instead of `chatRepository.refreshConversations()` — the method does not exist in ChatRepository yet. The stub produces correct UX behavior (visible refresh cycle) but does not trigger a real network reload. Will be wired when the data layer phase adds the method.

## Self-Check: PASSED

- [x] `PullToRefresh.kt` exists: FOUND
- [x] `ConversationsScreen.kt` modified: FOUND
- [x] `ConversationsViewModel.kt` modified: FOUND
- [x] Commit a0b74dc exists: FOUND
- [x] Commit 5603510 exists: FOUND
- [x] No legacy tokens in conversations package: VERIFIED
- [x] No hardcoded hex in ConversationsScreen: VERIFIED
- [x] KotoPullToRefresh present in ConversationsScreen: VERIFIED (1 occurrence)
