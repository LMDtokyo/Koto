# Clean-room заметки: что взять у Session Desktop как идею

> Этот документ — **идеи и паттерны**, которые мы наблюдаем в архитектуре
> Session-Desktop (`forks/session/session-desktop/`) и применяем у себя
> своими руками, своим кодом. **Ни одна строка их кода не копируется.**
>
> Лицензия Session — GPL-3.0. Идеи и общая архитектура НЕ покрываются
> авторским правом, только конкретный код. Поэтому мы:
>
> 1. Описываем паттерн словами в этом файле.
> 2. Закрываем Session-исходник.
> 3. Открываем наш файл и пишем с нуля.
>
> Не подсматривать в код в момент написания своего.

---

## 0. Архитектурный контекст

**Session Desktop** — Electron + React + TypeScript + SASS, без Tailwind.
**Koto Desktop** (наш) — Tauri + React + TypeScript + Tailwind v4.

Session значительно тяжелее (Electron = ~150 МБ runtime), наш Tauri-вариант
~10-20 МБ. Это уже преимущество. Структуру компонентов можно перенести
один-в-один, реализацию — нашими инструментами.

---

## 1. Структура UI (top-level)

Session делит окно на **три зоны**:

```
┌──────┬─────────────────┬───────────────────────────────┐
│      │                 │                               │
│ A    │   B             │   C                           │
│      │                 │                               │
│      │                 │                               │
└──────┴─────────────────┴───────────────────────────────┘
```

- **A — `ActionsPanel`** (узкий ~60–80px rail слева):
  - Брендинг (логотип сверху)
  - Иконки разделов: Сообщения, Контакты, Сообщения с заявками
  - Profile button внизу (avatar)

- **B — `LeftPaneMessageSection`** (~340–380px колонка):
  - `LeftPaneSectionHeader` сверху: title + actions (поиск, новый чат)
  - `LeftPaneSearch` строка поиска (collapsible)
  - `LeftPaneList` — vertical scroll с conversation items
  - `LeftPaneAnnouncements` — сверху над списком (security advisories, etc.)
  - `MessageRequestsBanner` — подсветка непринятых заявок

- **C — `SessionConversation`** (вся остальная ширина):
  - `ConversationHeader` (60–72px высота): avatar peer + name + actions + back
  - `RightPanel` (выезжает справа при click на header) — детали чата, медиа
  - Список сообщений (центральная область)
  - `CompositionBox` снизу

### Что у нас уже есть (✅) и чего не хватает (❌)

| Зона | Session | У нас | Действие |
|------|---------|-------|----------|
| A. ActionsPanel rail | ✅ | ✅ `sidebar-rail` | оставляем, наш TG-вариант лучше |
| A. Profile button внизу | ✅ avatar внизу rail | ✅ `rail-account-btn` | сделано |
| B. SectionHeader | ✅ | ✅ `chatlist-toolbar` | сделано |
| B. Search collapsible | ✅ — поиск разворачивается | ❌ у нас всегда видим | можно опционально свернуть в иконку |
| B. List | ✅ | ✅ | сделано |
| B. Announcements/Banners | ✅ | 🟡 connectivity banner есть | расширить под security advisories |
| B. MessageRequestsBanner | ✅ | ❌ нет | **взять идею** |
| C. ConversationHeader | ✅ | ✅ `thread-header` | сделано |
| C. RightPanel (slide-in) | ✅ | ❌ нет | **взять идею** |
| C. Messages list | ✅ | ✅ | сделано |
| C. CompositionBox | ✅ | ✅ `thread-composer` | сделано |
| C. EmojiPanel popover | ✅ | 🟡 простой picker | расширить |
| C. ReactBar | ✅ | 🟡 у нас есть quick-reactions | сделано |

---

## 2. UX-паттерны для копирования (как идеи!)

### 2.1. RightPanel slide-in для деталей чата

В Session при клике по `ConversationHeader` справа выезжает панель **в той же
области** (без overlay — содержимое чата сжимается слева). Она показывает:

- Большой avatar peer'а
- Display name + status
- Action-кнопки: Search в этом чате, Disappearing messages, Notifications, Block, Delete
- Вкладки внизу: **All Media**, **Documents**

**Наша реализация (план):**
- При клике на `thread-header__peer` → правая панель ~320px въезжает справа.
- Animation: `transform: translateX(100% → 0)`, 240ms cubic-bezier.
- Закрывается ESC, кликом по X, или повторным кликом по header.
- Внутри — наш `right-panel.tsx` с компонентами (`PeerInfoCard`, `ChatActionRow`, `MediaTabs`).

### 2.2. MessageRequests banner

Когда кто-то незнакомый пишет, сообщение **не приходит сразу в основной чат-лист**, а попадает в раздел **Message Requests**. В leftpane сверху висит баннер «У вас N запросов» → click открывает отдельный экран со списком.

**Наша реализация:**
- В overlay `friendsSidebar` уже есть pending-вкладка. Расширить:
  - Над `chat-list` баннер «N новых заявок» (видим только если есть pending).
  - Click → переключает leftpane в pending-mode, скрывая обычный chat-list.
  - Action для каждой заявки: «Принять» / «Отклонить» / «Заблокировать».

### 2.3. CompositionBox с расширенными возможностями

Session-CompositionBox умеет:
- ✅ Текст с `@mentions` (popup snowflake)
- ✅ Drag&drop файлов (`SessionFileDropzone`)
- ✅ Voice recording с waveform (`SessionRecording`)
- ✅ Emoji panel + GIF picker
- ✅ Quote-reply отображается над input'ом (`SessionQuotedMessageComposition`)
- ✅ Disappearing message timer dropdown
- ✅ CharacterCount при близости к лимиту

**Наша реализация (по убыванию приоритета):**

1. **Drag&drop** (P1) — ловим drag-events на всём окне чата + визуальный overlay при dragover «Перетащите файлы сюда».
2. **Quote-reply над composer** (P0) — у нас уже есть `thread-composer-reply`, можно полировать визуал.
3. **@mentions в группах** (P3, после групп) — popup при `@` с suggestions из участников.
4. **Voice recording с waveform** (P2) — Tauri-plugin-fs для записи; Web Audio API для waveform; libsignal для шифрования.
5. **GIF picker** (P3) — Tenor API search, отдельный popup.

### 2.4. Reaction bar при hover

Session показывает **горизонтальный bar с 6 quick-reactions** при hover на bubble (👍❤️😂😮😢🙏 + дополнительный picker). У нас **уже есть** в `chatThread.ts` через `openReactionPicker`. Полировка визуального стиля:

- В Session bar появляется **над bubble**, прижатым к нему. У нас сейчас тоже, но fixed-position.
- Animation: scale + opacity fade-in 120ms.
- Click → toggle reaction, bar закрывается.
- Long-press → расширенный emoji picker (без quick-set).

### 2.5. Typing indicator + read receipts

Session показывает «печатает...» снизу bubble + «прочитано» галочкой (мы это уже делаем — checks в `thread-msg__check`).

**Что не сделано:**
- Typing indicator (нужен WS event `typing` от gateway — на бэке не реализован).
- Last-seen indicator (`SessionLastSeenIndicator.tsx`) — отделяет «прочитанные» от «новых» горизонтальной линией внутри списка сообщений. **Хорошая идея взять.**

### 2.6. Disappearing messages — двойной режим

Session различает:
- **For everyone** — TTL применяется ко всем сообщениям пользователя в этом чате.
- **For me only** — локальная очистка только на нашей стороне.

Это разные семантики, и Session дает пользователю выбор. У нас сейчас один TTL.

### 2.7. ConversationHeaderSelectionOverlay

Когда пользователь долго нажимает (или Cmd+click) на сообщение — Session переключает header в **selection mode**: сверху overlay с количеством выбранных + actions (Delete, Forward, Save). Click по другим сообщениям добавляет их в selection. Esc или X выходит.

**Очень хороший паттерн.** У нас сейчас нет multi-select. Идея:
- При Shift+click или Ctrl+click → добавить в selection.
- В header отображается «N выбрано» + кнопки.

### 2.8. Settings — стандартные секции

Session settings разбита на: Account, Privacy, Notifications, Conversations, Appearance, Help. У нас уже **очень похоже** в `SettingsOverlay`. Наш набор лучше адаптирован под Koto, но проверить:

- ✅ Account / Профиль
- ✅ Privacy
- ❌ **Notifications** — у нас нет отдельной секции. Добавить с настройками per-channel.
- 🟡 Conversations (read receipts, link previews, message-trimming) — частично есть.
- ✅ Appearance (Theme).
- ❌ **Help / About** — добавить с версией приложения, ссылкой на репо, лицензией.

---

## 3. Стилистика и палитра — что НЕ берём

Session используют свою палитру (зелёный + оранжевый акцент). Мы **остаёмся на нашей** Koto-blurple `#5865F2`. Никакого визуального копирования.

Шрифт у Session: Roboto. У нас: Inter. Не меняем.

Иконки у Session: собственный SVG-set. У нас: Lucide React. Не меняем.

---

## 4. Что НЕ берём из Session

### 4.1. Lokinet onion routing

Архитектурно вредно (см. наши предыдущие обсуждения).

### 4.2. Session ID / Loki blockchain

Их identity-формат с префиксом `05` не совместим с нашим. Используем свой
hex-account-id из libsignal.

### 4.3. Service Node API

`SnodeAPI`, `OnionRequestAPI` — это их network-stack для Lokinet. Не нужны.

### 4.4. Storage Server protocol

У них messages хранятся в распределённом storage поверх Lokinet. У нас —
ScyllaDB в `services/chat`. Совершенно другой подход.

### 4.5. их Electron-стек

Мы на Tauri. Никаких `mains/`, `webworker/`, `preload.js`.

### 4.6. их шрифты, иконки, иллюстрации

Все ассеты Session — под GPL-3.0 + их copyright. Мы **не используем** ни
одного их svg-файла, ни одного шрифта.

---

## 5. Конкретный план применения паттернов

### Этап 1 (этой неделе)

1. **MessageRequests баннер** в leftpane — расширить наш `chatlist-connectivity-banner` под inbound friend-requests.
2. **RightPanel slide-in** для деталей чата — новый компонент `<ChatRightPanel />` с пиром, действиями, медиа-вкладками.
3. **LastSeenIndicator** в thread — горизонтальная линия отделяющая прочитанные от новых.

### Этап 2 (через неделю)

4. **Drag & drop** файлов на окно чата + overlay.
5. **Selection mode** для multi-select сообщений (forward, delete batch).
6. **Settings — Notifications** секция.

### Этап 3 (через месяц)

7. **Voice recording** с waveform.
8. **Disappearing messages двойной режим** (for-me / for-everyone).
9. **GIF picker** через Tenor API.

Каждое — **сначала пишем заметку в этот файл**, что хотим сделать. Потом
открываем свой код. **В Session не подсматриваем.**

---

## 6. Юридическая аккуратность

Перед каждым PR в `koto/desktop/` где «взяли паттерн из Session» — добавить
строку в commit message:

> Inspired by Session-Desktop UX pattern (no code copied; clean-room).

Это создаёт audit-trail на случай если кто-то когда-то спросит «не воровали
ли вы код Session?».

Если случайно посмотрел в их код перед написанием своего — **отметить это**,
переписать с нуля через 2 дня (cooling-off period для clean-room standards),
и явно проверить что финальный код не повторяет их структуру строк.
