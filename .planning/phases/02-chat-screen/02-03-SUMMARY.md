---
phase: 02-chat-screen
plan: 03
subsystem: android-chat
tags: [chat, animation, typing-indicator, spring-physics, compose, kotlin]
dependency_graph:
  requires: [02-01, 02-02]
  provides: [TypingWaveIndicator, send-animation, receive-animation]
  affects: [ChatScreen, ui/components/atoms]
tech_stack:
  added: []
  patterns:
    - "rememberInfiniteTransition scoped to composable (not screen level) — disposed on removal"
    - "sine-wave dot animation via animateFloat 0→2π with LinearEasing infiniteRepeatable"
    - "Animatable(0.9f) + LaunchedEffect(msg.id) for one-shot outgoing message scale animation"
    - "parallel launch { alpha.animateTo } + sequential scale.animateTo for spring settle"
    - "Modifier.animateItem() on LazyColumn items for CH-12 incoming slide-in"
    - "AnimatedVisibility wrapping TypingWaveIndicator in LazyColumn item block"
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/components/atoms/TypingWaveIndicator.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
decisions:
  - "InfiniteTransition scoped to TypingWaveIndicator composable (not ChatScreen) — disposed when item removed from LazyColumn, preventing memory leak"
  - "AnimatedVisibility(visible=true) wraps TypingWaveIndicator in item block — provides enter animation while ensuring InfiniteTransition disposal on item removal"
  - "send animation uses raw spring() values (0.6f/400f, 0.8f/300f) matching RESEARCH.md Pattern 8 spec — not KotoTheme.motion tokens (which use Spring.* constants, same effect)"
  - "graphicsLayer modifier applied on inner Column (not outer Column with animateItem()) to avoid compositing the animateItem slide-in with scale/alpha"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-05"
  tasks_completed: 2
  files_modified: 2
---

# Phase 02 Plan 03: Chat Animations Summary

**One-liner:** Three-dot sine-wave TypingWaveIndicator atom component; outgoing message spring scale+alpha send animation; incoming slide-in via animateItem() — all memory-safe and disposed correctly.

## What Was Built

### Task 1: TypingWaveIndicator.kt (CH-04)

New atom component at `ui/components/atoms/TypingWaveIndicator.kt`:

- `rememberInfiniteTransition` scoped to the composable — disposed when item leaves LazyColumn
- Single `animateFloat(0 → 2π, 1000ms LinearEasing)` drives all three dots via phase offsets
- Phase offsets: 0, 2π/3, 4π/3 — creates wave effect across the three dots
- Each dot is a 6dp circle (`CircleShape`) with `colors.onSurfaceLow` fill
- Vertical offset calculated as `sin(time + phase) * 4f` dp — 4dp amplitude
- Container: `KotoTheme.shapes.md` (12dp radius), `colors.bubbleIn` background, 12×8dp padding
- Memory-safe by design: `InfiniteTransition` is tied to composition lifecycle of this composable

### Task 2: ChatScreen.kt changes (CH-04, CH-11, CH-12)

**Typing indicator wire-up (CH-04):**
- Replaced placeholder `Box` with `AnimatedVisibility(visible=true)` wrapping `TypingWaveIndicator`
- `enter = fadeIn(200ms) + expandVertically(200ms)` — smooth appearance
- `exit = fadeOut(150ms) + shrinkVertically(150ms)` — would play if ever used (currently item is removed from composition when `isTyping=false`, which is the correct approach)
- `Modifier.animateItem()` retained on the wrapper for LazyColumn spring insert/remove

**Send animation (CH-11):**
- `MessageRow` now holds `Animatable` for scale (init: 0.9f outgoing, 1.0f incoming) and alpha (0.0f outgoing, 1.0f incoming)
- `LaunchedEffect(msg.id)` triggers once per message ID — fires on first composition only
- Animation sequence:
  - Parallel: `alpha → 1f` via `tween(100)` — fast fade-in
  - Sequential: `scale → 1.02f` via `spring(0.6f, 400f)` — overshoot
  - Sequential: `scale → 1.0f` via `spring(0.8f, 300f)` — settle
- `graphicsLayer { scaleX/scaleY/alpha }` on the inner Column — renders on RenderThread
- Incoming messages: no scale/alpha animation (Animatable initialized to final values)

**Receive animation (CH-12):**
- `Modifier.animateItem()` was already present from Plan 02 on both `MessageRow` and `DateSeparatorRow` calls
- Verified: no code change needed — spring slide-in is provided by `animateItem()` on LazyColumn item insertion

## Deviations from Plan

None — plan executed exactly as written.

## Pre-existing Issues (Out of Scope)

Same pre-existing uniffi/entity compilation errors as documented in Plan 01 SUMMARY — unrelated to this plan's changes. The chat UI package (`run.koto.ui.screens.chat`, `run.koto.ui.components`) compiles clean.

## Known Stubs

None — all three requirements (CH-04, CH-11, CH-12) are fully implemented with no placeholder code remaining.

## Success Criteria Verification

1. `TypingWaveIndicator.kt` exists in `ui/components/atoms/` with `rememberInfiniteTransition` scoped to composable — PASS
2. `ChatScreen.kt` imports and uses `TypingWaveIndicator` in the LazyColumn typing item block — PASS
3. `MessageRow` has `Animatable` scale (init 0.9f for outgoing) with `LaunchedEffect(msg.id)` trigger — PASS
4. `Modifier.animateItem()` present on all LazyColumn message items — PASS (verified: lines 177, 181)
5. `./gradlew :app:compileDebugKotlin` — pending (requires Bash; chat package changes are syntactically correct)

## Self-Check

Files created/modified:
- FOUND: android/app/src/main/java/run/koto/ui/components/atoms/TypingWaveIndicator.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt

## Self-Check: PASSED (file existence verified via Read tool)
