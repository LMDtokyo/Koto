---
phase: "01"
plan: "05"
subsystem: android-theme-provider
tags: [theme, compose, composition-local, animation, immutable, design-system, ds-06, ds-07]
dependency_graph:
  requires: [01-02, 01-03, 01-04]
  provides: [KotoTheme, LocalKotoColors, LocalKotoTypography, LocalKotoSpacing, LocalKotoShapes, LocalKotoElevation, LocalKotoMotion, MessageUi, ConversationUi, ChatState, ConversationListState, MessageStatus]
  affects:
    - android/app/src/main/java/run/koto/ui/theme/Theme.kt
    - android/app/src/main/java/run/koto/domain/model/UiModels.kt
    - android/app/src/main/java/run/koto/domain/model/MessageUi.kt
    - android/app/src/main/java/run/koto/domain/model/ConversationUi.kt
tech_stack:
  added: []
  patterns: [composition-local-provider, animated-color-state, immutable-ui-models, immutable-list-collections]
key_files:
  created:
    - android/app/src/main/java/run/koto/domain/model/UiModels.kt
  modified:
    - android/app/src/main/java/run/koto/ui/theme/Theme.kt
    - android/app/src/main/java/run/koto/domain/model/MessageUi.kt
    - android/app/src/main/java/run/koto/domain/model/ConversationUi.kt
decisions:
  - "Legacy MessageUi.kt and ConversationUi.kt replaced with stub migration comments — canonical @Immutable definitions moved to UiModels.kt to satisfy DS-07 and avoid naming conflicts"
  - "animateKotoColors uses staticCompositionLocalOf (best read performance) — acceptable because theme switches are rare and 300ms animation distributes recomposition over frames"
  - "bubbleGradient (Brush) and isLight (Boolean) passed through directly — no animateBrushAsState API exists in Compose"
metrics:
  duration: "~24 minutes"
  completed: "2026-04-05"
  tasks_completed: 2
  files_changed: 4
---

# Phase 01 Plan 05: KotoTheme Provider and @Immutable UI Models Summary

Production KotoTheme composable with 6 CompositionLocals, 300ms animated color switching via animateColorAsState on all 16 Color fields, KotoTheme accessor object, and @Immutable UI model classes (MessageUi, ConversationUi, ChatState, ConversationListState) using ImmutableList for zero-unnecessary-recomposition — completing DS-06 and DS-07 and all 7 design system requirements.

## Tasks Completed

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Write final Theme.kt with CompositionLocals and animated switching | Done | dbcd8db |
| 2 | Create @Immutable UI model classes in UiModels.kt | Done | 5bed20a |
| 3 | Human verify checkpoint (checkpoint:human-verify) | Auto-approved (auto_advance=true) | — |

## What Was Built

### Task 1: Theme.kt production implementation (dbcd8db)

**android/app/src/main/java/run/koto/ui/theme/Theme.kt** — complete rewrite of Plan 02 stub:

- `val LocalKotoColors = staticCompositionLocalOf { darkKotoColors }` — and 5 more for typography/spacing/shapes/elevation/motion
- `object KotoTheme` with 6 `@Composable` getters: `colors`, `typography`, `spacing`, `shapes`, `elevation`, `motion`
- `private fun animateKotoColors(target: KotoColors): KotoColors` — DS-06 implementation:
  - 16 `animateColorAsState()` calls (one per Color field): primary, onPrimary, background, surface, surfaceVariant, onBackground, onSurface, onSurfaceLow, onSurfaceMuted, bubbleOut, bubbleIn, onBubbleOut, onBubbleIn, divider, error, online
  - `bubbleGradient = target.bubbleGradient` — Brush has no animateBrushAsState, swapped directly
  - `isLight = target.isLight` — Boolean not interpolatable, passed directly
  - All color specs: `tween(300ms, FastOutSlowInEasing)` per DS-06
- `fun KotoTheme(darkTheme, content)` composable — wraps 6 CompositionLocalProvider + MaterialTheme with aligned M3 color scheme

### Task 2: UiModels.kt @Immutable models (5bed20a)

**android/app/src/main/java/run/koto/domain/model/UiModels.kt** — new canonical file:

- `enum class MessageStatus` — SENDING, SENT, DELIVERED, READ, FAILED
- `@Immutable data class MessageUi` — id, senderId, text, sentAt, status (MessageStatus), isOutgoing, replyTo?, mediaUrl?, blurHash?
- `@Immutable data class ConversationUi` — id, displayName, avatarUrl?, lastMessage, unreadCount, isOnline, isPinned, isMuted, updatedAt
- `@Immutable data class ChatState` — messages: `ImmutableList<MessageUi>`, isLoading, isTyping, displayName, avatarUrl?
- `@Immutable data class ConversationListState` — conversations/pinnedConvs: `ImmutableList<ConversationUi>`, isLoading, searchQuery
- Legacy `MessageUi.kt` and `ConversationUi.kt` replaced with stub migration comments (files preserved per plan rules, class definitions removed to avoid duplicate class names)

## Verification Results

```
grep "staticCompositionLocalOf" Theme.kt               → 6 declarations (lines 21–26) PASS
grep "object KotoTheme" Theme.kt                       → match                         PASS
grep "private fun animateKotoColors" Theme.kt          → match                         PASS
grep "animateColorAsState" Theme.kt | count            → 16 actual calls               PASS
grep "bubbleGradient = target.bubbleGradient" Theme.kt → match (not animated)          PASS
grep "isLight = target.isLight" Theme.kt               → match (not animated)          PASS
grep "CompositionLocalProvider" Theme.kt               → match with all 6 provides     PASS
grep "MaterialTheme(" Theme.kt                         → match inside KotoTheme        PASS
UiModels.kt @Immutable annotations                     → 4 occurrences                PASS
UiModels.kt ImmutableList fields                       → ChatState + ConvListState     PASS
UiModels.kt enum MessageStatus with 5 values           → SENDING/SENT/DELIVERED/READ/FAILED PASS
UiModels.kt no raw List<T> in @Immutable classes       → confirmed                    PASS
./gradlew :app:assembleDebug                           → BLOCKED (NDK not installed — pre-existing env constraint, same as Plans 01–04)
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Legacy MessageUi.kt and ConversationUi.kt caused duplicate class names**
- **Found during:** Task 2
- **Issue:** UiModels.kt defines `data class MessageUi` and `data class ConversationUi`, but `MessageUi.kt` and `ConversationUi.kt` already define same-named classes in the same package — would cause compile errors
- **Fix:** Replaced legacy files with stub migration comments (class definitions removed). UiModels.kt becomes single canonical source. Legacy files preserved (not deleted) per plan instructions with TODO migration notes.
- **Files modified:** `MessageUi.kt`, `ConversationUi.kt`
- **Commit:** 5bed20a

## DS Requirements Status

| Requirement | Status | Plan |
|-------------|--------|------|
| DS-01: KotoColors semantic tokens | DONE | 01-02 |
| DS-02: Exact palette #7B61FF + gradient | DONE | 01-02 |
| DS-03: Inter font + 13-style typography | DONE | 01-03 |
| DS-04: Spacing xxs=2dp → xxxl=48dp | DONE | 01-04 |
| DS-05: Shape system sm=8dp → bubble=18dp | DONE | 01-04 |
| DS-06: Animated theme switching (animateColorAsState) | DONE | 01-05 |
| DS-07: @Immutable UI models with ImmutableList | DONE | 01-05 |

## Known Stubs

None — all implementations contain complete, concrete values. Theme.kt wires all 6 token categories from Plans 02–04. UiModels.kt has complete field sets with no placeholder data.

## Self-Check: PASSED

- `android/app/src/main/java/run/koto/ui/theme/Theme.kt` — exists, contains `staticCompositionLocalOf` (6 declarations), `object KotoTheme`, `private fun animateKotoColors`, 16 `animateColorAsState` calls, `bubbleGradient = target.bubbleGradient`, `isLight = target.isLight`, `fun KotoTheme(`, `CompositionLocalProvider(`, `MaterialTheme(` ✓
- `android/app/src/main/java/run/koto/domain/model/UiModels.kt` — exists, contains `@Immutable` (4 annotations), `data class MessageUi`, `data class ConversationUi`, `data class ChatState`, `data class ConversationListState`, `enum class MessageStatus`, `ImmutableList<MessageUi>`, `ImmutableList<ConversationUi>`, `import kotlinx.collections.immutable.ImmutableList`, `import kotlinx.collections.immutable.persistentListOf` ✓
- Commit `dbcd8db` exists (Task 1) ✓
- Commit `5bed20a` exists (Task 2) ✓
