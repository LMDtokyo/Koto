//! Короткие сообщения для UI вместо сырых `HTTP … — {"error":…}`.

use reqwest::Error as ReqwestError;
use serde::Serialize;
use serde_json::Value;

#[derive(Clone, Copy, Debug)]
pub enum ApiContext {
    AuthRegister,
    AuthRestore,
    AuthRefresh,
    UserProfile,
    UploadKeys,
    Conversations,
    SearchUsers,
    SearchConversations,
    SearchMessagesMeta,
    Presence,
    Messages,
    SendMessage,
    CreateChat,
    GatewayHealth,
}

/// Структурированная ошибка IPC: фронтенд читает `status` (401 и т.д.), `message` — для UI.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct KotoApiError {
    pub status: u16,
    pub message: String,
}

impl std::fmt::Display for KotoApiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

/// HTTP-ответ с кодом (REST или handshake WS).
pub fn api_http_error(ctx: ApiContext, status: u16, body: &str) -> KotoApiError {
    KotoApiError {
        status,
        message: humanize_http(ctx, status, body),
    }
}

pub fn api_json_error(ctx: ApiContext, err: &serde_json::Error) -> KotoApiError {
    KotoApiError {
        status: 0,
        message: humanize_json_decode(ctx, err),
    }
}

pub fn api_transport_error(msg: impl Into<String>) -> KotoApiError {
    KotoApiError {
        status: 0,
        message: msg.into(),
    }
}

pub fn map_reqwest(err: ReqwestError) -> String {
    if err.is_timeout() {
        return "Сервер не ответил вовремя. Проверьте сеть и попробуйте ещё раз.".to_string();
    }
    if err.is_connect() {
        return "Не удалось подключиться к Koto. Запустите шлюз (например docker compose) и проверьте адрес и порт REST в настройках.".to_string();
    }
    "Не удалось связаться с сервером. Проверьте сеть и адрес Koto.".to_string()
}

fn json_error_slug(body: &str) -> Option<String> {
    let v: Value = serde_json::from_str(body.trim()).ok()?;
    v.get("error")?.as_str().map(str::to_string)
}

fn hint_from_body(body: &str) -> String {
    json_error_slug(body).unwrap_or_else(|| body.trim().chars().take(120).collect())
}

/// Ошибка HTTP после ответа (есть код и тело).
pub fn humanize_http(ctx: ApiContext, status: u16, body: &str) -> String {
    let slug = json_error_slug(body);
    let slug_l = slug.as_deref().unwrap_or("").to_ascii_lowercase();
    let body_l = body.to_ascii_lowercase();

    match status {
        400 => {
            if slug_l.contains("hex") || slug_l.contains("identity_key") {
                return "Неверный формат ключей в запросе. Обновите приложение.".to_string();
            }
            if slug_l.contains("json") || slug_l.contains("invalid") {
                return "Неверный запрос к серверу. Обновите приложение.".to_string();
            }
            if matches!(ctx, ApiContext::AuthRegister | ApiContext::AuthRestore) {
                return format!(
                    "Сервер отклонил данные для входа: {}",
                    hint_from_body(body)
                );
            }
            format!("Некорректный запрос ({}).", hint_from_body(body))
        }
        401 => match ctx {
            ApiContext::AuthRefresh => {
                "Сессия устарела. Выйдите и войдите снова по фразе восстановления. (HTTP 401)"
                    .to_string()
            }
            _ => "Сессия истекла или токен недействителен. Войдите снова. (HTTP 401)".to_string(),
        },
        403 => "Доступ запрещён.".to_string(),
        404 => match ctx {
            ApiContext::AuthRestore => {
                "Аккаунт с таким ключом на сервере не найден. Проверьте фразу или создайте новый Koto ID."
                    .to_string()
            }
            ApiContext::Messages | ApiContext::SendMessage => {
                "Чат или сообщение не найдены (возможно, удалили или неверная ссылка).".to_string()
            }
            ApiContext::CreateChat => "Не удалось создать чат: пользователь не найден.".to_string(),
            _ => format!("Не найдено: {}.", hint_from_body(body)),
        },
        409 => {
            if slug_l.contains("already exists") || body_l.contains("already exists") {
                return "Этот Koto ID уже зарегистрирован на сервере. Если это ваш аккаунт, используйте «У меня уже есть аккаунт»."
                    .to_string();
            }
            format!("Конфликт данных: {}.", hint_from_body(body))
        }
        429 => "Слишком много запросов. Подождите немного и попробуйте снова.".to_string(),
        502 | 503 | 504 => {
            "Сервер Koto временно недоступен или перегружен. Попробуйте позже.".to_string()
        }
        500..=599 => {
            if body_l.contains("relation") && body_l.contains("does not exist") {
                return "На сервере не применены миграции базы (нет таблицы). В корне проекта выполните: make migrate"
                    .to_string();
            }
            if slug_l.contains("create session") || body_l.contains("create session") {
                return "Ошибка при создании сессии на сервере. Проверьте логи auth и миграции Postgres."
                    .to_string();
            }
            "На стороне сервера произошла ошибка. Попробуйте позже или проверьте логи сервисов.".to_string()
        }
        _ => match ctx {
            ApiContext::AuthRegister | ApiContext::AuthRestore => {
                format!("Вход не удалён (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::AuthRefresh => {
                format!("Не удалось обновить сессию (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::UserProfile => {
                format!("Не удалось сохранить имя (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::UploadKeys => {
                format!("Не удалось загрузить ключи (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::Conversations => {
                format!("Не удалось загрузить список чатов (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::SearchUsers => {
                format!("Не удалось найти пользователей (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::SearchConversations => {
                format!("Не удалось найти чаты (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::SearchMessagesMeta => {
                format!("Не удалось выполнить поиск сообщений (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::Presence => {
                format!("Не удалось получить онлайн-статусы (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::Messages => {
                format!("Не удалось загрузить сообщения (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::SendMessage => {
                format!("Не удалось отправить сообщение (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::CreateChat => {
                format!("Не удалось создать чат (код {}). {}", status, hint_from_body(body))
            }
            ApiContext::GatewayHealth => {
                format!("Проверка шлюза не прошла (код {}). {}", status, hint_from_body(body))
            }
        },
    }
}

pub fn humanize_json_decode(ctx: ApiContext, _err: &serde_json::Error) -> String {
    match ctx {
        ApiContext::AuthRegister | ApiContext::AuthRestore | ApiContext::AuthRefresh => {
            "Сервер вернул неожиданный ответ при входе. Обновите клиент.".to_string()
        }
        ApiContext::Conversations => {
            "Сервер вернул неожиданный список чатов. Обновите клиент.".to_string()
        }
        ApiContext::SearchUsers => {
            "Сервер вернул неожиданный ответ поиска пользователей. Обновите клиент.".to_string()
        }
        ApiContext::SearchConversations => {
            "Сервер вернул неожиданный ответ поиска чатов. Обновите клиент.".to_string()
        }
        ApiContext::SearchMessagesMeta => {
            "Сервер вернул неожиданный ответ поиска сообщений. Обновите клиент.".to_string()
        }
        ApiContext::Presence => {
            "Сервер вернул неожиданный ответ статусов. Обновите клиент.".to_string()
        }
        ApiContext::Messages => {
            "Сервер вернул неожиданный формат сообщений. Обновите клиент.".to_string()
        }
        ApiContext::SendMessage => {
            "Сервер вернул неожиданный ответ после отправки. Обновите клиент.".to_string()
        }
        ApiContext::CreateChat => {
            "Сервер вернул неожиданный ответ при создании чата. Обновите клиент.".to_string()
        }
        ApiContext::UserProfile | ApiContext::UploadKeys | ApiContext::GatewayHealth => {
            "Сервер вернул неожиданный ответ. Обновите клиент.".to_string()
        }
    }
}

pub fn humanize_seed_crypto(err_text: &str) -> String {
    let l = err_text.to_ascii_lowercase();
    if l.contains("mnemonic")
        || l.contains("checksum")
        || l.contains("word")
        || l.contains("entropy")
        || l.contains("invalid")
    {
        return "Фраза неверна или не в формате BIP39 (12 английских слов из словаря).".to_string();
    }
    "Не удалось подготовить ключи из фразы. Проверьте написание слов.".to_string()
}
