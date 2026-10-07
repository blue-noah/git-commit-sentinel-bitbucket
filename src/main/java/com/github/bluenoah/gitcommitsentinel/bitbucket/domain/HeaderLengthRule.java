package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.Optional;

final class HeaderLengthRule implements Rule {

    @Override
    public String name() {
        return "header-length";
    }

    @Override
    public Level defaultLevel() {
        return Level.WARN;
    }

    @Override
    public Optional<String> violation(CommitMessage commitMessage, RuleConfig config) {
        return Optional.of(lengthAsTheUserSeesIt(commitMessage.header()))
                .filter(headerLength -> headerLength > config.headerMaxLength())
                .map(headerLength -> "header is %s characters long, exceeds the limit of %s"
                        .formatted(headerLength, config.headerMaxLength()));
    }

    private int lengthAsTheUserSeesIt(String text) {
        return text.codePointCount(0, text.length());
    }
}
