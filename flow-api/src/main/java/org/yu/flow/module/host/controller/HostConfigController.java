package org.yu.flow.module.host.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.host.ConfigurableFlowHostPrincipalProvider;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostCatalogDimension;
import org.yu.flow.module.host.FlowHostIdentityCatalogProvider;
import org.yu.flow.module.host.FlowHostIdentityCatalogService;
import org.yu.flow.module.host.HostCatalogApiExecutor;
import org.yu.flow.module.host.HostCatalogDimBinding;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.host.HostIdentityCatalogSettings;
import org.yu.flow.module.host.HostIdentityCatalogSettingsStore;
import org.yu.flow.module.host.HostPrincipalResolver;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.host.HostPrivacyProfiles;
import org.yu.flow.module.host.HostPrivacyProfilesStore;
import org.yu.flow.module.host.dto.HostCatalogApiMetaDTO;
import org.yu.flow.module.host.dto.HostCatalogOverviewDTO;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;
import org.yu.flow.module.host.dto.HostPrincipalOverviewDTO;
import org.yu.flow.module.host.dto.HostPrincipalTestResultDTO;
import org.yu.flow.module.host.dto.HostPrivacyProfilesDTO;
import org.yu.flow.module.host.dto.SaveHostCatalogSettingsDTO;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 平台设置 · 宿主机配置（身份目录绑定 + 草稿预览）。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/host/config")
@RequirePerm({"sys:host:view", "sys:host:write"})
public class HostConfigController {

    @Resource
    private HostIdentityCatalogSettingsStore settingsStore;
    @Resource
    private HostCatalogApiExecutor hostCatalogApiExecutor;
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private ObjectProvider<FlowHostIdentityCatalogProvider> catalogProvider;
    @Resource
    private FlowHostIdentityCatalogService hostIdentityCatalogService;
    @Resource
    private org.yu.flow.module.host.HostCatalogApiBootstrap hostCatalogApiBootstrap;
    @Resource
    private HostPrincipalSettingsStore principalSettingsStore;
    @Resource
    private HostPrincipalResolver hostPrincipalResolver;
    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;
    @Resource
    private HostPrivacyProfilesStore privacyProfilesStore;

    @GetMapping
    public R<HostCatalogOverviewDTO> overview() {
        hostCatalogApiBootstrap.ensureReservedApis();
        HostIdentityCatalogSettings settings = settingsStore.load();
        HostCatalogOverviewDTO dto = new HostCatalogOverviewDTO();
        dto.setSpiOverride(catalogProvider.getIfAvailable() != null);
        Map<String, HostCatalogDimBinding> settingMap = new LinkedHashMap<>();
        Map<String, HostCatalogApiMetaDTO> apis = new LinkedHashMap<>();
        Map<String, FlowApiDO> apiById = flowApiRepository.findAllById(HostCatalogReserved.ids())
                .stream()
                .collect(Collectors.toMap(FlowApiDO::getId, Function.identity(), (a, b) -> a));
        for (FlowHostCatalogDimension d : FlowHostCatalogDimension.values()) {
            settingMap.put(d.name(), settings.get(d));
            HostCatalogReserved.Spec spec = HostCatalogReserved.spec(d);
            FlowApiDO api = apiById.get(spec.id());
            HostCatalogApiMetaDTO meta = new HostCatalogApiMetaDTO()
                    .setId(spec.id())
                    .setName(spec.name())
                    .setUrl(spec.url());
            if (api != null) {
                meta.setServiceType(api.getServiceType())
                        .setPublishStatus(api.getPublishStatus())
                        .setHasUnpublishedChanges(UnpublishedChangeDetector.apiHasUnpublishedChanges(api));
            } else {
                meta.setPublishStatus(0);
            }
            apis.put(d.name(), meta);
        }
        dto.setSettings(settingMap);
        dto.setApis(apis);
        return R.ok(dto);
    }

    @PutMapping
    public R<HostCatalogOverviewDTO> save(@RequestBody SaveHostCatalogSettingsDTO body) {
        HostIdentityCatalogSettings next = settingsStore.load();
        if (body != null && body.getSettings() != null) {
            for (Map.Entry<String, HostCatalogDimBinding> e : body.getSettings().entrySet()) {
                FlowHostCatalogDimension dim;
                try {
                    dim = FlowHostCatalogDimension.parse(e.getKey());
                } catch (FlowException ignore) {
                    continue;
                }
                HostCatalogDimBinding incoming = e.getValue() != null
                        ? e.getValue() : HostCatalogDimBinding.disabledDefault();
                HostCatalogDimBinding b = HostCatalogDimBinding.disabledDefault();
                b.setEnabled(incoming.isEnabled());
                b.setValueField(incoming.resolvedValueField());
                b.setLabelField(incoming.resolvedLabelField());
                b.setParentField(incoming.resolvedParentField());
                b.setSearchable(incoming.isSearchable());
                next.put(dim, b);
            }
        }
        settingsStore.save(next);
        hostIdentityCatalogService.invalidateRuntimeCache();
        return overview();
    }

    @GetMapping("/catalog/preview")
    public R<List<HostIdentityCatalogItemDTO>> preview(
            @RequestParam("dimension") String dimension,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok(hostCatalogApiExecutor.list(
                FlowHostCatalogDimension.parse(dimension), keyword, limit, true));
    }

    // ============================ 配置式主体解析 ============================

    @GetMapping("/principal")
    public R<HostPrincipalOverviewDTO> principalOverview() {
        hostCatalogApiBootstrap.ensureReservedApis();
        HostCatalogReserved.Spec spec = HostCatalogReserved.PRINCIPAL;
        HostCatalogApiMetaDTO meta = new HostCatalogApiMetaDTO()
                .setId(spec.id())
                .setName(spec.name())
                .setUrl(spec.url());
        FlowApiDO api = flowApiRepository.findById(spec.id()).orElse(null);
        if (api != null) {
            meta.setServiceType(api.getServiceType())
                    .setPublishStatus(api.getPublishStatus())
                    .setHasUnpublishedChanges(UnpublishedChangeDetector.apiHasUnpublishedChanges(api));
        } else {
            meta.setPublishStatus(0);
        }
        return R.ok(new HostPrincipalOverviewDTO()
                .setSpiOverride(!(flowHostAuthSupport.getPrincipalProvider()
                        instanceof ConfigurableFlowHostPrincipalProvider))
                .setSettings(principalSettingsStore.load())
                .setApi(meta)
                .setFields(HostPrincipalSettings.FIELDS)
                .setDefaultHeaderNames(HostPrincipalSettings.defaultHeaderNames()));
    }

    @PutMapping("/principal")
    public R<HostPrincipalOverviewDTO> savePrincipal(@RequestBody HostPrincipalSettings body) {
        HostPrincipalSettings next = body != null ? body : new HostPrincipalSettings();
        next.setMode(next.resolvedMode());
        next.setCacheSeconds(next.resolvedCacheSeconds());
        next.setForwardHeaders(next.resolvedForwardHeaders());
        if (next.isEnabled() && next.isHeaderMode() && !next.isTrustProxyHeaders()) {
            throw new FlowException("HOST_PRINCIPAL_UNTRUSTED_HEADER",
                    "请求头模式必须确认 Flow 不直接暴露公网，否则任何人都能伪造身份头");
        }
        principalSettingsStore.save(next);
        return principalOverview();
    }

    @PostMapping("/principal/test")
    public R<HostPrincipalTestResultDTO> testPrincipal(HttpServletRequest request) {
        hostCatalogApiBootstrap.ensureReservedApis();
        return R.ok(hostPrincipalResolver.test(request, principalSettingsStore.load(), true));
    }

    // ============================ 隐私解密 / 脱敏方案 ============================

    /**
     * 目录/接口下拉与宿主机编辑共用。密钥不回传。
     * 接口编排账号（flow:api:view）可读选项；写入仍要 sys:host:write。
     */
    @GetMapping("/privacy-profiles")
    @RequirePerm({"sys:host:view", "sys:host:write", "flow:api:view", "flow:api:write"})
    public R<HostPrivacyProfilesDTO> listPrivacyProfiles() {
        return R.ok(privacyProfilesStore.toView(privacyProfilesStore.load()));
    }

    @PutMapping("/privacy-profiles")
    public R<HostPrivacyProfilesDTO> savePrivacyProfiles(@RequestBody HostPrivacyProfiles body) {
        return R.ok(privacyProfilesStore.toView(privacyProfilesStore.save(body)));
    }
}
