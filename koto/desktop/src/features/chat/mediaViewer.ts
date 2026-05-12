/**
 * Lightbox-просмотр картинок в стиле Telegram: чёрный фон, центрированное
 * изображение по содержимому, mouse-wheel zoom, drag pan, double-click toggle
 * fit↔100%, ESC / клик за картинкой / × — закрытие.
 *
 * Один общий overlay переиспользуется для всех вызовов.
 */

const MIN_SCALE = 0.1;
const MAX_SCALE = 8;
const FIT_SCALE = 1; // 1 = "по размеру окна" в нашем расчёте (object-fit: contain)

interface ViewerState {
  scale: number;
  tx: number;
  ty: number;
  fitScale: number; // baseline когда scale=1
  /** Текущая позиция курсора при drag — для инкрементального delta. */
  drag: { x: number; y: number } | null;
}

let overlay: HTMLDivElement | null = null;
let img: HTMLImageElement | null = null;
let state: ViewerState | null = null;

function build(): void {
  if (overlay) return;
  overlay = document.createElement("div");
  overlay.className = "media-viewer";
  overlay.setAttribute("hidden", "");
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");
  overlay.innerHTML = `
    <button type="button" class="media-viewer__close" aria-label="Закрыть">×</button>
    <div class="media-viewer__stage">
      <img class="media-viewer__img" alt="" draggable="false" />
    </div>
    <div class="media-viewer__hint">Колесо мыши — масштаб · Двойной клик — 1:1 · ESC — закрыть</div>
  `;
  document.body.appendChild(overlay);
  img = overlay.querySelector(".media-viewer__img") as HTMLImageElement;

  // Закрытие
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay || (e.target as HTMLElement).classList.contains("media-viewer__close")) {
      close();
    }
  });
  document.addEventListener("keydown", (e) => {
    if (overlay && overlay.dataset.open === "true" && e.key === "Escape") close();
  });

  // Wheel zoom
  overlay.addEventListener(
    "wheel",
    (e) => {
      if (!state || !img) return;
      e.preventDefault();
      const rect = img.getBoundingClientRect();
      const cx = e.clientX - rect.left - rect.width / 2;
      const cy = e.clientY - rect.top - rect.height / 2;
      const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
      const next = clamp(state.scale * factor, MIN_SCALE, MAX_SCALE);
      // Зум вокруг точки курсора (Telegram-pattern).
      const dx = cx * (next / state.scale - 1);
      const dy = cy * (next / state.scale - 1);
      state.scale = next;
      state.tx -= dx;
      state.ty -= dy;
      apply();
    },
    { passive: false },
  );

  // Drag (pan) — инкрементальный delta, чтобы при clamp у бордюра курсор
  // не «отрывался» от картинки.
  overlay.addEventListener("mousedown", (e) => {
    if (!state || !img) return;
    if (e.target !== img) return;
    state.drag = { x: e.clientX, y: e.clientY };
    overlay!.classList.add("media-viewer--dragging");
    e.preventDefault();
  });
  document.addEventListener("mousemove", (e) => {
    if (!state?.drag) return;
    state.tx += e.clientX - state.drag.x;
    state.ty += e.clientY - state.drag.y;
    state.drag.x = e.clientX;
    state.drag.y = e.clientY;
    apply();
  });
  document.addEventListener("mouseup", () => {
    if (!state) return;
    state.drag = null;
    overlay?.classList.remove("media-viewer--dragging");
  });

  // Double-click toggle 1x↔fit
  img.addEventListener("dblclick", () => {
    if (!state) return;
    if (Math.abs(state.scale - FIT_SCALE) < 0.01) {
      // fit → 1:1 (показать пиксели как есть, относительно fitScale).
      state.scale = 1 / state.fitScale;
    } else {
      state.scale = FIT_SCALE;
      state.tx = 0;
      state.ty = 0;
    }
    apply();
  });
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

function clampPan(): void {
  // Не даём утаскивать картинку за пределы видимой области.
  // Когда картинка УЖЕ помещается на экран при текущем масштабе — фиксируем
  // tx=ty=0 (она центрирована flex'ом). Когда увеличена — разрешаем pan
  // в пределах overflow (так картинку нельзя вынести наружу).
  if (!img || !state || !overlay) return;
  const stage = overlay.querySelector(".media-viewer__stage") as HTMLElement | null;
  if (!stage) return;
  const stageRect = stage.getBoundingClientRect();

  // Размер картинки при текущем scale. naturalWidth учитывает что img уже
  // подогнан CSS-ограничениями (max-width: 92vw), но getBoundingClientRect
  // даёт уже трансформированный размер — поэтому используем offsetWidth
  // (CSS-размер до трансформа) и умножаем на scale.
  const baseW = img.offsetWidth || 1;
  const baseH = img.offsetHeight || 1;
  const renderedW = baseW * state.scale;
  const renderedH = baseH * state.scale;

  // Сколько в КАЖДУЮ сторону картинка может «уйти» прежде чем её край
  // совпадёт с краем сцены (картинка центрирована, край сцены = центр ± half).
  const overflowX = Math.max(0, (renderedW - stageRect.width) / 2);
  const overflowY = Math.max(0, (renderedH - stageRect.height) / 2);

  state.tx = Math.max(-overflowX, Math.min(overflowX, state.tx));
  state.ty = Math.max(-overflowY, Math.min(overflowY, state.ty));
}

function apply(): void {
  if (!img || !state) return;
  clampPan();
  img.style.transform = `translate(${state.tx}px, ${state.ty}px) scale(${state.scale})`;
}

export function openMediaViewer(src: string): void {
  if (!src) return;
  build();
  if (!overlay || !img) return;
  state = {
    scale: 1,
    tx: 0,
    ty: 0,
    fitScale: 1,
    drag: null,
  };
  img.src = src;
  overlay.removeAttribute("hidden");
  overlay.dataset.open = "true";
  // На загрузке считаем fitScale (как картинка реально вписалась) — нужен
  // для правильного 1:1 toggle.
  img.onload = () => {
    if (!img || !state) return;
    const naturalW = img.naturalWidth || 1;
    const renderedW = img.getBoundingClientRect().width || naturalW;
    state.fitScale = renderedW / naturalW;
    apply();
  };
  apply();
  document.body.style.overflow = "hidden";
}

function close(): void {
  if (!overlay) return;
  overlay.dataset.open = "false";
  overlay.setAttribute("hidden", "");
  if (img) img.src = "";
  state = null;
  document.body.style.overflow = "";
}
