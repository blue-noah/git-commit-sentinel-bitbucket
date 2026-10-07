package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.text;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ControlCharactersTest {

    private final ControlCharacters sut = new ControlCharacters();

    @Test
    void nullIsShownAsNothing() {
        // when
        var shown = sut.neutralized(null);

        // then
        then(shown).isEmpty();
    }

    static Stream<Arguments> attacks() {
        return Stream.of(
                Arguments.of("clear screen: \u001b[2J", "clear screen: \\u{1b}[2J"),
                Arguments.of("red \u001b[31mtext\u001b[0m", "red \\u{1b}[31mtext\\u{1b}[0m"),
                Arguments.of("real\rfake line over it", "real\\u{d}fake line over it"),
                Arguments.of("next\nline", "next\\u{a}line"),
                Arguments.of("8-bit CSI \u009b2J", "8-bit CSI \\u{9b}2J"),
                Arguments.of(
                        "nul \u0000 del \u007f bell \u0007 tab \t", "nul \\u{0} del \\u{7f} bell \\u{7} tab \\u{9}"),
                Arguments.of("bidi \u202Egnp.exe", "bidi \\u{202e}gnp.exe"),
                Arguments.of("zero\u200Bwidth", "zero\\u{200b}width"),
                Arguments.of("hidden" + Character.toString(0xE0041), "hidden\\u{e0041}"),
                Arguments.of("line\u2028separator", "line\\u{2028}separator"));
    }

    @ParameterizedTest
    @MethodSource("attacks")
    void charactersThatAttackOrDeceiveTheReaderBecomeVisible(String untrustedText, String expectedShown) {
        // when
        var shown = sut.neutralized(untrustedText);

        // then
        then(shown).isEqualTo(expectedShown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"feat: add login", "say \"hi\"", "back\\slash", "caffè è ok", "emoji 🎉", "nbsp\u00A0here"})
    void everythingElseIsShownAsWritten(String text) {
        // when
        var shown = sut.neutralized(text);

        // then
        then(shown).isEqualTo(text);
    }

    @Test
    void noCodePointCanReachTheReaderRawAndEveryHarmlessOneIsKept() {
        // given
        var everyCodePoint = IntStream.rangeClosed(0, Character.MAX_CODE_POINT);

        // when
        var mishandled = everyCodePoint
                .filter(codePoint -> !isHandledSafely(codePoint))
                .mapToObj(Integer::toHexString)
                .toList();

        // then
        then(mishandled).isEmpty();
    }

    private boolean isHandledSafely(int codePoint) {
        var text = Character.toString(codePoint);
        var shown = sut.neutralized(text);
        return isDeceptive(codePoint) ? shown.codePoints().noneMatch(this::isDeceptive) : shown.equals(text);
    }

    private boolean isDeceptive(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }
}
