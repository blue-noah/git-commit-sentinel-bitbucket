package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import java.util.Set;
import java.util.regex.Pattern;

public record SentinelConfig(Pattern featureBranchPattern, RuleConfig ruleConfig, Set<String> bypassUsernames) {

    public static final Pattern DEFAULT_FEATURE_BRANCH_PATTERN = Pattern.compile("feature/.+");

    boolean isFeatureBranch(String branchName) {
        return featureBranchPattern.matcher(branchName).matches();
    }

    boolean exempts(String username) {
        return bypassUsernames.stream().anyMatch(username::equalsIgnoreCase);
    }
}
