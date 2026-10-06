package com.github.bluenoah.commitsentinel.hook;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;

import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookCommitCallback;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookCommitFilter;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.hook.repository.StandardRepositoryHookTrigger;
import com.atlassian.bitbucket.repository.MinimalRef;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.StandardRefType;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.github.bluenoah.commitsentinel.config.SentinelConfig;
import com.github.bluenoah.commitsentinel.config.SettingsKeys;
import com.github.bluenoah.commitsentinel.rules.RuleSet;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;

class PushValidatorTest {

    private final BypassPolicy bypassPolicy = mock(BypassPolicy.class);
    private final PreRepositoryHookContext context = mock(PreRepositoryHookContext.class);
    private final ApplicationUser alice = mock(ApplicationUser.class);
    private final StringWriter terminalOutput = new StringWriter();

    private final PushValidator sut = new PushValidator(bypassPolicy, RuleSet.standard());

    @BeforeEach
    void nobodyIsExemptByDefault() {
        given(alice.getName()).willReturn("Alice");
        given(bypassPolicy.pusherIfExempt(any())).willReturn(Optional.empty());
    }

    private RepositoryPushHookRequest push(RefChange... changes) {
        var push = mock(RepositoryPushHookRequest.class);
        given(push.getTrigger()).willReturn(StandardRepositoryHookTrigger.REPO_PUSH);
        given(push.getRefChanges()).willReturn(List.of(changes));
        var scm = mock(ScmHookDetails.class);
        given(scm.out()).willReturn(new PrintWriter(terminalOutput));
        given(push.getScmHookDetails()).willReturn(Optional.of(scm));
        return push;
    }

    private static RefChange tag(String name) {
        return refChange("refs/tags/%s".formatted(name), name, StandardRefType.TAG, RefChangeType.ADD);
    }

    private static RefChange branch(String name, RefChangeType type) {
        return refChange("refs/heads/%s".formatted(name), name, StandardRefType.BRANCH, type);
    }

    private static RefChange refChange(String id, String displayId, StandardRefType refType, RefChangeType type) {
        var ref = mock(MinimalRef.class);
        given(ref.getId()).willReturn(id);
        given(ref.getDisplayId()).willReturn(displayId);
        given(ref.getType()).willReturn(refType);
        var change = mock(RefChange.class);
        given(change.getRef()).willReturn(ref);
        given(change.getType()).willReturn(type);
        return change;
    }

    private static SentinelConfig config(Map<String, String> hookSettings) {
        return SentinelConfig.fromHookSettings(RuleSet.standard(), hookSettings, (key, problem) -> {});
    }

    private PreRepositoryHookCommitCallback registeredChecker() {
        var callback = ArgumentCaptor.forClass(PreRepositoryHookCommitCallback.class);
        BDDMockito.then(context)
                .should()
                .registerCommitCallback(callback.capture(), eq(RepositoryHookCommitFilter.ADDED_TO_REPOSITORY));
        return callback.getValue();
    }

    private void thenTheNewCommitsAreChecked() {
        BDDMockito.then(context)
                .should()
                .registerCommitCallback(any(), eq(RepositoryHookCommitFilter.ADDED_TO_REPOSITORY));
    }

    private void thenNoCommitIsChecked() {
        BDDMockito.then(context).should(never()).registerCommitCallback(any(), any());
    }

    @Test
    void featureBranchPushChecksOnlyTheCommitsNewToTheRepository() {
        // given
        var push = push(branch("main", RefChangeType.UPDATE), branch("feature/x", RefChangeType.UPDATE));

        // when
        var result = sut.check(context, push, () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenTheNewCommitsAreChecked();
    }

    @Test
    void theRegisteredCheckerIgnoresCommitsOfOtherBranchesInThePush() {
        // given
        sut.check(
                context,
                push(branch("main", RefChangeType.UPDATE), branch("feature/x", RefChangeType.UPDATE)),
                () -> config(Map.of()));
        var checkerItRegistered = registeredChecker();

        // when
        checkerItRegistered.onCommitAdded(CommitCheckerTest.commit("refs/heads/main", "wip: x", 1));
        var result = checkerItRegistered.getResult();

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void theRegisteredCheckerRejectsErrorsOnTheFeatureBranch() {
        // given
        sut.check(
                context,
                push(branch("main", RefChangeType.UPDATE), branch("feature/x", RefChangeType.UPDATE)),
                () -> config(Map.of()));
        var checkerItRegistered = registeredChecker();

        // when
        checkerItRegistered.onCommitAdded(CommitCheckerTest.commit("refs/heads/feature/x", "wip: x", 1));
        var result = checkerItRegistered.getResult();

        // then
        then(result).matches(RepositoryHookResult::isRejected, "rejected");
    }

    @Test
    void pushWithoutFeatureBranchesCostsNothing() {
        // when
        var result = sut.check(context, push(branch("main", RefChangeType.UPDATE)), () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenNoCommitIsChecked();
    }

    @Test
    void deletingAFeatureBranchIsNotChecked() {
        // when
        var result = sut.check(context, push(branch("feature/x", RefChangeType.DELETE)), () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenNoCommitIsChecked();
    }

    @Test
    void customBranchPatternIsHonoured() {
        // given
        var storiesToo = config(Map.of(SettingsKeys.BRANCH_PATTERN, "(feature|story)/.+"));

        // when
        var result = sut.check(context, push(branch("story/ABC-1", RefChangeType.ADD)), () -> storiesToo);

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenTheNewCommitsAreChecked();
    }

    @Test
    void exemptPusherIsNotChecked() {
        // given
        given(bypassPolicy.pusherIfExempt(any())).willReturn(Optional.of(alice));

        // when
        var result = sut.check(context, push(branch("feature/x", RefChangeType.UPDATE)), () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenNoCommitIsChecked();
        then(terminalOutput.toString()).contains("checks skipped for Alice (bypass)");
    }

    @Test
    void tagsAreNeverCheckedEvenIfNamedLikeAFeatureBranch() {
        // when
        var result = sut.check(context, push(tag("feature/v1")), () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenNoCommitIsChecked();
    }

    @Test
    void otherTriggersAreIgnored() {
        // given
        var fileEdit = push(branch("feature/x", RefChangeType.UPDATE));
        given(fileEdit.getTrigger()).willReturn(StandardRepositoryHookTrigger.FILE_EDIT);

        // when
        var result = sut.check(context, fileEdit, () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenNoCommitIsChecked();
    }

    @Test
    void pushWithoutATerminalIsStillChecked() {
        // given
        var noTerminal = push(branch("feature/x", RefChangeType.UPDATE));
        given(noTerminal.getScmHookDetails()).willReturn(Optional.empty());

        // when
        var result = sut.check(context, noTerminal, () -> config(Map.of()));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenTheNewCommitsAreChecked();
    }

    @Test
    void failureWhileReadingTheConfigurationAcceptsThePush() {
        // given
        var push = push(branch("feature/x", RefChangeType.UPDATE));

        // when
        var result = sut.check(context, push, () -> {
            throw new IllegalStateException("settings store down");
        });

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).contains("internal error");
    }
}
