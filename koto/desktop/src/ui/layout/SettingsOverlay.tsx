/**
 * Settings overlay (Discord-like full-screen + Linear/macOS-flavored search & scroll-spy).
 * Открывается поверх всего интерфейса, чат-лист продолжает существовать под backdrop'ом.
 */

import type { ReactElement } from "react";
import {
  CircleUserRound,
  MonitorSmartphone,
  KeyRound,
  ShieldCheck,
  Hourglass,
  SunMoon,
  BellOff,
  CaseSensitive,
  HardDrive,
  Globe2,
  Download,
  LogOut,
} from "lucide-react";

type CategoryDef = {
  id: string;
  title: string;
  group: string;
  icon: ReactElement;
};

const ICON_PROPS = { size: 18, strokeWidth: 1.75, absoluteStrokeWidth: true } as const;

const navIcon = {
  profile: <CircleUserRound {...ICON_PROPS} />,
  devices: <MonitorSmartphone {...ICON_PROPS} />,
  seed: <KeyRound {...ICON_PROPS} />,
  privacy: <ShieldCheck {...ICON_PROPS} />,
  ephemeral: <Hourglass {...ICON_PROPS} />,
  theme: <SunMoon {...ICON_PROPS} />,
  focus: <BellOff {...ICON_PROPS} />,
  font: <CaseSensitive {...ICON_PROPS} />,
  storage: <HardDrive {...ICON_PROPS} />,
  network: <Globe2 {...ICON_PROPS} />,
  auto: <Download {...ICON_PROPS} />,
  signout: <LogOut {...ICON_PROPS} />,
};

const categoryGroups: Array<{ label: string; items: CategoryDef[] }> = [
  {
    label: "Аккаунт",
    items: [
      { id: "profile", title: "Профиль", group: "Аккаунт", icon: navIcon.profile },
      { id: "devices", title: "Сеансы", group: "Аккаунт", icon: navIcon.devices },
      { id: "seed", title: "Резервная фраза", group: "Аккаунт", icon: navIcon.seed },
    ],
  },
  {
    label: "Приватность",
    items: [
      { id: "privacy", title: "Приватность", group: "Приватность", icon: navIcon.privacy },
      { id: "ephemeral", title: "Исчезающие сообщения", group: "Приватность", icon: navIcon.ephemeral },
    ],
  },
  {
    label: "Оформление",
    items: [
      { id: "theme", title: "Тема", group: "Оформление", icon: navIcon.theme },
      { id: "focus", title: "Не беспокоить", group: "Оформление", icon: navIcon.focus },
      { id: "font", title: "Размер текста", group: "Оформление", icon: navIcon.font },
    ],
  },
  {
    label: "Данные",
    items: [
      { id: "storage", title: "Хранилище", group: "Данные", icon: navIcon.storage },
      { id: "network", title: "Сеть", group: "Данные", icon: navIcon.network },
      { id: "auto", title: "Загрузка медиа", group: "Данные", icon: navIcon.auto },
      { id: "connection", title: "Соединение", group: "Данные", icon: navIcon.network },
    ],
  },
];

export function SettingsOverlay() {
  return (
    <div id="settings-overlay" className="settings-overlay" role="dialog" aria-modal="true" aria-labelledby="settings-overlay-title" hidden>
      <div className="settings-overlay__backdrop" data-settings-backdrop />

      <div className="settings-overlay__shell" role="document">
        <aside className="settings-overlay__nav" aria-label="Разделы настроек">
          <header className="settings-overlay__nav-header">
            <h2 className="settings-overlay__nav-title">Настройки</h2>
          </header>
          <label className="settings-overlay__search">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="m21 21-4.3-4.3" />
            </svg>
            <input
              id="settings-overlay-search"
              type="search"
              placeholder="Поиск по настройкам"
              autoComplete="off"
              spellCheck={false}
            />
          </label>
          <nav className="settings-overlay__nav-list" id="settings-overlay-nav-list">
            {categoryGroups.map((group) => (
              <section
                key={group.label}
                className="settings-overlay__nav-group"
                data-nav-group={group.label}
              >
                <h3 className="settings-overlay__nav-group-label">{group.label}</h3>
                {group.items.map((item) => (
                  <button
                    type="button"
                    key={item.id}
                    className="settings-overlay__nav-item"
                    data-section={item.id}
                    data-section-title={item.title}
                  >
                    <span className="settings-overlay__nav-ic" aria-hidden="true">
                      {item.icon}
                    </span>
                    <span className="settings-overlay__nav-label">{item.title}</span>
                  </button>
                ))}
              </section>
            ))}
            <section className="settings-overlay__nav-group" data-nav-group="Сессия">
              <h3 className="settings-overlay__nav-group-label">Сессия</h3>
              <button
                type="button"
                id="settings-overlay-sign-out"
                className="settings-overlay__nav-item settings-overlay__nav-item--danger"
              >
                <span className="settings-overlay__nav-ic" aria-hidden="true">
                  {navIcon.signout}
                </span>
                <span className="settings-overlay__nav-label">Выйти из Koto ID</span>
              </button>
            </section>
          </nav>
        </aside>

        <main className="settings-overlay__content" id="settings-overlay-content">
          <header className="settings-overlay__content-header">
            <div className="settings-overlay__content-titles">
              <span className="settings-overlay__content-crumb" id="settings-overlay-crumb">
                Аккаунт
              </span>
              <h2 id="settings-overlay-title" className="settings-overlay__content-title">
                Профиль
              </h2>
            </div>
            <button
              type="button"
              id="settings-overlay-close"
              className="settings-overlay__close"
              aria-label="Закрыть настройки"
              title="Закрыть (Esc)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div className="settings-overlay__scroll" id="settings-overlay-scroll">
            <section id="sec-profile" className="settings-overlay__section profile-editor" data-section-target="profile">
              <header className="settings-overlay__section-header">
                <h3 className="settings-overlay__section-title">Профиль</h3>
                <p className="settings-overlay__section-sub">Баннер, аватар и публичная информация. Сохраняется на сервере.</p>
              </header>

              <div className="profile-editor__card">
                <div
                  id="profile-editor-banner"
                  className="profile-editor__banner"
                  data-state="empty"
                  role="button"
                  tabIndex={0}
                  aria-label="Баннер профиля — нажмите, чтобы изменить"
                >
                  <div className="profile-editor__banner-fallback" aria-hidden="true" />
                  <img
                    id="profile-editor-banner-img"
                    className="profile-editor__banner-img"
                    alt=""
                    hidden
                  />
                  <div className="profile-editor__banner-overlay" aria-hidden="true">
                    <button
                      type="button"
                      id="profile-editor-banner-pick"
                      className="profile-editor__media-btn"
                      title="Загрузить баннер"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
                        <rect x="3" y="5" width="18" height="14" rx="2" />
                        <circle cx="9" cy="11" r="2" />
                        <path d="m21 17-5-5-9 9" />
                      </svg>
                      <span>Изменить баннер</span>
                    </button>
                    <button
                      type="button"
                      id="profile-editor-banner-remove"
                      className="profile-editor__media-btn profile-editor__media-btn--ghost"
                      title="Удалить баннер"
                      hidden
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                        <path d="M18 6 6 18M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                  <div id="profile-editor-banner-loader" className="profile-editor__loader" data-on="false" aria-hidden="true">
                    <div className="profile-editor__spinner" />
                  </div>
                  <input id="profile-editor-banner-input" type="file" accept="image/*" hidden />
                </div>

                <div className="profile-editor__avatar-wrap">
                  <button
                    type="button"
                    id="profile-editor-avatar"
                    className="profile-editor__avatar"
                    aria-label="Сменить аватар"
                  >
                    <span id="profile-editor-avatar-fallback" className="profile-editor__avatar-fallback">?</span>
                    <img
                      id="profile-editor-avatar-img"
                      className="profile-editor__avatar-img"
                      alt=""
                      hidden
                    />
                    <span className="profile-editor__avatar-edit" aria-hidden="true">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                        <circle cx="12" cy="13" r="4" />
                      </svg>
                    </span>
                    <span id="profile-editor-avatar-loader" className="profile-editor__avatar-loader" data-on="false" aria-hidden="true">
                      <span className="profile-editor__spinner" />
                    </span>
                  </button>
                  <input id="profile-editor-avatar-input" type="file" accept="image/*" hidden />
                </div>

                <div className="profile-editor__body">
                  <label className="profile-editor__field">
                    <span className="profile-editor__field-label">Отображаемое имя</span>
                    <input
                      id="profile-editor-display-name"
                      type="text"
                      maxLength={64}
                      placeholder="Как к вам обращаться"
                      className="profile-editor__input"
                      autoComplete="off"
                    />
                    <span className="profile-editor__field-hint">До 64 символов. Видно всем, с кем вы общаетесь.</span>
                  </label>

                  <label className="profile-editor__field">
                    <span className="profile-editor__field-label">Имя пользователя</span>
                    <div className="profile-editor__username">
                      <span className="profile-editor__username-prefix" aria-hidden="true">@</span>
                      <input
                        id="profile-editor-username-input"
                        type="text"
                        maxLength={32}
                        placeholder="username"
                        className="profile-editor__input profile-editor__input--username"
                        autoComplete="off"
                        spellCheck={false}
                      />
                    </div>
                    <span className="profile-editor__field-hint">
                      Латиница, цифры и подчёркивания. Можно сменить позже.
                    </span>
                  </label>

                  <label className="profile-editor__field">
                    <span className="profile-editor__field-label">О себе</span>
                    <textarea
                      id="profile-editor-bio"
                      maxLength={300}
                      rows={3}
                      placeholder="Пара слов о вас"
                      className="profile-editor__input profile-editor__input--multi"
                    />
                    <span className="profile-editor__field-hint">
                      <span id="profile-editor-bio-counter">0</span>/300
                    </span>
                  </label>

                  <div className="profile-editor__readonly">
                    <div className="profile-editor__readonly-row">
                      <span className="profile-editor__readonly-label">Koto ID</span>
                      <code id="profile-editor-id" className="profile-editor__readonly-value">—</code>
                      <button
                        type="button"
                        id="profile-editor-copy-id"
                        className="profile-editor__readonly-action"
                      >
                        Скопировать
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div className="profile-editor__actions" id="profile-editor-actions" data-dirty="false">
                <span id="profile-editor-status" className="profile-editor__status" aria-live="polite" />
                <button type="button" id="profile-editor-reset" className="profile-editor__btn profile-editor__btn--ghost">
                  Сбросить
                </button>
                <button type="button" id="profile-editor-save" className="profile-editor__btn profile-editor__btn--primary" disabled>
                  Сохранить
                </button>
              </div>

              <p id="settings-overlay-copy-feedback" className="settings-overlay__feedback" aria-live="polite" />
              <span id="settings-overlay-id-detail" hidden>—</span>
            </section>

            <section id="sec-devices" className="settings-overlay__section" data-section-target="devices">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Все устройства, на которых вы вошли в свой аккаунт. Если увидите незнакомое — завершите его сеанс.
                </p>
              </header>
              <div id="sessions-list" className="settings-overlay__sessions" />
              <p id="sessions-status" className="settings-overlay__empty-line" />
              <div className="settings-overlay__actions-row">
                <button
                  type="button"
                  id="sessions-revoke-others"
                  className="settings-overlay__btn settings-overlay__btn--danger"
                  disabled
                >
                  Выйти со всех других устройств
                </button>
              </div>
            </section>

            <section id="sec-seed" className="settings-overlay__section" data-section-target="seed">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  12 слов, которые позволяют восстановить аккаунт на новом устройстве. Никому не показывайте их и храните в надёжном месте — кто угодно с этой фразой получит полный доступ к вашим перепискам.
                </p>
              </header>
              <div className="settings-overlay__notice settings-overlay__notice--warn">
                <span className="settings-overlay__notice-ic" aria-hidden="true">⚠</span>
                <span>
                  Перед показом убедитесь, что рядом никого нет. После записи закройте окно.
                </span>
              </div>
              <div className="settings-overlay__actions-row">
                <button type="button" className="settings-overlay__btn settings-overlay__btn--primary" disabled>
                  Показать резервную фразу
                </button>
              </div>
            </section>

            <section id="sec-privacy" className="settings-overlay__section" data-section-target="privacy">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Кто может писать вам, видеть ваш онлайн-статус и подтверждать прочтение сообщений.
                </p>
              </header>
              <ul className="settings-overlay__list">
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">Кто может писать мне</span>
                    <span className="settings-overlay__list-sub">Все · Только контакты · Никто</span>
                  </div>
                  <div className="settings-overlay__segmented" role="radiogroup" aria-label="Кто может писать">
                    <button type="button" className="settings-overlay__segmented-opt settings-overlay__segmented-opt--active" data-priv-msg="all">Все</button>
                    <button type="button" className="settings-overlay__segmented-opt" data-priv-msg="contacts">Контакты</button>
                    <button type="button" className="settings-overlay__segmented-opt" data-priv-msg="none">Никто</button>
                  </div>
                </li>
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">Показывать «в сети»</span>
                    <span className="settings-overlay__list-sub">Другие будут видеть, когда вы онлайн.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-priv-toggle="online" defaultChecked />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">Уведомления о прочтении</span>
                    <span className="settings-overlay__list-sub">Собеседник видит, что вы прочитали сообщение.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-priv-toggle="receipts" defaultChecked />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
              </ul>
            </section>

            <section id="sec-ephemeral" className="settings-overlay__section" data-section-target="ephemeral">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Сообщения автоматически удаляются у обоих участников через выбранное время. Применяется только к новым чатам — существующие диалоги настраиваются отдельно.
                </p>
              </header>
              <div className="settings-overlay__segmented settings-overlay__segmented--full" role="radiogroup" aria-label="Срок исчезающих сообщений">
                <button type="button" className="settings-overlay__segmented-opt settings-overlay__segmented-opt--active" data-ephemeral="0">Никогда</button>
                <button type="button" className="settings-overlay__segmented-opt" data-ephemeral="3600">1 час</button>
                <button type="button" className="settings-overlay__segmented-opt" data-ephemeral="86400">24 часа</button>
                <button type="button" className="settings-overlay__segmented-opt" data-ephemeral="604800">7 дней</button>
                <button type="button" className="settings-overlay__segmented-opt" data-ephemeral="2592000">30 дней</button>
              </div>
            </section>

            <section id="sec-theme" className="settings-overlay__section" data-section-target="theme">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Светлая или тёмная схема. «Авто» подстраивается под систему.
                </p>
              </header>
              <div className="settings-overlay__segmented settings-overlay__segmented--full" role="radiogroup" aria-label="Тема оформления">
                <button type="button" className="settings-overlay__segmented-opt" data-theme-pick="light">Светлая</button>
                <button type="button" className="settings-overlay__segmented-opt" data-theme-pick="dark">Тёмная</button>
              </div>
              <span id="settings-overlay-theme-label" hidden>—</span>
              <button type="button" id="settings-overlay-theme-row" hidden>—</button>
            </section>

            <section id="sec-focus" className="settings-overlay__section" data-section-target="focus">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Скрывает превью сообщений в уведомлениях и приглушает звуки. Полезно, когда нужно сосредоточиться.
                </p>
              </header>
              <div className="settings-overlay__list-row settings-overlay__list-row--standalone">
                <div className="settings-overlay__list-text">
                  <span className="settings-overlay__list-title">Не беспокоить</span>
                  <span id="settings-overlay-focus-detail" className="settings-overlay__list-sub">Выключено</span>
                </div>
                <label className="settings-overlay__switch">
                  <input type="checkbox" id="settings-overlay-focus-toggle" />
                  <span className="settings-overlay__switch-track" aria-hidden="true" />
                </label>
              </div>
              <button type="button" id="settings-overlay-focus-row" hidden>—</button>
            </section>

            <section id="sec-font" className="settings-overlay__section" data-section-target="font">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Размер текста в сообщениях и списке чатов.
                </p>
              </header>
              <div className="settings-overlay__segmented settings-overlay__segmented--full" role="radiogroup" aria-label="Размер текста">
                <button type="button" className="settings-overlay__segmented-opt" data-font="sm">Маленький</button>
                <button type="button" className="settings-overlay__segmented-opt settings-overlay__segmented-opt--active" data-font="md">Обычный</button>
                <button type="button" className="settings-overlay__segmented-opt" data-font="lg">Крупный</button>
                <button type="button" className="settings-overlay__segmented-opt" data-font="xl">Очень крупный</button>
              </div>
              <div className="settings-overlay__font-preview" aria-hidden="true">
                <span>Привет 👋 Это пример сообщения.</span>
              </div>
            </section>

            <section id="sec-storage" className="settings-overlay__section" data-section-target="storage">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Сколько места занимает кеш и загруженные медиафайлы.
                </p>
              </header>
              <div className="settings-overlay__stat-card">
                <div>
                  <span className="settings-overlay__stat-label">Кеш и медиа</span>
                  <span className="settings-overlay__stat-value">0 МБ</span>
                </div>
                <button type="button" className="settings-overlay__btn">Очистить кеш</button>
              </div>
            </section>

            <section id="sec-network" className="settings-overlay__section" data-section-target="network">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Тонкие настройки соединения. По умолчанию ничего менять не нужно.
                </p>
              </header>
              <ul className="settings-overlay__list">
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">Экономия трафика</span>
                    <span className="settings-overlay__list-sub">Сжимает медиа перед отправкой и получением.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-net-toggle="saver" />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">Использовать прокси</span>
                    <span className="settings-overlay__list-sub">Подключаться через указанный сервер.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-net-toggle="proxy" />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
              </ul>
            </section>

            <section id="sec-auto" className="settings-overlay__section" data-section-target="auto">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Какие медиафайлы скачивать автоматически.
                </p>
              </header>
              <ul className="settings-overlay__list">
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">По Wi-Fi</span>
                    <span className="settings-overlay__list-sub">Фото, видео и голосовые.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-auto-toggle="wifi" defaultChecked />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">По мобильной сети</span>
                    <span className="settings-overlay__list-sub">Только голосовые сообщения.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-auto-toggle="cell" />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
                <li className="settings-overlay__list-row">
                  <div className="settings-overlay__list-text">
                    <span className="settings-overlay__list-title">В роуминге</span>
                    <span className="settings-overlay__list-sub">Не качать ничего автоматически.</span>
                  </div>
                  <label className="settings-overlay__switch">
                    <input type="checkbox" data-auto-toggle="roaming" />
                    <span className="settings-overlay__switch-track" aria-hidden="true" />
                  </label>
                </li>
              </ul>
            </section>

            <section id="sec-connection" className="settings-overlay__section" data-section-target="connection">
              <header className="settings-overlay__section-header">
                <p className="settings-overlay__section-sub">
                  Как клиент достучится до серверов Koto. По умолчанию — напрямую. Если ваша сеть блокирует
                  Koto, переключитесь на «Авто», и клиент сам подберёт работающий канал.
                </p>
              </header>

              <div className="settings-overlay__segmented settings-overlay__segmented--full" role="radiogroup" aria-label="Способ соединения">
                <button type="button" className="settings-overlay__segmented-opt settings-overlay__segmented-opt--active" data-conn="auto">Авто</button>
                <button type="button" className="settings-overlay__segmented-opt" data-conn="direct">Прямое</button>
                <button type="button" className="settings-overlay__segmented-opt" data-conn="bridge">Через Bridge</button>
                <button type="button" className="settings-overlay__segmented-opt" data-conn="tor">Tor</button>
              </div>

              <ul className="settings-overlay__list" id="connection-endpoints-list" />

              <div className="settings-overlay__actions-row">
                <button type="button" id="connection-test-btn" className="settings-overlay__btn">
                  Проверить соединение
                </button>
                <button type="button" id="connection-refresh-btn" className="settings-overlay__btn settings-overlay__btn--ghost">
                  Обновить список мостов
                </button>
                <span id="connection-test-result" className="settings-overlay__feedback" />
              </div>
            </section>

            <button type="button" id="settings-overlay-health-btn" hidden>—</button>
            <span id="settings-overlay-health" hidden>—</span>
            <span id="settings-overlay-rest-url" hidden>—</span>
            <span id="settings-overlay-ws-hint" hidden />
            <span id="settings-overlay-copy-feedback" hidden />
          </div>
        </main>
      </div>
    </div>
  );
}
