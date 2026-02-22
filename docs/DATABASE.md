# Базы данных

Документация по всем хранилищам данных: конфигурация, безопасность, производительность, узкие места, стресс-тестирование, бэкапы, мониторинг.

## Источники

- [PostgreSQL 17 Documentation](https://www.postgresql.org/docs/17/)
- [PostgreSQL Wiki — Tuning Your PostgreSQL Server](https://wiki.postgresql.org/wiki/Tuning_Your_PostgreSQL_Server)
- [PGTune — PostgreSQL Configuration Calculator](https://pgtune.leopard.in.ua/)
- [PostgreSQL Wiki — Don't Do This](https://wiki.postgresql.org/wiki/Don%27t_Do_This)
- [PgBouncer Documentation](https://www.pgbouncer.org/config.html)
- [pgAudit — PostgreSQL Audit Extension](https://www.pgaudit.org/)
- [CIS PostgreSQL Benchmark](https://www.cisecurity.org/benchmark/postgresql)
- [Patroni Documentation — HA for PostgreSQL](https://patroni.readthedocs.io/)
- [Redis Documentation](https://redis.io/docs/)
- [Redis Security](https://redis.io/docs/latest/operate/oss_and_stack/management/security/)
- [Redis Sentinel Documentation](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)
- [Discord Engineering Blog — How Discord Stores Trillions of Messages](https://discord.com/blog/how-discord-stores-trillions-of-messages)
- [ScyllaDB Documentation](https://docs.scylladb.com/)
- [MinIO Documentation](https://min.io/docs/minio/linux/index.html)
- [Meilisearch Documentation](https://www.meilisearch.com/docs)

---

## Обзор хранилищ

| Хранилище | Назначение | Rust crate |
|-----------|-----------|------------|
| **PostgreSQL 17** | Пользователи, гильдии, каналы, роли, auth, настройки | `sqlx` (compile-time SQL) |
| **Redis / Valkey 8** | Кеш, сессии, presence, rate limiting, pub/sub | `redis` + `deadpool-redis` |
| **ScyllaDB** | Сообщения (при масштабировании, > 100M) | `scylla` |
| **MinIO** | Файлы, аватары, вложения (S3 API) | `aws-sdk-s3` |
| **Meilisearch** | Полнотекстовый поиск | `meilisearch-sdk` |

---

## 1. PostgreSQL

### 1.1. Роль в системе

PostgreSQL — основная реляционная БД. Хранит:
- Auth: credentials, refresh tokens, OAuth, WebAuthn
- Users: профили, настройки, друзья, блоки
- Guilds: серверы, каналы, роли, участники, инвайты, баны, аудит-лог
- Messages: сообщения (на старте, до перехода на ScyllaDB)

**Принцип**: каждый микросервис имеет свою отдельную БД (database isolation). Auth Service не имеет доступа к БД Users Service и наоборот.

### 1.2. Конфигурация (production)

Источник: [PostgreSQL Wiki — Tuning](https://wiki.postgresql.org/wiki/Tuning_Your_PostgreSQL_Server), [PGTune](https://pgtune.leopard.in.ua/)

Рассчитано для сервера: 16 GB RAM, 4 CPU, SSD.

```ini
# postgresql.conf

# Память
shared_buffers = 4GB                    # 25% от RAM (PostgreSQL docs рекомендация)
effective_cache_size = 12GB             # 75% от RAM (оценка доступного кеша ОС)
work_mem = 16MB                         # на каждую операцию сортировки/хеша
maintenance_work_mem = 1GB              # для VACUUM, CREATE INDEX
huge_pages = try                        # использовать huge pages если доступны

# WAL
wal_level = replica                     # для streaming replication
max_wal_size = 4GB                      # макс размер WAL до checkpoint
min_wal_size = 1GB
wal_compression = zstd                  # сжатие WAL (PostgreSQL 15+)
archive_mode = on                       # для PITR бэкапов
archive_command = 'cp %p /archive/%f'   # или pg_basebackup + WAL shipping

# Checkpoints
checkpoint_completion_target = 0.9      # распределить I/O checkpoints
checkpoint_timeout = 15min

# Connections
max_connections = 200                   # НЕ ставить больше — использовать PgBouncer
superuser_reserved_connections = 3

# Planner
random_page_cost = 1.1                  # для SSD (default 4.0 для HDD)
effective_io_concurrency = 200          # для SSD

# Autovacuum (критически важно)
autovacuum = on
autovacuum_max_workers = 3
autovacuum_naptime = 30s                # проверять каждые 30 сек (default 1min)
autovacuum_vacuum_threshold = 50
autovacuum_vacuum_scale_factor = 0.05   # 5% (default 20% — слишком много для больших таблиц)
autovacuum_analyze_threshold = 50
autovacuum_analyze_scale_factor = 0.025

# Логирование
log_min_duration_statement = 200        # логировать запросы дольше 200ms
log_checkpoints = on
log_lock_waits = on
log_temp_files = 0                      # логировать все temp files
log_autovacuum_min_duration = 0         # логировать все autovacuum
log_statement = 'ddl'                   # логировать DDL операции

# Статистика
shared_preload_libraries = 'pg_stat_statements,pgaudit'
pg_stat_statements.track = all
pg_stat_statements.max = 10000
```

### 1.3. Connection Pooling — PgBouncer

Источник: [PgBouncer docs](https://www.pgbouncer.org/config.html)

**Зачем**: PostgreSQL создаёт отдельный процесс на каждое соединение (~10MB RAM каждое). 1000 соединений = 10GB только на процессы. PgBouncer мультиплексирует.

```ini
# pgbouncer.ini

[databases]
auth_db = host=postgres-auth port=5432 dbname=auth
users_db = host=postgres-users port=5432 dbname=users
guilds_db = host=postgres-guilds port=5432 dbname=guilds

[pgbouncer]
listen_port = 6432
listen_addr = 0.0.0.0

# Режим пулинга
pool_mode = transaction                 # НЕ session — transaction более эффективен для микросервисов

# Лимиты
max_client_conn = 1000                  # макс соединений от приложений
default_pool_size = 25                  # соединений к PG на БД
min_pool_size = 5
reserve_pool_size = 5
reserve_pool_timeout = 3

# Таймауты
server_idle_timeout = 300               # закрыть idle серверное соединение через 5 мин
client_idle_timeout = 0                 # не закрывать idle клиентские
query_timeout = 30                      # убить запрос через 30 сек
query_wait_timeout = 120                # ожидание свободного соединения

# Безопасность
auth_type = scram-sha-256              # НЕ md5, НЕ trust
client_tls_sslmode = require
server_tls_sslmode = require
```

**Формула размера пула** (PostgreSQL Wiki):
```
connections = (core_count * 2) + effective_spindle_count
```
Для SSD: `connections = (4 * 2) + 1 = 9` (минимум на один инстанс PG).
С учётом нескольких микросервисов: 20-30 соединений на PG, 25 default_pool_size.

**SQLx pool** (в Rust коде):
```rust
let pool = PgPoolOptions::new()
    .max_connections(25)            // к PgBouncer, не напрямую к PG
    .min_connections(5)
    .acquire_timeout(Duration::from_secs(5))
    .idle_timeout(Duration::from_secs(300))
    .max_lifetime(Duration::from_secs(1800))
    .connect(&database_url)
    .await?;
```

### 1.4. Безопасность

Источник: [CIS PostgreSQL Benchmark](https://www.cisecurity.org/benchmark/postgresql), [PostgreSQL Security docs](https://www.postgresql.org/docs/17/auth-methods.html)

**pg_hba.conf (аутентификация):**
```
# TYPE  DATABASE   USER        ADDRESS          METHOD
local   all        postgres                     peer
host    all        all         0.0.0.0/0        scram-sha-256
hostssl all        all         0.0.0.0/0        scram-sha-256
```

**Правила:**
- Никогда `trust` в production
- Только `scram-sha-256` (не md5)
- SSL/TLS обязателен (`hostssl`)
- Минимум привилегий: каждый сервис = свой пользователь + своя БД
- `REVOKE ALL ON SCHEMA public FROM PUBLIC`

**Роли по сервисам:**
```sql
-- Auth Service
CREATE ROLE auth_service WITH LOGIN PASSWORD '...' CONNECTION LIMIT 30;
CREATE DATABASE auth_db OWNER auth_service;
REVOKE ALL ON DATABASE auth_db FROM PUBLIC;

-- Users Service
CREATE ROLE users_service WITH LOGIN PASSWORD '...' CONNECTION LIMIT 30;
CREATE DATABASE users_db OWNER users_service;
REVOKE ALL ON DATABASE users_db FROM PUBLIC;

-- Guilds Service
CREATE ROLE guilds_service WITH LOGIN PASSWORD '...' CONNECTION LIMIT 30;
CREATE DATABASE guilds_db OWNER guilds_service;
REVOKE ALL ON DATABASE guilds_db FROM PUBLIC;
```

**pgAudit (аудит SQL):**

Источник: [pgAudit](https://www.pgaudit.org/)

```sql
-- Включить аудит DDL и привилегированных операций
ALTER SYSTEM SET pgaudit.log = 'ddl, role, write';
ALTER SYSTEM SET pgaudit.log_catalog = off;
ALTER SYSTEM SET pgaudit.log_parameter = on;
ALTER SYSTEM SET pgaudit.log_statement_once = on;
```

Логирует: CREATE/ALTER/DROP таблиц, GRANT/REVOKE, INSERT/UPDATE/DELETE.

**Encryption at rest:**
- Используем шифрование на уровне диска (LUKS / dm-crypt, или Kubernetes encrypted volumes)
- PostgreSQL 17 не имеет встроенного TDE — используем уровень ОС/инфраструктуры
- Для особо чувствительных полей (totp_secret, oauth tokens): шифрование на уровне приложения (AES-256-GCM) перед сохранением в БД

### 1.5. Высокая доступность (HA)

Источник: [Patroni docs](https://patroni.readthedocs.io/), [PostgreSQL Streaming Replication](https://www.postgresql.org/docs/17/warm-standby.html)

**Архитектура: Patroni + Streaming Replication**

```
┌─────────────────────────────────────────┐
│              Patroni Cluster            │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌────────┐│
│  │ PG Primary│  │PG Standby│  │  etcd  ││
│  │  (write)  │──│  (read)  │  │(leader)││
│  └──────────┘  └──────────┘  └────────┘│
│       │              │                  │
│       └──────────────┘                  │
│       Streaming Replication             │
└─────────────────────────────────────────┘
         │
    ┌────┴────┐
    │PgBouncer│  ← приложения подключаются сюда
    └─────────┘
```

**Patroni** автоматически:
- Определяет лидера через etcd/ZooKeeper/Consul
- Переключает роли при падении primary (automatic failover, ~10-30 секунд)
- Управляет streaming replication

**Конфигурация Patroni (patroni.yml):**
```yaml
scope: platform-postgres
namespace: /db/

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576    # 1MB — не переключаться если standby отстаёт больше
    synchronous_mode: true              # синхронная репликация (0 data loss)

  postgresql:
    parameters:
      synchronous_commit: "on"
      max_connections: 200
      shared_buffers: 4GB
      wal_level: replica
      max_wal_senders: 5
      max_replication_slots: 5
      hot_standby: "on"

postgresql:
  listen: 0.0.0.0:5432
  connect_address: ${POD_IP}:5432
  authentication:
    superuser:
      username: postgres
    replication:
      username: replicator

tags:
  nofailover: false
  noloadbalance: false
  nosync: false
```

**Synchronous mode**: гарантирует 0 потерь данных при failover (RPO = 0). Ценой ~2-5ms дополнительной latency на каждый write.

### 1.6. Бэкапы

Источник: [PostgreSQL Backup docs](https://www.postgresql.org/docs/17/backup.html)

| Метод | RPO | RTO | Когда |
|-------|-----|-----|-------|
| `pg_dump` | до 24 часов | 30-60 мин | малые БД (< 10GB) |
| `pg_basebackup` + WAL archiving | минуты | 10-30 мин | production (PITR) |
| Continuous WAL archiving (PITR) | секунды | 5-15 мин | production (рекомендуемый) |

**Стратегия (production):**

1. **Ежедневный** полный бэкап через `pg_basebackup`
2. **Непрерывный** WAL archiving (каждый WAL сегмент = 16MB)
3. **PITR** (Point-In-Time Recovery): восстановление на любой момент времени
4. Хранение: 7 дней полных бэкапов + WAL
5. Тестирование восстановления: раз в неделю (автоматически)

```bash
# Ежедневный полный бэкап
pg_basebackup -h postgres-primary -U replicator \
  -D /backups/$(date +%Y%m%d) \
  -Ft -z -P --wal-method=stream

# Restore (PITR на конкретное время)
restore_command = 'cp /archive/%f %p'
recovery_target_time = '2025-01-15 12:00:00+00'
```

### 1.7. Индексы и производительность

Источник: [PostgreSQL Index Types](https://www.postgresql.org/docs/17/indexes-types.html), [Use The Index, Luke](https://use-the-index-luke.com/)

**Типы индексов:**

| Тип | Когда использовать | Пример |
|-----|-------------------|--------|
| **B-tree** (default) | Equality, range, ORDER BY | `WHERE id = $1`, `WHERE created_at > $1` |
| **Hash** | Только equality (быстрее B-tree на =) | `WHERE token_hash = $1` |
| **GIN** | JSONB, массивы, полнотекстовый | `WHERE tags @> '{rust}'` |
| **GiST** | Геоданные, range types | `WHERE ip_range @> '10.0.0.1'` |
| **BRIN** | Коррелированные данные (timestamp + вставка по порядку) | `WHERE created_at BETWEEN $1 AND $2` на таблице сообщений |

**Partial indexes** (экономия места + скорость):
```sql
-- Только активные пользователи (не deleted)
CREATE INDEX idx_users_active ON users (username) WHERE deleted_at IS NULL;

-- Только pending дружбы
CREATE INDEX idx_friendships_pending ON friendships (addressee_id) WHERE status = 'pending';

-- Только не истёкшие инвайты
CREATE INDEX idx_invites_active ON invites (code) WHERE expires_at IS NULL OR expires_at > NOW();
```

**Covering indexes** (index-only scans):
```sql
-- Запрос SELECT id, username, avatar_hash может использовать только индекс
CREATE INDEX idx_users_profile ON users (id) INCLUDE (username, display_name, avatar_hash);
```

**EXPLAIN ANALYZE** — обязательно для каждого нового запроса:
```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM guild_members WHERE guild_id = $1 ORDER BY joined_at LIMIT 100;
```

Искать: `Seq Scan` на больших таблицах (нужен индекс), `Sort` без индекса, `Nested Loop` с большим кол-вом строк.

### 1.8. Партиционирование

Для таблицы сообщений (до перехода на ScyllaDB):

```sql
CREATE TABLE messages (
    id              BIGINT NOT NULL,        -- Snowflake ID
    channel_id      BIGINT NOT NULL,
    author_id       BIGINT NOT NULL,
    content         TEXT,
    created_at      TIMESTAMPTZ NOT NULL
) PARTITION BY RANGE (created_at);

-- Партиции по месяцам
CREATE TABLE messages_2025_01 PARTITION OF messages
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE messages_2025_02 PARTITION OF messages
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
-- ... автоматизировать через pg_partman
```

Преимущество: `VACUUM` и `DELETE` работают по партициям, не блокируя всю таблицу. Старые партиции можно просто `DROP`.

### 1.9. Узкие места

| Проблема | Причина | Решение |
|---------|---------|---------|
| **Connection exhaustion** | Слишком много прямых соединений | PgBouncer (transaction mode) |
| **Lock contention** | Параллельные UPDATE одной строки | Advisory locks, очереди, SKIP LOCKED |
| **Table bloat** | Агрессивные UPDATE без VACUUM | Настроить autovacuum (scale_factor = 0.05) |
| **Slow queries** | Отсутствие индексов, Seq Scan | EXPLAIN ANALYZE, pg_stat_statements |
| **N+1 queries** | Цикл запросов вместо JOIN | SQLx batch queries, JOIN, subquery |
| **Long transactions** | Удерживают locks и блокируют VACUUM | idle_in_transaction_session_timeout = 30s |
| **Index bloat** | Частые UPDATE индексированных колонок | REINDEX CONCURRENTLY (периодически) |
| **WAL accumulation** | Replication lag, abandoned slots | max_slot_wal_keep_size = 10GB |

### 1.10. Стресс-тестирование

Источник: [pgbench](https://www.postgresql.org/docs/17/pgbench.html)

```bash
# Инициализация тестовой БД (scale factor 100 = ~1.5GB данных)
pgbench -i -s 100 testdb

# Тест на чтение (SELECT)
pgbench -c 50 -j 4 -T 60 -S testdb
# -c 50 = 50 клиентов
# -j 4 = 4 потока
# -T 60 = 60 секунд
# -S = только SELECT

# Тест на запись (TPC-B)
pgbench -c 50 -j 4 -T 60 testdb

# Custom сценарий (наши запросы)
pgbench -c 100 -j 8 -T 120 -f custom_scenario.sql testdb
```

**Целевые метрики:**
- p99 латенция SELECT: < 5ms
- p99 латенция INSERT: < 10ms
- TPS (transactions per second): > 5000 при 50 соединениях

### 1.11. Мониторинг

Источник: [PostgreSQL Monitoring](https://www.postgresql.org/docs/17/monitoring-stats.html)

**Ключевые метрики (Prometheus + postgres_exporter):**

| Метрика | Предупреждение | Критично |
|---------|---------------|----------|
| Connections used % | > 70% | > 90% |
| Replication lag (bytes) | > 1MB | > 10MB |
| Transaction rate (TPS) | baseline +50% | baseline +100% |
| Cache hit ratio | < 99% | < 95% |
| Dead tuples % (bloat) | > 10% | > 20% |
| Longest running query | > 10s | > 30s |
| Lock waits | > 10/min | > 50/min |
| Disk usage % | > 70% | > 85% |
| WAL generation rate | > 1GB/min | > 5GB/min |

**pg_stat_statements** — топ медленных запросов:
```sql
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 20;
```

**pg_stat_user_tables** — bloat detection:
```sql
SELECT relname, n_dead_tup, n_live_tup,
       round(n_dead_tup::float / NULLIF(n_live_tup, 0) * 100, 2) AS dead_pct
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY dead_pct DESC;
```

### 1.12. Миграции

Через `sqlx-cli`:
```bash
# Создать миграцию
sqlx migrate add create_users

# Применить
sqlx migrate run --database-url $DATABASE_URL

# Откатить
sqlx migrate revert --database-url $DATABASE_URL
```

**Правила:**
- Каждая миграция — один файл, один DDL statement
- Миграции идемпотентны (IF NOT EXISTS)
- Никогда ALTER TABLE с блокировкой на больших таблицах — использовать `CONCURRENTLY`
- Тестировать миграции на копии production данных

---

## 2. Redis / Valkey

### 2.1. Роль в системе

Redis используется как:
- **Кеш** — профили, каналы, роли, права (TTL 2-10 мин)
- **Сессии** — JWT denylist, 2FA tickets, OAuth state (TTL)
- **Rate limiting** — sliding window counters
- **Presence** — online/offline/idle/dnd статусы
- **Typing indicators** — TTL 10 сек
- **Pub/Sub** — дополнительно к NATS для real-time уведомлений

### 2.2. Конфигурация (production)

Источник: [Redis Configuration](https://redis.io/docs/latest/operate/oss_and_stack/management/config/)

```ini
# redis.conf

# Память
maxmemory 4gb
maxmemory-policy allkeys-lru            # LRU eviction (подходит для кеша)

# Persistence
# AOF для данных которые нельзя потерять (сессии, rate limits)
appendonly yes
appendfsync everysec                    # fsync каждую секунду (компромисс durability/performance)
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# RDB snapshots (дополнительно)
save 900 1                              # snapshot каждые 15 мин если >= 1 изменение
save 300 100
save 60 10000

# Сеть
tcp-backlog 511
timeout 300                             # закрыть idle соединения через 5 мин
tcp-keepalive 60

# Безопасность
requirepass ${REDIS_PASSWORD}
# ACL (Redis 6+) — ограничить команды по пользователям
# user cache_user on >${PASSWORD} ~cache:* +get +set +del +expire +ttl
# user rate_user on >${PASSWORD} ~rate:* +get +incr +expire +ttl +pttl

# Отключить опасные команды
rename-command FLUSHALL ""
rename-command FLUSHDB ""
rename-command DEBUG ""
rename-command CONFIG ""                # или rename для admin-only доступа

# TLS
tls-port 6379
port 0                                  # отключить plain text порт
tls-cert-file /etc/redis/tls/redis.crt
tls-key-file /etc/redis/tls/redis.key
tls-ca-cert-file /etc/redis/tls/ca.crt

# Лимиты
maxclients 10000
```

### 2.3. Политики вытеснения (eviction)

Источник: [Redis Eviction](https://redis.io/docs/latest/develop/reference/eviction/)

| Политика | Когда |
|---------|-------|
| `allkeys-lru` | Кеш (наш случай) — вытесняет наименее используемые ключи |
| `volatile-lru` | Только ключи с TTL |
| `allkeys-lfu` | Вытеснение по частоте (Least Frequently Used) |
| `noeviction` | Возвращать ошибку при OOM (для данных, не кеша) |

Мы используем `allkeys-lru` — Redis как кеш. Если данные вытеснены, сервис пойдёт в PostgreSQL.

### 2.4. Безопасность

Источник: [Redis Security](https://redis.io/docs/latest/operate/oss_and_stack/management/security/)

- **AUTH** обязателен (`requirepass`)
- **ACL** (Redis 6+): отдельные пользователи для разных сервисов с ограничением на key patterns и команды
- **TLS** обязателен в production (port 0, tls-port 6379)
- **bind** — только на внутренние IP (не 0.0.0.0 напрямую, через K8s Service)
- **rename-command** — отключить FLUSHALL, FLUSHDB, DEBUG, CONFIG, KEYS
- **Никогда** не выставлять Redis в интернет
- **Protected mode** — включен по умолчанию в Redis 3.2+

### 2.5. Высокая доступность — Sentinel

Источник: [Redis Sentinel](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)

```
┌────────────────────────────────────────┐
│           Redis Sentinel (x3)          │
│                                        │
│  ┌──────────┐       ┌──────────┐      │
│  │  Master  │──────→│ Replica  │      │
│  │  (write) │       │  (read)  │      │
│  └──────────┘       └──────────┘      │
│                                        │
│  Sentinel мониторит и переключает     │
│  master при падении (~30 сек failover) │
└────────────────────────────────────────┘
```

**Когда использовать:**
- **Sentinel** — до ~100K ops/sec, один master для записи
- **Redis Cluster** — если нужно больше RAM, чем помещается в один инстанс, или > 100K ops/sec на запись

На старте: Sentinel (проще). При масштабировании: Redis Cluster.

### 2.6. Узкие места

| Проблема | Причина | Решение |
|---------|---------|---------|
| **OOM / eviction storm** | Больше данных чем maxmemory | Увеличить RAM, проверить TTL, уменьшить данные |
| **Hot key** | Одна гильдия с 100K online пользователей | Локальный кеш в приложении (TTL 5 сек) |
| **Big keys** | Один ключ > 1MB (список 100K друзей) | Разбить на paginated sets, HSCAN |
| **KEYS command** | Блокирует Redis (O(n)) | Запрещён через rename-command. Использовать SCAN |
| **Slow Lua scripts** | Блокирующие скрипты | Ограничить время выполнения, lua-time-limit 5000 |
| **Connection storm** | Микросервисы рестартуются одновременно | Connection pooling (deadpool-redis), backoff |

### 2.7. Мониторинг

**Ключевые метрики (Prometheus + redis_exporter):**

| Метрика | Предупреждение | Критично |
|---------|---------------|----------|
| Memory used % | > 70% | > 90% |
| Evictions/sec | > 100 | > 1000 |
| Connected clients | > 5000 | > 8000 |
| Keyspace hit ratio | < 90% | < 80% |
| Replication lag (sec) | > 1 | > 10 |
| Command latency p99 | > 1ms | > 5ms |
| Blocked clients | > 0 | > 10 |

```bash
# Мониторинг в реальном времени
redis-cli INFO stats
redis-cli INFO memory
redis-cli --latency
redis-cli SLOWLOG GET 10
```

---

## 3. ScyllaDB (масштабирование сообщений)

### 3.1. Зачем ScyllaDB

Источник: [Discord Engineering Blog — How Discord Stores Trillions of Messages](https://discord.com/blog/how-discord-stores-trillions-of-messages)

Discord перешёл с Cassandra на ScyllaDB:
- **p99 чтения**: 40-125ms (Cassandra) → 15ms (ScyllaDB)
- **p99 записи**: стабильные 5ms
- **Нет GC пауз**: ScyllaDB написана на C++ (vs Java у Cassandra)
- **Линейное масштабирование**: добавление нод = линейный рост throughput
- **Меньше нод**: 177 Cassandra нод → 72 ScyllaDB нод (при тех же данных)

### 3.2. Когда переходить

**PostgreSQL** (старт): до ~100M сообщений. Партиционирование по `created_at`.

**ScyllaDB** (масштабирование): когда PostgreSQL начинает тормозить:
- p99 чтения сообщений > 50ms
- Размер таблицы сообщений > 500GB
- > 10K writes/sec на сообщения

### 3.3. Модель данных (ScyllaDB)

```sql
-- Основная таблица сообщений
-- Partition key: channel_id (все сообщения канала на одной ноде)
-- Clustering key: message_id DESC (сортировка по времени, последние первые)
CREATE TABLE messages (
    channel_id  bigint,
    message_id  bigint,             -- Snowflake ID (содержит timestamp)
    author_id   bigint,
    content     text,
    edited_at   timestamp,
    flags       int,
    attachments frozen<list<text>>,
    embeds      frozen<list<text>>,
    reactions   frozen<map<text, frozen<list<bigint>>>>,
    reply_to    bigint,
    PRIMARY KEY (channel_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND compaction = {'class': 'LeveledCompactionStrategy'}
  AND gc_grace_seconds = 864000;    -- 10 дней
```

**Bucket pattern** (для очень активных каналов):

Если один канал имеет миллионы сообщений, партиция станет слишком большой. Решение — bucket по времени:

```sql
CREATE TABLE messages (
    channel_id  bigint,
    bucket      bigint,             -- channel_id + (message_id >> 22 / BUCKET_SIZE)
    message_id  bigint,
    -- ... остальные поля
    PRIMARY KEY ((channel_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

Discord использует bucket_interval, чтобы каждая партиция содержала сообщения за фиксированный период.

### 3.4. Rust crate

```toml
scylla = "0.14"                     # async Rust driver для ScyllaDB
```

```rust
use scylla::{SessionBuilder, Session};

let session: Session = SessionBuilder::new()
    .known_node("scylla-node-1:9042")
    .known_node("scylla-node-2:9042")
    .known_node("scylla-node-3:9042")
    .user("messages_service", &password)
    .use_keyspace("messages", false)
    .build()
    .await?;
```

---

## 4. MinIO (файловое хранилище)

### 4.1. Роль в системе

MinIO — S3-совместимое хранилище для:
- Аватары пользователей
- Иконки и баннеры серверов
- Вложения в сообщениях
- Media файлы

### 4.2. Конфигурация

Источник: [MinIO docs](https://min.io/docs/minio/linux/index.html)

**Erasure Coding**: MinIO использует Reed-Solomon erasure coding. Минимум 4 диска, может потерять до N/2 дисков без потери данных.

```yaml
# docker-compose (dev) / K8s StatefulSet (prod)
MINIO_ROOT_USER: admin
MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}  # минимум 16 символов
MINIO_BROWSER: "off"                          # отключить web UI в production
MINIO_REGION: us-east-1
```

**Bucket structure:**
```
avatars/          # user avatars (128x128, 256x256, 1024x1024)
banners/          # user/guild banners
icons/            # guild icons
attachments/      # message attachments
emojis/           # custom guild emojis
```

### 4.3. Безопасность

- **TLS** обязателен
- **Presigned URLs** для загрузки/скачивания (TTL 15 мин для upload, 1 час для download)
- **Bucket policies**: никакого public доступа, только через presigned URLs или CDN
- **Lifecycle rules**: удалять temp uploads через 24 часа
- **Отдельные credentials** для каждого сервиса (Access Key + Secret Key)
- **Файлы отдаются через CDN** (отдельный домен, без cookies) — предотвращение cookie theft

**Проверка файлов перед сохранением** (в Media Service):
1. MIME через magic bytes (`infer` crate), не по расширению
2. Размер: макс 25MB (вложения), 8MB (аватары)
3. Whitelist форматов: PNG, JPEG, WebP, GIF, MP4, MP3, PDF
4. Strip EXIF (`img-parts` crate, zero-copy) — удалить GPS, камера, etc.
5. Антивирус (ClamAV) для произвольных файлов
6. Рандомное имя: UUIDv7

### 4.4. Lifecycle rules

```json
{
    "Rules": [
        {
            "ID": "delete-temp-uploads",
            "Filter": { "Prefix": "temp/" },
            "Status": "Enabled",
            "Expiration": { "Days": 1 }
        },
        {
            "ID": "delete-old-avatars",
            "Filter": { "Prefix": "avatars/old/" },
            "Status": "Enabled",
            "Expiration": { "Days": 30 }
        }
    ]
}
```

---

## 5. Meilisearch (полнотекстовый поиск)

### 5.1. Роль в системе

Источник: [Meilisearch docs](https://www.meilisearch.com/docs)

Meilisearch — typo-tolerant поисковый движок (написан на Rust). Используется для:
- Поиск по сообщениям
- Поиск серверов (guild discovery)
- Поиск пользователей

### 5.2. Индексы

```json
// Индекс сообщений
{
    "uid": "messages",
    "primaryKey": "id",
    "searchableAttributes": ["content"],
    "filterableAttributes": ["channel_id", "guild_id", "author_id", "created_at"],
    "sortableAttributes": ["created_at"],
    "rankingRules": ["words", "typo", "proximity", "attribute", "sort", "exactness"]
}

// Индекс серверов (discovery)
{
    "uid": "guilds",
    "primaryKey": "id",
    "searchableAttributes": ["name", "description"],
    "filterableAttributes": ["member_count", "features", "preferred_locale"],
    "sortableAttributes": ["member_count"]
}
```

### 5.3. Производительность

- Типичная latency: < 50ms
- Рекомендуемый лимит документов: ~10M на индекс
- RAM: ~1GB на 1M документов (зависит от размера)
- Индексация: асинхронная через NATS (`message.created` → Search Service добавляет в индекс)

### 5.4. Безопасность

- **API key** обязателен (master key + search key + admin key)
- **Search key**: только search, ограничение по индексам, TTL
- **Admin key**: CRUD документов, только для Search Service (backend)
- **Никогда** не отдавать master key или admin key на фронтенд
- Meilisearch не имеет встроенного TLS — проксировать через Caddy/nginx с TLS

### 5.5. Бэкапы

```bash
# Создать snapshot (мгновенный)
curl -X POST http://meilisearch:7700/snapshots -H "Authorization: Bearer ${MEILI_MASTER_KEY}"

# Dumps (полный экспорт, для миграции)
curl -X POST http://meilisearch:7700/dumps -H "Authorization: Bearer ${MEILI_MASTER_KEY}"
```

Snapshots хранятся в `--snapshot-dir`, рекомендуется ежедневно.

---

## 6. Общая стратегия

### 6.1. Какие данные где

| Данные | Хранилище | Причина |
|--------|-----------|---------|
| User credentials, tokens | PostgreSQL (auth_db) | ACID, FK, секьюрность |
| User profiles, settings | PostgreSQL (users_db) | ACID, FK |
| Guilds, channels, roles | PostgreSQL (guilds_db) | ACID, FK, сложные JOIN |
| Messages (< 100M) | PostgreSQL (messages_db) | Партиционирование |
| Messages (> 100M) | ScyllaDB | Линейное масштабирование |
| Кеш (profiles, channels) | Redis | TTL, LRU, скорость |
| Сессии, JWT denylist | Redis | TTL, быстрый lookup |
| Rate limits | Redis | Atomic INCR, TTL |
| Presence (online/offline) | Redis | SET per guild, TTL |
| Typing indicators | Redis | TTL 10 сек |
| Файлы, медиа | MinIO | S3 API, erasure coding |
| Полнотекстовый поиск | Meilisearch | Typo-tolerant, < 50ms |

### 6.2. Принципы

1. **Database per service** — каждый микросервис имеет свою БД, никаких общих таблиц
2. **No cross-service JOINs** — данные из другого сервиса получаем через NATS request/reply или кеш
3. **Cache-aside** — сервис сначала проверяет Redis, при miss идёт в PostgreSQL и кеширует
4. **Write-through** — при записи в PostgreSQL инвалидируем кеш Redis
5. **Eventual consistency** — между сервисами допустима задержка (через NATS events)
6. **Strict consistency** — внутри одного сервиса (PostgreSQL ACID)

### 6.3. Чеклист безопасности (все хранилища)

- [ ] PostgreSQL: только scram-sha-256, TLS обязателен
- [ ] PostgreSQL: отдельная роль для каждого сервиса, минимум привилегий
- [ ] PostgreSQL: pgAudit для логирования DDL и write операций
- [ ] PostgreSQL: никакого `trust` в pg_hba.conf
- [ ] PostgreSQL: бэкапы тестируются (restore) еженедельно
- [ ] Redis: AUTH + ACL, TLS, rename-command для опасных команд
- [ ] Redis: не выставлен в интернет (только внутренняя сеть K8s)
- [ ] MinIO: presigned URLs, TLS, no public buckets
- [ ] MinIO: файлы проверяются перед сохранением (MIME, размер, антивирус)
- [ ] Meilisearch: API keys, search key с ограничениями, проксирование через TLS
- [ ] Все: шифрование на уровне диска (LUKS / K8s encrypted volumes)
- [ ] Все: credentials в Kubernetes Secrets (не в коде, не в .env в репо)
- [ ] Все: мониторинг и алерты настроены (Prometheus + Grafana)
- [ ] Все: connection pooling для каждого хранилища
