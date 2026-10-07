package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static java.util.function.Predicate.not;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CheckPush {

    private final RuleSet ruleSet;

    public CheckPush(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public Optional<PushedCommitsCheck> start(
            List<PushedRef> pushedRefs, Optional<String> pusher, SentinelConfig config, PushReport report) {
        var featureBranchRefIds = pushedRefs.stream()
                .filter(PushedRef::isBranch)
                .filter(not(PushedRef::isDeleted))
                .filter(branch -> config.isFeatureBranch(branch.name()))
                .map(PushedRef::id)
                .collect(Collectors.toUnmodifiableSet());
        if (featureBranchRefIds.isEmpty()) {
            return Optional.empty();
        }
        var exemptPusher = pusher.filter(config::exempts);
        exemptPusher.ifPresent(report::checksSkippedFor);
        return exemptPusher.isEmpty()
                ? Optional.of(new PushedCommitsCheck(ruleSet, config.ruleConfig(), featureBranchRefIds, report))
                : Optional.empty();
    }
}
