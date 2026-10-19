package org.yu.flow.module.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Ip2regionRegionResolverTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Ip2regionRegionResolver.class);

    @Test
    void formatDropsPlaceholdersAndRepeats() {
        assertEquals("中国-浙江省-杭州市-电信", Ip2regionRegionResolver.format("中国|0|浙江省|杭州市|电信"));
        assertEquals("内网IP", Ip2regionRegionResolver.format("0|0|0|内网IP|内网IP"));
        assertNull(Ip2regionRegionResolver.format("0|0|null"));
        assertNull(Ip2regionRegionResolver.format(""));
        assertNull(Ip2regionRegionResolver.format(null));
    }

    @Test
    void registeredOnlyWhenLibraryPresent() {
        runner.run(context -> assertThat(context).hasSingleBean(IpRegionResolver.class));
        runner.withClassLoader(new FilteredClassLoader("net.dreamlu"))
                .run(context -> assertThat(context).doesNotHaveBean(IpRegionResolver.class));
    }

    @Test
    void returnsNullWithoutSearcherBean() {
        runner.run(context -> assertNull(context.getBean(IpRegionResolver.class).resolve("1.2.3.4")));
    }
}
