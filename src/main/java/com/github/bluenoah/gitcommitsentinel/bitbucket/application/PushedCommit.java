package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import com.github.bluenoah.gitcommitsentinel.bitbucket.domain.CommitMessage;

public record PushedCommit(String displayId, String message, int parentCount, String refId, String branchName) {

    public String header() {
        return CommitMessage.parse(message).header();
    }

    boolean isMerge() {
        return parentCount > 1;
    }
}
