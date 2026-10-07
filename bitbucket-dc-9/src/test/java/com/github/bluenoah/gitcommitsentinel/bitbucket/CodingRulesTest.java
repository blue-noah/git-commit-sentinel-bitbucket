package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import org.junit.jupiter.api.Test;

class CodingRulesTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(location -> !location.contains("-tests.jar"))
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.bluenoah.gitcommitsentinel.bitbucket");

    private final JavaClasses testClasses = new ClassFileImporter()
            .withImportOption(location -> !location.contains("-tests.jar"))
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("com.github.bluenoah.gitcommitsentinel.bitbucket");

    @Test
    void staticMethodsAreFactories() {
        // when / then
        CodingRules.STATIC_METHODS_ARE_FACTORIES.check(productionClasses);
    }

    @Test
    void staticFieldsAreFinal() {
        // when / then
        CodingRules.STATIC_FIELDS_ARE_FINAL.check(productionClasses);
        CodingRules.STATIC_FIELDS_ARE_FINAL.check(testClasses);
    }

    @Test
    void dependenciesAreInjectedThroughConstructorsOnly() {
        // when / then
        CodingRules.FIELDS_ARE_NOT_INJECTED.check(productionClasses);
        CodingRules.METHODS_ARE_NOT_INJECTED.check(productionClasses);
    }

    @Test
    void onlyPerPushObjectsHaveMutableFields() {
        // when / then
        CodingRules.ONLY_PER_PUSH_OBJECTS_HAVE_MUTABLE_FIELDS.check(productionClasses);
    }

    @Test
    void testsMockWithBddMockitoOnly() {
        // when / then
        CodingRules.TESTS_MOCK_WITH_BDD_MOCKITO_ONLY.check(testClasses);
    }

    @Test
    void testsAssertWithAssertjBddOnly() {
        // when / then
        CodingRules.TESTS_ASSERT_WITH_ASSERTJ_BDD_ONLY.check(testClasses);
    }

    @Test
    void testsGetClasspathFilesInjected() {
        // when / then
        CodingRules.TESTS_GET_CLASSPATH_FILES_INJECTED.check(testClasses);
    }

    @Test
    void onlyThePusherTerminalWritesToTheTerminal() {
        // given
        var rule = noClasses()
                .that()
                .doNotHaveSimpleName("PusherTerminal")
                .should()
                .callMethod(ScmHookDetails.class, "out");

        // when / then
        rule.check(productionClasses);
    }

    @Test
    void thePusherTerminalNeutralizesWhatItWrites() {
        // given
        var rule = classes()
                .that()
                .haveSimpleName("PusherTerminal")
                .should(ArchCondition.from(describe(
                        "use ControlCharacters",
                        terminal -> terminal.getDirectDependenciesFromSelf().stream()
                                .anyMatch(dependency -> dependency
                                        .getTargetClass()
                                        .getSimpleName()
                                        .equals("ControlCharacters")))));

        // when / then
        rule.check(productionClasses);
    }
}
