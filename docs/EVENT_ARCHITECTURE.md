# Event-Driven архитектура

Полное руководство по событийной архитектуре платформы: NATS Core, JetStream, стримы, консьюмеры, гарантии доставки, Dead Letter Queue, эволюция схем, мониторинг и паттерны реализации на Rust.

## Источники

- [NATS JetStream](https://docs.nats.io/nats-concepts/jetstream)
- [NATS Core](https://docs.nats.io/nats-concepts/core-nats)
- [JetStream Streams](https://docs.nats.io/nats-concepts/jetstream/streams)
- [JetStream Consumers](https://docs.nats.io/nats-concepts/jetstream/consumers)
- [JetStream Developer Guide](https://docs.nats.io/using-nats/developer/develop_jetstream)
- [JetStream Model Deep Dive](https://docs.nats.io/using-nats/developer/develop_jetstream/model_deep_dive)
- [NATS Monitoring](https://docs.nats.io/running-a-nats-service/nats_admin/monitoring)
- [crate: async-nats](https://docs.rs/async-nats/latest/async_nats/)

---

## Оглавление

1. [NATS Core vs JetStream](#1-nats-core-vs-jetstream)
2. [JetStream Streams конфигурация](#2-jetstream-streams-конфигурация)
3. [Consumer Groups](#3-consumer-groups)
4. [Гарантии доставки](#4-гарантии-доставки)
5. [Dead Letter Queue (DLQ)](#5-dead-letter-queue-dlq)
6. [Эволюция схем событий](#6-эволюция-схем-событий)
7. [Полная карта событий](#7-полная-карта-событий)
8. [Мониторинг](#8-мониторинг)
9. [Паттерны реализации на Rust](#9-паттерны-реализации-на-rust)

---

## 1. NATS Core vs JetStream

Платформа использует две модели взаимодействия через NATS: **Core NATS** для синхронных запросов и **JetStream** для асинхронных событий с гарантиями доставки.

### Core NATS

Core NATS обеспечивает fire-and-forget pub/sub и request/reply паттерны. Сообщения доставляются **at-most-once** — если подписчик не онлайн, сообщение теряется.

**Используется для:**
- **Request/Reply** между API Gateway и микросервисами (`auth.login`, `guilds.create`, `users.get`)
- **Typing indicators** (`typing.{guild_id}.{channel_id}`) — потеря одного события typing не критична
- **Heartbeat** от Gateway к Presence Service

### JetStream

JetStream добавляет персистентность, replay, consumer groups и подтверждения. Сообщения сохраняются в стримы и доставляются **at-least-once**.

**Используется для:**
- Все бизнес-события (`messages.*.*.created`, `guild.*.member.joined`, `user.*.updated`)
- События, требующие надёжной доставки нескольким консьюмерам (Gateway, Notifications, Search)
- События, требующие replay при resume (WebSocket Gateway)

### Таблица решений

| Критерий | Core NATS | JetStream |
|----------|-----------|-----------|
| **Гарантия доставки** | At-most-once | At-least-once |
| **Персистентность** | Нет | Да (диск/память) |
| **Replay** | Нет | Да (по sequence, по времени) |
| **Consumer groups** | Queue groups (простые) | Durable consumers (с ACK) |
| **Latency** | Минимальная (~0.1ms) | Чуть выше (~0.5-1ms) |
| **Backpressure** | Нет | Да (MaxAckPending) |
| **Порядок** | Не гарантирован | Гарантирован в рамках subject |
| **Когда использовать** | Request/reply, typing, heartbeat | Бизнес-события, индексация, уведомления |

### Примеры использования в проекте

| Паттерн | Транспорт | Subject | Описание |
|---------|-----------|---------|----------|
| Request/Reply | Core NATS | `auth.login` | API Gateway запрашивает аутентификацию |
| Request/Reply | Core NATS | `guilds.create` | API Gateway создаёт гильдию |
| Request/Reply | Core NATS | `users.get` | API Gateway получает профиль |
| Fire-and-forget | Core NATS | `typing.{guild_id}.{channel_id}` | Typing indicator (TTL 10 сек) |
| Persistent event | JetStream | `messages.{guild_id}.{channel_id}.created` | Новое сообщение |
| Persistent event | JetStream | `guild.{guild_id}.member.joined` | Участник вступил |
| Persistent event | JetStream | `presence.{user_id}.updated` | Изменение статуса |
| Persistent event | JetStream | `media.uploaded` | Файл загружен |

---

## 2. JetStream Streams конфигурация

### Обзор стримов

Каждый стрим группирует связанные subjects и определяет политику хранения, TTL и репликацию.

| Стрим | Subjects | Назначение |
|-------|----------|------------|
| `MESSAGES` | `messages.>` | Все события сообщений: создание, обновление, удаление, реакции, пины |
| `GUILD_EVENTS` | `guild.>` | События гильдий: каналы, роли, участники, инвайты, баны |
| `USER_EVENTS` | `user.>` | Профили пользователей, друзья, удаление аккаунтов |
| `PRESENCE` | `presence.>`, `typing.>` | Онлайн-статус, typing indicators |
| `MEDIA` | `media.>` | Загрузка и удаление файлов |
| `MODERATION` | `moderation.>` | Автомодерация, антирейд |

### Конфигурация стримов

#### MESSAGES

Основной стрим для событий сообщений. Высокий throughput, средний retention.

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Subjects | `messages.>` | Все message events |
| Retention | `Limits` | Удаление по возрасту/размеру |
| Max Age | 7 дней | Достаточно для replay при resume Gateway |
| Max Bytes | 50 GB | Ограничение размера стрима |
| Storage | `File` | Персистентность на диске |
| Replicas | 3 | Отказоустойчивость (NATS кластер 3+ нод) |
| Discard | `Old` | При превышении лимита удаляются старые сообщения |
| Deduplication Window | 2 минуты | Для idempotency по `Nats-Msg-Id` |
| Max Msg Size | 1 MB | Ограничение размера одного события |

#### GUILD_EVENTS

События гильдий: каналы, роли, участники, бан.

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Subjects | `guild.>` | Все guild events |
| Retention | `Limits` | По возрасту/размеру |
| Max Age | 3 дня | Guild события реже, не нужен длинный retention |
| Max Bytes | 10 GB | Guild события компактнее message events |
| Storage | `File` | Персистентность |
| Replicas | 3 | Отказоустойчивость |
| Discard | `Old` | Стандартная политика |
| Deduplication Window | 2 минуты | Idempotency |

#### USER_EVENTS

Профили пользователей, друзья, удаление аккаунтов.

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Subjects | `user.>` | Все user events |
| Retention | `Limits` | По возрасту/размеру |
| Max Age | 3 дня | Аналогично guild events |
| Max Bytes | 5 GB | User events ещё компактнее |
| Storage | `File` | Персистентность |
| Replicas | 3 | Отказоустойчивость |
| Discard | `Old` | Стандартная политика |
| Deduplication Window | 2 минуты | Idempotency |

#### PRESENCE

Онлайн-статусы и typing. Высокая частота, короткий retention.

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Subjects | `presence.>`, `typing.>` | Presence + typing |
| Retention | `Limits` | По возрасту |
| Max Age | 1 час | Presence — эфемерные данные, typing истекает за 10 сек |
| Max Bytes | 2 GB | Маленькие payload-ы, высокая частота |
| Storage | `Memory` | Максимальная скорость, потеря при рестарте допустима |
| Replicas | 1 | Потеря presence данных некритична — восстановятся |
| Discard | `Old` | Стандартная политика |
| Deduplication Window | 30 секунд | Короткое окно для быстрых событий |

#### MEDIA

Загрузка и удаление файлов. Низкий throughput, нужна гарантия.

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Subjects | `media.>` | Media events |
| Retention | `Limits` | По возрасту/размеру |
| Max Age | 7 дней | Достаточно для обработки |
| Max Bytes | 5 GB | Мало событий, payload без бинарных данных |
| Storage | `File` | Персистентность |
| Replicas | 3 | Отказоустойчивость |
| Discard | `Old` | Стандартная политика |
| Deduplication Window | 5 минут | Upload может ретраиться дольше |

#### MODERATION

Автомодерация, антирейд. Низкий throughput, высокая важность.

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Subjects | `moderation.>` | Moderation events |
| Retention | `Limits` | По возрасту/размеру |
| Max Age | 30 дней | Модерация нужна для аудита |
| Max Bytes | 5 GB | Редкие события |
| Storage | `File` | Персистентность, аудит |
| Replicas | 3 | Отказоустойчивость |
| Discard | `Old` | Стандартная политика |
| Deduplication Window | 2 минуты | Стандартное окно |

### Rust-код создания стримов

```rust
use async_nats::jetstream::{self, stream};

/// Создание всех JetStream стримов при старте платформы.
/// Вызывается из init-контейнера или отдельного setup-сервиса.
pub async fn create_streams(
    js: &jetstream::Context,
) -> Result<(), async_nats::Error> {
    // MESSAGES stream
    js.get_or_create_stream(stream::Config {
        name: "MESSAGES".to_string(),
        subjects: vec!["messages.>".to_string()],
        retention: stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(7 * 24 * 60 * 60), // 7 дней
        max_bytes: 50 * 1024 * 1024 * 1024,                        // 50 GB
        storage: stream::StorageType::File,
        num_replicas: 3,
        discard: stream::DiscardPolicy::Old,
        duplicate_window: std::time::Duration::from_secs(120),      // 2 минуты
        max_message_size: 1024 * 1024,                              // 1 MB
        ..Default::default()
    })
    .await?;

    // GUILD_EVENTS stream
    js.get_or_create_stream(stream::Config {
        name: "GUILD_EVENTS".to_string(),
        subjects: vec!["guild.>".to_string()],
        retention: stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(3 * 24 * 60 * 60), // 3 дня
        max_bytes: 10 * 1024 * 1024 * 1024,                        // 10 GB
        storage: stream::StorageType::File,
        num_replicas: 3,
        discard: stream::DiscardPolicy::Old,
        duplicate_window: std::time::Duration::from_secs(120),
        ..Default::default()
    })
    .await?;

    // USER_EVENTS stream
    js.get_or_create_stream(stream::Config {
        name: "USER_EVENTS".to_string(),
        subjects: vec!["user.>".to_string()],
        retention: stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(3 * 24 * 60 * 60), // 3 дня
        max_bytes: 5 * 1024 * 1024 * 1024,                         // 5 GB
        storage: stream::StorageType::File,
        num_replicas: 3,
        discard: stream::DiscardPolicy::Old,
        duplicate_window: std::time::Duration::from_secs(120),
        ..Default::default()
    })
    .await?;

    // PRESENCE stream (in-memory для скорости)
    js.get_or_create_stream(stream::Config {
        name: "PRESENCE".to_string(),
        subjects: vec![
            "presence.>".to_string(),
            "typing.>".to_string(),
        ],
        retention: stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(60 * 60),          // 1 час
        max_bytes: 2 * 1024 * 1024 * 1024,                         // 2 GB
        storage: stream::StorageType::Memory,
        num_replicas: 1,
        discard: stream::DiscardPolicy::Old,
        duplicate_window: std::time::Duration::from_secs(30),
        ..Default::default()
    })
    .await?;

    // MEDIA stream
    js.get_or_create_stream(stream::Config {
        name: "MEDIA".to_string(),
        subjects: vec!["media.>".to_string()],
        retention: stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(7 * 24 * 60 * 60), // 7 дней
        max_bytes: 5 * 1024 * 1024 * 1024,                         // 5 GB
        storage: stream::StorageType::File,
        num_replicas: 3,
        discard: stream::DiscardPolicy::Old,
        duplicate_window: std::time::Duration::from_secs(300),      // 5 минут
        ..Default::default()
    })
    .await?;

    // MODERATION stream (длинный retention для аудита)
    js.get_or_create_stream(stream::Config {
        name: "MODERATION".to_string(),
        subjects: vec!["moderation.>".to_string()],
        retention: stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(30 * 24 * 60 * 60), // 30 дней
        max_bytes: 5 * 1024 * 1024 * 1024,                          // 5 GB
        storage: stream::StorageType::File,
        num_replicas: 3,
        discard: stream::DiscardPolicy::Old,
        duplicate_window: std::time::Duration::from_secs(120),
        ..Default::default()
    })
    .await?;

    tracing::info!("All JetStream streams created successfully");
    Ok(())
}
```

---

## 3. Consumer Groups

Каждый сервис-подписчик имеет **durable consumer** на нужном стриме. Durable consumer сохраняет позицию чтения — при рестарте сервиса обработка продолжится с последнего неподтверждённого сообщения.

### Соглашение об именовании

```
{service_name}_{stream_name}_consumer
```

Примеры:
- `gateway_messages_consumer` — Gateway подписан на стрим MESSAGES
- `notifications_messages_consumer` — Notification Service подписан на стрим MESSAGES
- `search_messages_consumer` — Search Service подписан на стрим MESSAGES
- `search_guild_events_consumer` — Search Service подписан на стрим GUILD_EVENTS
- `search_user_events_consumer` — Search Service подписан на стрим USER_EVENTS
- `notifications_guild_events_consumer` — Notification Service подписан на стрим GUILD_EVENTS
- `notifications_user_events_consumer` — Notification Service подписан на стрим USER_EVENTS
- `messages_guild_events_consumer` — Message Service подписан на стрим GUILD_EVENTS (каскадное удаление)
- `moderation_messages_consumer` — Moderation Service подписан на стрим MESSAGES (автомодерация)
- `gateway_guild_events_consumer` — Gateway подписан на стрим GUILD_EVENTS
- `gateway_presence_consumer` — Gateway подписан на стрим PRESENCE

### Параметры консьюмеров

| Параметр | Значение | Описание |
|----------|----------|----------|
| **Durable** | Да | Позиция сохраняется при отключении |
| **AckPolicy** | `Explicit` | Сообщение считается обработанным только после явного ACK |
| **AckWait** | 30 секунд | Время ожидания ACK до redelivery |
| **MaxDeliver** | 5 | Максимум попыток доставки (после — в DLQ) |
| **MaxAckPending** | 1000 | Максимум неподтверждённых сообщений (backpressure) |
| **DeliverPolicy** | `All` (новый) / `Last` (Gateway resume) | Откуда начать чтение |
| **FilterSubject** | По необходимости | Фильтрация subjects внутри стрима |

### Rust-код создания консьюмера

```rust
use async_nats::jetstream::consumer::{self, pull};

/// Создание durable pull consumer для сервиса.
pub async fn create_consumer(
    js: &async_nats::jetstream::Context,
    stream_name: &str,
    consumer_name: &str,
    filter_subject: Option<&str>,
) -> Result<pull::Consumer, async_nats::Error> {
    let stream = js.get_stream(stream_name).await?;

    let mut config = pull::Config {
        durable_name: Some(consumer_name.to_string()),
        ack_policy: consumer::AckPolicy::Explicit,
        ack_wait: std::time::Duration::from_secs(30),
        max_deliver: 5,
        max_ack_pending: 1000,
        deliver_policy: consumer::DeliverPolicy::All,
        ..Default::default()
    };

    if let Some(filter) = filter_subject {
        config.filter_subject = filter.to_string();
    }

    let consumer = stream
        .get_or_create_consumer(consumer_name, config)
        .await?;

    tracing::info!(
        stream = stream_name,
        consumer = consumer_name,
        "Consumer created/connected"
    );

    Ok(consumer)
}
```

### Пример создания всех консьюмеров для Search Service

```rust
pub async fn setup_search_consumers(
    js: &async_nats::jetstream::Context,
) -> Result<(), async_nats::Error> {
    // Подписка на события сообщений (индексация)
    create_consumer(
        js,
        "MESSAGES",
        "search_messages_consumer",
        Some("messages.>"),
    )
    .await?;

    // Подписка на события гильдий (индексация серверов)
    create_consumer(
        js,
        "GUILD_EVENTS",
        "search_guild_events_consumer",
        Some("guild.>"),
    )
    .await?;

    // Подписка на события пользователей (индексация профилей)
    create_consumer(
        js,
        "USER_EVENTS",
        "search_user_events_consumer",
        Some("user.>"),
    )
    .await?;

    Ok(())
}
```

### Горизонтальное масштабирование консьюмеров

Несколько реплик одного сервиса могут читать из одного durable consumer. NATS автоматически распределяет сообщения между подписчиками — каждое сообщение обрабатывается **ровно одной** репликой.

```
Search Service Pod 1 ──┐
Search Service Pod 2 ──┤── search_messages_consumer (durable)
Search Service Pod 3 ──┘
                         ↑
                    MESSAGES stream
```

Для Gateway ситуация другая: каждый pod получает **все** события (fan-out), потому что каждый pod обслуживает свои шарды. Для этого Gateway использует **отдельный consumer на каждый pod** или Core NATS подписки.

---

## 4. Гарантии доставки

### At-Least-Once доставка

JetStream гарантирует, что каждое сообщение будет доставлено **хотя бы один раз**. Это означает, что при сбоях сообщение может быть доставлено повторно. Поэтому все обработчики событий должны быть **идемпотентными**.

**Механизм:**

```
Publisher → JetStream Stream → Consumer (pull)
                                    │
                                    ├── Получает сообщение
                                    ├── Обрабатывает
                                    ├── Отправляет ACK
                                    │
                                    └── Если ACK не пришёл за AckWait (30 сек):
                                        └── NATS повторно доставляет (до MaxDeliver раз)
```

### Подтверждение (ACK)

```rust
use async_nats::jetstream::Message;

async fn process_message(msg: Message) -> Result<(), AppError> {
    // Обработка события
    let event: Event = serde_json::from_slice(&msg.payload)?;
    handle_event(event).await?;

    // Явное подтверждение — сообщение удаляется из pending
    msg.ack().await.map_err(|e| {
        tracing::error!(error = %e, "Failed to ACK message");
        AppError::Internal(e.into())
    })?;

    Ok(())
}
```

### Типы подтверждений

| Тип | Метод | Когда использовать |
|-----|-------|--------------------|
| `ack()` | Подтвердить обработку | Успешная обработка |
| `nak()` | Отклонить (немедленный redelivery) | Временная ошибка, хотим повторить сразу |
| `nak_with_delay(Duration)` | Отклонить с задержкой | Временная ошибка, но нужна пауза |
| `in_progress()` | Продлить AckWait | Долгая обработка, нужно больше времени |
| `term()` | Терминировать (больше не пытаться) | Невалидное сообщение, нет смысла повторять |

### Idempotency (идемпотентность)

Каждое сообщение публикуется с уникальным идентификатором в заголовке `Nats-Msg-Id`. JetStream использует его для дедупликации в рамках `duplicate_window`.

#### Публикация с Nats-Msg-Id

```rust
use async_nats::HeaderMap;

pub async fn publish_with_dedup(
    js: &async_nats::jetstream::Context,
    subject: &str,
    event: &Event,
    dedup_id: &str,
) -> Result<(), async_nats::Error> {
    let payload = serde_json::to_vec(event)?;

    let mut headers = HeaderMap::new();
    headers.insert("Nats-Msg-Id", dedup_id);

    js.publish_with_headers(subject.to_string(), headers, payload.into())
        .await?
        .await?;

    Ok(())
}
```

**Формат dedup_id:**

```rust
/// Генерация idempotency key для события.
/// Формат: {event_type}:{entity_id}:{timestamp_ms}
pub fn make_dedup_id(event_type: &str, entity_id: i64) -> String {
    let ts = chrono::Utc::now().timestamp_millis();
    format!("{event_type}:{entity_id}:{ts}")
}
```

Пример: `MessageCreated:1234567890:1740153600000`

#### Идемпотентная обработка на стороне консьюмера

Даже с дедупликацией на уровне NATS, консьюмер должен быть готов к повторной обработке (если дедупликационное окно истекло или произошёл redelivery после сбоя):

```rust
/// Идемпотентная обработка события.
/// Использует Redis для отслеживания обработанных событий.
pub async fn handle_event_idempotent(
    redis: &TypedCache,
    msg: &async_nats::jetstream::Message,
    handler: impl AsyncFnOnce(&Event) -> Result<(), AppError>,
) -> Result<(), AppError> {
    // Извлечь Nats-Msg-Id из заголовков
    let msg_id = msg
        .headers
        .as_ref()
        .and_then(|h| h.get("Nats-Msg-Id"))
        .map(|v| v.to_string())
        .unwrap_or_default();

    if msg_id.is_empty() {
        // Нет idempotency key — обработать без проверки
        let event: Event = serde_json::from_slice(&msg.payload)?;
        handler(&event).await?;
        msg.ack().await?;
        return Ok(());
    }

    // Проверить, обработано ли уже
    let cache_key = format!("event:processed:{msg_id}");
    if redis.exists(&cache_key).await? {
        tracing::debug!(msg_id = %msg_id, "Duplicate event, skipping");
        msg.ack().await?;
        return Ok(());
    }

    // Обработать
    let event: Event = serde_json::from_slice(&msg.payload)?;
    handler(&event).await?;

    // Отметить как обработанное (TTL = duplicate_window * 2)
    redis.set(&cache_key, &true, 300).await?;

    // Подтвердить
    msg.ack().await?;

    Ok(())
}
```

### Deduplication Window

Настраивается per-stream. JetStream автоматически отклоняет сообщения с одинаковым `Nats-Msg-Id` в рамках окна:

| Стрим | Deduplication Window | Обоснование |
|-------|---------------------|-------------|
| MESSAGES | 2 минуты | Стандартное окно |
| GUILD_EVENTS | 2 минуты | Стандартное окно |
| USER_EVENTS | 2 минуты | Стандартное окно |
| PRESENCE | 30 секунд | Короткие события, частые обновления |
| MEDIA | 5 минут | Upload может быть медленным |
| MODERATION | 2 минуты | Стандартное окно |

---

## 5. Dead Letter Queue (DLQ)

### Механизм

Когда количество попыток доставки сообщения превышает `MaxDeliver` (5 попыток), NATS JetStream публикует advisory event и прекращает доставку. Для отслеживания таких сообщений используется DLQ-паттерн.

### Advisory subjects

NATS публикует advisory события в специальные subjects:

| Subject | Описание |
|---------|----------|
| `$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.{stream}.{consumer}` | Достигнут MaxDeliver для сообщения |
| `$JS.EVENT.ADVISORY.STREAM.MSG_DENIED.{stream}` | Сообщение отклонено стримом |

### Реализация DLQ через advisory подписку

```rust
use async_nats::jetstream;

/// DLQ handler — слушает advisory events о превышении MaxDeliver
/// и сохраняет проблемные сообщения в отдельный стрим для анализа.
pub async fn start_dlq_handler(
    client: &async_nats::Client,
    js: &jetstream::Context,
) -> Result<(), async_nats::Error> {
    // Создать стрим для DLQ
    js.get_or_create_stream(async_nats::jetstream::stream::Config {
        name: "DLQ".to_string(),
        subjects: vec!["dlq.>".to_string()],
        retention: async_nats::jetstream::stream::RetentionPolicy::Limits,
        max_age: std::time::Duration::from_secs(30 * 24 * 60 * 60), // 30 дней
        max_bytes: 1024 * 1024 * 1024,                               // 1 GB
        storage: async_nats::jetstream::stream::StorageType::File,
        num_replicas: 3,
        ..Default::default()
    })
    .await?;

    // Подписаться на advisory events о MaxDeliver
    let mut sub = client
        .subscribe("$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.>")
        .await?;

    tracing::info!("DLQ handler started, listening for max delivery advisories");

    while let Some(msg) = sub.next().await {
        if let Err(e) = handle_dlq_advisory(js, &msg).await {
            tracing::error!(error = %e, "Failed to handle DLQ advisory");
        }
    }

    Ok(())
}

async fn handle_dlq_advisory(
    js: &jetstream::Context,
    msg: &async_nats::Message,
) -> Result<(), async_nats::Error> {
    // Парсим advisory payload
    let advisory: serde_json::Value = serde_json::from_slice(&msg.payload)?;

    let stream_name = advisory["stream"].as_str().unwrap_or("unknown");
    let consumer_name = advisory["consumer"].as_str().unwrap_or("unknown");
    let stream_seq = advisory["stream_seq"].as_u64().unwrap_or(0);

    tracing::warn!(
        stream = stream_name,
        consumer = consumer_name,
        seq = stream_seq,
        "Message exceeded max deliveries, moving to DLQ"
    );

    // Получить оригинальное сообщение из стрима по sequence
    let stream = js.get_stream(stream_name).await?;
    if let Ok(raw_msg) = stream.get_raw_message(stream_seq).await {
        // Опубликовать в DLQ стрим с метаданными
        let dlq_subject = format!("dlq.{stream_name}.{consumer_name}");

        let mut headers = async_nats::HeaderMap::new();
        headers.insert("DLQ-Original-Stream", stream_name);
        headers.insert("DLQ-Original-Consumer", consumer_name);
        headers.insert("DLQ-Original-Seq", &stream_seq.to_string());
        headers.insert("DLQ-Original-Subject", &raw_msg.subject);
        headers.insert(
            "DLQ-Timestamp",
            &chrono::Utc::now().to_rfc3339(),
        );

        js.publish_with_headers(dlq_subject, headers, raw_msg.payload)
            .await?
            .await?;

        // Метрика
        metrics::counter!("nats_dlq_messages_total", "stream" => stream_name.to_string()).increment(1);
    }

    Ok(())
}
```

### Мониторинг DLQ

| Метрика | Тип | Описание |
|---------|-----|----------|
| `nats_dlq_messages_total{stream}` | counter | Сообщений попавших в DLQ по стриму |
| `nats_dlq_pending` | gauge | Необработанных сообщений в DLQ |
| `nats_dlq_replay_total{stream}` | counter | Переигранных из DLQ |

**Алерты:**
- `nats_dlq_messages_total` rate > 0 за 5 минут: warning (появились сообщения в DLQ)
- `nats_dlq_pending` > 100: critical (накапливаются необработанные)

### Ручной replay из DLQ

```rust
/// Переиграть сообщения из DLQ обратно в оригинальный стрим.
pub async fn replay_dlq(
    js: &jetstream::Context,
    stream_name: &str,
    consumer_name: &str,
    max_messages: usize,
) -> Result<usize, async_nats::Error> {
    let dlq_stream = js.get_stream("DLQ").await?;
    let dlq_consumer_name = format!("replay_{stream_name}_{consumer_name}");

    let consumer = dlq_stream
        .get_or_create_consumer(
            &dlq_consumer_name,
            async_nats::jetstream::consumer::pull::Config {
                filter_subject: format!("dlq.{stream_name}.{consumer_name}"),
                ack_policy: async_nats::jetstream::consumer::AckPolicy::Explicit,
                ..Default::default()
            },
        )
        .await?;

    let mut messages = consumer.fetch().max_messages(max_messages).messages().await?;
    let mut replayed = 0;

    while let Some(Ok(msg)) = messages.next().await {
        // Извлечь оригинальный subject из заголовков
        let original_subject = msg
            .headers
            .as_ref()
            .and_then(|h| h.get("DLQ-Original-Subject"))
            .map(|v| v.to_string())
            .unwrap_or_default();

        if !original_subject.is_empty() {
            // Переопубликовать в оригинальный subject
            js.publish(original_subject, msg.payload.clone())
                .await?
                .await?;
            msg.ack().await?;
            replayed += 1;
        } else {
            msg.term().await?; // Невалидное DLQ сообщение
        }
    }

    tracing::info!(
        stream = stream_name,
        consumer = consumer_name,
        count = replayed,
        "Replayed messages from DLQ"
    );

    Ok(replayed)
}
```

---

## 6. Эволюция схем событий

### Версионирование

Каждое событие содержит поле `version` в payload:

```rust
#[derive(Debug, Serialize, Deserialize)]
pub struct EventEnvelope {
    /// Версия схемы события
    pub version: u32,
    /// Тип события (используется serde tag)
    #[serde(flatten)]
    pub event: Event,
    /// Timestamp публикации
    pub published_at: chrono::DateTime<chrono::Utc>,
}
```

### Правила обратной совместимости

**Допустимые изменения (backward compatible):**
- Добавление нового опционального поля (`Option<T>`)
- Добавление нового варианта в `Event` enum
- Добавление нового subject в стрим

**Недопустимые изменения (breaking):**
- Удаление поля
- Переименование поля
- Изменение типа поля
- Изменение семантики существующего поля

### Стратегия breaking changes

При необходимости несовместимых изменений используется версионирование subjects:

```
messages.{guild_id}.{channel_id}.created       ← v1 (текущий)
messages.v2.{guild_id}.{channel_id}.created    ← v2 (новая версия)
```

**Миграция через dual-publish:**

```
Фаза 1: Publisher пишет в v1 и v2 одновременно
         Consumers всё ещё читают v1

Фаза 2: Consumers переключаются на v2
         Publisher всё ещё пишет в v1 и v2

Фаза 3: Publisher прекращает писать в v1
         Старый subject удаляется из стрима
```

### Rust-код dual-publish

```rust
/// Publisher с поддержкой dual-publish для миграции версий.
pub async fn publish_with_migration(
    js: &async_nats::jetstream::Context,
    subject_v1: &str,
    subject_v2: Option<&str>,
    event_v1: &Event,
    event_v2: Option<&Event>,
) -> Result<(), async_nats::Error> {
    let payload_v1 = serde_json::to_vec(&EventEnvelope {
        version: 1,
        event: event_v1.clone(),
        published_at: chrono::Utc::now(),
    })?;

    js.publish(subject_v1.to_string(), payload_v1.into())
        .await?
        .await?;

    // Dual-publish в v2 если активна миграция
    if let (Some(subj), Some(evt)) = (subject_v2, event_v2) {
        let payload_v2 = serde_json::to_vec(&EventEnvelope {
            version: 2,
            event: evt.clone(),
            published_at: chrono::Utc::now(),
        })?;

        js.publish(subj.to_string(), payload_v2.into())
            .await?
            .await?;
    }

    Ok(())
}
```

### Обработка неизвестных версий

```rust
fn handle_event_versioned(envelope: &EventEnvelope) -> Result<(), AppError> {
    match envelope.version {
        1 => handle_v1(&envelope.event),
        2 => handle_v2(&envelope.event),
        unknown => {
            tracing::warn!(version = unknown, "Unknown event version, skipping");
            // Не nak — просто ACK и пропустить
            Ok(())
        }
    }
}
```

---

## 7. Полная карта событий

### Сообщения (стрим MESSAGES)

| Событие (Event enum) | Subject | Publisher | Consumers | Гарантия |
|----------------------|---------|-----------|-----------|----------|
| `MessageCreated` | `messages.{guild_id}.{channel_id}.created` | Message Service | Gateway, Notifications, Search, Moderation | At-least-once |
| `MessageUpdated` | `messages.{guild_id}.{channel_id}.updated` | Message Service | Gateway, Search | At-least-once |
| `MessageDeleted` | `messages.{guild_id}.{channel_id}.deleted` | Message Service | Gateway, Search | At-least-once |
| `MessageBulkDeleted` | `messages.{guild_id}.{channel_id}.bulk_deleted` | Message Service | Gateway, Search | At-least-once |
| `MessagePinned` | `messages.{guild_id}.{channel_id}.pins_update` | Message Service | Gateway | At-least-once |
| `MessageUnpinned` | `messages.{guild_id}.{channel_id}.pins_update` | Message Service | Gateway | At-least-once |
| `ReactionAdded` | `messages.{guild_id}.{channel_id}.reaction_added` | Message Service | Gateway, Notifications | At-least-once |
| `ReactionRemoved` | `messages.{guild_id}.{channel_id}.reaction_removed` | Message Service | Gateway | At-least-once |
| `ReactionsRemovedAll` | `messages.{guild_id}.{channel_id}.reactions_removed_all` | Message Service | Gateway | At-least-once |
| `ReactionsRemovedEmoji` | `messages.{guild_id}.{channel_id}.reactions_removed_emoji` | Message Service | Gateway | At-least-once |

### Гильдии (стрим GUILD_EVENTS)

| Событие (Event enum) | Subject | Publisher | Consumers | Гарантия |
|----------------------|---------|-----------|-----------|----------|
| `GuildCreated` | `guild.created` | Guild Service | Gateway, Search | At-least-once |
| `GuildUpdated` | `guild.{guild_id}.updated` | Guild Service | Gateway, Search | At-least-once |
| `GuildDeleted` | `guild.{guild_id}.deleted` | Guild Service | Gateway, Search, Messages | At-least-once |
| `ChannelCreated` | `guild.{guild_id}.channel.created` | Guild Service | Gateway | At-least-once |
| `ChannelUpdated` | `guild.{guild_id}.channel.updated` | Guild Service | Gateway | At-least-once |
| `ChannelDeleted` | `guild.{guild_id}.channel.deleted` | Guild Service | Gateway, Messages | At-least-once |
| `MemberJoined` | `guild.{guild_id}.member.joined` | Guild Service | Gateway, Notifications | At-least-once |
| `MemberLeft` | `guild.{guild_id}.member.left` | Guild Service | Gateway | At-least-once |
| `MemberUpdated` | `guild.{guild_id}.member.updated` | Guild Service | Gateway | At-least-once |
| `MemberBanned` | `guild.{guild_id}.member.banned` | Guild Service | Gateway, Messages, Notifications | At-least-once |
| `MemberUnbanned` | `guild.{guild_id}.member.unbanned` | Guild Service | Gateway | At-least-once |
| `MemberTimedOut` | `guild.{guild_id}.member.timed_out` | Guild Service | Gateway | At-least-once |
| `RoleCreated` | `guild.{guild_id}.role.created` | Guild Service | Gateway | At-least-once |
| `RoleUpdated` | `guild.{guild_id}.role.updated` | Guild Service | Gateway | At-least-once |
| `RoleDeleted` | `guild.{guild_id}.role.deleted` | Guild Service | Gateway | At-least-once |
| `InviteCreated` | `guild.{guild_id}.invite.created` | Guild Service | Gateway | At-least-once |
| `InviteDeleted` | `guild.{guild_id}.invite.deleted` | Guild Service | Gateway | At-least-once |
| `GuildEmojisUpdated` | `guild.{guild_id}.emojis.updated` | Guild Service | Gateway | At-least-once |
| `WebhooksUpdated` | `guild.{guild_id}.webhooks.updated` | Guild Service | Gateway | At-least-once |

### Пользователи (стрим USER_EVENTS)

| Событие (Event enum) | Subject | Publisher | Consumers | Гарантия |
|----------------------|---------|-----------|-----------|----------|
| `UserUpdated` | `user.{user_id}.updated` | User Service | Gateway, Search | At-least-once |
| `UserDeleted` | `user.deleted` | User Service | Search, Messages | At-least-once |

### Presence (стрим PRESENCE)

| Событие (Event enum) | Subject | Publisher | Consumers | Гарантия |
|----------------------|---------|-----------|-----------|----------|
| `PresenceUpdated` | `presence.{user_id}.updated` | Presence Service | Gateway, Notifications | At-least-once |
| `TypingStarted` | `typing.{guild_id}.{channel_id}` | Presence Service | Gateway | At-most-once* |

*Typing events доставляются через JetStream стрим PRESENCE (in-memory), но потеря не критична.

### Медиа (стрим MEDIA)

| Событие (Event enum) | Subject | Publisher | Consumers | Гарантия |
|----------------------|---------|-----------|-----------|----------|
| `MediaUploaded` | `media.uploaded` | Media Service | — | At-least-once |
| `MediaDeleted` | `media.deleted` | Media Service | — | At-least-once |

### Модерация (стрим MODERATION)

| Событие (Event enum) | Subject | Publisher | Consumers | Гарантия |
|----------------------|---------|-----------|-----------|----------|
| `AutoModActionExecuted` | `moderation.{guild_id}.automod.action` | Moderation Service | Gateway | At-least-once |

### Матрица подписок по сервисам

| Сервис | Стримы (подписки) | Фильтры subjects |
|--------|-------------------|-------------------|
| **Gateway** | MESSAGES, GUILD_EVENTS, USER_EVENTS, PRESENCE | `messages.>`, `guild.>`, `user.>`, `presence.>`, `typing.>` |
| **Notifications** | MESSAGES, GUILD_EVENTS, USER_EVENTS, PRESENCE | `messages.*.*.created`, `guild.*.member.*`, `user.friend.*`, `presence.*.updated` |
| **Search** | MESSAGES, GUILD_EVENTS, USER_EVENTS | `messages.>`, `guild.created`, `guild.*.updated`, `guild.*.deleted`, `user.*.updated`, `user.deleted` |
| **Messages** | GUILD_EVENTS | `guild.*.channel.deleted`, `guild.*.member.banned`, `user.deleted` |
| **Moderation** | MESSAGES | `messages.*.*.created` (для автомодерации) |

---

## 8. Мониторинг

### NATS Server метрики

NATS предоставляет HTTP endpoint для мониторинга: `http://nats-server:8222/varz`, `/jsz`, `/connz`.

**Prometheus integration:** используется [NATS Prometheus exporter](https://github.com/nats-io/prometheus-nats-exporter) для сбора метрик.

### Ключевые метрики

| Метрика | Тип | Описание | Warning | Critical |
|---------|-----|----------|---------|----------|
| `nats_jetstream_stream_messages{stream}` | gauge | Количество сообщений в стриме | — | — |
| `nats_jetstream_stream_bytes{stream}` | gauge | Размер стрима в байтах | > 80% max_bytes | > 95% max_bytes |
| `nats_jetstream_consumer_num_pending{consumer}` | gauge | Pending (необработанные) сообщения | > 10000 | > 50000 |
| `nats_jetstream_consumer_num_ack_pending{consumer}` | gauge | Ожидающие ACK | > 500 | > 900 (из 1000) |
| `nats_jetstream_consumer_num_redelivered{consumer}` | counter | Количество redelivery | rate > 10/min | rate > 100/min |
| `nats_server_connections` | gauge | Активные соединения | > 80% max | > 95% max |
| `nats_server_slow_consumers` | counter | Медленные консьюмеры | rate > 0 | rate > 10/min |
| `nats_dlq_messages_total{stream}` | counter | Сообщений в DLQ | rate > 0 | rate > 10/min |

### Consumer Lag (отставание консьюмера)

Consumer lag — количество сообщений в стриме, которые ещё не обработаны консьюмером. Критическая метрика для обнаружения узких мест.

```rust
/// Сбор метрик consumer lag для Prometheus.
pub async fn collect_consumer_lag(
    js: &async_nats::jetstream::Context,
) -> Result<(), async_nats::Error> {
    let streams = ["MESSAGES", "GUILD_EVENTS", "USER_EVENTS", "PRESENCE", "MEDIA", "MODERATION"];

    for stream_name in &streams {
        let stream = match js.get_stream(*stream_name).await {
            Ok(s) => s,
            Err(_) => continue,
        };

        let info = stream.info().await?;
        let stream_messages = info.state.messages;

        metrics::gauge!(
            "nats_stream_messages",
            "stream" => stream_name.to_string()
        )
        .set(stream_messages as f64);

        metrics::gauge!(
            "nats_stream_bytes",
            "stream" => stream_name.to_string()
        )
        .set(info.state.bytes as f64);

        // Собрать lag для каждого консьюмера стрима
        let mut consumers = stream.consumers();
        while let Some(Ok(consumer_info)) = consumers.next().await {
            let pending = consumer_info.num_pending;
            let ack_pending = consumer_info.num_ack_pending;

            metrics::gauge!(
                "nats_consumer_lag",
                "stream" => stream_name.to_string(),
                "consumer" => consumer_info.name.clone()
            )
            .set(pending as f64);

            metrics::gauge!(
                "nats_consumer_ack_pending",
                "stream" => stream_name.to_string(),
                "consumer" => consumer_info.name.clone()
            )
            .set(ack_pending as f64);

            metrics::gauge!(
                "nats_consumer_redelivered",
                "stream" => stream_name.to_string(),
                "consumer" => consumer_info.name.clone()
            )
            .set(consumer_info.num_redelivered as f64);
        }
    }

    Ok(())
}
```

### Advisory subjects

NATS публикует системные события в advisory subjects. Полезно для мониторинга здоровья системы.

| Advisory Subject | Описание | Действие |
|-----------------|----------|----------|
| `$JS.EVENT.ADVISORY.STREAM.CREATED.{stream}` | Стрим создан | Информационное |
| `$JS.EVENT.ADVISORY.STREAM.DELETED.{stream}` | Стрим удалён | Алерт (если не плановый) |
| `$JS.EVENT.ADVISORY.STREAM.UPDATED.{stream}` | Конфигурация стрима изменена | Аудит |
| `$JS.EVENT.ADVISORY.CONSUMER.CREATED.{stream}.{consumer}` | Консьюмер создан | Информационное |
| `$JS.EVENT.ADVISORY.CONSUMER.DELETED.{stream}.{consumer}` | Консьюмер удалён | Алерт |
| `$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.{stream}.{consumer}` | MaxDeliver превышен | Critical (DLQ) |
| `$JS.EVENT.ADVISORY.API` | API ошибки JetStream | Warning |

### Подписка на advisory в Rust

```rust
pub async fn monitor_advisories(
    client: &async_nats::Client,
) -> Result<(), async_nats::Error> {
    let mut sub = client.subscribe("$JS.EVENT.ADVISORY.>").await?;

    while let Some(msg) = sub.next().await {
        let subject = &msg.subject;

        if subject.contains("MAX_DELIVERIES") {
            tracing::error!(
                subject = %subject,
                "Message exceeded max deliveries"
            );
            metrics::counter!("nats_advisory_max_deliveries_total").increment(1);
        } else if subject.contains("STREAM.DELETED") {
            tracing::warn!(subject = %subject, "Stream deleted");
            metrics::counter!("nats_advisory_stream_deleted_total").increment(1);
        } else if subject.contains("CONSUMER.DELETED") {
            tracing::warn!(subject = %subject, "Consumer deleted");
            metrics::counter!("nats_advisory_consumer_deleted_total").increment(1);
        }
    }

    Ok(())
}
```

### Алерты

| Условие | Severity | Описание |
|---------|----------|----------|
| `nats_consumer_lag{consumer="search_messages_consumer"}` > 10000 | warning | Search отстаёт от потока сообщений |
| `nats_consumer_lag` > 50000 для любого consumer | critical | Серьёзное отставание консьюмера |
| `nats_consumer_ack_pending` > 900 | critical | Близко к MaxAckPending (backpressure) |
| `nats_dlq_messages_total` rate > 0 за 5 мин | warning | Появились сообщения в DLQ |
| `nats_stream_bytes` > 80% max_bytes | warning | Стрим близок к лимиту |
| `nats_server_slow_consumers` rate > 0 | warning | Есть медленные консьюмеры |
| Advisory `MAX_DELIVERIES` | critical | Сообщение не может быть обработано |

---

## 9. Паттерны реализации на Rust

Все примеры используют `async-nats` 0.38+ и следуют правилам проекта: без `.unwrap()` / `.expect()`, обработка ошибок через `Result<T, AppError>`.

### Publisher с retry

```rust
use async_nats::jetstream;
use std::time::Duration;
use tokio::time::sleep;

/// Publisher с экспоненциальным retry для JetStream.
pub struct EventPublisher {
    js: jetstream::Context,
    max_retries: u32,
    base_delay: Duration,
}

impl EventPublisher {
    pub fn new(js: jetstream::Context) -> Self {
        Self {
            js,
            max_retries: 3,
            base_delay: Duration::from_millis(100),
        }
    }

    /// Опубликовать событие с retry и дедупликацией.
    pub async fn publish(
        &self,
        subject: &str,
        event: &Event,
        dedup_id: &str,
    ) -> Result<(), AppError> {
        let payload = serde_json::to_vec(event)
            .map_err(|e| AppError::Internal(e.into()))?;

        let mut headers = async_nats::HeaderMap::new();
        headers.insert("Nats-Msg-Id", dedup_id);

        let mut last_error = None;

        for attempt in 0..=self.max_retries {
            if attempt > 0 {
                let delay = self.base_delay * 2u32.pow(attempt - 1);
                tracing::warn!(
                    subject = subject,
                    attempt = attempt,
                    delay_ms = delay.as_millis() as u64,
                    "Retrying NATS publish"
                );
                sleep(delay).await;
            }

            match self
                .js
                .publish_with_headers(
                    subject.to_string(),
                    headers.clone(),
                    payload.clone().into(),
                )
                .await
            {
                Ok(ack_future) => match ack_future.await {
                    Ok(_ack) => {
                        tracing::debug!(
                            subject = subject,
                            dedup_id = dedup_id,
                            "Event published successfully"
                        );
                        metrics::counter!(
                            "nats_publish_total",
                            "subject" => subject.to_string(),
                            "result" => "success"
                        )
                        .increment(1);
                        return Ok(());
                    }
                    Err(e) => {
                        last_error = Some(e.to_string());
                    }
                },
                Err(e) => {
                    last_error = Some(e.to_string());
                }
            }
        }

        metrics::counter!(
            "nats_publish_total",
            "subject" => subject.to_string(),
            "result" => "failure"
        )
        .increment(1);

        Err(AppError::Internal(anyhow::anyhow!(
            "Failed to publish event after {} retries: {}",
            self.max_retries,
            last_error.unwrap_or_default()
        )))
    }
}
```

### Consumer с graceful shutdown

```rust
use async_nats::jetstream::consumer::pull;
use tokio::sync::broadcast;
use tokio_stream::StreamExt;

/// Запуск consumer loop с graceful shutdown.
pub async fn run_consumer<F, Fut>(
    consumer: pull::Consumer,
    handler: F,
    mut shutdown_rx: broadcast::Receiver<()>,
) -> Result<(), AppError>
where
    F: Fn(async_nats::jetstream::Message) -> Fut + Send + Sync + 'static,
    Fut: std::future::Future<Output = Result<(), AppError>> + Send,
{
    tracing::info!("Consumer loop started");

    loop {
        tokio::select! {
            // Graceful shutdown
            _ = shutdown_rx.recv() => {
                tracing::info!("Consumer received shutdown signal, draining...");
                break;
            }

            // Fetch batch сообщений
            result = consumer.fetch().max_messages(100).messages() => {
                match result {
                    Ok(mut messages) => {
                        while let Some(msg_result) = messages.next().await {
                            match msg_result {
                                Ok(msg) => {
                                    let timer = metrics::histogram!(
                                        "nats_consumer_processing_duration_seconds"
                                    );
                                    let start = std::time::Instant::now();

                                    match handler(msg.clone()).await {
                                        Ok(()) => {
                                            // ACK вызывается внутри handler
                                            timer.record(start.elapsed());
                                            metrics::counter!(
                                                "nats_consumer_processed_total",
                                                "result" => "success"
                                            )
                                            .increment(1);
                                        }
                                        Err(e) => {
                                            tracing::error!(
                                                error = %e,
                                                "Event processing failed"
                                            );
                                            // NAK с задержкой для retry
                                            let _ = msg.nak_with_delay(
                                                Duration::from_secs(5)
                                            ).await;
                                            metrics::counter!(
                                                "nats_consumer_processed_total",
                                                "result" => "failure"
                                            )
                                            .increment(1);
                                        }
                                    }
                                }
                                Err(e) => {
                                    tracing::error!(
                                        error = %e,
                                        "Failed to receive message from consumer"
                                    );
                                }
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!(
                            error = %e,
                            "Failed to fetch messages, retrying in 1s"
                        );
                        tokio::time::sleep(Duration::from_secs(1)).await;
                    }
                }
            }
        }
    }

    tracing::info!("Consumer loop terminated gracefully");
    Ok(())
}
```

### Идемпотентный обработчик событий

Полный паттерн обработки события с идемпотентностью, парсингом и диспатчингом:

```rust
use async_nats::jetstream::Message;

/// Обработчик события для Search Service — индексация сообщений.
pub async fn handle_message_event(
    msg: Message,
    redis: &TypedCache,
    indexer: &SearchIndexer,
) -> Result<(), AppError> {
    // 1. Извлечь idempotency key
    let msg_id = msg
        .headers
        .as_ref()
        .and_then(|h| h.get("Nats-Msg-Id"))
        .map(|v| v.to_string());

    // 2. Проверить дубликат
    if let Some(ref id) = msg_id {
        let cache_key = format!("search:processed:{id}");
        if redis.exists(&cache_key).await? {
            tracing::debug!(msg_id = %id, "Duplicate event, ACKing and skipping");
            msg.ack().await.map_err(|e| AppError::Internal(e.into()))?;
            return Ok(());
        }
    }

    // 3. Десериализовать событие
    let event: Event = serde_json::from_slice(&msg.payload)
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to deserialize event");
            AppError::BadRequest {
                code: "INVALID_EVENT".to_string(),
                message: format!("Failed to deserialize: {e}"),
            }
        })?;

    // 4. Обработать по типу
    match &event {
        Event::MessageCreated(payload) => {
            indexer.index_message(payload).await?;
        }
        Event::MessageUpdated(payload) => {
            indexer.update_message(payload).await?;
        }
        Event::MessageDeleted(payload) => {
            indexer.delete_message(payload.id).await?;
        }
        Event::MessageBulkDeleted(payload) => {
            for id in &payload.ids {
                indexer.delete_message(*id).await?;
            }
        }
        _ => {
            tracing::debug!(
                event_type = std::any::type_name_of_val(&event),
                "Unhandled event type, skipping"
            );
        }
    }

    // 5. Отметить как обработанное
    if let Some(ref id) = msg_id {
        let cache_key = format!("search:processed:{id}");
        redis.set(&cache_key, &true, 300).await?;
    }

    // 6. Подтвердить
    msg.ack().await.map_err(|e| AppError::Internal(e.into()))?;

    Ok(())
}
```

### Полный пример инициализации event-driven сервиса

```rust
use async_nats::jetstream;
use tokio::sync::broadcast;

pub async fn start_event_system(
    nats_url: &str,
    service_name: &str,
    redis: TypedCache,
) -> Result<(), AppError> {
    // 1. Подключиться к NATS
    let client = async_nats::connect(nats_url)
        .await
        .map_err(|e| AppError::Internal(e.into()))?;
    let js = jetstream::new(client.clone());

    // 2. Создать стримы (idempotent — если уже существуют, ничего не делает)
    create_streams(&js)
        .await
        .map_err(|e| AppError::Internal(e.into()))?;

    // 3. Создать консьюмеры для этого сервиса
    let messages_consumer = create_consumer(
        &js,
        "MESSAGES",
        &format!("{service_name}_messages_consumer"),
        Some("messages.>"),
    )
    .await
    .map_err(|e| AppError::Internal(e.into()))?;

    // 4. Создать publisher
    let publisher = EventPublisher::new(js.clone());

    // 5. Shutdown channel
    let (shutdown_tx, _) = broadcast::channel::<()>(1);

    // 6. Запустить consumer loop в отдельном tokio task
    let redis_clone = redis.clone();
    let shutdown_rx = shutdown_tx.subscribe();
    let consumer_handle = tokio::spawn(async move {
        run_consumer(
            messages_consumer,
            move |msg| {
                let redis = redis_clone.clone();
                async move {
                    // Обработчик конкретного сервиса
                    handle_message_event(msg, &redis, &indexer).await
                }
            },
            shutdown_rx,
        )
        .await
    });

    // 7. Запустить мониторинг advisory
    let client_clone = client.clone();
    let advisory_handle = tokio::spawn(async move {
        if let Err(e) = monitor_advisories(&client_clone).await {
            tracing::error!(error = %e, "Advisory monitor failed");
        }
    });

    // 8. Запустить DLQ handler
    let client_clone2 = client.clone();
    let js_clone = js.clone();
    let dlq_handle = tokio::spawn(async move {
        if let Err(e) = start_dlq_handler(&client_clone2, &js_clone).await {
            tracing::error!(error = %e, "DLQ handler failed");
        }
    });

    // 9. Периодический сбор метрик consumer lag
    let js_clone2 = js.clone();
    let metrics_handle = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(15));
        loop {
            interval.tick().await;
            if let Err(e) = collect_consumer_lag(&js_clone2).await {
                tracing::error!(error = %e, "Failed to collect consumer lag");
            }
        }
    });

    // 10. Ожидание shutdown
    tokio::signal::ctrl_c()
        .await
        .map_err(|e| AppError::Internal(e.into()))?;

    tracing::info!("Shutdown signal received, stopping event system...");
    let _ = shutdown_tx.send(());

    // Ожидание завершения всех задач
    let _ = tokio::time::timeout(
        Duration::from_secs(30),
        consumer_handle,
    )
    .await;

    advisory_handle.abort();
    dlq_handle.abort();
    metrics_handle.abort();

    tracing::info!("Event system stopped");
    Ok(())
}
```

---

## Источники

- [NATS JetStream Overview](https://docs.nats.io/nats-concepts/jetstream)
- [NATS Core](https://docs.nats.io/nats-concepts/core-nats)
- [JetStream Streams](https://docs.nats.io/nats-concepts/jetstream/streams)
- [JetStream Consumers](https://docs.nats.io/nats-concepts/jetstream/consumers)
- [JetStream Developer Guide](https://docs.nats.io/using-nats/developer/develop_jetstream)
- [JetStream Model Deep Dive](https://docs.nats.io/using-nats/developer/develop_jetstream/model_deep_dive)
- [NATS Monitoring](https://docs.nats.io/running-a-nats-service/nats_admin/monitoring)
- [NATS Prometheus Exporter](https://github.com/nats-io/prometheus-nats-exporter)
- [crate: async-nats 0.38](https://docs.rs/async-nats/latest/async_nats/)
