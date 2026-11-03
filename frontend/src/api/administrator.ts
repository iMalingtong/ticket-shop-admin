import { permissionValues } from "./generated/schema";
const knownPermissions = new Set<string>(permissionValues);
import { http } from "../utils/http";
import { id, utcMicros, type AdminSummary, type Schema } from "./contracts";
const object = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);
export function decodeAdminSummary(value: unknown): AdminSummary {
  if (
    !object(value) ||
    typeof value.id !== "string" ||
    typeof value.username !== "string" ||
    typeof value.displayName !== "string" ||
    !/^[A-Za-z0-9._-]{3,64}$/.test(value.username) ||
    [...value.displayName].length < 1 ||
    [...value.displayName].length > 64 ||
    (value.role !== "SUPER_ADMIN" && value.role !== "OPERATOR") ||
    !Array.isArray(value.permissions) ||
    !value.permissions.every(
      p => typeof p === "string" && knownPermissions.has(p)
    ) ||
    typeof value.sessionExpiresAt !== "string" ||
    typeof value.idleExpiresAt !== "string"
  )
    throw new Error("Invalid administrator summary");
  id(value.id);
  utcMicros(value.sessionExpiresAt);
  utcMicros(value.idleExpiresAt);
  return {
    id: value.id,
    username: value.username,
    displayName: value.displayName,
    role: value.role as AdminSummary["role"],
    permissions: value.permissions as AdminSummary["permissions"],
    sessionExpiresAt: value.sessionExpiresAt,
    idleExpiresAt: value.idleExpiresAt
  };
}
// Typed authentication adapters used by login, restoration and real activity reporting.
export const readCurrentAdministrator = () =>
  http.request("/api/v1/admin/auth/me", { decode: decodeAdminSummary });
export async function rotateCsrf(): Promise<Schema<"CsrfToken">> {
  const result = await http.request("/api/v1/admin/auth/csrf", {
    decode(value): Schema<"CsrfToken"> {
      if (
        !object(value) ||
        typeof value.csrfToken !== "string" ||
        !/^[A-Za-z0-9_-]{43}$/.test(value.csrfToken) ||
        typeof value.expiresAt !== "string"
      )
        throw new Error("Invalid CSRF response");
      utcMicros(value.expiresAt);
      return { csrfToken: value.csrfToken, expiresAt: value.expiresAt };
    }
  });
  http.setCsrf(result.data.csrfToken);
  return result.data;
}

export type AdminLoginResult = Schema<"AdminLoginResult">;
function loginResult(value: unknown): AdminLoginResult {
  if (
    !object(value) ||
    typeof value.csrfToken !== "string" ||
    !/^[A-Za-z0-9_-]{43}$/.test(value.csrfToken)
  )
    throw new Error("Invalid login response");
  if (
    value.nextAction === "CHANGE_PASSWORD" &&
    typeof value.changeExpiresAt === "string"
  ) {
    utcMicros(value.changeExpiresAt);
    return {
      nextAction: "CHANGE_PASSWORD",
      changeExpiresAt: value.changeExpiresAt,
      csrfToken: value.csrfToken
    };
  }
  if (value.nextAction === "NONE")
    return {
      nextAction: "NONE",
      administrator: decodeAdminSummary(value.administrator),
      csrfToken: value.csrfToken
    };
  throw new Error("Invalid login response");
}
export async function signIn(username: string, password: string) {
  await rotateCsrf();
  const result = await http.request("/api/v1/admin/auth/login", {
    method: "POST",
    body: { username, password },
    decode: loginResult
  });
  http.setCsrf(result.data.csrfToken);
  return result.data;
}
function loginNext(value: unknown): Schema<"LoginNext"> {
  if (
    !object(value) ||
    value.nextAction !== "LOGIN" ||
    (value.cacheSyncStatus !== "SYNCED" && value.cacheSyncStatus !== "PENDING")
  )
    throw new Error("Invalid password response");
  return { nextAction: "LOGIN", cacheSyncStatus: value.cacheSyncStatus };
}
export const firstPassword = (newPassword: string) =>
  http.request("/api/v1/admin/auth/first-password", {
    method: "POST",
    body: { newPassword },
    decode: loginNext
  });
export const changeOwnPassword = (
  currentPassword: string,
  newPassword: string
) =>
  http.request("/api/v1/admin/auth/password", {
    method: "POST",
    body: { currentPassword, newPassword },
    decode: loginNext
  });
export const signOut = () =>
  http.request("/api/v1/admin/auth/logout", {
    method: "POST",
    decode(value): Schema<"LogoutResult"> {
      if (
        !object(value) ||
        value.revoked !== true ||
        (value.cacheSyncStatus !== "SYNCED" &&
          value.cacheSyncStatus !== "PENDING")
      )
        throw new Error("Invalid logout response");
      return { revoked: true, cacheSyncStatus: value.cacheSyncStatus };
    }
  });
export const reportActivity = () =>
  http.request("/api/v1/admin/auth/activity", {
    method: "POST",
    decode(value): Schema<"ActivityResult"> {
      if (
        !object(value) ||
        typeof value.sessionExpiresAt !== "string" ||
        typeof value.idleExpiresAt !== "string"
      )
        throw new Error("Invalid activity response");
      utcMicros(value.sessionExpiresAt);
      utcMicros(value.idleExpiresAt);
      return {
        sessionExpiresAt: value.sessionExpiresAt,
        idleExpiresAt: value.idleExpiresAt
      };
    }
  });
