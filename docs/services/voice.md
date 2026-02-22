# Voice Service

Голосовая и видеосвязь. Интеграция с LiveKit (SFU), управление комнатами, токены подключения, voice states, screen sharing.

Порт: `3008`
Путь: `services/voice/`
SFU: LiveKit
Кеш: Redis

## Источники

- [LiveKit Documentation](https://docs.livekit.io/)
- [LiveKit Server APIs](https://docs.livekit.io/home/server/managing-rooms/)
- [LiveKit Access Tokens](https://docs.livekit.io/home/get-started/authentication/)
- [LiveKit Rust Server SDK](https://docs.livekit.io/reference/server/server-sdk-rust/)
- [Discord Developer Docs — Voice Resource](https://docs.discord.com/developers/resources/voice)
- [Discord Developer Docs — Voice States](https://docs.discord.com/developers/events/gateway-events#voice-state-update)
- [Discord Developer Docs — Permissions](https://docs.discord.com/developers/topics/permissions)
- [crate: livekit-api](https://docs.rs/livekit-api/latest/livekit_api/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Структура сервиса

```
services/voice/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── rooms.rs           # Room management
│   │   ├── tokens.rs          # Token generation
│   │   └── voice_state.rs     # Voice state updates
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── rooms.rs
│   │   ├── tokens.rs
│   │   └── voice_state.rs
│   ├── models/
│   │   ├── mod.rs
│   │   ├── voice_state.rs
│   │   └── room.rs
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   ├── events/
│   │   ├── publisher.rs
│   │   └── subscriber.rs
│   └── livekit/
│       ├── mod.rs
│       ├── client.rs          # LiveKit API client wrapper
│       └── token.rs           # JWT token generation
├── tests/
│   ├── common/mod.rs
│   ├── rooms_test.rs
│   └── tokens_test.rs
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "voice-service"
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

# LiveKit
livekit-api = "0.4"

# Права
bitflags = { workspace = true }

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
cache = { path = "../../crates/cache" }
nats-events = { path = "../../crates/nats-events" }
rate-limit = { path = "../../crates/rate-limit" }
```

---

## Конфигурация (config.rs)

```rust
pub struct VoiceConfig {
    // Сервер
    pub host: String,                    // VOICE_HOST=0.0.0.0
    pub port: u16,                       // VOICE_PORT=3008

    // LiveKit
    pub livekit_url: String,             // LIVEKIT_URL=wss://livekit.example.com
    pub livekit_api_key: String,         // LIVEKIT_API_KEY=...
    pub livekit_api_secret: String,      // LIVEKIT_API_SECRET=...

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT (валидация)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=...

    // Лимиты
    pub max_participants_per_room: u32,  // MAX_PARTICIPANTS_PER_ROOM=99
    pub token_ttl_secs: u64,            // TOKEN_TTL_SECS=86400
}
```

---

## Формат ошибок

| Код | HTTP | Описание |
|-----|------|----------|
| `ROOM_NOT_FOUND` | 404 | Комната не найдена |
| `ROOM_FULL` | 403 | Превышен лимит участников |
| `MISSING_PERMISSIONS` | 403 | Нет прав (CONNECT, SPEAK, etc.) |
| `ALREADY_CONNECTED` | 400 | Пользователь уже в голосовом канале |
| `LIVEKIT_ERROR` | 500 | Ошибка LiveKit API |
| `RATE_LIMITED` | 429 | Превышен rate limit |

---

## Voice States

### Модель

```rust
pub struct VoiceState {
    pub guild_id: Option<i64>,
    pub channel_id: Option<i64>,     // None = disconnected
    pub user_id: i64,
    pub session_id: String,          // Gateway session ID

    // Self-state (управляет сам пользователь)
    pub self_mute: bool,
    pub self_deaf: bool,
    pub self_stream: bool,           // screen sharing / Go Live
    pub self_video: bool,            // camera on/off

    // Server-state (управляет модератор)
    pub mute: bool,                  // server muted
    pub deaf: bool,                  // server deafened
    pub suppress: bool,              // suppressed in stage channel

    pub request_to_speak_timestamp: Option<String>,  // ISO8601, для stage channels
}
```

### Хранение (Redis)

```
HSET voice_state:{guild_id}:{user_id} channel_id {channel_id}
HSET voice_state:{guild_id}:{user_id} self_mute {0|1}
HSET voice_state:{guild_id}:{user_id} self_deaf {0|1}
HSET voice_state:{guild_id}:{user_id} mute {0|1}
HSET voice_state:{guild_id}:{user_id} deaf {0|1}
HSET voice_state:{guild_id}:{user_id} self_stream {0|1}
HSET voice_state:{guild_id}:{user_id} self_video {0|1}
HSET voice_state:{guild_id}:{user_id} session_id {session_id}
EXPIRE voice_state:{guild_id}:{user_id} 86400
```

```
SADD voice_channel:{channel_id}:members {user_id}
```

---

## LiveKit Integration

### Room Management

Каждый голосовой канал = одна LiveKit room. Room создаётся при первом подключении.

```rust
use livekit_api::services::room::{CreateRoomOptions, RoomClient};

let room_client = RoomClient::new(
    &config.livekit_url,
    &config.livekit_api_key,
    &config.livekit_api_secret,
)?;

// Создать комнату
let room = room_client.create_room(
    format!("channel_{channel_id}"),
    CreateRoomOptions {
        empty_timeout: 300,           // 5 мин после последнего участника → удалить
        max_participants: 99,
        ..Default::default()
    },
).await?;

// Удалить комнату (при удалении канала)
room_client.delete_room(&room_name).await?;

// Список участников
let participants = room_client.list_participants(&room_name).await?;
```

### Token Generation

```rust
use livekit_api::access_token::{AccessToken, VideoGrants, AccessTokenOptions};

fn generate_voice_token(
    api_key: &str,
    api_secret: &str,
    user_id: &str,
    room_name: &str,
    can_publish: bool,      // SPEAK permission
    can_subscribe: bool,    // CONNECT permission
    can_publish_data: bool, // для screen sharing
) -> Result<String, Error> {
    let grants = VideoGrants {
        room_join: true,
        room: room_name.to_string(),
        can_publish,
        can_subscribe,
        can_publish_data,
        ..Default::default()
    };

    let token = AccessToken::with_api_key(api_key, api_secret)
        .with_identity(user_id)
        .with_grants(grants)
        .with_ttl(Duration::from_secs(86400))  // 24 часа
        .to_jwt()?;

    Ok(token)
}
```

### Participant Management

```rust
// Server mute участника
room_client.mute_published_track(
    &room_name,
    &participant_sid,
    &track_sid,
    true, // muted
).await?;

// Kick участника из комнаты
room_client.remove_participant(&room_name, &participant_identity).await?;

// Обновить permissions участника
room_client.update_participant(
    &room_name,
    &participant_identity,
    UpdateParticipantOptions {
        permission: Some(ParticipantPermission {
            can_publish: false,  // заглушить
            can_subscribe: true,
            ..Default::default()
        }),
        ..Default::default()
    },
).await?;
```

---

## Permission Matrix

| Действие | Требуемое право | Битовое значение |
|----------|----------------|------------------|
| Подключиться к каналу | `CONNECT` | 1 << 20 |
| Говорить | `SPEAK` | 1 << 21 |
| Видео | `STREAM` | 1 << 9 |
| Mute другого | `MUTE_MEMBERS` | 1 << 22 |
| Deafen другого | `DEAFEN_MEMBERS` | 1 << 23 |
| Переместить участника | `MOVE_MEMBERS` | 1 << 24 |
| Priority speaker | `PRIORITY_SPEAKER` | 1 << 8 |
| `ADMINISTRATOR` | Обходит все | 1 << 3 |

---

## API Endpoints

### POST /voice/token

Получить токен для подключения к голосовому каналу.

**Права**: `CONNECT` на канале

**Request:**

```json
{
    "channel_id": "123456789",
    "guild_id": "987654321"
}
```

**Логика:**
1. Проверить JWT
2. Проверить CONNECT permission на канале
3. Проверить лимит участников (SCARD voice_channel:{channel_id}:members < max)
4. Определить grants: SPEAK → can_publish, STREAM → can_publish_data
5. Создать LiveKit room (если не существует)
6. Сгенерировать LiveKit access token

**Response (200):**

```json
{
    "token": "eyJ...",
    "url": "wss://livekit.example.com",
    "room_name": "channel_123456789"
}
```

---

### PATCH /voice/state

Обновить своё voice state.

**Request:**

```json
{
    "channel_id": "123456789",
    "self_mute": true,
    "self_deaf": false,
    "self_video": false
}
```

`channel_id: null` = disconnect из голосового канала.

**Response:** 204 No Content

**NATS**: `voice.state.updated` → `VOICE_STATE_UPDATE`

---

### POST /voice/mute/:user_id

Server mute другого пользователя.

**Права**: `MUTE_MEMBERS`

**Response:** 204 No Content

---

### POST /voice/deafen/:user_id

Server deafen другого пользователя.

**Права**: `DEAFEN_MEMBERS`

**Response:** 204 No Content

---

### POST /voice/move/:user_id

Переместить пользователя в другой голосовой канал.

**Права**: `MOVE_MEMBERS`

**Request:**

```json
{
    "channel_id": "new_channel_id"
}
```

---

### POST /voice/kick/:user_id

Кикнуть из голосового канала (disconnect).

**Права**: `MOVE_MEMBERS`

---

### GET /voice/states/:guild_id

Получить все voice states гильдии.

**Response (200):**

```json
[
    {
        "user_id": "111222333",
        "channel_id": "123456789",
        "self_mute": false,
        "self_deaf": false,
        "mute": false,
        "deaf": false,
        "self_stream": false,
        "self_video": true
    }
]
```

---

## NATS Events

### Публикуемые

| Subject | Payload | Gateway Event |
|---------|---------|---------------|
| `voice.{guild_id}.state_updated` | VoiceState объект | `VOICE_STATE_UPDATE` |
| `voice.{guild_id}.server_mute` | `{ user_id, muted }` | `VOICE_STATE_UPDATE` |
| `voice.{guild_id}.server_deaf` | `{ user_id, deafened }` | `VOICE_STATE_UPDATE` |

### Подписки

| Subject | Источник | Действие |
|---------|----------|----------|
| `guild.channel.deleted` | Guild Service | Удалить LiveKit room, disconnect всех |
| `guild.member.banned` | Guild Service | Disconnect + remove из voice |
| `guild.member.left` | Guild Service | Disconnect из voice |

---

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /voice/token | 5 запросов | 10 секунд |
| PATCH /voice/state | 10 запросов | 10 секунд |
| POST /voice/mute,deafen,move,kick | 5 запросов | 5 секунд |

---

## Мониторинг

| Метрика | Тип | Описание |
|---------|-----|----------|
| `voice_active_rooms` | gauge | Активные комнаты |
| `voice_active_participants` | gauge | Участники в голосовых каналах |
| `voice_connections_total` | counter | Подключения |
| `voice_disconnections_total{reason}` | counter | Отключения по причине |
| `voice_token_generation_duration_ms` | histogram | Время генерации токена |
| `voice_livekit_errors_total` | counter | Ошибки LiveKit API |

---

## Безопасность

### Чеклист

- [ ] LiveKit API key/secret в Kubernetes Secret
- [ ] Токены подключения с ограниченным TTL (24 часа)
- [ ] Permission checks перед генерацией токена (CONNECT, SPEAK)
- [ ] LiveKit grants соответствуют Discord permissions
- [ ] Rate limiting на token generation
- [ ] Voice states хранятся в Redis с TTL (не бесконечно)
- [ ] При disconnect — cleanup voice state и LiveKit participant
- [ ] Пустые комнаты автоматически удаляются (empty_timeout: 300s)
- [ ] Server mute/deafen логируется в аудит-лог
