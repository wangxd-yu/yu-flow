package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量按目录前缀重写结果。
 */
@Data
public class BatchApplyDirPrefixResult {

    private int updated;
    private int skipped;
    private int failed;
    /** 其中已发布（仅改草稿、需再发布）条数 */
    private int publishedTouched;
    /** 实际使用的旧前缀（含服务端推断） */
    private String oldPrefixUsed;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String id;
        private String name;
        private String from;
        private String to;
        /** updated / skipped / failed */
        private String status;
        private String message;
    }
}
