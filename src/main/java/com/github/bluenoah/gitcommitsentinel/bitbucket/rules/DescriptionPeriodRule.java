package com.github.bluenoah.gitcommitsentinel.bitbucket.rules;

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

    // No headerIsConventional check: an unconventional header has an empty description, which never ends with ".".
    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return Optional.of(commitMessage.description())
                .filter(description -> description.endsWith("."))
                .map(description -> "description must not end with a period");
    }
}
