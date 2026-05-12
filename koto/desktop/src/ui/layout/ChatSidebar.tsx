import brandMark from "../../assets/koto-brand-mark.png";
import {
  KotoIconArchive,
  KotoIconBots,
  KotoIconCapture,
  KotoIconCompose,
  KotoIconFriends,
  KotoIconSearch,
} from "@/ui/icons/KotoIcons";

/**
 * Сайдбар: узкий rail (как серверная колонка Discord) + основная колонка (поиск и чипы как в Telegram).
 * ID кнопок сохранены для `chatList.ts` / `newChatPane.ts` / `settingsPane.ts`.
 */
export function ChatSidebar() {
  return (
    <aside className="sidebar chatlist-sidebar" aria-label="Список чатов">
      <nav className="sidebar-rail" aria-label="Быстрые действия">
        <div className="sidebar-rail__brand" title="Koto">
          <img src={brandMark} alt="" width={36} height={36} className="sidebar-rail__brand-img" decoding="async" />
        </div>

        {/* Session-pattern: rail без compose/friends/bots-кнопок. Compose теперь
            справа в toolbar чат-листа, остальные действия — через popover аккаунта. */}
        <div className="sidebar-rail__cluster" hidden>
          <button type="button" id="chatlist-new-chat-btn" className="sidebar-rail__btn" hidden>
            <KotoIconCompose size={22} />
          </button>
          <button type="button" id="sidebar-friends-btn" className="sidebar-rail__btn" hidden>
            <KotoIconFriends size={22} />
          </button>
          <button type="button" id="sidebar-bots-btn" className="sidebar-rail__btn" hidden>
            <KotoIconBots size={22} />
          </button>
          <button type="button" id="sidebar-camera-btn" className="sidebar-rail__btn" hidden>
            <KotoIconCapture size={22} />
          </button>
        </div>

        <div className="sidebar-rail__spacer" aria-hidden="true" />
        <button
          type="button"
          id="rail-account-btn"
          className="rail-account-btn"
          title="Аккаунт"
          aria-label="Аккаунт"
          aria-haspopup="menu"
          aria-expanded="false"
          aria-controls="rail-account-popover"
        >
          <span id="sidebar-user-avatar" className="rail-account-btn__avatar" aria-hidden="true">
            ?
          </span>
          <span
            id="sidebar-connection-dot"
            className="rail-account-btn__dot chatlist-connection-dot chatlist-connection-dot--idle"
            role="img"
            aria-label="Состояние соединения"
            title=""
          />
        </button>
      </nav>

      <div
        id="rail-account-popover"
        className="rail-account-popover"
        role="menu"
        aria-label="Меню аккаунта"
        hidden
      >
        <button
          type="button"
          id="chatlist-userbar-profile-hit"
          className="rail-account-popover__header rail-account-popover__header--btn"
          role="menuitem"
          title="Открыть профиль"
        >
          <span className="rail-account-popover__avatar" aria-hidden="true">
            <span id="rail-account-popover-avatar-text">?</span>
          </span>
          <span className="rail-account-popover__text">
            <span id="sidebar-user-name" className="rail-account-popover__name">
              Гость
            </span>
            <span id="sidebar-user-status" className="rail-account-popover__sub">
              Войдите в аккаунт
            </span>
          </span>
          <span className="rail-account-popover__chev" aria-hidden="true">›</span>
        </button>
        <div className="rail-account-popover__divider" aria-hidden="true" />
        <button
          type="button"
          id="sidebar-open-settings"
          className="rail-account-popover__row"
          role="menuitem"
        >
          <span className="rail-account-popover__row-ic" aria-hidden="true">⚙</span>
          <span>Настройки</span>
        </button>
        <div className="rail-account-popover__divider" aria-hidden="true" />
        <button
          type="button"
          id="rail-account-sign-out"
          className="rail-account-popover__row rail-account-popover__row--danger"
          role="menuitem"
        >
          <span className="rail-account-popover__row-ic" aria-hidden="true">↩</span>
          <span>Выйти из Koto ID</span>
        </button>
      </div>

      <div className="sidebar-main">
        <div className="chatlist-top">
          <header className="chatlist-toolbar">
            <div className="chatlist-toolbar__brand">
              <h1 className="chatlist-brand-title">Чаты</h1>
              <span id="chatlist-unread-badge" className="chatlist-brand-unread" hidden />
            </div>
            <button
              type="button"
              id="chatlist-toolbar-new-chat"
              className="chatlist-toolbar__action"
              title="Новый чат"
              aria-label="Новый чат"
            >
              <KotoIconCompose size={20} />
            </button>
          </header>
          <label className="chatlist-search">
            <span className="chatlist-search__icon" aria-hidden="true">
              <KotoIconSearch size={17} />
            </span>
            <input id="chatlist-search-input" type="search" placeholder="Поиск" autoComplete="off" />
          </label>
          {/* Session-style: фильтр-табов нет, один сплошной список чатов. */}
          <div id="chatlist-connectivity-banner" className="chatlist-connectivity-banner" hidden role="status">
            <p id="chatlist-connectivity-banner-msg" className="chatlist-connectivity-banner__msg" />
            <button
              type="button"
              id="chatlist-connectivity-retry"
              className="chatlist-connectivity-banner__retry"
              hidden
            >
              Повторить
            </button>
          </div>
        </div>

        <div className="chatlist-scroll">
          {/* Session-pattern: Message Requests banner — отдельная строка над списком чатов */}
          <button
            type="button"
            id="chatlist-requests-banner"
            className="chatlist-requests-banner"
            title="Открыть заявки в друзья"
            hidden
          >
            <span className="chatlist-requests-banner__ic" aria-hidden="true">
              <KotoIconFriends size={18} />
            </span>
            <span className="chatlist-requests-banner__title">Запросы на чат</span>
            <span id="chatlist-requests-banner-count" className="chatlist-requests-banner__count">
              0
            </span>
          </button>
          <button type="button" id="chatlist-archive-row" className="chatlist-archive-row" hidden>
            <span className="chatlist-archive-row__ic" aria-hidden="true">
              <KotoIconArchive size={20} />
            </span>
            <span className="chatlist-archive-row__body">
              <span className="chatlist-archive-row__title">Архив</span>
              <span id="chatlist-archive-sub" className="chatlist-archive-row__sub" />
            </span>
            <span id="chatlist-archive-unread" className="chatlist-archive-row__badge" hidden>
              0
            </span>
            <span className="chatlist-archive-row__chev" aria-hidden="true">
              ›
            </span>
          </button>
          <div id="chat-list-hint" className="chatlist-hint" />
          <ul id="chat-list" className="chatlist-rows" aria-label="Диалоги" />
        </div>

        <div id="friends-sidebar-stack" className="friends-sidebar-stack" hidden>
          <header className="friends-sidebar__toolbar">
            <div className="friends-sidebar__brand">
              <h1 className="friends-sidebar__title">Друзья</h1>
              <span id="friends-pending-count" className="friends-sidebar__count" hidden />
            </div>
          </header>
          <div className="friends-sidebar__tabs" role="tablist" aria-label="Раздел друзей">
            <button
              type="button"
              className="friends-sidebar__tab friends-sidebar__tab--active"
              data-friends-tab="all"
              role="tab"
              aria-selected="true"
            >
              Все
            </button>
            <button type="button" className="friends-sidebar__tab" data-friends-tab="pending" role="tab" aria-selected="false">
              Запросы
            </button>
          </div>
          <div className="friends-sidebar__scroll">
            <ul id="friends-list" className="friends-list" aria-label="Список друзей и заявок" />
            <p id="friends-empty-hint" className="friends-empty-hint" hidden />
          </div>
        </div>

        <div id="chatlist-settings-nav" className="chatlist-settings-nav" hidden>
          <div className="chatlist-settings-nav__head">
            <h2 className="chatlist-settings-nav__title">Настройки</h2>
          </div>
          <nav className="chatlist-settings-nav__list" aria-label="Разделы настроек">
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="profile">
              Профиль
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="notifications">
              Уведомления
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="privacy">
              Приватность
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="ephemeral">
              Исчезающие сообщения
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="theme">
              Тема и цвет
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="focus">
              Режим фокуса
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="storage">
              Хранилище
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="network">
              Использование сети
            </button>
            <button type="button" className="chatlist-settings-nav__item" data-settings-sidebar-section="auto">
              Автозагрузка
            </button>
          </nav>
        </div>

      </div>
    </aside>
  );
}
