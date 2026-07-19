package org.yu.flow.engine.model.step;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 仅接受 {@code cases: [{id,name,value}, ...]}。
 */
public class SwitchCasesDeserializer extends JsonDeserializer<List<SwitchCase>> {

    @Override
    public List<SwitchCase> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        List<SwitchCase> list = new ArrayList<>();
        if (node == null || node.isNull()) {
            return list;
        }
        if (!node.isArray()) {
            throw JsonMappingException.from(p, "switch.cases 必须是对象数组 [{id,name,value}]");
        }
        int i = 0;
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                i++;
                continue;
            }
            if (!item.isObject()) {
                throw JsonMappingException.from(p,
                        "switch.cases[" + i + "] 必须是对象 {id,name,value}，不再支持字符串");
            }
            String id = textOr(item, "id", "c" + i);
            String name = textOr(item, "name", "Case " + (i + 1));
            String value = textOr(item, "value", textOr(item, "match", ""));
            list.add(SwitchCase.of(id, name, value));
            i++;
        }
        return list;
    }

    private static String textOr(JsonNode obj, String field, String fallback) {
        JsonNode n = obj.get(field);
        if (n == null || n.isNull()) {
            return fallback;
        }
        String t = n.asText();
        return t == null || t.isBlank() ? fallback : t;
    }
}
