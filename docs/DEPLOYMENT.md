# Руководство по развёртыванию

Полное руководство по деплою коммуникационной платформы: от локальной разработки до продакшена в Kubernetes.

---

## Оглавление

1. [Среды (Environments)](#1-среды-environments)
2. [Локальная разработка](#2-локальная-разработка)
3. [Docker](#3-docker)
4. [Kubernetes: топология кластера](#4-kubernetes-топология-кластера)
5. [Kubernetes: ресурсы](#5-kubernetes-ресурсы)
6. [Helm Charts](#6-helm-charts)
7. [Сеть](#7-сеть)
8. [Service Discovery](#8-service-discovery)
9. [CI/CD Pipeline](#9-cicd-pipeline)
10. [Стратегии деплоя](#10-стратегии-деплоя)
11. [Управление секретами](#11-управление-секретами)
12. [Мониторинг деплоя](#12-мониторинг-деплоя)

---

## 1. Среды (Environments)

| Среда | Инфраструктура | Назначение |
|-------|---------------|------------|
| **Local Dev** | Docker Compose + `cargo run` | Разработка на машине разработчика |
| **CI** | GitHub Actions (эфемерные раннеры) | Автоматическое тестирование, линтинг, сборка |
| **Staging** | Kubernetes (1 кластер, 3 ноды) | Предрелизное тестирование, интеграция |
| **Production** | Kubernetes (multi-AZ, 3+ нод) | Боевой трафик |

```
Поток кода между средами:

  Developer       CI              Staging          Production
  +---------+    +------------+   +------------+   +------------+
  | Local   |--->| GitHub     |-->| K8s        |-->| K8s        |
  | Dev     |    | Actions    |   | Staging    |   | Production |
  |         |    |            |   | (auto)     |   | (manual)   |
  +---------+    +------------+   +------------+   +------------+
       |              |                |                  |
  cargo run      fmt/clippy/       helm upgrade       helm upgrade
  + compose      audit/test        --install           --install
                 build+push
```

### Отличия сред

| Параметр | Local Dev | Staging | Production |
|----------|-----------|---------|------------|
| Реплики сервисов | 1 (cargo run) | 1-2 | 2-10 (HPA) |
| PostgreSQL | Docker, 1 инстанс | StatefulSet, 1 инстанс | Patroni HA, 3 ноды |
| Redis/Valkey | Docker, пароль dev | StatefulSet, пароль | Sentinel, 3 ноды |
| NATS | Docker, 1 нода | StatefulSet, 1 нода | Кластер, 3 ноды |
| TLS | Нет (HTTP) | Let's Encrypt (staging) | Let's Encrypt (production) |
| Домен | localhost | staging.example.com | example.com |
| Логирование | stdout, debug | Loki, info | Loki, info/warn |
| Мониторинг | Нет | Prometheus + Grafana | Prometheus + Grafana + Alertmanager |

---

## 2. Локальная разработка

### Предварительные требования

- Rust 1.84+ (edition 2024)
- Docker 27+ и Docker Compose
- SQLx CLI: `cargo install sqlx-cli --features postgres`
- (Опционально) `cargo-watch`: `cargo install cargo-watch`

### Шаг 1: Клонировать репозиторий и настроить окружение

```bash
git clone <repo-url>
cd project

# Создать .env из шаблона
cp .env.example .env
```

Содержимое `.env.example`:

```bash
# PostgreSQL
DATABASE_URL=postgres://dev:dev@localhost:5432/auth_db
POSTGRES_USER=dev
POSTGRES_PASSWORD=dev

# Redis / Valkey
REDIS_URL=redis://:dev@localhost:6379

# NATS
NATS_URL=nats://localhost:4222

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin

# Meilisearch
MEILISEARCH_URL=http://localhost:7700
MEILISEARCH_MASTER_KEY=dev-master-key

# JWT (dev-only, НИКОГДА не использовать в продакшене)
JWT_PRIVATE_KEY=<dev-es256-private-key>
JWT_PUBLIC_KEY=<dev-es256-public-key>

# Логирование
RUST_LOG=info,sqlx=warn,tower_http=debug
```

### Шаг 2: Поднять инфраструктуру через Docker Compose

```bash
docker compose up -d
```

Docker Compose поднимает следующие компоненты:

```yaml
services:
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
      - "4222:4222"
      - "8222:8222"
    command: --jetstream --store_dir /data
    volumes:
      - nats_data:/data

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
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

volumes:
  postgres_data:
  nats_data:
  minio_data:
  meili_data:
```

### Шаг 3: Инициализировать базы данных

Скрипт `scripts/init-dbs.sql` выполняется автоматически при первом запуске PostgreSQL и создаёт отдельные БД для каждого сервиса:

```sql
-- scripts/init-dbs.sql
CREATE DATABASE auth_db;
CREATE DATABASE users_db;
CREATE DATABASE guilds_db;
CREATE DATABASE messages_db;
CREATE DATABASE moderation_db;
CREATE DATABASE notifications_db;
```

### Шаг 4: Применить миграции

```bash
# Для каждого сервиса с миграциями
cd services/auth && sqlx migrate run && cd ../..
cd services/users && sqlx migrate run && cd ../..
cd services/guilds && sqlx migrate run && cd ../..
cd services/messages && sqlx migrate run && cd ../..
cd services/moderation && sqlx migrate run && cd ../..
cd services/notifications && sqlx migrate run && cd ../..
```

Или одной командой через скрипт:

```bash
#!/bin/bash
# scripts/migrate-all.sh
for service in auth users guilds messages moderation notifications; do
  echo "=== Migrating $service ==="
  (cd services/$service && sqlx migrate run)
done
```

### Шаг 5: Запустить сервисы

Сервисы запускаются через `cargo run` для быстрой итерации (без пересборки Docker):

```bash
# Терминал 1: API Gateway
cargo run --bin api-gateway

# Терминал 2: WebSocket Gateway
cargo run --bin ws-gateway

# Терминал 3: Auth Service
cargo run --bin auth-service

# ... аналогично для каждого сервиса
```

С `cargo-watch` для автоматической перезагрузки при изменении кода:

```bash
cargo watch -x 'run --bin auth-service'
```

### Карта сервисов и портов

```
+--------------------------------------------------+
|                 localhost                          |
|                                                   |
|  Сервисы (cargo run):                             |
|    api-gateway ............ :3000                  |
|    ws-gateway ............. :4000                  |
|    auth-service ........... :3001                  |
|    users-service .......... :3002                  |
|    guilds-service ......... :3003                  |
|    messages-service ....... :3004                  |
|    media-service .......... :3005                  |
|    notifications-service .. :3006                  |
|    search-service ......... :3007                  |
|    voice-service .......... :3008                  |
|    moderation-service ..... :3009                  |
|    presence-service ....... :3010                  |
|                                                   |
|  Инфраструктура (Docker Compose):                 |
|    PostgreSQL 17 .......... :5432                  |
|    Valkey 8 ............... :6379                  |
|    NATS 2.11 .............. :4222 (мониторинг 8222)|
|    MinIO .................. :9000 (консоль 9001)   |
|    Meilisearch v1.12 ...... :7700                  |
+--------------------------------------------------+
```

### Проверка работоспособности

```bash
# PostgreSQL
psql postgres://dev:dev@localhost:5432/auth_db -c "SELECT 1"

# Redis / Valkey
redis-cli -a dev ping

# NATS
curl http://localhost:8222/healthz

# MinIO
curl http://localhost:9000/minio/health/live

# Meilisearch
curl http://localhost:7700/health

# API Gateway (после запуска)
curl http://localhost:3000/health/live
```

---

## 3. Docker

### Multi-Stage Dockerfile

Единый Dockerfile для всех 12 сервисов. Целевой сервис выбирается через `SERVICE_NAME` build argument:

```dockerfile
# Stage 1: Build
FROM rust:1.84-bookworm AS builder

WORKDIR /app

# Кешировать зависимости (слой пересобирается только при изменении Cargo.toml/lock)
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

### Характеристики образа

| Параметр | Значение |
|----------|---------|
| Builder stage | `rust:1.84-bookworm` (~1.5 GB, только на этапе сборки) |
| Runtime stage | `debian:bookworm-slim` (~80 MB base) |
| Итоговый размер | ~20-30 MB (Rust binary + slim base + ca-certificates) |
| Пользователь | Non-root (UID 1000, GID 1000) |
| Бинарник | Release build (`--release`) |

### Стратегия кеширования Docker слоёв

```
Слой 1: FROM debian:bookworm-slim          <-- кешируется всегда
Слой 2: apt-get install ca-certificates    <-- кешируется всегда
Слой 3: COPY Cargo.toml Cargo.lock         <-- пересобирается при изменении зависимостей
Слой 4: COPY crates/ services/             <-- пересобирается при изменении кода
Слой 5: cargo build --release              <-- пересобирается при изменении кода
Слой 6: COPY binary                        <-- пересобирается при новом бинарнике
```

Ключевое: `Cargo.toml` и `Cargo.lock` копируются первыми. Если зависимости не менялись, Docker использует кешированный слой сборки зависимостей.

### Сборка и публикация образа

```bash
# Сборка образа для конкретного сервиса
docker build --build-arg SERVICE_NAME=auth-service \
  -t ghcr.io/<org>/platform/auth-service:latest .

# Публикация в GHCR
docker push ghcr.io/<org>/platform/auth-service:latest

# Сборка с тегом коммита (для staging/production)
docker build --build-arg SERVICE_NAME=auth-service \
  -t ghcr.io/<org>/platform/auth-service:$(git rev-parse --short HEAD) .
```

### Реестр образов (GHCR)

Все образы хранятся в GitHub Container Registry:

```
ghcr.io/<org>/platform/api-gateway:<tag>
ghcr.io/<org>/platform/ws-gateway:<tag>
ghcr.io/<org>/platform/auth-service:<tag>
ghcr.io/<org>/platform/users-service:<tag>
ghcr.io/<org>/platform/guilds-service:<tag>
ghcr.io/<org>/platform/messages-service:<tag>
ghcr.io/<org>/platform/media-service:<tag>
ghcr.io/<org>/platform/notifications-service:<tag>
ghcr.io/<org>/platform/search-service:<tag>
ghcr.io/<org>/platform/voice-service:<tag>
ghcr.io/<org>/platform/moderation-service:<tag>
ghcr.io/<org>/platform/presence-service:<tag>
```

Стратегия тегирования:
- `<commit-sha>` -- хеш коммита (уникальный, immutable, для staging/production)
- `latest` -- последняя версия из main (для dev)

---

## 4. Kubernetes: топология кластера

### Общая схема

```
+----------------------------------------------------------------+
|                   Kubernetes Cluster                            |
|                                                                 |
|  +--- Namespace: platform --------------------------------+    |
|  |                                                         |    |
|  |  +--------------------------------------------------+  |    |
|  |  |              Ingress (Caddy)                      |  |    |
|  |  |  +-------------+-------------+-------------+     |  |    |
|  |  |  | api.example | ws.example  | cdn.example |     |  |    |
|  |  |  |  .com       |  .com       |  .com       |     |  |    |
|  |  |  +------+------+------+------+------+------+     |  |    |
|  |  +---------|-------------|-------------|-------------+  |    |
|  |            |             |             |                |    |
|  |            v             v             v                |    |
|  |  +-----------+  +-----------+  +-----------+           |    |
|  |  | API GW    |  | WS GW     |  |   MinIO   |           |    |
|  |  |  :3000    |  |  :4000    |  |  :9000    |           |    |
|  |  +-----+-----+  +-----+-----+  +-----------+           |    |
|  |        |               |                                |    |
|  |        +-------+-------+                                |    |
|  |                |                                        |    |
|  |                v                                        |    |
|  |  +------------------------------------------+          |    |
|  |  |         NATS JetStream                    |          |    |
|  |  |    (Event Bus / Message Broker)           |          |    |
|  |  +--+---+---+---+---+---+---+---+---+---+---+          |    |
|  |     |   |   |   |   |   |   |   |   |   |              |    |
|  |     v   v   v   v   v   v   v   v   v   v              |    |
|  |  +------+ +------+ +------+ +------+ +------+          |    |
|  |  | Auth | |Users | |Guilds| | Msgs | |Media |          |    |
|  |  |:3001 | |:3002 | |:3003 | |:3004 | |:3005 |          |    |
|  |  +------+ +------+ +------+ +------+ +------+          |    |
|  |  +------+ +------+ +------+ +------+ +------+          |    |
|  |  |Notif.| |Search| |Voice | |Moder.| |Pres. |          |    |
|  |  |:3006 | |:3007 | |:3008 | |:3009 | |:3010 |          |    |
|  |  +------+ +------+ +------+ +------+ +------+          |    |
|  |                                                         |    |
|  |  +--- StatefulSets ----------------------------+       |    |
|  |  |  PostgreSQL (Patroni)  |  Valkey (Sentinel) |       |    |
|  |  |  NATS JetStream        |  Meilisearch       |       |    |
|  |  |  MinIO                 |                    |       |    |
|  |  +---------------------------------------------+       |    |
|  +---------------------------------------------------------+    |
|                                                                 |
|  +--- Namespace: monitoring ------------------------------+    |
|  |  Prometheus | Grafana | Loki | Tempo | Alertmanager    |    |
|  +---------------------------------------------------------+    |
|                                                                 |
|  +--- Namespace: cert-manager ----------------------------+    |
|  |  cert-manager | ClusterIssuer (Let's Encrypt)          |    |
|  +---------------------------------------------------------+    |
+----------------------------------------------------------------+
```

### Namespace-ы

| Namespace | Содержимое |
|-----------|-----------|
| `platform` | Все 12 микросервисов (Deployments), StatefulSets инфраструктуры, Ingress, ConfigMaps, Secrets |
| `monitoring` | Prometheus, Grafana, Loki, Promtail, Tempo, Alertmanager |
| `cert-manager` | cert-manager для автоматических TLS-сертификатов |

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: platform
  labels:
    app.kubernetes.io/part-of: communication-platform
    pod-security.kubernetes.io/enforce: restricted
```

---

## 5. Kubernetes: ресурсы

### Deployment (шаблон)

Каждый микросервис разворачивается как Deployment. Пример для auth-service:

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
          image: ghcr.io/<org>/platform/auth-service:latest
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
          startupProbe:
            httpGet:
              path: /health/live
              port: 3001
            failureThreshold: 30
            periodSeconds: 2
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
```

### Service (ClusterIP)

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
  type: ClusterIP
```

Все микросервисы используют ClusterIP -- они недоступны извне напрямую. Внешний трафик проходит только через Ingress (Caddy) к API Gateway и WS Gateway.

### Таблица ресурсов (per pod)

| Сервис | Порт | CPU request | CPU limit | Memory request | Memory limit | Min replicas |
|--------|------|------------|-----------|---------------|-------------|-------------|
| API Gateway | 3000 | 200m | 1000m | 128Mi | 512Mi | 2 |
| WS Gateway | 4000 | 500m | 2000m | 256Mi | 1Gi | 2 |
| Auth | 3001 | 100m | 500m | 128Mi | 256Mi | 2 |
| Users | 3002 | 100m | 500m | 128Mi | 256Mi | 2 |
| Guilds | 3003 | 200m | 500m | 128Mi | 256Mi | 2 |
| Messages | 3004 | 200m | 1000m | 256Mi | 512Mi | 2 |
| Media | 3005 | 200m | 1000m | 256Mi | 512Mi | 2 |
| Notifications | 3006 | 100m | 500m | 128Mi | 256Mi | 1 |
| Search | 3007 | 100m | 500m | 128Mi | 256Mi | 1 |
| Voice | 3008 | 100m | 500m | 128Mi | 256Mi | 1 |
| Moderation | 3009 | 100m | 500m | 128Mi | 256Mi | 1 |
| Presence | 3010 | 100m | 500m | 128Mi | 256Mi | 2 |

### HPA (Horizontal Pod Autoscaler)

Автоскейлинг применяется ко всем сервисам. Пороги: CPU 70%, Memory 80%.

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

### ConfigMap (нечувствительная конфигурация)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: auth-config
  namespace: platform
data:
  RUST_LOG: "info,sqlx=warn"
  SERVICE_PORT: "3001"
```

### Health Probes

Каждый сервис реализует три endpoint-а:

| Endpoint | Назначение | K8s Probe |
|----------|-----------|-----------|
| `GET /health/live` | Процесс жив | livenessProbe |
| `GET /health/ready` | Все зависимости доступны (DB, Redis, NATS) | readinessProbe |
| `GET /metrics` | Prometheus метрики | prometheus.io/scrape annotation |

```
Логика health check:

  /health/live
      Процесс отвечает?
      |-- Да --> 200 OK
      +-- Нет --> Pod перезапускается (kubelet restart)

  /health/ready
      |-- PostgreSQL connection? -- Нет --> 503, Pod убирается из Service
      |-- Redis connection?      -- Нет --> 503, трафик не направляется
      |-- NATS connection?       -- Нет --> 503
      +-- Все OK                 -------> 200, Pod получает трафик
```

---

## 6. Helm Charts

### Структура Helm chart-а

```
helm/
+-- platform/
    |-- Chart.yaml
    |-- values.yaml               # Значения по умолчанию
    |-- values-staging.yaml       # Переопределения для staging
    |-- values-prod.yaml          # Переопределения для production
    +-- templates/
        |-- _helpers.tpl          # Вспомогательные шаблоны
        |-- namespace.yaml
        |-- ingress.yaml
        |-- network-policy.yaml
        |
        |-- api-gateway/
        |   |-- deployment.yaml
        |   |-- service.yaml
        |   |-- hpa.yaml
        |   |-- configmap.yaml
        |   +-- secret.yaml
        |
        |-- ws-gateway/
        |   +-- ...
        |-- auth-service/
        |   +-- ...
        |-- users-service/
        |   +-- ...
        |-- guilds-service/
        |   +-- ...
        |-- messages-service/
        |   +-- ...
        |-- media-service/
        |   +-- ...
        |-- notifications-service/
        |   +-- ...
        |-- search-service/
        |   +-- ...
        |-- voice-service/
        |   +-- ...
        |-- moderation-service/
        |   +-- ...
        +-- presence-service/
            +-- ...
```

### values.yaml (основной)

```yaml
global:
  imageRegistry: ghcr.io/<org>/platform
  imageTag: latest
  imagePullPolicy: IfNotPresent
  namespace: platform

services:
  apiGateway:
    replicas: 2
    port: 3000
    resources:
      requests:
        cpu: 200m
        memory: 128Mi
      limits:
        cpu: 1000m
        memory: 512Mi

  wsGateway:
    replicas: 2
    port: 4000
    resources:
      requests:
        cpu: 500m
        memory: 256Mi
      limits:
        cpu: 2000m
        memory: 1Gi

  auth:
    replicas: 2
    port: 3001
    resources:
      requests:
        cpu: 100m
        memory: 128Mi
      limits:
        cpu: 500m
        memory: 256Mi

  # ... аналогично для остальных 9 сервисов

hpa:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPU: 70
  targetMemory: 80

ingress:
  enabled: true
  className: caddy
  hosts:
    api: api.example.com
    ws: ws.example.com
    cdn: cdn.example.com
  tls:
    enabled: true
    secretName: platform-tls
```

### values-staging.yaml

```yaml
global:
  imageTag: "develop-latest"

services:
  apiGateway:
    replicas: 1
  wsGateway:
    replicas: 1
  auth:
    replicas: 1

hpa:
  enabled: false

ingress:
  hosts:
    api: api.staging.example.com
    ws: ws.staging.example.com
    cdn: cdn.staging.example.com
```

### values-prod.yaml

```yaml
global:
  imagePullPolicy: Always

services:
  apiGateway:
    replicas: 3
  wsGateway:
    replicas: 3
  messages:
    replicas: 3

hpa:
  enabled: true
  minReplicas: 2
  maxReplicas: 20

ingress:
  hosts:
    api: api.example.com
    ws: ws.example.com
    cdn: cdn.example.com
```

### Команды деплоя

```bash
# Staging
helm upgrade --install platform ./helm/platform \
  --namespace platform \
  --create-namespace \
  -f ./helm/platform/values-staging.yaml \
  --set global.imageTag=<commit-sha>

# Production
helm upgrade --install platform ./helm/platform \
  --namespace platform \
  --create-namespace \
  -f ./helm/platform/values-prod.yaml \
  --set global.imageTag=<commit-sha>

# Проверить статус
helm status platform --namespace platform

# Посмотреть историю релизов
helm history platform --namespace platform

# Откатить на предыдущую ревизию
helm rollback platform <revision> --namespace platform
```

---

## 7. Сеть

### Ingress (Caddy)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: platform-ingress
  namespace: platform
  annotations:
    kubernetes.io/ingress.class: caddy
    cert-manager.io/cluster-issuer: letsencrypt-prod
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

### Маршрутизация трафика

```
Интернет
    |
    v
+------------------------------------------------------+
|                    Caddy Ingress                      |
|              (TLS termination, HTTP/3)                |
|                                                       |
|  api.example.com -----> api-gateway:3000              |
|    /v1/auth/*            (REST API)                   |
|    /v1/users/*                                        |
|    /v1/guilds/*                                       |
|    /v1/channels/*                                     |
|    /v1/messages/*                                     |
|                                                       |
|  ws.example.com ------> ws-gateway:4000               |
|    /                     (WebSocket, wss://)           |
|                                                       |
|  cdn.example.com -----> minio:9000                    |
|    /avatars/*            (S3 objects)                  |
|    /attachments/*                                     |
+------------------------------------------------------+
```

### TLS: cert-manager + Let's Encrypt

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ops@example.com
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            class: caddy
```

cert-manager автоматически:
1. Запрашивает сертификат у Let's Encrypt
2. Проходит ACME challenge (HTTP-01)
3. Сохраняет сертификат в K8s Secret (`platform-tls`)
4. Обновляет сертификат за 30 дней до истечения

### Network Policies

Микросервисы общаются только через NATS. Прямые HTTP-вызовы между сервисами запрещены.

```yaml
# Default deny: запрещаем весь трафик по умолчанию
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: platform
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
```

Далее разрешаем только необходимые соединения:

```
Сводная матрица сетевых правил:

  Источник                  | Назначение      | Порт | Разрешено
  --------------------------+-----------------+------+----------
  Ingress (Caddy)           | API Gateway     | 3000 | Да
  Ingress (Caddy)           | WS Gateway      | 4000 | Да
  Ingress (Caddy)           | MinIO           | 9000 | Да
  Все сервисы (platform)    | NATS            | 4222 | Да
  Все сервисы (platform)    | Redis/Valkey    | 6379 | Да
  Auth, Users, Guilds,      | PostgreSQL      | 5432 | Да
    Messages, Notifications,|                 |      |
    Moderation              |                 |      |
  Media Service             | MinIO           | 9000 | Да
  Search Service            | Meilisearch     | 7700 | Да
  Prometheus (monitoring)   | Все /metrics    | *    | Да
  Все pods                  | DNS (CoreDNS)   | 53   | Да
  Сервис A --> Сервис B     | (напрямую HTTP) | *    | НЕТ
```

### DNS: CoreDNS

Внутри кластера сервисы доступны по DNS-именам:

```
Формат: <service-name>.<namespace>.svc.cluster.local

Примеры:
  auth-service.platform.svc.cluster.local:3001
  ws-gateway.platform.svc.cluster.local:4000
  postgresql.platform.svc.cluster.local:5432
  nats.platform.svc.cluster.local:4222
  valkey.platform.svc.cluster.local:6379
  minio.platform.svc.cluster.local:9000
  meilisearch.platform.svc.cluster.local:7700
```

---

## 8. Service Discovery

### Принцип: Event-Driven, не прямые вызовы

```
+-----------------------------------------------------------+
|                     ПРАВИЛЬНО                              |
|                                                            |
|  Auth -----NATS event-----> Users                          |
|  API Gateway --NATS req/reply--> Auth                      |
|  Messages ----NATS event-----> Search (индексация)         |
|  Messages ----NATS event-----> Gateway (доставка клиентам) |
+-----------------------------------------------------------+

+-----------------------------------------------------------+
|                     НЕПРАВИЛЬНО                            |
|                                                            |
|  Auth -----HTTP-----> Users    (ЗАПРЕЩЕНО)                 |
|  API GW ---HTTP-----> Messages (ЗАПРЕЩЕНО)                 |
+-----------------------------------------------------------+
```

### Как сервисы находят инфраструктуру

Все адреса передаются через переменные окружения (K8s ConfigMap / Secret):

| Переменная | Пример значения |
|-----------|----------------|
| `DATABASE_URL` | `postgres://auth:xxx@postgresql.platform.svc.cluster.local:5432/auth_db` |
| `REDIS_URL` | `redis://:xxx@valkey.platform.svc.cluster.local:6379` |
| `NATS_URL` | `nats://nats.platform.svc.cluster.local:4222` |
| `MINIO_ENDPOINT` | `http://minio.platform.svc.cluster.local:9000` |
| `MEILISEARCH_URL` | `http://meilisearch.platform.svc.cluster.local:7700` |
| `LIVEKIT_URL` | `http://livekit.platform.svc.cluster.local:7880` |

### NATS: межсервисное общение

```
Паттерны NATS:

1. Pub/Sub (события, fan-out):

   Message Service --publish--> "message.created"
                                    |
                        +-----------+-----------+
                        v           v           v
                    Gateway     Search     Notifications
                  (доставка)  (индекс)   (push/email)


2. Request/Reply (синхронный запрос):

   API Gateway --request--> "auth.validate_token" --> Auth Service
               <--reply---  { valid: true, user_id: 123 }


3. Queue Groups (балансировка нагрузки):

   NATS --> "message.created" --> [Search-1, Search-2, Search-3]
                                   (только один из группы получает)
```

### NATS топики (соглашение по именованию)

Формат: `<domain>.<entity>.<action>`

```
auth.user.registered       -- новый пользователь зарегистрирован
auth.user.login            -- пользователь вошёл
auth.user.logout           -- пользователь вышел
user.updated               -- профиль обновлён
user.friend.request        -- запрос в друзья
user.friend.accepted       -- запрос принят
guild.created              -- сервер создан
guild.member.joined        -- участник вступил
guild.channel.created      -- канал создан
guild.role.updated         -- роль обновлена
message.created            -- сообщение отправлено
message.updated            -- сообщение отредактировано
message.deleted            -- сообщение удалено
message.reaction.added     -- реакция добавлена
media.uploaded             -- файл загружен
media.deleted              -- файл удалён
presence.updated           -- статус изменён
```

---

## 9. CI/CD Pipeline

### Общая схема

```
+----------+   +------------+   +----------+   +--------------+   +-------------+
| Git Push |-->| Format     |-->| Clippy   |-->| cargo audit  |-->| cargo test  |
|          |   | Check      |   | Lint     |   | (CVE check)  |   | (unit+integ)|
+----------+   +------------+   +----------+   +--------------+   +------+------+
                                                                          |
                                                                          v
+-------------+   +--------------+   +--------------+   +---------------------+
| Deploy to   |<--| Push to GHCR |<--| Build Docker |<--| Tests passed        |
| Kubernetes  |   | (12 images)  |   | (matrix x12) |   |                     |
+-------------+   +--------------+   +--------------+   +---------------------+
```

### GitHub Actions Workflow

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
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
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
          helm upgrade --install platform ./helm/platform \
            --namespace platform \
            --set global.imageTag=${{ github.sha }}
```

### Стадии CI и что они проверяют

| Стадия | Команда | Что проверяет | Блокирует merge |
|--------|---------|--------------|-----------------|
| Format | `cargo fmt --all -- --check` | Форматирование кода | Да |
| Clippy | `cargo clippy --all-targets -- -D warnings` | Lint, потенциальные ошибки | Да |
| Audit | `cargo audit` | Уязвимости в зависимостях (CVE) | Да |
| Test | `cargo test --all --all-features` | Unit + integration тесты | Да |
| Build | `docker build` (matrix x12) | Компиляция release binary | Да (на main) |
| Push | `docker push` (GHCR) | Публикация образов | Только main |
| Deploy | `helm upgrade --install` | Раскатка в K8s | Только main |

### Кеширование в CI

- **Rust Cache** (`Swatinem/rust-cache@v2`): кеширует `~/.cargo/registry`, `target/` между запусками
- **Docker Layer Cache**: Docker BuildKit автоматически кеширует слои
- Кеширование сокращает время CI с ~15 мин до ~5 мин при отсутствии изменений в зависимостях

### Тестирование в CI

Тесты запускаются с реальными PostgreSQL 17 и Valkey 8, поднятыми как GitHub Actions services. Это гарантирует, что integration-тесты проверяют реальное взаимодействие с БД.

---

## 10. Стратегии деплоя

### Rolling Update (по умолчанию)

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0    # Ни один pod не выключается до готовности нового
      maxSurge: 1          # Один дополнительный pod во время обновления
```

```
Процесс Rolling Update:

Время --------------------------------------------------------->

Pod v1 [==========]                          [удаляется]
Pod v1 [==========]                [удаляется]
                    Pod v2         [====готов====]
                          Pod v2   [====готов====]

Трафик: ---v1-----------v1+v2--------------v2------------------->
```

Используется для: большинства обновлений, безопасные изменения.

### Blue-Green (для крупных релизов)

```
Blue (текущая версия):             Green (новая версия):
  +--------------+                   +--------------+
  | v1.0 (blue)  | <-- трафик        | v2.0 (green) |     (тестирование)
  | 3 replicas   |                   | 3 replicas   |
  +--------------+                   +--------------+

  Переключение (Service selector):

  +--------------+                   +--------------+
  | v1.0 (blue)  |     (standby)     | v2.0 (green) | <-- трафик
  +--------------+                   | 3 replicas   |
                                     +--------------+
```

Реализация через Helm: два Deployment-а, переключение через Service selector.
Используется для: major-версии, миграции БД-схемы, большие изменения в API.

### Canary (для рисковых изменений)

```
Начало:                          Canary (5%):
  +--------------+                 +--------------+
  | v1.0         | <-- 100%        | v1.0         | <-- 95% трафика
  | 10 replicas  |                 | 9 replicas   |
  +--------------+                 +--------------+
                                   +--------------+
                                   | v2.0 canary  | <-- 5% (мониторим)
                                   | 1 replica    |
                                   +--------------+

  Если OK:  5% --> 25% --> 50% --> 100%
  Если ошибки: откатываем canary
```

Используется для: gateway, auth и другие критические сервисы.

### Откат (Rollback)

```bash
# Посмотреть историю релизов
helm history platform --namespace platform

# Откатить на предыдущую ревизию
helm rollback platform <revision> --namespace platform

# Экстренный откат: вернуть конкретный образ
kubectl set image deployment/auth-service \
  auth-service=ghcr.io/<org>/platform/auth-service:<previous-sha> \
  --namespace platform
```

### Миграции базы данных

Миграции выполняются ДО деплоя нового кода. Правила backwards-compatibility:

```
1. Миграции должны быть backwards-compatible
   (старый код должен работать с новой схемой)

2. Порядок выполнения:

   +--------------+     +--------------+     +------------------+
   | Миграция БД  |---->| Деплой нового|---->| Очистка          |
   | (additive)   |     | кода         |     | (удаление старых |
   |              |     |              |     |  колонок в след.  |
   +--------------+     +--------------+     |  релизе)          |
                                              +------------------+

3. НИКОГДА: удалять колонки/таблицы в той же миграции,
   что и деплой кода, который их не использует
```

Пример безопасной миграции:

```sql
-- Релиз N: добавляем новую колонку (старый код её игнорирует)
ALTER TABLE users ADD COLUMN display_name VARCHAR(100);

-- Деплоим код, который использует display_name
-- Старые pod-ы ещё работают -- им новая колонка не мешает

-- Релиз N+1: удаляем старую колонку (если нужно)
-- Только после того, как все pod-ы обновлены
```

---

## 11. Управление секретами

### Принципы

- Секреты хранятся ТОЛЬКО в Kubernetes Secrets
- НИКОГДА в ConfigMap, НИКОГДА в коде, НИКОГДА в .env в репозитории
- Каждый сервис имеет доступ только к своим секретам
- JWT приватный ключ (ES256) есть только у auth-service

### Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: auth-secrets
  namespace: platform
type: Opaque
stringData:
  DATABASE_URL: postgres://auth:xxx@postgresql:5432/auth_db
  REDIS_URL: redis://:xxx@valkey:6379
  NATS_URL: nats://nats:4222
  JWT_PRIVATE_KEY: |
    -----BEGIN EC PRIVATE KEY-----
    ...
    -----END EC PRIVATE KEY-----
  JWT_PUBLIC_KEY: |
    -----BEGIN PUBLIC KEY-----
    ...
    -----END PUBLIC KEY-----
  GOOGLE_CLIENT_ID: xxx
  GOOGLE_CLIENT_SECRET: xxx
  GITHUB_CLIENT_ID: xxx
  GITHUB_CLIENT_SECRET: xxx
```

### Полная таблица переменных окружения

#### Общие (все сервисы)

| Переменная | Описание | Источник |
|-----------|----------|---------|
| `RUST_LOG` | Уровень логирования | ConfigMap |
| `NATS_URL` | NATS connection URL | Secret |
| `REDIS_URL` | Redis/Valkey connection URL | Secret |
| `JWT_PUBLIC_KEY` | ES256 public key (PEM) для валидации JWT | Secret |

#### Per-service (дополнительные)

| Сервис | Переменная | Источник |
|--------|-----------|---------|
| Auth | `DATABASE_URL` | Secret |
| Auth | `JWT_PRIVATE_KEY` (ES256 private, только auth) | Secret |
| Auth | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Secret |
| Auth | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | Secret |
| Users | `DATABASE_URL` | Secret |
| Guilds | `DATABASE_URL` | Secret |
| Messages | `DATABASE_URL` | Secret |
| Messages | `SCYLLA_NODES` (опционально, при миграции на ScyllaDB) | Secret |
| Media | `MINIO_ENDPOINT` | ConfigMap |
| Media | `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Secret |
| Media | `CLAMAV_URL` | ConfigMap |
| Notifications | `DATABASE_URL` | Secret |
| Notifications | `VAPID_PRIVATE_KEY`, `VAPID_PUBLIC_KEY` | Secret |
| Notifications | `FCM_SERVICE_ACCOUNT` | Secret |
| Notifications | `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD` | Secret |
| Search | `MEILISEARCH_URL` | ConfigMap |
| Search | `MEILISEARCH_MASTER_KEY` | Secret |
| Voice | `LIVEKIT_URL` | ConfigMap |
| Voice | `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | Secret |
| Moderation | `DATABASE_URL` | Secret |
| Presence | -- (только Redis + NATS) | -- |
| API Gateway | `CORS_ORIGINS`, `GLOBAL_RATE_LIMIT`, `MAX_BODY_SIZE` | ConfigMap |
| WS Gateway | `MAX_CONNECTIONS`, `HEARTBEAT_INTERVAL_MS` | ConfigMap |

### ES256 ключевая пара для JWT

```bash
# Генерация (выполняется однократно, хранится в K8s Secrets)
openssl ecparam -genkey -name prime256v1 -noout -out ec-private.pem
openssl ec -in ec-private.pem -pubout -out ec-public.pem
```

- `ec-private.pem` -- ТОЛЬКО в Secret для auth-service
- `ec-public.pem` -- в Secret для ВСЕХ сервисов (валидация JWT)

### Ротация секретов

| Секрет | Период ротации |
|--------|---------------|
| JWT signing keys (ES256) | 90 дней |
| Database passwords | 180 дней |
| Redis password | 180 дней |
| MinIO credentials | 180 дней |
| TLS certificates | Автоматически (cert-manager) |
| OAuth client secrets | 365 дней |

---

## 12. Мониторинг деплоя

### Стек мониторинга

```
+----------------------------------------------------------+
|               Namespace: monitoring                       |
|                                                           |
|  +------------+   +-----------+   +----------------+     |
|  | Prometheus |-->|  Grafana  |   | Alertmanager   |     |
|  | (метрики)  |   | (дашборды)|   | (-> Telegram/  |     |
|  +-----+------+   +-----------+   |    Slack)      |     |
|        |                           +----------------+     |
|        v                                                  |
|  +-----------+   +-----------+   +----------------+      |
|  | Loki      |   |   Tempo   |   |  Promtail      |      |
|  | (логи)    |   | (трейсы)  |   | (сбор логов,   |      |
|  |           |   |           |   |  DaemonSet)    |      |
|  +-----------+   +-----------+   +----------------+      |
+----------------------------------------------------------+
         ^                ^                  ^
         |                |                  |
   Scrape /metrics   OTLP export      Log collection
         |                |                  |
+--------+----------------+------------------+---------+
|              Namespace: platform                      |
|  +----------+  +-----------+  +-----------+          |
|  | Pod      |  | Pod       |  | Pod       |          |
|  | /metrics |  | OTLP spans|  | stdout    |          |
|  +----------+  +-----------+  +-----------+          |
+------------------------------------------------------+
```

### Prometheus аннотации на подах

Каждый Deployment включает аннотации для автоматического scrape-а метрик:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "3001"
  prometheus.io/path: "/metrics"
```

### Health endpoints (стандарт для всех сервисов)

| Endpoint | Назначение | K8s probe |
|----------|-----------|-----------|
| `GET /health/live` | Процесс жив | livenessProbe |
| `GET /health/ready` | Все зависимости доступны (DB, Redis, NATS) | readinessProbe |
| `GET /metrics` | Prometheus метрики | prometheus.io/scrape |

### Критические алерты

| Алерт | Условие | Severity |
|-------|---------|----------|
| `ServiceDown` | Replicas = 0 > 1 мин | critical |
| `HighErrorRate` | 5xx > 5% за 5 мин | critical |
| `HighLatency` | p99 > 1s за 5 мин | warning |
| `DatabaseConnectionExhausted` | Pool used > 90% | critical |
| `RedisOOM` | Memory > 90% | critical |
| `DiskSpaceHigh` | Disk > 85% | warning |
| `CertificateExpiry` | TLS cert < 14 дней | warning |
| `NATSDisconnected` | NATS connection lost | critical |
| `PodRestartLoop` | Restarts > 5 за 15 мин | warning |

### Пример Prometheus alerting rules

```yaml
groups:
  - name: platform-critical
    rules:
      - alert: ServiceDown
        expr: up{namespace="platform"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Сервис {{ $labels.job }} недоступен"

      - alert: HighErrorRate
        expr: |
          sum(rate(http_requests_total{status=~"5..", namespace="platform"}[5m])) by (service)
          /
          sum(rate(http_requests_total{namespace="platform"}[5m])) by (service)
          > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Сервис {{ $labels.service }}: error rate > 5%"

      - alert: HighLatency
        expr: >
          histogram_quantile(0.99,
            rate(http_request_duration_seconds_bucket{namespace="platform"}[5m])
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Сервис {{ $labels.service }}: p99 latency > 1s"
```

### Чеклист верификации деплоя

```
После каждого деплоя:

  [ ] Все pods в статусе Running
      kubectl get pods -n platform

  [ ] Нет CrashLoopBackOff
      kubectl get pods -n platform | grep -v Running

  [ ] Readiness probes проходят
      kubectl get endpoints -n platform

  [ ] Версия образа корректная
      kubectl get pods -n platform -o jsonpath='{.items[*].spec.containers[*].image}'

  [ ] Health endpoints отвечают 200
      curl https://api.example.com/health/live
      curl https://api.example.com/health/ready

  [ ] Prometheus видит все targets
      Grafana --> Explore --> up{namespace="platform"}

  [ ] Error rate не вырос
      Grafana --> Dashboard --> Platform Overview

  [ ] Latency p99 в норме (< 500ms для API, < 200ms для WS)
      Grafana --> Dashboard --> API Latency
```

### Smoke-тесты после деплоя

```bash
# API Gateway health
curl -sf https://api.example.com/health/live

# WebSocket Gateway health
curl -sf https://ws.example.com/health/live

# Проверка аутентификации (регистрация + логин)
curl -s -X POST https://api.example.com/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"smoketest","email":"smoke@test.com","password":"testpassword"}'

# Проверка WebSocket (wscat или аналог)
# wscat -c wss://ws.example.com
```

---

## Источники

- [Kubernetes Documentation](https://kubernetes.io/docs/) -- официальная документация Kubernetes
- [Helm Documentation](https://helm.sh/docs/) -- пакетный менеджер для Kubernetes
- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/) -- оптимизация Docker-образов
- [GitHub Actions Documentation](https://docs.github.com/en/actions) -- CI/CD платформа
- [Caddy Documentation](https://caddyserver.com/docs/) -- reverse proxy с авто-TLS и HTTP/3
- [cert-manager Documentation](https://cert-manager.io/docs/) -- автоматические TLS-сертификаты в K8s
- [Prometheus Documentation](https://prometheus.io/docs/) -- мониторинг и метрики
- [Grafana Loki Documentation](https://grafana.com/docs/loki/) -- агрегация логов
- [OpenTelemetry Rust SDK](https://docs.rs/opentelemetry/latest/opentelemetry/) -- distributed tracing для Rust
- [NATS Documentation](https://docs.nats.io/) -- message broker
- [Kubernetes Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/) -- сетевая изоляция
- [Kubernetes HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) -- горизонтальный автоскейлинг
