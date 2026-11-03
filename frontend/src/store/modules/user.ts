import { defineStore } from "pinia";
import { store } from "@/store";
import router from "@/router";
import type { AdminSummary } from "@/api/contracts";
import { http, ApiError } from "@/utils/http";
import {
  readCurrentAdministrator,
  rotateCsrf,
  signIn,
  signOut
} from "@/api/administrator";

let restoration: Promise<void> | null = null;
export const useUserStore = defineStore("pure-user", {
  state: () => ({
    identity: null as AdminSummary | null,
    phase: "ANONYMOUS" as "ANONYMOUS" | "NORMAL" | "PASSWORD_CHANGE",
    restored: false,
    unavailable: false,
    avatar: "",
    username: "",
    nickname: "",
    roles: [] as string[],
    permissions: [] as string[]
  }),
  actions: {
    applyIdentity(identity: AdminSummary) {
      this.identity = identity;
      this.phase = "NORMAL";
      this.username = identity.username;
      this.nickname = identity.displayName;
      this.roles = [identity.role];
      this.permissions = [...identity.permissions];
    },
    clearIdentity() {
      this.$reset();
      this.restored = true;
      http.clearCsrf();
    },
    async restoreIdentity() {
      if (this.restored) return;
      if (restoration) return restoration;
      restoration = (async () => {
        try {
          this.applyIdentity((await readCurrentAdministrator()).data);
          await rotateCsrf();
        } catch (error) {
          if (
            error instanceof ApiError &&
            error.code === "PASSWORD_CHANGE_REQUIRED"
          ) {
            this.phase = "PASSWORD_CHANGE";
            try {
              await rotateCsrf();
            } catch {
              this.clearIdentity();
              this.unavailable = true;
            }
          } else {
            this.clearIdentity();
            if (!(error instanceof ApiError) || error.status !== 401)
              this.unavailable = true;
          }
        } finally {
          this.restored = true;
          restoration = null;
        }
      })();
      return restoration;
    },
    async login(username: string, password: string) {
      const result = await signIn(username, password);
      this.clearIdentity();
      http.setCsrf(result.csrfToken);
      if (result.nextAction === "NONE")
        this.applyIdentity(result.administrator);
      else this.phase = "PASSWORD_CHANGE";
      return result.nextAction;
    },
    async logOut() {
      // A failed server logout must not be presented as completed revocation.
      await signOut();
      this.clearIdentity();
      await router.replace("/login");
    }
  }
});
export const useUserStoreHook = () => useUserStore(store);
