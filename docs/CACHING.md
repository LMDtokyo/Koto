# Стратегия кеширования

Документация по кешированию данных в Redis: паттерны, ключи, инвалидация, защита от проблем, мониторинг, Rust-реализация.

## Источники

- [Redis Patterns](https://redis.io/docs/latest/develop/use/patterns/)
- [Redis Performance Optimization](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/)
- [AWS Caching Best Practices](https://aws.amazon.com/caching/best-practices/)
- [Redis Eviction Policies](https://redis.io/docs/latest/develop/reference/eviction/)
- [crate: redis](https://docs.rs/redis/latest/redis/)
- [crate: deadpool-redis](https://docs.rs/deadpool-redis/latest/deadpool_redis/)

---

## Обзор

Redis / Valkey 8 используется как основной кеш-слой между микросервисами и PostgreSQL. Конфигурация Redis, Sentinel, eviction policies описаны в [DATABASE.md](DATABASE.md). Реализация `TypedCache` описана в [CRATES.md](CRATES.md) (`crates/cache`).

**Принцип**: Redis — кеш, а не primary storage. Если данные вытеснены (`allkeys-lru`) или Redis недоступен, сервис идёт в PostgreSQL. Единственное исключение — presence данные, которые живут только в Redis (stateless сервис, см. [services/presence.md](services/presence.md)).

---

## 1. Паттерны кеширования

Источник: [Redis Patterns](https://redis.io/docs/latest/develop/use/patterns/)

### 1.1. Cache-Aside (Lazy Loading)

Сервис сам управляет кешем: проверяет Redis, при miss запрашивает PostgreSQL, записывает результат в Redis.

```
Клиент → Сервис → Redis (GET)
                    ├─ hit  → вернуть данные
                    └─ miss → PostgreSQL → SET в Redis → вернуть данные
```

**Преимущества:**
- Кешируются только запрошенные данные (экономия памяти)
- Redis недоступен → сервис продолжает работать (fallback на PostgreSQL)

**Недостатки:**
- Первый запрос всегда медленный (cache miss)
- Данные могут устареть (stale) до истечения TTL

**Используется для:** профили пользователей, гильдии, каналы, роли, инвайты.

### 1.2. Write-Through

При записи в PostgreSQL данные сразу обновляются (или инвалидируются) в Redis.

```
Клиент → Сервис → PostgreSQL (INSERT/UPDATE)
                    → Redis (SET или DEL)
```

**Преимущества:**
- Кеш всегда актуален после записи
- Следующее чтение — cache hit

**Недостатки:**
- Каждая запись увеличивает latency (два хранилища)
- Кешируются данные, которые могут не запрашиваться

**Используется для:** обновление гильдий, каналов, ролей — после `UPDATE` в PostgreSQL инвалидируем соответствующий ключ Redis.

### 1.3. Write-Behind (асинхронная запись)

Данные пишутся в Redis, а в PostgreSQL синхронизируются асинхронно (фоновая задача).

```
Клиент → Сервис → Redis (SET) → [async] → PostgreSQL
```

**Преимущества:**
- Минимальная latency записи
- Пакетная запись в PostgreSQL (batch)

**Недостатки:**
- Риск потери данных при падении Redis до синхронизации
- Сложнее реализовать consistency

**Используется для:** presence (online/idle/dnd), typing indicators. Эти данные эфемерны — потеря при рестарте Redis допустима, восстановление через heartbeat.

### 1.4. Таблица решений: когда какой паттерн

| Критерий | Cache-Aside | Write-Through | Write-Behind |
|----------|-------------|---------------|--------------|
| **Данные меняются редко** | подходит | подходит | избыточно |
| **Данные меняются часто** | stale risk | latency | подходит |
| **Потеря данных допустима** | — | — | подходит |
| **Потеря данных недопустима** | подходит | подходит | не подходит |
| **Latency записи критична** | — | не подходит | подходит |
| **Consistency важна** | TTL-based | подходит | не подходит |

| Данные | Паттерн | Обоснование |
|--------|---------|-------------|
| User profiles | Cache-Aside | Читаются часто, меняются редко |
| Guild data | Cache-Aside + Write-Through (invalidation) | Читаются часто, при обновлении — инвалидация |
| Channel data | Cache-Aside + Write-Through (invalidation) | Аналогично guild data |
| Member roles | Cache-Aside | Меняются редко, при изменении — каскадная инвалидация |
| Computed permissions | Cache-Aside | Вычисляются из ролей, инвалидируются при изменении ролей |
| Presence | Write-Behind | Эфемерные данные, обновляются heartbeat каждые 30 сек |
| Typing indicators | Write-Behind | TTL 10 сек, потеря допустима |
| Rate limits | Прямая запись в Redis | Redis — единственное хранилище для rate limit counters |
| JWT denylist | Прямая запись в Redis | TTL = оставшееся время жизни токена |
| Invite codes | Cache-Aside | Кешируются при первом запросе из PostgreSQL |

---

## 2. Redis Key Naming Convention

### Формат

```
{domain}:{entity_id}
{domain}:{scope}:{id}
{domain}:{scope}:{sub_scope}:{id}
```

**Разделитель:** `:` (стандарт Redis, поддерживается Redis CLI и GUI-инструментами для группировки).

### Полная карта ключей

| Ключ | Тип Redis | Описание |
|------|-----------|----------|
| `user:{user_id}` | STRING (JSON) | Профиль пользователя |
| `guild:{guild_id}` | STRING (JSON) | Основная информация о гильдии |
| `guild:{guild_id}:channels` | STRING (JSON array) | Список каналов гильдии |
| `guild:{guild_id}:roles` | STRING (JSON array) | Список ролей гильдии |
| `guild:{guild_id}:member:{user_id}` | STRING (JSON) | Данные участника + роли |
| `guild:{guild_id}:member_count` | STRING (integer) | Количество участников |
| `guild:{guild_id}:bans` | SET | Множество забаненных user_id |
| `channel:{channel_id}` | STRING (JSON) | Данные канала |
| `member_roles:{guild_id}:{user_id}` | STRING (JSON array) | Роли участника в гильдии |
| `perms:{guild_id}:{channel_id}:{user_id}` | STRING (u64) | Вычисленные права в канале |
| `perms:{guild_id}:base:{user_id}` | STRING (u64) | Базовые вычисленные права в гильдии |
| `presence:{user_id}:status` | STRING | Статус: online/idle/dnd/invisible |
| `presence:{user_id}:clients` | HASH | Per-platform статус (desktop, mobile, web) |
| `presence:{user_id}:activities` | STRING (JSON array) | Активности пользователя |
| `presence:{user_id}:guilds` | SET | Гильдии пользователя (для fan-out) |
| `guild_online:{guild_id}` | SET | Множество online user_id в гильдии |
| `typing:{channel_id}:{user_id}` | STRING | Typing indicator (значение `1`) |
| `rate:{bucket}:{identifier}` | ZSET | Sliding window rate limit (timestamps) |
| `jwt_deny:{jti}` | STRING | JWT в denylist (значение `1`) |
| `invite:{code}` | STRING (JSON) | Данные инвайта |
| `2fa_ticket:{ticket}` | STRING (JSON) | Временный ticket для 2FA (TTL 5 мин) |
| `oauth_state:{state}` | STRING (JSON) | OAuth2 state + code_verifier (TTL 10 мин) |
| `webauthn_challenge:{user_id}` | STRING (JSON) | WebAuthn challenge state (TTL 2 мин) |

---

## 3. Что кешируем (детальная таблица)

| Данные | Ключ Redis | TTL | Паттерн | Событие инвалидации |
|--------|-----------|-----|---------|---------------------|
| User profile | `user:{user_id}` | 5 мин | Cache-Aside | `user.{user_id}.updated` |
| Guild data | `guild:{guild_id}` | 10 мин | Cache-Aside | `guild.{guild_id}.updated` |
| Guild channels | `guild:{guild_id}:channels` | 5 мин | Cache-Aside | `guild.{guild_id}.channel.created`, `guild.{guild_id}.channel.updated`, `guild.{guild_id}.channel.deleted` |
| Guild roles | `guild:{guild_id}:roles` | 5 мин | Cache-Aside | `guild.{guild_id}.role.created`, `guild.{guild_id}.role.updated`, `guild.{guild_id}.role.deleted` |
| Channel data | `channel:{channel_id}` | 10 мин | Cache-Aside | `guild.{guild_id}.channel.updated`, `guild.{guild_id}.channel.deleted` |
| Member data + roles | `guild:{guild_id}:member:{user_id}` | 5 мин | Cache-Aside | `guild.{guild_id}.member.updated`, `guild.{guild_id}.member.removed` |
| Member roles | `member_roles:{guild_id}:{user_id}` | 5 мин | Cache-Aside | `guild.{guild_id}.member.updated` (roles changed) |
| Computed base perms | `perms:{guild_id}:base:{user_id}` | 2 мин | Cache-Aside | `guild.{guild_id}.role.updated`, `guild.{guild_id}.role.deleted` |
| Computed channel perms | `perms:{guild_id}:{channel_id}:{user_id}` | 2 мин | Cache-Aside | `guild.{guild_id}.role.updated`, `guild.{guild_id}.channel.overwrite_updated` |
| Member count | `guild:{guild_id}:member_count` | 1 мин | Cache-Aside | `guild.{guild_id}.member.joined`, `guild.{guild_id}.member.removed` |
| Bans set | `guild:{guild_id}:bans` | 5 мин | Cache-Aside | `guild.{guild_id}.member.banned`, `guild.{guild_id}.member.unbanned` |
| Presence status | `presence:{user_id}:status` | 120 сек | Write-Behind | Heartbeat refresh, `gateway.disconnected.{user_id}` |
| Client status | `presence:{user_id}:clients` | 120 сек | Write-Behind | Heartbeat refresh |
| Activities | `presence:{user_id}:activities` | 120 сек | Write-Behind | `presence.{user_id}.updated` |
| Online members | `guild_online:{guild_id}` | Без TTL (per-member EXPIRE через presence) | Write-Behind | `gateway.connected.{user_id}`, `gateway.disconnected.{user_id}` |
| Typing indicator | `typing:{channel_id}:{user_id}` | 10 сек | Write-Behind | Автоматическое истечение TTL |
| Rate limit window | `rate:{bucket}:{identifier}` | = window size | Прямая запись | Автоматическое истечение TTL |
| JWT denylist | `jwt_deny:{jti}` | = оставшееся время жизни токена | Прямая запись | Автоматическое истечение TTL |
| 2FA ticket | `2fa_ticket:{ticket}` | 5 мин | Прямая запись | Одноразовое использование + DEL |
| OAuth state | `oauth_state:{state}` | 10 мин | Прямая запись | Одноразовое использование + DEL |
| Invite data | `invite:{code}` | = `expires_at` инвайта (или 24 часа для бессрочных) | Cache-Aside | `guild.invite.deleted`, использование инвайта |

---

## 4. Каскадная инвалидация

### Сценарий 1: Пользователь обновил профиль (username, avatar)

**Триггер:** `user.{user_id}.updated`

```
1. DEL user:{user_id}
```

Каскада нет. Guild members получают username через запрос к User Service (NATS request/reply) или через собственный кеш `guild:{guild_id}:member:{user_id}`, который имеет независимый TTL.

### Сценарий 2: Роль в гильдии обновлена (permissions changed)

**Триггер:** `guild.{guild_id}.role.updated`

```
1. DEL guild:{guild_id}:roles
2. DEL perms:{guild_id}:base:* (SCAN + DEL — все базовые права участников гильдии)
3. DEL perms:{guild_id}:*:*    (SCAN + DEL — все channel permissions гильдии)
4. NATS event guild.{guild_id}.role.updated → другие сервисы инвалидируют свой локальный кеш
```

**Важно:** используем `SCAN` с паттерном, а не `KEYS` (команда `KEYS` запрещена через `rename-command`, см. [DATABASE.md](DATABASE.md)).

### Сценарий 3: Канал удалён

**Триггер:** `guild.{guild_id}.channel.deleted`

```
1. DEL channel:{channel_id}
2. DEL guild:{guild_id}:channels
3. DEL perms:{guild_id}:{channel_id}:* (SCAN + DEL — все channel permissions)
```

### Сценарий 4: Пользователь кикнут/забанен из гильдии

**Триггер:** `guild.{guild_id}.member.removed` или `guild.{guild_id}.member.banned`

```
1. DEL member_roles:{guild_id}:{user_id}
2. DEL guild:{guild_id}:member:{user_id}
3. DEL perms:{guild_id}:base:{user_id}
4. DEL perms:{guild_id}:*:{user_id} (SCAN + DEL — все channel permissions пользователя)
5. SREM guild_online:{guild_id} {user_id}
6. DEL guild:{guild_id}:member_count
```

### Сценарий 5: Permission overwrite обновлён в канале

**Триггер:** `guild.{guild_id}.channel.overwrite_updated`

```
1. DEL perms:{guild_id}:{channel_id}:* (SCAN + DEL — все permissions этого канала)
```

Базовые права (`perms:{guild_id}:base:*`) не затрагиваются, так как overwrites влияют только на channel-level permissions.

### Сценарий 6: Гильдия удалена

**Триггер:** `guild.{guild_id}.deleted`

```
1. DEL guild:{guild_id}
2. DEL guild:{guild_id}:channels
3. DEL guild:{guild_id}:roles
4. DEL guild:{guild_id}:member_count
5. DEL guild:{guild_id}:bans
6. SCAN + DEL guild:{guild_id}:member:*
7. SCAN + DEL member_roles:{guild_id}:*
8. SCAN + DEL perms:{guild_id}:*
9. DEL guild_online:{guild_id}
```

### Сценарий 7: Участнику изменили роли

**Триггер:** `guild.{guild_id}.member.updated` (поле `roles` изменилось)

```
1. DEL member_roles:{guild_id}:{user_id}
2. DEL guild:{guild_id}:member:{user_id}
3. DEL perms:{guild_id}:base:{user_id}
4. DEL perms:{guild_id}:*:{user_id} (SCAN + DEL — все channel permissions пользователя)
```

---

## 5. Защита от проблем

Источник: [Redis Patterns](https://redis.io/docs/latest/develop/use/patterns/), [Redis Performance Optimization](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/)

### 5.1. Cache Stampede (Thundering Herd)

**Проблема:** TTL истекает на популярном ключе → сотни запросов одновременно идут в PostgreSQL.

**Решение 1: Distributed Lock (SETNX)**

Только один запрос идёт в PostgreSQL, остальные ждут.

```rust
use deadpool_redis::Pool;
use redis::AsyncCommands;

/// Получить данные с защитой от stampede через distributed lock.
/// Если кеш пуст, только один запрос идёт в БД, остальные ждут.
pub async fn get_with_lock<T, F, Fut>(
    pool: &Pool,
    key: &str,
    ttl_secs: u64,
    lock_ttl_ms: u64,
    fetch_fn: F,
) -> Result<T, CacheError>
where
    T: serde::Serialize + serde::de::DeserializeOwned,
    F: FnOnce() -> Fut,
    Fut: std::future::Future<Output = Result<T, CacheError>>,
{
    let mut conn = pool.get().await?;

    // 1. Проверить кеш
    let cached: Option<String> = redis::cmd("GET")
        .arg(key)
        .query_async(&mut conn)
        .await?;

    if let Some(json) = cached {
        return Ok(serde_json::from_str(&json)?);
    }

    // 2. Попытаться захватить lock
    let lock_key = format!("{key}:lock");
    let acquired: bool = redis::cmd("SET")
        .arg(&lock_key)
        .arg("1")
        .arg("NX")
        .arg("PX")
        .arg(lock_ttl_ms)
        .query_async(&mut conn)
        .await?;

    if acquired {
        // 3. Мы получили lock — запросить PostgreSQL
        let data = fetch_fn().await?;
        let json = serde_json::to_string(&data)?;

        redis::cmd("SET")
            .arg(key)
            .arg(&json)
            .arg("EX")
            .arg(ttl_secs)
            .query_async(&mut conn)
            .await?;

        // Снять lock
        redis::cmd("DEL")
            .arg(&lock_key)
            .query_async(&mut conn)
            .await?;

        Ok(data)
    } else {
        // 4. Lock занят — подождать и повторить чтение из кеша
        tokio::time::sleep(std::time::Duration::from_millis(50)).await;
        let cached: Option<String> = redis::cmd("GET")
            .arg(key)
            .query_async(&mut conn)
            .await?;

        match cached {
            Some(json) => Ok(serde_json::from_str(&json)?),
            // Fallback: lock holder ещё не записал — идём в PostgreSQL напрямую
            None => fetch_fn().await,
        }
    }
}
```

**Решение 2: Probabilistic Early Expiration (PER)**

TTL обновляется случайным образом раньше истечения. Чем ближе к истечению — тем выше вероятность обновления.

```rust
use rand::Rng;

/// Проверить, нужно ли обновить кеш досрочно.
/// beta — коэффициент (1.0 = стандартный, больше = более агрессивное обновление).
/// delta — время вычисления данных в секундах.
pub fn should_early_recompute(ttl_remaining_secs: f64, delta: f64, beta: f64) -> bool {
    let mut rng = rand::thread_rng();
    let random: f64 = rng.gen();

    // XFetch алгоритм: -delta * beta * ln(random) >= ttl_remaining
    let threshold = -delta * beta * random.ln();
    threshold >= ttl_remaining_secs
}
```

**Рекомендация:** использовать distributed lock для ключей с высокой конкуренцией (guild data больших серверов), PER — для менее критичных данных.

### 5.2. Hot Key

**Проблема:** одна гильдия с 100K+ online пользователей — ключ `guild:{guild_id}` запрашивается тысячи раз в секунду. Вся нагрузка на один слот Redis.

**Решение: локальный in-process кеш**

Перед Redis — кеш в памяти процесса с коротким TTL (1-5 секунд). Снижает нагрузку на Redis на порядки.

```rust
use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::RwLock;

/// Запись в локальном кеше.
struct LocalEntry {
    value: String,
    expires_at: Instant,
}

/// In-process кеш перед Redis для горячих ключей.
/// TTL 1-5 секунд, не используется для данных требующих мгновенной consistency.
pub struct LocalCache {
    entries: Arc<RwLock<HashMap<String, LocalEntry>>>,
    ttl: Duration,
}

impl LocalCache {
    pub fn new(ttl: Duration) -> Self {
        Self {
            entries: Arc::new(RwLock::new(HashMap::new())),
            ttl,
        }
    }

    pub async fn get(&self, key: &str) -> Option<String> {
        let entries = self.entries.read().await;
        entries.get(key).and_then(|entry| {
            if entry.expires_at > Instant::now() {
                Some(entry.value.clone())
            } else {
                None
            }
        })
    }

    pub async fn set(&self, key: String, value: String) {
        let mut entries = self.entries.write().await;
        entries.insert(key, LocalEntry {
            value,
            expires_at: Instant::now() + self.ttl,
        });
    }

    /// Фоновая очистка истёкших записей (запускать через tokio::spawn).
    pub async fn cleanup_loop(self: Arc<Self>, interval: Duration) {
        loop {
            tokio::time::sleep(interval).await;
            let mut entries = self.entries.write().await;
            let now = Instant::now();
            entries.retain(|_, entry| entry.expires_at > now);
        }
    }
}
```

**Когда использовать:**
- Guild data крупных серверов (> 10K members)
- `guild:{guild_id}:roles` — запрашивается при каждой проверке прав
- Данные, которые допускают задержку обновления в 1-5 секунд

**Когда НЕ использовать:**
- Permissions (computed) — инвалидация должна быть быстрой
- Presence — и так пишется напрямую в Redis
- Rate limits — атомарные операции только в Redis

### 5.3. Big Keys

**Проблема:** ключ > 512KB блокирует Redis при сериализации/десериализации. Пример: гильдия с 250 ролями, каждая с развёрнутыми данными.

**Правило:** ни один ключ не должен превышать 512KB.

**Решения:**
- Разбивать списки на paginated sets (например, `guild:{guild_id}:members:page:1`)
- Для больших SET — использовать `SSCAN` вместо `SMEMBERS`
- Хранить только ID в кеше, а полные данные — в отдельных ключах

```rust
/// Проверка размера перед записью в кеш.
const MAX_CACHE_VALUE_SIZE: usize = 512 * 1024; // 512 KB

pub async fn safe_cache_set<T: serde::Serialize>(
    pool: &Pool,
    key: &str,
    value: &T,
    ttl_secs: u64,
) -> Result<(), CacheError> {
    let json = serde_json::to_string(value)?;

    if json.len() > MAX_CACHE_VALUE_SIZE {
        tracing::warn!(
            key = key,
            size = json.len(),
            "Cache value exceeds 512KB limit, skipping cache write"
        );
        return Ok(());
    }

    let mut conn = pool.get().await?;
    redis::cmd("SET")
        .arg(key)
        .arg(&json)
        .arg("EX")
        .arg(ttl_secs)
        .query_async(&mut conn)
        .await?;

    Ok(())
}
```

### 5.4. Cache Poisoning

**Проблема:** невалидные или повреждённые данные записываются в кеш и отдаются всем клиентам до истечения TTL.

**Решение:** валидировать данные перед записью в кеш.

```rust
/// Валидация данных перед кешированием.
/// Проверяем базовые инварианты, чтобы повреждённые данные не попали в кеш.
pub fn validate_for_cache(guild: &GuildData) -> bool {
    !guild.name.is_empty()
        && guild.id > 0
        && guild.owner_id > 0
        && guild.name.len() <= 100
}
```

**Дополнительные правила:**
- Десериализация из кеша всегда через `serde_json::from_str` с обработкой ошибок — при ошибке считаем cache miss, идём в PostgreSQL
- Никогда не кешировать данные, полученные из пользовательского ввода без санитизации
- При подозрении на poisoning — `DEL` ключа и повторный запрос из PostgreSQL

### 5.5. Cache Penetration

**Проблема:** запросы к данным, которых нет ни в кеше, ни в PostgreSQL (например, несуществующий `user_id`). Каждый запрос проходит сквозь кеш в БД.

**Решение: кешировать отсутствие (negative caching)**

```rust
/// Значение-маркер для отсутствующих данных.
const CACHE_NULL_MARKER: &str = "__NULL__";
const CACHE_NULL_TTL_SECS: u64 = 60; // короткий TTL для negative cache

pub async fn get_user_cached(
    cache: &TypedCache,
    pool: &PgPool,
    user_id: i64,
) -> Result<Option<UserProfile>, AppError> {
    let key = format!("user:{user_id}");

    // 1. Проверить кеш
    let mut conn = cache.pool.get().await?;
    let cached: Option<String> = redis::cmd("GET")
        .arg(&key)
        .query_async(&mut conn)
        .await?;

    if let Some(ref value) = cached {
        if value == CACHE_NULL_MARKER {
            return Ok(None); // Известно, что пользователь не существует
        }
        return Ok(Some(serde_json::from_str(value)?));
    }

    // 2. Запрос PostgreSQL
    let user = sqlx::query_as!(
        UserProfile,
        "SELECT id, username, display_name, avatar_hash FROM users WHERE id = $1",
        user_id
    )
    .fetch_optional(pool)
    .await?;

    // 3. Записать в кеш (включая отсутствие)
    match &user {
        Some(u) => {
            let json = serde_json::to_string(u)?;
            redis::cmd("SET")
                .arg(&key)
                .arg(&json)
                .arg("EX")
                .arg(300_u64) // 5 мин
                .query_async(&mut conn)
                .await?;
        }
        None => {
            redis::cmd("SET")
                .arg(&key)
                .arg(CACHE_NULL_MARKER)
                .arg("EX")
                .arg(CACHE_NULL_TTL_SECS)
                .query_async(&mut conn)
                .await?;
        }
    }

    Ok(user)
}
```

---

## 6. Rust-паттерны кеширования

Все примеры используют `deadpool-redis` + `redis` crate из проекта (см. [CRATES.md](CRATES.md) — `crates/cache`).

### 6.1. Cache-Aside helper (generic)

```rust
use deadpool_redis::Pool;
use serde::{Serialize, de::DeserializeOwned};

/// Универсальная cache-aside функция.
/// Проверяет Redis → при miss вызывает fetch_fn → записывает в Redis.
pub async fn cache_aside<T, F, Fut>(
    pool: &Pool,
    key: &str,
    ttl_secs: u64,
    fetch_fn: F,
) -> Result<T, CacheError>
where
    T: Serialize + DeserializeOwned,
    F: FnOnce() -> Fut,
    Fut: std::future::Future<Output = Result<T, CacheError>>,
{
    let mut conn = pool.get().await?;

    // 1. Попытка чтения из кеша
    let cached: Option<String> = redis::cmd("GET")
        .arg(key)
        .query_async(&mut conn)
        .await?;

    if let Some(json) = cached {
        let value: T = serde_json::from_str(&json)?;
        return Ok(value);
    }

    // 2. Cache miss — запросить из источника
    let value = fetch_fn().await?;

    // 3. Записать в кеш
    let json = serde_json::to_string(&value)?;
    redis::cmd("SET")
        .arg(key)
        .arg(&json)
        .arg("EX")
        .arg(ttl_secs)
        .query_async(&mut conn)
        .await?;

    Ok(value)
}
```

**Использование в handler:**

```rust
pub async fn get_guild(
    State(state): State<AppState>,
    Path(guild_id): Path<i64>,
) -> Result<Json<GuildResponse>, AppError> {
    let guild = cache_aside(
        &state.redis_pool,
        &format!("guild:{guild_id}"),
        600, // TTL 10 мин
        || async {
            let row = sqlx::query_as!(
                Guild,
                "SELECT id, name, owner_id, icon_hash, description, \
                 member_count, premium_tier, created_at \
                 FROM guilds WHERE id = $1 AND deleted_at IS NULL",
                guild_id
            )
            .fetch_optional(&state.db_pool)
            .await?
            .ok_or_else(|| CacheError::NotFound)?;

            Ok(row)
        },
    )
    .await?;

    Ok(Json(guild.into()))
}
```

### 6.2. Инвалидация по NATS-событию

```rust
use async_nats::Client as NatsClient;
use deadpool_redis::Pool;

/// Подписчик NATS для инвалидации кеша.
/// Запускается в фоновой Tokio-задаче при старте сервиса.
pub async fn subscribe_cache_invalidation(
    nats: NatsClient,
    redis_pool: Pool,
) -> Result<(), anyhow::Error> {
    // Подписка на все события гильдий (wildcard)
    let mut sub = nats.subscribe("guild.>".to_string()).await?;

    while let Some(msg) = sub.next().await {
        let subject = msg.subject.as_str();
        let parts: Vec<&str> = subject.split('.').collect();

        let result = match parts.as_slice() {
            // guild.{guild_id}.updated
            ["guild", guild_id, "updated"] => {
                invalidate_guild(&redis_pool, guild_id).await
            }
            // guild.{guild_id}.channel.created|updated|deleted
            ["guild", guild_id, "channel", _action] => {
                invalidate_guild_channels(&redis_pool, guild_id).await
            }
            // guild.{guild_id}.role.updated|deleted
            ["guild", guild_id, "role", action] if *action == "updated" || *action == "deleted" => {
                invalidate_guild_permissions(&redis_pool, guild_id).await
            }
            // guild.{guild_id}.member.removed|banned
            ["guild", guild_id, "member", action] if *action == "removed" || *action == "banned" => {
                if let Ok(payload) = serde_json::from_slice::<MemberPayload>(&msg.payload) {
                    invalidate_member(
                        &redis_pool,
                        guild_id,
                        &payload.user_id.to_string(),
                    ).await
                } else {
                    tracing::warn!("Failed to deserialize member payload");
                    Ok(())
                }
            }
            _ => Ok(()),
        };

        if let Err(e) = result {
            tracing::error!(error = %e, subject = subject, "Cache invalidation failed");
        }
    }

    Ok(())
}

/// Инвалидация данных гильдии.
async fn invalidate_guild(pool: &Pool, guild_id: &str) -> Result<(), CacheError> {
    let mut conn = pool.get().await?;
    redis::cmd("DEL")
        .arg(format!("guild:{guild_id}"))
        .query_async(&mut conn)
        .await?;

    tracing::debug!(guild_id = guild_id, "Invalidated guild cache");
    Ok(())
}

/// Инвалидация каналов гильдии.
async fn invalidate_guild_channels(pool: &Pool, guild_id: &str) -> Result<(), CacheError> {
    let mut conn = pool.get().await?;
    redis::cmd("DEL")
        .arg(format!("guild:{guild_id}:channels"))
        .query_async(&mut conn)
        .await?;

    tracing::debug!(guild_id = guild_id, "Invalidated guild channels cache");
    Ok(())
}

/// Каскадная инвалидация прав при изменении роли.
async fn invalidate_guild_permissions(pool: &Pool, guild_id: &str) -> Result<(), CacheError> {
    let mut conn = pool.get().await?;

    // Удалить список ролей
    redis::cmd("DEL")
        .arg(format!("guild:{guild_id}:roles"))
        .query_async(&mut conn)
        .await?;

    // SCAN + DEL для всех computed permissions гильдии
    let pattern = format!("perms:{guild_id}:*");
    let mut cursor: u64 = 0;

    loop {
        let (next_cursor, keys): (u64, Vec<String>) = redis::cmd("SCAN")
            .arg(cursor)
            .arg("MATCH")
            .arg(&pattern)
            .arg("COUNT")
            .arg(100_u64)
            .query_async(&mut conn)
            .await?;

        if !keys.is_empty() {
            redis::cmd("DEL")
                .arg(&keys)
                .query_async(&mut conn)
                .await?;
        }

        cursor = next_cursor;
        if cursor == 0 {
            break;
        }
    }

    tracing::debug!(
        guild_id = guild_id,
        "Invalidated guild permissions cache (cascade)"
    );
    Ok(())
}

/// Инвалидация данных конкретного участника.
async fn invalidate_member(
    pool: &Pool,
    guild_id: &str,
    user_id: &str,
) -> Result<(), CacheError> {
    let mut conn = pool.get().await?;

    // Удалить данные участника
    redis::cmd("DEL")
        .arg(format!("member_roles:{guild_id}:{user_id}"))
        .arg(format!("guild:{guild_id}:member:{user_id}"))
        .arg(format!("perms:{guild_id}:base:{user_id}"))
        .query_async(&mut conn)
        .await?;

    // Удалить из множества online
    redis::cmd("SREM")
        .arg(format!("guild_online:{guild_id}"))
        .arg(user_id)
        .query_async(&mut conn)
        .await?;

    // SCAN + DEL channel permissions пользователя
    let pattern = format!("perms:{guild_id}:*:{user_id}");
    let mut cursor: u64 = 0;

    loop {
        let (next_cursor, keys): (u64, Vec<String>) = redis::cmd("SCAN")
            .arg(cursor)
            .arg("MATCH")
            .arg(&pattern)
            .arg("COUNT")
            .arg(100_u64)
            .query_async(&mut conn)
            .await?;

        if !keys.is_empty() {
            redis::cmd("DEL")
                .arg(&keys)
                .query_async(&mut conn)
                .await?;
        }

        cursor = next_cursor;
        if cursor == 0 {
            break;
        }
    }

    // Инвалидировать member_count
    redis::cmd("DEL")
        .arg(format!("guild:{guild_id}:member_count"))
        .query_async(&mut conn)
        .await?;

    tracing::debug!(
        guild_id = guild_id,
        user_id = user_id,
        "Invalidated member cache (cascade)"
    );
    Ok(())
}
```

### 6.3. Кеширование computed permissions

```rust
use permissions::{compute_base_permissions, compute_channel_permissions, Permissions};

/// Получить computed permissions для пользователя в канале.
/// Кешируется в Redis как u64 с TTL 2 мин.
pub async fn get_channel_permissions(
    redis_pool: &Pool,
    db_pool: &PgPool,
    guild_id: i64,
    channel_id: i64,
    user_id: i64,
) -> Result<Permissions, AppError> {
    let key = format!("perms:{guild_id}:{channel_id}:{user_id}");

    cache_aside(
        redis_pool,
        &key,
        120, // TTL 2 мин
        || async {
            // 1. Получить базовые данные (тоже через кеш)
            let member_roles = get_member_roles_cached(
                redis_pool, db_pool, guild_id, user_id,
            ).await?;

            let guild_roles = get_guild_roles_cached(
                redis_pool, db_pool, guild_id,
            ).await?;

            let guild_owner_id = get_guild_owner_cached(
                redis_pool, db_pool, guild_id,
            ).await?;

            // 2. Вычислить базовые права
            let base = compute_base_permissions(
                &member_roles,
                &guild_roles,
                guild_owner_id,
                user_id,
            );

            // 3. Получить channel overwrites
            let overwrites = sqlx::query_as!(
                PermissionOverwriteRow,
                "SELECT target_id, target_type, allow, deny \
                 FROM permission_overwrites WHERE channel_id = $1",
                channel_id
            )
            .fetch_all(db_pool)
            .await?;

            // 4. Вычислить channel permissions
            let channel_perms = compute_channel_permissions(
                base,
                &overwrites.iter().map(|o| o.into()).collect::<Vec<_>>(),
                &member_roles,
                user_id,
                guild_id, // @everyone role_id == guild_id
            );

            Ok(channel_perms)
        },
    ).await
}
```

### 6.4. Presence: write-behind паттерн

```rust
/// Обработка heartbeat от Gateway.
/// Данные пишутся только в Redis (write-behind: PostgreSQL не используется для presence).
pub async fn handle_heartbeat(
    redis_pool: &Pool,
    user_id: i64,
    presence_ttl_secs: u64,
) -> Result<(), AppError> {
    let mut conn = redis_pool.get().await?;

    // Atomic pipeline: обновить TTL всех presence ключей за один roundtrip
    redis::pipe()
        .cmd("EXPIRE")
        .arg(format!("presence:{user_id}:status"))
        .arg(presence_ttl_secs)
        .cmd("EXPIRE")
        .arg(format!("presence:{user_id}:clients"))
        .arg(presence_ttl_secs)
        .cmd("EXPIRE")
        .arg(format!("presence:{user_id}:activities"))
        .arg(presence_ttl_secs)
        .query_async(&mut conn)
        .await?;

    Ok(())
}

/// Установить presence статус.
pub async fn set_presence(
    redis_pool: &Pool,
    user_id: i64,
    status: &str,
    platform: &str,
    ttl_secs: u64,
) -> Result<(), AppError> {
    let mut conn = redis_pool.get().await?;

    redis::pipe()
        .cmd("SET")
        .arg(format!("presence:{user_id}:status"))
        .arg(status)
        .arg("EX")
        .arg(ttl_secs)
        .cmd("HSET")
        .arg(format!("presence:{user_id}:clients"))
        .arg(platform)
        .arg(status)
        .cmd("EXPIRE")
        .arg(format!("presence:{user_id}:clients"))
        .arg(ttl_secs)
        .query_async(&mut conn)
        .await?;

    Ok(())
}
```

### 6.5. Rate limiting (sliding window)

Реализация на основе `crates/rate-limit` (см. [CRATES.md](CRATES.md)):

```rust
/// Проверка rate limit через Redis ZSET sliding window.
/// Возвращает (allowed, remaining, retry_after_ms).
pub async fn check_rate_limit(
    redis_pool: &Pool,
    key: &str,
    limit: u32,
    window_secs: u64,
) -> Result<(bool, u32, u64), AppError> {
    let mut conn = redis_pool.get().await?;

    // Lua-скрипт выполняется атомарно в Redis
    let script = redis::Script::new(SLIDING_WINDOW_LUA);

    let now = chrono::Utc::now().timestamp_millis();
    let result: Vec<i64> = script
        .key(key)
        .arg(limit)
        .arg(window_secs)
        .arg(now)
        .invoke_async(&mut conn)
        .await?;

    let allowed = result[0] == 1;
    let remaining = result[1] as u32;
    let retry_after_ms = result[2] as u64;

    Ok((allowed, remaining, retry_after_ms))
}

/// Lua-скрипт для atomic sliding window (из crates/rate-limit).
const SLIDING_WINDOW_LUA: &str = r#"
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2]) * 1000
local now = tonumber(ARGV[3])

redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, window)
    return {1, limit - count - 1, window}
else
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local reset_after = oldest[2] + window - now
    return {0, 0, reset_after}
end
"#;
```

---

## 7. Мониторинг кеша

Источник: [Redis Performance Optimization](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/)

### 7.1. Ключевые метрики

| Метрика | Описание | Целевое значение | Предупреждение | Критично |
|---------|----------|-----------------|---------------|----------|
| **Hit ratio (user profiles)** | `keyspace_hits / (hits + misses)` для `user:*` | > 95% | < 95% | < 90% |
| **Hit ratio (permissions)** | `keyspace_hits / (hits + misses)` для `perms:*` | > 90% | < 90% | < 80% |
| **Hit ratio (общий)** | Redis INFO: `keyspace_hits / (hits + misses)` | > 90% | < 90% | < 80% |
| **Eviction rate** | Ключей вытесненных в секунду | < 100/сек | > 100/сек | > 1000/сек |
| **Memory usage** | Используемая память vs `maxmemory` | < 70% | > 70% | > 90% |
| **Memory by prefix** | Память на key prefix (`user:*`, `perms:*`, etc.) | — | Аномальный рост | — |
| **Command latency p99** | Время выполнения команд | < 1ms | > 1ms | > 5ms |
| **Slow commands** | `SLOWLOG GET` — команды медленнее threshold | 0 | > 10/мин | > 100/мин |
| **Connected clients** | Количество подключений | < 5000 | > 5000 | > 8000 |
| **Blocked clients** | Заблокированные клиенты (BRPOP, etc.) | 0 | > 0 | > 10 |

### 7.2. Prometheus метрики (redis_exporter)

```
# Hit ratio
redis_keyspace_hits_total
redis_keyspace_misses_total

# Memory
redis_memory_used_bytes
redis_memory_max_bytes

# Evictions
redis_evicted_keys_total

# Commands
redis_commands_duration_seconds_total
redis_slowlog_length

# Connections
redis_connected_clients
redis_blocked_clients
```

### 7.3. Метрики на уровне приложения (Prometheus + tracing)

Каждый сервис должен экспортировать:

```rust
/// Prometheus метрики для кеша.
/// Регистрируются при старте сервиса.

// Счётчик cache hit / miss по key prefix
// cache_hits_total{prefix="user"}
// cache_misses_total{prefix="guild"}

// Гистограмма времени операций с кешем
// cache_operation_duration_seconds{operation="get", prefix="perms"}
// cache_operation_duration_seconds{operation="set", prefix="user"}

// Счётчик инвалидаций по причине
// cache_invalidations_total{reason="nats_event", prefix="guild"}
// cache_invalidations_total{reason="cascade", prefix="perms"}
```

### 7.4. Диагностика Redis

```bash
# Общая информация
redis-cli INFO stats
redis-cli INFO memory

# Hit ratio
redis-cli INFO stats | grep keyspace

# Медленные команды (> 10ms по умолчанию)
redis-cli SLOWLOG GET 10

# Проверка латенции
redis-cli --latency
redis-cli --latency-history

# Размер ключей по паттерну (через SCAN, не KEYS)
redis-cli --scan --pattern 'guild:*' | wc -l

# Memory usage конкретного ключа
redis-cli MEMORY USAGE guild:123456789

# Клиенты
redis-cli CLIENT LIST
```

### 7.5. Алерты (Grafana / PagerDuty)

| Алерт | Условие | Действие |
|-------|---------|----------|
| Low hit ratio | < 80% за 5 мин | Проверить TTL, объём данных, eviction rate |
| High eviction rate | > 1000/сек за 1 мин | Увеличить `maxmemory`, проверить TTL |
| Memory critical | > 90% `maxmemory` | Увеличить RAM, проверить big keys |
| Slow commands | > 100 slow commands за 5 мин | Проверить SLOWLOG, оптимизировать паттерны |
| Replication lag | > 10 сек | Проверить сеть, нагрузку на replica |
| Cache invalidation spike | > 10x baseline за 1 мин | Возможен каскадный сброс или массовое обновление |

---

## 8. Сводка: кеш по сервисам

| Сервис | Какие данные кеширует | Основные ключи |
|--------|----------------------|----------------|
| **Auth** (`services/auth`) | JWT denylist, 2FA tickets, OAuth state, WebAuthn challenge | `jwt_deny:{jti}`, `2fa_ticket:{ticket}`, `oauth_state:{state}`, `webauthn_challenge:{user_id}` |
| **Users** (`services/users`) | Профили пользователей | `user:{user_id}` |
| **Guilds** (`services/guilds`) | Гильдии, каналы, роли, участники, права, инвайты, баны | `guild:{id}`, `guild:{id}:channels`, `guild:{id}:roles`, `guild:{id}:member:{uid}`, `perms:*`, `invite:{code}` |
| **Presence** (`services/presence`) | Статусы, client status, activities, typing, online members | `presence:{uid}:status`, `presence:{uid}:clients`, `typing:{cid}:{uid}`, `guild_online:{gid}` |
| **Gateway** (`services/gateway`) | Нет собственного кеша Redis (подписывается на NATS) | — |
| **Messages** (`services/messages`) | Нет Redis-кеша (читает из PostgreSQL/ScyllaDB напрямую) | — |
| **Notifications** (`services/notifications`) | Badge counts, push subscription data | — (опционально) |
| **Все сервисы** | Rate limits | `rate:{bucket}:{identifier}` |

---

## 9. Чеклист

- [ ] Каждый кешируемый тип данных имеет явный TTL (нет бесконечных ключей, кроме `guild_online:*`)
- [ ] TTL permissions (2 мин) короче TTL ролей (5 мин) — права пересчитываются чаще
- [ ] Каскадная инвалидация реализована для всех NATS-событий, влияющих на кеш
- [ ] `KEYS` запрещён (`rename-command` в redis.conf), используется `SCAN`
- [ ] Ни один ключ не превышает 512KB
- [ ] Cache-aside helper обрабатывает ошибки десериализации как cache miss
- [ ] Distributed lock реализован для горячих ключей крупных гильдий
- [ ] Локальный in-process кеш (TTL 1-5 сек) для hot keys в Gateway и Guilds Service
- [ ] Prometheus метрики: hit ratio, eviction rate, latency — по каждому key prefix
- [ ] Алерты на hit ratio < 80%, eviction > 1000/сек, memory > 90%
- [ ] Данные валидируются перед записью в кеш (защита от poisoning)
- [ ] Negative caching (маркер `__NULL__`) для несуществующих сущностей
- [ ] Redis connection pooling через `deadpool-redis` во всех сервисах
- [ ] При недоступности Redis сервис продолжает работать (fallback на PostgreSQL)
- [ ] Presence данные — единственное исключение (Redis = primary storage, TTL = heartbeat)
