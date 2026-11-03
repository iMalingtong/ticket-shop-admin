import { describe, expect, it, vi } from "vitest";
import { ApiError, createAdminClient } from "../../src/utils/http";
const envelope = (data: unknown) => ({
  data,
  requestId: "server-request",
  serverNow: "2026-11-03T00:00:00Z"
});
const decode = (value: unknown): { ok: boolean } => {
  if (
    !value ||
    typeof value !== "object" ||
    !("ok" in value) ||
    typeof value.ok !== "boolean"
  )
    throw new Error("invalid");
  return { ok: value.ok };
};
function failure(status: number, code: string) {
  return new Response(
    JSON.stringify({
      error: {
        code,
        message: "synthetic-error",
        details: { currentVersion: "2" }
      },
      requestId: "server-error",
      serverNow: "2026-11-03T00:00:00Z"
    }),
    { status, headers: { "Retry-After": "60" } }
  );
}
describe("admin HTTP boundary", () => {
  it("uses same-origin cookies and explicit CSRF/idempotency on writes", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(envelope({ ok: true }))));
    const client = createAdminClient(fetcher);
    client.setCsrf("a".repeat(43));
    await client.request("/api/v1/admin/users", {
      method: "POST",
      idempotencyKey: "f0000000-0000-4000-8000-000000000000",
      body: { displayName: "合成用户" },
      decode
    });
    const config = fetcher.mock.calls[0][1];
    expect(config.credentials).toBe("same-origin");
    expect(config.headers["X-CSRF-Token"]).toBe("a".repeat(43));
    expect(config.headers["Idempotency-Key"]).toBeDefined();
    expect(config.headers.Authorization).toBeUndefined();
    expect(config.headers.Origin).toBeUndefined();
    expect(fetcher).toHaveBeenCalledTimes(1);
    client.clearCsrf();
    await expect(
      client.request("/api/v1/admin/auth/logout", { method: "POST", decode })
    ).rejects.toMatchObject({ code: "CSRF_MISSING" });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it.each([
    [401, "SESSION_EXPIRED", "AUTHENTICATION"],
    [403, "FORBIDDEN", "PERMISSION"],
    [409, "VERSION_CONFLICT", "CONFLICT"],
    [429, "LOGIN_RATE_LIMITED", "RATE_LIMIT"],
    [503, "SERVICE_UNAVAILABLE", "UNAVAILABLE"]
  ])("separates HTTP %i without retry", async (status, code, expectedKind) => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(failure(Number(status), String(code)));
    await expect(
      createAdminClient(fetcher).request("/api/v1/admin/auth/me", { decode })
    ).rejects.toMatchObject({ kind: expectedKind, requestId: "server-error" });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("rejects malformed successful payloads and missing envelope fields", async () => {
    for (const raw of [
      { data: { ok: true } },
      envelope({ unknown: true }),
      "not-json"
    ]) {
      const fetcher = vi
        .fn()
        .mockResolvedValue(
          new Response(typeof raw === "string" ? raw : JSON.stringify(raw))
        );
      await expect(
        createAdminClient(fetcher).request("/api/v1/admin/auth/me", { decode })
      ).rejects.toBeInstanceOf(ApiError);
    }
  });
  it("does not leak credentials to external URLs or retry connection failures", async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error("offline"));
    const client = createAdminClient(fetcher);
    await expect(
      client.request("https://example.invalid/api/v1/admin/users", { decode })
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
    expect(fetcher).not.toHaveBeenCalled();
    await expect(
      client.request("/api/v1/admin/auth/me", { decode })
    ).rejects.toMatchObject({ kind: "UNAVAILABLE" });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});

it("rejects normalized auth-path escapes before sending a request", async () => {
  const fetcher = vi.fn();
  const client = createAdminClient(fetcher);
  client.setCsrf("a".repeat(43));
  for (const path of [
    "/api/v1/admin/auth/../users",
    "/api/v1/admin/auth/%2e%2e/users",
    "/api/v1/admin/users"
  ]) {
    await expect(
      client.request(path, { method: "POST", body: {}, decode })
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  }
  expect(fetcher).not.toHaveBeenCalled();
});
it("treats a status/error-code mismatch as an invalid response", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(failure(401, "SERVICE_UNAVAILABLE"));
  await expect(
    createAdminClient(fetcher).request("/api/v1/admin/auth/me", { decode })
  ).rejects.toMatchObject({ kind: "UNAVAILABLE", code: "INVALID_RESPONSE" });
});
