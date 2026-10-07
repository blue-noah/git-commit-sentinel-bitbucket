package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

public record PushVerdict(int commitsWithErrors) {

    public boolean rejectsThePush() {
        return commitsWithErrors > 0;
    }
}
