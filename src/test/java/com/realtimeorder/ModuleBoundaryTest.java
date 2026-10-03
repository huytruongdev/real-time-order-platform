package com.realtimeorder;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforce module boundary của Modular Monolith (ADR-029, CLAUDE.md).
 *
 * Quy tắc: module khác chỉ được phụ thuộc vào package {@code application} của một module.
 * Package {@code api}, {@code domain}, {@code infrastructure} là internal.
 * Ví dụ: Order không được dùng {@code Product} entity hoặc {@code ProductRepository} của Catalog.
 */
class ModuleBoundaryTest {

    private static final String ROOT = "com.realtimeorder";
    private static final String[] MODULES = {"user", "catalog", "order", "payment", "delivery", "notification"};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @ParameterizedTest(name = "chỉ package application của module {0} được dùng từ bên ngoài")
    @ValueSource(strings = {"user", "catalog", "order", "payment", "delivery", "notification"})
    void otherModulesOnlyDependOnApplicationPackage(String module) {
        String modulePackage = ROOT + "." + module;

        noClasses()
                .that().resideOutsideOfPackage(modulePackage + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        modulePackage + ".api..",
                        modulePackage + ".domain..",
                        modulePackage + ".infrastructure..")
                .because("module " + module + " chỉ public package application cho module khác")
                // Module chưa có class (phase sau) thì rule không có gì để kiểm tra.
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void sharedDoesNotDependOnAnyModule() {
        String[] modulePackages = new String[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) {
            modulePackages[i] = ROOT + "." + MODULES[i] + "..";
        }

        noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(modulePackages)
                .because("shared là hạ tầng dùng chung, không được biết về business module")
                .allowEmptyShould(true)
                .check(classes);
    }
}
