package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.atlassian.bitbucket.hook.repository.CommitAddedDetails;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.repository.MinimalRef;
import com.atlassian.bitbucket.repository.Repository;
import com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.outbound.PusherTerminal;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.CheckPush;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushPolicy;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushedRef;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PushedCommitsListenerTest {

    private static final String FEATURE = "refs/heads/feature/x";

    private final StringWriter terminalOutput = new StringWriter();
    private final PushedCommitsListener sut =
            pushedCommitsListener(PusherTerminal.of(pushWithTerminal(terminalOutput)));

    private PushedCommitsListener pushedCommitsListener(PusherTerminal pusherTerminal) {
        var policy = new PushPolicy(
                PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN,
                new RuleConfig(RuleConfig.DEFAULT_ALLOWED_TYPES, RuleConfig.DEFAULT_HEADER_MAX_LENGTH, Map.of()),
                Set.of());
        var pushedCommitsCheck = new CheckPush(RuleSet.standard())
                .start(
                        List.of(new PushedRef(FEATURE, "feature/x", true, false)),
                        Optional.empty(),
                        policy,
                        pusherTerminal)
                .orElseThrow();
        return new PushedCommitsListener(pushedCommitsCheck, pusherTerminal);
    }

    static RepositoryPushHookRequest pushWithTerminal(StringWriter terminalOutput) {
        var push = mock(RepositoryPushHookRequest.class);
        var scm = mock(ScmHookDetails.class);
        given(scm.out()).willReturn(new PrintWriter(terminalOutput));
        given(push.getScmHookDetails()).willReturn(Optional.of(scm));
        var project = mock(Project.class);
        given(project.getKey()).willReturn("PRJ");
        var repository = mock(Repository.class);
        given(repository.getProject()).willReturn(project);
        given(repository.getSlug()).willReturn("repo");
        given(push.getRepository()).willReturn(repository);
        return push;
    }

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

    @Test
    void validCommitsAreAccepted() {
        // when
        var keepStreaming = sut.onCommitAdded(commit(FEATURE, "feat: add login", 1));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void anErrorRejectsThePushAndStreamingGoesOnToReportEveryCommit() {
        // when
        var keepStreaming = sut.onCommitAdded(commit(FEATURE, "wip: stuff", 1));
        var result = sut.getResult();

        // then
        then(keepStreaming).isTrue();
        then(result).matches(RepositoryHookResult::isRejected, "rejected");
        then(result.getVetoes().get(0).getSummaryMessage()).isEqualTo("Commit messages are not Conventional Commits");
        then(terminalOutput.toString()).contains("abc1234 on feature/x: \"wip: stuff\"", "error: [type]");
    }

    @Test
    void theRejectionCountsEveryCommitWithErrorsEvenTheOnesNotShown() {
        // given
        IntStream.range(0, 25).forEach(i -> sut.onCommitAdded(commit(FEATURE, "wip: stuff", 1)));

        // when
        var result = sut.getResult();

        // then
        then(result.getVetoes().get(0).getDetailedMessage()).isEqualTo("""
                        25 commit(s) on feature branches have errors (see above). \
                        Reword them (git commit --amend, or git rebase -i) and push again.""");
    }

    @Test
    void theParentsOfACommitTellAMergeApart() {
        // when
        sut.onCommitAdded(commit(FEATURE, "wip: merge", 2));
        var result = sut.getResult();

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void theRefOfACommitTellsItsBranch() {
        // when
        sut.onCommitAdded(commit("refs/heads/main", "wip: stuff", 1));
        var result = sut.getResult();

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void anUnexpectedFailureStopsStreamingAndAcceptsThePush() {
        // given
        sut.onCommitAdded(commit(FEATURE, "wip: stuff", 1));
        var broken = mock(CommitAddedDetails.class);
        given(broken.getCommit()).willThrow(new IllegalStateException("boom"));

        // when
        var keepStreaming = sut.onCommitAdded(broken);
        var result = sut.getResult();

        // then
        then(keepStreaming).isFalse();
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).contains("internal error");
    }
}
