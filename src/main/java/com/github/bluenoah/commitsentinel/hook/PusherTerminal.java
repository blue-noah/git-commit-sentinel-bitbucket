package com.github.bluenoah.commitsentinel.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.github.bluenoah.commitsentinel.rules.CommitMessage;
import com.github.bluenoah.commitsentinel.rules.Level;
import com.github.bluenoah.commitsentinel.rules.RuleViolation;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

/** What the pushing developer reads; git prints each line with a {@code remote:} prefix. */
final class PusherTerminal {

    private static final String LINE_PREFIX = "git-commit-sentinel: ";

    private final PrintWriter writer;

    PusherTerminal(PrintWriter writer) {
        this.writer = writer;
    }

    /** Not every push has a terminal behind it: then lines go nowhere, and nobody has to check. */
    static PusherTerminal of(RepositoryPushHookRequest push) {
        return new PusherTerminal(push.getScmHookDetails()
                .map(ScmHookDetails::out)
                .orElseGet(() -> new PrintWriter(Writer.nullWriter())));
    }

    void violations(Commit commit, String branchName, List<RuleViolation> violations) {
        line("%s on %s: \"%s\""
                .formatted(
                        commit.getDisplayId(),
                        branchName,
                        CommitMessage.parse(commit.getMessage()).header()));
        violations.forEach(violation ->
                line("  %s: [%s] %s".formatted(label(violation.level()), violation.ruleName(), violation.message())));
    }

    void note(String note) {
        line(note);
    }

    void internalErrorWarning() {
        line("""
                warning: commit messages were not checked because of an internal error; \
                the push is accepted. Please tell your Bitbucket administrator.""");
    }

    // The single way out to the terminal: whatever a line carries (commit message, branch name, username), it is
    // neutralized here.
    private void line(String line) {
        writer.println(ControlCharacters.neutralized("%s%s".formatted(LINE_PREFIX, line)));
        writer.flush();
    }

    private static String label(Level level) {
        return switch (level) {
            case ERROR -> "error";
            case WARN, OFF -> "warning"; // a rule set to OFF never reports, but the switch must cover every level
        };
    }
}
