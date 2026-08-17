package org.yu.flow.module.transfer.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导出「已发布优先」依赖两件事：实体能脱管深拷贝，且发布快照能按字段名增量回填。
 * 快照字段名与实体属性一一对应（见各模块 buildSnapshot），这里锁住这个约定。
 */
class BundleEntityCopierTest {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    @Test
    @DisplayName("深拷贝与源对象隔离，改副本不影响源实体")
    void detachedCopyIsIsolated() {
        FlowApiDO source = new FlowApiDO();
        source.setId("1001");
        source.setName("订单查询");
        source.setUrl("/api/order/list");
        source.setMethod("GET");
        source.setPublishStatus(1);
        source.setPublishTime(LocalDateTime.of(2026, 8, 13, 10, 30, 0));

        FlowApiDO copy = BundleEntityCopier.detachedCopy(source, FlowApiDO.class);
        copy.setName("被改坏的名字");
        copy.setPublishStatus(0);

        assertEquals("订单查询", source.getName());
        assertEquals(1, source.getPublishStatus());
        assertEquals("1001", copy.getId());
        assertEquals("/api/order/list", copy.getUrl());
        assertEquals(LocalDateTime.of(2026, 8, 13, 10, 30, 0), copy.getPublishTime());
    }

    @Test
    @DisplayName("接口发布快照按字段名回填草稿，快照中没有的字段保持原值")
    void applyApiSnapshot() throws Exception {
        FlowApiDO draft = new FlowApiDO();
        draft.setId("1001");
        draft.setName("草稿名");
        draft.setUrl("/api/order/list-draft");
        draft.setMethod("GET");
        draft.setSqlContent("select 1");
        draft.setDirectoryId("dir-1");

        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("name", "线上名");
        snapshot.put("url", "/api/order/list");
        snapshot.put("method", "POST");
        snapshot.put("sqlContent", "select * from orders");
        snapshot.put("datasource", "ORDER_DB");
        snapshot.put("contract", "{\"request\":{}}");
        snapshot.put("securityConfig", "{\"authMode\":\"INHERIT\"}");

        BundleEntityCopier.applySnapshot(draft, MAPPER.writeValueAsString(snapshot));

        assertEquals("线上名", draft.getName());
        assertEquals("/api/order/list", draft.getUrl());
        assertEquals("POST", draft.getMethod());
        assertEquals("select * from orders", draft.getSqlContent());
        assertEquals("ORDER_DB", draft.getDatasource());
        assertEquals("{\"authMode\":\"INHERIT\"}", draft.getSecurityConfig());
        // 快照不含的字段不应被清空
        assertEquals("1001", draft.getId());
        assertEquals("dir-1", draft.getDirectoryId());
    }

    @Test
    @DisplayName("定时任务快照能回填 cron 与启停等布尔字段")
    void applyTaskSnapshot() throws Exception {
        FlowTaskDO draft = new FlowTaskDO();
        draft.setId("2001");
        draft.setName("草稿任务");
        draft.setCron("0 0/1 * * * ?");
        draft.setEnabled(false);
        draft.setDirectoryId("dir-2");

        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("name", "线上任务");
        snapshot.put("cron", "0 0 2 * * ?");
        snapshot.put("dslContent", "{\"nodes\":[]}");
        snapshot.put("enabled", true);
        snapshot.put("logEnabled", true);

        BundleEntityCopier.applySnapshot(draft, MAPPER.writeValueAsString(snapshot));

        assertEquals("线上任务", draft.getName());
        assertEquals("0 0 2 * * ?", draft.getCron());
        assertEquals("{\"nodes\":[]}", draft.getDslContent());
        assertTrue(draft.getEnabled());
        assertTrue(draft.getLogEnabled());
        assertEquals("dir-2", draft.getDirectoryId());
    }

    @Test
    @DisplayName("包体来自更高版本时多出的字段直接忽略，不让整包解析失败")
    void unknownFieldsAreIgnored() {
        FlowApiDO draft = new FlowApiDO();
        draft.setId("1001");

        BundleEntityCopier.applySnapshot(draft, "{\"name\":\"新版\",\"someFutureField\":123}");

        assertEquals("新版", draft.getName());
    }
}
