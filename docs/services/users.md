# User Service

Управление профилями пользователей, настройками, списком друзей и блокировками.

Порт: `3002`
Путь: `services/users/`
БД: PostgreSQL
Кеш: Redis

## Источники

- [OWASP User Privacy Protection Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/User_Privacy_Protection_Cheat_Sheet.html)
- [GDPR — право на удаление данных](https://gdpr-info.eu/art-17-gdpr/)
- [Discord Developer Docs — User Object](https://discord.com/developers/docs/resources/user)
- [OWASP Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)
- [crate: validator](https://docs.rs/validator/latest/validator/)
- [crate: infer](https://docs.rs/infer/latest/infer/) — MIME detection по magic bytes
- [crate: aws-sdk-s3](https://docs.rs/aws-sdk-s3/latest/aws_sdk_s3/) — MinIO/S3 клиент

---

## Структура сервиса

```
services/users/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── profile.rs
│   │   ├── settings.rs
│   │   ├── friends.rs
│   │   └── blocks.rs
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── profile.rs
│   │   ├── avatar.rs
│   │   ├── friends.rs
│   │   └── blocks.rs
│   ├── models/
│   │   ├── mod.rs
│   │   ├── user.rs
│   │   ├── user_settings.rs
│   │   ├── friendship.rs
│   │   └── block.rs
│   ├── middleware/
│   │   └── mod.rs
│   └── events/
│       ├── mod.rs
│       ├── publisher.rs
│       └── subscriber.rs
├── migrations/
│   ├── 001_create_users.sql
│   ├── 002_create_user_settings.sql
│   ├── 003_create_friendships.sql
│   └── 004_create_blocks.sql
├── tests/
├── Cargo.toml
└── Dockerfile
```

---

## Зависимости (Cargo.toml)

```toml
[dependencies]
axum = { workspace = true }
axum-extra = { version = "0.10", features = ["multipart"] }
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
async-nats = { workspace = true }
infer = "0.16"              # MIME detection по magic bytes
aws-sdk-s3 = "1"            # MinIO presigned URLs
deadpool-redis = "0.18"     # Redis connection pool
dotenvy = "0.15"            # .env loading
config = "0.15"             # typed env config

# internal crates
common = { path = "../../crates/common" }
snowflake = { path = "../../crates/snowflake" }
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
| `MINIO_ENDPOINT` | да | — | MinIO/S3 endpoint |
| `MINIO_ACCESS_KEY` | да | — | MinIO access key |
| `MINIO_SECRET_KEY` | да | — | MinIO secret key |
| `MINIO_BUCKET_AVATARS` | нет | `avatars` | Bucket для аватаров |
| `MINIO_BUCKET_BANNERS` | нет | `banners` | Bucket для баннеров |
| `CDN_BASE_URL` | да | — | Базовый URL CDN для медиа |
| `MAX_AVATAR_SIZE` | нет | `8388608` | Макс. размер аватара (bytes, 8MB) |
| `SERVICE_PORT` | нет | `3002` | Порт сервиса |
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
| 401 | `UNAUTHORIZED` | Нет или невалидный JWT |
| 403 | `FORBIDDEN` | Нет доступа к ресурсу |
| 404 | `USER_NOT_FOUND` | Пользователь не найден |
| 409 | `ALREADY_FRIENDS` | Уже друзья / запрос уже отправлен |
| 409 | `USERNAME_TAKEN` | Username уже занят |
| 413 | `FILE_TOO_LARGE` | Файл превышает лимит |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Неподдерживаемый формат файла |
| 422 | `VALIDATION_ERROR` | Невалидные поля |
| 429 | `RATE_LIMITED` | Превышен лимит запросов |

---

## Схема базы данных

```sql
-- migrations/001_create_users.sql
CREATE TABLE users (
    id              BIGINT PRIMARY KEY,            -- Snowflake ID
    username        VARCHAR(32) UNIQUE NOT NULL,
    display_name    VARCHAR(64),
    avatar_hash     VARCHAR(64),                   -- hash файла в MinIO
    banner_hash     VARCHAR(64),
    bio             VARCHAR(512),
    status          SMALLINT NOT NULL DEFAULT 0,   -- 0=offline, 1=online, 2=idle, 3=dnd, 4=invisible
    custom_status   VARCHAR(128),
    flags           BIGINT NOT NULL DEFAULT 0,     -- битовые флаги (staff, premium, etc.)
    locale          VARCHAR(8) DEFAULT 'en',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ                        -- soft delete (GDPR, 30 дней до hard delete)
);

CREATE UNIQUE INDEX idx_users_username_lower ON users (LOWER(username)) WHERE deleted_at IS NULL;

-- migrations/002_create_user_settings.sql
CREATE TABLE user_settings (
    user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    theme                   VARCHAR(16) NOT NULL DEFAULT 'dark',
    message_display         VARCHAR(16) NOT NULL DEFAULT 'cozy',     -- cozy, compact
    notification_level      SMALLINT NOT NULL DEFAULT 0,             -- 0=all, 1=mentions, 2=none
    dm_from_server_members  BOOLEAN NOT NULL DEFAULT TRUE,
    dm_from_everyone        BOOLEAN NOT NULL DEFAULT FALSE,
    explicit_content_filter SMALLINT NOT NULL DEFAULT 1,             -- 0=off, 1=recommended, 2=strict
    developer_mode          BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- migrations/003_create_friendships.sql
CREATE TYPE friendship_status AS ENUM ('pending', 'accepted');

CREATE TABLE friendships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          friendship_status NOT NULL DEFAULT 'pending',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    UNIQUE (requester_id, addressee_id),
    CHECK (requester_id != addressee_id)
);

CREATE INDEX idx_friendships_requester ON friendships (requester_id);
CREATE INDEX idx_friendships_addressee ON friendships (addressee_id);

-- migrations/004_create_blocks.sql
CREATE TABLE blocks (
    blocker_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id != blocked_id)
);
```

---

## API Endpoints

### GET /users/@me

Текущий пользователь (по JWT).

**Response 200:**
```json
{
    "id": "1234567890123456",
    "username": "john",
    "display_name": "John Doe",
    "avatar_url": "https://cdn.example.com/avatars/1234567890123456/a1b2c3d4.webp",
    "bio": "Hello!",
    "status": "online",
    "custom_status": "Working on something cool",
    "flags": 0,
    "locale": "en",
    "created_at": "2025-01-15T10:00:00Z"
}
```

---

### PATCH /users/@me

Обновление профиля.

**Request:**
```json
{
    "display_name": "John Updated",
    "bio": "New bio"
}
```

**Валидация:**
- `username`: 2-32 символа, alphanumeric + `_` + `.`, уникальный (case-insensitive)
- `display_name`: 1-64 символа
- `bio`: до 512 символов
- `custom_status`: до 128 символов

**Логика:**
1. Валидация полей
2. Если `username` меняется: проверить уникальность, rate limit (2 смены / 1 час)
3. Обновить в БД
4. Инвалидировать кеш Redis
5. Публикация NATS `user.updated`

---

### PUT /users/@me/avatar

Загрузка аватара.

**Request:** multipart/form-data, поле `avatar` (image/png, image/jpeg, image/webp, image/gif)

**Логика:**
1. Проверить MIME-тип (magic bytes через `infer` crate, не расширение)
2. Проверить размер (макс. 8MB)
3. Сгенерировать presigned URL → MinIO
4. Загрузить оригинал
5. NATS → Media Service для ресайза (128x128, 256x256, 1024x1024)
6. Сохранить `avatar_hash` в users
7. Вернуть URL

---

### GET /users/:id

Профиль другого пользователя (публичные поля).

**Response 200:** Те же поля, что `@me`, но без settings и приватных данных.

---

### GET /users/@me/settings

**Response 200:**
```json
{
    "theme": "dark",
    "message_display": "cozy",
    "notification_level": 0,
    "dm_from_server_members": true,
    "dm_from_everyone": false,
    "explicit_content_filter": 1,
    "developer_mode": false
}
```

---

### PATCH /users/@me/settings

**Request:**
```json
{
    "theme": "light",
    "notification_level": 1
}
```

---

### POST /users/@me/friends

Отправить запрос в друзья.

**Request:**
```json
{
    "username": "jane"
}
```

**Response 200:** `{ "status": "pending" }`
**Response 404:** `{ "code": "USER_NOT_FOUND", "message": "User not found" }`
**Response 409:** `{ "code": "ALREADY_FRIENDS", "message": "Already friends or request already sent" }`

**Логика:**
1. Найти пользователя по username
2. Проверить: не заблокирован ли? не друзья ли уже?
3. Проверить: нет ли уже pending запроса?
4. Создать запись `friendships` (status = pending)
5. NATS → Notification Service (push уведомление addressee)
6. NATS `user.friend.request`

---

### PUT /users/@me/friends/:user_id/accept

Принять запрос в друзья.

**Логика:**
1. Найти pending запрос где `addressee_id = @me`
2. Обновить `status = accepted`, `accepted_at = NOW()`
3. NATS `user.friend.accepted`

---

### DELETE /users/@me/friends/:user_id

Удалить из друзей или отклонить запрос.

---

### GET /users/@me/friends

Список друзей.

**Response 200:**
```json
{
    "friends": [
        {
            "id": "1234567890123457",
            "username": "jane",
            "display_name": "Jane",
            "avatar_url": "...",
            "status": "online"
        }
    ],
    "pending_incoming": [...],
    "pending_outgoing": [...]
}
```

---

### PUT /users/@me/blocks/:user_id

Заблокировать пользователя.

**Логика:**
1. Создать запись в `blocks`
2. Если были друзья — удалить дружбу
3. NATS `user.blocked`

---

### DELETE /users/@me/blocks/:user_id

Разблокировать.

---

### GET /users/@me/blocks

Список заблокированных пользователей.

**Response 200:**
```json
[
    {
        "id": "1234567890123458",
        "username": "blocked_user",
        "display_name": "Blocked",
        "avatar_url": "...",
        "created_at": "2025-01-15T10:00:00Z"
    }
]
```

---

### DELETE /users/@me

Удаление аккаунта (GDPR Article 17 — право на удаление).

**Request:**
```json
{
    "password": "currentPassword123"
}
```

**Response 204:** No content

**Логика:**
1. Потребовать повторный ввод пароля (re-authentication)
2. Установить `deleted_at = NOW()` (soft delete)
3. Анонимизировать: `username → "Deleted User #<hash>"`, обнулить `avatar_hash`, `bio`, `display_name`, `banner_hash`
4. Удалить все дружбы и блокировки
5. Инвалидировать весь кеш
6. NATS `user.deleted` → каскадная очистка в Auth, Guild, Message сервисах
7. Через 30 дней: Tokio cron → hard delete (`DELETE FROM users WHERE deleted_at < NOW() - INTERVAL '30 days'`)
8. Сообщения остаются, но с анонимизированным автором

---

## Rate Limiting

| Endpoint | Лимит | Окно | Ключ |
|----------|-------|------|------|
| PATCH /users/@me | 5 req | 1 мин | user_id |
| PATCH /users/@me (username change) | 2 req | 1 час | user_id |
| PUT /users/@me/avatar | 2 req | 10 мин | user_id |
| POST /users/@me/friends | 10 req | 1 час | user_id |
| PUT /users/@me/blocks/:id | 10 req | 1 мин | user_id |
| GET /users/:id | 30 req | 1 мин | user_id |
| DELETE /users/@me | 1 req | 24 часа | user_id |

---

## Лимиты

| Ресурс | Лимит |
|--------|-------|
| Друзей | 1 000 |
| Заблокированных | 1 000 |
| Pending friend requests (исходящие) | 100 |
| Username length | 2-32 символа |
| Display name length | 1-64 символа |
| Bio length | до 512 символов |
| Custom status length | до 128 символов |
| Размер аватара | макс. 8 MB |
| Форматы аватара | PNG, JPEG, WebP, GIF |
| Размеры ресайзов аватара | 128x128, 256x256, 1024x1024 |

---

## NATS события

| Событие | Payload | Подписчики |
|---------|---------|-----------|
| `user.updated` | `{ user_id, fields_changed[] }` | Gateway (broadcast), Guild Service (обновить member info) |
| `user.friend.request` | `{ from_id, to_id }` | Notification Service, Gateway |
| `user.friend.accepted` | `{ user_id_1, user_id_2 }` | Notification Service, Gateway, Presence Service |
| `user.friend.removed` | `{ user_id_1, user_id_2 }` | Gateway |
| `user.blocked` | `{ blocker_id, blocked_id }` | Message Service (скрыть DM), Gateway |
| `user.deleted` | `{ user_id }` | Все сервисы (каскадная очистка) |

## NATS подписки (входящие)

| Событие | Действие |
|---------|---------|
| `auth.user.registered` | Создать запись в `users` и `user_settings` |
| `auth.email.verified` | Можно обновить UI флаг |

---

## Кеширование (Redis)

| Ключ | TTL | Данные |
|------|-----|--------|
| `user:{id}` | 5 мин | Профиль пользователя (JSON) |
| `user:username:{username}` | 5 мин | user_id (для поиска по username) |
| `user:{id}:friends` | 2 мин | Список friend IDs |
| `user:{id}:blocks` | 5 мин | Список blocked IDs |

Инвалидация: при обновлении профиля, изменении друзей/блоков — удалять соответствующие ключи.

---

## Безопасность (чеклист)

- [ ] Все endpoints требуют JWT (кроме GET /users/:id — публичный профиль, но с rate limit)
- [ ] Входные данные валидируются (`validator` crate)
- [ ] Username: только alphanumeric + `_` + `.`, проверка через regex
- [ ] Bio/display_name: strip HTML тегов, предотвращение XSS
- [ ] Avatar: проверка MIME по magic bytes (`infer` crate), не по расширению
- [ ] Avatar: максимум 8MB, только разрешённые форматы
- [ ] Удаление аккаунта: re-authentication (пароль)
- [ ] Rate limiting на все мутационные endpoints
- [ ] Профиль чужого пользователя: не отдавать email, settings, блоки
- [ ] SQL запросы только через `sqlx::query!` с параметрами
- [ ] Кеш инвалидируется при каждом обновлении
- [ ] GDPR: полная анонимизация при удалении, hard delete через 30 дней
- [ ] Friendship: нельзя отправить запрос заблокированному / от заблокированного
- [ ] Block: автоматически удаляет дружбу

---

## Мониторинг

| Метрика | Тип | Описание |
|---------|-----|----------|
| `users_profile_updates_total` | counter | Обновления профиля |
| `users_avatar_uploads_total{status}` | counter | Загрузки аватаров (success/rejected) |
| `users_friend_requests_total` | counter | Запросы в друзья |
| `users_friendships_total` | gauge | Активных дружб |
| `users_blocks_total` | gauge | Активных блоков |
| `users_deletions_total` | counter | Удалений аккаунтов |
| `users_cache_hit_ratio` | gauge | Процент cache hit (Redis) |
| `users_db_query_duration_seconds{query}` | histogram | Время SQL запросов |
