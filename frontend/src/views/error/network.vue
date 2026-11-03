<template>
  <el-result
    icon="warning"
    title="连接异常"
    sub-title="暂时无法连接账号服务，请检查本机服务和网络后重试。"
  >
    <template #extra
      ><el-button type="primary" @click="$router.push('/welcome')"
        >返回环境检查</el-button
      ></template
    >
  </el-result>
</template>
