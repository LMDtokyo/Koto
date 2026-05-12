//! DNS-over-HTTPS (RFC 8484) resolver. Используется когда системному DNS
//! нельзя доверять (DNS-poisoning Роскомнадзора и подобных).
//!
//! Hard-coded fallback на несколько провайдеров — нельзя заблокировать все
//! одновременно без блокировки всего HTTPS.

use serde::Deserialize;
use std::time::Duration;

use crate::koto_errors::KotoApiError;

/// Список DoH-провайдеров, в порядке приоритета. JSON API.
const DOH_PROVIDERS: &[&str] = &[
    "https://cloudflare-dns.com/dns-query",
    "https://dns.google/resolve",
    "https://dns.quad9.net:5053/dns-query",
];

#[derive(Debug, Deserialize)]
struct DohAnswer {
    #[serde(rename = "Answer")]
    answer: Option<Vec<DohRecord>>,
}

#[derive(Debug, Deserialize)]
struct DohRecord {
    #[serde(rename = "type")]
    record_type: u16,
    data: String,
}

#[derive(Debug, serde::Serialize)]
pub struct DohResolveResult {
    pub host: String,
    pub a: Vec<String>,
    pub aaaa: Vec<String>,
    pub provider: String,
}

/// Резолвит host в IPv4/IPv6 через первый доступный DoH-провайдер.
pub async fn resolve(host: &str) -> Result<DohResolveResult, KotoApiError> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(6))
        .build()
        .map_err(|e| KotoApiError {
            status: 0,
            message: format!("DoH client init: {e}"),
        })?;

    let mut last_err = String::new();
    for provider in DOH_PROVIDERS {
        match query_provider(&client, provider, host).await {
            Ok(mut result) => {
                result.provider = (*provider).to_string();
                return Ok(result);
            }
            Err(e) => {
                last_err = e;
                continue;
            }
        }
    }
    Err(KotoApiError {
        status: 0,
        message: format!("DoH: все провайдеры недоступны. Последняя ошибка: {last_err}"),
    })
}

async fn query_provider(
    client: &reqwest::Client,
    provider: &str,
    host: &str,
) -> Result<DohResolveResult, String> {
    let mut a = Vec::new();
    let mut aaaa = Vec::new();
    for (qtype, key) in [(1u16, "A"), (28u16, "AAAA")] {
        let url = format!("{provider}?name={host}&type={key}");
        let response = client
            .get(&url)
            .header("Accept", "application/dns-json")
            .send()
            .await
            .map_err(|e| e.to_string())?;
        if !response.status().is_success() {
            return Err(format!("HTTP {}", response.status().as_u16()));
        }
        let parsed: DohAnswer = response.json().await.map_err(|e| e.to_string())?;
        if let Some(records) = parsed.answer {
            for record in records {
                if record.record_type == qtype {
                    if qtype == 1 {
                        a.push(record.data);
                    } else {
                        aaaa.push(record.data);
                    }
                }
            }
        }
    }
    Ok(DohResolveResult {
        host: host.to_string(),
        a,
        aaaa,
        provider: String::new(),
    })
}

/// Tauri-команда: front может вызывать для проверки или предзагрузки.
#[tauri::command]
pub async fn doh_resolve(host: String) -> Result<DohResolveResult, KotoApiError> {
    resolve(&host).await
}
