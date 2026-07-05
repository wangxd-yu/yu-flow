package org.yu.flow.auto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author yu-flow
 * @date 2025-03-05 23:31
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
public class PageBean<T> {

    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 当前页码（后端通用命名）
     */
    private int page;

    /**
     * 当前页码（兼容 Ant Design ProTable / amis）
     */
    private int current;

    /**
     * 每页条数
     */
    private int size;

    /**
     * 总页数
     */
    private int pages;

    /**
     * 总条数
     */
    private Long total;

    @Builder
    public PageBean(List<T> items, int current, int size, int pages, Long total) {
        this.items = items;
        this.page = current;
        this.current = current;
        this.size = size;
        this.pages = pages;
        this.total = total;
    }

    public void setPage(int page) {
        this.page = page;
        this.current = page;
    }

    public void setCurrent(int current) {
        this.current = current;
        this.page = current;
    }
}
