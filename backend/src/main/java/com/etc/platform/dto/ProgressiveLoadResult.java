package com.etc.platform.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 渐进式加载响应DTO
 * 
 * 支持 MySQL 热数据 + HBase 历史数据的两阶段加载
 */
@Data
public class ProgressiveLoadResult<T> {
    
    /**
     * 当前数据来源: "mysql" | "hbase" | "mixed"
     */
    private String source;
    
    /**
     * 当前页数据
     */
    private List<T> list;
    
    /**
     * MySQL 中的记录总数（7天内）
     */
    private Long mysqlTotal;
    
    /**
     * Redis 缓存的全量计数（来自 plate:count:* 或 cp:count:*）
     */
    private Long totalCount;
    
    /**
     * 是否还有更多历史记录可以加载
     */
    private Boolean hasMoreHistory;
    
    /**
     * 当前页
     */
    private Integer current;
    
    /**
     * 每页大小
     */
    private Integer size;
    
    /**
     * HBase 查询的起始 RowKey（用于下次"加载更多"）
     */
    private String nextRowKey;
    
    /**
     * 加载耗时（毫秒）
     */
    private Long queryTimeMs;
    
    /**
     * 额外的统计信息
     */
    private Map<String, Object> stats;

    public ProgressiveLoadResult() {}

    /**
     * 构建 MySQL 热数据响应
     */
    public static <T> ProgressiveLoadResult<T> fromMysql(
            List<T> list, Long mysqlTotal, Long totalCount, 
            Integer current, Integer size) {
        
        ProgressiveLoadResult<T> result = new ProgressiveLoadResult<>();
        result.setSource("mysql");
        result.setList(list);
        result.setMysqlTotal(mysqlTotal);
        result.setTotalCount(totalCount);
        result.setHasMoreHistory(totalCount > mysqlTotal);
        result.setCurrent(current);
        result.setSize(size);
        return result;
    }
    
    /**
     * 构建 HBase 历史数据响应
     */
    public static <T> ProgressiveLoadResult<T> fromHBase(
            List<T> list, Long totalCount, String nextRowKey,
            Integer current, Integer size) {
        
        ProgressiveLoadResult<T> result = new ProgressiveLoadResult<>();
        result.setSource("hbase");
        result.setList(list);
        result.setTotalCount(totalCount);
        result.setHasMoreHistory(nextRowKey != null && !nextRowKey.isEmpty());
        result.setNextRowKey(nextRowKey);
        result.setCurrent(current);
        result.setSize(size);
        return result;
    }
    
    /**
     * 构建混合数据响应
     */
    public static <T> ProgressiveLoadResult<T> mixed(
            List<T> list, Long mysqlTotal, Long totalCount,
            String nextRowKey, Integer current, Integer size) {
        
        ProgressiveLoadResult<T> result = new ProgressiveLoadResult<>();
        result.setSource("mixed");
        result.setList(list);
        result.setMysqlTotal(mysqlTotal);
        result.setTotalCount(totalCount);
        result.setHasMoreHistory(nextRowKey != null && !nextRowKey.isEmpty());
        result.setNextRowKey(nextRowKey);
        result.setCurrent(current);
        result.setSize(size);
        return result;
    }
}
