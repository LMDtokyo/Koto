/**
 * Правая выезжающая панель деталей чата.
 *
 * Открывается по клику на peer-area в `thread-header`. Показывает аватар,
 * имя и Koto ID собеседника, действия (mute/block/disappear), вкладки с
 * медиа/файлами/ссылками. Закрывается X / Esc / повторным кликом на header.
 */

import {
  Bell,
  BellOff,
  ChevronRight,
  Hourglass,
  Image as ImageIcon,
  Link2,
  Paperclip,
  Search,
  Trash2,
  UserMinus,
  X,
} from "lucide-react";

export function ChatRightPanel() {
  return (
    <aside
      id="chat-right-panel"
      className="chat-right-panel"
      role="complementary"
      aria-label="Детали чата"
      aria-hidden="true"
      hidden
    >
      <header className="chat-right-panel__header">
        <button
          type="button"
          id="chat-right-panel-close"
          className="chat-right-panel__close"
          aria-label="Закрыть"
          title="Закрыть (Esc)"
        >
          <X size={20} strokeWidth={1.75} absoluteStrokeWidth />
        </button>
        <h2 className="chat-right-panel__title">Детали</h2>
      </header>

      <div className="chat-right-panel__scroll">
        <section className="chat-right-panel__hero">
          <span id="chat-right-panel-avatar" className="chat-right-panel__avatar" aria-hidden="true">
            ?
          </span>
          <span id="chat-right-panel-name" className="chat-right-panel__name">
            —
          </span>
          <span id="chat-right-panel-handle" className="chat-right-panel__handle">
            —
          </span>
          <p id="chat-right-panel-bio" className="chat-right-panel__bio" hidden />
        </section>

        <nav className="chat-right-panel__actions" aria-label="Действия">
          <button type="button" id="chat-right-panel-search" className="chat-right-panel__action">
            <span className="chat-right-panel__action-ic">
              <Search size={18} strokeWidth={1.75} absoluteStrokeWidth />
            </span>
            <span className="chat-right-panel__action-label">Поиск в чате</span>
            <ChevronRight size={16} strokeWidth={1.75} absoluteStrokeWidth />
          </button>

          <button type="button" id="chat-right-panel-mute" className="chat-right-panel__action">
            <span className="chat-right-panel__action-ic">
              <Bell size={18} strokeWidth={1.75} absoluteStrokeWidth data-state="on" />
              <BellOff size={18} strokeWidth={1.75} absoluteStrokeWidth data-state="off" />
            </span>
            <span className="chat-right-panel__action-label">Уведомления</span>
            <span id="chat-right-panel-mute-state" className="chat-right-panel__action-detail">
              Включены
            </span>
          </button>

          <button type="button" id="chat-right-panel-disappearing" className="chat-right-panel__action">
            <span className="chat-right-panel__action-ic">
              <Hourglass size={18} strokeWidth={1.75} absoluteStrokeWidth />
            </span>
            <span className="chat-right-panel__action-label">Исчезающие сообщения</span>
            <span id="chat-right-panel-disappearing-state" className="chat-right-panel__action-detail">
              Никогда
            </span>
          </button>

          <button type="button" id="chat-right-panel-block" className="chat-right-panel__action chat-right-panel__action--danger">
            <span className="chat-right-panel__action-ic">
              <UserMinus size={18} strokeWidth={1.75} absoluteStrokeWidth />
            </span>
            <span className="chat-right-panel__action-label">Заблокировать</span>
          </button>

          <button type="button" id="chat-right-panel-clear" className="chat-right-panel__action chat-right-panel__action--danger">
            <span className="chat-right-panel__action-ic">
              <Trash2 size={18} strokeWidth={1.75} absoluteStrokeWidth />
            </span>
            <span className="chat-right-panel__action-label">Очистить переписку</span>
          </button>
        </nav>

        <section className="chat-right-panel__media" aria-label="Медиа">
          <div className="chat-right-panel__tabs" role="tablist">
            <button type="button" className="chat-right-panel__tab chat-right-panel__tab--active" data-media-tab="all" role="tab">
              <ImageIcon size={16} strokeWidth={1.75} absoluteStrokeWidth />
              <span>Медиа</span>
            </button>
            <button type="button" className="chat-right-panel__tab" data-media-tab="files" role="tab">
              <Paperclip size={16} strokeWidth={1.75} absoluteStrokeWidth />
              <span>Файлы</span>
            </button>
            <button type="button" className="chat-right-panel__tab" data-media-tab="links" role="tab">
              <Link2 size={16} strokeWidth={1.75} absoluteStrokeWidth />
              <span>Ссылки</span>
            </button>
          </div>
          <div id="chat-right-panel-media-grid" className="chat-right-panel__media-grid">
            <p className="chat-right-panel__media-empty">Здесь появятся фото и файлы из чата.</p>
          </div>
        </section>
      </div>
    </aside>
  );
}
