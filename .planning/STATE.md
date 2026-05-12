---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 05-04-PLAN.md
last_updated: "2026-04-05T20:54:48.010Z"
last_activity: 2026-04-05
progress:
  total_phases: 7
  completed_phases: 4
  total_plans: 22
  completed_plans: 19
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-05)

**Core value:** Визуально безупречный, моментально отзывчивый мессенджер, который ощущается лучше Telegram и нативнее iOS — на Android.
**Current focus:** Phase 05 — micro-interactions-haptics

## Current Position

Phase: 6
Plan: Not started
Status: Ready to execute
Last activity: 2026-04-05

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: —
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 03 P02 | 18 | 2 tasks | 3 files |
| Phase 03 P01 | 12 | 2 tasks | 4 files |
| Phase 01 P01 | 10 | 2 tasks | 3 files |
| Phase 01 P04 | 2 | 2 tasks | 4 files |
| Phase 01 P02 | 4 | 1 tasks | 6 files |
| Phase 01 P03 | 5 | 2 tasks | 3 files |
| Phase 01 P05 | 24 | 2 tasks | 4 files |
| Phase 02 P01 | 19 | 4 tasks | 5 files |
| Phase 02 P02 | 15 | 2 tasks | 1 files |
| Phase 02 P04 | 45 | 2 tasks | 1 files |
| Phase 02 P05 | 25 | 2 tasks | 2 files |
| Phase 03 P04 | 6 | 2 tasks | 3 files |
| Phase 04 P01 | 2 | 1 tasks | 1 files |
| Phase 04 P02 | 526 | 2 tasks | 5 files |
| Phase 04-navigation-system-integration P03 | 3 | 1 tasks | 2 files |
| Phase 05-micro-interactions-haptics P04 | 18 | 2 tasks | 3 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Phase order is strict bottom-up: tokens → chat screen → conversation list → navigation → micro-interactions → onboarding → performance
- [Roadmap]: Phases 2 and 3 can be parallelized; Phase 4 requires both to be complete
- [Research]: Compose BOM must be upgraded to 2026.03.01 before any UI work begins — unlocks stable shared elements, AnchoredDraggable, animateItem()
- [Research]: Phase 4 (Navigation) has a research flag — verify SharedTransitionLayout + NavHost integration pattern with Navigation Compose 2.9.0 before planning
- [Phase 01]: Compose BOM upgraded 2025.03.01 to 2026.03.01 — unlocks Material3 1.4.0, animateItem(), stable SharedTransitionLayout
- [Phase 01]: KotoThemeTest.kt created in RED state — intentionally references non-existent token classes until Plans 02-04 deliver them
- [Phase 01]: xxxl=48dp per REQUIREMENTS.md DS-04 (overrides ARCHITECTURE.md draft which had 32dp)
- [Phase 01]: KotoShapes uses asymmetric RoundedCornerShape for chat bubbles: bubbleOut tail at bottomEnd=4dp, bubbleIn tail at bottomStart=4dp
- [Phase 01]: DefaultKotoMotion is a top-level val (not object) — staticCompositionLocalOf provision pattern
- [Phase 01]: Two-tier color system: internal KotoPalette (raw hex) + public KotoColors semantic tokens — no composable outside theme/ references raw hex
- [Phase 01]: darkKotoColors.primary = Color(0xFF7B61FF) per DS-02; bubbleGradient = #7C3AED → #5B21B6; Brush.linearGradient not animated (swapped directly on theme change)
- [Phase 01]: ColorCompat.kt added as backward-compat layer so existing screens compile during incremental migration to KotoTheme.colors.* (Plan 05)
- [Phase 01]: Inter Variable v4.1 bundled TTF — Downloadable Fonts API lacks variable font support, mandatory APK bundling
- [Phase 01]: KotoTypography: @Immutable data class with 13 styles — same pattern as KotoColors; interFontFamily uses FontVariation.Settings for weight axis
- [Phase 01]: Legacy MessageUi.kt and ConversationUi.kt replaced with stub migration comments — canonical @Immutable definitions moved to UiModels.kt to satisfy DS-07 without duplicate class names
- [Phase 01]: animateKotoColors uses staticCompositionLocalOf (best read performance) — acceptable because theme switches are rare and 300ms animation distributes recomposition over frames
- [Phase 02]: reverseLayout=true with scrollToItem(0) for newest-at-bottom (not animateScrollToItem(lastIndex))
- [Phase 02]: imePadding() belongs on the LazyColumn modifier, NOT on the input bar Row
- [Phase 02]: buildChatItems() is a pure top-level function in ChatItemMapper.kt — not a method on ViewModel
- [Phase 02]: NDK version pinned to 27.2.12479018 in build.gradle.kts
- [Phase 02-02]: bubbleShape() is a non-composable private function returning Shape — avoids recomposition overhead
- [Phase 02-02]: ReplyPreviewChip stub scaffolds CH-05 structure in MessageBubble — full impl deferred to Plan 04
- [Phase 02-02]: ReadReceiptIcon uses AnimatedContent(targetState=status) with fade 200ms in / 100ms out; READ=colors.primary violet, FAILED=colors.error
- [Phase 02-04]: SwipeToReplyContainer uses detectHorizontalDragGestures — direction-locked horizontal swipe, no LazyColumn conflict; spring(0.6f, 400f) snap-back
- [Phase 02-04]: ContextMenuOverlay is Box inside ChatScreen Box (not Dialog) — same composition tree per PITFALLS A5; API 31+ blur via RenderEffect
- [Phase 02-05]: MediaMessageContent uses Painter placeholder on AsyncImage composable (not ImageRequest.Builder) — Coil 3.x Compose API
- [Phase 02-05]: decodeBlurHash() is pure Kotlin with no external deps; fallback = solid #1E1E2E when hash invalid
- [Phase 02-05]: ScrollToBottomFab uses derivedStateOf to avoid recomposition on every scroll pixel; springBouncy entrance; scrolls to index 0 (reverseLayout=true)
- [Phase 03-01]: ConversationEntity.toUi() maps old entity fields (lastMessagePreview→lastMessage, lastMessageTime→updatedAt, online→isOnline); isPinned/isMuted default false until server supports them
- [Phase 03-01]: showNewChat extracted from ConversationListState into separate MutableStateFlow<Boolean> showNewChatSheet in ViewModel
- [Phase 03-01]: OnlineDot pulse uses graphicsLayer{scaleX=scale;scaleY=scale} for draw-phase scale reads (PITFALLS P3)
- [Phase 03-02]: SwipeActionBackground action colors (archiveTeal/pinAmber/muteGray) are local vals — not semantic tokens; gesture-action colors intentionally non-tokenized
- [Phase 03-02]: Pinned conversations do not swipe (ConversationItem used directly) — matches Telegram pattern
- [Phase 03-02]: onArchive/onPin/onMute are Log stubs; chatRepository does not yet expose these; will be wired in future data phase
- [Phase 03]: BoxScope content lambda: KotoPullToRefresh accepts @Composable BoxScope.() -> Unit to match PullToRefreshBox content parameter type
- [Phase 03]: isRefreshing as separate StateFlow in ViewModel to avoid modifying domain UiModels.kt
- [Phase 04]: weight(1f) applied at Row call site via modifier param — avoids RowScope receiver on private BottomNavItem composable
- [Phase 04]: KotoBottomNavBar blur: API 31+ Modifier.blur(20dp) + surface 85% alpha; API<31 fallback solid 97% — both paths via KotoTheme.colors.surface only
- [Phase 04]: SharedTransitionScope passed as nullable param (not CompositionLocal) — keeps composables previewable without nav context
- [Phase 04]: sharedElement key pattern 'avatar-{convId}' matches exactly on both sides — ConversationItem uses conv.id, ChatTopBar receives convId string
- [Phase 04-navigation-system-integration]: enableEdgeToEdge() before super.onCreate() — repositioned from after to before for correct window flag setup
- [Phase 04-navigation-system-integration]: ChatTopBar WindowInsets.statusBars explicit — required when Scaffold contentWindowInsets=WindowInsets(0)
- [Phase 04-navigation-system-integration]: PredictiveBackHandler degrades gracefully on API < 34 — no version guard needed
- [Phase 05-04]: KotoHaptics refactored from object to class with View constructor + rememberKotoHaptics() factory for clean Compose integration
- [Phase 05-04]: onDoubleTap maps to CLOCK_TICK (subtle gesture ack); onReactionSettle maps to CONFIRM (stronger signal when heart settles)

### Roadmap Evolution

- Phase 05.1 inserted after Phase 5: Security and Bug Fix Sweep (URGENT) — 17 items from audit (7 security, 8 bugs, 2 UI stub groups). Blocks Phase 6 (Onboarding) since new users would enter broken E2EE paths.

### Pending Todos

None yet.

### Blockers/Concerns

- [Pre-Phase 1]: Compose BOM upgrade from 2025.03.01 → 2026.03.01 must be the first action in Phase 1 execution before any composable is written.
- [Pre-Phase 4]: SharedTransitionLayout + NavHost exact scope propagation with Navigation Compose 2.9.0 needs verification — do not plan Phase 4 without checking official docs.

## Session Continuity

Last session: 2026-04-05T20:54:00.934Z
Stopped at: Completed 05-04-PLAN.md
Resume file: None
