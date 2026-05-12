/**
 * Settings → Соединение. Подгружает список транспортов из Rust (transport_list),
 * рендерит, проверяет каждый через `transport_probe`, прогоняет
 * auto-fallback (`transport_select_active`) и подсвечивает победителя.
 * Выбранный режим сохраняется в localStorage; last-good кэшируется в Rust.
 */

const invoke = window.__TAURI__?.core?.invoke?.bind(window.__TAURI__.core);

const LS_CONN = "koto.connection.mode";

type TransportKind = "direct" | "cloudflare" | "reality" | "hysteria2" | "tor";

interface TransportEndpointDto {
  kind: TransportKind;
  rest_base_url: string;
  ws_base_url: string;
  order: number;
  label: string;
}

interface LastGoodDto {
  kind: TransportKind | null;
  label: string;
  picked_at_unix: number;
}

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function readMode(): string {
  try {
    return localStorage.getItem(LS_CONN) || "auto";
  } catch {
    return "auto";
  }
}

function writeMode(mode: string): void {
  try {
    localStorage.setItem(LS_CONN, mode);
  } catch {
    /* ignore */
  }
}

function setStatus(text: string, kind: "" | "ok" | "error" = ""): void {
  const el = $("connection-test-result");
  if (!el) return;
  el.textContent = text;
  el.classList.remove("settings-overlay__feedback--ok", "settings-overlay__feedback--error");
  if (kind === "ok") el.classList.add("settings-overlay__feedback--ok");
  if (kind === "error") el.classList.add("settings-overlay__feedback--error");
}

async function loadEndpoints(): Promise<TransportEndpointDto[]> {
  if (!invoke) return [];
  try {
    return (await invoke<TransportEndpointDto[]>("transport_list")) || [];
  } catch (e) {
    console.warn("transport_list failed", e);
    return [];
  }
}

function renderList(endpoints: TransportEndpointDto[]): void {
  const list = $("connection-endpoints-list");
  if (!list) return;
  list.innerHTML = "";
  if (!endpoints.length) {
    const empty = document.createElement("p");
    empty.className = "settings-overlay__empty-line";
    empty.textContent = "Список транспортов пока пуст.";
    list.appendChild(empty);
    return;
  }
  for (const ep of endpoints) {
    const li = document.createElement("li");
    li.className = "settings-overlay__list-row";
    li.dataset.transportKind = ep.kind;
    li.innerHTML = `
      <div class="settings-overlay__list-text">
        <span class="settings-overlay__list-title">${escapeHtml(ep.label || ep.kind)}</span>
        <span class="settings-overlay__list-sub">${escapeHtml(ep.rest_base_url)}</span>
      </div>
      <span class="settings-overlay__chip" data-transport-status>—</span>
    `;
    list.appendChild(li);
  }
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => {
    switch (c) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}

async function probeAll(endpoints: TransportEndpointDto[]): Promise<void> {
  if (!invoke) return;
  setStatus("Проверяем все каналы…");
  let okCount = 0;
  for (const ep of endpoints) {
    const li = document.querySelector<HTMLElement>(
      `.settings-overlay__list-row[data-transport-kind="${ep.kind}"]`
    );
    const chip = li?.querySelector<HTMLElement>("[data-transport-status]");
    if (chip) {
      chip.textContent = "проверка…";
      chip.classList.remove("settings-overlay__chip--ok");
    }
    li?.classList.remove("settings-overlay__list-row--active");
    try {
      await invoke<void>("transport_probe", { endpoint: ep });
      if (chip) {
        chip.textContent = "OK";
        chip.classList.add("settings-overlay__chip--ok");
      }
      okCount += 1;
    } catch (e) {
      if (chip) {
        chip.textContent = "недоступен";
      }
      console.warn(`probe ${ep.kind} failed`, e);
    }
  }
  setStatus(`Доступно каналов: ${okCount} из ${endpoints.length}.`, okCount > 0 ? "ok" : "error");
}

async function runAutoFallback(mode: string): Promise<TransportEndpointDto | null> {
  if (!invoke) return null;
  try {
    const picked = await invoke<TransportEndpointDto>("transport_select_active", { mode });
    return picked;
  } catch (e) {
    console.warn("transport_select_active failed", e);
    return null;
  }
}

function highlightActive(kind: TransportKind | null | undefined): void {
  document.querySelectorAll<HTMLElement>(".settings-overlay__list-row").forEach((row) => {
    row.classList.toggle(
      "settings-overlay__list-row--active",
      !!kind && row.dataset.transportKind === kind
    );
  });
}

async function loadLastGood(): Promise<LastGoodDto | null> {
  if (!invoke) return null;
  try {
    return await invoke<LastGoodDto>("transport_last_good");
  } catch (e) {
    console.warn("transport_last_good failed", e);
    return null;
  }
}

interface RefreshResultDto {
  version: number;
  generated_at: number;
  endpoint_count: number;
}

async function refreshManifest(): Promise<RefreshResultDto | string> {
  if (!invoke) return "Tauri runtime недоступен";
  try {
    return await invoke<RefreshResultDto>("transport_refresh_manifest");
  } catch (e) {
    return typeof e === "string" ? e : (e as { message?: string })?.message ?? String(e);
  }
}

function syncSegmentedActive(mode: string): void {
  document.querySelectorAll<HTMLButtonElement>("[data-conn]").forEach((btn) => {
    btn.classList.toggle("settings-overlay__segmented-opt--active", btn.dataset.conn === mode);
  });
}

export async function initConnectionSection(): Promise<void> {
  if (document.body.dataset.kotoConnectionSection === "1") return;
  document.body.dataset.kotoConnectionSection = "1";

  syncSegmentedActive(readMode());
  document.querySelectorAll<HTMLButtonElement>("[data-conn]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const v = btn.dataset.conn || "auto";
      writeMode(v);
      syncSegmentedActive(v);
    });
  });

  const endpoints = await loadEndpoints();
  renderList(endpoints);

  const lastGood = await loadLastGood();
  if (lastGood?.kind) {
    highlightActive(lastGood.kind);
    setStatus(`Последний рабочий канал: ${lastGood.label || lastGood.kind}.`);
  }

  $("connection-test-btn")?.addEventListener("click", async () => {
    await probeAll(endpoints);
    const picked = await runAutoFallback(readMode());
    if (picked) {
      highlightActive(picked.kind);
      setStatus(`Активный канал: ${picked.label || picked.kind}.`, "ok");
    } else if (readMode() !== "auto") {
      setStatus("Выбранный канал недоступен.", "error");
    }
  });

  $("connection-refresh-btn")?.addEventListener("click", async () => {
    setStatus("Загружаем список мостов…");
    const result = await refreshManifest();
    if (typeof result === "string") {
      setStatus(`Обновление не удалось: ${result}`, "error");
      return;
    }
    setStatus(
      `Список мостов обновлён до версии ${result.version} (${result.endpoint_count} каналов).`,
      "ok",
    );
    const fresh = await loadEndpoints();
    renderList(fresh);
    endpoints.splice(0, endpoints.length, ...fresh);
  });
}
