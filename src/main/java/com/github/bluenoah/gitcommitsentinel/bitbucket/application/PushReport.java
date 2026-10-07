package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleViolation;
import java.util.List;

public interface PushReport {

    void violations(PushedCommit commit, List<RuleViolation> violations);

    void commitsWithViolationsNotShown(int count);

    void checksSkippedFor(String exemptPusher);
}
