# Микросервисы

## Обзор

Каждый сервис — отдельный Rust binary, отдельный Docker-контейнер, деплоится в Kubernetes независимо.
Общение между сервисами — через NATS JetStream (события), не прямые HTTP вызовы.
Все сервисы на **Rust + Axum + Tokio**.

## Карта сервисов

```
services/
├── api/             → REST API Gateway         → порт 3000
├── gateway/         → WebSocket Gateway         → порт 4000
├── auth/            → Аутентификация            → порт 3001
├── users/           → Пользователи              → порт 3002
├── guilds/          → Серверы и каналы           → порт 3003
├── messages/        → Сообщения                 → порт 3004
├── media/           → Файлы и медиа             → порт 3005
├── notifications/   → Уведомления               → порт 3006
├── search/          → Поиск                     → порт 3007
├── voice/           → Голос и видео             → порт 3008
├── moderation/      → Модерация                 → порт 3009
└── presence/        → Онлайн-статус             → порт 3010
```

## Структура каждого сервиса

```
services/<name>/
├── src/
│   ├── main.rs           # Точка входа, Axum setup
│   ├── config.rs         # Env конфигурация
│   ├── errors.rs         # AppError
│   ├── routes/
│   │   ├── mod.rs        # Router
│   │   └── *.rs          # Endpoint handlers
│   ├── handlers/         # Бизнес-логика
│   ├── models/           # Структуры БД (sqlx::FromRow)
│   ├── middleware/        # Auth, rate limit
│   ├── events/           # NATS event publishers/subscribers
│   └── schemas/          # Входные/выходные структуры (Deserialize/Serialize)
├── tests/
│   ├── common/mod.rs     # Test helpers
│   └── *.rs              # Integration tests
├── migrations/           # SQL миграции (sqlx)
├── Cargo.toml
└── Dockerfile
```

## Общий Cargo workspace

```toml
# Корневой Cargo.toml
[workspace]
resolver = "2"
members = [
    "services/api",
    "services/gateway",
    "services/auth",
    "services/users",
    "services/guilds",
    "services/messages",
    "services/media",
    "services/notifications",
    "services/search",
    "services/voice",
    "services/moderation",
    "services/presence",
    "crates/common",
    "crates/snowflake",
    "crates/permissions",
    "crates/db",
    "crates/cache",
    "crates/nats-events",
    "crates/rate-limit",
]

[workspace.dependencies]
axum = "0.8"
tokio = { version = "1", features = ["full"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
sqlx = { version = "0.8", features = ["postgres", "runtime-tokio", "chrono"] }
redis = { version = "0.27", features = ["tokio-comp"] }
async-nats = "0.38"
jsonwebtoken = "9"
argon2 = "0.5"
tracing = "0.1"
tracing-subscriber = "0.3"
tower = "0.5"
tower-http = { version = "0.6", features = ["cors", "trace", "compression-gzip"] }
thiserror = "2"
validator = { version = "0.19", features = ["derive"] }
chrono = { version = "0.4", features = ["serde"] }
uuid = { version = "1", features = ["v7", "serde"] }
bitflags = "2"
```

## Детали сервисов

### API Gateway (`services/api`)
**Роль**: единая точка входа для REST запросов от клиентов

- Маршрутизирует запросы к внутренним сервисам через NATS request/reply
- JWT аутентификация (middleware)
- Rate limiting (tower middleware)
- CORS (tower-http)
- Request/response логирование (tracing)

**НЕ содержит бизнес-логику** — только проксирование и защита.

### WebSocket Gateway (`services/gateway`)
**Роль**: real-time доставка событий клиентам

- Axum WebSocket (`axum::extract::ws`)
- Tokio tasks для каждого соединения
- Heartbeat / keepalive (30 сек)
- Подписка на NATS топики по гильдиям
- Сжатие (zstd)
- Шардинг: `shard_id = (guild_id >> 22) % num_shards`

**Протокол** (Discord-совместимый):
```
Opcodes:
  0  = DISPATCH            (сервер → клиент, событие)
  1  = HEARTBEAT           (клиент → сервер)
  2  = IDENTIFY            (клиент → сервер, JWT)
  3  = PRESENCE_UPDATE     (клиент → сервер)
  4  = VOICE_STATE_UPDATE  (клиент → сервер)
  6  = RESUME              (клиент → сервер)
  7  = RECONNECT           (сервер → клиент)
  8  = REQUEST_GUILD_MEMBERS (клиент → сервер)
  9  = INVALID_SESSION     (сервер → клиент)
  10 = HELLO               (сервер → клиент)
  11 = HEARTBEAT_ACK       (сервер → клиент)
```

### Auth Service (`services/auth`)
**БД**: PostgreSQL
**Кеш**: Redis (сессии, rate limits)

- Регистрация (email + пароль)
- Логин → access token (JWT, 15 мин) + refresh token (opaque, 30 дней)
- OAuth2: Google, GitHub, Apple (PKCE flow) — `oauth2` crate
- 2FA: TOTP (`totp-rs`), WebAuthn (`webauthn-rs`)
- Пароли: `argon2` crate (Argon2id, m=47104 / 46 MiB, t=1, p=1 — по OWASP)

**NATS события**:
- `auth.user.registered`
- `auth.user.login`
- `auth.user.logout`

### User Service (`services/users`)
**БД**: PostgreSQL

- CRUD профиля (username, avatar, bio)
- Настройки пользователя
- Друзья, запросы, блокировка

**NATS события**: `user.updated`, `user.friend.request`, `user.friend.accepted`

### Guild Service (`services/guilds`)
**БД**: PostgreSQL

- CRUD серверов, каналов, ролей
- Участники (join, leave, kick, ban)
- Права: `bitflags` crate
- Приглашения (invite links, TTL)
- Аудит-лог

**NATS события**: `guild.created`, `guild.member.joined`, `guild.channel.created`, `guild.role.updated`

### Message Service (`services/messages`)
**БД**: PostgreSQL (старт) → ScyllaDB (масштаб, `scylla` crate)

- CRUD сообщений
- Реакции, ответы, треды, пины
- Пагинация: cursor-based (по Snowflake ID)
- Упоминания (@user, @role)

**NATS события**: `message.created`, `message.updated`, `message.deleted`, `message.reaction.added`

### Media Service (`services/media`)
**Хранилище**: MinIO (S3) — `aws-sdk-s3` crate

- Presigned URL для загрузки
- Обработка изображений: `image` crate (resize, thumbnails)
- Strip EXIF: `img-parts` (zero-copy)
- Проверка MIME: `infer` crate (magic bytes)
- Лимиты: 25MB

**NATS события**: `media.uploaded`, `media.deleted`

### Notification Service (`services/notifications`)
**Кеш**: Redis

- Push: `web-push` crate, FCM/APNs через HTTP API
- Email: `lettre` crate (SMTP)
- In-app badge counts
- Агрегация

**Слушает NATS**: `message.created`, `user.friend.request`, `guild.member.joined`

### Search Service (`services/search`)
**Поиск**: Meilisearch — `meilisearch-sdk` crate

- Индексация сообщений асинхронно
- Поиск по сообщениям, серверам, пользователям
- Фильтры

**Слушает NATS**: `message.created`, `message.updated`, `message.deleted`

### Voice Service (`services/voice`)
**SFU**: LiveKit — `livekit-api` crate

- Создание/удаление комнат
- Токены для подключения
- Управление: mute, deafen, kick

### Presence Service (`services/presence`)
**Хранилище**: Redis

- Онлайн/оффлайн/idle/dnd
- Typing indicators (TTL 10 сек)
- Heartbeat от Gateway

**NATS события**: `presence.updated`

### Moderation Service (`services/moderation`)
**БД**: PostgreSQL

- Автомодерация (regex фильтры)
- Репорты, anti-raid, slowmode
- Аудит-лог

## Общие crates (packages/)

| Crate | Назначение |
|-------|-----------|
| `crates/common` | Общие типы, AppState, конфигурация |
| `crates/snowflake` | Генератор Snowflake ID |
| `crates/permissions` | Битовые маски прав (bitflags) |
| `crates/db` | SQLx pool, миграции, общие queries |
| `crates/cache` | Redis обёртка, typed cache |
| `crates/nats-events` | Типизированные NATS события, publisher/subscriber |
| `crates/rate-limit` | Tower middleware для rate limiting |
