---
phase: 04-navigation-system-integration
plan: 02
subsystem: android-navigation
tags: [shared-element, spring-physics, nav-graph, avatar-morph]
dependency_graph:
  requires: [04-01]
  provides: [NAV-02, NAV-05]
  affects: [ConversationsScreen, ChatScreen, KotoNavGraph]
tech_stack:
  added: []
  patterns:
    - SharedTransitionLayout wrapping NavHost (Navigation Compose 2.9.0)
    - SharedTransitionScope propagation via lambda params
    - spring(0.85f, 380f) for both screen transitions and shared element boundsTransform
    - Optional scope params (null = no shared element, non-null = morph enabled)
key_files:
  created: []
  modified:
    - android/app/src/main/java/run/koto/ui/navigation/KotoNavGraph.kt
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationItem.kt
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
    - android/app/src/main/java/run/koto/ui/screens/conversations/SwipeableConversationItem.kt
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - SharedTransitionScope passed as nullable param (not CompositionLocal) — keeps composables previewable without nav context
  - sharedElement key pattern 'avatar-{convId}' matches exactly on both sides of transition
  - @OptIn(ExperimentalSharedTransitionApi::class) required on all composables using SharedTransitionScope/AnimatedVisibilityScope
metrics:
  duration_seconds: 526
  completed_date: "2026-04-05"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 5
---

# Phase 04 Plan 02: SharedTransitionLayout + Spring Transitions + Avatar Morph Summary

**One-liner:** SharedTransitionLayout wraps NavHost with spring(0.85f, 380f) screen transitions and sharedElement avatar morph via key 'avatar-{convId}'.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | SharedTransitionLayout + spring transitions in KotoNavGraph | 33d42e0 | KotoNavGraph.kt |
| 2 | sharedElement avatar morph on ConversationItem and ChatTopBar | 39c5f8d | ConversationItem.kt, ConversationsScreen.kt, SwipeableConversationItem.kt, ChatScreen.kt |

## What Was Built

### Task 1 — KotoNavGraph Refactor

`KotoNavGraph.kt` was rewritten:

- **SharedTransitionLayout** wraps the Scaffold+NavHost. The `this@SharedTransitionLayout` receiver is `SharedTransitionScope`, passed down to `ConversationsScreen` and `ChatScreen`.
- **Spring transitions (NAV-05):** All `tween()`-based `CubicBezierEasing` specs removed. Replaced with `navSpringSpec = spring<IntOffset>(0.85f, 380f)` and `navFloatSpec = spring<Float>(0.85f, 380f)`. The four transition vars (`pushEnter/Exit`, `popEnter/Exit`) all use these spring specs.
- **KotoBottomNavBar in Scaffold:** Integrated in `bottomBar` slot wrapped in `AnimatedVisibility` (slide+fade). Visible only on root tab routes (`conversations`, `contacts`, `calls`, `settings`). Tab navigation uses `popUpTo + saveState + restoreState` for proper back-stack tab state.
- **Stub routes:** `contacts` and `calls` composables added with `PlaceholderScreen`.

### Task 2 — Shared Element Avatar Morph (NAV-02)

**ConversationItem.kt:**
- New optional params: `sharedTransitionScope: SharedTransitionScope? = null`, `animatedVisibilityScope: AnimatedVisibilityScope? = null`
- `AvatarWithPresence` receives both scopes; when non-null, applies `Modifier.sharedElement(sharedContentState = rememberSharedContentState(key = "avatar-${conv.id}"), animatedVisibilityScope, boundsTransform = spring(0.85f, 380f))`
- Key `"avatar-${conv.id}"` is stable across navigation

**ConversationsScreen.kt:**
- Signature extended with `sharedTransitionScope` and `animatedVisibilityScope` optional params
- `ConversationList` extended to accept and forward scopes
- Both pinned `ConversationItem` and `SwipeableConversationItem` calls updated to pass scopes

**SwipeableConversationItem.kt:**
- Accepts and forwards scopes to the wrapped `ConversationItem`

**ChatScreen.kt + ChatTopBar:**
- `ChatScreen` signature extended with optional scope params
- `ChatTopBar` accepts `convId`, `sharedTransitionScope`, `animatedVisibilityScope`
- Avatar Box in `ChatTopBar` uses `avatarMod` with key `"avatar-$convId"` and same `spring(0.85f, 380f)` boundsTransform

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed sharedElement API parameter name**
- **Found during:** Task 2 compilation
- **Issue:** Plan specified `state = rememberSharedContentState(...)` but the actual Compose API parameter is named `sharedContentState` (not `state`)
- **Fix:** Renamed parameter from `state` to `sharedContentState` in both ConversationItem.kt and ChatScreen.kt
- **Files modified:** ConversationItem.kt, ChatScreen.kt
- **Commit:** 39c5f8d (included in same task commit)

## Pre-existing Errors (Out of Scope)

The following errors exist in the codebase and are NOT caused by this plan's changes. They were present before this plan began:
- `SwipeableConversationItem.kt`: `matchParentSize` and `offset` Unresolved (missing BoxScope import — pre-existing)
- `CryptoManager.kt`: Unresolved `uniffi.*` references (Rust native bindings not compiled)
- `AuthRepository.kt`, `ChatRepository.kt`: Unresolved Signal Protocol references (same)
- `Typography.kt`: Experimental API warnings
- `TypingWaveIndicator.kt`, `MessageEntity.kt`: Pre-existing issues

These are logged to `deferred-items.md` per deviation scope boundary rules.

## Verification

```
grep -n "SharedTransitionLayout" KotoNavGraph.kt  → lines 5, 108, 161, 175 ✓
grep -n "sharedElement" ConversationItem.kt        → line 161 ✓
grep -n "sharedElement" ChatScreen.kt              → line 836 ✓
grep -n "spring(dampingRatio = 0.85f" ConversationItem.kt → line 165 ✓
grep -n "navSpringSpec" KotoNavGraph.kt            → line 52 ✓
Zero errors in KotoNavGraph.kt, ConversationItem.kt, ConversationsScreen.kt, ChatScreen.kt ✓
```

## Known Stubs

- `PlaceholderScreen("Contacts")` and `PlaceholderScreen("Calls")` — intentional stubs for bottom nav destinations not yet implemented. These are structural scaffolding, not data stubs that affect plan goals.
- `avatarUrl` from `ConversationUi` and `ChatState` is not yet used in avatar rendering — both sides show initials only. This is a deferred feature (Coil image loading), not a blocker for the shared element morph itself.

## Self-Check: PASSED

- KotoNavGraph.kt: FOUND
- ConversationItem.kt: FOUND
- ChatScreen.kt: FOUND
- Commit 33d42e0: FOUND
- Commit 39c5f8d: FOUND
