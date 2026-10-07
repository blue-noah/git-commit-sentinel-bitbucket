package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SourceFilesTest {

    private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src"), Path.of("../core/src"), Path.of("../e2e"));

    @Test
    void noSourceFileContainsInvisibleOrDeceptiveCharacters() throws IOException {
        // given
        List<Path> sourceFiles;
        try (Stream<Path> files = SOURCE_ROOTS.stream().flatMap(this::filesUnder)) {
            sourceFiles = files.filter(file -> file.toString().matches(".*\\.(java|soy|yaml|sh)$"))
                    .toList();
        }

        // when
        var filesWithDeceptiveCharacters =
                sourceFiles.stream().filter(this::containsDeceptiveCharacters).toList();

        // then
        then(sourceFiles).isNotEmpty();
        then(filesWithDeceptiveCharacters).isEmpty();
    }

    private Stream<Path> filesUnder(Path root) {
        try {
            return Files.walk(root).filter(Files::isRegularFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean containsDeceptiveCharacters(Path file) {
        try {
            return Files.readString(file).codePoints().anyMatch(this::isDeceptive);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isDeceptive(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL -> codePoint != '\t' && codePoint != '\n' && codePoint != '\r';
            case Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            case Character.SPACE_SEPARATOR -> codePoint != ' ';
            default -> false;
        };
    }
}
