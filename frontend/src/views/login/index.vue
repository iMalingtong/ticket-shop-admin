<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStoreHook } from "@/store/modules/user";
import { authFeedback, validPassword } from "@/api/auth-feedback";
const router = useRouter(),
  user = useUserStoreHook();
const username = ref(""),
  password = ref(""),
  busy = ref(false),
  error = ref("");
async function submit() {
  if (busy.value) return;
  if (
    !/^[A-Za-z0-9._-]{3,64}$/.test(username.value) ||
    !validPassword(password.value)
  ) {
    error.value = "请输入正确账号及12–128个字符的密码";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (user.unavailable) {
      user.restored = false;
      await user.restoreIdentity();
      if (user.phase !== "ANONYMOUS") {
        await router.replace(
          user.phase === "NORMAL" ? "/account" : "/first-password"
        );
        return;
      }
    }
    const action = await user.login(username.value, password.value);
    password.value = "";
    await router.replace(
      action === "CHANGE_PASSWORD" ? "/first-password" : "/account"
    );
  } catch (e) {
    error.value = authFeedback(e);
  } finally {
    busy.value = false;
  }
}
onBeforeUnmount(() => {
  password.value = "";
});
</script>
<template>
  <main class="p-8 max-w-xl mx-auto">
    <h1 class="text-2xl mb-4">票小理管理平台</h1>
    <p class="mb-4">管理员登录</p>
    <el-alert
      v-if="$route.query.passwordChanged"
      title="密码已修改，请使用新密码登录"
      type="success"
      :closable="false"
    />
    <el-alert
      v-if="user.unavailable"
      title="身份恢复暂不可用，请稍后重新登录"
      type="warning"
      :closable="false"
    />
    <p v-if="error" role="alert" class="my-4">{{ error }}</p>
    <el-form class="mt-6" label-position="top" @submit.prevent="submit">
      <el-form-item label="账号" for="login-username"
        ><el-input
          id="login-username"
          v-model="username"
          autocomplete="username"
          :disabled="busy"
          :maxlength="64"
      /></el-form-item>
      <el-form-item label="密码" for="login-password"
        ><el-input
          id="login-password"
          v-model="password"
          type="password"
          show-password
          autocomplete="current-password"
          :disabled="busy"
      /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="busy"
        >登录</el-button
      >
      <el-button @click="router.push('/welcome')">返回环境检查</el-button>
    </el-form>
  </main>
</template>
