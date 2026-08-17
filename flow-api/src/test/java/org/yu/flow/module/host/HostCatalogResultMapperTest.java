package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostCatalogResultMapperTest {

    @Test
    void mapsJsonArray() {
        List<Map<String, Object>> raw = List.of(
                Map.of("value", "ADMIN", "label", "管理员"),
                Map.of("value", "END_USER", "label", "普通用户")
        );
        List<HostIdentityCatalogItemDTO> items = HostCatalogResultMapper.map(raw, "value", "label", null, 50);
        assertEquals(2, items.size());
        assertEquals("ADMIN", items.get(0).getValue());
        assertEquals("管理员", items.get(0).getLabel());
    }

    @Test
    void mapsCustomFieldsAndKeyword() {
        List<Map<String, Object>> raw = List.of(
                Map.of("code", "vip", "name", "会员"),
                Map.of("code", "op", "name", "运营")
        );
        List<HostIdentityCatalogItemDTO> items = HostCatalogResultMapper.map(raw, "code", "name", "会", 50);
        assertEquals(1, items.size());
        assertEquals("vip", items.get(0).getValue());
        assertEquals("会员", items.get(0).getLabel());
    }

    @Test
    void unwrapsRAndPageBean() {
        PageBean<Map<String, String>> page = new PageBean<>();
        page.setItems(List.of(Map.of("value", "dept-1", "label", "一部")));
        R<PageBean<Map<String, String>>> wrapped = R.ok(page);
        List<HostIdentityCatalogItemDTO> items = HostCatalogResultMapper.map(wrapped, "value", "label", null, 10);
        assertEquals(1, items.size());
        assertEquals("dept-1", items.get(0).getValue());
    }

    @Test
    void mapsParentId() {
        List<Map<String, Object>> raw = List.of(
                Map.of("value", "hq", "label", "总部"),
                Map.of("value", "rd", "label", "研发", "parentId", "hq")
        );
        List<HostIdentityCatalogItemDTO> items = HostCatalogResultMapper.map(raw, "value", "label", null, 50);
        assertEquals(null, items.get(0).getParentId());
        assertEquals("hq", items.get(1).getParentId());
    }

    @Test
    void parseSettingsDefaultsDisabled() {
        HostIdentityCatalogSettings empty = HostIdentityCatalogSettingsStore.parse(null);
        assertEquals(false, empty.get(FlowHostCatalogDimension.USER_TYPE).isEnabled());
        HostIdentityCatalogSettings parsed = HostIdentityCatalogSettingsStore.parse(
                "{\"dimensions\":{\"USER_TYPE\":{\"enabled\":true,\"valueField\":\"code\",\"labelField\":\"name\",\"searchable\":true}}}");
        HostCatalogDimBinding ut = parsed.get(FlowHostCatalogDimension.USER_TYPE);
        assertEquals(true, ut.isEnabled());
        assertEquals("code", ut.getValueField());
        assertEquals(true, ut.isSearchable());
        assertEquals(false, parsed.get(FlowHostCatalogDimension.ROLE).isEnabled());
    }
}
