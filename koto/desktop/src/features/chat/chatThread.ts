import {
  deleteConversationMessage,
  editConversationMessage,
  fetchConversationMessages,
  listMessageReactions,
  searchConversationMessagesMeta,
  sendConversationMessage,
  toggleMessageReaction,
  type ThreadMessageDto,
} from "@/shared/services/authService";
import { formatInvokeError, isNotFoundApiError } from "@/shared/services/invokeError";
import { refreshChatList } from "@/features/chat/chatList";
import { loadSession } from "@/shared/state/sessionStore";
import {
  decryptFromPeer,
  encryptForPeer,
  isCryptoReady,
  resetPeerCache,
} from "@/shared/services/cryptoSession";
import { mainNav, Screen } from "@/shared/state/navStore";
import {
  openEmojiOverlay,
  openEphemeralOverlay,
} from "@/features/shell/overlaysLayer";
import { clearComposerDraft, getComposerDraft, setComposerDraft } from "@/features/chat/composerDraft";
import { getPlaintext, rememberPlaintext } from "@/features/chat/messageCache";
import { openMediaViewer } from "@/features/chat/mediaViewer";
import { tryParseMediaEnvelope } from "@/features/chat/mediaEnvelope";
import {
  appendToThreadCache,
  loadThreadFromCache,
  saveThreadToCache,
  updateCachedPlaintext,
  type ThreadCacheRow,
} from "@/features/chat/offlineCache";

const PAGE_LIMIT = 50;

let activeConvId: string | null = null;
let activeTitle = "";
let activePeerId = "";
let activeOnline = false;

/** Context for the next `activateChat` (from list row or new-chat flow). */
let pendingOpen = { title: "", peerId: "", online: false };

let threadHistoryHasMore = false;
let threadHistoryOldestId: string | null = null;
let loadingOlder = false;

type PendingReply = { id: string; snippet: string };
let pendingReply: PendingReply | null = null;

export function setChatOpenContext(title: string, peerId: string, online: boolean): void {
  pendingOpen = {
    title: (title || "").trim(),
    peerId: (peerId || "").trim(),
    online: Boolean(online),
  };
}

function hashHue(id: string): number {
  let h = 0;
  const s = id || "";
  for (let i = 0; i < s.length; i += 1) h = s.charCodeAt(i) + ((h << 5) - h);
  return Math.abs(h) % 360;
}

function initialsFromTitle(title: string, fallbackId: string): string {
  const t = (title || "").trim();
  if (!t) return (fallbackId || "?").slice(0, 2).toUpperCase();
  const parts = t.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return t.slice(0, 2).toUpperCase();
}

/** Если title — голый 64-hex Koto ID, обрезаем до читаемого префикса. */
function displayTitle(raw: string): string {
  const t = (raw || "").trim();
  if (/^[0-9a-f]{64}$/i.test(t)) return t.slice(0, 12) + "…";
  return t;
}

function decodeCiphertextPreview(m: ThreadMessageDto): { text: string; hintTitle?: string } {
  // Plaintext, который мы расшифровали ранее или сами набрали при отправке.
  const cached = getPlaintext(m.id || "");
  if (cached !== undefined) return { text: cached };
  let shown = "Зашифрованное сообщение";
  const hintTitle: string | undefined = undefined;
  try {
    const ct = (m.ciphertext || "").replace(/\s/g, "");
    const raw = atob(ct);
    const bytes = Uint8Array.from(raw, (c) => c.charCodeAt(0));
    const dec = new TextDecoder("utf-8", { fatal: false }).decode(bytes);
    if (dec.trim() && dec.length < 8000 && !/[\uFFFD]/.test(dec)) {
      shown = dec;
    } else {
      // зашифрованное / бинарное содержимое — оставляем универсальный текст
    }
  } catch {
    /* ошибка декодирования — показываем плейсхолдер */
  }
  return { text: shown, hintTitle };
}

async function decryptInboundBatch(
  messages: ThreadMessageDto[],
  selfId: string
): Promise<void> {
  if (!isCryptoReady()) return;
  await Promise.all(
    messages.map(async (m) => {
      const id = m.id || "";
      if (!id) return;
      if (getPlaintext(id) !== undefined) return;
      const sender = (m.sender_id || "").trim();
      if (!sender || sender === selfId) return;
      const ct = (m.ciphertext || "").trim();
      if (!ct) return;
      try {
        const plain = await decryptFromPeer(sender, ct);
        rememberPlaintext(id, plain);
        if (activeConvId) updateCachedPlaintext(activeConvId, id, plain);
      } catch (e) {
        console.warn("decrypt failed for", id, e);
      }
    })
  );
}

function buildPreviewById(chronological: ThreadMessageDto[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const m of chronological) {
    const id = m.id || "";
    if (!id) continue;
    const { text } = decodeCiphertextPreview(m);
    map.set(id, text.length > 200 ? `${text.slice(0, 200)}…` : text);
  }
  return map;
}

function collectPreviewMapFromDom(container: HTMLElement): Map<string, string> {
  const map = new Map<string, string>();
  container.querySelectorAll(".thread-msg-wrap[data-msg-id]").forEach((wrap) => {
    const id = wrap.getAttribute("data-msg-id");
    const body = wrap.querySelector(".thread-msg__body");
    if (id && body?.textContent) {
      const t = body.textContent.trim();
      map.set(id, t.length > 200 ? `${t.slice(0, 200)}…` : t);
    }
  });
  return map;
}

function mergePreviewMaps(a: Map<string, string>, b: Map<string, string>): Map<string, string> {
  const out = new Map(a);
  b.forEach((v, k) => out.set(k, v));
  return out;
}

function authorLabel(senderId: string, selfId: string): string {
  if (selfId && senderId === selfId) return "Вы";
  return (senderId || "?").slice(0, 10);
}

function createMessageWrap(
  m: ThreadMessageDto,
  selfId: string,
  previewById: Map<string, string>
): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "thread-msg-wrap";
  const msgId = m.id || "";
  if (msgId) wrap.setAttribute("data-msg-id", msgId);

  const row = document.createElement("div");
  row.className = "thread-msg";
  if (selfId && m.sender_id === selfId) {
    row.classList.add("thread-msg--self");
    wrap.classList.add("thread-msg-wrap--self");
  }

  const bubble = document.createElement("div");
  bubble.className = "thread-msg__bubble";

  const actions = document.createElement("div");
  actions.className = "thread-msg__actions";
  const replyBtn = document.createElement("button");
  replyBtn.type = "button";
  replyBtn.className = "thread-msg__reply-btn";
  replyBtn.title = "Ответить";
  replyBtn.setAttribute("aria-label", "Ответить");
  replyBtn.textContent = "↩";
  replyBtn.addEventListener("click", (ev) => {
    ev.stopPropagation();
    if (!msgId) return;
    const { text } = decodeCiphertextPreview(m);
    const bodySnippet = text.length > 140 ? `${text.slice(0, 140)}…` : text;
    const who = authorLabel(m.sender_id || "", selfId);
    setPendingReply({
      id: msgId,
      snippet: `${who}: ${bodySnippet}`,
    });
    document.getElementById("thread-composer-input")?.focus();
  });
  actions.appendChild(replyBtn);

  const reactBtn = document.createElement("button");
  reactBtn.type = "button";
  reactBtn.className = "thread-msg__reply-btn";
  reactBtn.title = "Реакция";
  reactBtn.setAttribute("aria-label", "Реакция");
  reactBtn.textContent = "☺";
  reactBtn.addEventListener("click", (ev) => {
    ev.stopPropagation();
    if (msgId) openReactionPicker(reactBtn, msgId);
  });
  actions.appendChild(reactBtn);

  if (selfId && m.sender_id === selfId && msgId) {
    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.className = "thread-msg__reply-btn";
    editBtn.title = "Редактировать";
    editBtn.setAttribute("aria-label", "Редактировать");
    editBtn.textContent = "✎";
    editBtn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      void startEditingMessage(msgId, m);
    });
    actions.appendChild(editBtn);

    const deleteBtn = document.createElement("button");
    deleteBtn.type = "button";
    deleteBtn.className = "thread-msg__reply-btn thread-msg__reply-btn--danger";
    deleteBtn.title = "Удалить";
    deleteBtn.setAttribute("aria-label", "Удалить");
    deleteBtn.textContent = "🗑";
    deleteBtn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      void deleteMessage(msgId);
    });
    actions.appendChild(deleteBtn);
  } else if (msgId && m.sender_id) {
    // Чужое сообщение — кнопка «Пожаловаться».
    const reportBtn = document.createElement("button");
    reportBtn.type = "button";
    reportBtn.className = "thread-msg__reply-btn thread-msg__reply-btn--danger";
    reportBtn.title = "Пожаловаться";
    reportBtn.setAttribute("aria-label", "Пожаловаться");
    reportBtn.textContent = "⚑";
    reportBtn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      void reportMessage(msgId, m, selfId);
    });
    actions.appendChild(reportBtn);
  }

  bubble.appendChild(actions);

  const replyToId = (m.reply_to || "").trim();
  if (replyToId) {
    const quoted = previewById.get(replyToId);
    const quote = document.createElement("div");
    quote.className = "thread-msg__reply-quote";
    const strong = document.createElement("strong");
    strong.textContent = quoted ? "Ответ" : "Ответ на сообщение";
    quote.appendChild(strong);
    if (quoted) {
      const span = document.createElement("span");
      span.textContent = quoted;
      quote.appendChild(span);
    }
    bubble.appendChild(quote);
  }

  const body = document.createElement("div");
  body.className = "thread-msg__body";
  const { text: shown, hintTitle } = decodeCiphertextPreview(m);
  const media = tryParseMediaEnvelope(shown);
  if (media) {
    body.classList.add("thread-msg__body--media");
    const img = document.createElement("img");
    img.alt = media.name || "image";
    img.loading = "lazy";
    img.style.cssText = "max-width:320px;max-height:320px;border-radius:12px;display:block;cursor:zoom-in;";
    void resolveMediaSrc(media.fileId).then((src) => {
      if (src) img.src = src;
    });
    img.addEventListener("click", () => {
      if (img.src) openMediaViewer(img.src);
    });
    body.appendChild(img);
  } else {
    body.textContent = shown;
  }
  if (hintTitle) body.title = hintTitle;
  const fullTimestamp = new Date((m.sent_at || 0) * 1000).toLocaleString();
  if (!hintTitle) body.title = fullTimestamp;

  const meta = document.createElement("div");
  meta.className = "thread-msg__meta";
  meta.title = fullTimestamp;
  const time = formatBubbleTime(m.sent_at || 0);
  const timeNode = document.createElement("span");
  timeNode.className = "thread-msg__time";
  timeNode.textContent = time;
  meta.appendChild(timeNode);
  if (selfId && m.sender_id === selfId) {
    const check = document.createElement("span");
    check.className = "thread-msg__check";
    check.textContent = "✓✓";
    meta.appendChild(check);
  }

  bubble.appendChild(body);
  bubble.appendChild(meta);

  // Reactions chips под bubble — лениво подгрузим и отрендерим, если есть.
  const reactionsBox = document.createElement("div");
  reactionsBox.className = "thread-msg__reactions";
  reactionsBox.setAttribute("hidden", "");
  bubble.appendChild(reactionsBox);

  row.appendChild(bubble);
  wrap.appendChild(row);
  if (m.id) {
    void loadAndRenderReactions(m.id, reactionsBox, selfId);
  }
  return wrap;
}

/** Группировка реакций по emoji + рендер чипов в стиле Signal/TG. */
function renderReactionChips(
  container: HTMLElement,
  msgId: string,
  reactions: { actor_id: string; emoji: string }[],
  selfId: string,
): void {
  // Запоминаем какие emoji уже были на экране — анимация запускается только
  // на действительно НОВЫХ чипсах, не на каждой перерисовке (иначе дёргается).
  const previous = new Set<string>(
    Array.from(container.querySelectorAll<HTMLElement>(".thread-msg__reaction")).map(
      (el) => el.dataset.emoji || "",
    ),
  );
  container.replaceChildren();
  if (!reactions.length) {
    container.setAttribute("hidden", "");
    return;
  }
  container.removeAttribute("hidden");
  const grouped = new Map<string, { count: number; mine: boolean }>();
  for (const r of reactions) {
    const cur = grouped.get(r.emoji) ?? { count: 0, mine: false };
    cur.count += 1;
    if (r.actor_id === selfId) cur.mine = true;
    grouped.set(r.emoji, cur);
  }
  for (const [emoji, info] of grouped) {
    const chip = document.createElement("button");
    chip.type = "button";
    const isNew = !previous.has(emoji);
    chip.className =
      "thread-msg__reaction" +
      (info.mine ? " thread-msg__reaction--mine" : "") +
      (isNew ? " thread-msg__reaction--new" : "");
    chip.dataset.emoji = emoji;
    const e = document.createElement("span");
    e.className = "thread-msg__reaction-emoji";
    e.textContent = emoji;
    chip.appendChild(e);
    if (info.count > 1) {
      const c = document.createElement("span");
      c.className = "thread-msg__reaction-count";
      c.textContent = String(info.count);
      chip.appendChild(c);
    }
    chip.addEventListener("click", async (ev) => {
      ev.stopPropagation();
      if (!activeConvId) return;
      try {
        await toggleMessageReaction(activeConvId, msgId, emoji);
        // Локальный optimistic update — реальный список перерисуется по WS-фрейму.
        await loadAndRenderReactions(msgId, container, selfId);
      } catch (err) {
        console.warn("reaction toggle failed", err);
      }
    });
    container.appendChild(chip);
  }
}

const reactionsCache = new Map<string, { actor_id: string; emoji: string }[]>();

async function loadAndRenderReactions(
  msgId: string,
  container: HTMLElement,
  selfId: string,
): Promise<void> {
  if (!activeConvId) return;
  // Используем кеш если есть, иначе тянем с сервера.
  const cached = reactionsCache.get(msgId);
  if (cached) {
    renderReactionChips(container, msgId, cached, selfId);
  }
  try {
    const list = await listMessageReactions(activeConvId, msgId);
    reactionsCache.set(msgId, list);
    renderReactionChips(container, msgId, list, selfId);
  } catch {
    /* offline / 404 — оставляем как есть */
  }
}

function formatBubbleTime(sentAt: number): string {
  if (!sentAt) return "";
  const d = new Date(sentAt * 1000);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

function clearThreadMessageWraps(): void {
  const container = document.getElementById("thread-messages");
  if (!container) return;
  container.querySelectorAll(".thread-msg-wrap").forEach((n) => n.remove());
  container.querySelectorAll(".thread-history-hint").forEach((n) => n.remove());
}

function scrollThreadToBottom(behavior: ScrollBehavior = "auto"): void {
  const container = document.getElementById("thread-messages");
  if (!container) return;
  requestAnimationFrame(() => {
    container.scrollTo({ top: container.scrollHeight, behavior });
  });
}

function isNearBottom(): boolean {
  const container = document.getElementById("thread-messages");
  if (!container) return true;
  return container.scrollHeight - container.scrollTop - container.clientHeight < 96;
}

/** WS: добавить входящее сообщение в активный чат без полной перезагрузки. */
async function appendIncomingFrame(frame: {
  conversation_id: string;
  message_id: string;
  sender_id: string;
  ciphertext: string;
  sent_at: number;
  reply_to?: string | null;
}): Promise<void> {
  if (!activeConvId || frame.conversation_id !== activeConvId) return;
  const container = document.getElementById("thread-messages");
  if (!container) return;
  if (container.querySelector(`.thread-msg-wrap[data-msg-id="${cssEscapeId(frame.message_id)}"]`)) {
    return;
  }
  const dto: ThreadMessageDto = {
    id: frame.message_id,
    ciphertext: frame.ciphertext || "",
    sender_id: frame.sender_id || "",
    sent_at: frame.sent_at || Math.floor(Date.now() / 1000),
    reply_to: frame.reply_to || undefined,
  };
  const selfId = (loadSession()?.accountId || "").trim();
  // Сначала пытаемся расшифровать (если не наше) — потом рендерим уже с plaintext.
  await decryptInboundBatch([dto], selfId);
  if (activeConvId !== frame.conversation_id) return;
  const previewMap = collectPreviewMapFromDom(container as HTMLElement);
  const wasNearBottom = isNearBottom();
  const wrap = createMessageWrap(dto, selfId, previewMap);
  container.appendChild(wrap);
  if (wasNearBottom) scrollThreadToBottom("smooth");
  if (activeConvId) {
    appendToThreadCache(activeConvId, {
      id: dto.id || "",
      ciphertext: dto.ciphertext || "",
      sender_id: dto.sender_id || "",
      sent_at: dto.sent_at || 0,
      plain: getPlaintext(dto.id || ""),
      reply_to: dto.reply_to ?? undefined,
    });
  }
}

function cssEscapeId(s: string): string {
  return s.replace(/[^a-zA-Z0-9_-]/g, (ch) => `\\${ch}`);
}

function syncReplyBar(): void {
  const bar = document.getElementById("thread-composer-reply");
  const snippet = document.getElementById("thread-composer-reply-snippet");
  if (!bar || !snippet) return;
  if (pendingReply) {
    bar.removeAttribute("hidden");
    snippet.textContent = pendingReply.snippet;
  } else {
    bar.setAttribute("hidden", "");
    snippet.textContent = "";
  }
}

function setPendingReply(next: PendingReply): void {
  pendingReply = next;
  syncReplyBar();
}

function clearPendingReply(): void {
  pendingReply = null;
  syncReplyBar();
}

function upsertLoadOlderHint(container: HTMLElement, show: boolean): void {
  let btn = container.querySelector(".thread-history-hint") as HTMLButtonElement | null;
  if (!show) {
    btn?.remove();
    return;
  }
  const sentinel = document.getElementById("thread-history-sentinel");
  if (!sentinel) return;
  if (!btn) {
    btn = document.createElement("button");
    btn.type = "button";
    btn.className = "thread-history-hint";
    btn.textContent = "Загрузить ранее";
    btn.addEventListener("click", () => {
      void loadOlderThreadPage();
    });
    sentinel.after(btn);
  }
  btn.disabled = loadingOlder;
}

function setComposerEnabled(on: boolean): void {
  const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  const send = document.getElementById("thread-composer-send");
  const attach = document.querySelector(".thread-composer__attach");
  if (!on && ta && activeConvId) {
    setComposerDraft(activeConvId, ta.value || "");
  }
  if (!on) {
    clearPendingReply();
  }
  if (ta) {
    ta.disabled = !on;
    if (!on) ta.value = "";
  }
  if (send) {
    (send as HTMLButtonElement).disabled = !on;
    if (!on) send.classList.remove("thread-composer__send--active");
  }
  if (attach) {
    (attach as HTMLButtonElement).disabled = !on;
    attach.classList.toggle("thread-composer__attach--live", Boolean(on));
  }
  syncComposerSendState();
  autosizeComposer();
}

function setPinnedVisible(on: boolean, preview = ""): void {
  const bar = document.getElementById("thread-pinned-bar");
  const text = document.getElementById("thread-pinned-preview");
  if (!bar) return;
  if (on) {
    bar.removeAttribute("hidden");
    if (text) text.textContent = preview || "Закреплённое сообщение";
  } else {
    bar.setAttribute("hidden", "");
  }
}

async function submitOutgoingMessage(): Promise<void> {
  if (!activeConvId) return;
  const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  const text = (ta?.value || "").trim();
  if (!text) return;
  const status = document.getElementById("thread-status");
  if (status) status.textContent = "Отправка…";
  try {
    if (!isCryptoReady()) {
      throw new Error("Криптосессия не готова. Перезайдите в аккаунт.");
    }
    const peer = (activePeerId || "").trim();
    if (!peer) throw new Error("Не определён получатель сообщения.");
    let b64: string;
    try {
      b64 = await encryptForPeer(peer, text);
    } catch (e) {
      // На случай invalid-state в libsignal — сбрасываем кеш и пробуем ещё раз.
      resetPeerCache(peer);
      b64 = await encryptForPeer(peer, text);
      console.warn("retry encrypt after reset", e);
    }
    const sent = await sendConversationMessage(activeConvId, {
      messageType: 1,
      ciphertextBase64: b64,
      clientSeq: Date.now(),
      replyTo: pendingReply?.id ?? null,
    });
    if (sent?.id) rememberPlaintext(sent.id, text);
    clearPendingReply();
    if (activeConvId) clearComposerDraft(activeConvId);
    if (ta) ta.value = "";
    syncComposerSendState();
    autosizeComposer();
    await loadThreadMessages(activeConvId, { reset: true });
    // Gateway не шлёт WS-frame отправителю о собственном сообщении, поэтому
    // chatList.lastRows[i].last_message остаётся пустым и превью «Нет
    // сообщений». Синтезируем koto:ws:new-message чтобы chatList обновился.
    const sentAt = Math.floor(Date.now() / 1000);
    window.dispatchEvent(
      new CustomEvent("koto:ws:new-message", {
        detail: {
          conversation_id: activeConvId,
          message_id: sent?.id || "",
          sender_id: (loadSession()?.accountId || "").trim(),
          ciphertext: "",
          sent_at: sentAt,
        },
      }),
    );
    if (status) status.textContent = "";
  } catch (e) {
    if (status) status.textContent = formatInvokeError(e);
  }
}

function initThreadHeaderActions(): void {
  if (document.body.dataset.kotoThreadHeaderActions === "1") return;
  document.body.dataset.kotoThreadHeaderActions = "1";

  // Клик по peer-зоне в шапке открывает правую панель деталей
  // (chatRightPanel.ts вешает свой listener). Раньше здесь дополнительно
  // пушили Screen.contact — это выкидывало пользователя из чата. Убрано.
  document.getElementById("thread-header-search")?.addEventListener("click", () => {
    document.getElementById("thread-inline-search")?.removeAttribute("hidden");
    (document.getElementById("thread-inline-search-input") as HTMLInputElement | null)?.focus();
  });
  document.getElementById("thread-header-call")?.addEventListener("click", () => {
    const peer = activePeerId || activeConvId;
    if (peer) mainNav.push(Screen.call(peer, false));
  });
  document.getElementById("thread-header-video")?.addEventListener("click", () => {
    const peer = activePeerId || activeConvId;
    if (peer) mainNav.push(Screen.call(peer, true));
  });
  document.getElementById("thread-header-more")?.addEventListener("click", () => {
    mainNav.push(Screen.settingsSub("notifications"));
  });

  document.querySelector(".thread-composer__attach")?.addEventListener("click", () => {
    if ((document.getElementById("thread-composer-input") as HTMLTextAreaElement | null)?.disabled)
      return;
    void pickAndSendImage();
  });
  document.getElementById("thread-composer-emoji")?.addEventListener("click", () => {
    if ((document.getElementById("thread-composer-input") as HTMLTextAreaElement | null)?.disabled)
      return;
    openEmojiOverlay();
  });
  document.getElementById("thread-composer-ttl")?.addEventListener("click", () => {
    if ((document.getElementById("thread-composer-input") as HTMLTextAreaElement | null)?.disabled)
      return;
    openEphemeralOverlay();
  });

  document.getElementById("thread-pinned-unpin")?.addEventListener("click", () => {
    setPinnedVisible(false);
  });
  document.getElementById("thread-pinned-bar")?.addEventListener("click", (e) => {
    if ((e.target as HTMLElement).closest("#thread-pinned-unpin")) return;
    const log = document.getElementById("thread-messages");
    log?.scrollTo({ top: 0, behavior: "smooth" });
  });
}

function initThreadHistoryAndReply(): void {
  if (document.body.dataset.kotoThreadHistoryInit === "1") return;
  document.body.dataset.kotoThreadHistoryInit = "1";

  document.getElementById("thread-composer-reply-close")?.addEventListener("click", () => {
    clearPendingReply();
  });

  const container = document.getElementById("thread-messages");
  const sentinel = document.getElementById("thread-history-sentinel");
  if (!container || !sentinel) return;

  const io = new IntersectionObserver(
    (ents) => {
      for (const e of ents) {
        if (!e.isIntersecting) continue;
        if (!threadHistoryHasMore || loadingOlder || !activeConvId) continue;
        void loadOlderThreadPage();
      }
    },
    { root: container, rootMargin: "240px 0px 0px 0px", threshold: 0 },
  );
  io.observe(sentinel);
}

function refreshInlineSearch(query: string): void {
  const q = query.trim().toLowerCase();
  let count = 0;
  document.querySelectorAll(".thread-msg__body").forEach((el) => {
    const text = (el.textContent || "").toLowerCase();
    const on = q.length >= 2 && text.includes(q);
    el.classList.toggle("thread-msg__body--match", on);
    if (on) count += 1;
  });
  const countEl = document.getElementById("thread-inline-search-count");
  if (countEl) countEl.textContent = String(count);
}

async function refreshServerMetaSearchHint(query: string): Promise<void> {
  const q = query.trim();
  if (!activeConvId || q.length < 2) return;
  const status = document.getElementById("thread-status");
  try {
    const res = await searchConversationMessagesMeta(activeConvId, { limit: 20 });
    if (status) status.textContent = res.items.length ? `Серверный мета-поиск: ${res.items.length}` : "";
  } catch {
    /* noop */
  }
}

export function initChatThread(): void {
  initThreadHeaderActions();
  initThreadHistoryAndReply();

  if (document.body.dataset.kotoThreadWs !== "1") {
    document.body.dataset.kotoThreadWs = "1";
    window.addEventListener("koto:ws:new-message", (ev) => {
      const frame = (ev as CustomEvent<{
        conversation_id: string;
        message_id: string;
        sender_id: string;
        ciphertext: string;
        sent_at: number;
        reply_to?: string | null;
      }>).detail;
      if (frame) void appendIncomingFrame(frame);
    });

    window.addEventListener("koto:ws:message-edited", (ev) => {
      const frame = (ev as CustomEvent<{
        conversation_id: string;
        message_id: string;
        sender_id: string;
        ciphertext: string;
      }>).detail;
      if (!frame || frame.conversation_id !== activeConvId) return;
      void applyRemoteEdit(frame);
    });

    window.addEventListener("koto:ws:reaction", (ev) => {
      const frame = (ev as CustomEvent<{
        conversation_id: string;
        message_id: string;
      }>).detail;
      console.log("[reactions] WS frame", frame, "active=", activeConvId);
      if (!frame || !frame.message_id) return;
      // Сбросим кеш для затронутого сообщения. Перерисовка делается только
      // если чат сейчас открыт; в остальных случаях кеш будет загружен лениво
      // при следующем render'е.
      reactionsCache.delete(frame.message_id);
      if (frame.conversation_id !== activeConvId) return;
      const wrap = document.querySelector<HTMLElement>(
        `.thread-msg-wrap[data-msg-id="${cssEscapeId(frame.message_id)}"]`,
      );
      const box = wrap?.querySelector<HTMLElement>(".thread-msg__reactions");
      if (box) {
        const selfId = (loadSession()?.accountId || "").trim();
        void loadAndRenderReactions(frame.message_id, box, selfId);
      }
    });
  }
  if (document.body.dataset.kotoThreadSearchInit !== "1") {
    document.body.dataset.kotoThreadSearchInit = "1";
    const input = document.getElementById("thread-inline-search-input") as HTMLInputElement | null;
    const close = document.getElementById("thread-inline-search-close");
    let timer: ReturnType<typeof setTimeout> | null = null;
    input?.addEventListener("input", () => {
      refreshInlineSearch(input.value || "");
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        void refreshServerMetaSearchHint(input.value || "");
      }, 300);
    });
    close?.addEventListener("click", () => {
      document.getElementById("thread-inline-search")?.setAttribute("hidden", "");
      if (input) input.value = "";
      refreshInlineSearch("");
    });
  }

  document.getElementById("thread-composer-send")?.addEventListener("click", () => {
    void submitOutgoingMessage();
  });

  const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  ta?.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void submitOutgoingMessage();
    }
  });

  let draftTimer: ReturnType<typeof setTimeout> | null = null;
  ta?.addEventListener("input", () => {
    syncComposerSendState();
    autosizeComposer();
    if (!activeConvId) return;
    if (draftTimer) clearTimeout(draftTimer);
    draftTimer = setTimeout(() => {
      const el = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
      if (el && activeConvId) setComposerDraft(activeConvId, el.value || "");
    }, 400);
  });
  syncComposerSendState();
  autosizeComposer();
}

function syncComposerSendState(): void {
  const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  const send = document.getElementById("thread-composer-send");
  if (!send) return;
  const hasText = Boolean(ta && !ta.disabled && ta.value.trim().length > 0);
  send.classList.toggle("thread-composer__send--active", hasText);
  if (ta && !ta.disabled) {
    (send as HTMLButtonElement).disabled = false;
    send.setAttribute("title", hasText ? "Отправить" : "Голосовое сообщение (скоро)");
    send.setAttribute("aria-label", hasText ? "Отправить" : "Голосовое сообщение");
  }
}

function autosizeComposer(): void {
  const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  if (!ta) return;
  ta.style.height = "auto";
  ta.style.height = `${Math.min(ta.scrollHeight, 168)}px`;
}

async function loadOlderThreadPage(): Promise<void> {
  const convId = activeConvId;
  const container = document.getElementById("thread-messages");
  if (!convId || !container || !threadHistoryHasMore || loadingOlder || !threadHistoryOldestId) return;

  loadingOlder = true;
  upsertLoadOlderHint(container, threadHistoryHasMore);

  const prevScrollHeight = container.scrollHeight;
  const prevScrollTop = container.scrollTop;

  try {
    const older = (await fetchConversationMessages(convId, threadHistoryOldestId, PAGE_LIMIT)) as ThreadMessageDto[];
    if (activeConvId !== convId) return;

    if (!older.length) {
      threadHistoryHasMore = false;
      threadHistoryOldestId = null;
      upsertLoadOlderHint(container, false);
      return;
    }

    const chronological = [...older].reverse();
    threadHistoryHasMore = older.length >= PAGE_LIMIT;
    threadHistoryOldestId = chronological[0]?.id || threadHistoryOldestId;

    const session = loadSession();
    const selfId = session?.accountId || "";
    await decryptInboundBatch(chronological, selfId);
    if (activeConvId !== convId) return;

    const domMap = collectPreviewMapFromDom(container);
    const batchMap = buildPreviewById(chronological);
    const previewById = mergePreviewMaps(domMap, batchMap);

    const anchor = container.querySelector(".thread-msg-wrap");
    if (anchor) {
      for (let i = chronological.length - 1; i >= 0; i -= 1) {
        const wrap = createMessageWrap(chronological[i], selfId, previewById);
        container.insertBefore(wrap, anchor);
      }
    } else {
      for (const m of chronological) {
        container.appendChild(createMessageWrap(m, selfId, previewById));
      }
    }

    const delta = container.scrollHeight - prevScrollHeight;
    container.scrollTop = prevScrollTop + delta;

    const inlineInput = document.getElementById("thread-inline-search-input") as HTMLInputElement | null;
    if (inlineInput?.value) refreshInlineSearch(inlineInput.value);

    upsertLoadOlderHint(container, threadHistoryHasMore);
  } catch {
    /* surface only on full reload */
  } finally {
    loadingOlder = false;
    const c = document.getElementById("thread-messages");
    if (c) upsertLoadOlderHint(c, threadHistoryHasMore);
  }
}

async function loadThreadMessages(convId: string, opts?: { reset?: boolean }): Promise<void> {
  const container = document.getElementById("thread-messages");
  const status = document.getElementById("thread-status");
  if (!container) return;

  const reset = opts?.reset !== false;
  if (reset) {
    clearThreadMessageWraps();
    if (status) status.textContent = "";
    threadHistoryHasMore = false;
    threadHistoryOldestId = null;
  }

  // Снапшот из offline-кеша — мгновенно показываем что-то, пока сеть тянется.
  if (reset) {
    const cached = loadThreadFromCache(convId);
    if (cached.length) {
      const session = loadSession();
      const selfId = session?.accountId || "";
      for (const r of cached) {
        if (r.plain) rememberPlaintext(r.id, r.plain);
      }
      const previewById = buildPreviewById(cached);
      for (const r of cached) {
        const dto: ThreadMessageDto = {
          id: r.id,
          ciphertext: r.ciphertext,
          sender_id: r.sender_id,
          sent_at: r.sent_at,
          reply_to: r.reply_to,
        };
        container.appendChild(createMessageWrap(dto, selfId, previewById));
      }
      if (status) status.textContent = "";
    }
  }

  if (reset && status && !container.querySelector(".thread-msg-wrap")) {
    status.textContent = "Загрузка…";
  }

  try {
    const messages = (await fetchConversationMessages(convId, null, PAGE_LIMIT)) as ThreadMessageDto[];
    if (activeConvId !== convId) return;

    const chronological = [...messages].reverse();
    const session = loadSession();
    const selfId = session?.accountId || "";
    await decryptInboundBatch(chronological, selfId);
    if (activeConvId !== convId) return;
    const previewById = buildPreviewById(chronological);

    // Сетевые данные имеют приоритет над snapshot'ом — стираем кеш-нарисованное.
    container.querySelectorAll(".thread-msg-wrap").forEach((n) => n.remove());

    for (const m of chronological) {
      container.appendChild(createMessageWrap(m, selfId, previewById));
    }

    // Сохраняем свежий снимок в offline-кеш.
    saveThreadToCache(
      convId,
      chronological.map<ThreadCacheRow>((m) => ({
        id: m.id || "",
        ciphertext: m.ciphertext || "",
        sender_id: m.sender_id || "",
        sent_at: m.sent_at || 0,
        plain: getPlaintext(m.id || ""),
        reply_to: m.reply_to || undefined,
      }))
    );

    threadHistoryHasMore = messages.length >= PAGE_LIMIT;
    threadHistoryOldestId = chronological[0]?.id || null;

    if (!messages.length && status) {
      status.textContent = "Пока нет сообщений.";
    } else if (status?.textContent === "Загрузка…") {
      status.textContent = "";
    }

    setPinnedVisible(false);
    upsertLoadOlderHint(container, threadHistoryHasMore);

    const inlineInput = document.getElementById("thread-inline-search-input") as HTMLInputElement | null;
    if (inlineInput?.value) refreshInlineSearch(inlineInput.value);

    scrollThreadToBottom("auto");
  } catch (e) {
    if (status) status.textContent = formatInvokeError(e);
    upsertLoadOlderHint(container, false);
  }
}

export async function activateChat(convId: string): Promise<void> {
  activeConvId = convId;
  activeTitle = displayTitle(pendingOpen.title || convId);
  activePeerId = pendingOpen.peerId || "";
  activeOnline = pendingOpen.online;
  pendingOpen = { title: "", peerId: "", online: false };

  // Сбрасываем кеш реакций — иначе при переключении чатов могут «прилететь»
  // чипы из предыдущего conversation'а, или наоборот не обновятся свежими.
  reactionsCache.clear();
  clearPendingReply();

  const titleEl = document.getElementById("thread-title");
  if (titleEl) titleEl.textContent = activeTitle;

  const sub = document.getElementById("thread-subtitle");
  if (sub) sub.textContent = activeOnline ? "в сети" : "не в сети";

  const av = document.getElementById("thread-avatar");
  if (av) {
    av.textContent = initialsFromTitle(activeTitle, convId);
    const key = activePeerId || convId;
    const hue = hashHue(key);
    av.style.background = `hsl(${hue} 46% 42%)`;
    av.classList.toggle("thread-header__avatar--online", activeOnline);
  }

  const status = document.getElementById("thread-status");
  if (status) status.textContent = "Загрузка…";

  setComposerEnabled(true);
  const ta0 = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  const saved = getComposerDraft(convId);
  if (ta0 && saved) ta0.value = saved;
  syncComposerSendState();
  autosizeComposer();

  await loadThreadMessages(convId, { reset: true });
  // Session-style: композер открыт всегда. Pending-чаты прячутся из основного
  // списка (см. chatList.applyFilters), а Accept/Block-логика живёт в
  // боковой панели «Запросы на чат». В самом потоке гейта нет.
  if (status?.textContent === "Загрузка…") status.textContent = "";
}

export function deactivateChat(): void {
  const ta = document.getElementById("thread-composer-input") as HTMLTextAreaElement | null;
  if (activeConvId && ta) {
    setComposerDraft(activeConvId, ta.value || "");
  }
  activeConvId = null;
  activeTitle = "";
  activePeerId = "";
  activeOnline = false;
  clearPendingReply();
  threadHistoryHasMore = false;
  threadHistoryOldestId = null;
  setComposerEnabled(false);
  clearThreadMessageWraps();
  const status = document.getElementById("thread-status");
  if (status) status.textContent = "";
  setPinnedVisible(false);
  document.querySelectorAll(".chatlist-row--active").forEach((el) => {
    el.classList.remove("chatlist-row--active");
  });
}

/** @deprecated Use `mainNav.resetTo(Screen.empty())` — kept for auth/sign-out hooks. */
export function closeThread(): void {
  deactivateChat();
  mainNav.resetTo(Screen.empty());
}

/** Открыть диалог в main pane (как desktop `onOpenChat` → `resetTo(Chat)`). */
export function navigateToChat(
  convId: string,
  title: string,
  peerId: string,
  online: boolean
): void {
  setChatOpenContext(title, peerId, online);
  mainNav.resetTo(Screen.chat(convId));
}

// ─── Edit / Delete / Reactions ──────────────────────────────────────────────

async function startEditingMessage(msgId: string, m: ThreadMessageDto): Promise<void> {
  if (!activeConvId) return;
  const wrap = document.querySelector<HTMLElement>(
    `.thread-msg-wrap[data-msg-id="${cssEscapeId(msgId)}"]`
  );
  if (!wrap) return;
  const body = wrap.querySelector<HTMLElement>(".thread-msg__body");
  if (!body) return;
  const original = decodeCiphertextPreview(m).text || "";
  const editor = document.createElement("textarea");
  editor.className = "thread-msg__edit";
  editor.rows = Math.max(1, Math.min(6, original.split("\n").length));
  editor.value = original;
  editor.style.cssText = "width:100%;resize:none;border-radius:8px;border:none;background:rgba(0,0,0,0.18);color:#fff;padding:6px 8px;font:inherit;outline:none;";
  body.replaceWith(editor);
  editor.focus();
  editor.setSelectionRange(editor.value.length, editor.value.length);

  const finalize = async (commit: boolean): Promise<void> => {
    const newText = editor.value.trim();
    const restored = document.createElement("div");
    restored.className = "thread-msg__body";
    restored.title = body.title || "";
    if (!commit || newText === original.trim() || !newText) {
      restored.textContent = original;
      editor.replaceWith(restored);
      return;
    }
    const peer = (activePeerId || "").trim();
    if (!peer || !activeConvId) {
      restored.textContent = original;
      editor.replaceWith(restored);
      return;
    }
    try {
      const ct = await encryptForPeer(peer, newText);
      await editConversationMessage(activeConvId, msgId, ct);
      rememberPlaintext(msgId, newText);
      restored.textContent = newText;
      editor.replaceWith(restored);
    } catch (e) {
      restored.textContent = original;
      editor.replaceWith(restored);
      const status = document.getElementById("thread-status");
      if (status) status.textContent = formatInvokeError(e);
    }
  };

  editor.addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey) {
      ev.preventDefault();
      void finalize(true);
    } else if (ev.key === "Escape") {
      ev.preventDefault();
      void finalize(false);
    }
  });
  editor.addEventListener("blur", () => void finalize(true));
}

// ─── Media (photo) ──────────────────────────────────────────────────────────
//
// MVP-вариант: файл загружается на MinIO через presigned PUT (без AES-обёртки).
// В сообщении передаётся E2E-зашифрованный JSON-конверт с `fileId` и `mime`.
// Конкретный байтовый контент аватара/фото пока виден тем, у кого есть presigned
// URL — для альфы приемлемо, на TODO — обернуть в AES-GCM с ключом внутри
// конверта (будет client-side encrypt, как Signal media).

async function pickAndSendImage(): Promise<void> {
  if (!activeConvId || !activePeerId) return;
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/*";
  input.style.display = "none";
  document.body.appendChild(input);
  await new Promise<void>((resolve) => {
    input.addEventListener("change", async () => {
      const file = input.files?.[0];
      input.remove();
      if (!file) return resolve();
      const status = document.getElementById("thread-status");
      try {
        if (status) status.textContent = "Загрузка изображения…";
        const { uploadProfileImage } = await import("@/shared/services/profileService");
        const fileId = await uploadProfileImage(file);
        const envelope = JSON.stringify({
          v: 1,
          type: "image",
          fileId,
          mime: file.type || "image/png",
          name: file.name,
          size: file.size,
        });
        const ct = await encryptForPeer(activePeerId, envelope);
        const sent = await sendConversationMessage(activeConvId!, {
          messageType: 2,
          ciphertextBase64: ct,
          clientSeq: Date.now(),
        });
        if (sent?.id) rememberPlaintext(sent.id, envelope);
        await loadThreadMessages(activeConvId!, { reset: true });
        if (status) status.textContent = "";
      } catch (e) {
        if (isNotFoundApiError(e)) {
          // Чат был создан в локальном кеше, но на сервере отсутствует
          // (например, после очистки бэка). Чистим и просим пересоздать.
          if (status) {
            status.textContent =
              "Этот чат больше не существует на сервере. Создайте новый через ✎.";
          }
          void refreshChatList().catch(() => {});
          mainNav.resetTo(Screen.empty());
        } else if (status) {
          status.textContent = formatInvokeError(e);
        }
      }
      resolve();
    }, { once: true });
    input.click();
  });
}

const mediaSrcCache = new Map<string, string>();
async function resolveMediaSrc(fileId: string): Promise<string> {
  if (!fileId) return "";
  const hit = mediaSrcCache.get(fileId);
  if (hit) return hit;
  try {
    const { resolveMediaImageSrc } = await import("@/shared/services/profileService");
    const src = await resolveMediaImageSrc(fileId);
    mediaSrcCache.set(fileId, src);
    return src;
  } catch {
    return "";
  }
}

async function applyRemoteEdit(frame: {
  conversation_id: string;
  message_id: string;
  sender_id: string;
  ciphertext: string;
}): Promise<void> {
  if (!frame.message_id) return;
  // Расшифровать новый ciphertext (если он от пира) и обновить body.
  const selfId = (loadSession()?.accountId || "").trim();
  if (frame.sender_id && frame.sender_id !== selfId) {
    try {
      const plain = await decryptFromPeer(frame.sender_id, frame.ciphertext);
      rememberPlaintext(frame.message_id, plain);
    } catch (e) {
      console.warn("edit decrypt failed", e);
    }
  }
  const wrap = document.querySelector<HTMLElement>(
    `.thread-msg-wrap[data-msg-id="${cssEscapeId(frame.message_id)}"]`
  );
  const body = wrap?.querySelector<HTMLElement>(".thread-msg__body");
  if (!body) return;
  const cached = getPlaintext(frame.message_id);
  if (cached !== undefined) body.textContent = cached;
}

async function reportMessage(msgId: string, m: ThreadMessageDto, selfId: string): Promise<void> {
  if (!activeConvId || !msgId || !m.sender_id) return;
  const consent = confirm(
    "Отправить жалобу на это сообщение?\n\n" +
      "Plaintext этого сообщения и нескольких предыдущих будут переданы " +
      "Koto Trust & Safety для проверки. Это нарушит E2E только для этой жалобы — " +
      "обычные сообщения остаются зашифрованными.",
  );
  if (!consent) return;

  // Собираем контекст: 5 предыдущих сообщений в этом чате.
  const context: Array<{ message_id: string; sender_id: string; plaintext: string; sent_at: number }> = [];
  const wraps = Array.from(
    document.querySelectorAll<HTMLElement>(".thread-msg-wrap[data-msg-id]")
  );
  const idx = wraps.findIndex((w) => w.dataset.msgId === msgId);
  if (idx > 0) {
    const slice = wraps.slice(Math.max(0, idx - 5), idx);
    for (const w of slice) {
      const id = w.dataset.msgId || "";
      const body = w.querySelector(".thread-msg__body");
      const plain = getPlaintext(id) || (body?.textContent || "").trim();
      const isSelf = w.classList.contains("thread-msg-wrap--self");
      context.push({
        message_id: id,
        sender_id: isSelf ? selfId : (m.sender_id || ""),
        plaintext: plain,
        sent_at: 0,
      });
    }
  }

  const plain = getPlaintext(msgId) || decodeCiphertextPreview(m).text || "";

  try {
    const accessToken = loadSession()?.accessToken;
    if (!accessToken) throw new Error("Нет активной сессии");
    const baseUrl =
      (window as { KOTO_BASE_URL?: string }).KOTO_BASE_URL || "http://127.0.0.1:8081";
    const response = await fetch(`${baseUrl}/v1/moderation/report`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        reported_id: m.sender_id,
        conversation_id: activeConvId,
        message_id: msgId,
        reason: "abuse",
        plaintext: plain,
        context,
      }),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const status = document.getElementById("thread-status");
    if (status) {
      status.textContent = "Жалоба отправлена. Спасибо.";
      setTimeout(() => {
        if (status.textContent === "Жалоба отправлена. Спасибо.") status.textContent = "";
      }, 4000);
    }
  } catch (e) {
    const status = document.getElementById("thread-status");
    if (status) status.textContent = `Не удалось отправить жалобу: ${formatInvokeError(e)}`;
  }
}

async function deleteMessage(msgId: string): Promise<void> {
  if (!activeConvId) return;
  if (!confirm("Удалить это сообщение?")) return;
  try {
    await deleteConversationMessage(activeConvId, msgId);
    document
      .querySelector(`.thread-msg-wrap[data-msg-id="${cssEscapeId(msgId)}"]`)
      ?.remove();
  } catch (e) {
    const status = document.getElementById("thread-status");
    if (status) status.textContent = formatInvokeError(e);
  }
}

const QUICK_REACTIONS = ["👍", "❤️", "😂", "😮", "😢", "🙏"];
// Расширенный список — открывается по «+». В будущем заменить на полноценный
// emoji-picker (с категориями/поиском). Пока этот короткий список покрывает
// 95% реальных случаев, как в Signal.
const EXTENDED_REACTIONS = [
  "👍","👎","❤️","🔥","🎉","😂","😮","😢","🙏","👏","🤔","😊",
  "😎","🥰","😘","😅","🤣","😡","🤯","💯","✨","💪","🤝","👀",
  "💔","🙌","😴","🤗","🥺","😇","🤩","🫡","🫶","🤦","🤷","🙃",
];

function openReactionPicker(anchor: HTMLElement, msgId: string): void {
  document.querySelectorAll(".thread-reaction-picker").forEach((n) => n.remove());
  if (!activeConvId) return;
  const picker = document.createElement("div");
  picker.className = "thread-reaction-picker";
  let extended = false;

  const renderRow = (emojis: string[]): void => {
    picker.replaceChildren();
    if (extended) picker.classList.add("thread-reaction-picker--extended");
    else picker.classList.remove("thread-reaction-picker--extended");
    for (const emoji of emojis) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "thread-reaction-picker__btn";
      btn.textContent = emoji;
      btn.addEventListener("click", async () => {
        picker.remove();
        try {
          await toggleMessageReaction(activeConvId!, msgId, emoji);
        } catch (e) {
          console.warn("reaction failed", e);
        }
      });
      picker.appendChild(btn);
    }
    if (!extended) {
      const more = document.createElement("button");
      more.type = "button";
      more.className =
        "thread-reaction-picker__btn thread-reaction-picker__btn--more";
      more.textContent = "+";
      more.title = "Больше эмодзи";
      more.addEventListener("click", (e) => {
        e.stopPropagation();
        extended = true;
        renderRow(EXTENDED_REACTIONS);
        positionPicker();
      });
      picker.appendChild(more);
    }
  };

  const positionPicker = (): void => {
    const r = anchor.getBoundingClientRect();
    const pr = picker.getBoundingClientRect();
    const left = Math.min(window.innerWidth - pr.width - 8, Math.max(8, r.left));
    const top = Math.max(8, r.top - pr.height - 8);
    picker.style.left = `${left}px`;
    picker.style.top = `${top}px`;
  };

  renderRow(QUICK_REACTIONS);
  document.body.appendChild(picker);
  positionPicker();

  const onAway = (ev: MouseEvent) => {
    if (!picker.contains(ev.target as Node)) {
      picker.remove();
      window.removeEventListener("mousedown", onAway);
    }
  };
  setTimeout(() => window.addEventListener("mousedown", onAway), 0);
}
