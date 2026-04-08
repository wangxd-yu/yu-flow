package org.yu.flow.auto.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.*;

import java.util.Map;

/**
 * 模板解析工具类
 * 基于Hutool和Beetl实现，支持动态SQL等模板渲染
 */
public class TemplateParser {

    private final TemplateEngine engine;

    /**
     * 默认构造，使用字符串模板模式
     */
    public TemplateParser() {
        this(new TemplateConfig());
    }

    /**
     * 自定义配置构造
     *
     * @param config 模板配置
     */
    public TemplateParser(TemplateConfig config) {
        this.engine = TemplateUtil.createEngine(config);
    }

    /**
     * 渲染模板
     *
     * @param templateContent 模板内容
     * @param params          参数Map
     * @return 渲染后的字符串
     * @throws TemplateException 模板异常
     */
    public String render(String templateContent, Map<String, Object> params) throws TemplateException {
        if (StrUtil.isBlank(templateContent)) {
            return "";
        }

        try {
            Template template = engine.getTemplate(templateContent);
            return template.render(params);
        } catch (Exception e) {
            throw new TemplateException("模板渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 安全渲染模板（捕获异常返回空字符串）
     *
     * @param templateContent 模板内容
     * @param params          参数Map
     * @return 渲染后的字符串，出错返回空字符串
     */
    public String renderSafe(String templateContent, Map<String, Object> params) {
        try {
            return render(templateContent, params);
        } catch (TemplateException e) {
            return "";
        }
    }

}
