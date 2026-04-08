package org.yu.flow.engine.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.yu.flow.engine.model.step.ParallelStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowDefinition {
    private String id;
    private String version;
    private String startStepId;
    //默认输入、常量值
    //private List<String> inputs = new ArrayList<>();
    private Map<String, Object> args;
    @JsonAlias("nodes")  // 支持 "nodes" 和 "steps" 两种字段名
    private List<Step> steps = new ArrayList<>();
    private Map<String, ErrorDefinition> errors = new HashMap<>();

    // O(1) 查找缓存
    private transient Map<String, Step> stepMapCache;

   /* public void addInput(String input) {
        inputs.add(input);
    }*/

    public void addStep(Step step) {
        steps.add(step);
    }

    public void addError(String code, ErrorDefinition error) {
        errors.put(code, error);
    }

    // 添加获取所有步骤的方法（包括嵌套步骤）
    public List<Step> getAllSteps() {
        List<Step> allSteps = new ArrayList<>();
        for (Step step : steps) {
            allSteps.add(step);
            if (step instanceof ParallelStep) {
                allSteps.addAll(((ParallelStep) step).getTasks());
            }
        }
        return allSteps;
    }

    /**
     * O(1) 根据 ID 获取 Step（懒加载缓存）
     */
    public Step getStep(String stepId) {
        if (stepMapCache == null) {
            synchronized (this) {
                if (stepMapCache == null) {
                    Map<String, Step> map = new ConcurrentHashMap<>();
                    for (Step step : steps) {
                        map.put(step.getId(), step);
                        if (step instanceof ParallelStep) {
                            for (Step task : ((ParallelStep) step).getTasks()) {
                                map.put(task.getId(), task);
                            }
                        }
                    }
                    stepMapCache = map;
                }
            }
        }
        return stepMapCache.get(stepId);
    }
}
