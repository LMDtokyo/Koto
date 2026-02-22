# Media Service

Сервис управления файлами и медиа. Загрузка, обработка изображений, EXIF strip, антивирус, presigned URLs, CDN.

Порт: `3005`
Путь: `services/media/`
Хранилище: MinIO (S3-совместимый)
Кеш: Redis

## Источники

- [Discord Developer Docs — CDN Endpoints](https://docs.discord.com/developers/reference)
- [Discord Support — File Attachments FAQ](https://support.discord.com/hc/en-us/articles/25444343291031-File-Attachments-FAQ)
- [Discord Support — Server Boosting FAQ](https://support.discord.com/hc/en-us/articles/360028038352-Server-Boosting-FAQ)
- [AWS SDK for Rust — Presigned URLs](https://docs.aws.amazon.com/sdk-for-rust/latest/dg/presigned-urls.html)
- [AWS SDK for Rust — S3 Examples](https://docs.aws.amazon.com/code-library/latest/ug/rust_1_s3_code_examples.html)
- [MinIO — Object Lifecycle Management](https://min.io/docs/minio/linux/administration/object-management/object-lifecycle-management.html)
- [OWASP — Unrestricted File Upload](https://owasp.org/www-community/vulnerabilities/Unrestricted_File_Upload)
- [ClamAV Documentation](https://docs.clamav.net/)
- [crate: aws-sdk-s3](https://docs.rs/aws-sdk-s3/latest/aws_sdk_s3/)
- [crate: image](https://docs.rs/image/latest/image/)
- [crate: infer](https://docs.rs/infer/latest/infer/)
- [crate: kamadak-exif](https://docs.rs/kamadak-exif/latest/exif/)
- [crate: img-parts](https://docs.rs/img-parts/latest/img_parts/)
- [crate: clamav-client](https://docs.rs/clamav-client/latest/clamav_client/)
- [crate: ammonia](https://docs.rs/ammonia/latest/ammonia/) — SVG sanitization
- [crate: validator](https://docs.rs/validator/latest/validator/)
- [Google CDN — Web Security Best Practices](https://docs.cloud.google.com/cdn/docs/web-security-best-practices)
- [MDN — Cache-Control](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control)

---

## Структура сервиса

```
services/media/
├── src/
│   ├── main.rs
│   ├── config.rs
│   ├── errors.rs
│   ├── routes/
│   │   ├── mod.rs
│   │   ├── upload.rs          # Presigned URL generation, upload confirmation
│   │   ├── attachments.rs     # Attachment CRUD
│   │   ├── avatars.rs         # Avatar upload/delete
│   │   ├── banners.rs         # Guild/user banners
│   │   ├── emojis.rs          # Custom emoji upload
│   │   └── proxy.rs           # Image proxy для external URLs
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── upload.rs
│   │   ├── process.rs         # Image processing pipeline
│   │   ├── scan.rs            # ClamAV scanning
│   │   └── cleanup.rs         # Temp file cleanup
│   ├── models/
│   │   ├── mod.rs
│   │   ├── attachment.rs
│   │   └── upload_session.rs
│   ├── middleware/
│   │   ├── auth.rs
│   │   └── rate_limit.rs
│   ├── events/
│   │   ├── publisher.rs
│   │   └── subscriber.rs
│   ├── storage/
│   │   ├── mod.rs
│   │   ├── s3.rs              # MinIO/S3 client wrapper
│   │   └── presigned.rs       # Presigned URL generation
│   └── processing/
│       ├── mod.rs
│       ├── resize.rs          # Thumbnail generation
│       ├── exif.rs            # EXIF stripping
│       ├── validate.rs        # MIME validation (magic bytes)
│       └── svg.rs             # SVG sanitization
├── tests/
│   ├── common/mod.rs
│   ├── upload_test.rs
│   ├── process_test.rs
│   └── scan_test.rs
├── Cargo.toml
└── Dockerfile
```

---

## Cargo.toml

```toml
[package]
name = "media-service"
version = "0.1.0"
edition = "2024"

[dependencies]
# HTTP / async
axum = { workspace = true }
axum-extra = { version = "0.10", features = ["multipart"] }
tokio = { workspace = true }
tower = { workspace = true }
tower-http = { workspace = true }

# Сериализация
serde = { workspace = true }
serde_json = { workspace = true }

# S3 / MinIO
aws-sdk-s3 = "1"
aws-config = "1"
aws-credential-types = "1"

# Image processing
image = "0.25"
img-parts = "0.3"           # EXIF stripping (zero-copy)

# MIME detection
infer = "0.19"

# Антивирус
clamav-client = "0.5"

# SVG sanitization
ammonia = "4"

# Кеш
redis = { workspace = true }
deadpool-redis = "0.18"

# Брокер событий
async-nats = { workspace = true }

# Аутентификация
jsonwebtoken = { workspace = true }

# Валидация
validator = { workspace = true }

# Время / ID
chrono = { workspace = true }
uuid = { workspace = true }

# HTTP клиент (для image proxy)
reqwest = { version = "0.12", features = ["stream"] }

# Ошибки
thiserror = { workspace = true }

# Логирование
tracing = { workspace = true }
tracing-subscriber = { workspace = true }

# Конфигурация
config = "0.15"
dotenvy = "0.15"

# Внутренние crates
common = { path = "../../crates/common" }
snowflake = { path = "../../crates/snowflake" }
permissions = { path = "../../crates/permissions" }
cache = { path = "../../crates/cache" }
nats-events = { path = "../../crates/nats-events" }
rate-limit = { path = "../../crates/rate-limit" }
```

---

## Конфигурация (config.rs)

```rust
pub struct MediaConfig {
    // Сервер
    pub host: String,                    // MEDIA_HOST=0.0.0.0
    pub port: u16,                       // MEDIA_PORT=3005

    // MinIO / S3
    pub s3_endpoint: String,             // S3_ENDPOINT=http://minio:9000
    pub s3_region: String,               // S3_REGION=us-east-1
    pub s3_access_key: String,           // S3_ACCESS_KEY=...
    pub s3_secret_key: String,           // S3_SECRET_KEY=...
    pub s3_bucket_attachments: String,   // S3_BUCKET_ATTACHMENTS=attachments
    pub s3_bucket_avatars: String,       // S3_BUCKET_AVATARS=avatars
    pub s3_bucket_emojis: String,        // S3_BUCKET_EMOJIS=emojis
    pub s3_bucket_banners: String,       // S3_BUCKET_BANNERS=banners

    // CDN
    pub cdn_base_url: String,            // CDN_BASE_URL=https://cdn.example.com
    pub presigned_upload_ttl_secs: u64,  // PRESIGNED_UPLOAD_TTL_SECS=900
    pub presigned_download_ttl_secs: u64,// PRESIGNED_DOWNLOAD_TTL_SECS=3600

    // Redis
    pub redis_url: String,               // REDIS_URL=redis://...

    // NATS
    pub nats_url: String,                // NATS_URL=nats://...

    // JWT (валидация)
    pub jwt_public_key: String,          // JWT_PUBLIC_KEY=...

    // ClamAV
    pub clamav_host: String,             // CLAMAV_HOST=clamav
    pub clamav_port: u16,                // CLAMAV_PORT=3310

    // Лимиты
    pub max_attachment_size: usize,      // MAX_ATTACHMENT_SIZE=26214400 (25 MiB)
    pub max_avatar_size: usize,          // MAX_AVATAR_SIZE=10485760 (10 MiB)
    pub max_emoji_size: usize,           // MAX_EMOJI_SIZE=262144 (256 KiB)
    pub max_banner_size: usize,          // MAX_BANNER_SIZE=10485760 (10 MiB)

    // Image proxy
    pub proxy_timeout_ms: u64,           // PROXY_TIMEOUT_MS=5000
    pub proxy_max_size: usize,           // PROXY_MAX_SIZE=10485760 (10 MiB)
}
```

---

## Формат ошибок

```json
{
    "code": "FILE_TOO_LARGE",
    "message": "File exceeds maximum allowed size"
}
```

### Коды ошибок

| Код | HTTP | Описание |
|-----|------|----------|
| `FILE_TOO_LARGE` | 413 | Файл превышает лимит |
| `INVALID_FILE_TYPE` | 400 | Недопустимый MIME тип |
| `INVALID_IMAGE_DIMENSIONS` | 400 | Изображение слишком большое/маленькое |
| `UPLOAD_NOT_FOUND` | 404 | Upload session не найдена |
| `UPLOAD_EXPIRED` | 410 | Presigned URL истёк |
| `SCAN_FAILED` | 400 | Файл не прошёл антивирусную проверку |
| `SVG_UNSAFE` | 400 | SVG содержит XSS-вектор |
| `MISSING_PERMISSIONS` | 403 | Нет прав |
| `RATE_LIMITED` | 429 | Превышен rate limit |
| `STORAGE_ERROR` | 500 | Ошибка MinIO/S3 |
| `PROCESSING_ERROR` | 500 | Ошибка обработки изображения |
| `VALIDATION_ERROR` | 400 | Ошибка валидации |

---

## Upload Flow

### Presigned URL flow (рекомендуемый)

```
1. Client → POST /media/upload/presign
   {"filename": "photo.jpg", "content_type": "image/jpeg", "size": 2048576}

2. Server:
   a. Валидация (тип, размер)
   b. Генерация upload_id (Snowflake)
   c. Генерация presigned PUT URL (MinIO)
   d. Сохранить upload session в Redis (TTL 15 мин)

3. Server → Client
   {"upload_id": "...", "upload_url": "https://s3.../presigned...", "expires_at": "..."}

4. Client → MinIO (PUT upload_url, body: file bytes)

5. Client → POST /media/upload/confirm
   {"upload_id": "..."}

6. Server:
   a. Проверить upload session в Redis
   b. Скачать файл из temp bucket
   c. Проверить magic bytes (infer crate)
   d. Антивирус ClamAV
   e. Image processing (resize, EXIF strip, thumbnails)
   f. Переместить в постоянный bucket
   g. Удалить upload session из Redis

7. Server → Client
   {"id": "...", "url": "https://cdn.../...", "filename": "photo.jpg", ...}
```

### Presigned URL generation (aws-sdk-s3)

```rust
use aws_sdk_s3::presigning::PresigningConfig;
use std::time::Duration;

let presigning = PresigningConfig::builder()
    .expires_in(Duration::from_secs(900)) // 15 минут
    .build()?;

let presigned = s3_client
    .put_object()
    .bucket("temp-uploads")
    .key(format!("uploads/{upload_id}/{filename}"))
    .content_type(&content_type)
    .content_length(size as i64)
    .presigned(presigning)
    .await?;

let upload_url = presigned.uri().to_string();
```

---

## Bucket Structure (MinIO)

| Bucket | Назначение | Naming scheme | Lifecycle |
|--------|-----------|---------------|-----------|
| `temp-uploads` | Временные загрузки | `uploads/{upload_id}/{filename}` | Expire: 1 час |
| `attachments` | Вложения сообщений | `{channel_id}/{message_id}/{snowflake_id}/{filename}` | Без expiry |
| `avatars` | Аватары пользователей | `{user_id}/{snowflake_id}.webp` | Без expiry |
| `emojis` | Custom emoji | `{guild_id}/{emoji_id}.webp` | Без expiry |
| `banners` | Баннеры гильдий/юзеров | `{entity_type}/{entity_id}/{snowflake_id}.webp` | Без expiry |
| `icons` | Иконки гильдий | `{guild_id}/{snowflake_id}.webp` | Без expiry |

### Object naming

- Имена файлов **никогда** не берутся из пользовательского ввода напрямую
- Путь строится из ID-ов (Snowflake) + оригинальное расширение
- Защита от path traversal: никаких `../`, только alphanumeric + ограниченный набор символов
- Оригинальное имя файла хранится в БД (`attachments.filename`), а не в object key

---

## MIME Detection (magic bytes)

### infer crate

```rust
use infer;

fn validate_mime(bytes: &[u8], expected_type: &str) -> Result<String, Error> {
    let kind = infer::get(bytes).ok_or(Error::InvalidFileType)?;
    let mime = kind.mime_type();

    // Проверить whitelist
    if !ALLOWED_MIMES.contains(mime) {
        return Err(Error::InvalidFileType);
    }

    // Для изображений: доп. проверка что расширение совпадает
    Ok(mime.to_string())
}
```

### Whitelist MIME типов

**Изображения:**
- `image/jpeg`
- `image/png`
- `image/gif`
- `image/webp`

**Видео:**
- `video/mp4`
- `video/webm`
- `video/quicktime`

**Аудио:**
- `audio/ogg` (voice messages: Opus codec, 48kHz)
- `audio/mpeg`
- `audio/wav`

**Документы:**
- `application/pdf`
- `text/plain`

**Архивы:**
- `application/zip`
- `application/gzip`

**ЗАПРЕЩЕНО**: `application/x-executable`, `application/x-msdownload`, `text/html`, `application/javascript`, `image/svg+xml` (обрабатывается отдельно через sanitization).

---

## Image Processing Pipeline

```
1. MIME detection (infer)
    ↓
2. EXIF stripping (img-parts)
    ↓
3. Dimensions validation
    ↓
4. Resize / thumbnail generation (image crate)
    ↓
5. WebP conversion (для avatars, emojis, icons)
    ↓
6. Upload processed files to MinIO
```

### EXIF Stripping

EXIF данные содержат GPS координаты, модель камеры, дату — приватная информация. Удаляем всегда.

```rust
use img_parts::{ImageICC, Jpeg, Png, DynImage};
use img_parts::jpeg::JpegSegment;

fn strip_exif(bytes: &[u8], mime: &str) -> Result<Vec<u8>, Error> {
    match mime {
        "image/jpeg" => {
            let mut jpeg = Jpeg::from_bytes(bytes.into())?;
            // Удалить все EXIF/APP1 сегменты
            jpeg.segments_mut().retain(|seg| {
                seg.marker() != img_parts::jpeg::markers::APP1
            });
            let mut output = Vec::new();
            jpeg.encoder().write_to(&mut output)?;
            Ok(output)
        }
        "image/png" => {
            let mut png = Png::from_bytes(bytes.into())?;
            // PNG: удалить eXIf chunk
            png.chunks_mut().retain(|chunk| {
                chunk.chunk_type() != b"eXIf"
            });
            let mut output = Vec::new();
            png.encoder().write_to(&mut output)?;
            Ok(output)
        }
        _ => Ok(bytes.to_vec()),
    }
}
```

`img-parts` — zero-copy, не декодирует изображение полностью. Быстрее чем декод → энкод через `image` crate.

### Resize и thumbnail generation

```rust
use image::{DynamicImage, imageops::FilterType};

fn generate_variants(img: &DynamicImage, target_type: &str) -> Vec<(String, DynamicImage)> {
    match target_type {
        "avatar" => vec![
            ("128".into(),  img.resize(128, 128, FilterType::Lanczos3)),
            ("256".into(),  img.resize(256, 256, FilterType::Lanczos3)),
            ("512".into(),  img.resize(512, 512, FilterType::Lanczos3)),
            ("1024".into(), img.resize(1024, 1024, FilterType::Lanczos3)),
        ],
        "emoji" => vec![
            ("128".into(), img.resize(128, 128, FilterType::Lanczos3)),
        ],
        "attachment" => {
            let mut variants = vec![];
            // Thumbnail для preview (если изображение)
            if img.width() > 400 || img.height() > 300 {
                variants.push(("thumbnail".into(), img.thumbnail(400, 300)));
            }
            variants
        }
        _ => vec![],
    }
}
```

**FilterType::Lanczos3** — лучшее качество для downscale. Для thumbnails допустим `Triangle` (быстрее).

### WebP conversion

Аватары, emoji, иконки конвертируются в WebP для экономии bandwidth:

```rust
use image::codecs::webp::WebPEncoder;
use image::ColorType;

fn encode_webp(img: &DynamicImage, quality: u8) -> Vec<u8> {
    let mut buf = Vec::new();
    let encoder = WebPEncoder::new_lossless(&mut buf);
    // Для фото: lossy с quality 80-90
    // Для emoji/icons: lossless
    img.write_with_encoder(encoder)?;
    buf
}
```

---

## Размеры и лимиты

### Attachment лимиты

| Уровень | Макс. размер файла |
|---------|-------------------|
| По умолчанию | 25 MiB |
| Server Boost Level 2 | 50 MiB |
| Server Boost Level 3 | 100 MiB |

### Avatar размеры

| Размер | URL suffix | Назначение |
|--------|-----------|------------|
| 128×128 | `?size=128` | Список участников, mentions |
| 256×256 | `?size=256` | Профиль (мобильный) |
| 512×512 | `?size=512` | Профиль (десктоп) |
| 1024×1024 | `?size=1024` | Полноразмерный просмотр |

**Допустимые query sizes**: 16, 32, 64, 128, 256, 512, 1024, 2048, 4096 (степени двойки).

### Emoji размеры

| Ограничение | Значение |
|-------------|----------|
| Размер файла | max 256 KiB |
| Dimensions | 128×128 px (автоматический resize) |
| Формат | PNG, GIF (animated), WebP |

### Общие лимиты

| Ограничение | Значение |
|-------------|----------|
| Max attachments per message | 10 |
| Max total size per request | 25 MiB |
| Attachment description (alt text) | max 1024 символов |
| Original filename length | max 256 символов |
| Avatar upload | max 10 MiB |
| Banner upload | max 10 MiB |
| Max image dimensions | 10000×10000 px |
| Min image dimensions (avatar) | 128×128 px |

---

## CDN

### URL формат

```
https://cdn.example.com/avatars/{user_id}/{avatar_hash}.webp?size=256
https://cdn.example.com/emojis/{emoji_id}.webp
https://cdn.example.com/icons/{guild_id}/{icon_hash}.webp?size=128
https://cdn.example.com/banners/{guild_id}/{banner_hash}.webp?size=480
https://cdn.example.com/attachments/{channel_id}/{message_id}/{filename}
```

### HTTP headers

```
Cache-Control: public, max-age=31536000, immutable
Content-Type: image/webp
Content-Disposition: inline
X-Content-Type-Options: nosniff
Access-Control-Allow-Origin: *
```

Для скачивания файлов (не изображений):
```
Content-Disposition: attachment; filename="document.pdf"
```

### CDN домен

- **Отдельный домен** (`cdn.example.com`) — без cookies основного домена
- Cookieless → уменьшает размер HTTP запросов
- Отдельный CORS origin → изоляция
- Можно разместить за Cloudflare/CDN edge

### Image proxy

Для embed-изображений (внешние URL) — проксирование через наш CDN:

```
https://cdn.example.com/proxy?url=https://external.com/image.jpg
```

**Зачем**:
- Скрывает IP клиента от внешнего сервера
- Кеширует изображения
- Валидирует content-type
- SSRF protection (запрет приватных IP)

---

## Антивирус (ClamAV)

### Интеграция

```rust
use clamav_client::tokio::ClamdClient;

async fn scan_file(bytes: &[u8], clamav_host: &str, clamav_port: u16) -> Result<(), Error> {
    let client = ClamdClient::builder()
        .tcp_host(clamav_host)
        .tcp_port(clamav_port)
        .build()?;

    let result = client.scan_bytes(bytes).await?;

    if result.is_infected() {
        tracing::warn!(
            virus = %result.virus_name().unwrap_or("unknown"),
            "File infected, rejecting upload"
        );
        return Err(Error::ScanFailed);
    }

    Ok(())
}
```

### Pipeline

1. Файл загружен в temp bucket
2. **Перед** любой обработкой — скан ClamAV
3. Если infected → удалить из temp, вернуть ошибку
4. Если clean → продолжить processing pipeline

### ClamAV в Kubernetes

```yaml
# Отдельный pod/sidecar
containers:
  - name: clamav
    image: clamav/clamav:latest
    ports:
      - containerPort: 3310
    resources:
      requests:
        memory: "512Mi"
        cpu: "500m"
```

Обновление сигнатур: автоматически через `freshclam` (встроен в образ).

---

## SVG Sanitization

SVG файлы содержат потенциальные XSS-векторы: `<script>`, `<foreignObject>`, `onload`, `onerror`, event handlers, external references.

```rust
use ammonia::Builder;

fn sanitize_svg(svg_content: &str) -> Result<String, Error> {
    let sanitized = Builder::new()
        .tags(hashset![
            "svg", "g", "path", "circle", "rect", "ellipse", "line",
            "polyline", "polygon", "text", "tspan", "defs", "use",
            "clipPath", "mask", "pattern", "linearGradient",
            "radialGradient", "stop", "symbol", "title", "desc"
        ])
        .rm_tags(&["script", "foreignObject", "iframe", "object", "embed"])
        .generic_attributes(hashset![
            "id", "class", "style", "fill", "stroke", "stroke-width",
            "transform", "d", "cx", "cy", "r", "rx", "ry",
            "x", "y", "width", "height", "viewBox", "xmlns",
            "opacity", "font-size", "font-family", "text-anchor"
        ])
        .clean(svg_content)
        .to_string();

    // Дополнительно: проверить отсутствие external references
    if sanitized.contains("xlink:href=\"http") || sanitized.contains("url(http") {
        return Err(Error::SvgUnsafe);
    }

    Ok(sanitized)
}
```

---

## API Endpoints

### POST /media/upload/presign

Получить presigned URL для загрузки.

**Права**: JWT аутентификация

**Request:**

```json
{
    "filename": "photo.jpg",
    "content_type": "image/jpeg",
    "size": 2048576,
    "target": "attachment"
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `filename` | string | Имя файла (max 256 символов) |
| `content_type` | string | MIME тип (из whitelist) |
| `size` | integer | Размер в bytes |
| `target` | string | `attachment`, `avatar`, `emoji`, `banner`, `icon` |

**Валидация:**
- `content_type` в whitelist
- `size` ≤ лимит для target
- `filename`: sanitized, без path traversal символов

**Response (200):**

```json
{
    "upload_id": "1234567890123456",
    "upload_url": "https://s3.example.com/temp-uploads/uploads/1234567890123456/photo.jpg?X-Amz-...",
    "expires_at": "2026-02-21T12:15:00Z"
}
```

---

### POST /media/upload/confirm

Подтвердить загрузку, запустить обработку.

**Request:**

```json
{
    "upload_id": "1234567890123456"
}
```

**Логика:**
1. Получить upload session из Redis
2. Проверить что файл существует в temp bucket
3. Скачать файл → ClamAV scan
4. Проверить magic bytes (infer)
5. Image processing pipeline (если изображение)
6. Переместить в постоянный bucket
7. Удалить upload session

**Response (200):**

```json
{
    "id": "1234567890123457",
    "filename": "photo.jpg",
    "content_type": "image/jpeg",
    "size": 2048576,
    "url": "https://cdn.example.com/attachments/ch_id/msg_id/1234567890123457/photo.jpg",
    "proxy_url": "https://cdn.example.com/proxy/attachments/ch_id/msg_id/1234567890123457/photo.jpg",
    "width": 1920,
    "height": 1080
}
```

---

### PUT /users/@me/avatar

Загрузить аватар (multipart/form-data).

**Максимальный размер**: 10 MiB

**Допустимые форматы**: JPEG, PNG, GIF (animated), WebP

**Логика:**
1. Принять multipart upload
2. Magic bytes validation
3. EXIF strip
4. ClamAV scan
5. Resize до всех вариантов (128, 256, 512, 1024)
6. Конвертировать в WebP
7. Upload в `avatars/{user_id}/{hash}.webp`
8. Удалить старый аватар (если есть)
9. NATS event: `user.avatar.updated`

**Response (200):**

```json
{
    "avatar": "abc123def456",
    "avatar_url": "https://cdn.example.com/avatars/1111111111/abc123def456.webp"
}
```

---

### DELETE /users/@me/avatar

Удалить аватар (вернуть default).

**Response:** 204 No Content

---

### POST /guilds/:guild_id/emojis

Загрузить custom emoji.

**Права**: `MANAGE_GUILD_EXPRESSIONS`

**Максимальный размер**: 256 KiB

**Допустимые форматы**: PNG, GIF (animated), WebP

**Request (multipart):**

| Поле | Тип | Описание |
|------|-----|----------|
| `name` | string | Имя emoji (2–32 символа, alphanumeric + underscore) |
| `image` | file | Файл изображения |

**Логика:**
1. Magic bytes validation
2. ClamAV scan
3. Resize до 128×128
4. Upload в `emojis/{guild_id}/{emoji_id}.webp`

**Response (201):**

```json
{
    "id": "1234567890123458",
    "name": "custom_emoji",
    "animated": false,
    "url": "https://cdn.example.com/emojis/1234567890123458.webp"
}
```

---

### GET /media/proxy

Проксирование внешних изображений (для embed preview).

**Query**: `?url=https://external.com/image.jpg`

**Безопасность:**
- Только HTTPS URL-ы
- SSRF protection: запрет приватных IP (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.1)
- DNS rebinding protection
- Whitelist Content-Type (только image/*)
- Max size: 10 MiB
- Таймаут: 5 секунд
- Кеширование: Redis (TTL 1 час)

**Response**: проксированное изображение с заголовками:
```
Content-Type: image/jpeg
Cache-Control: public, max-age=3600
X-Content-Type-Options: nosniff
```

---

## NATS Events

### Публикуемые

| Subject | Payload | Описание |
|---------|---------|----------|
| `media.uploaded` | `{ id, url, content_type, size, uploader_id }` | Файл загружен |
| `media.deleted` | `{ id, url }` | Файл удалён |
| `media.avatar.updated` | `{ user_id, avatar_hash }` | Аватар обновлён |
| `media.emoji.created` | `{ guild_id, emoji_id, name, animated }` | Emoji создано |
| `media.emoji.deleted` | `{ guild_id, emoji_id }` | Emoji удалено |

### Подписки

| Subject | Источник | Действие |
|---------|----------|----------|
| `message.deleted` | Message Service | Удалить вложения сообщения |
| `guild.deleted` | Guild Service | Удалить все медиа гильдии (emojis, icon, banner) |
| `user.deleted` | User Service | Удалить аватар и баннер пользователя |

---

## Redis кеш

| Ключ | Данные | TTL | Назначение |
|------|--------|-----|------------|
| `upload:{upload_id}` | JSON upload session | 15 мин | Tracking presigned uploads |
| `proxy:{url_hash}` | Cached image bytes/metadata | 1 час | Image proxy cache |
| `avatar:{user_id}` | avatar hash | 10 мин | Quick avatar URL resolve |

---

## Rate Limiting

| Endpoint | Лимит | Окно |
|----------|-------|------|
| POST /media/upload/presign | 10 запросов | 1 минута |
| POST /media/upload/confirm | 10 запросов | 1 минута |
| PUT /users/@me/avatar | 2 запроса | 10 минут |
| POST /guilds/:id/emojis | 5 запросов | 1 минута |
| GET /media/proxy | 30 запросов | 1 минута |
| DELETE (любые медиа) | 10 запросов | 1 минута |

---

## Cron Jobs

| Задача | Расписание | Описание |
|--------|-----------|----------|
| Cleanup temp-uploads | Каждые 30 мин | Удалить файлы из temp-uploads старше 1 часа (MinIO lifecycle) |
| Cleanup orphaned files | Ежедневно | Найти файлы в S3 без записи в БД → удалить |
| ClamAV signature update | Автоматически | freshclam в ClamAV контейнере |
| Storage metrics | Каждые 5 мин | Собрать размеры bucket-ов для мониторинга |

---

## Мониторинг

### Метрики

| Метрика | Тип | Описание |
|---------|-----|----------|
| `media_uploads_total{target}` | counter | Загрузки по типу (attachment/avatar/emoji) |
| `media_upload_size_bytes` | histogram | Размер загруженных файлов |
| `media_upload_duration_seconds` | histogram | Время обработки загрузки |
| `media_scan_duration_seconds` | histogram | Время ClamAV сканирования |
| `media_scan_infected_total` | counter | Заражённых файлов |
| `media_processing_duration_seconds` | histogram | Время image processing |
| `media_proxy_requests_total` | counter | Запросов к image proxy |
| `media_proxy_cache_hit_ratio` | gauge | Cache hit rate proxy |
| `media_storage_bytes{bucket}` | gauge | Объём данных по bucket-ам |
| `media_presign_requests_total` | counter | Запросов на presigned URL |
| `media_errors_total{type}` | counter | Ошибки по типу |

### Алерты

| Условие | Severity | Описание |
|---------|----------|----------|
| `media_scan_infected_total` rate > 5/hour | warning | Повышенное кол-во заражённых файлов |
| `media_scan_duration_seconds{p99}` > 10s | warning | ClamAV медленный |
| `media_storage_bytes` > 80% capacity | warning | Хранилище заполняется |
| `media_upload_duration_seconds{p99}` > 30s | critical | Критическая деградация upload |

---

## Безопасность

### Чеклист

- [ ] Все endpoints требуют JWT аутентификацию
- [ ] MIME проверка через magic bytes (`infer` crate), не по расширению
- [ ] EXIF данные удаляются из всех изображений
- [ ] ClamAV сканирование **до** любой обработки
- [ ] SVG санитизируется (`ammonia`) — удаление script, foreignObject, event handlers
- [ ] Presigned URLs с ограниченным TTL (15 мин upload, 1 час download)
- [ ] Имена файлов: Snowflake ID, не пользовательский ввод
- [ ] Path traversal prevention: запрет `../` в путях
- [ ] SSRF protection для image proxy (приватные IP, DNS rebinding)
- [ ] CDN на отдельном домене (без cookies)
- [ ] `Content-Disposition: attachment` для не-изображений
- [ ] `X-Content-Type-Options: nosniff` на всех ответах CDN
- [ ] Max dimensions для изображений (10000×10000) — защита от decompression bomb
- [ ] Rate limiting на все upload endpoints
- [ ] Файлы не исполняются: no execute permissions, `Content-Type` всегда задан
- [ ] Whitelist MIME типов, не blacklist
- [ ] Логирование: upload/delete операции с user_id, file_id, размером
- [ ] Orphaned files cleanup (файлы без записи в БД)
