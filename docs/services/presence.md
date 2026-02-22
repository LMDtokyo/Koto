# Presence Service

Управление онлайн-статусом пользователей. Статусы, активности, typing indicators, client status per platform.

Порт: `3010`
Путь: `services/presence/`
Хранилище: Redis (primary — stateless сервис)

## Источники

- [Discord Developer Docs — Gateway Presence Update](https://docs.discord.com/developers/events/gateway-events#presence-update)
- [Discord Developer Docs — Activity Object](https://docs.discord.com/developers/events/gateway-events#activity-object)
- [Discord Developer Docs — Client Status](https://docs.discord.com/developers/events/gateway-events#client-status-object)
- [Discord Developer Docs — Update Presence (op:3)](https://docs.discord.com/developers/events/gateway-events#update-presence)
- [Redis Documentation — Strings](https://redis.io/docs/latest/develop/data-types/strings/)
- [crate: redis](https://docs.rs/redis/latest/redis/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Структура сервиса

```
services/presence/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   └── presence.rs
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── status.rs          # Status updates
│   │   ├── activity.rs        # Activity updates
│   │   └── typing.rs          # Typing indicators
│   ├── models/
│   │   ├── mod.rs
│   │   ├── presence.rs
│   │   └── activity.rs
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   ├── events/
│   │   ├── publisher.rs
│   │   └── subscriber.rs
│   └── idle_detector.rs       # Idle timeout management
├── tests/
│   ├── common/mod.rs
│   ├── presence_test.rs
│   └── typing_test.rs
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "presence-service"
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

# Кеш / хранилище
redis = { workspace = true }
deadpool-redis = "0.18"

# Брокер событий
async-nats = { workspace = true }

# Аутентификация
jsonwebtoken = { workspace = true }

# Время
chrono = { workspace = true }

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
cache = { path = "../../crates/cache" }
nats-events = { path = "../../crates/nats-events" }
rate-limit = { path = "../../crates/rate-limit" }
```

---

## Конфигурация (config.rs)

```rust
pub struct PresenceConfig {
    // Сервер
    pub host: String,                    // PRESENCE_HOST=0.0.0.0
    pub port: u16,                       // PRESENCE_PORT=3010

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=...

    // Таймауты
    pub idle_timeout_secs: u64,          // IDLE_TIMEOUT_SECS=300 (5 мин)
    pub offline_timeout_secs: u64,       // OFFLINE_TIMEOUT_SECS=60
    pub typing_ttl_secs: u64,            // TYPING_TTL_SECS=10
    pub presence_ttl_secs: u64,          // PRESENCE_TTL_SECS=120
}
```

---

## Статусы

| Значение | Имя | Описание |
|----------|-----|----------|
| `online` | Online | Активен |
| `idle` | Idle | Бездействует (auto: 5 мин без активности) |
| `dnd` | Do Not Disturb | Не беспокоить (подавляет push уведомления) |
| `invisible` | Invisible | Выглядит как offline, но online |
| `offline` | Offline | Не подключён |

### Приоритет

Если пользователь подключён с нескольких устройств, итоговый статус = "лучший":

```
online > idle > dnd > offline
```

Пример: desktop=idle, mobile=online → итоговый = online.

---

## Client Status

Per-platform статус:

```json
{
    "desktop": "online",
    "mobile": "idle",
    "web": "offline"
}
```

Значения: `online`, `idle`, `dnd`, или отсутствует (= offline на этой платформе).

---

## Activity

### Activity Types

| Значение | Имя | Формат отображения |
|----------|-----|--------------------|
| 0 | Playing | "Playing {name}" |
| 1 | Streaming | "Streaming {details}" |
| 2 | Listening | "Listening to {name}" |
| 3 | Watching | "Watching {name}" |
| 4 | Custom | "{emoji} {state}" |
| 5 | Competing | "Competing in {name}" |

### Activity Object

```json
{
    "name": "Visual Studio Code",
    "type": 0,
    "url": null,
    "details": "Editing main.rs",
    "state": "Workspace: kotov",
    "timestamps": {
        "start": 1740153600000,
        "end": null
    },
    "assets": {
        "large_image": "vscode",
        "large_text": "Visual Studio Code",
        "small_image": "rust",
        "small_text": "Editing Rust"
    },
    "created_at": 1740153600000
}
```

### Лимиты

| Ограничение | Значение |
|-------------|----------|
| Activities на пользователя | max 5 |
| Activity name | max 128 символов |
| Activity state | max 128 символов |
| Activity details | max 128 символов |
| Custom status text | max 128 символов |

---

## Redis Schema

### Presence

```
# Статус пользователя
SET presence:{user_id}:status "online" EX 120

# Client status (per platform)
HSET presence:{user_id}:clients desktop "online"
HSET presence:{user_id}:clients mobile "idle"
HSET presence:{user_id}:clients web "offline"
EXPIRE presence:{user_id}:clients 120

# Activities
SET presence:{user_id}:activities [JSON array] EX 120

# Гильдии пользователя (для fan-out при presence update)
SADD presence:{user_id}:guilds {guild_id_1} {guild_id_2}
```

### Typing Indicators

```
SET typing:{channel_id}:{user_id} 1 EX 10
```

### Heartbeat refresh

Gateway шлёт heartbeat → Presence Service обновляет TTL:

```
EXPIRE presence:{user_id}:status 120
EXPIRE presence:{user_id}:clients 120
EXPIRE presence:{user_id}:activities 120
```

Если TTL истекает (нет heartbeat > 120 сек) → пользователь считается offline.

---

## Idle Detection

```rust
// При каждом heartbeat от Gateway:
fn handle_heartbeat(user_id: i64, last_activity: Instant) {
    let idle_threshold = Duration::from_secs(300); // 5 минут

    if last_activity.elapsed() > idle_threshold {
        // Если текущий статус = online → автоматически переключить на idle
        if current_status == "online" {
            set_status(user_id, "idle");
            publish_presence_update(user_id);
        }
    }
}
```

**Важно**: idle — автоматический. Если пользователь выставил DND — он остаётся DND, не переключается на idle.

---

## API Endpoints

### PATCH /presence

Обновить свой статус (вызывается через Gateway op:3 PRESENCE_UPDATE).

**Request:**

```json
{
    "status": "online",
    "activities": [
        {
            "name": "Visual Studio Code",
            "type": 0
        }
    ],
    "since": null,
    "afk": false
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `status` | string | `online`, `idle`, `dnd`, `invisible` |
| `activities` | Activity[] | Массив активностей (max 5) |
| `since` | integer? | Unix timestamp (ms), когда стал idle |
| `afk` | boolean | Пользователь AFK |

**Response:** 204 No Content

**NATS**: `presence.{user_id}.updated` → `PRESENCE_UPDATE` всем гильдиям пользователя

---

### GET /presence/:user_id

Получить presence пользователя.

**Response (200):**

```json
{
    "user_id": "123456789",
    "status": "online",
    "client_status": {
        "desktop": "online",
        "mobile": null,
        "web": null
    },
    "activities": [
        {
            "name": "Visual Studio Code",
            "type": 0,
            "details": "Editing main.rs",
            "state": "Workspace: kotov",
            "timestamps": { "start": 1740153600000 },
            "created_at": 1740153600000
        }
    ]
}
```

**Для invisible пользователей**: возвращается `"status": "offline"` (invisible скрыт от других).

---

### GET /presence/guild/:guild_id

Получить presence всех online участников гильдии.

**Response (200):** массив presence объектов

---

### POST /channels/:channel_id/typing

Typing indicator. API Gateway проксирует сюда через NATS subject `presence.typing`.

**Права**: `SEND_MESSAGES` (или `SEND_MESSAGES_IN_THREADS` для треда)

**Логика:**
1. Проверить SEND_MESSAGES permission на канале
2. `SET typing:{channel_id}:{user_id} 1 EX 10`
3. Publish NATS event

**Response:** 204 No Content

**NATS**: `typing.{guild_id}.{channel_id}` → `TYPING_START` (payload: `{ channel_id, guild_id, user_id, timestamp, member }`)

---

### GET /channels/:channel_id/typing

Получить кто сейчас печатает.

**Response (200):**

```json
{
    "typing": [
        { "user_id": "111222333", "timestamp": 1740153600 },
        { "user_id": "444555666", "timestamp": 1740153605 }
    ]
}
```

---

## NATS Events

### Публикуемые

| Subject | Payload | Gateway Event |
|---------|---------|---------------|
| `presence.{user_id}.updated` | Presence объект | `PRESENCE_UPDATE` |
| `typing.{guild_id}.{channel_id}` | `{ channel_id, guild_id, user_id, timestamp, member }` | `TYPING_START` |

### Подписки

| Subject | Источник | Действие |
|---------|----------|----------|
| `gateway.heartbeat.{user_id}` | Gateway | Refresh TTL, idle detection |
| `gateway.connected.{user_id}` | Gateway | Set online |
| `gateway.disconnected.{user_id}` | Gateway | Start offline timer |
| `user.deleted` | User Service | Очистить все presence данные |
| `guild.member.joined` | Guild Service | Добавить guild в presence:guilds |
| `guild.member.left` | Guild Service | Убрать guild из presence:guilds |

---

## Fan-Out Presence Updates

При изменении presence:

1. Получить все гильдии пользователя: `SMEMBERS presence:{user_id}:guilds`
2. Для каждой гильдии: опубликовать в NATS `guild.{guild_id}.presence.updated`
3. Gateway фильтрует по intent `GUILD_PRESENCES` (privileged)

**Оптимизация**: для пользователей с большим кол-вом гильдий — batch publish.

---

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| PATCH /presence | 5 запросов | 60 секунд |
| POST /channels/:channel_id/typing | 5 запросов | 5 секунд |
| GET /presence/:user_id | 10 запросов | 10 секунд |
| GET /presence/guild/:guild_id | 5 запросов | 10 секунд |

---

## Мониторинг

| Метрика | Тип | Описание |
|---------|-----|----------|
| `presence_online_users` | gauge | Онлайн пользователей |
| `presence_by_status{status}` | gauge | Пользователей по статусу |
| `presence_updates_total` | counter | Обновлений статуса |
| `typing_active_total` | gauge | Активных typing indicators |
| `presence_heartbeat_refresh_total` | counter | Heartbeat refreshes |
| `presence_idle_transitions_total` | counter | Автопереходов в idle |
| `presence_ttl_expirations_total` | counter | TTL expirations (→ offline) |

---

## Безопасность

### Чеклист

- [ ] Invisible отображается как offline для других пользователей
- [ ] DND подавляет push уведомления (координация с Notification Service)
- [ ] Typing indicator: проверять SEND_MESSAGES / SEND_MESSAGES_IN_THREADS
- [ ] Activity content валидируется (max 128 символов, sanitization)
- [ ] Presence данные в Redis с TTL (не бесконечное хранение)
- [ ] Rate limiting на все endpoints
- [ ] Не логировать activity details (может содержать приватное)
- [ ] GUILD_PRESENCES — privileged intent (фильтрация на Gateway)
