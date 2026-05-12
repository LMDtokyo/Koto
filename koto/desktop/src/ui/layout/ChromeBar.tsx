export function ChromeBar() {
  return (
    <header className="chrome" id="chrome-bar">
      <div className="chrome__brand" data-tauri-drag-region>
        <img className="chrome__mark" src="./assets/koto-mark.png" width={26} height={26} alt="Koto" draggable={false} />
        <span className="chrome__title">Koto</span>
      </div>
      <div className="chrome__drag-gap" data-tauri-drag-region aria-hidden="true" />
      <div className="chrome__controls" data-tauri-drag-region-exclude="">
        <button type="button" id="theme-toggle" className="chrome__btn" title="Тема" aria-pressed="false">
          <span className="theme-icon theme-icon--moon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.5 5.5 0 0 1-7.8-7.8A9.043 9.043 0 0 0 12 3Z" />
            </svg>
          </span>
          <span className="theme-icon theme-icon--sun" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Zm0-5h1.5v3H12V2Zm0 17h1.5v3H12v-3ZM3.5 11H7v2H3.5v-2ZM17 11h3.5v2H17v-2ZM5.6 4.8l2.1 2.1-1.1 1.1-2.1-2.1 1.1-1.1Zm12.8 12.8l2.1 2.1-1.1 1.1-2.1-2.1 1.1-1.1Zm1.1-12.8-2.1 2.1-1.1-1.1 2.1-2.1 1.1 1.1ZM7.5 17.3l-2.1 2.1-1.1-1.1 2.1-2.1 1.1 1.1Z" />
            </svg>
          </span>
        </button>
        <button type="button" id="win-minimize" className="chrome__btn" title="Свернуть">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 13H5v-2h14v2Z" />
          </svg>
        </button>
        <button type="button" id="win-maximize" className="chrome__btn" title="Развернуть / восстановить">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M4 4h16v16H4zm2 4v10h12V8z" />
          </svg>
        </button>
        <button type="button" id="win-fullscreen" className="chrome__btn" title="Полный экран">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 3h4v2H9v2H7V3Zm10 0v4h-2V5h-2V3h4ZM7 21v-4h2v2h2v2H7Zm10 0h-4v-2h2v-2h2v4Z" />
          </svg>
        </button>
        <button type="button" id="win-close" className="chrome__btn chrome__btn--close" title="Закрыть">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
          </svg>
        </button>
      </div>
    </header>
  );
}
