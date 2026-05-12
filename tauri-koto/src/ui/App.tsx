import { AuthLayer } from "./layout/AuthLayer";
import { ChatSidebar } from "./layout/ChatSidebar";
import { ChromeBar } from "./layout/ChromeBar";
import { GlobalOverlays } from "./layout/GlobalOverlays";
import { MainColumn } from "./layout/MainColumn";
import { SettingsOverlay } from "./layout/SettingsOverlay";

/**
 * Корневой layout: тот же DOM и `id`, что в legacy `index.html`,
 * чтобы существующие модули (`bootstrap`, экраны) продолжали работать.
 */
export function App() {
  return (
    <>
      <ChromeBar />
      <AuthLayer />
      <div id="app" className="shell">
        <div className="shell__body">
          <ChatSidebar />
          <MainColumn />
        </div>
      </div>
      <GlobalOverlays />
      <SettingsOverlay />
    </>
  );
}
