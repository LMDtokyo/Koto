# Масштабирование

Комплексная стратегия масштабирования платформы: от 0 до 1M+ пользователей. Конкретные числа, пороги, действия на каждом этапе роста.

---

## 1. Этапы масштабирования

### 1.1. Сводная таблица

| Параметр | 0 -- 10K | 10K -- 100K | 100K -- 1M | 1M+ |
|----------|----------|-------------|------------|-----|
| **K8s ноды** | 3 (4 vCPU / 16GB) | 5--10 (8 vCPU / 32GB) | 10--20 (8 vCPU / 32GB) | 20+ per region |
| **WS Gateway pods** | 1 | 4--8 | 8--16 | 16--32 |
| **WS Gateway shards** | 1 | 4--16 | 16--32 | 64+ |
| **API Gateway pods** | 2 | 3--4 | 4--8 | 8--16 |
| **Messages pods** | 2 | 3--4 | 4--8 | 8--16 |
| **PostgreSQL** | 1P + 1S (Patroni) | 1P + 2R | 1P + 3R + PgBouncer | Sharded (4+) |
| **ScyllaDB** | -- | -- | 3 ноды | 6+ (multi-region) |
| **Redis** | Sentinel (1M + 1R + 3S) | Sentinel (1M + 2R) | Cluster (3M + 3R) | Cluster per region |
| **NATS** | 3 ноды | 3--5 | 5--7 | Super-cluster |
| **MinIO** | Standalone, 500GB | 4 ноды, 8TB | 8 нод, 32TB | 16+, 128TB+ |
| **Meilisearch** | 1 инстанс | 1 инстанс | 2+ (шардинг) | Cluster |
| **CDN** | Нет | Нет | Да (BunnyCDN / Cloudflare) | Глобальный |
| **Регионы** | 1 | 1 | 1 (multi-AZ) | 2+ (EU + US) |
| **Стоимость/мес** | $100--200 | $500--1500 | $3000--8000 | $15000+ |

### 1.2. Этап 0: 0 -- 10K пользователей

**Цель**: запустить MVP, получить первых пользователей, валидировать архитектуру.

**Инфраструктура:**

| Компонент | Конфигурация | Ресурсы |
|-----------|-------------|---------|
| Kubernetes кластер | 1 кластер, 3 ноды | 4 vCPU / 16GB RAM каждая |
| PostgreSQL | 1 primary, 1 standby (Patroni) | 4 vCPU / 16GB RAM, 200GB SSD |
| Redis | 1 master + 1 replica (Sentinel) | 2 vCPU / 4GB RAM |
| NATS | 3 ноды (кластер) | 2 vCPU / 4GB RAM |
| MinIO | 4 диска, erasure coding | 2 vCPU / 4GB RAM, 500GB |
| Meilisearch | 1 инстанс | 2 vCPU / 4GB RAM |

**Микросервисы:**

| Сервис | Порт | Реплики | CPU req/limit | Memory req/limit |
|--------|------|---------|---------------|------------------|
| API Gateway | 3000 | 2 | 200m / 1000m | 128Mi / 512Mi |
| WS Gateway | 4000 | 1 | 500m / 2000m | 256Mi / 1Gi |
| Auth | 3001 | 2 | 100m / 500m | 128Mi / 256Mi |
| Users | 3002 | 2 | 100m / 500m | 128Mi / 256Mi |
| Guilds | 3003 | 2 | 200m / 500m | 128Mi / 256Mi |
| Messages | 3004 | 2 | 200m / 1000m | 256Mi / 512Mi |
| Media | 3005 | 2 | 200m / 1000m | 256Mi / 512Mi |
| Notifications | 3006 | 1 | 100m / 500m | 128Mi / 256Mi |
| Search | 3007 | 1 | 100m / 500m | 128Mi / 256Mi |
| Voice | 3008 | 1 | 100m / 500m | 128Mi / 256Mi |
| Moderation | 3009 | 1 | 100m / 500m | 128Mi / 256Mi |
| Presence | 3010 | 2 | 100m / 500m | 128Mi / 256Mi |

**Что делаем:**
- Сообщения хранятся в PostgreSQL с партиционированием по `created_at` (помесячно)
- Redis в режиме Sentinel (1 master + 1 replica + 3 sentinel)
- Gateway без шардинга (1 pod обслуживает все гильдии)
- PgBouncer перед каждым PostgreSQL инстансом
- CDN не нужен, файлы отдаются напрямую из MinIO через presigned URLs
- Мониторинг: Prometheus + Grafana + Loki с базовыми алертами

**Лимиты на этом этапе:**
- ~2K одновременных WebSocket соединений (1 pod Gateway)
- ~500 сообщений/сек (PostgreSQL primary)
- ~10K ops/sec Redis
- Суммарный объём сообщений < 10M

**Примерная стоимость**: $100--200/мес (Hetzner Cloud / DigitalOcean).

---

### 1.3. Этап 1: 10K -- 100K пользователей

**Триггеры перехода:**
- Одновременных WebSocket соединений > 5K
- Message writes/sec > 1K
- PostgreSQL connection pool usage > 70%
- API latency p99 > 200ms

**Действия:**

**1. Шардирование WebSocket Gateway:**

Переход с 1 pod на 4--8 pods с шардированием по `guild_id`.

Формула назначения шарда:
```
shard_id = (guild_id >> 22) % num_shards
```

Конфигурация:
- 4 pods, по 2--4 шарда на pod
- ~50K соединений на pod (лимит Tokio async tasks при ~8KB на соединение без сжатия)
- Координация шардов через Redis: `gateway:shard:{shard_id}:pod`

**2. Read replicas PostgreSQL:**

Добавляем 1--2 read replicas через Patroni streaming replication.

Какие сервисы читают из реплик:
- Users Service: профили, поиск пользователей
- Guilds Service: список участников, каналы (некритичные запросы)
- Messages Service: история сообщений (cursor-based pagination)
- Search Service: индексация

Какие сервисы читают только из primary:
- Auth Service: проверка credentials (строгая консистентность)
- Guilds Service: создание/обновление ролей, прав (write-after-read)
- Messages Service: создание сообщений (проверка slowmode, лимитов)

**3. Увеличение PgBouncer pool:**

```ini
max_client_conn = 2000
default_pool_size = 40
min_pool_size = 10
reserve_pool_size = 10
```

**4. Горизонтальное масштабирование сервисов:**

| Сервис | Реплики | Причина |
|--------|---------|---------|
| API Gateway | 3--4 | Рост HTTP трафика |
| WS Gateway | 4--8 | Рост соединений |
| Messages | 3--4 | Высокая нагрузка на запись |
| Media | 3 | CPU-intensive обработка изображений |
| Presence | 3 | Все запрашивают "кто онлайн" |

**5. HPA (Horizontal Pod Autoscaler):**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: messages-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: messages-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
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
```

**Инфраструктура:**
- Kubernetes: 5--10 нод (8 vCPU / 32GB RAM каждая)
- PostgreSQL: 1 primary + 2 read replicas
- Redis: Sentinel (2 replicas)
- NATS: 3 ноды

**Примерная стоимость**: $500--1500/мес.

---

### 1.4. Этап 2: 100K -- 1M пользователей

**Триггеры перехода:**
- Сообщений в PostgreSQL > 100M
- p99 чтения сообщений > 50ms
- Message writes/sec > 5K
- Redis memory > 70% от maxmemory
- Одновременных соединений > 50K

**Действия:**

**1. Миграция сообщений на ScyllaDB** (см. раздел 4.4).

**2. CDN интеграция:**
- Presigned URLs MinIO через CDN (Cloudflare, BunnyCDN)
- Кеширование статических файлов на edge
- Отдельный домен `cdn.example.com` (без cookies)
- TTL: аватары 7 дней, вложения 30 дней, эмодзи 365 дней

**3. Multi-AZ развёртывание:**
- Kubernetes кластер в 3 availability zones
- PostgreSQL primary в AZ-1, standby в AZ-2, async replica в AZ-3
- Redis master в AZ-1, replica в AZ-2
- NATS кластер распределён по 3 AZ

**4. Redis Cluster** (см. раздел 4.5):
- Переход с Sentinel на Cluster для горизонтального масштабирования памяти
- 6 нод (3 master + 3 replica), 16GB RAM каждая

**5. Gateway: 16--32 шарда:**

| Параметр | Значение |
|----------|----------|
| Pods | 8--16 |
| Шардов на pod | 2--4 |
| Соединений на pod | ~30--40K |
| Max concurrency | 4--8 |

**6. Image processing workers:**
- Отдельный Deployment для thumbnail генерации
- HPA по очереди задач в NATS
- 4--8 workers

**Инфраструктура:**
- Kubernetes: 10--20 нод (8 vCPU / 32GB RAM)
- PostgreSQL: 1 primary + 3 read replicas + PgBouncer
- ScyllaDB: 3 ноды (8 vCPU / 32GB RAM, NVMe SSD)
- Redis Cluster: 6 нод (3 master + 3 replica)
- NATS: 5 нод
- MinIO: distributed mode, 8+ дисков
- CDN

**Примерная стоимость**: $3000--8000/мес.

---

### 1.5. Этап 3: 1M+ пользователей

**Триггеры перехода:**
- Одновременных соединений > 200K
- Message writes/sec > 20K
- PostgreSQL primary CPU > 80% постоянно
- Географически распределённая аудитория (latency > 150ms для удалённых регионов)

**Действия:**

**1. Шардинг PostgreSQL:**

Стратегия: шардинг по `guild_id` (hash-based).

| Шард | Данные | Принцип |
|------|--------|---------|
| guilds_shard_0 | guild_id % 4 == 0 | Гильдии, каналы, роли, участники |
| guilds_shard_1 | guild_id % 4 == 1 | Гильдии, каналы, роли, участники |
| guilds_shard_2 | guild_id % 4 == 2 | Гильдии, каналы, роли, участники |
| guilds_shard_3 | guild_id % 4 == 3 | Гильдии, каналы, роли, участники |

Users и Auth БД остаются не шардированными (меньший объём, проще consistency).

Роутинг в приложении:
```rust
fn get_shard_pool(guild_id: i64, pools: &[PgPool]) -> &PgPool {
    let shard_idx = (guild_id.unsigned_abs() as usize) % pools.len();
    &pools[shard_idx]
}
```

**2. Multi-region:**
- Primary регион: EU (Frankfurt)
- Secondary регион: US-East
- Read replicas PostgreSQL в каждом регионе
- NATS Super-cluster между регионами (см. раздел 4.6)
- ScyllaDB: добавление нод в обоих регионах (автоматическая репликация)
- Redis Cluster в каждом регионе с локальным кешем
- CDN с edge-серверами на всех континентах

**3. Gateway: 64+ шардов:**
- 16--32 pods
- Geo-routing: клиенты подключаются к ближайшему региону
- `resume_gateway_url` указывает на конкретный регион

**Инфраструктура:**
- Kubernetes: multi-cluster (2+ регионов), 20+ нод на регион
- PostgreSQL: шардированный (4+ шарда), primary + replicas в каждом
- ScyllaDB: 6+ нод (распределены по регионам)
- Redis Cluster: отдельный кластер в каждом регионе
- NATS: super-cluster
- Глобальный CDN

**Примерная стоимость**: $15000+/мес.

---

## 2. WebSocket Gateway Sharding

### 2.1. Архитектура

```
                         ┌─────────────────────┐
                         │    Load Balancer     │
                         │  (ws.example.com)    │
                         └──────────┬──────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
              ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐
              │ GW Pod 0  │  │ GW Pod 1  │  │ GW Pod 2  │
              │ shard 0,1 │  │ shard 2,3 │  │ shard 4,5 │
              └─────┬─────┘  └─────┬─────┘  └─────┬─────┘
                    │               │               │
              ┌─────┴───────────────┴───────────────┴─────┐
              │              NATS JetStream                │
              │  guild.{guild_id}.>  dm.{user_id}.>       │
              └───────────────────────────────────────────┘
                    │               │               │
              ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐
              │   Redis   │  │   Redis   │  │   Redis   │
              │  session  │  │  session  │  │  presence  │
              └───────────┘  └───────────┘  └───────────┘
```

Каждый Gateway pod подписывается через NATS на события своих шардов. NATS JetStream хранит события для resume (TTL 5 мин, max 1000 per subject).

### 2.2. Формула назначения шарда

```rust
/// Вычисляет shard_id для гильдии.
/// Right-shift на 22 бита извлекает timestamp-компонент Snowflake ID,
/// обеспечивая равномерное распределение гильдий по шардам.
fn guild_to_shard(guild_id: i64, total_shards: u16) -> u16 {
    ((guild_id >> 22) % total_shards as i64) as u16
}
```

Правила:
- Один шард обслуживает до **2500 гильдий** (рекомендуется ~1000)
- DM события (без `guild_id`) маршрутизируются на **shard 0**
- Каждое Gateway-соединение привязано к одному шарду
- Один pod может обслуживать несколько шардов

### 2.3. NATS-подписки на шард

Каждый Gateway pod подписывается на NATS subjects для своих шардов:

```rust
use async_nats::jetstream;

/// Подписка Gateway pod на события своих шардов.
/// Каждый шард подписывается на guild-события и DM для пользователей на этом шарде.
async fn subscribe_shard_events(
    js: &jetstream::Context,
    shard_id: u16,
    total_shards: u16,
) -> Result<(), AppError> {
    // Подписка на guild-события через consumer с фильтром
    let consumer = js
        .get_or_create_stream(jetstream::stream::Config {
            name: "GATEWAY_EVENTS".to_string(),
            subjects: vec!["guild.>".to_string(), "dm.>".to_string()],
            retention: jetstream::stream::RetentionPolicy::Limits,
            max_messages_per_subject: 1000,
            max_age: std::time::Duration::from_secs(300),
            storage: jetstream::stream::StorageType::Memory,
            num_replicas: 3,
            ..Default::default()
        })
        .await?;

    let config = jetstream::consumer::pull::Config {
        durable_name: Some(format!("gateway-shard-{shard_id}")),
        filter_subject: format!("guild.shard.{shard_id}.>"),
        ..Default::default()
    };

    let _consumer = consumer
        .get_or_create_consumer(&format!("shard-{shard_id}"), config)
        .await?;

    tracing::info!(
        shard_id,
        total_shards,
        "Subscribed to shard events"
    );

    Ok(())
}
```

Маршрутизация событий в NATS: при публикации события Messages Service вычисляет шард гильдии и публикует в `guild.shard.{shard_id}.{guild_id}.message_create`. Gateway pod с этим шардом получает событие и рассылает подключённым клиентам.

### 2.4. Расчёт памяти на соединение

| Компонент | Размер |
|-----------|--------|
| 2 Tokio tasks (read + write) | ~500 bytes |
| WebSocket read buffer | 4 KiB |
| WebSocket write buffer | 4 KiB |
| mpsc channel (32 slots) | ~2 KiB |
| Session state (guild_ids, intents) | ~1 KiB |
| **Итого (без сжатия)** | **~12 KiB** |
| + zlib-stream контекст | **~268 KiB** |
| + zstd контекст (альтернатива) | **~130 KiB** |

Capacity per pod:

```
┌──────────────┬───────────────────┬──────────────┬──────────────┐
│  Соединений  │ RAM (без сжатия)  │ RAM (с zlib) │ RAM (с zstd) │
├──────────────┼───────────────────┼──────────────┼──────────────┤
│   10 000     │    ~120 MB        │   ~2.6 GB    │   ~1.3 GB    │
│   50 000     │    ~600 MB        │   ~13 GB     │   ~6.5 GB    │
│  100 000     │    ~1.2 GB        │   ~26 GB     │   ~13 GB     │
└──────────────┴───────────────────┴──────────────┴──────────────┘
```

**Рекомендация**: 50K соединений на pod при 16GB RAM (с zlib). Для 100K+ -- использовать zstd-stream (меньший контекст) или ограничить сжатие.

### 2.5. Как клиент узнаёт свой шард

Endpoint `GET /gateway/bot` возвращает информацию о шардинге:

```json
{
    "url": "wss://gateway.example.com",
    "shards": 16,
    "session_start_limit": {
        "total": 1000,
        "remaining": 995,
        "reset_after": 86400000,
        "max_concurrency": 4
    }
}
```

Клиент при IDENTIFY передаёт свой шард:

```json
{
    "op": 2,
    "d": {
        "token": "...",
        "shard": [0, 16],
        "intents": 3276799
    }
}
```

Где `"shard": [shard_id, total_shards]`.

Для обычных пользователей (не ботов) шард назначается сервером автоматически на основе гильдий пользователя. API Gateway маршрутизирует WebSocket подключение на нужный pod через Redis-координацию:

```
GET gateway:shard:{shard_id}:pod  ->  pod_name
```

### 2.6. Session resume при решардинге

При решардинге (увеличение количества шардов) гильдия может переехать на другой шард.

Процедура для клиента:
1. Сервер отправляет `op: 7` (RECONNECT) с `resume_gateway_url`
2. Клиент закрывает текущее соединение
3. Клиент подключается к `resume_gateway_url`
4. Клиент отправляет `op: 6` (RESUME) с `session_id` и `seq`
5. Если сессия найдена в Redis (TTL = `heartbeat_timeout * 2`), сервер переигрывает пропущенные события из NATS JetStream
6. Если сессия не найдена, сервер отправляет `op: 9` (`d: false`) -- клиент выполняет полный IDENTIFY

Хранение сессии для resume:

```rust
/// При disconnect с resumable close code
async fn save_session_for_resume(
    redis: &deadpool_redis::Pool,
    session: &Session,
    timeout_secs: u64,
) -> Result<(), AppError> {
    let mut conn = redis.get().await?;
    let key = format!("gw:session:{}", session.session_id);
    let data = serde_json::to_string(&session.to_resumable())?;
    redis::cmd("SET")
        .arg(&key)
        .arg(&data)
        .arg("EX")
        .arg(timeout_secs * 2)
        .query_async(&mut *conn)
        .await?;
    Ok(())
}
```

### 2.7. Решардинг без даунтайма

Процедура увеличения количества шардов (например, с 8 до 16):

1. **Подготовка**: деплоим новые Gateway pods с конфигурацией `TOTAL_SHARDS=16`
2. **Обновление Redis**: регистрируем новые шарды в координации
3. **Rolling restart**: поочерёдно перезапускаем старые pods
   - Каждый pod перед остановкой отправляет `op: 7` (RECONNECT) всем клиентам
   - Клиенты переподключаются и получают назначение на новый шард
4. **Concurrent startup**: шарды стартуют группами по `max_concurrency`:
   ```
   rate_limit_key = shard_id % max_concurrency

   // max_concurrency=4, total_shards=16:
   // Bucket 0: shards [0, 4, 8, 12]  -- запускаются одновременно
   // Bucket 1: shards [1, 5, 9, 13]  -- через 5 сек
   // Bucket 2: shards [2, 6, 10, 14] -- через 10 сек
   // Bucket 3: shards [3, 7, 11, 15] -- через 15 сек
   ```
5. **Верификация**: все шарды зарегистрированы, все гильдии обслуживаются

Время решардинга: 2--5 минут при `max_concurrency=4` и 16 шардах.

---

## 3. Горизонтальное масштабирование сервисов

### 3.1. HPA для всех сервисов

Шаблон HPA для микросервисов:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ${SERVICE}-hpa
  namespace: platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ${SERVICE}
  minReplicas: ${MIN_REPLICAS}
  maxReplicas: ${MAX_REPLICAS}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
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
```

### 3.2. Ресурсы по сервисам (Resource Estimates)

| Сервис | Порт | CPU req | CPU limit | Mem req | Mem limit | Min реплик | Max реплик | HPA CPU% |
|--------|------|---------|-----------|---------|-----------|------------|------------|----------|
| API Gateway | 3000 | 200m | 1000m | 128Mi | 512Mi | 2 | 16 | 70% |
| WS Gateway | 4000 | 500m | 2000m | 256Mi | 1Gi | 2 | 32 | 70% |
| Auth | 3001 | 100m | 500m | 128Mi | 256Mi | 2 | 10 | 70% |
| Users | 3002 | 100m | 500m | 128Mi | 256Mi | 2 | 10 | 70% |
| Guilds | 3003 | 200m | 500m | 128Mi | 256Mi | 2 | 10 | 70% |
| Messages | 3004 | 200m | 1000m | 256Mi | 512Mi | 2 | 16 | 70% |
| Media | 3005 | 200m | 1000m | 256Mi | 512Mi | 2 | 16 | 60% |
| Notifications | 3006 | 100m | 500m | 128Mi | 256Mi | 1 | 8 | 70% |
| Search | 3007 | 100m | 500m | 128Mi | 256Mi | 1 | 8 | 70% |
| Voice | 3008 | 100m | 500m | 128Mi | 256Mi | 1 | 8 | 70% |
| Moderation | 3009 | 100m | 500m | 128Mi | 256Mi | 1 | 8 | 70% |
| Presence | 3010 | 100m | 500m | 128Mi | 256Mi | 2 | 10 | 70% |

### 3.3. PodDisruptionBudget

Для каждого сервиса -- гарантия доступности при обновлениях:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: messages-service-pdb
  namespace: platform
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: messages-service
```

Для Gateway (stateful соединения):

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: ws-gateway-pdb
  namespace: platform
spec:
  maxUnavailable: 1    # Максимум 1 pod одновременно (graceful drain)
  selector:
    matchLabels:
      app: ws-gateway
```

### 3.4. Приоритет масштабирования при росте нагрузки

```
1. WebSocket Gateway    -- каждое соединение = память, первый bottleneck
2. Message Service      -- самая высокая нагрузка на запись
3. Redis / Presence     -- все запрашивают "кто онлайн"
4. Media Service        -- CPU-intensive обработка файлов
5. PostgreSQL           -- read replicas, connection pooling (PgBouncer)
6. API Gateway          -- stateless, легко масштабируется
7. NATS                 -- обычно не bottleneck до 100K+ users
8. MinIO                -- масштабируется добавлением нод
```

---

## 4. Масштабирование баз данных

### 4.1. PostgreSQL: Read Replicas + Patroni + PgBouncer

**Архитектура с Patroni streaming replication:**

```
┌─────────────────────────────────────────────────┐
│              Patroni Cluster                    │
│                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Primary  │  │ Replica 1│  │ Replica 2│      │
│  │  (write) │─>│  (read)  │─>│  (read)  │      │
│  └──────────┘  └──────────┘  └──────────┘      │
│       │                                         │
│  synchronous   asynchronous                     │
│  replication   replication                      │
└─────────────────────────────────────────────────┘
         │            │             │
    ┌────┴────┐  ┌────┴────┐  ┌────┴────┐
    │PgBouncer│  │PgBouncer│  │PgBouncer│
    │ (write) │  │ (read)  │  │ (read)  │
    └─────────┘  └─────────┘  └─────────┘
         │            │             │
    ┌────┴────────────┴─────────────┴────┐
    │         Микросервисы               │
    │  Auth, Users, Guilds, Messages,    │
    │  Moderation, Notifications         │
    └────────────────────────────────────┘
```

**Маршрутизация в Rust:**

```rust
use std::sync::atomic::{AtomicUsize, Ordering};
use sqlx::PgPool;

pub struct DatabasePools {
    /// Пул для записи (primary)
    pub write: PgPool,
    /// Пул для чтения (read replicas, round-robin)
    pub read: Vec<PgPool>,
    read_index: AtomicUsize,
}

impl DatabasePools {
    /// Возвращает пул для чтения (round-robin по репликам).
    pub fn read_pool(&self) -> &PgPool {
        let idx = self.read_index.fetch_add(1, Ordering::Relaxed) % self.read.len();
        &self.read[idx]
    }

    /// Возвращает пул для записи (primary).
    pub fn write_pool(&self) -> &PgPool {
        &self.write
    }
}
```

**Распределение сервисов:**

| Сервис | Read (реплики) | Write (primary) |
|--------|---------------|-----------------|
| Auth Service | Нет (всегда primary) | credentials, tokens, OAuth |
| Users Service | Профили, поиск | Обновление профиля |
| Guilds Service | Участники, каналы, роли | CRUD гильдий, ролей |
| Messages Service | История, пагинация | Создание сообщений |
| Moderation Service | Аудит-лог, репорты | Создание действий |

### 4.2. PgBouncer Sizing

**Формула размера пула** (PostgreSQL Wiki):

```
connections = (core_count * 2) + effective_spindle_count
```

Для SSD `effective_spindle_count = 1`:

| CPU ядер PG | Формула | Соединений |
|-------------|---------|-----------|
| 4 | (4 * 2) + 1 | 9 |
| 8 | (8 * 2) + 1 | 17 |
| 16 | (16 * 2) + 1 | 33 |

**Конфигурация PgBouncer по этапам:**

```ini
[pgbouncer]
pool_mode = transaction

# Sizing по этапам:
# 0-10K users:  default_pool_size = 25,  max_client_conn = 1000
# 10K-100K:     default_pool_size = 40,  max_client_conn = 2000
# 100K-1M:      default_pool_size = 60,  max_client_conn = 5000
# 1M+:          default_pool_size = 80,  max_client_conn = 10000

default_pool_size = 25
min_pool_size = 5
reserve_pool_size = 5
reserve_pool_timeout = 3

server_idle_timeout = 300
query_timeout = 30
query_wait_timeout = 120

auth_type = scram-sha-256
client_tls_sslmode = require
server_tls_sslmode = require
```

**Connection pooling per service в SQLx:**

```rust
use sqlx::postgres::PgPoolOptions;
use std::time::Duration;

let pool = PgPoolOptions::new()
    .max_connections(25)            // К PgBouncer, не напрямую к PG
    .min_connections(5)
    .acquire_timeout(Duration::from_secs(5))
    .idle_timeout(Duration::from_secs(300))
    .max_lifetime(Duration::from_secs(1800))
    .connect(&database_url)
    .await?;
```

**Лимиты по сервисам:**

| Сервис | max_connections (SQLx pool) | PgBouncer pool |
|--------|---------------------------|----------------|
| Auth | 25 | auth_db |
| Users | 25 | users_db |
| Guilds | 30 | guilds_db |
| Messages | 20 | messages_db |
| Moderation | 15 | moderation_db |
| Notifications | 15 | notifications_db |

### 4.3. Партиционирование PostgreSQL

Таблица сообщений партиционируется по `created_at` (помесячно):

```sql
CREATE TABLE messages (
    id             BIGINT       NOT NULL,
    channel_id     BIGINT       NOT NULL,
    author_id      BIGINT       NOT NULL,
    content        TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
) PARTITION BY RANGE (created_at);

-- Партиции по месяцам
CREATE TABLE messages_y2026m01 PARTITION OF messages
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE messages_y2026m02 PARTITION OF messages
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE messages_y2026m03 PARTITION OF messages
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
```

Преимущества:
- Partition pruning: запросы за конкретный месяц сканируют только 1 партицию
- DROP старых партиций вместо DELETE (мгновенно, без VACUUM)
- Индексы на каждой партиции меньше по размеру
- VACUUM и REINDEX работают по партициям

Автоматизация через cron-job или `pg_partman`: создание партиций на 3 месяца вперёд.

**Index Maintenance:**

| Операция | Расписание | Когда критично |
|---------|-----------|---------------|
| VACUUM ANALYZE | Еженедельно, на каждой партиции | dead_tuples > 10% |
| REINDEX CONCURRENTLY | Ежемесячно | Index bloat > 30% |
| pg_repack | По необходимости | Table bloat > 20% |
| pg_stat_statements reset | Ежемесячно | Для свежей статистики |

### 4.4. Миграция сообщений на ScyllaDB

**Когда переходить:**

| Метрика | Порог | Текущее состояние |
|---------|-------|-------------------|
| Количество сообщений | > 100M | PostgreSQL справляется |
| p99 чтения сообщений | > 50ms | Индексы перестают помещаться в RAM |
| Writes/sec на сообщения | > 10K | PostgreSQL primary перегружен |
| Размер таблицы messages | > 500GB | VACUUM становится дорогим |

Discord перешёл с Cassandra на ScyllaDB и получил:
- p99 чтения: 40--125ms (Cassandra) -> 15ms (ScyllaDB)
- p99 записи: стабильные 5ms
- Нод: 177 (Cassandra) -> 72 (ScyllaDB) при тех же данных

**Dual-Write Strategy (миграция без даунтайма):**

```
Фаза 1: Dual-Write (2-4 недели)
┌────────────┐     ┌──────────┐     ┌──────────┐
│  Messages  │────>│PostgreSQL│     │ ScyllaDB │
│  Service   │────>│  (read)  │     │ (write)  │
└────────────┘     └──────────┘     └──────────┘

Фаза 2: Shadow Read (1-2 недели)
┌────────────┐     ┌──────────┐     ┌──────────┐
│  Messages  │────>│PostgreSQL│     │ ScyllaDB │
│  Service   │────>│  (read)  │<───>│  (read)  │  <-- сравнение результатов
└────────────┘     └──────────┘     └──────────┘

Фаза 3: Switchover
┌────────────┐     ┌──────────┐     ┌──────────┐
│  Messages  │     │PostgreSQL│     │ ScyllaDB │
│  Service   │────>│ (backup) │────>│  (read)  │
└────────────┘     └──────────┘     └──────────┘

Фаза 4: Cleanup
┌────────────┐                      ┌──────────┐
│  Messages  │─────────────────────>│ ScyllaDB │
│  Service   │                      │  (read)  │
└────────────┘                      └──────────┘
```

**Реализация dual-write:**

```rust
pub struct DualWriteMessageStore {
    pg: PgPool,
    scylla: scylla::CachingSession,
    read_from: ReadSource,  // feature flag: PostgreSQL | ScyllaDB | Both
}

impl DualWriteMessageStore {
    pub async fn create_message(&self, msg: &CreateMessage) -> Result<Message, AppError> {
        // Всегда пишем в обе БД
        let pg_result = self.write_to_pg(msg).await;
        let scylla_result = self.write_to_scylla(msg).await;

        // Логируем расхождения
        if let Err(ref e) = scylla_result {
            tracing::error!(error = %e, message_id = %msg.id, "scylla dual-write failed");
            // ScyllaDB ошибка не блокирует ответ (PostgreSQL -- source of truth)
        }

        pg_result
    }

    pub async fn get_messages(
        &self,
        channel_id: i64,
        before: Option<i64>,
        limit: i32,
    ) -> Result<Vec<Message>, AppError> {
        match self.read_from {
            ReadSource::PostgreSQL => self.read_from_pg(channel_id, before, limit).await,
            ReadSource::ScyllaDB => self.read_from_scylla(channel_id, before, limit).await,
            ReadSource::Both => {
                // Shadow read: запрашиваем обе, сравниваем, возвращаем PG
                let (pg, scylla) = tokio::join!(
                    self.read_from_pg(channel_id, before, limit),
                    self.read_from_scylla(channel_id, before, limit),
                );
                if let (Ok(ref pg_msgs), Ok(ref sc_msgs)) = (&pg, &scylla) {
                    if pg_msgs.len() != sc_msgs.len() {
                        tracing::warn!(
                            channel_id,
                            pg_count = pg_msgs.len(),
                            scylla_count = sc_msgs.len(),
                            "dual-read mismatch"
                        );
                    }
                }
                pg
            }
        }
    }
}
```

**Bucket Pattern для горячих каналов:**

```sql
CREATE TABLE messages (
    channel_id  bigint,
    bucket      int,             -- snowflake_to_bucket(message_id)
    message_id  bigint,
    author_id   bigint,
    content     text,
    PRIMARY KEY ((channel_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_unit': 'DAYS',
                    'compaction_window_size': 1}
  AND gc_grace_seconds = 864000;
```

Вычисление bucket:

```rust
const BUCKET_DURATION_MS: i64 = 1_000 * 60 * 60 * 24 * 10; // 10 дней
const DISCORD_EPOCH: i64 = 1_420_070_400_000;

fn snowflake_to_bucket(snowflake_id: i64) -> i32 {
    let timestamp_ms = (snowflake_id >> 22) + DISCORD_EPOCH;
    (timestamp_ms / BUCKET_DURATION_MS) as i32
}
```

### 4.5. Redis: Sentinel -> Cluster

**Когда переходить на Cluster:**
- Memory usage > 70% от одного инстанса
- Ops/sec > 100K на запись
- Нужно больше RAM, чем помещается в один сервер

**Sentinel (0 -- 100K пользователей):**
```
1 master + 1-2 replicas + 3 sentinels
maxmemory = 4GB
```

**Cluster (100K+ пользователей):**
```
6 нод = 3 master + 3 replica
maxmemory = 16GB per node
Total available memory = 48GB
```

Процедура миграции:

1. Развернуть Redis Cluster рядом с Sentinel
2. Обновить конфигурацию `deadpool-redis` в сервисах на Cluster URL
3. Выполнить rolling restart микросервисов (сервисы переподключаются к Cluster)
4. Данные с TTL мигрируют естественно (кеш перезаполняется из БД)
5. Для persistent данных (сессии): dual-write период 2--3 дня
6. Выключить Sentinel

**Оценка Redis RAM по этапам:**

| Этап | Пользователей | Сессии | Presence | Кеш | Total RAM |
|------|--------------|--------|----------|-----|-----------|
| 0--10K | 10K | 5 MB | 20 MB | 50 MB | ~200 MB |
| 10K--100K | 100K | 50 MB | 200 MB | 500 MB | ~2 GB |
| 100K--1M | 1M | 500 MB | 2 GB | 5 GB | ~15 GB |
| 1M+ | 5M+ | 2.5 GB | 10 GB | 25 GB | ~50 GB (Cluster) |

### 4.6. NATS Scaling

**Cluster Mode (3+ нод):**

```hcl
# nats-server.conf
cluster {
    name: platform-nats
    listen: 0.0.0.0:6222

    routes = [
        nats-route://nats-0:6222
        nats-route://nats-1:6222
        nats-route://nats-2:6222
    ]
}

jetstream {
    store_dir: /data/jetstream
    max_mem: 2G
    max_file: 50G
}
```

Минимум 3 ноды для кворума. NATS использует Raft consensus для JetStream.

| Этап | Ноды | Max Memory | Max File Store | Назначение |
|------|------|-----------|----------------|------------|
| 0--10K | 3 | 1GB | 10GB | Базовый кластер |
| 10K--100K | 3--5 | 2GB | 50GB | Больше потоков |
| 100K--1M | 5--7 | 4GB | 100GB | Высокий throughput |
| 1M+ | Super-cluster | 8GB | 200GB | Multi-region |

**Super-Cluster для Multi-Region:**

```hcl
# EU кластер (nats-eu.conf)
gateway {
    name: eu-cluster
    listen: 0.0.0.0:7222

    gateways: [
        { name: us-cluster, urls: ["nats://nats-us-0:7222", "nats://nats-us-1:7222"] }
        { name: ap-cluster, urls: ["nats://nats-ap-0:7222", "nats://nats-ap-1:7222"] }
    ]
}
```

Характеристики:
- Автоматическая маршрутизация сообщений между регионами
- Interest-based forwarding: сообщения пересылаются только если есть подписчик в другом кластере
- Не дублирует данные JetStream (streams остаются локальными, если не настроен mirror)

### 4.7. Meilisearch Sharding

При > 10M документов:
- Разделить индекс `messages` по `guild_id` на несколько инстансов
- Роутинг: `guild_id % num_meili_nodes`
- Альтернатива: Meilisearch multi-search API для parallel queries

---

## 5. Узкие места (Bottlenecks)

### 5.1. Таблица мониторинга

| Компонент | Метрика | Порог Warning | Порог Critical | Действие |
|-----------|---------|---------------|---------------|----------|
| **WS Gateway** | Connections per pod | > 40K | > 48K | Добавить pods, увеличить шарды |
| **WS Gateway** | Heartbeat latency p99 | > 2s | > 5s | Проверить CPU, уменьшить соединения на pod |
| **WS Gateway** | Slow consumers/min | > 20 | > 100 | Увеличить write buffer, проверить сеть |
| **WS Gateway** | Memory per pod | > 70% limit | > 85% limit | Увеличить limit или снизить соединения |
| **Message Service** | Writes/sec | > 3K | > 5K | Добавить replicas, рассмотреть ScyllaDB |
| **Message Service** | Read latency p99 | > 100ms | > 500ms | Проверить индексы, добавить read replicas |
| **API Gateway** | Request latency p99 | > 200ms | > 1s | Добавить pods, проверить downstream |
| **API Gateway** | Error rate (5xx) | > 1% | > 5% | Проверить зависимости, добавить pods |
| **PostgreSQL** | Connection pool usage | > 70% | > 90% | Увеличить pool / добавить read replicas |
| **PostgreSQL** | Replication lag | > 1MB | > 10MB | Проверить I/O, сеть, WAL |
| **PostgreSQL** | Cache hit ratio | < 99% | < 95% | Увеличить shared_buffers |
| **PostgreSQL** | Dead tuples % | > 10% | > 20% | Настроить autovacuum, pg_repack |
| **PostgreSQL** | Longest query | > 10s | > 30s | Kill query, оптимизация, индексы |
| **ScyllaDB** | Read latency p99 | > 20ms | > 50ms | Добавить ноды, проверить compaction |
| **ScyllaDB** | Write latency p99 | > 10ms | > 25ms | Добавить ноды, проверить диски |
| **ScyllaDB** | Disk usage | > 60% | > 80% | Добавить ноды / диски |
| **Redis** | Memory usage | > 70% maxmemory | > 90% maxmemory | Scale up или migrate to Cluster |
| **Redis** | Evictions/sec | > 100 | > 1000 | Увеличить RAM, проверить TTL |
| **Redis** | Command latency p99 | > 1ms | > 5ms | Проверить hot keys, pipeline |
| **Redis** | Connected clients | > 5000 | > 8000 | Connection pooling, увеличить maxclients |
| **NATS** | Consumer lag (pending) | > 500 | > 1000 | Добавить consumer replicas |
| **NATS** | Messages/sec | > 50K | > 100K | Добавить ноды кластера |
| **NATS** | Slow consumers | > 5 | > 20 | Увеличить буферы, проверить consumers |
| **MinIO** | Disk usage | > 70% | > 85% | Добавить ноды / диски |
| **MinIO** | Request latency p99 | > 200ms | > 1s | Проверить I/O, сеть |
| **Meilisearch** | Search latency p99 | > 100ms | > 500ms | Проверить RAM, индексы |
| **Meilisearch** | Documents count | > 8M | > 10M | Шардинг индексов |

### 5.2. Prometheus Alerting Rules

```yaml
groups:
  - name: scaling-alerts
    rules:
      - alert: GatewayHighConnections
        expr: gateway_connections_active > 40000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Gateway pod approaching connection limit"
          description: "Pod {{ $labels.pod }} has {{ $value }} connections (limit: 50K)"

      - alert: MessageServiceHighWriteRate
        expr: rate(messages_created_total[5m]) > 5000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Message write rate exceeding threshold"

      - alert: PostgresConnectionPoolExhausted
        expr: pg_stat_activity_count / pg_settings_max_connections > 0.8
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL connection pool usage > 80%"

      - alert: RedisHighMemory
        expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.7
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis memory usage > 70%"

      - alert: NATSHighConsumerLag
        expr: nats_consumer_num_pending > 1000
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "NATS consumer lag > 1000 pending messages"

      - alert: ScyllaHighReadLatency
        expr: scylla_storage_proxy_coordinator_read_latency_summary{quantile="0.99"} > 50000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "ScyllaDB p99 read latency > 50ms"

      - alert: HighAPIErrorRate
        expr: |
          sum(rate(http_requests_total{status=~"5.."}[5m]))
          /
          sum(rate(http_requests_total[5m])) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "API error rate > 5%"
```

---

## 6. Request Coalescing (Singleflight)

### 6.1. Проблема

100K пользователей онлайн в крупной гильдии. Все открывают список участников одновременно. Без coalescing:

```
100 000 клиентов -> 100 000 запросов -> 100 000 запросов в БД
```

С request coalescing:

```
100 000 клиентов -> 100 000 запросов -> 1 запрос в БД -> результат раздаётся всем
```

### 6.2. Реализация: Singleflight pattern

Дедупликация in-flight запросов по ключу. Если запрос с таким ключом уже выполняется, новые вызовы ожидают результат первого.

```rust
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::{broadcast, Mutex};

/// Singleflight -- дедупликация одновременных запросов по ключу.
/// Паттерн заимствован из Go singleflight, адаптирован для Tokio.
pub struct Singleflight<V: Clone + Send + Sync + 'static> {
    in_flight: Mutex<HashMap<String, broadcast::Sender<Arc<V>>>>,
}

impl<V: Clone + Send + Sync + 'static> Singleflight<V> {
    pub fn new() -> Self {
        Self {
            in_flight: Mutex::new(HashMap::new()),
        }
    }

    /// Выполняет `f` только один раз для данного ключа.
    /// Остальные вызовы с тем же ключом ждут результат первого.
    pub async fn do_once<F, Fut>(
        &self,
        key: &str,
        f: F,
    ) -> Result<Arc<V>, AppError>
    where
        F: FnOnce() -> Fut,
        Fut: std::future::Future<Output = Result<V, AppError>>,
    {
        // Проверяем, есть ли уже in-flight запрос
        {
            let map = self.in_flight.lock().await;
            if let Some(tx) = map.get(key) {
                let mut rx = tx.subscribe();
                drop(map); // Освободить lock до await
                return rx.recv().await.map_err(|_| AppError::Internal(
                    "singleflight: sender dropped".into()
                ));
            }
        }

        // Создаём новый in-flight запрос
        let (tx, _) = broadcast::channel(1);
        {
            let mut map = self.in_flight.lock().await;
            map.insert(key.to_string(), tx.clone());
        }

        // Выполняем запрос
        let result = f().await;

        // Удаляем из in-flight
        {
            let mut map = self.in_flight.lock().await;
            map.remove(key);
        }

        match result {
            Ok(value) => {
                let arc_value = Arc::new(value);
                // Отправляем результат всем ожидающим (ошибка send = нет подписчиков)
                let _ = tx.send(arc_value.clone());
                Ok(arc_value)
            }
            Err(e) => Err(e),
        }
    }
}
```

### 6.3. Использование в handler

```rust
use std::sync::Arc;

struct GuildMembersHandler {
    singleflight: Arc<Singleflight<Vec<GuildMember>>>,
    db: PgPool,
}

impl GuildMembersHandler {
    async fn get_members(
        &self,
        guild_id: i64,
        limit: i32,
    ) -> Result<Arc<Vec<GuildMember>>, AppError> {
        let key = format!("guild_members:{}:{}", guild_id, limit);
        let db = self.db.clone();

        self.singleflight
            .do_once(&key, move || async move {
                sqlx::query_as!(
                    GuildMember,
                    "SELECT user_id, nickname, joined_at
                     FROM guild_members
                     WHERE guild_id = $1
                     ORDER BY joined_at
                     LIMIT $2",
                    guild_id,
                    limit as i64,
                )
                .fetch_all(&db)
                .await
                .map_err(AppError::from)
            })
            .await
    }
}
```

### 6.4. Где применяется

| Запрос | Ключ coalescing | Частота |
|--------|----------------|---------|
| Список участников гильдии | `guild_members:{guild_id}:{limit}` | Очень высокая |
| Информация о канале | `channel:{channel_id}` | Высокая |
| Профиль пользователя | `user:{user_id}` | Высокая |
| Роли гильдии | `guild_roles:{guild_id}` | Высокая |
| Права пользователя в канале | `perms:{user_id}:{channel_id}` | Очень высокая |

---

## 7. Кеширование как инструмент масштабирования

### 7.1. TTL по типам данных

| Данные | Ключ Redis | TTL | Паттерн | Обоснование TTL |
|--------|-----------|-----|---------|-----------------|
| User profile | `user:{user_id}` | 5 мин | Cache-Aside | Меняется редко, при изменении -- NATS-инвалидация |
| Guild data | `guild:{guild_id}` | 10 мин | Cache-Aside + Write-Through | Базовые данные стабильны |
| Channel data | `channel:{channel_id}` | 10 мин | Cache-Aside + Write-Through | Аналогично guild data |
| Guild roles | `guild:{guild_id}:roles` | 5 мин | Cache-Aside | Роли меняются нечасто |
| Computed permissions | `perms:{guild_id}:{channel_id}:{user_id}` | 2 мин | Cache-Aside | Критичные данные, короткий TTL |
| Member count | `guild:{guild_id}:member_count` | 1 мин | Cache-Aside | Меняется при join/leave |
| Presence | `presence:{user_id}:status` | 120 сек | Write-Behind | Обновляется heartbeat каждые 30 сек |
| Typing indicator | `typing:{channel_id}:{user_id}` | 10 сек | Write-Behind | Эфемерные данные |
| Rate limits | `rate:{bucket}:{identifier}` | = window | Прямая запись | TTL = размер окна |
| JWT denylist | `jwt_deny:{jti}` | = остаток жизни токена | Прямая запись | Автоматически очищается |
| Invite | `invite:{code}` | = expires_at инвайта | Cache-Aside | Привязан к сроку инвайта |

### 7.2. In-process кеш с `moka`

Для hot keys используем in-process кеш `moka` (lock-free concurrent cache на Rust) перед Redis. Это снижает нагрузку на Redis в 100--1000x при горячих ключах.

```rust
use moka::future::Cache;
use std::time::Duration;

/// In-process кеш горячих ключей.
/// moka -- lock-free concurrent cache, оптимизированный для Tokio.
/// Снижает нагрузку на Redis при hot keys (гильдии с 100K+ online).
pub struct LocalMokaCache {
    cache: Cache<String, Vec<u8>>,
}

impl LocalMokaCache {
    pub fn new(max_capacity: u64, ttl: Duration) -> Self {
        let cache = Cache::builder()
            .max_capacity(max_capacity)
            .time_to_live(ttl)
            .time_to_idle(ttl / 2)
            .build();
        Self { cache }
    }

    pub async fn get(&self, key: &str) -> Option<Vec<u8>> {
        self.cache.get(key).await
    }

    pub async fn insert(&self, key: String, value: Vec<u8>) {
        self.cache.insert(key, value).await;
    }

    pub async fn invalidate(&self, key: &str) {
        self.cache.invalidate(key).await;
    }
}
```

Использование с Redis:

```rust
pub struct TieredCache {
    local: LocalMokaCache,
    redis: deadpool_redis::Pool,
}

impl TieredCache {
    /// L1 (moka, 0 latency) -> L2 (Redis, ~1ms) -> PostgreSQL (~5ms).
    pub async fn get_cached<T: serde::Serialize + serde::de::DeserializeOwned>(
        &self,
        key: &str,
        ttl_secs: u64,
        fetch_fn: impl std::future::Future<Output = Result<T, AppError>>,
    ) -> Result<T, AppError> {
        // L1: in-process moka
        if let Some(bytes) = self.local.get(key).await {
            return serde_json::from_slice(&bytes).map_err(|_| {
                // Повреждённые данные -- инвалидировать и продолжить
                // Не блокируем, cleanup асинхронный
                AppError::Internal("local cache deserialization failed".into())
            });
        }

        // L2: Redis
        let mut conn = self.redis.get().await?;
        let cached: Option<String> = redis::cmd("GET")
            .arg(key)
            .query_async(&mut *conn)
            .await?;

        if let Some(json) = cached {
            // Сохранить в L1
            self.local.insert(key.to_string(), json.as_bytes().to_vec()).await;
            return serde_json::from_str(&json).map_err(AppError::from);
        }

        // L3: PostgreSQL (через fetch_fn)
        let value = fetch_fn.await?;
        let json = serde_json::to_string(&value)?;

        // Записать в L2 (Redis)
        redis::cmd("SET")
            .arg(key)
            .arg(&json)
            .arg("EX")
            .arg(ttl_secs)
            .query_async(&mut *conn)
            .await?;

        // Записать в L1 (moka)
        self.local.insert(key.to_string(), json.as_bytes().to_vec()).await;

        Ok(value)
    }
}
```

### 7.3. Cache Stampede Prevention

**Проблема:** TTL истекает на популярном ключе -- сотни запросов одновременно идут в PostgreSQL.

**Решение 1: Distributed Lock (SETNX)**

```rust
/// Получить данные с защитой от stampede через distributed lock.
pub async fn get_with_lock<T, F, Fut>(
    pool: &deadpool_redis::Pool,
    key: &str,
    ttl_secs: u64,
    lock_ttl_ms: u64,
    fetch_fn: F,
) -> Result<T, AppError>
where
    T: serde::Serialize + serde::de::DeserializeOwned,
    F: FnOnce() -> Fut,
    Fut: std::future::Future<Output = Result<T, AppError>>,
{
    let mut conn = pool.get().await?;

    // 1. Проверить кеш
    let cached: Option<String> = redis::cmd("GET")
        .arg(key)
        .query_async(&mut *conn)
        .await?;

    if let Some(json) = cached {
        return serde_json::from_str(&json).map_err(AppError::from);
    }

    // 2. Попытаться захватить lock
    let lock_key = format!("{key}:lock");
    let acquired: bool = redis::cmd("SET")
        .arg(&lock_key)
        .arg("1")
        .arg("NX")
        .arg("PX")
        .arg(lock_ttl_ms)
        .query_async(&mut *conn)
        .await?;

    if acquired {
        // 3. Мы получили lock -- запросить PostgreSQL
        let data = fetch_fn().await?;
        let json = serde_json::to_string(&data)?;

        redis::cmd("SET")
            .arg(key)
            .arg(&json)
            .arg("EX")
            .arg(ttl_secs)
            .query_async(&mut *conn)
            .await?;

        // Снять lock
        redis::cmd("DEL")
            .arg(&lock_key)
            .query_async(&mut *conn)
            .await?;

        Ok(data)
    } else {
        // 4. Lock занят -- подождать и повторить чтение из кеша
        tokio::time::sleep(std::time::Duration::from_millis(50)).await;
        let cached: Option<String> = redis::cmd("GET")
            .arg(key)
            .query_async(&mut *conn)
            .await?;

        match cached {
            Some(json) => serde_json::from_str(&json).map_err(AppError::from),
            // Fallback: lock holder ещё не записал -- идём в PostgreSQL напрямую
            None => fetch_fn().await,
        }
    }
}
```

**Решение 2: Probabilistic Early Expiration (PER)**

```rust
use rand::Rng;

/// Проверить, нужно ли обновить кеш досрочно (XFetch алгоритм).
/// beta -- коэффициент (1.0 = стандартный, больше = более агрессивное обновление).
/// delta -- время вычисления данных в секундах.
pub fn should_early_recompute(ttl_remaining_secs: f64, delta: f64, beta: f64) -> bool {
    let mut rng = rand::thread_rng();
    let random: f64 = rng.gen();

    // XFetch: -delta * beta * ln(random) >= ttl_remaining
    let threshold = -delta * beta * random.ln();
    threshold >= ttl_remaining_secs
}
```

**Рекомендация:** distributed lock для ключей с высокой конкуренцией (guild data больших серверов), PER -- для менее критичных данных (профили, каналы).

---

## 8. CDN и статические файлы

### 8.1. Архитектура раздачи файлов

```
Клиент
  │
  ├── Upload:
  │     1. POST /media/upload  ->  Media Service
  │     2. Media Service генерирует presigned PUT URL
  │     3. Клиент делает PUT напрямую в MinIO
  │     4. Media Service проверяет файл (MIME, size, ClamAV)
  │     5. NATS: media.uploaded.{file_id}
  │
  └── Download:
        1. GET cdn.example.com/attachments/{hash}.png
        2. CDN проверяет кеш
           ├── Cache HIT  ->  отдаёт из edge (~5ms)
           └── Cache MISS ->  запрос к MinIO origin
                              -> кеширование на edge
                              -> отдача (~100ms первый раз)
```

### 8.2. Presigned URLs с CDN

```rust
use aws_sdk_s3::presigning::PresigningConfig;
use std::time::Duration;

/// Генерация CDN URL с presigned параметрами.
/// MinIO origin URL подменяется на CDN домен.
async fn generate_cdn_url(
    s3_client: &aws_sdk_s3::Client,
    bucket: &str,
    key: &str,
    expires_in: Duration,
) -> Result<String, AppError> {
    let presigned = s3_client
        .get_object()
        .bucket(bucket)
        .key(key)
        .presigned(
            PresigningConfig::builder()
                .expires_in(expires_in)
                .build()?,
        )
        .await?;

    // Подменяем origin URL на CDN URL
    let cdn_url = presigned
        .uri()
        .to_string()
        .replace("minio.internal:9000", "cdn.example.com");

    Ok(cdn_url)
}
```

### 8.3. CDN Caching Policy

| Тип файла | Cache-Control | TTL CDN |
|----------|---------------|---------|
| Аватары | `public, max-age=604800, immutable` | 7 дней |
| Иконки гильдий | `public, max-age=604800, immutable` | 7 дней |
| Вложения | `public, max-age=2592000` | 30 дней |
| Custom emoji | `public, max-age=31536000, immutable` | 365 дней |
| Temp uploads | `private, no-cache` | Не кешировать |

**Важно**: файлы хранятся с content-addressable именами (hash содержимого). При обновлении аватара создаётся новый ключ, старый инвалидируется.

### 8.4. MinIO Distributed Mode

| Этап | Ноды | Диски | Объём | Erasure Coding |
|------|------|-------|-------|---------------|
| 0--10K | 1 (standalone) | 1 | 500GB | Нет |
| 10K--100K | 4 | 16 | 8TB | EC:4 (tolerates 2 failures) |
| 100K--1M | 8 | 32 | 32TB | EC:8 (tolerates 4 failures) |
| 1M+ | 16+ | 64+ | 128TB+ | EC:16 |

### 8.5. Image Processing Workers

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: image-processor
spec:
  replicas: 4
  template:
    spec:
      containers:
        - name: image-processor
          image: registry.example.com/platform/media-service:latest
          command: ["service", "--mode=worker"]
          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: 2000m
              memory: 1Gi
```

HPA по метрике NATS consumer lag:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: image-processor-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: image-processor
  minReplicas: 2
  maxReplicas: 16
  metrics:
    - type: Pods
      pods:
        metric:
          name: nats_consumer_pending_messages
        target:
          type: AverageValue
          averageValue: "50"
```

Задачи worker-а:
- Resize изображений (128x128, 256x256, 1024x1024 для аватаров)
- Генерация thumbnails для вложений
- Strip EXIF данных (`img-parts` crate)
- Конвертация в WebP (экономия 25--35% размера)
- Проверка MIME через magic bytes (`infer` crate)
- Антивирусная проверка (ClamAV) для произвольных файлов

---

## 9. Мониторинг масштабирования

### 9.1. Стек мониторинга

| Компонент | Инструмент | Роль |
|-----------|-----------|------|
| Метрики | Prometheus | Сбор метрик (scrape /metrics) |
| Дашборды | Grafana | Визуализация |
| Логи | Grafana Loki + Promtail | Агрегация логов |
| Трейсы | OpenTelemetry + Tempo | Distributed tracing |
| Алерты | Alertmanager | Оповещения (Telegram / Slack) |

### 9.2. Ключевые Prometheus-запросы

**WebSocket Gateway:**

```promql
# Текущее количество соединений по pods
gateway_connections_active

# Скорость подключений/отключений
rate(gateway_connections_total[5m])
rate(gateway_disconnections_total[5m])

# Heartbeat latency p99
histogram_quantile(0.99, rate(gateway_heartbeat_duration_seconds_bucket[5m]))

# Slow consumers (клиенты, не успевающие читать)
rate(gateway_slow_consumers_total[5m])

# Память на pod
container_memory_working_set_bytes{pod=~"ws-gateway.*"}
```

**Messages Service:**

```promql
# Writes per second
rate(messages_created_total[5m])

# Read latency p99
histogram_quantile(0.99, rate(messages_read_duration_seconds_bucket[5m]))

# Write latency p99
histogram_quantile(0.99, rate(messages_write_duration_seconds_bucket[5m]))
```

**PostgreSQL:**

```promql
# Connection pool utilization
pg_stat_activity_count / pg_settings_max_connections

# Replication lag in bytes
pg_stat_replication_pg_wal_lsn_diff

# Cache hit ratio (должна быть > 99%)
pg_stat_database_blks_hit / (pg_stat_database_blks_hit + pg_stat_database_blks_read)

# Transactions per second
rate(pg_stat_database_xact_commit[5m]) + rate(pg_stat_database_xact_rollback[5m])

# Dead tuples (bloat indicator)
pg_stat_user_tables_n_dead_tup
```

**Redis:**

```promql
# Memory usage %
redis_memory_used_bytes / redis_memory_max_bytes

# Hit ratio
rate(redis_keyspace_hits_total[5m]) /
  (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))

# Evictions per second
rate(redis_evicted_keys_total[5m])

# Command latency
redis_commands_duration_seconds_total
```

**NATS:**

```promql
# Consumer pending messages (lag)
nats_consumer_num_pending

# Messages per second
rate(nats_server_messages_total[5m])

# Slow consumers
nats_server_slow_consumers
```

### 9.3. Grafana Dashboards

**Dashboard: Platform Overview**

Панели:
1. **Онлайн пользователей** (gauge) -- `sum(gateway_connections_active)`
2. **Сообщений/сек** (graph) -- `rate(messages_created_total[5m])`
3. **API latency p99** (graph) -- `histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))`
4. **Error rate** (graph) -- `sum(rate(http_requests_total{status=~"5.."}[5m]))`
5. **WS connections per pod** (bar) -- `gateway_connections_active by (pod)`

**Dashboard: Database Health**

Панели:
1. **PostgreSQL connection pool %** -- utilization по базам данных
2. **Replication lag** (graph) -- bytes отставания реплик
3. **Cache hit ratio** (gauge) -- цель > 99%
4. **Dead tuples** (table) -- по таблицам
5. **Redis memory %** (gauge) -- по нодам
6. **Redis hit ratio** (graph) -- по key prefixes
7. **ScyllaDB latency** (graph) -- p50/p95/p99

**Dashboard: Scaling Readiness**

Панели:
1. **CPU utilization per service** (heatmap) -- по pods
2. **Memory utilization per service** (heatmap) -- по pods
3. **HPA status** (table) -- current/desired replicas, scaling events
4. **NATS consumer lag** (graph) -- по consumers
5. **Disk usage** (bar) -- PostgreSQL, ScyllaDB, MinIO, Meilisearch

### 9.4. Alert Routing (Alertmanager)

```yaml
# alertmanager.yml
route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: telegram-default
  routes:
    - match:
        severity: critical
      receiver: telegram-oncall
      repeat_interval: 5m
    - match:
        severity: warning
      receiver: telegram-default
      repeat_interval: 30m

receivers:
  - name: telegram-oncall
    telegram_configs:
      - bot_token: ${TELEGRAM_BOT_TOKEN}
        chat_id: ${TELEGRAM_ONCALL_CHAT_ID}
        parse_mode: HTML
  - name: telegram-default
    telegram_configs:
      - bot_token: ${TELEGRAM_BOT_TOKEN}
        chat_id: ${TELEGRAM_DEFAULT_CHAT_ID}
        parse_mode: HTML
```

---

## 10. Capacity Planning

### 10.1. Формулы

**WebSocket Gateway:**
```
pods_needed = ceil(concurrent_connections / connections_per_pod)
connections_per_pod = pod_memory / memory_per_connection
memory_per_connection = 12 KiB (без сжатия) или 268 KiB (zlib)
```

**PostgreSQL connections:**
```
pg_max_connections = (core_count * 2) + effective_spindle_count
pgbouncer_pool_size = pg_max_connections * 0.8
total_app_connections = num_services * replicas_per_service * connections_per_replica
```

**Redis memory:**
```
redis_memory = sessions + presence + cache + rate_limits + overhead
sessions = num_users * 500 bytes
presence = num_online_users * (status_keys + guild_sets) * ~100 bytes
cache = num_cached_entities * avg_entity_size
overhead = redis_memory * 1.3  (fragmentation ratio)
```

**NATS throughput:**
```
events_per_second = messages_per_sec * (1 + avg_guild_size_factor)
  + presence_updates_per_sec
  + typing_events_per_sec
  + member_events_per_sec
```

**Bandwidth:**
```
ws_bandwidth = concurrent_connections * avg_events_per_sec * avg_event_size
api_bandwidth = requests_per_sec * avg_response_size
cdn_bandwidth = file_requests_per_sec * avg_file_size * (1 - cache_hit_ratio)
```

### 10.2. Пример расчёта: 100K пользователей

**Вводные:**
- 100K зарегистрированных пользователей
- ~30% DAU = 30K активных в день
- ~10% одновременно = 10K concurrent
- ~5K concurrent WS connections
- ~200 сообщений/мин в среднем = ~3.3 msg/sec
- ~500 гильдий, средняя гильдия 200 человек
- ~2000 каналов
- ~50 файлов/час

**WebSocket Gateway:**
```
connections = 5 000
memory_per_conn = 268 KiB (zlib)
total_memory = 5000 * 268 KiB = 1.3 GB
pods = ceil(5000 / 50000) = 1  (но для HA минимум 2)
-> 2 pods, 2 GB RAM каждый
```

**PostgreSQL:**
```
messages = 200/мин * 60 * 24 * 30 = ~8.6M/мес
total_messages (6 мес) = ~52M  (< 100M, PostgreSQL достаточно)
connections = 12 сервисов * 2 реплики * 25 conn = 600 (через PgBouncer)
pgbouncer_pool = 40 (к PostgreSQL)
-> 1 primary + 1 replica, 4 vCPU / 16GB RAM
```

**Redis:**
```
sessions = 30K * 500 bytes = 15 MB
presence = 5K * 100 bytes = 500 KB
cache_users = 10K * 1 KB = 10 MB
cache_guilds = 500 * 2 KB = 1 MB
cache_channels = 2K * 500 bytes = 1 MB
cache_permissions = 5K * 100 bytes = 500 KB
rate_limits = 10K * 50 bytes = 500 KB
-> ~30 MB данных, с overhead ~50 MB
-> Sentinel (1 master + 1 replica), 1 GB RAM достаточно
```

**NATS:**
```
message_events = 3.3 msg/sec * 2 (create + deliver) = ~7 events/sec
presence = 5K / 30 sec (heartbeat) = ~170 events/sec
typing = ~10 events/sec
total = ~200 events/sec << 50K threshold
-> 3 ноды, базовая конфигурация
```

**MinIO:**
```
files = 50/час * 24 * 30 = ~36K файлов/мес
avg_size = 2 MB
storage = 36K * 2 MB = ~72 GB/мес
-> Standalone, 500 GB достаточно
```

**Итого (100K пользователей):**

| Компонент | Конфигурация | Стоимость/мес |
|-----------|-------------|---------------|
| K8s (3 ноды, 4 vCPU / 16GB) | Hetzner CX41 | ~$75 |
| PostgreSQL (1P + 1R) | 4 vCPU / 16GB, 200GB SSD | ~$80 |
| Redis (Sentinel) | 2 vCPU / 4GB | ~$20 |
| NATS (3 ноды) | 2 vCPU / 4GB | ~$60 |
| MinIO | 500GB SSD | ~$15 |
| Meilisearch | 2 vCPU / 4GB | ~$20 |
| **Итого** | | **~$270/мес** |

### 10.3. Целевые метрики по этапам

| Метрика | 0--10K | 10K--100K | 100K--1M | 1M+ |
|---------|--------|-----------|----------|-----|
| API latency p99 | < 200ms | < 200ms | < 150ms | < 100ms |
| WS delivery p99 | < 100ms | < 100ms | < 80ms | < 50ms |
| Message create p99 | < 50ms | < 50ms | < 30ms | < 20ms |
| Message read p99 | < 20ms | < 20ms | < 15ms | < 10ms |
| WS connect time | < 2s | < 2s | < 1s | < 500ms |
| Error rate | < 1% | < 0.5% | < 0.1% | < 0.05% |
| Availability | 99.5% | 99.9% | 99.95% | 99.99% |
| Max concurrent WS | 2K | 50K | 200K | 1M+ |
| Messages/sec (write) | 500 | 5K | 20K | 100K+ |

---

## 11. Load Testing

### 11.1. Инструменты

| Инструмент | Назначение | Протокол |
|-----------|-----------|----------|
| **k6** | HTTP API нагрузка | HTTP/REST |
| **Custom Rust client** | WebSocket нагрузка | WebSocket |
| **pgbench** | PostgreSQL нагрузка | SQL |

### 11.2. k6: Message Flood

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    scenarios: {
        message_flood: {
            executor: 'ramping-rate',
            startRate: 10,
            timeUnit: '1s',
            stages: [
                { duration: '1m', target: 100 },
                { duration: '5m', target: 1000 },
                { duration: '5m', target: 5000 },
                { duration: '2m', target: 5000 },
                { duration: '1m', target: 0 },
            ],
            preAllocatedVUs: 500,
            maxVUs: 2000,
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.API_URL || 'https://api.example.com';
const TOKEN = __ENV.TOKEN;

export default function () {
    const channelId = __ENV.CHANNEL_ID || '123456789';
    const payload = JSON.stringify({
        content: `Load test message ${randomString(20)} ${Date.now()}`,
    });

    const res = http.post(
        `${BASE_URL}/channels/${channelId}/messages`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${TOKEN}`,
            },
        }
    );

    check(res, {
        'status is 201': (r) => r.status === 201,
        'latency < 500ms': (r) => r.timings.duration < 500,
    });
}
```

### 11.3. k6: Guild Join Spike

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        guild_join_spike: {
            executor: 'shared-iterations',
            vus: 500,
            iterations: 10000,
            maxDuration: '5m',
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<1000'],
        http_req_failed: ['rate<0.05'],
    },
};

export default function () {
    const inviteCode = __ENV.INVITE_CODE || 'test-invite';
    const res = http.post(
        `${__ENV.API_URL}/invites/${inviteCode}`,
        null,
        {
            headers: {
                'Authorization': `Bearer ${__ENV.TOKEN}`,
            },
        }
    );

    check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    });
}
```

### 11.4. Запуск тестов

```bash
# HTTP load test: message flood
k6 run \
  -e API_URL=https://api.staging.example.com \
  -e TOKEN=$LOAD_TEST_TOKEN \
  -e CHANNEL_ID=123456789 \
  scripts/load-tests/message-flood.js

# HTTP load test: guild join spike
k6 run \
  -e API_URL=https://api.staging.example.com \
  -e TOKEN=$LOAD_TEST_TOKEN \
  -e INVITE_CODE=test-invite \
  scripts/load-tests/guild-join-spike.js

# WebSocket load test (custom Rust client)
cargo run --release -p load-test-ws -- \
  --gateway-url wss://ws.staging.example.com \
  --tokens-file tokens.txt \
  --connections 10000 \
  --ramp-duration 60s \
  --sustain-duration 300s

# PostgreSQL stress test
pgbench -c 100 -j 8 -T 120 -f scripts/load-tests/pg-messages.sql messages_db
```

---

## Источники

- [Discord Engineering -- Why Discord is Switching from Go to Rust](https://discord.com/blog/why-discord-is-switching-from-go-to-rust)
- [Discord Engineering -- How Discord Stores Trillions of Messages](https://discord.com/blog/how-discord-stores-trillions-of-messages)
- [PostgreSQL 17 Documentation -- Warm Standby / Streaming Replication](https://www.postgresql.org/docs/17/warm-standby.html)
- [PostgreSQL 17 Documentation -- Table Partitioning](https://www.postgresql.org/docs/17/ddl-partitioning.html)
- [PostgreSQL Wiki -- Tuning Your PostgreSQL Server](https://wiki.postgresql.org/wiki/Tuning_Your_PostgreSQL_Server)
- [PgBouncer Documentation](https://www.pgbouncer.org/config.html)
- [Patroni Documentation -- HA for PostgreSQL](https://patroni.readthedocs.io/)
- [ScyllaDB Documentation](https://docs.scylladb.com/)
- [Redis Scaling Documentation](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/)
- [Redis Sentinel Documentation](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)
- [Redis Patterns](https://redis.io/docs/latest/develop/use/patterns/)
- [NATS Clustering Documentation](https://docs.nats.io/running-a-nats-service/configuration/clustering)
- [NATS Super-Cluster Documentation](https://docs.nats.io/running-a-nats-service/configuration/super_cluster)
- [NATS Leaf Nodes Documentation](https://docs.nats.io/running-a-nats-service/configuration/leafnodes)
- [MinIO Distributed Mode](https://min.io/docs/minio/linux/operations/install-deploy-manage/deploy-minio-multi-node-multi-drive.html)
- [Kubernetes HPA Documentation](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [k6 Load Testing Documentation](https://k6.io/docs/)
- [moka -- A fast concurrent cache library for Rust](https://docs.rs/moka/latest/moka/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Dashboards Documentation](https://grafana.com/docs/grafana/latest/dashboards/)
