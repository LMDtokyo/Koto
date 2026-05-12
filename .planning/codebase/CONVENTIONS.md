# Coding Conventions

**Analysis Date:** 2026-04-05

## Go Services (services/)

### Package Structure

Every service follows the same package layout without exception:

```
services/{name}/
├── cmd/server/main.go          # Wire-up only — no logic
├── config/config.go            # Env-var loading, Config struct
├── internal/
│   ├── app/service.go          # Use-case orchestration
│   ├── domain/{entity}.go      # Entities + repository interfaces
│   ├── infra/{backend}/*.go    # Repository implementations
│   └── transport/http/handler.go
└── go.mod                      # Separate module per service
```

Package doc comment is required on every file: `// Package app contains the application use-cases for the auth service.`

### Naming Patterns

**Files:**
- `snake_case.go` always
- Repo files: `{entity}_repo.go` (e.g. `account_repo.go`)
- Config: always `config.go` inside a `config/` package
- Entry: always `cmd/server/main.go`

**Types:**
- Structs: `PascalCase` — `AccountRepo`, `TokenPair`, `RegisterInput`
- Interfaces: noun or noun+role suffix — `AccountRepository`, `EventPublisher`, `RefreshTokenRepository`
- Type aliases: `PascalCase` — `MessageType`, `ConversationType`

**Functions and Methods:**
- `PascalCase` for exported — `NewAccountRepo`, `Register`, `FetchPreKeyBundle`
- `camelCase` for unexported — `issueTokenPair`, `generateSecureToken`, `writeJSON`
- Constructor: always `New{Type}(...)` — `NewHandler`, `NewHub`, `NewPool`

**Variables:**
- `camelCase` for locals — `accountID`, `accessToken`, `refreshTTL`
- Acronyms stay uppercase: `accountID` (not `accountId`), `HTTPAddr`, `DSN`

**Constants:**
- `{TypeName}{Variant}` pattern — `MessageTypeText`, `ConversationTypeDirect`

### Input/Output Types

Use-case input and output types live in `internal/app/` alongside the service:

```go
// Input structs suffix "Input"
type RegisterInput struct {
    IdentityKey     []byte
    SignedPreKey    []byte
    SignedPreKeyID  uint32
    OneTimePreKeys  [][]byte
}

// Output structs are named by what they contain
type TokenPair struct {
    AccountID    string
    AccessToken  string
    RefreshToken string
    ExpiresAt    time.Time
}
```

### Error Handling

**Sentinel errors** defined in `pkg/errors/errors.go`:

```go
var (
    ErrNotFound      = errors.New("not found")
    ErrAlreadyExists = errors.New("already exists")
    ErrUnauthorized  = errors.New("unauthorized")
    ErrForbidden     = errors.New("forbidden")
    ErrInvalidInput  = errors.New("invalid input")
    ErrInternal      = errors.New("internal error")
)
```

**AppError wrapper** used at all domain boundaries:

```go
// Domain validation
return apperrors.New(apperrors.ErrInvalidInput, "identity_key must be 32 bytes")

// Infrastructure wrapping
return apperrors.Wrap(apperrors.ErrInternal, "create account", err)

// ErrNotFound from infra
if errors.Is(err, pgx.ErrNoRows) {
    return domain.Account{}, apperrors.New(apperrors.ErrNotFound, "account not found")
}
```

**HTTP mapping** via `apperrors.HTTPStatus(err)` in `writeAppError`:

```go
func writeAppError(w http.ResponseWriter, err error) {
    status := apperrors.HTTPStatus(err)
    writeJSON(w, status, map[string]string{"error": err.Error()})
}
```

Rule: infra layer always wraps raw errors before returning. App layer checks specific sentinels with `apperrors.Is(err, apperrors.ErrNotFound)`.

### HTTP Handlers

Handlers live in `internal/transport/http/handler.go`. Pattern:

```go
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
    var body struct { ... }
    if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
        writeError(w, http.StatusBadRequest, "invalid JSON body")
        return
    }
    // validate inline, early return on error
    result, err := h.svc.SomeMethod(r.Context(), input)
    if err != nil {
        writeAppError(w, err)
        return
    }
    writeJSON(w, http.StatusOK, map[string]any{...})
}
```

Three helper functions used everywhere:
- `writeJSON(w, status, body)` — sets Content-Type, writes
- `writeError(w, status, msg)` — always `{"error": "..."}` shape
- `writeAppError(w, err)` — maps domain error to HTTP status

### Router Construction

All routes registered in `handler.Router()` method using chi:

```go
r.Use(middleware.RequestID)
r.Use(middleware.RealIP)
r.Use(middleware.Recoverer)
r.Use(requestLoggerMiddleware(h.log))

r.Route("/v1/auth", func(r chi.Router) {
    r.Post("/register", h.Register)
})
```

### Logging

Using `zerolog` via `pkg/logger`. Logger propagated through context:

```go
// Attach to context in middleware
ctx := logger.WithCtx(r.Context(), log.With().Str("req_id", ...).Logger())

// Retrieve in handler/service
log := logger.FromCtx(ctx)
log.Info().Str("account", id).Msg("account created")
```

Service logger initialised in `main.go`:

```go
log := logger.New("auth", version, cfg.LogLevel == "debug")
```

Structured fields use `.Str(key, value)`, `.Err(err)`, `.Int(key, n)`. Message is last via `.Msg("...")`.

### Configuration

Every service has `config/config.go` with a `Config` struct and `Load()` function:

```go
func Load() (Config, error) {
    return Config{
        HTTPAddr:    env("HTTP_ADDR", ":18001"),   // optional with default
        PostgresDSN: mustEnv("POSTGRES_DSN"),       // required — panics if absent
    }, nil
}
```

- `env(key, fallback)` — optional env var with fallback
- `mustEnv(key)` — required env var, panics if missing

### Main Function Pattern

`cmd/server/main.go` wires all dependencies in order:

1. Load config
2. Init logger
3. Connect infra (postgres, redis/dragonfly, etc.)
4. Construct repositories
5. Construct app service
6. Construct HTTP handler
7. Start HTTP server in goroutine
8. Block on OS signal
9. Graceful shutdown with 15s timeout

Section separators use `// ─── Section name ─────────────────────` style.

### Repository Pattern

Domain layer defines interfaces in `internal/domain/`:

```go
type AccountRepository interface {
    // Create stores a new account. Returns ErrAlreadyExists if ID is taken.
    Create(ctx context.Context, a Account) error
    // Get retrieves an account by ID. Returns ErrNotFound if absent.
    Get(ctx context.Context, id string) (Account, error)
}
```

Infrastructure implementations named `{Entity}Repo` in `internal/infra/{backend}/`:
- Postgres: `internal/infra/postgres/`
- ScyllaDB: `internal/infra/scylla/`
- Cache (Dragonfly/Redis): `internal/infra/cache/`

### Comments

Every exported type and function has a doc comment. Format:
- Type: `// TypeName does X.`
- Function: `// FunctionName verb-phrase.`
- Complex logic explained inline: `// Rotate: revoke old token, issue new pair`

---

## Android App (android/)

### Package Structure

```
run.koto/
├── crypto/         # CryptoManager, KeyManager (Rust FFI wrapper)
├── data/
│   ├── local/
│   │   ├── dao/    # Room DAOs
│   │   └── entity/ # Room entities with toUi() mappers
│   ├── prefs/      # DataStore wrappers (AccountPrefs, SettingsPrefs)
│   ├── remote/
│   │   ├── api/    # Retrofit interfaces + DTOs
│   │   └── ws/     # WebSocket client
│   └── repository/ # Single source of truth
├── di/             # Hilt modules
├── domain/model/   # UI models (ConversationUi, MessageUi)
├── security/       # AppLockManager
└── ui/
    ├── components/  # Reusable @Composable functions
    ├── navigation/  # KotoNavGraph, Screen sealed class
    ├── screens/     # {ScreenName}Screen.kt + {ScreenName}ViewModel.kt
    └── theme/       # Color.kt, Theme.kt, Type.kt
```

### Naming Patterns

**Files:**
- Screen + ViewModel co-located: `ChatScreen.kt`, `ChatViewModel.kt`
- Entities: `{Name}Entity.kt` — `MessageEntity.kt`
- DAOs: `{Name}Dao.kt` — `MessageDao.kt`
- DTOs in API files, suffixed `Dto` — `ConversationDto`, `MessageDto`

**Classes:**
- `PascalCase` throughout: `ChatViewModel`, `ChatRepository`, `CryptoManager`
- Hilt modules: `{Scope}Module` — `AppModule`, `DatabaseModule`, `NetworkModule`
- Navigation: sealed class `Screen` with objects — `Screen.Chat`, `Screen.Onboarding`

**Functions:**
- `camelCase`: `sendMessage`, `getMessagesFlow`, `processPreKeyBundle`
- Composables: `PascalCase` matching their purpose: `ChatScreen`, `MessageBubble`, `DateSeparator`

**Constants:**
- `SCREAMING_SNAKE_CASE` for top-level: `DISAPPEARING_TTL_MS`, `PUSH_DURATION`
- `PascalCase` for private val formatted as properties: `timeFmt`, `dayFmt`

### State Management (ViewModels)

All ViewModels follow MVI/UDF with `StateFlow`:

```kotlin
data class ChatState(
    val messages : List<MessageUi> = emptyList(),
    val sending  : Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(...) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()

    fun sendMessage() {
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            chatRepository.sendMessage(...)
            _state.update { it.copy(sending = false) }
        }
    }
}
```

Screen collects with `collectAsState()`. Events go through the ViewModel (never directly from composable to repository).

### Compose Screens

Each screen receives ViewModel via `hiltViewModel()`:

```kotlin
@Composable
fun ChatScreen(
    convId    : String,
    viewModel : ChatViewModel = hiltViewModel(),
    onBack    : () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(convId) { viewModel.load(convId) }
    ...
}
```

Private composable functions are lowercase prefixed with the screen name:

```kotlin
@Composable private fun ChatTopBar(...)
@Composable private fun MessageBubble(...)
@Composable private fun MessageRow(...)
```

Section separators: `// ─── Section Name ────────────────────────────────`

### Repository Pattern

Repositories are `@Singleton`, injected via Hilt, expose `Flow<T>` for observable data and `suspend Result<T>` for mutations:

```kotlin
fun getMessagesFlow(convId: String): Flow<List<MessageUi>>
suspend fun sendMessage(convId: String, plaintext: String): Result<Unit>
```

Use `runCatching { ... }` for wrapping suspend calls that can fail:

```kotlin
suspend fun createDirectConversation(peerId: String): Result<String> = runCatching {
    val resp = chatApi.createConversation(...)
    resp.conversation_id
}
```

### Room Entities

Entities use `@ColumnInfo(name = "snake_case")` for DB column names. Companion mapper function `fun {Entity}.toUi()` defined at file scope:

```kotlin
@Entity(tableName = "messages", indices = [Index("conversation_id")])
data class MessageEntity(
    @PrimaryKey val id : String,
    @ColumnInfo(name = "conversation_id") val conversationId : String,
    ...
)

fun MessageEntity.toUi() = MessageUi(id = id, text = plaintextCache, ...)
```

### Retrofit DTOs

DTOs use `snake_case` field names to match server JSON exactly:

```kotlin
data class MessageDto(
    val id         : String,
    val ciphertext : String,
    val sender_id  : String,
    val sent_at    : Long,
)
```

### Alignment Style (Kotlin)

Constructor parameters and assignments are vertically aligned using spaces (not just standard indentation):

```kotlin
data class ChatState(
    val displayName : String  = "",
    val online      : Boolean = false,
    val messages    : List<MessageUi> = emptyList(),
)
```

This is consistent throughout all data classes, DAOs, and DI modules.

### KDoc Comments

Public functions and classes use KDoc with backtick emphasis:

```kotlin
/**
 * Thin Kotlin wrapper around the Rust `KotoCrypto` uniffi bindings.
 *
 * Responsibilities:
 *  - Load / create identity keys on first launch
 *  - Expose suspend functions safe to call from coroutines
 */
@Singleton
class CryptoManager @Inject constructor(...)
```

---

## Rust Crypto Crate (crypto/)

### Module Layout

```
crypto/src/
├── lib.rs      # All public API — KotoCrypto struct, free functions, uniffi types
└── error.rs    # CryptoError enum with thiserror
```

### Error Pattern

`thiserror` + `uniffi::Error` derive:

```rust
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CryptoError {
    #[error("decryption failed: {reason}")]
    DecryptionFailed { reason: String },
    #[error("internal crypto error: {reason}")]
    Internal { reason: String },
}

impl From<libsignal_protocol::SignalProtocolError> for CryptoError {
    fn from(e: ...) -> Self { ... }
}
```

All public functions return `Result<T, CryptoError>`. Internal errors are mapped to enum variants with a `reason: String` field.

### uniffi Exports

Types shared with Kotlin/Swift are annotated:
- `#[derive(uniffi::Record)]` for plain data structs
- `#[derive(uniffi::Object)]` for stateful objects with methods
- `#[uniffi::export]` on `impl` blocks and free functions
- `#[uniffi::constructor]` on `fn new()`

### Thread Safety

`KotoCrypto` holds `Mutex<InMemSignalProtocolStore>`. All methods take `self: Arc<Self>`. Async libsignal calls are blocked via a global Tokio runtime (`Lazy<Runtime>`).

### Naming

- `snake_case` for all functions, fields, modules
- `PascalCase` for types: `KotoCrypto`, `RegistrationBundle`, `PreKeyData`
- `SCREAMING_SNAKE_CASE` for statics: `RT` (the Tokio runtime)

---

## Cross-Cutting Patterns

### X-Account-ID Header Convention

The gateway injects `X-Account-ID` after JWT validation. Upstream services read it directly from the HTTP header — no JWT parsing in individual services.

### UTC Times

All timestamps stored and compared as UTC. Go: `time.Now().UTC()`. Kotlin: `System.currentTimeMillis()` (epoch ms).

### Module Independence

Each Go service is a separate Go module with its own `go.mod`. Shared packages (`pkg/errors`, `pkg/logger`, `pkg/token`) referenced via `replace` directives pointing to local paths.

---

*Convention analysis: 2026-04-05*
