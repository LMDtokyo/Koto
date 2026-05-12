---
phase: 05-micro-interactions-haptics
plan: "04"
subsystem: android-ui
tags: [haptics, animation, micro-interactions, chat, kotlin, compose]
dependency_graph:
  requires: [05-01]
  provides: [HeartReaction composable, KotoHaptics instance class, rememberKotoHaptics]
  affects: [ChatScreen.kt, Haptics.kt, ReactionParticleOverlay.kt]
tech_stack:
  added: []
  patterns:
    - Spring-physics heart reaction (springBouncy via KotoTheme.motion)
    - Lambda-based Modifier.offset for layout-phase reads (PITFALLS P3)
    - Instance-based haptics class with rememberKotoHaptics() Compose factory
    - Single-fire threshold crossing tracking with crossedThreshold flag
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/components/atoms/ReactionParticleOverlay.kt
  modified:
    - android/app/src/main/java/run/koto/ui/theme/Haptics.kt
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - KotoHaptics refactored from object with (view: View) params to class with View constructor + rememberKotoHaptics() factory — cleaner Compose integration, eliminates repetitive LocalView.current lookups at call sites
  - onDoubleTap() maps to CLOCK_TICK (not CONFIRM) — subtle acknowledgement of gesture before animation plays; onReactionSettle() maps to CONFIRM — stronger signal when heart settles
  - offsetY stored in pixels (not dp) to avoid Dp->px conversion inside layout lambda; targetOffsetPx computed once at composition time
  - crossedThreshold state in SwipeToReplyContainer uses onDragStart reset so haptic can re-fire on next gesture
metrics:
  duration: 18
  completed_date: "2026-04-05"
  tasks_completed: 2
  files_modified: 3
---

# Phase 05 Plan 04: Double-Tap Heart Reaction + Haptics Wiring Summary

Heart reaction animation (MI-02) and all 5 haptic touch points (MI-03) wired into ChatScreen using spring physics overlay composable and KotoHaptics instance class.

## What Was Built

### Task 1: HeartReaction atom + KotoHaptics refactor

**ReactionParticleOverlay.kt** — New composable that runs a complete lifecycle then calls `onFinished()`:
- Phase 1: `Animatable(0f)` springs to `1f` via `motion.springBouncy` (natural overshoot to ~1.2 then settles)
- Phase 2: concurrent float up `-24dp` (stored as px) + fade to `0f` over `600ms` tween
- `onReactionSettle()` fires at phase boundary (when spring settles) — caller invokes `haptics.onReactionSettle()`
- `Modifier.offset { IntOffset(0, offsetY.value.roundToInt()) }` — lambda form defers read to layout phase (PITFALLS P3)

**Haptics.kt refactor** — `object KotoHaptics` with `(view: View)` params → `class KotoHaptics(view: View)` with zero-arg methods + `rememberKotoHaptics()` composable factory. Added `onReactionSettle()` method (CONFIRM API 30+ / CONTEXT_CLICK fallback).

### Task 2: ChatScreen wiring

5 haptic touch points wired:

| Method | Location | Trigger |
|---|---|---|
| `haptics.onSend()` | `onSend` lambda | Before `viewModel.sendMessage()` |
| `haptics.onLongPress()` | `onLongPress` lambda | Before `contextMenuTarget = msg` |
| `haptics.onDoubleTap()` | `MessageRow.combinedClickable.onDoubleClick` | On double-tap detection |
| `haptics.onReactionSettle()` | `HeartReaction.onReactionSettle` callback | When spring settles at 100% scale |
| `haptics.onSwipeThreshold()` | `SwipeToReplyContainer.onHorizontalDrag` | First crossing of 48dp threshold |

Double-tap flow:
1. `combinedClickable(onDoubleClick = { haptics.onDoubleTap(); showHeart = true })`
2. `HeartReaction` overlaid at `Alignment.Center` inside bubble `Box`
3. Spring-in → `onReactionSettle()` → float+fade → `onFinished()` → `showHeart = false`

`SwipeToReplyContainer` gets `haptics: KotoHaptics` param; `crossedThreshold` flag ensures single haptic fire per gesture (resets on `onDragStart` and `onDragCancel`/`onDragEnd`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KotoHaptics was an object, not an instance class**
- **Found during:** Task 2 setup — plan references `rememberKotoHaptics()` and no-arg `haptics.onSend()` style
- **Issue:** Plan 01 delivered `object KotoHaptics` with `fun onSend(view: View)` static methods; plan 04 requires instance-based `haptics.onSend()` and `rememberKotoHaptics()`
- **Fix:** Refactored `object KotoHaptics` to `class KotoHaptics(private val view: View)` with no-arg methods; added `rememberKotoHaptics()` composable via `LocalView.current`; also added missing `onReactionSettle()` method
- **Files modified:** `android/app/src/main/java/run/koto/ui/theme/Haptics.kt`
- **Commit:** 8211973

**2. [Rule 1 - Bug] ReactionParticleOverlay import issue with lambda-based offset**
- **Found during:** Task 1 — first compilation attempt
- **Issue:** `Modifier.offset { IntOffset(...) }` lambda overload requires explicit import from `androidx.compose.foundation.layout.offset`; `offsetY.value.dp.roundToPx()` inside lambda was incorrect — lambda provides `Density` scope but `Dp.roundToPx()` expects explicit receiver
- **Fix:** Added explicit `import androidx.compose.foundation.layout.offset`; changed to store offset in px (computed once via `with(density) { (-24).dp.toPx() }`); lambda reads raw Float via `offsetY.value.roundToInt()`
- **Files modified:** `android/app/src/main/java/run/koto/ui/components/atoms/ReactionParticleOverlay.kt`
- **Commit:** 8211973

## Verification

```
grep "springBouncy" ReactionParticleOverlay.kt → match
grep "spring(" ReactionParticleOverlay.kt → only in comment, no raw spring() calls
grep "haptics.on" ChatScreen.kt → 5 call sites (onSend, onLongPress, onDoubleTap, onReactionSettle, onSwipeThreshold)
grep "rememberKotoHaptics" ChatScreen.kt → match
grep "HeartReaction" ChatScreen.kt → match
grep "onDoubleClick" ChatScreen.kt → match
Compilation errors in ChatScreen.kt/ReactionParticleOverlay.kt/Haptics.kt → 0
```

Pre-existing build errors in other files (TypingWaveIndicator.kt, SwipeableConversationItem.kt, ChatRepository.kt, Typography.kt) are unrelated to this plan and not caused by these changes.

## Known Stubs

None — all features are fully implemented per plan spec.

## Self-Check: PASSED
