package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class CappedReportTest {

    private static final List<RuleViolation> TYPE_ERROR =
            List.of(new RuleViolation("type", Level.ERROR, "type \"wip\" is not in the allowed list"));

    private final PushReport report = mock(PushReport.class);
    private final CappedReport sut = new CappedReport(report);

    private void reportCommits(int count) {
        IntStream.range(0, count)
                .forEach(
                        i -> sut.violations(new PushedCommit("abc1234", "wip: x", 1, "refs/heads/f", "f"), TYPE_ERROR));
    }

    @Test
    void upToTheCapEveryCommitIsShownAndNothingIsLeftToSay() {
        // given
        reportCommits(CappedReport.MAX_COMMITS_SHOWN);

        // when
        sut.close();

        // then
        BDDMockito.then(report).should(times(CappedReport.MAX_COMMITS_SHOWN)).violations(any(), any());
        BDDMockito.then(report).should(never()).commitsWithViolationsNotShown(anyInt());
    }

    @Test
    void pastTheCapCommitsAreOnlyCounted() {
        // given
        reportCommits(CappedReport.MAX_COMMITS_SHOWN + 5);

        // when
        sut.close();

        // then
        BDDMockito.then(report).should(times(CappedReport.MAX_COMMITS_SHOWN)).violations(any(), any());
        BDDMockito.then(report).should().commitsWithViolationsNotShown(5);
    }
}
