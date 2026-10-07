package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.bluenoah.gitcommitsentinel.bitbucket");

    @Test
    void dependenciesPointInwardsOnly() {
        // given
        var rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain")
                .definedBy("..domain..")
                .layer("Application")
                .definedBy("..application..")
                .layer("Adapter")
                .definedBy("..adapter..")
                .whereLayer("Adapter")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("Adapter")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Application", "Adapter");

        // when / then
        rule.check(productionClasses);
    }

    @Test
    void outboundAdaptersNeverDependOnInboundOnes() {
        // given
        var rule = noClasses()
                .that()
                .resideInAPackage("..adapter.outbound..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.inbound..");

        // when / then
        rule.check(productionClasses);
    }

    @Test
    void untrustedTextHandlingDependsOnTheJdkOnly() {
        // given
        var rule = classes()
                .that()
                .resideInAPackage("..adapter.text..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "..adapter.text..");

        // when / then
        rule.check(productionClasses);
    }
}
