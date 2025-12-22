import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getTimeStatus, type TimeStatus } from '@/api/time'

type SimulatedClockOptions = {
  /**
   * 本地 UI 刷新间隔（毫秒）
   * 默认: 1000
   */
  tickIntervalMs?: number
  /**
   * 与后端重新对时的间隔（毫秒）
   * 默认: 5000（实时模式下5秒同步一次）
   */
  resyncIntervalMs?: number
}

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
})

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  weekday: 'long'
})

export function useSimulatedClock(options: SimulatedClockOptions = {}) {
  const { tickIntervalMs = 1000, resyncIntervalMs = 5000 } = options

  const status = ref<TimeStatus | null>(null)
  const isRunning = ref(true) // 实时模式始终运行
  const timeScale = ref(1) // 实时模式 1:1

  const baseRealMs = ref(0)
  const baseSimulatedMs = ref(0)
  const nowMs = ref(Date.now())

  let tickTimer: number | null = null
  let resyncTimer: number | null = null

  function applyStatus(next: TimeStatus) {
    status.value = next
    isRunning.value = true // 始终运行
    timeScale.value = next.timeScale ?? 1
    baseRealMs.value = Date.now()
    baseSimulatedMs.value = next.simulatedTimestamp
  }

  async function syncFromServer() {
    const res = await getTimeStatus()
    if (res.code === 200 && res.data) applyStatus(res.data)
  }

  const simulatedNowMs = computed(() => {
    if (!status.value) return null
    if (!isRunning.value) return baseSimulatedMs.value
    const deltaRealMs = nowMs.value - baseRealMs.value
    return baseSimulatedMs.value + deltaRealMs * timeScale.value
  })

  const simulatedDate = computed(() => {
    if (simulatedNowMs.value == null) return null
    return new Date(simulatedNowMs.value)
  })

  const timeText = computed(() => (simulatedDate.value ? timeFormatter.format(simulatedDate.value) : '--:--:--'))
  const dateText = computed(() => (simulatedDate.value ? dateFormatter.format(simulatedDate.value) : ''))

  onMounted(() => {
    syncFromServer().catch(() => {})
    tickTimer = window.setInterval(() => {
      nowMs.value = Date.now()
    }, tickIntervalMs)
    resyncTimer = window.setInterval(() => {
      syncFromServer().catch(() => {})
    }, resyncIntervalMs)
  })

  onUnmounted(() => {
    if (tickTimer) window.clearInterval(tickTimer)
    if (resyncTimer) window.clearInterval(resyncTimer)
    tickTimer = null
    resyncTimer = null
  })

  return {
    status,
    isRunning,
    simulatedDate,
    timeText,
    dateText,
    syncFromServer
  }
}

