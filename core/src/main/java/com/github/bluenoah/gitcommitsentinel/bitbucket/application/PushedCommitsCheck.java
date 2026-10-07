package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.util.Set;

public final class PushedCommitsCheck {

    private final RuleSet ruleSet;
    private final RuleConfig ruleConfig;
    private final Set<String> featureBranchRefIds;
    private final CappedReport report;

    private int commitsWithErrors;

    PushedCommitsCheck(RuleSet ruleSet, RuleConfig ruleConfig, Set<String> featureBranchRefIds, PushReport report) {
        this.ruleSet = ruleSet;
        this.ruleConfig = ruleConfig;
        this.featureBranchRefIds = featureBranchRefIds;
        this.report = new CappedReport(report);
    }

    public void check(PushedCommit commit) {
        if (!isChecked(commit)) {
            return;
        }
        var violations = ruleSet.violationsOf(commit.message(), ruleConfig);
        if (violations.isEmpty()) {
            return;
        }
        if (violations.stream().anyMatch(RuleViolation::isError)) {
            commitsWithErrors++;
        }
        report.violations(commit, violations);
    }

    public PushVerdict finish() {
        report.close();
        return new PushVerdict(commitsWithErrors);
    }

    private boolean isChecked(PushedCommit commit) {
        return featureBranchRefIds.contains(commit.refId()) && !commit.isMerge();
    }
}
