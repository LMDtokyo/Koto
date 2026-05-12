# Phase 05.1: Security and Bug Fix Sweep — Context

**Inserted:** 2026-04-24 (URGENT)
**Position:** After Phase 5 (Micro-Interactions, in progress) / Before Phase 6 (Onboarding, not started)
**Rationale:** Audit in `.planning/codebase/CONCERNS.md` + direct code reads surfaced 17 production-blocking issues. E2EE is silently defeated by Base64 fallback, prekey bundles leak without JWT, rate limiting is a no-op, `make migrate` targets the wrong database, WebSocket ignores typing/read-receipt events, Signal ratchet state is lost on process kill. These must land before Phase 6 ships, otherwise new users enter a broken protocol path.

---

## Scope: 17 items across 3 categories

### Security (7)

**1. Base64 plaintext fallback defeats E2EE**
- Files: `android/app/src/main/java/run/koto/data/repository/ChatRepository.kt:125-133,262-264`
- Current: if no Signal session exists for `peerId`, message is sent as `Base64(plaintext)` — server stores plaintext.
- Fix: block send (`Result.failure`) with a typed error; surface "Encryption unavailable" state in UI; retry after `processPreKeyBundle` succeeds.
- Reference: Signal Protocol spec — "no plaintext ever leaves the client".

**2. `GET /v1/auth/prekeys/bundle/{id}` exposed without JWT**
- Files: `services/auth/internal/transport/http/handler.go:38-47` (route mount), gateway routing table.
- Current: unauthenticated clients can enumerate any account's public identity + signed pre-keys + one-time pre-keys.
- Fix: add `JWTAuth` middleware to the bundle route. Verify Android client only calls `/v1/keys/{id}` (user service, already protected) — the auth-service bundle route may be dead and deletable.

**3. Bot webhook SSRF**
- Files: `services/bot/cmd/server/main.go:56-97` (register), `153-182` (dispatch).
- Current: any string accepted as webhook URL — attacker registers `http://169.254.169.254/...`, internal Docker targets, file://, gopher://.
- Fix: `url.Parse` → require `https` (or http in dev), resolve hostname, block IPs in RFC1918 (10/8, 172.16/12, 192.168/16), link-local (169.254/16), loopback (127/8, ::1), unique-local IPv6 (fc00::/7), multicast. Re-resolve at dispatch time (DNS rebinding defense).

**4. WebSocket access token in URL query string**
- Files: `services/gateway/cmd/server/main.go:219-222`, `android/app/src/main/java/run/koto/data/remote/ws/KotoWebSocket.kt:41-45`.
- Current: `wss://host:9080/ws?token=<jwt>` — query params leak to access logs, proxies, `Referer` headers.
- Fix: use `Sec-WebSocket-Protocol` header per RFC 6455 (readable pre-upgrade), echo back server side. Alternative: short-lived ticket endpoint (`POST /v1/ws/ticket` issues 30s single-use token).

**5. Timing attack on `X-Internal-Secret`**
- File: `services/bot/cmd/server/main.go:142`.
- Current: `if got != expected { ... }` — byte-by-byte comparison leaks secret length / prefix.
- Fix: `subtle.ConstantTimeCompare([]byte(got), []byte(expected)) == 1`.

**6. Postgres `sslmode=disable` across all services**
- Files: `docker-compose.yml` (5 service blocks with Postgres DSN).
- Current: hardcoded `sslmode=disable` — breaks on managed Postgres.
- Fix: extract `POSTGRES_SSLMODE` env var (default `disable` for local Docker, `require` for prod). Document in README.

**7. One-time prekeys not restored after process kill**
- Files: `android/app/src/main/java/run/koto/crypto/CryptoManager.kt:40-78` (init path), `crypto/src/lib.rs:122` (`InMemSignalProtocolStore`).
- Current: `CryptoManager.init()` restores identity + signed + Kyber prekeys from `AccountPrefs`. One-time prekeys consumed during X3DH are *not* persisted — on app kill mid-session, future decryption fails.
- Fix: persist one-time prekeys in Room (new `OneTimePreKeyEntity` table) as they are generated; on `init()` load via `cryptoManager.loadPrekeys(allUnused)`; on server-confirmed consumption mark row consumed (don't delete immediately — need key to decrypt already-in-flight messages).

### Bugs (8)

**8. `make migrate` targets wrong database**
- File: `Makefile:46-50`.
- Current: `-d nova` on 5 psql lines (historical project name); actual database is `koto` per `docker-compose.yml` `POSTGRES_DB: koto`.
- Additionally missing: `services/user/migrations/002_prekeys.sql`, `services/media/migrations/002_is_public.sql`.
- Fix: change `-d nova` → `-d koto`; append missing migrations.

**9. Rate limit middleware is a no-op**
- File: `services/gateway/internal/middleware/auth.go:45-47`.
- Current: `func RateLimit(next http.Handler) http.Handler { return next }` — pass-through.
- Fix: use `github.com/go-chi/httprate` (chi-native). Per-IP for public routes (`/v1/auth/register`, `/v1/auth/token/*`): 5 req/min. Per-account for authenticated (keyed on `X-Account-ID` injected by `JWTAuth`): 60 req/min for writes, 300 req/min for reads.
- Reference: https://github.com/go-chi/httprate

**10. WebSocket read loop discards client frames**
- File: `services/gateway/internal/ws/hub.go:109-116`.
- Current: read loop reads and drops all inbound frames; typing + read receipts silently fail.
- Fix: parse JSON envelope `{type: "typing"|"read", payload: {...}}`. For `typing`: publish to new NATS subject `chat.typing.{convId}`, gateway subscribers fan out to other conversation members (short TTL, no persistence). For `read`: call chat service internal endpoint `POST /internal/read` (add new route) which updates per-member read cursor in ScyllaDB.

**11. TOCTOU in `PublishPreKeys`**
- File: `services/auth/internal/app/service.go:151-168`.
- Current: `count := preKeys.Count(...)`, then `ID: count + i + 1` for each new key. Two concurrent uploads collide on IDs → `ON CONFLICT DO NOTHING` silently drops keys.
- Fix: use Postgres `SEQUENCE` (`CREATE SEQUENCE one_time_pre_key_id_seq`) or `INSERT ... RETURNING id`. Add migration `services/auth/migrations/003_prekey_sequence.sql`.

**12. Media fileID collision**
- File: `services/media/cmd/server/main.go:92`.
- Current: `fmt.Sprintf("%d-%s", time.Now().UnixNano(), accountID)` — predictable, collides within same nanosecond.
- Fix: `github.com/google/uuid` v4 for both `fileID` and `objectKey`. Add to `services/media/go.mod`.

**13. `fmt.Printf` error swallow in UpdateLastMessage**
- File: `services/chat/internal/app/service.go:82`.
- Current: `fmt.Printf` instead of zerolog — bypasses JSON log aggregation; failures invisible.
- Fix: replace with `logger.FromCtx(ctx).Error().Err(err).Str("conv_id", conv.ID).Msg("update last message failed")`. Consider whether failure should bubble up (decision: log-only is fine — message save already succeeded, just preview metadata drift).

**14. N+1 ScyllaDB queries on GetConversations**
- Files: `services/chat/internal/app/service.go:197-213`, `services/chat/internal/infra/scylla/message_repo.go`.
- Current: fetch all conv IDs for member, then loop `conversations.Get(id)` — 51 queries for 50 conversations.
- Fix: add `GetBatch(ctx, ids []string) ([]Conversation, error)` to `domain.ConversationRepository`. Implement with single CQL `SELECT * FROM conversations WHERE id IN ?` (or `gocql.Batch` for consistent reads).
- Reference: https://docs.scylladb.com/manual/stable/cql/dml.html#in-restrictions

**15. Bot dispatch endpoint never called**
- File: `services/bot/cmd/server/main.go:138-183`.
- Current: `POST /internal/dispatch` defined, guarded by `X-Internal-Secret`, but no caller exists.
- Decision needed: either (a) wire chat service to call dispatch on every message where recipient is a bot (add bot-account flag check in chat.SendMessage + HTTP call), or (b) wire bot service as NATS subscriber on `chat.deliver.>` that filters recipients against bot account table, or (c) delete endpoint as dead code. Check git log for original intent.
- Recommended: (b) — matches existing notification service pattern, no chat-service changes needed.

### UI stubs (2 groups)

**16. Chat context menu + attach button stubs**
- File: `android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt` (approx lines 235-236 copy/delete, ~1045 attach).
- Current: `{ /* TODO */ }` handlers.
- Fix:
  - Copy: `LocalClipboardManager.current.setText(AnnotatedString(message.text))` + toast/snackbar.
  - Delete: add `ChatRepository.deleteMessage(convId, msgId)` → `DELETE /v1/conversations/{convId}/messages/{msgId}` (add ChatApi method; add chat handler — backend already has `messages.Delete`?  verify). Local: `messageDao.deleteById(msgId)`.
  - Attach: open media picker, use existing `MediaRepository` presigned upload flow, send as `type=2` ciphertext message referencing file ID.

**17. Swipe action stubs on conversation list**
- File: `android/app/src/main/java/run/koto/ui/screens/conversations/ConversationsViewModel.kt:80-100`.
- Current: `onArchive`/`onPin`/`onMute` log only; comment says "chatRepository.xxxConversation — not yet in repo".
- Fix: since server doesn't expose archive/pin/mute (chat service has no such routes), keep state local: add `isArchived`, `isPinned`, `isMuted` columns to `ConversationEntity` (Room v3→v4 migration); expose `ChatRepository.setArchived/setPinned/setMuted(convId, bool)`; update `ConversationDao.observeAll` queries to sort pinned first and exclude archived from main list. Plumb through ViewModel.

---

## Non-goals

- Do not alter crypto protocol (libsignal stays as-is — only persistence fix for #7).
- Do not change docker-compose architecture (only sslmode parameterization for #6).
- Do not add new features.
- Phase 5 plans 05-01, 05-03, 05-05 remain in Phase 5 — this inserted phase is security + bugs + stubs only.

## Dependencies

None between items — all 17 are independent. Parallelization potential: backend Go fixes, Android fixes, Makefile fix, Rust crypto fix can proceed in parallel waves.

## Hints for plan-phase

- **UI hint:** No (backend-heavy; UI is light wiring only)
- **Research hint:** Yes — confirm current best practices for:
  - `go-chi/httprate` (per-IP + per-account keying)
  - `Sec-WebSocket-Protocol` token handshake (both `coder/websocket` server parsing and OkHttp client setting)
  - `github.com/google/uuid` for file IDs
  - Signal Protocol prekey persistence patterns (how WhatsApp/Signal Android client persist `InMemSignalProtocolStore` across process kills)
  - PostgreSQL `INSERT ... RETURNING` vs `SEQUENCE` for prekey IDs
  - ScyllaDB `IN` batch reads performance
  - Kotlin `subtle.ConstantTimeCompare` equivalent (Bouncy Castle constant-time helpers)

## Success criteria (what must be TRUE after phase)

1. Android client refuses to send a message as plaintext when no session exists — user sees an explicit "Encryption unavailable" error state rather than a silent plaintext leak.
2. `GET /v1/auth/prekeys/bundle/{id}` returns 401 without a valid JWT (or is deleted entirely in favor of `/v1/keys/{id}`).
3. Registering a bot with webhook URL `http://169.254.169.254/` or `http://localhost/` returns a 400 validation error.
4. WebSocket connects successfully without a `?token=` query parameter — access token is passed via `Sec-WebSocket-Protocol` header.
5. `make migrate` runs cleanly against the `koto` database and applies all 8 migrations (including `user/002_prekeys.sql` and `media/002_is_public.sql`).
6. Attempting 20 rapid `POST /v1/auth/register` requests from the same IP returns 429 after the configured threshold.
7. Typing in the chat screen broadcasts to the other conversation member's client (typing indicator appears on their device within 500ms).
8. Killing and restarting the Android app mid-session does not break subsequent message decryption (one-time prekey persistence verified).
9. Two concurrent `PUT /v1/auth/prekeys/publish` requests result in no duplicate prekey IDs in the database.
10. Tapping "Copy" on a message copies the plaintext to the system clipboard; "Delete" removes the message locally and on the server; "Attach" opens a file picker.
11. Archiving a conversation removes it from the main list; pinning keeps it at the top; muting suppresses notifications — all persist across app restarts.
12. `go test ./...` across all services shows no regressions (test suites may still be empty, but build must pass).
13. Android `./gradlew assembleDebug` succeeds with no new lint errors in modified files.

---

*Scope frozen at insert time — plan-phase will break this into atomic plans.*
