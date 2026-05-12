---
phase: 02-chat-screen
plan: 01
subsystem: android-chat
tags: [chat, data-model, viewmodel, compose, kotlin, immutable]
dependency_graph:
  requires: [01-05]
  provides: [ChatItemMapper, canonical-ChatState, reverseLayout-skeleton]
  affects: [ChatScreen, ChatViewModel, UiModels]
tech_stack:
  added: []
  patterns:
    - "sealed interface ChatItem with @Immutable subtypes"
    - "ThreadLocal<SimpleDateFormat> for thread-safe formatting in buildChatItems()"
    - "chatItems StateFlow via .map{}.flowOn(Dispatchers.Default).stateIn()"
    - "reverseLayout=true LazyColumn with imePadding() on scroll pane only"
    - "Scaffold(contentWindowInsets=WindowInsets(0)) prevents double inset consumption"
    - "Stable lambda references via remember { viewModel::method }"
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatItemMapper.kt
  modified:
    - android/app/src/main/java/run/koto/domain/model/UiModels.kt
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatViewModel.kt
    - android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
    - android/app/build.gradle.kts
decisions:
  - "reverseLayout=true with scrollToItem(0) for newest-at-bottom (not animateScrollToItem(lastIndex))"
  - "imePadding() belongs on the LazyColumn modifier, NOT on the input bar Row"
  - "buildChatItems() is a pure top-level function in ChatItemMapper.kt — not a method on ViewModel"
  - "NDK version pinned to 27.2.12479018 in build.gradle.kts (installed version)"
metrics:
  duration_minutes: 19
  completed_date: "2026-04-05"
  tasks_completed: 4
  files_modified: 5
---

# Phase 02 Plan 01: Chat Screen Structural Fixes Summary

**One-liner:** Structural pre-requisites fixed — sealed ChatItem model, reverseLayout LazyColumn, imePadding moved to scroll pane, all ColorCompat aliases replaced with KotoTheme tokens.

## What Was Built

Fixed 6 structural issues identified in the Phase 02 research before any premium UI features can be added:

1. **UiModels.kt** — Added `inputText`/`sending`/`online` fields to canonical `ChatState`; added `sealed interface ChatItem` with `@Immutable Message` and `DateSeparator` subtypes. ChatItem now carries `formattedTime` (pre-computed) to eliminate `SimpleDateFormat` calls in composition.

2. **ChatItemMapper.kt** — New pure function `buildChatItems()` that converts `ImmutableList<MessageUi>` to `ImmutableList<ChatItem>` with date separators and bubble grouping metadata. Uses `ThreadLocal<SimpleDateFormat>` for thread-safety. Intended to run on `Dispatchers.Default` only.

3. **ChatViewModel.kt** — Deleted the local duplicate `data class ChatState` (was shadowing the canonical domain model). Imports `run.koto.domain.model.ChatState` instead. Added `chatItems: StateFlow<ImmutableList<ChatItem>>` mapped via `.flowOn(Dispatchers.Default)`. Added `replyTarget`, `unreadBelowFold`, and helper methods for Plans 04–05.

4. **ChatScreen.kt** — Full structural rewrite:
   - `reverseLayout = true` on `LazyColumn` (index 0 = newest = visually at bottom)
   - `imePadding()` on the LazyColumn modifier only — removed from `MessageInputBar` Row
   - `Scaffold(contentWindowInsets = WindowInsets(0))` prevents Scaffold from consuming insets prematurely
   - Auto-scroll uses `scrollToItem(0)` guarded by `firstVisibleItemIndex <= 1`
   - All colors via `KotoTheme.colors.*` — zero `ColorCompat`/legacy alias references
   - `chatItems` collected from ViewModel, not `state.messages`
   - `key`/`contentType` in `LazyColumn` items for stable Compose rendering
   - Stable lambda references via `remember { viewModel::method }`

5. **build.gradle.kts** — Pinned `ndkVersion = "27.2.12479018"` to the installed NDK to fix build configuration failure (deviation, Rule 3).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] NDK not installed, blocking Kotlin compile task**
- **Found during:** Task 1 verification
- **Issue:** The `rust-android-gradle` plugin checks for NDK at configuration time. NDK was not installed, causing `A problem occurred configuring project ':app'` before any Kotlin compilation.
- **Fix:** Downloaded Android cmdline-tools, accepted licenses, installed `ndk;27.2.12479018`. Added `ndkVersion = "27.2.12479018"` to `android {}` block in `build.gradle.kts` to pin the expected version.
- **Files modified:** `android/app/build.gradle.kts`
- **Commit:** 29ed5fa (included with Task 1)

## Pre-existing Issues (Out of Scope)

These compile errors existed before this plan and remain — they require Rust toolchain for uniffi code generation:

- `CryptoManager.kt` — all `uniffi.*` references unresolved (needs `./gradlew cargoBuild`)
- `ConversationEntity.kt` — field mismatch (pre-existing schema evolution gap)
- `MessageEntity.kt` — `delivered` field mismatch (pre-existing)
- `AuthRepository.kt` — `RegistrationBundle` fields unresolved (uniffi)
- `ChatRepository.kt` — `PreKeyBundleInput` unresolved (uniffi)

Logged to `deferred-items.md`: uniffi code generation requires full Rust toolchain with Android cross-compilation targets — out of scope for UI plans.

## Known Stubs

- `ChatMessageList` typing indicator placeholder (`Box` with no content) — intentional, full `TypingWaveIndicator` delivered in Plan 03
- `onLongPress` in `ChatViewModel` is a no-op stub — context menu implementation deferred to Plan 04

## Success Criteria Verification

1. `ChatViewModel.kt` contains no local `data class ChatState` — PASS
2. `ChatItemMapper.kt` exists with `buildChatItems()` using `ThreadLocal<SimpleDateFormat>` — PASS
3. `ChatScreen.kt` has `reverseLayout = true` and exactly one `imePadding()` on LazyColumn — PASS
4. Zero `ColorCompat` aliases in `ui/screens/chat/` — PASS
5. `UiModels.kt` ChatState has `inputText`/`sending`/`online` fields and `sealed interface ChatItem` — PASS
6. `./gradlew :app:compileDebugKotlin` — chat package compiles clean; remaining failures are pre-existing uniffi/entity mismatches outside this plan's scope

## Self-Check: PASSED

Files created/modified:
- FOUND: android/app/src/main/java/run/koto/ui/screens/chat/ChatItemMapper.kt
- FOUND: android/app/src/main/java/run/koto/domain/model/UiModels.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/chat/ChatViewModel.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt
- FOUND: android/app/build.gradle.kts

Commits:
- 29ed5fa: feat(02-01): expand UiModels — ChatState fields + sealed ChatItem interface
- 417a93e: feat(02-01): create ChatItemMapper — pure buildChatItems() on background dispatcher
- dea70d8: refactor(02-01): ChatViewModel — delete duplicate ChatState, add chatItems flow
- 9997130: feat(02-01): rewrite ChatScreen — reverseLayout, correct imePadding, KotoTheme tokens
