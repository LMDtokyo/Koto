# Koto Desktop

Desktop-клиент Koto Messenger для Windows / macOS / Linux на **Compose Multiplatform + Kotlin/JVM**. Использует тот же backend (7 Go-микросервисов), те же REST/WebSocket эндпоинты и тот же Signal Protocol (Rust-крейт `crypto/` из корня репо), что и Android-клиент.

## Stack

| Слой | Технология |
|---|---|
| UI | Compose Multiplatform 1.10.3, Material3, Skia |
| Язык | Kotlin 2.1.20 (JVM 17) |
| Сеть | Ktor Client 3.4.3 (CIO engine, Bearer auth, WebSockets) |
| Локальная БД | SQLDelight 2.3.2 (SQLite) |
| DI | Koin 4.2 (`koin-compose`) |
| Сериализация | kotlinx.serialization 1.8.1 |
| Crypto | Rust libsignal через JNI (крейт `crypto/` из корня репо; пока-что заглушка) |
| Сборка | Gradle 8.11.1 (Kotlin DSL, version catalog) |

## Модульная структура (Clean Architecture)

```
desktop/
├── app/          :app      — entry point (Main.kt), Koin DI wiring, окно
├── ui/           :ui       — Compose screens, KotoTheme, atoms
├── data/         :data     — Ktor API, SQLDelight, WebSocket, репозитории
├── domain/       :domain   — pure Kotlin: модели + интерфейсы репозиториев
└── crypto/       :crypto   — Signal Protocol facade (обёртка над Rust libsignal)
```

**Правило зависимостей (Dependency Rule):**
```
:app ─► :ui ─► :domain
  │      │
  ├─► :data ──► :domain
  │      ▲
  └─► :crypto ─► :domain
```

- `:domain` не зависит ни от чего, кроме stdlib + coroutines.
- `:data` реализует интерфейсы из `:domain`, использует Ktor / SQLDelight.
- `:ui` читает модели из `:domain`, рисует Compose — **не знает** про Ktor или SQLite.
- `:crypto` — изолированная обёртка над libsignal; подменяется в DI одной строкой.
- `:app` — единственная точка, где всё склеивается (Koin модуль + `main()`).

## Требования

- **JDK 17+** (обязательно для `jpackage` при создании дистрибутивов)
- **Gradle** — wrapper в комплекте (`./gradlew` / `gradlew.bat`)
- Backend запущен на `127.0.0.1:8080` (REST) + `127.0.0.1:9080` (WebSocket) —  либо `docker compose up` из корня репо.

## Запуск в dev-режиме

```bash
cd desktop
./gradlew :app:run                               # запуск против локального бекенда
./gradlew :app:run -Dkoto.host=koto.run -Dkoto.tls=true   # против production
```

## Сборка дистрибутивов

```bash
./gradlew :app:packageMsi    # Windows — desktop/app/build/compose/binaries/main/msi/
./gradlew :app:packageDmg    # macOS   — desktop/app/build/compose/binaries/main/dmg/
./gradlew :app:packageDeb    # Linux   — desktop/app/build/compose/binaries/main/deb/
```

Пакеты собираются под текущую OS: Msi можно сделать только на Windows, Dmg — только на macOS с подписью Apple. Для кросс-платформы — CI matrix.

## Конфигурация

Всё через system properties или env — никаких config-файлов:

| Ключ | Default | Назначение |
|---|---|---|
| `koto.host` / `KOTO_HOST` | `127.0.0.1` | Хост gateway |
| `koto.tls`  / `KOTO_TLS`  | `false`     | HTTPS/WSS вместо HTTP/WS |

REST-порт = 443/8080, WS-порт = 9443/9080 (выбираются по `koto.tls`).

## Локальное хранилище

SQLite-БД пишется в OS-специфичный app-data каталог:

- Windows: `%APPDATA%\Koto\koto.db`
- macOS:   `~/Library/Application Support/Koto/koto.db`
- Linux:   `$XDG_DATA_HOME/koto/koto.db` (или `~/.local/share/koto/koto.db`)

Схема в [data/src/main/sqldelight/run/koto/desktop/data/local/KotoDb.sq](data/src/main/sqldelight/run/koto/desktop/data/local/KotoDb.sq). SQLDelight Gradle plugin автогенерит typed Kotlin-queries при компиляции.

## Статус crypto

В текущей итерации `:crypto` биндится в DI как `NotLinkedCryptoProvider` — любая попытка шифрования возвращает `Result.failure(IllegalStateException)`. Это **намеренно**: E2EE-контракт требует явный отказ при отсутствии сессии, а не молчаливый Base64-fallback (см. Phase 05.1 security sweep в `.planning/phases/05.1-security-and-bug-fix-sweep/CONTEXT.md`).

Финальная интеграция: кросс-компиляция Rust-крейта `crypto/` под `x86_64-pc-windows-msvc`, `aarch64-apple-darwin`, `x86_64-unknown-linux-gnu` с uniffi-генерацией JVM-биндингов (Android уже так делает). Следующий шаг после MVP UI.

## Дальше по дорожной карте

1. **Port ChatScreen** — портировать `MessageBubble`, `SwipeToReplyContainer`, `ContextMenuOverlay`, `MorphingSendButton`, `TypingWaveIndicator` из `android/` в `:ui`.
2. **Rust crypto для JVM** — cargo-gradle таск, ОС-специфичная загрузка `.dll/.dylib/.so`, swap `NotLinkedCryptoProvider` → `UniffiKotoCryptoProvider`.
3. **Window chrome** — кастомный title bar, системный tray icon, global hotkeys.
4. **Shared module** — выделить `ui/theme/*` + `domain/*` в отдельный multiplatform-модуль, переиспользуемый Android-клиентом.

## Разработка

- Форматирование: следовать конвенциям из корневого `CLAUDE.md` (`alignment style (Kotlin)` — выровненные `=` в data-классах).
- Никогда не использовать raw `spring()` / `tween()` с числовыми литералами в composable — только через `KotoTheme.motion.spring*`.
- Все цвета — через `KotoTheme.colors.*`; `KotoPalette` — `internal`, недоступен вне `ui/theme/`.
- Ни одна строка логики не должна лежать в `Main.kt` кроме создания окна и Koin-модулей.
