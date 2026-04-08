package org.yu.flow.auto.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.util.Objects;

/**
 * 雪花算法生成id。
 * 使用前提：服务器使用IPv4地址，多台服务器的最后一位不同
 * 基本逻辑：使用服务器IPv4地址的最后一位地址块作为变量 A 。机器id = A % 32; 数据中心id = A / 32。
 */
@Slf4j
public class SnowIdGenerator implements IdentifierGenerator {
    /**
     * ip地址
     */
    private static final Long hostAddress = Long.parseLong(NetUtil.getLocalhost().getHostAddress().split("\\.")[3]);
    private static volatile Snowflake snowflake;

    public SnowIdGenerator() {
    }

    /**
     * workId使用IP生成
     *
     * @return workId
     */
    private static Long getWorkId() {
        try {
            String hostAddress = NetUtil.getLocalhost().getHostAddress();
            int sum = 0;
            for (char c : hostAddress.toCharArray()) {
                sum += c; // 将字符转换为 ASCII 码并累加
            }
            return (long) (sum % 32);
        } catch (Exception e) {
            // 失败就随机
            return RandomUtil.randomLong(0, 31);
        }
    }

    /**
     * dataCenterId使用hostName生成
     *
     * @return dataCenterId
     */
    private static Long getDataCenterId() {
        try {
            String hostName = NetUtil.getLocalhost().getHostName();
            int sum = 0;
            for (char c : hostName.toCharArray()) {
                sum += c; // 将字符转换为 ASCII 码并累加
            }
            return (long) (sum % 32);
        } catch (Exception e) {
            // 失败就随机
            return RandomUtil.randomLong(0, 31);
        }
    }

    public static String getNextId() {
        return snowflake.nextIdStr();
    }

    @SneakyThrows
    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) {
        if (Objects.isNull(snowflake)) {
            synchronized (hostAddress) {
                if (Objects.isNull(snowflake)) {
                    log.info("=====雪花算法生成机器码，getworkId：" + getWorkId() + "getDataCenterId：" + getDataCenterId() + "=====");
                    snowflake = IdUtil.getSnowflake(getWorkId(), getDataCenterId());
                }
            }
        }
        return snowflake.nextIdStr();
    }

    public static String getId() {
        if (Objects.isNull(snowflake)) {
            synchronized (hostAddress) {
                if (Objects.isNull(snowflake)) {
                    log.info("=====雪花算法生成机器码，getworkId：" + getWorkId() + "getDataCenterId：" + getDataCenterId() + "=====");
                    snowflake = IdUtil.getSnowflake(getWorkId(), getDataCenterId());
                }
            }
        }
        return snowflake.nextIdStr();
    }
}
