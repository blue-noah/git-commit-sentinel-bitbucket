package com.github.bluenoah.gitcommitsentinel.bitbucket.rules;

import java.util.List;
import java.util.Optional;

public record RuleSet(List<Rule> rulesInReportingOrder) {

    public RuleSet {
        rulesInReportingOrder = List.copyOf(rulesInReportingOrder);
    }

    public static RuleSet standard() {
        return new RuleSet(List.of(
                new FormatRule(),
                new TypeRule(),
                new DescriptionEmptyRule(),
                new DescriptionPeriodRule(),
                new HeaderLengthRule(),
                new BodyBlankLineRule()));
    }

    public List<RuleViolation> violationsOf(String commitMessage, RuleConfig config) {
        var parsedMessage = CommitMessage.parse(commitMessage);
        return rulesInReportingOrder.stream()
                .flatMap(rule -> violationIfEnforced(rule, parsedMessage, config).stream())
                .toList();
    }

    private static Optional<RuleViolation> violationIfEnforced(
            Rule rule, CommitMessage commitMessage, RuleConfig config) {
        var level = config.effectiveLevelOf(rule);
        return level == Level.OFF
                ? Optional.empty()
                : rule.violation(commitMessage, config).map(message -> new RuleViolation(rule.name(), level, message));
    }
}
