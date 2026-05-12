# Koto Desktop — Security Model

Последнее обновление: 2026-04-24. Документ описывает threat model, применённые защиты, и то что ещё предстоит.

## Threat Model

### В скопе

| Атакующий | Возможности | Защита |
|---|---|---|
| **Network passive** | PCAP, ISP, Wi-Fi MitM | TLS + cert pinning, WebSocket через тот же канал |
| **Network active** | Compromised CA, enterprise proxy, MitM proxy | SPKI-based cert pinning (отключаемо только для localhost) |
| **Device co-user** | Читает файлы в `$APPDATA`/`~/.local/share` | AES-GCM encrypt-at-rest, master key в OS keystore |
| **Supply chain (JAR tamper)** | Подменяет native `koto_crypto.dll` в дистрибутиве | SHA-256 pin верифицируется при старте |
| **Compromised peer** | Шлёт мусорные/oversized сообщения | Size caps, encrypted auth-protected frames (libsignal) |
| **Server operator** | Читает всё что проходит через gateway/chat-сервис | E2EE (libsignal Signal Protocol + PQXDH), server видит только ciphertext |

### Вне скопа

| Атакующий | Почему |
|---|---|
| **Full device owner** (root/admin) | Может дампить RAM, читать Keychain интерактивно, заменить бинарь — невозможно защититься в user-space |
| **Nation-state с 0-day в OS** | Требует TEE/HSM; невыполнимо на consumer desktop |
| **Compromised backend с доступом к plaintext** | Protocol гарантирует, что сервер не видит plaintext; если backend compromised, E2EE ещё работает, но metadata (кто с кем переписывается, когда) leak |

## Крипто-протокол

| Слой | Алгоритм | Библиотека | Где живёт |
|---|---|---|---|
| Session init | **X3DH + PQXDH (Kyber1024)** | [`libsignal`](https://github.com/signalapp/libsignal) — тот же крейт что у Signal | `crypto/` Rust crate |
| Message encryption | **Double Ratchet** (AES-256-CBC + HMAC-SHA256 per message) | libsignal | Rust crate |
| Identity signing | **Ed25519** | libsignal + Rust `ed25519-dalek` | Rust crate |
| Post-quantum | **CRYSTALS-Kyber1024** (NIST PQC standard) | libsignal | Rust crate |
| At-rest local | **AES-256-GCM** (96-bit random IV, 128-bit tag) | JVM stdlib `Cipher` | [`LocalAead.kt`](crypto/src/main/kotlin/run/koto/desktop/crypto/security/LocalAead.kt) |
| Master key storage | Native OS keystore | [`KeyringVault.kt`](crypto/src/main/kotlin/run/koto/desktop/crypto/security/KeyringVault.kt) → Windows Credential Manager / macOS Keychain / Linux libsecret | OS-specific |

## Реализованные защиты (P0 + P1)

### At-rest encryption

Локальная SQLite больше не содержит никакой чувствительной plaintext-информации:

| Колонка | Было | Стало |
|---|---|---|
| `session.access_token` | UTF-8 plaintext | AES-GCM(плitext, masterKey) → hex |
| `session.refresh_token` | UTF-8 plaintext | AES-GCM(plaintext, masterKey) → hex |
| `crypto_identity.identity_key_pair` | plaintext BLOB | AES-GCM(bytes, masterKey) BLOB |
| `crypto_signed_prekey.private_key` | plaintext BLOB | AES-GCM(bytes, masterKey) BLOB |
| `crypto_kyber_prekey.serialized` | plaintext BLOB | AES-GCM(bytes, masterKey) BLOB |
| `message.plaintext_cache` | plaintext | Unchanged (см. [Memory hygiene](#memory-hygiene)) |

**Master key** — случайные 32 байта, сгенерированы при первом запуске, хранятся в OS keystore под `run.koto.desktop / master-key-v1`. Никогда не попадают на диск в открытом виде.

Fallback: если OS keystore недоступен (headless Linux без libsecret, broken Windows Credential Manager), используется [`FileBackedVault`](crypto/src/main/kotlin/run/koto/desktop/crypto/security/FileBackedVault.kt) с POSIX 0600 / Windows ACL permissions. Слабее чем OS keystore, но всё ещё защищает от ненастроенного file-sharing.

### Native library integrity

[`NativeIntegrity.kt`](crypto/src/main/kotlin/run/koto/desktop/crypto/security/NativeIntegrity.kt) проверяет SHA-256 у `koto_crypto.dll/.dylib/.so` ДО любого JNA-load:

1. Gradle task `:crypto:pinNativeHash` хеширует native-файл при сборке, записывает в `koto_crypto.sha256` (classpath resource).
2. При старте `Main.kt` читает и DLL, и pinned hash из classpath.
3. Сверяет constant-time — при несовпадении `exitProcess(2)`.
4. Копирует проверенный файл в temp-директорию + устанавливает `jna.library.path` так, чтобы JNA грузила только верифицированную копию.

**Лимит защиты:** атакующий, имеющий write-доступ к JAR, может заменить ОБА файла (dll + .sha256) синхронно. Полноценная защита требует подписи JAR (см. P2).

### TLS certificate pinning

[`PinnedTrustManager.kt`](data/src/main/kotlin/run/koto/desktop/data/remote/PinnedTrustManager.kt) — X509TrustManager с:

- **Platform chain validation** как первая линия (стандартный CA trust).
- **SPKI pinning** как вторая — SHA-256 Subject Public Key Info сертификата в цепочке должен совпадать с одним из pinned.
- **Bypass hosts** для dev: `localhost`, `127.0.0.1` не проверяются, чтобы docker-compose работал без настройки TLS.

Pins задаются через `-Dkoto.tls.pins=SHA256HEX1,SHA256HEX2` или env `KOTO_TLS_PINS`. Пустой список = pinning отключен (dev-режим).

**Для production:**
```bash
# Получить SPKI SHA-256 из сертификата:
openssl s_client -servername koto.run -connect koto.run:443 </dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | xxd -p -c 256
```

### Secure delete

`PRAGMA secure_delete = ON` устанавливается в [`DatabaseFactory`](data/src/main/kotlin/run/koto/desktop/data/local/DatabaseFactory.kt) — SQLite зануляет странички перед освобождением в freelist, что существенно усложняет forensic recovery через undelete-инструменты.

### Sign-out wipe

Когда `SessionCoordinator` видит `session=null` после signOut, вызывает:

```kotlin
db.kotoDbQueries.wipeAllMessages()
db.kotoDbQueries.wipeAllConversations()
db.kotoDbQueries.vacuum()
```

Это очищает локальную историю + объединяет freelist-странички → zeroed-pages не остаются в файле.

### Size caps

[`SecurityLimits.kt`](domain/src/main/kotlin/run/koto/desktop/domain/SecurityLimits.kt) — hard limits:

| Поле | Лимит |
|---|---|
| text plaintext | 64 KiB |
| ciphertext blob | 128 KiB |
| media file | 100 MiB |
| display_name | 64 chars |
| bio | 300 chars |
| contact nickname | 64 chars |

Валидация в репозиториях → `DomainError.InvalidInput` без сетевого вызова.

### Log sanitization

- Ktor HTTP logging по умолчанию **OFF** (`koto.http.log=NONE`). Включается явно через `-Dkoto.http.log=INFO/HEADERS/ALL`.
- Даже при INCLUDE: Authorization, Sec-WebSocket-Protocol, Cookie, Set-Cookie санитизируются.
- Application-level логи: account_id / message_id / conversation_id маскируются через [`IdRedactor.mask()`](domain/src/main/kotlin/run/koto/desktop/domain/util/IdRedactor.kt) — `055c…454d`.

### Error sanitization

`HttpResponseValidator` больше не пробрасывает server-messages в UI. Вместо этого:
- `401/403` → `"not authorised"`
- `404` → `"not found"`
- `400` → `"invalid request"`
- `429` → `"rate limited"`
- прочие → `"network error (code)"`

Оригинальное сообщение с сервера логируется на `DEBUG` — доступно разработчику, не пользователю.

### WebSocket auth

- Token идёт через `Sec-WebSocket-Protocol` header (не query-string) — не утекает в access logs / proxy Referer.
- Auto-reconnect с exponential backoff + jitter (1s → 2s → 4s → ... → 30s cap).

### HTTP retry + rate-limit

Ktor `HttpRequestRetry`:
- 5xx: 3 retries
- network timeout: 2 retries
- 429: 3 retries с exponential backoff (base=2, cap=10s)

### DI isolation

Три HTTP-клиента:
- `bare` — без Auth plugin, для `/v1/auth/*` (break refresh cycle)
- `main` — Bearer plugin + Auth + WebSocket + pinning
- `storage` — без Auth, без default host, только для MinIO presigned URL PUTs (SigV4 в URL)

### Transport hardening (Tor + user proxy) — IMPLEMENTED

Desktop теперь поддерживает прокси-транспорт с приоритетом **Tor > user proxy > direct**.
Реализация через [kmp-tor 2.6.0](https://github.com/05nelsonm/kmp-tor) + kmp-tor-resource-exec-tor 408.22.0
— Matthew Nelson's production-ready KMP-библиотека, активно maintained
(Feb 2026 release).

#### Архитектура

| Компонент | Роль | Файл |
|---|---|---|
| `TorManager` | Обёртка над kmp-tor `TorRuntime`. Bootstraps, парсит SOCKS-порт из логов, exposes `TorState` + `socksPort` StateFlow. | [data/network/TorManager.kt](data/src/main/kotlin/run/koto/desktop/data/network/TorManager.kt) |
| `TransportPolicy` | Резолвит активный proxy (Tor > userProxy > direct). Snapshot-based на старте, runtime-switch = restart app. | [data/network/TransportPolicy.kt](data/src/main/kotlin/run/koto/desktop/data/network/TransportPolicy.kt) |
| `UserProxy` | Модель пользовательского прокси (SOCKS5 / HTTP). `ss://` и `vmess://` out-of-scope. | [domain/model/TransportConfig.kt](domain/src/main/kotlin/run/koto/desktop/domain/model/TransportConfig.kt) |
| `NetworkSettingsRepository` | Персистит `torEnabled` + `userProxy` в SQLite таблицу `network_settings`. | [data/repository/NetworkSettingsRepositoryImpl.kt](data/src/main/kotlin/run/koto/desktop/data/repository/NetworkSettingsRepositoryImpl.kt) |

#### Startup flow

[Main.kt](app/src/main/kotlin/run/koto/desktop/Main.kt) делает `bootstrapTransport()` **ДО** `startKoin`:

1. Read `NetworkPreferences` snapshot.
2. Если `torEnabled == true`:
   - Create TorManager в `$APPDATA/Koto/tor` (work) и `/cache` (кеш исполняемого).
   - `torMgr.start()` — запускает daemon, waits до `Bootstrapped 100%` + парсит SOCKS-порт из лога `Opened Socks listener on 127.0.0.1:XXXXX`.
   - Если bootstrap fail — `exitProcess(3)` (fail-closed: user explicitly asked for Tor, **не** silently fallback).
3. `TransportPolicy.resolve(torState, socksPort, prefs)` → `ProxyConfig?`.
4. `bootTimeProxy` set globally; Koin факторы строят HttpClient с `engine.proxy = bootTimeProxy`.

#### Включение Tor

Три способа:
1. **Env / system property override** (для разработки/тестов): `KOTO_TOR=true ./gradlew :app:run` или `-Dkoto.tor=true`.
2. **Persistent setting** (для users): `networkSettingsRepository.setTorEnabled(true)` через будущий UI-экран.
3. **User proxy** (не Tor): `setUserProxy(UserProxy.Socks5("127.0.0.1", 1080))` — работает параллельно с выключенным Tor.

Runtime-toggle = **restart app** — Ktor CIO proxy config immutable после конструирования клиента, как и у Signal Desktop. Phase P3 может добавить `HttpClientHolder`-based swap, но v1 оставляем проще.

#### Fail-closed семантика

Если user enabled Tor, но bootstrap не прошёл — **приложение отказывается стартовать**, а не падает в direct. Это критически важно для anti-censorship user: "работает, но не через Tor" = утечка metadata ISP-у.

#### Tor binary distribution

`kmp-tor-resource-exec-tor:408.22.0` упаковывает tor-исполняемый в jar для всех платформ
(~8-12МБ per platform). На первом старте файл распаковывается в
`$APPDATA/Koto/tor/work/tor-resource/`, при последующих — переиспользуется.

Production-оптимизация: `kmp-tor-resource-filterjar-gradle-plugin` strip-ает неиспользуемые
платформы из дистрибутива (Windows installer содержит только Windows binary). Подключим при упаковке через `:app:packageMsi`.

#### Остаётся до зрелости

- [ ] **TlsPolicy и Tor совместимость** — при Tor-транспорте все коннекты идут через SOCKS5, но TLS cert pinning остаётся активным на выходном узле. Протестировать с прод-доменом.
- [ ] **WebSocket через Tor** — Ktor WebSocket plugin наследует engine proxy → должно работать автоматически. Верифицировать.
- [ ] **Bridges** (obfs4/snowflake) — для users в jurisdictions где Tor заблокирован. kmp-tor поддерживает, но требует config и fetch-er для bridge lines.
- [ ] **Kill-switch** — если Tor процесс внезапно умирает во время сессии, приложение должно заблокировать network пока пользователь не ре-запустит. Сейчас ws просто disconnect и пытается переконнектиться — direct попытка попадёт через hedging в случае fallback (сейчас fallback отключён — fail-closed).

### P2.2 — Signed releases (H5)

**Угроза:** Пользователь скачивает подменённый installer → код выполняется с правами юзера.

**План:**
- Windows: code-signing certificate ($300/год EV-cert) + signtool при `packageMsi`.
- macOS: Apple Developer ID + `codesign` + notarization через `xcrun notarytool`.
- Linux: GPG-detached signatures на `.deb`/`.AppImage`, SHA256SUMS file.
- [cosign](https://github.com/sigstore/cosign) + sigstore: keyless signing через GitHub OIDC.

**Effort:** 1 неделя + годовой bill на certs.

### P2.3 — JWT expiry client-side validation (L2)

**Угроза:** Подделанный JWT проскакивает локальную проверку до server 401.

**План:** Парсить `exp` claim при load session, fetchnew если остаётся <5 мин. Тривиально.

**Effort:** 2 часа.

### P2.4 — Multi-device (L3)

**Угроза:** Пользователь не может использовать несколько устройств для одного аккаунта.

**План:** Rust side — export `rotate_device(device_id)`; server side — prekey bundle per device; client — SSL pairing через QR-код или shared passphrase.

**Effort:** 2-3 недели — требует изменения и в Rust, и в backend.

### P2.5 — OTPK persistence (L1, Phase 05.1 #7)

**Угроза:** One-time prekeys consumed во время X3DH не сохраняются → если app killed в середине handshake, messages in-flight могут стать undecryptable.

**План:**
1. Добавить таблицу `crypto_one_time_prekey` (id, public, private, consumed BOOL).
2. Rust-side: `load_prekeys` при init() загружает неconsumed OTPKs обратно в store.
3. Сервер отмечает consumed OTPKs — клиент помечает локально по подтверждению.

**Effort:** 1 неделя — требует Rust API extension.

### P2.6 — App lock / idle timeout (M1)

**Угроза:** Неблокированный десктоп + открытое приложение = любой читает переписку.

**План:** `LockState` в domain, `IdleMonitor` в `:app`, UI-экран unlock (когда UI будет). Бинд к OS biometric через OS API. Actual implementation зависит от UI.

**Effort:** 3 дня когда появится UI.

### P2.7 — Plaintext memory hygiene (L5)

**Угроза:** Decrypted messages в String heap → memory dump / swap recover.

**План:** Переделать `Message.plaintext` на `CharArray`, явно зануллить после закрытия чата, опционально JNA-based mlock для чат-rendering buffer.

**Effort:** Несколько дней, coupling с UI-агентом.

## Смена master key (key rotation)

Если pointer на master key компрометируется / пользователь жалуется на подозрительную активность:

1. Вызвать `masterKeyManager.reset()` — удаляет ключ из OS keystore.
2. При следующем запуске автоматически генерится новый.
3. Все AES-GCM blob'ы в SQLite больше не decrypt'ятся → treated as null → пользователь ре-логинится, Signal keys регенерируются.

Это не "rotation" с сохранением данных, а scorched-earth. Для seamless rotation нужна re-encrypt процедура — выносим в P2.8 при необходимости.

## Verification

- [`./gradlew :crypto:smokeTest`](crypto/src/main/kotlin/run/koto/desktop/crypto/SmokeTest.kt) — проверяет native integrity + keygen + Signal Protocol round-trip.
- `./gradlew :app:run` — smoke-тест всего graph'а.
- После любого изменения `crypto/` Rust crate — **обязательно** `./gradlew :crypto:pinNativeHash` для регенерации `koto_crypto.sha256`.

## Contact

Security-issues в [.planning/phases/05.1-security-and-bug-fix-sweep/](../.planning/phases/05.1-security-and-bug-fix-sweep/) — phase продолжается, PRs на фиксы приветствуются.
