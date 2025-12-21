package com.etc.common;

import java.util.Map;

/**
 * 固定 19 个出市卡口的“展示用”名称/区县映射。
 *
 * <p>用于：
 * <ul>
 *   <li>确保演示环境中卡口名称稳定</li>
 *   <li>兜底处理 MySQL 初始化脚本字符集不一致导致的乱码</li>
 * </ul>
 */
public final class CheckpointCatalog {

    private CheckpointCatalog() {}

    private static final Map<String, String> CODE_TO_NAME =
            Map.ofEntries(
                    Map.entry("CP001", "苏皖界1(104省道)"),
                    Map.entry("CP002", "苏皖界2(311国道)"),
                    Map.entry("CP003", "苏皖界3(徐明高速)"),
                    Map.entry("CP004", "苏皖界4(宿新高速)"),
                    Map.entry("CP005", "苏皖界5(徐淮高速)"),
                    Map.entry("CP006", "苏皖界6(新扬高速)"),
                    Map.entry("CP007", "苏鲁界1(206国道)"),
                    Map.entry("CP008", "苏鲁界2(104国道)"),
                    Map.entry("CP009", "苏鲁界3(京台高速)"),
                    Map.entry("CP010", "苏鲁界4(枣庄连接线)"),
                    Map.entry("CP011", "苏鲁界5(京沪高速)"),
                    Map.entry("CP012", "苏鲁界6(沂河路)"),
                    Map.entry("CP013", "连云港界1(徐连高速)"),
                    Map.entry("CP014", "连云港界2(310国道)"),
                    Map.entry("CP015", "宿迁界1(徐宿高速)"),
                    Map.entry("CP016", "宿迁界2(徐宿快速)"),
                    Map.entry("CP017", "宿迁界3(104国道)"),
                    Map.entry("CP018", "宿迁界4(新扬高速)"),
                    Map.entry("CP019", "宿迁界5(徐盐高速)"));

    private static final Map<String, String> NAME_TO_CODE =
            CODE_TO_NAME.entrySet().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private static final Map<String, String> CODE_TO_DISTRICT =
            Map.ofEntries(
                    Map.entry("CP001", "睢宁县"),
                    Map.entry("CP002", "铜山区"),
                    Map.entry("CP003", "铜山区"),
                    Map.entry("CP004", "睢宁县"),
                    Map.entry("CP005", "沛县"),
                    Map.entry("CP006", "新沂市"),
                    Map.entry("CP007", "沛县"),
                    Map.entry("CP008", "邳州市"),
                    Map.entry("CP009", "贾汪区"),
                    Map.entry("CP010", "邳州市"),
                    Map.entry("CP011", "邳州市"),
                    Map.entry("CP012", "新沂市"),
                    Map.entry("CP013", "邳州市"),
                    Map.entry("CP014", "邳州市"),
                    Map.entry("CP015", "铜山区"),
                    Map.entry("CP016", "铜山区"),
                    Map.entry("CP017", "睢宁县"),
                    Map.entry("CP018", "睢宁县"),
                    Map.entry("CP019", "睢宁县"));

    private static final Map<String, String> CODE_TO_TYPE =
            Map.ofEntries(
                    Map.entry("CP001", "provincial"),
                    Map.entry("CP002", "provincial"),
                    Map.entry("CP003", "provincial"),
                    Map.entry("CP004", "provincial"),
                    Map.entry("CP005", "provincial"),
                    Map.entry("CP006", "provincial"),
                    Map.entry("CP007", "provincial"),
                    Map.entry("CP008", "provincial"),
                    Map.entry("CP009", "provincial"),
                    Map.entry("CP010", "provincial"),
                    Map.entry("CP011", "provincial"),
                    Map.entry("CP012", "municipal"),
                    Map.entry("CP013", "municipal"),
                    Map.entry("CP014", "municipal"),
                    Map.entry("CP015", "municipal"),
                    Map.entry("CP016", "municipal"),
                    Map.entry("CP017", "municipal"),
                    Map.entry("CP018", "municipal"),
                    Map.entry("CP019", "municipal"));

    public static String displayName(String code, String fallback) {
        if (code == null) return fallback;
        return CODE_TO_NAME.getOrDefault(code, fallback);
    }

    public static String codeByName(String name) {
        if (name == null) return null;
        return NAME_TO_CODE.get(name);
    }

    public static String displayDistrict(String code, String fallback) {
        if (code == null) return fallback;
        return CODE_TO_DISTRICT.getOrDefault(code, fallback);
    }

    public static String displayType(String code, String fallback) {
        if (code == null) return fallback;
        return CODE_TO_TYPE.getOrDefault(code, fallback);
    }
}
