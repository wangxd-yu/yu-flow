package org.yu.flow.module.oss.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yu.flow.module.oss.spi.FlowOssObjectAccessVoter;

import java.util.Optional;

@ConditionalOnOssEnabled
@Configuration
public class OssAccessVoterConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlowOssObjectAccessVoter.class)
    public FlowOssObjectAccessVoter noopFlowOssObjectAccessVoter() {
        return (principal, object, action) -> Optional.empty();
    }
}
