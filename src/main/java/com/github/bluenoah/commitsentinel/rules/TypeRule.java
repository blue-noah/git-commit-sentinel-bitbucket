package com.github.bluenoah.commitsentinel.rules;

import static java.util.function.Predicate.not;

import java.util.Optional;

final class TypeRule implements Rule {

    @Override
    public String name() {
        return "type";
    }

    @Override
    public Level defaultLevel() {
        return Level.ERROR;
    }

    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return Optional.of(commitMessage)
                .filter(CommitMessage::headerIsConventional)
                .map(CommitMessage::type)
                .filter(not(config.allowedTypes()::contains))
                .map(type -> "type \"%s\" is not in the allowed list: %s"
                        .formatted(type, String.join(", ", config.allowedTypes())));
    }
}
