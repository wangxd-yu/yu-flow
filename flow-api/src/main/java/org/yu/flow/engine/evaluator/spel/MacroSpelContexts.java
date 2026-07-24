package org.yu.flow.engine.evaluator.spel;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.yu.flow.config.YuFlowProperties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统宏 / 系统变量 SpEL 上下文构建：强制 {@link SafeTypeLocator}，
 * Bean 解析仅允许白名单（默认含 {@code environment}，另加 {@code script-allowed-beans}）。
 */
public final class MacroSpelContexts {

    /** 内置安全 Bean：GET_ENV 等宏依赖 @environment */
    private static final Set<String> BUILTIN_BEANS = Set.of("environment");

    private MacroSpelContexts() {
    }

    public static StandardEvaluationContext create() {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setTypeLocator(new SafeTypeLocator());
        ctx.setBeanResolver(allowlistBeanResolver());
        return ctx;
    }

    /** 带根对象的安全上下文（流程变量 / JSON 节点等）。 */
    public static StandardEvaluationContext create(Object rootObject) {
        StandardEvaluationContext ctx = create();
        if (rootObject != null) {
            ctx.setRootObject(rootObject);
        }
        return ctx;
    }

    private static BeanResolver allowlistBeanResolver() {
        Set<String> allow = new LinkedHashSet<>(BUILTIN_BEANS);
        try {
            YuFlowProperties props = SpringUtil.getBean(YuFlowProperties.class);
            if (props != null && props.getSecurity() != null) {
                List<String> configured = props.getSecurity().getScriptAllowedBeans();
                if (configured != null) {
                    for (String name : configured) {
                        if (StrUtil.isNotBlank(name)) {
                            allow.add(name.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 测试无 Spring 时仅内置白名单
        }

        BeanFactory factory;
        try {
            factory = SpringUtil.getBeanFactory();
        } catch (Exception e) {
            factory = null;
        }
        if (factory == null) {
            return (context, beanName) -> {
                throw new AccessException("BeanFactory unavailable");
            };
        }
        BeanResolver delegate = new BeanFactoryResolver(factory);
        Set<String> immutable = Collections.unmodifiableSet(allow);
        return (EvaluationContext context, String beanName) -> {
            if (beanName == null || !immutable.contains(beanName)) {
                throw new AccessException("SpEL Bean not allowed: " + beanName
                        + "（请加入 yu.flow.security.script-allowed-beans）");
            }
            return delegate.resolve(context, beanName);
        };
    }
}
