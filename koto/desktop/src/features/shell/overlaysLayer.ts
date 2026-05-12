/** Global shell overlays — desktop `KotoApp` attach / emoji / ephemeral sheets. */

const state = {
  attach: false,
  emoji: false,
  ephemeral: false,
};

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function sync(): void {
  $("overlay-attach")?.toggleAttribute("hidden", !state.attach);
  $("overlay-emoji")?.toggleAttribute("hidden", !state.emoji);
  $("overlay-ephemeral")?.toggleAttribute("hidden", !state.ephemeral);
}

export function isAnyOverlayOpen(): boolean {
  return state.attach || state.emoji || state.ephemeral;
}

export function dismissAllOverlays(): void {
  state.attach = false;
  state.emoji = false;
  state.ephemeral = false;
  sync();
}

export function openAttachOverlay(): void {
  state.attach = true;
  sync();
}

export function openEmojiOverlay(): void {
  state.emoji = true;
  sync();
}

export function openEphemeralOverlay(): void {
  state.ephemeral = true;
  sync();
}

export function initOverlaysLayer(): void {
  if (document.body.dataset.kotoOverlaysInit === "1") return;
  document.body.dataset.kotoOverlaysInit = "1";

  const closeAll = () => dismissAllOverlays();

  $("overlay-attach-dismiss")?.addEventListener("click", closeAll);
  $("overlay-attach")?.addEventListener("click", (e) => {
    if (e.target === $("overlay-attach")) closeAll();
  });

  $("overlay-emoji-dismiss")?.addEventListener("click", closeAll);
  $("overlay-emoji")?.addEventListener("click", (e) => {
    if (e.target === $("overlay-emoji")) closeAll();
  });

  $("overlay-ephemeral-dismiss")?.addEventListener("click", closeAll);
  $("overlay-ephemeral")?.addEventListener("click", (e) => {
    if (e.target === $("overlay-ephemeral")) closeAll();
  });

  $("overlay-emoji-pick")?.addEventListener("click", () => {
    const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
    if (ta && !ta.disabled) {
      ta.value += "👋";
      ta.dispatchEvent(new Event("input", { bubbles: true }));
    }
    state.emoji = false;
    sync();
  });
}
