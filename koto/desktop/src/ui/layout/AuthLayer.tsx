export function AuthLayer() {
  return (
    <div id="auth-layer" className="auth-layer" hidden>
      <div className="auth-backdrop" aria-hidden="true" />
      <div className="auth-stack">
        <div id="auth-view-welcome" className="auth-view auth-view--active auth-welcome">
          <div id="auth-welcome-main">
            <div className="auth-welcome__spacer" />
            <div className="auth-logo-tile">
              <img src="./assets/koto-mark.png" width={160} height={160} alt="Koto" draggable={false} />
            </div>
            <h1 className="auth-welcome__title">Koto</h1>
            <p className="auth-welcome__tagline">Приватный мессенджер</p>
            <div className="auth-welcome__spacer" />
            <p id="auth-welcome-status" className="auth-status auth-welcome__status" role="status" aria-live="polite" />
            <div className="auth-welcome__actions">
              <button type="button" id="auth-btn-create" className="auth-btn-primary">
                Создать новый Koto ID
              </button>
              <button type="button" id="auth-btn-have-account" className="auth-btn-secondary">
                У меня уже есть аккаунт
              </button>
            </div>
          </div>
        </div>

        <div id="auth-view-register" className="auth-view auth-register">
          <div className="auth-register-top">
            <button type="button" id="auth-reg-back" className="auth-back-chip">
              Назад
            </button>
            <span id="auth-reg-step-badge" className="auth-register-badge">
              шаг 1 из 3
            </span>
            <div className="auth-restore-spacer" aria-hidden="true" />
          </div>
          <div className="auth-register-progress" aria-hidden="true">
            <div className="auth-register-progress__seg auth-register-progress__seg--on" />
            <div className="auth-register-progress__seg" />
            <div className="auth-register-progress__seg" />
          </div>
          <div className="auth-register-scroll">
            <div id="auth-reg-panel-0" className="auth-register-panel">
              <div className="auth-register-inner">
                <div className="auth-restore-logo">
                  <img src="./assets/koto-mark.png" width={40} height={40} alt="Koto" draggable={false} />
                </div>
                <h2 className="auth-register-headline">
                  Ваша фраза
                  <br />
                  восстановления
                </h2>
                <p className="auth-restore-lead auth-register-lead">
                  12 слов — это и есть ваш аккаунт. Запишите их по порядку. Кто знает фразу — управляет Koto ID.
                </p>
                <div id="auth-reg-seed-grid" className="auth-reg-seed-grid" aria-label="Фраза из 12 слов" />
                <button type="button" id="auth-reg-copy-seed" className="auth-reg-copy-link">
                  скопировать фразу
                </button>
                <div className="auth-reg-warning" role="note">
                  <span className="auth-reg-warning__icon" aria-hidden="true">
                    ⚠
                  </span>
                  <p className="auth-reg-warning__text">
                    Никогда не пересылайте фразу в чатах, почте или облаке. Восстановление невозможно.
                  </p>
                </div>
              </div>
            </div>
            <div id="auth-reg-panel-1" className="auth-register-panel" hidden>
              <div className="auth-register-inner">
                <div id="auth-reg-quiz-wrap" className="auth-reg-quiz-wrap">
                  <div id="auth-reg-quiz-active">
                    <h2 className="auth-register-headline auth-register-headline--quiz">Сверим фразу</h2>
                    <p className="auth-restore-lead">Выберите слово, которое стоит на указанной позиции.</p>
                    <div className="auth-reg-quiz-card">
                      <span className="auth-reg-quiz-label">слово</span>
                      <span id="auth-reg-quiz-num" className="auth-reg-quiz-num">
                        #3
                      </span>
                      <span id="auth-reg-quiz-progress" className="auth-reg-quiz-meta">
                        0/3 подтверждено
                      </span>
                    </div>
                    <div id="auth-reg-quiz-choices" className="auth-reg-quiz-choices" />
                  </div>
                  <div id="auth-reg-quiz-success" className="auth-reg-quiz-success" hidden>
                    <div className="auth-reg-quiz-check" aria-hidden="true">
                      ✓
                    </div>
                    <h2 className="auth-register-headline">фраза подтверждена</h2>
                    <p className="auth-restore-lead">Koto ID готов к использованию</p>
                  </div>
                </div>
              </div>
            </div>
            <div id="auth-reg-panel-2" className="auth-register-panel" hidden>
              <div className="auth-register-inner">
                <div className="auth-restore-logo auth-restore-logo--lg">
                  <img src="./assets/koto-mark.png" width={48} height={48} alt="Koto" draggable={false} />
                </div>
                <h2 className="auth-register-headline">Как вас зовут?</h2>
                <p className="auth-restore-lead">Имя видят только те, кому вы пишете. На серверах — никогда.</p>
                <div className="auth-reg-name-row">
                  <div id="auth-reg-name-avatar" className="auth-reg-name-avatar" aria-hidden="true">
                    ?
                  </div>
                  <input
                    id="auth-reg-display-name"
                    className="auth-reg-name-input"
                    type="text"
                    maxLength={32}
                    autoComplete="nickname"
                    placeholder="Ваше имя"
                  />
                </div>
                <div className="auth-reg-koto-box">
                  <span className="auth-reg-koto-label">ВАШ KOTO ID</span>
                  <p id="auth-reg-koto-id" className="auth-reg-koto-id">
                    вычисляем…
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div className="auth-register-bottom">
            <button type="button" id="auth-reg-primary" className="auth-btn-primary auth-register-bottom__btn">
              я записал фразу
            </button>
            <p id="auth-reg-err" className="auth-status" role="alert" />
          </div>
        </div>

        <div id="auth-view-restore" className="auth-view auth-restore">
          <div className="auth-restore-top">
            <button type="button" id="auth-restore-back" className="auth-back-chip">
              Назад
            </button>
            <h2 className="auth-restore-title">Восстановление</h2>
            <div className="auth-restore-spacer" aria-hidden="true" />
          </div>
          <div className="auth-restore-body">
            <div className="auth-restore-card">
              <div className="auth-restore-logo">
                <img src="./assets/koto-mark.png" width={40} height={40} alt="Koto" draggable={false} />
              </div>
              <h2 className="auth-restore-headline">Введите фразу восстановления</h2>
              <p className="auth-restore-lead">
                12 или 24 слова на английском (BIP39), через пробел или с новой строки.
              </p>
              <textarea
                id="auth-seed"
                className="auth-seed-area"
                rows={5}
                spellCheck={false}
                autoComplete="off"
                placeholder="word1 word2 word3 …"
              />
              <p id="auth-status" className="auth-status" role="status" />
              <div className="auth-restore-actions">
                <button type="button" id="auth-restore-submit" className="auth-btn-primary">
                  Войти
                </button>
                <button type="button" id="auth-register-seed" className="auth-btn-secondary">
                  Новая регистрация по этой фразе
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
