package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PushPolicyTest {

    private static final RuleConfig DEFAULTS =
            new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, RuleConfig.DEFAULT_HEADER_MAX_LENGTH, Map.of());

    private PushPolicy policy(Pattern featureBranchPattern, Set<String> bypassUsernames) {
        return new PushPolicy(featureBranchPattern, DEFAULTS, bypassUsernames);
    }

    @ParameterizedTest(name = "{0} -> feature branch: {1}")
    @CsvSource({"feature/ABC-1, true", "main, false", "my-feature/x, false"})
    void theDefaultPatternMatchesTheWholeBranchName(String branchName, boolean featureBranch) {
        // given
        var sut = policy(PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN, Set.of());

        // when
        var isFeatureBranch = sut.isFeatureBranch(branchName);

        // then
        then(isFeatureBranch).isEqualTo(featureBranch);
    }

    @ParameterizedTest(name = "{0} -> feature branch: {1}")
    @CsvSource({
        "feature/x, true",
        "tmp/x, true",
        "mainline, true",
        "main, false",
        "develop, false",
        "release/1.0, false"
    })
    void everyBranchExceptTheProtectedOnesPatternFromAdr0001(String branchName, boolean featureBranch) {
        // given
        var sut = policy(Pattern.compile("(?!main$|develop$|release/).+"), Set.of());

        // when
        var isFeatureBranch = sut.isFeatureBranch(branchName);

        // then
        then(isFeatureBranch).isEqualTo(featureBranch);
    }

    @ParameterizedTest(name = "{0} exempt: {1}")
    @CsvSource({"CI-Bot, true", "ci-bot, true", "alice, false"})
    void listedUsernamesAreExemptIgnoringCase(String username, boolean exempt) {
        // given
        var sut = policy(PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN, Set.of("CI-Bot"));

        // when
        var exempts = sut.exempts(username);

        // then
        then(exempts).isEqualTo(exempt);
    }
}
