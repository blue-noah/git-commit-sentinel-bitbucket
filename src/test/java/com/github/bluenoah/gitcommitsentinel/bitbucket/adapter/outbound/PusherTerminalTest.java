package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.outbound;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushedCommit;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PusherTerminalTest {

    private static final List<RuleViolation> TYPE_ERROR =
            List.of(new RuleViolation("type", Level.ERROR, "type \"wip\" is not in the allowed list"));

    private final StringWriter terminalOutput = new StringWriter();
    private final PusherTerminal sut = new PusherTerminal(new PrintWriter(terminalOutput));

    private PushedCommit commit(String branchName, String message) {
        return new PushedCommit("abc1234", message, 1, "refs/heads/%s".formatted(branchName), branchName);
    }

    private RepositoryPushHookRequest pushWithTerminal(StringWriter terminalOutput) {
        var push = mock(RepositoryPushHookRequest.class);
        var scm = mock(ScmHookDetails.class);
        given(scm.out()).willReturn(new PrintWriter(terminalOutput));
        given(push.getScmHookDetails()).willReturn(Optional.of(scm));
        return push;
    }

    @Test
    void eachViolationIsPrintedUnderItsCommitWithItsLevel() {
        // given
        var violations = List.of(
                new RuleViolation("type", Level.ERROR, "bad type"),
                new RuleViolation("description-period", Level.WARN, "ends with a period"));

        // when
        sut.violations(commit("feature/x", "wip: stuff."), violations);

        // then
        then(terminalOutput.toString()).isEqualTo("""
                        git-commit-sentinel-bitbucket: abc1234 on feature/x: "wip: stuff."
                        git-commit-sentinel-bitbucket:   error: [type] bad type
                        git-commit-sentinel-bitbucket:   warning: [description-period] ends with a period
                        """.replace("\n", System.lineSeparator()));
    }

    @Test
    void onlyTheHeaderOfEachCommitIsPrinted() {
        // when
        sut.violations(commit("feature/x", "wip: stuff\n\nsecret body line"), TYPE_ERROR);

        // then
        then(terminalOutput.toString())
                .contains(": \"wip: stuff\"%n".formatted())
                .doesNotContain("secret body line");
    }

    @Test
    void windowsLineEndingsDoNotLeakIntoTheHeader() {
        // when
        sut.violations(commit("feature/x", "wip: stuff\r\n\r\nbody"), TYPE_ERROR);

        // then
        then(terminalOutput.toString()).contains(": \"wip: stuff\"%n".formatted());
    }

    @Test
    void theCommitLineShowsTheSameWholeHeaderTheRulesSee() {
        // when
        sut.violations(commit("feature/x", "wip OK\rhidden part\n\nbody"), TYPE_ERROR);

        // then
        then(terminalOutput.toString()).contains(": \"wip OK\\u{d}hidden part\"%n".formatted());
    }

    @Test
    void controlCharactersInTheHeaderNeverReachTheTerminalRaw() {
        // when
        sut.violations(commit("feature/x", "wip: \u001b[2Jcleared"), TYPE_ERROR);

        // then
        then(terminalOutput.toString()).contains(": \"wip: \\u{1b}[2Jcleared\"").doesNotContain("\u001b");
    }

    @Test
    void bidirectionalCharactersInTheBranchNameAreNeutralized() {
        // when
        sut.violations(commit("feature/\u202Egnp", "wip: stuff"), TYPE_ERROR);

        // then
        then(terminalOutput.toString()).contains(" on feature/\\u{202e}gnp: ").doesNotContain("\u202E");
    }

    @Test
    void theCountOfCommitsNotShownIsPrinted() {
        // when
        sut.commitsWithViolationsNotShown(5);

        // then
        then(terminalOutput.toString()).contains("... and 5 more commit(s) with findings, not shown");
    }

    @Test
    void anExemptPusherIsToldTheChecksWereSkipped() {
        // when
        sut.checksSkippedFor("Alice");

        // then
        then(terminalOutput.toString()).contains("commit message checks skipped for Alice (bypass)");
    }

    @Test
    void anInternalErrorIsAWarningThatThePushIsAccepted() {
        // when
        sut.internalErrorWarning();

        // then
        then(terminalOutput.toString()).contains("warning: ", "internal error", "the push is accepted");
    }

    @Test
    void everyLineIsFlushedAsSoonAsItIsPrinted() {
        // given
        var sink = new StringWriter();
        var sut = new PusherTerminal(new PrintWriter(new BufferedWriter(sink)));

        // when
        sut.violations(commit("feature/x", "wip: stuff"), TYPE_ERROR);
        sut.commitsWithViolationsNotShown(1);

        // then
        then(sink.toString()).contains("error: [type]", "... and 1 more");
    }

    @Test
    void ofAPushWritesToItsTerminal() {
        // given
        var sut = PusherTerminal.of(pushWithTerminal(terminalOutput));

        // when
        sut.checksSkippedFor("Alice");

        // then
        then(terminalOutput.toString()).contains("Alice");
    }

    @Test
    void ofAPushWithoutATerminalWritesNowhere() {
        // given
        var push = mock(RepositoryPushHookRequest.class);
        given(push.getScmHookDetails()).willReturn(Optional.empty());
        var sut = PusherTerminal.of(push);

        // when / then
        thenNoException().isThrownBy(sut::internalErrorWarning);
    }
}
