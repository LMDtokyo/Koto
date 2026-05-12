# Koto Messenger — Premium UI/UX

## What This Is

Koto — приватный мессенджер с end-to-end шифрованием (Signal Protocol + PQXDH) и премиальным UI/UX уровня iPhone 17 + Telegram. Android-приложение на Kotlin + Jetpack Compose с кастомными анимациями, нестандартными компонентами и плавностью 120fps. Бэкенд (7 Go-микросервисов) уже реализован — фокус этого milestone на UI/UX фронтенда.

## Core Value

Визуально безупречный, моментально отзывчивый мессенджер, который ощущается лучше Telegram и нативнее iOS — на Android.

## Requirements

### Validated

- ✓ Backend microservices (auth, user, chat, media, notification, bot, gateway) — existing
- ✓ Signal Protocol E2E encryption (X3DH + PQXDH + Double Ratchet) — existing
- ✓ Android app base (Compose, Room, Retrofit, Hilt) — existing
- ✓ WebSocket real-time messaging — existing
- ✓ Docker Compose infrastructure — existing

### Active

- [ ] Premium design system (цвета, типографика, spacing, shadows, glassmorphism)
- [ ] Кастомные анимации (shared element transitions, spring physics, gesture-driven)
- [ ] Плавный chat UI (lazy list 120fps, bubble animations, typing indicators)
- [ ] Нестандартные компоненты (custom navigation, bottom sheet, FAB, pull-to-refresh)
- [ ] Dark/Light тема с плавным переключением
- [ ] Haptic feedback и micro-interactions
- [ ] Splash screen + onboarding с анимациями
- [ ] Adaptive layout (tablet/foldable support)
- [ ] Performance: <16ms frame time, 0 jank, instant navigation

### Out of Scope

- iOS app — Android first
- Web frontend — отдельный проект (nova-web)
- Backend changes — бэкенд уже готов
- New features (calls, stories) — только UI/UX текущего функционала

## Context

- Текущий Android UI — базовый Compose без кастомных анимаций и дизайн-системы
- Бэкенд полностью функционален (7 сервисов + infra в Docker)
- Signal Protocol crypto интегрирован в Android
- Целевая аудитория: пользователи, ценящие приватность И премиальный UX
- Конкуренты: Telegram (скорость, UX), Signal (приватность), iMessage (polish)

## Constraints

- **Platform**: Android (minSdk 26, Kotlin + Jetpack Compose)
- **Performance**: 120fps target, <100ms touch response, <16ms frame time
- **Existing code**: Не ломать существующий функционал (crypto, networking, Room DB)
- **Libraries**: Предпочтение нестандартным, современным библиотекам (не Material Design "из коробки")

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Compose-first (no XML) | Современный, декларативный, лучше для анимаций | — Pending |
| Custom design system over Material3 defaults | Уникальный look, не "ещё одно Material приложение" | — Pending |
| Spring-based animations | Более естественные, как iOS | — Pending |

---
*Last updated: 2026-04-05 after initialization*
