package com.github.bluenoah.gitcommitsentinel.bitbucket.rules;

public record RuleViolation(String ruleName, Level level, String message) {

    public boolean isError() {
        return level == Level.ERROR;
    }
}
