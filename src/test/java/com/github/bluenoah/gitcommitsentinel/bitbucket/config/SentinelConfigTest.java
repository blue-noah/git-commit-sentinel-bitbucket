package com.github.bluenoah.gitcommitsentinel.bitbucket.config;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SentinelConfigTest {

    private final Map<String, String> reportedProblems = new HashMap<>();

    private SentinelConfig parse(Map<String, ?> hookSettings) {
        return SentinelConfig.fromHookSettings(RuleSet.standard(), hookSettings, reportedProblems::put);
    }

    @Test
    void emptySettingsGiveTheBuiltInDefaults() {
        // when
        var config = parse(Map.of());

        // then
        then(config.ruleConfig().allowedTypes()).isEqualTo(RuleConfig.DEFAULT_ALLOWED_TYPES);
        then(config.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(config.ruleConfig().configuredLevels()).isEmpty();
        then(config.bypassUsernames()).isEmpty();
        then(config.featureBranchPattern()).isEqualTo(SentinelConfig.DEFAULT_FEATURE_BRANCH_PATTERN);
        then(reportedProblems).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> feature branch: {1}")
    @CsvSource({"feature/ABC-1, true", "main, false", "my-feature/x, false"})
    void theDefaultPatternMatchesTheWholeBranchName(String branchName, boolean featureBranch) {
        // given
        var config = parse(Map.of());

        // when
        var isFeatureBranch = config.isFeatureBranch(branchName);

        // then
        then(isFeatureBranch).isEqualTo(featureBranch);
    }

    @Test
    void configuredValuesReplaceTheDefaultsAndBlankKeepsThem() {
        // given
        var hookSettings = Map.of(
                SettingsKeys.BRANCH_PATTERN,
                "(feature|story)/.+",
                SettingsKeys.TYPES,
                " feat, task ",
                SettingsKeys.HEADER_MAX_LENGTH,
                " ",
                SettingsKeys.ruleLevel("type"),
                "WARN",
                SettingsKeys.ruleLevel("format"),
                "default");

        // when
        var config = parse(hookSettings);

        // then
        then(config.isFeatureBranch("story/x")).isTrue();
        then(config.ruleConfig().allowedTypes()).containsExactly("feat", "task");
        then(config.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(config.ruleConfig().configuredLevels()).containsExactly(Map.entry("type", Level.WARN));
        then(reportedProblems).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> feature branch: {1}")
    @CsvSource({
        "feature/x, true",
        "tmp/x, true",
        "mainline, true",
        "main, false",
        "develop, false",
        "release/1.0, false"
    })
    void everyBranchExceptTheProtectedOnesPatternFromAdr0001(String branchName, boolean featureBranch) {
        // given
        var config = parse(Map.of(SettingsKeys.BRANCH_PATTERN, "(?!main$|develop$|release/).+"));

        // when
        var isFeatureBranch = config.isFeatureBranch(branchName);

        // then
        then(isFeatureBranch).isEqualTo(featureBranch);
    }

    @Test
    void bypassUsersAcceptCommasOrNewlinesAndAreKeptAsTyped() {
        // given
        var hookSettings = Map.of(SettingsKeys.BYPASS_USERS, "CI-Bot,\n alice ");

        // when
        var config = parse(hookSettings);

        // then: BypassPolicy compares usernames ignoring case
        then(config.bypassUsernames()).containsExactlyInAnyOrder("CI-Bot", "alice");
    }

    @Test
    void invalidValuesAreReportedAndFallBackToTheDefaults() {
        // given
        var hookSettings = Map.of(
                SettingsKeys.BRANCH_PATTERN,
                "feature/(",
                SettingsKeys.HEADER_MAX_LENGTH,
                "0",
                SettingsKeys.TYPES,
                " , ",
                SettingsKeys.ruleLevel("type"),
                "fatal");

        // when
        var config = parse(hookSettings);

        // then
        then(reportedProblems)
                .containsOnlyKeys(
                        SettingsKeys.BRANCH_PATTERN,
                        SettingsKeys.HEADER_MAX_LENGTH,
                        SettingsKeys.TYPES,
                        SettingsKeys.ruleLevel("type"));
        then(config.featureBranchPattern()).isEqualTo(SentinelConfig.DEFAULT_FEATURE_BRANCH_PATTERN);
        then(config.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(config.ruleConfig().allowedTypes()).isEqualTo(RuleConfig.DEFAULT_ALLOWED_TYPES);
        then(config.ruleConfig().configuredLevels()).isEmpty();
    }

    @Test
    void restJsonNumbersAreReadAsText() {
        // given
        var json = new HashMap<String, Object>();
        json.put(SettingsKeys.HEADER_MAX_LENGTH, 72);
        json.put(SettingsKeys.TYPES, "feat,fix");
        json.put(SettingsKeys.BYPASS_USERS, "CI-Bot,release");
        json.put(SettingsKeys.ruleLevel("type"), "warn");
        json.put(SettingsKeys.BRANCH_PATTERN, null);

        // when
        var config = parse(json);

        // then
        then(config.ruleConfig().headerMaxLength()).isEqualTo(72);
        then(config.ruleConfig().allowedTypes()).containsExactly("feat", "fix");
        then(config.bypassUsernames()).containsExactlyInAnyOrder("CI-Bot", "release");
        then(config.ruleConfig().configuredLevels()).containsExactly(Map.entry("type", Level.WARN));
        then(config.featureBranchPattern()).isEqualTo(SentinelConfig.DEFAULT_FEATURE_BRANCH_PATTERN);
        then(reportedProblems).isEmpty();
    }

    @Test
    void nonTextValuesThatMakeNoSenseAreProblemsNotExceptions() {
        // given
        var json = Map.of(SettingsKeys.HEADER_MAX_LENGTH, true, SettingsKeys.ruleLevel("format"), 3);

        // when
        parse(json);

        // then
        then(reportedProblems).containsOnlyKeys(SettingsKeys.HEADER_MAX_LENGTH, SettingsKeys.ruleLevel("format"));
    }

    @Test
    void invalidLevelMessageListsEveryAcceptedChoice() {
        // when
        parse(Map.of(SettingsKeys.ruleLevel("type"), "fatal"));

        // then
        then(reportedProblems)
                .containsEntry(
                        SettingsKeys.ruleLevel("type"), "Must be one of default, off, warn, error, got \"fatal\".");
    }

    @Test
    void nonNumericHeaderLengthIsAProblemNotAnException() {
        // when
        parse(Map.of(SettingsKeys.HEADER_MAX_LENGTH, "seventy"));

        // then
        then(reportedProblems).containsKey(SettingsKeys.HEADER_MAX_LENGTH);
    }
}
