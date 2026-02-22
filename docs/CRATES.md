# Общие Crates

Внутренние Rust библиотеки, переиспользуемые между микросервисами. Каждый crate — отдельная директория в `crates/`.

## Источники

- [Rust Cargo Workspaces](https://doc.rust-lang.org/cargo/reference/workspaces.html)
- [crate: sqlx](https://docs.rs/sqlx/latest/sqlx/)
- [crate: redis](https://docs.rs/redis/latest/redis/)
- [crate: deadpool-redis](https://docs.rs/deadpool-redis/latest/deadpool_redis/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)
- [crate: bitflags](https://docs.rs/bitflags/latest/bitflags/)
- [crate: tower](https://docs.rs/tower/latest/tower/)
- [crate: jsonwebtoken](https://docs.rs/jsonwebtoken/latest/jsonwebtoken/)
- [crate: tracing](https://docs.rs/tracing/latest/tracing/)
- [Twitter Snowflake — ID generation](https://blog.twitter.com/engineering/en_us/a/2010/announcing-snowflake)

---

## Карта crates

```
crates/
├── common/         → Общие типы, AppState, config helpers, error types
├── snowflake/      → Генератор Snowflake ID
├── permissions/    → Битовые маски прав (bitflags), вычисление permissions
├── db/             → SQLx pool, миграции, общие query helpers
├── cache/          → Redis обёртка, typed cache, сериализация
├── nats-events/    → Типизированные NATS события, publisher/subscriber
└── rate-limit/     → Tower middleware для rate limiting (Redis sliding window)
```

---

## crates/common

Общие типы и утилиты, используемые всеми сервисами.

```
crates/common/
├── src/
│   ├── lib.rs
│   ├── config.rs       # Загрузка конфигурации из env
│   ├── errors.rs       # AppError — единый тип ошибок
│   ├── state.rs        # AppState (shared state между handlers)
│   ├── extractors.rs   # Axum extractors (CurrentUser, Pagination)
│   ├── response.rs     # Стандартные response helpers
│   └── validation.rs   # Общие валидаторы
└── Cargo.toml
```

### Cargo.toml

```toml
[package]
name = "common"
version = "0.1.0"
edition = "2024"

[dependencies]
axum = { workspace = true }
serde = { workspace = true }
serde_json = { workspace = true }
thiserror = { workspace = true }
tracing = { workspace = true }
chrono = { workspace = true }
validator = { workspace = true }
config = "0.15"
dotenvy = "0.15"
```

### AppError

```rust
use axum::response::{IntoResponse, Response};
use axum::http::StatusCode;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("{message}")]
    BadRequest { code: String, message: String },

    #[error("Unauthorized")]
    Unauthorized,

    #[error("Forbidden: {code}")]
    Forbidden { code: String, message: String },

    #[error("Not found: {code}")]
    NotFound { code: String, message: String },

    #[error("Rate limited")]
    RateLimited { retry_after: f64 },

    #[error("Internal error")]
    Internal(#[from] anyhow::Error),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, code, message) = match &self {
            AppError::BadRequest { code, message } => (StatusCode::BAD_REQUEST, code.clone(), message.clone()),
            AppError::Unauthorized => (StatusCode::UNAUTHORIZED, "UNAUTHORIZED".into(), "Unauthorized".into()),
            AppError::Forbidden { code, message } => (StatusCode::FORBIDDEN, code.clone(), message.clone()),
            AppError::NotFound { code, message } => (StatusCode::NOT_FOUND, code.clone(), message.clone()),
            AppError::RateLimited { retry_after } => {
                // Добавить Retry-After header
                return (
                    StatusCode::TOO_MANY_REQUESTS,
                    [("Retry-After", retry_after.to_string())],
                    serde_json::json!({"code": "RATE_LIMITED", "message": "Rate limited", "retry_after": retry_after}),
                ).into_response();
            }
            AppError::Internal(_) => {
                tracing::error!(error = %self, "Internal error");
                (StatusCode::INTERNAL_SERVER_ERROR, "INTERNAL_ERROR".into(), "Internal error".into())
            }
        };

        (status, axum::Json(serde_json::json!({"code": code, "message": message}))).into_response()
    }
}
```

### CurrentUser Extractor

```rust
use axum::{extract::FromRequestParts, http::request::Parts};

#[derive(Debug, Clone)]
pub struct CurrentUser {
    pub user_id: i64,
}

impl<S> FromRequestParts<S> for CurrentUser
where
    S: Send + Sync,
{
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        parts.extensions
            .get::<CurrentUser>()
            .cloned()
            .ok_or(AppError::Unauthorized)
    }
}
```

---

## crates/snowflake

Генератор Snowflake ID — 64-bit уникальные, хронологически сортируемые идентификаторы.

```
crates/snowflake/
├── src/
│   └── lib.rs
└── Cargo.toml
```

### Структура Snowflake ID (64 бита)

```
| Timestamp (42 bits)  | Worker ID (5 bits) | Process ID (5 bits) | Sequence (12 bits) |
|   63 ............. 22 | 21 ............ 17 | 16 ............ 12  | 11 ............ 0  |
```

- **Timestamp**: миллисекунды с custom epoch
- **Worker ID**: ID инстанса (0–31)
- **Process ID**: ID процесса (0–31)
- **Sequence**: автоинкремент per-millisecond (0–4095)

### Custom Epoch

```rust
/// Custom epoch: January 1, 2025 00:00:00 UTC
pub const CUSTOM_EPOCH: i64 = 1735689600000;
```

### API

```rust
use std::sync::atomic::{AtomicU16, Ordering};
use std::sync::Mutex;

pub struct SnowflakeGenerator {
    worker_id: u8,       // 0-31
    process_id: u8,      // 0-31
    sequence: AtomicU16,  // 0-4095
    last_timestamp: Mutex<i64>,
}

impl SnowflakeGenerator {
    pub fn new(worker_id: u8, process_id: u8) -> Self {
        assert!(worker_id < 32, "worker_id must be 0-31");
        assert!(process_id < 32, "process_id must be 0-31");
        Self {
            worker_id,
            process_id,
            sequence: AtomicU16::new(0),
            last_timestamp: Mutex::new(0),
        }
    }

    pub fn generate(&self) -> Result<i64, SnowflakeError> {
        let mut last_ts = self.last_timestamp.lock()
            .map_err(|_| SnowflakeError::LockPoisoned)?;
        let mut timestamp = Self::current_timestamp();

        if timestamp == *last_ts {
            let seq = self.sequence.fetch_add(1, Ordering::SeqCst);
            if seq >= 4096 {
                // Подождать следующую миллисекунду
                while timestamp <= *last_ts {
                    timestamp = Self::current_timestamp();
                }
                self.sequence.store(0, Ordering::SeqCst);
            }
        } else {
            self.sequence.store(0, Ordering::SeqCst);
        }

        *last_ts = timestamp;

        Ok((timestamp << 22)
            | ((self.worker_id as i64) << 17)
            | ((self.process_id as i64) << 12)
            | (self.sequence.load(Ordering::SeqCst) as i64))
    }

    fn current_timestamp() -> i64 {
        chrono::Utc::now().timestamp_millis() - CUSTOM_EPOCH
    }
}

/// Извлечь timestamp из Snowflake ID
pub fn snowflake_timestamp(id: i64) -> i64 {
    (id >> 22) + CUSTOM_EPOCH
}
```

### Потокобезопасность

`SnowflakeGenerator` — thread-safe. Используется как `Arc<SnowflakeGenerator>` в AppState. Один генератор на один pod (worker_id из env).

---

## crates/permissions

Система прав на основе битовых масок. Вычисление effective permissions с учётом ролей и channel overwrites.

```
crates/permissions/
├── src/
│   ├── lib.rs
│   ├── flags.rs         # Все permission flags (bitflags)
│   └── calculator.rs    # Вычисление effective permissions
└── Cargo.toml
```

### Permission Flags

```rust
use bitflags::bitflags;

bitflags! {
    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    pub struct Permissions: u64 {
        const CREATE_INSTANT_INVITE  = 1 << 0;
        const KICK_MEMBERS           = 1 << 1;
        const BAN_MEMBERS            = 1 << 2;
        const ADMINISTRATOR          = 1 << 3;
        const MANAGE_CHANNELS        = 1 << 4;
        const MANAGE_GUILD           = 1 << 5;
        const ADD_REACTIONS          = 1 << 6;
        const VIEW_AUDIT_LOG         = 1 << 7;
        const PRIORITY_SPEAKER       = 1 << 8;
        const STREAM                 = 1 << 9;
        const VIEW_CHANNEL           = 1 << 10;
        const SEND_MESSAGES          = 1 << 11;
        const SEND_TTS_MESSAGES      = 1 << 12;
        const MANAGE_MESSAGES        = 1 << 13;
        const EMBED_LINKS            = 1 << 14;
        const ATTACH_FILES           = 1 << 15;
        const READ_MESSAGE_HISTORY   = 1 << 16;
        const MENTION_EVERYONE       = 1 << 17;
        const USE_EXTERNAL_EMOJIS    = 1 << 18;
        const CONNECT                = 1 << 20;
        const SPEAK                  = 1 << 21;
        const MUTE_MEMBERS           = 1 << 22;
        const DEAFEN_MEMBERS         = 1 << 23;
        const MOVE_MEMBERS           = 1 << 24;
        const CHANGE_NICKNAME        = 1 << 26;
        const MANAGE_NICKNAMES       = 1 << 27;
        const MANAGE_ROLES           = 1 << 28;
        const MANAGE_WEBHOOKS        = 1 << 29;
        const MANAGE_GUILD_EXPRESSIONS = 1 << 30;
        const MANAGE_THREADS         = 1 << 34;
        const CREATE_PUBLIC_THREADS  = 1 << 35;
        const CREATE_PRIVATE_THREADS = 1 << 36;
        const SEND_MESSAGES_IN_THREADS = 1 << 38;
        const MODERATE_MEMBERS       = 1 << 40;
        const PIN_MESSAGES           = 1 << 51;
    }
}
```

### Permission Calculator

```rust
pub struct PermissionOverwrite {
    pub id: i64,           // role_id или user_id
    pub overwrite_type: u8, // 0 = role, 1 = user
    pub allow: u64,
    pub deny: u64,
}

pub fn compute_base_permissions(
    member_roles: &[i64],
    guild_roles: &[(i64, u64)],  // (role_id, permissions)
    guild_owner_id: i64,
    user_id: i64,
) -> Permissions {
    // Guild owner = all permissions
    if user_id == guild_owner_id {
        return Permissions::all();
    }

    let mut permissions = Permissions::empty();

    // OR all role permissions
    for (role_id, role_perms) in guild_roles {
        if member_roles.contains(role_id) {
            permissions |= Permissions::from_bits_truncate(*role_perms);
        }
    }

    // Administrator = all permissions
    if permissions.contains(Permissions::ADMINISTRATOR) {
        return Permissions::all();
    }

    permissions
}

pub fn compute_channel_permissions(
    base: Permissions,
    overwrites: &[PermissionOverwrite],
    member_roles: &[i64],
    user_id: i64,
    everyone_role_id: i64,
) -> Permissions {
    if base.contains(Permissions::ADMINISTRATOR) {
        return Permissions::all();
    }

    let mut permissions = base;

    // 1. @everyone role overwrites
    if let Some(ow) = overwrites.iter().find(|o| o.id == everyone_role_id && o.overwrite_type == 0) {
        permissions &= !Permissions::from_bits_truncate(ow.deny);
        permissions |= Permissions::from_bits_truncate(ow.allow);
    }

    // 2. Role-specific overwrites (OR all allows, OR all denies)
    let mut allow = Permissions::empty();
    let mut deny = Permissions::empty();
    for ow in overwrites.iter().filter(|o| o.overwrite_type == 0 && member_roles.contains(&o.id)) {
        allow |= Permissions::from_bits_truncate(ow.allow);
        deny |= Permissions::from_bits_truncate(ow.deny);
    }
    permissions &= !deny;
    permissions |= allow;

    // 3. User-specific overwrites
    if let Some(ow) = overwrites.iter().find(|o| o.id == user_id && o.overwrite_type == 1) {
        permissions &= !Permissions::from_bits_truncate(ow.deny);
        permissions |= Permissions::from_bits_truncate(ow.allow);
    }

    permissions
}
```

---

## crates/db

SQLx connection pool, миграции, общие database utilities.

```
crates/db/
├── src/
│   ├── lib.rs
│   ├── pool.rs          # PgPool creation
│   └── pagination.rs    # Cursor-based pagination helpers
└── Cargo.toml
```

### Pool creation

```rust
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

pub async fn create_pool(database_url: &str, max_connections: u32) -> Result<PgPool, sqlx::Error> {
    PgPoolOptions::new()
        .max_connections(max_connections)
        .acquire_timeout(Duration::from_secs(5))
        .idle_timeout(Duration::from_secs(600))
        .test_before_acquire(true)
        .connect(database_url)
        .await
}
```

### Pagination helpers

```rust
pub struct CursorParams {
    pub before: Option<i64>,
    pub after: Option<i64>,
    pub around: Option<i64>,
    pub limit: i64,  // default 50, max 100
}

impl CursorParams {
    pub fn validate(&self) -> Result<(), AppError> {
        if self.limit < 1 || self.limit > 100 {
            return Err(AppError::bad_request("INVALID_LIMIT", "Limit must be 1-100"));
        }
        let cursor_count = [self.before, self.after, self.around].iter().filter(|c| c.is_some()).count();
        if cursor_count > 1 {
            return Err(AppError::bad_request("INVALID_CURSOR", "Only one of before/after/around allowed"));
        }
        Ok(())
    }
}
```

### Стандартный response для списков

Все endpoints, возвращающие списки, используют единый формат. Массив объектов в body + cursor-related headers:

**Response body:** массив объектов (не обёрнутый)

```json
[
    { "id": "111222333", ... },
    { "id": "444555666", ... }
]
```

**Query params:**

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `limit` | integer | 50 | 1-100, количество элементов |
| `before` | snowflake | - | Элементы до этого ID |
| `after` | snowflake | - | Элементы после этого ID |
| `around` | snowflake | - | Элементы вокруг этого ID |

Только один из `before`/`after`/`around` допускается. Snowflake ID используются как естественные курсоры (хронологически сортируемые).

**Определение "есть ли ещё":** клиент запрашивает `limit + 1`, если вернулось `limit + 1` элементов — значит есть ещё. Лишний элемент отбрасывается клиентом.

---

## crates/cache

Redis обёртка с типизированным доступом, сериализацией и TTL.

```
crates/cache/
├── src/
│   ├── lib.rs
│   ├── client.rs        # Redis client wrapper
│   └── typed.rs         # TypedCache<T> — generic cache
└── Cargo.toml
```

### TypedCache

```rust
use deadpool_redis::Pool;
use serde::{Serialize, de::DeserializeOwned};

pub struct TypedCache {
    pool: Pool,
}

impl TypedCache {
    pub async fn get<T: DeserializeOwned>(&self, key: &str) -> Result<Option<T>, CacheError> {
        let mut conn = self.pool.get().await?;
        let value: Option<String> = redis::cmd("GET").arg(key).query_async(&mut conn).await?;
        match value {
            Some(v) => Ok(Some(serde_json::from_str(&v)?)),
            None => Ok(None),
        }
    }

    pub async fn set<T: Serialize>(&self, key: &str, value: &T, ttl_secs: u64) -> Result<(), CacheError> {
        let mut conn = self.pool.get().await?;
        let json = serde_json::to_string(value)?;
        redis::cmd("SET").arg(key).arg(json).arg("EX").arg(ttl_secs)
            .query_async(&mut conn).await?;
        Ok(())
    }

    pub async fn del(&self, key: &str) -> Result<(), CacheError> {
        let mut conn = self.pool.get().await?;
        redis::cmd("DEL").arg(key).query_async(&mut conn).await?;
        Ok(())
    }

    pub async fn exists(&self, key: &str) -> Result<bool, CacheError> {
        let mut conn = self.pool.get().await?;
        let exists: bool = redis::cmd("EXISTS").arg(key).query_async(&mut conn).await?;
        Ok(exists)
    }
}
```

---

## crates/nats-events

Типизированные NATS события, publisher и subscriber.

```
crates/nats-events/
├── src/
│   ├── lib.rs
│   ├── events.rs        # Enum всех событий
│   ├── publisher.rs     # Type-safe publish
│   ├── subscriber.rs    # Type-safe subscribe
│   └── subjects.rs      # Subject constants
└── Cargo.toml
```

### NATS Subject Naming Convention

Все NATS subjects следуют единому формату:

```
{domain}.{scope}.{action}
```

| Тип | Формат | Примеры |
|-----|--------|---------|
| **Events (fan-out)** | `{entity}.{id}.{sub_entity}.{action}` | `guild.123.channel.created`, `messages.123.456.created` |
| **Presence** | `presence.{user_id}.updated` | `presence.789.updated` |
| **Typing** | `typing.{guild_id}.{channel_id}` | `typing.123.456` |
| **Voice** | `voice.{guild_id}.{action}` | `voice.123.state_updated` |
| **User** | `user.{user_id}.{action}` / `user.{action}` | `user.789.updated`, `user.deleted` |
| **Gateway** | `gateway.{event}.{user_id}` | `gateway.connected.789` |
| **Request/Reply** (API GW) | `{service}.{action}` | `auth.login`, `guilds.create`, `users.get` |
| **Moderation** | `moderation.{guild_id}.{sub}.{action}` | `moderation.123.automod.action` |

**Правила:**
1. Только lowercase + `.` + `_` в именах
2. ID всегда как часть subject (для NATS subject-based routing)
3. Events используют JetStream (at-least-once), request/reply — core NATS
4. Wildcard подписки: `guild.123.>` — все события гильдии 123

### Events enum

```rust
#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum Event {
    // Messages
    MessageCreated(MessagePayload),
    MessageUpdated(MessagePartialPayload),
    MessageDeleted(MessageDeletePayload),
    MessageBulkDeleted(MessageBulkDeletePayload),
    MessagePinned(MessagePinPayload),
    MessageUnpinned(MessagePinPayload),

    // Reactions
    ReactionAdded(ReactionPayload),
    ReactionRemoved(ReactionPayload),
    ReactionsRemovedAll(ReactionsRemovedAllPayload),
    ReactionsRemovedEmoji(ReactionsRemovedEmojiPayload),

    // Threads
    ThreadCreated(ChannelPayload),
    ThreadUpdated(ChannelPayload),
    ThreadDeleted(ChannelDeletePayload),

    // Channels
    ChannelCreated(ChannelPayload),
    ChannelUpdated(ChannelPayload),
    ChannelDeleted(ChannelDeletePayload),

    // Guild
    GuildCreated(GuildPayload),
    GuildUpdated(GuildPayload),
    GuildDeleted(GuildDeletePayload),
    MemberJoined(MemberPayload),
    MemberLeft(MemberPayload),
    MemberUpdated(MemberPayload),
    MemberBanned(MemberBanPayload),
    MemberUnbanned(MemberBanPayload),
    MemberTimedOut(MemberTimeoutPayload),

    // Roles
    RoleCreated(RolePayload),
    RoleUpdated(RolePayload),
    RoleDeleted(RoleDeletePayload),

    // Invites
    InviteCreated(InvitePayload),
    InviteDeleted(InviteDeletePayload),

    // Emoji
    GuildEmojisUpdated(GuildEmojisPayload),

    // Webhooks
    WebhooksUpdated(WebhooksUpdatePayload),

    // User
    UserUpdated(UserPayload),
    UserDeleted(UserDeletePayload),

    // Presence
    PresenceUpdated(PresencePayload),
    TypingStarted(TypingPayload),

    // Voice
    VoiceStateUpdated(VoiceStatePayload),

    // Media
    MediaUploaded(MediaPayload),
    MediaDeleted(MediaDeletePayload),

    // Moderation
    AutoModActionExecuted(AutoModActionPayload),
}
```

### Publisher

```rust
pub struct EventPublisher {
    client: async_nats::Client,
}

impl EventPublisher {
    pub async fn publish(&self, subject: &str, event: &Event) -> Result<(), NatsError> {
        let payload = serde_json::to_vec(event)?;
        self.client.publish(subject.to_string(), payload.into()).await?;
        Ok(())
    }

    /// Publish с JetStream (at-least-once delivery)
    pub async fn publish_persistent(&self, subject: &str, event: &Event) -> Result<(), NatsError> {
        let js = async_nats::jetstream::new(self.client.clone());
        let payload = serde_json::to_vec(event)?;
        js.publish(subject.to_string(), payload.into()).await?.await?;
        Ok(())
    }
}
```

### Subject constants

```rust
pub mod subjects {
    // Messages
    pub fn message_created(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.created")
    }
    pub fn message_updated(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.updated")
    }
    pub fn message_deleted(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.deleted")
    }
    pub fn message_bulk_deleted(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.bulk_deleted")
    }
    pub fn message_pinned(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.pins_update")
    }

    // Reactions
    pub fn reaction_added(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.reaction_added")
    }
    pub fn reaction_removed(guild_id: i64, channel_id: i64) -> String {
        format!("messages.{guild_id}.{channel_id}.reaction_removed")
    }

    // Channels
    pub fn channel_created(guild_id: i64) -> String {
        format!("guild.{guild_id}.channel.created")
    }
    pub fn channel_updated(guild_id: i64) -> String {
        format!("guild.{guild_id}.channel.updated")
    }
    pub fn channel_deleted(guild_id: i64) -> String {
        format!("guild.{guild_id}.channel.deleted")
    }

    // Guild
    pub fn guild_created() -> String {
        "guild.created".to_string()
    }
    pub fn guild_updated(guild_id: i64) -> String {
        format!("guild.{guild_id}.updated")
    }
    pub fn guild_deleted(guild_id: i64) -> String {
        format!("guild.{guild_id}.deleted")
    }
    pub fn guild_member_joined(guild_id: i64) -> String {
        format!("guild.{guild_id}.member.joined")
    }
    pub fn guild_member_left(guild_id: i64) -> String {
        format!("guild.{guild_id}.member.left")
    }
    pub fn guild_member_banned(guild_id: i64) -> String {
        format!("guild.{guild_id}.member.banned")
    }
    pub fn guild_member_unbanned(guild_id: i64) -> String {
        format!("guild.{guild_id}.member.unbanned")
    }

    // Roles
    pub fn role_created(guild_id: i64) -> String {
        format!("guild.{guild_id}.role.created")
    }
    pub fn role_updated(guild_id: i64) -> String {
        format!("guild.{guild_id}.role.updated")
    }
    pub fn role_deleted(guild_id: i64) -> String {
        format!("guild.{guild_id}.role.deleted")
    }

    // Typing
    pub fn typing(guild_id: i64, channel_id: i64) -> String {
        format!("typing.{guild_id}.{channel_id}")
    }

    // Presence
    pub fn presence_updated(user_id: i64) -> String {
        format!("presence.{user_id}.updated")
    }

    // Voice
    pub fn voice_state_updated(guild_id: i64) -> String {
        format!("voice.{guild_id}.state_updated")
    }

    // User
    pub fn user_updated(user_id: i64) -> String {
        format!("user.{user_id}.updated")
    }
    pub fn user_deleted() -> String {
        "user.deleted".to_string()
    }

    // Moderation
    pub fn automod_action(guild_id: i64) -> String {
        format!("moderation.{guild_id}.automod.action")
    }
}
```

---

## crates/rate-limit

Tower middleware для rate limiting через Redis sliding window.

```
crates/rate-limit/
├── src/
│   ├── lib.rs
│   ├── middleware.rs     # Tower Layer + Service
│   ├── limiter.rs       # Redis sliding window algorithm
│   └── config.rs        # Rate limit bucket definitions
└── Cargo.toml
```

### Sliding Window Algorithm (Redis)

```lua
-- Lua script для atomic sliding window
-- KEYS[1] = rate limit key
-- ARGV[1] = limit (max requests)
-- ARGV[2] = window (seconds)
-- ARGV[3] = now (current timestamp in ms)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2]) * 1000
local now = tonumber(ARGV[3])

-- Удалить устаревшие записи
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

-- Подсчитать текущие
local count = redis.call('ZCARD', key)

if count < limit then
    -- Разрешить: добавить timestamp
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, window)
    return {1, limit - count - 1, window}  -- allowed, remaining, reset_after
else
    -- Отклонить
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local reset_after = oldest[2] + window - now
    return {0, 0, reset_after}  -- denied, remaining=0, reset_after_ms
end
```

### Tower Middleware

```rust
use tower::{Layer, Service};

#[derive(Clone)]
pub struct RateLimitLayer {
    limiter: Arc<RateLimiter>,
    bucket: String,
    limit: u32,
    window_secs: u64,
}

impl<S> Layer<S> for RateLimitLayer {
    type Service = RateLimitService<S>;

    fn layer(&self, inner: S) -> Self::Service {
        RateLimitService {
            inner,
            limiter: self.limiter.clone(),
            bucket: self.bucket.clone(),
            limit: self.limit,
            window_secs: self.window_secs,
        }
    }
}
```

### Bucket Definitions

```rust
pub enum RateBucket {
    Global,                          // 50/1s per user
    MessageCreate { channel_id: i64 },  // 5/5s per channel
    MessageDelete { channel_id: i64 },  // 5/1s per channel
    Reaction { channel_id: i64 },       // 1/250ms per channel
    AuthLogin,                          // 5/15min per IP
    AuthRegister,                       // 3/1h per IP
    FileUpload,                         // 10/1min per user
}

impl RateBucket {
    pub fn key(&self, user_id: i64) -> String {
        match self {
            Self::Global => format!("rate:global:{user_id}"),
            Self::MessageCreate { channel_id } => format!("rate:msg_create:{channel_id}"),
            Self::AuthLogin => format!("rate:auth_login:{user_id}"),
            // ...
        }
    }

    pub fn limit(&self) -> u32 { /* ... */ }
    pub fn window_secs(&self) -> u64 { /* ... */ }
}
```
