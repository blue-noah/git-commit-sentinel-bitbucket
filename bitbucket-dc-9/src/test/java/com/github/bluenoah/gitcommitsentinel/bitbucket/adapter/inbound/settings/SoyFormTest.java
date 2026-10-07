package com.github.bluenoah.gitcommitsentinel.bitbucket.adapter.inbound.settings;

import static org.assertj.core.api.BDDAssertions.then;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.Level;
import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.RuleSet;
import io.hosuaby.inject.resources.junit.jupiter.GivenTextResource;
import io.hosuaby.inject.resources.junit.jupiter.TestWithResources;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@TestWithResources
class SoyFormTest {

    @GivenTextResource("/static/sentinel.soy")
    String soy;

    @GivenTextResource("/atlassian-plugin.xml")
    String descriptor;

    @Test
    void formHasAFieldForEverySettingAndRule() {
        // given
        var settingKeys = List.of("branchPattern", "types", "headerMaxLength", "bypassUsers");
        var rules = RuleSet.standard().rulesInReportingOrder();

        // then
        settingKeys.forEach(key -> then(soy).as("field %s", key).contains("{param name: '%s' /}".formatted(key)));
        rules.forEach(rule ->
                then(soy).as("level of rule %s", rule.name()).contains("{param rule: '%s' /}".formatted(rule.name())));
        then(soy).contains("value=\"%s\"".formatted("default"));
        Arrays.stream(Level.values())
                .forEach(level ->
                        then(soy).contains("value=\"%s\"".formatted(level.name().toLowerCase(Locale.ROOT))));
    }

    @Test
    void thePluginDescriptorNamesTheFormTemplateTheSoyFileDefines() {
        // when
        var formTemplate = "%s.%s"
                .formatted(firstMatch(soy, "\\{namespace ([\\w.]+)}"), firstMatch(soy, "\\{template \\.(\\w+)}"));

        // then
        then(descriptor).contains("<view>%s</view>".formatted(formTemplate));
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
