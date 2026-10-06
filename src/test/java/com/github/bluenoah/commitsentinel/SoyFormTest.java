package com.github.bluenoah.commitsentinel;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.commitsentinel.config.SettingsKeys;
import com.github.bluenoah.commitsentinel.rules.Level;
import com.github.bluenoah.commitsentinel.rules.RuleSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Soy form can't reference Java constants: this keeps its field names in step with the code. */
class SoyFormTest {

    @Test
    void formHasAFieldForEverySettingAndRule() throws IOException {
        // given
        var settingKeys = List.of(
                SettingsKeys.BRANCH_PATTERN,
                SettingsKeys.TYPES,
                SettingsKeys.HEADER_MAX_LENGTH,
                SettingsKeys.BYPASS_USERS);

        // when
        var soy = hookFormTemplate();

        // then
        settingKeys.forEach(key -> then(soy).as("field %s", key).contains("{param name: '%s' /}".formatted(key)));
        RuleSet.standard()
                .rulesInReportingOrder()
                .forEach(rule -> then(soy)
                        .as("level of rule %s", rule.name())
                        .contains("{param rule: '%s' /}".formatted(rule.name())));
        then(soy).contains("value=\"%s\"".formatted(SettingsKeys.DEFAULT_LEVEL_CHOICE));
        Arrays.stream(Level.values())
                .forEach(level -> then(soy).contains("value=\"%s\"".formatted(level.settingValue())));
    }

    private static String hookFormTemplate() throws IOException {
        try (InputStream template = SoyFormTest.class.getResourceAsStream("/static/sentinel.soy")) {
            then(template).as("sentinel.soy on the classpath").isNotNull();
            return new String(template.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
