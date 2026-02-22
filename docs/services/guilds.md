# Guild Service

Управление серверами (гильдиями), каналами, ролями, правами, участниками, приглашениями и аудит-логом.

Порт: `3003`
Путь: `services/guilds/`
БД: PostgreSQL
Кеш: Redis

## Источники

- [Discord Developer Docs — Guild Resource](https://docs.discord.com/developers/resources/guild)
- [Discord Developer Docs — Channel Resource](https://docs.discord.com/developers/resources/channel)
- [Discord Developer Docs — Permissions](https://docs.discord.com/developers/topics/permissions)
- [Discord Developer Docs — Invite Resource](https://docs.discord.com/developers/resources/invite)
- [Discord Developer Docs — Audit Log Resource](https://docs.discord.com/developers/resources/audit-log)
- [NIST RBAC — Role-Based Access Control](https://csrc.nist.gov/projects/role-based-access-control)
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [OWASP Access Control Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Access_Control_Cheat_Sheet.html)
- [crate: bitflags](https://docs.rs/bitflags/latest/bitflags/)
- [crate: validator](https://docs.rs/validator/latest/validator/)
- [crate: sqlx](https://docs.rs/sqlx/latest/sqlx/)

---

## Структура сервиса

```
services/guilds/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── guilds.rs
│   │   ├── channels.rs
│   │   ├── roles.rs
│   │   ├── members.rs
│   │   ├── invites.rs
│   │   └── bans.rs
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── guilds.rs
│   │   ├── channels.rs
│   │   ├── roles.rs
│   │   ├── members.rs
│   │   ├── invites.rs
│   │   ├── bans.rs
│   │   └── audit_log.rs
│   ├── models/
│   │   ├── mod.rs
│   │   ├── guild.rs
│   │   ├── channel.rs
│   │   ├── role.rs
│   │   ├── member.rs
│   │   ├── invite.rs
│   │   ├── ban.rs
│   │   ├── permission_overwrite.rs
│   │   └── audit_log.rs
│   ├── permissions/
│   │   ├── mod.rs
│   │   └── calculator.rs
│   ├── middleware/
│   │   └── mod.rs
│   └── events/
│       ├── mod.rs
│       ├── publisher.rs
│       └── subscriber.rs
├── migrations/
│   ├── 001_create_guilds.sql
│   ├── 002_create_channels.sql
│   ├── 003_create_roles.sql
│   ├── 004_create_guild_members.sql
│   ├── 005_create_member_roles.sql
│   ├── 006_create_permission_overwrites.sql
│   ├── 007_create_invites.sql
│   ├── 008_create_bans.sql
│   └── 009_create_audit_log.sql
├── tests/
├── Cargo.toml
└── Dockerfile
```

---

## Зависимости (Cargo.toml)

```toml
[dependencies]
axum = { workspace = true }
tokio = { workspace = true }
serde = { workspace = true }
serde_json = { workspace = true }
sqlx = { workspace = true }
redis = { workspace = true }
tracing = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }
thiserror = { workspace = true }
validator = { workspace = true }
chrono = { workspace = true }
uuid = { workspace = true }
bitflags = { workspace = true }
async-nats = { workspace = true }
rand = "0.8"                # генерация invite codes
deadpool-redis = "0.18"     # Redis connection pool
dotenvy = "0.15"
config = "0.15"

# internal crates
common = { path = "../../crates/common" }
snowflake = { path = "../../crates/snowflake" }
permissions = { path = "../../crates/permissions" }
db = { path = "../../crates/db" }
cache = { path = "../../crates/cache" }
rate-limit = { path = "../../crates/rate-limit" }
nats-events = { path = "../../crates/nats-events" }
```

---

## Конфигурация (config.rs)

| Переменная | Обязательная | Default | Описание |
|-----------|-------------|---------|----------|
| `DATABASE_URL` | да | — | PostgreSQL connection string |
| `REDIS_URL` | да | — | Redis connection string |
| `NATS_URL` | да | — | NATS server URL |
| `CDN_BASE_URL` | да | — | Базовый URL для иконок/баннеров |
| `MAX_GUILDS_PER_USER` | нет | `100` | Макс. серверов на пользователя |
| `MAX_CHANNELS_PER_GUILD` | нет | `500` | Макс. каналов на сервер |
| `MAX_ROLES_PER_GUILD` | нет | `250` | Макс. ролей на сервер |
| `MAX_MEMBERS_PER_GUILD` | нет | `500000` | Макс. участников на сервер |
| `MAX_INVITES_PER_GUILD` | нет | `1000` | Макс. инвайтов на сервер |
| `INVITE_CODE_LENGTH` | нет | `8` | Длина invite code |
| `AUDIT_LOG_RETENTION_DAYS` | нет | `90` | Хранение аудит-лога (дней) |
| `SERVICE_PORT` | нет | `3003` | Порт сервиса |
| `RUST_LOG` | нет | `info` | Уровень логирования |

---

## Формат ошибок

```json
{
    "code": "ERROR_CODE",
    "message": "Human-readable description"
}
```

| HTTP код | code | Когда |
|---------|------|-------|
| 400 | `BAD_REQUEST` | Невалидный JSON |
| 401 | `UNAUTHORIZED` | Нет JWT |
| 403 | `MISSING_PERMISSIONS` | Нет нужных прав |
| 403 | `ROLE_HIERARCHY` | Цель выше по иерархии |
| 403 | `OWNER_ONLY` | Только владелец может это сделать |
| 404 | `GUILD_NOT_FOUND` | Гильдия не найдена |
| 404 | `CHANNEL_NOT_FOUND` | Канал не найден |
| 404 | `ROLE_NOT_FOUND` | Роль не найдена |
| 404 | `MEMBER_NOT_FOUND` | Участник не найден |
| 404 | `INVITE_NOT_FOUND` | Инвайт не найден |
| 409 | `ALREADY_MEMBER` | Уже участник гильдии |
| 410 | `INVITE_EXPIRED` | Инвайт истёк или использован |
| 422 | `VALIDATION_ERROR` | Невалидные данные |
| 429 | `RATE_LIMITED` | Превышен лимит запросов |

---

## Схема базы данных

```sql
-- migrations/001_create_guilds.sql
CREATE TABLE guilds (
    id                              BIGINT PRIMARY KEY,         -- Snowflake ID
    name                            VARCHAR(100) NOT NULL,
    owner_id                        BIGINT NOT NULL,            -- FK → users (другой сервис, проверяется через NATS)
    icon_hash                       VARCHAR(64),                -- hash файла в MinIO
    splash_hash                     VARCHAR(64),                -- invite splash image
    banner_hash                     VARCHAR(64),
    description                     VARCHAR(512),
    verification_level              SMALLINT NOT NULL DEFAULT 0, -- 0=none, 1=email, 2=5min, 3=10min, 4=phone
    default_notifications           SMALLINT NOT NULL DEFAULT 0, -- 0=all, 1=mentions
    system_channel_id               BIGINT,                     -- канал для системных сообщений
    afk_channel_id                  BIGINT,                     -- AFK voice channel
    afk_timeout                     INTEGER NOT NULL DEFAULT 300, -- секунды (60, 300, 900, 1800, 3600)
    preferred_locale                VARCHAR(8) DEFAULT 'en',
    features                        BIGINT NOT NULL DEFAULT 0,  -- битовые флаги фич сервера
    premium_tier                    SMALLINT NOT NULL DEFAULT 0, -- 0-3 (boost level)
    premium_subscription_count      INTEGER NOT NULL DEFAULT 0,
    vanity_url_code                 VARCHAR(32) UNIQUE,
    member_count                    INTEGER NOT NULL DEFAULT 0,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                      TIMESTAMPTZ                         -- soft delete (7 дней до hard delete)
);

CREATE INDEX idx_guilds_owner ON guilds (owner_id);
CREATE INDEX idx_guilds_deleted ON guilds (deleted_at) WHERE deleted_at IS NOT NULL;

-- migrations/002_create_channels.sql
CREATE TYPE channel_type AS ENUM (
    'text',           -- 0: стандартный текстовый
    'voice',          -- 2: голосовой
    'category',       -- 4: категория (группа каналов)
    'announcement',   -- 5: канал объявлений
    'stage',          -- 13: stage (speaker/audience)
    'forum'           -- 15: форум (thread-only)
);

CREATE TABLE channels (
    id                  BIGINT PRIMARY KEY,             -- Snowflake ID
    guild_id            BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    type                channel_type NOT NULL,
    topic               VARCHAR(1024),
    position            INTEGER NOT NULL DEFAULT 0,
    parent_id           BIGINT REFERENCES channels(id) ON DELETE SET NULL, -- категория
    nsfw                BOOLEAN NOT NULL DEFAULT FALSE,
    rate_limit_per_user INTEGER NOT NULL DEFAULT 0,     -- slowmode (0-21600 сек)
    bitrate             INTEGER,                        -- voice: 8000-384000 bps
    user_limit          INTEGER,                        -- voice: 0=unlimited, 1-99
    last_message_id     BIGINT,                         -- для отображения непрочитанных
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_channels_guild ON channels (guild_id);
CREATE INDEX idx_channels_parent ON channels (parent_id);

-- migrations/003_create_roles.sql
CREATE TABLE roles (
    id              BIGINT PRIMARY KEY,                 -- Snowflake ID (@everyone role id = guild id)
    guild_id        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    color           INTEGER NOT NULL DEFAULT 0,         -- hex color as int (0 = no color)
    hoist           BOOLEAN NOT NULL DEFAULT FALSE,     -- показывать отдельно в списке участников
    icon_hash       VARCHAR(64),                        -- иконка роли
    unicode_emoji   VARCHAR(8),                         -- unicode emoji роли
    position        INTEGER NOT NULL DEFAULT 0,         -- 0 = @everyone (lowest)
    permissions     BIGINT NOT NULL DEFAULT 0,          -- битовая маска прав
    managed         BOOLEAN NOT NULL DEFAULT FALSE,     -- управляется интеграцией (бот)
    mentionable     BOOLEAN NOT NULL DEFAULT FALSE,
    description     VARCHAR(256),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roles_guild ON roles (guild_id);

-- migrations/004_create_guild_members.sql
CREATE TABLE guild_members (
    user_id                         BIGINT NOT NULL,    -- FK → users (другой сервис)
    guild_id                        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    nickname                        VARCHAR(32),
    joined_at                       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    premium_since                   TIMESTAMPTZ,        -- boost start
    pending                         BOOLEAN NOT NULL DEFAULT FALSE, -- membership screening
    communication_disabled_until    TIMESTAMPTZ,        -- timeout
    PRIMARY KEY (user_id, guild_id)
);

CREATE INDEX idx_guild_members_guild ON guild_members (guild_id);

-- migrations/005_create_member_roles.sql
CREATE TABLE member_roles (
    user_id     BIGINT NOT NULL,
    guild_id    BIGINT NOT NULL,
    role_id     BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, guild_id, role_id),
    FOREIGN KEY (user_id, guild_id) REFERENCES guild_members(user_id, guild_id) ON DELETE CASCADE
);

-- migrations/006_create_permission_overwrites.sql
CREATE TABLE permission_overwrites (
    channel_id      BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    target_id       BIGINT NOT NULL,                -- role_id или user_id
    target_type     SMALLINT NOT NULL,              -- 0 = role, 1 = member
    allow           BIGINT NOT NULL DEFAULT 0,      -- разрешённые права (битовая маска)
    deny            BIGINT NOT NULL DEFAULT 0,      -- запрещённые права (битовая маска)
    PRIMARY KEY (channel_id, target_id)
);

-- migrations/007_create_invites.sql
CREATE TABLE invites (
    code            VARCHAR(16) PRIMARY KEY,        -- alphanumeric
    guild_id        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id      BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    inviter_id      BIGINT NOT NULL,                -- FK → users
    max_uses        INTEGER NOT NULL DEFAULT 0,     -- 0 = unlimited
    uses            INTEGER NOT NULL DEFAULT 0,
    max_age         INTEGER NOT NULL DEFAULT 86400, -- TTL в секундах (0 = never)
    temporary       BOOLEAN NOT NULL DEFAULT FALSE, -- kick при disconnect если нет роли
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ                     -- вычисляется: created_at + max_age
);

CREATE INDEX idx_invites_guild ON invites (guild_id);
CREATE INDEX idx_invites_expires ON invites (expires_at) WHERE expires_at IS NOT NULL;

-- migrations/008_create_bans.sql
CREATE TABLE bans (
    guild_id        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL,
    reason          VARCHAR(512),
    banned_by       BIGINT NOT NULL,                -- кто забанил
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, user_id)
);

-- migrations/009_create_audit_log.sql
CREATE TYPE audit_action AS ENUM (
    -- Guild
    'guild_update',
    -- Channels
    'channel_create', 'channel_update', 'channel_delete',
    'channel_overwrite_create', 'channel_overwrite_update', 'channel_overwrite_delete',
    -- Members
    'member_kick', 'member_prune', 'member_ban_add', 'member_ban_remove',
    'member_update', 'member_role_update', 'member_timeout',
    -- Roles
    'role_create', 'role_update', 'role_delete',
    -- Invites
    'invite_create', 'invite_delete',
    -- Emoji
    'emoji_create', 'emoji_update', 'emoji_delete',
    -- Webhooks
    'webhook_create', 'webhook_update', 'webhook_delete',
    -- Messages (модерация)
    'message_delete', 'message_bulk_delete', 'message_pin', 'message_unpin'
);

CREATE TABLE audit_log (
    id              BIGINT PRIMARY KEY,             -- Snowflake ID
    guild_id        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL,                -- кто выполнил действие
    action          audit_action NOT NULL,
    target_id       BIGINT,                         -- на кого/что направлено действие
    changes         JSONB,                          -- { "key": { "old": ..., "new": ... } }
    reason          VARCHAR(512),                   -- причина (опционально)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_guild ON audit_log (guild_id, created_at DESC);
CREATE INDEX idx_audit_log_user ON audit_log (guild_id, user_id);
CREATE INDEX idx_audit_log_action ON audit_log (guild_id, action);

-- migrations/010_create_emojis.sql
CREATE TABLE emojis (
    id              BIGINT PRIMARY KEY,             -- Snowflake ID
    guild_id        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    name            VARCHAR(32) NOT NULL,
    creator_id      BIGINT NOT NULL,                -- кто загрузил
    animated        BOOLEAN NOT NULL DEFAULT FALSE,
    managed         BOOLEAN NOT NULL DEFAULT FALSE,  -- managed by integration
    available       BOOLEAN NOT NULL DEFAULT TRUE,   -- false если boost потерян
    require_colons  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_emojis_guild ON emojis (guild_id);

-- Роли, которым разрешено использовать эмодзи (whitelist)
CREATE TABLE emoji_roles (
    emoji_id    BIGINT NOT NULL REFERENCES emojis(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (emoji_id, role_id)
);
```

> **Связь с Moderation Service**: Guild Service хранит `audit_log` — лог административных действий (управление каналами, ролями, участниками, инвайтами). Moderation Service хранит отдельный `mod_audit_log` — лог модераторских действий (automod, reports, timeouts, anti-raid). Обе таблицы отображаются пользователю в едином UI "Audit Log" — API Gateway агрегирует из обоих сервисов через NATS request/reply.

---

## Система прав (Permissions)

Права хранятся как 64-битная маска (`BIGINT`). Каждый бит — отдельное право.

### Битовые флаги

```rust
use bitflags::bitflags;

bitflags! {
    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    pub struct Permissions: u64 {
        // Общие
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

        // Текстовые каналы
        const SEND_MESSAGES          = 1 << 11;
        const SEND_TTS_MESSAGES      = 1 << 12;
        const MANAGE_MESSAGES        = 1 << 13;
        const EMBED_LINKS            = 1 << 14;
        const ATTACH_FILES           = 1 << 15;
        const READ_MESSAGE_HISTORY   = 1 << 16;
        const MENTION_EVERYONE       = 1 << 17;
        const USE_EXTERNAL_EMOJIS    = 1 << 18;

        // Голосовые каналы
        const CONNECT                = 1 << 20;
        const SPEAK                  = 1 << 21;
        const MUTE_MEMBERS           = 1 << 22;
        const DEAFEN_MEMBERS         = 1 << 23;
        const MOVE_MEMBERS           = 1 << 24;

        // Управление
        const CHANGE_NICKNAME        = 1 << 26;
        const MANAGE_NICKNAMES       = 1 << 27;
        const MANAGE_ROLES           = 1 << 28;
        const MANAGE_WEBHOOKS        = 1 << 29;
        const MANAGE_GUILD_EXPRESSIONS = 1 << 30;

        // Треды
        const MANAGE_THREADS         = 1 << 34;
        const CREATE_PUBLIC_THREADS  = 1 << 35;
        const CREATE_PRIVATE_THREADS = 1 << 36;
        const SEND_MESSAGES_IN_THREADS = 1 << 38;

        // Модерация
        const MODERATE_MEMBERS       = 1 << 40;

        // Пины
        const PIN_MESSAGES           = 1 << 51;
    }
}
```

### Алгоритм вычисления прав

Источник: [Discord Permissions](https://docs.discord.com/developers/topics/permissions)

**Шаг 1: базовые права (уровень гильдии)**

```
compute_base_permissions(member, guild):
    if member.user_id == guild.owner_id:
        return ALL_PERMISSIONS          // владелец имеет все права

    everyone_role = guild.roles[@everyone]   // @everyone role id == guild id
    permissions = everyone_role.permissions

    for role in member.roles:
        permissions |= role.permissions     // OR всех ролей участника

    if permissions & ADMINISTRATOR:
        return ALL_PERMISSIONS              // админ имеет все права

    return permissions
```

**Шаг 2: права в канале (overwrites)**

```
compute_channel_permissions(base_permissions, member, channel):
    if base_permissions & ADMINISTRATOR:
        return ALL_PERMISSIONS              // админ игнорирует overwrites

    permissions = base_permissions

    // 1. @everyone overwrite (role overwrite где target_id == guild_id)
    everyone_overwrite = channel.overwrites[guild_id]
    if everyone_overwrite:
        permissions &= ~everyone_overwrite.deny
        permissions |= everyone_overwrite.allow

    // 2. Role overwrites (все роли участника, объединённые)
    deny = 0
    allow = 0
    for role in member.roles:
        overwrite = channel.overwrites[role.id]
        if overwrite:
            deny |= overwrite.deny
            allow |= overwrite.allow
    permissions &= ~deny
    permissions |= allow

    // 3. Member overwrite (наивысший приоритет)
    member_overwrite = channel.overwrites[member.user_id]
    if member_overwrite:
        permissions &= ~member_overwrite.deny
        permissions |= member_overwrite.allow

    return permissions
```

### Иерархия приоритетов

```
Владелец (всегда ALL)
  → ADMINISTRATOR (всегда ALL, игнорирует overwrites)
    → Позиция роли (higher position = больше полномочий)
      → Базовые права ролей (OR всех ролей)
        → @everyone overwrite в канале
          → Role overwrites в канале (объединённые deny/allow)
            → Member overwrite в канале (наивысший приоритет)
```

### Неявные запреты

- Запрет `VIEW_CHANNEL` → неявно запрещает все остальные права в канале
- Запрет `SEND_MESSAGES` → неявно запрещает `MENTION_EVERYONE`, `SEND_TTS_MESSAGES`, `ATTACH_FILES`, `EMBED_LINKS`
- Timeout: участник с `communication_disabled_until` в будущем теряет все права кроме `VIEW_CHANNEL` и `READ_MESSAGE_HISTORY`

---

## API Endpoints

### POST /guilds

Создание сервера.

**Request:**
```json
{
    "name": "My Server",
    "icon": null
}
```

**Валидация:**
- `name`: 2-100 символов

**Логика:**
1. Валидация полей
2. Проверить лимит серверов пользователя (макс. 100 серверов)
3. Создать запись в `guilds` (owner_id = текущий пользователь)
4. Создать роль `@everyone` (id = guild.id, position = 0, базовые права)
5. Создать системные каналы: `general` (text), `General` (voice)
6. Добавить владельца в `guild_members`
7. NATS `guild.created`

**Response 201:**
```json
{
    "id": "1234567890123456",
    "name": "My Server",
    "owner_id": "1234567890123457",
    "icon_url": null,
    "member_count": 1,
    "roles": [
        {
            "id": "1234567890123456",
            "name": "@everyone",
            "position": 0,
            "permissions": "104324673"
        }
    ],
    "channels": [
        {
            "id": "1234567890123458",
            "name": "general",
            "type": "text",
            "position": 0
        },
        {
            "id": "1234567890123459",
            "name": "General",
            "type": "voice",
            "position": 0
        }
    ],
    "created_at": "2025-01-15T10:00:00Z"
}
```

---

### GET /guilds/:guild_id

Информация о сервере.

**Response 200:**
```json
{
    "id": "1234567890123456",
    "name": "My Server",
    "owner_id": "1234567890123457",
    "icon_url": "https://cdn.example.com/icons/1234567890123456/a1b2c3d4.webp",
    "description": "A cool server",
    "verification_level": 1,
    "default_notifications": 1,
    "member_count": 150,
    "premium_tier": 1,
    "premium_subscription_count": 5,
    "features": 0,
    "created_at": "2025-01-15T10:00:00Z"
}
```

**Авторизация:** пользователь должен быть участником гильдии.

---

### PATCH /guilds/:guild_id

Обновление настроек сервера.

**Request:**
```json
{
    "name": "Updated Name",
    "description": "New description",
    "verification_level": 2,
    "default_notifications": 1,
    "afk_channel_id": "1234567890123460",
    "afk_timeout": 900,
    "system_channel_id": "1234567890123458"
}
```

**Авторизация:** `MANAGE_GUILD`

**Логика:**
1. Проверить права `MANAGE_GUILD`
2. Валидация полей
3. Обновить в БД
4. Инвалидировать кеш Redis
5. Запись в `audit_log` (action: `guild_update`, changes: старые → новые значения)
6. NATS `guild.updated`

---

### DELETE /guilds/:guild_id

Удаление сервера.

**Авторизация:** только владелец (owner_id).

**Логика:**
1. Проверить что запрос от владельца
2. Soft delete: пометить `deleted_at`, скрыть из списков
3. Через 7 дней: hard delete (CASCADE удалит каналы, роли, участников, инвайты, баны, аудит-лог)
4. NATS `guild.deleted`

---

### GET /guilds/:guild_id/channels

Список всех каналов сервера.

**Авторизация:** участник гильдии. Возвращаются только каналы, к которым пользователь имеет `VIEW_CHANNEL`.

**Response 200:**
```json
[
    {
        "id": "1234567890123458",
        "name": "general",
        "type": "text",
        "position": 0,
        "parent_id": null,
        "topic": "Welcome!",
        "nsfw": false,
        "rate_limit_per_user": 0
    },
    {
        "id": "1234567890123461",
        "name": "Voice Channels",
        "type": "category",
        "position": 1,
        "parent_id": null
    }
]
```

---

### GET /guilds/:guild_id/roles

Список всех ролей сервера.

**Авторизация:** участник гильдии.

**Response 200:**
```json
[
    {
        "id": "1234567890123456",
        "name": "@everyone",
        "color": 0,
        "hoist": false,
        "position": 0,
        "permissions": "104324673",
        "managed": false,
        "mentionable": false
    }
]
```

---

### POST /guilds/:guild_id/channels

Создание канала.

**Request:**
```json
{
    "name": "announcements",
    "type": "text",
    "parent_id": "1234567890123461",
    "topic": "Server announcements",
    "nsfw": false,
    "rate_limit_per_user": 0,
    "position": 5,
    "permission_overwrites": [
        {
            "target_id": "1234567890123456",
            "target_type": 0,
            "deny": "2048",
            "allow": "0"
        }
    ]
}
```

**Валидация:**
- `name`: 1-100 символов; для text/voice: lowercase, a-z, 0-9, `-`, `_`
- `type`: один из допустимых типов
- `parent_id`: должен быть каналом типа `category` в этой гильдии
- `position`: >= 0
- `rate_limit_per_user`: 0-21600
- `bitrate` (voice): 8000-384000
- `user_limit` (voice): 0-99
- Макс. 500 каналов на гильдию, макс. 50 каналов в категории

**Авторизация:** `MANAGE_CHANNELS`

**Логика:**
1. Проверить права `MANAGE_CHANNELS`
2. Валидация полей и лимитов
3. Создать канал в БД
4. Если есть `permission_overwrites` — создать записи в `permission_overwrites`
5. Запись в `audit_log` (action: `channel_create`)
6. NATS `guild.channel.created`

**Response 201:**
```json
{
    "id": "1234567890123462",
    "guild_id": "1234567890123456",
    "name": "announcements",
    "type": "text",
    "parent_id": "1234567890123461",
    "topic": "Server announcements",
    "nsfw": false,
    "rate_limit_per_user": 0,
    "position": 5,
    "permission_overwrites": [
        {
            "target_id": "1234567890123456",
            "target_type": 0,
            "deny": "2048",
            "allow": "0"
        }
    ],
    "created_at": "2025-01-15T10:00:00Z"
}
```

---

### PATCH /guilds/:guild_id/channels/:channel_id

Обновление канала.

**Авторизация:** `MANAGE_CHANNELS`

**Логика:**
1. Проверить права
2. Валидация полей
3. Обновить в БД
4. Инвалидировать кеш
5. Запись в `audit_log` (action: `channel_update`, changes)
6. NATS `guild.channel.updated`

---

### DELETE /guilds/:guild_id/channels/:channel_id

Удаление канала.

**Авторизация:** `MANAGE_CHANNELS`

**Логика:**
1. Проверить права
2. Нельзя удалить последний канал в гильдии
3. Удалить канал (CASCADE: permission_overwrites, invites)
4. Сообщения остаются в Message Service (очистка по NATS)
5. Запись в `audit_log` (action: `channel_delete`)
6. NATS `guild.channel.deleted`

---

### PATCH /guilds/:guild_id/channels/positions

Batch-обновление позиций каналов.

**Request:**
```json
[
    { "id": "1234567890123458", "position": 0, "parent_id": null },
    { "id": "1234567890123462", "position": 1, "parent_id": "1234567890123461" }
]
```

**Авторизация:** `MANAGE_CHANNELS`

---

### PUT /guilds/:guild_id/channels/:channel_id/overwrites/:target_id

Создание/обновление permission overwrite.

**Request:**
```json
{
    "target_type": 0,
    "allow": "1024",
    "deny": "2048"
}
```

**Авторизация:** `MANAGE_ROLES`

**Логика:**
1. Проверить права `MANAGE_ROLES`
2. Пользователь может выставлять только те права, которые есть у него самого
3. UPSERT в `permission_overwrites`
4. Запись в `audit_log`
5. Инвалидировать кеш прав
6. NATS `guild.channel.overwrite_updated`

---

### DELETE /guilds/:guild_id/channels/:channel_id/overwrites/:target_id

Удаление permission overwrite.

**Авторизация:** `MANAGE_ROLES`

---

### POST /guilds/:guild_id/roles

Создание роли.

**Request:**
```json
{
    "name": "Moderator",
    "color": 3447003,
    "hoist": true,
    "permissions": "1099511627775",
    "mentionable": true
}
```

**Валидация:**
- `name`: 1-100 символов
- `color`: 0-16777215 (0xFFFFFF)
- `permissions`: только те права, которые есть у создающего пользователя
- Макс. 250 ролей на гильдию

**Авторизация:** `MANAGE_ROLES`

**Логика:**
1. Проверить права
2. Новая роль создаётся с position = 1 (выше @everyone, ниже всех остальных)
3. Пользователь не может создать роль с правами, которых у него нет
4. Создать в БД
5. Запись в `audit_log` (action: `role_create`)
6. NATS `guild.role.created`

**Response 201:**
```json
{
    "id": "1234567890123463",
    "guild_id": "1234567890123456",
    "name": "Moderator",
    "color": 3447003,
    "hoist": true,
    "position": 1,
    "permissions": "1099511627775",
    "managed": false,
    "mentionable": true,
    "created_at": "2025-01-15T10:00:00Z"
}
```

---

### PATCH /guilds/:guild_id/roles/:role_id

Обновление роли.

**Авторизация:** `MANAGE_ROLES`

**Логика:**
1. Проверить права
2. Нельзя редактировать роль с позицией >= своей наивысшей роли
3. Нельзя выдавать права, которых нет у редактирующего
4. Обновить в БД
5. Инвалидировать кеш прав всех участников с этой ролью
6. Запись в `audit_log` (action: `role_update`, changes)
7. NATS `guild.role.updated`

---

### DELETE /guilds/:guild_id/roles/:role_id

Удаление роли.

**Авторизация:** `MANAGE_ROLES`

**Логика:**
1. Нельзя удалить `@everyone` (id == guild_id)
2. Нельзя удалить `managed` роль
3. Нельзя удалить роль с позицией >= своей
4. Удалить из БД (CASCADE: member_roles, permission_overwrites)
5. Инвалидировать кеш
6. Запись в `audit_log` (action: `role_delete`)
7. NATS `guild.role.deleted`

---

### PATCH /guilds/:guild_id/roles/positions

Batch-обновление позиций ролей.

**Request:**
```json
[
    { "id": "1234567890123463", "position": 3 },
    { "id": "1234567890123464", "position": 2 }
]
```

**Авторизация:** `MANAGE_ROLES`

**Логика:**
- Нельзя переместить роль выше своей наивысшей
- `@everyone` всегда position = 0

---

### GET /guilds/:guild_id/members

Список участников.

**Query параметры:**
- `limit`: 1-1000 (default: 100)
- `after`: Snowflake ID (cursor-based пагинация)

**Авторизация:** участник гильдии.

**Response 200:**
```json
[
    {
        "user": {
            "id": "1234567890123457",
            "username": "john",
            "display_name": "John",
            "avatar_url": "..."
        },
        "nickname": "Johnny",
        "roles": ["1234567890123463"],
        "joined_at": "2025-01-15T10:00:00Z",
        "premium_since": null,
        "pending": false,
        "communication_disabled_until": null
    }
]
```

---

### GET /guilds/:guild_id/members/:user_id

Конкретный участник.

---

### PATCH /guilds/:guild_id/members/:user_id

Обновление участника (nickname, roles, timeout).

**Request (смена ника):**
```json
{
    "nickname": "New Nick"
}
```

**Request (добавление роли):**
```json
{
    "roles": ["1234567890123463", "1234567890123464"]
}
```

**Request (timeout):**
```json
{
    "communication_disabled_until": "2025-01-16T10:00:00Z"
}
```

**Авторизация:**
- Смена своего ника: любой участник
- Смена чужого ника: `MANAGE_NICKNAMES`
- Изменение ролей: `MANAGE_ROLES` + роль ниже своей наивысшей
- Timeout: `MODERATE_MEMBERS` + цель ниже по иерархии

**Логика:**
1. Проверить права и иерархию ролей
2. Обновить в БД
3. Если роли изменились — инвалидировать кеш прав
4. Запись в `audit_log`
5. NATS `guild.member.updated`

---

### DELETE /guilds/:guild_id/members/:user_id

Кик участника.

**Авторизация:** `KICK_MEMBERS` + цель ниже по иерархии ролей.

**Логика:**
1. Проверить права и иерархию
2. Нельзя кикнуть владельца
3. Удалить из `guild_members` (CASCADE: member_roles)
4. Обновить `member_count`
5. Запись в `audit_log` (action: `member_kick`)
6. NATS `guild.member.removed`

---

### DELETE /guilds/:guild_id/members/@me

Покинуть сервер.

**Логика:**
1. Владелец не может покинуть (сначала передать ownership)
2. Удалить из `guild_members`
3. Обновить `member_count`
4. NATS `guild.member.removed`

---

### PUT /guilds/:guild_id/bans/:user_id

Забанить участника.

**Request:**
```json
{
    "reason": "Spam",
    "delete_message_days": 7
}
```

**Валидация:**
- `reason`: до 512 символов
- `delete_message_days`: 0-7

**Авторизация:** `BAN_MEMBERS` + цель ниже по иерархии.

**Логика:**
1. Проверить права и иерархию
2. Нельзя банить владельца
3. Создать запись в `bans`
4. Удалить из `guild_members`
5. Обновить `member_count`
6. Если `delete_message_days > 0` → NATS `guild.ban.messages_delete` → Message Service
7. Запись в `audit_log` (action: `member_ban_add`)
8. NATS `guild.member.banned`

---

### DELETE /guilds/:guild_id/bans/:user_id

Разбанить.

**Авторизация:** `BAN_MEMBERS`

**Логика:**
1. Удалить из `bans`
2. Запись в `audit_log` (action: `member_ban_remove`)
3. NATS `guild.member.unbanned`

---

### GET /guilds/:guild_id/bans

Список банов.

**Авторизация:** `BAN_MEMBERS`

**Response 200:**
```json
[
    {
        "user": {
            "id": "1234567890123465",
            "username": "spammer"
        },
        "reason": "Spam",
        "created_at": "2025-01-15T10:00:00Z"
    }
]
```

---

### POST /guilds/:guild_id/invites

Создание приглашения.

**Request:**
```json
{
    "channel_id": "1234567890123458",
    "max_age": 86400,
    "max_uses": 10,
    "temporary": false
}
```

**Валидация:**
- `max_age`: 0 (never), 1800, 3600, 21600, 43200, 86400, 604800
- `max_uses`: 0 (unlimited), 1, 5, 10, 25, 50, 100
- Макс. 1000 инвайтов на гильдию

**Авторизация:** `CREATE_INSTANT_INVITE`

**Логика:**
1. Проверить права
2. Сгенерировать уникальный код (8 символов, alphanumeric)
3. Вычислить `expires_at` если `max_age > 0`
4. Создать в БД
5. Запись в `audit_log` (action: `invite_create`)
6. NATS `guild.invite.created`

**Response 201:**
```json
{
    "code": "aBcDeFgH",
    "guild_id": "1234567890123456",
    "channel_id": "1234567890123458",
    "inviter": {
        "id": "1234567890123457",
        "username": "john"
    },
    "max_uses": 10,
    "uses": 0,
    "max_age": 86400,
    "temporary": false,
    "expires_at": "2025-01-16T10:00:00Z",
    "created_at": "2025-01-15T10:00:00Z"
}
```

---

### GET /invites/:code

Информация об инвайте (публичный endpoint).

**Response 200:**
```json
{
    "code": "aBcDeFgH",
    "guild": {
        "id": "1234567890123456",
        "name": "My Server",
        "icon_url": "...",
        "member_count": 150
    },
    "channel": {
        "id": "1234567890123458",
        "name": "general",
        "type": "text"
    },
    "expires_at": "2025-01-16T10:00:00Z"
}
```

---

### POST /invites/:code/accept

Принять приглашение (вступить на сервер).

**Логика:**
1. Проверить что инвайт не истёк (`expires_at`)
2. Проверить `max_uses` (если uses >= max_uses и max_uses > 0 → 410 Gone)
3. Проверить что пользователь не забанен
4. Проверить `verification_level` гильдии vs аккаунт пользователя
5. Проверить лимит серверов пользователя (макс. 100)
6. Добавить в `guild_members` (pending = true если membership screening включен)
7. Инкрементировать `uses` инвайта
8. Обновить `member_count`
9. NATS `guild.member.joined`

**Response 200:**
```json
{
    "guild_id": "1234567890123456",
    "user_id": "1234567890123466"
}
```

---

### DELETE /guilds/:guild_id/invites/:code

Отзыв приглашения.

**Авторизация:** `MANAGE_GUILD`

---

### GET /guilds/:guild_id/invites

Список приглашений.

**Авторизация:** `MANAGE_GUILD`

---

### GET /guilds/:guild_id/audit-log

Аудит-лог.

**Query параметры:**
- `user_id`: фильтр по исполнителю
- `action_type`: фильтр по типу действия
- `before`: Snowflake ID (пагинация)
- `limit`: 1-100 (default: 50)

**Авторизация:** `VIEW_AUDIT_LOG`

**Response 200:**
```json
{
    "entries": [
        {
            "id": "1234567890123500",
            "user_id": "1234567890123457",
            "action": "member_kick",
            "target_id": "1234567890123465",
            "changes": null,
            "reason": "Breaking rules",
            "created_at": "2025-01-15T12:00:00Z"
        },
        {
            "id": "1234567890123499",
            "user_id": "1234567890123457",
            "action": "role_update",
            "target_id": "1234567890123463",
            "changes": {
                "name": { "old": "Mod", "new": "Moderator" },
                "color": { "old": 0, "new": 3447003 }
            },
            "reason": null,
            "created_at": "2025-01-15T11:30:00Z"
        }
    ]
}
```

---

### PUT /guilds/:guild_id/owner

Передача владения сервером.

**Request:**
```json
{
    "new_owner_id": "1234567890123468"
}
```

**Авторизация:** только текущий владелец.

**Логика:**
1. Проверить что `new_owner_id` является участником гильдии
2. Обновить `owner_id` в `guilds`
3. Запись в `audit_log`
4. NATS `guild.updated`

---

## Иерархия ролей — правила

Источник: [Discord Permissions](https://docs.discord.com/developers/topics/permissions)

1. Пользователь может управлять (assign/remove/edit) только ролями **ниже** своей наивысшей роли по `position`
2. Владелец сервера игнорирует все проверки иерархии
3. `managed` роли (боты) нельзя назначать/удалять вручную
4. Kick/Ban/Timeout — можно применять только к участникам, чья наивысшая роль **ниже** наивысшей роли исполнителя
5. Цвет отображения участника = цвет его наивысшей роли с `color != 0`
6. Группа в sidebar = наивысшая роль с `hoist = true`

---

## NATS события

| Событие | Payload | Подписчики |
|---------|---------|-----------|
| `guild.created` | `{ guild_id, owner_id }` | Gateway |
| `guild.updated` | `{ guild_id, fields_changed[] }` | Gateway (broadcast всем участникам) |
| `guild.deleted` | `{ guild_id }` | Все сервисы (каскадная очистка) |
| `guild.channel.created` | `{ guild_id, channel }` | Gateway, Search Service |
| `guild.channel.updated` | `{ guild_id, channel_id, fields_changed[] }` | Gateway |
| `guild.channel.deleted` | `{ guild_id, channel_id }` | Gateway, Message Service (очистка), Search Service |
| `guild.channel.overwrite_updated` | `{ guild_id, channel_id }` | Gateway |
| `guild.role.created` | `{ guild_id, role }` | Gateway |
| `guild.role.updated` | `{ guild_id, role_id, fields_changed[] }` | Gateway |
| `guild.role.deleted` | `{ guild_id, role_id }` | Gateway |
| `guild.member.joined` | `{ guild_id, user_id }` | Gateway, Notification Service, Presence Service |
| `guild.member.removed` | `{ guild_id, user_id }` | Gateway, Presence Service |
| `guild.member.updated` | `{ guild_id, user_id, fields_changed[] }` | Gateway |
| `guild.member.banned` | `{ guild_id, user_id }` | Gateway, Message Service |
| `guild.member.unbanned` | `{ guild_id, user_id }` | Gateway |
| `guild.invite.created` | `{ guild_id, code }` | Gateway |
| `guild.invite.deleted` | `{ guild_id, code }` | Gateway |
| `guild.{guild_id}.emoji.created` | `{ guild_id, emoji }` | Gateway (`GUILD_EMOJIS_UPDATE`) |
| `guild.{guild_id}.emoji.updated` | `{ guild_id, emoji }` | Gateway (`GUILD_EMOJIS_UPDATE`) |
| `guild.{guild_id}.emoji.deleted` | `{ guild_id, emoji_id }` | Gateway (`GUILD_EMOJIS_UPDATE`) |

## NATS подписки (входящие)

| Событие | Действие |
|---------|----------|
| `user.deleted` | Удалить пользователя из всех гильдий, передать ownership или удалить гильдии |
| `user.updated` | Обновить кешированные данные пользователя в контексте гильдии |

---

## Кеширование (Redis)

| Ключ | TTL | Данные |
|------|-----|--------|
| `guild:{id}` | 10 мин | Основная информация о гильдии (JSON) |
| `guild:{id}:channels` | 5 мин | Список каналов (JSON array) |
| `guild:{id}:roles` | 5 мин | Список ролей (JSON array) |
| `guild:{id}:member:{user_id}` | 5 мин | Данные участника + роли |
| `guild:{id}:member:{user_id}:perms` | 2 мин | Вычисленные базовые права (u64) |
| `guild:{id}:member_count` | 1 мин | Количество участников |
| `invite:{code}` | до expires_at | Данные инвайта |
| `guild:{id}:bans` | 5 мин | SET забаненных user_id |

**Инвалидация:**
- При обновлении гильдии → `guild:{id}`
- При создании/удалении канала → `guild:{id}:channels`
- При изменении роли → `guild:{id}:roles` + все `guild:{id}:member:*:perms` участников с этой ролью
- При изменении ролей участника → `guild:{id}:member:{user_id}`, `guild:{id}:member:{user_id}:perms`
- При бане/разбане → `guild:{id}:bans`

---

## Очистка истёкших инвайтов

Cron-задача (Tokio interval, раз в час):
```
DELETE FROM invites WHERE expires_at IS NOT NULL AND expires_at < NOW()
```

---

## Rate Limiting

| Endpoint | Лимит | Окно | Ключ |
|----------|-------|------|------|
| POST /guilds | 10 req | 1 день | user_id |
| PATCH /guilds/:id | 5 req | 1 мин | user_id + guild_id |
| POST /guilds/:id/channels | 10 req | 10 мин | user_id + guild_id |
| POST /guilds/:id/roles | 10 req | 10 мин | user_id + guild_id |
| PUT /guilds/:id/bans/:id | 30 req | 1 мин | user_id + guild_id |
| POST /guilds/:id/invites | 5 req | 1 мин | user_id |
| POST /invites/:code/accept | 5 req | 10 мин | user_id |
| GET /guilds/:id/members | 10 req | 10 сек | user_id |
| PATCH /guilds/:id/members/:id | 10 req | 1 мин | user_id |

---

## Anti-Raid защита

При вступлении на сервер (POST /invites/:code/accept):

1. **Verification level** — проверка аккаунта (email verified, возраст аккаунта, phone)
2. **Join rate detection** — если > 10 вступлений за 10 секунд, временно заблокировать приём по инвайтам и отправить NATS `guild.raid.detected` → Moderation Service
3. **Membership screening** — если включен, новые участники получают `pending = true` и не могут писать/реагировать пока не пройдут screening
4. **Temporary membership** — если инвайт `temporary = true`, участник без ролей автоматически кикается при отключении

---

## Member Prune

### POST /guilds/:guild_id/prune

Массовое удаление неактивных участников.

**Request:**
```json
{
    "days": 7,
    "include_roles": [],
    "dry_run": true
}
```

**Валидация:**
- `days`: 1-30
- `include_roles`: пустой массив = только участники без ролей; с ID = включить участников с этими ролями

**Авторизация:** `KICK_MEMBERS`

**Логика:**
1. Найти участников, не активных `days` дней (последнее сообщение / voice join)
2. Если `dry_run = true` → вернуть только count, не удалять
3. Удалить подходящих участников
4. Запись в `audit_log` (action: `member_prune`, changes: `{ "removed": count }`)
5. NATS `guild.member.pruned`

**Response 200:**
```json
{
    "pruned": 42
}
```

---

## Лимиты

| Ресурс | Лимит |
|--------|-------|
| Серверов на пользователя | 100 |
| Каналов на сервер | 500 |
| Каналов в категории | 50 |
| Ролей на сервер | 250 |
| Участников на сервер | 500 000 (начальный) |
| Инвайтов на сервер | 1 000 |
| Записей аудит-лога | хранение 90 дней |
| Длина имени сервера | 2-100 символов |
| Длина имени канала | 1-100 символов |
| Длина имени роли | 1-100 символов |
| Длина ника | 1-32 символа |
| Длина темы канала | до 1024 символов |
| Slowmode | 0-21600 секунд (6 часов) |
| AFK timeout | 60, 300, 900, 1800, 3600 секунд |
| Описание сервера | до 512 символов |
| Причина бана | до 512 символов |
| Reason в audit log | до 512 символов |
| Описание роли | до 256 символов |
| Emoji на сервер (base) | 50 static + 50 animated = 100 |
| Emoji name | 2-32 символа, alphanumeric + `_` |
| Emoji file size | макс 256 KiB |
| Emoji dimensions | 128x128 px (рекомендуемые) |
| Emoji formats | PNG, JPEG, WebP, GIF (animated) |
| Webhooks на канал | 15 |
| Webhook name | 1-80 символов |

---

## Emoji (кастомные эмодзи)

Источник: [Discord Developer Docs — Emoji Resource](https://docs.discord.com/developers/resources/emoji)

### Модель

```rust
pub struct Emoji {
    pub id: i64,                         // Snowflake ID
    pub guild_id: i64,
    pub name: String,                    // 2-32 символа
    pub creator_id: i64,                 // кто загрузил
    pub animated: bool,                  // GIF emoji
    pub managed: bool,                   // managed by integration
    pub available: bool,                 // false если boost потерян
    pub require_colons: bool,
    pub roles: Vec<i64>,                 // whitelist ролей (пустой = все могут)
    pub created_at: DateTime<Utc>,
}
```

### Лимиты по Boost Level

| Boost Level | Static | Animated | Всего |
|-------------|--------|----------|-------|
| Base (нет бустов) | 50 | 50 | 100 |
| Level 1 (2 буста) | 100 | 100 | 200 |
| Level 2 (7 бустов) | 150 | 150 | 300 |
| Level 3 (14 бустов) | 250 | 250 | 500 |

Если гильдия теряет boost level — существующие emoji за пределами лимита становятся `available: false` (не удаляются).

### API Endpoints

#### GET /guilds/:guild_id/emojis

Получить все emoji гильдии.

**Response (200):** массив Emoji объектов

---

#### GET /guilds/:guild_id/emojis/:emoji_id

Получить конкретный emoji.

**Response (200):** Emoji объект

---

#### POST /guilds/:guild_id/emojis

Создать новый emoji.

**Права**: `MANAGE_GUILD_EXPRESSIONS`

**Request (multipart/form-data):**

| Поле | Тип | Описание |
|------|-----|----------|
| `name` | string | Имя emoji (2-32 символа, alphanumeric + `_`) |
| `image` | file | PNG, JPEG, WebP, GIF — макс 256 KiB, 128x128 px |
| `roles` | i64[] | Опционально: whitelist ролей |

**Логика:**
1. Проверить `MANAGE_GUILD_EXPRESSIONS` permission
2. Проверить лимит emoji для текущего boost level
3. Валидировать имя (2-32 символа, regex `^[a-zA-Z0-9_]+$`)
4. Валидировать файл: magic bytes (PNG/JPEG/WebP/GIF), размер <= 256 KiB
5. Определить `animated` по формату (GIF)
6. Загрузить в MinIO: `emojis/{guild_id}/{emoji_id}.{ext}`
7. Создать запись в БД
8. Запись в `audit_log` (action: `emoji_create`)

**Response (201):** Emoji объект

**NATS**: `guild.{guild_id}.emoji.created` → `GUILD_EMOJIS_UPDATE`

---

#### PATCH /guilds/:guild_id/emojis/:emoji_id

Изменить emoji (имя, roles).

**Права**: `MANAGE_GUILD_EXPRESSIONS`

**Request:**

```json
{
    "name": "new_name",
    "roles": [123456789]
}
```

**Response (200):** обновлённый Emoji объект

**NATS**: `guild.{guild_id}.emoji.updated` → `GUILD_EMOJIS_UPDATE`

---

#### DELETE /guilds/:guild_id/emojis/:emoji_id

Удалить emoji.

**Права**: `MANAGE_GUILD_EXPRESSIONS`

**Response:** 204 No Content

**Логика:**
1. Удалить из БД (CASCADE удалит emoji_roles)
2. Удалить файл из MinIO
3. Запись в `audit_log` (action: `emoji_delete`)

**NATS**: `guild.{guild_id}.emoji.deleted` → `GUILD_EMOJIS_UPDATE`

### Emoji в реакциях

| Тип | Формат в API | Пример |
|-----|-------------|--------|
| Unicode (стандартный) | raw Unicode символ | `🔥` (`%F0%9F%94%A5` URL-encoded) |
| Custom | `name:id` | `pepe_laugh:123456789012345678` |

При `MESSAGE_REACTION_ADD` / `MESSAGE_REACTION_REMOVE`:
- Custom emoji: `{ "id": "123...", "name": "pepe_laugh", "animated": false }`
- Unicode emoji: `{ "id": null, "name": "🔥" }`
- Если custom emoji удалён: `name` может быть `null`

### Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /guilds/:id/emojis | 50 операций | 1 час (per guild) |
| PATCH /guilds/:id/emojis/:id | 50 операций | 1 час (per guild) |
| DELETE /guilds/:id/emojis/:id | 50 операций | 1 час (per guild) |

---

## Webhooks

Источник: [Discord Developer Docs — Webhook Resource](https://docs.discord.com/developers/resources/webhook)

### Модель

```rust
pub struct Webhook {
    pub id: i64,                         // Snowflake ID
    pub webhook_type: u8,                // 1 = Incoming, 2 = Channel Follower
    pub guild_id: i64,
    pub channel_id: i64,
    pub creator_id: i64,                 // кто создал
    pub name: Option<String>,            // 1-80 символов
    pub avatar_hash: Option<String>,
    pub token: String,                   // secure token для execution
    pub created_at: DateTime<Utc>,
}
```

### Таблица БД

```sql
-- migrations/011_create_webhooks.sql
CREATE TABLE webhooks (
    id              BIGINT PRIMARY KEY,             -- Snowflake ID
    webhook_type    SMALLINT NOT NULL DEFAULT 1,    -- 1=Incoming
    guild_id        BIGINT NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id      BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    creator_id      BIGINT NOT NULL,
    name            VARCHAR(80),
    avatar_hash     VARCHAR(255),
    token           VARCHAR(68) NOT NULL,           -- secure random token
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhooks_channel ON webhooks (channel_id);
CREATE INDEX idx_webhooks_guild ON webhooks (guild_id);
```

### Лимиты

| Ресурс | Лимит |
|--------|-------|
| Webhooks на канал | 15 |
| Webhook name | 1-80 символов, без "clyde" / "discord" |
| Webhook execution rate | 5 req / 2 сек per webhook |

### API Endpoints

#### POST /channels/:channel_id/webhooks

Создать webhook.

**Права**: `MANAGE_WEBHOOKS`

**Request:**

```json
{
    "name": "My Webhook",
    "avatar": null
}
```

**Логика:**
1. Проверить `MANAGE_WEBHOOKS` permission
2. Проверить лимит (< 15 webhooks на канал)
3. Валидировать name: 1-80 символов, без запрещённых подстрок
4. Сгенерировать secure token (68 символов, CSPRNG)
5. Генерировать Snowflake ID
6. Сохранить в БД
7. Запись в `audit_log` (action: `webhook_create`)

**Response (201):** Webhook объект (включая `token`)

**NATS**: `guild.{guild_id}.webhooks.updated` → `WEBHOOKS_UPDATE`

---

#### GET /channels/:channel_id/webhooks

Получить все webhooks канала.

**Права**: `MANAGE_WEBHOOKS`

**Response (200):** массив Webhook объектов

---

#### GET /guilds/:guild_id/webhooks

Получить все webhooks гильдии.

**Права**: `MANAGE_WEBHOOKS`

**Response (200):** массив Webhook объектов

---

#### GET /webhooks/:webhook_id

Получить webhook по ID.

**Права**: `MANAGE_WEBHOOKS`

**Response (200):** Webhook объект

---

#### PATCH /webhooks/:webhook_id

Изменить webhook (name, avatar, channel_id).

**Права**: `MANAGE_WEBHOOKS`

**Request:**

```json
{
    "name": "New Name",
    "avatar": null,
    "channel_id": "987654321"
}
```

**Response (200):** обновлённый Webhook объект

**NATS**: `guild.{guild_id}.webhooks.updated` → `WEBHOOKS_UPDATE`

---

#### DELETE /webhooks/:webhook_id

Удалить webhook.

**Права**: `MANAGE_WEBHOOKS`

**Response:** 204 No Content

**NATS**: `guild.{guild_id}.webhooks.updated` → `WEBHOOKS_UPDATE`

---

#### POST /webhooks/:webhook_id/:token

**Execute webhook** — отправить сообщение от имени webhook. Не требует авторизации (token в URL).

**Query params:**
- `wait` — если `true`, ждать подтверждения и вернуть message объект

**Request:**

```json
{
    "content": "Hello from webhook!",
    "username": "Custom Name",
    "avatar_url": "https://example.com/avatar.png",
    "embeds": [],
    "tts": false
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `content` | string | Текст сообщения (до 2000 символов) |
| `username` | string | Override имени webhook |
| `avatar_url` | string | Override аватара |
| `embeds` | Embed[] | Embedded content (макс 10) |
| `tts` | boolean | Text-to-speech |

Минимум одно из: `content`, `embeds`, `file`.

**Rate limit**: 5 req / 2 сек per webhook

**Логика:**
1. Найти webhook по ID + token
2. Валидировать payload (content length, embed limits)
3. Создать message от имени webhook (author_id = webhook.id, `webhook_id` поле в message)
4. Опубликовать `MESSAGE_CREATE` event

**Response:** `204 No Content` (или message объект если `wait=true`)

### Безопасность

- Token генерируется через CSPRNG — 68 символов
- Webhook URL = `https://api.example.com/webhooks/{id}/{token}` — знание URL = полный доступ
- При утечке токена — владелец может удалить и пересоздать webhook
- Webhook messages помечаются `webhook_id` — нельзя спутать с обычными

---

## Очистка данных (Tokio cron)

| Задача | Интервал | SQL |
|--------|---------|-----|
| Истёкшие инвайты | 1 час | `DELETE FROM invites WHERE expires_at IS NOT NULL AND expires_at < NOW()` |
| Soft-deleted гильдии | 1 день | `DELETE FROM guilds WHERE deleted_at IS NOT NULL AND deleted_at < NOW() - INTERVAL '7 days'` |
| Старые записи аудит-лога | 1 день | `DELETE FROM audit_log WHERE created_at < NOW() - INTERVAL '90 days'` |

---

## Безопасность (чеклист)

По [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html):

- [ ] Все endpoints требуют JWT (кроме GET /invites/:code — публичный, с rate limit)
- [ ] Проверка прав на КАЖДОМ запросе (server-side, через middleware)
- [ ] Deny by default — если право не выдано явно, оно запрещено (OWASP)
- [ ] Иерархия ролей проверяется при kick/ban/timeout/role assign
- [ ] Владелец не может быть кикнут/забанен
- [ ] Пользователь не может выдать права, которых нет у него самого
- [ ] Permission overwrites: нельзя установить deny/allow на права, которых нет у исполнителя
- [ ] `ADMINISTRATOR` bypass: только для вычисления прав, НЕ для пропуска rate limiting или валидации
- [ ] SQL запросы только через `sqlx::query!` с параметрами
- [ ] Входные данные валидируются (`validator` crate)
- [ ] Channel name: strip HTML, ограничение символов
- [ ] Guild name: strip HTML, ограничение длины
- [ ] Invite code: генерация через CSPRNG (`rand::thread_rng`)
- [ ] Rate limiting на создание серверов, каналов, ролей, инвайтов
- [ ] Anti-raid: детекция массовых join, автоблокировка
- [ ] Аудит-лог: все административные действия записываются
- [ ] Аудит-лог: нельзя удалить или модифицировать записи (append-only)
- [ ] Soft delete для гильдий (7 дней на восстановление)
- [ ] CASCADE: удаление гильдии удаляет все связанные данные
- [ ] Кеш permissions инвалидируется при ЛЮБОМ изменении ролей

---

## Мониторинг

| Метрика | Тип | Описание |
|---------|-----|----------|
| `guilds_total` | gauge | Общее количество гильдий |
| `guilds_created_total` | counter | Созданных гильдий |
| `guilds_deleted_total` | counter | Удалённых гильдий |
| `guilds_members_total{guild_id}` | gauge | Участников в гильдии |
| `guilds_channels_total{guild_id}` | gauge | Каналов в гильдии |
| `guilds_member_joins_total` | counter | Вступлений |
| `guilds_member_leaves_total{reason}` | counter | Уходов (leave/kick/ban) |
| `guilds_invites_used_total` | counter | Использованных инвайтов |
| `guilds_permission_checks_total` | counter | Проверок прав |
| `guilds_permission_check_duration_seconds` | histogram | Время вычисления прав |
| `guilds_cache_hit_ratio` | gauge | Процент cache hit (Redis) |
| `guilds_db_query_duration_seconds{query}` | histogram | Время SQL запросов |
| `guilds_audit_log_entries_total{action}` | counter | Записей в audit_log |
