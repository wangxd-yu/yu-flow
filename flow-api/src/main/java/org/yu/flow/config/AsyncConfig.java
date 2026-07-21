package org.yu.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步 / 调度 / 引擎并行线程池配置（均从 {@link YuFlowProperties} 读取，可运维调参）。
 *
 * <p>JDK 17 暂不启用虚拟线程；升级 JDK 21+ 后可评估 I/O 密集路径切 VT。</p>
 */
@Slf4j
@Configuration
public class AsyncConfig {

    @Bean("flowAsyncExecutor")
    public Executor flowAsyncExecutor(YuFlowProperties properties) {
        YuFlowProperties.Task task = properties.getTask();
        int core = Math.max(1, task.getAsyncCorePoolSize());
        int max = Math.max(core, task.getAsyncMaxPoolSize());
        int queue = Math.max(1, task.getAsyncQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("flow-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[AsyncConfig] flowAsyncExecutor: core={}, max={}, queue={}", core, max, queue);
        return executor;
    }

    /**
     * 定时任务调度器（注入 FlowTaskScheduler）
     */
    @Bean
    public TaskScheduler taskScheduler(YuFlowProperties properties) {
        int poolSize = Math.max(1, properties.getTask().getSchedulerPoolSize());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("flow-task-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        log.info("[AsyncConfig] taskScheduler: poolSize={}", poolSize);
        return scheduler;
    }

    /**
     * 引擎 For / Parallel 等并行执行池。
     */
    @Bean(name = "flowEngineExecutor", destroyMethod = "shutdown")
    public ExecutorService flowEngineExecutor(YuFlowProperties properties) {
        YuFlowProperties.Engine engine = properties.getEngine();
        int cpu = Runtime.getRuntime().availableProcessors();
        int core = engine.getPoolCoreSize() > 0 ? engine.getPoolCoreSize() : Math.max(2, cpu * 2);
        int max = engine.getPoolMaxSize() > 0 ? engine.getPoolMaxSize() : Math.max(core, cpu * 4);
        int queue = Math.max(16, engine.getPoolQueueCapacity());

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                core,
                max,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("flow-engine-" + t.getId());
                    t.setDaemon(false);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("[AsyncConfig] flowEngineExecutor: core={}, max={}, queue={} (JDK17 平台线程；VT 待 JDK21+)",
                core, max, queue);
        return pool;
    }
}
