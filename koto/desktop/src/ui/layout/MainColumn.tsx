import { rowIc } from "./rowIc";

export function MainColumn() {
  return (
    <main className="main">
      <div id="main-pane-host" className="main-pane-host" data-nav-transition="none">
        <div id="pane-empty" className="main-pane">
          <div id="welcome-pane" className="welcome">
            <div className="welcome__inner">
              <img className="welcome__mark" src="./assets/koto-mark.png" alt="Koto" draggable={false} />
              <h1 className="welcome__brand">Koto</h1>
              <hr className="welcome__divider" aria-hidden="true" />
              <p className="welcome__headline">Нет открытых диалогов</p>
              <p className="welcome__body">
                Нажмите ✎ сверху, чтобы начать новую переписку.
              </p>
            </div>
          </div>
        </div>

        <div id="pane-friends" className="main-pane main-pane--friends" hidden>
          <div className="friends-home">
            <div className="friends-home__inner">
              <h1 className="friends-home__title">Друзья</h1>
              <p className="friends-home__body">
                Выберите человека в списке слева, чтобы открыть личные сообщения, или добавьте контакт через кнопку «Новый чат».
              </p>
            </div>
          </div>
        </div>

        <div id="pane-chat" className="main-pane" hidden>
          <div id="thread-view" className="thread">
            <header className="thread-header">
              <button type="button" id="thread-header-peer" className="thread-header__peer">
                <div id="thread-avatar-wrap" className="thread-header__avatar-wrap">
                  <div id="thread-avatar" className="thread-header__avatar" aria-hidden="true">
                    ?
                  </div>
                </div>
                <div className="thread-header__text">
                  <div id="thread-title" className="thread-header__title" />
                  <span id="thread-subtitle" className="thread-header__subtitle">
                    не в сети
                  </span>
                </div>
              </button>
              <div className="thread-header__actions">
                <button type="button" id="thread-header-search" className="thread-header__icon-btn thread-header__icon-btn--live" title="Поиск" aria-label="Поиск">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="11" cy="11" r="7" />
                    <path d="m21 21-4.3-4.3" />
                  </svg>
                </button>
                <button type="button" id="thread-header-call" className="thread-header__icon-btn thread-header__icon-btn--live" title="Звонок" aria-label="Звонок">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.86.3 1.7.54 2.5a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.58-1.11a2 2 0 0 1 2.11-.45c.8.24 1.64.42 2.5.54A2 2 0 0 1 22 16.92z" />
                  </svg>
                </button>
                <button type="button" id="thread-header-video" className="thread-header__icon-btn thread-header__icon-btn--live" title="Видео" aria-label="Видео">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="2" y="7" width="15" height="10" rx="2" />
                    <path d="m17 10 5-3v10l-5-3" />
                  </svg>
                </button>
                <button type="button" id="thread-header-more" className="thread-header__icon-btn thread-header__icon-btn--live" title="Ещё" aria-label="Ещё">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <circle cx="5" cy="12" r="2" />
                    <circle cx="12" cy="12" r="2" />
                    <circle cx="19" cy="12" r="2" />
                  </svg>
                </button>
              </div>
            </header>
            <div id="thread-inline-search" className="thread-inline-search" hidden>
              <input id="thread-inline-search-input" className="thread-inline-search__input" type="text" placeholder="Поиск по сообщениям" />
              <span id="thread-inline-search-count" className="thread-inline-search__count">
                0
              </span>
              <button id="thread-inline-search-close" className="thread-inline-search__close" type="button" aria-label="Закрыть поиск">
                ×
              </button>
            </div>
            <div id="thread-pinned-bar" className="thread-pinned-bar" hidden>
              <span className="thread-pinned-bar__stripe" aria-hidden="true" />
              <div className="thread-pinned-bar__text">
                <span className="thread-pinned-bar__label">Закреплено</span>
                <span id="thread-pinned-preview" className="thread-pinned-bar__preview" />
              </div>
              <button type="button" id="thread-pinned-unpin" className="thread-pinned-bar__unpin" title="Открепить" aria-label="Открепить">
                ×
              </button>
            </div>
            <div className="thread-messages-area">
              <p id="thread-status" className="thread__status" role="status" />
              <div id="thread-messages" className="thread__messages" role="log">
                <div id="thread-history-sentinel" className="thread-history-sentinel" aria-hidden="true" />
              </div>
            </div>
            <footer className="thread-composer">
              <div id="thread-composer-reply" className="thread-composer-reply" hidden>
                <div className="thread-composer-reply__stripe" aria-hidden="true" />
                <div className="thread-composer-reply__text">
                  <span id="thread-composer-reply-label" className="thread-composer-reply__label">
                    Ответ
                  </span>
                  <span id="thread-composer-reply-snippet" className="thread-composer-reply__snippet" />
                </div>
                <button type="button" id="thread-composer-reply-close" className="thread-composer-reply__close" title="Отменить ответ" aria-label="Отменить ответ">
                  ×
                </button>
              </div>
              {/* Session-layout: [attach] [textarea] [emoji] [send/voice] на одной строке */}
              <div className="thread-composer__row">
                <button type="button" className="thread-composer__attach" disabled title="Вложение" aria-label="Вложение">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="22" height="22">
                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66L9.64 16.2a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                  </svg>
                </button>
                <div className="thread-composer__field-shell">
                  <textarea id="thread-composer-input" className="thread-composer__field" rows={1} disabled placeholder="Сообщение" />
                  <button type="button" id="thread-composer-emoji" className="thread-composer__side thread-composer__side--live" title="Эмодзи" aria-label="Эмодзи">
                    <span aria-hidden="true">☺</span>
                  </button>
                  <button type="button" id="thread-composer-ttl" className="thread-composer__side thread-composer__side--live" title="Исчезающие" aria-label="Исчезающие">
                    <span aria-hidden="true">⏱</span>
                  </button>
                </div>
                <button type="button" id="thread-composer-send" className="thread-composer__send" disabled title="Отправить" aria-label="Отправить">
                  <svg className="thread-composer__send-icon thread-composer__send-icon--mic" viewBox="0 0 24 24" fill="currentColor" width="22" height="22" aria-hidden="true">
                    <path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v5a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2z" />
                  </svg>
                  <svg className="thread-composer__send-icon thread-composer__send-icon--send" viewBox="0 0 24 24" fill="currentColor" width="22" height="22" aria-hidden="true">
                    <path d="M2.01 21 23 12 2.01 3 2 10l15 2-15 2.01z" />
                  </svg>
                </button>
              </div>
            </footer>
          </div>
        </div>

        <div id="pane-settings" className="main-pane main-pane--settings" hidden>
          <div className="settings-sheet settings-sheet--in-pane" role="region" aria-labelledby="settings-title">
            <div className="settings-sheet__top">
              <h2 id="settings-title" className="settings-sheet__title">
                Настройки
              </h2>
              <div className="settings-sheet__top-actions">
                <button type="button" id="settings-pane-theme" className="settings-sheet__icon-btn" title="Тема">
                  ◐
                </button>
                <button type="button" id="settings-pane-close" className="settings-sheet__icon-btn" aria-label="Закрыть">
                  ×
                </button>
              </div>
            </div>
            <div className="settings-sheet__scroll">
              <button type="button" id="settings-profile-card" className="settings-profile-card">
                <div className="settings-profile-card__avatar" aria-hidden="true">
                  Я
                </div>
                <div className="settings-profile-card__text">
                  <span className="settings-profile-card__name">Вы</span>
                  <span id="settings-profile-sub" className="settings-profile-card__sub">
                    —
                  </span>
                </div>
                <span className="settings-profile-card__chev" aria-hidden="true">
                  ›
                </span>
              </button>

              <section className="settings-section">
                <h3 className="settings-section__label">Аккаунт</h3>
                <button type="button" className="settings-row" id="settings-copy-row">
                  <div className="settings-row__ic" style={rowIc("#7c5cff")}>
                    <span>ID</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Мой Koto ID</span>
                    <span id="settings-id-detail" className="settings-row__detail">
                      —
                    </span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" data-settings-section="seed">
                  <div className="settings-row__ic" style={rowIc("#ff6b35")}>
                    <span>12</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Фраза восстановления</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" data-settings-section="devices">
                  <div className="settings-row__ic" style={rowIc("#00a676")}>
                    <span>◉</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Связанные устройства</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" data-settings-section="username">
                  <div className="settings-row__ic" style={rowIc("#3276ff")}>
                    <span>@</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Имя пользователя</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
              </section>

              <section className="settings-section">
                <h3 className="settings-section__label">Приватность</h3>
                <button type="button" className="settings-row" data-settings-section="privacy">
                  <div className="settings-row__ic" style={rowIc("#00a676")}>
                    <span>◎</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Приватность</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" data-settings-section="ephemeral">
                  <div className="settings-row__ic" style={rowIc("#ff6b35")}>
                    <span>⏱</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Исчезающие сообщения</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
              </section>

              <section className="settings-section">
                <h3 className="settings-section__label">Внешний вид</h3>
                <button type="button" className="settings-row" id="settings-theme-row" title="Переключить тему">
                  <div className="settings-row__ic" style={rowIc("#7c5cff")}>
                    <span>◐</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Тема и цвет</span>
                    <span id="settings-theme-label" className="settings-row__detail">
                      как в шапке окна
                    </span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" id="settings-focus-row" title="Нажмите, чтобы включить или выключить">
                  <div className="settings-row__ic" style={rowIc("#5865f2")}>
                    <span>◎</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Режим фокуса</span>
                    <span id="settings-focus-detail" className="settings-row__detail">
                      —
                    </span>
                  </div>
                </button>
                <button type="button" className="settings-row" data-settings-section="font">
                  <div className="settings-row__ic" style={rowIc("#00a676")}>
                    <span>Aa</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Размер шрифта</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
              </section>

              <section className="settings-section">
                <h3 className="settings-section__label">Данные</h3>
                <button type="button" className="settings-row" data-settings-section="storage">
                  <div className="settings-row__ic" style={rowIc("#7c5cff")}>
                    <span>▣</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Хранилище</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" data-settings-section="network">
                  <div className="settings-row__ic" style={rowIc("#3276ff")}>
                    <span>⇅</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Использование сети</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
                <button type="button" className="settings-row" data-settings-section="auto">
                  <div className="settings-row__ic" style={rowIc("#00a676")}>
                    <span>↓</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Автозагрузка</span>
                    <span className="settings-row__detail">макет</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
              </section>

              <section className="settings-section">
                <h3 className="settings-section__label">Соединение</h3>
                <div className="settings-hint-box">
                  <p>
                    <strong>REST</strong> <span id="settings-rest-url" className="settings-mono-inline">—</span>
                  </p>
                  <p id="settings-ws-hint" />
                  <p
                    style={{
                      marginTop: 10,
                      display: "flex",
                      flexWrap: "wrap",
                      alignItems: "center",
                      gap: 10,
                    }}
                  >
                    <span className="settings-mono-inline">/health:</span>
                    <span id="settings-health" className="settings-mono-inline">
                      —
                    </span>
                    <button type="button" id="settings-health-btn" className="sidebar__btn">
                      Проверить
                    </button>
                  </p>
                </div>
              </section>

              <section className="settings-section">
                <h3 className="settings-section__label">Сообщения</h3>
                <div className="settings-hint-box">
                  <p>
                    В этом билде отправка в чат — тестовый режим: текст в base64 как ciphertext, без полного libsignal в UI. На сервер
                    уходит только ciphertext.
                  </p>
                </div>
              </section>

              <p id="settings-copy-feedback" className="settings-feedback" aria-live="polite" />

              <section className="settings-section">
                <h3 className="settings-section__label">Сессия</h3>
                <button type="button" className="settings-row settings-row--danger" id="settings-sign-out">
                  <div className="settings-row__ic" style={rowIc("#ff3b30")}>
                    <span>×</span>
                  </div>
                  <div className="settings-row__body">
                    <span className="settings-row__title">Выйти из Koto ID</span>
                    <span className="settings-row__detail">локальная сессия и токены</span>
                  </div>
                  <span className="settings-row__chev" aria-hidden="true">
                    ›
                  </span>
                </button>
              </section>

              <p className="settings-footnote">Koto · main pane stack (как desktop `KotoApp`)</p>
            </div>
          </div>
        </div>

        <div id="pane-settings-sub" className="main-pane main-pane--settings-sub" hidden>
          <div className="settings-sub-screen">
            <header className="settings-sub-screen__bar">
              <button type="button" id="settings-sub-back" className="settings-sub-screen__back">
                <span className="settings-sub-screen__back-ic" aria-hidden="true">
                  ‹
                </span>
                <span>Настройки</span>
              </button>
              <h2 id="settings-sub-title" className="settings-sub-screen__title">
                Подраздел
              </h2>
            </header>
            <div id="settings-sub-body" className="settings-sub-screen__body" />
          </div>
        </div>

        <div id="pane-new-chat" className="main-pane main-pane--new-chat" hidden>
          <div className="new-chat-screen" role="region" aria-labelledby="new-chat-title">
            <div className="new-chat-topbar">
              <button type="button" id="new-chat-cancel" className="new-chat-cancel-btn">
                Отмена
              </button>
              <h2 id="new-chat-title" className="new-chat-topbar-title">
                Новый чат
              </h2>
              <div className="new-chat-topbar-spacer" aria-hidden="true" />
            </div>
            <div className="new-chat-search-wrap">
              <div className="new-chat-search-field">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                  <circle cx="11" cy="11" r="7" />
                  <path d="m21 21-4.3-4.3" />
                </svg>
                <input
                  id="new-chat-peer-id"
                  className="new-chat-peer-input"
                  type="text"
                  spellCheck={false}
                  autoComplete="off"
                  placeholder="@username или Koto ID"
                  autoFocus
                />
                <button type="button" id="new-chat-clear" className="new-chat-clear-btn" hidden aria-label="Очистить">
                  ×
                </button>
              </div>
            </div>
            <div className="new-chat-body">
              <p className="new-chat-lead">
                Введите Koto ID собеседника или имя пользователя через @.
              </p>
              <p id="new-chat-err" className="new-chat-err" role="alert" />
              <div className="new-chat-actions">
                <button type="button" id="new-chat-new-group" className="new-chat-secondary">
                  Новая группа
                </button>
                <button type="button" id="new-chat-reject" className="new-chat-secondary" hidden>
                  Отклонить
                </button>
                <button type="button" id="new-chat-create" className="new-chat-primary">
                  Создать
                </button>
              </div>
            </div>
          </div>
        </div>

        <div id="pane-new-group" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="new-group-back" className="stack-pane__back">
              ‹ Назад
            </button>
            <h2 className="stack-pane__title">Новая группа</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет экрана `NewGroupScreen.kt` — выбор участников и название появятся позже.</p>
          </div>
        </div>

        <div id="pane-contact" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="contact-back" className="stack-pane__back">
              ‹ Назад
            </button>
            <h2 className="stack-pane__title">Контакт</h2>
          </header>
          <div className="stack-pane__body">
            <p id="contact-pane-sub" className="stack-pane__mono" />
            <p id="contact-pane-id" className="stack-pane__mono stack-pane__mono--lg" />
            <button type="button" id="contact-open-chat" className="stack-pane__primary">
              Открыть чат
            </button>
          </div>
        </div>

        <div id="pane-stories" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="stories-back" className="stack-pane__back">
              ‹ Назад
            </button>
            <h2 className="stack-pane__title">Истории</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет `StoriesScreen.kt` — лента и подписки переносятся с desktop.</p>
          </div>
        </div>

        <div id="pane-safety" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="safety-back" className="stack-pane__back">
              ‹ Назад
            </button>
            <h2 className="stack-pane__title">Проверка безопасности</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет `SafetyScreen.kt`.</p>
            <button type="button" id="safety-open-detail" className="stack-pane__secondary">
              Детали (демо)
            </button>
          </div>
        </div>

        <div id="pane-safety-detail" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="safety-detail-back" className="stack-pane__back">
              ‹ Назад
            </button>
            <h2 className="stack-pane__title">Чат</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет `SafetyDetailScreen.kt`.</p>
          </div>
        </div>

        <div id="pane-bots" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="bots-back" className="stack-pane__back">
              ‹ Закрыть
            </button>
            <h2 className="stack-pane__title">Боты</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет `BotsScreen.kt`.</p>
            <button type="button" id="bots-open-forge" className="stack-pane__secondary">
              Конструктор бота
            </button>
          </div>
        </div>

        <div id="pane-bot-forge" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="bot-forge-back" className="stack-pane__back">
              ‹ Назад
            </button>
            <h2 className="stack-pane__title">Конструктор</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет `BotForgeScreen.kt`.</p>
          </div>
        </div>

        <div id="pane-archive" className="main-pane stack-pane" hidden>
          <header className="stack-pane__header">
            <button type="button" id="archive-back" className="stack-pane__back">
              ‹ Закрыть
            </button>
            <h2 className="stack-pane__title">Архив</h2>
          </header>
          <div className="stack-pane__body">
            <p className="stack-pane__lead">Макет `ArchiveScreen.kt` — список заархивированных диалогов.</p>
            <button type="button" id="archive-open-mock" className="stack-pane__secondary">
              Открыть пример чата
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
