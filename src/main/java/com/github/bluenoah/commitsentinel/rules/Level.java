package com.github.bluenoah.commitsentinel.rules;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** {@code WARN} is reported but lets the push through; {@code ERROR} is reported and rejects it. */
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
