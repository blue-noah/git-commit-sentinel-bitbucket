package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class CheckPushTest {

    private static final RuleConfig DEFAULTS =
            new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, RuleConfig.DEFAULT_HEADER_MAX_LENGTH, Map.of());
    private static final SentinelConfig CONFIG =
            new SentinelConfig(SentinelConfig.DEFAULT_FEATURE_BRANCH_PATTERN, DEFAULTS, Set.of("CI-Bot"));
    private static final Optional<String> ALICE = Optional.of("alice");

    private final PushReport report = mock(PushReport.class);
    private final CheckPush sut = new CheckPush(RuleSet.standard());

    private PushedRef branch(String name) {
        return new PushedRef("refs/heads/%s".formatted(name), name, true, false);
    }

    private PushedRef deletedBranch(String name) {
        return new PushedRef("refs/heads/%s".formatted(name), name, true, true);
    }

    private PushedRef tag(String name) {
        return new PushedRef("refs/tags/%s".formatted(name), name, false, false);
    }

    static PushedCommit commit(String branchName, String message) {
        return new PushedCommit("abc1234", message, 1, "refs/heads/%s".formatted(branchName), branchName);
    }

    @Test
    void aPushToAFeatureBranchStartsACheckOfItsNewCommits() {
        // when
        var pushedCommitsCheck = sut.start(List.of(branch("main"), branch("feature/x")), ALICE, CONFIG, report);

        // then
        then(pushedCommitsCheck).isPresent();
    }

    @Test
    void theCheckItStartsLooksOnlyAtTheFeatureBranchesOfThePush() {
        // given
        var pushedCommitsCheck = sut.start(List.of(branch("main"), branch("feature/x")), ALICE, CONFIG, report)
                .orElseThrow();

        // when
        pushedCommitsCheck.check(commit("main", "wip: on main"));
        pushedCommitsCheck.check(commit("feature/x", "wip: on the feature branch"));
        var verdict = pushedCommitsCheck.finish();

        // then
        then(verdict.commitsWithErrors()).isEqualTo(1);
    }

    @Test
    void aPushWithoutFeatureBranchesHasNothingToCheck() {
        // when
        var pushedCommitsCheck = sut.start(List.of(branch("main")), ALICE, CONFIG, report);

        // then
        then(pushedCommitsCheck).isEmpty();
    }

    @Test
    void deletingAFeatureBranchHasNothingToCheck() {
        // when
        var pushedCommitsCheck = sut.start(List.of(deletedBranch("feature/x")), ALICE, CONFIG, report);

        // then
        then(pushedCommitsCheck).isEmpty();
    }

    @Test
    void tagsAreNeverCheckedEvenIfNamedLikeAFeatureBranch() {
        // when
        var pushedCommitsCheck = sut.start(List.of(tag("feature/v1")), ALICE, CONFIG, report);

        // then
        then(pushedCommitsCheck).isEmpty();
    }

    @Test
    void theConfiguredBranchPatternDecidesWhatAFeatureBranchIs() {
        // given
        var storiesToo = new SentinelConfig(Pattern.compile("(feature|story)/.+"), DEFAULTS, Set.of());

        // when
        var pushedCommitsCheck = sut.start(List.of(branch("story/ABC-1")), ALICE, storiesToo, report);

        // then
        then(pushedCommitsCheck).isPresent();
    }

    @Test
    void anExemptPusherIsNotCheckedAndIsToldSo() {
        // when
        var pushedCommitsCheck = sut.start(List.of(branch("feature/x")), Optional.of("ci-bot"), CONFIG, report);

        // then
        then(pushedCommitsCheck).isEmpty();
        BDDMockito.then(report).should().checksSkippedFor("ci-bot");
    }

    @Test
    void aPusherNotListedIsChecked() {
        // when
        var pushedCommitsCheck = sut.start(List.of(branch("feature/x")), ALICE, CONFIG, report);

        // then
        then(pushedCommitsCheck).isPresent();
        BDDMockito.then(report).should(never()).checksSkippedFor(anyString());
    }

    @Test
    void anAnonymousPushIsNeverExempt() {
        // when
        var pushedCommitsCheck = sut.start(List.of(branch("feature/x")), Optional.empty(), CONFIG, report);

        // then
        then(pushedCommitsCheck).isPresent();
    }
}
