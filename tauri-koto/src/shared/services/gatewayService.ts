const invoke = window.__TAURI__?.core?.invoke?.bind(window.__TAURI__.core);

/** Тело ответа `GET /health` (JSON строка от gateway). */
export async function gatewayHealth(): Promise<string> {
  if (!invoke) throw new Error("Tauri invoke unavailable");
  return invoke<string>("gateway_health");
}
