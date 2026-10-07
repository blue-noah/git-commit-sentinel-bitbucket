package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.Optional;

public sealed interface Rule
        permits FormatRule, TypeRule, DescriptionEmptyRule, DescriptionPeriodRule, HeaderLengthRule, BodyBlankLineRule {

    String name();

    Level defaultLevel();

    Optional<String> violation(CommitMessage commitMessage, RuleConfig config);
}
