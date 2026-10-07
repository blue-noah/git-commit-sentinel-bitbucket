package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound;

import com.atlassian.bitbucket.hook.repository.CommitAddedDetails;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookCommitCallback;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.outbound.PusherTerminal;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushedCommit;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushedCommitsCheck;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lives for a single push: Bitbucket streams it the commits the push adds, one at a time, then asks for the result. It
 * hands each commit to {@link PushedCommitsCheck} and turns its verdict into accepting or rejecting the push; an
 * internal failure accepts it.
 */
final class PushedCommitsListener implements PreRepositoryHookCommitCallback {

    private static final Logger log = LoggerFactory.getLogger(PushedCommitsListener.class);

    private static final boolean KEEP_STREAMING = true;
    private static final boolean STOP_STREAMING = false;

    private final PushedCommitsCheck pushedCommitsCheck;
    private final PusherTerminal pusherTerminal;

    private boolean checkingFailed;

    PushedCommitsListener(PushedCommitsCheck pushedCommitsCheck, PusherTerminal pusherTerminal) {
        this.pushedCommitsCheck = pushedCommitsCheck;
        this.pusherTerminal = pusherTerminal;
    }

    @Override
    public boolean onCommitAdded(@Nonnull CommitAddedDetails added) {
        try {
            pushedCommitsCheck.check(pushedCommit(added));
            return KEEP_STREAMING;
        } catch (RuntimeException e) {
            log.error("git-commit-sentinel-bitbucket failed while checking a pushed commit; accepting the push", e);
            checkingFailed = true;
            return STOP_STREAMING;
        }
    }

    @Nonnull
    @Override
    public RepositoryHookResult getResult() {
        if (checkingFailed) {
            pusherTerminal.internalErrorWarning();
            return RepositoryHookResult.accepted();
        }
        var verdict = pushedCommitsCheck.finish();
        if (!verdict.rejectsThePush()) {
            return RepositoryHookResult.accepted();
        }
        return RepositoryHookResult.rejected(
                "Commit messages are not Conventional Commits", """
                %s commit(s) on feature branches have errors (see above). \
                Reword them (git commit --amend, or git rebase -i) and push again.""".formatted(verdict.commitsWithErrors()));
    }

    private PushedCommit pushedCommit(CommitAddedDetails added) {
        var commit = added.getCommit();
        var ref = added.getRef();
        return new PushedCommit(
                commit.getDisplayId(),
                commit.getMessage(),
                commit.getParents().size(),
                ref.getId(),
                ref.getDisplayId());
    }
}
