const PREFIX = "koto.composerDraft.v1:";

export function composerDraftKey(convId: string): string {
  return `${PREFIX}${convId.trim()}`;
}

export function getComposerDraft(convId: string): string {
  try {
    return sessionStorage.getItem(composerDraftKey(convId)) || "";
  } catch {
    return "";
  }
}

export function setComposerDraft(convId: string, text: string): void {
  try {
    const t = text.trim();
    if (!t) sessionStorage.removeItem(composerDraftKey(convId));
    else sessionStorage.setItem(composerDraftKey(convId), text);
  } catch {
    /* private mode / quota */
  }
  window.dispatchEvent(new CustomEvent("koto:composer-draft-updated"));
}

export function clearComposerDraft(convId: string): void {
  try {
    sessionStorage.removeItem(composerDraftKey(convId));
  } catch {
    /* noop */
  }
  window.dispatchEvent(new CustomEvent("koto:composer-draft-updated"));
}
