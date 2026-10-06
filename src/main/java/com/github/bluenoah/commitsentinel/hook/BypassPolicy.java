package com.github.bluenoah.commitsentinel.hook;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.github.bluenoah.commitsentinel.config.SentinelConfig;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class BypassPolicy {

    private final AuthenticationContext authenticationContext;

    @Inject
    public BypassPolicy(@ComponentImport AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    public Optional<ApplicationUser> pusherIfExempt(SentinelConfig config) {
        return Optional.ofNullable(authenticationContext.getCurrentUser())
                .filter(pusher -> isListedByName(pusher, config));
    }

    // Bitbucket usernames are case-insensitive.
    private static boolean isListedByName(ApplicationUser pusher, SentinelConfig config) {
        return config.bypassUsernames().stream().anyMatch(pusher.getName()::equalsIgnoreCase);
    }
}
