package org.yu.flow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 *
 * <p>绑定 {@code @Async("flowAsyncExecutor")}，替代 Spring 默认的 {@code SimpleAsyncTaskExecutor}。
 * <p>默认的 SimpleAsyncTaskExecutor 每次都创建新线程，在高并发下会导致线程数爆炸（OOM）。
 * <p>本配置采用有界线程池：核心线程 4，最大线程 8，队列容量 200，超出时使用调用方线程执行（CallerRunsPolicy）。
 */
@Configuration
public class AsyncConfig {

    @Bean("flowAsyncExecutor")
    public Executor flowAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：始终保持存活，处理常规异步任务
        executor.setCorePoolSize(4);
        // 最大线程数：队列满时最多扩展到此值
        executor.setMaxPoolSize(8);
        // 队列容量：超过此值后才会扩展线程数
        executor.setQueueCapacity(200);
        // 空闲线程存活时间（超过 core 数量的线程）
        executor.setKeepAliveSeconds(60);
        // 线程名前缀，便于排查日志
        executor.setThreadNamePrefix("flow-async-");
        // 拒绝策略：队列满且线程数达上限时，由调用方线程同步执行，确保日志不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时等待正在执行的任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
