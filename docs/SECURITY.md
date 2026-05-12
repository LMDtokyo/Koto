# Koto — Security & Resilience

> Что мы защищаем, от кого, и какими средствами. Документ актуален на 2026-05-10.

---

## 1. Модель угроз

### Кто атакует

1. **Пассивный наблюдатель** — провайдер пользователя, чужая Wi-Fi сеть. Видит весь трафик до сервера.
2. **Активный сетевой блокировщик** — Роскомнадзор / ТСПУ / GFW / etc. Делает DPI, throttling, IP-блок, DNS-poison.
3. **Компрометированный сервер** — наш или скомпрометированный gateway. Видит ciphertext и метаданные.
4. **Кража устройства** — физический доступ к разблокированному (или не очень) клиенту.
5. **Государство-противник** — судебный запрос к нам / нашему хостеру.

### Что защищаем

| Актив | От кого | Чем |
|-------|---------|-----|
| **Plaintext сообщений** | все 5 категорий | E2E (Signal Protocol + PQXDH) |
| **Метаданные** (кто кому пишет) | компрометированный сервер | Sealed Sender (план), conversation salt (план) |
| **Identity-keys** | сервер | Key Transparency log (план) |
| **Достижимость сервиса** | сетевой блокировщик | Multi-domain + Cloudflare + Bridges (Reality/Hysteria2) + Tor |
| **Локальная история чатов** | кража устройства | SQLCipher с ключом из seed |
| **Доступ к аккаунту** | кража устройства | Optional passcode-lock (план) |

---

## 2. Стек устойчивости к блокировкам

### Слои клиента (auto-fallback каскад)

```
[1] Direct        → https://api.koto.run            (TLS+WS, 50-100ms latency)
[2] Cloudflare    → https://edge.koto-cdn.net       (тот же бэк через CF Worker)
[3] Reality       → bridge1.koto.run:443            (VLESS+Reality, мимикрия под legit-TLS)
[4] Hysteria2     → bridge2.koto.run:8443/UDP       (для нестабильных мобильных сетей)
[5] Tor           → onion-address.onion             (через arti, obfs4 pluggable transport)
```

Клиент пробует [1], таймаут 5s → [2] → [3] и т.д. Активный transport кэшируется в localStorage,
проверяется при каждом старте и переключается при сбое. UI показывает индикатор «текущий канал».

### Серверная сторона — bridge-pool

- **20-50 VPS** у разных провайдеров (Hetzner / OVH / DO / Vultr / Contabo).
- На каждом — Caddy + xray-core с Reality, замаскированным под `microsoft.com:443`.
- Bridge регистрируется в нашем **manifest-сервисе** (signed Ed25519, обновляется в `/bridges.json`).
- Когда bridge детектится РКН (через 1-3 недели) — выкидываем, добавляем новый. Постоянная ротация.

### DNS-устойчивость

- Клиент **никогда не использует системный DNS** при cенз-mode. Только DoH через Cloudflare 1.1.1.1, Quad9, NextDNS.
- Hard-coded fallback DoH-URLs — нельзя заблокировать DNS-poison.
- Bootstrap-domain имеет 3-5 зеркал: `koto.run`, `koto-app.com`, `koto-cdn.net` etc.

### Бэк-структура

- Gateway за **Cloudflare WS-proxy** — IP скрывается за Cloudflare anycast (РКН не блочит весь Cloudflare).
- Database (Postgres / Scylla / Dragonfly / NATS / MinIO) — за NAT, **не expose'd наружу**.
- TLS-сертификаты через Let's Encrypt автоматически (Caddy).
- LUKS-encryption диска VPS (хост уровень).

### Модуль `koto/desktop/src-tauri/src/transport/`

- `mod.rs` — trait `Tunnel`, enum `TransportKind`, `TransportSelection`.
- `direct.rs` — DirectTunnel (HTTPS+WS health-probe).
- `manifest.rs` — bundled-default manifest + версионирование.
- `commands.rs` — Tauri-handlers для UI.

Будущие импл: `cloudflare.rs`, `reality.rs` (через xray-core / sing-box / собственный), `tor.rs` (arti).

---

## 3. End-to-end шифрование

| Свойство | Реализация |
|----------|------------|
| Identity keys | Ed25519, deriv из BIP39 seed через HKDF-SHA256 |
| Session establishment | X3DH + PQXDH (Kyber-1024) |
| Message encryption | Double Ratchet (libsignal v0.51+) |
| Multi-device | Shared identity-from-seed (план: device-keys + linking) |
| Group encryption | Sender Keys (план: миграция на MLS RFC 9420) |
| Forward secrecy | Да, на каждое сообщение новый ratchet |
| Post-compromise security | Да, после ratchet новый ключ |
| Sealed sender | План (см. KOTO_PROTOCOL.md §2) |

**Сервер видит:**
- Sender + recipient account_id (до Sealed Sender).
- Timestamp.
- Размер ciphertext.
- IP клиента (если не через Cloudflare/Tor).

**Сервер НЕ видит:**
- Plaintext сообщений.
- Identity-keys (хранятся только публичные).
- Avatar / banner (хранятся encrypted в MinIO; план — AES-обёртка перед upload).

---

## 4. Модерация при E2EE

### Принцип

E2EE и серверная модерация **математически несовместимы**. Сервер не может читать то, что зашифровано
ключами, которых у него нет. Все приватные мессенджеры (Signal, Threema, WhatsApp E2E) принимают этот
trade-off.

### Что мы делаем

**1. Report-flow** — основной механизм.
- В context-menu любого сообщения — кнопка «Пожаловаться».
- При клике пользователь явно соглашается: «отправить plaintext этого сообщения и 5 предыдущих в Koto Trust & Safety».
- Plaintext + контекст шлются на `POST /v1/moderation/report` по обычному E2E-шифрованному каналу (мы расшифруем — потому что юзер сам согласился).
- На сервере жалоба прогоняется через **OpenAI Moderation API** (free до 10М токенов/мес).
- При категории `csam` / `terrorism` / `weapons_illicit` / `drugs_illicit` — **автоматический ban** аккаунта-нарушителя.
- Остальные категории идут в очередь human-модераторов через `GET /v1/moderation/pending`.

**2. Auto-ban при критичных категориях.**
- `moderation_actions` таблица хранит ban'ы.
- Перед каждым `SendMessage` chat-сервис должен дёргать `GET /v1/moderation/account/{id}` — TODO в P2.

**3. On-device ML (план)** — TensorFlow Lite классификатор картинок на клиенте перед отправкой.
- Блокирует отправку известных категорий локально.
- Не нарушает E2EE (мы не видим контент, клиент сам решает).
- Криминалы fork'нут код без сканера — поэтому это **не** основная защита.

**4. Hash-list known CSAM (план)** — PhotoDNA / NCMEC perceptual hashes в локальной базе клиента.
- Подаём заявку в [NCMEC](https://www.missingkids.org/gethelpnow/cybertipline) или [Microsoft PhotoDNA Cloud](https://www.microsoft.com/en-us/photodna).

**5. Public groups / channels (план)** — там модерация полностью возможна:
- Group sender keys → все участники могут читать; модератор-bot тоже участник.
- Бот читает контент, удаляет нарушения.

### Что мы НЕ делаем

- **Не сканируем 1:1 переписку без явного согласия** — это сломало бы E2EE.
- **Не передаём данные правоохранительным без судебного запроса.** Когда передаём — только метаданные (account_id, timestamps), потому что plaintext у нас и так нет.
- **Не сотрудничаем с Роскомнадзором** — мы юридически вне РФ.

### Сервис `services/moderation/`

- `domain/report.go` — типы Report, Action, Classifier.
- `app/service.go` — use-case `SubmitReport`, авто-classify, авто-ban при critical.
- `infra/openai_classifier.go` — OpenAI Moderation API (omni-moderation-latest).
- `infra/postgres/repos.go` — ReportRepo + ActionRepo.
- `transport/http/handler.go` — `/v1/moderation/{report,pending,account/:id}`.
- `migrations/001_reports.sql` — таблицы `moderation_reports`, `moderation_actions`.

---

## 5. Юр-сторона

### Где зарегистрироваться

Кандидаты:
- **Швейцария** — privacy-friendly, юридическая база Threema.
- **Эстония** — e-residency, дешёвая регистрация юрлица, EU GDPR.
- **Сейшелы / BVI** — оффшор, минимум disclosure (но проблемы с банками).

Регистрация **не в РФ** — иначе РФ-закон требует:
- Хранение метаданных 6 мес.
- Доступ ФСБ при запросе.
- Регистрация в реестре ОРИ.

### Privacy Policy / Terms

При первом публичном релизе обязательно:
- **Privacy Policy**: что мы видим (метаданные), что не видим (plaintext), сколько храним.
- **Terms of Service**: что запрещено (CSAM, terrorism, illegal commerce), report-flow, ban-policy.
- **Transparency report** (раз в квартал) — сколько запросов от государств, сколько ban'ов по типам.

---

## 6. Текущий статус (2026-05-10)

| Компонент | Статус |
|-----------|--------|
| E2E base (libsignal + Kyber) | ✅ работает |
| Identity from seed | ✅ работает |
| Multi-device shared identity | ✅ работает (план: device-keys) |
| Direct transport | ✅ работает |
| Cloudflare-edge transport | 🟡 endpoint в manifest, Worker — TODO |
| Reality transport | 🟡 каркас, integration с xray-core — TODO |
| Hysteria2 transport | ❌ план |
| Tor transport | ❌ план (arti integration) |
| Auto-fallback | 🟡 структура есть, runtime-логика — TODO |
| DoH client | ❌ план |
| Bridge-pool deploy | ❌ план (Terraform/Ansible scripts) |
| Sealed Sender | ❌ план |
| Key Transparency | ❌ план |
| Moderation service skeleton | ✅ создан, не подключён в gateway |
| Report-button в UI | ❌ план |
| OpenAI Moderation classify | ✅ готов в коде, нужен `OPENAI_API_KEY` |
| Auto-ban critical | ✅ работает в SubmitReport |
| Admin panel модератора | ❌ план |
| On-device CSAM scanner | ❌ план |
| Privacy Policy / Terms | ❌ нужно написать перед launch |

---

## 7. Что делается прямо сейчас

См. TodoWrite в текущей сессии. Каркасы готовы, дальше — последовательное наполнение
конкретными реализациями. Основной упор: **transport pipeline до рабочего bridge** (Reality)
и **report-button в UI** (видимая фича для пользователя).

---

## 8. Sources

- [Russia DPI / DNS / TSPU 2026 — Mediazona](https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026)
- [VLESS + Reality vs Hysteria2 vs Shadowsocks-2022](https://greatfirewallguide.com/lab/protocol-matrix)
- [Why VLESS+Reality is the last protocol — Meridian](https://getmeridian.org/blog/01-why-vless-reality/)
- [Telegram MTProto Fake-TLS](https://www.companionlink.com/blog/2026/04/mtproto-proxy-for-telegram-how-it-works-and-why-it-bypasses-blocking-better-than-vpn/)
- [sing-box (universal proxy core, GPL)](https://github.com/SagerNet/sing-box)
- [xray-core (MIT)](https://github.com/XTLS/Xray-core)
- [arti — Tor in Rust (MIT)](https://gitlab.torproject.org/tpo/core/arti)
- [OpenAI Moderation API](https://platform.openai.com/docs/guides/moderation)
- [Apple NeuralHash + client-side scanning debate](https://www.techmonitor.ai/policy/privacy-and-data-protection/client-side-scanning-content-moderation)
- [Meta CSAM hash matching with E2EE](https://peterrohde.org/meta-ai-explains-the-backdoors-in-meta-messenger-whatsapps-end-to-end-encryption/)
- [MLS RFC 9420](https://datatracker.ietf.org/doc/html/rfc9420)
- [WhatsApp Key Transparency](https://engineering.fb.com/2023/04/13/security/whatsapp-key-transparency/)
