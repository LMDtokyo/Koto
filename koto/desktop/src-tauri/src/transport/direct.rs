//! Прямое подключение к нашему gateway по HTTPS+WSS. Текущий default.

use async_trait::async_trait;
use std::time::Duration;

use super::{Tunnel, TransportEndpoint, TransportError};

pub struct DirectTunnel {
    endpoint: TransportEndpoint,
}

impl DirectTunnel {
    pub fn new(endpoint: TransportEndpoint) -> Self {
        Self { endpoint }
    }
}

#[async_trait]
impl Tunnel for DirectTunnel {
    fn endpoint(&self) -> &TransportEndpoint {
        &self.endpoint
    }

    async fn probe(&self) -> Result<(), TransportError> {
        let url = format!("{}/health", self.endpoint.rest_base_url.trim_end_matches('/'));
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .map_err(|e| TransportError::Unreachable(e.to_string()))?;
        let response = client
            .get(&url)
            .send()
            .await
            .map_err(|e| {
                if e.is_timeout() {
                    TransportError::Timeout
                } else {
                    TransportError::Unreachable(e.to_string())
                }
            })?;
        if !response.status().is_success() {
            return Err(TransportError::Unreachable(format!(
                "HTTP {}",
                response.status().as_u16()
            )));
        }
        Ok(())
    }
}
