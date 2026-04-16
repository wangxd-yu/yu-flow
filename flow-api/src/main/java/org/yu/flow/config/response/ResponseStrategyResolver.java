package org.yu.flow.config.response;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.cache.ResponseTemplateCacheManager;

import javax.annotation.Resource;

/**
 * 响应模板合并策略解析器
 */
@Slf4j
@Component
public class ResponseStrategyResolver {

    @Resource
    private ResponseTemplateCacheManager responseTemplateCacheManager;

    /**
     * 根据 ApiConfig 解析出最终合并后的包装上下文
     */
    public ResponseWrapperContext resolve(FlowApiDO apiConfig) {
        ResponseTemplateDO baseTemplate = null;

        if (StrUtil.isNotBlank(apiConfig.getTemplateId())) {
            baseTemplate = responseTemplateCacheManager.getTemplateById(apiConfig.getTemplateId()).orElse(null);
        }

        // 如果没有指定或者没找到对应的基座模板，尝试获取默认基座模板
        if (baseTemplate == null) {
            baseTemplate = responseTemplateCacheManager.getDefaultTemplate().orElse(null);
        }

        ResponseWrapperContext context = new ResponseWrapperContext();

        // 合并策略：局部配置 (apiConfig.customXxx) > 基座模板 (baseTemplate.xxx)

        // 1. Success Wrapper
        if (StrUtil.isNotBlank(apiConfig.getCustomSuccessWrapper())) {
            context.setSuccessWrapper(apiConfig.getCustomSuccessWrapper());
        } else if (baseTemplate != null) {
            context.setSuccessWrapper(baseTemplate.getSuccessWrapper());
        }

        // 2. Page Wrapper
        if (StrUtil.isNotBlank(apiConfig.getCustomPageWrapper())) {
            context.setPageWrapper(apiConfig.getCustomPageWrapper());
        } else if (baseTemplate != null) {
            context.setPageWrapper(baseTemplate.getPageWrapper());
        }

        // 3. Fail Wrapper
        if (StrUtil.isNotBlank(apiConfig.getCustomFailWrapper())) {
            context.setFailWrapper(apiConfig.getCustomFailWrapper());
        } else if (baseTemplate != null) {
            context.setFailWrapper(baseTemplate.getFailWrapper());
        }

        return context;
    }
}
