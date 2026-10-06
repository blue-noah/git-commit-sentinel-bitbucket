package com.github.bluenoah.commitsentinel;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * "Trojan Source": invisible or bidirectional characters make code read differently from what it does. Sources must
 * write such characters as Unicode escape sequences, never contain them.
 */
class SourceFilesTest {

    private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src"), Path.of("e2e"));

    @Test
    void noSourceFileContainsInvisibleOrDeceptiveCharacters() throws IOException {
        // given
        List<Path> sourceFiles;
        try (Stream<Path> files = SOURCE_ROOTS.stream().flatMap(SourceFilesTest::filesUnder)) {
            sourceFiles = files.filter(file -> file.toString().matches(".*\\.(java|soy|yaml|sh)$"))
                    .toList();
        }

        // when
        var filesWithDeceptiveCharacters = sourceFiles.stream()
                .filter(SourceFilesTest::containsDeceptiveCharacters)
                .toList();

        // then
        then(sourceFiles).isNotEmpty();
        then(filesWithDeceptiveCharacters).isEmpty();
    }

    private static Stream<Path> filesUnder(Path root) {
        try {
            return Files.walk(root).filter(Files::isRegularFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean containsDeceptiveCharacters(Path file) {
        try {
            return Files.readString(file).codePoints().anyMatch(SourceFilesTest::isDeceptive);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isDeceptive(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL -> codePoint != '\t' && codePoint != '\n' && codePoint != '\r';
            case Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            case Character.SPACE_SEPARATOR -> codePoint != ' ';
            default -> false;
        };
    }
}
