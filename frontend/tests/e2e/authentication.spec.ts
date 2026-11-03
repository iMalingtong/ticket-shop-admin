import { test, expect } from "@playwright/test";
// Executed only by the isolated Java/Testcontainers fixture. No shared database account is used.
test("real administrator first password, restore, own password and logout", async ({
  page,
  context
}, info) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto("/account");
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel("账号", { exact: true }).fill("SyntheticAdmin");
  await page
    .getByLabel("密码", { exact: true })
    .fill("SyntheticWrongPassword-2026");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("账号或密码不正确");
  await expect(page.getByLabel("账号", { exact: true })).toHaveValue(
    "SyntheticAdmin"
  );
  await page
    .getByLabel("密码", { exact: true })
    .fill("SyntheticInitialPassword-2026");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page).toHaveURL(/\/first-password$/);
  await page.goto("/account");
  await expect(page).toHaveURL(/\/first-password$/);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "首次登录修改密码" })
  ).toBeVisible();
  await page
    .getByLabel("新密码", { exact: true })
    .fill("SyntheticReplacementPassword-2026");
  await page
    .getByLabel("确认新密码", { exact: true })
    .fill("SyntheticReplacementPassword-2026");
  await page.getByRole("button", { name: "确认修改", exact: true }).click();
  await expect(page).toHaveURL(/\/login\?passwordChanged=1$/);
  await page.getByLabel("账号", { exact: true }).fill("SyntheticAdmin");
  await page
    .getByLabel("密码", { exact: true })
    .fill("SyntheticReplacementPassword-2026");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByRole("heading", { name: "当前管理员" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "当前管理员" })).toBeVisible();
  const cookies = await context.cookies();
  const session = cookies.find(c => c.name === "tsa_admin_session");
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe("Strict");
  expect(await page.evaluate(() => document.cookie)).not.toContain(
    "tsa_admin_session"
  );
  expect(
    await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage })
    )
  ).not.toMatch(/Synthetic|tsa_admin_session|csrfToken|eyJ/);
  await page.screenshot({
    path: info.outputPath("authenticated.png"),
    fullPage: true
  });
  await page.getByRole("button", { name: "修改本人密码", exact: true }).click();
  await page
    .getByLabel("当前密码", { exact: true })
    .fill("SyntheticReplacementPassword-2026");
  await page
    .getByLabel("新密码", { exact: true })
    .fill("SyntheticThirdPassword-2026");
  await page
    .getByLabel("确认新密码", { exact: true })
    .fill("SyntheticThirdPassword-2026");
  await page.getByRole("button", { name: "确认修改", exact: true }).click();
  await expect(page).toHaveURL(/\/login\?passwordChanged=1$/);
  await page.getByLabel("账号", { exact: true }).fill("SyntheticAdmin");
  await page
    .getByLabel("密码", { exact: true })
    .fill("SyntheticThirdPassword-2026");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByRole("heading", { name: "当前管理员" })).toBeVisible();
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto("/account");
  await expect(page).toHaveURL(/\/login$/);
  expect(
    (await context.cookies()).find(c => c.name === "tsa_admin_session")
  ).toBeUndefined();
  expect(errors).toEqual([]);
});
