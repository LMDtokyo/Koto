# Безопасность

## Принцип: Security by Default

Безопасность не добавляется потом — она закладывается с первого коммита.

## ЗАПРЕЩЕНО (абсолютно)

- Хардкодить секреты, ключи, пароли в коде
- Коммитить `.env` файлы
- Использовать `bcrypt` — только **Argon2id** (`argon2` crate)
- Форматировать SQL через `format!()` — только `sqlx::query!` с параметрами `$1, $2`
- Использовать `unsafe` без ревью и комментария
- Использовать `.unwrap()` / `.expect()` в продакшен коде
- Доверять входным данным без валидации (`validator` crate)
- Отключать TLS/HTTPS
- Логировать пароли, токены, персональные данные
- Хранить JWT в localStorage на фронте (только httpOnly cookie или memory)
- Отдавать stack trace / panic message клиенту в продакшене

## Преимущества Rust для безопасности

- **Memory safety** — нет buffer overflow, use-after-free, null pointer dereference
- **No data races** — компилятор гарантирует отсутствие гонок данных
- **No GC паузы** — предсказуемая latency
- **Compile-time SQL проверка** — SQLx проверяет запросы на этапе компиляции
- **Type system** — невозможно перепутать ID пользователя и ID канала (newtype pattern)

## Аутентификация

### Пароли
- Хеширование: **Argon2id** (по [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html))
  - memory: 46 MiB (m=47104)
  - iterations: 1 (t=1)
  - parallelism: 1 (p=1)
  - Формат: PHC string `$argon2id$v=19$m=47104,t=1,p=1$<salt>$<hash>`
- Минимальная длина: 8 символов, максимум 128
- Не требовать спецсимволы/цифры/заглавные (OWASP)
- Все Unicode-символы разрешены, пробелы включительно
- Проверка по базе утечённых паролей (HaveIBeenPwned API, k-anonymity, SHA-1 prefix)

### Токены
- **Access Token**: JWT, подписан ES256 (ECDSA)
  - TTL: 15 минут
  - Payload: `{ sub, iat, exp }` — минимум данных
- **Refresh Token**: opaque (random bytes), хранится в PostgreSQL
  - TTL: 30 дней
  - Ротация при каждом использовании
  - Привязан к device fingerprint

### 2FA
- TOTP (RFC 6238) — Google Authenticator, Authy
- WebAuthn — аппаратные ключи (YubiKey)
- Recovery codes: 10 одноразовых кодов при активации

### OAuth2
- Только Authorization Code Flow + PKCE
- Никогда Implicit Flow
- state parameter обязателен (защита от CSRF)

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /auth/login | 5 запросов | 15 минут |
| POST /auth/register | 3 запроса | 1 час |
| POST /channels/:id/messages | 5 сообщений | 5 секунд (per channel) |
| API (общий) | 50 запросов | 1 секунда |
| WebSocket events | 120 событий | 1 минута |
| File upload | 10 файлов | 1 минута |

**Реализация**: Redis sliding window (`crates/rate-limit`)

При превышении:
- Ответ: `429 Too Many Requests`
- Заголовки: `Retry-After`, `X-RateLimit-Remaining`
- Логирование аномалий

## Защита от атак

### XSS (Cross-Site Scripting)
- Фронтенд-фреймворк автоматически экранирует шаблоны (Tauri 2 webview)
- Content Security Policy (CSP) заголовки
- Никогда вставлять неэкранированный HTML без санитизации
- Санитизация markdown (DOMPurify на клиенте)
- Серверная санитизация контента сообщений (`ammonia` crate)

### CSRF (Cross-Site Request Forgery)
- SameSite=Strict cookies
- Origin header проверка на WebSocket handshake
- CSRF токен для мутаций (если cookie-based auth)

### SQL Injection
- SQLx — compile-time проверка SQL, параметризованные запросы `$1, $2`
- Никогда не интерполировать переменные в SQL строки
- Prepared statements для raw queries

### SSRF (Server-Side Request Forgery)
- Link preview: whitelist доменов
- Запрет запросов к приватным IP (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.1)
- Таймаут на внешние запросы: 5 секунд
- DNS rebinding protection

### DDoS
- Cloudflare / edge protection
- Rate limiting на всех уровнях
- Connection limits на WebSocket Gateway
- Captcha при подозрительной активности (hCaptcha)

## WebSocket Security

- Только `wss://` (TLS)
- Проверка Origin header при handshake
- Аутентификация: JWT в первом сообщении (IDENTIFY, 5 сек таймаут)
- Rate limiting: 120 events/min per connection
- Max payload size: 16KB
- Heartbeat timeout: 30 сек (нет ответа → disconnect)
- Reconnect flood protection

## Медиа / файлы

- Whitelist MIME-типов (проверка magic bytes, не расширения)
- Максимальный размер: 25MB
- Антивирус: ClamAV
- Strip EXIF данных из изображений
- Рандомные имена файлов (UUIDv7)
- Отдельный домен для CDN (без cookies)
- `Content-Disposition: attachment` для скачивания
- `X-Content-Type-Options: nosniff`

## HTTP Security Headers

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0  (CSP заменяет это)
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

## Секреты и конфигурация

- Секреты: Kubernetes Secrets (или Vault для enterprise)
- Env переменные: `.env.example` в репо (без значений), `.env` в `.gitignore`
- Ротация ключей: JWT signing key менять раз в 90 дней
- Минимум привилегий: каждый сервис имеет доступ только к своей БД

## Логирование безопасности

### Что логировать
- Неуспешные логины (IP, user-agent, username)
- Смена пароля, включение/выключение 2FA
- Изменение email
- Подозрительная активность (rate limit превышен, необычный IP)
- Административные действия

### Что НЕ логировать
- Пароли (даже хеши)
- Токены (access, refresh)
- Полные номера карт
- Содержимое личных сообщений (только metadata)

## Чеклист перед релизом

- [ ] Все endpoints защищены аутентификацией (кроме /auth/login, /auth/register)
- [ ] Rate limiting на всех публичных endpoints
- [ ] Входные данные валидируются (`validator` crate)
- [ ] SQL запросы параметризованы
- [ ] Нет hardcoded секретов в коде
- [ ] HTTP security headers настроены
- [ ] TLS 1.2+ для всех соединений
- [ ] CORS настроен (только наши домены)
- [ ] Файлы проверяются перед сохранением
- [ ] Ошибки не раскрывают внутреннюю структуру
- [ ] Dependency audit пройден (`cargo audit`)
