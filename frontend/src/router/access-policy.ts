import type { Permission } from "../api/contracts";
export type IdentityPhase = "ANONYMOUS" | "PASSWORD_CHANGE" | "NORMAL";
export type AccessDecision = "ALLOW" | "LOGIN" | "FIRST_PASSWORD" | "FORBIDDEN";
export type RouteAccess = {
  public?: boolean;
  passwordChangeOnly?: boolean;
  permissions?: readonly Permission[];
};

/** Default-deny policy used by P3 identity guards; P6 adds business route permissions. */
export function decideAccess(
  route: RouteAccess,
  phase: IdentityPhase,
  permissions: readonly string[]
): AccessDecision {
  if (route.public) return "ALLOW";
  if (phase === "ANONYMOUS") return "LOGIN";
  if (phase === "PASSWORD_CHANGE")
    return route.passwordChangeOnly ? "ALLOW" : "FIRST_PASSWORD";
  if (route.passwordChangeOnly) return "FORBIDDEN";
  return route.permissions?.length &&
    route.permissions.every(permission => permissions.includes(permission))
    ? "ALLOW"
    : "FORBIDDEN";
}
