import {
  errorCode400Values,
  errorCode401Values,
  errorCode403Values,
  errorCode404Values,
  errorCode409Values,
  errorCode426Values,
  errorCode429Values,
  errorCode503Values
} from "../../api/generated/schema";
import {
  utcMicros,
  type ApiEnvelope,
  type ErrorCode,
  type Schema
} from "../../api/contracts";

type FailureKind =
  | "VALIDATION"
  | "AUTHENTICATION"
  | "PERMISSION"
  | "NOT_FOUND"
  | "CONFLICT"
  | "UPGRADE"
  | "RATE_LIMIT"
  | "UNAVAILABLE";
type LocalError =
  | "NETWORK_UNAVAILABLE"
  | "INVALID_RESPONSE"
  | "CSRF_MISSING"
  | "INVALID_REQUEST";
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: ErrorCode | LocalError,
    public readonly kind: FailureKind,
    public readonly requestId?: string,
    public readonly details?: Schema<"ErrorDetails">,
    public readonly retryAfterSeconds?: number
  ) {
    // Never retain raw responses or request bodies in an error that a logging tool could inspect.
    super(code);
  }
}
const allowedCodes: Readonly<Record<number, readonly string[]>> = {
  400: errorCode400Values,
  401: errorCode401Values,
  403: errorCode403Values,
  404: errorCode404Values,
  409: errorCode409Values,
  426: errorCode426Values,
  429: errorCode429Values,
  503: errorCode503Values
};
const kind = (status: number): FailureKind =>
  (
    ({
      400: "VALIDATION",
      401: "AUTHENTICATION",
      403: "PERMISSION",
      404: "NOT_FOUND",
      409: "CONFLICT",
      426: "UPGRADE",
      429: "RATE_LIMIT"
    }) as Partial<Record<number, FailureKind>>
  )[status] ?? "UNAVAILABLE";
const record = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);
function metadata(
  v: unknown
): v is Record<string, unknown> & { requestId: string; serverNow: string } {
  if (
    !record(v) ||
    typeof v.requestId !== "string" ||
    !/^[A-Za-z0-9._-]{1,64}$/.test(v.requestId) ||
    typeof v.serverNow !== "string"
  )
    return false;
  try {
    utcMicros(v.serverNow);
    return true;
  } catch {
    return false;
  }
}
export type RequestOptions<T> = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  idempotencyKey?: string;
  decode: (data: unknown) => T;
};

export function createAdminClient(
  fetcher: typeof fetch = (input, init) => fetch(input, init)
) {
  let csrf: string | undefined;
  return {
    setCsrf(value: string) {
      if (!/^[A-Za-z0-9_-]{43}$/.test(value))
        throw new ApiError(0, "INVALID_RESPONSE", "UNAVAILABLE");
      csrf = value;
    },
    clearCsrf() {
      csrf = undefined;
    },
    async request<T>(
      path: string,
      options: RequestOptions<T>
    ): Promise<ApiEnvelope<T>> {
      if (!/^\/api\/v1\/admin\/[A-Za-z0-9/?=&:._%+\-]+$/.test(path))
        throw new ApiError(0, "INVALID_REQUEST", "VALIDATION");
      const pathname = path.split("?")[0];
      if (
        pathname.includes("%") ||
        pathname.split("/").some(part => part === "." || part === "..")
      )
        throw new ApiError(0, "INVALID_REQUEST", "VALIDATION");
      const method = options.method ?? "GET";
      if (method === "GET" && options.body !== undefined)
        throw new ApiError(0, "INVALID_REQUEST", "VALIDATION");
      const headers: Record<string, string> = {
        Accept: "application/json",
        "X-Request-Id": crypto.randomUUID()
      };
      if (method !== "GET") {
        if (!csrf) throw new ApiError(0, "CSRF_MISSING", "PERMISSION");
        headers["X-CSRF-Token"] = csrf;
        if (!path.startsWith("/api/v1/admin/auth/")) {
          if (
            !options.idempotencyKey ||
            !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
              options.idempotencyKey
            )
          )
            throw new ApiError(0, "INVALID_REQUEST", "VALIDATION");
          headers["Idempotency-Key"] = options.idempotencyKey;
        }
      }
      if (options.body !== undefined)
        headers["Content-Type"] = "application/json";
      let response: Response;
      try {
        response = await fetcher(path, {
          method,
          headers,
          body:
            options.body === undefined
              ? undefined
              : JSON.stringify(options.body),
          credentials: "same-origin",
          cache: "no-store",
          redirect: "error",
          signal: AbortSignal.timeout(10000)
        });
      } catch {
        throw new ApiError(
          0,
          "NETWORK_UNAVAILABLE",
          "UNAVAILABLE",
          headers["X-Request-Id"]
        );
      }
      let raw: unknown;
      try {
        raw = await response.json();
      } catch {
        throw new ApiError(
          response.status,
          "INVALID_RESPONSE",
          "UNAVAILABLE",
          headers["X-Request-Id"]
        );
      }
      if (!metadata(raw))
        throw new ApiError(
          response.status,
          "INVALID_RESPONSE",
          "UNAVAILABLE",
          headers["X-Request-Id"]
        );
      if (!response.ok) {
        if (
          !record(raw) ||
          !record(raw.error) ||
          typeof raw.error.code !== "string" ||
          !allowedCodes[response.status]?.includes(raw.error.code) ||
          typeof raw.error.message !== "string" ||
          raw.error.message.length < 1 ||
          raw.error.message.length > 500
        )
          throw new ApiError(
            response.status,
            "INVALID_RESPONSE",
            "UNAVAILABLE",
            raw.requestId
          );
        // Only retain safe conflict/rate details; never attach arbitrary server JSON.
        const details: { currentVersion?: string } = {};
        if (
          record(raw.error.details) &&
          typeof raw.error.details.currentVersion === "string" &&
          /^(0|[1-9][0-9]{0,18})$/.test(raw.error.details.currentVersion)
        )
          details.currentVersion = raw.error.details.currentVersion;
        if (
          typeof window !== "undefined" &&
          path !== "/api/v1/admin/auth/login" &&
          path !== "/api/v1/admin/auth/me" &&
          response.status === 401 &&
          raw.error.code !== "INVALID_CREDENTIALS"
        )
          window.dispatchEvent(new CustomEvent("tsa:authentication-expired"));
        const retry = Number(response.headers.get("Retry-After"));
        throw new ApiError(
          response.status,
          raw.error.code as ErrorCode,
          kind(response.status),
          raw.requestId,
          details,
          Number.isInteger(retry) && retry > 0 ? retry : undefined
        );
      }
      if (!record(raw) || !("data" in raw))
        throw new ApiError(
          response.status,
          "INVALID_RESPONSE",
          "UNAVAILABLE",
          raw.requestId
        );
      try {
        return {
          data: options.decode(raw.data),
          requestId: raw.requestId,
          serverNow: raw.serverNow
        };
      } catch {
        throw new ApiError(
          response.status,
          "INVALID_RESPONSE",
          "UNAVAILABLE",
          raw.requestId
        );
      }
    }
  };
}
export const http = createAdminClient();
