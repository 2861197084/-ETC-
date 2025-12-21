package com.etc.flink.model;

import java.io.Serializable;

/**
 * 套牌检测结果
 */
public class ClonePlateAlert implements Serializable {
    private static final long serialVersionUID = 1L;

    private String plateNumber; // 车牌号
    private String checkpointId1; // 第一次出现卡口
    private String checkpointId2; // 第二次出现卡口
    private String checkpointName1; // 卡口名称1
    private String checkpointName2; // 卡口名称2
    private String time1; // 第一次出现时间
    private String time2; // 第二次出现时间
    private long timeDiffSeconds; // 时间差(秒)
    private double confidenceScore; // 置信度

    // Getters and Setters
    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getCheckpointId1() {
        return checkpointId1;
    }

    public void setCheckpointId1(String checkpointId1) {
        this.checkpointId1 = checkpointId1;
    }

    public String getCheckpointId2() {
        return checkpointId2;
    }

    public void setCheckpointId2(String checkpointId2) {
        this.checkpointId2 = checkpointId2;
    }

    public String getCheckpointName1() {
        return checkpointName1;
    }

    public void setCheckpointName1(String checkpointName1) {
        this.checkpointName1 = checkpointName1;
    }

    public String getCheckpointName2() {
        return checkpointName2;
    }

    public void setCheckpointName2(String checkpointName2) {
        this.checkpointName2 = checkpointName2;
    }

    public String getTime1() {
        return time1;
    }

    public void setTime1(String time1) {
        this.time1 = time1;
    }

    public String getTime2() {
        return time2;
    }

    public void setTime2(String time2) {
        this.time2 = time2;
    }

    public long getTimeDiffSeconds() {
        return timeDiffSeconds;
    }

    public void setTimeDiffSeconds(long timeDiffSeconds) {
        this.timeDiffSeconds = timeDiffSeconds;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    @Override
    public String toString() {
        return "ClonePlateAlert{plate='" + plateNumber + "', cp1='" + checkpointName1 +
                "', cp2='" + checkpointName2 + "', timeDiff=" + timeDiffSeconds + "s}";
    }
}
