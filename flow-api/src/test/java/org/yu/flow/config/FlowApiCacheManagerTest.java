package org.yu.flow.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.AntPathMatcher;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.host.HostCatalogReserved;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowApiCacheManagerTest {

    private final Map<String, FlowApiDO> db = new LinkedHashMap<>();
    private final FlowApiRepository repository = mock(FlowApiRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final FlowApiCacheManager manager = new FlowApiCacheManager();

    @BeforeEach
    void setUp() {
        when(repository.findByPublishStatus(1)).thenAnswer(inv -> db.values().stream()
                .filter(api -> Integer.valueOf(1).equals(api.getPublishStatus()))
                .collect(Collectors.toList()));
        when(repository.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<String> ids = inv.getArgument(0);
            List<FlowApiDO> rows = new ArrayList<>();
            for (String id : ids) {
                if (db.containsKey(id)) {
                    rows.add(db.get(id));
                }
            }
            return rows;
        });
        ReflectionTestUtils.setField(manager, "flowApiRepository", repository);
        ReflectionTestUtils.setField(manager, "stringRedisTemplate", redis);

        put(api("a", "GET", "/a"));
        put(api("b", "GET", "/b/{id}"));
        put(api("e", "POST", "/e"));
        put(api("off", "GET", "/off").setPublishStatus(0));
        manager.initCache();
        clearInvocations(repository, redis);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void initialLoadSplitsExactAndPatternRoutes() {
        assertEquals(2, manager.getExactCacheSize());
        assertEquals(1, manager.getPatternCacheSize());
        assertEquals("a", manager.getExactMatch("get", "a").getId());
        assertNull(manager.getExactMatch("GET", "/off"));

        FlowApiCacheManager.AntMatchResult match = manager.getPatternMatch("GET", "/b/7", new AntPathMatcher());
        assertEquals("b", match.getApi().getId());
        assertEquals("7", match.getPathVariables().get("id"));
    }

    @Test
    void refreshApisReplacesOnlyGivenIds() {
        db.get("a").setPublishedSnapshot("{\"url\":\"/a2\",\"method\":\"GET\"}");
        db.get("b").setPublishStatus(0);
        db.remove("e");
        put(api("d", "PUT", "/d"));
        put(api("untouched", "GET", "/untouched-in-db-only"));

        manager.refreshApis(List.of("a", "b", "d", "e"));

        assertNull(manager.getExactMatch("GET", "/a"));
        assertEquals("a", manager.getExactMatch("GET", "/a2").getId());
        assertEquals(0, manager.getPatternCacheSize());
        assertNull(manager.getExactMatch("POST", "/e"));
        assertEquals("d", manager.getExactMatch("PUT", "/d").getId());
        assertNull(manager.getExactMatch("GET", "/untouched-in-db-only"), "不在刷新范围里的接口不应被顺带加载");
        verify(repository, never()).findByPublishStatus(any());
    }

    @Test
    void routesByPublishedSnapshotWithoutMutatingEntity() {
        FlowApiDO draft = api("s", "GET", "draft-url")
                .setPublishedSnapshot("{\"url\":\"live\",\"method\":\"post\"}");
        put(draft);

        manager.refreshApis(List.of("s"));

        FlowApiDO routed = manager.getExactMatch("POST", "/live");
        assertNotNull(routed);
        assertNotSame(draft, routed);
        assertEquals("/live", routed.getUrl());
        assertEquals("POST", routed.getMethod());
        assertEquals("draft-url", draft.getUrl());
        assertEquals("GET", draft.getMethod());
        assertNull(manager.getExactMatch("GET", "/draft-url"));
    }

    @Test
    void patternPriorityFollowsIdAfterIncrementalRefresh() {
        put(api("p1", "GET", "/p/{x}"));
        put(api("p2", "GET", "/p/**"));
        manager.refreshCache();
        assertEquals("p1", manager.getPatternMatch("GET", "/p/q", new AntPathMatcher()).getApi().getId());

        manager.refreshApis(List.of("p1"));

        assertEquals("p1", manager.getPatternMatch("GET", "/p/q", new AntPathMatcher()).getApi().getId());
    }

    @Test
    void reservedHostCatalogApisAreNeverRouted() {
        put(api(HostCatalogReserved.DIR_ROLE, "GET", "/__sys/host-catalog/role"));

        manager.refreshApis(List.of(HostCatalogReserved.DIR_ROLE));

        assertFalse(manager.isRouted(HostCatalogReserved.DIR_ROLE));
    }

    @Test
    void sameTransactionMergesIdsIntoOneRefreshAndOneBroadcast() {
        TransactionSynchronizationManager.initSynchronization();
        manager.publishRefreshEvent("a");
        manager.publishRefreshEvent(List.of("b", "a"));
        manager.publishRefreshEvent(List.of(" ", "e"));
        verify(repository, never()).findAllById(anyIterable());
        verify(redis, never()).convertAndSend(anyString(), any());

        TransactionSynchronizationUtils.triggerAfterCommit();

        ArgumentCaptor<Iterable<String>> ids = idsCaptor();
        verify(repository, times(1)).findAllById(ids.capture());
        assertEquals(Set.of("a", "b", "e"), toSet(ids.getValue()));
        verify(redis, times(1)).convertAndSend(FlowApiCacheManager.REFRESH_TOPIC,
                "API;node=" + FlowApiCacheManager.NODE_ID + ";ids=a,b,e");
        verify(repository, never()).findByPublishStatus(any());
    }

    @Test
    void fullRequestInSameTransactionWins() {
        TransactionSynchronizationManager.initSynchronization();
        manager.publishRefreshEvent("a");
        manager.publishRefreshEvent();

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(repository, times(1)).findByPublishStatus(1);
        verify(repository, never()).findAllById(anyIterable());
        verify(redis).convertAndSend(FlowApiCacheManager.REFRESH_TOPIC, "REFRESH;node=" + FlowApiCacheManager.NODE_ID);
    }

    @Test
    void tooManyIdsFallBackToFullReload() {
        List<String> ids = IntStream.rangeClosed(0, FlowApiCacheManager.MAX_INCREMENTAL_IDS)
                .mapToObj(i -> "x" + i)
                .collect(Collectors.toList());

        manager.publishRefreshEvent(ids);

        verify(repository, times(1)).findByPublishStatus(1);
        verify(repository, never()).findAllById(anyIterable());
        verify(redis).convertAndSend(FlowApiCacheManager.REFRESH_TOPIC, "REFRESH;node=" + FlowApiCacheManager.NODE_ID);
    }

    @Test
    void outsideTransactionAppliesImmediately() {
        put(api("d", "GET", "/d"));

        manager.publishRefreshEvent("d");

        assertTrue(manager.isRouted("d"));
        verify(redis).convertAndSend(FlowApiCacheManager.REFRESH_TOPIC,
                "API;node=" + FlowApiCacheManager.NODE_ID + ";ids=d");
    }

    @Test
    void ownBroadcastIsSkipped() {
        manager.onRefreshMessage("API;node=" + FlowApiCacheManager.NODE_ID + ";ids=a");
        manager.onRefreshMessage("REFRESH;node=" + FlowApiCacheManager.NODE_ID);

        verify(repository, never()).findAllById(anyIterable());
        verify(repository, never()).findByPublishStatus(any());
    }

    @Test
    void otherNodeBroadcastRefreshesListedIds() {
        db.get("a").setPublishStatus(0);

        manager.onRefreshMessage("API;node=other;ids=a,e");

        ArgumentCaptor<Iterable<String>> ids = idsCaptor();
        verify(repository).findAllById(ids.capture());
        assertEquals(Set.of("a", "e"), toSet(ids.getValue()));
        assertFalse(manager.isRouted("a"));
        verify(repository, never()).findByPublishStatus(any());
    }

    @Test
    void legacyOrIncompleteMessagesReloadEverything() {
        manager.onRefreshMessage("REFRESH");
        manager.onRefreshMessage(null);
        manager.onRefreshMessage("API;node=other");
        manager.onRefreshMessage("garbage");

        verify(repository, times(4)).findByPublishStatus(1);
        verify(repository, never()).findAllById(anyIterable());
    }

    @Test
    void broadcastFailureKeepsLocalUpdate() {
        doThrow(new IllegalStateException("redis down"))
                .when(redis).convertAndSend(eq(FlowApiCacheManager.REFRESH_TOPIC), any());
        put(api("d", "GET", "/d"));

        assertDoesNotThrow(() -> manager.publishRefreshEvent("d"));

        assertTrue(manager.isRouted("d"));
    }

    @Test
    void incrementalFailureFallsBackToFullReload() {
        doThrow(new IllegalStateException("db hiccup")).when(repository).findAllById(anyIterable());
        put(api("d", "GET", "/d"));

        manager.refreshApis(List.of("d"));

        verify(repository, times(1)).findByPublishStatus(1);
        assertTrue(manager.isRouted("d"));
    }

    @Test
    void fullReloadFailureKeepsPreviousRoutes() {
        doThrow(new IllegalStateException("db down")).when(repository).findByPublishStatus(1);

        manager.refreshCache();

        assertTrue(manager.isRouted("a"));
        assertTrue(manager.isRouted("b"));
    }

    @Test
    void routeOwnerMatchesRegisteredRouteLiterally() {
        assertEquals("a", manager.routeOwner("get", "a").getId());
        assertEquals("b", manager.routeOwner("GET", "/b/{id}").getId());
        assertNull(manager.routeOwner("GET", "/b/7"));
        assertNull(manager.routeOwner("POST", "/a"));
        assertTrue(manager.isRouted("e"));
        assertFalse(manager.isRouted("off"));
    }

    private void put(FlowApiDO api) {
        db.put(api.getId(), api);
    }

    private static FlowApiDO api(String id, String method, String url) {
        return new FlowApiDO().setId(id).setMethod(method).setUrl(url).setPublishStatus(1);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Iterable<String>> idsCaptor() {
        return ArgumentCaptor.forClass(Iterable.class);
    }

    private static Set<String> toSet(Iterable<String> ids) {
        return StreamSupport.stream(ids.spliterator(), false).collect(Collectors.toCollection(HashSet::new));
    }
}
