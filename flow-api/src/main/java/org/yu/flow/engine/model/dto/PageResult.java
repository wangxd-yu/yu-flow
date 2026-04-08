package org.yu.flow.engine.model.dto;

import lombok.Data;
import java.util.List;

/**
 * 分页结果定义
 * @param <T>
 */
@Data
public class PageResult<T> {
    private List<T> content;
    private long total;
    private int page;
    private int size;

    public PageResult() {}

    public PageResult(List<T> content, long total, int page, int size) {
        this.content = content;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
