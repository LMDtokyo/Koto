---
phase: 02-chat-screen
plan: "04"
subsystem: android-ui
tags: [gestures, animations, context-menu, swipe-to-reply, compose]
dependency_graph:
  requires: [02-02, 02-03]
  provides: [CH-05, CH-06]
  affects: [ChatScreen.kt]
tech_stack:
  added: []
  patterns:
    - detectHorizontalDragGestures with 48dp threshold and spring snap-back
    - Box overlay context menu (not Dialog) with AnimatedVisibility spring entry
    - RenderEffect.createBlurEffect gated by Build.VERSION_CODES.S (API 31+)
    - combinedClickable for long-press detection on message bubbles
    - derivedStateOf for gesture progress to minimize recomposition
key_files:
  created: []
  modified:
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - SwipeToReplyContainer uses detectHorizontalDragGestures (not AnchoredDraggable) — correctly handles direction locking without stealing scroll events from LazyColumn
  - MediaMessageContent stub retained (linter-injected) with placeholder Box — full Coil/BlurHash impl deferred to Plan 05
  - ScrollToBottomFab composable retained (linter-injected) — provides CH-08 partial implementation as bonus
metrics:
  duration: "~45 min"
  completed: "2026-04-05"
  tasks_completed: 2
  files_modified: 1
---

# Phase 02 Plan 04: Swipe-to-Reply and Context Menu Gestures Summary

**One-liner:** Gesture-driven message interaction — swipe-to-reply with spring snap-back (detectHorizontalDragGestures) and Box-overlay context menu with API 31+ blur backdrop.

## What Was Built

### Task 1: SwipeToReplyContainer (CH-05)

Added `SwipeToReplyContainer` private composable to `ChatScreen.kt` that wraps each `MessageRow` in the `LazyColumn`. The composable:

- Uses `detectHorizontalDragGestures` for direction-locked horizontal swipe detection
- Threshold: 48dp (converted to pixels via `LocalDensity`)
- Max drag: threshold × 1.5 (rubber-band resistance past threshold)
- Spring snap-back on release: `spring(dampingRatio=0.6f, stiffness=400f)`
- `onDragEnd`: calls `onReply()` if offsetX >= threshold, then always animates back to 0
- Reply affordance icon (ArrowBack in CircleShape) fades in as `progress` (0→1) increases
- Message content translated via lambda-based `offset { IntOffset(...) }` for layout-phase read

Wired `SwipeToReplyContainer` wrapping each `ChatItem.Message` in `ChatMessageList.items` block. Added `combinedClickable(onLongClick = { onLongPress(msg.id) })` to the inner `Column` in `MessageRow`.

Updated reply preview strip in `MessageInputBar` with:
- Primary-color 3dp left accent bar (`Box` with `width(3.dp)`)
- "Ответ" label in `colors.primary`
- Quoted text in `colors.onSurfaceLow` with ellipsis overflow
- Close icon button (32dp)

### Task 2: ContextMenuOverlay (CH-06)

Added context menu system:

**`onLongPress` lambda** now sets `contextMenuTarget` state by searching `chatItems` for the message by ID.

**Box overlay** inside `ChatScreen`'s Box container (not Dialog):
- API 31+ blur: `RenderEffect.createBlurEffect(20f, 20f, CLAMP).asComposeRenderEffect()` via `graphicsLayer { renderEffect = ... }`
- API < 31 fallback: `Color.Black.copy(alpha = 0.40f)` background
- Backdrop is `clickable { contextMenuTarget = null }` to dismiss on outside tap

**`AnimatedVisibility`** with:
- Enter: `scaleIn(spring(dampingRatio=0.65f, stiffness=380f), TransformOrigin(0.5f, 0.5f)) + fadeIn(tween(80))`
- Exit: `scaleOut(tween(120)) + fadeOut(tween(80))`

**`ContextMenuCard`** — `Surface` with `KotoTheme.shapes.md`, `tonalElevation=8dp`:
- Message preview text (60 char truncation)
- `HorizontalDivider`
- Reply, Copy, Delete actions via `ContextMenuItem`

**`ContextMenuItem`** — `Row` with `sizeIn(minHeight = 48.dp)` (AC2 accessibility), icon + label.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] TransformOrigin import was in wrong package**
- **Found during:** Task 2 compilation
- **Issue:** Plan specified `androidx.compose.animation.core.TransformOrigin` but the class is in `androidx.compose.ui.graphics.TransformOrigin`
- **Fix:** Changed import to `androidx.compose.ui.graphics.TransformOrigin`
- **Files modified:** ChatScreen.kt

**2. [Rule 3 - Blocking] Linter-injected broken MediaMessageContent function**
- **Found during:** Task 1/2 compilation (linter kept re-adding a broken implementation)
- **Issue:** A linter process injected `MediaMessageContent` with `decodeBlurHash`, `AsyncImage.placeholder()`, `Coil ImageRequest` that used wrong API versions and missing helper functions
- **Fix:** Replaced with a minimal stub `Box` placeholder. Media implementation deferred to Plan 05.
- **Files modified:** ChatScreen.kt

**3. [Rule 3 - Blocking] Linter-injected ScrollToBottomFab call without matching function**
- **Found during:** Compilation
- **Issue:** Linter added a `ScrollToBottomFab(...)` call in the Box but the private composable wasn't present yet
- **Fix:** Linter also added the `ScrollToBottomFab` private function — retained as it provides a usable partial CH-08 implementation. Import `KeyboardArrowDown` added.
- **Files modified:** ChatScreen.kt

### Pre-existing Out-of-Scope Build Errors (deferred)

The following files have pre-existing errors unrelated to this plan:
- `CryptoManager.kt` — uniffi bindings not generated (requires Rust build via `./gradlew cargoBuild`)
- `ConversationEntity.kt`, `MessageEntity.kt` — entity/model field mismatches
- `AuthRepository.kt`, `ChatRepository.kt` — crypto binding dependencies
- `TypingWaveIndicator.kt` — `offset` import ambiguity  
- `ConversationsScreen.kt` — model field mismatches
- `Typography.kt` — experimental API treated as errors

ChatScreen.kt itself has zero compilation errors.

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `MediaMessageContent` returns placeholder Box | ChatScreen.kt | Coil 3.x AsyncImage + BlurHash decode deferred to Plan 05 |
| `onCopy` in context menu: `/* TODO: clipboard */` | ChatScreen.kt | Clipboard API implementation deferred |
| `onDelete` in context menu: `/* TODO: delete */` | ChatScreen.kt | ViewModel delete method deferred |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| ChatScreen.kt exists and contains SwipeToReplyContainer | FOUND |
| ChatScreen.kt exists and contains ContextMenuCard | FOUND |
| 02-04-SUMMARY.md exists | FOUND |
| ChatScreen.kt has zero compilation errors | CONFIRMED |
| No Dialog or ModalBottomSheet usage | CONFIRMED |
| No git repo present — commits not applicable | N/A |
