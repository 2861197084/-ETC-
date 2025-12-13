<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  title: string
  value: number
  color?: string
  icon?: string
  trend?: number
}>()

// 数字滚动动画
const displayValue = ref(0)

watch(() => props.value, (newVal) => {
  animateValue(displayValue.value, newVal, 1000)
}, { immediate: true })

function animateValue(start: number, end: number, duration: number) {
  const startTime = performance.now()
  const diff = end - start
  
  function update(currentTime: number) {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    
    // 缓动函数
    const easeOut = 1 - Math.pow(1 - progress, 3)
    displayValue.value = Math.floor(start + diff * easeOut)
    
    if (progress < 1) {
      requestAnimationFrame(update)
    }
  }
  
  requestAnimationFrame(update)
}

const formattedValue = computed(() => {
  return displayValue.value.toLocaleString()
})

const borderColor = computed(() => props.color || '#00d4ff')
</script>

<template>
  <div class="kpi-card" :style="{ '--accent-color': borderColor }">
    <div class="kpi-icon" v-if="icon">{{ icon }}</div>
    <div class="kpi-content">
      <div class="kpi-value">{{ formattedValue }}</div>
      <div class="kpi-title">{{ title }}</div>
    </div>
    <div class="kpi-glow"></div>
  </div>
</template>

<style lang="scss" scoped>
.kpi-card {
  flex: 1;
  background: rgba(6, 30, 61, 0.6);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 8px;
  padding: 15px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 60%;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--accent-color), transparent);
  }
  
  &:hover {
    border-color: var(--accent-color);
    transform: translateY(-2px);
    
    .kpi-glow {
      opacity: 1;
    }
  }
}

.kpi-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.kpi-content {
  text-align: center;
}

.kpi-value {
  font-family: 'DIN Alternate', 'Helvetica Neue', sans-serif;
  font-size: 28px;
  font-weight: bold;
  color: var(--accent-color);
  line-height: 1.2;
  text-shadow: 0 0 20px rgba(0, 212, 255, 0.3);
}

.kpi-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 6px;
  letter-spacing: 1px;
}

.kpi-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse at center,
    rgba(0, 212, 255, 0.1) 0%,
    transparent 70%
  );
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}
</style>

