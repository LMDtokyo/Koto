let initialized = false;

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function closeFeatureStub(): void {
  $("feature-stub-overlay")?.setAttribute("hidden", "");
}

export function initStubOverlay(): void {
  if (initialized) return;
  initialized = true;

  const root = $("feature-stub-overlay");
  const btn = $("feature-stub-close");

  btn?.addEventListener("click", () => closeFeatureStub());
  root?.addEventListener("click", (e) => {
    if (e.target === root) closeFeatureStub();
  });

  document.addEventListener(
    "keydown",
    (e) => {
      if (e.key !== "Escape") return;
      if (!root || root.hasAttribute("hidden")) return;
      e.preventDefault();
      e.stopPropagation();
      closeFeatureStub();
    },
    true
  );
}

/** Desktop PlaceholderScreen parity — короткий диалог «в переносе». */
export function openFeatureStub(title: string, description = ""): void {
  initStubOverlay();
  const root = $("feature-stub-overlay");
  const t = $("feature-stub-title");
  const d = $("feature-stub-desc");
  if (t) t.textContent = title;
  if (d) d.textContent = description || "";
  root?.removeAttribute("hidden");
}
