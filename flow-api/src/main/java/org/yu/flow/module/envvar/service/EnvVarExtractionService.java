package org.yu.flow.module.envvar.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.envvar.dto.SaveSysEnvVariableDTO;
import org.yu.flow.module.envvar.repository.SysEnvVariableRepository;
import org.yu.flow.module.envvar.support.EnvVarRefs;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.release.support.AssetEditLockAspect;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把编排里写死的第三方地址抽取为环境变量：
 * 扫描 httpRequest 节点的 URL，按「协议 + 主机 + 端口」归组；确认后创建变量并把 URL 前缀替换为 {@code ${env.CODE}}。
 *
 * <p>只改草稿，改完需重新发布才生效（也便于在测试环境验证后再进版本单）。</p>
 */
@Slf4j
@Service
public class EnvVarExtractionService {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final Pattern BASE_URL = Pattern.compile("^(https?://[^/?#\\s$]+)", Pattern.CASE_INSENSITIVE);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public record Usage(String assetType, String assetId, String assetName, String url) {
    }

    public record Candidate(String baseUrl, String suggestedCode, boolean codeExists, List<Usage> usages) {
    }

    public record ApplyResult(String code, boolean variableCreated, int updatedAssets, int updatedNodes) {
    }

    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;
    @Resource
    private FlowTaskRepository flowTaskRepository;
    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;
    @Resource
    private SysEnvVariableRepository sysEnvVariableRepository;
    @Resource
    private SysEnvVariableService sysEnvVariableService;
    @Resource
    private RbacService rbacService;
    @Resource
    private AssetEditLockAspect assetEditLock;
    @Resource
    private AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        Map<String, List<Usage>> byBase = new LinkedHashMap<>();
        forEachDraft((type, id, name, dsl) -> collect(dsl, url -> {
            Matcher m = BASE_URL.matcher(url);
            if (m.find()) {
                byBase.computeIfAbsent(m.group(1).toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                        .add(new Usage(type, id, name, url));
            }
        }));
        List<Candidate> result = new ArrayList<>();
        byBase.forEach((base, usages) -> {
            String code = suggestCode(base);
            result.add(new Candidate(base, code, sysEnvVariableRepository.existsByCode(code), usages));
        });
        result.sort((a, b) -> Integer.compare(b.usages().size(), a.usages().size()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplyResult apply(String baseUrl, String code, String remark) {
        if (assetEditLock.isLocked()) {
            throw new FlowException("ASSET_EDIT_LOCKED", "本环境已锁定资产编辑，请在开发测试环境抽取后通过发布包导入");
        }
        String base = StrUtil.trimToEmpty(baseUrl).replaceAll("/+$", "");
        if (!BASE_URL.matcher(base).matches()) {
            throw new FlowException("ENV_VAR_INVALID", "请提供形如 https://host[:port] 的地址前缀");
        }
        if (!EnvVarRefs.isValidCode(code)) {
            throw new FlowException("ENV_VAR_INVALID", "变量名须以大写字母开头，只能包含大写字母、数字、下划线");
        }
        boolean created = false;
        if (!sysEnvVariableRepository.existsByCode(code)) {
            SaveSysEnvVariableDTO dto = new SaveSysEnvVariableDTO();
            dto.setCode(code);
            dto.setValue(base);
            dto.setSecret(false);
            dto.setRemark(StrUtil.blankToDefault(remark, "从编排中抽取的地址前缀，各环境填写各自的值"));
            sysEnvVariableService.create(dto);
            created = true;
        }
        String replacement = "${env." + code + "}";
        int[] counts = new int[2];
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (FlowApiDO api : flowApiRepository.findAll()) {
            if (HostCatalogReserved.isReservedId(api.getId())) {
                continue;
            }
            String next = rewrite(api.getDslContent(), base, replacement, counts);
            if (next != null) {
                requirePerm("flow:api:write");
                api.setDslContent(next);
                api.setUpdateTime(now);
                flowApiRepository.save(api);
            }
        }
        for (FlowServiceFlowDO s : flowServiceFlowRepository.findAll()) {
            String next = rewrite(s.getDslContent(), base, replacement, counts);
            if (next != null) {
                requirePerm("flow:service:write");
                s.setDslContent(next);
                s.setUpdateTime(now);
                flowServiceFlowRepository.save(s);
            }
        }
        for (FlowTaskDO t : flowTaskRepository.findAll()) {
            String next = rewrite(t.getDslContent(), base, replacement, counts);
            if (next != null) {
                requirePerm("flow:task:write");
                t.setDslContent(next);
                t.setUpdateTime(now);
                flowTaskRepository.save(t);
            }
        }
        for (FlowMqTaskDO t : flowMqTaskRepository.findAll()) {
            String next = rewrite(t.getDslContent(), base, replacement, counts);
            if (next != null) {
                requirePerm("flow:mq:write");
                t.setDslContent(next);
                t.setUpdateTime(now);
                flowMqTaskRepository.save(t);
            }
        }
        auditLogService.record("ENV_VAR_EXTRACT", "ENV_VAR", code,
                "{\"code\":\"" + code + "\",\"assets\":" + counts[0] + ",\"nodes\":" + counts[1] + "}");
        return new ApplyResult(code, created, counts[0], counts[1]);
    }

    /** 由主机名生成变量名，如 pay.example.com:8443 → PAY_EXAMPLE_COM_8443_URL */
    static String suggestCode(String baseUrl) {
        String host = baseUrl.replaceFirst("(?i)^https?://", "");
        String code = host.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "") + "_URL";
        if (!Character.isLetter(code.charAt(0))) {
            code = "HOST_" + code;
        }
        return code.length() > 64 ? code.substring(0, 64) : code;
    }

    /** 返回改写后的 DSL；没有命中返回 null。counts[0] 资产数、counts[1] 节点数 */
    static String rewrite(String dsl, String base, String replacement, int[] counts) {
        if (StrUtil.isBlank(dsl) || !StrUtil.containsIgnoreCase(dsl, base)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(dsl);
            int[] hits = {0};
            walk(root, node -> {
                JsonNode url = node.get("url");
                if (url != null && url.isTextual() && url.asText().toLowerCase(Locale.ROOT).startsWith(base.toLowerCase(Locale.ROOT))) {
                    ((ObjectNode) node).put("url", replacement + url.asText().substring(base.length()));
                    hits[0]++;
                }
            });
            if (hits[0] == 0) {
                return null;
            }
            counts[0]++;
            counts[1] += hits[0];
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[EnvVarExtraction] DSL 解析失败，跳过: {}", e.getMessage());
            return null;
        }
    }

    private static void collect(String dsl, Consumer<String> urlConsumer) {
        if (StrUtil.isBlank(dsl) || !dsl.contains("http")) {
            return;
        }
        try {
            walk(MAPPER.readTree(dsl), node -> {
                JsonNode url = node.get("url");
                if (url != null && url.isTextual()) {
                    urlConsumer.accept(url.asText());
                }
            });
        } catch (Exception ignored) {
            // 非法 DSL 不影响其它资产的扫描
        }
    }

    /** 访问所有 httpRequest 节点的配置对象（画布格式在 data 下，引擎格式平铺在节点上） */
    private static void walk(JsonNode node, Consumer<JsonNode> httpConfigVisitor) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> walk(child, httpConfigVisitor));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode type = node.get("type");
        if (type != null && "httpRequest".equals(type.asText())) {
            JsonNode data = node.get("data");
            httpConfigVisitor.accept(data != null && data.isObject() ? data : node);
        }
        node.fields().forEachRemaining(e -> walk(e.getValue(), httpConfigVisitor));
    }

    private interface DraftVisitor {
        void visit(String type, String id, String name, String dsl);
    }

    private void forEachDraft(DraftVisitor visitor) {
        flowApiRepository.findAll().stream().filter(a -> !HostCatalogReserved.isReservedId(a.getId()))
                .forEach(a -> visitor.visit("API", a.getId(), a.getName(), a.getDslContent()));
        flowServiceFlowRepository.findAll().forEach(s -> visitor.visit("SERVICE", s.getId(), s.getName(), s.getDslContent()));
        flowTaskRepository.findAll().forEach(t -> visitor.visit("TASK", t.getId(), t.getName(), t.getDslContent()));
        flowMqTaskRepository.findAll().forEach(t -> visitor.visit("MQ_TASK", t.getId(), t.getName(), t.getDslContent()));
    }

    private void requirePerm(String code) {
        if (!rbacService.isRbacEnabled()) {
            return;
        }
        String user = JwtTokenUtil.currentUsername();
        if (StrUtil.isBlank(user) || !rbacService.hasAnyPerm(user, code)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权限修改相关资产: " + code);
        }
    }
}
