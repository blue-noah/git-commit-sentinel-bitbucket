package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound.settings;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushPolicy;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsParserTest {

    private final Map<String, String> reportedProblems = new HashMap<>();

    private PushPolicy parse(Map<String, ?> hookSettings) {
        return new SettingsParser(RuleSet.standard()).pushPolicy(hookSettings, reportedProblems::put);
    }

    @Test
    void emptySettingsGiveTheBuiltInDefaults() {
        // when
        var policy = parse(Map.of());

        // then
        then(policy.ruleConfig().allowedTypes()).isEqualTo(RuleConfig.DEFAULT_ALLOWED_TYPES);
        then(policy.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(policy.ruleConfig().configuredLevels()).isEmpty();
        then(policy.bypassUsernames()).isEmpty();
        then(policy.featureBranchPattern()).isEqualTo(PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN);
        then(reportedProblems).isEmpty();
    }

    @Test
    void configuredValuesReplaceTheDefaultsAndBlankKeepsThem() {
        // given
        var hookSettings = Map.of(
                "branchPattern",
                "(feature|story)/.+",
                "types",
                " feat, task ",
                "headerMaxLength",
                " ",
                "rule-type",
                "WARN",
                "rule-format",
                "default");

        // when
        var policy = parse(hookSettings);

        // then
        then(policy.featureBranchPattern().pattern()).isEqualTo("(feature|story)/.+");
        then(policy.ruleConfig().allowedTypes()).containsExactly("feat", "task");
        then(policy.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(policy.ruleConfig().configuredLevels()).containsExactly(Map.entry("type", Level.WARN));
        then(reportedProblems).isEmpty();
    }

    @Test
    void bypassUsersAcceptCommasOrNewlinesAndAreKeptAsTyped() {
        // given
        var hookSettings = Map.of("bypassUsers", "CI-Bot,\n alice ");

        // when
        var policy = parse(hookSettings);

        // then
        then(policy.bypassUsernames()).containsExactlyInAnyOrder("CI-Bot", "alice");
    }

    @Test
    void invalidValuesAreReportedAndFallBackToTheDefaults() {
        // given
        var hookSettings =
                Map.of("branchPattern", "feature/(", "headerMaxLength", "0", "types", " , ", "rule-type", "fatal");

        // when
        var policy = parse(hookSettings);

        // then
        then(reportedProblems).containsOnlyKeys("branchPattern", "headerMaxLength", "types", "rule-type");
        then(policy.featureBranchPattern()).isEqualTo(PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN);
        then(policy.ruleConfig().headerMaxLength()).isEqualTo(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
        then(policy.ruleConfig().allowedTypes()).isEqualTo(RuleConfig.DEFAULT_ALLOWED_TYPES);
        then(policy.ruleConfig().configuredLevels()).isEmpty();
    }

    @Test
    void restJsonNumbersAreReadAsText() {
        // given
        var json = new HashMap<String, Object>();
        json.put("headerMaxLength", 72);
        json.put("types", "feat,fix");
        json.put("bypassUsers", "CI-Bot,release");
        json.put("rule-type", "warn");
        json.put("branchPattern", null);

        // when
        var policy = parse(json);

        // then
        then(policy.ruleConfig().headerMaxLength()).isEqualTo(72);
        then(policy.ruleConfig().allowedTypes()).containsExactly("feat", "fix");
        then(policy.bypassUsernames()).containsExactlyInAnyOrder("CI-Bot", "release");
        then(policy.ruleConfig().configuredLevels()).containsExactly(Map.entry("type", Level.WARN));
        then(policy.featureBranchPattern()).isEqualTo(PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN);
        then(reportedProblems).isEmpty();
    }

    @Test
    void nonTextValuesThatMakeNoSenseAreProblemsNotExceptions() {
        // given
        var json = Map.of("headerMaxLength", true, "rule-format", 3);

        // when
        parse(json);

        // then
        then(reportedProblems).containsOnlyKeys("headerMaxLength", "rule-format");
    }

    @Test
    void invalidLevelMessageListsEveryAcceptedChoice() {
        // when
        parse(Map.of("rule-type", "fatal"));

        // then
        then(reportedProblems).containsEntry("rule-type", "Must be one of default, off, warn, error, got \"fatal\".");
    }

    @Test
    void nonNumericHeaderLengthIsAProblemNotAnException() {
        // when
        parse(Map.of("headerMaxLength", "seventy"));

        // then
        then(reportedProblems).containsKey("headerMaxLength");
    }

    @Test
    void theValueEchoedInAProblemHasItsControlCharactersNeutralized() {
        // when
        parse(Map.of("headerMaxLength", "7\u001b[2J", "rule-type", "\u202Eerror"));

        // then
        then(reportedProblems)
                .containsEntry("headerMaxLength", "Must be a positive integer, got \"7\\u{1b}[2J\".")
                .containsEntry("rule-type", "Must be one of default, off, warn, error, got \"\\u{202e}error\".");
    }
}
