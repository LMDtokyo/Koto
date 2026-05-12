---
phase: 03-conversation-list
plan: 03
subsystem: android/conversations
tags: [search, animation, compose, spring, debounce, conversation-list]
dependency_graph:
  requires: [Phase 03 Plan 01 (ConversationListState, onSearchQueryChange), Phase 01 design tokens]
  provides: [AnimatedSearchBar composable, search filtering with debounce, SearchEmptyState]
  affects: [ConversationsScreen TopBar, conversation list render path]
tech_stack:
  added: []
  patterns: [AnimatedContent with SizeTransform for flicker-free transitions, spring physics expand/collapse, 200ms LaunchedEffect debounce, remember(specific keys) stability optimization]
key_files:
  created:
    - android/app/src/main/java/run/koto/ui/screens/conversations/SearchBar.kt
  modified:
    - android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
decisions:
  - AnimatedContent (not AnimatedVisibility) used for search bar toggle — avoids height flicker via SizeTransform(clip=true) (PITFALLS A7)
  - Spring physics for both enter (slideInHorizontally + fadeIn) and exit (slideOutHorizontally + fadeOut) transitions — NoBouncy dampingRatio, StiffnessMedium
  - Icons.AutoMirrored.Filled.ArrowBack used instead of Icons.Default.ArrowBack — follows Material3 LTR/RTL-aware icon guidelines
  - 200ms debounce implemented via LaunchedEffect(pendingQuery) + delay(200) in ConversationsScreen — cancels and restarts on each keystroke
  - filteredConvs remember() keyed on state.conversations, state.pinnedConvs, state.searchQuery (individual fields, not whole state) — stability optimization per PITFALLS P5
  - SearchEmptyState shown only when searchQuery is non-empty AND both filteredRegular and filteredPinned are empty
metrics:
  duration: 15 minutes
  completed_date: 2026-04-05T17:15:00Z
  tasks_completed: 2
  files_created: 1
  files_modified: 1
---

# Phase 03 Plan 03: AnimatedSearchBar + Conversation Filtering Summary

**One-liner:** AnimatedSearchBar using AnimatedContent + spring transitions, wired into ConversationsTopBar with 200ms debounce filtering via LaunchedEffect and SearchEmptyState for no-match feedback.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create AnimatedSearchBar composable (CL-04) | pending | SearchBar.kt |
| 2 | Wire AnimatedSearchBar into ConversationsScreen + filter logic (CL-04) | pending | ConversationsScreen.kt |

## What Was Built

### SearchBar.kt (new file — CL-04)
- `AnimatedSearchBar`: public composable used in TopAppBar `actions` slot
  - `AnimatedContent(targetState = expanded)` switches between `IconButton(Search)` and `SearchTextField`
  - Enter: `fadeIn(tweenFast) + slideInHorizontally(spring NoBouncy StiffnessMedium, initialOffset = width/3)`
  - Exit: `fadeOut(tweenFast) + slideOutHorizontally(spring NoBouncy StiffnessMedium, targetOffset = width/3)`
  - `SizeTransform(clip = true)` prevents height flicker during transition (PITFALLS A7)
- `SearchTextField`: private composable for expanded state
  - `BasicTextField` with `FocusRequester` — auto-focuses keyboard via `LaunchedEffect(Unit)`
  - Decoration box: search icon prefix, placeholder text, clear (X) button when query non-empty, back arrow close button
  - All styling via `KotoTheme.colors.*`, `KotoTheme.spacing.*`, `KotoTheme.typography.*`

### ConversationsScreen.kt (updated)
- `searchExpanded: Boolean` — `rememberSaveable` local state (survives config changes, ephemeral across process death)
- `pendingQuery: String` — local `remember` state updated immediately on each keystroke
- `LaunchedEffect(pendingQuery)` + `delay(200)` — 200ms debounce before calling `viewModel.onSearchQueryChange(pendingQuery)`
- `filteredConvs` — `remember(state.conversations, state.pinnedConvs, state.searchQuery)` keyed on specific fields for stability
- `ConversationsTopBar` updated: accepts `searchExpanded`, `searchQuery`, `onSearchToggle`, `onQueryChange`, `onSettings` params
- `AnimatedVisibility(visible = !searchExpanded)` wraps `KotoLogoTitle()` — fades out logo when search expands
- `KotoLogoTitle()` extracted as private composable — keeps TopBar readable
- `SearchEmptyState(query)` composable — shows SearchOff icon + "No results for {query}" when both filtered lists are empty
- Settings `IconButton` hidden when `searchExpanded` — frees horizontal space for full-width text field

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used Icons.AutoMirrored.Filled.ArrowBack instead of Icons.Default.ArrowBack**
- **Found during:** Task 1
- **Issue:** `Icons.Default.ArrowBack` is deprecated in Material Icons Extended — replaced by AutoMirrored variant for proper RTL support
- **Fix:** Used `Icons.AutoMirrored.Filled.ArrowBack` with corresponding import
- **Files modified:** SearchBar.kt
- **Commit:** pending

## Known Stubs

None — search filtering is fully wired to ViewModel state. All UI paths (expanded, collapsed, filtering, empty results) are implemented.

## Verification Results

SearchBar.kt acceptance criteria:
- `fun AnimatedSearchBar`: 1 (required: 1) — PASS
- `AnimatedContent`: 3 (required: >= 1) — PASS
- `SizeTransform`: 3 (required: >= 1) — PASS
- `spring(`: 2 (required: >= 2) — PASS
- `FocusRequester`: 2 (required: >= 1) — PASS
- `KotoTheme.`: 6 (required: >= 6) — PASS

ConversationsScreen.kt acceptance criteria:
- `AnimatedSearchBar`: 1 (required: >= 1) — PASS
- `searchExpanded`: 8 (required: >= 3) — PASS
- `delay(200)`: 1 (required: >= 1) — PASS
- `SearchEmptyState`: 2 (required: >= 1) — PASS
- `filteredRegular|filteredPinned|filteredConvs`: 4 (required: >= 3) — PASS

No new compilation errors in SearchBar.kt or ConversationsScreen.kt (pre-existing errors in AuthRepository.kt, ChatRepository.kt, TypingWaveIndicator.kt remain — out of scope).

## Self-Check: PASSED

Files verified:
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/SearchBar.kt
- FOUND: android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsScreen.kt
