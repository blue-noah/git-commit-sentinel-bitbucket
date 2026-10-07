package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.List;
import java.util.Map;

public record RuleConfig(List<String> allowedTypes, int headerMaxLength, Map<String, Level> configuredLevels) {

    public static final List<String> DEFAULT_ALLOWED_TYPES =
            List.of("feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore", "revert");

    public static final int DEFAULT_HEADER_MAX_LENGTH = 100;

    public RuleConfig {
        allowedTypes = List.copyOf(allowedTypes);
        configuredLevels = Map.copyOf(configuredLevels);
    }

    public Level effectiveLevelOf(Rule rule) {
        return configuredLevels.getOrDefault(rule.name(), rule.defaultLevel());
    }
}
