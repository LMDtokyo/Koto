# Notification Service

Сервис уведомлений. Push (Web Push, FCM, APNs), email (SMTP), in-app badge counts, агрегация, notification settings.

Порт: `3006`
Путь: `services/notifications/`
Кеш: Redis
БД: PostgreSQL (настройки, device tokens, подписки)

## Источники

- [RFC 8030 — Generic Event Delivery Using HTTP Push](https://datatracker.ietf.org/doc/html/rfc8030)
- [RFC 8291 — Message Encryption for Web Push](https://www.rfc-editor.org/rfc/rfc8291.html)
- [RFC 8292 — VAPID](https://datatracker.ietf.org/doc/html/rfc8292)
- [Firebase Cloud Messaging — FCM v1 API](https://firebase.google.com/docs/cloud-messaging/send/v1-api)
- [Apple Push Notification service — APNs](https://developer.apple.com/documentation/usernotifications/sending-notification-requests-to-apns)
- [Discord Support — Notifications Settings 101](https://support.discord.com/hc/en-us/articles/215253258-Notifications-Settings-101)
- [Discord Developer Docs — Guild Resource](https://docs.discord.com/developers/resources/guild)
- [crate: web-push](https://docs.rs/web-push/latest/web_push/)
- [crate: a2](https://docs.rs/a2/latest/a2/) — APNs client
- [crate: lettre](https://docs.rs/lettre/latest/lettre/) — SMTP
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Структура сервиса

```
services/notifications/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── settings.rs        # Notification settings CRUD
│   │   ├── devices.rs         # Device token registration
│   │   └── unread.rs          # Unread counts
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── settings.rs
│   │   ├── devices.rs
│   │   └── dispatcher.rs      # Dispatch logic (push/email routing)
│   ├── models/
│   │   ├── mod.rs
│   │   ├── device_token.rs
│   │   ├── notification_settings.rs
│   │   └── unread_state.rs
│   ├── providers/
│   │   ├── mod.rs
│   │   ├── web_push.rs        # Web Push (VAPID + AES-128-GCM)
│   │   ├── fcm.rs             # Firebase Cloud Messaging v1
│   │   ├── apns.rs            # Apple Push Notification service
│   │   └── email.rs           # SMTP (lettre)
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   ├── events/
│   │   └── subscriber.rs      # NATS subscriber (слушает события)
│   └── aggregation/
│       ├── mod.rs
│       ├── batcher.rs         # Time-window batching
│       └── formatter.rs       # Notification content formatting
├── tests/
│   ├── common/mod.rs
│   ├── dispatch_test.rs
│   ├── settings_test.rs
│   └── aggregation_test.rs
├── migrations/
│   ├── 001_create_device_tokens.sql
│   ├── 002_create_notification_settings.sql
│   └── 003_create_channel_overrides.sql
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "notifications-service"
version = "0.1.0"
edition = "2024"

[dependencies]
# HTTP / async
axum = { workspace = true }
tokio = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }

# Сериализация
serde = { workspace = true }
serde_json = { workspace = true }

# БД
sqlx = { workspace = true }

# Кеш
redis = { workspace = true }
deadpool-redis = "0.18"

# Push providers
web-push = "0.11"             # Web Push (VAPID)
a2 = "0.10"                   # APNs HTTP/2
reqwest = { version = "0.12", features = ["json"] }  # FCM v1 HTTP API

# Email
lettre = { version = "0.12", features = ["tokio1", "rustls-tls", "pool", "tracing"] }

# Брокер событий
async-nats = { workspace = true }

# Аутентификация
jsonwebtoken = { workspace = true }

# Валидация
validator = { workspace = true }

# Время / ID
chrono = { workspace = true }
uuid = { workspace = true }

# Ошибки
thiserror = { workspace = true }

# Логирование
tracing = { workspace = true }
tracing-subscriber = { workspace = true }

# Конфигурация
config = "0.15"
dotenvy = "0.15"

# Внутренние crates
common = { path = "../../crates/common" }
snowflake = { path = "../../crates/snowflake" }
cache = { path = "../../crates/cache" }
nats-events = { path = "../../crates/nats-events" }
rate-limit = { path = "../../crates/rate-limit" }
```

---

## Конфигурация (config.rs)

```rust
pub struct NotificationConfig {
    // Сервер
    pub host: String,                    // NOTIFICATION_HOST=0.0.0.0
    pub port: u16,                       // NOTIFICATION_PORT=3006

    // PostgreSQL
    pub database_url: String,            // DATABASE_URL=postgres://...
    pub db_max_connections: u32,         // DB_MAX_CONNECTIONS=10

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT (валидация)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=...

    // Web Push (VAPID)
    pub vapid_private_key_path: String,  // VAPID_PRIVATE_KEY_PATH=/keys/vapid_private.pem
    pub vapid_subject: String,           // VAPID_SUBJECT=mailto:admin@example.com

    // FCM
    pub fcm_project_id: String,          // FCM_PROJECT_ID=my-project
    pub fcm_service_account_path: String,// FCM_SERVICE_ACCOUNT_PATH=/keys/fcm.json

    // APNs
    pub apns_key_path: String,           // APNS_KEY_PATH=/keys/apns_key.p8
    pub apns_key_id: String,             // APNS_KEY_ID=ABC123DEFG
    pub apns_team_id: String,            // APNS_TEAM_ID=DEF123GHIJ
    pub apns_topic: String,              // APNS_TOPIC=com.example.app
    pub apns_production: bool,           // APNS_PRODUCTION=true

    // Email (SMTP)
    pub smtp_host: String,               // SMTP_HOST=smtp.example.com
    pub smtp_port: u16,                  // SMTP_PORT=587
    pub smtp_user: String,               // SMTP_USER=...
    pub smtp_pass: String,               // SMTP_PASS=...
    pub email_from: String,              // EMAIL_FROM=noreply@example.com

    // Aggregation
    pub batch_window_secs: u64,          // BATCH_WINDOW_SECS=10
    pub max_batch_size: usize,           // MAX_BATCH_SIZE=50
    pub email_digest_interval_secs: u64, // EMAIL_DIGEST_INTERVAL_SECS=1800
}
```

---

## Формат ошибок

```json
{
    "code": "DEVICE_NOT_FOUND",
    "message": "Device token not found"
}
```

### Коды ошибок

| Код | HTTP | Описание |
|-----|------|----------|
| `DEVICE_NOT_FOUND` | 404 | Device token не найден |
| `INVALID_DEVICE_TOKEN` | 400 | Невалидный device token |
| `SETTINGS_NOT_FOUND` | 404 | Настройки не найдены |
| `PUSH_DELIVERY_FAILED` | 500 | Ошибка доставки push |
| `EMAIL_DELIVERY_FAILED` | 500 | Ошибка отправки email |
| `MISSING_PERMISSIONS` | 403 | Нет прав |
| `RATE_LIMITED` | 429 | Превышен rate limit |
| `VALIDATION_ERROR` | 400 | Ошибка валидации |

---

## Миграции (PostgreSQL)

### 001_create_device_tokens.sql

```sql
CREATE TABLE device_tokens (
    id          BIGINT       PRIMARY KEY,  -- Snowflake ID
    user_id     BIGINT       NOT NULL,
    platform    SMALLINT     NOT NULL,     -- 0=web, 1=android, 2=ios, 3=desktop
    token       TEXT         NOT NULL,     -- device registration token
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (user_id, token)
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
```

### 002_create_notification_settings.sql

```sql
CREATE TABLE notification_settings (
    user_id                 BIGINT    NOT NULL,
    guild_id                BIGINT    NOT NULL,
    message_notifications   SMALLINT  NOT NULL DEFAULT 3,  -- 0=ALL, 1=MENTIONS, 2=NOTHING, 3=DEFAULT
    suppress_everyone       BOOLEAN   NOT NULL DEFAULT false,
    suppress_roles          BOOLEAN   NOT NULL DEFAULT false,
    muted                   BOOLEAN   NOT NULL DEFAULT false,
    mute_until              TIMESTAMPTZ,                   -- NULL = indefinite
    mobile_push             BOOLEAN   NOT NULL DEFAULT true,

    PRIMARY KEY (user_id, guild_id)
);

CREATE INDEX idx_notification_settings_guild ON notification_settings (guild_id);
```

### 003_create_channel_overrides.sql

```sql
CREATE TABLE channel_notification_overrides (
    user_id                 BIGINT    NOT NULL,
    channel_id              BIGINT    NOT NULL,
    guild_id                BIGINT    NOT NULL,
    message_notifications   SMALLINT  NOT NULL DEFAULT 3,  -- 0=ALL, 1=MENTIONS, 2=NOTHING, 3=DEFAULT
    muted                   BOOLEAN   NOT NULL DEFAULT false,
    mute_until              TIMESTAMPTZ,

    PRIMARY KEY (user_id, channel_id)
);

CREATE INDEX idx_channel_overrides_guild ON channel_notification_overrides (guild_id);
```

---

## Notification Levels

| Значение | Имя | Поведение |
|----------|-----|-----------|
| 0 | `ALL_MESSAGES` | Уведомление на каждое сообщение |
| 1 | `ONLY_MENTIONS` | Только @mentions |
| 2 | `NOTHING` | Без уведомлений (но unread badge для @mentions остаётся) |
| 3 | `DEFAULT` | Наследовать от гильдии |

### Приоритет настроек

```
Channel override → Guild user setting → Guild default → ALL_MESSAGES (для DM)
```

1. Если у канала есть override для user → использовать его
2. Если override = DEFAULT (3) или нет override → использовать guild setting пользователя
3. Если guild setting = DEFAULT (3) → использовать `guild.default_message_notifications`
4. DM: всегда ALL_MESSAGES

---

## Mention Routing

| Тип | Кто получает | Suppressed? |
|-----|-------------|-------------|
| `@user` | Конкретный пользователь | Нет (всегда доставляется, если не muted) |
| `@role` | Все с ролью | `suppress_roles = true` → нет уведомления |
| `@everyone` | Все участники канала | `suppress_everyone = true` → нет уведомления |
| `@here` | Только online участники | `suppress_everyone = true` → нет уведомления |

**DND статус**: подавляет ВСЕ уведомления (push не отправляется).

---

## Dispatch Pipeline

```
NATS event (message.created, user.friend.request, etc.)
    ↓
Subscriber получает event
    ↓
Определить целевых пользователей:
  - message.created → все участники канала (с правом VIEW_CHANNEL)
  - user.friend.request → конкретный пользователь
  - guild.member.joined → owner (если настроено)
    ↓
Для каждого пользователя:
  ├── Проверить presence: если online + focused на канале → skip push
  ├── Проверить DND: если dnd → skip push
  ├── Проверить mute: если guild/channel muted → skip push
  ├── Проверить notification level:
  │     ALL → отправить
  │     MENTIONS → только если @mentioned
  │     NOTHING → skip
  ├── Проверить suppress_everyone/suppress_roles
  └── Если проходит все проверки → добавить в batch
    ↓
Aggregation batcher (10 сек окно):
  ├── Группировка по user + channel
  ├── Если 1 сообщение → "User: message preview"
  ├── Если N сообщений → "N new messages in #channel"
  └── Emit batched notification
    ↓
Dispatch по платформам:
  ├── Web Push (web-push crate)
  ├── FCM (Android)
  ├── APNs (iOS)
  ├── In-app badge (Redis)
  └── Email (lettre, только для offline > 30 мин)
```

---

## Push Providers

### Web Push (VAPID)

```rust
use web_push::{
    SubscriptionInfo, WebPushMessageBuilder,
    VapidSignatureBuilder, IsahcWebPushClient,
    ContentEncoding, WebPushClient,
};
use std::fs::File;

async fn send_web_push(
    subscription: &SubscriptionInfo,
    payload: &[u8],
    vapid_key_path: &str,
) -> Result<(), Error> {
    let file = File::open(vapid_key_path)?;
    let sig = VapidSignatureBuilder::from_pem(file, subscription)?.build()?;

    let mut builder = WebPushMessageBuilder::new(subscription);
    builder.set_payload(ContentEncoding::Aes128Gcm, payload);
    builder.set_vapid_signature(sig);

    let client = IsahcWebPushClient::new()?;
    client.send(builder.build()?).await?;
    Ok(())
}
```

### FCM v1 (Android)

```
POST https://fcm.googleapis.com/v1/projects/{project_id}/messages:send
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "message": {
        "token": "{device_token}",
        "data": {
            "type": "MESSAGE_CREATE",
            "guild_id": "123",
            "channel_id": "456",
            "message_id": "789",
            "content_preview": "Hello everyone!",
            "author_name": "User",
            "channel_name": "#general",
            "guild_name": "My Server"
        },
        "android": {
            "priority": "high",
            "ttl": "300s",
            "notification": {
                "tag": "channel:456",
                "click_action": "OPEN_CHANNEL"
            }
        }
    }
}
```

**Collapse**: `tag` = `channel:{channel_id}` → новые сообщения в том же канале заменяют старое уведомление.

### APNs (iOS)

```rust
use a2::{Client, DefaultNotificationBuilder, NotificationBuilder, NotificationOptions, Priority};

async fn send_apns(
    device_token: &str,
    title: &str,
    body: &str,
    badge: u32,
    config: &NotificationConfig,
) -> Result<(), Error> {
    let client = Client::token(
        &mut File::open(&config.apns_key_path)?,
        &config.apns_key_id,
        &config.apns_team_id,
        config.apns_production,
    )?;

    let mut builder = DefaultNotificationBuilder::new();
    builder.set_title(title);
    builder.set_body(body);
    builder.set_badge(badge);
    builder.set_sound("default");
    builder.set_category("NEW_MESSAGE");

    let options = NotificationOptions {
        apns_topic: Some(&config.apns_topic),
        apns_collapse_id: Some(&collapse_id),  // channel_id
        apns_priority: Some(Priority::High),
        ..Default::default()
    };

    let payload = builder.build(device_token, options);
    client.send(payload).await?;
    Ok(())
}
```

### Email (SMTP)

```rust
use lettre::{
    AsyncSmtpTransport, AsyncTransport, Tokio1Executor,
    Message as EmailMessage,
    message::header::ContentType,
    transport::smtp::authentication::Credentials,
};

async fn send_email(
    to: &str,
    subject: &str,
    html_body: &str,
    config: &NotificationConfig,
) -> Result<(), Error> {
    let email = EmailMessage::builder()
        .from(config.email_from.parse()?)
        .to(to.parse()?)
        .subject(subject)
        .header(ContentType::TEXT_HTML)
        .body(html_body.to_string())?;

    let creds = Credentials::new(
        config.smtp_user.clone(),
        config.smtp_pass.clone(),
    );

    let mailer: AsyncSmtpTransport<Tokio1Executor> =
        AsyncSmtpTransport::<Tokio1Executor>::relay(&config.smtp_host)?
            .credentials(creds)
            .build();

    mailer.send(email).await?;
    Ok(())
}
```

**Когда email**: пользователь офлайн > 30 минут + есть непрочитанные @mentions.

---

## Aggregation

### Time-window batching

```rust
struct NotificationBatcher {
    pending: HashMap<(UserId, ChannelId), Vec<NotificationEvent>>,
    window: Duration,  // 10 секунд
}

impl NotificationBatcher {
    fn add(&mut self, user_id: UserId, channel_id: ChannelId, event: NotificationEvent) {
        let key = (user_id, channel_id);
        self.pending.entry(key).or_default().push(event);
        // Запустить таймер для key если ещё не запущен
    }

    fn flush(&mut self, user_id: UserId, channel_id: ChannelId) -> Option<BatchedNotification> {
        let events = self.pending.remove(&(user_id, channel_id))?;
        let result = match events.len() {
            0 => return None,
            1 => BatchedNotification::Single(events.into_iter().next()?),
            n => BatchedNotification::Aggregated {
                count: n,
                channel_id,
                last_author: events.last()?.author.clone(),
                preview: format!("{n} new messages in #{}", events[0].channel_name),
            },
        };
        Some(result)
    }
}
```

### Collapse keys

| Платформа | Механизм | Ключ |
|-----------|----------|------|
| FCM | `notification.tag` | `channel:{channel_id}` |
| APNs | `apns-collapse-id` | `channel:{channel_id}` |
| Web Push | `Topic` header | `channel:{channel_id}` |

→ Новое уведомление в том же канале **заменяет** предыдущее, а не добавляется.

---

## Unread State

### Redis структура

```
HSET unread:{user_id}:{guild_id} {channel_id} {last_message_id}
HSET mentions:{user_id}:{guild_id} {channel_id} {mention_count}
```

### Подсчёт badge

```
Guild badge = SUM(mentions:{user_id}:{guild_id} values)
Total badge = SUM across all guilds + DM unread count
```

### Mark as read

Когда пользователь открывает канал (через Gateway event):
```
HSET unread:{user_id}:{guild_id} {channel_id} {latest_message_id}
HSET mentions:{user_id}:{guild_id} {channel_id} 0
```

---

## API Endpoints

### GET /users/@me/notification-settings/:guild_id

Получить настройки уведомлений для гильдии.

**Response (200):**

```json
{
    "guild_id": "123456789",
    "message_notifications": 1,
    "suppress_everyone": false,
    "suppress_roles": false,
    "muted": false,
    "mute_until": null,
    "mobile_push": true,
    "channel_overrides": [
        {
            "channel_id": "987654321",
            "message_notifications": 2,
            "muted": false,
            "mute_until": null
        }
    ]
}
```

---

### PATCH /users/@me/notification-settings/:guild_id

Обновить настройки.

**Request:**

```json
{
    "message_notifications": 1,
    "suppress_everyone": true,
    "muted": false,
    "mobile_push": true
}
```

---

### PUT /users/@me/notification-settings/:guild_id/channels/:channel_id

Установить channel override.

**Request:**

```json
{
    "message_notifications": 2,
    "muted": true,
    "mute_until": "2026-02-22T12:00:00Z"
}
```

---

### POST /users/@me/devices

Зарегистрировать device token.

**Request:**

```json
{
    "platform": "android",
    "token": "fcm_device_token_here"
}
```

**Platform values**: `web`, `android`, `ios`, `desktop`

---

### DELETE /users/@me/devices/:device_id

Удалить device token (logout с устройства).

---

### GET /users/@me/unread

Получить unread counts.

**Response (200):**

```json
{
    "guilds": {
        "123456789": {
            "mention_count": 3,
            "channels": {
                "987654321": {
                    "last_message_id": "111222333",
                    "mention_count": 2
                }
            }
        }
    },
    "dm_unread": 1
}
```

---

### POST /channels/:channel_id/ack

Отметить канал как прочитанный.

**Request:**

```json
{
    "message_id": "111222333"
}
```

---

## NATS Events

### Подписки (входящие)

| Subject | Источник | Действие |
|---------|----------|----------|
| `messages.*.*.created` | Message Service | Dispatch push/email уведомления |
| `user.friend.request` | User Service | Push уведомление получателю |
| `user.friend.accepted` | User Service | Push уведомление отправителю |
| `guild.member.joined` | Guild Service | Push owner (если настроено) |
| `guild.member.banned` | Guild Service | Push забаненному |
| `guild.invite.used` | Guild Service | Обновить invite stats |
| `presence.updated` | Presence Service | Обновить presence cache (для suppress logic) |

### Публикуемые

| Subject | Payload | Описание |
|---------|---------|----------|
| `notification.sent` | `{ user_id, type, platform, success }` | Результат отправки |
| `notification.read` | `{ user_id, channel_id, message_id }` | Канал прочитан |

---

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /users/@me/devices | 5 запросов | 1 минута |
| PATCH notification-settings | 10 запросов | 1 минута |
| POST /channels/:id/ack | 30 запросов | 1 минута |
| GET /users/@me/unread | 10 запросов | 1 минута |

### Email rate limits

| Лимит | Значение |
|-------|----------|
| Per recipient per channel | 1 email / 30 мин |
| Per user daily | max 50 emails |
| Global throughput | Зависит от SMTP provider |

---

## Мониторинг

### Метрики

| Метрика | Тип | Описание |
|---------|-----|----------|
| `notifications_dispatched_total{platform,type}` | counter | Отправленные по платформе и типу |
| `notifications_delivery_success_total{platform}` | counter | Успешно доставленные |
| `notifications_delivery_failed_total{platform}` | counter | Ошибки доставки |
| `notifications_dispatch_duration_seconds{platform}` | histogram | Время отправки |
| `notifications_batch_size` | histogram | Размер batch-ей |
| `notifications_suppressed_total{reason}` | counter | Подавленные (mute, dnd, focus) |
| `email_sent_total` | counter | Отправленные email-ы |
| `unread_badge_updates_total` | counter | Обновления badge count |
| `device_tokens_total{platform}` | gauge | Зарегистрированные device tokens |
| `nats_events_processed_total` | counter | Обработанные NATS events |

### Алерты

| Условие | Severity | Описание |
|---------|----------|----------|
| `notifications_delivery_failed_total` rate > 100/min | critical | Массовые ошибки доставки |
| `notifications_dispatch_duration_seconds{p99}` > 5s | warning | Медленная отправка |
| APNs 410 (Unregistered) rate > 10% | warning | Много невалидных device tokens |
| Email bounce rate > 5% | warning | Проблемы с email доставкой |

---

## Безопасность

### Чеклист

- [ ] VAPID private key хранится в Kubernetes Secret, не в коде
- [ ] FCM service account JSON — в Secret
- [ ] APNs key (.p8) — в Secret
- [ ] SMTP credentials — в Secret
- [ ] Push payload не содержит полный текст сообщения (только preview ~100 chars)
- [ ] Push payload шифруется (Web Push: AES-128-GCM, RFC 8291)
- [ ] Device tokens валидируются при регистрации
- [ ] Stale device tokens удаляются (APNs 410, FCM errors)
- [ ] Email: SPF, DKIM, DMARC настроены
- [ ] Email не содержит sensitive data (пароли, токены)
- [ ] Rate limiting на email (per user daily cap)
- [ ] Notification settings контролируются пользователем
- [ ] DND статус уважается
- [ ] Логирование: отправки без payload content
