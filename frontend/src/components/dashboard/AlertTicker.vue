<script setup lang="ts">
import type { Alert } from '@/composables/useDashboardData'

defineProps<{
  alerts: Alert[]
}>()

const getStatusClass = (status: string) => {
  switch (status) {
    case 'danger': return 'status-danger'
    case 'warning': return 'status-warning'
    default: return 'status-normal'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'danger': return '严重'
    case 'warning': return '警告'
    default: return '正常'
  }
}
</script>

<template>
  <div class="alert-ticker">
    <div class="alert-list">
      <div 
        v-for="(alert, index) in alerts" 
        :key="alert.id"
        class="alert-item"
        :class="getStatusClass(alert.status)"
      >
        <div class="alert-header">
          <span class="alert-index">{{ index + 1 }}</span>
          <span class="alert-station">站口名称：{{ alert.stationName }}</span>
          <span class="alert-value" :class="getStatusClass(alert.status)">
            流量告警值：{{ alert.alertValue }}
          </span>
        </div>
        <div class="alert-body">
          <span class="alert-location">地址：{{ alert.location }}</span>
          <span class="alert-time">时间：{{ alert.time }}</span>
        </div>
        <div class="alert-footer">
          <span class="alert-content">报警内容：{{ alert.content }}</span>
          <span class="alert-status" :class="getStatusClass(alert.status)">
            {{ getStatusText(alert.status) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.alert-ticker {
  height: 100%;
  overflow: hidden;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 100%;
  overflow-y: auto;
  padding-right: 5px;
  
  &::-webkit-scrollbar {
    width: 4px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 212, 255, 0.3);
    border-radius: 2px;
  }
}

.alert-item {
  background: rgba(6, 30, 61, 0.5);
  border: 1px solid rgba(0, 212, 255, 0.15);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  transition: all 0.3s ease;
  
  &:hover {
    background: rgba(6, 30, 61, 0.8);
    border-color: rgba(0, 212, 255, 0.3);
  }
  
  &.status-warning {
    border-left: 3px solid #ffaa00;
  }
  
  &.status-danger {
    border-left: 3px solid #ff6b35;
  }
  
  &.status-normal {
    border-left: 3px solid #00ff88;
  }
}

.alert-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.alert-index {
  width: 18px;
  height: 18px;
  background: rgba(0, 212, 255, 0.2);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  color: #00d4ff;
}

.alert-station {
  color: #00d4ff;
  font-weight: 500;
}

.alert-value {
  margin-left: auto;
  
  &.status-warning {
    color: #ffaa00;
  }
  
  &.status-danger {
    color: #ff6b35;
  }
  
  &.status-normal {
    color: #00ff88;
  }
}

.alert-body {
  display: flex;
  gap: 15px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 6px;
}

.alert-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alert-content {
  color: rgba(255, 255, 255, 0.7);
}

.alert-status {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  
  &.status-warning {
    background: rgba(255, 170, 0, 0.2);
    color: #ffaa00;
  }
  
  &.status-danger {
    background: rgba(255, 107, 53, 0.2);
    color: #ff6b35;
  }
  
  &.status-normal {
    background: rgba(0, 255, 136, 0.2);
    color: #00ff88;
  }
}
</style>

