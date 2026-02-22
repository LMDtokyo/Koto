# Message Service

Сервис управления сообщениями. CRUD сообщений, реакции, пины, треды, вложения, упоминания, cursor-based пагинация.

Порт: `3004`
Путь: `services/messages/`
БД: PostgreSQL (старт) → ScyllaDB (при масштабировании > 100M сообщений)
Кеш: Redis

## Источники

- [Discord Developer Docs — Message Resource](https://docs.discord.com/developers/resources/message)
- [Discord Developer Docs — Channel Resource](https://docs.discord.com/developers/resources/channel)
- [Discord Developer Docs — Permissions](https://docs.discord.com/developers/topics/permissions)
- [Discord Developer Docs — Rate Limits](https://docs.discord.com/developers/topics/rate-limits)
- [Discord Developer Docs — Gateway Events](https://docs.discord.com/developers/events/gateway-events)
- [Discord Developer Docs — Threads](https://docs.discord.com/developers/topics/threads)
- [Discord Engineering — How Discord Stores Trillions of Messages](https://discord.com/blog/how-discord-stores-trillions-of-messages)
- [oEmbed Specification](https://oembed.com/)
- [PostgreSQL 17 — Table Partitioning](https://www.postgresql.org/docs/17/ddl-partitioning.html)
- [PostgreSQL 17 — GIN Indexes](https://www.postgresql.org/docs/17/gin.html)
- [ScyllaDB — Data Modeling Best Practices](https://www.scylladb.com/2019/08/20/best-practices-for-data-modeling/)
- [ScyllaDB — TTL Documentation](https://docs.scylladb.com/stable/cql/time-to-live.html)
- [Five Ways to Paginate in Postgres — CitusData](https://www.citusdata.com/blog/2016/03/30/five-ways-to-paginate/)
- [crate: sqlx](https://docs.rs/sqlx/latest/sqlx/)
- [crate: scylla](https://docs.rs/scylla/latest/scylla/) — async CQL driver for Rust
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)
- [crate: linkify](https://docs.rs/linkify/latest/linkify/)
- [crate: ammonia](https://docs.rs/ammonia/latest/ammonia/) — HTML sanitizer
- [crate: validator](https://docs.rs/validator/latest/validator/)
- [GitHub: scylla-rust-driver](https://github.com/scylladb/scylla-rust-driver)

---

## Структура сервиса

```
services/messages/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── messages.rs
│   │   ├── reactions.rs
│   │   ├── pins.rs
│   │   ├── threads.rs
│   │   └── bulk.rs
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── messages.rs
│   │   ├── reactions.rs
│   │   ├── pins.rs
│   │   ├── threads.rs
│   │   └── bulk.rs
│   ├── models/
│   │   ├── mod.rs
│   │   ├── message.rs
│   │   ├── embed.rs
│   │   ├── attachment.rs
│   │   ├── reaction.rs
│   │   └── thread.rs
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   ├── events/
│   │   ├── publisher.rs
│   │   └── subscriber.rs
│   ├── schemas/
│   │   ├── mod.rs
│   │   ├── create_message.rs
│   │   ├── update_message.rs
│   │   └── query_params.rs
│   └── services/
│       ├── mention_parser.rs
│       ├── embed_resolver.rs
│       └── content_validator.rs
├── tests/
│   ├── common/mod.rs
│   ├── messages_test.rs
│   ├── reactions_test.rs
│   ├── pins_test.rs
│   └── threads_test.rs
├── migrations/
│   ├── 001_create_messages.sql
│   ├── 002_create_attachments.sql
│   ├── 003_create_reactions.sql
│   ├── 004_create_pins.sql
│   └── 005_create_message_mentions.sql
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "messages-service"
version = "0.1.0"
edition = "2024"

[dependencies]
# HTTP / async
axum = { workspace = true }
axum-extra = { version = "0.10", features = ["query"] }
tokio = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }

# Сериализация
serde = { workspace = true }
serde_json = { workspace = true }

# БД
sqlx = { workspace = true }

# ScyllaDB (подключается при масштабировании)
# scylla = "0.15"

# Кеш
redis = { workspace = true }
deadpool-redis = "0.18"

# Брокер событий
async-nats = { workspace = true }

# Аутентификация
jsonwebtoken = { workspace = true }

# Валидация
validator = { workspace = true }

# Время / ID
chrono = { workspace = true }
uuid = { workspace = true }

# Контент-процессинг
linkify = "0.10"
ammonia = "4"
regex = "1"

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
permissions = { path = "../../crates/permissions" }
db = { path = "../../crates/db" }
cache = { path = "../../crates/cache" }
nats-events = { path = "../../crates/nats-events" }
rate-limit = { path = "../../crates/rate-limit" }
```

---

## Конфигурация (config.rs)

```rust
pub struct MessageConfig {
    // Сервер
    pub host: String,                    // MESSAGE_HOST=0.0.0.0
    pub port: u16,                       // MESSAGE_PORT=3004

    // PostgreSQL
    pub database_url: String,            // DATABASE_URL=postgres://...
    pub db_max_connections: u32,         // DB_MAX_CONNECTIONS=20

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT (только валидация, не создание)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=<ES256 public key PEM>

    // Лимиты контента
    pub max_content_length: usize,       // MAX_CONTENT_LENGTH=2000
    pub max_embeds_per_message: usize,   // MAX_EMBEDS_PER_MESSAGE=10
    pub max_embed_total_chars: usize,    // MAX_EMBED_TOTAL_CHARS=6000
    pub max_reactions_per_message: usize,// MAX_REACTIONS_PER_MESSAGE=20
    pub max_pins_per_channel: usize,     // MAX_PINS_PER_CHANNEL=50
    pub max_bulk_delete: usize,          // MAX_BULK_DELETE=100
    pub bulk_delete_max_age_days: u16,   // BULK_DELETE_MAX_AGE_DAYS=14

    // Embed resolver
    pub embed_resolve_timeout_ms: u64,   // EMBED_RESOLVE_TIMEOUT_MS=5000
    pub embed_max_response_bytes: usize, // EMBED_MAX_RESPONSE_BYTES=1048576
}
```

---

## Формат ошибок

```json
{
    "code": "MESSAGE_NOT_FOUND",
    "message": "Message not found"
}
```

### Коды ошибок

| Код | HTTP | Описание |
|-----|------|----------|
| `MESSAGE_NOT_FOUND` | 404 | Сообщение не найдено |
| `CHANNEL_NOT_FOUND` | 404 | Канал не найден |
| `MISSING_PERMISSIONS` | 403 | Нет прав для действия |
| `MESSAGE_TOO_LONG` | 400 | Превышена максимальная длина контента |
| `EMPTY_MESSAGE` | 400 | Нет контента, embed или attachment |
| `TOO_MANY_EMBEDS` | 400 | Превышен лимит embeds (10) |
| `EMBED_TOO_LARGE` | 400 | Суммарный размер embeds > 6000 символов |
| `TOO_MANY_REACTIONS` | 400 | 20 уникальных реакций на сообщении |
| `TOO_MANY_PINS` | 400 | Превышен лимит пинов на канале (50) |
| `BULK_DELETE_RANGE` | 400 | Кол-во сообщений вне диапазона 2–100 |
| `BULK_DELETE_TOO_OLD` | 400 | Сообщение старше 14 дней |
| `CANNOT_EDIT_OTHER` | 403 | Редактирование чужого сообщения |
| `REACTION_BLOCKED` | 403 | Автор сообщения заблокировал пользователя |
| `SLOWMODE_ACTIVE` | 429 | Пользователь ограничен slowmode канала |
| `RATE_LIMITED` | 429 | Превышен rate limit |
| `VALIDATION_ERROR` | 400 | Ошибка валидации входных данных |
| `THREAD_ARCHIVED` | 403 | Тред заархивирован |
| `INTERNAL_ERROR` | 500 | Внутренняя ошибка |

---

## Миграции (PostgreSQL)

### 001_create_messages.sql

```sql
CREATE TABLE messages (
    id             BIGINT       PRIMARY KEY,  -- Snowflake ID
    channel_id     BIGINT       NOT NULL,
    guild_id       BIGINT,                    -- NULL для DM
    author_id      BIGINT       NOT NULL,
    content        TEXT,                      -- NULL если только embed/attachment
    embeds         JSONB        DEFAULT '[]'::jsonb,
    mention_ids    BIGINT[]     DEFAULT '{}',
    mention_role_ids BIGINT[]   DEFAULT '{}',
    mention_everyone BOOLEAN    DEFAULT false,
    message_type   SMALLINT     NOT NULL DEFAULT 0,
    flags          INTEGER      NOT NULL DEFAULT 0,
    edited_at      TIMESTAMPTZ,
    pinned         BOOLEAN      NOT NULL DEFAULT false,
    tts            BOOLEAN      NOT NULL DEFAULT false,

    -- Reply / reference
    reference_message_id BIGINT,
    reference_channel_id BIGINT,

    -- Thread
    thread_id      BIGINT,                    -- ID треда, если сообщение стартовало тред

    -- Soft delete
    deleted_at     TIMESTAMPTZ,

    -- Timestamps
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Основной индекс: cursor-based пагинация по каналу
CREATE INDEX idx_messages_channel_id ON messages (channel_id, id DESC)
    WHERE deleted_at IS NULL;

-- Поиск по автору (история сообщений пользователя)
CREATE INDEX idx_messages_author ON messages (author_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Пиннеды по каналу
CREATE INDEX idx_messages_pinned ON messages (channel_id)
    WHERE pinned = true AND deleted_at IS NULL;

-- Поиск по guild_id (для модерации)
CREATE INDEX idx_messages_guild ON messages (guild_id, created_at DESC)
    WHERE deleted_at IS NULL AND guild_id IS NOT NULL;

-- Поиск по reference (для ответов)
CREATE INDEX idx_messages_reference ON messages (reference_message_id)
    WHERE reference_message_id IS NOT NULL AND deleted_at IS NULL;

-- GIN индекс для упоминаний (поиск "все сообщения где @user упомянут")
CREATE INDEX idx_messages_mentions ON messages USING GIN (mention_ids)
    WHERE deleted_at IS NULL;

-- GIN индекс для embeds (jsonb_path_ops — компактнее, достаточно для @> оператора)
CREATE INDEX idx_messages_embeds ON messages USING GIN (embeds jsonb_path_ops)
    WHERE deleted_at IS NULL;
```

### 002_create_attachments.sql

```sql
CREATE TABLE attachments (
    id             BIGINT       PRIMARY KEY,  -- Snowflake ID
    message_id     BIGINT       NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    channel_id     BIGINT       NOT NULL,
    filename       TEXT         NOT NULL,
    content_type   TEXT,                      -- MIME type
    size           INTEGER      NOT NULL,     -- bytes
    url            TEXT         NOT NULL,     -- CDN URL (MinIO presigned)
    proxy_url      TEXT,                      -- proxied URL
    width          INTEGER,                   -- для изображений/видео
    height         INTEGER,
    description    TEXT,                      -- alt text, max 1024
    duration_secs  REAL,                      -- для voice messages
    waveform       TEXT,                      -- base64 audio waveform
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_message ON attachments (message_id);
CREATE INDEX idx_attachments_channel ON attachments (channel_id, created_at DESC);
```

### 003_create_reactions.sql

```sql
CREATE TABLE reactions (
    message_id     BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    emoji_id       BIGINT,                    -- NULL для Unicode emoji
    emoji_name     TEXT         NOT NULL,     -- Unicode символ или имя custom emoji
    emoji_animated BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (message_id, user_id, emoji_name)
);

-- Подсчёт реакций на сообщении (для проверки лимита 20 unique)
CREATE INDEX idx_reactions_message ON reactions (message_id);

-- Поиск реакций пользователя (для удаления всех реакций при бане)
CREATE INDEX idx_reactions_user ON reactions (user_id);
```

### 004_create_pins.sql

```sql
-- Отдельная таблица для порядка пинов и быстрого подсчёта
CREATE TABLE channel_pins (
    channel_id     BIGINT       NOT NULL,
    message_id     BIGINT       NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    pinned_by      BIGINT       NOT NULL,
    pinned_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (channel_id, message_id)
);

-- Подсчёт пинов на канале (для проверки лимита 50)
CREATE INDEX idx_channel_pins_count ON channel_pins (channel_id);
```

### 005_create_message_mentions.sql

```sql
-- Денормализованная таблица для быстрого поиска "непрочитанные упоминания"
CREATE TABLE message_mentions (
    message_id     BIGINT       NOT NULL,
    channel_id     BIGINT       NOT NULL,
    guild_id       BIGINT,
    user_id        BIGINT       NOT NULL,     -- кого упомянули
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (message_id, user_id)
);

-- Быстрый поиск непрочитанных упоминаний для пользователя
CREATE INDEX idx_mentions_user ON message_mentions (user_id, created_at DESC);
CREATE INDEX idx_mentions_channel_user ON message_mentions (channel_id, user_id, created_at DESC);
```

---

## Типы сообщений

```rust
#[repr(u8)]
pub enum MessageType {
    Default                    = 0,
    RecipientAdd               = 1,
    RecipientRemove            = 2,
    Call                       = 3,
    ChannelNameChange          = 4,
    ChannelIconChange          = 5,
    ChannelPinnedMessage       = 6,  // системное: "X pinned a message"
    UserJoin                   = 7,
    GuildBoost                 = 8,
    GuildBoostTier1            = 9,
    GuildBoostTier2            = 10,
    GuildBoostTier3            = 11,
    ChannelFollowAdd           = 12,
    ThreadCreated              = 18,
    Reply                      = 19,
    ThreadStarterMessage       = 21,
    GuildInviteReminder        = 22,
    AutoModerationAction       = 24,
}
```

**Правила удаления**: системные сообщения типов 1–5 и 21 **не могут** быть удалены. Остальные — могут (type 24 требует `MANAGE_MESSAGES`).

---

## Message Flags (битовые)

```rust
bitflags! {
    pub struct MessageFlags: u32 {
        const CROSSPOSTED            = 1 << 0;  // опубликовано в подписанные каналы
        const IS_CROSSPOST           = 1 << 1;  // пришло из другого канала
        const SUPPRESS_EMBEDS        = 1 << 2;  // embeds скрыты
        const URGENT                 = 1 << 4;  // срочное сообщение
        const HAS_THREAD             = 1 << 5;  // есть ассоциированный тред
        const EPHEMERAL              = 1 << 6;  // видимо только автору
        const LOADING                = 1 << 7;  // deferred interaction response
        const SUPPRESS_NOTIFICATIONS = 1 << 12; // не триггерит push-уведомления
        const IS_VOICE_MESSAGE       = 1 << 13; // голосовое сообщение
    }
}
```

---

## Embeds

### Структура embed

```rust
pub struct Embed {
    pub title: Option<String>,       // max 256
    pub description: Option<String>, // max 4096
    pub url: Option<String>,
    pub timestamp: Option<String>,   // ISO8601
    pub color: Option<u32>,          // RGB decimal
    pub footer: Option<EmbedFooter>,
    pub image: Option<EmbedMedia>,
    pub thumbnail: Option<EmbedMedia>,
    pub video: Option<EmbedMedia>,
    pub provider: Option<EmbedProvider>,
    pub author: Option<EmbedAuthor>,
    pub fields: Option<Vec<EmbedField>>, // max 25
}

pub struct EmbedFooter {
    pub text: String,                // max 2048
    pub icon_url: Option<String>,
}

pub struct EmbedMedia {
    pub url: String,
    pub proxy_url: Option<String>,
    pub height: Option<u32>,
    pub width: Option<u32>,
}

pub struct EmbedProvider {
    pub name: Option<String>,
    pub url: Option<String>,
}

pub struct EmbedAuthor {
    pub name: String,                // max 256
    pub url: Option<String>,
    pub icon_url: Option<String>,
}

pub struct EmbedField {
    pub name: String,                // max 256
    pub value: String,               // max 1024
    pub inline: Option<bool>,
}
```

### Лимиты embeds

| Ограничение | Лимит |
|-------------|-------|
| Embeds на сообщение | 10 |
| Суммарно символов (title + description + footer.text + author.name + fields.name + fields.value) | 6000 |
| title | 256 |
| description | 4096 |
| fields | 25 на embed |
| field.name | 256 |
| field.value | 1024 |
| footer.text | 2048 |
| author.name | 256 |

### Link preview (embed resolver)

URL-ы в контенте сообщения автоматически резолвятся в embeds:

1. Парсинг URL-ов из контента (`linkify` crate)
2. HEAD запрос → проверка Content-Type
3. HTML-страницы: fetch `<head>`, ищем:
   - `<link rel="alternate" type="application/json+oembed" href="...">` → oEmbed
   - Open Graph tags: `og:title`, `og:description`, `og:image`, `og:url`
   - Twitter Card tags: `twitter:title`, `twitter:description`, `twitter:image`
   - Fallback: `<title>`, `<meta name="description">`
4. Формирование Embed из полученных данных
5. Результат кешируется в Redis (TTL 1 час)

**Безопасность embed resolver:**
- Таймаут: 5 секунд на запрос
- Максимальный размер ответа: 1 MiB
- Запрет запросов к приватным IP (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.1) — защита от SSRF
- DNS rebinding protection (резолвим DNS до запроса, проверяем IP)
- Whitelist Content-Type (text/html, application/json+oembed)
- Embeds резолвятся **асинхронно** — сообщение отправляется сразу, embeds доставляются через `MESSAGE_UPDATE` event

---

## Упоминания (Mentions)

### Форматы

| Формат | Тип | Regex |
|--------|-----|-------|
| `<@user_id>` | User mention | `<@(\d{1,20})>` |
| `<@&role_id>` | Role mention | `<@&(\d{1,20})>` |
| `<#channel_id>` | Channel mention | `<#(\d{1,20})>` |
| `@everyone` | Everyone | литерал `@everyone` |
| `@here` | Here (только онлайн) | литерал `@here` |

### Парсинг и сохранение

При создании сообщения:
1. Парсим контент regex-ом → извлекаем ID
2. Валидируем: существует ли user/role/channel (через NATS request к соответствующим сервисам)
3. Сохраняем в `messages.mention_ids[]`, `messages.mention_role_ids[]`, `messages.mention_everyone`
4. Записываем в `message_mentions` таблицу (для быстрого поиска непрочитанных)

### Allowed Mentions

Клиент может ограничить какие упоминания реально пингуют:

```json
{
    "allowed_mentions": {
        "parse": ["users", "roles"],
        "users": ["123456789"],
        "roles": ["987654321"],
        "replied_user": true
    }
}
```

- `parse` — массив типов для автопарсинга: `users`, `roles`, `everyone`
- `users` / `roles` — явный whitelist ID (max 100 каждый)
- `replied_user` — пинговать ли автора при reply
- Если `parse` и `users`/`roles` указаны одновременно — явный список имеет приоритет

---

## Пагинация (cursor-based)

Используем Snowflake ID как натуральный курсор — ID монотонно возрастают и уже индексированы.

### Параметры запроса

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `before` | snowflake | — | Сообщения с ID < before |
| `after` | snowflake | — | Сообщения с ID > after |
| `around` | snowflake | — | Сообщения вокруг ID |
| `limit` | integer | 50 | 1–100 |

`before`, `after`, `around` — **взаимоисключающие** (только один параметр за раз).

### SQL запросы

```sql
-- before (по умолчанию, новейшие сообщения):
SELECT * FROM messages
WHERE channel_id = $1 AND id < $2 AND deleted_at IS NULL
ORDER BY id DESC
LIMIT $3;

-- after (более новые):
SELECT * FROM messages
WHERE channel_id = $1 AND id > $2 AND deleted_at IS NULL
ORDER BY id ASC
LIMIT $3;

-- around (вокруг указанного):
(SELECT * FROM messages WHERE channel_id = $1 AND id >= $2 AND deleted_at IS NULL ORDER BY id ASC LIMIT $3)
UNION ALL
(SELECT * FROM messages WHERE channel_id = $1 AND id < $2 AND deleted_at IS NULL ORDER BY id DESC LIMIT $3)
ORDER BY id DESC;

-- без курсора (последние сообщения канала):
SELECT * FROM messages
WHERE channel_id = $1 AND deleted_at IS NULL
ORDER BY id DESC
LIMIT $2;
```

Все запросы используют индекс `idx_messages_channel_id (channel_id, id DESC) WHERE deleted_at IS NULL` — Index Scan, без Seq Scan.

---

## Реакции

### Лимиты

| Ограничение | Лимит |
|-------------|-------|
| Уникальных реакций на сообщение | 20 (Unicode + custom emoji суммарно) |
| Пользователей на реакцию (GET) | 100 (default 25) |

### Бизнес-логика

1. **Добавление реакции**: проверить лимит 20 unique, проверить права `ADD_REACTIONS` + `READ_MESSAGE_HISTORY`
2. **Первая реакция**: также требуется `ADD_REACTIONS`; последующие той же emoji — только `READ_MESSAGE_HISTORY`
3. **Удаление своей**: без дополнительных прав
4. **Удаление чужой**: требуется `MANAGE_MESSAGES`
5. **Удаление всех реакций**: требуется `MANAGE_MESSAGES`
6. **Удаление всех по emoji**: требуется `MANAGE_MESSAGES`

### Хранение счётчиков

Для ответа API нужен `count` реакций — используем агрегацию:

```sql
SELECT emoji_name, emoji_id, emoji_animated, COUNT(*) as count
FROM reactions
WHERE message_id = $1
GROUP BY emoji_name, emoji_id, emoji_animated;
```

Для горячих сообщений кешируем counts в Redis:

```
HSET reactions:{message_id} {emoji_name} {count}
EXPIRE reactions:{message_id} 300
```

---

## Пины

### Лимиты

| Ограничение | Лимит |
|-------------|-------|
| Пинов на канал | 50 |

### Бизнес-логика

1. **Пин**: требуется `PIN_MESSAGES` (1 << 51) или `MANAGE_MESSAGES`
2. **Анпин**: те же права
3. При пине создаётся системное сообщение `CHANNEL_PINNED_MESSAGE` (type 6)
4. Проверка лимита: `SELECT COUNT(*) FROM channel_pins WHERE channel_id = $1`
5. Пиннеды возвращаются в обратном хронологическом порядке (последний пин — первый)

---

## Треды

Тред — это канал с `parent_id`, указывающим на текущий текстовый канал. Управление тредами через Guild Service (создание канала), но Message Service обрабатывает:

### Типы тредов

| Тип | Значение | Создаётся в |
|-----|----------|-------------|
| `PUBLIC_THREAD` | 11 | GUILD_TEXT каналы |
| `ANNOUNCEMENT_THREAD` | 10 | GUILD_ANNOUNCEMENT каналы |
| `PRIVATE_THREAD` | 12 | GUILD_TEXT каналы |

### Auto-archive

| Длительность | Минуты |
|-------------|--------|
| 1 час | 60 |
| 1 день | 1440 |
| 3 дня | 4320 |
| 1 неделя | 10080 |

**Сброс таймера**: отправка сообщения, разархивирование, изменение длительности auto-archive.

### Thread metadata

```json
{
    "archived": false,
    "auto_archive_duration": 1440,
    "archive_timestamp": "2026-02-21T12:00:00Z",
    "locked": false
}
```

### Права на треды

| Право | Описание |
|-------|----------|
| `SEND_MESSAGES_IN_THREADS` | Отправка сообщений в треды (НЕ `SEND_MESSAGES`) |
| `CREATE_PUBLIC_THREADS` | Создание публичных тредов |
| `CREATE_PRIVATE_THREADS` | Создание приватных тредов |
| `MANAGE_THREADS` | Архивирование, удаление, модерация тредов |

**Важно**: `SEND_MESSAGES` **не работает** в тредах — нужен `SEND_MESSAGES_IN_THREADS`.

---

## API Endpoints

### POST /channels/:channel_id/messages

Создание сообщения.

**Права**: `SEND_MESSAGES` (для обычного канала) или `SEND_MESSAGES_IN_THREADS` (для треда)

**Дополнительные проверки**:
- `EMBED_LINKS` — для embeds
- `ATTACH_FILES` — для вложений
- `MENTION_EVERYONE` — для @everyone / @here
- Slowmode: `rate_limit_per_user` на канале (пропускается с `MANAGE_MESSAGES` или `MANAGE_CHANNEL`)

**Request:**

```json
{
    "content": "Hello @everyone! Check out this link",
    "embeds": [
        {
            "title": "Example",
            "description": "A rich embed",
            "color": 5814783
        }
    ],
    "message_reference": {
        "message_id": "1234567890",
        "channel_id": "9876543210"
    },
    "allowed_mentions": {
        "parse": ["users"],
        "replied_user": false
    },
    "tts": false,
    "flags": 0
}
```

**Валидация:**
- `content`: max 2000 символов (если указан)
- Хотя бы одно из: `content`, `embeds`, attachments
- `embeds`: max 10, суммарно max 6000 символов
- Каждый embed: title ≤ 256, description ≤ 4096, fields ≤ 25, field.name ≤ 256, field.value ≤ 1024, footer.text ≤ 2048, author.name ≤ 256
- `content` санитизируется (`ammonia` — whitelist-based HTML sanitizer)

**Response (201):**

```json
{
    "id": "1234567891234567",
    "channel_id": "9876543210",
    "guild_id": "5555555555",
    "author": {
        "id": "1111111111",
        "username": "user",
        "avatar": "abc123"
    },
    "content": "Hello @everyone! Check out this link",
    "embeds": [],
    "attachments": [],
    "mentions": [
        { "id": "2222222222", "username": "mentioned_user" }
    ],
    "mention_roles": [],
    "mention_everyone": true,
    "reactions": [],
    "pinned": false,
    "tts": false,
    "type": 0,
    "flags": 0,
    "message_reference": {
        "message_id": "1234567890",
        "channel_id": "9876543210"
    },
    "timestamp": "2026-02-21T12:00:00.000Z",
    "edited_timestamp": null
}
```

**NATS**: публикуется `message.created` → Gateway доставляет `MESSAGE_CREATE` всем подписчикам канала.

**Embed resolve**: если в `content` найдены URL, запускается асинхронный резолв → результат доставляется через `message.updated` → `MESSAGE_UPDATE`.

---

### GET /channels/:channel_id/messages

Получение истории сообщений. Cursor-based пагинация.

**Права**: `VIEW_CHANNEL` + `READ_MESSAGE_HISTORY`

**Query Parameters:**

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `before` | snowflake | — | ID < before |
| `after` | snowflake | — | ID > after |
| `around` | snowflake | — | Вокруг ID |
| `limit` | integer | 50 | 1–100 |

**Response (200):** массив message объектов (см. POST response)

```json
[
    { "id": "...", "content": "...", ... },
    { "id": "...", "content": "...", ... }
]
```

---

### GET /channels/:channel_id/messages/:message_id

Получение одного сообщения.

**Права**: `VIEW_CHANNEL` + `READ_MESSAGE_HISTORY`

**Response (200):** message объект

---

### PATCH /channels/:channel_id/messages/:message_id

Редактирование сообщения.

**Правила:**
- Автор может редактировать: `content`, `embeds`, `flags`, `attachments`
- Другой пользователь с `MANAGE_MESSAGES` может редактировать: только `flags` (конкретно `SUPPRESS_EMBEDS`)
- `edited_timestamp` устанавливается при каждом редактировании
- История правок **не хранится** — только последняя версия и `edited_timestamp`

**Request:**

```json
{
    "content": "Updated message text",
    "embeds": [],
    "flags": 4
}
```

**Валидация**: те же правила что и POST.

**Response (200):** обновлённый message объект

**NATS**: `message.updated` → `MESSAGE_UPDATE`

---

### DELETE /channels/:channel_id/messages/:message_id

Удаление сообщения (soft delete).

**Права:**
- Своё сообщение: без дополнительных прав
- Чужое сообщение: `MANAGE_MESSAGES`
- Системные сообщения типов 1–5, 21: **нельзя удалить**

**Response:** 204 No Content

**NATS**: `message.deleted` → `MESSAGE_DELETE` (payload: `{ id, channel_id, guild_id }`)

---

### POST /channels/:channel_id/messages/bulk-delete

Массовое удаление сообщений.

**Права**: `MANAGE_MESSAGES`

**Request:**

```json
{
    "messages": ["1234567890", "1234567891", "1234567892"]
}
```

**Валидация:**
- Количество: 2–100 сообщений
- Возраст: ни одно сообщение не старше 14 дней (вычисляется из Snowflake timestamp)
- Дубликаты ID → ошибка
- Все сообщения принадлежат указанному каналу

**Response:** 204 No Content

**NATS**: `message.bulk_deleted` → `MESSAGE_DELETE_BULK` (payload: `{ ids[], channel_id, guild_id }`)

---

### PUT /channels/:channel_id/pins/:message_id

Закрепить сообщение.

**Права**: `PIN_MESSAGES` или `MANAGE_MESSAGES`

**Логика:**
1. Проверить что сообщение существует и принадлежит каналу
2. Проверить лимит: `COUNT(*) FROM channel_pins WHERE channel_id = $1` < 50
3. Вставить в `channel_pins`
4. Обновить `messages.pinned = true`
5. Создать системное сообщение type 6 (`CHANNEL_PINNED_MESSAGE`)

**Response:** 204 No Content

**NATS**: `message.pinned` → `CHANNEL_PINS_UPDATE`

---

### DELETE /channels/:channel_id/pins/:message_id

Открепить сообщение.

**Права**: `PIN_MESSAGES` или `MANAGE_MESSAGES`

**Response:** 204 No Content

**NATS**: `message.unpinned` → `CHANNEL_PINS_UPDATE`

---

### GET /channels/:channel_id/pins

Получить все закреплённые сообщения канала.

**Права**: `VIEW_CHANNEL` + `READ_MESSAGE_HISTORY`

**Response (200):** массив message объектов (отсортированы по pinned_at DESC)

---

### PUT /channels/:channel_id/messages/:message_id/reactions/:emoji/@me

Добавить свою реакцию.

**Права**: `READ_MESSAGE_HISTORY` + `ADD_REACTIONS` (для первой реакции данного emoji)

**Emoji формат в URL:**
- Unicode: URL-encoded символ (напр. `%F0%9F%94%A5` для 🔥)
- Custom: `name:id` (напр. `custom_emoji:123456789`)

**Логика:**
1. Проверить что сообщение существует
2. Если это новый emoji на сообщении — проверить лимит 20 unique
3. Если пользователь уже поставил этот emoji — no-op (идемпотентность)
4. Вставить в `reactions`

**Response:** 204 No Content

**NATS**: `message.reaction_added` → `MESSAGE_REACTION_ADD`

---

### DELETE /channels/:channel_id/messages/:message_id/reactions/:emoji/@me

Удалить свою реакцию.

**Response:** 204 No Content

**NATS**: `message.reaction_removed` → `MESSAGE_REACTION_REMOVE`

---

### DELETE /channels/:channel_id/messages/:message_id/reactions/:emoji/:user_id

Удалить реакцию другого пользователя.

**Права**: `MANAGE_MESSAGES`

**Response:** 204 No Content

---

### GET /channels/:channel_id/messages/:message_id/reactions/:emoji

Получить пользователей, поставивших реакцию.

**Права**: `VIEW_CHANNEL` + `READ_MESSAGE_HISTORY`

**Query Parameters:**

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `after` | snowflake | — | Cursor для пагинации |
| `limit` | integer | 25 | 1–100 |

**Response (200):**

```json
[
    { "id": "1111111111", "username": "user1", "avatar": "abc" },
    { "id": "2222222222", "username": "user2", "avatar": "def" }
]
```

---

### DELETE /channels/:channel_id/messages/:message_id/reactions

Удалить все реакции с сообщения.

**Права**: `MANAGE_MESSAGES`

**Response:** 204 No Content

**NATS**: `message.reactions_removed_all` → `MESSAGE_REACTION_REMOVE_ALL`

---

### DELETE /channels/:channel_id/messages/:message_id/reactions/:emoji

Удалить все реакции определённого emoji.

**Права**: `MANAGE_MESSAGES`

**Response:** 204 No Content

**NATS**: `message.reactions_removed_emoji` → `MESSAGE_REACTION_REMOVE_EMOJI`

---

### POST /channels/:channel_id/typing

> **Владелец**: Presence Service. API Gateway проксирует этот endpoint на Presence Service через NATS subject `presence.typing`.

Индикатор набора текста. См. [presence.md](presence.md) — раздел "POST /channels/:channel_id/typing".

---

## NATS Events

### Публикуемые события

| Subject | Payload | Gateway Event |
|---------|---------|---------------|
| `messages.{guild_id}.{channel_id}.created` | Полный message объект + `guild_id`, `member` (partial) | `MESSAGE_CREATE` |
| `messages.{guild_id}.{channel_id}.updated` | Частичный message объект (всегда `id` + `channel_id`) | `MESSAGE_UPDATE` |
| `messages.{guild_id}.{channel_id}.deleted` | `{ id, channel_id, guild_id }` | `MESSAGE_DELETE` |
| `messages.{guild_id}.{channel_id}.bulk_deleted` | `{ ids[], channel_id, guild_id }` | `MESSAGE_DELETE_BULK` |
| `messages.{guild_id}.{channel_id}.reaction_added` | `{ user_id, channel_id, message_id, guild_id, emoji, message_author_id }` | `MESSAGE_REACTION_ADD` |
| `messages.{guild_id}.{channel_id}.reaction_removed` | `{ user_id, channel_id, message_id, guild_id, emoji }` | `MESSAGE_REACTION_REMOVE` |
| `messages.{guild_id}.{channel_id}.reactions_removed_all` | `{ channel_id, message_id, guild_id }` | `MESSAGE_REACTION_REMOVE_ALL` |
| `messages.{guild_id}.{channel_id}.reactions_removed_emoji` | `{ channel_id, guild_id, message_id, emoji }` | `MESSAGE_REACTION_REMOVE_EMOJI` |
| `messages.{guild_id}.{channel_id}.pins_update` | `{ channel_id, guild_id, last_pin_timestamp }` | `CHANNEL_PINS_UPDATE` |
| `typing.{guild_id}.{channel_id}` | `{ channel_id, guild_id, user_id, timestamp, member }` | `TYPING_START` |

### Подписки (входящие события)

| Subject | Источник | Действие |
|---------|----------|----------|
| `guild.channel.deleted` | Guild Service | Каскадное удаление сообщений канала |
| `guild.member.banned` | Guild Service | Удаление реакций забаненного пользователя |
| `user.deleted` | User Service | Анонимизация сообщений удалённого пользователя |

---

## Permission Matrix

Полная матрица прав для всех действий:

| Действие | Требуемое право | Битовое значение |
|----------|----------------|------------------|
| Просмотр канала | `VIEW_CHANNEL` | 1 << 10 |
| Читать историю | `READ_MESSAGE_HISTORY` | 1 << 16 |
| Отправить сообщение | `SEND_MESSAGES` | 1 << 11 |
| Отправить в тред | `SEND_MESSAGES_IN_THREADS` | 1 << 38 |
| TTS | `SEND_TTS_MESSAGES` | 1 << 12 |
| Управлять сообщениями (удалять чужие, пинить) | `MANAGE_MESSAGES` | 1 << 13 |
| Вставлять ссылки с embed | `EMBED_LINKS` | 1 << 14 |
| Прикреплять файлы | `ATTACH_FILES` | 1 << 15 |
| @everyone / @here | `MENTION_EVERYONE` | 1 << 17 |
| Внешние emoji | `USE_EXTERNAL_EMOJIS` | 1 << 18 |
| Добавить реакцию | `ADD_REACTIONS` | 1 << 6 |
| Пинить сообщения | `PIN_MESSAGES` | 1 << 51 |
| Создать публичный тред | `CREATE_PUBLIC_THREADS` | 1 << 35 |
| Создать приватный тред | `CREATE_PRIVATE_THREADS` | 1 << 36 |
| Управлять тредами | `MANAGE_THREADS` | 1 << 34 |
| `ADMINISTRATOR` (обходит все проверки) | `ADMINISTRATOR` | 1 << 3 |

**Порядок проверки прав:**
1. Вычислить base permissions из ролей пользователя
2. Если `ADMINISTRATOR` — пропустить все проверки
3. Применить channel permission overwrites (@everyone → роли → user)
4. Проверить конкретное право

---

## Rate Limiting

| Endpoint | Лимит | Окно | Scope |
|----------|-------|------|-------|
| POST /channels/:id/messages | 5 запросов | 5 секунд | per channel |
| PATCH /channels/:id/messages/:id | 5 запросов | 5 секунд | per channel |
| DELETE /channels/:id/messages/:id | 5 запросов | 1 секунда | per channel |
| POST /channels/:id/messages/bulk-delete | 1 запрос | 1 секунда | per channel |
| PUT/DELETE reactions | 1 запрос | 250 мс | per channel |
| GET /channels/:id/messages | 5 запросов | 5 секунд | per channel |
| GET /channels/:id/pins | 5 запросов | 5 секунд | per channel |
| PUT/DELETE pins | 5 запросов | 5 секунд | per channel |
| POST /channels/:id/typing | 5 запросов | 5 секунд | per channel |

### Slowmode

Канальный rate limit (задаётся в настройках канала):
- Поле: `rate_limit_per_user` (0–21600 секунд = 0–6 часов)
- 0 = slowmode выключен
- Применяется только к `POST /messages`
- **Обход**: пользователи с `MANAGE_MESSAGES` или `MANAGE_CHANNEL`

**Реализация:**
```
SET slowmode:{channel_id}:{user_id} 1 EX {rate_limit_per_user}
```

Перед отправкой: `EXISTS slowmode:{channel_id}:{user_id}` → если 1, ответить `429 SLOWMODE_ACTIVE` с `Retry-After`.

### Rate limit headers

Каждый ответ включает:
```
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 1740153600.000
X-RateLimit-Reset-After: 5.000
X-RateLimit-Bucket: channel:1234567890:messages
```

---

## Лимиты

| Ограничение | Значение |
|-------------|----------|
| Длина content | 2000 символов |
| Embeds на сообщение | 10 |
| Суммарно символов в embeds | 6000 |
| Attachments на сообщение | 10 |
| Суммарный размер attachments | 25 MiB |
| Уникальных реакций на сообщение | 20 |
| Пинов на канал | 50 |
| Bulk delete: количество | 2–100 |
| Bulk delete: возраст | не старше 14 дней |
| Allowed mentions: users | max 100 ID |
| Allowed mentions: roles | max 100 ID |
| Embed title | 256 |
| Embed description | 4096 |
| Embed fields | 25 |
| Embed field name | 256 |
| Embed field value | 1024 |
| Embed footer text | 2048 |
| Embed author name | 256 |
| Attachment description (alt text) | 1024 |
| Slowmode | 0–21600 секунд |
| GET messages limit | 1–100 (default 50) |
| GET reactions limit | 1–100 (default 25) |

---

## Миграция на ScyllaDB

### Когда переходить

PostgreSQL с партиционированием работает до ~100M сообщений. При превышении:
- Запросы замедляются (даже с правильными индексами)
- Размер индексов не помещается в RAM
- VACUUM становится дорогим

### Стратегия миграции

**Двойная запись (dual-write):**
1. Новые сообщения пишутся и в PostgreSQL, и в ScyllaDB
2. Чтение переключается на ScyllaDB (за feature flag)
3. Миграция исторических данных batch-процессом
4. После верификации — отключение PostgreSQL для сообщений

### ScyllaDB schema

```cql
CREATE TABLE messages (
    channel_id  bigint,
    bucket      int,          -- static_cast<int>(snowflake_id >> 22) / BUCKET_DURATION_MS
    message_id  bigint,
    author_id   bigint,
    content     text,
    embeds      text,         -- JSON
    mention_ids set<bigint>,
    mention_role_ids set<bigint>,
    mention_everyone boolean,
    message_type smallint,
    flags       int,
    edited_at   timestamp,
    pinned      boolean,
    tts         boolean,
    reference_message_id bigint,
    reference_channel_id bigint,
    thread_id   bigint,
    deleted     boolean,

    PRIMARY KEY ((channel_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_unit': 'DAYS',
                    'compaction_window_size': 1}
  AND gc_grace_seconds = 864000;  -- 10 дней
```

### Bucket pattern

Bucket = временное окно, нарезка по Snowflake timestamp:

```rust
const BUCKET_DURATION_MS: i64 = 1000 * 60 * 60 * 24 * 10; // 10 дней
const DISCORD_EPOCH: i64 = 1420070400000;

fn snowflake_to_bucket(snowflake_id: i64) -> i32 {
    let timestamp_ms = (snowflake_id >> 22) + DISCORD_EPOCH;
    (timestamp_ms / BUCKET_DURATION_MS) as i32
}
```

**10-дневный bucket**: балансирует между размером партиции (даже самый активный канал генерирует < 100MB за 10 дней) и количеством партиций для запроса (одна-две партиции для типичного fetch).

### Cursor-based pagination с ScyllaDB

```rust
use scylla::{Session, CachingSession};
use scylla::frame::value::CqlTimestamp;

// CachingSession — автоматический кеш prepared statements
let session = CachingSession::from(session, 100);

// Fetch messages before cursor
let result = session.execute(
    "SELECT * FROM messages WHERE channel_id = ? AND bucket = ? AND message_id < ? ORDER BY message_id DESC LIMIT ?",
    (channel_id, bucket, before_id, limit),
).await?;

// Если bucket не покрыл limit — запросить предыдущий bucket
// Итеративно по bucket-ам назад, пока не набралось limit сообщений
```

### ScyllaDB Rust crate usage

```rust
use scylla::{SessionBuilder, CachingSession};
use scylla::macros::{SerializeRow, DeserializeRow};

#[derive(SerializeRow)]
struct InsertMessage {
    channel_id: i64,
    bucket: i32,
    message_id: i64,
    author_id: i64,
    content: String,
}

#[derive(DeserializeRow)]
struct MessageRow {
    channel_id: i64,
    message_id: i64,
    author_id: i64,
    content: Option<String>,
    pinned: bool,
}

// Подключение с retry и compression
let session = SessionBuilder::new()
    .known_node("scylla-1:9042")
    .known_node("scylla-2:9042")
    .known_node("scylla-3:9042")
    .compression(Some(scylla::transport::Compression::Lz4))
    .build()
    .await?;

let session = CachingSession::from(session, 1000);
```

---

## Redis кеш

### Стратегия кеширования

| Ключ | Данные | TTL | Назначение |
|------|--------|-----|------------|
| `msg:{message_id}` | JSON message | 5 мин | Горячие сообщения |
| `reactions:{message_id}` | HASH emoji→count | 5 мин | Счётчики реакций |
| `pins:{channel_id}` | LIST message_ids | 10 мин | Пиннеды канала |
| `slowmode:{channel_id}:{user_id}` | 1 | rate_limit_per_user | Slowmode трекинг |
| `typing:{channel_id}:{user_id}` | 1 | 10 сек | Typing indicators |
| `embed_cache:{url_hash}` | JSON embed | 1 час | Кеш link preview |

### Инвалидация

- **Создание/обновление/удаление сообщения**: `DEL msg:{message_id}`
- **Изменение реакций**: `DEL reactions:{message_id}`
- **Пин/анпин**: `DEL pins:{channel_id}`
- **Write-through**: пишем в БД + инвалидируем кеш (не write-behind)

---

## Контент-процессинг

### Pipeline обработки сообщения при создании

```
1. Валидация (content length, embed limits)
    ↓
2. Санитизация контента (ammonia — whitelist HTML tags)
    ↓
3. Парсинг упоминаний (regex: <@\d+>, <@&\d+>, @everyone, @here)
    ↓
4. Валидация упоминаний (NATS request → User/Guild Service)
    ↓
5. Парсинг URL-ов (linkify crate)
    ↓
6. Сохранение в БД
    ↓
7. Публикация NATS event (MESSAGE_CREATE)
    ↓
8. Асинхронный embed resolve (spawn tokio::task)
    ↓
9. Если embeds зарезолвились → UPDATE message, NATS (MESSAGE_UPDATE)
```

### Санитизация

```rust
use ammonia::Builder;

let sanitized = Builder::new()
    .tags(hashset!["b", "i", "u", "s", "code", "pre", "br", "a", "blockquote", "span"])
    .link_rel(Some("nofollow noopener noreferrer"))
    .url_schemes(hashset!["http", "https"])
    .clean(content)
    .to_string();
```

---

## Partitioning (PostgreSQL)

### Стратегия на старте

Для начального этапа (< 100M сообщений) — **range partitioning по месяцам**:

```sql
CREATE TABLE messages (
    id             BIGINT       NOT NULL,
    channel_id     BIGINT       NOT NULL,
    -- ... все поля ...
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
) PARTITION BY RANGE (created_at);

CREATE TABLE messages_y2026m01 PARTITION OF messages
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE messages_y2026m02 PARTITION OF messages
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE messages_y2026m03 PARTITION OF messages
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
-- автоматизировать через cron job или pg_partman
```

**Преимущества:**
- Partition pruning: запросы к конкретному периоду сканируют только нужные партиции
- DROP/DETACH старых партиций вместо DELETE (мгновенно, без VACUUM)
- Индексы на каждой партиции меньше → быстрее B-tree lookup

**Автоматизация**: `pg_partman` extension или cron-job создающий партиции на 3 месяца вперёд.

**Важно**: PRIMARY KEY и UNIQUE INDEX должны включать partition key:
```sql
-- Нельзя: PRIMARY KEY (id) — на партиционированной таблице
-- Можно: PRIMARY KEY (id, created_at)
-- Или: уникальный индекс на каждой партиции отдельно
```

---

## Анонимизация (GDPR)

При удалении пользователя (`user.deleted` event):

```sql
UPDATE messages SET
    author_id = 0,     -- системный "Deleted User"
    content = NULL
WHERE author_id = $1;

-- Реакции удаляем полностью
DELETE FROM reactions WHERE user_id = $1;

-- Упоминания удаляем
DELETE FROM message_mentions WHERE user_id = $1;
```

Сообщение остаётся (для целостности тредов/ответов), но автор анонимизирован и контент удалён.

---

## Cron Jobs

| Задача | Расписание | Описание |
|--------|-----------|----------|
| Создание новых партиций | Ежедневно | Создать партиции на 3 месяца вперёд |
| Очистка soft-deleted | Еженедельно | `DELETE FROM messages WHERE deleted_at < now() - INTERVAL '30 days'` |
| Архивация тредов | Каждые 5 минут | Проверка auto_archive_duration, архивация неактивных тредов |
| Очистка typing keys | Не нужно | TTL 10 сек в Redis, автоочистка |
| VACUUM ANALYZE | Еженедельно | На каждой партиции отдельно (не на всей таблице) |
| Инвалидация embed кеша | Не нужно | TTL 1 час в Redis |

---

## Мониторинг

### Ключевые метрики (Prometheus)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `messages_created_total` | counter | Всего созданных сообщений |
| `messages_deleted_total` | counter | Всего удалённых |
| `messages_edited_total` | counter | Всего отредактированных |
| `reactions_added_total` | counter | Всего добавленных реакций |
| `messages_request_duration_seconds` | histogram | Время ответа endpoint-ов |
| `messages_content_length` | histogram | Распределение длины контента |
| `embeds_resolved_total` | counter | Успешно зарезолвленных embeds |
| `embeds_resolve_errors_total` | counter | Ошибки embed resolve |
| `slowmode_rejections_total` | counter | Отклонённых slowmode-ом |
| `bulk_delete_messages_total` | counter | Сообщений удалено через bulk delete |
| `db_query_duration_seconds` | histogram | Время запросов к БД |
| `cache_hit_ratio` | gauge | Cache hit/miss ratio |
| `nats_publish_duration_seconds` | histogram | Время публикации NATS events |

### Алерты

| Условие | Severity | Описание |
|---------|----------|----------|
| `messages_request_duration_seconds{p99} > 500ms` | warning | Медленные ответы |
| `messages_request_duration_seconds{p99} > 2s` | critical | Критическая деградация |
| `embeds_resolve_errors_total rate > 10/min` | warning | Массовые ошибки embed resolve |
| `db_query_duration_seconds{p99} > 200ms` | warning | Медленные запросы к БД |
| `cache_hit_ratio < 0.7` | warning | Низкий cache hit rate |

---

## Безопасность

### Чеклист

- [ ] Все endpoints требуют JWT аутентификацию
- [ ] Проверка прав через `permissions` crate перед каждым действием
- [ ] `ADMINISTRATOR` обходит все проверки прав
- [ ] Guild owner обходит все проверки прав в своей гильдии
- [ ] Content санитизируется `ammonia` (whitelist-based)
- [ ] SQL запросы только через `sqlx::query!` с параметрами `$1, $2`
- [ ] Embed resolver: SSRF protection (приватные IP, DNS rebinding)
- [ ] Embed resolver: таймаут 5 сек, max 1 MiB response
- [ ] Rate limiting на всех endpoints
- [ ] Slowmode enforcement (Redis-based)
- [ ] Bulk delete: проверка возраста (14 дней) и количества (2–100)
- [ ] Soft delete: данные не возвращаются клиенту после удаления
- [ ] Удалённые сообщения недоступны через GET
- [ ] Ошибки не раскрывают внутреннюю структуру БД
- [ ] Логирование: операции модерации (bulk delete, manage messages) записываются в аудит-лог
- [ ] GDPR: анонимизация при удалении пользователя
- [ ] Snowflake ID как строки в JSON ответах (предотвращение integer overflow в JavaScript)
- [ ] Валидация всех входных данных (`validator` crate)
- [ ] Allowed mentions: клиент контролирует кого пингуют
- [ ] Attachment URL — presigned, не прямой доступ к storage
