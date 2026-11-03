import { useUserStoreHook } from "@/store/modules/user";
import { reportActivity } from "./administrator";
import router from "@/router";

export function installAuthenticationEvents() {
  let lastReported = -Infinity,
    sending = false,
    redirecting = false;
  window.addEventListener("tsa:authentication-expired", () => {
    const user = useUserStoreHook();
    if (redirecting || user.phase === "ANONYMOUS") return;
    redirecting = true;
    user.clearIdentity();
    void router.replace("/login").finally(() => {
      redirecting = false;
    });
  });
  async function activity(event: Event) {
    const user = useUserStoreHook();
    if (
      !event.isTrusted ||
      document.visibilityState !== "visible" ||
      user.phase !== "NORMAL" ||
      sending ||
      performance.now() - lastReported < 60000
    )
      return;
    sending = true;
    lastReported = performance.now();
    try {
      const result = (await reportActivity()).data;
      if (user.identity) user.identity = { ...user.identity, ...result };
    } catch {
      /* No background retry. Authentication failures use the single expiration event. */
    } finally {
      sending = false;
    }
  }
  window.addEventListener("pointerdown", activity, { passive: true });
  window.addEventListener("keydown", activity);
}
