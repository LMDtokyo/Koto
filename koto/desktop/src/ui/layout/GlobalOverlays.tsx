export function GlobalOverlays() {
  return (
    <>
      <div id="overlay-attach" className="sheet-overlay" hidden>
        <div className="sheet-overlay__card" role="dialog" aria-modal="true" aria-labelledby="overlay-attach-title">
          <h2 id="overlay-attach-title" className="sheet-overlay__title">
            Вложение
          </h2>
          <p className="sheet-overlay__desc">Макет `AttachSheet.kt` — выбор файла и съёмка.</p>
          <button type="button" id="overlay-attach-dismiss" className="sheet-overlay__btn">
            Закрыть
          </button>
        </div>
      </div>
      <div id="overlay-emoji" className="sheet-overlay" hidden>
        <div className="sheet-overlay__card" role="dialog" aria-modal="true" aria-labelledby="overlay-emoji-title">
          <h2 id="overlay-emoji-title" className="sheet-overlay__title">
            Эмодзи
          </h2>
          <p className="sheet-overlay__desc">Макет `EmojiPicker.kt`.</p>
          <button type="button" id="overlay-emoji-pick" className="sheet-overlay__btn sheet-overlay__btn--accent">
            Вставить 👋
          </button>
          <button type="button" id="overlay-emoji-dismiss" className="sheet-overlay__btn sheet-overlay__btn--ghost">
            Закрыть
          </button>
        </div>
      </div>
      <div id="overlay-ephemeral" className="sheet-overlay" hidden>
        <div className="sheet-overlay__card" role="dialog" aria-modal="true" aria-labelledby="overlay-ephemeral-title">
          <h2 id="overlay-ephemeral-title" className="sheet-overlay__title">
            Исчезающие сообщения
          </h2>
          <p className="sheet-overlay__desc">Макет `EphemeralSheet.kt` — выбор TTL.</p>
          <button type="button" id="overlay-ephemeral-dismiss" className="sheet-overlay__btn">
            Закрыть
          </button>
        </div>
      </div>

      <div id="overlay-call" className="call-overlay" hidden>
        <div className="call-overlay__inner">
          <h2 className="call-overlay__title">Звонок</h2>
          <p className="call-overlay__sub" />
          <p className="call-overlay__hint">Макет `CallScreen.kt` — E2EE звонок поверх shell.</p>
          <button type="button" id="overlay-call-end" className="call-overlay__btn">
            Завершить
          </button>
        </div>
      </div>

      <div id="feature-stub-overlay" className="modal-overlay stub-overlay" hidden>
        <div className="stub-card" role="dialog" aria-modal="true" aria-labelledby="feature-stub-title">
          <div className="stub-card__mark">
            <img src="./assets/koto-mark.png" width={36} height={36} alt="" draggable={false} />
          </div>
          <h2 id="feature-stub-title" className="stub-card__title" />
          <p id="feature-stub-desc" className="stub-card__desc" />
          <button type="button" id="feature-stub-close" className="stub-card__btn">
            Закрыть
          </button>
        </div>
      </div>
    </>
  );
}
