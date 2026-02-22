# Инфраструктура

Развёртывание, контейнеризация, CI/CD, мониторинг. Все сервисы — Docker контейнеры в Kubernetes.

## Источники

- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Documentation](https://helm.sh/docs/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Loki Documentation](https://grafana.com/docs/loki/)
- [OpenTelemetry Rust SDK](https://docs.rs/opentelemetry/latest/opentelemetry/)
- [Caddy Documentation](https://caddyserver.com/docs/)
- [NATS Documentation](https://docs.nats.io/)

---

## Docker

### Dockerfile (единый для всех сервисов)

```dockerfile
# Stage 1: Build
FROM rust:1.84-bookworm AS builder

WORKDIR /app

# Кешировать зависимости
COPY Cargo.toml Cargo.lock ./
COPY crates/ crates/
COPY services/ services/

# Build только целевой сервис
ARG SERVICE_NAME
RUN cargo build --release --bin ${SERVICE_NAME}

# Stage 2: Runtime (минимальный образ)
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

ARG SERVICE_NAME
COPY --from=builder /app/target/release/${SERVICE_NAME} /usr/local/bin/service

EXPOSE 3000

USER 1000:1000

CMD ["service"]
```

**Размер образа**: ~20-30 MB (static Rust binary + slim base)

### docker-compose.yml (dev environment)

```yaml
services:
  # --- Инфраструктура ---
  postgres:
    image: postgres:17
    environment:
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init-dbs.sql:/docker-entrypoint-initdb.d/init.sql

  redis:
    image: valkey/valkey:8-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass dev

  nats:
    image: nats:2.11
    ports:
      - "4222:4222"   # Client
      - "8222:8222"   # Monitoring
    command: --jetstream --store_dir /data
    volumes:
      - nats_data:/data

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"   # API
      - "9001:9001"   # Console
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

  meilisearch:
    image: getmeili/meilisearch:v1.12
    ports:
      - "7700:7700"
    environment:
      MEILI_MASTER_KEY: dev-master-key
    volumes:
      - meili_data:/meili_data

  # --- Сервисы (опционально, можно запускать cargo run локально) ---
  # auth:
  #   build:
  #     context: .
  #     args:
  #       SERVICE_NAME: auth-service
  #   ports:
  #     - "3001:3001"
  #   env_file: .env.dev

volumes:
  postgres_data:
  nats_data:
  minio_data:
  meili_data:
```

### init-dbs.sql

```sql
-- Создать отдельные БД для каждого сервиса
CREATE DATABASE auth_db;
CREATE DATABASE users_db;
CREATE DATABASE guilds_db;
CREATE DATABASE messages_db;
CREATE DATABASE moderation_db;
CREATE DATABASE notifications_db;
```

---

## Kubernetes

### Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: platform
```

### Deployment (шаблон для сервиса)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: platform
  labels:
    app: auth-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "3001"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: auth-service
      containers:
        - name: auth-service
          image: registry.example.com/platform/auth-service:latest
          ports:
            - containerPort: 3001
          envFrom:
            - configMapRef:
                name: auth-config
            - secretRef:
                name: auth-secrets
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 256Mi
          livenessProbe:
            httpGet:
              path: /health/live
              port: 3001
            initialDelaySeconds: 5
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 3001
            initialDelaySeconds: 5
            periodSeconds: 5
          startupProbe:
            httpGet:
              path: /health/live
              port: 3001
            failureThreshold: 30
            periodSeconds: 2
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: platform
spec:
  selector:
    app: auth-service
  ports:
    - port: 3001
      targetPort: 3001
  type: ClusterIP    # Внутренний, не доступен извне
```

### HPA (Horizontal Pod Autoscaler)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: auth-service-hpa
  namespace: platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-service
  minReplicas: 2
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
```

### Resource Estimates (per pod)

| Сервис | CPU req | CPU limit | Memory req | Memory limit | Replicas (min) |
|--------|---------|-----------|------------|-------------|----------------|
| API Gateway | 200m | 1000m | 128Mi | 512Mi | 2 |
| WS Gateway | 500m | 2000m | 256Mi | 1Gi | 2 |
| Auth | 100m | 500m | 128Mi | 256Mi | 2 |
| Users | 100m | 500m | 128Mi | 256Mi | 2 |
| Guilds | 200m | 500m | 128Mi | 256Mi | 2 |
| Messages | 200m | 1000m | 256Mi | 512Mi | 2 |
| Media | 200m | 1000m | 256Mi | 512Mi | 2 |
| Notifications | 100m | 500m | 128Mi | 256Mi | 1 |
| Search | 100m | 500m | 128Mi | 256Mi | 1 |
| Voice | 100m | 500m | 128Mi | 256Mi | 1 |
| Moderation | 100m | 500m | 128Mi | 256Mi | 1 |
| Presence | 100m | 500m | 128Mi | 256Mi | 2 |

### Ingress (Caddy)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: platform-ingress
  namespace: platform
  annotations:
    kubernetes.io/ingress.class: caddy
spec:
  tls:
    - hosts:
        - api.example.com
        - ws.example.com
        - cdn.example.com
      secretName: platform-tls
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 3000
    - host: ws.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: ws-gateway
                port:
                  number: 4000
    - host: cdn.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: minio
                port:
                  number: 9000
```

### Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: auth-secrets
  namespace: platform
type: Opaque
stringData:
  DATABASE_URL: postgres://auth_service:xxx@postgres:5432/auth_db
  REDIS_URL: redis://:xxx@redis:6379
  NATS_URL: nats://nats:4222
  JWT_PRIVATE_KEY: |
    -----BEGIN EC PRIVATE KEY-----
    ...
    -----END EC PRIVATE KEY-----
  JWT_PUBLIC_KEY: |
    -----BEGIN PUBLIC KEY-----
    ...
    -----END PUBLIC KEY-----
```

---

## CI/CD — GitHub Actions

### Pipeline

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  CARGO_TERM_COLOR: always
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository }}

jobs:
  check:
    name: Check & Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
        with:
          components: clippy, rustfmt
      - uses: Swatinem/rust-cache@v2

      - name: Format check
        run: cargo fmt --all -- --check

      - name: Clippy
        run: cargo clippy --all-targets --all-features -- -D warnings

      - name: Audit
        run: |
          cargo install cargo-audit
          cargo audit

  test:
    name: Test
    runs-on: ubuntu-latest
    needs: check
    services:
      postgres:
        image: postgres:17
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: --health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5
      redis:
        image: valkey/valkey:8-alpine
        ports:
          - 6379:6379
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - uses: Swatinem/rust-cache@v2

      - name: Run tests
        run: cargo test --all --all-features
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/test
          REDIS_URL: redis://localhost:6379

  build-and-push:
    name: Build & Push
    runs-on: ubuntu-latest
    needs: test
    if: github.ref == 'refs/heads/main'
    strategy:
      matrix:
        service:
          - api-gateway
          - ws-gateway
          - auth-service
          - users-service
          - guilds-service
          - messages-service
          - media-service
          - notifications-service
          - search-service
          - voice-service
          - moderation-service
          - presence-service
    steps:
      - uses: actions/checkout@v4

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build & Push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          build-args: SERVICE_NAME=${{ matrix.service }}
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:latest

  deploy:
    name: Deploy
    runs-on: ubuntu-latest
    needs: build-and-push
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to K8s
        run: |
          # Обновить image tag в Helm values
          helm upgrade --install platform ./helm/platform \
            --namespace platform \
            --set global.imageTag=${{ github.sha }}
```

### Pipeline стадии

```
Push → Format Check → Clippy → cargo audit → Tests → Build Docker → Push to Registry → Deploy to K8s
```

| Стадия | Что проверяет | Блокирует merge |
|--------|--------------|-----------------|
| `cargo fmt --check` | Форматирование кода | Да |
| `cargo clippy -D warnings` | Lint, потенциальные ошибки | Да |
| `cargo audit` | Уязвимости в зависимостях | Да |
| `cargo test` | Unit + integration tests | Да |
| Build Docker | Компиляция production binary | Да (на main) |
| Deploy | Раскатка в K8s | Только main |

---

## Мониторинг

### Стек

| Компонент | Инструмент | Роль |
|-----------|-----------|------|
| Метрики | Prometheus | Сбор метрик (scrape /metrics) |
| Дашборды | Grafana | Визуализация |
| Логи | Grafana Loki + Promtail | Агрегация логов |
| Трейсы | OpenTelemetry + Tempo | Distributed tracing |
| Алерты | Alertmanager | Оповещения → Telegram/Slack |

### Health Endpoints (стандарт для всех сервисов)

| Endpoint | Назначение | K8s probe |
|----------|-----------|-----------|
| `GET /health/live` | Процесс жив | livenessProbe |
| `GET /health/ready` | Все зависимости доступны (DB, Redis, NATS) | readinessProbe |
| `GET /metrics` | Prometheus метрики | prometheus.io/scrape |

### Критические алерты

| Алерт | Условие | Severity |
|-------|---------|----------|
| ServiceDown | Replicas = 0 > 1 мин | critical |
| HighErrorRate | 5xx > 5% за 5 мин | critical |
| HighLatency | p99 > 1s за 5 мин | warning |
| DatabaseConnectionExhausted | Pool used > 90% | critical |
| RedisOOM | Memory > 90% | critical |
| DiskSpaceHigh | Disk > 85% | warning |
| CertificateExpiry | TLS cert < 14 дней | warning |
| NATSDisconnected | NATS connection lost | critical |
| PodRestartLoop | Restarts > 5 за 15 мин | warning |

---

## Env переменные (полный список per service)

### Общие (все сервисы)

| Переменная | Описание | Пример |
|-----------|----------|--------|
| `RUST_LOG` | Уровень логирования | `info,sqlx=warn` |
| `NATS_URL` | NATS connection URL | `nats://nats:4222` |
| `REDIS_URL` | Redis connection URL | `redis://:password@redis:6379` |
| `JWT_PUBLIC_KEY` | ES256 public key (PEM) | `-----BEGIN PUBLIC KEY-----...` |

### Per-service

| Сервис | Доп. переменные |
|--------|----------------|
| Auth | `DATABASE_URL`, `JWT_PRIVATE_KEY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` |
| Users | `DATABASE_URL` |
| Guilds | `DATABASE_URL` |
| Messages | `DATABASE_URL`, `SCYLLA_NODES` (опционально) |
| Media | `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `CLAMAV_URL` |
| Notifications | `DATABASE_URL`, `VAPID_PRIVATE_KEY`, `VAPID_PUBLIC_KEY`, `FCM_SERVICE_ACCOUNT`, `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD` |
| Search | `MEILISEARCH_URL`, `MEILISEARCH_MASTER_KEY` |
| Voice | `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` |
| Moderation | `DATABASE_URL` |
| Presence | — (только Redis + NATS) |
| API Gateway | `CORS_ORIGINS`, `GLOBAL_RATE_LIMIT`, `MAX_BODY_SIZE` |
| WS Gateway | `MAX_CONNECTIONS`, `HEARTBEAT_INTERVAL_MS` |

---

## Безопасность инфраструктуры

### Чеклист

- [ ] Все сервисы запускаются как non-root (UID 1000)
- [ ] Network Policies: сервисы общаются только через NATS, не напрямую
- [ ] Pod Security Standards: restricted profile
- [ ] Secrets в K8s Secrets (не в ConfigMap, не в .env)
- [ ] TLS между всеми компонентами (mTLS через service mesh опционально)
- [ ] Container image scanning (Trivy) в CI pipeline
- [ ] RBAC: minimal permissions для каждого ServiceAccount
- [ ] Ingress: только api.example.com и ws.example.com доступны извне
- [ ] Rate limiting на Ingress уровне (дополнительно к application-level)
- [ ] Логи не содержат secrets (пароли, токены, PII)
- [ ] Бэкапы: PostgreSQL (ежедневно), Redis (AOF), MinIO (erasure coding), Meilisearch (snapshots)
- [ ] Disaster recovery plan протестирован
