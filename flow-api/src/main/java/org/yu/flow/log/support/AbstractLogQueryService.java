package org.yu.flow.log.support;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 日志分页查询基类（模板方法）
 * ─────────────────────────────────────────────────────────────
 * 五个日志模块（execution/task/service/open/third）的列表查询共享同一骨架：
 * page/size 归一化 → CriteriaBuilder 投影查询（cb.construct 只取摘要字段，
 * 不加载 LOB）→ where + createTime 倒序 → 分页 TypedQuery → 独立 count → PageImpl。
 * 子类只需提供实体/DTO 类型、投影字段与业务过滤条件。
 *
 * @param <E> 日志实体类型（DO）
 * @param <Q> 查询条件 DTO 类型
 * @param <L> 列表投影 DTO 类型
 */
public abstract class AbstractLogQueryService<E, Q, L> {

    protected static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 绑定到具体子类的 logger，warn 输出与原实现保持同名 logger */
    protected final Logger queryLog = LoggerFactory.getLogger(getClass());

    @PersistenceContext
    protected EntityManager entityManager;

    /** 日志实体类型 */
    protected abstract Class<E> entityClass();

    /** 列表投影 DTO 类型（需有与 selections 顺序一致的构造器） */
    protected abstract Class<L> listDtoClass();

    /** 投影字段列表，顺序必须与 ListDTO 构造器参数一致 */
    protected abstract List<Selection<?>> selections(CriteriaBuilder cb, Root<E> root);

    /** 业务过滤条件（数据查询与 count 查询各调用一次） */
    protected abstract List<Predicate> buildPredicates(Q query, Root<E> root, CriteriaBuilder cb);

    /** size 缺省值，默认 10（开放平台日志覆写为 20） */
    protected int defaultPageSize() {
        return 10;
    }

    /** size 上限，默认不限制（开放平台日志覆写为 100） */
    protected int maxPageSize() {
        return Integer.MAX_VALUE;
    }

    /**
     * 公共分页骨架：子类 pageList 直接委托本方法。
     */
    protected Page<L> pageQuery(Q query, Integer pageParam, Integer sizeParam) {
        int page = pageParam == null ? 0 : Math.max(pageParam, 0);
        int size = sizeParam == null ? defaultPageSize() : Math.min(Math.max(sizeParam, 1), maxPageSize());
        Pageable pageable = PageRequest.of(page, size);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<L> dataQuery = cb.createQuery(listDtoClass());
        Root<E> root = dataQuery.from(entityClass());
        List<Predicate> predicates = buildPredicates(query, root, cb);

        dataQuery.select(cb.construct(listDtoClass(), selections(cb, root).toArray(new Selection<?>[0])));
        dataQuery.where(predicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.desc(root.get("createTime")));

        TypedQuery<L> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<L> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<E> countRoot = countQuery.from(entityClass());
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(query, countRoot, cb).toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * {@code CASE WHEN traceData IS NOT NULL} 投影 hasTrace，不加载 LOB 内容。
     * HighGo/PG 下 IS NOT NULL 不会展开 TOAST/LONGTEXT。
     */
    protected Expression<Boolean> hasTraceExpression(CriteriaBuilder cb, Root<E> root) {
        return notNullFlagExpression(cb, root, "traceData");
    }

    /**
     * {@code CASE WHEN field IS NOT NULL} 投影布尔标志，不加载 LOB 内容。
     */
    protected Expression<Boolean> notNullFlagExpression(CriteriaBuilder cb, Root<E> root, String field) {
        return cb.<Boolean>selectCase()
                .when(cb.isNotNull(root.get(field)), cb.literal(true))
                .otherwise(cb.literal(false));
    }

    /**
     * createTime 时间范围条件：解析失败仅 warn 不拦截（沿用原各实现行为）。
     *
     * @param logTag warn 日志的模块前缀，如 {@code [ExecutionLog]}
     */
    protected void addCreateTimeRange(List<Predicate> predicates, Root<E> root, CriteriaBuilder cb,
                                      String startTime, String endTime, String logTag) {
        if (StrUtil.isNotBlank(startTime)) {
            try {
                LocalDateTime start = LocalDateTime.parse(startTime.trim(), DATE_TIME_FMT);
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
            } catch (DateTimeParseException e) {
                queryLog.warn("{} startTime 格式不合法, value={}", logTag, startTime);
            }
        }
        if (StrUtil.isNotBlank(endTime)) {
            try {
                LocalDateTime end = LocalDateTime.parse(endTime.trim(), DATE_TIME_FMT);
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
            } catch (DateTimeParseException e) {
                queryLog.warn("{} endTime 格式不合法, value={}", logTag, endTime);
            }
        }
    }

    /**
     * 静默解析时间：非法格式返回 null（开放平台日志沿用此容错风格）。
     */
    protected LocalDateTime parseTimeQuietly(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim(), DATE_TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
