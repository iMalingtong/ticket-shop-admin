<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { firstPassword, changeOwnPassword } from "@/api/administrator";
import { authFeedback, validPassword } from "@/api/auth-feedback";
import { useUserStoreHook } from "@/store/modules/user";
const router = useRouter(),
  route = useRoute(),
  user = useUserStoreHook();
const first = computed(() => route.path === "/first-password");
const current = ref(""),
  password = ref(""),
  confirmation = ref(""),
  busy = ref(false),
  error = ref("");
async function submit() {
  if (busy.value) return;
  if (
    !validPassword(password.value) ||
    password.value !== confirmation.value ||
    (!first.value && !validPassword(current.value))
  ) {
    error.value = "密码须为12–128个字符，两次新密码须一致";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (first.value) await firstPassword(password.value);
    else await changeOwnPassword(current.value, password.value);
    current.value = password.value = confirmation.value = "";
    user.clearIdentity();
    await router.replace({ path: "/login", query: { passwordChanged: "1" } });
  } catch (e) {
    error.value = authFeedback(e);
  } finally {
    busy.value = false;
  }
}
onBeforeUnmount(() => {
  current.value = password.value = confirmation.value = "";
});
</script>
<template>
  <main class="p-8 max-w-xl mx-auto">
    <h1 class="text-2xl mb-4">
      {{ first ? "首次登录修改密码" : "修改本人密码" }}
    </h1>
    <p>请设置12–128个字符的新密码。成功后所有旧会话失效，需要重新登录。</p>
    <p v-if="error" role="alert" class="my-4">{{ error }}</p>
    <el-form class="mt-6" label-position="top" @submit.prevent="submit">
      <el-form-item v-if="!first" label="当前密码" for="current-password"
        ><el-input
          id="current-password"
          v-model="current"
          type="password"
          show-password
          autocomplete="current-password"
          :disabled="busy"
      /></el-form-item>
      <el-form-item label="新密码" for="new-password"
        ><el-input
          id="new-password"
          v-model="password"
          type="password"
          show-password
          autocomplete="new-password"
          :disabled="busy"
      /></el-form-item>
      <el-form-item label="确认新密码" for="confirm-password"
        ><el-input
          id="confirm-password"
          v-model="confirmation"
          type="password"
          show-password
          autocomplete="new-password"
          :disabled="busy"
      /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="busy"
        >确认修改</el-button
      >
      <el-button v-if="!first" :disabled="busy" @click="router.push('/account')"
        >返回账号</el-button
      >
      <el-button
        v-else
        :disabled="busy"
        @click="user.logOut().catch(e => (error = authFeedback(e)))"
        >退出登录</el-button
      >
    </el-form>
  </main>
</template>
