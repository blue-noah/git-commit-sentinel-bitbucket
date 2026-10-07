package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookCommitFilter;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.hook.repository.StandardRepositoryHookTrigger;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.StandardRefType;
import com.atlassian.bitbucket.scope.Scope;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.atlassian.bitbucket.setting.SettingsValidator;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.outbound.ControlCharacters;
import com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.outbound.PusherTerminal;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.CheckPush;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushPolicy;
import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushedRef;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bitbucket's entry point, called once per push, before any commit is read. From the refs the push changes, it asks
 * {@link CheckPush} whether there is anything to check and, if so, registers a {@link PushedCommitsListener} for the
 * push's new commits. It always accepts: rejecting is up to the listener. It also validates the hook settings form.
 */
public class ConventionalCommitsHook implements PreRepositoryHook<RepositoryPushHookRequest>, SettingsValidator {

    private static final Logger log = LoggerFactory.getLogger(ConventionalCommitsHook.class);

    private final CheckPush checkPush;
    private final RuleSet ruleSet;
    private final AuthenticationContext authenticationContext;
    private final ControlCharacters controlCharacters = new ControlCharacters();

    @Inject
    public ConventionalCommitsHook(
            CheckPush checkPush, RuleSet ruleSet, @ComponentImport AuthenticationContext authenticationContext) {
        this.checkPush = checkPush;
        this.ruleSet = ruleSet;
        this.authenticationContext = authenticationContext;
    }

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(
            @Nonnull PreRepositoryHookContext context, @Nonnull RepositoryPushHookRequest push) {
        var pusherTerminal = PusherTerminal.of(push);
        try {
            if (isGitPush(push)) {
                checkPush
                        .start(pushedRefs(push), pusherName(), pushPolicy(context, push), pusherTerminal)
                        .ifPresent(pushedCommitsCheck -> context.registerCommitCallback(
                                new PushedCommitsListener(pushedCommitsCheck, pusherTerminal),
                                RepositoryHookCommitFilter.ADDED_TO_REPOSITORY));
            }
        } catch (RuntimeException e) {
            log.error("git-commit-sentinel-bitbucket failed on a push to {}; accepting it", push.getRepository(), e);
            pusherTerminal.internalErrorWarning();
        }
        return RepositoryHookResult.accepted();
    }

    @Override
    public void validate(@Nonnull Settings settings, @Nonnull SettingsValidationErrors errors, @Nonnull Scope scope) {
        new SettingsParser(ruleSet, settings.asMap(), neutralizingProblems(errors::addFieldError)).pushPolicy();
    }

    private boolean isGitPush(RepositoryPushHookRequest push) {
        return push.getTrigger() == StandardRepositoryHookTrigger.REPO_PUSH;
    }

    private List<PushedRef> pushedRefs(RepositoryPushHookRequest push) {
        return push.getRefChanges().stream().map(this::pushedRef).toList();
    }

    private PushedRef pushedRef(RefChange change) {
        var ref = change.getRef();
        return new PushedRef(
                ref.getId(),
                ref.getDisplayId(),
                ref.getType() == StandardRefType.BRANCH,
                change.getType() == RefChangeType.DELETE);
    }

    private Optional<String> pusherName() {
        return Optional.ofNullable(authenticationContext.getCurrentUser()).map(ApplicationUser::getName);
    }

    private PushPolicy pushPolicy(PreRepositoryHookContext context, RepositoryPushHookRequest push) {
        return new SettingsParser(
                        ruleSet,
                        context.getSettings().asMap(),
                        neutralizingProblems((key, problem) -> log.warn(
                                "Ignoring invalid git-commit-sentinel-bitbucket setting {} on {}: {}",
                                key,
                                push.getRepository(),
                                problem)))
                .pushPolicy();
    }

    private BiConsumer<String, String> neutralizingProblems(BiConsumer<String, String> problemSink) {
        return (key, problem) -> problemSink.accept(key, controlCharacters.neutralized(problem));
    }
}
