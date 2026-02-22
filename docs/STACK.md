# Стек технологий

## Backend — Rust

### API Server — Axum
- **Язык**: Rust (edition 2024)
- **Async runtime**: Tokio
- **HTTP фреймворк**: Axum (tower-based, модульный)
- **ORM**: SQLx (compile-time checked queries) или SeaORM
- **Валидация**: validator crate + serde
- **Сериализация**: serde + serde_json
- **HTTP клиент**: reqwest
- **Логирование**: tracing + tracing-subscriber
- **Конфигурация**: config crate + dotenvy

### WebSocket Gateway — Axum + Tokio
- **WS**: axum::extract::ws или tokio-tungstenite
- **Сжатие**: zstd crate
- **Аутентификация**: jsonwebtoken crate (JWT)
- **Связь с backend**: async-nats
- **Конкурентность**: Tokio tasks (миллионы async tasks, ~8KB каждый)

### Ключевые crates (библиотеки)

| Crate | Назначение |
|-------|-----------|
| `axum` | HTTP/WS фреймворк |
| `tokio` | Async runtime |
| `serde` / `serde_json` | Сериализация/десериализация |
| `sqlx` | Async PostgreSQL (compile-time проверка SQL) |
| `redis` / `deadpool-redis` | Redis клиент + connection pool |
| `async-nats` | NATS клиент |
| `jsonwebtoken` | JWT создание/валидация |
| `argon2` | Хеширование паролей |
| `validator` | Валидация структур |
| `uuid` | UUID генерация |
| `chrono` | Дата/время |
| `bitflags` | Система прав (битовые маски) |
| `tracing` | Структурированное логирование |
| `tower` | Middleware (rate limit, cors, auth) |
| `tower-http` | HTTP middleware (cors, compression, trace) |
| `image` | Обработка изображений |
| `reqwest` | HTTP клиент |
| `thiserror` | Кастомные типы ошибок |
| `anyhow` | Удобная обработка ошибок (в приложениях) |

### Message Broker — NATS JetStream
- Pub/Sub для событий между сервисами
- JetStream для персистентных очередей
- Request/Reply для синхронных запросов
- Rust клиент: `async-nats`

### Голос/Видео — LiveKit
- SFU сервер (Go)
- WebRTC
- Rust SDK: `livekit-api` (серверный)
- JS SDK: `@livekit/components-react` (клиентский)

## Frontend

### Web App
- **React 19** — UI фреймворк
- **Rspack** — сборщик (замена Webpack, написан на Rust, в 10x быстрее)
- **Zustand** — стейт менеджмент (легче MobX)
- **React Aria Components** — accessible UI примитивы
- **Radix UI** — компоненты (checkbox, switch, radio)
- **Tailwind CSS v4** — стили
- **Framer Motion** — анимации
- **React Hook Form + Zod** — формы и валидация
- **Lingui** — i18n (интернационализация)
- **Vitest** — тесты фронтенда
- **TypeScript** — только для фронтенда

### Desktop — Tauri 2
- Rust backend (общий код с серверными сервисами)
- Нативный WebView
- ~10MB размер (vs ~150MB Electron)
- Низкое потребление RAM
- Нативные API: файловая система, уведомления, трей

### Mobile — React Native / Expo
- Переиспользование логики с веб-фронтенда
- Expo для упрощения сборки и деплоя

## Базы данных

### PostgreSQL 17 — основная реляционная БД
- Пользователи, гильдии, каналы, роли, настройки
- ACID транзакции
- Connection pooling: PgBouncer или встроенный в SQLx
- Миграции: sqlx-cli (`sqlx migrate run`)

### ScyllaDB — сообщения (при масштабировании)
- На старте: PostgreSQL с партиционированием
- При > 100M сообщений: миграция на ScyllaDB
- p99 чтения ~15ms, записи ~5ms
- Rust драйвер: `scylla` crate

### Redis / Valkey 8 — кеш и real-time
- Сессии, presence, typing indicators
- Rate limiting (sliding window)
- Pub/Sub для уведомлений
- Кеш горячих данных
- Rust: `redis` + `deadpool-redis` (connection pool)

### Meilisearch — полнотекстовый поиск
- Typo-tolerant (написан на Rust!)
- Быстрый (~50ms на запрос)
- REST API
- Rust SDK: `meilisearch-sdk`

### MinIO — хранение файлов
- S3-совместимый API
- Аватары, вложения, медиа
- Presigned URLs для прямой загрузки
- Rust: `aws-sdk-s3` или `rust-s3`

## Инфраструктура

### Оркестрация — Kubernetes
- Helm charts для деплоя
- HPA (Horizontal Pod Autoscaler)
- Ingress Controller (nginx-ingress или Traefik)
- cert-manager для TLS

### CI/CD — GitHub Actions / GitLab CI
- clippy → test → build → push Docker → deploy

### Reverse Proxy — Caddy
- Автоматический TLS (Let's Encrypt)
- HTTP/3 из коробки
- WebSocket проксирование

### Мониторинг
- **Логи**: Grafana Loki + Promtail (через tracing)
- **Метрики**: Prometheus + Grafana (через metrics crate)
- **Трейсы**: OpenTelemetry + Tempo/Jaeger (через tracing-opentelemetry)
- **Алерты**: Alertmanager → Telegram/Slack

### IaC — Terraform
- Provisioning серверов и кластеров
- Reproducible инфраструктура

## Версии (минимальные)

| Технология | Версия |
|-----------|--------|
| Rust | 1.84+ (edition 2024) |
| React | 19 |
| TypeScript | 5.7+ (только фронтенд) |
| pnpm | 9+ (только фронтенд) |
| Docker | 27+ |
| Kubernetes | 1.30+ |
| PostgreSQL | 17 |
| Redis/Valkey | 8 |

## Почему Rust для backend?

| Фактор | Rust | Node.js/TS | Go |
|--------|------|-----------|-----|
| **Скорость** | ~1x (эталон) | ~3-5x медленнее | ~1.5-2x медленнее |
| **Память** | Минимальная, нет GC | V8 heap, GC паузы | GC, но лёгкий |
| **Docker image** | ~10-20MB (static binary) | ~150MB+ | ~10-20MB |
| **Конкурентность** | Tokio async, fearless | Event loop, 1 поток | Goroutines |
| **Безопасность** | Memory safe на этапе компиляции | Runtime ошибки | Runtime ошибки |
| **Tauri** | Общий код backend + desktop | Разные языки | Разные языки |
| **Cold start** | Мгновенный (native binary) | ~500ms (V8 init) | Мгновенный |
