# Search Service

Полнотекстовый поиск по сообщениям, пользователям, серверам. Индексация через NATS events, поисковый движок — Meilisearch.

Порт: `3007`
Путь: `services/search/`
Поиск: Meilisearch
Кеш: Redis

## Источники

- [Meilisearch Documentation](https://www.meilisearch.com/docs)
- [Meilisearch — Search API](https://www.meilisearch.com/docs/reference/api/search)
- [Meilisearch — Settings API](https://www.meilisearch.com/docs/reference/api/settings)
- [Meilisearch — Typo Tolerance](https://www.meilisearch.com/docs/learn/relevancy/typo_tolerance_settings)
- [Meilisearch — Ranking Rules](https://www.meilisearch.com/docs/learn/relevancy/ranking_rules)
- [Meilisearch — Multi-Search](https://www.meilisearch.com/docs/reference/api/multi_search)
- [Meilisearch — Indexing Best Practices](https://www.meilisearch.com/docs/learn/indexing/indexing_best_practices)
- [Meilisearch — Tenant Tokens](https://www.meilisearch.com/docs/learn/security/multitenancy_tenant_tokens)
- [Meilisearch — API Keys](https://www.meilisearch.com/docs/reference/api/keys)
- [Discord Support — How to Use Search](https://support.discord.com/hc/en-us/articles/115000468588-How-to-Use-Search-on-Discord)
- [crate: meilisearch-sdk](https://docs.rs/meilisearch-sdk/latest/meilisearch_sdk/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Структура сервиса

```
services/search/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   └── search.rs          # Search endpoints
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── search.rs          # Search logic
│   │   └── filters.rs         # Discord-style filter parser
│   ├── indexer/
│   │   ├── mod.rs
│   │   ├── messages.rs        # Message indexing
│   │   ├── guilds.rs          # Guild indexing
│   │   ├── users.rs           # User indexing
│   │   └── batch.rs           # Batch indexing logic
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   ├── events/
│   │   └── subscriber.rs      # NATS subscriber (index events)
│   └── permissions.rs         # Permission-aware search filtering
├── tests/
│   ├── common/mod.rs
│   ├── search_test.rs
│   ├── filter_test.rs
│   └── indexer_test.rs
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "search-service"
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

# Meilisearch
meilisearch-sdk = "0.28"

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
pub struct SearchConfig {
    // Сервер
    pub host: String,                    // SEARCH_HOST=0.0.0.0
    pub port: u16,                       // SEARCH_PORT=3007

    // Meilisearch
    pub meilisearch_url: String,         // MEILISEARCH_URL=http://meilisearch:7700
    pub meilisearch_master_key: String,  // MEILISEARCH_MASTER_KEY=...
    pub meilisearch_search_key: String,  // MEILISEARCH_SEARCH_KEY=...

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT (валидация)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=...

    // Indexing
    pub batch_size: usize,              // BATCH_SIZE=1000
    pub batch_flush_interval_ms: u64,   // BATCH_FLUSH_INTERVAL_MS=5000
}
```

---

## Формат ошибок

```json
{
    "code": "SEARCH_FAILED",
    "message": "Search query failed"
}
```

| Код | HTTP | Описание |
|-----|------|----------|
| `SEARCH_FAILED` | 500 | Ошибка Meilisearch |
| `INVALID_QUERY` | 400 | Невалидный поисковый запрос |
| `INVALID_FILTER` | 400 | Невалидный фильтр |
| `MISSING_PERMISSIONS` | 403 | Нет прав на поиск в канале |
| `GUILD_NOT_FOUND` | 404 | Гильдия не найдена |
| `RATE_LIMITED` | 429 | Превышен rate limit |
| `INDEX_ERROR` | 500 | Ошибка индексации |

---

## Meilisearch Indexes

### messages

**Основной индекс** — поиск по содержимому сообщений.

**Document structure:**

```json
{
    "id": "message_snowflake_as_string",
    "content": "Hello everyone! Check out this link",
    "author_id": "user_snowflake",
    "author_name": "JohnDoe",
    "channel_id": "channel_snowflake",
    "guild_id": "guild_snowflake",
    "timestamp": 1740153600,
    "has_link": true,
    "has_embed": true,
    "has_file": false,
    "has_image": false,
    "has_video": false,
    "has_sticker": false,
    "mention_ids": ["user_id_1", "user_id_2"],
    "mention_everyone": false,
    "pinned": false
}
```

**Settings:**

```json
{
    "searchableAttributes": ["content", "author_name"],
    "filterableAttributes": [
        "author_id", "channel_id", "guild_id", "timestamp",
        "has_link", "has_embed", "has_file", "has_image",
        "has_video", "has_sticker",
        "mention_ids", "mention_everyone", "pinned"
    ],
    "sortableAttributes": ["timestamp"],
    "displayedAttributes": [
        "id", "content", "author_id", "author_name",
        "channel_id", "guild_id", "timestamp",
        "has_link", "has_embed", "has_file", "has_image",
        "pinned"
    ],
    "rankingRules": [
        "words", "typo", "proximity", "attribute", "sort", "exactness"
    ],
    "typoTolerance": {
        "enabled": true,
        "minWordSizeForTypos": {
            "oneTypo": 5,
            "twoTypos": 9
        },
        "disableOnAttributes": ["author_id", "channel_id", "guild_id"]
    },
    "pagination": {
        "maxTotalHits": 1000
    }
}
```

### guilds

**Document structure:**

```json
{
    "id": "guild_snowflake",
    "name": "My Server",
    "description": "A cool server for cool people",
    "member_count": 1500,
    "icon_url": "https://cdn.example.com/icons/...",
    "features": ["COMMUNITY", "DISCOVERABLE"]
}
```

**Settings:**

```json
{
    "searchableAttributes": ["name", "description"],
    "filterableAttributes": ["features", "member_count"],
    "sortableAttributes": ["member_count"],
    "displayedAttributes": ["id", "name", "description", "member_count", "icon_url"]
}
```

### users

**Document structure:**

```json
{
    "id": "user_snowflake",
    "username": "JohnDoe",
    "display_name": "John",
    "bio": "Developer"
}
```

**Settings:**

```json
{
    "searchableAttributes": ["username", "display_name"],
    "filterableAttributes": [],
    "sortableAttributes": [],
    "displayedAttributes": ["id", "username", "display_name"]
}
```

---

## Discord-style Filters

### Синтаксис

| Фильтр | Пример | Meilisearch filter |
|--------|--------|-------------------|
| `from:` | `from:JohnDoe` | `author_id = "snowflake"` (resolve username → id) |
| `mentions:` | `mentions:JohnDoe` | `mention_ids = "snowflake"` |
| `has:link` | `has:link` | `has_link = true` |
| `has:embed` | `has:embed` | `has_embed = true` |
| `has:file` | `has:file` | `has_file = true` |
| `has:image` | `has:image` | `has_image = true` |
| `has:video` | `has:video` | `has_video = true` |
| `has:sticker` | `has:sticker` | `has_sticker = true` |
| `before:` | `before:2026-02-21` | `timestamp < 1740096000` |
| `after:` | `after:2026-01-01` | `timestamp > 1735689600` |
| `during:` | `during:2026-02-21` | `timestamp >= X AND timestamp < X+86400` |
| `in:` | `in:#general` | `channel_id = "snowflake"` (resolve name → id) |
| `pinned:` | `pinned:true` | `pinned = true` |

### Parser

```rust
struct ParsedSearch {
    query: String,                         // оставшийся текст после извлечения фильтров
    filters: Vec<SearchFilter>,
}

enum SearchFilter {
    From(String),           // username → resolve to user_id
    Mentions(String),       // username → resolve to user_id
    Has(HasType),           // link, embed, file, image, video, sticker
    Before(NaiveDate),
    After(NaiveDate),
    During(NaiveDate),
    In(String),             // channel name → resolve to channel_id
    Pinned(bool),
}

enum HasType {
    Link, Embed, File, Image, Video, Sticker,
}

fn parse_search_query(input: &str) -> ParsedSearch {
    // Regex: (from|mentions|has|before|after|during|in|pinned):(\S+)
    // Извлечь фильтры, оставшееся = query text
}
```

### Resolving names → IDs

`from:JohnDoe` и `in:#general` содержат имена, не ID. Resolve через NATS request:
- `from:JohnDoe` → NATS request к User Service → получить user_id
- `in:#general` → NATS request к Guild Service → получить channel_id

Результаты кешируются в Redis (TTL 5 мин).

---

## Permission-Aware Search

Пользователь может искать **только в каналах, к которым имеет VIEW_CHANNEL доступ**.

### Алгоритм

1. Получить список channel_ids гильдии (NATS request к Guild Service)
2. Для каждого канала: вычислить permissions пользователя (через `permissions` crate)
3. Отфильтровать каналы с VIEW_CHANNEL
4. Добавить filter: `channel_id IN [id1, id2, id3, ...]`

### Кеширование

```
SET search:channels:{user_id}:{guild_id} [channel_ids] EX 300
```

Инвалидация при:
- Изменении ролей пользователя
- Изменении permission overwrites канала
- Добавлении/удалении канала

### Tenant Tokens (альтернативный подход)

Meilisearch tenant tokens позволяют вшить фильтр прямо в JWT:

```json
{
    "searchRules": {
        "messages": {
            "filter": "channel_id IN ['123', '456', '789']"
        }
    },
    "apiKeyUid": "search-key-uid",
    "exp": 1740157200
}
```

Токен подписывается search API key. Meilisearch принудительно применяет вшитый фильтр на стороне сервера. TTL: 5 минут.

---

## Indexing Pipeline

### Async indexing через NATS

```
Message Service: publish "messages.*.*.created"
    ↓
Search Indexer: subscribe "messages.>"
    ↓
Batch accumulator:
    ├── Добавить document в batch buffer
    ├── Если buffer.len() >= BATCH_SIZE (1000) → flush
    └── Если прошло BATCH_FLUSH_INTERVAL (5 сек) → flush
    ↓
Meilisearch: add_documents / delete_documents
```

### Batch indexing

```rust
use meilisearch_sdk::Client;

struct SearchIndexer {
    client: Client,
    batch: Vec<MessageDocument>,
    batch_size: usize,
    last_flush: Instant,
}

impl SearchIndexer {
    async fn add(&mut self, doc: MessageDocument) {
        self.batch.push(doc);
        if self.batch.len() >= self.batch_size {
            self.flush().await;
        }
    }

    async fn flush(&mut self) {
        if self.batch.is_empty() { return; }
        let docs = std::mem::take(&mut self.batch);
        let index = self.client.index("messages");

        match index.add_documents(&docs, Some("id")).await {
            Ok(task) => tracing::info!(task_uid = task.task_uid, count = docs.len(), "Indexed batch"),
            Err(e) => tracing::error!(error = %e, count = docs.len(), "Failed to index batch"),
        }

        self.last_flush = Instant::now();
    }
}
```

**Meilisearch auto-batching**: последовательные `add_documents` для одного индекса автоматически группируются в одну операцию на стороне Meilisearch.

### Re-indexing

При необходимости полной переиндексации:
1. Создать новый индекс `messages_v2` с нужными settings
2. Bulk-load всех документов из PostgreSQL/ScyllaDB
3. Дождаться завершения всех tasks
4. Swap index alias: `POST /swap-indexes` (если поддерживается) или обновить конфиг

---

## API Endpoints

### POST /guilds/:guild_id/search

Поиск в гильдии.

**Права**: JWT + `VIEW_CHANNEL` хотя бы в одном канале гильдии

**Request:**

```json
{
    "query": "hello world",
    "filters": "from:JohnDoe has:image",
    "sort": "timestamp:desc",
    "limit": 25,
    "offset": 0
}
```

**Или с pre-parsed фильтрами:**

```json
{
    "query": "hello world",
    "author_id": "123456789",
    "channel_id": "987654321",
    "has": ["image", "link"],
    "before": "2026-02-21",
    "after": "2026-01-01",
    "pinned": true,
    "sort": "timestamp:desc",
    "limit": 25,
    "offset": 0
}
```

**Response (200):**

```json
{
    "hits": [
        {
            "id": "111222333444555",
            "content": "hello world, check this image",
            "author_id": "123456789",
            "author_name": "JohnDoe",
            "channel_id": "987654321",
            "guild_id": "555666777",
            "timestamp": 1740153600,
            "has_image": true,
            "pinned": false,
            "_formatted": {
                "content": "<em>hello</em> <em>world</em>, check this image"
            }
        }
    ],
    "query": "hello world",
    "processingTimeMs": 12,
    "estimatedTotalHits": 42,
    "limit": 25,
    "offset": 0
}
```

---

### POST /search/users

Поиск пользователей (по username / display_name).

**Request:**

```json
{
    "query": "john",
    "limit": 10
}
```

**Response (200):**

```json
{
    "hits": [
        {
            "id": "123456789",
            "username": "JohnDoe",
            "display_name": "John"
        }
    ],
    "processingTimeMs": 3,
    "estimatedTotalHits": 5
}
```

---

### POST /search/guilds

Поиск серверов (discoverable).

**Request:**

```json
{
    "query": "gaming",
    "limit": 20,
    "sort": "member_count:desc"
}
```

---

## NATS Events

### Подписки (входящие)

| Subject | Источник | Действие |
|---------|----------|----------|
| `messages.*.*.created` | Message Service | Index document |
| `messages.*.*.updated` | Message Service | Update document |
| `messages.*.*.deleted` | Message Service | Delete document |
| `messages.*.*.bulk_deleted` | Message Service | Bulk delete documents |
| `guild.created` | Guild Service | Index guild |
| `guild.updated` | Guild Service | Update guild |
| `guild.deleted` | Guild Service | Delete guild + все сообщения гильдии |
| `user.updated` | User Service | Update user |
| `user.deleted` | User Service | Delete user document |

---

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /guilds/:id/search | 10 запросов | 10 секунд |
| POST /search/users | 10 запросов | 10 секунд |
| POST /search/guilds | 10 запросов | 10 секунд |

---

## Мониторинг

### Метрики

| Метрика | Тип | Описание |
|---------|-----|----------|
| `search_queries_total{index}` | counter | Поисковые запросы по индексу |
| `search_query_duration_ms` | histogram | Время поиска (Meilisearch processing) |
| `search_results_count` | histogram | Количество результатов |
| `search_index_documents_total{index}` | gauge | Документов в индексе |
| `search_indexing_batches_total` | counter | Отправленных batch-ей |
| `search_indexing_documents_total` | counter | Проиндексированных документов |
| `search_indexing_errors_total` | counter | Ошибки индексации |
| `search_indexing_lag_seconds` | gauge | Задержка индексации (NATS event → indexed) |
| `search_permission_checks_total` | counter | Проверок прав |
| `search_permission_cache_hit_ratio` | gauge | Cache hit rate для permissions |

### Алерты

| Условие | Severity | Описание |
|---------|----------|----------|
| `search_query_duration_ms{p99}` > 200ms | warning | Медленные запросы |
| `search_indexing_lag_seconds` > 30 | warning | Отставание индексации |
| `search_indexing_errors_total` rate > 10/min | critical | Массовые ошибки индексации |
| Meilisearch health check fail | critical | Meilisearch недоступен |

---

## Безопасность

### Чеклист

- [ ] Master key хранится в Kubernetes Secret, не в коде
- [ ] Search API key используется только для поиска (не admin операции)
- [ ] Permission-aware search: пользователь видит только доступные каналы
- [ ] Tenant tokens с коротким TTL (5 мин)
- [ ] Query sanitization: экранирование спецсимволов
- [ ] Rate limiting на все search endpoints
- [ ] Meilisearch не доступен извне (только внутренняя сеть Kubernetes)
- [ ] Content в индексе не содержит sensitive data (пароли, токены)
- [ ] Индексируются только сообщения из каналов (не DM, если не разрешено)
- [ ] При удалении пользователя: удалить/анонимизировать документы в индексе
- [ ] При удалении гильдии: удалить все документы гильдии
- [ ] Логирование: search queries без содержимого результатов
