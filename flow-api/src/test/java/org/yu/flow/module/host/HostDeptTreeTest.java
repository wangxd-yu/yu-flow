package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostDeptTreeTest {

    private static HostIdentityCatalogItemDTO item(String value, String parentId) {
        return new HostIdentityCatalogItemDTO().setValue(value).setLabel(value).setParentId(parentId);
    }

    @Test
    void expand_includesDescendantsAndKeepsUnknown() {
        List<HostIdentityCatalogItemDTO> tree = List.of(
                item("hq", null),
                item("rd", "hq"),
                item("rd-fe", "rd"),
                item("sales", "hq")
        );
        Set<String> expanded = HostDeptTree.expand(tree, List.of("rd", "ghost"));
        assertTrue(expanded.contains("rd"));
        assertTrue(expanded.contains("rd-fe"));
        assertTrue(expanded.contains("ghost"));
        assertFalse(expanded.contains("sales"));
        assertFalse(expanded.contains("hq"));
    }

    @Test
    void expand_rootCoversAll() {
        List<HostIdentityCatalogItemDTO> tree = List.of(
                item("hq", null),
                item("rd", "hq"),
                item("sales", "hq")
        );
        Set<String> expanded = HostDeptTree.expand(tree, List.of("HQ"));
        assertEquals(Set.of("HQ", "hq", "rd", "sales"), expanded);
    }

    @Test
    void hasTree_falseWhenFlat() {
        assertFalse(HostDeptTree.hasTree(List.of(item("a", null), item("b", ""))));
        assertTrue(HostDeptTree.hasTree(List.of(item("a", null), item("b", "a"))));
    }
}
