# External Integrations

**Analysis Date:** 2026-04-05

## APIs & External Services

**Push Notifications — Apple APNs:**
- Service: Apple Push Notification service (APNs)
- SDK: `github.com/sideshow/apns2` v0.25.0
- Implementation: `services/notification/internal/infra/apns/client.go`
- Auth: p8 key file (path configured via `APNS_KEY_PATH`)
- Config env vars: `APNS_KEY_PATH`, `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_BUNDLE_ID`, `APNS_PRODUCTION`
- Delivery model: silent background pushes only (`content-available: 1`) — no message content sent to Apple
- Mode: sandbox/production toggled via `APNS_PRODUCTION` env var

**Push Notifications — UnifiedPush:**
- Service: UnifiedPush (FCM-free, supports GrapheneOS, CalyxOS, etc.)
- SDK: standard `net/http` client (no third-party SDK)
- Implementation: `services/notification/internal/infra/unified_push/client.go`
- Auth: none — device registers its distributor endpoint URL during setup
- Delivery model: HTTP POST to device-registered endpoint URL; minimal payload (`conv_id` only, ≤4KB)

**Signal Protocol — libsignal:**
- Service: Signal's official open-source crypto library
- SDK: `libsignal-protocol` and `signal-crypto` (git dependency from `https://github.com/signalapp/libsignal.git`)
- Implementation: `crypto/` Rust crate (`crypto/Cargo.toml`)
- Binding generation: `uniffi` v0.28 generates Kotlin FFI bindings at build time
- Android integration: `android/app/src/main/java/run/koto/crypto/CryptoManager.kt`, `android/app/src/main/java/run/koto/crypto/KeyManager.kt`
- Note: server stores encrypted blobs only — plaintext never leaves the client

## Data Storage

**Relational Database — PostgreSQL:**
- Version: PostgreSQL 16 (Alpine image)
- Docker service: `postgres` in `docker-compose.yml`
- Connection: `POSTGRES_DSN` env var (format: `postgres://user:pass@host:5432/koto?sslmode=disable&pool_max_conns=N`)
- Client: `github.com/jackc/pgx/v5` v5.7.2
- Used by: auth, user, notification, media, bot services
- Schema migrations: SQL files in each service's `migrations/` directory
  - `services/auth/migrations/001_accounts.sql`
  - `services/user/migrations/001_users.sql`, `services/user/migrations/002_prekeys.sql`
  - `services/notification/migrations/001_devices.sql`
  - `services/media/migrations/001_files.sql`, `services/media/migrations/002_is_public.sql`
  - `services/bot/migrations/001_bots.sql`
- Pool config: `pool_max_conns=20` (auth, chat, user), `pool_max_conns=10` (notification, media, bot)

**Wide-Column Store — ScyllaDB:**
- Version: ScyllaDB 6.2
- Docker service: `scylladb` in `docker-compose.yml`
- Connection: `SCYLLA_HOSTS` env var (e.g., `scylladb:9042`), keyspace from `SCYLLA_KEYSPACE`
- Client: `github.com/gocql/gocql` v1.7.0
- Used by: chat service only (`services/chat/internal/infra/scylla/message_repo.go`)
- Schema migration: `services/chat/migrations/001_messages.cql`
- Consistency: `LocalQuorum`, protocol version 4, token-aware host selection policy
- Tables: `messages` (with optional TTL for expiring messages), `conversations`, `member_conversations`

**Cache / Session Store — Dragonfly:**
- Version: DragonflyDB v1.25.0 (Redis-compatible)
- Docker service: `dragonfly` in `docker-compose.yml`
- Connection: `DRAGONFLY_ADDR` + `DRAGONFLY_PASSWORD` env vars
- Client: `github.com/redis/go-redis/v9` v9.7.3
- Used by: gateway (session lookup), auth (refresh token store), chat (presence/rate limiting), user service
- Auth refresh tokens: stored with `rt:` key prefix, TTL-keyed to `JWT_REFRESH_TTL` (`services/auth/internal/infra/cache/refresh_token.go`)
- Pool: 20 max connections, 5 min idle

**Message Broker — NATS JetStream:**
- Version: NATS 2.10 (Alpine image, JetStream enabled)
- Docker service: `nats` in `docker-compose.yml`
- Connection: `NATS_URL` env var (e.g., `nats://nats:4222`)
- Client: `github.com/nats-io/nats.go` v1.38.0
- Used by: gateway (subscribe to events for WebSocket delivery), chat (publish new messages), notification (subscribe to dispatch push), bot service
- Stream: `CHAT` stream on subjects `chat.>`, file storage, 7-day retention, 2-minute dedup window
- Config: `services/chat/internal/infra/broker/nats_publisher.go`
- Monitoring port: 8222 (internal only)

**Object Storage — MinIO:**
- Version: MinIO latest
- Docker service: `minio` in `docker-compose.yml`
- Connection: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` env vars
- Client: `github.com/minio/minio-go/v7` v7.0.90
- Used by: media service only (`services/media/`)
- Bucket: `koto-media` (configured via `MINIO_BUCKET`)
- SSL: disabled in dev (`MINIO_USE_SSL=false`)
- Note: server stores encrypted blobs only — client encrypts before upload, decrypts after download; server never sees plaintext
- Console port: 9001 (internal only)

## Authentication & Identity

**JWT — Custom Ed25519:**
- Provider: self-hosted, no third-party auth service
- Implementation: `pkg/token/jwt.go`
- Algorithm: Ed25519 (`jwt.SigningMethodEdDSA`)
- Token issuer: auth service (has private key seed via `JWT_PRIVATE_KEY_SEED`)
- Token verifiers: gateway, chat, user, media, bot services (have public key via `JWT_PUBLIC_KEY`)
- Access TTL: 15 minutes (default, configurable via `JWT_ACCESS_TTL`)
- Refresh TTL: 720 hours / 30 days (default, configurable via `JWT_REFRESH_TTL`)
- Refresh tokens: opaque tokens stored in Dragonfly with TTL; lookup via `rt:` prefix

**Signal Protocol Key Exchange:**
- Registration flow: client uploads identity key, signed pre-key, signed pre-key signature, and one-time pre-keys at registration
- Server stores pre-keys in PostgreSQL (`services/user/migrations/002_prekeys.sql`)
- Android API: `android/app/src/main/java/run/koto/data/remote/api/AuthApi.kt` (`RegisterRequest` includes Signal key material)

## Android Local Storage

**Room Database:**
- Tables: `ConversationEntity` (`android/app/src/main/java/run/koto/data/local/entity/ConversationEntity.kt`), `MessageEntity` (`android/app/src/main/java/run/koto/data/local/entity/MessageEntity.kt`)
- DAOs: `ConversationDao`, `MessageDao` at `android/app/src/main/java/run/koto/data/local/dao/`
- Database class: `android/app/src/main/java/run/koto/data/local/KotoDatabase.kt`

**DataStore Preferences:**
- `AccountPrefs` — stores access token and account ID
- `SettingsPrefs` — user settings
- Location: `android/app/src/main/java/run/koto/data/prefs/`

## Webhooks & Callbacks

**Incoming:**
- None detected — no webhook endpoints defined

**Outgoing:**
- UnifiedPush: HTTP POST to device-registered distributor URLs (notification service)

## Monitoring & Observability

**Error Tracking:**
- None detected — no Sentry, Datadog, or equivalent integration

**Logs:**
- Structured JSON via `zerolog` to stdout on all backend services
- Log level configurable via `LOG_LEVEL` env var (default: `info`)
- Android: OkHttp `HttpLoggingInterceptor` at `BODY` level in debug builds, `NONE` in release

## CI/CD & Deployment

**Hosting:**
- Docker Compose on VPS (self-hosted)
- No managed cloud provider required for runtime

**CI Pipeline:**
- None detected — no GitHub Actions, CircleCI, or equivalent config files present

**Android Release:**
- Release build targets `https://koto.run` and `wss://koto.run`
- ProGuard/R8 minification and resource shrinking enabled

## Environment Configuration

**Required env vars (backend):**
- `POSTGRES_USER`, `POSTGRES_PASSWORD` — PostgreSQL credentials
- `DRAGONFLY_PASSWORD` — Dragonfly cache auth
- `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` — MinIO object storage credentials
- `JWT_PRIVATE_KEY_SEED` — 32-byte hex seed for Ed25519 JWT signing (auth service only)
- `JWT_PUBLIC_KEY` — hex-encoded Ed25519 public key (gateway, chat, user, media, bot)
- `ALLOWED_ORIGIN` — CORS allowed origin for gateway
- `APNS_KEY_PATH`, `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_BUNDLE_ID` — APNs credentials (notification service)
- `INTERNAL_SECRET` — inter-service authentication secret (bot service)

**Secrets location:**
- `.env` file at project root (dev values, consumed by `docker-compose.yml`)
- JWT keys generated by utility at `infra/scripts/keygen/main.go`

---

*Integration audit: 2026-04-05*
