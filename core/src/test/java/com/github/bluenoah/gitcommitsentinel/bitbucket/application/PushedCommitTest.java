package com.github.bluenoah.gitcommitsentinel.bitbucket.application;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushedCommitTest {

    @ParameterizedTest
    @ValueSource(strings = {"feat: add login", "feat: add login\n\nbody", "feat: add login\r\n\r\nbody"})
    void theHeaderIsTheFirstLineOfTheMessage(String message) {
        // given
        var sut = new PushedCommit("abc1234", message, 1, "refs/heads/feature/x", "feature/x");

        // when
        var header = sut.header();

        // then
        then(header).isEqualTo("feat: add login");
    }
}
