# API Gateway

Единая точка входа для REST запросов от клиентов. Маршрутизация к внутренним сервисам, JWT аутентификация, rate limiting, CORS, request logging.

Порт: `3000`
Путь: `services/api/`

## Источники

- [Discord Developer Docs — API Reference](https://docs.discord.com/developers/reference)
- [Discord Developer Docs — Rate Limits](https://docs.discord.com/developers/topics/rate-limits)
- [OWASP API Security Top 10](https://owasp.org/API-Security/)
- [crate: axum](https://docs.rs/axum/latest/axum/)
- [crate: tower](https://docs.rs/tower/latest/tower/)
- [crate: tower-http](https://docs.rs/tower-http/latest/tower_http/)
- [crate: jsonwebtoken](https://docs.rs/jsonwebtoken/latest/jsonwebtoken/)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Структура сервиса

```
services/api/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs             # Router: все маршруты
│   │   ├── auth.rs            # /auth/* → Auth Service
│   │   ├── users.rs           # /users/* → User Service
│   │   ├── guilds.rs          # /guilds/* → Guild Service
│   │   ├── channels.rs        # /channels/* → Message/Guild Service
│   │   ├── voice.rs           # /voice/* → Voice Service
│   │   ├── media.rs           # /media/* → Media Service
│   │   └── search.rs          # /search/* → Search Service
│   ├── middleware/
│   │   ├── mod.rs
│   │   ├── auth.rs            # JWT validation + extraction
│   │   ├── rate_limit.rs      # Global + per-endpoint rate limiting
│   │   ├── cors.rs            # CORS configuration
│   │   ├── request_id.rs      # X-Request-Id generation
│   │   └── logging.rs         # Request/response tracing
│   ├── proxy/
│   │   ├── mod.rs
│   │   └── nats_proxy.rs      # NATS request/reply proxy
│   └── extractors/
│       ├── mod.rs
│       └── auth.rs            # CurrentUser extractor
├── tests/
│   ├── common/mod.rs
│   ├── auth_middleware_test.rs
│   └── rate_limit_test.rs
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "api-gateway"
version = "0.1.0"
edition = "2024"

[dependencies]
# HTTP / async
axum = { workspace = true }
axum-extra = { version = "0.10", features = ["typed-header"] }
tokio = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }

# Сериализация
serde = { workspace = true }
serde_json = { workspace = true }

# NATS (проксирование к сервисам)
async-nats = { workspace = true }

# Кеш (rate limiting state)
redis = { workspace = true }
deadpool-redis = "0.18"

# Аутентификация
jsonwebtoken = { workspace = true }

# Время
chrono = { workspace = true }

# Ошибки
thiserror = { workspace = true }

# Логирование
tracing = { workspace = true }
tracing-subscriber = { workspace = true }

# Метрики
metrics = "0.24"
metrics-exporter-prometheus = "0.16"

# Утилиты
uuid = { workspace = true }

# Конфигурация
config = "0.15"
dotenvy = "0.15"

# Внутренние crates
common = { path = "../../crates/common" }
rate-limit = { path = "../../crates/rate-limit" }
cache = { path = "../../crates/cache" }
```

---

## Конфигурация (config.rs)

```rust
pub struct ApiConfig {
    // Сервер
    pub host: String,                    // API_HOST=0.0.0.0
    pub port: u16,                       // API_PORT=3000

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...
    pub nats_request_timeout_ms: u64,    // NATS_REQUEST_TIMEOUT_MS=5000

    // JWT (валидация)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=<ES256 public key PEM>

    // CORS
    pub cors_origins: Vec<String>,       // CORS_ORIGINS=https://app.example.com,https://admin.example.com

    // Rate limiting (global)
    pub global_rate_limit: u32,          // GLOBAL_RATE_LIMIT=50
    pub global_rate_window_secs: u64,    // GLOBAL_RATE_WINDOW_SECS=1

    // Request limits
    pub max_body_size: usize,            // MAX_BODY_SIZE=26214400 (25 MiB)
    pub request_timeout_ms: u64,         // REQUEST_TIMEOUT_MS=30000
}
```

---

## Принцип работы

API Gateway **не содержит бизнес-логику**. Он:

1. Принимает HTTP запрос от клиента
2. Валидирует JWT (middleware)
3. Проверяет rate limit (middleware)
4. Проксирует запрос к целевому сервису через **NATS request/reply**
5. Возвращает ответ клиенту

```
Client (HTTPS)
    ↓
API Gateway (:3000)
    ├── Middleware: CORS
    ├── Middleware: Request ID
    ├── Middleware: Rate Limit
    ├── Middleware: JWT Auth
    ├── Middleware: Logging
    ↓
Route handler → NATS request("auth.login", payload)
    ↓
Auth Service → NATS reply(response)
    ↓
API Gateway → HTTP Response to Client
```

---

## NATS Request/Reply Proxy

```rust
use async_nats::Client;

pub struct NatsProxy {
    client: Client,
    timeout: Duration,
}

impl NatsProxy {
    pub async fn request<Req: Serialize, Res: DeserializeOwned>(
        &self,
        subject: &str,
        payload: &Req,
    ) -> Result<Res, ApiError> {
        let data = serde_json::to_vec(payload)?;

        let response = tokio::time::timeout(
            self.timeout,
            self.client.request(subject, data.into()),
        )
        .await
        .map_err(|_| ApiError::ServiceTimeout)?
        .map_err(|_| ApiError::ServiceUnavailable)?;

        let result: ServiceResponse<Res> = serde_json::from_slice(&response.payload)?;

        match result {
            ServiceResponse::Ok(data) => Ok(data),
            ServiceResponse::Error { code, message } => Err(ApiError::ServiceError { code, message }),
        }
    }
}
```

### Subject mapping

| HTTP Route | NATS Subject | Target Service |
|------------|-------------|----------------|
| `POST /auth/register` | `auth.register` | Auth |
| `POST /auth/login` | `auth.login` | Auth |
| `POST /auth/refresh` | `auth.refresh` | Auth |
| `GET /users/@me` | `users.get_me` | Users |
| `PATCH /users/@me` | `users.update_me` | Users |
| `GET /users/:id` | `users.get` | Users |
| `POST /guilds` | `guilds.create` | Guilds |
| `GET /guilds/:id` | `guilds.get` | Guilds |
| `GET /guilds/:id/channels` | `guilds.get_channels` | Guilds |
| `POST /channels/:id/messages` | `messages.create` | Messages |
| `GET /channels/:id/messages` | `messages.list` | Messages |
| `POST /media/upload/presign` | `media.presign` | Media |
| `POST /guilds/:id/search` | `search.messages` | Search |
| `POST /voice/token` | `voice.token` | Voice |
| `POST /channels/:id/webhooks` | `guilds.webhook_create` | Guilds |
| `POST /webhooks/:id/:token` | direct proxy (no auth) | Guilds |
| `POST /channels/:id/typing` | `presence.typing` | Presence |
| `PATCH /presence` | `presence.update` | Presence |

### Request envelope

Каждый NATS request содержит:

```json
{
    "user_id": 123456789,
    "request_id": "uuid-v4",
    "data": { ... }
}
```

`user_id` извлекается из JWT на уровне API Gateway и добавляется в envelope. Внутренние сервисы **доверяют** user_id из envelope — они не ревалидируют JWT.

---

## JWT Auth Middleware

```rust
use axum::{extract::Request, middleware::Next, response::Response};
use jsonwebtoken::{decode, DecodingKey, Validation, Algorithm};

pub async fn auth_middleware(
    State(state): State<AppState>,
    mut request: Request,
    next: Next,
) -> Result<Response, ApiError> {
    // Публичные endpoints: skip auth
    let path = request.uri().path();
    if is_public_endpoint(path) {
        return Ok(next.run(request).await);
    }

    // Extract token from Authorization header
    let token = request
        .headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .ok_or(ApiError::Unauthorized)?;

    // Decode and validate JWT (ES256)
    let claims = decode::<Claims>(
        token,
        &DecodingKey::from_ec_pem(state.jwt_public_key.as_bytes())?,
        &Validation::new(Algorithm::ES256),
    )
    .map_err(|_| ApiError::InvalidToken)?
    .claims;

    // Check expiry
    if claims.exp < chrono::Utc::now().timestamp() as usize {
        return Err(ApiError::TokenExpired);
    }

    // Add user_id to request extensions
    request.extensions_mut().insert(CurrentUser {
        user_id: claims.sub,
    });

    Ok(next.run(request).await)
}

fn is_public_endpoint(path: &str) -> bool {
    matches!(path,
        "/auth/login" | "/auth/register" | "/auth/refresh" |
        "/health" | "/metrics"
    )
}
```

---

## CORS

```rust
use tower_http::cors::{CorsLayer, AllowOrigin, AllowMethods, AllowHeaders};

fn cors_layer(origins: &[String]) -> CorsLayer {
    CorsLayer::new()
        .allow_origin(
            origins.iter()
                .filter_map(|o| o.parse().ok())
                .collect::<Vec<HeaderValue>>()
        )
        .allow_methods([
            Method::GET, Method::POST, Method::PUT,
            Method::PATCH, Method::DELETE, Method::OPTIONS,
        ])
        .allow_headers([
            header::AUTHORIZATION,
            header::CONTENT_TYPE,
            header::ACCEPT,
        ])
        .allow_credentials(true)
        .max_age(Duration::from_secs(86400))
}
```

---

## Rate Limiting

### Global rate limit

| Лимит | Значение |
|-------|----------|
| Все запросы | 50 per 1 секунду per user |

### Per-endpoint (через SECURITY.md)

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /auth/login | 5 запросов | 15 минут |
| POST /auth/register | 3 запроса | 1 час |
| POST /channels/:id/messages | 5 сообщений | 5 секунд (per channel) |
| File upload | 10 файлов | 1 минута |

### Rate limit headers

```
X-RateLimit-Limit: 50
X-RateLimit-Remaining: 49
X-RateLimit-Reset: 1740153601.000
X-RateLimit-Reset-After: 1.000
X-RateLimit-Bucket: global
X-RateLimit-Scope: user
Retry-After: 1
```

### Implementation

Rate limiting через Redis sliding window (реализован в `crates/rate-limit`):

```
EVALSHA <sliding_window_script> 1 rate:{user_id}:{bucket} {limit} {window_secs} {now}
```

---

## Idempotency

Для защиты от дублирования при retry клиент может отправить заголовок:

```
X-Idempotency-Key: <uuid-v4>
```

**Правила:**
- Применяется к мутационным запросам: `POST`, `PATCH`, `PUT`
- API Gateway сохраняет ключ в Redis: `idempotency:{user_id}:{key}` → response, TTL 24 часа
- Если ключ уже существует — вернуть сохранённый response без повторного выполнения
- Ключ привязан к user_id (разные пользователи могут использовать одинаковые ключи)
- Max длина ключа: 64 символа

**Redis реализация:**

```
# Проверить + сохранить (atomic)
SET idempotency:{user_id}:{key} {response_json} NX EX 86400
```

---

## HTTP Security Headers

Middleware добавляет заголовки ко всем ответам:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Content-Security-Policy: default-src 'self'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

---

## Request/Response Logging

```rust
use tower_http::trace::TraceLayer;
use tracing::{info_span, Span};

fn trace_layer() -> TraceLayer<...> {
    TraceLayer::new_for_http()
        .make_span_with(|request: &Request| {
            let request_id = request.extensions()
                .get::<RequestId>()
                .map(|id| id.to_string())
                .unwrap_or_default();

            info_span!(
                "http_request",
                method = %request.method(),
                path = %request.uri().path(),
                request_id = %request_id,
            )
        })
        .on_response(|response: &Response, latency: Duration, _span: &Span| {
            tracing::info!(
                status = response.status().as_u16(),
                latency_ms = latency.as_millis(),
                "response"
            );
        })
}
```

### Что логируется

- Method, path, status code, latency
- Request ID (X-Request-Id)
- User ID (из JWT, если авторизован)
- IP адрес (из X-Forwarded-For или socket)
- User-Agent

### Что НЕ логируется

- Request/response body
- Authorization header (JWT токен)
- Пароли, токены, секреты

---

## Health Check

```
GET /health → 200 {"status": "ok"}
GET /health/ready → 200 (если NATS + Redis доступны)
GET /health/live → 200 (always, для Kubernetes liveness probe)
```

---

## Metrics

```
GET /metrics → Prometheus format
```

| Метрика | Тип | Описание |
|---------|-----|----------|
| `api_requests_total{method,path,status}` | counter | Запросы по endpoint |
| `api_request_duration_seconds{method,path}` | histogram | Latency |
| `api_rate_limited_total{bucket}` | counter | Отклонённые rate limit-ом |
| `api_auth_failures_total{reason}` | counter | Ошибки аутентификации |
| `api_nats_request_duration_seconds{subject}` | histogram | Время NATS request |
| `api_nats_errors_total{subject}` | counter | Ошибки NATS |
| `api_active_connections` | gauge | Активные HTTP соединения |

---

## Формат ошибок

Все ошибки возвращаются в едином формате:

```json
{
    "code": "RATE_LIMITED",
    "message": "You are being rate limited",
    "retry_after": 1.5
}
```

### Коды ошибок API Gateway

| Код | HTTP | Описание |
|-----|------|----------|
| `UNAUTHORIZED` | 401 | Отсутствует Authorization header |
| `INVALID_TOKEN` | 401 | Невалидный JWT |
| `TOKEN_EXPIRED` | 401 | JWT истёк |
| `RATE_LIMITED` | 429 | Превышен rate limit |
| `SERVICE_UNAVAILABLE` | 503 | Целевой сервис недоступен |
| `SERVICE_TIMEOUT` | 504 | NATS request timeout |
| `NOT_FOUND` | 404 | Маршрут не найден |
| `METHOD_NOT_ALLOWED` | 405 | HTTP метод не поддерживается |
| `PAYLOAD_TOO_LARGE` | 413 | Body > MAX_BODY_SIZE |
| `INTERNAL_ERROR` | 500 | Внутренняя ошибка |

---

## Безопасность

### Чеклист

- [ ] JWT public key в Kubernetes Secret
- [ ] CORS: только whitelist доменов
- [ ] Rate limiting: global (50/1s) + per-endpoint
- [ ] Security headers на всех ответах (HSTS, CSP, nosniff, X-Frame-Options)
- [ ] Request body size limit (25 MiB)
- [ ] Request timeout (30 сек)
- [ ] НЕ логирует токены, пароли, request body
- [ ] Публичные endpoints: только /auth/login, /auth/register, /auth/refresh, /health
- [ ] Все остальные endpoints: JWT required
- [ ] NATS request envelope содержит user_id из валидного JWT
- [ ] Внутренние сервисы НЕ доступны извне (только через API Gateway)
- [ ] X-Request-Id для трассировки
- [ ] 429 ответ содержит Retry-After header
- [ ] Cloudflare ban threshold: 10000 invalid requests (401/403/429) per 10 min
