# Koto — Tauri desktop

Каркас **Tauri 2** + фронт на **Vite + React + TypeScript** (сопоставление с Compose Desktop: **[DESKTOP_PORT.md](DESKTOP_PORT.md)**): разметка в [`src/ui/`](src/ui/), состояние и IPC в [`src/shared/`](src/shared/), доменные сценарии в [`src/features/`](src/features/), точка входа приложения и роутер main pane в [`src/app/`](src/app/), нативное окно в [`src/chrome/`](src/chrome/). Main pane как в desktop `KotoApp` / `NavState.kt` (`mainNav` + стек экранов), тема по `Palette.kt`, окно через **Window API**.

В `tauri.conf.json` у окна **`dragDropEnabled: false`** — иначе обработчик файлов WebView может мешать нативному перетаскиванию окна на Linux/Windows.

**Одно окно:** подключён [`tauri-plugin-single-instance`](https://v2.tauri.app/plugin/single-instance/).

## Запуск

Только **нативное окно Tauri**: `tauri dev` **не** поднимает Vite на `http://localhost:1420`. Перед стартом CLI один раз выполняет `npm run build` (сборка в `dist/`), затем WebView грузит UI из `frontendDist` — как в [доке Tauri](https://v2.tauri.app/reference/config/) (без отдельного dev-сервера бандлера).

```bash
cd tauri-koto
npm install
npm run tauri dev
```

Для отладки фронта **в браузере** (необязательно): `npm run dev` или `npm run preview` — вручную, это не часть обычного запуска приложения.

Linux: [зависимости Tauri](https://v2.tauri.app/start/prerequisites/).

## Карта экранов (desktop → Web)

Маршрутизация правой колонки: [`src/shared/state/navStore.ts`](src/shared/state/navStore.ts) (`Screen`, `NavStack`, синглтон `mainNav`). Синхронизация DOM: [`src/app/mainPaneRouter.ts`](src/app/mainPaneRouter.ts). Модальное окно настроек (`#settings-overlay`) **удалено** — настройки и подэкраны только в main pane.

| Экран (`Screen`) | Исходник Kotlin                                                                                     | Контейнер DOM                                             | TS / модули                                   | CSS                                       |
| ---------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | --------------------------------------------- | ----------------------------------------- |
| `Empty`          | `WelcomePane.kt`                                                                                    | `#pane-empty` / `#welcome-pane`                           | `mainPaneRouter.ts`                           | `welcome.css`, `main-panes.css`           |
| `Chat`           | `ConversationScreen.kt`, `ConversationHeader.kt`, `Composer.kt`, `PinnedBar.kt`, `MessageBubble.kt` | `#pane-chat` / `#thread-view`                             | `chatThread.ts`                               | `thread.css`                              |
| `Settings`       | `SettingsScreen.kt`                                                                                 | `#pane-settings`                                          | `settingsPane.ts`                             | `settings-sheet.css`                      |
| `SettingsSub`    | `SettingsSubScreen.kt` + подэкраны (`ProfileEdit`, `Username`, …)                                   | `#pane-settings-sub`                                      | `settingsPane.ts` (`applySettingsSubSection`) | `main-panes.css` (`.settings-sub-screen`) |
| `NewChat`        | `NewChatScreen.kt`                                                                                  | `#pane-new-chat`                                          | `newChatPane.ts`                              | `new-chat-screen.css`                     |
| `NewGroup`       | `NewGroupScreen.kt`                                                                                 | `#pane-new-group`                                         | `remainingPanes.ts`                           | `main-panes.css` (`.stack-pane`)          |
| `Contact`        | `ContactScreen.kt`                                                                                  | `#pane-contact`                                           | `remainingPanes.ts`, `mainPaneRouter.ts`      | `main-panes.css`                          |
| `Stories`        | `StoriesScreen.kt`                                                                                  | `#pane-stories`                                           | `remainingPanes.ts`, `chatList.ts` (вкладка)  | `main-panes.css`                          |
| `Safety`         | `SafetyScreen.kt`                                                                                   | `#pane-safety`                                            | `remainingPanes.ts`                           | `main-panes.css`                          |
| `SafetyDetail`   | `SafetyDetailScreen.kt`                                                                             | `#pane-safety-detail`                                     | `remainingPanes.ts`                           | `main-panes.css`                          |
| `Bots`           | `BotsScreen.kt`                                                                                     | `#pane-bots`                                              | `remainingPanes.ts`, `chatList.ts`            | `main-panes.css`                          |
| `BotForge`       | `BotForgeScreen.kt`                                                                                 | `#pane-bot-forge`                                         | `remainingPanes.ts`                           | `main-panes.css`                          |
| `Archive`        | `ArchiveScreen.kt`                                                                                  | `#pane-archive`                                           | `remainingPanes.ts`, `chatList.ts`            | `main-panes.css`                          |
| `Call`           | `CallScreen.kt` (fullscreen поверх shell)                                                           | `#overlay-call`                                           | `mainPaneRouter.ts`, `globalShortcuts.ts`     | `overlays.css`                            |
| Auth             | `AuthHost` / register flow                                                                          | `#auth-layer`                                             | `authScreen.ts`, `registerFlow.ts`            | `auth.css`                                |
| Оверлеи          | `AttachSheet.kt`, `EmojiPicker.kt`, `EphemeralSheet.kt`                                             | `#overlay-attach`, `#overlay-emoji`, `#overlay-ephemeral` | `overlaysLayer.ts`, `chatThread.ts`           | `overlays.css`                            |

**Горячие клавиши** (как `KotoApp` `onPreviewKeyEvent`): [`src/app/globalShortcuts.ts`](src/app/globalShortcuts.ts) — Ctrl/⌘+K или +N → новый чат, Ctrl/⌘+, → настройки, Escape → закрыть оверлеи / завершить звонок / `nav.pop()` при глубине > 1 / выйти из нового чата на пустой экран.

## Структура фронта (кратко)

| Путь                     | Назначение                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------ |
| `src/main.tsx`           | Точка входа Vite/React, стили, `boot()`, `initWindowControls()`                      |
| `src/ui/`                | React-разметка shell: `App`, layout-компоненты                                       |
| `src/app/`               | Инициализация и main-pane: `bootstrap.ts`, `mainPaneRouter.ts`, `globalShortcuts.ts` |
| `src/shared/state/`      | `navStore`, `sessionStore`, `themeStore`                                             |
| `src/shared/services/`   | Tauri/gateway: `authService`, `wsService`, `configService`, …                        |
| `src/features/auth/`     | `authScreen`, `registerFlow`                                                         |
| `src/features/chat/`     | `chatList`, `chatThread`, `newChatPane`                                              |
| `src/features/settings/` | `settingsPane`                                                                       |
| `src/features/sidebar/`  | `sidebarConnectivity`                                                                |
| `src/features/shell/`    | `overlaysLayer`, `remainingPanes`, `stubOverlay`, `welcomeScreen`                    |
| `src/chrome/`            | `windowControls` (Tauri window API)                                                  |
| `src/styles/`            | Тема, main pane, overlays (`tokens.css`, …)                                          |

Шрифты **Inter** / **JetBrains Mono** — [fonts.bunny.net](https://fonts.bunny.net). Иконка — `src/assets/koto-mark.png`.

## Rust

`koto-crypto` по `path = "../../crypto"`; **`reqwest`** (rustls) для HTTP к gateway. Команды: `get_app_config`, `gateway_health`, `crypto_registration_smoke`.

### Переменные окружения (как desktop)

| Переменная       | По умолчанию              |
| ---------------- | ------------------------- |
| `KOTO_HOST`      | `127.0.0.1`               |
| `KOTO_TLS`       | `false`                   |
| `KOTO_REST_PORT` | `8081` или `443` при TLS  |
| `KOTO_WS_PORT`   | `9080` или `9443` при TLS |
