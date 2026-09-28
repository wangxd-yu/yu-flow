package org.yu.flow.module.release.support;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.mqtask.consumer.MqConsumerManager;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入提交后的运行时自检：已启用的定时任务是否注册了调度、已启用的 MQ 任务是否订阅成功。
 *
 * <p>调度注册与订阅在事务提交后执行，失败只记日志；数据已落库无法回滚，这里把问题带回导入结果，
 * 避免「导入成功」而任务实际没有运行。只反映本节点的状态。</p>
 */
@Component
public class ReleaseRuntimeVerifier {

    @Resource
    private FlowTaskRepository flowTaskRepository;
    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;
    @Resource
    private FlowTaskScheduler flowTaskScheduler;
    @Resource
    private MqConsumerManager mqConsumerManager;
    @Resource
    private YuFlowProperties yuFlowProperties;

    public List<String> verify(AssetBundle bundle) {
        List<String> issues = new ArrayList<>();
        for (FlowTaskDO t : nullSafe(bundle.getTasks())) {
            flowTaskRepository.findById(t.getId())
                    .filter(task -> Boolean.TRUE.equals(task.getEnabled()) && isPublished(task.getPublishStatus()))
                    .filter(task -> !flowTaskScheduler.isScheduled(task.getId()))
                    .ifPresent(task -> issues.add("定时任务「" + task.getName() + "」已启用但没有注册调度（Cron 可能无效），请查看服务日志"));
        }
        boolean consumerEnabled = yuFlowProperties.getMq() == null || yuFlowProperties.getMq().isConsumerEnabled();
        for (FlowMqTaskDO t : nullSafe(bundle.getMqTasks())) {
            if (!consumerEnabled) {
                break;
            }
            flowMqTaskRepository.findById(t.getId())
                    .filter(task -> Boolean.TRUE.equals(task.getEnabled()) && isPublished(task.getPublishStatus()))
                    .filter(task -> !mqConsumerManager.isRunning(task.getId()))
                    .ifPresent(task -> issues.add("MQ 任务「" + task.getName() + "」已启用但订阅没有成功（连接可能未就绪），"
                            + "请检查 MQ 连接后在 MQ 任务页重新启用"));
        }
        return issues;
    }

    private static boolean isPublished(Integer status) {
        return status != null && status == 1;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
