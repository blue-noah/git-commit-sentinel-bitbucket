package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.gitcommitsentinel.bitbucket.application.SentinelConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsParserTest {

    private final Map<String, String> reportedProblems = new HashMap<>();

    private SentinelConfig parse(Map<String, ?> hookSettings) {
        return new SettingsParser(RuleSet.standard(), hookSettings, reportedProblems::put).sentinelConfig();
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
                SettingsKeys.RULE_LEVEL.formatted("type"),
                "WARN",
                SettingsKeys.RULE_LEVEL.formatted("format"),
                "default");

        // when
        var config = parse(hookSettings);

        // then
        then(config.featureBranchPattern().pattern()).isEqualTo("(feature|story)/.+");
        then(config.ruleConfig().allowedTypes()).containsExactly("feat", "task");
        then(config.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(config.ruleConfig().configuredLevels()).containsExactly(Map.entry("type", Level.WARN));
        then(reportedProblems).isEmpty();
    }

    @Test
    void bypassUsersAcceptCommasOrNewlinesAndAreKeptAsTyped() {
        // given
        var hookSettings = Map.of(SettingsKeys.BYPASS_USERS, "CI-Bot,\n alice ");

        // when
        var config = parse(hookSettings);

        // then
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
                SettingsKeys.RULE_LEVEL.formatted("type"),
                "fatal");

        // when
        var config = parse(hookSettings);

        // then
        then(reportedProblems)
                .containsOnlyKeys(
                        SettingsKeys.BRANCH_PATTERN,
                        SettingsKeys.HEADER_MAX_LENGTH,
                        SettingsKeys.TYPES,
                        SettingsKeys.RULE_LEVEL.formatted("type"));
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
        json.put(SettingsKeys.RULE_LEVEL.formatted("type"), "warn");
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
        var json = Map.of(SettingsKeys.HEADER_MAX_LENGTH, true, SettingsKeys.RULE_LEVEL.formatted("format"), 3);

        // when
        parse(json);

        // then
        then(reportedProblems)
                .containsOnlyKeys(SettingsKeys.HEADER_MAX_LENGTH, SettingsKeys.RULE_LEVEL.formatted("format"));
    }

    @Test
    void invalidLevelMessageListsEveryAcceptedChoice() {
        // when
        parse(Map.of(SettingsKeys.RULE_LEVEL.formatted("type"), "fatal"));

        // then
        then(reportedProblems)
                .containsEntry(
                        SettingsKeys.RULE_LEVEL.formatted("type"),
                        "Must be one of default, off, warn, error, got \"fatal\".");
    }

    @Test
    void nonNumericHeaderLengthIsAProblemNotAnException() {
        // when
        parse(Map.of(SettingsKeys.HEADER_MAX_LENGTH, "seventy"));

        // then
        then(reportedProblems).containsKey(SettingsKeys.HEADER_MAX_LENGTH);
    }
}
