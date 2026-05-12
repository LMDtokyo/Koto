---
phase: 02-chat-screen
plan: 05
subsystem: android/ui/chat
tags: [media, blurhash, coil, fab, animation, derivedStateOf, compose]
dependency_graph:
  requires: [02-02, 02-03]
  provides: [CH-07, CH-08]
  affects: [ChatScreen, MessageBubble]
tech_stack:
  added: [BlurHashDecoder pure Kotlin, Coil 3.x AsyncImage placeholder]
  patterns: [derivedStateOf for scroll state, springBouncy scaleIn FAB animation, BlurHash bitmap decode]
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/screens/chat/BlurHashDecoder.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - MediaMessageContent uses Painter placeholder directly on AsyncImage composable (not ImageRequest.Builder.placeholder) — Coil 3.x Compose API uses placeholder: Painter? parameter on AsyncImage
  - BlurHash decoded via pure Kotlin with no external deps; fallback is solid #1E1E2E (KotoPalette.surface1)
  - ScrollToBottomFab uses derivedStateOf to avoid recomposition on every scroll pixel — only triggers when showFab boolean flips
  - springBouncy from KotoTheme.motion used for FAB scaleIn entrance — consistent with motion system
  - FAB scrolls to index 0 (not lastIndex) because reverseLayout=true means 0 is the newest/bottom message
metrics:
  duration: ~25min
  completed: 2026-04-05
  tasks_completed: 2
  files_changed: 2
---

# Phase 2 Plan 05: Media Preview + Scroll-to-Bottom FAB Summary

BlurHash decoder in pure Kotlin (~120 lines, zero external deps) + Coil 3 AsyncImage media preview with BlurHash placeholder + scroll-to-bottom FAB with spring animation and derivedStateOf.

## Tasks Completed

| # | Task | Status |
|---|------|--------|
| 1 | Create BlurHashDecoder.kt — pure Kotlin BlurHash implementation (CH-07) | Done |
| 2 | Add MediaMessageContent + ScrollToBottomFab to ChatScreen (CH-07, CH-08) | Done |

## What Was Built

### Task 1: BlurHashDecoder.kt (CH-07)

New file at `android/app/src/main/java/run/koto/ui/screens/chat/BlurHashDecoder.kt`:

- `decodeBlurHash(hash: String, width: Int, height: Int): Bitmap` — public entry point
- `decode83()` — Base83 digit decoding
- `decodeDC()` / `decodeAC()` — DC and AC component extraction from BlurHash integer
- `srgbToLinear()` / `linearToSrgb()` — gamma correction for correct color rendering
- `signedPow2()` — AC component magnitude helper
- `solidFallback()` — returns a solid #1E1E2E Bitmap when hash is invalid/malformed

No external dependencies. Pure Kotlin math. ~120 lines total.

### Task 2: ChatScreen.kt changes (CH-07, CH-08)

**MediaMessageContent composable:**
- Placed at `// ─── Media Preview ───` section between ReplyPreviewChip and bubbleShape
- Uses `remember(blurHash)` to cache decoded BlurHash Bitmap (avoids recomputation on recompose)
- BlurHash decoded at 32x32px (efficient thumbnail for placeholder)
- Falls back to `ColorPainter(colors.surfaceVariant)` on null blurHash or decode exception
- `AsyncImage` with `placeholder = painter`, `crossfade(300)`, `heightIn(max=280.dp)`, `clip(shapes.md)`

**MessageBubble wiring (CH-07):**
- Added `if (msg.mediaUrl != null)` block BEFORE the text `Text()` composable
- Added spacer between media and text only when both are present
- Wrapped text in `if (msg.text.isNotBlank())` to avoid empty Text when media-only message

**ScrollToBottomFab composable (CH-08):**
- `derivedStateOf { listState.firstVisibleItemIndex > 2 }` — FAB shows when user scrolls up 3+ items
- `AnimatedVisibility` with `scaleIn(motion.springBouncy) + fadeIn(tween(80))` entrance
- `FloatingActionButton` with `shape = KotoTheme.shapes.pill` (CircleShape), size 48dp
- `Badge` with `containerColor = colors.primary` shown only when `unreadBelowFold > 0`
- Shows "99+" when count exceeds 99

**FAB wiring in ChatScreen Box:**
- Placed after `ChatMessageList` inside the Box overlay
- `onScrollToBottom = { scope.launch { listState.animateScrollToItem(0) } }` — index 0 = bottom (reverseLayout=true)
- Positioned at `Alignment.BottomEnd` with `padding(end=16.dp, bottom=16.dp)`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - API Correction] Coil 3 AsyncImage placeholder API**
- **Found during:** Task 2, Change A
- **Issue:** Plan spec showed `.placeholder(BitmapPainter(...))` as `ImageRequest.Builder` method, but in Coil 3.x Compose, `ImageRequest.Builder.placeholder()` takes `Drawable`, not `Painter`. The `Painter` placeholder goes directly on `AsyncImage(placeholder = painter)`.
- **Fix:** Used `placeholder = placeholder` as a parameter on `AsyncImage` composable (Coil 3 Compose API)
- **Files modified:** ChatScreen.kt

**2. [Rule 3 - Pre-existing Stub] MediaMessageContent stub existed from previous plan**
- **Found during:** Task 2
- **Issue:** Plan 03 or 04 had scaffolded a `MediaMessageContent` stub with `"[ media ]"` placeholder text and comment "deferred to Plan 05"
- **Fix:** Replaced stub with full implementation (AsyncImage + BlurHash)
- **Files modified:** ChatScreen.kt

## Known Stubs

None — both CH-07 and CH-08 are fully implemented. No placeholder text or TODO stubs remain in the modified files.

## Self-Check

- [x] `BlurHashDecoder.kt` exists with `decodeBlurHash()`, `decode83()`, `Bitmap.createBitmap`, `BASE83_CHARS`
- [x] `MediaMessageContent` composable present in ChatScreen.kt using `AsyncImage` + `decodeBlurHash`
- [x] `ScrollToBottomFab` present in ChatScreen.kt with `derivedStateOf` and `springBouncy`
- [x] FAB wired in Box with `animateScrollToItem(0)`
- [x] All new imports added (coil3.*, asImageBitmap, BitmapPainter, ContentScale, LocalContext)

## Self-Check: PASSED

All files created/modified as specified. Gradle compile verification was not possible (Bash access not available in this execution environment) — manual compile verification required before merging.
