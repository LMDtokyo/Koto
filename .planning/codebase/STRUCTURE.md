# Codebase Structure

**Analysis Date:** 2026-04-05

## Directory Layout

```
koto/
├── services/               # 7 Go microservices
│   ├── gateway/            # Public-facing API gateway (REST :8080 + WS :9080)
│   ├── auth/               # Account registration, JWT, pre-key distribution
│   ├── chat/               # Message storage and delivery (ScyllaDB + NATS)
│   ├── user/               # Profiles, contacts, PQXDH key bundles
│   ├── notification/       # Push notifications (APNs + UnifiedPush)
│   ├── media/              # Encrypted file storage (MinIO)
│   └── bot/                # Bot registration and webhook delivery
├── pkg/                    # Shared Go packages (go.work workspace)
│   ├── logger/             # zerolog wrapper — logger.New(service, version, debug)
│   ├── errors/             # Sentinel errors + AppError wrapper + HTTPStatus()
│   └── token/              # Ed25519 JWT manager — Issue + Verify
├── crypto/                 # Rust Signal Protocol library (uniffi → Kotlin/Swift)
│   └── src/
│       ├── lib.rs          # KotoCrypto struct + generate_registration_bundle()
│       └── error.rs        # CryptoError enum
├── android/                # Android app (Kotlin + Jetpack Compose)
│   └── app/src/main/java/run/koto/
│       ├── crypto/         # CryptoManager.kt — wraps Rust KotoCrypto via uniffi
│       ├── data/
│       │   ├── local/      # Room database: DAOs + entities
│       │   ├── prefs/      # AccountPrefs + SettingsPrefs (DataStore)
│       │   ├── remote/
│       │   │   ├── api/    # Retrofit API interfaces
│       │   │   └── ws/     # KotoWebSocket.kt
│       │   └── repository/ # AuthRepository, ChatRepository, MediaRepository
│       ├── di/             # Hilt modules: AppModule, DatabaseModule, NetworkModule
│       ├── domain/model/   # UI-facing models: ConversationUi, MessageUi, etc.
│       ├── security/       # Android Keystore helpers
│       └── ui/
│           ├── components/ # Shared composables
│           ├── navigation/ # KotoNavGraph.kt — Screen sealed class + NavHost
│           ├── screens/    # chat/, conversations/, onboarding/, settings/, lock/
│           └── theme/      # Material 3 theme
├── infra/
│   └── scripts/keygen/     # Key generation utilities
├── docker-compose.yml      # All services + infra (postgres, scylla, dragonfly, nats, minio)
├── go.work                 # Go workspace — includes all services + pkg/*
├── Makefile                # Build targets
└── deploy-crypto.sh        # Script to build and deploy Rust .so to Android
```

---

## Service Internal Structure (uniform across all services)

Every service follows this exact layout:

```
services/{name}/
├── cmd/server/
│   └── main.go             # Wire dependencies, start HTTP server, graceful shutdown
├── config/
│   └── config.go           # Load() reads env vars → typed Config struct (not in all services)
├── internal/
│   ├── domain/             # Entities + repository interfaces (no external imports)
│   ├── app/
│   │   └── service.go      # Use-case orchestration — calls domain interfaces
│   ├── infra/              # Repository implementations (postgres/, scylla/, broker/, cache/)
│   └── transport/
│       └── http/
│           └── handler.go  # chi Router, HTTP handlers, writeJSON/writeError helpers
└── migrations/             # SQL or CQL files (001_*.sql, 002_*.sql)
```

---

## Directory Purposes

**`services/gateway/`:**
- Purpose: Single external entry point for all clients
- Key files:
  - `cmd/server/main.go` — router setup, NATS subscription, WS hub wiring
  - `internal/proxy/proxy.go` — `httputil.ReverseProxy` wrapper (`Upstream` struct)
  - `internal/ws/hub.go` — WebSocket hub with `map[accountID][]*Client`
  - `internal/middleware/auth.go` — `JWTAuth` middleware that injects `X-Account-ID`

**`services/auth/`:**
- Purpose: Account creation, JWT lifecycle, Signal pre-key distribution
- Key files:
  - `internal/domain/account.go` — `Account`, `OneTimePreKey`, repository interfaces
  - `internal/app/service.go` — `Register`, `RefreshTokens`, `FetchPreKeyBundle`, etc.
  - `internal/infra/postgres/account_repo.go` — pgx v5 implementations, atomic `Pop` via CTE
  - `internal/infra/cache/` — Dragonfly-backed `RefreshTokenCache`
  - `migrations/001_accounts.sql` — accounts + one_time_pre_keys tables

**`services/chat/`:**
- Purpose: E2EE message persistence and delivery event publishing
- Key files:
  - `internal/domain/message.go` — `Message`, `Conversation`, `DeliveryEvent`
  - `internal/app/service.go` — `SendMessage`, `GetHistory`, `CreateConversation`
  - `internal/infra/scylla/` — ScyllaDB message + conversation repos
  - `internal/infra/broker/nats_publisher.go` — NATS `Publish` to `chat.deliver.{convId}`
  - `migrations/001_messages.cql` — ScyllaDB tables with TWCS compaction

**`services/user/`:**
- Purpose: Optional profiles, contact management, PQXDH key bundles
- Key files:
  - `internal/domain/user.go` — `Profile`, `Contact`, `PrekeyBundle`, `OneTimePrekey`
  - `internal/app/service.go` — `UpdateProfile`, `AddContact`, `UploadPrekeys`, `FetchPrekeyBundle`

**`services/notification/`:**
- Purpose: Offline push delivery
- Key files:
  - `internal/domain/device.go` — `Device` with `Platform` (iOS/Android)
  - `internal/infra/apns/client.go` — silent APNs push (background, no content in payload)
  - `internal/infra/unified_push/client.go` — Android UnifiedPush delivery

**`services/media/`:**
- Purpose: Pre-signed URL generation for encrypted file storage
- Key files:
  - `internal/domain/file.go` — `File` metadata entity
  - `cmd/server/main.go` — MinIO client wiring

**`services/bot/`:**
- Purpose: Bot API (Telegram-like webhook model)
- Key files:
  - `internal/domain/bot.go` — `Bot`, `BotEvent`, `BotRepository`

**`pkg/`:**
- Purpose: Shared Go code imported by all services via `go.work`
- Import path prefix: `github.com/koto-messenger/koto/pkg/`
- `pkg/logger/logger.go` — `New(service, version, debug) zerolog.Logger`
- `pkg/errors/errors.go` — sentinels, `AppError`, `HTTPStatus()`
- `pkg/token/` — `Manager` struct, `Issue()`, `Verify()`

**`crypto/`:**
- Purpose: Signal Protocol crypto compiled to `.so` for Android JNI
- Key files:
  - `crypto/src/lib.rs` — `KotoCrypto` uniffi Object, `generate_registration_bundle()` free function
  - `crypto/src/error.rs` — `CryptoError` enum
  - `crypto/Cargo.toml` — `libsignal-protocol` + `signal-crypto` git deps, uniffi 0.28

**`android/app/src/main/java/run/koto/`:**
- Purpose: Android application (MVVM + Repository)
- `crypto/CryptoManager.kt` — Kotlin wrapper around `uniffi.koto_crypto.KotoCrypto`
- `data/remote/api/` — `AuthApi.kt`, `ChatApi.kt`, `UserApi.kt`, `MediaApi.kt` (Retrofit interfaces)
- `data/remote/ws/KotoWebSocket.kt` — OkHttp WebSocket client, emits `WsEvent` Flow
- `data/repository/ChatRepository.kt` — Room + remote sync + WebSocket event handler + crypto calls
- `data/local/dao/` — `MessageDao.kt`, `ConversationDao.kt` (Room)
- `data/local/entity/` — `MessageEntity.kt`, `ConversationEntity.kt`
- `data/prefs/` — `AccountPrefs` (encrypted DataStore for keys + tokens), `SettingsPrefs`
- `di/AppModule.kt` — Hilt: OkHttpClient, Retrofit, KotoWebSocket
- `di/DatabaseModule.kt` — Hilt: Room database, DAOs
- `di/NetworkModule.kt` — Hilt: API interfaces
- `ui/navigation/KotoNavGraph.kt` — `Screen` sealed class, NavHost with Material 3 motion spec
- `ui/screens/chat/ChatScreen.kt` + `ChatViewModel.kt`
- `ui/screens/conversations/` — conversation list screen
- `ui/screens/onboarding/` — registration flow
- `ui/screens/settings/` — settings screen
- `ui/screens/lock/` — app lock / biometric screen

---

## Key File Locations

**Entry Points:**
- `services/gateway/cmd/server/main.go` — REST + WS gateway (public-facing)
- `services/auth/cmd/server/main.go` — auth service startup
- `services/chat/cmd/server/main.go` — chat service startup
- `services/user/cmd/server/main.go` — user service startup
- `services/notification/cmd/server/main.go` — notification service startup
- `services/media/cmd/server/main.go` — media service startup
- `services/bot/cmd/server/main.go` — bot service startup

**Configuration:**
- `docker-compose.yml` — all service env vars, infra ports, health checks
- `services/auth/config/config.go` — auth config struct + Load()
- `services/chat/config/config.go` — chat config struct + Load()

**Core Domain Logic:**
- `services/auth/internal/app/service.go` — Register, RefreshTokens, FetchPreKeyBundle
- `services/chat/internal/app/service.go` — SendMessage, GetHistory, CreateConversation
- `services/user/internal/app/service.go` — profile + contacts + prekey management
- `services/gateway/internal/ws/hub.go` — WebSocket multi-device fanout
- `crypto/src/lib.rs` — KotoCrypto (X3DH + Kyber + Double Ratchet)
- `android/app/src/main/java/run/koto/data/repository/ChatRepository.kt` — Android message lifecycle

**Shared Abstractions:**
- `pkg/errors/errors.go` — sentinel errors + HTTPStatus
- `pkg/token/` — JWT issue + verify
- `pkg/logger/logger.go` — zerolog service logger

**Database Migrations:**
- `services/auth/migrations/001_accounts.sql` — accounts + one_time_pre_keys
- `services/auth/migrations/002_prekeys.sql` — additional pre-key tables (Signal PQXDH migration)
- `services/chat/migrations/001_messages.cql` — ScyllaDB messages + conversations (TWCS)
- `services/user/migrations/001_users.sql` — profiles + contacts
- `services/user/migrations/002_prekeys.sql` — PQXDH key bundle tables
- `services/media/migrations/001_files.sql` — files metadata
- `services/media/migrations/002_is_public.sql` — is_public column addition

---

## Naming Conventions

**Go service files:**
- Domain entities: `{entity}.go` (e.g. `account.go`, `message.go`)
- Repository interface: defined in same file as entity or `{entity}_repo.go`
- Service: always `service.go` in `app/`
- Handler: always `handler.go` in `transport/http/`
- Infra adapters: `{storage_type}.go` or named by entity (e.g. `account_repo.go`, `nats_publisher.go`)
- Config: always `config.go` in `config/`
- Entry point: always `main.go` in `cmd/server/`

**Go packages:**
- Internal packages use `package domain`, `package app`, `package http` (qualified as `httptransport` on import to avoid collision with stdlib)
- Shared packages use `package logger`, `package errors`, `package token`

**Go types:**
- Repository interfaces: `{Entity}Repository` (e.g. `AccountRepository`, `MessageRepository`)
- Service structs: `Service` (single per package, no prefix)
- Handler struct: `Handler`
- Input DTOs: `{UseCase}Input` (e.g. `RegisterInput`, `SendInput`)

**Android files:**
- API interfaces: `{Domain}Api.kt` (e.g. `ChatApi.kt`)
- Repositories: `{Domain}Repository.kt` (e.g. `ChatRepository.kt`)
- ViewModels: `{Screen}ViewModel.kt`
- Screens: `{Screen}Screen.kt`
- Room entities: `{Entity}Entity.kt`
- Room DAOs: `{Entity}Dao.kt`

**Rust:**
- Public API types: PascalCase structs (`KotoCrypto`, `RegistrationBundle`)
- uniffi exported functions: `snake_case` with `#[uniffi::export]`

---

## Where to Add New Code

**New Go microservice:**
- Create `services/{name}/` following the exact structure: `cmd/server/main.go`, `internal/domain/`, `internal/app/service.go`, `internal/infra/`, `internal/transport/http/handler.go`, `migrations/`
- Add to `go.work` `use` block
- Add to `docker-compose.yml` with `HTTP_ADDR`, `POSTGRES_DSN`, `JWT_PUBLIC_KEY`, `NATS_URL` as appropriate
- Register upstream in gateway's `main.go` upstream map and route table

**New endpoint in existing service:**
- Add handler method to `services/{name}/internal/transport/http/handler.go`
- Add use-case method to `services/{name}/internal/app/service.go`
- Add domain type/interface to `services/{name}/internal/domain/`
- Register route in `handler.Router()`

**New repository:**
- Define interface in `services/{name}/internal/domain/`
- Implement in `services/{name}/internal/infra/postgres/` or `infra/scylla/`
- Inject in `services/{name}/cmd/server/main.go`

**New Android screen:**
- Add `Screen` object to `android/app/src/main/java/run/koto/ui/navigation/KotoNavGraph.kt`
- Create `{Name}Screen.kt` + `{Name}ViewModel.kt` in `android/app/src/main/java/run/koto/ui/screens/{name}/`
- Add `composable()` entry in `KotoNavGraph`

**New shared Go package:**
- Create `pkg/{name}/` with `go.mod`
- Add to `go.work` `use` block

**Database schema change:**
- Add `00N_{description}.sql` (PostgreSQL) or `.cql` (ScyllaDB) in `services/{name}/migrations/`
- Apply manually: `psql -U $POSTGRES_USER -d koto -f 00N_*.sql`

---

## Special Directories

**`.planning/`:**
- Purpose: GSD planning documents
- Generated: No (human/AI authored)
- Committed: Yes

**`crypto/target/`:**
- Purpose: Rust build artifacts
- Generated: Yes (cargo build)
- Committed: No

**`android/.gradle/`, `android/build/`:**
- Purpose: Gradle build caches and outputs
- Generated: Yes
- Committed: No

**`android/app/src/main/res/`:**
- Purpose: Android resources (icons, drawables, values, XML configs)
- Generated: Partially (mipmap icons generated at multiple densities)
- Committed: Yes

---

*Structure analysis: 2026-04-05*
