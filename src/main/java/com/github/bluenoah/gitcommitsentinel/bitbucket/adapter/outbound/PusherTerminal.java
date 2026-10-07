package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.outbound;

import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushReport;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushedCommit;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

public final class PusherTerminal implements PushReport {

    private static final String LINE_PREFIX = "git-commit-sentinel-bitbucket: ";

    private final PrintWriter writer;
    private final ControlCharacters controlCharacters = new ControlCharacters();

    PusherTerminal(PrintWriter writer) {
        this.writer = writer;
    }

    public static PusherTerminal of(RepositoryPushHookRequest push) {
        return new PusherTerminal(push.getScmHookDetails()
                .map(ScmHookDetails::out)
                .orElseGet(() -> new PrintWriter(Writer.nullWriter())));
    }

    @Override
    public void violations(PushedCommit commit, List<RuleViolation> violations) {
        line("%s on %s: \"%s\"".formatted(commit.displayId(), commit.branchName(), commit.header()));
        violations.forEach(violation -> line("  %s: [%s] %s"
                .formatted(violation.isError() ? "error" : "warning", violation.ruleName(), violation.message())));
    }

    @Override
    public void commitsWithViolationsNotShown(int count) {
        line("... and %s more commit(s) with findings, not shown".formatted(count));
    }

    @Override
    public void checksSkippedFor(String exemptPusher) {
        line("commit message checks skipped for %s (bypass)".formatted(exemptPusher));
    }

    public void internalErrorWarning() {
        line("""
                warning: commit messages were not checked because of an internal error; \
                the push is accepted. Please tell your Bitbucket administrator.""");
    }

    private void line(String line) {
        writer.println(controlCharacters.neutralized("%s%s".formatted(LINE_PREFIX, line)));
        writer.flush();
    }
}
