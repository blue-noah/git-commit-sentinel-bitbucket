package com.github.bluenoah.gitcommitsentinel.bitbucket.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Level {
    OFF,
    WARN,
    ERROR;

    public String settingValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Level> fromSettingValue(String settingValue) {
        return Arrays.stream(values())
                .filter(level -> level.settingValue().equalsIgnoreCase(settingValue.strip()))
                .findFirst();
    }
}
