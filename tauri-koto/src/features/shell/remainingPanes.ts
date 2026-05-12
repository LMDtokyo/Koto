import { mainNav, Screen } from "@/shared/state/navStore";
import { navigateToChat } from "@/features/chat/chatThread";
import { openFeatureStub } from "@/features/shell/stubOverlay";

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function backEmpty(): void {
  mainNav.resetTo(Screen.empty());
}

function backPopOrEmpty(): void {
  if (!mainNav.pop()) mainNav.resetTo(Screen.empty());
}

export function initRemainingPanes(): void {
  if (document.body.dataset.kotoRemainingPanesInit === "1") return;
  document.body.dataset.kotoRemainingPanesInit = "1";

  $("new-group-back")?.addEventListener("click", backPopOrEmpty);
  $("stories-back")?.addEventListener("click", backPopOrEmpty);
  $("safety-back")?.addEventListener("click", backPopOrEmpty);
  $("safety-detail-back")?.addEventListener("click", backPopOrEmpty);
  $("bot-forge-back")?.addEventListener("click", backPopOrEmpty);

  $("archive-back")?.addEventListener("click", backEmpty);
  $("bots-back")?.addEventListener("click", backEmpty);

  $("archive-open-mock")?.addEventListener("click", () => {
    openFeatureStub("Архив", "Открытие заархивированного чата из списка — после интеграции API.");
  });

  $("bots-open-forge")?.addEventListener("click", () => {
    mainNav.push(Screen.botForge("root"));
  });

  $("safety-open-detail")?.addEventListener("click", () => {
    mainNav.push(Screen.safetyDetail("demo-conv"));
  });

  $("contact-back")?.addEventListener("click", backPopOrEmpty);
  $("contact-open-chat")?.addEventListener("click", () => {
    const id = $("contact-pane-id")?.textContent?.trim() || "";
    if (id) navigateToChat(id, id, id, false);
  });
}
