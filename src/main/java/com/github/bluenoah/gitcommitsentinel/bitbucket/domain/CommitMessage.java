package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CommitMessage(
        String header, List<String> lines, boolean headerIsConventional, String type, String description) {

    private static final Pattern CONVENTIONAL_HEADER =
            Pattern.compile("^(?<type>[a-zA-Z]+)(?:\\([^)]+\\))?!?: (?<description>.*)$");

    public static CommitMessage parse(String commitMessage) {
        var lines = List.of(Objects.requireNonNullElse(commitMessage, "")
                .replace("\r\n", "\n")
                .split("\n", -1));
        var header = lines.get(0);
        var matcher = CONVENTIONAL_HEADER.matcher(header);
        return matcher.matches()
                ? withConventionalHeader(header, lines, matcher)
                : withUnconventionalHeader(header, lines);
    }

    private static CommitMessage withConventionalHeader(String header, List<String> lines, Matcher matcher) {
        return new CommitMessage(
                header,
                lines,
                true,
                matcher.group("type"),
                matcher.group("description").strip());
    }

    private static CommitMessage withUnconventionalHeader(String header, List<String> lines) {
        return new CommitMessage(header, lines, false, "", "");
    }
}
