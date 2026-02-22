# Auth Service

Сервис аутентификации и авторизации. Управляет регистрацией, логином, токенами, OAuth2 и 2FA.

Порт: `3001`
Путь: `services/auth/`
БД: PostgreSQL
Кеш: Redis

## Источники и стандарты

Реализация строго по следующим спецификациям:

- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [RFC 7519 — JSON Web Token](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 7636 — PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 9106 — Argon2](https://datatracker.ietf.org/doc/html/rfc9106)
- [Auth0 — Authorization Code Flow with PKCE](https://auth0.com/docs/get-started/authentication-and-authorization-flow/authorization-code-flow-with-pkce)
- [Google OAuth2 Best Practices](https://developers.google.com/identity/protocols/oauth2/resources/best-practices)
- [RFC 6238 — TOTP](https://datatracker.ietf.org/doc/html/rfc6238)
- [W3C Web Authentication (WebAuthn) Level 2](https://www.w3.org/TR/webauthn-2/)
- [crate: argon2](https://docs.rs/argon2/latest/argon2/)
- [crate: jsonwebtoken](https://docs.rs/jsonwebtoken/latest/jsonwebtoken/)
- [crate: totp-rs](https://docs.rs/totp-rs/latest/totp_rs/)
- [crate: oauth2](https://docs.rs/oauth2/latest/oauth2/)
- [crate: webauthn-rs](https://docs.rs/webauthn-rs/latest/webauthn_rs/)
- [crate: lettre](https://docs.rs/lettre/latest/lettre/)

---

## Структура сервиса

```
services/auth/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── register.rs
│   │   ├── login.rs
│   │   ├── logout.rs
│   │   ├── refresh.rs
│   │   ├── verify_email.rs
│   │   ├── forgot_password.rs
│   │   ├── reset_password.rs
│   │   ├── oauth.rs
│   │   └── two_factor.rs
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── password.rs
│   │   ├── token.rs
│   │   ├── oauth_providers.rs
│   │   └── totp.rs
│   ├── models/
│   │   ├── mod.rs
│   │   ├── user_credentials.rs
│   │   ├── refresh_token.rs
│   │   └── oauth_account.rs
│   ├── middleware/
│   │   ├── mod.rs
│   │   └── rate_limit.rs
│   └── events/
│       ├── mod.rs
│       └── publisher.rs
├── migrations/
│   ├── 001_create_user_credentials.sql
│   ├── 002_create_refresh_tokens.sql
│   ├── 003_create_oauth_accounts.sql
│   ├── 004_create_email_verifications.sql
│   └── 005_create_webauthn_credentials.sql
├── tests/
│   ├── common/mod.rs
│   ├── register_test.rs
│   ├── login_test.rs
│   ├── token_test.rs
│   └── oauth_test.rs
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
jsonwebtoken = { workspace = true }
argon2 = { workspace = true }
tracing = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }
thiserror = { workspace = true }
validator = { workspace = true }
chrono = { workspace = true }
uuid = { workspace = true }
rand = "0.8"
base64 = "0.22"
lettre = "0.11"           # отправка email
oauth2 = "4"              # OAuth2 клиент (Authorization Code + PKCE)
totp-rs = "5"             # TOTP 2FA (RFC 6238)
webauthn-rs = "0.5"       # WebAuthn / FIDO2 (аппаратные ключи)
reqwest = "0.12"          # HTTP клиент (проверка pwned passwords)
sha1 = "0.10"             # k-anonymity для HaveIBeenPwned API
dotenvy = "0.15"          # загрузка .env файлов
config = "0.15"           # типизированная конфигурация из env
deadpool-redis = "0.18"   # Redis connection pool
async-nats = { workspace = true }

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

Переменные окружения:

| Переменная | Обязательная | Default | Описание |
|-----------|-------------|---------|----------|
| `DATABASE_URL` | да | — | PostgreSQL connection string |
| `REDIS_URL` | да | — | Redis connection string |
| `NATS_URL` | да | — | NATS server URL |
| `JWT_PRIVATE_KEY` | да | — | ES256 private key (PEM) |
| `JWT_PUBLIC_KEY` | да | — | ES256 public key (PEM) |
| `JWT_ACCESS_TTL` | нет | `900` | Access token TTL (секунды) |
| `JWT_REFRESH_TTL` | нет | `2592000` | Refresh token TTL (секунды, 30 дней) |
| `ARGON2_MEMORY_KB` | нет | `47104` | Argon2id memory (KiB) |
| `ARGON2_ITERATIONS` | нет | `1` | Argon2id iterations |
| `ARGON2_PARALLELISM` | нет | `1` | Argon2id parallelism |
| `OAUTH_GOOGLE_CLIENT_ID` | нет | — | Google OAuth2 client ID |
| `OAUTH_GOOGLE_CLIENT_SECRET` | нет | — | Google OAuth2 client secret |
| `OAUTH_GITHUB_CLIENT_ID` | нет | — | GitHub OAuth2 client ID |
| `OAUTH_GITHUB_CLIENT_SECRET` | нет | — | GitHub OAuth2 client secret |
| `OAUTH_REDIRECT_BASE_URL` | нет | — | Base URL для OAuth2 callbacks |
| `SMTP_HOST` | нет | — | SMTP сервер (через Notification Service) |
| `HIBP_API_KEY` | нет | — | HaveIBeenPwned API key |
| `WEBAUTHN_RP_ID` | нет | — | WebAuthn Relying Party ID (домен) |
| `WEBAUTHN_RP_ORIGIN` | нет | — | WebAuthn origin URL |
| `SERVICE_PORT` | нет | `3001` | Порт сервиса |
| `RUST_LOG` | нет | `info` | Уровень логирования |

---

## Формат ошибок (стандартный)

Все ошибки возвращаются в едином формате:

```json
{
    "code": "ERROR_CODE",
    "message": "Human-readable description"
}
```

| HTTP код | code | Когда |
|---------|------|-------|
| 400 | `BAD_REQUEST` | Невалидный JSON, отсутствуют обязательные поля |
| 401 | `INVALID_CREDENTIALS` | Неверный email/пароль |
| 401 | `INVALID_TOKEN` | Невалидный или истёкший токен |
| 401 | `INVALID_CODE` | Неверный 2FA код |
| 403 | `EMAIL_NOT_VERIFIED` | Требуется подтверждение email |
| 403 | `ACCOUNT_LOCKED` | Аккаунт заблокирован после 5 неудачных попыток |
| 409 | `ACCOUNT_EXISTS` | Email уже зарегистрирован |
| 422 | `VALIDATION_ERROR` | Невалидные данные (пароль слишком короткий, etc.) |
| 422 | `COMPROMISED_PASSWORD` | Пароль найден в базе утечек |
| 429 | `RATE_LIMITED` | Превышен лимит запросов |
| 500 | `INTERNAL_ERROR` | Внутренняя ошибка (без подробностей клиенту) |

---

## Схема базы данных

```sql
-- migrations/001_create_user_credentials.sql
CREATE TABLE user_credentials (
    user_id         BIGINT PRIMARY KEY,           -- Snowflake ID, внешний ключ к User Service
    email           VARCHAR(255) UNIQUE NOT NULL,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash   TEXT NOT NULL,                 -- Argon2id PHC string
    totp_secret     TEXT,                          -- зашифровано, NULL если 2FA выключен
    totp_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    recovery_codes  TEXT[],                        -- одноразовые коды восстановления
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_credentials_email ON user_credentials (email);

-- migrations/002_create_refresh_tokens.sql
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES user_credentials(user_id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,                 -- SHA-256 хеш токена
    device_info     JSONB,                         -- user-agent, IP (для отображения сессий)
    expires_at      TIMESTAMPTZ NOT NULL,
    rotated_at      TIMESTAMPTZ,                   -- когда был использован для ротации
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);

-- migrations/003_create_oauth_accounts.sql
CREATE TABLE oauth_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES user_credentials(user_id) ON DELETE CASCADE,
    provider        VARCHAR(32) NOT NULL,          -- "google", "github", "apple"
    provider_id     VARCHAR(255) NOT NULL,         -- ID юзера у провайдера
    email           VARCHAR(255),
    access_token    TEXT,                           -- зашифровано
    refresh_token   TEXT,                           -- зашифровано
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_id)
);

CREATE INDEX idx_oauth_accounts_user ON oauth_accounts (user_id);

-- migrations/004_create_email_verifications.sql
CREATE TABLE email_verifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES user_credentials(user_id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,
    purpose         VARCHAR(32) NOT NULL,          -- "verify_email", "reset_password"
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verifications_hash ON email_verifications (token_hash);

-- migrations/005_create_webauthn_credentials.sql
CREATE TABLE webauthn_credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES user_credentials(user_id) ON DELETE CASCADE,
    credential_id   BYTEA NOT NULL UNIQUE,              -- идентификатор ключа
    public_key      BYTEA NOT NULL,                     -- публичный ключ COSE
    counter         BIGINT NOT NULL DEFAULT 0,          -- счётчик для replay protection
    name            VARCHAR(64) NOT NULL,               -- пользовательское имя ключа ("YubiKey 5")
    transports      TEXT[],                             -- "usb", "nfc", "ble", "internal"
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webauthn_credentials_user ON webauthn_credentials (user_id);
```

---

## API Endpoints

### POST /auth/register

Регистрация нового пользователя.

**Request:**
```json
{
    "username": "john",
    "email": "john@example.com",
    "password": "secureP@ssw0rd123"
}
```

**Response 201:**
```json
{
    "user_id": "1234567890123456",
    "email": "john@example.com",
    "email_verified": false
}
```

**Response 409:** `{ "code": "ACCOUNT_EXISTS", "message": "An account with this email already exists" }`
**Response 422:** `{ "code": "VALIDATION_ERROR", "message": "Password must be at least 8 characters" }`

**Логика:**
1. Валидация входных данных (`validator`)
2. Нормализация email (lowercase, trim)
3. Проверка email на уникальность
4. Проверка пароля по HaveIBeenPwned API (k-anonymity, SHA-1 prefix)
5. Генерация Snowflake ID
6. Хеширование пароля: Argon2id (см. параметры ниже)
7. Сохранение в БД
8. Генерация email verification token (32 bytes, random)
9. Отправка verification email (NATS → Notification Service)
10. Публикация NATS события `auth.user.registered`

**Валидация пароля (по OWASP Authentication Cheat Sheet):**
- Минимум 8 символов (с учётом обязательного 2FA в будущем)
- Максимум 128 символов
- Все Unicode-символы разрешены, включая пробелы
- Не обрезать пароль
- Проверка по базе утечённых паролей (HaveIBeenPwned)

**OWASP: НЕ требовать специальные символы, заглавные буквы или цифры — это не повышает безопасность, а ведёт к предсказуемым паттернам (Password1!, Welcome123#).**

---

### POST /auth/login

**Request:**
```json
{
    "email": "john@example.com",
    "password": "secureP@ssw0rd123"
}
```

**Response 200 (без 2FA):**
```json
{
    "access_token": "eyJhbGciOiJFUzI1NiIs...",
    "refresh_token": "dGhpcyBpcyBhIHJlZnJl...",
    "expires_in": 900,
    "token_type": "Bearer"
}
```

**Response 200 (с 2FA):**
```json
{
    "requires_2fa": true,
    "ticket": "tmp_ticket_abc123..."
}
```

**Response 401:** `{ "code": "INVALID_CREDENTIALS", "message": "Invalid email or password" }`
**Response 429:** `{ "code": "RATE_LIMITED", "message": "Too many attempts. Try again in 15 minutes" }`

**Логика:**
1. Rate limiting: 5 попыток / 15 минут per IP + per email
2. Найти пользователя по email
3. **OWASP: если пользователь не найден — НЕ говорить "email не найден". Вернуть общее "Invalid email or password"**
4. Проверить lockout (`locked_until > NOW()`)
5. Верифицировать пароль через Argon2id
6. При неудаче: инкрементировать `failed_attempts`
7. При `failed_attempts >= 5`: установить `locked_until = NOW() + 15min`
8. При успехе: сбросить `failed_attempts = 0`
9. Если 2FA включён: вернуть временный ticket (TTL 5 минут, Redis)
10. Если 2FA выключен: сгенерировать access + refresh tokens
11. Сохранить refresh token hash в БД
12. Публикация NATS события `auth.user.login`

---

### POST /auth/login/2fa

**Request:**
```json
{
    "ticket": "tmp_ticket_abc123...",
    "code": "123456"
}
```

**Response 200:** Те же access + refresh tokens
**Response 401:** `{ "code": "INVALID_CODE", "message": "Invalid verification code" }`

**Логика:**
1. Достать ticket из Redis, проверить TTL
2. Верифицировать TOTP код (`totp-rs` crate, RFC 6238)
3. Допустить ±1 временное окно (30 сек) для компенсации рассинхронизации
4. Если код — recovery code: пометить как использованный
5. Сгенерировать access + refresh tokens

---

### POST /auth/refresh

Ротация refresh token. Старый токен инвалидируется, выдаётся новая пара.

**Request:**
```json
{
    "refresh_token": "dGhpcyBpcyBhIHJlZnJl..."
}
```

**Response 200:** Новая пара access + refresh tokens
**Response 401:** `{ "code": "INVALID_TOKEN", "message": "Invalid or expired refresh token" }`

**Логика (по OWASP Session Management Cheat Sheet):**
1. Хешировать входящий refresh token (SHA-256)
2. Найти в БД по хешу
3. Проверить: не revoked, не expired
4. Если токен уже был rotated (`rotated_at IS NOT NULL`):
   - **Reuse detection** — кто-то украл токен
   - Отозвать ВСЕ refresh tokens этого пользователя
   - Залогировать security event
   - Вернуть 401
5. Пометить старый токен: `rotated_at = NOW()`
6. Создать новый refresh token, сохранить hash в БД
7. Сгенерировать новый access token

---

### POST /auth/logout

**Request:** Header `Authorization: Bearer <access_token>`

**Response 204:** No content

**Логика:**
1. Извлечь `user_id` из JWT
2. Добавить JTI (JWT ID) в Redis denylist с TTL = оставшееся время жизни токена
3. Отозвать текущий refresh token (`revoked = true`)
4. Публикация NATS события `auth.user.logout`

---

### POST /auth/verify-email

**Request:**
```json
{
    "token": "verification_token_here"
}
```

**Response 200:** `{ "message": "Email verified successfully" }`

**Логика:**
1. Хешировать входящий token (SHA-256)
2. Найти в `email_verifications` по хешу
3. Проверить: не expired, не used, purpose = "verify_email"
4. Пометить `used = true`
5. Обновить `user_credentials.email_verified = true`
6. Публикация NATS события `auth.email.verified`

---

### POST /auth/forgot-password

**Request:**
```json
{
    "email": "john@example.com"
}
```

**Response 200:** `{ "message": "If the email exists, a reset link has been sent" }`

**OWASP: всегда возвращать одинаковый ответ, даже если email не найден (предотвращение перечисления пользователей).**

**Логика:**
1. Rate limiting: 3 запроса / 1 час per email
2. Найти пользователя по email
3. Если не найден — вернуть тот же 200 (без отправки)
4. Сгенерировать reset token (32 bytes, random)
5. Сохранить hash в `email_verifications` (purpose = "reset_password", TTL 1 час)
6. Отправить email с ссылкой (NATS → Notification Service)

---

### POST /auth/reset-password

**Request:**
```json
{
    "token": "reset_token_here",
    "new_password": "newSecureP@ss456"
}
```

**Response 200:** `{ "message": "Password updated successfully" }`

**Логика:**
1. Валидировать новый пароль (те же правила, что при регистрации)
2. Проверить reset token (hash → БД → not expired, not used)
3. Хешировать новый пароль (Argon2id)
4. Обновить `password_hash`
5. Пометить token как `used = true`
6. Отозвать ВСЕ refresh tokens пользователя (принудительный re-login)
7. Залогировать событие смены пароля

---

### GET /auth/oauth/:provider

Инициация OAuth2 Authorization Code Flow + PKCE.

**Providers:** `google`, `github`, `apple`

**Response 302:** Redirect на страницу провайдера

**Логика (по RFC 7636 и Auth0 PKCE Guide):**
1. Сгенерировать `code_verifier` (43-128 символов, криптографически случайный)
2. Вычислить `code_challenge = BASE64URL(SHA256(code_verifier))`
3. Сгенерировать `state` параметр (CSRF защита)
4. Сохранить `code_verifier` и `state` в Redis (TTL 10 минут)
5. Redirect на authorization URL провайдера с параметрами:
   - `response_type=code`
   - `client_id`
   - `redirect_uri`
   - `scope` (email, profile)
   - `state`
   - `code_challenge`
   - `code_challenge_method=S256`

---

### GET /auth/oauth/:provider/callback

Callback после авторизации у провайдера.

**Логика:**
1. Проверить `state` параметр (Redis, одноразовый)
2. Обменять `code` на access token у провайдера, передав `code_verifier`
3. Получить профиль пользователя от провайдера (email, name, avatar)
4. Найти `oauth_accounts` по (provider, provider_id):
   - Найден → логин (сгенерировать токены)
   - Не найден → проверить email в `user_credentials`:
     - Email есть → привязать OAuth аккаунт к существующему пользователю
     - Email нет → создать нового пользователя (email_verified = true)
5. Сгенерировать access + refresh tokens
6. Redirect на клиент с токенами (через fragment или secure cookie)

---

### POST /auth/2fa/enable

**Request:** Header `Authorization: Bearer <access_token>`

**Response 200:**
```json
{
    "secret": "JBSWY3DPEHPK3PXP",
    "qr_code_uri": "otpauth://totp/App:john@example.com?secret=JBSWY3DPEHPK3PXP&issuer=App",
    "recovery_codes": [
        "a1b2c3d4", "e5f6g7h8", "i9j0k1l2",
        "m3n4o5p6", "q7r8s9t0", "u1v2w3x4",
        "y5z6a7b8", "c9d0e1f2", "g3h4i5j6",
        "k7l8m9n0"
    ]
}
```

**Логика:**
1. Проверить аутентификацию (JWT middleware)
2. Потребовать повторный ввод пароля (re-authentication, OWASP)
3. Сгенерировать TOTP secret (base32, 160 бит)
4. Сгенерировать 10 recovery codes (8 символов, alphanumeric)
5. Вернуть secret и QR URI (НЕ сохранять в БД ещё)
6. Пользователь должен подтвердить кодом (POST /auth/2fa/confirm)

---

### POST /auth/2fa/confirm

**Request:**
```json
{
    "code": "123456"
}
```

**Логика:**
1. Верифицировать TOTP код с временным secret
2. Если валидный — сохранить secret в БД (зашифрованный)
3. Сохранить recovery codes (хешированные, Argon2id)
4. Установить `totp_enabled = true`

---

### POST /auth/2fa/disable

Отключение 2FA.

**Request:**
```json
{
    "password": "currentPassword123"
}
```

**Логика:**
1. Потребовать повторный ввод пароля (re-authentication)
2. Удалить `totp_secret`, `recovery_codes`
3. Установить `totp_enabled = false`
4. Удалить все WebAuthn credentials
5. Публикация NATS `auth.2fa.disabled`

---

### POST /auth/webauthn/register/begin

Начало регистрации аппаратного ключа (WebAuthn).

Источник: [W3C WebAuthn Level 2](https://www.w3.org/TR/webauthn-2/)

**Request:** Header `Authorization: Bearer <access_token>`

**Response 200:**
```json
{
    "challenge": "base64url-encoded-challenge",
    "rp": { "name": "AppName", "id": "example.com" },
    "user": { "id": "base64url-user-id", "name": "john", "displayName": "John Doe" },
    "pubKeyCredParams": [
        { "alg": -7, "type": "public-key" },
        { "alg": -257, "type": "public-key" }
    ],
    "timeout": 60000,
    "attestation": "none",
    "authenticatorSelection": {
        "residentKey": "preferred",
        "userVerification": "preferred"
    }
}
```

**Логика:**
1. Потребовать повторный ввод пароля
2. Сгенерировать challenge через `webauthn-rs`
3. Сохранить challenge state в Redis (TTL 2 мин)
4. Вернуть PublicKeyCredentialCreationOptions

---

### POST /auth/webauthn/register/complete

Завершение регистрации аппаратного ключа.

**Request:**
```json
{
    "name": "YubiKey 5",
    "credential": { "... attestation response от браузера ..." }
}
```

**Логика:**
1. Верифицировать attestation response через `webauthn-rs`
2. Сохранить credential в `webauthn_credentials`
3. Если это первый security key и TOTP не включен — активировать 2FA

---

### POST /auth/webauthn/authenticate/begin

Начало аутентификации аппаратным ключом (вместо TOTP кода).

**Request:**
```json
{
    "ticket": "tmp_ticket_abc123..."
}
```

**Response 200:** PublicKeyCredentialRequestOptions

---

### POST /auth/webauthn/authenticate/complete

Завершение аутентификации аппаратным ключом.

**Логика:**
1. Верифицировать assertion response через `webauthn-rs`
2. Обновить counter (replay protection)
3. Сгенерировать access + refresh tokens

---

### DELETE /auth/webauthn/:credential_id

Удаление аппаратного ключа.

**Авторизация:** re-authentication (пароль)

**Логика:**
1. Проверить что у пользователя останется хотя бы один способ 2FA (TOTP или другой ключ)
2. Удалить credential из БД

---

### GET /auth/sessions

Список активных сессий (refresh tokens).

**Response 200:**
```json
{
    "sessions": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "device_info": {
                "user_agent": "Mozilla/5.0...",
                "ip": "192.168.1.1"
            },
            "created_at": "2025-01-15T10:00:00Z",
            "current": true
        }
    ]
}
```

---

### DELETE /auth/sessions/:session_id

Отзыв конкретной сессии.

---

### DELETE /auth/sessions

Отзыв всех сессий кроме текущей.

---

## Хеширование паролей

Строго по [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) и [RFC 9106](https://datatracker.ietf.org/doc/html/rfc9106).

**Алгоритм:** Argon2id (гибрид, устойчив к side-channel и GPU атакам)

**Параметры (OWASP рекомендация, первый вариант):**
```
m = 47104 (46 MiB)
t = 1 (итерации)
p = 1 (параллелизм)
```

**Альтернативные параметры (если ограничены ресурсы):**
```
m = 19456 (19 MiB)
t = 2 (итерации)
p = 1 (параллелизм)
```

**Формат хранения:** PHC string format (стандарт `argon2` crate):
```
$argon2id$v=19$m=47104,t=1,p=1$<salt>$<hash>
```

Salt генерируется автоматически crate, 16 bytes, криптографически случайный.

---

## JWT токены

По [RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519) и [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html).

### Access Token

**Алгоритм:** ES256 (ECDSA P-256) — предпочтительнее HS256 по OWASP (асимметричный, не нужен shared secret для верификации)

**TTL:** 15 минут

**Payload (минимальный, по OWASP):**
```json
{
    "sub": "1234567890123456",
    "iat": 1700000000,
    "exp": 1700000900,
    "jti": "550e8400-e29b-41d4-a716-446655440000"
}
```

- `sub` — user_id (Snowflake ID)
- `iat` — время выпуска
- `exp` — время истечения
- `jti` — уникальный ID токена (UUID v7, для denylist при logout)

**НЕ включать в payload:** роли, права, email, username. Эти данные запрашиваются из кеша/БД по `sub`.

### Refresh Token

**Формат:** Opaque (32 bytes, криптографически случайный, base64url)
**TTL:** 30 дней
**Хранение:** SHA-256 hash в PostgreSQL
**Ротация:** при каждом использовании (OWASP)
**Reuse detection:** если использован уже rotated token → отзыв всех токенов пользователя

---

## Rate Limiting

| Endpoint | Лимит | Окно | Ключ |
|----------|-------|------|------|
| POST /auth/register | 3 req | 1 час | IP |
| POST /auth/login | 5 req | 15 мин | IP + email |
| POST /auth/refresh | 30 req | 1 мин | user_id |
| POST /auth/forgot-password | 3 req | 1 час | email |
| POST /auth/login/2fa | 5 req | 5 мин | ticket |

Реализация: Redis sliding window (`crates/rate-limit`).

При превышении:
- HTTP 429 Too Many Requests
- Header `Retry-After: <seconds>`
- Header `X-RateLimit-Remaining: 0`

---

## NATS события

Все события публикуются в NATS JetStream.

| Событие | Payload | Подписчики |
|---------|---------|-----------|
| `auth.user.registered` | `{ user_id, email }` | User Service (создать профиль), Notification Service (welcome email) |
| `auth.user.login` | `{ user_id, ip, user_agent, timestamp }` | Presence Service |
| `auth.user.logout` | `{ user_id }` | Presence Service, Gateway |
| `auth.email.verified` | `{ user_id }` | User Service |
| `auth.password.changed` | `{ user_id }` | Notification Service (уведомление о смене) |
| `auth.security.suspicious` | `{ user_id, reason, ip }` | Moderation Service (логирование) |

---

## Безопасность (чеклист)

По [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html):

- [ ] Пароли хешируются Argon2id с параметрами по OWASP
- [ ] Общие сообщения об ошибках ("Invalid email or password")
- [ ] Rate limiting на все auth endpoints
- [ ] Account lockout после 5 неудачных попыток
- [ ] Refresh token rotation с reuse detection
- [ ] JWT denylist при logout (Redis, TTL = exp)
- [ ] Email verification обязательна
- [ ] Пароли проверяются по HaveIBeenPwned
- [ ] Повторная аутентификация при смене пароля/email
- [ ] OAuth2 с PKCE (code_challenge_method=S256)
- [ ] state параметр в OAuth2 (CSRF защита)
- [ ] TOTP secret хранится зашифрованным
- [ ] Recovery codes хешированы
- [ ] Все auth события логируются
- [ ] TLS для всех соединений

---

## Мониторинг

| Метрика | Тип | Описание |
|---------|-----|----------|
| `auth_registrations_total` | counter | Регистрации |
| `auth_logins_total{status}` | counter | Логины (success/failure) |
| `auth_login_failures_total{reason}` | counter | Неудачные логины по причине (invalid_credentials, locked, rate_limited) |
| `auth_token_refreshes_total` | counter | Обновления access token |
| `auth_token_refresh_failures_total{reason}` | counter | Ошибки refresh (expired, revoked, reuse_detected) |
| `auth_2fa_verifications_total{status}` | counter | 2FA проверки (success/failure) |
| `auth_oauth_logins_total{provider}` | counter | OAuth логины по провайдеру |
| `auth_password_hash_duration_seconds` | histogram | Время хеширования Argon2id |
| `auth_lockouts_total` | counter | Account lockouts |
| `auth_hibp_check_duration_seconds` | histogram | Время запроса к HaveIBeenPwned API |
| `auth_active_refresh_tokens` | gauge | Активных refresh tokens |
