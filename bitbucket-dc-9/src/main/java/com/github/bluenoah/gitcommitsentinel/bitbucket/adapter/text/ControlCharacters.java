package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.text;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes untrusted text (commit messages, branch names, usernames, settings typed by an admin) safe to show on a
 * terminal, in a log or in a form. Text is kept as written, except for characters a terminal executes (ANSI escape
 * sequences, carriage returns) or that change how text reads (bidirectional overrides, invisible characters, line
 * separators): each becomes a visible <code>&#92;u{hex}</code>, so the text can neither attack nor deceive its reader.
 */
public final class ControlCharacters {

    private static final Set<Integer> UNICODE_CATEGORIES_TO_NEUTRALIZE =
            Set.of((int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR, (int)
                    Character.PARAGRAPH_SEPARATOR);

    public String neutralized(String untrustedText) {
        return Objects.requireNonNullElse(untrustedText, "")
                .codePoints()
                .mapToObj(this::visibleForm)
                .collect(Collectors.joining());
    }

    private String visibleForm(int codePoint) {
        return UNICODE_CATEGORIES_TO_NEUTRALIZE.contains(Character.getType(codePoint))
                ? "\\u{%s}".formatted(Integer.toHexString(codePoint))
                : Character.toString(codePoint);
    }
}
