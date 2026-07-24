package org.yu.flow.module.datasource.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.yu.flow.module.datasource.service.DynamicDataSourceService.DataSourceCallback;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FLOW 调试 / 回归期间的业务库写回滚作用域（线程级）。
 *
 * <p>同一线程、同一数据源上的多次 SQL 加入同一事务，流程内可读到未提交写入；
 * {@link #close()} 时统一 rollback，不落库。</p>
 *
 * <p>并行 For 分支请在子线程内单独 {@link #open()}/{@link #close()}（线程池不会自动继承）。</p>
 */
public final class FlowDbRollbackScope {

    private static final ThreadLocal<State> TL = new ThreadLocal<>();

    private FlowDbRollbackScope() {
    }

    public static boolean isActive() {
        State s = TL.get();
        return s != null && s.active;
    }

    public static void open() {
        State existing = TL.get();
        if (existing != null && existing.active) {
            existing.depth++;
            return;
        }
        State s = new State();
        s.active = true;
        s.depth = 1;
        TL.set(s);
    }

    /**
     * 结束作用域并回滚本线程上已开启的全部数据源事务。
     */
    public static void close() {
        State s = TL.get();
        if (s == null || !s.active) {
            return;
        }
        s.depth--;
        if (s.depth > 0) {
            return;
        }
        try {
            for (Map.Entry<String, TransactionStatus> e : s.statuses.entrySet()) {
                PlatformTransactionManager tm = s.managers.get(e.getKey());
                if (tm != null && e.getValue() != null && !e.getValue().isCompleted()) {
                    tm.rollback(e.getValue());
                }
            }
        } finally {
            TL.remove();
        }
    }

    /**
     * 在回滚作用域内执行；按数据源 code 复用未提交事务。
     */
    public static <T> T participate(String code,
                                    PlatformTransactionManager tm,
                                    JdbcTemplate jt,
                                    DataSourceCallback<T> callback) {
        State s = TL.get();
        if (s == null || !s.active) {
            throw new IllegalStateException("FlowDbRollbackScope 未开启");
        }
        TransactionStatus status = s.statuses.get(code);
        if (status == null || status.isCompleted()) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            status = tm.getTransaction(def);
            s.statuses.put(code, status);
            s.managers.put(code, tm);
        }
        return callback.doInDataSource(jt);
    }

    private static final class State {
        boolean active;
        int depth;
        final Map<String, TransactionStatus> statuses = new LinkedHashMap<>();
        final Map<String, PlatformTransactionManager> managers = new LinkedHashMap<>();
    }
}
