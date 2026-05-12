/**
 * Разбор ошибок IPC: команды с `Result<_, KotoApiError>` отдают `{ status, message }`.
 */
export function parseKotoApiError(err: unknown): { status: number | null; message: string } {
  if (
    err &&
    typeof err === "object" &&
    "status" in err &&
    "message" in err &&
    typeof (err as { status: unknown }).status === "number" &&
    typeof (err as { message: unknown }).message === "string"
  ) {
    return {
      status: (err as { status: number }).status,
      message: (err as { message: string }).message,
    };
  }
  const raw =
    err && typeof err === "object" && "message" in err ? (err as { message: unknown }).message : err;
  if (typeof raw === "string") {
    try {
      const j = JSON.parse(raw) as { status?: unknown; message?: unknown };
      if (j && typeof j.status === "number" && typeof j.message === "string") {
        return { status: j.status, message: j.message };
      }
    } catch {
      /* ignore */
    }
    return { status: null, message: raw };
  }
  return { status: null, message: String(err ?? "") };
}

export function isUnauthorizedApiError(err: unknown): boolean {
  return parseKotoApiError(err).status === 401;
}

/** Сообщение для UI (не `[object Object]` при структурированной ошибке). */
export function formatInvokeError(err: unknown): string {
  return parseKotoApiError(err).message;
}
