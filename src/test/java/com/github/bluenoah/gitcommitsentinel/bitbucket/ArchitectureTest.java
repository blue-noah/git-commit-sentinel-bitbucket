package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArchitectureTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/github/bluenoah/gitcommitsentinel/bitbucket");
    private static final String PROJECT_PACKAGE = "com.github.bluenoah.gitcommitsentinel.bitbucket";

    @ParameterizedTest(name = "{0} imports only {1}")
    @CsvSource({"domain, java.", "application, java. domain."})
    void eachLayerImportsOnlyTheLayersInsideIt(String layer, String allowedImportPrefixes) {
        // given
        var allowed = Stream.of(allowedImportPrefixes.split(" "))
                .map(prefix -> prefix.startsWith("java.") ? prefix : "%s.%s".formatted(PROJECT_PACKAGE, prefix))
                .toList();

        // when
        var forbiddenImports = importsOf(MAIN_SOURCES.resolve(layer)).stream()
                .filter(imported -> allowed.stream().noneMatch(imported::startsWith))
                .toList();

        // then
        then(importsOf(MAIN_SOURCES.resolve(layer))).isNotEmpty();
        then(forbiddenImports).isEmpty();
    }

    @Test
    void outboundAdaptersNeverImportInboundOnes() {
        // given
        var inboundPackage = "%s.adapter.inbound.".formatted(PROJECT_PACKAGE);

        // when
        var outboundImports = importsOf(MAIN_SOURCES.resolve("adapter/outbound"));

        // then
        then(outboundImports).isNotEmpty().noneMatch(imported -> imported.startsWith(inboundPackage));
    }

    private List<String> importsOf(Path layerSources) {
        try (Stream<Path> files = Files.list(layerSources)) {
            return files.flatMap(this::lines)
                    .filter(line -> line.startsWith("import "))
                    .map(line -> line.replaceFirst("^import (static )?", ""))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
