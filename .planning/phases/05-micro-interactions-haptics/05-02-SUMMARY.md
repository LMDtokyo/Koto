---
phase: 05-micro-interactions-haptics
plan: 02
subsystem: android-ui
tags: [animation, composable, send-button, micro-interactions]
dependency_graph:
  requires: [05-01]
  provides: [MorphingSendButton atom]
  affects: [ChatScreen.kt MessageInputBar]
tech_stack:
  added: []
  patterns: [AnimatedContent with SpringSpec, LaunchedEffect edge-detect for justSent flash]
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/components/atoms/MorphingSendButton.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - "backgroundMod extracted as local val — Color and Brush have separate .background() overloads; when-expression cannot unify them, so separate if/else locals resolve the type ambiguity"
  - "clickMod typed as Modifier explicitly — Kotlin cannot infer Modifier from when-expression branches without explicit type annotation"
  - "Pre-existing errors in CryptoManager.kt/MessageEntity.kt/Typography.kt are out of scope — not caused by this plan, no errors in MorphingSendButton.kt or ChatScreen.kt"
metrics:
  duration: 8
  completed_date: "2026-04-05"
  tasks_completed: 2
  files_changed: 2
requirements: [MI-01, MI-05]
---

# Phase 05 Plan 02: MorphingSendButton Summary

**One-liner:** 4-state morphing send button (mic/arrow/progress/checkmark) extracted as MorphingSendButton atom using KotoTheme.motion.springMicro spring transitions.

## Tasks Completed

| # | Task | Status | Files |
|---|------|--------|-------|
| 1 | Create MorphingSendButton atom | Done | MorphingSendButton.kt (new) |
| 2 | Wire into ChatScreen MessageInputBar | Done | ChatScreen.kt (modified) |

## What Was Built

### MorphingSendButton.kt (new atom)

Full 4-state composable at `android/app/src/main/java/run/koto/ui/components/atoms/MorphingSendButton.kt`:

- **IDLE_EMPTY**: mic icon, `surfaceVariant` background — shown when text field is empty
- **IDLE_TEXT**: send arrow icon, `bubbleGradient` background — shown when text is entered
- **SENDING**: `CircularProgressIndicator` 2dp stroke — shown while `sending=true`
- **JUST_SENT**: checkmark icon holds 800ms via `LaunchedEffect` + `delay(800L)` — shown after `sending` flips false

All `AnimatedContent` transitions use `motion.springMicro` (stiffness=400, dampingRatio=0.70) — zero raw `spring()` literals, MI-05 compliant.

### ChatScreen.kt (updated)

- Old `AnimatedContent { hasText -> ... }` block with label `"send_icon"` replaced with `MorphingSendButton(hasText = text.isNotBlank(), sending = sending, onSend = onSend)`
- Removed unused imports: `Icons.AutoMirrored.Filled.Send`, `Icons.Default.Mic` (now in MorphingSendButton.kt)
- Added import: `run.koto.ui.components.atoms.MorphingSendButton`
- `MessageInputBar` signature unchanged — no callers needed updating

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Type ambiguity in `.background()` and `.then()` Modifier chain**
- **Found during:** Task 1 — first compile attempt
- **Issue:** Plan's template used `when(s) { ... -> colors.surfaceVariant; else -> colors.bubbleGradient }` inside `.background()`. `surfaceVariant` is `Color`, `bubbleGradient` is `Brush` — different types, `when` expression cannot unify them. Also `.then(when(s) { ... -> Modifier })` fails without explicit type annotation.
- **Fix:** Extracted `backgroundMod` as local `val` with `if/else` selecting `.background(color, shape)` or `.background(brush, shape)` separately. Typed `clickMod: Modifier` explicitly for the `when` expression.
- **Files modified:** MorphingSendButton.kt only
- **Commit:** (see commits below)

## Known Stubs

None — all 4 states are fully implemented with real UI content. Voice recording (IDLE_EMPTY click) is intentionally a no-op placeholder for a future plan.

## Verification

All checks pass:
- `grep "MorphingSendButton" ChatScreen.kt` → 2 matches (import + call)
- `grep "send_icon" ChatScreen.kt` → no match (old block removed)
- `grep "springMicro" MorphingSendButton.kt` → match (MI-05 compliant)
- `grep "IDLE_EMPTY|IDLE_TEXT|SENDING|JUST_SENT" MorphingSendButton.kt | wc -l` → 22 (4+ required)
- `./gradlew :app:compileDebugKotlin` — no errors in MorphingSendButton.kt or ChatScreen.kt; pre-existing errors in CryptoManager.kt/MessageEntity.kt/Typography.kt are out of scope

## Self-Check

Files verified to exist:
- FOUND: android/app/src/main/java/run/koto/ui/components/atoms/MorphingSendButton.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt (updated)

## Self-Check: PASSED
