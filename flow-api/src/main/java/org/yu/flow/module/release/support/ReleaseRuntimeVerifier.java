package org.yu.flow.module.release.support;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.host.HostCatalogReserved;
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
 * 导入提交后的运行时自检：已发布的接口是否进了路由表、已启用的定时任务是否注册了调度、
 * 已启用的 MQ 任务是否订阅成功。
 *
 * <p>路由刷新、调度注册与订阅在事务提交后执行，失败只记日志；数据已落库无法回滚，这里把问题带回导入结果，
 * 避免「导入成功」而接口 404、任务实际没有运行。只反映本节点的状态（本节点在提交回调里已同步刷新路由）。</p>
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
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowApiCacheManager flowApiCacheManager;

    public List<String> verify(AssetBundle bundle) {
        List<String> issues = new ArrayList<>();
        for (FlowApiDO a : nullSafe(bundle.getApis())) {
            if (a == null || a.getId() == null || HostCatalogReserved.isReservedId(a.getId())) {
                continue;
            }
            flowApiRepository.findById(a.getId())
                    .filter(api -> isPublished(api.getPublishStatus()))
                    .ifPresent(api -> checkRoute(api, issues));
        }
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

    private void checkRoute(FlowApiDO api, List<String> issues) {
        String method = PublishedApiSnapshot.resolveMethod(api);
        String url = PublishedApiSnapshot.resolveUrl(api);
        String name = PublishedApiSnapshot.resolveName(api);
        String label = "接口「" + (name == null ? api.getId() : name) + "」";
        if (method == null || url == null) {
            issues.add(label + "已发布但发布快照里没有请求方法或路径，网关无法登记路由");
            return;
        }
        FlowApiDO owner = flowApiCacheManager.routeOwner(method, url);
        if (owner == null || !flowApiCacheManager.isRouted(api.getId())) {
            issues.add(label + "已发布但本节点路由表里没有（" + method.toUpperCase() + " " + url + "），请查看服务日志");
        } else if (!api.getId().equals(owner.getId())) {
            issues.add(label + "的路由 " + method.toUpperCase() + " " + url + " 被另一个接口「"
                    + owner.getName() + "」占用，请求会走到那个接口");
        }
    }

    private static boolean isPublished(Integer status) {
        return status != null && status == 1;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
