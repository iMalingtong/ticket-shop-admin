import { test, expect } from "@playwright/test";
test("foundation shell exposes real empty login without demo credentials", async ({
  page
}) => {
  await page.goto("/welcome");
  await expect(
    page.getByRole("heading", { name: "票小理管理平台" })
  ).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("尚未检查连接");
  await page.getByRole("button", { name: "检查连接" }).click();
  await expect(page.getByRole("status")).not.toHaveText("尚未检查连接");
  await page.getByRole("button", { name: "登录入口", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "登录", exact: true })
  ).toBeEnabled();
  await expect(page.getByLabel("账号")).toHaveValue("");
  expect(
    await page.evaluate(() =>
      Object.keys(localStorage).filter(key => /token|user-info/.test(key))
    )
  ).toEqual([]);
});
test("unknown routes and network feedback remain usable", async ({ page }) => {
  await page.goto("/does-not-exist");
  await expect(page.getByText("404", { exact: true })).toBeVisible();
  await page.goto("/network-error");
  await expect(page.getByText("连接异常", { exact: true })).toBeVisible();
});

test("theme survives reload without browser errors", async ({
  page
}, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto("/welcome");
  await expect(page.getByRole("heading", { name: "服务连接" })).toBeVisible();
  await page.getByRole("button", { name: "检查连接", exact: true }).click();
  await expect(page.getByRole("status")).not.toHaveText("尚未检查连接");
  await page.screenshot({
    path: testInfo.outputPath("light.png"),
    fullPage: true,
    animations: "disabled"
  });
  await page.locator('[title="打开系统配置"]').click();
  await page.getByText("深色", { exact: true }).click();
  await expect(page.locator("html")).toHaveClass(/dark/);
  await page.reload();
  await expect(page.getByRole("heading", { name: "服务连接" })).toBeVisible();
  await expect(page.locator("html")).toHaveClass(/dark/);
  await page.screenshot({
    path: testInfo.outputPath("dark.png"),
    fullPage: true,
    animations: "disabled"
  });
  expect(errors).toEqual([]);
});
