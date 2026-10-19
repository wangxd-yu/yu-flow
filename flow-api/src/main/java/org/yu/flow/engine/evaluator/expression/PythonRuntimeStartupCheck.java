package org.yu.flow.engine.evaluator.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import java.util.List;
import java.util.Locale;

/**
 * 默认构建不带 GraalPy。白名单开了 python 却没有运行时，Python 节点会静默返回 null，
 * 启动时明确报出来，避免上线后才发现结果全是空。
 */
@Slf4j
@Component
public class PythonRuntimeStartupCheck implements ApplicationRunner {

    private final YuFlowRuntimeSettings runtimeSettings;

    public PythonRuntimeStartupCheck(YuFlowRuntimeSettings runtimeSettings) {
        this.runtimeSettings = runtimeSettings;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> languages = runtimeSettings.getScriptAllowedLanguages();
        if (languages == null || languages.stream().noneMatch(PythonRuntimeStartupCheck::isPython)) {
            return;
        }
        if (!PythonEvaluatorImpl.isRuntimeAvailable()) {
            log.error("[Python] 脚本白名单包含 python，但当前可执行包不含 GraalPy，Python 节点将返回 null。"
                    + "需要 Python 时用 mvn package -Pwith-python 重新打包；不需要则从 YU_FLOW_SCRIPT_ALLOWED_LANGUAGES 移除");
        }
    }

    private static boolean isPython(String language) {
        String l = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        return "python".equals(l) || "py".equals(l);
    }
}
