package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

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
                .forEach(level ->
                        then(soy).contains("value=\"%s\"".formatted(level.name().toLowerCase(Locale.ROOT))));
    }

    private String hookFormTemplate() throws IOException {
        try (InputStream template = SoyFormTest.class.getResourceAsStream("/static/sentinel.soy")) {
            then(template).as("sentinel.soy on the classpath").isNotNull();
            return new String(template.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
