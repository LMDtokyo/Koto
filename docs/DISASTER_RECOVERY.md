# Disaster Recovery и Operational Runbook

Комплексный документ по аварийному восстановлению, операционным процедурам и обеспечению
отказоустойчивости коммуникационной платформы. Охватывает все 12 микросервисов (Rust/Axum),
хранилища данных, инфраструктуру Kubernetes и процессы реагирования на инциденты.

---

## Оглавление

1. [SLO/SLA определения](#1-slosla-определения)
2. [Стратегия бэкапов](#2-стратегия-бэкапов)
3. [Сценарии отказов и Runbooks](#3-сценарии-отказов-и-runbooks)
4. [Процедуры восстановления](#4-процедуры-восстановления)
5. [Тестирование DR](#5-тестирование-dr)
6. [Мониторинг и алерты](#6-мониторинг-и-алерты)
7. [Коммуникация при инцидентах](#7-коммуникация-при-инцидентах)
8. [Инфраструктурная устойчивость](#8-инфраструктурная-устойчивость)
9. [Источники](#9-источники)

---

## 1. SLO/SLA определения

### 1.1. Service Level Objectives

| Метрика | SLO Target | Метод измерения | Допустимый простой |
|---|---|---|---|
| Availability (REST API, порт 3000) | 99.9% | Успешные ответы / всего запросов (Prometheus) | 8 ч 45 мин / год |
| Availability (WebSocket Gateway, порт 4000) | 99.9% | Время в connected / общее время (клиентские метрики) | 8 ч 45 мин / год |
| API latency p50 | < 100ms | `http_request_duration_seconds` histogram | - |
| API latency p99 | < 500ms | `http_request_duration_seconds` histogram | - |
| WS message delivery p99 | < 200ms | E2E measurement (отправка -> доставка клиенту) | - |
| Message durability | 99.999% | Подтвержденные сообщения без потерь | - |
| Recovery Time Objective (RTO) | < 30 мин | Время от обнаружения до восстановления | - |
| Recovery Point Objective (RPO) | < 5 мин | Максимальное окно потери данных | - |

### 1.2. Error Budget

```
Error Budget = 1 - SLO = 0.1%

За месяц (30 дней):
  0.1% x 30 x 24 x 60 = 43.2 минуты допустимого простоя

За квартал (90 дней):
  0.1% x 90 x 24 x 60 = 129.6 минут (~2 часа 10 минут)

За год (365 дней):
  0.1% x 365 x 24 x 60 = 525.6 минут (~8 часов 45 минут)
```

При исчерпании Error Budget за квартал:
- Замораживаются все feature-деплои
- Фокус команды переключается на reliability
- Разрешены только hotfix и infrastructure-улучшения

### 1.3. RTO/RPO по компонентам

| Компонент | RTO (auto) | RTO (manual) | RPO | Обоснование |
|---|---|---|---|---|
| PostgreSQL (Patroni failover) | 10-30 сек | 2-5 мин | 0 (sync replication) | `synchronous_mode: true` |
| PostgreSQL (PITR из WAL archive) | - | 5-15 мин | секунды | Непрерывный WAL archiving |
| Redis (Sentinel failover) | ~30 сек | 2-5 мин | ~1 сек | `appendfsync everysec` |
| NATS JetStream | 5-15 сек | 1-5 мин | 0 | Cluster R=3 replication |
| ScyllaDB | 10-60 сек | 5-15 мин | 0 | Multi-node replication |
| MinIO | 1-5 мин | часы | 0 | Erasure coding |
| Meilisearch | 1-5 мин | 5-30 мин | до 24 ч | Переиндексация из source |
| Микросервис (pod restart) | 5-30 сек | 1-3 мин | 0 | Stateless, данные в БД |
| WS Gateway (shard) | 5-30 сек | 1-5 мин | пропущенные events | RESUME + JetStream replay |

### 1.4. Архитектурная карта зависимостей

```
                         +--------------+
                         |   Клиенты    |
                         | Web/Desktop/ |
                         | Mobile/Bots  |
                         +------+-------+
                                |
                         +------v-------+
                         |    Caddy     |
                         | TLS терминация|
                         | Rate limiting|
                         +--+--------+--+
                            |        |
                   +--------v-+  +---v--------+
                   | REST API |  | WebSocket  |
                   | Gateway  |  | Gateway    |
                   | :3000    |  | :4000      |
                   +----+-----+  +-----+------+
                        |              |
                        +------++------+
                               ||
                          +----vv----+
                          |   NATS   |
                          | JetStream|
                          | (3 ноды) |
                          +----+-----+
                               |
           +-------+-------+---+---+-------+-------+
           |       |       |       |       |       |
       +---v-+ +--v--+ +--v--+ +--v--+ +--v--+ +--v---+
       |Auth | |Users| |Guild| |Msgs | |Media| |Notif |
       |:3001| |:3002| |:3003| |:3004| |:3005| |:3006 |
       +--+--+ +--+--+ +--+--+ +--+--+ +--+--+ +--+---+
          |       |       |       |       |       |
       +--v--+ +--v--+ +--v---+  |    +--v--+ +--v---+
       |Searc| |Voice| |Moder |  |    |Prese| |      |
       |:3007| |:3008| |:3009 |  |    |:3010| |      |
       +--+--+ +--+--+ +--+---+  |    +--+--+ |      |
          |       |       |       |       |    |      |
   +------v-------v-------v-------v-------v----v------v---+
   |                    DATA LAYER                         |
   |                                                       |
   | +-------------+  +----------+  +----------+          |
   | | PostgreSQL  |  | Redis/   |  | ScyllaDB |          |
   | | 17 (Patroni |  | Valkey 8 |  | (msgs)   |          |
   | | + PgBouncer)|  | (Sentinel)|  |          |          |
   | +-------------+  +----------+  +----------+          |
   |                                                       |
   | +-------------+  +----------+                         |
   | | MinIO (S3)  |  | Meili-   |                         |
   | | файлы/медиа |  | search   |                         |
   | +-------------+  +----------+                         |
   +-------------------------------------------------------+
```

### 1.5. Критичность сервисов

| Tier | Сервисы | Последствия отказа | Min replicas |
|---|---|---|---|
| **Tier 1** | API Gateway, WS Gateway, Auth, PostgreSQL, Redis, NATS | Платформа полностью или частично недоступна | 2+ |
| **Tier 2** | Messages, Guilds, Users, Presence | Основной функционал недоступен | 2 |
| **Tier 3** | Media, Notifications, Search, Voice, Moderation | Деградация отдельных функций | 1-2 |

---

## 2. Стратегия бэкапов

### 2.1. Сводная таблица

| Хранилище | Метод бэкапа | Частота | Retention | RPO | Хранение |
|---|---|---|---|---|---|
| PostgreSQL 17 | `pg_basebackup` + WAL archiving (PITR) через wal-g | Daily full + continuous WAL | 7 дн full, 30 дн WAL | секунды | S3 (отдельный бакет MinIO) |
| Redis / Valkey 8 | AOF (`appendfsync everysec`) + RDB snapshots | Continuous + каждые 15 мин | 3 дня | ~1 сек | PersistentVolume + S3 |
| ScyllaDB | `nodetool snapshot` + incremental backup | Daily full + hourly incremental | 7 дней | 1 час | S3 (отдельный бакет) |
| MinIO | Erasure coding (встроенное) + cross-site replication | Continuous | indefinite | 0 (replicated) | Второй кластер MinIO |
| Meilisearch | Snapshots через API | Daily | 7 дней | 24 ч (re-indexable) | S3 |
| NATS JetStream | File-based storage + R=3 replication | Continuous | per stream config | 0 (replicated) | PersistentVolumes |
| K8s Secrets | `kubectl get secrets -o yaml` (encrypted) | При каждом изменении | 30 дней | - | Offsite encrypted storage |
| etcd (Patroni DCS) | `etcdctl snapshot save` | Daily | 7 дней | до 24 ч | Offsite storage |

### 2.2. PostgreSQL -- архитектура бэкапа

```
  +---------------------+          +---------------------+
  | PostgreSQL Primary  |   sync   | PostgreSQL Standby  |
  | (Patroni Leader)    |  ------> | (Patroni Replica)   |
  +----------+----------+          +---------------------+
             |
             | WAL archiving (wal-g)
             v
  +---------------------+          +---------------------+
  |   WAL Archive       |          |  pg_basebackup      |
  |   (S3 / MinIO)      |          |  (Daily Full, S3)   |
  |   непрерывно        |          |  CronJob 02:00 UTC  |
  +----------+----------+          +----------+----------+
             |                                |
             +----------------+---------------+
                              |
                              v
                   +---------------------+
                   | Point-in-Time       |
                   | Recovery (PITR)     |
                   | до любого момента   |
                   | в пределах retention|
                   +---------------------+
```

Конфигурация WAL archiving (`postgresql.conf`):
```
archive_mode = on
archive_command = 'wal-g wal-push %p'
archive_timeout = 60
wal_level = replica
max_wal_senders = 10
max_replication_slots = 10
```

Скрипт ежедневного бэкапа (CronJob):
```bash
#!/bin/bash
set -euo pipefail

BACKUP_DATE=$(date +%Y-%m-%d_%H-%M-%S)

# Полный бэкап через wal-g
wal-g backup-push /var/lib/postgresql/data

# Удаление старых бэкапов (retain 7 полных)
wal-g delete retain FULL 7 --confirm

# Верификация: список бэкапов
wal-g backup-list

# Уведомление об успехе
curl -sf "${ALERT_WEBHOOK}" \
    -d "{\"text\": \"PostgreSQL backup OK: ${BACKUP_DATE}\"}" \
    || true
```

### 2.3. Redis / Valkey 8 -- конфигурация persistence

```
# redis.conf / valkey.conf

# AOF -- основной механизм, потеря максимум 1 секунда
appendonly yes
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# RDB -- дополнительные снапшоты
save 900 1       # каждые 15 мин если 1+ изменение
save 300 10      # каждые 5 мин если 10+ изменений
save 60 10000    # каждую минуту если 10000+ изменений
```

### 2.4. ScyllaDB -- бэкап сообщений

```bash
#!/bin/bash
set -euo pipefail

SNAPSHOT_TAG="daily-$(date +%Y%m%d)"

# Ежедневный полный снапшот
nodetool snapshot -t "${SNAPSHOT_TAG}" messages_keyspace

# Копирование на S3
aws s3 sync \
    /var/lib/scylla/data/messages_keyspace/ \
    "s3://backups-scylladb/$(date +%Y-%m-%d)/" \
    --endpoint-url "${MINIO_ENDPOINT}"

# Очистка старых снапшотов (старше 7 дней)
nodetool clearsnapshot -t "daily-$(date -d '-7 days' +%Y%m%d)" messages_keyspace || true
```

### 2.5. Kubernetes CronJob для бэкапов

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: platform
spec:
  schedule: "0 2 * * *"    # Каждый день в 02:00 UTC
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      backoffLimit: 3
      activeDeadlineSeconds: 3600
      template:
        spec:
          containers:
          - name: backup
            image: registry.internal/backup-tools:latest
            command: ["/scripts/postgres-backup.sh"]
            envFrom:
            - secretRef:
                name: backup-credentials
            resources:
              requests:
                memory: "1Gi"
                cpu: "500m"
              limits:
                memory: "2Gi"
                cpu: "1"
          restartPolicy: OnFailure
---
apiVersion: batch/v1
kind: CronJob
metadata:
  name: scylladb-backup
  namespace: platform
spec:
  schedule: "0 3 * * *"    # Каждый день в 03:00 UTC
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      backoffLimit: 3
      template:
        spec:
          containers:
          - name: backup
            image: registry.internal/backup-tools:latest
            command: ["/scripts/scylladb-backup.sh"]
            envFrom:
            - secretRef:
                name: backup-credentials
          restartPolicy: OnFailure
---
apiVersion: batch/v1
kind: CronJob
metadata:
  name: meilisearch-snapshot
  namespace: platform
spec:
  schedule: "0 4 * * *"    # Каждый день в 04:00 UTC
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          containers:
          - name: snapshot
            image: curlimages/curl:latest
            command:
            - /bin/sh
            - -c
            - |
              curl -sf -X POST \
                "http://meilisearch.platform.svc:7700/snapshots" \
                -H "Authorization: Bearer ${MEILI_MASTER_KEY}" \
                && echo "Meilisearch snapshot: OK" \
                || (echo "Meilisearch snapshot: FAILED" && exit 1)
            envFrom:
            - secretRef:
                name: meilisearch-credentials
          restartPolicy: OnFailure
```

### 2.6. Правило 3-2-1

```
Правило хранения бэкапов:

  3 копии данных:
    +------------------+
    | 1. Production    |  <-- основные данные
    +------------------+
    +------------------+
    | 2. On-site S3    |  <-- бэкапы в MinIO (тот же ДЦ)
    |    (MinIO backup)|
    +------------------+
    +------------------+
    | 3. Off-site S3   |  <-- бэкапы во внешнем хранилище
    |    (другой ДЦ)   |     (другой регион/провайдер)
    +------------------+

  2 типа носителей:
    - Блочное хранилище (PersistentVolume в K8s)
    - Объектное хранилище (S3-совместимое)

  1 копия offsite:
    - Физически в другом дата-центре / регионе
```

Дополнительные правила:
- Бэкапы шифруются перед передачей (AES-256-GCM)
- Доступ к бэкапам через отдельный ServiceAccount с минимальными правами
- Retention-политики применяются автоматически
- Бэкапы тестируются восстановлением еженедельно (CronJob, раздел 5)

---

## 3. Сценарии отказов и Runbooks

### 3.1. PostgreSQL Primary Down

**Severity**: P1 (Critical)
**Влияние**: Auth (:3001), Users (:3002), Guilds (:3003), Moderation (:3009) -- операции записи
недоступны. Новые логины невозможны. Чтение через standby может продолжаться.
**Детекция**: Alertmanager `DatabaseDown`, `DatabaseConnectionExhausted`. Patroni health check
`/patroni` endpoint.

```
Таймлайн автоматического восстановления (Patroni):

  T+0 сек      Patroni обнаруживает отказ Primary
                (etcd TTL, loop_wait: 10, retry_timeout: 10)
  |
  T+5 сек      Patroni проверяет replication lag на standby
                (maximum_lag_on_failover: 1048576 = 1MB)
  |
  T+10-30 сек  Patroni promote standby -> новый Primary
                (synchronous_mode: true -> RPO = 0)
  |
  T+30-45 сек  PgBouncer обнаруживает смену лидера
                и переключает пул соединений
  |
  T+45-60 сек  Сервисы переподключаются через PgBouncer
                (SQLx acquire_timeout: 5s, автоматический retry)
```

**Автоматическое восстановление (Patroni + PgBouncer)**:

1. Patroni определяет недоступность primary через DCS (etcd)
2. Patroni проверяет что replication lag на standby < 1MB (`maximum_lag_on_failover`)
3. `synchronous_mode: true` гарантирует RPO = 0
4. Patroni promote standby в новый primary
5. PgBouncer переключает соединения (transparent для приложений)
6. SQLx pool в сервисах повторяет попытки подключения

**Ручные действия (если Patroni НЕ справился)**:

```bash
# 1. Проверить состояние кластера Patroni
kubectl exec -it patroni-0 -n platform -- patronictl list

# Ожидаемый вывод:
# +--------+----------+--------+---------+----+-----------+
# | Member | Host     | Role   | State   | TL | Lag in MB |
# +--------+----------+--------+---------+----+-----------+
# | node-0 | 10.0.0.1 | Leader | running |  3 |           |
# | node-1 | 10.0.0.2 | Replica| running |  3 |         0 |
# +--------+----------+--------+---------+----+-----------+

# 2. Если Leader отсутствует -- ручной failover
kubectl exec -it patroni-0 -n platform -- \
    patronictl failover --candidate patroni-1

# 3. Проверить PgBouncer
kubectl exec -it pgbouncer-0 -n platform -- \
    psql -p 6432 -U pgbouncer pgbouncer -c "SHOW SERVERS;"

# 4. Если PgBouncer не переключился -- перезагрузить
kubectl exec -it pgbouncer-0 -n platform -- \
    kill -HUP 1

# 5. Проверить replication на новом primary
kubectl exec -it patroni-0 -n platform -- \
    psql -U postgres -c "
    SELECT client_addr, state, sent_lsn, replay_lsn,
           pg_wal_lsn_diff(sent_lsn, replay_lsn) AS lag_bytes
    FROM pg_stat_replication;"

# 6. Верифицировать работоспособность сервисов
for svc in auth users guilds moderation; do
    echo -n "${svc}: "
    kubectl exec deploy/${svc}-service -n platform -- \
        curl -sf http://localhost:3000/health/ready \
        && echo "OK" || echo "FAIL"
done

# 7. Восстановить бывший primary как новую replica
# (после выяснения причины отказа)
kubectl exec -it patroni-0 -n platform -- \
    patronictl reinit <cluster-name> <old-primary-name>
```

**Верификация после восстановления**:

```bash
# Проверить что нет потерянных транзакций
kubectl exec -it patroni-0 -n platform -- \
    psql -U postgres -c "
    SELECT pg_current_wal_lsn(),
           pg_last_wal_receive_lsn(),
           pg_last_wal_replay_lsn();"

# Проверить consistency данных
kubectl exec -it patroni-0 -n platform -- \
    psql -U postgres -d app -c "
    SELECT 'users' AS tbl, count(*) FROM users
    UNION ALL SELECT 'guilds', count(*) FROM guilds
    UNION ALL SELECT 'channels', count(*) FROM channels
    UNION ALL SELECT 'roles', count(*) FROM roles;"
```

**Post-mortem задачи**:
- Восстановить упавший узел как replica (`patronictl reinit`)
- Проверить WAL archiving продолжает работать
- Проверить etcd (Patroni DCS) доступность
- Обновить документ с результатами

---

### 3.2. Redis Master Down

**Severity**: P1 (Critical)
**Влияние**: Сессии, presence, rate limiting, кеш прав -- деградация. Rate limiting
временно не работает. Пользователи могут быть разлогинены при истечении access token (15 мин).
**Детекция**: Alertmanager `RedisDown`, `RedisOOM`. Redis Sentinel monitoring.

```
Таймлайн автоматического восстановления (Sentinel):

  T+0 сек       Sentinel обнаруживает что master не отвечает
  |
  T+5-10 сек    Sentinel помечает master как SDOWN (subjectively down)
  |
  T+15-20 сек   Кворум Sentinel (2 из 3) согласует ODOWN (objectively down)
  |
  T+20-30 сек   Sentinel выбирает replica с наименьшим replication lag
                 и promote в новый master
  |
  T+30-45 сек   Клиенты переподключаются через Sentinel discovery
                 (deadpool-redis автоматический reconnect)
```

**Ручные действия (если Sentinel не справился)**:

```bash
# 1. Проверить статус Sentinel
kubectl exec -it redis-sentinel-0 -n platform -- \
    redis-cli -p 26379 SENTINEL masters

# 2. Проверить replicas
kubectl exec -it redis-sentinel-0 -n platform -- \
    redis-cli -p 26379 SENTINEL replicas mymaster

# 3. Принудительный failover
kubectl exec -it redis-sentinel-0 -n platform -- \
    redis-cli -p 26379 SENTINEL failover mymaster

# 4. Если Sentinel тоже недоступен -- ручной promote
kubectl exec -it redis-replica-0 -n platform -- \
    redis-cli REPLICAOF NO ONE

# 5. Верификация
kubectl exec -it redis-sentinel-0 -n platform -- \
    redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

**Поведение сервисов при отказе Redis (graceful degradation)**:

Все наши Rust-сервисы обрабатывают недоступность Redis через fallback на PostgreSQL:

```rust
use crate::errors::AppError;
use crate::permissions::Permission;

/// Получение прав пользователя с graceful fallback.
///
/// При недоступности Redis запрос прозрачно переходит
/// к PostgreSQL (source of truth). Повышенная latency,
/// но без ошибок для пользователя.
pub async fn get_user_permissions(
    cache: &RedisPool,
    db: &PgPool,
    user_id: i64,
    guild_id: i64,
) -> Result<Permission, AppError> {
    // Пробуем кеш Redis
    match cache.get_permissions(user_id, guild_id).await {
        Ok(Some(perms)) => return Ok(perms),
        Ok(None) => { /* кеш пуст, идем в БД */ }
        Err(e) => {
            tracing::warn!(
                error = %e,
                user_id,
                guild_id,
                "Redis недоступен, fallback на PostgreSQL"
            );
        }
    }

    // Fallback: читаем из PostgreSQL
    let perms = sqlx::query_scalar!(
        "SELECT r.permissions
         FROM member_roles mr
         JOIN roles r ON r.id = mr.role_id
         WHERE mr.user_id = $1 AND mr.guild_id = $2",
        user_id,
        guild_id
    )
    .fetch_all(db)
    .await?;

    let combined = perms
        .iter()
        .fold(0i64, |acc, p| acc | p.unwrap_or(0));

    let result = Permission::from_bits_truncate(combined as u64);

    // Попытка записать в кеш (если Redis снова доступен)
    if let Err(e) = cache.set_permissions(user_id, guild_id, &result).await {
        tracing::debug!(error = %e, "Не удалось записать в кеш Redis");
    }

    Ok(result)
}
```

**Последствия потери данных Redis**:

```
+---------------------+---------------------+--------------------------------+
| Тип данных          | Source of Truth      | Способ восстановления          |
+---------------------+---------------------+--------------------------------+
| Сессии              | PostgreSQL           | Перелогин при истечении         |
|                     | (refresh_tokens)     | access token (15 мин)          |
+---------------------+---------------------+--------------------------------+
| Кеш прав            | PostgreSQL           | Автоматически при запросе      |
|                     | (member_roles+roles) | (cache-aside pattern)          |
+---------------------+---------------------+--------------------------------+
| Presence            | WS Gateway           | Heartbeat rebuild (~30 сек)    |
|                     | (live connections)   |                                |
+---------------------+---------------------+--------------------------------+
| Rate limits         | Вычисляемые          | Сброс, восстановятся сами     |
|                     |                     | Временно: Caddy rate limit     |
+---------------------+---------------------+--------------------------------+
| Typing indicators   | Эфемерные            | TTL 10 сек, не критично        |
+---------------------+---------------------+--------------------------------+
| JWT denylist        | AOF + PostgreSQL     | При потере: ротация JWT ключей |
+---------------------+---------------------+--------------------------------+
```

---

### 3.3. NATS Cluster Partition

**Severity**: P1 (Critical)
**Влияние**: Межсервисная коммуникация прервана. API Gateway (:3000) не может маршрутизировать
запросы через NATS request/reply (503). WebSocket Gateway (:4000) не получает события для
доставки клиентам. Notification Service и Search Service не получают события.
**Детекция**: Alertmanager `NATSDisconnected`. Monitoring endpoint `:8222/healthz`.

```
Нормальная работа:               Network Partition:

  +------+  +------+              +------+     +------+
  |NATS-0|--|NATS-1|              |NATS-0|  X  |NATS-1|
  +--+---+  +--+---+              +--+---+     +--+---+
     |          |                     |     SPLIT   |
     +----+-----+                     |             |
          |                        +--+---+      +--+---+
       +--+---+                    |NATS-2|      | ???  |
       |NATS-2|                    +------+      +------+
       +------+
                                   Кворум (2/3)  Нет кворума
                                   Работает      Только чтение
```

**Поведение JetStream при partition**:
- JetStream использует Raft consensus (R=3 по умолчанию)
- При потере кворума (2 из 3 узлов) -- запись невозможна
- Сообщения в стримах сохраняются
- Consumer ACK отслеживает позицию -- при восстановлении replay автоматически
- Core NATS (fire-and-forget) -- потеря in-flight сообщений

**Диагностика и восстановление**:

```bash
# 1. Проверить статус NATS кластера
kubectl exec -it nats-0 -n platform -- nats server report jetstream

# 2. Проверить подключения
kubectl exec -it nats-0 -n platform -- nats server report connections

# 3. Проверить стримы JetStream
kubectl exec -it nats-0 -n platform -- nats stream ls

# 4. Проверить consumer lag (есть ли неподтвержденные сообщения)
kubectl exec -it nats-0 -n platform -- nats consumer report

# 5. Проверить что все NATS ноды видят друг друга
for i in 0 1 2; do
    echo -n "nats-${i}: "
    kubectl exec -it nats-${i} -n platform -- \
        nats server ping 2>/dev/null && echo "OK" || echo "UNREACHABLE"
done

# 6. Если partition вызвана NetworkPolicy / firewall
kubectl get networkpolicy -n platform
kubectl describe networkpolicy -n platform

# 7. После восстановления -- проверить что consumers догнали
kubectl exec -it nats-0 -n platform -- nats consumer report
```

**Конфигурация NATS для отказоустойчивости**:

```
# nats.conf
jetstream {
  store_dir: "/data/jetstream"
  max_mem: 1Gi
  max_file: 10Gi
}

cluster {
  name: platform-nats
  routes: [
    nats-route://nats-0.nats.platform.svc:6222
    nats-route://nats-1.nats.platform.svc:6222
    nats-route://nats-2.nats.platform.svc:6222
  ]
}
```

---

### 3.4. Kubernetes Node Failure

**Severity**: P2 (зависит от подов на узле)
**Влияние**: Поды на упавшем узле недоступны до rescheduling. При правильной настройке PDB
и anti-affinity другие реплики продолжают обслуживать трафик.
**Детекция**: Kubernetes node controller, `kube_node_status_condition`, Prometheus `up` metric.

```
Таймлайн восстановления:

  T+0 сек        Node перестает отвечать
  |
  T+40 сек       kubelet пропускает heartbeat
  |
  T+5 мин        Node Controller помечает узел NotReady
                  (--node-monitor-grace-period=40s)
  |
  T+5-10 мин     Pod Eviction Controller начинает эвикцию подов
                  (--pod-eviction-timeout=5m)
  |
  T+5-12 мин     Поды рескедулятся на другие узлы
  |
  T+7-15 мин     Поды стартуют и проходят readiness probe
```

**Ускорение восстановления (ручные действия)**:

```bash
# 1. Проверить статус узлов
kubectl get nodes -o wide

# 2. Определить какие поды были на упавшем узле
kubectl get pods -A --field-selector spec.nodeName=<failed-node>

# 3. Принудительно удалить поды (ускорить rescheduling)
kubectl delete pods -A \
    --field-selector spec.nodeName=<failed-node> \
    --grace-period=0 --force

# 4. Если узел не вернется -- drain и remove
kubectl drain <failed-node> \
    --ignore-daemonsets \
    --delete-emptydir-data \
    --force \
    --timeout=120s

kubectl delete node <failed-node>

# 5. Проверить что все поды перешедулились
kubectl get pods -n platform | grep -v Running | grep -v Completed
```

**Важно**: Pod Disruption Budgets (PDB) гарантируют что при voluntary disruption
(drain, rolling update) минимальное количество подов остается доступным.
Однако node failure -- involuntary disruption, PDB не применяется.
Защита -- pod anti-affinity по зонам (раздел 8.1).

---

### 3.5. Full Service Outage (неудачный деплой)

**Severity**: P1 (Critical)
**Влияние**: Один или несколько сервисов работают некорректно после обновления.
**Детекция**: Alertmanager `HighErrorRate` (5xx > 5% за 5 мин), `PodRestartLoop`
(restarts > 5 за 15 мин), `ServiceDown` (replicas = 0).

**Немедленные действия -- Helm Rollback**:

```bash
# 1. Определить проблемный релиз
helm history platform -n platform

# Пример вывода:
# REVISION  STATUS      DESCRIPTION
# 14        superseded  Upgrade complete
# 15        deployed    Upgrade complete   <-- текущий (проблемный)

# 2. Откатить к предыдущей ревизии
helm rollback platform 14 -n platform

# 3. Проверить статус отката
helm status platform -n platform

# 4. Дождаться завершения rollout
kubectl rollout status deployment -n platform --timeout=120s

# 5. Если Helm rollback не помогает -- откатить конкретный deployment
kubectl rollout undo deployment/messages-service -n platform

# 6. Верификация
for svc in api gateway auth users guilds messages media notifications search voice moderation presence; do
    echo -n "${svc}: "
    kubectl exec deploy/${svc}-service -n platform -- \
        curl -sf http://localhost:3000/health/ready 2>/dev/null \
        && echo "OK" || echo "FAIL"
done
```

**Canary Deployment (предотвращение полного outage)**:

```
Canary стратегия развертывания:

  Шаг 1: 10% трафика на canary
  +---------+  +---------+  +---------+
  | Pod v2  |  | Pod v1  |  | Pod v1  |
  | (canary)|  | (stable)|  | (stable)|
  | 10%     |  | 45%     |  | 45%     |
  +---------+  +---------+  +---------+

  --> Анализ метрик 5 мин: error rate, latency

  Шаг 2: 50% трафика (если метрики OK)
  +---------+  +---------+  +---------+
  | Pod v2  |  | Pod v2  |  | Pod v1  |
  | 33%     |  | 33%     |  | 33%     |
  +---------+  +---------+  +---------+

  --> Анализ метрик 5 мин

  Шаг 3: 100% (если метрики OK)
  +---------+  +---------+  +---------+
  | Pod v2  |  | Pod v2  |  | Pod v2  |
  | 33%     |  | 33%     |  | 33%     |
  +---------+  +---------+  +---------+

  Auto-rollback: если error rate > 5% на любом шаге
```

**Feature flags (безопасное включение фич без деплоя)**:

```rust
use crate::errors::AppError;

/// Проверка feature flag через Redis.
/// Позволяет включать/выключать фичи без деплоя.
pub async fn is_feature_enabled(
    cache: &RedisPool,
    feature: &str,
) -> Result<bool, AppError> {
    let key = format!("feature_flag:{feature}");
    let result: Option<String> = redis::cmd("GET")
        .arg(&key)
        .query_async(&mut cache.get().await?)
        .await?;

    Ok(result.as_deref() == Some("1"))
}

// Пример использования в хендлере
pub async fn create_thread(
    State(state): State<AppState>,
    Json(input): Json<CreateThreadInput>,
) -> Result<Json<ThreadResponse>, AppError> {
    if !is_feature_enabled(&state.cache, "threads_enabled").await? {
        return Err(AppError::NotImplemented(
            "Threads functionality is not enabled".into(),
        ));
    }
    // ... бизнес-логика
    todo!()
}
```

```bash
# Включить feature flag
kubectl exec -it redis-master-0 -n platform -- \
    redis-cli SET "feature_flag:threads_enabled" "1"

# Выключить feature flag (мгновенный rollback функционала)
kubectl exec -it redis-master-0 -n platform -- \
    redis-cli SET "feature_flag:threads_enabled" "0"
```

---

### 3.6. Data Corruption

**Severity**: P1 (Critical)
**Влияние**: Некорректные данные в хранилищах. Возможна потеря данных при неправильном
восстановлении.

#### 3.6.1. PostgreSQL -- PITR к конкретному моменту

```bash
# 1. НЕМЕДЛЕННО: остановить запись (scale down сервисов)
kubectl scale deployment --replicas=0 -l tier=app -n platform

# 2. Определить target time (момент ДО коррупции)
# Из логов Alertmanager, аудит-лога pgAudit, или бизнес-логики
TARGET_TIME="2026-02-22 14:30:00+00"

# 3. Скачать последний base backup через wal-g
wal-g backup-fetch /var/lib/postgresql/data-restore LATEST

# 4. Настроить параметры восстановления
cat >> /var/lib/postgresql/data-restore/postgresql.auto.conf << CONF
restore_command = 'wal-g wal-fetch %f %p'
recovery_target_time = '${TARGET_TIME}'
recovery_target_action = 'promote'
CONF

# Создать signal file
touch /var/lib/postgresql/data-restore/recovery.signal

# 5. Запустить PostgreSQL в recovery mode
pg_ctl -D /var/lib/postgresql/data-restore start

# 6. PostgreSQL воспроизведет WAL до target time и промоутится

# 7. Проверить данные
psql -U postgres -d app -c "
SELECT 'users' AS tbl, count(*) FROM users
UNION ALL SELECT 'guilds', count(*) FROM guilds
UNION ALL SELECT 'channels', count(*) FROM channels;"

# 8. Если данные корректны -- переключить на восстановленный инстанс
# 9. Scale up сервисов
kubectl scale deployment --replicas=2 -l tier=app -n platform
```

#### 3.6.2. ScyllaDB -- восстановление snapshot

```bash
# 1. Остановить Message Service
kubectl scale deployment messages-service --replicas=0 -n platform

# 2. На каждом узле ScyllaDB:
nodetool disablegossip
nodetool disablebinary

# 3. Восстановить snapshot
SNAPSHOT_TAG="daily-20260222"
cp -r /var/lib/scylla/data/messages_keyspace/messages-*/snapshots/${SNAPSHOT_TAG}/* \
      /var/lib/scylla/data/messages_keyspace/messages-*/

# 4. Загрузить данные
nodetool refresh messages_keyspace messages
nodetool enablebinary
nodetool enablegossip

# 5. Верификация
cqlsh -e "SELECT count(*) FROM messages_keyspace.messages LIMIT 1;"

# 6. Запустить Message Service
kubectl scale deployment messages-service --replicas=2 -n platform
```

#### 3.6.3. Redis -- flush и rebuild

Redis **НЕ является source of truth**. При коррупции -- полная очистка и rebuild:

```bash
# 1. Очистить все данные
kubectl exec -it redis-master-0 -n platform -- redis-cli FLUSHALL

# 2. Восстановление автоматическое:
#    - Кеш прав: rebuild при первом запросе из PostgreSQL (cache-aside)
#    - Presence: rebuild через heartbeat Gateway (~30 сек)
#    - Rate limits: сброс, восстановятся при следующем запросе
#    - Сессии: пользователи перелогинятся (refresh token в PostgreSQL)
```

---

### 3.7. Security Incident (утечка / взлом)

**Severity**: P0 (Emergency)
**Влияние**: Потенциальная утечка данных пользователей, компрометация системы.
**Ответственные**: On-call SRE + Security Team + CTO.

```
Порядок реагирования на security incident:

  +----------+     +----------+     +----------+     +----------+
  | 1. Обнар.|---->| 2. Сдерж.|---->| 3. Ротац.|---->| 4. Аудит |
  |  ужение  |     |  ивание  |     |  ия      |     |          |
  +----------+     +----------+     +----------+     +----------+
       |                |                |                |
   Alerting        Изоляция        JWT ключи        Логи pgAudit
   On-call SRE     подов           DB credentials   NATS логи
   Security team   NetworkPolicy   NATS creds       API access logs
                                   MinIO keys
```

**Шаг 1: Containment (сдерживание) -- первые 5 минут**:

```bash
# Изолировать скомпрометированные поды через NetworkPolicy
kubectl apply -f - << 'POLICY'
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: emergency-isolate
  namespace: platform
spec:
  podSelector:
    matchLabels:
      compromised: "true"
  policyTypes:
  - Ingress
  - Egress
POLICY

# При необходимости -- блокировка внешнего доступа
kubectl patch svc caddy-ingress -n platform \
    -p '{"spec":{"type":"ClusterIP"}}'
```

**Шаг 2: Ротация JWT ключей (ES256) -- инвалидация ВСЕХ access tokens**:

```bash
# 1. Сгенерировать новую пару ключей ES256
openssl ecparam -genkey -name prime256v1 -out jwt-private-new.pem
openssl ec -in jwt-private-new.pem -pubout -out jwt-public-new.pem

# 2. Обновить Kubernetes Secret
kubectl create secret generic jwt-keys \
    --from-file=private.pem=jwt-private-new.pem \
    --from-file=public.pem=jwt-public-new.pem \
    -n platform \
    --dry-run=client -o yaml | kubectl apply -f -

# 3. Перезапустить все сервисы, проверяющие JWT
kubectl rollout restart deployment/auth-service -n platform
kubectl rollout restart deployment/api-gateway -n platform
kubectl rollout restart deployment/ws-gateway -n platform

# РЕЗУЛЬТАТ: ВСЕ access tokens немедленно невалидны.
# Пользователи перелогинятся через refresh token.
```

**Шаг 3: Инвалидация ВСЕХ сессий**:

```bash
# Очистить все refresh tokens
kubectl exec -it patroni-0 -n platform -- \
    psql -U app -d app -c "DELETE FROM refresh_tokens;"

# Очистить все сессии в Redis
kubectl exec -it redis-master-0 -n platform -- \
    redis-cli KEYS "session:*" | xargs -L 100 redis-cli DEL
```

**Шаг 4: Ротация credentials баз данных**:

```bash
# 1. PostgreSQL -- новый пароль application user
kubectl exec -it patroni-0 -n platform -- \
    psql -U postgres -c "ALTER USER app PASSWORD 'NEW_SECURE_GENERATED_PASSWORD';"

# 2. Обновить Secret
kubectl create secret generic db-credentials \
    --from-literal=password='NEW_SECURE_GENERATED_PASSWORD' \
    -n platform \
    --dry-run=client -o yaml | kubectl apply -f -

# 3. Перезапустить все сервисы
kubectl rollout restart deployment -l tier=app -n platform
```

**Шаг 5: Ротация NATS credentials**:

```bash
# 1. Сгенерировать новые NATS credentials
nsc generate creds -a platform -n service-account > new-creds.creds

# 2. Обновить Secret
kubectl create secret generic nats-credentials \
    --from-file=creds=new-creds.creds \
    -n platform \
    --dry-run=client -o yaml | kubectl apply -f -

# 3. Перезапустить NATS и сервисы
kubectl rollout restart statefulset/nats -n platform
kubectl rollout restart deployment -l tier=app -n platform
```

**Шаг 6: Аудит**:

```bash
# Проверить аудит-лог PostgreSQL (pgAudit)
kubectl exec -it patroni-0 -n platform -- \
    psql -U postgres -c "
    SELECT event_time, user_name, command_tag, object_name
    FROM pgaudit.log_entries
    WHERE event_time > NOW() - INTERVAL '24 hours'
    ORDER BY event_time DESC
    LIMIT 100;"

# Экспортировать логи доступа к API для расследования
kubectl logs -l app=api-gateway -n platform --since=48h \
    > /tmp/incident-api-logs-$(date +%Y%m%d).txt

# Экспортировать логи всех сервисов
kubectl logs -l tier=app -n platform --since=48h \
    > /tmp/incident-all-logs-$(date +%Y%m%d).txt

# Проверить аномальную активность в Redis
kubectl exec -it redis-master-0 -n platform -- \
    redis-cli SLOWLOG GET 100
```

**Шаг 7: Коммуникация**:
- Уведомить руководство и юридический отдел
- При утечке PII -- уведомить затронутых пользователей (GDPR requirement)
- Обновить status page
- Подготовить post-mortem в течение 48 часов

---

## 4. Процедуры восстановления

### 4.1. PostgreSQL PITR -- полная пошаговая процедура

```
Порядок восстановления PostgreSQL:

  1. Определить target time (до инцидента)
  |
  2. Остановить запись (scale down)
  |
  3. Скачать последний base backup (wal-g backup-fetch)
  |
  4. Настроить recovery.signal + restore_command
  |
  5. Запустить PostgreSQL в recovery mode
  |
  6. PG воспроизводит WAL до target time
  |
  7. PG промоутится в primary (recovery_target_action = promote)
  |
  8. Верификация данных
  |
  9. Переключить Patroni / PgBouncer на восстановленный инстанс
  |
  10. Scale up сервисов
  |
  11. Пересоздать standby replica
```

```bash
# --- Подготовка ---
TARGET="2026-02-22T14:30:00Z"

# 1. Остановить сервисы
kubectl scale deployment --replicas=0 -l tier=app -n platform

# --- Восстановление ---
# 2. Создать pod для восстановления
kubectl run pg-restore \
    --image=registry.internal/backup-tools:latest \
    --restart=Never \
    -n platform \
    -- sleep infinity

# 3. Скачать base backup
kubectl exec -it pg-restore -n platform -- \
    wal-g backup-fetch /restore/pgdata LATEST

# 4. Настроить восстановление
kubectl exec -it pg-restore -n platform -- bash -c "
cat >> /restore/pgdata/postgresql.auto.conf << CONF
restore_command = 'wal-g wal-fetch %f %p'
recovery_target_time = '${TARGET}'
recovery_target_action = 'promote'
CONF
touch /restore/pgdata/recovery.signal
"

# 5. Запустить и дождаться восстановления
kubectl exec -it pg-restore -n platform -- \
    pg_ctl -D /restore/pgdata -l /restore/recovery.log start -w

# 6. Мониторить процесс
kubectl exec -it pg-restore -n platform -- \
    tail -f /restore/recovery.log
# Ожидать: "redo done at ..." -> "database system is ready to accept connections"

# --- Верификация ---
# 7. Проверить целостность данных
kubectl exec -it pg-restore -n platform -- \
    psql -U postgres -d app -c "
    SELECT 'users' AS tbl, count(*) FROM users
    UNION ALL SELECT 'guilds', count(*) FROM guilds
    UNION ALL SELECT 'channels', count(*) FROM channels
    UNION ALL SELECT 'roles', count(*) FROM roles;"

# --- Переключение ---
# 8. Переинициализировать Patroni кластер с восстановленными данными
# (процедура зависит от конфигурации Patroni)

# 9. Scale up сервисов
kubectl scale deployment --replicas=2 -l tier=app -n platform

# 10. Очистить
kubectl delete pod pg-restore -n platform
```

### 4.2. Redis -- варианты восстановления

**Вариант A: Восстановление из AOF (предпочтительно)**:

```bash
# 1. Остановить Redis
kubectl exec -it redis-master-0 -n platform -- redis-cli SHUTDOWN NOSAVE

# 2. Скопировать бэкап AOF
kubectl cp backups/appendonly.aof \
    platform/redis-master-0:/data/appendonly.aof

# 3. Проверить целостность AOF
kubectl exec -it redis-master-0 -n platform -- \
    redis-check-aof --fix /data/appendonly.aof

# 4. Перезапустить (StatefulSet пересоздаст pod, Redis загрузит AOF)
kubectl delete pod redis-master-0 -n platform
```

**Вариант B: Восстановление из RDB snapshot**:

```bash
# 1. Остановить Redis
kubectl exec -it redis-master-0 -n platform -- redis-cli SHUTDOWN NOSAVE

# 2. Скопировать RDB
kubectl cp backups/dump.rdb platform/redis-master-0:/data/dump.rdb

# 3. Удалить AOF (чтобы загрузился RDB)
kubectl exec -it redis-master-0 -n platform -- rm -f /data/appendonly.aof

# 4. Перезапустить
kubectl delete pod redis-master-0 -n platform
```

**Вариант C: Полный rebuild из source of truth (PostgreSQL)**:

```bash
# Просто очищаем Redis -- данные восстановятся автоматически:
kubectl exec -it redis-master-0 -n platform -- redis-cli FLUSHALL

# Кеш: cache-aside паттерн пополнит при запросах
# Сессии: пользователи перелогинятся
# Presence: rebuild через WS Gateway heartbeat (30 сек)
# Rate limits: пересоздадутся при запросах
```

### 4.3. Meilisearch -- переиндексация из source

Meilisearch не является source of truth. Все данные доступны в PostgreSQL и ScyllaDB.

**Восстановление из snapshot**:

```bash
# 1. Скопировать snapshot
kubectl cp backups/meilisearch/latest.snapshot \
    platform/meilisearch-0:/meili_data/snapshots/

# 2. Перезапустить с импортом snapshot
kubectl delete pod meilisearch-0 -n platform
# Pod запустится с флагом --import-snapshot (если настроено в spec)
```

**Полная переиндексация (если snapshot недоступен)**:

```bash
# 1. Удалить поврежденные индексы
curl -X DELETE "http://meilisearch.platform.svc:7700/indexes/messages" \
    -H "Authorization: Bearer ${MEILI_MASTER_KEY}"

curl -X DELETE "http://meilisearch.platform.svc:7700/indexes/guilds" \
    -H "Authorization: Bearer ${MEILI_MASTER_KEY}"

curl -X DELETE "http://meilisearch.platform.svc:7700/indexes/users" \
    -H "Authorization: Bearer ${MEILI_MASTER_KEY}"

# 2. Создать индексы заново
for idx in messages guilds users; do
    curl -X POST "http://meilisearch.platform.svc:7700/indexes" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${MEILI_MASTER_KEY}" \
        -d "{\"uid\": \"${idx}\", \"primaryKey\": \"id\"}"
done

# 3. Запустить batch-переиндексацию через Search Service
kubectl exec deploy/search-service -n platform -- \
    curl -X POST http://localhost:3007/admin/reindex \
    -H "Content-Type: application/json" \
    -d '{"target": "all"}'
```

Пример кода переиндексации в Search Service:

```rust
use crate::errors::AppError;

/// Batch-переиндексация сообщений из ScyllaDB в Meilisearch.
/// Читает пакетами по 1000 записей, чтобы не перегрузить память.
pub async fn reindex_messages(
    db: &PgPool,
    meili: &MeilisearchClient,
) -> Result<u64, AppError> {
    let mut total_indexed: u64 = 0;
    let batch_size: i64 = 1000;
    let mut last_id: i64 = 0;

    loop {
        let messages = sqlx::query_as!(
            SearchableMessage,
            "SELECT id, channel_id, author_id, content, created_at
             FROM messages
             WHERE id > $1
             ORDER BY id ASC
             LIMIT $2",
            last_id,
            batch_size
        )
        .fetch_all(db)
        .await?;

        if messages.is_empty() {
            break;
        }

        last_id = messages
            .last()
            .map(|m| m.id)
            .unwrap_or(last_id);

        let count = messages.len() as u64;

        meili
            .index("messages")
            .add_documents(&messages, Some("id"))
            .await
            .map_err(|e| AppError::Internal(e.to_string()))?;

        total_indexed += count;

        tracing::info!(
            batch_count = count,
            total = total_indexed,
            last_id,
            "Переиндексирована batch сообщений"
        );
    }

    tracing::info!(total = total_indexed, "Переиндексация завершена");
    Ok(total_indexed)
}
```

### 4.4. Emergency Rollback (Helm)

```bash
# 1. Определить текущую и предыдущую ревизии
helm history platform -n platform

# 2. Откатить
helm rollback platform <previous-revision> -n platform

# 3. Дождаться rollout
kubectl rollout status deployment -n platform --timeout=120s

# 4. Если проблема в миграции БД (не только код)
# 4.1. Включить maintenance mode
kubectl annotate ingress platform-ingress -n platform maintenance-mode="true"

# 4.2. Откатить миграцию
kubectl exec -it patroni-0 -n platform -- \
    sqlx migrate revert --database-url "${DATABASE_URL}"

# 4.3. Откатить Helm release
helm rollback platform <previous-revision> -n platform

# 4.4. Выключить maintenance mode
kubectl annotate ingress platform-ingress -n platform maintenance-mode-

# 5. Верификация
kubectl get pods -n platform
```

---

## 5. Тестирование DR

### 5.1. Расписание тестирования

| Тест | Частота | Ответственный | Метод |
|---|---|---|---|
| PostgreSQL backup restore | Еженедельно (авто) | CronJob | Restore + SELECT count |
| PostgreSQL PITR | Ежемесячно | SRE | Ручной тест в staging |
| PostgreSQL Patroni failover | Ежемесячно | SRE | Kill primary pod |
| Redis Sentinel failover | Ежемесячно | SRE | Kill master pod |
| NATS partition recovery | Ежеквартально | SRE | NetworkPolicy isolation |
| Full DR drill | Ежеквартально | Вся команда | Полное восстановление |
| Security incident drill | Раз в полгода | Security + SRE | Credential rotation |
| Helm rollback | При каждом деплое | CI/CD | Canary auto-rollback |
| Pod kill (chaos) | Еженедельно (авто) | CronJob | Random pod deletion |

### 5.2. Автоматический тест восстановления PostgreSQL (CronJob)

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup-verify
  namespace: platform
spec:
  schedule: "0 4 * * 0"    # Каждое воскресенье 04:00 UTC
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      backoffLimit: 1
      activeDeadlineSeconds: 3600
      template:
        spec:
          containers:
          - name: verify
            image: registry.internal/backup-tools:latest
            command:
            - /bin/bash
            - -c
            - |
              set -euo pipefail

              echo "=== Скачиваем последний бэкап ==="
              wal-g backup-fetch /tmp/restore LATEST

              echo "=== Настраиваем recovery ==="
              cat >> /tmp/restore/postgresql.auto.conf << 'CONF'
              restore_command = 'wal-g wal-fetch %f %p'
              recovery_target_action = 'promote'
              CONF
              touch /tmp/restore/recovery.signal

              echo "=== Запускаем PostgreSQL ==="
              pg_ctl -D /tmp/restore -l /tmp/pg.log start -w -o "-p 15432"

              echo "=== Проверяем данные ==="
              USERS=$(psql -p 15432 -U postgres -d app -tAc "SELECT count(*) FROM users;")
              GUILDS=$(psql -p 15432 -U postgres -d app -tAc "SELECT count(*) FROM guilds;")
              CHANNELS=$(psql -p 15432 -U postgres -d app -tAc "SELECT count(*) FROM channels;")

              echo "Users: ${USERS}, Guilds: ${GUILDS}, Channels: ${CHANNELS}"

              if [ "${USERS}" -eq 0 ]; then
                echo "ОШИБКА: таблица users пуста!"
                curl -sf "${ALERT_WEBHOOK}" \
                    -d '{"text": "BACKUP VERIFY FAILED: users table is empty!"}' || true
                exit 1
              fi

              echo "=== Бэкап верифицирован ==="
              pg_ctl -D /tmp/restore stop -m fast

              curl -sf "${ALERT_WEBHOOK}" \
                  -d "{\"text\": \"Backup verify OK: users=${USERS}, guilds=${GUILDS}\"}" || true

            envFrom:
            - secretRef:
                name: backup-credentials
            resources:
              requests:
                memory: "2Gi"
                cpu: "1"
              limits:
                memory: "4Gi"
                cpu: "2"
          restartPolicy: Never
```

### 5.3. Ежемесячный Failover Drill

Проводится в период минимальной нагрузки (02:00-04:00 UTC).

| Шаг | Действие | Ожидаемый результат | Критерий успеха |
|---|---|---|---|
| 1 | Kill PostgreSQL primary pod | Patroni promote standby | Failover < 30 сек, RPO = 0 |
| 2 | Kill Redis master pod | Sentinel promote replica | Failover < 30 сек |
| 3 | Kill 1 из 3 NATS нод | Кластер продолжает работать | Нет потери сообщений |
| 4 | Kill WS Gateway pod (1 из N) | Клиенты reconnect с RESUME | Reconnect < 10 сек |
| 5 | Restore PG из pg_basebackup | Данные целостны | RTO < 30 мин |
| 6 | Restore Redis из RDB | Данные восстановлены | RTO < 5 мин |
| 7 | Meilisearch переиндексация | Поиск работает | RTO < 30 мин |
| 8 | `helm rollback` | Все сервисы работают | Rollback < 2 мин |
| 9 | Проверить алерты | Уведомления доставлены | Все critical-алерты пришли |

### 5.4. Чеклист DR Drill

```
Подготовка:
  [ ] Уведомить команду о проведении drill
  [ ] Подготовить staging-окружение (изолированное от production)
  [ ] Убедиться что бэкапы доступны и актуальны
  [ ] Назначить ответственных за каждый этап
  [ ] Подготовить каналы коммуникации

PostgreSQL:
  [ ] Восстановить из последнего base backup
  [ ] Проверить PITR к конкретному timestamp
  [ ] Проверить Patroni failover
  [ ] Замерить фактический RTO
  [ ] Проверить данные после восстановления (RPO)

Redis:
  [ ] Sentinel failover
  [ ] Восстановление из AOF
  [ ] Восстановление из RDB
  [ ] Проверить reconnect сервисов

ScyllaDB:
  [ ] Восстановить из snapshot
  [ ] Верифицировать данные сообщений

NATS:
  [ ] Имитировать partition (NetworkPolicy)
  [ ] Проверить JetStream replay
  [ ] Проверить consumer позиции после восстановления

Приложения:
  [ ] Helm rollback
  [ ] Canary с auto-rollback
  [ ] Feature flag toggle on/off

Результаты:
  [ ] Записать фактические RTO/RPO
  [ ] Документировать обнаруженные проблемы
  [ ] Создать задачи на улучшение
  [ ] Обновить runbooks по результатам drill
```

### 5.5. Chaos Engineering (еженедельно, автоматически)

```bash
#!/bin/bash
# chaos-test.sh -- запускается CronJob еженедельно
set -euo pipefail

echo "=== Chaos Test: Random Pod Kill ==="

# Выбрать случайный pod из некритичных сервисов
SERVICES=("users-service" "guilds-service" "messages-service" "media-service")
RANDOM_SVC=${SERVICES[$RANDOM % ${#SERVICES[@]}]}

echo "Target: ${RANDOM_SVC}"

# Удалить случайный pod
POD=$(kubectl get pods -n platform -l app=${RANDOM_SVC} -o name | head -1)
kubectl delete ${POD} -n platform --grace-period=0 --force

# Ждать восстановления
sleep 30

# Проверить что pod восстановился
READY=$(kubectl get pods -n platform -l app=${RANDOM_SVC} \
    -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}')

if [ "${READY}" = "True" ]; then
    echo "OK: ${RANDOM_SVC} восстановился"
else
    echo "FAIL: ${RANDOM_SVC} не восстановился за 30 сек"
    # Отправить алерт
    curl -sf "${ALERT_WEBHOOK}" \
        -d "{\"text\": \"Chaos test FAILED: ${RANDOM_SVC} not recovered\"}" || true
    exit 1
fi
```

---

## 6. Мониторинг и алерты

### 6.1. Стек мониторинга

```
                            +-------------------+
                            |    Grafana         |
                            | (Dashboards, Viz)  |
                            +----+-----+----+----+
                                 |     |    |
                    +------------+     |    +-----------+
                    |                  |                 |
            +-------v--------+ +------v-------+  +-----v--------+
            |  Prometheus    | | Grafana Loki |  |   Tempo /    |
            |  (Метрики)     | | (Логи)       |  |   Jaeger     |
            +-------+--------+ +------+-------+  |   (Трейсы)   |
                    |                  |          +-----+--------+
                    |                  |                 |
            +-------v--------+ +------v-------+  +-----v--------+
            | Alertmanager   | | Promtail     |  | OpenTelemetry|
            | (Алерты)       | | (Collector)  |  | Collector    |
            +-------+--------+ +--------------+  +--------------+
                    |
           +--------+--------+
           |                 |
    +------v------+   +-----v------+
    |  PagerDuty  |   |  Telegram  |
    |  (P0-P1)    |   |  (P2-P4)  |
    +-------------+   +------------+
```

### 6.2. Полная таблица алертов

| Алерт | Условие | Severity | Канал | Runbook |
|---|---|---|---|---|
| `ServiceDown` | replicas = 0 дольше 1 мин | critical | PagerDuty | 3.4 / 3.5 |
| `HighErrorRate` | 5xx > 5% за 5 мин | critical | PagerDuty | 3.5 |
| `HighLatency` | p99 > 1s за 5 мин | warning | Telegram | Проверить DB/Redis |
| `DatabaseDown` | PostgreSQL недоступен > 30 сек | critical | PagerDuty | 3.1 |
| `DatabaseConnectionExhausted` | pool > 90% capacity | critical | PagerDuty | 3.1 |
| `ReplicationLag` | PG lag > 10MB | critical | PagerDuty | 3.1 |
| `RedisDown` | Redis master недоступен > 15 сек | critical | PagerDuty | 3.2 |
| `RedisOOM` | memory > 90% maxmemory | critical | Telegram | 3.2 |
| `NATSDisconnected` | Сервис потерял NATS connection | critical | PagerDuty | 3.3 |
| `DiskSpaceHigh` | disk > 85% | warning | Telegram | Cleanup/expand PV |
| `DiskSpaceCritical` | disk > 95% | critical | PagerDuty | Срочная очистка |
| `CertificateExpiry` | TLS cert < 14 дней | warning | Telegram | cert-manager |
| `PodRestartLoop` | restarts > 5 за 15 мин | warning | Telegram | Проверить логи |
| `BackupFailed` | CronJob backup failed | critical | Telegram | Проверить S3/creds |
| `HighMemoryUsage` | pod memory > 85% limit | warning | Telegram | Утечки / limits |
| `HighCPUUsage` | CPU > 80% за 10 мин | warning | Telegram | HPA / optimize |
| `WSConnectionsHigh` | WS > 80% capacity | warning | Telegram | Scale Gateway |
| `NATSConsumerLag` | lag > 10000 msgs | warning | Telegram | Scale consumer |
| `WALAccumulation` | WAL > 5GB/min growth | critical | PagerDuty | Slots / archiving |
| `PostgresCacheHitRatio` | cache hit < 95% | warning | Telegram | shared_buffers |

### 6.3. Prometheus Alert Rules

```yaml
groups:
  - name: platform.critical
    rules:
      - alert: ServiceDown
        expr: |
          sum by (deployment) (
            kube_deployment_status_replicas_available{namespace="platform"}
          ) == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.deployment }} -- 0 доступных реплик"
          runbook: "docs/DISASTER_RECOVERY.md#34"

      - alert: HighErrorRate
        expr: |
          sum(rate(http_requests_total{namespace="platform",status=~"5.."}[5m])) by (service)
          /
          sum(rate(http_requests_total{namespace="platform"}[5m])) by (service)
          > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.service }}: error rate {{ $value | humanizePercentage }}"
          runbook: "docs/DISASTER_RECOVERY.md#35"

      - alert: DatabaseConnectionExhausted
        expr: |
          pg_stat_activity_count{state="active"}
          / pg_settings_max_connections > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL pool > 90%: {{ $value | humanizePercentage }}"

      - alert: ReplicationLag
        expr: pg_replication_lag_bytes > 10485760
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "PG replication lag: {{ $value | humanize1024 }}B"

      - alert: NATSDisconnected
        expr: nats_connection_status{namespace="platform"} == 0
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.service }} потерял NATS connection"

      - alert: BackupFailed
        expr: |
          kube_job_status_failed{namespace="platform",job_name=~".*backup.*"} > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Бэкап {{ $labels.job_name }} провалился"

  - name: platform.warning
    rules:
      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_request_duration_seconds_bucket{namespace="platform"}[5m])) by (le, service)
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }}: p99 latency {{ $value }}s"

      - alert: RedisOOM
        expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.9
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Redis: memory {{ $value | humanizePercentage }}"

      - alert: DiskSpaceHigh
        expr: |
          (node_filesystem_size_bytes - node_filesystem_avail_bytes)
          / node_filesystem_size_bytes > 0.85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Диск {{ $labels.instance }}: {{ $value | humanizePercentage }}"

      - alert: CertificateExpiry
        expr: |
          (certmanager_certificate_expiration_timestamp_seconds - time()) < 1209600
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "TLS {{ $labels.name }} истекает через {{ $value | humanizeDuration }}"

      - alert: PodRestartLoop
        expr: |
          increase(kube_pod_container_status_restarts_total{namespace="platform"}[15m]) > 5
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.pod }}: {{ $value }} restarts за 15 мин"

      - alert: WSConnectionsHigh
        expr: ws_active_connections / ws_max_connections > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "WS Gateway: {{ $value | humanizePercentage }} capacity"

      - alert: NATSConsumerLag
        expr: nats_consumer_num_pending > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "NATS consumer {{ $labels.consumer }}: {{ $value }} pending"
```

### 6.4. Обязательные Grafana Dashboards

| Dashboard | Ключевые панели |
|---|---|
| **Platform Overview** | SLO compliance, error budget remaining, active users, uptime |
| **API Gateway (:3000)** | RPS, latency p50/p95/p99, error rate by status, top endpoints |
| **WS Gateway (:4000)** | Active connections, messages/sec, reconnect rate, memory per pod |
| **PostgreSQL** | QPS, active connections, replication lag, cache hit ratio, slow queries |
| **Redis / Valkey** | Memory usage, commands/sec, connected clients, hit/miss ratio |
| **NATS JetStream** | Published/consumed msgs, stream sizes, consumer lag, Raft health |
| **ScyllaDB** | Read/write latency, compactions, partition sizes |
| **Kubernetes** | Node CPU/mem, pod restarts, HPA status, PV usage, PDB status |
| **Per-Service (x12)** | Latency, errors, throughput, custom business metrics |

---

## 7. Коммуникация при инцидентах

### 7.1. Уровни severity

| Уровень | Описание | Пример | Response Time | Кто вовлечен |
|---|---|---|---|---|
| **P0** | Полный outage или security breach | Все сервисы лежат; утечка данных | < 5 мин | On-call + Lead + CTO + Security |
| **P1** | Критичная деградация | PostgreSQL/NATS/Redis down; error rate > 50% | < 15 мин | On-call + Lead |
| **P2** | Значительная деградация | 1 Tier-2 сервис недоступен; поиск/голос не работает | < 30 мин | On-call |
| **P3** | Минорная деградация | Высокая latency; медленные загрузки файлов | < 2 часа | Инженер |
| **P4** | Косметическая проблема | Некорректное отображение; мелкий баг | Следующий рабочий день | Инженер |

### 7.2. Escalation Matrix

```
Инцидент обнаружен
         |
         v
+--------------------+
| On-call SRE        |  <-- PagerDuty (P0/P1), Telegram (P2-P4)
| (ротация 7 дней)   |
| Primary + Secondary |
+--------+-----------+
         |
         | Не решено за 15 мин (P0/P1)
         v
+--------+-----------+
| Tech Lead          |
| (backend / infra)  |
+--------+-----------+
         |
         | Не решено за 30 мин (P0)
         v
+--------+-----------+
| CTO / Engineering  |
| Manager            |
+--------+-----------+
         |
         | Security breach
         v
+--------+-----------+
| Security Team +    |
| Legal + CEO        |
+--------------------+
```

### 7.3. Шаблон обновления Status Page

```
[HH:MM UTC] Investigating
  Мы зафиксировали проблемы с [компонент].
  Ведем расследование. Затронутые функции: [список].

[HH:MM UTC] Identified
  Причина определена: [краткое описание].
  Работаем над исправлением. ETA: ~XX минут.

[HH:MM UTC] Monitoring
  Исправление применено. Мониторим систему.
  Все функции должны быть восстановлены.

[HH:MM UTC] Resolved
  Инцидент разрешен. Все системы работают штатно.
  Длительность: XX минут.
  Post-mortem будет опубликован в течение 48 часов.
```

### 7.4. Post-mortem шаблон

```
## Post-mortem: [Краткое описание инцидента]

Дата: YYYY-MM-DD
Severity: P0/P1/P2
Длительность: от HH:MM до HH:MM UTC (X минут)
Автор: [Имя]

### Краткое описание
[1-2 предложения о том, что случилось и какое было влияние]

### Влияние
- Затронутые пользователи: [количество / процент]
- Затронутые функции: [список]
- Потеря данных: [да/нет, описание]
- SLO impact: [использовано X минут из error budget]

### Таймлайн

| Время (UTC) | Событие                            |
|-------------|-------------------------------------|
| HH:MM       | Первый alert (какой именно)         |
| HH:MM       | On-call подключился                 |
| HH:MM       | Причина определена                  |
| HH:MM       | Fix применен                        |
| HH:MM       | Сервис восстановлен                 |
| HH:MM       | Полная верификация                  |

### Root Cause
[Детальное описание технической причины]

### Что сработало хорошо
- [пункт]

### Что можно улучшить
- [пункт]

### Action Items

| Действие         | Ответственный | Дедлайн | Приоритет |
|------------------|---------------|---------|-----------|
| [действие]       | [имя]         | [дата]  | P1/P2/P3  |
```

---

## 8. Инфраструктурная устойчивость

### 8.1. Multi-AZ Deployment

```
Распределение по Availability Zones:

  +---------------------+  +---------------------+  +---------------------+
  |       AZ-1          |  |       AZ-2          |  |       AZ-3          |
  |                     |  |                     |  |                     |
  | K8s Node 1          |  | K8s Node 2          |  | K8s Node 3          |
  | +---+---+---+---+   |  | +---+---+---+---+   |  | +---+---+---+---+   |
  | |API|GW |Auth|Usr|   |  | |API|GW |Auth|Gld|   |  | |GW |Msg|Med|Ntf|   |
  | +---+---+---+---+   |  | +---+---+---+---+   |  | +---+---+---+---+   |
  |                     |  |                     |  |                     |
  | Patroni Primary     |  | Patroni Standby     |  | Patroni Standby     |
  | Redis Master        |  | Redis Replica       |  | Redis Replica       |
  | NATS Node 0         |  | NATS Node 1         |  | NATS Node 2         |
  | ScyllaDB Node 0     |  | ScyllaDB Node 1     |  | ScyllaDB Node 2     |
  | MinIO Server 0-1    |  | MinIO Server 2-3    |  | MinIO Server 4-5    |
  +---------------------+  +---------------------+  +---------------------+

Потеря одной AZ:
  - K8s перешедулит поды на оставшиеся AZ
  - Patroni failover на standby в другой AZ
  - Redis Sentinel failover
  - NATS/ScyllaDB -- кворум сохраняется (2 из 3)
  - MinIO -- erasure coding переживает потерю 1/3 серверов
```

**Pod Anti-Affinity (гарантия распределения по AZ)**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ws-gateway
  namespace: platform
spec:
  replicas: 3
  template:
    metadata:
      labels:
        app: ws-gateway
        tier: gateway
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchLabels:
                app: ws-gateway
            topologyKey: topology.kubernetes.io/zone
      topologySpreadConstraints:
      - maxSkew: 1
        topologyKey: topology.kubernetes.io/zone
        whenUnsatisfiable: DoNotSchedule
        labelSelector:
          matchLabels:
            app: ws-gateway
```

### 8.2. Pod Disruption Budgets

```yaml
# Tier 1 -- критичные сервисы
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: api-gateway-pdb
  namespace: platform
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: api-gateway
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: ws-gateway-pdb
  namespace: platform
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: ws-gateway
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: auth-service-pdb
  namespace: platform
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: auth-service

# Stateful -- базы данных
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: patroni-pdb
  namespace: platform
spec:
  maxUnavailable: 1
  selector:
    matchLabels:
      app: patroni
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: redis-pdb
  namespace: platform
spec:
  maxUnavailable: 1
  selector:
    matchLabels:
      app: redis
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: nats-pdb
  namespace: platform
spec:
  maxUnavailable: 1
  selector:
    matchLabels:
      app: nats
```

### 8.3. Resource Quotas и Limits

```yaml
# Namespace-level quota
apiVersion: v1
kind: ResourceQuota
metadata:
  name: platform-quota
  namespace: platform
spec:
  hard:
    requests.cpu: "32"
    requests.memory: "64Gi"
    limits.cpu: "64"
    limits.memory: "128Gi"
    persistentvolumeclaims: "20"
    pods: "100"
---
# LimitRange -- дефолтные лимиты
apiVersion: v1
kind: LimitRange
metadata:
  name: platform-limits
  namespace: platform
spec:
  limits:
  - default:
      cpu: "500m"
      memory: "512Mi"
    defaultRequest:
      cpu: "100m"
      memory: "128Mi"
    type: Container
```

Рекомендуемые лимиты по сервисам:

| Сервис | CPU req | CPU limit | Mem req | Mem limit | Replicas |
|---|---|---|---|---|---|
| API Gateway (:3000) | 200m | 1000m | 256Mi | 512Mi | 2-5 |
| WS Gateway (:4000) | 500m | 2000m | 512Mi | 2Gi | 2-10 |
| Auth (:3001) | 100m | 500m | 128Mi | 512Mi | 2 |
| Users (:3002) | 100m | 500m | 128Mi | 256Mi | 2 |
| Guilds (:3003) | 100m | 500m | 128Mi | 256Mi | 2 |
| Messages (:3004) | 200m | 1000m | 256Mi | 1Gi | 2-5 |
| Media (:3005) | 500m | 2000m | 512Mi | 2Gi | 2 |
| Notifications (:3006) | 100m | 500m | 128Mi | 256Mi | 1-2 |
| Search (:3007) | 200m | 1000m | 256Mi | 512Mi | 1-2 |
| Voice (:3008) | 100m | 500m | 128Mi | 256Mi | 1-2 |
| Moderation (:3009) | 100m | 500m | 128Mi | 256Mi | 1-2 |
| Presence (:3010) | 200m | 500m | 256Mi | 512Mi | 2 |

### 8.4. Network Policies

```yaml
# 1. Default deny -- запрещаем весь трафик по умолчанию
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny
  namespace: platform
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
---
# 2. Caddy -> Gateways (API + WS)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-ingress-to-gateways
  namespace: platform
spec:
  podSelector:
    matchLabels:
      tier: gateway
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: caddy
    ports:
    - port: 3000
    - port: 4000
---
# 3. Сервисы -> PostgreSQL (через PgBouncer)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-app-to-postgres
  namespace: platform
spec:
  podSelector:
    matchLabels:
      app: pgbouncer
  ingress:
  - from:
    - podSelector:
        matchLabels:
          tier: app
    ports:
    - port: 6432
---
# 4. Сервисы -> Redis
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-app-to-redis
  namespace: platform
spec:
  podSelector:
    matchLabels:
      app: redis
  ingress:
  - from:
    - podSelector:
        matchLabels:
          tier: app
    ports:
    - port: 6379
---
# 5. Сервисы -> NATS
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-app-to-nats
  namespace: platform
spec:
  podSelector:
    matchLabels:
      app: nats
  ingress:
  - from:
    - podSelector:
        matchLabels:
          tier: app
    ports:
    - port: 4222
---
# 6. Разрешить DNS для всех подов
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns
  namespace: platform
spec:
  podSelector: {}
  egress:
  - to:
    - namespaceSelector: {}
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    ports:
    - port: 53
      protocol: UDP
    - port: 53
      protocol: TCP
```

### 8.5. Graceful Shutdown в Rust-сервисах

Все сервисы должны корректно обрабатывать SIGTERM от Kubernetes:

```rust
use axum::Router;
use tokio::signal;

use crate::errors::AppError;

/// Запускает HTTP-сервер с graceful shutdown.
///
/// При SIGTERM/SIGINT:
/// 1. Перестает принимать новые подключения
/// 2. Дожидается завершения текущих запросов (до 30 сек)
/// 3. Корректно закрывает пулы БД и NATS соединения
pub async fn run_server(app: Router, addr: &str) -> Result<(), AppError> {
    let listener = tokio::net::TcpListener::bind(addr).await?;

    tracing::info!(addr, "Сервер запущен");

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    tracing::info!("Сервер корректно остановлен");
    Ok(())
}

async fn shutdown_signal() {
    let ctrl_c = async {
        if let Err(e) = signal::ctrl_c().await {
            tracing::error!("Не удалось установить Ctrl+C handler: {e}");
        }
    };

    #[cfg(unix)]
    let terminate = async {
        match signal::unix::signal(signal::unix::SignalKind::terminate()) {
            Ok(mut signal) => { signal.recv().await; }
            Err(e) => tracing::error!("Не удалось установить SIGTERM handler: {e}"),
        }
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => tracing::info!("Получен SIGINT"),
        _ = terminate => tracing::info!("Получен SIGTERM"),
    }
}
```

**Kubernetes probe конфигурация**:

```yaml
containers:
- name: service
  livenessProbe:
    httpGet:
      path: /health/live
      port: 3000
    initialDelaySeconds: 5
    periodSeconds: 10
    failureThreshold: 3
  readinessProbe:
    httpGet:
      path: /health/ready
      port: 3000
    initialDelaySeconds: 5
    periodSeconds: 5
    failureThreshold: 3
  startupProbe:
    httpGet:
      path: /health/live
      port: 3000
    failureThreshold: 30
    periodSeconds: 2
  lifecycle:
    preStop:
      exec:
        command: ["sleep", "5"]   # Дать K8s время убрать pod из endpoints
  terminationGracePeriodSeconds: 30
```

### 8.6. HPA (Horizontal Pod Autoscaler)

```yaml
# WS Gateway -- автоскейлинг по connections и CPU
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ws-gateway-hpa
  namespace: platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ws-gateway
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: ws_active_connections
      target:
        type: AverageValue
        averageValue: "5000"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Pods
        value: 2
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Pods
        value: 1
        periodSeconds: 120

---
# Messages Service -- автоскейлинг по CPU
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: messages-service-hpa
  namespace: platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: messages-service
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### 8.7. Чеклист готовности DR

```
Инфраструктура:
  [ ] Patroni cluster: primary + standby, synchronous_mode: true
  [ ] PgBouncer: pool_mode = transaction, перед PostgreSQL
  [ ] Redis Sentinel: 3 Sentinel, master + replica(s)
  [ ] NATS cluster: 3 ноды, JetStream R=3
  [ ] MinIO: erasure coding, минимум 4 диска

Бэкапы:
  [ ] PostgreSQL: ежедневный pg_basebackup + continuous WAL (wal-g)
  [ ] Redis: AOF appendfsync everysec + RDB snapshots
  [ ] ScyllaDB: ежедневный nodetool snapshot
  [ ] Meilisearch: ежедневные snapshots
  [ ] Offsite копия бэкапов (правило 3-2-1)

Kubernetes:
  [ ] Probes: livenessProbe + readinessProbe + startupProbe (все сервисы)
  [ ] PDB: для всех Tier-1 сервисов и stateful компонентов
  [ ] Anti-affinity: критичные поды в разных AZ
  [ ] HPA: настроен для Gateway, Messages, API Gateway
  [ ] Network Policies: default deny + whitelist
  [ ] Resource Quotas и LimitRange

Мониторинг:
  [ ] Все алерты из таблицы 6.2 настроены и протестированы
  [ ] Grafana dashboards для всех компонентов
  [ ] Alertmanager -> PagerDuty (P0-P1) + Telegram (P2-P4)

Процессы:
  [ ] On-call ротация настроена (primary + secondary)
  [ ] Backup restore test: CronJob еженедельно
  [ ] Failover drill: ежемесячно
  [ ] Full DR drill: ежеквартально
  [ ] Post-mortem шаблон и процесс
  [ ] Helm rollback протестирован
  [ ] Rolling update: maxUnavailable=0

Документация:
  [ ] Runbooks для всех сценариев протестированы минимум 1 раз
  [ ] Контакты escalation актуальны
  [ ] Status page настроен
```

---

## 9. Источники

### PostgreSQL и бэкапы
- [PostgreSQL 17: Continuous Archiving and PITR](https://www.postgresql.org/docs/17/continuous-archiving.html)
- [PostgreSQL 17: pg_basebackup](https://www.postgresql.org/docs/17/app-pgbasebackup.html)
- [WAL-G -- Archival and Restoration Tool](https://github.com/wal-g/wal-g)
- [Patroni Documentation](https://patroni.readthedocs.io/en/latest/)
- [PgBouncer Documentation](https://www.pgbouncer.org/)
- [pgAudit -- PostgreSQL Audit Extension](https://www.pgaudit.org/)

### Redis
- [Redis Sentinel Documentation](https://redis.io/docs/management/sentinel/)
- [Redis Persistence (AOF + RDB)](https://redis.io/docs/management/persistence/)

### ScyllaDB
- [ScyllaDB Backup and Restore](https://docs.scylladb.com/stable/operating-scylla/procedures/backup-restore/)

### NATS
- [NATS JetStream Documentation](https://docs.nats.io/nats-concepts/jetstream)
- [NATS Clustering](https://docs.nats.io/running-a-nats-service/configuration/clustering)

### Kubernetes
- [Pod Disruption Budgets](https://kubernetes.io/docs/tasks/run-application/configure-pdb/)
- [Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/)
- [Resource Quotas](https://kubernetes.io/docs/concepts/policy/resource-quotas/)
- [Pod Lifecycle and Graceful Shutdown](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)
- [Topology Spread Constraints](https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/)

### Мониторинг
- [Prometheus Alerting Rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)
- [Grafana Loki Documentation](https://grafana.com/docs/loki/latest/)
- [OpenTelemetry for Rust](https://opentelemetry.io/docs/languages/rust/)

### MinIO и Meilisearch
- [MinIO Erasure Coding](https://min.io/docs/minio/linux/operations/concepts/erasure-coding.html)
- [Meilisearch Snapshots](https://www.meilisearch.com/docs/learn/advanced/snapshots)

### Helm
- [Helm Rollback](https://helm.sh/docs/helm/helm_rollback/)

### DR практики
- [Google SRE Book -- Managing Incidents](https://sre.google/sre-book/managing-incidents/)
- [Google SRE Book -- Postmortem Culture](https://sre.google/sre-book/postmortem-culture/)
