package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.Optional;

final class DescriptionPeriodRule implements Rule {

    @Override
    public String name() {
        return "description-period";
    }

    @Override
    public Level defaultLevel() {
        return Level.WARN;
    }

    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return Optional.of(commitMessage.description())
                .filter(description -> description.endsWith("."))
                .map(description -> "description must not end with a period");
    }
}
