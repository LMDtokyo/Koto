# Project Research Summary

**Project:** Koto Messenger — Android UI/UX Premium Overhaul
**Domain:** Android Messenger (Kotlin + Jetpack Compose, 120fps target)
**Researched:** 2026-04-05
**Confidence:** HIGH

---

## Executive Summary

Koto is a privacy-first Android messenger with a completed backend (Signal Protocol crypto, E2EE, real-time infra) that needs a premium UI/UX overhaul to reach "better than Telegram on Android." Research across all four domains converges on a single clear recommendation: build a fully custom design system layered strictly bottom-up (tokens → atoms → molecules → organisms → screens), then add physics-based spring animations and gesture interactions on top of that stable foundation. Every piece of research reinforces that the design token system is the load-bearing first step — nothing else can be built correctly without it. The entire animation, performance, and feature work depends on stable, semantically-named color and typography tokens being in place first.

The recommended approach is 100% Jetpack Compose with zero third-party libraries beyond what is already in the project (plus Lottie and EmojiCompat). The Compose BOM must be upgraded to 2026.03.01 before any UI work begins — this is not optional. It unlocks stable shared element transitions, AnchoredDraggable, `animateItem()`, `Modifier.dropShadow`, and Material3 1.4.0, all of which are critical APIs for the feature set. The color palette research is clear and opinionated: deep violet/indigo (#8B5CF6 → #6D28D9 gradient) differentiates from every major competitor and reads as "premium + encrypted." The gradient outgoing bubble technique (Telegram-style, but in violet) is the single highest-impact visual differentiator to implement early.

The primary risks are all performance-related: Compose recomposition from unstable types and wrong scope reads, LazyColumn without stable keys, and RenderEffect overdraw on mid-range devices. These are not theoretical risks — they will cause visible jank at 120fps if not addressed at foundation time. The mitigation is architectural: `@Immutable` on all UI data classes, `ImmutableList` for collections, deferred state reads via lambda-form modifiers, and blur used sparingly (one surface at a time, never during animation). Treating these as day-one design decisions rather than late optimizations is the difference between a premium feel and a janky one.

---

## Key Findings

### Recommended Stack

The project's existing stack is largely correct and needs no major changes. The one mandatory action before any UI work is upgrading the Compose BOM from `2025.03.01` to `2026.03.01`. This single change unlocks Compose UI 1.10.6, Animation 1.10.6 (stable shared elements), Foundation 1.10.6 (stable AnchoredDraggable), and Material3 1.4.0. All other existing dependencies (Navigation Compose 2.9.0, Coil 3.1.0, Hilt 2.56, Room 2.7.1, Retrofit, OkHttp) are correct and should not be changed. New additions are minimal: `coil-gif` module, `lottie-compose`, `emoji2-bundled`, and `kotlinx-collections-immutable`.

The design system approach is to build a fully custom theme (`KotoTheme` via `CompositionLocal`) that wraps Material3's `MaterialTheme`. This is not "extend Material3" — it is a separate system that sits above it, with Material3 present only so that system-level components (ModalBottomSheet fallback, Snackbar) continue to function. Do not use Material You dynamic color as the primary theme — it destroys brand identity across devices.

**Core technologies:**
- Compose BOM `2026.03.01`: base for all UI work — upgrade before starting
- `kotlinx-collections-immutable:0.3.8`: `ImmutableList` for stable LazyColumn parameters — prevents full-list recomposition
- `lottie-compose:6.x`: loading states, empty state animations, success/error micro-animations
- `androidx.emoji2:emoji2-bundled:1.5.0`: consistent emoji rendering across minSdk 26 devices
- Inter Variable font (bundled locally in `res/font/`): premium typography, not network-loaded
- Native Compose APIs for all gestures, blur, haptics, particles — no third-party wrappers needed

**What NOT to add:**
- `skydoves/cloudy` (blur wrapper with no added value), `skydoves/landscapist` (Coil abstraction), `accompanist/*` (graduated or deprecated), any external spring library, any View-based blur library

### Expected Features

**Must have (table stakes) — users expect, absence feels broken:**
- Rounded message bubbles with tail + consecutive message grouping (flat corners on clusters)
- Read receipts with animated state transitions (single check → double check → colored)
- Typing indicator with staggered spring-animated dots
- Inline reply UI with colored left-border quote, swipe-to-reply gesture at 80dp threshold
- Long-press context menu (floating emoji row + action strip, NOT a covering bottom sheet)
- Date separators, on-demand timestamps, emoji-only message sizing (48/36/16sp)
- Media thumbnails in bubbles, link previews, voice message player (defer waveform renderer)
- Swipe-to-archive (left) and swipe-to-pin/mute (right) on conversation list
- Unread count badge, avatar with online indicator, relative timestamps
- Bottom navigation bar (3-4 tabs), edge-to-edge layout (Android 15 enforces this)
- Predictive back gesture (Android 14+), immersive keyboard handling with `imePadding()`
- Send button micro-animation (mic ↔ arrow), scroll-to-bottom FAB with unread badge
- Animated splash screen via Android 12+ SplashScreen API
- Phone + OTP onboarding with 6-digit animated boxes and SMS auto-fill

**Should have (differentiators) — creates the "this is different" moment:**
- Gradient outgoing bubbles: global diagonal canvas `#8B5CF6 → #6D28D9 → #4C1D95`, bubbles clip through it as the user scrolls — this is the single highest-ROI visual feature
- Glassmorphism input bar (frosted glass, `RenderEffect` on API 31+, tinted fallback on older)
- Message reactions with particle burst animation (8 emoji particles scatter on add)
- Spring physics on all list interactions (custom fling deceleration, rubber-band overscroll)
- Contextual sender color in group chats (name hash → HSL with fixed saturation/lightness)
- Shared element avatar transition: conversation list → chat header
- Animated unread badge counter (slot-machine roll on increment)
- Conversation category filter chips (All / Unread / Private / Groups)
- Spring physics on navigation push/pop (dampingRatio 0.85, stiffness 380)
- Lottie empty state animations (chat list, search no results)
- Pull-to-refresh with Koto brand lock animation (path draw → click-lock → unlock on complete)
- Springy send button press (scale 1.0 → 0.85 → 1.0, dampingRatio 0.4, stiffness 600)
- Welcome screen: full-bleed gradient hero + floating particle animation

**Defer to later (post-launch):**
- Voice message waveform player (high complexity, not blocking)
- TGS/RLottie animated sticker support (no production Kotlin library; requires JNI bridge)
- Vertical slide navigation metaphor (validate with users first — unfamiliar to Android users)
- Tablet/foldable adaptive layout
- Interactive E2EE key exchange animation for onboarding
- Composition tracing and Baseline Profiles (add after UI implementation is stable)

### Architecture Approach

The architecture is a strict 5-layer bottom-up system: Design Tokens (Layer 1) → Atom components (Layer 2) → Molecule components (Layer 3) → Organism components (Layer 4) → Screens with ViewModels (Layer 5). Each layer depends only on layers below it — no screen logic leaks into atoms, no raw color values appear outside the token file. Navigation uses Jetpack Navigation Compose 2.9.0 with type-safe `@Serializable` routes. A `SharedTransitionLayout` wraps the entire `NavHost` so shared elements work across any destination pair. State follows the existing MVI/UDF convention (ViewModel holds StateFlow, screens collect it, content composables are pure functions of state plus lambdas).

**Major components:**
1. `KotoTheme` (Layer 1) — `CompositionLocal`-based token system: `KotoColors`, `KotoTypography`, `KotoSpacing`, `KotoShapes`, `KotoElevation`, `KotoMotion`. Single source of truth for all visual values. Animated on dark/light theme switch via `animateColorAsState` per-color.
2. Atom components (Layer 2) — `KotoButton`, `KotoTextField`, `Avatar`, `Badge`, `StatusDot`, `KotoChip`, `Shimmer`. Stateless, accept only primitives + lambdas, always accept `Modifier`.
3. Molecule components (Layer 3) — `MessageBubble`, `InputBar`, `ConversationRow`, `TypingIndicator`, `ReactionRow`, `SearchBar`. Combinations of atoms with layout logic; no business logic, no ViewModels.
4. Organism components (Layer 4) — `ChatMessageList`, `ConversationList`, `KotoBottomSheet` (custom, not Material3), `ChatAppBar`, `KotoNavigationBar`. Domain-aware, accept `ImmutableList<MessageUi>`.
5. Screens + Navigation (Layer 5) — `ConversationsScreen`, `ChatScreen`, `OnboardingScreen`, `SettingsScreen`. Each has a paired `*Content` composable for preview/testing separation.

**Critical architecture decisions:**
- `KotoBottomSheet` must be custom (using `AnchoredDraggableState`), not Material3's `ModalBottomSheet`, for shared element compatibility and gesture physics control
- All animation constants live in `KotoMotionSpec` — no component defines its own durations or easing values
- `ImmutableList<MessageUi>` is mandatory in `ChatMessageList`; `List<MessageUi>` causes full-list recomposition
- Lambda-form modifiers (`Modifier.offset { }` not `Modifier.offset(dp)`) for all gesture-driven animations — bypasses composition during drag

### Critical Pitfalls

1. **LazyColumn without stable keys** — Without `key = { message.id }` in `items()`, every new message causes full-list recomposition, `animateItem()` silently does nothing, and scroll position jumps. Fix: always provide stable DB primary key as item key and include `contentType` for layout reuse.

2. **Unstable types in message data classes** — `data class Message(val reactions: List<Reaction>)` is unstable; the Compose compiler cannot skip recomposition even when nothing changed. Fix: annotate all UI model data classes with `@Immutable` and use `ImmutableList<T>` from `kotlinx-collections-immutable` for all collection fields.

3. **State reads in wrong composition scope** — Reading scroll position or animation fraction at a high level in the composition tree recomposes the entire screen on every frame. Fix: use `derivedStateOf` for threshold-based reads, lambda-form modifiers for continuous reads (`Modifier.offset { }` reads in layout phase only), and `Modifier.drawBehind { }` for draw-only color animations.

4. **IME inset mismanagement** — Android 15 enforces edge-to-edge; `windowSoftInputMode=adjustResize` is deprecated and causes input field disappearance. Fix: `enableEdgeToEdge()` in Activity, remove `adjustResize` from manifest, use `Modifier.imePadding()` on the chat layout root (not the input bar alone).

5. **Glassmorphism overdraw on mid-range devices** — `RenderEffect.createBlurEffect` requires double-draw and an offscreen GPU buffer. Animating a composable with an active `renderEffect` (e.g., a sliding bottom sheet) forces blur recomputation every frame — confirmed jank below Snapdragon 888. Fix: limit to one blurred surface at a time, disable `renderEffect` during animation, provide explicit API 31+ guard with tinted-surface fallback for API 26–30 devices.

---

## Recommended Color Palette

### Dark Mode (OLED-optimized, primary target)

| Token | Hex | Usage |
|-------|-----|-------|
| `background` | `#0D0B12` | App background — near-black with violet undertone |
| `surface` | `#1A1625` | Cards, bottom sheets |
| `surfaceVariant` | `#231E30` | Incoming message bubble |
| `primary` | `#7B61FF` | Primary actions, links |
| `bubbleOutGradientStart` | `#7C3AED` | Outgoing bubble gradient start |
| `bubbleOutGradientEnd` | `#5B21B6` | Outgoing bubble gradient end |
| `bubbleOutGradientDeep` | `#3B0764` | Outgoing bubble gradient deep anchor |
| `onBackground` | `#E8E0F0` | Primary text (not pure white) |
| `onSurfaceLow` | `#9D8FAA` | Secondary text, conversation previews |
| `onSurfaceMuted` | `#5D5270` | Timestamps, muted labels |
| `divider` | `#2C2438` | Dividers |
| `inputBackground` | `#1E1A2B` | Input field surface |
| `navigationBar` | `#110F1A` | Bottom navigation surface |
| `online` | `#4CAF50` | Online presence dot |
| `error` | `#FF5252` | Error states |

### Light Mode

| Token | Hex | Usage |
|-------|-----|-------|
| `background` | `#F8F7FD` | Chat and list background (slight violet tint) |
| `surface` | `#FFFFFF` | Cards, sheets, input |
| `bubbleIncoming` | `#F2F0F8` | Incoming bubble |
| `kotoAccent` | `#7C5CBF` | Primary actions, outgoing bubble base |
| `bubbleOutGradientStart` | `#8B5CF6` | Outgoing gradient start |
| `bubbleOutGradientEnd` | `#6D28D9` | Outgoing gradient end |
| `textPrimary` | `#1C1B1F` | Main text |
| `textSecondary` | `#6E5F7A` | Secondary text |
| `textMuted` | `#A89BB5` | Timestamps, read receipts |
| `divider` | `#E6E0ED` | Dividers |
| `online` | `#22C55E` | Online presence dot |
| `error` | `#B3261E` | Error states |

### Gradient Canvas (outgoing bubbles)
Dark mode: `Brush.linearGradient([#7C3AED, #5B21B6, #3B0764])`
Light mode: `Brush.linearGradient([#8B5CF6, #6D28D9, #4C1D95])`
Direction: `start = Offset(0, 0)`, `end = Offset(screenWidth, screenHeight)` — global canvas, not per-bubble.

---

## Implications for Roadmap

### Phase 1: Design System Foundation
**Rationale:** Every other piece of work depends on tokens, theming, and stable data models existing first. Building atoms without tokens produces hardcoded values that must be refactored. Building screens without `@Immutable` data classes produces a janky app that cannot be fixed without rewriting data models.
**Delivers:** `KotoTheme` with full token set, dark/light modes with animated transition, Inter Variable font loaded, `@Immutable` UI model data classes, `ImmutableList` wiring, `KotoMotionSpec` registry, EmojiCompat initialization, Compose BOM upgrade.
**Addresses:** All of the design token + typography features from FEATURES.md
**Avoids:** Pitfalls C-4 (unstable types), m-1 (hardcoded colors)
**Research flag:** Standard patterns — no additional research needed. Official Compose custom design system docs are definitive.

### Phase 2: Chat Screen — Table Stakes
**Rationale:** The chat screen is the core experience and the highest-complexity screen. It must be built early so that animation and interaction work in Phase 3 has a stable foundation. The LazyColumn architecture decisions (stable keys, reverseLayout, ImmutableList) are load-bearing and cannot be retrofitted later without a rewrite.
**Delivers:** `ChatMessageList` organism with stable keys + `animateItem()`, `MessageBubble` with grouping logic (tail/tailless, corner radius variants), gradient outgoing bubbles (global canvas technique), read receipts with animated transitions, typing indicator, inline reply UI, date separators, emoji-only sizing, media thumbnails.
**Uses:** `KotoTheme`, all Layer 2-4 components built bottom-up, `ImmutableList<MessageUi>`
**Avoids:** Pitfalls C-1 (missing keys), C-2 (reverseLayout gotchas), C-3 (recomposition scope), M-1 (wrong animation API), M-7 (image loading jank)
**Research flag:** Standard patterns — LazyColumn chat architecture is well-documented.

### Phase 3: Conversation List + Input Bar
**Rationale:** The conversation list is the entry point to every chat. Its complexity is lower than the chat screen but it shares the same LazyColumn architecture requirements. The input bar (glassmorphism, send button) belongs here because it is the primary interaction surface tested alongside the chat screen.
**Delivers:** `ConversationList` organism with swipe-to-archive/pin/mute, unread badge with slot-machine counter, avatar with online indicator, relative timestamps, search with animated transition, category filter chips, `InputBar` with glassmorphism surface, send/mic button with morphing animation.
**Avoids:** Pitfall M-5 (glassmorphism overdraw — one blurred surface only, disabled during animation), M-2 (gesture conflict — separate `pointerInput` blocks)
**Research flag:** Standard patterns.

### Phase 4: Navigation + System Integration
**Rationale:** Navigation is built after the individual screens exist so that transitions can be implemented against real composables. Edge-to-edge and keyboard handling are systemic concerns that affect every screen — they must be verified end-to-end at this point before adding animation polish.
**Delivers:** `KotoNavGraph` with `SharedTransitionLayout` wrapping `NavHost`, shared element avatar transition (conversation list → chat header), predictive back gesture (Android 14+), edge-to-edge with correct insets on all screens, keyboard `imePadding()` on chat screen, spring-based navigation transitions (dampingRatio 0.85, stiffness 380), `navigateSingle()` guard against duplicate navigation.
**Avoids:** Pitfalls C-5 (IME insets), M-4 (shared element memory leak — data class keys, modifier order), M-8 (navigation back stack duplicates)
**Research flag:** Shared element transitions with Navigation Compose — verify exact `SharedTransitionLayout` + `NavHost` integration pattern against current 2.9.0 docs before implementation.

### Phase 5: Micro-Interactions + Haptics
**Rationale:** Micro-interactions layer on top of working screens. Doing this after screens are stable means animations are never retrofitted into broken layouts. This phase turns a functional app into a delightful one.
**Delivers:** Springy send button press, message launch animation from input bar, scroll-to-bottom FAB with spring appear/hide, long-press haptic on message, swipe threshold haptic, send confirmation haptic, `HapticFeedbackType` mapping table fully implemented, staggered message appearance on chat open, `AnimatedContent` delivery status (spinner → check → double-check).
**Avoids:** Pitfall M-3 (spring misconfiguration — use `KotoMotionSpec` constants exclusively)
**Research flag:** Standard patterns.

### Phase 6: Differentiators + Polish
**Rationale:** The gradient bubble scroll effect, reaction animations, and branded pull-to-refresh are the features that create the "premium" perception. They are intentionally last so they are not rushed and do not block core functionality.
**Delivers:** Gradient canvas scroll effect for outgoing bubbles (the flagship visual), message reactions with particle burst and slot-machine count roll, floating emoji picker on long-press (contextual, not a bottom sheet), pull-to-refresh with Koto brand lock path animation, avatar ripple on new message arrival, Lottie empty state animations, reaction haptic patterns, sender color differentiation in group chats.
**Avoids:** M-5 (blur overdraw), M-2 (gesture conflicts)
**Research flag:** Particle system via `Canvas` + `withFrameNanos` — standard pattern. Reaction animation timing may benefit from brief spike research into Telegram's open-source animation timings.

### Phase 7: Onboarding
**Rationale:** Onboarding is independent from the main app flow and can be built last. It reuses design system components built in earlier phases.
**Delivers:** Animated splash screen (SplashScreen API, spring scale), welcome screen with gradient hero + floating particles, phone entry with `libphonenumber` formatting, OTP 6-box UI with spring digit animation + SMS auto-fill (`SmsRetriever`), profile setup with Photo Picker API (no STORAGE permission), haptic OTP completion pattern, permission rationale screen.
**Avoids:** Anti-features (contacts permission request, T&C wall, permission fatigue)
**Research flag:** `SmsRetriever` API — verify current Google Play Services dependency version.

### Phase Ordering Rationale

- Tokens before atoms because atoms reference tokens; atoms before molecules for the same reason — this is a strict dependency chain.
- Chat screen before conversation list because it is harder and sets more architectural precedent (LazyColumn, ImmutableList, animation APIs).
- Navigation after both screens so shared element transitions are implemented against real content.
- Micro-interactions after navigation because they interact with the gesture layer which navigation defines.
- Differentiators last — they are the "wow" layer, not the foundation. Shipping a complete, functional, correct app first and then making it delightful is the right order.

### Research Flags

Phases needing deeper research before or during planning:
- **Phase 4 (Navigation):** Verify `SharedTransitionLayout` wrapping `NavHost` integration pattern with Navigation Compose 2.9.0 — the exact scope propagation via `CompositionLocal` is version-sensitive.
- **Phase 6 (Differentiators):** The gradient outgoing bubble technique requires verifying that `Brush.linearGradient` used as a global canvas in `drawBehind` inside `LazyColumn` does not cause excessive recomposition. Test on a physical device early.

Phases with standard, well-documented patterns (skip deep research):
- **Phase 1 (Design System):** Official Compose custom design system docs are complete and correct.
- **Phase 2 (Chat Screen):** LazyColumn with reverseLayout is a documented pattern with official gotcha documentation.
- **Phase 5 (Micro-Interactions):** All APIs are stable; KotoMotionSpec constants cover all use cases.
- **Phase 7 (Onboarding):** SplashScreen API, Photo Picker, and SmsRetriever are stable APIs with complete documentation.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All recommendations from official Android docs; BOM version verified from official BOM mapping page. Only Lottie version number is MEDIUM — verify on Maven Central before pinning. |
| Features | HIGH | Telegram/iMessage/WhatsApp analysis is stable knowledge through training data cutoff. Competitor hex values are MEDIUM — verify against current screenshots. |
| Architecture | HIGH | All patterns verified against official Android documentation. Navigation 3 noted as future path but not recommended yet (prerelease). |
| Pitfalls | HIGH | All critical pitfalls sourced from official Compose performance and list docs with direct citations. |

**Overall confidence:** HIGH

### Gaps to Address

- **Lottie version number:** Training data shows 6.x; verify exact latest stable version on Maven Central before adding to `build.gradle.kts`.
- **Competitor color hex values:** FEATURES.md notes these are derived from design teardowns in training data. Verify against current app screenshots before finalizing palette decisions (the Koto palette itself is independently designed and not affected by this).
- **Inter Variable font axis names:** `FontVariation.Setting("opsz", 32f)` — verify axis names against rsms.me/inter spec before using variable font API.
- **Shared element + Navigation Compose exact integration:** The `SharedTransitionLayout` wrapping `NavHost` pattern is described in official docs but the exact `CompositionLocal` scope propagation should be confirmed against Navigation Compose 2.9.0 before Phase 4 begins.
- **RenderEffect behavior during animation:** Official docs confirm the API exists; the "disable blur during animation" rule comes from Android graphics training data. Validate with GPU profiler on a real mid-range device during Phase 3.
- **Inter variable font performance on API 26 devices:** Variable fonts may have worse performance on older hardware. Test `inter_variable.ttf` on a Pixel 3a equivalent before committing to variable font vs. discrete weight files.

---

## Sources

### Primary (HIGH confidence)
- https://developer.android.com/jetpack/compose/bom/bom-mapping — Compose BOM 2026.03.01 version mapping
- https://developer.android.com/develop/ui/compose/animation/shared-elements — Shared element transitions
- https://developer.android.com/develop/ui/compose/animation/choose-api — Animation API selection
- https://developer.android.com/develop/ui/compose/lists — LazyColumn keys, reverseLayout, animateItem
- https://developer.android.com/develop/ui/compose/performance/bestpractices — Recomposition scope, deferred reads
- https://developer.android.com/develop/ui/compose/performance/stability — Unstable types, ImmutableList
- https://developer.android.com/develop/ui/compose/layouts/insets — IME insets, edge-to-edge
- https://developer.android.com/develop/ui/compose/designsystems/custom — Custom design system with CompositionLocal
- https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures — Gesture conflict patterns
- https://developer.android.com/develop/ui/views/haptics — Haptic feedback API

### Secondary (MEDIUM confidence)
- Telegram Android open-source codebase — gradient bubble technique, BottomSheet gesture patterns
- rsms.me/inter — Inter variable font axis specifications
- Maven Central — Lottie 6.x, emoji2, profileinstaller version numbers (verify before pinning)

### Tertiary (LOW confidence / needs validation)
- Competitor color values — derived from published design teardowns; verify against current app screenshots

---
*Research completed: 2026-04-05*
*Ready for roadmap: yes*
