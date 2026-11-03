<script setup lang="ts">
import { ref, computed } from "vue";
import { checkReadiness, type ServiceState } from "@/api/health";
const state = ref<ServiceState>();
const loading = ref(false);
const label = computed(() =>
  state.value === "UP"
    ? "基础服务已就绪"
    : state.value === "DOWN"
      ? "依赖尚未就绪"
      : state.value === "UNAVAILABLE"
        ? "暂时无法连接服务"
        : "尚未检查连接"
);
async function check() {
  loading.value = true;
  try {
    state.value = await checkReadiness();
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="p-6">
    <h1 class="text-2xl mb-4">票小理管理平台</h1>
    <p class="mb-6 text-[var(--el-text-color-secondary)]">基础环境检查</p>
    <el-alert
      title="账号与管理功能尚未开放"
      description="当前用于检查本机账号服务、MySQL与Redis是否就绪，不代表已获得管理权限。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-card shadow="never" class="mt-6">
      <h2 class="text-lg mb-4">服务连接</h2>
      <p role="status" aria-live="polite" class="mb-4">{{ label }}</p>
      <el-button type="primary" :loading="loading" @click="check"
        >检查连接</el-button
      >
      <el-button @click="$router.push('/login')">登录入口</el-button>
    </el-card>
  </section>
</template>
