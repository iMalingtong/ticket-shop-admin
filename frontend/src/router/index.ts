import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw
} from "vue-router";
import { decideAccess } from "./access-policy";
import { useUserStoreHook } from "@/store/modules/user";
import home from "./modules/home";
import remaining from "./modules/remaining";
import { usePermissionStoreHook } from "@/store/modules/permission";

export const constantRoutes = [home] as RouteRecordRaw[];
export const constantMenus = [home, ...remaining];
export const remainingPaths = remaining.map(route => route.path);
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    home,
    ...remaining,
    {
      path: "/:pathMatch(.*)*",
      name: "PageNotFound",
      component: () => import("@/views/error/404.vue"),
      meta: { title: "页面不存在", showLink: false }
    }
  ] as RouteRecordRaw[],
  scrollBehavior: () => ({ top: 0 })
});
router.beforeEach(async to => {
  document.title = `${String(to.meta.title ?? "环境检查")} | 票小理管理平台`;
  const protectedRoute = ["/account", "/password", "/first-password"].includes(
    to.path
  );
  if (!protectedRoute && to.path !== "/login") return;
  const user = useUserStoreHook();
  await user.restoreIdentity();
  if (to.path === "/login") {
    if (user.phase === "PASSWORD_CHANGE") return "/first-password";
    if (user.phase === "NORMAL") return "/account";
    return;
  }
  const decision = decideAccess(
    to.path === "/first-password"
      ? { passwordChangeOnly: true }
      : { permissions: ["dashboard:read"] },
    user.phase,
    user.permissions
  );
  if (decision === "LOGIN") return "/login";
  if (decision === "FIRST_PASSWORD") return "/first-password";
  if (decision === "FORBIDDEN")
    return user.phase === "NORMAL" && to.path === "/first-password"
      ? "/account"
      : "/access-denied";
});
export function resetLoadedPaths() {}
export function resetRouter() {
  usePermissionStoreHook().clearAllCachePage();
}
export default router;
