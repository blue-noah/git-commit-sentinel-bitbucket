package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(location -> !location.contains("-tests.jar"))
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.bluenoah.gitcommitsentinel.bitbucket");

    @Test
    void theDomainDependsOnTheJdkOnly() {
        // given
        var rule = classes()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "..domain..");

        // when / then
        rule.check(productionClasses);
    }

    @Test
    void theApplicationDependsOnTheJdkAndTheDomainOnly() {
        // given
        var rule = classes()
                .that()
                .resideInAPackage("..application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "..domain..", "..application..");

        // when / then
        rule.check(productionClasses);
    }
}
