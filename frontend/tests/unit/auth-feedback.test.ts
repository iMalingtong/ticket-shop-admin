import { describe, expect, it } from "vitest";
import { authFeedback, validPassword } from "../../src/api/auth-feedback";
import { ApiError } from "../../src/utils/http";
describe("authentication feedback", () => {
  it("uses the same credential message without exposing server internals", () => {
    expect(
      authFeedback(new ApiError(401, "INVALID_CREDENTIALS", "AUTHENTICATION"))
    ).toBe("账号或密码不正确");
    expect(
      authFeedback(new Error("synthetic sensitive raw response"))
    ).not.toContain("sensitive");
  });
  it("counts Unicode code points without trim or byte truncation", () => {
    expect(validPassword("🙂".repeat(128))).toBe(true);
    expect(validPassword("🙂".repeat(129))).toBe(false);
    expect(validPassword(" ".repeat(12))).toBe(true);
    expect(validPassword("a".repeat(11))).toBe(false);
  });
});
