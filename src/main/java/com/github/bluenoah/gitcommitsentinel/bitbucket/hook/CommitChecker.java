package com.github.bluenoah.gitcommitsentinel.bitbucket.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.hook.repository.CommitAddedDetails;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookCommitCallback;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleViolation;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bitbucket hands over the commits a push adds to the repository one at a time, then asks for the result. */
final class CommitChecker implements PreRepositoryHookCommitCallback {

    private static final Logger log = LoggerFactory.getLogger(CommitChecker.class);

    // Past this, only a count: a huge push must not flood the developer's terminal.
    static final int MAX_COMMITS_REPORTED_IN_FULL = 20;

    private static final boolean KEEP_STREAMING = true;
    private static final boolean STOP_STREAMING = false;

    private final RuleSet ruleSet;
    private final RuleConfig ruleConfig;
    private final Set<String> checkedFeatureBranchRefIds;
    private final PusherTerminal pusherTerminal;

    private int commitsReportedInFull;
    private int commitsWithViolationsNotShown;
    private int commitsWithErrors;
    private boolean checkingFailed;

    CommitChecker(
            RuleSet ruleSet,
            RuleConfig ruleConfig,
            Set<String> checkedFeatureBranchRefIds,
            PusherTerminal pusherTerminal) {
        this.ruleSet = ruleSet;
        this.ruleConfig = ruleConfig;
        this.checkedFeatureBranchRefIds = checkedFeatureBranchRefIds;
        this.pusherTerminal = pusherTerminal;
    }

    @Override
    public boolean onCommitAdded(CommitAddedDetails added) {
        try {
            if (isOnACheckedFeatureBranch(added) && !isMergeCommit(added.getCommit())) {
                check(added.getCommit(), added.getRef().getDisplayId());
            }
            return KEEP_STREAMING;
        } catch (RuntimeException e) {
            log.error("git-commit-sentinel-bitbucket failed while checking a pushed commit; accepting the push", e);
            checkingFailed = true;
            return STOP_STREAMING;
        }
    }

    @Override
    public RepositoryHookResult getResult() {
        if (checkingFailed) {
            pusherTerminal.internalErrorWarning();
            return RepositoryHookResult.accepted();
        }
        if (commitsWithViolationsNotShown > 0) {
            pusherTerminal.note(
                    "... and %s more commit(s) with findings, not shown".formatted(commitsWithViolationsNotShown));
        }
        if (commitsWithErrors == 0) {
            return RepositoryHookResult.accepted();
        }
        return RepositoryHookResult.rejected(
                "Commit messages are not Conventional Commits", """
                %s commit(s) on feature branches have errors (see above). \
                Reword them (git commit --amend, or git rebase -i) and push again.""".formatted(commitsWithErrors));
    }

    private boolean isOnACheckedFeatureBranch(CommitAddedDetails added) {
        return checkedFeatureBranchRefIds.contains(added.getRef().getId());
    }

    private static boolean isMergeCommit(Commit commit) {
        return commit.getParents().size() > 1;
    }

    private void check(Commit commit, String branchName) {
        var violations = ruleSet.violationsOf(commit.getMessage(), ruleConfig);
        if (violations.isEmpty()) {
            return;
        }
        if (violations.stream().anyMatch(RuleViolation::isError)) {
            commitsWithErrors++;
        }
        report(commit, branchName, violations);
    }

    private void report(Commit commit, String branchName, List<RuleViolation> violations) {
        if (commitsReportedInFull < MAX_COMMITS_REPORTED_IN_FULL) {
            commitsReportedInFull++;
            pusherTerminal.violations(commit, branchName, violations);
        } else {
            commitsWithViolationsNotShown++;
        }
    }
}
