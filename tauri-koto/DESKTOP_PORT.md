# Перенос UI с **Compose Desktop** → **tauri-koto**

Этот файл — ориентир для агентов и разработчиков: **откуда смотреть «эталонный» десктоп** и как он соотносится с Tauri-фронтом. Не путать с **`android/`** — это другой клиент.

## Источник правды (Compose Desktop)

| Что                                                  | Путь в репозитории                                                                                    |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Shell (двухпанельный layout, сайдбар 340dp, оверлеи) | [`desktop/ui/.../KotoApp.kt`](../desktop/ui/src/main/kotlin/run/koto/desktop/ui/KotoApp.kt)           |
| Модель экранов main pane + overlay-типы в sealed     | [`desktop/ui/.../nav/NavState.kt`](../desktop/ui/src/main/kotlin/run/koto/desktop/ui/nav/NavState.kt) |
| Список чатов в сайдбаре                              | `desktop/ui/.../screens/chatlist/ChatListScreen.kt`                                                   |
| Тред, настройки, оверлеи и т.д.                      | `desktop/ui/.../screens/**` (см. дерево `desktop/ui/src/main/kotlin/run/koto/desktop/ui/screens/`)    |
| JVM entry / окно                                     | `desktop/app/.../Main.kt`                                                                             |

**Android** (`android/app/...`) — отдельный продукт; для паритета **tauri-koto** сравнивать с **`desktop/`**, если задача явно про «десктоп».

## Как устроен Tauri-фронт (куда класть код)

| Слой                     | Путь            | Роль                                                      |
| ------------------------ | --------------- | --------------------------------------------------------- |
| React-разметка           | `src/ui/`       | DOM + `id` для императивного кода                         |
| Старт + main pane router | `src/app/`      | `bootstrap.ts`, `mainPaneRouter.ts`, `globalShortcuts.ts` |
| Состояние / IPC          | `src/shared/`   | `navStore`, `sessionStore`, `themeStore`, `services/*`    |
| Сценарии по фичам        | `src/features/` | `auth/`, `chat/`, `settings/`, `sidebar/`, `shell/`       |
| Окно Tauri               | `src/chrome/`   | `windowControls.ts`                                       |

Алиас импортов: `@/` → `src/` (см. `vite.config.js`, `tsconfig.json`).

## Навигация: Desktop `Screen` ↔ Tauri `AppScreen`

В **Compose** (`NavState.kt`) в sealed `Screen` входят и main-pane, и overlay-экраны (`Welcome`, `Register`, `Login`, `Lock`).

В **Tauri** overlay **входа** вынесен в React/DOM (`#auth-layer`, `AuthLayer.tsx`); в `AppScreen` / `mainNav` только то, что крутится в **main pane** + `Call` (как на desktop поверх стека).

Соответствие main-pane (по смыслу): `Empty`, `Chat`, `Settings`, `SettingsSub`, `NewChat`, `NewGroup`, `Contact`, `Stories`, `Safety`, `SafetyDetail`, `Bots`, `BotForge`, `Archive`, `Call` — см. [`src/shared/state/navStore.ts`](src/shared/state/navStore.ts).

## Что уже перенесено (каркас / поведение)

- Двухпанельный shell (сайдбар + main column) в React.
- Стек `NavStack` + переходы `PUSH`/`POP`/`NONE`, синхронизация панелей в `mainPaneRouter.ts`.
- Горячие клавиши в духе `KotoApp.onPreviewKeyEvent`: Ctrl+K/N → новый чат, Ctrl+, → настройки, Escape → оверлеи / pop / выход из NewChat (см. `globalShortcuts.ts`).
- Список чатов, открытие чата, новый чат, настройки (каркас), auth + register flow (через Tauri IPC), WS-слушатели, health в сайдбаре.

## Что **ещё не** дотянуто до desktop (`KotoApp` + экраны)

Ниже — расхождения по функционалу/UX, не по пикселям.

1. **Сайдбар как `ChatListScreen`**  
   На desktop: реальный `ChatListState`, выбранный чат, истории без «вечного disabled», камера → attach sheet, профиль/статус/офлайн и т.д.  
   В Tauri: часть кнопок — заглушки или упрощение; **userbar + индикатор WS** в разметке есть, **подписка в TS не дописана**.

2. **`StatusBanner` (офлайн)**  
   В `KotoApp` есть баннер «Нет подключения…» (`offlineBanner`). В Tauri аналога нет.

3. **Оверлеи вложений / эмодзи / TTL**  
   Desktop: `AttachSheet`, `EmojiPicker`, `EphemeralSheet` с реальным выбором.  
   Tauri: `overlaysLayer.ts` + простой DOM; эмодзи — заглушка (один символ в композер).

4. **Настройки и подэкраны**  
   Desktop: для многих секций отдельные экраны + ViewModel (профиль, username, устройства, seed, privacy, safety list/detail, storage, auto-download, network).  
   Tauri: каркас настроек + **`SettingsSub` в основном placeholder** в `settingsPane.ts` (кроме общей логики копирования ID, health, темы).

5. **Данные в шапке настроек**  
   Desktop тянет `ProfileRepository`, счётчики устройств, `StorageRepository`, `NetworkStats`, `AutoDownloadPrefs` в строки настроек.  
   Tauri этого **нет** — только сессия и `getAppConfig` / health где подключено.

6. **Архив**  
   Desktop: `ArchiveScreen` с `ChatListState`.  
   Tauri: упрощённый pane + mock «открыть» через stub overlay.

7. **Боты / BotForge / Safety detail**  
   Desktop: полноценные экраны + ViewModel.  
   Tauri: в основном заглушки / упрощённый safety detail.

8. **Разговор (`ConversationScreen`)**  
   Desktop: полный UI + emoji picker pipeline + ephemeral.  
   Tauri: DOM-тред, тестовая отправка base64, без libsignal в этом слое.

9. **Анимации main pane**  
   Desktop: `AnimatedContent` + `slideInHorizontally` / fade с `KotoEasing`.  
   Tauri: CSS-классы (`main-pane--enter`, `data-nav-transition`).

10. **Экраны `Welcome` / `Register` / `Login` / `Lock` в `Screen`**  
    На desktop они в sealed, но при нормальном флоу рендерятся через **AuthHost** до сессии; в Tauri auth целиком в **`#auth-layer`**, не в стеке `mainNav`.

---

При добавлении фич в **tauri-koto** сначала смотреть **`desktop/ui/...`**, затем решать, что дублируем в TS/React, а что остаётся в Rust (`src-tauri`).

## Сайдбар Tauri (не путать с Android)

- Узкая колонка **`sidebar-rail`** (иконки) + **`sidebar-main`** (чаты, поиск, табы) — см. `src/ui/layout/ChatSidebar.tsx`, стили в `src/styles/sidebar-chatlist.css`.
- Сгенерированный марк бренда: `src/assets/koto-brand-mark.png`. Векторные иконки действий: `src/ui/icons/KotoIcons.tsx`.

## Roadmap «ощущение Telegram» (приоритеты для tauri-koto)

Ориентир по слоям: **P0** быстрый UX-win → **P1** список/тред → **P2** оверлеи и настройки → **P3** локальная БД → **P4** E2EE как на JVM.

| Фаза   | Что                                                                                                   | Статус                                                              |
| ------ | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| **P0** | Баннер офлайн/ошибки WS (аналог `offlineBanner` в `KotoApp.kt`), кнопка «Повторить» → `koto_ws_start` | частично: `chatlist-connectivity-banner` + `sidebarConnectivity.ts` |
| **P0** | Черновик сообщения в `sessionStorage` по `convId` (как у Telegram до отправки)                        | частично: `composerDraft.ts`, тред + превью в списке                |
| **P1** | Превью строки чата: тип сообщения / «черновик» в списке при наличии локального черновика              | запланировано                                                       |
| **P1** | Подгрузка истории треда, якорь к последнему сообщению, reply-цитата (UI)                              | запланировано                                                       |
| **P2** | `AttachSheet` / emoji / TTL — перенос поведения из `desktop/.../overlays` в `overlaysLayer.ts`        | запланировано                                                       |
| **P2** | Настройки: живые строки из API как на `SettingsScreen.kt`                                             | запланировано                                                       |
| **P3** | Локальный кэш диалогов (SQLite / tauri-plugin-sql) — источник правды для списка                       | запланировано                                                       |
| **P4** | libsignal в Tauri (uniffi), без тестового base64                                                      | запланировано                                                       |
