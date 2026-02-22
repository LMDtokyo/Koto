import { API_URL } from "@/shared/config/constants";
import { Effect, Schedule, pipe } from "effect";
import { jwtDecode } from "jwt-decode";

// ---------------------------------------------------------------------------
// Error types
// ---------------------------------------------------------------------------

export class ApiError {
  readonly _tag = "ApiError";
  constructor(
    readonly status: number,
    readonly code: string,
    readonly message: string,
  ) {}
}

export class NetworkError {
  readonly _tag = "NetworkError";
  constructor(readonly cause: unknown) {}
}

export type AppError = ApiError | NetworkError;

// ---------------------------------------------------------------------------
// Token management (side-effect-free reads; mutations go through store)
// ---------------------------------------------------------------------------

const TOKEN_EXPIRY_MARGIN_MS = 30_000;
const MAX_RATE_LIMIT_RETRIES = 3;

type TokenAccessor = {
  getToken: () => string | null;
  setToken: (token: string) => void;
  clearAuth: () => void;
};

let tokenAccessor: TokenAccessor | null = null;

export function bindTokenAccessor(accessor: TokenAccessor): void {
  tokenAccessor = accessor;
}

function isTokenExpired(token: string): boolean {
  try {
    const { exp } = jwtDecode<{ exp: number }>(token);
    return Date.now() >= exp * 1000 - TOKEN_EXPIRY_MARGIN_MS;
  } catch {
    return true;
  }
}

// ---------------------------------------------------------------------------
// Refresh token — deduplication
// ---------------------------------------------------------------------------

let refreshPromise: Promise<string> | null = null;

function refreshAccessToken(): Effect.Effect<string, AppError> {
  return Effect.tryPromise({
    try: async () => {
      if (!refreshPromise) {
        refreshPromise = (async () => {
          const response = await fetch(`${API_URL}/v1/auth/refresh`, {
            method: "POST",
            credentials: "include",
          });

          if (!response.ok) {
            tokenAccessor?.clearAuth();
            throw new ApiError(401, "TOKEN_EXPIRED", "Session expired");
          }

          const data = (await response.json()) as { access_token: string };
          tokenAccessor?.setToken(data.access_token);
          return data.access_token;
        })().finally(() => {
          refreshPromise = null;
        });
      }
      return refreshPromise;
    },
    catch: (error) => {
      if (error instanceof ApiError) return error;
      return new NetworkError(error);
    },
  });
}

// ---------------------------------------------------------------------------
// Get valid token — proactively refresh if close to expiry
// ---------------------------------------------------------------------------

function getValidToken(): Effect.Effect<string | null, AppError> {
  return Effect.gen(function* () {
    const token = tokenAccessor?.getToken() ?? null;
    if (!token) return null;

    if (!isTokenExpired(token)) return token;

    return yield* refreshAccessToken();
  });
}

// ---------------------------------------------------------------------------
// Core request
// ---------------------------------------------------------------------------

interface RequestOptions {
  method?: string;
  body?: unknown;
  params?: Record<string, string>;
  headers?: Record<string, string>;
  skipAuth?: boolean;
}

function executeRequest<T>(path: string, options: RequestOptions = {}): Effect.Effect<T, AppError> {
  return Effect.gen(function* () {
    const { method = "GET", body, params, headers: customHeaders, skipAuth } = options;

    const url = new URL(`${API_URL}${path}`);
    if (params) {
      for (const [key, value] of Object.entries(params)) {
        url.searchParams.set(key, value);
      }
    }

    const headers = new Headers(customHeaders);
    if (body !== undefined && body !== null) {
      headers.set("Content-Type", "application/json");
    }

    if (!skipAuth) {
      const token = yield* getValidToken();
      if (token) {
        headers.set("Authorization", `Bearer ${token}`);
      }
    }

    let response = yield* Effect.tryPromise({
      try: () =>
        fetch(url, {
          method,
          headers,
          body: body !== undefined && body !== null ? JSON.stringify(body) : undefined,
          credentials: "include",
        }),
      catch: (cause) => new NetworkError(cause),
    });

    // 401 → try refresh once
    if (response.status === 401 && !skipAuth) {
      const newToken = yield* refreshAccessToken();
      headers.set("Authorization", `Bearer ${newToken}`);

      response = yield* Effect.tryPromise({
        try: () =>
          fetch(url, {
            method,
            headers,
            body: body !== undefined && body !== null ? JSON.stringify(body) : undefined,
            credentials: "include",
          }),
        catch: (cause) => new NetworkError(cause),
      });
    }

    // 429 → propagate for retry layer
    if (response.status === 429) {
      return yield* Effect.fail(new ApiError(429, "RATE_LIMITED", "Too many requests"));
    }

    if (!response.ok) {
      const error = yield* Effect.tryPromise({
        try: () =>
          response.json().catch(() => ({
            code: "UNKNOWN",
            message: response.statusText,
          })) as Promise<{ code: string; message: string }>,
        catch: () => new NetworkError("Failed to parse error response"),
      });
      return yield* Effect.fail(new ApiError(response.status, error.code, error.message));
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return yield* Effect.tryPromise({
      try: () => response.json() as Promise<T>,
      catch: (cause) => new NetworkError(cause),
    });
  });
}

// ---------------------------------------------------------------------------
// Public API — wraps executeRequest with 429 retry (exponential backoff, max 3)
// ---------------------------------------------------------------------------

const retrySchedule = pipe(
  Schedule.exponential("1 seconds"),
  Schedule.intersect(Schedule.recurs(MAX_RATE_LIMIT_RETRIES - 1)),
);

const retryPolicy = {
  schedule: retrySchedule,
  while: (error: AppError) => error instanceof ApiError && error.status === 429,
};

export function api<T>(path: string, options: RequestOptions = {}): Effect.Effect<T, AppError> {
  return Effect.retry(executeRequest<T>(path, options), retryPolicy);
}

// ---------------------------------------------------------------------------
// Convenience runners — execute Effect in Promise-land for React
// ---------------------------------------------------------------------------

export function runApi<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return Effect.runPromise(api<T>(path, options));
}
