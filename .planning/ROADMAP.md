# Roadmap: Koto Messenger — Premium UI/UX

## Overview

This milestone transforms the existing functional Android app (Compose base, Signal crypto, full backend) into a premium messenger that looks and feels better than Telegram on Android. Work proceeds strictly bottom-up: design tokens first (every other phase depends on them), then the two core screens (chat and conversation list), then navigation wiring, then animation polish, then onboarding, and finally performance verification. Each phase delivers a complete, independently verifiable capability and unblocks all phases after it.

## Phases

**Phase Numbering:**
- Integer phases (1–7): Planned milestone work
- Decimal phases: Urgent insertions created via `/gsd:insert-phase`

- [ ] **Phase 1: Design System Foundation** - KotoTheme token system, typography, spacing, shape, animated theme switch, @Immutable UI models
- [x] **Phase 2: Chat Screen** - Gradient bubbles, message grouping, read receipts, typing indicator, reply, context menu, media, FAB, 120fps LazyColumn (completed 2026-04-05)
- [x] **Phase 3: Conversation List** - Conversation rows, swipe actions, pinned section, search, pull-to-refresh, online indicators (completed 2026-04-05)
- [x] **Phase 4: Navigation & System Integration** - Custom bottom nav, shared element transitions, edge-to-edge, predictive back, spring screen transitions (completed 2026-04-05)
- [ ] **Phase 5: Micro-Interactions & Haptics** - Send button morph, double-tap reactions, haptic mapping, avatar profile card, spring animations everywhere
- [ ] **Phase 6: Onboarding** - Animated splash, Lottie onboarding flow, phone/email input validation, permission screens
- [ ] **Phase 7: Performance & Polish** - Baseline profiles, compiler stability reports, frame time verification, glassmorphism API fallback

## Phase Details

### Phase 1: Design System Foundation
**Goal**: KotoTheme is the single source of truth for all visual values — colors, typography, spacing, shapes, motion, and stable data models — so every downstream composable builds on a correct, recomposition-safe foundation.
**Depends on**: Nothing (first phase)
**Requirements**: DS-01, DS-02, DS-03, DS-04, DS-05, DS-06, DS-07
**Success Criteria** (what must be TRUE):
  1. A composable using `KotoTheme.colors.primary` renders the correct violet (#7B61FF) in dark mode and (#7C5CBF) in light mode with no hardcoded hex values anywhere in the codebase.
  2. Switching between dark and light theme triggers a visible animated transition (circular reveal or crossfade) rather than an instant snap.
  3. Inter Variable font is loaded from bundled resources and applies the correct 8-style typography scale (display through label) on all API 26+ devices.
  4. All UI model data classes (`MessageUi`, `ConversationUi`, etc.) are annotated `@Immutable` and use `ImmutableList<T>` for collection fields — Compose compiler reports show zero unstable types in these classes.
  5. `KotoMotionSpec` object exists with named spring constants (dampingRatio, stiffness) used by at least one animated composable, proving the motion registry pattern is in place.
**Plans**: 5 plans
Plans:
- [x] 01-01-PLAN.md — BOM upgrade to 2026.03.01, kotlinx-collections-immutable, Compose compiler reports, KotoThemeTest scaffold
- [x] 01-02-PLAN.md — Two-tier color system: Palette.kt (internal) + Colors.kt (KotoColors semantic tokens)
- [x] 01-03-PLAN.md — Inter Variable font bundle + Typography.kt (13-style KotoTypography)
- [x] 01-04-PLAN.md — Spacing.kt, Shapes.kt, Elevation.kt, Motion.kt token files
- [x] 01-05-PLAN.md — Theme.kt (CompositionLocals, animated switching, KotoTheme object) + UiModels.kt (@Immutable)
**UI hint**: yes

### Phase 2: Chat Screen
**Goal**: Users can send and receive messages in a visually premium chat screen that runs at 120fps, with gradient bubbles, animated read receipts, typing indicators, swipe-to-reply, and media previews — all built on stable LazyColumn architecture.
**Depends on**: Phase 1
**Requirements**: CH-01, CH-02, CH-03, CH-04, CH-05, CH-06, CH-07, CH-08, CH-09, CH-10, CH-11, CH-12
**Success Criteria** (what must be TRUE):
  1. Outgoing message bubbles display a violet diagonal gradient (not a flat color); consecutive messages from the same sender within the time window render without a tail, showing smart grouping.
  2. Tapping reply on a message (swipe right) shows a quoted preview with spring snap-back; the reply clears correctly after send.
  3. A custom wave typing indicator appears when the remote user is typing; read receipt icons animate through sent → delivered → read states with morphing transitions.
  4. Long-pressing a message shows a floating context menu with blur backdrop and spring scale animation, not a covering bottom sheet.
  5. The chat list scrolls at 120fps with no visible jank — verified by opening a conversation with 200+ messages and scrolling rapidly; the scroll-to-bottom FAB appears with spring animation and shows the correct unread count.
**Plans**: 5 plans
Plans:
- [x] 02-01-PLAN.md — Foundation fixes: ChatItemMapper, ChatViewModel canonical ChatState, reverseLayout, imePadding, KotoTheme token migration
- [x] 02-02-PLAN.md — Gradient bubbles + grouping + AnimatedContent read receipts (CH-01, CH-02, CH-03)
- [x] 02-03-PLAN.md — TypingWaveIndicator atom + send/receive spring animations (CH-04, CH-11, CH-12)
- [x] 02-04-PLAN.md — SwipeToReplyContainer + ContextMenu overlay (CH-05, CH-06)
- [x] 02-05-PLAN.md — BlurHashDecoder + MediaMessageContent + ScrollToBottomFab (CH-07, CH-08)
**UI hint**: yes

### Phase 3: Conversation List
**Goal**: Users can see all their conversations in a polished list with swipe actions, pinned conversations, live search, and online presence indicators — the entry point that sets first impressions.
**Depends on**: Phase 1
**Requirements**: CL-01, CL-02, CL-03, CL-04, CL-05, CL-06
**Success Criteria** (what must be TRUE):
  1. Each conversation row shows avatar, display name, last message preview, relative timestamp, and unread badge — all correctly laid out and readable in both dark and light themes.
  2. Swiping left on a conversation row exposes an archive action; swiping right exposes pin/mute actions; crossing the swipe threshold produces a haptic pulse.
  3. Pinned conversations appear in a distinct section at the top of the list, separate from regular conversations.
  4. Tapping the search icon expands an animated search bar; typing filters conversations in real time; closing the search bar animates back to the collapsed state.
  5. Pulling down the list triggers a custom spring/liquid pull-to-refresh animation; online contacts show a green pulsing dot on their avatar.
**Plans**: 4 plans
Plans:
- [x] 03-01-PLAN.md — ViewModel migration to ConversationListState (ImmutableList) + ConversationItem atom with OnlineDot (CL-01, CL-06)
- [x] 03-02-PLAN.md — SwipeableConversationItem (AnchoredDraggable + haptics) + pinned section (CL-02, CL-03)
- [x] 03-03-PLAN.md — AnimatedSearchBar (spring expand/collapse) + real-time filtering with debounce (CL-04)
- [x] 03-04-PLAN.md — KotoPullToRefresh (Canvas blob indicator) + full screen integration + human checkpoint (CL-05)
**UI hint**: yes

### Phase 4: Navigation & System Integration
**Goal**: All screens are connected with spring-based transitions, the shared element avatar animation works between the conversation list and chat header, edge-to-edge layout is correct on all screens, and predictive back gesture is supported on Android 14+.
**Depends on**: Phase 2, Phase 3
**Requirements**: NAV-01, NAV-02, NAV-03, NAV-04, NAV-05
**Success Criteria** (what must be TRUE):
  1. The custom glassmorphism bottom navigation bar is visible and functional; tapping tabs navigates between screens with a 300ms spring transition (dampingRatio 0.85, stiffness 380).
  2. Tapping a conversation row morphs the avatar into the chat header avatar via a shared element transition — the morph is smooth with no layout jump or flicker.
  3. The app renders edge-to-edge (content appears behind the status bar and navigation bar) on all screens with correct inset handling — no clipped content, no white bars.
  4. On Android 14+ devices, swiping back from the chat screen triggers the predictive back preview animation and returns to the conversation list without navigating twice.
  5. Keyboard appearance in the chat screen does not cause a layout jump — the input bar slides up smoothly using `imePadding()`.
**Plans**: 3 plans
Plans:
- [x] 04-01-PLAN.md — KotoBottomNavBar: glassmorphism bar + spring tab indicator (NAV-01)
- [x] 04-02-PLAN.md — SharedTransitionLayout + spring screen transitions + avatar sharedElement morph (NAV-02, NAV-05)
- [x] 04-03-PLAN.md — Edge-to-edge inset wiring + PredictiveBackHandler + human checkpoint (NAV-03, NAV-04)
**UI hint**: yes

### Phase 5: Micro-Interactions & Haptics
**Goal**: Every touch point in the app produces the precise physical feedback — visual spring animations and mapped haptics — that makes the app feel alive and premium rather than functional.
**Depends on**: Phase 4
**Requirements**: MI-01, MI-02, MI-03, MI-04, MI-05
**Success Criteria** (what must be TRUE):
  1. The send button visibly morphs between mic icon (when the text field is empty) and arrow icon (when text is present); pressing it animates with a springy scale-down then scale-up.
  2. Double-tapping a message triggers a heart/emoji particle burst animation (particles scatter and fade from the tap point).
  3. Sending a message, receiving a message, long-pressing a bubble, crossing a swipe threshold, and triggering pull-to-refresh each produce a distinct haptic pattern — verified on a physical device.
  4. Long-pressing an avatar reveals a popup profile card with blur backdrop and spring-entry animation; tapping outside dismisses it with spring-exit.
  5. No animation in the app uses a linear or ease curve — all transitions use named spring constants from `KotoMotionSpec`.
**Plans**: 5 plans
Plans:
- [ ] 05-01-PLAN.md — KotoHaptics utility (MI-03) + KotoMotion springMicro/springEmphasized extensions (MI-05)
- [x] 05-02-PLAN.md — MorphingSendButton atom: 4-state morph mic→arrow→progress→checkmark (MI-01, MI-05)
- [ ] 05-03-PLAN.md — AvatarProfileCard atom + ConversationItem long-press wiring (MI-04, MI-05)
- [x] 05-04-PLAN.md — HeartReaction atom + double-tap wiring + haptics integration at all touch points (MI-02, MI-03)
- [ ] 05-05-PLAN.md — Full build + spring audit + human checkpoint (MI-01 through MI-05)
**UI hint**: yes

### Phase 05.1: Security and Bug Fix Sweep (INSERTED)

**Goal:** Close the 17 production-blocking issues surfaced by the 2026-04-24 audit — 7 security defects (Base64 plaintext fallback, unauthenticated prekey bundle, webhook SSRF, WS token in URL query, timing attack on internal secret, sslmode=disable everywhere, OTK non-persistence), 8 reliability bugs (make migrate to wrong DB, rate-limit no-op, WS read loop discards client frames, TOCTOU on prekey IDs, fileID collisions, fmt.Printf in chat service, N+1 ScyllaDB reads on conversation list, bot dispatch never called), and 2 UI stubs (chat context menu + swipe actions). Measured by 13 success criteria (SC-1 through SC-13); no new features.
**Requirements**: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8, SC-9, SC-10, SC-11, SC-12, SC-13 (defined in CONTEXT.md — remediation phase, not in v1 REQUIREMENTS.md table)
**Depends on:** Phase 5
**Plans:** 11 plans

Plans:
- [ ] 05.1-00-PLAN.md — Test infrastructure bootstrap (MockK + coroutines-test, 4 Go test skeletons, 2 shell smoke scripts, 2 Kotlin test skeletons)
- [ ] 05.1-01-PLAN.md — Gateway: httprate 3-tier rate limit + Sec-WebSocket-Protocol auth + WS read-loop dispatcher for typing/read (SC-4, SC-6, SC-7)
- [ ] 05.1-02-PLAN.md — Auth: delete (or JWT-guard) prekey bundle route + migration 003 GENERATED BY DEFAULT AS IDENTITY + concurrent prekey publish test (SC-2, SC-9)
- [ ] 05.1-03-PLAN.md — Chat: GetBatch N+1 fix via IN-list + fmt.Printf → zerolog in UpdateLastMessage (SC-12)
- [ ] 05.1-04-PLAN.md — Bot: SSRF webhook validation + subtle.ConstantTimeCompare + NATS chat.deliver.> consumer (SC-3)
- [ ] 05.1-05-PLAN.md — Media: google/uuid v1.6.0 for fileID + objectKey (SC-12)
- [ ] 05.1-06-PLAN.md — Makefile: -d nova → -d koto, add 3 missing migrations + parameterize docker-compose sslmode (SC-5)
- [ ] 05.1-07-PLAN.md — Android: OneTimePreKeyEntity + DAO + Room v3→v4 MIGRATION_3_4 + CryptoManager reload on init (SC-8)
- [ ] 05.1-08-PLAN.md — Android: ChatRepository blocks Base64 fallback + EncryptionUnavailableException + UI banner (SC-1)
- [ ] 05.1-09-PLAN.md — Android: chat context menu Copy/Delete/Attach wiring via ClipboardManager + deleteMessage + GetContent (SC-10)
- [ ] 05.1-10-PLAN.md — Android: ConversationEntity is_archived/is_pinned/is_muted + DAO mutators + ConversationsViewModel wiring (SC-11)

**UI hint**: no (backend-heavy; 3 light UI wiring touches only)

### Phase 6: Onboarding
**Goal**: New users experience a branded, animated first-run flow — from animated splash to display name entry and permission screens — that communicates the app's premium quality before they send their first message.
**Depends on**: Phase 1
**Requirements**: ON-01, ON-02, ON-03, ON-04
**Success Criteria** (what must be TRUE):
  1. The splash screen shows the Koto logo animating in with a spring settle (not a static image) using the Android 12+ SplashScreen API.
  2. The onboarding flow presents animated Lottie illustrations that play as the user advances through screens.
  3. The display name input field provides smooth inline validation feedback — invalid formats show an animated error state, valid input shows a confirmation indicator without navigation away.
  4. Permission request screens show a friendly explanation of why each permission is needed before the system dialog appears; the user can skip optional permissions without the app blocking.
**Plans**: 4 plans
Plans:
- [ ] 06-01-PLAN.md — Animated splash exit: values-v31/themes.xml + MainActivity OnExitAnimationListener spring scale (ON-01)
- [ ] 06-02-PLAN.md — Lottie dep + IllustratedSlides composable (Security/Privacy/Speed) + spring page dots (ON-02)
- [ ] 06-03-PLAN.md — DisplayNamePage with animated inline validation (error slide-in, checkmark scale-in) (ON-03)
- [ ] 06-04-PLAN.md — PermissionScreen.kt + 3 explanation screens + human checkpoint (ON-04)
**UI hint**: yes

### Phase 7: Performance & Polish
**Goal**: The app's 120fps and <16ms frame time targets are verified and locked in — not aspirational — and the performance infrastructure (Baseline Profiles, compiler stability, API fallbacks) is in place for production.
**Depends on**: Phase 5, Phase 6
**Requirements**: PF-01, PF-02, PF-03, PF-04
**Success Criteria** (what must be TRUE):
  1. Baseline Profiles are generated and bundled; cold start time is measurably faster (target: 30–50% reduction compared to without profiles) on a mid-range Android device (e.g., Pixel 4a equivalent).
  2. Running Compose compiler reports shows zero unstable composables in the chat scroll path (LazyColumn items, `MessageBubble`, `ConversationRow`) — every item in the scroll-critical paths is skippable.
  3. Scrolling a 200-message chat screen shows <16ms frame times in the Android GPU profiler — no frame exceeds 32ms (no dropped frames at 60fps minimum, smooth at 120fps on capable hardware).
  4. On a device running Android 10 (API 29), the app renders without crashing — glassmorphism surfaces fall back to semi-transparent tinted surfaces with no blur, and no `RenderEffect` call is made.

## Progress

**Execution Order:**
Phases execute in order: 1 → 2 → 3 → 4 → 5 → 6 → 7 (Phases 2 and 3 can be parallelized; Phase 4 requires both)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Design System Foundation | 5/5 | Complete | 2026-04-05 |
| 2. Chat Screen | 5/5 | Complete   | 2026-04-05 |
| 3. Conversation List | 4/4 | Complete   | 2026-04-05 |
| 4. Navigation & System Integration | 3/3 | Complete   | 2026-04-05 |
| 5. Micro-Interactions & Haptics | 2/5 | In Progress|  |
| 05.1. Security and Bug Fix Sweep | 0/11 | Planned    | - |
| 6. Onboarding | 0/4 | Not started | - |
| 7. Performance & Polish | 0/TBD | Not started | - |
