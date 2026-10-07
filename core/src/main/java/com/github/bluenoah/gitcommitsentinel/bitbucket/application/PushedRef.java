package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

public record PushedRef(String id, String name, boolean isBranch, boolean isDeleted) {}
