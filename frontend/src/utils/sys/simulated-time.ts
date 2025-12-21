import { getTimeStatus, startSimulation } from '@/api/time'

/**
 * 确保后端模拟时间处于运行状态（用于演示场景）。
 * 失败时静默处理，避免影响页面加载。
 */
export async function ensureSimulatedTimeRunning() {
  try {
    const res = await getTimeStatus()
    if (res.code === 200 && res.data && !res.data.isRunning) {
      await startSimulation()
    }
  } catch {
    // ignore
  }
}

