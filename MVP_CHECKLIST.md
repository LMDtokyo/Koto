# Koto — MVP Checklist

> Что должно быть, чтобы можно было сказать «мы запустились» — и без чего нельзя.

---

## Текущее состояние одной строкой

- **Backend (Go-микросервисы)** — production-ready, не нужно ничего ломать.
- **Crypto crate (Rust + libsignal)** — собран, экспортирован через uniffi, **не интегрирован в Tauri-клиент**.
- **Android-клиент** — стабильный, не наш фокус сейчас.
- **Desktop `desktop/` (Compose/JVM)** — эталон, full-featured, можно использовать как MVP-альтернативу.
- **Tauri-клиент `tauri-koto/`** — UI готов на 90%, но **сообщения идут plaintext в base64** (нет E2EE). Это блокер.
- **Website** — scaffold + design-brief, ничего не построено.
- **Production deployment** — нет.

---

## Жёсткий scope-cut: что в MVP НЕ входит

Чтобы не растягивать запуск:

- ❌ Группы (group conversations + sender keys)
- ❌ Голосовые сообщения
- ❌ Голосовые/видео-звонки
- ❌ Stories
- ❌ Боты
- ❌ TOR-режим
- ❌ iOS-клиент (только Android + Desktop в MVP)
- ❌ Notifications через APNS / UnifiedPush (только in-app)
- ❌ Экспорт чатов
- ❌ Веб-версия (только download-сайт)

Это **пост-MVP**. Сейчас всё это заглушки в UI — оставляем заглушки, не вырезаем (чтобы видно было направление).

---

## P0 — блокеры. Без этого MVP не существует

### 1. Реальное E2EE в Tauri-клиенте 🔴

**Файлы:**
- [crypto/src/lib.rs](crypto/src/lib.rs) — есть `KotoCrypto::encrypt/decrypt/process_prekey_bundle`.
- [tauri-koto/src-tauri/src/](tauri-koto/src-tauri/src/) — нет интеграции.
- [tauri-koto/src/shared/services/cryptoService.ts](tauri-koto/src/shared/services/cryptoService.ts) — только `crypto_registration_smoke`.
- [tauri-koto/src/features/chat/chatThread.ts:318](tauri-koto/src/features/chat/chatThread.ts) — `utf8TextToCiphertextBase64` (plaintext в base64).

**Что сделать:**
1. В `src-tauri/Cargo.toml` подключить локальный crate `koto-crypto` (path-зависимость).
2. Создать `src-tauri/src/crypto.rs` с handlers:
   - `crypto_init_session(account_id, identity_key_pair, registration_id)` — стартует `KotoCrypto`, держит в `tauri::State`.
   - `crypto_load_prekey_bundle(peer_id)` — фетчит `/v1/keys/{peer_id}` и `process_prekey_bundle`.
   - `crypto_encrypt(peer_id, plaintext)` → `(type, ciphertext_base64)`.
   - `crypto_decrypt(peer_id, ciphertext_base64)` → `plaintext`.
3. В TS заменить `utf8TextToCiphertextBase64` на вызов `crypto_encrypt`. Decode при получении WS — через `crypto_decrypt`.
4. Storage сессий Signal — на старте использовать **in-memory `InMemSignalProtocolStore`**. Потом — persistent (P1).
5. При первой отправке peer'у — fetch его prekey bundle и инициализировать сессию. Cache `Set<peerId>` уже инициализированных.

**Почему критично:** «Приватный мессенджер без E2EE» — это маркетинговая ложь. Запускать так нельзя.

**Объём:** 2–3 дня одному человеку.

---

### 2. Регистрация → отправка → доставка (golden path) 🔴

**Сейчас:**
- Регистрация работает, токены сохраняются.
- WS подключается.
- Сообщение посылается plaintext в base64.
- WS-фрейм приходит — добавлен real-time handler.

**Что проверить (manual e2e):**
1. Регистрация Alice → seed → quiz → finish → попадает в чат-лист пустым.
2. Регистрация Bob (второе устройство / приватное окно).
3. Alice находит Bob по @username.
4. Friend request → Bob принимает.
5. Alice пишет «привет» → ciphertext улетает на сервер → Bob получает по WS → видит «привет».
6. Bob отвечает → Alice видит без перезагрузки.

**Где сейчас сломается:**
- Шаг 5–6: нет реального шифрования (см. P0.1).
- Возможно: на 0 chat history bug → проверить `loadThreadMessages({reset: true})`.
- Возможно: первый prekey-bundle 404 если не запушен (`/v1/auth/prekeys/publish` после регистрации).

**Что сделать:** на регистрации сразу публиковать prekeys (используем `crypto_*` handlers + auth REST). Выделить под это `cryptoBootstrap.ts`.

**Объём:** 1 день после P0.1.

---

### 3. Production deployment стека 🔴

**Сейчас:** `make stack` поднимает локально. Production — нет.

**Что нужно:**
- Реверс-прокси (Nginx/Caddy) с TLS (Let's Encrypt) перед gateway:8080 и :9080.
- Domain `koto.run` → A-запись → VPS.
- `docker-compose.prod.yml` с persistent volumes (`postgres_data`, `scylla_data`, `minio_data`) и backups.
- Секреты в `.env.production` (не в репо), JWT seed реальный, MinIO credentials.
- Healthcheck monitoring (хотя бы UptimeRobot или простой cron).
- MinIO public-endpoint должен быть достижим с клиента (не `10.0.2.2`).

**Что сделать практически:**
1. Купить VPS (Hetzner / Scaleway / DigitalOcean — 4–8 GB RAM).
2. Установить Docker + docker-compose.
3. Скопировать `Makefile` + compose, добавить `prod`-таргет.
4. Caddy с автоматическим TLS — самый простой путь.
5. Поднять, проверить `https://koto.run/health`.

**Объём:** 1 день devops.

---

### 4. Сборка и distribution Tauri-клиента 🔴

**Сейчас:** `npm run tauri dev` работает, `npm run tauri build` не проверен.

**Что нужно:**
- Проверить `tauri build --release` локально на каждой ОС (или через Tauri-action в GitHub Actions).
- Подписать бинари (опционально для MVP — без code-signing будет «unrecognized publisher», но запускается).
- Хостить релизы — **GitHub Releases** (бесплатно, готов под CDN).
- На сайте `/download` ссылки ведут на `github.com/rize/koto/releases/latest/download/...`.
- Версионирование: `v0.1.0` в `Cargo.toml` + `package.json` синхронизировано.

**GitHub Actions workflow** (`.github/workflows/release.yml`):
- Trigger: `push tags v*`
- Matrix: Linux / macOS / Windows
- Использовать `tauri-apps/tauri-action@v0`.

**Объём:** полдня.

---

### 5. Минимальный сайт `koto.run` 🔴

**Что нужно для MVP-сайта:**
- `/` — Hero + ссылка на скачать (Prompt 1 из DESIGN_BRIEF.md).
- `/download` — кнопки под Linux/Mac/Win + Android (Prompt 4).
- `/about` — манифест (Prompt 5) — может быть просто manifesto.md.
- `/privacy` — privacy-policy одна страница (юридический минимум: что собираем, как храним, как удалить аккаунт).
- `/terms` — terms одной страницей (обязательно для App Store потом).
- Footer с GitHub и контактами.

**News, Docs — пост-MVP.** На запуск можно указать «Документация скоро».

**Deploy:** Vercel / Netlify / Cloudflare Pages из репо за 5 минут.

**Объём:** 1 день после design-генерации.

---

## P1 — важно для базового мессенджера

Без этих фич MVP запускается, но ощущается «недоделанным». Делаем после P0.

### 6. Загрузка медиа в чате 🟡

- Бэк готов: `/v1/media/upload-url`, `/v1/media/{id}`. Уже используем для аватара.
- Нужно: attach-кнопка в композере → file-picker → upload → отправить как сообщение `type=2` (image) или `type=3` (file) с `media_id` внутри ciphertext.
- Рендер в bubble: img-preview с lazy-load, click → fullscreen.
- E2EE: файл шифруется client-side **перед** загрузкой в MinIO (не plaintext upload). Симметричный ключ кладётся в ciphertext сообщения.

**Объём:** 2 дня.

---

### 7. Установка @username и поиск по нему 🟡

- Бэк: `PUT /v1/users/me/username`, `GET /v1/users/username-available/{u}`, `GET /v1/users/by-username/{u}`.
- Фронт: в Profile editor сделать поле `@username` **рабочим** (сейчас disabled).
- Username-availability check на onChange (debounce 500ms).
- В new-chat при вводе `@nick` → `searchUsers(nick)` уже работает.

**Объём:** полдня.

---

### 8. Persistent local cache (offline read) 🟡

- Сейчас все сообщения читаются с сервера каждый раз → включил приложение, нет интернета → пустой чат.
- Решение: SQLite через `tauri-plugin-sql` (уже в roadmap [tauri-koto/DESKTOP_PORT.md](tauri-koto/DESKTOP_PORT.md)).
- Минимум: таблицы `conversations`, `messages` локально. Кешировать decrypt'нутый plaintext.
- При запуске: показать кеш → подгрузить свежее.

**Объём:** 1.5 дня.

---

### 9. Уведомления (in-app) 🟡

- Звуковой beep + visual badge на иконке окна (Tauri tray) при новом сообщении когда окно неактивно.
- Системные toast'ы через `@tauri-apps/plugin-notification`.
- Уважать настройку «Не беспокоить» (уже есть toggle в settings).

**Объём:** полдня.

---

### 10. Edit / Delete сообщения 🟡

- Бэк готов: `PATCH/DELETE /v1/conversations/{convID}/messages/{msgID}`.
- UI: hover на своём сообщении → context-menu (или кнопка ✎ / 🗑).
- Edit → inline-textarea вместо bubble, Enter сохранить, Esc отменить.
- Delete → подтверждение → удаление из локального DOM + сервер.
- WS-фреймы `message_edited` уже маршрутизируются — нужен handler.

**Объём:** 1 день.

---

### 11. Реакции на сообщения 🟡

- Бэк готов.
- UI: при hover на bubble → emoji-picker (6 быстрых: 👍 ❤️ 😂 😮 😢 🙏).
- Рендер: pill под bubble с emoji + счётчик. Click → toggle.
- WS `reaction` фрейм маршрутизируется → handler.

**Объём:** 1 день.

---

### 12. Прочее по UI 🟡

- Закрепление чатов (PATCH conversation? на бэке нет — нужен endpoint, либо локальный pin в SQLite).
- Архив (бэк нет — локально или endpoint новый).
- Read-receipts (галочки ✓✓ — есть в UI, но синхронизации с сервером нет).
- Indicator «печатает…» (нужен WS event, на бэке не реализован).

**Объём суммарно:** 2 дня. Можно отложить ВСЁ, кроме pin'а на главную.

---

## P2 — после MVP

- Группы + sender keys.
- Голосовые сообщения с waveform.
- Звонки (WebRTC + SFU).
- iOS-клиент (Compose Multiplatform или Swift).
- Stories.
- Боты.
- TOR-режим (как в desktop/).
- Backup экспорт seed + чатов в зашифрованный файл.
- Темы (кастомные accent-цвета).
- Stickers.
- Polls.

---

## Чеклист безопасности перед публичным запуском

- [ ] JWT seed в production — не из `.env.example`, а сгенерированный заново.
- [ ] Postgres / Scylla / MinIO credentials уникальные.
- [ ] `prekeys` действительно публикуются после регистрации (не в out-of-sync).
- [ ] Crypto-сессии хранятся в защищённом месте (хотя бы файловая система с правами 600, лучше keyring).
- [ ] HTTPS-only (HSTS header).
- [ ] CORS на gateway: только `koto.run` и tauri-bundle.
- [ ] Rate-limiting на гейтвей: 10 r/s per IP на REST, 1 connection per token на WS.
- [ ] Минимальный security-audit крипто-flow (хоть бы collegue review).
- [ ] Privacy policy реально соответствует: что серверы видят (только зашифрованное), что хранят (метаданные: account_id, timestamps).
- [ ] `security.txt` на сайте с PGP-ключом для responsible disclosure.

---

## С чего начинать прямо сейчас

**Неделя 1 (1 человек):**
1. День 1: P0.1 — Rust crypto handlers, замена `utf8TextToCiphertextBase64`.
2. День 2: P0.1 — prekey-bundle fetch + session init flow.
3. День 3: P0.1 — decrypt входящих, проверка на двух аккаунтах.
4. День 4: P0.2 — golden path manual-test, фикс багов.
5. День 5: P0.4 — `tauri build` на трёх ОС через GitHub Actions, релиз `v0.1.0-alpha`.

**Неделя 2:**
1. День 6–7: P0.3 — деплой стека на VPS, домен, TLS.
2. День 8: P0.5 — собрать сайт по DESIGN_BRIEF.md (минимум Home + Download + About + Privacy).
3. День 9: P1.7 — username editor работает.
4. День 10: P1.6 — медиа в чате (фото).

**Неделя 3:**
1. День 11–12: P1.8 — SQLite кеш сообщений.
2. День 13: P1.10 — edit/delete.
3. День 14: P1.11 — reactions.
4. День 15: smoke-test, public alpha launch.

После этого MVP = публичная альфа, можно говорить «попробуйте Koto» в комьюнити.

---

## Что я сейчас могу сделать прямо тут

В порядке приоритета, скажи — берусь:

1. **Rust crypto handlers + интеграция в TS** (P0.1) — самое главное, ~3–4 часа работы в чате.
2. **Prekey-bundle публикация при регистрации** (P0.2) — 1 час.
3. **`tauri build` тестирование локально + GitHub Actions релиз** (P0.4) — 1 час.
4. **Username editor flow** (P1.7) — 30 минут.
5. **Edit/Delete сообщений** (P1.10) — 1.5 часа.
6. **Reactions UI** (P1.11) — 1.5 часа.
7. **Сайт** (P0.5) — большой кусок, 2–3 часа на минимум.

Скажи, с чего начинаем.
