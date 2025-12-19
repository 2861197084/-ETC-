package com.etc.platform.dto;

import lombok.Data;
import java.util.List;

/**
 * 分页响应DTO
 */
@Data
public class PageResult<T> {
    private List<T> list;
    private Long total;
    private Integer current;
    private Integer size;
    private Integer pages;

    public PageResult() {}

    public PageResult(List<T> list, Long total, Integer current, Integer size) {
        this.list = list;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = (int) Math.ceil((double) total / size);
    }

    public static <T> PageResult<T> of(List<T> list, Long total, Integer current, Integer size) {
        return new PageResult<>(list, total, current, size);
    }
}
