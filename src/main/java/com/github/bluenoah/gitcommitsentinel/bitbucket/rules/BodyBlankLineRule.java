package com.github.bluenoah.gitcommitsentinel.bitbucket.rules;

import static java.util.function.Predicate.not;

import java.util.Optional;

final class BodyBlankLineRule implements Rule {

    @Override
    public String name() {
        return "body-blank-line";
    }

    @Override
    public Level defaultLevel() {
        return Level.WARN;
    }

    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return commitMessage.lines().stream()
                .skip(1)
                .findFirst()
                .filter(not(String::isBlank))
                .map(secondLine -> "second line must be blank to separate header from body");
    }
}
