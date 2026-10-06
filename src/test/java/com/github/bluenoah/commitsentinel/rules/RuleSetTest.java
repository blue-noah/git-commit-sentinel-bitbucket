package com.github.bluenoah.commitsentinel.rules;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cases ported from git-commit-sentinel's validate_test.go. */
class RuleSetTest {

    private final RuleSet sut = RuleSet.standard();

    private static final RuleConfig DEFAULTS =
            new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, RuleConfig.DEFAULT_HEADER_MAX_LENGTH, Map.of());

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "NONE", textBlock = """
            valid feat                       | 'feat: add login endpoint'                      | false | NONE
            valid fix with scope             | 'fix(auth): handle expired tokens'              | false | NONE
            valid breaking change            | 'feat(api)!: remove deprecated field'           | false | NONE
            valid with body                  | 'fix: correct off-by-one error\\n\\nThis fixes issue #42.' | false | NONE
            missing colon                    | 'feat add login endpoint'                       | true  | format
            unknown type                     | 'wip: work in progress'                         | true  | type
            empty description                | 'feat: '                                        | true  | description-empty
            trailing period is only a warn   | 'fix: correct the bug.'                         | false | description-period
            missing blank line before body   | 'fix: correct bug\\nsee also #1'                | false | body-blank-line
            empty message                    | ''                                              | true  | format
            uppercase type is rejected       | 'Feat: add thing'                               | true  | type
            empty scope is a format error    | 'feat(): add thing'                             | true  | format
            breaking change without a scope  | 'feat!: remove deprecated field'                | false | NONE
            CRLF normalized like LF          | 'feat: add thing\\r\\n\\r\\nbody line'          | false | NONE
            """)
    void defaults(String name, String escapedMessage, boolean anErrorExpected, String onlyRuleExpectedToFire) {
        // given
        var commitMessage = escapedMessage.replace("\\r", "\r").replace("\\n", "\n");

        // when
        var violations = sut.violationsOf(commitMessage, DEFAULTS);

        // then: exactly the expected rule fires, since a rule firing on a message it should ignore is a bug too
        then(violations.stream().anyMatch(RuleViolation::isError)).isEqualTo(anErrorExpected);
        then(violations)
                .extracting(RuleViolation::ruleName)
                .isEqualTo(onlyRuleExpectedToFire == null ? List.of() : List.of(onlyRuleExpectedToFire));
    }

    @ParameterizedTest(name = "{0} -> error: {1}")
    @CsvSource({"'task: do something', false", "'feat: add thing', true"})
    void customTypesReplaceTheDefaults(String commitMessage, boolean anErrorExpected) {
        // given
        var onlyTask = new RuleConfig(List.of("task"), 100, Map.of());

        // when
        var violations = sut.violationsOf(commitMessage, onlyTask);

        // then
        then(violations.stream().anyMatch(RuleViolation::isError)).isEqualTo(anErrorExpected);
    }

    @Test
    void ruleSetToOffNeverFires() {
        // given
        var typeOff = new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, 100, Map.of("type", Level.OFF));

        // when
        var violations = sut.violationsOf("wip: work in progress", typeOff);

        // then
        then(violations).extracting(RuleViolation::ruleName).doesNotContain("type");
    }

    @Test
    void warnRuleCanBeEscalatedToError() {
        // given
        var periodIsAnError =
                new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, 100, Map.of("description-period", Level.ERROR));

        // when
        var violations = sut.violationsOf("fix: correct the bug.", periodIsAnError);

        // then
        then(violations.stream().anyMatch(RuleViolation::isError)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> fires: {1}")
    @CsvSource({"'feat: exactly twenty', false", "'feat: exactly twenty1', true"})
    void headerAtTheLimitPassesAndOneOverFires(String header, boolean headerLengthExpectedToFire) {
        // given
        var limitOfTwentyOneCharacters =
                new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, "feat: exactly twenty".length(), Map.of());

        // when
        var violations = sut.violationsOf(header, limitOfTwentyOneCharacters);

        // then
        then(violations.stream().map(RuleViolation::ruleName).anyMatch("header-length"::equals))
                .isEqualTo(headerLengthExpectedToFire);
    }

    @Test
    void headerLengthCountsCharactersNotUtf16Units() {
        // given: 9 characters, 12 UTF-16 chars
        var header = "feat: 🎉🎉🎉";
        var limitOfNineCharacters = new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, 9, Map.of());

        // when
        var violations = sut.violationsOf(header, limitOfNineCharacters);

        // then
        then(violations).extracting(RuleViolation::ruleName).doesNotContain("header-length");
    }

    @Test
    void nullMessageIsAFormatErrorNotACrash() {
        // when
        var violations = sut.violationsOf(null, DEFAULTS);

        // then
        then(violations).extracting(RuleViolation::ruleName).contains("format");
    }

    @Test
    void everyRuleHasANameAndADefaultLevel() {
        // when
        var rules = sut.rulesInReportingOrder();

        // then
        then(rules)
                .extracting(Rule::name)
                .allSatisfy(name -> then(name).isNotBlank())
                .doesNotHaveDuplicates();
        then(rules).extracting(Rule::defaultLevel).doesNotContainNull();
    }

    @Test
    void everyPermittedRuleIsRunExactlyOnce() {
        // when
        var classesRun =
                sut.rulesInReportingOrder().stream().map(Object::getClass).toList();

        // then
        then(classesRun).doesNotHaveDuplicates().containsExactlyInAnyOrder(Rule.class.getPermittedSubclasses());
    }
}
