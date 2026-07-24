package org.yu.flow.module.datasource.dto;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.druid.wall.WallConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 产品化 SQL 安全墙配置（映射 Druid {@link WallConfig}，不暴露裸属性字典）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSourceWallConfig {

    /** 默认危险函数建议集 */
    public static final List<String> DEFAULT_FUNCTION_BLACKLIST = Arrays.asList(
            "sleep", "benchmark", "load_file", "updatexml", "extractvalue", "pg_sleep"
    );

    private boolean enabled = true;
    private boolean multiStatementAllow = false;
    private boolean commentAllow = false;
    private boolean noneBaseStatementAllow = false;
    private boolean selectAllow = true;
    private boolean insertAllow = true;
    private boolean updateAllow = true;
    private boolean deleteAllow = true;
    private boolean tableCheck = true;
    private List<String> tableWhiteList = new ArrayList<>();
    private List<String> tableBlackList = new ArrayList<>();
    /** 只读表：允许 SELECT，禁止对该表的 INSERT / UPDATE / DELETE（映射 Druid readOnlyTables） */
    private List<String> tableReadOnlyList = new ArrayList<>();
    private List<String> functionBlackList = new ArrayList<>(DEFAULT_FUNCTION_BLACKLIST);
    private boolean variantCheck = true;

    public static DataSourceWallConfig enabledDefaults() {
        DataSourceWallConfig c = new DataSourceWallConfig();
        c.setEnabled(true);
        return c;
    }

    public static DataSourceWallConfig disabledDefaults() {
        DataSourceWallConfig c = new DataSourceWallConfig();
        c.setEnabled(false);
        return c;
    }

    public static DataSourceWallConfig fromJson(String json) {
        if (StrUtil.isBlank(json)) {
            return enabledDefaults();
        }
        try {
            DataSourceWallConfig c = JSONUtil.toBean(json, DataSourceWallConfig.class);
            return c != null ? c : enabledDefaults();
        } catch (Exception e) {
            return enabledDefaults();
        }
    }

    public String toJson() {
        return JSONUtil.toJsonStr(this);
    }

    /**
     * 转为 Druid WallConfig；表白名单非空时自动并入常见元数据表，降低误伤。
     */
    public WallConfig toDruidWallConfig() {
        WallConfig wall = new WallConfig();
        wall.setMultiStatementAllow(multiStatementAllow);
        wall.setCommentAllow(commentAllow);
        wall.setNoneBaseStatementAllow(noneBaseStatementAllow);
        wall.setSelectAllow(selectAllow);
        wall.setInsertAllow(insertAllow);
        wall.setUpdateAllow(updateAllow);
        wall.setDeleteAllow(deleteAllow);
        wall.setTableCheck(tableCheck);
        wall.setVariantCheck(variantCheck);
        wall.setFunctionCheck(true);
        wall.setMetadataAllow(true);
        wall.setShowAllow(true);

        Set<String> white = new LinkedHashSet<>();
        if (tableWhiteList != null) {
            tableWhiteList.stream().filter(StrUtil::isNotBlank).map(String::trim).forEach(white::add);
        }
        if (!white.isEmpty()) {
            // 元数据探测常用表，避免开启表白名单后管理端拉表失败
            white.add("information_schema.tables");
            white.add("information_schema.columns");
            white.add("information_schema.statistics");
            white.add("pg_catalog.pg_class");
            white.add("pg_catalog.pg_namespace");
            white.add("pg_catalog.pg_attribute");
            white.add("pg_catalog.pg_index");
            white.add("pg_catalog.pg_user");
            white.add("pg_catalog.pg_stat_user_tables");
        }
        if (!white.isEmpty()) {
            wall.getPermitTables().addAll(white);
        }

        if (tableBlackList != null) {
            tableBlackList.stream()
                    .filter(StrUtil::isNotBlank)
                    .map(String::trim)
                    .forEach(t -> wall.getDenyTables().add(t));
        }

        if (tableReadOnlyList != null) {
            tableReadOnlyList.stream()
                    .filter(StrUtil::isNotBlank)
                    .map(String::trim)
                    .forEach(t -> wall.getReadOnlyTables().add(t));
        }

        List<String> funcs = (functionBlackList == null || functionBlackList.isEmpty())
                ? DEFAULT_FUNCTION_BLACKLIST
                : functionBlackList;
        funcs.stream()
                .filter(StrUtil::isNotBlank)
                .map(s -> s.trim().toLowerCase())
                .forEach(f -> wall.getDenyFunctions().add(f));

        return wall;
    }
}
