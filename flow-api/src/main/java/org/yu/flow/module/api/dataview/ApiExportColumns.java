package org.yu.flow.module.api.dataview;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.api.dto.ViewExportColumnDTO;
import org.yu.flow.module.api.dto.ViewExportConfigDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 预览 / 导出的列：表头取值顺序为配置 header → 契约 title → description → 字段名；
 * 配置列与 SQL 结果字段对不上时以 SQL 列为准。也负责单元格取值与 Excel 写出前的类型转换。
 */
@Slf4j
public final class ApiExportColumns {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String IDENTIFIER = "^[A-Za-z_][A-Za-z0-9_]*$";

    private ApiExportColumns() {
    }

    /**
     * @param exportOnly true 时按 exportable 过滤配置列，false 时按 visible 过滤
     */
    public static List<ViewExportColumnDTO> resolve(ViewExportConfigDTO cfg, String contract,
                                                    List<Map<String, Object>> sampleRows,
                                                    Map<String, Object> object, boolean exportOnly) {
        Map<String, String> titleMap = contractTitles(contract);
        List<ViewExportColumnDTO> configured = cfg.getColumns() == null ? List.of() : cfg.getColumns();

        if (!configured.isEmpty()) {
            return configured.stream()
                    .filter(c -> StrUtil.isNotBlank(c.getField()))
                    .filter(c -> exportOnly
                            ? !Boolean.FALSE.equals(c.getExportable())
                            : !Boolean.FALSE.equals(c.getVisible()))
                    .map(c -> ViewExportColumnDTO.builder()
                            .field(c.getField())
                            .header(StrUtil.isNotBlank(c.getHeader())
                                    ? c.getHeader() : titleMap.getOrDefault(c.getField(), c.getField()))
                            .width(c.getWidth())
                            .exportable(c.getExportable() == null || c.getExportable())
                            .visible(c.getVisible() == null || c.getVisible())
                            .templateKey(StrUtil.blankToDefault(c.getTemplateKey(), c.getField()))
                            .build())
                    .toList();
        }

        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (object != null) {
            fields.addAll(object.keySet());
        }
        if (sampleRows != null) {
            for (Map<String, Object> row : sampleRows) {
                if (row != null) {
                    fields.addAll(row.keySet());
                }
            }
        }
        if (fields.isEmpty() && !titleMap.isEmpty()) {
            fields.addAll(titleMap.keySet());
        }
        return fields.stream()
                .map(f -> column(f, titleMap.getOrDefault(f, f)))
                .toList();
    }

    /** 配置列与 SQL 结果字段对齐；一个都对不上时完全以 SQL 结果列为准，表头仍取契约 / 配置里的中文 */
    public static List<ViewExportColumnDTO> alignToSqlFields(List<ViewExportColumnDTO> configured,
                                                             List<String> labels, List<String> camelFields,
                                                             String contract) {
        Map<String, String> titleMap = contractTitles(contract);
        if (configured != null) {
            for (ViewExportColumnDTO c : configured) {
                if (c != null && StrUtil.isNotBlank(c.getField()) && StrUtil.isNotBlank(c.getHeader())) {
                    titleMap.putIfAbsent(c.getField(), c.getHeader());
                }
            }
        }

        boolean matched = configured != null && configured.stream()
                .anyMatch(c -> c != null && StrUtil.isNotBlank(c.getField())
                        && findSqlField(c.getField(), camelFields, labels) != null);
        if (matched) {
            List<ViewExportColumnDTO> aligned = new ArrayList<>();
            for (ViewExportColumnDTO c : configured) {
                if (c == null || StrUtil.isBlank(c.getField()) || Boolean.FALSE.equals(c.getExportable())) {
                    continue;
                }
                String sqlField = findSqlField(c.getField(), camelFields, labels);
                if (sqlField == null) {
                    continue;
                }
                String header = StrUtil.isNotBlank(c.getHeader())
                        ? c.getHeader()
                        : titleMap.getOrDefault(c.getField(), titleMap.getOrDefault(sqlField, sqlField));
                aligned.add(ViewExportColumnDTO.builder()
                        .field(sqlField)
                        .header(header)
                        .width(c.getWidth())
                        .exportable(true)
                        .visible(true)
                        .templateKey(StrUtil.blankToDefault(c.getTemplateKey(), c.getField()))
                        .build());
            }
            if (!aligned.isEmpty()) {
                return aligned;
            }
        }

        List<ViewExportColumnDTO> fromSql = new ArrayList<>();
        for (int i = 0; i < camelFields.size(); i++) {
            String camel = camelFields.get(i);
            String label = labels.get(i);
            String header = titleMap.getOrDefault(camel,
                    titleMap.getOrDefault(label, titleMap.getOrDefault(
                            label == null ? camel : label.toLowerCase(Locale.ROOT), camel)));
            fromSql.add(column(camel, header));
        }
        return fromSql;
    }

    /** 没有任何列信息时，用结果行里出现过的键作列 */
    public static List<ViewExportColumnDTO> fromRows(List<Map<String, Object>> rows) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row != null) {
                    fields.addAll(row.keySet());
                }
            }
        }
        return fields.stream()
                .map(f -> ViewExportColumnDTO.builder().field(f).header(f).exportable(true).visible(true).build())
                .toList();
    }

    private static ViewExportColumnDTO column(String field, String header) {
        return ViewExportColumnDTO.builder()
                .field(field)
                .header(header)
                .exportable(true)
                .visible(true)
                .templateKey(field)
                .build();
    }

    private static String findSqlField(String field, List<String> camelFields, List<String> labels) {
        if (StrUtil.isBlank(field)) {
            return null;
        }
        for (String c : camelFields) {
            if (field.equalsIgnoreCase(c)) {
                return c;
            }
        }
        for (String l : labels) {
            if (field.equalsIgnoreCase(l)) {
                return StrUtil.toCamelCase(l);
            }
        }
        String camel = StrUtil.toCamelCase(field);
        for (String c : camelFields) {
            if (camel.equalsIgnoreCase(c)) {
                return c;
            }
        }
        return null;
    }

    /** 模板占位符 key 只允许字母、数字、下划线；不合法时回退到字段名 */
    public static String templateKey(String key, String fallbackField) {
        String k = StrUtil.trim(key);
        if (StrUtil.isNotBlank(k) && k.matches(IDENTIFIER)) {
            return k;
        }
        String fb = StrUtil.trim(fallbackField);
        if (StrUtil.isNotBlank(fb) && fb.matches(IDENTIFIER)) {
            return fb;
        }
        return StrUtil.blankToDefault(StrUtil.toCamelCase(fb), "field");
    }

    /** 按字段取单元格值：精确匹配 → 忽略大小写 → 驼峰形式 */
    public static Object cellValue(Map<String, Object> row, String field) {
        if (row == null || StrUtil.isBlank(field)) {
            return null;
        }
        Object raw = null;
        if (row.containsKey(field)) {
            raw = row.get(field);
        } else {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(field)) {
                    raw = e.getValue();
                    break;
                }
            }
            if (raw == null) {
                raw = row.get(StrUtil.toCamelCase(field));
            }
        }
        return toExcelCellValue(raw);
    }

    /** EasyExcel 对 java.util.Date / java.sql.* 等缺省 Converter，统一转成字符串写出 */
    private static Object toExcelCellValue(Object raw) {
        if (raw == null || raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.format(DATE_TIME);
        }
        if (raw instanceof LocalDate ld) {
            return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (raw instanceof LocalTime lt) {
            return lt.format(TIME);
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DATE_TIME);
        }
        if (raw instanceof java.sql.Date d) {
            return d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (raw instanceof java.sql.Time t) {
            return t.toLocalTime().format(TIME);
        }
        if (raw instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).format(DATE_TIME);
        }
        if (raw instanceof OffsetDateTime odt) {
            return odt.toLocalDateTime().format(DATE_TIME);
        }
        if (raw instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_TIME);
        }
        if (raw instanceof byte[]) {
            return "[binary]";
        }
        return String.valueOf(raw);
    }

    /** 从响应契约扁平提取 field → title / description；数组与对象的子字段都展开成列 */
    private static Map<String, String> contractTitles(String contractJson) {
        Map<String, String> map = new LinkedHashMap<>();
        if (StrUtil.isBlank(contractJson)) {
            return map;
        }
        try {
            JsonNode root = ApiDataViewSupport.MAPPER.readTree(contractJson);
            JsonNode body = null;
            JsonNode responses = root.get("responses");
            if (responses != null && responses.isObject()) {
                JsonNode r200 = responses.get("200");
                if (r200 == null) {
                    Iterator<String> it = responses.fieldNames();
                    if (it.hasNext()) {
                        r200 = responses.get(it.next());
                    }
                }
                if (r200 != null) {
                    body = r200.get("body");
                }
            }
            if (body == null) {
                body = root.get("responseBody");
            }
            if (body != null && body.isArray()) {
                collectSchemaTitles(body, map);
            }
        } catch (Exception e) {
            log.debug("[ApiDataView] 解析契约 title 失败: {}", e.getMessage());
        }
        return map;
    }

    private static final Set<String> CONTAINER_TYPES = Set.of("array", "object");

    private static void collectSchemaTitles(JsonNode nodes, Map<String, String> map) {
        for (JsonNode n : nodes) {
            String name = textOrNull(n, "name");
            String type = textOrNull(n, "type");
            JsonNode children = n.get("children");
            if (type != null && CONTAINER_TYPES.contains(type.toLowerCase(Locale.ROOT))
                    && children != null && children.isArray()) {
                collectSchemaTitles(children, map);
                continue;
            }
            if (StrUtil.isBlank(name)) {
                continue;
            }
            String title = textOrNull(n, "title");
            if (StrUtil.isBlank(title)) {
                title = textOrNull(n, "description");
            }
            map.putIfAbsent(name, StrUtil.isNotBlank(title) ? title : name);
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String v = n.get(field).asText(null);
        return StrUtil.isBlank(v) ? null : v;
    }
}
