---
phase: 02-chat-screen
plan: "02"
subsystem: android-ui
tags: [chat-screen, bubbles, animations, material-icons, read-receipts]
dependency_graph:
  requires: [02-01]
  provides: [gradient-bubbles, bubble-grouping, animated-read-receipts]
  affects: [ChatScreen.kt]
tech_stack:
  added: [material-icons-extended, Icons.Default.DoneAll, Icons.Default.Schedule, Icons.Default.Check, Icons.Default.ErrorOutline]
  patterns: [AnimatedContent, bubbleShape-helper, RoundedCornerShape-grouping, Brush.background-gradient]
key_files:
  modified:
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - "bubbleShape() implemented as non-composable private function (not inline) to avoid recomposition overhead — returns Shape, called from MessageBubble composable"
  - "ReplyPreviewChip stub added to MessageBubble to scaffold CH-05 structure (Plan 04 delivers full implementation)"
  - "ReadReceiptIcon uses Icons.Default.DoneAll (available via material-icons-extended) — no fallback needed"
  - "AnimatedContent label='read_receipt' for tooling/tracing clarity"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-04-05"
  tasks_completed: 2
  files_modified: 1
---

# Phase 02 Plan 02: Gradient Bubbles, Grouping, and Animated Read Receipts Summary

**One-liner:** Violet diagonal gradient sent bubbles with 4-corner grouping logic and AnimatedContent read receipts morphing through 5 states using Material Icons.

## What Was Built

### Task 1: MessageBubble — gradient + grouping-aware implementation (CH-01, CH-02)

The structural skeleton `MessageBubble` from Plan 01 was replaced with the full premium implementation:

- **Gradient background (CH-01):** Outgoing bubbles use `Modifier.background(colors.bubbleGradient)` — the `Brush.linearGradient` token from `KotoColors` (#7C3AED → #5B21B6 diagonal). Incoming bubbles use `colors.bubbleIn` flat color.
- **Grouping-aware shape (CH-02):** `bubbleShape(isOutgoing, showTail)` selects one of 4 `RoundedCornerShape` configurations based on direction and group position:
  - Outgoing + tail: `(18, 18, 4, 18)` — tail at bottomEnd
  - Outgoing + middle: `(18, 5, 5, 18)` — compact sender-side corners
  - Incoming + tail: `(18, 18, 18, 4)` — tail at bottomStart
  - Incoming + middle: `(5, 18, 18, 5)` — compact sender-side corners
- **ReplyPreviewChip stub:** Scaffolds the CH-05 structure — renders `replyTo` text when present with translucent background. Full implementation deferred to Plan 04.
- **Time row alignment:** `Modifier.align(Alignment.End)` ensures time + receipt row is right-aligned inside the bubble column.

### Task 2: ReadReceiptIcon — AnimatedContent morphing implementation (CH-03)

The Text-based stub was replaced with a full `AnimatedContent` implementation:

- **AnimatedContent keyed on MessageStatus** — transitions fade in/out (200ms in, 100ms out) via `fadeIn(tween(200)) togetherWith fadeOut(tween(100))`
- **5 distinct states with icons:**
  - `SENDING` → `Icons.Default.Schedule` (clock), 45% alpha
  - `SENT` → `Icons.Default.Check`, 55% alpha
  - `DELIVERED` → `Icons.Default.DoneAll`, 70% alpha
  - `READ` → `Icons.Default.DoneAll`, `colors.primary` violet tint (CH-03)
  - `FAILED` → `Icons.Default.ErrorOutline`, `colors.error` red tint
- **Size:** 14dp Icon with semantic `contentDescription` for accessibility
- **Icons source:** `material-icons-extended` dependency (confirmed in `libs.versions.toml` + `build.gradle.kts`)

## Deviations from Plan

### Pre-existing issues (out of scope, logged for deferred-items)

The Gradle compile check (`./gradlew :app:compileDebugKotlin`) revealed two pre-existing errors unrelated to plan 02-02:

1. `ConversationsScreen.kt:223,236` — `lastMessageTime` type mismatch and `lastMessagePreview` unresolved reference (old legacy model field names)
2. `Typography.kt:20,24,28,32` — experimental API warnings treated as errors

**ChatScreen.kt itself compiled with zero errors.** These are pre-existing issues from legacy screens — out of scope per deviation rules scope boundary. Logged to deferred-items.

### Auto-added features (Rule 2 — missing critical functionality)

None beyond plan specification.

## Known Stubs

- `ReplyPreviewChip` — renders up to 80 characters of `replyTo.text` with no sender attribution, collapse/expand, or tap-to-scroll behavior. Full quoted reply chip implementation is Plan 04 (CH-05). The stub is intentional and correct per plan design.

## Self-Check: PASSED

- `colors.bubbleGradient` present in ChatScreen.kt: YES (line 263, 609)
- `fun bubbleShape` present: YES (line 346)
- `showTail` references: 7 (lines 198, 250, 340, 341, 346, 351, 356)
- `replyTo` references: YES (lines 270, 271, 273)
- `AnimatedContent` in ReadReceiptIcon: YES (line 373)
- `MessageStatus.READ` with `colors.primary`: YES (lines 396-399)
- `fadeIn(tween(200))`: YES (line 376)
- ChatScreen.kt errors in compile: 0 (pre-existing errors in other files only)
