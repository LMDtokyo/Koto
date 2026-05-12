const invoke = window.__TAURI__?.core?.invoke?.bind(window.__TAURI__.core);

export interface AppConfig {
  baseHost: string;
  baseTls: boolean;
  restPort: number;
  wsPort: number;
  restBaseUrl: string;
  wsBaseUrl: string;
}

export async function getAppConfig(): Promise<AppConfig> {
  if (!invoke) throw new Error("Tauri invoke unavailable");
  return invoke<AppConfig>("get_app_config");
}
