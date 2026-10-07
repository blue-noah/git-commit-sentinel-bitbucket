package com.github.bluenoah.gitcommitsentinel.bitbucket.config;

import static java.util.function.Predicate.not;

import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record SettingsParser(
        RuleSet ruleSet, Function<String, String> rawSettingByKey, BiConsumer<String, String> reportInvalidSetting) {

    private static final String ACCEPTED_LEVEL_CHOICES = Stream.concat(
                    Stream.of(SettingsKeys.DEFAULT_LEVEL_CHOICE),
                    Arrays.stream(Level.values()).map(Level::settingValue))
            .collect(Collectors.joining(", "));

    SentinelConfig parse() {
        return new SentinelConfig(
                featureBranchPattern(),
                new RuleConfig(allowedTypes(), headerMaxLength(), configuredLevels()),
                Set.copyOf(listSetting(SettingsKeys.BYPASS_USERS)));
    }

    private Pattern featureBranchPattern() {
        return nonBlankSetting(SettingsKeys.BRANCH_PATTERN)
                .flatMap(this::compiledOrReported)
                .orElse(SentinelConfig.DEFAULT_FEATURE_BRANCH_PATTERN);
    }

    private List<String> allowedTypes() {
        return nonBlankSetting(SettingsKeys.TYPES)
                .flatMap(raw -> reportedIfEmpty(
                        Optional.of(splitOnCommasAndNewlines(raw)).filter(not(List::isEmpty)),
                        SettingsKeys.TYPES,
                        "List at least one type, or leave blank for the defaults."))
                .orElse(RuleConfig.DEFAULT_ALLOWED_TYPES);
    }

    private int headerMaxLength() {
        return nonBlankSetting(SettingsKeys.HEADER_MAX_LENGTH)
                .flatMap(raw -> reportedIfEmpty(
                        positiveInteger(raw),
                        SettingsKeys.HEADER_MAX_LENGTH,
                        "Must be a positive integer, got \"%s\".".formatted(raw)))
                .orElse(RuleConfig.DEFAULT_HEADER_MAX_LENGTH);
    }

    private Map<String, Level> configuredLevels() {
        return ruleSet.rulesInReportingOrder().stream()
                .flatMap(rule ->
                        configuredLevel(SettingsKeys.ruleLevel(rule.name()))
                                .map(level -> Map.entry(rule.name(), level))
                                .stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Optional<Level> configuredLevel(String key) {
        return nonBlankSetting(key)
                .filter(not(SettingsParser::isDefaultLevelChoice))
                .flatMap(raw -> reportedIfEmpty(
                        Level.fromSettingValue(raw),
                        key,
                        "Must be one of %s, got \"%s\".".formatted(ACCEPTED_LEVEL_CHOICES, raw)));
    }

    private Optional<String> nonBlankSetting(String key) {
        return Optional.ofNullable(rawSettingByKey.apply(key)).filter(not(String::isBlank));
    }

    private List<String> listSetting(String key) {
        return nonBlankSetting(key)
                .map(SettingsParser::splitOnCommasAndNewlines)
                .orElse(List.of());
    }

    private Optional<Pattern> compiledOrReported(String regex) {
        try {
            return Optional.of(Pattern.compile(regex));
        } catch (PatternSyntaxException invalidRegex) {
            reportInvalidSetting.accept(
                    SettingsKeys.BRANCH_PATTERN,
                    "Invalid regular expression: %s".formatted(invalidRegex.getDescription()));
            return Optional.empty();
        }
    }

    private <T> Optional<T> reportedIfEmpty(Optional<T> value, String key, String problem) {
        if (value.isEmpty()) {
            reportInvalidSetting.accept(key, problem);
        }
        return value;
    }

    private static List<String> splitOnCommasAndNewlines(String raw) {
        return Arrays.stream(raw.split("[,\\n]"))
                .map(String::strip)
                .filter(not(String::isEmpty))
                .toList();
    }

    private static boolean isDefaultLevelChoice(String raw) {
        return raw.strip().equalsIgnoreCase(SettingsKeys.DEFAULT_LEVEL_CHOICE);
    }

    private static Optional<Integer> positiveInteger(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.strip())).filter(number -> number > 0);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
