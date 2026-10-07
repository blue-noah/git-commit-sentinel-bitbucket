package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.text;

import java.util.Set;
import java.util.stream.Collectors;

public final class ControlCharacters {

    private static final Set<Integer> UNICODE_CATEGORIES_TO_NEUTRALIZE =
            Set.of((int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR, (int)
                    Character.PARAGRAPH_SEPARATOR);

    public String neutralized(String untrustedText) {
        return untrustedText.codePoints().mapToObj(this::visibleForm).collect(Collectors.joining());
    }

    private String visibleForm(int codePoint) {
        return UNICODE_CATEGORIES_TO_NEUTRALIZE.contains(Character.getType(codePoint))
                ? "\\u{%s}".formatted(Integer.toHexString(codePoint))
                : Character.toString(codePoint);
    }
}
