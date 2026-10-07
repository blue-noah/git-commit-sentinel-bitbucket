package com.github.bluenoah.gitcommitsentinel.bitbucket.config;

import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public record SentinelConfig(Pattern featureBranchPattern, RuleConfig ruleConfig, Set<String> bypassUsernames) {

    public static final Pattern DEFAULT_FEATURE_BRANCH_PATTERN = Pattern.compile("feature/.+");

    /**
     * Through the REST API a setting can be a JSON string, number or boolean: all are read as their text, so
     * {@code "headerMaxLength": 72} works like {@code "72"}. An invalid setting falls back to its default, because the
     * hook must keep working even if bad data slips past the form's validation.
     */
    public static SentinelConfig fromHookSettings(
            RuleSet ruleSet, Map<String, ?> hookSettings, BiConsumer<String, String> reportInvalidSetting) {
        return new SettingsParser(ruleSet, key -> Objects.toString(hookSettings.get(key), null), reportInvalidSetting)
                .parse();
    }

    public boolean isFeatureBranch(String branchNameWithoutRefsHeads) {
        return featureBranchPattern.matcher(branchNameWithoutRefsHeads).matches();
    }
}
