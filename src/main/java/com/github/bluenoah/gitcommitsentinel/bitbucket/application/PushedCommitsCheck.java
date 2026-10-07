package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.util.List;
import java.util.Set;

public final class PushedCommitsCheck {

    static final int MAX_COMMITS_REPORTED_IN_FULL = 20;

    private final RuleSet ruleSet;
    private final RuleConfig ruleConfig;
    private final Set<String> featureBranchRefIds;
    private final PushReport report;

    private int commitsReportedInFull;
    private int commitsWithViolationsNotShown;
    private int commitsWithErrors;

    PushedCommitsCheck(RuleSet ruleSet, RuleConfig ruleConfig, Set<String> featureBranchRefIds, PushReport report) {
        this.ruleSet = ruleSet;
        this.ruleConfig = ruleConfig;
        this.featureBranchRefIds = featureBranchRefIds;
        this.report = report;
    }

    public void check(PushedCommit commit) {
        if (!featureBranchRefIds.contains(commit.refId()) || commit.isMerge()) {
            return;
        }
        var violations = ruleSet.violationsOf(commit.message(), ruleConfig);
        if (violations.isEmpty()) {
            return;
        }
        if (violations.stream().anyMatch(RuleViolation::isError)) {
            commitsWithErrors++;
        }
        report(commit, violations);
    }

    public PushVerdict finish() {
        if (commitsWithViolationsNotShown > 0) {
            report.commitsWithViolationsNotShown(commitsWithViolationsNotShown);
        }
        return new PushVerdict(commitsWithErrors);
    }

    private void report(PushedCommit commit, List<RuleViolation> violations) {
        if (commitsReportedInFull < MAX_COMMITS_REPORTED_IN_FULL) {
            commitsReportedInFull++;
            report.violations(commit, violations);
        } else {
            commitsWithViolationsNotShown++;
        }
    }
}
