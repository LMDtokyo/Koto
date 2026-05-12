# Koto Protocol — спецификация идей

> Это не «свой MTProto» (мы не изобретаем wire-формат и crypto). Это
> **операционная спецификация** поверх стандартных слоёв: TLS 1.3 + WebSocket +
> Signal Protocol + Kyber. Как у Signal, Matrix и SimpleX.
>
> Цель — определить, что именно мы делаем **по-другому**, и где можем
> опередить существующие приватные мессенджеры.

---

## 0. Слои стека

```
┌────────────────────────────────────────────┐
│  Koto Protocol (application layer)         │  ← мы пишем эту спеку
│  - Envelope, events, handshake, federation │
├────────────────────────────────────────────┤
│  Signal Protocol + PQXDH (libsignal)       │  ← готово, MIT/Apache
│  - Double Ratchet, Sealed Sender, Kyber    │
├────────────────────────────────────────────┤
│  TLS 1.3 + WebSocket + HTTP/2              │  ← стандарт IETF
└────────────────────────────────────────────┘
```

Менять что-либо ниже application layer — это **roll-your-own-crypto** и `not happening`.

---

## 1. Идентичность без телефонов и email

### Уже сделано

- BIP39 seed (12 слов) → HKDF-SHA256 → Curve25519 private key.
- `account_id = hex(public_key)` — 64 hex.
- Опционально `@username` через user-service.

### Что добавить (P1 идеи)

**1.1. Device-specific identity (Linking).**
Сейчас все устройства одного аккаунта имеют один и тот же приватный identity-ключ (деривируется из seed на каждом устройстве). Это даёт владельцу seed = доступ к аккаунту, но ломает forward secrecy между устройствами.

Решение: каждое устройство имеет свою пару ключей `(device_pk, device_sk)`. Их подписывает «master identity», который держится в seed. Сервер хранит **set of device-pks** для аккаунта. Ratchet идёт `peer_device_n → my_device_m`.

Получаем:
- Возможность отозвать одно устройство (revoke device_n) без уничтожения всего аккаунта.
- При краже одного телефона — sessions других устройств не компрометированы.
- Multi-device sync через **paired devices** (как Signal-Desktop linking через QR).

**1.2. Username с приватным резервированием.**
Сейчас username — публичный (поиск по нему). Опционально дать пользователю «hidden alias»: `account_id` шифруется в username-record, никто кроме него не может разрешить. Сценарий: «пиши мне на @koto-rize, но если ты не в моих контактах — я даже не вижу что ты искал».

---

## 2. Envelope сообщения

### Текущее (P0)

```json
{
  "conversation_id": "uuid",
  "message_id": "timeuuid",
  "sender_id": "hex64",
  "ciphertext": "base64",
  "sent_at": 1715200000,
  "type": 1,
  "reply_to": "msg-id"
}
```

### Идея — единый Envelope с **Sealed Sender** (Signal-style)

Outer-frame, который видит сервер:
```
Outer = { conv_id, recipient_ids[], sealed_envelope_bytes }
```

Внутри `sealed_envelope` — зашифровано identity-ключом получателя:
```
SealedInner = {
  sender_id,         // ← скрыт от сервера
  message_id,
  sent_at,
  inner_ciphertext   // ← Signal Protocol payload (E2E)
}
```

Сервер **не знает кто отправитель** — только список получателей. Это и есть Sealed Sender. Signal делает так с 2018, у нас пока обычный sender.

**Что добавить помимо Signal:**

- **Sender certificate rotation**: каждое устройство периодически меняет «sender certificate» (привязка `device_pk ↔ account_id`). Сервер хранит только текущий, прошлые auto-expire через 7 дней.
- **Padding до фикс-длины** (PKCS#7 + ceil к 256/512/1024 байт): ciphertext-length всегда кратна корзине. Network observer не отличает «hi» от длинного абзаца.
- **Timing jitter**: send-frame через random 0–500ms задержку (опционально). Защита от traffic-analysis.

---

## 3. Group Protocol — сразу на MLS, минуя SenderKeys

Signal использует **Sender Keys** для групп — это работает, но имеет ограничения:
- Каждый new-member заставляет всех ratchet'ить заново (O(N²) сообщения).
- Нет post-quantum для групп (Signal обещает, но пока нет).

Альтернатива: **MLS — Messaging Layer Security** (RFC 9420, IETF, 2023).

| | Sender Keys (Signal) | MLS |
|--|---------------------|-----|
| Группа 1000 человек | работает но неуклюже | дизайнили под 50k+ |
| New member rotation | O(N) per add | O(log N) |
| Post-quantum | нет (пока) | хук есть, можно вставить Kyber |
| Реализации в Rust | libsignal-протокол | [openmls](https://github.com/openmls/openmls) (MIT/Apache) |

**Идея**: Koto-группы строим сразу на MLS через `openmls`. Получаем:
- Масштабируемость до больших групп
- Post-quantum hook готов
- IETF-стандарт → потенциальная interoperability в будущем

Это нас отличит **от всех** privacy-мессенджеров. Signal/Matrix-Megolm пока не на MLS (Matrix только пилотирует).

---

## 4. Key Transparency — то, чего нет ни у кого кроме WhatsApp и Apple

**Проблема**: сервер может подсунуть вам фальшивый identity-key другого пользователя (server-controlled MITM). Сейчас у Signal есть «safety numbers» — но их 99% юзеров не сравнивают.

**Решение**: Key Transparency log. Подсмотрено у:
- [WhatsApp Key Transparency](https://engineering.fb.com/2023/04/13/security/whatsapp-key-transparency/) (2023, на основе CONIKS + Merkle-tree)
- [Apple iMessage Contact Key Verification](https://security.apple.com/blog/imessage-contact-key-verification/) (2023)

Как это работает:
1. Сервер ведёт **append-only Merkle-tree** всех `(account_id, identity_pk, version)`.
2. Каждый чанк дерева публикуется в публичный лог (или несколько).
3. Клиент при каждом контакте **проверяет inclusion proof** — что текущий identity peer'а действительно в дереве.
4. Если сервер попытается подменить ключ — это будет видно в логе (и любой watcher заметит несоответствие).

Технически:
- Серверная часть: Merkle-tree поверх Postgres/ScyllaDB, обновляется per-epoch (раз в час).
- Клиент-часть: проверяет proof при первом контакте + раз в неделю.
- **Public auditor**: третья сторона (мы или комьюнити) скачивает дерево и проверяет append-only-ность.

Реализации в open-source:
- [Sigsum](https://www.sigsum.org/) — minimalistic key transparency log (Apache 2.0)
- [Google Trillian](https://github.com/google/trillian) — production-grade Merkle-tree (Apache 2.0)

**Это убойная фича для маркетинга «приватного мессенджера»**: «никто, включая нас, не может подменить ваш ключ — каждое изменение публично логируется».

---

## 5. Metadata минимизация

Что сервер видит сейчас:
- `account_id` отправителя
- `account_id` получателей
- Время сообщения
- Размер ciphertext
- IP-адрес клиента

Цель: спрятать как можно больше.

### 5.1. Sealed Sender (см. §2)
Сервер не знает sender'а.

### 5.2. Conversation salt
`conversation_id` сейчас один на весь чат → сервер видит «кто с кем общается». Идея:
- `conversation_id` deriv'ed как HMAC-SHA256(shared_secret, epoch) → меняется раз в день.
- Сервер видит «есть тред X и тред Y», но не может связать «это тот же чат, что вчера».

Минус: усложняет сервер-side state. Не делать в P0.

### 5.3. Padding и pad-to-length (см. §2)

### 5.4. Onion-routing опционально (через **Arti** — Rust Tor)
- В Tauri: `arti` (https://gitlab.torproject.org/tpo/core/arti, MIT) даёт нам Tor SOCKS5-proxy без внешних зависимостей.
- Toggle «hide your IP» в settings → весь трафик через Tor → наш gateway за `.onion`.
- Latency хуже (1-2s vs 50ms), но опционально для пользователей-параноиков.
- В `desktop/` (Compose) **уже есть** через kmp-tor. Можем портировать в Tauri.

### 5.5. IP minimization через Cloudflare WS-proxy
В пользовательских настройках: «передавать через Cloudflare» (трафик идёт `client → cloudflare → koto.run`). Cloudflare видит IP, но не передаёт его дальше. Не идеал, но средний слой защиты бесплатно.

---

## 6. Backup и migration

Самая больная точка приватных мессенджеров: **что если ты потерял телефон/seed**?

### Идея — encrypted backup-blob, self-custody

1. Пользователь нажимает «Экспорт чатов».
2. Клиент берёт всю SQLite-историю → AEAD-encrypts (XChaCha20-Poly1305) ключом, derived из **passcode** + **Argon2id** (memory-hard KDF).
3. Получается blob (`koto-backup-2026-05-09.bin`) → пользователь сохраняет где хочет: USB, GDrive, Mega, Tarsnap.
4. На новом устройстве: вход через seed → импорт blob с passcode → история восстановлена.

Никаких облачных бэкапов на наших серверах. Self-custody: **только пользователь имеет passcode**.

Реализация: WebCrypto / age (Rust crate, https://github.com/str4d/rage) — стандарт для encrypted file format.

### Опционально — share with one trusted contact

«Если ты потеряешь seed — разделить ключ восстановления на 2 части по схеме Shamir, отдать part 1 другу, part 2 хранить на USB. Любой 1 из 2 не разблокирует, но 2-of-2 дают восстановление».

---

## 7. Push notifications без раскрытия

### Проблема

iOS APNS требует «notification body» в plain (Apple читает). Android FCM — то же самое. Pushy/UnifiedPush лучше, но не всегда доступны.

### Решение

1. Push-payload содержит **только** `{"sender": "wake_up"}` — никакого preview, отправителя, conversation_id.
2. Клиент при получении такого push'а:
   - Запускается в фоне на 5-10 секунд
   - Подключается к WebSocket → fetches новые сообщения
   - Decrypt → формирует **локальное** уведомление
3. Apple/Google видят только «koto-app проснулся», не содержание.

Это паттерн Signal/Threema. Делается тривиально через `content-available` push.

### Альтернатива для Linux desktop — **UnifiedPush + ntfy**
[ntfy](https://ntfy.sh) — open-source push-relay, можно self-hosted. UnifiedPush — Android-стандарт, не зависит от Google. Для Linux Tauri — собственный self-hosted relay, никакой Apple/Google вообще.

---

## 8. Voice / Video — на WebRTC поверх Signal

### Стек (когда будем делать)

- **Signal Protocol** для key exchange (как Signal делает)
- **WebRTC SDP** через signalling-канал (наш WebSocket)
- **DTLS-SRTP** для media — стандарт WebRTC
- **TURN server** наш собственный (coturn, MIT, self-hosted) для NAT traversal
- **SFU для групп**: [LiveKit](https://github.com/livekit/livekit) (Apache 2.0, Go) или [mediasoup](https://mediasoup.org/) (Apache 2.0, Node.js)

WebRTC — стандарт W3C, реализован в WebView Tauri. Минимум собственной работы.

---

## 9. Federation — опционально, как у Matrix

В будущем (P3): пользователь `@alice:koto.run` может писать `@bob:family-server.example`. Серверы общаются между собой через тот же Koto Protocol.

Зачем:
- **Independence** — кто-то может поднять свой Koto-сервер для своей семьи / community.
- **Resistance against takedown** — нельзя «убить» Koto одним судебным решением.
- **Networking effects** Matrix-style — много мелких серверов лучше одного.

Минус: усложняет moderation, abuse, key transparency.

Реализация: **server-to-server protocol** — отдельная REST-API на тех же сервисах + sender_certificate-валидация cross-domain.

---

## 10. Что делает Koto Protocol уникальным

| Фича | Signal | Matrix | SimpleX | Telegram | **Koto** |
|------|--------|--------|---------|----------|----------|
| E2E by default | ✅ | ⚠️ опционально | ✅ | ❌ только secret chats | ✅ |
| **Post-quantum (Kyber)** | ✅ (2024) | ❌ pilot | ❌ | ❌ | ✅ из коробки |
| **MLS для групп** | ❌ Sender Keys | ⚠️ pilot | ❌ | ❌ | ✅ план |
| **Key Transparency** | ❌ Safety Numbers | ❌ | ❌ | ❌ | ✅ план |
| Без телефона | ❌ требует phone | ✅ | ✅ | ⚠️ phone обязателен | ✅ |
| **Federation** | ❌ | ✅ | ✅ | ❌ | ✅ план (P3) |
| Self-hosted backups | ⚠️ нет | ✅ | ✅ | ❌ их сервера | ✅ план |
| Tor mode | ❌ | ⚠️ только web | ✅ | ⚠️ proxy в settings | ✅ из `desktop/` |
| Open source | ✅ | ✅ | ✅ | client only | ✅ |
| Лицензия server | AGPL | AGPL/Apache | AGPL | proprietary | AGPL-free (MIT/Apache) |

### Где наш реальный «moat»

1. **MLS + Kyber для групп** — единственный privacy-мессенджер, у которого это будет в продакшене раньше Signal.
2. **Key Transparency** — кроме WhatsApp и iMessage этого нет ни у кого, и они closed-source. Мы будем первыми open-source с реальным KT.
3. **AGPL-free** licensing — позволяет нам делать enterprise-варианты, которые невозможны на стеке Signal/Matrix/SimpleX.
4. **Tor-from-day-one** — у `desktop/` уже есть, осталось портировать.
5. **Self-custody backups** — обычная фича, но Signal/Telegram её специально не делают (vendor lock-in).

---

## 11. Roadmap по уровням протокола

### Уровень A — оперативное (сделать в MVP)
- ✅ Identity из seed (готово)
- ✅ X3DH + PQXDH session establishment (libsignal, готово)
- ✅ Direct messages с E2E (только что сделали)
- ✅ Sealed Sender — **TODO** на пост-MVP, ~2 недели
- ⏳ Username + Key Transparency lite (без Merkle-tree пока, простой server-side log) — 1 неделя

### Уровень B — после MVP
- MLS для групп через `openmls` — 4–6 недель
- Backup format (age-encrypted) — 1 неделя
- Tor-mode через `arti` — 1 неделя
- Sender certificate rotation — 3 дня

### Уровень C — серьёзный апгрейд
- Полноценный Key Transparency через Trillian — 2–3 недели
- Federation server-to-server — 6–8 недель
- Voice/video через WebRTC + LiveKit — 4–6 недель
- Device linking (отдельные device-keys, не shared identity) — 2 недели

---

## 12. Что делать прямо сейчас

Я предлагаю не «переписывать всё» (мы только что сделали MVP), а **поэтапно вносить уникальные фичи**:

**Шаг 1 (сейчас, после MVP):** Sealed Sender + simple Key Transparency lite.
**Шаг 2 (через 1 месяц):** MLS-pilot для маленькой группы (2-3 человека).
**Шаг 3 (через 2 месяца):** Tor-mode из коробки.
**Шаг 4 (через 3 месяца):** Encrypted backups + iOS-клиент через Compose Multiplatform.

К моменту public-beta мы имеем то, чего **нет ни у кого** в OSS-приватных-мессенджерах:
- Post-quantum + MLS + Key Transparency + Tor + AGPL-free.

Это и есть наш «свой протокол» — не другой wire-формат, а **другая комбинация и более продвинутые operational guarantees** на стандартных кирпичах.

---

## Sources

- [Signal Protocol Specifications](https://signal.org/docs/specifications/doubleratchet/)
- [PQXDH whitepaper (Signal, 2023)](https://signal.org/docs/specifications/pqxdh/)
- [MLS RFC 9420 (IETF, 2023)](https://datatracker.ietf.org/doc/html/rfc9420)
- [WhatsApp Key Transparency engineering blog](https://engineering.fb.com/2023/04/13/security/whatsapp-key-transparency/)
- [Apple iMessage Contact Key Verification](https://security.apple.com/blog/imessage-contact-key-verification/)
- [CONIKS paper (USENIX 2015)](https://www.usenix.org/system/files/conference/usenixsecurity15/sec15-paper-melara.pdf)
- [Sigsum minimal KT log (Apache 2.0)](https://www.sigsum.org/)
- [Google Trillian (Apache 2.0)](https://github.com/google/trillian)
- [openmls — MLS implementation in Rust (MIT/Apache)](https://github.com/openmls/openmls)
- [Arti — Tor in Rust (MIT)](https://gitlab.torproject.org/tpo/core/arti)
- [age — encrypted file format (Apache 2.0)](https://github.com/FiloSottile/age) / [rage Rust](https://github.com/str4d/rage)
- [LiveKit SFU (Apache 2.0)](https://github.com/livekit/livekit)
