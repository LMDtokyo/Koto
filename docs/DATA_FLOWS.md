# Потоки данных (Data Flows)

Полная документация потоков данных в коммуникационной платформе. Описывает пошаговые последовательности для каждого критического сценария, включая участвующие сервисы, NATS события, кеширование и обработку ошибок.

## Источники

- [NATS Core — Request/Reply](https://docs.nats.io/nats-concepts/core-nats/request-reply)
- [NATS JetStream](https://docs.nats.io/nats-concepts/jetstream)
- [Discord Engineering — How Discord Stores Trillions of Messages](https://discord.com/blog/how-discord-stores-trillions-of-messages)
- [Discord Engineering — How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)
- [Discord Developer Docs — Gateway](https://docs.discord.com/developers/events/gateway)
- [Discord Developer Docs — Permissions](https://docs.discord.com/developers/topics/permissions)
- Внутренняя документация: [SERVICES.md](SERVICES.md), [CRATES.md](CRATES.md), [SECURITY.md](SECURITY.md)
- Документация сервисов: [api.md](services/api.md), [gateway.md](services/gateway.md), [auth.md](services/auth.md), [messages.md](services/messages.md), [guilds.md](services/guilds.md)

---

## Оглавление

1. [Паттерны межсервисного общения](#1-паттерны-межсервисного-общения)
2. [Отправка сообщения](#2-отправка-сообщения)
3. [Регистрация пользователя](#3-регистрация-пользователя)
4. [Проверка прав (Permission Check)](#4-проверка-прав-permission-check)
5. [Подключение WebSocket](#5-подключение-websocket)
6. [Загрузка файла](#6-загрузка-файла)
7. [Поиск сообщений](#7-поиск-сообщений)
8. [Вступление в сервер по инвайту](#8-вступление-в-сервер-по-инвайту)

---

## 1. Паттерны межсервисного общения

В платформе используются два паттерна взаимодействия через NATS, каждый для своего класса задач.

### 1.1 NATS Request/Reply (синхронный)

Используется API Gateway (порт 3000) для проксирования клиентских запросов к внутренним сервисам.

```
Client                 API Gateway (:3000)              Auth Service (:3001)
  |                         |                                |
  |-- POST /auth/login ---->|                                |
  |                         |-- NATS request("auth.login")-->|
  |                         |         payload + user_id      |
  |                         |                                |
  |                         |<-- NATS reply(response) -------|
  |<-- HTTP 200 ------------|                                |
```

**Характеристики:**
- Транспорт: Core NATS (без JetStream)
- Семантика: at-most-once
- Таймаут: 5000 мс (`NATS_REQUEST_TIMEOUT_MS`)
- Формат envelope:

```json
{
    "user_id": 123456789,
    "request_id": "uuid-v4",
    "data": { "...payload..." }
}
```

**Когда использовать:**
- Клиент ожидает синхронный ответ (REST API)
- Операции чтения (`GET /guilds/:id`, `GET /channels/:id/messages`)
- Мутации с ожиданием результата (`POST /auth/login`, `POST /guilds`)

**Subject mapping (API Gateway -> сервисы):**

| HTTP Route | NATS Subject | Целевой сервис (порт) |
|------------|-------------|----------------------|
| `POST /auth/register` | `auth.register` | Auth (:3001) |
| `POST /auth/login` | `auth.login` | Auth (:3001) |
| `POST /auth/refresh` | `auth.refresh` | Auth (:3001) |
| `GET /users/@me` | `users.get_me` | Users (:3002) |
| `POST /guilds` | `guilds.create` | Guilds (:3003) |
| `GET /guilds/:id` | `guilds.get` | Guilds (:3003) |
| `POST /channels/:id/messages` | `messages.create` | Messages (:3004) |
| `GET /channels/:id/messages` | `messages.list` | Messages (:3004) |
| `POST /media/upload/presign` | `media.presign` | Media (:3005) |
| `POST /guilds/:id/search` | `search.messages` | Search (:3007) |
| `POST /voice/token` | `voice.token` | Voice (:3008) |
| `POST /invites/:code/accept` | `guilds.invite_accept` | Guilds (:3003) |

### 1.2 NATS JetStream Pub/Sub (асинхронный)

Используется для межсервисных событий, когда отправитель не ждет ответа.

```
Messages Service (:3004)     NATS JetStream         Подписчики
  |                              |                      |
  |-- publish_persistent ------->|                      |
  |   subject: messages.         |                      |
  |     {guild_id}.{channel_id}  |                      |
  |     .created                 |                      |
  |                              |-- deliver ---------->| Gateway (:4000)
  |                              |-- deliver ---------->| Search (:3007)
  |                              |-- deliver ---------->| Notifications (:3006)
  |                              |                      |
  |                              |<-- ack --------------|
```

**Характеристики:**
- Транспорт: NATS JetStream
- Семантика: at-least-once (с ack)
- Персистентность: события сохраняются в stream
- Replay: возможность повторной доставки пропущенных событий (для Resume)

**Когда использовать:**
- Fan-out событий нескольким подписчикам
- Доставка real-time событий через Gateway
- Асинхронная индексация (Search Service)
- Уведомления (Notification Service)
- Любой случай, когда потеря события недопустима

**Subject иерархия (из `crates/nats-events/src/subjects.rs`):**

| Subject | Тип | Издатель |
|---------|-----|----------|
| `messages.{guild_id}.{channel_id}.created` | JetStream | Messages (:3004) |
| `messages.{guild_id}.{channel_id}.updated` | JetStream | Messages (:3004) |
| `messages.{guild_id}.{channel_id}.deleted` | JetStream | Messages (:3004) |
| `messages.{guild_id}.{channel_id}.bulk_deleted` | JetStream | Messages (:3004) |
| `messages.{guild_id}.{channel_id}.reaction_added` | JetStream | Messages (:3004) |
| `messages.{guild_id}.{channel_id}.reaction_removed` | JetStream | Messages (:3004) |
| `messages.{guild_id}.{channel_id}.pins_update` | JetStream | Messages (:3004) |
| `guild.{guild_id}.channel.created` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.channel.updated` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.channel.deleted` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.member.joined` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.member.left` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.member.banned` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.role.created` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.role.updated` | JetStream | Guilds (:3003) |
| `guild.{guild_id}.role.deleted` | JetStream | Guilds (:3003) |
| `auth.user.registered` | JetStream | Auth (:3001) |
| `auth.user.login` | JetStream | Auth (:3001) |
| `auth.user.logout` | JetStream | Auth (:3001) |
| `user.{user_id}.updated` | JetStream | Users (:3002) |
| `presence.{user_id}.updated` | JetStream | Presence (:3010) |
| `typing.{guild_id}.{channel_id}` | Core NATS | Presence (:3010) |
| `voice.{guild_id}.state_updated` | JetStream | Voice (:3008) |
| `moderation.{guild_id}.automod.action` | JetStream | Moderation (:3009) |
| `media.uploaded` | JetStream | Media (:3005) |

### 1.3 Сравнение паттернов

```
                    Request/Reply              JetStream Pub/Sub
                    ─────────────              ──────────────────
Семантика:          at-most-once               at-least-once
Кол-во получателей: 1 (строго)                 N (fan-out)
Ответ:              ожидается                  нет (fire-and-forget)
Персистентность:    нет                        да (stream)
Таймаут:            5000 мс                    нет (async delivery)
Использование:      API запросы                Межсервисные события
```

---

## 2. Отправка сообщения

Полный поток отправки текстового сообщения от клиента до всех получателей.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| API Gateway | 3000 | JWT проверка, rate limiting, проксирование |
| Messages Service | 3004 | Бизнес-логика, БД, публикация событий |
| Guilds Service | 3003 | Проверка прав (через NATS request) |
| WS Gateway | 4000 | Доставка события подключённым клиентам |
| Search Service | 3007 | Асинхронная индексация в Meilisearch |
| Notification Service | 3006 | Push-уведомления для offline пользователей |

### Диаграмма последовательности

```
Client          API GW         Messages       Guilds         NATS         WS Gateway      Search
  |             (:3000)        (:3004)        (:3003)      JetStream      (:4000)        (:3007)
  |                |               |              |            |              |              |
  |--POST /channels/:id/messages-->|              |            |              |              |
  |  Authorization: Bearer <JWT>   |              |            |              |              |
  |  {"content": "Hello!"}         |              |            |              |              |
  |                |               |              |            |              |              |
  |           [1] JWT validation   |              |            |              |              |
  |           [2] Rate limit check |              |            |              |              |
  |                |               |              |            |              |              |
  |                |--NATS req---->|              |            |              |              |
  |                | "messages.    |              |            |              |              |
  |                |  create"      |              |            |              |              |
  |                |               |              |            |              |              |
  |                |          [3] Валидация       |            |              |              |
  |                |          [4] Проверка прав   |            |              |              |
  |                |               |--NATS req--->|            |              |              |
  |                |               | "guilds.     |            |              |              |
  |                |               |  check_perms"|            |              |              |
  |                |               |<--reply------|            |              |              |
  |                |               |              |            |              |              |
  |                |          [5] Snowflake ID    |            |              |              |
  |                |          [6] INSERT в БД     |            |              |              |
  |                |          [7] Инвалидация кеша|            |              |              |
  |                |               |              |            |              |              |
  |                |          [8] Publish event   |            |              |              |
  |                |               |--publish_persistent------>|              |              |
  |                |               |  "messages.{gid}.         |              |              |
  |                |               |   {cid}.created"          |              |              |
  |                |               |              |            |              |              |
  |                |<--reply-------|              |            |              |              |
  |<--HTTP 201-----|               |              |            |              |              |
  |  {message obj}                 |              |            |              |              |
  |                |               |              |            |--deliver---->|              |
  |                |               |              |            |              |              |
  |                |               |              |       [9] Fan-out:       |              |
  |                |               |              |       intents check      |              |
  |                |               |              |       permissions check  |              |
  |                |               |              |              |              |              |
  |                |               |              |         [10] WS send     |              |
  |                |               |              |          op:0 DISPATCH   |              |
  |                |               |              |          t:MESSAGE_CREATE|              |
  |                |               |              |              |---->Recipients             |
  |                |               |              |            |              |              |
  |                |               |              |            |--deliver--------------->|  |
  |                |               |              |            |              |    [11] Index|
  |                |               |              |            |              |    в Meili-  |
  |                |               |              |            |              |    search    |
```

### Пошаговая последовательность

**Шаг 1: JWT валидация (API Gateway)**

API Gateway извлекает JWT из заголовка `Authorization: Bearer <token>`, проверяет подпись (ES256), expiry (`exp`), и добавляет `CurrentUser { user_id }` в extensions запроса.

- **Ошибка**: невалидный JWT -> `401 INVALID_TOKEN`, запрос не проксируется
- **Ошибка**: JWT истёк -> `401 TOKEN_EXPIRED`

**Шаг 2: Rate limit check (API Gateway)**

Проверяется глобальный лимит (50 req/1s per user) и per-endpoint лимит (`POST /channels/:id/messages` -> 5 req/5s per channel). Реализация через Redis sliding window (`crates/rate-limit`).

- **Ошибка**: превышен лимит -> `429 RATE_LIMITED`, заголовок `Retry-After`
- **Redis ключ**: `rate:msg_create:{channel_id}`

**Шаг 3: Валидация входных данных (Messages Service)**

Messages Service получает NATS request на subject `messages.create` и валидирует:
- `content`: максимум 2000 символов
- `embeds`: максимум 10, суммарно 6000 символов
- Хотя бы одно из: `content`, `embeds`, attachments
- Контент санитизируется через `ammonia` crate (whitelist HTML тегов)
- Упоминания парсятся regex: `<@\d+>`, `<@&\d+>`, `@everyone`, `@here`

- **Ошибка**: пустое сообщение -> `400 EMPTY_MESSAGE`
- **Ошибка**: контент слишком длинный -> `400 MESSAGE_TOO_LONG`
- **Ошибка**: слишком много embeds -> `400 TOO_MANY_EMBEDS`

**Шаг 4: Проверка прав (Messages Service -> Guilds Service)**

Messages Service делает NATS request к Guilds Service для проверки прав пользователя в канале. Подробный алгоритм описан в разделе [4. Проверка прав](#4-проверка-прав-permission-check).

Требуемые права:
- `SEND_MESSAGES` (1 << 11) для обычного канала
- `SEND_MESSAGES_IN_THREADS` (1 << 38) для треда
- `EMBED_LINKS` (1 << 14) если есть embeds
- `ATTACH_FILES` (1 << 15) если есть вложения
- `MENTION_EVERYONE` (1 << 17) если есть `@everyone` / `@here`

Дополнительно проверяется slowmode:
- Redis ключ: `slowmode:{channel_id}:{user_id}`
- Если ключ существует -> `429 SLOWMODE_ACTIVE`
- Обход: пользователи с `MANAGE_MESSAGES` или `MANAGE_CHANNELS`

- **Ошибка**: нет прав -> `403 MISSING_PERMISSIONS`
- **Ошибка**: slowmode активен -> `429 SLOWMODE_ACTIVE`

**Шаг 5: Генерация Snowflake ID**

Через `crates/snowflake::SnowflakeGenerator` генерируется уникальный 64-битный ID:

```
| Timestamp (42 bits) | Worker ID (5 bits) | Process ID (5 bits) | Sequence (12 bits) |
```

Custom epoch: `2025-01-01 00:00:00 UTC` (1735689600000 мс).

- **Ошибка**: lock poisoned (крайне маловероятно) -> `500 INTERNAL_ERROR`

**Шаг 6: Сохранение в БД (PostgreSQL)**

```sql
-- sqlx::query! с параметрами $1, $2, ...
INSERT INTO messages (id, channel_id, guild_id, author_id, content, embeds,
    mention_ids, mention_role_ids, mention_everyone, message_type, flags)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
RETURNING *;
```

Индекс `idx_messages_channel_id (channel_id, id DESC) WHERE deleted_at IS NULL` используется для последующего cursor-based чтения.

- **Ошибка**: PostgreSQL недоступен -> `500 INTERNAL_ERROR`, NATS reply с кодом ошибки

**Шаг 7: Инвалидация кеша**

- `DEL msg:{message_id}` (на случай кеш-коллизии)
- Установка slowmode: `SET slowmode:{channel_id}:{user_id} 1 EX {rate_limit_per_user}`

**Шаг 8: Публикация NATS события**

Messages Service публикует событие через JetStream (`publish_persistent`):

- Subject: `messages.{guild_id}.{channel_id}.created`
- Payload: полный message объект + `guild_id`, `member` (partial)
- Event enum: `Event::MessageCreated(MessagePayload)`

**Шаг 9: Fan-out на WS Gateway**

WS Gateway (:4000) подписан на `guild.{guild_id}.>` для каждой гильдии, обслуживаемой его шардами. При получении события:

1. Определить все сессии, подключённые к гильдии `guild_id`
2. Для каждой сессии проверить:
   - Intent `GUILD_MESSAGES` (бит 9, значение 512) включён?
   - Пользователь имеет `VIEW_CHANNEL` (1 << 10) в данном канале?
3. Отфильтровать `content` если intent `MESSAGE_CONTENT` (бит 15) не включён

**Шаг 10: Отправка через WebSocket**

Для каждого подходящего клиента формируется DISPATCH сообщение:

```json
{
    "op": 0,
    "s": 42,
    "t": "MESSAGE_CREATE",
    "d": { "...полный message объект..." }
}
```

Sequence number (`s`) инкрементируется монотонно для каждого соединения. Сообщение сжимается (zlib-stream или zstd-stream) и отправляется через `session.tx` (mpsc channel, bounded 32).

- **Ошибка**: slow consumer (channel полон) -> disconnect через 5 сек -> клиент reconnect

**Шаг 11: Асинхронная индексация (Search Service)**

Search Service (:3007) подписан на `messages.{guild_id}.{channel_id}.created` через JetStream consumer. При получении:

1. Извлечь `content`, `author_id`, `channel_id`, `guild_id`, `id`
2. Добавить документ в Meilisearch индекс `messages`
3. ACK события в JetStream

- **Ошибка**: Meilisearch недоступен -> NACK, JetStream повторит доставку

### Кеширование (Redis)

| Ключ | Данные | TTL | Когда устанавливается |
|------|--------|-----|----------------------|
| `msg:{message_id}` | JSON message | 5 мин | При GET запросе (cache-aside) |
| `slowmode:{channel_id}:{user_id}` | `1` | `rate_limit_per_user` | После отправки сообщения |
| `embed_cache:{url_hash}` | JSON embed | 1 час | После резолва link preview |

### Асинхронный embed resolve

Если в `content` найдены URL-ы (парсинг через `linkify` crate), после шага 8 запускается фоновая задача:

```
Messages Service
  |
  [tokio::spawn] --> Парсинг URL из content
                     |
                     +--> HEAD запрос (таймаут 5 сек)
                     +--> Fetch <head> (Open Graph, oEmbed)
                     +--> Формирование Embed
                     |
                     +--> UPDATE messages SET embeds = $2 WHERE id = $1
                     +--> Publish "messages.{gid}.{cid}.updated"
                     +--> Gateway -> MESSAGE_UPDATE -> клиенты
```

---

## 3. Регистрация пользователя

Поток создания нового аккаунта пользователя.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| API Gateway | 3000 | Rate limiting, проксирование |
| Auth Service | 3001 | Валидация, хеширование пароля, создание credentials |
| Users Service | 3002 | Создание профиля пользователя |
| Notification Service | 3006 | Отправка verification email |

### Диаграмма последовательности

```
Client          API GW         Auth Service      NATS JS       Users         Notifications
  |             (:3000)        (:3001)                         (:3002)       (:3006)
  |                |               |                |             |              |
  |--POST /auth/register---------->|               |             |              |
  |  {"username":"john",           |               |             |              |
  |   "email":"john@example.com",  |               |             |              |
  |   "password":"..."}            |               |             |              |
  |                |               |               |             |              |
  |           [1] Rate limit       |               |             |              |
  |                |               |               |             |              |
  |                |--NATS req---->|               |             |              |
  |                | "auth.register"               |             |              |
  |                |               |               |             |              |
  |                |          [2] Валидация         |             |              |
  |                |          [3] Email уникальность|             |              |
  |                |          [4] HaveIBeenPwned    |             |              |
  |                |          [5] Snowflake ID      |             |              |
  |                |          [6] Argon2id hash     |             |              |
  |                |          [7] INSERT credentials|             |              |
  |                |          [8] Verification token|             |              |
  |                |               |               |             |              |
  |                |          [9] Publish events    |             |              |
  |                |               |--publish------>|             |              |
  |                |               | "auth.user.    |             |              |
  |                |               |  registered"   |             |              |
  |                |               |               |             |              |
  |                |<--reply-------|               |             |              |
  |<--HTTP 201-----|               |               |             |              |
  |  {user_id,                     |               |             |              |
  |   email,                       |               |             |              |
  |   email_verified: false}       |               |             |              |
  |                |               |               |             |              |
  |                |               |               |--deliver--->|              |
  |                |               |          [10] Создать       |              |
  |                |               |               профиль      |              |
  |                |               |               |             |              |
  |                |               |               |--deliver-------------->|   |
  |                |               |          [11] Отправить     |              |
  |                |               |               verification  |              |
  |                |               |               email         |              |
```

### Пошаговая последовательность

**Шаг 1: Rate limit (API Gateway)**

Rate limit для регистрации: 3 запроса / 1 час per IP.

- **Redis ключ**: `rate:auth_register:{ip}`
- **Ошибка**: превышен лимит -> `429 RATE_LIMITED`

**Шаг 2: Валидация входных данных (Auth Service)**

Auth Service валидирует через `validator` crate:
- `username`: непустой, допустимые символы
- `email`: валидный формат, нормализация (lowercase, trim)
- `password`: 8-128 символов, все Unicode-символы разрешены (по OWASP)

- **Ошибка**: невалидные данные -> `422 VALIDATION_ERROR`

**Шаг 3: Проверка уникальности email**

```sql
-- sqlx::query! с параметром $1
SELECT EXISTS(SELECT 1 FROM user_credentials WHERE email = $1) AS exists;
```

- **Ошибка**: email уже существует -> `409 ACCOUNT_EXISTS`

**Шаг 4: Проверка HaveIBeenPwned (Auth Service)**

Проверка пароля по базе утечек через k-anonymity API:
1. Вычислить SHA-1 хеш пароля
2. Отправить первые 5 символов хеша в HaveIBeenPwned API
3. Проверить наличие полного хеша в ответе

- **Ошибка**: пароль скомпрометирован -> `422 COMPROMISED_PASSWORD`
- **Ошибка**: HaveIBeenPwned недоступен -> пропустить (не блокировать регистрацию)

**Шаг 5: Генерация Snowflake ID**

Через `crates/snowflake::SnowflakeGenerator` генерируется `user_id`.

**Шаг 6: Хеширование пароля (Argon2id)**

Параметры по OWASP Password Storage Cheat Sheet:
- Алгоритм: Argon2id
- m = 47104 (46 MiB)
- t = 1 (итерация)
- p = 1 (параллелизм)
- Salt: 16 bytes, автоматически (argon2 crate)

Результат: PHC string `$argon2id$v=19$m=47104,t=1,p=1$<salt>$<hash>`

- **Ошибка**: нехватка памяти для хеширования -> `500 INTERNAL_ERROR`

**Шаг 7: Сохранение в БД**

```sql
INSERT INTO user_credentials (user_id, email, password_hash, email_verified)
VALUES ($1, $2, $3, false);
```

- **Ошибка**: race condition (дубликат email) -> обработка UNIQUE constraint -> `409 ACCOUNT_EXISTS`
- **Ошибка**: PostgreSQL недоступен -> `500 INTERNAL_ERROR`

**Шаг 8: Генерация verification token**

32 bytes криптографически случайных данных (CSPRNG), хешируются SHA-256 для хранения:

```sql
INSERT INTO email_verifications (user_id, token_hash, purpose, expires_at)
VALUES ($1, $2, 'verify_email', NOW() + INTERVAL '24 hours');
```

**Шаг 9: Публикация NATS события**

Auth Service публикует в JetStream:
- Subject: `auth.user.registered`
- Payload: `{ user_id, email }`
- Подписчики: Users Service, Notification Service

**Шаг 10: Создание профиля (Users Service)**

Users Service (:3002) получает событие `auth.user.registered` и создаёт профиль:

```sql
INSERT INTO users (id, username, avatar, bio)
VALUES ($1, $2, NULL, NULL);
```

- **Ошибка**: сбой создания профиля -> JetStream NACK, повторная доставка

**Шаг 11: Отправка verification email (Notification Service)**

Notification Service (:3006) получает событие и отправляет email через `lettre` crate (SMTP):
- Тема: "Подтвердите ваш email"
- Ссылка: `https://app.example.com/verify?token={raw_token}`

- **Ошибка**: SMTP недоступен -> JetStream NACK, повторная попытка

### Кеширование (Redis)

| Ключ | Данные | TTL | Когда |
|------|--------|-----|-------|
| `rate:auth_register:{ip}` | ZSET timestamps | 1 час | При каждой попытке регистрации |

### Последующий поток: Email Verification

```
Client          API GW         Auth Service
  |             (:3000)        (:3001)
  |                |               |
  |--POST /auth/verify-email------>|
  |  {"token": "raw_token"}        |
  |                |               |
  |                |--NATS req---->|
  |                | "auth.verify  |
  |                |  _email"      |
  |                |               |
  |                |  [1] SHA-256 hash token
  |                |  [2] SELECT from email_verifications
  |                |  [3] Проверить: not expired, not used
  |                |  [4] UPDATE user_credentials SET email_verified = true
  |                |  [5] UPDATE email_verifications SET used = true
  |                |  [6] Publish "auth.email.verified"
  |                |               |
  |                |<--reply-------|
  |<--HTTP 200-----|               |
```

---

## 4. Проверка прав (Permission Check)

Полный алгоритм вычисления эффективных прав пользователя в контексте канала. Реализован в `crates/permissions/src/calculator.rs`.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| Запрашивающий сервис | * | Инициирует проверку |
| Guilds Service | 3003 | Вычисление прав, доступ к БД |
| Redis | — | Кеширование вычисленных прав |

### Диаграмма вычисления прав

```
Запрос на действие
       |
       v
[1] Извлечь user_id из JWT
       |
       v
[2] Получить данные пользователя и гильдии
    +----------------------------------------------+
    | Redis: guild:{id}:member:{user_id}:perms     |
    | Hit? -> вернуть кешированные base permissions |
    | Miss? -> вычислить (шаги 3-6)                |
    +----------------------------------------------+
       |
       v
[3] user_id == guild.owner_id ?
    +-----> ДА: return ALL_PERMISSIONS (0xFFFFFFFFFFFFFFFF)
    |
    NO
    |
    v
[4] Вычислить base permissions (уровень гильдии)
    +------------------------------------------------+
    | permissions = @everyone_role.permissions        |
    |                                                |
    | for role in member.roles:                      |
    |     permissions |= role.permissions    (OR)    |
    |                                                |
    | if permissions & ADMINISTRATOR (1 << 3):       |
    |     return ALL_PERMISSIONS                     |
    +------------------------------------------------+
       |
       v
[5] Применить channel overwrites
    +------------------------------------------------+
    | // Шаг 5a: @everyone overwrite                 |
    | ow = overwrites.find(id == guild_id, type == 0)|
    | permissions &= ~ow.deny                        |
    | permissions |= ow.allow                        |
    |                                                |
    | // Шаг 5b: Role overwrites (все роли, merged)  |
    | allow = 0, deny = 0                            |
    | for role in member.roles:                      |
    |     ow = overwrites.find(id == role.id, t == 0)|
    |     allow |= ow.allow                          |
    |     deny  |= ow.deny                           |
    | permissions &= ~deny                           |
    | permissions |= allow                           |
    |                                                |
    | // Шаг 5c: User-specific overwrite             |
    | ow = overwrites.find(id == user_id, type == 1) |
    | permissions &= ~ow.deny                        |
    | permissions |= ow.allow                        |
    +------------------------------------------------+
       |
       v
[6] Применить неявные правила
    +------------------------------------------------+
    | if !(permissions & VIEW_CHANNEL):              |
    |     permissions = 0   // нет доступа к каналу  |
    |                                                |
    | if !(permissions & SEND_MESSAGES):             |
    |     permissions &= ~(MENTION_EVERYONE |        |
    |         SEND_TTS | ATTACH_FILES | EMBED_LINKS) |
    |                                                |
    | if member.communication_disabled_until > now():  |
    |     permissions = VIEW_CHANNEL |               |
    |                   READ_MESSAGE_HISTORY          |
    +------------------------------------------------+
       |
       v
[7] Кешировать в Redis
    SET guild:{id}:member:{user_id}:perms {permissions} EX 120
       |
       v
[8] Проверить конкретное право
    permissions & REQUIRED_PERMISSION != 0 ?
    +-----> ДА: разрешить действие
    |
    NO: вернуть 403 MISSING_PERMISSIONS
```

### Данные для вычисления

Для вычисления прав необходимы следующие данные из БД (Guilds Service):

```sql
-- 1. Гильдия (owner_id)
SELECT owner_id FROM guilds WHERE id = $1;

-- 2. Роли пользователя
SELECT r.id, r.permissions, r.position
FROM member_roles mr
JOIN roles r ON r.id = mr.role_id
WHERE mr.user_id = $1 AND mr.guild_id = $2;

-- 3. @everyone роль (id == guild_id)
SELECT permissions FROM roles WHERE id = $1; -- guild_id

-- 4. Channel permission overwrites
SELECT target_id, target_type, allow, deny
FROM permission_overwrites
WHERE channel_id = $1;

-- 5. Timeout статус
SELECT communication_disabled_until
FROM guild_members
WHERE user_id = $1 AND guild_id = $2;
```

### Кеширование прав (Redis)

| Ключ | TTL | Данные | Инвалидация |
|------|-----|--------|-------------|
| `guild:{id}:member:{user_id}:perms` | 2 мин | u64 (base permissions) | Изменение ролей участника, изменение role permissions |
| `guild:{id}:roles` | 5 мин | JSON массив ролей | Создание/обновление/удаление роли |
| `guild:{id}:member:{user_id}` | 5 мин | JSON данные участника + роли | Изменение ролей, nickname, timeout |

**Инвалидация**: при публикации `guild.{guild_id}.role.updated` или `guild.{guild_id}.member.updated` через NATS, Guilds Service инвалидирует все связанные ключи кеша.

### Иерархия приоритетов (сводка)

```
Приоритет (от высшего к низшему):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 1. Владелец гильдии           -> ВСЕ ПРАВА (безусловно)
 2. ADMINISTRATOR               -> ВСЕ ПРАВА (игнорирует overwrites)
 3. User-specific overwrite     -> наивысший приоритет среди overwrites
 4. Role overwrites (merged)    -> объединённые allow/deny всех ролей
 5. @everyone overwrite         -> overwrite для роли @everyone
 6. Base permissions            -> OR всех role.permissions участника
 7. @everyone role permissions  -> начальная точка вычисления
```

---

## 5. Подключение WebSocket

Полный поток установления real-time соединения от клиента до получения событий.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| WS Gateway | 4000 | Управление WebSocket соединениями |
| Auth (JWT) | — | JWT public key для валидации (не прямой вызов) |
| Guilds Service | 3003 | Получение списка гильдий пользователя |
| Presence Service | 3010 | Обновление онлайн-статуса |
| Redis | — | Хранение session state, координация шардов |
| NATS | — | Подписка на события гильдий |

### Диаграмма последовательности

```
Client                  WS Gateway (:4000)           Redis        NATS       Guilds     Presence
  |                          |                         |           |         (:3003)     (:3010)
  |                          |                         |           |            |            |
  |--wss://gw?v=1&          |                         |           |            |            |
  |  encoding=json&          |                         |           |            |            |
  |  compress=zlib-stream--->|                         |           |            |            |
  |                          |                         |           |            |            |
  |                     [1] WebSocket upgrade          |           |            |            |
  |                     [2] Origin check               |           |            |            |
  |                     [3] Connection limit check     |           |            |            |
  |                          |                         |           |            |            |
  |<---Hello (op:10)---------|                         |           |            |            |
  |  {"heartbeat_interval":  |                         |           |            |            |
  |   41250}                 |                         |           |            |            |
  |                          |                         |           |            |            |
  | [4] Клиент ждёт          |                         |           |            |            |
  |     jitter * interval    |                         |           |            |            |
  |                          |                         |           |            |            |
  |--Heartbeat (op:1)------->|                         |           |            |            |
  |  {"d": null}             |                         |           |            |            |
  |<---Heartbeat ACK (op:11)-|                         |           |            |            |
  |                          |                         |           |            |            |
  |--Identify (op:2)-------->|                         |           |            |            |
  |  {"token": "eyJ...",     |                         |           |            |            |
  |   "intents": 3276799,    |                         |           |            |            |
  |   "shard": [0, 1],       |                         |           |            |            |
  |   "compress": false,     |                         |           |            |            |
  |   "properties": {        |                         |           |            |            |
  |     "os": "windows",     |                         |           |            |            |
  |     "browser": "chrome"  |                         |           |            |            |
  |   }}                     |                         |           |            |            |
  |                          |                         |           |            |            |
  |                     [5] JWT валидация (ES256)       |           |            |            |
  |                     [6] IDENTIFY rate limit check   |           |            |            |
  |                          |--INCR identify:{hash}-->|           |            |            |
  |                          |<--count-----------------|           |            |            |
  |                          |                         |           |            |            |
  |                     [7] Shard validation            |           |            |            |
  |                     [8] Intents validation          |           |            |            |
  |                          |                         |           |            |            |
  |                     [9] Получить гильдии            |           |            |            |
  |                          |--NATS req "guilds.get_user_guilds"->|            |            |
  |                          |<--reply (guild_ids[])---------------|            |            |
  |                          |                         |           |            |            |
  |                     [10] Создать сессию             |           |            |            |
  |                          |--SET gw:session:{sid}-->|           |            |            |
  |                          |--SADD gw:user:{uid}---->|           |            |            |
  |                          |   :sessions {sid}       |           |            |            |
  |                          |                         |           |            |            |
  |                     [11] Подписка на NATS           |           |            |            |
  |                          |--subscribe "guild.{id}.>"---------->|            |            |
  |                          |  (для каждой гильдии)   |           |            |            |
  |                          |                         |           |            |            |
  |                     [12] Обновить presence          |           |            |            |
  |                          |--NATS publish "presence.{uid}.updated"---------->|            |
  |                          |                         |           |            |            |
  |<---Ready (op:0, t:READY)|                         |           |            |            |
  |  {"v": 1,                |                         |           |            |            |
  |   "user": {...},         |                         |           |            |            |
  |   "guilds": [            |                         |           |            |            |
  |     {"id":"111",         |                         |           |            |            |
  |      "unavailable":true} |                         |           |            |            |
  |   ],                     |                         |           |            |            |
  |   "session_id": "abc..", |                         |           |            |            |
  |   "resume_gateway_url":  |                         |           |            |            |
  |    "wss://gw-resume.."   |                         |           |            |            |
  |   "shard": [0, 1]}       |                         |           |            |            |
  |                          |                         |           |            |            |
  |<---GUILD_CREATE (op:0)---|  [13] Lazy guild loading|           |            |            |
  |  {полные данные гильдии} |     (для каждой гильдии)|           |            |            |
  |                          |                         |           |            |            |
  |                     [14] Начало dispatch loop       |           |            |            |
  |<---Events (op:0)---------|  s++ для каждого dispatch|           |            |            |
  |--Heartbeat (op:1)------->|  каждые 41250 мс        |           |            |            |
  |<---Heartbeat ACK (op:11)-|                         |           |            |            |
```

### Пошаговая последовательность

**Шаг 1: WebSocket upgrade**

Клиент подключается к `wss://gateway.example.com/?v=1&encoding=json&compress=zlib-stream`. Axum обрабатывает upgrade:

```rust
// services/gateway/src/routes/ws.rs
async fn ws_handler(ws: WebSocketUpgrade, State(state): State<AppState>) -> Response {
    ws.on_upgrade(move |socket| handle_connection(socket, state))
}
```

**Шаг 2: Origin check**

Проверка заголовка `Origin` при WebSocket handshake — whitelist доверенных доменов.

- **Ошибка**: Origin не в whitelist -> HTTP 403, upgrade отклонён

**Шаг 3: Connection limit check**

Проверка `max_connections` (100 000 per инстанс). Если лимит превышен, новое соединение отклоняется.

- **Ошибка**: лимит -> HTTP 503, upgrade отклонён

**Шаг 4: Hello и Initial Heartbeat**

Сервер немедленно отправляет Hello (op:10) с `heartbeat_interval: 41250` мс. Клиент отправляет первый heartbeat через `heartbeat_interval * random(0..1)` — jitter предотвращает thundering herd при массовом reconnect.

**Шаг 5: JWT валидация при IDENTIFY**

JWT из IDENTIFY payload проверяется:
- Алгоритм: ES256 (ECDSA P-256)
- Public key из env `JWT_PUBLIC_KEY`
- Проверка `exp` (expiry)
- Извлечение `sub` (user_id)

Таймаут: 5 секунд от Hello. Если IDENTIFY не получен -> close 4003 (Not Authenticated).

- **Ошибка**: невалидный JWT -> close 4004 (Authentication Failed), NOT resumable
- **Ошибка**: таймаут -> close 4003

**Шаг 6: IDENTIFY rate limit**

Глобальный лимит: 1000 IDENTIFY per day per token.

```
INCR gateway:identify:{token_hash}:{date}
EXPIRE gateway:identify:{token_hash}:{date} 86400
```

- **Ошибка**: превышен лимит -> close 4009 (Session Timed Out)

**Шаг 7: Shard validation**

Проверка `shard: [shard_id, total_shards]`:
- `shard_id < total_shards`
- Данный Gateway инстанс обслуживает указанный shard

- **Ошибка**: невалидный shard -> close 4010 (Invalid Shard), NOT resumable

**Шаг 8: Intents validation**

Проверка `intents` bitfield:
- Значение не содержит неизвестных битов
- Privileged intents (GUILD_MEMBERS bit 1, GUILD_PRESENCES bit 8, MESSAGE_CONTENT bit 15) допускаются только для одобренных ботов

- **Ошибка**: невалидные intents -> close 4013/4014, NOT resumable

**Шаг 9: Получение списка гильдий**

NATS request к Guilds Service (:3003) для получения guild_ids пользователя (отфильтрованных по shard_id):

```
shard_id = (guild_id >> 22) % total_shards
```

**Шаг 10: Создание сессии**

Сессия регистрируется в DashMap (concurrent HashMap) и Redis:

```
SET gw:session:{session_id} {json_state} EX {heartbeat_timeout * 2}
SADD gw:user:{user_id}:sessions {session_id}
```

Состояние сессии:

```rust
pub struct Session {
    pub session_id: String,
    pub user_id: i64,
    pub shard_id: u16,
    pub seq: AtomicU64,
    pub intents: u32,
    pub guilds: HashSet<i64>,
    pub compression: CompressionType,
    pub tx: mpsc::Sender<GatewayMessage>,
    pub connected_at: Instant,
    pub last_heartbeat: AtomicInstant,
}
```

**Шаг 11: Подписка на NATS**

Для каждой гильдии пользователя:

```rust
client.subscribe(format!("guild.{guild_id}.>")).await?;
```

Wildcard `>` подписывает на все sub-subjects гильдии: сообщения, каналы, участники, роли, typing, voice.

**Шаг 12: Обновление presence**

Публикация начального статуса в NATS: `presence.{user_id}.updated` -> `PresenceUpdated`.

**Шаг 13: READY и Lazy Guild Loading**

Ready отправляется с гильдиями как `unavailable: true`. Затем для каждой гильдии отправляется `GUILD_CREATE` dispatch с полными данными (каналы, роли, участники).

**Шаг 14: Dispatch loop**

Три Tokio task на соединение:
- **Read task**: WebSocket -> decompress -> dispatch opcodes
- **Write task**: mpsc channel -> compress -> WebSocket send
- **Heartbeat task**: проверка heartbeat каждые `heartbeat_interval`

### Кеширование (Redis)

| Ключ | Данные | TTL |
|------|--------|-----|
| `gw:session:{session_id}` | JSON session state | `heartbeat_timeout * 2` |
| `gw:user:{user_id}:sessions` | SET session_ids | нет TTL |
| `gw:shard:{shard_id}:guilds` | SET guild_ids | нет TTL |
| `gw:shard:{shard_id}:pod` | pod_name | 60 сек |
| `gw:identify:{token_hash}:{date}` | counter | 24 часа |

### Обработка ошибок при disconnect

```
Close code      Resumable?    Действие клиента
──────────      ──────────    ────────────────
4000            Да            Reconnect + Resume (op:6)
4001            Да            Reconnect + Resume
4003            Да            Reconnect + Resume
4004            Нет           Новый Identify (проверить токен)
4007            Да            Reconnect + Resume
4008            Да            Подождать + Reconnect + Resume
4009            Да            Reconnect + новый Identify
4010            Нет           Исправить shard конфигурацию
4013            Нет           Исправить intents
1000/1001       Нет           Сессия инвалидирована, новый Identify
```

### Resume Flow

```
Client                  WS Gateway (:4000)           Redis          NATS JS
  |                          |                         |               |
  |--wss://resume-gw?v=1--->|                         |               |
  |                          |                         |               |
  |<---Hello (op:10)---------|                         |               |
  |                          |                         |               |
  |--Heartbeat (op:1)------->|                         |               |
  |<---Heartbeat ACK (op:11)-|                         |               |
  |                          |                         |               |
  |--Resume (op:6)---------->|                         |               |
  |  {"token": "eyJ...",     |                         |               |
  |   "session_id": "abc",   |                         |               |
  |   "seq": 1337}           |                         |               |
  |                          |                         |               |
  |                     [1] JWT валидация               |               |
  |                     [2] Восстановить сессию         |               |
  |                          |--GET gw:session:{sid}-->|               |
  |                          |<--session_state---------|               |
  |                          |                         |               |
  |                     [3] Replay пропущенных          |               |
  |                          |--get_or_create_consumer------------>|   |
  |                          |  start_sequence = 1338  |               |
  |                          |<--missed events-----------------------|
  |                          |                         |               |
  |<---Missed events (op:0)--|  s=1338, 1339, ...      |               |
  |<---Resumed (op:0)--------|  t:RESUMED              |               |
  |                          |                         |               |
  |                     [4] Продолжение dispatch loop   |               |
```

---

## 6. Загрузка файла

Поток загрузки файла использует presigned URL для прямой загрузки в MinIO, минуя API Gateway для тяжёлого трафика.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| API Gateway | 3000 | JWT проверка, проксирование запроса presigned URL |
| Media Service | 3005 | Генерация presigned URL, обработка после загрузки |
| MinIO (S3) | — | Хранилище файлов, приём прямой загрузки |
| Messages Service | 3004 | Привязка attachment к сообщению |

### Диаграмма последовательности

```
Client          API GW         Media Service      MinIO (S3)     Messages
  |             (:3000)        (:3005)                            (:3004)
  |                |               |                  |              |
  |                |               |                  |              |
  | === ФАЗ А: Получение presigned URL ===            |              |
  |                |               |                  |              |
  |--POST /media/upload/presign--->|                  |              |
  |  Authorization: Bearer <JWT>   |                  |              |
  |  {"filename": "photo.jpg",    |                  |              |
  |   "content_type":"image/jpeg", |                  |              |
  |   "size": 2048576}            |                  |              |
  |                |               |                  |              |
  |           [1] JWT + Rate limit |                  |              |
  |                |               |                  |              |
  |                |--NATS req---->|                  |              |
  |                | "media.presign"                  |              |
  |                |               |                  |              |
  |                |          [2] Валидация           |              |
  |                |          [3] MIME проверка       |              |
  |                |          [4] Snowflake ID        |              |
  |                |               |                  |              |
  |                |          [5] Генерация presigned |              |
  |                |               |--PUT presign---->|              |
  |                |               |<--presigned URL--|              |
  |                |               |                  |              |
  |                |<--reply-------|                  |              |
  |<--HTTP 200-----|               |                  |              |
  |  {"upload_url":                |                  |              |
  |   "https://s3.../presigned",   |                  |              |
  |   "upload_id": "snowflake",    |                  |              |
  |   "expires_in": 300}           |                  |              |
  |                |               |                  |              |
  |                |               |                  |              |
  | === ФАЗА Б: Прямая загрузка в MinIO ===          |              |
  |                |               |                  |              |
  |--PUT presigned URL (binary data)----------------->|              |
  |<--HTTP 200 (OK)------------------------------------              |
  |                |               |                  |              |
  |                |               |                  |              |
  | === ФАЗА В: Подтверждение и обработка ===         |              |
  |                |               |                  |              |
  |--POST /media/upload/complete-->|                  |              |
  |  {"upload_id": "snowflake"}    |                  |              |
  |                |               |                  |              |
  |                |--NATS req---->|                  |              |
  |                | "media.       |                  |              |
  |                |  complete"    |                  |              |
  |                |               |                  |              |
  |                |          [6] Проверить файл      |              |
  |                |               |--HEAD object---->|              |
  |                |               |<--metadata-------|              |
  |                |               |                  |              |
  |                |          [7] Magic bytes (infer)  |              |
  |                |          [8] EXIF strip (img-parts)|             |
  |                |          [9] Thumbnails (image crate)            |
  |                |          [10] Сохранить metadata   |             |
  |                |               |                  |              |
  |                |          [11] Publish event       |              |
  |                |               |--NATS JS "media.uploaded"------>|
  |                |               |                  |              |
  |                |<--reply-------|                  |              |
  |<--HTTP 200-----|               |                  |              |
  |  {"attachment": {              |                  |              |
  |    "id": "snowflake",          |                  |              |
  |    "url": "https://cdn/..",    |                  |              |
  |    "proxy_url": "...",         |                  |              |
  |    "filename": "photo.jpg",    |                  |              |
  |    "size": 2048576,            |                  |              |
  |    "width": 1920,              |                  |              |
  |    "height": 1080}}            |                  |              |
```

### Пошаговая последовательность

**Шаг 1: JWT и Rate limit (API Gateway)**

Rate limit для загрузки файлов: 10 файлов / 1 минута per user.

- **Redis ключ**: `rate:file_upload:{user_id}`
- **Ошибка**: превышен лимит -> `429 RATE_LIMITED`

**Шаг 2: Валидация (Media Service)**

- `filename`: непустой, без path traversal (`..`, `/`, `\`)
- `content_type`: whitelist MIME-типов
- `size`: максимум 25 MiB (26 214 400 bytes)

- **Ошибка**: невалидные данные -> `400 BAD_REQUEST`
- **Ошибка**: файл слишком большой -> `413 PAYLOAD_TOO_LARGE`

**Шаг 3: Проверка MIME-типа**

Whitelist допустимых типов:
- Изображения: `image/jpeg`, `image/png`, `image/gif`, `image/webp`
- Видео: `video/mp4`, `video/webm`
- Аудио: `audio/mpeg`, `audio/ogg`, `audio/wav`
- Документы: `application/pdf`, `text/plain`

- **Ошибка**: недопустимый MIME -> `400 INVALID_CONTENT_TYPE`

**Шаг 4: Генерация Snowflake ID**

`upload_id` (Snowflake) используется как уникальный идентификатор файла и часть пути в MinIO.

**Шаг 5: Генерация presigned URL**

Media Service генерирует presigned PUT URL через `aws-sdk-s3` crate:
- Bucket: `attachments`
- Key: `{guild_id}/{channel_id}/{upload_id}/{filename}`
- Expiry: 300 секунд (5 минут)
- Content-Type: заданный клиентом
- Max size: 25 MiB

**Шаг 6-9: Обработка после загрузки (Media Service)**

После подтверждения загрузки клиентом:

1. **HEAD object** в MinIO — проверить что файл реально загружен и размер совпадает
2. **Magic bytes** (infer crate) — проверить реальный MIME по содержимому, а не по расширению
3. **EXIF strip** (img-parts crate) — удалить метаданные из изображений (GPS координаты, серийный номер камеры)
4. **Thumbnails** (image crate) — для изображений создать миниатюры (resize)
5. Определить `width` и `height` для изображений и видео

- **Ошибка**: файл не найден в MinIO -> `404 NOT_FOUND`
- **Ошибка**: magic bytes не совпадают с заявленным MIME -> удалить файл, `400 INVALID_CONTENT`

**Шаг 10: Сохранение метаданных**

Media Service сохраняет метаданные attachment. При отправке сообщения с attachment, Messages Service использует `upload_id` для привязки.

**Шаг 11: Публикация события**

Subject: `media.uploaded`
Event: `Event::MediaUploaded(MediaPayload)`

### Кеширование (Redis)

| Ключ | Данные | TTL |
|------|--------|-----|
| `upload:{upload_id}` | JSON upload state | 5 мин |
| `rate:file_upload:{user_id}` | ZSET timestamps | 1 мин |

### Безопасность

- Presigned URL ограничен по времени (5 минут) и размеру
- Клиент загружает напрямую в MinIO, API Gateway не проксирует тяжёлый трафик
- Magic bytes проверка предотвращает загрузку вредоносных файлов под видом изображений
- EXIF strip удаляет потенциально приватные метаданные
- CDN URL (`proxy_url`) проксируется через отдельный домен без cookies

---

## 7. Поиск сообщений

Поток полнотекстового поиска по сообщениям через Meilisearch.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| API Gateway | 3000 | JWT проверка, проксирование |
| Search Service | 3007 | Запрос к Meilisearch, фильтрация по правам |
| Guilds Service | 3003 | Проверка прав доступа к каналам |
| Meilisearch | — | Полнотекстовый поиск |

### Диаграмма последовательности

```
Client          API GW         Search          Guilds         Meilisearch
  |             (:3000)        (:3007)         (:3003)
  |                |               |              |              |
  |--POST /guilds/:id/search----->|              |              |
  |  Authorization: Bearer <JWT>   |              |              |
  |  {"content": "hello",         |              |              |
  |   "author_id": "123",         |              |              |
  |   "channel_id": "456",        |              |              |
  |   "has": "file",              |              |              |
  |   "limit": 25}                |              |              |
  |                |               |              |              |
  |           [1] JWT + Rate limit |              |              |
  |                |               |              |              |
  |                |--NATS req---->|              |              |
  |                | "search.      |              |              |
  |                |  messages"    |              |              |
  |                |               |              |              |
  |                |          [2] Валидация        |              |
  |                |               |              |              |
  |                |          [3] Получить доступные каналы       |
  |                |               |--NATS req--->|              |
  |                |               | "guilds.     |              |
  |                |               |  get_visible_|              |
  |                |               |  channels"   |              |
  |                |               |<--reply------|              |
  |                |               | [channel_ids]|              |
  |                |               |              |              |
  |                |          [4] Построить фильтр |              |
  |                |               |              |              |
  |                |          [5] Запрос к Meilisearch            |
  |                |               |--search query-------------->|
  |                |               |  filters:                   |
  |                |               |   channel_id IN [...]       |
  |                |               |   guild_id = {guild_id}     |
  |                |               |   author_id = 123 (if set)  |
  |                |               |                             |
  |                |               |<--results (hits[], total)---|
  |                |               |              |              |
  |                |          [6] Обогащение данных|              |
  |                |               |              |              |
  |                |<--reply-------|              |              |
  |<--HTTP 200-----|               |              |              |
  |  {"messages": [...],           |              |              |
  |   "total_results": 42}        |              |              |
```

### Пошаговая последовательность

**Шаг 1: JWT и Rate limit (API Gateway)**

Стандартная проверка. Поиск доступен только авторизованным пользователям -- участникам гильдии.

**Шаг 2: Валидация (Search Service)**

- `content`: непустая строка для поиска
- `limit`: 1-25 (default 25)
- `channel_id`: опциональный фильтр
- `author_id`: опциональный фильтр
- `has`: опциональный (`file`, `embed`, `link`, `video`, `image`)

**Шаг 3: Получение доступных каналов**

Search Service запрашивает у Guilds Service (:3003) список каналов, где пользователь имеет `VIEW_CHANNEL` (1 << 10) + `READ_MESSAGE_HISTORY` (1 << 16). Это предотвращает утечку данных из каналов, к которым у пользователя нет доступа.

**Шаг 4: Построение фильтра**

Meilisearch фильтр строится из:
- `guild_id = {guild_id}` (обязательно)
- `channel_id IN [{visible_channels}]` (ограничение по правам)
- Дополнительные фильтры от клиента (author_id, channel_id, has)

Если клиент указал `channel_id`, он проверяется на вхождение в `visible_channels`.

**Шаг 5: Запрос к Meilisearch**

Используется `meilisearch-sdk` crate:

```rust
let results = client
    .index("messages")
    .search()
    .with_query(&query.content)
    .with_filter(&filter_string)
    .with_limit(query.limit)
    .with_sort(&["id:desc"])
    .execute::<MessageSearchResult>()
    .await?;
```

- **Ошибка**: Meilisearch недоступен -> `503 SERVICE_UNAVAILABLE`

**Шаг 6: Обогащение данных**

Результаты Meilisearch содержат только индексированные поля. Search Service обогащает каждый результат данными автора (username, avatar) через NATS request к Users Service (:3002) или Redis кеш.

### Асинхронная индексация (фоновый процесс)

Search Service подписан на NATS JetStream и индексирует сообщения асинхронно:

```
Messages Service                  NATS JetStream              Search Service (:3007)
      |                                |                             |
      |--publish "messages.{gid}.      |                             |
      |  {cid}.created"                |                             |
      |                                |--deliver------------------->|
      |                                |                             |
      |                                |                   [1] Десериализация
      |                                |                   [2] Добавить в Meilisearch
      |                                |                   [3] ACK
      |                                |<--ack----------------------|
      |                                |                             |
      |--publish "messages.{gid}.      |                             |
      |  {cid}.updated"                |                             |
      |                                |--deliver------------------->|
      |                                |                   [4] Обновить в Meilisearch
      |                                |<--ack----------------------|
      |                                |                             |
      |--publish "messages.{gid}.      |                             |
      |  {cid}.deleted"                |                             |
      |                                |--deliver------------------->|
      |                                |                   [5] Удалить из Meilisearch
      |                                |<--ack----------------------|
```

### Кеширование (Redis)

| Ключ | Данные | TTL |
|------|--------|-----|
| `search:visible_channels:{guild_id}:{user_id}` | SET channel_ids | 2 мин |
| `user:{user_id}:profile` | JSON (username, avatar) | 5 мин |

---

## 8. Вступление в сервер по инвайту

Полный поток принятия приглашения и вступления в гильдию.

### Участвующие сервисы

| Сервис | Порт | Роль |
|--------|------|------|
| API Gateway | 3000 | JWT проверка, rate limiting |
| Guilds Service | 3003 | Валидация инвайта, добавление участника |
| WS Gateway | 4000 | Доставка GUILD_CREATE и MEMBER_ADD событий |
| Notification Service | 3006 | Welcome-уведомление |
| Presence Service | 3010 | Обновление guild list для presence |

### Диаграмма последовательности

```
Client          API GW         Guilds          NATS JS       WS Gateway    Notifications
  |             (:3000)        (:3003)                        (:4000)       (:3006)
  |                |               |              |              |              |
  | === ФАЗА А: Просмотр инвайта (необязательно) ===            |              |
  |                |               |              |              |              |
  |--GET /invites/aBcDeFgH-------->|              |              |              |
  |                |--NATS req---->|              |              |              |
  |                | "guilds.      |              |              |              |
  |                |  get_invite"  |              |              |              |
  |                |               |              |              |              |
  |                |          [A1] Проверить Redis кеш           |              |
  |                |          [A2] SELECT invite + guild info     |              |
  |                |               |              |              |              |
  |                |<--reply-------|              |              |              |
  |<--HTTP 200-----|               |              |              |              |
  |  {"code":"aBcDeFgH",          |              |              |              |
  |   "guild":{name, icon,        |              |              |              |
  |     member_count},             |              |              |              |
  |   "channel":{name, type},     |              |              |              |
  |   "expires_at":"..."}         |              |              |              |
  |                |               |              |              |              |
  | === ФАЗА Б: Принятие инвайта ===             |              |              |
  |                |               |              |              |              |
  |--POST /invites/aBcDeFgH/accept>|              |              |              |
  |  Authorization: Bearer <JWT>   |              |              |              |
  |                |               |              |              |              |
  |           [1] JWT + Rate limit |              |              |              |
  |                |               |              |              |              |
  |                |--NATS req---->|              |              |              |
  |                | "guilds.      |              |              |              |
  |                |  invite_accept"              |              |              |
  |                |               |              |              |              |
  |                |          [2] Валидация инвайта|              |              |
  |                |          [3] Проверка бана    |              |              |
  |                |          [4] Verification lvl |              |              |
  |                |          [5] Лимит серверов   |              |              |
  |                |          [6] Anti-raid check  |              |              |
  |                |               |              |              |              |
  |                |          [7] INSERT member    |              |              |
  |                |          [8] Increment uses   |              |              |
  |                |          [9] Update count     |              |              |
  |                |               |              |              |              |
  |                |          [10] Publish events   |              |              |
  |                |               |--publish------>|              |              |
  |                |               | "guild.{gid}. |              |              |
  |                |               |  member.joined"|             |              |
  |                |               |              |              |              |
  |                |<--reply-------|              |              |              |
  |<--HTTP 200-----|               |              |              |              |
  |  {"guild_id":"...",            |              |              |              |
  |   "user_id":"..."}            |              |              |              |
  |                |               |              |              |              |
  |                |               |              |--deliver---->|              |
  |                |               |              |         [11] Доставить     |
  |                |               |              |         GUILD_MEMBER_ADD   |
  |                |               |              |         всем участникам    |
  |                |               |              |              |              |
  |                |               |              |         [12] Подписать     |
  |                |               |              |         нового участника   |
  |                |               |              |         на guild.{gid}.>   |
  |                |               |              |              |              |
  |                |               |              |         [13] Отправить     |
  |                |               |              |         GUILD_CREATE       |
  |                |               |              |         новому участнику   |
  |                |               |              |              |              |
  |                |               |              |--deliver--------------->|  |
  |                |               |              |              |    [14] Send |
  |                |               |              |              |    welcome   |
  |                |               |              |              |    notif.    |
```

### Пошаговая последовательность

**Шаг 1: JWT и Rate limit (API Gateway)**

Rate limit: 5 запросов / 10 минут per user (POST /invites/:code/accept).

- **Redis ключ**: `rate:invite_accept:{user_id}`
- **Ошибка**: превышен лимит -> `429 RATE_LIMITED`

**Шаг 2: Валидация инвайта (Guilds Service)**

```sql
SELECT code, guild_id, channel_id, inviter_id, max_uses, uses,
       max_age, temporary, expires_at, created_at
FROM invites
WHERE code = $1;
```

Проверки:
- Инвайт существует
- `expires_at IS NULL OR expires_at > NOW()` (не истёк)
- `max_uses = 0 OR uses < max_uses` (не исчерпан)
- Пользователь ещё не участник гильдии

- **Ошибка**: инвайт не найден -> `404 INVITE_NOT_FOUND`
- **Ошибка**: инвайт истёк/исчерпан -> `410 INVITE_EXPIRED`
- **Ошибка**: уже участник -> `409 ALREADY_MEMBER`

**Шаг 3: Проверка бана**

```sql
SELECT EXISTS(SELECT 1 FROM bans WHERE guild_id = $1 AND user_id = $2) AS banned;
```

- **Ошибка**: забанен -> `403 FORBIDDEN` (без раскрытия причины бана)

**Шаг 4: Проверка verification level**

Гильдия может требовать определённый уровень верификации аккаунта:

| Level | Требование |
|-------|-----------|
| 0 | Нет |
| 1 | Подтверждённый email |
| 2 | Аккаунт старше 5 минут |
| 3 | Участник платформы > 10 минут |
| 4 | Подтверждённый телефон |

- **Ошибка**: уровень не выполнен -> `403 VERIFICATION_LEVEL_TOO_HIGH`

**Шаг 5: Проверка лимита серверов**

Пользователь может состоять максимум в 100 серверах.

- **Ошибка**: лимит -> `400 MAX_GUILDS`

**Шаг 6: Anti-raid проверка**

Guilds Service отслеживает частоту вступлений:

```
INCR guild:{guild_id}:join_rate:{10sec_bucket}
EXPIRE guild:{guild_id}:join_rate:{10sec_bucket} 30
```

Если > 10 вступлений за 10 секунд:
1. Временно заблокировать приём (60 секунд)
2. Публикация NATS `moderation.{guild_id}.automod.action` (raid detection)
3. Вернуть клиенту `429 RATE_LIMITED`

**Шаг 7: Добавление участника**

```sql
INSERT INTO guild_members (user_id, guild_id, pending)
VALUES ($1, $2, $3);
-- pending = true если membership screening включен
```

Также автоматически назначается роль `@everyone`:

```sql
INSERT INTO member_roles (user_id, guild_id, role_id)
VALUES ($1, $2, $3); -- role_id = guild_id (@everyone)
```

- **Ошибка**: PostgreSQL недоступен -> `500 INTERNAL_ERROR`

**Шаг 8: Инкремент использований**

```sql
UPDATE invites SET uses = uses + 1 WHERE code = $1;
```

**Шаг 9: Обновление member_count**

```sql
UPDATE guilds SET member_count = member_count + 1 WHERE id = $1;
```

Инвалидация Redis: `DEL guild:{guild_id}:member_count`

**Шаг 10: Публикация NATS событий**

Guilds Service публикует в JetStream:
- Subject: `guild.{guild_id}.member.joined`
- Payload: `{ guild_id, user_id }`
- Event: `Event::MemberJoined(MemberPayload)`

Дополнительно, запись в audit_log если инвайт создан с `temporary: true`.

**Шаг 11: Доставка GUILD_MEMBER_ADD (WS Gateway)**

WS Gateway получает событие `guild.{guild_id}.member.joined` и доставляет `GUILD_MEMBER_ADD` всем подключённым клиентам в гильдии, у которых включён intent `GUILD_MEMBERS` (бит 1, privileged).

```json
{
    "op": 0,
    "s": 43,
    "t": "GUILD_MEMBER_ADD",
    "d": {
        "guild_id": "1234567890123456",
        "user": { "id": "...", "username": "john", "avatar": "..." },
        "roles": ["1234567890123456"],
        "joined_at": "2026-02-22T12:00:00Z",
        "pending": false
    }
}
```

**Шаг 12: Подписка нового участника на NATS**

Если новый участник уже подключён к WS Gateway, его сессия подписывается на события новой гильдии:

```rust
client.subscribe(format!("guild.{new_guild_id}.>")).await?;
session.guilds.insert(new_guild_id);
```

**Шаг 13: GUILD_CREATE для нового участника**

Новому участнику через WebSocket отправляется `GUILD_CREATE` с полными данными гильдии (каналы, роли, участники):

```json
{
    "op": 0,
    "s": 44,
    "t": "GUILD_CREATE",
    "d": {
        "id": "1234567890123456",
        "name": "My Server",
        "owner_id": "...",
        "channels": [...],
        "roles": [...],
        "members": [...],
        "member_count": 151
    }
}
```

**Шаг 14: Welcome уведомление (Notification Service)**

Notification Service (:3006) подписан на `guild.{guild_id}.member.joined`. При получении:
1. Проверяет настройки system_channel гильдии
2. Если system_channel настроен — создаёт системное сообщение (type 7, UserJoin)
3. Может отправить push-уведомление владельцу сервера

### Кеширование (Redis)

| Ключ | Данные | TTL | Операция |
|------|--------|-----|----------|
| `invite:{code}` | JSON invite data | до expires_at | SET при создании |
| `guild:{id}:bans` | SET user_ids | 5 мин | Инвалидируется при бане/разбане |
| `guild:{id}:member_count` | integer | 1 мин | DEL при вступлении/уходе |
| `guild:{id}:member:{user_id}` | JSON member data | 5 мин | SET при создании |
| `guild:{id}:join_rate:{bucket}` | counter | 30 сек | INCR при каждом вступлении |
| `rate:invite_accept:{user_id}` | ZSET timestamps | 10 мин | При каждой попытке |

### Обработка ошибок (сводка)

| Шаг | Ошибка | HTTP код | Восстановление |
|-----|--------|----------|----------------|
| 1 | Rate limit | 429 | Retry после Retry-After |
| 2 | Инвайт не найден | 404 | Запросить новый инвайт |
| 2 | Инвайт истёк | 410 | Запросить новый инвайт |
| 2 | Уже участник | 409 | Идемпотентно (можно игнорировать) |
| 3 | Забанен | 403 | Невозможно вступить |
| 4 | Verification level | 403 | Повысить уровень верификации |
| 5 | Лимит серверов | 400 | Покинуть другой сервер |
| 6 | Anti-raid | 429 | Подождать и повторить |
| 7 | БД ошибка | 500 | Повторить запрос |
| 10 | NATS недоступен | 500 | Участник добавлен, события доставятся позже |

---

## Приложение: Карта всех NATS подписок по сервисам

Сводная таблица кто на что подписан.

### Publishers (кто публикует)

| Сервис | Subjects |
|--------|---------|
| Auth (:3001) | `auth.user.registered`, `auth.user.login`, `auth.user.logout`, `auth.email.verified`, `auth.password.changed` |
| Users (:3002) | `user.{user_id}.updated`, `user.deleted` |
| Guilds (:3003) | `guild.created`, `guild.{guild_id}.updated`, `guild.{guild_id}.deleted`, `guild.{guild_id}.channel.*`, `guild.{guild_id}.member.*`, `guild.{guild_id}.role.*` |
| Messages (:3004) | `messages.{guild_id}.{channel_id}.created/updated/deleted/bulk_deleted/reaction_*/pins_update` |
| Media (:3005) | `media.uploaded`, `media.deleted` |
| Presence (:3010) | `presence.{user_id}.updated`, `typing.{guild_id}.{channel_id}` |
| Voice (:3008) | `voice.{guild_id}.state_updated` |
| Moderation (:3009) | `moderation.{guild_id}.automod.action` |

### Subscribers (кто слушает)

| Сервис | Подписки | Действие |
|--------|---------|----------|
| WS Gateway (:4000) | `guild.{guild_id}.>` (для каждой гильдии шарда) | Fan-out -> WebSocket клиенты |
| WS Gateway (:4000) | `auth.user.logout` | Инвалидация сессии |
| Users (:3002) | `auth.user.registered` | Создание профиля |
| Users (:3002) | `auth.email.verified` | Обновление verified статуса |
| Messages (:3004) | `guild.channel.deleted` | Каскадное удаление сообщений |
| Messages (:3004) | `guild.member.banned` | Удаление реакций забаненного |
| Messages (:3004) | `user.deleted` | Анонимизация сообщений |
| Search (:3007) | `messages.*.*.created/updated/deleted` | Индексация/обновление/удаление в Meilisearch |
| Notifications (:3006) | `messages.*.*.created` | Push-уведомления |
| Notifications (:3006) | `guild.*.member.joined` | Welcome-сообщения |
| Notifications (:3006) | `user.*.friend.request` | Уведомление о заявке |
| Presence (:3010) | `auth.user.login` | Установить online статус |
| Presence (:3010) | `auth.user.logout` | Установить offline статус |
| Guilds (:3003) | `user.deleted` | Удаление пользователя из гильдий |
