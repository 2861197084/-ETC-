<template>
  <div class="clone-plate-alert">
    <!-- 警报弹窗 -->
    <el-dialog
      v-model="modalVisible"
      title="🚨 套牌车检测警报"
      width="600px"
      :close-on-click-modal="false"
      center
      class="clone-plate-modal"
    >
      <div v-if="currentAlert" class="alert-content">
        <div class="alert-header">
          <div class="plate-number">{{ currentAlert.plateNumber }}</div>
          <el-tag :type="getConfidenceColor(currentAlert.confidence)">
            置信度: {{ (currentAlert.confidence * 100).toFixed(0) }}%
          </el-tag>
        </div>
        
        <el-divider />
        
        <div class="detection-info">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="检测时间">
              {{ currentAlert.detectionTime }}
            </el-descriptions-item>
            <el-descriptions-item label="第一次出现">
              <div class="checkpoint-info">
                <span class="name">{{ currentAlert.checkpoint1Name }}</span>
                <span class="time">{{ currentAlert.checkpoint1Time }}</span>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="第二次出现">
              <div class="checkpoint-info">
                <span class="name">{{ currentAlert.checkpoint2Name }}</span>
                <span class="time">{{ currentAlert.checkpoint2Time }}</span>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="两点距离">
              {{ currentAlert.distance }} 公里
            </el-descriptions-item>
            <el-descriptions-item label="时间间隔">
              {{ currentAlert.timeDiff }} 分钟
            </el-descriptions-item>
            <el-descriptions-item label="推算时速">
              <span class="speed-warning">{{ currentAlert.calculatedSpeed?.toFixed(0) }} km/h</span>
              <span class="speed-note">（物理上不可能）</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
        
        <div class="alert-actions">
          <el-button type="danger" @click="confirmAlert">
            确认套牌
          </el-button>
          <el-button @click="dismissAlert">
            误报处理
          </el-button>
          <el-button type="primary" link @click="viewDetails">
            查看详情
          </el-button>
        </div>
      </div>
    </el-dialog>
    
    <!-- 已移除右下角角标，套牌告警现在显示在右侧 AlertTicker 列表中 -->
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import { getClonePlates, handleClonePlate } from '@/api/admin/realtime'

interface ClonePlateAlert {
  id: number
  plateNumber: string
  detectionTime: string
  checkpoint1Id: number
  checkpoint1Name: string
  checkpoint1Time: string
  checkpoint2Id: number
  checkpoint2Name: string
  checkpoint2Time: string
  distance: number
  timeDiff: number
  calculatedSpeed: number
  confidence: number
  status: number
}

const modalVisible = ref(false)
const currentAlert = ref<ClonePlateAlert | null>(null)
const lastCheckId = ref(0)

let pollTimer: ReturnType<typeof setInterval> | null = null

// 获取置信度颜色 (Element Plus tag type)
function getConfidenceColor(confidence: number): 'danger' | 'warning' | 'info' {
  if (confidence >= 0.9) return 'danger'
  if (confidence >= 0.7) return 'warning'
  return 'info'
}

// 轮询检查新的套牌检测
async function pollClonePlates() {
  try {
    const res = await getClonePlates({ status: '0', page: 1, pageSize: 10 })
    // API 返回格式: { code, msg, data: { list, total } }
    const list = res.data?.list || res.list || []
    
    if (list.length > 0) {
      // 找出新的警报
      const newAlerts = list.filter((item: any) => item.id > lastCheckId.value)
      
      if (newAlerts.length > 0) {
        // 更新最后检查的ID
        lastCheckId.value = Math.max(...list.map((item: any) => item.id))
        
        // 显示最新的弹窗（套牌车告警也会通过 AlertTicker 显示在右侧列表中）
        currentAlert.value = newAlerts[0]
        modalVisible.value = true
        
        // 播放提示音
        playAlertSound()
        
        console.log('🚨 检测到套牌车:', newAlerts.length, '条新记录')
      }
    }
  } catch (error) {
    console.error('轮询套牌检测失败:', error)
  }
}

// 播放警报声音
function playAlertSound() {
  try {
    const audio = new Audio('/alert.mp3')
    audio.volume = 0.5
    audio.play().catch(() => {})
  } catch {}
}

// 确认套牌
async function confirmAlert() {
  if (!currentAlert.value) return
  try {
    await handleClonePlate(String(currentAlert.value.id), { status: 'confirmed' })
    ElMessage.success('已确认为套牌车')
    closeModal()
  } catch {
    ElMessage.error('操作失败')
  }
}

// 误报处理
async function dismissAlert() {
  if (!currentAlert.value) return
  try {
    await handleClonePlate(String(currentAlert.value.id), { status: 'dismissed' })
    ElMessage.info('已标记为误报')
    closeModal()
  } catch {
    ElMessage.error('操作失败')
  }
}

// 关闭弹窗
function closeModal() {
  modalVisible.value = false
  currentAlert.value = null
}

// 查看详情
function viewDetails() {
  // 跳转到套牌检测详情页
  modalVisible.value = false
}

// 初始化：获取当前最大ID，避免历史数据全部弹出
async function initLastCheckId() {
  try {
    const res = await getClonePlates({ status: '0', page: 1, pageSize: 1 })
    const list = res.data?.list || res.list || []
    if (list.length > 0) {
      lastCheckId.value = list[0].id
      console.log('📌 初始化 lastCheckId:', lastCheckId.value)
    }
  } catch (error) {
    console.error('初始化失败:', error)
  }
}

onMounted(async () => {
  // 先初始化最大ID
  await initLastCheckId()
  
  // 每5秒轮询一次
  pollTimer = setInterval(pollClonePlates, 5000)
  console.log('🔄 套牌检测轮询已启动')
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})
</script>

<style scoped lang="scss">
.clone-plate-alert {
  .alert-content {
    .alert-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      
      .plate-number {
        font-size: 28px;
        font-weight: bold;
        color: #f5222d;
        font-family: 'Courier New', monospace;
        letter-spacing: 2px;
      }
    }
    
    .detection-info {
      margin: 16px 0;
      
      .checkpoint-info {
        display: flex;
        flex-direction: column;
        
        .name {
          font-weight: 500;
        }
        
        .time {
          font-size: 12px;
          color: #666;
        }
      }
      
      .speed-warning {
        color: #f5222d;
        font-weight: bold;
        font-size: 18px;
      }
      
      .speed-note {
        margin-left: 8px;
        color: #999;
        font-size: 12px;
      }
    }
    
    .alert-actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      margin-top: 24px;
    }
  }
}

:deep(.clone-plate-modal) {
  .el-dialog__header {
    background: linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%);
    
    .el-dialog__title {
      color: #fff;
    }
  }
}
</style>
