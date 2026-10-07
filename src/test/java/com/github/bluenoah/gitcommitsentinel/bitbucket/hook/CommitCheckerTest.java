package com.github.bluenoah.gitcommitsentinel.bitbucket.hook;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.hook.repository.CommitAddedDetails;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.repository.MinimalRef;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CommitCheckerTest {

    private static final RuleConfig DEFAULTS =
            new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, RuleConfig.DEFAULT_HEADER_MAX_LENGTH, Map.of());

    private static final String FEATURE = "refs/heads/feature/x";

    private final StringWriter terminalOutput = new StringWriter();
    private final CommitChecker sut = new CommitChecker(
            RuleSet.standard(), DEFAULTS, Set.of(FEATURE), new PusherTerminal(new PrintWriter(terminalOutput)));

    static CommitAddedDetails commit(String refId, String message, int parents) {
        var commit = mock(Commit.class);
        given(commit.getMessage()).willReturn(message);
        given(commit.getDisplayId()).willReturn("abc1234");
        given(commit.getParents()).willReturn(Collections.nCopies(parents, mock(MinimalCommit.class)));

        var ref = mock(MinimalRef.class);
        given(ref.getId()).willReturn(refId);
        given(ref.getDisplayId()).willReturn(refId.replace("refs/heads/", ""));

        var added = mock(CommitAddedDetails.class);
        given(added.getCommit()).willReturn(commit);
        given(added.getRef()).willReturn(ref);
        return added;
    }

    static RepositoryPushHookRequest pushWithoutTerminal() {
        var push = mock(RepositoryPushHookRequest.class);
        given(push.getScmHookDetails()).willReturn(Optional.empty());
        return push;
    }

    private static void addTheSameCommit(CommitChecker commitChecker, int times, String message) {
        IntStream.range(0, times).forEach(i -> commitChecker.onCommitAdded(commit(FEATURE, message, 1)));
    }

    @Test
    void validCommitsAreAcceptedSilently() {
        // when
        var keepStreaming = sut.onCommitAdded(commit(FEATURE, "feat: add login", 1));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).isEmpty();
    }

    @Test
    void anErrorRejectsThePushAndStreamingGoesOnToReportEveryCommit() {
        // when
        var keepStreaming = sut.onCommitAdded(commit(FEATURE, "wip: stuff", 1));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isRejected, "rejected");
        then(terminalOutput.toString()).contains("abc1234 on feature/x: \"wip: stuff\"", "error: [type]");
    }

    @Test
    void warningsArePrintedButDoNotReject() {
        // when
        var keepStreaming = sut.onCommitAdded(commit(FEATURE, "fix: the bug.", 1));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).contains("warning: [description-period]");
    }

    @Test
    void mergeCommitsAreAlwaysSkipped() {
        // when
        var keepStreaming = sut.onCommitAdded(commit(FEATURE, "Merge branch 'main' into feature/x", 2));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).isEmpty();
    }

    @Test
    void commitsOnOtherBranchesAreIgnored() {
        // when
        var keepStreaming = sut.onCommitAdded(commit("refs/heads/main", "wip: stuff", 1));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void outputIsCappedButEveryErrorStillCounts() {
        // given
        var commitsWithErrors = CommitChecker.MAX_COMMITS_REPORTED_IN_FULL + 5;
        addTheSameCommit(sut, commitsWithErrors, "wip: stuff");

        // when
        var result = sut.getResult();

        // then
        then(result).matches(RepositoryHookResult::isRejected, "rejected");
        then(result.getVetoes().get(0).getDetailedMessage()).startsWith("%s commit(s)".formatted(commitsWithErrors));
        then(terminalOutput.toString()).contains("... and 5 more");
    }

    @Test
    void anUnexpectedFailureStopsStreamingAndAcceptsThePush() {
        // given
        sut.onCommitAdded(commit(FEATURE, "wip: stuff", 1));
        var broken = mock(CommitAddedDetails.class);
        given(broken.getRef()).willThrow(new IllegalStateException("boom"));

        // when
        var keepStreaming = sut.onCommitAdded(broken);
        var result = sut.getResult();

        // then
        then(keepStreaming).isFalse();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).contains("internal error");
    }

    @Test
    void withoutATerminalErrorsStillReject() {
        // given
        var sut = new CommitChecker(
                RuleSet.standard(), DEFAULTS, Set.of(FEATURE), PusherTerminal.of(pushWithoutTerminal()));
        addTheSameCommit(sut, CommitChecker.MAX_COMMITS_REPORTED_IN_FULL + 1, "wip: stuff");

        // when
        var result = sut.getResult();

        // then
        then(result).matches(RepositoryHookResult::isRejected, "rejected");
    }

    @Test
    void withoutATerminalAFailureStillAcceptsThePush() {
        // given: getRef() is null on this mock, so checking it fails
        var sut = new CommitChecker(
                RuleSet.standard(), DEFAULTS, Set.of(FEATURE), PusherTerminal.of(pushWithoutTerminal()));
        sut.onCommitAdded(mock(CommitAddedDetails.class));

        // when
        var result = sut.getResult();

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void windowsLineEndingsDoNotLeakIntoTheHeader() {
        // when
        sut.onCommitAdded(commit(FEATURE, "wip: stuff\r\n\r\nbody", 1));

        // then
        then(terminalOutput.toString()).contains(": \"wip: stuff\"%n".formatted());
    }

    @Test
    void controlCharactersInTheHeaderNeverReachTheTerminalRaw() {
        // when
        sut.onCommitAdded(commit(FEATURE, "wip: \u001b[2Jcleared", 1));

        // then
        then(terminalOutput.toString()).contains(": \"wip: \\u{1b}[2Jcleared\"").doesNotContain("\u001b");
    }

    @Test
    void bidirectionalCharactersInTheBranchNameAreNeutralized() {
        // given
        var reversedBranch = "refs/heads/feature/\u202Egnp";
        var sut = new CommitChecker(
                RuleSet.standard(),
                DEFAULTS,
                Set.of(reversedBranch),
                new PusherTerminal(new PrintWriter(terminalOutput)));

        // when
        sut.onCommitAdded(commit(reversedBranch, "wip: stuff", 1));

        // then
        then(terminalOutput.toString()).contains(" on feature/\\u{202e}gnp: ").doesNotContain("\u202E");
    }

    @Test
    void theCommitLineShowsTheSameWholeHeaderTheRulesSee() {
        // when
        sut.onCommitAdded(commit(FEATURE, "wip OK\rhidden part\n\nbody", 1));

        // then
        then(terminalOutput.toString()).contains(": \"wip OK\\u{d}hidden part\"%n".formatted());
    }

    @Test
    void onlyTheHeaderOfEachCommitIsPrinted() {
        // when
        sut.onCommitAdded(commit(FEATURE, "wip: stuff\n\nsecret body line", 1));

        // then
        then(terminalOutput.toString())
                .contains(": \"wip: stuff\"%n".formatted())
                .doesNotContain("secret body line");
    }

    @Test
    void eachReportedCommitIsFlushedAsSoonAsItIsPrinted() {
        // given: Bitbucket streams this writer to the developer, lines must not wait in a buffer
        var sink = new StringWriter();
        var sut = new CommitChecker(
                RuleSet.standard(),
                DEFAULTS,
                Set.of(FEATURE),
                new PusherTerminal(new PrintWriter(new BufferedWriter(sink))));

        // when
        sut.onCommitAdded(commit(FEATURE, "wip: stuff", 1));

        // then
        then(sink.toString()).contains("error: [type]");
    }

    @Test
    void theCountOfCommitsNotShownIsFlushedToo() {
        // given
        var sink = new StringWriter();
        var sut = new CommitChecker(
                RuleSet.standard(),
                DEFAULTS,
                Set.of(FEATURE),
                new PusherTerminal(new PrintWriter(new BufferedWriter(sink))));
        addTheSameCommit(sut, CommitChecker.MAX_COMMITS_REPORTED_IN_FULL + 1, "wip: stuff");

        // when
        sut.getResult();

        // then
        then(sink.toString()).contains("... and 1 more");
    }
}
