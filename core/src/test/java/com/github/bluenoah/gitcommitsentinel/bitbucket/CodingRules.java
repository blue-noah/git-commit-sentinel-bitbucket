package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

public final class CodingRules {

    public static final ArchRule STATIC_METHODS_ARE_FACTORIES = methods()
            .that()
            .areStatic()
            .and(describe(
                    "are written by hand", method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC)))
            .and(describe(
                    "are not an enum's values()",
                    method -> !(method.getOwner().isEnum() && method.getName().equals("values"))))
            .should(ArchCondition.from(describe(
                    "return an instance of their own class",
                    method -> method.getRawReturnType().equals(method.getOwner()))));

    public static final ArchRule STATIC_FIELDS_ARE_FINAL =
            fields().that().areStatic().should().beFinal();

    public static final ArchRule FIELDS_ARE_NOT_INJECTED = noFields()
            .should()
            .beAnnotatedWith("javax.inject.Inject")
            .orShould()
            .beAnnotatedWith("jakarta.inject.Inject");

    public static final ArchRule METHODS_ARE_NOT_INJECTED = noMethods()
            .should()
            .beAnnotatedWith("javax.inject.Inject")
            .orShould()
            .beAnnotatedWith("jakarta.inject.Inject");

    public static final ArchRule ONLY_PER_PUSH_OBJECTS_HAVE_MUTABLE_FIELDS = fields().that()
            .areNotStatic()
            .and()
            .areDeclaredInClassesThat(describe(
                    "are shared between pushes",
                    owner -> !Set.of("PushedCommitsCheck", "CappedReport", "PushedCommitsListener")
                            .contains(owner.getSimpleName())))
            .should()
            .beFinal();

    public static final ArchRule TESTS_MOCK_WITH_BDD_MOCKITO_ONLY =
            noClasses().should().accessClassesThat().haveFullyQualifiedName("org.mockito.Mockito");

    public static final ArchRule TESTS_ASSERT_WITH_ASSERTJ_BDD_ONLY = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.assertj.core.api.Assertions")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.junit.jupiter.api.Assertions");

    public static final ArchRule TESTS_GET_CLASSPATH_FILES_INJECTED = noClasses()
            .should()
            .callMethod(Class.class, "getResourceAsStream", String.class)
            .orShould()
            .callMethod(ClassLoader.class, "getResourceAsStream", String.class);

    private CodingRules() {}
}
