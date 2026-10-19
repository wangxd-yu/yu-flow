package org.yu.flow;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * 包依赖规则。存量违规冻结在 {@code src/test/resources/archunit-store}，只许减少。
 */
class ArchitectureRulesTest {

    private static final String MODULE_PREFIX = "org.yu.flow.module.";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.yu.flow");

    @Test
    void controllersDoNotUseRepositoriesDirectly() {
        freeze(noClasses().that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("控制器经由 service 访问数据"))
                .check(CLASSES);
    }

    @Test
    void engineDoesNotDependOnModules() {
        freeze(noClasses().that().resideInAPackage("org.yu.flow.engine..")
                .should().dependOnClassesThat().resideInAPackage("org.yu.flow.module..")
                .because("引擎不感知管理端业务模块，需要的能力通过 engine 里的接口注入"))
                .check(CLASSES);
    }

    @Test
    void modulesDoNotReachIntoOtherModulesInternals() {
        freeze(classes().that().resideInAPackage("org.yu.flow.module..")
                .should(notDependOnRepositoriesOrImplsOfOtherModules())
                .because("跨模块走对方的 service 接口；实体和 DTO 可以共用"))
                .check(CLASSES);
    }

    private static ArchCondition<JavaClass> notDependOnRepositoriesOrImplsOfOtherModules() {
        return new ArchCondition<>("not depend on repositories or service implementations of other modules") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = moduleOf(origin.getPackageName());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    String to = moduleOf(targetPackage);
                    if (to == null || to.equals(from)) {
                        continue;
                    }
                    if (isInternal(targetPackage)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static boolean isInternal(String pkg) {
        return pkg.endsWith(".repository") || pkg.contains(".repository.")
                || pkg.endsWith(".impl") || pkg.contains(".impl.");
    }

    private static String moduleOf(String pkg) {
        if (!pkg.startsWith(MODULE_PREFIX)) {
            return null;
        }
        String rest = pkg.substring(MODULE_PREFIX.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
