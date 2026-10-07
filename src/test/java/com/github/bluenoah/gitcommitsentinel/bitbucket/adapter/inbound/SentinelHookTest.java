package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound;

import static com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound.PushedCommitsListenerTest.commit;
import static com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound.PushedCommitsListenerTest.pushWithTerminal;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;

import com.atlassian.bitbucket.auth.AuthenticationContext;
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
import com.atlassian.bitbucket.scope.Scope;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.CheckPush;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;

class SentinelHookTest {

    private final AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
    private final PreRepositoryHookContext context = mock(PreRepositoryHookContext.class);
    private final SettingsValidationErrors formErrors = mock(SettingsValidationErrors.class);
    private final StringWriter terminalOutput = new StringWriter();

    private final SentinelHook sut =
            new SentinelHook(new CheckPush(RuleSet.standard()), RuleSet.standard(), authenticationContext);

    @BeforeEach
    void aliceIsPushingWithDefaultSettings() {
        var alice = mock(ApplicationUser.class);
        given(alice.getName()).willReturn("Alice");
        given(authenticationContext.getCurrentUser()).willReturn(alice);
        givenHookSettings(Map.of());
    }

    private void givenHookSettings(Map<String, Object> values) {
        var hookSettings = settings(values);
        given(context.getSettings()).willReturn(hookSettings);
    }

    private Settings settings(Map<String, Object> values) {
        var settings = mock(Settings.class);
        given(settings.asMap()).willReturn(values);
        return settings;
    }

    private RepositoryPushHookRequest push(RefChange... changes) {
        var push = pushWithTerminal(terminalOutput);
        given(push.getTrigger()).willReturn(StandardRepositoryHookTrigger.REPO_PUSH);
        given(push.getRefChanges()).willReturn(List.of(changes));
        return push;
    }

    private RefChange tag(String name) {
        return refChange("refs/tags/%s".formatted(name), name, StandardRefType.TAG, RefChangeType.ADD);
    }

    private RefChange branch(String name, RefChangeType type) {
        return refChange("refs/heads/%s".formatted(name), name, StandardRefType.BRANCH, type);
    }

    private RefChange refChange(String id, String displayId, StandardRefType refType, RefChangeType type) {
        var ref = mock(MinimalRef.class);
        given(ref.getId()).willReturn(id);
        given(ref.getDisplayId()).willReturn(displayId);
        given(ref.getType()).willReturn(refType);
        var change = mock(RefChange.class);
        given(change.getRef()).willReturn(ref);
        given(change.getType()).willReturn(type);
        return change;
    }

    private PreRepositoryHookCommitCallback registeredCallback() {
        var callback = ArgumentCaptor.forClass(PreRepositoryHookCommitCallback.class);
        BDDMockito.then(context)
                .should()
                .registerCommitCallback(callback.capture(), eq(RepositoryHookCommitFilter.ADDED_TO_REPOSITORY));
        return callback.getValue();
    }

    private void thenNoCommitIsChecked() {
        BDDMockito.then(context).should(never()).registerCommitCallback(any(), any());
    }

    @Test
    void aFeatureBranchPushIsAcceptedNowAndItsNewCommitsAreCheckedAsTheyStream() {
        // given
        var push = push(branch("main", RefChangeType.UPDATE), branch("feature/x", RefChangeType.UPDATE));

        // when
        var result = sut.preUpdate(context, push);

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        var callback = registeredCallback();
        callback.onCommitAdded(commit("refs/heads/feature/x", "wip: x", 1));
        then(callback.getResult()).matches(RepositoryHookResult::isRejected, "rejected");
    }

    @Test
    void refNamesTypesAndDeletionsReachTheUseCase() {
        // when
        sut.preUpdate(
                context,
                push(
                        branch("main", RefChangeType.UPDATE),
                        tag("feature/v1"),
                        branch("feature/x", RefChangeType.DELETE)));

        // then
        thenNoCommitIsChecked();
    }

    @Test
    void theHookSettingsReachTheUseCaseAndInvalidOnesAreIgnored() {
        // given
        givenHookSettings(Map.of(SettingsKeys.TYPES, "task", SettingsKeys.HEADER_MAX_LENGTH, "x"));

        // when
        sut.preUpdate(context, push(branch("feature/x", RefChangeType.ADD)));

        // then
        var callback = registeredCallback();
        callback.onCommitAdded(commit("refs/heads/feature/x", "task: do it", 1));
        then(callback.getResult()).matches(RepositoryHookResult::isAccepted, "accepted");
    }

    @Test
    void thePusherNameReachesTheUseCase() {
        // given
        givenHookSettings(Map.of(SettingsKeys.BYPASS_USERS, "alice"));

        // when
        sut.preUpdate(context, push(branch("feature/x", RefChangeType.UPDATE)));

        // then
        thenNoCommitIsChecked();
        then(terminalOutput.toString()).contains("checks skipped for Alice (bypass)");
    }

    @Test
    void anAnonymousPushIsChecked() {
        // given
        given(authenticationContext.getCurrentUser()).willReturn(null);
        givenHookSettings(Map.of(SettingsKeys.BYPASS_USERS, "alice"));

        // when
        sut.preUpdate(context, push(branch("feature/x", RefChangeType.UPDATE)));

        // then
        registeredCallback();
    }

    @Test
    void otherTriggersAreIgnored() {
        // given
        var fileEdit = push(branch("feature/x", RefChangeType.UPDATE));
        given(fileEdit.getTrigger()).willReturn(StandardRepositoryHookTrigger.FILE_EDIT);

        // when
        var result = sut.preUpdate(context, fileEdit);

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        thenNoCommitIsChecked();
    }

    @Test
    void aPushWithoutATerminalIsStillChecked() {
        // given
        var noTerminal = push(branch("feature/x", RefChangeType.UPDATE));
        given(noTerminal.getScmHookDetails()).willReturn(Optional.empty());

        // when
        sut.preUpdate(context, noTerminal);

        // then
        registeredCallback();
    }

    @Test
    void aFailureWhileReadingTheSettingsAcceptsThePush() {
        // given
        given(context.getSettings()).willThrow(new IllegalStateException("settings store down"));

        // when
        var result = sut.preUpdate(context, push(branch("feature/x", RefChangeType.UPDATE)));

        // then
        then(result).matches(RepositoryHookResult::isAccepted, "accepted");
        then(terminalOutput.toString()).contains("internal error");
    }

    @Test
    void validateReportsEveryInvalidFieldToTheForm() {
        // given
        var settings = settings(Map.of(SettingsKeys.BRANCH_PATTERN, "feature/(", SettingsKeys.HEADER_MAX_LENGTH, 0));

        // when
        sut.validate(settings, formErrors, mock(Scope.class));

        // then
        BDDMockito.then(formErrors).should().addFieldError(eq(SettingsKeys.BRANCH_PATTERN), anyString());
        BDDMockito.then(formErrors).should().addFieldError(eq(SettingsKeys.HEADER_MAX_LENGTH), anyString());
    }

    @Test
    void problemsShownOnTheFormHaveTheirControlCharactersNeutralized() {
        // given
        var settings = settings(Map.of(SettingsKeys.HEADER_MAX_LENGTH, "7\u001b[2J"));

        // when
        sut.validate(settings, formErrors, mock(Scope.class));

        // then
        BDDMockito.then(formErrors)
                .should()
                .addFieldError(SettingsKeys.HEADER_MAX_LENGTH, "Must be a positive integer, got \"7\\u{1b}[2J\".");
    }

    @Test
    void validateAcceptsValidSettings() {
        // given
        var settings = settings(Map.of(SettingsKeys.TYPES, "feat", SettingsKeys.HEADER_MAX_LENGTH, 72));

        // when
        sut.validate(settings, formErrors, mock(Scope.class));

        // then
        BDDMockito.then(formErrors).should(never()).addFieldError(anyString(), anyString());
        BDDMockito.then(formErrors).should(never()).addFormError(anyString());
    }
}
