import { defineConfig } from "@playwright/test";
export default defineConfig({
  testIgnore:
    process.env.P3_BROWSER === "true" ? undefined : "**/authentication.spec.ts",
  testDir: "./tests/e2e",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://127.0.0.1:8848",
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined
  },
  webServer: process.env.PLAYWRIGHT_EXTERNAL_SERVER
    ? undefined
    : {
        command: "pnpm dev",
        url: "http://127.0.0.1:8848",
        reuseExistingServer: !process.env.CI
      }
});
