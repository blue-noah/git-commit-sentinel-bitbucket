package com.github.bluenoah.gitcommitsentinel.bitbucket.hook;

import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.scope.Scope;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.atlassian.bitbucket.setting.SettingsValidator;
import com.github.bluenoah.gitcommitsentinel.bitbucket.config.SentinelConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bitbucket passes the settings of the scope that applies: the repository's override, or else its project's. Invalid
 * settings are described with the value the admin typed, neutralized on its way out to the log and to the form.
 */
public class SentinelHook implements PreRepositoryHook<RepositoryPushHookRequest>, SettingsValidator {

    private static final Logger log = LoggerFactory.getLogger(SentinelHook.class);

    private final PushValidator pushValidator;
    private final RuleSet ruleSet;

    @Inject
    public SentinelHook(PushValidator pushValidator, RuleSet ruleSet) {
        this.pushValidator = pushValidator;
        this.ruleSet = ruleSet;
    }

    @Override
    public RepositoryHookResult preUpdate(PreRepositoryHookContext context, RepositoryPushHookRequest push) {
        return pushValidator.check(
                context,
                push,
                () -> SentinelConfig.fromHookSettings(
                        ruleSet,
                        context.getSettings().asMap(),
                        neutralizingProblems((key, problem) -> log.warn(
                                "Ignoring invalid git-commit-sentinel-bitbucket setting {} on {}: {}",
                                key,
                                push.getRepository(),
                                problem))));
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Scope scope) {
        SentinelConfig.fromHookSettings(ruleSet, settings.asMap(), neutralizingProblems(errors::addFieldError));
    }

    private static BiConsumer<String, String> neutralizingProblems(BiConsumer<String, String> problemSink) {
        return (key, problem) -> problemSink.accept(key, ControlCharacters.neutralized(problem));
    }
}
