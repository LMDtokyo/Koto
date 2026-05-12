<!-- GSD:project-start source:PROJECT.md -->

## Project

**Koto Messenger — Premium UI/UX**

Koto — приватный мессенджер с end-to-end шифрованием (Signal Protocol + PQXDH) и премиальным UI/UX уровня iPhone 17 + Telegram. Android-приложение на Kotlin + Jetpack Compose с кастомными анимациями, нестандартными компонентами и плавностью 120fps. Бэкенд (7 Go-микросервисов) уже реализован — фокус этого milestone на UI/UX фронтенда.

**Core Value:** Визуально безупречный, моментально отзывчивый мессенджер, который ощущается лучше Telegram и нативнее iOS — на Android.

### Constraints

- **Platform**: Android (minSdk 26, Kotlin + Jetpack Compose)
- **Performance**: 120fps target, <100ms touch response, <16ms frame time
- **Existing code**: Не ломать существующий функционал (crypto, networking, Room DB)
- **Libraries**: Предпочтение нестандартным, современным библиотекам (не Material Design "из коробки")
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Go 1.23 — all 7 backend microservices and shared packages
- Kotlin 2.1.20 — Android client (JVM target 17)
- Rust (edition 2021) — `crypto/` crate for Signal Protocol primitives, compiled to `.so` via `cdylib` and `.a` via `staticlib`
- SQL — PostgreSQL migrations across 5 services (`.sql` files in each service's `migrations/` directory)
- CQL — ScyllaDB schema migration at `services/chat/migrations/001_messages.cql`

## Runtime

- Go runtime 1.23 (Alpine-based Docker build, distroless runtime image `gcr.io/distroless/static-debian12:nonroot`)
- CGO disabled (`CGO_ENABLED=0`) — statically linked binaries
- Android minSdk 26 (Android 8.0), targetSdk/compileSdk 36
- JVM 17
- Tokio async runtime (`rt`, `rt-multi-thread`, `macros` features)
- Compiled via `org.mozilla.rust-android-gradle` plugin v0.9.4 for `arm64` and `x86_64` ABIs

## Package Manager

- Go Modules with workspace file `go.work` at project root
- Each service and shared package has its own `go.mod` — 10 modules total
- Shared packages use `replace` directives pointing to local `../../pkg/` paths
- Gradle 8.9.2 (Kotlin DSL: `build.gradle.kts`)
- Version catalog at `android/gradle/libs.versions.toml`
- KSP 2.1.20-1.0.32 for annotation processing (Hilt, Room)
- Cargo — `crypto/Cargo.toml`
- `libsignal` crate fetched as git dependency from `https://github.com/signalapp/libsignal.git`

## Frameworks

- `github.com/go-chi/chi/v5` v5.2.1 — HTTP router used in every service
- `net/http/httputil.ReverseProxy` (stdlib) — gateway reverse proxy logic at `services/gateway/internal/proxy/proxy.go`
- `github.com/coder/websocket` v1.8.14 — WebSocket server in gateway (`services/gateway/internal/ws/hub.go`)
- Jetpack Compose BOM 2025.03.01 — declarative UI
- Material3 — component library
- Navigation Compose 2.9.0 — in-app navigation
- Lifecycle ViewModel + Runtime 2.9.0
- Hilt 2.56 (Dagger-based) with `hilt-navigation-compose` 1.2.0
- Room 2.7.1 — local SQLite ORM (`KotoDatabase` at `android/app/src/main/java/run/koto/data/local/KotoDatabase.kt`)
- DataStore Preferences 1.1.4 — lightweight key-value storage (`AccountPrefs`, `SettingsPrefs`)
- Retrofit 2.11.0 + Gson converter — typed HTTP client
- OkHttp 4.12.0 + logging interceptor — underlying HTTP client
- Coil 3.1.0 (`coil-compose`, `coil-network-okhttp`)

## Key Dependencies

- `github.com/jackc/pgx/v5` v5.7.2 — PostgreSQL driver (auth, user, notification, media, bot)
- `github.com/gocql/gocql` v1.7.0 — ScyllaDB/Cassandra driver (chat service only)
- `github.com/redis/go-redis/v9` v9.7.3 — Dragonfly (Redis-compatible) client (gateway, auth, chat, user)
- `github.com/nats-io/nats.go` v1.38.0 — NATS JetStream client (gateway, chat, notification, bot)
- `github.com/golang-jwt/jwt/v5` v5.2.1 — JWT signing/verification in `pkg/token`
- `github.com/rs/zerolog` v1.33.0 — structured JSON logging (all services)
- `github.com/sideshow/apns2` v0.25.0 — Apple Push Notification service (notification service)
- `github.com/minio/minio-go/v7` v7.0.90 — MinIO S3-compatible object storage client (media service)
- `github.com/gofrs/uuid/v5` v5.3.0 — UUID generation (chat service)
- `libsignal-protocol` (git, signalapp/libsignal) — Signal Protocol implementation
- `signal-crypto` (git, signalapp/libsignal) — Signal crypto primitives
- `uniffi` v0.28 — generates Kotlin and Swift FFI bindings from Rust
- `zeroize` v1.7 — secure memory wiping for key material
- `org.bouncycastle:bcprov-jdk18on` v1.79 — JVM crypto provider for Signal Protocol primitives
- `net.java.dev.jna:jna` v5.14.0 (AAR) — JNA runtime required by uniffi-generated Kotlin bindings

## Shared Packages

- `pkg/logger` — zerolog-based structured logger
- `pkg/errors` — typed application error types
- `pkg/token` — Ed25519 JWT signing and verification (`pkg/token/jwt.go`)

## Configuration

- All configuration loaded from environment variables (no config files)
- Pattern: each service has `config/config.go` with `Load()` function using `os.Getenv`
- Required vars panic/error on startup if absent (e.g., `POSTGRES_DSN`, `JWT_PRIVATE_KEY_SEED`)
- Defaults provided for optional vars (e.g., `HTTP_ADDR` defaults to service-specific port)
- `BuildConfig` fields injected at compile time in `android/app/build.gradle.kts`
- Debug: `BASE_URL=http://10.0.2.2:8080`, `WS_BASE_URL=ws://10.0.2.2:9080`
- Release: `BASE_URL=https://koto.run`, `WS_BASE_URL=wss://koto.run`
- `.env` present at project root — consumed by `docker-compose.yml` variable substitution

## Build

- `docker-compose.yml` — defines full build and run for all 7 services plus 5 infra components
- Each service has its own `services/{name}/Dockerfile` using multi-stage build:
- `Makefile` at project root — `make stack` поднимает compose, ждёт БД, выполняет `make migrate` и проверяет `http://127.0.0.1:8081/health`; при уже запущенных контейнерах достаточно `make migrate`
- Standard Gradle build: `./gradlew assembleDebug` / `./gradlew assembleRelease`
- `./gradlew cargoBuild` triggers Rust compilation for target ABIs
- ProGuard enabled for release builds (`proguard-rules.pro`)
- uniffi Kotlin bindings generated to `build/generated/uniffi/` and added to source sets

## Platform Requirements

- Docker and Docker Compose for full local stack
- Go 1.23+ toolchain for backend changes
- Android Studio with NDK for Android + Rust crypto changes
- Rust toolchain with Android cross-compilation targets (`aarch64-linux-android`, `x86_64-linux-android`)
- Docker Compose on any Linux VPS or container host
- External ports: gateway REST `:8080`, gateway WebSocket `:9080`
- All infra (postgres, scylladb, dragonfly, nats, minio) run as Docker services — not externally hosted
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Go Services (services/)

### Package Structure

### Naming Patterns

- `snake_case.go` always
- Repo files: `{entity}_repo.go` (e.g. `account_repo.go`)
- Config: always `config.go` inside a `config/` package
- Entry: always `cmd/server/main.go`
- Structs: `PascalCase` — `AccountRepo`, `TokenPair`, `RegisterInput`
- Interfaces: noun or noun+role suffix — `AccountRepository`, `EventPublisher`, `RefreshTokenRepository`
- Type aliases: `PascalCase` — `MessageType`, `ConversationType`
- `PascalCase` for exported — `NewAccountRepo`, `Register`, `FetchPreKeyBundle`
- `camelCase` for unexported — `issueTokenPair`, `generateSecureToken`, `writeJSON`
- Constructor: always `New{Type}(...)` — `NewHandler`, `NewHub`, `NewPool`
- `camelCase` for locals — `accountID`, `accessToken`, `refreshTTL`
- Acronyms stay uppercase: `accountID` (not `accountId`), `HTTPAddr`, `DSN`
- `{TypeName}{Variant}` pattern — `MessageTypeText`, `ConversationTypeDirect`

### Input/Output Types

### Error Handling

### HTTP Handlers

- `writeJSON(w, status, body)` — sets Content-Type, writes
- `writeError(w, status, msg)` — always `{"error": "..."}` shape
- `writeAppError(w, err)` — maps domain error to HTTP status

### Router Construction

### Logging

### Configuration

- `env(key, fallback)` — optional env var with fallback
- `mustEnv(key)` — required env var, panics if missing

### Main Function Pattern

### Repository Pattern

- Postgres: `internal/infra/postgres/`
- ScyllaDB: `internal/infra/scylla/`
- Cache (Dragonfly/Redis): `internal/infra/cache/`

### Comments

- Type: `// TypeName does X.`
- Function: `// FunctionName verb-phrase.`
- Complex logic explained inline: `// Rotate: revoke old token, issue new pair`

## Android App (android/)

### Package Structure

### Naming Patterns

- Screen + ViewModel co-located: `ChatScreen.kt`, `ChatViewModel.kt`
- Entities: `{Name}Entity.kt` — `MessageEntity.kt`
- DAOs: `{Name}Dao.kt` — `MessageDao.kt`
- DTOs in API files, suffixed `Dto` — `ConversationDto`, `MessageDto`
- `PascalCase` throughout: `ChatViewModel`, `ChatRepository`, `CryptoManager`
- Hilt modules: `{Scope}Module` — `AppModule`, `DatabaseModule`, `NetworkModule`
- Navigation: sealed class `Screen` with objects — `Screen.Chat`, `Screen.Onboarding`
- `camelCase`: `sendMessage`, `getMessagesFlow`, `processPreKeyBundle`
- Composables: `PascalCase` matching their purpose: `ChatScreen`, `MessageBubble`, `DateSeparator`
- `SCREAMING_SNAKE_CASE` for top-level: `DISAPPEARING_TTL_MS`, `PUSH_DURATION`
- `PascalCase` for private val formatted as properties: `timeFmt`, `dayFmt`

### State Management (ViewModels)

### Compose Screens

### Repository Pattern

### Room Entities

### Retrofit DTOs

### Alignment Style (Kotlin)

### KDoc Comments

## Rust Crypto Crate (crypto/)

### Module Layout

### Error Pattern

#[derive(Debug, thiserror::Error, uniffi::Error)]

### uniffi Exports

- `#[derive(uniffi::Record)]` for plain data structs
- `#[derive(uniffi::Object)]` for stateful objects with methods
- `#[uniffi::export]` on `impl` blocks and free functions
- `#[uniffi::constructor]` on `fn new()`

### Thread Safety

### Naming

- `snake_case` for all functions, fields, modules
- `PascalCase` for types: `KotoCrypto`, `RegistrationBundle`, `PreKeyData`
- `SCREAMING_SNAKE_CASE` for statics: `RT` (the Tokio runtime)

## Cross-Cutting Patterns

### X-Account-ID Header Convention

### UTC Times

### Module Independence

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## Pattern Overview

- Backend follows Domain-Driven Design with strict layering: `domain` → `app` → `infra` + `transport`
- Single externally-facing gateway (port 8080 REST, port 9080 WebSocket); all other services are internal only
- End-to-end encryption enforced at protocol level — server stores only ciphertext blobs, never plaintext
- NATS JetStream used as async event bus between chat and gateway for real-time delivery
- Android client uses Repository pattern with Room local database as truth source; remote APIs are sync/background only

## Backend Layers (per service)

- Purpose: Pure Go types and repository interfaces. Zero external imports.
- Location: `services/{name}/internal/domain/`
- Contains: Entity structs, repository interfaces, domain-specific value types
- Depends on: Nothing outside stdlib
- Used by: `app` layer
- Purpose: Business logic, calls domain interfaces, never touches infrastructure directly
- Location: `services/{name}/internal/app/`
- Contains: Service struct with use-case methods, input/output DTOs
- Depends on: `domain` interfaces, `pkg/errors`, `pkg/token`
- Used by: `transport` layer
- Purpose: Implements domain repository interfaces against real storage/brokers
- Location: `services/{name}/internal/infra/`
- Contains: PostgreSQL repos (`pgx/v5`), ScyllaDB repos (`gocql`), Redis/Dragonfly cache, NATS publisher, APNs client
- Depends on: `domain` interfaces, external SDKs
- Used by: `cmd/server/main.go` during wiring
- Purpose: Decode HTTP requests, call `app.Service`, encode responses
- Location: `services/{name}/internal/transport/http/`
- Contains: `Handler` struct, `Router()` method (chi), individual handler methods
- Depends on: `app.Service`, `pkg/errors`, `pkg/logger`
- Used by: `cmd/server/main.go`
- Purpose: Load and validate env vars into a typed `Config` struct
- Location: `services/{name}/config/config.go` (present in auth, chat, user)
- Contains: Single `Load() (Config, error)` function
- Depends on: stdlib only
- Used by: `cmd/server/main.go`
- Purpose: Wire dependencies, start HTTP server, handle graceful shutdown
- Location: `services/{name}/cmd/server/main.go`
- Contains: Single `main()` that reads config, creates infra, creates app service, creates handler, serves
- Depends on: All internal layers

## Service Responsibilities

- Exposes REST API on `:8080` and WebSocket on `:9080`
- Validates JWT (verify-only, no private key) via `pkg/token`
- Injects `X-Account-ID` header, proxies to upstream services via `httputil.ReverseProxy`
- Manages WebSocket hub (`internal/ws/Hub`): multi-device fanout, buffered channels per client
- Subscribes to NATS `chat.deliver.>` and pushes `new_message` frames to connected WebSocket clients
- Route table: `/v1/auth/*` → auth (public), all others → respective service (JWT protected)
- Anonymous accounts identified by Ed25519 public key (account ID = hex(pubkey))
- Issues Ed25519-signed JWT access tokens (15m) and opaque refresh tokens (720h)
- Stores refresh tokens in Dragonfly (Redis-compatible) with TTL
- Stores account key material (identity key, signed pre-key, one-time pre-keys) in PostgreSQL
- Manages X3DH key distribution: `POST /v1/auth/prekeys/publish`, `GET /v1/auth/prekeys/bundle/{id}`
- Stores E2EE messages in ScyllaDB with TIMEUUID primary keys and time-window compaction
- Conversations partitioned by `conversation_id`; member lookup via `member_conversations` table
- After `Save`, publishes `domain.DeliveryEvent` to NATS subject `chat.deliver.{convId}`
- Gateway subscribes and fans out to online recipients; offline users get push notifications via notification service
- Messages support per-row TTL for disappearing messages
- Optional user profiles (display name, avatar URL, bio) — all fields optional, anonymity preserved
- Contact list management with block/unblock support
- Signal Protocol PQXDH key bundle management: `UploadPrekeys`, `FetchPrekeyBundle`
- Stores in PostgreSQL via pgx v5
- Subscribes to NATS events (implied by config: `NATS_URL` present)
- Delivers silent APNs push (background, content-available) to iOS — no message content in push payload
- UnifiedPush support for Android (Google-free push)
- Device token registration per account
- Pre-signed URL flow for encrypted file uploads/downloads to MinIO
- File metadata stored in PostgreSQL; binary object stored in MinIO bucket `koto-media`
- Server never sees plaintext — client encrypts before upload, decrypts after download
- Bot registration with webhook URL
- Events delivered to bot's webhook when users interact
- Bots have their own account IDs in the messenger

## Shared Packages (`pkg/`)

- Wraps `zerolog`; `New(service, version, debug)` returns a `zerolog.Logger`
- `WithCtx` / `FromCtx` for context-propagated logging
- Used identically by all services
- Sentinel errors: `ErrNotFound`, `ErrAlreadyExists`, `ErrUnauthorized`, `ErrForbidden`, `ErrInvalidInput`, `ErrInternal`
- `AppError` struct wrapping sentinel + message + cause
- `HTTPStatus(err)` maps sentinels to HTTP status codes — used in every handler's `writeAppError()`
- `New(sentinel, msg)` and `Wrap(sentinel, msg, cause)` constructors
- Ed25519-based JWT manager
- Auth service uses `NewManager(seed, ttl)` (has private key, can issue)
- Gateway uses `NewManagerFromPublicKey(pubkey)` (verify-only)
- `Manager.Issue(accountID)` → signed JWT; `Manager.Verify(token)` → `Claims{AccountID}`

## Data Flow

## Key Abstractions

- Purpose: Decouple business logic from storage technology
- Pattern: Defined in `domain/`, implemented in `infra/`, injected in `main.go`
- Examples: `domain.AccountRepository`, `domain.MessageRepository`, `domain.ConversationRepository`
- Purpose: Single use-case orchestrator per service
- Pattern: Struct with repository interfaces as fields; each method is one use case
- Examples: `services/auth/internal/app/service.go`, `services/chat/internal/app/service.go`
- Purpose: In-process registry of live WebSocket connections, supports multi-device per account
- Location: `services/gateway/internal/ws/hub.go`
- Pattern: `map[accountID][]*Client` with `sync.RWMutex`; buffered send channel per client (size 256)
- Purpose: Pre-configured `httputil.ReverseProxy` per upstream service
- Location: `services/gateway/internal/proxy/proxy.go`
- Pattern: One `Upstream` per service name, registered at gateway startup from env vars
- Purpose: Signal Protocol session state per local account
- Location: `crypto/src/lib.rs`
- Pattern: `Arc<Mutex<InMemSignalProtocolStore>>` wrapped in uniffi `Object`; all methods callable from Kotlin via JNI

## Entry Points

- Location: `services/gateway/cmd/server/main.go:main()`
- Listens: `:8080` (env `HTTP_ADDR`)
- Triggers: Incoming HTTP requests from Android / web clients
- Location: `services/gateway/cmd/server/main.go:wsHandler()`
- Listens: `:9080` (env `WS_ADDR`)
- Triggers: Android `KotoWebSocket.connect(accessToken)` with `?token=` query param
- Location: `services/auth/cmd/server/main.go:main()`
- Listens: `:18001` (env `HTTP_ADDR`, internal only)
- Location: `services/chat/cmd/server/main.go:main()`
- Listens: `:18002` (internal only)

## Error Handling

- Domain layer returns `*apperrors.AppError` wrapping a sentinel (`ErrNotFound`, etc.)
- `apperrors.HTTPStatus(err)` in `writeAppError()` translates to HTTP status
- Repository layer maps `pgx.ErrNoRows` → `ErrNotFound`, unique violations → `ErrAlreadyExists`
- All handlers follow: decode request → call service → if err: `writeAppError(w, err)` → return
- Gateway proxy errors surface as `{"error":"upstream unavailable"}` with HTTP 502
- Rust crypto errors use `thiserror`-derived `CryptoError` enum, exposed to Kotlin as uniffi error type

## Cross-Cutting Concerns

<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.

<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.

<!-- GSD:profile-end -->
