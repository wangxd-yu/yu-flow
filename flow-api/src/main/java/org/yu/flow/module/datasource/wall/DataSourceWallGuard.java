package org.yu.flow.module.datasource.wall;

import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.spi.MySqlWallProvider;
import com.alibaba.druid.wall.spi.PGWallProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.datasource.dto.DataSourceWallConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 按数据源 code 缓存并执行 Druid Wall 校验（覆盖 [DEFAULT] 与业务源）。
 */
@Slf4j
@Component
public class DataSourceWallGuard {

    private final ConcurrentHashMap<String, CachedWall> cache = new ConcurrentHashMap<>();

    public void reload(String code, String dbType, DataSourceWallConfig config) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        if (config == null || !config.isEnabled()) {
            cache.remove(code);
            log.info("[DataSourceWallGuard] 已卸载安全墙, code={}", code);
            return;
        }
        WallConfig wallConfig = config.toDruidWallConfig();
        WallProvider provider = createProvider(dbType, wallConfig);
        cache.put(code, new CachedWall(provider, dbType));
        log.info("[DataSourceWallGuard] 已加载安全墙, code={}, dbType={}", code, dbType);
    }

    public void unload(String code) {
        if (StrUtil.isNotBlank(code)) {
            cache.remove(code);
        }
    }

    /**
     * 校验 SQL；未启用或无缓存时直接放行。
     *
     * @throws IllegalArgumentException 违规时抛出（含中文原因）
     */
    public void assertSqlAllowed(String code, String sql) {
        if (StrUtil.isBlank(code) || StrUtil.isBlank(sql)) {
            return;
        }
        CachedWall cached = cache.get(code);
        if (cached == null) {
            return;
        }
        WallCheckResult result = cached.provider.check(sql);
        if (result == null || result.getViolations() == null || result.getViolations().isEmpty()) {
            return;
        }
        String reason = result.getViolations().stream()
                .map(v -> {
                    String msg = v.getMessage();
                    return StrUtil.isNotBlank(msg) ? msg : v.toString();
                })
                .collect(Collectors.joining("；"));
        log.warn("[DataSourceWallGuard] SQL 被安全墙拦截, code={}, reason={}, sqlSnippet={}",
                code, reason, StrUtil.maxLength(sql, 200));
        throw new IllegalArgumentException("SQL 安全墙拦截：" + reason);
    }

    public boolean isEnabled(String code) {
        return StrUtil.isNotBlank(code) && cache.containsKey(code);
    }

    private WallProvider createProvider(String dbType, WallConfig wallConfig) {
        String t = StrUtil.blankToDefault(dbType, "mysql").toLowerCase();
        if (t.contains("postgre") || t.contains("highgo") || "pgsql".equals(t) || "pg".equals(t)) {
            return new PGWallProvider(wallConfig);
        }
        return new MySqlWallProvider(wallConfig);
    }

    private static final class CachedWall {
        private final WallProvider provider;
        private final String dbType;

        private CachedWall(WallProvider provider, String dbType) {
            this.provider = provider;
            this.dbType = dbType;
        }
    }
}
