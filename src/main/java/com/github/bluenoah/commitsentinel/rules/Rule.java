package com.github.bluenoah.commitsentinel.rules;

import java.util.Optional;

/**
 * Adding a rule means writing one class, permitting it here, listing it in {@link RuleSet#standard()} and adding its
 * level to the hook form (sentinel.soy; SoyFormTest fails until you do). Names match git-commit-sentinel's, so a rule
 * has the same name client side and server side.
 */
public sealed interface Rule
        permits FormatRule, TypeRule, DescriptionEmptyRule, DescriptionPeriodRule, HeaderLengthRule, BodyBlankLineRule {

    String name();

    Level defaultLevel();

    Optional<String> violation(CommitMessage commitMessage, RuleConfig config);
}
