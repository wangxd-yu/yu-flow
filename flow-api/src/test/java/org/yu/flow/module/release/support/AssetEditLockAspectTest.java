package org.yu.flow.module.release.support;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetEditLockAspectTest {

    private final YuFlowProperties props = new YuFlowProperties();
    private final AssetEditLockAspect aspect = new AssetEditLockAspect();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aspect, "yuFlowProperties", props);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static JoinPoint call(String method) {
        JoinPoint jp = mock(JoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getName()).thenReturn(method);
        when(jp.getSignature()).thenReturn(sig);
        return jp;
    }

    @Test
    void unlockedByDefault() {
        assertDoesNotThrow(() -> aspect.check(call("publish")));
    }

    @Test
    void lockedBlocksContentChangesButNotOpsToggles() {
        props.getRelease().setLockAssetEditing(true);
        FlowException ex = assertThrows(FlowException.class, () -> aspect.check(call("publish")));
        assertEquals("ASSET_EDIT_LOCKED", ex.getErrorCode());
        assertThrows(FlowException.class, () -> aspect.check(call("update")));
        assertDoesNotThrow(() -> aspect.check(call("updateLogEnabled")));
        assertDoesNotThrow(() -> aspect.check(call("enable")));
        assertDoesNotThrow(() -> aspect.check(call("findPage")));
    }

    @Test
    void importFlowAndNonRequestCallsBypass() {
        props.getRelease().setLockAssetEditing(true);
        assertDoesNotThrow(() -> AssetEditLock.runUnlocked(() -> {
            aspect.check(call("publish"));
            return null;
        }));
        RequestContextHolder.resetRequestAttributes();
        assertDoesNotThrow(() -> aspect.check(call("save")));
    }

    @Test
    void newlyCoveredAssetsAreLocked() {
        props.getRelease().setLockAssetEditing(true);
        for (String method : List.of("uploadExcelTemplate", "replaceGrants", "createRule", "deleteSuite", "importFromDdl")) {
            assertThrows(FlowException.class, () -> aspect.check(call(method)), method);
        }
        for (String method : List.of("rotateCredential", "createChannel", "runSuite", "exportExcel")) {
            assertDoesNotThrow(() -> aspect.check(call(method)), method);
        }
    }

    /** 查询、运行、启停与环境内操作：锁定时照常可用 */
    private static final Set<String> ALLOWED = Set.of(
            "existsByUrlAndMethod", "findAll", "findAllUrls", "findById", "findByName", "findByPublishStatus", "findByUrl",
            "findPage", "findPublishApi", "listVersions", "updateCacheConfig", "updateLogEnabled",
            "enable", "disable", "listReferenceLabels", "existsByRoutePath", "getAllActiveMacros",
            "createExportLink", "downloadExcelTemplate", "downloadSampleExcelTemplate", "exportBySignedToken", "exportExcel",
            "exportPublishedOpenExcel", "getExcelTemplateMeta", "preview",
            "createCredential", "rotateCredential", "disableCredential", "getById", "listCredentials", "listGrantDetails",
            "listGrantedApiIds", "page",
            "createChannel", "updateChannel", "deleteChannel", "testChannel", "listChannels", "listEnabledRules",
            "pageChannels", "pageEvents", "pageRules", "runRuleOnce",
            "assertDirectoryBizType", "getAllChildIds", "getTree", "resolveDirectoryPrivacyOverrides",
            "resolveDirectorySecurityOverrides", "resolveEffectivePathPrefix", "refreshDirectoryChainCache",
            "batchRun", "checkGate", "currentEnv", "getRun", "getSuite", "listEnvs", "pageRuns", "pageSuites", "runSuite");

    /**
     * 切点覆盖的接口新增方法时必须在锁定 / 放行中归类，避免新写方法悄悄绕过编辑锁。
     */
    @Test
    void everyMethodOfCoveredInterfacesIsClassified() throws Exception {
        Matcher m = Pattern.compile("execution\\(\\* ([\\w.]+)\\.\\*\\(\\.\\.\\)\\)").matcher(AssetEditLockAspect.POINTCUT);
        List<String> unclassified = new ArrayList<>();
        int interfaces = 0;
        while (m.find()) {
            interfaces++;
            Class<?> type = Class.forName(m.group(1));
            for (Method method : type.getMethods()) {
                String name = method.getName();
                if (!AssetEditLockAspect.LOCKED_METHODS.contains(name) && !ALLOWED.contains(name)) {
                    unclassified.add(type.getSimpleName() + "." + name);
                }
            }
        }
        assertEquals(13, interfaces);
        assertTrue(unclassified.isEmpty(), "请在 AssetEditLockAspect.LOCKED_METHODS 或测试的 ALLOWED 中归类: " + unclassified);
        Before before = AssetEditLockAspect.class.getMethod("check", JoinPoint.class).getAnnotation(Before.class);
        assertEquals(AssetEditLockAspect.POINTCUT, before.value());
    }
}
