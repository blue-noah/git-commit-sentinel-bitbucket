package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound.settings;

import static java.util.function.Predicate.not;

import com.github.bluenoah.gitcommitsentinel.bitbucket.application.PushPolicy;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SettingsParser(
        RuleSet ruleSet, Map<String, ?> hookSettings, BiConsumer<String, String> reportInvalidSetting) {

    private static final String BRANCH_PATTERN = "branchPattern";
    private static final String TYPES = "types";
    private static final String HEADER_MAX_LENGTH = "headerMaxLength";
    private static final String BYPASS_USERS = "bypassUsers";
    private static final String RULE_LEVEL = "rule-%s";
    private static final String DEFAULT_LEVEL_CHOICE = "default";

    public PushPolicy pushPolicy() {
        return new PushPolicy(
                featureBranchPattern(),
                new RuleConfig(allowedTypes(), headerMaxLength(), configuredLevels()),
                Set.copyOf(listSetting(BYPASS_USERS)));
    }

    private Pattern featureBranchPattern() {
        return nonBlankSetting(BRANCH_PATTERN)
                .flatMap(this::compiledOrReported)
                .orElse(PushPolicy.DEFAULT_FEATURE_BRANCH_PATTERN);
    }

    private List<String> allowedTypes() {
        return nonBlankSetting(TYPES)
                .flatMap(raw -> reportedIfEmpty(
                        Optional.of(splitOnCommasAndNewlines(raw)).filter(not(List::isEmpty)),
                        TYPES,
                        "List at least one type, or leave blank for the defaults."))
                .orElse(RuleConfig.DEFAULT_ALLOWED_TYPES);
    }

    private int headerMaxLength() {
        return nonBlankSetting(HEADER_MAX_LENGTH)
                .flatMap(raw -> reportedIfEmpty(
                        positiveInteger(raw),
                        HEADER_MAX_LENGTH,
                        "Must be a positive integer, got \"%s\".".formatted(raw)))
                .orElse(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
    }

    private Map<String, Level> configuredLevels() {
        return ruleSet.rulesInReportingOrder().stream()
                .flatMap(rule ->
                        configuredLevel(RULE_LEVEL.formatted(rule.name()))
                                .map(level -> Map.entry(rule.name(), level))
                                .stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Optional<Level> configuredLevel(String key) {
        return nonBlankSetting(key)
                .filter(not(this::isDefaultLevelChoice))
                .flatMap(raw -> reportedIfEmpty(
                        level(raw), key, "Must be one of %s, got \"%s\".".formatted(acceptedLevelChoices(), raw)));
    }

    private Optional<String> nonBlankSetting(String key) {
        return Optional.ofNullable(Objects.toString(hookSettings.get(key), null))
                .filter(not(String::isBlank));
    }

    private List<String> listSetting(String key) {
        return nonBlankSetting(key).map(this::splitOnCommasAndNewlines).orElse(List.of());
    }

    private Optional<Pattern> compiledOrReported(String regex) {
        try {
            return Optional.of(Pattern.compile(regex));
        } catch (PatternSyntaxException invalidRegex) {
            reportInvalidSetting.accept(
                    BRANCH_PATTERN, "Invalid regular expression: %s".formatted(invalidRegex.getDescription()));
            return Optional.empty();
        }
    }

    private <T> Optional<T> reportedIfEmpty(Optional<T> value, String key, String problem) {
        if (value.isEmpty()) {
            reportInvalidSetting.accept(key, problem);
        }
        return value;
    }

    private List<String> splitOnCommasAndNewlines(String raw) {
        return Arrays.stream(raw.split("[,\\n]"))
                .map(String::strip)
                .filter(not(String::isEmpty))
                .toList();
    }

    private Optional<Level> level(String raw) {
        return Arrays.stream(Level.values())
                .filter(level -> levelChoice(level).equalsIgnoreCase(raw.strip()))
                .findFirst();
    }

    private String acceptedLevelChoices() {
        return Stream.concat(
                        Stream.of(DEFAULT_LEVEL_CHOICE),
                        Arrays.stream(Level.values()).map(this::levelChoice))
                .collect(Collectors.joining(", "));
    }

    private String levelChoice(Level level) {
        return level.name().toLowerCase(Locale.ROOT);
    }

    private boolean isDefaultLevelChoice(String raw) {
        return raw.strip().equalsIgnoreCase(DEFAULT_LEVEL_CHOICE);
    }

    private Optional<Integer> positiveInteger(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.strip())).filter(number -> number > 0);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
