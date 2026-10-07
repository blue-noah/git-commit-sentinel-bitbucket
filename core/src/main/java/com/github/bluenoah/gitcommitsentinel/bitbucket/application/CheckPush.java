package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static java.util.function.Predicate.not;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CheckPush {

    private final RuleSet ruleSet;

    public CheckPush(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public Optional<PushedCommitsCheck> start(
            List<PushedRef> pushedRefs, Optional<String> pusher, PushPolicy policy, PushReport report) {
        var featureBranchRefIds = pushedRefs.stream()
                .filter(PushedRef::isBranch)
                .filter(not(PushedRef::isDeleted))
                .filter(branch -> policy.isFeatureBranch(branch.name()))
                .map(PushedRef::id)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(featureBranchRefIds)
                .filter(not(Set::isEmpty))
                .filter(refIds -> isCheckedOtherwiseToldSkipped(pusher, policy, report))
                .map(refIds -> new PushedCommitsCheck(ruleSet, policy.ruleConfig(), refIds, report));
    }

    private boolean isCheckedOtherwiseToldSkipped(Optional<String> pusher, PushPolicy policy, PushReport report) {
        var exemptPusher = pusher.filter(policy::exempts);
        exemptPusher.ifPresent(report::checksSkippedFor);
        return exemptPusher.isEmpty();
    }
}
