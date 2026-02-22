# Moderation Service

Автомодерация, репорты, anti-raid, content filtering, аудит модерационных действий.

Порт: `3009`
Путь: `services/moderation/`
БД: PostgreSQL
Кеш: Redis

## Источники

- [Discord Developer Docs — Auto Moderation](https://docs.discord.com/developers/resources/auto-moderation)
- [Discord Developer Docs — Audit Log Resource](https://docs.discord.com/developers/resources/audit-log)
- [Discord Developer Docs — Permissions](https://docs.discord.com/developers/topics/permissions)
- [Discord Developer Docs — Guild Resource (Member)](https://docs.discord.com/developers/resources/guild#guild-member-object)
- [Discord Support — Membership Screening](https://support.discord.com/hc/en-us/articles/1500000466882-Rules-Screening-FAQ)
- [OWASP Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)
- [crate: regex](https://docs.rs/regex/latest/regex/)
- [crate: sqlx](https://docs.rs/sqlx/latest/sqlx/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Структура сервиса

```
services/moderation/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── automod.rs         # AutoMod rules CRUD
│   │   ├── reports.rs         # User reports
│   │   └── audit_log.rs       # Audit log queries
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── automod.rs
│   │   ├── reports.rs
│   │   ├── anti_raid.rs       # Raid detection
│   │   └── audit_log.rs
│   ├── models/
│   │   ├── mod.rs
│   │   ├── automod_rule.rs
│   │   ├── report.rs
│   │   ├── audit_entry.rs
│   │   └── timeout.rs
│   ├── engine/
│   │   ├── mod.rs
│   │   ├── keyword_filter.rs  # Keyword matching engine
│   │   ├── spam_detector.rs   # Spam/mention spam detection
│   │   └── raid_detector.rs   # Join rate anomaly detection
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   └── events/
│       ├── publisher.rs
│       └── subscriber.rs
├── tests/
│   ├── common/mod.rs
│   ├── automod_test.rs
│   ├── keyword_filter_test.rs
│   └── raid_detector_test.rs
├── migrations/
│   ├── 001_create_automod_rules.sql
│   ├── 002_create_reports.sql
│   ├── 003_create_mod_audit_log.sql
│   └── 004_create_timeouts.sql
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "moderation-service"
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

# Regex (keyword matching)
regex = "1"

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
permissions = { path = "../../crates/permissions" }
db = { path = "../../crates/db" }
cache = { path = "../../crates/cache" }
nats-events = { path = "../../crates/nats-events" }
rate-limit = { path = "../../crates/rate-limit" }
```

---

## Конфигурация (config.rs)

```rust
pub struct ModerationConfig {
    // Сервер
    pub host: String,                    // MODERATION_HOST=0.0.0.0
    pub port: u16,                       // MODERATION_PORT=3009

    // PostgreSQL
    pub database_url: String,            // DATABASE_URL=postgres://...
    pub db_max_connections: u32,         // DB_MAX_CONNECTIONS=10

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=...

    // Anti-raid
    pub raid_join_threshold: u32,        // RAID_JOIN_THRESHOLD=10 (joins per window)
    pub raid_join_window_secs: u64,      // RAID_JOIN_WINDOW_SECS=10
    pub raid_min_account_age_secs: u64,  // RAID_MIN_ACCOUNT_AGE_SECS=86400 (1 day)

    // Audit log
    pub audit_log_retention_days: u32,   // AUDIT_LOG_RETENTION_DAYS=90
}
```

---

## Формат ошибок

| Код | HTTP | Описание |
|-----|------|----------|
| `RULE_NOT_FOUND` | 404 | AutoMod rule не найдено |
| `REPORT_NOT_FOUND` | 404 | Report не найден |
| `MAX_RULES_EXCEEDED` | 400 | Превышен лимит правил (6 per type) |
| `INVALID_REGEX` | 400 | Невалидный regex в правиле |
| `MISSING_PERMISSIONS` | 403 | Нет прав |
| `TARGET_IS_OWNER` | 403 | Нельзя модерировать owner-а |
| `TARGET_HIGHER_ROLE` | 403 | Цель имеет более высокую роль |
| `RATE_LIMITED` | 429 | Превышен rate limit |

---

## Миграции

### 001_create_automod_rules.sql

```sql
CREATE TABLE automod_rules (
    id               BIGINT       PRIMARY KEY,  -- Snowflake ID
    guild_id         BIGINT       NOT NULL,
    name             TEXT         NOT NULL,
    event_type       SMALLINT     NOT NULL,     -- 1=MESSAGE_SEND
    trigger_type     SMALLINT     NOT NULL,     -- 1=KEYWORD, 3=SPAM, 5=MENTION_SPAM, 6=MEMBER_PROFILE
    trigger_metadata JSONB        NOT NULL DEFAULT '{}',
    actions          JSONB        NOT NULL DEFAULT '[]',
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    exempt_roles     BIGINT[]     DEFAULT '{}',
    exempt_channels  BIGINT[]     DEFAULT '{}',
    creator_id       BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_automod_rules_guild ON automod_rules (guild_id) WHERE enabled = true;
```

### 002_create_reports.sql

```sql
CREATE TABLE reports (
    id               BIGINT       PRIMARY KEY,  -- Snowflake ID
    guild_id         BIGINT       NOT NULL,
    reporter_id      BIGINT       NOT NULL,
    target_user_id   BIGINT,                    -- кого репортят
    target_message_id BIGINT,                   -- какое сообщение (опционально)
    channel_id       BIGINT,
    reason           TEXT         NOT NULL,      -- max 1000
    status           SMALLINT     NOT NULL DEFAULT 0,  -- 0=OPEN, 1=REVIEWED, 2=RESOLVED, 3=DISMISSED
    resolved_by      BIGINT,
    resolved_at      TIMESTAMPTZ,
    notes            TEXT,                       -- заметки модератора
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_reports_guild ON reports (guild_id, status, created_at DESC);
CREATE INDEX idx_reports_target ON reports (target_user_id, created_at DESC);
```

### 003_create_mod_audit_log.sql

> **Связь с Guild Service**: `mod_audit_log` хранит лог модераторских действий (automod, reports, timeouts, anti-raid). Guild Service хранит отдельный `audit_log` для административных действий (каналы, роли, участники). В UI оба лога отображаются как единый "Audit Log" — агрегация через API Gateway.

```sql
CREATE TABLE mod_audit_log (
    id               BIGINT       PRIMARY KEY,  -- Snowflake ID
    guild_id         BIGINT       NOT NULL,
    action_type      SMALLINT     NOT NULL,
    moderator_id     BIGINT       NOT NULL,     -- кто выполнил
    target_id        BIGINT,                    -- кого/что
    reason           TEXT,
    changes          JSONB,                     -- что изменилось
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_mod_audit_guild ON mod_audit_log (guild_id, created_at DESC);
CREATE INDEX idx_mod_audit_moderator ON mod_audit_log (moderator_id, created_at DESC);
CREATE INDEX idx_mod_audit_target ON mod_audit_log (target_id, created_at DESC);
CREATE INDEX idx_mod_audit_type ON mod_audit_log (guild_id, action_type, created_at DESC);
```

### 004_create_timeouts.sql

```sql
CREATE TABLE timeouts (
    guild_id         BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    moderator_id     BIGINT       NOT NULL,
    reason           TEXT,
    expires_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (guild_id, user_id)
);

CREATE INDEX idx_timeouts_expires ON timeouts (expires_at) WHERE expires_at > now();
```

---

## AutoMod

### Trigger Types

| Значение | Имя | Описание |
|----------|-----|----------|
| 1 | `KEYWORD` | Проверка по ключевым словам |
| 3 | `SPAM` | Автоматическое обнаружение спама |
| 5 | `MENTION_SPAM` | Превышение лимита упоминаний |
| 6 | `MEMBER_PROFILE` | Проверка профиля участника (username, display_name, bio) |

### Event Types

| Значение | Имя | Описание |
|----------|-----|----------|
| 1 | `MESSAGE_SEND` | При отправке сообщения |
| 2 | `MEMBER_UPDATE` | При обновлении профиля участника |

### Trigger Metadata

**KEYWORD (type 1):**

```json
{
    "keyword_filter": ["badword", "spam*", "*scam*"],
    "regex_patterns": ["\\b(buy|sell)\\s+crypto\\b"],
    "allow_list": ["classic", "class"]
}
```

**Keyword matching rules:**
- `word` — exact word match (word boundaries)
- `word*` — prefix match (начинается с)
- `*word` — suffix match (заканчивается на)
- `*word*` — substring match (содержит)

**Лимиты:**
- Max 1000 keywords per rule
- Max keyword length: 60 символов
- Max 10 regex patterns per rule
- Max 100 allow_list entries
- Max 6 rules per trigger type per guild

**MENTION_SPAM (type 5):**

```json
{
    "mention_total_limit": 5
}
```

Если сообщение содержит больше N unique mentions → триггер.

### Actions

```json
[
    {
        "type": 1,
        "metadata": {}
    },
    {
        "type": 2,
        "metadata": {
            "channel_id": "123456789"
        }
    },
    {
        "type": 3,
        "metadata": {
            "duration_seconds": 60
        }
    }
]
```

| Значение | Имя | Описание |
|----------|-----|----------|
| 1 | `BLOCK_MESSAGE` | Заблокировать сообщение |
| 2 | `SEND_ALERT_MESSAGE` | Отправить alert в канал |
| 3 | `TIMEOUT` | Таймаут пользователя (max 2419200 сек = 28 дней) |
| 4 | `BLOCK_MEMBER_INTERACTION` | Заблокировать взаимодействие (DM, реакции и т.д.) |

### Exempt roles и channels

```json
{
    "exempt_roles": ["role_id_1", "role_id_2"],
    "exempt_channels": ["channel_id_1"]
}
```

Пользователи с exempt role или сообщения в exempt channel не проверяются AutoMod. `ADMINISTRATOR` всегда exempt.

---

## AutoMod Engine

### Message Processing Pipeline

```
Message Service: NATS "messages.*.*.created"
    ↓
Moderation Service: subscriber
    ↓
Получить все enabled AutoMod rules для guild_id
    ↓
Для каждого rule:
    ├── Проверить exempt: author roles ∩ exempt_roles → skip
    ├── Проверить exempt: channel_id ∈ exempt_channels → skip
    ├── Keyword filter: match(content, keywords + regex)
    ├── Mention spam: count unique mentions > limit
    └── Spam: ML/heuristic detection
    ↓
Если match:
    ├── Action BLOCK_MESSAGE → NATS request к Message Service → delete
    ├── Action SEND_ALERT → NATS request к Message Service → create alert msg
    ├── Action TIMEOUT → NATS request к Guild Service → timeout member
    └── Создать системное сообщение type 24 (AUTO_MODERATION_ACTION)
    ↓
Запись в mod_audit_log
```

### Keyword Filter Implementation

```rust
fn check_keyword_filter(content: &str, metadata: &KeywordMetadata) -> bool {
    let content_lower = content.to_lowercase();

    // Allow list — если найдено, пропустить
    for allowed in &metadata.allow_list {
        // Remove allowed words before checking
    }

    // Keyword matching
    for keyword in &metadata.keyword_filter {
        let keyword_lower = keyword.to_lowercase();

        let matched = if keyword_lower.starts_with('*') && keyword_lower.ends_with('*') {
            // *word* — substring
            let word = &keyword_lower[1..keyword_lower.len()-1];
            content_lower.contains(word)
        } else if keyword_lower.ends_with('*') {
            // word* — prefix
            let word = &keyword_lower[..keyword_lower.len()-1];
            content_lower.split_whitespace().any(|w| w.starts_with(word))
        } else if keyword_lower.starts_with('*') {
            // *word — suffix
            let word = &keyword_lower[1..];
            content_lower.split_whitespace().any(|w| w.ends_with(word))
        } else {
            // exact word — word boundaries
            content_lower.split_whitespace().any(|w| w == keyword_lower)
        };

        if matched { return true; }
    }

    // Regex patterns
    for pattern in &metadata.regex_patterns {
        if let Ok(re) = regex::Regex::new(pattern) {
            if re.is_match(&content_lower) { return true; }
        }
    }

    false
}
```

**Кеш**: скомпилированные regex кешируются в памяти (per guild). Инвалидация при изменении rule.

---

## Anti-Raid Detection

### Алгоритм

```rust
struct RaidDetector {
    join_window: Duration,       // 10 секунд
    join_threshold: u32,         // 10 joins
    min_account_age: Duration,   // 24 часа
}

impl RaidDetector {
    async fn check_join(&self, guild_id: i64, user_id: i64, account_created_at: DateTime) -> RaidAction {
        // 1. Инкрементировать join counter в Redis
        let key = format!("raid:joins:{guild_id}");
        let count: u32 = redis.incr(&key, 1).await;
        redis.expire(&key, self.join_window.as_secs()).await;

        // 2. Проверить join rate
        if count >= self.join_threshold {
            return RaidAction::EnableRaidMode;
        }

        // 3. Проверить возраст аккаунта
        let account_age = Utc::now() - account_created_at;
        if account_age < self.min_account_age {
            return RaidAction::SuspiciousAccount;
        }

        RaidAction::Allow
    }
}

enum RaidAction {
    Allow,
    SuspiciousAccount,   // → kick с уведомлением "Account too new"
    EnableRaidMode,      // → временно заблокировать joins, alert модераторов
}
```

### Raid Mode

При активации:
1. Временно заблокировать новые joins (30 мин)
2. Отправить alert в mod-канал: "Raid detected: {count} joins in {window}"
3. Включить membership screening (если не включён)
4. Логировать в mod_audit_log

### Redis

```
INCR raid:joins:{guild_id}
EXPIRE raid:joins:{guild_id} 10

SET raid:mode:{guild_id} 1 EX 1800  # 30 мин
```

---

## Member Timeout

Timeout = временный mute (не может отправлять сообщения, реагировать, подключаться к voice).

### API

```
PUT /guilds/:guild_id/members/:user_id/timeout
```

**Права**: `MODERATE_MEMBERS` (1 << 40)

**Request:**

```json
{
    "duration_seconds": 3600,
    "reason": "Spam in #general"
}
```

**Лимит**: max 2419200 секунд (28 дней).

**Иерархия ролей**: модератор может timeout только пользователей с ролью ниже своей.

**NATS**: `guild.member.timeout` → `GUILD_MEMBER_UPDATE` (communication_disabled_until field)

---

## Reports

### Статусы

| Значение | Имя | Описание |
|----------|-----|----------|
| 0 | `OPEN` | Новый, не рассмотрен |
| 1 | `REVIEWED` | Рассмотрен модератором |
| 2 | `RESOLVED` | Решён (action taken) |
| 3 | `DISMISSED` | Отклонён |

### Endpoints

**POST /guilds/:guild_id/reports** — создать report

```json
{
    "target_user_id": "123456789",
    "target_message_id": "987654321",
    "channel_id": "555666777",
    "reason": "Harassment"
}
```

**GET /guilds/:guild_id/reports** — список reports

**Права**: `MANAGE_GUILD`

Query: `?status=0&limit=25&before=snowflake_id`

**PATCH /guilds/:guild_id/reports/:report_id** — обновить статус

```json
{
    "status": 2,
    "notes": "User warned and timed out for 1 hour"
}
```

---

## Audit Log

### Action Types

| Значение | Имя | Описание |
|----------|-----|----------|
| 1 | `MEMBER_KICK` | Пользователь кикнут |
| 2 | `MEMBER_BAN` | Пользователь забанен |
| 3 | `MEMBER_UNBAN` | Пользователь разбанен |
| 4 | `MEMBER_TIMEOUT` | Timeout применён |
| 5 | `MEMBER_TIMEOUT_REMOVE` | Timeout снят |
| 10 | `MESSAGE_DELETE` | Сообщение удалено модератором |
| 11 | `MESSAGE_BULK_DELETE` | Массовое удаление |
| 12 | `MESSAGE_PIN` | Сообщение закреплено |
| 20 | `AUTOMOD_BLOCK` | AutoMod заблокировал сообщение |
| 21 | `AUTOMOD_TIMEOUT` | AutoMod применил timeout |
| 22 | `AUTOMOD_ALERT` | AutoMod отправил alert |
| 30 | `AUTOMOD_RULE_CREATE` | AutoMod rule создан |
| 31 | `AUTOMOD_RULE_UPDATE` | AutoMod rule изменён |
| 32 | `AUTOMOD_RULE_DELETE` | AutoMod rule удалён |
| 40 | `REPORT_REVIEWED` | Report рассмотрен |
| 41 | `REPORT_RESOLVED` | Report решён |
| 50 | `RAID_MODE_ENABLED` | Raid mode активирован |
| 51 | `RAID_MODE_DISABLED` | Raid mode деактивирован |

### API

**GET /guilds/:guild_id/audit-log**

**Права**: `VIEW_AUDIT_LOG` (1 << 7)

**Query Parameters:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `user_id` | snowflake | Фильтр по модератору |
| `action_type` | integer | Фильтр по типу действия |
| `before` | snowflake | Cursor (пагинация) |
| `after` | snowflake | Cursor |
| `limit` | integer | 1–100 (default 50) |

**Response (200):**

```json
{
    "entries": [
        {
            "id": "111222333",
            "guild_id": "555666777",
            "action_type": 4,
            "moderator": {
                "id": "888999000",
                "username": "moderator"
            },
            "target": {
                "id": "123456789",
                "username": "spammer"
            },
            "reason": "Spam in #general",
            "changes": {
                "communication_disabled_until": "2026-02-21T13:00:00Z"
            },
            "created_at": "2026-02-21T12:00:00Z"
        }
    ]
}
```

### Retention

Audit log записи хранятся **90 дней**. Cron job: `DELETE FROM mod_audit_log WHERE created_at < now() - INTERVAL '90 days'`.

---

## Permission Matrix

| Действие | Требуемое право | Бит |
|----------|----------------|-----|
| Kick member | `KICK_MEMBERS` | 1 << 1 |
| Ban member | `BAN_MEMBERS` | 1 << 2 |
| Timeout member | `MODERATE_MEMBERS` | 1 << 40 |
| Manage messages (delete others') | `MANAGE_MESSAGES` | 1 << 13 |
| View audit log | `VIEW_AUDIT_LOG` | 1 << 7 |
| Manage AutoMod rules | `MANAGE_GUILD` | 1 << 5 |
| Manage reports | `MANAGE_GUILD` | 1 << 5 |
| `ADMINISTRATOR` | Обходит все проверки | 1 << 3 |

### Иерархия ролей

Модератор **не может** модерировать пользователя с:
- Более высокой ролью (position > модератора)
- Ролью `ADMINISTRATOR`
- Guild owner

---

## NATS Events

### Подписки

| Subject | Источник | Действие |
|---------|----------|----------|
| `messages.*.*.created` | Message Service | AutoMod проверка |
| `guild.member.joined` | Guild Service | Anti-raid проверка, membership screening |
| `guild.member.updated` | Guild Service | AutoMod profile check (type 6) |

### Публикуемые

| Subject | Payload | Описание |
|---------|---------|----------|
| `moderation.automod.triggered` | `{ guild_id, rule_id, user_id, action, message_id }` | AutoMod сработал |
| `moderation.raid.detected` | `{ guild_id, join_count, window_secs }` | Raid обнаружен |
| `moderation.timeout.applied` | `{ guild_id, user_id, duration, moderator_id }` | Timeout применён |
| `moderation.report.created` | `{ guild_id, report_id, reporter_id }` | Новый report |

---

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /guilds/:id/automod/rules | 5 запросов | 1 минута |
| POST /guilds/:id/reports | 3 запроса | 1 минута |
| GET /guilds/:id/audit-log | 5 запросов | 5 секунд |
| PUT /guilds/:id/members/:id/timeout | 5 запросов | 5 секунд |

---

## Cron Jobs

| Задача | Расписание | Описание |
|--------|-----------|----------|
| Expire timeouts | Каждую минуту | `DELETE FROM timeouts WHERE expires_at < now()` + NATS event |
| Audit log cleanup | Ежедневно | `DELETE FROM mod_audit_log WHERE created_at < now() - INTERVAL '90 days'` |
| Stale reports | Еженедельно | Автозакрытие OPEN reports старше 30 дней |

---

## Мониторинг

| Метрика | Тип | Описание |
|---------|-----|----------|
| `automod_checks_total` | counter | Проверок AutoMod |
| `automod_triggered_total{action}` | counter | Сработавших правил по action |
| `automod_check_duration_ms` | histogram | Время проверки |
| `raid_detections_total` | counter | Обнаруженных raid-ов |
| `timeouts_active` | gauge | Активных timeouts |
| `reports_open_total` | gauge | Открытых reports |
| `audit_log_entries_total{action_type}` | counter | Записей в audit log |

---

## Безопасность

### Чеклист

- [ ] Иерархия ролей: модератор не может модерировать вышестоящих
- [ ] Guild owner не может быть timeout/kick/ban
- [ ] AutoMod regex: лимит на сложность (max 10 patterns, timeout на regex execution)
- [ ] Regex execution с таймаутом (защита от ReDoS)
- [ ] Reports: rate limited (3/мин, защита от report spam)
- [ ] Audit log: immutable (нет DELETE/UPDATE endpoints)
- [ ] Все модерационные действия логируются в audit log
- [ ] AutoMod exempt для ADMINISTRATOR
- [ ] Anti-raid: автоматическое включение с alert модераторам
- [ ] Timeout reason логируется, но не виден другим пользователям (кроме модераторов)
- [ ] SQL queries параметризованы (особенно для audit log фильтров)
- [ ] Входные regex валидируются перед сохранением (компилируемость + не пустой)
