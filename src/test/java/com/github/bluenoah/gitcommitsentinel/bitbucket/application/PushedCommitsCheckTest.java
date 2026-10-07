package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static com.github.bluenoah.gitcommitsentinel.bitbucket.application.CheckPushTest.commit;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class PushedCommitsCheckTest {

    private static final RuleConfig DEFAULTS =
            new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, RuleConfig.DEFAULT_HEADER_MAX_LENGTH, Map.of());

    private final PushReport report = mock(PushReport.class);
    private final PushedCommitsCheck sut =
            new PushedCommitsCheck(RuleSet.standard(), DEFAULTS, Set.of("refs/heads/feature/x"), report);

    private void checkTheSameCommit(int times, String message) {
        IntStream.range(0, times).forEach(i -> sut.check(commit("feature/x", message)));
    }

    private boolean isOneViolationOf(List<RuleViolation> violations, String ruleName, Level level) {
        return violations.size() == 1
                && violations.get(0).ruleName().equals(ruleName)
                && violations.get(0).level() == level;
    }

    @Test
    void validCommitsPassSilently() {
        // when
        sut.check(commit("feature/x", "feat: add login"));
        var verdict = sut.finish();

        // then
        then(verdict.rejectsThePush()).isFalse();
        BDDMockito.then(report).shouldHaveNoInteractions();
    }

    @Test
    void anErrorIsReportedAndRejectsThePush() {
        // given
        var wip = commit("feature/x", "wip: stuff");

        // when
        sut.check(wip);
        var verdict = sut.finish();

        // then
        then(verdict.rejectsThePush()).isTrue();
        then(verdict.commitsWithErrors()).isEqualTo(1);
        BDDMockito.then(report)
                .should()
                .violations(eq(wip), argThat(found -> isOneViolationOf(found, "type", Level.ERROR)));
    }

    @Test
    void warningsAreReportedButDoNotReject() {
        // given
        var withPeriod = commit("feature/x", "fix: the bug.");

        // when
        sut.check(withPeriod);
        var verdict = sut.finish();

        // then
        then(verdict.rejectsThePush()).isFalse();
        BDDMockito.then(report)
                .should()
                .violations(
                        eq(withPeriod), argThat(found -> isOneViolationOf(found, "description-period", Level.WARN)));
    }

    @Test
    void mergeCommitsAreAlwaysSkipped() {
        // when
        sut.check(new PushedCommit("abc1234", "Merge branch 'main'", 2, "refs/heads/feature/x", "feature/x"));
        var verdict = sut.finish();

        // then
        then(verdict.rejectsThePush()).isFalse();
        BDDMockito.then(report).shouldHaveNoInteractions();
    }

    @Test
    void commitsOnOtherBranchesAreIgnored() {
        // when
        sut.check(commit("main", "wip: stuff"));
        var verdict = sut.finish();

        // then
        then(verdict.rejectsThePush()).isFalse();
        BDDMockito.then(report).shouldHaveNoInteractions();
    }

    @Test
    void theReportIsCappedButEveryErrorStillCounts() {
        // given
        var commitsWithErrors = PushedCommitsCheck.MAX_COMMITS_REPORTED_IN_FULL + 5;
        checkTheSameCommit(commitsWithErrors, "wip: stuff");

        // when
        var verdict = sut.finish();

        // then
        then(verdict.commitsWithErrors()).isEqualTo(commitsWithErrors);
        BDDMockito.then(report)
                .should(times(PushedCommitsCheck.MAX_COMMITS_REPORTED_IN_FULL))
                .violations(any(), any());
        BDDMockito.then(report).should().commitsWithViolationsNotShown(5);
    }

    @Test
    void upToTheCapEveryCommitIsReportedInFull() {
        // given
        checkTheSameCommit(PushedCommitsCheck.MAX_COMMITS_REPORTED_IN_FULL, "wip: stuff");

        // when
        sut.finish();

        // then
        BDDMockito.then(report).should(never()).commitsWithViolationsNotShown(anyInt());
    }
}
