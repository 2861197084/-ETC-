package com.etc.agent.tools;

import com.etc.common.CheckpointCatalog;
import com.etc.entity.Checkpoint;
import com.etc.repository.CheckpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 卡口信息工具 - 提供卡口基础信息查询能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointInfoTool {

    private final CheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 演示环境兜底：固定 19 个卡口编号（与初始化 SQL 一致）。
     * 当数据库为空时，用 {@link CheckpointCatalog} 生成“可展示”的名称/区县信息。
     */
    private static final List<String> DEFAULT_CODES =
            List.of(
                    "CP001",
                    "CP002",
                    "CP003",
                    "CP004",
                    "CP005",
                    "CP006",
                    "CP007",
                    "CP008",
                    "CP009",
                    "CP010",
                    "CP011",
                    "CP012",
                    "CP013",
                    "CP014",
                    "CP015",
                    "CP016",
                    "CP017",
                    "CP018",
                    "CP019");

    @Tool(description = "查询所有卡口的基础信息列表，包括卡口ID、名称、位置、所属区域等")
    public String getAllCheckpoints() {
        log.info("[Agent Tool] 调用 getAllCheckpoints");
        
        try {
            List<Checkpoint> checkpoints = checkpointRepository.findAll();
            
            if (checkpoints.isEmpty()) {
                // 如果数据库没有，使用内置目录
                return getCheckpointCatalog();
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("【徐州市ETC卡口列表】\n\n");
            sb.append(String.format("共 %d 个卡口:\n\n", checkpoints.size()));
            
            for (Checkpoint cp : checkpoints) {
                sb.append(String.format("📍 %s (%s)\n", cp.getCode(), cp.getName()));
                sb.append(String.format("   位置: %.6f, %.6f\n", cp.getLongitude(), cp.getLatitude()));
                sb.append(String.format("   所属: %s\n", cp.getDistrict()));
                sb.append(String.format("   类型: %s | 状态: %s\n\n", 
                    cp.getType() != null ? cp.getType() : "普通", 
                    cp.getStatus() != null ? (cp.getStatus() == 1 ? "正常" : "异常") : "正常"));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("查询卡口列表失败", e);
            return getCheckpointCatalog();
        }
    }

    @Tool(description = "根据卡口ID或名称查询单个卡口的详细信息")
    public String getCheckpointInfo(
            @ToolParam(description = "卡口ID(如CP001)或卡口名称关键字") String keyword) {
        
        log.info("[Agent Tool] 调用 getCheckpointInfo, keyword={}", keyword);
        
        if (keyword == null || keyword.isBlank()) {
            return "请提供卡口ID或名称关键字";
        }
        
        try {
            // 先尝试精确匹配ID
            Optional<Checkpoint> cpOpt = checkpointRepository.findByCode(keyword.toUpperCase());
            
            if (cpOpt.isEmpty()) {
                // 尝试按名称模糊匹配
                List<Checkpoint> matches = checkpointRepository.findByNameContaining(keyword);
                if (!matches.isEmpty()) {
                    cpOpt = Optional.of(matches.get(0));
                }
            }
            
            if (cpOpt.isEmpty()) {
                // 从内置目录查找
                return getFromCatalog(keyword);
            }
            
            Checkpoint cp = cpOpt.get();
            
            StringBuilder sb = new StringBuilder();
            sb.append("【卡口详细信息】\n\n");
            sb.append(String.format("🔖 卡口编号: %s\n", cp.getCode()));
            sb.append(String.format("📛 卡口名称: %s\n", cp.getName()));
            sb.append(String.format("📍 坐标位置: %.6f, %.6f\n", cp.getLongitude(), cp.getLatitude()));
            sb.append(String.format("🏢 所属区域: %s\n", cp.getDistrict()));
            sb.append(String.format("🏷️ 卡口类型: %s\n", cp.getType() != null ? cp.getType() : "普通收费站"));
            sb.append(String.format("✅ 运行状态: %s\n", cp.getStatus() != null ? (cp.getStatus() == 1 ? "正常" : "异常") : "正常"));
            
            if (cp.getRoadName() != null && !cp.getRoadName().isBlank()) {
                sb.append(String.format("📝 所属道路: %s\n", cp.getRoadName()));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("查询卡口信息失败", e);
            return getFromCatalog(keyword);
        }
    }

    @Tool(description = "根据区域名称查询该区域的所有卡口")
    public String getCheckpointsByRegion(
            @ToolParam(description = "区域名称，如'铜山区'、'新沂市'、'睢宁县'等") String region) {
        
        log.info("[Agent Tool] 调用 getCheckpointsByRegion, region={}", region);
        
        if (region == null || region.isBlank()) {
            return "请提供区域名称，如'铜山区'、'新沂市'等";
        }
        
        try {
            List<Checkpoint> checkpoints = checkpointRepository.findByDistrictContaining(region);
            
            if (checkpoints.isEmpty()) {
                return String.format("未找到 %s 的卡口信息。可用区域: 铜山区、新沂市、睢宁县、丰县、邳州市、沛县等", region);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【%s 卡口列表】\n\n", region));
            sb.append(String.format("共 %d 个卡口:\n\n", checkpoints.size()));
            
            for (Checkpoint cp : checkpoints) {
                sb.append(String.format("📍 %s - %s\n", cp.getCode(), cp.getName()));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("按区域查询卡口失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 从内置卡口目录获取信息
     */
    private String getCheckpointCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("【徐州市ETC卡口目录】\n\n");

        for (String code : DEFAULT_CODES) {
            String name = CheckpointCatalog.displayName(code, code);
            String region = CheckpointCatalog.displayDistrict(code, "未知");
            sb.append(String.format("📍 %s - %s (%s)\n", code, name, region));
        }

        return sb.toString();
    }

    private String getFromCatalog(String keyword) {
        String upperKey = keyword.toUpperCase();

        // 1) 编号精确匹配
        if (DEFAULT_CODES.contains(upperKey)) {
            return formatCatalogInfo(upperKey);
        }

        // 2) 名称精确匹配（目录内 name->code）
        String codeByName = CheckpointCatalog.codeByName(keyword);
        if (codeByName != null) {
            return formatCatalogInfo(codeByName);
        }

        // 3) 名称模糊匹配
        for (String code : DEFAULT_CODES) {
            String name = CheckpointCatalog.displayName(code, "");
            if (name != null && !name.isBlank() && name.contains(keyword)) {
                return formatCatalogInfo(code);
            }
        }

        return String.format("未找到与 '%s' 匹配的卡口。请使用如 CP001、CP002 等卡口编号，或卡口名称关键字。", keyword);
    }

    private String formatCatalogInfo(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("【卡口信息】\n\n");
        sb.append(String.format("🔖 卡口编号: %s\n", code));

        String name = CheckpointCatalog.displayName(code, code);
        String region = CheckpointCatalog.displayDistrict(code, "未知");
        String type = CheckpointCatalog.displayType(code, "未知");

        sb.append(String.format("📛 卡口名称: %s\n", name));
        sb.append(String.format("🏢 所属区域: %s\n", region));
        sb.append(String.format("🏷️ 卡口类型: %s\n", type));
        sb.append("📍 坐标位置: 数据库为空，暂无坐标\n");
        return sb.toString();
    }
}
