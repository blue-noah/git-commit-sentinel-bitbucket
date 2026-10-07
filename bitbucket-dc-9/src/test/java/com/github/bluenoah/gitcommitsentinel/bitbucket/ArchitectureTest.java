package com.github.bluenoah.gitcommitsentinel.bitbucket;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/github/bluenoah/gitcommitsentinel/bitbucket");
    private static final String PROJECT_PACKAGE = "com.github.bluenoah.gitcommitsentinel.bitbucket";

    @Test
    void outboundAdaptersNeverImportInboundOnes() {
        // given
        var inboundPackage = "%s.adapter.inbound.".formatted(PROJECT_PACKAGE);

        // when
        var outboundImports = importsOf(MAIN_SOURCES.resolve("adapter/outbound"));

        // then
        then(outboundImports).isNotEmpty().noneMatch(imported -> imported.startsWith(inboundPackage));
    }

    @Test
    void untrustedTextHandlingDependsOnTheJdkOnly() {
        // when
        var textImports = importsOf(MAIN_SOURCES.resolve("adapter/text"));

        // then
        then(textImports).isNotEmpty().allMatch(imported -> imported.startsWith("java."));
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
