package org.yu.flow.module.auth;

import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.core.IpInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 基于 mica-ip2region 离线库。类路径上没有这个库时整个类不加载（条件按类名判断）。
 */
@Component
@ConditionalOnClass(name = "net.dreamlu.mica.ip2region.core.Ip2regionSearcher")
public class Ip2regionRegionResolver implements IpRegionResolver {

    private final ObjectProvider<Ip2regionSearcher> searcher;

    public Ip2regionRegionResolver(ObjectProvider<Ip2regionSearcher> searcher) {
        this.searcher = searcher;
    }

    @Override
    public String resolve(String ip) {
        Ip2regionSearcher s = searcher.getIfAvailable();
        if (s == null || ip == null) {
            return null;
        }
        IpInfo info = s.memorySearch(ip);
        return info == null ? null : format(info.getAddress());
    }

    /**
     * 去掉 ip2region 地址里的「0」占位和连续重复段，例如 {@code 中国|0|浙江省|杭州市|电信}。
     */
    static String format(String rawAddress) {
        if (rawAddress == null || rawAddress.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String lastPart = null;
        for (String part : rawAddress.split("\\|")) {
            if (part == null || part.isEmpty() || "0".equals(part) || "null".equalsIgnoreCase(part)
                    || part.equals(lastPart)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("-");
            }
            sb.append(part);
            lastPart = part;
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
