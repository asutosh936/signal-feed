package com.signalfeed.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSendExceptionTest {

    // ── Constructor: message only ─────────────────────────────────────────────

    @Test
    void constructor_messageOnly_storesMessage() {
        EmailSendException ex = new EmailSendException("transport failed");
        assertThat(ex.getMessage()).isEqualTo("transport failed");
    }

    @Test
    void constructor_messageOnly_causeIsNull() {
        EmailSendException ex = new EmailSendException("oops");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructor_messageOnly_isRuntimeException() {
        assertThat(new EmailSendException("x")).isInstanceOf(RuntimeException.class);
    }

    // ── Constructor: message + cause ──────────────────────────────────────────

    @Test
    void constructor_messagePlusCause_storesMessage() {
        Throwable cause = new IllegalStateException("smtp error");
        EmailSendException ex = new EmailSendException("send failed", cause);
        assertThat(ex.getMessage()).isEqualTo("send failed");
    }

    @Test
    void constructor_messagePlusCause_storesCause() {
        Throwable cause = new RuntimeException("root");
        EmailSendException ex = new EmailSendException("wrapper", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void constructor_messagePlusCause_isRuntimeException() {
        assertThat(new EmailSendException("x", new RuntimeException()))
                .isInstanceOf(RuntimeException.class);
    }

    // ── Throwability ─────────────────────────────────────────────────────────

    @Test
    void emailSendException_canBeThrown_andCaught() {
        assertThatThrownBy(() -> { throw new EmailSendException("thrown"); })
                .isInstanceOf(EmailSendException.class)
                .hasMessage("thrown");
    }

    @Test
    void emailSendException_canBeThrown_withCause() {
        Throwable root = new RuntimeException("root cause");
        assertThatThrownBy(() -> { throw new EmailSendException("wrapper", root); })
                .isInstanceOf(EmailSendException.class)
                .hasCause(root);
    }
}
