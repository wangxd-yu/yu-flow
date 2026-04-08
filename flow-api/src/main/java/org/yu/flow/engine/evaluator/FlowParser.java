package org.yu.flow.engine.evaluator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yu.flow.engine.model.ErrorDefinition;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.Step;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Flow JSON 解析器
 * 支持两种 JSON 格式:
 * 1. 原始格式: steps 数组中每个步骤有 next 字段定义连接
 * 2. 画布格式: nodes 数组 + edges 数组定义连接, 步骤数据在 data 字段中
 */
public class FlowParser {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public FlowDefinition parse(String json) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);

        // 检测是否是画布格式 (有 edges 数组)
        if (root.has("edges") && root.has("nodes")) {
            root = transformCanvasFormat(root);
        }

        return objectMapper.convertValue(root, FlowDefinition.class);
    }

    /**
     * 转换画布格式为引擎格式
     * 1. 将 edges 转换为每个节点的 next 字段
     * 2. 将 data 中的字段展开到节点根级别
     */
    private JsonNode transformCanvasFormat(JsonNode root) {
        ObjectNode result = objectMapper.createObjectNode();

        // 复制基础字段
        if (root.has("id")) result.put("id", root.get("id").asText());
        if (root.has("version")) result.put("version", root.get("version").asText());
        if (root.has("args")) result.set("args", root.get("args"));
        if (root.has("errors")) result.set("errors", root.get("errors"));

        // 处理节点
        ArrayNode nodes = (ArrayNode) root.get("nodes");
        ArrayNode edges = (ArrayNode) root.get("edges");

        // 构建 nodeId -> node 的映射用于后续处理
        Map<String, ObjectNode> nodeMap = new HashMap<>();
        for (JsonNode node : nodes) {
            String nodeId = node.get("id").asText();
            ObjectNode normalizedNode = normalizeNode(node);
            nodeMap.put(nodeId, normalizedNode);
        }

        // 处理 edges，将连接信息添加到 next 字段
        if (edges != null) {
            for (JsonNode edge : edges) {
                processEdge(edge, nodeMap);
            }
        }

        // 设置 startStepId (start 或 request 节点作为入口)
        for (ObjectNode node : nodeMap.values()) {
            String type = node.get("type").asText();
            if ("start".equals(type) || "request".equals(type)) {
                result.put("startStepId", node.get("id").asText());
                break; // start 和 request 这里仅记录 startStepId，严格校验在下面
            }
        }

        // --- 对 Request 节点进行规则校验 ---
        int requestCount = 0;
        String requestNodeId = null;
        for (ObjectNode node : nodeMap.values()) {
            if ("request".equals(node.get("type").asText())) {
                requestCount++;
                requestNodeId = node.get("id").asText();
            }
        }

        if (requestCount > 1) {
            throw new FlowException("REQUEST_VALIDATION_ERROR", "单个流程中只能有一个 request 节点");
        }

        if (requestNodeId != null) {
            // 校验没有别的节点连向 request (它必须是第一个节点)
            for (ObjectNode node : nodeMap.values()) {
                if (!node.get("id").asText().equals(requestNodeId)) {
                    JsonNode nextNode = node.get("next");
                    if (nextNode != null && nextNode.isObject()) {
                        Iterator<JsonNode> nextTargets = nextNode.elements();
                        while (nextTargets.hasNext()) {
                            JsonNode target = nextTargets.next();
                            if (target.isArray()) {
                                for (JsonNode t : target) {
                                    if (requestNodeId.equals(t.asText())) {
                                        throw new FlowException("REQUEST_VALIDATION_ERROR", "request 节点必须为流程中的第一个节点，不能有其他节点指向它");
                                    }
                                }
                            } else if (requestNodeId.equals(target.asText())) {
                                throw new FlowException("REQUEST_VALIDATION_ERROR", "request 节点必须为流程中的第一个节点，不能有其他节点指向它");
                            }
                        }
                    }
                }
            }
        }
        // ---------------------------------

        // 转换 nodeMap 为 steps 数组
        ArrayNode steps = objectMapper.createArrayNode();
        for (ObjectNode node : nodeMap.values()) {
            steps.add(node);
        }
        result.set("steps", steps);

        return result;
    }

    /**
     * 标准化节点：将 data 字段中的内容展开到节点根级别
     */
    private ObjectNode normalizeNode(JsonNode node) {
        ObjectNode normalized = objectMapper.createObjectNode();

        // 复制基础字段
        normalized.put("id", node.get("id").asText());
        normalized.put("type", node.get("type").asText());
        if (node.has("name")) normalized.put("name", node.get("name").asText());

        // 初始化 next 字段
        normalized.set("next", objectMapper.createObjectNode());

        // 展开 data 字段中的内容
        if (node.has("data")) {
            JsonNode data = node.get("data");
            Iterator<String> fieldNames = data.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                normalized.set(fieldName, data.get(fieldName));
            }
        }

        return normalized;
    }

    /**
     * 处理 edge，将连接信息添加到源节点的 next 字段
     * edge 格式: { "source": {"cell": "nodeId", "port": "out"}, "target": {"cell": "targetId", "port": "in"} }
     * 如果同一个源端口有多条边，则 next[port] 存储为数组
     */
    private void processEdge(JsonNode edge, Map<String, ObjectNode> nodeMap) {
        JsonNode source = edge.get("source");
        JsonNode target = edge.get("target");

        if (source == null || target == null) return;

        String sourceCell = source.get("cell").asText();
        String sourcePort = source.has("port") ? source.get("port").asText() : "out";
        String targetCell = target.get("cell").asText();

        ObjectNode sourceNode = nodeMap.get(sourceCell);
        if (sourceNode == null) return;

        ObjectNode nextObject = (ObjectNode) sourceNode.get("next");

        // 检查是否已有该端口的连接
        if (nextObject.has(sourcePort)) {
            JsonNode existing = nextObject.get(sourcePort);
            if (existing.isArray()) {
                // 已经是数组，追加
                ((ArrayNode) existing).add(targetCell);
            } else {
                // 原来是单个值，转换为数组
                ArrayNode array = objectMapper.createArrayNode();
                array.add(existing.asText());
                array.add(targetCell);
                nextObject.set(sourcePort, array);
            }
        } else {
            // 新端口，直接添加
            nextObject.put(sourcePort, targetCell);
        }

        // 处理自动完善 extractPath 的逻辑
        String targetPort = target.has("port") ? target.get("port").asText() : "in";

        if (targetPort.startsWith("in:var:")) {
            // ── in:var:xxx “用户动态变量”端口 (If / Database / Response)──
            // 参数名通过遍历 inputs 居 id 字段匹配找到
            String varId = targetPort.substring("in:var:".length());
            ObjectNode targetNode = nodeMap.get(targetCell);
            if (targetNode != null) {
                JsonNode inputsNode = targetNode.get("inputs");
                String varName = null;
                if (inputsNode != null && inputsNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = inputsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        JsonNode val = field.getValue();
                        if (val.isObject() && val.has("id") && varId.equals(val.get("id").asText())) {
                            varName = field.getKey();
                            break;
                        }
                    }
                }
                if (varName != null && !varName.isEmpty()) {
                    autoFillExtractPath(inputsNode, varName, sourceCell, sourcePort);
                }
            }

        } else if (targetPort.startsWith("in:arg:")) {
            // ── in:arg:xxx “系统方法参数”端口 (SystemMethod)──
            // 端口 ID 后缀就是参数名，直接作为 inputs 的 key，无需遍历
            String paramName = targetPort.substring("in:arg:".length());
            ObjectNode targetNode = nodeMap.get(targetCell);
            if (targetNode != null && !paramName.isEmpty()) {
                JsonNode inputsNode = targetNode.get("inputs");
                autoFillExtractPath(inputsNode, paramName, sourceCell, sourcePort);
            }
        } else if ("in".equals(targetPort)) {
            ObjectNode targetNode = nodeMap.get(targetCell);
            if (targetNode != null) {
                String targetType = targetNode.has("type") ? targetNode.get("type").asText() : "";
                if ("for".equals(targetType)) {
                    // ── for 节点的 in 端口，即为循环数组 list ──
                    JsonNode inputsNode = targetNode.get("inputs");
                    if (inputsNode == null || !inputsNode.isObject()) {
                        inputsNode = objectMapper.createObjectNode();
                        targetNode.set("inputs", inputsNode);
                    }
                    autoFillExtractPath(inputsNode, "list", sourceCell, sourcePort);
                } else if ("evaluate".equals(targetType)) {
                    // ── evaluate 节点的 in 端口: 补全所有 inputs 中的 extractPath ──
                    JsonNode inputsNode = targetNode.get("inputs");
                    if (inputsNode != null && inputsNode.isObject()) {
                        Iterator<String> fieldNames = inputsNode.fieldNames();
                        while (fieldNames.hasNext()) {
                            String fieldName = fieldNames.next();
                            autoFillExtractPath(inputsNode, fieldName, sourceCell, sourcePort, nodeMap);
                        }
                    }
                }
            }
        }
    }

    /**
     * 自动补全 extractPath。
     * 规则：
     *   • 空 / "$" → 赋为 "$.源节点.sourcePort"(取上游的主输出)
     *   • 已有 "$." 开头且第二段为已知 nodeId → 视为绝对路径，不修改
     *   • 已有 "$." 开头但第二段不是 nodeId → 视为相对路径，"$.field" 展开为 "$.源节点.field"
     *   • 其他情况不修改（用户已手动设置相对路径）
     */
    private void autoFillExtractPath(JsonNode inputsNode, String fieldKey,
                                      String sourceCell, String sourcePort) {
        autoFillExtractPath(inputsNode, fieldKey, sourceCell, sourcePort, null);
    }

    private void autoFillExtractPath(JsonNode inputsNode, String fieldKey,
                                      String sourceCell, String sourcePort,
                                      Map<String, ObjectNode> nodeMap) {
        if (inputsNode == null || !inputsNode.isObject()) return;
        JsonNode inputDef = inputsNode.get(fieldKey);

        String userPath = null;
        if (inputDef == null) {
            userPath = "";
        } else if (inputDef.isObject()) {
            JsonNode pathNode = inputDef.get("extractPath");
            if (pathNode != null) userPath = pathNode.asText().trim();
        } else if (inputDef.isTextual()) {
            userPath = inputDef.asText().trim();
        }

        if (userPath == null) return;

        String prefix = "$." + sourceCell + "." + sourcePort;
        boolean modified = false;

        if (userPath.startsWith("$.")) {
            // 判断是绝对路径还是相对简写
            // 绝对路径形如 "$.nodeId.xxx"，其中 nodeId 是已知的节点 ID
            // 相对简写形如 "$.field"，$ 代表上游节点
            String afterDollarDot = userPath.substring(2); // 去掉 "$."
            String firstSegment = afterDollarDot.contains(".")
                    ? afterDollarDot.substring(0, afterDollarDot.indexOf('.'))
                    : afterDollarDot;

            boolean isAbsolutePath = false;
            if (nodeMap != null) {
                isAbsolutePath = nodeMap.containsKey(firstSegment);
            } else {
                // 无 nodeMap 时，兼容旧行为：如果已经指向 sourceCell 则不修改
                isAbsolutePath = userPath.startsWith("$." + sourceCell);
            }

            if (isAbsolutePath) {
                // 已是绝对路径且指向正确的源节点，不修改
                // 如果指向错误的源节点，也不修改（用户可能故意引用其他节点）
            } else {
                // 相对简写："$.field" → "$.sourceCell.sourcePort.field"
                userPath = prefix + "." + afterDollarDot;
                modified = true;
            }
        } else if (userPath.isEmpty() || "$".equals(userPath)) {
            // 空或仅为 "$"，设为源节点绝对路径（取 sourcePort 对应的输出）
            userPath = prefix;
            modified = true;
        }
        // 小注：如果用户填写了形如 "fieldName" 这种相对路径，不自动处理

        if (modified) {
            if (inputDef == null) {
                ObjectNode newNode = ((ObjectNode) inputsNode).objectNode();
                newNode.put("extractPath", userPath);
                ((ObjectNode) inputsNode).set(fieldKey, newNode);
            } else if (inputDef.isObject()) {
                ((ObjectNode) inputDef).put("extractPath", userPath);
            } else {
                ((ObjectNode) inputsNode).put(fieldKey, userPath);
            }
        }
    }

    /**
     * 解析错误定义
     */
    private ErrorDefinition parseError(JsonNode errorNode) {
        int code = errorNode.get("code").asInt();
        String message = errorNode.get("message").asText();
        return ErrorDefinition.of(code, message);
    }

    private Step parseStep(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, Step.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
