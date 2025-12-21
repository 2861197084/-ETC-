import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getTimeStatus, startSimulation, type TimeStatus } from '@/api/time'

type SimulatedClockOptions = {
  /**
   * 自动启动模拟时间（若后端当前为暂停状态）
   * 默认: true
   */
  autoStart?: boolean
  /**
   * 本地 UI 刷新间隔（毫秒）
   * 默认: 1000
   */
  tickIntervalMs?: number
  /**
   * 与后端重新对时的间隔（毫秒）
   * 默认: 30000
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
  const { autoStart = true, tickIntervalMs = 1000, resyncIntervalMs = 30000 } = options

  const status = ref<TimeStatus | null>(null)
  const isRunning = ref(false)
  const timeScale = ref(300)

  const baseRealMs = ref(0)
  const baseSimulatedMs = ref(0)
  const nowMs = ref(Date.now())

  let tickTimer: number | null = null
  let resyncTimer: number | null = null

  function applyStatus(next: TimeStatus) {
    status.value = next
    isRunning.value = !!next.isRunning
    timeScale.value = next.timeScale ?? 300
    baseRealMs.value = Date.now()
    baseSimulatedMs.value = next.simulatedTimestamp
  }

  async function syncFromServer() {
    const res = await getTimeStatus()
    if (res.code === 200 && res.data) applyStatus(res.data)
  }

  async function ensureRunning() {
    await syncFromServer()
    if (!autoStart || isRunning.value) return

    const res = await startSimulation()
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
    ensureRunning().catch(() => {})
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
    syncFromServer,
    ensureRunning
  }
}

