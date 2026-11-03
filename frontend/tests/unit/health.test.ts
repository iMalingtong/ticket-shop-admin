import { afterEach, describe, expect, it, vi } from "vitest";
import { checkReadiness } from "../../src/api/health";
afterEach(() => vi.unstubAllGlobals());
describe("readiness response boundaries", () => {
  it("only accepts a successful UP response", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ status: "UP" }), { status: 200 })
        )
    );
    expect(await checkReadiness()).toBe("UP");
  });
  it("distinguishes dependency failure from connection failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ status: "DOWN" }), { status: 503 })
        )
    );
    expect(await checkReadiness()).toBe("DOWN");
  });
  it("does not treat invalid JSON or a failed request as readiness", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("not json", { status: 200 }))
    );
    expect(await checkReadiness()).toBe("UNAVAILABLE");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("connection refused"))
    );
    expect(await checkReadiness()).toBe("UNAVAILABLE");
  });
});
