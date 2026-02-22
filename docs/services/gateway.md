# WebSocket Gateway

Real-time доставка событий клиентам через WebSocket. Управление соединениями, шардинг, heartbeat, сжатие, fan-out событий из NATS.

Порт: `4000`
Путь: `services/gateway/`
Кеш: Redis (session state, presence)

## Источники

- [Discord Developer Docs — Gateway](https://docs.discord.com/developers/events/gateway)
- [Discord Developer Docs — Gateway Events](https://docs.discord.com/developers/events/gateway-events)
- [Discord Developer Docs — Gateway Close Codes](https://discord-api-types.dev/api/discord-api-types-v10/enum/GatewayCloseCodes)
- [Discord Developer Docs — Gateway Intents](https://discord-api-types.dev/api/discord-api-types-v10/enum/GatewayIntentBits)
- [OWASP WebSocket Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html)
- [NATS — Subject-Based Messaging](https://docs.nats.io/nats-concepts/subjects)
- [NATS — Pub/Sub](https://docs.nats.io/nats-concepts/core-nats/pubsub)
- [NATS — Queue Groups](https://docs.nats.io/nats-concepts/core-nats/queue)
- [crate: axum — WebSocket](https://docs.rs/axum/latest/axum/extract/ws/index.html)
- [crate: tokio-tungstenite](https://github.com/snapview/tokio-tungstenite)
- [crate: flate2](https://docs.rs/flate2/latest/flate2/) — zlib-stream compression
- [crate: zstd](https://docs.rs/zstd/latest/zstd/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)
- [crate: jsonwebtoken](https://docs.rs/jsonwebtoken/latest/jsonwebtoken/)
- [crate: metrics](https://docs.rs/metrics/latest/metrics/)
- [Tokio scheduler internals](https://tokio.rs/blog/2019-10-scheduler)

---

## Структура сервиса

```
services/gateway/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   └── ws.rs              # WebSocket upgrade handler
│   ├── handler/
│   │   ├── mod.rs
│   │   ├── connection.rs      # Connection lifecycle
│   │   ├── identify.rs        # IDENTIFY / RESUME обработка
│   │   ├── heartbeat.rs       # Heartbeat loop
│   │   ├── dispatch.rs        # Event dispatch to client
│   │   └── opcodes.rs         # Opcode enum и маршрутизация
│   ├── session/
│   │   ├── mod.rs
│   │   ├── state.rs           # Per-connection state
│   │   ├── manager.rs         # Session registry (HashMap<session_id, Session>)
│   │   └── shard.rs           # Shard assignment и координация
│   ├── compression/
│   │   ├── mod.rs
│   │   ├── zlib_stream.rs     # zlib-stream (flate2)
│   │   └── zstd_stream.rs     # zstd-stream
│   ├── events/
│   │   ├── subscriber.rs      # NATS subscriber → fan-out
│   │   └── router.rs          # Event → target connections routing
│   ├── middleware/
│   │   └── rate_limit.rs
│   └── metrics.rs
├── tests/
│   ├── common/mod.rs
│   ├── connection_test.rs
│   ├── heartbeat_test.rs
│   └── dispatch_test.rs
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "gateway-service"
version = "0.1.0"
edition = "2024"

[dependencies]
# HTTP / WebSocket
axum = { workspace = true }
tokio = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }
futures = "0.3"

# Сериализация
serde = { workspace = true }
serde_json = { workspace = true }

# Сжатие
flate2 = "1"             # zlib-stream
zstd = "0.13"            # zstd-stream

# Брокер событий
async-nats = { workspace = true }

# Кеш (session state)
redis = { workspace = true }
deadpool-redis = "0.18"

# Аутентификация
jsonwebtoken = { workspace = true }

# Время / ID
chrono = { workspace = true }

# Ошибки
thiserror = { workspace = true }

# Логирование
tracing = { workspace = true }
tracing-subscriber = { workspace = true }

# Метрики
metrics = "0.24"
metrics-exporter-prometheus = "0.16"

# Конфигурация
config = "0.15"
dotenvy = "0.15"

# Утилиты
rand = "0.9"
dashmap = "6"             # concurrent HashMap для session registry
tokio-util = "0.7"

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
pub struct GatewayConfig {
    // Сервер
    pub host: String,                    // GATEWAY_HOST=0.0.0.0
    pub port: u16,                       // GATEWAY_PORT=4000

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT (только валидация)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=<ES256 public key PEM>

    // Sharding
    pub shard_id: u16,                   // SHARD_ID=0
    pub total_shards: u16,              // TOTAL_SHARDS=1
    pub max_concurrency: u16,           // MAX_CONCURRENCY=1

    // Соединения
    pub heartbeat_interval_ms: u64,     // HEARTBEAT_INTERVAL_MS=41250
    pub identify_timeout_ms: u64,       // IDENTIFY_TIMEOUT_MS=5000
    pub heartbeat_timeout_ms: u64,      // HEARTBEAT_TIMEOUT_MS=30000
    pub max_connections: usize,         // MAX_CONNECTIONS=100000
    pub max_payload_bytes: usize,       // MAX_PAYLOAD_BYTES=4096

    // Rate limiting
    pub events_per_minute: u32,         // EVENTS_PER_MINUTE=120
    pub identify_per_day: u32,          // IDENTIFY_PER_DAY=1000

    // Compression
    pub default_compression: String,    // DEFAULT_COMPRESSION=zlib-stream

    // WebSocket buffers
    pub ws_read_buffer_size: usize,     // WS_READ_BUFFER_SIZE=4096
    pub ws_write_buffer_size: usize,    // WS_WRITE_BUFFER_SIZE=4096
    pub ws_max_write_buffer_size: usize,// WS_MAX_WRITE_BUFFER_SIZE=65536
}
```

---

## Формат ошибок

Ошибки Gateway передаются через WebSocket Close codes, не через JSON body.

### Close Codes

| Код | Имя | Resumable | Описание |
|-----|-----|-----------|----------|
| 4000 | Unknown Error | Да | Неизвестная ошибка, попробовать reconnect |
| 4001 | Unknown Opcode | Да | Отправлен невалидный opcode |
| 4002 | Decode Error | Да | Невалидный payload (не JSON) |
| 4003 | Not Authenticated | Да | Payload отправлен до IDENTIFY |
| 4004 | Authentication Failed | **Нет** | Невалидный токен в IDENTIFY |
| 4005 | Already Authenticated | Да | Повторный IDENTIFY |
| 4007 | Invalid Seq | Да | Невалидный seq при RESUME |
| 4008 | Rate Limited | Да | Превышен лимит событий |
| 4009 | Session Timed Out | Да | Сессия истекла, reconnect + новый IDENTIFY |
| 4010 | Invalid Shard | **Нет** | Невалидная конфигурация шарда |
| 4011 | Sharding Required | **Нет** | Слишком много гильдий, нужен шардинг |
| 4012 | Invalid API Version | **Нет** | Невалидная версия Gateway |
| 4013 | Invalid Intents | **Нет** | Невалидный intents bitfield |
| 4014 | Disallowed Intents | **Нет** | Privileged intent не одобрен |

**Правило**: close codes 1000 и 1001 инвалидируют сессию (нельзя resume).

---

## Протокол

### Opcodes

| Opcode | Имя | Направление | Описание |
|--------|-----|-------------|----------|
| 0 | DISPATCH | Server → Client | Событие (несёт `s` sequence и `t` event name) |
| 1 | HEARTBEAT | Оба | Keepalive (клиент шлёт, сервер может запросить) |
| 2 | IDENTIFY | Client → Server | Аутентификация (JWT, intents, shard) |
| 3 | PRESENCE_UPDATE | Client → Server | Обновить статус (online/idle/dnd/offline) |
| 4 | VOICE_STATE_UPDATE | Client → Server | Подключение к голосовому каналу |
| 6 | RESUME | Client → Server | Восстановить сессию (session_id + seq) |
| 7 | RECONNECT | Server → Client | Сервер просит клиента переподключиться |
| 8 | REQUEST_GUILD_MEMBERS | Client → Server | Запросить участников гильдии |
| 9 | INVALID_SESSION | Server → Client | Сессия невалидна (`d: true` = resumable) |
| 10 | HELLO | Server → Client | Первое сообщение, содержит heartbeat_interval |
| 11 | HEARTBEAT_ACK | Server → Client | Подтверждение heartbeat |

**Примечание**: opcode 5 не используется. Пробелы в нумерации — намеренные (совместимость с Discord протоколом).

### Формат сообщений

```json
{
    "op": 0,
    "d": {},
    "s": 42,
    "t": "MESSAGE_CREATE"
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `op` | integer | Opcode |
| `d` | any | Payload данные |
| `s` | integer? | Sequence number (только для op 0 DISPATCH) |
| `t` | string? | Event name (только для op 0 DISPATCH) |

---

## Connection Lifecycle

```
Client                                          Server
  │                                                │
  │── WebSocket connect (wss://gw?v=1) ──────────>│
  │                                                │
  │<──────────── Hello (op:10) ───────────────────│
  │              {"heartbeat_interval": 41250}     │
  │                                                │
  │── Heartbeat (op:1) после jitter ─────────────>│  ← initial jitter
  │<──────────── Heartbeat ACK (op:11) ───────────│
  │                                                │
  │── Identify (op:2) {token, intents} ──────────>│  ← 5 сек таймаут
  │                                                │
  │<──────────── Ready (op:0, t:READY) ───────────│
  │              {session_id, user, guilds}         │
  │                                                │
  │<──────────── Dispatch events (op:0) ──────────│  ← s++ каждый dispatch
  │── Heartbeat (op:1) каждые interval ──────────>│
  │<──────────── Heartbeat ACK (op:11) ───────────│
  │              ...                               │
```

### Фазы

1. **WebSocket connect**: клиент подключается к `wss://gateway.example.com/?v=1&encoding=json&compress=zlib-stream`
2. **Hello (op:10)**: сервер немедленно отправляет `heartbeat_interval` в миллисекундах
3. **Initial heartbeat**: клиент отправляет первый heartbeat после `heartbeat_interval * random(0..1)` — jitter предотвращает thundering herd при массовом reconnect
4. **Identify (op:2)**: клиент отправляет JWT токен, intents, connection properties. Таймаут: **5 секунд** от Hello → если нет Identify → close 4003
5. **Ready (op:0)**: сервер отправляет `session_id`, `resume_gateway_url`, `user` объект, список гильдий
6. **Dispatch loop**: сервер отправляет события с монотонно возрастающим `s` (sequence number)

### Heartbeat

- **Interval**: из Hello payload (рекомендуется ~41250ms)
- **Payload**: `{"op": 1, "d": <last_sequence_number>}` (null если dispatch ещё не было)
- **ACK**: сервер отвечает op:11
- **Zombie detection**: если ACK не получен до следующего heartbeat — соединение мёртвое. Клиент закрывает с non-1000 кодом и reconnect-ит
- **Server-initiated**: сервер может отправить op:1 для запроса внеочередного heartbeat
- **Timeout**: 30 секунд без heartbeat от клиента → сервер disconnect

### Resume

Когда resume возможен:
- Получен op:7 (Reconnect)
- Disconnect с resumable close code (4000, 4001, 4002, 4003, 4005, 4007, 4008, 4009)
- Получен op:9 с `d: true`

**Необходимое состояние**:
- `session_id` (из Ready)
- `resume_gateway_url` (из Ready)
- `seq` (последний sequence number)

**Resume flow:**
1. Подключиться к `resume_gateway_url`
2. Получить Hello, начать heartbeat
3. Отправить Resume (op:6): `{"token": "...", "session_id": "...", "seq": 1337}`
4. Сервер переиграет пропущенные события
5. Сервер отправит Resumed dispatch event

**Failure**: op:9 с `d: false` → resume невозможен, подключиться заново + новый Identify.

### Session State

Для каждого соединения хранится:

```rust
pub struct Session {
    pub session_id: String,
    pub user_id: i64,
    pub shard_id: u16,
    pub seq: AtomicU64,                     // sequence number
    pub intents: u32,                        // intents bitfield
    pub guilds: HashSet<i64>,               // guild_ids на этом шарде
    pub compression: CompressionType,
    pub encoding: EncodingType,
    pub tx: mpsc::Sender<GatewayMessage>,   // канал для отправки клиенту
    pub connected_at: Instant,
    pub last_heartbeat: AtomicInstant,
}
```

**Session registry**: `DashMap<String, Arc<Session>>` — concurrent HashMap для быстрого lookup по session_id.

---

## Identify Payload

### Client → Server (op:2)

```json
{
    "op": 2,
    "d": {
        "token": "eyJhbGciOi...",
        "intents": 3276799,
        "properties": {
            "os": "windows",
            "browser": "chrome",
            "device": ""
        },
        "compress": false,
        "shard": [0, 1],
        "presence": {
            "status": "online",
            "activities": [],
            "since": null,
            "afk": false
        }
    }
}
```

### Server → Client: Ready (op:0, t:READY)

```json
{
    "op": 0,
    "s": 1,
    "t": "READY",
    "d": {
        "v": 1,
        "user": {
            "id": "1234567890",
            "username": "user",
            "avatar": "abc123"
        },
        "guilds": [
            {"id": "111", "unavailable": true},
            {"id": "222", "unavailable": true}
        ],
        "session_id": "abc123def456",
        "resume_gateway_url": "wss://gateway-resume.example.com",
        "shard": [0, 1]
    }
}
```

**Guilds**: при READY гильдии приходят как `unavailable: true`. Полные данные приходят через отдельные `GUILD_CREATE` dispatch events.

---

## Intents (битовое поле)

Intents контролируют какие события клиент хочет получать. Обязательны при IDENTIFY.

| Intent | Бит | Значение | Privileged | События |
|--------|-----|----------|------------|---------|
| GUILDS | 0 | 1 | Нет | GUILD_CREATE, GUILD_UPDATE, GUILD_DELETE, CHANNEL_* |
| GUILD_MEMBERS | 1 | 2 | **Да** | GUILD_MEMBER_ADD, GUILD_MEMBER_UPDATE, GUILD_MEMBER_REMOVE |
| GUILD_MODERATION | 2 | 4 | Нет | GUILD_BAN_ADD, GUILD_BAN_REMOVE, GUILD_AUDIT_LOG_ENTRY_CREATE |
| GUILD_EXPRESSIONS | 3 | 8 | Нет | GUILD_EMOJIS_UPDATE, GUILD_STICKERS_UPDATE |
| GUILD_INTEGRATIONS | 4 | 16 | Нет | GUILD_INTEGRATIONS_UPDATE |
| GUILD_WEBHOOKS | 5 | 32 | Нет | WEBHOOKS_UPDATE |
| GUILD_INVITES | 6 | 64 | Нет | INVITE_CREATE, INVITE_DELETE |
| GUILD_VOICE_STATES | 7 | 128 | Нет | VOICE_STATE_UPDATE |
| GUILD_PRESENCES | 8 | 256 | **Да** | PRESENCE_UPDATE |
| GUILD_MESSAGES | 9 | 512 | Нет | MESSAGE_CREATE, MESSAGE_UPDATE, MESSAGE_DELETE |
| GUILD_MESSAGE_REACTIONS | 10 | 1024 | Нет | MESSAGE_REACTION_* |
| GUILD_MESSAGE_TYPING | 11 | 2048 | Нет | TYPING_START |
| DIRECT_MESSAGES | 12 | 4096 | Нет | MESSAGE_CREATE (DM) |
| DIRECT_MESSAGE_REACTIONS | 13 | 8192 | Нет | MESSAGE_REACTION_* (DM) |
| DIRECT_MESSAGE_TYPING | 14 | 16384 | Нет | TYPING_START (DM) |
| MESSAGE_CONTENT | 15 | 32768 | **Да** | message.content не пустой |

**Privileged intents** (GUILD_MEMBERS, GUILD_PRESENCES, MESSAGE_CONTENT) требуют явного одобрения для ботов с > 100 гильдий.

**Фильтрация**: если клиент не указал intent, соответствующие события **не отправляются** ему. Это снижает нагрузку на Gateway.

---

## Шардинг

### Формула

```rust
fn guild_to_shard(guild_id: i64, total_shards: u16) -> u16 {
    ((guild_id >> 22) % total_shards as i64) as u16
}
```

Right-shift на 22 бита извлекает timestamp-компонент из Snowflake ID → равномерное распределение гильдий по шардам.

### Правила

- Один шард обслуживает до **2500 гильдий** (рекомендуется ~1000)
- Каждое Gateway-соединение = один шард
- Один инстанс Gateway может обслуживать **несколько шардов**
- DM события (без guild_id) маршрутизируются на **shard 0**

### Concurrent startup

Шарды запускаются группами по `max_concurrency`:

```
rate_limit_key = shard_id % max_concurrency

// max_concurrency=4, total_shards=16:
// Bucket 0: shards [0, 4, 8, 12]  — запускаются одновременно
// Bucket 1: shards [1, 5, 9, 13]  — через 5 сек
// Bucket 2: shards [2, 6, 10, 14] — через 10 сек
// Bucket 3: shards [3, 7, 11, 15] — через 15 сек
```

5 секунд между каждым bucket-ом для rate limit IDENTIFY.

### Масштабирование в Kubernetes

```
Gateway Pod 0: shard 0, 1, 2, 3
Gateway Pod 1: shard 4, 5, 6, 7
Gateway Pod 2: shard 8, 9, 10, 11
Gateway Pod 3: shard 12, 13, 14, 15
```

Координация через Redis:
- `SET gateway:shard:{shard_id}:pod {pod_name}` — какой pod обслуживает шард
- `SADD gateway:pod:{pod_name}:shards {shard_id}` — какие шарды на поде

---

## Сжатие

### Параметры подключения

URL: `wss://gateway.example.com/?v=1&encoding=json&compress=zlib-stream`

| Параметр | Значения | Описание |
|----------|----------|----------|
| `v` | `1` | Версия Gateway API |
| `encoding` | `json` | Формат сообщений |
| `compress` | `zlib-stream`, `zstd-stream` | Transport compression |

### zlib-stream (flate2 crate)

Все сообщения проходят через единый zlib-контекст на соединение. Контекст накапливает словарь, улучшая compression ratio с каждым сообщением.

```rust
use flate2::{Compress, Decompress, FlushCompress, FlushDecompress, Compression};

// Server-side: compress outgoing messages
struct ZlibEncoder {
    compress: Compress,
}

impl ZlibEncoder {
    fn new() -> Self {
        Self { compress: Compress::new(Compression::default(), true) }
    }

    fn compress(&mut self, input: &[u8]) -> Result<Vec<u8>, std::io::Error> {
        let mut output = Vec::with_capacity(input.len());
        self.compress.compress_vec(input, &mut output, FlushCompress::Sync)?;
        Ok(output)
        // Результат заканчивается на 0x00 0x00 0xFF 0xFF (Z_SYNC_FLUSH suffix)
    }
}

// Client-side: decompress incoming messages
struct ZlibDecoder {
    decompress: Decompress,
}

impl ZlibDecoder {
    fn new() -> Self {
        Self { decompress: Decompress::new(true) }
    }

    fn decompress(&mut self, input: &[u8]) -> Result<Vec<u8>, std::io::Error> {
        // Буферизовать данные пока не получим Z_SYNC_FLUSH suffix: 00 00 FF FF
        let mut output = Vec::new();
        self.decompress.decompress_vec(input, &mut output, FlushDecompress::Sync)?;
        Ok(output)
    }
}
```

**Важно**: объекты `Compress` / `Decompress` живут всё время соединения. НЕ создавать новые для каждого сообщения.

### zstd-stream (zstd crate)

```rust
use zstd::stream::write::Encoder;
use zstd::stream::read::Decoder;

// Per-connection encoder (level 3 — default, хороший баланс скорость/сжатие)
let mut encoder = Encoder::new(Vec::new(), 3)?;
```

### Compression ratio

Типичный JSON payload Discord-подобного протокола:
- **Без контекста**: 70-80% сжатие
- **С накопленным контекстом** (после ~100 сообщений): 85-92% сжатие
- zstd на ~5-10% лучше zlib при сравнимой скорости декомпрессии (360 MB/s vs 175 MB/s)

---

## NATS Fan-Out

### Subject hierarchy

```
guild.{guild_id}.>                          — все события гильдии
guild.{guild_id}.message_create             — новое сообщение
guild.{guild_id}.message_update             — сообщение обновлено
guild.{guild_id}.message_delete             — сообщение удалено
guild.{guild_id}.channel.created            — канал создан
guild.{guild_id}.member.joined              — участник вступил
guild.{guild_id}.member.left               — участник покинул
guild.{guild_id}.role.updated              — роль обновлена
guild.{guild_id}.typing                     — набор текста
guild.{guild_id}.presence.updated          — статус изменён
guild.{guild_id}.voice_state.updated       — голосовой статус

dm.{user_id}.message_create                — DM сообщение
dm.{user_id}.typing                        — DM набор текста

presence.{user_id}.updated                 — глобальное обновление присутствия
```

### Подписка

Gateway инстанс подписывается на гильдии своих шардов:

```rust
// При старте: подписка на все гильдии шарда
for guild_id in shard_guild_ids {
    let sub = client.subscribe(format!("guild.{guild_id}.>")).await?;
    // Слушать события в tokio::spawn
}

// При GUILD_CREATE (пользователь вступил в новую гильдию):
client.subscribe(format!("guild.{new_guild_id}.>")).await?;

// При GUILD_DELETE (пользователь покинул гильдию):
subscription.unsubscribe().await?;
```

### Fan-out routing

```
NATS subject: guild.123456.message_create
    ↓
Gateway получает event
    ↓
Проверяет: какие сессии подключены к guild 123456?
    ↓
Для каждой сессии:
    ├── Проверить intents (GUILD_MESSAGES включён?)
    ├── Проверить permissions (VIEW_CHANNEL?)
    └── Отправить через session.tx (mpsc channel)
         ↓
    Write task → compress → WebSocket send
```

### Queue groups (horizontal scaling)

Если несколько Gateway pods обслуживают один шард (для HA):

```rust
// Все pods одного шарда подписываются в одну queue group
client.queue_subscribe(
    format!("guild.{guild_id}.>"),
    format!("gateway-shard-{shard_id}"),
).await?;
```

NATS доставит каждое событие **только одному** инстансу в группе → предотвращение дублирования.

---

## Rate Limiting

### Per-connection

| Лимит | Значение | Действие при превышении |
|-------|----------|------------------------|
| Events от клиента | 120 за 60 секунд | Close 4008 (Rate Limited) |
| IDENTIFY per day | 1000 за 24 часа | Close 4009, email владельцу |
| Max concurrent IDENTIFY | по `max_concurrency` | op:9 Invalid Session |

### Реализация

```rust
struct ConnectionRateLimiter {
    events: SlidingWindow,  // 120 per 60s
}

impl ConnectionRateLimiter {
    fn check(&mut self) -> bool {
        self.events.try_acquire()
    }
}
```

IDENTIFY rate (per-token, per-day) отслеживается глобально в Redis:
```
INCR gateway:identify:{token_hash}:{date}
EXPIRE gateway:identify:{token_hash}:{date} 86400
```

---

## Буферы и память

### Настройка WebSocket буферов

```rust
ws.read_buffer_size(4096)          // 4 KiB (вместо default 128 KiB)
  .write_buffer_size(4096)         // 4 KiB
  .max_write_buffer_size(65536)    // 64 KiB backpressure limit
  .max_message_size(4096)          // 4 KiB max payload
  .max_frame_size(4096)            // 4 KiB max frame
```

### Память на соединение

| Компонент | Размер |
|-----------|--------|
| 2 Tokio tasks (read + write) | ~500 bytes |
| WebSocket read buffer | 4 KiB |
| WebSocket write buffer | 4 KiB |
| mpsc channel (32 slots) | ~2 KiB |
| Session state | ~1 KiB |
| Compression context (zlib) | ~256 KiB |
| **Итого (без сжатия)** | **~12 KiB** |
| **Итого (с zlib-stream)** | **~268 KiB** |

### Capacity planning

| Соединений | RAM (без сжатия) | RAM (с zlib) |
|-----------|------------------|--------------|
| 10,000 | ~120 MB | ~2.6 GB |
| 50,000 | ~600 MB | ~13 GB |
| 100,000 | ~1.2 GB | ~26 GB |

**Рекомендация**: для 100K+ соединений — zstd-stream (контекст меньше) или ограничить zlib сжатие.

---

## Concurrency Model

### Tokio tasks на соединение

```rust
async fn handle_connection(socket: WebSocket, state: Arc<AppState>) {
    let (ws_sender, ws_receiver) = socket.split();
    let (tx, rx) = tokio::sync::mpsc::channel::<GatewayMessage>(32);

    // Write task: mpsc → compress → WebSocket
    let write_task = tokio::spawn(async move {
        write_loop(ws_sender, rx, compression).await;
    });

    // Read task: WebSocket → decompress → dispatch
    let read_task = tokio::spawn(async move {
        read_loop(ws_receiver, tx.clone(), session, rate_limiter).await;
    });

    // Heartbeat task
    let heartbeat_task = tokio::spawn(async move {
        heartbeat_loop(session, heartbeat_interval).await;
    });

    // Ждём завершения любой задачи → отменяем остальные
    tokio::select! {
        _ = write_task => {},
        _ = read_task => {},
        _ = heartbeat_task => {},
    }

    // Cleanup: удалить сессию, отписаться от NATS, обновить presence
    cleanup_session(session_id, state).await;
}
```

### Write loop

```rust
async fn write_loop(
    mut sender: SplitSink<WebSocket, Message>,
    mut rx: mpsc::Receiver<GatewayMessage>,
    mut compressor: Option<ZlibEncoder>,
) {
    while let Some(msg) = rx.recv().await {
        let payload = serde_json::to_vec(&msg)?;
        let data = match &mut compressor {
            Some(c) => c.compress(&payload),
            None => payload,
        };
        if sender.send(Message::Binary(data.into())).await.is_err() {
            break;
        }
    }
}
```

### Backpressure

- `mpsc::channel(32)` — bounded channel на 32 сообщения
- Если клиент не успевает читать → channel заполняется → `tx.send()` блокируется
- `tx.send_timeout(msg, Duration::from_secs(5))` — если за 5 сек не отправлено → disconnect slow client
- Мониторинг: `tx.capacity()` → если < 25% → warning → если 0 → disconnect

---

## Graceful Shutdown

```rust
// В main.rs
let shutdown_signal = async {
    tokio::signal::ctrl_c().await.ok();
};

// Broadcast shutdown
let (shutdown_tx, _) = tokio::sync::broadcast::channel::<()>(1);

// В каждом connection handler:
tokio::select! {
    _ = read_task => {},
    _ = shutdown_rx.recv() => {
        // Отправить Close frame клиенту
        sender.send(Message::Close(Some(CloseFrame {
            code: 4007,
            reason: "Server shutting down, please reconnect".into(),
        }))).await.ok();
    },
}

// Graceful timeout: 30 секунд на закрытие всех соединений
tokio::time::timeout(Duration::from_secs(30), shutdown_complete).await.ok();
```

---

## Redis (session state)

| Ключ | Данные | TTL | Назначение |
|------|--------|-----|------------|
| `gw:session:{session_id}` | JSON session state | heartbeat_timeout * 2 | Для resume |
| `gw:user:{user_id}:sessions` | SET of session_ids | — | Все сессии пользователя |
| `gw:shard:{shard_id}:guilds` | SET of guild_ids | — | Гильдии на шарде |
| `gw:shard:{shard_id}:pod` | pod_name | 60 сек | Координация шардов |
| `gw:identify:{token_hash}:{date}` | counter | 24 часа | IDENTIFY rate limit |
| `gw:events:{session_id}:{minute}` | counter | 120 сек | Per-connection rate limit |

### Resume state

При disconnect с resumable кодом — session сохраняется в Redis:

```rust
// Сохранить сессию для resume
redis.set_ex(
    format!("gw:session:{session_id}"),
    serde_json::to_string(&session_state)?,
    heartbeat_timeout_secs * 2, // double timeout для запаса
).await?;

// Resume: восстановить сессию
let state: SessionState = redis.get(format!("gw:session:{session_id}")).await?;
// Replay пропущенных событий из NATS JetStream
```

### Event replay (для resume)

Пропущенные события хранятся в NATS JetStream с sequence numbers:

```rust
// При resume: запросить события с seq+1
let consumer = stream.get_or_create_consumer(
    format!("resume-{session_id}"),
    jetstream::consumer::pull::Config {
        deliver_policy: DeliverPolicy::ByStartSequence {
            start_sequence: last_seq + 1,
        },
        ..Default::default()
    },
).await?;
```

---

## Мониторинг

### Метрики (Prometheus)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `gateway_connections_active` | gauge | Активные WebSocket соединения |
| `gateway_connections_total` | counter | Всего соединений за всё время |
| `gateway_disconnects_total{code}` | counter | Disconnect-ы по close code |
| `gateway_events_dispatched_total{event}` | counter | Отправленные события по типу |
| `gateway_events_received_total{opcode}` | counter | Полученные от клиентов по opcode |
| `gateway_heartbeat_latency_seconds` | histogram | Latency heartbeat (send → ACK) |
| `gateway_identify_total{result}` | counter | IDENTIFY попытки (success/fail) |
| `gateway_resume_total{result}` | counter | RESUME попытки (success/fail) |
| `gateway_message_size_bytes{direction}` | histogram | Размер сообщений (in/out) |
| `gateway_compression_ratio` | histogram | Ratio сжатия |
| `gateway_connection_duration_seconds` | histogram | Длительность соединений |
| `gateway_nats_events_total` | counter | Получено событий из NATS |
| `gateway_nats_latency_seconds` | histogram | Задержка NATS → WebSocket |
| `gateway_slow_consumers_total` | counter | Отключённые slow consumers |
| `process_resident_memory_bytes` | gauge | Использование памяти |

### Per-shard метрики

| Метрика | Тип | Описание |
|---------|-----|----------|
| `gateway_shard_connections{shard_id}` | gauge | Соединения на шарде |
| `gateway_shard_guilds{shard_id}` | gauge | Гильдии на шарде |
| `gateway_shard_events{shard_id}` | counter | Dispatch events на шарде |

### Алерты

| Условие | Severity | Описание |
|---------|----------|----------|
| `gateway_connections_active` drop > 20% за 1 мин | critical | Массовый disconnect |
| `gateway_heartbeat_latency_seconds{p99}` > 5s | warning | Медленные heartbeats |
| `process_resident_memory_bytes` > 80% лимита | warning | Приближение к OOM |
| `gateway_slow_consumers_total` rate > 50/min | warning | Много slow consumers |
| `gateway_identify_total{result="fail"}` rate > 10/min | warning | Массовые ошибки auth |

---

## Безопасность

### Чеклист

- [ ] Только `wss://` (TLS) в production
- [ ] Валидация Origin header при handshake (whitelist доменов)
- [ ] JWT аутентификация при IDENTIFY (ES256, проверка expiry)
- [ ] Таймаут 5 секунд на IDENTIFY после Hello
- [ ] Rate limiting: 120 events/60s per connection
- [ ] Rate limiting: 1000 IDENTIFY/24h per token
- [ ] Max payload size: 4096 bytes
- [ ] Reconnect flood protection: max_concurrency + jitter
- [ ] Все входящие сообщения валидируются (JSON schema, opcode range)
- [ ] Сессия инвалидируется при logout (через NATS event)
- [ ] Close codes 1000/1001 инвалидируют сессию (не resumable)
- [ ] Данные сессии в Redis с TTL (не бесконечное хранение)
- [ ] Ping/Pong не раскрывает внутреннюю информацию
- [ ] Логирование: connect/disconnect с IP, user_id, shard_id (без токенов)
- [ ] Ограничение max_connections на инстанс (предотвращение resource exhaustion)
- [ ] Graceful shutdown: Close frame всем клиентам перед остановкой
- [ ] No permessage-deflate (уязвимость к CRIME/BREACH атакам — используем transport compression)
