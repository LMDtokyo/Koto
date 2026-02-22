# Стандарты проектирования REST API

Документ описывает правила и соглашения для проектирования REST API коммуникационной платформы. Все endpoints проходят через API Gateway (`services/api`, порт 3000), который проксирует запросы к внутренним микросервисам через NATS request/reply.

## Источники

- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
- [RFC 7396 — JSON Merge Patch](https://www.rfc-editor.org/rfc/rfc7396)
- [JSON:API Specification](https://jsonapi.org/)
- [Discord API Reference](https://discord.com/developers/docs/reference)
- [GitHub REST API](https://docs.github.com/en/rest)

---

## Оглавление

1. [Стандарты URL](#1-стандарты-url)
2. [HTTP методы](#2-http-методы)
3. [Формат ответов](#3-формат-ответов)
4. [Каталог кодов ошибок](#4-каталог-кодов-ошибок)
5. [Пагинация](#5-пагинация)
6. [Фильтрация и сортировка](#6-фильтрация-и-сортировка)
7. [Rate Limiting](#7-rate-limiting)
8. [Версионирование API](#8-версионирование-api)
9. [Аутентификация в запросах](#9-аутентификация-в-запросах)
10. [Content Negotiation](#10-content-negotiation)
11. [Idempotency](#11-idempotency)
12. [Audit Headers](#12-audit-headers)
13. [Примеры кода (Rust / Axum)](#13-примеры-кода-rust--axum)

---

## 1. Стандарты URL

### Базовый путь

Все публичные endpoints начинаются с префикса версии:

```
https://api.example.com/api/v1/...
```

### Правила именования

| Правило | Пример | Пояснение |
|---------|--------|-----------|
| Множественное число для коллекций | `/guilds`, `/channels`, `/messages` | Ресурс — это коллекция, один элемент адресуется через `:id` |
| kebab-case в URL | `/guild-discovery`, `/audit-logs` | Разделитель слов — дефис, не подчёркивание |
| snake_case в query params | `?role_id=123`, `?created_after=...` | Query параметры — snake_case |
| Только существительные | `/guilds/:id/members` | Глаголы не используются; действие определяется HTTP методом |
| Строчные буквы | `/channels/:id/messages` | Никаких CamelCase или UPPERCASE в путях |

### Вложенные ресурсы

Ресурсы, которые существуют только в контексте родительского объекта, выражаются вложенностью:

```
GET  /guilds/:guild_id/channels                    # каналы гильдии
GET  /channels/:channel_id/messages                # сообщения канала
GET  /guilds/:guild_id/members                     # участники гильдии
POST /channels/:channel_id/messages                # отправить сообщение
POST /guilds/:guild_id/roles                       # создать роль
GET  /guilds/:guild_id/audit-logs                  # аудит-лог
```

### Когда вкладывать, когда выносить на верхний уровень

**Вложенные** — ресурс не существует без родителя:

```
/guilds/:guild_id/channels          # канал принадлежит гильдии
/guilds/:guild_id/members           # участник привязан к гильдии
/guilds/:guild_id/roles             # роль существует внутри гильдии
/channels/:channel_id/messages      # сообщение привязано к каналу
/channels/:channel_id/pins          # пины привязаны к каналу
/channels/:channel_id/webhooks      # вебхуки привязаны к каналу
```

**Верхний уровень** — ресурс глобально адресуемый или часто запрашиваемый по ID напрямую:

```
/users/:id                          # пользователь глобален
/users/@me                          # текущий пользователь
/channels/:id                       # канал запрашивают по ID напрямую
/guilds/:id                         # гильдия запрашивается по ID
/invites/:code                      # инвайт по коду
/webhooks/:id/:token                # вебхук по ID + токену (без авторизации)
```

**Правило**: максимальная глубина вложенности — 2 уровня. Вместо `/guilds/:id/channels/:id/messages/:id/reactions` выносить:

```
/channels/:channel_id/messages/:message_id/reactions    # допустимо (2 уровня)
```

### Специальные endpoint-ы

| Endpoint | Назначение |
|----------|-----------|
| `/users/@me` | Текущий аутентифицированный пользователь |
| `/channels/:id/typing` | Typing indicator (POST) |
| `/guilds/:id/search` | Поиск по сообщениям внутри гильдии (POST) |
| `/voice/token` | Получение LiveKit токена (POST) |
| `/media/upload/presign` | Presigned URL для загрузки файла (POST) |
| `/auth/login` | Логин (POST) |
| `/auth/register` | Регистрация (POST) |
| `/auth/refresh` | Обновление access token (POST) |
| `/health` | Health check |
| `/health/ready` | Readiness probe (NATS + Redis доступны) |
| `/health/live` | Liveness probe (Kubernetes) |
| `/metrics` | Prometheus метрики |

### Полная карта маршрутов

| HTTP метод | Путь | NATS Subject | Сервис |
|-----------|------|-------------|--------|
| `POST` | `/auth/register` | `auth.register` | Auth |
| `POST` | `/auth/login` | `auth.login` | Auth |
| `POST` | `/auth/refresh` | `auth.refresh` | Auth |
| `GET` | `/users/@me` | `users.get_me` | Users |
| `PATCH` | `/users/@me` | `users.update_me` | Users |
| `GET` | `/users/:id` | `users.get` | Users |
| `POST` | `/guilds` | `guilds.create` | Guilds |
| `GET` | `/guilds/:id` | `guilds.get` | Guilds |
| `GET` | `/guilds/:id/channels` | `guilds.get_channels` | Guilds |
| `POST` | `/channels/:id/messages` | `messages.create` | Messages |
| `GET` | `/channels/:id/messages` | `messages.list` | Messages |
| `POST` | `/media/upload/presign` | `media.presign` | Media |
| `POST` | `/guilds/:id/search` | `search.messages` | Search |
| `POST` | `/voice/token` | `voice.token` | Voice |
| `POST` | `/channels/:id/webhooks` | `guilds.webhook_create` | Guilds |
| `POST` | `/webhooks/:id/:token` | прямой proxy (без auth) | Guilds |
| `POST` | `/channels/:id/typing` | `presence.typing` | Presence |
| `PATCH` | `/presence` | `presence.update` | Presence |

---

## 2. HTTP методы

### GET — получение ресурса

- **Идемпотентный** и **безопасный** (safe) — не изменяет состояние сервера
- **Кешируемый** — допускает Cache-Control заголовки
- Тело запроса отсутствует
- Параметры передаются через query string

```
GET /api/v1/guilds/123456789
GET /api/v1/channels/987654321/messages?limit=50&after=111222333
```

### POST — создание ресурса

- **Не идемпотентный** (повторный вызов может создать дубликат) — для защиты используется `X-Idempotency-Key`
- Возвращает `201 Created` с телом созданного ресурса
- Заголовок `Location` не обязателен (ресурс адресуется по Snowflake ID из тела ответа)

```
POST /api/v1/guilds
Content-Type: application/json

{
    "name": "My Server",
    "icon": null
}

→ 201 Created
{
    "id": "123456789",
    "name": "My Server",
    "owner_id": "987654321",
    "icon": null,
    "created_at": "2026-02-22T12:00:00Z"
}
```

### PUT — полная замена ресурса

- **Идемпотентный** — повторный вызов с тем же телом приводит к тому же результату
- Клиент отправляет полное представление ресурса
- Возвращает `200 OK` с обновлённым ресурсом
- Используется редко; в большинстве случаев предпочитается PATCH

```
PUT /api/v1/guilds/123456789/roles/555666777
Content-Type: application/json

{
    "name": "Moderator",
    "color": 3447003,
    "permissions": "1071698660929",
    "hoist": true,
    "mentionable": false
}

→ 200 OK
```

### PATCH — частичное обновление (JSON Merge Patch, RFC 7396)

- **Идемпотентный** — повторный PATCH с тем же телом приводит к тому же результату
- Клиент отправляет только изменённые поля
- `Content-Type: application/merge-patch+json` (допускается `application/json`)
- `null` означает удаление поля
- Возвращает `200 OK` с полным обновлённым ресурсом

```
PATCH /api/v1/users/@me
Content-Type: application/json

{
    "username": "new_name",
    "bio": null
}

→ 200 OK
{
    "id": "987654321",
    "username": "new_name",
    "bio": null,
    "avatar": "abc123.png",
    "created_at": "2025-06-01T10:00:00Z"
}
```

### DELETE — удаление ресурса

- **Идемпотентный** — повторный DELETE уже удалённого ресурса возвращает `204` или `404`
- Тело запроса отсутствует
- Возвращает `204 No Content` (без тела)

```
DELETE /api/v1/channels/987654321/messages/111222333

→ 204 No Content
```

### Сводка

| Метод | Идемпотентный | Безопасный | Тело запроса | Типичный ответ |
|-------|:---:|:---:|:---:|---|
| GET | Да | Да | Нет | 200 OK + тело |
| POST | Нет | Нет | Да | 201 Created + тело |
| PUT | Да | Нет | Да | 200 OK + тело |
| PATCH | Да | Нет | Да | 200 OK + тело |
| DELETE | Да | Нет | Нет | 204 No Content |

---

## 3. Формат ответов

### Успешные ответы

Формат ответа — **прямой объект или массив**, без обёрточных структур вида `{ "data": ... }`. Это соответствует соглашениям Discord API.

**Один ресурс:**

```json
{
    "id": "123456789",
    "name": "general",
    "type": 0,
    "guild_id": "987654321",
    "position": 0
}
```

**Коллекция:**

```json
[
    { "id": "111222333", "content": "Hello", "author_id": "444555666" },
    { "id": "111222334", "content": "World", "author_id": "444555666" }
]
```

**Пустая коллекция:**

```json
[]
```

**Пустой ответ (удаление, typing):**

```
HTTP/1.1 204 No Content
(тело отсутствует)
```

### Формат ID

Все ID — Snowflake (64-bit integer). В JSON передаются как **строки** для совместимости с JavaScript (потеря точности при `Number > 2^53`):

```json
{
    "id": "123456789012345678",
    "channel_id": "987654321098765432",
    "author_id": "111222333444555666"
}
```

### Формат дат

ISO 8601 (UTC), поле всегда заканчивается на `Z`:

```json
{
    "created_at": "2026-02-22T12:30:45Z",
    "edited_at": "2026-02-22T12:35:00Z"
}
```

### Формат ошибок

Все ошибки возвращаются в едином формате:

```json
{
    "code": "ERROR_CODE",
    "message": "Человекочитаемое описание ошибки"
}
```

Для некоторых ошибок добавляются дополнительные поля:

```json
{
    "code": "RATE_LIMITED",
    "message": "You are being rate limited",
    "retry_after": 1.5
}
```

```json
{
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "errors": {
        "username": ["Must be between 2 and 32 characters"],
        "email": ["Invalid email format"]
    }
}
```

### HTTP статусы

| Статус | Когда используется |
|--------|-------------------|
| `200 OK` | Успешный GET, PATCH, PUT |
| `201 Created` | Успешный POST (создание) |
| `204 No Content` | Успешный DELETE, typing, пустой ответ |
| `400 Bad Request` | Невалидные входные данные |
| `401 Unauthorized` | Отсутствует или невалидный токен |
| `403 Forbidden` | Недостаточно прав |
| `404 Not Found` | Ресурс не найден |
| `405 Method Not Allowed` | HTTP метод не поддерживается для маршрута |
| `409 Conflict` | Конфликт (дублирующий ресурс) |
| `413 Payload Too Large` | Тело запроса превышает лимит (25 MiB) |
| `429 Too Many Requests` | Превышен rate limit |
| `500 Internal Server Error` | Внутренняя ошибка сервера |
| `503 Service Unavailable` | Целевой сервис недоступен |
| `504 Gateway Timeout` | NATS request timeout |

---

## 4. Каталог кодов ошибок

### Ошибки API Gateway

| Код | HTTP статус | Описание |
|-----|:----------:|----------|
| `UNAUTHORIZED` | 401 | Отсутствует заголовок Authorization |
| `INVALID_TOKEN` | 401 | JWT невалидный (неверная подпись, формат) |
| `TOKEN_EXPIRED` | 401 | Срок действия JWT истёк |
| `RATE_LIMITED` | 429 | Превышен rate limit |
| `SERVICE_UNAVAILABLE` | 503 | Целевой микросервис недоступен в NATS |
| `SERVICE_TIMEOUT` | 504 | NATS request/reply timeout (30 сек) |
| `NOT_FOUND` | 404 | Маршрут не существует |
| `METHOD_NOT_ALLOWED` | 405 | HTTP метод не поддерживается |
| `PAYLOAD_TOO_LARGE` | 413 | Тело запроса > 25 MiB |
| `INTERNAL_ERROR` | 500 | Внутренняя ошибка (детали не раскрываются) |

### Ошибки бизнес-логики (из AppError в `crates/common`)

| Код | HTTP статус | Описание |
|-----|:----------:|----------|
| `VALIDATION_ERROR` | 400 | Входные данные не прошли валидацию |
| `INVALID_LIMIT` | 400 | Параметр `limit` вне диапазона 1-100 |
| `INVALID_CURSOR` | 400 | Указано более одного cursor (before/after/around) |
| `USER_NOT_FOUND` | 404 | Пользователь не найден |
| `GUILD_NOT_FOUND` | 404 | Гильдия не найдена |
| `CHANNEL_NOT_FOUND` | 404 | Канал не найден |
| `MESSAGE_NOT_FOUND` | 404 | Сообщение не найдено |
| `ROLE_NOT_FOUND` | 404 | Роль не найдена |
| `INVITE_NOT_FOUND` | 404 | Инвайт не найден или истёк |
| `MISSING_PERMISSIONS` | 403 | Недостаточно прав (битовая маска) |
| `MISSING_ACCESS` | 403 | Нет доступа к каналу (VIEW_CHANNEL) |
| `DUPLICATE_USERNAME` | 409 | Имя пользователя уже занято |
| `MAX_GUILDS_REACHED` | 400 | Превышен лимит гильдий для пользователя |
| `MAX_CHANNELS_REACHED` | 400 | Превышен лимит каналов в гильдии |
| `MESSAGE_TOO_LONG` | 400 | Сообщение превышает 4000 символов |
| `EMPTY_MESSAGE` | 400 | Пустое сообщение без вложений |

### Пример ответа с ошибкой

```
HTTP/1.1 403 Forbidden
Content-Type: application/json
X-Request-ID: 550e8400-e29b-41d4-a716-446655440000

{
    "code": "MISSING_PERMISSIONS",
    "message": "You do not have the MANAGE_MESSAGES permission in this channel"
}
```

---

## 5. Пагинация

### Подход: Cursor-based на Snowflake ID

Используется cursor-based пагинация вместо offset-based. Snowflake ID являются естественными курсорами, так как они хронологически сортируемы (42 бита timestamp).

Реализация находится в `crates/db` (структура `CursorParams`).

### Query параметры

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|:------------:|----------|
| `limit` | integer | 50 | Количество элементов (диапазон: 1-100) |
| `before` | snowflake | - | Элементы с ID меньше указанного |
| `after` | snowflake | - | Элементы с ID больше указанного |
| `around` | snowflake | - | Элементы вокруг указанного ID |

**Ограничение**: допускается только один из параметров `before`, `after`, `around`. При указании нескольких — ошибка `INVALID_CURSOR`.

### Порядок сортировки

- `before` — от новых к старым (DESC по ID), возвращает элементы **до** курсора
- `after` — от старых к новым (ASC по ID), возвращает элементы **после** курсора
- `around` — возвращает `limit / 2` элементов до и после курсора
- Без курсора — от новых к старым (DESC, последние N элементов)

### Определение "есть ли ещё"

Клиент запрашивает `limit + 1` элементов. Если API вернул ровно `limit + 1` элемент — значит есть ещё, и клиент отбрасывает лишний элемент. Если вернулось `<= limit` — это последняя страница.

Сервер не модифицирует клиентский `limit`. Валидация на стороне сервера ограничивает значение диапазоном 1-100.

### Примеры запросов и ответов

**Запрос первой страницы (50 последних сообщений):**

```
GET /api/v1/channels/987654321/messages?limit=51
Authorization: Bearer <access_token>
```

```
HTTP/1.1 200 OK
Content-Type: application/json

[
    { "id": "900000000000000051", "content": "Newest", "author_id": "111" },
    { "id": "900000000000000050", "content": "...", "author_id": "222" },
    ...
    { "id": "900000000000000001", "content": "51st message", "author_id": "333" }
]
```

Вернулся 51 элемент — значит есть ещё. Клиент отбрасывает последний (51-й) и запоминает ID последнего видимого (50-го) элемента.

**Запрос следующей страницы:**

```
GET /api/v1/channels/987654321/messages?limit=51&before=900000000000000001
Authorization: Bearer <access_token>
```

```
HTTP/1.1 200 OK
Content-Type: application/json

[
    { "id": "899999999999999950", "content": "...", "author_id": "111" },
    ...
    { "id": "899999999999999920", "content": "...", "author_id": "222" }
]
```

Вернулось 31 элемент (< 51) — это последняя страница.

**Запрос новых сообщений (polling):**

```
GET /api/v1/channels/987654321/messages?after=900000000000000051&limit=51
```

**Запрос контекста вокруг сообщения:**

```
GET /api/v1/channels/987654321/messages?around=900000000000000025&limit=50
```

Вернёт до 25 сообщений до и 25 после указанного ID.

### Валидация на сервере

```rust
pub struct CursorParams {
    pub before: Option<i64>,
    pub after: Option<i64>,
    pub around: Option<i64>,
    pub limit: i64, // default 50, max 100
}

impl CursorParams {
    pub fn validate(&self) -> Result<(), AppError> {
        if self.limit < 1 || self.limit > 100 {
            return Err(AppError::bad_request(
                "INVALID_LIMIT",
                "Limit must be 1-100",
            ));
        }
        let cursor_count = [self.before, self.after, self.around]
            .iter()
            .filter(|c| c.is_some())
            .count();
        if cursor_count > 1 {
            return Err(AppError::bad_request(
                "INVALID_CURSOR",
                "Only one of before/after/around allowed",
            ));
        }
        Ok(())
    }
}
```

---

## 6. Фильтрация и сортировка

### Фильтрация через query параметры

Фильтры передаются как query параметры в формате snake_case:

```
GET /api/v1/guilds/123/members?role_id=555666777&limit=50
GET /api/v1/guilds/123/audit-logs?user_id=444555666&action_type=25&limit=20
GET /api/v1/channels/987/messages?author_id=444555666&has=attachment&limit=50
```

### Стандартные фильтры по endpoints

**GET /guilds/:id/members**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `role_id` | snowflake | Фильтр по роли |
| `limit` | integer | 1-1000, по умолчанию 100 |
| `after` | snowflake | Cursor для пагинации |

**GET /guilds/:id/audit-logs**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `user_id` | snowflake | Фильтр по автору действия |
| `action_type` | integer | Тип действия (enum) |
| `before` | snowflake | Cursor |
| `limit` | integer | 1-100, по умолчанию 50 |

**POST /guilds/:id/search** (поиск через тело запроса)

```json
{
    "content": "search query",
    "author_id": "444555666",
    "channel_id": "987654321",
    "has": ["attachment", "embed", "link"],
    "min_id": "100000000000000000",
    "max_id": "999999999999999999",
    "limit": 25
}
```

Поиск использует POST, потому что параметры могут быть сложными (массивы, вложенные объекты) и не укладываются в query string.

### Сортировка

Для большинства endpoints сортировка определяется семантикой запроса:

- Сообщения — по Snowflake ID (хронологический порядок)
- Участники — по Snowflake ID (порядок присоединения)
- Audit-log — по Snowflake ID (хронологический порядок)

Если endpoint поддерживает несколько вариантов сортировки, используется параметр `sort`:

```
GET /api/v1/guilds/123/members?sort=joined_at&order=asc&limit=50
```

| Параметр | Значения | По умолчанию |
|----------|----------|:------------:|
| `sort` | зависит от ресурса | `id` |
| `order` | `asc`, `desc` | `desc` |

---

## 7. Rate Limiting

### Таблица лимитов

| Endpoint | Лимит | Окно | Scope |
|----------|:-----:|:----:|-------|
| API (общий) | 50 запросов | 1 секунда | per user |
| `POST /auth/login` | 5 запросов | 15 минут | per IP |
| `POST /auth/register` | 3 запроса | 1 час | per IP |
| `POST /channels/:id/messages` | 5 сообщений | 5 секунд | per channel |
| `DELETE /channels/:id/messages/:id` | 5 запросов | 1 секунда | per channel |
| Reactions | 1 запрос | 250 мс | per channel |
| File upload | 10 файлов | 1 минута | per user |
| WebSocket events | 120 событий | 1 минута | per connection |

### Заголовки Rate Limit

Каждый ответ API содержит заголовки rate limiting:

```
X-RateLimit-Limit: 50
X-RateLimit-Remaining: 47
X-RateLimit-Reset: 1740249601.000
X-RateLimit-Reset-After: 0.850
X-RateLimit-Bucket: global
X-RateLimit-Scope: user
```

| Заголовок | Описание |
|-----------|----------|
| `X-RateLimit-Limit` | Максимум запросов в текущем окне |
| `X-RateLimit-Remaining` | Оставшиеся запросы |
| `X-RateLimit-Reset` | Unix timestamp (в секундах с дробной частью), когда bucket сбросится |
| `X-RateLimit-Reset-After` | Секунды до сброса (для клиентов без синхронизации часов) |
| `X-RateLimit-Bucket` | Имя bucket-а (`global`, `msg_create`, `auth_login` и т.д.) |
| `X-RateLimit-Scope` | Область: `user` или `global` |
| `Retry-After` | Секунды до повторной попытки (только при 429) |

### Пример ответа 429

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 1.5
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1740249606.500
X-RateLimit-Reset-After: 1.500
X-RateLimit-Bucket: msg_create
X-RateLimit-Scope: user

{
    "code": "RATE_LIMITED",
    "message": "You are being rate limited",
    "retry_after": 1.5
}
```

### Bucket types

Buckets из `crates/rate-limit`:

| Bucket | Redis key pattern | Описание |
|--------|------------------|----------|
| `Global` | `rate:global:{user_id}` | Общий лимит на все запросы |
| `MessageCreate` | `rate:msg_create:{channel_id}` | Отправка сообщений |
| `MessageDelete` | `rate:msg_delete:{channel_id}` | Удаление сообщений |
| `Reaction` | `rate:reaction:{channel_id}` | Реакции |
| `AuthLogin` | `rate:auth_login:{ip}` | Логин (по IP) |
| `AuthRegister` | `rate:auth_register:{ip}` | Регистрация (по IP) |
| `FileUpload` | `rate:file_upload:{user_id}` | Загрузка файлов |

### Рекомендации для клиентов

1. Всегда обрабатывать 429 и ждать `Retry-After` секунд перед повтором
2. Отслеживать `X-RateLimit-Remaining` и замедляться при приближении к 0
3. Использовать `X-RateLimit-Reset-After` (относительное значение) вместо `X-RateLimit-Reset` (абсолютное), чтобы не зависеть от синхронизации часов
4. Cloudflare ban threshold: 10000 невалидных запросов (401/403/429) за 10 минут

---

## 8. Версионирование API

### Стратегия: URL-based

Версия указывается в URL:

```
/api/v1/guilds
/api/v2/guilds
```

### Когда создавать новую версию

Новая мажорная версия (`v2`) создаётся **только при breaking changes**:

| Breaking change (нужна v2) | Non-breaking (остаётся v1) |
|----------------------------|---------------------------|
| Удаление поля из ответа | Добавление нового поля в ответ |
| Переименование поля | Добавление нового endpoint |
| Изменение типа поля | Добавление нового query param |
| Изменение семантики endpoint | Добавление нового error code |
| Удаление endpoint | Изменение внутренней реализации |
| Изменение формата ID | Расширение enum новыми значениями |

### Deprecation Policy

1. **Анонс**: минимум за 3 месяца до удаления версии
2. **Заголовок**: ответы устаревшей версии содержат `Sunset: <date>` (RFC 8594)
3. **Документация**: deprecated endpoints помечаются, указываются альтернативы
4. **Период поддержки**: старая версия остаётся рабочей минимум 6 месяцев после выхода новой
5. **Финальное удаление**: старая версия начинает возвращать `410 Gone`

Пример заголовка deprecation:

```
HTTP/1.1 200 OK
Sunset: Sat, 01 Nov 2026 00:00:00 GMT
Deprecation: true
Link: </api/v2/guilds>; rel="successor-version"
```

### Правила

- Первая публичная версия: `v1`
- Нумерация: только целые числа (`v1`, `v2`, `v3`)
- Не более 2 активных версий одновременно
- Bot API и User API версионируются вместе

---

## 9. Аутентификация в запросах

### User Authentication

Заголовок:

```
Authorization: Bearer <access_token>
```

`access_token` — JWT, подписанный ES256 (ECDSA P-256). TTL: 15 минут.

Payload JWT:

```json
{
    "sub": 123456789,
    "iat": 1740249600,
    "exp": 1740250500
}
```

Минимум данных в payload (только `sub`, `iat`, `exp`). Дополнительная информация о пользователе запрашивается из базы.

### Bot Authentication

```
Authorization: Bot <bot_token>
```

`bot_token` — opaque строка, хранится в PostgreSQL. API Gateway различает тип аутентификации по префиксу (`Bearer` vs `Bot`).

### Публичные endpoints (без аутентификации)

| Endpoint | Назначение |
|----------|-----------|
| `POST /auth/login` | Логин |
| `POST /auth/register` | Регистрация |
| `POST /auth/refresh` | Обновление access token |
| `POST /webhooks/:id/:token` | Входящий webhook (авторизация через token в URL) |
| `GET /health` | Health check |
| `GET /health/ready` | Readiness probe |
| `GET /health/live` | Liveness probe |
| `GET /metrics` | Prometheus метрики |

Все остальные endpoints требуют валидный JWT.

### Refresh Token Flow

```
POST /api/v1/auth/refresh
Content-Type: application/json

{
    "refresh_token": "<opaque_token>"
}

→ 200 OK
{
    "access_token": "<new_jwt>",
    "refresh_token": "<new_opaque_token>",
    "expires_in": 900
}
```

Refresh token ротируется при каждом использовании. Старый refresh token инвалидируется.

---

## 10. Content Negotiation

### Content-Type

Все запросы с телом должны использовать:

```
Content-Type: application/json
```

Для PATCH допускается:

```
Content-Type: application/merge-patch+json
```

Все ответы с телом возвращают:

```
Content-Type: application/json; charset=utf-8
```

### Сжатие

Клиент указывает поддерживаемые алгоритмы:

```
Accept-Encoding: gzip, zstd
```

Сервер выбирает оптимальный и указывает в ответе:

```
Content-Encoding: gzip
```

Поддерживаемые алгоритмы:

| Алгоритм | Приоритет | Пояснение |
|----------|:---------:|-----------|
| `zstd` | 1 (высший) | Лучшее соотношение сжатия/скорости |
| `gzip` | 2 | Универсальная поддержка |
| identity | 3 | Без сжатия |

### Максимальный размер тела запроса

| Тип | Лимит |
|-----|:-----:|
| JSON body | 1 MiB |
| File upload (presigned URL) | 25 MiB |
| Общий (API Gateway) | 25 MiB |

При превышении лимита — `413 Payload Too Large`:

```json
{
    "code": "PAYLOAD_TOO_LARGE",
    "message": "Request body exceeds maximum size of 25 MiB"
}
```

Настраивается через переменную окружения `MAX_BODY_SIZE` (по умолчанию 26214400 байт = 25 MiB).

---

## 11. Idempotency

### Заголовок X-Idempotency-Key

Для защиты от дублирования при retry мутационных запросов клиент передаёт:

```
X-Idempotency-Key: <uuid-v4>
```

### Правила

| Правило | Описание |
|---------|----------|
| **Методы** | Применяется к `POST`, `PATCH`, `PUT` |
| **Формат ключа** | UUID v4, максимум 64 символа |
| **Привязка** | Ключ привязан к `user_id` (разные пользователи могут использовать одинаковые ключи) |
| **Хранение** | Redis: `idempotency:{user_id}:{key}` -> сохранённый response |
| **TTL** | 24 часа |
| **При совпадении** | Возвращается сохранённый response без повторного выполнения |

### Реализация в Redis

```
SET idempotency:{user_id}:{key} {response_json} NX EX 86400
```

- `NX` — установить только если ключ не существует (atomic check-and-set)
- `EX 86400` — TTL 24 часа

### Пример

**Первый запрос:**

```
POST /api/v1/channels/987654321/messages
Authorization: Bearer <token>
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
    "content": "Hello world!"
}

→ 201 Created
{
    "id": "111222333444555666",
    "content": "Hello world!",
    "author_id": "444555666777888999"
}
```

**Повторный запрос с тем же ключом (retry):**

```
POST /api/v1/channels/987654321/messages
Authorization: Bearer <token>
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
    "content": "Hello world!"
}

→ 201 Created  (из кеша, сообщение НЕ создано повторно)
{
    "id": "111222333444555666",
    "content": "Hello world!",
    "author_id": "444555666777888999"
}
```

---

## 12. Audit Headers

### X-Request-ID

Каждый запрос получает уникальный идентификатор для сквозной трассировки:

```
X-Request-ID: 550e8400-e29b-41d4-a716-446655440000
```

**Правила:**

- Если клиент передал `X-Request-ID` — сервер использует его (при условии валидного UUID v4)
- Если заголовок отсутствует — API Gateway генерирует новый UUID v4
- `X-Request-ID` передаётся во все внутренние сервисы через NATS request envelope
- Возвращается клиенту в заголовке ответа
- Логируется через `tracing` (span поле `request_id`)

### X-Audit-Log-Reason

При модерационных действиях клиент может указать причину, которая будет записана в audit log:

```
DELETE /api/v1/guilds/123456789/members/444555666
Authorization: Bearer <token>
X-Audit-Log-Reason: Spam in #general
```

**Правила:**

| Правило | Описание |
|---------|----------|
| **Максимальная длина** | 512 символов |
| **Кодировка** | UTF-8, URL-encoded если содержит спецсимволы |
| **Обязательность** | Опциональный |
| **Где записывается** | `guilds.audit_log` (PostgreSQL) |
| **Применяется к** | Kick, ban, unban, role change, channel delete, message delete (bulk) |

### NATS Request Envelope

Все audit headers передаются во внутренний NATS запрос:

```json
{
    "user_id": 444555666777888999,
    "request_id": "550e8400-e29b-41d4-a716-446655440000",
    "audit_log_reason": "Spam in #general",
    "data": { ... }
}
```

---

## 13. Примеры кода (Rust / Axum)

### Стандартный handler с валидацией

```rust
use axum::{extract::State, Json};
use serde::{Deserialize, Serialize};
use validator::Validate;

use common::errors::AppError;
use common::extractors::CurrentUser;

/// Входные данные для создания сообщения.
#[derive(Debug, Deserialize, Validate)]
pub struct CreateMessageInput {
    #[validate(length(min = 1, max = 4000))]
    pub content: String,
    pub reply_to: Option<i64>,
}

/// Ответ с данными сообщения.
#[derive(Debug, Serialize)]
pub struct MessageResponse {
    pub id: String,
    pub channel_id: String,
    pub author_id: String,
    pub content: String,
    pub reply_to: Option<String>,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

/// POST /channels/:channel_id/messages
pub async fn create_message(
    State(state): State<AppState>,
    user: CurrentUser,
    axum::extract::Path(channel_id): axum::extract::Path<i64>,
    Json(input): Json<CreateMessageInput>,
) -> Result<(axum::http::StatusCode, Json<MessageResponse>), AppError> {
    // 1. Валидация входных данных
    input
        .validate()
        .map_err(|e| AppError::BadRequest {
            code: "VALIDATION_ERROR".into(),
            message: e.to_string(),
        })?;

    // 2. Проксирование к Message Service через NATS
    let response: MessageResponse = state
        .nats_proxy
        .request(
            "messages.create",
            &NatsEnvelope {
                user_id: user.user_id,
                request_id: state.request_id.clone(),
                data: CreateMessageRequest {
                    channel_id,
                    content: input.content,
                    reply_to: input.reply_to,
                },
            },
        )
        .await?;

    Ok((axum::http::StatusCode::CREATED, Json(response)))
}
```

### Формат ответа с пагинацией

```rust
use axum::{extract::{Path, Query, State}, Json};
use serde::Deserialize;

use common::errors::AppError;
use common::extractors::CurrentUser;
use db::pagination::CursorParams;

/// Query параметры пагинации сообщений.
#[derive(Debug, Deserialize)]
pub struct ListMessagesQuery {
    pub before: Option<i64>,
    pub after: Option<i64>,
    pub around: Option<i64>,
    #[serde(default = "default_limit")]
    pub limit: i64,
}

fn default_limit() -> i64 {
    50
}

impl From<ListMessagesQuery> for CursorParams {
    fn from(q: ListMessagesQuery) -> Self {
        Self {
            before: q.before,
            after: q.after,
            around: q.around,
            limit: q.limit,
        }
    }
}

/// GET /channels/:channel_id/messages
///
/// Возвращает массив сообщений. Клиент запрашивает limit+1
/// для определения наличия следующей страницы.
pub async fn list_messages(
    State(state): State<AppState>,
    user: CurrentUser,
    Path(channel_id): Path<i64>,
    Query(query): Query<ListMessagesQuery>,
) -> Result<Json<Vec<MessageResponse>>, AppError> {
    let cursor: CursorParams = query.into();
    cursor.validate()?;

    let messages: Vec<MessageResponse> = state
        .nats_proxy
        .request(
            "messages.list",
            &NatsEnvelope {
                user_id: user.user_id,
                request_id: state.request_id.clone(),
                data: ListMessagesRequest {
                    channel_id,
                    before: cursor.before,
                    after: cursor.after,
                    around: cursor.around,
                    limit: cursor.limit,
                },
            },
        )
        .await?;

    // Возвращаем массив напрямую (без обёртки)
    Ok(Json(messages))
}
```

### SQL запрос с cursor-based пагинацией (внутри Message Service)

```rust
/// Получение сообщений с cursor-based пагинацией.
///
/// Snowflake ID используются как естественные курсоры
/// (хронологически сортируемые, 42 бита timestamp).
pub async fn fetch_messages(
    pool: &sqlx::PgPool,
    channel_id: i64,
    cursor: &CursorParams,
) -> Result<Vec<Message>, AppError> {
    let messages = if let Some(before) = cursor.before {
        sqlx::query_as!(
            Message,
            r#"
            SELECT id, channel_id, author_id, content, created_at
            FROM messages
            WHERE channel_id = $1 AND id < $2
            ORDER BY id DESC
            LIMIT $3
            "#,
            channel_id,
            before,
            cursor.limit,
        )
        .fetch_all(pool)
        .await?
    } else if let Some(after) = cursor.after {
        sqlx::query_as!(
            Message,
            r#"
            SELECT id, channel_id, author_id, content, created_at
            FROM messages
            WHERE channel_id = $1 AND id > $2
            ORDER BY id ASC
            LIMIT $3
            "#,
            channel_id,
            after,
            cursor.limit,
        )
        .fetch_all(pool)
        .await?
    } else {
        // Без курсора — последние N сообщений
        sqlx::query_as!(
            Message,
            r#"
            SELECT id, channel_id, author_id, content, created_at
            FROM messages
            WHERE channel_id = $1
            ORDER BY id DESC
            LIMIT $2
            "#,
            channel_id,
            cursor.limit,
        )
        .fetch_all(pool)
        .await?
    };

    Ok(messages)
}
```

### Rate limit middleware (использование)

```rust
use axum::{middleware, Router};
use rate_limit::{RateLimitLayer, RateBucket};

/// Конфигурация маршрутов с rate limiting.
///
/// Каждая группа endpoints получает свой bucket
/// с индивидуальными лимитами.
pub fn message_routes(state: AppState) -> Router<AppState> {
    let msg_create_rate_limit = RateLimitLayer::new(
        state.rate_limiter.clone(),
        RateBucket::MessageCreate {
            channel_id: 0, // подставляется из path param в middleware
        },
    );

    let msg_delete_rate_limit = RateLimitLayer::new(
        state.rate_limiter.clone(),
        RateBucket::MessageDelete {
            channel_id: 0,
        },
    );

    Router::new()
        .route(
            "/channels/:channel_id/messages",
            axum::routing::get(list_messages)
                .post(create_message.layer(msg_create_rate_limit)),
        )
        .route(
            "/channels/:channel_id/messages/:message_id",
            axum::routing::get(get_message)
                .patch(edit_message)
                .delete(delete_message.layer(msg_delete_rate_limit)),
        )
}

/// Конфигурация всего API Router с global rate limit.
pub fn api_router(state: AppState) -> Router {
    let global_rate_limit = RateLimitLayer::new(
        state.rate_limiter.clone(),
        RateBucket::Global,
    );

    Router::new()
        .nest("/api/v1", all_routes(state.clone()))
        .layer(middleware::from_fn_with_state(
            state.clone(),
            auth_middleware,
        ))
        .layer(global_rate_limit)
        .with_state(state)
}
```

### AppError с IntoResponse (из `crates/common`)

```rust
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("{message}")]
    BadRequest { code: String, message: String },

    #[error("Unauthorized")]
    Unauthorized,

    #[error("Forbidden: {code}")]
    Forbidden { code: String, message: String },

    #[error("Not found: {code}")]
    NotFound { code: String, message: String },

    #[error("Rate limited")]
    RateLimited { retry_after: f64 },

    #[error("Internal error")]
    Internal(#[from] anyhow::Error),
}

impl AppError {
    pub fn bad_request(code: &str, message: &str) -> Self {
        Self::BadRequest {
            code: code.into(),
            message: message.into(),
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, code, message) = match &self {
            AppError::BadRequest { code, message } => {
                (StatusCode::BAD_REQUEST, code.clone(), message.clone())
            }
            AppError::Unauthorized => (
                StatusCode::UNAUTHORIZED,
                "UNAUTHORIZED".into(),
                "Unauthorized".into(),
            ),
            AppError::Forbidden { code, message } => {
                (StatusCode::FORBIDDEN, code.clone(), message.clone())
            }
            AppError::NotFound { code, message } => {
                (StatusCode::NOT_FOUND, code.clone(), message.clone())
            }
            AppError::RateLimited { retry_after } => {
                return (
                    StatusCode::TOO_MANY_REQUESTS,
                    [("Retry-After", retry_after.to_string())],
                    axum::Json(serde_json::json!({
                        "code": "RATE_LIMITED",
                        "message": "Rate limited",
                        "retry_after": retry_after
                    })),
                )
                    .into_response();
            }
            AppError::Internal(_) => {
                tracing::error!(error = %self, "Internal error");
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR".into(),
                    "Internal error".into(),
                )
            }
        };

        (
            status,
            axum::Json(serde_json::json!({
                "code": code,
                "message": message
            })),
        )
            .into_response()
    }
}
```
