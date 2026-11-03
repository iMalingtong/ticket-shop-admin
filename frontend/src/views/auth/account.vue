<script setup lang="ts">
import { ref } from "vue";
import { useUserStoreHook } from "@/store/modules/user";
import { authFeedback } from "@/api/auth-feedback";
const user = useUserStoreHook(),
  busy = ref(false),
  error = ref("");
async function logout() {
  if (busy.value) return;
  busy.value = true;
  try {
    await user.logOut();
  } catch (e) {
    error.value = authFeedback(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="p-8 max-w-xl mx-auto">
    <h1 class="text-2xl mb-4">当前管理员</h1>
    <p>账号：{{ user.identity?.username }}</p>
    <p>名称：{{ user.identity?.displayName }}</p>
    <p>
      角色：{{
        user.identity?.role === "SUPER_ADMIN" ? "超级管理员" : "运营管理员"
      }}
    </p>
    <p class="my-4">客户管理功能将在后续阶段开放。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <el-button @click="$router.push('/password')">修改本人密码</el-button>
    <el-button type="primary" :loading="busy" @click="logout"
      >退出登录</el-button
    >
    <el-button @click="$router.push('/welcome')">环境检查</el-button>
  </main>
</template>
