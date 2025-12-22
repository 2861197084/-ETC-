package com.etc.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间服务
 * 
 * 使用真实时间，固定使用北京时区 (UTC+8)
 */
@Service
public class TimeService {

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 获取当前时间（北京时间）
     */
    public LocalDateTime getSimulatedTime() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    /**
     * 获取真实时间（北京时间）
     */
    public LocalDateTime getRealTime() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    /**
     * 获取时间缩放比例（实时=1）
     */
    public int getTimeScale() {
        return 1;
    }

    /**
     * 是否正在运行（始终运行）
     */
    public boolean isRunning() {
        return true;
    }

    /**
     * 启动（无操作）
     */
    public void start() {
        // 实时模式无需启动
    }

    /**
     * 暂停（无操作）
     */
    public void pause() {
        // 实时模式不支持暂停
    }

    /**
     * 重置（无操作）
     */
    public void reset() {
        // 实时模式无需重置
    }

    /**
     * 获取时间窗口（用于数据查询）
     * 返回 [windowStart, windowEnd]，表示当前 5 分钟窗口
     */
    public LocalDateTime[] getCurrentWindow() {
        LocalDateTime now = LocalDateTime.now();
        // 对齐到 5 分钟边界
        int minute = now.getMinute();
        int alignedMinute = (minute / 5) * 5;
        LocalDateTime windowStart = now.withMinute(alignedMinute).withSecond(0).withNano(0);
        LocalDateTime windowEnd = windowStart.plusMinutes(5);
        return new LocalDateTime[] { windowStart, windowEnd };
    }

    /**
     * 获取基准时间（当前时间）
     */
    public LocalDateTime getBaseTime() {
        return LocalDateTime.now();
    }
}
