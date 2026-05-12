# Testing Patterns

**Analysis Date:** 2026-04-05

## Current State

**No tests exist in this codebase.** Exploration confirmed zero test files across all three sub-projects:

- Go services: no `*_test.go` files found in `services/`
- Android app: `src/test/` and `src/androidTest/` directories are absent
- Rust crate: no `#[cfg(test)]` or `#[test]` blocks in `crypto/src/`

The architecture is well-structured for testing (interfaces, DI, clean layers) but tests have not been written yet.

---

## Recommended Testing Approach

The sections below describe **how testing should be implemented** given the existing architecture. Follow these patterns when adding tests.

---

## Go Services

### Framework

**Runner:** standard `testing` package (Go built-in)
**Test doubles:** define in-package fakes that satisfy domain interfaces
**Assertions:** `testify/assert` and `testify/require` (add to go.mod when writing first test)

**Run commands:**
```bash
go test ./...                       # Run all tests in a service
go test ./internal/app/...          # App layer only
go test -v ./internal/app/...       # Verbose output
go test -race ./...                 # Race detector (required for concurrent code)
go test -cover ./...                # Coverage output
go test -coverprofile=coverage.out ./... && go tool cover -html=coverage.out
```

### Test File Organization

Place test files co-located with the code they test, using `_test` package suffix for black-box tests:

```
services/auth/internal/app/
├── service.go
└── service_test.go         # package app_test (black-box)

services/auth/internal/domain/
└── account.go              # no test file needed — pure structs/interfaces
```

### What to Test First (Priority Order)

1. `internal/app/` — use-case logic: the most valuable tests, zero infra dependencies
2. `internal/transport/http/` — handler request/response mapping using `httptest`
3. `pkg/` shared packages — `pkg/errors`, `pkg/token`

### App Layer Test Pattern

Domain interfaces make the app layer trivially testable with in-memory fakes:

```go
// services/auth/internal/app/service_test.go
package app_test

import (
    "context"
    "testing"
    "time"

    "github.com/stretchr/testify/require"
    "github.com/koto-messenger/koto/services/auth/internal/app"
    "github.com/koto-messenger/koto/services/auth/internal/domain"
)

// ── fakes ────────────────────────────────────────────────────────────────────

type fakeAccountRepo struct {
    accounts map[string]domain.Account
}

func newFakeAccountRepo() *fakeAccountRepo {
    return &fakeAccountRepo{accounts: make(map[string]domain.Account)}
}

func (r *fakeAccountRepo) Create(ctx context.Context, a domain.Account) error {
    if _, exists := r.accounts[a.ID]; exists {
        return apperrors.New(apperrors.ErrAlreadyExists, "account already exists")
    }
    r.accounts[a.ID] = a
    return nil
}

func (r *fakeAccountRepo) Get(ctx context.Context, id string) (domain.Account, error) {
    a, ok := r.accounts[id]
    if !ok {
        return domain.Account{}, apperrors.New(apperrors.ErrNotFound, "account not found")
    }
    return a, nil
}

func (r *fakeAccountRepo) Exists(ctx context.Context, id string) (bool, error) {
    _, ok := r.accounts[id]
    return ok, nil
}

// ── tests ─────────────────────────────────────────────────────────────────────

func TestRegister_ValidInput_ReturnsTokenPair(t *testing.T) {
    svc := app.New(
        newFakeAccountRepo(),
        newFakePreKeyRepo(),
        newFakeRefreshRepo(),
        mustTokenManager(t),
        time.Hour,
    )

    pair, err := svc.Register(context.Background(), validRegisterInput())
    require.NoError(t, err)
    require.NotEmpty(t, pair.AccessToken)
    require.NotEmpty(t, pair.RefreshToken)
}

func TestRegister_InvalidKey_ReturnsInvalidInput(t *testing.T) {
    svc := newTestService(t)
    in := validRegisterInput()
    in.IdentityKey = []byte("short")

    _, err := svc.Register(context.Background(), in)
    require.Error(t, err)
    require.True(t, apperrors.Is(err, apperrors.ErrInvalidInput))
}
```

### HTTP Handler Test Pattern

Use `net/http/httptest` — no real server needed:

```go
// services/auth/internal/transport/http/handler_test.go
package http_test

import (
    "bytes"
    "encoding/json"
    "net/http"
    "net/http/httptest"
    "testing"

    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
)

func TestRegisterHandler_MissingBody_Returns400(t *testing.T) {
    h := NewHandler(newMockService(), zerolog.Nop())
    r := httptest.NewRequest(http.MethodPost, "/v1/auth/register", bytes.NewReader([]byte("{")))
    w := httptest.NewRecorder()

    h.Router().ServeHTTP(w, r)

    assert.Equal(t, http.StatusBadRequest, w.Code)
    var resp map[string]string
    require.NoError(t, json.NewDecoder(w.Body).Decode(&resp))
    assert.Contains(t, resp["error"], "invalid")
}
```

### Mocking Strategy

**Preferred: in-package fakes** (not generated mocks). Create small structs that implement the domain interface directly. Benefits:
- Compile-time checked
- Readable test setup
- No mock library dependency

Domain repository interfaces in `services/{name}/internal/domain/` are the injection points. Build one fake per interface per service's test files.

**If mock generation is needed later:** use `github.com/vektra/mockery` v2 with `--with-expecter` flag.

### What NOT to Test

- `internal/domain/` structs — pure data, no logic
- `config/config.go` — env-var parsing tested by running
- `cmd/server/main.go` — wiring, covered by integration

---

## Android App

### Framework

**Unit tests:** JUnit 4 + Kotlin Coroutines Test (`kotlinx-coroutines-test`)
**UI tests:** Compose UI Testing (`androidx.compose.ui:ui-test-junit4`)
**Mocking:** MockK (`io.mockk:mockk`)

Add to `app/build.gradle.kts`:
```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("io.mockk:mockk:1.13.x")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.x")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

**Run commands:**
```bash
./gradlew test                      # JVM unit tests
./gradlew connectedAndroidTest      # Instrumented tests (requires device/emulator)
./gradlew testDebugUnitTest         # Debug variant only
```

### Test File Locations

```
android/app/src/
├── test/java/run/koto/             # JVM unit tests (ViewModels, Repositories)
│   ├── ui/screens/chat/
│   │   └── ChatViewModelTest.kt
│   └── data/repository/
│       └── ChatRepositoryTest.kt
└── androidTest/java/run/koto/      # Instrumented/UI tests
    └── ui/screens/
        └── ChatScreenTest.kt
```

### ViewModel Test Pattern

ViewModels are pure JVM — no Android context needed:

```kotlin
// ChatViewModelTest.kt
class ChatViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()  // replaces Main dispatcher

    private val chatRepository = mockk<ChatRepository>()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        viewModel = ChatViewModel(chatRepository)
    }

    @Test
    fun `sendMessage clears input and sets sending true`() = runTest {
        // Given
        coEvery { chatRepository.sendMessage(any(), any()) } returns Result.success(Unit)
        viewModel.onInputChange("Hello")

        // When
        viewModel.sendMessage()
        advanceUntilIdle()

        // Then
        assert(viewModel.state.value.inputText.isEmpty())
        assert(!viewModel.state.value.sending)
    }

    @Test
    fun `sendMessage restores input on failure`() = runTest {
        coEvery { chatRepository.sendMessage(any(), any()) } returns Result.failure(RuntimeException("network"))
        viewModel.onInputChange("Hello")

        viewModel.sendMessage()
        advanceUntilIdle()

        assert(viewModel.state.value.inputText == "Hello")
    }
}
```

### Repository Test Pattern

Repositories depend on DAOs, APIs, and CryptoManager — mock all:

```kotlin
class ChatRepositoryTest {

    private val chatApi       = mockk<ChatApi>()
    private val messageDao    = mockk<MessageDao>()
    private val cryptoManager = mockk<CryptoManager>()
    private lateinit var repository: ChatRepository

    @Test
    fun `sendMessage encrypts and persists message`() = runTest {
        coEvery { cryptoManager.encrypt(any(), any()) } returns "base64cipher"
        coEvery { chatApi.sendMessage(any(), any()) } returns SendMessageResponse("id1", 1000L)
        coEvery { messageDao.upsert(any()) } just runs

        val result = repository.sendMessage("conv1", "hello")

        result.isSuccess shouldBe true
        coVerify { chatApi.sendMessage("conv1", match { it.ciphertext == "base64cipher" }) }
    }
}
```

### Compose UI Test Pattern

```kotlin
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `messages list shows text`() {
        val fakeState = ChatState(messages = listOf(
            MessageUi(id = "1", text = "Hello", isOutgoing = true, sentAt = 0L, delivered = false)
        ))
        composeRule.setContent {
            ChatScreen(convId = "c1", viewModel = fakeViewModel(fakeState))
        }

        composeRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
```

### MainCoroutineRule

Required for all ViewModel tests:

```kotlin
// test/java/run/koto/util/MainCoroutineRule.kt
class MainCoroutineRule(
    val dispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
        dispatcher.cleanupTestCoroutines()
    }
}
```

---

## Rust Crypto Crate

### Framework

**Runner:** `cargo test` (built-in)
**Location:** inline `#[cfg(test)]` module in `src/lib.rs` and `src/error.rs`

**Run commands:**
```bash
cd crypto
cargo test                          # Run all tests
cargo test -- --nocapture           # Show println! output
cargo test encrypt                  # Run tests matching "encrypt"
```

### Test Pattern

```rust
#[cfg(test)]
mod tests {
    use super::*;

    fn make_crypto() -> Arc<KotoCrypto> {
        let bundle = generate_registration_bundle(12345).unwrap();
        KotoCrypto::new(
            bundle.identity_key_pair,
            12345,
            "test_account_id".to_string(),
        ).unwrap()
    }

    #[test]
    fn test_generate_registration_bundle_produces_100_prekeys() {
        let bundle = generate_registration_bundle(1).unwrap();
        assert_eq!(bundle.prekeys.len(), 100);
        assert_eq!(bundle.prekeys[0].id, 1);
        assert_eq!(bundle.prekeys[99].id, 100);
    }

    #[test]
    fn test_encrypt_decrypt_roundtrip() {
        let alice = make_crypto_with_id("alice");
        let bob   = make_crypto_with_id("bob");

        // Alice establishes session with Bob's bundle
        let bob_bundle = bob_prekey_bundle(&bob);
        alice.clone().process_prekey_bundle("bob".to_string(), bob_bundle).unwrap();

        let plaintext = b"hello world".to_vec();
        let ciphertext = alice.clone().encrypt("bob".to_string(), plaintext.clone()).unwrap();
        let decrypted  = bob.clone().decrypt("alice".to_string(), ciphertext).unwrap();

        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_decrypt_empty_ciphertext_returns_error() {
        let crypto = make_crypto();
        let result = crypto.decrypt("peer".to_string(), vec![]);
        assert!(matches!(result, Err(CryptoError::DecryptionFailed { .. })));
    }
}
```

---

## Coverage Targets (Recommended)

| Layer | Priority | Target |
|-------|----------|--------|
| Go `internal/app/` | High — core business logic | 80%+ |
| Go `internal/transport/http/` | High — API contract | 70%+ |
| Go `pkg/` | Medium — shared utilities | 90%+ |
| Android ViewModels | High — state logic | 70%+ |
| Android Repositories | Medium — integration | 50%+ |
| Rust crypto | High — security-critical | 80%+ |

---

## Test Coverage Gaps (Current)

**All layers are untested.** The highest-risk gaps given the domain:

- **`services/auth/internal/app/`** — token issuance, signature verification, pre-key rotation: security-critical, zero coverage
- **`crypto/src/lib.rs`** — encrypt/decrypt roundtrip, session establishment, error paths: no `#[test]` blocks exist
- **`services/chat/internal/app/`** — member authorization checks (conversation membership), message deletion guards
- **Android `CryptoManager`** — Base64 fallback path, init/uninitialised behaviour
- **`pkg/token/jwt.go`** — token signing and verification

---

*Testing analysis: 2026-04-05*
