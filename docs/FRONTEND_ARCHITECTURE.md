# Архитектура фронтенда

Полное руководство по архитектуре клиентских приложений коммуникационной платформы.

---

## Оглавление

1. [Архитектура приложения](#1-архитектура-приложения)
2. [Структура проекта (apps/web/)](#2-структура-проекта-appsweb)
3. [State Management (Zustand)](#3-state-management-zustand)
4. [WebSocket Client](#4-websocket-client)
5. [REST API Client](#5-rest-api-client)
6. [Компоненты](#6-компоненты)
7. [Формы и валидация](#7-формы-и-валидация)
8. [Интернационализация (i18n)](#8-интернационализация-i18n)
9. [Тестирование](#9-тестирование)
10. [Сборка и оптимизация](#10-сборка-и-оптимизация)
11. [Tauri 2 (Desktop)](#11-tauri-2-desktop)
12. [React Native (Mobile)](#12-react-native-mobile)
13. [Источники](#источники)

---

## 1. Архитектура приложения

### Стек

| Технология | Назначение | Версия |
|-----------|-----------|--------|
| React | UI-фреймворк | 19 |
| TypeScript | Статическая типизация | 5.7+ |
| Rspack | Сборщик (написан на Rust, 10x быстрее Webpack) | latest |
| Zustand | Стейт менеджмент | 5+ |
| React Aria Components | Доступные UI-примитивы (Adobe) | latest |
| Radix UI | Компоненты (checkbox, switch, radio) | latest |
| Tailwind CSS | Стили (utility-first) | v4 |
| Framer Motion | Анимации | latest |
| React Hook Form + Zod | Формы и валидация | latest |
| Lingui | Интернационализация (i18n) | latest |
| Vitest | Тесты | latest |
| Biome | Линтер и форматтер | latest |
| pnpm | Пакетный менеджер | 9+ |

### Клиентские приложения

```
apps/
├── web/          # React + Rspack + TypeScript
├── desktop/      # Tauri 2 (Rust backend + WebView)
└── mobile/       # React Native / Expo
```

### Высокоуровневая схема

```
┌─── React App ──────────────────────────────────────────┐
│                                                         │
│  ┌── Pages ──────────────────────────────────────────┐  │
│  │  Login | Register | Guild | Channel | Settings    │  │
│  └───────────────────────┬───────────────────────────┘  │
│                          │                               │
│  ┌── Components ─────────┴───────────────────────────┐  │
│  │  MessageList | MemberList | Sidebar | ChatInput   │  │
│  └───────────────────────┬───────────────────────────┘  │
│                          │                               │
│  ┌── Hooks ──────────────┴───────────────────────────┐  │
│  │  useAuth | useGuild | useMessages | usePresence   │  │
│  └───────────────────────┬───────────────────────────┘  │
│                          │                               │
│  ┌── State (Zustand) ────┴───────────────────────────┐  │
│  │  authStore | guildStore | messageStore            │  │
│  │  userStore | presenceStore | uiStore              │  │
│  └───────────────────────┬───────────────────────────┘  │
│                          │                               │
│  ┌── Transport Layer ────┴───────────────────────────┐  │
│  │  REST Client (fetch)  |  WebSocket Client         │  │
│  │  api.example.com:3000 |  ws.example.com:4000      │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Поток данных

```
Пользователь
    │
    ▼
React Component (UI)
    │
    ├──── Действие (клик, ввод)
    │         │
    │         ▼
    │     Zustand Store  ─────── REST API (мутации)
    │         │                      │
    │         ▼                      ▼
    │     Обновление состояния   Backend (Axum)
    │         │                      │
    │         ▼                      ▼
    │     Re-render              NATS Event
    │                                │
    │                                ▼
    │                          WS Gateway
    │                                │
    └──── WebSocket Event ◄──────────┘
              │
              ▼
          Zustand Store
              │
              ▼
          Re-render
```

### Принципы архитектуры

- **Однонаправленный поток данных** -- действия идут вниз, события вверх
- **Event-driven** -- WebSocket события обновляют стор напрямую, UI реагирует автоматически
- **Оптимистичные обновления** -- UI обновляется мгновенно, откатывается при ошибке
- **Ленивая загрузка** -- роуты и тяжёлые компоненты грузятся по требованию
- **Строгая типизация** -- никаких `any`, все данные типизированы через TypeScript
- **Доступность (a11y)** -- React Aria Components обеспечивают WCAG 2.1 AA

---

## 2. Структура проекта (apps/web/)

```
apps/web/
├── src/
│   ├── app/                        # App shell, routing, providers
│   │   ├── app.tsx                 # Корневой компонент
│   │   ├── router.tsx              # React Router конфигурация
│   │   ├── providers.tsx           # Composition root (все providers)
│   │   └── layouts/
│   │       ├── app-layout.tsx      # Основной layout (sidebar + content)
│   │       ├── auth-layout.tsx     # Layout для auth страниц
│   │       └── guild-layout.tsx    # Layout гильдии (channels + chat)
│   │
│   ├── pages/                      # Страницы (route-level, lazy loaded)
│   │   ├── login.tsx
│   │   ├── register.tsx
│   │   ├── forgot-password.tsx
│   │   ├── dm-list.tsx             # /channels/@me
│   │   ├── dm-chat.tsx             # /channels/@me/:channelId
│   │   ├── guild-channel.tsx       # /channels/:guildId/:channelId
│   │   ├── guild-settings.tsx      # /guilds/:guildId/settings
│   │   ├── settings/
│   │   │   ├── settings-page.tsx
│   │   │   ├── profile-settings.tsx
│   │   │   └── notification-settings.tsx
│   │   └── not-found.tsx
│   │
│   ├── features/                   # Feature-модули по доменным областям
│   │   ├── auth/
│   │   │   ├── components/
│   │   │   │   ├── login-form.tsx
│   │   │   │   └── register-form.tsx
│   │   │   ├── hooks/
│   │   │   │   └── use-auth.ts
│   │   │   └── utils/
│   │   │       └── token-storage.ts
│   │   ├── guild/
│   │   │   ├── components/
│   │   │   │   ├── guild-sidebar.tsx
│   │   │   │   ├── guild-icon.tsx
│   │   │   │   ├── channel-list.tsx
│   │   │   │   ├── member-list.tsx
│   │   │   │   ├── role-badge.tsx
│   │   │   │   └── invite-modal.tsx
│   │   │   └── hooks/
│   │   │       ├── use-guild.ts
│   │   │       └── use-members.ts
│   │   ├── chat/
│   │   │   ├── components/
│   │   │   │   ├── message-list.tsx
│   │   │   │   ├── message-item.tsx
│   │   │   │   ├── message-input.tsx
│   │   │   │   ├── message-reactions.tsx
│   │   │   │   ├── typing-indicator.tsx
│   │   │   │   ├── embed-preview.tsx
│   │   │   │   └── reaction-picker.tsx
│   │   │   └── hooks/
│   │   │       ├── use-messages.ts
│   │   │       ├── use-send-message.ts
│   │   │       └── use-scroll.ts
│   │   └── voice/
│   │       ├── components/
│   │       │   ├── voice-channel.tsx
│   │       │   ├── voice-controls.tsx
│   │       │   └── voice-participant.tsx
│   │       └── hooks/
│   │           └── use-voice.ts
│   │
│   ├── shared/                     # Переиспользуемые компоненты, хуки, утилиты
│   │   ├── components/
│   │   │   ├── ui/                 # Атомарные: Button, Input, Avatar, Badge
│   │   │   │   ├── button.tsx
│   │   │   │   ├── input.tsx
│   │   │   │   ├── avatar.tsx
│   │   │   │   ├── modal.tsx
│   │   │   │   ├── tooltip.tsx
│   │   │   │   ├── toast.tsx
│   │   │   │   └── spinner.tsx
│   │   │   └── patterns/           # Молекулы: UserCard, ChannelHeader
│   │   │       ├── user-card.tsx
│   │   │       ├── channel-header.tsx
│   │   │       └── context-menu.tsx
│   │   ├── hooks/
│   │   │   ├── use-debounce.ts
│   │   │   ├── use-intersection.ts
│   │   │   ├── use-media-query.ts
│   │   │   └── use-click-outside.ts
│   │   ├── utils/
│   │   │   ├── snowflake.ts        # Snowflake ID утилиты (парсинг timestamp)
│   │   │   ├── format-date.ts
│   │   │   ├── format-file-size.ts
│   │   │   ├── permissions.ts      # Битовые маски прав (зеркало crates/permissions)
│   │   │   ├── markdown.ts         # Парсинг markdown в сообщениях
│   │   │   └── constants.ts
│   │   └── types/
│   │       ├── user.ts
│   │       ├── guild.ts
│   │       ├── channel.ts
│   │       ├── message.ts
│   │       ├── role.ts
│   │       ├── permissions.ts
│   │       └── gateway.ts          # WebSocket opcodes, event types
│   │
│   ├── stores/                     # Zustand stores
│   │   ├── auth-store.ts
│   │   ├── guild-store.ts
│   │   ├── channel-store.ts
│   │   ├── message-store.ts
│   │   ├── presence-store.ts
│   │   ├── typing-store.ts
│   │   ├── voice-store.ts
│   │   └── ui-store.ts
│   │
│   ├── api/                        # REST API клиент
│   │   ├── client.ts               # Базовая обёртка над fetch
│   │   ├── endpoints/
│   │   │   ├── auth.ts             # POST /auth/login, /auth/register
│   │   │   ├── guilds.ts           # CRUD серверов
│   │   │   ├── channels.ts         # CRUD каналов
│   │   │   ├── messages.ts         # CRUD сообщений
│   │   │   ├── users.ts            # Профили, друзья
│   │   │   └── media.ts            # Загрузка файлов
│   │   └── types/
│   │       ├── requests.ts
│   │       └── responses.ts
│   │
│   ├── ws/                         # WebSocket клиент
│   │   ├── gateway-client.ts       # Singleton-менеджер WS-соединения
│   │   ├── event-handler.ts        # Маршрутизация событий в stores
│   │   ├── compression.ts          # zstd-декомпрессия (wasm)
│   │   └── types.ts                # Opcodes, payload types
│   │
│   ├── i18n/                       # Lingui переводы
│   │   ├── lingui.config.ts
│   │   └── locales/
│   │       ├── ru/
│   │       │   └── messages.po
│   │       └── en/
│   │           └── messages.po
│   │
│   └── styles/                     # Глобальные стили
│       ├── global.css              # Tailwind directives, CSS variables
│       └── themes/
│           ├── dark.css
│           └── light.css
│
├── public/                         # Статические файлы
│   ├── favicon.ico
│   └── manifest.json
│
├── tests/
│   ├── e2e/                        # Playwright E2E тесты
│   └── setup.ts                    # Vitest setup
│
├── rspack.config.ts                # Конфигурация Rspack
├── tsconfig.json                   # TypeScript конфигурация
├── biome.json                      # Biome (линтер + форматтер)
├── tailwind.config.ts              # Tailwind CSS v4 конфигурация
├── postcss.config.js               # PostCSS (для Tailwind)
├── vitest.config.ts                # Vitest конфигурация
├── package.json
└── pnpm-lock.yaml
```

### Правила организации

- **1 компонент = 1 файл**: `message-list.tsx` содержит только `MessageList`
- **Файлы**: kebab-case (`guild-sidebar.tsx`, `use-auth.ts`)
- **Компоненты**: PascalCase (`GuildSidebar`, `MessageList`)
- **Feature-модули**: группировка по доменной области, не по техническому типу
- **Shared**: только то, что используется 2+ feature-модулями
- **Нет циклических зависимостей**: `pages -> features -> shared -> stores/api/ws`
- **Default export** для page-компонентов (для `lazy()`)
- **Named export** для всех остальных компонентов

### Именование файлов

| Что | Конвенция | Пример |
|-----|----------|--------|
| Компоненты | kebab-case, `.tsx` | `message-list.tsx`, `guild-sidebar.tsx` |
| Stores | kebab-case, `.ts` | `auth-store.ts`, `message-store.ts` |
| Хуки | kebab-case, `use-*.ts` | `use-auth.ts`, `use-messages.ts` |
| Утилиты | kebab-case, `.ts` | `format-date.ts`, `snowflake.ts` |
| Типы | kebab-case, `.ts` | `user.ts`, `guild.ts` |
| Тесты | `*.test.ts` / `*.test.tsx` | `auth-store.test.ts`, `message-list.test.tsx` |

### Именование в коде

| Что | Конвенция | Пример |
|-----|----------|--------|
| Компоненты (React) | PascalCase | `MessageList`, `GuildSidebar` |
| Функции, переменные | camelCase | `handleClick`, `messageCount` |
| Константы | UPPER_SNAKE_CASE | `MAX_MESSAGE_LENGTH`, `WS_RECONNECT_DELAY` |
| Типы, интерфейсы | PascalCase | `User`, `GuildMember`, `MessageState` |
| Enum-значения | PascalCase | `ChannelType.Text`, `Status.Online` |

---

## 3. State Management (Zustand)

### Принципы

- **Store per domain**: отдельный store для каждой доменной области
- **Minimal state**: хранить только источник истины, производные данные вычислять через selectors
- **Нормализация**: entities хранятся по ID в `Map<string, Entity>`, не в массивах
- **No duplicate data**: один entity живёт в одном store
- **WebSocket events обновляют stores напрямую**: без промежуточных слоёв
- **Сторы независимы**: кросс-импортов между сторами нет

### Stores

| Store | Ответственность |
|-------|-----------------|
| `authStore` | Текущий пользователь, токены, состояние аутентификации |
| `guildStore` | Гильдии, участники, роли |
| `channelStore` | Каналы, категории, текущий выбранный канал |
| `messageStore` | Сообщения по каналам, реакции, pending messages |
| `presenceStore` | Онлайн-статусы пользователей |
| `typingStore` | Typing indicators (кто печатает) |
| `uiStore` | Состояние UI: открытые модалки, sidebar, тема, локаль |
| `voiceStore` | Состояние голосового подключения, участники |

### Типы данных

```typescript
// shared/types/user.ts
interface User {
  id: string;
  username: string;
  email: string;
  avatarUrl: string | null;
  status: UserStatus;
  flags: number;
  createdAt: string;
}

enum UserStatus {
  Offline = 0,
  Online = 1,
  Idle = 2,
  DoNotDisturb = 3,
}

// shared/types/guild.ts
interface Guild {
  id: string;
  name: string;
  ownerId: string;
  iconUrl: string | null;
  description: string | null;
  memberCount: number;
}

interface GuildMember {
  userId: string;
  guildId: string;
  nickname: string | null;
  roleIds: string[];
  joinedAt: string;
}

// shared/types/channel.ts
interface Channel {
  id: string;
  guildId: string;
  name: string;
  type: ChannelType;
  position: number;
  topic: string | null;
  parentId: string | null;
  slowmode: number;
}

enum ChannelType {
  Text = 0,
  Voice = 1,
  Category = 2,
  DM = 3,
}

// shared/types/message.ts
interface Message {
  id: string;
  channelId: string;
  authorId: string;
  content: string;
  editedAt: string | null;
  attachments: Attachment[];
  reactions: Reaction[];
  replyTo: string | null;
  createdAt: string;
}

interface Attachment {
  id: string;
  filename: string;
  url: string;
  contentType: string;
  size: number;
}

interface Reaction {
  emoji: string;
  count: number;
  userIds: string[];
}
```

### Схема зависимостей сторов

```
              ┌──────────────┐
              │   authStore   │
              │ (user, token) │
              └──────┬───────┘
                     │ token используется в api/client.ts
                     ▼
   ┌──────────────────────────────────┐
   │         Transport Layer          │
   │  REST Client  |  WS Client      │
   └──────┬────────────────┬──────────┘
          │                │
          ▼                ▼
  ┌─────────────┐  ┌──────────────┐
  │ guildStore  │  │ messageStore │
  └─────────────┘  └──────────────┘
  ┌─────────────┐  ┌──────────────┐
  │ channelStore│  │ presenceStore│
  └─────────────┘  └──────────────┘
  ┌─────────────┐  ┌──────────────┐
  │ typingStore │  │  voiceStore  │
  └─────────────┘  └──────────────┘
  ┌─────────────┐
  │   uiStore   │ (модалки, sidebar, тема)
  └─────────────┘

  Сторы НЕЗАВИСИМЫ. Кросс-импортов нет.
  WS handlers обновляют нужные сторы через
  прямой вызов useXxxStore.getState().method()
```

### Пример: guildStore с нормализованными данными

```typescript
// stores/guild-store.ts
import { create } from "zustand";
import { immer } from "zustand/middleware/immer";
import type { Guild, GuildMember } from "../shared/types/guild";

interface GuildState {
  // Нормализованные данные -- Map по ID, не массив
  guilds: Map<string, Guild>;
  members: Map<string, Map<string, GuildMember>>; // guildId -> userId -> member

  // Derived selectors
  getGuild: (guildId: string) => Guild | undefined;
  getGuildIds: () => string[];
  getMember: (guildId: string, userId: string) => GuildMember | undefined;
  getMemberCount: (guildId: string) => number;

  // Actions
  addGuild: (guild: Guild) => void;
  removeGuild: (guildId: string) => void;
  updateGuild: (guildId: string, partial: Partial<Guild>) => void;
  addMember: (guildId: string, member: GuildMember) => void;
  removeMember: (guildId: string, userId: string) => void;
  setMembers: (guildId: string, members: GuildMember[]) => void;
}

export const useGuildStore = create<GuildState>()(
  immer((set, get) => ({
    guilds: new Map(),
    members: new Map(),

    // Selectors
    getGuild: (guildId) => get().guilds.get(guildId),
    getGuildIds: () => [...get().guilds.keys()],
    getMember: (guildId, userId) => get().members.get(guildId)?.get(userId),
    getMemberCount: (guildId) => get().members.get(guildId)?.size ?? 0,

    // Actions
    addGuild: (guild) =>
      set((state) => {
        state.guilds.set(guild.id, guild);
      }),

    removeGuild: (guildId) =>
      set((state) => {
        state.guilds.delete(guildId);
        state.members.delete(guildId);
      }),

    updateGuild: (guildId, partial) =>
      set((state) => {
        const guild = state.guilds.get(guildId);
        if (guild) {
          Object.assign(guild, partial);
        }
      }),

    addMember: (guildId, member) =>
      set((state) => {
        if (!state.members.has(guildId)) {
          state.members.set(guildId, new Map());
        }
        state.members.get(guildId)?.set(member.userId, member);
      }),

    removeMember: (guildId, userId) =>
      set((state) => {
        state.members.get(guildId)?.delete(userId);
      }),

    setMembers: (guildId, members) =>
      set((state) => {
        const memberMap = new Map<string, GuildMember>();
        for (const member of members) {
          memberMap.set(member.userId, member);
        }
        state.members.set(guildId, memberMap);
      }),
  })),
);
```

### Пример: messageStore с оптимистичными обновлениями

```typescript
// stores/message-store.ts
import { create } from "zustand";
import { immer } from "zustand/middleware/immer";
import type { Message } from "../shared/types/message";

interface PendingMessage extends Message {
  pending: true;
  tempId: string;
  failed?: boolean;
}

interface MessageState {
  // channelId -> Map<messageId, Message>
  messages: Map<string, Map<string, Message | PendingMessage>>;

  // Selectors
  getChannelMessages: (channelId: string) => (Message | PendingMessage)[];
  getMessage: (channelId: string, messageId: string) => Message | undefined;

  // Actions
  addMessage: (channelId: string, message: Message) => void;
  addPendingMessage: (channelId: string, message: PendingMessage) => void;
  confirmPendingMessage: (channelId: string, tempId: string, realMessage: Message) => void;
  markPendingFailed: (channelId: string, tempId: string) => void;
  updateMessage: (channelId: string, messageId: string, partial: Partial<Message>) => void;
  deleteMessage: (channelId: string, messageId: string) => void;
  setMessages: (channelId: string, messages: Message[]) => void;
}

export const useMessageStore = create<MessageState>()(
  immer((set, get) => ({
    messages: new Map(),

    getChannelMessages: (channelId) => {
      const channelMessages = get().messages.get(channelId);
      if (!channelMessages) return [];
      // Сортировка по Snowflake ID (монотонно возрастающий = хронологический порядок)
      return [...channelMessages.values()].sort((a, b) =>
        a.id.localeCompare(b.id),
      );
    },

    getMessage: (channelId, messageId) =>
      get().messages.get(channelId)?.get(messageId),

    addMessage: (channelId, message) =>
      set((state) => {
        if (!state.messages.has(channelId)) {
          state.messages.set(channelId, new Map());
        }
        state.messages.get(channelId)?.set(message.id, message);
      }),

    addPendingMessage: (channelId, message) =>
      set((state) => {
        if (!state.messages.has(channelId)) {
          state.messages.set(channelId, new Map());
        }
        state.messages.get(channelId)?.set(message.tempId, message);
      }),

    confirmPendingMessage: (channelId, tempId, realMessage) =>
      set((state) => {
        const channelMessages = state.messages.get(channelId);
        if (channelMessages) {
          channelMessages.delete(tempId);
          channelMessages.set(realMessage.id, realMessage);
        }
      }),

    markPendingFailed: (channelId, tempId) =>
      set((state) => {
        const msg = state.messages.get(channelId)?.get(tempId);
        if (msg && "pending" in msg) {
          (msg as PendingMessage).failed = true;
        }
      }),

    updateMessage: (channelId, messageId, partial) =>
      set((state) => {
        const msg = state.messages.get(channelId)?.get(messageId);
        if (msg) {
          Object.assign(msg, partial);
        }
      }),

    deleteMessage: (channelId, messageId) =>
      set((state) => {
        state.messages.get(channelId)?.delete(messageId);
      }),

    setMessages: (channelId, messages) =>
      set((state) => {
        const messageMap = new Map<string, Message>();
        for (const msg of messages) {
          messageMap.set(msg.id, msg);
        }
        // Merge, не заменяй (для пагинации)
        const existing = state.messages.get(channelId);
        if (existing) {
          for (const [id, msg] of messageMap) {
            existing.set(id, msg);
          }
        } else {
          state.messages.set(channelId, messageMap);
        }
      }),
  })),
);
```

### Пример: presenceStore

```typescript
// stores/presence-store.ts
import { create } from "zustand";

type UserStatus = "online" | "idle" | "dnd" | "offline";

interface PresenceState {
  statuses: Map<string, UserStatus>;

  setPresence: (userId: string, status: UserStatus) => void;
  getPresence: (userId: string) => UserStatus;
  bulkSetPresences: (presences: Array<{ userId: string; status: UserStatus }>) => void;
}

export const usePresenceStore = create<PresenceState>((set, get) => ({
  statuses: new Map(),

  setPresence: (userId, status) =>
    set((state) => {
      const newStatuses = new Map(state.statuses);
      newStatuses.set(userId, status);
      return { statuses: newStatuses };
    }),

  getPresence: (userId) => get().statuses.get(userId) ?? "offline",

  bulkSetPresences: (presences) =>
    set((state) => {
      const newStatuses = new Map(state.statuses);
      for (const { userId, status } of presences) {
        newStatuses.set(userId, status);
      }
      return { statuses: newStatuses };
    }),
}));
```

### Пример: typingStore

```typescript
// stores/typing-store.ts
import { create } from "zustand";

interface TypingEntry {
  userId: string;
  expiresAt: number;
}

interface TypingState {
  // channelId -> TypingEntry[]
  typing: Map<string, TypingEntry[]>;

  setTyping: (channelId: string, userId: string) => void;
  getTyping: (channelId: string) => string[];
}

const TYPING_TTL = 10_000; // 10 секунд

export const useTypingStore = create<TypingState>((set, get) => ({
  typing: new Map(),

  setTyping: (channelId, userId) =>
    set((state) => {
      const newTyping = new Map(state.typing);
      const channelTyping = (newTyping.get(channelId) ?? []).filter(
        (t) => t.expiresAt > Date.now() && t.userId !== userId,
      );
      channelTyping.push({
        userId,
        expiresAt: Date.now() + TYPING_TTL,
      });
      newTyping.set(channelId, channelTyping);
      return { typing: newTyping };
    }),

  getTyping: (channelId) => {
    const now = Date.now();
    const channelTyping = get().typing.get(channelId) ?? [];
    return channelTyping
      .filter((t) => t.expiresAt > now)
      .map((t) => t.userId);
  },
}));
```

### Оптимизация ре-рендеров через selectors

```typescript
// Подписка только на конкретную гильдию -- компонент не ре-рендерится
// при изменениях в других гильдиях
const guild = useGuildStore((state) => state.getGuild(guildId));

// Shallow comparison для массивов
import { useShallow } from "zustand/react/shallow";

const guildIds = useGuildStore(useShallow((state) => state.getGuildIds()));
```

---

## 4. WebSocket Client

### Opcodes

| Opcode | Имя | Направление | Описание |
|--------|-----|-------------|----------|
| 0 | DISPATCH | Server -> Client | Событие с `s` (sequence) и `t` (event name) |
| 1 | HEARTBEAT | Оба | Keepalive |
| 2 | IDENTIFY | Client -> Server | Аутентификация (JWT) |
| 3 | PRESENCE_UPDATE | Client -> Server | Обновить статус |
| 4 | VOICE_STATE_UPDATE | Client -> Server | Подключение к голосовому каналу |
| 6 | RESUME | Client -> Server | Восстановить прерванную сессию |
| 7 | RECONNECT | Server -> Client | Сервер просит переподключиться |
| 9 | INVALID_SESSION | Server -> Client | Сессия невалидна |
| 10 | HELLO | Server -> Client | heartbeat_interval после подключения |
| 11 | HEARTBEAT_ACK | Server -> Client | Подтверждение heartbeat |

### Жизненный цикл соединения

```
Client                                          Server (Gateway :4000)
  |                                                |
  |-- WebSocket connect (wss://ws.example.com) --->|
  |                                                |
  |<------------- Hello (op:10) -------------------|
  |              {heartbeat_interval: 41250}        |
  |                                                |
  |-- Heartbeat (op:1) после jitter* ------------>|  * jitter = interval * Math.random()
  |<------------- Heartbeat ACK (op:11) -----------|
  |                                                |
  |-- Identify (op:2) {token} ------------------->|  таймаут 5 сек
  |                                                |
  |<------------- Ready (op:0, t:READY) -----------|
  |              {session_id, user, guilds}         |
  |                                                |
  |<------------- GUILD_CREATE (op:0) -------------|  для каждой гильдии
  |                                                |
  |<------------- Dispatch events (op:0) ----------|  s++ каждый dispatch
  |-- Heartbeat (op:1) каждые interval ----------->|
  |<------------- Heartbeat ACK (op:11) -----------|
  |                                                |
  |     ... Обрыв соединения ...                   |
  |                                                |
  |-- WebSocket connect (resume URL) ------------>|  переподключение
  |<------------- Hello (op:10) -------------------|
  |-- Resume (op:6) {session_id, seq} ----------->|  восстановление сессии
  |<------------- Resumed (op:0, t:RESUMED) -------|
  |<------------- Пропущенные события... -----------|
```

### Логика переподключения

```
Экспоненциальный backoff с jitter:

Попытка 1:  1 секунда  +/- 25% jitter
Попытка 2:  2 секунды  +/- 25% jitter
Попытка 3:  4 секунды  +/- 25% jitter
Попытка 4:  8 секунд   +/- 25% jitter
Попытка 5:  16 секунд  +/- 25% jitter
Попытка 6+: 30 секунд  (максимум)

Если есть session_id и sequence --> RESUME (получить пропущенные события)
Если INVALID_SESSION (op:9) с d=false --> новый IDENTIFY
Если код закрытия 4004 (auth failed) --> НЕ переподключаться, logout
```

### Реализация GatewayClient

```typescript
// ws/gateway-client.ts
import type {
  GatewayMessage,
  IdentifyPayload,
  ResumePayload,
} from "./types";
import { GatewayOpcode } from "./types";
import { EventEmitter } from "./event-emitter";

const MAX_RECONNECT_DELAY = 30_000;
const INITIAL_RECONNECT_DELAY = 1_000;

type GatewayStatus =
  | "disconnected"
  | "connecting"
  | "identifying"
  | "connected"
  | "resuming"
  | "reconnecting";

export class GatewayClient {
  private static instance: GatewayClient | null = null;

  private ws: WebSocket | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private heartbeatAcked = true;
  private sequence: number | null = null;
  private sessionId: string | null = null;
  private resumeGatewayUrl: string | null = null;
  private reconnectAttempts = 0;
  private status: GatewayStatus = "disconnected";

  readonly events = new EventEmitter();

  private constructor(
    private readonly gatewayUrl: string,
    private readonly token: string,
  ) {}

  static getInstance(gatewayUrl: string, token: string): GatewayClient {
    if (!GatewayClient.instance) {
      GatewayClient.instance = new GatewayClient(gatewayUrl, token);
    }
    return GatewayClient.instance;
  }

  static destroy(): void {
    GatewayClient.instance?.disconnect();
    GatewayClient.instance = null;
  }

  connect(): void {
    this.status = "connecting";
    const url = `${this.gatewayUrl}?v=1&encoding=json`;
    this.ws = new WebSocket(url);
    this.ws.binaryType = "arraybuffer"; // для zstd-сжатых сообщений

    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
    };

    this.ws.onmessage = (event: MessageEvent) => {
      const message = this.decode(event.data);
      this.handleMessage(message);
    };

    this.ws.onclose = (event: CloseEvent) => {
      this.stopHeartbeat();
      this.handleClose(event.code);
    };

    this.ws.onerror = () => {
      // onclose вызовется следом
    };
  }

  private handleMessage(msg: GatewayMessage): void {
    switch (msg.op) {
      case GatewayOpcode.Hello:
        this.handleHello(msg.d.heartbeatInterval);
        break;

      case GatewayOpcode.HeartbeatAck:
        this.heartbeatAcked = true;
        break;

      case GatewayOpcode.Dispatch:
        if (msg.s !== null && msg.s !== undefined) {
          this.sequence = msg.s;
        }
        this.handleDispatch(msg.t, msg.d);
        break;

      case GatewayOpcode.Heartbeat:
        // Сервер запросил внеочередной heartbeat
        this.sendHeartbeat();
        break;

      case GatewayOpcode.Reconnect:
        this.reconnect(true);
        break;

      case GatewayOpcode.InvalidSession:
        if (msg.d === true) {
          // Resumable -- подождать 1-5 сек и resume
          setTimeout(() => this.resume(), 1000 + Math.random() * 4000);
        } else {
          // Not resumable -- новый identify
          this.sessionId = null;
          this.sequence = null;
          this.reconnect(false);
        }
        break;
    }
  }

  private handleHello(heartbeatInterval: number): void {
    // Первый heartbeat: interval * random(0..1)
    // Jitter предотвращает thundering herd
    const jitter = Math.random() * heartbeatInterval;
    setTimeout(() => {
      this.sendHeartbeat();
      this.startHeartbeat(heartbeatInterval);
    }, jitter);

    if (this.sessionId && this.sequence !== null) {
      this.resume();
    } else {
      this.identify();
    }
  }

  private identify(): void {
    this.status = "identifying";
    const payload: IdentifyPayload = {
      token: this.token,
      properties: {
        os: navigator.platform,
        browser: "web",
        device: "",
      },
    };
    this.send(GatewayOpcode.Identify, payload);
  }

  private resume(): void {
    this.status = "resuming";
    const payload: ResumePayload = {
      token: this.token,
      sessionId: this.sessionId!,
      seq: this.sequence!,
    };
    this.send(GatewayOpcode.Resume, payload);
  }

  private handleDispatch(eventName: string, data: unknown): void {
    if (eventName === "READY") {
      this.status = "connected";
      const readyData = data as {
        sessionId: string;
        resumeGatewayUrl: string;
      };
      this.sessionId = readyData.sessionId;
      this.resumeGatewayUrl = readyData.resumeGatewayUrl;
    }

    if (eventName === "RESUMED") {
      this.status = "connected";
    }

    // Dispatch к store handlers
    this.events.emit(eventName, data);
  }

  // -- Heartbeat --

  private startHeartbeat(interval: number): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (!this.heartbeatAcked) {
        // Zombie connection -- ACK не получен
        this.ws?.close(4000, "Zombie connection");
        return;
      }
      this.heartbeatAcked = false;
      this.sendHeartbeat();
    }, interval);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private sendHeartbeat(): void {
    this.send(GatewayOpcode.Heartbeat, this.sequence);
  }

  // -- Reconnect: exponential backoff + jitter --

  private handleClose(code: number): void {
    const nonResumableCodes = [4004, 4010, 4011, 4012, 4013, 4014];
    const sessionInvalidCodes = [1000, 1001];

    if (nonResumableCodes.includes(code)) {
      // Фатальная ошибка -- не переподключаться
      this.status = "disconnected";
      this.events.emit("FATAL_ERROR", { code });
      return;
    }

    if (sessionInvalidCodes.includes(code)) {
      // Сессия инвалидирована -- новый identify
      this.sessionId = null;
      this.sequence = null;
    }

    this.reconnect(this.sessionId !== null);
  }

  private reconnect(canResume: boolean): void {
    this.status = "reconnecting";
    this.reconnectAttempts++;

    // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
    const delay = Math.min(
      INITIAL_RECONNECT_DELAY * 2 ** (this.reconnectAttempts - 1),
      MAX_RECONNECT_DELAY,
    );

    // Jitter: +/- 25%
    const jitter = delay * 0.25 * (Math.random() * 2 - 1);
    const totalDelay = delay + jitter;

    if (!canResume) {
      this.sessionId = null;
      this.sequence = null;
    }

    setTimeout(() => {
      this.connect();
    }, totalDelay);
  }

  // -- Send --

  private send(op: number, d: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ op, d }));
    }
  }

  disconnect(): void {
    this.stopHeartbeat();
    this.ws?.close(1000, "Client disconnect");
    this.ws = null;
    this.status = "disconnected";
  }

  // Декодирование (JSON или zstd-compressed binary)
  private decode(data: string | ArrayBuffer): GatewayMessage {
    if (typeof data === "string") {
      return JSON.parse(data) as GatewayMessage;
    }
    // zstd-декомпрессия для бинарных данных (см. compression.ts)
    const decompressed = this.decompressZstd(data);
    return JSON.parse(decompressed) as GatewayMessage;
  }

  private decompressZstd(_buffer: ArrayBuffer): string {
    // Реализация через wasm-модуль, см. раздел "zstd-декомпрессия"
    throw new Error("zstd decompression: see compression.ts");
  }

  // Публичные методы для клиентских opcodes
  updatePresence(status: string): void {
    this.send(GatewayOpcode.PresenceUpdate, {
      status,
      activities: [],
      since: null,
      afk: false,
    });
  }

  updateVoiceState(guildId: string, channelId: string | null): void {
    this.send(GatewayOpcode.VoiceStateUpdate, {
      guildId,
      channelId,
      selfMute: false,
      selfDeaf: false,
    });
  }
}
```

### Маршрутизация событий: Gateway -> Stores

```typescript
// ws/event-handler.ts
import type { GatewayClient } from "./gateway-client";
import { useAuthStore } from "../stores/auth-store";
import { useGuildStore } from "../stores/guild-store";
import { useChannelStore } from "../stores/channel-store";
import { useMessageStore } from "../stores/message-store";
import { usePresenceStore } from "../stores/presence-store";
import { useTypingStore } from "../stores/typing-store";

export function registerEventHandlers(client: GatewayClient): void {
  // --- Сессия ---
  client.events.on("READY", (data) => {
    useAuthStore.getState().setUser(data.user);
    for (const guild of data.guilds) {
      useGuildStore.getState().addGuild(guild);
    }
  });

  // --- Гильдии ---
  client.events.on("GUILD_CREATE", (data) => {
    useGuildStore.getState().addGuild(data);
    for (const channel of data.channels) {
      useChannelStore.getState().addChannel(channel);
    }
  });

  client.events.on("GUILD_UPDATE", (data) => {
    useGuildStore.getState().updateGuild(data.id, data);
  });

  client.events.on("GUILD_DELETE", (data) => {
    useGuildStore.getState().removeGuild(data.id);
  });

  client.events.on("GUILD_MEMBER_ADD", (data) => {
    useGuildStore.getState().addMember(data.guildId, data);
  });

  client.events.on("GUILD_MEMBER_REMOVE", (data) => {
    useGuildStore.getState().removeMember(data.guildId, data.userId);
  });

  // --- Каналы ---
  client.events.on("CHANNEL_CREATE", (data) => {
    useChannelStore.getState().addChannel(data);
  });

  client.events.on("CHANNEL_UPDATE", (data) => {
    useChannelStore.getState().updateChannel(data.id, data);
  });

  client.events.on("CHANNEL_DELETE", (data) => {
    useChannelStore.getState().removeChannel(data.id);
  });

  // --- Сообщения ---
  client.events.on("MESSAGE_CREATE", (data) => {
    useMessageStore.getState().addMessage(data.channelId, data);
  });

  client.events.on("MESSAGE_UPDATE", (data) => {
    useMessageStore.getState().updateMessage(data.channelId, data.id, data);
  });

  client.events.on("MESSAGE_DELETE", (data) => {
    useMessageStore.getState().deleteMessage(data.channelId, data.id);
  });

  // --- Presence ---
  client.events.on("PRESENCE_UPDATE", (data) => {
    usePresenceStore.getState().setPresence(data.userId, data.status);
  });

  // --- Typing ---
  client.events.on("TYPING_START", (data) => {
    useTypingStore.getState().setTyping(data.channelId, data.userId);
  });
}
```

### Сводная таблица: WS-событие -> Store

| WS Event | Zustand Store | Метод |
|----------|---------------|-------|
| `READY` | `authStore`, `guildStore` | `setUser()`, `addGuild()` |
| `GUILD_CREATE` | `guildStore`, `channelStore` | `addGuild()`, `addChannel()` |
| `GUILD_UPDATE` | `guildStore` | `updateGuild()` |
| `GUILD_DELETE` | `guildStore` | `removeGuild()` |
| `GUILD_MEMBER_ADD` | `guildStore` | `addMember()` |
| `GUILD_MEMBER_REMOVE` | `guildStore` | `removeMember()` |
| `CHANNEL_CREATE` | `channelStore` | `addChannel()` |
| `CHANNEL_UPDATE` | `channelStore` | `updateChannel()` |
| `CHANNEL_DELETE` | `channelStore` | `removeChannel()` |
| `MESSAGE_CREATE` | `messageStore` | `addMessage()` |
| `MESSAGE_UPDATE` | `messageStore` | `updateMessage()` |
| `MESSAGE_DELETE` | `messageStore` | `deleteMessage()` |
| `PRESENCE_UPDATE` | `presenceStore` | `setPresence()` |
| `TYPING_START` | `typingStore` | `setTyping()` |
| `VOICE_STATE_UPDATE` | `voiceStore` | `updateVoiceState()` |

### zstd-декомпрессия в браузере

WebSocket Gateway сжимает данные через zstd. Для декомпрессии на клиенте
используется wasm-модуль:

```typescript
// ws/compression.ts
import { ZstdInit, type ZstdSimple } from "@aspect-build/aspect-zstd";

let zstd: ZstdSimple | null = null;

export async function initZstdDecoder(): Promise<void> {
  const instance = await ZstdInit();
  zstd = instance.ZstdSimple;
}

export function decompressZstd(buffer: ArrayBuffer): string {
  if (!zstd) {
    throw new Error("zstd not initialized. Call initZstdDecoder() at app startup.");
  }
  const decompressed = zstd.decompress(new Uint8Array(buffer));
  return new TextDecoder().decode(decompressed);
}
```

---

## 5. REST API Client

### Принципы

- Обёртка над нативным `fetch` API (не axios -- меньше bundle size)
- Base URL: `https://api.example.com` (Axum, порт 3000)
- Автоматический `Authorization: Bearer` header
- Token refresh при 401 с дедупликацией (все запросы ждут один refresh)
- Rate limit handling: `429` -> `Retry-After` header
- Типизированные ошибки (`ApiRequestError`)

### Реализация

```typescript
// api/client.ts

interface ApiError {
  code: string;
  message: string;
  details?: Record<string, string[]>;
}

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: Record<string, string[]>,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

class ApiClient {
  private baseUrl: string;
  private accessToken: string | null = null;
  private refreshPromise: Promise<void> | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  setAccessToken(token: string | null): void {
    this.accessToken = token;
  }

  async get<T>(path: string): Promise<T> {
    return this.request<T>("GET", path);
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("POST", path, body);
  }

  async patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PATCH", path, body);
  }

  async put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PUT", path, body);
  }

  async delete<T = void>(path: string): Promise<T> {
    return this.request<T>("DELETE", path);
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
  ): Promise<T> {
    // Если идёт refresh -- ждём его завершения
    if (this.refreshPromise) {
      await this.refreshPromise;
    }

    const headers: Record<string, string> = {};

    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }

    if (this.accessToken) {
      headers["Authorization"] = `Bearer ${this.accessToken}`;
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: "include", // для refresh token cookie
    });

    // 429 -- Rate limit: ждём Retry-After и повторяем
    if (response.status === 429) {
      const retryAfter = Number(response.headers.get("Retry-After") ?? "1");
      await new Promise((resolve) => setTimeout(resolve, retryAfter * 1000));
      return this.request<T>(method, path, body);
    }

    // 401 -- Token refresh
    if (response.status === 401 && this.accessToken) {
      await this.performTokenRefresh();
      // Повторяем исходный запрос с новым токеном
      return this.request<T>(method, path, body);
    }

    if (!response.ok) {
      const error = (await response.json()) as ApiError;
      throw new ApiRequestError(response.status, error.code, error.message, error.details);
    }

    // 204 No Content
    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }

  private async performTokenRefresh(): Promise<void> {
    // Дедупликация: если refresh уже идёт, все запросы ждут его
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    this.refreshPromise = (async () => {
      try {
        const response = await fetch(`${this.baseUrl}/auth/refresh`, {
          method: "POST",
          credentials: "include",
        });

        if (!response.ok) {
          this.accessToken = null;
          window.location.href = "/login";
          return;
        }

        const data = (await response.json()) as { accessToken: string };
        this.accessToken = data.accessToken;
      } finally {
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }
}

export const apiClient = new ApiClient("https://api.example.com");
```

### Пример: endpoints

```typescript
// api/endpoints/messages.ts
import { apiClient } from "../client";
import type { Message } from "../../shared/types/message";

interface SendMessageParams {
  content: string;
  replyTo?: string;
}

interface FetchMessagesParams {
  before?: string;    // cursor: Snowflake ID
  after?: string;
  limit?: number;     // default 50, max 100
}

export const messagesApi = {
  send: (channelId: string, params: SendMessageParams) =>
    apiClient.post<Message>(`/channels/${channelId}/messages`, params),

  fetch: (channelId: string, params?: FetchMessagesParams) =>
    apiClient.get<Message[]>(
      `/channels/${channelId}/messages?${new URLSearchParams(
        Object.entries(params ?? {}).map(([k, v]) => [k, String(v)]),
      )}`,
    ),

  edit: (channelId: string, messageId: string, content: string) =>
    apiClient.patch<Message>(`/channels/${channelId}/messages/${messageId}`, {
      content,
    }),

  delete: (channelId: string, messageId: string) =>
    apiClient.delete(`/channels/${channelId}/messages/${messageId}`),

  addReaction: (channelId: string, messageId: string, emoji: string) =>
    apiClient.put(
      `/channels/${channelId}/messages/${messageId}/reactions/${encodeURIComponent(emoji)}/@me`,
    ),

  removeReaction: (channelId: string, messageId: string, emoji: string) =>
    apiClient.delete(
      `/channels/${channelId}/messages/${messageId}/reactions/${encodeURIComponent(emoji)}/@me`,
    ),
};
```

```typescript
// api/endpoints/auth.ts
import { apiClient } from "../client";
import type { User } from "../../shared/types/user";

interface LoginRequest {
  email: string;
  password: string;
}

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

interface AuthResponse {
  accessToken: string;
  user: User;
}

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<AuthResponse>("/auth/login", data),

  register: (data: RegisterRequest) =>
    apiClient.post<AuthResponse>("/auth/register", data),

  refresh: () =>
    apiClient.post<{ accessToken: string }>("/auth/refresh"),

  logout: () =>
    apiClient.post<void>("/auth/logout"),
};
```

### Диаграмма обработки ошибок

```
Запрос
  │
  ├── 200-299  ──> Вернуть данные (JSON)
  │
  ├── 204      ──> Вернуть undefined (No Content)
  │
  ├── 401      ──> Попробовать refreshToken()
  │                  │
  │                  ├── Успех ──> Повторить запрос с новым токеном
  │                  └── Ошибка ──> Редирект на /login
  │
  ├── 403      ──> ApiRequestError("MISSING_PERMISSIONS")
  │
  ├── 404      ──> ApiRequestError("NOT_FOUND")
  │
  ├── 422      ──> ApiRequestError с details (ошибки по полям)
  │
  ├── 429      ──> Ждать Retry-After секунд, повторить
  │
  └── 500+     ──> ApiRequestError("INTERNAL_ERROR")
```

---

## 6. Компоненты

### Принципы

1. **1 компонент = 1 файл** (kebab-case: `message-item.tsx`)
2. **Файл < 300 строк**: если больше -- разбить на sub-компоненты
3. **Props типизированы**: интерфейс `XxxProps` для каждого компонента
4. **Никаких `any`**: строгая типизация
5. **React Aria** для интерактивных элементов (buttons, dialogs, menus)
6. **Radix UI** для специфических элементов (checkbox, switch, radio, dropdown)
7. **Tailwind CSS v4** -- все стили через утилитарные классы
8. **Framer Motion** -- для анимаций (fade, slide, layout transitions)
9. **Ленивая загрузка** -- `React.lazy()` для page-компонентов
10. **Виртуализация** -- `@tanstack/react-virtual` для длинных списков

### Atomic Design

| Уровень | Расположение | Примеры |
|---------|-------------|---------|
| **Atoms** | `shared/components/ui/` | `Button`, `Input`, `Avatar`, `Badge`, `Tooltip` |
| **Molecules** | `shared/components/patterns/` | `UserCard`, `ChannelHeader`, `ContextMenu` |
| **Organisms** | `features/*/components/` | `MessageList`, `GuildSidebar`, `MemberList` |
| **Pages** | `pages/` | `GuildChannelPage`, `DmChatPage` |

### Иерархия компонентов

```
AppLayout
├── GuildSidebar                     # Левая панель: список серверов
│   ├── GuildIcon (для каждой гильдии)
│   └── CreateGuildButton
│
├── GuildLayout
│   ├── ChannelSidebar               # Список каналов
│   │   ├── ChannelCategory
│   │   │   └── ChannelItem (для каждого канала)
│   │   └── VoiceChannel
│   │       └── VoiceParticipant
│   │
│   ├── ChatArea                     # Центральная часть
│   │   ├── ChannelHeader            # Название канала, топик
│   │   ├── MessageList              # Виртуализированный список
│   │   │   ├── MessageItem
│   │   │   │   ├── Avatar
│   │   │   │   ├── MessageContent
│   │   │   │   ├── MessageReactions
│   │   │   │   └── MessageActions (edit, delete, reply)
│   │   │   └── DateDivider
│   │   ├── TypingIndicator
│   │   └── MessageInput
│   │       ├── TextArea
│   │       ├── FileUploadButton
│   │       └── EmojiPicker
│   │
│   └── MemberList                   # Правая панель
│       ├── MemberCategory (по ролям)
│       └── MemberItem
│           ├── Avatar
│           ├── Username
│           └── StatusIndicator
│
└── SettingsModal
    ├── ProfileSettings
    ├── NotificationSettings
    └── AppearanceSettings
```

### Пример: Button с React Aria и Tailwind

```typescript
// shared/components/ui/button.tsx
import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from "react-aria-components";

interface ButtonProps extends AriaButtonProps {
  variant?: "primary" | "secondary" | "danger" | "ghost";
  size?: "sm" | "md" | "lg";
}

const variantStyles: Record<NonNullable<ButtonProps["variant"]>, string> = {
  primary: "bg-indigo-600 text-white hover:bg-indigo-700 pressed:bg-indigo-800",
  secondary: "bg-zinc-700 text-zinc-100 hover:bg-zinc-600 pressed:bg-zinc-500",
  danger: "bg-red-600 text-white hover:bg-red-700 pressed:bg-red-800",
  ghost: "bg-transparent text-zinc-400 hover:text-zinc-100 hover:bg-zinc-700/50",
};

const sizeStyles: Record<NonNullable<ButtonProps["size"]>, string> = {
  sm: "px-3 py-1.5 text-sm",
  md: "px-4 py-2 text-base",
  lg: "px-6 py-3 text-lg",
};

export function Button({
  variant = "primary",
  size = "md",
  className,
  ...props
}: ButtonProps) {
  return (
    <AriaButton
      className={`
        inline-flex items-center justify-center rounded-md font-medium
        transition-colors focus-visible:outline-2 focus-visible:outline-offset-2
        focus-visible:outline-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed
        ${variantStyles[variant]}
        ${sizeStyles[size]}
        ${className ?? ""}
      `}
      {...props}
    />
  );
}
```

### Пример: Modal с React Aria

```typescript
// shared/components/ui/modal.tsx
import {
  Dialog,
  Modal as AriaModal,
  ModalOverlay,
  Heading,
} from "react-aria-components";

interface ModalProps {
  title: string;
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
}

export function Modal({ title, isOpen, onClose, children }: ModalProps) {
  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={(open) => { if (!open) onClose(); }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
    >
      <AriaModal className="w-full max-w-md rounded-lg bg-zinc-800 p-6 shadow-xl">
        <Dialog className="outline-none">
          <Heading slot="title" className="text-xl font-bold text-zinc-100">
            {title}
          </Heading>
          {children}
        </Dialog>
      </AriaModal>
    </ModalOverlay>
  );
}
```

### Виртуализированный список сообщений

```typescript
// features/chat/components/message-list.tsx
import { useRef, useEffect, useCallback } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { useMessageStore } from "../../../stores/message-store";
import { messagesApi } from "../../../api/endpoints/messages";
import { MessageItem } from "./message-item";

interface MessageListProps {
  channelId: string;
}

export function MessageList({ channelId }: MessageListProps) {
  const messages = useMessageStore((s) => s.getChannelMessages(channelId));
  const setMessages = useMessageStore((s) => s.setMessages);
  const parentRef = useRef<HTMLDivElement>(null);
  const isLoadingRef = useRef(false);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 44,
    overscan: 20,
  });

  // Бесконечный скролл: загрузка старых сообщений при скролле вверх
  const loadOlderMessages = useCallback(async () => {
    if (isLoadingRef.current || messages.length === 0) return;
    isLoadingRef.current = true;

    try {
      const oldestMessageId = messages[0]?.id;
      const olderMessages = await messagesApi.fetch(channelId, {
        before: oldestMessageId,
        limit: 50,
      });

      if (olderMessages.length > 0) {
        setMessages(channelId, olderMessages);
      }
    } finally {
      isLoadingRef.current = false;
    }
  }, [channelId, messages, setMessages]);

  useEffect(() => {
    const el = parentRef.current;
    if (!el) return;

    const handleScroll = () => {
      if (el.scrollTop < 200) {
        loadOlderMessages();
      }
    };

    el.addEventListener("scroll", handleScroll, { passive: true });
    return () => el.removeEventListener("scroll", handleScroll);
  }, [loadOlderMessages]);

  return (
    <div ref={parentRef} className="flex-1 overflow-y-auto">
      <div
        style={{
          height: virtualizer.getTotalSize(),
          width: "100%",
          position: "relative",
        }}
      >
        {virtualizer.getVirtualItems().map((virtualItem) => {
          const message = messages[virtualItem.index];
          const prevMessage = messages[virtualItem.index - 1];
          const isCompact =
            prevMessage !== undefined &&
            prevMessage.authorId === message.authorId &&
            new Date(message.createdAt).getTime() -
              new Date(prevMessage.createdAt).getTime() <
              5 * 60 * 1000; // 5 минут

          return (
            <div
              key={message.id}
              style={{
                position: "absolute",
                top: 0,
                left: 0,
                width: "100%",
                transform: `translateY(${virtualItem.start}px)`,
              }}
              ref={virtualizer.measureElement}
              data-index={virtualItem.index}
            >
              <MessageItem message={message} isCompact={isCompact} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

### Анимации (Framer Motion)

```typescript
// features/chat/components/message-item.tsx (фрагмент)
import { motion } from "framer-motion";
import type { Message } from "../../../shared/types/message";

interface MessageItemProps {
  message: Message;
  isCompact: boolean;
}

export function MessageItem({ message, isCompact }: MessageItemProps) {
  const isPending = "pending" in message;
  const isFailed = "failed" in message && (message as { failed: boolean }).failed;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -20 }}
      transition={{ duration: 0.15 }}
      className={`flex gap-4 px-4 py-1.5 hover:bg-zinc-800/30 group ${
        isPending ? "opacity-50" : ""
      }`}
      data-message-id={message.id}
    >
      {/* Avatar, username, content, reactions */}
      {isFailed && (
        <button
          type="button"
          className="mt-1 text-xs text-red-400 hover:underline"
        >
          Не удалось отправить. Нажмите для повтора.
        </button>
      )}
    </motion.div>
  );
}
```

### Ленивая загрузка страниц

```typescript
// app/router.tsx
import { createBrowserRouter, Navigate, Outlet } from "react-router-dom";
import { lazy, Suspense } from "react";
import { AppLayout } from "./layouts/app-layout";
import { AuthLayout } from "./layouts/auth-layout";
import { ProtectedRoute } from "./protected-route";
import { Spinner } from "../shared/components/ui/spinner";

const LoginPage = lazy(() => import("../pages/login"));
const RegisterPage = lazy(() => import("../pages/register"));
const DmListPage = lazy(() => import("../pages/dm-list"));
const DmChatPage = lazy(() => import("../pages/dm-chat"));
const GuildChannelPage = lazy(() => import("../pages/guild-channel"));
const GuildSettingsPage = lazy(() => import("../pages/guild-settings"));
const NotFoundPage = lazy(() => import("../pages/not-found"));

function SuspenseWrapper() {
  return (
    <Suspense fallback={<Spinner />}>
      <Outlet />
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    element: <SuspenseWrapper />,
    children: [
      // Публичные маршруты
      {
        element: <AuthLayout />,
        children: [
          { path: "/login", element: <LoginPage /> },
          { path: "/register", element: <RegisterPage /> },
        ],
      },
      // Protected маршруты
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { path: "/channels/@me", element: <DmListPage /> },
              { path: "/channels/@me/:channelId", element: <DmChatPage /> },
              { path: "/channels/:guildId/:channelId", element: <GuildChannelPage /> },
              { path: "/guilds/:guildId/settings", element: <GuildSettingsPage /> },
            ],
          },
        ],
      },
      { path: "/", element: <Navigate to="/channels/@me" replace /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
```

---

## 7. Формы и валидация

### Стек: React Hook Form + Zod

- **React Hook Form** -- управление формами (минимальные ре-рендеры)
- **Zod** -- декларативная schema validation (совместима с серверной `validator` crate)
- **@hookform/resolvers** -- связка RHF + Zod

### Валидация: клиент = сервер

Правила валидации на клиенте (Zod schemas) дублируют серверные правила (`validator` crate в Rust):

| Поле | Клиент (Zod) | Сервер (validator) |
|------|-------------|-------------------|
| `username` | `z.string().min(2).max(32).regex(...)` | `#[validate(length(min = 2, max = 32))]` |
| `email` | `z.string().email()` | `#[validate(email)]` |
| `password` | `z.string().min(8).max(128)` | `#[validate(length(min = 8, max = 128))]` |
| `message.content` | `z.string().min(1).max(4000)` | `#[validate(length(min = 1, max = 4000))]` |
| `guild.name` | `z.string().min(2).max(100)` | `#[validate(length(min = 2, max = 100))]` |

### Пример: форма регистрации

```typescript
// features/auth/components/register-form.tsx
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "../../../shared/components/ui/button";
import { Input } from "../../../shared/components/ui/input";
import { Trans } from "@lingui/react/macro";
import { authApi } from "../../../api/endpoints/auth";
import { ApiRequestError } from "../../../api/client";

const registerSchema = z
  .object({
    username: z
      .string()
      .min(2, "Имя пользователя: минимум 2 символа")
      .max(32, "Имя пользователя: максимум 32 символа")
      .regex(/^[a-zA-Z0-9_]+$/, "Только латинские буквы, цифры и _"),
    email: z.string().email("Введите корректный email"),
    password: z
      .string()
      .min(8, "Пароль: минимум 8 символов")
      .max(128, "Пароль: максимум 128 символов"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Пароли не совпадают",
    path: ["confirmPassword"],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

export function RegisterForm() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (data: RegisterFormData) => {
    try {
      await authApi.register({
        username: data.username,
        email: data.email,
        password: data.password,
      });
    } catch (error) {
      if (error instanceof ApiRequestError) {
        // Маппинг серверных кодов ошибок на поля формы
        if (error.code === "USERNAME_TAKEN") {
          setError("username", { message: "Имя уже занято" });
        } else if (error.code === "EMAIL_TAKEN") {
          setError("email", { message: "Email уже используется" });
        } else if (error.details) {
          for (const [field, messages] of Object.entries(error.details)) {
            setError(field as keyof RegisterFormData, {
              message: messages[0],
            });
          }
        }
      }
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <div>
        <label htmlFor="username" className="text-sm font-medium text-zinc-300">
          <Trans>Имя пользователя</Trans>
        </label>
        <Input
          id="username"
          autoComplete="username"
          {...register("username")}
        />
        {errors.username && (
          <p className="mt-1 text-sm text-red-400">{errors.username.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="email" className="text-sm font-medium text-zinc-300">
          <Trans>Email</Trans>
        </label>
        <Input
          id="email"
          type="email"
          autoComplete="email"
          {...register("email")}
        />
        {errors.email && (
          <p className="mt-1 text-sm text-red-400">{errors.email.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="password" className="text-sm font-medium text-zinc-300">
          <Trans>Пароль</Trans>
        </label>
        <Input
          id="password"
          type="password"
          autoComplete="new-password"
          {...register("password")}
        />
        {errors.password && (
          <p className="mt-1 text-sm text-red-400">{errors.password.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="confirmPassword" className="text-sm font-medium text-zinc-300">
          <Trans>Подтвердите пароль</Trans>
        </label>
        <Input
          id="confirmPassword"
          type="password"
          autoComplete="new-password"
          {...register("confirmPassword")}
        />
        {errors.confirmPassword && (
          <p className="mt-1 text-sm text-red-400">{errors.confirmPassword.message}</p>
        )}
      </div>

      <Button type="submit" isDisabled={isSubmitting}>
        {isSubmitting ? <Trans>Регистрация...</Trans> : <Trans>Зарегистрироваться</Trans>}
      </Button>
    </form>
  );
}
```

### Пример: ввод сообщения

```typescript
// features/chat/components/message-input.tsx
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useSendMessage } from "../hooks/use-send-message";

const messageSchema = z.object({
  content: z.string().min(1).max(4000),
});

type MessageFormData = z.infer<typeof messageSchema>;

interface MessageInputProps {
  channelId: string;
}

export function MessageInput({ channelId }: MessageInputProps) {
  const { sendMessage } = useSendMessage(channelId);
  const { register, handleSubmit, reset } = useForm<MessageFormData>({
    resolver: zodResolver(messageSchema),
  });

  const onSubmit = async (data: MessageFormData) => {
    await sendMessage(data.content);
    reset();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter = отправить, Shift+Enter = новая строка
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(onSubmit)();
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="px-4 pb-4">
      <div className="flex items-end gap-2 rounded-lg bg-zinc-700 p-2">
        <textarea
          {...register("content")}
          onKeyDown={handleKeyDown}
          placeholder="Написать сообщение..."
          className="flex-1 resize-none bg-transparent text-zinc-100 outline-none
                     placeholder:text-zinc-500 max-h-48 overflow-y-auto"
          rows={1}
        />
        <Button type="submit" size="sm" variant="primary">
          Отправить
        </Button>
      </div>
    </form>
  );
}
```

### Оптимистичные обновления при отправке сообщения

```typescript
// features/chat/hooks/use-send-message.ts
import { useCallback } from "react";
import { useMessageStore } from "../../../stores/message-store";
import { useAuthStore } from "../../../stores/auth-store";
import { messagesApi } from "../../../api/endpoints/messages";
import type { PendingMessage } from "../../../stores/message-store";

let tempIdCounter = 0;

function generateTempId(): string {
  tempIdCounter++;
  return `temp_${Date.now()}_${tempIdCounter}`;
}

export function useSendMessage(channelId: string) {
  const addPendingMessage = useMessageStore((s) => s.addPendingMessage);
  const confirmPendingMessage = useMessageStore((s) => s.confirmPendingMessage);
  const markPendingFailed = useMessageStore((s) => s.markPendingFailed);

  const sendMessage = useCallback(
    async (content: string, replyTo?: string) => {
      const tempId = generateTempId();
      const user = useAuthStore.getState().user;
      if (!user) return;

      // 1. Добавить pending сообщение (мгновенно видно в UI)
      const pendingMessage: PendingMessage = {
        id: tempId,
        tempId,
        channelId,
        authorId: user.id,
        content,
        editedAt: null,
        attachments: [],
        reactions: [],
        replyTo: replyTo ?? null,
        createdAt: new Date().toISOString(),
        pending: true,
      };
      addPendingMessage(channelId, pendingMessage);

      try {
        // 2. Отправить на сервер
        const realMessage = await messagesApi.send(channelId, {
          content,
          replyTo,
        });

        // 3. Заменить temporary ID на реальный Snowflake ID
        confirmPendingMessage(channelId, tempId, realMessage);
      } catch {
        // 4. Пометить как failed (показать retry button)
        markPendingFailed(channelId, tempId);
      }
    },
    [channelId, addPendingMessage, confirmPendingMessage, markPendingFailed],
  );

  return { sendMessage };
}
```

---

## 8. Интернационализация (i18n)

### Lingui -- обзор

Lingui -- легковесная библиотека для i18n в React. Переводы хранятся в `.po`
файлах (стандарт gettext), компилируются в JS для продакшена. Минимальный
runtime overhead.

Поддерживаемые языки: русский (по умолчанию), английский.

### Конфигурация

```typescript
// i18n/lingui.config.ts
import type { LinguiConfig } from "@lingui/conf";

const config: LinguiConfig = {
  locales: ["ru", "en"],
  sourceLocale: "ru",
  catalogs: [
    {
      path: "src/i18n/locales/{locale}/messages",
      include: ["src"],
    },
  ],
  format: "po",
};

export default config;
```

### Настройка провайдера

```typescript
// app/providers.tsx
import { i18n } from "@lingui/core";
import { I18nProvider } from "@lingui/react";

async function loadCatalog(locale: string): Promise<void> {
  const { messages } = await import(`../i18n/locales/${locale}/messages.ts`);
  i18n.load(locale, messages);
  i18n.activate(locale);
}

// Загрузка при инициализации
const savedLocale = localStorage.getItem("locale");
await loadCatalog(savedLocale ?? "ru");
```

### Использование в компонентах

```typescript
// Макрос Trans для JSX
import { Trans } from "@lingui/react/macro";

function WelcomeMessage({ username }: { username: string }) {
  return (
    <h1>
      <Trans>Добро пожаловать, {username}!</Trans>
    </h1>
  );
}

// Функция t для строк (атрибуты, переменные)
import { useLingui } from "@lingui/react";

function SearchBar() {
  const { t } = useLingui();

  return (
    <input
      placeholder={t`Поиск сообщений...`}
      aria-label={t`Поиск`}
    />
  );
}

// Plurals (ICU format) -- для русского языка необходимы формы: one, few, many
import { Plural } from "@lingui/react/macro";

function MemberCount({ count }: { count: number }) {
  return (
    <span>
      <Plural
        value={count}
        one="# участник"
        few="# участника"
        many="# участников"
        other="# участников"
      />
    </span>
  );
}
```

### Структура файлов переводов

```
src/i18n/
├── lingui.config.ts
└── locales/
    ├── ru/
    │   └── messages.po      # Русский (source locale)
    └── en/
        └── messages.po      # Английский
```

### Пример .po файла

```
# src/i18n/locales/en/messages.po
msgid "Добро пожаловать, {username}!"
msgstr "Welcome, {username}!"

msgid "Войти"
msgstr "Log In"

msgid "Зарегистрироваться"
msgstr "Sign Up"

msgid "Поиск сообщений..."
msgstr "Search messages..."

msgid "Имя пользователя"
msgstr "Username"

msgid "Пароль"
msgstr "Password"
```

### Переключение языка

```typescript
// stores/ui-store.ts (фрагмент)
import { i18n } from "@lingui/core";

interface UiState {
  locale: string;
  setLocale: (locale: string) => Promise<void>;
  // ...
}

// В store:
setLocale: async (locale) => {
  const { messages } = await import(`../i18n/locales/${locale}/messages.ts`);
  i18n.load(locale, messages);
  i18n.activate(locale);
  set({ locale });
  localStorage.setItem("locale", locale);
},
```

### Команды CLI

```bash
# Извлечь строки для перевода из исходников
pnpm lingui extract

# Скомпилировать переводы в JS (для продакшена)
pnpm lingui compile
```

---

## 9. Тестирование

### Стек

| Инструмент | Назначение | Уровень |
|-----------|-----------|---------|
| **Vitest** | Unit-тесты: stores, utils, hooks | Unit |
| **React Testing Library** | Тесты компонентов: рендеринг, взаимодействие | Integration |
| **@testing-library/user-event** | Симуляция пользовательских действий | Integration |
| **MSW (Mock Service Worker)** | Мок API-запросов | Integration |
| **Playwright** | E2E тесты: полный user flow в браузере | E2E |

### Конфигурация Vitest

```typescript
// vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./tests/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      exclude: ["tests/**", "**/*.d.ts"],
    },
  },
});
```

### Setup файл

```typescript
// tests/setup.ts
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
  cleanup();
});
```

### Именование тестов

Тесты располагаются рядом с тестируемым файлом:

```
src/
├── stores/
│   ├── message-store.ts
│   └── message-store.test.ts
├── features/
│   └── chat/
│       └── components/
│           ├── message-item.tsx
│           └── message-item.test.tsx
└── shared/
    └── utils/
        ├── snowflake.ts
        └── snowflake.test.ts
```

### Unit-тесты (Zustand stores)

```typescript
// stores/message-store.test.ts
import { describe, it, expect, beforeEach } from "vitest";
import { useMessageStore } from "./message-store";
import type { Message } from "../shared/types/message";

describe("messageStore", () => {
  beforeEach(() => {
    useMessageStore.setState({ messages: new Map() });
  });

  it("добавляет сообщение в канал", () => {
    const message: Message = {
      id: "123456789",
      channelId: "ch_1",
      authorId: "u_1",
      content: "Привет",
      editedAt: null,
      attachments: [],
      reactions: [],
      replyTo: null,
      createdAt: new Date().toISOString(),
    };

    useMessageStore.getState().addMessage("ch_1", message);

    const messages = useMessageStore.getState().getChannelMessages("ch_1");
    expect(messages).toHaveLength(1);
    expect(messages[0].content).toBe("Привет");
  });

  it("подтверждает pending сообщение реальным ID", () => {
    const pending = {
      id: "temp_1",
      tempId: "temp_1",
      channelId: "ch_1",
      authorId: "u_1",
      content: "Тест",
      editedAt: null,
      attachments: [],
      reactions: [],
      replyTo: null,
      createdAt: new Date().toISOString(),
      pending: true as const,
    };

    useMessageStore.getState().addPendingMessage("ch_1", pending);
    expect(useMessageStore.getState().getChannelMessages("ch_1")).toHaveLength(1);

    const realMessage: Message = {
      ...pending,
      id: "real_snowflake_id",
    };
    useMessageStore.getState().confirmPendingMessage("ch_1", "temp_1", realMessage);

    const messages = useMessageStore.getState().getChannelMessages("ch_1");
    expect(messages).toHaveLength(1);
    expect(messages[0].id).toBe("real_snowflake_id");
  });

  it("удаляет сообщение из канала", () => {
    const message: Message = {
      id: "msg_1",
      channelId: "ch_1",
      authorId: "u_1",
      content: "Удали меня",
      editedAt: null,
      attachments: [],
      reactions: [],
      replyTo: null,
      createdAt: new Date().toISOString(),
    };

    useMessageStore.getState().addMessage("ch_1", message);
    useMessageStore.getState().deleteMessage("ch_1", "msg_1");

    expect(useMessageStore.getState().getChannelMessages("ch_1")).toHaveLength(0);
  });
});
```

### Тесты компонентов (React Testing Library)

```typescript
// features/chat/components/message-item.test.tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MessageItem } from "./message-item";

describe("MessageItem", () => {
  const mockMessage = {
    id: "123",
    channelId: "ch_1",
    authorId: "u_1",
    content: "Тестовое сообщение",
    editedAt: null,
    attachments: [],
    reactions: [],
    replyTo: null,
    createdAt: "2026-01-01T00:00:00Z",
  };

  it("отображает содержимое сообщения", () => {
    render(<MessageItem message={mockMessage} isCompact={false} />);
    expect(screen.getByText("Тестовое сообщение")).toBeInTheDocument();
  });

  it("показывает кнопку retry для failed сообщений", () => {
    const failedMessage = {
      ...mockMessage,
      pending: true as const,
      tempId: "temp_1",
      failed: true,
    };
    render(<MessageItem message={failedMessage} isCompact={false} />);
    expect(screen.getByText(/не удалось отправить/i)).toBeInTheDocument();
  });
});
```

### Тесты с MSW (мок API)

```typescript
// tests/mocks/handlers.ts
import { http, HttpResponse } from "msw";

export const handlers = [
  http.post("https://api.example.com/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };

    if (body.email === "test@test.com" && body.password === "password123") {
      return HttpResponse.json({
        accessToken: "mock-token",
        user: { id: "1", username: "testuser", email: body.email },
      });
    }

    return HttpResponse.json(
      { code: "INVALID_CREDENTIALS", message: "Invalid credentials" },
      { status: 401 },
    );
  }),

  http.get("https://api.example.com/channels/:channelId/messages", () => {
    return HttpResponse.json([
      {
        id: "100",
        channelId: "1",
        authorId: "1",
        content: "Hello, world!",
        editedAt: null,
        attachments: [],
        reactions: [],
        replyTo: null,
        createdAt: "2026-01-01T00:00:00Z",
      },
    ]);
  }),
];
```

### E2E тесты (Playwright)

```typescript
// tests/e2e/chat.spec.ts
import { test, expect } from "@playwright/test";

test.describe("Чат", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await page.fill('[name="email"]', "test@example.com");
    await page.fill('[name="password"]', "password123");
    await page.click('button[type="submit"]');
    await page.waitForURL("/channels/@me");
  });

  test("отправка сообщения", async ({ page }) => {
    await page.click('[data-guild-id="123"]');
    await page.click('[data-channel-id="456"]');

    await page.fill('[data-testid="message-input"]', "Привет, мир!");
    await page.press('[data-testid="message-input"]', "Enter");

    await expect(page.getByText("Привет, мир!")).toBeVisible();
  });
});
```

### Команды

```bash
# Запуск всех тестов
pnpm vitest

# Запуск в watch-режиме
pnpm vitest --watch

# С покрытием кода
pnpm vitest --coverage

# E2E тесты
pnpm playwright test
```

---

## 10. Сборка и оптимизация

### Rspack -- почему не Webpack/Vite

| Характеристика | Rspack | Webpack | Vite |
|---------------|--------|---------|------|
| Язык | Rust | JavaScript | Go (esbuild) + JS |
| Скорость сборки | 10x быстрее Webpack | Эталон | 3-5x быстрее Webpack |
| Совместимость | Webpack-совместимый API | -- | Другой API |
| HMR | ~50ms | ~500ms+ | ~100ms |
| Production бандл | Оптимизированный (SWC) | Terser | esbuild/rollup |

Rspack написан на Rust (как и наш backend), совместим с Webpack-плагинами и лоадерами.

### Конфигурация Rspack

```typescript
// rspack.config.ts
import { defineConfig } from "@rspack/cli";
import { rspack } from "@rspack/core";
import RefreshPlugin from "@rspack/plugin-react-refresh";

const isDev = process.env.NODE_ENV === "development";

export default defineConfig({
  entry: {
    main: "./src/app/main.tsx",
  },
  output: {
    path: "./dist",
    filename: isDev ? "[name].js" : "[name].[contenthash:8].js",
    chunkFilename: isDev ? "[name].js" : "[name].[contenthash:8].js",
    publicPath: "/",
    clean: true,
  },
  resolve: {
    extensions: [".tsx", ".ts", ".js"],
    alias: {
      "@": "./src",
    },
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: {
          loader: "builtin:swc-loader",
          options: {
            jsc: {
              parser: {
                syntax: "typescript",
                tsx: true,
              },
              transform: {
                react: {
                  runtime: "automatic",
                  development: isDev,
                  refresh: isDev,
                },
              },
            },
          },
        },
        type: "javascript/auto",
      },
      {
        test: /\.css$/,
        use: ["postcss-loader"],
        type: "css",
      },
      {
        test: /\.(png|jpg|gif|svg|webp|avif)$/,
        type: "asset",
        parser: {
          dataUrlCondition: {
            maxSize: 8 * 1024, // 8KB -- inline как data URL
          },
        },
      },
      {
        test: /\.(woff|woff2|eot|ttf|otf)$/,
        type: "asset/resource",
      },
    ],
  },
  plugins: [
    new rspack.HtmlRspackPlugin({
      template: "./public/index.html",
    }),
    new rspack.DefinePlugin({
      "process.env.API_URL": JSON.stringify(
        process.env.API_URL ?? "https://api.example.com",
      ),
      "process.env.WS_URL": JSON.stringify(
        process.env.WS_URL ?? "wss://ws.example.com",
      ),
    }),
    isDev && new RefreshPlugin(),
  ].filter(Boolean),
  optimization: {
    splitChunks: {
      chunks: "all",
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: "vendor",
          chunks: "all",
          priority: 10,
        },
        react: {
          test: /[\\/]node_modules[\\/](react|react-dom|react-router)[\\/]/,
          name: "react",
          chunks: "all",
          priority: 20,
        },
      },
    },
    minimize: !isDev,
  },
  devServer: {
    port: 5173,
    hot: true,
    historyApiFallback: true,
    proxy: [
      {
        context: ["/api"],
        target: "http://localhost:3000",
        pathRewrite: { "^/api": "" },
      },
    ],
  },
  devtool: isDev ? "eval-cheap-module-source-map" : "source-map",
});
```

### Стратегия Code Splitting

```
Route-based splitting (React.lazy):

┌──────────────────────────────────────────────────┐
│ main.js                    ~5KB   (router, app)  │
│ react.js                   ~40KB  (react, r-dom) │
│ vendor.js                  ~80KB  (zustand, zod) │
├──────────────────────────────────────────────────┤
│ login.js                   lazy   ~3KB           │
│ register.js                lazy   ~3KB           │
│ guild-channel.js           lazy   ~20KB          │
│ guild-settings.js          lazy   ~10KB          │
│ dm-chat.js                 lazy   ~15KB          │
├──────────────────────────────────────────────────┤
│ emoji-picker.js            lazy   ~30KB          │
│ markdown-renderer.js       lazy   ~15KB          │
│ zstd-wasm.js               lazy   ~100KB         │
└──────────────────────────────────────────────────┘
```

### Performance Budget

| Метрика | Цель | Инструмент |
|---------|------|-----------|
| Initial bundle (gzip) | < 200 KB | Rspack build stats |
| First Contentful Paint | < 1.5 сек | Lighthouse |
| Time to Interactive | < 3 сек | Lighthouse |
| Largest Contentful Paint | < 2.5 сек | Lighthouse |
| Cumulative Layout Shift | < 0.1 | Lighthouse |

### Оптимизации

| Техника | Описание |
|---------|----------|
| Lazy loading страниц | `React.lazy()` + `Suspense` для каждой страницы |
| Виртуализация списков | `@tanstack/react-virtual` для сообщений и участников |
| Zustand selectors | Подписка на минимальный slice, избегаем лишних ре-рендеров |
| React.memo() | Для `MessageItem`, `UserCard`, `ChannelItem` |
| Image lazy loading | `loading="lazy"` для аватаров за пределами viewport |
| Debounce/throttle | Поиск (300ms), typing indicator, скролл |
| Tree shaking | Rspack + ESM -- неиспользуемый код не попадает в бандл |
| Compression | gzip/brotli на уровне Caddy reverse proxy |
| Шрифты | `font-display: swap`, woff2, preload для основного |
| Изображения | WebP/AVIF, аватары через CDN с ресайзом (?size=64) |

---

## 11. Tauri 2 (Desktop)

### Архитектура

```
┌─── Tauri Desktop App (~10MB) ────────────────────┐
│                                                    │
│  ┌── WebView (System) ─────────────────────────┐  │
│  │                                               │  │
│  │  React App (тот же код, что apps/web/)       │  │
│  │  ├── Компоненты                               │  │
│  │  ├── Zustand stores                           │  │
│  │  ├── WS клиент                                │  │
│  │  └── REST API клиент                          │  │
│  │                                               │  │
│  └───────────────┬───────────────────────────────┘  │
│                  │ IPC (invoke / events)             │
│  ┌───────────────▼───────────────────────────────┐  │
│  │  Rust Backend (Tauri Core)                     │  │
│  │  ├── Нативные уведомления (notify)             │  │
│  │  ├── Системный трей (tray icon)                │  │
│  │  ├── Горячие клавиши (global shortcuts)        │  │
│  │  ├── Файловая система (file dialogs)           │  │
│  │  ├── Автозапуск (autostart)                    │  │
│  │  ├── Deep links (protocol handler)             │  │
│  │  └── Автообновление (updater)                  │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### Структура apps/desktop/

```
apps/desktop/
├── src-tauri/
│   ├── src/
│   │   ├── main.rs              # Точка входа Tauri
│   │   ├── commands.rs          # Tauri commands (Rust -> JS)
│   │   ├── tray.rs              # Системный трей
│   │   └── notifications.rs     # Нативные уведомления
│   ├── Cargo.toml
│   ├── tauri.conf.json          # Конфигурация Tauri
│   ├── capabilities/            # Permissions (Tauri 2)
│   └── icons/                   # Иконки приложения
├── src/                          # WebView -- переиспользуем apps/web/src/
├── package.json
└── tsconfig.json
```

### Tauri Commands (IPC: Rust <-> JavaScript)

```rust
// src-tauri/src/commands.rs
use tauri::command;

/// Показать нативное уведомление
#[command]
pub async fn show_notification(title: String, body: String) -> Result<(), String> {
    // Нативное уведомление через tauri-plugin-notification
    Ok(())
}

/// Получить путь к папке загрузок
#[command]
pub async fn get_download_path() -> Result<String, String> {
    let path = dirs::download_dir()
        .ok_or_else(|| "Cannot find download directory".to_string())?;
    path.to_str()
        .map(String::from)
        .ok_or_else(|| "Invalid path".to_string())
}
```

Вызов из JavaScript:

```typescript
// Вызов Tauri commands
import { invoke } from "@tauri-apps/api/core";

async function sendNativeNotification(title: string, body: string): Promise<void> {
  await invoke("show_notification", { title, body });
}

async function getDownloadPath(): Promise<string> {
  return invoke<string>("get_download_path");
}
```

### Определение платформы

```typescript
// shared/utils/platform.ts
export function isTauri(): boolean {
  return "__TAURI_INTERNALS__" in window;
}

export function isWeb(): boolean {
  return !isTauri();
}

// Использование в коде
if (isTauri()) {
  await invoke("show_notification", { title, body });
} else {
  new Notification(title, { body });
}
```

### Сравнение с Electron

| Характеристика | Tauri 2 | Electron |
|---------------|---------|----------|
| Размер приложения | ~10MB | ~150MB |
| RAM (idle) | ~30MB | ~100-200MB |
| Backend | Rust (нативный) | Node.js (V8) |
| WebView | Системный (WebKit/WebView2) | Chromium (встроенный) |
| Startup time | ~200ms | ~1-3s |
| Безопасность | Capability-based permissions | Полный доступ |
| Кросс-компиляция | Встроенная | Через electron-builder |

---

## 12. React Native (Mobile)

### Стратегия переиспользования кода

```
Что ПЕРЕИСПОЛЬЗУЕТСЯ с web:
├── shared/types/    # 100% -- TypeScript типы
├── stores/          # 95%  -- Zustand stores (тот же API)
├── api/             # 95%  -- REST API клиент (fetch работает в RN)
├── ws/              # 90%  -- WS клиент (WebSocket API идентичен)
├── shared/utils/    # 90%  -- Утилиты (snowflake, permissions, format)
└── i18n/            # 100% -- Lingui переводы

Что РАЗНОЕ:
├── components/      # 0%   -- React Native компоненты (View, Text, FlatList)
├── navigation/      # 0%   -- React Navigation (не react-router)
├── styles/          # 0%   -- StyleSheet / NativeWind (не Tailwind в браузере)
└── native/          # 0%   -- Push notifications, file system, camera
```

### Структура apps/mobile/

```
apps/mobile/
├── src/
│   ├── app.tsx                   # Entry point (Expo Router)
│   ├── screens/                  # Экраны (аналог pages/)
│   │   ├── login-screen.tsx
│   │   ├── guild-screen.tsx
│   │   ├── channel-screen.tsx
│   │   └── settings-screen.tsx
│   ├── components/               # RN-компоненты
│   │   ├── message-list.tsx      # FlatList с сообщениями
│   │   ├── message-item.tsx
│   │   └── guild-sidebar.tsx
│   ├── navigation/               # React Navigation
│   │   └── root-navigator.tsx
│   ├── native/                   # Нативные интеграции
│   │   └── push-notifications.ts
│   ├── stores/                   # Переиспользуется из apps/web/
│   ├── api/                      # Переиспользуется из apps/web/
│   ├── ws/                       # Переиспользуется из apps/web/
│   └── shared/                   # Переиспользуется из apps/web/
├── app.json                      # Expo конфигурация
├── package.json
└── tsconfig.json
```

### Навигация (React Navigation)

```typescript
// navigation/root-navigator.tsx
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { LoginScreen } from "../screens/login-screen";
import { GuildScreen } from "../screens/guild-screen";
import { ChannelScreen } from "../screens/channel-screen";
import { useAuthStore } from "../stores/auth-store";

type RootStackParamList = {
  Login: undefined;
  Guild: { guildId: string };
  Channel: { channelId: string; guildId: string };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return (
    <NavigationContainer>
      <Stack.Navigator>
        {isAuthenticated ? (
          <>
            <Stack.Screen name="Guild" component={GuildScreen} />
            <Stack.Screen name="Channel" component={ChannelScreen} />
          </>
        ) : (
          <Stack.Screen name="Login" component={LoginScreen} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
```

### Push-уведомления (Expo)

```typescript
// native/push-notifications.ts
import * as Notifications from "expo-notifications";
import * as Device from "expo-device";
import { apiClient } from "../api/client";

export async function registerForPushNotifications(): Promise<string | null> {
  if (!Device.isDevice) {
    return null; // Не работает на эмуляторе
  }

  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;

  if (existingStatus !== "granted") {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }

  if (finalStatus !== "granted") {
    return null;
  }

  const tokenData = await Notifications.getExpoPushTokenAsync();
  const pushToken = tokenData.data;

  // Отправляем токен на сервер
  await apiClient.post("/users/@me/push-token", { token: pushToken });

  return pushToken;
}
```

### Общий пакет (опционально)

Для максимального переиспользования кода между web и mobile можно вынести
общую логику в отдельный пакет в монорепо:

```
packages/
└── shared/
    ├── stores/       # Zustand stores
    ├── api/          # REST API клиент
    ├── ws/           # WebSocket клиент
    ├── types/        # TypeScript типы
    ├── utils/        # Утилиты
    └── i18n/         # Переводы
```

---

## Источники

- [React 19 Documentation](https://react.dev/)
- [Zustand Documentation](https://zustand.docs.pmnd.rs/)
- [Rspack Documentation](https://rspack.dev/)
- [Tailwind CSS v4 Documentation](https://tailwindcss.com/docs)
- [React Aria Components](https://react-spectrum.adobe.com/react-aria/)
- [Radix UI Documentation](https://www.radix-ui.com/primitives/docs/overview/introduction)
- [Tauri 2 Documentation](https://v2.tauri.app/)
- [Lingui Documentation](https://lingui.dev/)
- [Vitest Documentation](https://vitest.dev/)
- [React Hook Form Documentation](https://react-hook-form.com/)
- [Zod Documentation](https://zod.dev/)
- [Framer Motion Documentation](https://www.framer.com/motion/)
- [React Native / Expo Documentation](https://docs.expo.dev/)
- [Biome Documentation](https://biomejs.dev/)
- [TanStack Virtual Documentation](https://tanstack.com/virtual/latest)
