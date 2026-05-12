# Requirements: Koto Messenger — Premium UI/UX

**Defined:** 2026-04-05
**Core Value:** Визуально безупречный, моментально отзывчивый мессенджер с уникальным дизайном

## v1 Requirements

### Design System (DS)

- [x] **DS-01**: Custom KotoTheme с semantic color tokens (primary, surface, onSurface, etc.) для light/dark
- [x] **DS-02**: Цветовая палитра: deep violet primary (#7B61FF), gradient sent bubbles (#7C3AED→#5B21B6)
- [x] **DS-03**: Typography scale на базе Inter font (display, title, body, label — 8 стилей)
- [x] **DS-04**: Spacing scale (xxs 2dp → xxxl 48dp) как design tokens
- [x] **DS-05**: Shape system (rounded corners: sm 8dp, md 12dp, lg 16dp, xl 20dp, bubble 18dp)
- [x] **DS-06**: Animated theme switching (dark ↔ light) с circular reveal или crossfade
- [x] **DS-07**: @Immutable UI model classes для всех data objects (zero unnecessary recompositions)

### Chat Screen (CH)

- [x] **CH-01**: Gradient chat bubbles — sent messages с violet gradient, received с surface variant
- [x] **CH-02**: Message grouping по sender + time proximity с smart tail/no-tail логикой
- [x] **CH-03**: Read receipts анимация (sent → delivered → read) с плавным morphing иконок
- [ ] **CH-04**: Typing indicator — custom wave animation (не стандартные 3 точки)
- [ ] **CH-05**: Reply (swipe right) с quoted message preview и spring snap-back
- [ ] **CH-06**: Long press context menu с blur backdrop и spring scale animation
- [ ] **CH-07**: Image/media preview в чате с BlurHash placeholder
- [ ] **CH-08**: Scroll-to-bottom FAB с unread count badge и spring animation
- [x] **CH-09**: LazyColumn 120fps (keys, stable params, deferred state reads)
- [x] **CH-10**: Keyboard handling: smooth imePadding, no layout jumps
- [ ] **CH-11**: Message send animation: bubble scale-up → spring settle
- [ ] **CH-12**: Message receive animation: slide-in from left с spring physics

### Conversation List (CL)

- [x] **CL-01**: Conversation item: avatar + name + last message preview + timestamp + unread badge
- [ ] **CL-02**: Swipe actions (archive, pin, mute) с haptic feedback на threshold
- [ ] **CL-03**: Pinned conversations section at top
- [ ] **CL-04**: Search с animated expand/collapse transition
- [x] **CL-05**: Pull-to-refresh с custom spring/liquid animation
- [x] **CL-06**: Online/last seen indicator на avatar (green dot с pulse animation)

### Navigation (NAV)

- [x] **NAV-01**: Custom bottom navigation bar с glassmorphism effect
- [x] **NAV-02**: Shared element transition: conversation item → chat header (avatar morph)
- [x] **NAV-03**: Edge-to-edge design (behind status bar + navigation bar)
- [x] **NAV-04**: Predictive back gesture support (Android 14+)
- [x] **NAV-05**: Smooth screen transitions (300ms spring, not linear)

### Micro-Interactions (MI)

- [ ] **MI-01**: Send button morphing animation (mic → arrow → checkmark)
- [x] **MI-02**: Double-tap to react с heart/emoji particle animation
- [x] **MI-03**: Haptic feedback mapping (send, receive, long-press, swipe threshold, pull-to-refresh)
- [ ] **MI-04**: Avatar long-press: popup profile card с blur backdrop
- [ ] **MI-05**: Spring-based animations everywhere (не linear/ease)

### Onboarding (ON)

- [ ] **ON-01**: Animated splash screen (logo animation с spring settle)
- [ ] **ON-02**: Onboarding flow с animated illustrations (Lottie)
- [ ] **ON-03**: Phone/email input с smooth validation feedback
- [ ] **ON-04**: Permission requests с friendly explanation screens

### Performance (PF)

- [ ] **PF-01**: Baseline profiles для 30-50% faster cold start
- [ ] **PF-02**: Compose compiler reports enabled — zero unstable composables in scroll path
- [ ] **PF-03**: <16ms frame time в chat scroll (verified с Layout Inspector)
- [ ] **PF-04**: Glassmorphism fallback для API <31 (semi-transparent surface, no blur)

## v2 Requirements

### Advanced Features

- **ADV-01**: Per-chat custom themes (user-selectable accent colors)
- **ADV-02**: Animated stickers (TGS/Lottie) в чате
- **ADV-03**: Voice message waveform visualization
- **ADV-04**: Tablet/foldable adaptive layout
- **ADV-05**: Custom emoji reactions picker с categories
- **ADV-06**: Chat wallpapers с parallax effect

## Out of Scope

| Feature | Reason |
|---------|--------|
| iOS app | Android first — отдельный проект позже |
| Web frontend | Уже есть отдельный nova-web проект |
| Backend changes | Backend полностью готов (7 Go сервисов) |
| New features (calls, stories) | Только UI/UX текущего функционала |
| XML layouts | Compose only, no legacy XML |
| Third-party UI kits | Custom design system, не готовые компоненты |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DS-01 | Phase 1 — Design System Foundation | Complete |
| DS-02 | Phase 1 — Design System Foundation | Complete |
| DS-03 | Phase 1 — Design System Foundation | Complete |
| DS-04 | Phase 1 — Design System Foundation | Complete |
| DS-05 | Phase 1 — Design System Foundation | Complete |
| DS-06 | Phase 1 — Design System Foundation | Complete |
| DS-07 | Phase 1 — Design System Foundation | Complete |
| CH-01 | Phase 2 — Chat Screen | Complete |
| CH-02 | Phase 2 — Chat Screen | Complete |
| CH-03 | Phase 2 — Chat Screen | Complete |
| CH-04 | Phase 2 — Chat Screen | Pending |
| CH-05 | Phase 2 — Chat Screen | Pending |
| CH-06 | Phase 2 — Chat Screen | Pending |
| CH-07 | Phase 2 — Chat Screen | Pending |
| CH-08 | Phase 2 — Chat Screen | Pending |
| CH-09 | Phase 2 — Chat Screen | Complete |
| CH-10 | Phase 2 — Chat Screen | Complete |
| CH-11 | Phase 2 — Chat Screen | Pending |
| CH-12 | Phase 2 — Chat Screen | Pending |
| CL-01 | Phase 3 — Conversation List | Complete |
| CL-02 | Phase 3 — Conversation List | Pending |
| CL-03 | Phase 3 — Conversation List | Pending |
| CL-04 | Phase 3 — Conversation List | Pending |
| CL-05 | Phase 3 — Conversation List | Complete |
| CL-06 | Phase 3 — Conversation List | Complete |
| NAV-01 | Phase 4 — Navigation & System Integration | Complete |
| NAV-02 | Phase 4 — Navigation & System Integration | Complete |
| NAV-03 | Phase 4 — Navigation & System Integration | Complete |
| NAV-04 | Phase 4 — Navigation & System Integration | Complete |
| NAV-05 | Phase 4 — Navigation & System Integration | Complete |
| MI-01 | Phase 5 — Micro-Interactions & Haptics | Pending |
| MI-02 | Phase 5 — Micro-Interactions & Haptics | Complete |
| MI-03 | Phase 5 — Micro-Interactions & Haptics | Complete |
| MI-04 | Phase 5 — Micro-Interactions & Haptics | Pending |
| MI-05 | Phase 5 — Micro-Interactions & Haptics | Pending |
| ON-01 | Phase 6 — Onboarding | Pending |
| ON-02 | Phase 6 — Onboarding | Pending |
| ON-03 | Phase 6 — Onboarding | Pending |
| ON-04 | Phase 6 — Onboarding | Pending |
| PF-01 | Phase 7 — Performance & Polish | Pending |
| PF-02 | Phase 7 — Performance & Polish | Pending |
| PF-03 | Phase 7 — Performance & Polish | Pending |
| PF-04 | Phase 7 — Performance & Polish | Pending |

**Coverage:**
- v1 requirements: 42 total
- Mapped to phases: 42
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-05*
*Last updated: 2026-04-05 after roadmap creation — phase names added to traceability*
