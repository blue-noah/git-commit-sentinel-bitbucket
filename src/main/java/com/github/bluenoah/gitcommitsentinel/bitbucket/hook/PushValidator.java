package com.github.bluenoah.gitcommitsentinel.bitbucket.hook;

import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookCommitFilter;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryPushHookRequest;
import com.atlassian.bitbucket.hook.repository.StandardRepositoryHookTrigger;
import com.atlassian.bitbucket.repository.MinimalRef;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.StandardRefType;
import com.github.bluenoah.gitcommitsentinel.bitbucket.config.SentinelConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The hook itself always accepts: a push is rejected later, by the {@link CommitChecker} it registers. Fail-open: a bug
 * in this plugin must never stop developers from pushing, so any failure accepts the push.
 */
@Named
public class PushValidator {

    private static final Logger log = LoggerFactory.getLogger(PushValidator.class);

    private final BypassPolicy bypassPolicy;
    private final RuleSet ruleSet;

    @Inject
    public PushValidator(BypassPolicy bypassPolicy, RuleSet ruleSet) {
        this.bypassPolicy = bypassPolicy;
        this.ruleSet = ruleSet;
    }

    public RepositoryHookResult check(
            PreRepositoryHookContext context,
            RepositoryPushHookRequest push,
            Supplier<SentinelConfig> configReadInsideTheFailOpenGuard) {
        var pusherTerminal = PusherTerminal.of(push);
        try {
            if (isGitPush(push)) {
                checkFeatureBranches(context, push, configReadInsideTheFailOpenGuard.get(), pusherTerminal);
            }
        } catch (RuntimeException e) {
            log.error("git-commit-sentinel-bitbucket failed on a push to {}; accepting it", push.getRepository(), e);
            pusherTerminal.internalErrorWarning();
        }
        return RepositoryHookResult.accepted();
    }

    private static boolean isGitPush(RepositoryPushHookRequest push) {
        return push.getTrigger() == StandardRepositoryHookTrigger.REPO_PUSH;
    }

    private void checkFeatureBranches(
            PreRepositoryHookContext context,
            RepositoryPushHookRequest push,
            SentinelConfig config,
            PusherTerminal pusherTerminal) {
        var featureBranchRefIds = createdOrUpdatedFeatureBranchRefIds(push, config);
        if (featureBranchRefIds.isEmpty()) {
            return;
        }
        bypassPolicy
                .pusherIfExempt(config)
                .ifPresentOrElse(
                        exemptPusher -> pusherTerminal.note(
                                "commit message checks skipped for %s (bypass)".formatted(exemptPusher.getName())),
                        () -> checkTheirNewCommits(context, config, featureBranchRefIds, pusherTerminal));
    }

    private void checkTheirNewCommits(
            PreRepositoryHookContext context,
            SentinelConfig config,
            Set<String> featureBranchRefIds,
            PusherTerminal pusherTerminal) {
        context.registerCommitCallback(
                new CommitChecker(ruleSet, config.ruleConfig(), featureBranchRefIds, pusherTerminal),
                RepositoryHookCommitFilter.ADDED_TO_REPOSITORY);
    }

    private static Set<String> createdOrUpdatedFeatureBranchRefIds(
            RepositoryPushHookRequest push, SentinelConfig config) {
        return push.getRefChanges().stream()
                .filter(change -> change.getType() != RefChangeType.DELETE)
                .map(RefChange::getRef)
                .filter(ref -> ref.getType() == StandardRefType.BRANCH)
                .filter(branch -> config.isFeatureBranch(branch.getDisplayId()))
                .map(MinimalRef::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
