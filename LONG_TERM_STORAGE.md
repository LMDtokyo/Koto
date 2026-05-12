# Долгосрочное хранение и поиск сообщений в Koto

> Что использовать, чтобы пользователь мог найти своё сообщение даже через год.
> На основе паттернов 2026 (Meta Labyrinth, Signal Search, ScyllaDB encryption-at-rest, SQLCipher + FTS5).

---

## TL;DR — итоговая архитектура

| Слой | Что хранит | Чем зашифровано | Где живёт |
|------|-----------|-----------------|-----------|
| **Сервер: ScyllaDB** | Ciphertext-блобы + метаданные (msg_id, conv_id, sender, sent_at) | Signal Protocol (на клиенте) **+** AES at-rest (диск/Scylla EE) | VPS, retention бесконечно |
| **Клиент: SQLite + SQLCipher** | Расшифрованные plaintext'ы + FTS5-индекс для поиска | AES-256-GCM, ключ из seed-фразы | Локальное устройство |
| **Клиент: FTS5 виртуальная таблица** | Полнотекстовый индекс по plaintext'ам | Тот же SQLCipher key | Локально |

**Ключевой принцип:** сервер хранит навсегда (или сколько нужно), но **никогда не видит plaintext**. Поиск — только локально, по локально расшифрованному индексу. Это та же модель, что у Signal и Meta Messenger Labyrinth — отличие в деталях реализации.

---

## Серверная сторона (уже есть, нужно полировать)

### ScyllaDB как primary store сообщений

У нас уже есть `services/chat` на ScyllaDB. Нужно докрутить:

**1. Схема + clustering key (уже сделано):**
```cql
CREATE TABLE messages (
  conversation_id  uuid,
  message_id       timeuuid,        -- TimeUUID гарантирует сортировку по времени
  sender_id        text,
  ciphertext       blob,
  message_type     int,
  sent_at          timestamp,
  client_seq       bigint,
  reply_to         text,
  forwarded_from   text,
  edited_at        timestamp,
  PRIMARY KEY ((conversation_id), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

Партиция = conversation_id, clustering = TimeUUID (DESC). `SELECT ... LIMIT N` пагинирует с курсором естественно.

**2. Compaction-стратегия для long-term retention:**

Перейти на **Time Window Compaction Strategy (TWCS)** — стандарт для time-series в Cassandra/ScyllaDB. Окна по неделе или месяцу, старые SSTables не пересжимаются:

```cql
ALTER TABLE messages WITH compaction = {
  'class': 'TimeWindowCompactionStrategy',
  'compaction_window_unit': 'DAYS',
  'compaction_window_size': '7'
};
```

Это снижает write amplification и позволяет хранить миллионы сообщений на доступном объёме диска.

**3. Encryption at-rest:**

ScyllaDB Enterprise поддерживает [encryption at rest](https://docs.scylladb.com/manual/stable/operating-scylla/security/encryption-at-rest.html) с AES-128 по умолчанию (можно AES-256). Если используем ScyllaDB Open Source — шифруем диск целиком через LUKS на VPS:

```bash
# При создании VPS-volume:
cryptsetup luksFormat --type luks2 --cipher aes-xts-plain64 /dev/sdb
```

Это даёт защиту от физического доступа к диску, но не от компрометации live-инстанса (это уже зона E2E).

**4. Retention policies (опционально):**

Если пользователь хочет «удалять через N дней» — TTL прямо в insert:
```cql
INSERT INTO messages (...) VALUES (...) USING TTL 15552000;  -- 180 дней
```
TTL — управляется флагом `expires_at` в API (уже есть в нашем `SendInput`).

**5. Index на messages для поиска:**

ScyllaDB **не предназначен** для full-text поиска. Не добавляем secondary index по ciphertext — это бессмысленно (он зашифрован). Поиск делается **только client-side** по plaintext-индексу. Сервер отдаёт сообщения по convId+cursor — это и есть его роль.

---

## Клиентская сторона (что нужно сделать)

### SQLite через `@tauri-apps/plugin-sql` + SQLCipher

Локальная БД на каждом устройстве. План таблиц:

```sql
-- Сами сообщения. Plaintext записываем после успешного decrypt.
CREATE TABLE messages_local (
  message_id   TEXT PRIMARY KEY,
  conv_id      TEXT NOT NULL,
  sender_id    TEXT NOT NULL,
  sent_at      INTEGER NOT NULL,        -- unix sec
  message_type INTEGER NOT NULL,        -- 1=text, 2=image, ...
  plaintext    TEXT,                    -- NULL до decrypt; после — расшифровано
  ciphertext   BLOB,                    -- кэш для retry-decrypt
  reply_to     TEXT,
  edited_at    INTEGER,
  status       TEXT DEFAULT 'sent',     -- sent / delivered / read
  FOREIGN KEY (conv_id) REFERENCES conversations_local(conv_id)
);
CREATE INDEX idx_messages_conv_time ON messages_local(conv_id, sent_at DESC);

-- Состояние синхронизации с сервером.
CREATE TABLE sync_state (
  conv_id          TEXT PRIMARY KEY,
  last_synced_at   INTEGER NOT NULL,    -- max(sent_at) на момент последнего sync
  oldest_local_at  INTEGER,             -- самое старое в локальном кеше — для гайды по scroll-back
  has_more_remote  INTEGER DEFAULT 1
);

-- FTS5: полнотекстовый поиск по plaintext'ам.
-- contentless-mode чтобы избежать дублирования + сразу пишется в SQLCipher.
CREATE VIRTUAL TABLE messages_fts USING fts5(
  message_id UNINDEXED,
  conv_id UNINDEXED,
  plaintext,
  tokenize = "unicode61 remove_diacritics 2"
);

-- Триггер: при INSERT/UPDATE/DELETE messages_local — синхронизируем FTS.
CREATE TRIGGER messages_local_ai AFTER INSERT ON messages_local
WHEN NEW.plaintext IS NOT NULL
BEGIN
  INSERT INTO messages_fts(message_id, conv_id, plaintext)
  VALUES (NEW.message_id, NEW.conv_id, NEW.plaintext);
END;

CREATE TRIGGER messages_local_au AFTER UPDATE OF plaintext ON messages_local
WHEN NEW.plaintext IS NOT NULL
BEGIN
  DELETE FROM messages_fts WHERE message_id = NEW.message_id;
  INSERT INTO messages_fts(message_id, conv_id, plaintext)
  VALUES (NEW.message_id, NEW.conv_id, NEW.plaintext);
END;
```

### SQLCipher для at-rest шифрования

SQLite сам по себе хранит данные plain. Поверх — **SQLCipher** (256-bit AES-CBC + HMAC-SHA512), стандарт де-факто для encrypted SQLite в мобильных мессенджерах (Signal, Threema, Wire).

Tauri:
- Самый простой путь — `tauri-plugin-sql` + опция SQLCipher в Cargo (нужно собирать с feature `sqlite-cipher`).
- Альтернатива — [`rusqlite` + `bundled-sqlcipher` feature](https://docs.rs/rusqlite/latest/rusqlite/) и наш собственный Tauri-handler.

**Ключ SQLCipher** деривируется из seed-фразы:

```rust
// При инициализации DB:
let key = hkdf::Hkdf::<Sha256>::new(None, &seed_bytes)
    .expand(b"koto/sqlite-storage/v1", &mut output_32_bytes);
let pragma = format!("PRAGMA key = \"x'{}'\"", hex::encode(output_32_bytes));
db.execute_batch(&pragma)?;
```

Тот же seed, что генерирует identity-key, генерирует и SQLCipher-key — но через отдельный `info` в HKDF. Получается: владелец seed = владелец БД.

### Поиск

Клиент:
```sql
-- "найти все упоминания «отпуск» во всех чатах за всё время"
SELECT m.message_id, m.conv_id, m.sender_id, m.sent_at,
       snippet(messages_fts, 2, '<b>', '</b>', '…', 16) AS preview
FROM messages_fts
JOIN messages_local m ON m.message_id = messages_fts.message_id
WHERE messages_fts MATCH 'отпуск'
ORDER BY m.sent_at DESC
LIMIT 50;
```

FTS5 поддерживает русский (unicode61 + диакритика). Bloom-фильтры в SQLite уже встроены в query-planner (с 2022). Производительность на 100к сообщений — миллисекунды.

### Синхронизация с сервером

При открытии чата:
1. Показать всё, что есть в `messages_local` (instant).
2. Запросить сервер: `GET /v1/conversations/{id}/messages?since={last_synced_at}` — добор «новых».
3. Расшифровать → upsert в `messages_local`. Триггер обновит FTS.
4. Если пользователь прокрутил вверх до старых границ → `?cursor={oldest_local_at}` за следующей пачкой с сервера.

При WS `new_message`:
1. Decrypt.
2. Insert в `messages_local`.
3. FTS обновляется триггером.

При decrypt в чате (backfill старых) — то же самое.

### Memory & Storage budget

- 100к сообщений по 200 байт plaintext = 20 МБ + ~30 МБ FTS index ≈ 50 МБ на 100к. На современных устройствах — копейки.
- Нет нужды стирать локально через 6 мес. Ограничение «последние 100 сообщений на чат», которое сейчас в `offlineCache.ts` — это временная мера до подключения SQLite.

---

## Угрозы и компромиссы

| Угроза | Mitigation |
|--------|-----------|
| Кража ноутбука | SQLCipher с ключом из seed; без seed БД нечитаема. |
| Компрометация сервера | Сервер видит только ciphertext (Signal-encrypted). Поиск физически невозможен на сервере. |
| Кража одного устройства из multi-device | На остальных устройствах — свои локальные DB. Сервер хранит ciphertext, новое устройство при первом sync скачает и расшифрует. |
| Потеря seed | Невосстановимо. Юзер должен экспортировать backup (encrypted JSON, защищённый паролем) — это **отдельная фича пост-MVP**. |
| Memory dump на работающем устройстве | Не защищено (как и в Signal/Threema). Только полный disk-encryption ОС спасёт. |
| Долгосрочное retention сервера | Положительно: пользователь может перейти на новое устройство и подтянуть всю историю. Negatively: метаданные (timestamps, partition sizes) выдают паттерны общения. Решение пост-MVP — **traffic padding** + **server-side metadata stripping**. |

---

## Что делать СЕЙЧАС vs ПОТОМ

### Этап А — MVP (lightweight)
✅ **Уже сделано:** in-memory cache (`messageCache.ts`) + localStorage snapshot (`offlineCache.ts`, последние 100/чат). Это работает в альфе.
✅ ScyllaDB с TimeUUID partitioning + cursor-based history. Сервер хранит навсегда.
⚠️ TWCS на messages таблице — **добавить миграцию** в `services/chat/migrations/`.

### Этап Б — Full long-term (P1.8 расширенный)
1. Установить `@tauri-apps/plugin-sql` + Cargo feature `sqlite-cipher`.
2. Создать миграции локальной БД (см. CREATE TABLE выше).
3. Дериватор ключа из seed (`crypto::derive_storage_key`).
4. Клиентский слой `localStore.ts`: `loadConv`, `appendMessage`, `markPlaintext`, `searchFts(query)`.
5. UI поиска: уже есть `thread-inline-search-input` (per-chat) + добавить **глобальный** Cmd+K-search по всем чатам.
6. Sync-логика: replace `offlineCache.ts` + dropna localStorage limits.

### Этап В — Production hardening (пост-MVP)
1. ScyllaDB Enterprise лицензия (или AWS/GCP managed) для at-rest encryption + backups.
2. Encrypted backups seed-фразы → опционально загрузить в свою облако (Tarsnap-style: zero-knowledge).
3. Подписки `koto.run/billing`: оплата за расширенный retention / большие медиа.
4. Audit logs (key rotations, sessions revoked, etc.).

---

## Конкретные команды для реализации Этапа Б

```bash
# 1. Tauri-plugin-sql c SQLCipher
cd tauri-koto
npm install @tauri-apps/plugin-sql

# 2. В src-tauri/Cargo.toml добавить:
# tauri-plugin-sql = { version = "2", features = ["sqlite-cipher"] }

# 3. Capability: разрешить sql.execute / sql.select для приложения
# (через src-tauri/capabilities/default.json)

# 4. Миграции — в src-tauri/migrations/0001_init.sql
```

---

## Sources

- [Meta Messenger Labyrinth — End-to-End Encryption Overview (PDF, 2023)](https://engineering.fb.com/wp-content/uploads/2023/12/MessengerEnd-to-EndEncryptionOverview_12-6-2023.pdf)
- [Signal Search — Brown ESL blog (encrypted client-side search proposal)](https://esl.cs.brown.edu/blog/signal/)
- [ScyllaDB Encryption at Rest documentation](https://docs.scylladb.com/manual/stable/operating-scylla/security/encryption-at-rest.html)
- [ScyllaDB Cloud DB-level encryption at rest (Feb 2026)](https://www.scylladb.com/2026/02/16/getting-started-with-database-level-encryption-at-rest-in-scylladb-cloud/)
- [Cassandra/ScyllaDB Time-Series Data Modeling glossary](https://www.scylladb.com/glossary/cassandra-time-series-data-modeling/)
- [SQLCipher — Encrypted SQLite (Zetetic)](https://www.zetetic.net/sqlcipher/)
- [SQLite FTS5 — Full-Text Search](https://www.sqlite.org/fts5.html)
- [SQLite Bloom Filter Optimization (HN discussion)](https://news.ycombinator.com/item?id=42486610)
- [Privacy-Enhanced Searches Using Encrypted Bloom Filters — Bellovin (Columbia)](https://mice.cs.columbia.edu/getTechreport.php?techreportID=483)
- [Proxy-Mediated Searchable Encryption in SQL (eprint 2019/806)](https://eprint.iacr.org/2019/806.pdf)
- [Threema vs Signal сравнение хранения](https://threema.com/en)

---

## Следующий шаг

Сейчас переписать [P1.8 в MVP_CHECKLIST.md](MVP_CHECKLIST.md) на «Этап Б» (полноценная локальная SQLite + FTS5 + SQLCipher). Если ОК — берусь делать в следующей итерации:
1. Зависимости + capability.
2. Миграции SQL.
3. Rust-команды `db_init`, `db_upsert_message`, `db_search_fts`.
4. TS-обёртки.
5. Замена `offlineCache.ts` (localStorage) на `localStore.ts` (SQLite).
6. UI поиска (per-chat + глобальный Cmd+K).
7. Sync-логика: at startup → all conv'ы pull `since={last_synced_at}`.

Это ~1.5 дня работы. После — у пользователя сообщения за 6 месяцев и больше можно искать в milliseconds локально, а сервер хранит ciphertext «вечно» при минимальных затратах диска.
