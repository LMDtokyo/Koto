# Architecture

**Analysis Date:** 2026-04-05

## Pattern Overview

**Overall:** Multi-component system — Go microservices backend + Android Kotlin client + Rust crypto library

**Key Characteristics:**
- Backend follows Domain-Driven Design with strict layering: `domain` → `app` → `infra` + `transport`
- Single externally-facing gateway (port 8080 REST, port 9080 WebSocket); all other services are internal only
- End-to-end encryption enforced at protocol level — server stores only ciphertext blobs, never plaintext
- NATS JetStream used as async event bus between chat and gateway for real-time delivery
- Android client uses Repository pattern with Room local database as truth source; remote APIs are sync/background only

---

## Backend Layers (per service)

All Go services share the same internal layering convention:

**`domain` — Core entities and interfaces:**
- Purpose: Pure Go types and repository interfaces. Zero external imports.
- Location: `services/{name}/internal/domain/`
- Contains: Entity structs, repository interfaces, domain-specific value types
- Depends on: Nothing outside stdlib
- Used by: `app` layer

**`app` — Use-case orchestration:**
- Purpose: Business logic, calls domain interfaces, never touches infrastructure directly
- Location: `services/{name}/internal/app/`
- Contains: Service struct with use-case methods, input/output DTOs
- Depends on: `domain` interfaces, `pkg/errors`, `pkg/token`
- Used by: `transport` layer

**`infra` — Infrastructure adapters:**
- Purpose: Implements domain repository interfaces against real storage/brokers
- Location: `services/{name}/internal/infra/`
- Contains: PostgreSQL repos (`pgx/v5`), ScyllaDB repos (`gocql`), Redis/Dragonfly cache, NATS publisher, APNs client
- Depends on: `domain` interfaces, external SDKs
- Used by: `cmd/server/main.go` during wiring

**`transport/http` — HTTP layer:**
- Purpose: Decode HTTP requests, call `app.Service`, encode responses
- Location: `services/{name}/internal/transport/http/`
- Contains: `Handler` struct, `Router()` method (chi), individual handler methods
- Depends on: `app.Service`, `pkg/errors`, `pkg/logger`
- Used by: `cmd/server/main.go`

**`config` — Environment config:**
- Purpose: Load and validate env vars into a typed `Config` struct
- Location: `services/{name}/config/config.go` (present in auth, chat, user)
- Contains: Single `Load() (Config, error)` function
- Depends on: stdlib only
- Used by: `cmd/server/main.go`

**`cmd/server` — Wiring and startup:**
- Purpose: Wire dependencies, start HTTP server, handle graceful shutdown
- Location: `services/{name}/cmd/server/main.go`
- Contains: Single `main()` that reads config, creates infra, creates app service, creates handler, serves
- Depends on: All internal layers

---

## Service Responsibilities

**`gateway`** (`services/gateway/`):
- Exposes REST API on `:8080` and WebSocket on `:9080`
- Validates JWT (verify-only, no private key) via `pkg/token`
- Injects `X-Account-ID` header, proxies to upstream services via `httputil.ReverseProxy`
- Manages WebSocket hub (`internal/ws/Hub`): multi-device fanout, buffered channels per client
- Subscribes to NATS `chat.deliver.>` and pushes `new_message` frames to connected WebSocket clients
- Route table: `/v1/auth/*` → auth (public), all others → respective service (JWT protected)

**`auth`** (`services/auth/`):
- Anonymous accounts identified by Ed25519 public key (account ID = hex(pubkey))
- Issues Ed25519-signed JWT access tokens (15m) and opaque refresh tokens (720h)
- Stores refresh tokens in Dragonfly (Redis-compatible) with TTL
- Stores account key material (identity key, signed pre-key, one-time pre-keys) in PostgreSQL
- Manages X3DH key distribution: `POST /v1/auth/prekeys/publish`, `GET /v1/auth/prekeys/bundle/{id}`

**`chat`** (`services/chat/`):
- Stores E2EE messages in ScyllaDB with TIMEUUID primary keys and time-window compaction
- Conversations partitioned by `conversation_id`; member lookup via `member_conversations` table
- After `Save`, publishes `domain.DeliveryEvent` to NATS subject `chat.deliver.{convId}`
- Gateway subscribes and fans out to online recipients; offline users get push notifications via notification service
- Messages support per-row TTL for disappearing messages

**`user`** (`services/user/`):
- Optional user profiles (display name, avatar URL, bio) — all fields optional, anonymity preserved
- Contact list management with block/unblock support
- Signal Protocol PQXDH key bundle management: `UploadPrekeys`, `FetchPrekeyBundle`
- Stores in PostgreSQL via pgx v5

**`notification`** (`services/notification/`):
- Subscribes to NATS events (implied by config: `NATS_URL` present)
- Delivers silent APNs push (background, content-available) to iOS — no message content in push payload
- UnifiedPush support for Android (Google-free push)
- Device token registration per account

**`media`** (`services/media/`):
- Pre-signed URL flow for encrypted file uploads/downloads to MinIO
- File metadata stored in PostgreSQL; binary object stored in MinIO bucket `koto-media`
- Server never sees plaintext — client encrypts before upload, decrypts after download

**`bot`** (`services/bot/`):
- Bot registration with webhook URL
- Events delivered to bot's webhook when users interact
- Bots have their own account IDs in the messenger

---

## Shared Packages (`pkg/`)

**`pkg/logger`** (`pkg/logger/`):
- Wraps `zerolog`; `New(service, version, debug)` returns a `zerolog.Logger`
- `WithCtx` / `FromCtx` for context-propagated logging
- Used identically by all services

**`pkg/errors`** (`pkg/errors/`):
- Sentinel errors: `ErrNotFound`, `ErrAlreadyExists`, `ErrUnauthorized`, `ErrForbidden`, `ErrInvalidInput`, `ErrInternal`
- `AppError` struct wrapping sentinel + message + cause
- `HTTPStatus(err)` maps sentinels to HTTP status codes — used in every handler's `writeAppError()`
- `New(sentinel, msg)` and `Wrap(sentinel, msg, cause)` constructors

**`pkg/token`** (`pkg/token/`):
- Ed25519-based JWT manager
- Auth service uses `NewManager(seed, ttl)` (has private key, can issue)
- Gateway uses `NewManagerFromPublicKey(pubkey)` (verify-only)
- `Manager.Issue(accountID)` → signed JWT; `Manager.Verify(token)` → `Claims{AccountID}`

---

## Data Flow

**Sending a message:**

1. Android client encrypts plaintext with Signal Double Ratchet via `CryptoManager.encrypt(peerId, plaintext)` (Rust `KotoCrypto` via uniffi)
2. `POST /v1/conversations/{id}/messages` reaches gateway on `:8080`
3. Gateway validates JWT, injects `X-Account-ID`, proxies to `http://chat:18002`
4. Chat `app.Service.SendMessage` saves `Message{Ciphertext: ...}` to ScyllaDB
5. Chat publishes `DeliveryEvent` to NATS subject `chat.deliver.{convId}`
6. Gateway NATS subscriber receives event, calls `hub.Send(recipientID, Envelope{Type:"new_message", ...})`
7. Each connected WebSocket client for that account ID receives the frame immediately
8. Android `KotoWebSocket.onMessage` → `ChatRepository.observeWebSocket` → `MessageDao.upsert` → UI Flow updates

**Session establishment (X3DH + Kyber PQXDH):**

1. Alice calls `GET /v1/keys/{bobId}` → user service returns `PrekeyBundle`
2. Alice's `CryptoManager.processPreKeyBundle(bobId, bundle)` establishes session via Rust libsignal
3. First message is a `PreKeySignalMessage`; subsequent messages are `SignalMessage` (Double Ratchet)

**Registration:**

1. Android generates `RegistrationBundle` via Rust `generate_registration_bundle()`
2. `POST /v1/auth/register` with hex-encoded public keys
3. Auth service verifies signed pre-key signature, creates account, issues token pair
4. Client persists identity key pair in encrypted DataStore via `AccountPrefs`

---

## Key Abstractions

**`domain.Repository` interfaces:**
- Purpose: Decouple business logic from storage technology
- Pattern: Defined in `domain/`, implemented in `infra/`, injected in `main.go`
- Examples: `domain.AccountRepository`, `domain.MessageRepository`, `domain.ConversationRepository`

**`app.Service`:**
- Purpose: Single use-case orchestrator per service
- Pattern: Struct with repository interfaces as fields; each method is one use case
- Examples: `services/auth/internal/app/service.go`, `services/chat/internal/app/service.go`

**`ws.Hub`:**
- Purpose: In-process registry of live WebSocket connections, supports multi-device per account
- Location: `services/gateway/internal/ws/hub.go`
- Pattern: `map[accountID][]*Client` with `sync.RWMutex`; buffered send channel per client (size 256)

**`proxy.Upstream`:**
- Purpose: Pre-configured `httputil.ReverseProxy` per upstream service
- Location: `services/gateway/internal/proxy/proxy.go`
- Pattern: One `Upstream` per service name, registered at gateway startup from env vars

**`KotoCrypto` (Rust):**
- Purpose: Signal Protocol session state per local account
- Location: `crypto/src/lib.rs`
- Pattern: `Arc<Mutex<InMemSignalProtocolStore>>` wrapped in uniffi `Object`; all methods callable from Kotlin via JNI

---

## Entry Points

**Gateway REST:**
- Location: `services/gateway/cmd/server/main.go:main()`
- Listens: `:8080` (env `HTTP_ADDR`)
- Triggers: Incoming HTTP requests from Android / web clients

**Gateway WebSocket:**
- Location: `services/gateway/cmd/server/main.go:wsHandler()`
- Listens: `:9080` (env `WS_ADDR`)
- Triggers: Android `KotoWebSocket.connect(accessToken)` with `?token=` query param

**Auth service:**
- Location: `services/auth/cmd/server/main.go:main()`
- Listens: `:18001` (env `HTTP_ADDR`, internal only)

**Chat service:**
- Location: `services/chat/cmd/server/main.go:main()`
- Listens: `:18002` (internal only)

**Other services** follow identical startup pattern: config load → infra connect → app.New → handler.Router → `http.Server.ListenAndServe`

---

## Error Handling

**Strategy:** Sentinel-wrapping with HTTP status mapping

**Patterns:**
- Domain layer returns `*apperrors.AppError` wrapping a sentinel (`ErrNotFound`, etc.)
- `apperrors.HTTPStatus(err)` in `writeAppError()` translates to HTTP status
- Repository layer maps `pgx.ErrNoRows` → `ErrNotFound`, unique violations → `ErrAlreadyExists`
- All handlers follow: decode request → call service → if err: `writeAppError(w, err)` → return
- Gateway proxy errors surface as `{"error":"upstream unavailable"}` with HTTP 502
- Rust crypto errors use `thiserror`-derived `CryptoError` enum, exposed to Kotlin as uniffi error type

---

## Cross-Cutting Concerns

**Logging:** zerolog via `pkg/logger`. Service name and version in every log entry. Request ID and path logged per request via `requestLoggerMiddleware`. Context-propagated via `logger.WithCtx` / `logger.FromCtx`.

**Validation:** Performed in `app.Service` methods (not in HTTP handler). Returns `apperrors.ErrInvalidInput` on bad input.

**Authentication:** Gateway validates JWT and injects `X-Account-ID`. Upstream services read `X-Account-ID` header — they trust the gateway, no re-validation. WebSocket auth via `?token=` query param (header inaccessible post-upgrade).

**Graceful shutdown:** All services use `signal.Notify(quit, SIGINT, SIGTERM)` + `http.Server.Shutdown(ctx)` with 15-second timeout.

**Database migrations:** SQL files in `services/{name}/migrations/` (e.g. `001_accounts.sql`). Applied manually with psql/cqlsh — no migration runner embedded.

---

*Architecture analysis: 2026-04-05*
