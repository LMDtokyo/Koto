import { createRoot } from "react-dom/client";
import { initWindowControls } from "@/chrome/windowControls";
import { boot } from "@/app/bootstrap";
import { App } from "./ui/App";
import "./styles/main.css";

const rootEl = document.getElementById("root");
if (!rootEl) {
  throw new Error("Missing #root — проверьте index.html");
}

/* Без StrictMode: в dev React 19 дважды размонтирует дерево — слушатели на кнопках
   title bar остаются на старых DOM-узлах, клики в Tauri «мёртвые». */
createRoot(rootEl).render(<App />);

/** React 19 коммитит в микротаске — сразу после render() элементов шапки ещё нет; иногда микротаск бежит раньше коммита → rAF. */
function startChromeAndBoot(attempt = 0): void {
  if (!document.getElementById("chrome-bar")) {
    if (attempt < 120) {
      requestAnimationFrame(() => startChromeAndBoot(attempt + 1));
    } else {
      console.error("Koto: #chrome-bar не появился — проверьте App / React mount.");
    }
    return;
  }
  initWindowControls();
  void boot();
}

function scheduleStart(): void {
  queueMicrotask(startChromeAndBoot);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", scheduleStart, { once: true });
} else {
  scheduleStart();
}
