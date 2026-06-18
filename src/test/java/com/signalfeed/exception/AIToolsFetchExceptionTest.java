package com.signalfeed.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIToolsFetchExceptionTest {

    @Test
    void messageConstructor_storesMessage() {
        var ex = new AIToolsFetchException("fetch failed");

        assertThat(ex.getMessage()).isEqualTo("fetch failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageCauseConstructor_storesMessageAndCause() {
        var cause = new RuntimeException("upstream error");
        var ex = new AIToolsFetchException("fetch failed", cause);

        assertThat(ex.getMessage()).isEqualTo("fetch failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void isRuntimeException() {
        var ex = new AIToolsFetchException("error");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void causeChain_isPreserved() {
        var root = new IllegalStateException("root cause");
        var mid = new RuntimeException("mid", root);
        var ex = new AIToolsFetchException("top", mid);

        assertThat(ex.getCause()).isSameAs(mid);
        assertThat(ex.getCause().getCause()).isSameAs(root);
    }

    @Test
    void thrownAndCaught_behavesAsUncheckedException() {
        try {
            throw new AIToolsFetchException("boom");
        } catch (AIToolsFetchException e) {
            assertThat(e.getMessage()).isEqualTo("boom");
        }
    }
}
