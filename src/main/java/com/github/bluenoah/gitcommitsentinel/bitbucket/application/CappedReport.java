package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.util.List;

final class CappedReport {

    static final int MAX_COMMITS_SHOWN = 20;

    private final PushReport report;

    private int commitsShown;
    private int commitsNotShown;

    CappedReport(PushReport report) {
        this.report = report;
    }

    void violations(PushedCommit commit, List<RuleViolation> violations) {
        if (commitsShown < MAX_COMMITS_SHOWN) {
            commitsShown++;
            report.violations(commit, violations);
        } else {
            commitsNotShown++;
        }
    }

    void close() {
        if (commitsNotShown > 0) {
            report.commitsWithViolationsNotShown(commitsNotShown);
        }
    }
}
