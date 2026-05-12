import {
  authGenerateMnemonic,
  authPreviewAccountId,
  authRegisterFinish,
  authRegisterQuizChoices,
} from "@/shared/services/authService";

const POSITIONS = [2, 6, 10];
const SEED_COUNT = 12;

let seedWords: string[] = [];
let step = 0;
let confirmRound = 0;
let setActiveView: (which: "welcome" | "restore" | "register") => void = () => {};
let finishLogin: (tokens: Record<string, unknown>, seedPhrase?: string[]) => Promise<void> =
  async () => {};

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function syncBadgeAndProgress(): void {
  const el = $("auth-reg-step-badge");
  if (el) el.textContent = `шаг ${step + 1} из 3`;
  document.querySelectorAll(".auth-register-progress__seg").forEach((node, i) => {
    node.classList.toggle("auth-register-progress__seg--on", i <= step);
  });
}

function setRegErr(msg: string): void {
  const el = $("auth-reg-err");
  if (el) el.textContent = msg ?? "";
}

function showPanel(idx: number): void {
  for (let i = 0; i < 3; i += 1) {
    const p = $(`auth-reg-panel-${i}`);
    if (!p) continue;
    if (i === idx) p.removeAttribute("hidden");
    else p.setAttribute("hidden", "");
  }
}

function renderSeedGrid(): void {
  const grid = $("auth-reg-seed-grid");
  if (!grid) return;
  grid.innerHTML = "";
  const half = Math.ceil(SEED_COUNT / 2);
  for (let r = 0; r < half; r += 1) {
    const row = document.createElement("div");
    row.className = "auth-reg-seed-row";
    for (const idx of [r, r + half]) {
      if (idx >= SEED_COUNT) {
        const sp = document.createElement("div");
        sp.className = "auth-reg-seed-cell auth-reg-seed-cell--empty";
        row.appendChild(sp);
        continue;
      }
      const cell = document.createElement("div");
      cell.className = "auth-reg-seed-cell";
      const n = document.createElement("span");
      n.className = "auth-reg-seed-cell__idx";
      n.textContent = String(idx + 1).padStart(2, " ");
      const w = document.createElement("span");
      w.className = "auth-reg-seed-cell__word";
      w.textContent = seedWords[idx] || "";
      cell.appendChild(n);
      cell.appendChild(w);
      row.appendChild(cell);
    }
    grid.appendChild(row);
  }
}

async function refreshQuizChoices(): Promise<void> {
  const choices = await authRegisterQuizChoices(seedWords, confirmRound);
  const host = $("auth-reg-quiz-choices");
  if (!host) return;
  host.innerHTML = "";
  for (const word of choices) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "auth-reg-choice-btn";
    btn.textContent = word;
    btn.addEventListener("click", () => {
      void onQuizPick(word);
    });
    host.appendChild(btn);
  }
  const pos = POSITIONS[confirmRound];
  const numEl = $("auth-reg-quiz-num");
  if (numEl) numEl.textContent = `#${pos + 1}`;
  const prog = $("auth-reg-quiz-progress");
  if (prog) prog.textContent = `${confirmRound}/${POSITIONS.length} подтверждено`;
}

function showQuizSuccess(): void {
  $("auth-reg-quiz-active")?.setAttribute("hidden", "");
  $("auth-reg-quiz-success")?.removeAttribute("hidden");
}

function showQuizActive(): void {
  $("auth-reg-quiz-success")?.setAttribute("hidden", "");
  $("auth-reg-quiz-active")?.removeAttribute("hidden");
}

async function onQuizPick(word: string): Promise<void> {
  const pos = POSITIONS[confirmRound];
  const expected = seedWords[pos];
  if (word !== expected) {
    setRegErr("не совпадает, попробуйте ещё раз");
    $("auth-reg-quiz-wrap")?.classList.add("auth-reg-quiz-wrap--shake");
    setTimeout(() => {
      $("auth-reg-quiz-wrap")?.classList.remove("auth-reg-quiz-wrap--shake");
      setRegErr("");
    }, 600);
    return;
  }
  setRegErr("");
  confirmRound += 1;
  if (confirmRound >= POSITIONS.length) {
    showQuizSuccess();
    const prim = $("auth-reg-primary");
    if (prim) {
      prim.removeAttribute("hidden");
      prim.textContent = "продолжить";
    }
    return;
  }
  await refreshQuizChoices();
}

function updatePrimaryButton(): void {
  const prim = $("auth-reg-primary") as HTMLButtonElement | null;
  if (!prim) return;
  if (step === 0) {
    prim.removeAttribute("hidden");
    prim.textContent = "я записал фразу";
    prim.disabled = false;
  } else if (step === 1) {
    if (confirmRound >= POSITIONS.length) {
      prim.removeAttribute("hidden");
      prim.textContent = "продолжить";
      prim.disabled = false;
    } else {
      prim.setAttribute("hidden", "");
    }
  } else if (step === 2) {
    prim.removeAttribute("hidden");
    prim.textContent = "войти в Koto";
    const name = (($("auth-reg-display-name") as HTMLInputElement | null)?.value || "").trim();
    prim.disabled = !name;
  }
}

async function refreshKotoPreview(): Promise<void> {
  const out = $("auth-reg-koto-id");
  if (!out) return;
  out.textContent = "вычисляем…";
  try {
    const id = await authPreviewAccountId(seedWords);
    out.textContent = id;
  } catch {
    out.textContent = "—";
  }
}

async function onPrimaryClick(): Promise<void> {
  const prim = $("auth-reg-primary") as HTMLButtonElement | null;
  if (step === 0) {
    step = 1;
    confirmRound = 0;
    syncBadgeAndProgress();
    showPanel(1);
    showQuizActive();
    await refreshQuizChoices();
    updatePrimaryButton();
    return;
  }
  if (step === 1) {
    if (confirmRound < POSITIONS.length) return;
    step = 2;
    syncBadgeAndProgress();
    showPanel(2);
    await refreshKotoPreview();
    updatePrimaryButton();
    return;
  }
  if (step === 2) {
    const name = (($("auth-reg-display-name") as HTMLInputElement | null)?.value || "").trim();
    if (!name || !prim) return;
    prim.disabled = true;
    prim.textContent = "регистрация…";
    setRegErr("");
    try {
      const tokens = await authRegisterFinish(seedWords, name);
      await finishLogin(tokens, seedWords);
    } catch (e) {
      setRegErr(String(e));
      prim.textContent = "войти в Koto";
      prim.disabled = !(($("auth-reg-display-name") as HTMLInputElement | null)?.value || "").trim();
    }
  }
}

function resetRegisterState(): void {
  seedWords = [];
  step = 0;
  confirmRound = 0;
  setRegErr("");
  showPanel(0);
  showQuizActive();
  const name = $("auth-reg-display-name") as HTMLInputElement | null;
  if (name) name.value = "";
  const prim = $("auth-reg-primary") as HTMLButtonElement | null;
  if (prim) {
    prim.textContent = "я записал фразу";
    prim.disabled = false;
  }
  syncBadgeAndProgress();
}

export async function openRegisterFlow(): Promise<void> {
  resetRegisterState();
  setActiveView("register");
  setRegErr("");
  try {
    seedWords = await authGenerateMnemonic();
    renderSeedGrid();
    syncBadgeAndProgress();
    updatePrimaryButton();
  } catch (e) {
    setRegErr(String(e));
    setActiveView("welcome");
  }
}

export function closeRegisterFlow(): void {
  resetRegisterState();
  setActiveView("welcome");
}

export function initRegisterFlow(deps: {
  setActiveView: (which: "welcome" | "restore" | "register") => void;
  finishLogin: (tokens: Record<string, unknown>, seedPhrase?: string[]) => Promise<void>;
}): void {
  setActiveView = deps.setActiveView;
  finishLogin = deps.finishLogin;

  $("auth-reg-back")?.addEventListener("click", () => {
    closeRegisterFlow();
  });

  $("auth-reg-copy-seed")?.addEventListener("click", async () => {
    const text = seedWords.join(" ");
    try {
      await navigator.clipboard.writeText(text);
      const btn = $("auth-reg-copy-seed");
      if (btn) {
        const prev = btn.textContent;
        btn.textContent = "✓ скопировано";
        setTimeout(() => {
          if (btn) btn.textContent = prev || "скопировать фразу";
        }, 1500);
      }
    } catch {
      setRegErr("Не удалось скопировать в буфер.");
    }
  });

  $("auth-reg-primary")?.addEventListener("click", () => {
    void onPrimaryClick();
  });

  $("auth-reg-display-name")?.addEventListener("input", () => {
    const name = (($("auth-reg-display-name") as HTMLInputElement | null)?.value || "").trim();
    const av = $("auth-reg-name-avatar");
    if (av) av.textContent = name ? name.charAt(0).toUpperCase() : "?";
    updatePrimaryButton();
  });
}
