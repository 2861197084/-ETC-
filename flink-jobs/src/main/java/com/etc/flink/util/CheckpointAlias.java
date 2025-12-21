package com.etc.flink.util;

import java.util.Map;

public final class CheckpointAlias {

    private CheckpointAlias() {}

    private static final Map<String, String> RAW_KKMC_TO_ID = Map.ofEntries(
            // 苏皖界（省际）
            Map.entry("徐州市睢宁县G104北京-福州K873江苏徐州-G104-苏皖界省际卡口", "CP001"),
            Map.entry("徐州市铜山县G311徐州-西峡K207江苏徐州-G311-苏皖界省际卡口", "CP002"),
            Map.entry("徐州市睢宁县S252塔双线K56江苏徐州-S252-苏皖界省际卡口", "CP003"),
            Map.entry("徐州市铜山县G206烟台-汕头K816江苏徐州-G206-苏皖界省际卡口", "CP004"),
            Map.entry("徐州市丰县G518518国道K358马楼公路站省际卡口", "CP005"),
            Map.entry("徐州市丰县G237国道237线K148荣庄卡口省际卡口", "CP006"),

            // 苏鲁界（省际）
            Map.entry("徐州市沛县S253郑沛龙线K0江苏徐州-S253-苏鲁界省际卡口", "CP007"),
            Map.entry("徐州市铜山县G104北京-福州K744江苏徐州-G104-苏鲁界省际卡口", "CP008"),
            Map.entry("G3京台高速K731江苏高速五大队江苏徐州-G3-苏鲁界省际卡口", "CP009"),
            Map.entry("徐州市邳州市S250宿邳线K1江苏徐州-S250-苏鲁界省际卡口", "CP010"),
            Map.entry("徐州市邳州市S251枣睢线K5江苏徐州-S251-苏鲁界省际卡口", "CP011"),
            Map.entry("江苏省徐州市新沂市S323连徐线K96瓦窑检查站市际卡口", "CP012"),

            // 连云港界（市际）
            Map.entry("徐州市新沂市S323连徐线K10阿湖卡口-323省道连云港交界市际卡口", "CP013"),
            Map.entry("徐州市铜山县G310连云港-天水K310江苏徐州-G310-苏皖界省际卡口", "CP014"),

            // 宿迁界（市际）
            Map.entry("徐州市新沂市S505505省道K10新沂高速西出口-505省道宿迁界市际卡口", "CP015"),
            Map.entry("江苏省徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口", "CP016"),
            Map.entry("徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口", "CP016"),
            Map.entry("徐州市睢宁县S324燕沭睢线K201省道桑庄王马路路口西侧-向东卡口市际卡口", "CP017"),
            Map.entry("徐州市新沂市G235国道235K10江苏徐州-G235-交界市际卡口", "CP018"),
            Map.entry("徐州市丰县鹿梁路K19丰县梁寨检查站市际卡口", "CP019")
    );

    public static String checkpointIdByRawName(String rawKkmc) {
        if (rawKkmc == null) return null;
        return RAW_KKMC_TO_ID.get(rawKkmc.trim());
    }
}

