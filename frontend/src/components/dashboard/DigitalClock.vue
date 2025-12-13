<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const currentTime = ref('')
const currentDate = ref('')

function updateTime() {
  const now = new Date()
  
  // 格式化时间
  currentTime.value = now.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
  
  // 格式化日期
  currentDate.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short'
  })
}

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<template>
  <div class="digital-clock">
    <span class="date">{{ currentDate }}</span>
    <span class="time">{{ currentTime }}</span>
    <span class="refresh-icon">🔄</span>
  </div>
</template>

<style lang="scss" scoped>
.digital-clock {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: rgba(0, 212, 255, 0.05);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 6px;
}

.date {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.time {
  font-family: 'DIN Alternate', monospace;
  font-size: 18px;
  font-weight: bold;
  color: #00d4ff;
  letter-spacing: 2px;
}

.refresh-icon {
  font-size: 14px;
  animation: rotate 3s linear infinite;
  opacity: 0.6;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>

