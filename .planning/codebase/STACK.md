# Technology Stack

**Analysis Date:** 2026-04-05

## Languages

**Primary:**
- Go 1.23 — all 7 backend microservices and shared packages
- Kotlin 2.1.20 — Android client (JVM target 17)

**Secondary:**
- Rust (edition 2021) — `crypto/` crate for Signal Protocol primitives, compiled to `.so` via `cdylib` and `.a` via `staticlib`
- SQL — PostgreSQL migrations across 5 services (`.sql` files in each service's `migrations/` directory)
- CQL — ScyllaDB schema migration at `services/chat/migrations/001_messages.cql`

## Runtime

**Backend:**
- Go runtime 1.23 (Alpine-based Docker build, distroless runtime image `gcr.io/distroless/static-debian12:nonroot`)
- CGO disabled (`CGO_ENABLED=0`) — statically linked binaries

**Android:**
- Android minSdk 26 (Android 8.0), targetSdk/compileSdk 36
- JVM 17

**Rust:**
- Tokio async runtime (`rt`, `rt-multi-thread`, `macros` features)
- Compiled via `org.mozilla.rust-android-gradle` plugin v0.9.4 for `arm64` and `x86_64` ABIs

## Package Manager

**Go:**
- Go Modules with workspace file `go.work` at project root
- Each service and shared package has its own `go.mod` — 10 modules total
- Shared packages use `replace` directives pointing to local `../../pkg/` paths

**Android:**
- Gradle 8.9.2 (Kotlin DSL: `build.gradle.kts`)
- Version catalog at `android/gradle/libs.versions.toml`
- KSP 2.1.20-1.0.32 for annotation processing (Hilt, Room)

**Rust:**
- Cargo — `crypto/Cargo.toml`
- `libsignal` crate fetched as git dependency from `https://github.com/signalapp/libsignal.git`

## Frameworks

**Backend HTTP:**
- `github.com/go-chi/chi/v5` v5.2.1 — HTTP router used in every service
- `net/http/httputil.ReverseProxy` (stdlib) — gateway reverse proxy logic at `services/gateway/internal/proxy/proxy.go`

**WebSocket:**
- `github.com/coder/websocket` v1.8.14 — WebSocket server in gateway (`services/gateway/internal/ws/hub.go`)

**Android UI:**
- Jetpack Compose BOM 2025.03.01 — declarative UI
- Material3 — component library
- Navigation Compose 2.9.0 — in-app navigation
- Lifecycle ViewModel + Runtime 2.9.0

**Android DI:**
- Hilt 2.56 (Dagger-based) with `hilt-navigation-compose` 1.2.0

**Android Persistence:**
- Room 2.7.1 — local SQLite ORM (`KotoDatabase` at `android/app/src/main/java/run/koto/data/local/KotoDatabase.kt`)
- DataStore Preferences 1.1.4 — lightweight key-value storage (`AccountPrefs`, `SettingsPrefs`)

**Android Network:**
- Retrofit 2.11.0 + Gson converter — typed HTTP client
- OkHttp 4.12.0 + logging interceptor — underlying HTTP client

**Android Image Loading:**
- Coil 3.1.0 (`coil-compose`, `coil-network-okhttp`)

## Key Dependencies

**Backend Critical:**
- `github.com/jackc/pgx/v5` v5.7.2 — PostgreSQL driver (auth, user, notification, media, bot)
- `github.com/gocql/gocql` v1.7.0 — ScyllaDB/Cassandra driver (chat service only)
- `github.com/redis/go-redis/v9` v9.7.3 — Dragonfly (Redis-compatible) client (gateway, auth, chat, user)
- `github.com/nats-io/nats.go` v1.38.0 — NATS JetStream client (gateway, chat, notification, bot)
- `github.com/golang-jwt/jwt/v5` v5.2.1 — JWT signing/verification in `pkg/token`
- `github.com/rs/zerolog` v1.33.0 — structured JSON logging (all services)
- `github.com/sideshow/apns2` v0.25.0 — Apple Push Notification service (notification service)
- `github.com/minio/minio-go/v7` v7.0.90 — MinIO S3-compatible object storage client (media service)
- `github.com/gofrs/uuid/v5` v5.3.0 — UUID generation (chat service)

**Rust Crypto Critical:**
- `libsignal-protocol` (git, signalapp/libsignal) — Signal Protocol implementation
- `signal-crypto` (git, signalapp/libsignal) — Signal crypto primitives
- `uniffi` v0.28 — generates Kotlin and Swift FFI bindings from Rust
- `zeroize` v1.7 — secure memory wiping for key material

**Android Crypto:**
- `org.bouncycastle:bcprov-jdk18on` v1.79 — JVM crypto provider for Signal Protocol primitives
- `net.java.dev.jna:jna` v5.14.0 (AAR) — JNA runtime required by uniffi-generated Kotlin bindings

## Shared Packages

Located in `pkg/`, used by all services via `go.work` workspace:

- `pkg/logger` — zerolog-based structured logger
- `pkg/errors` — typed application error types
- `pkg/token` — Ed25519 JWT signing and verification (`pkg/token/jwt.go`)

## Configuration

**Backend:**
- All configuration loaded from environment variables (no config files)
- Pattern: each service has `config/config.go` with `Load()` function using `os.Getenv`
- Required vars panic/error on startup if absent (e.g., `POSTGRES_DSN`, `JWT_PRIVATE_KEY_SEED`)
- Defaults provided for optional vars (e.g., `HTTP_ADDR` defaults to service-specific port)

**Android:**
- `BuildConfig` fields injected at compile time in `android/app/build.gradle.kts`
- Debug: `BASE_URL=http://10.0.2.2:8080`, `WS_BASE_URL=ws://10.0.2.2:9080`
- Release: `BASE_URL=https://koto.run`, `WS_BASE_URL=wss://koto.run`

**Environment file:**
- `.env` present at project root — consumed by `docker-compose.yml` variable substitution

## Build

**Backend:**
- `docker-compose.yml` — defines full build and run for all 7 services plus 5 infra components
- Each service has its own `services/{name}/Dockerfile` using multi-stage build:
  1. `golang:1.23-alpine` builder stage compiles binary
  2. `gcr.io/distroless/static-debian12:nonroot` runtime stage (no shell, minimal attack surface)
- `Makefile` at project root

**Android:**
- Standard Gradle build: `./gradlew assembleDebug` / `./gradlew assembleRelease`
- `./gradlew cargoBuild` triggers Rust compilation for target ABIs
- ProGuard enabled for release builds (`proguard-rules.pro`)
- uniffi Kotlin bindings generated to `build/generated/uniffi/` and added to source sets

## Platform Requirements

**Development:**
- Docker and Docker Compose for full local stack
- Go 1.23+ toolchain for backend changes
- Android Studio with NDK for Android + Rust crypto changes
- Rust toolchain with Android cross-compilation targets (`aarch64-linux-android`, `x86_64-linux-android`)

**Production:**
- Docker Compose on any Linux VPS or container host
- External ports: gateway REST `:8080`, gateway WebSocket `:9080`
- All infra (postgres, scylladb, dragonfly, nats, minio) run as Docker services — not externally hosted

---

*Stack analysis: 2026-04-05*
