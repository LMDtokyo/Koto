---
phase: 03-conversation-list
plan: 02
subsystem: android/conversations
tags: [swipe-gestures, haptics, pinned-section, compose, conversation-list]
dependency_graph:
  requires: [Phase 03 Plan 01 — ConversationItem, ConversationListState]
  provides: [SwipeableConversationItem with AnchoredDraggable, PinnedSectionHeader, swipe action handlers]
  affects: [03-03 (search screen — uses updated ConversationList)]
tech_stack:
  added: [AnchoredDraggableState (Compose Foundation 1.8.x via BOM 2026.03.01)]
  patterns: [AnchoredDraggable swipe, layout-phase offset reads via Modifier.offset lambda, haptic threshold detection, animateItem() spring animations]
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/screens/conversations/SwipeableConversationItem.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsViewModel.kt
decisions:
  - SwipeActionBackground action colors (archiveTeal, pinAmber, muteGray) defined as local vals inside composable — gesture-action colors are not semantic brand tokens, intentionally non-tokenized
  - Pinned conversations do not have swipe actions (matches Telegram pattern) — ConversationItem used directly for pinned section
  - onArchive/onPin/onMute ViewModel methods are Log stubs — chatRepository does not yet expose these operations; will be wired in a future phase
  - Legacy color tokens (BgPrimary, AccentPrimary, TextPrimary, IconDefault, DividerColor, etc.) fully removed from ConversationsScreen.kt — replaced with KotoTheme.colors.* references
  - Search functionality from previous plan (Plan 03-03 merge) preserved intact — filteredPinned passed to ConversationList
metrics:
  duration: 18 minutes
  completed_date: 2026-04-05T17:20:00Z
  tasks_completed: 2
  files_created: 1
  files_modified: 2
---

# Phase 03 Plan 02: Swipe Actions + Pinned Section Summary

**One-liner:** SwipeableConversationItem with AnchoredDraggable left/right swipe (archive teal, pin amber, mute gray) + haptic feedback at threshold crossings, wrapped by updated ConversationList with pinned section header and animateItem() spring insertions.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create SwipeableConversationItem with AnchoredDraggable and haptics (CL-02) | pending | SwipeableConversationItem.kt, ConversationsViewModel.kt |
| 2 | Update ConversationList in ConversationsScreen — pinned section + swipeable rows (CL-03) | pending | ConversationsScreen.kt |

## What Was Built

### SwipeableConversationItem.kt (new — CL-02)
- `SwipeableConversationItem`: Box with behind layer (action panels) + front layer (ConversationItem offset by drag)
- `SwipeAnchor` enum: `Center` (0f), `Left` (-72dp), `Right` (+72dp)
- `AnchoredDraggableState` with spring snap (StiffnessMedium, NoBouncy) and exponential decay
- Haptic threshold: `LaunchedEffect(offset)` tracks 50% threshold crossing, fires `CONTEXT_CLICK` once per crossing direction change
- Commit haptic: `LaunchedEffect(draggableState.currentValue)` fires `CONFIRM` (API 30+) or `LONG_PRESS` (API 26-29) on snap
- Offset reads use `Modifier.offset { IntOffset(offset.roundToInt(), 0) }` — layout-phase lambda defers state read (PITFALLS P3)
- `.anchoredDraggable(draggableState, Orientation.Horizontal)` automatically participates in nestedScroll system

### SwipeActionBackground (private — inside SwipeableConversationItem.kt)
- Left swipe (offset < 0): teal (`#0D9488`) archive panel 72dp wide at trailing edge
- Right swipe (offset > 0): amber (`#F59E0B`) pin + gray (`#6B7280`) mute panels, each 72dp = 144dp total at leading edge
- All action panels use `.clickable` to trigger callbacks
- `errorColor` parameter reserved for future delete action (currently `@Suppress("UNUSED_PARAMETER")`)

### ConversationsViewModel.kt (updated — CL-02)
- Added `onArchive(convId: String)`, `onPin(convId: String)`, `onMute(convId: String)` methods
- All three are coroutine stubs with `Log.d` — chatRepository doesn't expose these operations yet

### ConversationsScreen.kt (updated — CL-03)
- Removed legacy inline `private fun ConversationItem` (replaced by public `ConversationItem` from `ConversationItem.kt`)
- Removed `avatarGradient` helper reference
- Removed `import run.koto.ui.theme.*` wildcard — only `import run.koto.ui.theme.KotoTheme` remains
- All legacy tokens migrated: `BgPrimary` → `colors.background`, `AccentPrimary` → `colors.primary`, `AccentContainer` → `colors.surfaceVariant`, `TextPrimary` → `colors.onSurface`, `TextTertiary` → `colors.onSurfaceMuted`, `IconDefault` → `colors.onSurfaceLow`, `DividerColor` → `colors.divider`, `BgElevated` → `colors.surface`, `BgSurface1` → `colors.surface`, `TextSecondary` → `colors.onSurfaceLow`, `AccentDark/Light` → `colors.primary`
- `ConversationList` updated to accept `pinnedConvs + conversations + onArchive/onPin/onMute`
- `PinnedSectionHeader` composable: pin icon + "Pinned" label in `onSurfaceMuted`, 14dp icon size
- Pinned section renders before regular conversations, followed by `HorizontalDivider` section separator
- Regular conversations use `SwipeableConversationItem` with `Modifier.animateItem()`
- Pinned conversations use plain `ConversationItem` (no swipe, Telegram pattern)
- Search functionality preserved: `filteredPinned` and `filteredRegular` both passed to `ConversationList`
- KotoTheme token count: 18 references (requirement: >= 10)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unused `threshold` parameter from SwipeActionBackground**
- **Found during:** Task 1 code review
- **Issue:** `threshold: Float` parameter was received by `SwipeActionBackground` but never used inside the function; action widths are hardcoded as `72.dp`/`144.dp`
- **Fix:** Removed `threshold` parameter from function signature and call site
- **Files modified:** SwipeableConversationItem.kt

**2. [Rule 2 - Missing functionality] Preserved search feature from pre-existing ConversationsScreen**
- **Found during:** Task 2 — reading current ConversationsScreen.kt (updated by an intermediate plan)
- **Issue:** File already contained `AnimatedSearchBar`, `SearchEmptyState`, debounce `LaunchedEffect`, `rememberSaveable` search state, and `filteredConvs` derived state — none of which were in the 03-02 plan spec
- **Fix:** Preserved all search functionality intact; updated `ConversationList` call site to pass `filteredPinned`/`filteredRegular` instead of full lists; updated `EmptyState` check to include `state.pinnedConvs.isEmpty()` guard
- **Files modified:** ConversationsScreen.kt

**3. [Rule 1 - Bug] Updated EmptyState condition to include pinnedConvs check**
- **Found during:** Task 2
- **Issue:** Original plan's empty state condition only checked `state.conversations.isEmpty()` — would hide empty state when only pinned convs exist and regular convs are empty
- **Fix:** Changed to `state.conversations.isEmpty() && state.pinnedConvs.isEmpty() && !state.isLoading`

## Known Stubs

- `ConversationsViewModel.onArchive()` — logs convId, does not call chatRepository. Repository method `archiveConversation()` not yet implemented.
- `ConversationsViewModel.onPin()` — logs convId, does not call chatRepository. Repository method `pinConversation()` not yet implemented.
- `ConversationsViewModel.onMute()` — logs convId, does not call chatRepository. Repository method `muteConversation()` not yet implemented.

These stubs are intentional: the swipe gesture and haptic UI is fully functional; the backend persistence will be wired in a future data-layer phase. The plan spec explicitly documents these as stubs.

## Verification Results

- `fun SwipeableConversationItem` count in SwipeableConversationItem.kt: 1 (required: 1)
- `AnchoredDraggableState` count in SwipeableConversationItem.kt: 2 (required: >= 1)
- `performHapticFeedback` count in SwipeableConversationItem.kt: 2 (required: >= 2)
- `IntOffset` count in SwipeableConversationItem.kt: 4 (required: >= 1)
- `PinnedSectionHeader` count in ConversationsScreen.kt: 2 (required: >= 1)
- `SwipeableConversationItem` count in ConversationsScreen.kt: 1 (required: >= 1)
- `animateItem` count in ConversationsScreen.kt: 2 (required: >= 1)
- `pinnedConvs` count in ConversationsScreen.kt: 8 (required: >= 2)
- Legacy tokens (BgPrimary|AccentPrimary|TextPrimary|IconDefault|DividerColor) in ConversationsScreen.kt: 0 (required: 0)
- `KotoTheme.` count in ConversationsScreen.kt: 18 (required: >= 10)

## Self-Check

Files verified:
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/SwipeableConversationItem.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsViewModel.kt

## Self-Check: PASSED
