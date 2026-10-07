package com.github.bluenoah.gitcommitsentinel.bitbucket.hook;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Untrusted text (commit messages, branch names, settings) is shown as written, except for characters a terminal
 * executes (ANSI escapes, carriage returns) or that change how text reads (bidirectional overrides, invisible
 * characters, line separators): those become a visible <code>&#92;u{...}</code>, so the text can't attack or deceive
 * whoever reads it, on a terminal or in a log.
 */
final class ControlCharacters {

    private static final Set<Integer> UNICODE_CATEGORIES_TO_NEUTRALIZE =
            Set.of((int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR, (int)
                    Character.PARAGRAPH_SEPARATOR);

    private ControlCharacters() {}

    static String neutralized(String untrustedText) {
        return untrustedText
                .codePoints()
                .mapToObj(ControlCharacters::visibleForm)
                .collect(Collectors.joining());
    }

    private static String visibleForm(int codePoint) {
        return UNICODE_CATEGORIES_TO_NEUTRALIZE.contains(Character.getType(codePoint))
                ? "\\u{%s}".formatted(Integer.toHexString(codePoint))
                : Character.toString(codePoint);
    }
}
