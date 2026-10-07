package com.github.bluenoah.gitcommitsentinel.bitbucket.rules;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommitMessageTest {

    @Test
    void conventionalHeaderIsSplitIntoItsParts() {
        // given
        var commitMessage = "feat(api)!:  drop legacy tokens  \n\nbody";

        // when
        var parsed = CommitMessage.parse(commitMessage);

        // then
        then(parsed.headerIsConventional()).isTrue();
        then(parsed.type()).isEqualTo("feat");
        then(parsed.description()).isEqualTo("drop legacy tokens");
        then(parsed.lines()).containsExactly("feat(api)!:  drop legacy tokens  ", "", "body");
    }

    @ParameterizedTest(name = "{0} -> conventional: {1}")
    @CsvSource({
        "'fix: handle nulls', true",
        "'fix!: handle nulls', true",
        "'fix(api): handle nulls', true",
        "'fix(): handle nulls', false",
        "'fix(api: handle nulls', false",
        "'fix!!: handle nulls', false"
    })
    void scopeAndBreakingMarkAreOptionalButMustBeWellFormed(String header, boolean conventional) {
        // when
        var parsed = CommitMessage.parse(header);

        // then
        then(parsed.headerIsConventional()).isEqualTo(conventional);
    }

    @Test
    void unconventionalHeaderKeepsOnlyTheRawHeader() {
        // given
        var commitMessage = "Merge branch 'main'";

        // when
        var parsed = CommitMessage.parse(commitMessage);

        // then
        then(parsed.headerIsConventional()).isFalse();
        then(parsed.header()).isEqualTo("Merge branch 'main'");
        then(parsed.type()).isEmpty();
        then(parsed.description()).isEmpty();
    }
}
