package org.yu.flow.module.open.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.domain.FlowOpenCredentialDO;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.open.dto.FlowOpenCredentialDTO;
import org.yu.flow.module.open.dto.FlowOpenPlatformDTO;
import org.yu.flow.module.open.dto.OpenGrantItemDTO;
import org.yu.flow.module.open.query.FlowOpenPlatformQueryDTO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenCredentialRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.open.service.FlowOpenPlatformService;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.open.support.IpAllowlistUtil;
import org.yu.flow.util.AesEncryptUtil;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowOpenPlatformServiceImpl implements FlowOpenPlatformService {

    @Resource
    private FlowOpenPlatformRepository platformRepository;
    @Resource
    private FlowOpenCredentialRepository credentialRepository;
    @Resource
    private FlowOpenApiGrantRepository grantRepository;
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private AesEncryptUtil aesEncryptUtil;
    @Resource
    private OpenPlatformCache openPlatformCache;
    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;
    @Resource
    private DemoModeGuard demoModeGuard;
    @Resource
    private AuditLogService auditLogService;

    @Override
    @Transactional
    public FlowOpenPlatformDO save(FlowOpenPlatformDO body) {
        if (body == null || StrUtil.isBlank(body.getName()) || StrUtil.isBlank(body.getCode())) {
            throw new FlowException("OPEN_PLATFORM_INVALID", "名称与编码不能为空");
        }
        body.setCode(body.getCode().trim());
        if (platformRepository.existsByCode(body.getCode())) {
            throw new FlowException("OPEN_PLATFORM_CODE_DUP", "平台编码已存在");
        }
        if (body.getStatus() == null) body.setStatus(1);
        body.setIpAllowlist(IpAllowlistUtil.normalizeAndValidate(body.getIpAllowlist()));
        if (body.getRateLimitQps() != null && body.getRateLimitQps() <= 0) {
            body.setRateLimitQps(null);
        }
        FlowOpenPlatformDO saved = platformRepository.save(body);
        openPlatformCache.publishRefresh();
        return saved;
    }

    @Override
    @Transactional
    public FlowOpenPlatformDO update(FlowOpenPlatformDO body) {
        demoModeGuard.checkModifyOrDelete(body.getId(), "开放平台");
        FlowOpenPlatformDO db = platformRepository.findById(body.getId())
                .orElseThrow(() -> new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在"));
        if (StrUtil.isNotBlank(body.getCode()) && !body.getCode().equals(db.getCode())) {
            if (platformRepository.existsByCodeAndIdNot(body.getCode().trim(), db.getId())) {
                throw new FlowException("OPEN_PLATFORM_CODE_DUP", "平台编码已存在");
            }
            db.setCode(body.getCode().trim());
        }
        if (StrUtil.isNotBlank(body.getName())) db.setName(body.getName());
        if (body.getStatus() != null) db.setStatus(body.getStatus());
        db.setContact(body.getContact());
        db.setRemark(body.getRemark());
        db.setIpAllowlist(IpAllowlistUtil.normalizeAndValidate(body.getIpAllowlist()));
        db.setExpireAt(body.getExpireAt());
        if (body.getOpenCallLogEnabled() != null) {
            db.setOpenCallLogEnabled(body.getOpenCallLogEnabled());
        }
        if (body.getRateLimitQps() != null) {
            db.setRateLimitQps(body.getRateLimitQps() <= 0 ? null : body.getRateLimitQps());
        }
        FlowOpenPlatformDO saved = platformRepository.save(db);
        openPlatformCache.publishRefresh();
        return saved;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "开放平台");
        grantRepository.deleteByPlatformId(id);
        credentialRepository.findByPlatformIdOrderByCreateTimeDesc(id)
                .forEach(c -> credentialRepository.delete(c));
        platformRepository.deleteById(id);
        openPlatformCache.publishRefresh();
    }

    @Override
    public FlowOpenPlatformDTO getById(String id) {
        FlowOpenPlatformDO d = platformRepository.findById(id)
                .orElseThrow(() -> new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在"));
        FlowOpenPlatformDTO dto = FlowOpenPlatformDTO.fromDO(d);
        dto.setCredentialCount(credentialRepository.countByPlatformIdAndStatus(id, FlowOpenCredentialDO.STATUS_ENABLED));
        dto.setGrantCount(grantRepository.countByPlatformId(id));
        return dto;
    }

    @Override
    public PageBean<FlowOpenPlatformDTO> page(FlowOpenPlatformQueryDTO query) {
        int page = query.getPage() == null ? 0 : Math.max(0, query.getPage());
        int size = query.getSize() == null ? 20 : Math.min(100, Math.max(1, query.getSize()));
        Specification<FlowOpenPlatformDO> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(query.getName())) {
                ps.add(cb.like(root.get("name"), "%" + query.getName().trim() + "%"));
            }
            if (StrUtil.isNotBlank(query.getCode())) {
                ps.add(cb.like(root.get("code"), "%" + query.getCode().trim() + "%"));
            }
            if (query.getStatus() != null) {
                ps.add(cb.equal(root.get("status"), query.getStatus()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<FlowOpenPlatformDO> result = platformRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<FlowOpenPlatformDTO> items = result.getContent().stream().map(d -> {
            FlowOpenPlatformDTO dto = FlowOpenPlatformDTO.fromDO(d);
            dto.setCredentialCount(credentialRepository.countByPlatformIdAndStatus(
                    d.getId(), FlowOpenCredentialDO.STATUS_ENABLED));
            dto.setGrantCount(grantRepository.countByPlatformId(d.getId()));
            return dto;
        }).collect(Collectors.toList());
        return new PageBean<>(items, result.getNumber(), result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    @Override
    @Transactional
    public FlowOpenCredentialDTO createCredential(String platformId) {
        demoModeGuard.checkModifyOrDelete(platformId, "开放平台");
        ensurePlatform(platformId);
        return issueCredential(platformId, null);
    }

    @Override
    @Transactional
    public FlowOpenCredentialDTO rotateCredential(String platformId, String credentialId) {
        demoModeGuard.checkModifyOrDelete(platformId, "开放平台");
        ensurePlatform(platformId);
        FlowOpenCredentialDO old = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new FlowException("OPEN_CRED_NOT_FOUND", "凭证不存在"));
        if (!platformId.equals(old.getPlatformId())) {
            throw new FlowException("OPEN_CRED_NOT_FOUND", "凭证不属于该平台");
        }
        int grace = yuFlowRuntimeSettings.getOpenRotateGraceHours();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        old.setStatus(FlowOpenCredentialDO.STATUS_ROTATED);
        if (grace > 0) {
            old.setExpireAt(now.plusHours(grace));
        } else {
            old.setExpireAt(now);
            old.setStatus(FlowOpenCredentialDO.STATUS_DISABLED);
        }
        credentialRepository.save(old);
        FlowOpenCredentialDTO neu = issueCredential(platformId, old.getId());
        openPlatformCache.publishRefresh();
        auditLogService.record("OPEN_SECRET_ROTATE", "OPEN_CREDENTIAL", platformId,
                "{\"oldCredId\":\"" + credentialId
                        + "\",\"newCredId\":\"" + (neu != null ? neu.getId() : "")
                        + "\",\"graceHours\":" + grace + "}");
        return neu;
    }

    @Override
    @Transactional
    public void disableCredential(String platformId, String credentialId) {
        demoModeGuard.checkModifyOrDelete(platformId, "开放平台");
        FlowOpenCredentialDO c = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new FlowException("OPEN_CRED_NOT_FOUND", "凭证不存在"));
        if (!platformId.equals(c.getPlatformId())) {
            throw new FlowException("OPEN_CRED_NOT_FOUND", "凭证不属于该平台");
        }
        c.setStatus(FlowOpenCredentialDO.STATUS_DISABLED);
        credentialRepository.save(c);
        openPlatformCache.publishRefresh();
    }

    @Override
    public List<FlowOpenCredentialDTO> listCredentials(String platformId) {
        return credentialRepository.findByPlatformIdOrderByCreateTimeDesc(platformId).stream()
                .map(FlowOpenCredentialDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listGrantedApiIds(String platformId) {
        return grantRepository.findByPlatformId(platformId).stream()
                .map(FlowOpenApiGrantDO::getApiId)
                .collect(Collectors.toList());
    }

    @Override
    public List<OpenGrantItemDTO> listGrantDetails(String platformId) {
        ensurePlatform(platformId);
        List<FlowOpenApiGrantDO> grants = grantRepository.findByPlatformId(platformId);
        if (grants.isEmpty()) {
            return List.of();
        }
        Set<String> apiIds = grants.stream().map(FlowOpenApiGrantDO::getApiId).collect(Collectors.toSet());
        Map<String, FlowApiDO> apiMap = flowApiRepository.findAllById(apiIds).stream()
                .collect(Collectors.toMap(FlowApiDO::getId, a -> a, (a, b) -> a));
        List<OpenGrantItemDTO> out = new ArrayList<>();
        for (FlowOpenApiGrantDO g : grants) {
            FlowApiDO api = apiMap.get(g.getApiId());
            boolean valid = api != null && api.getPublishStatus() != null && api.getPublishStatus() == 1;
            out.add(OpenGrantItemDTO.builder()
                    .apiId(g.getApiId())
                    .apiName(api != null ? api.getName() : null)
                    .method(api != null ? api.getMethod() : null)
                    .url(api != null ? api.getUrl() : null)
                    .allowMethods(g.getAllowMethods())
                    .publishStatus(api != null ? api.getPublishStatus() : 0)
                    .valid(valid)
                    .build());
        }
        return out;
    }

    @Override
    @Transactional
    public int purgeInvalidGrants(String platformId) {
        demoModeGuard.checkModifyOrDelete(platformId, "开放平台");
        ensurePlatform(platformId);
        List<OpenGrantItemDTO> details = listGrantDetails(platformId);
        List<OpenGrantItemDTO> keepDetails = details.stream()
                .filter(OpenGrantItemDTO::isValid)
                .collect(Collectors.toList());
        List<String> keep = keepDetails.stream().map(OpenGrantItemDTO::getApiId).collect(Collectors.toList());
        Map<String, String> methods = new LinkedHashMap<>();
        for (OpenGrantItemDTO d : keepDetails) {
            if (StrUtil.isNotBlank(d.getAllowMethods())) {
                methods.put(d.getApiId(), d.getAllowMethods());
            }
        }
        int before = details.size();
        replaceGrants(platformId, keep, methods);
        return Math.max(0, before - keep.size());
    }

    @Override
    @Transactional
    public void replaceGrants(String platformId, List<String> apiIds) {
        replaceGrants(platformId, apiIds, null);
    }

    @Override
    @Transactional
    public void replaceGrants(String platformId, List<String> apiIds, Map<String, String> allowMethodsByApiId) {
        demoModeGuard.checkModifyOrDelete(platformId, "开放平台");
        ensurePlatform(platformId);
        grantRepository.deleteByPlatformId(platformId);
        if (apiIds != null) {
            for (String apiId : apiIds) {
                if (StrUtil.isBlank(apiId)) continue;
                var api = flowApiRepository.findById(apiId)
                        .orElseThrow(() -> new FlowException("OPEN_API_NOT_FOUND", "接口不存在: " + apiId));
                if (api.getPublishStatus() == null || api.getPublishStatus() != 1) {
                    throw new FlowException("OPEN_API_NOT_PUBLISHED",
                            "仅可授权已发布接口: " + (StrUtil.blankToDefault(api.getName(), apiId)));
                }
                String allow = allowMethodsByApiId != null ? allowMethodsByApiId.get(apiId) : null;
                if (StrUtil.isNotBlank(allow)) {
                    allow = normalizeAllowMethods(allow);
                } else {
                    allow = null;
                }
                grantRepository.save(FlowOpenApiGrantDO.builder()
                        .platformId(platformId)
                        .apiId(apiId)
                        .allowMethods(allow)
                        .build());
            }
        }
        openPlatformCache.publishRefresh();
    }

    private static String normalizeAllowMethods(String raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            if (StrUtil.isNotBlank(part)) {
                set.add(part.trim().toUpperCase());
            }
        }
        return set.isEmpty() ? null : String.join(",", set);
    }

    private FlowOpenCredentialDTO issueCredential(String platformId, String rotatedFromId) {
        String appKey = "yf_" + IdUtil.fastSimpleUUID();
        String secret = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        String hint = secret.length() <= 4 ? secret : secret.substring(secret.length() - 4);
        FlowOpenCredentialDO cred = FlowOpenCredentialDO.builder()
                .platformId(platformId)
                .appKey(appKey)
                .appSecretEnc(aesEncryptUtil.encrypt(secret))
                .secretHint(hint)
                .status(FlowOpenCredentialDO.STATUS_ENABLED)
                .rotatedFromId(rotatedFromId)
                .build();
        credentialRepository.save(cred);
        openPlatformCache.publishRefresh();
        FlowOpenCredentialDTO dto = FlowOpenCredentialDTO.fromDO(cred);
        dto.setAppSecret(secret);
        return dto;
    }

    private void ensurePlatform(String platformId) {
        if (!platformRepository.existsById(platformId)) {
            throw new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在");
        }
    }
}
