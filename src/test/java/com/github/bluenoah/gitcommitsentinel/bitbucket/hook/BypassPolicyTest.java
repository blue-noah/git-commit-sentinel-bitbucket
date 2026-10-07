package com.github.bluenoah.gitcommitsentinel.bitbucket.hook;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.github.bluenoah.gitcommitsentinel.bitbucket.config.SentinelConfig;
import com.github.bluenoah.gitcommitsentinel.bitbucket.config.SettingsKeys;
import com.github.bluenoah.gitcommitsentinel.bitbucket.rules.RuleSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BypassPolicyTest {

    private final AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
    private final ApplicationUser alice = mock(ApplicationUser.class);
    private final BypassPolicy sut = new BypassPolicy(authenticationContext);

    @BeforeEach
    void aliceIsPushing() {
        given(alice.getName()).willReturn("Alice");
        given(authenticationContext.getCurrentUser()).willReturn(alice);
    }

    private static SentinelConfig config(Map<String, String> hookSettings) {
        return SentinelConfig.fromHookSettings(RuleSet.standard(), hookSettings, (key, problem) -> {});
    }

    @Test
    void nobodyIsExemptByDefault() {
        // when
        var exemptPusher = sut.pusherIfExempt(config(Map.of()));

        // then
        then(exemptPusher).isEmpty();
    }

    @Test
    void listedUsernameIsExemptIgnoringCase() {
        // given
        var config = config(Map.of(SettingsKeys.BYPASS_USERS, "ALICE"));

        // when
        var exemptPusher = sut.pusherIfExempt(config);

        // then
        then(exemptPusher).contains(alice);
    }

    @Test
    void anonymousPushIsNeverExempt() {
        // given
        given(authenticationContext.getCurrentUser()).willReturn(null);
        var config = config(Map.of(SettingsKeys.BYPASS_USERS, "alice"));

        // when
        var exemptPusher = sut.pusherIfExempt(config);

        // then
        then(exemptPusher).isEmpty();
    }
}
