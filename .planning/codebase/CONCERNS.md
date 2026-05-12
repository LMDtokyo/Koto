# Codebase Concerns

**Analysis Date:** 2026-04-05

---

## Tech Debt

### Rate Limiting Is a No-Op
- Issue: `RateLimit` middleware in the gateway is a placeholder that passes every request through unconditionally.
- Files: `services/gateway/internal/middleware/auth.go` (line 45–47), `services/gateway/cmd/server/main.go` (line 128)
- Impact: No per-account, per-IP, or global request throttling exists at the gateway. Any unauthenticated endpoint (registration, token refresh) is open to abuse at full network speed.
- Fix approach: Replace `RateLimit` with a token-bucket (e.g., `golang.org/x/time/rate` keyed per IP for unauthenticated routes, per account for authenticated routes) or use a middleware like `go-chi/httprate`.

### WebSocket Client-Sent Events Are Ignored
- Issue: The WebSocket read loop in the hub reads and discards all client-sent frames. Typing indicators and read receipts are mentioned but not handled.
- Files: `services/gateway/internal/ws/hub.go` (line 115)
- Impact: Typing indicators and read receipts cannot be delivered from client to server. Any Android feature relying on these will silently fail.
- Fix approach: Add a message dispatcher in the read loop that routes typed events to appropriate handlers (e.g., broadcast typing to conversation members, persist read receipts to DB).

### Attachment Send and Voice Are Stub UI Buttons
- Issue: The "attach" and "voice" buttons in the chat screen have empty `onClick` handlers.
- Files: `android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt` (lines 319, 387)
- Impact: Buttons render and are tappable but do nothing; users get no feedback.
- Fix approach: Either wire up the media upload flow (upload-url → S3 presign) or disable the buttons until implemented.

### Migrations Are Not Automated
- Issue: No migration runner (golang-migrate, goose, etc.) is integrated. All migrations are `.sql`/`.cql` files run manually. The `make migrate` target must be invoked explicitly after every deploy.
- Files: `Makefile` (lines 43–52), `services/*/migrations/`
- Impact: Easy to miss migrations on fresh deploys; no version tracking; team must remember to run manually.
- Fix approach: Embed and auto-run migrations at service startup using `golang-migrate/migrate` with `pgx` driver.

### `make migrate` Has Two Critical Bugs
- Issue 1: All five `psql` invocations in the `migrate` target target database `nova`, not `koto` — the actual database name defined in `docker-compose.yml`.
- Issue 2: Three migrations added after initial setup are absent from the target: `services/user/migrations/002_prekeys.sql`, `services/media/migrations/002_is_public.sql`, and the ScyllaDB keyspace is referenced with replication factor 3 which will fail on single-node dev clusters.
- Files: `Makefile` (lines 46–50)
- Impact: `make migrate` currently fails silently or runs against the wrong database on every fresh dev/prod setup. The `prekey_bundles` and `is_public` columns do not exist after running `make migrate`.
- Fix approach: Change `-d nova` → `-d koto` on all psql lines; add missing migrations to the target.

### One-Time Pre-Key Upload Uses Incremental Non-Atomic ID Assignment
- Issue: `PublishPreKeys` in auth service computes the next ID by calling `Count()` then assigning `count + i + 1`. This is a TOCTOU race: two concurrent uploads will produce duplicate IDs.
- Files: `services/auth/internal/app/service.go` (lines 153–168)
- Impact: Under concurrent key uploads (e.g., multi-device), duplicate IDs cause `ON CONFLICT DO NOTHING` to silently drop keys, reducing the pre-key pool.
- Fix approach: Use a `SEQUENCE` or `SERIAL` column in Postgres and let the DB assign IDs, or use a CTE `INSERT … SELECT … FOR UPDATE`.

### Media Service File ID Uses UnixNano + Account ID (Predictable, Non-Unique)
- Issue: Object keys are generated as `fmt.Sprintf("%d-%s", time.Now().UnixNano(), accountID)`. Two simultaneous uploads from the same account within the same nanosecond collide. Also, the file ID is structurally guessable.
- Files: `services/media/cmd/server/main.go` (line 92)
- Impact: ID collision causes a metadata INSERT failure; object key collision overwrites a previous upload in MinIO.
- Fix approach: Use `crypto/rand` UUID (e.g., `github.com/google/uuid`) for both file ID and object key.

### `GetConversations` N+1 ScyllaDB Queries
- Issue: `GetConversations` first fetches all conversation IDs for a member, then fires one `conversations.Get(ctx, id)` query per ID in a sequential loop.
- Files: `services/chat/internal/app/service.go` (lines 197–213)
- Impact: A user in 50 conversations triggers 51 ScyllaDB queries on every inbox load. This degrades linearly with conversation count.
- Fix approach: Add a `GetBatch(ctx, ids []string) ([]Conversation, error)` method to `domain.ConversationRepository` and implement it with a single `WHERE id IN ?` CQL query using `gocql.Batch` or a multi-value IN clause.

### `SaveOneTimePrekeys` Issues Individual Inserts in a Loop
- Issue: `SaveOneTimePrekeys` loops over all keys and executes one `pool.Exec` per key rather than using `pgx.Batch`.
- Files: `services/user/internal/infra/postgres/repos.go` (lines 184–196)
- Impact: Uploading 100 one-time prekeys at registration requires 100 round-trips to Postgres, adding 5–50 ms per key on realistic latency.
- Fix approach: Use `pgx.Batch` (the same pattern already used in `services/auth/internal/infra/postgres/account_repo.go`).

### `UpdateLastMessage` Uses a Separate Scylla Write with Silent Failure
- Issue: After saving a message, `UpdateLastMessage` is called and its failure is logged only via `fmt.Printf` (not the structured zerolog logger), and execution continues.
- Files: `services/chat/internal/app/service.go` (line 82)
- Impact: Conversation `last_message` metadata can silently drift. fmt.Printf output bypasses log aggregation (e.g., if logs are collected as JSON, this line will not be captured).
- Fix approach: Replace `fmt.Printf` with the service's injected logger. Consider whether this failure should actually bubble up.

---

## Known Bugs

### `make migrate` Points to Wrong Database
- Symptoms: Running `make migrate` on a fresh deployment silently creates tables in non-existent database `nova` rather than `koto`.
- Files: `Makefile` (lines 46–50)
- Trigger: `make migrate` on any clean deployment.
- Workaround: Run `psql` commands manually with `-d koto`.

### `FetchPreKeyBundle` in Auth Service Is Unauthenticated
- Symptoms: `GET /v1/auth/prekeys/bundle/{accountID}` does not require a valid JWT — it is mounted under the same `/v1/auth` router as unauthenticated registration routes, with no `JWTAuth` middleware applied.
- Files: `services/auth/internal/transport/http/handler.go` (lines 38–47)
- Trigger: Any unauthenticated HTTP client can enumerate all public keys for any account ID.
- Workaround: None. The route is fully public.
- Note: The equivalent route at `/v1/keys/{targetID}` in the user service IS behind `accountIDMiddleware`. The auth service route is a separate, unguarded path.

### Bot Row Scan Silences All Errors
- Symptoms: When listing bots, `rows.Scan(...)` errors are discarded with `_ =`, so any scan failure returns a partial or empty list without any error to the caller.
- Files: `services/bot/cmd/server/main.go` (line 121)
- Trigger: Any DB schema mismatch or NULL value in bots table.
- Workaround: None.

---

## Security Considerations

### Base64 Fallback Sends Plaintext Messages
- Risk: `ChatRepository.sendMessage` falls back to plain Base64-encoding the message if `CryptoManager` has no session for the peer (e.g., first message before session establishment completes, or if `processPreKeyBundle` silently failed). Base64 is not encryption.
- Files: `android/app/src/main/java/run/koto/data/repository/ChatRepository.kt` (lines 123–133, 236–238)
- Current mitigation: The code comments acknowledge this as a fallback; Signal decryption is attempted first.
- Recommendations: Block message send if no session exists rather than falling back to Base64. Return an error to the UI so the user knows the message was not encrypted. Alternatively, log a visible warning.

### Bot Webhook URLs Are Not Validated
- Risk: A bot owner can register any URL as a webhook, including `http://169.254.169.254/latest/meta-data/` (AWS metadata), internal Docker network addresses (`http://postgres:5432`), or other SSRF targets. The bot service will POST to it when events fire.
- Files: `services/bot/cmd/server/main.go` (lines 56–97, 153–182)
- Current mitigation: None.
- Recommendations: Validate webhook URLs against an allowlist of schemes (`https` only), block private IP ranges (RFC1918, link-local), and consider a separate outbound egress proxy with IP filtering.

### WebSocket Access Token Exposed in URL Query Parameter
- Risk: The WebSocket connects via `wss://host:9080/ws?token=<jwt>`. Query parameters appear in server access logs, CDN logs, browser history, and `Referer` headers.
- Files: `services/gateway/cmd/server/main.go` (lines 219–222), `android/app/src/main/java/run/koto/data/remote/ws/KotoWebSocket.kt` (lines 41–45)
- Current mitigation: Comment acknowledges that headers are inaccessible post-upgrade in some stacks.
- Recommendations: Use the WebSocket handshake `Sec-WebSocket-Protocol` header to pass the token (the server can read it before the upgrade), or use a short-lived WebSocket ticket obtained via an authenticated REST call.

### Internal Service Secret Uses String Equality Check (Timing Attack)
- Risk: Bot service compares `X-Internal-Secret` header using `!=` (string equality), which is not constant-time.
- Files: `services/bot/cmd/server/main.go` (line 142)
- Current mitigation: The internal endpoint is not exposed externally (Docker network only), which reduces practical risk.
- Recommendations: Replace with `subtle.ConstantTimeCompare([]byte(got), []byte(expected)) == 1`.

### All Postgres Connections Use `sslmode=disable`
- Risk: All service-to-Postgres communication is unencrypted within the Docker network. If TLS is ever required (e.g., managed Postgres on a cloud provider), this must be changed.
- Files: `docker-compose.yml` (lines 114, 151, 167, 186, 205)
- Current mitigation: Traffic is contained to the Docker bridge network in the current single-host deployment.
- Recommendations: Switch to `sslmode=require` if Postgres is moved to a managed service or separate host.

### Signal Protocol State Is Purely In-Memory (Rust Crypto Crate)
- Risk: `KotoCrypto` uses `InMemSignalProtocolStore` — all ratchet state (session keys, message keys) is lost on app process kill. On restart, `load_prekeys` / `load_signed_prekeys` / `load_kyber_prekeys` must be called again from persisted local DB. If the Android app is killed between sending and persisting prekey state, the session is corrupted and future messages cannot be decrypted.
- Files: `crypto/src/lib.rs` (line 122), `android/app/src/main/java/run/koto/crypto/CryptoManager.kt` (lines 40–78)
- Current mitigation: `CryptoManager.init()` restores signed and Kyber prekeys from `AccountPrefs` on each startup. One-time prekeys are NOT restored (they are consumed and not repersisted).
- Recommendations: Persist and reload one-time prekeys from the local Room database. Consider implementing a durable `SignalProtocolStore` backed by the Room database to survive process death during active ratchet sessions.

### No Request Body Size Limits on JSON Endpoints
- Risk: All services accept arbitrary-size JSON bodies. A client can POST a multi-MB JSON payload to any endpoint (e.g., `POST /v1/auth/register` with 10,000 one-time pre-keys), causing OOM or slow parsing.
- Files: All service `cmd/server/main.go` files; notably no `http.MaxBytesReader` usage anywhere.
- Current mitigation: None.
- Recommendations: Wrap `r.Body` with `http.MaxBytesReader(w, r.Body, maxBytes)` in a shared middleware. 64 KB is reasonable for JSON endpoints.

---

## Performance Bottlenecks

### GetConversations: O(N) ScyllaDB Queries Per Inbox Load
- Problem: Each conversation requires a separate DB round-trip (see Tech Debt above).
- Files: `services/chat/internal/app/service.go` (lines 197–213), `services/chat/internal/infra/scylla/message_repo.go`
- Cause: No batch read method on `ConversationRepository`.
- Improvement path: Implement `GetBatch` with CQL `IN` clause; cap per-request conversation count.

### Presigned URL Context Leaked in Media Service
- Problem: `mc.PresignedPutObject(ctx, ...)` and `mc.PresignedGetObject(ctx, ...)` use the incoming HTTP request context. When the HTTP handler's `WriteTimeout` (60 s) fires, the MinIO call is cancelled mid-flight.
- Files: `services/media/cmd/server/main.go` (lines 95, 139)
- Cause: MinIO client inherits request context with built-in deadline.
- Improvement path: Use `context.WithTimeout(context.Background(), 5*time.Second)` for the MinIO presign calls.

### WebSocket Hub `Send` Drops Messages for Slow Clients
- Problem: The hub's send channel is buffered at 256 entries. If a client is slower than the server writes (e.g., mobile on a congested network), messages beyond 256 are dropped with no delivery guarantee.
- Files: `services/gateway/internal/ws/hub.go` (lines 75–83, line 124)
- Cause: `select { case c.send <- env: ... default: /* skip */ }` silently drops on full channel.
- Improvement path: This is intentional (comment says "client will catch up on reconnect via history"). However, history sync on reconnect is not implemented — `syncMessages` in the Android client fetches recent history but there is no server-side cursor persisted per client. Document this gap or implement missed-message delivery via persistent cursors.

---

## Fragile Areas

### Dual Pre-Key Stores: Auth Service vs User Service
- Files: `services/auth/internal/app/service.go`, `services/auth/internal/infra/postgres/account_repo.go`, `services/user/internal/app/service.go`, `services/user/internal/infra/postgres/repos.go`
- Why fragile: Both auth and user services maintain separate pre-key tables (`one_time_pre_keys` in auth, `one_time_prekeys` in user service). The table names differ by underscore conventions; the column names differ (`key_data` vs `public_key`). The Android client calls `/v1/keys` (user service) for key bundles at conversation creation, but `/v1/auth/prekeys` at registration. It is unclear which store is canonical. If both are populated, a session initiator may receive mismatched keys.
- Safe modification: Consolidate all pre-key management into the user service. Deprecate and remove the auth service's pre-key endpoints after verifying Android client only calls `/v1/keys`.
- Test coverage: Zero tests exist for either path.

### `deliveryEvent` Struct Duplicated in Gateway
- Files: `services/gateway/cmd/server/main.go` (lines 28–37)
- Why fragile: The struct must stay byte-for-byte aligned with `chat/internal/domain.DeliveryEvent`. A field rename or type change in chat silently breaks NATS event parsing in the gateway (no compile-time check across services).
- Safe modification: Extract a shared `events` package in the `pkg/` workspace module; both services import it. Or use protobuf/JSON schema validation.
- Test coverage: None.

### `BotEvent.Text` Contains Decrypted Message Content
- Files: `services/bot/internal/domain/bot.go` (line 29)
- Why fragile: The comment says "decrypted on client side before forwarding (optional)". If the Android client ever forwards decrypted text in `BotEvent`, it leaves the E2E encrypted boundary. The server stores and forwards plaintext. No enforcement prevents this.
- Safe modification: Remove the `Text` field from `BotEvent` or explicitly assert it is always empty server-side before forwarding.

### ScyllaDB Replication Factor of 3 Fails on Single-Node Dev
- Files: `services/chat/migrations/001_messages.cql` (line 9: `'datacenter1': 3`)
- Why fragile: `NetworkTopologyStrategy` with RF=3 requires at least 3 nodes. A single-node dev cluster (the default Docker Compose setup) will create the keyspace but `LocalQuorum` writes and reads will fail immediately.
- Safe modification: Use `SimpleStrategy` with `replication_factor: 1` for dev; parameterise the migration or document the override needed.

---

## Scaling Limits

### Single NATS Subject Per Conversation (`chat.deliver.{convId}`)
- Current capacity: One NATS consumer per conversation, broadcast fan-out handled in gateway memory.
- Limit: When multiple gateway instances run (horizontal scaling), each gateway only delivers to clients connected to it. Recipients connected to a different gateway instance never receive messages.
- Scaling path: Switch to per-recipient subjects (`chat.deliver.{recipientId}`) and implement a queue group so any gateway instance can pick up the message. Or use a NATS JetStream push consumer per gateway with durable subject filters.

### In-Process WebSocket Hub Does Not Scale Horizontally
- Current capacity: The hub is a single in-process `sync.RWMutex`-protected map.
- Limit: A second gateway instance has an empty hub; messages published via NATS to gateway-1 cannot reach clients connected to gateway-2.
- Scaling path: Replace the in-process hub with presence tracking in Dragonfly (already in the infra). Each gateway publishes `chat.deliver.{recipientId}` and subscribes to the same subject using NATS queue groups; only the gateway holding the client's WebSocket connection delivers the message.

---

## Dependencies at Risk

### `minio/minio-go` Pinned to `latest`
- Risk: `docker-compose.yml` uses `minio/minio:latest` for the MinIO image. Breaking API changes in MinIO (they have a history of removing legacy S3 API endpoints) can silently break the media service.
- Impact: Unexpected breakage on `docker-compose pull`.
- Migration plan: Pin to a specific MinIO release tag (e.g., `RELEASE.2024-01-01T00-00-00Z`).

### `uniffi` Generated Bindings Must Be Regenerated After Rust Changes
- Risk: The Kotlin bindings in `android/` are generated by `uniffi` from the Rust crate. If `crypto/src/lib.rs` changes any exported type or function signature, the bindings must be manually regenerated and committed. There is no CI step enforcing this.
- Impact: Kotlin compile errors or runtime `UnsatisfiedLinkError` if Rust and Android are out of sync.
- Migration plan: Add a CI check that runs `cargo build` + `uniffi-bindgen generate` and diffs the output against committed bindings.

---

## Missing Critical Features

### No Message Delivery Guarantee for Offline Recipients
- Problem: When a recipient has no active WebSocket connection, the gateway's `hub.Send` drops the message. The notification service sends a silent push to wake the app, but if the push is delayed or dropped, the message is silently lost until the client polls history on next open.
- Blocks: Reliable messaging for users who are offline or on poor networks.

### Unread Count Is Always Zero
- Problem: `GetConversations` always returns `UnreadCount: 0` and `Delivered: false` for all messages. There is no mechanism to track which messages a user has seen.
- Files: `services/chat/internal/transport/http/handler.go` (line 112)
- Blocks: Badge counts, notification suppression for already-read messages.

### No Signed Pre-Key Rotation Enforcement
- Problem: Signed pre-keys (X25519) and Kyber pre-keys are generated once at registration with ID 1. The Signal specification requires rotation every ~2 weeks. There is no server-side enforcement of rotation age, no client-side rotation timer, and no endpoint to upload a replacement signed pre-key independently of a full re-registration.
- Files: `crypto/src/lib.rs` (`generate_registration_bundle` hardcodes IDs 1 for both), `services/user/cmd/server/main.go` (`PUT /v1/keys` replaces the bundle but is documented as "called once at registration")
- Blocks: Long-term forward secrecy degrades as the signed pre-key ages.

### Bot Dispatch Is Never Called
- Problem: The `/internal/dispatch` endpoint in the bot service is defined but no other service calls it. Bots are registered and listed but never receive events from user messages.
- Files: `services/bot/cmd/server/main.go` (lines 138–183)
- Blocks: Bot platform is non-functional end-to-end.

---

## Test Coverage Gaps

### Zero Automated Tests Exist
- What's not tested: All 7 Go microservices, the Rust crypto crate, and the Android app have zero test files.
- Files: `services/*/`, `crypto/src/`, `android/app/src/`
- Risk: Any refactor, migration, or bug fix has no regression safety net. Crypto correctness (X3DH session establishment, Double Ratchet decryption across restarts) is entirely untested.
- Priority: High
- Critical path to add: Start with `services/auth/internal/app/service.go` (unit test with mock repos), `services/chat/internal/app/service.go` (SendMessage/GetHistory), and `crypto/src/lib.rs` (`#[cfg(test)]` round-trip encrypt/decrypt).

---

*Concerns audit: 2026-04-05*
