package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.Optional;

final class DescriptionEmptyRule implements Rule {

    @Override
    public String name() {
        return "description-empty";
    }

    @Override
    public Level defaultLevel() {
        return Level.ERROR;
    }

    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return Optional.of(commitMessage)
                .filter(CommitMessage::headerIsConventional)
                .map(CommitMessage::description)
                .filter(String::isEmpty)
                .map(emptyDescription -> "description must not be empty");
    }
}
