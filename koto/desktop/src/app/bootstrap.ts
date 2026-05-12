import { initTheme, bindThemeToggle } from "@/shared/state/themeStore";
import { initWelcomeActions } from "@/features/shell/welcomeScreen";
import { initSidebarConnectivity } from "@/features/sidebar/sidebarConnectivity";
import { initRailAccount } from "@/features/sidebar/railAccount";
import { initSettingsPane } from "@/features/settings/settingsPane";
import { initSettingsOverlay } from "@/features/settings/settingsOverlay";
import { initProfileEditor } from "@/features/settings/profileEditor";
import { initSessionsView } from "@/features/settings/sessionsView";
import { initConnectionSection } from "@/features/settings/connectionSection";
import { initAuthScreen } from "@/features/auth/authScreen";
import { initChatThread } from "@/features/chat/chatThread";
import { initWsRouter } from "@/features/chat/wsRouter";
import { initChatRightPanel } from "@/features/chat/chatRightPanel";
import { initNotifications } from "@/features/chat/notifications";
import { initNewChatPane } from "@/features/chat/newChatPane";
import { initStubOverlay } from "@/features/shell/stubOverlay";
import { initChatListSidebarStubs } from "@/features/chat/chatList";
import { initFriendsSidebar } from "@/features/chat/friendsSidebar";
import { initMainPaneRouter } from "@/app/mainPaneRouter";
import { initGlobalShortcuts } from "@/app/globalShortcuts";
import { initOverlaysLayer } from "@/features/shell/overlaysLayer";
import { initRemainingPanes } from "@/features/shell/remainingPanes";
import { hasSession } from "@/shared/state/sessionStore";
import {
  initKotoWsListeners,
  startKotoWsIfSession,
  stopKotoWs,
} from "@/shared/services/wsService";

let bootStarted = false;

export async function boot(): Promise<void> {
  if (bootStarted) return;
  bootStarted = true;

  initTheme();
  bindThemeToggle(document.getElementById("theme-toggle"));
  initWelcomeActions();
  initOverlaysLayer();
  initChatThread();
  initWsRouter();
  initChatRightPanel();
  initNotifications();
  initMainPaneRouter();
  initGlobalShortcuts();
  initSettingsPane();
  initSettingsOverlay();
  initProfileEditor();
  initSessionsView();
  void initConnectionSection();
  initNewChatPane();
  initRemainingPanes();
  initStubOverlay();
  initChatListSidebarStubs();
  initFriendsSidebar();
  await initKotoWsListeners();
  window.addEventListener("koto:session-changed", () => {
    if (hasSession()) {
      void startKotoWsIfSession().catch(console.error);
    } else {
      void stopKotoWs().catch(console.error);
    }
  });
  await initSidebarConnectivity();
  initRailAccount();
  await initAuthScreen();
}
