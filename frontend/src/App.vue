<script setup lang="ts">
import { NConfigProvider, NMessageProvider, darkTheme } from 'naive-ui'

// 自定义主题覆盖
const themeOverrides = {
  common: {
    primaryColor: '#00d4ff',
    primaryColorHover: '#33dfff',
    primaryColorPressed: '#00a8cc',
    successColor: '#00ff88',
    warningColor: '#ffaa00',
    errorColor: '#ff6b35',
    infoColor: '#8b5cf6',
  },
}
</script>

<template>
  <NConfigProvider :theme="darkTheme" :theme-overrides="themeOverrides">
    <NMessageProvider>
      <router-view />
    </NMessageProvider>
  </NConfigProvider>
</template>

<style scoped>
</style>
