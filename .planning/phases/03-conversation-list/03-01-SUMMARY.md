---
phase: 03-conversation-list
plan: 01
subsystem: android/conversations
tags: [viewmodel, compose, state, animation, conversation-list]
dependency_graph:
  requires: [Phase 01 design tokens, Phase 02 chat screen patterns]
  provides: [ConversationListState shape, ConversationItem composable, OnlineDot atom]
  affects: [03-02 (swipe wrappers), 03-03 (search logic)]
tech_stack:
  added: []
  patterns: [ImmutableList state shape, InfiniteTransition pulse animation, graphicsLayer draw-phase scale, KotoTheme semantic token usage]
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationItem.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsViewModel.kt
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
    - android/app/src/main/java/run/koto/data/local/entity/ConversationEntity.kt
decisions:
  - ConversationEntity.toUi() updated to map old fields (lastMessagePreview, lastMessageTime, online) to new ConversationUi (lastMessage, updatedAt, isOnline); isPinned/isMuted default to false until server supports them
  - showNewChat extracted from ConversationListState into separate MutableStateFlow<Boolean> showNewChatSheet per plan spec
  - OnlineDot uses spacing.xxs (2dp) for border width instead of hardcoded 2.dp — follows KotoTheme token convention
metrics:
  duration: 12 minutes
  completed_date: 2026-04-05T16:52:00Z
  tasks_completed: 2
  files_created: 1
  files_modified: 3
---

# Phase 03 Plan 01: ViewModel Migration + ConversationItem Summary

**One-liner:** ConversationListState with ImmutableList pinnedConvs/conversations + ConversationItem composable with pulsing OnlineDot via graphicsLayer and KotoTheme tokens.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Migrate ConversationsViewModel to ConversationListState | 9a27c9c | ConversationsViewModel.kt, ConversationEntity.kt, ConversationsScreen.kt |
| 2 | Create ConversationItem composable with OnlineDot (CL-01, CL-06) | 19576d4 | ConversationItem.kt |

## What Was Built

### ConversationsViewModel (migrated)
- Exposes `StateFlow<ConversationListState>` with `conversations` (non-pinned) and `pinnedConvs` (pinned) as `ImmutableList<ConversationUi>`
- Separate `showNewChatSheet: StateFlow<Boolean>` for modal state (not in ConversationListState)
- `onSearchQueryChange(query)` updates `searchQuery` field in state
- Collects `chatRepository.getConversationsFlow()`, splits by `isPinned`, converts to `ImmutableList`

### ConversationItem.kt (new file — CL-01 + CL-06)
- `ConversationItem`: 72dp row, avatar 48dp, name (titleMedium), preview (bodySmall, 1 line), timestamp (labelMedium), unread badge
- `AvatarWithPresence`: gradient initials with `OnlineDot` at bottom-right when `isOnline`
- `OnlineDot`: `rememberInfiniteTransition` scale 1f→1.25f (900ms tween, Reverse), `graphicsLayer { scaleX=scale; scaleY=scale }` for draw-phase reads (PITFALLS P3)
- `UnreadBadge`: CircleShape for count < 10, RoundedCornerShape(10dp) for count >= 10; muted variant uses `surfaceVariant`
- `formatConvTime()`: HH:mm for today, dd.MM for earlier dates
- `avatarGradientColors()`: deterministic 5-pair palette from `id.hashCode()`
- All color/spacing/typography through KotoTheme tokens (9 references)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated ConversationEntity.toUi() to map to canonical ConversationUi**
- **Found during:** Task 1
- **Issue:** `ConversationEntity.toUi()` used old field names (`lastMessagePreview`, `lastMessageTime`, `online`) that don't exist in the new `ConversationUi` from `UiModels.kt`
- **Fix:** Updated `toUi()` to map: `lastMessage = lastMessagePreview.orEmpty()`, `updatedAt = lastMessageTime ?: 0L`, `isOnline = online`, `isPinned = false`, `isMuted = false`, `avatarUrl = null`
- **Files modified:** `android/app/src/main/java/run/koto/data/local/entity/ConversationEntity.kt`
- **Commit:** 9a27c9c

**2. [Rule 1 - Bug] Fixed ConversationsScreen.kt field references**
- **Found during:** Task 1
- **Issue:** `ConversationsScreen.kt` referenced `state.loading` (removed), `state.showNewChat` (removed from state), `conv.online` (renamed), `conv.lastMessagePreview` (renamed), `conv.lastMessageTime` (renamed)
- **Fix:** Updated to `state.isLoading`, `showNewChat` local from `viewModel.showNewChatSheet.collectAsState()`, `conv.isOnline`, `conv.lastMessage`, `conv.updatedAt`
- **Files modified:** `android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt`
- **Commit:** 9a27c9c

## Known Stubs

None — `isPinned` and `isMuted` default to `false` in `ConversationEntity.toUi()` because the entity and server DTO don't expose these fields yet. This is intentional: the state shape is correct and future plans can add these columns to the entity/sync layer.

## Verification Results

- `ConversationsState` removed: zero hits in conversations package
- `KotoTheme.` count in ConversationItem.kt: 9 (>= 8 required)
- `ImmutableList`/`toImmutableList` in ViewModel: 4 lines
- `ConversationListState StateFlow` in ViewModel: 2 lines
- `onSearchQueryChange` in ViewModel: 1 line
- No new compilation errors introduced (pre-existing uniffi/crypto errors unrelated)

## Self-Check: PASSED

Files verified:
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/ConversationItem.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsViewModel.kt
- FOUND: android/app/src/main/java/run/koto/data/local/entity/ConversationEntity.kt

Commits verified:
- FOUND: 9a27c9c (feat(03-01): migrate ConversationsViewModel to ConversationListState)
- FOUND: 19576d4 (feat(03-01): create ConversationItem composable with OnlineDot)
