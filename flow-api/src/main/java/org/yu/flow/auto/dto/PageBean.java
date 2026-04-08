package org.yu.flow.auto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
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
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageBean<T> {

    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 当前页码
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
}
