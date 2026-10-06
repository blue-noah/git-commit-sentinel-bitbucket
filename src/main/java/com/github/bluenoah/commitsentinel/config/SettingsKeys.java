package com.github.bluenoah.commitsentinel.config;

/** Keys of the hook settings form (sentinel.soy) and of Bitbucket's hook settings REST API: a public contract. */
public final class SettingsKeys {

    public static final String BRANCH_PATTERN = "branchPattern";
    public static final String TYPES = "types";
    public static final String HEADER_MAX_LENGTH = "headerMaxLength";
    public static final String BYPASS_USERS = "bypassUsers";

    public static final String DEFAULT_LEVEL_CHOICE = "default";

    private SettingsKeys() {}

    public static String ruleLevel(String ruleName) {
        return "rule-%s".formatted(ruleName);
    }
}
