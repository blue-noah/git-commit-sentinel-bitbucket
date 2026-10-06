package com.github.bluenoah.commitsentinel.rules;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The exact texts developers read. */
class RuleMessagesTest {

    private final RuleSet sut = RuleSet.standard();

    private static final RuleConfig CONFIG = new RuleConfig(List.of("feat", "fix"), 20, Map.of());

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            format             | 'nope'                       | 'header "nope" does not match the expected format: <type>(<scope>)!: <description>'
            type               | 'wip: x'                     | 'type "wip" is not in the allowed list: feat, fix'
            description-empty  | 'feat: '                     | 'description must not be empty'
            description-period | 'fix: done.'                 | 'description must not end with a period'
            header-length      | 'feat: twenty-one chars!!'   | 'header is 24 characters long, exceeds the limit of 20'
            body-blank-line    | 'fix: x\\nbody'              | 'second line must be blank to separate header from body'
            """)
    void message(String ruleName, String commitMessage, String expectedMessage) {
        // when
        var violations = sut.violationsOf(commitMessage.replace("\\n", "\n"), CONFIG);

        // then
        then(violations)
                .filteredOn(violation -> violation.ruleName().equals(ruleName))
                .extracting(RuleViolation::message)
                .containsExactly(expectedMessage);
    }

    @Test
    void rulesQuoteTheHeaderAsWritten() {
        // given: neutralizing control characters is the job of whatever prints the message, not of the rules
        var header = "say \"hi\"\u001b[2J now";

        // when
        var violations = sut.violationsOf(header, CONFIG);

        // then
        then(violations)
                .extracting(RuleViolation::message)
                .first()
                .isEqualTo(
                        "header \"say \"hi\"\u001b[2J now\" does not match the expected format: <type>(<scope>)!: <description>");
    }
}
