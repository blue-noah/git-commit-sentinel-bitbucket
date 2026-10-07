package com.github.bluenoah.gitcommitsentinel.bitbucket.rules;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LevelTest {

    @ParameterizedTest
    @CsvSource({"' off ', OFF", "Warn, WARN", "ERROR, ERROR"})
    void parsesEveryLevelIgnoringCaseAndSurroundingSpace(String settingValue, Level expected) {
        // when
        var level = Level.fromSettingValue(settingValue);

        // then
        then(level).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fatal"})
    void anythingElseIsEmpty(String settingValue) {
        // when
        var level = Level.fromSettingValue(settingValue);

        // then
        then(level).isEmpty();
    }
}
