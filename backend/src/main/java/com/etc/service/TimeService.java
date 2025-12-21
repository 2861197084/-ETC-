package com.etc.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 时间模拟服务
 * 
 * 模拟时间从 2024-01-01 00:00 开始
 * 每过 1 真实秒 = 系统过 5 分钟 (timeScale = 300)
 */
@Service
public class TimeService {

    // 基准模拟时间: 2024-01-01 00:00:00
    private static final LocalDateTime BASE_SIMULATED_TIME = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

    // 时间缩放比例: 1秒 = 5分钟 = 300秒
    private static final int TIME_SCALE = 300;

    // 真实开始时间戳(毫秒)
    private final AtomicLong realStartTime = new AtomicLong(0);

    // 是否正在运行
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 暂停时的模拟时间偏移(秒)
    private final AtomicLong pausedOffset = new AtomicLong(0);

    /**
     * 获取当前模拟时间
     */
    public LocalDateTime getSimulatedTime() {
        if (!running.get()) {
            // 未运行时，返回基准时间 + 暂停偏移
            return BASE_SIMULATED_TIME.plusSeconds(pausedOffset.get());
        }

        long realElapsedMs = System.currentTimeMillis() - realStartTime.get();
        long simulatedSeconds = (realElapsedMs / 1000) * TIME_SCALE + pausedOffset.get();
        return BASE_SIMULATED_TIME.plusSeconds(simulatedSeconds);
    }

    /**
     * 获取真实时间
     */
    public LocalDateTime getRealTime() {
        return LocalDateTime.now();
    }

    /**
     * 获取时间缩放比例
     */
    public int getTimeScale() {
        return TIME_SCALE;
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 启动模拟
     */
    public void start() {
        if (!running.get()) {
            realStartTime.set(System.currentTimeMillis());
            running.set(true);
        }
    }

    /**
     * 暂停模拟
     */
    public void pause() {
        if (running.get()) {
            // 保存当前模拟时间偏移
            long realElapsedMs = System.currentTimeMillis() - realStartTime.get();
            long simulatedSeconds = (realElapsedMs / 1000) * TIME_SCALE + pausedOffset.get();
            pausedOffset.set(simulatedSeconds);
            running.set(false);
        }
    }

    /**
     * 重置到初始状态
     */
    public void reset() {
        running.set(false);
        realStartTime.set(0);
        pausedOffset.set(0);
    }

    /**
     * 获取模拟时间窗口（用于数据查询）
     * 返回 [windowStart, windowEnd]，表示当前 5 分钟窗口
     */
    public LocalDateTime[] getCurrentWindow() {
        LocalDateTime simTime = getSimulatedTime();
        // 对齐到 5 分钟边界
        int minute = simTime.getMinute();
        int alignedMinute = (minute / 5) * 5;
        LocalDateTime windowStart = simTime.withMinute(alignedMinute).withSecond(0).withNano(0);
        LocalDateTime windowEnd = windowStart.plusMinutes(5);
        return new LocalDateTime[] { windowStart, windowEnd };
    }

    /**
     * 获取基准模拟时间
     */
    public LocalDateTime getBaseTime() {
        return BASE_SIMULATED_TIME;
    }
}
