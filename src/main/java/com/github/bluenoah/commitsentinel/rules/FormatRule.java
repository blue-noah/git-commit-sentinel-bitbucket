package com.github.bluenoah.commitsentinel.rules;

import static java.util.function.Predicate.not;

import java.util.Optional;

final class FormatRule implements Rule {

    @Override
    public String name() {
        return "format";
    }

    @Override
    public Level defaultLevel() {
        return Level.ERROR;
    }

    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return Optional.of(commitMessage)
                .filter(not(CommitMessage::headerIsConventional))
                .map(unconventional ->
                        "header \"%s\" does not match the expected format: <type>(<scope>)!: <description>"
                                .formatted(unconventional.header()));
    }
}
