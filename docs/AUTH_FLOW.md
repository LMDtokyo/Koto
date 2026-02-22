# Аутентификация и авторизация: полный flow

Документ описывает все потоки аутентификации и авторизации платформы: жизненный цикл токенов, ротацию refresh token, revocation, проверку прав, OAuth2, 2FA, WebSocket аутентификацию, мульти-сессии и бот-аутентификацию.

## Источники и стандарты

- [RFC 7519 --- JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 6749 --- OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636 --- Proof Key for Code Exchange (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 6238 --- TOTP: Time-Based One-Time Password Algorithm](https://datatracker.ietf.org/doc/html/rfc6238)
- [RFC 7009 --- OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009)
- [RFC 9106 --- Argon2 Memory-Hard Function](https://datatracker.ietf.org/doc/html/rfc9106)
- [W3C Web Authentication (WebAuthn) Level 3](https://www.w3.org/TR/webauthn-3/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [Auth0 --- Refresh Tokens: What Are They and When to Use Them](https://auth0.com/blog/refresh-tokens-what-are-they-and-when-to-use-them/)

---

## Содержание

1. [Жизненный цикл токенов](#1-жизненный-цикл-токенов)
2. [Refresh Token Rotation](#2-refresh-token-rotation)
3. [Token Revocation / Denylist](#3-token-revocation--denylist)
4. [Pipeline проверки прав (Permission Resolution)](#4-pipeline-проверки-прав-permission-resolution)
5. [OAuth2 Flow (Google, GitHub, Apple)](#5-oauth2-flow-google-github-apple)
6. [2FA (TOTP + WebAuthn)](#6-2fa-totp--webauthn)
7. [WebSocket Authentication](#7-websocket-authentication)
8. [Multi-device / Multi-session](#8-multi-device--multi-session)
9. [Bot Authentication](#9-bot-authentication)

---

## 1. Жизненный цикл токенов

### 1.1. Обзор

Платформа использует двухтокенную схему по [RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519) и [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749):

| Токен | Формат | Алгоритм | TTL | Хранение |
|-------|--------|----------|-----|----------|
| **Access Token** | JWT (RFC 7519) | ES256 (ECDSA P-256) | 15 минут | Клиент (memory / httpOnly cookie) |
| **Refresh Token** | Opaque (32 random bytes, hex) | --- | 30 дней | SHA-256 hash в PostgreSQL |

**Access Token** --- короткоживущий, содержит минимум данных. Используется для аутентификации каждого API запроса.

**Refresh Token** --- долгоживущий, непрозрачный. Используется исключительно для получения новой пары access+refresh. При каждом использовании ротируется (старый инвалидируется).

### 1.2. Access Token (JWT ES256)

Подписан алгоритмом ES256 (ECDSA P-256) --- асимметричная подпись. Для верификации нужен только публичный ключ, приватный ключ хранится исключительно в Auth Service.

**Payload (минимальный, по OWASP):**

```json
{
    "sub": "1234567890123456",
    "iat": 1700000000,
    "exp": 1700000900,
    "jti": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Claim | Описание |
|-------|----------|
| `sub` | user_id (Snowflake ID) |
| `iat` | Время выпуска (Unix timestamp) |
| `exp` | Время истечения (Unix timestamp, iat + 900) |
| `jti` | Уникальный ID токена (UUID v7, для denylist) |

**В payload НЕ включаются:** роли, права, email, username. Эти данные запрашиваются из Redis-кеша или PostgreSQL по `sub`.

### 1.3. Refresh Token

- 32 криптографически случайных байта, закодированных в base64url
- В БД хранится **только SHA-256 хеш** токена (сам токен известен только клиенту)
- Привязан к device fingerprint (user-agent, IP)
- Ротация при каждом использовании

### 1.4. Полный flow: Регистрация -> Логин -> Использование

```
                    РЕГИСТРАЦИЯ
                    ===========

Client                    API Gateway (:3000)         Auth Service (:3001)       PostgreSQL       Redis
  |                            |                            |                       |               |
  |-- POST /auth/register ---->|                            |                       |               |
  |   {email, password,        |-- NATS req: auth.register->|                       |               |
  |    username}               |                            |                       |               |
  |                            |                            |-- Validate input      |               |
  |                            |                            |-- Check email unique ->|               |
  |                            |                            |<- ok / conflict -------|               |
  |                            |                            |-- Check HaveIBeenPwned (HTTP)         |
  |                            |                            |-- Hash password (Argon2id)            |
  |                            |                            |-- Generate Snowflake ID               |
  |                            |                            |-- INSERT user_credentials ->|          |
  |                            |                            |<- ok --------------------|            |
  |                            |                            |-- Generate email verification token   |
  |                            |                            |-- NATS publish: auth.user.registered  |
  |                            |<-- {user_id, email} -------|                       |               |
  |<-- 201 Created ------------|                            |                       |               |


                    ПОДТВЕРЖДЕНИЕ EMAIL
                    ===================

Client                    API Gateway                 Auth Service               PostgreSQL
  |                            |                            |                       |
  |-- POST /auth/verify-email->|                            |                       |
  |   {token}                  |-- NATS req: auth.verify -->|                       |
  |                            |                            |-- SHA-256(token)      |
  |                            |                            |-- SELECT from         |
  |                            |                            |   email_verifications->|
  |                            |                            |<- row ----------------|
  |                            |                            |-- Validate: not used, |
  |                            |                            |   not expired,        |
  |                            |                            |   purpose=verify_email|
  |                            |                            |-- UPDATE email_verified|
  |                            |                            |   = true ------------>|
  |                            |<-- 200 OK -----------------|                       |
  |<-- 200 "Email verified" ---|                            |                       |


                    ЛОГИН (без 2FA)
                    ===============

Client                    API Gateway                 Auth Service               PostgreSQL       Redis
  |                            |                            |                       |               |
  |-- POST /auth/login ------->|                            |                       |               |
  |   {email, password}        |-- Rate limit check ------->|                       |      check -->|
  |                            |                            |                       |               |
  |                            |-- NATS req: auth.login --->|                       |               |
  |                            |                            |-- SELECT by email --->|               |
  |                            |                            |<- user_credentials ---|               |
  |                            |                            |-- Check lockout       |               |
  |                            |                            |-- Verify Argon2id     |               |
  |                            |                            |-- Reset failed_attempts|              |
  |                            |                            |-- Generate access token (JWT ES256)   |
  |                            |                            |-- Generate refresh token (32 bytes)   |
  |                            |                            |-- Store SHA-256(refresh)              |
  |                            |                            |   + device_info ----->|               |
  |                            |                            |-- NATS: auth.user.login               |
  |                            |<-- {access_token,          |                       |               |
  |                            |     refresh_token,         |                       |               |
  |                            |     expires_in: 900} ------|                       |               |
  |<-- 200 OK + tokens --------|                            |                       |               |


                    ИСПОЛЬЗОВАНИЕ ACCESS TOKEN
                    ==========================

Client                    API Gateway                 Target Service
  |                            |                            |
  |-- GET /users/@me --------->|                            |
  |   Authorization: Bearer JWT|                            |
  |                            |-- Verify JWT (ES256 pubkey)|
  |                            |-- Check exp > now          |
  |                            |-- Check JTI not in denylist|
  |                            |   (Redis)                  |
  |                            |-- Extract user_id from sub |
  |                            |                            |
  |                            |-- NATS req: users.get_me ->|
  |                            |   {user_id, request_id}    |
  |                            |                            |-- Process request
  |                            |<-- response ---------------|
  |<-- 200 OK + user data -----|                            |


                    ОБНОВЛЕНИЕ ТОКЕНОВ (REFRESH)
                    ============================

Client                    API Gateway                 Auth Service               PostgreSQL       Redis
  |                            |                            |                       |               |
  |-- POST /auth/refresh ----->|                            |                       |               |
  |   {refresh_token}          |-- NATS req: auth.refresh ->|                       |               |
  |                            |                            |-- SHA-256(token)      |               |
  |                            |                            |-- SELECT by hash ---->|               |
  |                            |                            |<- refresh_token row --|               |
  |                            |                            |-- Check: not revoked, |               |
  |                            |                            |   not expired,        |               |
  |                            |                            |   rotated_at IS NULL  |               |
  |                            |                            |-- Mark old:           |               |
  |                            |                            |   rotated_at=NOW() -->|               |
  |                            |                            |-- Generate new pair   |               |
  |                            |                            |-- Store new refresh ->|               |
  |                            |<-- {new access_token,      |                       |               |
  |                            |     new refresh_token,     |                       |               |
  |                            |     expires_in: 900} ------|                       |               |
  |<-- 200 OK + new tokens ----|                            |                       |               |
```

### 1.5. Генерация токенов (Rust)

```rust
use jsonwebtoken::{encode, Header, EncodingKey, Algorithm};
use chrono::Utc;
use uuid::Uuid;

#[derive(Debug, serde::Serialize)]
struct Claims {
    sub: String,   // user_id (Snowflake)
    iat: i64,      // issued at
    exp: i64,      // expiration
    jti: String,   // unique token ID
}

fn generate_access_token(
    user_id: i64,
    private_key: &[u8],
    ttl_secs: i64,
) -> Result<String, AppError> {
    let now = Utc::now().timestamp();
    let claims = Claims {
        sub: user_id.to_string(),
        iat: now,
        exp: now + ttl_secs,
        jti: Uuid::now_v7().to_string(),
    };

    let header = Header::new(Algorithm::ES256);
    let key = EncodingKey::from_ec_pem(private_key)
        .map_err(|e| AppError::Internal(e.into()))?;

    encode(&header, &claims, &key)
        .map_err(|e| AppError::Internal(e.into()))
}

fn generate_refresh_token() -> Result<(String, String), AppError> {
    use rand::RngCore;
    use sha2::{Sha256, Digest};

    let mut bytes = [0u8; 32];
    rand::thread_rng().try_fill_bytes(&mut bytes)
        .map_err(|e| AppError::Internal(e.into()))?;

    let token = base64_url::encode(&bytes);
    let hash = hex::encode(Sha256::digest(token.as_bytes()));

    Ok((token, hash)) // token -> клиенту, hash -> в БД
}
```

### 1.6. Хеширование паролей (Argon2id)

По [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) и [RFC 9106](https://datatracker.ietf.org/doc/html/rfc9106).

Параметры:

```
Алгоритм: Argon2id (гибрид, устойчив к side-channel и GPU атакам)
m = 47104 (46 MiB)
t = 1 (итерация)
p = 1 (параллелизм)
Формат: $argon2id$v=19$m=47104,t=1,p=1$<salt>$<hash>
```

```rust
use argon2::{Argon2, PasswordHasher, PasswordVerifier, password_hash::SaltString};

fn hash_password(password: &str) -> Result<String, AppError> {
    let salt = SaltString::generate(&mut rand::thread_rng());
    let argon2 = Argon2::new(
        argon2::Algorithm::Argon2id,
        argon2::Version::V0x13,
        argon2::Params::new(47104, 1, 1, None)
            .map_err(|e| AppError::Internal(e.into()))?,
    );

    argon2
        .hash_password(password.as_bytes(), &salt)
        .map(|h| h.to_string())
        .map_err(|e| AppError::Internal(e.into()))
}

fn verify_password(password: &str, hash: &str) -> Result<bool, AppError> {
    let parsed_hash = argon2::PasswordHash::new(hash)
        .map_err(|e| AppError::Internal(e.into()))?;

    Ok(Argon2::default()
        .verify_password(password.as_bytes(), &parsed_hash)
        .is_ok())
}
```

---

## 2. Refresh Token Rotation

Источник: [Auth0 --- Refresh Tokens](https://auth0.com/blog/refresh-tokens-what-are-they-and-when-to-use-them/), [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

### 2.1. Принцип

При каждом использовании refresh token:

1. Старый refresh token помечается как использованный (`rotated_at = NOW()`)
2. Генерируется новая пара access + refresh token
3. Новый refresh token сохраняется в БД
4. Клиент получает обе новых пары

Это обеспечивает:
- **Ограниченное окно атаки** --- украденный refresh token можно использовать только один раз
- **Обнаружение кражи** --- повторное использование уже rotated токена сигнализирует о компрометации

### 2.2. Family ID и обнаружение reuse

Каждый refresh token принадлежит "семье" (family). Family начинается при логине и продолжается через цепочку ротаций.

```
Login -> RT_1 (family_id: abc)
Refresh(RT_1) -> RT_2 (family_id: abc), RT_1 marked rotated_at
Refresh(RT_2) -> RT_3 (family_id: abc), RT_2 marked rotated_at
...
```

Если атакующий украл RT_1 и пытается его использовать после того, как легитимный пользователь уже сделал refresh:

```
                    НОРМАЛЬНАЯ РОТАЦИЯ
                    ==================

Client (легитимный)           Auth Service                    PostgreSQL
  |                                |                               |
  |-- refresh(RT_1) -------------->|                               |
  |                                |-- SELECT by hash(RT_1) ----->|
  |                                |<- {rotated_at: NULL} --------|
  |                                |-- UPDATE rotated_at=NOW() -->|
  |                                |-- INSERT RT_2 (new) -------->|
  |<-- {access_token_2, RT_2} -----|                               |


                    REUSE DETECTION (атака)
                    =======================

Attacker (украл RT_1)         Auth Service                    PostgreSQL
  |                                |                               |
  |-- refresh(RT_1) -------------->|                               |
  |                                |-- SELECT by hash(RT_1) ----->|
  |                                |<- {rotated_at: "2025-01-15"} |  <-- УЖЕ ИСПОЛЬЗОВАН
  |                                |                               |
  |                                |== REUSE DETECTED! ===========|
  |                                |                               |
  |                                |-- REVOKE ALL refresh tokens  |
  |                                |   WHERE user_id = X -------->|  <-- ВСЕ сессии
  |                                |-- Log security event         |
  |                                |-- NATS: auth.security.suspicious
  |<-- 401 INVALID_TOKEN ----------|                               |
```

### 2.3. Логика refresh (Rust)

```rust
async fn refresh_token(
    pool: &PgPool,
    incoming_token: &str,
) -> Result<TokenPair, AppError> {
    let token_hash = sha256_hex(incoming_token);

    // Найти refresh token по хешу
    let stored = sqlx::query_as!(
        RefreshTokenRow,
        r#"SELECT id, user_id, token_hash, expires_at, rotated_at, revoked
           FROM refresh_tokens WHERE token_hash = $1"#,
        &token_hash
    )
    .fetch_optional(pool)
    .await
    .map_err(|e| AppError::Internal(e.into()))?
    .ok_or(AppError::Unauthorized)?;

    // Проверка: не revoked
    if stored.revoked {
        return Err(AppError::Unauthorized);
    }

    // Проверка: не expired
    if stored.expires_at < Utc::now() {
        return Err(AppError::Unauthorized);
    }

    // Проверка: reuse detection
    if stored.rotated_at.is_some() {
        // Токен уже был использован -> КРАЖА!
        // Отозвать ВСЕ refresh tokens пользователя
        sqlx::query!(
            "UPDATE refresh_tokens SET revoked = true WHERE user_id = $1",
            stored.user_id
        )
        .execute(pool)
        .await
        .map_err(|e| AppError::Internal(e.into()))?;

        tracing::warn!(
            user_id = stored.user_id,
            token_id = %stored.id,
            "Refresh token reuse detected, all sessions revoked"
        );

        return Err(AppError::Unauthorized);
    }

    // Пометить старый токен как использованный
    sqlx::query!(
        "UPDATE refresh_tokens SET rotated_at = NOW() WHERE id = $1",
        stored.id
    )
    .execute(pool)
    .await
    .map_err(|e| AppError::Internal(e.into()))?;

    // Сгенерировать новую пару
    let (new_refresh, new_hash) = generate_refresh_token()?;
    let new_access = generate_access_token(stored.user_id, &private_key, 900)?;

    // Сохранить новый refresh token
    sqlx::query!(
        r#"INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
           VALUES ($1, $2, NOW() + INTERVAL '30 days')"#,
        stored.user_id,
        &new_hash
    )
    .execute(pool)
    .await
    .map_err(|e| AppError::Internal(e.into()))?;

    Ok(TokenPair {
        access_token: new_access,
        refresh_token: new_refresh,
        expires_in: 900,
        token_type: "Bearer".to_string(),
    })
}
```

### 2.4. Таблица состояний refresh token

| Состояние | `revoked` | `rotated_at` | Действие при попытке использования |
|-----------|-----------|-------------|-----------------------------------|
| Активный | `false` | `NULL` | Ротация: создать новую пару, пометить `rotated_at` |
| Ротированный | `false` | `<timestamp>` | **Reuse detected**: отозвать ВСЕ токены пользователя |
| Отозванный | `true` | любое | Вернуть 401 |
| Истёкший | любое | любое | Вернуть 401 (проверка `expires_at`) |

---

## 3. Token Revocation / Denylist

Источник: [RFC 7009 --- OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009).

### 3.1. Проблема

JWT --- stateless токены. После выпуска их нельзя "отозвать" на уровне подписи. Но при logout или компрометации аккаунта необходимо немедленно прекратить действие токенов.

### 3.2. Решение: Redis Denylist

При logout JTI (JWT ID) access token добавляется в Redis denylist. TTL записи в denylist равен оставшемуся времени жизни access token (нет смысла хранить дольше --- токен всё равно истечёт).

```
                    LOGOUT
                    ======

Client                    API Gateway                 Auth Service               PostgreSQL       Redis
  |                            |                            |                       |               |
  |-- POST /auth/logout ------>|                            |                       |               |
  |   Authorization: Bearer JWT|                            |                       |               |
  |                            |-- Extract JTI from JWT     |                       |               |
  |                            |-- NATS req: auth.logout -->|                       |               |
  |                            |                            |                       |               |
  |                            |                            |-- Compute remaining   |               |
  |                            |                            |   TTL = exp - now     |               |
  |                            |                            |                       |               |
  |                            |                            |-- SET denylist:{jti}  |               |
  |                            |                            |   "1" EX {remaining}--|-----> SET --->|
  |                            |                            |                       |               |
  |                            |                            |-- UPDATE refresh_tokens|              |
  |                            |                            |   SET revoked=true    |               |
  |                            |                            |   WHERE current ------>|              |
  |                            |                            |                       |               |
  |                            |                            |-- NATS: auth.user.logout              |
  |                            |<-- 204 No Content ---------|                       |               |
  |<-- 204 No Content ---------|                            |                       |               |
```

### 3.3. Проверка denylist в middleware

При каждом запросе API Gateway проверяет, не находится ли JTI access token в denylist:

```rust
pub async fn auth_middleware(
    State(state): State<AppState>,
    mut request: Request,
    next: Next,
) -> Result<Response, ApiError> {
    let path = request.uri().path();
    if is_public_endpoint(path) {
        return Ok(next.run(request).await);
    }

    // 1. Извлечь токен
    let token = request
        .headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .ok_or(ApiError::Unauthorized)?;

    // 2. Декодировать и проверить подпись (ES256)
    let claims = decode::<Claims>(
        token,
        &DecodingKey::from_ec_pem(state.jwt_public_key.as_bytes())
            .map_err(|e| ApiError::Internal(e.into()))?,
        &Validation::new(Algorithm::ES256),
    )
    .map_err(|_| ApiError::InvalidToken)?
    .claims;

    // 3. Проверить expiry
    if claims.exp < Utc::now().timestamp() as usize {
        return Err(ApiError::TokenExpired);
    }

    // 4. Проверить denylist (Redis)
    let denied = state.cache
        .exists(&format!("denylist:{}", claims.jti))
        .await
        .map_err(|e| ApiError::Internal(e.into()))?;

    if denied {
        return Err(ApiError::InvalidToken);
    }

    // 5. Добавить user_id в request extensions
    request.extensions_mut().insert(CurrentUser {
        user_id: claims.sub.parse::<i64>()
            .map_err(|e| ApiError::Internal(e.into()))?,
    });

    Ok(next.run(request).await)
}
```

### 3.4. Redis ключи для revocation

| Ключ Redis | Значение | TTL | Описание |
|------------|----------|-----|----------|
| `denylist:{jti}` | `"1"` | `exp - now` (оставшееся время жизни JWT) | Access token в denylist |
| `2fa_ticket:{ticket}` | `{user_id}` | 5 минут | Временный ticket для 2FA |
| `oauth_state:{state}` | `{code_verifier}` | 10 минут | PKCE state для OAuth2 |

### 3.5. Стратегия revocation по типу токена

| Действие | Access Token | Refresh Token |
|---------|-------------|---------------|
| **Logout (одна сессия)** | JTI -> Redis denylist (TTL = remaining) | `revoked = true` в PostgreSQL |
| **Logout everywhere** | Все JTI текущих сессий -> Redis denylist | Все refresh tokens: `revoked = true` |
| **Смена пароля** | Все JTI -> denylist | Все refresh tokens: `revoked = true` |
| **Reuse detected** | --- (access token короткоживущий) | Все refresh tokens user: `revoked = true` |
| **Account lock** | --- | Все refresh tokens: `revoked = true` |

---

## 4. Pipeline проверки прав (Permission Resolution)

### 4.1. Полный pipeline

Каждый запрос к защищённому ресурсу проходит следующий pipeline:

```
                    ПОЛНЫЙ PIPELINE ПРОВЕРКИ ПРАВ
                    =============================

Запрос с Authorization: Bearer <JWT>
  |
  v
+-----------------------------------------------------+
| Step 1: Extract JWT                                  |
|   Authorization header -> strip "Bearer " -> raw JWT |
+-----------------------------------------------------+
  |
  v
+-----------------------------------------------------+
| Step 2: Verify Signature (ES256)                     |
|   DecodingKey::from_ec_pem(public_key)               |
|   decode::<Claims>(token, &key, &Validation::ES256)  |
|   FAIL -> 401 INVALID_TOKEN                          |
+-----------------------------------------------------+
  |
  v
+-----------------------------------------------------+
| Step 3: Check Expiry + Denylist                      |
|   claims.exp < now? -> 401 TOKEN_EXPIRED             |
|   Redis EXISTS denylist:{jti}? -> 401 INVALID_TOKEN  |
+-----------------------------------------------------+
  |
  v
+-----------------------------------------------------+
| Step 4: Extract user_id                              |
|   claims.sub -> user_id (Snowflake ID)               |
|   -> CurrentUser { user_id } в request extensions    |
+-----------------------------------------------------+
  |
  v
+-----------------------------------------------------+
| Step 5: Permission Check (для guild-scoped операций) |
|                                                       |
|   5a. Загрузить roles пользователя в гильдии         |
|       Redis: cache:guild:{guild_id}:member:{user_id} |
|       Miss -> PostgreSQL -> cache (TTL 5 мин)        |
|                                                       |
|   5b. Вычислить base permissions                     |
|       compute_base_permissions(                       |
|           member_roles, guild_roles,                  |
|           guild_owner_id, user_id                     |
|       )                                               |
|       -> OR всех role permissions                     |
|       -> ADMINISTRATOR = all permissions              |
|       -> Owner = all permissions                      |
|                                                       |
|   5c. Применить channel overwrites                   |
|       compute_channel_permissions(                    |
|           base, overwrites, member_roles,             |
|           user_id, everyone_role_id                   |
|       )                                               |
|       -> @everyone overwrites                         |
|       -> Role overwrites (OR allow, OR deny)          |
|       -> User-specific overwrites                     |
|                                                       |
|   5d. Проверить конкретный permission bit             |
|       final.contains(Permissions::SEND_MESSAGES)?     |
|       FAIL -> 403 MISSING_PERMISSIONS                 |
+-----------------------------------------------------+
  |
  v
  Запрос разрешён -> передать целевому сервису
```

### 4.2. Compute Base Permissions

Из `crates/permissions/src/calculator.rs`:

```rust
pub fn compute_base_permissions(
    member_roles: &[i64],
    guild_roles: &[(i64, u64)],  // (role_id, permissions)
    guild_owner_id: i64,
    user_id: i64,
) -> Permissions {
    // Guild owner = все права безусловно
    if user_id == guild_owner_id {
        return Permissions::all();
    }

    let mut permissions = Permissions::empty();

    // OR всех role permissions пользователя
    for (role_id, role_perms) in guild_roles {
        if member_roles.contains(role_id) {
            permissions |= Permissions::from_bits_truncate(*role_perms);
        }
    }

    // ADMINISTRATOR = все права
    if permissions.contains(Permissions::ADMINISTRATOR) {
        return Permissions::all();
    }

    permissions
}
```

### 4.3. Compute Channel Permissions

Из `crates/permissions/src/calculator.rs`. Порядок применения overwrites критически важен:

```rust
pub fn compute_channel_permissions(
    base: Permissions,
    overwrites: &[PermissionOverwrite],
    member_roles: &[i64],
    user_id: i64,
    everyone_role_id: i64,
) -> Permissions {
    // ADMINISTRATOR -> все права в любом канале
    if base.contains(Permissions::ADMINISTRATOR) {
        return Permissions::all();
    }

    let mut permissions = base;

    // 1. @everyone role overwrites (самый низкий приоритет)
    if let Some(ow) = overwrites.iter().find(|o| o.id == everyone_role_id && o.overwrite_type == 0) {
        permissions &= !Permissions::from_bits_truncate(ow.deny);
        permissions |= Permissions::from_bits_truncate(ow.allow);
    }

    // 2. Role-specific overwrites (OR all allows, OR all denies)
    let mut allow = Permissions::empty();
    let mut deny = Permissions::empty();
    for ow in overwrites.iter().filter(|o| o.overwrite_type == 0 && member_roles.contains(&o.id)) {
        allow |= Permissions::from_bits_truncate(ow.allow);
        deny |= Permissions::from_bits_truncate(ow.deny);
    }
    permissions &= !deny;
    permissions |= allow;

    // 3. User-specific overwrites (самый высокий приоритет)
    if let Some(ow) = overwrites.iter().find(|o| o.id == user_id && o.overwrite_type == 1) {
        permissions &= !Permissions::from_bits_truncate(ow.deny);
        permissions |= Permissions::from_bits_truncate(ow.allow);
    }

    permissions
}
```

### 4.4. Приоритет overwrites

```
Приоритет (от низкого к высокому):
==================================

  base permissions (OR всех ролей)
       |
       v
  @everyone overwrite (deny, затем allow)
       |
       v
  Role overwrites (OR всех deny, OR всех allow, затем: deny, allow)
       |
       v
  User-specific overwrite (deny, затем allow)  <--- наивысший приоритет
```

### 4.5. Middleware chain в Axum

```rust
use axum::{Router, middleware};

fn build_router(state: AppState) -> Router {
    let public_routes = Router::new()
        .route("/auth/login", post(auth::login))
        .route("/auth/register", post(auth::register))
        .route("/auth/refresh", post(auth::refresh))
        .route("/health", get(health));

    let protected_routes = Router::new()
        .route("/users/@me", get(users::get_me))
        .route("/guilds", post(guilds::create))
        .route("/guilds/:guild_id/channels", get(guilds::get_channels))
        .route("/channels/:channel_id/messages", post(messages::create))
        .layer(middleware::from_fn_with_state(
            state.clone(),
            auth_middleware,       // Step 1-4: JWT verification
        ))
        .layer(middleware::from_fn_with_state(
            state.clone(),
            rate_limit_middleware, // Global rate limit: 50/1s per user
        ));

    Router::new()
        .merge(public_routes)
        .merge(protected_routes)
        .layer(middleware::from_fn(cors_middleware))
        .layer(middleware::from_fn(request_id_middleware))
        .layer(middleware::from_fn(logging_middleware))
        .with_state(state)
}
```

### 4.6. Permission check в handler

```rust
use permissions::{Permissions, compute_base_permissions, compute_channel_permissions};

async fn send_message(
    State(state): State<AppState>,
    user: CurrentUser,
    Path(channel_id): Path<i64>,
    Json(payload): Json<CreateMessage>,
) -> Result<Json<Message>, AppError> {
    // Загрузить данные для permission check
    let channel = state.cache
        .get_or_fetch::<Channel>(&format!("channel:{channel_id}"), || {
            fetch_channel_from_db(pool, channel_id)
        })
        .await?;

    let guild_id = channel.guild_id;

    let member_roles = state.cache
        .get_or_fetch::<Vec<i64>>(&format!("member_roles:{guild_id}:{}", user.user_id), || {
            fetch_member_roles(pool, guild_id, user.user_id)
        })
        .await?;

    let guild_roles = state.cache
        .get_or_fetch::<Vec<(i64, u64)>>(&format!("guild_roles:{guild_id}"), || {
            fetch_guild_roles(pool, guild_id)
        })
        .await?;

    let guild = state.cache
        .get_or_fetch::<Guild>(&format!("guild:{guild_id}"), || {
            fetch_guild(pool, guild_id)
        })
        .await?;

    // Вычислить base permissions
    let base = compute_base_permissions(
        &member_roles,
        &guild_roles,
        guild.owner_id,
        user.user_id,
    );

    // Вычислить channel permissions
    let channel_perms = compute_channel_permissions(
        base,
        &channel.permission_overwrites,
        &member_roles,
        user.user_id,
        guild.everyone_role_id,
    );

    // Проверить конкретный бит
    if !channel_perms.contains(Permissions::SEND_MESSAGES) {
        return Err(AppError::Forbidden {
            code: "MISSING_PERMISSIONS".to_string(),
            message: "You do not have permission to send messages in this channel".to_string(),
        });
    }

    // ... создать сообщение
    Ok(Json(message))
}
```

---

## 5. OAuth2 Flow (Google, GitHub, Apple)

Источник: [RFC 6749 --- OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc6749), [RFC 7636 --- PKCE](https://datatracker.ietf.org/doc/html/rfc7636).

### 5.1. Authorization Code + PKCE

Платформа использует исключительно Authorization Code Flow с PKCE. Implicit Flow запрещён.

**PKCE** (Proof Key for Code Exchange) защищает от перехвата authorization code --- даже если code перехвачен, без `code_verifier` его нельзя обменять на токен.

### 5.2. Sequence diagram

```
                    OAuth2 Authorization Code + PKCE
                    ==================================

Client (браузер)           API Gateway         Auth Service              OAuth Provider       Redis
  |                            |                    |                       (Google/GitHub)       |
  |                            |                    |                            |                |
  |-- GET /auth/oauth/google ->|                    |                            |                |
  |                            |-- NATS req ------->|                            |                |
  |                            |                    |                            |                |
  |                            |                    |-- Generate code_verifier   |                |
  |                            |                    |   (43-128 random chars)    |                |
  |                            |                    |                            |                |
  |                            |                    |-- code_challenge =         |                |
  |                            |                    |   BASE64URL(SHA256(        |                |
  |                            |                    |     code_verifier))        |                |
  |                            |                    |                            |                |
  |                            |                    |-- Generate state           |                |
  |                            |                    |   (CSRF protection)        |                |
  |                            |                    |                            |                |
  |                            |                    |-- Store in Redis: ---------|--------------->|
  |                            |                    |   oauth_state:{state} =    |    SET         |
  |                            |                    |   {code_verifier}          |    TTL 10min   |
  |                            |                    |                            |                |
  |                            |<-- 302 Redirect ---|                            |                |
  |<-- 302 Redirect ---------->|                    |                            |                |
  |   Location: https://accounts.google.com/o/oauth2/v2/auth                    |                |
  |     ?response_type=code                         |                            |                |
  |     &client_id=...                              |                            |                |
  |     &redirect_uri=.../auth/oauth/google/callback|                            |                |
  |     &scope=email+profile                        |                            |                |
  |     &state={state}                              |                            |                |
  |     &code_challenge={challenge}                 |                            |                |
  |     &code_challenge_method=S256                 |                            |                |
  |                            |                    |                            |                |
  |------ Пользователь авторизуется у провайдера ---|--------------------------->|                |
  |                            |                    |                            |                |
  |<-- Redirect: /auth/oauth/google/callback?code=...&state=... <---------------|                |
  |                            |                    |                            |                |
  |-- GET /auth/oauth/google/  |                    |                            |                |
  |   callback?code=X&state=Y->|                    |                            |                |
  |                            |-- NATS req ------->|                            |                |
  |                            |                    |                            |                |
  |                            |                    |-- Validate state (Redis) --|--------------->|
  |                            |                    |<- code_verifier -----------|<-- GET --------|
  |                            |                    |-- Delete state from Redis -|---- DEL ------>|
  |                            |                    |                            |                |
  |                            |                    |-- Exchange code + verifier |                |
  |                            |                    |   POST /token ------------>|                |
  |                            |                    |   {code, code_verifier,    |                |
  |                            |                    |    client_id, client_secret,|               |
  |                            |                    |    redirect_uri}           |                |
  |                            |                    |<- {access_token, ...} -----|                |
  |                            |                    |                            |                |
  |                            |                    |-- GET /userinfo ---------->|                |
  |                            |                    |<- {email, name, avatar} ---|                |
  |                            |                    |                            |                |
  |                            |                    |== LINKING LOGIC ===========|                |
  |                            |                    | 1. Найти oauth_accounts    |                |
  |                            |                    |    по (provider, provider_id)               |
  |                            |                    |    -> Найден: логин        |                |
  |                            |                    |    -> Не найден:           |                |
  |                            |                    |       2. Проверить email   |                |
  |                            |                    |          в user_credentials|                |
  |                            |                    |          -> Есть: привязать|                |
  |                            |                    |             OAuth аккаунт  |                |
  |                            |                    |          -> Нет: создать   |                |
  |                            |                    |             нового user    |                |
  |                            |                    |             (email_verified|                |
  |                            |                    |              = true)       |                |
  |                            |                    |===========================|                |
  |                            |                    |                            |                |
  |                            |                    |-- Generate access + refresh tokens         |
  |                            |<-- tokens ---------|                            |                |
  |<-- 302 Redirect + tokens --|                    |                            |                |
  |   (через fragment или      |                    |                            |                |
  |    secure httpOnly cookie)  |                    |                            |                |
```

### 5.3. State parameter (CSRF)

`state` --- криптографически случайная строка, одноразовая, с TTL 10 минут. Предотвращает CSRF-атаки: атакующий не может подставить свой authorization code в callback легитимного пользователя.

### 5.4. Привязка OAuth к существующему аккаунту

| Ситуация | Действие |
|---------|---------|
| `oauth_accounts` содержит запись `(provider, provider_id)` | Логин --- генерация токенов для существующего пользователя |
| `oauth_accounts` не содержит, но email совпадает с `user_credentials.email` | Привязка OAuth к существующему аккаунту, затем логин |
| `oauth_accounts` не содержит, email не найден | Создание нового пользователя с `email_verified = true` (провайдер уже подтвердил email) |

---

## 6. 2FA (TOTP + WebAuthn)

### 6.1. TOTP (Time-Based One-Time Password)

Источник: [RFC 6238 --- TOTP](https://datatracker.ietf.org/doc/html/rfc6238).

TOTP генерирует 6-значный код на основе shared secret и текущего времени (окно 30 секунд). Совместим с Google Authenticator, Authy и другими TOTP-приложениями.

#### Включение TOTP

```
Client                       Auth Service                    PostgreSQL       Redis
  |                               |                               |               |
  |-- POST /auth/2fa/enable ----->|                               |               |
  |   Authorization: Bearer JWT   |                               |               |
  |   {password: "re-auth"}       |                               |               |
  |                               |-- Verify password (re-auth)   |               |
  |                               |-- Generate TOTP secret        |               |
  |                               |   (base32, 160 bit)           |               |
  |                               |-- Generate 10 recovery codes  |               |
  |                               |-- Store temp in Redis --------|-------------->|
  |                               |   (TTL 10 мин)                |               |
  |                               |                               |               |
  |<-- {secret, qr_uri,          |                               |               |
  |     recovery_codes[10]} ------|                               |               |
  |                               |                               |               |
  |   (Пользователь сканирует QR)|                               |               |
  |                               |                               |               |
  |-- POST /auth/2fa/confirm ---->|                               |               |
  |   {code: "123456"}            |                               |               |
  |                               |-- Verify TOTP code            |               |
  |                               |   (допуск +/- 1 окно)        |               |
  |                               |-- Store encrypted secret ---->|               |
  |                               |-- Store hashed recovery codes>|               |
  |                               |-- SET totp_enabled = true --->|               |
  |                               |-- Delete temp from Redis -----|-----> DEL --->|
  |<-- 200 OK --------------------|                               |               |
```

#### Логин с TOTP

```
Client                       Auth Service                    PostgreSQL       Redis
  |                               |                               |               |
  |-- POST /auth/login ---------->|                               |               |
  |   {email, password}           |-- Verify credentials          |               |
  |                               |-- 2FA enabled? YES            |               |
  |                               |-- Generate ticket ------------|-----> SET --->|
  |                               |   (random, TTL 5 мин)         |    ticket:X   |
  |<-- {requires_2fa: true,       |                               |               |
  |     ticket: "tmp_..."} -------|                               |               |
  |                               |                               |               |
  |-- POST /auth/login/2fa ------>|                               |               |
  |   {ticket, code: "123456"}    |                               |               |
  |                               |-- Validate ticket (Redis) ----|-----> GET --->|
  |                               |-- Verify TOTP code            |               |
  |                               |   (totp-rs, +/- 1 window)    |               |
  |                               |-- Delete ticket from Redis ---|-----> DEL --->|
  |                               |-- Generate access + refresh   |               |
  |<-- {access_token,             |                               |               |
  |     refresh_token} -----------|                               |               |
```

#### Recovery codes

Если пользователь потерял доступ к TOTP-приложению, можно использовать recovery code вместо TOTP:

- 10 одноразовых кодов (8 символов, alphanumeric)
- Хранятся хешированными (Argon2id) в PostgreSQL
- Каждый код --- одноразовый: после использования помечается как `used`
- Если все 10 кодов израсходованы --- обращение в support

### 6.2. WebAuthn (аппаратные ключи)

Источник: [W3C Web Authentication Level 3](https://www.w3.org/TR/webauthn-3/).

WebAuthn позволяет использовать аппаратные ключи безопасности (YubiKey, Titan Key) или платформенные аутентификаторы (Touch ID, Windows Hello) как второй фактор.

#### Регистрация WebAuthn credential

```
Client (браузер)             Auth Service                    PostgreSQL       Redis
  |                               |                               |               |
  |-- POST /webauthn/register/    |                               |               |
  |   begin ---------------------->|                               |               |
  |   {password: "re-auth"}       |                               |               |
  |                               |-- Verify password             |               |
  |                               |-- webauthn-rs: start_passkey_ |               |
  |                               |   registration()              |               |
  |                               |-- Store challenge state ------|-----> SET --->|
  |                               |   in Redis (TTL 2 мин)        |   challenge:X |
  |<-- PublicKeyCredential        |                               |               |
  |    CreationOptions -----------|                               |               |
  |                               |                               |               |
  |   (Пользователь касается ключа)                               |               |
  |                               |                               |               |
  |-- POST /webauthn/register/    |                               |               |
  |   complete ------------------->|                               |               |
  |   {name: "YubiKey 5",        |                               |               |
  |    credential: {...}}         |                               |               |
  |                               |-- Get challenge (Redis) ------|-----> GET --->|
  |                               |-- webauthn-rs: finish_passkey_|               |
  |                               |   registration()              |               |
  |                               |-- Store credential ---------->|               |
  |                               |   (credential_id, public_key, |               |
  |                               |    counter, name, transports) |               |
  |                               |-- Delete challenge (Redis) ---|-----> DEL --->|
  |<-- 200 OK --------------------|                               |               |
```

#### Аутентификация WebAuthn (вместо TOTP)

```
Client (браузер)             Auth Service                    PostgreSQL       Redis
  |                               |                               |               |
  |   (После успешного email+password, получен ticket)            |               |
  |                               |                               |               |
  |-- POST /webauthn/             |                               |               |
  |   authenticate/begin -------->|                               |               |
  |   {ticket}                    |                               |               |
  |                               |-- Validate ticket (Redis) ----|               |
  |                               |-- Load user WebAuthn creds -->|               |
  |                               |-- webauthn-rs: start_passkey_ |               |
  |                               |   authentication()            |               |
  |                               |-- Store auth state (Redis) ---|-----> SET --->|
  |<-- PublicKeyCredential        |                               |   TTL 2 min  |
  |    RequestOptions ------------|                               |               |
  |                               |                               |               |
  |   (Пользователь касается ключа)                               |               |
  |                               |                               |               |
  |-- POST /webauthn/             |                               |               |
  |   authenticate/complete ------>|                               |               |
  |   {credential: {...}}         |                               |               |
  |                               |-- webauthn-rs: finish_passkey_|               |
  |                               |   authentication()            |               |
  |                               |-- Update counter ------------>|  (replay      |
  |                               |   (replay protection)         |   protection) |
  |                               |-- Generate access + refresh   |               |
  |<-- {access_token,             |                               |               |
  |     refresh_token} -----------|                               |               |
```

### 6.3. Матрица 2FA решений при логине

| TOTP включён | WebAuthn ключи есть | Что происходит при логине |
|-------------|---------------------|--------------------------|
| Нет | Нет | Прямой логин (access + refresh) |
| Да | Нет | Требуется TOTP код или recovery code |
| Нет | Да | Требуется WebAuthn аутентификация |
| Да | Да | Пользователь выбирает: TOTP или WebAuthn |

---

## 7. WebSocket Authentication

Источник: [OWASP WebSocket Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html).

### 7.1. IDENTIFY flow

WebSocket Gateway **не принимает JWT при handshake** (не в query params, не в headers). Аутентификация происходит через IDENTIFY opcode после установления WebSocket соединения.

```
Client                                          Gateway Server (:4000)
  |                                                      |
  |== WebSocket connect (wss://gw?v=1&encoding=json) ==>|
  |                                                      |
  |<========== HELLO (op:10) ===========================|
  |            {"op": 10,                                |
  |             "d": {"heartbeat_interval": 41250}}      |
  |                                                      |
  |  [Таймер: 5 секунд на IDENTIFY, иначе disconnect]   |
  |                                                      |
  |== Heartbeat (op:1) после jitter ===================>|  <-- interval * random(0..1)
  |<========== Heartbeat ACK (op:11) ===================|
  |                                                      |
  |== IDENTIFY (op:2) =================================>|
  |   {"op": 2,                                          |
  |    "d": {                                            |
  |      "token": "eyJhbGciOiJFUzI1NiIs...",            |   <-- JWT Access Token
  |      "intents": 3276799,                             |
  |      "properties": {                                 |
  |        "os": "windows",                              |
  |        "browser": "chrome"                           |
  |      },                                              |
  |      "shard": [0, 1]                                 |
  |    }}                                                |
  |                                                      |
  |                      [Сервер:]                       |
  |                      1. Verify JWT (ES256 pubkey)    |
  |                      2. Check exp > now              |
  |                      3. Check denylist (Redis)       |
  |                      4. Extract user_id from sub     |
  |                      5. Validate intents             |
  |                      6. Validate shard config        |
  |                      7. Create session               |
  |                      8. Subscribe to NATS guilds     |
  |                                                      |
  |<========== READY (op:0, t:READY) ===================|
  |            {"op": 0, "s": 1, "t": "READY",          |
  |             "d": {                                   |
  |               "v": 1,                                |
  |               "user": {"id": "123", ...},            |
  |               "guilds": [{"id": "111",               |
  |                  "unavailable": true}, ...],         |
  |               "session_id": "abc123def456",          |
  |               "resume_gateway_url":                  |
  |                 "wss://gw-resume.example.com",       |
  |               "shard": [0, 1]                        |
  |             }}                                       |
  |                                                      |
  |<========== DISPATCH events (op:0, s++) =============|
  |== Heartbeat (op:1) каждые 41250ms ================>|
  |<========== Heartbeat ACK (op:11) ===================|
  |                      ...                             |
```

### 7.2. Таймауты и ошибки

| Ситуация | Действие |
|---------|---------|
| 5 сек после HELLO, нет IDENTIFY | Close 4003 (Not Authenticated) |
| JWT невалиден / истёк / в denylist | Close 4004 (Authentication Failed, **не resumable**) |
| Повторный IDENTIFY | Close 4005 (Already Authenticated) |
| 30 сек нет heartbeat от клиента | Disconnect (zombie detection) |

### 7.3. Обновление токена при активном соединении

Access token живёт 15 минут. При активном WebSocket-соединении, которое длится часами, клиент должен обновить токен. Протокол **не поддерживает** смену токена внутри активного соединения. Вместо этого:

1. Клиент обновляет access + refresh через `POST /auth/refresh`
2. Клиент **не** разрывает текущее WebSocket-соединение
3. При следующем reconnect (по любой причине) клиент использует новый access token в IDENTIFY
4. Gateway верифицирует JWT только при IDENTIFY --- во время жизни соединения JWT не проверяется повторно

Если сессия была инвалидирована (logout, смена пароля), Gateway получает NATS event `auth.user.logout` и принудительно закрывает все WebSocket-соединения этого пользователя.

### 7.4. Resume flow

При потере соединения с resumable close code, клиент может восстановить сессию без повторной аутентификации:

```
Client                                          Gateway Server
  |                                                      |
  |   [Соединение потеряно, close code 4000]            |
  |   [Есть: session_id, resume_gateway_url, seq]       |
  |                                                      |
  |== WebSocket connect (resume_gateway_url) ===========>|
  |                                                      |
  |<========== HELLO (op:10) ===========================|
  |                                                      |
  |== Heartbeat (op:1) после jitter ===================>|
  |<========== Heartbeat ACK (op:11) ===================|
  |                                                      |
  |== RESUME (op:6) ===================================>|
  |   {"op": 6,                                          |
  |    "d": {                                            |
  |      "token": "eyJhbG...",                           |
  |      "session_id": "abc123def456",                   |
  |      "seq": 1337                                     |
  |    }}                                                |
  |                                                      |
  |                      [Сервер:]                       |
  |                      1. Verify JWT                   |
  |                      2. Load session from Redis      |
  |                      3. Replay missed events (seq+1) |
  |                                                      |
  |<========== Missed events (op:0, s:1338...) =========|
  |<========== RESUMED (op:0, t:RESUMED) ===============|
  |                                                      |
  |<========== Dispatch events continue ================|
```

---

## 8. Multi-device / Multi-session

### 8.1. Модель

Один пользователь может быть одновременно залогинен с нескольких устройств. Каждая сессия --- это:

- Отдельный refresh token в PostgreSQL
- Привязка к device fingerprint (user-agent, IP)
- Независимое WebSocket-соединение (если используется)

### 8.2. Лимиты

| Параметр | Значение |
|---------|---------|
| Максимум активных сессий (refresh tokens) | 10 |
| Максимум WebSocket-соединений на пользователя | Не ограничено (по одному на шард) |
| Поведение при превышении лимита сессий | Самая старая сессия автоматически отзывается |

### 8.3. Управление сессиями

```
                    СПИСОК СЕССИЙ
                    ==============

Client                       Auth Service                    PostgreSQL
  |                               |                               |
  |-- GET /auth/sessions -------->|                               |
  |   Authorization: Bearer JWT   |                               |
  |                               |-- SELECT refresh_tokens       |
  |                               |   WHERE user_id = $1          |
  |                               |   AND revoked = false         |
  |                               |   AND expires_at > NOW() ---->|
  |                               |<- sessions -------------------|
  |<-- 200 [{                     |                               |
  |   id: "...",                  |                               |
  |   device_info: {              |                               |
  |     user_agent: "...",        |                               |
  |     ip: "..."                 |                               |
  |   },                          |                               |
  |   created_at: "...",          |                               |
  |   current: true/false         |                               |
  | }, ...] ---------------------|                               |
```

### 8.4. Logout Everywhere

"Выйти со всех устройств" --- отзывает все refresh tokens и добавляет все текущие access token JTI в denylist:

```
Client                       Auth Service                    PostgreSQL       Redis
  |                               |                               |               |
  |-- DELETE /auth/sessions ----->|                               |               |
  |   Authorization: Bearer JWT   |                               |               |
  |                               |-- SELECT all active refresh   |               |
  |                               |   tokens for user ---------->|               |
  |                               |<- [tokens with JTI info] ----|               |
  |                               |                               |               |
  |                               |-- UPDATE refresh_tokens       |               |
  |                               |   SET revoked = true          |               |
  |                               |   WHERE user_id = $1          |               |
  |                               |   AND id != current --------->|               |
  |                               |                               |               |
  |                               |-- For each active JTI:        |               |
  |                               |   SET denylist:{jti} "1"     |               |
  |                               |   EX {remaining_ttl} --------|-----> SET --->|
  |                               |                               |               |
  |                               |-- NATS: auth.user.logout      |               |
  |                               |   (все Gateway закроют WS)    |               |
  |<-- 204 No Content ------------|                               |               |
```

### 8.5. Таблица refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES user_credentials(user_id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,                 -- SHA-256 хеш токена
    device_info     JSONB,                         -- user-agent, IP (для отображения сессий)
    expires_at      TIMESTAMPTZ NOT NULL,          -- NOW() + 30 days
    rotated_at      TIMESTAMPTZ,                   -- когда был использован для ротации
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Каждая строка --- одна сессия. `device_info` содержит информацию для отображения пользователю ("Chrome на Windows", "Safari на iPhone").

---

## 9. Bot Authentication

### 9.1. Отличие от пользовательской аутентификации

Боты не используют JWT. Вместо этого они используют долгоживущий opaque token:

```
Authorization: Bot <bot_token>
```

| Параметр | User | Bot |
|---------|------|-----|
| Header | `Authorization: Bearer <jwt>` | `Authorization: Bot <token>` |
| Формат токена | JWT (ES256) | Opaque (64 random bytes, hex) |
| TTL | 15 минут (access) | Бессрочный (до отзыва) |
| Хранение на сервере | JTI в denylist (Redis) | SHA-256 hash в PostgreSQL |
| Refresh | Через refresh token | Не требуется |
| 2FA | Поддерживается | Не применимо |
| Rate limits | Global: 50/1s | Отдельные лимиты |

### 9.2. Bot token flow

```
                    СОЗДАНИЕ БОТА
                    ==============

Developer (User)              API Gateway              Bot Service            PostgreSQL
  |                               |                         |                      |
  |-- POST /applications -------->|                         |                      |
  |   {name: "MyBot"}            |-- NATS req ------------->|                      |
  |                               |                         |-- Create application |
  |                               |                         |   + bot user ------->|
  |                               |                         |-- Generate bot_token |
  |                               |                         |   (64 random bytes)  |
  |                               |                         |-- Store SHA-256(token)|
  |                               |                         |   in DB ------------>|
  |                               |<-- {app, bot_token} ----|                      |
  |<-- 201 {token: "Bot abc..."} -|                         |                      |
  |                                                                                |
  |   ВАЖНО: bot_token показывается только один раз.                               |
  |   При утере --- regenerate (старый инвалидируется).                            |


                    ИСПОЛЬЗОВАНИЕ BOT TOKEN
                    ========================

Bot Client                    API Gateway                    Target Service
  |                               |                               |
  |-- GET /guilds/123 ----------->|                               |
  |   Authorization: Bot abc...   |                               |
  |                               |-- Detect "Bot " prefix        |
  |                               |-- SHA-256(token)              |
  |                               |-- SELECT from bot_tokens      |
  |                               |   WHERE hash = $1 (cache/DB) |
  |                               |-- Extract bot_user_id         |
  |                               |-- Rate limit check            |
  |                               |   (separate from user limits) |
  |                               |                               |
  |                               |-- NATS req: guilds.get ------>|
  |                               |   {user_id: bot_user_id}     |
  |                               |<-- response -----------------|
  |<-- 200 OK + guild data -------|                               |
```

### 9.3. Rate limits для ботов

Bot rate limits отделены от пользовательских и имеют другие значения:

| Endpoint | User limit | Bot limit |
|----------|-----------|-----------|
| Global | 50/1s | 50/1s |
| POST /channels/:id/messages | 5/5s per channel | 5/5s per channel |
| DELETE /channels/:id/messages/:id | 5/1s | 5/1s |
| PATCH /channels/:id | 2/10min | 2/10min |
| Gateway IDENTIFY | 1000/day | 1000/day |

Бот rate limits привязаны к `bot_user_id`, не к IP. При превышении --- `429 Too Many Requests` с заголовком `Retry-After`.

### 9.4. Безопасность bot tokens

- Bot token --- конфиденциальный секрет. Утечка = полный доступ к боту
- В PostgreSQL хранится **только SHA-256 хеш** (как refresh token)
- При компрометации --- немедленный regenerate через API (старый токен инвалидируется)
- Bot token **не передаётся** в логах, метриках, трейсах
- Рекомендация разработчикам: хранить в переменных окружения, никогда в коде

---

## Сводная таблица безопасности

| Механизм | Реализация | Стандарт/Источник |
|---------|-----------|-------------------|
| Хеширование паролей | Argon2id (m=47104, t=1, p=1) | [RFC 9106](https://datatracker.ietf.org/doc/html/rfc9106), [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) |
| JWT подпись | ES256 (ECDSA P-256) | [RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519) |
| Refresh token | Opaque, SHA-256 hash в БД, ротация | [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) |
| Token revocation | Redis denylist (JTI, TTL) | [RFC 7009](https://datatracker.ietf.org/doc/html/rfc7009) |
| OAuth2 | Authorization Code + PKCE (S256) | [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749), [RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636) |
| TOTP 2FA | SHA-1, 6 digits, 30s window | [RFC 6238](https://datatracker.ietf.org/doc/html/rfc6238) |
| WebAuthn | webauthn-rs, FIDO2 | [W3C WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/) |
| Rate limiting | Redis sliding window (Lua script) | [OWASP API Security](https://owasp.org/API-Security/) |
| Проверка паролей | HaveIBeenPwned API (k-anonymity) | [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) |
| Account lockout | 5 неудачных попыток / 15 мин | [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) |
| Permissions | Bitflags u64, channel overwrites | --- |
| WebSocket auth | IDENTIFY opcode, 5s timeout | [OWASP WebSocket Security](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html) |
