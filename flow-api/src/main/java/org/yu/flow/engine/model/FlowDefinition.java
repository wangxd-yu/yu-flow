package org.yu.flow.engine.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowDefinition {
    private String id;
    private String version;
    private String startStepId;
    private Map<String, Object> args;
    @JsonAlias("nodes")
    private List<Step> steps = new ArrayList<>();
    private Map<String, ErrorDefinition> errors = new HashMap<>();

    private transient Map<String, Step> stepMapCache;

    /** 子节点 → 父节点列表（解析后挂载，只读共享） */
    private transient volatile Map<String, List<String>> parentMap;

    /** 并行分支节点 → 兄弟集合（解析后挂载，只读共享） */
    private transient volatile Map<String, Set<String>> parallelSiblings;

    public void addStep(Step step) {
        steps.add(step);
    }

    public void addError(String code, ErrorDefinition error) {
        errors.put(code, error);
    }

    public List<Step> getAllSteps() {
        return new ArrayList<>(steps);
    }

    /**
     * O(1) 根据 ID 获取 Step（懒加载缓存）
     */
    public Step getStep(String stepId) {
        ensureStepMapCache();
        return stepMapCache.get(stepId);
    }

    /**
     * 确保拓扑索引已构建（parentMap / parallelSiblings / stepMap）。
     * <p>可被 {@link org.yu.flow.engine.cache.FlowDefinitionCache} 与引擎共享同一实例调用，幂等。</p>
     */
    public void ensureTopologyIndexes() {
        if (parentMap != null && parallelSiblings != null) {
            ensureStepMapCache();
            return;
        }
        synchronized (this) {
            if (parentMap != null && parallelSiblings != null) {
                ensureStepMapCache();
                return;
            }
            ensureStepMapCache();
            this.parentMap = Collections.unmodifiableMap(buildParentMapping());
            this.parallelSiblings = Collections.unmodifiableMap(buildParallelSiblingsMapping());
        }
    }

    public Map<String, List<String>> getParentMap() {
        ensureTopologyIndexes();
        return parentMap;
    }

    public Map<String, Set<String>> getParallelSiblings() {
        ensureTopologyIndexes();
        return parallelSiblings;
    }

    private void ensureStepMapCache() {
        if (stepMapCache != null) {
            return;
        }
        synchronized (this) {
            if (stepMapCache != null) {
                return;
            }
            Map<String, Step> map = new ConcurrentHashMap<>();
            if (steps != null) {
                for (Step step : steps) {
                    if (step != null && step.getId() != null) {
                        map.put(step.getId(), step);
                    }
                }
            }
            stepMapCache = map;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> buildParentMapping() {
        Map<String, List<String>> raw = new HashMap<>();
        if (steps == null) {
            return Collections.emptyMap();
        }
        for (Step step : steps) {
            if (step == null || step.getNext() == null) {
                continue;
            }
            String parentId = step.getId();
            for (Object nextTarget : step.getNext().values()) {
                if (nextTarget instanceof String childId) {
                    raw.computeIfAbsent(childId, k -> new ArrayList<>()).add(parentId);
                } else if (nextTarget instanceof List) {
                    for (String childId : (List<String>) nextTarget) {
                        raw.computeIfAbsent(childId, k -> new ArrayList<>()).add(parentId);
                    }
                }
            }
        }
        Map<String, List<String>> frozen = new HashMap<>(raw.size());
        for (Map.Entry<String, List<String>> e : raw.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return frozen;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> buildParallelSiblingsMapping() {
        Map<String, Set<String>> siblingsMap = new HashMap<>();
        if (steps == null) {
            return Collections.emptyMap();
        }
        for (Step step : steps) {
            if (step == null || step.getNext() == null) {
                continue;
            }
            for (Object nextTarget : step.getNext().values()) {
                if (nextTarget instanceof List) {
                    List<String> parallelBranches = (List<String>) nextTarget;
                    Set<String> siblingsSet = Collections.unmodifiableSet(new HashSet<>(parallelBranches));
                    for (String branchId : parallelBranches) {
                        siblingsMap.put(branchId, siblingsSet);
                    }
                }
            }
        }
        return siblingsMap;
    }
}
