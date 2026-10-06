package com.github.bluenoah.commitsentinel.hook;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;

import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.scope.Scope;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.github.bluenoah.commitsentinel.config.SentinelConfig;
import com.github.bluenoah.commitsentinel.config.SettingsKeys;
import com.github.bluenoah.commitsentinel.rules.RuleSet;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;

class SentinelHookTest {

    private final PushValidator pushValidator = mock(PushValidator.class);
    private final SentinelHook sut = new SentinelHook(pushValidator, RuleSet.standard());
    private final SettingsValidationErrors formErrors = mock(SettingsValidationErrors.class);

    private static Settings settings(Map<String, Object> values) {
        var settings = mock(Settings.class);
        given(settings.asMap()).willReturn(values);
        return settings;
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

    @Test
    @SuppressWarnings("unchecked")
    void preUpdateHandsTheHookSettingsToThePushValidator() {
        // given: an invalid header length, which must be logged rather than thrown
        var context = mock(PreRepositoryHookContext.class);
        var settings = settings(Map.of(SettingsKeys.TYPES, "task", SettingsKeys.HEADER_MAX_LENGTH, "x"));
        given(context.getSettings()).willReturn(settings);
        var push = mock(RepositoryPushHookRequest.class);
        var accepted = RepositoryHookResult.accepted();
        ArgumentCaptor<Supplier<SentinelConfig>> config = ArgumentCaptor.forClass(Supplier.class);
        given(pushValidator.check(eq(context), eq(push), config.capture())).willReturn(accepted);

        // when
        var result = sut.preUpdate(context, push);

        // then
        then(result).isSameAs(accepted);
        then(config.getValue().get().ruleConfig().allowedTypes()).containsExactly("task");
    }
}
