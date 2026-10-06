package com.github.bluenoah.commitsentinel.rules;

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

    // Code points, not chars: an emoji is one character to the user but two UTF-16 chars to String.length().
    private static int lengthAsTheUserSeesIt(String text) {
        return text.codePointCount(0, text.length());
    }
}
