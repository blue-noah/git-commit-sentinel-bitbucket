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
import java.util.regex.Pattern;
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
        var soy = resource("/static/sentinel.soy");

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

    @Test
    void thePluginDescriptorNamesTheFormTemplateTheSoyFileDefines() throws IOException {
        // given
        var soy = resource("/static/sentinel.soy");
        var formTemplate = "%s.%s"
                .formatted(firstMatch(soy, "\\{namespace ([\\w.]+)}"), firstMatch(soy, "\\{template \\.(\\w+)}"));

        // when
        var descriptor = resource("/atlassian-plugin.xml");

        // then
        then(descriptor).contains("<view>%s</view>".formatted(formTemplate));
    }

    private String resource(String path) throws IOException {
        try (InputStream resource = SoyFormTest.class.getResourceAsStream(path)) {
            then(resource).as("%s on the classpath", path).isNotNull();
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String firstMatch(String text, String regex) {
        return Pattern.compile(regex)
                .matcher(text)
                .results()
                .findFirst()
                .orElseThrow()
                .group(1);
    }
}
