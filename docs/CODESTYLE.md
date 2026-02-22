# Код-стайл

## Общие правила

- Линтер: **Clippy** (`cargo clippy -- -D warnings`)
- Форматтер: **rustfmt** (`cargo fmt`)
- Edition: **2024**
- Никаких `unsafe` без ревью и комментария ПОЧЕМУ
- Никаких `unwrap()` / `expect()` в продакшен коде — обрабатывай ошибки через `?` и `Result`
- Никаких `clone()` без необходимости — предпочитай ссылки

## Именование

| Что | Конвенция | Пример |
|-----|----------|--------|
| Переменные, функции | snake_case | `get_user_by_id`, `message_count` |
| Типы, структуры, enums, traits | PascalCase | `UserService`, `GuildMember` |
| Константы | UPPER_SNAKE_CASE | `MAX_MESSAGE_LENGTH`, `DEFAULT_AVATAR_URL` |
| Файлы/модули | snake_case | `auth_service.rs`, `user_handler.rs` |
| Папки | snake_case | `rate_limit/`, `nats_client/` |
| Переменные окружения | UPPER_SNAKE_CASE | `DATABASE_URL`, `JWT_SECRET` |
| Crates (пакеты) | kebab-case в Cargo.toml | `my-project-auth`, `my-project-db` |

## Структура файлов

```rust
// 1. Внешние crates
use axum::{Router, Json, extract::State};
use serde::{Serialize, Deserialize};
use sqlx::PgPool;

// 2. Внутренние модули
use crate::db::UserRepository;
use crate::errors::AppError;

// 3. Константы
const MAX_RETRIES: u32 = 3;

// 4. Типы
#[derive(Debug, Deserialize)]
pub struct CreateUserInput {
    pub username: String,
    pub email: String,
}

// 5. Реализация
pub async fn create_user(
    State(pool): State<PgPool>,
    Json(input): Json<CreateUserInput>,
) -> Result<Json<User>, AppError> {
    // ...
}
```

## Обработка ошибок

- Используй `thiserror` для кастомных типов ошибок в библиотечных crates
- Используй `anyhow` только в бинарниках / точках входа
- Никогда `.unwrap()` — используй `?` оператор
- Логируй через `tracing` crate

```rust
// Хорошо — кастомные ошибки
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("User not found: {0}")]
    NotFound(String),

    #[error("Validation failed: {0}")]
    Validation(String),

    #[error("Database error")]
    Database(#[from] sqlx::Error),

    #[error("Unauthorized")]
    Unauthorized,
}

// Использование
pub async fn get_user(id: i64) -> Result<User, AppError> {
    let user = sqlx::query_as!(User, "SELECT * FROM users WHERE id = $1", id)
        .fetch_optional(&pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("User {id}")))?;

    Ok(user)
}

// Плохо
pub async fn get_user(id: i64) -> User {
    sqlx::query_as!(User, "SELECT * FROM users WHERE id = $1", id)
        .fetch_one(&pool)
        .await
        .unwrap() // ПАНИКА! Никогда так не делай
}
```

## Структуры и сериализация

```rust
// Входные данные (десериализация)
#[derive(Debug, Deserialize, Validate)]
pub struct CreateMessageInput {
    #[validate(length(min = 1, max = 4000))]
    pub content: String,
    pub reply_to: Option<i64>,
}

// Выходные данные (сериализация)
#[derive(Debug, Serialize)]
pub struct MessageResponse {
    pub id: i64,
    pub content: String,
    pub author_id: i64,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

// Модель БД
#[derive(Debug, sqlx::FromRow)]
pub struct Message {
    pub id: i64,
    pub channel_id: i64,
    pub author_id: i64,
    pub content: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
}
```

## Правила для функций

- Максимум **50 строк** на функцию — если больше, разбей
- Максимум **300 строк** на файл — если больше, выноси в модуль
- Максимум **3-4 параметра** — если больше, используй struct
- Всегда указывай типы возврата
- Предпочитай `impl Trait` для параметров вместо dyn Trait

```rust
// Хорошо — struct для параметров
pub struct SendMessageParams {
    pub channel_id: i64,
    pub author_id: i64,
    pub content: String,
    pub reply_to: Option<i64>,
}

pub async fn send_message(pool: &PgPool, params: SendMessageParams) -> Result<Message, AppError> {
    // ...
}

// Плохо — слишком много параметров
pub async fn send_message(
    pool: &PgPool,
    channel_id: i64,
    author_id: i64,
    content: String,
    reply_to: Option<i64>,
    attachments: Vec<String>,
) -> Result<Message, AppError> { ... }
```

## SQL запросы (SQLx)

- Используй `sqlx::query!` и `sqlx::query_as!` — compile-time проверка SQL
- Никогда не форматируй SQL строки через `format!`
- Миграции через `sqlx migrate`

```rust
// Хорошо — compile-time checked
let user = sqlx::query_as!(
    User,
    "SELECT id, username, email FROM users WHERE id = $1",
    user_id
)
.fetch_optional(&pool)
.await?;

// Плохо — SQL injection!
let query = format!("SELECT * FROM users WHERE id = '{}'", user_id);
sqlx::query(&query).fetch_one(&pool).await?;
```

## Модули и проект

```
service/
├── src/
│   ├── main.rs           # Точка входа, настройка Axum
│   ├── lib.rs            # Библиотечный crate (опционально)
│   ├── config.rs         # Конфигурация (env)
│   ├── errors.rs         # AppError
│   ├── routes/
│   │   ├── mod.rs        # Router
│   │   ├── users.rs      # User endpoints
│   │   └── health.rs     # Health check
│   ├── handlers/         # Бизнес-логика
│   ├── models/           # Структуры БД
│   ├── middleware/        # Auth, rate limit
│   └── events/           # NATS handlers
├── tests/
│   ├── common/mod.rs     # Test helpers
│   └── api_tests.rs      # Integration tests
├── migrations/           # SQL миграции
├── Cargo.toml
└── Dockerfile
```

## Комментарии

- Не комментируй очевидное
- Комментируй ПОЧЕМУ, а не ЧТО
- `///` для документации публичного API
- `//` для внутренних комментариев
- TODO с тикетом: `// TODO(PROJ-123): migrate to ScyllaDB`

```rust
// Плохо
// Получаем пользователя
let user = get_user(id).await?;

// Хорошо
// Snowflake ID содержит timestamp — используем для сортировки
// вместо отдельного поля created_at
let messages = get_messages_after(last_snowflake_id).await?;
```

## Frontend (TypeScript/React)

Фронтенд остаётся на TypeScript. Правила:
- **Файлы**: kebab-case (`message-list.tsx`)
- **Компоненты**: PascalCase (`MessageList`)
- **Функции/переменные**: camelCase
- **Линтер**: Biome
- **1 компонент = 1 файл**
- Никаких `any` — строгая типизация
- Никаких `var` — только `const` / `let`
