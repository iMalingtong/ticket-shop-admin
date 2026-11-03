import { describe, expect, it } from "vitest";
import {
  exclusiveDayEnd,
  id,
  version,
  requirePage,
  requireValidity,
  OneTimeSecret
} from "../../src/api/contracts";

describe("public scalar and dialog contracts", () => {
  it("keeps large IDs and versions as strings and rejects unsafe ranges", () => {
    expect(id("9007199254740993")).toBe("9007199254740993");
    expect(version("0")).toBe("0");
    for (const value of ["0", "01", "-1", "9223372036854775808", "1.0"])
      expect(() => id(value)).toThrow();
    expect(() => requirePage(0, 10)).toThrow();
    expect(() => requirePage(1, 200)).toThrow();
  });
  it("enforces strict UTC and microsecond-exclusive validity", () => {
    requireValidity(
      "2026-11-03T00:00:00.000001Z",
      "2026-11-03T00:00:00.000002Z"
    );
    expect(() =>
      requireValidity("2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z")
    ).toThrow();
    expect(() =>
      requireValidity("2026-02-30T00:00:00Z", "2026-12-01T00:00:00Z")
    ).toThrow();
    expect(() =>
      requireValidity("2026-11-03T08:00:00+08:00", "2026-12-01T00:00:00Z")
    ).toThrow();
  });
  it("uses next local midnight across daylight-saving boundaries", () => {
    expect(exclusiveDayEnd("2026-11-03", "Asia/Shanghai")).toBe(
      "2026-11-03T16:00:00.000Z"
    );
    expect(exclusiveDayEnd("2026-03-08", "America/New_York")).toBe(
      "2026-03-09T04:00:00.000Z"
    );
    expect(exclusiveDayEnd("2026-03-07", "America/New_York")).toBe(
      "2026-03-08T05:00:00.000Z"
    );
    expect(() => exclusiveDayEnd("2026-02-30", "UTC")).toThrow();
  });
  it("drops a one-time secret on close", () => {
    const value = new OneTimeSecret();
    value.show("synthetic-value-only");
    expect(value.read()).toBeDefined();
    expect(JSON.stringify(value)).toBe("{}");
    value.close();
    expect(value.read()).toBeUndefined();
  });
});

describe("route policy stays separate from server authorization", () => {
  it("denies unclassified business routes and isolates password-change identity", async () => {
    const { decideAccess } = await import("../../src/router/access-policy");
    expect(decideAccess({}, "NORMAL", ["admin:create"])).toBe("FORBIDDEN");
    expect(
      decideAccess({ permissions: ["customer:read"] }, "ANONYMOUS", [])
    ).toBe("LOGIN");
    expect(
      decideAccess({ permissions: ["customer:read"] }, "PASSWORD_CHANGE", [])
    ).toBe("FIRST_PASSWORD");
    expect(
      decideAccess({ passwordChangeOnly: true }, "PASSWORD_CHANGE", [])
    ).toBe("ALLOW");
    expect(
      decideAccess({ permissions: ["admin:create"] }, "NORMAL", [
        "customer:read"
      ])
    ).toBe("FORBIDDEN");
    expect(
      decideAccess({ permissions: ["customer:read"] }, "NORMAL", [
        "customer:read"
      ])
    ).toBe("ALLOW");
  });
});
