package com.etc.flink.model;

import java.io.Serializable;

/**
 * 过车记录
 */
public class PassRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String hp; // 车牌号
    private String gcsj; // 过车时间
    private String gcxh; // 过车序号
    private String xzqhmc; // 行政区划
    private String kkmc; // 卡口名称
    private String fxlx; // 方向
    private String hpzl; // 号牌种类
    private String clppxh; // 车辆品牌型号
    private String checkpointId; // 卡口ID

    // Getters and Setters
    public String getHp() {
        return hp;
    }

    public void setHp(String hp) {
        this.hp = hp;
    }

    public String getGcsj() {
        return gcsj;
    }

    public void setGcsj(String gcsj) {
        this.gcsj = gcsj;
    }

    public String getGcxh() {
        return gcxh;
    }

    public void setGcxh(String gcxh) {
        this.gcxh = gcxh;
    }

    public String getXzqhmc() {
        return xzqhmc;
    }

    public void setXzqhmc(String xzqhmc) {
        this.xzqhmc = xzqhmc;
    }

    public String getKkmc() {
        return kkmc;
    }

    public void setKkmc(String kkmc) {
        this.kkmc = kkmc;
    }

    public String getFxlx() {
        return fxlx;
    }

    public void setFxlx(String fxlx) {
        this.fxlx = fxlx;
    }

    public String getHpzl() {
        return hpzl;
    }

    public void setHpzl(String hpzl) {
        this.hpzl = hpzl;
    }

    public String getClppxh() {
        return clppxh;
    }

    public void setClppxh(String clppxh) {
        this.clppxh = clppxh;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    @Override
    public String toString() {
        return "PassRecord{hp='" + hp + "', kkmc='" + kkmc + "', gcsj='" + gcsj + "'}";
    }
}
