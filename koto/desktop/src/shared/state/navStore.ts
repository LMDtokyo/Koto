/**
 * Main-pane navigation — mirrors desktop `NavState.kt` (`Screen` + `NavStack`).
 * Fullscreen overlays (auth) stay outside this stack.
 */

export type NavTransition = "PUSH" | "POP" | "NONE";

export type AppScreen =
  | { type: "Empty" }
  | { type: "Chat"; convId: string }
  | { type: "Friends" }
  | { type: "Settings" }
  | { type: "SettingsSub"; section: string }
  | { type: "NewChat" }
  | { type: "NewGroup" }
  | { type: "Contact"; id: string }
  | { type: "Stories" }
  | { type: "Safety" }
  | { type: "SafetyDetail"; convId: string }
  | { type: "Bots" }
  | { type: "BotForge"; step: string }
  | { type: "Archive" }
  | { type: "Call"; peerId: string; video: boolean };

/** Фабрики экранов (значение `Screen` — только объект с методами). */
export const Screen = {
  empty: (): AppScreen => ({ type: "Empty" }),
  chat: (convId: string): AppScreen => ({ type: "Chat", convId }),
  friends: (): AppScreen => ({ type: "Friends" }),
  settings: (): AppScreen => ({ type: "Settings" }),
  settingsSub: (section: string): AppScreen => ({ type: "SettingsSub", section }),
  newChat: (): AppScreen => ({ type: "NewChat" }),
  newGroup: (): AppScreen => ({ type: "NewGroup" }),
  contact: (id: string): AppScreen => ({ type: "Contact", id }),
  stories: (): AppScreen => ({ type: "Stories" }),
  safety: (): AppScreen => ({ type: "Safety" }),
  safetyDetail: (convId: string): AppScreen => ({ type: "SafetyDetail", convId }),
  bots: (): AppScreen => ({ type: "Bots" }),
  botForge: (step = "root"): AppScreen => ({ type: "BotForge", step }),
  archive: (): AppScreen => ({ type: "Archive" }),
  call: (peerId: string, video: boolean): AppScreen => ({ type: "Call", peerId, video }),
};

export function screenEquals(a: AppScreen, b: AppScreen): boolean {
  if (a.type !== b.type) return false;
  switch (a.type) {
    case "Chat":
      return a.convId === (b as { type: "Chat"; convId: string }).convId;
    case "SettingsSub":
      return a.section === (b as { type: "SettingsSub"; section: string }).section;
    case "Contact":
      return a.id === (b as { type: "Contact"; id: string }).id;
    case "SafetyDetail":
      return a.convId === (b as { type: "SafetyDetail"; convId: string }).convId;
    case "BotForge":
      return a.step === (b as { type: "BotForge"; step: string }).step;
    case "Call":
      return (
        a.peerId === (b as { type: "Call"; peerId: string; video: boolean }).peerId &&
        a.video === (b as { type: "Call"; peerId: string; video: boolean }).video
      );
    default:
      return true;
  }
}

export class NavStack {
  private _stack: AppScreen[];
  lastTransition: NavTransition;
  private readonly _listeners = new Set<() => void>();

  constructor(initial: AppScreen) {
    this._stack = [initial];
    this.lastTransition = "NONE";
  }

  subscribe(fn: () => void): () => void {
    this._listeners.add(fn);
    return () => this._listeners.delete(fn);
  }

  private _emit(): void {
    for (const fn of this._listeners) fn();
  }

  get current(): AppScreen {
    return this._stack[this._stack.length - 1];
  }

  get depth(): number {
    return this._stack.length;
  }

  get stack(): AppScreen[] {
    return [...this._stack];
  }

  push(screen: AppScreen): void {
    this.lastTransition = "PUSH";
    this._stack.push(screen);
    this._emit();
  }

  pop(): boolean {
    if (this._stack.length <= 1) return false;
    this.lastTransition = "POP";
    this._stack.pop();
    this._emit();
    return true;
  }

  resetTo(screen: AppScreen): void {
    this.lastTransition = "NONE";
    this._stack = [screen];
    this._emit();
  }
}

/** Singleton main-pane stack (desktop `KotoApp` `nav`). */
export const mainNav = new NavStack(Screen.empty());
